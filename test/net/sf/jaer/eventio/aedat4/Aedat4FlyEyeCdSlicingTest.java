package net.sf.jaer.eventio.aedat4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.List;

import org.junit.Test;

import ch.unizh.ini.jaer.chip.flyeye.FlyEye;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.RecordingConfigurationSnapshot;
import net.sf.jaer.eventio.aedat4.dv.CompressionType;
import net.sf.jaer.stereopsis.Stereopsis;

/**
 * FlyEye native dual EVTS: ConstantDuration must mix both eyes in one timeslice.
 * File order is left packet then right packet with the same Unix window.
 */
public class Aedat4FlyEyeCdSlicingTest {

    private static final int DT_US = 20_000;
    private static final int STEP_US = 50;
    private static final int EVENTS_PER_PACKET = 2000; // 100 ms span
    private static final int PAIRS = 3;

    @Test
    public void constantDurationMixesLeftAndRightEyes() throws Exception {
        File file = File.createTempFile("jaer-flyeye-cd", ".aedat4");
        FlyEye chip = new FlyEye();
        try {
            writeOverlappingPairFile(file, chip);
            Aedat4FileInputStream in = new Aedat4FileInputStream(file, chip);
            try {
                assertEquals(EVENTS_PER_PACKET * PAIRS * 2L, in.size());
                int mixed = 0;
                int onlyL = 0;
                int onlyR = 0;
                long totL = 0;
                long totR = 0;
                int slices = 0;
                try {
                    while (slices < 10_000) {
                        AEPacketRaw pkt = in.readPacketByTime(DT_US);
                        int[] lr = countEyes(pkt);
                        totL += lr[0];
                        totR += lr[1];
                        if (lr[0] > 0 && lr[1] > 0) {
                            mixed++;
                        } else if (lr[0] > 0) {
                            onlyL++;
                        } else if (lr[1] > 0) {
                            onlyR++;
                        }
                        if (slices < 8) {
                            assertTrue("slice " + slices + " should include both eyes, L=" + lr[0] + " R=" + lr[1],
                                    lr[0] > 0 && lr[1] > 0);
                        }
                        slices++;
                    }
                } catch (EOFException done) {
                    // consumed
                }
                assertTrue("expected several CD slices, got " + slices, slices >= 10);
                assertEquals("left events dropped", EVENTS_PER_PACKET * PAIRS, totL);
                assertEquals("right events dropped", EVENTS_PER_PACKET * PAIRS, totR);
                assertEquals("left-only CD slices (file-order bug)", 0, onlyL);
                assertEquals("right-only CD slices", 0, onlyR);
                assertTrue("mixed slices=" + mixed, mixed >= 10);
            } finally {
                in.close();
            }
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }

    private static void writeOverlappingPairFile(File file, FlyEye chip) throws Exception {
        RecordingConfigurationSnapshot snap = RecordingConfigurationSnapshot.captureFromChip(chip);
        List<Aedat4CameraTrack> tracks = chip.aedat4RecordingTracks(snap);
        try (Aedat4FileOutputStream os = new Aedat4FileOutputStream(
                new FileOutputStream(file), tracks, CompressionType.LZ4, 1_700_000_000_000_000L)) {
            for (int p = 0; p < PAIRS; p++) {
                int t0 = p * EVENTS_PER_PACKET * STEP_US;
                os.writeBundle(polarityBundle(t0, 1), false, 0);
                os.writeBundle(polarityBundle(t0, 2), false, 1);
            }
        }
    }

    private static PacketBundle polarityBundle(int t0, int x) {
        PacketBundle bundle = new PacketBundle();
        net.sf.jaer.event.EventPacket<PolarityEvent> events = new net.sf.jaer.event.EventPacket<>(PolarityEvent.class);
        OutputEventIterator<PolarityEvent> out = events.outputIterator();
        for (int i = 0; i < EVENTS_PER_PACKET; i++) {
            PolarityEvent e = out.nextOutput();
            e.timestamp = t0 + i * STEP_US;
            e.x = (short) x;
            e.y = 4;
            e.setPolarity(PolarityEvent.Polarity.On);
        }
        bundle.add(events);
        return bundle;
    }

    private static int[] countEyes(AEPacketRaw pkt) {
        int l = 0;
        int r = 0;
        if (pkt == null) {
            return new int[] {0, 0};
        }
        int n = pkt.getNumEvents();
        int[] addr = pkt.getAddresses();
        for (int i = 0; i < n; i++) {
            if (Stereopsis.isRightRawAddress(addr[i])) {
                r++;
            } else {
                l++;
            }
        }
        return new int[] {l, r};
    }
}
