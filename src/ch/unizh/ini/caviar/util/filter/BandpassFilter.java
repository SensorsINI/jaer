package ch.unizh.ini.caviar.util.filter;

/** A first-order bandpass IIR filter based on a series of first lowpass and then highpass.
 * 
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
    
    /** Sets the lowpass time constant
     * 
     * @param tauMsLow time constant in ms
     */
    public void setTauMsLow(float tauMsLow) {
        this.lpFilter.setTauMs(tauMsLow);
    }
    public float getTauMsHigh() {
        return hpFilter.getTauMs();
    }
    
    /** Sets the highpass time constant
     * @param tauMsHigh the time constant of the highpass 
     */
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

