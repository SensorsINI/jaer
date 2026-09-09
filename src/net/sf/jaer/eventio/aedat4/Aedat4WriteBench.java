package net.sf.jaer.eventio.aedat4;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.aedat4.dv.CompressionType;
import net.sf.jaer.util.EngineeringFormat;

/**
 * Times {@link Aedat4FileOutputStream#writeBundle} for each AEDAT-4 compression
 * codec on two synthetic polarity workloads (high-rate random vs quiet/bursty).
 *
 * <pre>
 * java -cp "build/classes;jars/*" net.sf.jaer.eventio.aedat4.Aedat4WriteBench
 * </pre>
 */
public final class Aedat4WriteBench {

    public static final int[] CODECS = {
        CompressionType.NONE,
        CompressionType.LZ4,
        CompressionType.LZ4_HIGH,
        CompressionType.ZSTD,
        CompressionType.ZSTD_HIGH
    };

    private Aedat4WriteBench() {
    }

    public static final class Config {
        public int width = 640;
        public int height = 480;
        public int dtUs = 20_000;
        public int highRatePackets = 40;
        public int highRateEventsPerPacket = 8_000;
        public int quietPackets = 80;
        public int quietEventsPerPacket = 80;
        public int burstPackets = 40;
        public int burstEventsPerPacket = 8_000;
        public File tempDir = new File(System.getProperty("java.io.tmpdir"));

        public static Config interactive() {
            return new Config();
        }

        /** Fast config for unit tests (still hits every codec and both workloads). */
        public static Config tiny() {
            Config c = new Config();
            c.highRatePackets = 3;
            c.highRateEventsPerPacket = 128;
            c.quietPackets = 4;
            c.quietEventsPerPacket = 16;
            c.burstPackets = 2;
            c.burstEventsPerPacket = 64;
            return c;
        }
    }

    public enum Workload {
        HIGH_RATE("High-rate (random pixels, ~1 Meps)"),
        QUIET_BURST("Quiet / bursty (clustered pixels)");

        public final String label;

        Workload(String label) {
            this.label = label;
        }
    }

    public static final class Row {
        public final Workload workload;
        public final int compression;
        public final String codecName;
        public final long events;
        public final int packets;
        public final long wallNs;
        public final long uncompressedPayloadBytes;
        public final long compressedPayloadBytes;
        public final long fileBytes;

        Row(Workload workload, int compression, long events, int packets, long wallNs,
                long uncompressedPayloadBytes, long compressedPayloadBytes, long fileBytes) {
            this.workload = workload;
            this.compression = compression;
            this.codecName = Aedat4Compression.nameOf(compression);
            this.events = events;
            this.packets = packets;
            this.wallNs = wallNs;
            this.uncompressedPayloadBytes = uncompressedPayloadBytes;
            this.compressedPayloadBytes = compressedPayloadBytes;
            this.fileBytes = fileBytes;
        }

        public double wallS() {
            return wallNs * 1e-9;
        }

        public double eventsPerSec() {
            return wallNs <= 0 ? 0 : events / (wallNs * 1e-9);
        }

        public double uncompressedMebiPerSec() {
            return wallNs <= 0 ? 0 : uncompressedPayloadBytes / (wallNs * 1e-9) / (1024.0 * 1024.0);
        }

        public double payloadRatio() {
            return compressedPayloadBytes <= 0 ? 1.0
                    : uncompressedPayloadBytes / (double) compressedPayloadBytes;
        }
    }

    public static final class Report {
        public final List<Row> rows = new ArrayList<>();
        public final String host;

        Report() {
            this.host = System.getProperty("os.name") + " "
                    + System.getProperty("os.arch") + " "
                    + Runtime.getRuntime().availableProcessors() + " threads, "
                    + System.getProperty("java.vm.name") + " "
                    + System.getProperty("java.version");
        }

        public String toPlainText() {
            EngineeringFormat eng = new EngineeringFormat();
            eng.setPrecision(1);
            StringBuilder sb = new StringBuilder();
            sb.append("AEDAT-4 write bench (").append(host).append(")\n");
            sb.append(String.format(Locale.US,
                    "%-28s %-10s %8s %8s %10s %10s %8s %8s%n",
                    "workload", "codec", "events/s", "MiB/s", "raw B", "file B", "ratio", "ms"));
            for (Row r : rows) {
                sb.append(String.format(Locale.US,
                        "%-28s %-10s %8s %8.0f %10s %10s %7.2f:1 %7.0f%n",
                        r.workload.label, r.codecName,
                        eng.format(r.eventsPerSec()).trim(),
                        r.uncompressedMebiPerSec(),
                        eng.format((double) r.uncompressedPayloadBytes).trim(),
                        eng.format((double) r.fileBytes).trim(),
                        r.payloadRatio(),
                        r.wallS() * 1000.0));
            }
            sb.append('\n').append(recommendation());
            return sb.toString();
        }

