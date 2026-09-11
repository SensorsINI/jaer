package net.sf.jaer.util;

import java.util.OptionalLong;

/**
 * Headless checks for {@link HostSleepTimeout} parsing and warning policy. Run
 * after {@code ant compile}:
 * {@code java -cp build/classes:lib/*:jars/* net.sf.jaer.util.HostSleepTimeoutDemo}
 */
public final class HostSleepTimeoutDemo {

    public static void main(String[] args) {
        HostSleepTimeout.resetCacheForTests();
        testFormatDuration();
        testShouldWarn();
        testParseWindowsRegistry();
        testParseWindowsPowercfg();
        testParsePmset();
        testParseGsettings();
        testWarningOncePerJvm();
        testWarningHtml();
        System.out.println("ALL PASS");
    }

    private static void testFormatDuration() {
        assertTrue("15 minutes".equals(HostSleepTimeout.formatDuration(HostSleepTimeout.TYPICAL_MS)), "15 m");
        assertTrue("2 hours".equals(HostSleepTimeout.formatDuration(2L * 3600L * 1000L)), "2 h");
        assertTrue("1 hour 15 minutes".equals(HostSleepTimeout.formatDuration(75L * 60L * 1000L)), "1h15m");
        System.out.println("PASS testFormatDuration");
    }

    private static void testShouldWarn() {
        OptionalLong unknown = OptionalLong.empty();
        OptionalLong never = OptionalLong.of(0L);
        OptionalLong fifteen = OptionalLong.of(HostSleepTimeout.TYPICAL_MS);
        long twoHours = 2L * 3600L * 1000L;
        assertTrue(!HostSleepTimeout.shouldWarn(0L, fifteen), "no limit");
        assertTrue(!HostSleepTimeout.shouldWarn(HostSleepTimeout.TYPICAL_MS, unknown),
                "unknown OS: equal to typical does not warn");
        assertTrue(HostSleepTimeout.shouldWarn(HostSleepTimeout.TYPICAL_MS + 1L, unknown),
                "unknown OS: longer than 15 m warns");
        assertTrue(!HostSleepTimeout.shouldWarn(twoHours, never), "sleep never");
        assertTrue(HostSleepTimeout.shouldWarn(twoHours, fifteen), "sleep shorter than limit");
        assertTrue(!HostSleepTimeout.shouldWarn(HostSleepTimeout.TYPICAL_MS, fifteen),
                "sleep equal to limit does not warn");
        assertTrue(!HostSleepTimeout.shouldWarn(10L * 60L * 1000L, fifteen), "limit shorter than sleep");
        System.out.println("PASS testShouldWarn");
    }

    private static void testParseWindowsRegistry() {
        String schemes = "HKEY_LOCAL_MACHINE\\SYSTEM\\CurrentControlSet\\Control\\Power\\User\\PowerSchemes\r\n"
                + "    ActivePowerScheme    REG_SZ    381b4222-f694-41f0-9685-ff5bb260df2e\r\n";
        String setting = "    ACSettingIndex    REG_DWORD    0x1c20\r\n"
                + "    DCSettingIndex    REG_DWORD    0xa8c\r\n";
        OptionalLong ms = HostSleepTimeout.parseWindowsRegistryStandbyIdleMs(schemes, setting);
        assertTrue(ms.isPresent() && ms.getAsLong() == 2700L * 1000L,
                "min of AC 2h and DC 45m is 45m, got " + ms);
        String never = "    ACSettingIndex    REG_DWORD    0x0\r\n    DCSettingIndex    REG_DWORD    0x0\r\n";
        OptionalLong z = HostSleepTimeout.parseWindowsRegistryStandbyIdleMs(schemes, never);
        assertTrue(z.isPresent() && z.getAsLong() == 0L, "both never");
        System.out.println("PASS testParseWindowsRegistry");
    }

    private static void testParseWindowsPowercfg() {
        String out = "Current AC Power Setting Index: 0x00001c20\r\n"
                + "Current DC Power Setting Index: 0x00000a8c\r\n";
        OptionalLong ms = HostSleepTimeout.parseWindowsPowercfgStandbyIdleMs(out);
        assertTrue(ms.isPresent() && ms.getAsLong() == 2700L * 1000L, "powercfg min DC");
        System.out.println("PASS testParseWindowsPowercfg");
    }

    private static void testParsePmset() {
        String pmset = "System-wide power settings:\nCurrently in use:\n"
                + " standby              1\n disksleep            10\n"
                + " sleep                1\n displaysleep         10\n";
        OptionalLong ms = HostSleepTimeout.parsePmsetSleepMs(pmset);
        assertTrue(ms.isPresent() && ms.getAsLong() == 60_000L, "pmset sleep is minutes, not displaysleep");
        OptionalLong never = HostSleepTimeout.parsePmsetSleepMs(" sleep                0\n displaysleep 10\n");
        assertTrue(never.isPresent() && never.getAsLong() == 0L, "pmset sleep 0 is never");
        System.out.println("PASS testParsePmset");
    }

    private static void testParseGsettings() {
        OptionalLong ms = HostSleepTimeout.parseGsettingsSecondsMs("uint32 1800", "uint32 900");
        assertTrue(ms.isPresent() && ms.getAsLong() == 900_000L, "gnome min of ac/battery");
        OptionalLong never = HostSleepTimeout.parseGsettingsSecondsMs("uint32 0", "0");
        assertTrue(never.isPresent() && never.getAsLong() == 0L, "gnome never");
        System.out.println("PASS testParseGsettings");
    }

    private static void testWarningOncePerJvm() {
        HostSleepTimeout.resetCacheForTests();
        OptionalLong fifteen = OptionalLong.of(HostSleepTimeout.TYPICAL_MS);
        long twoHours = 2L * 3600L * 1000L;
        assertTrue(!HostSleepTimeout.claimWarningThisJvm(0L, fifteen), "no limit does not claim");
        assertTrue(!HostSleepTimeout.wasWarnedThisJvm(), "flag stays false when not claimed");
        assertTrue(HostSleepTimeout.claimWarningThisJvm(twoHours, fifteen), "first warn claims");
        assertTrue(HostSleepTimeout.wasWarnedThisJvm(), "flag set after claim");
        assertTrue(!HostSleepTimeout.claimWarningThisJvm(twoHours, fifteen), "second warn is suppressed");
        System.out.println("PASS testWarningOncePerJvm");
    }

    private static void testWarningHtml() {
        String known = HostSleepTimeout.warningHtml(2L * 3600L * 1000L, OptionalLong.of(HostSleepTimeout.TYPICAL_MS));
        assertTrue(known.contains("2 hours"), known);
        assertTrue(known.contains("15 minutes"), known);
        assertTrue(known.contains("Extend or disable"), known);
        String unknown = HostSleepTimeout.warningHtml(2L * 3600L * 1000L, OptionalLong.empty());
        assertTrue(unknown.contains("typical"), unknown);
        System.out.println("PASS testWarningHtml");
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }
}
