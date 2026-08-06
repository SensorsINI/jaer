package net.sf.jaer.eventio.aedat4;

import com.github.luben.zstd.Zstd;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.lz4.LZ4FrameOutputStream;
import net.jpountz.lz4.LZ4FrameOutputStream.BLOCKSIZE;
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorInputStream;
import net.sf.jaer.eventio.aedat4.dv.CompressionType;
import net.sf.jaer.eventio.aedat4.dv.IOHeader;

/**
 * AEDAT-4 / DV packet payload compression (LZ4 or ZSTD frame per packet).
 * FlatBuffers itself is not compressed; only the size-prefixed FlatBuffer bytes
 * that follow each 8-byte packet header.
 * <p>
 * LZ4 strategy (speed + DV compatibility):
 * <ul>
 *   <li><b>Write</b> — {@code lz4-java} framed LZ4 with independent blocks
 *       (native/JNI when available) for live high-rate logging.</li>
 *   <li><b>Read</b> — {@code lz4-java} when the frame has independent blocks;
 *       fall back to Commons Compress only for DV <em>dependent-block</em> frames
 *       that {@code lz4-java} rejects. Commons Compress has no fast decompress
 *       path (pure Java, ~30× slower).</li>
 * </ul>
 */
public final class Aedat4Compression {

    /** Default ZSTD level (DV "ZSTD"). */
    private static final int ZSTD_LEVEL = 3;
    /** Higher ZSTD level for {@link CompressionType#ZSTD_HIGH}. */
    private static final int ZSTD_HIGH_LEVEL = 9;

    /** LZ4 frame magic little-endian {@code 0x184D2204}. */
    private static final int LZ4_MAGIC_0 = 0x04;
    private static final int LZ4_MAGIC_1 = 0x22;
    private static final int LZ4_MAGIC_2 = 0x4D;
    private static final int LZ4_MAGIC_3 = 0x18;
    /** FLG bit 5: block independence (1 = independent, 0 = dependent). */
    private static final int LZ4_FLG_BLOCK_INDEPENDENCE = 0x20;

    private Aedat4Compression() {
    }

    public static String nameOf(int compression) {
        switch (compression) {
            case CompressionType.NONE:
                return "NONE";
            case CompressionType.LZ4:
                return "LZ4";
            case CompressionType.LZ4_HIGH:
                return "LZ4_HIGH";
            case CompressionType.ZSTD:
                return "ZSTD";
            case CompressionType.ZSTD_HIGH:
                return "ZSTD_HIGH";
            default:
                return "UNKNOWN(" + compression + ")";
        }
    }

    public static int clamp(int compression) {
        if (compression < CompressionType.NONE || compression > CompressionType.ZSTD_HIGH) {
            return CompressionType.LZ4;
        }
        return compression;
    }

    /**
     * Compresses a size-prefixed FlatBuffer payload for one AEDAT-4 packet.
     *
     * @return original bytes if {@code compression == NONE}
     */
    public static byte[] compress(byte[] uncompressed, int compression) throws IOException {
        compression = clamp(compression);
        if (compression == CompressionType.NONE || uncompressed == null || uncompressed.length == 0) {
            return uncompressed;
        }
        switch (compression) {
            case CompressionType.LZ4:
                return lz4FrameCompress(uncompressed, false);
            case CompressionType.LZ4_HIGH:
                return lz4FrameCompress(uncompressed, true);
            case CompressionType.ZSTD:
                return Zstd.compress(uncompressed, ZSTD_LEVEL);
            case CompressionType.ZSTD_HIGH:
                return Zstd.compress(uncompressed, ZSTD_HIGH_LEVEL);
            default:
                return uncompressed;
        }
    }

    /**
     * Decompresses one AEDAT-4 packet payload to a size-prefixed FlatBuffer.
     */
    public static byte[] decompress(byte[] compressed, int compression) throws IOException {
        compression = clamp(compression);
        if (compression == CompressionType.NONE || compressed == null || compressed.length == 0) {
            return compressed;
        }
        switch (compression) {
            case CompressionType.LZ4:
            case CompressionType.LZ4_HIGH:
                return lz4FrameDecompress(compressed);
            case CompressionType.ZSTD:
            case CompressionType.ZSTD_HIGH:
                long size = Zstd.decompressedSize(compressed);
                if (size <= 0 || size > Integer.MAX_VALUE) {
                    // Fallback when frame size is not stored: grow until success.
                    return Zstd.decompress(compressed, compressed.length * 8);
                }
                return Zstd.decompress(compressed, (int) size);
            default:
                return compressed;
        }
    }

