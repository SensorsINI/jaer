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
 * The class provides methods to store and maintain the result of a fourier 
 * transformation.
 */
public class SimpleFourierTransform implements FourierTransform {
    /** Stores the fourier transformation. */
    private float[] transform;
    
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
    
    /**
     * Creates a new SimpleFourierTransform.
     */
    public SimpleFourierTransform() {
        this.results = new ArrayList<List<Float>>();
        this.maximums = new ArrayList<Float>();
        this.averages = new ArrayList<Float>();
        for (int i = 0; i < SIZE; i++) {
            this.results.add(new ArrayList<Float>());
            this.maximums.add(0.0f);
            this.averages.add(0.0f);
        }
        
        this.isValid = false;
    }

    @Override
    public void setFrequency(int frequency) {
        if (this.frequency != frequency) this.reset();
        
        this.frequency = frequency;
    }

    @Override
    public void add(float[] transform) {
        if (transform.length > 0) {
            this.reset();
            
            this.transform = transform;
            
            this.results.get(AMPLITUDE).add(this.transform[0]);
            this.results.get(POWER).add((float)Math.pow(this.transform[0], 2.0));
            for (int i = 1; i < this.transform.length / 2; i++) {
                this.results.get(AMPLITUDE).add((float)Math.hypot(this.transform[2 * i], this.transform[2 * i + 1]));
                if (this.maximums.get(AMPLITUDE) < this.results.get(AMPLITUDE).get(i)) this.maximums.set(AMPLITUDE, this.results.get(AMPLITUDE).get(i));
                
                this.results.get(POWER).add((float)(Math.pow(this.transform[2 * i], 2.0) + Math.pow(this.transform[2 * i + 1], 2.0)));
                if (this.maximums.get(POWER) < this.results.get(POWER).get(i)) this.maximums.set(POWER, this.results.get(POWER).get(i));
            }
            
            for (int i = 0; i < SIZE; i++) {
                for (int j = 0; j < this.results.get(i).size(); j++) {
                    if (this.maximums.get(i) < this.results.get(i).get(j)) this.maximums.set(i, this.results.get(i).get(j));
                    this.averages.set(i, this.averages.get(i) + this.results.get(i).get(j));
                }
                this.averages.set(i, this.averages.get(i) / this.results.get(i).size());
            }

            this.isValid = true;
        }
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

    @Override
    public void reset() {
        this.isValid = false;
        
        for (int i = 0; i < SIZE; i++) {
            this.results.get(i).clear();
            this.maximums.set(i, 0.0f);
            this.averages.set(i, 0.0f);
        }
    }
}
