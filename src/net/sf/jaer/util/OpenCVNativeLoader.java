package net.sf.jaer.util;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opencv.core.Core;

/**
 * Loads OpenCV native libraries from the openpnp {@code opencv-4.8.1-0.jar} bundle.
 * <p>
 * On Java 12+, {@code OpenCV.loadShared()} logs a SEVERE message and falls back anyway;
 * {@code OpenCV.loadLocally()} is the supported path and extracts the platform DLL/SO
 * from {@code nu/pattern/opencv/...} inside the jar.
 */
public final class OpenCVNativeLoader {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final AtomicBoolean loaded = new AtomicBoolean(false);
    private static volatile boolean available;

    private OpenCVNativeLoader() {
    }

    /** Loads OpenCV once; safe to call from multiple static initializers. */
    public static boolean load() {
        if (loaded.get()) {
            return available;
        }
        synchronized (OpenCVNativeLoader.class) {
            if (loaded.get()) {
                return available;
            }
            try {
                nu.pattern.OpenCV.loadLocally();
                available = true;
                log.info("Loaded OpenCV " + Core.VERSION);
            } catch (UnsatisfiedLinkError | Exception e) {
                available = false;
                log.log(Level.WARNING,
                        "Native OpenCV library failed to load: {0}. "
                                + "OpenCV-based filters (calibration, optical flow) will not work. "
                                + "See https://github.com/openpnp/opencv",
                        e.toString());
            }
            loaded.set(true);
            return available;
        }
    }

    public static boolean isAvailable() {
        return available;
    }
}
