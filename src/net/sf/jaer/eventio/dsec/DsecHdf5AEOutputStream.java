package net.sf.jaer.eventio.dsec;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.logging.Logger;

import io.jhdf.HdfFile;
import io.jhdf.WritableHdfFile;
import io.jhdf.api.WritableGroup;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.PolarityEvent.Polarity;

/**
 * Writes a DSEC-layout cooked event HDF5 file
 * ({@code /events/{p,t,x,y}}, {@code /ms_to_idx}, {@code /t_offset}).
 * <p>
 * Events are buffered then flushed on {@link #close()}. jHDF 0.12 writes
 * contiguous uncompressed datasets (gzip/Blosc write is not available).
 * {@code t_offset} is the first exported timestamp; stored {@code t} is
 * relative. {@code width}/{@code height} attributes are written for
 * {@link DsecHdf5AEInputStream#peekSensorSize(File)}.
 * <p>
 * Coordinates follow DSEC / computer-vision convention: {@code x} is column
 * from the left, {@code y} is row from the <em>top</em>, {@code p} is 0=off /
 * 1=on. {@link #write(PolarityEvent)} converts jAER lower-left {@code y}.
 */
public final class DsecHdf5AEOutputStream implements AutoCloseable {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final int CHUNK = 1 << 20;
    /** Abort if estimated arrays would exceed this fraction of max heap. */
    private static final double HEAP_FRACTION = 0.45;

    private final File file;
    private final int width;
    private final int height;
    private final IntChunks tRel = new IntChunks();
    private final ShortChunks x = new ShortChunks();
    private final ShortChunks y = new ShortChunks();
    private final ByteChunks p = new ByteChunks();
    private long tOffset = Long.MIN_VALUE;
    private long eventsWritten;
    private boolean closed;

    public DsecHdf5AEOutputStream(File file, int width, int height) {
        this.file = file;
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
    }

    public void write(PolarityEvent ae) throws IOException {
        if (ae == null || closed) {
            return;
        }
        // DSEC / image coords: row 0 is top. jAER y=0 is bottom (LL origin).
        int yDsec = (height - 1) - (ae.y & 0xffff);
        if (yDsec < 0) {
            yDsec = 0;
        }
        write(ae.timestamp, ae.x, yDsec, ae.polarity == Polarity.On ? (byte) 1 : (byte) 0);
    }

    /**
     * Append one event in DSEC coordinates (column from left, row from top,
     * {@code p}=0 off / 1 on).
     */

    public void write(int timestampUs, int col, int row, byte polarity01) throws IOException {
        if (closed) {
            return;
        }
        if (eventsWritten >= Integer.MAX_VALUE) {
            throw new IOException("DSEC HDF5 export exceeds Java array size (2^31 events)");
        }
        if (tOffset == Long.MIN_VALUE) {
            tOffset = timestampUs;
        }
        long rel = (timestampUs & 0xffffffffL) - (tOffset & 0xffffffffL);
        if (rel < 0) {
            rel = 0;
        }
        if (rel > Integer.MAX_VALUE) {
            throw new IOException("Relative timestamp overflow for DSEC t[] (int32 µs)");
        }
        maybeCheckHeap();
        tRel.add((int) rel);
        x.add((short) col);
        y.add((short) row);
        p.add(polarity01 != 0 ? (byte) 1 : (byte) 0);
        eventsWritten++;
    }

    public long getEventsWritten() {
        return eventsWritten;
    }

    public long getTOffset() {
        return tOffset == Long.MIN_VALUE ? 0 : tOffset;
    }

    /**
     * Discard buffered events without writing a file (cancelled export).
     */
    public void abort() {
        closed = true;
        eventsWritten = 0;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        int n = (int) eventsWritten;
        int[] tArr = tRel.toArray(n);
        short[] xArr = x.toArray(n);
        short[] yArr = y.toArray(n);
        byte[] pArr = p.toArray(n);
        long[] msToIdx = buildMsToIdx(tArr);
        long offset = tOffset == Long.MIN_VALUE ? 0 : tOffset;
        Path path = file.toPath();
        try (WritableHdfFile hdf = HdfFile.write(path)) {
            hdf.putAttribute("width", width);
            hdf.putAttribute("height", height);
            hdf.putAttribute("sizeX", width);
            hdf.putAttribute("sizeY", height);
            hdf.putDataset("t_offset", offset);
            hdf.putDataset("ms_to_idx", msToIdx);
            WritableGroup events = hdf.putGroup("events");
            events.putAttribute("width", width);
            events.putAttribute("height", height);
            // jHDF 0.12 Utils.getArrayType does Array.get(a, 0) and throws on empty arrays.
            putDataset1d(events, "t", tArr);
            putDataset1d(events, "x", xArr);
            putDataset1d(events, "y", yArr);
            putDataset1d(events, "p", pArr);
        }
        log.info(String.format("Wrote DSEC HDF5 %s: %,d events, %dx%d, t_offset=%d",
                file.getName(), eventsWritten, width, height, offset));
    }

