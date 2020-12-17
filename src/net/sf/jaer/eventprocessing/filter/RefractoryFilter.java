package net.sf.jaer.eventprocessing.filter;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.Observable;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.eventio.AEInputStream;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.AbstractAEPlayer;

/**
 * Adds a refractory period to pixels so that they events only pass if there is
 * sufficient time since the last event from that pixel; so it knocks out high
 * firing rates from cells. The option passShortISIsEnabled inverts the logic.
 * redundant events.
 *
 * @author tobi
 */
@Description("Applies a refractory period to pixels so that they events only pass if there is sufficient time since the last event from that pixel")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class RefractoryFilter extends AbstractNoiseFilter implements PropertyChangeListener {

    final int DEFAULT_TIMESTAMP = Integer.MIN_VALUE;
    /**
     * the time in timestamp ticks (1us at present) that a spike needs to be
     * supported by a prior event in the neighborhood by to pass through
     */
    protected int refractoryPeriodUs = getPrefs().getInt("RefractoryFilter.refractoryPeriodUs", 1000);
    /**
     * the amount to subsample x and y event location by in bit shifts when
     * writing to past event times map. This effectively increases the range of
     * support. E.g. setting subSamplingShift to 1 quadruples range because both
     * x and y are shifted right by one bit
     */
    private int subsampleBy = getPrefs().getInt("RefractoryFilter.subsampleBy", 0);
    private boolean passShortISIsEnabled = prefs().getBoolean("RefractoryFilter.passShortISIsEnabled", false);
    int[][] lastTimestamps;


    public RefractoryFilter(AEChip chip) {
        super(chip);
        initFilter();
        resetFilter();
        setPropertyTooltip("refractoryPeriodUs", "Events with less than this delta time in us are blocked");
        setPropertyTooltip("subsampleBy", "Past event addresses are subsampled by this many bits in x and y");
        setPropertyTooltip("passShortISIsEnabled", "<html>Inverts filtering so that only events with short ISIs are passed through.<br>If refractoryPeriodUs==0, then you can block all events with idential timestamp from the same pixel.");
    }

    void allocateMaps(AEChip chip) {
        lastTimestamps = new int[chip.getSizeX()][chip.getSizeY()];
    }
    int ts = 0; // used to reset filter

    /**
     * filters in to out. if filtering is enabled, the number of out may be less
     * than the number putString in
     *
     * @param in input events can be null or empty.
     * @return the processed events, may be fewer in number. filtering may occur
     * in place in the in packet.
     */
    synchronized public EventPacket filterPacket(EventPacket in) {
//        checkOutputPacketEventType(in);
        if (lastTimestamps == null) {
            allocateMaps(chip);
        }
        maybeAddListeners(chip);
        int sx = chip.getSizeX(), sy = chip.getSizeY();
        // for each event only write it to the out buffers if it is 
        // more than refractoryPeriodUs after the last time an event happened in neighborhood
//        OutputEventIterator outItr = getOutputPacket().outputIterator();
//        int sx = chip.getSizeX() - 1;
//        int sy = chip.getSizeY() - 1;
        totalEventCount = 0;
        filteredOutEventCount = 0;
        for (Object e : in) {
            BasicEvent i = (BasicEvent) e;
            if (i.isSpecial()) {
                continue;
            }
            if (i.x >= sx || i.x < 0 || i.y >= sy || i.y < 0) {
                continue;
            }
            totalEventCount++;
            ts = i.timestamp;
            short x = (short) (i.x >>> subsampleBy), y = (short) (i.y >>> subsampleBy);
            int lastt = lastTimestamps[x][y];
            int deltat = (ts - lastt);
            boolean longISI = lastt == DEFAULT_TIMESTAMP || deltat > refractoryPeriodUs; // if refractoryPeriodUs==0, then all events with ISI==0 pass if passShortISIsEnabled
            if ((longISI && !passShortISIsEnabled) || (!longISI && passShortISIsEnabled)) {
//                BasicEvent o = (BasicEvent) outItr.nextOutput();
//                o.copyFrom(i);
                i.setFilteredOut(false);
            } else {
                i.setFilteredOut(true);
                filteredOutEventCount++;
            }
            lastTimestamps[x][y] = ts;
        }
        return in;
    }

    /**
     * gets the refractory period
     *
     * @return delay allowed for spike since last in neighborhood to pass (us)
     */
    public int getRefractoryPeriodUs() {
        return this.refractoryPeriodUs;
    }

    /**
     * sets the refractory delay in us
     * <p>
     * Fires a PropertyChangeEvent "refractoryPeriodUs"
     *
     * @param refractoryPeriodUs the address is refractory for this long in us
     * after an event
     */
    public void setRefractoryPeriodUs(final int refractoryPeriodUs) {
        this.refractoryPeriodUs = refractoryPeriodUs;
        getPrefs().putInt("RefractoryFilter.refractoryPeriodUs", refractoryPeriodUs);
        getSupport().firePropertyChange("refractoryPeriodUs", null, refractoryPeriodUs);
    }

    public Object getFilterState() {
        return lastTimestamps;
    }

    void resetLastTimestamps() {
        for (int[] a : lastTimestamps) {
            Arrays.fill(a, DEFAULT_TIMESTAMP);
        }
    }

    synchronized public void resetFilter() {
        // set all lastTimestamps to max value so that any event is soon enough, guarenteed to be less than it
        resetLastTimestamps();
    }

    public void initFilter() {
        allocateMaps(chip);
    }

    public int getSubsampleBy() {
        return subsampleBy;
    }

    /**
     * Sets the number of bits to subsample by when storing events into the map
     * of past events. Increasing this value will increase the number of events
     * that pass through and will also allow passing events from small sources
     * that do not stimulate every pixel.
     *
     * @param subsampleBy the number of bits, 0 means no subsampling, 1 means
     * cut event time map resolution by a factor of two in x and in y
     *
     */
    public void setSubsampleBy(int subsampleBy) {
        if (subsampleBy < 0) {
            subsampleBy = 0;
        } else if (subsampleBy > 4) {
            subsampleBy = 4;
        }
        this.subsampleBy = subsampleBy;
        getSupport().firePropertyChange("subsampleBy", null, subsampleBy);
        getPrefs().putInt("RefractoryFilter.subsampleBy", subsampleBy);
    }

    /**
     * @return the passShortISIsEnabled
     */
    public boolean isPassShortISIsEnabled() {
        return passShortISIsEnabled;
    }

    /**
     * @param passShortISIsEnabled the passShortISIsEnabled to set
     */
    public void setPassShortISIsEnabled(boolean passShortISIsEnabled) {
        boolean old = this.passShortISIsEnabled;
        this.passShortISIsEnabled = passShortISIsEnabled;
        prefs().putBoolean("RefractoryFilter.passShortISIsEnabled", passShortISIsEnabled);
        getSupport().firePropertyChange("passShortISIsEnabled", old, passShortISIsEnabled);
    }

    @Override
    public int[][] getLastTimesMap() {
        return lastTimestamps;
    }



}
