package net.sf.jaer.eventio.aedat4;

/**
 * Makes 32-bit (signed or unsigned) microsecond timestamps monotonic, and
 * repairs Unix-µs values that jumped backward by ~2<sup>32</sup> µs (the
 * pre-fix AEDAT-4 writer sign-extended {@code int} camera timestamps).
 * <p>
 * Hardware timestamps wrap every {@value #UINT32_US} µs (~71.6 min). Java
 * {@code int} also wraps at {@code Integer.MAX_VALUE} (~35.8 min) when
 * sign-extended to {@code long}. Detect a backward jump larger than
 * 2<sup>31</sup> µs and add 2<sup>32</sup>.
 */
public final class TimestampUnwrapper {

    /** 2<sup>32</sup> µs = 4294.967296 s. */
    public static final long UINT32_US = 1L << 32;
    static final long WRAP_DETECT_US = 1L << 31;

    private long wrapOffset;
    private long lastRaw;
    private boolean have;

    /** Treat {@code ts} as unsigned 32-bit µs and return a monotonic long. */
    public long unwrapUnsigned32(int ts) {
        return unwrapRaw(ts & 0xffffffffL);
    }

    /**
     * Unwrap a Unix-µs (or already-relative) timeline that may jump backward
     * by ~2<sup>32</sup> µs.
     */
    public long unwrapRaw(long rawUs) {
        if (have && rawUs < lastRaw && (lastRaw - rawUs) > WRAP_DETECT_US) {
            wrapOffset += UINT32_US;
        }
        have = true;
        lastRaw = rawUs;
        return rawUs + wrapOffset;
    }

    public long wrapOffset() {
        return wrapOffset;
    }

    public void reset() {
        wrapOffset = 0;
        lastRaw = 0;
        have = false;
    }

    /**
     * True when {@code end} is a 32-bit wrap after {@code prev} while reading
     * forward (positive {@code int} to non-positive), matching
     * {@code AEFileInputStream} bigWrap.
     */
    public static boolean isSignedWrapForward(int prev, int next) {
        return prev > 0 && next <= 0;
    }
}
