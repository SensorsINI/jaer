package net.sf.jaer.eventio.aedat4;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.junit.BeforeClass;
import org.junit.Test;

import ch.unizh.ini.jaer.chip.retina.DVS128;
import eu.seebetter.ini.chips.davis.DAVIS240C;
import eu.seebetter.ini.chips.davis.Davis346blue;
import eu.seebetter.ini.chips.davis.Davis346redColor;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.EventExtractor2D;
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.eventio.AEFileInputStream.Marks;
import net.sf.jaer.eventprocessing.filter.AreaEventCountExposer;
import net.sf.jaer.util.SampleDataSupport;
import nrv.chip.NRVS5KRC1S;
import prophesee.chip.PropheseeIMX636HD;

/**
 * Headless playback slicing for AEDAT-4 sample recordings.
 * <p>
 * Mirrors the three AEPlayer exposure modes ({@code CountDuration} /
 * {@code ConstantCount} / {@code AreaEventCount}), single-step determinism,
 * rewind with and without IN, and IN/OUT marker bounds (repeat on/off).
 * {@code close()} persists marks — tests restore the prefs cache so sample
 * files keep the user's IN/OUT.
 * Skips when {@code sampleData/*.aedat4} is not present.
 */
public class Aedat4FileInputStreamSlicingTest {

    /** Same 40 ms the GUI bug report used. */
    private static final int DURATION_US = 40_000;
    /** Same ConstantCount the GUI bug report used. */
    private static final int COUNT_EVENTS = 245;
    private static final int AREA_EVENTS = 245;
    private static final int AREA_NUM = 32;
    private static final int AREA_CHUNK = 256;
    /** First-50-from-start missed the GUI short slices; sample several places along the file. */
    private static final float[] SEEK_FRACS = {0f, 0.2f, 0.4f, 0.55f, 0.7f, 0.85f};
    private static final int SLICES_PER_REGION = 12;
    /** Dense slice: enough events that a 40 ms window should not collapse to &lt;10 ms. */
    private static final int MIN_EVENTS_FOR_DURATION_CHECK = 200;
    /** Actual packed duration below this fraction of the request is a short-slice failure. */
    private static final double MIN_DURATION_FRACTION = 0.25;
    private static final int MAX_EVENTS_PER_READ = AEFileInputStream.MAX_BUFFER_SIZE_EVENTS;

    private static List<File> recordings;

    @BeforeClass
    public static void findSampleRecordings() {
        recordings = listAedat4();
    }

    @Test
    public void constantDurationSlicesMatchRequestedDt() throws Exception {
        assumeRecordingsPresent();
        List<String> failures = new ArrayList<>();
        for (File file : recordings) {
            AEChip chip = chipFor(file);
            if (chip == null) {
                continue;
            }
            Marks saved = AEFileInputStream.marksGetForFile(file);
            Aedat4FileInputStream in = new Aedat4FileInputStream(file, chip);
            try {
                if (in.size() < 2) {
                    continue;
                }
                SliceStats stats = collectDurationSlices(in, DURATION_US);
                System.out.println(formatDurationReport(file, stats));
                String err = durationFailure(file, stats);
                if (err != null) {
                    failures.add(err);
                }
            } finally {
                closeRestoringMarks(in, file, saved);
            }
        }
        if (!failures.isEmpty()) {
            fail(String.join("\n", failures));
        }
    }

    @Test
    public void constantCountSlicesMatchRequestedN() throws Exception {
        assumeRecordingsPresent();
        List<String> failures = new ArrayList<>();
        for (File file : recordings) {
            AEChip chip = chipFor(file);
            if (chip == null) {
                continue;
            }
            Marks saved = AEFileInputStream.marksGetForFile(file);
            Aedat4FileInputStream in = new Aedat4FileInputStream(file, chip);
            try {
                if (in.size() < COUNT_EVENTS) {
                    continue;
                }
                CountStats stats = collectCountSlices(in, COUNT_EVENTS);
                System.out.println(formatCountReport(file, stats));
                if (stats.wrongAdvance > 0) {
                    failures.add(String.format(Locale.ROOT,
                            "%s ConstantCount: %d/%d slices advanced %d..%d events, want %d (packed %d..%d)",
                            file.getName(), stats.wrongAdvance, stats.slices,
                            stats.minConsumed, stats.maxConsumed, COUNT_EVENTS,
                            stats.minPacked, stats.maxPacked));
                }
            } finally {
                closeRestoringMarks(in, file, saved);
            }
        }
        if (!failures.isEmpty()) {
            fail(String.join("\n", failures));
        }
    }

