/*
 * AEDZInputStream.java
 *
 * Reads AEDZ format files: byte-transposed + zstd compressed AEDAT-2 events.
 * The AEDZ format stores events in chunks, each compressed with zstd after
 * byte-transposition. This reader decompresses chunks on demand (caching the
 * most recent one) and implements AEFileInputStreamInterface for integration
 * with AEViewer/AEPlayer.
 *
 * Robustness contract: every length, offset, event count and compressed size is
 * validated AGAINST THE ACTUAL FILE before any allocation or decompression.
 * A malformed or truncated file must surface as a controlled IOException —
 * never an ArrayIndexOutOfBoundsException, NegativeArraySizeException,
 * OutOfMemoryError, or unchecked Buffer/Channel exception. Both the legacy
 * 12-byte chunk index (offset + n_events) and the extended 20-byte index
 * (offset + n_events + first_ts + last_ts) are accepted; an index region whose
 * size matches neither is rejected deterministically.
 */
package net.sf.jaer.eventio;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.luben.zstd.Zstd;

import net.sf.jaer.aemonitor.AEPacketRaw;

/**
 * Input stream that reads AEDZ compressed format files. Implements
 * AEFileInputStreamInterface for integration with AEViewer/AEPlayer.
 *
 * @author jAER
 */
public class AEDZInputStream implements AEFileInputStreamInterface, java.io.Closeable {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    static final byte[] MAGIC = new byte[]{'A', 'E', 'D', 'Z', 0x00, 0x01, 0x00, 0x00};
    static final byte[] FOOTER_MAGIC = new byte[]{'A', 'E', 'D', 'Z'};
    static final int INDEX_ENTRY_LEGACY = 12, INDEX_ENTRY_EXTENDED = 20;
    static final int N_PLANES = 8;
    static final int PLANE_SIZES_LEN = N_PLANES * 4;

    /** Upper bound on a single chunk's event count; matches the writer's fixed chunk size. */
    static final int MAX_CHUNK_EVENTS = 65536;
    /** Upper bound on decompressed plane length (== chunk event count). */
    static final int MAX_PLANE_LEN = MAX_CHUNK_EVENTS;

    private final PropertyChangeSupport support = new PropertyChangeSupport(this);

    private File file;
    private RandomAccessFile raf;

    // Header data
    private long totalEvents;
    private int nChunks;
    private byte flags;
    private byte[] aedatHeader;
    private byte[] trailing;

    // Chunk index
    private long[] chunkOffsets;
    private int[] chunkEventCounts;
    private long[] chunkEventStarts; // cumulative event start index for each chunk

    // Footer
    private long indexOffset;
    private long summaryOffset;
    private int crc32Value;

    // Decoded chunk cache
    private int cachedChunkIndex = -1;
    private int[] cachedAddr;
    private int[] cachedTs;

    // State
    private long position = 0;
    private int mostRecentTimestamp;
    private int firstTimestamp;
    private int lastTimestamp;
    private int currentStartTimestamp;
    private boolean firstReadCompleted = false;
    private boolean repeat = true;
    private int timestampResetBitmask = 0;
    private boolean nonMonotonicTimeExceptionsChecked = true;
    private long absoluteStartingTimeMs = 0;
    private ZoneId zoneId = ZoneId.systemDefault();

    // Marks
    private long markIn = 0;
    private long markOut = Long.MAX_VALUE;
    private boolean markInSet = false;
    private boolean markOutSet = false;
    private TreeSet<Long> otherMarks = new TreeSet<>();

    // Packet buffer
    private static final int MAX_BUFFER_SIZE_EVENTS = 1 << 20;
    private final AEPacketRaw packet = new AEPacketRaw(MAX_BUFFER_SIZE_EVENTS);

    /**
     * Creates a new AEDZInputStream from the given file.
     *
     * @param file the .aedz file to read
     * @throws IOException if the file cannot be read or has invalid format
     */
    public AEDZInputStream(File file) throws IOException {
        this.file = file;
        this.raf = new RandomAccessFile(file, "r");
        try {
            readFile();
        } catch (IOException | RuntimeException e) {
            // Never leak the open handle on a malformed file.
            try {
                raf.close();
            } catch (IOException ignored) {
            }
            raf = null;
            throw e;
        }
        parseAbsoluteStartingTime();
    }

