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
import net.sf.jaer.util.filter.LowpassFilter;

/**
 * Tracks low-level USB IN transfer statistics for FIFO / buffer tuning.
 * When printing is enabled, logs about once per second to the jAER logger
 * (console and {@code jAER-0.log}), not {@code System.out}.
 *
 * @author tobi
 */
public class USBPacketStatistics implements HasUsbStatistics {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    /** Wall-clock throttle; USB packet rate can be kHz. */
    private static final long LOG_INTERVAL_NS = 1_000_000_000L;

    public LowpassFilter packetIntevalUsFilter = new LowpassFilter(100);
    public LowpassFilter packetSizeBytesFilter = new LowpassFilter(100);
    private long lastTimeNs;
    private boolean initialized = false;
    int nSamples = 0;
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
    private long lastLogNs;
    private int minBytes = Integer.MAX_VALUE;
    private int maxBytes;

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
        if (!ok) {
            errorCount++;
            maybeLogLocked();
            return;
        }
        if (!initialized) {
            lastTimeNs = System.nanoTime();
            windowStartNs = lastTimeNs;
            lastLogNs = lastTimeNs;
            initialized = true;
        }
        nSamples++;
        windowTransfers++;
        long timeNowNs = System.nanoTime();
        int dtUs = (int) ((timeNowNs - lastTimeNs) / 1000);
        lastTimeNs = timeNowNs;
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
            if (dtUs >= 0) {
                packetSizeBytesFilter.filter(nBytes, dtUs);
                packetIntevalUsFilter.filter(dtUs, dtUs);
            }
        }
        maybeLogLocked();
    }

    private void maybeLogLocked() {
        if (!printEnabled || !initialized) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastLogNs < LOG_INTERVAL_NS) {
            return;
        }
        lastLogNs = now;
        log.info(formatLine(now));
        resetWindowLocked();
    }

    private String formatLine(long nowNs) {
        float avgB = packetSizeBytesFilter.getValue();
        float avgDtUs = packetIntevalUsFilter.getValue();
        double elapsedS = Math.max(1e-6, (nowNs - windowStartNs) * 1e-9);
        double bytesPerSec = windowBytes / elapsedS;
        String fill = "";
        String hint = "";
        if (fifoSizeBytes > 0 && avgB > 0) {
            float frac = avgB / fifoSizeBytes;
            fill = String.format(" fill=%d%%", Math.round(100 * frac));
            if (frac >= 0.85f) {
                hint = " [full — raise FIFO/buffers if dropping]";
            } else if (frac <= 0.15f && windowTransfers > 8) {
                hint = " [sparse — FIFO larger than the camera is filling]";
            }
        }
        String fifo = fifoSizeBytes > 0
                ? String.format("fifo=%sB x%d", engFmt.format(fifoSizeBytes).trim(), Math.max(1, numBuffers))
                : "fifo=?";
        String minMax = maxBytes > 0
                ? String.format(" min/max %s/%sB", engFmt.format(minBytes).trim(), engFmt.format(maxBytes).trim())
                : "";
        return String.format(
                "USB IN %s | pkt %sB%s dt %ss | %sB/s | n=%d empty=%d short=%d err=%d%s%s",
                fifo,
                engFmt.format(avgB).trim(),
                fill,
                engFmt.format(avgDtUs * 1e-6).trim(),
                engFmt.format(bytesPerSec).trim(),
                windowTransfers,
                emptyCount,
                shortCount,
                errorCount,
                minMax,
                hint);
    }

    public void reset() {
        synchronized (this) {
            nSamples = 0;
            initialized = false;
            packetIntevalUsFilter.reset();
            packetSizeBytesFilter.reset();
            resetWindowLocked();
            emptyCount = 0;
            shortCount = 0;
            errorCount = 0;
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

    @Override
    public String toString() {
        return formatLine(System.nanoTime());
    }

    @Override
    public void setShowUsbStatistics(boolean yes) {
        showEnabled = yes;
        if (yes) {
            reset();
        }
    }

    @Override
    public void setPrintUsbStatistics(boolean yes) {
        printEnabled = yes;
        if (yes) {
            reset();
            log.info("USB IN statistics logging enabled (~1 Hz). Tune FIFO/buffers from USB → USB tuning…");
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
