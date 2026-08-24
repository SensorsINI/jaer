package net.sf.jaer.eventio;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

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

/**
 * Acceptance coverage for exclusive OUT positions in legacy AEDAT-2 streams.
 */
public class AEFileInputStreamBoundaryTest {

    private static final int[] THREE_ADDRESSES = {0x101, 0x202, 0x303};
    private static final int[] THREE_TIMESTAMPS = {100, 200, 300};

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void defaultOutReturnsTheOnlyEventAndFinalTimestamp() throws IOException {
        File fixture = writeFixture("one-event.aedat", new int[]{0x101}, new int[]{100});

        try (AEFileInputStream input = open(fixture)) {
            input.setRepeat(false);

            AEPacketRaw actual = input.readPacketByNumber(1);

            assertPacketEquals("default OUT for one-event file", new int[]{0x101}, new int[]{100}, actual);
            assertEquals("one-event final timestamp", 100, input.getLastTimestamp());
            assertEquals("default OUT is the exclusive stream size", 1L, input.getMarkOutPosition());
        }
    }

    @Test
    public void defaultOutReturnsAllThreeEventsAndFinalTimestamp() throws IOException {
        File fixture = writeFixture("three-events-default.aedat", THREE_ADDRESSES, THREE_TIMESTAMPS);

        try (AEFileInputStream input = open(fixture)) {
            input.setRepeat(false);

            AEPacketRaw actual = input.readPacketByNumber(THREE_ADDRESSES.length);

            assertPacketEquals("default OUT for three-event file", THREE_ADDRESSES, THREE_TIMESTAMPS, actual);
            assertEquals("three-event final timestamp", 300, input.getLastTimestamp());
            assertEquals("default OUT is the exclusive stream size", 3L, input.getMarkOutPosition());
        }
    }

    @Test
    public void explicitOutAtSizeReturnsAllEvents() throws IOException {
        File fixture = writeFixture("three-events-out-size.aedat", THREE_ADDRESSES, THREE_TIMESTAMPS);

        try (AEFileInputStream input = open(fixture)) {
            input.setRepeat(false);
            setExplicitOut(input, input.size());

            AEPacketRaw actual = input.readPacketByNumber(THREE_ADDRESSES.length);

            assertPacketEquals("explicit OUT=size", THREE_ADDRESSES, THREE_TIMESTAMPS, actual);
            assertEquals("explicit OUT remains at size", 3L, input.getMarkOutPosition());
        }
    }

    @Test
    public void explicitOutAtTwoReturnsExactlyFirstTwoEvents() throws IOException {
        File fixture = writeFixture("three-events-out-two.aedat", THREE_ADDRESSES, THREE_TIMESTAMPS);

        try (AEFileInputStream input = open(fixture)) {
            input.setRepeat(false);
            setExplicitOut(input, 2);

            AEPacketRaw actual = input.readPacketByNumber(THREE_ADDRESSES.length);

            assertPacketEquals("explicit OUT=2", Arrays.copyOf(THREE_ADDRESSES, 2),
                    Arrays.copyOf(THREE_TIMESTAMPS, 2), actual);
            assertEquals("explicit OUT remains at two", 2L, input.getMarkOutPosition());
        }
    }

    @Test
    public void emptyFileReadsEmptyWithExclusiveOutAtZero() throws IOException {
        File fixture = writeFixture("zero-events.aedat", new int[0], new int[0]);
        AEFileInputStream opened;
        try {
            opened = open(fixture);
        } catch (IOException e) {
            fail("empty AEDAT-2 fixture must open with exclusive OUT zero: " + e.getMessage());
            return;
        }

        try (AEFileInputStream input = opened) {
            input.setRepeat(false);

            assertEquals("empty stream size", 0L, input.size());
            assertEquals("empty stream exclusive OUT", 0L, input.getMarkOutPosition());
            assertEquals("empty stream read", 0, input.readPacketByNumber(1).getNumEvents());
        }
    }

    private File writeFixture(String name, int[] addresses, int[] timestamps) throws IOException {
        File fixture = temporaryFolder.newFile(name);
        try (AEFileOutputStream output = new AEFileOutputStream(new FileOutputStream(fixture),
                newFixtureChip(), AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT2)) {
            output.writePacket(new AEPacketRaw(addresses, timestamps));
        }
        return fixture;
    }

    private static AEFileInputStream open(File fixture) throws IOException {
        return new AEFileInputStream(fixture, newFixtureChip());
    }

    private static void setExplicitOut(AEFileInputStream input, long out) {
        input.position(out);
        input.setMarkOut();
        input.position(0);
    }

    private static void assertPacketEquals(String message, int[] expectedAddresses,
            int[] expectedTimestamps, AEPacketRaw actual) {
        assertEquals(message + " event count", expectedAddresses.length, actual.getNumEvents());
        assertArrayEquals(message + " addresses", expectedAddresses,
                Arrays.copyOf(actual.getAddresses(), actual.getNumEvents()));
        assertArrayEquals(message + " timestamps", expectedTimestamps,
                Arrays.copyOf(actual.getTimestamps(), actual.getNumEvents()));
    }

    private static FixtureChip newFixtureChip() {
        return new ObjenesisStd().newInstance(FixtureChip.class);
    }

    /** Avoids constructing the JOGL canvas while retaining the public chip hooks used by legacy I/O. */
    private static final class FixtureChip extends AEChip {

        private FixtureChip() {
        }

        @Override
        public EventExtractor2D getEventExtractor() {
            return null;
        }

        @Override
        public void setEventExtractor(EventExtractor2D eventExtractor) {
            // No extractor is needed for raw AEDAT-2 boundary tests.
        }

        @Override
        public void writeAdditionalAEFileOutputStreamHeader(AEFileOutputStream output) throws IOException {
            output.writeHeaderLine(" AEChip: " + FixtureChip.class.getName());
        }
    }
}
