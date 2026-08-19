package net.sf.jaer.graphics;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventio.AEDZOutputStream;
import net.sf.jaer.eventio.AEDZInputStream;
import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventio.RecordingConfigurationSnapshot;

/**
 * Headless production-path probe for the AEViewer recording-snapshot lifecycle
 * and the AEDZ (compressed AEDAT-2) recording wiring (plans Todo 3, part 3, and
 * Todo 4, commit D).
 *
 * <p>A full {@link AEViewer} cannot be constructed headlessly (it builds a
 * {@link javax.swing.JFrame}, which throws {@link java.awt.HeadlessException}
 * without a display), so this probe drives the exact production seams that
 * {@code AEViewer.startLogging} and the recording-setup dialog use —
 * {@link AEViewer#openWithFrozenSnapshot},
 * {@link AEViewer#constructLoggingWriter},
 * {@link AEViewer#resolveLogFormat(String, String)},
 * {@link AEViewer#normalizeLoggingDataFileVersion(String)} and the preferences
 * index↔version mapping — and verifies:
 * <ul>
 *   <li>a failed file open must clear the chip's cached snapshot so a later,
 *       successful start re-captures fresh metadata instead of reusing a stale
 *       snapshot;</li>
 *   <li>writing after a writer-construction failure closes the stream and
 *       releases the snapshot exactly once;</li>
 *   <li>the .aedz version/extension selects the AEDZ log writer and appends the
 *       correct extension;</li>
 *   <li>the AEDZ writer path builds a real {@link AEDZOutputStream} that writes
 *       a valid file and keeps ownership of the stream;</li>
 *   <li>an AEDZ writer-construction failure closes the stream and clears the
 *       snapshot;</li>
 *   <li>the recording-format preference round-trips (index↔version), accepting
 *       "aedz"/"4.0"/"2.0" and defaulting anything else to AEDAT-4.</li>
 * </ul>
 *
 * <p>Each check throws {@link AssertionError} on mismatch (non-zero exit) and
 * prints PASS on success. Run headlessly after {@code ant clean compile}:
 * {@code java -cp build/classes:lib/*:jars/* net.sf.jaer.graphics.AEViewerSnapshotProbe}
 */
public class AEViewerSnapshotProbe {

    public static void main(String[] args) throws Exception {
        testSuccessPathFreezesBeforeOpen();
        testFailedOpenClearsSnapshot();
        testRepeatedFailedOpenStaysClear();
        testSuccessfulStartAfterFailureCapturesFresh();
        testWriterConstructionFailureClosesStream();
        testWriterConstructionFailureReleasesFileAndSnapshot();
        testRepeatedWriterFailureLeaksNoState();
        testWriterFailureThenSuccessCapturesFresh();
        testSuccessfulWriterConstructionKeepsStreamOpen();
        testResolveLogFormatSelectsAedzWriter();
        testNormalizeLoggingDataFileVersion();
        testPreferenceIndexRoundTrip();
        testAedzWriterPathConstructsWriter();
        testAedzWriterFailureCleanup();
        System.out.println("ALL PASS");
    }

    /** Success path: snapshot is frozen and placed on the chip before the file opens. */
    private static void testSuccessPathFreezesBeforeOpen() throws Exception {
        AEChip chip = bareChip();
        chip.getPrefs().put("AEChip.level", "37");
        assert chip.getRecordingConfigurationSnapshot() == null : "precondition: no snapshot yet";

        File out = File.createTempFile("jaer-aeviewer-success", ".aedat");
        AEViewer.OpenedLogStream opened = AEViewer.openWithFrozenSnapshot(chip, out);
        try {
            assertTrue(opened.snapshot != null, "returned snapshot present");
            assertTrue(chip.getRecordingConfigurationSnapshot() != null, "chip holds snapshot after open");
            assertTrue("37".equals(opened.snapshot.get("AEChip.level")), "snapshot holds current 37");
            assertTrue(opened.stream != null && opened.stream.getChannel().isOpen(), "stream opened");
        } finally {
            try {
                opened.stream.close();
            } catch (IOException ignore) {
            }
            out.delete();
        }
        System.out.println("PASS testSuccessPathFreezesBeforeOpen");
    }

