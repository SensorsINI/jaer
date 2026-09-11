package net.sf.jaer.graphics;

/**
 * Headless checks for {@link RecordingTimeLimit} and setup-dialog show policy.
 * Run after {@code ant compile}:
 * {@code java -cp build/classes:lib/*:jars/* net.sf.jaer.graphics.RecordingTimeLimitDemo}
 */
public final class RecordingTimeLimitDemo {

    public static void main(String[] args) {
        testParse();
        testFormat();
        testShouldShow();
        System.out.println("ALL PASS");
    }

    private static void testParse() {
        assertTrue(RecordingTimeLimit.parseMs("0") == 0L, "0");
        assertTrue(RecordingTimeLimit.parseMs("") == 0L, "empty");
        assertTrue(RecordingTimeLimit.parseMs(RecordingTimeLimit.NO_LIMIT) == 0L, "No limit");
        assertTrue(RecordingTimeLimit.parseMs("1000") == 1000L, "ms implied");
        assertTrue(RecordingTimeLimit.parseMs("10m") == 10L * 60_000L, "10m");
        assertTrue(RecordingTimeLimit.parseMs("2h") == 2L * 3600_000L, "2h");
        assertTrue(RecordingTimeLimit.parseMs("1h 15m") == 75L * 60_000L, "1h 15m");
        System.out.println("PASS testParse");
    }

    private static void testFormat() {
        assertTrue(RecordingTimeLimit.NO_LIMIT.equals(RecordingTimeLimit.formatForDialog(0L)), "0 format");
        assertTrue("10m".equals(RecordingTimeLimit.initialValue(10L * 60_000L)), "preset 10m");
        assertTrue("2h".equals(RecordingTimeLimit.formatForDialog(2L * 3600_000L)), "2h format");
        System.out.println("PASS testFormat");
    }

    private static void testShouldShow() {
        assertTrue(RecordingSetupDialog.shouldShow(0, 0L, false), "first unlimited show");
        assertTrue(RecordingSetupDialog.shouldShow(2, 0L, false), "third unlimited show");
        assertTrue(!RecordingSetupDialog.shouldShow(3, 0L, false), "fourth L/button skips");
        assertTrue(RecordingSetupDialog.shouldShow(3, 0L, true), "File menu always shows");
        assertTrue(RecordingSetupDialog.shouldShow(3, 2L * 3600_000L, false), "timed recording always shows");
        assertTrue(!RecordingSetupDialog.shouldShow(10, 0L, false), "many short recordings skip");
        System.out.println("PASS testShouldShow");
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }
}
