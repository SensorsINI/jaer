package net.sf.jaer.eventio;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objenesis.ObjenesisStd;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.EventExtractor2D;

/** Regression coverage for a valid timestamp-zero event in an AEDAT-3 packet. */
public class AEFileInputStreamAedat3ZeroTimestampTest {

    private static final byte[] FIXTURE = decodeHex(
            "23214145522d444154332e310d0a23456e64204f66204153434949204865616465720d0a"
            + "01000000080000000400000000000000050000000500000003000000"
            + "03000200000000000400040005000000050006000a000000070008001400000008000a0019000000");

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void preservesFirstValidTimestampZeroInAedat31Packet() throws IOException {
        File fixture = temporaryFolder.newFile("zero-timestamp-aedat3.aedat");
        try (FileOutputStream output = new FileOutputStream(fixture)) {
            output.write(FIXTURE);
        }

        try (AEFileInputStream input = new AEFileInputStream(fixture, newFixtureChip())) {
            input.setRepeat(false);
            AEPacketRaw actual = input.readPacketByNumber(3);

            assertEquals(3, actual.getNumEvents());
            assertArrayEquals(new int[]{0x00020003, 0x00060005, 0x00080007},
                    Arrays.copyOf(actual.getAddresses(), actual.getNumEvents()));
            assertArrayEquals(new int[]{0, 10, 20},
                    Arrays.copyOf(actual.getTimestamps(), actual.getNumEvents()));
            assertEquals(0, input.getFirstTimestamp());
        }
    }

    private static byte[] decodeHex(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(2 * i, (2 * i) + 2), 16);
        }
        return bytes;
    }

    private static FixtureChip newFixtureChip() {
        return new ObjenesisStd().newInstance(FixtureChip.class);
    }

    private static final class FixtureChip extends AEChip {

        private FixtureChip() {
        }

        @Override
        public EventExtractor2D getEventExtractor() {
            return null;
        }

        @Override
        public void setEventExtractor(EventExtractor2D eventExtractor) {
        }
    }
}
