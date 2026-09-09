package net.sf.jaer.eventio.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
    public void compactDuration() {
        assertEquals("8s", SaveAsExporter.formatCompactDurationMs(8000));
        assertEquals("2m 05s", SaveAsExporter.formatCompactDurationMs(125_000));
        assertEquals("1h 03m", SaveAsExporter.formatCompactDurationMs(3_780_000));
        assertTrue(SaveAsExporter.formatCompactDurationMs(0).equals("0s"));
    }
}
