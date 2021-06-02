package net.sf.jaer.util.filter;

/**
 * A first-order highpass IIR filter.
 *
 */
public class HighpassFilter extends Filter {

    float lpVal = 0, lastVal = 0, value = 0;
    LowpassFilter lpFilter = new LowpassFilter();

    /**
     * Applies a new input value at the time given and returns the new output
     * value.
     *
     * @param val the new sample
     * @param time the time in us
     * @return the filter output value
     */
    @Override
    public float filter(float val, int time) {
        lpVal = lpFilter.filter(val, time);
        lastVal = val;
        value = val - lpVal;
        initialized=false;
        return value;
    }

    @Override
    public String toString() {
        return "HP tauMs=" + tauMs + (initialized ? (" lpVal=" + lpVal + ": " + lastVal + "->" + value) : (" (unitialized)"));
    }

    @Override
    public void setInternalValue(float value) {
        this.value = value;
        lpFilter.setInternalValue(value);
    }

    /**
     * Returns internal lowpass filter value, i.e. offset or average DC of
     * input.
     *
     * @return the value of the lowpass filter.
     */
    public float getInternalValue() {
        return lpFilter.getValue();
    }

    @Override
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

    /**
     * Overridden to reset the enclosed lowpass filter. Apply this method to
     * start from 0 output.
     *
     */
    @Override
    public void reset() {
        super.reset();
        lpFilter.reset();
    }

}
