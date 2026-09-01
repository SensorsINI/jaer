package net.sf.jaer.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Wall-clock marks from JVM process start to first {@code AEViewer} visibility.
 * Off unless {@code -Djaer.startup.profile=true}. Optional
 * {@code -Djaer.startup.profile.exitAfterVisibleMs=N} exits after the window
 * is shown so a Flight Recorder dump can finish without USB autobind.
 */
public final class StartupProfiler {

    public static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("jaer.startup.profile", "false"));

    private static final long T0_NS = System.nanoTime();
    private static final long T0_MILLIS = System.currentTimeMillis();
    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final List<Mark> MARKS = Collections.synchronizedList(new ArrayList<>());

    private StartupProfiler() {
    }

    public static long elapsedMs() {
        return (System.nanoTime() - T0_NS) / 1_000_000L;
    }

    public static void mark(String name) {
        if (!ENABLED) {
            return;
        }
        long ms = elapsedMs();
        String thread = Thread.currentThread().getName();
        MARKS.add(new Mark(name, ms, thread));
        String line = String.format("STARTUP +%5d ms  [%s]  %s", ms, thread, name);
        System.out.println(line);
        log.info(line);
    }

    public static void scheduleExitAfterVisible() {
        if (!ENABLED) {
            return;
        }
        int ms = 0;
        try {
            ms = Integer.parseInt(System.getProperty("jaer.startup.profile.exitAfterVisibleMs", "0"));
        } catch (NumberFormatException e) {
            return;
        }
        if (ms <= 0) {
            return;
        }
        final int waitMs = ms;
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            dump();
            System.exit(0);
        }, "jaer-startup-profile-exit");
        t.setDaemon(true);
        t.start();
    }

    public static void dump() {
        if (!ENABLED) {
            return;
        }
        File out = new File(JaerTmpdir.get(), "startup-profile.txt");
        try (PrintWriter w = new PrintWriter(new FileWriter(out, false))) {
            w.println("# jAER JVM startup profile (ms from first StartupProfiler class init)");
            w.println("# t0.millis=" + T0_MILLIS);
            w.println("# name\tms\tthread");
            synchronized (MARKS) {
                for (Mark m : MARKS) {
                    w.println(m.name + "\t" + m.ms + "\t" + m.thread);
                }
            }
            w.flush();
        } catch (IOException e) {
            log.warning("Could not write " + out + ": " + e);
        }
        System.out.println("STARTUP profile written to " + out.getAbsolutePath());
    }

    public static List<Mark> snapshot() {
        synchronized (MARKS) {
            return new ArrayList<>(MARKS);
        }
    }

    public static final class Mark {
        public final String name;
        public final long ms;
        public final String thread;

        Mark(String name, long ms, String thread) {
            this.name = name;
            this.ms = ms;
            this.thread = thread;
        }
    }
}
