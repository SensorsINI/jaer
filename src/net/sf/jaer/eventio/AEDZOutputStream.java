/*
 * AEDZOutputStream.java
 *
 * Writes AEDZ format files: byte-transposed + zstd compressed AEDAT-2 events.
 * The AEDZ format stores events in chunks of CHUNK_EVENTS (65536) events each,
 * compressed with zstd level 1 after byte-transposition for better compression.
 *
 * The format preserves the original AEDAT-2 ASCII header and supports streaming
 * (write during recording). On close, a chunk index, a summary block and a
 * footer are appended and the header is patched with the final event/chunk
 * counts. The file is laid out as:
 *
 *   [header: magic(8) n_events(8) n_chunks(4) flags(1) aedat_header_len(4)
 *            aedat_header trailing_len(4) trailing]
 *   [chunk]* : n_events(4) compressed_size(4) 8x plane_size(4) 8x compressed_plane
 *   [index: 12 or 20 bytes per chunk: offset(8) n_events(4) (+first_ts,last_ts)]
 *   [summary: summary_len(4)]
 *   [footer: index_offset(8) summary_offset(8) crc32(4) magic(4)]
 *
 * All multi-byte fields are little-endian.
 */
package net.sf.jaer.eventio;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Logger;
import java.util.zip.CRC32;

import com.github.luben.zstd.Zstd;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.util.EngineeringFormat;

/**
 * Output stream that writes AEDZ compressed format files. AEDZ is a compressed
 * version of AEDAT-2 using byte-transpose + zstd compression, designed for
 * streaming (write during recording). Events are buffered in fixed-size chunks
 * and flushed as complete chunks; on {@link #close()} any partial final chunk is
 * flushed, then the chunk index, summary and footer are written and the header
 * patched with the final counts. The whole recording is never held in memory —
 * only up to one chunk of 65536 events is buffered at a time.
 *
 * <p>The format is compatible with the SciDVS jAER branch's writer if and only
 * if the exact byte layout above is honoured; the embedded AEDAT header is the
 * standard AEDAT-2 ASCII header (including the chip-specific preference lines)
 * captured once at construction.
 *
 * <p>The API is backward-compatible: callers that cannot supply a configuration
 * snapshot use {@link #AEDZOutputStream(FileOutputStream, AEChip)} and the
 * embedded header is captured from the chip at construction.
 *
 * @author jAER
 */
public class AEDZOutputStream implements AEDataFile, java.io.Closeable {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    /** Magic bytes identifying AEDZ format */
    static final byte[] MAGIC = new byte[]{'A', 'E', 'D', 'Z', 0x00, 0x01, 0x00, 0x00};
    /** Footer magic */
    static final byte[] FOOTER_MAGIC = new byte[]{'A', 'E', 'D', 'Z'};
    /** Number of events per chunk */
    static final int CHUNK_EVENTS = 65536;
    /** Zstd compression level */
    static final int ZSTD_LEVEL = 1;
    /** Size of a legacy (12-byte) chunk index entry and an extended (20-byte) entry. */
    static final int INDEX_ENTRY_LEGACY = 12, INDEX_ENTRY_EXTENDED = 20;

    private final FileChannel channel;
    private final FileOutputStream fos;

    // Buffered events (at most one chunk is ever held in memory)
    private int[] addrBuf = new int[CHUNK_EVENTS];
    private int[] tsBuf = new int[CHUNK_EVENTS];
    private int bufCount = 0;
    private boolean closed = false;

    // Tracking
    private long totalEvents = 0;
    private int nChunks = 0;
    private long firstTs = 0;
    private long lastTs = 0;
    private final ArrayList<long[]> chunkIndex = new ArrayList<>(); // [offset, n_events, first_ts, last_ts]
    private final CRC32 crc32 = new CRC32();

    // Header info
    private byte[] aedatHeader;
    private final long headerPatchOffset = 8; // n_events follows MAGIC(8)

    // Timing
    private Date startDate;
    private Date endDate;
    private long startTimeMs;
    private long endTimeMs;
    private final EngineeringFormat eng = new EngineeringFormat();

