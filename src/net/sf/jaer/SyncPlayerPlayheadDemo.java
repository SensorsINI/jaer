package net.sf.jaer;

/**
 * Headless check that a slider time past 32-bit wrap is not treated as EOF.
 * After {@code ant compile}:
 * {@code java -cp "build/classes:lib/*:jars/*" net.sf.jaer.SyncPlayerPlayheadDemo}
 */
public final class SyncPlayerPlayheadDemo {

    public static void main(String[] args) {
        // 90 min. Low 32 bits of the end are ~18.4 min, which used to compare
        // as already past any later scrub (the merged VCR slider rewind).
        long endUs = 90L * 60L * 1_000_000L;
        long truncatedEndUs = (int) endUs & 0xffffffffL;
        long scrubUs = 26L * 60L * 1_000_000L;
        long wrappedScrubUs = (int) (50L * 60L * 1_000_000L) & 0xffffffffL;

        check(!SyncPlayer.playheadPastEnd(scrubUs, endUs), "26 min scrub is inside a 90 min file");
        check(SyncPlayer.playheadPastEnd(scrubUs, truncatedEndUs),
                "truncated end still looks like EOF; callers must pass the unwrapped end");
        check(!SyncPlayer.playheadPastEnd(wrappedScrubUs, endUs), "50 min scrub is inside a 90 min file");
        check(!SyncPlayer.playheadPastEnd(endUs, endUs), "landing on the last timestamp still shows that slice");
        check(SyncPlayer.playheadPastEnd(endUs + 1, endUs), "one us past the end repeats");
        long twoH = 2L * 3600L * 1_000_000L;
        long fourH = 4L * 3600L * 1_000_000L;
        check(Math.abs(SyncPlayer.fractionOnSpan(twoH, 0L, twoH) - 1f) < 1e-5f,
                "2 h is the end of a 2 h file");
        check(Math.abs(SyncPlayer.fractionOnSpan(twoH, 0L, fourH) - 0.5f) < 1e-5f,
                "2 h is halfway on a shared 0–4 h axis");
        System.out.println("ALL PASS");
    }

    private static void check(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }
}
