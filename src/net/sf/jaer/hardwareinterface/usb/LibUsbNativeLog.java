package net.sf.jaer.hardwareinterface.usb;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.usb4java.LibUsb;

/**
 * Forwards libusb log lines into the jAER logger when that logger is at FINE
 * or below. Installed once, after {@link LibUsb#init}, and only when the
 * loaded native is libusb 1.0.30 or newer.
 * <p>
 * The callback runs on a libusb thread and only writes to the logger.
 */
final class LibUsbNativeLog {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static volatile boolean installed;

    private LibUsbNativeLog() {
    }

    static void installIfFine() {
        if (installed || !log.isLoggable(Level.FINE) || !LibUsbRuntime.atLeast130()) {
            return;
        }
        try {
            int libusbLevel = log.isLoggable(Level.FINER)
                    ? LibUsb.LOG_LEVEL_DEBUG
                    : LibUsb.LOG_LEVEL_INFO;
            int option = LibUsb.setOption(null, LibUsb.OPTION_LOG_LEVEL, libusbLevel);
            if (option != LibUsb.SUCCESS) {
                log.fine("libusb log level not set: " + LibUsb.errorName(option));
            }
            LibUsb.setLogCallback(null, (context, level, message) -> {
                if (message == null || message.isEmpty()) {
                    return;
                }
                log.log(julLevel(level), "libusb: " + message);
            }, LibUsb.LOG_CB_GLOBAL);
            installed = true;
            log.fine("libusb log callback installed at "
                    + (libusbLevel == LibUsb.LOG_LEVEL_DEBUG ? "debug" : "info"));
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            log.log(Level.FINE, "libusb log callback not installed", e);
        }
    }

    /** libusb info at FINE, libusb debug at FINER. Errors and warnings stay at FINE. */
    static Level julLevel(int libusbLevel) {
        if (libusbLevel >= LibUsb.LOG_LEVEL_DEBUG) {
            return Level.FINER;
        }
        return Level.FINE;
    }
}
