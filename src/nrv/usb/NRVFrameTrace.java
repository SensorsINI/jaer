package nrv.usb;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.HashSet;
import java.util.Set;

import net.sf.jaer.aemonitor.AEPacketRaw;

/**
 * Optional CSV trace for NRV sensor-frame cadence and playback slicing (off by default).
 * <ul>
 * <li>{@code -Djaer.nrv.trace.frames=true} — USB frame-end (0x0C), USB commits, viewer packets</li>
 * <li>{@code -Djaer.nrv.trace.playback=true} — {@code readPacketByTime} slices during file playback</li>
 * <li>{@code -Djaer.nrv.trace.playback.tinyLog=true} — INFO log for each slice with 1–9 events (unique timestamps)</li>
 * <li>{@code -Djaer.nrv.trace.frames.file=C:/temp/jaer-nrv-frames.csv} — append CSV (default below)</li>
 * <li>{@code -Djaer.nrv.trace.frames.intervalMs=2000} — INFO summary interval (default 2000)</li>
 * </ul>
 *
 * @see https://nrv.kr/
 */
public final class NRVFrameTrace {

    private static final Logger LOG = Logger.getLogger("net.sf.jaer");

    static final boolean FRAMES_ENABLED = Boolean.getBoolean("jaer.nrv.trace.frames");
    static final boolean PLAYBACK_ENABLED = Boolean.getBoolean("jaer.nrv.trace.playback");
    static final boolean PLAYBACK_TINY_LOG = Boolean.getBoolean("jaer.nrv.trace.playback.tinyLog");
    static final long INTERVAL_MS = parsePositiveLong(
            System.getProperty("jaer.nrv.trace.frames.intervalMs"), 2000L);

    private static final Object FILE_LOCK = new Object();
    private static BufferedWriter fileWriter;
    private static boolean fileHeaderWritten;

    private static long summaryStartMs;
    private static long usbFrameEnds;
    private static long usbFrameEndDeltaRefSum;
    private static long usbFrameEndEventsSum;
    private static long usbCommits;
    private static long usbCommitEventsSum;
    private static long viewerPackets;
    private static long viewerEventsSum;
    private static long playbackSlices;
    private static long playbackEmptySlices;
    private static long playbackMaxConsecutiveEmpty;
    private static long playbackConsecutiveEmpty;
    private static long playbackSkipRender;
    private static long playbackEventsSum;
    private static int playbackEventsMin = Integer.MAX_VALUE;
    private static int playbackEventsMax;
    private static long playbackSparseSlices;
    private static long playbackTinySlices;
    private static long playbackUniqueTsSum;
    private static long playbackUniqueTsSamples;

    private NRVFrameTrace() {
    }

    public static boolean isPlaybackEnabled() {
        return PLAYBACK_ENABLED;
    }

    static void logUsbFrameEnd(long frameIndex, long refMs, long fullUs, int outUs,
            int eventsSinceLastFrameEnd, boolean skipWindowActive, int posX,
            long prevRefMs, long prevFullUs) {
        if (!FRAMES_ENABLED) {
            return;
        }
        final long deltaRefMs = prevRefMs >= 0 ? refMs - prevRefMs : -1;
        final long deltaOutUs = prevFullUs >= 0 ? fullUs - prevFullUs : -1;
        noteUsbFrameEnd(deltaRefMs, eventsSinceLastFrameEnd);
        appendRow(String.format(
                "%d,usb_frame_end,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d",
                System.currentTimeMillis(),
                frameIndex, refMs, fullUs, outUs,
                deltaRefMs, deltaOutUs,
                eventsSinceLastFrameEnd,
                skipWindowActive ? 1 : 0,
                posX,
                0,
                0));
    }

    static void logUsbCommit(int events, int minOutUs, int maxOutUs, boolean overflowed) {
        if (!FRAMES_ENABLED || events <= 0) {
            return;
        }
        noteUsbCommit(events);
        appendRow(String.format(
                "%d,usb_commit,0,0,0,0,0,0,%d,%d,%d,%d,%d",
                System.currentTimeMillis(),
                events,
                minOutUs,
                maxOutUs,
                maxOutUs - minOutUs,
                overflowed ? 1 : 0,
                0));
    }

