package net.sf.jaer.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

/**
 * EventPacket for-each must not share a cursor across consumers (GitHub issue 60:
 * SpaceTimeRolling vs AEChipRenderer on the GL / ViewLoop threads).
 */
public class EventPacketIteratorTest {

    private static EventPacket<BasicEvent> filledPacket(int n) {
        EventPacket<BasicEvent> packet = new EventPacket<>(BasicEvent.class);
        packet.allocate(n);
        OutputEventIterator<BasicEvent> out = packet.outputIterator();
        for (int i = 0; i < n; i++) {
            BasicEvent e = out.nextOutput();
            e.timestamp = i;
            e.x = (short) (i % 128);
            e.y = (short) ((i / 128) % 128);
        }
        return packet;
    }

    @Test
    public void twoForEachLoopsDoNotShareCursor() {
        EventPacket<BasicEvent> packet = filledPacket(100_000);
        Iterator<BasicEvent> a = packet.iterator();
        Iterator<BasicEvent> b = packet.iterator();
        assertFalse("iterator() must not return the cached inputIterator instance", a == b);
        int na = 0;
        int nb = 0;
        while (a.hasNext()) {
            a.next();
            na++;
        }
        while (b.hasNext()) {
            b.next();
            nb++;
        }
        assertEquals(100_000, na);
        assertEquals(100_000, nb);
    }

    @Test
    public void concurrentForEachDoesNotThrow() throws InterruptedException {
        final EventPacket<BasicEvent> packet = filledPacket(100_000);
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final CountDownLatch start = new CountDownLatch(1);
        Runnable walk = () -> {
            try {
                start.await();
                for (int round = 0; round < 50; round++) {
                    int n = 0;
                    for (BasicEvent e : packet) {
                        n += e.timestamp;
                    }
                    if (n == Integer.MIN_VALUE) {
                        fail("unreachable");
                    }
                }
            } catch (Throwable t) {
                error.compareAndSet(null, t);
            }
        };
        Thread t1 = new Thread(walk, "iter-a");
        Thread t2 = new Thread(walk, "iter-b");
        t1.start();
        t2.start();
        start.countDown();
        t1.join();
        t2.join();
        if (error.get() != null) {
            fail(error.get().toString());
        }
    }

    @Test
    public void sizeLargerThanBackingArrayDoesNotThrow() {
        EventPacket<BasicEvent> packet = filledPacket(8);
        packet.setSize(packet.getElementData().length + 1);
        int n = 0;
        for (BasicEvent e : packet) {
            n++;
        }
        assertEquals(packet.getElementData().length, n);
    }
}
