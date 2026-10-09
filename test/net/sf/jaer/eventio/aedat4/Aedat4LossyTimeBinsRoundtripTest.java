package net.sf.jaer.eventio.aedat4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;

import org.junit.Test;

import eu.seebetter.ini.chips.davis.imu.IMUSample;
import ch.unizh.ini.jaer.chip.retina.DVS128;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.FramePacket;
import net.sf.jaer.event.ImuPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.aedat4.dv.CompressionType;

/**
 * Lossy time bins: counts expand back to jAER events at the quantized timestamp,
 * in first-seen order. Frames and IMU stay exact. Lossy off still writes DV events.
 */
public class Aedat4LossyTimeBinsRoundtripTest {

    private static final long BASE_US = 1_700_000_000_000_000L;

    @Test
    public void codecKeepsFirstSeenOrderAndSplitsHugeCounts() throws Exception {
        Aedat4LossyTimeBins.Accumulator acc = new Aedat4LossyTimeBins.Accumulator(10);
        acc.add(100, 1, 2, true);
        acc.add(200, 3, 4, false);
        acc.add(300, 1, 2, true);
        acc.add(400, 3, 4, true);
        acc.add(1500, 9, 9, true);
        Aedat4LossyTimeBins.Encoded first = acc.pollCompleted();
        Aedat4LossyTimeBins.View view = Aedat4LossyTimeBins.decode(
                java.nio.ByteBuffer.wrap(first.payload));
        assertEquals(4, view.expandedLength());
        assertEquals(0L, view.timestamp(0));
        assertEquals(1, view.x(0));
        assertEquals(2, view.y(0));
        assertEquals(1, view.type(0));
        assertEquals(1, view.x(1));
        assertEquals(0, view.type(2));
        assertEquals(3, view.x(2));
        assertEquals(1, view.type(3));

        Aedat4LossyTimeBins.Accumulator huge = new Aedat4LossyTimeBins.Accumulator(10);
        for (int i = 0; i < Aedat4LossyTimeBins.COUNT_MAX + 1; i++) {
            huge.add(50, 7, 8, false);
        }
        Aedat4LossyTimeBins.View split = Aedat4LossyTimeBins.decode(
                java.nio.ByteBuffer.wrap(huge.finish().payload));
        assertEquals(Aedat4LossyTimeBins.COUNT_MAX + 1, split.expandedLength());
        assertEquals(0, split.type(0));
        assertEquals(0, split.type(Aedat4LossyTimeBins.COUNT_MAX));
        assertEquals(7, split.x(Aedat4LossyTimeBins.COUNT_MAX));
    }

    @Test
    public void lz4AndZstdRoundTripExpandedEvents() throws Exception {
        roundTrip(CompressionType.LZ4);
        roundTrip(CompressionType.ZSTD);
    }

