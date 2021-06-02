/*
 * Filter.java
 *
 * Created on June 3, 2006, 10:48 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright June 3, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package net.sf.jaer.util.filter;

/**
 * Base class for IIR filters.
 * @author tobi
 */
public abstract class Filter{
    
    /** ticks per ms of input time */
    public final int TICK_PER_MS=1000;
    
    /** The filter time constant in ms. Default value is 100ms. */
    protected float tauMs=100;
    
    /** The last timestamp used */
    protected int lastTime=0;
    
    /** This flag is false until the filter sets it true on the first value. */
    protected boolean initialized=false;
    
    /** apply the filter.
     @param val the new input value
     @param time the new input time - in ticks (us)
     @return the new filter output value 
     */
    abstract float filter(float val, int time);
    
    /** @return present value of output of filter */
    abstract float getValue();
    
    public float getTauMs() {
        return tauMs;
    }
    
    /** @param tauMs the time constant in ms. Value is clipped to 0 minimum. If tauMs=0
     * filter computes instantaneous value on each update.
     */
    public void setTauMs(float tauMs) {
        if(tauMs<0) tauMs=0;
        this.tauMs = tauMs;
//        System.out.println("tauMs="+tauMs);
    }
    
    final static float PI2=(float)(Math.PI*2);
    
    /** @param freq the 3dB corner frequency in Hz
     */
    public void set3dBFreqHz(float freq){
        float tauMs=1000/(PI2*freq);
        setTauMs(tauMs);
    }
    
    /** @return 3dB freq in Hz 
     */
    public float get3dBFreqHz(){
        float freq=1000/(PI2*getTauMs());
        return freq;
    }
    
    /** Sets the internal value for initializing the filter
     @param value the value
     */
    abstract void setInternalValue(float value);

    /** return last time filter was called. Can be used to look for negative time */
    public int getLastTime() {
        return lastTime;
    }

    /** Resets the filter so that the next sample sets the internal value.
     * 
     */
    public void reset(){
        initialized=false;
    }

    /**
     * Returns true if the filter has had at least one value applied.
     * 
     * @return the initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Sets the filter to initialized state, so that subsequent filtering operations treat the last time value as having been stored and the
     * internal value as having been set already.  Can also be used to check if the filter has had a value applied to it.
     * Can also be used to have this IIR filter start out, for example, with 0 as the starting value, so that values build up to the DC solution
     * starting from 0 instead of the first value applied. To do this, reset the filter or set initial value, then setInitialized(true).
     * 
     * @param initialized the initialized to set
     */
    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }
}
