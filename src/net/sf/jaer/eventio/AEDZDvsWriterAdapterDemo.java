package net.sf.jaer.eventio;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.function.ToIntFunction;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.FramePacket;
import net.sf.jaer.event.ImuPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PacketType;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.TypedDataPacket;

/**
 * RED acceptance demo for the typed DVS projection used by live AEDZ recording.
 * The absent adapter is loaded reflectively so the pre-migration tree compiles
 * and fails with the frozen "adapter missing" reason rather than linkage noise.
 */
public final class AEDZDvsWriterAdapterDemo {

    private static int assertions;

    private AEDZDvsWriterAdapterDemo() {
    }

    public static void main(final String[] args) throws Exception {
        rawAedzFixtureRoundTrips();
        System.out.println("AEDZ_DVS_ADAPTER PRECONDITION_ASSERTIONS=" + assertions);
        typedProjectionContract();
        System.out.println("AEDZ_DVS_ADAPTER ASSERTIONS=" + assertions);
        System.out.println("AEDZ_DVS_ADAPTER PASS");
    }

    private static void rawAedzFixtureRoundTrips() throws Exception {
        final AEPacketRaw raw = rawPacket(
                new int[]{0x01020304, 0x11223344, 0x55667788},
                new int[]{100, 101, 104});
        final File file = tempFile();
        try {
            writeRaw(file, raw);
            final AEPacketRaw reopened = read(file, 3);
            require(reopened.getNumEvents() == 3, "raw AEDZ fixture reopens three events");
            for (int i = 0; i < 3; i++) {
                require(reopened.getAddresses()[i] == raw.getAddresses()[i]
                        && reopened.getTimestamps()[i] == raw.getTimestamps()[i],
                        "raw AEDZ fixture event parity " + i);
            }
        } finally {
            file.delete();
        }
    }

    private static void typedProjectionContract() throws Exception {
        final Class<?> adapterClass = requiredClass(
                "net.sf.jaer.eventio.AEDZDvsWriterAdapter",
                "AEDZDvsWriterAdapter missing");
        final Constructor<?> constructor = requiredConstructor(adapterClass,
                AEDZOutputStream.class, ToIntFunction.class);
        final Method writeBundle = requiredMethod(adapterClass, "writeBundle", PacketBundle.class);
        final Method getSkippedCount = requiredMethod(adapterClass,
                "getSkippedCount", PacketType.class);
        final Method setEpoch = requiredMethod(TypedDataPacket.class,
                "setTimestampEpoch", long.class);
        final Method begin = requiredMethod(PacketBundle.class,
                "beginAcquisition", long.class, long.class);
        final Method seal = requiredMethod(PacketBundle.class, "seal");

        equivalentRawAndTypedBytes(constructor, writeBundle, setEpoch, begin, seal);
        filteredAddressReconstruction(constructor, writeBundle, setEpoch, begin, seal);
        skippedNonDvsCounts(constructor, writeBundle, getSkippedCount,
                setEpoch, begin, seal);
        epochChangeForcesChunkBoundary(constructor, writeBundle, setEpoch, begin, seal);
    }

    private static void equivalentRawAndTypedBytes(final Constructor<?> constructor,
            final Method writeBundle, final Method setEpoch, final Method begin,
            final Method seal) throws Exception {
        final int[] addresses = {0x12340001, 0x12340002, 0x12340003};
        final int[] timestamps = {200, 202, 205};
        final AEPacketRaw raw = rawPacket(addresses, timestamps);
        final EventPacket<PolarityEvent> polarity = polarityPacket(addresses, timestamps);
        invoke(setEpoch, polarity, 0L);
        final PacketBundle bundle = authoritativeBundle(begin, seal, 11L, 1L, polarity);
        final File rawFile = tempFile();
        final File typedFile = tempFile();
        try {
            writeRaw(rawFile, raw);
            writeTyped(typedFile, constructor, writeBundle, bundle, event -> event.address);
            require(Arrays.equals(firstChunkBytes(rawFile), firstChunkBytes(typedFile)),
                    "typed DVS projection is byte-identical to equivalent raw AEDZ chunk");
            final AEPacketRaw reopened = read(typedFile, addresses.length);
            for (int i = 0; i < addresses.length; i++) {
                require(reopened.getAddresses()[i] == addresses[i]
                        && reopened.getTimestamps()[i] == timestamps[i],
                        "typed DVS projection preserves order/address/timestamp " + i);
            }
        } finally {
            rawFile.delete();
            typedFile.delete();
        }
    }

