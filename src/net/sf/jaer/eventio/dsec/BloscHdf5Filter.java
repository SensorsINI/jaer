package net.sf.jaer.eventio.dsec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.logging.Logger;

import com.github.luben.zstd.Zstd;

import io.jhdf.exceptions.HdfFilterException;
import io.jhdf.filter.Filter;
import io.jhdf.filter.FilterManager;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4SafeDecompressor;

/**
 * HDF5 filter id 32001 (Blosc) for jHDF, enough to read
 * <a href="https://dsec.ifi.uzh.ch/data-format/">DSEC</a> {@code events.h5}
 * (Blosc + ZSTD with byte-shuffle or bitshuffle).
 * <p>
 * Registered once via {@link #ensureRegistered()}.
 */
public final class BloscHdf5Filter implements Filter {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    /** HDF5 registered filter id for Blosc. */
    public static final int FILTER_ID = 32001;

    private static final int BLOSC_HEADER = 16;
    private static final int BLOSC_DOSHUFFLE = 0x1;
    private static final int BLOSC_MEMCPYED = 0x2;
    private static final int BLOSC_DOBITSHUFFLE = 0x4;
    private static final int BLOSC_MAX_TYPESIZE = 255;
    private static final int BLOSC_MAX_BLOCKSIZE = (Integer.MAX_VALUE - BLOSC_MAX_TYPESIZE * 4) / 3;
    private static final int MIN_BUFFERSIZE = 128;
    private static final int MAX_SPLITS = 16;

    /*
     * Blosc header flags bits 5–7 store the *format* code (BLOSC_*_FORMAT /
     * BLOSC_*_LIB), not the compressor enum (BLOSC_ZSTD=5 etc.):
     *   0=blosclz, 1=lz4, 2=snappy, 3=zlib, 4=zstd
     */
    private static final int BLOSC_FORMAT_LZ4 = 1;
    private static final int BLOSC_FORMAT_ZLIB = 3;
    private static final int BLOSC_FORMAT_ZSTD = 4;

    private static final LZ4SafeDecompressor LZ4
            = LZ4Factory.fastestInstance().safeDecompressor();

    private static volatile boolean registered;

    public static void ensureRegistered() {
        if (registered) {
            return;
        }
        synchronized (BloscHdf5Filter.class) {
            if (registered) {
                return;
            }
            quietJhdfLogging();
            FilterManager.addFilter(new BloscHdf5Filter());
            registered = true;
            log.info("Registered HDF5 Blosc filter (id=" + FILTER_ID + ") for DSEC HDF5 playback");
        }
    }

    /** jHDF defaults to DEBUG and floods the console on every slice read. */
    private static void quietJhdfLogging() {
        try {
            org.slf4j.Logger slf = org.slf4j.LoggerFactory.getLogger("io.jhdf");
            if (slf instanceof ch.qos.logback.classic.Logger) {
                ((ch.qos.logback.classic.Logger) slf).setLevel(ch.qos.logback.classic.Level.WARN);
            }
        } catch (Throwable t) {
            // logback may be absent; ignore
        }
    }

    @Override
    public int getId() {
        return FILTER_ID;
    }

    @Override
    public String getName() {
        return "blosc";
    }

    @Override
    public byte[] decode(byte[] encodedData, int[] filterData) {
        if (encodedData == null || encodedData.length < BLOSC_HEADER) {
            throw new HdfFilterException("Blosc buffer too short");
        }
        try {
            return decompress(encodedData);
        } catch (HdfFilterException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new HdfFilterException("Blosc decode failed: " + e.getMessage(), e);
        }
    }

