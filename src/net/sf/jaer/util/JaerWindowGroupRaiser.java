package net.sf.jaer.util;

import java.awt.AWTEvent;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.WindowEvent;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.SwingUtilities;

import net.sf.jaer.JaerConstants;

/**
 * When any jAER {@link Frame} or {@link Dialog} is activated, raises the other
 * visible (non-iconified) frames and dialogs of this JVM so AEViewer, Biasgen,
 * Filters, and extra viewers are not left behind other applications.
 * <p>
 * Swing has no portable window-group API. {@link Window#toFront()} of siblings
 * works for a foreground process on Windows and typically on macOS; some Linux
 * window managers ignore it (focus-stealing prevention). {@link Window#setAutoRequestFocus(boolean)
 * setAutoRequestFocus(false)} avoids a focus cascade. Minimized windows stay
 * minimized. Popup/{@code JWindow} focus is ignored so menus are not dismissed.
 */
public final class JaerWindowGroupRaiser implements AWTEventListener {

    public static final String PREF_KEY = "raiseAllWindowsOnFocus";
    /** Default on: biasgen/filters otherwise stay behind other apps. */
    public static final boolean PREF_DEFAULT = true;

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final JaerWindowGroupRaiser INSTANCE = new JaerWindowGroupRaiser();
    private static volatile boolean installed;
    private static volatile boolean enabled = PREF_DEFAULT;
    private static volatile boolean raising;

    private JaerWindowGroupRaiser() {
    }

    /** JVM-wide listener; safe to call more than once. Reads the AEViewer prefs node. */
    public static void install() {
        enabled = loadPref();
        if (installed) {
            return;
        }
        installed = true;
        Toolkit.getDefaultToolkit().addAWTEventListener(INSTANCE, AWTEvent.WINDOW_EVENT_MASK);
        log.fine("JaerWindowGroupRaiser installed, enabled=" + enabled);
    }

    static boolean loadPref() {
        return prefsNode().getBoolean(PREF_KEY, PREF_DEFAULT);
    }

    static Preferences prefsNode() {
        return JaerConstants.PREFS_ROOT.node("AEViewer");
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean on) {
        enabled = on;
        prefsNode().putBoolean(PREF_KEY, on);
    }

    @Override
    public void eventDispatched(AWTEvent event) {
        if (!enabled || raising) {
            return;
        }
        if (event.getID() != WindowEvent.WINDOW_ACTIVATED) {
            return;
        }
        Window focused = ((WindowEvent) event).getWindow();
        if (!isFrameOrDialog(focused)) {
            return;
        }
        if (modalBlocksRaise(focused)) {
            return;
        }
        raiseOthers(focused);
    }

    private static void raiseOthers(Window focused) {
        raising = true;
        try {
            for (Window other : Window.getWindows()) {
                if (!shouldRaiseOther(focused, other)) {
                    continue;
                }
                raiseWithoutStealingFocus(other);
            }
            raiseWithoutStealingFocus(focused);
        } finally {
            SwingUtilities.invokeLater(() -> raising = false);
        }
    }

    private static void raiseWithoutStealingFocus(Window w) {
        boolean auto = w.isAutoRequestFocus();
        w.setAutoRequestFocus(false);
        try {
            w.toFront();
        } finally {
            w.setAutoRequestFocus(auto);
        }
    }

    static boolean isFrameOrDialog(Window w) {
        return w instanceof Frame || w instanceof Dialog;
    }

    /**
     * True when a different modal dialog is showing: do not shuffle z-order
     * under it.
     */
    static boolean modalBlocksRaise(Window focused) {
        for (Window w : Window.getWindows()) {
            if (w instanceof Dialog) {
                Dialog d = (Dialog) w;
                if (d.isModal() && d.isVisible() && d != focused) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean shouldRaiseOther(Window focused, Window other) {
        if (other == null || other == focused) {
            return false;
        }
        boolean otherFrame = other instanceof Frame;
        boolean iconified = otherFrame
                && ((((Frame) other).getExtendedState() & Frame.ICONIFIED) != 0);
        return shouldRaiseOther(
                isFrameOrDialog(focused),
                otherFrame,
                other instanceof Dialog,
                other.isVisible(),
                other.isDisplayable(),
                other.isShowing(),
                other.isAlwaysOnTop(),
                iconified);
    }

    /**
     * Headless policy: raise other frames/dialogs that are showing, not
     * always-on-top, and not iconified. Ignore popups ({@code JWindow}).
     */
    static boolean shouldRaiseOther(boolean focusedIsFrameOrDialog,
            boolean otherIsFrame, boolean otherIsDialog,
            boolean otherVisible, boolean otherDisplayable, boolean otherShowing,
            boolean otherAlwaysOnTop, boolean otherIconified) {
        if (!focusedIsFrameOrDialog) {
            return false;
        }
        if (!otherIsFrame && !otherIsDialog) {
            return false;
        }
        if (!otherVisible || !otherDisplayable || !otherShowing) {
            return false;
        }
        if (otherAlwaysOnTop || otherIconified) {
            return false;
        }
        return true;
    }
}
