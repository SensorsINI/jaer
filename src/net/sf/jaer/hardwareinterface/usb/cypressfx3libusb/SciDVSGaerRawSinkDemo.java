package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import net.sf.jaer.aemonitor.AEPacketRaw;

/** Fixed legacy-raw vectors for the production decoder and raw sink adapter. */
public final class SciDVSGaerRawSinkDemo {

    private static int assertions;

    private SciDVSGaerRawSinkDemo() {
    }

    public static void main(final String[] args) {
        testGroupGoldenArrays();
        testPersistentYAndGroupBounds();
        testTimestampWrapResetAndExternalAcrossTransfers();
        testApsGoldenArrays();
        testImuGoldenArraysAndInvalidDiscard();
        testMisc11AndOddTruncation();
        testCapacityExpansionAndStickyOverrun();
        testStartingCounterAndCaptureBookkeeping();
        System.out.println("SCIDVS GAER RAW ASSERTIONS=" + assertions);
        System.out.println("SCIDVS GAER RAW VECTORS PASS");
    }

    private static void testGroupGoldenArrays() {
        assertRaw("empty group", decodeOnce(defaultConfig(), 4096,
                0x8064, 0x100A, 0x2000), new int[0], new int[0]);
        assertRaw("one OFF bit", decodeOnce(defaultConfig(), 4096,
                0x8064, 0x100A, 0x2001),
                new int[]{42397696}, new int[]{100});
        assertRaw("mixed bit0+bit4", decodeOnce(defaultConfig(), 4096,
                0x8064, 0x100A, 0x2011),
                new int[]{42397696, 42399744}, new int[]{100, 100});
        assertRaw("all eight", decodeOnce(defaultConfig(), 4096,
                0x8064, 0x100A, 0x20FF),
                new int[]{
                    42397696, 42393600, 42389504, 42385408,
                    42399744, 42395648, 42391552, 42387456
                },
                new int[]{100, 100, 100, 100, 100, 100, 100, 100});
        assertRaw("invertXY", decodeOnce(config(true, 112, 126), 4096,
                0x8064, 0x100A, 0x2001),
                new int[]{465608704}, new int[]{100});
    }

    private static void testPersistentYAndGroupBounds() {
        final SciDVSGaerDecoder decoder = new SciDVSGaerDecoder(defaultConfig());
        final SciDVSGaerRawSink raw = new SciDVSGaerRawSink(() -> 4096, () -> { });
        final AEPacketRaw packet = new AEPacketRaw();
        int eventCounter = transfer(decoder, raw, packet, 0, words(0x8064, 0x100A, 0x2001));
        require(eventCounter == 1, "first persistent-Y transfer count");
        require(packet.lastCaptureIndex == 0 && packet.lastCaptureLength == 1,
                "first persistent-Y capture bounds");
        eventCounter = transfer(decoder, raw, packet, eventCounter, words(0x107E, 0x2002));
        require(eventCounter == 2, "out-of-range Y leaves output cursor advancing");
        require(packet.lastCaptureIndex == 1 && packet.lastCaptureLength == 1,
                "second persistent-Y capture bounds");
        assertRaw("out-of-range Y retains previous",
                packet,
                new int[]{42397696, 42393600}, new int[]{100, 100});

        assertRaw("out-of-range group base", decodeOnce(defaultConfig(), 4096,
                0x100A, 0x3CFF), new int[0], new int[0]);
        assertRaw("missing Y legacy default", decodeOnce(defaultConfig(), 4096,
                0x2001), new int[]{524742656}, new int[]{0});
        assertRaw("code3 high valid group", decodeOnce(defaultConfig(), 4096,
                0x100A, 0x3B01), new int[]{41955328}, new int[]{0});
    }

    private static void testTimestampWrapResetAndExternalAcrossTransfers() {
        final AtomicInteger resets = new AtomicInteger();
        final SciDVSGaerDecoder decoder = new SciDVSGaerDecoder(defaultConfig());
        final SciDVSGaerRawSink raw = new SciDVSGaerRawSink(() -> 4096, resets::incrementAndGet);
        final AEPacketRaw packet = new AEPacketRaw();
        int eventCounter = transfer(decoder, raw, packet, 0,
                words(0x8064, 0x100A, 0x2001, 0x7003, 0x2010));
        require(eventCounter == 2, "first timestamp/wrap transfer count");
        require(packet.lastCaptureIndex == 0 && packet.lastCaptureLength == 2,
                "first timestamp/wrap capture bounds");
        eventCounter = transfer(decoder, raw, packet, eventCounter,
                words(0x8005, 0x2002, 0x0001, 0x0002, 0x0003, 0x0004));
        require(eventCounter == 6, "second timestamp/reset transfer count");
        require(packet.lastCaptureIndex == 2 && packet.lastCaptureLength == 4,
                "second timestamp/reset capture bounds");
        require(resets.get() == 1, "raw sink forwards one timestamp reset notification");
        assertRaw("timestamp wrap reset external",
                packet,
                new int[]{42397696, 42399744, 42393600, 1026, 1027, 1028},
                new int[]{100, 98304, 98309, 0, 0, 0});
    }

