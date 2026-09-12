package net.sf.jaer.eventio.export;

import java.awt.Point;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingWorker;

import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.DavisBaseCamera;
import eu.seebetter.ini.chips.davis.DavisFrameAssembler;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.FramePacket;
import net.sf.jaer.event.ImuPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PacketType;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.TypedDataPacket;
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.eventio.aedat4.Aedat4FileInputStream;
import net.sf.jaer.eventio.aedat4.Aedat4FileOutputStream;
import net.sf.jaer.eventio.dsec.DsecHdf5AEOutputStream;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.filter.LowpassFilter;

/**
 * Offline File → Save As scan: open a detached input stream on a headless chip
 * copy so AEViewer can keep playing or open another file. Writes AEDAT-4, CSV,
 * or DSEC HDF5 (plus optional HVS sidecars).
 */
public final class SaveAsExporter extends SwingWorker<SaveAsExporter.Result, String> {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    /**
     * Events per {@code readPacketByNumber} slice (not render timeslice).
     * Keep this well below {@link Aedat4FileInputStream}'s mega-packet scan
     * threshold so a marked IN/OUT export stays event/frame interpolated.
     */
    private static final int SLICE_EVENTS = 8_192;
    /** Dialog / taskbar PropertyChange cadence (SwingWorker {@code progress} + status). */
    private static final long UI_INTERVAL_NS = 500_000_000L;
    /** Smooth coverage/sec so a fast FRAME/IMU burst does not make ETA jump to 0s. */
    private static final float ETA_RATE_TAU_MS = 4_000f;
    private static final long ETA_MIN_ELAPSED_NS = 2_000_000_000L;

    public static final String PROP_PROGRESS = "saveAsProgress";
    public static final String PROP_STATUS = "saveAsStatus";

    private final SaveAsOptions options;
    private long clipStart;
    private long clipEndExclusive;
    private long clipRange;
    private long covered;
    private long eventsIn;
    private long exportStartNs;
    private long lastUiNs;
    private final LowpassFilter etaRateLp = new LowpassFilter(ETA_RATE_TAU_MS);
    private long lastRateCovered;
    private long lastRateNs;

    public SaveAsExporter(SaveAsOptions options) {
        this.options = options;
    }

    File getOutputFile() {
        return options != null ? options.outputFile : null;
    }

    /** True when output would truncate the source recording (same path). */
    static boolean sameRecordingPath(File a, File b) {
        if (a == null || b == null) {
            return false;
        }
        try {
            return a.toPath().toAbsolutePath().normalize()
                    .equals(b.toPath().toAbsolutePath().normalize());
        } catch (Exception e) {
            return a.getAbsoluteFile().equals(b.getAbsoluteFile());
        }
    }

