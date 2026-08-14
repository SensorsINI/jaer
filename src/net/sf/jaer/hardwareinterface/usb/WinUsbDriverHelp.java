/*
 * WinUsbDriverHelp.java
 *
 * Once-per-JVM Windows dialog when libusb cannot open a supported camera
 * because WinUSB is not bound (LIBUSB_ERROR_NOT_SUPPORTED).
 */
package net.sf.jaer.hardwareinterface.usb;

import java.awt.Component;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.util.MessageWithLink;

/**
 * Guides Windows users to install WinUSB (Zadig, or Prophesee wdi-simple for
 * EVK4) when a jAER-supported USB camera cannot be opened via libusb.
 */
public final class WinUsbDriverHelp {

    /** Official Zadig download / home page. */
    public static final String ZADIG_URL = "https://zadig.akeo.ie/";
    /** libusb Windows backend notes (WinUSB recommended). */
    public static final String LIBUSB_WINDOWS_URL = "https://github.com/libusb/libusb/wiki/Windows";
    /** Prophesee OpenEB docs: Camera Plugins / wdi-simple on Windows. */
    public static final String PROPHESEE_CAMERA_PLUGINS_URL
            = "https://docs.prophesee.ai/stable/installation/windows_openeb.html#camera-plugins";
    /** Prophesee-hosted wdi-simple.exe (linked from OpenEB / Metavision docs). */
    public static final String PROPHESEE_WDI_SIMPLE_URL
            = "https://kdrive.infomaniak.com/app/share/975517/cb164518-e68f-49fd-a6a1-eea693783bd2";

    /** Cypress VID used by Prophesee EVK4 and NRV cameras. */
    private static final short CYPRESS_VID = (short) 0x04B4;
    private static final short PROPHESEE_EVK4_PID = (short) 0x00F5;

    private static final AtomicBoolean DIALOG_SHOWN = new AtomicBoolean();

    private WinUsbDriverHelp() {
    }

    /**
     * Once per JVM, on Windows {@code LIBUSB_ERROR_NOT_SUPPORTED} for a
     * registered USB camera, post a WinUSB install how-to dialog on the EDT.
     * Safe to call from ViewLoop.
     *
     * @param parent dialog parent (e.g. AEViewer)
     * @param hw     interface that failed to open (may be null)
     * @param error  open failure (message should contain {@code LIBUSB_ERROR_NOT_SUPPORTED})
     */
    public static void maybeShowDialog(final Component parent, final HardwareInterface hw,
            final Throwable error) {
        if (error == null || error.getMessage() == null
                || !error.getMessage().contains("LIBUSB_ERROR_NOT_SUPPORTED")) {
            return;
        }
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            return;
        }
        if (!(hw instanceof USBInterface)) {
            return;
        }
        final UsbIds.Pair ids = UsbIds.peek(hw);
        final boolean registered = ids.isKnown()
                && UsbHardwareRegistry.instance().isSupported(ids.vid, ids.pid);
        if (!registered && !isKnownLibUsbCamera(hw)) {
            return;
        }
        if (!DIALOG_SHOWN.compareAndSet(false, true)) {
            return;
        }
        final String typeName = safeTypeName(hw);
        final short vid = ids.isKnown() ? ids.vid : (short) 0;
        final short pid = ids.isKnown() ? ids.pid : (short) 0;
        final Runnable r = () -> showDialog(parent, typeName, vid, pid);
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    private static boolean isKnownLibUsbCamera(HardwareInterface hw) {
        final String n = hw.getClass().getName();
        return n.startsWith("prophesee.usb.") || n.startsWith("nrv.usb.")
                || n.contains("cypressfx2libusb") || n.contains("cypressfx3libusb");
    }

    private static String safeTypeName(HardwareInterface hw) {
        try {
            final String t = hw.getTypeName();
            if (t != null && !t.isEmpty()) {
                return t;
            }
        } catch (Throwable ignored) {
            // fall through
        }
        return hw.getClass().getSimpleName();
    }

    private static void showDialog(Component parent, String typeName, short vid, short pid) {
        final String vidPid = (vid != 0 || pid != 0)
                ? String.format("%04x:%04x", vid & 0xffff, pid & 0xffff)
                : "unknown";
        final boolean propheseeEvk4 = vid == CYPRESS_VID && pid == PROPHESEE_EVK4_PID;

        final StringBuilder html = new StringBuilder();
        html.append("jAER cannot open <b>").append(escape(typeName)).append("</b>");
        html.append(" (USB <code>").append(vidPid).append("</code>).<br>");
        html.append("Windows libusb reported <code>LIBUSB_ERROR_NOT_SUPPORTED</code> — ");
        html.append("usually this USB ID is not bound to <b>WinUSB</b> (<code>winusb.sys</code>).<br><br>");

        html.append("<b>1.</b> Download <a href=\"").append(ZADIG_URL).append("\">Zadig</a>");
        html.append(" from <a href=\"").append(ZADIG_URL).append("\">").append(ZADIG_URL).append("</a>.<br>");
        html.append("<b>2.</b> Run Zadig as Administrator → <i>Options → List All Devices</i>.<br>");
        html.append("<b>3.</b> Select USB ID <code>").append(vidPid).append("</code>, ");
        html.append("choose driver <b>WinUSB</b> (not libusb-win32 / libusb0), Install Driver.<br>");
        html.append("<b>4.</b> Unplug the camera, plug it back in, then choose it again from the");
        html.append(" Interface menu (or restart jAER).<br><br>");

        if (propheseeEvk4) {
            html.append("For Prophesee EVK4 you can instead install Prophesee's ");
            html.append("<a href=\"").append(PROPHESEE_WDI_SIMPLE_URL).append("\">wdi-simple</a>");
            html.append(" WinUSB package (see ");
            html.append("<a href=\"").append(PROPHESEE_CAMERA_PLUGINS_URL)
                    .append("\">OpenEB Windows camera plugins</a>");
            html.append(").<br><br>");
        }

        html.append("Background: <a href=\"").append(LIBUSB_WINDOWS_URL)
                .append("\">libusb on Windows</a>. ");
        html.append("If it still fails, close Metavision, flashy, caer, or another jAER instance.");

        JOptionPane.showMessageDialog(parent, new MessageWithLink(html.toString()),
                "Windows WinUSB driver required",
                JOptionPane.WARNING_MESSAGE);
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
