package net.sf.jaer.eventio;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.prefs.Preferences;
import javax.xml.parsers.DocumentBuilderFactory;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.PotArray;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.aedat4.Aedat4Compression;
import net.sf.jaer.eventio.aedat4.Aedat4FileOutputStream;
import net.sf.jaer.eventio.aedat4.dv.CompressionType;
import net.sf.jaer.eventprocessing.DataLogger;

/**
 * Todo 5 production-path integration demo: one immutable owner snapshot is
 * supplied to legacy AEDAT, AEDZ, AEDAT-4, and DataLogger, then every generated
 * file is reopened for both metadata and event content.
 *
 * <p>The owner captures value {@code 37} with one bias flush, mutates live state
 * to {@code 41}, and writes one event packet to each format. The reopened entry
 * sets must be identical and retain 37 plus a hostile escaped value. AEDZ must
 * contain one recognizable, correctly ordered AEDAT header and one chunk;
 * AEDAT-4 must contain one event block and exactly one schema-1
 * {@code jAERConfigSnapshot} node; no sidecar may exist.
 */
public class CrossFormatMetadataDemo {

    private static final int EVENT_COUNT = 3;

    public static void main(String[] args) throws Exception {
        testSingleOwnerSnapshotAcrossFourReopenedFormats();
        System.out.println("ALL CROSS-FORMAT METADATA TESTS PASS");
    }