    /**
     * Creates a new AEDZOutputStream writing the standard version of the
     * embedded AEDAT header captured from the given chip.
     *
     * @param fos the FileOutputStream to write to
     * @param chip the AEChip providing header info (may be {@code null})
     * @throws IOException on write error
     */
    public AEDZOutputStream(FileOutputStream fos, AEChip chip) throws IOException {
        this(fos, chip, null);
    }

    /**
     * Creates a new AEDZOutputStream. If {@code snapshot} is supplied it is used
     * to produce the embedded AEDAT preference entries (a recording-start
     * snapshot reflects the immutable captured values and is never reread at
     * close); otherwise the header is captured from the chip at construction.
     *
     * @param fos the FileOutputStream to write to
     * @param chip the AEChip providing header info (may be {@code null})
     * @param snapshot optional immutable recording-start configuration; may be {@code null}
     * @throws IOException on write error
     */
    public AEDZOutputStream(FileOutputStream fos, AEChip chip, RecordingConfigurationSnapshot snapshot) throws IOException {
        this.fos = fos;
        this.channel = fos.getChannel();
        this.startDate = new Date();
        this.startTimeMs = System.currentTimeMillis();

        // Build the AEDAT-2 header in memory.
        if (snapshot != null) {
            aedatHeader = buildAedatHeaderFromSnapshot(snapshot);
        } else {
            aedatHeader = buildAedatHeader(chip);
        }

        // Write AEDZ binary header.
        writeAedzHeader();
    }

    /**
     * Build the standard AEDAT-2 header as bytes from a real chip, replicating
     * the exact header bytes AEFileOutputStream would produce (including
     * chip-specific header lines). A {@code null} chip yields the minimal
     * standard header without chip lines (needed for headless tests).
     */
    private static byte[] buildAedatHeader(AEChip chip) throws IOException {
        if (chip == null) {
            return buildMinimalAedatHeader();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
        try (AEFileOutputStream os = new AEFileOutputStream(baos, chip, AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT2)) {
            // body written in constructor
        }
        return baos.toByteArray();
    }

    /**
     * Build the standard AEDAT-2 header as bytes from an immutable recording
     * snapshot, so the header preference entries reflect the frozen values and
     * never reread mutable preferences. Serialized directly (the {@code '#'}
     * comment lines, each terminated by CRLF) without any intermediate
     * {@link AEFileOutputStream}; the entry lines are byte-for-byte equivalent to
     * {@link RecordingConfigurationSnapshot#writeLegacyEntries} and are framed by
     * the same recognizable {@code Start/End of Preferences} markers the legacy
     * writer emits, so the embedded AEDZ header reads as a conventional AEDAT-2
     * header.
     */
    private static byte[] buildAedatHeaderFromSnapshot(RecordingConfigurationSnapshot snapshot) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
        writeMinimalAedatHeaderPreamble(baos);
        // Frame the snapshot entries with the same recognizable markers the legacy
        // writer emits, so the embedded AEDZ header is structurally a recognizable
        // AEDAT header (sans chip-class line, which the snapshot does not carry).
        writeCommentLine(baos, "Start of Preferences for this AEChip (search for \"End of Preferences\" to find end of this block)");
        for (SnapshotCodec.Entry e : snapshot.entries()) {
            writeCommentLine(baos, "<entry key=\"" + SnapshotCodec.escape(e.getKey())
                    + "\" value=\"" + SnapshotCodec.escape(e.getValue()) + "\"/>");
        }
        writeCommentLine(baos, "End of Preferences for this AEChip");
        writeMinimalAedatHeaderTrailer(baos);
        return baos.toByteArray();
    }

    /** Write one {@code '#'}-prefixed comment header line terminated by CRLF. */
    private static void writeCommentLine(ByteArrayOutputStream baos, String line) throws IOException {
        baos.write(AEDataFile.COMMENT_CHAR);
        baos.write(line.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        baos.write(AEDataFile.EOL);
    }

    /**
     * Write a standards-shaped AEDAT-2 ASCII header (all mandatory lines except
     * the chip-specific preference block) without requiring a real chip, and
     * with no preference block, suitable for headless tests and snapshot-built
     * headers. Mirrors the line set AEFileOutputStream writes.
     */
    private static byte[] buildMinimalAedatHeader() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
        writeMinimalAedatHeaderPreamble(baos);
        writeMinimalAedatHeaderTrailer(baos);
        return baos.toByteArray();
    }

