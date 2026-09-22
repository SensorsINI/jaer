package net.sf.jaer.eventio.dsec;

import java.util.logging.Logger;

import com.github.luben.zstd.Zstd;

import io.jhdf.exceptions.HdfFilterException;
import io.jhdf.filter.Filter;

/**
 * HDF5 filter id 32015 (standalone Zstandard, hdf5plugin / HDF Group plugin).
 * Distinct from Blosc+ZSTD ({@link BloscHdf5Filter} id 32001). Needed for
 * TU Delft event_planar {@code .h5} chunks.
 *
 * @see <a href="https://github.com/silx-kit/hdf5plugin">hdf5plugin</a>
 */
public final class ZstdHdf5Filter implements Filter {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    /** HDF5 registered filter id for Zstandard. */
    public static final int FILTER_ID = 32015;

    @Override
    public int getId() {
        return FILTER_ID;
    }

    @Override
    public String getName() {
        return "zstd";
    }

    @Override
    public byte[] decode(byte[] encodedData, int[] filterData) {
        if (encodedData == null || encodedData.length == 0) {
            throw new HdfFilterException("ZSTD buffer empty");
        }
        try {
            return decompress(encodedData, filterData);
        } catch (HdfFilterException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new HdfFilterException("ZSTD decode failed: " + e.getMessage(), e);
        }
    }

    static byte[] decompress(byte[] src, int[] filterData) {
        long frameSize = Zstd.getFrameContentSize(src);
        if (frameSize > 0 && frameSize <= Integer.MAX_VALUE) {
            return Zstd.decompress(src, (int) frameSize);
        }
        int hint = hintUncompressedSize(filterData);
        if (hint > 0) {
            return Zstd.decompress(src, hint);
        }
        // Some writers prefix a little-endian int32 uncompressed size.
        if (src.length > 8) {
            int prefix = (src[0] & 0xff)
                    | ((src[1] & 0xff) << 8)
                    | ((src[2] & 0xff) << 16)
                    | ((src[3] & 0xff) << 24);
            if (prefix > 0 && prefix < Integer.MAX_VALUE / 2) {
                byte[] rest = new byte[src.length - 4];
                System.arraycopy(src, 4, rest, 0, rest.length);
                try {
                    return Zstd.decompress(rest, prefix);
                } catch (RuntimeException ignore) {
                    log.fine("ZSTD 4-byte size prefix decode failed, retrying raw");
                }
            }
        }
        throw new HdfFilterException("ZSTD frame content size unknown");
    }

    private static int hintUncompressedSize(int[] filterData) {
        if (filterData == null) {
            return 0;
        }
        for (int i = 1; i < filterData.length; i++) {
            if (filterData[i] > 64 && filterData[i] < Integer.MAX_VALUE / 4) {
                return filterData[i];
            }
        }
        return 0;
    }
}
