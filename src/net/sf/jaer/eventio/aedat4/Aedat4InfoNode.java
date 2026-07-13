package net.sf.jaer.eventio.aedat4;

import net.sf.jaer.chip.AEChip;

/** Builds the minimal AEDAT-4 infoNode XML consumed by DV. */
public final class Aedat4InfoNode {

    private Aedat4InfoNode() {
    }

    public static String build(AEChip chip) {
        int sx = chip == null ? 0 : chip.getSizeX();
        int sy = chip == null ? 0 : chip.getSizeY();
        String source = chip == null ? "jAER" : chip.getClass().getSimpleName();
        String escapedSource = escape(source);
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<dv version=\"2.0\">"
                + "<outInfo>"
                + stream("0", "EVTS", sx, sy, escapedSource)
                + stream("1", "FRME", sx, sy, escapedSource)
                + stream("2", "IMUS", sx, sy, escapedSource)
                + "</outInfo>"
                + "</dv>";
    }

    private static String stream(String name, String type, int sx, int sy, String source) {
        return "<node name=\"" + name + "\">"
                + "<attr key=\"typeIdentifier\" value=\"" + type + "\"/>"
                + "<attr key=\"sizeX\" value=\"" + sx + "\"/>"
                + "<attr key=\"sizeY\" value=\"" + sy + "\"/>"
                + "<attr key=\"source\" value=\"" + source + "\"/>"
                + "</node>";
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