    private static void testSingleOwnerSnapshotAcrossFourReopenedFormats() throws Exception {
        File dir = Files.createTempDirectory("jaer-cross-format-").toFile();
        File legacyFile = new File(dir, "recording.aedat");
        File aedzFile = new File(dir, "recording.aedz");
        File aedat4File = new File(dir, "recording.aedat4");
        File dataLoggerFile = new File(dir, "recording-datalogger.aedat");
        AEChip chip = bareChip(withPrefs());
        chip.setSupport(new java.beans.PropertyChangeSupport(chip));
        String hostile = "a<&>\"b\n\rsecond";
        chip.getPrefs().put("AEChip.level", "37");
        chip.getPrefs().put("AEChip.hostile", hostile);

        final int[] flushCount = {0};
        final String[] liveBias = {"37"};
        Biasgen biasgen = new Biasgen(chip);
        biasgen.setPotArray(new PotArray(biasgen) {
            @Override
            public void storePreferences() {
                flushCount[0]++;
                chip.getPrefs().put("biasgen.live", liveBias[0]);
            }
        });
        chip.setBiasgen(biasgen);

        File[] outputs = {legacyFile, aedzFile, aedat4File, dataLoggerFile};
        try {
            RecordingConfigurationSnapshot ownerSnapshot = RecordingConfigurationSnapshot.captureFromChip(chip);
            chip.setRecordingConfigurationSnapshot(ownerSnapshot);
            assertTrue("37".equals(ownerSnapshot.get("AEChip.level")), "owner captured level 37");
            assertTrue(flushCount[0] == 1, "owner capture flushed exactly once");

            chip.getPrefs().put("AEChip.level", "41");
            liveBias[0] = "41";
            AEPacketRaw raw = makeRawPacket(EVENT_COUNT);

            try (AEDZOutputStream out = new AEDZOutputStream(
                    new FileOutputStream(aedzFile), chip, ownerSnapshot)) {
                out.writePacket(raw);
            }
            try (Aedat4FileOutputStream out = new Aedat4FileOutputStream(
                    new FileOutputStream(aedat4File), chip, CompressionType.LZ4, ownerSnapshot)) {
                out.writeBundle(buildBundle(EVENT_COUNT));
            }
            try (AEFileOutputStream out = new AEFileOutputStream(
                    new FileOutputStream(legacyFile), chip, AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT2)) {
                out.writePacket(raw);
            }

            DataLogger dataLogger = new DataLogger(chip);
            File logged = dataLogger.startLogging(dataLoggerFile.getAbsolutePath());
            assertTrue(logged != null, "DataLogger started with external owner snapshot");
            assertTrue(chip.getRecordingConfigurationSnapshot() == ownerSnapshot,
                    "DataLogger reused the exact owner object");
            dataLogger.filterPacket(buildEventPacket(EVENT_COUNT));
            dataLogger.stopLogging(false);
            assertTrue(chip.getRecordingConfigurationSnapshot() == ownerSnapshot,
                    "DataLogger stop did not stomp external ownership");
            assertTrue(flushCount[0] == 1,
                    "all four writers performed no second capture/flush, got " + flushCount[0]);

            LegacyReopen legacy = reopenLegacy(legacyFile, raw);
            AedzReopen aedz = reopenAedz(aedzFile, raw);
            Aedat4Reopen aedat4 = reopenAedat4(aedat4File);
            LegacyReopen dataLoggerReopen = reopenLegacy(dataLoggerFile, raw);

            String normalized = ownerSnapshot.serializeLegacyEntries();
            assertSameEntries(normalized, legacy.snapshot, "legacy AEDAT");
            assertSameEntries(normalized, aedz.snapshot, "AEDZ");
            assertSameEntries(normalized, aedat4.snapshot, "AEDAT-4");
            assertSameEntries(normalized, dataLoggerReopen.snapshot, "DataLogger");

            assertFrozenValues(hostile, legacy.snapshot, "legacy AEDAT");
            assertFrozenValues(hostile, aedz.snapshot, "AEDZ");
            assertFrozenValues(hostile, aedat4.snapshot, "AEDAT-4");
            assertFrozenValues(hostile, dataLoggerReopen.snapshot, "DataLogger");
            assertOneLegacyBlock(legacy.header, "legacy AEDAT");
            assertOneLegacyBlock(aedz.header, "AEDZ");
            assertOneLegacyBlock(dataLoggerReopen.header, "DataLogger");

            String aedzHeader = aedz.header;
            int preferences = aedzHeader.indexOf("Start of Preferences for this AEChip");
            assertTrue(countOccurrences(aedzHeader,
                    "#!AER-DAT" + AEDataFile.DATA_FILE_VERSION_NUMBER_AEDAT2 + "\r\n") == 1,
                    "AEDZ has exactly one embedded AEDAT-2 marker");
            assertTrue(aedzHeader.contains("This is a raw AE data file - do not edit"),
                    "AEDZ embeds a recognizable raw AEDAT header");
            assertTrue(preferences >= 0
                    && preferences < aedzHeader.indexOf(AEDataFile.DATA_START_TIME_SYSTEMCURRENT_TIME_MILLIS)
                    && preferences < aedzHeader.indexOf(AEDataFile.END_OF_HEADER_STRING),
                    "AEDZ preference block precedes data-start and end-of-header markers");
            byte[] aedzBytes = Files.readAllBytes(aedzFile.toPath());
            assertTrue(littleEndianInt(aedzBytes, 16) == 1, "AEDZ reopened one event chunk");

            assertTrue(aedat4.eventBlocks == 1,
                    "AEDAT-4 reopened exactly one EVTS block, got " + aedat4.eventBlocks);
            assertTrue(aedat4.events == EVENT_COUNT,
                    "AEDAT-4 reopened " + EVENT_COUNT + " events, got " + aedat4.events);
            assertOneSnapshotNode(aedat4.infoNodeXml);

            for (File sibling : dir.listFiles()) {
                assertTrue(!sibling.getName().toLowerCase().endsWith(".xml"),
                        "no sidecar XML: " + sibling.getName());
            }
            assertTrue(flushCount[0] == 1, "final flush count remains one");
            System.out.println("PASS four formats reopened: frozen=37 live=41 hostile-value events="
                    + EVENT_COUNT + " aedzChunks=1 aedat4Blocks=1 snapshotNodes=1 flushes=1 sidecars=0");
        } finally {
            chip.setRecordingConfigurationSnapshot(null);
            for (File output : outputs) {
                Files.deleteIfExists(output.toPath());
            }
            File[] leftovers = dir.listFiles();
            if (leftovers != null) {
                for (File leftover : leftovers) {
                    Files.deleteIfExists(leftover.toPath());
                }
            }
            Files.deleteIfExists(dir.toPath());
        }
    }

