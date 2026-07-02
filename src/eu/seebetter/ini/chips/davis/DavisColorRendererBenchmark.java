/*
 * Headless benchmark for DavisColorRenderer.processColorFrame / endFrame.
 * Streams an existing .aedat recording (same pattern as DvsSliceAviWriter).
 */
package eu.seebetter.ini.chips.davis;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.EventExtractor2D;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEFileInputStream;

/**
 * Benchmark CDAVIS color demosaic using real APS frames from an aedat file.
 *
 * @author tobi
 */
public class DavisColorRendererBenchmark {

    private static final Logger log = Logger.getLogger(DavisColorRendererBenchmark.class.getName());

    private static final String DEFAULT_AEDAT = System.getProperty("user.home")
            + "/Downloads/CDAVIS-2025-02-21T10-20-49+0100-CDAV0002-0 tobi lab cdaviis red cam 1.aedat";

    private static final int WARMUP_ITERATIONS = 10;
    private static final int DEFAULT_ITERATIONS = 50;
    private static final int DEFAULT_MAX_FRAMES = 1;

    public static void main(String[] args) {
        log.setLevel(Level.INFO);
        log.setUseParentHandlers(true);

        OptionParser parser = new OptionParser();
        OptionSpec<Integer> iterationsOpt = parser.accepts("iterations").withRequiredArg().ofType(Integer.class)
                .defaultsTo(DEFAULT_ITERATIONS);
        OptionSpec<Integer> maxFramesOpt = parser.accepts("maxframes").withRequiredArg().ofType(Integer.class)
                .defaultsTo(DEFAULT_MAX_FRAMES);
        OptionSpec<Boolean> awbOpt = parser.accepts("awb").withRequiredArg().ofType(Boolean.class).defaultsTo(true);
        OptionSpec<Boolean> ccOpt = parser.accepts("cc").withRequiredArg().ofType(Boolean.class).defaultsTo(true);
        OptionSpec<Boolean> monoOpt = parser.accepts("monochrome").withRequiredArg().ofType(Boolean.class).defaultsTo(false);
        OptionSpec<String> aedatOpt = parser.nonOptions();

        OptionSet options;
        try {
            options = parser.parse(args);
        } catch (Exception ex) {
            printUsage();
            System.exit(1);
            return;
        }

        List<?> files = options.valuesOf(aedatOpt);
        String aedatPath = files.isEmpty() ? DEFAULT_AEDAT : (String) files.get(0);
        File aedatFile = new File(aedatPath);
        if (!aedatFile.isFile()) {
            log.severe("Input file not found: " + aedatFile.getAbsolutePath());
            System.exit(1);
        }

        final int iterations = options.valueOf(iterationsOpt);
        final int maxFrames = options.valueOf(maxFramesOpt);
        final boolean awb = options.valueOf(awbOpt);
        final boolean cc = options.valueOf(ccOpt);
        final boolean monochrome = options.valueOf(monoOpt);

        log.info("CDAVIS color renderer benchmark");
        log.info("  input: " + aedatFile.getAbsolutePath());
        log.info("  iterations/frame: " + iterations + ", capture frames: " + maxFrames);
        log.info("  awb=" + awb + " colorCorrection=" + cc + " monochrome=" + monochrome);

        final CDAVIS chip = new CDAVIS();
        final CaptureRenderer renderer = new CaptureRenderer(chip);
        chip.setRenderer(renderer);
        configureVideoControl(chip, awb, cc, monochrome);
        finishChipSetup(chip, renderer);

        try {
            loadPreDemosaicFrames(chip, renderer, aedatFile, maxFrames);
        } catch (IOException ex) {
            log.severe("Failed to read recording: " + ex.toString());
            System.exit(1);
        }

        if (renderer.preDemosaicFrames.isEmpty()) {
            log.severe("No APS frames captured from recording");
            System.exit(1);
        }

        log.info("Captured " + renderer.preDemosaicFrames.size() + " pre-demosaic frame(s) from recording");

        log.info("Checking legacy vs refactored output equivalence...");
        verifyLegacyEquivalence(renderer, renderer.preDemosaicFrames.get(0), awb, cc, monochrome);

        final int nFrames = renderer.preDemosaicFrames.size();
        log.info("Timing legacy processColorFrame (" + iterations + " iterations x " + nFrames
                + " frames; legacy is slower, please wait)...");
        long legacyProcessColorNs = 0;
        int frameIdx = 0;
        for (float[] frame : renderer.preDemosaicFrames) {
            legacyProcessColorNs += timeLegacyProcessColorFrame(renderer, frame, iterations, awb, cc, monochrome);
            frameIdx++;
            if (nFrames > 1) {
                log.info("  legacy frame " + frameIdx + "/" + nFrames + " done");
            }
        }
        legacyProcessColorNs /= nFrames;

        log.info("Timing refactored processColorFrame...");
        long processColorNs = 0;
        frameIdx = 0;
        for (float[] frame : renderer.preDemosaicFrames) {
            processColorNs += timeProcessColorFrame(renderer, frame, iterations);
            frameIdx++;
            if (nFrames > 1) {
                log.info("  refactored frame " + frameIdx + "/" + nFrames + " done");
            }
        }
        processColorNs /= nFrames;

        log.info("Timing endFrame (incl. pixmap copy)...");
        long endFrameNs = 0;
        for (float[] frame : renderer.preDemosaicFrames) {
            endFrameNs += timeEndFrame(renderer, frame, iterations);
        }
        endFrameNs /= nFrames;

        final double legacyUs = legacyProcessColorNs / (1000.0 * iterations);
        final double processUs = processColorNs / (1000.0 * iterations);
        final double speedup = legacyProcessColorNs / (double) processColorNs;

        log.info(String.format("legacy processColorFrame:  %.2f us/frame (mean over %d captured frames, %d iters each)",
                legacyUs, nFrames, iterations));
        log.info(String.format("refactored processColorFrame: %.2f us/frame", processUs));
        log.info(String.format("speedup (legacy / refactored): %.2fx", speedup));
        log.info(String.format("endFrame (incl. pixmap copy): %.2f us/frame",
                endFrameNs / (1000.0 * iterations)));
    }

