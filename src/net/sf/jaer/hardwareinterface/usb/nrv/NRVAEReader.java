package net.sf.jaer.hardwareinterface.usb.nrv;

import java.beans.PropertyChangeSupport;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

import org.usb4java.LibUsb;

import li.longi.USBTransferThread.RestrictedTransfer;
import li.longi.USBTransferThread.RestrictedTransferCallback;
import li.longi.USBTransferThread.USBTransferThread;
import net.sf.jaer.JaerConstants;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.aemonitor.AEPacketRawPool;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.ReaderBufferControl;

/**
 * USB bulk reader for NRV DVS devices (endpoint 0x81).
 */
public class NRVAEReader implements ReaderBufferControl {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final byte ENDPOINT_IN = (byte) 0x81;
    private static final int DEFAULT_FIFO_SIZE = 16384;
    private static final int DEFAULT_NUM_BUFFERS = 4;
    private static final long TIMESTAMP_LOG_INTERVAL_MS = 2000L;

    private final NRVHardwareInterface monitor;
    private final S5KRC1SParser parser = new S5KRC1SParser();
    private USBTransferThread usbTransfer;
    private int fifoSize = JaerConstants.PREFS_ROOT_HARDWARE.getInt("NRV.AEReader.fifoSize", DEFAULT_FIFO_SIZE);
    private int numBuffers = JaerConstants.PREFS_ROOT_HARDWARE.getInt("NRV.AEReader.numBuffers", DEFAULT_NUM_BUFFERS);
    private boolean logTimestampStats = JaerConstants.PREFS_ROOT_HARDWARE.getBoolean("NRV.logTimestampStats", true);

    public NRVAEReader(NRVHardwareInterface monitor) {
        this.monitor = monitor;
    }

    public void startThread() throws HardwareInterfaceException {
        if (!monitor.isOpen()) {
            monitor.open();
        }
        if (usbTransfer != null) {
            return;
        }
        log.info("Starting NRV AEReader on endpoint 0x81 (timestamp stats logging="
                + logTimestampStats + ", interval=" + TIMESTAMP_LOG_INTERVAL_MS + "ms)");
        usbTransfer = new USBTransferThread(
                monitor.getDeviceHandle(),
                ENDPOINT_IN,
                LibUsb.TRANSFER_TYPE_BULK,
                new ProcessAEData(),
                getNumBuffers(),
                getFifoSize());
        usbTransfer.setName("NRVAEReaderThread");
        usbTransfer.start();
        monitor.getReaderSupportInternal().firePropertyChange("readerStarted", false, true);
    }

    public void stopThread() {
        if (usbTransfer == null) {
            return;
        }
        log.info("Stopping NRV AEReader");
        usbTransfer.interrupt();
        try {
            usbTransfer.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        usbTransfer = null;
        monitor.getReaderSupportInternal().firePropertyChange("readerStopped", false, true);
    }

    public void resetTimestamps() {
        parser.resetTimestampOrigin();
    }

    S5KRC1SParser getParser() {
        return parser;
    }

    public boolean isLogTimestampStats() {
        return logTimestampStats;
    }

    public void setLogTimestampStats(boolean logTimestampStats) {
        this.logTimestampStats = logTimestampStats;
        JaerConstants.PREFS_ROOT_HARDWARE.putBoolean("NRV.logTimestampStats", logTimestampStats);
    }

    @Override
    public int getFifoSize() {
        return fifoSize;
    }

    @Override
    public void setFifoSize(int fifoSize) {
        this.fifoSize = fifoSize;
        JaerConstants.PREFS_ROOT_HARDWARE.putInt("NRV.AEReader.fifoSize", fifoSize);
        if (usbTransfer != null) {
            usbTransfer.setBufferSize(fifoSize);
        }
    }

    @Override
    public int getNumBuffers() {
        return numBuffers;
    }

    @Override
    public void setNumBuffers(int numBuffers) {
        this.numBuffers = numBuffers;
        JaerConstants.PREFS_ROOT_HARDWARE.putInt("NRV.AEReader.numBuffers", numBuffers);
        if (usbTransfer != null) {
            usbTransfer.setBufferNumber(numBuffers);
        }
    }

    @Override
    public PropertyChangeSupport getReaderSupport() {
        return monitor.getReaderSupportInternal();
    }

    private void checkTimestampOrder(int[] timestamps, int start, int count) {
        if (count < 2) {
            return;
        }
        for (int e = start + 1; e < start + count; e++) {
            if (timestamps[e] < timestamps[e - 1]) {
                log.info(String.format(
                        "non-monotonic timestamp at event %d, ts %d -> %d",
                        e, timestamps[e - 1], timestamps[e]));
                return;
            }
        }
    }

    private void translateEvents(ByteBuffer buffer) {
        final AEPacketRawPool aePacketRawPool = monitor.getAePacketRawPool();
        final AEPacketRaw writeBuffer = aePacketRawPool.writeBuffer();
        if (writeBuffer.overrunOccuredFlag) {
            return;
        }

        final int bytesAvailable = buffer.remaining();
        if (bytesAvailable == 0) {
            return;
        }
        if ((bytesAvailable % 4) != 0) {
            log.warning("NRV packet size " + bytesAvailable + " is not a multiple of 4");
        }

        final byte[] raw = new byte[bytesAvailable];
        buffer.duplicate().get(raw);

        final int[] addresses = writeBuffer.getAddresses();
        final int[] timestamps = writeBuffer.getTimestamps();
        final int startEvent = monitor.getEventCounter();
        writeBuffer.lastCaptureIndex = startEvent;

        final int maxEvents = monitor.getAEBufferSize();
        final int parsed = parser.parse(raw, raw.length, addresses, timestamps, startEvent, maxEvents);
        monitor.accumulateUsbParseStats(parser.takeParseStats());
        monitor.maybeLogTimestampStatsTable();

        if (parsed < 0) {
            writeBuffer.overrunOccuredFlag = true;
            log.warning("NRV AEPacketRaw buffer overrun at event index " + startEvent
                    + " (capacity " + maxEvents + " events; increase via Control > Set rendering AE buffer size)");
            return;
        }

        if (parsed > 0) {
            checkTimestampOrder(timestamps, startEvent, parsed);
        }

        monitor.setEventCounter(startEvent + parsed);
        writeBuffer.setNumEvents(monitor.getEventCounter());
        writeBuffer.lastCaptureLength = parsed;
    }

    private class ProcessAEData implements RestrictedTransferCallback {

        private volatile boolean active = true;

        @Override
        public void prepareTransfer(RestrictedTransfer transfer) {
        }

        @Override
        public void processTransfer(RestrictedTransfer transfer) {
            if (!active || monitor.isUsbTransferFailed()) {
                return;
            }
            final AEPacketRawPool aePacketRawPool = monitor.getAePacketRawPool();
            synchronized (aePacketRawPool) {
                if (transfer.status() == LibUsb.TRANSFER_COMPLETED) {
                    translateEvents(transfer.buffer());
                } else if (transfer.status() != LibUsb.TRANSFER_CANCELLED) {
                    active = false;
                    monitor.markUsbDisconnected(transfer.status());
                }
            }
        }
    }
}