    private static LegacyReopen reopenLegacy(File file, AEPacketRaw expected) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        byte[] endMarker = ("#" + AEDataFile.END_OF_HEADER_STRING + "\r\n")
                .getBytes(StandardCharsets.UTF_8);
        int markerOffset = indexOf(bytes, endMarker);
        assertTrue(markerOffset >= 0, file.getName() + " has an AEDAT end-of-header marker");
        int dataOffset = markerOffset + endMarker.length;
        assertTrue(bytes.length - dataOffset == expected.getNumEvents() * 8,
                file.getName() + " reopened exactly " + expected.getNumEvents() + " legacy events");
        ByteBuffer data = ByteBuffer.wrap(bytes, dataOffset, bytes.length - dataOffset).order(ByteOrder.BIG_ENDIAN);
        for (int i = 0; i < expected.getNumEvents(); i++) {
            int address = data.getInt();
            int timestamp = data.getInt();
            assertTrue(address == expected.getAddresses()[i] && timestamp == expected.getTimestamps()[i],
                    file.getName() + " event " + i + " address/timestamp round-trip");
        }
        String header = new String(bytes, 0, dataOffset, StandardCharsets.UTF_8);
        return new LegacyReopen(
                RecordingConfigurationSnapshot.parseLegacyEntries(splitHeaderLines(header)), header);
    }

    private static AedzReopen reopenAedz(File file, AEPacketRaw expected) throws IOException {
        try (AEDZInputStream in = new AEDZInputStream(file)) {
            assertTrue(in.size() == expected.getNumEvents(), "AEDZ size matches written event count");
            AEPacketRaw got = in.readPacketByNumber(expected.getNumEvents());
            assertTrue(got.getNumEvents() == expected.getNumEvents(), "AEDZ actual packet reopened");
            for (int i = 0; i < expected.getNumEvents(); i++) {
                assertTrue(got.getAddresses()[i] == expected.getAddresses()[i]
                        && got.getTimestamps()[i] == expected.getTimestamps()[i],
                        "AEDZ event " + i + " address/timestamp round-trip");
            }
            String header = new String(in.getAedatHeader(), StandardCharsets.UTF_8);
            return new AedzReopen(
                    RecordingConfigurationSnapshot.parseLegacyEntries(splitHeaderLines(header)), header);
        }
    }

    private static Aedat4Reopen reopenAedat4(File file) throws Exception {
        String infoNode = RecordingChipDetector.peekAedat4InfoNodeXml(file);
        RecordingConfigurationSnapshot snapshot = snapshotFromAedat4InfoNode(infoNode);
        int eventBlocks = 0;
        int events = 0;
        try (FileInputStream input = new FileInputStream(file); FileChannel channel = input.getChannel()) {
            channel.position(Aedat4FileOutputStream.VERSION_LINE.length);
            ByteBuffer ioHeaderBuffer = readSizePrefixed(channel);
            net.sf.jaer.eventio.aedat4.dv.IOHeader ioHeader =
                    net.sf.jaer.eventio.aedat4.dv.IOHeader
                            .getSizePrefixedRootAsIOHeader(ioHeaderBuffer);
            int compression = Aedat4Compression.clamp(ioHeader.compression());
            long dataTablePosition = ioHeader.dataTablePosition();
            assertTrue(dataTablePosition >= channel.position() && dataTablePosition <= channel.size(),
                    "AEDAT-4 data table position is bounded");
            ByteBuffer packetHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            while (channel.position() + 8 <= dataTablePosition) {
                packetHeader.clear();
                readFully(channel, packetHeader);
                packetHeader.flip();
                int streamId = packetHeader.getInt();
                int size = packetHeader.getInt();
                assertTrue(size >= 0 && size <= dataTablePosition - channel.position(),
                        "AEDAT-4 packet size is bounded");
                ByteBuffer payload = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
                readFully(channel, payload);
                payload.flip();
                if (streamId == Aedat4FileOutputStream.STREAM_EVENTS) {
                    eventBlocks++;
                    byte[] compressed = new byte[payload.remaining()];
                    payload.get(compressed);
                    byte[] flat = Aedat4Compression.decompress(compressed, compression);
                    events += net.sf.jaer.eventio.aedat4.dv.EventPacket
                            .getSizePrefixedRootAsEventPacket(
                                    ByteBuffer.wrap(flat).order(ByteOrder.LITTLE_ENDIAN))
                            .elementsLength();
                }
            }
            assertTrue(channel.position() == dataTablePosition,
                    "AEDAT-4 packet scan ends exactly at its data table");
            long encodedTableSize = channel.size() - dataTablePosition;
            assertTrue(encodedTableSize > 0 && encodedTableSize <= Integer.MAX_VALUE,
                    "AEDAT-4 encoded data table size is bounded");
            ByteBuffer encodedTable = ByteBuffer.allocate((int) encodedTableSize);
            readFully(channel, encodedTable);
            byte[] encodedTableBytes = encodedTable.array();
            byte[] tableBytes = Aedat4Compression.decompress(encodedTableBytes, compression);
            ByteBuffer tableBuffer = ByteBuffer.wrap(tableBytes).order(ByteOrder.LITTLE_ENDIAN);
            net.sf.jaer.eventio.aedat4.dv.FileDataTable table =
                    net.sf.jaer.eventio.aedat4.dv.FileDataTable
                            .getSizePrefixedRootAsFileDataTable(tableBuffer);
            assertTrue(table.tableLength() == 1, "AEDAT-4 file data table records one packet block");
            assertTrue(table.table(0).packetInfoStreamID() == Aedat4FileOutputStream.STREAM_EVENTS
                    && table.table(0).numElements() == EVENT_COUNT,
                    "AEDAT-4 data table records one " + EVENT_COUNT + "-event block");
        }
        return new Aedat4Reopen(snapshot, infoNode, eventBlocks, events);
    }

    private static RecordingConfigurationSnapshot snapshotFromAedat4InfoNode(String xml) throws Exception {
        Map<String, String> entries = parseSnapshotNode(xml);
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            lines.add("<entry key=\"" + SnapshotCodec.escape(entry.getKey())
                    + "\" value=\"" + SnapshotCodec.escape(entry.getValue()) + "\"/>");
        }
        return RecordingConfigurationSnapshot.parseLegacyEntries(lines);
    }

    private static Map<String, String> parseSnapshotNode(String xml) throws Exception {
        assertTrue(xml != null, "AEDAT-4 infoNode is readable");
        Map<String, String> entries = new TreeMap<>();
        var document = newDocumentBuilderFactory().newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        var nodes = document.getElementsByTagName("node");
        for (int i = 0; i < nodes.getLength(); i++) {
            var node = nodes.item(i);
            var name = node.getAttributes() == null ? null : node.getAttributes().getNamedItem("name");
            if (name != null && "jAERConfigSnapshot".equals(name.getNodeValue())) {
                for (var child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
                    if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                            && "attr".equals(child.getNodeName())) {
                        entries.put(child.getAttributes().getNamedItem("key").getNodeValue(), child.getTextContent());
                    }
                }
            }
        }
        return entries;
    }

    private static void assertOneSnapshotNode(String xml) throws Exception {
        var document = newDocumentBuilderFactory().newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        var nodes = document.getElementsByTagName("node");
        int count = 0;
        String schema = null;
        for (int i = 0; i < nodes.getLength(); i++) {
            var attributes = nodes.item(i).getAttributes();
            if (attributes != null && attributes.getNamedItem("name") != null
                    && "jAERConfigSnapshot".equals(attributes.getNamedItem("name").getNodeValue())) {
                count++;
                schema = attributes.getNamedItem("schema_version") == null
                        ? null : attributes.getNamedItem("schema_version").getNodeValue();
            }
        }
        assertTrue(count == 1, "exactly one jAERConfigSnapshot node, got " + count);
        assertTrue("1".equals(schema), "jAERConfigSnapshot schema_version=1, got " + schema);
    }

    private static DocumentBuilderFactory newDocumentBuilderFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    private static void assertFrozenValues(
            String hostile, RecordingConfigurationSnapshot snapshot, String format) {
        assertTrue("37".equals(snapshot.get("AEChip.level")),
                format + " records owner-frozen 37, got <" + snapshot.get("AEChip.level") + ">");
        assertTrue(!"41".equals(snapshot.get("AEChip.level")), format + " never records live 41");
        assertTrue(hostile.equals(snapshot.get("AEChip.hostile")), format + " hostile value round-trips");
    }

    private static void assertSameEntries(
            String expected, RecordingConfigurationSnapshot actual, String format) {
        assertTrue(expected.equals(actual.serializeLegacyEntries()),
                format + " normalized entries differ\nexpected=" + expected
                        + "\nactual=" + actual.serializeLegacyEntries());
    }

    private static void assertOneLegacyBlock(String header, String format) {
        assertTrue(countOccurrences(header, "Start of Preferences for this AEChip") == 1,
                format + " has one preference start marker");
        assertTrue(countOccurrences(header, "#End of Preferences for this AEChip\r\n") == 1,
                format + " has exactly one preference end marker");
        assertTrue(countOccurrences(header, "key=\"AEChip.level\"") == 1,
                format + " has exactly one level entry");
    }

    private static AEPacketRaw makeRawPacket(int count) {
        AEPacketRaw packet = new AEPacketRaw(count);
        for (int i = 0; i < count; i++) {
            packet.getAddresses()[i] = 0x1200 + i;
            packet.getTimestamps()[i] = 1000 + i;
        }
        packet.setNumEvents(count);
        return packet;
    }

    private static PacketBundle buildBundle(int count) {
        PacketBundle bundle = new PacketBundle();
        bundle.add(buildEventPacket(count));
        return bundle;
    }

    private static EventPacket<PolarityEvent> buildEventPacket(int count) {
        EventPacket<PolarityEvent> events = new EventPacket<>(PolarityEvent.class);
        OutputEventIterator<PolarityEvent> output = events.outputIterator();
        for (int i = 0; i < count; i++) {
            PolarityEvent event = output.nextOutput();
            event.timestamp = 1000 + i;
            event.address = 0x1200 + i;
            event.x = (short) (10 + i);
            event.y = (short) (20 + i);
            event.setPolarity((i & 1) == 0 ? PolarityEvent.Polarity.On : PolarityEvent.Polarity.Off);
        }
        return events;
    }

    private static AEChip bareChip(Preferences preferences) {
        AEChip chip = new org.objenesis.ObjenesisStd().newInstance(AEChip.class);
        chip.setPrefs(preferences);
        return chip;
    }

    private static Preferences withPrefs() {
        return RecordingConfigSnapshotDemo.MemoryPreferences.root();
    }

    private static List<String> splitHeaderLines(String header) {
        List<String> lines = new ArrayList<>();
        for (String line : header.split("\\r?\\n")) {
            if (!line.isEmpty() && line.charAt(0) == AEDataFile.COMMENT_CHAR) {
                line = line.substring(1);
            }
            lines.add(line);
        }
        return lines;
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        for (int offset = 0; (offset = text.indexOf(needle, offset)) >= 0; offset += needle.length()) {
            count++;
        }
        return count;
    }

    private static int littleEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16)
                | ((bytes[offset + 3] & 0xff) << 24);
    }

    private static int indexOf(byte[] bytes, byte[] needle) {
        outer:
        for (int i = 0; i <= bytes.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (bytes[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static ByteBuffer readSizePrefixed(FileChannel channel) throws IOException {
        ByteBuffer sizeBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        readFully(channel, sizeBuffer);
        sizeBuffer.flip();
        int size = sizeBuffer.getInt();
        assertTrue(size >= 0 && size <= channel.size() - channel.position(), "AEDAT-4 IOHeader size is bounded");
        ByteBuffer data = ByteBuffer.allocate(size + 4).order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(size);
        readFully(channel, data);
        data.flip();
        return data;
    }

    private static void readFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                throw new EOFException("truncated file while reopening cross-format fixture");
            }
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class LegacyReopen {
        final RecordingConfigurationSnapshot snapshot;
        final String header;

        LegacyReopen(RecordingConfigurationSnapshot snapshot, String header) {
            this.snapshot = snapshot;
            this.header = header;
        }
    }

    private static final class AedzReopen {
        final RecordingConfigurationSnapshot snapshot;
        final String header;

        AedzReopen(RecordingConfigurationSnapshot snapshot, String header) {
            this.snapshot = snapshot;
            this.header = header;
        }
    }

    private static final class Aedat4Reopen {
        final RecordingConfigurationSnapshot snapshot;
        final String infoNodeXml;
        final int eventBlocks;
        final int events;

        Aedat4Reopen(
                RecordingConfigurationSnapshot snapshot, String infoNodeXml, int eventBlocks, int events) {
            this.snapshot = snapshot;
            this.infoNodeXml = infoNodeXml;
            this.eventBlocks = eventBlocks;
            this.events = events;
        }
    }
}
