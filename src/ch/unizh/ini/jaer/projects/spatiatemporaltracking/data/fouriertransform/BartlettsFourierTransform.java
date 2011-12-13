/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.fouriertransform;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author matthias
 * 
 * Uses Bartlett's method to reduce the variance of the periodogram.
 * 
 * Bartlett's method consists of the following steps:
 * - The original N point data segment is split up into K data segments of length M
 * - For each segment, compute the periodogram by computing the discrete Fourier 
 *   transform (DFT version which does not divide by M), then computing the squared 
 *   magnitude of the result and dividing this by M.
 * - Average the result of the periodogram's above for the K data segments.
 */
public class BartlettsFourierTransform  implements FourierTransform {
    
    /** Stores the results computed out of the fourier transformation. */
    private List<List<Float>> results;
    
    /** Stores the maximal of the results. */
    private List<Float> maximums;
    
    /** Stores the average of each result. */
    private List<Float> averages;
    
    /** The sampling frequency used to sample the signal. */
    private int frequency;
    
    /** Indicates whether the fourier transformation is valid or not. */
    private boolean isValid;
    
    /** Stores the number of iterations. */
    private int iterations;
    
    /**
     * Creates a new SimpleFourierTransform.
     */
    public BartlettsFourierTransform() {
        this.results = new ArrayList<List<Float>>();
        this.maximums = new ArrayList<Float>();
        this.averages = new ArrayList<Float>();
        
        this.reset();
    }
    
    @Override
    public void reset() {
        this.isValid = false;
        
        for (int i = 0; i < this.results.size(); i++) {
            this.results.get(i).clear();
            this.maximums.set(i, 0.0f);
            this.averages.set(i, 0.0f);
        }
        
        this.frequency = 0;
        
        this.iterations = 0;
    }
    
    @Override
    public void setFrequency(int frequency) {
        if (this.frequency != frequency) this.reset();
        
        this.frequency = frequency;
    }

    @Override
    public void add(float[] transform) {
        if (transform.length % 2 != 0) return;
        
        if (this.results.size() < SIZE || transform.length / 2 != this.results.get(0).size()) {
            this.iterations = 0;
            
            this.results.clear();
            int size = transform.length / 2;
            for (int i = 0; i < SIZE; i++) {
                this.results.add(new ArrayList<Float>());
                for (int j = 0; j < size; j++){
                    this.results.get(i).add(0.0f);
                }
                this.maximums.add(0.0f);
                this.averages.add(0.0f);
            }
        }
        
        this.results.get(AMPLITUDE).set(0, this.results.get(AMPLITUDE).get(0) * this.iterations + transform[0]);
        this.results.get(POWER).set(0, this.results.get(POWER).get(0) * this.iterations + (float)Math.pow(transform[0], 2.0));
        for (int i = 1; i < transform.length / 2; i++) {
            this.results.get(AMPLITUDE).set(i, this.results.get(AMPLITUDE).get(i) * this.iterations + (float)(Math.hypot(transform[2 * i], transform[2 * i + 1])));
            if (this.maximums.get(AMPLITUDE) < this.results.get(AMPLITUDE).get(i)) this.maximums.set(AMPLITUDE, this.results.get(AMPLITUDE).get(i));

            this.results.get(POWER).set(i, this.results.get(POWER).get(i) * this.iterations + (float)(Math.pow(transform[2 * i], 2.0) + Math.pow(transform[2 * i + 1], 2.0)));
            if (this.maximums.get(POWER) < this.results.get(POWER).get(i)) this.maximums.set(POWER, this.results.get(POWER).get(i));
        }
        this.iterations++;

        for (int i = 0; i < SIZE; i++) {
            this.maximums.set(i, 0.0f);
            this.averages.set(i, 0.0f);
            
            this.results.get(i).set(0, this.results.get(i).get(0) / this.iterations);
            for (int j = 1; j < this.getSize(i); j++) {
                this.results.get(i).set(j, this.results.get(i).get(j) / this.iterations);
                
                if (this.maximums.get(i) < this.results.get(i).get(j)) this.maximums.set(i, this.results.get(i).get(j));
                this.averages.set(i, this.averages.get(i) + this.results.get(i).get(j));
            }
            this.averages.set(i, this.averages.get(i) / this.results.get(i).size());
        }
        this.isValid = true;
    }

    @Override
    public float get(int result, int index) {
        return this.results.get(result).get(index);
    }
    
    @Override
    public float getMax(int result) {
        return this.maximums.get(result);
    }
    
    @Override
    public float getAverage(int result) {
        return this.averages.get(result);
    }
    
    @Override
    public int getSize(int result) {
        return this.results.get(result).size();
    }

    @Override
    public int getFrequency() {
        return this.frequency;
    }

    @Override
    public boolean isValid() {
        return this.isValid;
    }
}
