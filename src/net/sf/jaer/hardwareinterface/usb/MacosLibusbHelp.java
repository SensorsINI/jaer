/*
 * MacosLibusbHelp.java
 *
 * Once-per-JVM dialog when Apple Silicon cannot load libusb4java because
 * Homebrew libusb is missing (jars/libusb4java-*-darwin-aarch64.jar links
 * /opt/homebrew/opt/libusb/lib/libusb-1.0.0.dylib).
 */
package net.sf.jaer.hardwareinterface.usb;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import net.sf.jaer.util.MessageWithLink;

/**
 * Guides Apple Silicon users to install Homebrew libusb when the bundled
 * libusb4java JNI wrapper cannot dlopen libusb-1.0.0.dylib.
 */
public final class MacosLibusbHelp {

    public static final String BREW_INSTALL_LIBUSB = "brew install libusb";
    public static final String ANT_INSTALL_LIBUSB = "ant install-macos-libusb";
    public static final String LIBUSB_DYLIB = "/opt/homebrew/opt/libusb/lib/libusb-1.0.0.dylib";
    public static final String BREW_LIBUSB_URL = "https://formulae.brew.sh/formula/libusb";
    public static final String HOMEBREW_URL = "https://brew.sh/";

    private static final AtomicBoolean DIALOG_SHOWN = new AtomicBoolean();

    private MacosLibusbHelp() {
    }

    /**
     * True on macOS running an aarch64/arm64 JVM (Apple Silicon).
     */
    public static boolean isAppleSiliconMac() {
        final String os = System.getProperty("os.name", "");
        if (!os.contains("Mac") && !os.contains("Darwin")) {
            return false;
        }
        final String arch = System.getProperty("os.arch", "").toLowerCase();
        return arch.contains("aarch64") || arch.contains("arm64");
    }

    /**
     * Once per JVM, if this looks like a missing Homebrew libusb on Apple
     * Silicon, post a how-to dialog (on the EDT if needed).
     */
    public static void maybeShowDialog(final Throwable error) {
        maybeShowDialog(null, error);
    }

    public static void maybeShowDialog(final Component parent, final Throwable error) {
        if (!isAppleSiliconMac() || !looksLikeMissingHomebrewLibusb(error)) {
            return;
        }
        if (!DIALOG_SHOWN.compareAndSet(false, true)) {
            return;
        }
        final Runnable r = () -> showDialog(parent);
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(r);
            } catch (final InvocationTargetException | InterruptedException e) {
                System.err.println("MacosLibusbHelp: could not show dialog: " + e);
                System.exit(1);
            }
        }
    }

    static boolean looksLikeMissingHomebrewLibusb(final Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            final String m = t.getMessage();
            if (m == null) {
                continue;
            }
            if (m.contains("libusb-1.0.0.dylib") || m.contains("/opt/homebrew/opt/libusb")
                    || m.contains("brew install libusb")) {
                return true;
            }
        }
        return false;
    }

    private static void showDialog(final Component parent) {
        final String commands = BREW_INSTALL_LIBUSB + "\n"
                + "# developers, from the jAER tree (same command, via Ant):\n"
                + ANT_INSTALL_LIBUSB + "\n";

        final JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        final MessageWithLink intro = new MessageWithLink(
                "jAER cannot start on this Apple Silicon Mac: the USB native library"
                + " failed to load, so the viewer never opens"
                + " (file playback included).<br><br>"
                + "The bundled <code>libusb4java</code> JNI wrapper links Homebrew"
                + " <code>libusb</code> at<br><code>" + LIBUSB_DYLIB + "</code>, which is missing.<br><br>"
                + "This process cannot recover after a failed native load — "
                + "<b>OK will quit jAER</b>. Install libusb, then start jAER again.<br><br>"
                + "<b>1.</b> Install <a href=\"" + HOMEBREW_URL + "\">Homebrew</a> if needed.<br>"
                + "<b>2.</b> Copy the command below (Homebrew refuses sudo).<br>"
                + "<b>3.</b> Click OK to quit, run the command in Terminal, then start jAER again.<br><br>"
                + "Developers: <code>ant run</code> installs this automatically if Homebrew is present."
                + " Formula: <a href=\"" + BREW_LIBUSB_URL + "\">libusb</a>.");
        intro.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(intro);
        panel.add(Box.createVerticalStrut(8));

        final JTextArea area = new JTextArea(commands);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setLineWrap(false);
        area.selectAll();
        final JScrollPane scroll = new JScrollPane(area);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.setPreferredSize(new Dimension(520, 70));
        panel.add(scroll);
        panel.add(Box.createVerticalStrut(8));

        final JButton copy = new JButton("Copy commands");
        copy.setAlignmentX(Component.LEFT_ALIGNMENT);
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(area.getText()), null);
            copy.setText("Copied");
        });
        panel.add(copy);

        final JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        wrap.add(panel, BorderLayout.CENTER);

        JOptionPane.showMessageDialog(parent, wrap,
                "Homebrew libusb required (Apple Silicon) — OK quits jAER",
                JOptionPane.WARNING_MESSAGE);
        // LibUsb class init already failed; dyld will not pick up a newly
        // installed libusb in this JVM. Exit so the next launch can load it.
        System.exit(1);
    }
}
