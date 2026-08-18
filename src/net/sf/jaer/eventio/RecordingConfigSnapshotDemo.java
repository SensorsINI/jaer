package net.sf.jaer.eventio;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.TreeMap;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.PotArray;
import net.sf.jaer.chip.AEChip;

/**
 * Headless production-path self-test for the immutable live configuration
 * snapshot (phase1-recording, plan Todo 2).
 *
 * <p>A real {@link AEChip} cannot be constructed headlessly here because its
 * constructor builds a JOGL renderer/canvas that requires a display or DRM
 * device. The snapshot/header contract under test only depends on the chip's
 * preference node, biasgen, and the snapshot field, so we allocate a bare
 * {@link AEChip} with Objenesis (a declared dependency) and set those explicitly.
 * This exercises the exact production {@link AEChip#writeAdditionalAEFileOutputStreamHeader}
 * hook writing through a real {@link AEFileOutputStream} to a real file, whose
 * header bytes we then reopen and parse with {@link SnapshotCodec#parseEntryLine}.
 *
 * <p>Each check throws {@link AssertionError} on mismatch (non-zero exit) and
 * prints PASS lines on success. Run headlessly after {@code ant clean compile}:
 * {@code java -cp build/classes:lib/*:jars/* net.sf.jaer.eventio.RecordingConfigSnapshotDemo}
 */
public class RecordingConfigSnapshotDemo {

    public static void main(String[] args) throws Exception {
        testBaselineMarkers();
        testEscapingAndInjectionSafetyAndRoundTrip();
        testSnapshot37PersistsAfterLiveBecomes41();
        testDeterminism();
        testNullChipAndNullPrefs();
        testBiasgenFlushOnce();
        testOwnerClearsThenFreshCapture();
        System.out.println("ALL PASS");
    }

    /** Characterizes the recognizable legacy header markers (base-compatible). */
    private static void testBaselineMarkers() throws IOException {
        AEChip chip = bareChip(withPrefs());
        String header = writeHeaderToFile(chip, "2.0");
        assertTrue(header.contains(" AEChip: "), "AEChip marker");
        assertTrue(header.contains(" AEChip: net.sf.jaer.chip.AEChip"), "AEChip class marker");
        assertTrue(header.contains("Start of Preferences for this AEChip"), "start marker");
        assertTrue(header.contains("End of Preferences for this AEChip"), "end marker");
        assertTrue(header.contains("# This is a raw AE data file - do not edit"), "raw file marker");
        System.out.println("PASS testBaselineMarkers");
    }

    /** Escapes XML metacharacters and line breaks; value round-trips and cannot inject a line. */
    private static void testEscapingAndInjectionSafetyAndRoundTrip() throws IOException {
        String nasty = "a<&\"b\nsecond\rline";
        AEChip chip = bareChip(withPrefs());
        chip.getPrefs().put("AEChip.nasty", nasty);

        String serialized = RecordingConfigurationSnapshot.captureFromChip(chip).serializeLegacyEntries();
        // Raw metacharacters must not appear unescaped.
        String encoded = SnapshotCodec.escape(nasty);
        assertTrue(!serialized.contains("value=\"a<&\"b"), "raw metacharacters leaked into serialization");
        assertTrue(serialized.contains("value=\"" + encoded + "\""), "escaped value present in serialization");
        // The newline must not produce a physical line break inside the serialization.
        assertTrue(!serialized.contains("a<&\"b\nsecond"), "line break leaked into serialization");

        // Round-trip through the actual codec (escape then unescape) must recover the exact value.
        assertTrue(nasty.equals(SnapshotCodec.unescape(encoded)), "escape/unescape round-trip");
        assertTrue("x<&\"y\r\nz".equals(SnapshotCodec.unescape(SnapshotCodec.escape("x<&\"y\r\nz"))),
                "escape/unescape round-trip 2");

        // End-to-end through a real header: write, reopen, parse, and check exactly one entry that decodes intact.
        String header = writeHeaderToFile(chip, "2.0");
        assertTrue(!header.contains("value=\"a<&\"b"), "raw metacharacters leaked into header");
        RecordingConfigurationSnapshot reopened = RecordingConfigurationSnapshot.parseLegacyEntries(splitHeaderLines(header));
        String got = reopened.get("AEChip.nasty");
        assertTrue(got != null, "nasty entry present after reopen");
        assertTrue(nasty.equals(got), "nasty value round-tripped through real header: was <" + got + ">");
        // Exactly one entry line for this key: count occurrences.
        int count = 0;
        for (String line : splitHeaderLines(header)) {
            SnapshotCodec.Entry e = SnapshotCodec.parseEntryLine(line);
            if (e != null && e.getKey().equals("AEChip.nasty")) {
                count++;
            }
        }
        assertTrue(count == 1, "exactly one entry line for nasty key, got " + count);
        System.out.println("PASS testEscapingAndInjectionSafetyAndRoundTrip");
    }

