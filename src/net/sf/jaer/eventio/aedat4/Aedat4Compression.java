package net.sf.jaer.eventio.aedat4;

import com.github.luben.zstd.Zstd;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.lz4.LZ4FrameOutputStream;
import net.jpountz.lz4.LZ4FrameOutputStream.BLOCKSIZE;
import net.sf.jaer.eventio.aedat4.dv.CompressionType;

/**
 * AEDAT-4 / DV packet payload compression (LZ4 or ZSTD frame per packet).
 * FlatBuffers itself is not compressed; only the size-prefixed FlatBuffer bytes
 * that follow each 8-byte packet header.
 */
public final class Aedat4Compression {

    /** Default ZSTD level (DV "ZSTD"). */
    private static final int ZSTD_LEVEL = 3;
    /** Higher ZSTD level for {@link CompressionType#ZSTD_HIGH}. */
    private static final int ZSTD_HIGH_LEVEL = 9;

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

    private static byte[] lz4FrameCompress(byte[] data, boolean high) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.max(64, data.length / 2));
        // LZ4 frame format (DV). HIGH uses larger blocks for better ratio.
        BLOCKSIZE blockSize = high ? BLOCKSIZE.SIZE_1MB : BLOCKSIZE.SIZE_64KB;
        try (OutputStream out = new LZ4FrameOutputStream(baos, blockSize)) {
            out.write(data);
        }
        return baos.toByteArray();
    }

    private static byte[] lz4FrameDecompress(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.max(64, data.length * 2));
        try (InputStream in = new LZ4FrameInputStream(new ByteArrayInputStream(data))) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                baos.write(buf, 0, n);
            }
        }
        return baos.toByteArray();
    }
}
