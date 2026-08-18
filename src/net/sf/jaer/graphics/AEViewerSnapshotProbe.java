package net.sf.jaer.graphics;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventio.RecordingConfigurationSnapshot;

/**
 * Headless production-path probe for the AEViewer recording-snapshot lifecycle
 * (plan Todo 3, part 3).
 *
 * <p>A full {@link AEViewer} cannot be constructed headlessly because it builds
 * a {@link javax.swing.JFrame}. This probe instead drives the exact production
 * {@link AEViewer#openWithFrozenSnapshot} seam and verifies that recording-start
 * state is captured before open, every failed open clears the cached snapshot,
 * and a later successful start captures fresh metadata rather than stale state.
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
        System.out.println("ALL PASS");
    }

    /** Success path: snapshot is frozen and placed on the chip before the file opens. */
    private static void testSuccessPathFreezesBeforeOpen() throws Exception {
        AEChip chip = bareChip();
        chip.getPrefs().put("AEChip.level", "37");
        assertTrue(chip.getRecordingConfigurationSnapshot() == null, "precondition: no snapshot yet");

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

        File bad = new File("/nonexistent-dir-jaer-xyz/junk.aedat");
        try {
            AEViewer.openWithFrozenSnapshot(chip, bad);
            throw new AssertionError("expected FileNotFoundException");
        } catch (java.io.FileNotFoundException expected) {
        }
        assertTrue(chip.getRecordingConfigurationSnapshot() == null, "clear after failed start");

        chip.getPrefs().put("AEChip.level", "99");

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
            assertTrue(!opened.stream.getChannel().isOpen(),
                    "opened stream closed after writer-constructor failure (no leaked handle)");
        } finally {
            out.delete();
        }
        System.out.println("PASS testWriterConstructionFailureClosesStream");
    }

    /** Failed writer construction releases the file and clears the chip snapshot. */
    private static void testWriterConstructionFailureReleasesFileAndSnapshot() throws Exception {
        AEChip chip = bareChip();
        chip.getPrefs().put("AEChip.level", "37");
        File out = File.createTempFile("jaer-aeviewer-wcrelease", ".aedat");
        AEViewer.OpenedLogStream opened = AEViewer.openWithFrozenSnapshot(chip, out);
        try {
            assertTrue(chip.getRecordingConfigurationSnapshot() != null,
                    "precondition: snapshot set before failure");
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
            FileOutputStream reopened = new FileOutputStream(out);
            reopened.close();
        } finally {
            out.delete();
        }
        System.out.println("PASS testWriterConstructionFailureReleasesFileAndSnapshot");
    }

    /** Repeated writer-constructor failures leak no stream or snapshot state. */
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

    /** A success after writer-constructor failure captures fresh metadata. */
    private static void testWriterFailureThenSuccessCapturesFresh() throws Exception {
        AEChip chip = bareChip();
        chip.getPrefs().put("AEChip.level", "37");

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
        assertTrue(chip.getRecordingConfigurationSnapshot() == null,
                "chip snapshot clear after failed writer construction");

        chip.getPrefs().put("AEChip.level", "99");

        File out2 = File.createTempFile("jaer-aeviewer-wcfresh2", ".aedat");
        AEViewer.OpenedLogStream opened = AEViewer.openWithFrozenSnapshot(chip, out2);
        try {
            assertTrue("99".equals(opened.snapshot.get("AEChip.level")),
                    "successful start after failed writer construction captures fresh 99, got <"
                            + opened.snapshot.get("AEChip.level") + ">");
            assertTrue(!"37".equals(chip.getRecordingConfigurationSnapshot().get("AEChip.level")),
                    "no stale reuse after failed writer construction");
        } finally {
            try {
                opened.stream.close();
            } catch (IOException ignore) {
            }
            out2.delete();
        }
        System.out.println("PASS testWriterFailureThenSuccessCapturesFresh");
    }

    /** Successful writer construction transfers ownership and leaves the stream open. */
    private static void testSuccessfulWriterConstructionKeepsStreamOpen() throws Exception {
        AEChip chip = bareChip();
        chip.getPrefs().put("AEChip.level", "37");
        File out = File.createTempFile("jaer-aeviewer-wcsuccess", ".aedat");
        AEViewer.OpenedLogStream opened = AEViewer.openWithFrozenSnapshot(chip, out);
        try {
            AEViewer.constructLoggingWriter(chip, opened, (stream, snapshot) -> {
                // ownership transferred on normal return
            });
            assertTrue(opened.stream.getChannel().isOpen(),
                    "successful writer construction leaves owned stream open");
        } finally {
            try {
                opened.stream.close();
            } catch (IOException ignore) {
            }
            out.delete();
        }
        System.out.println("PASS testSuccessfulWriterConstructionKeepsStreamOpen");
    }

    private static AEChip bareChip() {
        AEChip chip = new org.objenesis.ObjenesisStd().newInstance(AEChip.class);
        chip.setPrefs(new MapBackedPreferences(null, ""));
        return chip;
    }

    /** Minimal in-memory {@link Preferences} so the probe never touches the user store. */
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

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
