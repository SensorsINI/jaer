package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.DavisUsbPacketBundleBuilder;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.ExternalEvent;
import net.sf.jaer.event.FramePacket;
import net.sf.jaer.event.ImuPacket;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.TypedDataPacket;

/**
 * Frozen headless acceptance vectors for the package-local typed-first GAER
 * sink. This file is intentionally RED at compile time only because
 * {@code SciDVSGaerTypedSink} and its constructor
 * {@code (DavisUsbPacketBundleBuilder,SciDVSGaerRawSink,IntSupplier,Runnable)}
 * are absent. The builder, bundle, decoder, raw sink, and event classes are the
 * production implementations. No chip or hardware is constructed.
 */
public final class SciDVSGaerTypedSinkDemo {

    private static final int APS_WIDTH = 112;
    private static final int APS_HEIGHT = 126;
    private static int assertions;

    private SciDVSGaerTypedSinkDemo() {
    }

    public static void main(final String[] args) throws Exception {
        testPolarityUsesPackedAddressAndSuppliedSize();
        testExternalCodesAndTimestampReset();
        testApsAndImuRawGate();
        testFrameCompletionIsCountBasedAndMarkersAreNoOps();
        testFlushIsIdempotent();
        testTypedOutputSurvivesRawOverrun();
        testNoFrameEndOrExposureOverrides();
        System.out.println("SCIDVS_GAER_TYPED_SINK ASSERTIONS=" + assertions);
        System.out.println("SCIDVS_GAER_TYPED_SINK PASS");
    }

    private static void testPolarityUsesPackedAddressAndSuppliedSize() throws Exception {
        final Harness exact = new Harness(defaultConfig(), 112, false, 4096, 16);
        exact.decode(0x8064, 0x100A, 0x2011);
        exact.finish();
        assertRaw(exact.rawPacket,
                new int[]{42397696, 42399744}, new int[]{100, 100}, "exact-size polarity");
        assertPolarity(exact.bundle, exact.rawPacket, new int[]{0, 0}, "exact-size polarity");

        final Harness mismatch = new Harness(defaultConfig(), 120, false, 4096, 16);
        mismatch.decode(0x8064, 0x100A, 0x2011);
        mismatch.finish();
        assertRaw(mismatch.rawPacket,
                new int[]{42397696, 42399744}, new int[]{100, 100}, "mismatch polarity");
        assertPolarity(mismatch.bundle, mismatch.rawPacket, new int[]{8, 8},
                "supplied size mismatch +8");
        require(Arrays.equals(Arrays.copyOf(exact.rawPacket.getAddresses(), 2),
                Arrays.copyOf(mismatch.rawPacket.getAddresses(), 2)),
                "supplied typed size never changes raw addresses");
    }

    private static void assertPolarity(final PacketBundle bundle, final AEPacketRaw raw,
            final int[] expectedX, final String name) {
        final EventPacket<?> packet = bundle.getFirstPolarityPacket();
        require(packet != null && packet.getSize() == expectedX.length,
                name + " typed polarity count");
        for (int i = 0; i < expectedX.length; i++) {
            final PolarityEvent event = (PolarityEvent) packet.getEvent(i);
            final int packed = raw.getAddresses()[i];
            final int expectedY = (packed & DavisChip.YMASK) >>> DavisChip.YSHIFT;
            final boolean expectedOn = ((packed & DavisChip.POLMASK) >>> DavisChip.POLSHIFT) != 0;
            require(event.getX() == expectedX[i], name + " typed X " + i);
            require(event.getY() == expectedY, name + " typed Y matches raw golden " + i);
            require((event.getPolarity() == PolarityEvent.Polarity.On) == expectedOn,
                    name + " typed polarity matches raw golden " + i);
            require(event.address == packed,
                    name + " typed packed address matches raw golden " + i);
            require(event.getTimestamp() == raw.getTimestamps()[i],
                    name + " typed timestamp matches raw golden " + i);
        }
    }

    private static void testExternalCodesAndTimestampReset() throws Exception {
        final Harness harness = new Harness(defaultConfig(), 112, false, 4096, 16);
        harness.decode(0x8007, 0x0002, 0x0003, 0x0004, 0x0001);
        harness.finish();

        final EventPacket<?> external = externalPacket(harness.bundle);
        require(external != null && external.getSize() == 3, "three typed external events");
        final ExternalEvent.Edge[] edges = {
            ExternalEvent.Edge.Falling, ExternalEvent.Edge.Rising, ExternalEvent.Edge.Pulse
        };
        for (int i = 0; i < 3; i++) {
            final ExternalEvent event = (ExternalEvent) external.getEvent(i);
            require(event.getCode() == i + 2, "external code " + (i + 2) + " exact");
            require(event.getEdge() == edges[i], "external edge " + (i + 2) + " exact");
            require(event.getTimestamp() == 7, "external timestamp exact " + i);
        }
        require(harness.typedResets[0] == 1, "typed timestamp reset handler runs exactly once");
        require(harness.rawResets[0] == 0, "raw reset handler is not double-invoked");
        assertRaw(harness.rawPacket, new int[]{1026, 1027, 1028},
                new int[]{7, 7, 7}, "external raw golden");
    }

