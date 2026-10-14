package net.sf.jaer.eventio.aedat4;

import java.io.File;
import java.nio.file.Files;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.aedat4.dv.CompressionType;
import net.sf.jaer.graphics.RecordingVcrSession;

/**
 * Headless concat / deck-detection checks. After {@code ant compile}:
 * {@code java -cp "build/classes;lib/*;jars/*" net.sf.jaer.eventio.aedat4.Aedat4ConcatDemo}
 */
public final class Aedat4ConcatDemo {

    public static void main(String[] args) throws Exception {
        testIsDeck();
        testMergeKeepsSources();
        testSpaceCheckRefuses();
        testMismatchDeletesPartial();
        testStitchClosesCassetteGap();
        testStitchLeavesSmallBoundaryGap();
        testOpenCassetteStillFeedsHistogram();
        testTrailingZeroTimeDoesNotZeroDuration();
        System.out.println("ALL PASS");
    }

    private static void testIsDeck() throws Exception {
        File tmp = Files.createTempDirectory("vcr-deck-").toFile();
        try {
            assertTrue(!RecordingVcrSession.isDeck(tmp), "empty dir");
            RecordingVcrSession s = RecordingVcrSession.begin(tmp, "Deck_t0", new Date(0L),
                    RecordingVcrSession.Mode.INFINITE, 8, 60_000L);
            File c1 = s.openNextCassette();
            writeTinyAedat4(c1, 1);
            s.closeCurrentCassette();
            File c2 = s.openNextCassette();
            writeTinyAedat4(c2, 2);
            assertTrue(RecordingVcrSession.isDeck(s.getSessionDir()), "dir with manifest");
            assertTrue(RecordingVcrSession.isDeck(c1), "cassette parent is deck");
            assertTrue(RecordingVcrSession.isDeck(s.manifestFile()), "manifest");
            assertTrue(!RecordingVcrSession.isDeck(new File(tmp, "nope.txt")), "random file");
            assertTrue(RecordingVcrSession.cassetteFilesForConcat(s.getSessionDir()).size() == 2, "2 cassettes");
        } finally {
            deleteTree(tmp);
        }
        System.out.println("PASS testIsDeck");
    }

    private static void testMergeKeepsSources() throws Exception {
        File tmp = Files.createTempDirectory("vcr-merge-").toFile();
        try {
            RecordingVcrSession s = RecordingVcrSession.begin(tmp, "Merge_t0", new Date(1L),
                    RecordingVcrSession.Mode.ROTATE, 8, 10_000L);
            File c1 = s.openNextCassette();
            writeTinyAedat4(c1, 3);
            s.closeCurrentCassette();
            File c2 = s.openNextCassette();
            writeTinyAedat4(c2, 5);
            s.closeCurrentCassette();
            File out = RecordingVcrSession.defaultConcatOutput(s.getSessionDir());
            Aedat4Concat.Result r = Aedat4Concat.merge(s.getSessionDir(), out);
            assertTrue(out.isFile() && out.length() > 0, "output exists");
            assertTrue(c1.isFile() && c2.isFile(), "sources kept");
            assertTrue(!new File(out.getPath() + Aedat4Concat.PARTIAL_SUFFIX).exists(), "no partial");
            assertTrue(r.packets > 0, "copied packets");
        } finally {
            deleteTree(tmp);
        }
        System.out.println("PASS testMergeKeepsSources");
    }

    private static void testSpaceCheckRefuses() throws Exception {
        File tmp = Files.createTempDirectory("vcr-space-").toFile();
        try {
            File a = new File(tmp, "a.aedat4");
            writeTinyAedat4(a, 1);
            File dest = new File(tmp, "out.aedat4");
            Aedat4Concat.SpaceCheck check = Aedat4Concat.checkSpace(List.of(a), dest, 1L << 60);
            assertTrue(!check.enough, "huge minFree fails");
            assertTrue(!dest.exists(), "no output file");
        } finally {
            deleteTree(tmp);
        }
        System.out.println("PASS testSpaceCheckRefuses");
    }

