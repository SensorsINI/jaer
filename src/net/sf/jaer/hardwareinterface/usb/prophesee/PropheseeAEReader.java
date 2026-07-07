package net.sf.jaer.hardwareinterface.usb.prophesee;

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
import net.sf.jaer.hardwareinterface.usb.prophesee.evt3.Evt3Parser;
import net.sf.jaer.hardwareinterface.usb.prophesee.evk4.Evk4BoardCommand;

/**
 * USB bulk reader for Prophesee EVK4 (endpoint 0x81, EVT3).
 */
public class PropheseeAEReader implements ReaderBufferControl {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final byte ENDPOINT_IN = Evk4BoardCommand.EP_EVENTS_IN;
    private static final int DEFAULT_FIFO_SIZE = 1 << 17;
    private static final int DEFAULT_NUM_BUFFERS = 32;

    private final PropheseeHardwareInterface monitor;
    private final Evt3Parser parser = new Evt3Parser();
    private USBTransferThread usbTransfer;
    private int fifoSize = JaerConstants.PREFS_ROOT_HARDWARE.getInt("Prophesee.AEReader.fifoSize", DEFAULT_FIFO_SIZE);
    private int numBuffers = JaerConstants.PREFS_ROOT_HARDWARE.getInt("Prophesee.AEReader.numBuffers", DEFAULT_NUM_BUFFERS);

    // TODO(remove): temporary EVK4 USB/event diagnostics
    private long usbTransferCount;
    private long usbBytesTotal;
    private long usbEventsParsed;
    private long lastTraceLogMs;
    private long lastOverrunLogMs;

    public PropheseeAEReader(PropheseeHardwareInterface monitor) {
        this.monitor = monitor;
    }

    public void startThread() throws HardwareInterfaceException {
        if (!monitor.isOpen()) {
            monitor.open();
        }
        if (usbTransfer != null) {
            return;
        }
        log.info("Starting Prophesee AEReader on endpoint 0x81 (EVT3)");
        usbTransfer = new USBTransferThread(
                monitor.getDeviceHandle(),
                ENDPOINT_IN,
                LibUsb.TRANSFER_TYPE_BULK,
                new ProcessAEData(),
                getNumBuffers(),
                getFifoSize());
        usbTransfer.setName("PropheseeAEReaderThread");
        usbTransfer.start();
        monitor.getReaderSupportInternal().firePropertyChange("readerStarted", false, true);
    }

    public void stopThread() {
        if (usbTransfer == null) {
            return;
        }
        log.info("Stopping Prophesee AEReader");
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

    Evt3Parser getParser() {
        return parser;
    }

    @Override
    public int getFifoSize() {
        return fifoSize;
    }

    @Override
    public void setFifoSize(int fifoSize) {
        this.fifoSize = fifoSize;
        JaerConstants.PREFS_ROOT_HARDWARE.putInt("Prophesee.AEReader.fifoSize", fifoSize);
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
        JaerConstants.PREFS_ROOT_HARDWARE.putInt("Prophesee.AEReader.numBuffers", numBuffers);
        if (usbTransfer != null) {
            usbTransfer.setBufferNumber(numBuffers);
        }
    }

    @Override
    public PropertyChangeSupport getReaderSupport() {
        return monitor.getReaderSupportInternal();
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
        if ((bytesAvailable % 2) != 0) {
            log.warning("Prophesee packet size " + bytesAvailable + " is not a multiple of 2");
        }

        final byte[] raw = new byte[bytesAvailable];
        buffer.duplicate().get(raw);

        final int[] addresses = writeBuffer.getAddresses();
        final int[] timestamps = writeBuffer.getTimestamps();
        final int startEvent = monitor.getEventCounter();
        writeBuffer.lastCaptureIndex = startEvent;

        final int maxEvents = monitor.getAEBufferSize();
        final int parsed = parser.parse(raw, raw.length, addresses, timestamps, startEvent, maxEvents);

        if (parsed < 0) {
            final int committed = maxEvents - startEvent;
            if (committed > 0) {
                monitor.setEventCounter(maxEvents);
                writeBuffer.setNumEvents(maxEvents);
                writeBuffer.lastCaptureLength = committed;
            }
            writeBuffer.overrunOccuredFlag = true;
            logOverrun(startEvent, maxEvents, committed);
            return;
        }

        monitor.setEventCounter(startEvent + parsed);
        writeBuffer.setNumEvents(monitor.getEventCounter());
        writeBuffer.lastCaptureLength = parsed;

        if (parsed > 0 || bytesAvailable > 0) {
            usbTransferCount++;
            usbBytesTotal += bytesAvailable;
            usbEventsParsed += Math.max(0, parsed);
            maybeLogTraceStats(parsed, bytesAvailable);
        }
    }

    private void logOverrun(int startEvent, int maxEvents, int committed) {
        final long now = System.currentTimeMillis();
        if (now - lastOverrunLogMs < 2000L) {
            return;
        }
        lastOverrunLogMs = now;
        log.warning(String.format(
                "Prophesee AEPacketRaw buffer overrun at event index %d (capacity %d, committed %d). "
                        + "Increase via Control > Set rendering AE buffer size (EVK4 HD needs ~500k+).",
                startEvent, maxEvents, committed));
    }

    private void maybeLogTraceStats(int parsed, int bytesAvailable) {
        if (!PropheseeTrace.ENABLED) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (now - lastTraceLogMs < 2000L) {
            return;
        }
        lastTraceLogMs = now;
        PropheseeTrace.finer(log,
                "Prophesee USB: transfers={0} bytes={1} parsedEvents={2} lastTransfer bytes={3} events={4} poolEvents={5}",
                usbTransferCount, usbBytesTotal, usbEventsParsed, bytesAvailable, parsed,
                monitor.getEventCounter());
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
                    PropheseeTrace.fine(log, "Prophesee USB transfer failed: status={0} ({1})",
                            transfer.status(), LibUsb.errorName(transfer.status()));
                    active = false;
                    monitor.markUsbDisconnected(transfer.status());
                }
            }
        }
    }
}