    /** A failed open (missing parent directory) throws and clears the chip snapshot. */
    private static void testFailedOpenClearsSnapshot() throws Exception {
        AEChip chip = bareChip();
        chip.getPrefs().put("AEChip.level", "37");

        // Pre-freeze a snapshot as if a (failed) start had set one, then open a bad path.
        RecordingConfigurationSnapshot stale = RecordingConfigurationSnapshot.captureFromChip(chip);
        chip.setRecordingConfigurationSnapshot(stale);

        File bad = new File("/nonexistent-dir-jaer-xyz/junk.aedat");
        boolean threw = false;
        try {
            AEViewer.openWithFrozenSnapshot(chip, bad);
        } catch (java.io.FileNotFoundException e) {
            threw = true;
        }
        assertTrue(threw, "open on missing parent directory throws FileNotFoundException");
        assertTrue(chip.getRecordingConfigurationSnapshot() == null,
                "chip snapshot cleared after failed open (no stale metadata)");
        System.out.println("PASS testFailedOpenClearsSnapshot");
    }

    /** Repeated failed opens keep the chip snapshot clear each time. */
    private static void testRepeatedFailedOpenStaysClear() throws Exception {
        AEChip chip = bareChip();
        chip.getPrefs().put("AEChip.level", "37");

        for (int i = 0; i < 2; i++) {
            File bad = new File("/nonexistent-dir-jaer-xyz/run" + i + ".aedat");
            try {
                AEViewer.openWithFrozenSnapshot(chip, bad);
                throw new AssertionError("expected FileNotFoundException attempt " + i);
            } catch (java.io.FileNotFoundException expected) {
                // expected
            }
            assertTrue(chip.getRecordingConfigurationSnapshot() == null,
                    "chip snapshot clear after failed open attempt " + i);
        }
        System.out.println("PASS testRepeatedFailedOpenStaysClear");
    }

    /** After a failed open and a live-prefs change, a successful start re-captures the fresh value. */
    private static void testSuccessfulStartAfterFailureCapturesFresh() throws Exception {
        AEChip chip = bareChip();
        chip.getPrefs().put("AEChip.level", "37");

        // Fail one start (simulates the reviewer scenario).
        File bad = new File("/nonexistent-dir-jaer-xyz/junk.aedat");
        try {
            AEViewer.openWithFrozenSnapshot(chip, bad);
            throw new AssertionError("expected FileNotFoundException");
        } catch (java.io.FileNotFoundException expected) {
        }
        assertTrue(chip.getRecordingConfigurationSnapshot() == null, "clear after failed start");

        // Live preferences change between attempts.
        chip.getPrefs().put("AEChip.level", "99");

        // Successful start must capture 99 (fresh), not the stale 37 frozen earlier.
        File out = File.createTempFile("jaer-aeviewer-fresh", ".aedat");
        AEViewer.OpenedLogStream opened = AEViewer.openWithFrozenSnapshot(chip, out);
        try {
            assertTrue("99".equals(opened.snapshot.get("AEChip.level")),
                    "successful start re-captures fresh 99, got <" + opened.snapshot.get("AEChip.level") + ">");
            assertTrue(!"37".equals(chip.getRecordingConfigurationSnapshot().get("AEChip.level")),
                    "no stale reuse: chip snapshot not the pre-failure 37");
        } finally {
            try {
                opened.stream.close();
            } catch (IOException ignore) {
            }
            out.delete();
        }
        System.out.println("PASS testSuccessfulStartAfterFailureCapturesFresh");
    }

