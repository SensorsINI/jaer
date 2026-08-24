package net.sf.jaer.eventprocessing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventio.RecordingConfigurationSnapshot;

/** Acceptance tests for DataLogger's AEDAT-2 encoded-byte file limit. */
public class DataLoggerByteLimitTest {

    private static final long ONE_MIB = 1L << 20;
    private static final int AEDAT2_BYTES_PER_EVENT = 8;
    private static final int EVENTS_PER_PACKET = 8192;
    private static final long PACKET_ENCODED_BYTES = (long) EVENTS_PER_PACKET * AEDAT2_BYTES_PER_EVENT;
    private static final int PACKETS_TO_CROSS_ONE_MIB = (int) (ONE_MIB / PACKET_ENCODED_BYTES) + 1;

    private Path tempDir;
    private AEChip chip;
    private DataLogger logger;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("jaer-datalogger-byte-limit-");
        chip = new org.objenesis.ObjenesisStd().newInstance(AEChip.class);
        chip.setPrefs(MemoryPreferences.root());
        chip.setSupport(new PropertyChangeSupport(chip));

        logger = new DataLogger(chip);
        logger.setRecordingFolder(tempDir.toString());
        logger.setFilenameTimestampEnabled(false);
        logger.setLogFileBaseName("byte-limit");
        logger.setRotatePeriod(4);
    }

    @After
    public void tearDown() throws Exception {
        if (logger != null && booleanField(logger, "recordingEnabled")) {
            logger.stopRecording(false);
        }
        if (chip != null) {
            chip.setRecordingConfigurationSnapshot(null);
        }
        if (tempDir != null && Files.exists(tempDir)) {
            try (Stream<Path> paths = Files.walk(tempDir)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    @Test
    public void countsEightEncodedBytesPerAedat2Event() throws Exception {
        File output = tempDir.resolve("one-event.aedat").toFile();
        assertEquals(output, logger.startRecording(output.getAbsolutePath()));

        logger.filterPacket(packet(1));

        assertEquals("one AEDAT-2 address/timestamp pair is eight encoded bytes",
                AEDAT2_BYTES_PER_EVENT, longField(logger, "bytesWritten"));
    }

    @Test
    public void stopsAtOneMibWithinOnePacketAllowance() throws Exception {
        logger.setMaxLogFileSizeMB(1);
        File output = tempDir.resolve("one-mib.aedat").toFile();
        assertEquals(output, logger.startRecording(output.getAbsolutePath()));
        EventPacket<BasicEvent> packet = packet(EVENTS_PER_PACKET);

        for (int i = 0; i < PACKETS_TO_CROSS_ONE_MIB; i++) {
            logger.filterPacket(packet);
        }

        long bytesWritten = longField(logger, "bytesWritten");
        assertFalse("recording must stop after the packet that crosses one MiB",
                booleanField(logger, "recordingEnabled"));
        assertTrue("the byte limit must not stop before one MiB: " + bytesWritten,
                bytesWritten > ONE_MIB);
        assertTrue("the byte limit may overshoot by at most one packet: " + bytesWritten,
                bytesWritten <= ONE_MIB + PACKET_ENCODED_BYTES);
    }

    @Test
    public void successfulExplicitStartResetsByteCounter() throws Exception {
        setLongField(logger, "bytesWritten", 12345L);
        File output = tempDir.resolve("explicit-start.aedat").toFile();

        assertEquals(output, logger.startRecording(output.getAbsolutePath()));

        assertEquals("a successful startRecording(String) begins a new encoded-byte count",
                0L, longField(logger, "bytesWritten"));
    }

    @Test
    public void rotationResetsCounterAndPreservesExternalSnapshotOwnership() throws Exception {
        RecordingConfigurationSnapshot ownerSnapshot = RecordingConfigurationSnapshot.captureFromChip(chip);
        chip.setRecordingConfigurationSnapshot(ownerSnapshot);
        logger.setMaxLogFileSizeMB(1);
        logger.setRotateFilesEnabled(true);

        File firstFile = logger.startRecording();
        assertNotNull(firstFile);
        assertSame(ownerSnapshot, chip.getRecordingConfigurationSnapshot());
        EventPacket<BasicEvent> packet = packet(EVENTS_PER_PACKET);
        for (int i = 0; i < PACKETS_TO_CROSS_ONE_MIB; i++) {
            logger.filterPacket(packet);
        }

        assertTrue("rotation must leave the replacement recording active",
                booleanField(logger, "recordingEnabled"));
        assertNotEquals("crossing the limit must rotate to a replacement file",
                firstFile, objectField(logger, "recordingFile"));
        assertEquals("the replacement recording starts with a zero byte counter",
                0L, longField(logger, "bytesWritten"));
        assertSame("rotation must preserve the external snapshot on the chip",
                ownerSnapshot, chip.getRecordingConfigurationSnapshot());
        assertSame("the replacement writer must reuse the external snapshot by identity",
                ownerSnapshot, objectField(logger, "activeRecordingSnapshot"));

        assertNotNull(logger.stopRecording(false));
        assertSame("stopping after rotation must leave release to the external owner",
                ownerSnapshot, chip.getRecordingConfigurationSnapshot());
    }

    private static EventPacket<BasicEvent> packet(int eventCount) {
        EventPacket<BasicEvent> packet = new EventPacket<>(BasicEvent.class);
        OutputEventIterator<BasicEvent> output = packet.outputIterator();
        for (int i = 0; i < eventCount; i++) {
            BasicEvent event = output.nextOutput();
            event.address = i + 1;
            event.timestamp = 1000 + i;
        }
        return packet;
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = DataLogger.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static long longField(DataLogger logger, String name) throws ReflectiveOperationException {
        return field(name).getLong(logger);
    }

    private static void setLongField(DataLogger logger, String name, long value) throws ReflectiveOperationException {
        field(name).setLong(logger, value);
    }

    private static boolean booleanField(DataLogger logger, String name) throws ReflectiveOperationException {
        return field(name).getBoolean(logger);
    }

    private static Object objectField(DataLogger logger, String name) throws ReflectiveOperationException {
        return field(name).get(logger);
    }

    /** In-memory preferences keep this test isolated from the user's jAER settings. */
    private static final class MemoryPreferences extends AbstractPreferences {
        private final TreeMap<String, String> values = new TreeMap<>();

        private MemoryPreferences(AbstractPreferences parent, String name) {
            super(parent, name);
        }

        static MemoryPreferences root() {
            return new MemoryPreferences(null, "");
        }

        @Override
        protected void putSpi(String key, String value) {
            values.put(key, value);
        }

        @Override
        protected String getSpi(String key) {
            return values.get(key);
        }

        @Override
        protected void removeSpi(String key) {
            values.remove(key);
        }

        @Override
        protected void removeNodeSpi() throws BackingStoreException {
        }

        @Override
        protected String[] keysSpi() {
            return values.keySet().toArray(String[]::new);
        }

        @Override
        protected String[] childrenNamesSpi() {
            return new String[0];
        }

        @Override
        protected AbstractPreferences childSpi(String name) {
            return new MemoryPreferences(this, name);
        }

        @Override
        protected void syncSpi() throws BackingStoreException {
        }

        @Override
        protected void flushSpi() throws BackingStoreException {
        }
    }
}
