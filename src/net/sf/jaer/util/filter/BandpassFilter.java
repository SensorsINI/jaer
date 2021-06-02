package net.sf.jaer.util.filter;
/** A first-order bandpass IIR filter based on a series of first lowpass and then highpass.
 * 
 */
public class BandpassFilter extends Filter {

     LowpassFilter lpFilter=new LowpassFilter();
    HighpassFilter hpFilter=new HighpassFilter();

    /** Creates a new instance of BandpassFilter with defaults time constant values.
     */
    public BandpassFilter(){

    }

    /** Create a new instance of BandpassFilter with specified low and high pass filter time constants.
     *
     * @param tauLowMs the averaging time in ms.
     * @param tauHighMs the forgetting time in ms.
     */
    public BandpassFilter(float tauLowMs, float tauHighMs){
        this();
        setTauMsLow(tauLowMs);
        setTauMsHigh(tauHighMs);
    }


    /** Filters the incoming signal according to 
     * /br
     * <code>
     * float ret= hpFilter.filter(lpFilter.filter(val,time),time);
     * </code>
     * 
     * @param val the incoming sample
     * @param time the time of the sample in us
     * @return the filter output value
     */
    public float filter(float val, int time) {
        float ret=hpFilter.filter(lpFilter.filter(val, time), time);
//            System.out.println("BP in="+val+" out="+ret);
        return ret;
    }

    public String toString() {
        return "BP: "+lpFilter+" "+hpFilter;
    }

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

    /** Set the highpass corner frequency 
     * @param hz the frequency in Hz
     */
    public void set3dBCornerFrequencyHz(float hz) {
        hpFilter.set3dBFreqHz(hz);
    }

    /** Set the lowpass (rolloff) corner frequency
     * @param hz the frequency in Hz
     */
    public void set3dBPoleFrequencyHz(float hz) {
        lpFilter.set3dBFreqHz(hz);
    }
    
    public float get3dBCornerFrequencyHz(){
        return hpFilter.get3dBFreqHz();
    }
    
    public float get3dBPoleFrequencyHz(){
        return lpFilter.get3dBFreqHz();
    }
}

