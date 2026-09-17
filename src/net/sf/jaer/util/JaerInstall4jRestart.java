package net.sf.jaer.util;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.security.CodeSource;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Relaunch the install4j {@code jaer} executable after this JVM exits.
 * <p>
 * The launcher is {@code singleInstance="true"}, so starting it while this
 * process is still alive only notifies the current instance. A short-lived
 * helper JVM waits for our PID (and the running semaphore) to go away, then
 * starts the launcher. {@code ant run} / IDE have no launcher, so
 * {@link #isAvailable()} is false there.
 */
public final class JaerInstall4jRestart {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final long WAIT_PID_MS = 90_000L;
    private static final long WAIT_SEMAPHORE_MS = 15_000L;
    private static final long AFTER_EXIT_PAUSE_MS = 750L;

    private JaerInstall4jRestart() {
    }

    /** True when this JVM was started by the installed {@code jaer} launcher. */
    public static boolean isAvailable() {
        File launcher = launcherFile();
        return launcher != null && launcher.isFile();
    }

    /**
     * Start a helper that relaunches {@link #launcherFile()} after this PID
     * exits. {@code semaphore} is the running-instance marker to wait for (and
     * delete if leftover) so the next launch does not show an unclean-exit
     * dialog. Returns false when not an install4j launch or the helper could
     * not be started.
     */
    public static boolean scheduleAfterThisProcessExits(File semaphore) {
        File launcher = launcherFile();
        if (launcher == null || !launcher.isFile()) {
            log.info("No install4j launcher; cannot schedule restart");
            return false;
        }
        File java = helperJava();
        File cp = codeSourceFile();
        if (java == null || !java.isFile() || cp == null || !cp.exists()) {
            log.warning("Cannot schedule install4j restart (java=" + java + " classpath=" + cp + ")");
            return false;
        }
        ProcessBuilder pb = new ProcessBuilder(
                java.getAbsolutePath(),
                "-cp",
                cp.getAbsolutePath(),
                JaerInstall4jRestart.class.getName(),
                Long.toString(ProcessHandle.current().pid()),
                launcher.getAbsolutePath(),
                semaphore != null ? semaphore.getAbsolutePath() : "");
        pb.redirectErrorStream(true);
        File waiterLog = new File(JaerTmpdir.get(), "restart-waiter.log");
        pb.redirectOutput(waiterLog);
        try {
            pb.start();
            log.info("Scheduled install4j restart of " + launcher.getAbsolutePath()
                    + " after PID " + ProcessHandle.current().pid()
                    + " (waiter log " + waiterLog.getAbsolutePath() + ")");
            return true;
        } catch (IOException e) {
            log.log(Level.WARNING, "Could not start install4j restart waiter", e);
            return false;
        }
    }

    /**
     * Helper JVM entry: args are {@code pid}, {@code launcher}, optional
     * {@code semaphoreFile}.
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: pid launcher [semaphore]");
            return;
        }
        long pid;
        try {
            pid = Long.parseLong(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("bad pid: " + args[0]);
            return;
        }
        File launcher = new File(args[1]);
        File semaphore = args.length >= 3 && !args[2].isBlank() ? new File(args[2]) : null;
        System.out.println("waiting for PID " + pid + " then launching " + launcher.getAbsolutePath());
        if (!waitUntilDead(pid, WAIT_PID_MS)) {
            System.err.println("PID " + pid + " still alive after " + WAIT_PID_MS + " ms; not relaunching");
            return;
        }
        if (semaphore != null) {
            waitUntilGoneOrDelete(semaphore, WAIT_SEMAPHORE_MS);
        }
        sleepQuietly(AFTER_EXIT_PAUSE_MS);
        if (!launcher.isFile()) {
            System.err.println("launcher missing: " + launcher.getAbsolutePath());
            return;
        }
        try {
            ProcessBuilder launch = launchCommand(launcher);
            launch.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            launch.redirectError(ProcessBuilder.Redirect.DISCARD);
            launch.start();
            System.out.println("launched " + launcher.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("launch failed: " + e);
        }
    }

    static File launcherFile() {
        String module = System.getProperty("exe4j.moduleName");
        if (module != null && !module.isBlank()) {
            File f = new File(module);
            if (f.isFile()) {
                return f;
            }
        }
        String exeDir = System.getProperty("install4j.exeDir");
        if (exeDir == null || exeDir.isBlank()) {
            return null;
        }
        File dir = new File(exeDir);
        boolean win = os().contains("win");
        File cand = new File(dir, win ? "jaer.exe" : "jaer");
        return cand.isFile() ? cand : null;
    }

    private static File helperJava() {
        File bin = new File(System.getProperty("java.home", "."), "bin");
        boolean win = os().contains("win");
        File javaw = new File(bin, win ? "javaw.exe" : "java");
        if (javaw.isFile()) {
            return javaw;
        }
        File java = new File(bin, win ? "java.exe" : "java");
        return java.isFile() ? java : null;
    }

    private static File codeSourceFile() {
        try {
            CodeSource cs = JaerInstall4jRestart.class.getProtectionDomain().getCodeSource();
            if (cs == null || cs.getLocation() == null) {
                return null;
            }
            URI uri = cs.getLocation().toURI();
            return new File(uri);
        } catch (Exception e) {
            log.log(Level.FINE, "Could not resolve JaerInstall4jRestart CodeSource", e);
            return null;
        }
    }

    private static ProcessBuilder launchCommand(File launcher) {
        File app = macAppBundle(launcher);
        if (app != null) {
            return new ProcessBuilder("/usr/bin/open", app.getAbsolutePath());
        }
        ProcessBuilder pb = new ProcessBuilder(launcher.getAbsolutePath());
        File dir = launcher.getParentFile();
        if (dir != null) {
            pb.directory(dir);
        }
        return pb;
    }

    private static File macAppBundle(File launcher) {
        File macos = launcher.getParentFile();
        if (macos == null || !"MacOS".equals(macos.getName())) {
            return null;
        }
        File contents = macos.getParentFile();
        if (contents == null || !"Contents".equals(contents.getName())) {
            return null;
        }
        File app = contents.getParentFile();
        if (app != null && app.getName().toLowerCase(Locale.ROOT).endsWith(".app")) {
            return app;
        }
        return null;
    }

    private static boolean waitUntilDead(long pid, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
            if (System.currentTimeMillis() > deadline) {
                return false;
            }
            sleepQuietly(200L);
        }
        return true;
    }

    private static void waitUntilGoneOrDelete(File semaphore, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (semaphore.isFile() && System.currentTimeMillis() < deadline) {
            sleepQuietly(100L);
        }
        if (semaphore.isFile() && !semaphore.delete()) {
            System.err.println("could not delete leftover semaphore " + semaphore.getAbsolutePath());
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String os() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    }
}
