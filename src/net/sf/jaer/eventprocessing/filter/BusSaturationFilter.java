/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.filter;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.util.filter.LowpassFilter;

/**
 * Filters out events from a saturated readout.
 *
 * @author tobi
 */
public class BusSaturationFilter extends EventFilter2D {

    static String getFilterDescription(){
        return "Filters out events from a saturated readout.";
    }

    private float saturationPeriodUs = getPrefs().getFloat("BusSaturationFilter.saturationPeriodUs", .5f);


    {
        setPropertyTooltip("dsaturationPeriodUst", "If event interspike period drops below this for tauMs ms, events are filtered out");
    }
    private float tauMs = getPrefs().getFloat("BusSaturationFilter.tauMs", 1);


    {
        setPropertyTooltip("tauMs", "If event interspike period drops below this for tauMs ms, events are filtered out");
    }
    private LowpassFilter lowpass = new LowpassFilter();
    int lastts = 0;

    public BusSaturationFilter(AEChip chip) {
        super(chip);
        lowpass.setTauMs(tauMs);

    }

    /**
     * Get the value of saturationPeriodUs
     *
     * @return the value of saturationPeriodUs
     */
    public float getSaturationPeriodUs() {
        return saturationPeriodUs;
    }

    /**
     * Set the value of saturationPeriodUs
     *
     * @param saturationPeriodUs new value of saturationPeriodUs
     */
    public void setSaturationPeriodUs(float saturationPeriodUs) {
        support.firePropertyChange("saturationPeriodUs", this.saturationPeriodUs, saturationPeriodUs);
        this.saturationPeriodUs = saturationPeriodUs;
        getPrefs().putFloat("BusSaturationFilter.saturationPeriodUs", saturationPeriodUs);
    }

    @Override
    public Object getFilterState() {
        return null;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!isFilterEnabled()) {
            return in;
        }
        if (in == null) {
            return null;
        }
        checkOutputPacketEventType(in.getEventClass());
        OutputEventIterator outItr = out.outputIterator();
        for (BasicEvent e : in) {
            int dt = e.timestamp - lastts;
            lastts=e.timestamp;
            lowpass.filter(dt, e.timestamp);
            if (lowpass.getValue() > saturationPeriodUs) {
                BasicEvent o = (BasicEvent) outItr.nextOutput();
                o.copyFrom(e);
            }
        }
        return out;
    }

    /**
     * @return the tauMs
     */
    public float getTauMs() {
        return tauMs;
    }

    /**
     * @param tauMs the tauMs to set
     */
    public void setTauMs(float tauMs) {
        support.firePropertyChange("tauMs", this.tauMs, tauMs);
        this.tauMs = tauMs;
        getPrefs().putFloat("BusSaturationFilter.tauMs", tauMs);
    }
}
