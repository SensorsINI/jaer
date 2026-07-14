package nrv.usb;

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
import net.sf.jaer.hardwareinterface.usb.UsbPipelineBench;
import net.sf.jaer.hardwareinterface.usb.UsbReaderBufferSettings;

/**
 * USB bulk reader for NRV DVS devices (endpoint 0x81).
 *
 * @see https://nrv.kr/
 */
public class NRVAEReader implements ReaderBufferControl {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final byte ENDPOINT_IN = (byte) 0x81;
    private static final int DEFAULT_FIFO_SIZE = 1 << 17;
    private static final int DEFAULT_NUM_BUFFERS = 16;
    private static final String PREF_FIFO_SIZE = "NRV.AEReader.fifoSize";
    private static final String PREF_NUM_BUFFERS = "NRV.AEReader.numBuffers";
    private static final long OVERRUN_LOG_INTERVAL_MS = 2000L;
    private static final long STOP_JOIN_TIMEOUT_MS = 3000L;

    private final NRVHardwareInterface monitor;
    private final S5KRC1SParser parser = new S5KRC1SParser();
    private USBTransferThread usbTransfer;
    private int fifoSize = UsbReaderBufferSettings.loadFifoSize(
            JaerConstants.PREFS_ROOT_HARDWARE, PREF_FIFO_SIZE, DEFAULT_FIFO_SIZE, log, "NRV");
    private int numBuffers = UsbReaderBufferSettings.loadNumBuffers(
            JaerConstants.PREFS_ROOT_HARDWARE, PREF_NUM_BUFFERS, DEFAULT_NUM_BUFFERS, fifoSize, log, "NRV");
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
        synchronized (monitor.getAePacketRawPool()) {
            monitor.getAePacketRawPool().allocateMemory();
        }
        parser.reset();
        clearEndpointHalt(monitor.getDeviceHandle());
        log.info("Starting NRV AEReader on endpoint 0x81 (fifo=" + getFifoSize()
                + " buffers=" + getNumBuffers()
                + ", timestampOrderTrace=" + NRVTrace.TIMESTAMP_ORDER_ENABLED
                + ", timingTrace=" + NRVTrace.TIMING_ENABLED
                + (NRVTrace.TIMING_ENABLED ? ", timingIntervalMs=" + NRVTrace.TIMING_INTERVAL_MS : "")
                + ", pipelineBench=" + UsbPipelineBench.ENABLED
                + (UsbPipelineBench.ENABLED && System.getProperty("jaer.usb.trace.file") != null
                        ? ", traceFile=" + System.getProperty("jaer.usb.trace.file") : "")
                + ")");
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
            usbTransfer.join(STOP_JOIN_TIMEOUT_MS);
            if (usbTransfer.isAlive()) {
                log.warning("NRV AEReader thread did not stop within " + STOP_JOIN_TIMEOUT_MS + " ms");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        usbTransfer = null;
        monitor.getReaderSupportInternal().firePropertyChange("readerStopped", false, true);
    }

    public void resetTimestamps() {
        parser.resetTimestampOrigin();
    }

    /** Re-base ref/full timestamp tracking after live timing-register I2C writes. */
    void resyncTimingAfterRegisterChange(int regAddr, String reason) {
        parser.resyncTimingState(regAddr, reason);
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
        this.fifoSize = UsbReaderBufferSettings.sanitizeFifoSize(
                JaerConstants.PREFS_ROOT_HARDWARE, PREF_FIFO_SIZE, fifoSize, DEFAULT_FIFO_SIZE, log, "NRV");
        this.numBuffers = UsbReaderBufferSettings.sanitizeNumBuffers(
                JaerConstants.PREFS_ROOT_HARDWARE, PREF_NUM_BUFFERS, numBuffers, this.fifoSize,
                DEFAULT_NUM_BUFFERS, log, "NRV");
        JaerConstants.PREFS_ROOT_HARDWARE.putInt(PREF_FIFO_SIZE, this.fifoSize);
        JaerConstants.PREFS_ROOT_HARDWARE.putInt(PREF_NUM_BUFFERS, numBuffers);
        if (usbTransfer != null) {
            usbTransfer.setBufferSize(this.fifoSize);
            usbTransfer.setBufferNumber(numBuffers);
        }
    }

