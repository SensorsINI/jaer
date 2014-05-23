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
 * The class SimpleHistogram represents a histogram providing the just
 * the fundamental operations.
 * 
 */
public class SimpleHistogram extends AbstractHistogram {
    /** Stores the values of the bins. */
    private float[] histogram;
    
    /** Stores the number of values in the histogram. */
    private int N;
    
    /** The end of the histogram. */
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
    * @param start The start of the histogram.
     * @param step The step size of the histogram.
     * @param nBins The number of bins used by the histogram.
     * @param window The window specifies how the values are distributed over
     * the neighboring bins. Set window to zero to simply bin the values ordinarily. To spread over the nearest neighbor bins
     * in each direction, set window to 1, etc.
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
                if (key < 0) key = 0;
                if (key >= this.nBins) key = this.nBins - 1;
                
                this.histogram[key] += this.gaussian[i + this.window];
            }
            this.N++;
        }
    }

    @Override
    public float get(int index) {
        if (index < 0 && index >= this.nBins) return 0;
        return this.histogram[index];
    }

    @Override
    public float getNormalized(int index) {
        if (this.N == 0) return 0;
        return ((float)this.get(index)) / this.N;
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
}
