package net.sf.jaer.eventio.aedat4;

import com.github.luben.zstd.Zstd;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.xxhash.XXHash32;
import net.jpountz.xxhash.XXHashFactory;
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorInputStream;
import net.sf.jaer.eventio.aedat4.dv.CompressionType;
import net.sf.jaer.eventio.aedat4.dv.IOHeader;
import net.sf.jaer.util.EngineeringFormat;

/**
 * AEDAT-4 / DV packet payload compression (LZ4 or ZSTD frame per packet).
 * FlatBuffers itself is not compressed; only the size-prefixed FlatBuffer bytes
 * that follow each 8-byte packet header.
 * <p>
 * LZ4 strategy (speed + DV compatibility):
 * <ul>
 *   <li><b>Write</b> — {@code LZ4Compressor} into reused buffers, independent-block
 *       LZ4 frames (same layout as {@code LZ4FrameOutputStream}).</li>
 *   <li><b>Read</b> — {@code lz4-java} frame reader for independent blocks;
 *       for DV <em>dependent-block</em> frames use {@link Aedat4Lz4FrameDecoder}
 *       (native lz4-java per block, Commons {@code BlockLZ4}+prefill only for
 *       continuation blocks). Full Commons framed reader is the last resort.</li>
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
    /** Version 1 + BLOCK_INDEPENDENCE (lz4-java {@code LZ4FrameOutputStream} default). */
    private static final byte LZ4_FLG_INDEPENDENT_V1 = (byte) 0x60;
    /** FLG bit 3: 8-byte little-endian uncompressed content size follows BD. */
    private static final int LZ4_FLG_CONTENT_SIZE = 0x08;
    /** Bytes of a compressed packet to peek for ZSTD/LZ4 uncompressed size. */
    public static final int UNCOMPRESSED_SIZE_HEADER_BYTES = 32;
    /** Uncompressed block flag in the 32-bit block-size field. */
    private static final int LZ4_FRAME_INCOMPRESSIBLE_MASK = 0x80000000;
    /** BD byte: 64 KiB blocks ({@code indicator==4}). */
    private static final byte LZ4_BD_64KB = (byte) (4 << 4);
    /** BD byte: 1 MiB blocks ({@code indicator==6}). */
    private static final byte LZ4_BD_1MB = (byte) (6 << 4);
    private static final int LZ4_BLOCK_64KB = 1 << 16;
    private static final int LZ4_BLOCK_1MB = 1 << 20;

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
            case CompressionType.LZ4_HIGH:
            case CompressionType.ZSTD:
            case CompressionType.ZSTD_HIGH:
                ByteBuffer wrapped = compressDirect(uncompressed, compression);
                byte[] copy = new byte[wrapped.remaining()];
                wrapped.get(copy);
                return copy;
            default:
                return uncompressed;
        }
    }

    /**
     * Compresses one packet payload. For LZ4 the returned buffer aliases a
     * thread-local array and is valid only until the next compress on this thread.
     */
    static ByteBuffer compressDirect(byte[] uncompressed, int compression) throws IOException {
        compression = clamp(compression);
        if (compression == CompressionType.NONE || uncompressed == null || uncompressed.length == 0) {
            return ByteBuffer.wrap(uncompressed == null ? new byte[0] : uncompressed);
        }
        switch (compression) {
            case CompressionType.LZ4:
                return lz4FrameCompressDirect(uncompressed, false);
            case CompressionType.LZ4_HIGH:
                return lz4FrameCompressDirect(uncompressed, true);
            case CompressionType.ZSTD:
                return ByteBuffer.wrap(Zstd.compress(uncompressed, ZSTD_LEVEL));
            case CompressionType.ZSTD_HIGH:
                return ByteBuffer.wrap(Zstd.compress(uncompressed, ZSTD_HIGH_LEVEL));
            default:
                return ByteBuffer.wrap(uncompressed);
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

    /**
     * Uncompressed FlatBuffer size from a codec frame header, without decompressing.
     * {@code compressedLength} is used when {@code compression == NONE}.
     *
     * @return uncompressed byte count, or {@code -1} if the header does not store it
     *         (older LZ4 frames without Content Size, truncated peek, etc.)
     */
    public static long uncompressedSize(byte[] prefix, int compression, int compressedLength) {
        return uncompressedSize(prefix, 0, prefix == null ? 0 : prefix.length, compression, compressedLength);
    }

    public static long uncompressedSize(byte[] prefix, int off, int len, int compression, int compressedLength) {
        compression = clamp(compression);
        if (compression == CompressionType.NONE) {
            return compressedLength >= 0 ? compressedLength : Math.max(0, len);
        }
        if (prefix == null || len <= 0 || off < 0 || off + len > prefix.length) {
            return -1;
        }
        switch (compression) {
            case CompressionType.ZSTD:
            case CompressionType.ZSTD_HIGH:
                return zstdContentSize(prefix, off, len);
            case CompressionType.LZ4:
            case CompressionType.LZ4_HIGH:
                return lz4ContentSize(prefix, off, len);
            default:
                return -1;
        }
    }

    /**
     * One-line input (uncompressed FlatBuffers) vs output (on-disk payloads) summary.
     * Empty if both sizes are missing.
     */
    public static String formatPayloadCompression(int compression, long uncompressedBytes, long compressedBytes) {
        EngineeringFormat eng = new EngineeringFormat();
        eng.setPrecision(3);
        String name = nameOf(compression);
        if (uncompressedBytes <= 0 && compressedBytes <= 0) {
            return "";
        }
        if (uncompressedBytes <= 0) {
            return String.format("Payloads: %sB compressed (%s); uncompressed size not in packet headers",
                    eng.format((double) compressedBytes).trim(), name);
        }
        String raw = eng.format((double) uncompressedBytes).trim();
        String packed = eng.format((double) compressedBytes).trim();
        if (compression == CompressionType.NONE || uncompressedBytes == compressedBytes) {
            return String.format("Payloads: %sB uncompressed (%s)", raw, name);
        }
        double pct = 100.0 * compressedBytes / (double) uncompressedBytes;
        double ratio = compressedBytes > 0 ? uncompressedBytes / (double) compressedBytes : 0;
        return String.format(
                "Payloads: %sB uncompressed -> %sB compressed (%s, %.0f%% of uncompressed, %.1f:1)",
                raw, packed, name, pct, ratio);
    }

    private static long zstdContentSize(byte[] prefix, int off, int len) {
        byte[] slice = (off == 0 && len == prefix.length) ? prefix : Arrays.copyOfRange(prefix, off, off + len);
        long size = Zstd.decompressedSize(slice);
        if (size <= 0 || size > Integer.MAX_VALUE) {
            return -1;
        }
        return size;
    }

    private static long lz4ContentSize(byte[] prefix, int off, int len) {
        if (len < 14) {
            return -1;
        }
        if ((prefix[off] & 0xff) != LZ4_MAGIC_0 || (prefix[off + 1] & 0xff) != LZ4_MAGIC_1
                || (prefix[off + 2] & 0xff) != LZ4_MAGIC_2 || (prefix[off + 3] & 0xff) != LZ4_MAGIC_3) {
            return -1;
        }
        int flg = prefix[off + 4] & 0xff;
        if ((flg & LZ4_FLG_CONTENT_SIZE) == 0) {
            return -1;
        }
        long size = getLongLE(prefix, off + 6);
        if (size < 0 || size > Integer.MAX_VALUE) {
            return -1;
        }
        return size;
    }

    private static final ThreadLocal<Lz4Scratch> LZ4_FAST = ThreadLocal.withInitial(() -> new Lz4Scratch(false));
    private static final ThreadLocal<Lz4Scratch> LZ4_HIGH = ThreadLocal.withInitial(() -> new Lz4Scratch(true));

    /**
     * Independent-block LZ4 frame using a reused {@link LZ4Compressor} and dest
     * buffers (same bytes as {@code new LZ4FrameOutputStream(out, blockSize)}).
     */
    private static ByteBuffer lz4FrameCompressDirect(byte[] data, boolean high) {
        Lz4Scratch s = (high ? LZ4_HIGH : LZ4_FAST).get();
        s.out.reset();
        s.header[0] = (byte) LZ4_MAGIC_0;
        s.header[1] = (byte) LZ4_MAGIC_1;
        s.header[2] = (byte) LZ4_MAGIC_2;
        s.header[3] = (byte) LZ4_MAGIC_3;
        s.header[4] = (byte) (LZ4_FLG_INDEPENDENT_V1 | LZ4_FLG_CONTENT_SIZE);
        s.header[5] = high ? LZ4_BD_1MB : LZ4_BD_64KB;
        putLongLE(s.header, 6, data.length);
        int hc = (s.checksum.hash(s.header, 4, 10, 0) >> 8) & 0xFF;
        s.header[14] = (byte) hc;
        s.out.write(s.header, 0, 15);

        int off = 0;
        while (off < data.length) {
            int chunk = Math.min(s.blockSize, data.length - off);
            int max = s.compressor.maxCompressedLength(chunk);
            if (s.compressed.length < max) {
                s.compressed = new byte[max];
            }
            int clen = s.compressor.compress(data, off, chunk, s.compressed, 0, s.compressed.length);
            if (clen >= chunk) {
                putIntLE(s.sizeLE, chunk | LZ4_FRAME_INCOMPRESSIBLE_MASK);
                s.out.write(s.sizeLE, 0, 4);
                s.out.write(data, off, chunk);
            } else {
                putIntLE(s.sizeLE, clen);
                s.out.write(s.sizeLE, 0, 4);
                s.out.write(s.compressed, 0, clen);
            }
            off += chunk;
        }
        putIntLE(s.sizeLE, 0);
        s.out.write(s.sizeLE, 0, 4);
        return ByteBuffer.wrap(s.out.raw(), 0, s.out.size());
    }

    private static void putIntLE(byte[] dest, int value) {
        dest[0] = (byte) value;
        dest[1] = (byte) (value >>> 8);
        dest[2] = (byte) (value >>> 16);
        dest[3] = (byte) (value >>> 24);
    }

    private static void putLongLE(byte[] dest, int off, long value) {
        dest[off] = (byte) value;
        dest[off + 1] = (byte) (value >>> 8);
        dest[off + 2] = (byte) (value >>> 16);
        dest[off + 3] = (byte) (value >>> 24);
        dest[off + 4] = (byte) (value >>> 32);
        dest[off + 5] = (byte) (value >>> 40);
        dest[off + 6] = (byte) (value >>> 48);
        dest[off + 7] = (byte) (value >>> 56);
    }

    private static long getLongLE(byte[] src, int off) {
        return (src[off] & 0xffL)
                | ((src[off + 1] & 0xffL) << 8)
                | ((src[off + 2] & 0xffL) << 16)
                | ((src[off + 3] & 0xffL) << 24)
                | ((src[off + 4] & 0xffL) << 32)
                | ((src[off + 5] & 0xffL) << 40)
                | ((src[off + 6] & 0xffL) << 48)
                | ((src[off + 7] & 0xffL) << 56);
    }

    private static final class GrowableBytes extends ByteArrayOutputStream {
        GrowableBytes(int cap) {
            super(cap);
        }

        byte[] raw() {
            return buf;
        }
    }

    private static final class Lz4Scratch {
        final LZ4Compressor compressor;
        final XXHash32 checksum;
        final int blockSize;
        final GrowableBytes out;
        final byte[] header = new byte[15];
        final byte[] sizeLE = new byte[4];
        byte[] compressed;

        Lz4Scratch(boolean high) {
            LZ4Factory factory = LZ4Factory.fastestInstance();
            compressor = high ? factory.highCompressor() : factory.fastCompressor();
            checksum = XXHashFactory.fastestInstance().hash32();
            blockSize = high ? LZ4_BLOCK_1MB : LZ4_BLOCK_64KB;
            compressed = new byte[compressor.maxCompressedLength(blockSize)];
            out = new GrowableBytes(1 << 16);
        }
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
     * Prefer {@code lz4-java}; dependent-block DV frames use the hybrid decoder.
     */
    private static byte[] lz4FrameDecompress(byte[] data) throws IOException {
        if (isDependentBlockLz4Frame(data)) {
            try {
                return Aedat4Lz4FrameDecoder.decompress(data);
            } catch (IOException | RuntimeException e) {
                return lz4FrameDecompressCommonsCompress(data);
            }
        }
        try {
            return lz4FrameDecompressLz4Java(data);
        } catch (RuntimeException e) {
            // lz4-java throws RuntimeException when BLOCK_INDEPENDENCE is clear.
            if (isDependentBlockUnsupported(e)) {
                try {
                    return Aedat4Lz4FrameDecoder.decompress(data);
                } catch (IOException | RuntimeException e2) {
                    return lz4FrameDecompressCommonsCompress(data);
                }
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
