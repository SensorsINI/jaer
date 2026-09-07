package net.sf.jaer.util;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * <p>
 * On Linux the install4j launcher often still starts a second JVM (file
 * association {@code %U} / no native single-instance socket). That process
 * writes the path under {@code ${java.io.tmpdir}/jaer/open-requests/} and
 * exits; the first instance watches that folder.
 */
public final class Install4jFileOpen {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final Object lock = new Object();
    private static volatile JAERViewer viewer;
    private static File pending;
    private static final AtomicBoolean watcherStarted = new AtomicBoolean(false);

    /** Subfolder of {@link JaerTmpdir} for second-launch file-open requests. */
    public static final String OPEN_REQUESTS_DIR_NAME = "open-requests";

    private Install4jFileOpen() {
    }

    /**
     * Call on the main thread before any AWT window (macOS can drop the first
     * open otherwise). Also starts the tmpdir request watcher so Linux / ant-run
     * can receive a double-clicked recording.
     */
    public static void register() {
        startRequestWatcher();
        try {
            com.install4j.api.launcher.StartupNotification.registerStartupListener(parameters -> {
                log.info("install4j startup notification: [" + parameters + "]");
                onStartupParameters(parameters);
            });
            log.info("install4j StartupNotification listener registered");
        } catch (NoClassDefFoundError e) {
            log.info("No install4j runtime; file-open uses tmpdir handoff only");
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
        drainOpenRequests();
    }

    /**
     * If a live jAER already advertised file-open handoff, write the recording
     * path for it and return true so this JVM can exit (Windows-like).
     */
    public static boolean tryHandoffToLiveInstance(File launchFile) {
        if (launchFile == null || !usableFile(launchFile)) {
            return false;
        }
        File semaphore = JAERViewer.getRunningSemaphoreFile();
        if (semaphore == null || !semaphore.isFile()) {
            File legacy = new File(JaerTmpdir.systemTmp(), JAERViewer.RUNNING_SEMAPHORE_FILENAME);
            semaphore = legacy.isFile() ? legacy : null;
        }
        if (semaphore == null) {
            return false;
        }
        String detail;
        try {
            detail = Files.readString(semaphore.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return false;
        }
        if (!JaerIssueReporter.supportsFileOpenHandoff(detail)) {
            return false;
        }
        Long pid = JaerIssueReporter.parseSemaphorePid(detail);
        if (pid == null || !JaerIssueReporter.isPidAlive(pid)) {
            return false;
        }
        if (!JaerIssueReporter.looksLikeJaerProcess(pid, semaphore.lastModified())) {
            return false;
        }
        if (!requestOpenInRunningInstance(launchFile)) {
            return false;
        }
        log.info("Live jAER PID " + pid + " accepts file-open handoff of " + launchFile.getAbsolutePath());
        return true;
    }

    /**
     * Write {@code recording} for the running instance's watcher. Returns false
     * if the request could not be created.
     */
    public static boolean requestOpenInRunningInstance(File recording) {
        if (recording == null || !usableFile(recording)) {
            return false;
        }
        File dir = openRequestsDir();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            log.warning("Could not create file-open handoff directory " + dir);
            return false;
        }
        try {
            File tmp = File.createTempFile("open-", ".tmp", dir);
            Files.writeString(tmp.toPath(), recording.getAbsolutePath() + System.lineSeparator(),
                    StandardCharsets.UTF_8);
            File dest = new File(dir, "open-" + System.nanoTime() + ".txt");
            try {
                Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("Wrote file-open handoff " + dest.getAbsolutePath());
            return true;
        } catch (IOException e) {
            log.log(Level.WARNING, "Could not write file-open handoff for " + recording, e);
            return false;
        }
    }

    static void onStartupParameters(String parameters) {
        File f = firstExistingFile(parameters);
        if (f == null) {
            return;
        }
        queueOrOpen(f);
    }

    private static void queueOrOpen(File f) {
        JAERViewer jv;
        synchronized (lock) {
            jv = viewer;
            if (jv == null) {
                pending = f;
                log.info("Queued file-open until JAERViewer is ready: " + f);
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
     * Parse a launcher argv / install4j / {@code file://} string into an
     * existing recording file, or null.
     */
    public static File parseLaunchArgument(String parameters) {
        if (parameters == null || parameters.isBlank()) {
            return null;
        }
        String stripped = stripMatchingQuotes(parameters.trim());
        File whole = fileFromLaunchString(stripped);
        if (usableFile(whole)) {
            return whole;
        }
        return null;
    }

    /**
     * install4j may pass one unquoted path with spaces, or several quoted paths,
     * or a Linux {@code file://} URL from the desktop {@code %U} Exec key.
     */
    static File firstExistingFile(String parameters) {
        if (parameters == null || parameters.isBlank()) {
            log.warning("install4j startup notification had empty parameters");
            return null;
        }
        File f = parseLaunchArgument(parameters);
        if (f != null) {
            return f;
        }
        log.warning("install4j startup parameters were not an existing file: " + parameters);
        return null;
    }

    static File openRequestsDir() {
        return JaerTmpdir.file(OPEN_REQUESTS_DIR_NAME);
    }

    static void drainOpenRequests() {
        File dir = openRequestsDir();
        File[] files = dir.isDirectory() ? dir.listFiles() : null;
        if (files == null) {
            return;
        }
        for (File req : files) {
            consumeOpenRequestFile(req);
        }
    }

    static File consumeOpenRequestFile(File req) {
        if (req == null || !req.isFile()) {
            return null;
        }
        String name = req.getName();
        if (!name.startsWith("open-") || !name.endsWith(".txt")) {
            return null;
        }
        String body;
        try {
            body = Files.readString(req.toPath(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return null;
        }
        try {
            Files.deleteIfExists(req.toPath());
        } catch (IOException e) {
            log.fine("Could not delete file-open request " + req + ": " + e.getMessage());
        }
        File recording = parseLaunchArgument(body);
        if (recording == null) {
            log.warning("Ignoring file-open handoff that is not an existing file: " + body);
            return null;
        }
        queueOrOpen(recording);
        return recording;
    }

    private static void startRequestWatcher() {
        if (!watcherStarted.compareAndSet(false, true)) {
            return;
        }
        File dir = openRequestsDir();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            log.warning("Could not create " + dir.getAbsolutePath() + "; Linux file-open handoff disabled");
            watcherStarted.set(false);
            return;
        }
        drainOpenRequests();
        Thread t = new Thread(Install4jFileOpen::watchOpenRequests, "jaer-file-open-watch");
        t.setDaemon(true);
        t.start();
        log.info("Watching " + dir.getAbsolutePath() + " for file-open handoff");
    }

    private static void watchOpenRequests() {
        Path dir = openRequestsDir().toPath();
        try (WatchService ws = FileSystems.getDefault().newWatchService()) {
            dir.register(ws, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key = ws.poll(1, TimeUnit.SECONDS);
                if (key == null) {
                    drainOpenRequests();
                    continue;
                }
                for (WatchEvent<?> ev : key.pollEvents()) {
                    Object ctx = ev.context();
                    if (ctx instanceof Path) {
                        consumeOpenRequestFile(dir.resolve((Path) ctx).toFile());
                    }
                }
                if (!key.reset()) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            log.log(Level.WARNING, "file-open handoff watcher failed; polling", e);
            while (!Thread.currentThread().isInterrupted()) {
                drainOpenRequests();
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static File fileFromLaunchString(String s) {
        if (s.regionMatches(true, 0, "file:", 0, 5)) {
            try {
                URI uri = new URI(s);
                if ("file".equalsIgnoreCase(uri.getScheme())) {
                    return new File(uri);
                }
            } catch (Exception e) {
                return new File(decodeLooseFileUrl(s));
            }
        }
        return new File(s);
    }

    private static String decodeLooseFileUrl(String s) {
        String path = s;
        if (path.regionMatches(true, 0, "file://localhost", 0, 16)) {
            path = path.substring(16);
        } else if (path.regionMatches(true, 0, "file://", 0, 7)) {
            path = path.substring(7);
        } else if (path.regionMatches(true, 0, "file:", 0, 5)) {
            path = path.substring(5);
        }
        try {
            return URLDecoder.decode(path, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return path;
        }
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
