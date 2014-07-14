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
        return this.histogram[index];
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
        Arrays.fill(this.histogram, 0);
        this.N = 0;
    }

    @Override
    public boolean isExpressable() {
        return true;
    }

    private Statistics s = new Statistics();

    /** Holds statistics of this SimpleHistogram
     * 
     */
    public class Statistics {

        /** The number of bins; same as size */
        public int nBins;
        /** Maximum count in any bin */
        public float maxCount = Float.NEGATIVE_INFINITY;
        /** The number of the bin with maximum count value, or nBins/2 if there are no counts in any bin */
        public int maxBin = 0;
        /** The sum of all bin values */
        public float binSum = 0;
        /** The sum weighted by bin number (not bin value relative to start and step) */
        public float weightedSum = 0;
        /** The rounded mean bin */
        public int meanBin = 0;
        /** The maximum bin with any value in it */
        public int maxNonZeroBin=0;
        // TODO add median stat
        
        public String toString(){
            return String.format("Exposure statistics: nBins=%d maxCount=%d maxBin=%d meanBin=%d maxNonZeroBin=%d",nBins,maxCount,maxBin,meanBin,maxNonZeroBin);
        }
    }

    /** Computes the fields in the Statistics object and returns it.
     * 
     * @return the reference to the built-in Statistics object
     */
    public Statistics computeStatistics() {
        s.nBins = getSize();
        s.maxCount = Float.NEGATIVE_INFINITY;
        s.maxBin = 0;
        s.binSum = 0;
        s.weightedSum = 0;
//        s.maxNonZeroBin=0; // DO NOT reset this statistic  because it marks the maximum signal that system can produce with bias values, etc
        for (int i = 0; i < nBins; i++) {
            float v = get(i);
            s.binSum += v;
            s.weightedSum += i * v;
            if (v > s.maxCount) {
                s.maxBin = i;
                s.maxCount = v;
            }
            if(v>0){
                if(i>s.maxNonZeroBin) s.maxNonZeroBin=i;
            }
        }
        s.meanBin = 0;
        if (s.binSum <= 0) {
            s.meanBin = nBins / 2;
            s.maxBin = (int) s.meanBin;
        } else {
            s.meanBin = Math.round(s.weightedSum / s.binSum);
        }
        return s;
    }
}
