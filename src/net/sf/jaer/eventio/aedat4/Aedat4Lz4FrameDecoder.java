package net.sf.jaer.eventio.aedat4;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4SafeDecompressor;
import net.jpountz.xxhash.XXHash32;
import net.jpountz.xxhash.XXHashFactory;
import org.apache.commons.compress.compressors.lz4.BlockLZ4CompressorInputStream;

/**
 * Fast LZ4 <em>frame</em> decoder for AEDAT-4 packet payloads.
 * <p>
 * DV recordings clear the block-independence flag. {@code lz4-java}'s
 * {@code LZ4FrameInputStream} rejects those frames entirely, and Apache
 * Commons Compress's framed reader is pure Java (~30× slower).
 * <p>
 * Strategy: parse the frame ourselves; decompress each block with native
 * {@code lz4-java} when no history is required (independent frames, or the
 * first block of a dependent frame). Only continuation blocks in dependent
 * frames use Commons Compress {@link BlockLZ4CompressorInputStream} with
 * {@link BlockLZ4CompressorInputStream#prefill(byte[])} for the rolling 64 KiB
 * dictionary. Falls back to a full Commons framed decode on parse errors.
 */
final class Aedat4Lz4FrameDecoder {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    private static final int MAGIC = 0x184D2204;
    private static final int FLG_VERSION_MASK = 0xC0;
    private static final int FLG_VERSION = 0x40;
    private static final int FLG_BLOCK_INDEPENDENCE = 0x20;
    private static final int FLG_BLOCK_CHECKSUM = 0x10;
    private static final int FLG_CONTENT_SIZE = 0x08;
    private static final int FLG_CONTENT_CHECKSUM = 0x04;
    private static final int FLG_DICT_ID = 0x01;
    private static final int BLOCK_UNCOMPRESSED_FLAG = 0x80000000;
    private static final int DICT_WINDOW = 64 * 1024;
    private static final int XXHASH_SEED = 0;

    private static final LZ4SafeDecompressor LZ4
            = LZ4Factory.fastestInstance().safeDecompressor();
    private static final XXHash32 XXH32 = XXHashFactory.fastestInstance().hash32();

    private static final AtomicBoolean LOGGED_FAST_PATH = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_FALLBACK = new AtomicBoolean();

    private Aedat4Lz4FrameDecoder() {
    }

    /**
     * Decompress one LZ4 frame. Prefer hybrid native path; on failure throw so
     * the caller can use Commons Compress framed fallback.
     */
    static byte[] decompress(byte[] frame) throws IOException {
        if (frame == null || frame.length < 7) {
            throw new IOException("LZ4 frame too short");
        }
        try {
            byte[] out = decompressHybrid(frame);
            if (LOGGED_FAST_PATH.compareAndSet(false, true)) {
                log.info("AEDAT-4 LZ4: using hybrid native block decoder "
                        + "(lz4-java + BlockLZ4 prefill for dependent continuations)");
            }
            return out;
        } catch (IOException | RuntimeException e) {
            if (LOGGED_FALLBACK.compareAndSet(false, true)) {
                log.log(Level.INFO, "AEDAT-4 LZ4 hybrid decoder failed once; "
                        + "falling back to Commons Compress framed reader: " + e, e);
            }
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        }
    }

    private static byte[] decompressHybrid(byte[] frame) throws IOException {
        ByteBuffer in = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN);
        int magic = in.getInt();
        if (magic != MAGIC) {
            throw new IOException("Not an LZ4 frame (magic=" + Integer.toHexString(magic) + ")");
        }
        int headerStart = in.position();
        int flg = in.get() & 0xff;
        int bd = in.get() & 0xff;
        if ((flg & FLG_VERSION_MASK) != FLG_VERSION) {
            throw new IOException("Unsupported LZ4 frame version");
        }
        boolean blockIndep = (flg & FLG_BLOCK_INDEPENDENCE) != 0;
        boolean blockChecksum = (flg & FLG_BLOCK_CHECKSUM) != 0;
        boolean hasContentSize = (flg & FLG_CONTENT_SIZE) != 0;
        boolean contentChecksum = (flg & FLG_CONTENT_CHECKSUM) != 0;
        boolean hasDictId = (flg & FLG_DICT_ID) != 0;

