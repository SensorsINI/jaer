package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

/** Hardware-free acceptance checks for the per-reader startup reset barrier. */
public final class Fx3StartupTimestampResetBarrierDemo {

    private static int assertions;

    private Fx3StartupTimestampResetBarrierDemo() {
    }

    public static void main(final String[] args) throws Exception {
        final Fx3StartupTimestampResetBarrier pending
                = new Fx3StartupTimestampResetBarrier();
        require(!pending.awaitReset(1),
                "a reader cannot pass before its hardware reset marker");

        final Fx3StartupTimestampResetBarrier observed
                = new Fx3StartupTimestampResetBarrier();
        observed.markResetObserved();
        require(observed.awaitReset(0),
                "a marker observed before the wait releases the reader");

        observed.markResetObserved();
        require(observed.awaitReset(0),
                "duplicate marker notification is idempotent");

        boolean rejected = false;
        try {
            observed.awaitReset(-1);
        } catch (final IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "negative timeout fails closed");

        System.out.println("FX3_STARTUP_TIMESTAMP_RESET_BARRIER ASSERTIONS=" + assertions);
        System.out.println("FX3_STARTUP_TIMESTAMP_RESET_BARRIER PASS");
    }

    private static void require(final boolean condition, final String description) {
        assertions++;
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
