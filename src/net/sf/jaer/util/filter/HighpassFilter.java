package net.sf.jaer.util.filter;

/** A first-order highpass IIR filter.
 
 */
public  class HighpassFilter extends Filter{
    float lpVal=0, lastVal=0, value=0;
    LowpassFilter lpFilter=new LowpassFilter();
    public float filter(float val, int time){
        lpVal=lpFilter.filter(val,time);
        lastVal=val;
        value=val-lpVal;
        return value;
    }
    public String toString(){ return "HP tauMs="+tauMs+" lpVal="+lpVal+": "+lastVal+"->"+value; }
    
    public void setInternalValue(float value) {
        this.value=value;
        lpFilter.setInternalValue(value);
    }

    public float getValue() {
        return value;
    }
    
    @Override 
    public void setTauMs(float tauMs) {
        lpFilter.setTauMs(tauMs);
    }
    @Override
    public float getTauMs() {
        return lpFilter.getTauMs();
    }
}