    private static void finishChipSetup(final CDAVIS chip, final CaptureRenderer renderer) {
        if (chip.getFilterChain() != null) {
            chip.getFilterChain().initFilters();
        }
        renderer.allocatePixmaps();
        if (renderer.benchmarkSizeX() <= 0 || renderer.benchmarkSizeY() <= 0) {
            log.severe(String.format(
                    "Chip/renderer not sized after init (chip %dx%d, renderer %dx%d); "
                            + "initFilters() must run after chip construction completes",
                    chip.getSizeX(), chip.getSizeY(), renderer.benchmarkSizeX(), renderer.benchmarkSizeY()));
            System.exit(1);
        }
        log.info(String.format("  chip size: %dx%d, renderer size: %dx%d, texture: %dx%d",
                chip.getSizeX(), chip.getSizeY(), renderer.benchmarkSizeX(), renderer.benchmarkSizeY(),
                renderer.textureWidth, renderer.textureHeight));
    }

    private static void configureVideoControl(final CDAVIS chip, final boolean awb, final boolean cc, final boolean monochrome) {
        final DavisConfig.VideoControl vc = chip.getDavisConfig().getVideoControl();
        vc.setSeparateAPSByColor(false);
        vc.setAutoWhiteBalance(awb);
        vc.setColorCorrection(cc);
        vc.setMonochrome(monochrome);
    }

    private static void loadPreDemosaicFrames(final CDAVIS chip, final CaptureRenderer renderer,
            final File aedatFile, final int maxFrames) throws IOException {
        renderer.preDemosaicFrames.clear();
        renderer.maxCapture = maxFrames;
        final AEFileInputStream ais = new AEFileInputStream(aedatFile, chip);
        ais.setNonMonotonicTimeExceptionsChecked(false);
        final EventExtractor2D extractor = chip.getEventExtractor();
        renderer.setDisplayFrames(true);
        renderer.setDisplayEvents(false);

        while (renderer.preDemosaicFrames.size() < maxFrames) {
            try {
                final AEPacketRaw raw = ais.readPacketByNumber(100000);
                final EventPacket cooked = extractor.extractPacket(raw);
                renderer.render(cooked);
            } catch (EOFException ex) {
                break;
            }
        }
        // Do not call ais.close(); marksFilesMap may be null in headless use.
    }

