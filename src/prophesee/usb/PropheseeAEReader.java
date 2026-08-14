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
import net.sf.jaer.hardwareinterface.usb.UsbAsyncBulkReaderLifecycle;
import net.sf.jaer.hardwareinterface.usb.UsbAsyncBulkReaderLifecycle.Config;
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
    private final UsbAsyncBulkReaderLifecycle bufferLifecycle;
    private USBTransferThread usbTransfer;
    private volatile boolean readerActive;
    private volatile long sessionGeneration;
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
        bufferLifecycle = new UsbAsyncBulkReaderLifecycle(new BufferHost());
    }

    void syncUsbBufferSettings(int fifoSize, int numBuffers) {
        this.fifoSize = fifoSize;
        this.numBuffers = numBuffers;
    }

    /**
     * Queue a FIFO/buffer change. Rapid Control-menu scrolls coalesce; one
     * transfer-session replace runs after a short idle delay.
     */
    void applyBufferSettingsAndRestart(int fifoSize, int numBuffers) {
        syncUsbBufferSettings(fifoSize, numBuffers);
        bufferLifecycle.schedule(new Config(fifoSize, numBuffers));
    }

    boolean isBufferReconfigPending() {
        return bufferLifecycle.isReconfigPending();
    }

    UsbAsyncBulkReaderLifecycle.Status getBufferConfigStatus() {
        return bufferLifecycle.statusSnapshot();
    }

    int getActiveFifoSize() {
        final Config applied = bufferLifecycle.appliedConfig();
        return applied != null ? applied.fifoSize : fifoSize;
    }

    int getActiveNumBuffers() {
        final Config applied = bufferLifecycle.appliedConfig();
        return applied != null ? applied.numBuffers : numBuffers;
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
        if (usbTransfer != null && usbTransfer.isAlive()) {
            return;
        }
        if (usbTransfer != null) {
            log.warning("Prophesee AEReader thread died; starting a new one");
            usbTransfer = null;
        }
        syncUsbBufferSettings(monitor.getFifoSize(), monitor.getNumBuffers());
        final long gen = bufferLifecycle.adoptExternalStart(new Config(fifoSize, numBuffers));
        try {
            startThreadInternal(true, gen);
        } catch (HardwareInterfaceException e) {
            bufferLifecycle.markFailed();
            throw e;
        }
    }

    private void startThreadInternal(boolean resetParser, long generation) throws HardwareInterfaceException {
        if (!monitor.isOpen()) {
            monitor.open();
        }
        if (usbTransfer != null && usbTransfer.isAlive()) {
            return;
        }
        usbTransfer = null;
        if (resetParser) {
            parser.reset();
        }
        sessionGeneration = generation;
        usbTransferCount = 0;
        usbBytesTotal = 0;
        usbEventsParsed = 0;
        ensureParseBuffers();
        final int cap = displayEventCap();
        if (monitor.getAEBufferSize() > cap) {
            log.info("Prophesee live keep limit " + cap
                    + " events/frame (AE buffer " + monitor.getAEBufferSize()
                    + ", keep pref " + monitor.getLiveDisplayEventCap()
                    + "). Further events keep EVT3 timebase but are not stored.");
        }

        // Draining 0x81 is only safe while the sensor is idle. A streaming EVK4 refills the
        // endpoint faster than synchronous reads empty it, which blocks this thread for tens of
        // seconds (frozen live view) and leaves the endpoint stalled for the new transfers.
        final boolean sensorStreaming = monitor.isSensorStreaming();
        HardwareInterfaceException lastFailure = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            if (!sensorStreaming) {
                Evk4BoardCommand.flushEventEndpoint(monitor.getDeviceHandle(), 50L, 250L);
            }
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
                    new ProcessAEData(generation),
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
            if (isDeviceIoFailure(err)) {
                // The endpoint or device is wedged; a smaller FIFO cannot fix that and retrying
                // would only persist a degraded size. Let the caller recover the device.
                break;
            }
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

    private static boolean isDeviceIoFailure(Throwable err) {
        final String msg = err != null ? err.getMessage() : null;
        if (msg == null) {
            return false;
        }
        return msg.contains("LIBUSB_ERROR_IO")
                || msg.contains("LIBUSB_ERROR_NO_DEVICE")
                || msg.contains("LIBUSB_ERROR_PIPE");
    }

    void prepareForStop() {
        bufferLifecycle.discardPendingRestart();
        bufferLifecycle.markQuiescing();
        readerActive = false;
        if (usbTransfer != null) {
            usbTransfer.interrupt();
        }
    }

    /**
     * @return true if the transfer thread is fully stopped (or was already null)
     */
    boolean finishStop() {
        if (usbTransfer == null) {
            bufferLifecycle.markStopped();
            return true;
        }
        final boolean stopped = UsbAsyncBulkReaderLifecycle.interruptAndJoin(
                usbTransfer, UsbAsyncBulkReaderLifecycle.DEFAULT_JOIN_TIMEOUT_MS, log, "Prophesee AEReader");
        if (!stopped) {
            bufferLifecycle.markFailed();
            return false;
        }
        usbTransfer = null;
        bufferLifecycle.markStopped();
        monitor.getReaderSupportInternal().firePropertyChange("readerStopped", false, true);
        return true;
    }

    public void stopThread() {
        if (usbTransfer == null) {
            bufferLifecycle.discardPendingRestart();
            bufferLifecycle.markStopped();
            return;
        }
        log.info("Stopping Prophesee AEReader");
        prepareForStop();
        if (!finishStop()) {
            monitor.recoverFailedBufferReconfig(new HardwareInterfaceException(
                    "Prophesee AEReader did not stop within "
                            + UsbAsyncBulkReaderLifecycle.DEFAULT_JOIN_TIMEOUT_MS + " ms"));
        }
    }

    /**
     * Max polarity events committed per ViewLoop write buffer. Defaults to
     * {@link HasLiveDisplayEventCap#DEFAULT_LIVE_DISPLAY_EVENT_CAP}; raised via
     * USB tuning. Still cannot exceed the AE packet pool size.
     * EVT3 timebase is still advanced for dropped events.
     */
    int displayEventCap() {
        final int pool = Math.max(monitor.getAEBufferSize(), 1000);
        final int keep = Math.max(1000, monitor.getLiveDisplayEventCap());
        return Math.min(pool, keep);
    }

    private static final int[] DISCARD_INTS = new int[1];

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
            // Grow on this USB thread so a Live-keep change from the EDT cannot race allocate().
            polarityBuilder.ensureCapacity(Math.max(requested, displayEventCap()));
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
        // startEvent = fill index before this USB commit (0 after a ViewLoop swap is normal);
        // committed = events kept from this chunk; remaining polarity events for the frame are dropped.
        log.warning(String.format(
                "Prophesee live view saturating: only the first %,d events per display frame are kept "
                        + "(packet was at %,d, kept %,d from this USB chunk). "
                        + "Further polarity events are discarded until the next frame; EVT3 timebase still advances. "
                        + "Effective keep limit is min(AE render packet, Live keep limit) from USB tuning. "
                        + "Live view and AEDAT logging both use this capped packet - a recording will miss the discarded events. "
                        + "Raise Live keep limit and Render events together if you need more per frame, "
                        + "or lower the sensor rate (biases / ROI / less motion). "
                        + "If the live image already looks fine, you can ignore this warning.",
                maxEvents, startEvent, committed));
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

        private final long generation;
        private volatile boolean active = true;

        ProcessAEData(long generation) {
            this.generation = generation;
        }

        @Override
        public void prepareTransfer(RestrictedTransfer transfer) {
        }

        @Override
        public void processTransfer(RestrictedTransfer transfer) {
            if (!active || !readerActive || !bufferLifecycle.isCurrent(generation)
                    || monitor.isUsbTransferFailed()) {
                return;
            }
            if (transfer.status() == LibUsb.TRANSFER_COMPLETED) {
                if (!bufferLifecycle.isCurrent(generation)) {
                    return;
                }
                final UsbPipelineBench.Sample sample = UsbPipelineBench.newSample("EVK4");
                final long totalStart = sample != null ? System.nanoTime() : 0;
                final ParsedChunk chunk = parseUsbChunk(transfer.buffer(), sample);
                if (!bufferLifecycle.isCurrent(generation)) {
                    return;
                }
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

    private final class BufferHost implements UsbAsyncBulkReaderLifecycle.Host {
        @Override
        public String deviceLabel() {
            return "Prophesee";
        }

        @Override
        public Logger log() {
            return log;
        }

        @Override
        public PropertyChangeSupport readerSupport() {
            return monitor.getReaderSupportInternal();
        }

        @Override
        public boolean hasActiveTransfer() {
            return usbTransfer != null && usbTransfer.isAlive();
        }

        @Override
        public boolean stopSession(long generation, long joinTimeoutMs) {
            readerActive = false;
            if (usbTransfer == null) {
                return true;
            }
            // USBTransferThread exits only once its transfer list drains, and it resubmits every
            // transfer that completes. While the sensor streams, completions keep racing ahead of
            // the cancellations, so the list never empties and the join always times out. Stop the
            // sensor first, like the acquisition-disable path does.
            final long stopStartNs = System.nanoTime();
            monitor.stopSensorStreaming();
            final boolean stopped = UsbAsyncBulkReaderLifecycle.interruptAndJoin(
                    usbTransfer, joinTimeoutMs, log, "Prophesee AEReader");
            log.info("Prophesee reader stop took " + ((System.nanoTime() - stopStartNs) / 1000000L)
                    + " ms (stopped=" + stopped + ")");
            if (!stopped) {
                return false;
            }
            usbTransfer = null;
            monitor.getReaderSupportInternal().firePropertyChange("readerStopped", false, true);
            return true;
        }

        @Override
        public Config startSession(Config requested, long generation) throws Exception {
            syncUsbBufferSettings(requested.fifoSize, requested.numBuffers);
            startThreadInternal(false, generation);
            // URBs are queued now, so the FX3 does not overflow into an unserviced endpoint.
            monitor.startSensorStreaming();
            return new Config(getFifoSize(), getNumBuffers());
        }

        @Override
        public void applyIdleConfig(Config config) {
            syncUsbBufferSettings(config.fifoSize, config.numBuffers);
        }

        @Override
        public void recoverFailedSession(Config pending, Exception cause) {
            monitor.recoverFailedBufferReconfig(cause);
        }
    }
}
