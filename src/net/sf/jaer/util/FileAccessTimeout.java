package net.sf.jaer.util;

import java.awt.EventQueue;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;

/**
 * Filesystem probes ({@code exists}/{@code isDirectory}/{@code isFile}) that
 * cannot stall the caller when a path is on a wedged volume (Dropbox conflict,
 * offline-only placeholder, NFS). Native {@link File} checks are not
 * interruptible; this class waits up to a timeout and then skips, leaving the
 * stuck probe on a daemon thread.
 * <p>
 * Timeout is {@code -Djaer.fileAccessTimeoutMs} (default 2000). When called on
 * the EDT, a {@link SecondaryLoop} keeps AWT pumping so splash ESC abort still
 * works during the wait.
 */
public final class FileAccessTimeout {

    public enum Kind {
        FILE, DIRECTORY, OTHER, MISSING, TIMEOUT
    }

    public static final long DEFAULT_TIMEOUT_MS = 2000L;
    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final AtomicInteger threadSeq = new AtomicInteger();
    private static final ExecutorService EXEC = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "jaer-file-access-" + threadSeq.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    private FileAccessTimeout() {
    }

    /**
     * Probe timeout in milliseconds ({@code -Djaer.fileAccessTimeoutMs}, default
     * {@value #DEFAULT_TIMEOUT_MS}).
     */
    public static long timeoutMs() {
        return Math.max(50L, Long.getLong("jaer.fileAccessTimeoutMs", DEFAULT_TIMEOUT_MS));
    }

    public static Kind kind(File file) {
        return kind(file, timeoutMs());
    }

    public static Kind kind(File file, long timeoutMs) {
        if (file == null) {
            return Kind.MISSING;
        }
        Kind k = callWithTimeout(() -> probe(file), timeoutMs, Kind.TIMEOUT);
        if (k == Kind.TIMEOUT) {
            log.warning("Filesystem probe timed out after " + timeoutMs + " ms for " + file
                    + " (Dropbox/NFS/offline file?); skipping");
        }
        return k;
    }

    /**
     * {@code file} if it is a reachable directory within the timeout, otherwise
     * {@code null}.
     */
    public static File directoryOrNull(File file) {
        return kind(file) == Kind.DIRECTORY ? file : null;
    }

    public static boolean isDirectory(File file) {
        return kind(file) == Kind.DIRECTORY;
    }

    public static boolean isFile(File file) {
        return kind(file) == Kind.FILE;
    }

    /**
     * Classify many paths with one wall-clock timeout (probes run in parallel).
     * Timed-out entries are {@link Kind#TIMEOUT}.
     */
    public static Map<File, Kind> classify(Collection<File> files) {
        return classify(files, timeoutMs());
    }

    public static Map<File, Kind> classify(Collection<File> files, long timeoutMs) {
        LinkedHashMap<File, Kind> out = new LinkedHashMap<>();
        if (files == null || files.isEmpty()) {
            return out;
        }
        LinkedHashMap<File, Future<Kind>> futures = new LinkedHashMap<>();
        for (File f : files) {
            if (f == null) {
                continue;
            }
            futures.put(f, EXEC.submit(() -> probe(f)));
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(50L, timeoutMs));
        if (SwingUtilities.isEventDispatchThread()) {
            drainWithSecondaryLoop(futures.values(), deadline);
        }
        for (Map.Entry<File, Future<Kind>> e : futures.entrySet()) {
            Kind k = takeUntil(e.getValue(), deadline, Kind.TIMEOUT);
            if (k == Kind.TIMEOUT) {
                log.warning("Filesystem probe timed out after " + timeoutMs + " ms for " + e.getKey()
                        + " (Dropbox/NFS/offline file?); skipping");
            }
            out.put(e.getKey(), k);
        }
        return out;
    }

    private static Kind probe(File file) {
        try {
            BasicFileAttributes a = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            if (a.isDirectory()) {
                return Kind.DIRECTORY;
            }
            if (a.isRegularFile() || a.isSymbolicLink()) {
                return Kind.FILE;
            }
            return Kind.OTHER;
        } catch (NoSuchFileException e) {
            return Kind.MISSING;
        } catch (IOException e) {
            return Kind.MISSING;
        }
    }

    private static <T> T callWithTimeout(Callable<T> task, long timeoutMs, T onTimeout) {
        Future<T> future = EXEC.submit(task);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(50L, timeoutMs));
        if (SwingUtilities.isEventDispatchThread()) {
            drainWithSecondaryLoop(List.of(future), deadline);
        }
        return takeUntil(future, deadline, onTimeout);
    }

    /**
     * Pump AWT while background probes run so splash ESC abort still works.
     */
    private static void drainWithSecondaryLoop(Iterable<? extends Future<?>> futures, long deadlineNanos) {
        EventQueue eq = Toolkit.getDefaultToolkit().getSystemEventQueue();
        SecondaryLoop loop = eq.createSecondaryLoop();
        EXEC.submit(() -> {
            try {
                while (System.nanoTime() < deadlineNanos && anyIncomplete(futures)) {
                    long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
                    Thread.sleep(Math.min(Math.max(1L, remainingMs), 20L));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                loop.exit();
            }
        });
        if (!loop.enter()) {
            log.fine("SecondaryLoop.enter() failed; falling back to blocking file-access wait");
        }
    }

    private static boolean anyIncomplete(Iterable<? extends Future<?>> futures) {
        for (Future<?> f : futures) {
            if (!f.isDone()) {
                return true;
            }
        }
        return false;
    }

    private static <T> T takeUntil(Future<T> future, long deadlineNanos, T onTimeout) {
        long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
        if (remainingMs <= 0 && !future.isDone()) {
            return onTimeout;
        }
        try {
            return future.get(Math.max(0L, remainingMs), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            return onTimeout;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return onTimeout;
        } catch (Exception e) {
            return onTimeout;
        }
    }
}
