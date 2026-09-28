package net.sf.jaer.eventprocessing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AcquisitionProcessingStatsTest {

    @Test
    public void intervalMeanAndStdArePrimary() {
        AcquisitionProcessingStats stats = new AcquisitionProcessingStats();
        assertEquals("Low-latency mode", stats.line());
        // Two 1 ms gaps: interval 1.0m ± 0 s, rate 1.0k /s = 1/mean.
        assertFalse(stats.noteCycle(0L));
        assertTrue(stats.noteCycle(1_000_000L));
        assertTrue(stats.noteCycle(2_000_000L));
        assertEquals("Low-latency mode  1.0m ± 0 s  1.0k /s", stats.line());
    }

    @Test
    public void windowDropsSamplesOlderThan100() {
        AcquisitionProcessingStats stats = new AcquisitionProcessingStats();
        long t = 0;
        stats.noteCycle(t);
        for (int i = 0; i < AcquisitionProcessingStats.WINDOW; i++) {
            t += 1_000_000L;
            stats.noteCycle(t);
        }
        for (int i = 0; i < AcquisitionProcessingStats.WINDOW; i++) {
            t += 2_000_000L;
            stats.noteCycle(t);
        }
        assertEquals("Low-latency mode  2.0m ± 0 s  500.0 /s", stats.line());
    }

    @Test
    public void resetClearsTheLine() {
        AcquisitionProcessingStats stats = new AcquisitionProcessingStats();
        stats.noteCycle(0L);
        stats.noteCycle(1_000_000L);
        stats.reset();
        assertEquals("Low-latency mode", stats.line());
    }
}
