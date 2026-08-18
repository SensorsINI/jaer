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
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventprocessing.DataLogger;

/**
 * Headless production-path test for freezing the recording-start configuration
 * snapshot into legacy AEDAT and DataLogger output (plan Todo 3, part 2).
 *
 * <p>An immutable {@link RecordingConfigurationSnapshot} captured at recording
 * start is written as one recognizable block in the legacy AEDAT header and by
 * {@link DataLogger} (which uses its active chip and explicit AEDAT-2 version).
 * Each path writes a real file, closes it, reopens it and compares the decoded
 * snapshot. Live preferences are mutated (37 -&gt; 41) after capture to prove the
 * frozen snapshot is what was recorded. Each check throws {@link AssertionError}
 * on mismatch and prints PASS on success.
 */
public class DataLoggerMetadataDemo {

    public static void main(String[] args) throws Exception {
        testLegacyAEDATSnapshot();
        testDataLoggerSnapshotAndStopClear();
        testDataLoggerEmptyAndNoSidecar();
        System.out.println("ALL PASS");
    }

    /** Legacy AEDAT: recognizable markers, one entry, frozen 37 and hostile value round-trip. */
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
        chip.getPrefs().put("AEChip.level", "41");
        String header = readText(f);
        Files.deleteIfExists(f.toPath());

        assertTrue(header.contains("This is a raw AE data file"), "legacy raw marker");
        assertTrue(header.contains("Start of Preferences for this AEChip"), "legacy start marker");
        assertTrue(header.contains("End of Preferences for this AEChip"), "legacy end marker");
        RecordingConfigurationSnapshot reopened = RecordingConfigurationSnapshot.parseLegacyEntries(splitHeaderLines(header));
        assertTrue("37".equals(reopened.get("AEChip.level")), "legacy recorded frozen 37");
        assertTrue(!"41".equals(reopened.get("AEChip.level")), "legacy did not record live 41");
        assertTrue("a<&\"b\n\rsecond".equals(reopened.get("AEChip.nasty")), "legacy hostile value round-trips");
        int levelEntries = 0;
        for (String line : splitHeaderLines(header)) {
            SnapshotCodec.Entry e = SnapshotCodec.parseEntryLine(line);
            if (e != null && "AEChip.level".equals(e.getKey())) {
                levelEntries++;
            }
        }
        assertTrue(levelEntries == 1, "legacy has exactly one level entry, got " + levelEntries);
        System.out.println("PASS testLegacyAEDATSnapshot");
    }

    /** DataLogger captures at start, records frozen 37 after live becomes 41, and clears on stop. */
    private static void testDataLoggerSnapshotAndStopClear() throws Exception {
        AEChip chip = bareChip(withPrefs());
        chip.setSupport(new java.beans.PropertyChangeSupport(chip));
        chip.getPrefs().put("AEChip.level", "37");
        chip.getPrefs().put("AEChip.nasty", "a<&\"b\n\rsecond");

        DataLogger logger = new DataLogger(chip);
        File f = File.createTempFile("jaer-datalogger-snapshot", ".aedat");
        File logged = logger.startLogging(f.getAbsolutePath());
        assertTrue(logged != null, "DataLogger starts with active chip and AEDAT-2 version");
        RecordingConfigurationSnapshot active = chip.getRecordingConfigurationSnapshot();
        assertTrue(active != null && "37".equals(active.get("AEChip.level")), "DataLogger froze 37 at start");
        chip.getPrefs().put("AEChip.level", "41");
        logger.stopLogging(false);
        assertTrue(chip.getRecordingConfigurationSnapshot() == null, "DataLogger stop clears cached snapshot");

        RecordingConfigurationSnapshot reopened = RecordingConfigurationSnapshot.parseLegacyEntries(
                splitHeaderLines(readText(logged)));
        assertTrue("37".equals(reopened.get("AEChip.level")), "DataLogger recorded frozen 37");
        assertTrue(!"41".equals(reopened.get("AEChip.level")), "DataLogger did not record live 41");
        assertTrue("a<&\"b\n\rsecond".equals(reopened.get("AEChip.nasty")), "DataLogger hostile value round-trips");
        Files.deleteIfExists(logged.toPath());
        System.out.println("PASS testDataLoggerSnapshotAndStopClear");
    }

    /** Empty prefs produce valid legacy/DataLogger files and no sidecar XML. */
    private static void testDataLoggerEmptyAndNoSidecar() throws Exception {
        AEChip chip = bareChip(withPrefs());
        chip.setSupport(new java.beans.PropertyChangeSupport(chip));
        RecordingConfigurationSnapshot empty = RecordingConfigurationSnapshot.captureFromChip(chip);
        assertTrue(empty.isEmpty(), "empty prefs yield empty snapshot");

        File dir = Files.createTempDirectory("jaer-datalogger-no-sidecar-").toFile();
        File legacy = new File(dir, "empty.aedat");
        chip.setRecordingConfigurationSnapshot(empty);
        try (AEFileOutputStream os = new AEFileOutputStream(new FileOutputStream(legacy), chip, "2.0")) {
        }
        assertTrue(readText(legacy).contains("End of Preferences for this AEChip"),
                "legacy header completes with empty snapshot");

        DataLogger logger = new DataLogger(chip);
        File dataLoggerFile = new File(dir, "empty-dl.aedat");
        File logged = logger.startLogging(dataLoggerFile.getAbsolutePath());
        assertTrue(logged != null, "DataLogger starts with empty prefs without null-chip/null-version failure");
        logger.stopLogging(false);

        for (File sibling : dir.listFiles()) {
            if (!sibling.getName().equals(legacy.getName())
                    && !sibling.getName().equals(dataLoggerFile.getName())
                    && sibling.getName().toLowerCase().endsWith(".xml")) {
                throw new AssertionError("unexpected sidecar XML " + sibling);
            }
        }
        Files.deleteIfExists(legacy.toPath());
        Files.deleteIfExists(dataLoggerFile.toPath());
        Files.deleteIfExists(dir.toPath());
        System.out.println("PASS testDataLoggerEmptyAndNoSidecar");
    }

    private static AEChip bareChip(Preferences prefs) {
        AEChip chip = new org.objenesis.ObjenesisStd().newInstance(AEChip.class);
        chip.setPrefs(prefs);
        return chip;
    }

    private static Preferences withPrefs() {
        return RecordingConfigSnapshotDemo.MemoryPreferences.root();
    }

    private static String readText(File f) throws IOException {
        byte[] bytes;
        try (FileInputStream in = new FileInputStream(f)) {
            bytes = in.readAllBytes();
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static List<String> splitHeaderLines(String header) {
        List<String> lines = new ArrayList<>();
        for (String line : header.split("\\r?\\n")) {
            if (!line.isEmpty() && line.charAt(0) == AEDataFile.COMMENT_CHAR) {
                line = line.substring(1);
            }
            lines.add(line);
        }
        return lines;
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
