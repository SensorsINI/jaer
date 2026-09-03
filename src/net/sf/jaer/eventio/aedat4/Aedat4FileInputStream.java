package net.sf.jaer.eventio.aedat4;

import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.DavisBaseCamera;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ProgressMonitor;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.EventExtractor2D;
import net.sf.jaer.event.FramePacket;
import net.sf.jaer.event.ImuPacket;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.eventio.AEFileInputStream.Marks;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.eventio.RecordingChipDetector;
import net.sf.jaer.eventio.aedat4.dv.CompressionType;
import net.sf.jaer.eventio.aedat4.dv.Event;
import net.sf.jaer.eventio.aedat4.dv.EventPacket;
import net.sf.jaer.eventio.aedat4.dv.FileDataDefinition;
import net.sf.jaer.eventio.aedat4.dv.FileDataTable;
import net.sf.jaer.eventio.aedat4.dv.Frame;
import net.sf.jaer.eventio.aedat4.dv.FrameFormat;
import net.sf.jaer.eventio.aedat4.dv.IMU;
import net.sf.jaer.eventio.aedat4.dv.IMUPacket;
import net.sf.jaer.eventio.aedat4.dv.IOHeader;
import net.sf.jaer.util.EngineeringFormat;

/**
 * AEDAT-4 reader with a <b>sparse packet index</b>: file offsets, time bounds, and
 * event counts per EVTS/FRME/IMUS packet. Polarity address/timestamp arrays are
 * decoded on demand for the current playback window (not stored for the whole file).
 * <p>
 * First open prefers the trailing FileDataTable (decompress once, no per-packet LZ4);
 * falls back to a linear packet scan when the table is missing or invalid. A small
 * index cache under {@link net.sf.jaer.util.JaerTmpdir#aeidx()} then makes reopen
 * effectively instant.
 */
public class Aedat4FileInputStream implements AEFileInputStreamInterface {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    /**
     * v10: packet-level sparse index per selected EVTS stream (multi-camera AEDAT-4).
     * Chip class is not part of the cache — AEChip affects decode/render only.
     */
    private static final int INDEX_CACHE_VERSION = 11;
    private static final String INDEX_CACHE_MAGIC = "JAER4IDX";
    private static final int INDEX_CACHE_MAX_PACKETS = 10_000_000;
    private static final int INDEX_CACHE_MAX_TIMELINE = 50_000_000;
    /**
     * File-dialog preview: index at most this many EVTS packets when FileDataTable
     * and the {@code *.aedat4idx} cache are missing (avoids a full linear scan).
     */
    public static final int PREVIEW_INDEX_EVTS_PACKETS = 30;
    /**
     * Max polarity events returned from one {@code readPacketBy*}. Prevents OOM / multi-second
     * hangs when on-demand decode would otherwise walk millions of FlatBuffer events.
     */
    private static final int MAX_EVENTS_PER_READ = 100_000;
    /** Equal-time bins for the AEPlayer log event-rate sparkline (packet-table estimate). */
    private static final int EVENT_RATE_BINS = 1024;
    /** Floor (Hz) so log scale stays defined for quiet bins. */
    private static final double EVENT_RATE_LOG_FLOOR_HZ = 1.0;
    /**
     * Scan actual event timestamps for timeslices when EVTS packet durations
     * differ by at least this factor (2 orders of magnitude).
     */
    private static final long TIMESLICE_SCAN_DURATION_RATIO = 100;
    /**
     * Save As used to write one EVTS packet per 100k events (tens of seconds).
     * Equal-duration mega-packets never trip {@link #TIMESLICE_SCAN_DURATION_RATIO},
     * so interpolation attaches APS frames to a later clock than the events.
     */
    private static final long TIMESLICE_SCAN_MIN_PACKET_SPAN_US = 500_000L;
    private static final long TIMESLICE_SCAN_MIN_PACKET_EVENTS = 16_384L;

    private final AEChip chip;
    private final PropertyChangeSupport support = new PropertyChangeSupport(this);
    private final TreeSet<Long> markers = new TreeSet<>();
    /** For quick double-prev: skip marker just landed on via next/prev (see {@link #jumpToPrevMarker()}). */
    private long lastJumpTimeMs;
    private File file;
    private RandomAccessFile randomAccessFile;
    private FileChannel channel;
    private int compression = CompressionType.NONE;

    /**
     * Stream IDs selected for playback. DV may mux several cameras; jAER plays one
     * EVTS stream (plus FRME/IMUS that share that camera's {@code source}).
     * Resolved from the constructor argument / infoNode during open.
     */
    private int eventStreamId = Aedat4FileOutputStream.STREAM_EVENTS;
    private int frameStreamId = Aedat4FileOutputStream.STREAM_FRAMES;
    private int imuStreamId = Aedat4FileOutputStream.STREAM_IMU;
    /** Requested EVTS stream before header parse; null = first EVTS in infoNode. */
    private final Integer requestedEventStreamId;
    private String selectedSource;
    /**
     * True when file event/frame coords use DV/OpenCV (top-left). False for jAER-written
     * streams whose addresses already match live Davis packing (no extra XY flip on read).
     */
    private boolean dvOpenCvCoordinates = true;
    /**
     * Byte offset of the trailing FileDataTable, or &lt;0 if absent/pending.
     * Set in {@link #readHeaderAndResolveStreams()}.
     */
    private long dataTablePosition = -1L;

    /** Polarity stream packets (sparse seek table). */
    private PacketRef[] eventRefs = new PacketRef[0];
    private PacketRef[] frameRefs = new PacketRef[0];
    private PacketRef[] imuRefs = new PacketRef[0];
    private long baseUnixUs;
    private long eventCount;
    private long frameCount;
    private long imuSampleCount;
    /**
     * True when the sparse index covers the whole recording (cache or FileDataTable
     * or a full packet scan). False for a truncated preview scan.
     */
    private boolean indexComplete;
    /**
     * Sum of on-disk packet payload sizes. {@code -1} until {@link #ensurePayloadCompressionStats()}.
     */
    private long cachedCompressedPayloadBytes = -1;
    /**
     * Uncompressed FlatBuffer payload bytes from codec headers.
     * {@code -1} not computed; {@code -2} headers do not store the size.
     */
    private long cachedUncompressedPayloadBytes = -1;

    /**
     * Synthetic clock when the file has frames/IMU but no polarity events.
     * Unused when {@link #eventRefs} is non-empty.
     */
    private int[] timelineTimestamps = new int[0];

    private long position;
    private long markIn;
    private long markOut = Long.MAX_VALUE;
    private boolean repeat;
    private boolean nonMonotonicTimeExceptionsChecked = true;
    private int currentStartTimestamp;
    /** Last 32-bit relative timestamp emitted (for EVENT_WRAPPED_TIME). */
    private int mostRecentEmittedTimestamp;
    private boolean haveEmittedTimestamp;
    private int timestampResetBitmask;

    private static final long SKIPPED_EVENT_WARNING_INTERVAL_MS = 1000L;
    private long lastSkippedEventWarningMs;
    private long skippedEventsSinceWarning;
    /** Steady-state read/append FINE is once per this interval; anomalies stay unthrottled. */
    private static final long PLAYBACK_FINE_INTERVAL_MS = 2000L;
    private long lastPlaybackFineLogMs;
    private int lastLoggedDecodeKey = Integer.MIN_VALUE;

    /** Typed packets for the most recent readPacketBy* time window. */
    private final List<FramePacket> pendingFrames = new ArrayList<>();
    private final List<ImuPacket> pendingImu = new ArrayList<>();
    private long lastReadT0;
    private long lastReadT1;
    private int frameCursor;
    private int imuCursor;
    private boolean loggedImuOffsetFallback;

    /** Last decompressed polarity packet (sequential playback reuse). */
    private int cachedEventPacketIndex = -1;
    private ByteBuffer cachedEventFlat;

    /**
     * Log10(rate)/log10(max) per equal-time bin, or {@code null}. Built from the
     * sparse packet table (no decompress).
     */
    private float[] logRelativeEventRateByTime;
    /**
     * True when EVTS packet durations vary by {@link #TIMESLICE_SCAN_DURATION_RATIO},
     * a packet is a Save-As mega-packet, or some packets have inverted/zero span —
     * linear interpolation then mis-slices events versus APS frames.
     */
    private boolean scanTimesliceInPacket;

    public Aedat4FileInputStream(File file, AEChip chip) throws IOException {
        this(file, chip, null, null);
    }

    public Aedat4FileInputStream(File file, AEChip chip, ProgressMonitor progressMonitor) throws IOException {
        this(file, chip, progressMonitor, null);
    }

    /**
     * @param eventStreamId AEDAT-4 EVTS stream to play; {@code null} selects the first
     *        EVTS stream from {@code infoNode} (legacy files: stream 0).
     */
    public Aedat4FileInputStream(File file, AEChip chip, ProgressMonitor progressMonitor,
            Integer eventStreamId) throws IOException {
        this(file, chip, progressMonitor, eventStreamId, true);
    }

    /**
     * @param allowLinearIndexScan if false, skip a full packet scan when the
     *        FileDataTable and index cache are missing; index only
     *        {@link #PREVIEW_INDEX_EVTS_PACKETS} EVTS packets for a file-dialog preview
     */
    public Aedat4FileInputStream(File file, AEChip chip, ProgressMonitor progressMonitor,
            Integer eventStreamId, boolean allowLinearIndexScan) throws IOException {
        this.file = file;
        this.chip = chip;
        this.requestedEventStreamId = eventStreamId;
        this.randomAccessFile = new RandomAccessFile(file, "r");
        this.channel = randomAccessFile.getChannel();
        try {
            log.fine("Aedat4FileInputStream open begin: " + file + " requestedEventStreamId=" + eventStreamId
                    + " allowLinearIndexScan=" + allowLinearIndexScan);
            readHeaderAndResolveStreams();
            log.fine("header compression=" + Aedat4Compression.nameOf(compression)
                    + " eventStreamId=" + this.eventStreamId
                    + " frameStreamId=" + this.frameStreamId
                    + " imuStreamId=" + this.imuStreamId
                    + " source=" + selectedSource);
            if (!maybeLoadCachedIndex(progressMonitor)) {
                log.fine("no usable cache; indexing " + file.getName());
                indexFile(progressMonitor, allowLinearIndexScan);
                if (indexComplete) {
                    log.fine("index complete; writing cache");
                    cacheIndex(progressMonitor);
                    log.fine("cache write complete");
                } else {
                    log.fine("preview index is partial; not writing cache");
                }
            } else {
                indexComplete = true;
                log.fine("loaded index from cache");
            }
            throwIfCanceled(progressMonitor, "AEDAT-4 open");
            if (progressMonitor != null) {
                progressMonitor.setNote("Finishing open " + file.getName());
                // Do not set 100 here — ProgressMonitor closes at max before EDT playback setup.
                progressMonitor.setProgress(99);
            }
            log.fine("clearMarks / EVENT_INIT");
        } catch (IOException e) {
            log.fine("open failed: " + e);
            try {
                close();
            } catch (IOException ignore) {
                // already failing open
            }
            throw e;
        }
        clearMarks();
        buildLogRelativeEventRateBins();
        chooseTimesliceEstimator();
        EngineeringFormat eng = new EngineeringFormat();
        eng.setPrecision(3);
        log.info(String.format(
                "Opened AEDAT-4 %s (%s): stream %d%s: %s events, %s frames, %s IMU samples, duration=%ss (%d EVTS packets indexed)",
                file.getName(),
                Aedat4Compression.nameOf(compression),
                this.eventStreamId,
                selectedSource == null ? "" : " (" + selectedSource + ")",
                eng.format((double) eventCount).trim(),
                eng.format((double) frameCount).trim(),
                eng.format((double) imuSampleCount).trim(),
                eng.format(getDurationUsLong() * 1e-6).trim(),
                eventRefs.length));
        support.firePropertyChange(AEInputStream.EVENT_INIT, null, this);
        log.fine("Aedat4FileInputStream constructor returning");
    }

    /** Selected polarity stream ID after open. */
    public int getEventStreamId() {
        return eventStreamId;
    }

    public String getSelectedSource() {
        return selectedSource;
    }

    /** Whole-file sparse index (cache, FileDataTable, or full scan), not a preview prefix. */
    public boolean isIndexComplete() {
        return indexComplete;
    }

    @Override
    public boolean usesTimeMappedSlider() {
        return indexComplete && eventRefs.length > 0 && getDurationUsLong() > 0;
    }

    @Override
    public float getPlaybackSliderFraction() {
        return usesTimeMappedSlider() ? getFractionalTimePosition() : getFractionalPosition();
    }

    @Override
    public void setPlaybackSliderFraction(float frac) {
        if (usesTimeMappedSlider()) {
            setFractionalTimePosition(frac);
        } else {
            setFractionalPosition(frac);
        }
    }

    @Override
    public int eventPositionToSliderValue(long eventPos, int sliderMax) {
        if (!usesTimeMappedSlider() || sliderMax <= 0) {
            return AEFileInputStreamInterface.super.eventPositionToSliderValue(eventPos, sliderMax);
        }
        long dur = getDurationUsLong();
        long n = playableSize();
        if (dur <= 0 || n <= 0) {
            return 0;
        }
        long idx = Math.max(0, Math.min(eventPos, n - 1));
        float f = (float) ((timestampApproxLong(idx) - eventRefs[0].unixStart) / (double) dur);
        if (f < 0) {
            f = 0;
        } else if (f > 1) {
            f = 1;
        }
        return Math.round(f * sliderMax);
    }

    @Override
    public float[] getLogRelativeEventRateByTime() {
        return logRelativeEventRateByTime;
    }

