/*
 * UsbIds.java
 *
 * Peek VID/PID from a USBInterface without requiring a full device open.
 */
package net.sf.jaer.hardwareinterface.usb;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.usb4java.Device;
import org.usb4java.DeviceDescriptor;
import org.usb4java.LibUsb;

import net.sf.jaer.hardwareinterface.CompositeHardwareInterface;
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
     * {@link CompositeHardwareInterface} wrappers (stereo / FlyEye / multi-camera)
     * match if any child is the same physical device.
     */
    public static boolean samePhysicalDevice(HardwareInterface a, HardwareInterface b) {
        if (a == null || b == null) {
            return false;
        }
        if (a == b) {
            return true;
        }
        for (HardwareInterface ca : components(a)) {
            if (ca == null) {
                continue;
            }
            for (HardwareInterface cb : components(b)) {
                if (cb == null) {
                    continue;
                }
                if (ca == cb) {
                    return true;
                }
                Device da = libUsbDevice(ca);
                Device db = libUsbDevice(cb);
                if (da == null || db == null) {
                    continue;
                }
                try {
                    if (sameUsbPort(da, db)) {
                        return true;
                    }
                } catch (RuntimeException e) {
                    // ignore
                }
            }
        }
        return false;
    }

    /**
     * Unwrap a {@link CompositeHardwareInterface} to its children; otherwise
     * {@code hw} alone. Does not {@code LibUsb.open}.
     */
    public static HardwareInterface[] components(HardwareInterface hw) {
        if (hw instanceof CompositeHardwareInterface composite) {
            HardwareInterface[] c = composite.getComponentInterfaces();
            if (c != null && c.length > 0) {
                return c;
            }
        }
        return new HardwareInterface[] { hw };
    }

    /**
     * Same USB bus/addr. {@link Device#equals} is native-pointer identity; each
     * {@code LibUsb.getDeviceList} returns new JNI wrappers for the same port.
     */
    public static boolean sameUsbPort(Device a, Device b) {
        if (a == null || b == null) {
            return false;
        }
        if (a == b) {
            return true;
        }
        try {
            return LibUsb.getBusNumber(a) == LibUsb.getBusNumber(b)
                    && LibUsb.getDeviceAddress(a) == LibUsb.getDeviceAddress(b);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Merge a fresh libusb scan into {@code kept}. Keep existing Device objects
     * when the port is still present (LIVE wrappers hold those refs). Unref
     * scan duplicates and devices that disappeared.
     */
    public static void mergeLibUsbDeviceScan(List<Device> kept, List<Device> scanned) {
        if (kept == null || scanned == null) {
            return;
        }
        final List<Device> removals = new ArrayList<>();
        for (Device old : kept) {
            Device dup = takeSameUsbPort(scanned, old);
            if (dup != null) {
                LibUsb.unrefDevice(dup);
            } else {
                removals.add(old);
                LibUsb.unrefDevice(old);
            }
        }
        kept.removeAll(removals);
        kept.addAll(scanned);
        scanned.clear();
    }

    private static Device takeSameUsbPort(List<Device> scanned, Device old) {
        for (int i = 0; i < scanned.size(); i++) {
            if (sameUsbPort(scanned.get(i), old)) {
                return scanned.remove(i);
            }
        }
        return null;
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
     * Stable enumeration identity for last-interface mapping: class simple name
     * plus VID:PID and bus/addr. Does not {@code LibUsb.open}. Too long for
     * {@link java.util.prefs.Preferences} keys on Windows (max 80).
     */
    public static String enumerationKey(HardwareInterface hw) {
        if (hw == null) {
            return "";
        }
        return unopenedLabel(hw, hw.getClass().getSimpleName());
    }

    /**
     * Compact identity for Java Preferences: {@code vid:pid.Type.bNaM}. Strips
     * a trailing {@code HardwareInterface} so DVX Micro vs classic stay distinct
     * under {@link java.util.prefs.Preferences#MAX_KEY_LENGTH}. Does not
     * {@code LibUsb.open}.
     */
    public static String prefsKey(HardwareInterface hw) {
        if (hw == null) {
            return "";
        }
        Pair ids = hw instanceof USBInterface ? peek(hw) : new Pair((short) 0, (short) 0);
        String type = hw.getClass().getSimpleName();
        final String hi = "HardwareInterface";
        if (type.endsWith(hi)) {
            type = type.substring(0, type.length() - hi.length());
        }
        StringBuilder sb = new StringBuilder();
        sb.append(ids.isKnown() ? ids.key() : "0000:0000");
        sb.append('.').append(type);
        Device d = libUsbDevice(hw);
        if (d != null) {
            try {
                sb.append(".b").append(LibUsb.getBusNumber(d) & 0xff)
                        .append('a').append(LibUsb.getDeviceAddress(d) & 0xff);
            } catch (RuntimeException ignored) {
            }
        }
        return sb.toString();
    }

    /**
     * Fill a new {@link DeviceDescriptor} from libusb without claiming the
     * interface. Returns {@code null} when {@code device} is gone or the
     * native descriptor pointer was never initialized (unplug). Never returns
     * a descriptor whose {@code idProduct()} throws
     * {@code IllegalStateException}.
     *
     * @param device libusb device, or {@code null}
     * @return populated descriptor, or {@code null}
     */
    public static DeviceDescriptor readDeviceDescriptor(Device device) {
        if (device == null) {
            return null;
        }
        DeviceDescriptor d = new DeviceDescriptor();
        try {
            int status = LibUsb.getDeviceDescriptor(device, d);
            if (status != LibUsb.SUCCESS) {
                log.log(Level.FINE, "getDeviceDescriptor: {0}", LibUsb.errorName(status));
                return null;
            }
            d.idProduct();
            return d;
        } catch (IllegalStateException e) {
            log.log(Level.FINE, "USB device descriptor native pointer not initialized", e);
            return null;
        }
    }

    /**
     * True when field accessors on {@code d} will not throw because the
     * usb4java native pointer is missing (typical after unplug/close).
     *
     * @param d descriptor, or {@code null}
     * @return {@code false} if {@code d} is null or its native pointer is dead
     */
    public static boolean descriptorReadable(DeviceDescriptor d) {
        if (d == null) {
            return false;
        }
        try {
            d.idProduct();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
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