    /**
     * Read and parse the entire AEDZ file structure, validating every length and
     * offset against the real file before allocation or decompression.
     */
    private void readFile() throws IOException {
        long fileLen = raf.length();

        // ---- Minimum structural length: header (8+8+4+1+4+4) + footer (24) = 53 bytes.
        if (fileLen < 8 + 8 + 4 + 1 + 4 + 4 + 24) {
            throw new IOException("AEDZ file too short: " + fileLen + " bytes");
        }

        // ---- Magic and version.
        byte[] magic = new byte[8];
        raf.seek(0);
        raf.readFully(magic);
        for (int i = 0; i < 8; i++) {
            if (magic[i] != MAGIC[i]) {
                throw new IOException("Not an AEDZ file: bad magic");
            }
        }

        // ---- Header fields (little-endian).
        byte[] buf8 = new byte[8];
        byte[] buf4 = new byte[4];

        raf.readFully(buf8);
        totalEvents = getLongLE(buf8, 0);

        raf.readFully(buf4);
        nChunks = getIntLE(buf4, 0);

        flags = raf.readByte();

        raf.readFully(buf4);
        int aedatHeaderLen = getIntLE(buf4, 0);
        if (aedatHeaderLen < 0) {
            throw new IOException("Negative AEDAT header length: " + aedatHeaderLen);
        }
        // header region: after magic(8)+n_events(8)+n_chunks(4)+flags(1)+hlen(4)
        long headerEnd = 8L + 8 + 4 + 1 + 4 + aedatHeaderLen;
        if (headerEnd > fileLen) {
            throw new IOException("AEDAT header length " + aedatHeaderLen + " exceeds file size " + fileLen);
        }
        aedatHeader = new byte[aedatHeaderLen];
        raf.readFully(aedatHeader);

        raf.readFully(buf4);
        int trailingLen = getIntLE(buf4, 0);
        if (trailingLen < 0) {
            throw new IOException("Negative trailing length: " + trailingLen);
        }
        long dataStart = 8L + 8 + 4 + 1 + 4 + aedatHeaderLen + 4;
        if (trailingLen > 0) {
            long trailingEnd = dataStart + trailingLen;
            if (trailingEnd > fileLen) {
                throw new IOException("Trailing length " + trailingLen + " exceeds file size " + fileLen);
            }
            trailing = new byte[trailingLen];
            raf.readFully(trailing);
        } else {
            trailing = new byte[0];
        }

        // ---- Footer (in the last 24 bytes).
        if (fileLen < 24) {
            throw new IOException("AEDZ file too short for footer");
        }
        raf.seek(fileLen - 24);
        byte[] footerBuf = new byte[24];
        raf.readFully(footerBuf);
        indexOffset = getLongLE(footerBuf, 0);
        summaryOffset = getLongLE(footerBuf, 8);
        crc32Value = getIntLE(footerBuf, 16);
        if (footerBuf[20] != 'A' || footerBuf[21] != 'E' || footerBuf[22] != 'D' || footerBuf[23] != 'Z') {
            throw new IOException("Bad AEDZ footer magic");
        }

        // ---- Validate footer offsets (bounds and ordering).
        if (indexOffset < 0 || summaryOffset < 0) {
            throw new IOException("Negative AEDZ footer offset: index=" + indexOffset + " summary=" + summaryOffset);
        }
        if (indexOffset > summaryOffset) {
            throw new IOException("AEDZ index offset " + indexOffset + " after summary offset " + summaryOffset);
        }
        // summary block must be at least 4 bytes (summary_len); footer occupies the last 24.
        if (summaryOffset + 4 + 24 > fileLen) {
            throw new IOException("AEDZ summary offset " + summaryOffset + " overruns file size " + fileLen);
        }

        // ---- Chunk event totals must be nonnegative and internally consistent
        // ---- with the header counts before we trust nChunks for index sizing.
        if (totalEvents < 0) {
            throw new IOException("Negative total event count: " + totalEvents);
        }
        if (nChunks < 0) {
            throw new IOException("Negative chunk count: " + nChunks);
        }

        // ---- Read the chunk index; accept exactly the legacy 12-byte or the
        // ---- extended 20-byte record, rejecting anything else deterministically.
        long indexRegionLen = summaryOffset - indexOffset;
        if (nChunks == 0) {
            if (totalEvents != 0) {
                throw new IOException("Zero chunks but totalEvents=" + totalEvents);
            }
            if (indexRegionLen != 0) {
                throw new IOException("Zero chunks but non-empty index region: " + indexRegionLen + " bytes");
            }
        } else {
            long legacyBytes = (long) nChunks * INDEX_ENTRY_LEGACY;
            long extendedBytes = (long) nChunks * INDEX_ENTRY_EXTENDED;
            if (indexRegionLen == legacyBytes) {
                readChunkIndex(INDEX_ENTRY_LEGACY);
            } else if (indexRegionLen == extendedBytes) {
                readChunkIndex(INDEX_ENTRY_EXTENDED);
            } else {
                throw new IOException("AEDZ index region " + indexRegionLen + " bytes does not match "
                        + nChunks + " chunks at 12 (" + legacyBytes + ") or 20 (" + extendedBytes + ") bytes per entry");
            }
        }

        // ---- totalEvents must agree with the sum of chunk event counts.
        if (sumChunkEvents() != totalEvents) {
            throw new IOException("AEDZ totalEvents=" + totalEvents + " inconsistent with chunk event sum=" + sumChunkEvents());
        }

        // ---- First and last timestamps by decoding the relevant chunks.
        if (totalEvents > 0) {
            decodeChunk(0);
            firstTimestamp = cachedTs[0];
            currentStartTimestamp = firstTimestamp;
            mostRecentTimestamp = firstTimestamp;

            if (nChunks > 0) {
                decodeChunk(nChunks - 1);
                lastTimestamp = cachedTs[chunkEventCounts[nChunks - 1] - 1];
            }
        }
    }

