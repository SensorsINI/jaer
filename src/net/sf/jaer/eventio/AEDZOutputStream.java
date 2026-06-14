/*
 * AEDZOutputStream.java
 *
 * Writes AEDZ format files: byte-transposed + zstd compressed AEDAT2 events.
 * The AEDZ format stores events in chunks of CHUNK_EVENTS (65536) events each,
 * compressed with zstd level 1 after byte-transposition for better compression.
 *
 * The format preserves the original AEDAT2 header and supports streaming
 * (write during recording). On close, a chunk index and footer are appended
 * and the header is patched with final event/chunk counts.
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
 * version of AEDAT2 using byte-transpose + zstd compression, designed for
 * streaming (write during recording).
 *
 * @author jAER
 */
public class AEDZOutputStream implements AEDataFile {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    /** Magic bytes identifying AEDZ format */
    private static final byte[] MAGIC = new byte[]{'A', 'E', 'D', 'Z', 0x00, 0x01, 0x00, 0x00};
    /** Footer magic */
    private static final byte[] FOOTER_MAGIC = new byte[]{'A', 'E', 'D', 'Z'};
    /** Number of events per chunk */
    private static final int CHUNK_EVENTS = 65536;
    /** Zstd compression level */
    private static final int ZSTD_LEVEL = 1;

    private final FileChannel channel;
    private final FileOutputStream fos;

    // Buffered events
    private int[] addrBuf = new int[CHUNK_EVENTS];
    private int[] tsBuf = new int[CHUNK_EVENTS];
    private int bufCount = 0;

    // Tracking
    private long totalEvents = 0;
    private int nChunks = 0;
    private final ArrayList<long[]> chunkIndex = new ArrayList<>(); // [offset, n_events]
    private final CRC32 crc32 = new CRC32();

    // Header info
    private byte[] aedatHeader;
    private long headerPatchOffset; // offset where n_events/n_chunks are written

    // Timing
    private Date startDate;
    private Date endDate;
    private long startTimeMs;
    private long endTimeMs;
    private final EngineeringFormat eng = new EngineeringFormat();

    /**
     * Creates a new AEDZOutputStream. Writes the AEDAT-compatible ASCII header
     * first (captured as aedat_header), then writes the AEDZ binary header.
     *
     * @param fos the FileOutputStream to write to
     * @param chip the AEChip providing header info
     * @throws IOException on write error
     */
    public AEDZOutputStream(FileOutputStream fos, AEChip chip) throws IOException {
        this.fos = fos;
        this.channel = fos.getChannel();
        this.startDate = new Date();
        this.startTimeMs = System.currentTimeMillis();

        // Build the AEDAT header in memory
        aedatHeader = buildAedatHeader(chip);

        // Write AEDZ binary header
        writeAedzHeader();
    }

