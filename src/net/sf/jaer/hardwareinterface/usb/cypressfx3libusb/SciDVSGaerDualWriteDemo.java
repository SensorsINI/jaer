package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import eu.seebetter.ini.chips.davis.imu.IMUSample;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import net.sf.jaer.aemonitor.AEPacketRaw;

/**
 * Frozen headless acceptance demo for the SciDVS {@code SciDVSGaerRawSink} APS/IMU
 * dual-write gate.
 *
 * <p>This demo must <em>fail to compile right now</em> and only because the
 * three-argument constructor
 * {@code SciDVSGaerRawSink(IntSupplier, Runnable, BooleanSupplier)} does not yet
 * exist in the production sink. Everything else it exercises is real,
 * production, deterministic and hardware-free. The moment that constructor is
 * added, this file builds and then verifies the dual-write semantics:
 *
 * <ul>
 * <li>the legacy two-argument constructor preserves the exact legacy APS and
 * seven-event IMU raw writes;</li>
 * <li>a false dual-write supplier suppresses APS and IMU (but never polarity or
 * external) and keeps the capture length exact;</li>
 * <li>a runtime {@link AtomicBoolean} toggle is re-evaluated on <em>every</em>
 * callback, not captured once at construction;</li>
 * <li>suppressed events cannot cause an overrun or mutate packet capacity;</li>
 * <li>a true dual-write supplier writes the exact APS and exactly
 * {@link IMUSample#SIZE_EVENTS} IMU raw events;</li>
 * <li>an address patch issued immediately after a suppressed APS patches the
 * preceding polarity (pinned latent DAViS-equivalent behavior, not
 * redesigned).</li>
 * </ul>
 *
 * <p>No hardware, network, or display is used. Deterministic by construction:
 * golden vectors were captured once from the production decoder
 * and are asserted byte-for-byte.
 */
public final class SciDVSGaerDualWriteDemo {

    private static int assertions;

    private SciDVSGaerDualWriteDemo() {
    }

    public static void main(final String[] args) {
        testLegacyTwoArgPreservesExactAps();
        testLegacyTwoArgPreservesExactImu();
        testDualWriteTrueWritesExactAps();
        testDualWriteTrueWritesExactImuSizeEvents();
        testSuppressFalseDropsApsImuKeepsPolarityExternal();
        testCaptureLengthExactUnderSuppress();
        testToggleEvaluatedPerCallback();
        testSuppressedImuCannotOverrunOrMutateCapacity();
        testAddressPatchAfterSuppressedApsPatchesPrecedingPolarity();
        System.out.println("SCIDVS_GAER_DUAL_WRITE ASSERTIONS=" + assertions);
        System.out.println("SCIDVS_GAER_DUAL_WRITE PASS");
    }

    /** Legacy 2-arg ctor: APS readout writes exactly the same raw words as before. */
    private static void testLegacyTwoArgPreservesExactAps() {
        final AEPacketRaw packet = decodeOnce(new SciDVSGaerRawSink(() -> 4096, () -> { }),
                apsTwoByTwoConfig(), 4096,
                0x8032, 0x0008,
                0x000B, 0x4001, 0x4002, 0x000D,
                0x000B, 0x4003, 0x4004, 0x000D,
                0x000C, 0x4005, 0x4006, 0x000D,
                0x000C, 0x4007, 0x4008, 0x000D,
                0x000E, 0x000F, 0x000A);
        assertRaw("legacy 2-arg APS", packet,
                new int[]{
                    -2143289343, -2147483646, -2143285245, -2147479548,
                    -2143288315, -2147482618, -2143284217, -2147478520
                },
                new int[]{50, 50, 50, 50, 50, 50, 50, 50});
    }

    /** Legacy 2-arg ctor: a valid IMU frame writes exactly seven raw events. */
    private static void testLegacyTwoArgPreservesExactImu() {
        final AEPacketRaw packet = decodeOnce(new SciDVSGaerRawSink(() -> 4096, () -> { }),
                defaultConfig(), 4096, imuSixAxisVector());
        assertRaw("legacy 2-arg IMU", packet, imuGoldenAddresses(), imuGoldenTimestamps());
        require(packet.getNumEvents() == IMUSample.SIZE_EVENTS,
                "legacy 2-arg IMU writes exactly SIZE_EVENTS");
    }

    /** 3-arg true supplier must write APS exactly as the legacy 2-arg path did. */
    private static void testDualWriteTrueWritesExactAps() {
        final AEPacketRaw packet = decodeOnce(new SciDVSGaerRawSink(
                        () -> 4096, () -> { }, () -> true),
                apsTwoByTwoConfig(), 4096,
                0x8032, 0x0008,
                0x000B, 0x4001, 0x4002, 0x000D,
                0x000B, 0x4003, 0x4004, 0x000D,
                0x000C, 0x4005, 0x4006, 0x000D,
                0x000C, 0x4007, 0x4008, 0x000D,
                0x000E, 0x000F, 0x000A);
        assertRaw("dual-write true APS", packet,
                new int[]{
                    -2143289343, -2147483646, -2143285245, -2147479548,
                    -2143288315, -2147482618, -2143284217, -2147478520
                },
                new int[]{50, 50, 50, 50, 50, 50, 50, 50});
    }

