package net.sf.jaer.graphics;

import java.io.File;
import java.nio.file.Files;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventio.RecordingFilename;

/**
 * Headless checks for {@link RecordingVcrSession} names, rotate-delete order,
 * manifest round-trip, and setup-dialog sticky VCR show policy. Run after
 * {@code ant compile}:
 * {@code java -cp build/classes;lib/*;jars/* net.sf.jaer.graphics.RecordingVcrSessionDemo}
 */
public final class RecordingVcrSessionDemo {

    public static void main(String[] args) throws Exception {
        RecordingSetupDialog.resetShownThisJvmForTests();
        testNames();
        testClampAndParse();
        testRotateDeleteOrder();
        testInfiniteKeepsAll();
        testManifestRoundTrip();
        testStickyShouldShow();
        testLastTimedPrefs();
        testVcrAvailableAndFirstCassette();
        testAttachExistingThenNextCassette();
        testIsDeck();
        RecordingSetupDialog.resetShownThisJvmForTests();
        System.out.println("ALL PASS");
    }

    private static void testNames() {
        String base = RecordingVcrSession.sessionFolderName("Davis346blue_2026-09-13T06-30-12-0400");
        assertTrue(base.equals("Davis346blue_2026-09-13T06-30-12-0400"), "keep chip+date basename");
        assertTrue(RecordingVcrSession.cassetteFileName(base, 1).equals(
                base + "_c0001" + AEDataFile.DATA_FILE_EXTENSION_AEDAT4), "c0001");
        assertTrue(RecordingVcrSession.cassetteIndexLabel(12).equals("c0012".substring(1)), "0012");
        String fromChip = RecordingVcrSession.sessionFolderName(
                RecordingFilename.singleCameraBase(null, new Date(0L)));
        assertTrue(!fromChip.contains("/") && !fromChip.contains("\\"), "no path sep");
        assertTrue(RecordingVcrSession.sessionFolderName("cam.aedat4").equals("cam"), "strip ext");
        String marked = RecordingVcrSession.vcrSessionFolderName(
                "NRVS5KRC1S-us2addr5_2026-09-13T08-42-43-0400");
        assertTrue(marked.equals("NRVS5KRC1S-us2addr5_2026-09-13T08-42-43-0400-VCR"), marked);
        assertTrue(RecordingVcrSession.vcrSessionFolderName("cam-VCR").equals("cam-VCR"), "no double mark");
        assertTrue(RecordingVcrSession.vcrSessionFolderName("cam-vcr.aedat4").equals("cam-vcr"),
                "keep existing mark case");
        assertTrue(RecordingVcrSession.stripVcrFolderMark("cam-VCR.aedat4").equals("cam"), "strip mark");
        System.out.println("PASS testNames");
    }

    private static void testClampAndParse() {
        assertTrue(RecordingVcrSession.clampRotateKeep(1) == RecordingVcrSession.ROTATE_MIN, "min 2");
        assertTrue(RecordingVcrSession.clampRotateKeep(8) == 8, "default");
        assertTrue(RecordingVcrSession.clampRotateKeep(10_000) == RecordingVcrSession.ROTATE_MAX, "max");
        assertTrue(RecordingVcrSession.parseMode("rotate") == RecordingVcrSession.Mode.ROTATE, "parse");
        assertTrue(RecordingVcrSession.parseMode("") == RecordingVcrSession.Mode.OFF, "blank off");
        assertTrue(!RecordingVcrSession.enabled(RecordingVcrSession.Mode.OFF, 60_000L), "off disabled");
        assertTrue(!RecordingVcrSession.enabled(RecordingVcrSession.Mode.INFINITE, 0L), "no duration");
        assertTrue(RecordingVcrSession.enabled(RecordingVcrSession.Mode.ROTATE, 60_000L), "rotate on");
        System.out.println("PASS testClampAndParse");
    }

    private static void testRotateDeleteOrder() throws Exception {
        File tmp = Files.createTempDirectory("vcr-rotate-").toFile();
        try {
            RecordingVcrSession s = RecordingVcrSession.begin(tmp, "Chip_t0", new Date(0L),
                    RecordingVcrSession.Mode.ROTATE, 3, 60_000L);
            for (int i = 1; i <= 5; i++) {
                File next = s.openNextCassette();
                Files.writeString(next.toPath(), "cassette-" + i);
                File closed = s.closeCurrentCassette();
                assertTrue(closed.equals(next), "closed matches opened");
            }
            List<File> closed = s.getClosedCassettes();
            assertTrue(closed.size() == 3, "keep 3 closed names, got " + closed.size());
            Set<String> names = new HashSet<>();
            for (File f : closed) {
                names.add(f.getName());
                assertTrue(f.isFile(), "kept file exists " + f.getName());
            }
            assertTrue(names.contains(RecordingVcrSession.cassetteFileName(s.getBasename(), 3)), "c0003");
            assertTrue(names.contains(RecordingVcrSession.cassetteFileName(s.getBasename(), 4)), "c0004");
            assertTrue(names.contains(RecordingVcrSession.cassetteFileName(s.getBasename(), 5)), "c0005");
            assertTrue(!new File(s.getSessionDir(),
                    RecordingVcrSession.cassetteFileName(s.getBasename(), 1)).isFile(), "c0001 deleted");
            assertTrue(!new File(s.getSessionDir(),
                    RecordingVcrSession.cassetteFileName(s.getBasename(), 2)).isFile(), "c0002 deleted");
            assertTrue(s.overlayCassetteLabel().equals("VCR c0005/3"), s.overlayCassetteLabel());
        } finally {
            deleteTree(tmp);
        }
        System.out.println("PASS testRotateDeleteOrder");
    }

