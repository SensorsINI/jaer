package net.sf.jaer.eventio.aedat4;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventio.aedat4.dv.CompressionType;
import net.sf.jaer.graphics.AEChipRenderer;

/**
 * Builds AEDAT-4 {@code infoNode} XML in the same shape DV writes, so files are
 * interchangeable. Attributes use {@code <attr key="k" type="t">v</attr>}.
 */
public final class Aedat4InfoNode {

    private Aedat4InfoNode() {
    }

    public static String build(AEChip chip) {
        return build(chip, CompressionType.LZ4);
    }

    public static String build(AEChip chip, int compression) {
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
        sb.append("</node>");
        sb.append("</dv>");
        return sb.toString();
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