    @Override
    protected Result doInBackground() throws Exception {
        Thread.currentThread().setName("jaer-save-as");
        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        if (options.sourceFile == null || !options.sourceFile.isFile()) {
            throw new IOException("No source recording to export");
        }
        if (options.chipClass == null) {
            throw new IOException("No AEChip class for export");
        }
        options.outputFile = SaveAsOptions.ensureFormatExtension(options.outputFile, options.format);
        if (sameRecordingPath(options.outputFile, options.sourceFile)) {
            throw new IOException("Cannot overwrite a playing recording; choose a different output file");
        }
        AEChip chip = constructHeadlessChip(options.chipClass);
        AEFileInputStreamInterface stream = null;
        final String sourceFileInfo = options.sourceFileInfo != null ? options.sourceFileInfo : "";
        CsvEventSink csv = null;
        DsecHdf5AEOutputStream h5 = null;
        Aedat4FileOutputStream aedat4 = null;
        ImuCsvSink imu = null;
        FramePngSink frames = null;
        DavisFrameAssembler assembler = null;
        try {
            if (chip.getEventExtractor() != null) {
                chip.getEventExtractor().setSubsamplingEnabled(false);
            }
            publish("Opening source for export…");
            stream = chip.openDetachedFileInputStream(options.sourceFile, options.aedat4EventStreamId);
            stream.setRepeat(false);
            stream.setNonMonotonicTimeExceptionsChecked(false);
            long start = Math.max(0L, options.rangeStart);
            long end = options.rangeEnd;
            if (end <= start) {
                if (options.useInOutMarkers) {
                    throw new IOException("OUT marker is not after IN marker");
                }
                end = Long.MAX_VALUE;
            }
            if (end != Long.MAX_VALUE && stream.size() > 0 && end > stream.size()) {
                end = stream.size();
            }
            stream.position(start);
            if (chip instanceof DavisBaseCamera) {
                ((DavisBaseCamera) chip).resetUsbApsAssembler();
            }
            if (stream instanceof AEFileInputStream) {
                AEFileInputStream aedat = (AEFileInputStream) stream;
                aedat.setMostRecentTimestamp(Integer.MIN_VALUE);
                aedat.setCurrentStartTimestamp(Integer.MIN_VALUE);
            }
            FilterChain chain = null;
            if (options.applyEventFilters) {
                chain = chip.getFilterChain();
                if (chain != null) {
                    chain.setFilteringEnabled(options.filterChainGloballyEnabled);
                    prepareExportFilters(chain);
                }
            }
            // RotateFilter invertX&&invertY used to swap DAVIS APS SOF/EOF corners
            // on the export chip; restore after each filterBundle so the next
            // extractBundleTyped slice still sees original readout addresses.
            final Point apsFirst = copyPoint(chip instanceof DavisBaseCamera
                    ? ((DavisBaseCamera) chip).getApsFirstPixelReadOut() : null);
            final Point apsLast = copyPoint(chip instanceof DavisBaseCamera
                    ? ((DavisBaseCamera) chip).getApsLastPixelReadOut() : null);
            log.info(String.format("Save As %s (background): events [%d, %d) of %d, filters=%s, markers=%s, source=%s",
                    options.format, start, end, stream.size(),
                    options.applyEventFilters, options.useInOutMarkers, options.sourceFile.getName()));
            File source = options.sourceFile;
            if (options.format == SaveAsOptions.Format.CSV) {
                csv = new CsvEventSink(options.outputFile, options.csvFormatter, source);
            } else if (options.format == SaveAsOptions.Format.DSEC_H5) {
                h5 = new DsecHdf5AEOutputStream(options.outputFile, options.sensorWidth, options.sensorHeight);
            } else {
                long baseUs = 0;
                if (stream instanceof Aedat4FileInputStream) {
                    baseUs = ((Aedat4FileInputStream) stream).getBaseUnixUs();
                } else {
                    try {
                        long absMs = stream.getAbsoluteStartingTimeMs();
                        if (absMs > 0) {
                            baseUs = absMs * 1000L;
                        }
                    } catch (Exception e) {
                        log.log(Level.FINE, "No absolute start time for AEDAT-4 export", e);
                    }
                }
                aedat4 = new Aedat4FileOutputStream(options.outputFile, chip, options.aedat4Compression, baseUs,
                        stream.getZoneId());
            }
            if (options.writeImu) {
                imu = new ImuCsvSink(options.imuFile(), source);
            }
            final boolean sourceIsAedat4 = stream instanceof Aedat4FileInputStream;
            final boolean sourceHasTypedFrames = sourceIsAedat4
                    && ((Aedat4FileInputStream) stream).hasFramePackets();
            // AEDAT-4 already has sensor APS as FRME packets. Do not run
            // DavisFrameAssembler on leftover mixed APS samples — that invents
            // extra frames and duplicates the originals (e.g. 39 → 91).
            boolean needAssembler = (options.writeFrames || aedat4 != null)
                    && chip instanceof DavisBaseCamera
                    && !sourceHasTypedFrames
                    && !(aedat4 != null && sourceIsAedat4);
            if (options.writeFrames) {
                int maxAdc = chip instanceof DavisChip ? ((DavisChip) chip).getMaxADC() : DavisChip.MAX_ADC;
                frames = new FramePngSink(options.framesDir(), source, maxAdc);
            }
            if (needAssembler) {
                assembler = new DavisFrameAssembler((DavisBaseCamera) chip);
            } else if (sourceHasTypedFrames && chip instanceof DavisBaseCamera) {
                log.info("Save As: copying indexed AEDAT-4 APS frames; not re-assembling from mixed APS samples");
            }
            initClipProgress(start, end, stream.size());
            long badEvents = 0;
            int stuckSlices = 0;
            setProgress(0);
            publish("Exporting…");
            if (aedat4 != null && sourceIsAedat4) {
                exportAedat4RecordOrder((Aedat4FileInputStream) stream, aedat4, chip, chain, start, end);
            }
            while (!isCancelled() && !(aedat4 != null && sourceIsAedat4)) {
                long pos = stream.position();
                if (pos >= end) {
                    break;
                }
                int n = (int) Math.min(SLICE_EVENTS, end == Long.MAX_VALUE ? SLICE_EVENTS : end - pos);
                if (n <= 0) {
                    break;
                }
                AEPacketRaw raw;
                try {
                    raw = stream.readPacketByNumber(n);
                } catch (EOFException eof) {
                    break;
                } catch (RuntimeException | IOException ex) {
                    badEvents += skipBadSlice(stream, pos, end, n, ex);
                    if (stream.position() <= pos) {
                        stuckSlices++;
                        if (stuckSlices > 10_000) {
                            throw new IOException("Save As stuck after too many bad slices at position " + pos, ex);
                        }
                    } else {
                        stuckSlices = 0;
                    }
                    continue;
                }
                if (raw == null || raw.getNumEvents() == 0) {
                    if (stream.position() <= pos) {
                        break;
                    }
                    continue;
                }
                try {
                    PacketBundle bundle = chip.getEventExtractor().extractBundle(raw);
                    if (stream instanceof Aedat4FileInputStream) {
                        if (bundle == null) {
                            bundle = new PacketBundle();
                        }
                        ((Aedat4FileInputStream) stream).appendTypedPackets(bundle);
                    }
                    if (bundle == null || bundle.isEmpty()) {
                        continue;
                    }
                    eventsIn += polarityCount(bundle, false);
                    // Assemble leftover mixed APS using original x/y, then filter
                    // so RotateFilter remaps FramePacket pixels instead of APS AE.
                    bundle = toTypedBundle(bundle, assembler, chip, false);
                    if (options.applyEventFilters && chain != null) {
                        bundle = chain.filterBundle(bundle);
                        restoreApsReadoutCorners(chip, apsFirst, apsLast);
                        if (bundle == null) {
                            continue;
                        }
                    }
                    badEvents += markOutOfBounds(bundle, chip);
                    if (aedat4 != null) {
                        aedat4.writeBundle(toTypedBundle(bundle, null, chip, true), true);
                    } else {
                        for (TypedDataPacket p : bundle) {
                            consume(p, csv, h5, imu, frames, null, chip);
                        }
                    }
                } catch (RuntimeException | IOException ex) {
                    badEvents += skipBadSlice(stream, pos, end, n, ex);
                    if (stream.position() <= pos) {
                        stuckSlices++;
                        if (stuckSlices > 10_000) {
                            throw new IOException("Save As stuck after too many bad slices at position " + pos, ex);
                        }
                    } else {
                        stuckSlices = 0;
                    }
                    continue;
                }
                stuckSlices = 0;
                covered = Math.max(0L, Math.min(clipRange, stream.position() - clipStart));
                reportUi(false, eventsWritten(csv, h5, aedat4), badEvents);
            }
            if (isCancelled()) {
                throw new CancellationException("Save As cancelled");
            }
            Result result = new Result();
            result.outputFile = options.outputFile;
            result.events = eventsWritten(csv, h5, aedat4);
            result.imuSamples = aedat4 != null ? aedat4.getImuSamplesWritten()
                    : (imu != null ? imu.getSamplesWritten() : 0);
            result.frames = aedat4 != null ? aedat4.getFramesWritten()
                    : (frames != null ? frames.getFramesWritten() : 0);
            result.badEvents = badEvents;
            result.cancelled = false;
            result.sourceFileInfo = sourceFileInfo;
            result.outputFileInfo = snapshotOutputFileInfo(aedat4, csv, h5,
                    result.events, result.frames, result.imuSamples);
            if (badEvents > 0) {
                log.warning(String.format("Save As skipped %,d bad events while writing %s",
                        badEvents, options.outputFile.getName()));
            }
            closeSink(csv);
            csv = null;
            closeSink(h5);
            h5 = null;
            closeSink(aedat4);
            aedat4 = null;
            closeSink(imu);
            imu = null;
            closeSink(frames);
            frames = null;
            result.outputFileInfo = appendFileSizeSummary(result.outputFileInfo,
                    options.sourceFileBytes, options.outputFile);
            return result;
        } catch (CancellationException cancel) {
            if (h5 != null) {
                h5.abort();
            }
            throw cancel;
        } finally {
            if (isCancelled() && h5 != null) {
                h5.abort();
            }
            closeQuietly(csv);
            closeQuietly(h5);
            closeQuietly(aedat4);
            closeQuietly(imu);
            closeQuietly(frames);
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception e) {
                    log.log(Level.WARNING, "Could not close Save As source stream", e);
                }
            }
            cleanupHeadlessChip(chip);
        }
    }

    /**
     * Headless chip constructs filters with {@code filterEnabled=false}. FilterFrame
     * is what normally calls {@link EventFilter#setPreferredEnabledState()}; copy
     * the playback chip's enabled flags instead so Save As matches the dialog.
     */
    private void prepareExportFilters(FilterChain chain) {
        chain.initFilters();
        java.util.HashSet<String> on = new java.util.HashSet<>();
        if (options.enabledFilterClassNames != null) {
            on.addAll(options.enabledFilterClassNames);
        }
        if (!on.isEmpty()) {
            for (EventFilter2D f : chain) {
                if (f != null) {
                    f.setFilterEnabledForProcessing(on.contains(f.getClass().getName()));
                }
            }
        } else {
            for (EventFilter2D f : chain) {
                if (f != null) {
                    f.setPreferredEnabledState();
                }
            }
        }
        StringBuilder names = new StringBuilder();
        int n = 0;
        for (EventFilter2D f : chain) {
            if (f != null && f.isFilterEnabled()) {
                if (n > 0) {
                    names.append(", ");
                }
                names.append(f.getClass().getSimpleName());
                n++;
            }
        }
        log.info(n == 0
                ? "Save As: Apply EventFilters is on but no filter is enabled on the export chip"
                : "Save As applying " + n + " EventFilter(s): " + names);
    }

    private static AEChip constructHeadlessChip(Class<? extends AEChip> clazz) throws IOException {
        ChipCanvas.beginPreviewHeadless();
        try {
            return clazz.getConstructor().newInstance();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IOException("Could not construct " + clazz.getSimpleName() + " for Save As: " + cause, cause);
        } finally {
            ChipCanvas.endPreviewHeadless();
        }
    }

    private static void cleanupHeadlessChip(AEChip chip) {
        if (chip == null) {
            return;
        }
        try {
            FilterChain chain = chip.getFilterChain();
            if (chain != null) {
                for (EventFilter2D f : chain) {
                    if (f != null) {
                        try {
                            f.cleanup();
                        } catch (Exception e) {
                            log.log(Level.FINE, "Save As filter cleanup", e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.log(Level.FINE, "Save As headless chip cleanup", e);
        }
    }

    /**
     * Original recording summary captured before the export scan. AEDAT-4 uses
     * {@link Aedat4FileInputStream#getFileInfo()}; other formats get path +
     * {@link Object#toString()}.
     */
    static String snapshotSourceFileInfo(AEFileInputStreamInterface stream) {
        if (stream == null) {
            return "";
        }
        if (stream instanceof Aedat4FileInputStream) {
            String info = stream.getFileInfo();
            return info != null ? info : "";
        }
        StringBuilder sb = new StringBuilder();
        File f = stream.getFile();
        if (f != null) {
            sb.append(f.getAbsolutePath()).append('\n');
        }
        sb.append(stream);
        return sb.toString();
    }

    /** Saved-file summary, matching the recording-finished confirmation when AEDAT-4. */
    private static String snapshotOutputFileInfo(Aedat4FileOutputStream aedat4,
            CsvEventSink csv, DsecHdf5AEOutputStream h5, long events, long frames, long imuSamples) {
        if (aedat4 != null) {
            return aedat4.toString();
        }
        StringBuilder sb = new StringBuilder();
        if (csv != null) {
            sb.append(String.format("CSV/text: %,d events", events));
        } else if (h5 != null) {
            sb.append(String.format("DSEC HDF5: %,d events", events));
        } else {
            sb.append(String.format("%,d events", events));
        }
        if (frames > 0) {
            sb.append(String.format(", %,d frames", frames));
        }
        if (imuSamples > 0) {
            sb.append(String.format(", %,d IMU samples", imuSamples));
        }
        return sb.toString();
    }

    /**
     * Trailing line: original on-disk size, exported size (engineering, 1 digit),
     * and exported/original as percent and ratio.
     */
    static String appendFileSizeSummary(String info, long originalBytes, File out) {
        EngineeringFormat eng = new EngineeringFormat();
        eng.setPrecision(1);
        String line;
        long exported = (out != null && out.isFile()) ? out.length() : -1;
        if (originalBytes > 0 && exported >= 0) {
            String orig = eng.format((double) originalBytes).trim();
            String exp = eng.format((double) exported).trim();
            double pct = 100.0 * exported / (double) originalBytes;
            double ratio = exported > 0 ? originalBytes / (double) exported : 0;
            line = String.format("Files: %sB original -> %sB exported (%.0f%% of original, %.1f:1)",
                    orig, exp, pct, ratio);
        } else if (exported >= 0) {
            line = String.format("Size: %sB", eng.format((double) exported).trim());
        } else {
            return info;
        }
        if (info == null || info.isEmpty()) {
            return line;
        }
        return info + "\n\n" + line;
    }

    /**
     * Advance past a corrupt slice so export can continue. Returns a count of
     * skipped items (at least 1).
     */
    private static long skipBadSlice(AEFileInputStreamInterface stream, long pos, long end, int requested,
            Exception ex) {
        log.log(Level.WARNING, String.format(
                "Save As skipping bad data at position %,d (slice %,d): %s", pos, requested, ex.toString()),
                ex);
        if (stream.position() <= pos) {
            try {
                long next = pos + 1;
                if (end != Long.MAX_VALUE) {
                    next = Math.min(next, end);
                }
                stream.position(next);
            } catch (Exception e) {
                log.log(Level.WARNING, "Save As could not advance past bad slice", e);
            }
        }
        return 1;
    }

    /**
     * Copy EVTS/FRME/IMUS in file order. Does not reslice events or assemble
     * Davis APS. Filters and IN/OUT only rewrite EVTS packets that need it.
     */
    private void exportAedat4RecordOrder(Aedat4FileInputStream in, Aedat4FileOutputStream out,
            AEChip chip, FilterChain chain, long start, long end) throws IOException {
        in.setPolarityEventSkip(0);
        List<Aedat4FileInputStream.RecordedPacket> packets = in.packetsInRecordOrder();
        final boolean filter = options.applyEventFilters && chain != null;
        int totalInRange = 0;
        for (Aedat4FileInputStream.RecordedPacket packet : packets) {
            if (in.inEventIndexRange(packet, start, end)) {
                totalInRange++;
            }
        }
        log.info(String.format(
                "Save As AEDAT-4 record-order: %d packets (%d in IN/OUT range), events [%d, %d), filters=%s",
                packets.size(), totalInRange, start, end, filter));
        final long baseUnixUs = in.getBaseUnixUs();
        for (Aedat4FileInputStream.RecordedPacket packet : packets) {
            if (isCancelled()) {
                throw new CancellationException("Save As cancelled");
            }
            if (!in.inEventIndexRange(packet, start, end)) {
                continue;
            }
            if (packet.kind == Aedat4FileInputStream.RecordedPacket.Kind.EVENTS) {
                long begin = Math.max(clipStart, packet.firstEventIndex);
                covered = Math.max(covered, Math.max(0L, begin - clipStart));
            }
            switch (packet.kind) {
                case FRAME -> out.writeCopiedPacket(Aedat4FileOutputStream.STREAM_FRAMES,
                        in.readUncompressedPayload(packet), packet.numElements,
                        packet.fileUnixStart(baseUnixUs), packet.fileUnixEnd(baseUnixUs));
                case IMU -> out.writeCopiedPacket(Aedat4FileOutputStream.STREAM_IMU,
                        in.readUncompressedPayload(packet), packet.numElements,
                        packet.fileUnixStart(baseUnixUs), packet.fileUnixEnd(baseUnixUs));
                case EVENTS -> {
                    if (!filter && packet.eventsFullyInside(start, end)) {
                        eventsIn += packet.numElements;
                        out.writeCopiedPacket(Aedat4FileOutputStream.STREAM_EVENTS,
                                in.readUncompressedPayload(packet), packet.numElements,
                                packet.fileUnixStart(baseUnixUs), packet.fileUnixEnd(baseUnixUs));
                    } else {
                        writeRecordedEvents(in, out, chip, chain, filter, packet, start, end);
                    }
                    long pos = Math.min(clipEndExclusive, packet.firstEventIndex + packet.numElements);
                    covered = Math.max(covered, Math.max(0L, pos - clipStart));
                }
            }
            reportUi(false, out.getEventsWritten(), 0);
        }
    }

    private void writeRecordedEvents(Aedat4FileInputStream in, Aedat4FileOutputStream out,
            AEChip chip, FilterChain chain, boolean filter,
            Aedat4FileInputStream.RecordedPacket packet, long start, long end) throws IOException {
        AEPacketRaw raw = in.extractRecordedEvents(packet, start, end);
        if (raw == null || raw.getNumEvents() == 0) {
            return;
        }
        PacketBundle bundle = chip.getEventExtractor().extractBundle(raw);
        if (bundle == null || bundle.isEmpty()) {
            return;
        }
        eventsIn += polarityCount(bundle, false);
        if (filter) {
            bundle = chain.filterBundle(bundle);
            if (bundle == null) {
                return;
            }
        }
        markOutOfBounds(bundle, chip);
        out.writeBundle(polarityOnly(toTypedBundle(bundle, null, chip, true)), true);
    }

    /** Drop frames/IMU decoded from mixed APS samples; those stay as copied FRME/IMUS. */
    private static PacketBundle polarityOnly(PacketBundle bundle) {
        if (bundle == null) {
            return bundle;
        }
        PacketBundle out = new PacketBundle();
        for (TypedDataPacket p : bundle) {
            if (p instanceof FramePacket || p instanceof ImuPacket) {
                continue;
            }
            if (p != null && !p.isEmpty()) {
                out.add(p);
            }
        }
        return out;
    }

    private static Point copyPoint(Point p) {
        return p == null ? null : new Point(p);
    }

    /** Undo RotateFilter invertX&&invertY swapping DAVIS APS SOF/EOF corners. */
    private static void restoreApsReadoutCorners(AEChip chip, Point first, Point last) {
        if (!(chip instanceof DavisBaseCamera) || first == null || last == null) {
            return;
        }
        DavisBaseCamera davis = (DavisBaseCamera) chip;
        davis.setApsFirstPixelReadOut(first);
        davis.setApsLastPixelReadOut(last);
    }

    /** Marks polarity events outside the chip as filteredOut. Returns how many. */
    private static int markOutOfBounds(PacketBundle bundle, AEChip chip) {
        if (bundle == null || chip == null) {
            return 0;
        }
        int w = chip.getSizeX();
        int h = chip.getSizeY();
        if (w <= 0 || h <= 0) {
            return 0;
        }
        int bad = 0;
        for (TypedDataPacket p : bundle) {
            if (!(p instanceof EventPacket)) {
                continue;
            }
            EventPacket<?> ep = (EventPacket<?>) p;
            int size = ep.getSize();
            for (int i = 0; i < size; i++) {
                BasicEvent e = ep.getEvent(i);
                if (e == null || e.isFilteredOut()) {
                    continue;
                }
                if (e instanceof ApsDvsEvent) {
                    ApsDvsEvent aps = (ApsDvsEvent) e;
                    if (aps.isApsData() || aps.isImuSample()) {
                        continue;
                    }
                }
                if (e.x < 0 || e.x >= w || e.y < 0 || e.y >= h) {
                    e.setFilteredOut(true);
                    bad++;
                }
            }
        }
        return bad;
    }

    private static long eventsWritten(CsvEventSink csv, DsecHdf5AEOutputStream h5,
            Aedat4FileOutputStream aedat4) {
        if (aedat4 != null) {
            return aedat4.getEventsWritten();
        }
        if (csv != null) {
            return csv.getEventsWritten();
        }
        return h5 != null ? h5.getEventsWritten() : 0;
    }

    private void initClipProgress(long start, long end, long streamSize) {
        clipStart = Math.max(0L, start);
        if (end == Long.MAX_VALUE || end <= clipStart) {
            clipEndExclusive = Math.max(clipStart + 1, streamSize);
        } else {
            clipEndExclusive = end;
        }
        clipRange = Math.max(1L, clipEndExclusive - clipStart);
        covered = 0;
        eventsIn = 0;
        exportStartNs = System.nanoTime();
        lastUiNs = 0;
        lastRateCovered = 0;
        lastRateNs = exportStartNs;
        etaRateLp.reset();
    }

    /**
     * 0–99 progress of {@code position} in {@code [start, end)}. IN/OUT clips
     * start at 0% even when IN is mid-file.
     */
    static int clipProgressPercent(long start, long endExclusive, long position) {
        long range = Math.max(1L, endExclusive - start);
        long covered = Math.max(0L, Math.min(range, position - start));
        return (int) Math.min(99, (100L * covered) / range);
    }

    static String formatCompactDurationMs(long durationMs) {
        long sec = Math.max(0L, durationMs) / 1000L;
        if (sec < 60) {
            return sec + "s";
        }
        long min = sec / 60L;
        sec = sec % 60L;
        if (min < 60) {
            return String.format("%dm %02ds", min, sec);
        }
        long h = min / 60L;
        min = min % 60L;
        return String.format("%dh %02dm", h, min);
    }

    /**
     * Remaining time from average rate. Uses double so {@code elapsed * remaining}
     * cannot overflow {@code long} (that showed as {@code ETA 0s} at ~tens of %).
     */
    static long etaRemainingNs(long elapsedNs, long covered, long range) {
        if (covered <= 0 || range <= 0 || elapsedNs <= 0) {
            return -1L;
        }
        long remaining = range - covered;
        if (remaining <= 0) {
            return 0L;
        }
        return (long) (elapsedNs * (remaining / (double) covered));
    }

    static String formatEta(long remainingNs) {
        if (remainingNs < 0) {
            return null;
        }
        if (remainingNs > 0 && remainingNs < 1_000_000_000L) {
            remainingNs = 1_000_000_000L;
        }
        return "ETA " + formatCompactDurationMs(remainingNs / 1_000_000L);
    }

    /** @deprecated test helper wrapping {@link #etaRemainingNs} */
    static String formatEta(long elapsedNs, long covered, long range) {
        if (elapsedNs < 1_000_000_000L) {
            return null;
        }
        return formatEta(etaRemainingNs(elapsedNs, covered, range));
    }

    private static int polarityCount(PacketBundle bundle, boolean keptOnly) {
        if (bundle == null) {
            return 0;
        }
        int n = 0;
        for (TypedDataPacket p : bundle) {
            if (p == null || p.getPacketType() != PacketType.POLARITY || !(p instanceof EventPacket)) {
                continue;
            }
            EventPacket<?> ep = (EventPacket<?>) p;
            if (ep instanceof ApsDvsEventPacket) {
                Iterator<?> it = ((ApsDvsEventPacket<?>) ep).fullIterator();
                while (it.hasNext()) {
                    Object o = it.next();
                    if (!(o instanceof ApsDvsEvent e)) {
                        continue;
                    }
                    if (e.isApsData() || e.isImuSample()) {
                        continue;
                    }
                    if (keptOnly && e.isFilteredOut()) {
                        continue;
                    }
                    n++;
                }
            } else {
                n += keptOnly ? ep.getSizeNotFilteredOut() : ep.getSize();
            }
        }
        return n;
    }

    private void reportUi(boolean force, long eventsWritten, long badEvents) {
        long now = System.nanoTime();
        if (!force && lastUiNs != 0 && now - lastUiNs < UI_INTERVAL_NS) {
            return;
        }
        lastUiNs = now;
        int pct = clipProgressPercent(clipStart, clipEndExclusive, clipStart + covered);
        setProgress(pct);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Exported %,d events (%d%%)", eventsWritten, pct));
        if (options.applyEventFilters && eventsIn > 0) {
            double filtered = 100.0 * Math.max(0L, eventsIn - eventsWritten) / (double) eventsIn;
            sb.append(String.format(", filtered out %.0f%%", Math.min(100.0, filtered)));
        }
        if (badEvents > 0) {
            sb.append(String.format(", skipped %,d bad", badEvents));
        }
        String eta = formatEta(etaRemainingNsSmoothed(now));
        if (eta != null) {
            sb.append(", ").append(eta);
        }
        publish(sb.toString());
    }

    /**
     * Instantaneous coverage/sec through {@link LowpassFilter} (first sample
     * initializes the state). Falls back to mean rate. Never uses overflowing
     * {@code long} multiply.
     */
    private long etaRemainingNsSmoothed(long nowNs) {
        long elapsedNs = nowNs - exportStartNs;
        if (elapsedNs < ETA_MIN_ELAPSED_NS || covered <= 0) {
            return -1L;
        }
        long remaining = clipRange - covered;
        if (remaining <= 0) {
            return 0L;
        }
        long dtNs = nowNs - lastRateNs;
        long dCovered = covered - lastRateCovered;
        if (dtNs > 0 && dCovered > 0) {
            float perSec = (float) (dCovered * 1_000_000_000d / dtNs);
            // First filter() call sets lpVal = perSec (initialized=false).
            etaRateLp.filter(perSec, nowNs / 1000L);
            lastRateCovered = covered;
            lastRateNs = nowNs;
        }
        float rate = etaRateLp.isInitialized() ? etaRateLp.getValue() : 0f;
        long avgNs = etaRemainingNs(elapsedNs, covered, clipRange);
        if (rate > 1e-3f) {
            long lpNs = (long) (remaining / rate * 1_000_000_000d);
            // Prefer the slower estimate so a fast burst cannot collapse ETA to 0s.
            return Math.max(avgNs, lpNs);
        }
        return avgNs;
    }

    /**
     * AEDAT-4 {@link Aedat4FileOutputStream#writeBundle} writes polarity /
     * {@link FramePacket} / {@link ImuPacket}. Split leftover mixed
     * {@link ApsDvsEventPacket}s so APS/IMU are not dropped or stored as DVS.
     */
    private PacketBundle toTypedBundle(PacketBundle bundle, DavisFrameAssembler assembler, AEChip chip,
            boolean skipFilteredOut) {
        if (bundle == null) {
            return bundle;
        }
        boolean mixed = false;
        for (TypedDataPacket p : bundle) {
            if (p instanceof ApsDvsEventPacket) {
                mixed = true;
                break;
            }
        }
        if (!mixed) {
            return bundle;
        }
        PacketBundle out = new PacketBundle();
        EventPacket<PolarityEvent> polarity = new EventPacket<>(PolarityEvent.class);
        ImuPacket imuPkt = new ImuPacket();
        OutputEventIterator<PolarityEvent> polOut = polarity.outputIterator();
        boolean rolling = chip instanceof DavisBaseCamera
                && ((DavisBaseCamera) chip).getDavisConfig() != null
                && !((DavisBaseCamera) chip).getDavisConfig().isGlobalShutter();
        DavisBaseCamera camera = chip instanceof DavisBaseCamera ? (DavisBaseCamera) chip : null;
        for (TypedDataPacket p : bundle) {
            if (p instanceof ApsDvsEventPacket) {
                Iterator<?> it = ((ApsDvsEventPacket<?>) p).fullIterator();
                while (it.hasNext()) {
                    Object o = it.next();
                    if (!(o instanceof ApsDvsEvent)) {
                        continue;
                    }
                    ApsDvsEvent e = (ApsDvsEvent) o;
                    if (skipFilteredOut && e.isFilteredOut()) {
                        continue;
                    }
                    if (e.isImuSample()) {
                        IMUSample s = e.getImuSample();
                        if (s != null) {
                            imuPkt.appendCopy(s);
                        }
                        continue;
                    }
                    if (e.isApsData()) {
                        if (assembler != null && camera != null
                                && (e.isResetRead() || e.isSignalRead())) {
                            boolean pixFirst = camera.firstFrameAddress(e.x, e.y);
                            boolean pixLast = camera.lastFrameAddress(e.x, e.y);
                            FramePacket completed = assembler.process(e.getAdcSample(), e.timestamp, e.x, e.y,
                                    e.getReadoutType(), pixFirst, pixLast, rolling);
                            if (completed != null) {
                                out.add(completed);
                            }
                        }
                        continue;
                    }
                    PolarityEvent dst = polOut.nextOutput();
                    dst.copyFrom(e);
                }
            } else if (p != null && !p.isEmpty()) {
                out.add(p);
            }
        }
        if (!polarity.isEmpty()) {
            out.add(polarity);
        }
        if (!imuPkt.isEmpty()) {
            out.add(imuPkt);
        }
        return out;
    }

    private void consume(TypedDataPacket p, CsvEventSink csv, DsecHdf5AEOutputStream h5,
            ImuCsvSink imu, FramePngSink frames, DavisFrameAssembler assembler, AEChip chip)
            throws IOException {
        if (p instanceof FramePacket) {
            if (frames != null) {
                frames.write((FramePacket) p);
            }
            return;
        }
        if (p instanceof ImuPacket) {
            if (imu != null) {
                ImuPacket ip = (ImuPacket) p;
                for (int i = 0; i < ip.getSize(); i++) {
                    imu.write(ip.get(i));
                }
            }
            return;
        }
        if (p instanceof ApsDvsEventPacket) {
            consumeMixed((ApsDvsEventPacket<?>) p, csv, h5, imu, frames, assembler, chip);
            return;
        }
        if (p instanceof EventPacket) {
            @SuppressWarnings("unchecked")
            EventPacket<BasicEvent> ep = (EventPacket<BasicEvent>) p;
            for (BasicEvent be : ep) {
                writePolarity(be, csv, h5);
            }
        }
    }

    private void consumeMixed(ApsDvsEventPacket<?> packet, CsvEventSink csv, DsecHdf5AEOutputStream h5,
            ImuCsvSink imu, FramePngSink frames, DavisFrameAssembler assembler, AEChip chip)
            throws IOException {
        boolean rolling = chip instanceof DavisBaseCamera
                && ((DavisBaseCamera) chip).getDavisConfig() != null
                && !((DavisBaseCamera) chip).getDavisConfig().isGlobalShutter();
        DavisBaseCamera camera = chip instanceof DavisBaseCamera ? (DavisBaseCamera) chip : null;
        Iterator<?> it = packet.fullIterator();
        while (it.hasNext()) {
            Object o = it.next();
            if (!(o instanceof ApsDvsEvent)) {
                continue;
            }
            ApsDvsEvent e = (ApsDvsEvent) o;
            if (e.isFilteredOut()) {
                continue;
            }
            if (e.isImuSample()) {
                IMUSample s = e.getImuSample();
                if (imu != null && s != null) {
                    imu.write(s);
                }
                continue;
            }
            if (e.isApsData()) {
                if (frames != null && assembler != null && camera != null
                        && (e.isResetRead() || e.isSignalRead())) {
                    boolean pixFirst = camera.firstFrameAddress(e.x, e.y);
                    boolean pixLast = camera.lastFrameAddress(e.x, e.y);
                    FramePacket completed = assembler.process(e.getAdcSample(), e.timestamp, e.x, e.y,
                            e.getReadoutType(), pixFirst, pixLast, rolling);
                    if (completed != null) {
                        frames.write(completed);
                    }
                }
                continue;
            }
            writePolarity(e, csv, h5);
        }
    }

    private void writePolarity(BasicEvent be, CsvEventSink csv, DsecHdf5AEOutputStream h5) throws IOException {
        if (be == null || be.isFilteredOut() || !(be instanceof PolarityEvent)) {
            return;
        }
        if (be instanceof ApsDvsEvent) {
            ApsDvsEvent aps = (ApsDvsEvent) be;
            if (aps.isApsData() || aps.isImuSample()) {
                return;
            }
        }
        PolarityEvent pe = (PolarityEvent) be;
        if (csv != null) {
            csv.write(pe);
        } else if (h5 != null) {
            h5.write(pe);
        }
    }

    @Override
    protected void process(java.util.List<String> chunks) {
        if (!chunks.isEmpty()) {
            firePropertyChange(PROP_STATUS, null, chunks.get(chunks.size() - 1));
        }
    }

    @Override
    protected void done() {
        // listeners on the worker itself (progress) already fire
    }

    private static void closeSink(AutoCloseable c) throws Exception {
        if (c != null) {
            c.close();
        }
    }

    private static void closeQuietly(AutoCloseable c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (Exception e) {
            log.log(Level.WARNING, "Error closing export sink", e);
        }
    }

    public static final class Result {
        public File outputFile;
        public long events;
        public long imuSamples;
        public long frames;
        public long badEvents;
        public boolean cancelled;
        /** Original open recording summary (AEDAT-4 {@code getFileInfo()} when applicable). */
        public String sourceFileInfo;
        /** Written file summary ({@link Aedat4FileOutputStream#toString()} when AEDAT-4). */
        public String outputFileInfo;
    }
}
