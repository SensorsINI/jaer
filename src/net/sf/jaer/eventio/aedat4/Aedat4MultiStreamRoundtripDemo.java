package net.sf.jaer.eventio.aedat4;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.RecordingChipDetector;
import net.sf.jaer.eventio.RecordingConfigurationSnapshot;
import net.sf.jaer.eventio.RecordingFilename;
import net.sf.jaer.eventio.aedat4.dv.CompressionType;

/**
 * Muxed two-camera AEDAT-4 write/read, IOHeader size, and filename sanitizer.
 *
 * {@code java -cp build/classes:lib/*:jars/* net.sf.jaer.eventio.aedat4.Aedat4MultiStreamRoundtripDemo}
 */
public class Aedat4MultiStreamRoundtripDemo {

    public static void main(String[] args) throws Exception {
        testMuxedWriteRead();
        testHeaderSizeTwoCameras();
        testRecordingFilename();
        testChipResolveMuxSource();
        System.out.println("ALL PASS");
    }

    private static void testMuxedWriteRead() throws Exception {
        AEChip chip0 = bareChip(8, 8);
        AEChip chip1 = bareChip(16, 16);
        RecordingConfigurationSnapshot snap0 = RecordingConfigurationSnapshot.captureFromChip(chip0);
        RecordingConfigurationSnapshot snap1 = RecordingConfigurationSnapshot.captureFromChip(chip1);
        List<Aedat4CameraTrack> tracks = new ArrayList<>();
        tracks.add(new Aedat4CameraTrack(chip0, "Davis346-SN0001", snap0, 0));
        tracks.add(new Aedat4CameraTrack(chip1, "DVXplorer-DXA0002", snap1, 1));
        File f = File.createTempFile("jaer-aedat4-mux", ".aedat4");
        try (Aedat4FileOutputStream os = new Aedat4FileOutputStream(
                new FileOutputStream(f), tracks, CompressionType.LZ4, 1_700_000_000_000_000L)) {
            os.writeBundle(bundle(3, 1000), false, 0);
            os.writeBundle(bundle(5, 2000), false, 1);
        }
        String info = RecordingChipDetector.peekAedat4InfoNodeXml(f);
        List<RecordingChipDetector.StreamHint> streams = RecordingChipDetector.streamsFromInfoNodeXml(info);
        assertTrue(streams.size() == 6, "muxed infoNode has 6 streams, got " + streams.size());
        List<RecordingChipDetector.StreamHint> evts = RecordingChipDetector.listAedat4EventStreams(f);
        assertTrue(evts.size() == 2, "two EVTS streams, got " + evts.size());
        assertTrue(evts.get(0).streamId == 0 && evts.get(1).streamId == 3, "EVTS ids 0 and 3");

        Aedat4FileInputStream in0 = new Aedat4FileInputStream(f, chip0, null, 0);
        try {
            assertTrue(in0.getEventStreamId() == 0, "stream 0 selected");
            assertTrue(in0.size() == 3, "stream 0 events=" + in0.size());
        } finally {
            in0.close();
        }
        Aedat4FileInputStream in1 = new Aedat4FileInputStream(f, chip1, null, 3);
        try {
            assertTrue(in1.getEventStreamId() == 3, "stream 3 selected");
            assertTrue(in1.size() == 5, "stream 3 events=" + in1.size());
        } finally {
            in1.close();
        }
        Files.deleteIfExists(f.toPath());
        System.out.println("PASS testMuxedWriteRead");
    }

    private static void testHeaderSizeTwoCameras() throws Exception {
        AEChip chip0 = bareChip(4, 4);
        AEChip chip1 = bareChip(6, 6);
        List<Aedat4CameraTrack> tracks = new ArrayList<>();
        tracks.add(Aedat4CameraTrack.fromChip(chip0, RecordingConfigurationSnapshot.captureFromChip(chip0), 0));
        tracks.add(Aedat4CameraTrack.fromChip(chip1, RecordingConfigurationSnapshot.captureFromChip(chip1), 1));
        File f = File.createTempFile("jaer-aedat4-mux-hdr", ".aedat4");
        try (Aedat4FileOutputStream os = new Aedat4FileOutputStream(
                new FileOutputStream(f), tracks, CompressionType.LZ4, System.currentTimeMillis() * 1000L)) {
            os.writeBundle(bundle(2, 10), false, 0);
            os.writeBundle(bundle(2, 20), false, 1);
        }
        String info = RecordingChipDetector.peekAedat4InfoNodeXml(f);
        int expected = Aedat4InfoNode.build(tracks, CompressionType.LZ4).length();
        assertTrue(info != null && info.length() == expected,
                "muxed infoNode close length matches open build, expected " + expected
                        + " now " + (info == null ? "null" : info.length()));
        Files.deleteIfExists(f.toPath());
        System.out.println("PASS testHeaderSizeTwoCameras len=" + expected);
    }

