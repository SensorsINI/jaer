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
     * Overlay text: free space on the first line; estimated time until the
     * auto-stop threshold on a second line when a write rate is known.
     */
    public static String overlayLine(long usableBytes, long recordedBytes, long elapsedMs) {
        StringBuilder sb = new StringBuilder("Free ");
        sb.append(formatBytes(usableBytes));
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
        long h = seconds / 3600L;
        long m = (seconds % 3600L) / 60L;
        if (m == 0L) {
            return h + "h";
        }
        return h + "h " + m + "m";
    }
}
