package net.sf.jaer.util;

import java.awt.AWTEvent;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.RenderingHints;
import java.awt.SplashScreen;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import net.sf.jaer.JaerConstants;

/**
 * Abort jAER while the splash/startup path is still running. Java's
 * {@link SplashScreen} is native and does not receive key events; showing any
 * AWT window also closes it. The AWT event pump stops if the EDT blocks in a
 * native filesystem call. This installs:
 * <ul>
 * <li>AWT ESC listener (works while the EDT is pumping, including
 * {@link java.awt.SecondaryLoop} during folder timeouts)</li>
 * <li>On Windows, {@code GetAsyncKeyState} (JNA) so ESC still works when the
 * EDT is stuck in a native call</li>
 * <li>install4j native splash: {@code SplashScreen.writeMessage} (one status
 * line, works off the EDT). No Swing window while that splash is up.</li>
 * <li>When {@code java.awt.SplashScreen} is absent and this is not an
 * install4j launch, an undecorated PNG window with log overlay</li>
 * <li>A small always-on-top Abort window only if no splash image is available</li>
 * </ul>
 * Disable with {@code -Djaer.splashAbort=false}.
 */
public final class SplashStartupAbort {

    public static final String HINT = "Press ESC to abort startup";
    /** Enable {@link JAERViewer} Java-splash log overlay (scrolling lines). Default false. */
    public static final String LOG_OVERLAY_PROP = "jaer.splashLogOverlay";
    /** Min ms between install4j {@code writeMessage} calls. 0 = every log record. Default 75. */
    public static final String WRITE_INTERVAL_PROP = "jaer.splashWriteMinIntervalMs";
    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final AtomicBoolean armed = new AtomicBoolean(false);
    private static final AtomicBoolean installed = new AtomicBoolean(false);
    private static final AtomicBoolean install4jSplash = new AtomicBoolean(false);
    private static volatile java.lang.reflect.Method install4jWriteMessage;
    private static volatile long splashArmedAtNs;
    private static volatile long lastWriteNs;
    private static volatile String pendingWrite;
    private static int splashRecords;
    private static int splashWrites;
    private static int splashCoalesced;
    private static volatile JFrame abortFrame;
    private static volatile JFrame imageSplashFrame;
    private static volatile Handler imageSplashLogHandler;
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
     * Java {@link SplashScreen} scrolling log overlay (not install4j
     * {@code writeMessage}). Off by default; {@code -Djaer.splashLogOverlay=true} enables it.
     */
    public static boolean isLogOverlayEnabled() {
        return Boolean.parseBoolean(System.getProperty(LOG_OVERLAY_PROP, "false"));
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
        splashArmedAtNs = System.nanoTime();
        splashRecords = splashWrites = splashCoalesced = 0;
        attachInstall4jLogHandler();
        writeInstall4jMessage(HINT);
        log.info(HINT);
        Toolkit.getDefaultToolkit().addAWTEventListener(awtEsc, AWTEvent.KEY_EVENT_MASK);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher);
        startWindowsEscPoller();
        if (install4jSplash.get()) {
            log.info("install4j native splash: log lines go to status text (writeMessage)");
            return;
        }
        Runnable show = SplashStartupAbort::showStartupUi;
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
        closeFrame(abortFrame);
        abortFrame = null;
        Handler h = imageSplashLogHandler;
        imageSplashLogHandler = null;
        if (h != null) {
            try {
                log.removeHandler(h);
                h.close();
            } catch (Exception e) {
                log.log(Level.FINE, "Could not remove splash log handler", e);
            }
        }
        closeFrame(imageSplashFrame);
        imageSplashFrame = null;
        Thread t = poller;
        poller = null;
        if (t != null) {
            t.interrupt();
        }
        flushInstall4jMessage();
        long ms = (System.nanoTime() - splashArmedAtNs) / 1_000_000L;
        log.info(String.format(
                "Startup splash writeMessage: %d sent, %d coalesced, %d log records in %d ms (interval -D%s, overlay -D%s=%s)",
                splashWrites, splashCoalesced, splashRecords, ms,
                WRITE_INTERVAL_PROP, LOG_OVERLAY_PROP, isLogOverlayEnabled()));
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

