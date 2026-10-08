package net.sf.jaer.eventio.aedat4;

import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * {@code imu}) the infoNode embeds {@code jAERConfigSnapshot} node(s)
 * (schema version {@code 1}) carrying the immutable recording-start
 * configuration snapshot as deterministic, escaped {@code <attr>} entries.
 * Snapshot nodes and {@code jAERRecording} (recording-site time zone) are
 * siblings of {@code outInfo} under {@code <dv>} — not children of
 * {@code outInfo}. PyPI {@code aedat} 2.2.0 parses every
 * {@code outInfo} child {@code name} as a stream id ({@code u32}) and fails
 * with {@code invalid digit found in string} on {@code jAERConfigSnapshot}.
 * Muxed files emit one snapshot node per camera
 * ({@code jAERConfigSnapshot}, {@code jAERConfigSnapshot-1}, …).
 */
public final class Aedat4InfoNode {

    /** XML node name, schema version. Kept public for reader/tests. */
    public static final String CONFIG_SNAPSHOT_NODE_NAME = "jAERConfigSnapshot";
    public static final String CONFIG_SNAPSHOT_SCHEMA_VERSION = "1";
    /**
     * Sibling of {@code outInfo} (not a numbered stream). Stores the recording
     * site {@link java.time.ZoneId} so playback can show local evening time
     * instead of converting Unix µs into the viewer's zone.
     */
    public static final String RECORDING_NODE_NAME = "jAERRecording";
    public static final String TIME_ZONE_ATTR = "timeZone";
    /**
     * Sibling of {@code outInfo}. Present only for lossy time-bin recordings.
     * {@code timeShiftBits} is the right-shift applied to Unix microsecond
     * timestamps (10 ≈ 1 ms). EVTS payloads are then LBEV, not DV FlatBuffers.
     */
    public static final String LOSSY_NODE_NAME = "jAERLossyTimeBins";
    public static final String TIME_SHIFT_ATTR = "timeShiftBits";
    public static final String COLLAPSE_POLARITIES_ATTR = "collapsePolarities";

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
        return build(chip, compression, snapshot, null);
    }

    public static String build(AEChip chip, int compression, RecordingConfigurationSnapshot snapshot,
            ZoneId recordingTimeZone) {
        int sx = chip == null ? 0 : chip.getSizeX();
        int sy = chip == null ? 0 : chip.getSizeY();
        String source = chip == null ? "jAER" : chip.getClass().getSimpleName();
        Integer colorFilter = colorFilterForChip(chip);
        return buildStreams(compression, new StreamSpec[]{
            new StreamSpec("0", "EVTS", "events", "Array of events (polarity ON/OFF).", sx, sy, source, colorFilter),
            new StreamSpec("1", "FRME", "frames", "Standard frame (8-bit image).", sx, sy, source, null),
            new StreamSpec("2", "IMUS", "imu", "Inertial Measurement Unit data samples.", sx, sy, source, null)
        }, new RecordingConfigurationSnapshot[]{snapshot}, recordingTimeZone);
    }

    /**
     * Muxed cameras: camera {@code i} uses stream IDs {@code 3i}/{@code 3i+1}/{@code 3i+2}.
     * Geometry and {@code source} come from the frozen tracks (not live chip size).
     */
    public static String build(java.util.List<Aedat4CameraTrack> tracks, int compression) {
        return build(tracks, compression, null);
    }

    public static String build(java.util.List<Aedat4CameraTrack> tracks, int compression, ZoneId recordingTimeZone) {
        return build(tracks, compression, recordingTimeZone, Aedat4LossyTimeBins.SHIFT_OFF);
    }

    /**
     * @param lossyTimeShift {@link Aedat4LossyTimeBins#SHIFT_OFF} for DV event
     *                       packets, otherwise the right-shift stored in
     *                       {@code jAERLossyTimeBins}
     */
    public static String build(java.util.List<Aedat4CameraTrack> tracks, int compression, ZoneId recordingTimeZone,
            int lossyTimeShift) {
        return build(tracks, compression, recordingTimeZone, lossyTimeShift, false);
    }

    public static String build(java.util.List<Aedat4CameraTrack> tracks, int compression, ZoneId recordingTimeZone,
            int lossyTimeShift, boolean collapsePolarities) {
        if (tracks == null || tracks.isEmpty()) {
            return buildStreams(compression, new StreamSpec[]{
                new StreamSpec("0", "EVTS", "events", "Array of events (polarity ON/OFF).", 0, 0, "jAER", null),
                new StreamSpec("1", "FRME", "frames", "Standard frame (8-bit image).", 0, 0, "jAER", null),
                new StreamSpec("2", "IMUS", "imu", "Inertial Measurement Unit data samples.", 0, 0, "jAER", null)
            }, new RecordingConfigurationSnapshot[]{null}, recordingTimeZone, lossyTimeShift, collapsePolarities);
        }
        if (tracks.size() == 1) {
            Aedat4CameraTrack t = tracks.get(0);
            String xml = build(t.chip, compression, t.snapshot, recordingTimeZone);
            if (lossyTimeShift < 0) {
                return xml;
            }
            int end = xml.lastIndexOf("</dv>");
            if (end < 0) {
                return xml;
            }
            StringBuilder sb = new StringBuilder(xml.length() + 96);
            sb.append(xml, 0, end);
            appendLossyNode(sb, lossyTimeShift, collapsePolarities);
            sb.append("</dv>");
            return sb.toString();
        }
        java.util.List<StreamSpec> specs = new java.util.ArrayList<>(tracks.size() * 3);
        RecordingConfigurationSnapshot[] snaps = new RecordingConfigurationSnapshot[tracks.size()];
        for (Aedat4CameraTrack t : tracks) {
            specs.add(new StreamSpec(Integer.toString(t.eventsStreamId()), "EVTS", "events",
                    "Array of events (polarity ON/OFF).", t.sizeX, t.sizeY, t.source, t.colorFilter));
            specs.add(new StreamSpec(Integer.toString(t.framesStreamId()), "FRME", "frames",
                    "Standard frame (8-bit image).", t.sizeX, t.sizeY, t.source, null));
            specs.add(new StreamSpec(Integer.toString(t.imuStreamId()), "IMUS", "imu",
                    "Inertial Measurement Unit data samples.", t.sizeX, t.sizeY, t.source, null));
            snaps[t.index] = t.snapshot;
        }
        return buildStreams(compression, specs.toArray(new StreamSpec[0]), snaps, recordingTimeZone,
                lossyTimeShift, collapsePolarities);
    }

    private static String buildStreams(int compression, StreamSpec[] streams,
            RecordingConfigurationSnapshot[] snapshots, ZoneId recordingTimeZone) {
        return buildStreams(compression, streams, snapshots, recordingTimeZone, Aedat4LossyTimeBins.SHIFT_OFF);
    }

    private static String buildStreams(int compression, StreamSpec[] streams,
            RecordingConfigurationSnapshot[] snapshots, ZoneId recordingTimeZone, int lossyTimeShift) {
        return buildStreams(compression, streams, snapshots, recordingTimeZone, lossyTimeShift, false);
    }

    private static String buildStreams(int compression, StreamSpec[] streams,
            RecordingConfigurationSnapshot[] snapshots, ZoneId recordingTimeZone, int lossyTimeShift,
            boolean collapsePolarities) {
        String compressionName = Aedat4Compression.nameOf(Aedat4Compression.clamp(compression));
        StringBuilder sb = new StringBuilder(1024);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<dv version=\"2.0\">");
        sb.append("<node name=\"outInfo\">");
        for (StreamSpec s : streams) {
            appendStream(sb, s.name, s.typeId, s.outputName, s.typeDescription,
                    compressionName, s.sx, s.sy, s.source, s.colorFilter);
        }
        sb.append("</node>");
        if (snapshots != null) {
            for (int i = 0; i < snapshots.length; i++) {
                appendConfigSnapshotNode(sb, snapshotNodeName(i), snapshots[i]);
            }
        }
        appendRecordingNode(sb, recordingTimeZone);
        appendLossyNode(sb, lossyTimeShift, collapsePolarities);
        sb.append("</dv>");
        return sb.toString();
    }

    private static void appendLossyNode(StringBuilder sb, int lossyTimeShift, boolean collapsePolarities) {
        if (lossyTimeShift < 0) {
            return;
        }
        sb.append("<node name=\"").append(LOSSY_NODE_NAME).append("\">");
        attr(sb, TIME_SHIFT_ATTR, "int", Integer.toString(Aedat4LossyTimeBins.clampShift(lossyTimeShift)));
        if (collapsePolarities) {
            attr(sb, COLLAPSE_POLARITIES_ATTR, "bool", "1");
        }
        sb.append("</node>");
    }

    /**
     * Right-shift from {@code jAERLossyTimeBins}, or {@link Aedat4LossyTimeBins#SHIFT_OFF}
     * when the recording is ordinary DV events.
     */
    public static int parseLossyTimeShiftBits(String infoNode) {
        if (infoNode == null || infoNode.isEmpty()) {
            return Aedat4LossyTimeBins.SHIFT_OFF;
        }
        Pattern node = Pattern.compile(
                "<node\\s+name\\s*=\\s*\"" + Pattern.quote(LOSSY_NODE_NAME) + "\"[^>]*>(.*?)</node>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher nm = node.matcher(infoNode);
        if (!nm.find()) {
            return Aedat4LossyTimeBins.SHIFT_OFF;
        }
        Pattern attrPat = Pattern.compile(
                "<attr\\s+key\\s*=\\s*\"" + Pattern.quote(TIME_SHIFT_ATTR)
                        + "\"[^>]*>([^<]*)</attr>",
                Pattern.CASE_INSENSITIVE);
        Matcher am = attrPat.matcher(nm.group(1));
        if (!am.find()) {
            return Aedat4LossyTimeBins.SHIFT_DEFAULT;
        }
        try {
            return Aedat4LossyTimeBins.clampShift(Integer.parseInt(am.group(1).trim()));
        } catch (NumberFormatException e) {
            return Aedat4LossyTimeBins.SHIFT_DEFAULT;
        }
    }

    /** True when the lossy node asks playback to ignore event order inside a bin. */
    public static boolean parseLossyCollapsePolarities(String infoNode) {
        if (infoNode == null || infoNode.isEmpty()) {
            return false;
        }
        Pattern node = Pattern.compile(
                "<node\\s+name\\s*=\\s*\"" + Pattern.quote(LOSSY_NODE_NAME) + "\"[^>]*>(.*?)</node>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher nm = node.matcher(infoNode);
        if (!nm.find()) {
            return false;
        }
        Pattern attrPat = Pattern.compile(
                "<attr\\s+key\\s*=\\s*\"" + Pattern.quote(COLLAPSE_POLARITIES_ATTR)
                        + "\"[^>]*>([^<]*)</attr>",
                Pattern.CASE_INSENSITIVE);
        Matcher am = attrPat.matcher(nm.group(1));
        if (!am.find()) {
            return false;
        }
        String v = am.group(1).trim();
        return "1".equals(v) || "true".equalsIgnoreCase(v);
    }

    private static void appendRecordingNode(StringBuilder sb, ZoneId recordingTimeZone) {
        ZoneId zone = recordingTimeZone != null ? recordingTimeZone : ZoneId.systemDefault();
        sb.append("<node name=\"").append(RECORDING_NODE_NAME).append("\">");
        attr(sb, TIME_ZONE_ATTR, "string", zone.getId());
        sb.append("</node>");
    }

    /**
     * Recording-site zone from a jAER {@code jAERRecording} infoNode, or
     * {@code null} for DV files that omit it.
     */
    public static ZoneId parseRecordingTimeZone(String infoNode) {
        if (infoNode == null || infoNode.isEmpty()) {
            return null;
        }
        Pattern node = Pattern.compile(
                "<node\\s+name\\s*=\\s*\"" + Pattern.quote(RECORDING_NODE_NAME) + "\"[^>]*>(.*?)</node>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher nm = node.matcher(infoNode);
        if (!nm.find()) {
            return null;
        }
        Pattern attrPat = Pattern.compile(
                "<attr\\s+key\\s*=\\s*\"" + Pattern.quote(TIME_ZONE_ATTR)
                        + "\"[^>]*>([^<]*)</attr>",
                Pattern.CASE_INSENSITIVE);
        Matcher am = attrPat.matcher(nm.group(1));
        if (!am.find()) {
            return null;
        }
        String id = am.group(1).trim();
        if (id.isEmpty()) {
            return null;
        }
        try {
            return ZoneId.of(id);
        } catch (Exception e) {
            return null;
        }
    }

    private static String snapshotNodeName(int cameraIndex) {
        return cameraIndex == 0 ? CONFIG_SNAPSHOT_NODE_NAME : CONFIG_SNAPSHOT_NODE_NAME + "-" + cameraIndex;
    }

    private static final class StreamSpec {
        final String name;
        final String typeId;
        final String outputName;
        final String typeDescription;
        final int sx;
        final int sy;
        final String source;
        final Integer colorFilter;

        StreamSpec(String name, String typeId, String outputName, String typeDescription,
                int sx, int sy, String source, Integer colorFilter) {
            this.name = name;
            this.typeId = typeId;
            this.outputName = outputName;
            this.typeDescription = typeDescription;
            this.sx = sx;
            this.sy = sy;
            this.source = source;
            this.colorFilter = colorFilter;
        }
    }

    /**
     * Append exactly one {@code <node name="jAERConfigSnapshot" schema_version="1">}
     * with one deterministic, escaped {@code <attr>} per snapshot entry, in key
     * order. Entries are escaped with {@link SnapshotCodec} (including line
     * breaks) so any preference value round-trips exactly.
     */
    private static void appendConfigSnapshotNode(StringBuilder sb, RecordingConfigurationSnapshot snapshot) {
        appendConfigSnapshotNode(sb, CONFIG_SNAPSHOT_NODE_NAME, snapshot);
    }

    private static void appendConfigSnapshotNode(StringBuilder sb, String nodeName,
            RecordingConfigurationSnapshot snapshot) {
        sb.append("<node name=\"").append(escape(nodeName))
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
    public static Integer colorFilterForChip(AEChip chip) {
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