    /**
     * Build the standard AEDAT2 header as bytes. Uses a real AEFileOutputStream
     * writing to a ByteArrayOutputStream to capture the exact header bytes
     * (including chip-specific header lines).
     */
    private byte[] buildAedatHeader(AEChip chip) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
        // AEFileOutputStream constructor writes the full header, including chip-specific lines.
        // When the underlying stream is not a FileOutputStream, channel/byteBuf remain null,
        // but the header is still written via DataOutputStream methods.
        new AEFileOutputStream(baos, chip, "2.0");
        return baos.toByteArray();
    }

    /**
     * Write the AEDZ file header.
     */
    private void writeAedzHeader() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(8 + 8 + 4 + 1 + 4 + aedatHeader.length + 4);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        // Magic (8 bytes)
        buf.put(MAGIC);

        // n_events placeholder (8 bytes) - will be patched on close
        headerPatchOffset = buf.position() + 0; // relative to start; actual file position = this value
        buf.putLong(0L);

        // n_chunks placeholder (4 bytes) - will be patched on close
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
        channel.write(buf);
    }

    /**
     * Writes a packet of raw events. Events are buffered and flushed as
     * complete chunks.
     *
     * @param ae the raw event packet to write
     * @throws IOException on write error
     */
    public void writePacket(AEPacketRaw ae) throws IOException {
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

            // Update CRC32 as if writing AEDAT2 (big-endian addr+ts pairs)
            updateCRC(addr[i], ts[i]);

            if (bufCount >= CHUNK_EVENTS) {
                flushChunk();
            }
        }
    }

    /**
     * Update CRC32 with one event as it would appear in AEDAT2 format
     * (big-endian int32 addr + int32 ts).
     */
    private final byte[] crcBuf = new byte[8];

    private void updateCRC(int addr, int ts) {
        // Big-endian addr
        crcBuf[0] = (byte) ((addr >> 24) & 0xFF);
        crcBuf[1] = (byte) ((addr >> 16) & 0xFF);
        crcBuf[2] = (byte) ((addr >> 8) & 0xFF);
        crcBuf[3] = (byte) (addr & 0xFF);
        // Big-endian ts
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

        // Byte-transpose addresses (convert to little-endian, then transpose)
        byte[] addrPlane0 = new byte[n];
        byte[] addrPlane1 = new byte[n];
        byte[] addrPlane2 = new byte[n];
        byte[] addrPlane3 = new byte[n];

        for (int i = 0; i < n; i++) {
            // Extract bytes in little-endian order (LSB first) from the Java int.
            // Java int 0x12345678 -> LE bytes: 0x78, 0x56, 0x34, 0x12
            int a = addrBuf[i];
            addrPlane0[i] = (byte) (a & 0xFF);
            addrPlane1[i] = (byte) ((a >> 8) & 0xFF);
            addrPlane2[i] = (byte) ((a >> 16) & 0xFF);
            addrPlane3[i] = (byte) ((a >> 24) & 0xFF);
        }

        // Delta-encode timestamps
        int[] dts = new int[n];
        dts[0] = tsBuf[0]; // absolute base timestamp
        for (int i = 1; i < n; i++) {
            dts[i] = tsBuf[i] - tsBuf[i - 1]; // uint32 subtraction wraps naturally
        }

        // Byte-transpose delta-timestamps (as little-endian)
        byte[] dtsPlane0 = new byte[n];
        byte[] dtsPlane1 = new byte[n];
        byte[] dtsPlane2 = new byte[n];
        byte[] dtsPlane3 = new byte[n];

        for (int i = 0; i < n; i++) {
            // Extract bytes in little-endian order from the delta-timestamp int
            int d = dts[i];
            dtsPlane0[i] = (byte) (d & 0xFF);
            dtsPlane1[i] = (byte) ((d >> 8) & 0xFF);
            dtsPlane2[i] = (byte) ((d >> 16) & 0xFF);
            dtsPlane3[i] = (byte) ((d >> 24) & 0xFF);
        }

        // Compress each of the 8 planes
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
        int chunkDataSize = 8 * 4 + totalCompressed; // 8 plane sizes + compressed data

        ByteBuffer chunkBuf = ByteBuffer.allocate(4 + 4 + chunkDataSize);
        chunkBuf.order(ByteOrder.LITTLE_ENDIAN);

        // Chunk header
        chunkBuf.putInt(n); // n_events
        chunkBuf.putInt(chunkDataSize); // compressed_size

        // Plane sizes
        for (int p = 0; p < 8; p++) {
            chunkBuf.putInt(compressed[p].length);
        }

        // Compressed plane data
        for (int p = 0; p < 8; p++) {
            chunkBuf.put(compressed[p]);
        }

        chunkBuf.flip();
        channel.write(chunkBuf);

        // Record chunk index entry
        chunkIndex.add(new long[]{chunkOffset, n});

        totalEvents += n;
        nChunks++;
        bufCount = 0;
    }

    /**
     * Close the stream. Flushes remaining events, writes chunk index, footer,
     * and patches the header with final counts.
     *
     * @throws IOException on write error
     */
    public void close() throws IOException {
        // Flush remaining buffered events
        flushChunk();

        // Write chunk index
        long indexOffset = channel.position();
        ByteBuffer indexBuf = ByteBuffer.allocate(nChunks * (8 + 4));
        indexBuf.order(ByteOrder.LITTLE_ENDIAN);
        for (long[] entry : chunkIndex) {
            indexBuf.putLong(entry[0]); // offset
            indexBuf.putInt((int) entry[1]); // n_events
        }
        indexBuf.flip();
        channel.write(indexBuf);

        // Write summary block (no summaries for streaming)
        long summaryOffset = channel.position();
        ByteBuffer summaryBuf = ByteBuffer.allocate(4);
        summaryBuf.order(ByteOrder.LITTLE_ENDIAN);
        summaryBuf.putInt(0); // summary_len = 0
        summaryBuf.flip();
        channel.write(summaryBuf);

        // Write footer
        ByteBuffer footerBuf = ByteBuffer.allocate(8 + 8 + 4 + 4);
        footerBuf.order(ByteOrder.LITTLE_ENDIAN);
        footerBuf.putLong(indexOffset);
        footerBuf.putLong(summaryOffset);
        footerBuf.putInt((int) (crc32.getValue() & 0xFFFFFFFFL));
        footerBuf.put(FOOTER_MAGIC);
        footerBuf.flip();
        channel.write(footerBuf);

        // Patch header: seek back to write n_events and n_chunks
        // headerPatchOffset is 8 (after MAGIC)
        channel.position(8);
        ByteBuffer patchBuf = ByteBuffer.allocate(8 + 4);
        patchBuf.order(ByteOrder.LITTLE_ENDIAN);
        patchBuf.putLong(totalEvents);
        patchBuf.putInt(nChunks);
        patchBuf.flip();
        channel.write(patchBuf);

        // Close the channel and stream
        channel.close();
        fos.close();

        endDate = new Date();
        endTimeMs = System.currentTimeMillis();
        log.info(String.format("wrote %s", toString()));
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

    @Override
    public String toString() {
        float durationM = (float) getDurationMs() / 1000 / 60f;
        return String.format("AEDZOutputStream: %s events in %d chunks, %s minutes",
                eng.format(getNumEvents()), nChunks, eng.format(durationM));
    }
}
