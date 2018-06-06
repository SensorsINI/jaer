/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.filter;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.util.filter.LowpassFilter;

/**
 * Estimates event rate from the input stream.
 *
 * @author tobi
 *
 * This is part of jAER <a
 * href="http://jaerproject.net/">jaerproject.net</a>, licensed under the LGPL (<a
 * href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
@Description("Estimates event rate from the input event packets")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class EventRateEstimator extends EventFilter2D {

    /**
     * The filter generates PropertyChangeEvent when the rate is updated
     * internally (at least every eventRateTauMs). Listeners can add a property
     * change listener to get these events
     */
    public static final String EVENT_RATE_UPDATE = "EVENT_RATE_UPDATE";

    protected LowpassFilter filter = new LowpassFilter();
    private int lastComputeTimestamp = 0;
    private float maxRate = getFloat("maxRate", 10e6f);
    private float filteredRate = 0, instantaneousRate = 0;
    private float eventRateTauMs = getFloat("eventRateTauMs", 100);
    private int eventRateTauUs = (int) (eventRateTauMs * 1000);
    /* Event rate estimates are sent to observers this many times per tau */
    protected int UPDATE_RATE_TAU_DIVIDER = 1;
    private boolean initialized = false;
    private int numEventsSinceLastUpdate = 0;
    private int numEventsInLastPacket = 0;

    public EventRateEstimator(AEChip chip) {
        super(chip);
        filter.setTauMs(eventRateTauMs);
        setPropertyTooltip("eventRateTauMs", "lowpass filter time constant in ms for measuring event rate");
        setPropertyTooltip("maxRate", "maximum estimated rate, which is used for zero ISIs between packets");
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (in == null || in.getSize() == 0) {
            return in; // if there are no events, don't touch values since we don't have a new update time
        }
        numEventsInLastPacket = 0;
        for (BasicEvent e : in) {
            addEvent(e, in);
        }
        return in;
    }

    /**
     * Processes event
     *
     * @param e
     * @param in
     * @return true if event updated rate, false otherwise
     */
    protected boolean addEvent(BasicEvent e, EventPacket<?> in) {
        if (e.isSpecial()) {
            return false;
        }
        numEventsInLastPacket++;
        int dt = e.timestamp - lastComputeTimestamp;
        if (!initialized) {
            numEventsSinceLastUpdate = 0;
            lastComputeTimestamp = e.timestamp;
            initialized = true;
            return false;
        }
        if (dt < 0) {
            // nonmonotonic timestamp; reset initialized to false and continue after memorizing this timestamp
            initialized = false;
            return false; // just ignore this event entirely
        }
        numEventsSinceLastUpdate++;
        if (dt >= eventRateTauUs / UPDATE_RATE_TAU_DIVIDER) {
//                System.out.println("            rate update dt="+dt/1000+" ms");
            lastComputeTimestamp = e.timestamp;
            instantaneousRate = 1e6f * (float) numEventsSinceLastUpdate / (dt * AEConstants.TICK_DEFAULT_US);
            numEventsSinceLastUpdate = 0;
//            float oldRate=filteredRate;
            filteredRate = instantaneousRate; // already uses time windows to count events // filter.filter(instantaneousRate, e.timestamp);
            UpdateMessage msg = new UpdateMessage(this, in, e.timestamp);
            getSupport().firePropertyChange(EVENT_RATE_UPDATE, null, msg);
        }
        return true;
    }

    @Override
    public void resetFilter() {
        filter.reset();
    }

    @Override
    public void initFilter() {
    }

    @Override
    public String toString() {
        return super.toString() + " rate=" + filteredRate;
    }

    public float getEventRateTauMs() {
        return eventRateTauMs;
    }

    /**
     * Time constant of event rate lowpass filter in ms
     */
    public void setEventRateTauMs(float eventRateTauMs) {
        if (eventRateTauMs < 0) {
            eventRateTauMs = 0;
        }
        this.eventRateTauMs = eventRateTauMs;
        eventRateTauUs = (int) (eventRateTauMs * 1000);
        filter.setTauMs(eventRateTauMs);
        putFloat("eventRateTauMs", eventRateTauMs);
    }

    /**
     * Returns last instantaneous rate, which is the rate of events from the
     * last packet that was filtered that had a rate.
     *
     * @return last instantaneous rate.
     */
    public float getInstantaneousEventRate() {
        return instantaneousRate;
    }

    /**
     * Returns measured event rate
     *
     * @return lowpass filtered event rate
     */
    public float getFilteredEventRate() {
        return filteredRate;
    }

    /**
     * @return the maxRate
     */
    public float getMaxRate() {
        return maxRate;
    }

    /**
     * The max rate that will be estimated when events are simultaneous.
     *
     * @param maxRate the maxRate to set
     */
    public void setMaxRate(float maxRate) {
        this.maxRate = maxRate;
        putFloat("maxRate", maxRate);
    }

    /**
     * Returns the number of events in last input packet.
     *
     * @return the numEventsInLastPacket
     */
    public int getNumEventsInLastPacket() {
        return numEventsInLastPacket;
    }
}
