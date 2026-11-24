package net.sf.jaer.eventprocessing.tracking;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ASCII log of {@link RectangularClusterTracker} cluster paths (LogClusters).
 * <p>
 * Lines that start with {@code #} document the file. The body is YAML: one
 * header document, then one document per cluster ({@code ---}). Each cluster
 * has a variable-length {@code samples} list. A sample has Unix
 * {@code absoluteTimeUs}, camera {@code timestampUs}, chip-pixel {@code x,y},
 * and {@code vx,vy} in pixels/second ({@code null} until velocity exists).
 * <p>
 * {@code absoluteTimeUs = timeOriginUs + timestampUs}. {@code timestampUs} is
 * the unsigned 32-bit event timestamp. Two cameras are logged separately;
 * align them later on {@code absoluteTimeUs}.
 */
public final class ClusterFlightLog {

    public static final class Sample {
        public final long absoluteTimeUs;
        public final long timestampUs;
        public final float x;
        public final float y;
        /** Pixels/second, or {@code null} if this sample has no velocity yet. */
        public final Float vx;
        public final Float vy;

        public Sample(long absoluteTimeUs, long timestampUs, float x, float y, Float vx, Float vy) {
            this.absoluteTimeUs = absoluteTimeUs;
            this.timestampUs = timestampUs;
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
        }
    }

    public static final class ClusterRecord {
        public final int index;
        public final int id;
        public final List<Sample> samples;

        public ClusterRecord(int index, int id, List<Sample> samples) {
            this.index = index;
            this.id = id;
            this.samples = samples;
        }
    }

    public static final class Header {
        public final String camera;
        public final String chip;
        public final String source;
        public final long timeOriginUs;
        /** {@code aedat4}, {@code filename}, {@code recording}, or {@code host}. */
        public final String timeOrigin;

        public Header(String camera, String chip, String source, long timeOriginUs, String timeOrigin) {
            this.camera = camera;
            this.chip = chip;
            this.source = source;
            this.timeOriginUs = timeOriginUs;
            this.timeOrigin = timeOrigin;
        }
    }

    public static final class FileContents {
        public final Header header;
        public final List<ClusterRecord> clusters;

        public FileContents(Header header, List<ClusterRecord> clusters) {
            this.header = header;
            this.clusters = clusters;
        }
    }

    private ClusterFlightLog() {
    }

    /** Field guide. Every line starts with {@code #}. */
    public static void writeGuide(PrintStream out) {
        out.println("# RectangularClusterTracker LogClusters");
        out.println("# Lines starting with # are comments. The rest is YAML.");
        out.println("# First document is the header. Each later document (after ---) is one cluster.");
        out.println("# Clusters have different lengths. One camera may see a flight the other misses.");
        out.println("# Align two cameras in post-processing with absoluteTimeUs. This file is one camera.");
        out.println("# header.camera: short camera name (same token as in the file name)");
        out.println("# header.chip: chip class simple name");
        out.println("# header.source: recording path, or live");
        out.println("# header.timeOrigin: aedat4 | filename | recording | host");
        out.println("# header.timeOriginUs: Unix epoch microseconds added to timestampUs");
        out.println("# absoluteTimeUs: Unix microseconds. absoluteTimeUs = timeOriginUs + timestampUs");
        out.println("# timestampUs: camera event time, unsigned 32-bit microseconds (wraps after about 71.6 min)");
        out.println("# x, y: cluster location in chip pixels");
        out.println("# vx, vy: cluster velocity in pixels/second, or null if not yet measured");
        out.println("# cluster: index in this file, from 0, in the order clusters were logged (when they died)");
        out.println("# id: tracker cluster number");
        out.println("# samples: path points at the global update interval, one per update that had events");
    }

    public static void writeHeader(PrintStream out, Header header) {
        out.println("kind: header");
        out.println("camera: " + yamlString(header.camera));
        out.println("chip: " + yamlString(header.chip));
        out.println("source: " + yamlString(header.source));
        out.println("timeOrigin: " + yamlString(header.timeOrigin));
        out.println("timeOriginUs: " + header.timeOriginUs);
    }

    public static void writeCluster(PrintStream out, ClusterRecord cluster) {
        out.println("---");
        out.println("kind: cluster");
        out.println("cluster: " + cluster.index);
        out.println("id: " + cluster.id);
        out.println("samples:");
        for (Sample s : cluster.samples) {
            out.println("  - {absoluteTimeUs: " + s.absoluteTimeUs
                    + ", timestampUs: " + s.timestampUs
                    + ", x: " + num(s.x)
                    + ", y: " + num(s.y)
                    + ", vx: " + num(s.vx)
                    + ", vy: " + num(s.vy) + "}");
        }
    }

    public static FileContents parse(String text) throws IOException {
        return parse(new BufferedReader(new StringReader(text)));
    }

    public static FileContents parse(BufferedReader in) throws IOException {
        List<String> docs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            if (line.trim().equals("---")) {
                docs.add(current.toString());
                current.setLength(0);
            } else {
                current.append(line).append('\n');
            }
        }
        docs.add(current.toString());

        Header header = null;
        List<ClusterRecord> clusters = new ArrayList<>();
        for (String doc : docs) {
            ParsedDoc parsed = parseDocument(doc);
            if (parsed == null) {
                continue;
            }
            if ("cluster".equals(parsed.kind)) {
                clusters.add(parsed.cluster);
            } else if (header == null && parsed.header != null) {
                header = parsed.header;
            }
        }
        return new FileContents(header, clusters);
    }

    /** Round-trip a two-cluster file, including a null velocity and a short path. */
    public static void selfCheck() throws IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, java.nio.charset.StandardCharsets.UTF_8);
        writeGuide(out);
        writeHeader(out, new Header("NRV", "NRVS5KRC1S", "/data/fly box/NRV_rec.aedat4", 1_700_000_000_000_000L, "aedat4"));
        List<Sample> longPath = new ArrayList<>();
        longPath.add(new Sample(1_700_000_000_000_000L + 1000L, 1000L, 10.5f, 20.25f, 1.5f, -2.25f));
        longPath.add(new Sample(1_700_000_000_000_000L + 2000L, 2000L, 12f, 18f, null, null));
        writeCluster(out, new ClusterRecord(0, 3, longPath));
        List<Sample> shortPath = new ArrayList<>();
        shortPath.add(new Sample(1_700_000_000_000_000L + 5000L, 5000L, -1f, 0f, 0f, 4f));
        writeCluster(out, new ClusterRecord(1, 9, shortPath));
        out.flush();

        FileContents contents = parse(bytes.toString(java.nio.charset.StandardCharsets.UTF_8));
        if (contents.header == null) {
            throw new IOException("missing header");
        }
        if (!"NRV".equals(contents.header.camera) || !"aedat4".equals(contents.header.timeOrigin)) {
            throw new IOException("header camera/origin: " + contents.header.camera + " " + contents.header.timeOrigin);
        }
        if (!contents.header.source.contains("fly box")) {
            throw new IOException("source not parsed: " + contents.header.source);
        }
        if (contents.clusters.size() != 2) {
            throw new IOException("cluster count " + contents.clusters.size());
        }
        ClusterRecord a = contents.clusters.get(0);
        if (a.index != 0 || a.id != 3 || a.samples.size() != 2) {
            throw new IOException("first cluster shape");
        }
        Sample s0 = a.samples.get(0);
        if (s0.absoluteTimeUs != 1_700_000_000_000_000L + 1000L || s0.timestampUs != 1000L
                || s0.vx == null || s0.vy == null || s0.vy > 0) {
            throw new IOException("first sample " + s0.absoluteTimeUs + " " + s0.vx + " " + s0.vy);
        }
        if (a.samples.get(1).vx != null || a.samples.get(1).vy != null) {
            throw new IOException("expected null velocity");
        }
        if (contents.clusters.get(1).samples.size() != 1 || contents.clusters.get(1).id != 9) {
            throw new IOException("second cluster");
        }
        if (s0.absoluteTimeUs != contents.header.timeOriginUs + s0.timestampUs) {
            throw new IOException("absoluteTimeUs != timeOriginUs + timestampUs");
        }
    }

    public static void main(String[] args) throws IOException {
        selfCheck();
        System.out.println("ClusterFlightLog selfCheck PASS");
    }

    private static String num(Float v) {
        if (v == null || v.isNaN() || v.isInfinite()) {
            return "null";
        }
        return num(v.floatValue());
    }

    private static String num(float v) {
        if (Float.isNaN(v) || Float.isInfinite(v)) {
            return "null";
        }
        String s = String.format(Locale.US, "%.5f", v);
        int dot = s.indexOf('.');
        if (dot < 0) {
            return s;
        }
        int end = s.length();
        while (end > dot + 1 && s.charAt(end - 1) == '0') {
            end--;
        }
        if (s.charAt(end - 1) == '.') {
            end--;
        }
        return s.substring(0, end);
    }

    private static String yamlString(String s) {
        if (s == null) {
            return "\"\"";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static final class ParsedDoc {
        String kind;
        Header header;
        ClusterRecord cluster;
    }

    private static ParsedDoc parseDocument(String doc) {
        Map<String, String> keys = new LinkedHashMap<>();
        List<Sample> samples = new ArrayList<>();
        boolean inSamples = false;
        for (String raw : doc.split("\n", -1)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.equals("samples:")) {
                inSamples = true;
                continue;
            }
            if (inSamples && line.startsWith("-")) {
                samples.add(parseSample(line));
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim();
            String value = unquote(line.substring(colon + 1).trim());
            keys.put(key, value);
            inSamples = false;
        }
        if (keys.isEmpty() && samples.isEmpty()) {
            return null;
        }
        ParsedDoc parsed = new ParsedDoc();
        parsed.kind = keys.get("kind");
        if ("cluster".equals(parsed.kind) || keys.containsKey("cluster")) {
            parsed.kind = "cluster";
            int index = parseInt(keys.get("cluster"), samples.size() == 0 ? 0 : 0);
            int id = parseInt(keys.get("id"), index);
            parsed.cluster = new ClusterRecord(index, id, samples);
            return parsed;
        }
        parsed.header = new Header(
                keys.getOrDefault("camera", ""),
                keys.getOrDefault("chip", ""),
                keys.getOrDefault("source", ""),
                parseLong(keys.get("timeOriginUs"), 0L),
                keys.getOrDefault("timeOrigin", ""));
        parsed.kind = "header";
        return parsed;
    }

    private static Sample parseSample(String line) {
        int open = line.indexOf('{');
        int close = line.lastIndexOf('}');
        if (open < 0 || close < open) {
            throw new IllegalArgumentException("sample is not a YAML flow map: " + line);
        }
        Map<String, String> map = new LinkedHashMap<>();
        String body = line.substring(open + 1, close);
        for (String part : body.split(",")) {
            int colon = part.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            map.put(part.substring(0, colon).trim(), part.substring(colon + 1).trim());
        }
        return new Sample(
                parseLong(map.get("absoluteTimeUs"), 0L),
                parseLong(map.get("timestampUs"), 0L),
                parseFloat(map.get("x"), 0f),
                parseFloat(map.get("y"), 0f),
                parseFloatOrNull(map.get("vx")),
                parseFloatOrNull(map.get("vy")));
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            String inner = value.substring(1, value.length() - 1);
            return inner.replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return value;
    }

    private static int parseInt(String s, int fallback) {
        if (s == null || s.isEmpty()) {
            return fallback;
        }
        return Integer.parseInt(s.trim());
    }

    private static long parseLong(String s, long fallback) {
        if (s == null || s.isEmpty() || "null".equals(s)) {
            return fallback;
        }
        return Long.parseLong(s.trim());
    }

    private static float parseFloat(String s, float fallback) {
        Float v = parseFloatOrNull(s);
        return v == null ? fallback : v;
    }

    private static Float parseFloatOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty() || "null".equals(t) || ".nan".equalsIgnoreCase(t) || "nan".equalsIgnoreCase(t)) {
            return null;
        }
        return Float.parseFloat(t);
    }
}
