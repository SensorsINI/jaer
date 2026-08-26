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
    static final int FOOTER_LEN = 24;
    static final int CHUNK_HEADER_LEN = 8 + PLANE_SIZES_LEN;

    /** Upper bound on a single chunk's event count; matches the writer's fixed chunk size. */
    static final int MAX_CHUNK_EVENTS = 65536;
    /** Upper bound on decompressed plane length (== chunk event count). */
    static final int MAX_PLANE_LEN = MAX_CHUNK_EVENTS;
    /** Independent caps for file-supplied metadata regions. */
    static final int MAX_AEDAT_HEADER_BYTES = 8 * 1024 * 1024;
    static final int MAX_TRAILING_METADATA_BYTES = 8 * 1024 * 1024;
    static final int MAX_SUMMARY_METADATA_BYTES = 8 * 1024 * 1024;
    /** Caps index allocations even when a sparse file makes large offsets plausible. */
    static final int MAX_CHUNKS = 262144;
    static final long MAX_INDEX_BYTES = 4L * 1024 * 1024;
    /** Zstd output is bounded separately; these caps bound attacker-controlled compressed input. */
    static final int MAX_COMPRESSED_PLANE_BYTES = 128 * 1024;
    static final int MAX_COMPRESSED_CHUNK_BYTES
            = PLANE_SIZES_LEN + N_PLANES * MAX_COMPRESSED_PLANE_BYTES;

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
    private long dataOffset;
    private int crc32Value;

    // Payload/file statistics. Compressed bytes are only the eight plane payloads.
    private long onDiskFileSizeBytes;
    private long compressedPlanePayloadBytes;

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
    /** Set only by marksInitialize(); preview streams close without touching preferences. */
    private boolean marksInitialized = false;

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
            parseAbsoluteStartingTime();
        } catch (IOException e) {
            closeAfterConstructorFailure(e);
            throw e;
        } catch (RuntimeException e) {
            // Parsing/decompression implementation exceptions are part of the
            // malformed-input boundary, never an unchecked public-file result.
            IOException wrapped = new IOException("Malformed AEDZ file: " + e.getMessage(), e);
            closeAfterConstructorFailure(wrapped);
            throw wrapped;
        } catch (Error e) {
            // Preserve VM/linkage/assertion Error identity, but constructor
            // ownership still requires deterministic descriptor release.
            closeAfterConstructorFailure(e);
            throw e;
        }
    }

    private void closeAfterConstructorFailure(Throwable failure) {
        RandomAccessFile opened = raf;
        raf = null;
        if (opened == null) {
            return;
        }
        try {
            opened.close();
        } catch (IOException | RuntimeException | Error closeFailure) {
            if (closeFailure != failure) {
                failure.addSuppressed(closeFailure);
            }
        }
    }

    /**
     * Read and parse the entire AEDZ file structure, validating every length and
     * offset against the real file before allocation or decompression.
     */
    private void readFile() throws IOException {
        long fileLen = raf.length();
        onDiskFileSizeBytes = fileLen;

        // ---- Minimum structural length: fixed header and trailing length (29),
        // ---- summary length (4), and footer (24) = 57 bytes.
        if (fileLen < 8 + 8 + 4 + 1 + 4 + 4 + 4 + FOOTER_LEN) {
            throw new IOException("AEDZ file too short: " + fileLen + " bytes");
        }
        long footerStart = fileLen - FOOTER_LEN;

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
        if (aedatHeaderLen > MAX_AEDAT_HEADER_BYTES) {
            throw new IOException("AEDAT header length " + aedatHeaderLen
                    + " exceeds maximum " + MAX_AEDAT_HEADER_BYTES);
        }
        requireRegion(raf.getFilePointer(), aedatHeaderLen, footerStart, "AEDAT header");
        aedatHeader = new byte[aedatHeaderLen];
        raf.readFully(aedatHeader);

        if (raf.getFilePointer() > footerStart - 4) {
            throw new IOException("AEDZ file has no complete trailing metadata length before footer");
        }
        raf.readFully(buf4);
        int trailingLen = getIntLE(buf4, 0);
        if (trailingLen < 0) {
            throw new IOException("Negative trailing length: " + trailingLen);
        }
        if (trailingLen > MAX_TRAILING_METADATA_BYTES) {
            throw new IOException("Trailing metadata length " + trailingLen
                    + " exceeds maximum " + MAX_TRAILING_METADATA_BYTES);
        }
        requireRegion(raf.getFilePointer(), trailingLen, footerStart, "trailing metadata");
        if (trailingLen > 0) {
            trailing = new byte[trailingLen];
            raf.readFully(trailing);
        } else {
            trailing = new byte[0];
        }
        dataOffset = raf.getFilePointer();

        // ---- Footer (in the last 24 bytes).
        raf.seek(footerStart);
        byte[] footerBuf = new byte[FOOTER_LEN];
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
        if (indexOffset < dataOffset) {
            throw new IOException("AEDZ index offset " + indexOffset
                    + " overlaps header/metadata ending at " + dataOffset);
        }
        // Subtraction-based checks cannot wrap for hostile near-Long.MAX offsets.
        if (summaryOffset > footerStart - 4) {
            throw new IOException("AEDZ summary offset " + summaryOffset
                    + " leaves no complete summary length before footer at " + footerStart);
        }

        // ---- Chunk event totals must be nonnegative and internally consistent
        // ---- with the header counts before we trust nChunks for index sizing.
        if (totalEvents < 0) {
            throw new IOException("Negative total event count: " + totalEvents);
        }
        if (nChunks < 0) {
            throw new IOException("Negative chunk count: " + nChunks);
        }
        if (nChunks > MAX_CHUNKS) {
            throw new IOException("AEDZ chunk count " + nChunks
                    + " exceeds maximum " + MAX_CHUNKS);
        }

        // ---- Validate the summary length without allocating or reading its body.
        raf.seek(summaryOffset);
        raf.readFully(buf4);
        int summaryLen = getIntLE(buf4, 0);
        if (summaryLen < 0) {
            throw new IOException("Negative AEDZ summary metadata length: " + summaryLen);
        }
        if (summaryLen > MAX_SUMMARY_METADATA_BYTES) {
            throw new IOException("AEDZ summary metadata length " + summaryLen
                    + " exceeds maximum " + MAX_SUMMARY_METADATA_BYTES);
        }
        long summaryBytesInFile = footerStart - (summaryOffset + 4);
        if ((long) summaryLen != summaryBytesInFile) {
            throw new IOException("AEDZ summary metadata length " + summaryLen
                    + " does not match " + summaryBytesInFile + " bytes before footer");
        }

        // ---- Read the chunk index; accept exactly the legacy 12-byte or the
        // ---- extended 20-byte record, rejecting anything else deterministically.
        long indexRegionLen = summaryOffset - indexOffset;
        if (indexRegionLen > MAX_INDEX_BYTES) {
            throw new IOException("AEDZ index region " + indexRegionLen
                    + " bytes exceeds maximum " + MAX_INDEX_BYTES);
        }
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
        long indexedEvents = sumChunkEvents();
        if (indexedEvents != totalEvents) {
            throw new IOException("AEDZ totalEvents=" + totalEvents
                    + " inconsistent with chunk event sum=" + indexedEvents);
        }
        compressedPlanePayloadBytes = calculateCompressedPlanePayloadBytes();

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
        if (expectedRegion > MAX_INDEX_BYTES) {
            throw new IOException("AEDZ index region " + expectedRegion
                    + " bytes exceeds maximum " + MAX_INDEX_BYTES);
        }
        chunkOffsets = new long[nChunks];
        chunkEventCounts = new int[nChunks];
        chunkEventStarts = new long[nChunks];

        // Random access needs the three compact primitive arrays, but index bytes
        // themselves are consumed one record at a time rather than duplicated in heap.
        byte[] entryBuf = new byte[entrySize];
        raf.seek(indexOffset);

        long cumEvents = 0;
        long previousOffset = -1;
        for (int i = 0; i < nChunks; i++) {
            raf.readFully(entryBuf);
            long off = getLongLE(entryBuf, 0);
            int nEv = getIntLE(entryBuf, 8);
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
            if (off < dataOffset) {
                throw new IOException("Chunk " + i + " offset " + off
                        + " overlaps header/metadata ending at " + dataOffset);
            }
            // At least the fixed chunk header must fit before the index. Use
            // subtraction after ordering checks so hostile offsets cannot wrap.
            if (off > indexOffset || CHUNK_HEADER_LEN > indexOffset - off) {
                throw new IOException("Chunk " + i + " header at " + off + " overruns index at " + indexOffset);
            }
            if (i > 0) {
                if (off <= previousOffset) {
                    throw new IOException("Chunk " + i + " offset " + off
                            + " is not after previous chunk offset " + previousOffset);
                }
                if (off - previousOffset < CHUNK_HEADER_LEN) {
                    throw new IOException("Chunk " + (i - 1) + " header at " + previousOffset
                            + " overlaps chunk " + i + " at " + off);
                }
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
            previousOffset = off;
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
     * Reads only fixed chunk metadata to sum the eight compressed plane lengths.
     * The 8-byte chunk header and 32-byte plane-size table are deliberately not
     * counted as compressed payload.
     */
    private long calculateCompressedPlanePayloadBytes() throws IOException {
        long total = 0;
        byte[] header = new byte[8];
        byte[] sizes = new byte[PLANE_SIZES_LEN];
        for (int chunk = 0; chunk < nChunks; chunk++) {
            long offset = chunkOffsets[chunk];
            raf.seek(offset);
            raf.readFully(header);
            int events = getIntLE(header, 0);
            int compressedSize = getIntLE(header, 4);
            if (events != chunkEventCounts[chunk]) {
                throw new IOException("Chunk " + chunk + " header n_events " + events
                        + " disagrees with index " + chunkEventCounts[chunk]);
            }
            if (compressedSize < PLANE_SIZES_LEN || offset + 8L + compressedSize > indexOffset) {
                throw new IOException("Chunk " + chunk + " has invalid compressed_size " + compressedSize);
            }
            raf.readFully(sizes);
            long planeSum = 0;
            for (int plane = 0; plane < N_PLANES; plane++) {
                int length = getIntLE(sizes, plane * 4);
                if (length < 0) {
                    throw new IOException("Chunk " + chunk + " plane " + plane + " negative size " + length);
                }
                planeSum += length;
            }
            if (planeSum != compressedSize - PLANE_SIZES_LEN) {
                throw new IOException("Chunk " + chunk + " plane sizes sum " + planeSum
                        + " != compressed payload " + (compressedSize - PLANE_SIZES_LEN));
            }
            total += planeSum;
        }
        return total;
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
        long off = chunkOffsets[chunkIdx];
        long chunkLimit = chunkIdx + 1 < nChunks ? chunkOffsets[chunkIdx + 1] : indexOffset;

        // Chunk header: n_events(4) + compressed_size(4).
        if (off > chunkLimit || 8 > chunkLimit - off) {
            throw new IOException("Chunk " + chunkIdx + " header at " + off
                    + " overruns chunk boundary " + chunkLimit);
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
        if (compressedSize > MAX_COMPRESSED_CHUNK_BYTES) {
            throw new IOException("Chunk " + chunkIdx + " compressed_size " + compressedSize
                    + " exceeds maximum " + MAX_COMPRESSED_CHUNK_BYTES);
        }
        long payloadStart = off + 8;
        if ((long) compressedSize > chunkLimit - payloadStart) {
            throw new IOException("Chunk " + chunkIdx + " compressed payload of " + compressedSize
                    + " bytes overruns chunk boundary " + chunkLimit);
        }

        // Read and validate the 8 plane sizes.
        byte[] planeSizeBuf = new byte[PLANE_SIZES_LEN];
        raf.readFully(planeSizeBuf);
        int[] planeSizes = new int[N_PLANES];
        long planeSum = 0;
        for (int p = 0; p < N_PLANES; p++) {
            int ps = getIntLE(planeSizeBuf, p * 4);
            if (ps <= 0) {
                throw new IOException("Chunk " + chunkIdx + " plane " + p
                        + " has non-positive compressed size " + ps);
            }
            if (ps > MAX_COMPRESSED_PLANE_BYTES) {
                throw new IOException("Chunk " + chunkIdx + " plane " + p + " compressed size " + ps
                        + " exceeds maximum " + MAX_COMPRESSED_PLANE_BYTES);
            }
            planeSizes[p] = ps;
            planeSum += ps;
        }
        // Plane-size sum must be exactly the compressed payload after the 32-byte sizes block.
        long payloadLen = compressedSize - PLANE_SIZES_LEN;
        if (planeSum != payloadLen) {
            throw new IOException("Chunk " + chunkIdx + " plane sizes sum " + planeSum
                    + " != compressed payload " + payloadLen);
        }

        // Decompress each plane; the decompressed length must be exactly nEvents.
        byte[][] planes = new byte[N_PLANES][];
        for (int p = 0; p < N_PLANES; p++) {
            int ps = planeSizes[p];
            byte[] compPlane = new byte[ps];
            raf.readFully(compPlane);
            planes[p] = decompressPlane(compPlane, nEvents, chunkIdx, p);
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
        if (eventIdx < 0 || eventIdx >= totalEvents) {
            throw new IOException("Event index " + eventIdx + " out of range [0," + totalEvents + ")");
        }
        int chunkIdx = findChunkForEvent(eventIdx);
        decodeChunk(chunkIdx);
        int localIdx = (int) (eventIdx - chunkEventStarts[chunkIdx]);
        if (localIdx < 0 || localIdx >= cachedAddr.length || localIdx >= cachedTs.length) {
            throw new IOException("Event index " + eventIdx + " maps outside chunk " + chunkIdx
                    + " at local index " + localIdx);
        }
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

        int cap = packet.getCapacity();
        int requested = (int) Math.min(Math.abs((long) n), (long) cap);

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
                for (int i = 0; i < requested; i++) {
                    long lower = lowerReadBound();
                    long upper = upperReadBound();
                    if (position < lower) {
                        position = lower;
                    }
                    if (position >= upper) {
                        if (!repeat || upper <= lower) {
                            break;
                        }
                        long boundary = position;
                        position = lower;
                        support.firePropertyChange(AEInputStream.EVENT_REWOUND, boundary, position);
                    }
                    readEvent(position, addr, ts, i);
                    mostRecentTimestamp = ts[i];
                    currentStartTimestamp = ts[i];
                    position++;
                    count++;
                }
            } else if (n < 0) {
                for (int i = 0; i < requested; i++) {
                    long lower = lowerReadBound();
                    long upper = upperReadBound();
                    if (position > upper) {
                        position = upper;
                    }
                    if (position <= lower) {
                        if (!repeat || upper <= lower) {
                            break;
                        }
                        long boundary = position;
                        position = upper;
                        support.firePropertyChange(AEInputStream.EVENT_REWOUND, boundary, position);
                    }
                    long eventPosition = position - 1;
                    if (eventPosition < lower) {
                        break;
                    }
                    readEvent(eventPosition, addr, ts, i);
                    position = eventPosition;
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

        int windowStartTimestamp = currentStartTimestamp;
        int endTimestamp = windowStartTimestamp + dt;
        currentStartTimestamp = endTimestamp;

        int[] addr = packet.getAddresses();
        int[] ts = packet.getTimestamps();
        long oldPosition = position;
        int count = 0;
        boolean rewound = false;

        if (totalEvents == 0 || dt == 0) {
            packet.setNumEvents(0);
            support.firePropertyChange(AEInputStream.EVENT_POSITION, oldPosition, position);
            return packet;
        }

        try {
            if (dt > 0) {
                while (count < addr.length) {
                    long lower = lowerReadBound();
                    long upper = upperReadBound();
                    if (position < lower) {
                        position = lower;
                    }
                    if (position >= upper) {
                        if (!repeat || upper <= lower) {
                            break;
                        }
                        long oldPos = position;
                        position = lower;
                        if (position < upper) {
                            currentStartTimestamp = timestampAt(position);
                            mostRecentTimestamp = currentStartTimestamp;
                        }
                        rewound = true;
                        support.firePropertyChange(AEInputStream.EVENT_REWOUND, oldPos, position);
                        break; // do not mix events from opposite sides of a rewind
                    }

                    // Peek at next event timestamp
                    int eventTs = timestampAt(position);

                    if (!isInForwardTimeWindow(windowStartTimestamp, eventTs, dt)) {
                        break;
                    }
                    if (count > 0) {
                        int previous = ts[count - 1];
                        if (isForwardTimestampWrap(previous, eventTs)) {
                            support.firePropertyChange(AEInputStream.EVENT_WRAPPED_TIME, previous, eventTs);
                        } else if (nonMonotonicTimeExceptionsChecked && eventTs < previous) {
                            support.firePropertyChange(AEInputStream.EVENT_NON_MONOTONIC_TIMESTAMP, previous, eventTs);
                            break;
                        }
                    }

                    readEvent(position, addr, ts, count);
                    mostRecentTimestamp = eventTs;
                    position++;
                    count++;
                }

                // If no events matched the time window but we haven't reached EOF,
                // snap currentStartTimestamp to the next event's timestamp.
                if (count == 0 && !rewound && position < upperReadBound()) {
                    currentStartTimestamp = timestampAt(position);
                }
            } else {
                // Read backwards
                while (count < addr.length) {
                    long lower = lowerReadBound();
                    long upper = upperReadBound();
                    if (position > upper) {
                        position = upper;
                    }
                    if (position <= lower) {
                        if (!repeat || upper <= lower) {
                            break;
                        }
                        long oldPos = position;
                        position = upper;
                        if (position > lower) {
                            currentStartTimestamp = timestampAt(position - 1);
                            mostRecentTimestamp = currentStartTimestamp;
                        }
                        rewound = true;
                        support.firePropertyChange(AEInputStream.EVENT_REWOUND, oldPos, position);
                        break;
                    }

                    long eventPosition = position - 1;
                    int eventTs = timestampAt(eventPosition);
                    if (!isInBackwardTimeWindow(windowStartTimestamp, eventTs, dt)) {
                        break;
                    }
                    if (count > 0) {
                        int previous = ts[count - 1];
                        if (isBackwardTimestampWrap(previous, eventTs)) {
                            support.firePropertyChange(AEInputStream.EVENT_WRAPPED_TIME, previous, eventTs);
                        } else if (nonMonotonicTimeExceptionsChecked && eventTs > previous) {
                            support.firePropertyChange(AEInputStream.EVENT_NON_MONOTONIC_TIMESTAMP, previous, eventTs);
                            break;
                        }
                    }

                    readEvent(eventPosition, addr, ts, count);
                    position = eventPosition;
                    mostRecentTimestamp = eventTs;
                    count++;
                }
            }
        } catch (RuntimeException e) {
            throw new IOException("Unexpected AEDZ time-read failure at event " + position, e);
        }

        packet.setNumEvents(count);
        support.firePropertyChange(AEInputStream.EVENT_POSITION, oldPosition, position);
        return packet;
    }

    private long lowerReadBound() {
        return markInSet ? markIn : 0;
    }

    private long upperReadBound() {
        return markOutSet ? Math.min(markOut, totalEvents) : totalEvents;
    }

    private int timestampAt(long eventPosition) throws IOException {
        if (eventPosition < 0 || eventPosition >= totalEvents) {
            throw new IOException("Timestamp event index " + eventPosition
                    + " out of range [0," + totalEvents + ")");
        }
        int chunkIdx = findChunkForEvent(eventPosition);
        decodeChunk(chunkIdx);
        int localIdx = (int) (eventPosition - chunkEventStarts[chunkIdx]);
        if (localIdx < 0 || localIdx >= cachedTs.length) {
            throw new IOException("Timestamp event index " + eventPosition
                    + " maps outside chunk " + chunkIdx);
        }
        return cachedTs[localIdx];
    }

    private static boolean isInForwardTimeWindow(int start, int timestamp, int dt) {
        long elapsed = Integer.toUnsignedLong(timestamp - start);
        return elapsed <= (long) dt;
    }

    private static boolean isInBackwardTimeWindow(int start, int timestamp, int dt) {
        long elapsed = Integer.toUnsignedLong(start - timestamp);
        return elapsed <= -(long) dt;
    }

    private static boolean isForwardTimestampWrap(int previous, int timestamp) {
        return previous > 0 && timestamp <= 0;
    }

    private static boolean isBackwardTimestampWrap(int previous, int timestamp) {
        return previous < 0 && timestamp >= 0;
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
    public synchronized void close() throws IOException {
        if (marksInitialized) {
            try {
                persistMarks();
            } catch (RuntimeException e) {
                log.warning("Could not persist AEDZ marks: " + e);
            }
        }
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
    public synchronized boolean toggleMarker() {
        Long pos = Long.valueOf(position);
        if (otherMarks.remove(pos)) {
            support.firePropertyChange(AEInputStream.EVENT_MARK_TOGGLED, pos, null);
            return false;
        }
        otherMarks.add(pos);
        support.firePropertyChange(AEInputStream.EVENT_MARK_TOGGLED, null, pos);
        return true;
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
        long oldPosition = position;
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
        } else if (totalEvents > 0) {
            mostRecentTimestamp = firstTimestamp;
            currentStartTimestamp = firstTimestamp;
        }
        support.firePropertyChange(AEInputStream.EVENT_REPOSITIONED, oldPosition, position);
        if (oldPosition != position) {
            support.firePropertyChange(AEInputStream.EVENT_POSITION, oldPosition, position);
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

    /** Restores event-index markers from the shared AEDAT preference cache. */
    @Override
    public synchronized void marksInitialize() {
        marksInitialized = true;
        AEFileInputStream.Marks saved = AEFileInputStream.marksGetForFile(file);
        if (saved == null || totalEvents <= 0) {
            clearMarks();
            return;
        }
        markIn = clampMark(saved.markIn, 0, totalEvents);
        markInSet = markIn > 0;
        long savedOut = saved.markOut;
        if (savedOut == Long.MAX_VALUE || savedOut < 0 || savedOut >= totalEvents) {
            markOut = Long.MAX_VALUE;
            markOutSet = false;
        } else {
            markOut = clampMark(savedOut, markIn, totalEvents);
            markOutSet = markOut > markIn;
        }
        otherMarks.clear();
        if (saved.otherMarks != null) {
            for (Long marker : saved.otherMarks) {
                if (marker != null && marker >= 0 && marker < totalEvents) {
                    otherMarks.add(marker);
                }
            }
        }
        position = markIn;
        AEFileInputStream.Marks applied = snapshotMarks();
        support.firePropertyChange(AEInputStream.EVENT_MARKS_LOADED, null, applied);
        log.info(String.format("Restored AEDZ marks for %s: %s", file.getName(), applied));
    }

    /** Current IN/OUT/ordinary markers for AEPlayer slider restoration. */
    public synchronized AEFileInputStream.Marks getPlaybackMarks() {
        return snapshotMarks();
    }

    private void persistMarks() {
        if (file == null) {
            return;
        }
        if (isMarkInSet() || isMarkOutSet() || !otherMarks.isEmpty()) {
            AEFileInputStream.marksPutForFile(file, snapshotMarks());
        } else {
            AEFileInputStream.marksPutForFile(file, null);
        }
    }

    private AEFileInputStream.Marks snapshotMarks() {
        AEFileInputStream.Marks result = new AEFileInputStream.Marks();
        result.markIn = markIn;
        result.markOut = markOut;
        result.otherMarks.addAll(otherMarks);
        return result;
    }

    private static long clampMark(long value, long min, long max) {
        return Math.max(min, Math.min(value, max));
    }

    @Override
    public synchronized void clearMarks() {
        AEFileInputStream.Marks oldMarks = snapshotMarks();
        markIn = 0;
        markOut = Long.MAX_VALUE;
        markInSet = false;
        markOutSet = false;
        otherMarks.clear();
        support.firePropertyChange(AEInputStream.EVENT_MARKS_CLEARED, oldMarks, snapshotMarks());
    }

    @Override
    public synchronized long setMarkIn() {
        long here = position;
        if (markOutSet && here >= markOut) {
            return markIn;
        }
        long old = markIn;
        markIn = here;
        markInSet = here > 0;
        support.firePropertyChange(AEInputStream.EVENT_MARK_IN_SET, old, markIn);
        return markIn;
    }

    @Override
    public synchronized long setMarkOut() {
        long here = position;
        if (here <= lowerReadBound()) {
            return getMarkOutPosition();
        }
        long old = getMarkOutPosition();
        markOut = here;
        markOutSet = true;
        support.firePropertyChange(AEInputStream.EVENT_MARK_OUT_SET, old, markOut);
        return markOut;
    }

    @Override
    public synchronized long getMarkInPosition() {
        return markIn;
    }

    @Override
    public synchronized long getMarkOutPosition() {
        return markOutSet ? markOut : totalEvents;
    }

    @Override
    public synchronized boolean isMarkInSet() {
        return markInSet;
    }

    @Override
    public synchronized boolean isMarkOutSet() {
        return markOutSet;
    }

    @Override
    public synchronized void setRepeat(boolean repeat) {
        this.repeat = repeat;
    }

    @Override
    public synchronized boolean isRepeat() {
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

    private static void requireRegion(long start, long length, long limit, String context) throws IOException {
        if (start < 0 || length < 0 || start > limit || length > limit - start) {
            throw new IOException(context + " length " + length
                    + " exceeds remaining file region " + Math.max(0, limit - start));
        }
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

    public long getOnDiskFileSizeBytes() {
        return onDiskFileSizeBytes;
    }

    public long getUncompressedPayloadBytes() {
        return 8L * totalEvents;
    }

    public long getCompressedPlanePayloadBytes() {
        return compressedPlanePayloadBytes;
    }

    public double getCompressedPayloadPercentage() {
        long uncompressed = getUncompressedPayloadBytes();
        return uncompressed == 0 ? 0 : 100.0 * compressedPlanePayloadBytes / uncompressed;
    }

    public double getUncompressedToCompressedRatio() {
        return compressedPlanePayloadBytes == 0 ? 1.0
                : getUncompressedPayloadBytes() / (double) compressedPlanePayloadBytes;
    }

    public String formatCompressionSummary() {
        return String.format(
                "File size: %,d bytes%nPlane payloads: %,d compressed / %,d uncompressed bytes "
                + "(%.3f%% of uncompressed, %.3f:1 uncompressed:compressed)",
                onDiskFileSizeBytes, compressedPlanePayloadBytes, getUncompressedPayloadBytes(),
                getCompressedPayloadPercentage(), getUncompressedToCompressedRatio());
    }

    @Override
    public String getFileInfo() {
        String path = file != null ? file.getAbsolutePath() : "";
        return String.format("%s%nAEDZ zstd level %d: %,d events in %,d chunks, duration=%,d us%n%s",
                path, AEDZOutputStream.ZSTD_LEVEL, totalEvents, nChunks, getDurationUs(),
                formatCompressionSummary());
    }

    @Override
    public String toString() {
        return String.format("AEDZInputStream: %s, %d events, %d chunks, duration=%d us",
                file != null ? file.getName() : "null",
                totalEvents, nChunks, getDurationUs());
    }
}
