package prophesee.usb;

import org.usb4java.Context;
import org.usb4java.LibUsb;
import org.usb4java.LibUsbException;

/**
 * Dedicated libusb session for EVK4. Cypress / DVS128
 * {@code USBTransferThread}s pump the default context; EVK4 ISSD
 * ({@code LibUsb.bulkTransfer}) and the 2 MiB event reader must not share
 * that {@code handleEvents} loop (sibling cameras stall / exceptional-close).
 */
public final class PropheseeLibUsb {

    private static final Context CONTEXT = new Context();
    private static volatile boolean initialized;

    private PropheseeLibUsb() {
    }

    public static synchronized Context context() {
        if (!initialized) {
            final int result = LibUsb.init(CONTEXT);
            if (result != LibUsb.SUCCESS) {
                throw new LibUsbException("Unable to initialize Prophesee libusb context", result);
            }
            initialized = true;
        }
        return CONTEXT;
    }
}