    @Test
    public void areaEventCountSlicesExposeWhenAreaFills() throws Exception {
        assumeRecordingsPresent();
        List<String> failures = new ArrayList<>();
        for (File file : recordings) {
            AEChip chip = chipFor(file);
            if (chip == null || chip.getEventExtractor() == null) {
                continue;
            }
            Marks saved = AEFileInputStream.marksGetForFile(file);
            Aedat4FileInputStream in = new Aedat4FileInputStream(file, chip);
            try {
                if (in.size() < AREA_EVENTS) {
                    continue;
                }
                AreaStats stats = collectAreaSlices(in, chip, AREA_EVENTS, AREA_NUM);
                System.out.println(formatAreaReport(file, stats));
                if (stats.unexposed > 0) {
                    failures.add(String.format(Locale.ROOT,
                            "%s AreaEventCount: %d/%d slices never filled an area of %d events (packed %d..%d)",
                            file.getName(), stats.unexposed, stats.slices, AREA_EVENTS,
                            stats.minPacked, stats.maxPacked));
                }
                if (stats.overshoot > 0) {
                    failures.add(String.format(Locale.ROOT,
                            "%s AreaEventCount: %d/%d slices packed %d..%d events (cap %d = %d areas * %d ev * 4)",
                            file.getName(), stats.overshoot, stats.slices,
                            stats.minPacked, stats.maxPacked, AREA_EVENTS * AREA_NUM * 4,
                            AREA_NUM, AREA_EVENTS));
                }
            } finally {
                closeRestoringMarks(in, file, saved);
            }
        }
        if (!failures.isEmpty()) {
            fail(String.join("\n", failures));
        }
    }

    @Test
    public void singleStepReadsAreDeterministic() throws Exception {
        assumeRecordingsPresent();
        List<String> failures = new ArrayList<>();
        for (File file : recordings) {
            AEChip chip = chipFor(file);
            if (chip == null) {
                continue;
            }
            Marks saved = AEFileInputStream.marksGetForFile(file);
            Aedat4FileInputStream in = new Aedat4FileInputStream(file, chip);
            try {
                long n = in.size();
                if (n < COUNT_EVENTS * 4L) {
                    continue;
                }
                int mismatches = 0;
                int checks = 0;
                String firstDiff = null;
                long[] seeks = {0L, n / 8, n / 4, n / 2, (3 * n) / 4};
                for (long pos : seeks) {
                    if (pos < 0 || pos >= n - 2) {
                        continue;
                    }
                    in.position(pos);
                    AEPacketRaw a = readOneSlice(in, DURATION_US, COUNT_EVENTS);
                    in.position(pos);
                    AEPacketRaw b = readOneSlice(in, DURATION_US, COUNT_EVENTS);
                    checks++;
                    String diff = packetsDiffer(a, b);
                    if (diff != null) {
                        mismatches++;
                        if (firstDiff == null) {
                            firstDiff = String.format(Locale.ROOT, "pos=%d %s", pos, diff);
                        }
                    }
                    in.rewind();
                    AEPacketRaw seq1 = readOneSlice(in, DURATION_US, COUNT_EVENTS);
                    in.rewind();
                    AEPacketRaw seq2 = readOneSlice(in, DURATION_US, COUNT_EVENTS);
                    checks++;
                    diff = packetsDiffer(seq1, seq2);
                    if (diff != null) {
                        mismatches++;
                        if (firstDiff == null) {
                            firstDiff = "rewind+first-slice " + diff;
                        }
                    }
                }
                System.out.printf(Locale.ROOT, "%s single-step: %d checks, %d mismatches%n",
                        file.getName(), checks, mismatches);
                if (mismatches > 0) {
                    failures.add(file.getName() + " single-step not deterministic: " + firstDiff);
                }
            } finally {
                closeRestoringMarks(in, file, saved);
            }
        }
        if (!failures.isEmpty()) {
            fail(String.join("\n", failures));
        }
    }

