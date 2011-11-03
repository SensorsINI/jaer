package eu.seebetter.ini.chips.seebetter1011;


import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.PolarityEvent;

/** This event class is used in the extractor to hold data from the sensor 
 * so that it can be logged to files and played back here. It adds the ADC sample value.
 * This event has the usual timestamp in us.
 */
public class PolarityADCSampleEvent extends PolarityEvent {

    /** The ADC sample value */
    protected int adcSample = 0;

    public PolarityADCSampleEvent() {
    }

    /**
     * @return the adcSample
     */
    public int getAdcSample() {
        return adcSample;
    }

    /**
     * @param adcSample the adcSample to set
     */
    public void setAdcSample(int adcSample) {
        this.adcSample = adcSample;
    }

    @Override
    public void copyFrom(BasicEvent src) {
        PolarityADCSampleEvent e = (PolarityADCSampleEvent) src;
        super.copyFrom(src);
        adcSample = e.getAdcSample();
    }
}