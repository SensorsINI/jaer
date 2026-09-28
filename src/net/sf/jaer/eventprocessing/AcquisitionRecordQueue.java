/*
 * AcquisitionRecordQueue.java
 *
 * Bounded queue so acquisition-cycle recording does not block the USB thread on disk.
 */
package net.sf.jaer.eventprocessing;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.AcquisitionBundleCopy;
import net.sf.jaer.event.PacketBundle;

/**
 * Hands filter output to a daemon writer. If every slot is busy, the slice is
 * dropped and the USB callback returns.
 */
public final class AcquisitionRecordQueue {

    /** Preference key. Default depth is 8. */
    public static final String PREF_DEPTH = "AEViewer.acquisitionRecordQueueDepth";

    public interface Sink {
        void write(PacketBundle bundle) throws Exception;
    }

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    private static final class Slot {
        final PacketBundle bundle = new PacketBundle();
    }

    private final ArrayBlockingQueue<Slot> free;
    private final ArrayBlockingQueue<Slot> pending;
    private final Sink sink;
    private final AtomicBoolean accepting = new AtomicBoolean(false);
    private final AtomicInteger drops = new AtomicInteger();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final Thread writer;
    private volatile boolean running = true;

    public AcquisitionRecordQueue(int depth, Sink sink) {
        if (sink == null) {
            throw new NullPointerException("sink");
        }
        int n = Math.max(1, depth);
        free = new ArrayBlockingQueue<>(n);
        pending = new ArrayBlockingQueue<>(n);
        for (int i = 0; i < n; i++) {
            free.add(new Slot());
        }
        this.sink = sink;
        writer = new Thread(this::run, "AEViewer.AcquisitionRecord");
        writer.setDaemon(true);
        writer.start();
    }

    public static int depthFrom(AEChip chip) {
        int depth = 8;
        if (chip != null && chip.getPrefs() != null) {
            depth = chip.getPrefs().getInt(PREF_DEPTH, 8);
        }
        return Math.max(1, depth);
    }

    /**
     * Copies {@code src} into a free slot and returns. Does not block when the
     * queue is full.
     */
    public void offer(PacketBundle src) {
        if (!accepting.get() || src == null || src.isEmpty()) {
            return;
        }
        Slot slot = free.poll();
        if (slot == null) {
            drops.incrementAndGet();
            return;
        }
        inFlight.incrementAndGet();
        try {
            AcquisitionBundleCopy.copyInto(slot.bundle, src);
            if (!pending.offer(slot)) {
                drops.incrementAndGet();
                recycle(slot);
            }
        } catch (RuntimeException e) {
            recycle(slot);
            throw e;
        }
    }

    public void resume() {
        accepting.set(true);
    }

    /**
     * Stops new offers and waits until queued slices have been written.
     * Returns immediately on the writer thread.
     */
    public void drain() {
        accepting.set(false);
        if (isWriterThread()) {
            return;
        }
        long deadline = System.currentTimeMillis() + 2000L;
        while (System.currentTimeMillis() < deadline) {
            if (pending.isEmpty() && inFlight.get() == 0) {
                return;
            }
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.warning("acquisition record queue drain timed out; pending=" + pending.size()
                + " inFlight=" + inFlight.get());
    }

    public int getDrops() {
        return drops.get();
    }

    public boolean isWriterThread() {
        return Thread.currentThread() == writer;
    }

    /** Stops the daemon. Used by tests. */
    public void shutdown() {
        running = false;
        accepting.set(false);
        writer.interrupt();
    }

    private void recycle(Slot slot) {
        slot.bundle.clear();
        free.offer(slot);
        inFlight.decrementAndGet();
    }

    private void run() {
        while (running) {
            try {
                Slot slot = pending.poll(200, TimeUnit.MILLISECONDS);
                if (slot == null) {
                    continue;
                }
                try {
                    sink.write(slot.bundle);
                } catch (Exception e) {
                    log.log(Level.SEVERE, "acquisition record write failed", e);
                } finally {
                    recycle(slot);
                }
            } catch (InterruptedException e) {
                if (!running) {
                    return;
                }
            }
        }
    }
}
