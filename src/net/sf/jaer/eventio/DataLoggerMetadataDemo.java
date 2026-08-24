package net.sf.jaer.eventio;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.PotArray;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.DataLogger;

/**
 * Headless production-path test for freezing the recording-start configuration
 * snapshot into legacy AEDAT and DataLogger output (plan Todo 3, part 2).
 *
 * <p>An immutable {@link RecordingConfigurationSnapshot} captured at recording
 * start is written as one recognizable block in the legacy AEDAT header and by
 * {@link DataLogger} (which now uses its active chip and explicit AEDAT-2
 * version instead of a null-chip/null-version start that NPE'd). Each path is
 * written through the real writer, closed, reopened and the decoded snapshot
 * compared — not merely string-generation checked. Live preferences are mutated
 * (37 -&gt; 41) after capture to prove the frozen snapshot is what is recorded.
 * A snapshot captured internally by {@link DataLogger} is released on failure,
 * stop, and write error so a later recording captures fresh values; an external
 * owner snapshot is reused by identity and left for its owner to release. Each check throws
 * {@link AssertionError} on mismatch (non-zero exit) and prints PASS on
 * success.
 *
 * <p>Run headlessly after {@code ant clean compile}:
 * {@code java -cp build/classes:lib/*:jars/* net.sf.jaer.eventio.DataLoggerMetadataDemo}
 */
public class DataLoggerMetadataDemo {

    public static void main(String[] args) throws Exception {
        testLegacyAEDATSnapshot();
        testFailedInternalStartRetriesFresh();
        testExternalSnapshotReuseAndOwnership();
        testDirectFreshCaptureAndStopClear();
        testRepeatedStartKeepsActiveRecording();
        testWriteErrorClearsInternalSnapshot();
        testDataLoggerEmptyAndNoSidecar();
        System.out.println("ALL PASS");
    }

    /** Legacy AEDAT: one snapshot block with recognizable markers; 37 persists after live 41; nasty value round-trips. */
    private static void testLegacyAEDATSnapshot() throws IOException {
        AEChip chip = bareChip(withPrefs());
        chip.getPrefs().put("AEChip.level", "37");
        chip.getPrefs().put("AEChip.nasty", "a<&\"b\n\rsecond");
        RecordingConfigurationSnapshot snap = RecordingConfigurationSnapshot.captureFromChip(chip);
        chip.setRecordingConfigurationSnapshot(snap);

        File f = File.createTempFile("jaer-legacy-snapshot", ".aedat");
        try (AEFileOutputStream os = new AEFileOutputStream(new FileOutputStream(f), chip, "2.0")) {
            // header written in constructor
        }
        // Live prefs mutate after capture; the written header must still hold 37, never 41.
        chip.getPrefs().put("AEChip.level", "41");
        String header = readText(f);
        Files.deleteIfExists(f.toPath());

        assertTrue(header.contains("This is a raw AE data file"), "legacy raw marker");
        assertTrue(header.contains("Start of Preferences for this AEChip"), "legacy start marker once");
        assertTrue(header.contains("End of Preferences for this AEChip"), "legacy end marker");
        RecordingConfigurationSnapshot reopened = RecordingConfigurationSnapshot.parseLegacyEntries(splitHeaderLines(header));
        assertTrue("37".equals(reopened.get("AEChip.level")), "legacy recorded 37, got <" + reopened.get("AEChip.level") + ">");
        assertTrue(!"41".equals(reopened.get("AEChip.level")), "legacy must not record live 41");
        assertTrue("a<&\"b\n\rsecond".equals(reopened.get("AEChip.nasty")), "legacy nasty value round-trips");
        // Exactly one snapshot block: the level entry appears once, never 41.
        int level37 = 0;
        for (String line : splitHeaderLines(header)) {
            SnapshotCodec.Entry e = SnapshotCodec.parseEntryLine(line);
            if (e != null && "AEChip.level".equals(e.getKey())) {
                level37++;
            }
        }
        assertTrue(level37 == 1, "legacy snapshot block has exactly one level entry, got " + level37);
        System.out.println("PASS testLegacyAEDATSnapshot");
    }

