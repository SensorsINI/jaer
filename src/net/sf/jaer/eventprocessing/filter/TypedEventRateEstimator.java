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
import net.sf.jaer.event.TypedEvent;

/**
 * Estimates event rates of TypedEvent in a packet, optionally per cell type.
 * Individual-type measurement counts events per type in a single pass without
 * allocating temporary packets.
 *
 * @author tobi
 */
@Description("Estimates event rates of TypedEvent in a packet, optionally per cell type")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class TypedEventRateEstimator extends EventRateEstimator {

    public static final String EVENT_MEASURE_INDIVIDUAL_TYPES_CHANGED = "measureIndividualTypesEnabled";

    private int numCellTypes = 0;
    protected EventRateEstimator[] eventRateEstimators = null;
    public boolean measureIndividualTypesEnabled = getBoolean("measureIndividualTypesEnabled", true);

    public TypedEventRateEstimator(AEChip chip) {
        super(chip);
        setPropertyTooltip("measureIndividualTypesEnabled", "measures cells types individually rather than lumping all types into one overall rate measure");
    }

    public int getNumCellTypes() {
        // Prefer live estimators length so annotate cannot use a stale count before the first packet
        if (measureIndividualTypesEnabled) {
            return eventRateEstimators != null ? eventRateEstimators.length : 0;
        }
        return numCellTypes;
    }

    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        if (!measureIndividualTypesEnabled) {
            this.numCellTypes = 1;
            super.filterPacket(in); // measure overall event rate and send updates to observers that listen for these updates
            return in;
        }
        if (in == null || in.getSize() == 0) {
            return in;
        }
        if (numCellTypes != in.getNumCellTypes() || eventRateEstimators == null) {
            numCellTypes = in.getNumCellTypes();
            eventRateEstimators = new EventRateEstimator[numCellTypes];
            for (int i = 0; i < numCellTypes; i++) {
                eventRateEstimators[i] = new EventRateEstimator(chip);
                eventRateEstimators[i].setEventRateTauMs(getEventRateTauMs());
                eventRateEstimators[i].setMaxRate(getMaxRate());
                PropertyChangeListener[] pcls = getSupport().getPropertyChangeListeners(EVENT_RATE_UPDATE);
                for (PropertyChangeListener p : pcls) {
                    eventRateEstimators[i].getSupport().addPropertyChangeListener(p);
                }
            }
        }
        // Prepare each type estimator (reset packet counters / bias-change pause)
        boolean[] prepared = new boolean[numCellTypes];
        boolean anyActive = false;
        for (int i = 0; i < numCellTypes; i++) {
            prepared[i] = eventRateEstimators[i].prepareForPacket(in);
            anyActive |= prepared[i];
        }
        if (!anyActive) {
            return in;
        }
        // Single pass: route each event to its type's estimator (no packet copy)
        for (BasicEvent i : in) {
            TypedEvent e = (TypedEvent) i;
            int type = e.getType();
            if (type >= 0 && type < numCellTypes && prepared[type]) {
                eventRateEstimators[type].addEvent(e, in);
            }
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
        if (eventRateEstimators == null || (i < 0) || (i >= eventRateEstimators.length)) {
            return Float.NaN;
        }
        EventRateEstimator e = eventRateEstimators[i];
        return e != null ? e.getInstantaneousEventRate() : Float.NaN;
    }

    public float getFilteredEventRate(int i) {
        if (!measureIndividualTypesEnabled) {
            return super.getFilteredEventRate();
        }
        if (eventRateEstimators == null || (i < 0) || (i >= eventRateEstimators.length)) {
            return Float.NaN;
        }
        EventRateEstimator e = eventRateEstimators[i];
        return e != null ? e.getFilteredEventRate() : Float.NaN;
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
        if (old != this.measureIndividualTypesEnabled) {
            // Drop stale type count / estimators so annotate cannot NPE before next packet
            numCellTypes = 0;
            eventRateEstimators = null;
        }
        getSupport().firePropertyChange(EVENT_MEASURE_INDIVIDUAL_TYPES_CHANGED, old, this.measureIndividualTypesEnabled);
    }

    /**
     * @return the eventRateEstimators
     */
    public EventRateEstimator[] getEventRateEstimators() {
        return eventRateEstimators;
    }

}