    @Override
    public long getPositionTimestampUs() {
        if (!hasPolarity() || playableSize() == 0) {
            return getMostRecentTimestamp() & 0xffffffffL;
        }
        long index = Math.max(0, Math.min(position == 0 ? 0 : position - 1, playableSize() - 1));
        return timestampApproxLong(index);
    }

    public long getFrameCount() {
        return frameCount;
    }

    public long getImuSampleCount() {
        return imuSampleCount;
    }

    /**
     * Summary of this AEDAT-4 recording: event/frame/IMU counts, duration,
     * on-disk size, and packet-payload compression vs uncompressed FlatBuffers
     * when codec headers store the original size (ZSTD; LZ4 written by current jAER).
     */
    @Override
    public String getFileInfo() {
        EngineeringFormat eng = new EngineeringFormat();
        eng.setPrecision(3);
        StringBuilder sb = new StringBuilder();
        if (file != null) {
            sb.append(file.getAbsolutePath()).append('\n');
        }
        long durationUs = getDurationUsLong();
        String durationStr = eng.format(durationUs * 1e-6).trim() + "s";
        if (durationUs > 3_600_000_000L) { // more than 1 h
            long totalMin = durationUs / 60_000_000L;
            durationStr += String.format(" (%dh%02dm)", totalMin / 60, totalMin % 60);
        }
        sb.append(String.format("AEDAT-4 %s: %s events, %s frames, %s IMU samples, duration=%s",
                Aedat4Compression.nameOf(compression),
                eng.format((double) eventCount).trim(),
                eng.format((double) frameCount).trim(),
                eng.format((double) imuSampleCount).trim(),
                durationStr));
        if (file != null) {
            sb.append(String.format("\nSize: %sB on disk", eng.format((double) file.length()).trim()));
        }
        ensurePayloadCompressionStats();
        String compressionLine = Aedat4Compression.formatPayloadCompression(
                compression, cachedUncompressedPayloadBytes > 0 ? cachedUncompressedPayloadBytes : -1,
                cachedCompressedPayloadBytes);
        if (!compressionLine.isEmpty()) {
            sb.append('\n').append(compressionLine);
        }
        sb.append(String.format("\nStream %d%s, %d EVTS packets indexed",
                eventStreamId,
                selectedSource == null ? "" : " (" + selectedSource + ")",
                eventRefs.length));
        if (chip != null) {
            sb.append("\nChip: ").append(chip.getClass().getSimpleName());
        }
        return sb.toString();
    }

    /**
     * Sum compressed payload sizes; peek ZSTD/LZ4 frame headers for uncompressed size.
     * Positional {@link FileChannel} reads do not move the playback cursor.
     */
    private void ensurePayloadCompressionStats() {
        if (cachedCompressedPayloadBytes >= 0) {
            return;
        }
        cachedCompressedPayloadBytes = sumPayloadBytes(eventRefs)
                + sumPayloadBytes(frameRefs) + sumPayloadBytes(imuRefs);
        if (compression == CompressionType.NONE) {
            cachedUncompressedPayloadBytes = cachedCompressedPayloadBytes;
            return;
        }
        if (channel == null || !channel.isOpen()) {
            cachedUncompressedPayloadBytes = -2;
            return;
        }
        ByteBuffer peek = ByteBuffer.allocate(Aedat4Compression.UNCOMPRESSED_SIZE_HEADER_BYTES);
        long uncompressed = 0;
        long fromEvents = uncompressedFromRefs(eventRefs, peek);
        if (fromEvents < 0) {
            cachedUncompressedPayloadBytes = -2;
            return;
        }
        uncompressed += fromEvents;
        long fromFrames = uncompressedFromRefs(frameRefs, peek);
        if (fromFrames < 0) {
            cachedUncompressedPayloadBytes = -2;
            return;
        }
        uncompressed += fromFrames;
        long fromImu = uncompressedFromRefs(imuRefs, peek);
        if (fromImu < 0) {
            cachedUncompressedPayloadBytes = -2;
            return;
        }
        cachedUncompressedPayloadBytes = uncompressed + fromImu;
    }

    /** Sum of uncompressed payload sizes, or {@code -1} if any packet header omits it. */
    private long uncompressedFromRefs(PacketRef[] refs, ByteBuffer peek) {
        if (refs == null || refs.length == 0) {
            return 0;
        }
        long n = 0;
        for (PacketRef r : refs) {
            if (r.payloadSize <= 0) {
                continue;
            }
            peek.clear();
            peek.limit(Math.min(peek.capacity(), r.payloadSize));
            int read;
            try {
                read = channel.read(peek, r.payloadOffset);
            } catch (IOException e) {
                log.log(Level.FINE, "Could not peek AEDAT-4 payload header at " + r.payloadOffset, e);
                return -1;
            }
            if (read <= 0) {
                return -1;
            }
            long u = Aedat4Compression.uncompressedSize(peek.array(), 0, read, compression, r.payloadSize);
            if (u < 0) {
                return -1;
            }
            n += u;
        }
        return n;
    }

    private static long sumPayloadBytes(PacketRef[] refs) {
        long n = 0;
        if (refs == null) {
            return 0;
        }
        for (PacketRef r : refs) {
            if (r.payloadSize > 0) {
                n += r.payloadSize;
            }
        }
        return n;
    }

    private static void throwIfCanceled(ProgressMonitor progressMonitor, String what) throws IOException {
        if (Thread.currentThread().isInterrupted()
                || (progressMonitor != null && progressMonitor.isCanceled())) {
            throw new IOException(what + " canceled");
        }
    }

    /**
     * Appends FRME/IMUS packets that fall in the last {@code readPacketBy*}
     * time window into {@code bundle}, and updates {@link DavisBaseCamera}'s
     * latest IMU sample for the overlay.
     */
    public synchronized void appendTypedPackets(PacketBundle bundle) {
        if (bundle == null) {
            return;
        }
        final int nFrames = pendingFrames.size();
        final int nImu = pendingImu.size();
        for (FramePacket frame : pendingFrames) {
            bundle.add(frame);
        }
        for (ImuPacket imu : pendingImu) {
            bundle.add(imu);
            if (imu.getSize() > 0) {
                final IMUSample last = imu.get(imu.getSize() - 1);
                if (chip instanceof DavisBaseCamera) {
                    ((DavisBaseCamera) chip).setImuSample(last);
                } else if (chip instanceof ch.unizh.ini.jaer.chip.retina.DVXplorer dvx) {
                    dvx.setLatestImuSample(last);
                }
            }
        }
        if ((nFrames > 0 || nImu > 0) && shouldLogPlaybackFine()) {
            log.fine(String.format("AEDAT-4 appendTypedPackets window=[%d,%d] frames=%d imuPkts=%d (indexed frames=%d)",
                    lastReadT0, lastReadT1, nFrames, nImu, frameRefs.length));
        }
        pendingFrames.clear();
        pendingImu.clear();
    }

    /** True when this open selected a FRME stream with indexed frames. */
    public boolean hasFramePackets() {
        return frameStreamId >= 0 && frameRefs != null && frameRefs.length > 0;
    }

    /** True when this open selected an IMU stream with indexed samples. */
    public boolean hasImuPackets() {
        return imuStreamId >= 0 && imuRefs != null && imuRefs.length > 0;
    }

    /** Reads IOHeader, resolves selected camera stream IDs from infoNode. */
    private void readHeaderAndResolveStreams() throws IOException {
        channel.position(0);
        ByteBuffer version = ByteBuffer.allocate(Aedat4FileOutputStream.VERSION_LINE.length);
        readFully(channel, version);
        if (!Arrays.equals(version.array(), Aedat4FileOutputStream.VERSION_LINE)) {
            throw new IOException(file + " is not an AEDAT-4 file");
        }
        ByteBuffer headerBytes = readSizePrefixed(channel);
        IOHeader header = IOHeader.getSizePrefixedRootAsIOHeader(headerBytes);
        compression = Aedat4Compression.clamp(header.compression());
        dataTablePosition = header.dataTablePosition();
        resolveStreamIds(header.infoNode());
        log.info(String.format(
                "AEDAT-4 header %s: compression=%s dataTablePosition=%d",
                file.getName(),
                Aedat4Compression.nameOf(compression),
                dataTablePosition));
    }

    /**
     * Map infoNode streams to the EVTS/FRME/IMUS IDs used for indexing.
     * Multi-camera DV files assign arbitrary IDs (e.g. 0=DAVIS EVTS, 1=DVXplorer EVTS).
     */
    private void resolveStreamIds(String infoNode) {
        eventStreamId = Aedat4FileOutputStream.STREAM_EVENTS;
        frameStreamId = Aedat4FileOutputStream.STREAM_FRAMES;
        imuStreamId = Aedat4FileOutputStream.STREAM_IMU;
        selectedSource = null;
        List<RecordingChipDetector.StreamHint> streams
                = RecordingChipDetector.streamsFromInfoNodeXml(infoNode);
        logAedat4InfoNodeSummary(streams);
        if (streams.isEmpty()) {
            if (requestedEventStreamId != null) {
                eventStreamId = requestedEventStreamId;
            }
            return;
        }
        List<RecordingChipDetector.StreamHint> evts = new ArrayList<>();
        for (RecordingChipDetector.StreamHint s : streams) {
            if (s.isEvents()) {
                evts.add(s);
            }
        }
        RecordingChipDetector.StreamHint chosen = null;
        if (requestedEventStreamId != null) {
            for (RecordingChipDetector.StreamHint s : evts) {
                if (s.streamId == requestedEventStreamId) {
                    chosen = s;
                    break;
                }
            }
            if (chosen == null) {
                for (RecordingChipDetector.StreamHint s : streams) {
                    if (s.streamId == requestedEventStreamId) {
                        chosen = s;
                        break;
                    }
                }
            }
            if (chosen == null) {
                log.warning("Requested AEDAT-4 event stream " + requestedEventStreamId
                        + " not in infoNode; using first EVTS stream");
            }
        }
        if (chosen == null && !evts.isEmpty()) {
            chosen = evts.get(0);
        }
        if (chosen == null) {
            chosen = streams.get(0);
        }
        eventStreamId = chosen.streamId;
        selectedSource = chosen.source;
        dvOpenCvCoordinates = chosen.hasDvOpenCvCoordinates();
        if (!dvOpenCvCoordinates) {
            log.info("AEDAT-4 stream " + eventStreamId
                    + ": jAER coordinate space (skip DV OpenCV XY remap on read)");
        }
        frameStreamId = -1;
        imuStreamId = -1;
        for (RecordingChipDetector.StreamHint s : streams) {
            if (s.streamId == eventStreamId) {
                continue;
            }
            boolean sameSource = selectedSource != null && selectedSource.equals(s.source);
            // Same-source typed streams, or legacy single-camera 0/1/2 layout.
            if (s.isFrames() && (sameSource || frameStreamId < 0 && streams.size() <= 3)) {
                if (frameStreamId < 0 || sameSource) {
                    frameStreamId = s.streamId;
                }
            } else if (s.isImu() && (sameSource || imuStreamId < 0 && streams.size() <= 3)) {
                if (imuStreamId < 0 || sameSource) {
                    imuStreamId = s.streamId;
                }
            }
        }
        if (evts.size() > 1) {
            log.info(String.format(
                    "AEDAT-4 multi-camera file: playing EVTS stream %d (%s); %d EVTS streams available",
                    eventStreamId, selectedSource, evts.size()));
        }
    }

