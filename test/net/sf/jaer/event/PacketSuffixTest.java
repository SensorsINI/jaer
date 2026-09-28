package net.sf.jaer.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import net.sf.jaer.eventprocessing.AcquisitionCycle;
import net.sf.jaer.eventprocessing.AcquisitionRecordQueue;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.AEViewer;

/**
 * Acquisition-cycle suffix: each pooled event is sliced once, record copies do
 * not alias the pool, and a full record queue drops without blocking.
 */
public class PacketSuffixTest {

    private static void append(EventPacket<BasicEvent> packet, int timestamp) {
        // getOutputIterator does not reset, so a second append does not overwrite the first event.
        OutputEventIterator<BasicEvent> out = packet.getOutputIterator();
        BasicEvent e = out.nextOutput();
        e.timestamp = timestamp;
        e.x = 1;
        e.y = 2;
        e.setFilteredOut(false);
    }

    private static List<Integer> timestamps(PacketBundle bundle) {
        List<Integer> ts = new ArrayList<>();
        for (TypedDataPacket packet : bundle.snapshot()) {
            if (packet instanceof EventPacket<?> events) {
                for (Object o : events) {
                    ts.add(((BasicEvent) o).timestamp);
                }
            }
        }
        return ts;
    }

    @Test
    public void eachEventIsSlicedOnceAcrossTwoGrowthSteps() {
        EventPacket<BasicEvent> pool = new EventPacket<>(BasicEvent.class);
        PacketBundle bundle = new PacketBundle();
        bundle.addAllowEmpty(pool);
        PacketSuffix.Mark mark = new PacketSuffix.Mark();
        mark.capture(bundle);

        append(pool, 10);
        append(pool, 20);
        PacketBundle first = PacketSuffix.slice(bundle, mark);
        List<Integer> firstTs = timestamps(first);
        assertEquals(List.of(10, 20), firstTs);

        BasicEvent sliced = (BasicEvent) ((EventPacket<?>) first.get(0)).getEvent(0);
        sliced.setFilteredOut(true);
        assertSame(pool.getEvent(0), sliced);
        assertTrue(pool.getEvent(0).isFilteredOut());

        mark.capture(bundle);
        append(pool, 30);
        PacketBundle second = PacketSuffix.slice(bundle, mark);
        assertEquals(List.of(30), timestamps(second));
        assertFalse(((BasicEvent) ((EventPacket<?>) second.get(0)).getEvent(0)).isFilteredOut());

        List<Integer> once = new ArrayList<>(firstTs);
        once.addAll(timestamps(second));
        assertEquals(List.of(10, 20, 30), once);
    }

    @Test
    public void deepCopyKeepsFilteredOutAndSurvivesALaterUrb() {
        EventPacket<BasicEvent> pool = new EventPacket<>(BasicEvent.class);
        append(pool, 10);
        pool.getEvent(0).setFilteredOut(true);
        EventPacket<BasicEvent> copy = pool.deepCopy();
        assertTrue(copy.getEvent(0).isFilteredOut());
        assertTrue(pool.getEvent(0).isFilteredOut());

        pool.getEvent(0).timestamp = 999;
        pool.getEvent(0).setFilteredOut(false);
        append(pool, 20);
        assertEquals(10, copy.getEvent(0).timestamp);
        assertTrue(copy.getEvent(0).isFilteredOut());
        assertEquals(1, copy.getSize());
    }

    @Test
    public void fullQueueDropsAndReturns() throws Exception {
        CountDownLatch inWrite = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch wrote = new CountDownLatch(1);
        AtomicInteger writes = new AtomicInteger();
        AcquisitionRecordQueue queue = new AcquisitionRecordQueue(1, bundle -> {
            writes.incrementAndGet();
            inWrite.countDown();
            release.await(2, TimeUnit.SECONDS);
            wrote.countDown();
        });
        try {
            queue.resume();
            queue.offer(oneEventBundle(1));
            assertTrue(inWrite.await(2, TimeUnit.SECONDS));
            long t0 = System.nanoTime();
            queue.offer(oneEventBundle(2));
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
            assertEquals(1, queue.getDrops());
            assertTrue("offer blocked for " + elapsedMs + " ms", elapsedMs < 200);
            release.countDown();
            assertTrue(wrote.await(2, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            queue.shutdown();
        }
        assertEquals(1, writes.get());
    }

    @Test
    public void renderingModeDoesNotOwnTheUsbThread() {
        assertFalse(AcquisitionCycle.acquiresOnUsb(FilterChain.ProcessingMode.RENDERING, AEViewer.PlayMode.LIVE));
        assertTrue(AcquisitionCycle.viewLoopFilters(FilterChain.ProcessingMode.RENDERING, AEViewer.PlayMode.LIVE));
        assertTrue(AcquisitionCycle.viewLoopRecords(FilterChain.ProcessingMode.RENDERING, AEViewer.PlayMode.LIVE));
        assertTrue(AcquisitionCycle.filtersBlockRenderSkip(FilterChain.ProcessingMode.RENDERING,
                AEViewer.PlayMode.LIVE, true, false));
    }

    @Test
    public void liveAcquisitionSkipsViewLoopFilterRecordAndRenderBlock() {
        assertTrue(AcquisitionCycle.acquiresOnUsb(FilterChain.ProcessingMode.ACQUISITION, AEViewer.PlayMode.LIVE));
        assertFalse(AcquisitionCycle.viewLoopFilters(FilterChain.ProcessingMode.ACQUISITION, AEViewer.PlayMode.LIVE));
        assertFalse(AcquisitionCycle.viewLoopRecords(FilterChain.ProcessingMode.ACQUISITION, AEViewer.PlayMode.LIVE));
        assertFalse(AcquisitionCycle.filtersBlockRenderSkip(FilterChain.ProcessingMode.ACQUISITION,
                AEViewer.PlayMode.LIVE, true, true));
        assertTrue(AcquisitionCycle.viewLoopFilters(FilterChain.ProcessingMode.ACQUISITION, AEViewer.PlayMode.PLAYBACK));
        assertTrue(AcquisitionCycle.viewLoopRecords(FilterChain.ProcessingMode.ACQUISITION, AEViewer.PlayMode.PLAYBACK));
    }

    @Test
    public void finishWithNoChipIsANoOp() {
        AcquisitionCycle cycle = new AcquisitionCycle();
        cycle.markRaw(null, null);
        cycle.markTyped(null, null, false);
        cycle.finish(null, null);
    }

    private static PacketBundle oneEventBundle(int timestamp) {
        EventPacket<BasicEvent> packet = new EventPacket<>(BasicEvent.class);
        append(packet, timestamp);
        PacketBundle bundle = new PacketBundle();
        bundle.add(packet);
        return bundle;
    }
}
