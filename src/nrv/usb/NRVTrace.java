package nrv.usb;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Optional NRV diagnostics. Timestamp-order and timing traces are off unless
 * enabled with {@code -D} flags. Host-vs-device clock drift is {@code FINE}
 * (file log, not console), silent until a software timestamp zero, then every
 * 5 s for 3 min and once a minute after that. Disable with
 * {@code -Djaer.nrv.trace.clockDrift=false}.
 * <ul>
 * <li>{@code -Djaer.nrv.trace.timestampOrder=true} — log first non-monotonic timestamp per USB chunk</li>
 * <li>{@code -Djaer.nrv.trace.timing=true} — throttled parser / timing-resync trace (development)</li>
 * <li>{@code -Djaer.nrv.trace.timing.intervalMs=2000} — throttle interval for timing trace (default 2000)</li>
 * <li>{@code -Djaer.nrv.trace.clockDrift=false} — disable host vs device clock log</li>
 * <li>{@code -Djaer.nrv.hostTimeStretch=false} — leave USB timestamps as device µs (default stretches onto nanoTime)</li>
 * </ul>
 *
 * @see https://nrv.kr/
 */
final class NRVTrace {

    private static final Logger LOG = Logger.getLogger("net.sf.jaer");

    static final boolean TIMESTAMP_ORDER_ENABLED = Boolean.getBoolean("jaer.nrv.trace.timestampOrder");
    static final boolean TIMING_ENABLED = Boolean.getBoolean("jaer.nrv.trace.timing");
    static final boolean CLOCK_DRIFT_ENABLED = !"false".equalsIgnoreCase(
            System.getProperty("jaer.nrv.trace.clockDrift", "true"));
    static final long TIMING_INTERVAL_MS = parsePositiveLong(
            System.getProperty("jaer.nrv.trace.timing.intervalMs"), 2000L);
    private static final long CLOCK_DRIFT_FAST_WINDOW_MS = 3L * 60L * 1000L;
    private static final long CLOCK_DRIFT_FAST_INTERVAL_MS = 5_000L;
    private static final long CLOCK_DRIFT_SLOW_INTERVAL_MS = 60_000L;

    private static long lastTimingLogMs;
    private static long lastClockDriftLogMs;

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

    static void onClockDriftOriginReset() {
        lastClockDriftLogMs = 0L;
    }

    /**
     * Compare NRV device µs since origin to {@link System#nanoTime()} since the
     * same origin. Silent until software timestamp zero; then FINE every 5 s for
     * 3 min and once a minute after that.
     */
    static void logClockDrift(S5KRC1SParser parser) {
        if (!CLOCK_DRIFT_ENABLED || parser == null || !LOG.isLoggable(Level.FINE)) {
            return;
        }
        final long armMs = parser.getClockDriftLogArmMs();
        if (armMs <= 0) {
            return;
        }
        final long now = System.currentTimeMillis();
        final long intervalMs = (now - armMs) < CLOCK_DRIFT_FAST_WINDOW_MS
                ? CLOCK_DRIFT_FAST_INTERVAL_MS
                : CLOCK_DRIFT_SLOW_INTERVAL_MS;
        if (now - lastClockDriftLogMs < intervalMs) {
            return;
        }
        final S5KRC1SParser.ClockDrift d = parser.sampleClockDrift();
        if (d == null || d.hostElapsedUs < 500_000L) {
            return;
        }
        lastClockDriftLogMs = now;
        final double pct = d.hostElapsedUs > 0 ? 100.0 * d.driftUs / d.hostElapsedUs : 0;
        final long stretchedDriftUs = d.stretchedElapsedUs - d.hostElapsedUs;
        LOG.fine(String.format(
                "NRV clock vs host: device=%.3fs host=%.3fs drift=%+.0fms (%+.2f%%) extraMsBumps=%d (%.2f/s)"
                        + " stretch=%s scale=%.5f stretchedDrift=%+.0fms",
                d.deviceElapsedUs * 1e-6, d.hostElapsedUs * 1e-6, d.driftUs / 1000.0, pct,
                d.extraMsBumps, d.hostElapsedUs > 0 ? 1e6 * d.extraMsBumps / d.hostElapsedUs : 0,
                d.stretchEnabled ? "on" : "off", d.hostScale, stretchedDriftUs / 1000.0));
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
