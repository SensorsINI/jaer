/*
 * UsbIds.java
 *
 * Peek VID/PID from a USBInterface without requiring a full device open.
 */
package net.sf.jaer.hardwareinterface.usb;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.usb4java.Device;
import org.usb4java.LibUsb;

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
                log.log(Level.FINE, "Could not read VID from " + hw.getClass().getSimpleName(), t2);
            }
        }
        try {
            pid = usb.getPID();
        } catch (Throwable t) {
            log.log(Level.FINE, "Could not read PID from " + hw.getClass().getSimpleName(), t);
        }
        return new Pair(vid, pid);
    }

    /**
     * Libusb {@link Device} when the implementation exposes {@code getLibUsbDevice()}.
     */
    public static Device libUsbDevice(HardwareInterface hw) {
        if (hw == null) {
            return null;
        }
        try {
            java.lang.reflect.Method m = hw.getClass().getMethod("getLibUsbDevice");
            Object o = m.invoke(hw);
            return (o instanceof Device) ? (Device) o : null;
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (Throwable t) {
            log.log(Level.FINE, "getLibUsbDevice failed on " + hw.getClass().getSimpleName(), t);
            return null;
        }
    }

    /**
     * True when both interfaces wrap the same USB bus address (not VID/PID alone).
     */
    public static boolean samePhysicalDevice(HardwareInterface a, HardwareInterface b) {
        if (a == null || b == null) {
            return false;
        }
        if (a == b) {
            return true;
        }
        Device da = libUsbDevice(a);
        Device db = libUsbDevice(b);
        if (da == null || db == null) {
            return false;
        }
        try {
            return LibUsb.getBusNumber(da) == LibUsb.getBusNumber(db)
                    && LibUsb.getDeviceAddress(da) == LibUsb.getDeviceAddress(db);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Menu label that does not {@code LibUsb.open}: type, VID:PID, bus/addr.
     */
    public static String unopenedLabel(HardwareInterface hw, String typeName) {
        String name = (typeName == null || typeName.isBlank()) ? "USB" : typeName;
        if (hw == null) {
            return name;
        }
        Pair ids = hw instanceof USBInterface ? peek(hw) : new Pair((short) 0, (short) 0);
        Device d = libUsbDevice(hw);
        String topo = "";
        if (d != null) {
            try {
                topo = String.format(" bus%d-addr%d",
                        LibUsb.getBusNumber(d) & 0xff, LibUsb.getDeviceAddress(d) & 0xff);
            } catch (RuntimeException ignored) {
            }
        }
        if (ids.isKnown()) {
            return name + " " + ids.key() + topo;
        }
        return name + topo;
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
