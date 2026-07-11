package prophesee.usb;

import java.beans.PropertyChangeSupport;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

import org.usb4java.BufferUtils;
import org.usb4java.LibUsb;

import net.sf.jaer.JaerConstants;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.aemonitor.AEPacketRawPool;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.ReaderBufferControl;
import net.sf.jaer.util.TimestampSpread;
import prophesee.usb.evt3.Evt3Parser;
import prophesee.usb.evk4.Evk4BoardCommand;

/**
 * USB bulk reader for Prophesee EVK4 (endpoint 0x81, EVT3).
 * Uses synchronous bulk reads (same path as neuromorphic-drivers flush/poll).
 *
 * @see https://www.prophesee.ai/
 */
public class PropheseeAEReader implements ReaderBufferControl {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final int DEFAULT_FIFO_SIZE = 1 << 17;
    private static final int MIN_FIFO_SIZE = 4096;
    /** Cap avoids int overflow when doubling FIFO size from the Control menu. */
    private static final int MAX_FIFO_SIZE = 1 << 22;
    private static final int DEFAULT_NUM_BUFFERS = 32;
    private static final long READ_TIMEOUT_MS = 1000L;

    private final PropheseeHardwareInterface monitor;
    private final Evt3Parser parser = new Evt3Parser();
    private Thread readerThread;
    private volatile boolean readerActive;
    private int fifoSize = sanitizeFifoSize(
            JaerConstants.PREFS_ROOT_HARDWARE.getInt("Prophesee.AEReader.fifoSize", DEFAULT_FIFO_SIZE));
    private int numBuffers = JaerConstants.PREFS_ROOT_HARDWARE.getInt("Prophesee.AEReader.numBuffers", DEFAULT_NUM_BUFFERS);

    private long usbTransferCount;
    private long usbBytesTotal;
    private long usbEventsParsed;
    private long readTimeouts;
    private long lastTraceLogMs;
    private long lastTimeoutLogMs;
    private long lastOverrunLogMs;
    private long lastTimestampTraceLogMs;

    public PropheseeAEReader(PropheseeHardwareInterface monitor) {
        this.monitor = monitor;
    }

    public void startThread() throws HardwareInterfaceException {
        if (!monitor.isOpen()) {
            monitor.open();
        }
        if (readerThread != null) {
            return;
        }
        parser.reset();
        usbTransferCount = 0;
        usbBytesTotal = 0;
        usbEventsParsed = 0;
        readTimeouts = 0;
        Evk4BoardCommand.clearEventEndpointHalt(monitor.getDeviceHandle());
        log.info("Starting Prophesee AEReader on endpoint 0x81 (EVT3, sync bulk)");
        log.fine("Prophesee AEReader: fifoSize=" + getFifoSize() + " readTimeoutMs=" + READ_TIMEOUT_MS);
        readerActive = true;
        readerThread = new Thread(this::readLoop, "PropheseeAEReader");
        readerThread.setDaemon(true);
        readerThread.start();
        monitor.getReaderSupportInternal().firePropertyChange("readerStarted", false, true);
    }

    void prepareForStop() {
        readerActive = false;
        if (readerThread != null) {
            readerThread.interrupt();
        }
    }

    void finishStop() {
        if (readerThread == null) {
            return;
        }
        try {
            readerThread.join(3000L);
            if (readerThread.isAlive()) {
                log.warning("Prophesee AEReader thread did not stop within 3s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        readerThread = null;
        monitor.getReaderSupportInternal().firePropertyChange("readerStopped", false, true);
    }

    public void stopThread() {
        if (readerThread == null) {
            return;
        }
        log.info("Stopping Prophesee AEReader");
        prepareForStop();
        finishStop();
    }

    private void readLoop() {
        final ByteBuffer buffer = BufferUtils.allocateByteBuffer(getFifoSize());
        while (readerActive && monitor.isOpen() && !monitor.isUsbTransferFailed()) {
            buffer.clear();
            final int bytesRead;
            try {
                bytesRead = Evk4BoardCommand.readEventBulk(
                        monitor.getDeviceHandle(), buffer, READ_TIMEOUT_MS);
            } catch (HardwareInterfaceException e) {
                if (readerActive) {
                    log.warning("Prophesee event bulk read failed: " + e.getMessage());
                }
                break;
            }
            if (bytesRead == LibUsb.ERROR_TIMEOUT) {
                readTimeouts++;
                maybeLogReadTimeouts();
                continue;
            }
            if (bytesRead == 0) {
                continue;
            }
            if (bytesRead < 0) {
                if (readerActive) {
                    log.warning("Prophesee event bulk read: " + LibUsb.errorName(bytesRead));
                    monitor.markUsbDisconnected(bytesRead);
                }
                break;
            }
            buffer.limit(bytesRead);
            synchronized (monitor.getAePacketRawPool()) {
                if (!readerActive) {
                    return;
                }
                translateEvents(buffer);
            }
        }
        if (readerActive) {
            log.fine("Prophesee AEReader read loop exited: transfers=" + usbTransferCount
                    + " timeouts=" + readTimeouts);
        }
    }

    private void maybeLogReadTimeouts() {
        final long now = System.currentTimeMillis();
        if (now - lastTimeoutLogMs < 5000L) {
            return;
        }
        lastTimeoutLogMs = now;
        if (usbTransferCount == 0) {
            log.fine("Prophesee AEReader: no USB bytes yet (readTimeouts=" + readTimeouts + ")");
            if (readTimeouts >= 30) {
                log.warning("Prophesee AEReader: still no USB bytes after " + readTimeouts
                        + "s on endpoint 0x81; try Interface > Reset USB interface");
            }
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
        this.fifoSize = sanitizeFifoSize(fifoSize);
        JaerConstants.PREFS_ROOT_HARDWARE.putInt("Prophesee.AEReader.fifoSize", this.fifoSize);
    }

    private static int sanitizeFifoSize(int fifoSize) {
        if (fifoSize >= MIN_FIFO_SIZE && fifoSize <= MAX_FIFO_SIZE) {
            return fifoSize;
        }
        log.warning(String.format(
                "Invalid Prophesee USB FIFO size %d bytes; resetting to %d (valid range %d..%d). "
                        + "This can happen after repeatedly increasing FIFO size in Control menu.",
                fifoSize, DEFAULT_FIFO_SIZE, MIN_FIFO_SIZE, MAX_FIFO_SIZE));
        JaerConstants.PREFS_ROOT_HARDWARE.putInt("Prophesee.AEReader.fifoSize", DEFAULT_FIFO_SIZE);
        return DEFAULT_FIFO_SIZE;
    }

    @Override
    public int getNumBuffers() {
        return numBuffers;
    }

    @Override
    public void setNumBuffers(int numBuffers) {
        this.numBuffers = numBuffers;
        JaerConstants.PREFS_ROOT_HARDWARE.putInt("Prophesee.AEReader.numBuffers", numBuffers);
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

        if (usbTransferCount == 0) {
            log.info(String.format(
                    "Prophesee first USB event packet: %d bytes, %d events parsed",
                    bytesAvailable, Math.max(0, parsed)));
            if (bytesAvailable > 0 && parsed == 0) {
                log.warning("Prophesee EVT3: received bytes but parsed 0 events (possible stream desync)");
            }
        }
        usbTransferCount++;
        usbBytesTotal += bytesAvailable;
        usbEventsParsed += Math.max(0, parsed);
        maybeLogTraceStats(parsed, bytesAvailable);
        maybeLogTimestampTrace(timestamps, startEvent, parsed);
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
}
