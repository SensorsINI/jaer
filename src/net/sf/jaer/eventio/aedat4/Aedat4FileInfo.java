package net.sf.jaer.eventio.aedat4;

import java.awt.FontMetrics;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.jaer.eventio.aedat4.dv.CompressionType;
import net.sf.jaer.util.EngineeringFormat;

/**
 * Rows for File → Show file info, plus the legacy one-block summary
 * ({@link Aedat4FileInputStream#getFileInfo()}) used by Save As and logs.
 */
public final class Aedat4FileInfo {

    /** Shown when the index has on-disk payload sizes but not codec content sizes. */
    public static final String RATIO_UNAVAILABLE = "unavailable";
    /** Shown for the uncompressed-payload row in that same case. */
    public static final String UNCOMPRESSED_NOT_IN_HEADERS = "not in packet headers";
    static final String RATIO_TIP_UNAVAILABLE =
            "Uncompressed size sits in each packet's codec header. File info does not read those headers,"
                    + " so the ratio is known only for uncompressed AEDAT-4 (or when the sizes were already equal).";

    private static final Pattern COUNTS = Pattern.compile(
            "^AEDAT-4\\s+(\\S+):\\s+(\\S+)\\s+\\(([\\d,]+)\\)\\s+events,"
                    + "\\s+(\\S+)\\s+\\(([\\d,]+)\\)\\s+frames,"
                    + "\\s+(\\S+)\\s+\\(([\\d,]+)\\)\\s+IMU samples,\\s+duration=(.+)$");
    private static final Pattern STREAM = Pattern.compile(
            "^Stream\\s+(\\d+)(?:\\s+\\((.+)\\))?,\\s+([\\d,]+)\\s+EVTS packets indexed$");
    private static final Pattern PAY_BOTH = Pattern.compile(
            "^Payloads: (.+) uncompressed -> (.+) compressed \\(([^,]+), (\\d+)% of uncompressed, ([\\d.]+):1\\)$");
    private static final Pattern PAY_RAW = Pattern.compile(
            "^Payloads: (.+) uncompressed \\((.+)\\)$");
    private static final Pattern PAY_COMP = Pattern.compile(
            "^Payloads: (.+) compressed \\(([^)]+)\\)(.*)$");

    private final String summaryText;
    private final List<Row> rows;

    private Aedat4FileInfo(String summaryText, List<Row> rows) {
        this.summaryText = summaryText;
        this.rows = List.copyOf(rows);
    }

    /** Same characters as the historical File info text block. */
    public String toSummaryText() {
        return summaryText;
    }

    public List<Row> rows() {
        return rows;
    }

    public Section asSection(String title) {
        return new Section(title, rows);
    }

    public String value(String property) {
        return valueOf(rows, property);
    }

