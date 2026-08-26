package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.DavisUsbPacketBundleBuilder;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.event.AcquisitionMetadata;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PacketType;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Hardware-free runtime vectors for the production standard DAVIS USB parsers.
 * The typed vectors invoke {@code RetinaAEReader.translateStandardTyped}
 * directly; the raw vectors invoke the established {@code translateEvents}
 * legacy branch on an offline monitor with fixed SPI configuration readbacks.
 */
public final class StandardDavisTypedParserDemo {

    private static final int DVS_SIZE_X = 4;
    private static final int DVS_SIZE_Y = 3;
    private static final int APS_SIZE_X = 2;
    private static final int APS_SIZE_Y = 2;
    private static final int WARNING_INTERVAL = 100000;
    private static final Method TRANSLATE_STANDARD_TYPED;
    private static final Field TYPED_BUILDER;
    private static final Field GAER_RESOLVED;
    private static final Field APS_COUNT_Y;
    private static final Field WARNING_COUNT;
    private static int assertions;
    private static long sequence;

    static {
        try {
            TRANSLATE_STANDARD_TYPED = DAViSFX3HardwareInterface.RetinaAEReader.class
                    .getDeclaredMethod("translateStandardTyped", ByteBuffer.class);
            TRANSLATE_STANDARD_TYPED.setAccessible(true);
            TYPED_BUILDER = DAViSFX3HardwareInterface.RetinaAEReader.class
                    .getDeclaredField("typedBuilder");
            TYPED_BUILDER.setAccessible(true);
            GAER_RESOLVED = DAViSFX3HardwareInterface.RetinaAEReader.class
                    .getDeclaredField("gaerResolved");
            GAER_RESOLVED.setAccessible(true);
            APS_COUNT_Y = DAViSFX3HardwareInterface.RetinaAEReader.class
                    .getDeclaredField("apsCountY");
            APS_COUNT_Y.setAccessible(true);
            WARNING_COUNT = DAViSFX3HardwareInterface.class
                    .getDeclaredField("warningCount");
            WARNING_COUNT.setAccessible(true);
        } catch (final ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private StandardDavisTypedParserDemo() {
    }

    public static void main(final String[] args) throws Exception {
        testByteVectorPolarityAndAddressPatchMatchesRaw();
        testInterveningWordsCloseAddressPatchWindow();
        testSecondAddressPatchIsNoOp();
        testInvalidPolarityWordClosesAddressPatchWindow();
        testRejectedPolarityPatchDoesNotTouchPreviousAccepted();
        testAcceptedPatchRecomputesAllDerivedFields();
        testInvalidApsSamplesAreDiscardedByBothParsers();
        testMiscellaneousWordDiagnosticsMatchRaw();
        testWarningThrottlingMatchesRaw();
        testUnknownDavisImuAssemblyRemainsConservative();
        System.out.println("STANDARD_DAVIS_TYPED_PARSER ASSERTIONS=" + assertions);
        System.out.println("STANDARD_DAVIS_TYPED_PARSER PASS");
    }

    private static void testByteVectorPolarityAndAddressPatchMatchesRaw() throws Exception {
        final int[] vector = {0x8007, 0x1001, 0x2002, 0x6805, 0x3001};
        final RawHarness raw = new RawHarness();
        raw.parse(vector);
        final TypedHarness typed = new TypedHarness(Integer.MAX_VALUE);
        typed.parse(vector);

        final EventPacket<?> packet = typed.polarity();
        require(raw.packet.getNumEvents() == 2 && packet != null && packet.getSize() == 2,
                "standard raw and typed parsers emit two polarity events");
        for (int i = 0; i < 2; i++) {
            final PolarityEvent event = (PolarityEvent) packet.getEvent(i);
            final int rawAddress = raw.packet.getAddresses()[i];
            require(event.address == rawAddress,
                    "typed patched address matches established raw address " + i);
            require(event.getTimestamp() == raw.packet.getTimestamps()[i],
                    "typed timestamp matches established raw timestamp " + i);
            assertDerivedFields(event, rawAddress, DVS_SIZE_X - 1,
                    "standard byte-vector event " + i);
        }
        require((raw.packet.getAddresses()[0] & 0x07ff) == 5,
                "reachable miscellaneous-11 patch payload is retained");
    }

    private static void testInterveningWordsCloseAddressPatchWindow() throws Exception {
        final int[] interveningWords = {
            0x800A, // Ordinary timestamp.
            0x0002, // Special external-input event.
            0x1001, // DVS Y address.
            0x4001, // APS sample.
            0x5000, // IMU byte.
            0x6000  // Miscellaneous-11 exposure word.
        };
        final String[] descriptions = {
            "timestamp", "special", "Y", "APS", "IMU", "exposure"
        };
        for (int i = 0; i < interveningWords.length; i++) {
            final TypedHarness typed = new TypedHarness(Integer.MAX_VALUE);
            typed.parse(0x8009, 0x1001, 0x2002, interveningWords[i], 0x6805);
            final EventPacket<?> packet = typed.polarity();
            require(packet != null && packet.getSize() == 1,
                    descriptions[i] + " vector retains one polarity event");
            final PolarityEvent event = (PolarityEvent) packet.getEvent(0);
            require((event.address & 0x07ff) == 0,
                    descriptions[i] + " word closes the address-patch window");
        }
    }

    private static void testSecondAddressPatchIsNoOp() throws Exception {
        final TypedHarness typed = new TypedHarness(Integer.MAX_VALUE);
        typed.parse(0x8009, 0x1001, 0x2002, 0x6801, 0x6804);
        final EventPacket<?> packet = typed.polarity();
        require(packet != null && packet.getSize() == 1,
                "double-patch vector retains one polarity event");
        final PolarityEvent event = (PolarityEvent) packet.getEvent(0);
        require((event.address & 0x07ff) == 1,
                "second patch cannot re-patch the same polarity event");
    }

    private static void testInvalidPolarityWordClosesAddressPatchWindow() throws Exception {
        final TypedHarness typed = new TypedHarness(Integer.MAX_VALUE);
        typed.parse(0x8009, 0x1001, 0x2002,
                0x2000 | DVS_SIZE_X, 0x6805);
        final EventPacket<?> packet = typed.polarity();
        require(packet != null && packet.getSize() == 1,
                "invalid-polarity vector retains only the valid polarity event");
        final PolarityEvent event = (PolarityEvent) packet.getEvent(0);
        require((event.address & 0x07ff) == 0,
                "invalid X/polarity word leaves address-patch eligibility false");
    }

    private static void testRejectedPolarityPatchDoesNotTouchPreviousAccepted() throws Exception {
        final TypedHarness typed = new TypedHarness(1);
        typed.parse(0x8009, 0x1001, 0x2002, 0x3001, 0x6805);
        final EventPacket<?> packet = typed.polarity();
        require(packet != null && packet.getSize() == 1,
                "bounded typed parser retains exactly the first polarity event");
        final PolarityEvent first = (PolarityEvent) packet.getEvent(0);
        require((first.address & 0x07ff) == 0,
                "patch after rejected polarity leaves preceding accepted address untouched");
        assertDerivedFields(first, first.address, DVS_SIZE_X - 1,
                "preceding accepted bounded event");

        final AcquisitionMetadata metadata = typed.bundle.getAcquisitionMetadata();
        require(metadata.getExactLossCount(PacketType.POLARITY) == 1,
                "bounded typed parser records one rejected polarity event");
        final AcquisitionMetadata.LossRecord loss = metadata.getLossRecords().iterator().next();
        require(loss.getKind() == AcquisitionMetadata.LossKind.HOST_CAPACITY,
                "bounded typed parser preserves structured HOST_CAPACITY loss");
    }

    private static void testAcceptedPatchRecomputesAllDerivedFields() {
        final PacketBundle bundle = new PacketBundle();
        bundle.beginAcquisition(71, sequence++);
        final DavisUsbPacketBundleBuilder builder = new DavisUsbPacketBundleBuilder();
        builder.attach(bundle, null, 16, 16);
        builder.addPolarity(15, 0, false, 11, 0);
        final int patch = (4 << DavisChip.YSHIFT)
                | (3 << DavisChip.XSHIFT) | DavisChip.POLMASK;
        builder.patchLastPolarityAddress(patch);
        builder.flushAll();
        bundle.seal();

        final PolarityEvent event = (PolarityEvent) bundle.getFirstPolarityPacket().getEvent(0);
        require(event.address == patch, "accepted address patch updates packed address");
        require(event.getX() == 12, "accepted address patch recomputes typed X");
        require(event.getY() == 4, "accepted address patch recomputes typed Y");
        require(event.getPolarity() == PolarityEvent.Polarity.On,
                "accepted address patch recomputes typed polarity");
        require(event.getType() == 1, "accepted address patch recomputes typed event type");
    }

    private static void testInvalidApsSamplesAreDiscardedByBothParsers() throws Exception {
        final TypedHarness typed = new TypedHarness(Integer.MAX_VALUE);
        typed.setWarningCount(1);
        typed.setResetRowCount(APS_SIZE_Y);
        typed.parse(0x4001);

        final RawHarness raw = new RawHarness();
        raw.setWarningCount(1);
        raw.setResetRowCount(APS_SIZE_Y);
        raw.parse(0x4001);

        require(typed.bundle.isEmpty(), "typed parser discards invalid APS sample");
        require(raw.packet.getNumEvents() == 0,
                "raw parser discards the same invalid APS sample outside a log interval");
        require(typed.resetRowCount() == APS_SIZE_Y && raw.resetRowCount() == APS_SIZE_Y,
                "invalid APS sample advances neither parser state");
        require(typed.warningCount() == 2 && raw.warningCount() == 2,
                "invalid APS discard advances both warning throttles once");
    }

    private static void testMiscellaneousWordDiagnosticsMatchRaw() throws Exception {
        final TypedHarness typed = new TypedHarness(Integer.MAX_VALUE);
        final List<LogRecord> typedLogs = captureLogs(
                () -> typed.parse(0x5401, 0x6001));
        final RawHarness raw = new RawHarness();
        final List<LogRecord> rawLogs = captureLogs(
                () -> raw.parse(0x5401, 0x6001));

        require(countMessages(typedLogs, "Caught Misc8 event that can't be handled.") == 1,
                "typed parser diagnoses one unknown miscellaneous-8 word");
        require(countMessages(rawLogs, "Caught Misc8 event that can't be handled.") == 1,
                "raw parser diagnoses the same unknown miscellaneous-8 word");
        require(countMessages(typedLogs, "Caught Misc10 event that can't be handled.") == 0
                && countMessages(rawLogs, "Caught Misc10 event that can't be handled.") == 0,
                "both legal miscellaneous-11 selectors are handled without diagnostics");
    }

    private static void testWarningThrottlingMatchesRaw() throws Exception {
        final int[] invalidX = new int[WARNING_INTERVAL + 1];
        java.util.Arrays.fill(invalidX, 0x2000 | DVS_SIZE_X);

        final TypedHarness typed = new TypedHarness(Integer.MAX_VALUE);
        final List<LogRecord> typedLogs = captureLogs(() -> typed.parse(invalidX));
        final RawHarness raw = new RawHarness();
        final List<LogRecord> rawLogs = captureLogs(() -> raw.parse(invalidX));

        require(countMessagesContaining(typedLogs, "DVS: X address out of range") == 2,
                "typed parser logs first and 100001st invalid X only");
        require(countMessagesContaining(rawLogs, "DVS: X address out of range") == 2,
                "raw parser uses the same invalid-X warning throttle");
        require(typed.warningCount() == WARNING_INTERVAL + 1
                && raw.warningCount() == WARNING_INTERVAL + 1,
                "raw and typed warning counters advance on every invalid X");
        require(typed.bundle.isEmpty() && raw.packet.getNumEvents() == 0,
                "warning throttling never changes invalid-X discard semantics");
    }

    private static void testUnknownDavisImuAssemblyRemainsConservative() throws Exception {
        final TypedHarness typed = new TypedHarness(Integer.MAX_VALUE);
        typed.setWarningCount(1);
        final List<LogRecord> typedLogs = captureLogs(
                () -> typed.parse(0x0007, 0x0007, 0x0007));
        final RawHarness raw = new RawHarness();
        raw.setWarningCount(1);
        final List<LogRecord> rawLogs = captureLogs(
                () -> raw.parse(0x0007, 0x0007, 0x0007));

        require(typed.warningCount() == 4 && raw.warningCount() == 4,
                "suppressed incomplete-IMU warnings still advance both throttles");
        require(countMessagesContaining(typedLogs, "failed to validate IMU sample count") == 0
                && countMessagesContaining(rawLogs, "failed to validate IMU sample count") == 0,
                "non-interval incomplete-IMU diagnostics remain suppressed");
        final AcquisitionMetadata metadata = typed.bundle.getAcquisitionMetadata();
        require(metadata.hasUnquantifiedLoss(PacketType.IMU6),
                "DAVIS unknown IMU assembly is conservatively unquantified");
        require(metadata.getLossRecords().size() == 3,
                "each DAVIS IMU end without a tracked start records unknown loss");
        for (final AcquisitionMetadata.LossRecord loss : metadata.getLossRecords()) {
            require(loss.getKind() == AcquisitionMetadata.LossKind.PARTIAL_IMU
                    && loss.isUnquantified(),
                    "unknown DAVIS IMU loss retains structured PARTIAL_IMU kind");
        }
    }

    private static void assertDerivedFields(final PolarityEvent event,
            final int address, final int unflipMaxX, final String description) {
        final int expectedX = unflipMaxX
                - ((address & DavisChip.XMASK) >>> DavisChip.XSHIFT);
        final int expectedY = (address & DavisChip.YMASK) >>> DavisChip.YSHIFT;
        final boolean expectedOn = (address & DavisChip.POLMASK) != 0;
        require(event.getX() == expectedX, description + " derived X");
        require(event.getY() == expectedY, description + " derived Y");
        require((event.getPolarity() == PolarityEvent.Polarity.On) == expectedOn,
                description + " derived polarity");
        require(event.getType() == (expectedOn ? 1 : 0),
                description + " derived type");
    }

    private static List<LogRecord> captureLogs(final CheckedRunnable action) throws Exception {
        final Logger logger = CypressFX3.log;
        final Level oldLevel = logger.getLevel();
        final CapturingHandler handler = new CapturingHandler();
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        logger.setLevel(Level.ALL);
        try {
            action.run();
            return new ArrayList<>(handler.records);
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(oldLevel);
        }
    }

    private static int countMessages(final List<LogRecord> records, final String message) {
        int count = 0;
        for (final LogRecord record : records) {
            if (message.equals(record.getMessage())) {
                count++;
            }
        }
        return count;
    }

    private static int countMessagesContaining(final List<LogRecord> records,
            final String text) {
        int count = 0;
        for (final LogRecord record : records) {
            if (record.getMessage() != null && record.getMessage().contains(text)) {
                count++;
            }
        }
        return count;
    }

    private static ByteBuffer words(final int... words) {
        final ByteBuffer buffer = ByteBuffer.allocate(words.length * Short.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (final int word : words) {
            buffer.putShort((short) word);
        }
        return buffer.flip();
    }

    private static void invokeTyped(final DAViSFX3HardwareInterface.RetinaAEReader reader,
            final ByteBuffer input) throws Exception {
        try {
            TRANSLATE_STANDARD_TYPED.invoke(reader, input);
        } catch (final InvocationTargetException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw e;
        }
    }

    private static void require(final boolean condition, final String description) {
        assertions++;
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }

    private static final class CapturingHandler extends Handler {
        final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(final LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    private static final class TypedHarness {
        final OfflineDavisFx3 monitor = new OfflineDavisFx3();
        final DAViSFX3HardwareInterface.RetinaAEReader reader;
        final DavisUsbPacketBundleBuilder builder;
        final PacketBundle bundle = new PacketBundle();

        TypedHarness(final int capacity) throws Exception {
            reader = monitor.new RetinaAEReader(monitor);
            GAER_RESOLVED.set(reader, Boolean.FALSE);
            builder = (DavisUsbPacketBundleBuilder) TYPED_BUILDER.get(reader);
            builder.setHostCapacitySupplier(() -> capacity);
            bundle.beginAcquisition(17, sequence++);
            builder.attach(bundle, null, APS_SIZE_X, APS_SIZE_Y);
        }

        void parse(final int... inputWords) throws Exception {
            invokeTyped(reader, words(inputWords));
            builder.flushAll();
            bundle.seal();
        }

        EventPacket<?> polarity() {
            return bundle.getFirstPolarityPacket();
        }

        void setWarningCount(final int count) throws IllegalAccessException {
            WARNING_COUNT.setInt(monitor, count);
        }

        int warningCount() throws IllegalAccessException {
            return WARNING_COUNT.getInt(monitor);
        }

        void setResetRowCount(final int count) throws IllegalAccessException {
            ((short[]) APS_COUNT_Y.get(reader))[0] = (short) count;
        }

        int resetRowCount() throws IllegalAccessException {
            return ((short[]) APS_COUNT_Y.get(reader))[0];
        }
    }

    private static final class RawHarness {
        final OfflineDavisFx3 monitor = new OfflineDavisFx3();
        final DAViSFX3HardwareInterface.RetinaAEReader reader;
        AEPacketRaw packet;

        RawHarness() throws Exception {
            reader = monitor.new RetinaAEReader(monitor);
            GAER_RESOLVED.set(reader, Boolean.FALSE);
        }

        void parse(final int... inputWords) {
            reader.translateEvents(words(inputWords));
            packet = monitor.rawWriteBuffer();
        }

        void setWarningCount(final int count) throws IllegalAccessException {
            WARNING_COUNT.setInt(monitor, count);
        }

        int warningCount() throws IllegalAccessException {
            return WARNING_COUNT.getInt(monitor);
        }

        void setResetRowCount(final int count) throws IllegalAccessException {
            ((short[]) APS_COUNT_Y.get(reader))[0] = (short) count;
        }

        int resetRowCount() throws IllegalAccessException {
            return ((short[]) APS_COUNT_Y.get(reader))[0];
        }
    }

    /** Offline-only monitor: constructor and parser perform no USB operations. */
    private static final class OfflineDavisFx3 extends DAViSFX3HardwareInterface {

        OfflineDavisFx3() {
            super(null);
        }

        @Override
        public short getVID_THESYCON_FX2_CPLD() {
            return 0;
        }

        @Override
        public short getPID() {
            return PID_FX3;
        }

        @Override
        protected void checkFirmwareLogic(final int requiredFirmwareVersion,
                final int requiredLogicRevision) {
        }

        @Override
        public synchronized int spiConfigReceive(final short moduleAddr,
                final short paramAddr) throws HardwareInterfaceException {
            if (moduleAddr == CypressFX3.FPGA_SYSINFO && paramAddr == 1) {
                return CHIP_DAVIS240C;
            }
            if (moduleAddr == CypressFX3.FPGA_APS && paramAddr == 0) {
                return APS_SIZE_X;
            }
            if (moduleAddr == CypressFX3.FPGA_APS && paramAddr == 1) {
                return APS_SIZE_Y;
            }
            if (moduleAddr == CypressFX3.FPGA_DVS && paramAddr == 0) {
                return DVS_SIZE_X;
            }
            if (moduleAddr == CypressFX3.FPGA_DVS && paramAddr == 1) {
                return DVS_SIZE_Y;
            }
            return 0;
        }

        @Override
        protected void updateTimestampMasterStatus() {
        }

        AEPacketRaw rawWriteBuffer() {
            return aePacketRawPool.writeBuffer();
        }
    }
}