    static void logViewerPacket(AEPacketRaw packet) {
        if (!FRAMES_ENABLED || packet == null) {
            return;
        }
        final int n = packet.getNumEvents();
        int minTs = 0;
        int maxTs = 0;
        if (n > 0) {
            final int[] ts = packet.getTimestamps();
            minTs = ts[0];
            maxTs = ts[0];
            for (int i = 1; i < n; i++) {
                minTs = Math.min(minTs, ts[i]);
                maxTs = Math.max(maxTs, ts[i]);
            }
        }
        noteViewerPacket(n);
        appendRow(String.format(
                "%d,viewer_packet,0,0,0,0,0,0,%d,%d,%d,%d,%d",
                System.currentTimeMillis(),
                n,
                minTs,
                maxTs,
                n > 0 ? maxTs - minTs : 0,
                packet.overrunOccuredFlag ? 1 : 0,
                0));
    }

    public static void logPlaybackSlice(int sliceUs, AEPacketRaw packet) {
        if (!PLAYBACK_ENABLED || packet == null) {
            return;
        }
        final int n = packet.getNumEvents();
        int minTs = 0;
        int maxTs = 0;
        if (n > 0) {
            final int[] ts = packet.getTimestamps();
            minTs = ts[0];
            maxTs = ts[0];
            for (int i = 1; i < n; i++) {
                minTs = Math.min(minTs, ts[i]);
                maxTs = Math.max(maxTs, ts[i]);
            }
        }
        final int uniqueTs = countUniqueTimestamps(packet, n);
        final int spanUs = n > 0 ? maxTs - minTs : 0;
        notePlaybackSlice(n, uniqueTs);
        if (PLAYBACK_TINY_LOG && n > 0 && n < 10) {
            LOG.info(String.format(
                    "NRV playback tiny slice: sliceUs=%d events=%d uniqueTs=%d minTs=%d maxTs=%d spanUs=%d",
                    sliceUs, n, uniqueTs, minTs, maxTs, spanUs));
        }
        appendRow(String.format(
                "%d,playback_slice,0,0,0,0,0,0,%d,%d,%d,%d,%d",
                System.currentTimeMillis(),
                sliceUs,
                n,
                minTs,
                maxTs,
                spanUs,
                uniqueTs));
    }

    private static int countUniqueTimestamps(AEPacketRaw packet, int n) {
        if (n <= 0) {
            return 0;
        }
        if (n > 200_000) {
            return -1;
        }
        final int[] ts = packet.getTimestamps();
        final Set<Integer> unique = new HashSet<>(Math.min(n, 4096));
        for (int i = 0; i < n; i++) {
            unique.add(ts[i]);
        }
        return unique.size();
    }

    public static void logPlaybackSkipRender(int sliceUs) {
        if (!PLAYBACK_ENABLED) {
            return;
        }
        notePlaybackSkipRender();
        appendRow(String.format(
                "%d,playback_skip_render,0,0,0,0,0,0,%d,0,0,0,0,0",
                System.currentTimeMillis(),
                sliceUs));
    }

    private static void noteUsbFrameEnd(long deltaRefMs, int events) {
        synchronized (FILE_LOCK) {
            usbFrameEnds++;
            if (deltaRefMs > 0) {
                usbFrameEndDeltaRefSum += deltaRefMs;
            }
            usbFrameEndEventsSum += events;
            maybeFlushSummary();
        }
    }

    private static void noteUsbCommit(int events) {
        synchronized (FILE_LOCK) {
            usbCommits++;
            usbCommitEventsSum += events;
            maybeFlushSummary();
        }
    }

    private static void noteViewerPacket(int events) {
        synchronized (FILE_LOCK) {
            viewerPackets++;
            viewerEventsSum += events;
            maybeFlushSummary();
        }
    }

    private static void notePlaybackSlice(int events, int uniqueTs) {
        synchronized (FILE_LOCK) {
            playbackSlices++;
            playbackEventsSum += events;
            if (uniqueTs >= 0) {
                playbackUniqueTsSum += uniqueTs;
                playbackUniqueTsSamples++;
            }
            if (events < playbackEventsMin) {
                playbackEventsMin = events;
            }
            if (events > playbackEventsMax) {
                playbackEventsMax = events;
            }
            if (events == 0) {
                playbackEmptySlices++;
                playbackConsecutiveEmpty++;
                if (playbackConsecutiveEmpty > playbackMaxConsecutiveEmpty) {
                    playbackMaxConsecutiveEmpty = playbackConsecutiveEmpty;
                }
            } else {
                playbackConsecutiveEmpty = 0;
                if (events < 100) {
                    playbackSparseSlices++;
                }
                if (events < 10) {
                    playbackTinySlices++;
                }
            }
            maybeFlushSummary();
        }
    }

    private static void notePlaybackSkipRender() {
        synchronized (FILE_LOCK) {
            playbackSkipRender++;
            maybeFlushSummary();
        }
    }

    private static void maybeFlushSummary() {
        final long now = System.currentTimeMillis();
        if (summaryStartMs == 0) {
            summaryStartMs = now;
            return;
        }
        if (now - summaryStartMs < INTERVAL_MS) {
            return;
        }
        flushSummary(now);
    }

