package net.sf.jaer.eventio.dsec;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

import io.jhdf.HdfFile;
import io.jhdf.api.Dataset;

/**
 * Writes a tiny DSEC-layout HDF5 and reads it back (no AEChip required).
 * Run: {@code java -cp ... net.sf.jaer.eventio.dsec.DsecHdf5WriteRoundTrip}
 */
public final class DsecHdf5WriteRoundTrip {

    public static void main(String[] args) throws Exception {
        File f = Files.createTempFile("dsec-roundtrip-", ".h5").toFile();
        f.deleteOnExit();
        int[] tAbs = {1_000, 1_500, 2_100};
        short[] x = {10, 20, 30};
        short[] y = {40, 50, 60};
        byte[] p = {0, 1, 0};
        DsecHdf5AEOutputStream.writeSynthetic(f, 640, 480, tAbs, x, y, p);
        if (!DsecHdf5AEInputStream.isDsecEventsFile(f)) {
            throw new IllegalStateException("written file is not recognized as DSEC: " + f);
        }
        DsecHdf5AEInputStream.SensorSize sz = DsecHdf5AEInputStream.peekSensorSize(f);
        if (sz == null || sz.width != 640 || sz.height != 480) {
            throw new IllegalStateException("peekSensorSize expected 640x480, got " + sz);
        }
        try (HdfFile h = new HdfFile(f.toPath())) {
            Dataset dt = (Dataset) h.getByPath("/events/t");
            Dataset dx = (Dataset) h.getByPath("/events/x");
            Dataset dy = (Dataset) h.getByPath("/events/y");
            Dataset dp = (Dataset) h.getByPath("/events/p");
            Dataset off = (Dataset) h.getByPath("/t_offset");
            Dataset ms = (Dataset) h.getByPath("/ms_to_idx");
            if (dt == null || dx == null || dy == null || dp == null || off == null || ms == null) {
                throw new IllegalStateException("missing DSEC datasets in " + f);
            }
            int[] t = toInt(dt.getData());
            if (!Arrays.equals(t, new int[]{0, 500, 1100})) {
                throw new IllegalStateException("relative t expected [0,500,1100], got " + Arrays.toString(t));
            }
            long tOff = toLong(off.getData());
            if (tOff != 1_000) {
                throw new IllegalStateException("t_offset expected 1000, got " + tOff);
            }
        }
        System.out.println("DSEC HDF5 round-trip OK: " + f.getAbsolutePath());

        File empty = Files.createTempFile("dsec-empty-", ".h5").toFile();
        empty.deleteOnExit();
        try (DsecHdf5AEOutputStream out = new DsecHdf5AEOutputStream(empty, 346, 260)) {
            // no events: close must not throw (jHDF 0.12 cannot write zero-length arrays)
        }
        System.out.println("DSEC HDF5 empty close OK: " + empty.getAbsolutePath());
    }

    private static int[] toInt(Object data) {
        if (data instanceof int[]) {
            return (int[]) data;
        }
        if (data instanceof short[]) {
            short[] s = (short[]) data;
            int[] o = new int[s.length];
            for (int i = 0; i < s.length; i++) {
                o[i] = s[i] & 0xffff;
            }
            return o;
        }
        throw new IllegalStateException("unexpected t type " + (data == null ? "null" : data.getClass()));
    }

    private static long toLong(Object data) {
        if (data instanceof Number) {
            return ((Number) data).longValue();
        }
        if (data instanceof long[] && ((long[]) data).length > 0) {
            return ((long[]) data)[0];
        }
        if (data instanceof int[] && ((int[]) data).length > 0) {
            return ((int[]) data)[0];
        }
        throw new IllegalStateException("unexpected t_offset type " + (data == null ? "null" : data.getClass()));
    }
}
