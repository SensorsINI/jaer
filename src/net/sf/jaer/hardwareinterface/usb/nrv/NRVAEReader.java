package net.sf.jaer.hardwareinterface.usb.nrv;

import java.beans.PropertyChangeSupport;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

import org.usb4java.DeviceHandle;
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
 *
 * @see https://nrv.kr/
 */
public class NRVAEReader implements ReaderBufferControl {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final byte ENDPOINT_IN = (byte) 0x81;
    private static final int DEFAULT_FIFO_SIZE = 1 << 17;
    private static final int MIN_FIFO_SIZE = 4096;
    /** Cap avoids OOM from Control-menu FIFO doubling or corrupted prefs. */
    private static final int MAX_FIFO_SIZE = 1 << 22;
    private static final int DEFAULT_NUM_BUFFERS = 16;
    private static final int MAX_NUM_BUFFERS = 32;
    private static final long MAX_TOTAL_USB_BUFFER_BYTES = 64L * 1024L * 1024L;
    private static final long OVERRUN_LOG_INTERVAL_MS = 2000L;

    private final NRVHardwareInterface monitor;
    private final S5KRC1SParser parser = new S5KRC1SParser();
    private USBTransferThread usbTransfer;
    private int fifoSize = sanitizeFifoSize(
            JaerConstants.PREFS_ROOT_HARDWARE.getInt("NRV.AEReader.fifoSize", DEFAULT_FIFO_SIZE));
    private int numBuffers = sanitizeNumBuffers(
            JaerConstants.PREFS_ROOT_HARDWARE.getInt("NRV.AEReader.numBuffers", DEFAULT_NUM_BUFFERS),
            fifoSize);
    private byte[] parseScratch;
    private int[] stagingAddresses;
    private int[] stagingTimestamps;
    private long lastOverrunLogMs;

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
        parser.reset();
        clearEndpointHalt(monitor.getDeviceHandle());
        log.info("Starting NRV AEReader on endpoint 0x81 (fifo=" + getFifoSize()
                + " buffers=" + getNumBuffers()
                + ", timestampOrderTrace=" + NRVTrace.TIMESTAMP_ORDER_ENABLED + ")");
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

