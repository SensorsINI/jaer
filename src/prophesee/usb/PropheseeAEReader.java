package prophesee.usb;

import java.beans.PropertyChangeSupport;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.usb4java.LibUsb;

import li.longi.USBTransferThread.RestrictedTransfer;
import li.longi.USBTransferThread.RestrictedTransferCallback;
import li.longi.USBTransferThread.USBTransferThread;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.UsbPipelineBench;
import net.sf.jaer.hardwareinterface.usb.UsbPolarityBundleBuilder;
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
public class PropheseeAEReader {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final byte ENDPOINT_IN = Evk4BoardCommand.EP_EVENTS_IN;

    private static final int XMASK = 0x7FF;
    private static final int YMASK = 0x7FF << 11;
    private static final int TYPEMASK = 1 << 22;

    private final PropheseeHardwareInterface monitor;
    private final Evt3Parser parser = new Evt3Parser();
    private final UsbPolarityBundleBuilder polarityBuilder = new UsbPolarityBundleBuilder();
    private USBTransferThread usbTransfer;
    private volatile boolean readerActive;
    private int fifoSize;
    private int numBuffers;

    private byte[] parseScratch;
    private int[] stagingAddresses;
    private int[] stagingTimestamps;
    /** Reused parse result to avoid per-USB-transfer allocation. */
    private final ParsedChunk parseScratchChunk = new ParsedChunk(0, 0);

    private long usbTransferCount;
    private long usbBytesTotal;
    private long usbEventsParsed;
    private long lastTraceLogMs;
    private long lastOverrunLogMs;
    private long lastTimestampTraceLogMs;

    public PropheseeAEReader(PropheseeHardwareInterface monitor) {
        this.monitor = monitor;
        syncUsbBufferSettings(monitor.getFifoSize(), monitor.getNumBuffers());
    }

    void syncUsbBufferSettings(int fifoSize, int numBuffers) {
        this.fifoSize = fifoSize;
        this.numBuffers = numBuffers;
    }

    /**
     * {@link USBTransferThread} cannot resize in-flight bulk transfers (LIBUSB_ERROR_IO
     * on EVK4). Stop capture, then start a new transfer thread with the new sizes.
     */
    void applyBufferSettingsAndRestart(int fifoSize, int numBuffers) {
        this.fifoSize = fifoSize;
        this.numBuffers = numBuffers;
        if (usbTransfer == null) {
            return;
        }
        log.info("Restarting Prophesee AEReader to apply USB fifo=" + fifoSize
                + " buffers=" + numBuffers);
        // Do not ISSD-stop under load: control EP times out while bulk IN is saturated.
        stopThread();
        try {
            startThreadInternal(false);
        } catch (HardwareInterfaceException e) {
            log.warning("Failed to restart Prophesee AEReader after USB buffer change: " + e);
        }
    }

    PropertyChangeSupport getReaderSupport() {
        return monitor.getReaderSupportInternal();
    }

    int getFifoSize() {
        return fifoSize;
    }

    int getNumBuffers() {
        return numBuffers;
    }

    public void startThread() throws HardwareInterfaceException {
        startThreadInternal(true);
    }

