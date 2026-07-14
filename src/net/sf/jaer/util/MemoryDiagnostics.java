package net.sf.jaer.util;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Heap and JVM memory snapshots for debugging long-run live capture OOMs.
 * <p>
 * Enable periodic logging with {@code -Djaer.memory.trace.intervalMs=60000} (see
 * {@link #maybeStartPeriodicLogging(Logger)}).
 * <p>
 * For native/GPU exhaustion (GL error 1285, {@code hs_err} with
 * {@code Chunk::new}), also use {@code -XX:NativeMemoryTracking=summary} and
 * {@code jcmd <pid> VM.native_memory summary}.
 */
public final class MemoryDiagnostics {

    private static final long MIN_LOG_INTERVAL_MS = 30_000L;
    private static volatile long lastSummaryLogMs;
    private static volatile Thread periodicThread;

    private MemoryDiagnostics() {
    }

    public static String heapSummary() {
        final Runtime rt = Runtime.getRuntime();
        final long used = rt.totalMemory() - rt.freeMemory();
        final long max = rt.maxMemory();
        final double pct = max > 0 ? (100.0 * used / max) : 0;
        final StringBuilder sb = new StringBuilder(256);
        sb.append(String.format("heap used=%s max=%s total=%s free=%s (%.1f%% of max)",
                formatBytes(used), formatBytes(max), formatBytes(rt.totalMemory()),
                formatBytes(rt.freeMemory()), pct));
        try {
            final MemoryMXBean bean = ManagementFactory.getMemoryMXBean();
            final MemoryUsage nonHeap = bean.getNonHeapMemoryUsage();
            if (nonHeap != null) {
                sb.append(String.format(" nonHeap used=%s committed=%s",
                        formatBytes(nonHeap.getUsed()), formatBytes(nonHeap.getCommitted())));
            }
        } catch (Exception ignored) {
        }
        sb.append(" threads=").append(Thread.activeCount());
        return sb.toString();
    }

    public static String oomHints() {
        return "Hints: -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=... "
                + "| -XX:NativeMemoryTracking=summary then jcmd <pid> VM.native_memory summary "
                + "| GL 1285=out of GPU memory (reduce chip display size, disable sliding window/accumulate) "
                + "| see hs_err_pid*.log and replay_pid*.log in working directory";
    }

    public static void logSummary(Logger log, Level level, String context) {
        if (log == null) {
            return;
        }
        log.log(level, context + " " + heapSummary());
    }

    /** Rate-limited heap log (default 30s) to avoid spam during GL OOM storms. */
    public static void logSummaryRateLimited(Logger log, String context) {
        final long now = System.currentTimeMillis();
        if (now - lastSummaryLogMs < MIN_LOG_INTERVAL_MS) {
            return;
        }
        lastSummaryLogMs = now;
        logSummary(log, Level.WARNING, context);
    }

    public static void logOomContext(Logger log, String context, Throwable t) {
        logSummary(log, Level.SEVERE, context);
        if (log != null) {
            log.log(Level.SEVERE, oomHints(), t);
        }
    }

    /**
     * Starts a background thread that logs heap usage every
     * {@code jaer.memory.trace.intervalMs} (disabled when property unset or &lt;= 0).
     */
    public static synchronized void maybeStartPeriodicLogging(Logger log) {
        final long intervalMs = parsePositiveLong(System.getProperty("jaer.memory.trace.intervalMs"), 0);
        if (intervalMs <= 0 || periodicThread != null) {
            return;
        }
        periodicThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(intervalMs);
                    logSummary(log, Level.INFO, "[memory trace]");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "jAER-memory-trace");
        periodicThread.setDaemon(true);
        periodicThread.start();
        log.info("Memory trace enabled: intervalMs=" + intervalMs + " (" + heapSummary() + ")");
    }

    private static long parsePositiveLong(String raw, long defaultValue) {
        if (raw == null || raw.isEmpty()) {
            return defaultValue;
        }
        try {
            final long v = Long.parseLong(raw.trim());
            return v > 0 ? v : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0) {
            return "n/a";
        }
        if (bytes < 1024) {
            return bytes + "B";
        }
        if (bytes < 1024L * 1024) {
            return String.format("%.1fKB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1fMB", bytes / (1024.0 * 1024));
        }
        return String.format("%.2fGB", bytes / (1024.0 * 1024 * 1024));
    }
}
