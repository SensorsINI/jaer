/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.speakerid;

import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.filter.EventRateEstimator;

/**
 * Estimates event rates of each channel in a packet. Expensive because it splits
 * up data to temporary packets to estimate rate.
 *
 * @author philipp
 */
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class ChannelEventRateEstimator extends EventRateEstimator {

    private int numChannels = 0;
    private EventPacket<? extends BasicEvent>[] typedEventPackets = null;
    private EventRateEstimator[] eventRateEstimators = null;
    protected boolean measureIndividualChannelsEnabled=getBoolean("measureIndividualTypesEnabled", true);

    public ChannelEventRateEstimator(AEChip chip) {
        super(chip);
        setPropertyTooltip("measureIndividualTypesEnabled", "measures the channel activity for each cochlea channel seperatly");
    }

    public int getNumChannels(){
        return numChannels;
    }

    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        super.filterPacket(in); // measure overall event rate and send updates to observers that listen for these updates
        if(!isMeasureIndividualChannelsEnabled()) {
			return in;
		}
        checkOutputPacketEventType(in);
        if (numChannels != chip.getSizeX()) {                     // build tmp packets to hold events of different channels
            numChannels = chip.getSizeX();
            typedEventPackets = new EventPacket[numChannels];
            eventRateEstimators=new EventRateEstimator[numChannels];
            for (int i = 0; i < numChannels; i++) {     //construct an eventRateEstimator for each channel
                typedEventPackets[i] = in.constructNewPacket();
                eventRateEstimators[i] = new EventRateEstimator(chip);
                eventRateEstimators[i].setEventRateTauMs(getEventRateTauMs());
                eventRateEstimators[i].setMaxRate(getMaxRate());
            }
        }
        numChannels=chip.getSizeX(); // do it again in case option measureIndividualChannelsEnabled was changed
        OutputEventIterator[] outItrs = new OutputEventIterator[numChannels];
        for (int i = 0; i < numChannels; i++) {                            // get the iterators to fill these packets
            outItrs[i] = typedEventPackets[i].outputIterator(); // reset tmp packets
        }
        for (BasicEvent i : in) {                                       // fill the packets
            outItrs[i.getX()].nextOutput().copyFrom(i); // split up events to packets
        }
        for (int i = 0; i < numChannels; i++) {                    // process each packet
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
        if ((i < 0) || (i >= numChannels)) {
            return Float.NaN;
        } else {
            return eventRateEstimators[i].getInstantaneousEventRate();
        }
    }

    public float getFilteredEventRate(int i) {
        if(!measureIndividualChannelsEnabled) {
			return super.getFilteredEventRate();
		}
        if ((i < 0) || (i >= numChannels)) {
            return Float.NaN;
        } else {
            return eventRateEstimators[i].getFilteredEventRate();
        }
    }

    @Override
    public float getInstantaneousEventRate() {
        if (numChannels == 0) {
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
        return sum / numChannels;
    }

    @Override
    public float getFilteredEventRate() {
        if (numChannels == 0) {
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
        return sum / numChannels;
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
     * @return the measureIndividualChannelsEnabled
     */
    public boolean isMeasureIndividualChannelsEnabled() {
        return measureIndividualChannelsEnabled;
    }

    /**
     * @param measureIndividualChannelsEnabled the measureIndividualChannelsEnabled to set
     */
    public void setMeasureIndividualChannelsEnabled(boolean measureIndividualChannelsEnabled) {
        this.measureIndividualChannelsEnabled = measureIndividualChannelsEnabled;
        putBoolean("measureIndividualTypesEnabled", measureIndividualChannelsEnabled);
    }


}