    /** A post-open writer-constructor failure must close the already-opened stream exactly once. */
    private static void testWriterConstructionFailureClosesStream() throws Exception {
        AEChip chip = bareChip();
        chip.getPrefs().put("AEChip.level", "37");
        File out = File.createTempFile("jaer-aeviewer-wcfail", ".aedat");
        AEViewer.OpenedLogStream opened = AEViewer.openWithFrozenSnapshot(chip, out);
        try {
            boolean threw = false;
            try {
                AEViewer.constructLoggingWriter(chip, opened, (stream, snapshot) -> {
                    throw new IOException("injected writer-construction failure");
                });
            } catch (IOException expected) {
                threw = true;
            }
            assertTrue(threw, "writer-constructor IOException propagates");
            // The stream must have been closed exactly once by the production seam.
            assertTrue(!opened.stream.getChannel().isOpen(),
                    "opened stream closed after writer-constructor failure (no leaked handle)");
        } finally {
            out.delete();
        }
        System.out.println("PASS testWriterConstructionFailureClosesStream");
    }

    /** The failed writer construction must release the file (reopenable/deletable) and clear the chip snapshot. */
    private static void testWriterConstructionFailureReleasesFileAndSnapshot() throws Exception {
        AEChip chip = bareChip();
        chip.getPrefs().put("AEChip.level", "37");
        File out = File.createTempFile("jaer-aeviewer-wcrelease", ".aedat");
        AEViewer.OpenedLogStream opened = AEViewer.openWithFrozenSnapshot(chip, out);
        try {
            assertTrue(chip.getRecordingConfigurationSnapshot() != null, "precondition: snapshot set before failure");
            try {
                AEViewer.constructLoggingWriter(chip, opened, (stream, snapshot) -> {
                    throw new IOException("injected writer-construction failure");
                });
                throw new AssertionError("expected IOException");
            } catch (IOException expected) {
                // expected
            }
            assertTrue(chip.getRecordingConfigurationSnapshot() == null,
                    "chip snapshot cleared after writer-constructor failure (no stale metadata)");
            // No leaked file handle: the original file is deletable and reopenable.
            FileOutputStream reopened = new FileOutputStream(out);
            reopened.close();
        } finally {
            out.delete();
        }
        System.out.println("PASS testWriterConstructionFailureReleasesFileAndSnapshot");
    }

    /** Repeated writer-constructor failures leak no stream handle and leave the snapshot clear each time. */
    private static void testRepeatedWriterFailureLeaksNoState() throws Exception {
        AEChip chip = bareChip();
        chip.getPrefs().put("AEChip.level", "37");
        for (int i = 0; i < 2; i++) {
            final int attempt = i;
            File out = File.createTempFile("jaer-aeviewer-wcrepeat-" + attempt, ".aedat");
            AEViewer.OpenedLogStream opened = AEViewer.openWithFrozenSnapshot(chip, out);
            try {
                try {
                    AEViewer.constructLoggingWriter(chip, opened, (stream, snapshot) -> {
                        throw new IOException("injected failure attempt " + attempt);
                    });
                    throw new AssertionError("expected IOException attempt " + attempt);
                } catch (IOException expected) {
                    // expected
                }
                assertTrue(!opened.stream.getChannel().isOpen(),
                        "stream closed on repeated failure attempt " + attempt);
                assertTrue(chip.getRecordingConfigurationSnapshot() == null,
                        "chip snapshot clear after repeated failure attempt " + attempt);
            } finally {
                out.delete();
            }
        }
        System.out.println("PASS testRepeatedWriterFailureLeaksNoState");
    }

    /** After a writer-constructor failure and a live-prefs change, a later success re-captures fresh metadata. */
    private static void testWriterFailureThenSuccessCapturesFresh() throws Exception {
        AEChip chip = bareChip();
        chip.getPrefs().put("AEChip.level", "37");

        // One failed writer construction (stream opened, then construction throws).
        File out = File.createTempFile("jaer-aeviewer-wcfresh", ".aedat");
        AEViewer.OpenedLogStream failed = AEViewer.openWithFrozenSnapshot(chip, out);
        try {
            try {
                AEViewer.constructLoggingWriter(chip, failed, (stream, snapshot) -> {
                    throw new IOException("injected writer-construction failure");
                });
                throw new AssertionError("expected IOException");
            } catch (IOException expected) {
                // expected
            }
        } finally {
            out.delete();
        }
        assertTrue(chip.getRecordingConfigurationSnapshot() == null, "chip snapshot clear after failed writer construction");

        // Live preferences change between attempts.
        chip.getPrefs().put("AEChip.level", "99");

        // A later successful start must capture the fresh 99, never the stale 37.
        File out2 = File.createTempFile("jaer-aeviewer-wcfresh2", ".aedat");
        AEViewer.OpenedLogStream opened = AEViewer.openWithFrozenSnapshot(chip, out2);
        try {
            assertTrue("99".equals(opened.snapshot.get("AEChip.level")),
                    "successful start after failed writer construction captures fresh 99, got <"
                            + opened.snapshot.get("AEChip.level") + ">");
            assertTrue(!"37".equals(chip.getRecordingConfigurationSnapshot().get("AEChip.level")),
                    "no stale reuse after failed writer construction: not the pre-failure 37");
        } finally {
            try {
                opened.stream.close();
            } catch (IOException ignore) {
            }
            out2.delete();
        }
        System.out.println("PASS testWriterFailureThenSuccessCapturesFresh");
    }