    private static void testMismatchDeletesPartial() throws Exception {
        File tmp = Files.createTempDirectory("vcr-bad-").toFile();
        try {
            File a = new File(tmp, "a.aedat4");
            writeTinyAedat4(a, 1);
            File b = new File(tmp, "b.aedat4");
            Files.writeString(b.toPath(), "not aedat4");
            File dest = new File(tmp, "out.aedat4");
            try {
                Aedat4Concat.mergeFiles(List.of(a, b), dest);
                throw new AssertionError("expected mismatch failure");
            } catch (Exception expected) {
                assertTrue(expected.getMessage() != null, "message");
            }
            assertTrue(!dest.exists(), "no dest");
            assertTrue(!new File(dest.getPath() + Aedat4Concat.PARTIAL_SUFFIX).exists(), "partial deleted");
            assertTrue(a.isFile(), "source a kept");
            assertTrue(b.isFile(), "source b kept");
        } finally {
            deleteTree(tmp);
        }
        System.out.println("PASS testMismatchDeletesPartial");
    }

    private static void testStitchClosesCassetteGap() throws Exception {
        File tmp = Files.createTempDirectory("vcr-stitch-").toFile();
        try {
            long base = 1_700_000_000_000_000L;
            File c1 = new File(tmp, "c0001.aedat4");
            File c2 = new File(tmp, "c0002.aedat4");
            writeTinyAedat4(c1, 3, base, 1_000);
            writeTinyAedat4(c2, 3, base + 60_000_000L, 2_000);
            File out = new File(tmp, "merged.aedat4");
            Aedat4Concat.mergeFiles(List.of(c1, c2), out);
            List<long[]> ranges = Aedat4Concat.packetTimeRanges(out);
            assertTrue(ranges.size() == 2, "two packets");
            long firstEnd = ranges.get(0)[1];
            long secondStart = ranges.get(1)[0];
            long gap = secondStart - firstEnd;
            assertTrue(gap >= 0 && gap <= 2, "cassette 60s jump closed, gapUs=" + gap);
            long firstSpan = ranges.get(0)[1] - ranges.get(0)[0];
            assertTrue(firstSpan == 2, "intra-cassette event spacing kept");
        } finally {
            deleteTree(tmp);
        }
        System.out.println("PASS testStitchClosesCassetteGap");
    }

    private static void testStitchLeavesSmallBoundaryGap() throws Exception {
        File tmp = Files.createTempDirectory("vcr-noshift-").toFile();
        try {
            long base = 1_700_000_000_000_000L;
            File c1 = new File(tmp, "c0001.aedat4");
            File c2 = new File(tmp, "c0002.aedat4");
            writeTinyAedat4(c1, 1, base, 1_000);
            writeTinyAedat4(c2, 1, base, 21_000);
            File out = new File(tmp, "merged.aedat4");
            Aedat4Concat.mergeFiles(List.of(c1, c2), out);
            List<long[]> ranges = Aedat4Concat.packetTimeRanges(out);
            assertTrue(ranges.size() == 2, "two packets");
            long gap = ranges.get(1)[0] - ranges.get(0)[1];
            assertTrue(gap == 20_000L, "20ms boundary gap left as-is, gapUs=" + gap);
        } finally {
            deleteTree(tmp);
        }
        System.out.println("PASS testStitchLeavesSmallBoundaryGap");
    }