    /** Captures 37, mutates live prefs to 41, and proves the snapshot still emits 37 exactly once. */
    private static void testSnapshot37PersistsAfterLiveBecomes41() throws IOException {
        AEChip chip = bareChip(withPrefs());
        chip.getPrefs().put("AEChip.level", "37");
        RecordingConfigurationSnapshot snap = RecordingConfigurationSnapshot.captureFromChip(chip);
        chip.setRecordingConfigurationSnapshot(snap);

        String header1 = writeHeaderToFile(chip, "2.0");
        assertTrue(countValue(header1, "37") == 1, "value 37 appears exactly once in first header");
        assertTrue(countValue(header1, "41") == 0, "value 41 absent in first header");

        // Mutate the live preference; the pre-captured snapshot is reused as-is.
        chip.getPrefs().put("AEChip.level", "41");
        String header2 = writeHeaderToFile(chip, "2.0");
        assertTrue(countValue(header2, "37") == 1, "value 37 appears exactly once after live became 41");
        assertTrue(countValue(header2, "41") == 0, "value 41 never appears in snapshot header");

        // The entry block (which is what records the config) is identical across both writes
        // because the same immutable snapshot was reused; header timestamps legitimately differ.
        assertTrue(entryBlock(header1).equals(entryBlock(header2)),
                "stable entry block across two writes from one snapshot");
        System.out.println("PASS testSnapshot37PersistsAfterLiveBecomes41");
    }

