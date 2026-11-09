package net.sf.jaer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Shared IN/OUT times for synchronized multi-viewer playback. */
public class SyncPlayerMarkTest {

    @Test
    public void rewindUsesEarliestInOrFileStart() {
        assertEquals(0L, SyncPlayer.syncedRewindUs(new long[] {5_000_000L}, new boolean[] {false}, 0L));
        assertEquals(1_000_000L, SyncPlayer.syncedRewindUs(
                new long[] {1_000_000L, 4_000_000L}, new boolean[] {true, true}, 0L));
        // A camera that starts later must not pull the section forward.
        assertEquals(2_000_000L, SyncPlayer.syncedRewindUs(
                new long[] {2_000_000L, 9_000_000L}, new boolean[] {true, false}, 0L));
    }

    @Test
    public void outLimitUsesLatestOut() {
        assertNull(SyncPlayer.syncedOutLimitUs(new long[] {100L}, new boolean[] {false}));
        // A short file clamped at its own end must not cut the longer recording.
        assertEquals(Long.valueOf(8_000_000L), SyncPlayer.syncedOutLimitUs(
                new long[] {3_000_000L, 8_000_000L}, new boolean[] {true, true}));
    }

    @Test
    public void playheadStepsPastOutAndStillShowsTheOutSlice() {
        assertEquals(false, SyncPlayer.playheadPastEnd(8_000_000L, 8_000_000L));
        assertEquals(true, SyncPlayer.playheadPastEnd(8_000_001L, 8_000_000L));
    }
}