    /** 3-arg true supplier writes exactly SIZE_EVENTS IMU raw events, byte-exact. */
    private static void testDualWriteTrueWritesExactImuSizeEvents() {
        final AEPacketRaw packet = decodeOnce(new SciDVSGaerRawSink(
                        () -> 4096, () -> { }, () -> true),
                defaultConfig(), 4096, imuSixAxisVector());
        assertRaw("dual-write true IMU", packet, imuGoldenAddresses(), imuGoldenTimestamps());
        require(packet.getNumEvents() == IMUSample.SIZE_EVENTS,
                "dual-write true IMU writes exactly SIZE_EVENTS=7");
    }

    /** False supplier drops APS + IMU but never polarity or external. */
    private static void testSuppressFalseDropsApsImuKeepsPolarityExternal() {
        final SciDVSGaerDecoder decoder = new SciDVSGaerDecoder(defaultConfig());
        final SciDVSGaerRawSink raw = new SciDVSGaerRawSink(() -> 4096, () -> { }, () -> false);
        final AEPacketRaw packet = new AEPacketRaw();

        int eventCounter = transfer(decoder, raw, packet, 0,
                words(0x8064, 0x100A, 0x2001));
        require(eventCounter == 1, "false supplier keeps polarity");
        require(packet.lastCaptureIndex == 0 && packet.lastCaptureLength == 1,
                "false supplier polarity capture length");

        eventCounter = transfer(decoder, raw, packet, eventCounter,
                words(0x8007, 0x0002));
        require(eventCounter == 2, "false supplier keeps external");
        require(packet.lastCaptureIndex == 1 && packet.lastCaptureLength == 1,
                "false supplier external capture length");

        eventCounter = transfer(decoder, raw, packet, eventCounter,
                words(0x8032, 0x0008,
                        0x000B, 0x4001, 0x4002, 0x000D,
                        0x000B, 0x4003, 0x4004, 0x000D,
                        0x000C, 0x4005, 0x4006, 0x000D,
                        0x000C, 0x4007, 0x4008, 0x000D,
                        0x000E, 0x000F, 0x000A));
        require(eventCounter == 2, "false supplier drops APS (counter unchanged)");
        require(packet.lastCaptureIndex == 2 && packet.lastCaptureLength == 0,
                "false supplier APS capture length zero");

        eventCounter = transfer(decoder, raw, packet, eventCounter,
                words(imuSixAxisVector()));
        require(eventCounter == 2, "false supplier drops IMU (counter unchanged)");
        require(packet.lastCaptureIndex == 2 && packet.lastCaptureLength == 0,
                "false supplier IMU capture length zero");

        assertRaw("false supplier polarity+external only",
                packet, new int[]{42397696, 1026}, new int[]{100, 7});
    }

    /** Capture length stays exact when suppression empties a whole transfer. */
    private static void testCaptureLengthExactUnderSuppress() {
        final SciDVSGaerRawSink raw = new SciDVSGaerRawSink(() -> 4096, () -> { }, () -> false);
        final AEPacketRaw packet = new AEPacketRaw();
        final int eventCounter = transfer(new SciDVSGaerDecoder(apsTwoByTwoConfig()),
                raw, packet, 0,
                words(0x8032, 0x0008,
                        0x000B, 0x4001, 0x4002, 0x000D,
                        0x000E, 0x000F, 0x000A));
        require(eventCounter == 0, "fully-suppressed APS capture returns zero counter");
        require(packet.getNumEvents() == 0, "fully-suppressed APS numEvents zero");
        require(packet.lastCaptureIndex == 0 && packet.lastCaptureLength == 0,
                "fully-suppressed APS capture bounds exact");
    }

    /**
     * The dual-write toggle is read from the supplier on every callback, so
     * flipping it between successive IMU samples flips the raw write within a
     * single begin/end session.
     */
    private static void testToggleEvaluatedPerCallback() {
        final AtomicBoolean dualWrite = new AtomicBoolean(true);
        final SciDVSGaerRawSink raw = new SciDVSGaerRawSink(() -> 4096, () -> { }, dualWrite::get);
        final AEPacketRaw packet = new AEPacketRaw();
        final IMUSample sample = new IMUSample(77, new short[]{0x5001, 0x5002, 0x5003,
                0x5004, 0x5005, 0x5006, 0x5007});

        raw.begin(packet, 0);
        raw.onImuSample(sample, 77);
        require(packet.getNumEvents() == IMUSample.SIZE_EVENTS,
                "first IMU (toggle true) writes SIZE_EVENTS");

        dualWrite.set(false);
        raw.onImuSample(sample, 88);
        require(packet.getNumEvents() == IMUSample.SIZE_EVENTS,
                "second IMU (toggle false) writes nothing additional");

        dualWrite.set(true);
        raw.onImuSample(sample, 99);
        require(packet.getNumEvents() == 2 * IMUSample.SIZE_EVENTS,
                "third IMU (toggle true again) writes SIZE_EVENTS more");

        final int counter = raw.end();
        require(counter == 2 * IMUSample.SIZE_EVENTS, "toggle session end counter");
        require(packet.lastCaptureIndex == 0 && packet.lastCaptureLength == 2 * IMUSample.SIZE_EVENTS,
                "toggle session capture bounds span only non-suppressed samples");
    }

