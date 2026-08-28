package net.sf.jaer.hardwareinterface.usb;

import net.sf.jaer.event.AcquisitionMetadata;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PacketType;

/** Hardware-free regression checks for DVX authoritative polarity accounting. */
public final class DVXplorerTypedDemuxTest {

    private DVXplorerTypedDemuxTest() {
    }

    public static void main(String[] args) {
        testCapacityAndLossResetPerWriteBundle();
        testTimestampResetSplitsEpochPackets();
        testTimestampRolloverSplitsEpochPackets();
        System.out.println("DVXPLORER_TYPED_DEMUX PASS");
    }

    private static void testCapacityAndLossResetPerWriteBundle() {
        final UsbPolarityBundleBuilder builder = new UsbPolarityBundleBuilder();
        final PacketBundle first = new PacketBundle();
        first.beginAcquisition(7, 0);
        builder.beginAuthoritative(first, 2, 0);
        assertTrue(builder.addAuthoritativePolarity(1, 2, true, 10, 0x11));
        assertTrue(builder.addAuthoritativePolarity(2, 3, false, 11, 0x22));
        assertFalse(builder.addAuthoritativePolarity(3, 4, true, 12, 0x33));
        builder.flushAll();
        first.seal();

        assertEquals(2, first.getNumPolarityEvents());
        assertEquals(2L, first.getAcquisitionMetadata().getAcceptedCount(PacketType.POLARITY));
        assertEquals(1L, first.getAcquisitionMetadata().getExactLossCount(PacketType.POLARITY));
        assertEquals(AcquisitionMetadata.LossKind.HOST_CAPACITY,
                first.getAcquisitionMetadata().getLossRecords().iterator().next().getKind());
        assertEquals(0L, first.getFirstPolarityPacket().getTimestampEpoch());

        final PacketBundle second = new PacketBundle();
        second.beginAcquisition(7, 1);
        builder.beginAuthoritative(second, 2, 0);
        assertTrue(builder.addAuthoritativePolarity(4, 5, true, 20, 0x44));
        assertTrue(builder.addAuthoritativePolarity(5, 6, false, 21, 0x55));
        builder.flushAll();
        second.seal();

        assertEquals(2, second.getNumPolarityEvents());
        assertEquals(0L, second.getAcquisitionMetadata().getExactLossCount(PacketType.POLARITY));
    }

    private static void testTimestampResetSplitsEpochPackets() {
        final UsbPolarityBundleBuilder builder = new UsbPolarityBundleBuilder();
        final PacketBundle bundle = new PacketBundle();
        bundle.beginAcquisition(8, 0);
        builder.beginAuthoritative(bundle, 8, 0);
        assertTrue(builder.addAuthoritativePolarity(1, 1, true, 100, 0x01));
        builder.onTimestampReset(1);
        assertTrue(builder.addAuthoritativePolarity(2, 2, false, 1, 0x02));
        builder.flushAll();
        bundle.seal();

        assertEquals(2, bundle.getNumPackets());
        assertEquals(0L, bundle.get(0).getTimestampEpoch());
        assertEquals(1L, bundle.get(1).getTimestampEpoch());
        assertTrue(bundle.getAcquisitionMetadata().getTimestampEpochs().contains(0L));
        assertTrue(bundle.getAcquisitionMetadata().getTimestampEpochs().contains(1L));
    }

    private static void testTimestampRolloverSplitsEpochPackets() {
        final UsbPolarityBundleBuilder builder = new UsbPolarityBundleBuilder();
        final PacketBundle bundle = new PacketBundle();
        bundle.beginAcquisition(9, 0);
        builder.beginAuthoritative(bundle, 8, 4);
        assertTrue(builder.addAuthoritativePolarity(
                1, 1, true, Integer.MAX_VALUE, 0x01));
        builder.onTimestampReset(5);
        assertTrue(builder.addAuthoritativePolarity(2, 2, false, 0, 0x02));
        builder.flushAll();
        bundle.seal();

        assertEquals(2, bundle.getNumPackets());
        assertEquals(4L, bundle.get(0).getTimestampEpoch());
        assertEquals(5L, bundle.get(1).getTimestampEpoch());
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("expected true");
        }
    }

    private static void assertFalse(boolean condition) {
        if (condition) {
            throw new AssertionError("expected false");
        }
    }

    private static void assertEquals(long expected, long actual) {
        if (expected != actual) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }
}
