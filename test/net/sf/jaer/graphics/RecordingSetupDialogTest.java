package net.sf.jaer.graphics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Date;

import org.junit.Test;

import net.sf.jaer.eventio.AEDataFile;
import net.sf.jaer.eventio.RecordingFilename;

/**
 * Filename/format helpers for the once-per-session recording setup dialog.
 */
public class RecordingSetupDialogTest {

    @Test
    public void withFormatExtensionReplacesDataSuffix() {
        assertEquals("Davis346_2026-09-09.aedat4",
                RecordingSetupDialog.withFormatExtension("Davis346_2026-09-09.aedz",
                        AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4));
        assertEquals("cam.aedat2",
                RecordingSetupDialog.withFormatExtension("cam.aedat4",
                        AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT2));
        assertEquals("cam.aedz",
                RecordingSetupDialog.withFormatExtension("cam",
                        AEDataFile.DATA_FILE_VERSION_NUMBER_AEDZ));
    }

    @Test
    public void proposedFileUsesFolderAndVersionExtension() {
        File dir = new File(".");
        File out = RecordingSetupDialog.proposedFile(null, null,
                AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT4, new Date(0L), dir);
        assertTrue(out.getName().endsWith(AEDataFile.DATA_FILE_EXTENSION_AEDAT4));
        assertEquals(dir.getAbsoluteFile(), out.getParentFile().getAbsoluteFile());
        String expectedBase = RecordingFilename.singleCameraBase(null, new Date(0L));
        assertTrue(out.getName().startsWith(expectedBase));
    }

    @Test
    public void setupDialogShowsForFirstThreeThenFileMenuOrTimed() {
        assertTrue(RecordingSetupDialog.shouldShow(0, 0L, false));
        assertTrue(RecordingSetupDialog.shouldShow(2, 0L, false));
        assertTrue(!RecordingSetupDialog.shouldShow(3, 0L, false));
        assertTrue(RecordingSetupDialog.shouldShow(3, 0L, true));
        assertTrue(RecordingSetupDialog.shouldShow(99, 60_000L, false));
    }
}
