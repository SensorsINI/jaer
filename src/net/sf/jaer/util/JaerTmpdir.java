package net.sf.jaer.util;

import java.io.File;

/**
 * jAER-owned temporary files live under {@code ${java.io.tmpdir}/jaer/}, not the
 * system temp root. Does <em>not</em> change {@code java.io.tmpdir}, so
 * third-party native unpackers (lz4, zstd-jni, etc.) keep using the OS temp
 * directory.
 */
public final class JaerTmpdir {

    /** Subfolder name under the system temporary directory. */
    public static final String DIR_NAME = "jaer";

    /**
     * Playback index-cache subfolder under {@link #get()}
     * ({@code *.aedat4idx}, {@code *.rosbagidx}, {@code *.metavisionrawidx}).
     */
    public static final String AEIDX_DIR_NAME = "aeidx";

    /**
     * Playback IN/OUT/other-marks CSV subfolder under {@link #get()}
     * ({@code *.marks.csv}).
     */
    public static final String MARKERS_DIR_NAME = "markers";

    private static volatile File cached;
    private static volatile File cachedAeidx;
    private static volatile File cachedMarkers;

    private JaerTmpdir() {
    }

    /**
     * {@code ${java.io.tmpdir}/jaer}, created if needed. Falls back to the
     * system temp directory if the subfolder cannot be created.
     */
    public static File get() {
        File d = cached;
        if (d != null) {
            return d;
        }
        synchronized (JaerTmpdir.class) {
            if (cached != null) {
                return cached;
            }
            File systemTmp = new File(System.getProperty("java.io.tmpdir", "."));
            File jaerTmp = DIR_NAME.equalsIgnoreCase(systemTmp.getName())
                    ? systemTmp
                    : new File(systemTmp, DIR_NAME);
            if (!jaerTmp.isDirectory() && !jaerTmp.mkdirs()) {
                System.err.println("JaerTmpdir: could not create " + jaerTmp.getAbsolutePath()
                        + "; using system temp " + systemTmp.getAbsolutePath());
                cached = systemTmp;
            } else {
                cached = jaerTmp;
            }
            return cached;
        }
    }

    /** {@code new File(get(), name)}. */
    public static File file(String name) {
        return new File(get(), name);
    }

    /**
     * {@code ${java.io.tmpdir}/jaer/aeidx}, created if needed. Falls back to
     * {@link #get()} if the subfolder cannot be created.
     */
    public static File aeidx() {
        File d = cachedAeidx;
        if (d != null) {
            return d;
        }
        synchronized (JaerTmpdir.class) {
            if (cachedAeidx != null) {
                return cachedAeidx;
            }
            File dir = new File(get(), AEIDX_DIR_NAME);
            if (!dir.isDirectory() && !dir.mkdirs()) {
                System.err.println("JaerTmpdir: could not create " + dir.getAbsolutePath()
                        + "; using " + get().getAbsolutePath());
                cachedAeidx = get();
            } else {
                cachedAeidx = dir;
            }
            return cachedAeidx;
        }
    }

    /** Write location for a playback index cache: {@code new File(aeidx(), name)}. */
    public static File aeidxFile(String name) {
        return new File(aeidx(), name);
    }

    /**
     * {@code ${java.io.tmpdir}/jaer/markers}, created if needed. Falls back to
     * {@link #get()} if the subfolder cannot be created.
     */
    public static File markers() {
        File d = cachedMarkers;
        if (d != null) {
            return d;
        }
        synchronized (JaerTmpdir.class) {
            if (cachedMarkers != null) {
                return cachedMarkers;
            }
            File dir = new File(get(), MARKERS_DIR_NAME);
            if (!dir.isDirectory() && !dir.mkdirs()) {
                System.err.println("JaerTmpdir: could not create " + dir.getAbsolutePath()
                        + "; using " + get().getAbsolutePath());
                cachedMarkers = get();
            } else {
                cachedMarkers = dir;
            }
            return cachedMarkers;
        }
    }

    /** Write location for a playback marks CSV: {@code new File(markers(), name)}. */
    public static File markersFile(String name) {
        return new File(markers(), name);
    }

    /**
     * Existing marks CSV: prefer {@code jaer/markers/name}, then {@code jaer/name},
     * then the system temp root. Does not move files from the older locations.
     */
    public static File resolveMarkers(String name) {
        File preferred = markersFile(name);
        if (preferred.isFile()) {
            return preferred;
        }
        File inJaerRoot = file(name);
        if (inJaerRoot.isFile() && !inJaerRoot.equals(preferred)) {
            return inJaerRoot;
        }
        File systemRoot = new File(systemTmp(), name);
        return systemRoot.isFile() ? systemRoot : preferred;
    }

    /**
     * HotSpot {@code -XX:ErrorFile} value ({@code hs_err_pid%p.log} under
     * {@link #get()}). {@code %p} is replaced by the crashed PID.
     */
    public static String errorFilePath() {
        return file("hs_err_pid%p.log").getAbsolutePath();
    }

    /**
     * HotSpot {@code -XX:ReplayDataFile} value ({@code replay_pid%p.log} under
     * {@link #get()}).
     */
    public static String replayDataFilePath() {
        return file("replay_pid%p.log").getAbsolutePath();
    }

    /**
     * Existing index cache: prefer {@code jaer/aeidx/name}, then {@code jaer/name},
     * then the system temp root. Does not move files from the older locations.
     */
    public static File resolveAeidx(String name) {
        File preferred = aeidxFile(name);
        if (preferred.isFile()) {
            return preferred;
        }
        File inJaerRoot = file(name);
        if (inJaerRoot.isFile() && !inJaerRoot.equals(preferred)) {
            return inJaerRoot;
        }
        File systemRoot = new File(systemTmp(), name);
        return systemRoot.isFile() ? systemRoot : preferred;
    }

    /**
     * Absolute path of {@link #get()} with a trailing separator, for JUL
     * {@code FileHandler} patterns that need {@code %t/jaer/...}.
     */
    public static String path() {
        String p = get().getAbsolutePath();
        if (p.endsWith(File.separator)) {
            return p;
        }
        return p + File.separator;
    }

    /** System {@code java.io.tmpdir} (parent of {@link #get()} when nested). */
    public static File systemTmp() {
        return new File(System.getProperty("java.io.tmpdir", "."));
    }
}
