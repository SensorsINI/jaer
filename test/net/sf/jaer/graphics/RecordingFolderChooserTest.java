package net.sf.jaer.graphics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.Test;

/**
 * Use this folder while the chooser is already inside the recording folder.
 * Aqua resolves the filename field under the open directory, so a field that
 * still says {@code flybox} becomes the child path {@code flybox/flybox}.
 */
public class RecordingFolderChooserTest {

    @Test
    public void alreadyInsideFolderAcceptsThatFolder() throws Exception {
        File parent = Files.createTempDirectory("rec-folder-inside").toFile();
        File flybox = new File(parent, "flybox");
        assertTrue(flybox.mkdir());
        File missingChild = new File(flybox, "flybox");

        File chosen = RecordingFolderChooser.resolveChosenDirectory(flybox, missingChild);

        assertEquals(flybox.getCanonicalFile(), chosen.getCanonicalFile());
    }

    @Test
    public void alreadyInsideIgnoresSameNamedChild() throws Exception {
        File parent = Files.createTempDirectory("rec-folder-nested").toFile();
        File flybox = new File(parent, "flybox");
        File nested = new File(flybox, "flybox");
        assertTrue(nested.mkdirs());

        File chosen = RecordingFolderChooser.resolveChosenDirectory(flybox, nested);

        assertEquals(flybox.getCanonicalFile(), chosen.getCanonicalFile());
    }

    @Test
    public void enclosingFolderSelectsNamedChild() throws Exception {
        File parent = Files.createTempDirectory("rec-folder-up").toFile();
        File flybox = new File(parent, "flybox");
        assertTrue(flybox.mkdir());

        File chosen = RecordingFolderChooser.resolveChosenDirectory(parent, flybox);

        assertEquals(flybox.getCanonicalFile(), chosen.getCanonicalFile());
    }

    @Test
    public void noSelectionUsesOpenFolder() throws Exception {
        File parent = Files.createTempDirectory("rec-folder-empty").toFile();
        File flybox = new File(parent, "flybox");
        assertTrue(flybox.mkdir());

        File chosen = RecordingFolderChooser.resolveChosenDirectory(flybox, null);

        assertEquals(flybox.getCanonicalFile(), chosen.getCanonicalFile());
    }

    @Test
    public void highlightedFileStillUsesItsFolder() throws Exception {
        File parent = Files.createTempDirectory("rec-folder-file").toFile();
        File flybox = new File(parent, "flybox");
        assertTrue(flybox.mkdir());
        File recording = new File(flybox, "clip.aedat4");
        assertTrue(recording.createNewFile());

        File chosen = RecordingFolderChooser.resolveChosenDirectory(flybox, recording);

        assertEquals(flybox.getCanonicalFile(), chosen.getCanonicalFile());
    }

    @Test
    public void missingTypedNameIsRejected() throws Exception {
        File parent = Files.createTempDirectory("rec-folder-missing").toFile();
        File flybox = new File(parent, "flybox");
        assertTrue(flybox.mkdir());
        File missing = new File(flybox, "no-such");

        assertNull(RecordingFolderChooser.resolveChosenDirectory(flybox, missing));
    }

    @Test
    public void existingSubfolderNameSelectsThatSubfolder() throws Exception {
        File parent = Files.createTempDirectory("rec-folder-sub").toFile();
        File flybox = new File(parent, "flybox");
        File sub = new File(flybox, "session");
        assertTrue(sub.mkdirs());

        File chosen = RecordingFolderChooser.resolveChosenDirectory(flybox, sub);

        assertEquals(sub.getCanonicalFile(), chosen.getCanonicalFile());
    }
}
