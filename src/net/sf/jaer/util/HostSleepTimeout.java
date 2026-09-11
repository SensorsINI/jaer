package net.sf.jaer.util;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Host OS system-sleep timeout (standby), used to warn before a time-limited
 * recording that is longer than when the computer will sleep.
 */
public final class HostSleepTimeout {

    /** Typical laptop sleep timeout when the OS value cannot be read. */
    public static final long TYPICAL_MS = 15L * 60L * 1000L;

    static final long QUERY_TIMEOUT_MS = 2500L;

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final Object QUERY_LOCK = new Object();
    private static final AtomicBoolean warnedThisJvm = new AtomicBoolean(false);
    private static boolean queried;
    private static OptionalLong cached = OptionalLong.empty();

    private static final Pattern WIN_ACTIVE_SCHEME = Pattern.compile(
            "ActivePowerScheme\\s+REG_SZ\\s+([0-9a-fA-F-]{36})", Pattern.CASE_INSENSITIVE);
    private static final Pattern WIN_AC_INDEX = Pattern.compile(
            "ACSettingIndex\\s+REG_DWORD\\s+(0x[0-9a-fA-F]+|\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WIN_DC_INDEX = Pattern.compile(
            "DCSettingIndex\\s+REG_DWORD\\s+(0x[0-9a-fA-F]+|\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WIN_POWERCFG_AC = Pattern.compile(
            "Current AC Power Setting Index:\\s*(0x[0-9a-fA-F]+|\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WIN_POWERCFG_DC = Pattern.compile(
            "Current DC Power Setting Index:\\s*(0x[0-9a-fA-F]+|\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PMSET_SLEEP = Pattern.compile("(?m)^\\s*sleep\\s+(\\d+)");
    private static final Pattern UINT_SECONDS = Pattern.compile("(\\d+)");

    private static final String WIN_SUB_SLEEP = "238c9fa8-0aad-41ed-83f4-97be242c8f20";
    private static final String WIN_STANDBYIDLE = "29f6c1db-86da-48c5-9fdb-f2b67b1f44da";

    private HostSleepTimeout() {
    }

    /**
     * Cached query of milliseconds until system sleep. Empty if unknown;
     * {@code 0} means sleep is disabled (never).
     */
    public static OptionalLong query() {
        synchronized (QUERY_LOCK) {
            if (!queried) {
                cached = queryUncached();
                queried = true;
                if (cached.isPresent()) {
                    long ms = cached.getAsLong();
                    log.info(ms <= 0L
                            ? "host system sleep timeout: never"
                            : "host system sleep timeout: " + formatDuration(ms));
                } else {
                    log.info("host system sleep timeout: unknown (will assume "
                            + formatDuration(TYPICAL_MS) + " for long recordings)");
                }
            }
            return cached;
        }
    }

    /**
     * Warn when a time-limited recording may be cut short by OS sleep.
     * If the timeout is known and shorter than {@code recordingLimitMs}, warn.
     * If unknown, warn when the limit is longer than {@link #TYPICAL_MS}.
     * Sleep disabled ({@code 0}) does not warn.
     */
    public static boolean shouldWarn(long recordingLimitMs) {
        return shouldWarn(recordingLimitMs, query());
    }

    public static boolean shouldWarn(long recordingLimitMs, OptionalLong sleepTimeoutMs) {
        if (recordingLimitMs <= 0L) {
            return false;
        }
        if (sleepTimeoutMs == null || sleepTimeoutMs.isEmpty()) {
            return recordingLimitMs > TYPICAL_MS;
        }
        long sleepMs = sleepTimeoutMs.getAsLong();
        if (sleepMs <= 0L) {
            return false;
        }
        return sleepMs < recordingLimitMs;
    }

    /**
     * True if this JVM has not yet shown the recording/sleep warning and
     * {@link #shouldWarn(long)} is true. First successful claim wins.
     */
    public static boolean claimWarningThisJvm(long recordingLimitMs) {
        return claimWarningThisJvm(recordingLimitMs, query());
    }

    public static boolean claimWarningThisJvm(long recordingLimitMs, OptionalLong sleepTimeoutMs) {
        if (!shouldWarn(recordingLimitMs, sleepTimeoutMs)) {
            return false;
        }
        return warnedThisJvm.compareAndSet(false, true);
    }

    public static String warningHtml(long recordingLimitMs) {
        return warningHtml(recordingLimitMs, query());
    }

    public static String warningHtml(long recordingLimitMs, OptionalLong sleepTimeoutMs) {
        String limit = formatDuration(recordingLimitMs);
        if (sleepTimeoutMs != null && sleepTimeoutMs.isPresent() && sleepTimeoutMs.getAsLong() > 0L) {
            return "<html>This recording is limited to <b>" + limit
                    + "</b>, but this computer is set to sleep after <b>"
                    + formatDuration(sleepTimeoutMs.getAsLong()) + "</b>.<br><br>"
                    + "If the computer sleeps, jAER stops the recording and offers to save the file.<br><br>"
                    + "Extend or disable the OS sleep timeout before a long recording.</html>";
        }
        return "<html>This recording is limited to <b>" + limit
                + "</b>, longer than a typical " + formatDuration(TYPICAL_MS)
                + " sleep timeout.<br><br>"
                + "If the computer sleeps, jAER stops the recording and offers to save the file.<br><br>"
                + "Extend or disable the OS sleep timeout before a long recording.</html>";
    }

    public static String formatDuration(long ms) {
        long totalSec = Math.max(0L, ms) / 1000L;
        if (totalSec < 60L) {
            return totalSec + (totalSec == 1L ? " second" : " seconds");
        }
        long totalMin = totalSec / 60L;
        if (totalMin < 60L) {
            return totalMin + (totalMin == 1L ? " minute" : " minutes");
        }
        long h = totalMin / 60L;
        long m = totalMin % 60L;
        if (m == 0L) {
            return h + (h == 1L ? " hour" : " hours");
        }
        return h + (h == 1L ? " hour " : " hours ") + m + (m == 1L ? " minute" : " minutes");
    }

    static OptionalLong parseWindowsRegistryStandbyIdleMs(String activeSchemeOutput, String settingOutput) {
        if (activeSchemeOutput == null || settingOutput == null) {
            return OptionalLong.empty();
        }
        Matcher scheme = WIN_ACTIVE_SCHEME.matcher(activeSchemeOutput);
        if (!scheme.find()) {
            return OptionalLong.empty();
        }
        return minPositiveSecondsToMs(
                matchSeconds(WIN_AC_INDEX, settingOutput),
                matchSeconds(WIN_DC_INDEX, settingOutput));
    }

    static OptionalLong parseWindowsPowercfgStandbyIdleMs(String powercfgOutput) {
        if (powercfgOutput == null || powercfgOutput.isEmpty()) {
            return OptionalLong.empty();
        }
        return minPositiveSecondsToMs(
                matchSeconds(WIN_POWERCFG_AC, powercfgOutput),
                matchSeconds(WIN_POWERCFG_DC, powercfgOutput));
    }

    static OptionalLong parsePmsetSleepMs(String pmsetOutput) {
        if (pmsetOutput == null || pmsetOutput.isEmpty()) {
            return OptionalLong.empty();
        }
        Matcher m = PMSET_SLEEP.matcher(pmsetOutput);
        if (!m.find()) {
            return OptionalLong.empty();
        }
        long minutes = Long.parseLong(m.group(1));
        return OptionalLong.of(minutes * 60L * 1000L);
    }

    static OptionalLong parseGsettingsSecondsMs(String acOutput, String batteryOutput) {
        OptionalLong ac = parseLeadingUintSecondsMs(acOutput);
        OptionalLong bat = parseLeadingUintSecondsMs(batteryOutput);
        return minPositiveMs(ac, bat);
    }

    static OptionalLong parseLeadingUintSecondsMs(String output) {
        if (output == null || output.isEmpty()) {
            return OptionalLong.empty();
        }
        Matcher m = UINT_SECONDS.matcher(output);
        String last = null;
        while (m.find()) {
            last = m.group(1);
        }
        if (last == null) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Long.parseLong(last) * 1000L);
    }

