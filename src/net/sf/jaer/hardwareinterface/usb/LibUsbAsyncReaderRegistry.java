package net.sf.jaer.hardwareinterface.usb;

/**
 * Detects live {@code USBTransferThread} event loops on the shared libusb
 * context. Sync {@code LibUsb.controlTransfer} (classic FX3 4-byte SPI)
 * deadlocks with those loops on WinUSB; the 500 ms timeout never returns.
 */
public final class LibUsbAsyncReaderRegistry {

    private LibUsbAsyncReaderRegistry() {
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
        int n = 0;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (isLibusbEventLoopThread(t)) {
                n++;
            }
        }
        return n;
    }

    /**
     * @param selfReaderAlive true when this device's AEReader UTT is already
     *        running (counts as one of {@link #liveEventLoopCount()})
     */
    public static boolean siblingEventLoopsLive(boolean selfReaderAlive) {
        final int n = liveEventLoopCount();
        if (selfReaderAlive) {
            return n > 1;
        }
        return n > 0;
    }
}