        long contentSize = -1;
        if (hasContentSize) {
            if (in.remaining() < 8) {
                throw new IOException("Truncated LZ4 content size");
            }
            contentSize = in.getLong();
            if (contentSize < 0 || contentSize > Integer.MAX_VALUE - 16) {
                throw new IOException("Implausible LZ4 content size " + contentSize);
            }
        }
        if (hasDictId) {
            if (in.remaining() < 4) {
                throw new IOException("Truncated LZ4 dict id");
            }
            in.getInt(); // ignored — DV frames do not use external dict ids
        }
        if (in.remaining() < 1) {
            throw new IOException("Truncated LZ4 header checksum");
        }
        int hc = in.get() & 0xff;
        int headerLen = in.position() - headerStart - 1; // exclude HC byte
        int expectedHc = (XXH32.hash(frame, headerStart, headerLen, XXHASH_SEED) >> 8) & 0xff;
        if (hc != expectedHc) {
            throw new IOException("LZ4 header checksum mismatch");
        }

        int blockMax = blockMaxSize(bd);
        ByteArrayOutputStream out = new ByteArrayOutputStream(
                contentSize >= 0 ? (int) contentSize : Math.max(64, frame.length * 3));
        byte[] dict = new byte[0];
        byte[] blockDest = new byte[blockMax];
        int blockIndex = 0;

        while (true) {
            if (in.remaining() < 4) {
                throw new IOException("Truncated LZ4 block size");
            }
            int blockHeader = in.getInt();
            if (blockHeader == 0) {
                break; // EndMark
            }
            boolean uncompressed = (blockHeader & BLOCK_UNCOMPRESSED_FLAG) != 0;
            int blockSize = blockHeader & ~BLOCK_UNCOMPRESSED_FLAG;
            if (blockSize < 0 || blockSize > blockMax || in.remaining() < blockSize) {
                throw new IOException("Invalid LZ4 block size " + blockSize);
            }
            int blockOff = in.position();
            in.position(blockOff + blockSize);
            if (blockChecksum) {
                if (in.remaining() < 4) {
                    throw new IOException("Truncated LZ4 block checksum");
                }
                in.getInt(); // skip; optional verify omitted for speed
            }

            int produced;
            if (uncompressed) {
                if (blockSize > blockDest.length) {
                    blockDest = new byte[blockSize];
                }
                System.arraycopy(frame, blockOff, blockDest, 0, blockSize);
                produced = blockSize;
            } else if (blockIndep || dict.length == 0) {
                produced = LZ4.decompress(frame, blockOff, blockSize, blockDest, 0, blockMax);
            } else {
                produced = decompressDependentBlock(frame, blockOff, blockSize, dict, blockDest, blockMax);
            }
            out.write(blockDest, 0, produced);
            if (!blockIndep) {
                dict = updateDict(dict, blockDest, produced);
            }
            blockIndex++;
        }

        if (contentChecksum) {
            if (in.remaining() < 4) {
                throw new IOException("Truncated LZ4 content checksum");
            }
            in.getInt(); // skip
        }
        if (contentSize >= 0 && out.size() != contentSize) {
            throw new IOException("LZ4 content size mismatch: expected " + contentSize
                    + " got " + out.size() + " (" + blockIndex + " blocks)");
        }
        return out.toByteArray();
    }

    private static int decompressDependentBlock(byte[] frame, int blockOff, int blockSize,
            byte[] dict, byte[] blockDest, int blockMax) throws IOException {
        // lz4-java has no usingDict API; BlockLZ4 + prefill handles history.
        try (BlockLZ4CompressorInputStream bin = new BlockLZ4CompressorInputStream(
                new ByteArrayInputStream(frame, blockOff, blockSize))) {
            if (dict.length > 0) {
                bin.prefill(dict);
            }
            int n = 0;
            while (n < blockMax) {
                int r = bin.read(blockDest, n, blockMax - n);
                if (r < 0) {
                    break;
                }
                n += r;
            }
            if (n <= 0) {
                throw new IOException("Dependent LZ4 block produced no output");
            }
            return n;
        }
    }

    /** Keep last {@value #DICT_WINDOW} bytes of decoded output. */
    private static byte[] updateDict(byte[] prev, byte[] block, int len) {
        if (len >= DICT_WINDOW) {
            return Arrays.copyOfRange(block, len - DICT_WINDOW, len);
        }
        if (prev.length + len <= DICT_WINDOW) {
            byte[] next = Arrays.copyOf(prev, prev.length + len);
            System.arraycopy(block, 0, next, prev.length, len);
            return next;
        }
        byte[] next = new byte[DICT_WINDOW];
        int keep = DICT_WINDOW - len;
        System.arraycopy(prev, prev.length - keep, next, 0, keep);
        System.arraycopy(block, 0, next, keep, len);
        return next;
    }

    private static int blockMaxSize(int bd) {
        int code = (bd >> 4) & 0x7;
        switch (code) {
            case 4:
                return 64 * 1024;
            case 5:
                return 256 * 1024;
            case 6:
                return 1024 * 1024;
            case 7:
                return 4 * 1024 * 1024;
            default:
                return 64 * 1024; // DV default
        }
    }
}