    /** Failed internal capture at 37 is released; retry after live changes records fresh 41. */
    private static void testFailedInternalStartRetriesFresh() throws Exception {
        FlushFixture fixture = new FlushFixture("37");
        DataLogger dl = new DataLogger(fixture.chip);
        File badParent = File.createTempFile("jaer-datalogger-bad-parent", ".tmp");
        File failed = dl.startRecording(new File(badParent, "failed.aedat").getAbsolutePath());
        assertTrue(failed == null, "failed start returns null");
        assertTrue(fixture.chip.getRecordingConfigurationSnapshot() == null,
                "failed internally captured 37 is cleared");
        assertTrue(fixture.flushCount == 1, "failed attempt captured exactly once");

        fixture.live = "41";
        File file = File.createTempFile("jaer-datalogger-retry", ".aedat");
        File logged = dl.startRecording(file.getAbsolutePath());
        assertTrue(logged != null, "retry succeeds");
        RecordingConfigurationSnapshot retry = fixture.chip.getRecordingConfigurationSnapshot();
        assertTrue(retry != null && "41".equals(retry.get("AEChip.level")),
                "retry captured fresh 41, not failed-attempt 37");
        assertTrue(fixture.flushCount == 2, "each direct attempt flushes once, got " + fixture.flushCount);
        dl.filterPacket(buildEventPacket(1));
        dl.stopRecording(false);
        assertTrue(fixture.chip.getRecordingConfigurationSnapshot() == null,
                "stop releases internally captured retry snapshot");
        assertRecordedLevel(logged, "41", "failed-start retry");
        Files.deleteIfExists(logged.toPath());
        Files.deleteIfExists(badParent.toPath());
        System.out.println("PASS testFailedInternalStartRetriesFresh 37->41 flushes=2");
    }

    /** External frozen 37 is reused by identity with one flush and is never stomped by DataLogger failure/stop. */
    private static void testExternalSnapshotReuseAndOwnership() throws Exception {
        FlushFixture fixture = new FlushFixture("37");
        RecordingConfigurationSnapshot ownerSnapshot = RecordingConfigurationSnapshot.captureFromChip(fixture.chip);
        fixture.chip.setRecordingConfigurationSnapshot(ownerSnapshot);
        assertTrue(fixture.flushCount == 1, "owner capture flushed once");
        fixture.live = "41";

        DataLogger dl = new DataLogger(fixture.chip);
        File badParent = File.createTempFile("jaer-datalogger-owner-bad", ".tmp");
        assertTrue(dl.startRecording(new File(badParent, "failed.aedat").getAbsolutePath()) == null,
                "owner-snapshot failed start returns null");
        assertTrue(fixture.chip.getRecordingConfigurationSnapshot() == ownerSnapshot,
                "failed start preserves exact external object");
        assertTrue(fixture.flushCount == 1, "failed external start does not recapture");

        File file = File.createTempFile("jaer-datalogger-owner", ".aedat");
        File logged = dl.startRecording(file.getAbsolutePath());
        assertTrue(logged != null, "external-snapshot start succeeds");
        assertTrue(fixture.chip.getRecordingConfigurationSnapshot() == ownerSnapshot,
                "active DataLogger keeps exact external object");
        assertTrue(fixture.flushCount == 1, "writer performs no second bias flush");
        dl.filterPacket(buildEventPacket(2));
        dl.stopRecording(false);
        assertTrue(fixture.chip.getRecordingConfigurationSnapshot() == ownerSnapshot,
                "normal stop does not stomp external ownership");
        assertRecordedLevel(logged, "37", "external frozen snapshot");
        Files.deleteIfExists(logged.toPath());
        Files.deleteIfExists(badParent.toPath());
        fixture.chip.setRecordingConfigurationSnapshot(null); // explicit owner release
        System.out.println("PASS testExternalSnapshotReuseAndOwnership frozen=37 live=41 flushes=1");
    }