    /** A successful writer construction transfers ownership: the stream stays open (not closed). */
    private static void testSuccessfulWriterConstructionKeepsStreamOpen() throws Exception {
        AEChip chip = bareChip();
        chip.getPrefs().put("AEChip.level", "37");
        File out = File.createTempFile("jaer-aeviewer-wcsuccess", ".aedat");
        AEViewer.OpenedLogStream opened = AEViewer.openWithFrozenSnapshot(chip, out);
        try {
            // A no-op "writer" that takes ownership and returns normally.
            AEViewer.constructLoggingWriter(chip, opened, (stream, snapshot) -> {
                // ownership transferred; nothing to do
            });
            assertTrue(opened.stream.getChannel().isOpen(),
                    "successful writer construction leaves owned stream open (not closed after transfer)");
        } finally {
            try {
                opened.stream.close();
            } catch (IOException ignore) {
            }
            out.delete();
        }
        System.out.println("PASS testSuccessfulWriterConstructionKeepsStreamOpen");
    }

    /**
     * version/extension/log-writer selection: the "aedz" version and the .aedz
     * extension both resolve to the AEDZ writer, with the correct extension
     * appended when the filename has none; AEDAT-4/2 remain untouched.
     */
    private static void testResolveLogFormatSelectsAedzWriter() {
        AEViewer.LogFormatChoice aedz = AEViewer.resolveLogFormat("rec.x", AEDataFile.DATA_FILE_VERSION_NUMBER_AEDZ);
        assertTrue(AEDataFile.DATA_FILE_VERSION_NUMBER_AEDZ.equals(aedz.version),
                "version 'aedz' selects the AEDZ writer");
        assertTrue(aedz.filename.endsWith(AEDataFile.DATA_FILE_EXTENSION_AEDZ),
                "version 'aedz' with no extension appends .aedz, got " + aedz.filename);

        AEViewer.LogFormatChoice byExt = AEViewer.resolveLogFormat("rec.aedz", AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4);
        assertTrue(AEDataFile.DATA_FILE_VERSION_NUMBER_AEDZ.equals(byExt.version),
                "filename ending in .aedz selects the AEDZ writer even for version '4.0'");

        // The version sentinel ORs with the extension: 'aedz' forces the AEDZ writer
        // even for a legacy .aedat filename, and a legacy version keeps a legacy file legacy.
        AEViewer.LogFormatChoice forced = AEViewer.resolveLogFormat("rec.aedat", AEDataFile.DATA_FILE_VERSION_NUMBER_AEDZ);
        assertTrue(AEDataFile.DATA_FILE_VERSION_NUMBER_AEDZ.equals(forced.version),
                "version 'aedz' forces the AEDZ writer even for a legacy .aedat filename");
        AEViewer.LogFormatChoice legacy = AEViewer.resolveLogFormat("rec.aedat", AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT2);
        assertTrue(AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT2.equals(legacy.version),
                "legacy .aedat with version '2.0' stays AEDAT-2, not captured by the AEDZ branch");

        AEViewer.LogFormatChoice aedat4v = AEViewer.resolveLogFormat("rec.x", AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4);
        assertTrue(AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4.equals(aedat4v.version)
                        && aedat4v.filename.endsWith(AEDataFile.DATA_FILE_EXTENSION_AEDAT4),
                "version '4.0' selects AEDAT-4 and appends .aedat4");

        AEViewer.LogFormatChoice aedat2 = AEViewer.resolveLogFormat("rec.x", AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT2);
        assertTrue(AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT2.equals(aedat2.version)
                        && aedat2.filename.endsWith(AEDataFile.DATA_FILE_EXTENSION_AEDAT2),
                "version '2.0' selects AEDAT-2 and appends .aedat2");

        System.out.println("PASS testResolveLogFormatSelectsAedzWriter");
    }

