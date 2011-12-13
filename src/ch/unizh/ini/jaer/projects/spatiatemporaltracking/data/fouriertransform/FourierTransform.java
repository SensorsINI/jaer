/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.fouriertransform;

/**
 *
 * @author matthias
 * 
 * The interface FourierTransform provides methods to store and maintain the
 * result of a fourier transformation.
 */
public interface FourierTransform {
    
    /** Defines the storage for the amplitude. */
    public final int AMPLITUDE = 0;
    
    /** Defines the storage for the power. */
    public final int POWER = 1;
    
    /** Defines the size of the storage. */
    public final int SIZE = 2;
    
    /**
     * Sets the sampling frequency used in this fourier transformation.
     * @param frequency 
     */
    public void setFrequency(int frequency);
    
    /**
     * Adds the values of the fourier transformation to this instance.
     * 
     * @param transform The fourier transformation to add.
     */
    public void add(float[] transform);
    
    /**
     * Gets the value of the result in the frequency domain based on the
     * fourier transformation specified by the given index.
     * 
     * @param result Specifies the result.
     * @param index The index of the frequency.
     * @return The value of the result in the frequency domain.
     */
    public float get(int result, int index);
    
    /**
     * Gets the maximal value of the results.
     * 
     * @param result Specifies the result.
     * @return The maximal value.
     */
    public float getMax(int result);
    
    /**
     * Gets the average of each result.
     * 
     * @param result Specifies the result.
     * @return The average.
     */
    public float getAverage(int result);
    
    /**
     * Gets the number of results computed base on the fourier transformation.
     * 
     * @param result Specifies the result.
     * @return The number of results.
     */
    public int getSize(int result);
    
    /**
     * Gets the sampling frequency used to sample the signal.
     * 
     * @return The sampling frequency used to sample the signal.
     */
    public int getFrequency();
    
    /**
     * Indicates whether the fourier transformation is valid or not.
     * 
     * @return True, if the transformation is valid, false otherwise.
     */
    public boolean isValid();
    
    /**
     * Resets the fourier transformation.
     */
    public void reset();
}