    /**
     * Read the chunk index at {@link #indexOffset} with the given record size,
     * validating every offset against the real file.
     */
    private void readChunkIndex(int entrySize) throws IOException {
        long expectedRegion = (long) nChunks * entrySize;
        if (expectedRegion > Integer.MAX_VALUE) {
            throw new IOException("AEDZ index region too large: " + expectedRegion + " bytes");
        }
        chunkOffsets = new long[nChunks];
        chunkEventCounts = new int[nChunks];
        chunkEventStarts = new long[nChunks];

        byte[] indexBuf = new byte[(int) expectedRegion];
        raf.seek(indexOffset);
        raf.readFully(indexBuf);

        long cumEvents = 0;
        for (int i = 0; i < nChunks; i++) {
            long off = getLongLE(indexBuf, i * entrySize);
            int nEv = getIntLE(indexBuf, i * entrySize + 8);
            if (off < 0) {
                throw new IOException("Negative chunk " + i + " offset: " + off);
            }
            if (nEv < 0) {
                throw new IOException("Negative chunk " + i + " event count: " + nEv);
            }
            if (nEv == 0) {
                throw new IOException("Chunk " + i + " has zero events");
            }
            if (nEv > MAX_CHUNK_EVENTS) {
                throw new IOException("Chunk " + i + " event count " + nEv + " exceeds max " + MAX_CHUNK_EVENTS);
            }
            // Chunk must lie within the data region strictly before the index.
            // Worst-case chunk header is 8 + 32 = 40 bytes; the actual compressed
            // size is read at decode time and bounded there too. Here we require
            // at least the header to fit.
            long chunkHdr = off + 8L + PLANE_SIZES_LEN;
            if (chunkHdr > indexOffset) {
                throw new IOException("Chunk " + i + " header at " + off + " overruns index at " + indexOffset);
            }
            // No overflow when accumulating event starts.
            long newCum = cumEvents + nEv;
            if (newCum < cumEvents) {
                throw new IOException("Event count overflow at chunk " + i);
            }
            chunkOffsets[i] = off;
            chunkEventCounts[i] = nEv;
            chunkEventStarts[i] = cumEvents;
            cumEvents = newCum;
        }
    }