    /** normalizeLoggingDataFileVersion accepts aedz/4.0/2.0 and defaults any other value to AEDAT-4. */
    private static void testNormalizeLoggingDataFileVersion() {
        assertTrue(AEDataFile.DATA_FILE_VERSION_NUMBER_AEDZ.equals(
                        AEViewer.normalizeLoggingDataFileVersion(AEDataFile.DATA_FILE_VERSION_NUMBER_AEDZ)),
                "validate 'aedz' accepted");
        assertTrue(AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4.equals(
                        AEViewer.normalizeLoggingDataFileVersion(AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4)),
                "validate '4.0' accepted");
        assertTrue(AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT2.equals(
                        AEViewer.normalizeLoggingDataFileVersion(AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT2)),
                "validate '2.0' accepted");
        assertTrue(AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4.equals(
                        AEViewer.normalizeLoggingDataFileVersion("9.9")),
                "invalid version defaults to AEDAT-4");
        assertTrue(AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4.equals(
                        AEViewer.normalizeLoggingDataFileVersion(null)),
                "null version defaults to AEDAT-4");
        System.out.println("PASS testNormalizeLoggingDataFileVersion");
    }

    /** The recording-format preference round-trips: index↔version inverse for AEDZ/AEDAT-4/AEDAT-2. */
    private static void testPreferenceIndexRoundTrip() {
        // Round-trip for every represented format.
        for (String version : new String[]{
            AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4,
            AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT2,
            AEDataFile.DATA_FILE_VERSION_NUMBER_AEDZ}) {
            int idx = AEViewerPreferencesDialog.loggingFormatIndexForVersion(version);
            String back = AEViewerPreferencesDialog.loggingFormatVersionForIndex(idx);
            assertTrue(version.equals(back), "preference round-trip for " + version + " (idx " + idx + ")");
        }
        // Explicit sentinel mappings (aedz == combo index 2).
        assertTrue(AEViewerPreferencesDialog.loggingFormatIndexForVersion(AEDataFile.DATA_FILE_VERSION_NUMBER_AEDZ) == 2,
                "aedz maps to combo index 2");
        assertTrue(AEDataFile.DATA_FILE_VERSION_NUMBER_AEDZ.equals(
                        AEViewerPreferencesDialog.loggingFormatVersionForIndex(2)),
                "combo index 2 maps to aedz");
        // Unknown version falls back to the AEDAT-2 (index 1) position.
        assertTrue(AEViewerPreferencesDialog.loggingFormatIndexForVersion("9.9") == 1,
                "unknown version falls back to AEDAT-2 index 1");
        assertTrue(AEViewerPreferencesDialog.loggingFormatIndexForVersion(null) == 1,
                "null version falls back to AEDAT-2 index 1");
        System.out.println("PASS testPreferenceIndexRoundTrip");
    }

