/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.util.histogram;

import java.util.Arrays;

/**
 *
 * @author matthias
 *
 * The class SimpleHistogram represents a histogram providing the just the
 * fundamental operations.
 *
 */
public class SimpleHistogram extends AbstractHistogram {

    /**
     * Stores the values of the bins.
     */
    private float[] histogram;

    /**
     * Stores the number of values in the histogram.
     */
    private int N;

    /**
     * The end of the histogram.
     */
    private int end;

    /**
     * Creates a new SimpleHistogram based on the default values.
     */
    public SimpleHistogram() {
        super();

        this.init();
        this.reset();
    }

    /**
     * Creates a new SimpleHistogram.
     *
     * @param start The start of the histogram.
     * @param step The step size of the histogram.
     * @param nBins The number of bins used by the histogram.
     * @param window The window specifies how the values are distributed over
     * the neighboring bins. Set window to zero to simply bin the values
     * ordinarily. To spread over the nearest neighbor bins in each direction,
     * set window to 1, etc.
     */
    public SimpleHistogram(int start, int step, int nBins, int window) {
        super(start, step, nBins, window);

        this.init();
        this.reset();
    }

    /**
     * Returns the backing float[] of histogram counts. Values are Float to
     * allow for Gaussian spreading of added values.
     *
     * @return the histogram
     */
    public float[] getHistogram() {
        return histogram;
    }

    @Override
    public void add(int value) {
        if (this.start <= value && this.end >= value) {
            int index = (value - this.start) / step;

            for (int i = -this.window; i <= this.window; i++) {
                int key = index + i;
                if (key < 0) {
                    key = 0;
                }
                if (key >= this.nBins) {
                    key = this.nBins - 1;
                }

                this.histogram[key] += this.gaussian[i + this.window];
            }
            this.N++;
        }
    }

    @Override
    public float get(int index) {
        if (index < 0 && index >= this.nBins) {
            return 0;
        }
        return this.getHistogram()[index];
    }

    @Override
    public float getNormalized(int index) {
        if (this.N == 0) {
            return 0;
        }
        return ((float) this.get(index)) / this.N;
    }

    @Override
    public int getN() {
        return this.N;
    }

    @Override
    public int getSize() {
        return this.nBins;
    }

    @Override
    public void init() {
        super.init();

        this.histogram = new float[nBins];

        this.end = start + step * nBins;
    }

    @Override
    public void reset() {
        Arrays.fill(this.getHistogram(), 0);
        this.N = 0;
    }

    @Override
    public boolean isExpressable() {
        return true;
    }

    private Statistics statistics = new Statistics();

    /**
     * Compute useful statistics of histogram
     *
     * @return the computed statistics
     */
    public Statistics computeStatistics() {
        statistics.computeStatistics();
        return statistics;
    }

    /**
     * Returns previously computed Statistics
     *
     * @return existing Statistics object, which needs to be explictly computed
     * with computeStatistics
     */
    public Statistics getStatistics() {
        return statistics;
    }

    /**
     * Holds statistics of this SimpleHistogram
     *
     */
    public class Statistics {

        /**
         * The number of bins; same as size
         */
        public int nBins;
        /**
         * Maximum count in any bin
         */
        public float maxCount = Float.NEGATIVE_INFINITY;
        /**
         * The number of the bin with maximum count value, or nBins/2 if there
         * are no counts in any bin
         */
        public int maxBin = 0;
        /**
         * The sum of all bin values
         */
        public float binSum = 0;
        /**
         * The sum weighted by bin number (not bin value relative to start and
         * step)
         */
        public float weightedSum = 0;
        /**
         * The rounded mean bin
         */
        public int meanBin = 0;
        /**
         * Learned analog-floor bin (lowest occupied bin seen since {@link #reset()}).
         * Grows downward. Used with {@link #maxNonZeroBin} as the measured DN
         * full scale, not the ADC length.
         */
        public int minNonZeroBin = 0;
        /**
         * Learned analog-ceiling bin (highest occupied bin seen since
         * {@link #reset()}). Grows upward.
         */
        public int maxNonZeroBin = 0;
        /**
         * True after at least one non-empty histogram has set
         * {@link #minNonZeroBin}/{@link #maxNonZeroBin}.
         */
        private boolean analogRangeInitialized = false;

        /**
         * Install a learned analog DN range from the caller (needed because APS
         * histograms are double-buffered and each has its own Statistics).
         */
        public void setLearnedAnalogRange(int minBin, int maxBin, boolean initialized) {
            minNonZeroBin = minBin;
            maxNonZeroBin = maxBin;
            analogRangeInitialized = initialized;
        }

        public boolean isAnalogRangeInitialized() {
            return analogRangeInitialized;
        }
        /**
         * Fraction of current-frame samples in the low band of the learned
         * analog range
         */
        public float fracLow = 0;
        /**
         * Fraction of current-frame samples in the high band of the learned
         * analog range
         */
        public float fracHigh = 0;

        /**
         * Upper edge of the low band, as a fraction of the learned analog
         * [minNonZeroBin, maxNonZeroBin] range
         */
        private float lowBoundary = 0.1f;
        /**
         * Lower edge of the high band, as a fraction of the learned analog
         * [minNonZeroBin, maxNonZeroBin] range
         */
        private float highBoundary = .9f;

