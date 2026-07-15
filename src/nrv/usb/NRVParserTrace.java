package nrv.usb;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parser-side diagnostics for NRV timestamp quantization and event-rate steadiness (off by default).
 * <ul>
 * <li>{@code -Djaer.nrv.trace.parser=true} — per-ms output-timestamp buckets and per-frame-end stats</li>
 * <li>{@code -Djaer.nrv.trace.parser.intervalMs=2000} — INFO summary interval (default 2000)</li>
 * <li>{@code -Djaer.nrv.trace.parser.file=C:/temp/jaer-nrv-parser.csv} — optional append CSV (batched)</li>
 * <li>{@code -Djaer.nrv.trace.parser.burstLog=true} — FINE log for ms buckets with &ge;1000 events (never INFO; too slow)</li>
 * <li>{@code -Djaer.nrv.trace.parser.tinyLog=true} — FINE log for ms buckets with 1–9 events (never INFO; too slow)</li>
 * <li>{@code -Djaer.nrv.trace.parser.sampleLog=true} — up to 3 tiny + 3 burst examples per INFO summary interval</li>
 * </ul>
 *
 * <p>Use during <strong>live USB</strong> acquisition to distinguish sensor/event bursts from timestamp
 * decoding (1 ms ref quantization vs sub/column spreading). Playback uses stored timestamps; compare
 * with {@code scripts/analyze-nrv-recording-events.py} on text exports.
 *
 * @see https://nrv.kr/
 */
public final class NRVParserTrace {

    private static final Logger LOG = Logger.getLogger("net.sf.jaer");

    static final boolean ENABLED = Boolean.getBoolean("jaer.nrv.trace.parser");
    static final boolean BURST_LOG = Boolean.getBoolean("jaer.nrv.trace.parser.burstLog");
    static final boolean TINY_LOG = Boolean.getBoolean("jaer.nrv.trace.parser.tinyLog");
    static final boolean SAMPLE_LOG = Boolean.getBoolean("jaer.nrv.trace.parser.sampleLog");
    static final long INTERVAL_MS = parsePositiveLong(
            System.getProperty("jaer.nrv.trace.parser.intervalMs"), 2000L);
    private static final int CSV_FLUSH_ROWS = parsePositiveInt(
            System.getProperty("jaer.nrv.trace.parser.csvFlushRows"), 512);
    private static final int SAMPLE_LOG_MAX = 3;

    private static final Object FILE_LOCK = new Object();
    private static BufferedWriter fileWriter;
    private static boolean fileHeaderWritten;
    private static final List<String> pendingCsvRows = new ArrayList<>(CSV_FLUSH_ROWS);

    private static int sampleTinyLogged;
    private static int sampleBurstLogged;

    private static long summaryStartMs = System.currentTimeMillis();

    private static int openBucketTs = Integer.MIN_VALUE;
    private static int openBucketCount;
    private static int openBucketSensor = -1;

    private static long msBucketsClosed;
    private static long msBucketEventsSum;
    private static int msBucketEventsMin = Integer.MAX_VALUE;
    private static int msBucketEventsMax;
    private static long msBucketTiny;
    private static long msBucketSparse;
    private static long msBucketBurst;

    private static long frameEnds;
    private static long frameEventsSum;
    private static int frameEventsMin = Integer.MAX_VALUE;
    private static int frameEventsMax;
    private static long deltaRefSum;
    private static long deltaRefMin = Long.MAX_VALUE;
    private static long deltaRefMax;
    private static long deltaRefSamples;

    private NRVParserTrace() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    /** Events decoded with the same jAER output timestamp (µs relative to origin). */
    static void noteEvents(int outputTs, int count, int sensorID) {
        if (!ENABLED || count <= 0) {
            return;
        }
        if (outputTs != openBucketTs) {
            closeMsBucket();
            openBucketTs = outputTs;
            openBucketCount = 0;
            openBucketSensor = sensorID;
        }
        openBucketCount += count;
    }

