package net.sf.jaer.eventio;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objenesis.ObjenesisStd;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.EventExtractor2D;

/** Regression coverage for logical marks and valid timestamp-zero AEDAT-2 events. */
public class AEFileInputStreamZeroTimestampAndMarkTest {

    private static final byte[] ZERO_TIMESTAMP_FIXTURE = decodeHex(
            "23214145522d444154322e300d0a23456e64204f66204153434949204865616465720d0a"
            + "1011121300000000212223250000000a3132333500000014");

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void preservesValidZeroTimestampEvent() throws IOException {
        File fixture = writeBytes("zero-timestamp.aedat", ZERO_TIMESTAMP_FIXTURE);

        try (AEFileInputStream input = open(fixture)) {
            input.setRepeat(false);

            AEPacketRaw actual = input.readPacketByNumber(3);

            assertEquals("all three events", 3, actual.getNumEvents());
            assertArrayEquals("fixed addresses",
                    new int[]{0x10111213, 0x21222325, 0x31323335},
                    Arrays.copyOf(actual.getAddresses(), actual.getNumEvents()));
            assertArrayEquals("timestamp zero is data, not padding",
                    new int[]{0, 10, 20},
                    Arrays.copyOf(actual.getTimestamps(), actual.getNumEvents()));
            assertEquals("first timestamp", 0, input.getFirstTimestamp());
            assertEquals("last timestamp", 20, input.getLastTimestamp());
        }
    }

    @Test
    public void storesAndReloadsExactLogicalMarkIndices() throws IOException {
        File fixture = writeFourteenEventFixture();
        try {
            try (AEFileInputStream input = open(fixture)) {
                input.marksInitialize();

                input.position(1);
                assertEquals("early IN mark is logical event one", 1L, input.setMarkIn());

                input.position(13);
                assertTrue("ordinary marker is added", input.toggleMarker());
                assertTrue("ordinary marker is logical event thirteen",
                        input.getMarks().otherMarks.contains(13L));
            }

            try (AEFileInputStream reopened = open(fixture)) {
                reopened.marksInitialize();
                assertEquals("persisted IN mark", 1L, reopened.getMarkInPosition());
                assertTrue("persisted ordinary marker",
                        reopened.getMarks().otherMarks.contains(13L));
                reopened.clearMarks();
            }
        } finally {
            AEFileInputStream.marksPutForFile(fixture, null);
        }
    }

    private File writeFourteenEventFixture() throws IOException {
        File fixture = temporaryFolder.newFile("logical-marks.aedat");
        try (DataOutputStream output = new DataOutputStream(new FileOutputStream(fixture))) {
            output.write("#!AER-DAT2.0\r\n#End Of ASCII Header\r\n"
                    .getBytes(StandardCharsets.US_ASCII));
            for (int i = 0; i < 14; i++) {
                output.writeInt(0x1001 + (2 * i));
                output.writeInt(i * 10);
            }
        }
        return fixture;
    }

    private File writeBytes(String name, byte[] bytes) throws IOException {
        File fixture = temporaryFolder.newFile(name);
        try (FileOutputStream output = new FileOutputStream(fixture)) {
            output.write(bytes);
        }
        return fixture;
    }

    private static byte[] decodeHex(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(2 * i, (2 * i) + 2), 16);
        }
        return bytes;
    }

    private static AEFileInputStream open(File fixture) throws IOException {
        return new AEFileInputStream(fixture, newFixtureChip());
    }

    private static FixtureChip newFixtureChip() {
        return new ObjenesisStd().newInstance(FixtureChip.class);
    }

    /** Avoids constructing the JOGL canvas while retaining the chip hooks used by legacy I/O. */
    private static final class FixtureChip extends AEChip {

        private FixtureChip() {
        }

        @Override
        public EventExtractor2D getEventExtractor() {
            return null;
        }

        @Override
        public void setEventExtractor(EventExtractor2D eventExtractor) {
            // Raw input assertions do not need a cooked-event extractor.
        }
    }
}
