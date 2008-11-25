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
    
    /** The filter time constant in ms */
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
    
    /** @param tauMs the time constant in ms 
     */
    public void setTauMs(float tauMs) {
        if(tauMs<1) tauMs=1;
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
        return PI2/1000/getTauMs();
    }
    
    /** Sets the internal value for initializing the filter
     @param value the value
     */
    abstract void setInternalValue(float value);

    /** return last time filter was called. Can be used to look for negative time */
    public int getLastTime() {
        return lastTime;
    }
}
