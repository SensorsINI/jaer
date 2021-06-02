/*
 * copright Tobi Delbruck, CapoCaccia 2011
 */
package net.sf.jaer.util.filter;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Arrays;

/**
 * A running median filter for scalar signals. The output value is the middle value of a running list of input samples.
 * A single <code>length</code> parameter determines the window size.
 * 
 * @author tobi
 */
public class MedianLowpassFilter {

    private float median;
    /** Default length of median computation.*/
    public static int DEFAULT_LENGTH = 3;
    /** The sample array length */
    protected int length = DEFAULT_LENGTH;
    /** Fired when sample length property is changed */
    public static final String PROP_SAMPLES = "samples";
    private int pointer = 0;
    private float[] samples = new float[length];

    public MedianLowpassFilter() {
    }

    public MedianLowpassFilter(int length) {
        setLength(length);
    }

    public MedianLowpassFilter(float median) {
        this.median = median;
        setInternalValue(median);
    }

    public MedianLowpassFilter(int length, float initialValue) {
        setLength(length);
        setInternalValue(initialValue);
    }

    /** Adds a new sample and returns running median value
     * 
     * @param val the new value
     * @return the running median. After initialization, the value will be zero for at least length/2 samples.
     */
    synchronized public float filter(float val) {
        samples[pointer] = val;
        pointer++;
        if (pointer >= length) {
            pointer = 0;
        }
        Arrays.sort(samples);
        int midPoint = length / 2;
        median = samples[midPoint];
//        System.out.println("sample="+val+" median="+median);
        return median;
    }

    /**
     * Get the value of length over which the filter computes the median. The filter has a latency of half this number of length.
     * 
     *
     * @return the value of length
     */
    public int getLength() {
        return length;
    }

    /**
     * Set the value of length of median filter in samples. The latency is half the length.
     *
     * @param n new value of length.  Clipped to 1 sample.
     */
    synchronized public void setLength(int n) {
        if (n < 1) {
            n = 1;
        }
        int oldSamples = this.length;
        this.length = n;
        samples = new float[length];
        pointer=0;
        propertyChangeSupport.firePropertyChange(PROP_SAMPLES, oldSamples, samples);
    }
    private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    /**
     * Add PropertyChangeListener.
     *
     * @param listener
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    /**
     * Remove PropertyChangeListener.
     *
     * @param listener
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    /** Returns the current median
     * 
     * @return median value 
     */
    public float getValue() {
        return median;
    }

    /** Sets the internal value by setting all samples to the value
     * 
     * @param value the value to set 
     */
    synchronized public void setInternalValue(float value) {
        allocate();
        Arrays.fill(samples, value);
    }

    private void allocate() {
        if (samples == null || samples.length != length) {
            samples = new float[length];
        }
    }
}