    private static void testInfiniteKeepsAll() throws Exception {
        File tmp = Files.createTempDirectory("vcr-inf-").toFile();
        try {
            RecordingVcrSession s = RecordingVcrSession.begin(tmp, "Inf_t0", new Date(1L),
                    RecordingVcrSession.Mode.INFINITE, 8, 10_000L);
            for (int i = 1; i <= 4; i++) {
                Files.writeString(s.openNextCassette().toPath(), "i" + i);
                s.closeCurrentCassette();
            }
            assertTrue(s.getClosedCassettes().size() == 4, "infinite keeps 4");
            assertTrue(s.overlayCassetteLabel().startsWith("VCR "), "infinite overlay");
            assertTrue(s.overlayCassetteLabel().contains("∞"), "infinite mark");
            File c1 = new File(s.getSessionDir(), RecordingVcrSession.cassetteFileName(s.getBasename(), 1));
            assertTrue(c1.isFile(), "c0001 still there");
        } finally {
            deleteTree(tmp);
        }
        System.out.println("PASS testInfiniteKeepsAll");
    }

    private static void testManifestRoundTrip() throws Exception {
        File tmp = Files.createTempDirectory("vcr-man-").toFile();
        try {
            RecordingVcrSession s = RecordingVcrSession.begin(tmp, "Man_t0", new Date(2L),
                    RecordingVcrSession.Mode.ROTATE, 8, 120_000L);
            Files.writeString(s.openNextCassette().toPath(), "one");
            s.closeCurrentCassette();
            File current = s.openNextCassette();
            Files.writeString(current.toPath(), "two");
            RecordingVcrSession loaded = RecordingVcrSession.readManifest(s.getSessionDir());
            assertTrue(loaded.getMode() == RecordingVcrSession.Mode.ROTATE, "mode");
            assertTrue(loaded.getRotateKeep() == 8, "keep");
            assertTrue(loaded.getCassetteDurationMs() == 120_000L, "duration");
            assertTrue(loaded.getCassetteIndex() == 2, "index");
            assertTrue(loaded.getClosedCassettes().size() == 1, "one closed");
            assertTrue(loaded.getCurrentCassette().getName().equals(current.getName()), "current");
        } finally {
            deleteTree(tmp);
        }
        System.out.println("PASS testManifestRoundTrip");
    }

    private static void testStickyShouldShow() {
        RecordingSetupDialog.resetShownThisJvmForTests();
        assertTrue(!RecordingSetupDialog.isSessionVcrEnabled(), "default off");
        assertTrue(!RecordingSetupDialog.shouldShow(3, 0L, false), "fourth skip");
        assertTrue(RecordingSetupDialog.shouldShow(3, 0L, false, true), "VCR always shows");
        RecordingSetupDialog.setSessionVcr(RecordingVcrSession.Mode.INFINITE, 1);
        assertTrue(RecordingSetupDialog.sessionRotateKeep() == RecordingVcrSession.ROTATE_MIN, "clamp sticky");
        assertTrue(RecordingSetupDialog.isSessionVcrEnabled(), "sticky on");
        assertTrue(RecordingSetupDialog.shouldShow(99, 0L, false,
                RecordingSetupDialog.isSessionVcrEnabled()), "sticky VCR shows");
        RecordingSetupDialog.resetShownThisJvmForTests();
        assertTrue(!RecordingSetupDialog.isSessionVcrEnabled(), "reset off");
        System.out.println("PASS testStickyShouldShow");
    }

