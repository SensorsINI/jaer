package net.sf.jaer.eventio.aedat4;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventio.RecordingConfigurationSnapshot;
import net.sf.jaer.eventio.SnapshotCodec;
import net.sf.jaer.eventio.aedat4.dv.CompressionType;
import net.sf.jaer.graphics.AEChipRenderer;

/**
 * Builds AEDAT-4 {@code infoNode} XML in the same shape DV writes, so files are
 * interchangeable. Attributes use {@code <attr key="k" type="t">v</attr>}.
 *
 * <p>Alongside the jAER stream descriptors ({@code events}, {@code frames},
 * {@code imu}) the infoNode embeds exactly one {@code jAERConfigSnapshot} node
 * (schema version {@code 1}) carrying the immutable recording-start
 * configuration snapshot as deterministic, escaped {@code <attr>} entries. DV
 * and jAER ignore the non-numeric node safely while parsing the numbered
 * stream nodes.
 */
public final class Aedat4InfoNode {

    /** XML node name, schema version. Kept public for reader/tests. */
    public static final String CONFIG_SNAPSHOT_NODE_NAME = "jAERConfigSnapshot";
    public static final String CONFIG_SNAPSHOT_SCHEMA_VERSION = "1";

    private Aedat4InfoNode() {
    }

    public static String build(AEChip chip) {
        return build(chip, CompressionType.LZ4);
    }

    public static String build(AEChip chip, int compression) {
        return build(chip, compression, null);
    }

    /**
     * Build the infoNode with an explicit recording snapshot. The snapshot is
     * reused verbatim for the open and close IOHeader rebuild so the serialized
     * header size is stable even if live preferences change after open. A
     * {@code null} snapshot still emits the (empty) config node so output stays
     * deterministic.
     *
     * @param chip the chip providing geometry/source/color metadata (may be null)
     * @param compression the AEDAT-4 compression to declare
     * @param snapshot the frozen recording-start configuration, or {@code null}
     * @return the complete infoNode XML string
     */
    public static String build(AEChip chip, int compression, RecordingConfigurationSnapshot snapshot) {
        int sx = chip == null ? 0 : chip.getSizeX();
        int sy = chip == null ? 0 : chip.getSizeY();
        String source = chip == null ? "jAER" : chip.getClass().getSimpleName();
        String compressionName = Aedat4Compression.nameOf(Aedat4Compression.clamp(compression));
        Integer colorFilter = colorFilterForChip(chip);
        StringBuilder sb = new StringBuilder(1024);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<dv version=\"2.0\">");
        sb.append("<node name=\"outInfo\">");
        appendStream(sb, "0", "EVTS", "events", "Array of events (polarity ON/OFF).",
                compressionName, sx, sy, source, colorFilter);
        appendStream(sb, "1", "FRME", "frames", "Standard frame (8-bit image).",
                compressionName, sx, sy, source, null);
        appendStream(sb, "2", "IMUS", "imu", "Inertial Measurement Unit data samples.",
                compressionName, sx, sy, source, null);
        appendConfigSnapshotNode(sb, snapshot);
        sb.append("</node>");
        sb.append("</dv>");
        return sb.toString();
    }

    /**
     * Append exactly one {@code <node name="jAERConfigSnapshot" schema_version="1">}
     * with one deterministic, escaped {@code <attr>} per snapshot entry, in key
     * order. Entries are escaped with {@link SnapshotCodec} (including line
     * breaks) so any preference value round-trips exactly.
     */
    private static void appendConfigSnapshotNode(StringBuilder sb, RecordingConfigurationSnapshot snapshot) {
        sb.append("<node name=\"").append(CONFIG_SNAPSHOT_NODE_NAME)
                .append("\" schema_version=\"").append(CONFIG_SNAPSHOT_SCHEMA_VERSION).append("\">");
        if (snapshot != null) {
            for (SnapshotCodec.Entry e : snapshot.entries()) {
                sb.append("<attr key=\"").append(SnapshotCodec.escape(e.getKey()))
                        .append("\" type=\"string\">")
                        .append(SnapshotCodec.escape(e.getValue()))
                        .append("</attr>");
            }
        }
        sb.append("</node>");
    }

    private static void appendStream(StringBuilder sb, String name, String typeId, String outputName,
            String typeDescription, String compression, int sx, int sy, String source, Integer colorFilter) {
        sb.append("<node name=\"").append(name).append("\">");
        attr(sb, "compression", "string", compression);
        attr(sb, "originalModuleName", "string", "jAER");
        attr(sb, "originalOutputName", "string", outputName);
        attr(sb, "typeDescription", "string", typeDescription);
        attr(sb, "typeIdentifier", "string", typeId);
        sb.append("<node name=\"info\">");
        if (colorFilter != null) {
            attr(sb, "colorFilter", "int", Integer.toString(colorFilter));
        }
        if (sx > 0) {
            attr(sb, "sizeX", "int", Integer.toString(sx));
        }
        if (sy > 0) {
            attr(sb, "sizeY", "int", Integer.toString(sy));
        }
        attr(sb, "source", "string", source);
        sb.append("</node>");
        sb.append("</node>");
    }

    private static void attr(StringBuilder sb, String key, String type, String value) {
        sb.append("<attr key=\"").append(escape(key)).append("\" type=\"").append(type).append("\">")
                .append(escape(value)).append("</attr>");
    }

    /**
     * DV {@code colorFilter}: 0=RGBG, 1=GRGB, 2=GBGR, 3=BGRG. Omitted for mono.
     * jAER color Davis chips use a Bayer CFA; we emit 0 as a generic color marker.
     */
    private static Integer colorFilterForChip(AEChip chip) {
        if (chip == null) {
            return null;
        }
        String simple = chip.getClass().getSimpleName().toLowerCase();
        if (simple.contains("color") || simple.contains("rgb")) {
            return 0;
        }
        AEChipRenderer renderer = chip.getRenderer();
        if (renderer != null && renderer.getClass().getSimpleName().toLowerCase().contains("color")) {
            return 0;
        }
        return null;
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
