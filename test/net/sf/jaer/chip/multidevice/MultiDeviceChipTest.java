package net.sf.jaer.chip.multidevice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.RecordingChipDetector;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.MultiDeviceRenderer;

/**
 * Tiled multi-device chip: native coordinates, source tags, stream binding,
 * pose prefs, and the record-side unplace.
 */
public class MultiDeviceChipTest {

    /** 128×128 stand-in until a flybox file names the real camera class. */
    public static class SyntheticDvs128 extends AEChip {
        public SyntheticDvs128() {
            setName("SyntheticDvs128");
            setSizeX(128);
            setSizeY(128);
            setNumCellTypes(2);
            setEventClass(PolarityEvent.class);
        }
    }

    public static class Pair extends MultiDeviceChip {
        public Pair() {
            DeviceSlot a = addSlot("camA", SyntheticDvs128.class, Modality.VISION);
            a.setSourceName("camA");
            DeviceSlot b = addSlot("camB", SyntheticDvs128.class, Modality.VISION);
            b.setSourceName("camB");
            setRelation(new TiledNativeRelation());
        }
    }

    @Before
    public void headlessCanvas() {
        ChipCanvas.beginPreviewHeadless();
    }

    @After
    public void endHeadlessCanvas() {
        ChipCanvas.endPreviewHeadless();
    }

    @Test
    public void addressPacksSourceWithoutTheSpecialBit() {
        int addr = MultiDeviceAddress.pack(10, 20, 1, 3);
        assertEquals(10, MultiDeviceAddress.x(addr));
        assertEquals(20, MultiDeviceAddress.y(addr));
        assertEquals(1, MultiDeviceAddress.type(addr));
        assertEquals(3, MultiDeviceAddress.source(addr));
        assertEquals(0, addr & 0x80000000);
        assertEquals(63, MultiDeviceAddress.source(MultiDeviceAddress.pack(0, 0, 0, 63)));
    }

    @Test
    public void tilesKeepNativeCoordinatesAndTagTheSlot() {
        Pair chip = new Pair();
        assertEquals(256, chip.getSizeX());
        assertEquals(128, chip.getSizeY());
        assertEquals(256, chip.getRenderer().getWidth());
        assertEquals(128, chip.getRenderer().getHeight());
        assertEquals(0, chip.getRelation().tileOriginX(chip, chip.getSlots().get(0)));
        assertEquals(128, chip.getRelation().tileOriginX(chip, chip.getSlots().get(1)));

        chip.getSlots().get(1).setTimestampOffsetUs(5);
        int later = MultiDeviceAddress.pack(10, 20, 1, 0);
        int earlier = MultiDeviceAddress.pack(3, 4, 0, 1);
        AEPacketRaw raw = new AEPacketRaw(new int[] {later, earlier}, new int[] {200, 100});
        EventPacket extracted = chip.getEventExtractor().extractPacket(raw);
        assertEquals(2, extracted.getSize());

        BasicEvent first = (BasicEvent) extracted.getEvent(0);
        BasicEvent second = (BasicEvent) extracted.getEvent(1);
        assertEquals(1, first.source);
        assertEquals(3, first.x);
        assertEquals(4, first.y);
        assertEquals(105, first.timestamp);
        assertEquals(PolarityEvent.Polarity.Off, ((PolarityEvent) first).getPolarity());
        assertEquals(131, MultiDeviceRenderer.canvasX(chip, first));
        assertEquals(0, second.source);
        assertEquals(10, second.x);
        assertEquals(200, second.timestamp);
        assertEquals(PolarityEvent.Polarity.On, ((PolarityEvent) second).getPolarity());
        assertEquals(10, MultiDeviceRenderer.canvasX(chip, second));

        List<EventPacket<PolarityEvent>> nativePackets = chip.nativePolarityBySlot(extracted, false);
        assertEquals(2, nativePackets.size());
        PolarityEvent camA = nativePackets.get(0).getEvent(0);
        PolarityEvent camB = nativePackets.get(1).getEvent(0);
        assertEquals(10, camA.x);
        assertEquals(20, camA.y);
        assertEquals(200, camA.timestamp);
        assertEquals(3, camB.x);
        assertEquals(4, camB.y);
        assertEquals(100, camB.timestamp);
    }

