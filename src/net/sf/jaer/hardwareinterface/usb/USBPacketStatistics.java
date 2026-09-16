/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.hardwareinterface.usb;

import java.util.logging.Logger;

import de.thesycon.usbio.UsbIoBuf;
import li.longi.USBTransferThread.RestrictedTransfer;
import net.sf.jaer.util.EngineeringFormat;

/**
 * Tracks low-level USB IN transfer statistics for FIFO / buffer tuning.
 * Collection runs only while {@link #setShowUsbStatistics(boolean) show} or
 * {@link #setPrintUsbStatistics(boolean) print} is enabled. The USB tuning
 * panel enables show while it is open and calls {@link #takeSnapshot()} once
 * per second to display and rotate the window. Print remains available for a
 * throttled jAER log line when the panel is not open.
 *
 * @author tobi
 */
public class USBPacketStatistics implements HasUsbStatistics {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    /** Used only when printing without the tuning panel. */
    private static final long PRINT_INTERVAL_NS = 1_000_000_000L;

    private boolean initialized = false;
    EngineeringFormat engFmt = new EngineeringFormat();
    volatile boolean printEnabled = false;
    volatile boolean showEnabled = false;

    private int fifoSizeBytes;
    private int numBuffers;
    private int emptyCount;
    private int shortCount;
    private int errorCount;
    private int windowTransfers;
    private long windowBytes;
    private long windowStartNs;
    private long lastPrintNs;
    private int minBytes = Integer.MAX_VALUE;
    private int maxBytes;
    private int seq;
    private Snapshot lastCompleted = Snapshot.EMPTY;

    /**
     * One USB IN measurement window. Formatting is left to the UI so
     * {@link EngineeringFormat} is not used off the USB thread without the
     * collector lock.
     */
    public static final class Snapshot {
        public static final Snapshot EMPTY = new Snapshot(
                false, 0, 0, 0, 0f, 0f, 0d, 0, 0, 0, 0, 0, 0, 0d, "");

        public final boolean ready;
        public final int seq;
        public final int fifoSizeBytes;
        public final int numBuffers;
        public final float avgPacketBytes;
        public final float avgIntervalUs;
        public final double bytesPerSec;
        public final int transfers;
        public final int emptyCount;
        public final int shortCount;
        public final int errorCount;
        public final int minBytes;
        public final int maxBytes;
        public final double elapsedS;
        public final String hint;

        Snapshot(boolean ready, int seq, int fifoSizeBytes, int numBuffers, float avgPacketBytes,
                float avgIntervalUs, double bytesPerSec, int transfers, int emptyCount,
                int shortCount, int errorCount, int minBytes, int maxBytes, double elapsedS, String hint) {
            this.ready = ready;
            this.seq = seq;
            this.fifoSizeBytes = fifoSizeBytes;
            this.numBuffers = numBuffers;
            this.avgPacketBytes = avgPacketBytes;
            this.avgIntervalUs = avgIntervalUs;
            this.bytesPerSec = bytesPerSec;
            this.transfers = transfers;
            this.emptyCount = emptyCount;
            this.shortCount = shortCount;
            this.errorCount = errorCount;
            this.minBytes = minBytes;
            this.maxBytes = maxBytes;
            this.elapsedS = elapsedS;
            this.hint = hint != null ? hint : "";
        }

        /** Average packet size as a fraction of FIFO, or NaN if unknown. */
        public float fillFraction() {
            if (!ready || fifoSizeBytes <= 0 || avgPacketBytes <= 0) {
                return Float.NaN;
            }
            return avgPacketBytes / fifoSizeBytes;
        }
    }

    public void setPipeParams(int fifoSizeBytes, int numBuffers) {
        this.fifoSizeBytes = fifoSizeBytes;
        this.numBuffers = numBuffers;
    }

    public void addSample(final RestrictedTransfer transfer) {
        addValues(transfer.actualLength(), transfer.status() == 0);
    }

    public void addSample(final RestrictedTransfer transfer, int fifoSizeBytes, int numBuffers) {
        if (!(showEnabled || printEnabled)) {
            return;
        }
        setPipeParams(fifoSizeBytes, numBuffers);
        addSample(transfer);
    }

    public void addSample(final UsbIoBuf transfer) {
        addValues(transfer.BytesTransferred, true);
    }

    /** Completed transfer with {@code nBytes} (0 = empty/ZLP). {@code ok} false counts as a USB error. */
    public void addValues(int nBytes, boolean ok) {
        if (!(showEnabled || printEnabled)) {
            return;
        }
        synchronized (this) {
            addValuesLocked(nBytes, ok);
        }
    }

    private void addValuesLocked(int nBytes, boolean ok) {
        long timeNowNs = System.nanoTime();
        if (!initialized) {
            windowStartNs = timeNowNs;
            lastPrintNs = timeNowNs;
            initialized = true;
        }
        if (!ok) {
            errorCount++;
            maybePrintLocked(timeNowNs);
            return;
        }
        windowTransfers++;
        if (nBytes <= 0) {
            emptyCount++;
        } else {
            windowBytes += nBytes;
            if (nBytes < minBytes) {
                minBytes = nBytes;
            }
            if (nBytes > maxBytes) {
                maxBytes = nBytes;
            }
            if (fifoSizeBytes > 0 && nBytes < fifoSizeBytes) {
                shortCount++;
            }
        }
        maybePrintLocked(timeNowNs);
    }

