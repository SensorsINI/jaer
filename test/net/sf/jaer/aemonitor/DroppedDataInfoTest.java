package net.sf.jaer.aemonitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DroppedDataInfoTest {

    @Test
    public void noneIsEmptyTokenAndNotAny() {
        DroppedDataInfo n = DroppedDataInfo.none();
        assertFalse(n.any());
        assertEquals(DroppedDataInfo.Kind.NONE, n.getKind());
        assertEquals(10, n.getStatsLineToken().length());
        assertTrue(n.getStatsLineToken().trim().isEmpty());
    }

    @Test
    public void overrunTokenFitsStatsLine() {
        DroppedDataInfo o = DroppedDataInfo.hostBufferOverrun();
        assertTrue(o.any());
        assertEquals(DroppedDataInfo.Kind.HOST_BUFFER_OVERRUN, o.getKind());
        assertEquals(10, o.getStatsLineToken().length());
        assertTrue(o.getStatsLineToken().startsWith("(overrun)"));
    }

    @Test
    public void liveKeepCapTokenAndBiasAdvice() {
        DroppedDataInfo d = DroppedDataInfo.liveKeepCap(232_695, 262_144, 80_000_000);
        assertTrue(d.any());
        assertEquals(DroppedDataInfo.Kind.LIVE_KEEP_CAP, d.getKind());
        assertEquals(10, d.getStatsLineToken().length());
        assertTrue(d.getStatsLineToken().startsWith("(DROP)"));
        assertEquals(232_695, d.getRecentCount());
        assertEquals(262_144, d.getTotalCount());
        assertTrue(d.getDetail().contains("262,144"));
        assertTrue(d.getDetail().contains("threshold") || d.getDetail().contains("refractory"));
        assertTrue(d.getDetail().contains("80,000,000"));
    }
}
