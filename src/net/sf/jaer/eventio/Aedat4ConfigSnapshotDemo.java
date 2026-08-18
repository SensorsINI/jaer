package net.sf.jaer.eventio;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import javax.xml.parsers.DocumentBuilderFactory;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.aedat4.Aedat4FileOutputStream;
import net.sf.jaer.eventio.aedat4.dv.CompressionType;

/**
 * Headless production-path test for freezing the recording-start configuration
 * snapshot into AEDAT-4 output (plan Todo 3, part 1).
 *
 * <p>One immutable {@link RecordingConfigurationSnapshot} captured at recording
 * start is embedded in the AEDAT-4 {@code infoNode} as exactly one
 * {@code jAERConfigSnapshot} node (schema version 1), reused verbatim for the
 * open and close IOHeader rebuild so the serialized header byte length stays
 * stable even if live preferences change after open. The three stream
 * descriptors (EVTS/FRME/IMUS) are byte-unchanged. Each check throws
 * {@link AssertionError} on mismatch (non-zero exit) and prints PASS on
 * success.
 *
 * <p>Run headlessly after {@code ant clean compile}:
 * {@code java -cp build/classes:lib/*:jars/* net.sf.jaer.eventio.Aedat4ConfigSnapshotDemo}
 */
public class Aedat4ConfigSnapshotDemo {

    public static void main(String[] args) throws Exception {
        testSnapshotRecordedAndStable();
        testEmptyAndNoSidecar();
        testDescriptorsAndClosePatchStable();
        System.out.println("ALL PASS");
    }

    /** One jAERConfigSnapshot node schema 1; 37 persists after live 41; nasty value round-trips; descriptors unchanged. */
    private static void testSnapshotRecordedAndStable() throws Exception {
        AEChip chip = bareChip(withPrefs());
        chip.getPrefs().put("AEChip.level", "37");
        chip.getPrefs().put("AEChip.nasty", "a<&\"b\n\rsecond");
        RecordingConfigurationSnapshot snap = RecordingConfigurationSnapshot.captureFromChip(chip);

        File f = File.createTempFile("jaer-aedat4-snapshot", ".aedat4");
        try (Aedat4FileOutputStream os = new Aedat4FileOutputStream(
                new FileOutputStream(f), chip, CompressionType.LZ4, snap)) {
            os.writeBundle(buildBundle(4));
        }
        // Live prefs mutate after capture; the recorded snapshot must still hold 37, never 41.
        chip.getPrefs().put("AEChip.level", "41");
        String info = RecordingChipDetector.peekAedat4InfoNodeXml(f);

        Map<String, String> entries = parseSnapshotNode(info);
        assertTrue(entries.size() >= 2, "aedat4 snapshot node has entries, got " + entries.size());
        assertTrue("37".equals(entries.get("AEChip.level")), "aedat4 recorded 37, got <" + entries.get("AEChip.level") + ">");
        assertTrue(entries.containsKey("AEChip.level") && !"41".equals(entries.get("AEChip.level")),
                "aedat4 must not record live 41");
        assertTrue("a<&\"b\n\rsecond".equals(entries.get("AEChip.nasty")), "aedat4 nasty value round-trips");
        assertOneSnapshotNode(info);
        assertDescribedStreams(info);
        Files.deleteIfExists(f.toPath());
        System.out.println("PASS testSnapshotRecordedAndStable");
    }

    /** Empty prefs yield a valid (possibly empty) snapshot node and no sidecar XML. */
    private static void testEmptyAndNoSidecar() throws Exception {
        AEChip chip = bareChip(withPrefs());
        RecordingConfigurationSnapshot empty = RecordingConfigurationSnapshot.captureFromChip(chip);
        assertTrue(empty.isEmpty(), "empty prefs yield empty snapshot");

        File dir = Files.createTempDirectory("jaer-aedat4-no-sidecar-").toFile();
        File fa4 = new File(dir, "empty.aedat4");
        try (Aedat4FileOutputStream os = new Aedat4FileOutputStream(
                new FileOutputStream(fa4), chip, CompressionType.LZ4, empty)) {
            os.writeBundle(buildBundle(1));
        }
        String info = RecordingChipDetector.peekAedat4InfoNodeXml(fa4);
        Map<String, String> emptyEntries = parseSnapshotNode(info);
        assertTrue(emptyEntries.isEmpty(), "empty snapshot yields no aedat4 entries");
        assertOneSnapshotNode(info);

        for (File sib : dir.listFiles()) {
            if (!sib.getName().equals(fa4.getName()) && sib.getName().toLowerCase().endsWith(".xml")) {
                throw new AssertionError("unexpected sidecar XML " + sib);
            }
        }
        Files.deleteIfExists(fa4.toPath());
        Files.deleteIfExists(dir.toPath());
        System.out.println("PASS testEmptyAndNoSidecar");
    }