    /**
     * Do not show any AWT window while Java's {@link SplashScreen} is visible:
     * the first window closes it and drops the log overlay. install4j native
     * launchers ignore {@code -splash:}, so then show the PNG in a JFrame.
     */
    private static void showStartupUi() {
        if (!armed.get() || abortFrame != null || imageSplashFrame != null) {
            return;
        }
        if (javaSplashVisible() || install4jSplash.get()) {
            log.fine("Native/Java splash visible; skip Swing splash (would hide it)");
            return;
        }
        if (showImageSplashFrame()) {
            return;
        }
        showAbortFrame();
    }

    /**
     * install4j {@code writeMessage} via reflection so git/ant run compiles
     * without i4jruntime.jar. No-op when not launched by an install4j exe.
     */
    private static void attachInstall4jLogHandler() {
        try {
            Class<?> c = Class.forName("com.install4j.api.launcher.SplashScreen");
            install4jWriteMessage = c.getMethod("writeMessage", String.class);
        } catch (Throwable e) {
            install4jWriteMessage = null;
            return;
        }
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (!armed.get() || record == null) {
                    return;
                }
                String s = record.getMessage();
                if (s == null || s.isEmpty()) {
                    return;
                }
                writeInstall4jMessage(s);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        log.addHandler(handler);
        imageSplashLogHandler = handler;
    }

    private static synchronized void writeInstall4jMessage(String raw) {
        java.lang.reflect.Method m = install4jWriteMessage;
        if (m == null || raw == null) {
            return;
        }
        String one = raw.replace('\n', ' ').replace('\r', ' ').trim();
        if (one.isEmpty()) {
            return;
        }
        if (one.length() > 90) {
            one = one.substring(0, 87) + "...";
        }
        splashRecords++;
        long intervalMs = 75L;
        try {
            intervalMs = Long.parseLong(System.getProperty(WRITE_INTERVAL_PROP, "75"));
        } catch (NumberFormatException ignored) {
        }
        long now = System.nanoTime();
        if (intervalMs > 0 && lastWriteNs != 0L
                && (now - lastWriteNs) < intervalMs * 1_000_000L) {
            pendingWrite = one;
            splashCoalesced++;
            return;
        }
        pendingWrite = null;
        invokeInstall4jWrite(m, one, now);
    }

    private static synchronized void flushInstall4jMessage() {
        String pending = pendingWrite;
        pendingWrite = null;
        java.lang.reflect.Method m = install4jWriteMessage;
        if (pending == null || m == null) {
            return;
        }
        invokeInstall4jWrite(m, pending, System.nanoTime());
    }

    private static void invokeInstall4jWrite(java.lang.reflect.Method m, String one, long nowNs) {
        try {
            m.invoke(null, one);
            install4jSplash.set(true);
            lastWriteNs = nowNs;
            splashWrites++;
        } catch (Throwable e) {
            install4jWriteMessage = null;
            install4jSplash.set(false);
        }
    }

