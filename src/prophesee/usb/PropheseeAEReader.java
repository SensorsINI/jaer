package prophesee.usb;

import java.beans.PropertyChangeSupport;
import java.nio.ByteBuffer;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

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
import net.sf.jaer.util.TimestampSpread;
import prophesee.usb.evt3.Evt3Parser;
import prophesee.usb.evk4.Evk4BoardCommand;

/**
 * USB bulk reader for Prophesee EVK4 (endpoint 0x81, EVT3).
 * Uses pipelined async bulk transfer ({@link USBTransferThread}) like NRV.
 *
 * @see https://www.prophesee.ai/
 */
public class PropheseeAEReader implements ReaderBufferControl {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final byte ENDPOINT_IN = Evk4BoardCommand.EP_EVENTS_IN;
    private static final int DEFAULT_FIFO_SIZE = 131072; // 128 KiB — tuned for low display latency
    private static final int DEFAULT_NUM_BUFFERS = 16;
    private static final String PREF_FIFO_SIZE = "Prophesee.AEReader.fifoSize";
    private static final String PREF_NUM_BUFFERS = "Prophesee.AEReader.numBuffers";
    /** Bump when default fifo/buffer tuning changes; migrates stored hardware prefs once. */
    private static final int USB_READER_PREFS_VERSION = 1;

    static {
        migrateUsbReaderPrefs();
    }

    private static void migrateUsbReaderPrefs() {
        final Preferences hw = JaerConstants.PREFS_ROOT_HARDWARE;
        if (hw.getInt("Prophesee.AEReader.prefsVersion", 0) >= USB_READER_PREFS_VERSION) {
            UsbReaderBufferSettings.loadFifoSize(hw, PREF_FIFO_SIZE, DEFAULT_FIFO_SIZE, log, "Prophesee");
            UsbReaderBufferSettings.loadNumBuffers(hw, PREF_NUM_BUFFERS, DEFAULT_NUM_BUFFERS,
                    hw.getInt(PREF_FIFO_SIZE, DEFAULT_FIFO_SIZE), log, "Prophesee");
            return;
        }
        hw.putInt(PREF_FIFO_SIZE, DEFAULT_FIFO_SIZE);
        hw.putInt(PREF_NUM_BUFFERS, DEFAULT_NUM_BUFFERS);
        hw.putInt("Prophesee.AEReader.prefsVersion", USB_READER_PREFS_VERSION);
    }

    private final PropheseeHardwareInterface monitor;
    private final Evt3Parser parser = new Evt3Parser();
    private USBTransferThread usbTransfer;
    private volatile boolean readerActive;
    private int fifoSize = UsbReaderBufferSettings.loadFifoSize(
            JaerConstants.PREFS_ROOT_HARDWARE, PREF_FIFO_SIZE, DEFAULT_FIFO_SIZE, log, "Prophesee");
    private int numBuffers = UsbReaderBufferSettings.loadNumBuffers(
            JaerConstants.PREFS_ROOT_HARDWARE, PREF_NUM_BUFFERS, DEFAULT_NUM_BUFFERS, fifoSize, log, "Prophesee");

    private byte[] parseScratch;
    private int[] stagingAddresses;
    private int[] stagingTimestamps;