    private static void testRecordingFilename() {
        String bad = RecordingFilename.sanitizeSegment("Davis<>:\"/\\|?* 346 ");
        assertTrue(!bad.contains("<") && !bad.contains(":") && !bad.contains("/"),
                "illegal chars stripped, got " + bad);
        String serial = RecordingFilename.shortSerial("xx--000012345678");
        assertTrue(serial.length() <= RecordingFilename.MAX_SERIAL_ALNUM, "serial length " + serial);
        List<RecordingFilename.DeviceToken> many = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            many.add(new RecordingFilename.DeviceToken("VeryLongChipNameHere", "SERIAL" + i));
        }
        String base = RecordingFilename.muxedAedat4Base(many, new Date(0L));
        assertTrue(base.startsWith("Multidevice (12) "), "3+ cameras use Multidevice (N), got " + base);
        assertTrue(!base.contains("plus"), "no plusN fallback, got " + base);
        assertTrue(base.contains("VeryL"), "abbrev from chip name, got " + base);
        assertTrue(RecordingFilename.deviceAbbrev("NRVS5KRC1S").equals("NRV"),
                "NRV abbrev");
        assertTrue(RecordingFilename.deviceAbbrev("PropheseeIMX636HD").equals("Proph"),
                "Proph abbrev");
        assertTrue(RecordingFilename.deviceAbbrev("DVXplorerMicro").equals("DVXm"),
                "DVXm abbrev");
        assertTrue(RecordingFilename.deviceAbbrev("DVS128").length() <= RecordingFilename.MAX_DEVICE_ABBREV,
                "DVS128 abbrev length");
        List<RecordingFilename.DeviceToken> five = new ArrayList<>();
        five.add(new RecordingFilename.DeviceToken("NRVS5KRC1S", ""));
        five.add(new RecordingFilename.DeviceToken("DVXplorerMicro", "s5addr15"));
        five.add(new RecordingFilename.DeviceToken("PropheseeIMX636HD", ""));
        five.add(new RecordingFilename.DeviceToken("DVS128", "0633"));
        five.add(new RecordingFilename.DeviceToken("DAVIS240C", ""));
        String fiveBase = RecordingFilename.muxedAedat4Base(five, new Date(0L));
        assertTrue(fiveBase.startsWith("Multidevice (5) NRV DVXm Proph DVS12 Davis-"),
                "5-cam pattern, got " + fiveBase);
        List<RecordingFilename.DeviceToken> two = new ArrayList<>();
        two.add(new RecordingFilename.DeviceToken("Davis346B", "SN0001"));
        two.add(new RecordingFilename.DeviceToken("DVXplorer", "DXA0002"));
        String twoBase = RecordingFilename.muxedAedat4Base(two, new Date(0L));
        assertTrue(twoBase.startsWith("Davis346B-SN0001_DVXplorer-DXA0002_"),
                "2 cameras keep full tokens, got " + twoBase);
        String emptySerial = RecordingFilename.cameraToken("Davis346B", "");
        assertTrue("Davis346B".equals(emptySerial), "empty serial omits hyphen, got " + emptySerial);
        System.out.println("PASS testRecordingFilename " + fiveBase);
    }

    private static void testChipResolveMuxSource() {
        List<Class<? extends AEChip>> loaded = new ArrayList<>();
        loaded.add(ch.unizh.ini.jaer.chip.retina.DVXplorer.class);
        loaded.add(ch.unizh.ini.jaer.chip.retina.DVXplorerMicro.class);
        loaded.add(ch.unizh.ini.jaer.chip.retina.DVS128.class);
        Class<? extends AEChip> micro = RecordingChipDetector.resolve(
                new RecordingChipDetector.Hint("DVXplorerMicro-s5addr15", 640, 480, "test"), loaded);
        assertTrue(micro == ch.unizh.ini.jaer.chip.retina.DVXplorerMicro.class,
                "mux source DVXplorerMicro-s5addr15 -> DVXplorerMicro, got " + micro);
        Class<? extends AEChip> dvx = RecordingChipDetector.resolve(
                new RecordingChipDetector.Hint("DVXplorer-us4addr1", 640, 480, "test"), loaded);
        assertTrue(dvx == ch.unizh.ini.jaer.chip.retina.DVXplorer.class,
                "mux source DVXplorer-us4addr1 -> DVXplorer, got " + dvx);
        Class<? extends AEChip> dvs = RecordingChipDetector.resolve(
                new RecordingChipDetector.Hint("DVS128-0633", 128, 128, "test"), loaded);
        assertTrue(dvs == ch.unizh.ini.jaer.chip.retina.DVS128.class,
                "mux source DVS128-0633 -> DVS128, got " + dvs);
        System.out.println("PASS testChipResolveMuxSource");
    }

    private static PacketBundle bundle(int n, int t0) {
        PacketBundle bundle = new PacketBundle();
        net.sf.jaer.event.EventPacket<PolarityEvent> events = new net.sf.jaer.event.EventPacket<>(PolarityEvent.class);
        OutputEventIterator<PolarityEvent> out = events.outputIterator();
        for (int i = 0; i < n; i++) {
            PolarityEvent e = out.nextOutput();
            e.timestamp = t0 + i;
            e.x = (short) i;
            e.y = (short) (i + 1);
            e.setPolarity((i & 1) == 0 ? PolarityEvent.Polarity.On : PolarityEvent.Polarity.Off);
        }
        bundle.add(events);
        return bundle;
    }

    private static AEChip bareChip(int sx, int sy) throws Exception {
        AEChip chip = new org.objenesis.ObjenesisStd().newInstance(AEChip.class);
        java.lang.reflect.Field x = net.sf.jaer.chip.Chip2D.class.getDeclaredField("sizeX");
        java.lang.reflect.Field y = net.sf.jaer.chip.Chip2D.class.getDeclaredField("sizeY");
        x.setAccessible(true);
        y.setAccessible(true);
        x.setInt(chip, sx);
        y.setInt(chip, sy);
        return chip;
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }
}
