package net.sf.jaer.hardwareinterface.usb;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ViewLoop-level live acquisition metrics (off by default). Complements
 * {@link UsbPipelineBench} (USB-thread chunk timings).
 * <ul>
 * <li>{@code -Djaer.live.bench=true} — aggregate events/s, FPS, overruns, heap</li>
 * <li>{@code -Djaer.live.bench.file=&lt;path&gt;} — append CSV summary rows</li>
 * <li>{@code -Djaer.live.bench.intervalMs=2000} — summary interval (default 2000)</li>
 * </ul>
 * Capture baselines for Davis346 / NRV / Prophesee / DVS128 before and after
 * USB typed demux; see {@code docs/usb-live-acquisition-bench.md}.
 */
public final class LiveAcquisitionBench {

    private static final Logger LOG = Logger.getLogger("net.sf.jaer");

    /**
     * Read each call (not a {@code static final} from class-load time) so
     * {@code -Djaer.live.bench=true} applied in {@code JAERViewer.main} still
     * works when PowerShell passed the flag as an app argument.
     */
    public static boolean isEnabled() {
        return Boolean.getBoolean("jaer.live.bench");
    }

    public static long intervalMs() {
        return parsePositiveLong(System.getProperty("jaer.live.bench.intervalMs"), 2000L);
    }

    private static final Object FILE_LOCK = new Object();
    private static BufferedWriter fileWriter;
    private static boolean fileHeaderWritten;

    private static long windowStartMs;
    private static long frames;
    private static long polarityEvents;
    private static long rawEvents;
    private static long overruns;
    private static long viewLoopNs;
    private static long maxViewLoopNs;
    private static String lastChip = "";
    private static String lastDriver = "";
    private static boolean lastTypedDemux;
    private static long lastHeapUsedBytes;

    private LiveAcquisitionBench() {
    }

    /**
     * Record one ViewLoop iteration after acquire/filter (call when enabled).
     *
     * @param chipName chip class simple name
     * @param driverName hardware interface type name (or empty)
     * @param typedDemux true when ViewLoop used HW PacketBundle (no extractBundle)
     * @param polarityEventCount cooked polarity events this slice
     * @param rawEventCount raw AE count if available, else 0
     * @param overrun true if this slice reported an overrun
     * @param viewLoopNs nanoseconds for this ViewLoop iteration (acquire→pace)
     */
    public static void record(String chipName, String driverName, boolean typedDemux,
            int polarityEventCount, int rawEventCount, boolean overrun, long viewLoopNsSample) {
        if (!isEnabled()) {
            return;
        }
        final long now = System.currentTimeMillis();
        final Runtime rt = Runtime.getRuntime();
        final long heapUsed = rt.totalMemory() - rt.freeMemory();
        synchronized (LiveAcquisitionBench.class) {
            if (windowStartMs == 0) {
                windowStartMs = now;
            }
            lastChip = chipName != null ? chipName : "";
            lastDriver = driverName != null ? driverName : "";
            lastTypedDemux = typedDemux;
            lastHeapUsedBytes = heapUsed;
            frames++;
            polarityEvents += Math.max(0, polarityEventCount);
            rawEvents += Math.max(0, rawEventCount);
            if (overrun) {
                overruns++;
            }
            viewLoopNs += Math.max(0, viewLoopNsSample);
            if (viewLoopNsSample > maxViewLoopNs) {
                maxViewLoopNs = viewLoopNsSample;
            }
            if (now - windowStartMs >= intervalMs()) {
                flushSummary(now);
            }
        }
    }

    private static void flushSummary(long now) {
        if (frames == 0) {
            windowStartMs = now;
            return;
        }
        final double sec = (now - windowStartMs) / 1000.0;
        final double fps = frames / sec;
        final double keps = (polarityEvents / 1000.0) / sec;
        final double rawKeps = (rawEvents / 1000.0) / sec;
        final double avgLoopMs = (viewLoopNs / frames) / 1_000_000.0;
        final double heapMb = lastHeapUsedBytes / (1024.0 * 1024.0);
        final String msg = String.format(
                "Live bench [%s driver=%s typedDemux=%s]: %.1f fps  %.1f keps(pol)  %.1f keps(raw)  "
                        + "overruns=%d  avgLoopMs=%.2f  maxLoopMs=%.2f  heapMB=%.1f",
                lastChip, lastDriver, lastTypedDemux, fps, keps, rawKeps, overruns,
                avgLoopMs, maxViewLoopNs / 1_000_000.0, heapMb);
        LOG.info(msg);
        appendCsvSummary(now, sec, fps, keps, rawKeps, avgLoopMs, heapMb);
        resetWindow(now);
    }

    private static void appendCsvSummary(long now, double sec, double fps, double keps,
            double rawKeps, double avgLoopMs, double heapMb) {
        final String path = System.getProperty("jaer.live.bench.file");
        if (path == null || path.isEmpty()) {
            return;
        }
        synchronized (FILE_LOCK) {
            try {
                if (fileWriter == null) {
                    final Path p = Paths.get(path);
                    final Path parent = p.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    fileWriter = Files.newBufferedWriter(p, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                }
                if (!fileHeaderWritten) {
                    fileWriter.write("epochMs,chip,driver,typedDemux,windowSec,frames,fps,"
                            + "polarityEvents,kepsPol,rawEvents,kepsRaw,overruns,avgLoopMs,maxLoopMs,heapMB");
                    fileWriter.newLine();
                    fileHeaderWritten = true;
                }
                fileWriter.write(String.format("%d,%s,%s,%s,%.3f,%d,%.2f,%d,%.2f,%d,%.2f,%d,%.3f,%.3f,%.2f",
                        now, csv(lastChip), csv(lastDriver), lastTypedDemux, sec, frames, fps,
                        polarityEvents, keps, rawEvents, rawKeps, overruns,
                        avgLoopMs, maxViewLoopNs / 1_000_000.0, heapMb));
                fileWriter.newLine();
                fileWriter.flush();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Live acquisition bench file write failed: " + e.getMessage());
                closeFile();
            }
        }
    }

    private static void resetWindow(long now) {
        windowStartMs = now;
        frames = 0;
        polarityEvents = 0;
        rawEvents = 0;
        overruns = 0;
        viewLoopNs = 0;
        maxViewLoopNs = 0;
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    static void closeFile() {
        synchronized (FILE_LOCK) {
            if (fileWriter != null) {
                try {
                    fileWriter.close();
                } catch (IOException ignored) {
                }
                fileWriter = null;
                fileHeaderWritten = false;
            }
        }
    }

    private static long parsePositiveLong(String raw, long defaultValue) {
        if (raw == null || raw.isEmpty()) {
            return defaultValue;
        }
        try {
            final long v = Long.parseLong(raw.trim());
            return v > 0 ? v : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