        // TODO add median stats
        public String toString() {
            return String.format("Exposure statistics: nBins=%d maxCount=%.0f maxBin=%d meanBin=%d minNonZeroBin=%d maxNonZeroBin=%d fracLow (<%%%2.0f)=%.2f fracHigh(>%%%2.0f)=%.2f",
                    nBins, maxCount, maxBin, meanBin, minNonZeroBin, maxNonZeroBin, lowBoundary * 100, fracLow, highBoundary * 100, fracHigh);
        }

        /**
         * Computes the fields in the Statistics object and returns it.
         *
         * @return the reference to the built-in Statistics object
         */
        public void computeStatistics() {
            nBins = getSize();
            maxCount = Float.NEGATIVE_INFINITY;
            maxBin = 0;
            binSum = 0;
            weightedSum = 0;
            for (int i = 0; i < nBins; i++) {
                float v = histogram[i];
                binSum += v;
                weightedSum += i * v;
                if (v > maxCount) {
                    maxBin = i;
                    maxCount = v;
                }
            }

            meanBin = 0;
            if (binSum <= 0) {
                meanBin = nBins / 2;
                maxBin = (int) meanBin;
                fracLow = 0;
                fracHigh = 0;
                return;
            }
            meanBin = Math.round(weightedSum / binSum);

            // Ignore isolated hot/dead pixels when finding this frame's occupied DN span.
            final float occupancy = Math.max(1f, 1e-4f * binSum);
            int frameMin = -1, frameMax = -1;
            for (int i = 0; i < nBins; i++) {
                if (histogram[i] >= occupancy) {
                    if (frameMin < 0) {
                        frameMin = i;
                    }
                    frameMax = i;
                }
            }
            if (frameMin < 0) {
                for (int i = 0; i < nBins; i++) {
                    if (histogram[i] > 0) {
                        if (frameMin < 0) {
                            frameMin = i;
                        }
                        frameMax = i;
                    }
                }
            }

            // Expand learned analog floor/ceiling. Do not reset each frame: scoring against
            // the current image's own min/max makes a bright low-contrast background look
            // well-exposed relative to itself (HDR hold / overexposure).
            if (frameMin >= 0) {
                if (!analogRangeInitialized) {
                    minNonZeroBin = frameMin;
                    maxNonZeroBin = frameMax;
                    analogRangeInitialized = true;
                } else {
                    if (frameMin < minNonZeroBin) {
                        minNonZeroBin = frameMin;
                    }
                    if (frameMax > maxNonZeroBin) {
                        maxNonZeroBin = frameMax;
                    }
                }
            }

            fracLow = 0;
            fracHigh = 0;
            final int analogRange = maxNonZeroBin - minNonZeroBin;
            // Until dark and bright DNs have both been seen, the analog span is too
            // narrow to use as full scale. Fall back to ADC midpoint only for that bootstrap.
            if (analogRange < Math.max(4, nBins / 20)) {
                if (meanBin > nBins / 2) {
                    fracHigh = 1;
                } else if (meanBin < nBins / 2) {
                    fracLow = 1;
                }
                return;
            }

            int binLow = minNonZeroBin + Math.round(getLowBoundary() * analogRange);
            int binHigh = minNonZeroBin + Math.round(getHighBoundary() * analogRange);
            if (binLow < minNonZeroBin) {
                binLow = minNonZeroBin;
            }
            if (binHigh > maxNonZeroBin) {
                binHigh = maxNonZeroBin;
            }
            if (binLow >= binHigh) {
                if (meanBin >= (minNonZeroBin + maxNonZeroBin) / 2) {
                    fracHigh = 1;
                } else {
                    fracLow = 1;
                }
                return;
            }
            float sumLow = 0, sumHigh = 0;
            for (int i = minNonZeroBin; i <= binLow; i++) {
                sumLow += histogram[i];
            }
            for (int i = binHigh; i <= maxNonZeroBin; i++) {
                sumHigh += histogram[i];
            }
            fracLow = sumLow / binSum;
            fracHigh = sumHigh / binSum;
        }

        /**
         * Upper edge of the low band, as a fraction of the measured DN range
         *
         * @return the lowBoundary
         */
        public float getLowBoundary() {
            return lowBoundary;
        }

        /**
         * Upper edge of the low band, as a fraction of the measured DN range
         *
         * @param lowBoundary the lowBoundary to set
         */
        public void setLowBoundary(float lowBoundary) {
            this.lowBoundary = lowBoundary;
        }

        /**
         * Lower edge of the high band, as a fraction of the measured DN range
         *
         * @return the highBoundary
         */
        public float getHighBoundary() {
            return highBoundary;
        }

        /**
         * Lower edge of the high band, as a fraction of the measured DN range
         *
         * @param highBoundary the highBoundary to set
         */
        public void setHighBoundary(float highBoundary) {
            this.highBoundary = highBoundary;
        }

        public void reset() {
            minNonZeroBin = 0;
            maxNonZeroBin = 0;
            analogRangeInitialized = false;
            maxCount = 0;
            fracLow = 0;
            fracHigh = 0;
        }
    }

    @Override
    public void print() {
        System.out.println(String.format("Start - Stop: Count"));
        for (int i = 0; i < nBins; i++) {
            int st = getStart() + i * getStep();
            int en = st + getStep();
            System.out.println(String.format("%8d - %8d: %10d", st, en, (int)get(i)));
        }
    }

}