    private static void filteredAddressReconstruction(final Constructor<?> constructor,
            final Method writeBundle, final Method setEpoch, final Method begin,
            final Method seal) throws Exception {
        final EventPacket<PolarityEvent> polarity = polarityPacket(
                new int[]{0x0badc0de}, new int[]{333});
        final PolarityEvent event = polarity.getEvent(0);
        event.x = 7;
        event.y = 9;
        event.setPolarity(PolarityEvent.Polarity.Off);
        final ToIntFunction<PolarityEvent> reconstruct = item -> 0x40000000
                | ((item.x & 0xff) << 16) | ((item.y & 0xff) << 1)
                | (item.getPolarity() == PolarityEvent.Polarity.On ? 1 : 0);
        final int expectedAddress = reconstruct.applyAsInt(event);
        invoke(setEpoch, polarity, 2L);
        final PacketBundle bundle = authoritativeBundle(begin, seal, 11L, 2L, polarity);
        final File file = tempFile();
        try {
            writeTyped(file, constructor, writeBundle, bundle, reconstruct);
            final AEPacketRaw reopened = read(file, 1);
            require(reopened.getAddresses()[0] == expectedAddress,
                    "adapter uses filtered-event address reconstruction");
            require(reopened.getAddresses()[0] != event.address,
                    "adapter does not reuse stale pre-filter raw address");
            require(reopened.getTimestamps()[0] == event.timestamp,
                    "filtered-address reconstruction preserves timestamp");
        } finally {
            file.delete();
        }
    }

    private static void skippedNonDvsCounts(final Constructor<?> constructor,
            final Method writeBundle, final Method getSkippedCount, final Method setEpoch,
            final Method begin, final Method seal) throws Exception {
        final EventPacket<PolarityEvent> polarity = polarityPacket(new int[]{7}, new int[]{400});
        final FramePacket frame = new FramePacket(2, 2, FramePacket.ColorMode.GRAYSCALE);
        frame.setTimestampStartUs(401);
        frame.setTimestampEndUs(402);
        final ImuPacket imu = new ImuPacket();
        imu.nextOutput();
        invoke(setEpoch, polarity, 5L);
        invoke(setEpoch, frame, 5L);
        invoke(setEpoch, imu, 5L);
        final PacketBundle bundle = authoritativeBundle(begin, seal, 11L, 3L,
                polarity, frame, imu);
        final File file = tempFile();
        Object adapter = null;
        try (AEDZOutputStream output = new AEDZOutputStream(new FileOutputStream(file), null)) {
            adapter = constructor.newInstance(output,
                    (ToIntFunction<PolarityEvent>) event -> event.address);
            invoke(writeBundle, adapter, bundle);
        } finally {
            file.delete();
        }
        require(number(invoke(getSkippedCount, adapter, PacketType.FRAME)) == 1L,
                "adapter reports one skipped frame payload");
        require(number(invoke(getSkippedCount, adapter, PacketType.IMU6)) == 1L,
                "adapter reports one skipped IMU payload");
    }

    private static void epochChangeForcesChunkBoundary(final Constructor<?> constructor,
            final Method writeBundle, final Method setEpoch, final Method begin,
            final Method seal) throws Exception {
        final EventPacket<PolarityEvent> first = polarityPacket(new int[]{21}, new int[]{500});
        final EventPacket<PolarityEvent> second = polarityPacket(new int[]{22}, new int[]{5});
        invoke(setEpoch, first, 8L);
        invoke(setEpoch, second, 9L);
        final PacketBundle bundle = authoritativeBundle(begin, seal, 11L, 4L, first, second);
        final File file = tempFile();
        final AEDZOutputStream output = new AEDZOutputStream(new FileOutputStream(file), null);
        try {
            final Object adapter = constructor.newInstance(output,
                    (ToIntFunction<PolarityEvent>) event -> event.address);
            invoke(writeBundle, adapter, bundle);
        } finally {
            output.close();
        }
        try {
            require(output.getNumEvents() == 2,
                    "epoch-separated adapter output retains both polarity events");
            require(output.getNumChunks() == 2,
                    "typed epoch change forces an AEDZ chunk boundary");
        } finally {
            file.delete();
        }
    }