    /** One INFO line per stream so DV source / size / colorFilter are visible on open. */
    private void logAedat4InfoNodeSummary(List<RecordingChipDetector.StreamHint> streams) {
        if (streams == null || streams.isEmpty()) {
            log.info("AEDAT-4 infoNode: (no streams parsed)");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("AEDAT-4 infoNode: ").append(streams.size()).append(" stream(s)");
        for (RecordingChipDetector.StreamHint s : streams) {
            sb.append(" | ").append(s.displayLabel());
            if (s.colorFilter != null) {
                sb.append(" colorFilter=").append(s.colorFilter);
            }
            if (s.originalModuleName != null && !s.originalModuleName.isEmpty()) {
                sb.append(" module=").append(s.originalModuleName);
            }
            if (s.originalOutputName != null && !s.originalOutputName.isEmpty()
                    && (s.source == null || !s.originalOutputName.equals(s.source))) {
                sb.append(" out=").append(s.originalOutputName);
            }
        }
        log.info(sb.toString());
    }

    /**
     * Build the sparse packet index. Prefers the trailing FileDataTable and
     * falls back to a packet scan when the table is missing or invalid.
     */
    private void indexFile(ProgressMonitor progressMonitor, boolean allowLinearScan) throws IOException {
        if (tryIndexFromFileDataTable(progressMonitor)) {
            indexComplete = true;
            return;
        }
        int maxEvts = allowLinearScan ? Integer.MAX_VALUE : PREVIEW_INDEX_EVTS_PACKETS;
        indexFileByScanningPackets(progressMonitor, maxEvts);
        indexComplete = allowLinearScan;
    }

    /**
     * Index from AEDAT-4 FileDataTable: offsets, stream IDs, element counts, and
     * timestamp bounds — no payload decompression.
     *
     * @return true if the table was used successfully
     */
    private boolean tryIndexFromFileDataTable(ProgressMonitor progressMonitor) throws IOException {
        long fileSize = channel.size();
        if (dataTablePosition < 0 || dataTablePosition >= fileSize) {
            log.fine("AEDAT-4 FileDataTable unavailable (dataTablePosition=" + dataTablePosition + ")");
            return false;
        }
        long t0 = System.currentTimeMillis();
        if (progressMonitor != null) {
            progressMonitor.setNote("Reading AEDAT-4 FileDataTable");
            progressMonitor.setProgress(5);
        }
        throwIfCanceled(progressMonitor, "AEDAT-4 FileDataTable index");
        final long remaining = fileSize - dataTablePosition;
        if (remaining < 8 || remaining > 512L * 1024 * 1024) {
            log.warning("AEDAT-4 FileDataTable remaining bytes implausible (" + remaining + "); scanning packets");
            return false;
        }
        // The FileDataTable uses the IOHeader codec. Legacy jAER files wrote it
        // uncompressed, so retain raw-FTAB detection. Region is [dataTablePosition, EOF).
        channel.position(dataTablePosition);
        ByteBuffer rawTable = ByteBuffer.allocate((int) remaining).order(ByteOrder.LITTLE_ENDIAN);
        try {
            readFully(channel, rawTable);
        } catch (IOException e) {
            log.warning("AEDAT-4 FileDataTable region read failed; scanning packets: " + e.getMessage());
            return false;
        }
        rawTable.flip();
        ByteBuffer tableBytes;
        try {
            if (compression == CompressionType.NONE || looksLikeFileDataTable(rawTable)) {
                tableBytes = rawTable;
            } else {
                // Decompress entire trailing region (one LZ4/ZSTD frame).
                byte[] compressed = new byte[rawTable.remaining()];
                rawTable.get(compressed);
                byte[] flat = Aedat4Compression.decompress(compressed, compression);
                tableBytes = ByteBuffer.wrap(flat).order(ByteOrder.LITTLE_ENDIAN);
            }
        } catch (IOException e) {
            log.warning("AEDAT-4 FileDataTable decompress failed; scanning packets: " + e.getMessage());
            return false;
        }
        if (!looksLikeFileDataTable(tableBytes)) {
            log.warning("AEDAT-4 FileDataTable after decompress is not FTAB; scanning packets");
            return false;
        }
        // Size-prefixed root: reject absurd prefixes before FlatBuffers walk.
        if (tableBytes.remaining() >= 4) {
            int prefix = tableBytes.getInt(tableBytes.position());
            if (prefix < 8 || prefix + 4L > tableBytes.remaining()) {
                log.warning("AEDAT-4 FileDataTable size prefix=" + prefix + " vs buffer="
                        + tableBytes.remaining() + "; scanning packets");
                return false;
            }
        }
        FileDataTable table;
        try {
            table = FileDataTable.getSizePrefixedRootAsFileDataTable(tableBytes);
        } catch (Exception e) {
            log.warning("AEDAT-4 FileDataTable parse failed; scanning packets: " + e);
            return false;
        }
        int n = table.tableLength();
        if (n <= 0 || n > INDEX_CACHE_MAX_PACKETS) {
            log.warning("AEDAT-4 FileDataTable length=" + n + " unusable; scanning packets");
            return false;
        }
        ArrayList<PacketRef> events = new ArrayList<>();
        ArrayList<PacketRef> frames = new ArrayList<>();
        ArrayList<PacketRef> imus = new ArrayList<>();
        long cumEvents = 0;
        long imuElems = 0;
        int used = 0;
        int skipped = 0;
        FileDataDefinition def = new FileDataDefinition();
        final long dataEnd = dataTablePosition; // packets must lie before the table
        // DV and current jAER store byteOffset at the compressed payload; legacy
        // jAER stores the PacketHeader.
        Boolean offsetIsPayload = null;
        for (int i = 0; i < n; i++) {
            throwIfCanceled(progressMonitor, "AEDAT-4 FileDataTable index");
            if (progressMonitor != null && (i & 1023) == 0) {
                progressMonitor.setProgress(5 + (int) Math.min(80, (i * 80L) / n));
            }
            FileDataDefinition d = table.table(def, i);
            if (d == null) {
                log.warning("AEDAT-4 FileDataTable null entry at " + i + "; scanning packets");
                return false;
            }
            int streamId = d.packetInfoStreamID();
            int payloadSize = d.packetInfoSize();
            long byteOffset = d.byteOffset();
            long numElements = d.numElements();
            long tStart = d.timestampStart();
            long tEnd = d.timestampEnd();
            if (payloadSize < 0 || byteOffset < 0) {
                log.warning(String.format(
                        "AEDAT-4 FileDataTable entry %d invalid (off=%d size=%d); scanning packets",
                        i, byteOffset, payloadSize));
                return false;
            }
            if (offsetIsPayload == null) {
                offsetIsPayload = detectFtabOffsetIsPayload(byteOffset, streamId, payloadSize, dataEnd);
                log.fine("AEDAT-4 FileDataTable byteOffset points to "
                        + (offsetIsPayload ? "payload (DV)" : "PacketHeader (jAER)"));
            }
            long payloadOffset = offsetIsPayload ? byteOffset : byteOffset + 8L;
            if (payloadOffset + (long) payloadSize > dataEnd) {
                log.warning(String.format(
                        "AEDAT-4 FileDataTable entry %d out of range (off=%d payloadOff=%d size=%d dataEnd=%d); scanning packets",
                        i, byteOffset, payloadOffset, payloadSize, dataEnd));
                return false;
            }
            boolean known = streamId == eventStreamId
                    || streamId == frameStreamId
                    || streamId == imuStreamId;
            if (!known) {
                skipped++;
                continue;
            }
            if (streamId == eventStreamId) {
                int count = (int) Math.min(Integer.MAX_VALUE, Math.max(0, numElements));
                events.add(new PacketRef(payloadOffset, payloadSize, tStart, tEnd, count, cumEvents));
                cumEvents += count;
                used++;
            } else if (streamId == frameStreamId) {
                frames.add(new PacketRef(payloadOffset, payloadSize, tStart, tEnd, 1, 0));
                used++;
            } else if (streamId == imuStreamId) {
                int count = (int) Math.min(Integer.MAX_VALUE, Math.max(0, numElements));
                imus.add(new PacketRef(payloadOffset, payloadSize, tStart, tEnd, count, 0));
                imuElems += count;
                used++;
            }
        }
        if (used == 0 && n > 0) {
            // Table OK but no packets for selected streams — still a valid empty index.
            log.info("AEDAT-4 FileDataTable has " + n + " entries but none for selected streams "
                    + eventStreamId + "/" + frameStreamId + "/" + imuStreamId);
        }
        imuSampleCount = 0; // finalizeIndex will set from imus
        finalizeIndex(events, frames, imus, imuElems);
        log.info(String.format(
                "Indexed AEDAT-4 %s (%s) stream %d from FileDataTable in %d ms (%d table entries, %d used, %d other-stream): %,d events in %d EVTS packets, %,d frames, %,d IMU samples",
                file.getName(), Aedat4Compression.nameOf(compression), eventStreamId,
                System.currentTimeMillis() - t0, n, used, skipped,
                eventCount, eventRefs.length, frameCount, imuSampleCount));
        return true;
    }

    /**
     * FileDataTable {@code byteOffset} normally points at the compressed payload;
     * legacy jAER records the 8-byte PacketHeader. Peek the first entry to tell
     * them apart.
     */
    private boolean detectFtabOffsetIsPayload(long byteOffset, int streamId, int payloadSize, long dataEnd)
            throws IOException {
        ByteBuffer hdr = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        if (byteOffset >= 8) {
            channel.position(byteOffset - 8);
            hdr.clear();
            readFully(channel, hdr);
            hdr.flip();
            if (hdr.getInt() == streamId && hdr.getInt() == payloadSize) {
                return true; // header immediately before offset → DV payload offset
            }
        }
        if (byteOffset + 8L <= dataEnd) {
            channel.position(byteOffset);
            hdr.clear();
            readFully(channel, hdr);
            hdr.flip();
            if (hdr.getInt() == streamId && hdr.getInt() == payloadSize) {
                return false; // header at offset → jAER
            }
        }
        // Geometry fallback: last packets often only fit if offset is the payload.
        return byteOffset + (long) payloadSize <= dataEnd
                && byteOffset + 8L + payloadSize > dataEnd;
    }

    /** Slow path: decompress packets to recover counts/timestamps. */
    private void indexFileByScanningPackets(ProgressMonitor progressMonitor, int maxEventPackets) throws IOException {
        ArrayList<PacketRef> events = new ArrayList<>();
        ArrayList<PacketRef> frames = new ArrayList<>();
        ArrayList<PacketRef> imus = new ArrayList<>();
        long t0 = System.currentTimeMillis();
        long cumEvents = 0;
        long imuElems = 0;

        channel.position(0);
        ByteBuffer version = ByteBuffer.allocate(Aedat4FileOutputStream.VERSION_LINE.length);
        readFully(channel, version);
        if (!Arrays.equals(version.array(), Aedat4FileOutputStream.VERSION_LINE)) {
            throw new IOException(file + " is not an AEDAT-4 file");
        }

        ByteBuffer headerBytes = readSizePrefixed(channel);
        IOHeader header = IOHeader.getSizePrefixedRootAsIOHeader(headerBytes);
        compression = Aedat4Compression.clamp(header.compression());
        long tablePos = header.dataTablePosition();
        long fileSize = channel.size();
        ByteBuffer packetHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        long scannedPackets = 0;
        long skippedOtherStreams = 0;
        if (progressMonitor != null) {
            progressMonitor.setNote("Indexing AEDAT-4 packets (stream " + eventStreamId + ")");
        }
        while (channel.position() + 8 <= fileSize) {
            throwIfCanceled(progressMonitor, "AEDAT-4 indexing");
            if (tablePos >= 0 && channel.position() >= tablePos) {
                break;
            }
            long packetOffset = channel.position();
            packetHeader.clear();
            readFully(channel, packetHeader);
            packetHeader.flip();
            int streamId = packetHeader.getInt();
            int payloadSize = packetHeader.getInt();
            long remaining = fileSize - channel.position();
            if (payloadSize < 0 || payloadSize > remaining) {
                break;
            }
            boolean known = streamId == eventStreamId
                    || streamId == frameStreamId
                    || streamId == imuStreamId;
            // tablePos may be unset (-1) or pending (-2); stop before FTAB.
            if (tablePos < 0 && !known && streamId > 64) {
                log.info(String.format(
                        "Stopping AEDAT-4 index at offset %d (streamId=%d); FileDataTable follows and IOHeader dataTablePosition=%d",
                        packetOffset, streamId, tablePos));
                break;
            }
            long payloadOffset = channel.position();
            ByteBuffer payload = ByteBuffer.allocate(payloadSize).order(ByteOrder.LITTLE_ENDIAN);
            readFully(channel, payload);
            payload.flip();
            scannedPackets++;
            if (!known) {
                skippedOtherStreams++;
                continue;
            }
            ByteBuffer flat;
            try {
                flat = maybeDecompress(payload);
            } catch (IOException ex) {
                if (tablePos < 0 && looksLikeFileDataTable(payload)) {
                    log.info("Stopping AEDAT-4 index before FileDataTable (dataTablePosition unset)");
                    break;
                }
                throw ex;
            }
            if (streamId == eventStreamId) {
                EventPacket packet = EventPacket.getSizePrefixedRootAsEventPacket(flat);
                int num = packet.elementsLength();
                long start = 0;
                long end = 0;
                if (num > 0) {
                    start = packet.elements(0).timestamp();
                    end = packet.elements(num - 1).timestamp();
                }
                events.add(new PacketRef(payloadOffset, payloadSize, start, end, num, cumEvents));
                cumEvents += num;
                if (maxEventPackets < Integer.MAX_VALUE && events.size() >= maxEventPackets) {
                    log.info("AEDAT-4 preview index stopping after " + events.size() + " EVTS packets");
                    break;
                }
            } else if (streamId == frameStreamId) {
                Frame frame = Frame.getSizePrefixedRootAsFrame(flat);
                long start = frame.timestampStartOfFrame() != 0 ? frame.timestampStartOfFrame() : frame.timestamp();
                long end = frame.timestampEndOfFrame() != 0 ? frame.timestampEndOfFrame() : start;
                frames.add(new PacketRef(payloadOffset, payloadSize, start, end, 1, 0));
            } else if (streamId == imuStreamId) {
                IMUPacket packet = IMUPacket.getSizePrefixedRootAsIMUPacket(flat);
                int num = packet.elementsLength();
                long start = 0;
                long end = 0;
                if (num > 0) {
                    start = packet.elements(0).timestamp();
                    end = packet.elements(num - 1).timestamp();
                }
                imus.add(new PacketRef(payloadOffset, payloadSize, start, end, num, 0));
                imuElems += num;
            }
            if (progressMonitor != null && fileSize > 0) {
                long denom = tablePos > 0 ? tablePos : fileSize;
                progressMonitor.setProgress((int) Math.min(89, (packetOffset * 89) / Math.max(1, denom)));
            }
        }

        finalizeIndex(events, frames, imus, imuElems);
        log.info(String.format(
                "Indexed AEDAT-4 %s (%s) stream %d by packet scan in %d ms (%d packets scanned, %d other-stream skipped): %,d events in %d EVTS packets, %,d frames, %,d IMU samples",
                file.getName(), Aedat4Compression.nameOf(compression), eventStreamId,
                System.currentTimeMillis() - t0, scannedPackets, skippedOtherStreams,
                eventCount, eventRefs.length, frameCount, imuSampleCount));
    }

    private void finalizeIndex(List<PacketRef> events, List<PacketRef> frames, List<PacketRef> imus,
            long imuElems) {
        frameCount = frames.size();
        eventCount = 0;
        for (PacketRef r : events) {
            eventCount += r.numElements;
        }
        imuSampleCount = imuElems;
        if (imuSampleCount == 0) {
            for (PacketRef r : imus) {
                imuSampleCount += r.numElements;
            }
        }

        if (!events.isEmpty()) {
            baseUnixUs = events.get(0).unixStart;
        } else if (!frames.isEmpty()) {
            baseUnixUs = frames.get(0).unixStart;
        } else if (!imus.isEmpty()) {
            baseUnixUs = imus.get(0).unixStart;
        }

        eventRefs = unwrapPacketRefs(toRelativeRefs(events));
        frameRefs = unwrapPacketRefs(toRelativeRefs(frames));
        imuRefs = unwrapPacketRefs(toRelativeRefs(imus));
        timelineTimestamps = new int[0];
        if (eventRefs.length == 0 && (!frames.isEmpty() || !imus.isEmpty())) {
            synthesizeTimelineFromTypedStreams(frames, imus);
        }
        markOut = playableSize();
        cachedEventPacketIndex = -1;
        cachedEventFlat = null;
    }

    /**
     * Prefer packet-table interpolation for timeslices. Scan FlatBuffer timestamps
     * when packet durations vary by {@link #TIMESLICE_SCAN_DURATION_RATIO}, any
     * packet spans {@link #TIMESLICE_SCAN_MIN_PACKET_SPAN_US} or has
     * {@link #TIMESLICE_SCAN_MIN_PACKET_EVENTS} events (Save-As mega-packets),
     * or some packets have inverted/zero span.
     */
    private void chooseTimesliceEstimator() {
        scanTimesliceInPacket = false;
        if (eventRefs.length == 0) {
            return;
        }
        long minSpan = Long.MAX_VALUE;
        long maxSpan = 0;
        long maxElements = 0;
        int inverted = 0;
        for (PacketRef r : eventRefs) {
            if (r.numElements <= 0) {
                continue;
            }
            if (r.numElements > maxElements) {
                maxElements = r.numElements;
            }
            long span = r.unixEnd - r.unixStart;
            if (span <= 0) {
                inverted++;
                continue;
            }
            if (span < minSpan) {
                minSpan = span;
            }
            if (span > maxSpan) {
                maxSpan = span;
            }
        }
        long ratio = 0;
        boolean mega = maxSpan >= TIMESLICE_SCAN_MIN_PACKET_SPAN_US
                || maxElements >= TIMESLICE_SCAN_MIN_PACKET_EVENTS;
        if (inverted > 0 || mega) {
            scanTimesliceInPacket = true;
        } else if (minSpan > 0 && minSpan != Long.MAX_VALUE) {
            ratio = maxSpan / minSpan;
            scanTimesliceInPacket = ratio >= TIMESLICE_SCAN_DURATION_RATIO;
        }
        if (scanTimesliceInPacket) {
            log.info(String.format(
                    "AEDAT-4 timeslice: scanning event timestamps (packet duration ratio %s, inverted/zero-span packets=%d, maxSpan=%d us, maxEvents=%d)",
                    ratio > 0 ? Long.toString(ratio) : "n/a", inverted, maxSpan, maxElements));
        }
    }

    /**
     * Packet-mean event rate in equal-time bins, then log-relative to the file max.
     * Quiet bins map to 0. No file I/O.
     */
    private void buildLogRelativeEventRateBins() {
        logRelativeEventRateByTime = null;
        if (!hasPolarity() || eventRefs.length == 0) {
            return;
        }
        long t0 = eventRefs[0].unixStart;
        long dur = getDurationUsLong();
        if (dur <= 0) {
            return;
        }
        int nBins = EVENT_RATE_BINS;
        double[] counts = new double[nBins];
        for (PacketRef r : eventRefs) {
            if (r.numElements <= 0) {
                continue;
            }
            long s = r.unixStart - t0;
            long e = r.unixEnd - t0;
            if (e <= s) {
                e = s + 1;
            }
            int b0 = (int) Math.min(nBins - 1, Math.max(0, s * nBins / dur));
            int b1 = (int) Math.min(nBins - 1, Math.max(0, (e - 1) * nBins / dur));
            if (b1 < b0) {
                b1 = b0;
            }
            double perBin = r.numElements / (double) (b1 - b0 + 1);
            for (int b = b0; b <= b1; b++) {
                counts[b] += perBin;
            }
        }
        double binS = (dur * 1e-6) / nBins;
        if (binS <= 0) {
            return;
        }
        double maxHz = 0;
        double[] hz = new double[nBins];
        for (int i = 0; i < nBins; i++) {
            hz[i] = counts[i] / binS;
            if (hz[i] > maxHz) {
                maxHz = hz[i];
            }
        }
        if (maxHz <= 0) {
            log.fine("AEDAT-4 event-rate sparkline skipped: maxHz=0");
            return;
        }
        // Log-rate mapped from 5th–98th percentile so baseline activity is near zero height
        // and a few hot packets do not flatten the rest.
        int nLog = 0;
        double[] logs = new double[nBins];
        for (int i = 0; i < nBins; i++) {
            if (hz[i] > 0) {
                logs[nLog++] = Math.log10(Math.max(hz[i], EVENT_RATE_LOG_FLOOR_HZ));
            }
        }
        if (nLog == 0) {
            return;
        }
        Arrays.sort(logs, 0, nLog);
        int pLow = Math.min(nLog - 1, Math.max(0, (int) (0.05 * nLog)));
        int pHigh = Math.min(nLog - 1, Math.max(pLow + 1, (int) Math.ceil(0.98 * nLog) - 1));
        double logMin = logs[pLow];
        double logMax = logs[pHigh];
        if (logMax <= logMin) {
            logMin = 0;
            logMax = Math.log10(Math.max(maxHz, EVENT_RATE_LOG_FLOOR_HZ));
        }
        if (logMax <= logMin) {
            return;
        }
        double span = logMax - logMin;
        float[] out = new float[nBins];
        for (int i = 0; i < nBins; i++) {
            if (hz[i] <= 0) {
                out[i] = 0;
            } else {
                float v = (float) ((Math.log10(Math.max(hz[i], EVENT_RATE_LOG_FLOOR_HZ)) - logMin) / span);
                if (v < 0f) {
                    v = 0f;
                } else if (v > 1f) {
                    v = 1f;
                }
                out[i] = v;
            }
        }
        logRelativeEventRateByTime = out;
        if (log.isLoggable(Level.FINE)) {
            int nz = 0;
            float minP = 1, maxP = 0;
            for (float v : out) {
                if (v > 0) {
                    nz++;
                }
                if (v < minP) {
                    minP = v;
                }
                if (v > maxP) {
                    maxP = v;
                }
            }
            log.fine(String.format(
                    "AEDAT-4 event-rate sparkline: %d bins, %d nonzero, maxHz=%.3g, logRel min=%.3f max=%.3f, packets=%d durationUs=%d",
                    out.length, nz, maxHz, minP, maxP, eventRefs.length, dur));
        }
    }

    private void synthesizeTimelineFromTypedStreams(List<PacketRef> frames, List<PacketRef> imus) {
        ArrayList<Long> marks = new ArrayList<>();
        for (PacketRef f : frames) {
            marks.add(f.unixStart + ((f.unixEnd - f.unixStart) / 2));
        }
        for (PacketRef i : imus) {
            marks.add(i.unixStart);
        }
        marks.sort(Long::compareTo);
        if (marks.isEmpty()) {
            return;
        }
        if (baseUnixUs == 0) {
            baseUnixUs = marks.get(0);
        }
        timelineTimestamps = new int[marks.size()];
        for (int i = 0; i < marks.size(); i++) {
            timelineTimestamps[i] = (int) (marks.get(i) - baseUnixUs);
        }
    }

    private PacketRef[] toRelativeRefs(List<PacketRef> src) {
        PacketRef[] out = new PacketRef[src.size()];
        for (int i = 0; i < src.size(); i++) {
            PacketRef s = src.get(i);
            out[i] = new PacketRef(s.payloadOffset, s.payloadSize,
                    s.unixStart - baseUnixUs, s.unixEnd - baseUnixUs, s.numElements, s.firstEventIndex, 0L);
        }
        return out;
    }

    /**
     * Packet table Unix times from a buggy writer jump backward by ~2^32 µs
     * every ~35.8 min (sign-extended camera timestamps). Add 2^32 so seeking
     * and duration use a monotonic long timeline. wrapOffset is applied again
     * when decoding individual event Unix times.
     */
    private PacketRef[] unwrapPacketRefs(PacketRef[] refs) {
        if (refs.length == 0) {
            return refs;
        }
        long wrap = 0;
        long lastEnd = Long.MIN_VALUE;
        PacketRef[] out = new PacketRef[refs.length];
        int wraps = 0;
        for (int i = 0; i < refs.length; i++) {
            PacketRef s = refs[i];
            long start = s.unixStart + wrap;
            long end = s.unixEnd + wrap;
            if (end < start && start - end > TimestampUnwrapper.WRAP_DETECT_US) {
                end += TimestampUnwrapper.UINT32_US;
            }
            if (lastEnd != Long.MIN_VALUE && start < lastEnd
                    && lastEnd - start > TimestampUnwrapper.WRAP_DETECT_US) {
                wrap += TimestampUnwrapper.UINT32_US;
                start += TimestampUnwrapper.UINT32_US;
                end += TimestampUnwrapper.UINT32_US;
                wraps++;
            }
            out[i] = new PacketRef(s.payloadOffset, s.payloadSize, start, end,
                    s.numElements, s.firstEventIndex, wrap);
            lastEnd = end;
        }
        if (wraps > 0) {
            log.info(String.format(
                    "AEDAT-4 unwrapped %d 32-bit timestamp wrap(s) in packet table (duration %s s)",
                    wraps, new EngineeringFormat().format(out[out.length - 1].unixEnd * 1e-6).trim()));
        }
        return out;
    }

    private long playableSize() {
        if (eventRefs.length > 0) {
            return eventCount;
        }
        return timelineTimestamps.length;
    }

    private boolean hasPolarity() {
        return eventRefs.length > 0;
    }

    /**
     * Relative timestamp at an event index using only the sparse packet table
     * (linear interpolation within the packet). <b>No file I/O / decompress</b> —
     * safe for slider seeks and UI while ViewLoop holds the stream lock.
     * Returns a monotonic long (unwrapped); 32-bit {@link #timestampApprox}
     * truncates for the AEFileInputStreamInterface.
     */
    private long timestampApproxLong(long eventIndex) {
        if (!hasPolarity()) {
            int i = (int) Math.max(0, Math.min(eventIndex, timelineTimestamps.length - 1));
            return timelineTimestamps.length == 0 ? 0 : timelineTimestamps[i];
        }
        if (eventCount == 0 || eventRefs.length == 0) {
            return 0;
        }
        long idx = Math.max(0, Math.min(eventIndex, eventCount - 1));
        PacketRef ref = eventRefs[findEventPacket(idx)];
        if (ref.numElements <= 1 || ref.unixEnd <= ref.unixStart) {
            return ref.unixStart;
        }
        double frac = (idx - ref.firstEventIndex) / (double) (ref.numElements - 1);
        if (frac < 0) {
            frac = 0;
        } else if (frac > 1) {
            frac = 1;
        }
        return Math.round(ref.unixStart + frac * (ref.unixEnd - ref.unixStart));
    }

    private int timestampApprox(long eventIndex) {
        return (int) timestampApproxLong(eventIndex);
    }

    /** Exact timestamp via FlatBuffer (decompresses the containing EVTS packet). */
    private int timestampAt(long eventIndex) throws IOException {
        return (int) timestampAtLong(eventIndex);
    }

    /**
     * Unwrapped relative µs of one event from the FlatBuffer (same units as
     * {@code frameRefs.unixStart}). Used to attach APS/IMU to the events actually
     * extracted, not the packet-table interpolation of a Save-As mega-packet.
     */
    private long timestampAtLong(long eventIndex) throws IOException {
        if (!hasPolarity()) {
            return timestampApproxLong(eventIndex);
        }
        if (eventCount == 0) {
            return 0;
        }
        long idx = Math.max(0, Math.min(eventIndex, eventCount - 1));
        int pi = findEventPacket(idx);
        PacketRef ref = eventRefs[pi];
        EventPacket packet = eventPacketAt(pi);
        int local = (int) (idx - ref.firstEventIndex);
        if (local < 0 || local >= packet.elementsLength()) {
            log.warning(String.format(
                    "AEDAT-4 timestampAt local=%d out of elementsLength=%d for EVTS[%d]; using approx",
                    local, packet.elementsLength(), pi));
            return timestampApproxLong(idx);
        }
        return packet.elements(local).timestamp() - baseUnixUs + eventRefs[pi].wrapOffset;
    }

    /** First IMU packet at or after {@code payloadOffset} (file order). */
    private int findImuRefAtOrAfterOffset(long payloadOffset) {
        int lo = 0;
        int hi = imuRefs.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (imuRefs[mid].payloadOffset < payloadOffset) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /** Binary search packet containing global event index. */
    private int findEventPacket(long eventIndex) {
        int lo = 0;
        int hi = eventRefs.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            PacketRef r = eventRefs[mid];
            long start = r.firstEventIndex;
            long end = start + r.numElements;
            if (eventIndex < start) {
                hi = mid - 1;
            } else if (eventIndex >= end) {
                lo = mid + 1;
            } else {
                return mid;
            }
        }
        return Math.max(0, Math.min(eventRefs.length - 1, lo));
    }

    private EventPacket eventPacketAt(int packetIndex) throws IOException {
        if (packetIndex != cachedEventPacketIndex || cachedEventFlat == null) {
            cachedEventFlat = readPayload(eventRefs[packetIndex]);
            cachedEventPacketIndex = packetIndex;
            // Verbose playback trace (re-enable for decode hangs):
            // log.fine(String.format("AEDAT-4 decompress EVTS[%d] payload=%d B", packetIndex, eventRefs[packetIndex].payloadSize));
        }
        ByteBuffer view = cachedEventFlat.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        view.rewind();
        return EventPacket.getSizePrefixedRootAsEventPacket(view);
    }

    private AEPacketRaw extractPolarity(long startIdx, long endIdx) throws IOException {
        if (!hasPolarity() || startIdx >= endIdx) {
            return new AEPacketRaw(0);
        }
        endIdx = Math.min(endIdx, eventCount);
        startIdx = Math.max(0, startIdx);
        if (startIdx >= endIdx) {
            return new AEPacketRaw(0);
        }
        IntGrow addresses = new IntGrow((int) Math.min(Integer.MAX_VALUE, endIdx - startIdx));
        IntGrow timestamps = new IntGrow(addresses.a.length);
        // log.fine(String.format("AEDAT-4 extractPolarity ENTER [%,d,%,d)", startIdx, endIdx));
        int skipped = 0;
        long i = startIdx;
        while (i < endIdx) {
            int pi = findEventPacket(i);
            PacketRef ref = eventRefs[pi];
            EventPacket packet = eventPacketAt(pi);
            int local = (int) (i - ref.firstEventIndex);
            int localEnd = (int) Math.min(ref.numElements, endIdx - ref.firstEventIndex);
            int packetElements = packet.elementsLength();
            if (localEnd > packetElements) {
                log.warning(String.format(
                        "AEDAT-4 extract: localEnd=%d > elementsLength=%d on EVTS[%d] (index says n=%d); clamping",
                        localEnd, packetElements, pi, ref.numElements));
                localEnd = packetElements;
            }
            for (int j = local; j < localEnd; j++) {
                Event event = packet.elements(j);
                if (event == null) {
                    skipped++;
                    continue;
                }
                int address = packAddress(event);
                if (address < 0) {
                    skipped++;
                    continue; // Davis out-of-range
                }
                addresses.add(address);
                int ts32 = emitRelativeTimestamp(event.timestamp(), ref);
                timestamps.add(ts32);
            }
            i = ref.firstEventIndex + Math.max(localEnd, local + 1);
            if (localEnd <= local) {
                // Avoid infinite loop if packet metadata disagrees with FlatBuffer length.
                log.warning("AEDAT-4 extract: no progress in EVTS[" + pi + "], advancing past packet");
                i = ref.firstEventIndex + ref.numElements;
            }
        }
        if (skipped > 0) {
            skippedEventsSinceWarning += skipped;
            long now = System.currentTimeMillis();
            if (now - lastSkippedEventWarningMs >= SKIPPED_EVENT_WARNING_INTERVAL_MS) {
                log.warning(String.format(
                        "AEDAT-4 extractPolarity [%,d,%,d) skipped %d events this slice, %,d since last notice (null/out-of-range)",
                        startIdx, endIdx, skipped, skippedEventsSinceWarning));
                lastSkippedEventWarningMs = now;
                skippedEventsSinceWarning = 0;
            }
        }
        return new AEPacketRaw(addresses.toArray(), timestamps.toArray());
    }

    /**
     * File Unix µs → 32-bit relative timestamp, applying packet wrapOffset and
     * firing {@link AEInputStream#EVENT_WRAPPED_TIME} on signed 32-bit wrap
     * (same as {@code AEFileInputStream} bigWrap, for Info clock correction).
     */
    private int emitRelativeTimestamp(long fileUnixUs, PacketRef ref) {
        long unwrappedUnix = fileUnixUs + ref.wrapOffset;
        long rawStart = ref.unixStart + baseUnixUs - ref.wrapOffset;
        if (fileUnixUs < rawStart && rawStart - fileUnixUs > TimestampUnwrapper.WRAP_DETECT_US) {
            unwrappedUnix += TimestampUnwrapper.UINT32_US;
        }
        int ts32 = (int) (unwrappedUnix - baseUnixUs);
        if (haveEmittedTimestamp && TimestampUnwrapper.isSignedWrapForward(mostRecentEmittedTimestamp, ts32)) {
            support.firePropertyChange(AEInputStream.EVENT_WRAPPED_TIME, mostRecentEmittedTimestamp, ts32);
        }
        haveEmittedTimestamp = true;
        mostRecentEmittedTimestamp = ts32;
        return ts32;
    }

    private int packAddress(Event event) {
        int x = event.x() & 0xffff;
        int y = event.y() & 0xffff;
        int type = event.polarity() ? 1 : 0; // On=1 / Off=0
        EventExtractor2D extractor = chip != null ? chip.getEventExtractor() : null;
        final boolean useDavisPacking = chip instanceof DavisChip;
        int sx1 = chip == null ? 0 : chip.getSizeX() - 1;
        if (useDavisPacking) {
            final int sy = chip == null ? 0 : chip.getSizeY();
            if (x > sx1 || y >= sy) {
                return -1;
            }
            // DavisEventExtractor.extractBundleTyped always sets e.x = sx1 - addrX
            // (USB typed demux writes the same unflipped display X into PacketBundle).
            // Pack addrX = sx1 - fileX so playback restores fileX.
            final int px = sx1 - x;
            // Y: DV/OpenCV is top-left → flip into jAER bottom-origin. jAER-written
            // files already store display Y — leave as-is.
            final int py = dvOpenCvCoordinates ? (sy - 1 - y) : y;
            return DavisChip.ADDRESS_TYPE_DVS
                    | ((px & 0x3ff) << DavisChip.XSHIFT)
                    | ((py & 0x1ff) << DavisChip.YSHIFT)
                    | ((type & 1) << DavisChip.POLSHIFT);
        }
        if (extractor != null) {
            // getAddressFromCell expects jAER display Y (bottom origin), the inverse of
            // extractPacket. DV/OpenCV files store top-left Y — convert first. jAER-written
            // files already store display Y (same as Davis).
            int py = y;
            if (dvOpenCvCoordinates) {
                final int sy = chip.getSizeY();
                if (sy > 1) {
                    py = sy - 1 - y;
                }
            }
            return extractor.getAddressFromCell(x, py, type);
        }
        return (x & 0xffff) | ((y & 0xffff) << 16) | (type << 31);
    }

    private void collectTypedForWindow(long t0, long t1) throws IOException {
        collectTypedForWindow(t0, t1, -1, -1);
    }

    private void collectTypedForWindow(long t0, long t1, long eventStart, long eventEnd) throws IOException {
        pendingFrames.clear();
        pendingImu.clear();
        // Backward jog/seek can move earlier than the last window — rewind typed cursors.
        if (t0 < lastReadT0) {
            frameCursor = 0;
            imuCursor = 0;
        }
        lastReadT0 = t0;
        lastReadT1 = t1;
        while (frameCursor < frameRefs.length && frameRefs[frameCursor].unixEnd < t0) {
            frameCursor++;
        }
        int fi = frameCursor;
        while (fi < frameRefs.length && frameRefs[fi].unixStart <= t1) {
            if (frameRefs[fi].unixEnd >= t0) {
                FramePacket decoded = decodeFrame(frameRefs[fi]);
                pendingFrames.add(decoded);
                if (decoded.isEmpty() && log.isLoggable(Level.FINE)) {
                    log.fine(String.format(
                            "AEDAT-4 collectTyped empty frameRef[%d] relTs=[%d,%d] -> %s",
                            fi, frameRefs[fi].unixStart, frameRefs[fi].unixEnd, decoded));
                }
            }
            fi++;
        }
        while (imuCursor < imuRefs.length && packetHi(imuRefs[imuCursor]) < t0) {
            imuCursor++;
        }
        int ii = imuCursor;
        int afterWindow = 0;
        while (ii < imuRefs.length) {
            PacketRef r = imuRefs[ii];
            long lo = packetLo(r);
            long hi = packetHi(r);
            if (lo <= t1 && hi >= t0) {
                ImuPacket decoded = decodeImu(r, t0, t1);
                if (decoded.getSize() > 0) {
                    pendingImu.add(decoded);
                }
                afterWindow = 0;
            } else if (lo > t1) {
                afterWindow++;
                if (afterWindow >= 8) {
                    break;
                }
            }
            ii++;
        }
        if (pendingImu.isEmpty() && eventStart >= 0 && eventEnd > eventStart
                && Aedat4FileOutputStream.imuHostStamped(chip)) {
            collectImuByEventFileRange(eventStart, eventEnd);
        }
    }

    private static long packetLo(PacketRef r) {
        return Math.min(r.unixStart, r.unixEnd);
    }

    private static long packetHi(PacketRef r) {
        return Math.max(r.unixStart, r.unixEnd);
    }

    /**
     * Mini/Micro host-clock IMU that still does not overlap DVS after record-time
     * rebase: attach IMU packets written beside the EVTS packets (ViewLoop writes
     * polarity then IMU each slice). Not used for Davis — those clocks already
     * share 1 µs ticks; file-order attach poisons Steadicam dt.
     */
    private void collectImuByEventFileRange(long eventStart, long eventEnd) throws IOException {
        if (eventRefs.length == 0 || imuRefs.length == 0 || eventEnd <= eventStart) {
            return;
        }
        int p0 = findEventPacket(eventStart);
        int p1 = findEventPacket(eventEnd - 1);
        long off0 = eventRefs[p0].payloadOffset;
        long off1;
        if (p1 + 1 < eventRefs.length) {
            off1 = eventRefs[p1 + 1].payloadOffset;
        } else {
            off1 = Long.MAX_VALUE;
        }
        int added = 0;
        int i = findImuRefAtOrAfterOffset(off0);
        while (i < imuRefs.length && imuRefs[i].payloadOffset < off1) {
            pendingImu.add(decodeImu(imuRefs[i]));
            added++;
            i++;
        }
        if (added > 0 && !loggedImuOffsetFallback) {
            loggedImuOffsetFallback = true;
            log.info(String.format(
                    "AEDAT-4 Mini/Micro IMU timestamps do not overlap DVS; attaching %d IMU packet(s) by file order so overlay/Steadicam still see gyros",
                    added));
        }
    }

    private FramePacket decodeFrame(PacketRef ref) throws IOException {
        ByteBuffer payload = readPayload(ref);
        Frame frame = Frame.getSizePrefixedRootAsFrame(payload);
        int w = frame.sizeX() & 0xffff;
        int h = frame.sizeY() & 0xffff;
        if (w <= 0 || h <= 0) {
            w = chip != null ? chip.getSizeX() : 0;
            h = chip != null ? chip.getSizeY() : 0;
        }
        final int nbytes = frame.pixelsLength();
        final byte fmt = frame.format();
        final FrameLayout layout = resolveFrameLayout(fmt, w, h, nbytes);
        FramePacket out = new FramePacket(w, h, layout.colorMode);
        out.setTimestampStartUs(ref.unixStart);
        out.setTimestampEndUs(ref.unixEnd);
        out.setExposureUs((int) Math.min(Integer.MAX_VALUE, Math.max(0, frame.exposure())));
        out.setSource(frame.source());
        short[] pixels = out.getPixels();
        final int ch = layout.channels;
        final int srcStride = w * ch * (layout.u16 ? 2 : 1);
        // DV/OpenCV: y=0 at top. jAER Davis pixmap: y=0 at bottom — flip while copying.
        // jAER-written frames are already bottom-origin; copy without Y remap.
        for (int y = 0; y < h; y++) {
            final int srcY = dvOpenCvCoordinates ? (h - 1 - y) : y;
            final int srcRow = srcY * srcStride;
            final int dstRow = y * w * ch;
            for (int x = 0; x < w; x++) {
                final int srcPix = srcRow + x * ch * (layout.u16 ? 2 : 1);
                final int dstPix = dstRow + x * ch;
                if (layout.u16) {
                    for (int c = 0; c < ch; c++) {
                        final int o = srcPix + c * 2;
                        if (o + 1 >= nbytes) {
                            break;
                        }
                        int lo = frame.pixels(o) & 0xff;
                        int hi = frame.pixels(o + 1) & 0xff;
                        pixels[dstPix + c] = (short) (lo | (hi << 8));
                    }
                } else {
                    for (int c = 0; c < ch; c++) {
                        final int o = srcPix + c;
                        if (o >= nbytes) {
                            break;
                        }
                        // Expand 8-bit to jAER's 16-bit-ish APS range (same as mono 8U path).
                        pixels[dstPix + c] = (short) ((frame.pixels(o) & 0xff) << 8);
                    }
                }
                // OpenCV C3/C4 is BGR(A); FramePacket RGB stores R,G,B(,A).
                if (layout.opencvBgr && ch >= 3) {
                    short b = pixels[dstPix];
                    short r = pixels[dstPix + 2];
                    pixels[dstPix] = r;
                    pixels[dstPix + 2] = b;
                }
            }
        }
        int decodeKey = (fmt & 0xff) ^ (w << 8) ^ (h << 20) ^ nbytes ^ (layout.colorMode.ordinal() << 4)
                ^ (layout.u16 ? 1 : 0) ^ (layout.opencvBgr ? 2 : 0) ^ (dvOpenCvCoordinates ? 4 : 0);
        if (decodeKey != lastLoggedDecodeKey && log.isLoggable(Level.FINE)) {
            lastLoggedDecodeKey = decodeKey;
            log.fine(String.format(
                    "AEDAT-4 decodeFrame fmt=%d %dx%d nbytes=%d -> %s u16=%s bgr=%s dvOpenCv=%s",
                    fmt & 0xff, w, h, nbytes, layout.colorMode, layout.u16, layout.opencvBgr, dvOpenCvCoordinates));
        }
        return out;
    }

    /**
     * Maps DV {@link FrameFormat} (+ nbytes fallback) to {@link FramePacket} layout.
     * OpenCV multi-channel frames are BGR(A) in the file.
     */
    private static FrameLayout resolveFrameLayout(byte fmt, int w, int h, int nbytes) {
        final int n = Math.max(0, w) * Math.max(0, h);
        switch (fmt) {
            case FrameFormat.OPENCV_8U_C3:
                return new FrameLayout(FramePacket.ColorMode.RGB, 3, false, true);
            case FrameFormat.OPENCV_16U_C3:
                return new FrameLayout(FramePacket.ColorMode.RGB, 3, true, true);
            case FrameFormat.OPENCV_8U_C4:
                return new FrameLayout(FramePacket.ColorMode.RGBA, 4, false, true);
            case FrameFormat.OPENCV_16U_C4:
                return new FrameLayout(FramePacket.ColorMode.RGBA, 4, true, true);
            case FrameFormat.OPENCV_16U_C1:
                return new FrameLayout(FramePacket.ColorMode.GRAYSCALE, 1, true, false);
            case FrameFormat.OPENCV_8U_C1:
                return new FrameLayout(FramePacket.ColorMode.GRAYSCALE, 1, false, false);
            default:
                break;
        }
        // Infer from payload size when format is missing/unknown (do not treat C3 as u16 C1).
        if (n > 0) {
            if (nbytes == n * 3) {
                return new FrameLayout(FramePacket.ColorMode.RGB, 3, false, true);
            }
            if (nbytes == n * 6) {
                return new FrameLayout(FramePacket.ColorMode.RGB, 3, true, true);
            }
            if (nbytes == n * 4) {
                return new FrameLayout(FramePacket.ColorMode.RGBA, 4, false, true);
            }
            if (nbytes == n * 8) {
                return new FrameLayout(FramePacket.ColorMode.RGBA, 4, true, true);
            }
            if (nbytes >= n * 2) {
                return new FrameLayout(FramePacket.ColorMode.GRAYSCALE, 1, true, false);
            }
        }
        return new FrameLayout(FramePacket.ColorMode.GRAYSCALE, 1, false, false);
    }

    private static final class FrameLayout {
        final FramePacket.ColorMode colorMode;
        final int channels;
        final boolean u16;
        final boolean opencvBgr;

        FrameLayout(FramePacket.ColorMode colorMode, int channels, boolean u16, boolean opencvBgr) {
            this.colorMode = colorMode;
            this.channels = channels;
            this.u16 = u16;
            this.opencvBgr = opencvBgr;
        }
    }

    private ImuPacket decodeImu(PacketRef ref) throws IOException {
        return decodeImu(ref, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    /**
     * @param t0 inclusive relative-µs window start; {@link Long#MIN_VALUE} keeps all
     * @param t1 inclusive relative-µs window end; {@link Long#MAX_VALUE} keeps all
     */
    private ImuPacket decodeImu(PacketRef ref, long t0, long t1) throws IOException {
        ByteBuffer payload = readPayload(ref);
        IMUPacket packet = IMUPacket.getSizePrefixedRootAsIMUPacket(payload);
        int n = packet.elementsLength();
        ImuPacket out = new ImuPacket(Math.max(ImuPacket.DEFAULT_CAPACITY, n));
        for (int i = 0; i < n; i++) {
            IMU imu = packet.elements(i);
            int ts = (int) (imu.timestamp() - baseUnixUs + ref.wrapOffset);
            if ((long) ts < t0 || (long) ts > t1) {
                continue;
            }
            IMUSample sample = out.nextOutput();
            sample.setFromPhysicalUnits(ts,
                    imu.accelerometerX(), imu.accelerometerY(), imu.accelerometerZ(),
                    imu.gyroscopeX(), imu.gyroscopeY(), imu.gyroscopeZ(),
                    imu.temperature());
        }
        return out;
    }

    private ByteBuffer maybeDecompress(ByteBuffer payload) throws IOException {
        if (compression == CompressionType.NONE) {
            return payload;
        }
        byte[] raw = new byte[payload.remaining()];
        int pos = payload.position();
        payload.get(raw);
        payload.position(pos);
        byte[] flat = Aedat4Compression.decompress(raw, compression);
        return ByteBuffer.wrap(flat).order(ByteOrder.LITTLE_ENDIAN);
    }

    /** True if buffer looks like a size-prefixed or bare FileDataTable ("FTAB") FlatBuffer. */
    private static boolean looksLikeFileDataTable(ByteBuffer payload) {
        int p = payload.position();
        int n = payload.remaining();
        if (n >= 4) {
            if (payload.get(p) == 'F' && payload.get(p + 1) == 'T'
                    && payload.get(p + 2) == 'A' && payload.get(p + 3) == 'B') {
                return true;
            }
        }
        if (n >= 12) {
            if (payload.get(p + 8) == 'F' && payload.get(p + 9) == 'T'
                    && payload.get(p + 10) == 'A' && payload.get(p + 11) == 'B') {
                return true;
            }
        }
        return false;
    }

    private ByteBuffer readPayload(PacketRef ref) throws IOException {
        ensureChannelOpen();
        ByteBuffer payload = ByteBuffer.allocate(ref.payloadSize).order(ByteOrder.LITTLE_ENDIAN);
        try {
            channel.position(ref.payloadOffset);
            readFully(channel, payload);
        } catch (ClosedChannelException e) {
            ensureChannelOpen();
            channel.position(ref.payloadOffset);
            readFully(channel, payload);
        }
        payload.flip();
        return maybeDecompress(payload);
    }

    /**
     * Reopen FileChannel if a ViewLoop interrupt closed it
     * ({@link java.nio.channels.ClosedByInterruptException}). Index remains valid.
     */
    private synchronized void ensureChannelOpen() throws IOException {
        if (channel != null && channel.isOpen()) {
            return;
        }
        if (file == null) {
            throw new IOException("AEDAT-4 channel closed and file is null");
        }
        log.info("Reopening AEDAT-4 FileChannel after close/interrupt: " + file.getName());
        if (randomAccessFile != null) {
            try {
                randomAccessFile.close();
            } catch (IOException ignore) {
            }
        }
        randomAccessFile = new RandomAccessFile(file, "r");
        channel = randomAccessFile.getChannel();
        cachedEventPacketIndex = -1;
        cachedEventFlat = null;
    }

    private static ByteBuffer readSizePrefixed(FileChannel channel) throws IOException {
        ByteBuffer sizeBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        readFully(channel, sizeBuffer);
        sizeBuffer.flip();
        int size = sizeBuffer.getInt();
        if (size < 0) {
            throw new IOException("Negative FlatBuffer size prefix " + size);
        }
        ByteBuffer payload = ByteBuffer.allocate(size + 4).order(ByteOrder.LITTLE_ENDIAN);
        payload.putInt(size);
        readFully(channel, payload);
        payload.flip();
        return payload;
    }

    private static void readFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                throw new EOFException();
            }
        }
    }