    @Override
    public void setNumBuffers(int numBuffers) {
        this.numBuffers = UsbReaderBufferSettings.sanitizeNumBuffers(
                JaerConstants.PREFS_ROOT_HARDWARE, PREF_NUM_BUFFERS, numBuffers, fifoSize,
                DEFAULT_NUM_BUFFERS, log, "NRV");
        JaerConstants.PREFS_ROOT_HARDWARE.putInt(PREF_NUM_BUFFERS, this.numBuffers);
        if (usbTransfer != null) {
            usbTransfer.setBufferNumber(this.numBuffers);
        }
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
    private ParsedChunk parseUsbChunk(ByteBuffer buffer, UsbPipelineBench.Sample sample) {
        final int bytesAvailable = buffer.remaining();
        if (bytesAvailable == 0) {
            return ParsedChunk.EMPTY;
        }
        if ((bytesAvailable % 4) != 0) {
            log.warning("NRV packet size " + bytesAvailable + " is not a multiple of 4");
        }
        if (sample != null) {
            sample.usbBytes = bytesAvailable;
        }

        final int parseLimit;
        final long limitLockStart = sample != null ? System.nanoTime() : 0;
        synchronized (monitor.getAePacketRawPool()) {
            if (sample != null) {
                sample.limitLockNs = System.nanoTime() - limitLockStart;
            }
            if (monitor.getAePacketRawPool().writeBuffer().overrunOccuredFlag) {
                return ParsedChunk.EMPTY;
            }
            parseLimit = monitor.getAEBufferSize() - monitor.getEventCounter();
            if (parseLimit <= 0) {
                return ParsedChunk.OVERFLOW;
            }
        }

        final long copyStart = sample != null ? System.nanoTime() : 0;
        if (parseScratch == null || parseScratch.length < bytesAvailable) {
            parseScratch = new byte[bytesAvailable];
        }
        buffer.get(parseScratch, 0, bytesAvailable);
        if (sample != null) {
            sample.byteCopyNs = System.nanoTime() - copyStart;
        }
        ensureStaging(parseLimit);

        final long parseStart = sample != null ? System.nanoTime() : 0;
        final int parsed = parser.parse(parseScratch, bytesAvailable,
                stagingAddresses, stagingTimestamps, 0, parseLimit);
        if (sample != null) {
            sample.parseNs = System.nanoTime() - parseStart;
            sample.eventsParsed = Math.max(0, parsed);
        }

        if (parsed > 0) {
            checkTimestampOrder(stagingTimestamps, 0, parsed);
            if (NRVTrace.TIMING_ENABLED) {
                int minTs = stagingTimestamps[0];
                int maxTs = stagingTimestamps[0];
                for (int e = 1; e < parsed; e++) {
                    minTs = Math.min(minTs, stagingTimestamps[e]);
                    maxTs = Math.max(maxTs, stagingTimestamps[e]);
                }
                parser.noteChunkTimestampSpanUs(maxTs - minTs);
            }
        }
        return new ParsedChunk(parsed, parseLimit);
    }

    private void commitParsedChunk(ParsedChunk chunk, UsbPipelineBench.Sample sample) {
        if (chunk == ParsedChunk.EMPTY) {
            return;
        }
        final long commitStart = sample != null ? System.nanoTime() : 0;
        long arrayCopyNs = 0;
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
                    ensureWriteBufferCapacity(writeBuffer, maxEvents, startEvent, committed);
                    final long acStart = sample != null ? System.nanoTime() : 0;
                    System.arraycopy(stagingAddresses, 0, writeBuffer.getAddresses(), startEvent, committed);
                    System.arraycopy(stagingTimestamps, 0, writeBuffer.getTimestamps(), startEvent, committed);
                    if (sample != null) {
                        arrayCopyNs += System.nanoTime() - acStart;
                    }
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
                ensureWriteBufferCapacity(writeBuffer, maxEvents, startEvent, toCopy);
                final long acStart = sample != null ? System.nanoTime() : 0;
                System.arraycopy(stagingAddresses, 0, writeBuffer.getAddresses(), startEvent, toCopy);
                System.arraycopy(stagingTimestamps, 0, writeBuffer.getTimestamps(), startEvent, toCopy);
                if (sample != null) {
                    arrayCopyNs += System.nanoTime() - acStart;
                }
            }
            monitor.setEventCounter(startEvent + toCopy);
            writeBuffer.setNumEvents(monitor.getEventCounter());
            writeBuffer.lastCaptureLength = toCopy;
        }
        if (sample != null) {
            sample.commitLockNs = System.nanoTime() - commitStart;
            sample.arrayCopyNs = arrayCopyNs;
        }
    }

    private static void ensureWriteBufferCapacity(AEPacketRaw writeBuffer, int maxEvents, int startEvent, int count) {
        final int[] destAddr = writeBuffer.getAddresses();
        final int[] destTs = writeBuffer.getTimestamps();
        if (destAddr == null || destAddr.length < startEvent + count
                || destTs == null || destTs.length < startEvent + count) {
            writeBuffer.ensureCapacity(maxEvents);
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
                final UsbPipelineBench.Sample sample = UsbPipelineBench.newSample("NRV");
                final long totalStart = sample != null ? System.nanoTime() : 0;
                final ParsedChunk chunk = parseUsbChunk(transfer.buffer(), sample);
                commitParsedChunk(chunk, sample);
                if (sample != null) {
                    sample.totalNs = System.nanoTime() - totalStart;
                    UsbPipelineBench.record(sample);
                }
            } else if (transfer.status() != LibUsb.TRANSFER_CANCELLED) {
                active = false;
                monitor.markUsbDisconnected(transfer.status());
            }
        }
    }
}
