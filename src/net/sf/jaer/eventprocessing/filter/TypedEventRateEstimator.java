/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.filter;

import java.beans.PropertyChangeListener;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.TypedEvent;

/**
 * Estimates event rates of TypedEvent in a packet. Expensive because it splits
 * up data to temporary packets to estimate rate.
 *
 * @author tobi
 */
@Description("Estimates event rates of TypedEvent in a packet. Expensive because it splits\n"
        + " * up data to temporary packets to estimate rate.")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class TypedEventRateEstimator extends EventRateEstimator {

    public static final String EVENT_MEASURE_INDIVIDUAL_TYPES_CHANGED = "measureIndividualTypesEnabled";

    private int numCellTypes = 0;
    private EventPacket<? extends BasicEvent>[] typedEventPackets = null;
    protected EventRateEstimator[] eventRateEstimators = null;
    public boolean measureIndividualTypesEnabled = getBoolean("measureIndividualTypesEnabled", true);

    public TypedEventRateEstimator(AEChip chip) {
        super(chip);
        setPropertyTooltip("measureIndividualTypesEnabled", "measures cells types individually rather than lumping all types into one overall rate measure");
    }

    public int getNumCellTypes() {
        return numCellTypes;
    }

    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        if (!measureIndividualTypesEnabled) {
            this.numCellTypes = 1;
            super.filterPacket(in); // measure overall event rate and send updates to observers that listen for these updates
            return in;
        }
        checkOutputPacketEventType(in);
        if (numCellTypes != in.getNumCellTypes()) {                     // build tmp packets to hold different types of events
            numCellTypes = in.getNumCellTypes();
            typedEventPackets = new EventPacket[numCellTypes];
            eventRateEstimators = new EventRateEstimator[numCellTypes];
            for (int i = 0; i < numCellTypes; i++) {
                typedEventPackets[i] = in.constructNewPacket();
                eventRateEstimators[i] = new EventRateEstimator(chip);
                eventRateEstimators[i].setEventRateTauMs(getEventRateTauMs());
                eventRateEstimators[i].setMaxRate(getMaxRate());
                PropertyChangeListener[] pcls = getSupport().getPropertyChangeListeners(EVENT_RATE_UPDATE);
                for (PropertyChangeListener p : pcls) {
                    eventRateEstimators[i].getSupport().addPropertyChangeListener(p);
                }
            }
        }
        numCellTypes = in.getNumCellTypes(); // do it again in case option measureIndividualTypesEnabled was changed
        OutputEventIterator[] outItrs = new OutputEventIterator[numCellTypes];
        for (int i = 0; i < numCellTypes; i++) {                            // get the iterators to fill these packets
            outItrs[i] = typedEventPackets[i].outputIterator(); // reset tmp packets
        }

        for (BasicEvent i : in) {                                       // fill the packets
            TypedEvent e = (TypedEvent) i;
            outItrs[e.getType()].nextOutput().copyFrom(e); // split up events to packets
        }
        for (int i = 0; i < numCellTypes; i++) {                    // process each packet
            eventRateEstimators[i].filterPacket(typedEventPackets[i]);
        }
        return in;
    }

    @Override
    synchronized public void resetFilter() {
        super.resetFilter(); //To change body of generated methods, choose Tools | Templates.
        if (eventRateEstimators != null) {
            for (EventRateEstimator e : eventRateEstimators) {
                if (e != null) {
                    e.resetFilter();
                }
            }
        }
    }

    @Override
    synchronized public void setEventRateTauMs(float eventRateTauMs) {
        super.setEventRateTauMs(eventRateTauMs); //To change body of generated methods, choose Tools | Templates.
        if (eventRateEstimators != null) {
            for (EventRateEstimator e : eventRateEstimators) {
                if (e != null) {
                    e.setEventRateTauMs(eventRateTauMs);
                }
            }
        }

    }

    public float getInstantaneousEventRate(int i) {
        if (!measureIndividualTypesEnabled) {
            return super.getInstantaneousEventRate();
        }

        if ((i < 0) || (i >= numCellTypes)) {
            return Float.NaN;
        } else {
            return eventRateEstimators[i].getInstantaneousEventRate();
        }
    }

    public float getFilteredEventRate(int i) {
        if (!measureIndividualTypesEnabled) {
            return super.getFilteredEventRate();
        }
        if ((i < 0) || (i >= numCellTypes)) {
            return Float.NaN;
        } else {
            return eventRateEstimators[i].getFilteredEventRate();
        }
    }

    @Override
    public float getInstantaneousEventRate() {
        if (!measureIndividualTypesEnabled) {
            return super.getInstantaneousEventRate();
        }
        if (numCellTypes == 0) {
            return Float.NaN;
        }
        float sum = 0;
        if (eventRateEstimators != null) {
            for (EventRateEstimator e : eventRateEstimators) {
                if (e != null) {
                    sum += e.getInstantaneousEventRate();
                }
            }
        }
        return sum / numCellTypes;
    }

    @Override
    public float getFilteredEventRate() {
        if (!measureIndividualTypesEnabled) {
            return super.getFilteredEventRate();
        }

        if (numCellTypes == 0) {
            return Float.NaN;
        }
        float sum = 0;
        if (eventRateEstimators != null) {
            for (EventRateEstimator e : eventRateEstimators) {
                if (e != null) {
                    sum += e.getFilteredEventRate();
                }
            }
        }
        return sum / numCellTypes;
    }

    @Override
    synchronized public void setMaxRate(float maxRate) {
        super.setMaxRate(maxRate); //To change body of generated methods, choose Tools | Templates.
        if (eventRateEstimators != null) {
            for (EventRateEstimator e : eventRateEstimators) {
                if (e != null) {
                    e.setMaxRate(maxRate);
                }
            }
        }
    }

    /**
     * @return the measureIndividualTypesEnabled
     */
    public boolean isMeasureIndividualTypesEnabled() {
        return measureIndividualTypesEnabled;
    }

    /**
     * @param measureIndividualTypesEnabled the measureIndividualTypesEnabled to
     * set
     */
    public void setMeasureIndividualTypesEnabled(boolean measureIndividualTypesEnabled) {
        boolean old = this.measureIndividualTypesEnabled;
        this.measureIndividualTypesEnabled = measureIndividualTypesEnabled;
        putBoolean("measureIndividualTypesEnabled", measureIndividualTypesEnabled);
        getSupport().firePropertyChange(EVENT_MEASURE_INDIVIDUAL_TYPES_CHANGED, old, this.measureIndividualTypesEnabled);
    }

    /**
     * @return the eventRateEstimators
     */
    public EventRateEstimator[] getEventRateEstimators() {
        return eventRateEstimators;
    }

}
