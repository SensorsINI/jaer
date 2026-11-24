package net.sf.jaer.eventio.aedat4;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventio.RecordingChipDetector;
import net.sf.jaer.eventio.aedat4.dv.FileDataDefinition;
import net.sf.jaer.eventio.aedat4.dv.FileDataTable;
import net.sf.jaer.eventio.aedat4.dv.IOHeader;

/**
 * Clock scale of each camera in one multi-camera AEDAT-4 recording.
 *
 * <p>Stored packet times are the computer time when recording started, plus
 * that camera's own elapsed microseconds. Cameras that watched the same scene
 * share activity bursts. Those bursts are taken from the packet index (event
 * counts over each packet's timestamp span). Sliding-window cross-correlation
 * measures how the lag between cameras grows, which is their clock scale.
 *
 * <p>The computer-clock span is the filename start through the file's
 * modification time. Dividing a camera's timestamp span by that duration is
 * that camera's scale against the computer. Copying the file later changes
 * the modification time and this absolute scale.
 *
 * <pre>
 *   scripts/measure-clock-drift.sh recording.aedat4
 *   scripts/measure-clock-drift.ps1 recording.aedat4
 *   scripts/measure-clock-drift.sh --self-check
 * </pre>
 */
public final class MulticameraClockDrift {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    /** Activity histograms longer than this are refused; widen the bin. */
    static final int MAX_BINS = 2_000_000;
    /** Report lines fit the measurement dialog's monospaced text area. */
    public static final int REPORT_WIDTH = 72;
    private static final DateTimeFormatter INSTANT_FORMAT
            = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.ROOT);

    private MulticameraClockDrift() {
    }

    /** Called with a note and a percent in 0..100. Return false to stop. */
    @FunctionalInterface
    public interface Progress {
        boolean update(String note, int percent);
    }

    /** Bin width, correlation window, and lag search. Times are microseconds. */
    public static final class Options {
        public long binUs = 250_000L;
        public long windowUs = 10L * 60L * 1_000_000L;
        public long stepUs = 5L * 60L * 1_000_000L;
        public long maxLagUs = 120L * 1_000_000L;
        public double minCorrelation = 0.2;

        void validate() {
            if (binUs < 1_000L) {
                throw new IllegalArgumentException("Bin must be at least 1 ms");
            }
            if (windowUs < binUs * 8L) {
                throw new IllegalArgumentException("Window must cover at least 8 bins");
            }
            if (stepUs < binUs) {
                throw new IllegalArgumentException("Step must be at least one bin");
            }
            if (maxLagUs < binUs) {
                throw new IllegalArgumentException("Max lag must be at least one bin");
            }
            if (minCorrelation < -1.0 || minCorrelation > 1.0) {
                throw new IllegalArgumentException("Minimum correlation must be between -1 and 1");
            }
        }
    }

    /** One camera after the fit. */
    public static final class CameraFit {
        public final String label;
        public final int streamId;
        public final boolean reference;
        public final long events;
        public final long packets;
        public final long firstUs;
        public final long lastUs;
        /**
         * This camera's elapsed time divided by the reference camera's elapsed
         * time. Greater than 1 means this clock ran fast. 1 for the reference.
         * NaN when the rate was not fit.
         */
        public final double scaleVsReference;
        /** Lag (this camera minus reference) at the reference's first timestamp, microseconds. */
        public final double offsetAtStartUs;
        /** Predicted lag at the reference's last timestamp, microseconds. */
        public final double lagAtEndUs;
        /** Timestamp span divided by the computer-clock span. NaN when that span is unknown. */
        public final double endpointScaleVsComputer;
        public final int windowsUsed;
        public final int windowsTried;
        public final double medianCorrelation;
        public final double fitR2;
        public final boolean lagSearchSaturated;
        /** First, middle, and last accepted lags, microseconds. Empty when none were accepted. */
        public final double[] sampleLagsUs;

        CameraFit(String label, int streamId, boolean reference, long events, long packets,
                long firstUs, long lastUs, double scaleVsReference, double offsetAtStartUs,
                double lagAtEndUs, double endpointScaleVsComputer, int windowsUsed, int windowsTried,
                double medianCorrelation, double fitR2, boolean lagSearchSaturated, double[] sampleLagsUs) {
            this.label = label;
            this.streamId = streamId;
            this.reference = reference;
            this.events = events;
            this.packets = packets;
            this.firstUs = firstUs;
            this.lastUs = lastUs;
            this.scaleVsReference = scaleVsReference;
            this.offsetAtStartUs = offsetAtStartUs;
            this.lagAtEndUs = lagAtEndUs;
            this.endpointScaleVsComputer = endpointScaleVsComputer;
            this.windowsUsed = windowsUsed;
            this.windowsTried = windowsTried;
            this.medianCorrelation = medianCorrelation;
            this.fitR2 = fitR2;
            this.lagSearchSaturated = lagSearchSaturated;
            this.sampleLagsUs = sampleLagsUs;
        }

        public boolean rateWasFit() {
            return !reference && !Double.isNaN(scaleVsReference) && windowsUsed >= 3;
        }
    }

    /** Measurement for one file. {@link #report} is the text the dialog and the script show. */
    public static final class Result {
        public final File file;
        public final String referenceLabel;
        public final long computerSpanUs;
        public final String computerClockNote;
        public final List<CameraFit> cameras;
        public final String report;

        Result(File file, String referenceLabel, long computerSpanUs, String computerClockNote,
                List<CameraFit> cameras, String report) {
            this.file = file;
            this.referenceLabel = referenceLabel;
            this.computerSpanUs = computerSpanUs;
            this.computerClockNote = computerClockNote;
            this.cameras = List.copyOf(cameras);
            this.report = report;
        }

        public boolean crossCorrelationOk() {
            for (CameraFit c : cameras) {
                if (c.rateWasFit()) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Read the packet index and measure clock scales.
     *
     * @param progress optional; {@code false} from {@link Progress#update} stops the read
     */
    public static Result measure(File file, Options options, Progress progress) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("AEDAT-4 file is missing");
        }
        if (options == null) {
            options = new Options();
        }
        options.validate();
        report(progress, "Reading packet index", 1);
        List<Activity> activities = readActivities(file, options.binUs, progress);
        if (activities.size() < 2) {
            throw new IOException("Need event streams from at least two cameras. Found " + activities.size() + ".");
        }
        report(progress, "Correlating activity", 72);
        long computerSpanUs = 0L;
        String computerNote = "The filename has no recording start, so scales are versus the reference camera.";
        AEDataFile.FilenameStart named = AEDataFile.parseFilenameStart(file);
        if (named != null && file.lastModified() > named.epochMs) {
            computerSpanUs = (file.lastModified() - named.epochMs) * 1000L;
            computerNote = String.format(Locale.ROOT,
                    "Computer clock span %s, from filename %s to file modified %s. "
                            + "That span is the recording length when this file was closed here and has not been copied since.",
                    formatDuration(computerSpanUs),
                    formatInstant(named.epochMs, named.zone),
                    formatInstant(file.lastModified(), ZoneId.systemDefault()));
        }
        Result result = measureActivities(file, activities, computerSpanUs, computerNote, options, progress);
        log.info("Clock drift " + file.getName() + ": " + oneLine(result));
        report(progress, "Done", 100);
        return result;
    }

    static Result measureActivities(File file, List<Activity> activities, long computerSpanUs,
            String computerNote, Options options, Progress progress) {
        options.validate();
        Activity reference = referenceOf(activities);
        List<CameraFit> fits = new ArrayList<>();
        double refEndpoint = endpointScale(reference, computerSpanUs);
        fits.add(fitReference(reference, refEndpoint));
        int nOther = activities.size() - 1;
        int done = 0;
        for (Activity other : activities) {
            if (other == reference) {
                continue;
            }
            int pct = nOther <= 0 ? 90 : 72 + (26 * done) / nOther;
            fits.add(fitOther(reference, other, options, computerSpanUs, progress, pct));
            done++;
            if (nOther > 0) {
                report(progress, "Correlating " + other.label, 72 + (26 * done) / nOther);
            }
        }
        fits.sort((a, b) -> {
            if (a.reference != b.reference) {
                return a.reference ? -1 : 1;
            }
            return Integer.compare(a.streamId, b.streamId);
        });
        String report = formatReport(file, reference.label, computerSpanUs, computerNote, fits, options);
        return new Result(file, reference.label, computerSpanUs, computerNote, fits, report);
    }

    /**
     * Checks binning, a constant offset, and a known fast clock.
     * {@code java ... MulticameraClockDrift --self-check}
     */
    static void selfCheck() {
        long[] start = {0L};
        long[] end = {2_000_000L};
        long[] count = {100L};
        double[] bins = new double[2];
        addSpans(bins, 0L, 1_000_000L, start, end, count, 1);
        if (Math.abs(bins[0] - 50.0) > 1e-6 || Math.abs(bins[1] - 50.0) > 1e-6) {
            throw new AssertionError("even split bins " + bins[0] + ", " + bins[1]);
        }
        double[] point = new double[2];
        addSpans(point, 0L, 1_000_000L, new long[]{1_500_000L}, new long[]{1_500_000L}, new long[]{7L}, 1);
        if (point[0] != 0.0 || point[1] != 7.0) {
            throw new AssertionError("point packet landed in bin " + point[0] + ", " + point[1]);
        }

        Options offsetOpt = new Options();
        offsetOpt.binUs = 50_000L;
        offsetOpt.windowUs = 2L * 60L * 1_000_000L;
        offsetOpt.stepUs = 60L * 1_000_000L;
        offsetOpt.maxLagUs = 2L * 1_000_000L;
        offsetOpt.minCorrelation = 0.3;
        long twentyMin = 20L * 60L * 1_000_000L;
        Activity refOff = pulses("ref", 0, twentyMin, offsetOpt.binUs, 1.0, 0.0, 2_000_000L, 500_000L, 8000);
        Activity late = pulses("late", 3, twentyMin, offsetOpt.binUs, 1.0, 40_000.0, 2_000_000L, 500_000L, 8000);
        Result offsetResult = measureActivities(new File("offset-self-check"),
                List.of(refOff, late), 0L, "", offsetOpt, null);
        CameraFit lateFit = nonReference(offsetResult);
        if (!lateFit.rateWasFit()) {
            throw new AssertionError("offset fit failed\n" + offsetResult.report);
        }
        if (Math.abs(lateFit.scaleVsReference - 1.0) > 5e-5) {
            throw new AssertionError("offset case scale " + lateFit.scaleVsReference + "\n" + offsetResult.report);
        }
        if (Math.abs(lateFit.offsetAtStartUs - 40_000.0) > 30_000.0) {
            throw new AssertionError("offset case lag " + lateFit.offsetAtStartUs + "\n" + offsetResult.report);
        }

        Options scaleOpt = new Options();
        scaleOpt.binUs = 250_000L;
        scaleOpt.windowUs = 10L * 60L * 1_000_000L;
        scaleOpt.stepUs = 5L * 60L * 1_000_000L;
        scaleOpt.maxLagUs = 5L * 1_000_000L;
        scaleOpt.minCorrelation = 0.3;
        long twoHours = 2L * 3600L * 1_000_000L;
        double fast = 1.0002;
        Activity refScale = pulses("ref", 0, twoHours, scaleOpt.binUs, 1.0, 0.0, 5_000_000L, 1_000_000L, 8000);
        Activity fastCam = pulses("fast", 3, twoHours, scaleOpt.binUs, fast, 30_000.0, 5_000_000L, 1_000_000L, 8000);
        Result scaleResult = measureActivities(new File("scale-self-check"),
                List.of(refScale, fastCam), 0L, "", scaleOpt, null);
        CameraFit fastFit = nonReference(scaleResult);
        if (!fastFit.rateWasFit()) {
            throw new AssertionError("scale fit failed\n" + scaleResult.report);
        }
        if (Math.abs(fastFit.scaleVsReference - fast) > 3e-5) {
            throw new AssertionError("scale " + fastFit.scaleVsReference + "\n" + scaleResult.report);
        }
        if (Math.abs(fastFit.offsetAtStartUs - 30_000.0) > 150_000.0) {
            throw new AssertionError("scale-case offset " + fastFit.offsetAtStartUs + "\n" + scaleResult.report);
        }
        if (!offsetResult.report.contains("Conclusion:") || !offsetResult.report.contains("after")) {
            throw new AssertionError(offsetResult.report);
        }
        if (!scaleResult.report.contains(" fast versus ")) {
            throw new AssertionError(scaleResult.report);
        }
        assertWrapped(offsetResult.report);
        assertWrapped(scaleResult.report);
        String wrappedPath = wrapReport("/Volumes/" + "n".repeat(80) + " dr mel.aedat4\n");
        if (!wrappedPath.contains("\n  ")) {
            throw new AssertionError("wrapped path was not indented:\n" + wrappedPath);
        }
        assertWrapped(wrappedPath);
        Aedat4ClockCorrection.selfCheck();
    }

    private static void assertWrapped(String text) {
        for (String line : text.split("\n", -1)) {
            if (line.length() > REPORT_WIDTH) {
                throw new AssertionError("line longer than " + REPORT_WIDTH + ": " + line);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--self-check".equals(args[0])) {
            selfCheck();
            System.out.println("self-check ok");
            return;
        }
        Options options = new Options();
        File file = null;
        File writeTo = null;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--help".equals(arg) || "-h".equals(arg)) {
                usage();
                return;
            }
            if ("--bin-ms".equals(arg)) {
                options.binUs = Long.parseLong(req(args, ++i, arg)) * 1_000L;
            } else if ("--write".equals(arg)) {
                writeTo = new File(req(args, ++i, arg));
            } else if ("--window-min".equals(arg)) {
                options.windowUs = Long.parseLong(req(args, ++i, arg)) * 60L * 1_000_000L;
            } else if ("--step-min".equals(arg)) {
                options.stepUs = Long.parseLong(req(args, ++i, arg)) * 60L * 1_000_000L;
            } else if ("--max-lag-s".equals(arg)) {
                options.maxLagUs = Long.parseLong(req(args, ++i, arg)) * 1_000_000L;
            } else if ("--min-corr".equals(arg)) {
                options.minCorrelation = Double.parseDouble(req(args, ++i, arg));
            } else if (arg.startsWith("-")) {
                System.err.println("Unknown option " + arg);
                usage();
                System.exit(2);
                return;
            } else if (file != null) {
                System.err.println("One recording only");
                usage();
                System.exit(2);
                return;
            } else {
                file = new File(arg);
            }
        }
        if (file == null) {
            usage();
            System.exit(2);
            return;
        }
        Result result = measure(file, options, (note, percent) -> {
            System.err.println(percent + "% " + note);
            return true;
        });
        System.out.print(result.report);
        if (writeTo != null) {
            File out = Aedat4ClockCorrection.writeCorrectedCopy(file, writeTo, result, (note, percent) -> {
                System.err.println(percent + "% " + note);
                return true;
            });
            System.out.println("Wrote " + out.getAbsolutePath());
        }
    }

    private static void usage() {
        System.err.println("""
                Usage: measure-clock-drift.sh or measure-clock-drift.ps1 [options] recording.aedat4
                  --bin-ms N       activity bin width (default 250)
                  --window-min N   correlation window (default 10)
                  --step-min N     window step (default 5)
                  --max-lag-s N    lag search, either side (default 120)
                  --min-corr X     drop windows below this correlation (default 0.2)
                  --write FILE     after measuring, write a corrected AEDAT-4 copy
                  --self-check     run the built-in numeric check""");
    }

    private static String req(String[] args, int i, String flag) {
        if (i >= args.length) {
            throw new IllegalArgumentException(flag + " needs a value");
        }
        return args[i];
    }

    private static CameraFit nonReference(Result result) {
        for (CameraFit c : result.cameras) {
            if (!c.reference) {
                return c;
            }
        }
        throw new AssertionError("no second camera");
    }

    static final class Activity {
        final String label;
        final int streamId;
        final long originUs;
        final long binUs;
        final double[] counts;
        final long firstUs;
        final long lastUs;
        final long events;
        final long packets;

        Activity(String label, int streamId, long originUs, long binUs, double[] counts,
                long firstUs, long lastUs, long events, long packets) {
            this.label = label;
            this.streamId = streamId;
            this.originUs = originUs;
            this.binUs = binUs;
            this.counts = counts;
            this.firstUs = firstUs;
            this.lastUs = lastUs;
            this.events = events;
            this.packets = packets;
        }
    }

    private static Activity pulses(String label, int streamId, long durationUs, long binUs,
            double scale, double offsetUs, long everyUs, long widthUs, double amplitude) {
        long lastStamp = (long) Math.ceil(scale * durationUs + offsetUs + widthUs);
        int nBins = (int) (lastStamp / binUs) + 2;
        double[] counts = new double[nBins];
        long first = Long.MAX_VALUE;
        long last = Long.MIN_VALUE;
        long events = 0L;
        long amp = Math.max(1L, Math.round(amplitude));
        for (long host = 0L; host + widthUs <= durationUs; host += everyUs) {
            long cam0 = Math.round(scale * host + offsetUs);
            long cam1 = Math.round(scale * (host + widthUs) + offsetUs);
            if (cam1 <= cam0) {
                cam1 = cam0 + 1L;
            }
            addSpan(counts, 0L, binUs, cam0, cam1, amp);
            events += amp;
            if (cam0 < first) {
                first = cam0;
            }
            if (cam1 > last) {
                last = cam1;
            }
        }
        return new Activity(label, streamId, 0L, binUs, counts, first, last, events, events);
    }

    private static List<Activity> readActivities(File file, long binUs, Progress progress) throws IOException {
        Map<Integer, String> labels = new LinkedHashMap<>();
        java.util.Set<Integer> eventIds = new java.util.HashSet<>();
        int compression;
        long dataTablePosition;
        long fileSize;
        try (FileInputStream in = new FileInputStream(file); FileChannel channel = in.getChannel()) {
            ByteBuffer version = ByteBuffer.allocate(Aedat4FileOutputStream.VERSION_LINE.length);
            readFully(channel, version);
            if (!Arrays.equals(version.array(), Aedat4FileOutputStream.VERSION_LINE)) {
                throw new IOException(file.getName() + " is not an AEDAT-4 file");
            }
            ByteBuffer headerBytes = readSizePrefixed(channel);
            IOHeader header = IOHeader.getSizePrefixedRootAsIOHeader(headerBytes);
            compression = Aedat4Compression.clamp(header.compression());
            dataTablePosition = header.dataTablePosition();
            fileSize = channel.size();
            for (RecordingChipDetector.StreamHint hint
                    : RecordingChipDetector.streamsFromInfoNodeXml(header.infoNode())) {
                if (hint.isEvents()) {
                    eventIds.add(hint.streamId);
                    labels.put(hint.streamId, hint.displayLabel());
                }
            }
            Map<Integer, Acc> accs = new LinkedHashMap<>();
            int[] pct = {5};
            boolean indexed = Aedat4FileDataIndex.forEachTable(channel, dataTablePosition, fileSize, compression,
                    new Aedat4FileDataIndex.TableConsumer() {
                        @Override
                        public void beginChunk(int index, int count) {
                            pct[0] = count <= 1 ? 10 : 5 + (60 * index) / count;
                            report(progress, "Reading packet index (part " + (index + 1) + " of " + count + ")", pct[0]);
                        }

                        @Override
                        public void accept(ByteBuffer sizePrefixedLittleEndian) throws IOException {
                            FileDataTable table = FileDataTable.getSizePrefixedRootAsFileDataTable(
                                    sizePrefixedLittleEndian);
                            FileDataDefinition def = new FileDataDefinition();
                            int n = table.tableLength();
                            boolean filter = !eventIds.isEmpty();
                            for (int i = 0; i < n; i++) {
                                if ((i & 0xffff) == 0 && !report(progress, "Reading packet index", pct[0])) {
                                    throw new Cancelled();
                                }
                                FileDataDefinition row = table.table(def, i);
                                if (row == null) {
                                    continue;
                                }
                                int streamId = row.packetInfoStreamID();
                                if (filter) {
                                    if (!eventIds.contains(streamId)) {
                                        continue;
                                    }
                                } else if (Math.floorMod(streamId, Aedat4CameraTrack.STREAMS_PER_CAMERA) != 0) {
                                    continue;
                                }
                                long elements = row.numElements();
                                if (elements <= 0L) {
                                    continue;
                                }
                                Acc acc = accs.get(streamId);
                                if (acc == null) {
                                    String label = labels.get(streamId);
                                    if (label == null) {
                                        label = "stream " + streamId;
                                    }
                                    acc = new Acc(streamId, label);
                                    accs.put(streamId, acc);
                                }
                                acc.add(row.timestampStart(), row.timestampEnd(), elements);
                            }
                        }
                    });
            if (!indexed) {
                throw new IOException(file.getName() + " has no AEDAT-4 packet index. Close the recording and measure again.");
            }
            if (!report(progress, "Binning activity", 68)) {
                throw new Cancelled();
            }
            long min = Long.MAX_VALUE;
            long max = Long.MIN_VALUE;
            for (Acc acc : accs.values()) {
                if (acc.packets == 0) {
                    continue;
                }
                if (acc.firstUs < min) {
                    min = acc.firstUs;
                }
                if (acc.lastUs > max) {
                    max = acc.lastUs;
                }
            }
            if (min == Long.MAX_VALUE || max <= min) {
                throw new IOException("Event packets have no timestamp span");
            }
            long span = max - min;
            if (span / binUs > MAX_BINS) {
                throw new IOException(String.format(Locale.ROOT,
                        "Bin of %.3f ms would be %,d bins over %s. Use a wider bin.",
                        binUs / 1e3, span / binUs + 1, formatDuration(span)));
            }
            int nBins = (int) (span / binUs) + 1;
            List<Activity> out = new ArrayList<>();
            for (Acc acc : accs.values()) {
                if (acc.packets == 0) {
                    continue;
                }
                double[] counts = new double[nBins];
                addSpans(counts, min, binUs, acc.startUs.a, acc.endUs.a, acc.counts.a, acc.packets);
                out.add(new Activity(acc.label, acc.streamId, min, binUs, counts,
                        acc.firstUs, acc.lastUs, acc.events, acc.packets));
            }
            return out;
        }
    }

    private static final class Acc {
        final int streamId;
        final String label;
        final LongList startUs = new LongList();
        final LongList endUs = new LongList();
        final LongList counts = new LongList();
        long wrap;
        long lastEnd = Long.MIN_VALUE;
        long firstUs = Long.MAX_VALUE;
        long lastUs = Long.MIN_VALUE;
        long events;
        int packets;

        Acc(int streamId, String label) {
            this.streamId = streamId;
            this.label = label;
        }

        void add(long start, long end, long count) {
            if (count <= 0L) {
                return;
            }
            if (lastEnd != Long.MIN_VALUE && start + wrap < lastEnd - TimestampUnwrapper.WRAP_DETECT_US) {
                wrap += TimestampUnwrapper.UINT32_US;
            }
            start += wrap;
            end += wrap;
            if (end < start && start - end > TimestampUnwrapper.WRAP_DETECT_US) {
                end += TimestampUnwrapper.UINT32_US;
            }
            if (end < start) {
                end = start;
            }
            if (start == 0L && end == 0L) {
                return;
            }
            startUs.add(start);
            endUs.add(end);
            counts.add(count);
            packets++;
            events += count;
            if (start < firstUs) {
                firstUs = start;
            }
            if (end > lastUs) {
                lastUs = end;
            }
            lastEnd = end;
        }
    }

    private static final class LongList {
        long[] a = new long[1024];
        int n;

        void add(long v) {
            if (n == a.length) {
                a = Arrays.copyOf(a, a.length * 2);
            }
            a[n++] = v;
        }
    }

    static void addSpans(double[] bins, long originUs, long binUs,
            long[] start, long[] end, long[] count, int n) {
        for (int i = 0; i < n; i++) {
            addSpan(bins, originUs, binUs, start[i], end[i], count[i]);
        }
    }

    private static void addSpan(double[] bins, long originUs, long binUs, long start, long end, long count) {
        if (count <= 0L || bins.length == 0 || binUs <= 0L) {
            return;
        }
        if (end <= start) {
            long rel = start - originUs;
            if (rel < 0L) {
                return;
            }
            int b = (int) (rel / binUs);
            if (b >= 0 && b < bins.length) {
                bins[b] += count;
            }
            return;
        }
        long gridEnd = originUs + binUs * (long) bins.length;
        long clip0 = Math.max(start, originUs);
        long clip1 = Math.min(end, gridEnd);
        if (clip1 <= clip0) {
            return;
        }
        double perUs = count / (double) (end - start);
        int b0 = (int) ((clip0 - originUs) / binUs);
        int b1 = (int) ((clip1 - 1L - originUs) / binUs);
        if (b0 < 0) {
            b0 = 0;
        }
        if (b1 >= bins.length) {
            b1 = bins.length - 1;
        }
        for (int b = b0; b <= b1; b++) {
            long bs = originUs + (long) b * binUs;
            long be = bs + binUs;
            long lo = Math.max(clip0, bs);
            long hi = Math.min(clip1, be);
            if (hi > lo) {
                bins[b] += perUs * (hi - lo);
            }
        }
    }

    private static Activity referenceOf(List<Activity> activities) {
        Activity best = activities.get(0);
        for (int i = 1; i < activities.size(); i++) {
            Activity cand = activities.get(i);
            if (cand.events > best.events
                    || (cand.events == best.events && cand.streamId < best.streamId)) {
                best = cand;
            }
        }
        return best;
    }

    private static CameraFit fitReference(Activity reference, double endpointScale) {
        return new CameraFit(reference.label, reference.streamId, true, reference.events, reference.packets,
                reference.firstUs, reference.lastUs, 1.0, 0.0, 0.0, endpointScale,
                0, 0, Double.NaN, Double.NaN, false, new double[0]);
    }

    private static CameraFit fitOther(Activity reference, Activity other, Options options, long computerSpanUs,
            Progress progress, int percent) {
        double endpoint = endpointScale(other, computerSpanUs);
        long binUs = reference.binUs;
        int maxLagBins = (int) Math.min(Integer.MAX_VALUE, options.maxLagUs / binUs);
        if (maxLagBins < 1) {
            maxLagBins = 1;
        }
        long span = reference.lastUs - reference.firstUs;
        long windowUs = options.windowUs;
        // The window has to move. One window covering the whole file cannot
        // separate a fixed offset from a clock scale.
        if (span < windowUs + 2L * Math.max(binUs, options.stepUs)) {
            return unfinished(other, endpoint, 0, 0, false);
        }
        int windowBins = (int) Math.min(Integer.MAX_VALUE, windowUs / binUs);
        if (windowBins < 8) {
            return unfinished(other, endpoint, 0, 0, false);
        }
        int minOverlap = Math.max(8, windowBins / 2);
        List<LagSample> samples = new ArrayList<>();
        int tried = 0;
        int saturated = 0;
        double expectedLagUs = 0.0;
        long step = Math.max(binUs, options.stepUs);
        long center = reference.firstUs + windowUs / 2L;
        long guard = 0L;
        while (center - windowUs / 2L < reference.lastUs && guard++ < 1_000_000L) {
            long w0 = center - windowUs / 2L;
            long w1 = w0 + windowUs;
            tried++;
            if ((tried & 7) == 0 && !report(progress, "Correlating " + other.label, percent)) {
                throw new Cancelled();
            }
            boolean preferSmallLag = samples.isEmpty();
            Peak peak = peakLag(reference, other, w0, w1, maxLagBins, minOverlap, expectedLagUs, preferSmallLag);
            if (peak != null && peak.saturated) {
                saturated++;
            }
            if (peak != null && !peak.saturated && peak.correlation >= options.minCorrelation) {
                samples.add(new LagSample(center, peak.lagUs, peak.correlation));
                expectedLagUs = peak.lagUs;
            }
            if (center > Long.MAX_VALUE - step) {
                break;
            }
            long next = center + step;
            if (next <= center) {
                break;
            }
            center = next;
            if (w0 >= reference.lastUs) {
                break;
            }
        }
        if (samples.size() < 3) {
            return unfinished(other, endpoint, samples.size(), tried, saturated > 0);
        }
        samples.sort((a, b) -> Long.compare(a.timeUs, b.timeUs));
        Line line = fitLag(samples, reference.firstUs);
        double spanSec = (reference.lastUs - reference.firstUs) / 1e6;
        double lagAtEnd = line.interceptUs + line.slopeUsPerSec * spanSec;
        double scale = 1.0 + line.slopeUsPerSec / 1e6;
        double[] shown = new double[]{
            samples.get(0).lagUs,
            samples.get(samples.size() / 2).lagUs,
            samples.get(samples.size() - 1).lagUs
        };
        return new CameraFit(other.label, other.streamId, false, other.events, other.packets,
                other.firstUs, other.lastUs, scale, line.interceptUs, lagAtEnd, endpoint,
                samples.size(), tried, medianCorrelation(samples), line.r2, saturated > 0, shown);
    }

    private static CameraFit unfinished(Activity other, double endpoint, int used, int tried, boolean saturated) {
        return new CameraFit(other.label, other.streamId, false, other.events, other.packets,
                other.firstUs, other.lastUs, Double.NaN, Double.NaN, Double.NaN, endpoint,
                used, tried, Double.NaN, Double.NaN, saturated, new double[0]);
    }

    private static final class LagSample {
        final long timeUs;
        final double lagUs;
        final double correlation;

        LagSample(long timeUs, double lagUs, double correlation) {
            this.timeUs = timeUs;
            this.lagUs = lagUs;
            this.correlation = correlation;
        }
    }

    private static final class Peak {
        final double lagUs;
        final double correlation;
        final boolean saturated;

        Peak(double lagUs, double correlation, boolean saturated) {
            this.lagUs = lagUs;
            this.correlation = correlation;
            this.saturated = saturated;
        }
    }

    private static final class Line {
        final double interceptUs;
        final double slopeUsPerSec;
        final double r2;

        Line(double interceptUs, double slopeUsPerSec, double r2) {
            this.interceptUs = interceptUs;
            this.slopeUsPerSec = slopeUsPerSec;
            this.r2 = r2;
        }
    }

    /**
     * Lag is how much later {@code other}'s timestamps are than {@code reference}
     * for the same burst. Positive lag means {@code other} is ahead.
     * Periodic activity has a peak every period. The first window prefers the
     * peak nearest zero (clocks are aligned at the start). Later windows stay
     * near {@code expectedLagUs}.
     */
    private static Peak peakLag(Activity reference, Activity other, long w0, long w1,
            int maxLagBins, int minOverlap, double expectedLagUs, boolean preferSmallLag) {
        long binUs = reference.binUs;
        int i0 = binIndex(reference.originUs, binUs, w0);
        int i1 = binIndex(reference.originUs, binUs, w1);
        if (i0 < 0) {
            i0 = 0;
        }
        if (i1 > reference.counts.length) {
            i1 = reference.counts.length;
        }
        if (i1 - i0 < minOverlap) {
            return null;
        }
        int originDelta = 0;
        if (other.originUs != reference.originUs) {
            long delta = reference.originUs - other.originUs;
            if (delta % binUs != 0L) {
                return null;
            }
            originDelta = (int) (delta / binUs);
        }
        int bestLag = 0;
        double best = Double.NEGATIVE_INFINITY;
        boolean any = false;
        double[] corr = new double[maxLagBins * 2 + 1];
        for (int lag = -maxLagBins; lag <= maxLagBins; lag++) {
            double c = correlate(reference.counts, i0, i1, other.counts, lag + originDelta, minOverlap);
            corr[lag + maxLagBins] = c;
            if (!Double.isNaN(c) && c > best) {
                best = c;
                bestLag = lag;
                any = true;
            }
        }
        if (!any) {
            return null;
        }
        double targetUs = preferSmallLag ? 0.0 : expectedLagUs;
        double band = preferSmallLag ? 0.10 : 0.05;
        int chosen = bestLag;
        double chosenDist = Double.POSITIVE_INFINITY;
        boolean found = false;
        for (int lag = -maxLagBins; lag <= maxLagBins; lag++) {
            double c = corr[lag + maxLagBins];
            if (Double.isNaN(c) || c < best - band) {
                continue;
            }
            if (!isLocalPeak(corr, lag + maxLagBins)) {
                continue;
            }
            double dist = Math.abs(lag * (double) binUs - targetUs);
            if (!found || dist < chosenDist) {
                found = true;
                chosen = lag;
                chosenDist = dist;
            }
        }
        if (!found) {
            chosen = bestLag;
        }
        boolean saturated = chosen == -maxLagBins || chosen == maxLagBins;
        double frac = 0.0;
        int at = chosen + maxLagBins;
        if (!saturated && at > 0 && at + 1 < corr.length && !Double.isNaN(corr[at - 1]) && !Double.isNaN(corr[at + 1])) {
            double ym = corr[at - 1];
            double y0 = corr[at];
            double yp = corr[at + 1];
            double den = ym - 2.0 * y0 + yp;
            if (den != 0.0) {
                frac = 0.5 * (ym - yp) / den;
                if (frac > 1.0) {
                    frac = 1.0;
                } else if (frac < -1.0) {
                    frac = -1.0;
                }
            }
        }
        return new Peak((chosen + frac) * binUs, corr[at], saturated);
    }

    private static boolean isLocalPeak(double[] corr, int at) {
        double c = corr[at];
        if (Double.isNaN(c)) {
            return false;
        }
        if (at > 0 && !Double.isNaN(corr[at - 1]) && corr[at - 1] > c) {
            return false;
        }
        if (at + 1 < corr.length && !Double.isNaN(corr[at + 1]) && corr[at + 1] > c) {
            return false;
        }
        return true;
    }

    private static int binIndex(long originUs, long binUs, long timeUs) {
        long rel = timeUs - originUs;
        if (rel <= 0L) {
            return rel < 0L ? -1 : 0;
        }
        return (int) (rel / binUs);
    }

    static double correlate(double[] a, int i0, int i1, double[] b, int lag, int minOverlap) {
        int start = i0;
        int end = i1;
        if (start + lag < 0) {
            start = -lag;
        }
        if (end + lag > b.length) {
            end = b.length - lag;
        }
        if (start < 0) {
            start = 0;
        }
        if (end > a.length) {
            end = a.length;
        }
        int n = end - start;
        if (n < minOverlap) {
            return Double.NaN;
        }
        double sumA = 0.0;
        double sumB = 0.0;
        for (int i = start; i < end; i++) {
            sumA += a[i];
            sumB += b[i + lag];
        }
        double meanA = sumA / n;
        double meanB = sumB / n;
        double num = 0.0;
        double sa = 0.0;
        double sb = 0.0;
        for (int i = start; i < end; i++) {
            double da = a[i] - meanA;
            double db = b[i + lag] - meanB;
            num += da * db;
            sa += da * da;
            sb += db * db;
        }
        if (sa <= 0.0 || sb <= 0.0) {
            return Double.NaN;
        }
        return num / Math.sqrt(sa * sb);
    }

    /** Lag in microseconds versus seconds after {@code t0Us}. */
    private static Line fitLag(List<LagSample> samples, long t0Us) {
        double sw = 0.0;
        double swt = 0.0;
        double swl = 0.0;
        double swtt = 0.0;
        double swtl = 0.0;
        for (LagSample s : samples) {
            double w = Math.max(0.0, s.correlation);
            if (w == 0.0) {
                w = 1.0;
            }
            double t = (s.timeUs - t0Us) / 1e6;
            sw += w;
            swt += w * t;
            swl += w * s.lagUs;
            swtt += w * t * t;
            swtl += w * t * s.lagUs;
        }
        double den = sw * swtt - swt * swt;
        double intercept;
        double slope;
        if (sw <= 0.0) {
            return new Line(0.0, 0.0, Double.NaN);
        }
        if (Math.abs(den) < 1e-12) {
            intercept = swl / sw;
            slope = 0.0;
        } else {
            slope = (sw * swtl - swt * swl) / den;
            intercept = (swl * swtt - swt * swtl) / den;
        }
        double mean = swl / sw;
        double ssTot = 0.0;
        double ssRes = 0.0;
        for (LagSample s : samples) {
            double w = Math.max(0.0, s.correlation);
            if (w == 0.0) {
                w = 1.0;
            }
            double t = (s.timeUs - t0Us) / 1e6;
            double pred = intercept + slope * t;
            double d = s.lagUs - mean;
            double r = s.lagUs - pred;
            ssTot += w * d * d;
            ssRes += w * r * r;
        }
        double r2 = ssTot <= 0.0 ? 1.0 : 1.0 - ssRes / ssTot;
        return new Line(intercept, slope, r2);
    }

    private static double medianCorrelation(List<LagSample> samples) {
        double[] v = new double[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            v[i] = samples.get(i).correlation;
        }
        Arrays.sort(v);
        int n = v.length;
        if (n == 0) {
            return Double.NaN;
        }
        if ((n & 1) == 1) {
            return v[n / 2];
        }
        return 0.5 * (v[n / 2 - 1] + v[n / 2]);
    }

    private static double endpointScale(Activity activity, long computerSpanUs) {
        if (computerSpanUs <= 0L) {
            return Double.NaN;
        }
        long span = activity.lastUs - activity.firstUs;
        if (span <= 0L) {
            return Double.NaN;
        }
        return span / (double) computerSpanUs;
    }

    private static String formatReport(File file, String referenceLabel, long computerSpanUs, String computerNote,
            List<CameraFit> fits, Options options) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("Clock drift\n");
        if (file != null) {
            sb.append(file.getAbsolutePath()).append('\n');
        }
        sb.append('\n');
        sb.append("Cross-correlation compares packet activity (events per ")
                .append(String.format(Locale.ROOT, "%.3f ms", options.binUs / 1e3))
                .append(" bin). Windows are ")
                .append(String.format(Locale.ROOT, "%.1f min", options.windowUs / 60e6))
                .append(", stepped by ")
                .append(String.format(Locale.ROOT, "%.1f min", options.stepUs / 60e6))
                .append(", lag search ±")
                .append(String.format(Locale.ROOT, "%.1f s", options.maxLagUs / 1e6))
                .append(".\n");
        sb.append("Reference camera (most events): ").append(referenceLabel).append('\n');
        sb.append(computerNote).append('\n');
        sb.append('\n');
        boolean anyFit = false;
        for (CameraFit c : fits) {
            sb.append(c.label).append('\n');
            sb.append(String.format(Locale.ROOT, "  events %,d in %,d packets, span %s\n",
                    c.events, c.packets, formatDuration(c.lastUs - c.firstUs)));
            if (c.reference) {
                sb.append("  cross-correlation reference (scale 1)\n");
            } else if (c.rateWasFit()) {
                anyFit = true;
                sb.append(String.format(Locale.ROOT,
                        "  scale versus reference: %.8f (%s)\n",
                        c.scaleVsReference, ppm(c.scaleVsReference)));
                sb.append(String.format(Locale.ROOT,
                        "  offset at start: %s\n", formatSignedUs(c.offsetAtStartUs)));
                sb.append(String.format(Locale.ROOT,
                        "  lag at end of reference: %s\n", formatSignedUs(c.lagAtEndUs)));
                sb.append(String.format(Locale.ROOT,
                        "  windows %d of %d, median correlation %.3f, R2 %.3f\n",
                        c.windowsUsed, c.windowsTried, c.medianCorrelation, c.fitR2));
                if (c.sampleLagsUs.length == 3) {
                    sb.append(String.format(Locale.ROOT,
                            "  lag samples start / mid / end: %s, %s, %s\n",
                            formatSignedUs(c.sampleLagsUs[0]),
                            formatSignedUs(c.sampleLagsUs[1]),
                            formatSignedUs(c.sampleLagsUs[2])));
                }
                if (c.fitR2 < 0.5 && Math.abs(c.scaleVsReference - 1.0) > 1e-5) {
                    sb.append("  The lag is not a straight line, so one scale is only an average.\n");
                }
            } else if (c.windowsTried == 0) {
                sb.append("  The recording is shorter than one window plus two steps. Shorten the window and the step.\n");
            } else {
                sb.append(String.format(Locale.ROOT,
                        "  cross-correlation did not fit a rate (%d of %d windows).\n",
                        c.windowsUsed, c.windowsTried));
                sb.append("  Widen Max lag, lengthen the window, or lower the minimum correlation.\n");
            }
            if (c.lagSearchSaturated) {
                sb.append("  Some windows sat on the lag limit. Raise Max lag and measure again.\n");
            }
            if (!Double.isNaN(c.endpointScaleVsComputer)) {
                double gapUs = computerSpanUs - (double) (c.lastUs - c.firstUs);
                sb.append(String.format(Locale.ROOT,
                        "  filename-to-modification minus timestamp span: %s. "
                                + "That difference is time before the first event and after the last, so it is not a clock rate.\n",
                        formatSignedUs(gapUs)));
            }
            sb.append('\n');
        }
        if (!anyFit) {
            sb.append("No camera produced a cross-correlation rate.\n");
            return wrapReport(sb.toString());
        }
        String referenceName = shortName(referenceLabel);
        for (CameraFit c : fits) {
            if (!c.rateWasFit()) {
                continue;
            }
            String direction = c.scaleVsReference < 1.0 ? "slow" : "fast";
            String start = c.offsetAtStartUs >= 0.0 ? "after" : "before";
            String end = c.lagAtEndUs < 0.0 ? "earlier" : "later";
            sb.append("Conclusion: ").append(shortName(c.label)).append(" is ")
                    .append(ppm(c.scaleVsReference)).append(' ').append(direction)
                    .append(" versus ").append(referenceName)
                    .append(" and starts ").append(formatAbsUs(c.offsetAtStartUs)).append(' ').append(start)
                    .append(" it. At the end its timestamps are ")
                    .append(formatAbsUs(c.lagAtEndUs)).append(' ').append(end).append(".\n");
        }
        sb.append("  corrected = referenceFirst + (timestamp - referenceFirst - offset) / scale\n\n");
        sb.append("Save corrected copy writes a new file with that map applied to packet times and to event, frame, and IMU timestamps. ");
        sb.append(referenceName).append(" is copied unchanged.\n");
        return wrapReport(sb.toString());
    }

    /** Wrap each line to {@link #REPORT_WIDTH}. Continuation lines keep a hanging indent. */
    public static String wrapReport(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        StringBuilder out = new StringBuilder(text.length() + 64);
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == '\n') {
                wrapLine(out, text.substring(start, i), REPORT_WIDTH);
                if (i < text.length()) {
                    out.append('\n');
                }
                start = i + 1;
            }
        }
        return out.toString();
    }

    private static void wrapLine(StringBuilder out, String line, int width) {
        if (line.length() <= width) {
            out.append(line);
            return;
        }
        int indent = 0;
        while (indent < line.length() && line.charAt(indent) == ' ') {
            indent++;
        }
        int hangCount = indent == 0 ? 2 : Math.min(indent + 2, width / 2);
        String hang = " ".repeat(hangCount);
        int pos = 0;
        boolean first = true;
        while (pos < line.length()) {
            int room = first ? width : width - hang.length();
            if (room < 8) {
                room = 8;
            }
            if (pos + room >= line.length()) {
                if (!first) {
                    out.append(hang);
                }
                out.append(line.substring(pos));
                return;
            }
            int breakAt = line.lastIndexOf(' ', pos + room);
            if (breakAt <= pos) {
                breakAt = Math.min(line.length(), pos + room);
            }
            if (!first) {
                out.append(hang);
            }
            out.append(line.substring(pos, breakAt).stripTrailing());
            pos = breakAt;
            while (pos < line.length() && line.charAt(pos) == ' ') {
                pos++;
            }
            if (pos < line.length()) {
                out.append('\n');
            }
            first = false;
        }
    }

    static String shortName(String label) {
        if (label == null || label.isEmpty()) {
            return "the reference";
        }
        int colon = label.indexOf(": ");
        String name = colon >= 0 ? label.substring(colon + 2) : label;
        int paren = name.lastIndexOf(" (");
        if (paren > 0) {
            name = name.substring(0, paren);
        }
        return name;
    }

    static String formatAbsUs(double us) {
        String signed = formatSignedUs(us);
        if (signed.startsWith("+") || signed.startsWith("-")) {
            return signed.substring(1);
        }
        return signed;
    }

    private static String oneLine(Result result) {
        StringBuilder sb = new StringBuilder();
        sb.append("reference ").append(result.referenceLabel);
        for (CameraFit c : result.cameras) {
            if (!c.reference && c.rateWasFit()) {
                sb.append("; ").append(c.label).append(' ').append(ppm(c.scaleVsReference));
                sb.append(" (").append(formatSignedUs(c.lagAtEndUs)).append(" at end)");
            }
        }
        return sb.toString();
    }

    static String ppm(double scale) {
        return String.format(Locale.ROOT, "%+.2f ppm", (scale - 1.0) * 1e6);
    }

    static String formatSignedUs(double us) {
        double abs = Math.abs(us);
        String body;
        if (abs >= 1_000_000.0) {
            body = String.format(Locale.ROOT, "%.3f s", abs / 1e6);
        } else if (abs >= 1_000.0) {
            body = String.format(Locale.ROOT, "%.1f ms", abs / 1e3);
        } else {
            body = String.format(Locale.ROOT, "%.0f us", abs);
        }
        return (us < 0.0 ? "-" : "+") + body;
    }

    static String formatDuration(long us) {
        if (us < 0L) {
            us = 0L;
        }
        long totalMs = us / 1000L;
        long hours = totalMs / 3_600_000L;
        long minutes = (totalMs / 60_000L) % 60L;
        double seconds = (totalMs % 60_000L) / 1e3;
        if (hours > 0L) {
            return String.format(Locale.ROOT, "%dh %dm %.1fs", hours, minutes, seconds);
        }
        if (minutes > 0L) {
            return String.format(Locale.ROOT, "%dm %.1fs", minutes, seconds);
        }
        return String.format(Locale.ROOT, "%.3fs", us / 1e6);
    }

    private static String formatInstant(long epochMs, ZoneId zone) {
        ZoneId z = zone == null ? ZoneId.systemDefault() : zone;
        return INSTANT_FORMAT.format(Instant.ofEpochMilli(epochMs).atZone(z));
    }

    private static boolean report(Progress progress, String note, int percent) {
        if (progress == null) {
            return true;
        }
        return progress.update(note, percent);
    }

    /** Thrown when a measurement or corrected-copy write is stopped. */
    public static final class Cancelled extends RuntimeException {
        public Cancelled() {
            super("Clock drift measurement cancelled");
        }
    }

    public static boolean isCancelled(Throwable t) {
        return t instanceof Cancelled;
    }

    private static ByteBuffer readSizePrefixed(FileChannel channel) throws IOException {
        ByteBuffer sizeBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        readFully(channel, sizeBuffer);
        sizeBuffer.flip();
        int size = sizeBuffer.getInt();
        if (size < 0 || size > 16 * 1024 * 1024) {
            throw new IOException("Implausible AEDAT-4 header size " + size);
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
                throw new IOException("Unexpected EOF");
            }
        }
    }
}
