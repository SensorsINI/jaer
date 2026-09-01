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

    private static volatile File cached;
    private static volatile File cachedAeidx;

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
