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
 * The DecayingHistogram is a histogram providing methods to let its values
 * decay over time.
 */
public class DecayingHistogram extends AbstractHistogram {
    /** Stores the values of the bins. */
    private float[] bins;
    
    /** Stores the number of values in the histogram. */
    private float N;
    
    /** The end of the histogram. */
    private int end;
    
    /**
     * Creates a new DecayingHistogram based on the default values.
     */
    public DecayingHistogram() {
        super();
        
        this.init();
        this.reset();
    }
    
    /**
     * Creates a new DecayingHistogram.
     */
    public DecayingHistogram(int start, int step, int nBins, int window) {
        super(start, step, nBins, window);
        this.window = window;
        
        this.init();
        this.reset();
    }
    
    @Override
    public void add(int value) {
        if (this.start <= value && this.end >= value) {
            int index = (value - this.start) / step;
            
            for (int i = -this.window; i <= this.window; i++) {
                int current = index + i;
                if (current < 0) {
                    current = 0;
                }
                else if (current >= this.bins.length) {
                    current = this.bins.length - 1;
                }
                
                this.bins[current] += this.gaussian[i + this.window];
            }
            N++;
        }
    }
    
    @Override
    public float get(int index){
        if (index < 0 && index >= this.nBins) return 0;
        return this.bins[index];
    }
    
    @Override
    public float getNormalized(int index){
        if (this.N == 0) return 0;
        if (index < 0 && index >= this.nBins) return 0;
        
        return this.bins[index] / this.N;
    }
    
    @Override
    public int getN() {
        return (int)this.N;
    }
    
    /**
     * This methods is used to decay the histograms values uniformly. This is
     * done by substracting a value from the histogram which is then
     * substracted from the bins according their weights.
     * 
     * @param value The value that has to be substracted from the histogram.
     */
    public void decay(double value) {
        if (value * 10 > N) return;
        
        value = (N - value) / N;
        N = 0;
        for (int i = 0; i < this.bins.length; i++) {
            this.bins[i] -= value * this.bins[i];
            N += this.bins[i];
        }
    }
    
    @Override
    public int getSize() {
        return this.bins.length;
    }
    
    @Override
    public void init() {
        super.init();
        
        this.bins = new float[nBins];
        this.N = 0;
        
        this.end = start + step * nBins;
    }
    
    @Override
    public void reset() {
        this.N = 0;
        Arrays.fill(this.bins, 0);
    }
    
    @Override
    public boolean isExpressable() {
        return this.N > 200;
    }
}