    @Test
    public void swapMovesTheTileNotTheSource() {
        Pair chip = new Pair();
        chip.swapVisionSlots(0, 1);
        DeviceSlot first = chip.visionSlots().get(0);
        assertEquals("camB", first.getId());
        assertEquals(1, first.getSource());
        assertEquals(0, chip.getRelation().tileOriginX(chip, first));
        assertEquals(128, chip.getRelation().tileOriginX(chip, chip.visionSlots().get(1)));
    }

    @Test
    public void bindsStreamsBySourceNameThenOrder() {
        Pair chip = new Pair();
        List<RecordingChipDetector.StreamHint> streams = List.of(
                hint(0, "unrelated"),
                hint(3, "rig-camB"),
                hint(6, "rig-camA"),
                hint(1, "FRME", "rig-camA"));
        assertEquals(2, chip.bindPlaybackStreams(streams));
        assertEquals(6, chip.getSlots().get(0).getBoundStreamId());
        assertEquals(3, chip.getSlots().get(1).getBoundStreamId());
        assertEquals(0, chip.sourceForStream(6));
        assertEquals(1, chip.sourceForStream(3));
        assertTrue(chip.isBoundStream(6));
        assertEquals(-1, chip.sourceForStream(0));

        chip.getSlots().get(0).setSourceName(null);
        chip.getSlots().get(1).setSourceName(null);
        List<RecordingChipDetector.StreamHint> plain = List.of(hint(0, "alpha"), hint(4, "beta"));
        assertEquals(2, chip.bindPlaybackStreams(plain));
        assertEquals(0, chip.getSlots().get(0).getBoundStreamId());
        assertEquals(4, chip.getSlots().get(1).getBoundStreamId());
    }

    @Test
    public void poseAndOffsetRoundTripThroughPrefs() {
        Pair chip = new Pair();
        ViewingPose.SlotPose pose = chip.getPose().poseFor("camA");
        pose.azimuthDeg = 90f;
        pose.elevationDeg = -12.5f;
        pose.baselineMm = 40f;
        chip.getSlots().get(0).setTimestampOffsetUs(250);
        chip.getPose().store(chip.getPrefs(), chip.getSlots());
        try {
            ViewingPose loaded = new ViewingPose();
            loaded.load(chip.getPrefs(), chip.getSlots());
            ViewingPose.SlotPose again = loaded.poseFor("camA");
            assertEquals(90f, again.azimuthDeg, 0.001f);
            assertEquals(-12.5f, again.elevationDeg, 0.001f);
            assertEquals(40f, again.baselineMm, 0.001f);
            Pair reread = new Pair();
            assertEquals(250, reread.getSlots().get(0).getTimestampOffsetUs());
        } finally {
            chip.getPrefs().remove(ViewingPose.prefix("camA") + "azimuthDeg");
            chip.getPrefs().remove(ViewingPose.prefix("camA") + "elevationDeg");
            chip.getPrefs().remove(ViewingPose.prefix("camA") + "baselineMm");
            chip.getPrefs().remove(ViewingPose.offsetKey("camA"));
            chip.getPrefs().remove(ViewingPose.prefix("camB") + "azimuthDeg");
            chip.getPrefs().remove(ViewingPose.prefix("camB") + "elevationDeg");
            chip.getPrefs().remove(ViewingPose.prefix("camB") + "baselineMm");
            chip.getPrefs().remove(ViewingPose.offsetKey("camB"));
        }
    }

    private static RecordingChipDetector.StreamHint hint(int streamId, String source) {
        return hint(streamId, "EVTS", source);
    }

    private static RecordingChipDetector.StreamHint hint(int streamId, String type, String source) {
        return new RecordingChipDetector.StreamHint(streamId, type, source, 128, 128, null, source);
    }
}