    private static long timeLegacyProcessColorFrame(final CaptureRenderer renderer, final float[] snapshot,
            final int iterations, final boolean awb, final boolean cc, final boolean monochrome) {
        final float[] work = new float[snapshot.length];
        final int sx = renderer.benchmarkSizeX();
        final int sy = renderer.benchmarkSizeY();
        final int tw = renderer.textureWidth;
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            System.arraycopy(snapshot, 0, work, 0, snapshot.length);
            DavisColorRendererLegacy.processColorFrame(work, sx, sy, tw, renderer.benchmarkCfaSequence(),
                    renderer.benchmarkColorCorrectionMatrix(), awb, cc, monochrome);
        }
        final long t0 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            System.arraycopy(snapshot, 0, work, 0, snapshot.length);
            DavisColorRendererLegacy.processColorFrame(work, sx, sy, tw, renderer.benchmarkCfaSequence(),
                    renderer.benchmarkColorCorrectionMatrix(), awb, cc, monochrome);
        }
        return System.nanoTime() - t0;
    }

    private static void verifyLegacyEquivalence(final CaptureRenderer renderer, final float[] snapshot,
            final boolean awb, final boolean cc, final boolean monochrome) {
        final float[] legacy = snapshot.clone();
        final float[] refactored = snapshot.clone();
        final int sx = renderer.benchmarkSizeX();
        final int sy = renderer.benchmarkSizeY();
        final int tw = renderer.textureWidth;
        DavisColorRendererLegacy.processColorFrame(legacy, sx, sy, tw, renderer.benchmarkCfaSequence(),
                renderer.benchmarkColorCorrectionMatrix(), awb, cc, monochrome);
        System.arraycopy(refactored, 0, renderer.getPixBufferArray(), 0, refactored.length);
        renderer.processColorFrame();
        System.arraycopy(renderer.getPixBufferArray(), 0, refactored, 0, refactored.length);

        float maxDiff = 0;
        double sumDiff = 0;
        int n = 0;
        int nanMismatch = 0;
        for (int y = 0; y < sy; y++) {
            for (int x = 0; x < sx; x++) {
                final int base = 4 * ((y * tw) + x);
                for (int c = 0; c < 3; c++) {
                    final float lv = legacy[base + c];
                    final float rv = refactored[base + c];
                    if (Float.isNaN(lv) && Float.isNaN(rv)) {
                        n++;
                        continue;
                    }
                    if (Float.isNaN(lv) || Float.isNaN(rv)) {
                        nanMismatch++;
                        continue;
                    }
                    final float d = Math.abs(lv - rv);
                    if (d > maxDiff) {
                        maxDiff = d;
                    }
                    sumDiff += d;
                    n++;
                }
            }
        }
        if (nanMismatch > 0) {
            log.warning("equivalence: " + nanMismatch + " RGB samples differ (one NaN, one not)");
        }
        log.info(String.format(
                "equivalence check (frame 0, %dx%d, %d RGB samples): max diff=%.6f, mean diff=%.6f",
                sx, sy, n, maxDiff, n > 0 ? sumDiff / n : 0));
    }

    private static long timeProcessColorFrame(final CaptureRenderer renderer, final float[] snapshot, final int iterations) {
        final float[] buf = renderer.getPixBufferArray();
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            System.arraycopy(snapshot, 0, buf, 0, snapshot.length);
            renderer.processColorFrame();
        }
        final long t0 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            System.arraycopy(snapshot, 0, buf, 0, snapshot.length);
            renderer.processColorFrame();
        }
        return System.nanoTime() - t0;
    }

    private static long timeEndFrame(final CaptureRenderer renderer, final float[] snapshot, final int iterations) {
        final float[] buf = renderer.getPixBufferArray();
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            System.arraycopy(snapshot, 0, buf, 0, snapshot.length);
            renderer.benchmarkEndFrame(0);
        }
        final long t0 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            System.arraycopy(snapshot, 0, buf, 0, snapshot.length);
            renderer.benchmarkEndFrame(0);
        }
        return System.nanoTime() - t0;
    }

    private static void printUsage() {
        System.err.println("Usage: java eu.seebetter.ini.chips.davis.DavisColorRendererBenchmark [options] [input.aedat]");
        System.err.println("  -iterations=N   (default " + DEFAULT_ITERATIONS + ")");
        System.err.println("  -maxframes=N    APS frames to capture from recording (default " + DEFAULT_MAX_FRAMES + ")");
        System.err.println("  -awb=true|false -cc=true|false -monochrome=true|false");
        System.err.println("  Default input: " + DEFAULT_AEDAT);
    }

    static final class CaptureRenderer extends DavisColorRenderer {

        final ArrayList<float[]> preDemosaicFrames = new ArrayList<>();
        int maxCapture = DEFAULT_MAX_FRAMES;
        private boolean captureEnabled = true;

        CaptureRenderer(final CDAVIS chip) {
            super(chip, true, CDAVIS.COLOR_FILTER, true, CDAVIS.COLOR_CORRECTION);
        }

        float[] getPixBufferArray() {
            return pixBuffer.array();
        }

        int benchmarkSizeX() {
            return sizeX > 0 ? sizeX : CDAVIS.WIDTH_PIXELS;
        }

        int benchmarkSizeY() {
            return sizeY > 0 ? sizeY : CDAVIS.HEIGHT_PIXELS;
        }

        void allocatePixmaps() {
            checkPixmapAllocation();
        }

        @Override
        protected void endFrame(final int ts) {
            if (captureEnabled && preDemosaicFrames.size() < maxCapture) {
                preDemosaicFrames.add(pixBuffer.array().clone());
            }
            super.endFrame(ts);
        }

        void benchmarkEndFrame(final int ts) {
            captureEnabled = false;
            endFrame(ts);
            captureEnabled = true;
        }
    }
}