    private static PacketBundle authoritativeBundle(final Method begin, final Method seal,
            final long session, final long sequence, final TypedDataPacket... packets)
            throws Exception {
        final PacketBundle bundle = new PacketBundle();
        invoke(begin, bundle, session, sequence);
        for (final TypedDataPacket packet : packets) {
            bundle.addAllowEmpty(packet);
        }
        invoke(seal, bundle);
        return bundle;
    }

    private static void writeTyped(final File file, final Constructor<?> constructor,
            final Method writeBundle, final PacketBundle bundle,
            final ToIntFunction<PolarityEvent> reconstructor) throws Exception {
        try (AEDZOutputStream output = new AEDZOutputStream(new FileOutputStream(file), null)) {
            final Object adapter = constructor.newInstance(output, reconstructor);
            invoke(writeBundle, adapter, bundle);
        }
    }

    private static void writeRaw(final File file, final AEPacketRaw raw) throws Exception {
        try (AEDZOutputStream output = new AEDZOutputStream(new FileOutputStream(file), null)) {
            output.writePacket(raw);
        }
    }

    private static AEPacketRaw read(final File file, final int count) throws Exception {
        try (AEDZInputStream input = new AEDZInputStream(file)) {
            return input.readPacketByNumber(count);
        }
    }

    private static byte[] firstChunkBytes(final File file) throws Exception {
        final byte[] bytes = Files.readAllBytes(file.toPath());
        final int headerLength = leInt(bytes, 21);
        final int firstChunk = 29 + headerLength;
        final int chunkDataLength = leInt(bytes, firstChunk + 4);
        return Arrays.copyOfRange(bytes, firstChunk, firstChunk + 8 + chunkDataLength);
    }

    private static int leInt(final byte[] bytes, final int offset) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16)
                | ((bytes[offset + 3] & 0xff) << 24);
    }

    private static AEPacketRaw rawPacket(final int[] addresses, final int[] timestamps) {
        require(addresses.length == timestamps.length, "raw fixture lengths match");
        final AEPacketRaw raw = new AEPacketRaw(Math.max(1, addresses.length));
        System.arraycopy(addresses, 0, raw.getAddresses(), 0, addresses.length);
        System.arraycopy(timestamps, 0, raw.getTimestamps(), 0, timestamps.length);
        raw.setNumEvents(addresses.length);
        return raw;
    }

    private static EventPacket<PolarityEvent> polarityPacket(
            final int[] addresses, final int[] timestamps) {
        require(addresses.length == timestamps.length, "typed fixture lengths match");
        final EventPacket<PolarityEvent> packet = new EventPacket<>(PolarityEvent.class);
        final OutputEventIterator<PolarityEvent> output = packet.outputIterator();
        for (int i = 0; i < addresses.length; i++) {
            final PolarityEvent event = output.nextOutput();
            event.address = addresses[i];
            event.timestamp = timestamps[i];
            event.x = (short) (i + 1);
            event.y = (short) (i + 2);
            event.setPolarity((i & 1) == 0
                    ? PolarityEvent.Polarity.On : PolarityEvent.Polarity.Off);
        }
        return packet;
    }

    private static File tempFile() throws Exception {
        return File.createTempFile("jaer-aedz-typed-adapter", ".aedz");
    }

    private static Class<?> requiredClass(final String name, final String failure) {
        try {
            return Class.forName(name);
        } catch (final ClassNotFoundException expected) {
            require(false, failure);
            return null;
        }
    }

    private static Constructor<?> requiredConstructor(final Class<?> owner,
            final Class<?>... parameters) {
        try {
            return owner.getConstructor(parameters);
        } catch (final NoSuchMethodException expected) {
            require(false, "AEDZDvsWriterAdapter missing constructor (AEDZOutputStream,ToIntFunction)");
            return null;
        }
    }

    private static Method requiredMethod(final Class<?> owner, final String name,
            final Class<?>... parameters) {
        try {
            return owner.getMethod(name, parameters);
        } catch (final NoSuchMethodException expected) {
            require(false, "missing typed AEDZ contract: " + owner.getSimpleName() + "." + name);
            return null;
        }
    }

    private static Object invoke(final Method method, final Object target,
            final Object... arguments) throws Exception {
        try {
            return method.invoke(target, arguments);
        } catch (final InvocationTargetException wrapped) {
            final Throwable cause = wrapped.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new RuntimeException(cause);
        }
    }

    private static long number(final Object value) {
        return ((Number) value).longValue();
    }

    private static void require(final boolean condition, final String description) {
        assertions++;
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
