package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

/** Headless regression for restarting an FX3 reader after pool reallocation. */
public final class CypressFX3BufferResetDemo {

    private CypressFX3BufferResetDemo() {
    }

    public static void main(final String[] args) {
        final Harness monitor = new Harness();
        monitor.seedCaptureCursor(42_616);
        monitor.reallocateBuffers();
        require(monitor.captureCursor() == 0,
                "pool reallocation retained capture cursor " + monitor.captureCursor());
        System.out.println("PASS CypressFX3 buffer reallocation resets capture cursor");
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Harness extends CypressFX3 {

        Harness() {
            super(null);
        }

        void seedCaptureCursor(final int value) {
            eventCounter = value;
        }

        void reallocateBuffers() {
            allocateAEBuffers();
        }

        int captureCursor() {
            return eventCounter;
        }
    }
}
