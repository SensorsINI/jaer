package net.sf.jaer.eventio.aedat4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.Test;

/** Local day/night flags for the playback sparkline. No file I/O. */
public class Aedat4DayNightSparklineTest {

    private static final ZoneId NY = ZoneId.of("America/New_York");

    @Test
    public void twelveHoursStaysSingleColor() {
        long start = us(ZonedDateTime.of(2026, 10, 13, 10, 0, 0, 0, NY));
        assertNull(Aedat4FileInputStream.nightBinsForSpan(
                start, Aedat4FileInputStream.DAY_NIGHT_MIN_DURATION_US, 64, NY));
    }

    @Test
    public void longerThanTwelveHoursSplitsAtEighteen() {
        // 10:00 local, 13 hours, one bin per hour. Centers fall on the half hour.
        long start = us(ZonedDateTime.of(2026, 10, 13, 10, 0, 0, 0, NY));
        long dur = 13L * 3_600_000_000L;
        boolean[] night = Aedat4FileInputStream.nightBinsForSpan(start, dur, 13, NY);
        assertEquals(13, night.length);
        for (int i = 0; i < 8; i++) {
            assertFalse("bin " + i + " is daytime", night[i]);
        }
        for (int i = 8; i < 13; i++) {
            assertTrue("bin " + i + " is night", night[i]);
        }
    }

    @Test
    public void noonIsDayAndMidnightIsNight() {
        long noon = us(ZonedDateTime.of(2026, 10, 13, 12, 0, 0, 0, NY));
        long midnight = us(ZonedDateTime.of(2026, 10, 14, 0, 0, 0, 0, NY));
        assertFalse(Aedat4FileInputStream.localHourIsNight(noon, NY));
        assertTrue(Aedat4FileInputStream.localHourIsNight(midnight, NY));
    }

    private static long us(ZonedDateTime zdt) {
        return zdt.toInstant().toEpochMilli() * 1000L;
    }
}
