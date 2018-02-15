/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.filter;

import eu.seebetter.ini.chips.davis.imu.IMUSample;
import java.util.Iterator;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 * Enables filtering out of either DVS or APS events from ApsDvsEventPacket
 *
 * @author tobi
 */
@Description("Enables filtering out of either DVS events, APS samples, or IMU samples from ApsDvsEventPacket, and also filtering out of transient events caused by global shutter closing")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class ApsDvsEventFilter extends EventFilter2D {

    private boolean filterDVSEvents = getBoolean("filterDVSEvents", false);
    private boolean filterAPSEvents = getBoolean("filterAPSEvents", false);
    private boolean filterIMUSamples = getBoolean("filterIMUSamples", false);
    private boolean filterFrameTransientEvents = getBoolean("filterFrameTransientEvents", false);
    private int filterFrameTransientEventsTimeUs = getInt("filterFrameTransientEventsTimeUs", 2000);
    private int lastFrameStartTimestamp = Integer.MIN_VALUE;

    public ApsDvsEventFilter(AEChip chip) {
        super(chip);
        setPropertyTooltip("filterFrameTransientEventsTimeUs", "Events within this time in us after end-of-frame are filtered out; used for filtering DVS events possibly tirggered by APS global shutter");
        setPropertyTooltip("filterFrameTransientEvents", "Filter out events caused by global shutter in DAVIS240b");
        setPropertyTooltip("filterAPSEvents", "Filter out APS intensity samples");
        setPropertyTooltip("filterDVSEvents", "Filter out DVS events");
        setPropertyTooltip("filterIMUSamples", "Filter out IMU (intertial measurement unit) samples");
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!(in instanceof ApsDvsEventPacket) || (!filterAPSEvents && !filterDVSEvents && !filterFrameTransientEvents && !filterIMUSamples)) {
            return in;
        }
        ApsDvsEventPacket apsDvsEventPacket = (ApsDvsEventPacket) in;
        Iterator fullIterator = apsDvsEventPacket.fullIterator();
        checkOutputPacketEventType(in);
        OutputEventIterator outItr = out.outputIterator();
        while (fullIterator.hasNext()) {
            ApsDvsEvent event = (ApsDvsEvent) fullIterator.next();
            if (event.isEndOfExposure()) {
                lastFrameStartTimestamp = event.getTimestamp();
            }
            if (  filterFrameTransientEvents && !event.isApsData() && !event.isImuSample() && lastFrameStartTimestamp != Integer.MIN_VALUE && event.getTimestamp() - lastFrameStartTimestamp < filterFrameTransientEventsTimeUs) {
                continue;
            }
            if (filterAPSEvents && event.isApsData() /*&& !((event instanceof IMUSample) && !(((IMUSample) event).imuSampleEvent))*/)  {
                continue;
            }
            if (filterDVSEvents && !event.isApsData()) {
                continue;
            }
            if (filterIMUSamples && event.isImuSample()) {
                continue; // TODO does not filter all IMU samples!!!
            }
            if (filterIMUSamples && event.isSpecial()) {
                continue;
            }
            outItr.nextOutput().copyFrom(event);
        }
        return out;
    }

    @Override
    public void resetFilter() {
        lastFrameStartTimestamp = Integer.MIN_VALUE;
    }

    @Override
    public void initFilter() {

    }

    /**
     * @return the filterDVSEvents
     */
    public boolean isFilterDVSEvents() {
        return filterDVSEvents;
    }

    /**
     * @param filterDVSEvents the filterDVSEvents to set
     */
    public void setFilterDVSEvents(boolean filterDVSEvents) {
        this.filterDVSEvents = filterDVSEvents;
        putBoolean("filterDVSEvents", filterDVSEvents);
    }

    /**
     * @return the filterAPSEvents
     */
    public boolean isFilterAPSEvents() {
        return filterAPSEvents;
    }

    /**
     * @param filterAPSEvents the filterAPSEvents to set
     */
    public void setFilterAPSEvents(boolean filterAPSEvents) {
        this.filterAPSEvents = filterAPSEvents;
        putBoolean("filterAPSEvents", filterAPSEvents);
    }

    /**
     * @return the filterFrameTransientEvents
     */
    public boolean isFilterFrameTransientEvents() {
        return filterFrameTransientEvents;
    }

    /**
     * @param filterFrameTransientEvents the filterFrameTransientEvents to set
     */
    public void setFilterFrameTransientEvents(boolean filterFrameTransientEvents) {
        this.filterFrameTransientEvents = filterFrameTransientEvents;
        putBoolean("filterFrameTransientEvents", filterFrameTransientEvents);
    }

    /**
     * @return the filterFrameTransientEventsTimeUs
     */
    public int getFilterFrameTransientEventsTimeUs() {
        return filterFrameTransientEventsTimeUs;
    }

    /**
     * @param filterFrameTransientEventsTimeUs the
     * filterFrameTransientEventsTimeUs to set
     */
    public void setFilterFrameTransientEventsTimeUs(int filterFrameTransientEventsTimeUs) {
        this.filterFrameTransientEventsTimeUs = filterFrameTransientEventsTimeUs;
        putInt("filterFrameTransientEventsTimeUs", filterFrameTransientEventsTimeUs);
    }

    /**
     * @return the filterIMUSamples
     */
    public boolean isFilterIMUSamples() {
        return filterIMUSamples;
    }

    /**
     * @param filterIMUSamples the filterIMUSamples to set
     */
    public void setFilterIMUSamples(boolean filterIMUSamples) {
        this.filterIMUSamples = filterIMUSamples;
        putBoolean("filterIMUSamples", filterIMUSamples);
    }

}