        public String toHtml() {
            EngineeringFormat eng = new EngineeringFormat();
            eng.setPrecision(1);
            StringBuilder sb = new StringBuilder();
            sb.append("<html><body>");
            sb.append("<h2>AEDAT-4 write / compress bench</h2>");
            sb.append("<p>").append(esc(host)).append("</p>");
            sb.append("<p>Times <code>Aedat4FileOutputStream.writeBundle</code> (FlatBuffers + codec + disk) ");
            sb.append("for synthetic polarity packets. Run this on the PC you record with.</p>");
            sb.append("<table border=\"1\" cellpadding=\"4\" cellspacing=\"0\">");
            sb.append("<tr><th>Workload</th><th>Codec</th><th>events/s</th><th>MiB/s raw</th>");
            sb.append("<th>Uncompressed</th><th>File</th><th>Ratio</th><th>ms</th></tr>");
            for (Row r : rows) {
                sb.append("<tr><td>").append(esc(r.workload.label)).append("</td><td><b>")
                        .append(esc(r.codecName)).append("</b></td><td>")
                        .append(esc(eng.format(r.eventsPerSec()).trim())).append("</td><td>")
                        .append(String.format(Locale.US, "%.0f", r.uncompressedMebiPerSec())).append("</td><td>")
                        .append(esc(eng.format((double) r.uncompressedPayloadBytes).trim())).append("B</td><td>")
                        .append(esc(eng.format((double) r.fileBytes).trim())).append("B</td><td>")
                        .append(String.format(Locale.US, "%.2f:1", r.payloadRatio())).append("</td><td>")
                        .append(String.format(Locale.US, "%.0f", r.wallS() * 1000.0)).append("</td></tr>");
            }
            sb.append("</table>");
            sb.append("<p>").append(esc(recommendation()).replace("\n", "<br>")).append("</p>");
            sb.append("</body></html>");
            return sb.toString();
        }

        /**
         * Fastest high-rate codec whose file is not much larger than NONE, plus
         * smallest quiet file that is not far slower than LZ4.
         */
        public String recommendation() {
            Row highPick = pickHighRate();
            Row quietPick = pickQuiet();
            StringBuilder sb = new StringBuilder();
            if (highPick != null) {
                sb.append("High-rate streams on this PC: prefer ").append(highPick.codecName)
                        .append(String.format(Locale.US, " (%.2f:1, %s events/s).",
                                highPick.payloadRatio(),
                                new EngineeringFormat().format(highPick.eventsPerSec()).trim()));
            }
            if (quietPick != null) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append("Quiet / bursty scenes on this PC: prefer ").append(quietPick.codecName)
                        .append(String.format(Locale.US, " (%.2f:1, %s events/s).",
                                quietPick.payloadRatio(),
                                new EngineeringFormat().format(quietPick.eventsPerSec()).trim()));
            }
            sb.append("\nDefault for live recording is LZ4. If the viewer stalls while recording a fast camera, switch to NONE.");
            return sb.toString();
        }

        private Row pickHighRate() {
            List<Row> high = rowsFor(Workload.HIGH_RATE);
            if (high.isEmpty()) {
                return null;
            }
            Row none = find(high, CompressionType.NONE);
            double noneRate = none != null ? none.eventsPerSec() : 0;
            Row best = none;
            double bestRate = noneRate;
            for (Row r : high) {
                if (r.compression == CompressionType.NONE) {
                    continue;
                }
                boolean savesDisk = none == null || r.fileBytes < none.fileBytes * 0.85;
                boolean keepsUp = noneRate <= 0 || r.eventsPerSec() >= noneRate * 0.55;
                if (savesDisk && keepsUp && r.eventsPerSec() >= bestRate * 0.9) {
                    if (best == null || r.fileBytes < best.fileBytes
                            || (r.fileBytes <= best.fileBytes * 1.05 && r.eventsPerSec() > bestRate)) {
                        best = r;
                        bestRate = r.eventsPerSec();
                    }
                }
            }
            if (best == null) {
                best = fastest(high);
            }
            return best;
        }

        private Row pickQuiet() {
            List<Row> quiet = rowsFor(Workload.QUIET_BURST);
            if (quiet.isEmpty()) {
                return null;
            }
            Row lz4 = find(quiet, CompressionType.LZ4);
            double minRate = lz4 != null ? lz4.eventsPerSec() * 0.25 : 0;
            Row best = null;
            for (Row r : quiet) {
                if (r.eventsPerSec() < minRate && minRate > 0 && r.compression != CompressionType.LZ4) {
                    continue;
                }
                if (best == null || r.fileBytes < best.fileBytes) {
                    best = r;
                }
            }
            return best != null ? best : fastest(quiet);
        }

