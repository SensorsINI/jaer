package net.sf.jaer.hardwareinterface.usb;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.usb4java.LibUsb;
import org.usb4java.Version;

/**
 * Whether the loaded libusb native is new enough for the 1.0.30 JNI methods.
 * {@link LibUsb#getVersion()} exists on the older natives, so a 1.0.22 library
 * skips those methods instead of throwing {@link UnsatisfiedLinkError}.
 */
final class LibUsbRuntime {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static volatile Boolean atLeast130;

    private LibUsbRuntime() {
    }

    static boolean atLeast130() {
        Boolean cached = atLeast130;
        if (cached != null) {
            return cached.booleanValue();
        }
        boolean ok = false;
        try {
            Version v = LibUsb.getVersion();
            ok = v != null && atLeast(v.major(), v.minor(), v.micro(), 1, 0, 30);
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            log.log(Level.FINE, "libusb version unavailable", e);
        }
        atLeast130 = Boolean.valueOf(ok);
        return ok;
    }

    private static boolean atLeast(int major, int minor, int micro, int needMajor, int needMinor, int needMicro) {
        if (major != needMajor) {
            return major > needMajor;
        }
        if (minor != needMinor) {
            return minor > needMinor;
        }
        return micro >= needMicro;
    }
}
