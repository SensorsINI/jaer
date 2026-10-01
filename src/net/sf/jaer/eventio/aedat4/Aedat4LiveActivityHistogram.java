package net.sf.jaer.eventio.aedat4;

import java.util.Arrays;
import java.util.List;

/**
 * Fixed activity bins for a growing AEDAT-4 recording.
 * The left edge is the first packet of this recording, not device-open time.
 * Each bin is the event count in that interval, same measure as the closed-file
 * sparkline (events per bin time). A packet rate of events/microsecond is not
 * used: a few tiny spans then own the percentile scale and the rest of the
 * activity drops to the baseline. Earlier bins are not rebuilt when the file
 * gets longer.
 */
public final class Aedat4LiveActivityHistogram {

    /** Short enough that a burst is not averaged away, long enough that one packet span cannot explode the scale. */
    public static final long BIN_US = 50_000L;
    private static final double BIN_S = BIN_US * 1e-6;
    private static final double LOG_FLOOR_HZ = 1.0;

    private long[] counts = new long[256];
    private int usedBins;
    private int cursor;
    private long baseUnixUs;
    /** First packet time of this recording. Camera timestamps keep running from device open. */
    private long originUnixUs = Long.MIN_VALUE;

    public void reset(long baseUnixUs) {
        Arrays.fill(counts, 0L);
        usedBins = 0;
        cursor = 0;
        this.baseUnixUs = baseUnixUs;
        originUnixUs = Long.MIN_VALUE;
    }

    public long getBaseUnixUs() {
        return baseUnixUs;
    }

    /**
     * Fold catalog entries at and after {@link #cursor}. A shorter catalog (new
     * cassette) resets the bins.
     */
    public void addCatalog(List<Aedat4FileOutputStream.LiveCatalogEntry> catalog, long baseUnixUs) {
        if (catalog == null) {
            return;
        }
        if (baseUnixUs != this.baseUnixUs || catalog.size() < cursor) {
            reset(baseUnixUs);
        }
        int n = catalog.size();
        for (int i = cursor; i < n; i++) {
            Aedat4FileOutputStream.LiveCatalogEntry e = catalog.get(i);
            if (e.numElements <= 0) {
                continue;
            }
            int streamRem = e.streamId % Aedat4CameraTrack.STREAMS_PER_CAMERA;
            if (streamRem < 0) {
                streamRem += Aedat4CameraTrack.STREAMS_PER_CAMERA;
            }
            if (streamRem != 0) {
                continue;
            }
            addSpan(e.timestampStart, e.timestampEnd, e.numElements);
        }
        cursor = n;
    }

    public int getCursor() {
        return cursor;
    }

    /**
     * Log-relative rates in {@code displayBins} columns (max of the bins in
     * each column). Quiet columns are 0. Scale is the 5th–98th percentile of
     * occupied bins, matching closed-file playback.
     */
    public float[] logRelativeRates(int displayBins) {
        int n = Math.max(1, displayBins);
        if (usedBins <= 0) {
            return null;
        }
        int nLog = 0;
        double[] logs = new double[usedBins];
        double maxHz = 0;
        for (int i = 0; i < usedBins; i++) {
            double hz = counts[i] / BIN_S;
            if (hz > maxHz) {
                maxHz = hz;
            }
            if (hz > 0) {
                logs[nLog++] = Math.log10(Math.max(hz, LOG_FLOOR_HZ));
            }
        }
        if (nLog == 0 || maxHz <= 0) {
            return null;
        }
        Arrays.sort(logs, 0, nLog);
        int pLow = Math.min(nLog - 1, Math.max(0, (int) (0.05 * nLog)));
        int pHigh = Math.min(nLog - 1, Math.max(pLow + 1, (int) Math.ceil(0.98 * nLog) - 1));
        double logMin = logs[pLow];
        double logMax = logs[pHigh];
        if (logMax <= logMin) {
            logMin = 0;
            logMax = Math.log10(Math.max(maxHz, LOG_FLOOR_HZ));
        }
        if (logMax <= logMin) {
            return null;
        }
        double span = logMax - logMin;
        float[] columns = new float[n];
        for (int c = 0; c < n; c++) {
            int a = (int) ((long) c * usedBins / n);
            int b = (int) ((long) (c + 1) * usedBins / n);
            if (b <= a) {
                b = Math.min(usedBins, a + 1);
            }
            float peak = 0;
            for (int i = a; i < b; i++) {
                float v;
                double hz = counts[i] / BIN_S;
                if (hz <= 0) {
                    v = 0;
                } else {
                    double log = Math.log10(Math.max(hz, LOG_FLOOR_HZ));
                    v = (float) ((log - logMin) / span);
                    if (v < 0) {
                        v = 0;
                    } else if (v > 1) {
                        v = 1;
                    }
                }
                if (v > peak) {
                    peak = v;
                }
            }
            columns[c] = peak;
        }
        return columns;
    }

    private void addSpan(long timestampStart, long timestampEnd, long numElements) {
        if (originUnixUs == Long.MIN_VALUE) {
            originUnixUs = timestampStart;
        }
        long rel0 = timestampStart - originUnixUs;
        long rel1 = timestampEnd - originUnixUs;
        if (rel0 < 0) {
            rel0 = 0;
        }
        if (rel1 < rel0) {
            rel1 = rel0;
        }
        int b0 = (int) (rel0 / BIN_US);
        int b1 = (int) (rel1 / BIN_US);
        if (b0 < 0) {
            b0 = 0;
        }
        if (b1 < b0) {
            b1 = b0;
        }
        ensure(b1 + 1);
        int span = b1 - b0 + 1;
        long each = numElements / span;
        long rem = numElements % span;
        for (int b = b0; b <= b1; b++) {
            counts[b] += each + (b == b1 ? rem : 0);
        }
    }

    private void ensure(int minBins) {
        if (minBins <= counts.length) {
            if (minBins > usedBins) {
                usedBins = minBins;
            }
            return;
        }
        int n = counts.length;
        while (n < minBins) {
            n *= 2;
        }
        counts = Arrays.copyOf(counts, n);
        usedBins = minBins;
    }
}
