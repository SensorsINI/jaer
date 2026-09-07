package net.sf.jaer.eventio.export;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
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

    public static final String PROP_PROGRESS = "saveAsProgress";
    public static final String PROP_STATUS = "saveAsStatus";

    private final SaveAsOptions options;

    public SaveAsExporter(SaveAsOptions options) {
        this.options = options;
    }

    File getOutputFile() {
        return options != null ? options.outputFile : null;
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
                }
            }
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
                try {
                    long absMs = stream.getAbsoluteStartingTimeMs();
                    if (absMs > 0) {
                        baseUs = absMs * 1000L;
                    }
                } catch (Exception e) {
                    log.log(Level.FINE, "No absolute start time for AEDAT-4 export", e);
                }
                aedat4 = new Aedat4FileOutputStream(options.outputFile, chip, options.aedat4Compression, baseUs);
            }
            if (options.writeImu) {
                imu = new ImuCsvSink(options.imuFile(), source);
            }
            boolean needAssembler = options.writeFrames
                    || (aedat4 != null && chip instanceof DavisBaseCamera);
            if (options.writeFrames) {
                int maxAdc = chip instanceof DavisChip ? ((DavisChip) chip).getMaxADC() : DavisChip.MAX_ADC;
                frames = new FramePngSink(options.framesDir(), source, maxAdc);
            }
            if (needAssembler && chip instanceof DavisBaseCamera) {
                assembler = new DavisFrameAssembler((DavisBaseCamera) chip);
            }
            long range = Math.max(1, end == Long.MAX_VALUE ? Math.max(1, stream.size()) : end - start);
            long badEvents = 0;
            int stuckSlices = 0;
            setProgress(0);
            publish("Exporting…");
            while (!isCancelled()) {
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
                    if (options.applyEventFilters && chain != null) {
                        bundle = chain.filterBundle(bundle);
                        if (bundle == null) {
                            continue;
                        }
                    }
                    badEvents += markOutOfBounds(bundle, chip);
                    if (aedat4 != null) {
                        aedat4.writeBundle(toTypedBundle(bundle, assembler, chip, true), true);
                    } else {
                        for (TypedDataPacket p : bundle) {
                            consume(p, csv, h5, imu, frames, assembler, chip);
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
                int pct = (int) Math.min(99, (100L * Math.max(0, stream.position() - start)) / range);
                setProgress(pct);
                long nEv = eventsWritten(csv, h5, aedat4);
                if (badEvents > 0) {
                    publish(String.format("Exported %,d events, skipped %,d bad (%.0f%%)", nEv, badEvents, (double) pct));
                } else {
                    publish(String.format("Exported %,d events (%.0f%%)", nEv, (double) pct));
                }
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
