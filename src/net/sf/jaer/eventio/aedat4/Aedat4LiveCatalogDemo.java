package net.sf.jaer.eventio.aedat4;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PolarityEvent;

/**
 * Headless live-catalog check. After {@code ant compile}:
 * {@code java -cp "build/classes;lib/*;jars/*" net.sf.jaer.eventio.aedat4.Aedat4LiveCatalogDemo}
 */
public final class Aedat4LiveCatalogDemo {

    public static void main(String[] args) throws Exception {
        File file = Files.createTempFile("live-catalog-", ".aedat4").toFile();
        file.deleteOnExit();
        int[] counts = {4, 7, 3};
        AtomicReference<Aedat4FileOutputStream> writerRef = new AtomicReference<>();
        Aedat4FileOutputStream writer = new Aedat4FileOutputStream(file, null,
                net.sf.jaer.eventio.aedat4.dv.CompressionType.NONE, 1_700_000_000_000_000L);
        writerRef.set(writer);
        try {
            Thread reader = new Thread(() -> readUntil(writerRef, file, sum(counts)), "live-catalog-reader");
            reader.start();
            for (int n : counts) {
                writer.writeBundle(bundle(n, n * 1000));
                Thread.sleep(20);
            }
            reader.join(5000);
            assertTrue(!reader.isAlive(), "reader finished");
            List<Aedat4FileOutputStream.LiveCatalogEntry> catalog = writer.copyLiveCatalog();
            assertTrue(catalog.size() >= counts.length, "catalog rows " + catalog.size());
            long events = 0;
            for (Aedat4FileOutputStream.LiveCatalogEntry e : catalog) {
                if (e.streamId % Aedat4CameraTrack.STREAMS_PER_CAMERA == 0) {
                    events += e.numElements;
                    assertTrue(e.byteOffset > 0, "payload offset");
                }
            }
            assertTrue(events == sum(counts), "catalog events " + events);
            Aedat4FileInputStream tail = Aedat4FileInputStream.openLiveTail(
                    file, null, catalog, writer.getBaseUnixUs());
            try {
                tail.refreshLiveCatalog();
                assertTrue(tail.getHeaderDataTablePosition() < 0,
                        "dataTablePosition " + tail.getHeaderDataTablePosition());
                assertTrue(tail.getIndexedEventCount() == events, "reader events " + tail.getIndexedEventCount());
                assertTrue(tail.eventPayloadOffset(0) == catalog.get(0).byteOffset, "first offset");
            } finally {
                tail.close();
            }
        } finally {
            writer.close();
        }
        Aedat4FileInputStream closed = new Aedat4FileInputStream(file, null);
        try {
            assertTrue(closed.getHeaderDataTablePosition() >= 0,
                    "closed dataTablePosition " + closed.getHeaderDataTablePosition());
        } finally {
            closed.close();
        }
        System.out.println("ALL PASS");
    }

    private static void readUntil(AtomicReference<Aedat4FileOutputStream> writerRef, File file, int expect) { try {
        long deadline = System.currentTimeMillis() + 4000;
        while (System.currentTimeMillis() < deadline) {
            Aedat4FileOutputStream writer = writerRef.get();
            if (writer == null) {
                continue;
            }
            List<Aedat4FileOutputStream.LiveCatalogEntry> catalog = writer.copyLiveCatalog();
            long n = 0;
            for (Aedat4FileOutputStream.LiveCatalogEntry e : catalog) {
                if (e.streamId % Aedat4CameraTrack.STREAMS_PER_CAMERA == 0) {
                    n += e.numElements;
                }
            }
            if (n == expect && !catalog.isEmpty()) {
                Aedat4FileInputStream in = Aedat4FileInputStream.openLiveTail(
                        file, null, catalog, writer.getBaseUnixUs());
                try {
                    assertTrue(in.getHeaderDataTablePosition() < 0, "open writer still pending FTAB");
                    assertTrue(in.getIndexedEventCount() == expect, "tail count " + in.getIndexedEventCount());
                    assertTrue(in.eventPayloadOffset(0) == catalog.get(0).byteOffset, "tail offset");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    try {
                        in.close();
                    } catch (Exception closeFailure) {
                        throw new RuntimeException(closeFailure);
                    }
                }
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new AssertionError("reader timed out waiting for " + expect + " events");
        } catch (Exception e) {
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(e);
        }
    }

    private static PacketBundle bundle(int n, int firstTs) {
        PacketBundle bundle = new PacketBundle();
        EventPacket<PolarityEvent> events = new EventPacket<>(PolarityEvent.class);
        OutputEventIterator<PolarityEvent> out = events.outputIterator();
        for (int i = 0; i < n; i++) {
            PolarityEvent event = out.nextOutput();
            event.timestamp = firstTs + i;
            event.x = (short) i;
            event.y = (short) 1;
            event.setPolarity((i & 1) == 0 ? PolarityEvent.Polarity.On : PolarityEvent.Polarity.Off);
        }
        bundle.add(events);
        return bundle;
    }

    private static int sum(int[] counts) {
        int n = 0;
        for (int c : counts) {
            n += c;
        }
        return n;
    }

    private static void assertTrue(boolean cond, String message) {
        if (!cond) {
            throw new AssertionError(message);
        }
    }
}
