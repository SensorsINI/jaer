package net.sf.jaer.hardwareinterface.usb;

import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * Validates and persists host-side USB async bulk-transfer buffer settings
 * (FIFO size and buffer count) used by NRV and Prophesee readers.
 */
public final class UsbReaderBufferSettings {

    public static final String PREF_KEY_FIFO_SIZE = "AEReader.fifoSize";
    public static final String PREF_KEY_NUM_BUFFERS = "AEReader.numBuffers";

    public static final int MIN_FIFO_SIZE = 4096;
    public static final int MAX_FIFO_SIZE = 20 * 1024 * 1024;
    public static final int MIN_NUM_BUFFERS = 1;
    public static final int MAX_NUM_BUFFERS = 32;
    public static final long MAX_TOTAL_USB_BUFFER_BYTES = (long) MAX_FIFO_SIZE * MAX_NUM_BUFFERS;

    private UsbReaderBufferSettings() {
    }

    /**
     * Copies a legacy hardware-root preference into the vendor node when the
     * canonical key is not yet set (e.g. {@code NRV.AEReader.fifoSize} →
     * {@code /jaer/hardware/NRV/AEReader.fifoSize}).
     */
    public static void migrateLegacyRootKey(Preferences hardwareRoot, String legacyRootKey,
            Preferences vendorPrefs, String vendorKey) {
        if (vendorPrefs.get(vendorKey, null) != null) {
            return;
        }
        if (hardwareRoot.get(legacyRootKey, null) == null) {
            return;
        }
        vendorPrefs.put(vendorKey, hardwareRoot.get(legacyRootKey, null));
        hardwareRoot.remove(legacyRootKey);
    }

    public static int loadFifoSize(Preferences prefs, String prefKey, int defaultFifoSize, Logger log,
            String deviceLabel) {
        final int stored = prefs.getInt(prefKey, defaultFifoSize);
        return applyFifoSize(prefs, prefKey, stored, log, deviceLabel);
    }

    public static int loadNumBuffers(Preferences prefs, String prefKey, int defaultNumBuffers, int fifoSize,
            Logger log, String deviceLabel) {
        final int stored = prefs.getInt(prefKey, defaultNumBuffers);
        return applyNumBuffers(prefs, prefKey, stored, fifoSize, log, deviceLabel);
    }

    public static int clampFifoSize(int fifoSize) {
        return Math.max(MIN_FIFO_SIZE, Math.min(fifoSize, MAX_FIFO_SIZE));
    }

    public static int clampNumBuffers(int numBuffers) {
        return Math.max(MIN_NUM_BUFFERS, Math.min(numBuffers, MAX_NUM_BUFFERS));
    }

    /** Clamps, persists, and returns the sanitized FIFO size. */
    public static int applyFifoSize(Preferences prefs, String prefKey, int fifoSize, Logger log,
            String deviceLabel) {
        final int sanitized = clampFifoSize(fifoSize);
        if (sanitized != fifoSize) {
            log.warning(String.format(
                    "Invalid %s USB FIFO size %d bytes; clamped to %d (valid range %d..%d). "
                            + "This can happen after repeatedly increasing FIFO size in Control menu or corrupted prefs.",
                    deviceLabel, fifoSize, sanitized, MIN_FIFO_SIZE, MAX_FIFO_SIZE));
        }
        prefs.putInt(prefKey, sanitized);
        return sanitized;
    }

    /** Clamps, persists, and returns the sanitized buffer count. */
    public static int applyNumBuffers(Preferences prefs, String prefKey, int numBuffers, int fifoSize,
            Logger log, String deviceLabel) {
        int n = clampNumBuffers(numBuffers);
        if (n != numBuffers) {
            log.warning(String.format(
                    "Invalid %s USB buffer count %d; clamped to %d (valid range %d..%d).",
                    deviceLabel, numBuffers, n, MIN_NUM_BUFFERS, MAX_NUM_BUFFERS));
        }
        final int safeFifo = clampFifoSize(fifoSize);
        if (safeFifo > 0) {
            final long total = (long) safeFifo * n;
            if (total > MAX_TOTAL_USB_BUFFER_BYTES) {
                n = (int) (MAX_TOTAL_USB_BUFFER_BYTES / safeFifo);
                if (n < MIN_NUM_BUFFERS) {
                    n = MIN_NUM_BUFFERS;
                }
                log.warning(String.format(
                        "%s USB buffer count reduced to %d so fifo (%d) x buffers stays under %d MB",
                        deviceLabel, n, safeFifo, MAX_TOTAL_USB_BUFFER_BYTES / (1024 * 1024)));
            }
        }
        prefs.putInt(prefKey, n);
        return n;
    }
}