    /** Stream descriptors keep their identities and the close-time IOHeader patch keeps a stable byte length. */
    private static void testDescriptorsAndClosePatchStable() throws Exception {
        AEChip chip = bareChip(withPrefs());
        chip.getPrefs().put("AEChip.level", "37");
        RecordingConfigurationSnapshot snap = RecordingConfigurationSnapshot.captureFromChip(chip);
        File f = File.createTempFile("jaer-aedat4-descriptors", ".aedat4");
        long openInfoLength;
        try (Aedat4FileOutputStream os = new Aedat4FileOutputStream(
                new FileOutputStream(f), chip, CompressionType.ZSTD, snap)) {
            openInfoLength = RecordingChipDetector.peekAedat4InfoNodeXml(f).length();
            os.writeBundle(buildBundle(2));
        }
        String info = RecordingChipDetector.peekAedat4InfoNodeXml(f);
        assertTrue(info.contains("<attr key=\"compression\" type=\"string\">ZSTD</attr>"), "aedat4 compression ZSTD preserved");
        assertDescribedStreams(info);
        assertTrue(openInfoLength == info.length(),
                "infoNode open/close byte length stable, was " + openInfoLength + " now " + info.length());
        Files.deleteIfExists(f.toPath());
        System.out.println("PASS testDescriptorsAndClosePatchStable");
    }

    // ------------------------------------------------------------------
    // parsing helpers
    // ------------------------------------------------------------------

    private static void assertDescribedStreams(String info) {
        List<RecordingChipDetector.StreamHint> streams = RecordingChipDetector.streamsFromInfoNodeXml(info);
        assertTrue(streams.size() >= 3, "aedat4 declares events/frames/imu, got " + streams.size());
        String[] expectedTypes = {"EVTS", "FRME", "IMUS"};
        String[] expectedOutputs = {"events", "frames", "imu"};
        for (int i = 0; i < 3; i++) {
            RecordingChipDetector.StreamHint s = streams.get(i);
            assertTrue(expectedTypes[i].equals(s.typeIdentifier), "stream " + i + " typeIdentifier " + expectedTypes[i] + " got " + s.typeIdentifier);
            assertTrue(expectedOutputs[i].equals(s.originalOutputName), "stream " + i + " outputName " + expectedOutputs[i] + " got " + s.originalOutputName);
            assertTrue("jAER".equals(s.originalModuleName), "stream " + i + " moduleName jAER got " + s.originalModuleName);
        }
    }

    private static Map<String, String> parseSnapshotNode(String xml) throws Exception {
        Map<String, String> out = new LinkedHashMap<>();
        var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        var nodes = doc.getElementsByTagName("node");
        for (int i = 0; i < nodes.getLength(); i++) {
            var node = nodes.item(i);
            String name = node.getAttributes() != null ? node.getAttributes().getNamedItem("name").getNodeValue() : null;
            if ("jAERConfigSnapshot".equals(name)) {
                for (var child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
                    if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                            && "attr".equals(child.getNodeName())) {
                        String key = child.getAttributes().getNamedItem("key").getNodeValue();
                        out.put(key, child.getTextContent());
                    }
                }
            }
        }
        return out;
    }

    private static void assertOneSnapshotNode(String xml) throws Exception {
        var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        var nodes = doc.getElementsByTagName("node");
        int count = 0;
        String sn = "";
        boolean seen = false;
        for (int i = 0; i < nodes.getLength(); i++) {
            var a = nodes.item(i).getAttributes();
            if (a != null && a.getNamedItem("name") != null
                    && "jAERConfigSnapshot".equals(a.getNamedItem("name").getNodeValue())) {
                count++;
                seen = true;
                String sv = a.getNamedItem("schema_version") != null ? a.getNamedItem("schema_version").getNodeValue() : null;
                if (sv == null) {
                    sn = "missing";
                } else {
                    sn = sv;
                }
            }
        }
        assertTrue(count == 1, "exactly one jAERConfigSnapshot node, got " + count);
        assertTrue(seen && "1".equals(sn), "jAERConfigSnapshot schema_version=1, got " + sn);
    }

    // ------------------------------------------------------------------
    // event / chip / text helpers
    // ------------------------------------------------------------------

    private static net.sf.jaer.event.PacketBundle buildBundle(int n) {
        net.sf.jaer.event.PacketBundle bundle = new net.sf.jaer.event.PacketBundle();
        bundle.add(buildEventPacket(n));
        return bundle;
    }

    private static EventPacket<PolarityEvent> buildEventPacket(int n) {
        EventPacket<PolarityEvent> events = new EventPacket<>(PolarityEvent.class);
        OutputEventIterator<PolarityEvent> out = events.outputIterator();
        for (int i = 0; i < n; i++) {
            PolarityEvent e = out.nextOutput();
            e.timestamp = 1000 + i;
            e.x = (short) (10 + i);
            e.y = (short) (20 + i);
            e.setPolarity((i & 1) == 0 ? PolarityEvent.Polarity.On : PolarityEvent.Polarity.Off);
        }
        return events;
    }

    private static AEChip bareChip(Preferences prefs) {
        AEChip chip = new org.objenesis.ObjenesisStd().newInstance(AEChip.class);
        chip.setPrefs(prefs);
        return chip;
    }

    private static Preferences withPrefs() {
        return RecordingConfigSnapshotDemo.MemoryPreferences.root();
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }
}