    private static void testApsGoldenArrays() {
        final SciDVSGaerDecoder.Config config = new SciDVSGaerDecoder.Config(
                SciDVSHardwareInterface.CHIP_DAVIS240C,
                112, 126, false,
                2, 2, false, false, false,
                false, false, false);
        final AEPacketRaw packet = decodeOnce(config, 4096,
                0x8032, 0x0008,
                0x000B, 0x4001, 0x4002, 0x000D,
                0x000B, 0x4003, 0x4004, 0x000D,
                0x000C, 0x4005, 0x4006, 0x000D,
                0x000C, 0x4007, 0x4008, 0x000D,
                0x000E, 0x000F, 0x000A);
        assertRaw("APS reset/signal/frame/exposure",
                packet,
                new int[]{
                    -2143289343, -2147483646, -2143285245, -2147479548,
                    -2143288315, -2147482618, -2143284217, -2147478520
                },
                new int[]{50, 50, 50, 50, 50, 50, 50, 50});

        final SciDVSGaerDecoder.Config transformed = new SciDVSGaerDecoder.Config(
                SciDVSHardwareInterface.CHIP_DAVIS240C,
                112, 126, false,
                2, 3, true, true, true,
                false, false, false);
        assertRaw("APS transformed", decodeOnce(transformed, 4096,
                0x0008, 0x000B, 0x4009),
                new int[]{-2147475447}, new int[]{0});
    }

    private static void testImuGoldenArraysAndInvalidDiscard() {
        final int[] valid = {
            0x804D, 0x0005, 0x53E0,
            0x5001, 0x5002, 0x5003, 0x5004, 0x5005, 0x5006, 0x5007,
            0x5008, 0x5009, 0x500A, 0x500B, 0x500C, 0x500D, 0x500E,
            0x0007
        };
        assertRaw("valid IMU",
                decodeOnce(defaultConfig(), 4096, valid),
                new int[]{
                    -2146423808, -1875883008, -1605342208, -1334801408,
                    -1064260608, -793719808, -523179008
                },
                new int[]{77, 77, 77, 77, 77, 77, 77});

        final int[] invalid = {
            0x804D, 0x0005, 0x53E0,
            0x5001, 0x5002, 0x5003, 0x5004, 0x5005, 0x5006, 0x5007,
            0x5008, 0x5009, 0x500A, 0x500B, 0x500C, 0x500D,
            0x0007
        };
        assertRaw("invalid IMU count discarded",
                decodeOnce(defaultConfig(), 4096, invalid), new int[0], new int[0]);
    }

