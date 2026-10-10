package net.sf.jaer.eventio.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.Test;

/**
 * IN/OUT clip progress and ETA helpers for File → Save As.
 */
public class SaveAsExporterTest {

    @Test
    public void inOutClipStartsAtZeroNotFilePercent() {
        long start = 50;
        long end = 70;
        assertEquals(0, SaveAsExporter.clipProgressPercent(start, end, start));
        assertEquals(50, SaveAsExporter.clipProgressPercent(start, end, 60));
        assertEquals(99, SaveAsExporter.clipProgressPercent(start, end, end));
    }

    @Test
    public void wholeFilePositionIsPercentOfFile() {
        assertEquals(0, SaveAsExporter.clipProgressPercent(0, 100, 0));
        assertEquals(25, SaveAsExporter.clipProgressPercent(0, 100, 25));
        assertEquals(99, SaveAsExporter.clipProgressPercent(0, 100, 100));
    }

    @Test
    public void etaNeedsASecondAndSomeCoverage() {
        assertNull(SaveAsExporter.formatEta(500_000_000L, 10, 100));
        assertEquals("ETA 9s", SaveAsExporter.formatEta(1_000_000_000L, 10, 100));
        assertEquals("ETA 0s", SaveAsExporter.formatEta(2_000_000_000L, 100, 100));
    }

    @Test
    public void etaDoesNotOverflowLongMultiplyToZero() {
        // 3 min elapsed, 80M/280M of a large clip — old code: elapsed*remaining overflowed.
        long elapsed = 180L * 1_000_000_000L;
        long remainingNs = SaveAsExporter.etaRemainingNs(elapsed, 80_000_000L, 280_000_000L);
        assertTrue(remainingNs > 60L * 1_000_000_000L);
        assertEquals("ETA 7m 30s", SaveAsExporter.formatEta(elapsed, 80_000_000L, 280_000_000L));
    }

    @Test
    public void etaIgnoresPrepAndFollowsOutputRate() {
        long range = 2_083_721_691L;
        long covered = 3_000_000L;
        long prepPlusWrite = 47L * 1_000_000_000L;
        long diluted = SaveAsExporter.etaRemainingNs(prepPlusWrite, covered, range);
        assertTrue(diluted > 8L * 3600L * 1_000_000_000L);
        // No output clock yet: do not invent an ETA from the open/index stall.
        assertEquals(-1L, SaveAsExporter.etaRemainingFromOutput(0L, covered, range, 500_000f));
        assertEquals(-1L, SaveAsExporter.etaRemainingFromOutput(2L * 1_000_000_000L, 0L, range, 500_000f));
        // Once bytes are flowing, the running rate replaces the diluted average.
        long tracked = SaveAsExporter.etaRemainingFromOutput(prepPlusWrite, covered, range, 500_000f);
        assertTrue(tracked > 0L);
        assertTrue(tracked < diluted / 2L);
    }

    @Test
    public void cancelDeletesPartialAndKeepsSource() throws Exception {
        File dir = Files.createTempDirectory("save-as-cancel").toFile();
        File source = new File(dir, "source.aedat4");
        File partial = new File(dir, "source-export.aedat4");
        File frames = new File(dir, "source-export-frames");
        assertTrue(source.createNewFile());
        assertTrue(partial.createNewFile());
        assertTrue(frames.mkdir());
        assertTrue(new File(frames, "timestamps.txt").createNewFile());
        assertFalse(SaveAsExporter.deleteIfExportOutput(source, source));
        assertTrue(source.isFile());
        assertTrue(SaveAsExporter.deleteIfExportOutput(partial, source));
        assertFalse(partial.exists());
        SaveAsExporter.deleteExportTree(frames, source);
        assertFalse(frames.exists());
        assertTrue(source.isFile());
        assertTrue(source.delete());
        assertTrue(dir.delete());
    }

    @Test
    public void compactDuration() {
        assertEquals("8s", SaveAsExporter.formatCompactDurationMs(8000));
        assertEquals("2m 05s", SaveAsExporter.formatCompactDurationMs(125_000));
        assertEquals("1h 03m", SaveAsExporter.formatCompactDurationMs(3_780_000));
        assertTrue(SaveAsExporter.formatCompactDurationMs(0).equals("0s"));
    }
}
