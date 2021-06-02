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
 * The MultiDimensionalHistogram stores multiple histograms in an array. Each of
 * this histograms stores a certain amount of values and are organized as
 * a circular list.
 * As soon as a histogram exceeds a certain number of values the pointer of the
 * circular list advances and the values are stored in the next histogram.
 */
public class MultiDimensionalHistogram extends AbstractHistogram {
    /** Stores the values of the bins of the various histograms. */
    private float [][] history;
    
    /** Stores the overall some of all histograms in the circular list. */
    private float [] sum;
    
    /** The number of elements in each histogram. */
    private int [] Ns;
    
    /** The total number of elements in all histogram.*/
    private int N;
    
    /** Defines how many values are stored in one histogram. */
    private int limit;
    
    /** Stores the number of histogram used in the circular list. */
    private int nDimensions;
    
    /** Defines the end of the histogram. */
    private int end;
    
    /** Pointer to the currently used histogram in the circular list. */
    private int counter = 0;
    
    /**
     * Creates a new MultiDimensionalHistogram based on the default values.
     */
    public MultiDimensionalHistogram() {
        super();
        this.nDimensions = 5;
        this.limit = 100;
        
        this.init();
        this.reset();
    }
    
    /**
     * Creates a new MultiDimensionalHistogram.
     * 
     * @param start The start of the histogram.
     * @param step The step size of the histogram.
     * @param nBins The number of bins used by the histogram.
     * @param nDimensions The number of histogram used in the circular list.
     * @param limit The number of elements stored in each histogram.
     * @param window The window specifies how the values are distributed over
     * the neighbouring bins.
     */
    public MultiDimensionalHistogram(int start, int step, int nBins, int nDimensions, int limit, int window) {
        super(start, step, nBins, window);
        this.nDimensions = nDimensions;
        this.limit = limit;
        
        this.init();
        this.reset();
    }
    
    /**
     * Increments the bin corresponding to the given value. The value is stored
     * in the currently used histogram. If the number of elements exceeds a 
     * threshold the points is advanced and the overall sum updated.
     * 
     * @param value Defines the corresponding bin of the value.
     */
    @Override
    public void add(int value) {
        if (this.start <= value && this.end > value) {
            int index = Math.round((value - this.start) / (float)step);
            
            for (int i = -this.window; i <= this.window; i++) {
                int key = index + i;
                if (key < 0) key = 0;
                if (key >= this.nBins) key = this.nBins - 1;
                
                this.history[this.counter][key] += this.gaussian[i + this.window];
                this.sum[key] += this.gaussian[i + this.window];
            }
            
            this.Ns[this.counter]++;
            this.N++;
        }
        
        if (this.Ns[this.counter] >= this.limit) {
            this.counter = (this.counter + 1) % this.nDimensions;
            
            for (int i = 0; i < this.nBins; i++) {
                this.sum[i] -= this.history[this.counter][i];
            }
            this.N -= this.Ns[this.counter];
            
            this.Ns[this.counter] = 0;
            Arrays.fill(this.history[this.counter], 0);
        }
    }

    @Override
    public float get(int index) {
        return this.sum[index];
    }

    @Override
    public float getNormalized(int index) {
        return this.sum[index] / this.N;
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
        
        this.sum = new float[nBins];
        this.history = new float[nDimensions][nBins];
        
        this.Ns = new int[nDimensions];
        
        this.end = start + step * nBins;
    }
    
    @Override
    public void reset() {
        Arrays.fill(this.sum, 0);
        for (int i = 0; i < this.history.length; i++) {
            Arrays.fill(this.history[i], 0);
        }
        Arrays.fill(this.Ns, 0);
        this.N = 0;
    }

    @Override
    public boolean isExpressable() {
        return (N > this.limit);
    }
}