    private static void testApsAndImuRawGate() throws Exception {
        final int[] apsWords = {
            0x8032, 0x0008,
            0x000B, 0x4001, 0x4002, 0x000D,
            0x000B, 0x4003, 0x4004, 0x000D,
            0x000C, 0x4005, 0x4006, 0x000D,
            0x000C, 0x4007, 0x4008, 0x000D
        };
        final Harness apsFalse = new Harness(apsTwoByTwoConfig(), 112, false, 4096, 16);
        apsFalse.decode(apsWords);
        apsFalse.finish();
        require(apsFalse.rawPacket.getNumEvents() == 0, "false raw gate suppresses APS AEs");

        final Harness apsTrue = new Harness(apsTwoByTwoConfig(), 112, true, 4096, 16);
        apsTrue.decode(apsWords);
        apsTrue.finish();
        assertRaw(apsTrue.rawPacket,
                new int[]{
                    -2143289343, -2147483646, -2143285245, -2147479548,
                    -2143288315, -2147482618, -2143284217, -2147478520
                },
                new int[]{50, 50, 50, 50, 50, 50, 50, 50}, "true raw APS gate");

        require(IMUSample.SIZE_EVENTS == 7, "IMUSample.SIZE_EVENTS frozen at seven");
        final Harness imuFalse = new Harness(defaultConfig(), 112, false, 4096, 16);
        imuFalse.decode(imuVector());
        imuFalse.finish();
        require(imuFalse.rawPacket.getNumEvents() == 0, "false raw gate suppresses IMU AEs");
        require(imuFalse.bundle.getFirstImuPacket() != null
                && imuFalse.bundle.getFirstImuPacket().getSize() == 1,
                "false raw gate still emits one typed IMU sample first");

        final Harness imuTrue = new Harness(defaultConfig(), 112, true, 4096, 16);
        imuTrue.decode(imuVector());
        imuTrue.finish();
        require(imuTrue.rawPacket.getNumEvents() == IMUSample.SIZE_EVENTS,
                "true raw gate writes exactly seven IMU AEs");
        require(imuTrue.bundle.getFirstImuPacket() != null
                && imuTrue.bundle.getFirstImuPacket().getSize() == 1,
                "true raw gate emits one typed IMU sample");
    }

    private static void testFrameCompletionIsCountBasedAndMarkersAreNoOps() throws Exception {
        final Harness harness = new Harness(defaultConfig(), 112, false, 4096, 16);
        harness.sink.onFrameStart(false, 10);
        final int samples = APS_WIDTH * APS_HEIGHT;
        for (int i = 0; i < samples - 1; i++) {
            final int x = i % APS_WIDTH;
            final int y = i / APS_WIDTH;
            harness.sink.onApsSample(0, 1000 + i, x, y, false,
                    i == 0, i == 0, 20 + i);
        }
        harness.sink.onFrameEnd(false, 50);
        harness.sink.onExposureStart(51);
        harness.sink.onExposureEnd(52);
        harness.builder.flushAll();
        require(harness.bundle.getFirstFramePacket() == null,
                "frame-end/exposure and an early pixelLast do not complete a short frame");

        harness.sink.onApsSample(0, 2000, APS_WIDTH - 1, APS_HEIGHT - 1,
                false, false, false, 20 + samples - 1);
        final FramePacket frame = harness.bundle.getFirstFramePacket();
        require(frame != null, "configured signal sample count completes frame");
        require(frame.getWidth() == APS_WIDTH && frame.getHeight() == APS_HEIGHT,
                "completed frame uses attached chip-null 112x126 geometry");
        require(harness.raw.end() == 0, "false gate leaves count-based frame out of raw AEs");
    }

    private static void testFlushIsIdempotent() throws Exception {
        final Harness harness = new Harness(defaultConfig(), 112, false, 4096, 16);
        harness.decode(0x8003, 0x100A, 0x2001, 0x0002);
        harness.raw.end();
        harness.builder.flushAll();
        final int once = harness.bundle.getNumPackets();
        harness.builder.flushAll();
        require(once == 2, "first flush installs external and polarity packets once");
        require(harness.bundle.getNumPackets() == once,
                "second flush installs no duplicate typed packets");
    }

