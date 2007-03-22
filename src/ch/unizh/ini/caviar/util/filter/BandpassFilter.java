package ch.unizh.ini.caviar.util.filter;

/** A first-order bandpass IIR filter
 */
public class BandpassFilter extends Filter{
    LowpassFilter lpFilter=new LowpassFilter();
    HighpassFilter hpFilter=new HighpassFilter();
    public float filter(float val, int time){
        float ret= hpFilter.filter(lpFilter.filter(val,time),time);
//            System.out.println("BP in="+val+" out="+ret);
        return ret;
    }
    public String toString(){ return "BP: "+lpFilter+" "+hpFilter; }
    
    public float getTauMsLow() {
        return lpFilter.getTauMs();
    }
    
    public void setTauMsLow(float tauMsLow) {
        this.lpFilter.setTauMs(tauMsLow);
    }
    public float getTauMsHigh() {
        return hpFilter.getTauMs();
    }
    
    public void setTauMsHigh(float tauMsHigh) {
        this.hpFilter.setTauMs(tauMsHigh);
    }
    
    public void setInternalValue(float value) {
        lpFilter.setInternalValue(value);
        hpFilter.setInternalValue(value);
    }

    public float getValue() {
        return hpFilter.getValue();
    }
}

