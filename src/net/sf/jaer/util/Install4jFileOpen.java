package net.sf.jaer.util;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import net.sf.jaer.JAERViewer;
import net.sf.jaer.graphics.AEViewer;

/**
 * install4j single-instance / macOS file-open: a second launch delivers the
 * path here instead of a new {@code main}. Direct
 * {@code StartupNotification} import matches {@link net.sf.jaer.JaerUpdaterInstall4j}.
 */
public final class Install4jFileOpen {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final Object lock = new Object();
    private static volatile JAERViewer viewer;
    private static File pending;

    private Install4jFileOpen() {
    }

    /**
     * Call on the main thread before any AWT window (macOS can drop the first
     * open otherwise).
     */
    public static void register() {
        try {
            com.install4j.api.launcher.StartupNotification.registerStartupListener(parameters -> {
                log.info("install4j startup notification: [" + parameters + "]");
                onStartupParameters(parameters);
            });
            log.info("install4j StartupNotification listener registered");
        } catch (NoClassDefFoundError e) {
            log.fine("No install4j runtime; file-open listener not registered");
        } catch (Throwable t) {
            log.log(Level.WARNING, "Could not register install4j file-open listener", t);
        }
    }

    public static void attach(JAERViewer jv) {
        File queued;
        synchronized (lock) {
            viewer = jv;
            queued = pending;
            pending = null;
        }
        if (queued != null) {
            openLater(jv, queued);
        }
    }

    static void onStartupParameters(String parameters) {
        File f = firstExistingFile(parameters);
        if (f == null) {
            return;
        }
        JAERViewer jv;
        synchronized (lock) {
            jv = viewer;
            if (jv == null) {
                pending = f;
                log.info("Queued install4j file-open until JAERViewer is ready: " + f);
                return;
            }
        }
        openLater(jv, f);
    }

    private static void openLater(JAERViewer jv, File f) {
        Thread t = new Thread(() -> openOnViewer(jv, f), "jaer-install4j-open");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Same wait + {@code SyncPlayer.startPlayback} path as argv[0] file open.
     */
    public static void openOnViewer(JAERViewer jv, File f) {
        if (jv == null || f == null) {
            return;
        }
        log.info("Opening launch file " + f.getAbsolutePath());
        try {
            final AEViewer ready = JAERViewer.waitForReadyViewer(jv, 60_000L);
            if (ready == null || jv.getSyncPlayer() == null) {
                throw new IllegalStateException(
                        "Timed out waiting for AEViewer chip to initialize before opening "
                                + f.getAbsolutePath());
            }
            Runnable play = () -> {
                try {
                    jv.getSyncPlayer().startPlayback(f);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
            if (SwingUtilities.isEventDispatchThread()) {
                play.run();
            } else {
                SwingUtilities.invokeAndWait(play);
            }
        } catch (Exception e) {
            Throwable shown = e;
            while ((shown instanceof InvocationTargetException || shown instanceof RuntimeException)
                    && shown.getCause() != null && shown.getCause() != shown) {
                shown = shown.getCause();
            }
            log.log(Level.SEVERE, "Failed to open launch file " + f, shown);
            final Throwable msg = shown;
            Runnable dlg = () -> JOptionPane.showMessageDialog(null,
                    "<html>Trying to start JAERViewer with <br>file=\"" + f + "\"<br>Caught " + msg);
            if (SwingUtilities.isEventDispatchThread()) {
                dlg.run();
            } else {
                SwingUtilities.invokeLater(dlg);
            }
        }
    }

    /**
     * install4j may pass one unquoted path with spaces, or several quoted paths.
     */
    static File firstExistingFile(String parameters) {
        if (parameters == null || parameters.isBlank()) {
            log.warning("install4j startup notification had empty parameters");
            return null;
        }
        String stripped = stripMatchingQuotes(parameters.trim());
        File whole = new File(stripped);
        if (usableFile(whole)) {
            return whole;
        }
        log.warning("install4j startup parameters were not an existing file: " + parameters);
        return null;
    }

    private static String stripMatchingQuotes(String s) {
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static boolean usableFile(File f) {
        FileAccessTimeout.Kind k = FileAccessTimeout.kind(f);
        if (k == FileAccessTimeout.Kind.FILE) {
            return true;
        }
        // Dropbox/offline probe timeout: still try if the name looks like an AE file.
        if (k == FileAccessTimeout.Kind.TIMEOUT) {
            String n = f.getName().toLowerCase();
            return n.endsWith(".aedat") || n.endsWith(".aedat2") || n.endsWith(".aedat4")
                    || n.endsWith(".aedatz") || n.endsWith(".aedz") || n.endsWith(".dat");
        }
        return false;
    }
}