    /**
     * Snapshot of one open AEDAT-4 file. {@code fileLength} is null when there is
     * no file. {@code uncompressedPayload} is negative when codec content size
     * was not read.
     */
    public static Aedat4FileInfo fromRecording(
            String path,
            int compression,
            long eventCount,
            long frameCount,
            long imuSampleCount,
            String durationText,
            long durationUs,
            int lossyTimeShift,
            boolean lossyCollapsePolarities,
            Long fileLength,
            long compressedPayload,
            long uncompressedPayload,
            int eventStreamId,
            String source,
            int evtsPackets,
            int frmePackets,
            int imusPackets,
            String chipName,
            long baseUnixUs,
            ZoneId zone) {
        String compressionName = Aedat4Compression.nameOf(compression);
        String summary = legacySummary(path, compression, compressionName, eventCount, frameCount, imuSampleCount,
                durationText, lossyTimeShift, lossyCollapsePolarities, fileLength, compressedPayload,
                uncompressedPayload, eventStreamId, source, evtsPackets, chipName);
        List<Row> rows = new ArrayList<>();
        if (path != null) {
            rows.add(new Row("Path", path, null, true));
        }
        rows.add(new Row("Format", "AEDAT-4", null, false));
        rows.add(new Row("Compression", compressionName, null, false));
        if (chipName != null && !chipName.isEmpty()) {
            rows.add(new Row("Chip", chipName, null, false));
        }
        rows.add(new Row("Stream", streamText(eventStreamId, source), null, false));
        if (baseUnixUs > 0) {
            rows.add(new Row("Recording start", formatStart(baseUnixUs, zone),
                    "Unix time of the AEDAT-4 timestamp origin", false));
        }
        rows.add(new Row("Duration", durationTableText(durationText, durationUs), null, false));
        if (durationUs > 0) {
            rows.add(new Row("Event rate", eventRateText(eventCount, durationUs),
                    "Indexed events divided by the recording duration", false));
        }
        rows.add(new Row("Events", countText(eventCount), null, false));
        rows.add(new Row("Frames", countText(frameCount), null, false));
        rows.add(new Row("IMU samples", countText(imuSampleCount), null, false));
        rows.add(new Row("EVTS packets", String.format("%,d", evtsPackets), null, false));
        rows.add(new Row("FRME packets", String.format("%,d", frmePackets), null, false));
        rows.add(new Row("IMUS packets", String.format("%,d", imusPackets), null, false));
        rows.add(new Row("Lossy time bins", lossyText(lossyTimeShift, lossyCollapsePolarities), null, false));
        if (fileLength != null) {
            rows.add(new Row("File size", bytesText(fileLength, 3),
                    "Bytes on disk, including the header and the packet index", false));
        }
        rows.add(new Row("Compressed payload",
                compressedPayload > 0 ? bytesText(compressedPayload, 3) : "0",
                "Sum of on-disk packet payloads (codec bytes), excluding the header and index", false));
        if (uncompressedPayload > 0) {
            rows.add(new Row("Uncompressed payload", bytesText(uncompressedPayload, 3),
                    "FlatBuffer payload bytes before compression", false));
        } else {
            rows.add(new Row("Uncompressed payload", UNCOMPRESSED_NOT_IN_HEADERS, RATIO_TIP_UNAVAILABLE, false));
        }
        String ratio = ratioText(compression, uncompressedPayload, compressedPayload);
        String ratioTip = RATIO_UNAVAILABLE.equals(ratio)
                ? RATIO_TIP_UNAVAILABLE
                : "Uncompressed payload bytes divided by compressed payload bytes";
        rows.add(new Row("Compression ratio", ratio, ratioTip, false));
        return new Aedat4FileInfo(summary, rows);
    }

