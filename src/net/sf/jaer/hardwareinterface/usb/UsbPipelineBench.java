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
 * Optional USB reader pipeline microbenchmarks (off by default).
 * <ul>
 * <li>{@code -Djaer.usb.trace.pipeline=true} — aggregate per-chunk timings</li>
 * <li>{@code -Djaer.usb.trace.file=<path>} — append CSV rows (also logs INFO summary)</li>
 * <li>{@code -Djaer.usb.trace.intervalMs=2000} — summary interval (default 2000)</li>
 * </ul>
 * Enable together with {@code -Djaer.nrv.trace.timing=true} or
 * {@code -Djaer.prophesee.trace.pipeline=true} for driver-specific parser counters.
 */
public final class UsbPipelineBench {

    private static final Logger LOG = Logger.getLogger("net.sf.jaer");

    public static final boolean ENABLED = Boolean.getBoolean("jaer.usb.trace.pipeline");
    public static final long INTERVAL_MS = parsePositiveLong(
            System.getProperty("jaer.usb.trace.intervalMs"), 2000L);

    private static final Object FILE_LOCK = new Object();
    private static BufferedWriter fileWriter;
    private static boolean fileHeaderWritten;

    private static long windowStartMs;
    private static long chunks;
    private static long usbBytes;
    private static long events;
    private static long usbReadNs;
    private static long limitLockNs;
    private static long byteCopyNs;
    private static long parseNs;
    private static long commitLockNs;
    private static long arrayCopyNs;
    private static long totalNs;
    private static long maxTotalNs;
    private static String lastDriver;
    private static String lastThread;

    private UsbPipelineBench() {
    }

    /** Per-USB-chunk timings; nanosecond fields are 0 when not measured. */
    public static final class Sample {
        public String driver = "";
        public String threadName = "";
        public int usbBytes;
        public int eventsParsed;
        public long usbReadNs;
        public long limitLockNs;
        public long byteCopyNs;
        public long parseNs;
        public long commitLockNs;
        public long arrayCopyNs;
        public long totalNs;
    }

    public static Sample newSample(String driver) {
        if (!ENABLED) {
            return null;
        }
        final Sample s = new Sample();
        s.driver = driver;
        s.threadName = Thread.currentThread().getName();
        return s;
    }

    public static void record(Sample sample) {
        if (!ENABLED || sample == null) {
            return;
        }
        final long now = System.currentTimeMillis();
        synchronized (UsbPipelineBench.class) {
            if (windowStartMs == 0) {
                windowStartMs = now;
            }
            lastDriver = sample.driver;
            lastThread = sample.threadName;
            chunks++;
            usbBytes += sample.usbBytes;
            events += sample.eventsParsed;
            usbReadNs += sample.usbReadNs;
            limitLockNs += sample.limitLockNs;
            byteCopyNs += sample.byteCopyNs;
            parseNs += sample.parseNs;
            commitLockNs += sample.commitLockNs;
            arrayCopyNs += sample.arrayCopyNs;
            totalNs += sample.totalNs;
            if (sample.totalNs > maxTotalNs) {
                maxTotalNs = sample.totalNs;
            }
            appendCsvRow(sample);
            if (now - windowStartMs >= INTERVAL_MS) {
                flushSummary(now);
            }
        }
    }

    private static void flushSummary(long now) {
        if (chunks == 0) {
            windowStartMs = now;
            return;
        }
        final double ms = (now - windowStartMs) / 1000.0;
        final double chunksPerS = chunks / ms;
        final double mbPerS = (usbBytes / (1024.0 * 1024.0)) / ms;
        final double keps = (events / 1000.0) / ms;
        final double avgTotalUs = (totalNs / chunks) / 1000.0;
        final double avgParseUs = (parseNs / chunks) / 1000.0;
        final double avgCommitUs = (commitLockNs / chunks) / 1000.0;
        final String msg = String.format(
                "USB pipeline [%s thread=%s]: %.1f chunks/s  %.2f MB/s  %.1f keps  "
                        + "avgUs total=%.1f parse=%.1f commitLock=%.1f limitLock=%.1f copy=%.1f arrayCopy=%.1f usbRead=%.1f maxChunkUs=%.1f",
                lastDriver, lastThread, chunksPerS, mbPerS, keps,
                avgTotalUs, avgParseUs, avgCommitUs,
                (limitLockNs / chunks) / 1000.0,
                (byteCopyNs / chunks) / 1000.0,
                (arrayCopyNs / chunks) / 1000.0,
                (usbReadNs / chunks) / 1000.0,
                maxTotalNs / 1000.0);
        LOG.info(msg);
        resetWindow(now);
    }

    private static void resetWindow(long now) {
        windowStartMs = now;
        chunks = 0;
        usbBytes = 0;
        events = 0;
        usbReadNs = 0;
        limitLockNs = 0;
        byteCopyNs = 0;
        parseNs = 0;
        commitLockNs = 0;
        arrayCopyNs = 0;
        totalNs = 0;
        maxTotalNs = 0;
    }

    private static void appendCsvRow(Sample s) {
        final String path = System.getProperty("jaer.usb.trace.file");
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
                    fileWriter.write("epochMs,driver,thread,usbBytes,events,usbReadNs,limitLockNs,byteCopyNs,parseNs,commitLockNs,arrayCopyNs,totalNs");
                    fileWriter.newLine();
                    fileHeaderWritten = true;
                }
                fileWriter.write(String.format("%d,%s,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d",
                        System.currentTimeMillis(),
                        csv(s.driver), csv(s.threadName),
                        s.usbBytes, s.eventsParsed,
                        s.usbReadNs, s.limitLockNs, s.byteCopyNs, s.parseNs,
                        s.commitLockNs, s.arrayCopyNs, s.totalNs));
                fileWriter.newLine();
                fileWriter.flush();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "USB pipeline trace file write failed: " + e.getMessage());
                closeFile();
            }
        }
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