    private static void testMisc11AndOddTruncation() {
        assertRaw("misc11 previous-address OR",
                decodeOnce(defaultConfig(), 4096,
                        0x6805, 0x8064, 0x100A, 0x2001, 0x682A),
                new int[]{42397738}, new int[]{100});

        final ByteBuffer odd = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN);
        odd.putShort((short) 0x8009);
        odd.putShort((short) 0x0002);
        odd.put((byte) 0x55);
        odd.flip();
        final SciDVSGaerDecoder decoder = new SciDVSGaerDecoder(defaultConfig());
        final SciDVSGaerRawSink raw = new SciDVSGaerRawSink(() -> 4096, () -> { });
        final AEPacketRaw packet = new AEPacketRaw();
        final int eventCounter = transfer(decoder, raw, packet, 0, odd);
        require(eventCounter == 1, "odd input emits only one complete raw event");
        assertRaw("odd input", packet, new int[]{1026}, new int[]{9});
        require(odd.limit() == 4 && odd.position() == 0,
                "odd input preserves legacy truncated limit and position");
    }

    private static void testCapacityExpansionAndStickyOverrun() {
        final AEPacketRaw expanded = new AEPacketRaw(1);
        final SciDVSGaerDecoder expandDecoder = new SciDVSGaerDecoder(defaultConfig());
        final SciDVSGaerRawSink expandRaw = new SciDVSGaerRawSink(() -> 100, () -> { });
        final int expandedCount = transfer(expandDecoder, expandRaw, expanded, 0,
                words(0x8064, 0x100A, 0x20FF));
        require(expandedCount == 8, "capacity expansion writes all eight events");
        require(expanded.getCapacity() >= 8, "capacity expanded to at least eight");
        require(!expanded.overrunOccuredFlag, "capacity expansion does not set overrun");
        assertRaw("capacity expansion", expanded,
                new int[]{
                    42397696, 42393600, 42389504, 42385408,
                    42399744, 42395648, 42391552, 42387456
                },
                new int[]{100, 100, 100, 100, 100, 100, 100, 100});

        final int[] limit = {1};
        final AEPacketRaw overrun = new AEPacketRaw(2);
        final SciDVSGaerDecoder overrunDecoder = new SciDVSGaerDecoder(defaultConfig());
        final SciDVSGaerRawSink overrunRaw = new SciDVSGaerRawSink(() -> limit[0], () -> { });
        int eventCounter = transfer(overrunDecoder, overrunRaw, overrun, 0,
                words(0x8064, 0x100A, 0x20FF));
        require(eventCounter == 2, "overrun freezes event cursor at packet capacity");
        require(overrun.overrunOccuredFlag, "overrun flag latches");
        assertRaw("overrun prefix", overrun,
                new int[]{42397696, 42393600}, new int[]{100, 100});

        eventCounter = transfer(overrunDecoder, overrunRaw, overrun, eventCounter,
                words(0x0002));
        require(eventCounter == 2, "sticky overrun drops later transfer");
        require(overrun.lastCaptureIndex == 2 && overrun.lastCaptureLength == 0,
                "sticky overrun reports empty later capture");

        limit[0] = 100;
        final AEPacketRaw recoveredPacket = new AEPacketRaw();
        final int recovered = transfer(overrunDecoder, overrunRaw, recoveredPacket, 0,
                words(0x7003, 0x2001));
        require(recovered == 1, "new packet accepts output after prior packet overrun");
        assertRaw("parser state advances despite overrun",
                recoveredPacket, new int[]{42397696}, new int[]{98304});
    }

    private static void testStartingCounterAndCaptureBookkeeping() {
        final AEPacketRaw packet = new AEPacketRaw(4);
        packet.getAddresses()[0] = 11;
        packet.getAddresses()[1] = 22;
        packet.getTimestamps()[0] = 1;
        packet.getTimestamps()[1] = 2;
        packet.setNumEvents(2);
        final SciDVSGaerRawSink raw = new SciDVSGaerRawSink(() -> 4096, () -> { });
        final int eventCounter = transfer(new SciDVSGaerDecoder(defaultConfig()), raw,
                packet, 2, words(0x8007, 0x0002));
        require(eventCounter == 3, "raw sink returns adopted start counter plus new event");
        require(packet.getNumEvents() == 3, "raw sink end sets packet numEvents");
        require(packet.lastCaptureIndex == 2 && packet.lastCaptureLength == 1,
                "raw sink capture index/length use starting counter");
        assertRaw("starting counter preserves prefix", packet,
                new int[]{11, 22, 1026}, new int[]{1, 2, 7});
    }

    private static AEPacketRaw decodeOnce(final SciDVSGaerDecoder.Config config,
            final int capacityLimit, final int... inputWords) {
        final AEPacketRaw packet = new AEPacketRaw();
        final SciDVSGaerRawSink raw = new SciDVSGaerRawSink(() -> capacityLimit, () -> { });
        final int eventCounter = transfer(new SciDVSGaerDecoder(config), raw,
                packet, 0, words(inputWords));
        require(eventCounter == packet.getNumEvents(), "decodeOnce counter equals numEvents");
        require(packet.lastCaptureIndex == 0 && packet.lastCaptureLength == eventCounter,
                "decodeOnce capture bounds");
        return packet;
    }

    private static int transfer(final SciDVSGaerDecoder decoder,
            final SciDVSGaerRawSink raw, final AEPacketRaw packet,
            final int startingEventCounter, final ByteBuffer input) {
        raw.begin(packet, startingEventCounter);
        decoder.decode(input, raw);
        return raw.end();
    }

    private static SciDVSGaerDecoder.Config defaultConfig() {
        return config(false, 112, 126);
    }

    private static SciDVSGaerDecoder.Config config(final boolean dvsInvertXY,
            final int apsSizeX, final int apsSizeY) {
        return new SciDVSGaerDecoder.Config(
                SciDVSHardwareInterface.CHIP_DAVIS240C,
                112, 126, dvsInvertXY,
                apsSizeX, apsSizeY, false, false, false,
                false, false, false);
    }

    private static ByteBuffer words(final int... words) {
        final ByteBuffer buffer = ByteBuffer.allocate(words.length * Short.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (final int word : words) {
            buffer.putShort((short) word);
        }
        return buffer.flip();
    }

    private static void assertRaw(final String name, final AEPacketRaw packet,
            final int[] expectedAddresses, final int[] expectedTimestamps) {
        require(expectedAddresses.length == expectedTimestamps.length,
                name + " oracle arrays have equal length");
        require(packet.getNumEvents() == expectedAddresses.length,
                name + " exact event count expected=" + expectedAddresses.length
                + " actual=" + packet.getNumEvents());
        final int[] actualAddresses = packet.getNumEvents() == 0
                ? new int[0] : Arrays.copyOf(packet.getAddresses(), packet.getNumEvents());
        final int[] actualTimestamps = packet.getNumEvents() == 0
                ? new int[0] : Arrays.copyOf(packet.getTimestamps(), packet.getNumEvents());
        require(Arrays.equals(actualAddresses, expectedAddresses),
                name + " addresses expected=" + Arrays.toString(expectedAddresses)
                + " actual=" + Arrays.toString(actualAddresses));
        require(Arrays.equals(actualTimestamps, expectedTimestamps),
                name + " timestamps expected=" + Arrays.toString(expectedTimestamps)
                + " actual=" + Arrays.toString(actualTimestamps));
    }

    private static void require(final boolean condition, final String description) {
        assertions++;
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
