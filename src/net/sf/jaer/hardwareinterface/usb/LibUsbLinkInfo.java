package net.sf.jaer.hardwareinterface.usb;

import java.nio.ByteBuffer;
import java.util.logging.Logger;

import org.apache.commons.text.WordUtils;
import org.usb4java.BufferUtils;
import org.usb4java.Device;
import org.usb4java.DeviceDescriptor;
import org.usb4java.LibUsb;

/**
 * Formats libusb link topology so a camera open log shows negotiated speed,
 * device USB version, and bus/port path.
 * <p>
 * {@link LibUsb#getDeviceSpeed} is the <em>negotiated</em> link (what the
 * port/cable/hub actually trained), not marketing names on the connector.
 * SuperSpeed 5&nbsp;Gbit/s is USB 3.0, 3.1 Gen1, and 3.2 Gen1 — libusb does not
 * distinguish those. SuperSpeed+ 10&nbsp;Gbit/s is USB 3.1 Gen2 / 3.2 Gen2.
 * {@link DeviceDescriptor#bcdUSB()} is what the device advertises, which can
 * still say 3.x when the link fell back to USB 2.0 High Speed.
 */
public final class LibUsbLinkInfo {

    private static volatile Snapshot lastOpen;

    private LibUsbLinkInfo() {
    }

    /**
     * Last successful {@link #logOnOpen} snapshot, or {@code null}. Used for the
     * ChipCanvas overlay and Interface-menu tooltip after AEChip matching.
     */
    public static Snapshot lastOpen() {
        return lastOpen;
    }

    /**
     * Log negotiated USB speed and topology after a successful libusb open.
     *
     * @param log logger of the hardware interface
     * @param label camera or interface name (appears at the start of the line)
     * @param device libusb device, or {@code null} to skip
     * @param descriptor already-read descriptor, or {@code null} to fetch one
     */
    public static void logOnOpen(Logger log, String label, Device device, DeviceDescriptor descriptor) {
        if (log == null || device == null) {
            return;
        }
        try {
            Snapshot snap = capture(device, descriptor);
            lastOpen = snap;
            log.info(label + " USB link: " + snap.logLine());
            if (snap.downgradeHint != null) {
                log.warning(label + " " + snap.downgradeHint);
            }
        } catch (RuntimeException e) {
            log.fine(label + " USB link info unavailable: " + e);
        }
    }

    /** Capture speed and topology without logging. */
    public static Snapshot capture(Device device, DeviceDescriptor descriptor) {
        if (device == null) {
            return null;
        }
        int speed = LibUsb.getDeviceSpeed(device);
        int bcd = bcdUsbOf(device, descriptor);
        int bus = -1;
        int addr = -1;
        int port = -1;
        String path = "";
        try {
            bus = LibUsb.getBusNumber(device);
            addr = LibUsb.getDeviceAddress(device);
            port = LibUsb.getPortNumber(device);
            ByteBuffer ports = BufferUtils.allocateByteBuffer(8);
            int n = LibUsb.getPortNumbers(device, ports);
            if (n > 0) {
                StringBuilder p = new StringBuilder();
                for (int i = 0; i < n; i++) {
                    if (i > 0) {
                        p.append('.');
                    }
                    p.append(ports.get(i) & 0xff);
                }
                path = p.toString();
            }
        } catch (RuntimeException e) {
            // topology optional
        }
        String parentKind = null;
        int parentSpeed = -1;
        try {
            Device parent = LibUsb.getParent(device);
            if (parent != null) {
                parentKind = "parent";
                DeviceDescriptor pd = new DeviceDescriptor();
                if (LibUsb.getDeviceDescriptor(parent, pd) == LibUsb.SUCCESS
                        && (pd.bDeviceClass() & 0xff) == (LibUsb.CLASS_HUB & 0xff)) {
                    parentKind = "parent hub";
                }
                parentSpeed = LibUsb.getDeviceSpeed(parent);
            }
        } catch (RuntimeException e) {
            // root of tree
        }
        return new Snapshot(speed, bcd, bus, addr, port, path, parentKind, parentSpeed);
    }

    /**
     * One-line description: speed, device bcdUSB, bus/addr/port, parent hub speed.
     */
    public static String describe(Device device, DeviceDescriptor descriptor) {
        Snapshot snap = capture(device, descriptor);
        return snap == null ? "no libusb device" : snap.logLine();
    }

    /** Negotiated {@link LibUsb#getDeviceSpeed} as a user-facing label. */
    public static String speedLabel(int speed) {
        if (speed == LibUsb.SPEED_LOW) {
            return "Low Speed 1.5 Mbit/s (USB 1.0)";
        }
        if (speed == LibUsb.SPEED_FULL) {
            return "Full Speed 12 Mbit/s (USB 1.1)";
        }
        if (speed == LibUsb.SPEED_HIGH) {
            return "High Speed 480 Mbit/s (USB 2.0)";
        }
        if (speed == LibUsb.SPEED_SUPER) {
            return "SuperSpeed 5 Gbit/s (USB 3.0 / 3.1 Gen1 / 3.2 Gen1)";
        }
        if (speed == LibUsb.SPEED_SUPER_PLUS) {
            return "SuperSpeed+ 10 Gbit/s (USB 3.1 Gen2 / 3.2 Gen2)";
        }
        if (speed == LibUsb.SPEED_UNKNOWN) {
            return "unknown speed";
        }
        return "speed code " + speed;
    }