    static void resetCacheForTests() {
        synchronized (QUERY_LOCK) {
            queried = false;
            cached = OptionalLong.empty();
        }
        warnedThisJvm.set(false);
    }

    static boolean wasWarnedThisJvm() {
        return warnedThisJvm.get();
    }

    private static OptionalLong queryUncached() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                OptionalLong fromReg = queryWindowsRegistry();
                if (fromReg.isPresent()) {
                    return fromReg;
                }
                return parseWindowsPowercfgStandbyIdleMs(runCommand(QUERY_TIMEOUT_MS,
                        "powercfg", "/q", "SCHEME_CURRENT", "SUB_SLEEP", "STANDBYIDLE"));
            }
            if (os.contains("mac")) {
                return parsePmsetSleepMs(runCommand(QUERY_TIMEOUT_MS, "pmset", "-g"));
            }
            OptionalLong gnome = parseGsettingsSecondsMs(
                    runCommand(QUERY_TIMEOUT_MS, "gsettings", "get",
                            "org.gnome.settings-daemon.plugins.power", "sleep-inactive-ac-timeout"),
                    runCommand(QUERY_TIMEOUT_MS, "gsettings", "get",
                            "org.gnome.settings-daemon.plugins.power", "sleep-inactive-battery-timeout"));
            if (gnome.isPresent()) {
                return gnome;
            }
        } catch (Exception e) {
            log.log(Level.FINE, "host sleep timeout query failed: " + e, e);
        }
        return OptionalLong.empty();
    }

    private static OptionalLong queryWindowsRegistry() {
        String schemes = runCommand(QUERY_TIMEOUT_MS, "reg", "query",
                "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Power\\User\\PowerSchemes",
                "/v", "ActivePowerScheme");
        if (schemes == null) {
            return OptionalLong.empty();
        }
        Matcher scheme = WIN_ACTIVE_SCHEME.matcher(schemes);
        if (!scheme.find()) {
            return OptionalLong.empty();
        }
        String key = "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Power\\User\\PowerSchemes\\"
                + scheme.group(1) + "\\" + WIN_SUB_SLEEP + "\\" + WIN_STANDBYIDLE;
        String setting = runCommand(QUERY_TIMEOUT_MS, "reg", "query", key);
        return parseWindowsRegistryStandbyIdleMs(schemes, setting);
    }

    private static OptionalLong matchSeconds(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        if (!m.find()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(parseIntOrHex(m.group(1)));
    }

    static long parseIntOrHex(String raw) {
        String s = raw.trim();
        if (s.startsWith("0x") || s.startsWith("0X")) {
            return Long.parseLong(s.substring(2), 16);
        }
        return Long.parseLong(s);
    }

    private static OptionalLong minPositiveSecondsToMs(OptionalLong acSec, OptionalLong dcSec) {
        OptionalLong ac = acSec.isPresent() ? OptionalLong.of(acSec.getAsLong() * 1000L) : OptionalLong.empty();
        OptionalLong dc = dcSec.isPresent() ? OptionalLong.of(dcSec.getAsLong() * 1000L) : OptionalLong.empty();
        return minPositiveMs(ac, dc);
    }

    /**
     * Among known timeouts, use the shortest that is actually enabled. {@code 0}
     * means never for that power source. If every known source is never, return
     * {@code 0}. If none parsed, empty.
     */
    static OptionalLong minPositiveMs(OptionalLong a, OptionalLong b) {
        boolean any = a.isPresent() || b.isPresent();
        if (!any) {
            return OptionalLong.empty();
        }
        long min = Long.MAX_VALUE;
        if (a.isPresent() && a.getAsLong() > 0L) {
            min = Math.min(min, a.getAsLong());
        }
        if (b.isPresent() && b.getAsLong() > 0L) {
            min = Math.min(min, b.getAsLong());
        }
        if (min == Long.MAX_VALUE) {
            return OptionalLong.of(0L);
        }
        return OptionalLong.of(min);
    }

    static String runCommand(long timeoutMs, String... cmd) {
        if (cmd == null || cmd.length == 0) {
            return null;
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            boolean done = p.waitFor(Math.max(50L, timeoutMs), TimeUnit.MILLISECONDS);
            if (!done) {
                p.destroyForcibly();
                return null;
            }
            try (InputStream in = p.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.log(Level.FINE, "command failed: " + String.join(" ", cmd), e);
            return null;
        }
    }
}
