package net.sf.jaer.aemonitor;

/**
 * Snapshot of data-loss state for one acquisition poll cycle.
 * <p>
 * Drivers set richer kinds over time; {@link AEMonitorInterface#getDroppedDataInfo()}
 * defaults to mapping {@link AEMonitorInterface#overrunOccurred()} to
 * {@link Kind#HOST_BUFFER_OVERRUN}.
 */
public final class DroppedDataInfo {

    public enum Kind {
        NONE,
        HOST_BUFFER_OVERRUN,
        USB_OR_PARSE_LOSS,
        SENSOR_REPORTED_DROP
    }

    private static final int STATS_LINE_WIDTH = 10;
    private static final DroppedDataInfo NONE = new DroppedDataInfo(
            Kind.NONE, statsToken(""), "", 0, 0);

    private final Kind kind;
    private final String statsLineToken;
    private final String detail;
    private final long recentCount;
    private final long totalCount;

    private DroppedDataInfo(Kind kind, String statsLineToken, String detail, long recentCount, long totalCount) {
        this.kind = kind;
        this.statsLineToken = statsLineToken;
        this.detail = detail == null ? "" : detail;
        this.recentCount = recentCount;
        this.totalCount = totalCount;
    }

    public static DroppedDataInfo none() {
        return NONE;
    }

    public static DroppedDataInfo hostBufferOverrun() {
        return hostBufferOverrun(
                "Host event buffer overrun: newest events discarded until the viewer catches up.");
    }

    public static DroppedDataInfo hostBufferOverrun(String detail) {
        return new DroppedDataInfo(
                Kind.HOST_BUFFER_OVERRUN,
                statsToken("(overrun)"),
                detail,
                1,
                0);
    }

    public Kind getKind() {
        return kind;
    }

    public boolean any() {
        return kind != Kind.NONE;
    }

    /** Fixed-width token for the compact statistics line in {@code AEViewer}. */
    public String getStatsLineToken() {
        return statsLineToken;
    }

    public String getDetail() {
        return detail;
    }

    public long getRecentCount() {
        return recentCount;
    }

    public long getTotalCount() {
        return totalCount;
    }

    private static String statsToken(String label) {
        if (label.length() >= STATS_LINE_WIDTH) {
            return label.substring(0, STATS_LINE_WIDTH);
        }
        StringBuilder sb = new StringBuilder(STATS_LINE_WIDTH);
        sb.append(label);
        while (sb.length() < STATS_LINE_WIDTH) {
            sb.append(' ');
        }
        return sb.toString();
    }
}