    /** Fast native/JNI framed LZ4 with independent blocks (live logging). */
    private static byte[] lz4FrameCompress(byte[] data, boolean high) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.max(64, data.length / 2));
        // HIGH uses larger blocks for better ratio; both keep BLOCK_INDEPENDENCE set.
        BLOCKSIZE blockSize = high ? BLOCKSIZE.SIZE_1MB : BLOCKSIZE.SIZE_64KB;
        try (OutputStream out = new LZ4FrameOutputStream(baos, blockSize)) {
            out.write(data);
        }
        return baos.toByteArray();
    }

    /**
     * True when the LZ4 frame descriptor clears block independence (DV default).
     * Unknown/short/non-LZ4 buffers return false.
     */
    public static boolean isDependentBlockLz4Frame(byte[] data) {
        return lz4FrameNeedsDependentBlockDecoder(data);
    }

    /**
     * Peeks the first data packet of an AEDAT-4 file and returns true when it uses
     * dependent-block LZ4 (slow Commons Compress path in jAER).
     * Returns false for non-LZ4 files, empty files, or independent-block LZ4.
     */
    public static boolean probeUsesDependentBlockLz4(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "r"); FileChannel ch = raf.getChannel()) {
            ByteBuffer version = ByteBuffer.allocate(Aedat4FileOutputStream.VERSION_LINE.length);
            readFully(ch, version);
            if (!Arrays.equals(version.array(), Aedat4FileOutputStream.VERSION_LINE)) {
                return false;
            }
            ByteBuffer headerBytes = readSizePrefixed(ch);
            IOHeader header = IOHeader.getSizePrefixedRootAsIOHeader(headerBytes);
            int compression = clamp(header.compression());
            if (compression != CompressionType.LZ4 && compression != CompressionType.LZ4_HIGH) {
                return false;
            }
            long dataTablePosition = header.dataTablePosition();
            long fileSize = ch.size();
            long dataEnd = (dataTablePosition >= 0 && dataTablePosition < fileSize) ? dataTablePosition : fileSize;
            if (ch.position() + 8 > dataEnd) {
                return false;
            }
            ByteBuffer packetHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            readFully(ch, packetHeader);
            packetHeader.flip();
            packetHeader.getInt(); // streamId
            int payloadSize = packetHeader.getInt();
            if (payloadSize < 5 || ch.position() + payloadSize > dataEnd) {
                return false;
            }
            ByteBuffer prefix = ByteBuffer.allocate(Math.min(16, payloadSize));
            readFully(ch, prefix);
            return isDependentBlockLz4Frame(prefix.array());
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Prefer {@code lz4-java}; use Commons Compress only for dependent-block frames.
     */
    private static byte[] lz4FrameDecompress(byte[] data) throws IOException {
        if (isDependentBlockLz4Frame(data)) {
            return lz4FrameDecompressCommonsCompress(data);
        }
        try {
            return lz4FrameDecompressLz4Java(data);
        } catch (RuntimeException e) {
            // lz4-java throws RuntimeException when BLOCK_INDEPENDENCE is clear.
            if (isDependentBlockUnsupported(e)) {
                return lz4FrameDecompressCommonsCompress(data);
            }
            throw e;
        }
    }

    private static ByteBuffer readSizePrefixed(FileChannel channel) throws IOException {
        ByteBuffer sizeBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        readFully(channel, sizeBuffer);
        sizeBuffer.flip();
        int size = sizeBuffer.getInt();
        if (size < 0) {
            throw new IOException("Negative FlatBuffer size prefix " + size);
        }
        ByteBuffer payload = ByteBuffer.allocate(size + 4).order(ByteOrder.LITTLE_ENDIAN);
        payload.putInt(size);
        readFully(channel, payload);
        payload.flip();
        return payload;
    }

    private static void readFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                throw new IOException("Unexpected EOF");
            }
        }
    }

    private static byte[] lz4FrameDecompressLz4Java(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.max(64, data.length * 2));
        try (InputStream in = new LZ4FrameInputStream(new ByteArrayInputStream(data))) {
            copy(in, baos);
        }
        return baos.toByteArray();
    }

    private static byte[] lz4FrameDecompressCommonsCompress(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.max(64, data.length * 2));
        try (InputStream in = new FramedLZ4CompressorInputStream(new ByteArrayInputStream(data))) {
            copy(in, baos);
        }
        return baos.toByteArray();
    }

    private static void copy(InputStream in, ByteArrayOutputStream baos) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) {
            baos.write(buf, 0, n);
        }
    }

    /**
     * True when the LZ4 frame descriptor clears block independence (DV default).
     * Unknown/short buffers return false so {@code lz4-java} can report the error.
     */
    private static boolean lz4FrameNeedsDependentBlockDecoder(byte[] data) {
        if (data == null || data.length < 5) {
            return false;
        }
        if ((data[0] & 0xff) != LZ4_MAGIC_0 || (data[1] & 0xff) != LZ4_MAGIC_1
                || (data[2] & 0xff) != LZ4_MAGIC_2 || (data[3] & 0xff) != LZ4_MAGIC_3) {
            return false;
        }
        return (data[4] & LZ4_FLG_BLOCK_INDEPENDENCE) == 0;
    }

    private static boolean isDependentBlockUnsupported(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            String msg = c.getMessage();
            if (msg != null && msg.toLowerCase().contains("dependent block")) {
                return true;
            }
        }
        return false;
    }
}