    /**
     * Last cassette discarded the way a VCR roll still being written is: packets
     * on disk, FileDataTable not written yet. Those LBEV times must survive concat
     * and the merged file must still have a playback duration and sparkline.
     */
    private static void testOpenCassetteStillFeedsHistogram() throws Exception {
        File tmp = Files.createTempDirectory("vcr-open-cassette-").toFile();
        try {
            long base = 1_700_000_000_000_000L;
            File closed = new File(tmp, "c0001.aedat4");
            File open = new File(tmp, "c0002.aedat4");
            writeLossy(closed, base, new int[] { 1_000, 2_000 }, true);
            writeLossy(open, base, new int[] { 0, 20_000 }, false);
            File out = new File(tmp, "merged.aedat4");
            Aedat4Concat.mergeFiles(List.of(closed, open), out);
            List<long[]> ranges = Aedat4Concat.packetTimeRanges(out);
            assertTrue(ranges.size() >= 2, "closed plus open cassette packets, n=" + ranges.size());
            for (long[] range : ranges) {
                assertTrue(range[0] > 1_000_000_000_000L && range[1] >= range[0],
                        "packet time not left at 0: " + range[0] + ".." + range[1]);
            }
            Aedat4FileInputStream in = new Aedat4FileInputStream(out, null);
            try {
                assertTrue(in.getDurationUsLong() > 0, "duration " + in.getDurationUsLong());
                float[] rates = in.getLogRelativeEventRateByTime();
                assertTrue(rates != null && rates.length > 0, "sparkline bins");
                boolean any = false;
                for (float v : rates) {
                    if (v > 0f) {
                        any = true;
                        break;
                    }
                }
                assertTrue(any, "sparkline has activity");
            } finally {
                in.close();
            }
        } finally {
            deleteTree(tmp);
        }
        System.out.println("PASS testOpenCassetteStillFeedsHistogram");
    }

    /** The already-merged spider file: last rows are time 0, so last end is before the origin. */
    private static void testTrailingZeroTimeDoesNotZeroDuration() {
        long span = Aedat4FileInputStream.polarityTimelineSpanUs(0L,
                new long[] { 90_007_957_504L, -1_791_897_491_726_336L });
        assertTrue(span == 90_007_957_504L, "span " + span);
        long monotonic = Aedat4FileInputStream.polarityTimelineSpanUs(0L, new long[] { 500L, 2_000L });
        assertTrue(monotonic == 2_000L, "monotonic " + monotonic);
        System.out.println("PASS testTrailingZeroTimeDoesNotZeroDuration");
    }

    private static void writeLossy(File file, long baseUnixUs, int[] timestamps, boolean close) throws Exception {
        PacketBundle bundle = new PacketBundle();
        net.sf.jaer.event.EventPacket<PolarityEvent> events = new net.sf.jaer.event.EventPacket<>(PolarityEvent.class);
        OutputEventIterator<PolarityEvent> it = events.outputIterator();
        for (int i = 0; i < timestamps.length; i++) {
            PolarityEvent event = it.nextOutput();
            event.timestamp = timestamps[i];
            event.x = (short) i;
            event.y = (short) (i + 1);
            event.setPolarity(PolarityEvent.Polarity.On);
        }
        bundle.add(events);
        Aedat4FileOutputStream output = new Aedat4FileOutputStream(file, null, CompressionType.LZ4, baseUnixUs,
                ZoneId.of("UTC"), Aedat4LossyTimeBins.SHIFT_DEFAULT);
        output.writeBundle(bundle);
        if (close) {
            output.close();
        } else {
            output.discard();
        }
    }

    private static void writeTinyAedat4(File file, int nEvents) throws Exception {
        writeTinyAedat4(file, nEvents, 0L, 1000);
    }

    private static void writeTinyAedat4(File file, int nEvents, long baseUnixUs, int firstTs) throws Exception {
        PacketBundle bundle = new PacketBundle();
        net.sf.jaer.event.EventPacket<PolarityEvent> events = new net.sf.jaer.event.EventPacket<>(PolarityEvent.class);
        OutputEventIterator<PolarityEvent> out = events.outputIterator();
        for (int i = 0; i < nEvents; i++) {
            PolarityEvent event = out.nextOutput();
            event.timestamp = firstTs + i;
            event.x = (short) i;
            event.y = (short) (i + 1);
            event.setPolarity((i & 1) == 0 ? PolarityEvent.Polarity.On : PolarityEvent.Polarity.Off);
        }
        bundle.add(events);
        Aedat4FileOutputStream output = baseUnixUs > 0L
                ? new Aedat4FileOutputStream(file, null, net.sf.jaer.eventio.aedat4.dv.CompressionType.LZ4, baseUnixUs)
                : new Aedat4FileOutputStream(file, null);
        try (output) {
            output.writeBundle(bundle);
        }
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                deleteTree(k);
            }
        }
        f.delete();
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }
}