    @Test
    public void rewindAndInOutMarks() throws Exception {
        assumeRecordingsPresent();
        List<String> failures = new ArrayList<>();
        for (File file : recordings) {
            AEChip chip = chipFor(file);
            if (chip == null) {
                continue;
            }
            Marks saved = AEFileInputStream.marksGetForFile(file);
            Aedat4FileInputStream in = new Aedat4FileInputStream(file, chip);
            try {
                long n = in.size();
                if (n < COUNT_EVENTS * 20L) {
                    System.out.println(file.getName() + " rewind/marks: skip (too few events)");
                    continue;
                }
                String err = checkRewindWithoutMarks(in);
                if (err != null) {
                    failures.add(file.getName() + " rewind-no-marks: " + err);
                }
                err = checkRewindWithMarkIn(in);
                if (err != null) {
                    failures.add(file.getName() + " rewind-with-IN: " + err);
                }
                err = checkInOutBounds(in);
                if (err != null) {
                    failures.add(file.getName() + " IN/OUT: " + err);
                }
                System.out.println(file.getName() + " rewind/marks: ok");
            } finally {
                closeRestoringMarks(in, file, saved);
            }
        }
        if (!failures.isEmpty()) {
            fail(String.join("\n", failures));
        }
    }

    private static void assumeRecordingsPresent() {
        assumeTrue("sampleData/*.aedat4 not found (Help > Sample data download)",
                recordings != null && !recordings.isEmpty());
    }

