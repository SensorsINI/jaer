package net.sf.jaer.util;

import java.io.File;

/**
 * Headless checks for {@link RecordingDiskSpace} formatting and the free-space
 * threshold. Run after {@code ant compile}:
 * {@code java -cp build/classes:lib/*:jars/* net.sf.jaer.util.RecordingDiskSpaceDemo}
 */
public final class RecordingDiskSpaceDemo {

    public static void main(String[] args) {
        testFormatBytes();
        testEngineeringBytes();
        testEta();
        testEnoughSpaceThreshold();
        testOverlayLine();
        testTempDirUsableSpace();
        System.out.println("ALL PASS");
    }

    private static void testFormatBytes() {
        assertTrue("0 B".equals(RecordingDiskSpace.formatBytes(0)), "0 B");
        assertTrue("512 B".equals(RecordingDiskSpace.formatBytes(512)), "512 B");
        assertTrue("1.0 KB".equals(RecordingDiskSpace.formatBytes(1024)), "1 KiB");
        assertTrue("1.0 MB".equals(RecordingDiskSpace.formatBytes(1L << 20)), "1 MiB");
        assertTrue("1.0 GB".equals(RecordingDiskSpace.formatBytes(RecordingDiskSpace.MIN_FREE_BYTES)), "1 GiB");
        assertTrue("1.5 GB".equals(RecordingDiskSpace.formatBytes((3L << 30) / 2L)), "1.5 GiB");
        System.out.println("PASS testFormatBytes");
    }

    private static void testEngineeringBytes() {
        assertTrue("0".equals(RecordingDiskSpace.formatEngineeringBytes(0)), "zero");
        assertTrue("10.0G".equals(RecordingDiskSpace.formatEngineeringBytes(10_000_000_000L)), "10G");
        assertTrue("1.0k".equals(RecordingDiskSpace.formatEngineeringBytes(1000)), "1k");
        assertTrue("1.1G".equals(RecordingDiskSpace.formatEngineeringBytes(1L << 30)), "1 GiB is 1.1G");
        assertTrue(!RecordingDiskSpace.formatEngineeringBytes(10_000_000_000L).startsWith("+"), "no leading plus");
        System.out.println("PASS testEngineeringBytes");
    }

    private static void testEta() {
        assertTrue("12s".equals(RecordingDiskSpace.formatEta(12)), "seconds");
        assertTrue("5m".equals(RecordingDiskSpace.formatEta(5 * 60)), "minutes");
        assertTrue("2h".equals(RecordingDiskSpace.formatEta(2 * 3600)), "hours");
        assertTrue("1h 5m".equals(RecordingDiskSpace.formatEta(3600 + 5 * 60)), "hours and minutes");
        assertTrue("2d".equals(RecordingDiskSpace.formatEta(2 * 86_400)), "days");
        assertTrue("2d".equals(RecordingDiskSpace.formatEta(2 * 86_400 + 3 * 3600)), "days ignore hours under half a day");
        assertTrue("3d".equals(RecordingDiskSpace.formatEta(2 * 86_400 + 12 * 3600)), "round half day up");
        assertTrue("1w".equals(RecordingDiskSpace.formatEta(7 * 86_400)), "one week");
        assertTrue("1w 2d".equals(RecordingDiskSpace.formatEta(9 * 86_400)), "weeks and days");
        assertTrue("4w".equals(RecordingDiskSpace.formatEta(28 * 86_400)), "four weeks");
        assertTrue("4w 6d".equals(RecordingDiskSpace.formatEta(34 * 86_400)), "still weeks through 4w");
        assertTrue("1mo 1w".equals(RecordingDiskSpace.formatEta(35 * 86_400)), "months after four weeks");
        assertTrue("8mo 2w".equals(RecordingDiskSpace.formatEta(242 * 86_400)), "long recording");
        assertTrue("1y".equals(RecordingDiskSpace.formatEta(48L * 7L * 86_400L)), "one year of 28-day months");
        assertTrue("1y 2mo".equals(RecordingDiskSpace.formatEta(56L * 7L * 86_400L)), "years and months");
        System.out.println("PASS testEta");
    }

    private static void testEnoughSpaceThreshold() {
        assertTrue(RecordingDiskSpace.MIN_FREE_BYTES == (1L << 30), "hardcoded 1 GiB");
        assertTrue(RecordingDiskSpace.CHECK_INTERVAL_MS == 5000L, "5 s check interval");
        File nowhere = new File("Z:/this-volume-does-not-exist-jaer-disk-space-demo");
        assertTrue(RecordingDiskSpace.usableBytes(nowhere) == 0L
                || !RecordingDiskSpace.hasEnoughSpace(nowhere),
                "missing volume is not enough space");
        System.out.println("PASS testEnoughSpaceThreshold");
    }

    private static void testOverlayLine() {
        String line = RecordingDiskSpace.overlayLine(RecordingDiskSpace.MIN_FREE_BYTES * 2, 0, 0);
        assertTrue(line.startsWith("Free "), "overlay starts with Free");
        assertTrue(line.contains("G") && !line.contains("GB"), "engineering units in overlay: " + line);
        String withEta = RecordingDiskSpace.overlayLine(
                RecordingDiskSpace.MIN_FREE_BYTES * 2, 10L << 20, 10_000L);
        assertTrue(withEta.contains("\n~"), "ETA is a second line: " + withEta);
        assertTrue(withEta.contains("until auto-stop"), "ETA when write rate is known: " + withEta);
        assertTrue(!withEta.contains("("), "ETA is not parenthetical on the Free line: " + withEta);
        System.out.println("PASS testOverlayLine");
    }

    private static void testTempDirUsableSpace() {
        File tmp = new File(System.getProperty("java.io.tmpdir"));
        long free = RecordingDiskSpace.usableBytes(tmp);
        assertTrue(free > 0L, "temp dir reports usable space, got " + free);
        File child = new File(tmp, "jaer-disk-space-demo-does-not-exist.aedat4");
        assertTrue(RecordingDiskSpace.usableBytes(child) == free,
                "missing file probes the same volume as its parent");
        System.out.println("PASS testTempDirUsableSpace free=" + RecordingDiskSpace.formatBytes(free));
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }
}
