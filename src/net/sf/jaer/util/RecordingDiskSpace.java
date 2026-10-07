package net.sf.jaer.util;

import java.io.File;
import java.util.Locale;

/**
 * Free-space checks for event-camera recording. The minimum is hardcoded so a
 * recording cannot start (or continue) when the destination volume is nearly
 * full.
 */
public final class RecordingDiskSpace {

    /** Refuse or stop recording when usable space is below this (1 GiB). */
    public static final long MIN_FREE_BYTES = 1L << 30;

    /** How often to refresh {@link File#getUsableSpace()} while recording. */
    public static final long CHECK_INTERVAL_MS = 5000L;

    private RecordingDiskSpace() {
    }

    /**
     * Directory whose volume should be probed: the folder itself, or the parent
     * of a file path that does not yet exist.
     */
    public static File directoryToProbe(File fileOrDir) {
        if (fileOrDir == null) {
            return null;
        }
        if (fileOrDir.isDirectory()) {
            return fileOrDir;
        }
        File parent = fileOrDir.getParentFile();
        return parent != null ? parent : fileOrDir;
    }

    /**
     * Usable bytes on the volume of {@code fileOrDir}, or {@code 0} if unknown.
     */
    public static long usableBytes(File fileOrDir) {
        File dir = directoryToProbe(fileOrDir);
        if (dir == null) {
            return 0L;
        }
        try {
            return Math.max(0L, dir.getUsableSpace());
        } catch (SecurityException e) {
            return 0L;
        }
    }

    public static boolean hasEnoughSpace(File fileOrDir) {
        return usableBytes(fileOrDir) >= MIN_FREE_BYTES;
    }

    private static final EngineeringFormat ENG_BYTES = new EngineeringFormat();

    /**
     * Human-readable size using 1024-based units ({@code 1.5 GB}).
     */
    public static String formatBytes(long bytes) {
        if (bytes < 0L) {
            return "unknown";
        }
        if (bytes < 1024L) {
            return bytes + " B";
        }
        final String[] units = {"KB", "MB", "GB", "TB", "PB"};
        double value = bytes;
        int unit = -1;
        while (value >= 1024.0 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }
        if (unit < 0) {
            return bytes + " B";
        }
        return String.format(Locale.US, "%.1f %s", value, units[unit]);
    }

    public static String minFreeSpaceLabel() {
        return formatBytes(MIN_FREE_BYTES);
    }

    /**
     * Overlay size via {@link EngineeringFormat} at one fractional digit,
     * without a leading sign, e.g. {@code 10.0G} or {@code 512.0}.
     */
    public static String formatEngineeringBytes(long bytes) {
        if (bytes < 0L) {
            return "unknown";
        }
        String s;
        synchronized (ENG_BYTES) {
            s = ENG_BYTES.format((double) bytes).trim();
        }
        if (!s.isEmpty() && s.charAt(0) == '+') {
            s = s.substring(1);
        }
        return s;
    }

    /**
     * Overlay text: free space on the first line (engineering units); estimated
     * time until the auto-stop threshold on a second line when a write rate is known.
     */
    public static String overlayLine(long usableBytes, long recordedBytes, long elapsedMs) {
        StringBuilder sb = new StringBuilder("Free ");
        sb.append(formatEngineeringBytes(usableBytes));
        if (elapsedMs >= 3000L && recordedBytes > 1024L && usableBytes > MIN_FREE_BYTES) {
            double bytesPerSec = recordedBytes / (elapsedMs / 1000.0);
            if (bytesPerSec >= 1.0) {
                long remain = usableBytes - MIN_FREE_BYTES;
                long etaSec = (long) (remain / bytesPerSec);
                sb.append('\n').append('~').append(formatEta(etaSec)).append(" until auto-stop");
            }
        }
        return sb.toString();
    }

    /**
     * Coarse time until auto-stop. Under a day: seconds, minutes, or hours.
     * One day up to a week: nearest day. Through four weeks: weeks and days.
     * Longer: months of four weeks plus leftover weeks, then years and months.
     */
    static String formatEta(long seconds) {
        if (seconds < 0L) {
            seconds = 0L;
        }
        if (seconds < 60L) {
            return seconds + "s";
        }
        if (seconds < 3600L) {
            return (seconds / 60L) + "m";
        }
        final long day = 86_400L;
        if (seconds < day) {
            long h = seconds / 3600L;
            long m = (seconds % 3600L) / 60L;
            if (m == 0L) {
                return h + "h";
            }
            return h + "h " + m + "m";
        }
        long days = (seconds + day / 2L) / day;
        if (days < 1L) {
            days = 1L;
        }
        if (days < 7L) {
            return days + "d";
        }
        long weeks = days / 7L;
        long remDays = days % 7L;
        if (weeks <= 4L) {
            if (remDays == 0L) {
                return weeks + "w";
            }
            return weeks + "w " + remDays + "d";
        }
        long months = weeks / 4L;
        long remWeeks = weeks % 4L;
        if (months >= 12L) {
            long years = months / 12L;
            long remMonths = months % 12L;
            if (remMonths == 0L) {
                return years + "y";
            }
            return years + "y " + remMonths + "mo";
        }
        if (remWeeks == 0L) {
            return months + "mo";
        }
        return months + "mo " + remWeeks + "w";
    }
}