    private static void testTypedOutputSurvivesRawOverrun() throws Exception {
        final Harness harness = new Harness(defaultConfig(), 112, false, 1, 2);
        harness.decode(0x8064, 0x100A, 0x20FF);
        harness.finish();
        require(harness.rawPacket.overrunOccuredFlag, "raw packet overrun is observable");
        require(harness.rawPacket.getNumEvents() == 2, "raw overrun freezes at capacity two");
        require(harness.bundle.getNumPolarityEvents() == 8,
                "typed-first path retains all eight decoded events");
        require(harness.bundle.getNumPolarityEvents() > harness.rawPacket.getNumEvents(),
                "typed count remains greater than truncated raw count");
    }

    private static void testNoFrameEndOrExposureOverrides() {
        require(!declares("onFrameEnd", boolean.class, int.class),
                "typed sink declares no frame-end override");
        require(!declares("onExposureStart", int.class),
                "typed sink declares no exposure-start override");
        require(!declares("onExposureEnd", int.class),
                "typed sink declares no exposure-end override");
    }

    private static boolean declares(final String name, final Class<?>... parameters) {
        try {
            final Method ignored = SciDVSGaerTypedSink.class.getDeclaredMethod(name, parameters);
            return ignored != null;
        } catch (final NoSuchMethodException expected) {
            return false;
        }
    }

    private static EventPacket<?> externalPacket(final PacketBundle bundle) {
        for (final TypedDataPacket packet : bundle) {
            if (packet instanceof EventPacket && packet.getSize() > 0
                    && ((EventPacket<?>) packet).getEvent(0) instanceof ExternalEvent) {
                return (EventPacket<?>) packet;
            }
        }
        return null;
    }

    private static SciDVSGaerRawSink gatedRaw(final int limit, final Runnable reset,
            final BooleanSupplier gate) throws Exception {
        final Constructor<SciDVSGaerRawSink> constructor = SciDVSGaerRawSink.class
                .getDeclaredConstructor(IntSupplier.class, Runnable.class, BooleanSupplier.class);
        constructor.setAccessible(true);
        return constructor.newInstance((IntSupplier) () -> limit, reset, gate);
    }

    private static void assertRaw(final AEPacketRaw packet, final int[] addresses,
            final int[] timestamps, final String name) {
        require(addresses.length == timestamps.length, name + " oracle lengths match");
        require(packet.getNumEvents() == addresses.length, name + " exact event count");
        require(Arrays.equals(Arrays.copyOf(packet.getAddresses(), addresses.length), addresses),
                name + " exact addresses");
        require(Arrays.equals(Arrays.copyOf(packet.getTimestamps(), timestamps.length), timestamps),
                name + " exact timestamps");
    }

    private static int[] imuVector() {
        return new int[]{
            0x804D, 0x0005, 0x53E0,
            0x5001, 0x5002, 0x5003, 0x5004, 0x5005, 0x5006, 0x5007,
            0x5008, 0x5009, 0x500A, 0x500B, 0x500C, 0x500D, 0x500E,
            0x0007
        };
    }

    private static SciDVSGaerDecoder.Config defaultConfig() {
        return config(APS_WIDTH, APS_HEIGHT);
    }

    private static SciDVSGaerDecoder.Config apsTwoByTwoConfig() {
        return config(2, 2);
    }

    private static SciDVSGaerDecoder.Config config(final int apsWidth, final int apsHeight) {
        return new SciDVSGaerDecoder.Config(
                SciDVSHardwareInterface.CHIP_DAVIS240C,
                112, 126, false,
                apsWidth, apsHeight, false, false, false,
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

    private static void require(final boolean condition, final String description) {
        assertions++;
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    private static final class Harness {
        final DavisUsbPacketBundleBuilder builder = new DavisUsbPacketBundleBuilder();
        final PacketBundle bundle = new PacketBundle();
        final AEPacketRaw rawPacket;
        final SciDVSGaerRawSink raw;
        final SciDVSGaerTypedSink sink;
        final SciDVSGaerDecoder decoder;
        final int[] typedResets = {0};
        final int[] rawResets = {0};

        Harness(final SciDVSGaerDecoder.Config config, final int suppliedSizeX,
                final boolean rawApsImu, final int rawLimit, final int rawCapacity)
                throws Exception {
            builder.attach(bundle, null, APS_WIDTH, APS_HEIGHT);
            rawPacket = new AEPacketRaw(rawCapacity);
            raw = gatedRaw(rawLimit, () -> rawResets[0]++, () -> rawApsImu);
            sink = new SciDVSGaerTypedSink(
                    builder, raw, () -> suppliedSizeX, () -> typedResets[0]++);
            decoder = new SciDVSGaerDecoder(config);
            raw.begin(rawPacket, 0);
        }

        void decode(final int... inputWords) {
            decoder.decode(words(inputWords), sink);
        }

        void finish() {
            raw.end();
            builder.flushAll();
        }
    }
}