    static byte[] decompress(byte[] src) {
        final int version = src[0] & 0xFF;
        if (version != 2) {
            throw new HdfFilterException("Unsupported Blosc format version " + version);
        }
        final int flags = src[2] & 0xFF;
        if ((flags & 0x08) != 0) {
            throw new HdfFilterException("Blosc compressor flags from the future");
        }
        final int typesize = src[3] & 0xFF;
        final int nbytes = le32(src, 4);
        final int blocksize = le32(src, 8);
        final int cbytes = le32(src, 12);
        if (nbytes < 0 || blocksize <= 0 || blocksize > BLOSC_MAX_BLOCKSIZE
                || typesize <= 0 || typesize > BLOSC_MAX_TYPESIZE) {
            throw new HdfFilterException("Invalid Blosc header sizes");
        }
        if (cbytes > src.length) {
            throw new HdfFilterException("Blosc cbytes exceeds buffer length");
        }

        final byte[] dest = new byte[nbytes];
        if (nbytes == 0) {
            return dest;
        }

        int nblocks = nbytes / blocksize;
        final int leftover = nbytes % blocksize;
        if (leftover > 0) {
            nblocks++;
        }

        if ((flags & BLOSC_MEMCPYED) != 0) {
            if (nbytes + BLOSC_HEADER != cbytes) {
                throw new HdfFilterException("Blosc MEMCPYED size mismatch");
            }
            System.arraycopy(src, BLOSC_HEADER, dest, 0, nbytes);
            return dest;
        }

        if (nblocks > (cbytes - BLOSC_HEADER) / 4) {
            throw new HdfFilterException("Blosc bstarts array overruns buffer");
        }

        final int compformat = (flags >> 5) & 0x7;
        final boolean doshuffle = ((flags & BLOSC_DOSHUFFLE) != 0) && typesize > 1;
        final boolean dobitshuffle = ((flags & BLOSC_DOBITSHUFFLE) != 0) && blocksize >= typesize;
        final boolean needsTmp = doshuffle || dobitshuffle;
        final byte[] tmp = needsTmp ? new byte[blocksize] : null;
        // c-blosc bitunshuffle needs a second workspace the size of the block
        final byte[] tmp2 = dobitshuffle ? new byte[blocksize] : null;

        for (int j = 0; j < nblocks; j++) {
            int bsize = blocksize;
            boolean leftoverBlock = false;
            if (j == nblocks - 1 && leftover > 0) {
                bsize = leftover;
                leftoverBlock = true;
            }
            final int srcOffset = le32(src, BLOSC_HEADER + j * 4);
            final byte[] blockDest = needsTmp ? tmp : dest;
            final int destOffset = needsTmp ? 0 : j * blocksize;
            final int got = decompressBlock(src, srcOffset, cbytes, blockDest, destOffset,
                    bsize, typesize, flags, leftoverBlock, compformat);
            if (got != bsize) {
                throw new HdfFilterException("Blosc block decompress size mismatch: " + got + " vs " + bsize);
            }
            if (doshuffle) {
                unshuffle(typesize, bsize, tmp, dest, j * blocksize);
            } else if (dobitshuffle) {
                bitunshuffle(typesize, bsize, tmp, dest, j * blocksize, tmp2);
            }
        }
        return dest;
    }

    private static int decompressBlock(byte[] src, int srcOffset, int compressedSize,
            byte[] dest, int destOffset, int blocksize, int typesize, int flags,
            boolean leftoverBlock, int compformat) {
        final boolean dontSplit = ((flags & 0x10) != 0);
        final int nsplits;
        if (!dontSplit
                && typesize <= MAX_SPLITS
                && (blocksize / typesize) >= MIN_BUFFERSIZE
                && !leftoverBlock) {
            nsplits = typesize;
        } else {
            nsplits = 1;
        }
        final int neblock = blocksize / nsplits;
        int ntbytes = 0;
        int off = srcOffset;
        for (int j = 0; j < nsplits; j++) {
            if (off < 0 || off > compressedSize - 4) {
                throw new HdfFilterException("Blosc split offset invalid");
            }
            final int splitCbytes = le32(src, off);
            off += 4;
            if (splitCbytes < 0 || splitCbytes > compressedSize - off) {
                throw new HdfFilterException("Blosc split cbytes invalid");
            }
            if (splitCbytes == neblock) {
                System.arraycopy(src, off, dest, destOffset + ntbytes, neblock);
            } else {
                decompressCodec(compformat, src, off, splitCbytes, dest, destOffset + ntbytes, neblock);
            }
            off += splitCbytes;
            ntbytes += neblock;
        }
        return ntbytes;
    }