    /** The AEDZ route passes the exact owner snapshot even when mutable chip state cannot supply it. */
    private static void testAedzWriterPathConstructsWriter() throws IOException {
        AEChip chip = bareChip();
        chip.getPrefs().put("AEChip.level", "37");
        RecordingConfigurationSnapshot ownerSnapshot = RecordingConfigurationSnapshot.captureFromChip(chip);
        chip.getPrefs().put("AEChip.level", "41");
        assertTrue(chip.getRecordingConfigurationSnapshot() == null,
                "explicit-identity precondition: owner did not place snapshot on chip");
        File out = File.createTempFile("jaer-aeviewer-aedzwriter", ".aedz");
        AEViewer.OpenedLogStream opened = new AEViewer.OpenedLogStream(new FileOutputStream(out), ownerSnapshot);
        final AEDZOutputStream[] writer = new AEDZOutputStream[1];
        try {
            // Same construction expression the .aedz branch of startLogging uses.
            AEViewer.constructLoggingWriter(chip, opened, (stream, snapshot) -> {
                assertTrue(snapshot == ownerSnapshot, "factory received exact owner snapshot object");
                writer[0] = new AEDZOutputStream(stream, chip, snapshot);
            });
            assertTrue(writer[0] != null, "AEDZ writer constructed on the production seam");
            assertTrue(opened.stream.getChannel().isOpen(),
                    "AEDZ writer construction success keeps the stream open (ownership transferred)");
            writer[0].close();
            writer[0] = null;
            try (AEDZInputStream in = new AEDZInputStream(out)) {
                String header = new String(in.getAedatHeader(), StandardCharsets.UTF_8);
                RecordingConfigurationSnapshot reopened = RecordingConfigurationSnapshot.parseLegacyEntries(
                        java.util.Arrays.asList(header.split("\\r?\\n")));
                assertTrue("37".equals(reopened.get("AEChip.level")),
                        "explicit owner 37 reached AEDZ instead of live 41");
            }
        } finally {
            if (writer[0] != null) {
                writer[0].close();
            }
            out.delete();
        }
        assertTrue(out.exists() == false, "temp AEDZ recording cleaned up");
        System.out.println("PASS testAedzWriterPathConstructsWriter");
    }

    /** An AEDZ writer-construction failure closes the stream and clears the snapshot exactly once. */
    private static void testAedzWriterFailureCleanup() throws IOException {
        AEChip chip = bareChip();
        chip.getPrefs().put("AEChip.level", "37");
        File out = File.createTempFile("jaer-aeviewer-aedzfail", ".aedz");
        AEViewer.OpenedLogStream opened = AEViewer.openWithFrozenSnapshot(chip, out);
        try {
            // Fail while handing the stream to the AEDZ writer (pre-ownership).
            try {
                AEViewer.constructLoggingWriter(chip, opened, (stream, snapshot) -> {
                    throw new IOException("injected AEDZ writer-construction failure");
                });
                throw new AssertionError("expected IOException");
            } catch (IOException expected) {
                // good
            }
            assertTrue(!opened.stream.getChannel().isOpen(),
                    "AEDZ writer failure closes the already-opened stream");
            assertTrue(chip.getRecordingConfigurationSnapshot() == null,
                    "AEDZ writer failure clears the chip snapshot");
        } finally {
            out.delete();
        }
        System.out.println("PASS testAedzWriterFailureCleanup");
    }

    // ------------------------------------------------------------------
    // chip / prefs helpers
    // ------------------------------------------------------------------

    private static AEChip bareChip() {
        AEChip chip = new org.objenesis.ObjenesisStd().newInstance(AEChip.class);
        chip.setPrefs(new MapBackedPreferences(null, ""));
        return chip;
    }

    /** Minimal in-memory {@link Preferences} so the probe needs no user store and mutates nothing. */
    static final class MapBackedPreferences extends AbstractPreferences {

        private final Map<String, String> store = new HashMap<>();

        MapBackedPreferences(AbstractPreferences parent, String name) {
            super(parent, name);
        }

        @Override
        protected void putSpi(String key, String value) {
            store.put(key, value);
        }

        @Override
        protected String getSpi(String key) {
            return store.get(key);
        }

        @Override
        protected void removeSpi(String key) {
            store.remove(key);
        }

        @Override
        protected void removeNodeSpi() throws BackingStoreException {
        }

        @Override
        protected String[] keysSpi() throws BackingStoreException {
            return store.keySet().toArray(new String[0]);
        }

        @Override
        protected String[] childrenNamesSpi() throws BackingStoreException {
            return new String[0];
        }

        @Override
        protected AbstractPreferences childSpi(String name) {
            return new MapBackedPreferences(this, name);
        }

        @Override
        protected void syncSpi() throws BackingStoreException {
        }

        @Override
        protected void flushSpi() throws BackingStoreException {
        }
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }
}