    /** Suppressed events must not set overrun or grow packet capacity. */
    private static void testSuppressedImuCannotOverrunOrMutateCapacity() {
        final AEPacketRaw tight = new AEPacketRaw(1);
        final int capacityBefore = tight.getCapacity();
        final SciDVSGaerRawSink raw = new SciDVSGaerRawSink(() -> 1, () -> { }, () -> false);
        final IMUSample sample = new IMUSample(77, new short[]{0x5001, 0x5002, 0x5003,
                0x5004, 0x5005, 0x5006, 0x5007});
        raw.begin(tight, 0);
        for (int i = 0; i < 1000; i++) {
            raw.onImuSample(sample, 77 + i);
        }
        final int counter = raw.end();
        require(counter == 0, "suppressed IMU flood leaves counter at zero");
        require(tight.getNumEvents() == 0, "suppressed IMU flood leaves numEvents zero");
        require(tight.getCapacity() == capacityBefore,
                "suppressed IMU flood does not mutate capacity");
        require(!tight.overrunOccuredFlag, "suppressed IMU flood does not set overrun");
    }

    /**
     * Address patch immediately after a suppressed APS patches the preceding
     * polarity. This pins the DAViS-equivalent latent behavior: with APS/IMU
     * dual-write off, the decoder's trailing address patch that in the DAViS
     * raw path would have followed the APS word instead lands on the last event
     * that was actually written, which is the preceding polarity. This is
     * accepted unpinned behavior, not a redesigned feature — we only assert that
     * it is stable exactly as observed.
     */
    private static void testAddressPatchAfterSuppressedApsPatchesPrecedingPolarity() {
        final SciDVSGaerRawSink raw = new SciDVSGaerRawSink(() -> 4096, () -> { }, () -> false);
        final AEPacketRaw packet = new AEPacketRaw();
        raw.begin(packet, 0);
        raw.onPolarity(42397696, 8, 10, false, 100);
        // Suppressed APS sample: no event is written, so the trailing patch must
        // apply to the polarity written just before it.
        raw.onApsSample(-2143289343, 0, 0, 1, false, false, false, 100);
        raw.onAddressPatch(0x02A);
        final int counter = raw.end();
        require(counter == 1, "patch-after-suppressed-APS leaves one event");
        require(packet.getNumEvents() == 1, "patch-after-suppressed-APS numEvents one");
        final int expected = 42397696 | 0x02A;
        require(packet.getAddresses()[0] == expected,
                "patch-after-suppressed-APS patches preceding polarity address ex=0x"
                        + Integer.toHexString(expected));
        require(packet.getTimestamps()[0] == 100, "patched polarity keeps timestamp");
        require(packet.lastCaptureIndex == 0 && packet.lastCaptureLength == 1,
                "patch-after-suppressed-APS capture bounds");
    }

    private static int[] imuSixAxisVector() {
        return new int[]{
            0x804D, 0x0005, 0x53E0,
            0x5001, 0x5002, 0x5003, 0x5004, 0x5005, 0x5006, 0x5007,
            0x5008, 0x5009, 0x500A, 0x500B, 0x500C, 0x500D, 0x500E,
            0x0007
        };
    }

    private static int[] imuGoldenAddresses() {
        return new int[]{
            -2146423808, -1875883008, -1605342208, -1334801408,
            -1064260608, -793719808, -523179008
        };
    }

    private static int[] imuGoldenTimestamps() {
        return new int[]{77, 77, 77, 77, 77, 77, 77};
    }

    private static AEPacketRaw decodeOnce(final SciDVSGaerRawSink raw,
            final SciDVSGaerDecoder.Config config, final int capacityLimit,
            final int... inputWords) {
        final AEPacketRaw packet = new AEPacketRaw();
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
        return config(false, 112, 126, 112, 126);
    }

    private static SciDVSGaerDecoder.Config apsTwoByTwoConfig() {
        return config(false, 112, 126, 2, 2);
    }

    private static SciDVSGaerDecoder.Config config(final boolean dvsInvertXY,
            final int dvsSizeX, final int dvsSizeY, final int apsSizeX, final int apsSizeY) {
        return new SciDVSGaerDecoder.Config(
                SciDVSHardwareInterface.CHIP_DAVIS240C,
                dvsSizeX, dvsSizeY, dvsInvertXY,
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