    private static boolean javaSplashVisible() {
        try {
            SplashScreen splash = SplashScreen.getSplashScreen();
            return splash != null && splash.isVisible();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * @return true if the image splash window is showing
     */
    private static boolean showImageSplashFrame() {
        BufferedImage img = loadSplashImage();
        if (img == null) {
            return false;
        }
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int w = img.getWidth();
        int h = img.getHeight();
        if (w <= 0 || h <= 0) {
            return false;
        }
        double scale = Math.min(1.0, Math.min((screen.width * 0.9) / w, (screen.height * 0.9) / h));
        int dw = Math.max(1, (int) Math.round(w * scale));
        int dh = Math.max(1, (int) Math.round(h * scale));
        Image drawImg = scale == 1.0 ? img : img.getScaledInstance(dw, dh, Image.SCALE_SMOOTH);

        SplashPanel panel = new SplashPanel(drawImg, dw, dh);
        JFrame f = new JFrame("jAER");
        f.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        f.setUndecorated(true);
        f.setAlwaysOnTop(true);
        f.setResizable(false);
        f.getContentPane().add(panel);
        f.pack();
        f.setLocationRelativeTo(null);
        f.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    e.consume();
                    abort("ESC");
                }
            }
        });
        if (isLogOverlayEnabled()) {
            Handler handler = new Handler() {
                @Override
                public void publish(LogRecord record) {
                    if (record == null) {
                        return;
                    }
                    String s = record.getMessage();
                    if (s == null) {
                        return;
                    }
                    SwingUtilities.invokeLater(() -> panel.addLine(s));
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            };
            log.addHandler(handler);
            imageSplashLogHandler = handler;
        }
        f.setVisible(true);
        f.toFront();
        f.requestFocus();
        imageSplashFrame = f;
        log.info("Startup splash window (Java SplashScreen not available; typical for install4j)");
        return true;
    }

    private static BufferedImage loadSplashImage() {
        List<File> files = new ArrayList<>();
        files.add(new File("SplashScreen.png"));
        files.add(new File("images/800w/SplashScreen.png"));
        files.add(new File("images/1024w/SplashScreen.png"));
        files.add(new File("images/SplashScreen.png"));
        String exeDir = System.getProperty("install4j.exeDir");
        if (exeDir != null && !exeDir.isBlank()) {
            files.add(new File(exeDir, "SplashScreen.png"));
            files.add(new File(exeDir, "jaer/SplashScreen.png"));
            files.add(new File(exeDir, "jaer/images/800w/SplashScreen.png"));
            files.add(new File(exeDir, "jaer/images/1024w/SplashScreen.png"));
        }
        for (File f : files) {
            if (f.isFile()) {
                try {
                    BufferedImage img = ImageIO.read(f);
                    if (img != null) {
                        log.fine("Loaded splash image from " + f.getAbsolutePath());
                        return img;
                    }
                } catch (Exception e) {
                    log.log(Level.FINE, "Could not read splash " + f, e);
                }
            }
        }
        try (InputStream in = SplashStartupAbort.class.getResourceAsStream(JaerConstants.SPLASH_SCREEN_IMAGE)) {
            if (in != null) {
                return ImageIO.read(in);
            }
        } catch (Exception e) {
            log.log(Level.FINE, "Could not read splash from classpath", e);
        }
        return null;
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
        f.setLocationByPlatform(true);
        f.setVisible(true);
        f.toFront();
        abort.requestFocusInWindow();
        abortFrame = f;
    }

    private static void closeFrame(JFrame f) {
        if (f == null) {
            return;
        }
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

    private static final class SplashPanel extends JPanel {

        private static final int MAX_LINES = 18;
        private final Image image;
        private final int imgW;
        private final int imgH;
        private final List<String> lines = new ArrayList<>();

        SplashPanel(Image image, int imgW, int imgH) {
            this.image = image;
            this.imgW = imgW;
            this.imgH = imgH;
            setPreferredSize(new Dimension(imgW, imgH));
            setOpaque(true);
        }

        void addLine(String s) {
            if (s == null || s.isEmpty()) {
                return;
            }
            String one = s.replace('\n', ' ');
            if (one.length() > 140) {
                one = one.substring(0, 137) + "...";
            }
            lines.add(one);
            while (lines.size() > MAX_LINES) {
                lines.remove(0);
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.drawImage(image, 0, 0, imgW, imgH, this);
                g2.setComposite(AlphaComposite.SrcOver);
                g2.setColor(Color.blue);
                g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 13f));
                int x = 45;
                int y = 30;
                int ystep = 16;
                for (String line : lines) {
                    g2.drawString(line, x, y);
                    y += ystep;
                }
                int hintY = Math.max(20, imgH - 18);
                g2.drawString(HINT, x, hintY);
            } finally {
                g2.dispose();
            }
        }
    }
}
