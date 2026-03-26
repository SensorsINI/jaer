/*
 * AEDZInputStream.java
 *
 * Reads AEDZ format files: byte-transposed + zstd compressed AEDAT2 events.
 * The AEDZ format stores events in chunks, each compressed with zstd after
 * byte-transposition. This reader decompresses chunks on demand and caches
 * them for performance.
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
public class AEDZInputStream implements AEFileInputStreamInterface {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    private static final byte[] MAGIC = new byte[]{'A', 'E', 'D', 'Z', 0x00, 0x01, 0x00, 0x00};
    private static final byte[] FOOTER_MAGIC = new byte[]{'A', 'E', 'D', 'Z'};

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
        readFile();
        parseAbsoluteStartingTime();
    }

    /**
     * Read and parse the entire AEDZ file structure.
     */
    private void readFile() throws IOException {
        // Read and verify magic
        byte[] magic = new byte[8];
        raf.readFully(magic);
        for (int i = 0; i < 8; i++) {
            if (magic[i] != MAGIC[i]) {
                throw new IOException("Not an AEDZ file: bad magic");
            }
        }

        // Read header fields (little-endian)
        byte[] buf8 = new byte[8];
        byte[] buf4 = new byte[4];

        raf.readFully(buf8);
        totalEvents = getLongLE(buf8, 0);

        raf.readFully(buf4);
        nChunks = getIntLE(buf4, 0);

        flags = raf.readByte();

        raf.readFully(buf4);
        int aedatHeaderLen = getIntLE(buf4, 0);

        aedatHeader = new byte[aedatHeaderLen];
        raf.readFully(aedatHeader);

        raf.readFully(buf4);
        int trailingLen = getIntLE(buf4, 0);
        if (trailingLen > 0) {
            trailing = new byte[trailingLen];
            raf.readFully(trailing);
        } else {
            trailing = new byte[0];
        }

        // Read footer to get index offset
        long fileLen = raf.length();
        raf.seek(fileLen - 4 - 4 - 8 - 8); // footer_magic(4) + crc32(4) + summary_offset(8) + index_offset(8)
        byte[] footerBuf = new byte[4 + 4 + 8 + 8];
        // Actually: index_offset(8) + summary_offset(8) + crc32(4) + footer_magic(4)
        raf.readFully(footerBuf);
        indexOffset = getLongLE(footerBuf, 0);
        summaryOffset = getLongLE(footerBuf, 8);
        crc32Value = getIntLE(footerBuf, 16);

        // Verify footer magic
        if (footerBuf[20] != 'A' || footerBuf[21] != 'E' || footerBuf[22] != 'D' || footerBuf[23] != 'Z') {
            throw new IOException("Bad AEDZ footer magic");
        }

        // Read chunk index
        chunkOffsets = new long[nChunks];
        chunkEventCounts = new int[nChunks];
        chunkEventStarts = new long[nChunks];

        raf.seek(indexOffset);
        byte[] indexBuf = new byte[nChunks * 12]; // 8 + 4 per chunk
        raf.readFully(indexBuf);

        long cumEvents = 0;
        for (int i = 0; i < nChunks; i++) {
            chunkOffsets[i] = getLongLE(indexBuf, i * 12);
            chunkEventCounts[i] = getIntLE(indexBuf, i * 12 + 8);
            chunkEventStarts[i] = cumEvents;
            cumEvents += chunkEventCounts[i];
        }

        // Read first and last timestamps
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
     * Decode a chunk by index, caching the result.
     */
    private void decodeChunk(int chunkIdx) throws IOException {
        if (chunkIdx == cachedChunkIndex) {
            return; // already cached
        }

        raf.seek(chunkOffsets[chunkIdx]);

        byte[] hdr = new byte[8];
        raf.readFully(hdr);
        int nEvents = getIntLE(hdr, 0);
        int compressedSize = getIntLE(hdr, 4);

        byte[] chunkData = new byte[compressedSize];
        raf.readFully(chunkData);

        // Read 8 plane sizes
        int[] planeSizes = new int[8];
        for (int p = 0; p < 8; p++) {
            planeSizes[p] = getIntLE(chunkData, p * 4);
        }

        // Decompress each plane
        int offset = 32; // 8 * 4 bytes for plane sizes
        byte[][] planes = new byte[8][];
        for (int p = 0; p < 8; p++) {
            byte[] compPlane = new byte[planeSizes[p]];
            System.arraycopy(chunkData, offset, compPlane, 0, planeSizes[p]);
            planes[p] = Zstd.decompress(compPlane, nEvents);
            offset += planeSizes[p];
        }

        // Un-transpose addresses: reconstruct Java ints from LE byte planes
        // Plane 0 has LSB (byte 0), plane 3 has MSB (byte 3)
        // Reconstruct: int = byte0 | (byte1 << 8) | (byte2 << 16) | (byte3 << 24)
        cachedAddr = new int[nEvents];
        for (int i = 0; i < nEvents; i++) {
            cachedAddr[i] = (planes[0][i] & 0xFF)
                    | ((planes[1][i] & 0xFF) << 8)
                    | ((planes[2][i] & 0xFF) << 16)
                    | ((planes[3][i] & 0xFF) << 24);
        }

        // Un-transpose delta-timestamps (same LE byte order)
        int[] dts = new int[nEvents];
        for (int i = 0; i < nEvents; i++) {
            dts[i] = (planes[4][i] & 0xFF)
                    | ((planes[5][i] & 0xFF) << 8)
                    | ((planes[6][i] & 0xFF) << 16)
                    | ((planes[7][i] & 0xFF) << 24);
        }

        // Reconstruct timestamps from cumulative sum of deltas
        cachedTs = new int[nEvents];
        cachedTs[0] = dts[0]; // absolute base timestamp
        for (int i = 1; i < nEvents; i++) {
            cachedTs[i] = cachedTs[i - 1] + dts[i]; // wraps naturally
        }

        cachedChunkIndex = chunkIdx;
    }

    /**
     * Find which chunk contains event number n (0-based global event index).
     */
    private int findChunkForEvent(long eventIdx) {
        // Binary search on chunkEventStarts
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
                // This prevents the pulsing problem where the time window drifts
                // away from the actual event timestamps.
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
        if (totalEvents == 0) return 0;
        return (float) position / totalEvents;
    }

    @Override
    public long position() {
        return position;
    }

    @Override
    public synchronized void position(long n) {
        if (n < 0) n = 0;
        if (n > totalEvents) n = totalEvents;
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
        if (file == null) return;
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
                // Try to remove serial number suffix (e.g. -XXXXX)
                // Parse with the jAER date format
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ssZ")
                            .withResolverStyle(ResolverStyle.LENIENT);
                    ZonedDateTime zdt = ZonedDateTime.parse(dateStr, formatter);
                    absoluteStartingTimeMs = zdt.toInstant().toEpochMilli();
                    zoneId = zdt.getZone();
                } catch (DateTimeParseException e) {
                    // Could not parse, try from header
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
        if (aedatHeader == null) return;
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
     * Returns the original AEDAT2 header stored in the AEDZ file.
     *
     * @return the header bytes
     */
    public byte[] getAedatHeader() {
        return aedatHeader;
    }

    @Override
    public String toString() {
        return String.format("AEDZInputStream: %s, %d events, %d chunks, duration=%d us",
                file != null ? file.getName() : "null",
                totalEvents, nChunks, getDurationUs());
    }
}