    static void noteFrameEnd(int sensorID, long refMs, long fullUs, int outUs,
            int eventsSinceFrameEnd, long prevRefMs) {
        if (!ENABLED) {
            return;
        }
        final long deltaRefMs = prevRefMs >= 0 ? refMs - prevRefMs : -1;
        noteFrameEndStats(eventsSinceFrameEnd, deltaRefMs);
        appendRow(String.format(
                "%d,frame_end,%d,%d,%d,%d,%d,%d,%d",
                System.currentTimeMillis(),
                sensorID,
                outUs,
                eventsSinceFrameEnd,
                refMs,
                deltaRefMs,
                fullUs,
                0));
        if (BURST_LOG && eventsSinceFrameEnd >= 1000) {
            LOG.log(Level.FINE, String.format(
                    "NRV parser burst frame: sensor=%d refMs=%d deltaRefMs=%d events=%d fullUs=%d outUs=%d",
                    sensorID, refMs, deltaRefMs, eventsSinceFrameEnd, fullUs, outUs));
        }
        if (SAMPLE_LOG && eventsSinceFrameEnd >= 1000 && sampleBurstLogged < SAMPLE_LOG_MAX) {
            sampleBurstLogged++;
            LOG.info(String.format(
                    "NRV parser sample burst frame: sensor=%d refMs=%d events=%d",
                    sensorID, refMs, eventsSinceFrameEnd));
        }
    }

    /** Called at end of each USB parse; flushes open bucket and may emit interval summary. */
    static void endParseChunk() {
        if (!ENABLED) {
            return;
        }
        maybeLogSummary(false);
        flushCsv(false);
    }

    static void shutdown() {
        if (!ENABLED) {
            return;
        }
        closeMsBucket();
        flushCsv(true);
        maybeLogSummary(true);
        synchronized (FILE_LOCK) {
            if (fileWriter != null) {
                try {
                    fileWriter.flush();
                    fileWriter.close();
                } catch (IOException ignored) {
                    // best effort
                }
                fileWriter = null;
                fileHeaderWritten = false;
            }
        }
    }

    private static void closeMsBucket() {
        if (openBucketCount <= 0 || openBucketTs == Integer.MIN_VALUE) {
            openBucketTs = Integer.MIN_VALUE;
            openBucketCount = 0;
            openBucketSensor = -1;
            return;
        }
        final int events = openBucketCount;
        final int outputTs = openBucketTs;
        final int sensor = openBucketSensor;
        noteMsBucketStats(events);
        appendRow(String.format(
                "%d,ms_bucket,%d,%d,%d,0,0,0,0",
                System.currentTimeMillis(),
                sensor,
                outputTs,
                events,
                0,
                0,
                0,
                0));
        if (BURST_LOG && events >= 1000) {
            LOG.log(Level.FINE, String.format(
                    "NRV parser burst ms bucket: sensor=%d outputTs=%d events=%d",
                    sensor, outputTs, events));
        } else if (TINY_LOG && events > 0 && events < 10) {
            LOG.log(Level.FINE, String.format(
                    "NRV parser tiny ms bucket: sensor=%d outputTs=%d events=%d",
                    sensor, outputTs, events));
        }
        if (SAMPLE_LOG && events >= 1000 && sampleBurstLogged < SAMPLE_LOG_MAX) {
            sampleBurstLogged++;
            LOG.info(String.format(
                    "NRV parser sample burst ms bucket: sensor=%d outputTs=%d events=%d",
                    sensor, outputTs, events));
        } else if (SAMPLE_LOG && events > 0 && events < 10 && sampleTinyLogged < SAMPLE_LOG_MAX) {
            sampleTinyLogged++;
            LOG.info(String.format(
                    "NRV parser sample tiny ms bucket: sensor=%d outputTs=%d events=%d",
                    sensor, outputTs, events));
        }
        openBucketTs = Integer.MIN_VALUE;
        openBucketCount = 0;
        openBucketSensor = -1;
    }

    private static void noteMsBucketStats(int events) {
        msBucketsClosed++;
        msBucketEventsSum += events;
        if (events < msBucketEventsMin) {
            msBucketEventsMin = events;
        }
        if (events > msBucketEventsMax) {
            msBucketEventsMax = events;
        }
        if (events < 10) {
            msBucketTiny++;
        } else if (events < 100) {
            msBucketSparse++;
        } else if (events >= 1000) {
            msBucketBurst++;
        }
    }

    private static void noteFrameEndStats(int events, long deltaRefMs) {
        frameEnds++;
        frameEventsSum += events;
        if (events < frameEventsMin) {
            frameEventsMin = events;
        }
        if (events > frameEventsMax) {
            frameEventsMax = events;
        }
        if (deltaRefMs >= 0) {
            deltaRefSamples++;
            deltaRefSum += deltaRefMs;
            if (deltaRefMs < deltaRefMin) {
                deltaRefMin = deltaRefMs;
            }
            if (deltaRefMs > deltaRefMax) {
                deltaRefMax = deltaRefMs;
            }
        }
    }

