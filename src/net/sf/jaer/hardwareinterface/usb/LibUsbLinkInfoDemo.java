package net.sf.jaer.hardwareinterface.usb;

import org.usb4java.LibUsb;

/**
 * Headless checks for {@link LibUsbLinkInfo} speed / bcdUSB labels (no device).
 *
 * <p>Run after {@code ant compile}:
 * {@code java -cp build/classes:jars/* net.sf.jaer.hardwareinterface.usb.LibUsbLinkInfoDemo}
 */
public final class LibUsbLinkInfoDemo {

    private LibUsbLinkInfoDemo() {
    }

    public static void main(String[] args) {
        require(LibUsbLinkInfo.speedLabel(LibUsb.SPEED_HIGH).contains("480"),
                "HIGH should mention 480 Mbit/s");
        require(LibUsbLinkInfo.speedLabel(LibUsb.SPEED_SUPER).contains("5 Gbit"),
                "SUPER should mention 5 Gbit/s");
        require(LibUsbLinkInfo.speedLabel(LibUsb.SPEED_SUPER_PLUS).contains("10 Gbit"),
                "SUPER_PLUS should mention 10 Gbit/s");
        require(LibUsbLinkInfo.speedLabel(LibUsb.SPEED_SUPER_PLUS_X2).contains("20 Gbit"),
                "SUPER_PLUS_X2 should mention 20 Gbit/s");
        require(LibUsbLinkInfo.downgradeHint(LibUsb.SPEED_SUPER_PLUS_X2, 0x0320) == null,
                "USB 3.2 device at Gen2x2 is not a downgrade");
        require(LibUsbLinkInfo.bcdUsbLabel(0x0320).contains("3.20"),
                "0x0320 is USB 3.20");
        require(LibUsbLinkInfo.downgradeHint(LibUsb.SPEED_SUPER, 0x0300) == null,
                "USB 3 device at SuperSpeed is not a downgrade");
        String hint = LibUsbLinkInfo.downgradeHint(LibUsb.SPEED_HIGH, 0x0300);
        require(hint != null && hint.contains("SuperSpeed port"),
                "USB 3 device at High Speed should warn about port/cable/hub");
        require(LibUsbLinkInfo.downgradeHint(LibUsb.SPEED_HIGH, 0x0200) == null,
                "USB 2 device at High Speed is expected");
        String overlay = new LibUsbLinkInfo.Snapshot(LibUsb.SPEED_SUPER, 0x0300, 2, 5, 3, "1.3",
                "parent hub", LibUsb.SPEED_SUPER).overlayText();
        require(overlay.contains("\n") && overlay.contains("SuperSpeed"),
                "overlay is multiline SuperSpeed text");
        String tip = new LibUsbLinkInfo.Snapshot(LibUsb.SPEED_HIGH, 0x0300, 1, 2, 1, "1",
                "parent hub", LibUsb.SPEED_HIGH).tooltipHtml();
        require(tip.startsWith("<html>") && tip.contains("<br>"),
                "tooltip is HTML with line breaks");
        require(tip.contains("USB 2 link"),
                "downgraded USB 3 device tooltip mentions USB 2 link");
        String tuning = new LibUsbLinkInfo.Snapshot(LibUsb.SPEED_SUPER_PLUS, 0x0320, 1, 14, 2, "2",
                "parent hub", LibUsb.SPEED_SUPER_PLUS).tuningText();
        require(tuning.contains("10 Gbit") && tuning.contains("USB 3.20") && tuning.contains("port=2"),
                "tuning text has negotiated speed, bcdUSB, and port");
        require(tuning.contains("parent hub"),
                "tuning text includes the parent hub");
        System.out.println("PASS LibUsbLinkInfo speed/bcdUSB labels");
    }

    private static void require(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }
}
