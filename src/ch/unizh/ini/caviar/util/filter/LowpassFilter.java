package ch.unizh.ini.caviar.util.filter;

/**
 A first-order lowpass IIR filter
 */
public  class LowpassFilter extends Filter{
    float lpVal, lastVal=0;
    
    /** @param val the new input value
     @param time the time in us - note units here, microseconds!
     */
    public float filter(float val, int time){
        int dt=time-lastTime;
        if(dt<0) dt=0;
        lastTime=time;
        float fac=(float)dt/tauMs/TICK_PER_MS;
        if(fac>1) fac=1;
        lpVal=lpVal+(val-lpVal)*fac;
        lastVal=val;
        return lpVal;
    }
    public String toString(){ return "LP: "+lastVal+"->"+lpVal; }
    
    /** Sets the internral value; used to initialize filter
     @param value the value 
     */
    public void setInternalValue(float value) {
        lpVal=value;
    }

    /** @return output of filter */
    public float getValue() {
        return lpVal;
    }
}