    private void startThreadInternal(boolean resetParser) throws HardwareInterfaceException {
        if (!monitor.isOpen()) {
            monitor.open();
        }
        if (usbTransfer != null && !usbTransfer.isAlive()) {
            log.warning("Prophesee AEReader thread died; starting a new one");
            usbTransfer = null;
        }
        if (usbTransfer != null) {
            return;
        }
        syncUsbBufferSettings(monitor.getFifoSize(), monitor.getNumBuffers());
        if (resetParser) {
            parser.reset();
        }
        usbTransferCount = 0;
        usbBytesTotal = 0;
        usbEventsParsed = 0;
        ensureParseBuffers();
        final int cap = displayEventCap();
        if (monitor.getAEBufferSize() > cap) {
            log.info("Prophesee live display capped at " + cap
                    + " events/packet (AE buffer " + monitor.getAEBufferSize()
                    + "). Further events keep EVT3 timebase but are not rendered.");
        }

        HardwareInterfaceException lastFailure = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            Evk4BoardCommand.flushEventEndpoint(monitor.getDeviceHandle(), 50L);
            Evk4BoardCommand.clearEventEndpointHalt(monitor.getDeviceHandle());
            log.info("Starting Prophesee AEReader on endpoint 0x81 (EVT3, async bulk, fifo="
                    + getFifoSize() + " buffers=" + getNumBuffers()
                    + ", pipelineBench=" + UsbPipelineBench.ENABLED
                    + (attempt > 0 ? ", retry=" + attempt : "") + ")");
            readerActive = true;
            final AtomicReference<Throwable> startError = new AtomicReference<>();
            usbTransfer = new USBTransferThread(
                    monitor.getDeviceHandle(),
                    ENDPOINT_IN,
                    LibUsb.TRANSFER_TYPE_BULK,
                    new ProcessAEData(),
                    getNumBuffers(),
                    getFifoSize());
            usbTransfer.setName("PropheseeAEReader");
            usbTransfer.setUncaughtExceptionHandler((t, ex) -> {
                startError.set(ex);
                log.log(Level.WARNING, "Prophesee AEReader died: " + ex.getMessage(), ex);
            });
            usbTransfer.start();
            try {
                usbTransfer.join(400L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (usbTransfer.isAlive()) {
                monitor.getReaderSupportInternal().firePropertyChange("readerStarted", false, true);
                return;
            }
            usbTransfer = null;
            readerActive = false;
            final Throwable err = startError.get();
            lastFailure = new HardwareInterfaceException("USBTransferThread failed to start (fifo="
                    + getFifoSize() + " buffers=" + getNumBuffers() + "): "
                    + (err != null ? err.getMessage() : "thread exited"));
            final int smaller = Math.max(UsbReaderBufferSettings.MIN_FIFO_SIZE, getFifoSize() / 2);
            if (smaller >= getFifoSize()) {
                break;
            }
            log.warning("Reducing Prophesee USB FIFO " + getFifoSize() + " -> " + smaller
                    + " after LIBUSB submit failure");
            monitor.persistUsbFifoSize(smaller);
            syncUsbBufferSettings(monitor.getFifoSize(), monitor.getNumBuffers());
        }
        throw lastFailure != null ? lastFailure
                : new HardwareInterfaceException("USBTransferThread failed to start");
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

    /**
     * Max polarity events committed per ViewLoop write buffer. A 4M AE buffer
     * would otherwise materialize millions of events per frame (render hitch).
     * EVT3 timebase is still advanced for dropped events.
     */
    static final int MAX_DISPLAY_EVENTS_PER_PACKET = 262144;
    private static final int[] DISCARD_INTS = new int[1];

    int displayEventCap() {
        return Math.min(Math.max(monitor.getAEBufferSize(), 1000), MAX_DISPLAY_EVENTS_PER_PACKET);
    }

    void onAeBufferSizeChanged(int size) {
        final int cap = displayEventCap();
        ensureStaging(cap);
        polarityBuilder.ensureCapacity(cap);
    }

    private void ensureParseBuffers() {
        final int fifo = Math.max(fifoSize, UsbReaderBufferSettings.MIN_FIFO_SIZE);
        if (parseScratch == null || parseScratch.length < fifo) {
            parseScratch = new byte[fifo];
        }
        final int cap = displayEventCap();
        ensureStaging(cap);
        polarityBuilder.ensureCapacity(cap);
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
            final boolean writeBufferOverrun = monitor.getAePacketRawPool().writeBuffer().overrunOccuredFlag;
            final int cap = displayEventCap();
            parseLimit = writeBufferOverrun ? 0 : Math.max(0, cap - monitor.getEventCounter());
        }

        final long copyStart = sample != null ? System.nanoTime() : 0;
        if (parseScratch == null || parseScratch.length < bytesAvailable) {
            parseScratch = new byte[Math.max(bytesAvailable, fifoSize)];
        }
        buffer.get(parseScratch, 0, bytesAvailable);
        if (sample != null) {
            sample.byteCopyNs = System.nanoTime() - copyStart;
        }
        if (parseLimit > 0) {
            ensureStaging(parseLimit);
        }

        final long parseStart = sample != null ? System.nanoTime() : 0;
        final int[] addr = parseLimit > 0 ? stagingAddresses : DISCARD_INTS;
        final int[] ts = parseLimit > 0 ? stagingTimestamps : DISCARD_INTS;
        final int parsed = parser.parse(parseScratch, bytesAvailable, addr, ts, 0, parseLimit, true);
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
        parseScratchChunk.parsed = parsed;
        parseScratchChunk.parseLimit = parseLimit;
        return parseScratchChunk;
    }

    private void commitParsedChunk(ParsedChunk chunk, UsbPipelineBench.Sample sample) {
        if (chunk == ParsedChunk.EMPTY) {
            return;
        }
        final boolean demux = monitor.isUsbTypedDemuxActive();
        final int maxEvents = displayEventCap();
        final int requested;
        synchronized (monitor.getAePacketRawPool()) {
            final AEPacketRaw writeBuffer = monitor.getAePacketRawPool().writeBuffer();
            if (writeBuffer.overrunOccuredFlag) {
                return;
            }
            if (chunk.parseLimit == 0 && chunk.parsed < 0) {
                // Overrun during parse, or ViewLoop swapped while we scanned USB for timebase.
                return;
            }
            final int remaining = maxEvents - monitor.getEventCounter();
            if (chunk == ParsedChunk.OVERFLOW || remaining <= 0) {
                writeBuffer.overrunOccuredFlag = true;
                logOverrun(monitor.getEventCounter(), maxEvents, 0);
                return;
            }
            requested = chunk.parsed < 0
                    ? Math.min(chunk.parseLimit, remaining)
                    : Math.min(chunk.parsed, remaining);
        }

        long arrayCopyNs = 0;
        if (demux && requested > 0) {
            final long decodeStart = sample != null ? System.nanoTime() : 0;
            final AEChip chip = monitor.getChip();
            final int sizeX = chip != null ? chip.getSizeX() : Evt3Parser.WIDTH;
            final int sizeY = chip != null ? chip.getSizeY() : Evt3Parser.HEIGHT;
            polarityBuilder.fillPackedOffline(stagingAddresses, stagingTimestamps, 0, requested,
                    XMASK, 0, YMASK, 11, TYPEMASK, 22,
                    false, true, false, sizeX, sizeY);
            if (sample != null) {
                arrayCopyNs = System.nanoTime() - decodeStart;
            }
        }

        final long commitStart = sample != null ? System.nanoTime() : 0;
        synchronized (monitor.getAePacketRawPool()) {
            final AEPacketRaw writeBuffer = monitor.getAePacketRawPool().writeBuffer();
            if (writeBuffer.overrunOccuredFlag) {
                return;
            }
            final int startEvent = monitor.getEventCounter();
            writeBuffer.lastCaptureIndex = startEvent;
            final int remaining = maxEvents - startEvent;
            if (remaining <= 0) {
                writeBuffer.overrunOccuredFlag = true;
                logOverrun(startEvent, maxEvents, 0);
                return;
            }
            final int toCopy = Math.min(requested, remaining);
            if (toCopy > 0) {
                if (demux) {
                    final PacketBundle typedOut = monitor.getPacketBundlePool().writeBuffer();
                    polarityBuilder.installFill(typedOut, toCopy);
                    typedOut.setRawPacket(null);
                    writeBuffer.setNumEvents(0);
                } else {
                    final long acStart = System.nanoTime();
                    System.arraycopy(stagingAddresses, 0, writeBuffer.getAddresses(), startEvent, toCopy);
                    System.arraycopy(stagingTimestamps, 0, writeBuffer.getTimestamps(), startEvent, toCopy);
                    writeBuffer.setNumEvents(startEvent + toCopy);
                    arrayCopyNs += System.nanoTime() - acStart;
                }
            }
            monitor.setEventCounter(startEvent + toCopy);
            writeBuffer.lastCaptureLength = toCopy;
            if (chunk.parsed < 0 || toCopy < requested) {
                writeBuffer.overrunOccuredFlag = true;
                logOverrun(startEvent, maxEvents, toCopy);
            }
        }
        if (sample != null) {
            sample.commitLockNs = System.nanoTime() - commitStart;
            sample.arrayCopyNs = arrayCopyNs;
        }
    }

    private static final class ParsedChunk {
        static final ParsedChunk EMPTY = new ParsedChunk(0, 0);
        static final ParsedChunk OVERFLOW = new ParsedChunk(-1, 0);

        int parsed;
        int parseLimit;

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
                "Prophesee display packet full at %d events (cap %d, committed %d). "
                        + "Further events this frame are dropped (EVT3 timebase kept). "
                        + "High-rate EVK4: enable ARS; do not raise AE buffer above ~256k for live view.",
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