    /** Write the standard lines that precede any chip-specific preference block. */
    private static void writeMinimalAedatHeaderPreamble(ByteArrayOutputStream baos) throws IOException {
        String[] lines = {
            "!AER-DAT" + AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT2,
            " This is a raw AE data file - do not edit",
            " Data format is int32 address, int32 timestamp (8 bytes total), repeated for each event",
            " Timestamps tick: " + net.sf.jaer.aemonitor.AEConstants.TICK_DEFAULT_US + " us",
            " Creation date: " + new Date(),
            " Creation time: System.currentTimeMillis() " + System.currentTimeMillis(),
            " User name: " + System.getProperty("user.name"),
        };
        for (String line : lines) {
            writeCommentLine(baos, line);
        }
    }

    /** Write the standard lines that terminate the AEDAT header after preferences. */
    private static void writeMinimalAedatHeaderTrailer(ByteArrayOutputStream baos) throws IOException {
        writeCommentLine(baos, AEDataFile.DATA_START_TIME_SYSTEMCURRENT_TIME_MILLIS + System.currentTimeMillis());
        writeCommentLine(baos, AEDataFile.END_OF_HEADER_STRING);
    }

    /**
     * Write the AEDZ file header.
     */
    private void writeAedzHeader() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(8 + 8 + 4 + 1 + 4 + aedatHeader.length + 4);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        // Magic (8 bytes)
        buf.put(MAGIC);

        // n_events placeholder (8 bytes) - patched on close
        buf.putLong(0L);

        // n_chunks placeholder (4 bytes) - patched on close
        buf.putInt(0);

        // flags (1 byte) - bit 0 = has_summaries, 0 for streaming
        buf.put((byte) 0);

        // aedat_header_len (4 bytes)
        buf.putInt(aedatHeader.length);

        // aedat_header
        buf.put(aedatHeader);

        // trailing_len (4 bytes) - 0 for live recording
        buf.putInt(0);

