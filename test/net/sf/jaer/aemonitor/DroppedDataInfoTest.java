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

}