    private long sumChunkEvents() {
        long s = 0;
        for (int i = 0; i < nChunks; i++) {
            s += chunkEventCounts[i];
        }
        return s;
    }

    /**
     * Decode a chunk by index, caching the result. All lengths are validated
     * against the real file and the chunk header before allocation or
     * decompression.
     */
    private void decodeChunk(int chunkIdx) throws IOException {
        if (chunkIdx == cachedChunkIndex) {
            return; // already cached
        }
        if (chunkIdx < 0 || chunkIdx >= nChunks) {
            throw new IOException("Chunk index " + chunkIdx + " out of range [0," + nChunks + ")");
        }
        long fileLen = raf.length();
        long off = chunkOffsets[chunkIdx];

        // Chunk header: n_events(4) + compressed_size(4).
        if (off + 8 > indexOffset) {
            throw new IOException("Chunk " + chunkIdx + " header at " + off + " overruns index at " + indexOffset);
        }
        raf.seek(off);
        byte[] hdr = new byte[8];
        raf.readFully(hdr);
        int nEvents = getIntLE(hdr, 0);
        int compressedSize = getIntLE(hdr, 4);

        if (nEvents != chunkEventCounts[chunkIdx]) {
            throw new IOException("Chunk " + chunkIdx + " header n_events " + nEvents
                    + " disagrees with index " + chunkEventCounts[chunkIdx]);
        }
        if (compressedSize < PLANE_SIZES_LEN) {
            throw new IOException("Chunk " + chunkIdx + " compressed_size " + compressedSize
                    + " too small to hold plane sizes (" + PLANE_SIZES_LEN + ")");
        }
        // compressed_size must fit in the region before the index/footer.
        long chunkEnd = off + 8L + compressedSize;
        if (chunkEnd > indexOffset) {
            throw new IOException("Chunk " + chunkIdx + " payload ends at " + chunkEnd + " beyond index " + indexOffset);
        }

        byte[] chunkData = new byte[compressedSize];
        raf.readFully(chunkData);

        // Read and validate the 8 plane sizes.
        int[] planeSizes = new int[N_PLANES];
        int planeSum = 0;
        for (int p = 0; p < N_PLANES; p++) {
            int ps = getIntLE(chunkData, p * 4);
            if (ps < 0) {
                throw new IOException("Chunk " + chunkIdx + " plane " + p + " negative size " + ps);
            }
            planeSizes[p] = ps;
            planeSum += ps;
            if (planeSum < 0) {
                throw new IOException("Chunk " + chunkIdx + " plane size sum overflow");
            }
        }
        // Plane-size sum must be exactly the compressed payload after the 32-byte sizes block.
        long payloadLen = compressedSize - PLANE_SIZES_LEN;
        if ((long) planeSum != payloadLen) {
            throw new IOException("Chunk " + chunkIdx + " plane sizes sum " + planeSum
                    + " != compressed payload " + payloadLen);
        }

        // Decompress each plane; the decompressed length must be exactly nEvents.
        int offset = PLANE_SIZES_LEN;
        byte[][] planes = new byte[N_PLANES][];
        for (int p = 0; p < N_PLANES; p++) {
            int ps = planeSizes[p];
            if (offset + ps > chunkData.length) {
                throw new IOException("Chunk " + chunkIdx + " plane " + p + " data overruns chunk");
            }
            if (ps == 0) {
                // a zero-length compressed plane is only valid if nEvents == 0, which we forbid;
                // reject to avoid a silent zero-filled plane.
                throw new IOException("Chunk " + chunkIdx + " plane " + p + " has zero compressed length");
            }
            byte[] compPlane = new byte[ps];
            System.arraycopy(chunkData, offset, compPlane, 0, ps);
            planes[p] = decompressPlane(compPlane, nEvents, chunkIdx, p);
            offset += ps;
        }

        // Un-transpose addresses: little-endian byte planes, plane0 = LSB.
        int[] addr = new int[nEvents];
        for (int i = 0; i < nEvents; i++) {
            addr[i] = (planes[0][i] & 0xFF)
                    | ((planes[1][i] & 0xFF) << 8)
                    | ((planes[2][i] & 0xFF) << 16)
                    | ((planes[3][i] & 0xFF) << 24);
        }

        // Un-transpose delta-timestamps.
        int[] dts = new int[nEvents];
        for (int i = 0; i < nEvents; i++) {
            dts[i] = (planes[4][i] & 0xFF)
                    | ((planes[5][i] & 0xFF) << 8)
                    | ((planes[6][i] & 0xFF) << 16)
                    | ((planes[7][i] & 0xFF) << 24);
        }

        // Reconstruct timestamps from the cumulative sum of deltas (wrap naturally).
        int[] ts = new int[nEvents];
        ts[0] = dts[0];
        for (int i = 1; i < nEvents; i++) {
            ts[i] = ts[i - 1] + dts[i];
        }

        cachedAddr = addr;
        cachedTs = ts;
        cachedChunkIndex = chunkIdx;
    }