        buf.flip();
        writeFully(buf);
    }

    /**
     * Writes a packet of raw events. Events are buffered and flushed as
     * complete chunks.
     *
     * @param ae the raw event packet to write
     * @throws IOException on write error
     */
    public synchronized void writePacket(AEPacketRaw ae) throws IOException {
        if (closed) {
            throw new IOException("AEDZOutputStream is closed");
        }
        if (ae == null) {
            return;
        }
        int n = ae.getNumEvents();
        if (n == 0) {
            return;
        }

        int[] addr = ae.getAddresses();
        int[] ts = ae.getTimestamps();

        for (int i = 0; i < n; i++) {
            addrBuf[bufCount] = addr[i];
            tsBuf[bufCount] = ts[i];
            bufCount++;

            // Update CRC32 as if writing AEDAT-2 (little-endian addr+ts pairs).
            updateCRC(addr[i], ts[i]);

            if (bufCount >= CHUNK_EVENTS) {
                flushChunk();
            }
        }
    }

    /**
     * Update CRC32 with one event as it would appear in AEDAT-2 format
     * (big-endian int32 addr + int32 ts, matching the SciDVS branch writer's
     * convention so a downstream reader that opts into checksum verification is
     * byte-compatible).
     */
    private final byte[] crcBuf = new byte[8];

    private void updateCRC(int addr, int ts) {
        // big-endian addr
        crcBuf[0] = (byte) ((addr >> 24) & 0xFF);
        crcBuf[1] = (byte) ((addr >> 16) & 0xFF);
        crcBuf[2] = (byte) ((addr >> 8) & 0xFF);
        crcBuf[3] = (byte) (addr & 0xFF);
        // big-endian ts
        crcBuf[4] = (byte) ((ts >> 24) & 0xFF);
        crcBuf[5] = (byte) ((ts >> 16) & 0xFF);
        crcBuf[6] = (byte) ((ts >> 8) & 0xFF);
        crcBuf[7] = (byte) (ts & 0xFF);
        crc32.update(crcBuf, 0, 8);
    }

    /**
     * Flush the current buffer as a compressed chunk.
     */
    private void flushChunk() throws IOException {
        if (bufCount == 0) {
            return;
        }

        int n = bufCount;
        long chunkOffset = channel.position();

        // Byte-transpose addresses (little-endian byte planes).
        byte[] addrPlane0 = new byte[n];
        byte[] addrPlane1 = new byte[n];
        byte[] addrPlane2 = new byte[n];
        byte[] addrPlane3 = new byte[n];

        for (int i = 0; i < n; i++) {
            int a = addrBuf[i];
            addrPlane0[i] = (byte) (a & 0xFF);
            addrPlane1[i] = (byte) ((a >> 8) & 0xFF);
            addrPlane2[i] = (byte) ((a >> 16) & 0xFF);
            addrPlane3[i] = (byte) ((a >> 24) & 0xFF);
        }

        // Delta-encode timestamps (uint32 subtraction wraps naturally).
        int[] dts = new int[n];
        dts[0] = tsBuf[0];
        for (int i = 1; i < n; i++) {
            dts[i] = tsBuf[i] - tsBuf[i - 1];
        }

        // Byte-transpose delta-timestamps.
        byte[] dtsPlane0 = new byte[n];
        byte[] dtsPlane1 = new byte[n];
        byte[] dtsPlane2 = new byte[n];
        byte[] dtsPlane3 = new byte[n];

        for (int i = 0; i < n; i++) {
            int d = dts[i];
            dtsPlane0[i] = (byte) (d & 0xFF);
            dtsPlane1[i] = (byte) ((d >> 8) & 0xFF);
            dtsPlane2[i] = (byte) ((d >> 16) & 0xFF);
            dtsPlane3[i] = (byte) ((d >> 24) & 0xFF);
        }

        // Compress each of the 8 planes.
        byte[][] planes = new byte[][]{
            addrPlane0, addrPlane1, addrPlane2, addrPlane3,
            dtsPlane0, dtsPlane1, dtsPlane2, dtsPlane3
        };

        byte[][] compressed = new byte[8][];
        int totalCompressed = 0;
        for (int p = 0; p < 8; p++) {
            compressed[p] = Zstd.compress(planes[p], ZSTD_LEVEL);
            totalCompressed += compressed[p].length;
        }

        // Build chunk data: [8 x uint32 plane sizes] + [8 compressed planes]
        int chunkDataSize = 8 * 4 + totalCompressed;

        ByteBuffer chunkBuf = ByteBuffer.allocate(4 + 4 + chunkDataSize);
        chunkBuf.order(ByteOrder.LITTLE_ENDIAN);

        // Chunk header
        chunkBuf.putInt(n);
        chunkBuf.putInt(chunkDataSize);

        // Plane sizes
        for (int p = 0; p < 8; p++) {
            chunkBuf.putInt(compressed[p].length);
        }

        // Compressed plane data
        for (int p = 0; p < 8; p++) {
            chunkBuf.put(compressed[p]);
        }

        chunkBuf.flip();
        writeFully(chunkBuf);

        if (nChunks == 0) {
            firstTs = tsBuf[0];
        }
        lastTs = tsBuf[n - 1];

        // Record chunk index entry.
        chunkIndex.add(new long[]{chunkOffset, n, tsBuf[0], tsBuf[n - 1]});

        totalEvents += n;
        nChunks++;
        bufCount = 0;
    }

    /**
     * Close the stream. Flushes remaining events, writes the chunk index, the
     * summary block and the footer, then patches the header with the final
     * counts. Idempotent: a second close is a no-op.
     *
     * @throws IOException on write error
     */
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        Throwable failure = null;
        try {
            // Flush remaining buffered events.
            flushChunk();

            // Write chunk index (legacy 12-byte records: offset + n_events, matching
            // the SciDVS branch writer; the reader also accepts the newer 20-byte form).
            long indexOffset = channel.position();
            ByteBuffer indexBuf = ByteBuffer.allocate(nChunks * INDEX_ENTRY_LEGACY);
            indexBuf.order(ByteOrder.LITTLE_ENDIAN);
            for (long[] entry : chunkIndex) {
                indexBuf.putLong(entry[0]); // offset
                indexBuf.putInt((int) entry[1]); // n_events
            }
            indexBuf.flip();
            writeFully(indexBuf);

            // Write summary block (none for streaming).
            long summaryOffset = channel.position();
            ByteBuffer summaryBuf = ByteBuffer.allocate(4);
            summaryBuf.order(ByteOrder.LITTLE_ENDIAN);
            summaryBuf.putInt(0); // summary_len = 0
            summaryBuf.flip();
            writeFully(summaryBuf);
            long footerPosition = channel.position();

            // Patch the header before writing the footer. Successful output is
            // byte-identical, while a patch failure cannot leave footer magic that
            // falsely presents an incompletely finalized file as successful.
            channel.position(headerPatchOffset);
            ByteBuffer patchBuf = ByteBuffer.allocate(8 + 4);
            patchBuf.order(ByteOrder.LITTLE_ENDIAN);
            patchBuf.putLong(totalEvents);
            patchBuf.putInt(nChunks);
            patchBuf.flip();
            writeFully(patchBuf);
            channel.position(footerPosition);

            // Footer is the final successful-finalization write.
            ByteBuffer footerBuf = ByteBuffer.allocate(8 + 8 + 4 + 4);
            footerBuf.order(ByteOrder.LITTLE_ENDIAN);
            footerBuf.putLong(indexOffset);
            footerBuf.putLong(summaryOffset);
            footerBuf.putInt((int) (crc32.getValue() & 0xFFFFFFFFL));
            footerBuf.put(FOOTER_MAGIC);
            footerBuf.flip();
            writeFully(footerBuf);
        } catch (IOException | RuntimeException e) {
            failure = e;
        } finally {
            // The FileOutputStream is the owning layer. Invoke it exactly once;
            // if a custom stream throws before releasing its FileChannel, close
            // that still-open channel as the fallback. Never mask the primary
            // flush/index/summary/header/footer failure.
            try {
                fos.close();
            } catch (IOException | RuntimeException closeFailure) {
                failure = appendFailure(failure, closeFailure);
            }
            if (channel.isOpen()) {
                try {
                    channel.close();
                } catch (IOException | RuntimeException closeFailure) {
                    failure = appendFailure(failure, closeFailure);
                }
            }
        }

        rethrowCloseFailure(failure);

        endDate = new Date();
        endTimeMs = System.currentTimeMillis();
        log.info(String.format("wrote %s", toString()));
    }

    private static Throwable appendFailure(Throwable primary, Throwable next) {
        if (primary == null) {
            return next;
        }
        if (next != primary) {
            primary.addSuppressed(next);
        }
        return primary;
    }

    private static void rethrowCloseFailure(Throwable failure) throws IOException {
        if (failure == null) {
            return;
        }
        if (failure instanceof IOException) {
            throw (IOException) failure;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        throw new IOException("Unexpected AEDZ close failure", failure);
    }

    /**
     * Write the remaining bytes of {@code buf} to the channel, returning only
     * when all bytes are written or an {@link IOException} is thrown.
     */
    private void writeFully(ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) {
            channel.write(buf);
        }
    }

    /**
     * Return number of events written.
     *
     * @return total event count
     */
    public long getNumEvents() {
        return totalEvents;
    }

    /**
     * Return duration in ms.
     *
     * @return duration in milliseconds
     */
    public long getDurationMs() {
        if (endTimeMs > 0) {
            return endTimeMs - startTimeMs;
        } else {
            return System.currentTimeMillis() - startTimeMs;
        }
    }

    public Date getStartDate() {
        return startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    /** @return the first event timestamp written, or 0 if none */
    public long getFirstTimestamp() {
        return firstTs;
    }

    /** @return the last event timestamp written, or 0 if none */
    public long getLastTimestamp() {
        return lastTs;
    }

    /** @return number of compressed chunks written */
    public int getNumChunks() {
        return nChunks;
    }

    @Override
    public String toString() {
        float durationM = (float) getDurationMs() / 1000 / 60f;
        return String.format("AEDZOutputStream: %s events in %d chunks, %s minutes",
                eng.format(getNumEvents()), nChunks, eng.format(durationM));
    }
}
