/*
 * UsbIds.java
 *
 * Peek VID/PID from a USBInterface without requiring a full device open.
 */
package net.sf.jaer.hardwareinterface.usb;

import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.jaer.hardwareinterface.HardwareInterface;

/**
 * Helpers to read USB vendor/product IDs from an enumerated interface. Libusb
 * implementations often leave the device descriptor null until {@code open()};
 * this class peeks the descriptor when possible.
 */
public final class UsbIds {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    /** Immutable VID/PID pair (0,0 if unknown). */
    public static final class Pair {
        public final short vid;
        public final short pid;

        public Pair(short vid, short pid) {
            this.vid = vid;
            this.pid = pid;
        }

        public boolean isKnown() {
            return vid != 0 || pid != 0;
        }

        public String key() {
            return String.format("%04x:%04x", vid & 0xffff, pid & 0xffff);
        }

        @Override
        public String toString() {
            return key();
        }
    }

    private UsbIds() {
    }

    /**
     * Best-effort VID/PID from a hardware interface. Returns (0,0) if not a
     * {@link USBInterface} or descriptors are unavailable.
     */
    public static Pair peek(HardwareInterface hw) {
        if (!(hw instanceof USBInterface)) {
            return new Pair((short) 0, (short) 0);
        }
        USBInterface usb = (USBInterface) hw;
        ensureDescriptor(usb);
        short vid = 0;
        short pid = 0;
        try {
            vid = usb.getVID();
        } catch (Throwable t) {
            try {
                vid = usb.getVID_THESYCON_FX2_CPLD();
            } catch (Throwable t2) {
                log.log(Level.FINE, "Could not read VID from " + hw, t2);
            }
        }
        try {
            pid = usb.getPID();
        } catch (Throwable t) {
            log.log(Level.FINE, "Could not read PID from " + hw, t);
        }
        return new Pair(vid, pid);
    }

    /**
     * Ask known libusb USBInterface implementations to populate their device
     * descriptor without claiming the interface for AE streaming.
     */
    private static void ensureDescriptor(USBInterface usb) {
        try {
            // CypressFX3 / CypressFX2 libusb / NRV / Prophesee expose getPID which
            // may warn if descriptor is null; call ensureUsbDeviceDescriptor if present.
            java.lang.reflect.Method m = usb.getClass().getMethod("ensureUsbDeviceDescriptor");
            m.invoke(usb);
        } catch (NoSuchMethodException ignored) {
            // USBIO and other stacks may already have descriptors after construction
        } catch (Throwable t) {
            log.log(Level.FINE, "ensureUsbDeviceDescriptor failed on " + usb.getClass().getSimpleName(), t);
        }
    }
}
