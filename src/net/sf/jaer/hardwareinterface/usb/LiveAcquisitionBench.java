package net.sf.jaer.hardwareinterface.usb;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.EnumMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jaer.aemonitor.DroppedDataInfo;
import net.sf.jaer.event.AcquisitionMetadata;
import net.sf.jaer.event.PacketType;

/**
 * ViewLoop-level live acquisition metrics (off by default). Complements
 * {@link UsbPipelineBench} (USB-thread chunk timings).
 * <ul>
 * <li>{@code -Djaer.live.bench=true} — source counts/loss/epochs, FPS, heap</li>
 * <li>{@code -Djaer.live.bench.file=&lt;path&gt;} — append CSV summary rows</li>
 * <li>{@code -Djaer.live.bench.intervalMs=2000} — summary interval (default 2000)</li>
 * </ul>
 * Capture authoritative DAVIS/SciDVS baselines and legacy-interface comparison
 * baselines; see {@code docs/usb-live-acquisition-bench.md}.
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
    private static final EnumMap<PacketType, Long> sourceAcceptedCounts
            = new EnumMap<>(PacketType.class);
    private static long exactLossEvents;
    private static long unquantifiedLossRecords;
    private static long timestampEpochObservations;
    private static long lastAcquisitionSessionId = -1;
    private static long lastSequenceId = -1;
    private static String lastTimestampEpochs = "";
    private static String lastLossDetail = "";
    private static long viewLoopNs;
    private static long maxViewLoopNs;
    private static String lastChip = "";
    private static String lastDriver = "";
    private static boolean lastTypedDemux;
    private static long lastHeapUsedBytes;

    private LiveAcquisitionBench() {
    }

    /**
     * Records an authoritative typed ViewLoop iteration. Source accepted counts,
     * timestamp epochs, and exact/unquantified losses come directly from the
     * sealed acquisition metadata and are not inferred from an {@code AEPacketRaw}.
     */
    public static void recordTyped(String chipName, String driverName,
            AcquisitionMetadata metadata, int polarityEventCount,
            long viewLoopNsSample) {
        if (!isEnabled()) {
            return;
        }
        if (metadata == null || !metadata.isSealed()) {
            throw new IllegalArgumentException(
                    "typed live bench requires sealed acquisition metadata");
        }
        final long now = System.currentTimeMillis();
        final Runtime rt = Runtime.getRuntime();
        final long heapUsed = rt.totalMemory() - rt.freeMemory();
        synchronized (LiveAcquisitionBench.class) {
            recordCommon(chipName, driverName, true, polarityEventCount,
                    viewLoopNsSample, now, heapUsed);
            for (final Map.Entry<PacketType, Long> accepted
                    : metadata.getAcceptedCounts().entrySet()) {
                sourceAcceptedCounts.merge(accepted.getKey(), accepted.getValue(),
                        Math::addExact);
            }
            final StringJoiner lossDetail = new StringJoiner("; ");
            for (final AcquisitionMetadata.LossRecord loss : metadata.getLossRecords()) {
                if (loss.isExact()) {
                    exactLossEvents = Math.addExact(exactLossEvents, loss.getExactCount());
                    lossDetail.add(loss.getPacketType() + "=" + loss.getExactCount()
                            + " (" + loss.getReason() + ")");
                } else {
                    unquantifiedLossRecords = Math.addExact(unquantifiedLossRecords, 1);
                    lossDetail.add(loss.getPacketType() + "=? (" + loss.getReason() + ")");
                }
            }
            if (lossDetail.length() > 0) {
                lastLossDetail = lossDetail.toString();
            }
            lastAcquisitionSessionId = metadata.getAcquisitionSessionId();
            lastSequenceId = metadata.getSequenceId();
            timestampEpochObservations = Math.addExact(timestampEpochObservations,
                    metadata.getTimestampEpochs().size());
            final StringJoiner epochs = new StringJoiner("|");
            for (final long epoch : metadata.getTimestampEpochs()) {
                epochs.add(Long.toString(epoch));
            }
            lastTimestampEpochs = epochs.toString();
            flushSummaryIfDue(now);
        }
    }

    /**
     * Legacy compatibility adapter for raw acquisition mode. The caller passes
     * the raw source count and the driver's structured loss snapshot; the bench
     * itself never reads an {@code AEPacketRaw}.
     */
    public static void recordLegacy(String chipName, String driverName,
            int polarityEventCount, int rawEventCount,
            DroppedDataInfo droppedData, long viewLoopNsSample) {
        if (!isEnabled()) {
            return;
        }
        final long now = System.currentTimeMillis();
        final Runtime rt = Runtime.getRuntime();
        final long heapUsed = rt.totalMemory() - rt.freeMemory();
        synchronized (LiveAcquisitionBench.class) {
            recordCommon(chipName, driverName, false, polarityEventCount,
                    viewLoopNsSample, now, heapUsed);
            rawEvents = Math.addExact(rawEvents, Math.max(0, rawEventCount));
            if (droppedData != null && droppedData.any()) {
                overruns++;
                lastLossDetail = droppedData.getDetail();
            }
            flushSummaryIfDue(now);
        }
    }

    /** Retained source-compatible adapter for older callers. */
    public static void record(String chipName, String driverName, boolean typedDemux,
            int polarityEventCount, int rawEventCount, boolean overrun,
            long viewLoopNsSample) {
        recordLegacy(chipName, driverName, polarityEventCount, rawEventCount,
                overrun ? DroppedDataInfo.hostBufferOverrun() : DroppedDataInfo.none(),
                viewLoopNsSample);
    }

    private static void recordCommon(String chipName, String driverName,
            boolean typedDemux, int polarityEventCount, long viewLoopNsSample,
            long now, long heapUsed) {
        if (windowStartMs == 0) {
            windowStartMs = now;
        }
        lastChip = chipName != null ? chipName : "";
        lastDriver = driverName != null ? driverName : "";
        lastTypedDemux = typedDemux;
        lastHeapUsedBytes = heapUsed;
        frames++;
        polarityEvents = Math.addExact(polarityEvents,
                Math.max(0, polarityEventCount));
        viewLoopNs = Math.addExact(viewLoopNs, Math.max(0, viewLoopNsSample));
        if (viewLoopNsSample > maxViewLoopNs) {
            maxViewLoopNs = viewLoopNsSample;
        }
    }

    private static void flushSummaryIfDue(long now) {
        if (now - windowStartMs >= intervalMs()) {
            flushSummary(now);
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
        final long sourceEvents = sumSourceAcceptedCounts();
        final double sourceKeps = (sourceEvents / 1000.0) / sec;
        final double avgLoopMs = (viewLoopNs / frames) / 1_000_000.0;
        final double heapMb = lastHeapUsedBytes / (1024.0 * 1024.0);
        final String msg = String.format(
                "Live bench [%s driver=%s typedDemux=%s]: %.1f fps  %.1f keps(pol)  "
                        + "%.1f keps(source)  %.1f keps(legacyRaw)  accepted={%s}  "
                        + "exactLoss=%d unquantifiedLossRecords=%d epochsObserved=%d "
                        + "session=%d sequence=%d epochs=%s legacyOverruns=%d  "
                        + "avgLoopMs=%.2f  maxLoopMs=%.2f  heapMB=%.1f%s",
                lastChip, lastDriver, lastTypedDemux, fps, keps, sourceKeps,
                rawKeps, formatSourceAcceptedCounts(), exactLossEvents,
                unquantifiedLossRecords, timestampEpochObservations,
                lastAcquisitionSessionId, lastSequenceId, lastTimestampEpochs,
                overruns, avgLoopMs, maxViewLoopNs / 1_000_000.0, heapMb,
                lastLossDetail.isEmpty() ? "" : " lossDetail=" + lastLossDetail);
        LOG.info(msg);
        appendCsvSummary(now, sec, fps, keps, rawKeps, sourceEvents,
                sourceKeps, avgLoopMs, heapMb);
        resetWindow(now);
    }

    private static void appendCsvSummary(long now, double sec, double fps, double keps,
            double rawKeps, long sourceEvents, double sourceKeps,
            double avgLoopMs, double heapMb) {
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
                            + "polarityEvents,kepsPol,rawEvents,kepsRaw,overruns,avgLoopMs,maxLoopMs,heapMB,"
                            + "sourceEvents,kepsSource,sourceAcceptedCounts,exactLossEvents,"
                            + "unquantifiedLossRecords,timestampEpochObservations,acquisitionSessionId,"
                            + "sequenceId,timestampEpochs,lossDetail");
                    fileWriter.newLine();
                    fileHeaderWritten = true;
                }
                fileWriter.write(String.format("%d,%s,%s,%s,%.3f,%d,%.2f,%d,%.2f,%d,%.2f,%d,%.3f,%.3f,%.2f,"
                        + "%d,%.2f,%s,%d,%d,%d,%d,%d,%s,%s",
                        now, csv(lastChip), csv(lastDriver), lastTypedDemux, sec, frames, fps,
                        polarityEvents, keps, rawEvents, rawKeps, overruns,
                        avgLoopMs, maxViewLoopNs / 1_000_000.0, heapMb,
                        sourceEvents, sourceKeps, csv(formatSourceAcceptedCounts()),
                        exactLossEvents, unquantifiedLossRecords,
                        timestampEpochObservations, lastAcquisitionSessionId,
                        lastSequenceId, csv(lastTimestampEpochs), csv(lastLossDetail)));
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
        sourceAcceptedCounts.clear();
        exactLossEvents = 0;
        unquantifiedLossRecords = 0;
        timestampEpochObservations = 0;
        lastAcquisitionSessionId = -1;
        lastSequenceId = -1;
        lastTimestampEpochs = "";
        lastLossDetail = "";
        viewLoopNs = 0;
        maxViewLoopNs = 0;
    }

    private static long sumSourceAcceptedCounts() {
        long total = 0;
        for (final long count : sourceAcceptedCounts.values()) {
            total = Math.addExact(total, count);
        }
        return total;
    }

    private static String formatSourceAcceptedCounts() {
        final StringJoiner counts = new StringJoiner(";");
        for (final Map.Entry<PacketType, Long> entry
                : sourceAcceptedCounts.entrySet()) {
            counts.add(entry.getKey() + "=" + entry.getValue());
        }
        return counts.toString();
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
