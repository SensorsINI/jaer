package net.sf.jaer.event;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import net.sf.jaer.aemonitor.AEPacketRaw;

/**
 * Headless RED contract for authoritative typed acquisition bundles. Missing
 * production APIs are reached only through reflection so this demo compiles
 * against the pre-migration tree and fails with a controlled contract message.
 */
public final class PacketBundleAuthorityDemo {

    private static int assertions;

    private PacketBundleAuthorityDemo() {
    }

    public static void main(final String[] args) throws Exception {
        legacyRawBridgePrecondition();
        System.out.println("PACKET_BUNDLE_AUTHORITY PRECONDITION_ASSERTIONS=" + assertions);
        authoritativeMetadataAndSealing();
        System.out.println("PACKET_BUNDLE_AUTHORITY ASSERTIONS=" + assertions);
        System.out.println("PACKET_BUNDLE_AUTHORITY PASS");
    }

    private static void legacyRawBridgePrecondition() {
        final PacketBundle bundle = new PacketBundle();
        final AEPacketRaw raw = new AEPacketRaw(2);
        raw.setNumEvents(1);
        bundle.setRawPacket(raw);
        require(bundle.getRawPacket() == raw, "legacy bridge retains its explicit raw sidecar");

        final EventPacket<PolarityEvent> polarity = polarityPacket(
                new int[]{0x10203040}, new int[]{11});
        bundle.add(polarity);
        require(bundle.getNumPackets() == 1 && bundle.getNumPolarityEvents() == 1,
                "legacy bridge can still carry its derived typed packet");
        bundle.clear();
        require(bundle.isEmpty() && bundle.getRawPacket() == null,
                "bundle clear removes legacy payload and sidecar");
    }

