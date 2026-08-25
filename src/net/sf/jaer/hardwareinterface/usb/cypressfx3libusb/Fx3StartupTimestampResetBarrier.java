package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * One-reader barrier that proves a hardware timestamp-reset marker reached the
 * decoder before a restarted FX3 acquisition becomes visible to consumers.
 */
final class Fx3StartupTimestampResetBarrier {

    private final CountDownLatch resetObserved = new CountDownLatch(1);

    void markResetObserved() {
        resetObserved.countDown();
    }

    boolean awaitReset(final long timeoutMs) throws InterruptedException {
        if (timeoutMs < 0) {
            throw new IllegalArgumentException("timeoutMs must be nonnegative");
        }
        return resetObserved.await(timeoutMs, TimeUnit.MILLISECONDS);
    }
}
