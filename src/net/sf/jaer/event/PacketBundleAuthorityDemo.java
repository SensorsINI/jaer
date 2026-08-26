package net.sf.jaer.event;

import eu.seebetter.ini.chips.davis.DavisUsbPacketBundleBuilder;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.CypressFX3;

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
        final Class<?> lossKindClass = requiredClass(
                "net.sf.jaer.event.AcquisitionMetadata$LossKind",
                "missing structured loss contract: AcquisitionMetadata.LossKind");
        final Class<?> lossRecordClass = requiredClass(
                "net.sf.jaer.event.AcquisitionMetadata$LossRecord",
                "missing structured loss contract: AcquisitionMetadata.LossRecord");
        final Object hostCapacity = requiredEnumConstant(lossKindClass, "HOST_CAPACITY");
        final Object deviceReported = requiredEnumConstant(lossKindClass, "DEVICE_REPORTED");
        final Object partialFrame = requiredEnumConstant(lossKindClass, "PARTIAL_FRAME");
        final Object partialImu = requiredEnumConstant(lossKindClass, "PARTIAL_IMU");
        final Object malformedInput = requiredEnumConstant(lossKindClass, "MALFORMED_INPUT");
        final Object unknown = requiredEnumConstant(lossKindClass, "UNKNOWN");
        require(Set.of(hostCapacity, deviceReported, partialFrame, partialImu,
                malformedInput, unknown).size() == 6,
                "structured loss contract exposes all required distinct kinds");

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
        final Method recordExactLossWithKind = requiredMethod(metadataClass, "recordExactLoss",
                PacketType.class, lossKindClass, long.class, String.class);
        final Method recordUnquantifiedLoss = requiredMethod(metadataClass,
                "recordUnquantifiedLoss", PacketType.class, String.class);
        final Method recordUnquantifiedLossWithKind = requiredMethod(metadataClass,
                "recordUnquantifiedLoss", PacketType.class, lossKindClass, String.class);
        final Method getExactLossCount = requiredMethod(metadataClass,
                "getExactLossCount", PacketType.class);
        final Method hasUnquantifiedLoss = requiredMethod(metadataClass,
                "hasUnquantifiedLoss", PacketType.class);
        final Method getLossRecords = requiredMethod(metadataClass, "getLossRecords");
        final Method getLossKind = requiredMethod(lossRecordClass, "getKind");

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
        invoke(recordExactLossWithKind, metadata, PacketType.POLARITY,
                hostCapacity, 5L, "opaque source accounting label");
        invoke(recordUnquantifiedLossWithKind, metadata, PacketType.IMU6,
                partialImu, "partial sample at reset");
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
        final Collection<?> authoritativeLosses
                = (Collection<?>) invoke(getLossRecords, metadata);
        require(authoritativeLosses.size() == 2,
                "metadata exposes both structured loss records");
        require(invoke(getLossKind,
                findLoss(authoritativeLosses, PacketType.POLARITY)) == hostCapacity,
                "typed exact loss retains HOST_CAPACITY independently of reason wording");
        require(invoke(getLossKind,
                findLoss(authoritativeLosses, PacketType.IMU6)) == partialImu,
                "typed unquantified loss retains PARTIAL_IMU");

        builderLossKindBehavior(begin, seal, getLossRecords, getLossKind,
                hostCapacity, partialFrame, partialImu);
        structuredOverrunBehavior(begin, seal, recordExactLoss,
                recordExactLossWithKind, getLossRecords, getLossKind,
                hostCapacity, unknown);

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

        runtimeFilterChainAuthorityBehavior(authoritative, metadata, polarity,
                getMetadata, getEpoch, getLossKind);

        final PacketBundle legacy = new PacketBundle();
        legacy.setRawPacket(raw);
        final PacketBundle legacyCopy = new PacketBundle();
        invoke(copyContext, legacyCopy, legacy);
        require(legacyCopy.getRawPacket() == raw
                && (Boolean) invoke(isLegacyRawBridge, legacyCopy),
                "filter context copies a raw sidecar only for a legacy bridge");
    }

    private static void builderLossKindBehavior(final Method begin,
            final Method seal, final Method getLossRecords,
            final Method getLossKind, final Object hostCapacity,
            final Object partialFrame, final Object partialImu) throws Exception {
        final PacketBundle bundle = new PacketBundle();
        invoke(begin, bundle, 11L, 1L);
        final DavisUsbPacketBundleBuilder builder = new DavisUsbPacketBundleBuilder();
        builder.setHostCapacitySupplier(() -> 0);
        final AEChip chip = newTestChip();
        try {
            builder.attach(bundle, chip, 1, 1);
            builder.onFrameStart(false, 10);
            builder.onImuStart();
            builder.addPolarity(0, 0, true, 11);
            builder.onTimestampReset(false);
            builder.flushAll();
            invoke(seal, bundle);

            final Collection<?> losses = (Collection<?>) invoke(getLossRecords,
                    bundle.getAcquisitionMetadata());
            require(losses.size() == 3,
                    "builder records capacity, partial-frame, and partial-IMU losses");
            require(invoke(getLossKind,
                    findLoss(losses, PacketType.POLARITY)) == hostCapacity,
                    "authoritative capacity exhaustion is HOST_CAPACITY");
            require(invoke(getLossKind,
                    findLoss(losses, PacketType.FRAME)) == partialFrame,
                    "reset-discarded frame is PARTIAL_FRAME");
            require(invoke(getLossKind,
                    findLoss(losses, PacketType.IMU6)) == partialImu,
                    "reset-discarded IMU sample is PARTIAL_IMU");
        } finally {
            chip.cleanup();
        }
    }

    private static void structuredOverrunBehavior(final Method begin,
            final Method seal, final Method recordExactLoss,
            final Method recordExactLossWithKind, final Method getLossRecords,
            final Method getLossKind, final Object hostCapacity,
            final Object unknown) throws Exception {
        final String identicalReason = "opaque wording 4187";
        final TestCypressFX3 monitor = new TestCypressFX3();
        setAuthoritativeDelivery(monitor);

        final PacketBundle capacityBundle = new PacketBundle();
        final Object capacityMetadata = invoke(begin, capacityBundle, 12L, 1L);
        invoke(recordExactLossWithKind, capacityMetadata, PacketType.POLARITY,
                hostCapacity, 1L, identicalReason);
        invoke(seal, capacityBundle);
        monitor.publish(capacityBundle);
        require(monitor.overrunOccurred(),
                "HOST_CAPACITY reports overrun with arbitrary reason wording");

        final PacketBundle unknownBundle = new PacketBundle();
        final Object unknownMetadata = invoke(begin, unknownBundle, 12L, 2L);
        invoke(recordExactLoss, unknownMetadata, PacketType.POLARITY,
                1L, identicalReason);
        invoke(seal, unknownBundle);
        final Collection<?> unknownLosses
                = (Collection<?>) invoke(getLossRecords, unknownMetadata);
        require(invoke(getLossKind, findLoss(unknownLosses, PacketType.POLARITY)) == unknown,
                "source-compatible exact overload maps loss kind to UNKNOWN");
        monitor.publish(unknownBundle);
        require(!monitor.overrunOccurred(),
                "UNKNOWN does not report overrun with identical reason wording");
    }

    private static void runtimeFilterChainAuthorityBehavior(
            final PacketBundle source, final Object sourceMetadata,
            final EventPacket<PolarityEvent> sourcePolarity,
            final Method getMetadata, final Method getEpoch,
            final Method getLossKind) throws Exception {
        final Method getAcceptedCounts = requiredMethod(sourceMetadata.getClass(),
                "getAcceptedCounts");
        final Method getTimestampEpochs = requiredMethod(sourceMetadata.getClass(),
                "getTimestampEpochs");
        final Method getLossRecords = requiredMethod(sourceMetadata.getClass(),
                "getLossRecords");

        final Map<?, ?> sourceCountsBefore = new LinkedHashMap<>(
                (Map<?, ?>) invoke(getAcceptedCounts, sourceMetadata));
        final Set<?> sourceEpochsBefore = new LinkedHashSet<>(
                (Collection<?>) invoke(getTimestampEpochs, sourceMetadata));
        final List<?> sourceLossesBefore = new ArrayList<>(
                (Collection<?>) invoke(getLossRecords, sourceMetadata));
        final List<String> sourceLossSignaturesBefore
                = lossSignatures(sourceLossesBefore, getLossKind);

        System.setProperty("java.awt.headless", "true");
        final AEChip chip = newTestChip();
        try {
            final FilterChain chain = new FilterChain(chip);
            final CopyingTestFilter filter = new CopyingTestFilter(chip);
            filter.setFilterEnabled(true);
            chain.add(filter);
            final PacketBundle output = chain.filterBundle(source);
            final Object outputMetadata = invoke(getMetadata, output);

            require(output != source,
                    "runtime FilterChain produces a distinct bundle");
            require(filter.invocations == 1 && filter.lastInput == sourcePolarity,
                    "enabled runtime filter processes the authoritative polarity packet");
            require(output.isSealed() && source.isSealed(),
                    "runtime FilterChain preserves sealed authority without mutating source sealing");
            require(outputMetadata != null && outputMetadata != sourceMetadata,
                    "runtime FilterChain deep-copies authoritative metadata");

            final Map<?, ?> sourceCountsAfter
                    = (Map<?, ?>) invoke(getAcceptedCounts, sourceMetadata);
            final Map<?, ?> outputCounts
                    = (Map<?, ?>) invoke(getAcceptedCounts, outputMetadata);
            require(sourceCountsAfter.equals(sourceCountsBefore),
                    "runtime FilterChain leaves source accepted counts unchanged");
            require(outputCounts.equals(sourceCountsBefore)
                    && outputCounts != sourceCountsAfter,
                    "runtime FilterChain deep-copies source accepted counts unchanged");

            final Collection<?> sourceEpochsAfter
                    = (Collection<?>) invoke(getTimestampEpochs, sourceMetadata);
            final Collection<?> outputEpochs
                    = (Collection<?>) invoke(getTimestampEpochs, outputMetadata);
            require(new LinkedHashSet<>(sourceEpochsAfter).equals(sourceEpochsBefore),
                    "runtime FilterChain leaves source epochs unchanged");
            require(new LinkedHashSet<>(outputEpochs).equals(sourceEpochsBefore)
                    && outputEpochs != sourceEpochsAfter,
                    "runtime FilterChain deep-copies source epochs unchanged");

            final List<?> sourceLossesAfter = new ArrayList<>(
                    (Collection<?>) invoke(getLossRecords, sourceMetadata));
            final List<?> outputLosses = new ArrayList<>(
                    (Collection<?>) invoke(getLossRecords, outputMetadata));
            require(lossSignatures(sourceLossesAfter, getLossKind)
                    .equals(sourceLossSignaturesBefore),
                    "runtime FilterChain leaves source losses unchanged");
            require(lossSignatures(outputLosses, getLossKind)
                    .equals(sourceLossSignaturesBefore),
                    "runtime FilterChain copies all source loss fields unchanged");
            require(recordsAreDistinct(sourceLossesBefore, outputLosses),
                    "runtime FilterChain deep-copies loss records without aliasing");

            final EventPacket<?> outputPolarity = output.getFirstPolarityPacket();
            require(outputPolarity != null && outputPolarity != sourcePolarity
                    && outputPolarity == filter.lastOutput,
                    "runtime filter produces and reuses its own polarity packet");
            require(number(invoke(getEpoch, outputPolarity)) == 3L,
                    "runtime FilterChain preserves the event packet epoch");
            require(output.getRawPacket() == null,
                    "runtime FilterChain adds no raw sidecar to authoritative output");
        } finally {
            chip.cleanup();
        }
    }

    private static List<String> lossSignatures(final Collection<?> records,
            final Method getLossKind) throws Exception {
        final List<String> signatures = new ArrayList<>();
        for (final Object record : records) {
            final Class<?> type = record.getClass();
            final Method getPacketType = requiredMethod(type, "getPacketType");
            final Method getQuantification = requiredMethod(type, "getQuantification");
            final Method isExact = requiredMethod(type, "isExact");
            final Method getExactCount = requiredMethod(type, "getExactCount");
            final Method getReason = requiredMethod(type, "getReason");
            final Object count = (Boolean) invoke(isExact, record)
                    ? invoke(getExactCount, record) : "unquantified";
            signatures.add(invoke(getPacketType, record) + "|"
                    + invoke(getLossKind, record) + "|"
                    + invoke(getQuantification, record) + "|" + count + "|"
                    + invoke(getReason, record));
        }
        return signatures;
    }

    private static boolean recordsAreDistinct(final List<?> source,
            final List<?> output) {
        if (source.size() != output.size()) {
            return false;
        }
        for (int i = 0; i < source.size(); i++) {
            if (source.get(i) == output.get(i)) {
                return false;
            }
        }
        return true;
    }

    private static Object findLoss(final Collection<?> records,
            final PacketType packetType) throws Exception {
        for (final Object record : records) {
            final Method getPacketType = requiredMethod(record.getClass(), "getPacketType");
            if (invoke(getPacketType, record) == packetType) {
                return record;
            }
        }
        throw new AssertionError("missing loss record for " + packetType);
    }

    /** Builds only the state FilterChain needs, avoiding GUI, sockets, and hardware. */
    private static AEChip newTestChip() throws Exception {
        final Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        final Field singleton = unsafeClass.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        final Object unsafe = singleton.get(null);
        final Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
        final AEChip chip = (AEChip) allocateInstance.invoke(unsafe, AEChip.class);
        chip.setPrefs(java.util.prefs.Preferences.userRoot().node(
                "/jaer/tests/PacketBundleAuthorityDemo"));
        chip.setSupport(new java.beans.PropertyChangeSupport(chip));
        return chip;
    }

    private static void setAuthoritativeDelivery(final CypressFX3 monitor)
            throws Exception {
        final Field field = CypressFX3.class.getDeclaredField("deliveryMode");
        field.setAccessible(true);
        field.set(monitor, requiredEnumConstant(field.getType(), "AUTHORITATIVE_TYPED"));
    }

    private static Object requiredEnumConstant(final Class<?> owner,
            final String name) {
        try {
            return owner.getField(name).get(null);
        } catch (final ReflectiveOperationException expected) {
            require(false, "missing enum constant " + owner.getSimpleName() + "." + name);
            return null;
        }
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

    private static final class CopyingTestFilter extends EventFilter2D {

        private int invocations;
        private EventPacket<?> lastInput;
        private EventPacket<?> lastOutput;

        CopyingTestFilter(final AEChip chip) {
            super(chip);
        }

        @Override
        public EventPacket<? extends BasicEvent> filterPacket(
                final EventPacket<? extends BasicEvent> input) {
            invocations++;
            lastInput = input;
            checkOutputPacketEventType(input);
            final OutputEventIterator<?> output = out.outputIterator();
            for (final BasicEvent event : input) {
                ((BasicEvent) output.nextOutput()).copyFrom(event);
            }
            lastOutput = out;
            return out;
        }

        @Override
        public void resetFilter() {
        }

        @Override
        public void initFilter() {
        }
    }

    private static final class TestCypressFX3 extends CypressFX3 {

        TestCypressFX3() {
            super(null);
        }

        void publish(final PacketBundle bundle) {
            lastPacketBundle = bundle;
        }
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }
}