    private static void testLastTimedPrefs() {
        RecordingSetupDialog.LastTimed previous = RecordingSetupDialog.lastTimedPrefs();
        try {
            RecordingSetupDialog.resetShownThisJvmForTests();
            RecordingSetupDialog.persistLastTimedPrefs(60_000L, RecordingVcrSession.Mode.ROTATE, 3);
            RecordingSetupDialog.resetShownThisJvmForTests();
            assertTrue(RecordingSetupDialog.shouldRestoreLastTimedFromPrefs(), "fresh JVM restores prefs");
            RecordingSetupDialog.LastTimed loaded = RecordingSetupDialog.lastTimedPrefs();
            assertTrue(loaded.timeLimitMs == 60_000L, "limit");
            assertTrue(loaded.vcrMode == RecordingVcrSession.Mode.ROTATE, "rotate");
            assertTrue(loaded.rotateKeep == 3, "keep 3");
            RecordingSetupDialog.setSessionVcr(RecordingVcrSession.Mode.INFINITE, 8);
            assertTrue(!RecordingSetupDialog.shouldRestoreLastTimedFromPrefs(), "sticky VCR keeps UI");
            RecordingSetupDialog.resetShownThisJvmForTests();
            RecordingSetupDialog.LastTimed still = RecordingSetupDialog.lastTimedPrefs();
            assertTrue(still.timeLimitMs == 60_000L && still.vcrMode == RecordingVcrSession.Mode.ROTATE,
                    "Cancel does not write; last persist remains");
            RecordingSetupDialog.persistLastTimedPrefs(0L, RecordingVcrSession.Mode.OFF, 8);
            RecordingSetupDialog.LastTimed unlimited = RecordingSetupDialog.lastTimedPrefs();
            assertTrue(unlimited.timeLimitMs == 0L, "Start unlimited writes 0");
            assertTrue(unlimited.vcrMode == RecordingVcrSession.Mode.OFF, "Start unlimited writes OFF");
        } finally {
            RecordingSetupDialog.persistLastTimedPrefs(previous.timeLimitMs, previous.vcrMode, previous.rotateKeep);
            RecordingSetupDialog.resetShownThisJvmForTests();
        }
        System.out.println("PASS testLastTimedPrefs");
    }

    private static void testVcrAvailableAndFirstCassette() {
        assertTrue(RecordingSetupDialog.vcrAvailable(AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4, 60_000L),
                "aedat4 + limit");
        assertTrue(!RecordingSetupDialog.vcrAvailable(AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4, 0L),
                "needs duration");
        assertTrue(!RecordingSetupDialog.vcrAvailable(AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT2, 60_000L),
                "not aedat2");
        File parent = new File("recordings");
        File first = RecordingVcrSession.firstCassetteFile(parent, "Davis346blue_t0.aedat4");
        assertTrue(first.getName().equals("Davis346blue_t0_c0001.aedat4"), first.getName());
        assertTrue(first.getParentFile().getName().equals("Davis346blue_t0"), first.getParent());
        assertTrue(first.getParentFile().getParentFile().equals(parent), "parent folder");
        assertTrue(RecordingVcrSession.cassetteIndexFromFile(first) == 1, "index 1");
        assertTrue("VCR c0003/8".equals(
                RecordingVcrSession.overlayLine(RecordingVcrSession.Mode.ROTATE, 8, 3)), "rotate overlay");
        assertTrue("VCR c0001 ∞".equals(
                RecordingVcrSession.overlayLine(RecordingVcrSession.Mode.INFINITE, 8, 1)), "infinite overlay");
        assertTrue(RecordingVcrSession.overlayLine(RecordingVcrSession.Mode.OFF, 8, 1) == null, "off");
        System.out.println("PASS testVcrAvailableAndFirstCassette");
    }

    private static void testAttachExistingThenNextCassette() throws Exception {
        File tmp = Files.createTempDirectory("vcr-attach-demo-").toFile();
        try {
            File dir = new File(tmp, "Chip_t1");
            assertTrue(dir.mkdirs(), "mkdir session");
            File first = RecordingVcrSession.firstCassetteFile(tmp, "Chip_t1");
            Files.writeString(first.toPath(), "c1");
            RecordingVcrSession s = RecordingVcrSession.attachExisting(dir,
                    RecordingVcrSession.Mode.ROTATE, 3, 60_000L, new Date(0L));
            s.noteOpenedCassette(first);
            assertTrue(s.getCassetteIndex() == 1, "index 1");
            s.closeCurrentCassette();
            File next = s.openNextCassette();
            assertTrue(RecordingVcrSession.cassetteIndexFromFile(next) == 2, "c0002");
            assertTrue(next.getParentFile().equals(dir), "same session dir");
        } finally {
            deleteTree(tmp);
        }
        System.out.println("PASS testAttachExistingThenNextCassette");
    }

    private static void testIsDeck() throws Exception {
        File tmp = Files.createTempDirectory("vcr-deck-demo-").toFile();
        try {
            assertTrue(!RecordingVcrSession.isDeck(tmp), "empty");
            RecordingVcrSession s = RecordingVcrSession.begin(tmp, "Deck_t0", new Date(0L),
                    RecordingVcrSession.Mode.INFINITE, 8, 60_000L);
            assertTrue(RecordingVcrSession.isDeck(s.getSessionDir()), "manifest makes a deck");
            Files.writeString(s.openNextCassette().toPath(), "c1");
            s.closeCurrentCassette();
            File c2 = s.openNextCassette();
            Files.writeString(c2.toPath(), "c2");
            assertTrue(RecordingVcrSession.isDeck(c2), "cassette");
            assertTrue(RecordingVcrSession.previewOverlay(s.getSessionDir()).contains("VCR deck"),
                    "overlay");
        } finally {
            deleteTree(tmp);
        }
        System.out.println("PASS testIsDeck");
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
