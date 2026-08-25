/*
 * Timed USB string-descriptor reads. LibUsb.getStringDescriptor has no timeout
 * and can hang forever on Windows WinUSB after rapid close/reopen (jAER log:
 * CypressFX3/Prophesee open never reaches "device opened" / ISSD).
 */
package net.sf.jaer.hardwareinterface.usb;

import java.nio.ByteBuffer;
import java.util.logging.Logger;

import org.usb4java.BufferUtils;
import org.usb4java.DeviceHandle;
import org.usb4java.LibUsb;

/**
 * Read USB string descriptors with a finite control-transfer timeout.
 */
public final class LibUsbStringDescriptors {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    /** Default per-string timeout; short enough to keep Interface switching responsive. */
    public static final long DEFAULT_TIMEOUT_MS = 500L;

    /** English (US) language ID used by most jAER cameras. */
    private static final short LANG_EN_US = 0x0409;

    private LibUsbStringDescriptors() {
    }

    /**
     * @param index USB string index (1=manufacturer, 2=product, 3=serial typical)
     * @return decoded string, or null on timeout/error
     */
    public static String read(DeviceHandle handle, byte index) {
        return read(handle, index, DEFAULT_TIMEOUT_MS);
    }

    public static String read(DeviceHandle handle, byte index, long timeoutMs) {
        if (handle == null || index == 0) {
            return null;
        }
        final ByteBuffer buf = BufferUtils.allocateByteBuffer(255);
        final byte bmRequestType = (byte) (LibUsb.ENDPOINT_IN | LibUsb.REQUEST_TYPE_STANDARD
                | LibUsb.RECIPIENT_DEVICE);
        final short wValue = (short) ((LibUsb.DT_STRING << 8) | (index & 0xff));
        final int n = LibUsb.controlTransfer(handle, bmRequestType, LibUsb.REQUEST_GET_DESCRIPTOR,
                wValue, LANG_EN_US, buf, Math.max(1L, timeoutMs));
        if (n < 2) {
            if (n < 0) {
                log.fine("string descriptor index=" + (index & 0xff) + ": " + LibUsb.errorName(n));
            }
            return null;
        }
        // USB string: bLength, bDescriptorType, then UTF-16LE code units
        final StringBuilder sb = new StringBuilder(Math.max(0, (n - 2) / 2));
        for (int i = 2; i + 1 < n; i += 2) {
            sb.append((char) ((buf.get(i) & 0xff) | ((buf.get(i + 1) & 0xff) << 8)));
        }
        return sb.toString();
    }
}
