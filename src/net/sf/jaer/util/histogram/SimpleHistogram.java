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
         * Lowest bin with any samples in the current histogram. Together with
         * {@link #maxNonZeroBin} this is the measured DN range used for
         * under/over exposure fractions, not the full ADC scale.
         */
        public int minNonZeroBin = 0;
        /**
         * The maximum bin with any value in it in the current histogram.
         */
        public int maxNonZeroBin = 0;
        /**
         * Fraction of values in the low portion of the measured DN range
         * (below {@link #lowBoundary} of [minNonZeroBin, maxNonZeroBin])
         */
        public float fracLow = 0;
        /**
         * Fraction of values in the high portion of the measured DN range
         * (above {@link #highBoundary} of [minNonZeroBin, maxNonZeroBin])
         */
        public float fracHigh = 0;

        /**
         * Upper edge of the low band, as a fraction of the measured
         * [minNonZeroBin, maxNonZeroBin] range
         */
        private float lowBoundary = 0.1f;
        /**
         * Lower edge of the high band, as a fraction of the measured
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
            minNonZeroBin = -1;
            maxNonZeroBin = 0;
            for (int i = 0; i < nBins; i++) {
                float v = histogram[i];
                binSum += v;
                weightedSum += i * v;
                if (v > maxCount) {
                    maxBin = i;
                    maxCount = v;
                }
                if (v > 0) {
                    if (minNonZeroBin < 0) {
                        minNonZeroBin = i;
                    }
                    maxNonZeroBin = i;
                }
            }
            if (minNonZeroBin < 0) {
                minNonZeroBin = 0;
            }

            meanBin = 0;
            if (binSum <= 0) {
                meanBin = nBins / 2;
                maxBin = (int) meanBin;
            } else {
                meanBin = Math.round(weightedSum / binSum);
            }

            fracLow = 0;
            fracHigh = 0;
            final int measuredRange = maxNonZeroBin - minNonZeroBin;
            if (binSum > 0 && measuredRange > 0) {
                // low/high bands are fractions of the occupied DN range, not of the ADC full scale
                int binLow = minNonZeroBin + Math.round(getLowBoundary() * measuredRange);
                int binHigh = minNonZeroBin + Math.round(getHighBoundary() * measuredRange);
                if (binLow < minNonZeroBin) {
                    binLow = minNonZeroBin;
                }
                if (binHigh > maxNonZeroBin) {
                    binHigh = maxNonZeroBin;
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
            maxCount = 0;
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