    private static void flushSummary(long now) {
        final double sec = (now - summaryStartMs) / 1000.0;
        if (sec <= 0) {
            summaryStartMs = now;
            return;
        }
        final StringBuilder sb = new StringBuilder("NRV frame trace summary:");
        if (FRAMES_ENABLED && usbFrameEnds > 0) {
            final double frameHz = usbFrameEnds / sec;
            final double avgDeltaRefMs = usbFrameEndDeltaRefSum / (double) usbFrameEnds;
            final double avgEvents = usbFrameEndEventsSum / (double) usbFrameEnds;
            sb.append(String.format(
                    " usbFrameEnd=%.1fHz avgDeltaRefMs=%.3f avgEventsPerFrame=%.1f",
                    frameHz, avgDeltaRefMs, avgEvents));
        }
        if (FRAMES_ENABLED && usbCommits > 0) {
            sb.append(String.format(" usbCommits=%.1f/s avgEvents=%.1f",
                    usbCommits / sec, usbCommitEventsSum / (double) usbCommits));
        }
        if (FRAMES_ENABLED && viewerPackets > 0) {
            sb.append(String.format(" viewerPackets=%.1f/s avgEvents=%.1f",
                    viewerPackets / sec, viewerEventsSum / (double) viewerPackets));
        }
        if (PLAYBACK_ENABLED && playbackSlices > 0) {
            final double avgEventsPerSlice = playbackEventsSum / (double) playbackSlices;
            final int minEv = playbackEventsMin == Integer.MAX_VALUE ? 0 : playbackEventsMin;
            final String uniqueTsPart = playbackUniqueTsSamples > 0
                    ? String.format(" avgUniqueTs=%.1f",
                            playbackUniqueTsSum / (double) playbackUniqueTsSamples)
                    : "";
            sb.append(String.format(
                    " playbackSlices=%.1f/s empty=%.1f%% maxConsecutiveEmpty=%d skipRender=%d"
                            + " eventsPerSlice avg=%.0f min=%d max=%d sparse(<100)=%d (%.1f%%) tiny(<10)=%d%s",
                    playbackSlices / sec,
                    100.0 * playbackEmptySlices / playbackSlices,
                    playbackMaxConsecutiveEmpty,
                    playbackSkipRender,
                    avgEventsPerSlice,
                    minEv,
                    playbackEventsMax,
                    playbackSparseSlices,
                    100.0 * playbackSparseSlices / playbackSlices,
                    playbackTinySlices,
                    uniqueTsPart));
        }
        LOG.info(sb.toString());
        usbFrameEnds = 0;
        usbFrameEndDeltaRefSum = 0;
        usbFrameEndEventsSum = 0;
        usbCommits = 0;
        usbCommitEventsSum = 0;
        viewerPackets = 0;
        viewerEventsSum = 0;
        playbackSlices = 0;
        playbackEmptySlices = 0;
        playbackMaxConsecutiveEmpty = 0;
        playbackConsecutiveEmpty = 0;
        playbackSkipRender = 0;
        playbackEventsSum = 0;
        playbackEventsMin = Integer.MAX_VALUE;
        playbackEventsMax = 0;
        playbackSparseSlices = 0;
        playbackTinySlices = 0;
        playbackUniqueTsSum = 0;
        playbackUniqueTsSamples = 0;
        summaryStartMs = now;
    }

    private static void appendRow(String row) {
        final String path = traceFilePath();
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
                    fileWriter.write("epochMs,kind,frameIdx,refMs,fullUs,outUs,deltaRefMs,deltaOutUs,"
                            + "sliceUs_or_events,numEvents_or_minTs,minTs_or_maxTs,maxTs_or_span,spanUs_or_flag,uniqueTs");
                    fileWriter.newLine();
                    fileHeaderWritten = true;
                }
                fileWriter.write(row);
                fileWriter.newLine();
                fileWriter.flush();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "NRV frame trace file write failed: " + e.getMessage());
                closeFile();
            }
        }
    }

    private static String traceFilePath() {
        String path = System.getProperty("jaer.nrv.trace.frames.file");
        if (path == null || path.isEmpty()) {
            path = System.getProperty("jaer.nrv.trace.file");
        }
        if (path == null || path.isEmpty()) {
            path = "C:/temp/jaer-nrv-frames.csv";
        }
        return path;
    }

    private static void closeFile() {
        if (fileWriter != null) {
            try {
                fileWriter.close();
            } catch (IOException ignored) {
            }
            fileWriter = null;
            fileHeaderWritten = false;
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