    /**
     * One recording described by {@link #toSummaryText()}, or any leftover lines
     * kept as Info rows so a saved Save As snapshot still shows in the table.
     */
    public static Section parseSummary(String title, String text) {
        if (text == null || text.trim().isEmpty()) {
            return message(title, "File info is not available for this recording format.");
        }
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        String path = null;
        String compression = null;
        String events = null;
        String frames = null;
        String imu = null;
        String duration = null;
        String lossy = null;
        boolean sawLossy = false;
        String fileSize = null;
        String compressed = null;
        String uncompressed = null;
        String ratio = null;
        String stream = null;
        String evtsPackets = null;
        String chip = null;
        boolean sawAedat4 = false;
        List<String> notes = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || "Original (input)".equals(line) || "Exported (output)".equals(line)) {
                continue;
            }
            Matcher counts = COUNTS.matcher(line);
            Matcher streamMatch = STREAM.matcher(line);
            if (counts.matches()) {
                sawAedat4 = true;
                compression = counts.group(1);
                events = countFromLegacy(counts.group(2), counts.group(3));
                frames = countFromLegacy(counts.group(4), counts.group(5));
                imu = countFromLegacy(counts.group(6), counts.group(7));
                duration = counts.group(8).trim();
            } else if (line.startsWith("Lossy time bins:")) {
                sawLossy = true;
                lossy = line.substring("Lossy time bins:".length()).trim();
            } else if (line.startsWith("Size:")) {
                fileSize = line.substring("Size:".length()).trim();
                if (fileSize.endsWith(" on disk")) {
                    fileSize = fileSize.substring(0, fileSize.length() - " on disk".length()).trim();
                }
            } else if (line.startsWith("Payloads:")) {
                String[] payload = parsePayload(line);
                if (payload == null) {
                    notes.add(line);
                } else {
                    compressed = payload[0];
                    uncompressed = payload[1];
                    ratio = payload[2];
                }
            } else if (streamMatch.matches()) {
                String id = streamMatch.group(1);
                String source = streamMatch.group(2);
                stream = source == null || source.isEmpty() ? id : id + " (" + source + ")";
                evtsPackets = streamMatch.group(3);
            } else if (line.startsWith("Chip:")) {
                chip = line.substring("Chip:".length()).trim();
            } else if (path == null && !line.contains(": ")) {
                path = line;
            } else {
                notes.add(line);
            }
        }
        if (!sawAedat4 && path == null && notes.size() == lines.length) {
            return message(title, text.trim());
        }
        List<Row> rows = new ArrayList<>();
        if (path != null) {
            rows.add(new Row("Path", path, null, true));
        }
        if (sawAedat4) {
            rows.add(new Row("Format", "AEDAT-4", null, false));
        }
        addIfPresent(rows, "Compression", compression);
        addIfPresent(rows, "Chip", chip);
        addIfPresent(rows, "Stream", stream);
        addIfPresent(rows, "Duration", duration);
        addIfPresent(rows, "Events", events);
        addIfPresent(rows, "Frames", frames);
        addIfPresent(rows, "IMU samples", imu);
        addIfPresent(rows, "EVTS packets", evtsPackets);
        if (sawLossy) {
            rows.add(new Row("Lossy time bins", lossy == null || lossy.isEmpty() ? "off" : lossy, null, false));
        } else if (sawAedat4) {
            rows.add(new Row("Lossy time bins", "off", null, false));
        }
        addIfPresent(rows, "File size", fileSize);
        addIfPresent(rows, "Compressed payload", compressed);
        addIfPresent(rows, "Uncompressed payload", uncompressed);
        if (ratio != null) {
            String tip = RATIO_UNAVAILABLE.equals(ratio) ? RATIO_TIP_UNAVAILABLE : null;
            rows.add(new Row("Compression ratio", ratio, tip, false));
        }
        for (String note : notes) {
            rows.add(new Row("Info", note, null, false));
        }
        if (rows.isEmpty()) {
            return message(title, text.trim());
        }
        return new Section(title, rows);
    }

    /** Split a Save As "Original / Exported" block, or one plain summary. */
    public static List<Section> parseSections(String text) {
        if (text == null || text.trim().isEmpty()) {
            return List.of(message(null, "File info is not available for this recording format."));
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String exportedHeader = "Exported (output)\n";
        String originalHeader = "Original (input)\n";
        int exp = normalized.indexOf(exportedHeader);
        if (normalized.startsWith(originalHeader) && exp > 0) {
            String src = normalized.substring(originalHeader.length(), exp).trim();
            String out = normalized.substring(exp + exportedHeader.length()).trim();
            List<Section> list = new ArrayList<>(2);
            list.add(parseSummary("Original (input)", src));
            list.add(parseSummary("Exported (output)", out));
            return list;
        }
        return List.of(parseSummary(null, normalized));
    }

    public static Section message(String title, String text) {
        String body = text == null || text.trim().isEmpty()
                ? "File info is not available for this recording format."
                : text.trim();
        return new Section(title, List.of(new Row("Info", body, null, false)));
    }

    /** Tab-separated property/value text. Paths are the full path. */
    public static String toTabSeparated(List<Section> sections) {
        if (sections == null || sections.isEmpty()) {
            return "";
        }
        boolean anyTitle = false;
        for (Section section : sections) {
            if (section != null && section.title != null && !section.title.isEmpty()) {
                anyTitle = true;
                break;
            }
        }
        StringBuilder sb = new StringBuilder();
        if (anyTitle) {
            sb.append("Section\tProperty\tValue\n");
        } else {
            sb.append("Property\tValue\n");
        }
        for (Section section : sections) {
            if (section == null) {
                continue;
            }
            String title = section.title == null ? "" : section.title;
            for (Row row : section.rows) {
                if (anyTitle) {
                    sb.append(tsv(title)).append('\t');
                }
                sb.append(tsv(row.property)).append('\t').append(tsv(row.value)).append('\n');
            }
        }
        return sb.toString();
    }

    /** HTML table using the same full values as {@link #toTabSeparated}. */
    public static String toHtml(List<Section> sections) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");
        if (sections != null) {
            for (Section section : sections) {
                if (section == null) {
                    continue;
                }
                if (section.title != null && !section.title.isEmpty()) {
                    sb.append("<p><b>").append(escapeHtml(section.title)).append("</b></p>");
                }
                sb.append("<table border=\"1\" cellspacing=\"0\" cellpadding=\"4\">");
                sb.append("<tr><th align=\"left\">Property</th><th align=\"left\">Value</th></tr>");
                for (Row row : section.rows) {
                    sb.append("<tr><td>").append(escapeHtml(row.property))
                            .append("</td><td>").append(escapeHtml(row.value)).append("</td></tr>");
                }
                sb.append("</table>");
            }
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    /**
     * Shorten {@code path} so both the beginning and the end stay visible
     * within {@code pixelWidth}. The full path is unchanged when it already fits.
     */
    public static String truncatePath(String path, FontMetrics fm, int pixelWidth) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        if (fm == null || pixelWidth <= 0 || fm.stringWidth(path) <= pixelWidth) {
            return path;
        }
        String ell = "\u2026";
        int ellW = fm.stringWidth(ell);
        if (pixelWidth <= ellW) {
            return ell;
        }
        int budget = pixelWidth - ellW;
        int head = 0;
        int tail = 0;
        boolean takeTail = true;
        while (head + tail < path.length()) {
            if (takeTail) {
                if (tail < path.length() - head && widthOf(fm, path, head, tail + 1) <= budget) {
                    tail++;
                    takeTail = false;
                    continue;
                }
                takeTail = false;
            }
            if (head < path.length() - tail && widthOf(fm, path, head + 1, tail) <= budget) {
                head++;
                takeTail = true;
                continue;
            }
            break;
        }
        if (head < 1 && tail > 1) {
            while (tail > 1 && widthOf(fm, path, 1, tail) > budget) {
                tail--;
            }
            if (widthOf(fm, path, 1, Math.max(tail, 1)) <= budget) {
                head = 1;
            }
        }
        if (tail < 1 && head > 1) {
            while (head > 1 && widthOf(fm, path, head, 1) > budget) {
                head--;
            }
            if (widthOf(fm, path, Math.max(head, 1), 1) <= budget) {
                tail = 1;
            }
        }
        if (head < 1) {
            head = 1;
        }
        if (tail < 1) {
            tail = 1;
        }
        if (head + tail >= path.length()) {
            return path;
        }
        while (fm.stringWidth(path.substring(0, head) + ell + path.substring(path.length() - tail)) > pixelWidth
                && (head > 1 || tail > 1)) {
            if (head >= tail && head > 1) {
                head--;
            } else if (tail > 1) {
                tail--;
            } else {
                break;
            }
        }
        String result = path.substring(0, head) + ell + path.substring(path.length() - tail);
        if (fm.stringWidth(result) > pixelWidth) {
            return ell;
        }
        return result;
    }

    private static int widthOf(FontMetrics fm, String path, int head, int tail) {
        int w = 0;
        if (head > 0) {
            w += fm.stringWidth(path.substring(0, head));
        }
        if (tail > 0) {
            w += fm.stringWidth(path.substring(path.length() - tail));
        }
        return w;
    }

    private static String legacySummary(
            String path, int compression, String compressionName,
            long eventCount, long frameCount, long imuSampleCount, String durationText,
            int lossyTimeShift, boolean lossyCollapsePolarities, Long fileLength,
            long compressedPayload, long uncompressedPayload,
            int eventStreamId, String source, int evtsPackets, String chipName) {
        EngineeringFormat eng = new EngineeringFormat();
        eng.setPrecision(3);
        StringBuilder sb = new StringBuilder();
        if (path != null) {
            sb.append(path).append('\n');
        }
        sb.append(String.format(
                "AEDAT-4 %s: %s (%,d) events, %s (%,d) frames, %s (%,d) IMU samples, duration=%s",
                compressionName,
                eng.format((double) eventCount).trim(), eventCount,
                eng.format((double) frameCount).trim(), frameCount,
                eng.format((double) imuSampleCount).trim(), imuSampleCount,
                durationText == null ? "" : durationText));
        if (lossyTimeShift >= 0) {
            sb.append(String.format("\nLossy time bins: right shift %d bits (jAER-only EVTS)%s",
                    lossyTimeShift, lossyCollapsePolarities ? ", On and Off collapsed" : ""));
        }
        if (fileLength != null) {
            sb.append(String.format("\nSize: %sB on disk", eng.format(fileLength.doubleValue()).trim()));
        }
        String payload = legacyPayloadLine(compression, compressedPayload, uncompressedPayload);
        if (!payload.isEmpty()) {
            sb.append('\n').append(payload);
        }
        sb.append(String.format("\nStream %d%s, %,d EVTS packets indexed",
                eventStreamId,
                source == null ? "" : " (" + source + ")",
                evtsPackets));
        if (chipName != null) {
            sb.append("\nChip: ").append(chipName);
        }
        return sb.toString();
    }

    private static String legacyPayloadLine(int compression, long compressed, long uncompressed) {
        if (compressed <= 0 && uncompressed <= 0) {
            return "";
        }
        if (uncompressed > 0) {
            return Aedat4Compression.formatPayloadCompression(compression, uncompressed, compressed);
        }
        EngineeringFormat eng = new EngineeringFormat();
        eng.setPrecision(1);
        return String.format("Payloads: %sB compressed (%s)",
                eng.format((double) compressed).trim(),
                Aedat4Compression.nameOf(compression));
    }

    /** {@code compressed}, {@code uncompressed}, {@code ratio}; nulls when that part is absent. */
    private static String[] parsePayload(String line) {
        Matcher both = PAY_BOTH.matcher(line);
        if (both.matches()) {
            String ratio = both.group(5) + ":1 (" + both.group(4) + "% of uncompressed)";
            return new String[] {both.group(2), both.group(1), ratio};
        }
        Matcher raw = PAY_RAW.matcher(line);
        if (raw.matches()) {
            return new String[] {null, raw.group(1), "1:1"};
        }
        Matcher comp = PAY_COMP.matcher(line);
        if (comp.matches()) {
            String rest = comp.group(3) == null ? "" : comp.group(3);
            String uncompressed = rest.isEmpty() || rest.contains("not in packet headers")
                    ? UNCOMPRESSED_NOT_IN_HEADERS : null;
            return new String[] {comp.group(1), uncompressed, RATIO_UNAVAILABLE};
        }
        return null;
    }

    private static String countText(long n) {
        if (n == 0) {
            return "0";
        }
        EngineeringFormat eng = new EngineeringFormat();
        eng.setPrecision(3);
        return String.format("%,d", n) + " (" + eng.format((double) n).trim() + ")";
    }

    private static String countFromLegacy(String eng, String exactWithCommas) {
        if ("0".equals(exactWithCommas) || "0".equals(eng)) {
            return "0";
        }
        return exactWithCommas + " (" + eng + ")";
    }

    private static String durationTableText(String compact, long durationUs) {
        String text = compact == null || compact.isEmpty() ? "0s" : compact;
        if (durationUs >= 1_000_000L) {
            double sec = durationUs / 1_000_000.0;
            return text + String.format(" (%,.2f s)", sec);
        }
        return text;
    }

    private static String eventRateText(long eventCount, long durationUs) {
        double eps = (eventCount * 1_000_000.0) / (double) durationUs;
        if (eps < 1000.0) {
            if (eps == 0.0) {
                return "0 events/s";
            }
            return String.format("%,.3f events/s", eps);
        }
        EngineeringFormat eng = new EngineeringFormat();
        eng.setPrecision(3);
        return eng.format(eps).trim() + " events/s";
    }

    private static String lossyText(int shift, boolean collapse) {
        if (shift < 0) {
            return "off";
        }
        return String.format("right shift %d bits (jAER-only EVTS)%s",
                shift, collapse ? ", On and Off collapsed" : "");
    }

    private static String bytesText(long n, int precision) {
        if (n <= 0) {
            return "0";
        }
        EngineeringFormat eng = new EngineeringFormat();
        eng.setPrecision(precision);
        return eng.format((double) n).trim() + "B (" + String.format("%,d bytes", n) + ")";
    }

    private static String ratioText(int compression, long uncompressed, long compressed) {
        if (uncompressed > 0 && compressed > 0
                && (compression == CompressionType.NONE || uncompressed == compressed)) {
            return "1:1";
        }
        if (uncompressed > 0 && compressed > 0) {
            double pct = 100.0 * compressed / (double) uncompressed;
            double ratio = uncompressed / (double) compressed;
            return String.format("%.1f:1 (%.0f%% of uncompressed)", ratio, pct);
        }
        if (compressed > 0) {
            return RATIO_UNAVAILABLE;
        }
        return "n/a";
    }

    private static String streamText(int eventStreamId, String source) {
        if (source == null || source.isEmpty()) {
            return Integer.toString(eventStreamId);
        }
        return eventStreamId + " (" + source + ")";
    }

    private static String formatStart(long baseUnixUs, ZoneId zone) {
        ZoneId z = zone == null ? ZoneId.systemDefault() : zone;
        long secs = Math.floorDiv(baseUnixUs, 1_000_000L);
        long micros = Math.floorMod(baseUnixUs, 1_000_000L);
        Instant instant = Instant.ofEpochSecond(secs, micros * 1000L);
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(instant.atZone(z));
    }

    private static void addIfPresent(List<Row> rows, String property, String value) {
        if (value != null && !value.isEmpty()) {
            String tip = UNCOMPRESSED_NOT_IN_HEADERS.equals(value) ? RATIO_TIP_UNAVAILABLE : null;
            rows.add(new Row(property, value, tip, "Path".equals(property)));
        }
    }

    private static String valueOf(List<Row> rows, String property) {
        for (Row row : rows) {
            if (row.property.equals(property)) {
                return row.value;
            }
        }
        return null;
    }

    private static String tsv(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
    }

    public static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }
        return sb.toString();
    }

    /** One labeled recording (the open file, or the Save As original). */
    public static final class Section {
        public final String title;
        public final List<Row> rows;

        public Section(String title, List<Row> rows) {
            this.title = title;
            this.rows = List.copyOf(rows);
        }

        public String value(String property) {
            return valueOf(rows, property);
        }
    }

    /** One property. {@code path} rows show a shortened path in the table; the value stays the full path. */
    public static final class Row {
        public final String property;
        public final String value;
        /** Plain-text tooltip, or null. Path rows use {@link #value} as the tooltip. */
        public final String tooltip;
        public final boolean path;

        public Row(String property, String value, String tooltip, boolean path) {
            this.property = property;
            this.value = value == null ? "" : value;
            this.tooltip = tooltip;
            this.path = path;
        }
    }
}