    private String indexCacheFileName() {
        return String.format("%s.%d.%d.s%d.aedat4idx",
                file.getName(), file.length(), file.lastModified(), eventStreamId);
    }

    private File indexCacheFile() {
        return net.sf.jaer.util.JaerTmpdir.aeidxFile(indexCacheFileName());
    }

    /** Prefer {@code jaer/aeidx}; then {@code jaer/}; then the system-temp root. */
    private File resolveIndexCacheFile() {
        return net.sf.jaer.util.JaerTmpdir.resolveAeidx(indexCacheFileName());
    }

    private boolean maybeLoadCachedIndex(ProgressMonitor progressMonitor) throws IOException {
        File cache = resolveIndexCacheFile();
        if (!cache.isFile() || !cache.canRead() || cache.length() == 0) {
            return false;
        }
        long t0 = System.currentTimeMillis();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(cache), 1 << 20))) {
            if (!INDEX_CACHE_MAGIC.equals(in.readUTF()) || in.readInt() != INDEX_CACHE_VERSION) {
                log.info("AEDAT-4 index cache version mismatch, rebuilding: " + cache);
                return false;
            }
            if (in.readLong() != file.length() || in.readLong() != file.lastModified()) {
                log.info("AEDAT-4 index cache stale, rebuilding: " + cache);
                return false;
            }
            if (in.readInt() != eventStreamId) {
                log.info("AEDAT-4 index cache for different event stream, rebuilding: " + cache);
                return false;
            }
            if (progressMonitor != null) {
                progressMonitor.setNote("Reading cached AEDAT-4 index");
                progressMonitor.setProgress(1);
            }
            baseUnixUs = in.readLong();
            eventCount = in.readLong();
            frameCount = in.readLong();
            imuSampleCount = in.readLong();
            int nTimeline = in.readInt();
            if (nTimeline < 0 || nTimeline > INDEX_CACHE_MAX_TIMELINE) {
                log.warning("AEDAT-4 index cache bad timeline length=" + nTimeline + ", rebuilding");
                return false;
            }
            timelineTimestamps = new int[nTimeline];
            for (int i = 0; i < nTimeline; i++) {
                timelineTimestamps[i] = in.readInt();
            }
            eventRefs = readEventRefs(in);
            frameRefs = readRefs(in);
            imuRefs = readRefs(in);
            markOut = playableSize();
            cachedEventPacketIndex = -1;
            cachedEventFlat = null;
            log.info(String.format(
                    "Loaded sparse AEDAT-4 index from %s in %d ms (stream %d: %,d events in %d packets, %,d frames, %,d IMU, %.1f KB)",
                    cache.getName(), System.currentTimeMillis() - t0, eventStreamId,
                    eventCount, eventRefs.length, frameCount, imuSampleCount, cache.length() / 1024.0));
            return true;
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("canceled")) {
                throw e;
            }
            log.warning("Could not load AEDAT-4 index cache, rebuilding: " + e);
            return false;
        } catch (Exception e) {
            log.warning("Could not load AEDAT-4 index cache, rebuilding: " + e);
            return false;
        }
    }

    private void cacheIndex(ProgressMonitor progressMonitor) throws IOException {
        File cache = indexCacheFile();
        if (progressMonitor != null) {
            progressMonitor.setNote("Writing AEDAT-4 index cache");
            progressMonitor.setProgress(90);
        }
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(cache), 1 << 20))) {
            out.writeUTF(INDEX_CACHE_MAGIC);
            out.writeInt(INDEX_CACHE_VERSION);
            out.writeLong(file.length());
            out.writeLong(file.lastModified());
            out.writeInt(eventStreamId);
            out.writeLong(baseUnixUs);
            out.writeLong(eventCount);
            out.writeLong(frameCount);
            out.writeLong(imuSampleCount);
            out.writeInt(timelineTimestamps.length);
            for (int t : timelineTimestamps) {
                out.writeInt(t);
            }
            writeEventRefs(out, eventRefs);
            writeRefs(out, frameRefs);
            writeRefs(out, imuRefs);
            out.flush();
            log.info(String.format("Cached sparse AEDAT-4 index (%s) to %s (%.1f KB)",
                    file.getName(), cache.getAbsolutePath(), cache.length() / 1024.0));
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("canceled")) {
                if (!cache.delete() && cache.exists()) {
                    log.warning("Could not delete partial AEDAT-4 index cache " + cache);
                }
                throw e;
            }
            log.warning("Could not cache AEDAT-4 index: " + e);
        } catch (Exception e) {
            log.warning("Could not cache AEDAT-4 index: " + e);
        }
    }

    private static PacketRef[] readEventRefs(DataInputStream in) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > INDEX_CACHE_MAX_PACKETS) {
            throw new IOException("bad event packet count " + n);
        }
        PacketRef[] refs = new PacketRef[n];
        for (int i = 0; i < n; i++) {
            refs[i] = new PacketRef(in.readLong(), in.readInt(), in.readLong(), in.readLong(),
                    in.readInt() & 0xffffffffL, in.readLong(), in.readLong());
        }
        return refs;
    }

    private static void writeEventRefs(DataOutputStream out, PacketRef[] refs) throws IOException {
        out.writeInt(refs.length);
        for (PacketRef r : refs) {
            out.writeLong(r.payloadOffset);
            out.writeInt(r.payloadSize);
            out.writeLong(r.unixStart);
            out.writeLong(r.unixEnd);
            out.writeInt((int) Math.min(Integer.MAX_VALUE, r.numElements));
            out.writeLong(r.firstEventIndex);
            out.writeLong(r.wrapOffset);
        }
    }

    private static PacketRef[] readRefs(DataInputStream in) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > INDEX_CACHE_MAX_PACKETS) {
            throw new IOException("bad packet ref count " + n);
        }
        PacketRef[] refs = new PacketRef[n];
        for (int i = 0; i < n; i++) {
            refs[i] = new PacketRef(in.readLong(), in.readInt(), in.readLong(), in.readLong(),
                    in.readInt() & 0xffffffffL, 0, in.readLong());
        }
        return refs;
    }

    private static void writeRefs(DataOutputStream out, PacketRef[] refs) throws IOException {
        out.writeInt(refs.length);
        for (PacketRef r : refs) {
            out.writeLong(r.payloadOffset);
            out.writeInt(r.payloadSize);
            out.writeLong(r.unixStart);
            out.writeLong(r.unixEnd);
            out.writeInt((int) Math.min(Integer.MAX_VALUE, r.numElements));
            out.writeLong(r.wrapOffset);
        }
    }

    @Override
    public synchronized AEPacketRaw readPacketByNumber(int n) throws IOException {
        ensureChannelOpen();
        if (n == 0) {
            n = 1;
        }
        boolean forwards = n > 0;
        ensureReadableOrThrow(forwards);
        long limitOut = effectiveMarkOut();
        long limitIn = markIn;
        long pos0 = position;
        if (forwards) {
            long start = position;
            int cappedN = Math.min(n, MAX_EVENTS_PER_READ);
            long end = Math.min(limitOut, position + cappedN);
            if (start >= end) {
                throw new EOFException();
            }
            position = end;
            long tStart = timestampAtLong(start);
            currentStartTimestamp = (int) tStart;
            long tEnd = timestampAtLong(Math.max(start, end - 1));
            collectTypedForWindow(tStart, tEnd, start, end);
            firePosition();
            AEPacketRaw pkt = extractPolarity(start, end);
            logPlaybackRead("readPacketByNumber n=%d pos %d->%d [%d,%d) events=%d",
                    n, pos0, position, start, end, pkt.getNumEvents());
            return pkt;
        }
        // Backwards: events in [start, position), then move position to start.
        int cappedN = Math.min(-n, MAX_EVENTS_PER_READ);
        long end = position;
        long start = Math.max(limitIn, end - cappedN);
        if (start >= end) {
            throw new EOFException("reached start of file");
        }
        position = start;
        long t0 = timestampAtLong(start);
        long t1 = timestampAtLong(Math.max(start, end - 1));
        currentStartTimestamp = (int) t0;
        collectTypedForWindow(t0, t1, start, end);
        firePosition();
        AEPacketRaw pkt = extractPolarity(start, end);
        logPlaybackRead("readPacketByNumber n=%d (back) pos %d->%d [%d,%d) events=%d",
                n, pos0, position, start, end, pkt.getNumEvents());
        return pkt;
    }

    @Override
    public synchronized AEPacketRaw readPacketByTime(int dt) throws IOException {
        ensureChannelOpen();
        if (dt == 0) {
            dt = 1;
        }
        boolean forwards = dt > 0;
        ensureReadableOrThrow(forwards);
        long limitOut = effectiveMarkOut();
        long limitIn = markIn;
        long pos0 = position;
        // Approx timestamps from packet table only — do not decompress here (slider/UI race).
        if (forwards) {
            long start = position;
            long tStart = timestampApproxLong(start);
            long target = tStart + dt;
            long end;
            if (hasPolarity()) {
                end = findEndIndexByTime(start, target, limitOut);
            } else {
                end = start + 1;
                while (end < limitOut && timelineTimestamps[(int) (end - 1)] <= target) {
                    end++;
                }
            }
            if (end - start > MAX_EVENTS_PER_READ) {
                end = start + MAX_EVENTS_PER_READ;
            }
            if (start >= end) {
                throw new EOFException();
            }
            position = end;
            long t0 = timestampAtLong(start);
            currentStartTimestamp = (int) t0;
            long tEnd = timestampAtLong(Math.max(start, end - 1));
            collectTypedForWindow(t0, tEnd, start, end);
            firePosition();
            AEPacketRaw pkt = extractPolarity(start, end);
            logPlaybackRead("readPacketByTime dt=%d pos %d->%d [%d,%d) t=%d..%d events=%d",
                    dt, pos0, position, start, end, t0, tEnd, pkt.getNumEvents());
            return pkt;
        }
        // Backwards: exclusive end is current position; find start with ts >= target.
        long end = position;
        if (end <= limitIn) {
            throw new EOFException("reached start of file");
        }
        long tEnd = timestampApproxLong(Math.max(limitIn, end - 1));
        long target = tEnd + dt; // dt < 0
        long start;
        if (hasPolarity()) {
            start = findStartIndexByTime(end, target, limitIn);
        } else {
            start = end - 1;
            while (start > limitIn && timelineTimestamps[(int) (start - 1)] >= target) {
                start--;
            }
        }
        if (end - start > MAX_EVENTS_PER_READ) {
            start = end - MAX_EVENTS_PER_READ;
        }
        if (start >= end) {
            throw new EOFException("reached start of file");
        }
        position = start;
        long t0 = timestampAtLong(start);
        currentStartTimestamp = (int) t0;
        long t1 = timestampAtLong(Math.max(start, end - 1));
        collectTypedForWindow(t0, t1, start, end);
        firePosition();
        AEPacketRaw pkt = extractPolarity(start, end);
        logPlaybackRead("readPacketByTime dt=%d (back) pos %d->%d [%d,%d) t=%d..%d target=%d events=%d",
                dt, pos0, position, start, end, t0, t1, target, pkt.getNumEvents());
        return pkt;
    }

    /**
     * Exclusive end index for events with relative timestamp &lt;= {@code target},
     * at least {@code start + 1}, capped by {@code limit}. Packet-table
     * interpolation unless {@link #scanTimesliceInPacket}.
     */
    private long findEndIndexByTime(long start, long target, long limit) throws IOException {
        if (start >= limit) {
            return start;
        }
        if (scanTimesliceInPacket) {
            long dt = target - timestampApproxLong(start);
            return scanEndIndexByActualTime(start, dt, limit);
        }
        long end = start + 1; // at least one event
        int pi = findEventPacket(start);
        while (pi < eventRefs.length && end < limit) {
            PacketRef ref = eventRefs[pi];
            long pktEnd = Math.min(limit, ref.firstEventIndex + ref.numElements);
            if (pktEnd <= start) {
                pi++;
                continue;
            }
            if (ref.unixEnd <= target) {
                end = pktEnd;
                pi++;
                continue;
            }
            if (ref.unixEnd <= ref.unixStart || ref.numElements <= 0) {
                return Math.min(limit, Math.max(end, start + 1));
            }
            double frac = (target - (double) ref.unixStart) / (double) (ref.unixEnd - ref.unixStart);
            if (frac < 0) {
                frac = 0;
            } else if (frac > 1) {
                frac = 1;
            }
            long estExclusive = ref.firstEventIndex + 1
                    + (long) Math.round(frac * Math.max(0, ref.numElements - 1));
            return Math.max(start + 1, Math.min(pktEnd, estExclusive));
        }
        return Math.min(limit, end);
    }

    /**
     * Walk actual event timestamps from {@code start} until {@code origin+dt}.
     * Uses the first event's own clock so packet-table Unix vs FlatBuffer Unix
     * mismatches cannot collapse the slice to one event.
     */
    private long scanEndIndexByActualTime(long start, long dt, long limit) throws IOException {
        long goalDelta = Math.max(1L, dt);
        long i = start;
        long origin = Long.MIN_VALUE;
        long wrap = 0;
        long lastTs = Long.MIN_VALUE;
        int pi = -1;
        EventPacket packet = null;
        int nEl = 0;
        PacketRef ref = null;
        while (i < limit) {
            int p = findEventPacket(i);
            if (p != pi) {
                pi = p;
                ref = eventRefs[pi];
                packet = eventPacketAt(pi);
                nEl = packet.elementsLength();
                wrap = ref.wrapOffset;
                lastTs = Long.MIN_VALUE;
            }
            int local = (int) (i - ref.firstEventIndex);
            if (local < 0 || local >= nEl) {
                i = ref.firstEventIndex + Math.max(1, ref.numElements);
                continue;
            }
            long ts = packet.elements(local).timestamp() - baseUnixUs + wrap;
            if (lastTs != Long.MIN_VALUE && ts < lastTs
                    && lastTs - ts > TimestampUnwrapper.WRAP_DETECT_US) {
                wrap += TimestampUnwrapper.UINT32_US;
                ts += TimestampUnwrapper.UINT32_US;
            }
            lastTs = ts;
            if (origin == Long.MIN_VALUE) {
                origin = ts;
            }
            if (ts - origin > goalDelta) {
                return Math.max(start + 1, i);
            }
            i++;
        }
        return Math.max(start + 1, Math.min(limit, i));
    }

    /**
     * Inclusive start index for a backward window ending at exclusive {@code end},
     * covering events with approx timestamp &gt;= {@code target}, floored by {@code limitIn}.
     * At least one event when {@code end > limitIn}.
     */
    private long findStartIndexByTime(long end, long target, long limitIn) throws IOException {
        if (end <= limitIn) {
            return limitIn;
        }
        if (scanTimesliceInPacket) {
            long dt = target - timestampApproxLong(Math.max(limitIn, end - 1));
            return scanStartIndexByActualTime(end, dt, limitIn);
        }
        long start = end - 1; // at least one event
        int pi = findEventPacket(end - 1);
        while (pi >= 0 && start > limitIn) {
            PacketRef ref = eventRefs[pi];
            long pktStart = ref.firstEventIndex;
            long pktEnd = pktStart + ref.numElements;
            long segStart = Math.max(limitIn, pktStart);
            long segEnd = Math.min(end, pktEnd);
            if (segStart >= segEnd) {
                pi--;
                continue;
            }
            if (ref.unixEnd < target) {
                return start;
            }
            if (ref.unixStart >= target) {
                start = segStart;
                pi--;
                continue;
            }
            if (ref.unixEnd <= ref.unixStart || ref.numElements <= 0) {
                return Math.max(limitIn, Math.min(start, segStart));
            }
            double frac = (target - (double) ref.unixStart) / (double) (ref.unixEnd - ref.unixStart);
            if (frac < 0) {
                frac = 0;
            } else if (frac > 1) {
                frac = 1;
            }
            long estInclusive = ref.firstEventIndex
                    + (long) Math.round(frac * Math.max(0, ref.numElements - 1));
            return Math.max(segStart, Math.min(segEnd - 1, estInclusive));
        }
        return Math.max(limitIn, start);
    }

    /**
     * Walk actual event timestamps backward from exclusive {@code end} until
     * {@code origin+dt} ({@code dt} is negative). Origin is the last event
     * before {@code end}.
     */
    private long scanStartIndexByActualTime(long end, long dt, long limitIn) throws IOException {
        long i = end - 1;
        if (i < limitIn) {
            return limitIn;
        }
        long origin = Long.MIN_VALUE;
        long wrap = 0;
        long lastTs = Long.MIN_VALUE;
        int pi = -1;
        EventPacket packet = null;
        int nEl = 0;
        PacketRef ref = null;
        while (i >= limitIn) {
            int p = findEventPacket(i);
            if (p != pi) {
                pi = p;
                ref = eventRefs[pi];
                packet = eventPacketAt(pi);
                nEl = packet.elementsLength();
                wrap = ref.wrapOffset;
                lastTs = Long.MIN_VALUE;
            }
            int local = (int) (i - ref.firstEventIndex);
            if (local < 0 || local >= nEl) {
                i = ref.firstEventIndex - 1;
                continue;
            }
            long ts = packet.elements(local).timestamp() - baseUnixUs + wrap;
            if (lastTs != Long.MIN_VALUE && ts > lastTs
                    && ts - lastTs > TimestampUnwrapper.WRAP_DETECT_US) {
                wrap -= TimestampUnwrapper.UINT32_US;
                ts -= TimestampUnwrapper.UINT32_US;
            }
            lastTs = ts;
            if (origin == Long.MIN_VALUE) {
                origin = ts;
            }
            if (origin - ts > Math.max(1L, -dt)) {
                return Math.max(limitIn, Math.min(end - 1, i + 1));
            }
            i--;
        }
        return Math.max(limitIn, i + 1);
    }

    private void ensureReadableOrThrow(boolean forwards) throws EOFException {
        if (playableSize() == 0) {
            throw new EOFException("AEDAT-4 file has no playable timeline (no events/frames/IMU)");
        }
        if (forwards) {
            if (position < effectiveMarkOut()) {
                return;
            }
            if (repeat) {
                try {
                    rewind();
                } catch (IOException e) {
                    throw new EOFException(e.toString());
                }
                if (position >= effectiveMarkOut()) {
                    throw new EOFException();
                }
                return;
            }
            throw new EOFException();
        }
        if (position > markIn) {
            return;
        }
        throw new EOFException("reached start of file");
    }

    private long effectiveMarkOut() {
        return Math.min(markOut, playableSize());
    }

    private boolean shouldLogPlaybackFine() {
        if (!log.isLoggable(Level.FINE)) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastPlaybackFineLogMs < PLAYBACK_FINE_INTERVAL_MS) {
            return false;
        }
        lastPlaybackFineLogMs = now;
        return true;
    }

    private void logPlaybackRead(String fmt, Object... args) {
        if (shouldLogPlaybackFine()) {
            log.fine(String.format(fmt, args));
        }
    }

    private void firePosition() {
        support.firePropertyChange(AEInputStream.EVENT_POSITION, null, position);
    }

    @Override
    public boolean isNonMonotonicTimeExceptionsChecked() { return nonMonotonicTimeExceptionsChecked; }

    @Override
    public void setNonMonotonicTimeExceptionsChecked(boolean yes) { nonMonotonicTimeExceptionsChecked = yes; }

    @Override
    public long getAbsoluteStartingTimeMs() { return baseUnixUs / 1000L; }

    @Override
    public ZoneId getZoneId() { return ZoneId.systemDefault(); }

    @Override
    public int getDurationUs() {
        long d = getDurationUsLong();
        if (d > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (d < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) d;
    }

    /** Unwrapped duration in µs (12 h recordings exceed 32-bit). */
    public long getDurationUsLong() {
        if (hasPolarity() && eventRefs.length > 0) {
            return Math.max(0L, eventRefs[eventRefs.length - 1].unixEnd - eventRefs[0].unixStart);
        }
        if (timelineTimestamps.length == 0) {
            return 0;
        }
        return (timelineTimestamps[timelineTimestamps.length - 1] & 0xffffffffL)
                - (timelineTimestamps[0] & 0xffffffffL);
    }

    @Override
    public int getFirstTimestamp() {
        if (hasPolarity()) {
            return eventRefs.length == 0 ? 0 : (int) eventRefs[0].unixStart;
        }
        return timelineTimestamps.length == 0 ? 0 : timelineTimestamps[0];
    }

    @Override
    public PropertyChangeSupport getSupport() { return support; }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) { support.addPropertyChangeListener(listener); }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) { support.removePropertyChangeListener(listener); }

    @Override
    public File getFile() { return file; }

    @Override
    public int getLastTimestamp() {
        if (hasPolarity()) {
            return eventRefs.length == 0 ? 0 : (int) eventRefs[eventRefs.length - 1].unixEnd;
        }
        return timelineTimestamps.length == 0 ? 0 : timelineTimestamps[timelineTimestamps.length - 1];
    }

    @Override
    public int getMostRecentTimestamp() {
        if (playableSize() == 0) {
            return 0;
        }
        long index = Math.max(0, Math.min(position - 1, playableSize() - 1));
        return timestampApprox(index);
    }

    @Override
    public void setFile(File file) { this.file = file; }

    @Override
    public int getTimestampResetBitmask() { return timestampResetBitmask; }

    @Override
    public void setTimestampResetBitmask(int timestampResetBitmask) { this.timestampResetBitmask = timestampResetBitmask; }

    @Override
    public void close() throws IOException {
        try {
            persistMarks();
        } catch (Exception e) {
            log.warning("Could not persist AEDAT-4 marks: " + e);
        }
        cachedEventPacketIndex = -1;
        cachedEventFlat = null;
        if (channel != null) {
            channel.close();
        }
        if (randomAccessFile != null) {
            randomAccessFile.close();
        }
    }

    /**
     * Restores IN/OUT/other marks from the shared preferences cache (same map
     * as AEDAT-2 {@link AEFileInputStream}). Positions are event indices for
     * AEDAT-4. Applies marks to the player slider when available.
     */
    @Override
    public void marksInitialize() {
        Marks saved = AEFileInputStream.marksGetForFile(file);
        if (saved == null) {
            clearMarks();
            return;
        }
        long n = playableSize();
        if (n <= 0) {
            clearMarks();
            return;
        }
        markIn = clampMark(saved.markIn, 0, n);
        long out = saved.markOut;
        if (out == Long.MAX_VALUE || out < 0) {
            out = n;
        }
        markOut = clampMark(out, markIn, n);
        // Legacy / cleared: treat end-of-file OUT as unset
        if (markOut >= n) {
            markOut = n;
        }
        markers.clear();
        if (saved.otherMarks != null) {
            for (Long m : saved.otherMarks) {
                if (m != null && m >= 0 && m < n) {
                    markers.add(m);
                }
            }
        }
        position = markIn;
        final Marks applied = snapshotMarks();
        support.firePropertyChange(AEInputStream.EVENT_MARKS_LOADED, null, applied);
        // Do not call setMarks here: AEPlayer has not assigned aeInputStream yet.
        // AEPlayer.done() applies marks on the EDT after the stream is live.
        log.info(String.format("Restored AEDAT-4 marks for %s: %s", file.getName(), applied));
    }

    /** Current IN/OUT/other marks (for player UI after open). */
    public Marks getPlaybackMarks() {
        return snapshotMarks();
    }

    /** Writes current marks into the shared preferences cache (or clears entry). */
    private void persistMarks() {
        if (file == null) {
            return;
        }
        if (isMarkInSet() || isMarkOutSet() || !markers.isEmpty()) {
            AEFileInputStream.marksPutForFile(file, snapshotMarks());
            log.fine("Persisted AEDAT-4 marks for " + file.getAbsolutePath());
        } else {
            AEFileInputStream.marksPutForFile(file, null);
        }
    }

    private Marks snapshotMarks() {
        Marks m = new Marks();
        m.markIn = markIn;
        m.markOut = markOut;
        m.otherMarks.addAll(markers);
        return m;
    }

    private static long clampMark(long v, long min, long max) {
        if (v < min) {
            return min;
        }
        if (v > max) {
            return max;
        }
        return v;
    }

    @Override
    public int getCurrentStartTimestamp() { return currentStartTimestamp; }

    @Override
    public void setCurrentStartTimestamp(int currentStartTimestamp) { this.currentStartTimestamp = currentStartTimestamp; }

    /**
     * Packet-table binary search to the event index nearest {@code timestampUs}
     * (relative µs). Does not decompress packets.
     */
    @Override
    public synchronized void setPositionFromTimestamp(int timestampUs) {
        position(eventIndexNearestTimestamp(timestampUs & 0xffffffffL));
    }

    /**
     * Packet-table seek using unwrapped relative µs (not truncated to 32-bit).
     */
    public synchronized void setPositionFromTimestampUs(long timestampUs) {
        position(eventIndexNearestTimestamp(timestampUs));
    }

    /**
     * Nearest playable event index for relative timestamp {@code t} using only
     * the sparse packet table (or timeline). No file I/O.
     */
    long eventIndexNearestTimestamp(long t) {
        if (!hasPolarity()) {
            int n = timelineTimestamps.length;
            if (n == 0) {
                return 0;
            }
            int lo = 0;
            int hi = n - 1;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if ((timelineTimestamps[mid] & 0xffffffffL) < t) {
                    lo = mid + 1;
                } else {
                    hi = mid;
                }
            }
            if (lo > 0) {
                long a = timelineTimestamps[lo - 1] & 0xffffffffL;
                long b = timelineTimestamps[lo] & 0xffffffffL;
                if (Math.abs(t - a) <= Math.abs(b - t)) {
                    return lo - 1;
                }
            }
            return lo;
        }
        if (eventRefs.length == 0 || eventCount == 0) {
            return 0;
        }
        if (t <= eventRefs[0].unixStart) {
            return eventRefs[0].firstEventIndex;
        }
        PacketRef last = eventRefs[eventRefs.length - 1];
        if (t >= last.unixEnd) {
            return Math.max(0, eventCount - 1);
        }
        int lo = 0;
        int hi = eventRefs.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (eventRefs[mid].unixEnd < t) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        PacketRef r = eventRefs[lo];
        if (r.numElements <= 1 || r.unixEnd <= r.unixStart) {
            return r.firstEventIndex;
        }
        double frac = (t - r.unixStart) / (double) (r.unixEnd - r.unixStart);
        if (frac < 0) {
            frac = 0;
        } else if (frac > 1) {
            frac = 1;
        }
        long idx = r.firstEventIndex + Math.round(frac * (r.numElements - 1));
        return Math.max(0, Math.min(eventCount - 1, idx));
    }

    @Override
    public boolean toggleMarker() {
        Long here = position;
        boolean added = markers.add(here);
        if (!added) {
            markers.remove(here);
        }
        support.firePropertyChange(AEInputStream.EVENT_MARK_TOGGLED, added ? null : here, added ? here : null);
        return added;
    }

    @Override
    public synchronized boolean jumpToNextMarker() {
        lastJumpTimeMs = System.currentTimeMillis();
        Long next = markers.higher(position);
        if (next == null) {
            return false;
        }
        position(next);
        return true;
    }

    /**
     * Jump to the previous marker strictly before {@link #position}.
     * <p>
     * If another marker jump happened within 2&nbsp;s (typically right after
     * {@link #jumpToNextMarker()}), skip that just-reached marker and go to the
     * one before it — otherwise {@code lower(position)} returns the marker we
     * are already on/just past and the jump looks like a no-op.
     */
    @Override
    public synchronized boolean jumpToPrevMarker() {
        Long prev = markers.lower(position);
        if (prev == null) {
            return false;
        }
        if (System.currentTimeMillis() - lastJumpTimeMs <= 2000) {
            Long earlier = markers.lower(prev);
            if (earlier != null) {
                prev = earlier;
            }
        }
        lastJumpTimeMs = System.currentTimeMillis();
        position(prev);
        return true;
    }

    @Override
    public float getFractionalPosition() {
        long n = playableSize();
        return n == 0 ? 0 : (float) position / n;
    }

    /**
     * Fraction of unwrapped recording duration at {@link #position()}.
     */
    public float getFractionalTimePosition() {
        long dur = getDurationUsLong();
        if (dur <= 0 || eventRefs.length == 0) {
            return getFractionalPosition();
        }
        long n = playableSize();
        if (position <= 0) {
            return 0;
        }
        if (n > 0 && position >= n) {
            return 1;
        }
        float f = (float) ((timestampApproxLong(Math.min(position, Math.max(0, n - 1)))
                - eventRefs[0].unixStart) / (double) dur);
        if (f < 0) {
            return 0;
        }
        if (f > 1) {
            return 1;
        }
        return f;
    }

    /**
     * Seek to the packet-table event nearest {@code frac} of recording duration.
     */
    public void setFractionalTimePosition(float frac) {
        frac = Math.max(0, Math.min(1, frac));
        long dur = getDurationUsLong();
        if (dur <= 0 || eventRefs.length == 0) {
            setFractionalPosition(frac);
            return;
        }
        long t = eventRefs[0].unixStart + (long) (frac * dur);
        position(eventIndexNearestTimestamp(t));
    }

    @Override
    public long position() { return position; }

    @Override
    public synchronized void position(long n) {
        long old = position;
        position = Math.max(markIn, Math.min(n, effectiveMarkOut()));
        // Packet-table approx only — never decompress on slider seek (that hung ViewLoop).
        long t = playableSize() == 0 ? 0
                : timestampApproxLong(Math.min(Math.max(0, position), playableSize() - 1));
        currentStartTimestamp = (int) t;
        haveEmittedTimestamp = false;
        // log.fine(String.format("AEDAT-4 position %d->%d approxTs=%d", old, position, t));
        frameCursor = 0;
        imuCursor = 0;
        while (frameCursor < frameRefs.length && frameRefs[frameCursor].unixEnd < t) {
            frameCursor++;
        }
        while (imuCursor < imuRefs.length && imuRefs[imuCursor].unixEnd < t) {
            imuCursor++;
        }
        support.firePropertyChange(AEInputStream.EVENT_REPOSITIONED, old, position);
        // Player slider listens for EVENT_POSITION (not EVENT_REPOSITIONED).
        if (old != position) {
            firePosition();
        }
    }

    @Override
    public void rewind() throws IOException {
        long old = position;
        position = markIn;
        frameCursor = 0;
        imuCursor = 0;
        haveEmittedTimestamp = false;
        support.firePropertyChange(AEInputStream.EVENT_REWOUND, old, position);
    }

    @Override
    public void setFractionalPosition(float frac) {
        position((long) (Math.max(0, Math.min(1, frac)) * playableSize()));
    }

    @Override
    public long size() { return playableSize(); }

    @Override
    public void clearMarks() {
        long[] oldMarks = new long[]{markIn, markOut};
        markIn = 0;
        markOut = playableSize();
        markers.clear();
        support.firePropertyChange(AEInputStream.EVENT_MARKS_CLEARED, oldMarks, new long[]{markIn, markOut});
    }

    @Override
    public long setMarkIn() {
        if (position <= markOut) {
            long old = markIn;
            markIn = position;
            support.firePropertyChange(AEInputStream.EVENT_MARK_IN_SET, old, markIn);
        }
        return markIn;
    }

    @Override
    public long setMarkOut() {
        if (position > markIn) {
            long old = markOut;
            markOut = position;
            support.firePropertyChange(AEInputStream.EVENT_MARK_OUT_SET, old, markOut);
        }
        return markOut;
    }

    @Override
    public long getMarkInPosition() { return markIn; }

    @Override
    public long getMarkOutPosition() { return markOut; }

    @Override
    public boolean isMarkInSet() { return markIn != 0; }

    @Override
    public boolean isMarkOutSet() { return markOut != playableSize(); }

    @Override
    public void setRepeat(boolean repeat) { this.repeat = repeat; }

    @Override
    public boolean isRepeat() { return repeat; }

    /** Growable int buffer used while extracting a playback slice. */
    private static final class IntGrow {
        int[] a;
        int size;

        IntGrow(int capacityHint) {
            a = new int[Math.max(16, capacityHint)];
        }

        void add(int v) {
            if (size == a.length) {
                a = Arrays.copyOf(a, a.length * 2);
            }
            a[size++] = v;
        }

        int[] toArray() {
            return size == a.length ? a : Arrays.copyOf(a, size);
        }
    }

    /**
     * Packet payload location. For polarity packets, {@link #unixStart}/{@link #unixEnd}
     * are relative µs after indexing (unwrapped / monotonic);
     * {@link #firstEventIndex} is the global event offset.
     * {@link #wrapOffset} is added to file Unix timestamps when decoding events.
     */
    private static final class PacketRef {
        final long payloadOffset;
        final int payloadSize;
        final long unixStart;
        final long unixEnd;
        final long numElements;
        final long firstEventIndex;
        /** Add to {@code event.timestamp() - baseUnixUs} (0 unless the file wrapped). */
        final long wrapOffset;

        PacketRef(long payloadOffset, int payloadSize, long unixStart, long unixEnd,
                long numElements, long firstEventIndex) {
            this(payloadOffset, payloadSize, unixStart, unixEnd, numElements, firstEventIndex, 0L);
        }

        PacketRef(long payloadOffset, int payloadSize, long unixStart, long unixEnd,
                long numElements, long firstEventIndex, long wrapOffset) {
            this.payloadOffset = payloadOffset;
            this.payloadSize = payloadSize;
            this.unixStart = unixStart;
            this.unixEnd = unixEnd;
            this.numElements = numElements;
            this.firstEventIndex = firstEventIndex;
            this.wrapOffset = wrapOffset;
        }
    }
}