    private static List<File> listAedat4() {
        List<File> dirs = new ArrayList<>();
        dirs.add(SampleDataSupport.folder());
        File install = new File("D:\\jAER\\sampleData");
        if (!dirs.contains(install)) {
            dirs.add(install);
        }
        List<File> out = new ArrayList<>();
        for (File dir : dirs) {
            File[] files = dir.isDirectory() ? dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".aedat4")) : null;
            if (files == null) {
                continue;
            }
            Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File f : files) {
                if (f.isFile() && f.length() > 0) {
                    out.add(f);
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        return out;
    }

    private static void closeRestoringMarks(Aedat4FileInputStream in, File file, Marks saved) throws IOException {
        try {
            if (in != null) {
                in.close();
            }
        } finally {
            AEFileInputStream.marksPutForFile(file, saved);
        }
    }

    /** Rewind with no IN/OUT must land at event 0 and replay the first slice. */
    private static String checkRewindWithoutMarks(Aedat4FileInputStream in) throws IOException {
        in.clearMarks();
        in.setRepeat(false);
        in.rewind();
        if (in.position() != 0) {
            return "rewind without marks left position=" + in.position();
        }
        AEPacketRaw first = in.readPacketByNumber(COUNT_EVENTS);
        in.readPacketByNumber(COUNT_EVENTS);
        in.readPacketByNumber(COUNT_EVENTS);
        in.rewind();
        if (in.position() != 0) {
            return "second rewind without marks left position=" + in.position();
        }
        AEPacketRaw again = in.readPacketByNumber(COUNT_EVENTS);
        String diff = packetsDiffer(first, again);
        return diff == null ? null : "first slice after rewind differed: " + diff;
    }

    /** Rewind with IN set must land on IN, not 0, and replay that slice. */
    private static String checkRewindWithMarkIn(Aedat4FileInputStream in) throws IOException {
        in.clearMarks();
        in.setRepeat(false);
        long n = in.size();
        long inPos = Math.max(COUNT_EVENTS, n / 10);
        if (inPos >= n - COUNT_EVENTS * 2L) {
            return null;
        }
        in.position(inPos);
        if (in.setMarkIn() != inPos || !in.isMarkInSet()) {
            return "setMarkIn at " + inPos + " did not stick (got " + in.getMarkInPosition() + ")";
        }
        AEPacketRaw fromIn = in.readPacketByNumber(COUNT_EVENTS);
        in.readPacketByNumber(COUNT_EVENTS);
        if (in.position() <= inPos) {
            return "did not advance past IN";
        }
        in.rewind();
        if (in.position() != inPos) {
            return "rewind went to " + in.position() + ", want IN=" + inPos;
        }
        AEPacketRaw again = in.readPacketByNumber(COUNT_EVENTS);
        String diff = packetsDiffer(fromIn, again);
        return diff == null ? null : "slice at IN after rewind differed: " + diff;
    }

    /**
     * IN/OUT window: repeat-off stops at OUT; repeat-on wraps to IN; slider
     * seek to/past OUT continues; clearMarks restores the full file.
     */
    private static String checkInOutBounds(Aedat4FileInputStream in) throws IOException {
        in.clearMarks();
        long n = in.size();
        long inPos = Math.max(COUNT_EVENTS, n / 10);
        long span = Math.min(COUNT_EVENTS * 8L, Math.max(COUNT_EVENTS * 2L, n / 50));
        long outPos = Math.min(n - 1, inPos + span);
        if (outPos <= inPos + COUNT_EVENTS) {
            return null;
        }
        in.position(inPos);
        in.setMarkIn();
        in.position(outPos);
        in.setMarkOut();
        if (!in.isMarkOutSet() || in.getMarkOutPosition() != outPos) {
            return "setMarkOut at " + outPos + " did not stick (got " + in.getMarkOutPosition() + ")";
        }

        in.setRepeat(false);
        in.rewind();
        long consumed = 0;
        int slices = 0;
        try {
            while (slices++ < 10_000) {
                long p0 = in.position();
                in.readPacketByNumber(COUNT_EVENTS);
                long p1 = in.position();
                if (p1 > outPos) {
                    return "repeat-off read passed OUT: pos " + p0 + "->" + p1 + " OUT=" + outPos;
                }
                if (p1 < p0) {
                    return "repeat-off went backward " + p0 + "->" + p1;
                }
                consumed += p1 - p0;
            }
            return "repeat-off never EOF after " + slices + " slices";
        } catch (EOFException expected) {
            // playback reached OUT
        }
        if (consumed != outPos - inPos) {
            return String.format(Locale.ROOT,
                    "repeat-off consumed %d events in [%d,%d), want %d",
                    consumed, inPos, outPos, outPos - inPos);
        }
        if (in.position() != outPos) {
            return "repeat-off ended at " + in.position() + ", want OUT=" + outPos;
        }

        in.setRepeat(true);
        in.rewind();
        boolean wrapped = false;
        long prev = in.position();
        for (int i = 0; i < 40; i++) {
            in.readPacketByNumber(COUNT_EVENTS);
            long now = in.position();
            if (now < prev) {
                wrapped = true;
                long expectAfterWrapRead = inPos + Math.min(COUNT_EVENTS, outPos - inPos);
                if (now != expectAfterWrapRead) {
                    return "repeat wrap landed at " + now + ", want " + expectAfterWrapRead
                            + " (IN=" + inPos + ")";
                }
                break;
            }
            if (now > outPos) {
                return "repeat-on read passed OUT: " + now;
            }
            prev = now;
        }
        if (!wrapped) {
            return "repeat-on never wrapped at OUT";
        }

        in.setRepeat(false);
        in.position(outPos);
        try {
            AEPacketRaw past = in.readPacketByNumber(COUNT_EVENTS);
            if (past.getNumEvents() == 0) {
                return "seek to OUT then read returned empty (loop should disarm)";
            }
            if (in.position() <= outPos) {
                return "seek to OUT did not advance past OUT (pos=" + in.position() + ")";
            }
        } catch (EOFException e) {
            return "seek to OUT then read EOF'd; slider seek should disarm OUT";
        }

        in.clearMarks();
        if (in.isMarkInSet() || in.isMarkOutSet()) {
            return "clearMarks left IN=" + in.getMarkInPosition() + " OUT=" + in.getMarkOutPosition();
        }
        in.rewind();
        if (in.position() != 0) {
            return "rewind after clearMarks left position=" + in.position();
        }
        in.position(outPos);
        try {
            in.readPacketByNumber(COUNT_EVENTS);
        } catch (EOFException e) {
            return "after clearMarks, read at former OUT EOF'd";
        }
        return null;
    }

    private static AEChip chipFor(File file) {
        String n = file.getName();
        if (n.startsWith("PropheseeIMX636HD")) {
            return new PropheseeIMX636HD();
        }
        if (n.startsWith("DAVIS240C")) {
            return new DAVIS240C();
        }
        if (n.startsWith("Davis346redColor")) {
            return new Davis346redColor();
        }
        if (n.startsWith("Davis346") || n.startsWith("DDD20")) {
            return new Davis346blue();
        }
        if (n.startsWith("DVS128")) {
            return new DVS128();
        }
        if (n.startsWith("NRV") || n.contains("DELTA01")) {
            return new NRVS5KRC1S();
        }
        System.out.println("skip (no chip mapping): " + n);
        return null;
    }

    private static AEPacketRaw readOneSlice(Aedat4FileInputStream in, int dtUs, int nEvents) throws IOException {
        try {
            return in.readPacketByTime(dtUs);
        } catch (EOFException e) {
            try {
                return in.readPacketByNumber(nEvents);
            } catch (EOFException e2) {
                return new AEPacketRaw(0);
            }
        }
    }

    private static SliceStats collectDurationSlices(Aedat4FileInputStream in, int dtUs) throws IOException {
        SliceStats s = new SliceStats();
        s.requestedUs = dtUs;
        for (float frac : SEEK_FRACS) {
            in.setFractionalPosition(frac);
            for (int i = 0; i < SLICES_PER_REGION; i++) {
                long remaining = in.size() - in.position();
                if (remaining <= 1) {
                    break;
                }
                AEPacketRaw pkt;
                try {
                    pkt = in.readPacketByTime(dtUs);
                } catch (EOFException e) {
                    break;
                }
                s.slices++;
                int n = pkt.getNumEvents();
                s.minPacked = Math.min(s.minPacked, n);
                s.maxPacked = Math.max(s.maxPacked, n);
                boolean eofish = remaining < MAX_EVENTS_PER_READ && in.position() >= in.size();
                boolean capped = n >= MAX_EVENTS_PER_READ * 99 / 100;
                if (capped) {
                    s.capped++;
                    continue;
                }
                if (eofish) {
                    s.eof++;
                    continue;
                }
                long dur = durationUs(pkt);
                s.durations.add(dur);
                if (n >= MIN_EVENTS_FOR_DURATION_CHECK && dur < (long) (MIN_DURATION_FRACTION * dtUs)) {
                    s.shortDense++;
                    if (s.exampleShortUs == 0) {
                        s.exampleShortUs = dur;
                        s.exampleShortEvents = n;
                    }
                }
            }
        }
        Collections.sort(s.durations);
        return s;
    }

    private static CountStats collectCountSlices(Aedat4FileInputStream in, int nEvents) throws IOException {
        CountStats s = new CountStats();
        for (float frac : SEEK_FRACS) {
            in.setFractionalPosition(frac);
            for (int i = 0; i < SLICES_PER_REGION; i++) {
                long remaining = in.size() - in.position();
                if (remaining <= 0) {
                    break;
                }
                long pos0 = in.position();
                AEPacketRaw pkt;
                try {
                    pkt = in.readPacketByNumber(nEvents);
                } catch (EOFException e) {
                    break;
                }
                long consumed = in.position() - pos0;
                int packed = pkt.getNumEvents();
                s.slices++;
                s.minPacked = Math.min(s.minPacked, packed);
                s.maxPacked = Math.max(s.maxPacked, packed);
                s.minConsumed = Math.min(s.minConsumed, consumed);
                s.maxConsumed = Math.max(s.maxConsumed, consumed);
                long expect = Math.min(nEvents, remaining);
                if (consumed != expect) {
                    s.wrongAdvance++;
                }
            }
        }
        return s;
    }

    private static AreaStats collectAreaSlices(Aedat4FileInputStream in, AEChip chip, int areaEvents, int numAreas)
            throws IOException {
        AreaStats s = new AreaStats();
        AreaEventCountExposer exposer = new AreaEventCountExposer(chip);
        exposer.setEventExposureMode(AreaEventCountExposer.EventExposureMode.AreaEventCount);
        exposer.setEventCount(areaEvents);
        exposer.setNumAreas(numAreas);
        EventExtractor2D extractor = chip.getEventExtractor();
        final int overshootCap = areaEvents * numAreas * 4;
        for (float frac : SEEK_FRACS) {
            in.setFractionalPosition(frac);
            AEPacketRaw leftover = null;
            for (int i = 0; i < SLICES_PER_REGION; i++) {
                if (in.position() >= in.size() && (leftover == null || leftover.getNumEvents() == 0)) {
                    break;
                }
                exposer.resetAccumulation();
                AEPacketRaw out = new AEPacketRaw(0);
                int guard = 0;
                boolean exposed = false;
                while (!exposer.isExposed() && guard++ < 20_000) {
                    AEPacketRaw chunk;
                    if (leftover != null && leftover.getNumEvents() > 0) {
                        chunk = leftover;
                        leftover = null;
                    } else {
                        try {
                            chunk = in.readPacketByNumber(AREA_CHUNK);
                        } catch (EOFException e) {
                            break;
                        }
                    }
                    if (chunk == null || chunk.getNumEvents() == 0) {
                        break;
                    }
                    int n = chunk.getNumEvents();
                    int cut = exposer.addRawEvents(chunk.getAddresses(), chunk.getTimestamps(), n, extractor);
                    if (cut < 0) {
                        out = appendRaw(out, chunk);
                    } else {
                        int used = cut + 1;
                        out = appendRaw(out, copyRawRange(chunk, 0, used));
                        if (used < n) {
                            leftover = copyRawRange(chunk, used, n - used);
                        }
                        exposed = true;
                        break;
                    }
                }
                if (out.getNumEvents() == 0) {
                    break;
                }
                s.slices++;
                int packed = out.getNumEvents();
                s.minPacked = Math.min(s.minPacked, packed);
                s.maxPacked = Math.max(s.maxPacked, packed);
                if (!exposed && !exposer.isExposed()) {
                    s.unexposed++;
                }
                if (packed > overshootCap) {
                    s.overshoot++;
                }
            }
        }
        return s;
    }

    private static String durationFailure(File file, SliceStats stats) {
        if (stats.durations.isEmpty()) {
            return null;
        }
        long med = percentile(stats.durations, 0.5);
        long min = stats.durations.get(0);
        if (stats.shortDense > 0) {
            return String.format(Locale.ROOT,
                    "%s CountDuration: %d/%d dense slices lasted < %.0f%% of %d us (example %d us / %d events; min=%d median=%d max=%d)",
                    file.getName(), stats.shortDense, stats.durations.size(),
                    MIN_DURATION_FRACTION * 100, stats.requestedUs,
                    stats.exampleShortUs, stats.exampleShortEvents,
                    min, med, stats.durations.get(stats.durations.size() - 1));
        }
        if (med < (long) (0.5 * stats.requestedUs) && stats.durations.size() >= 8) {
            return String.format(Locale.ROOT,
                    "%s CountDuration: median duration %d us, requested %d us",
                    file.getName(), med, stats.requestedUs);
        }
        return null;
    }

    private static String formatDurationReport(File file, SliceStats s) {
        if (s.durations.isEmpty()) {
            return String.format(Locale.ROOT, "%s CountDuration: slices=%d (no dense windows) packed=%d..%d capped=%d",
                    file.getName(), s.slices, s.minPacked == Integer.MAX_VALUE ? 0 : s.minPacked, s.maxPacked, s.capped);
        }
        return String.format(Locale.ROOT,
                "%s CountDuration: slices=%d durUs min/p50/max=%d/%d/%d packed=%d..%d shortDense=%d capped=%d",
                file.getName(), s.slices, s.durations.get(0), percentile(s.durations, 0.5),
                s.durations.get(s.durations.size() - 1), s.minPacked, s.maxPacked, s.shortDense, s.capped);
    }

    private static String formatCountReport(File file, CountStats s) {
        return String.format(Locale.ROOT,
                "%s ConstantCount: slices=%d consumed=%d..%d packed=%d..%d wrongAdvance=%d want=%d",
                file.getName(), s.slices, s.minConsumed == Long.MAX_VALUE ? 0 : s.minConsumed, s.maxConsumed,
                s.minPacked == Integer.MAX_VALUE ? 0 : s.minPacked, s.maxPacked, s.wrongAdvance, COUNT_EVENTS);
    }

    private static String formatAreaReport(File file, AreaStats s) {
        return String.format(Locale.ROOT,
                "%s AreaEventCount: slices=%d packed=%d..%d unexposed=%d overshoot=%d (N=%d areas=%d)",
                file.getName(), s.slices, s.minPacked == Integer.MAX_VALUE ? 0 : s.minPacked, s.maxPacked,
                s.unexposed, s.overshoot, AREA_EVENTS, AREA_NUM);
    }

    private static long durationUs(AEPacketRaw pkt) {
        int n = pkt.getNumEvents();
        if (n < 2) {
            return 0;
        }
        int[] ts = pkt.getTimestamps();
        long t0 = ts[0] & 0xffffffffL;
        long t1 = ts[n - 1] & 0xffffffffL;
        long d = t1 - t0;
        if (d < 0) {
            d += 1L << 32;
        }
        return d;
    }

    private static long percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int i = (int) Math.round(p * (sorted.size() - 1));
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, i)));
    }

    private static String packetsDiffer(AEPacketRaw a, AEPacketRaw b) {
        if (a == null || b == null) {
            return "null packet";
        }
        if (a.getNumEvents() != b.getNumEvents()) {
            return "n=" + a.getNumEvents() + " vs " + b.getNumEvents();
        }
        int n = a.getNumEvents();
        int[] aa = a.getAddresses();
        int[] ba = b.getAddresses();
        int[] at = a.getTimestamps();
        int[] bt = b.getTimestamps();
        for (int i = 0; i < n; i++) {
            if (aa[i] != ba[i] || at[i] != bt[i]) {
                return String.format(Locale.ROOT, "event[%d] addr %d/%d ts %d/%d", i, aa[i], ba[i], at[i], bt[i]);
            }
        }
        return null;
    }

    private static AEPacketRaw copyRawRange(AEPacketRaw src, int from, int len) {
        if (src == null || len <= 0) {
            return new AEPacketRaw(0);
        }
        AEPacketRaw dest = new AEPacketRaw(len);
        System.arraycopy(src.getAddresses(), from, dest.getAddresses(), 0, len);
        System.arraycopy(src.getTimestamps(), from, dest.getTimestamps(), 0, len);
        dest.setNumEvents(len);
        return dest;
    }

    private static AEPacketRaw appendRaw(AEPacketRaw a, AEPacketRaw b) {
        if (a == null || a.getNumEvents() == 0) {
            return copyRawRange(b, 0, b.getNumEvents());
        }
        if (b == null || b.getNumEvents() == 0) {
            return a;
        }
        int na = a.getNumEvents();
        int nb = b.getNumEvents();
        AEPacketRaw out = new AEPacketRaw(na + nb);
        System.arraycopy(a.getAddresses(), 0, out.getAddresses(), 0, na);
        System.arraycopy(a.getTimestamps(), 0, out.getTimestamps(), 0, na);
        System.arraycopy(b.getAddresses(), 0, out.getAddresses(), na, nb);
        System.arraycopy(b.getTimestamps(), 0, out.getTimestamps(), na, nb);
        out.setNumEvents(na + nb);
        return out;
    }

    private static final class SliceStats {
        int requestedUs;
        int slices;
        int capped;
        int eof;
        int shortDense;
        int minPacked = Integer.MAX_VALUE;
        int maxPacked;
        long exampleShortUs;
        int exampleShortEvents;
        final List<Long> durations = new ArrayList<>();

        SliceStats() {
        }
    }

    private static final class CountStats {
        int slices;
        int wrongAdvance;
        int minPacked = Integer.MAX_VALUE;
        int maxPacked;
        long minConsumed = Long.MAX_VALUE;
        long maxConsumed;
    }

    private static final class AreaStats {
        int slices;
        int unexposed;
        int overshoot;
        int minPacked = Integer.MAX_VALUE;
        int maxPacked;
    }
}