    private long usbTransferCount;
    private long usbBytesTotal;
    private long usbEventsParsed;
    private long lastTraceLogMs;
    private long lastOverrunLogMs;
    private long lastTimestampTraceLogMs;

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
        parser.reset();
        usbTransferCount = 0;
        usbBytesTotal = 0;
        usbEventsParsed = 0;
        Evk4BoardCommand.clearEventEndpointHalt(monitor.getDeviceHandle());
        log.info("Starting Prophesee AEReader on endpoint 0x81 (EVT3, async bulk, fifo="
                + getFifoSize() + " buffers=" + getNumBuffers()
                + ", pipelineBench=" + UsbPipelineBench.ENABLED + ")");
        readerActive = true;
        usbTransfer = new USBTransferThread(
                monitor.getDeviceHandle(),
                ENDPOINT_IN,
                LibUsb.TRANSFER_TYPE_BULK,
                new ProcessAEData(),
                getNumBuffers(),
                getFifoSize());
        usbTransfer.setName("PropheseeAEReader");
        usbTransfer.start();
        monitor.getReaderSupportInternal().firePropertyChange("readerStarted", false, true);
    }

    void prepareForStop() {
        readerActive = false;
        if (usbTransfer != null) {
            usbTransfer.interrupt();
        }
    }

    void finishStop() {
        if (usbTransfer == null) {
            return;
        }
        try {
            usbTransfer.join(3000L);
            if (usbTransfer.isAlive()) {
                log.warning("Prophesee AEReader thread did not stop within 3s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        usbTransfer = null;
        monitor.getReaderSupportInternal().firePropertyChange("readerStopped", false, true);
    }

    public void stopThread() {
        if (usbTransfer == null) {
            return;
        }
        log.info("Stopping Prophesee AEReader");
        prepareForStop();
        finishStop();
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
     * {@link Evt3Parser} state is only touched on the USB transfer thread.
     */
    private ParsedChunk parseUsbChunk(ByteBuffer buffer, UsbPipelineBench.Sample sample) {
        final int bytesAvailable = buffer.remaining();
        if (bytesAvailable == 0) {
            return ParsedChunk.EMPTY;
        }
        if ((bytesAvailable % 2) != 0) {
            log.warning("Prophesee packet size " + bytesAvailable + " is not a multiple of 2");
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

        if (usbTransferCount == 0 && parsed >= 0) {
            log.info(String.format(
                    "Prophesee first USB event packet: %d bytes, %d events parsed",
                    bytesAvailable, Math.max(0, parsed)));
            if (bytesAvailable > 0 && parsed == 0) {
                log.warning("Prophesee EVT3: received bytes but parsed 0 events (possible stream desync)");
            }
        }
        if (parsed >= 0) {
            usbTransferCount++;
            usbBytesTotal += bytesAvailable;
            usbEventsParsed += parsed;
            maybeLogTraceStats(parsed, bytesAvailable);
            maybeLogTimestampTrace(stagingTimestamps, 0, parsed);
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
        this.fifoSize = UsbReaderBufferSettings.sanitizeFifoSize(
                JaerConstants.PREFS_ROOT_HARDWARE, PREF_FIFO_SIZE, fifoSize, DEFAULT_FIFO_SIZE, log, "Prophesee");
        this.numBuffers = UsbReaderBufferSettings.sanitizeNumBuffers(
                JaerConstants.PREFS_ROOT_HARDWARE, PREF_NUM_BUFFERS, numBuffers, this.fifoSize,
                DEFAULT_NUM_BUFFERS, log, "Prophesee");
        JaerConstants.PREFS_ROOT_HARDWARE.putInt(PREF_FIFO_SIZE, this.fifoSize);
        JaerConstants.PREFS_ROOT_HARDWARE.putInt(PREF_NUM_BUFFERS, numBuffers);
        if (usbTransfer != null) {
            usbTransfer.setBufferSize(this.fifoSize);
            usbTransfer.setBufferNumber(numBuffers);
        }
    }

    @Override
    public int getNumBuffers() {
        return numBuffers;
    }

    @Override
    public void setNumBuffers(int numBuffers) {
        this.numBuffers = UsbReaderBufferSettings.sanitizeNumBuffers(
                JaerConstants.PREFS_ROOT_HARDWARE, PREF_NUM_BUFFERS, numBuffers, fifoSize,
                DEFAULT_NUM_BUFFERS, log, "Prophesee");
        JaerConstants.PREFS_ROOT_HARDWARE.putInt(PREF_NUM_BUFFERS, this.numBuffers);
        if (usbTransfer != null) {
            usbTransfer.setBufferNumber(this.numBuffers);
        }
    }

    @Override
    public PropertyChangeSupport getReaderSupport() {
        return monitor.getReaderSupportInternal();
    }

    private void maybeLogTimestampTrace(int[] timestamps, int start, int count) {
        if (!PropheseeTrace.TIMESTAMP_ENABLED || count <= 0) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (now - lastTimestampTraceLogMs < 2000L) {
            return;
        }
        lastTimestampTraceLogMs = now;
        final TimestampSpread spread = TimestampSpread.compute(timestamps, start, count);
        PropheseeTrace.fine(log,
                "EVT3 ts: tUs={0} origin={1} overflows={2} lsb={3} msb={4} rejected={5} "
                        + "vect12={6} othersSkip={7} batch events={8} span={9}us unique={10} "
                        + "steps=[{11},{12}]us first={13} last={14}",
                parser.getTUs(), parser.getTimestampOriginUs(), parser.getOverflows(),
                parser.getTraceLsbUpdates(), parser.getTraceMsbUpdates(), parser.getTraceBackwardRejections(),
                parser.getTraceVect12Triples(), parser.getTraceOthersSkipped(),
                count, spread.spanUs, spread.uniqueTs, spread.minStepUs, spread.maxStepUs,
                timestamps[start], timestamps[start + count - 1]);
        parser.clearTraceCounters();
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
            if (!active || !readerActive || monitor.isUsbTransferFailed()) {
                return;
            }
            if (transfer.status() == LibUsb.TRANSFER_COMPLETED) {
                final UsbPipelineBench.Sample sample = UsbPipelineBench.newSample("EVK4");
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
