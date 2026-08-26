package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

/**
 * Fail-closed gate for the post-reset SciDVS stream.
 *
 * <p>Completed transfers count only while qualification is active. A reported
 * transfer/reset failure is permanent for that qualification attempt. Meeting
 * the byte and callback thresholds is necessary but does not open publication;
 * the owner must explicitly commit after restoring the requested source state.</p>
 */
final class SciDVSPhaseQualification {

    private final long requiredBytes;
    private final int requiredCallbacks;

    private boolean active;
    private boolean committed;
    private long completedBytes;
    private int completedCallbacks;
    private String failure;

    SciDVSPhaseQualification(final int requiredBytes, final int requiredCallbacks) {
        if (requiredBytes <= 0) {
            throw new IllegalArgumentException("requiredBytes must be positive");
        }
        if (requiredCallbacks <= 0) {
            throw new IllegalArgumentException("requiredCallbacks must be positive");
        }
        this.requiredBytes = requiredBytes;
        this.requiredCallbacks = requiredCallbacks;
    }

    synchronized void begin() {
        active = true;
        committed = false;
        completedBytes = 0;
        completedCallbacks = 0;
        notifyAll();
    }

    synchronized boolean isQuarantining() {
        return !committed;
    }

    synchronized boolean isActive() {
        return active && !committed && failure == null;
    }

    synchronized void noteCompletedTransfer(final int bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("completed transfer byte count must not be negative");
        }
        if (!active || committed || failure != null) {
            return;
        }
        completedCallbacks++;
        completedBytes = completedBytes > Long.MAX_VALUE - bytes
                ? Long.MAX_VALUE : completedBytes + bytes;
        notifyAll();
    }

    synchronized void noteFailure(final String description) {
        if (committed || failure != null) {
            return;
        }
        failure = description == null || description.isBlank()
                ? "unspecified post-reset stream failure" : description;
        notifyAll();
    }

    synchronized boolean awaitSuccess(final long timeoutMillis) {
        if (timeoutMillis < 0) {
            throw new IllegalArgumentException("timeoutMillis must not be negative");
        }
        if (!active || failure != null) {
            return false;
        }
        final long deadline = System.nanoTime() + (timeoutMillis * 1_000_000L);
        while (!thresholdsMet() && failure == null) {
            final long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                return false;
            }
            final long waitMillis = remainingNanos / 1_000_000L;
            final int waitNanos = (int) (remainingNanos % 1_000_000L);
            try {
                wait(waitMillis, waitNanos);
            } catch (final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                noteFailure("interrupted while awaiting post-reset stream qualification");
                return false;
            }
        }
        return failure == null && thresholdsMet();
    }

    synchronized void commit() {
        if (!active || failure != null || !thresholdsMet()) {
            throw new IllegalStateException("cannot commit an incomplete or failed SciDVS qualification");
        }
        committed = true;
        active = false;
        notifyAll();
    }

    private boolean thresholdsMet() {
        return completedBytes >= requiredBytes && completedCallbacks >= requiredCallbacks;
    }
}