    /**
     * Writes a synthetic DSEC file (for round-trip smoke tests).
     */
    public static void writeSynthetic(File file, int width, int height,
            int[] tAbsUs, short[] xs, short[] ys, byte[] ps) throws IOException {
        if (tAbsUs == null || xs == null || ys == null || ps == null) {
            throw new IOException("null arrays");
        }
        if (tAbsUs.length != xs.length || tAbsUs.length != ys.length || tAbsUs.length != ps.length) {
            throw new IOException("array length mismatch");
        }
        try (DsecHdf5AEOutputStream out = new DsecHdf5AEOutputStream(file, width, height)) {
            for (int i = 0; i < tAbsUs.length; i++) {
                out.write(tAbsUs[i], xs[i], ys[i], ps[i]);
            }
        }
    }

    /**
     * jHDF 0.12 cannot infer the dtype of a zero-length array.
     */
    private static void putDataset1d(WritableGroup group, String name, Object array) {
        if (array == null || java.lang.reflect.Array.getLength(array) == 0) {
            log.warning("Skipping empty HDF5 dataset /events/" + name
                    + " (jHDF 0.12 cannot write zero-length arrays)");
            return;
        }
        group.putDataset(name, array);
    }

    static long[] buildMsToIdx(int[] tRelUs) {
        if (tRelUs == null || tRelUs.length == 0) {
            return new long[]{0};
        }
        int last = tRelUs[tRelUs.length - 1];
        if (last < 0) {
            last = 0;
        }
        int nMs = last / 1000;
        long[] msToIdx = new long[nMs + 1];
        int ev = 0;
        int n = tRelUs.length;
        for (int ms = 0; ms <= nMs; ms++) {
            long threshold = (long) ms * 1000L;
            while (ev < n && (tRelUs[ev] & 0xffffffffL) < threshold) {
                ev++;
            }
            msToIdx[ms] = ev;
        }
        return msToIdx;
    }

    private void maybeCheckHeap() throws IOException {
        if ((eventsWritten & 0xfffff) != 0) { // every ~1M
            return;
        }
        long est = (eventsWritten + CHUNK) * 11L; // int+short+short+byte ≈ 11
        long max = Runtime.getRuntime().maxMemory();
        if (est > max * HEAP_FRACTION) {
            throw new IOException(String.format(
                    "DSEC export would use ~%,d bytes of heap (max %,d). Shorten IN/OUT or use CSV.",
                    est, max));
        }
    }

    private static final class IntChunks {
        private final ArrayList<int[]> chunks = new ArrayList<>();
        private int[] cur = new int[CHUNK];
        private int i;

        void add(int v) {
            if (i == cur.length) {
                chunks.add(cur);
                cur = new int[CHUNK];
                i = 0;
            }
            cur[i++] = v;
        }

        int[] toArray(int n) {
            int[] out = new int[n];
            int off = 0;
            for (int[] c : chunks) {
                System.arraycopy(c, 0, out, off, c.length);
                off += c.length;
            }
            if (i > 0) {
                System.arraycopy(cur, 0, out, off, i);
            }
            chunks.clear();
            cur = null;
            return out;
        }
    }

    private static final class ShortChunks {
        private final ArrayList<short[]> chunks = new ArrayList<>();
        private short[] cur = new short[CHUNK];
        private int i;

        void add(short v) {
            if (i == cur.length) {
                chunks.add(cur);
                cur = new short[CHUNK];
                i = 0;
            }
            cur[i++] = v;
        }

        short[] toArray(int n) {
            short[] out = new short[n];
            int off = 0;
            for (short[] c : chunks) {
                System.arraycopy(c, 0, out, off, c.length);
                off += c.length;
            }
            if (i > 0) {
                System.arraycopy(cur, 0, out, off, i);
            }
            chunks.clear();
            cur = null;
            return out;
        }
    }

    private static final class ByteChunks {
        private final ArrayList<byte[]> chunks = new ArrayList<>();
        private byte[] cur = new byte[CHUNK];
        private int i;

        void add(byte v) {
            if (i == cur.length) {
                chunks.add(cur);
                cur = new byte[CHUNK];
                i = 0;
            }
            cur[i++] = v;
        }

        byte[] toArray(int n) {
            byte[] out = new byte[n];
            int off = 0;
            for (byte[] c : chunks) {
                System.arraycopy(c, 0, out, off, c.length);
                off += c.length;
            }
            if (i > 0) {
                System.arraycopy(cur, 0, out, off, i);
            }
            chunks.clear();
            cur = null;
            return out;
        }
    }
}
