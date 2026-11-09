package net.sf.jaer.hardwareinterface.usb;

/**
 * Detects live {@code USBTransferThread} event loops on the shared libusb
 * context. Sync {@code LibUsb.controlTransfer} (classic FX3 4-byte SPI)
 * deadlocks with those loops on WinUSB; the 500 ms timeout never returns.
 */
public final class LibUsbAsyncReaderRegistry {

    /**
     * Depth of {@link #beginPauseEventLoops()} for exclusive sync USB (EVK4
     * ISSD, classic DVX SPI). Sibling {@code acquire} must not restart
     * AEReaders while this is &gt; 0.
     */
    private static final java.util.concurrent.atomic.AtomicInteger pauseDepth
            = new java.util.concurrent.atomic.AtomicInteger();
    /** The jaer-aemon-open thread that paused siblings; must not await itself. */
    private static final ThreadLocal<Boolean> pauseOwner = new ThreadLocal<>();

    private LibUsbAsyncReaderRegistry() {
    }

    /**
     * Stop sibling {@code USBTransferThread}s before EVK4 Treuzell bulk.
     * WinUSB times out those bulks while other AEReaders sit in
     * {@code handleEventsTimeout} (jAER 12:46:45, op=0x79).
     */
    public static void beginPauseEventLoops() {
        pauseDepth.incrementAndGet();
        pauseOwner.set(Boolean.TRUE);
    }

    public static void endPauseEventLoops() {
        pauseDepth.updateAndGet(n -> n > 0 ? n - 1 : 0);
        if (pauseDepth.get() == 0) {
            pauseOwner.remove();
        }
    }

    public static boolean eventLoopsPausedForExclusiveSync() {
        return pauseDepth.get() > 0;
    }

    /**
     * Wait until EVK4 ISSD (or similar) has released sibling USB event loops.
     * The thread that called {@link #beginPauseEventLoops()} must not wait
     * here: it resumes sibling {@code setEventAcquisitionEnabled} while the
     * pause is still logically in force until {@link #endPauseEventLoops()}.
     */
    public static void awaitEventLoopsUnpaused(long timeoutMs) {
        if (pauseOwner.get() != null) {
            return;
        }
        final long end = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        while (eventLoopsPausedForExclusiveSync() && System.currentTimeMillis() < end) {
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * Threads that call {@code LibUsb.handleEventsTimeout} on the process
     * libusb context.
     */
    static boolean isLibusbEventLoopThread(Thread t) {
        if (t == null || !t.isAlive()) {
            return false;
        }
        final String n = t.getName();
        if (n == null) {
            return false;
        }
        return n.contains("USBTransferThread")
                || n.contains("AEReaderThread")
                || n.contains("AsyncStatusThread")
                || n.contains("PropheseeAEReader")
                || n.contains("NRVAEReader");
    }

    public static int liveEventLoopCount() {
        return liveEventLoopCount(false);
    }

    /**
     * @param ignoreNrv EVK4 open leaves the NRV AEReader up; do not treat it as
     *        a loop that must be stopped before ISSD
     */
    public static int liveEventLoopCount(boolean ignoreNrv) {
        int n = 0;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (!isLibusbEventLoopThread(t)) {
                continue;
            }
            if (ignoreNrv && t.getName() != null && t.getName().contains("NRVAEReader")) {
                continue;
            }
            n++;
        }
        return n;
    }

    /**
     * @param selfReaderAlive true when this device's AEReader UTT is already
     *        running (counts as one of {@link #liveEventLoopCount()})
     */
    public static boolean siblingEventLoopsLive(boolean selfReaderAlive) {
        return siblingEventLoopsLive(selfReaderAlive, false);
    }

    /**
     * @param ignoreNrv do not count an NRV AEReader left streaming on purpose
     */
    public static boolean siblingEventLoopsLive(boolean selfReaderAlive, boolean ignoreNrv) {
        final int n = liveEventLoopCount(ignoreNrv);
        if (selfReaderAlive) {
            return n > 1;
        }
        return n > 0;
    }
}
