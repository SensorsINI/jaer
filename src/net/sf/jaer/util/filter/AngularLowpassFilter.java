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

package net.sf.jaer.util.filter;

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
        float d=angularDistance(getValue(),val);
        int dt=time-lastTime;
        if(dt<0) dt=0;
        lastTime=time;
        float fac=(float)dt/tauMs/TICK_PER_MS;
        if(fac>1) fac=1;
        lpVal=lpVal+d*fac;
        if (lpVal > period)
            lpVal -= period;
        if (lpVal < 0)
            lpVal += period;
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
    
    /** returns v2-v1 including cut. 
     This scalar value points from v1 to v2 including crossing the cut. 
     
     If v2>v1 then it will be positive unless it is a shorter distance to cross
     the cut; in that case it will be negative and have value v2-v1-period.
     For example, period=180, v1=10, v2=170, v2-v1=160 which is greater than period/2.
     Thus distance is v2-v1-period=160-180=-20.
     
     If v1>v2, it will be negative unless it is a shorter distance to cross
     the cut; in that case it will be positive and have value period-d again.
     For example, period=180, v1=170, v2=10, v2-v1=-160 which is less than -period/2=-90, thus
     signed distance across the cut is period+(v2-v1)=+20.
     
     @param v1 the start of the scalar vector
     @param v2 the end of the scalar vector.
     @return v2-v1 the shortest way. 
     */
    private float angularDistance(float v1, float v2){
        float d=v2-v1;
       if(d>-periodBy2 && d<=periodBy2) return d;
        // distance across cut is smaller than distance
        else if(d>0) return d-period;
        else return d+period;
    }
}
