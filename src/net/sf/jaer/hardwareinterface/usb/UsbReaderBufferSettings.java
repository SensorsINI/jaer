package net.sf.jaer.hardwareinterface.usb;

import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * Validates and persists host-side USB async bulk-transfer buffer settings
 * (FIFO size and buffer count) used by NRV and Prophesee readers.
 */
public final class UsbReaderBufferSettings {

    public static final int MIN_FIFO_SIZE = 4096;
    public static final int MAX_FIFO_SIZE = 1 << 22;
    public static final int MAX_NUM_BUFFERS = 32;
    public static final long MAX_TOTAL_USB_BUFFER_BYTES = 64L * 1024L * 1024L;

    private UsbReaderBufferSettings() {
    }

    public static int loadFifoSize(Preferences prefs, String prefKey, int defaultFifoSize, Logger log,
            String deviceLabel) {
        return sanitizeFifoSize(prefs, prefKey, prefs.getInt(prefKey, defaultFifoSize), defaultFifoSize, log,
                deviceLabel);
    }

    public static int loadNumBuffers(Preferences prefs, String prefKey, int defaultNumBuffers, int fifoSize,
            Logger log, String deviceLabel) {
        return sanitizeNumBuffers(prefs, prefKey, prefs.getInt(prefKey, defaultNumBuffers), fifoSize,
                defaultNumBuffers, log, deviceLabel);
    }

    public static int sanitizeFifoSize(Preferences prefs, String prefKey, int fifoSize, int defaultFifoSize,
            Logger log, String deviceLabel) {
        if (fifoSize >= MIN_FIFO_SIZE && fifoSize <= MAX_FIFO_SIZE) {
            return fifoSize;
        }
        log.warning(String.format(
                "Invalid %s USB FIFO size %d bytes; resetting to %d (valid range %d..%d). "
                        + "This can happen after repeatedly increasing FIFO size in Control menu or corrupted prefs.",
                deviceLabel, fifoSize, defaultFifoSize, MIN_FIFO_SIZE, MAX_FIFO_SIZE));
        prefs.putInt(prefKey, defaultFifoSize);
        return defaultFifoSize;
    }

    public static int sanitizeNumBuffers(Preferences prefs, String prefKey, int numBuffers, int fifoSize,
            int defaultNumBuffers, Logger log, String deviceLabel) {
        int n = numBuffers;
        if (n < 1) {
            n = Math.max(1, defaultNumBuffers);
        }
        if (n > MAX_NUM_BUFFERS) {
            n = MAX_NUM_BUFFERS;
        }
        final int safeFifo = Math.max(MIN_FIFO_SIZE, Math.min(fifoSize, MAX_FIFO_SIZE));
        if (safeFifo > 0) {
            final long total = (long) safeFifo * n;
            if (total > MAX_TOTAL_USB_BUFFER_BYTES) {
                n = (int) (MAX_TOTAL_USB_BUFFER_BYTES / safeFifo);
                if (n < 1) {
                    n = 1;
                }
                log.warning(String.format(
                        "%s USB buffer count reduced to %d so fifo (%d) x buffers stays under %d MB",
                        deviceLabel, n, safeFifo, MAX_TOTAL_USB_BUFFER_BYTES / (1024 * 1024)));
            }
        }
        if (n != numBuffers) {
            prefs.putInt(prefKey, n);
        }
        return n;
    }
}