    private static void decompressCodec(int compformat, byte[] src, int srcOff, int srcLen,
            byte[] dest, int destOff, int destLen) {
        switch (compformat) {
            case BLOSC_FORMAT_ZSTD: {
                byte[] in = Arrays.copyOfRange(src, srcOff, srcOff + srcLen);
                byte[] out = Zstd.decompress(in, destLen);
                if (out.length != destLen) {
                    throw new HdfFilterException("ZSTD size " + out.length + " != " + destLen);
                }
                System.arraycopy(out, 0, dest, destOff, destLen);
                break;
            }
            case BLOSC_FORMAT_LZ4: {
                LZ4.decompress(src, srcOff, srcLen, dest, destOff, destLen);
                break;
            }
            case BLOSC_FORMAT_ZLIB: {
                try {
                    java.util.zip.Inflater inflater = new java.util.zip.Inflater();
                    inflater.setInput(src, srcOff, srcLen);
                    int n = inflater.inflate(dest, destOff, destLen);
                    inflater.end();
                    if (n != destLen) {
                        throw new HdfFilterException("ZLIB size " + n + " != " + destLen);
                    }
                } catch (java.util.zip.DataFormatException e) {
                    throw new HdfFilterException("ZLIB decompress failed: " + e.getMessage(), e);
                }
                break;
            }
            default:
                throw new HdfFilterException(
                        "Unsupported Blosc format code " + compformat + " (need ZSTD/LZ4/ZLIB for DSEC)");
        }
    }

    /** Inverse of Blosc byte-shuffle into {@code dest} at {@code destOff}. */
    static void unshuffle(int typesize, int blocksize, byte[] shuffled, byte[] dest, int destOff) {
        final int elements = blocksize / typesize;
        int pos = 0;
        for (int i = 0; i < typesize; i++) {
            for (int j = 0; j < elements; j++) {
                dest[destOff + j * typesize + i] = shuffled[pos++];
            }
        }
        if (pos < blocksize) {
            System.arraycopy(shuffled, pos, dest, destOff + pos, blocksize - pos);
        }
    }

    /**
     * Inverse of c-blosc bitshuffle ({@code blosc_internal_bshuf_untrans_bit_elem_scal}).
     * If element count is not a multiple of 8, c-blosc stored a plain memcpy.
     */
    static void bitunshuffle(int typesize, int blocksize, byte[] shuffled, byte[] dest,
            int destOff, byte[] tmp) {
        final int size = blocksize / typesize; // element count
        if ((size % 8) != 0) {
            System.arraycopy(shuffled, 0, dest, destOff, blocksize);
            return;
        }
        // c-blosc: bitrow transpose into tmp, then 8-element bit shuffle into dest
        bshufTransByteBitrow(shuffled, tmp, size, typesize);
        if (destOff == 0 && dest.length >= blocksize) {
            bshufShuffleBitEightElem(tmp, dest, size, typesize);
        } else {
            byte[] out = new byte[blocksize];
            bshufShuffleBitEightElem(tmp, out, size, typesize);
            System.arraycopy(out, 0, dest, destOff, blocksize);
        }
    }

    /** {@code bshuf_trans_byte_bitrow_scal} */
    private static void bshufTransByteBitrow(byte[] in, byte[] out, int size, int elemSize) {
        final int nbyteRow = size / 8;
        for (int jj = 0; jj < elemSize; jj++) {
            for (int ii = 0; ii < nbyteRow; ii++) {
                for (int kk = 0; kk < 8; kk++) {
                    out[ii * 8 * elemSize + jj * 8 + kk] = in[(jj * 8 + kk) * nbyteRow + ii];
                }
            }
        }
    }

    /** {@code blosc_internal_bshuf_shuffle_bit_eightelem_scal} (little-endian). */
    private static void bshufShuffleBitEightElem(byte[] in, byte[] out, int size, int elemSize) {
        final int nbyte = elemSize * size;
        for (int jj = 0; jj < 8 * elemSize; jj += 8) {
            for (int ii = 0; ii + 8 * elemSize - 1 < nbyte; ii += 8 * elemSize) {
                long x = le64(in, ii + jj);
                x = transBit8x8(x);
                for (int kk = 0; kk < 8; kk++) {
                    out[ii + jj / 8 + kk * elemSize] = (byte) (x & 0xFF);
                    x >>>= 8;
                }
            }
        }
    }

    /** {@code TRANS_BIT_8X8} from c-blosc bitshuffle-generic.h */
    private static long transBit8x8(long x) {
        long t = (x ^ (x >>> 7)) & 0x00AA00AA00AA00AAL;
        x = x ^ t ^ (t << 7);
        t = (x ^ (x >>> 14)) & 0x0000CCCC0000CCCCL;
        x = x ^ t ^ (t << 14);
        t = (x ^ (x >>> 28)) & 0x00000000F0F0F0F0L;
        x = x ^ t ^ (t << 28);
        return x;
    }

    private static long le64(byte[] b, int off) {
        return ByteBuffer.wrap(b, off, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    private static int le32(byte[] b, int off) {
        return ByteBuffer.wrap(b, off, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }
}