    @Test
    public void fileInfoPrintsExactLongEventCount() throws Exception {
        File file = File.createTempFile("jaer-file-info", ".aedat4");
        DVS128 chip = new DVS128();
        try {
            try (Aedat4FileOutputStream out = new Aedat4FileOutputStream(
                    file, chip, CompressionType.LZ4, BASE_US)) {
                out.writeBundle(sampleEvents());
            }
            Aedat4FileInputStream in = new Aedat4FileInputStream(file, chip);
            try {
                assertEquals(5L, in.getIndexedEventCount());
                String info = in.getFileInfo();
                assertTrue(info.contains("(5) events"));
                assertTrue(info.contains("(0) frames"));
                assertTrue(info.contains("(0) IMU samples"));
                assertEquals(info, in.getFileInfo(null));
            } finally {
                in.close();
            }
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }

    @Test
    public void lossyOffKeepsMicrosecondTimestamps() throws Exception {
        File file = File.createTempFile("jaer-lossy-off", ".aedat4");
        DVS128 chip = new DVS128();
        try {
            try (Aedat4FileOutputStream out = new Aedat4FileOutputStream(
                    file, chip, CompressionType.LZ4, BASE_US)) {
                out.writeBundle(sampleEvents());
            }
            Aedat4FileInputStream in = new Aedat4FileInputStream(file, chip);
            try {
                assertFalse(in.isLossyTimeBins());
                AEPacketRaw raw = in.readPacketByNumber(10);
                assertEquals(5, raw.getNumEvents());
                int[] ts = raw.getTimestamps();
                assertTrue(ts[1] != ts[0]);
                assertEquals(1400, ts[4] - ts[0]);
            } finally {
                in.close();
            }
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }

    private static void roundTrip(int compression) throws Exception {
        File file = File.createTempFile("jaer-lossy-" + compression, ".aedat4");
        DVS128 chip = new DVS128();
        try {
            try (Aedat4FileOutputStream out = new Aedat4FileOutputStream(
                    file, chip, compression, BASE_US, null, 10)) {
                PacketBundle bundle = sampleEvents();
                FramePacket frame = new FramePacket(2, 2, FramePacket.ColorMode.GRAYSCALE);
                frame.setTimestampStartUs(2000);
                frame.setTimestampEndUs(2100);
                frame.setExposureUs(100);
                bundle.add(frame);
                ImuPacket imu = new ImuPacket();
                imu.appendCopy(IMUSample.fromRawUntracked(1800, new short[7]));
                bundle.add(imu);
                out.writeBundle(bundle);
                out.writeBundle(moreOfSameBin());
            }
            Aedat4FileInputStream in = new Aedat4FileInputStream(file, chip);
            try {
                assertEquals(10, in.getLossyTimeShift());
                assertEquals(6, in.getIndexedEventCount());
                assertEquals(1, in.getFrameCount());
                assertEquals(1, in.getImuSampleCount());
                AEPacketRaw raw = in.readPacketByNumber(10);
                assertEquals(6, raw.getNumEvents());
                int[] ts = raw.getTimestamps();
                int[] addr = raw.getAddresses();
                assertEquals(0, ts[0]);
                assertEquals(0, ts[1]);
                assertEquals(0, ts[2]);
                assertEquals(0, ts[3]);
                assertEquals(1024, ts[4]);
                assertEquals(0, ts[5]);
                assertEquals(addr[0], addr[1]);
                assertEquals(addr[0], addr[5]);
                assertTrue(addr[2] != addr[0]);
                assertTrue(addr[3] != addr[2]);
            } finally {
                in.close();
            }
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }

    @Test
    public void collapsedPolaritiesPackOnAndOffAndSpillPast255() throws Exception {
        Aedat4LossyTimeBins.Accumulator acc = new Aedat4LossyTimeBins.Accumulator(10, true);
        acc.add(100, 1, 2, true);
        acc.add(200, 1, 2, false);
        acc.add(300, 1, 2, true);
        acc.add(400, 1, 2, false);
        acc.add(500, 1, 2, true);
        acc.add(600, 3, 4, false);
        acc.add(1500, 9, 9, true);
        Aedat4LossyTimeBins.View view = Aedat4LossyTimeBins.decode(
                ByteBuffer.wrap(acc.pollCompleted().payload));
        assertEquals(6, view.expandedLength());
        assertEquals(1, view.type(0));
        assertEquals(1, view.type(2));
        assertEquals(0, view.type(3));
        assertEquals(0, view.type(4));
        assertEquals(1, view.x(0));
        assertEquals(2, view.y(0));
        assertEquals(3, view.x(5));
        assertEquals(0, view.type(5));
        assertEquals(0, view.timestamp(0));

        Aedat4LossyTimeBins.Accumulator hot = new Aedat4LossyTimeBins.Accumulator(10, true);
        for (int i = 0; i < 256; i++) {
            hot.add(i, 7, 8, true);
        }
        hot.add(2000, 1, 1, false);
        Aedat4LossyTimeBins.View spilled = Aedat4LossyTimeBins.decode(
                ByteBuffer.wrap(hot.pollCompleted().payload));
        assertEquals(256, spilled.expandedLength());
        assertEquals(1, spilled.type(0));
        assertEquals(1, spilled.type(255));
        assertEquals(7, spilled.x(255));
    }

    @Test
    public void collapsedFilePlaysOnThenOff() throws Exception {
        File file = File.createTempFile("jaer-lossy-pairs", ".aedat4");
        DVS128 chip = new DVS128();
        try {
            try (Aedat4FileOutputStream out = new Aedat4FileOutputStream(
                    file, chip, CompressionType.LZ4, BASE_US, null, 10, true)) {
                PacketBundle bundle = new PacketBundle();
                EventPacket<PolarityEvent> events = new EventPacket<>(PolarityEvent.class);
                OutputEventIterator<PolarityEvent> it = events.outputIterator();
                add(it, 100, 1, 2, PolarityEvent.Polarity.On);
                add(it, 200, 1, 2, PolarityEvent.Polarity.Off);
                add(it, 300, 1, 2, PolarityEvent.Polarity.On);
                add(it, 400, 1, 2, PolarityEvent.Polarity.Off);
                add(it, 500, 1, 2, PolarityEvent.Polarity.On);
                bundle.add(events);
                out.writeBundle(bundle);
            }
            Aedat4FileInputStream in = new Aedat4FileInputStream(file, chip);
            try {
                assertTrue(in.isLossyCollapsePolarities());
                assertEquals(5, in.getIndexedEventCount());
                AEPacketRaw raw = in.readPacketByNumber(10);
                assertEquals(5, raw.getNumEvents());
                int[] addr = raw.getAddresses();
                assertEquals(addr[0], addr[1]);
                assertEquals(addr[0], addr[2]);
                assertEquals(addr[3], addr[4]);
                assertTrue(addr[0] != addr[3]);
            } finally {
                in.close();
            }
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }

    /** Three On at (1,2), one Off at (3,4), one On at (3,4), then one On at (1,2) in the next bin. */
    private static PacketBundle sampleEvents() {
        PacketBundle bundle = new PacketBundle();
        EventPacket<PolarityEvent> events = new EventPacket<>(PolarityEvent.class);
        OutputEventIterator<PolarityEvent> out = events.outputIterator();
        add(out, 100, 1, 2, PolarityEvent.Polarity.On);
        add(out, 200, 3, 4, PolarityEvent.Polarity.Off);
        add(out, 300, 1, 2, PolarityEvent.Polarity.On);
        add(out, 400, 3, 4, PolarityEvent.Polarity.On);
        add(out, 1500, 1, 2, PolarityEvent.Polarity.On);
        bundle.add(events);
        return bundle;
    }

    /** After the 1024 µs bin has closed, a later event at t=500 opens a new bin at 0. */
    private static PacketBundle moreOfSameBin() {
        PacketBundle bundle = new PacketBundle();
        EventPacket<PolarityEvent> events = new EventPacket<>(PolarityEvent.class);
        OutputEventIterator<PolarityEvent> out = events.outputIterator();
        add(out, 500, 1, 2, PolarityEvent.Polarity.On);
        bundle.add(events);
        return bundle;
    }

    private static void add(OutputEventIterator<PolarityEvent> out, int t, int x, int y,
            PolarityEvent.Polarity polarity) {
        PolarityEvent e = out.nextOutput();
        e.timestamp = t;
        e.x = (short) x;
        e.y = (short) y;
        e.setPolarity(polarity);
    }
}
