package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.sf.jaer.aemonitor.AEPacketRaw;

/**
 * Frozen headless acceptance vectors for throttling repeated GAER decoder
 * warnings. This file is intentionally RED at compile time until the
 * package-local {@code SciDVSGaerLogThrottle} functional interface and the
 * {@code SciDVSGaerDecoder(Config,String,SciDVSGaerLogThrottle)} constructor
 * exist. The legacy constructors, decoder, raw sink, and logger are production
 * objects rather than test doubles.
 */
public final class SciDVSGaerThrottleDemo {

    private static int assertions;

    private SciDVSGaerThrottleDemo() {
    }

    public static void main(final String[] args) {
        testLegacyConstructorsAlwaysLog();
        testFalseThrottlePreservesRawOutput();
        testEveryThirdUsesExactInvocationCount();
        testOddByteWarningIsUngated();
        System.out.println("SCIDVS_GAER_THROTTLE ASSERTIONS=" + assertions);
        System.out.println("SCIDVS_GAER_THROTTLE PASS");
    }

    private static void testLegacyConstructorsAlwaysLog() {
        try (SevereCapture capture = new SevereCapture()) {
            final AEPacketRaw packet = decode(new SciDVSGaerDecoder(defaultConfig()),
                    warningVector());
            require(capture.count == 10, "legacy Config constructor logs all ten warnings");
            assertRaw("legacy Config constructor", packet);
        }

        try (SevereCapture capture = new SevereCapture()) {
            final AEPacketRaw packet = decode(
                    new SciDVSGaerDecoder(defaultConfig(), "frozen-throttle-demo"),
                    warningVector());
            require(capture.count == 10,
                    "legacy Config/String constructor logs all ten warnings");
            assertRaw("legacy Config/String constructor", packet);
        }
    }

    private static void testFalseThrottlePreservesRawOutput() {
        final int[] invocations = {0};
        final SciDVSGaerLogThrottle never = () -> {
            invocations[0]++;
            return false;
        };
        try (SevereCapture capture = new SevereCapture()) {
            final AEPacketRaw packet = decode(new SciDVSGaerDecoder(
                    defaultConfig(), "never", never), warningVector());
            require(invocations[0] == 10,
                    "false throttle is consulted once for each reachable per-event warning");
            require(capture.count == 0, "false throttle suppresses all repeated warnings");
            assertRaw("false throttle", packet);
        }
    }

    private static void testEveryThirdUsesExactInvocationCount() {
        final int[] invocations = {0};
        final SciDVSGaerLogThrottle everyThird = () -> (++invocations[0] % 3) == 0;
        try (SevereCapture capture = new SevereCapture()) {
            final AEPacketRaw packet = decode(new SciDVSGaerDecoder(
                    defaultConfig(), "every-third", everyThird),
                    words(0x8064,
                            0x107E, 0x107F, 0x1080, 0x1081,
                            0x1082, 0x1083, 0x1084, 0x1085,
                            0x100A, 0x2001));
            require(invocations[0] == 8, "every-third throttle has exactly eight invocations");
            require(capture.count == 2, "eight invocations log exactly calls three and six");
            require(packet.getNumEvents() == 1, "warning flood leaves one valid raw event");
            require(packet.getAddresses()[0] == 42397696,
                    "warning flood valid event keeps fixed raw address");
            require(packet.getTimestamps()[0] == 100,
                    "warning flood valid event keeps fixed timestamp");
        }
    }

    private static void testOddByteWarningIsUngated() {
        final int[] invocations = {0};
        final SciDVSGaerLogThrottle never = () -> {
            invocations[0]++;
            return false;
        };
        final ByteBuffer odd = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN);
        odd.putShort((short) 0x8009).putShort((short) 0x0002).put((byte) 0x55).flip();

        try (SevereCapture capture = new SevereCapture()) {
            final AEPacketRaw packet = decode(new SciDVSGaerDecoder(
                    defaultConfig(), "odd", never), odd);
            require(capture.count == 1, "odd byte limit always emits one loud SEVERE");
            require(invocations[0] == 0, "odd byte limit does not consult per-event throttle");
            require(odd.limit() == 4 && odd.position() == 0,
                    "odd byte input retains production truncation contract");
            require(packet.getNumEvents() == 1 && packet.getAddresses()[0] == 1026
                    && packet.getTimestamps()[0] == 9,
                    "odd byte input decodes its complete external event exactly");
        }
    }

    /**
     * Yields 10 gated records/invocations across all nine reachable sites.
     * The outer event-switch default and the misc11 default cannot be reached
     * from their three-bit/one-bit masks; production review confirms they
     * remain throttle-guarded.
     */
    private static ByteBuffer warningVector() {
        return words(0x8064, 0x8063, 0x107E, 0x0000, 0x0007, 0x000A, 0x000D,
                0x0012, 0x3C01, 0x5F00, 0x100A, 0x2001, 0x0002);
    }

    private static void assertRaw(final String name, final AEPacketRaw packet) {
        require(packet.getNumEvents() == 2, name + " exact raw event count");
        require(Arrays.equals(Arrays.copyOf(packet.getAddresses(), 2),
                new int[]{42397696, 1026}), name + " exact raw addresses");
        require(Arrays.equals(Arrays.copyOf(packet.getTimestamps(), 2),
                new int[]{99, 99}), name + " exact raw timestamps");
    }

    private static AEPacketRaw decode(final SciDVSGaerDecoder decoder,
            final ByteBuffer input) {
        final AEPacketRaw packet = new AEPacketRaw();
        final SciDVSGaerRawSink raw = new SciDVSGaerRawSink(() -> 4096, () -> { });
        raw.begin(packet, 0);
        decoder.decode(input, raw);
        final int count = raw.end();
        require(count == packet.getNumEvents(), "raw sink cursor equals packet event count");
        return packet;
    }

    private static SciDVSGaerDecoder.Config defaultConfig() {
        return new SciDVSGaerDecoder.Config(
                SciDVSHardwareInterface.CHIP_DAVIS240C,
                112, 126, false,
                112, 126, false, false, false,
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

    /** Isolates and counts production decoder INFO and SEVERE records. */
    private static final class SevereCapture implements AutoCloseable {
        private final Logger logger = Logger.getLogger(SciDVSGaerDecoder.class.getName());
        private final Level oldLevel = logger.getLevel();
        private final boolean oldUseParentHandlers = logger.getUseParentHandlers();
        private int count;
        private final Handler handler = new Handler() {
            @Override
            public void publish(final LogRecord record) {
                if (record.getLevel().intValue() >= Level.INFO.intValue()) {
                    count++;
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };

        SevereCapture() {
            logger.setUseParentHandlers(false);
            logger.setLevel(Level.ALL);
            handler.setLevel(Level.ALL);
            logger.addHandler(handler);
        }

        @Override
        public void close() {
            logger.removeHandler(handler);
            logger.setLevel(oldLevel);
            logger.setUseParentHandlers(oldUseParentHandlers);
        }
    }
}
