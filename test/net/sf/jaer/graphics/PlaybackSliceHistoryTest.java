package net.sf.jaer.graphics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import net.sf.jaer.aemonitor.AEPacketRaw;

/**
 * Session-long bookmarks: jog-back moves a cursor and restores state before the
 * target slice (player then re-reads that one slice).
 */
public class PlaybackSliceHistoryTest {

    @Test
    public void rewindJumpsToEarlierBookmarkWithoutDroppingOlderOnes() {
        PlaybackSliceHistory h = new PlaybackSliceHistory();
        h.resetOrigin(0L, 0, null);
        for (int i = 0; i < 5; i++) {
            h.push(leftover(i), 1000 + i, 50_000 + i);
        }
        assertEquals(5, h.size());
        assertEquals(4, h.cursor());

        PlaybackSliceHistory.RewindResult r = h.rewind(2);
        assertEquals(2, r.stepsTaken);
        assertEquals(5, h.size());
        assertEquals(2, r.cursor);
        assertEquals(1001, r.stateBefore.positionAfter);
        assertEquals(50_001, r.stateBefore.currentStartTimestamp);
        assertEquals(101, r.stateBefore.leftover.getAddresses()[0]);
    }

    @Test
    public void rewindToStartRestoresOrigin() {
        PlaybackSliceHistory h = new PlaybackSliceHistory();
        h.resetOrigin(7L, 42, leftover(9));
        h.push(leftover(0), 10, 1);
        h.push(leftover(1), 20, 2);
        h.push(leftover(2), 30, 3);

        PlaybackSliceHistory.RewindResult r = h.rewind(100);
        assertEquals(2, r.stepsTaken);
        assertTrue(r.atOldest);
        assertEquals(0, r.cursor);
        assertEquals(3, h.size());
        assertEquals(7L, r.stateBefore.positionAfter);
        assertEquals(42, r.stateBefore.currentStartTimestamp);
        assertEquals(109, r.stateBefore.leftover.getAddresses()[0]);
    }

    @Test
    public void emptyHistoryCannotRewind() {
        PlaybackSliceHistory h = new PlaybackSliceHistory();
        PlaybackSliceHistory.RewindResult r = h.rewind(5);
        assertEquals(0, r.stepsTaken);
        assertTrue(r.atOldest);
        assertNull(r.stateBefore);
    }

    @Test
    public void singleSliceRewindReplaysOrigin() {
        PlaybackSliceHistory h = new PlaybackSliceHistory();
        h.resetOrigin(5L, 7, null);
        h.push(null, 15, 8);
        PlaybackSliceHistory.RewindResult r = h.rewind(3);
        assertEquals(0, r.stepsTaken);
        assertTrue(r.atOldest);
        assertEquals(5L, r.stateBefore.positionAfter);
        assertEquals(7, r.stateBefore.currentStartTimestamp);
        assertNull(r.stateBefore.leftover);
    }

    @Test
    public void leftoverCopyIsIndependent() {
        PlaybackSliceHistory h = new PlaybackSliceHistory();
        h.resetOrigin(0L, 0, null);
        AEPacketRaw live = leftover(1);
        h.push(live, 1, 1);
        live.getAddresses()[0] = 999;
        assertEquals(101, h.peekNewest().leftover.getAddresses()[0]);
        assertNotSame(live, h.peekNewest().leftover);
    }

    @Test
    public void playForwardAfterRewindDropsFutureBookmarks() {
        PlaybackSliceHistory h = new PlaybackSliceHistory();
        h.resetOrigin(0L, 0, null);
        for (int i = 0; i < 10; i++) {
            h.push(null, i, i);
        }
        h.rewind(4);
        assertEquals(5, h.cursor());
        h.push(null, 100, 100);
        assertEquals(7, h.size());
        assertEquals(6, h.cursor());
        assertEquals(100, h.peekNewest().positionAfter);
    }

    @Test
    public void manySlicesAreKept() {
        PlaybackSliceHistory h = new PlaybackSliceHistory();
        h.resetOrigin(0L, 0, null);
        int n = 2000;
        for (int i = 0; i < n; i++) {
            h.push(null, i, i);
        }
        assertEquals(n, h.size());
        PlaybackSliceHistory.RewindResult r = h.rewind(n);
        assertEquals(n - 1, r.stepsTaken);
        assertTrue(r.atOldest);
        assertEquals(0L, r.stateBefore.positionAfter);
    }

    @Test
    public void findRenderedAgoPicksSliceAtLeastDelayOld() {
        PlaybackSliceHistory h = new PlaybackSliceHistory();
        h.resetOrigin(0L, 0, null);
        long t0 = 1_000_000_000L;
        h.push(null, 10, 1, t0);
        h.push(null, 20, 2, t0 + 100_000_000L);
        h.push(null, 30, 3, t0 + 250_000_000L);
        h.push(null, 40, 4, t0 + 500_000_000L);
        long now = t0 + 500_000_000L;
        PlaybackSliceHistory.Bookmark b = h.findRenderedAgo(400_000_000L, now);
        assertEquals(20, b.positionAfter);
        b = h.findRenderedAgo(50_000_000L, now);
        assertEquals(30, b.positionAfter);
        b = h.findRenderedAgo(10_000_000_000L, now);
        assertEquals(10, b.positionAfter);
    }

    @Test
    public void findRenderedAgoEmptyIsNull() {
        PlaybackSliceHistory h = new PlaybackSliceHistory();
        assertNull(h.findRenderedAgo(400_000_000L, 1L));
    }

    @Test
    public void copyPacketNullAndEmpty() {
        assertNull(PlaybackSliceHistory.copyPacket(null));
        AEPacketRaw empty = PlaybackSliceHistory.copyPacket(new AEPacketRaw(0));
        assertNotNull(empty);
        assertEquals(0, empty.getNumEvents());
    }

    private static AEPacketRaw leftover(int seed) {
        AEPacketRaw p = new AEPacketRaw(1);
        p.getAddresses()[0] = 100 + seed;
        p.getTimestamps()[0] = 200 + seed;
        p.setNumEvents(1);
        return p;
    }
}
