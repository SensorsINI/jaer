/*
 * AngularLowpassFilter.java
 *
 * Created on July 15, 2007, 2:14 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright July 15, 2007 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.caviar.util.filter;

/**
 * A lowpass filter that correctly handles a circular variable like an angle. Values crossing the cut do not have undue influence.
 The filter has a period (like 2Pi) and the cut is assumed to be at 0/period.
 For example, a present value of 350 deg and an input of 10 deg will push the filter by an angle of 20 degrees towards zero, instead of
 by -340 deg.
 
 * @author tobi
 */
public class AngularLowpassFilter extends LowpassFilter {
    private float period, periodBy2;
    
    /** Creates a new instance of AngularLowpassFilter */
    public AngularLowpassFilter(float period) {
        super();
        setPeriod(period);
    }
    
    /** Overrides super to handle cut. If the absolute distance between the sample "value" and the internal value
     is shorter crossing the cut, then the
     */
    @Override
    public float filter(float val, int time) {
        float d=dValue(val,getValue());
        int dt=time-lastTime;
        if(dt<0) dt=0;
        lastTime=time;
        float fac=(float)dt/tauMs/TICK_PER_MS;
        if(fac>1) fac=1;
        lpVal=lpVal+d*fac;
        lastVal=val;
        return lpVal;
    }
    
    public float getPeriod() {
        return period;
    }
    
    /** Sets the circular period
     @param period the period, e.g. 2*Math.PI or 360
     */
    public void setPeriod(float period) {
        this.period = period;
        periodBy2=period/2;
    }
    
    /** returns v2-v1 including cut. This points from 1 to 2 including crossing the cut.
     @param v1 the start of the scalar vector
     @param v2 the end of the scalar vector.
     @return v2-v1 the shortest way. Points from 1 to 2, e.g. if v2>v1 then it will be positive if it doesn't cross cut.
     */
    private float dValue(float v1, float v2){
        float d=v2-v1;
        if(d<periodBy2 || d>-periodBy2) return d;
        // distance across cut is smaller than distance
        return period-d;
    }
}
