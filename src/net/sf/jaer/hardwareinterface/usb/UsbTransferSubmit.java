package net.sf.jaer.hardwareinterface.usb;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import li.longi.USBTransferThread.USBTransferThread;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Handshake for {@code USBTransferThread}: {@code allocateTransfers()} throws
 * {@link IllegalStateException} on {@code LIBUSB_ERROR_NOT_FOUND} (endpoint
 * missing after a hotplug SuperSpeed claim) and never reaches the shutdown
 * callback. Catch that on the reader thread and {@link Thread#join(long) join}
 * until URBs are queued or the thread has died.
 */
public final class UsbTransferSubmit {

    /** Match Prophesee: {@code allocateTransfers} is the first thing {@code run()} does. */
    public static final long DEFAULT_QUEUE_WAIT_MS = 400L;

    private UsbTransferSubmit() {
    }

    /**
     * {@code NOT_FOUND} / {@code NO_DEVICE} / {@code PIPE} / {@code IO}: a smaller
     * FIFO cannot fix a missing endpoint or a gone device.
     */
    public static boolean isUnrecoverableSubmitFailure(Throwable t) {
        while (t != null) {
            final String msg = t.getMessage();
            if (msg != null) {
                final String u = msg.toUpperCase();
                if (u.contains("LIBUSB_ERROR_NOT_FOUND")
                        || u.contains("LIBUSB_ERROR_NO_DEVICE")
                        || u.contains("LIBUSB_ERROR_PIPE")
                        || u.contains("LIBUSB_ERROR_IO")) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }

    /** Unrecoverable libusb errors, or the jar's {@code could not submit transfer} wrap. */
    public static boolean isSubmitFailure(Throwable t) {
        if (isUnrecoverableSubmitFailure(t)) {
            return true;
        }
        Throwable cur = t;
        while (cur != null) {
            final String msg = cur.getMessage();
            if (msg != null && msg.toLowerCase().contains("could not submit")) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    /**
     * @param startError set when the thread dies (handshake and later)
     * @param running when true, {@code onDeadAfterStart} runs (must not join this thread)
     */
    public static void installFailureHandler(Thread thread, Logger log, String name,
            AtomicReference<Throwable> startError, AtomicBoolean running, Runnable onDeadAfterStart) {
        thread.setUncaughtExceptionHandler((t, ex) -> {
            startError.set(ex);
            log.log(Level.WARNING, name + " died: " + ex.getMessage(), ex);
            if (running.get() && onDeadAfterStart != null) {
                onDeadAfterStart.run();
            }
        });
    }

    public static boolean awaitQueued(Thread thread, Logger log, String name) {
        return awaitQueued(thread, DEFAULT_QUEUE_WAIT_MS, log, name);
    }

    /**
     * @return {@code true} if the thread is still alive after {@code timeoutMs}
     *         ({@code allocateTransfers} succeeded and URBs are queued)
     */
    public static boolean awaitQueued(Thread thread, long timeoutMs, Logger log, String name) {
        if (thread == null) {
            return false;
        }
        try {
            thread.join(timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warning(name + " interrupted while waiting for USB IN transfers to queue");
        }
        if (thread.isAlive()) {
            log.info(name + ": USB IN transfers queued");
            return true;
        }
        final Throwable submitFail = startFailureOf(thread, null);
        if (submitFail != null) {
            log.warning(name + " exited before USB IN transfers queued: " + submitFail.getMessage());
        } else {
            log.warning(name + " exited before USB IN transfers queued");
        }
        return false;
    }

    /**
     * {@link USBTransferThread#run()} catches {@code allocateTransfers} failures
     * and stores them on {@link USBTransferThread#getStartFailure()}; it does
     * not throw, so an uncaught-exception handler stays empty ("thread exited").
     */
    public static Throwable startFailureOf(Thread thread, AtomicReference<Throwable> startError) {
        if (startError != null) {
            final Throwable err = startError.get();
            if (err != null) {
                return err;
            }
        }
        if (thread instanceof USBTransferThread utt) {
            return utt.getStartFailure();
        }
        return null;
    }

    public static HardwareInterfaceException startFailed(String name, Throwable err) {
        final String detail = err != null ? err.getMessage() : "thread exited";
        return new HardwareInterfaceException(name + " failed to queue USB IN: " + detail, err);
    }
}