        private List<Row> rowsFor(Workload w) {
            List<Row> out = new ArrayList<>();
            for (Row r : rows) {
                if (r.workload == w) {
                    out.add(r);
                }
            }
            return out;
        }

        private static Row find(List<Row> rows, int compression) {
            for (Row r : rows) {
                if (r.compression == compression) {
                    return r;
                }
            }
            return null;
        }

        private static Row fastest(List<Row> rows) {
            Row best = null;
            for (Row r : rows) {
                if (best == null || r.eventsPerSec() > best.eventsPerSec()) {
                    best = r;
                }
            }
            return best;
        }

        private static String esc(String s) {
            if (s == null) {
                return "";
            }
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    public static void main(String[] args) throws IOException {
        Report report = run(Config.interactive());
        System.out.print(report.toPlainText());
    }

    public static Report run(Config config) throws IOException {
        Config c = config == null ? Config.interactive() : config;
        warmup(c);
        Report report = new Report();
        for (Workload w : Workload.values()) {
            for (int codec : CODECS) {
                report.rows.add(runOne(c, w, codec));
            }
        }
        return report;
    }

    private static void warmup(Config c) throws IOException {
        Config w = new Config();
        w.width = c.width;
        w.height = c.height;
        w.dtUs = c.dtUs;
        w.tempDir = c.tempDir;
        w.highRatePackets = 2;
        w.highRateEventsPerPacket = Math.min(256, c.highRateEventsPerPacket);
        w.quietPackets = 0;
        w.burstPackets = 0;
        runOne(w, Workload.HIGH_RATE, CompressionType.LZ4);
    }

    private static Row runOne(Config c, Workload workload, int compression) throws IOException {
        File file = File.createTempFile("jaer-aedat4-write-bench-", ".aedat4", c.tempDir);
        EventPacket<PolarityEvent> packet = new EventPacket<>(PolarityEvent.class);
        PacketBundle bundle = new PacketBundle();
        Random rng = new Random(1L);
        int packets = packetCount(c, workload);
        long events = 0;
        long wallNs;
        long uncompressed;
        long compressed;
        try (Aedat4FileOutputStream out = new Aedat4FileOutputStream(file, null, compression)) {
            long t0 = System.nanoTime();
            for (int p = 0; p < packets; p++) {
                int n = eventsInPacket(c, workload, p);
                fillPacket(packet, rng, c, workload, p, n);
                bundle.clear();
                bundle.add(packet);
                out.writeBundle(bundle);
                events += n;
            }
            wallNs = System.nanoTime() - t0;
            uncompressed = out.getUncompressedPayloadBytes();
            compressed = out.getCompressedPayloadBytes();
        }
        long fileBytes = file.length();
        if (!file.delete()) {
            file.deleteOnExit();
        }
        return new Row(workload, compression, events, packets, wallNs, uncompressed, compressed, fileBytes);
    }

    private static int packetCount(Config c, Workload w) {
        if (w == Workload.HIGH_RATE) {
            return c.highRatePackets;
        }
        return c.quietPackets + c.burstPackets;
    }

    private static int eventsInPacket(Config c, Workload w, int packetIndex) {
        if (w == Workload.HIGH_RATE) {
            return c.highRateEventsPerPacket;
        }
        if (packetIndex >= c.quietPackets) {
            return c.burstEventsPerPacket;
        }
        return c.quietEventsPerPacket;
    }

    private static void fillPacket(EventPacket<PolarityEvent> packet, Random rng, Config c,
            Workload workload, int packetIndex, int n) {
        OutputEventIterator<PolarityEvent> it = packet.outputIterator();
        int t0 = packetIndex * c.dtUs;
        boolean high = workload == Workload.HIGH_RATE;
        int clusterX = 40 + (packetIndex % 16);
        int clusterY = 40 + ((packetIndex / 16) % 16);
        for (int i = 0; i < n; i++) {
            PolarityEvent e = it.nextOutput();
            e.timestamp = t0 + i;
            if (high) {
                e.x = (short) rng.nextInt(c.width);
                e.y = (short) rng.nextInt(c.height);
            } else {
                e.x = (short) (clusterX + (i & 7));
                e.y = (short) (clusterY + ((i >> 3) & 7));
            }
            e.setPolarity((i & 1) == 0 ? PolarityEvent.Polarity.On : PolarityEvent.Polarity.Off);
        }
    }
}
