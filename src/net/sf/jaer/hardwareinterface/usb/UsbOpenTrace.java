/*
 * UsbOpenTrace.java — session log for multi-camera USB open serialization.
 */
package net.sf.jaer.hardwareinterface.usb;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.jaer.util.JaerTmpdir;

/**
 * Compact {@code ${java.io.tmpdir}/jaer/usb-open-trace.log} for one JVM session.
 * Expected: ui-restore (windows placed) → running → LIVE+acquiring per viewer.
 * Interface is user-open on that viewer (waits on USB_OPEN_SERIAL_LOCK if busy).
 */
public final class UsbOpenTrace {

    public static final String FILE_NAME = "usb-open-trace.log";
    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final Object LOCK = new Object();
    private static PrintWriter out;
    private static String lastKey;
    private static long lastMs;
    /** Repeat of the same line (LIVE acquire) is skipped for this long. */
    private static final long THROTTLE_MS = 5_000L;

    private UsbOpenTrace() {
    }

    /** Absolute path of this session's trace file. */
    public static File file() {
        return JaerTmpdir.file(FILE_NAME);
    }

    /**
     * @param phase    hold / wait / open / aereader / release / timeout / mismatch
     * @param expected what the serializer should be doing
     * @param actual   what happened (viewer, device, extra)
     */
    public static void event(String phase, String expected, String actual) {
        String key = phase + "|" + expected + "|" + actual;
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            if (key.equals(lastKey) && (now - lastMs) < THROTTLE_MS) {
                return;
            }
            lastKey = key;
            lastMs = now;
        }
        String line = TS.format(LocalDateTime.now()) + "  " + phase + "  expected=" + expected
                + "  actual=" + actual + "  " + UsbLog.t();
        // TODO comment out once USB open serialization is stable (LIVE acquire flooded INFO).
        log.fine("USB-OPEN-TRACE " + line);
        synchronized (LOCK) {
            try {
                if (out == null) {
                    File f = file();
                    out = new PrintWriter(new FileWriter(f, false), true);
                    out.println("# " + f.getAbsolutePath());
                    out.println("# expected: ui-restore -> running -> LIVE+acquiring");
                    out.println("# expected: Interface is user-open on that viewer (USB_OPEN_SERIAL_LOCK)");
                }
                out.println(line);
            } catch (IOException e) {
                log.log(Level.WARNING, "usb-open-trace.log write failed: " + e.getMessage());
            }
        }
    }
}
