package net.sf.jaer.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

/** Folder qualification for sample-data unpack. Does not touch Java preferences. */
public class SampleDataSupportFolderTest {

    @Test
    public void parentGetsJaerSampleDataChild() throws Exception {
        File parent = Files.createTempDirectory("jaer-sample-parent").toFile();
        File downloads = new File(parent, "Downloads");
        assertTrue(downloads.mkdir());
        File qualified = SampleDataSupport.qualifiedUnpackFolder(downloads);
        assertEquals(new File(downloads, SampleDataSupport.FOLDER_NAME), qualified);
        assertTrue(downloads.isDirectory());
        assertFalse(qualified.exists());
    }

    @Test
    public void alreadyQualifiedFolderStays() throws Exception {
        File parent = Files.createTempDirectory("jaer-sample-canon").toFile();
        File folder = new File(parent, SampleDataSupport.FOLDER_NAME);
        assertTrue(folder.mkdir());
        assertEquals(folder, SampleDataSupport.qualifiedUnpackFolder(folder));
    }

    @Test
    public void legacyFolderIsRenamed() throws Exception {
        File parent = Files.createTempDirectory("jaer-sample-legacy").toFile();
        File legacy = new File(parent, SampleDataSupport.LEGACY_FOLDER_NAME);
        assertTrue(legacy.mkdir());
        File clip = new File(legacy, "clip.aedat4");
        Files.writeString(clip.toPath(), "x", StandardCharsets.UTF_8);
        File qualified = SampleDataSupport.qualifiedUnpackFolder(legacy);
        File expected = new File(parent, SampleDataSupport.FOLDER_NAME);
        assertEquals(expected, qualified);
        assertTrue(expected.isDirectory());
        assertFalse(legacy.exists());
        assertTrue(new File(expected, "clip.aedat4").isFile());
    }

    @Test
    public void misplacedUnpackMovesOnlySampleFiles() throws Exception {
        File mixed = Files.createTempDirectory("jaer-sample-mixed").toFile();
        Files.writeString(new File(mixed, "README.md").toPath(), "samples", StandardCharsets.UTF_8);
        Files.writeString(new File(mixed, "SIZE.txt").toPath(), "zipMiB=1", StandardCharsets.UTF_8);
        Files.writeString(new File(mixed, "clip.aedat4").toPath(), "events", StandardCharsets.UTF_8);
        Files.writeString(new File(mixed, "ticket.pdf").toPath(), "keep", StandardCharsets.UTF_8);
        File child = SampleDataSupport.relocateMisplacedRecordings(mixed);
        assertEquals(new File(mixed, SampleDataSupport.FOLDER_NAME), child);
        assertTrue(new File(child, "README.md").isFile());
        assertTrue(new File(child, "SIZE.txt").isFile());
        assertTrue(new File(child, "clip.aedat4").isFile());
        assertFalse(new File(mixed, "README.md").exists());
        assertTrue(new File(mixed, "ticket.pdf").isFile());
    }

    @Test
    public void canonicalFolderIsNotRelocated() throws Exception {
        File parent = Files.createTempDirectory("jaer-sample-noreloc").toFile();
        File folder = new File(parent, SampleDataSupport.FOLDER_NAME);
        assertTrue(folder.mkdir());
        Files.writeString(new File(folder, "README.md").toPath(), "samples", StandardCharsets.UTF_8);
        assertNull(SampleDataSupport.relocateMisplacedRecordings(folder));
        assertTrue(new File(folder, "README.md").isFile());
    }

    @Test
    public void legacyKeptWhenItHasRecordingsAndCanonDoesNot() throws Exception {
        File parent = Files.createTempDirectory("jaer-sample-both").toFile();
        File legacy = new File(parent, SampleDataSupport.LEGACY_FOLDER_NAME);
        File canon = new File(parent, SampleDataSupport.FOLDER_NAME);
        assertTrue(legacy.mkdir());
        assertTrue(canon.mkdir());
        Files.writeString(new File(legacy, "clip.aedat4").toPath(), "events", StandardCharsets.UTF_8);
        Files.writeString(new File(canon, "README.md").toPath(), "readme", StandardCharsets.UTF_8);
        assertEquals(legacy, SampleDataSupport.qualifiedUnpackFolder(legacy));
        assertTrue(new File(legacy, "clip.aedat4").isFile());
    }

    @Test
    public void stripSampleRootLeavesFlatEntries() {
        assertEquals("clip.aedat4", SampleDataSupport.stripSampleRoot("clip.aedat4"));
        assertEquals("clip.aedat4", SampleDataSupport.stripSampleRoot("jaerSampleData/clip.aedat4"));
        assertEquals("clip.aedat4", SampleDataSupport.stripSampleRoot("sampleData/clip.aedat4"));
        assertEquals("", SampleDataSupport.stripSampleRoot("jaerSampleData/"));
    }
}