    /** Print path only when the tuning panel is not rotating the window. */
    private void maybePrintLocked(long nowNs) {
        if (!printEnabled || showEnabled || !initialized) {
            return;
        }
        if (nowNs - lastPrintNs < PRINT_INTERVAL_NS) {
            return;
        }
        lastPrintNs = nowNs;
        Snapshot s = buildSnapshotLocked(nowNs);
        lastCompleted = s;
        log.info(formatLine(s));
        resetWindowLocked();
    }

    private Snapshot buildSnapshotLocked(long nowNs) {
        int sized = Math.max(0, windowTransfers - emptyCount);
        float avgB = sized > 0 ? (float) (windowBytes / (double) sized) : 0f;
        double elapsedS = Math.max(1e-6, (nowNs - windowStartNs) * 1e-9);
        float avgDtUs = windowTransfers > 1
                ? (float) ((nowNs - windowStartNs) / 1000.0 / (windowTransfers - 1))
                : 0f;
        double bytesPerSec = windowBytes / elapsedS;
        int transfersPerSec = (int) Math.round(windowTransfers / elapsedS);
        int emptyPerSec = (int) Math.round(emptyCount / elapsedS);
        int shortPerSec = (int) Math.round(shortCount / elapsedS);
        int errorPerSec = (int) Math.round(errorCount / elapsedS);
        String hint = "";
        int min = minBytes == Integer.MAX_VALUE ? 0 : minBytes;
        if (fifoSizeBytes > 0 && avgB > 0) {
            float frac = avgB / fifoSizeBytes;
            if (frac >= 0.85f) {
                hint = "FIFO full — raise FIFO/buffers if dropping";
            } else if (frac <= 0.15f && windowTransfers > 8) {
                if (min > 0 && min == maxBytes) {
                    hint = "Each bulk IN completes at a fixed size; raising FIFO does not make completions larger";
                } else {
                    hint = "Sparse — FIFO larger than camera fill";
                }
            }
        }
        return new Snapshot(initialized, seq + 1, fifoSizeBytes, numBuffers, avgB, avgDtUs, bytesPerSec,
                transfersPerSec, emptyPerSec, shortPerSec, errorPerSec, min, maxBytes, elapsedS, hint);
    }

    private String formatLine(Snapshot s) {
        if (!s.ready) {
            return "USB IN (collecting…)";
        }
        float frac = s.fillFraction();
        String fill = Float.isNaN(frac) ? "" : String.format(" fill=%d%%", Math.round(100 * frac));
        String fifo = s.fifoSizeBytes > 0
                ? String.format("fifo=%sB x%d", engFmt.format(s.fifoSizeBytes).trim(), Math.max(1, s.numBuffers))
                : "fifo=?";
        String minMax = s.maxBytes > 0
                ? String.format(" min/max %s/%sB", engFmt.format(s.minBytes).trim(), engFmt.format(s.maxBytes).trim())
                : "";
        String hint = s.hint.isEmpty() ? "" : " [" + s.hint + "]";
        return String.format(
                "USB IN %s | pkt %sB%s dt %ss | %sB/s | n=%d empty=%d short=%d err=%d%s%s",
                fifo,
                engFmt.format(s.avgPacketBytes).trim(),
                fill,
                engFmt.format(s.avgIntervalUs * 1e-6).trim(),
                engFmt.format(s.bytesPerSec).trim(),
                s.transfers,
                s.emptyCount,
                s.shortCount,
                s.errorCount,
                minMax,
                hint);
    }

    public void reset() {
        synchronized (this) {
            initialized = false;
            seq = 0;
            resetWindowLocked();
            lastCompleted = Snapshot.EMPTY;
        }
    }

    private void resetWindowLocked() {
        windowTransfers = 0;
        windowBytes = 0;
        windowStartNs = System.nanoTime();
        minBytes = Integer.MAX_VALUE;
        maxBytes = 0;
        emptyCount = 0;
        shortCount = 0;
        errorCount = 0;
    }

    /**
     * Last window already taken or printed. Does not rotate.
     */
    public Snapshot peekSnapshot() {
        synchronized (this) {
            return lastCompleted;
        }
    }

    /**
     * Copy the current accumulation window, then start a new one. The USB
     * tuning panel calls this at 1 Hz.
     */
    public Snapshot takeSnapshot() {
        synchronized (this) {
            if (!initialized) {
                return Snapshot.EMPTY;
            }
            Snapshot s = buildSnapshotLocked(System.nanoTime());
            seq = s.seq;
            lastCompleted = s;
            if (printEnabled) {
                log.info(formatLine(s));
            }
            resetWindowLocked();
            return s;
        }
    }

    @Override
    public USBPacketStatistics.Snapshot snapshotUsbStatistics() {
        return peekSnapshot();
    }

    @Override
    public USBPacketStatistics.Snapshot takeUsbStatisticsSnapshot() {
        return takeSnapshot();
    }

    @Override
    public String toString() {
        synchronized (this) {
            return formatLine(lastCompleted);
        }
    }

    @Override
    public void setShowUsbStatistics(boolean yes) {
        boolean was = showEnabled;
        showEnabled = yes;
        if (yes && !was) {
            reset();
        }
    }

    @Override
    public void setPrintUsbStatistics(boolean yes) {
        printEnabled = yes;
        if (yes) {
            log.info("USB IN statistics logging enabled (~1 Hz). Same numbers appear in USB → USB tuning…");
        }
    }

    @Override
    public boolean isShowUsbStatistics() {
        return showEnabled;
    }

    @Override
    public boolean isPrintUsbStatistics() {
        return printEnabled;
    }
}
