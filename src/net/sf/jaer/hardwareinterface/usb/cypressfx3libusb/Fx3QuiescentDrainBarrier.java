package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Per-reader barrier that waits until completed USB transfers have carried no
 * payload for a bounded quiet interval while event sources are stopped.
 */
final class Fx3QuiescentDrainBarrier {

    private static final int TRANSFER_TIMELINE_CAPACITY = 4_096;

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final LongSupplier nanoTime;
    private final Sleeper sleeper;
    private boolean draining;
    private long drainStartedNanos;
    private long lastPayloadNanos;
    private final int[] completedTransferLengths
            = new int[TRANSFER_TIMELINE_CAPACITY];
    private final long[] completedTransferElapsedNanos
            = new long[TRANSFER_TIMELINE_CAPACITY];
    private final boolean[] completedTransferSourcePayload
            = new boolean[TRANSFER_TIMELINE_CAPACITY];
    private int completedTransferCount;
    private boolean transferTimelineTruncated;

    Fx3QuiescentDrainBarrier() {
        this(System::nanoTime, Thread::sleep);
    }

    Fx3QuiescentDrainBarrier(final LongSupplier nanoTime,
            final Sleeper sleeper) {
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    synchronized void beginDrain() {
        final long now = nanoTime.getAsLong();
        drainStartedNanos = now;
        lastPayloadNanos = now;
        completedTransferCount = 0;
        transferTimelineTruncated = false;
        draining = true;
    }

    synchronized void noteCompletedTransfer(final int actualLength,
            final boolean sourcePayload) {
        if (actualLength < 0) {
            throw new IllegalArgumentException("actualLength must be nonnegative");
        }
        if (sourcePayload && actualLength == 0) {
            throw new IllegalArgumentException(
                    "zero-length transfer cannot contain source payload");
        }
        if (draining) {
            final long now = nanoTime.getAsLong();
            if (completedTransferCount < TRANSFER_TIMELINE_CAPACITY) {
                completedTransferLengths[completedTransferCount] = actualLength;
                completedTransferElapsedNanos[completedTransferCount]
                        = now - drainStartedNanos;
                completedTransferSourcePayload[completedTransferCount]
                        = sourcePayload;
                completedTransferCount++;
            } else {
                transferTimelineTruncated = true;
            }
            if (sourcePayload) {
                lastPayloadNanos = now;
            }
        }
    }

    synchronized boolean isDraining() {
        return draining;
    }

    synchronized int getCompletedTransferCount() {
        return completedTransferCount;
    }

    synchronized int getCompletedTransferLength(final int index) {
        checkCompletedTransferIndex(index);
        return completedTransferLengths[index];
    }

    synchronized long getCompletedTransferElapsedNanos(final int index) {
        checkCompletedTransferIndex(index);
        return completedTransferElapsedNanos[index];
    }

    synchronized boolean getCompletedTransferSourcePayload(final int index) {
        checkCompletedTransferIndex(index);
        return completedTransferSourcePayload[index];
    }

    synchronized boolean isTransferTimelineTruncated() {
        return transferTimelineTruncated;
    }

    boolean awaitQuiescence(final long quietMillis, final long timeoutMillis)
            throws InterruptedException {
        final long quietNanos = checkedMillisToNanos(quietMillis, "quietMillis");
        final long timeoutNanos = checkedMillisToNanos(timeoutMillis, "timeoutMillis");
        if (timeoutNanos < quietNanos) {
            throw new IllegalArgumentException("timeoutMillis must cover quietMillis");
        }

        while (true) {
            final long sleepNanos;
            synchronized (this) {
                if (!draining) {
                    return false;
                }
                final long now = nanoTime.getAsLong();
                final long quietElapsed = now - lastPayloadNanos;
                if (quietElapsed >= quietNanos) {
                    return true;
                }
                final long totalElapsed = now - drainStartedNanos;
                if (totalElapsed >= timeoutNanos) {
                    return false;
                }
                sleepNanos = Math.min(quietNanos - quietElapsed,
                        timeoutNanos - totalElapsed);
            }
            sleeper.sleep(ceilNanosToMillis(sleepNanos));
        }
    }

    synchronized void endDrain() {
        draining = false;
    }

    private void checkCompletedTransferIndex(final int index) {
        if (index < 0 || index >= completedTransferCount) {
            throw new IndexOutOfBoundsException(
                    "completed transfer index " + index + " outside [0, "
                    + completedTransferCount + ")");
        }
    }

    private static long checkedMillisToNanos(final long millis,
            final String name) {
        if (millis <= 0L) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return Math.multiplyExact(millis, TimeUnit.MILLISECONDS.toNanos(1L));
    }

    private static long ceilNanosToMillis(final long nanos) {
        long millis = TimeUnit.NANOSECONDS.toMillis(nanos);
        if (TimeUnit.MILLISECONDS.toNanos(millis) < nanos) {
            millis++;
        }
        return Math.max(1L, millis);
    }
}