    private static void clearEndpointHalt(DeviceHandle handle) {
        final int status = LibUsb.clearHalt(handle, ENDPOINT_IN);
        if (status != LibUsb.SUCCESS && status != LibUsb.ERROR_NOT_FOUND) {
            log.fine("NRV clearHalt 0x81: " + LibUsb.errorName(status));
        }
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

    @Override
    public int getFifoSize() {
        return fifoSize;
    }

    @Override
    public void setFifoSize(int fifoSize) {
        this.fifoSize = sanitizeFifoSize(fifoSize);
        this.numBuffers = sanitizeNumBuffers(numBuffers, this.fifoSize);
        JaerConstants.PREFS_ROOT_HARDWARE.putInt("NRV.AEReader.fifoSize", this.fifoSize);
        JaerConstants.PREFS_ROOT_HARDWARE.putInt("NRV.AEReader.numBuffers", numBuffers);
        if (usbTransfer != null) {
            usbTransfer.setBufferSize(this.fifoSize);
            usbTransfer.setBufferNumber(numBuffers);
        }
    }

    @Override
    public void setNumBuffers(int numBuffers) {
        this.numBuffers = sanitizeNumBuffers(numBuffers, fifoSize);
        JaerConstants.PREFS_ROOT_HARDWARE.putInt("NRV.AEReader.numBuffers", this.numBuffers);
        if (usbTransfer != null) {
            usbTransfer.setBufferNumber(this.numBuffers);
        }
    }

    private static int sanitizeFifoSize(int fifoSize) {
        if (fifoSize >= MIN_FIFO_SIZE && fifoSize <= MAX_FIFO_SIZE) {
            return fifoSize;
        }
        log.warning(String.format(
                "Invalid NRV USB FIFO size %d bytes; resetting to %d (valid range %d..%d). "
                        + "This can happen after repeatedly increasing FIFO size in Control menu.",
                fifoSize, DEFAULT_FIFO_SIZE, MIN_FIFO_SIZE, MAX_FIFO_SIZE));
        JaerConstants.PREFS_ROOT_HARDWARE.putInt("NRV.AEReader.fifoSize", DEFAULT_FIFO_SIZE);
        return DEFAULT_FIFO_SIZE;
    }

    private static int sanitizeNumBuffers(int numBuffers, int fifoSize) {
        int n = numBuffers;
        if (n < 1) {
            n = 1;
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
                        "NRV USB buffer count reduced to %d so fifo (%d) x buffers stays under %d MB",
                        n, safeFifo, MAX_TOTAL_USB_BUFFER_BYTES / (1024 * 1024)));
            }
        }
        return n;
    }

    @Override
    public int getNumBuffers() {
        return numBuffers;
    }

    @Override
    public PropertyChangeSupport getReaderSupport() {
        return monitor.getReaderSupportInternal();
    }

    private void checkTimestampOrder(int[] timestamps, int start, int count) {
        if (!NRVTrace.TIMESTAMP_ORDER_ENABLED || count < 2) {
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

    private void ensureStaging(int eventCapacity) {
        if (eventCapacity <= 0) {
            return;
        }
        if (stagingAddresses == null || stagingAddresses.length < eventCapacity) {
            stagingAddresses = new int[eventCapacity];
            stagingTimestamps = new int[eventCapacity];
        }
    }

    /**
     * Copies USB bytes and parses into thread-local staging (outside {@link AEPacketRawPool} lock).
     * {@link S5KRC1SParser} state is only touched on the USB transfer thread.
     */
    private ParsedChunk parseUsbChunk(ByteBuffer buffer) {
        final int bytesAvailable = buffer.remaining();
        if (bytesAvailable == 0) {
            return ParsedChunk.EMPTY;
        }
        if ((bytesAvailable % 4) != 0) {
            log.warning("NRV packet size " + bytesAvailable + " is not a multiple of 4");
        }

        final int parseLimit;
        synchronized (monitor.getAePacketRawPool()) {
            if (monitor.getAePacketRawPool().writeBuffer().overrunOccuredFlag) {
                return ParsedChunk.EMPTY;
            }
            parseLimit = monitor.getAEBufferSize() - monitor.getEventCounter();
            if (parseLimit <= 0) {
                return ParsedChunk.OVERFLOW;
            }
        }

        if (parseScratch == null || parseScratch.length < bytesAvailable) {
            parseScratch = new byte[bytesAvailable];
        }
        buffer.duplicate().get(parseScratch, 0, bytesAvailable);
        ensureStaging(parseLimit);

        final int parsed = parser.parse(parseScratch, bytesAvailable,
                stagingAddresses, stagingTimestamps, 0, parseLimit);

        if (parsed > 0) {
            checkTimestampOrder(stagingTimestamps, 0, parsed);
        }
        return new ParsedChunk(parsed, parseLimit);
    }

    private void commitParsedChunk(ParsedChunk chunk) {
        if (chunk == ParsedChunk.EMPTY) {
            return;
        }
        synchronized (monitor.getAePacketRawPool()) {
            final AEPacketRawPool aePacketRawPool = monitor.getAePacketRawPool();
            final AEPacketRaw writeBuffer = aePacketRawPool.writeBuffer();
            if (writeBuffer.overrunOccuredFlag) {
                return;
            }

            final int maxEvents = monitor.getAEBufferSize();
            final int startEvent = monitor.getEventCounter();
            writeBuffer.lastCaptureIndex = startEvent;
            final int remaining = maxEvents - startEvent;

            if (chunk == ParsedChunk.OVERFLOW || remaining <= 0) {
                writeBuffer.overrunOccuredFlag = true;
                logOverrun(startEvent, maxEvents, 0);
                return;
            }

            if (chunk.parsed < 0) {
                final int committed = Math.min(chunk.parseLimit, remaining);
                if (committed > 0) {
                    System.arraycopy(stagingAddresses, 0, writeBuffer.getAddresses(), startEvent, committed);
                    System.arraycopy(stagingTimestamps, 0, writeBuffer.getTimestamps(), startEvent, committed);
                    monitor.setEventCounter(startEvent + committed);
                    writeBuffer.setNumEvents(monitor.getEventCounter());
                    writeBuffer.lastCaptureLength = committed;
                }
                writeBuffer.overrunOccuredFlag = true;
                logOverrun(startEvent, maxEvents, committed);
                return;
            }

            final int toCopy = Math.min(chunk.parsed, remaining);
            if (toCopy > 0) {
                System.arraycopy(stagingAddresses, 0, writeBuffer.getAddresses(), startEvent, toCopy);
                System.arraycopy(stagingTimestamps, 0, writeBuffer.getTimestamps(), startEvent, toCopy);
            }
            monitor.setEventCounter(startEvent + toCopy);
            writeBuffer.setNumEvents(monitor.getEventCounter());
            writeBuffer.lastCaptureLength = toCopy;
        }
    }

    private static final class ParsedChunk {
        static final ParsedChunk EMPTY = new ParsedChunk(0, 0);
        static final ParsedChunk OVERFLOW = new ParsedChunk(-1, 0);

        final int parsed;
        final int parseLimit;

        ParsedChunk(int parsed, int parseLimit) {
            this.parsed = parsed;
            this.parseLimit = parseLimit;
        }
    }

    private void logOverrun(int startEvent, int maxEvents, int committed) {
        final long now = System.currentTimeMillis();
        if (now - lastOverrunLogMs < OVERRUN_LOG_INTERVAL_MS) {
            return;
        }
        lastOverrunLogMs = now;
        log.warning(String.format(
                "NRV AEPacketRaw buffer overrun at event index %d (capacity %d, committed %d). "
                        + "Increase via Control > Set rendering AE buffer size (NRV needs ~500k+).",
                startEvent, maxEvents, committed));
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
            if (transfer.status() == LibUsb.TRANSFER_COMPLETED) {
                commitParsedChunk(parseUsbChunk(transfer.buffer()));
            } else if (transfer.status() != LibUsb.TRANSFER_CANCELLED) {
                active = false;
                monitor.markUsbDisconnected(transfer.status());
            }
        }
    }
}