    /**
     * Decompress one plane and require the decompressed length to be exactly
     * {@code expected} (the chunk event count), rejecting any mismatch.
     */
    private byte[] decompressPlane(byte[] compPlane, int expected, int chunkIdx, int planeIdx) throws IOException {
        byte[] result = new byte[expected];
        try {
            long actual = Zstd.decompress(result, compPlane);
            if (actual != expected) {
                throw new IOException("Chunk " + chunkIdx + " plane " + planeIdx
                        + " decompressed to " + actual + " bytes, expected " + expected);
            }
        } catch (RuntimeException e) {
            // zstd-jni surfaces corrupt streams as RuntimeException; wrap into IOException
            // so malformed data is never delivered as an unchecked exception.
            throw new IOException("Chunk " + chunkIdx + " plane " + planeIdx + " decompression failed: " + e.getMessage(), e);
        }
        return result;
    }

    /**
     * Find which chunk contains event number n (0-based global event index).
     */
    private int findChunkForEvent(long eventIdx) {
        if (nChunks == 0 || eventIdx < 0) {
            return 0;
        }
        int lo = 0, hi = nChunks - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (chunkEventStarts[mid] <= eventIdx) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    /**
     * Read a single event at the given global position.
     */
    private void readEvent(long eventIdx, int[] addrOut, int[] tsOut, int outIdx) throws IOException {
        int chunkIdx = findChunkForEvent(eventIdx);
        decodeChunk(chunkIdx);
        int localIdx = (int) (eventIdx - chunkEventStarts[chunkIdx]);
        addrOut[outIdx] = cachedAddr[localIdx];
        tsOut[outIdx] = cachedTs[localIdx];
    }

    // ── AEFileInputStreamInterface implementation ──

    @Override
    public synchronized AEPacketRaw readPacketByNumber(int n) throws IOException {
        if (!firstReadCompleted) {
            firstReadCompleted = true;
            support.firePropertyChange(AEInputStream.EVENT_INIT, 0, 0);
        }

        int an = Math.abs(n);
        int cap = packet.getCapacity();
        if (an > cap) {
            an = cap;
            n = (n > 0) ? cap : -cap;
        }

        // An empty recording has no events to read regardless of the repeat flag:
        // return an empty packet rather than rewind-looping into a read.
        if (totalEvents == 0) {
            AEPacketRaw empty = new AEPacketRaw(0);
            return empty;
        }

        int[] addr = packet.getAddresses();
        int[] ts = packet.getTimestamps();
        long oldPosition = position;
        int count = 0;

        try {
            if (n > 0) {
                for (int i = 0; i < n; i++) {
                    if (position >= totalEvents) {
                        if (repeat) {
                            position = markIn;
                            support.firePropertyChange(AEInputStream.EVENT_REWOUND, totalEvents, 0);
                        } else {
                            throw new EOFException("end of AEDZ file");
                        }
                    }
                    if (markOutSet && position >= markOut) {
                        if (repeat) {
                            position = markIn;
                            support.firePropertyChange(AEInputStream.EVENT_REWOUND, markOut, markIn);
                        } else {
                            break;
                        }
                    }
                    readEvent(position, addr, ts, i);
                    mostRecentTimestamp = ts[i];
                    currentStartTimestamp = ts[i];
                    position++;
                    count++;
                }
            } else {
                n = -n;
                for (int i = 0; i < n; i++) {
                    if (position <= 0) {
                        if (repeat) {
                            position = markOutSet ? markOut : totalEvents;
                        } else {
                            break;
                        }
                    }
                    position--;
                    readEvent(position, addr, ts, i);
                    mostRecentTimestamp = ts[i];
                    currentStartTimestamp = ts[i];
                    count++;
                }
            }
        } catch (EOFException e) {
            // end of file
        }

        packet.setNumEvents(count);
        support.firePropertyChange(AEInputStream.EVENT_POSITION, oldPosition, position);
        return packet;
    }

    @Override
    public synchronized AEPacketRaw readPacketByTime(int dt) throws IOException {
        if (!firstReadCompleted) {
            firstReadCompleted = true;
            support.firePropertyChange(AEInputStream.EVENT_INIT, 0, 0);
        }

        int endTimestamp = currentStartTimestamp + dt;
        currentStartTimestamp = endTimestamp;

        int[] addr = packet.getAddresses();
        int[] ts = packet.getTimestamps();
        long oldPosition = position;
        int count = 0;
        boolean rewound = false;

        try {
            if (dt > 0) {
                while (count < addr.length) {
                    // Handle end of file / mark out
                    if (position >= totalEvents || (markOutSet && position >= markOut)) {
                        if (repeat) {
                            long oldPos = position;
                            position = markIn;
                            // Reset time tracking to match rewound position
                            if (position < totalEvents) {
                                int chunkIdx = findChunkForEvent(position);
                                decodeChunk(chunkIdx);
                                int localIdx = (int) (position - chunkEventStarts[chunkIdx]);
                                currentStartTimestamp = cachedTs[localIdx];
                                endTimestamp = currentStartTimestamp + dt;
                                mostRecentTimestamp = currentStartTimestamp;
                            }
                            rewound = true;
                            support.firePropertyChange(AEInputStream.EVENT_REWOUND, oldPos, position);
                            break; // return empty packet on rewind, next call gets fresh data
                        } else {
                            break;
                        }
                    }

                    // Peek at next event timestamp
                    int chunkIdx = findChunkForEvent(position);
                    decodeChunk(chunkIdx);
                    int localIdx = (int) (position - chunkEventStarts[chunkIdx]);
                    int eventTs = cachedTs[localIdx];

                    if (eventTs > endTimestamp) {
                        break;
                    }

                    addr[count] = cachedAddr[localIdx];
                    ts[count] = eventTs;
                    mostRecentTimestamp = eventTs;
                    position++;
                    count++;
                }

                // If no events matched the time window but we haven't reached EOF,
                // snap currentStartTimestamp to the next event's timestamp.
                if (count == 0 && !rewound && position < totalEvents) {
                    int chunkIdx = findChunkForEvent(position);
                    decodeChunk(chunkIdx);
                    int localIdx = (int) (position - chunkEventStarts[chunkIdx]);
                    currentStartTimestamp = cachedTs[localIdx];
                }
            } else {
                // Read backwards
                while (count < addr.length && position > 0) {
                    position--;
                    int chunkIdx = findChunkForEvent(position);
                    decodeChunk(chunkIdx);
                    int localIdx = (int) (position - chunkEventStarts[chunkIdx]);
                    int eventTs = cachedTs[localIdx];

                    if (eventTs < endTimestamp) {
                        position++;
                        break;
                    }

                    addr[count] = cachedAddr[localIdx];
                    ts[count] = eventTs;
                    mostRecentTimestamp = eventTs;
                    count++;
                }
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            log.log(Level.WARNING, "Exception reading AEDZ: " + e.toString(), e);
        }

        packet.setNumEvents(count);
        support.firePropertyChange(AEInputStream.EVENT_POSITION, oldPosition, position);
        return packet;
    }

    @Override
    public boolean isNonMonotonicTimeExceptionsChecked() {
        return nonMonotonicTimeExceptionsChecked;
    }

    @Override
    public void setNonMonotonicTimeExceptionsChecked(boolean yes) {
        this.nonMonotonicTimeExceptionsChecked = yes;
    }

    @Override
    public long getAbsoluteStartingTimeMs() {
        return absoluteStartingTimeMs;
    }

    @Override
    public ZoneId getZoneId() {
        return zoneId;
    }

    @Override
    public int getDurationUs() {
        return lastTimestamp - firstTimestamp;
    }

    @Override
    public int getFirstTimestamp() {
        return firstTimestamp;
    }

    @Override
    public PropertyChangeSupport getSupport() {
        return support;
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public int getLastTimestamp() {
        return lastTimestamp;
    }

    @Override
    public int getMostRecentTimestamp() {
        return mostRecentTimestamp;
    }

    @Override
    public void setFile(File file) {
        this.file = file;
    }

    @Override
    public int getTimestampResetBitmask() {
        return timestampResetBitmask;
    }

    @Override
    public void setTimestampResetBitmask(int timestampResetBitmask) {
        this.timestampResetBitmask = timestampResetBitmask;
    }

    @Override
    public void close() throws IOException {
        if (raf != null) {
            raf.close();
            raf = null;
        }
    }

    @Override
    public int getCurrentStartTimestamp() {
        return currentStartTimestamp;
    }

    @Override
    public void setCurrentStartTimestamp(int currentStartTimestamp) {
        this.currentStartTimestamp = currentStartTimestamp;
    }

    @Override
    public boolean toggleMarker() {
        Long pos = Long.valueOf(position);
        if (otherMarks.contains(pos)) {
            otherMarks.remove(pos);
            return false;
        } else {
            otherMarks.add(pos);
            return true;
        }
    }

    @Override
    public boolean jumpToNextMarker() {
        Long higher = otherMarks.higher(position);
        if (higher != null) {
            position(higher);
            return true;
        }
        return false;
    }

    @Override
    public boolean jumpToPrevMarker() {
        Long lower = otherMarks.lower(position);
        if (lower != null) {
            position(lower);
            return true;
        }
        return false;
    }

    // ── InputDataFileInterface implementation ──

    @Override
    public float getFractionalPosition() {
        if (totalEvents == 0) {
            return 0;
        }
        return (float) position / totalEvents;
    }

    @Override
    public long position() {
        return position;
    }

    @Override
    public synchronized void position(long n) {
        if (n < 0) {
            n = 0;
        }
        if (n > totalEvents) {
            n = totalEvents;
        }
        this.position = n;
        // Update mostRecentTimestamp if possible
        if (n > 0 && n <= totalEvents) {
            try {
                long idx = n - 1;
                int chunkIdx = findChunkForEvent(idx);
                decodeChunk(chunkIdx);
                int localIdx = (int) (idx - chunkEventStarts[chunkIdx]);
                mostRecentTimestamp = cachedTs[localIdx];
                currentStartTimestamp = mostRecentTimestamp;
            } catch (IOException e) {
                log.warning("Error seeking in AEDZ file: " + e);
            }
        }
    }

    @Override
    public synchronized void rewind() throws IOException {
        long oldPosition = position;
        position = markIn;
        if (totalEvents > 0 && position < totalEvents) {
            int chunkIdx = findChunkForEvent(position);
            decodeChunk(chunkIdx);
            int localIdx = (int) (position - chunkEventStarts[chunkIdx]);
            mostRecentTimestamp = cachedTs[localIdx];
            currentStartTimestamp = mostRecentTimestamp;
        } else {
            mostRecentTimestamp = firstTimestamp;
            currentStartTimestamp = firstTimestamp;
        }
        support.firePropertyChange(AEInputStream.EVENT_REWOUND, oldPosition, position);
    }

    @Override
    public void setFractionalPosition(float frac) {
        position((long) (frac * totalEvents));
    }

    @Override
    public long size() {
        return totalEvents;
    }

    @Override
    public void clearMarks() {
        markIn = 0;
        markOut = Long.MAX_VALUE;
        markInSet = false;
        markOutSet = false;
        otherMarks.clear();
    }

    @Override
    public long setMarkIn() {
        if (markInSet) {
            markIn = 0;
            markInSet = false;
        } else {
            markIn = position;
            markInSet = true;
        }
        return markIn;
    }

    @Override
    public long setMarkOut() {
        if (markOutSet) {
            markOut = Long.MAX_VALUE;
            markOutSet = false;
        } else {
            markOut = position;
            markOutSet = true;
        }
        return markOut;
    }

    @Override
    public long getMarkInPosition() {
        return markIn;
    }

    @Override
    public long getMarkOutPosition() {
        return markOutSet ? markOut : totalEvents;
    }

    @Override
    public boolean isMarkInSet() {
        return markInSet;
    }

    @Override
    public boolean isMarkOutSet() {
        return markOutSet;
    }

    @Override
    public void setRepeat(boolean repeat) {
        this.repeat = repeat;
    }

    @Override
    public boolean isRepeat() {
        return repeat;
    }

    // ── Helper methods ──

    private static int getIntLE(byte[] buf, int offset) {
        return (buf[offset] & 0xFF)
                | ((buf[offset + 1] & 0xFF) << 8)
                | ((buf[offset + 2] & 0xFF) << 16)
                | ((buf[offset + 3] & 0xFF) << 24);
    }

    private static long getLongLE(byte[] buf, int offset) {
        return (buf[offset] & 0xFFL)
                | ((buf[offset + 1] & 0xFFL) << 8)
                | ((buf[offset + 2] & 0xFFL) << 16)
                | ((buf[offset + 3] & 0xFFL) << 24)
                | ((buf[offset + 4] & 0xFFL) << 32)
                | ((buf[offset + 5] & 0xFFL) << 40)
                | ((buf[offset + 6] & 0xFFL) << 48)
                | ((buf[offset + 7] & 0xFFL) << 56);
    }

    /**
     * Parse the absolute starting time from the filename, similar to
     * AEFileInputStream.
     */
    private void parseAbsoluteStartingTime() {
        if (file == null) {
            return;
        }
        String name = file.getName();
        try {
            // Try to parse date from filename like ClassName-2007-04-04T11-32-21-0700-0.aedz
            int tIdx = name.indexOf('-');
            if (tIdx > 0) {
                String dateStr = name.substring(tIdx + 1);
                // Remove suffix like -0.aedz
                int lastDot = dateStr.lastIndexOf('.');
                if (lastDot > 0) {
                    dateStr = dateStr.substring(0, lastDot);
                }
                // Remove trailing -N suffix number
                int lastDash = dateStr.lastIndexOf('-');
                if (lastDash > 0) {
                    String suffix = dateStr.substring(lastDash + 1);
                    try {
                        Integer.parseInt(suffix);
                        dateStr = dateStr.substring(0, lastDash);
                    } catch (NumberFormatException e) {
                        // not a number suffix, keep as is
                    }
                }
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ssZ")
                            .withResolverStyle(ResolverStyle.LENIENT);
                    ZonedDateTime zdt = ZonedDateTime.parse(dateStr, formatter);
                    absoluteStartingTimeMs = zdt.toInstant().toEpochMilli();
                    zoneId = zdt.getZone();
                } catch (DateTimeParseException e) {
                    parseTimeFromHeader();
                }
            }
        } catch (Exception e) {
            // ignore parsing errors
        }
    }

    /**
     * Try to parse the creation time from the AEDAT header.
     */
    private void parseTimeFromHeader() {
        if (aedatHeader == null) {
            return;
        }
        String headerStr = new String(aedatHeader);
        String[] lines = headerStr.split("\\r\\n|\\n|\\r");
        for (String line : lines) {
            if (line.contains("Creation time: System.currentTimeMillis()")) {
                try {
                    String[] parts = line.split("System.currentTimeMillis\\(\\)\\s*");
                    if (parts.length > 1) {
                        absoluteStartingTimeMs = Long.parseLong(parts[1].trim());
                    }
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
    }

    /**
     * Returns the original AEDAT-2 header stored in the AEDZ file.
     *
     * @return the header bytes
     */
    public byte[] getAedatHeader() {
        return aedatHeader;
    }

    /** @return the number of compressed chunks in this file */
    public int getNumChunksRead() {
        return nChunks;
    }

    @Override
    public String toString() {
        return String.format("AEDZInputStream: %s, %d events, %d chunks, duration=%d us",
                file != null ? file.getName() : "null",
                totalEvents, nChunks, getDurationUs());
    }
}
