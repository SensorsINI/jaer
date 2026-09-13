package net.sf.jaer.graphics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Date;
import java.util.List;

import org.junit.Test;

import net.sf.jaer.eventio.AEDataFile;

/**
 * Names and rotate-delete order for VCR timed recording sessions.
 */
public class RecordingVcrSessionTest {

    @Test
    public void cassetteNamesUsePaddedIndex() {
        String base = RecordingVcrSession.sessionFolderName("Davis346blue_2026-09-13T06-30-12-0400");
        assertEquals("Davis346blue_2026-09-13T06-30-12-0400_c0001" + AEDataFile.DATA_FILE_EXTENSION_AEDAT4,
                RecordingVcrSession.cassetteFileName(base, 1));
        assertEquals("Davis346blue_2026-09-13T06-30-12-0400-VCR",
                RecordingVcrSession.vcrSessionFolderName(base));
        assertEquals("cam-VCR", RecordingVcrSession.ensureVcrFolderMark("cam-VCR.aedat4"));
        assertEquals("cam", RecordingVcrSession.stripVcrFolderMark("cam-VCR"));
        assertEquals(2, RecordingVcrSession.clampRotateKeep(1));
        assertEquals(999, RecordingVcrSession.clampRotateKeep(5_000));
    }

    @Test
    public void rotate3KeepsLastThreeFiles() throws Exception {
        File tmp = Files.createTempDirectory("vcr-junit-").toFile();
        try {
            RecordingVcrSession s = RecordingVcrSession.begin(tmp, "JChip_t0", new Date(0L),
                    RecordingVcrSession.Mode.ROTATE, 3, 60_000L);
            for (int i = 1; i <= 5; i++) {
                Files.writeString(s.openNextCassette().toPath(), Integer.toString(i));
                s.closeCurrentCassette();
            }
            List<File> closed = s.getClosedCassettes();
            assertEquals(3, closed.size());
            assertFalse(new File(s.getSessionDir(),
                    RecordingVcrSession.cassetteFileName(s.getBasename(), 1)).isFile());
            assertTrue(new File(s.getSessionDir(),
                    RecordingVcrSession.cassetteFileName(s.getBasename(), 5)).isFile());
            assertEquals("VCR keep 3 c0005", s.overlayCassetteLabel());
        } finally {
            deleteTree(tmp);
        }
    }

    @Test
    public void finite3StopsAfterThreeCassettes() throws Exception {
        File tmp = Files.createTempDirectory("vcr-finite-junit-").toFile();
        try {
            RecordingVcrSession s = RecordingVcrSession.begin(tmp, "JFin", new Date(0L),
                    RecordingVcrSession.Mode.FINITE, 3, 60_000L);
            for (int i = 1; i <= 3; i++) {
                assertFalse(s.isFiniteComplete());
                Files.writeString(s.openNextCassette().toPath(), Integer.toString(i));
                s.closeCurrentCassette();
            }
            assertTrue(s.isFiniteComplete());
            assertEquals(3, s.getClosedCassettes().size());
            assertEquals("VCR c0003/3", s.overlayCassetteLabel());
        } finally {
            deleteTree(tmp);
        }
    }

    @Test
    public void attachExistingThenRollOpensC0002() throws Exception {
        File tmp = Files.createTempDirectory("vcr-attach-").toFile();
        try {
            File dir = new File(tmp, "Chip_t1");
            assertTrue(dir.mkdirs());
            File first = RecordingVcrSession.firstCassetteFile(tmp, "Chip_t1");
            Files.writeString(first.toPath(), "c1");
            RecordingVcrSession s = RecordingVcrSession.attachExisting(dir,
                    RecordingVcrSession.Mode.INFINITE, 8, 60_000L, new Date(0L));
            s.noteOpenedCassette(first);
            assertEquals(1, s.getCassetteIndex());
            s.closeCurrentCassette();
            File next = s.openNextCassette();
            assertEquals(2, RecordingVcrSession.cassetteIndexFromFile(next));
            assertEquals(first.getParentFile(), next.getParentFile());
        } finally {
            deleteTree(tmp);
        }
    }

    @Test
    public void folderWithManifestIsDeck() throws Exception {
        File tmp = Files.createTempDirectory("vcr-deck-junit-").toFile();
        try {
            RecordingVcrSession s = RecordingVcrSession.begin(tmp, "JDeck", new Date(0L),
                    RecordingVcrSession.Mode.ROTATE, 3, 1000L);
            assertTrue(RecordingVcrSession.isDeck(s.getSessionDir()));
            Files.writeString(s.openNextCassette().toPath(), "a");
            s.closeCurrentCassette();
            File c2 = s.openNextCassette();
            Files.writeString(c2.toPath(), "b");
            assertTrue(RecordingVcrSession.isDeck(c2));
            assertTrue(RecordingVcrSession.isDeck(s.manifestFile()));
            assertEquals(s.getSessionDir(), RecordingVcrSession.deckFolder(c2));
        } finally {
            deleteTree(tmp);
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
}
