/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.filter;

import java.beans.PropertyChangeEvent;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.AEViewer;

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

//    protected LowpassFilter filter = new LowpassFilter();
    private int lastComputeTimestamp = 0;
    private boolean initialized = false;
    private float maxRate = getFloat("maxRate", 10e6f);
    private float filteredRate = 0, instantaneousRate = 0;
    private float eventRateTauMs = getFloat("eventRateTauMs", 100);
    private float biasChangePauseS = getFloat("biasChangePauseS", .5f);
    /* Event rate estimates are sent to observers this many times per tau */
    protected int UPDATE_RATE_TAU_DIVIDER = 1;
    private int numEventsSinceLastUpdate = 0;
    private int numEventsInLastPacket = 0;
    private boolean biasChanged = false;
    private long biasChangedTimeMs = 0;

    public EventRateEstimator(AEChip chip) {
        super(chip);
//        filter.setTauMs(eventRateTauMs);
        setPropertyTooltip("eventRateTauMs", "lowpass filter time constant in ms for measuring event rate");
        setPropertyTooltip("maxRate", "maximum estimated rate, which is used for zero ISIs between packets");
        setPropertyTooltip("biasChangePauseS", "time in seconds to pause measurement after detected change of any bias (0 to disable)");
    }

    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        if (in == null || in.getSize() == 0) {
            return in; // if there are no events, don't touch values since we don't have a new update time
        }
        if (biasChanged && this.biasChangePauseS > 0) {
            final long timeSinceBiasChangeMs = System.currentTimeMillis() - biasChangedTimeMs;
            if (timeSinceBiasChangeMs < 1000 * this.biasChangePauseS) {
                lastComputeTimestamp = in.getLastTimestamp();
//                System.out.println(String.format("timeSinceBiasChangeMs=%d < %.0f",timeSinceBiasChangeMs,1000*this.biasChangePauseS));
                return in;
            } else {
                biasChanged = false;
            }
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
    protected boolean addEvent(BasicEvent e, EventPacket<? extends BasicEvent> in) {
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
        if (dt >= 1000 * eventRateTauMs / UPDATE_RATE_TAU_DIVIDER) {
//                System.out.println("            rate update dt="+dt/1000+" ms");
            if (e.timestamp < lastComputeTimestamp) {
                log.warning("nonmonotonic update time");
            }
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
//        filter.reset();
        initialized = false;
        filteredRate = Float.NaN;
        instantaneousRate = Float.NaN;
    }

    @Override
    public void initFilter() {
        resetFilter();
        if (chip.getAeViewer() != null) {
            chip.getAeViewer().getSupport().addPropertyChangeListener(AEViewer.EVENT_CHIP, this);
            if (chip.getBiasgen() != null) {
                chip.getBiasgen().getSupport().addPropertyChangeListener(this);
            }
        }
    }

    @Override
    public String toString() {
        return super.toString() + " rate=" + filteredRate;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getSource() instanceof Chip && evt.getPropertyName()==Chip.EVENT_HARDWARE_CHANGE) {
            biasChanged = true;
            biasChangedTimeMs = System.currentTimeMillis();
            log.info(String.format("bias change: pausing biasChangedTimeS=%.2fs",biasChangePauseS));
        }
    }

    public float getEventRateTauMs() {
        return eventRateTauMs;
    }

    /**
     * Time constant of event rate lowpass filter in ms
     */
    public void setEventRateTauMs(float eventRateTauMs) {
        float old = this.eventRateTauMs;
        if (eventRateTauMs < 0) {
            eventRateTauMs = 0;
        }
        this.eventRateTauMs = eventRateTauMs;
        putFloat("eventRateTauMs", eventRateTauMs);
        getSupport().firePropertyChange("eventRateTauMs", old, this.eventRateTauMs);
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

    /**
     * @return the biasChangePauseS
     */
    public float getBiasChangePauseS() {
        return biasChangePauseS;
    }

    /**
     * @param biasChangePauseS the biasChangePauseS to set
     */
    synchronized public void setBiasChangePauseS(float biasChangePauseS) {
        this.biasChangePauseS = biasChangePauseS;
        putFloat("biasChangePauseS", biasChangePauseS);
//        log.info(String.format("Set biasChangePauseS=%.3fs",this.biasChangePauseS));
    }
}