    /**
     * Device-advertised USB version from {@code bcdUSB} (BCD, e.g. 0x0320 → USB 3.20).
     */
    public static String bcdUsbLabel(int bcdUsb) {
        int bcd = bcdUsb & 0xffff;
        int major = (bcd >> 8) & 0xff;
        int minor = bcd & 0xff;
        return String.format("USB %d.%02x (bcdUSB=0x%04x)", major, minor, bcd);
    }

    /** Short device USB version without the hex, e.g. {@code USB 3.00}. */
    public static String bcdUsbShort(int bcdUsb) {
        int bcd = bcdUsb & 0xffff;
        return String.format("USB %d.%02x", (bcd >> 8) & 0xff, bcd & 0xff);
    }

    /**
     * Hint when a USB 3 device trained at USB 2 (or slower). {@code null} if the
     * negotiated speed matches a USB 3 device, or the device is USB 2.
     */
    public static String downgradeHint(int speed, int bcdUsb) {
        if (bcdUsb < 0x0300) {
            return null;
        }
        if (speed == LibUsb.SPEED_SUPER || speed == LibUsb.SPEED_SUPER_PLUS) {
            return null;
        }
        if (speed == LibUsb.SPEED_UNKNOWN) {
            return null;
        }
        return "device reports " + bcdUsbLabel(bcdUsb)
                + " but the link is " + speedLabel(speed)
                + ". Try a SuperSpeed port (blue USB-A or USB-C), a SuperSpeed cable, "
                + "and avoid USB 2 hubs — a USB 2 port/hub/cable caps the camera at 480 Mbit/s.";
    }

    /**
     * Immutable USB link facts captured at open. Safe to keep after the libusb
     * {@link Device} is no longer used.
     */
    public static final class Snapshot {
        public final int speed;
        public final int bcdUsb;
        public final int bus;
        public final int addr;
        public final int port;
        public final String path;
        public final String parentKind;
        public final int parentSpeed;
        public final String downgradeHint;

        Snapshot(int speed, int bcdUsb, int bus, int addr, int port, String path,
                String parentKind, int parentSpeed) {
            this.speed = speed;
            this.bcdUsb = bcdUsb;
            this.bus = bus;
            this.addr = addr;
            this.port = port;
            this.path = path == null ? "" : path;
            this.parentKind = parentKind;
            this.parentSpeed = parentSpeed;
            this.downgradeHint = LibUsbLinkInfo.downgradeHint(speed, bcdUsb);
        }

        /** Console / log one-liner. */
        public String logLine() {
            StringBuilder sb = new StringBuilder(speedLabel(speed));
            if (bcdUsb >= 0) {
                sb.append("; device ").append(bcdUsbLabel(bcdUsb));
            }
            if (bus >= 0) {
                sb.append(String.format("; bus=%d addr=%d port=%d", bus, addr, port));
            }
            if (!path.isEmpty()) {
                sb.append(" path=").append(path);
            }
            if (parentKind != null && parentSpeed >= 0) {
                sb.append("; ").append(parentKind).append(' ').append(speedLabel(parentSpeed));
            }
            return sb.toString();
        }

        /** ChipCanvas overlay; long lines wrap at word breaks ({@link WordUtils#wrap}). */
        public String overlayText() {
            StringBuilder sb = new StringBuilder();
            sb.append(speedLabel(speed));
            if (bcdUsb >= 0) {
                sb.append('\n').append("device ").append(bcdUsbShort(bcdUsb));
            }
            if (bus >= 0) {
                sb.append('\n').append("bus ").append(bus).append("  port ").append(port);
                if (!path.isEmpty()) {
                    sb.append("  path ").append(path);
                }
            }
            if (parentKind != null && parentSpeed >= 0) {
                sb.append('\n').append(parentKind).append(' ').append(speedLabel(parentSpeed));
            }
            if (downgradeHint != null) {
                sb.append('\n').append("USB 2 link — try SuperSpeed port, cable, or hub");
            }
            return wrapLines(sb.toString(), 42);
        }

        /**
         * HTML tooltip for the selected Interface-menu item (about 1–3 lines).
         */
        public String tooltipHtml() {
            StringBuilder sb = new StringBuilder();
            sb.append(speedLabel(speed));
            StringBuilder line2 = new StringBuilder();
            if (bcdUsb >= 0) {
                line2.append("device ").append(bcdUsbShort(bcdUsb));
            }
            if (bus >= 0) {
                if (line2.length() > 0) {
                    line2.append(" · ");
                }
                line2.append("bus ").append(bus).append(" port ").append(port);
                if (!path.isEmpty()) {
                    line2.append(" path ").append(path);
                }
            }
            if (line2.length() > 0) {
                sb.append('\n').append(line2);
            }
            if (downgradeHint != null) {
                sb.append('\n').append("USB 2 link — try SuperSpeed port, cable, or hub");
            } else if (parentKind != null && parentSpeed >= 0) {
                sb.append('\n').append(parentKind).append(' ').append(speedLabel(parentSpeed));
            }
            return "<html>" + wrapLines(sb.toString(), 52).replace("\n", "<br>");
        }

        static String wrapLines(String text, int width) {
            if (text == null || text.isEmpty()) {
                return "";
            }
            StringBuilder out = new StringBuilder();
            String[] lines = text.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) {
                    out.append('\n');
                }
                out.append(WordUtils.wrap(lines[i], width));
            }
            return out.toString();
        }
    }

    private static int bcdUsbOf(Device device, DeviceDescriptor descriptor) {
        DeviceDescriptor d = descriptor;
        if (d == null) {
            d = new DeviceDescriptor();
            int status = LibUsb.getDeviceDescriptor(device, d);
            if (status != LibUsb.SUCCESS) {
                return -1;
            }
        }
        return d.bcdUSB() & 0xffff;
    }
}
