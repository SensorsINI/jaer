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
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.TypedDataPacket;
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.eventio.aedat4.Aedat4FileInputStream;
import net.sf.jaer.eventio.dsec.DsecHdf5AEOutputStream;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.AEViewer;

/**
 * Offline File → Save As scan: pause playback, iterate the input stream, write
 * CSV or DSEC HDF5 (plus optional HVS sidecars), restore position.
 */
public final class SaveAsExporter extends SwingWorker<SaveAsExporter.Result, String> {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    /** Events per {@code readPacketByNumber} slice (not render timeslice). */
    private static final int SLICE_EVENTS = 250_000;

    public static final String PROP_PROGRESS = "saveAsProgress";
    public static final String PROP_STATUS = "saveAsStatus";

    private final AEViewer viewer;
    private final SaveAsOptions options;

    public SaveAsExporter(AEViewer viewer, SaveAsOptions options) {
        this.viewer = viewer;
        this.options = options;
    }

    @Override
    protected Result doInBackground() throws Exception {
        AEChip chip = viewer.getChip();
        if (chip == null) {
            throw new IOException("No AEChip");
        }
        AEFileInputStreamInterface stream = viewer.getAePlayer() != null
                ? viewer.getAePlayer().getAEInputStream() : null;
        if (stream == null) {
            throw new IOException("No playback file is open");
        }
        boolean wasPaused = viewer.isPaused();
        boolean wasRepeat = stream.isRepeat();
        boolean wasMono = stream.isNonMonotonicTimeExceptionsChecked();
        long savedPos = stream.position();
        long savedMarkIn = stream.getMarkInPosition();
        long savedMarkOut = stream.getMarkOutPosition();
        boolean savedInSet = stream.isMarkInSet();
        boolean savedOutSet = stream.isMarkOutSet();
        boolean restoreAedat2Marks = false;
        boolean restoreAedat4Marks = false;
        boolean subSaved = chip.getEventExtractor() != null && chip.getEventExtractor().isSubsamplingEnabled();
        CsvEventSink csv = null;
        DsecHdf5AEOutputStream h5 = null;
        ImuCsvSink imu = null;
        FramePngSink frames = null;
        DavisFrameAssembler assembler = null;
        try {
            viewer.setPaused(true);
            // Let ViewLoop finish the current grabInput before we seek the stream.
            try {
                Thread.sleep(150);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            stream.setRepeat(false);
            // Seeking backward from playback would otherwise throw NonMonotonicTimeException
            // on the first event (position() does not reset mostRecentTimestamp).
            stream.setNonMonotonicTimeExceptionsChecked(false);
            if (chip.getEventExtractor() != null) {
                chip.getEventExtractor().setSubsamplingEnabled(false);
            }
            long start = 0;
            long end = stream.size();
            if (options.useInOutMarkers) {
                if (stream.isMarkInSet()) {
                    start = stream.getMarkInPosition();
                }
                if (stream.isMarkOutSet()) {
                    end = stream.getMarkOutPosition();
                }
            } else if (savedInSet || savedOutSet) {
                // Stream readPacketByNumber still stops at OUT even when the checkbox is off.
                if (stream instanceof AEFileInputStream) {
                    AEFileInputStream.Marks m = ((AEFileInputStream) stream).getMarks();
                    m.markIn = 0;
                    m.markOut = Long.MAX_VALUE;
                    restoreAedat2Marks = true;
                } else {
                    stream.clearMarks();
                    restoreAedat4Marks = true;
                }
                start = 0;
                end = stream.size();
            }
            if (end <= start) {
                throw new IOException("OUT marker is not after IN marker");
            }
            stream.position(start);
            if (stream instanceof AEFileInputStream) {
                ((AEFileInputStream) stream).setMostRecentTimestamp(Integer.MIN_VALUE);
            }
            stream.setCurrentStartTimestamp(Integer.MIN_VALUE);
            log.info(String.format("Save As %s: events [%d, %d) of %d, filters=%s, markers=%s",
                    options.format, start, end, stream.size(),
                    options.applyEventFilters, options.useInOutMarkers));
            File source = stream.getFile();
            if (options.format == SaveAsOptions.Format.CSV) {
                csv = new CsvEventSink(options.outputFile, options.csvFormatter, source);
            } else {
                h5 = new DsecHdf5AEOutputStream(options.outputFile, options.sensorWidth, options.sensorHeight);
            }
            if (options.writeImu) {
                imu = new ImuCsvSink(options.imuFile(), source);
            }
            if (options.writeFrames) {
                int maxAdc = chip instanceof DavisChip ? ((DavisChip) chip).getMaxADC() : DavisChip.MAX_ADC;
                frames = new FramePngSink(options.framesDir(), source, maxAdc);
                if (chip instanceof DavisBaseCamera) {
                    assembler = new DavisFrameAssembler((DavisBaseCamera) chip);
                }
            }
            FilterChain chain = chip.getFilterChain();
            long range = Math.max(1, end - start);
            setProgress(0);
            publish("Exporting…");
            while (!isCancelled()) {
                long pos = stream.position();
                if (pos >= end) {
                    break;
                }
                int n = (int) Math.min(SLICE_EVENTS, end - pos);
                if (n <= 0) {
                    break;
                }
                AEPacketRaw raw;
                try {
                    raw = stream.readPacketByNumber(n);
                } catch (EOFException eof) {
                    break;
                }
                if (raw == null || raw.getNumEvents() == 0) {
                    // Empty slice with no progress: OUT/EOF or a stuck non-monotonic event.
                    if (stream.position() <= pos) {
                        break;
                    }
                    continue;
                }
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
                for (TypedDataPacket p : bundle) {
                    consume(p, csv, h5, imu, frames, assembler, chip);
                }
                int pct = (int) Math.min(99, (100L * (stream.position() - start)) / range);
                setProgress(pct);
                long nEv = csv != null ? csv.getEventsWritten() : (h5 != null ? h5.getEventsWritten() : 0);
                publish(String.format("Exported %,d events (%.0f%%)", nEv, (double) pct));
            }
            if (isCancelled()) {
                throw new CancellationException("Save As cancelled");
            }
            Result result = new Result();
            result.outputFile = options.outputFile;
            result.events = csv != null ? csv.getEventsWritten() : (h5 != null ? h5.getEventsWritten() : 0);
            result.imuSamples = imu != null ? imu.getSamplesWritten() : 0;
            result.frames = frames != null ? frames.getFramesWritten() : 0;
            result.cancelled = false;
            // Flush sinks before returning so HDF5 close failures are not reported as success.
            closeSink(csv);
            csv = null;
            closeSink(h5);
            h5 = null;
            closeSink(imu);
            imu = null;
            closeSink(frames);
            frames = null;
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
            closeQuietly(imu);
            closeQuietly(frames);
            try {
                if (restoreAedat2Marks && stream instanceof AEFileInputStream) {
                    AEFileInputStream.Marks m = ((AEFileInputStream) stream).getMarks();
                    m.markIn = savedMarkIn;
                    m.markOut = savedMarkOut;
                } else if (restoreAedat4Marks) {
                    restoreMarks(stream, savedMarkIn, savedMarkOut, savedInSet, savedOutSet);
                }
                stream.position(savedPos);
            } catch (Exception e) {
                log.log(Level.WARNING, "Could not restore playback position", e);
            }
            stream.setRepeat(wasRepeat);
            stream.setNonMonotonicTimeExceptionsChecked(wasMono);
            if (chip.getEventExtractor() != null) {
                chip.getEventExtractor().setSubsamplingEnabled(subSaved);
            }
            viewer.setPaused(wasPaused);
        }
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

    private static void restoreMarks(AEFileInputStreamInterface stream, long in, long out,
            boolean inSet, boolean outSet) {
        if (!inSet && !outSet) {
            return;
        }
        long here = stream.position();
        if (inSet) {
            stream.position(in);
            stream.setMarkIn();
        }
        if (outSet) {
            stream.position(out);
            stream.setMarkOut();
        }
        stream.position(here);
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
        public boolean cancelled;
    }
}
