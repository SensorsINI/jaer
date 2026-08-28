package net.sf.jaer.hardwareinterface.usb;

import java.beans.PropertyChangeSupport;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serializes USB async-bulk reader sessions so FIFO/buffer-count changes never
 * overlap libusb transfer sets. Scroll-wheel adjustments update a pending
 * {@link Config}; one idle-time reconfiguration replaces the session after
 * {@link #DEFAULT_DEBOUNCE_MS} with no further edits. Edits that arrive while a
 * replace is in flight are stored as the next requested size and applied only
 * after that replace finishes and another idle interval has elapsed.
 * <p>
 * In-flight {@code USBTransferThread.setBufferSize}/{@code setBufferNumber} is
 * not used: reallocating URBs owned by libusb causes {@code LIBUSB_ERROR_IO}.
 * A join timeout is a failed session; the host must recover the device rather
 * than start a competing reader on the same handle.
 */
public final class UsbAsyncBulkReaderLifecycle {

    public static final String EVENT_CONFIG_PENDING = "usbBufferConfigPending";
    public static final String EVENT_CONFIG_APPLIED = "usbBufferConfigApplied";
    /** Fired with a {@link Status} snapshot whenever phase/requested/active changes. */
    public static final String EVENT_CONFIG_STATUS = "usbBufferConfigStatus";

    /** Idle time after the last FIFO/buffer edit before replacing the USB session. */
    public static final long DEFAULT_DEBOUNCE_MS = 1000L;
    public static final long DEFAULT_JOIN_TIMEOUT_MS = 3000L;

    public enum State {
        STOPPED, STARTING, RUNNING, QUIESCING, FAILED
    }

    /** Coarse UI-facing phase for requested vs active USB buffer settings. */
    public enum Phase {
        /** Reader stopped; values stored for the next acquisition start. */
        STORED_FOR_NEXT_START,
        /** Live session is using the applied settings. */
        ACTIVE,
        /** A newer setting is queued (debounce idle). */
        QUEUED,
        /** Old session stopping or new session starting. */
        RESTARTING,
        /** Stop/start failed; device recovery is required. */
        FAILED
    }

    /** Immutable FIFO/buffer snapshot for one reader session. */
    public static final class Config {
        public final int fifoSize;
        public final int numBuffers;

        public Config(int fifoSize, int numBuffers) {
            this.fifoSize = fifoSize;
            this.numBuffers = numBuffers;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Config)) {
                return false;
            }
            final Config other = (Config) o;
            return fifoSize == other.fifoSize && numBuffers == other.numBuffers;
        }

        @Override
        public int hashCode() {
            return Objects.hash(fifoSize, numBuffers);
        }

        @Override
        public String toString() {
            return "fifo=" + fifoSize + " buffers=" + numBuffers;
        }
    }

    /** Immutable requested/active status for Control menu and tuning dialog. */
    public static final class Status {
        public final Phase phase;
        public final Config requested;
        public final Config active;
        public final long generation;
        public final String detail;

        public Status(Phase phase, Config requested, Config active, long generation, String detail) {
            this.phase = phase;
            this.requested = requested;
            this.active = active;
            this.generation = generation;
            this.detail = detail;
        }

        public String shortLabel() {
            switch (phase) {
                case QUEUED:
                    return "Queued";
                case RESTARTING:
                    return "Restarting";
                case ACTIVE:
                    return "Active";
                case STORED_FOR_NEXT_START:
                    return "Stored for next start";
                case FAILED:
                    return "Failed";
                default:
                    return phase.name();
            }
        }
    }

    /**
     * Device-specific transfer start/stop. Must not call back into
     * {@link UsbAsyncBulkReaderLifecycle#schedule} or wait on this lifecycle's
     * executor (would deadlock the reconfig worker).
     */
    public interface Host {
        String deviceLabel();

        Logger log();

        PropertyChangeSupport readerSupport();

        /** True when a transfer thread is allocated and alive. */
        boolean hasActiveTransfer();

        /**
         * Stop the current transfer generation: deactivate callbacks, cancel
         * URBs, join. Return true only if the thread is no longer alive. Do
         * not discard the thread reference if join timed out.
         * <p>
         * {@code USBTransferThread} resubmits every transfer that completes and
         * exits only once its transfer list is empty, so a device that keeps
         * filling the endpoint can never be joined. Hosts must quiesce the data
         * source (stop sensor streaming) before joining.
         */
        boolean stopSession(long generation, long joinTimeoutMs);

        /**
         * Allocate and start a new transfer thread bound to {@code generation}.
         *
         * @return the configuration actually used (may be smaller after a FIFO
         * fallback)
         */
        Config startSession(Config requested, long generation) throws Exception;

        /**
         * Store sizes when the reader is not running so the next start uses them.
         */
        void applyIdleConfig(Config config);

        /**
         * Join timed out or start failed. Must not start another reader on the
         * same handle; close or recover the device instead.
         */
        void recoverFailedSession(Config pending, Exception cause);
    }

    private final Host host;
    private final long debounceMs;
    private final long joinTimeoutMs;
    private final ScheduledExecutorService executor;

    private final AtomicReference<Config> pending = new AtomicReference<>();
    private final AtomicReference<Config> applied = new AtomicReference<>();
    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<State> state = new AtomicReference<>(State.STOPPED);
    private final AtomicBoolean restartEnabled = new AtomicBoolean(true);
    private final AtomicBoolean applying = new AtomicBoolean(false);
    private final AtomicReference<String> lastFailure = new AtomicReference<>();

    private final Object debounceLock = new Object();
    private ScheduledFuture<?> debounceTask;

    public UsbAsyncBulkReaderLifecycle(Host host) {
        this(host, DEFAULT_DEBOUNCE_MS, DEFAULT_JOIN_TIMEOUT_MS);
    }

    public UsbAsyncBulkReaderLifecycle(Host host, long debounceMs, long joinTimeoutMs) {
        this.host = Objects.requireNonNull(host, "host");
        this.debounceMs = Math.max(0L, debounceMs);
        this.joinTimeoutMs = Math.max(1L, joinTimeoutMs);
        final ThreadFactory tf = r -> {
            final Thread t = new Thread(r, host.deviceLabel() + "-usb-reconfig");
            t.setDaemon(true);
            return t;
        };
        this.executor = Executors.newSingleThreadScheduledExecutor(tf);
    }

    public State state() {
        return state.get();
    }

    public long currentGeneration() {
        return generation.get();
    }

    public Config pendingConfig() {
        return pending.get();
    }

    public Config appliedConfig() {
        return applied.get();
    }

    /** Immutable snapshot of requested vs active USB buffer configuration. */
    public Status statusSnapshot() {
        final Config req = pending.get();
        final Config act = applied.get();
        final State st = state.get();
        final Phase phase;
        if (st == State.FAILED) {
            phase = Phase.FAILED;
        } else if (st == State.QUIESCING || st == State.STARTING || applying.get()) {
            phase = Phase.RESTARTING;
        } else if (debounceTaskQueued()) {
            phase = Phase.QUEUED;
        } else if (st == State.RUNNING && host.hasActiveTransfer()) {
            if (req != null && act != null && !req.equals(act)) {
                phase = Phase.QUEUED;
            } else {
                phase = Phase.ACTIVE;
            }
        } else {
            phase = Phase.STORED_FOR_NEXT_START;
        }
        return new Status(phase, req, act, generation.get(), lastFailure.get());
    }

    /**
     * True while a queued or in-flight reconfiguration has not yet matched the
     * pending snapshot, or a debounce is waiting.
     */
    public boolean isReconfigPending() {
        if (debounceTaskQueued()) {
            return true;
        }
        if (applying.get()) {
            return true;
        }
        final Config p = pending.get();
        final Config a = applied.get();
        return p != null && !p.equals(a) && host.hasActiveTransfer();
    }

    /**
     * Callbacks of {@code sessionGeneration} may parse/commit only in
     * {@link State#RUNNING} for the current generation.
     */
    public boolean isCurrent(long sessionGeneration) {
        return state.get() == State.RUNNING && generation.get() == sessionGeneration;
    }

    /**
     * Queue a FIFO/buffer change. Coalesces rapid calls; applies after
     * {@code debounceMs} of idle time. While a replace is running, only the
     * requested snapshot is updated — no new session is started until the
     * current one finishes.
     */
    public void schedule(Config config) {
        Objects.requireNonNull(config, "config");
        pending.set(config);
        lastFailure.set(null);
        fire(EVENT_CONFIG_PENDING, config);
        restartEnabled.set(true);
        if (applying.get()) {
            fireStatus();
            return;
        }
        scheduleApplyAfterIdle();
    }

    private void scheduleApplyAfterIdle() {
        synchronized (debounceLock) {
            if (debounceTask != null) {
                debounceTask.cancel(false);
            }
            debounceTask = executor.schedule(this::applyPending, debounceMs, TimeUnit.MILLISECONDS);
        }
        fireStatus();
    }

    /**
     * Store a configuration synchronously for the next externally initiated
     * reader start. No replacement session is queued or started.
     *
     * @throws IllegalStateException if a transfer session is active
     */
    public void storeForNextStart(Config config) {
        Objects.requireNonNull(config, "config");
        synchronized (debounceLock) {
            if (host.hasActiveTransfer()) {
                throw new IllegalStateException(
                        host.deviceLabel() + " reader must be stopped before storing USB buffer settings");
            }
            restartEnabled.set(false);
            if (debounceTask != null) {
                debounceTask.cancel(false);
                debounceTask = null;
            }
            pending.set(config);
            applied.set(config);
            state.set(State.STOPPED);
            lastFailure.set(null);
            host.applyIdleConfig(config);
        }
        fire(EVENT_CONFIG_PENDING, config);
        fire(EVENT_CONFIG_APPLIED, config);
        fireStatus();
    }

    /** Test helper: apply the current pending snapshot immediately. */
    public void applyNow() {
        synchronized (debounceLock) {
            if (debounceTask != null) {
                debounceTask.cancel(false);
                debounceTask = null;
            }
        }
        try {
            executor.submit(this::applyPending).get(joinTimeoutMs + 2000L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            host.log().log(Level.WARNING, host.deviceLabel() + " applyNow failed", e);
        }
    }

    /**
     * Bind a generation to a start that the host initiated (acquisition enable).
     */
    public long adoptExternalStart(Config config) {
        Objects.requireNonNull(config, "config");
        restartEnabled.set(true);
        pending.compareAndSet(null, config);
        if (pending.get() == null) {
            pending.set(config);
        }
        applied.set(config);
        final long gen = generation.incrementAndGet();
        state.set(State.RUNNING);
        lastFailure.set(null);
        fire(EVENT_CONFIG_APPLIED, config);
        fireStatus();
        return gen;
    }

    /**
     * Cancel a queued restart (user stopped acquisition). Does not wait for an
     * in-flight apply; the worker checks {@link #restartEnabled} before start.
     */
    public void discardPendingRestart() {
        restartEnabled.set(false);
        synchronized (debounceLock) {
            if (debounceTask != null) {
                debounceTask.cancel(false);
                debounceTask = null;
            }
        }
    }

    public void markQuiescing() {
        state.set(State.QUIESCING);
        fireStatus();
    }

    public void markStopped() {
        state.set(State.STOPPED);
        fireStatus();
    }

    public void markFailed() {
        markFailed("USB reader session failed");
    }

    public void markFailed(String detail) {
        state.set(State.FAILED);
        if (detail != null) {
            lastFailure.set(detail);
        }
        fireStatus();
    }

    /**
     * Interrupt {@code thread} and join. Returns true only if it is no longer
     * alive. Does not clear the caller's reference.
     */
    public static boolean interruptAndJoin(Thread thread, long timeoutMs, Logger log, String label) {
        if (thread == null) {
            return true;
        }
        thread.interrupt();
        try {
            thread.join(timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return !thread.isAlive();
        }
        if (thread.isAlive()) {
            log.warning(label + " did not stop within " + timeoutMs + " ms; not starting a replacement reader");
            return false;
        }
        return true;
    }

    /**
     * {@code LibUsb.close} / {@code releaseInterface} with a live
     * {@code USBTransferThread} in native libusb crashed the JVM
     * ({@code hs_err_pid34924}: {@code EXCEPTION_ACCESS_VIOLATION} in
     * {@code ntdll} from {@code LibUsb.close} on {@code jaer-hw-close}).
     *
     * @return {@code true} when native close must be skipped
     */
    public static boolean abandonNativeHandle(boolean readerStopped, Logger log, String label) {
        if (readerStopped) {
            return false;
        }
        log.warning(label
                + ": AEReader still in native LibUsb; abandoning USB handle — do not LibUsb.close (hs_err pid34924)");
        return true;
    }

    public void awaitIdle(long timeoutMs) throws InterruptedException, TimeoutException {
        final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(1L, timeoutMs));
        while (true) {
            final long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                throw new TimeoutException(host.deviceLabel() + " USB reconfig did not go idle");
            }
            if (!debounceTaskQueued() && !applying.get()) {
                final Future<?> drained = executor.submit(() -> {
                });
                try {
                    drained.get(Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remaining)), TimeUnit.MILLISECONDS);
                } catch (ExecutionException | CancellationException e) {
                    throw new TimeoutException(host.deviceLabel() + " USB reconfig drain failed: " + e);
                }
                if (!debounceTaskQueued() && !applying.get()) {
                    return;
                }
            }
            Thread.sleep(Math.min(20L, Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remaining))));
        }
    }

    public void shutdown() {
        discardPendingRestart();
        executor.shutdownNow();
    }

    private boolean debounceTaskQueued() {
        synchronized (debounceLock) {
            return debounceTask != null && !debounceTask.isDone();
        }
    }

    private void applyPending() {
        applying.set(true);
        try {
            synchronized (debounceLock) {
                if (debounceTask != null) {
                    debounceTask.cancel(false);
                    debounceTask = null;
                }
            }
            Config want = pending.get();
            if (want == null) {
                return;
            }
            if (!restartEnabled.get()) {
                host.applyIdleConfig(want);
                applied.set(want);
                state.set(State.STOPPED);
                fire(EVENT_CONFIG_APPLIED, want);
                fireStatus();
                return;
            }
            if (!host.hasActiveTransfer()) {
                host.applyIdleConfig(want);
                applied.set(want);
                state.set(State.STOPPED);
                fire(EVENT_CONFIG_APPLIED, want);
                fireStatus();
                return;
            }
            final Config current = applied.get();
            if (want.equals(current) && state.get() == State.RUNNING) {
                fireStatus();
                return;
            }

            host.log().info(host.deviceLabel() + " applying USB buffer config " + want
                    + " (replacing transfer session; cap " + UsbReaderBufferSettings.MAX_FIFO_SIZE + ")");
            state.set(State.QUIESCING);
            fireStatus();
            final long replaceStartNs = System.nanoTime();
            final long oldGen = generation.get();
            final boolean stopped = host.stopSession(oldGen, joinTimeoutMs);
            if (!stopped) {
                final String detail = host.deviceLabel() + " USB reader did not stop within " + joinTimeoutMs + " ms";
                lastFailure.set(detail);
                state.set(State.FAILED);
                fireStatus();
                host.recoverFailedSession(want, new TimeoutException(detail));
                return;
            }
            if (!restartEnabled.get()) {
                applied.set(want);
                state.set(State.STOPPED);
                fire(EVENT_CONFIG_APPLIED, want);
                fireStatus();
                return;
            }
            final long newGen = generation.incrementAndGet();
            state.set(State.STARTING);
            fireStatus();
            try {
                final Config used = host.startSession(want, newGen);
                final Config appliedNow = used != null ? used : want;
                applied.set(appliedNow);
                pending.compareAndSet(want, appliedNow);
                lastFailure.set(null);
                state.set(State.RUNNING);
                fire(EVENT_CONFIG_APPLIED, appliedNow);
                fireStatus();
                host.log().info(host.deviceLabel() + " USB buffer config " + appliedNow + " active after "
                        + ((System.nanoTime() - replaceStartNs) / 1_000_000L) + " ms of no acquisition");
            } catch (Exception e) {
                lastFailure.set(e.getMessage() != null ? e.getMessage() : e.toString());
                state.set(State.FAILED);
                fireStatus();
                host.log().log(Level.WARNING, host.deviceLabel() + " failed to start USB reader after buffer change", e);
                host.recoverFailedSession(want, e);
            }
        } finally {
            applying.set(false);
            final Config stillPending = pending.get();
            final Config nowApplied = applied.get();
            if (restartEnabled.get() && stillPending != null && !stillPending.equals(nowApplied)
                    && host.hasActiveTransfer() && state.get() == State.RUNNING) {
                // Wheel moved during this replace: keep the last requested size
                // and wait a full idle interval after the session is up.
                scheduleApplyAfterIdle();
            } else if (restartEnabled.get() && stillPending != null
                    && !stillPending.equals(nowApplied) && !host.hasActiveTransfer()) {
                // The session stopped while a later wheel edit arrived. Store
                // that final request for the next start instead of leaving a
                // phantom pending status.
                scheduleApplyAfterIdle();
            } else if (stillPending != null && !stillPending.equals(nowApplied)) {
                // A failed replacement still owns an active/native session and
                // cannot be retried safely here. Reconcile status to the last
                // proven active config; explicit later input can schedule anew.
                pending.compareAndSet(stillPending, nowApplied);
                fireStatus();
            }
        }
    }

    private void fire(String event, Config config) {
        final PropertyChangeSupport pcs = host.readerSupport();
        if (pcs != null) {
            pcs.firePropertyChange(event, null, config);
        }
    }

    private void fireStatus() {
        final PropertyChangeSupport pcs = host.readerSupport();
        if (pcs != null) {
            pcs.firePropertyChange(EVENT_CONFIG_STATUS, null, statusSnapshot());
        }
    }
}
