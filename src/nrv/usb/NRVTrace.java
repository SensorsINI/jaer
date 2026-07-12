package nrv.usb;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Optional NRV diagnostics (off by default for acquisition performance).
 * <ul>
 * <li>{@code -Djaer.nrv.trace.timestampOrder=true} — log first non-monotonic timestamp per USB chunk</li>
 * <li>{@code -Djaer.nrv.trace.timing=true} — throttled parser / timing-resync trace (development)</li>
 * <li>{@code -Djaer.nrv.trace.timing.intervalMs=2000} — throttle interval for timing trace (default 2000)</li>
 * </ul>
 *
 * @see https://nrv.kr/
 */
final class NRVTrace {

    private static final Logger LOG = Logger.getLogger("net.sf.jaer");

    static final boolean TIMESTAMP_ORDER_ENABLED = Boolean.getBoolean("jaer.nrv.trace.timestampOrder");
    static final boolean TIMING_ENABLED = Boolean.getBoolean("jaer.nrv.trace.timing");
    static final long TIMING_INTERVAL_MS = parsePositiveLong(
            System.getProperty("jaer.nrv.trace.timing.intervalMs"), 2000L);

    private static long lastTimingLogMs;

    private NRVTrace() {
    }

    static void logTimingResync(int regAddr, String reason, long originUs, long lastOutUs, int posX0) {
        if (!TIMING_ENABLED) {
            return;
        }
        LOG.info(String.format(
                "NRV timing resync: reg=0x%04X reason=%s originUs=%d lastOutUs=%d posX0=%d (ref/full cleared, column kept)",
                regAddr, reason, originUs, lastOutUs, posX0));
    }

    static void logTimingSummary(S5KRC1SParser.TimingStats stats) {
        if (!TIMING_ENABLED) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (now - lastTimingLogMs < TIMING_INTERVAL_MS) {
            return;
        }
        lastTimingLogMs = now;
        LOG.info(String.format(
                "NRV timing trace: ref=%d sub=%d frmEnd=%d col=%d events=%d | refMs=%d fullUs=%d outUs=%d posX=%d maxChunkSpanUs=%d",
                stats.refPackets, stats.subPackets, stats.frameEndPackets, stats.colPackets, stats.eventCount,
                stats.refMs0, stats.fullUs0, stats.lastOutUs, stats.posX0, stats.maxChunkSpanUs));
        stats.resetIntervalCounters();
    }

    static void logTimingRefSub(int sensorID, boolean sub, long refMs, int subUs, long fullUs) {
        if (!TIMING_ENABLED) {
            return;
        }
        LOG.log(Level.FINE, String.format(
                "NRV ts pkt sensor=%d %s refMs=%d subUs=%d fullUs=%d",
                sensorID, sub ? "SUB" : "REF", refMs, subUs, fullUs));
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