    private static void maybeLogSummary(boolean force) {
        final long now = System.currentTimeMillis();
        if (!force && now - summaryStartMs < INTERVAL_MS) {
            return;
        }
        if (msBucketsClosed == 0 && frameEnds == 0) {
            summaryStartMs = now;
            return;
        }
        final double avgMsBucket = msBucketsClosed > 0
                ? (double) msBucketEventsSum / msBucketsClosed : 0;
        final double avgFrame = frameEnds > 0
                ? (double) frameEventsSum / frameEnds : 0;
        final double avgDeltaRef = deltaRefSamples > 0
                ? (double) deltaRefSum / deltaRefSamples : -1;
        final double tinyPct = msBucketsClosed > 0 ? 100.0 * msBucketTiny / msBucketsClosed : 0;
        final double burstPct = msBucketsClosed > 0 ? 100.0 * msBucketBurst / msBucketsClosed : 0;
        LOG.info(String.format(
                "NRV parser trace: msBuckets=%d avg/min/max=%.0f/%d/%d tiny=%d(%.1f%%) sparse=%d burst=%d(%.1f%%)"
                        + " | frames=%d avg/min/max=%.0f/%d/%d deltaRefMs avg/min/max=%.2f/%d/%d",
                msBucketsClosed, avgMsBucket, bucketMinOrNa(msBucketEventsMin), msBucketEventsMax,
                msBucketTiny, tinyPct, msBucketSparse, msBucketBurst, burstPct,
                frameEnds, avgFrame, frameMinOrNa(frameEventsMin), frameEventsMax,
                avgDeltaRef, deltaMinOrNa(deltaRefMin), deltaRefMax));
        resetInterval();
        sampleTinyLogged = 0;
        sampleBurstLogged = 0;
        summaryStartMs = now;
    }

    private static int bucketMinOrNa(int v) {
        return v == Integer.MAX_VALUE ? 0 : v;
    }

    private static int frameMinOrNa(int v) {
        return v == Integer.MAX_VALUE ? 0 : v;
    }

    private static long deltaMinOrNa(long v) {
        return v == Long.MAX_VALUE ? 0 : v;
    }

    private static void resetInterval() {
        msBucketsClosed = 0;
        msBucketEventsSum = 0;
        msBucketEventsMin = Integer.MAX_VALUE;
        msBucketEventsMax = 0;
        msBucketTiny = 0;
        msBucketSparse = 0;
        msBucketBurst = 0;
        frameEnds = 0;
        frameEventsSum = 0;
        frameEventsMin = Integer.MAX_VALUE;
        frameEventsMax = 0;
        deltaRefSum = 0;
        deltaRefMin = Long.MAX_VALUE;
        deltaRefMax = 0;
        deltaRefSamples = 0;
    }

    private static void appendRow(String line) {
        synchronized (FILE_LOCK) {
            if (resolveFilePath() == null) {
                return;
            }
            pendingCsvRows.add(line);
            if (pendingCsvRows.size() >= CSV_FLUSH_ROWS) {
                flushCsvLocked(true);
            }
        }
    }

    private static void flushCsv(boolean force) {
        synchronized (FILE_LOCK) {
            flushCsvLocked(force);
        }
    }

    private static void flushCsvLocked(boolean force) {
        if (!force && pendingCsvRows.size() < CSV_FLUSH_ROWS) {
            return;
        }
        if (pendingCsvRows.isEmpty()) {
            return;
        }
        if (fileWriter == null) {
            final Path path = resolveFilePath();
            if (path == null) {
                pendingCsvRows.clear();
                return;
            }
            try {
                Files.createDirectories(path.getParent());
                fileWriter = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "NRV parser trace: cannot open " + path, e);
                pendingCsvRows.clear();
                return;
            }
        }
        try {
            if (!fileHeaderWritten) {
                fileWriter.write(
                        "epochMs,kind,sensorID,outputTsUs,eventCount,refMs,deltaRefMs,fullUs");
                fileWriter.newLine();
                fileHeaderWritten = true;
            }
            for (String line : pendingCsvRows) {
                fileWriter.write(line);
                fileWriter.newLine();
            }
            fileWriter.flush();
            pendingCsvRows.clear();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "NRV parser trace: write failed", e);
        }
    }

    private static Path resolveFilePath() {
        String path = System.getProperty("jaer.nrv.trace.parser.file");
        if (path == null || path.isEmpty()) {
            return null;
        }
        return Paths.get(path);
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

    private static int parsePositiveInt(String raw, int defaultValue) {
        if (raw == null || raw.isEmpty()) {
            return defaultValue;
        }
        try {
            final int v = Integer.parseInt(raw.trim());
            return v > 0 ? v : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