    /** A direct call with no owner captures the current 41 and stop releases it. */
    private static void testDirectFreshCaptureAndStopClear() throws Exception {
        FlushFixture fixture = new FlushFixture("41");
        DataLogger dl = new DataLogger(fixture.chip);
        File file = File.createTempFile("jaer-datalogger-direct", ".aedat");
        File logged = dl.startRecording(file.getAbsolutePath());
        assertTrue(logged != null, "direct no-owner start succeeds");
        assertTrue(fixture.flushCount == 1, "direct call captures exactly once");
        assertTrue("41".equals(fixture.chip.getRecordingConfigurationSnapshot().get("AEChip.level")),
                "direct call captures live 41");
        dl.stopRecording(false);
        assertTrue(fixture.chip.getRecordingConfigurationSnapshot() == null,
                "direct stop clears internally captured snapshot");
        assertRecordedLevel(logged, "41", "direct fresh capture");
        Files.deleteIfExists(logged.toPath());
        System.out.println("PASS testDirectFreshCaptureAndStopClear live=41 flushes=1");
    }

    /** A repeated public start must not replace or leak the active writer/snapshot. */
    private static void testRepeatedStartKeepsActiveRecording() throws Exception {
        FlushFixture fixture = new FlushFixture("37");
        DataLogger dl = new DataLogger(fixture.chip);
        File dir = Files.createTempDirectory("jaer-datalogger-repeat-start-").toFile();
        File first = new File(dir, "first.aedat");
        File second = new File(dir, "second.aedat");
        assertTrue(first.equals(dl.startRecording(first.getAbsolutePath())), "first start succeeds");
        RecordingConfigurationSnapshot snapshot = fixture.chip.getRecordingConfigurationSnapshot();
        java.lang.reflect.Field streamField = DataLogger.class.getDeclaredField("recordingOutputStream");
        streamField.setAccessible(true);
        AEFileOutputStream writer = (AEFileOutputStream) streamField.get(dl);

        assertTrue(first.equals(dl.startRecording(second.getAbsolutePath())),
                "repeated start returns active file");
        assertTrue(streamField.get(dl) == writer,
                "repeated start preserves the writer by identity");
        assertTrue(fixture.chip.getRecordingConfigurationSnapshot() == snapshot,
                "repeated start preserves the active snapshot by identity");
        assertTrue(fixture.flushCount == 1, "repeated start performs no second capture");
        assertTrue(!second.exists(), "repeated start does not create a second file");

        dl.filterPacket(buildEventPacket(1));
        assertTrue(first.equals(dl.stopRecording(false)), "stop closes and returns the original recording");
        assertTrue(streamField.get(dl) == null, "stop releases the original writer field");
        assertTrue(fixture.chip.getRecordingConfigurationSnapshot() == null,
                "stop releases the original logger-owned snapshot");
        Files.deleteIfExists(first.toPath());
        Files.deleteIfExists(dir.toPath());
        System.out.println("PASS testRepeatedStartKeepsActiveRecording");
    }

    /** A packet-write error clears logger-owned state even when close is reached through the error path. */
    private static void testWriteErrorClearsInternalSnapshot() throws Exception {
        FlushFixture fixture = new FlushFixture("37");
        DataLogger dl = new DataLogger(fixture.chip);
        File file = File.createTempFile("jaer-datalogger-write-error", ".aedat");
        assertTrue(dl.startRecording(file.getAbsolutePath()) != null, "error-path setup starts");
        assertTrue(fixture.chip.getRecordingConfigurationSnapshot() != null,
                "error-path setup owns a snapshot");
        java.lang.reflect.Field streamField = DataLogger.class.getDeclaredField("recordingOutputStream");
        streamField.setAccessible(true);
        ((AEFileOutputStream) streamField.get(dl)).close();
        dl.filterPacket(buildEventPacket(1)); // closed channel -> IOException -> production error cleanup
        assertTrue(fixture.chip.getRecordingConfigurationSnapshot() == null,
                "write error clears internally captured snapshot");
        Files.deleteIfExists(file.toPath());
        System.out.println("PASS testWriteErrorClearsInternalSnapshot");
    }