    private static void authoritativeMetadataAndSealing() throws Exception {
        final Class<?> metadataClass = requiredClass("net.sf.jaer.event.AcquisitionMetadata",
                "missing authoritative metadata contract: AcquisitionMetadata");
        final Method begin = requiredMethod(PacketBundle.class, "beginAcquisition",
                long.class, long.class);
        final Method getMetadata = requiredMethod(PacketBundle.class, "getAcquisitionMetadata");
        final Method seal = requiredMethod(PacketBundle.class, "seal");
        final Method isSealed = requiredMethod(PacketBundle.class, "isSealed");
        final Method isLegacyRawBridge = requiredMethod(PacketBundle.class, "isLegacyRawBridge");
        final Method copyContext = requiredMethod(PacketBundle.class,
                "copyAcquisitionContextFrom", PacketBundle.class);
        final Method setEpoch = requiredMethod(TypedDataPacket.class,
                "setTimestampEpoch", long.class);
        final Method getEpoch = requiredMethod(TypedDataPacket.class, "getTimestampEpoch");

        final Method getSessionId = requiredMethod(metadataClass, "getAcquisitionSessionId");
        final Method getSequenceId = requiredMethod(metadataClass, "getSequenceId");
        final Method getAcceptedCount = requiredMethod(metadataClass,
                "getAcceptedCount", PacketType.class);
        final Method getEpochs = requiredMethod(metadataClass, "getTimestampEpochs");
        final Method recordExactLoss = requiredMethod(metadataClass, "recordExactLoss",
                PacketType.class, long.class, String.class);
        final Method recordUnquantifiedLoss = requiredMethod(metadataClass,
                "recordUnquantifiedLoss", PacketType.class, String.class);
        final Method getExactLossCount = requiredMethod(metadataClass,
                "getExactLossCount", PacketType.class);
        final Method hasUnquantifiedLoss = requiredMethod(metadataClass,
                "hasUnquantifiedLoss", PacketType.class);
        final Method getLossRecords = requiredMethod(metadataClass, "getLossRecords");

        final AEPacketRaw raw = new AEPacketRaw(1);
        final PacketBundle rawFirst = new PacketBundle();
        rawFirst.setRawPacket(raw);
        expectIllegalState(() -> invoke(begin, rawFirst, 7L, 1L),
                "beginning authoritative metadata rejects an existing raw sidecar");

        final PacketBundle authoritative = new PacketBundle();
        final Object metadata = invoke(begin, authoritative, 7L, 2L);
        require(metadataClass.isInstance(metadata),
                "beginAcquisition returns authoritative AcquisitionMetadata");
        expectIllegalState(() -> authoritative.setRawPacket(raw),
                "setting a raw sidecar rejects begun authoritative metadata");
        require(!(Boolean) invoke(isLegacyRawBridge, authoritative),
                "authoritative acquisition is not a legacy raw bridge");

        final EventPacket<PolarityEvent> polarity = polarityPacket(
                new int[]{0x11110001, 0x22220002}, new int[]{100, 101});
        final FramePacket frame = new FramePacket(1, 1, FramePacket.ColorMode.GRAYSCALE);
        frame.setTimestampStartUs(102);
        frame.setTimestampEndUs(103);
        invoke(setEpoch, polarity, 3L);
        invoke(setEpoch, frame, 4L);
        authoritative.add(polarity);
        authoritative.add(frame);
        invoke(recordExactLoss, metadata, PacketType.POLARITY, 5L, "host capacity");
        invoke(recordUnquantifiedLoss, metadata, PacketType.IMU6, "partial sample at reset");
        invoke(seal, authoritative);

        require((Boolean) invoke(isSealed, authoritative),
                "authoritative bundle is sealed before publication");
        require(authoritative.getRawPacket() == null,
                "sealed authoritative bundle has no raw sidecar");
        require(number(invoke(getSessionId, metadata)) == 7L
                && number(invoke(getSequenceId, metadata)) == 2L,
                "metadata retains acquisition session and sequence identifiers");
        require(number(invoke(getAcceptedCount, metadata, PacketType.POLARITY)) == 2L,
                "sealing derives accepted polarity count from payload");
        require(number(invoke(getAcceptedCount, metadata, PacketType.FRAME)) == 1L,
                "sealing derives accepted frame count from payload");

        final Collection<?> epochs = (Collection<?>) invoke(getEpochs, metadata);
        final Set<Long> epochValues = new HashSet<>();
        for (final Object epoch : epochs) {
            epochValues.add(((Number) epoch).longValue());
        }
        require(epochValues.equals(Set.of(3L, 4L)),
                "sealing records both typed timestamp epochs");
        require(number(invoke(getEpoch, polarity)) == 3L
                && number(invoke(getEpoch, frame)) == 4L,
                "typed packets retain their non-negative timestamp epochs");
        require(number(invoke(getExactLossCount, metadata, PacketType.POLARITY)) == 5L,
                "metadata retains exact loss counts");
        require((Boolean) invoke(hasUnquantifiedLoss, metadata, PacketType.IMU6),
                "metadata retains unquantified loss without reporting zero");
        require(((Collection<?>) invoke(getLossRecords, metadata)).size() == 2,
                "metadata exposes both structured loss records");

        final PacketBundle invalid = new PacketBundle();
        invoke(begin, invalid, 7L, 3L);
        invalid.add(polarityPacket(new int[]{3}, new int[]{9}));
        expectIllegalState(() -> invoke(seal, invalid),
                "sealing rejects a live typed packet with no non-negative epoch");

        final Method constructNewPacket = requiredMethod(EventPacket.class, "constructNewPacket");
        final EventPacket<?> filteredPacket = (EventPacket<?>) invoke(constructNewPacket, polarity);
        require(number(invoke(getEpoch, filteredPacket)) == 3L,
                "typed filtering copies the packet timestamp epoch");
        final PacketBundle filtered = new PacketBundle();
        invoke(copyContext, filtered, authoritative);
        filtered.addAllowEmpty(filteredPacket);
        invoke(seal, filtered);
        final Object filteredMetadata = invoke(getMetadata, filtered);
        require(filteredMetadata != metadata && metadataClass.isInstance(filteredMetadata),
                "filtering copies rather than aliases acquisition metadata");
        require(number(invoke(getAcceptedCount, filteredMetadata, PacketType.POLARITY)) == 2L
                && number(invoke(getAcceptedCount, filteredMetadata, PacketType.FRAME)) == 1L,
                "filtering preserves source accepted counts");
        require(number(invoke(getExactLossCount, filteredMetadata, PacketType.POLARITY)) == 5L
                && (Boolean) invoke(hasUnquantifiedLoss, filteredMetadata, PacketType.IMU6),
                "filtering preserves exact and unquantified source loss");
        require(filtered.getRawPacket() == null,
                "filtering an authoritative bundle does not invent a raw sidecar");

        final PacketBundle legacy = new PacketBundle();
        legacy.setRawPacket(raw);
        final PacketBundle legacyCopy = new PacketBundle();
        invoke(copyContext, legacyCopy, legacy);
        require(legacyCopy.getRawPacket() == raw
                && (Boolean) invoke(isLegacyRawBridge, legacyCopy),
                "filter context copies a raw sidecar only for a legacy bridge");

        final Path filterSource = Paths.get("src", "net", "sf", "jaer",
                "eventprocessing", "FilterChain.java");
        final String source = Files.readString(filterSource, StandardCharsets.UTF_8);
        require(source.contains("copyAcquisitionContextFrom(in)"),
                "FilterChain copies the acquisition context into filtered bundles");
        require(!source.contains("out.setRawPacket(in.getRawPacket())"),
                "FilterChain no longer copies raw sidecars unconditionally");
    }

    private static EventPacket<PolarityEvent> polarityPacket(
            final int[] addresses, final int[] timestamps) {
        require(addresses.length == timestamps.length, "polarity fixture lengths match");
        final EventPacket<PolarityEvent> packet = new EventPacket<>(PolarityEvent.class);
        final OutputEventIterator<PolarityEvent> output = packet.outputIterator();
        for (int i = 0; i < addresses.length; i++) {
            final PolarityEvent event = output.nextOutput();
            event.address = addresses[i];
            event.timestamp = timestamps[i];
            event.x = (short) i;
            event.y = (short) (i + 1);
            event.setPolarity((i & 1) == 0
                    ? PolarityEvent.Polarity.On : PolarityEvent.Polarity.Off);
        }
        return packet;
    }

    private static Class<?> requiredClass(final String name, final String failure) {
        try {
            return Class.forName(name);
        } catch (final ClassNotFoundException expected) {
            require(false, failure);
            return null;
        }
    }

    private static Method requiredMethod(final Class<?> owner, final String name,
            final Class<?>... parameters) {
        try {
            return owner.getMethod(name, parameters);
        } catch (final NoSuchMethodException expected) {
            require(false, "missing authoritative metadata contract: "
                    + owner.getSimpleName() + "." + name);
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

    private static void expectIllegalState(final CheckedAction action, final String description)
            throws Exception {
        try {
            action.run();
            require(false, description);
        } catch (final IllegalStateException expected) {
            require(true, description);
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

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }
}
