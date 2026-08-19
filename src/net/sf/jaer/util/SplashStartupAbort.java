package net.sf.jaer.util;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.SplashScreen;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import net.sf.jaer.JaerConstants;

/**
 * Abort jAER while the splash/startup path is still running. Java's
 * {@link SplashScreen} is native and does not receive key events; the AWT
 * event pump also stops if the EDT blocks in a native filesystem call. This
 * installs:
 * <ul>
 * <li>AWT ESC listener (works while the EDT is pumping, including
 * {@link java.awt.SecondaryLoop} during folder timeouts)</li>
 * <li>On Windows, {@code GetAsyncKeyState} (JNA) so ESC still works when the
 * EDT is stuck in a native call</li>
 * <li>A small always-on-top Abort window as a fallback</li>
 * </ul>
 * Disable with {@code -Djaer.splashAbort=false}.
 */
public final class SplashStartupAbort {

    public static final String HINT = "Press ESC to abort startup";
    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final AtomicBoolean armed = new AtomicBoolean(false);
    private static final AtomicBoolean installed = new AtomicBoolean(false);
    private static volatile JFrame abortFrame;
    private static volatile Thread poller;
    private static final AWTEventListener awtEsc = e -> {
        if (!armed.get() || !(e instanceof KeyEvent ke)) {
            return;
        }
        if (ke.getID() == KeyEvent.KEY_PRESSED && ke.getKeyCode() == KeyEvent.VK_ESCAPE) {
            ke.consume();
            abort("ESC");
        }
    };
    private static final KeyEventDispatcher dispatcher = e -> {
        if (!armed.get()) {
            return false;
        }
        if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            e.consume();
            abort("ESC");
            return true;
        }
        return false;
    };

    private SplashStartupAbort() {
    }

    public static boolean isArmed() {
        return armed.get();
    }

    /**
     * Call from {@code main} as soon as the splash exists. Safe to call more
     * than once.
     */
    public static void install() {
        if (!Boolean.parseBoolean(System.getProperty("jaer.splashAbort", "true"))) {
            log.info("Splash ESC abort disabled (-Djaer.splashAbort=false)");
            return;
        }
        if (!installed.compareAndSet(false, true)) {
            armed.set(true);
            return;
        }
        armed.set(true);
        log.info(HINT);
        Toolkit.getDefaultToolkit().addAWTEventListener(awtEsc, AWTEvent.KEY_EVENT_MASK);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher);
        startWindowsEscPoller();
        Runnable show = SplashStartupAbort::showAbortFrame;
        if (SwingUtilities.isEventDispatchThread()) {
            show.run();
        } else {
            SwingUtilities.invokeLater(show);
        }
    }

    /**
     * Stop treating ESC as process abort (call when the first AEViewer is
     * shown so playback jog ESC still works).
     */
    public static void disarm() {
        if (!armed.compareAndSet(true, false)) {
            return;
        }
        JFrame f = abortFrame;
        abortFrame = null;
        if (f != null) {
            Runnable close = () -> {
                f.setVisible(false);
                f.dispose();
            };
            if (SwingUtilities.isEventDispatchThread()) {
                close.run();
            } else {
                SwingUtilities.invokeLater(close);
            }
        }
        Thread t = poller;
        poller = null;
        if (t != null) {
            t.interrupt();
        }
        log.fine("Splash ESC abort disarmed");
    }

    private static void abort(String how) {
        if (!armed.compareAndSet(true, false)) {
            return;
        }
        String msg = "Startup aborted (" + how + ")";
        log.warning(msg);
        System.out.println(msg);
        System.out.flush();
        JaerConstants.skipPreferenceWriteOnExit = true;
        Thread halt = new Thread(() -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
                return;
            }
            Runtime.getRuntime().halt(0);
        }, "jaer-startup-abort-halt");
        halt.setDaemon(true);
        halt.start();
        System.exit(0);
    }

    private static void showAbortFrame() {
        if (!armed.get() || abortFrame != null) {
            return;
        }
        JFrame f = new JFrame("jAER");
        f.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        f.setAlwaysOnTop(true);
        f.setResizable(false);
        f.setType(Window.Type.UTILITY);
        JLabel label = new JLabel("  " + HINT + "  ");
        label.setForeground(Color.blue);
        JButton abort = new JButton("Abort");
        abort.addActionListener(e -> abort("Abort button"));
        f.getRootPane().setDefaultButton(abort);
        f.getContentPane().setLayout(new BorderLayout(6, 6));
        f.getContentPane().add(label, BorderLayout.CENTER);
        f.getContentPane().add(abort, BorderLayout.EAST);
        f.pack();
        SplashScreen splash = SplashScreen.getSplashScreen();
        if (splash != null && splash.isVisible()) {
            try {
                java.awt.Rectangle b = splash.getBounds();
                f.setLocation(b.x + 12, b.y + Math.max(0, b.height - f.getHeight() - 12));
            } catch (IllegalStateException e) {
                f.setLocationByPlatform(true);
            }
        } else {
            f.setLocationByPlatform(true);
        }
        f.setVisible(true);
        f.toFront();
        abort.requestFocusInWindow();
        abortFrame = f;
    }

    /**
     * Poll VK_ESCAPE without the AWT pump (Java splash does not get key
     * events, and a blocked EDT does not pump them).
     */
    private static void startWindowsEscPoller() {
        if (!isWindows()) {
            return;
        }
        final com.sun.jna.platform.win32.User32 user32;
        try {
            user32 = com.sun.jna.platform.win32.User32.INSTANCE;
            if (user32 == null) {
                return;
            }
            user32.GetAsyncKeyState(KeyEvent.VK_ESCAPE);
        } catch (Throwable e) {
            log.log(Level.FINE, "Windows GetAsyncKeyState not available; splash ESC abort uses AWT only", e);
            return;
        }
        Thread t = new Thread(() -> {
            boolean wasDown = false;
            while (armed.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    boolean down = (user32.GetAsyncKeyState(KeyEvent.VK_ESCAPE) & 0x8000) != 0;
                    if (down && !wasDown) {
                        abort("ESC");
                        return;
                    }
                    wasDown = down;
                    Thread.sleep(40);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Throwable e) {
                    log.log(Level.FINE, "GetAsyncKeyState poll failed", e);
                    return;
                }
            }
        }, "jaer-splash-esc-poller");
        t.setDaemon(true);
        poller = t;
        t.start();
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase().contains("win");
    }
}
