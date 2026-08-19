package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Passing characterization vectors that discriminate GAER grouped words from
 * ordinary DAViS per-address words using the actual production decoder.
 */
public final class SciDVSGaerRouteDiscriminationDemo {

    private static int assertions;

    private SciDVSGaerRouteDiscriminationDemo() {
    }

    public static void main(final String[] args) {
        testDavisAddressThirtyExpandsAsGaerBits();
        testOutOfRangeGaerGroupIsLoudOnce();
        System.out.println("SCIDVS_GAER_ROUTE_DISCRIMINATION ASSERTIONS=" + assertions);
        System.out.println("SCIDVS_GAER_ROUTE_DISCRIMINATION PASS");
    }

    private static void testDavisAddressThirtyExpandsAsGaerBits() {
        final RecordingSink sink = new RecordingSink();
        final int davisAddressThirtyWord = 0x2000 | 30;
        new SciDVSGaerDecoder(defaultConfig()).decode(
                words(0x8064, 0x100A, davisAddressThirtyWord), sink);

        require(Integer.bitCount(30) == 4, "frozen bit count for DAViS address thirty");
        require(sink.events.size() == Integer.bitCount(30),
                "GAER route expands DAViS-format address thirty by payload bit count");
        require(sink.events.size() != 1,
                "GAER route does not interpret DAViS-format address thirty as one event");
        final int[] expectedX = {1, 2, 3, 0};
        final boolean[] expectedOn = {false, false, false, true};
        for (int i = 0; i < expectedX.length; i++) {
            final Polarity event = sink.events.get(i);
            require(event.x == expectedX[i], "expanded address thirty X " + i);
            require(event.y == 10, "expanded address thirty Y " + i);
            require(event.on == expectedOn[i], "expanded address thirty polarity " + i);
            require(event.timestamp == 100, "expanded address thirty timestamp " + i);
        }
    }

    private static void testOutOfRangeGaerGroupIsLoudOnce() {
        final RecordingSink sink = new RecordingSink();
        try (SevereCapture capture = new SevereCapture()) {
            new SciDVSGaerDecoder(defaultConfig()).decode(words(0x100A, 0x3C01), sink);
            require(sink.events.isEmpty(), "out-of-range GAER group 28 emits no polarity");
            require(capture.count == 1, "out-of-range GAER group 28 emits one loud SEVERE");
            require(capture.message != null && capture.message.contains("groupAddr: 28"),
                    "group 28 SEVERE identifies the rejected group");
        }
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

    private static final class RecordingSink implements SciDVSGaerSink {
        final List<Polarity> events = new ArrayList<>();

        @Override
        public void onPolarity(final int packedAddress, final int x, final int y,
                final boolean on, final int timestamp) {
            events.add(new Polarity(x, y, on, timestamp));
        }
    }

    private static final class Polarity {
        final int x;
        final int y;
        final boolean on;
        final int timestamp;

        Polarity(final int x, final int y, final boolean on, final int timestamp) {
            this.x = x;
            this.y = y;
            this.on = on;
            this.timestamp = timestamp;
        }
    }

    private static final class SevereCapture implements AutoCloseable {
        private final Logger logger = Logger.getLogger(SciDVSGaerDecoder.class.getName());
        private final Level oldLevel = logger.getLevel();
        private final boolean oldUseParentHandlers = logger.getUseParentHandlers();
        private int count;
        private String message;
        private final Handler handler = new Handler() {
            @Override
            public void publish(final LogRecord record) {
                if (record.getLevel() == Level.SEVERE) {
                    count++;
                    message = record.getMessage();
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