    /** Empty prefs produce a valid legacy header, no DataLogger null-chip failure, and no sidecar XML. */
    private static void testDataLoggerEmptyAndNoSidecar() throws Exception {
        AEChip chip = bareChip(withPrefs());
        chip.setSupport(new java.beans.PropertyChangeSupport(chip));
        RecordingConfigurationSnapshot empty = RecordingConfigurationSnapshot.captureFromChip(chip);
        assertTrue(empty.isEmpty(), "empty prefs yield empty snapshot");

        File dir = Files.createTempDirectory("jaer-datalogger-no-sidecar-").toFile();
        File fl = new File(dir, "empty.aedat");
        chip.setRecordingConfigurationSnapshot(empty);
        try (AEFileOutputStream os = new AEFileOutputStream(new FileOutputStream(fl), chip, "2.0")) {
        }
        assertTrue(readText(fl).contains("End of Preferences for this AEChip"), "legacy header completes with empty snapshot");

        DataLogger dl = new DataLogger(chip);
        File dlf = new File(dir, "empty-dl.aedat");
        File dlRet = dl.startRecording(dlf.getAbsolutePath());
        assertTrue(dlRet != null, "DataLogger starts with empty prefs (active chip, no null-chip NPE)");
        dl.filterPacket(buildEventPacket(2));
        dl.stopRecording(false);

        for (File sib : dir.listFiles()) {
            if (!sib.getName().equals(fl.getName())
                    && !sib.getName().equals(dlf.getName())
                    && sib.getName().toLowerCase().endsWith(".xml")) {
                throw new AssertionError("unexpected sidecar XML " + sib);
            }
        }
        Files.deleteIfExists(fl.toPath());
        Files.deleteIfExists(dlf.toPath());
        Files.deleteIfExists(dir.toPath());
        System.out.println("PASS testDataLoggerEmptyAndNoSidecar");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static EventPacket<PolarityEvent> buildEventPacket(int n) {
        EventPacket<PolarityEvent> events = new EventPacket<>(PolarityEvent.class);
        OutputEventIterator<PolarityEvent> out = events.outputIterator();
        for (int i = 0; i < n; i++) {
            PolarityEvent e = out.nextOutput();
            e.timestamp = 1000 + i;
            e.x = (short) (10 + i);
            e.y = (short) (20 + i);
            e.setPolarity((i & 1) == 0 ? PolarityEvent.Polarity.On : PolarityEvent.Polarity.Off);
        }
        return events;
    }

    private static AEChip bareChip(Preferences prefs) {
        AEChip chip = new org.objenesis.ObjenesisStd().newInstance(AEChip.class);
        chip.setPrefs(prefs);
        return chip;
    }

    private static Preferences withPrefs() {
        return RecordingConfigSnapshotDemo.MemoryPreferences.root();
    }

    private static void assertRecordedLevel(File file, String expected, String label) throws IOException {
        RecordingConfigurationSnapshot reopened = RecordingConfigurationSnapshot.parseLegacyEntries(
                splitHeaderLines(readText(file)));
        assertTrue(expected.equals(reopened.get("AEChip.level")),
                label + " recorded " + expected + ", got <" + reopened.get("AEChip.level") + ">");
    }

    /** Chip fixture whose live-bias flush writes the selected value and counts captures. */
    private static final class FlushFixture {
        final AEChip chip;
        int flushCount;
        String live;

        FlushFixture(String live) {
            this.live = live;
            chip = bareChip(withPrefs());
            chip.setSupport(new java.beans.PropertyChangeSupport(chip));
            Biasgen biasgen = new Biasgen(chip);
            biasgen.setPotArray(new PotArray(biasgen) {
                @Override
                public void storePreferences() {
                    flushCount++;
                    chip.getPrefs().put("AEChip.level", FlushFixture.this.live);
                }
            });
            chip.setBiasgen(biasgen);
        }
    }

    private static String readText(File f) throws IOException {
        byte[] bytes;
        try (FileInputStream in = new FileInputStream(f)) {
            bytes = in.readAllBytes();
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static List<String> splitHeaderLines(String header) {
        List<String> out = new ArrayList<>();
        for (String line : header.split("\\r?\\n")) {
            if (line.length() > 0 && line.charAt(0) == AEDataFile.COMMENT_CHAR) {
                line = line.substring(1);
            }
            out.add(line);
        }
        return out;
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }
}