    /** Sorted, immutable output is byte-stable across two serializations. */
    private static void testDeterminism() throws IOException {
        AEChip chip = bareChip(withPrefs());
        Preferences p = chip.getPrefs();
        p.put("AEChip.zeta", "z");
        p.put("AEChip.alpha", "a");
        p.put("AEChip.mid", "<m&\"v>\n");
        RecordingConfigurationSnapshot snap = RecordingConfigurationSnapshot.captureFromChip(chip);
        String s1 = snap.serializeLegacyEntries();
        String s2 = snap.serializeLegacyEntries();
        assertTrue(s1.equals(s2), "two serializations byte-identical");
        assertTrue(s1.indexOf("AEChip.alpha") < s1.indexOf("AEChip.mid")
                && s1.indexOf("AEChip.mid") < s1.indexOf("AEChip.zeta"), "entries sorted by key");

        // The snapshot must be immutable: a caller cannot mutate the returned list.
        try {
            snap.entries().clear();
            assertTrue(false, "entries() should be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
        System.out.println("PASS testDeterminism");
    }

    /** Null chip and null prefs are handled explicitly and never abort recording. */
    private static void testNullChipAndNullPrefs() throws IOException {
        RecordingConfigurationSnapshot empty = RecordingConfigurationSnapshot.captureFromChip((AEChip) null);
        assertTrue(empty.isEmpty(), "null chip yields empty snapshot");

        AEChip chip = bareChip(null); // null prefs
        chip.setBiasgen(null); // no biasgen
        String header = writeHeaderToFile(chip, "2.0");
        assertTrue(header.contains("End of Preferences for this AEChip"), "header completes with null prefs/biasgen");
        System.out.println("PASS testNullChipAndNullPrefs");
    }

    /** When a biasgen exists, storePreferences() is flushed exactly once at capture and its live value is captured. */
    private static void testBiasgenFlushOnce() {
        AEChip chip = bareChip(withPrefs());
        final int[] flushCount = {0};
        final String[] live = {"37"};
        Biasgen bg = new Biasgen(chip);
        PotArray pa = new PotArray(bg) {
            @Override
            public void storePreferences() {
                flushCount[0]++;
                chip.getPrefs().put("biasgen.live", live[0]);
            }
        };
        bg.setPotArray(pa);
        chip.setBiasgen(bg);

        RecordingConfigurationSnapshot snap = RecordingConfigurationSnapshot.captureFromChip(chip);
        assertTrue(flushCount[0] == 1, "one live bias flush at capture, got " + flushCount[0]);
        assertTrue("37".equals(snap.get("biasgen.live")), "flushed live value captured");

        // Mutate live, capture a second snapshot: the flush happens once more and the new value is captured.
        live[0] = "41";
        RecordingConfigurationSnapshot snap2 = RecordingConfigurationSnapshot.captureFromChip(chip);
        assertTrue(flushCount[0] == 2, "second capture flushes once more, got " + flushCount[0]);
        assertTrue("41".equals(snap2.get("biasgen.live")), "second capture got new live value");
        assertTrue("37".equals(snap.get("biasgen.live")), "first snapshot still frozen at 37");
        System.out.println("PASS testBiasgenFlushOnce");
    }

    /** Owner clears a set snapshot; the next write then captures fresh state (no silent stale reuse). */
    private static void testOwnerClearsThenFreshCapture() throws IOException {
        AEChip chip = bareChip(withPrefs());
        chip.getPrefs().put("AEChip.level", "37");
        chip.setRecordingConfigurationSnapshot(RecordingConfigurationSnapshot.captureFromChip(chip));
        assertTrue(countValue(writeHeaderToFile(chip, "2.0"), "37") == 1, "set snapshot used");

        // Owner clears; live pref mutated to 41; next write captures fresh => 41, not stale 37.
        chip.setRecordingConfigurationSnapshot(null);
        chip.getPrefs().put("AEChip.level", "41");
        String header = writeHeaderToFile(chip, "2.0");
        assertTrue(countValue(header, "41") == 1, "fresh capture reflects live 41 after clear");
        assertTrue(countValue(header, "37") == 0, "no stale 37 after clear");

        // Default path (nothing ever set) captures fresh each time too.
        AEChip ch2 = bareChip(withPrefs());
        ch2.getPrefs().put("AEChip.level", "37");
        assertTrue(countValue(writeHeaderToFile(ch2, "2.0"), "37") == 1, "default path reflects 37");
        ch2.getPrefs().put("AEChip.level", "41");
        assertTrue(countValue(writeHeaderToFile(ch2, "2.0"), "41") == 1, "default path reflects 41 next time, no cache");
        System.out.println("PASS testOwnerClearsThenFreshCapture");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static AEChip bareChip(Preferences prefs) {
        AEChip chip = new org.objenesis.ObjenesisStd().newInstance(AEChip.class);
        chip.setPrefs(prefs);
        return chip;
    }

    private static MemoryPreferences withPrefs() {
        return MemoryPreferences.root();
    }

    /** Write the legacy header for {@code chip} to a real temp file and return its raw text. */
    private static String writeHeaderToFile(AEChip chip, String version) throws IOException {
        File f = File.createTempFile("jaer-snapshot-hdr", ".aedat");
        try (AEFileOutputStream os = new AEFileOutputStream(new FileOutputStream(f), chip, version)) {
            // header written in constructor; close flushes channel + closes file
        }
        byte[] bytes;
        try (FileInputStream in = new FileInputStream(f)) {
            bytes = in.readAllBytes();
        }
        Files.deleteIfExists(f.toPath());
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** Split raw header text into physical lines, stripping the leading comment char and trailing CR. */
    private static java.util.List<String> splitHeaderLines(String header) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String line : header.split("\\r?\\n")) {
            if (line.length() > 0 && line.charAt(0) == AEDataFile.COMMENT_CHAR) {
                line = line.substring(1);
            }
            out.add(line);
        }
        return out;
    }

    /** Count occurrences of a full escaped value attribute {@code value="<v>"} in the header. */
    private static int countValue(String header, String v) {
        String needle = "value=\"" + v + "\"";
        int count = 0;
        int idx = 0;
        while ((idx = header.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    /** Extract the canonical, sorted {@code <entry .../>} block from a header, joined by newline. */
    private static String entryBlock(String header) {
        RecordingConfigurationSnapshot s = RecordingConfigurationSnapshot.parseLegacyEntries(splitHeaderLines(header));
        return s.serializeLegacyEntries();
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }

    /** In-memory Preferences so tests are deterministic and do not touch the real preference store. */
    static final class MemoryPreferences extends AbstractPreferences {
        private final TreeMap<String, String> map = new TreeMap<>();

        private MemoryPreferences(AbstractPreferences parent, String name) {
            super(parent, name);
        }

        static MemoryPreferences root() {
            return new MemoryPreferences(null, "");
        }

        @Override
        protected void putSpi(String key, String value) {
            map.put(key, value);
        }

        @Override
        protected String getSpi(String key) {
            return map.get(key);
        }

        @Override
        protected void removeSpi(String key) {
            map.remove(key);
        }

        @Override
        protected void removeNodeSpi() throws BackingStoreException {
        }

        @Override
        protected String[] keysSpi() {
            return map.keySet().toArray(new String[0]);
        }

        @Override
        protected String[] childrenNamesSpi() {
            return new String[0];
        }

        @Override
        protected AbstractPreferences childSpi(String name) {
            return new MemoryPreferences(this, name);
        }

        @Override
        protected void syncSpi() throws BackingStoreException {
        }

        @Override
        protected void flushSpi() throws BackingStoreException {
        }
    }
}
