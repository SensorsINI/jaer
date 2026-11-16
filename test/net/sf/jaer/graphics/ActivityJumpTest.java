package net.sf.jaer.graphics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Sparkline rises used by Shift+J / Shift+K. Landing is the bin before the rise.
 */
public class ActivityJumpTest {

    private static final long START = 1_000_000L;
    private static final int N = 32;
    private static final long DUR = 32_000_000L;

    @Test
    public void stepLandsOnTheBinBeforeTheRise() {
        float[] rates = flat(0.1f);
        rates[10] = 0.9f;
        Long t = ActivityJump.landingUs(rates, START, DUR, START, 0.20f, true);
        assertEquals(Long.valueOf(START + 9L * DUR / N), t);
    }

    @Test
    public void forwardFromTheLandingSkipsThatRise() {
        float[] rates = flat(0.1f);
        rates[10] = 0.9f;
        rates[20] = 0.9f;
        long first = ActivityJump.landingUs(rates, START, DUR, START, 0.20f, true);
        Long second = ActivityJump.landingUs(rates, START, DUR, first, 0.20f, true);
        assertEquals(Long.valueOf(START + 19L * DUR / N), second);
        // A few microseconds short of the landing is not there yet.
        Long again = ActivityJump.landingUs(rates, START, DUR, first - 50L, 0.20f, true);
        assertEquals(Long.valueOf(first), again);
    }

    @Test
    public void secondJumpBackSkipsTheRiseJustReached() {
        float[] rates = flat(0.1f);
        rates[10] = 0.9f;
        rates[20] = 0.9f;
        long afterSecond = START + 20L * DUR / N;
        Long one = ActivityJump.landingUs(rates, START, DUR, afterSecond, 0.20f, false);
        Long two = ActivityJump.landingUs(rates, START, DUR, afterSecond, 0.20f, false, 2);
        assertEquals(Long.valueOf(START + 19L * DUR / N), one);
        assertEquals(Long.valueOf(START + 9L * DUR / N), two);
    }

    @Test
    public void secondJumpBackStaysOnTheOnlyRise() {
        float[] rates = flat(0.1f);
        rates[10] = 0.9f;
        long after = START + 10L * DUR / N;
        Long two = ActivityJump.landingUs(rates, START, DUR, after, 0.20f, false, 2);
        assertEquals(Long.valueOf(START + 9L * DUR / N), two);
    }

    @Test
    public void previousFromAfterTheRiseReturnsTheSameLanding() {
        float[] rates = flat(0.1f);
        rates[10] = 0.9f;
        long after = START + 10L * DUR / N;
        Long t = ActivityJump.landingUs(rates, START, DUR, after, 0.20f, false);
        assertEquals(Long.valueOf(START + 9L * DUR / N), t);
    }

    @Test
    public void smallWiggleIsIgnored() {
        float[] rates = flat(0.2f);
        rates[8] = 0.3f;
        assertNull(ActivityJump.landingUs(rates, START, DUR, START, 0.20f, true));
    }

    @Test
    public void plateauIsOneJump() {
        float[] rates = flat(0f);
        for (int i = 10; i <= 14; i++) {
            rates[i] = 1f;
        }
        Long first = ActivityJump.landingUs(rates, START, DUR, START, 0.20f, true);
        assertEquals(Long.valueOf(START + 9L * DUR / N), first);
        assertNull(ActivityJump.landingUs(rates, START, DUR, first, 0.20f, true));
    }

    @Test
    public void noTrace() {
        assertNull(ActivityJump.landingUs(null, START, DUR, START, 0.20f, true));
        assertNull(ActivityJump.landingUs(flat(0.5f), START, 0L, START, 0.20f, true));
    }

    private static float[] flat(float v) {
        float[] rates = new float[N];
        for (int i = 0; i < N; i++) {
            rates[i] = v;
        }
        return rates;
    }
}
