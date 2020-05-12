/*
 * FilterSyncedEvents.java
 *
 * Created on February 1, 2012
 *
 */
package ch.unizh.ini.jaer.projects.laser3d;

import java.util.Arrays;
import java.util.Observable;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 * Tracks pulsed laserline
 *
 * @author Thomas
 */
@Description("Filters out events, which are not in a specific timewindow")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class FilterSyncedEvents extends EventFilter2D {

    /**
     * *******
     * Options *******
     */
    /**
     *
     */
    protected boolean useOnAndOff = getPrefs().getBoolean("FilterSyncedEvents.useOnAndOff", true);
    /**
     *
     */
    protected int t0 = getPrefs().getInt("FilterSyncedEvents.t0", 500);
    /**
     *
     */
    protected int t1 = getPrefs().getInt("FilterSyncedEvents.t1", 500);

    /**
     * *********************
     * Variables *********************
     */
    private int DEFAULT_TIMESTAMP = 0; //Integer.MIN_VALUE;
    private int[][] lastOnTimestamps;
    private int[][] lastOffTimestamps;
    private int lastTriggerTimestamp;
    private int laserPeriod;

    /**
     * Creates a new instance of FilterLaserLine
     *
     * @param chip
     */
    public FilterSyncedEvents(AEChip chip) {
        super(chip);
        initFilter();
        resetFilter();
        setPropertyTooltip("Trigger Signal", "t0", "latency of the laserbeam to the trigger signal");
        setPropertyTooltip("Trigger Signal", "t1", "windowlength for on events");
        setPropertyTooltip("Trigger Signal", "useOnAndOff", "Use on and off events combined?");
    }

    @Override
    public EventPacket filterPacket(EventPacket in) {
        if (!isFilterEnabled()) {
            return in;
        }
        if (lastOnTimestamps == null) {
            initFilter();
        }
//        out.setEventClass(PolarityEvent.class);
        OutputEventIterator outItr = out.outputIterator();
        for (Object e : in) {
            PolarityEvent pE = (PolarityEvent) e;
            if (pE.isSpecial()) {
                if (lastTriggerTimestamp != DEFAULT_TIMESTAMP) {
                    laserPeriod = pE.timestamp - lastTriggerTimestamp;
                    if (laserPeriod < 0) {
                        log.warning("laserPeriod is smaller 0!");
                        laserPeriod = 0;
                    }
                }
                lastTriggerTimestamp = pE.timestamp;
                BasicEvent o = outItr.nextOutput();
                o.copyFrom(pE);
                continue;
            }
            updateMaps(pE);
            int lastOnTimestamp;
            int lastOffTimestamp;

            if (pE.polarity == PolarityEvent.Polarity.Off) {
                lastOnTimestamp = getLastActivity(pE.x, pE.y, PolarityEvent.Polarity.On);
                lastOffTimestamp = pE.timestamp;

                if ((lastOnTimestamp >= (lastTriggerTimestamp + t0))
                        & (lastOnTimestamp < (lastTriggerTimestamp + t0 + t1))
                        & (lastOffTimestamp > (lastTriggerTimestamp + (laserPeriod / 2)))) {
                    BasicEvent o = outItr.nextOutput();
                    o.copyFrom(pE);
                }
            }
        }
        return out;
    }

    @Override
    public final void resetFilter() {
        if ((lastOnTimestamps != null) & (lastOffTimestamps != null) & (chip != null)) {
            for (int x = 0; x < chip.getSizeX(); x++) {
                Arrays.fill(lastOnTimestamps[x], DEFAULT_TIMESTAMP);
                Arrays.fill(lastOffTimestamps[x], DEFAULT_TIMESTAMP);
            }
        }
    }

    @Override
    public final void initFilter() {
        if ((chip != null) & (chip.getNumCells() > 0)) {
            lastOnTimestamps = new int[chip.getSizeX()][chip.getSizeY()];
            lastOffTimestamps = new int[chip.getSizeX()][chip.getSizeY()];
        }
    }

    private void updateMaps(PolarityEvent ev) {
        if (ev.polarity == PolarityEvent.Polarity.On) {
            lastOnTimestamps[ev.x][ev.y] = ev.timestamp;
        } else if (ev.polarity == PolarityEvent.Polarity.Off) {
            lastOffTimestamps[ev.x][ev.y] = ev.timestamp;
        }
    }

    /**
     *
     * @param x
     * @param y
     * @return
     */
    public int getLastActivity(short x, short y) {
        if (lastOnTimestamps[x][y] > lastOffTimestamps[x][y]) {
            return lastOnTimestamps[x][y];
        } else {
            return lastOffTimestamps[x][y];
        }
    }

    /**
     *
     * @param x
     * @param y
     * @param pol
     * @return
     */
    public int getLastActivity(short x, short y, PolarityEvent.Polarity pol) {
        if (pol == PolarityEvent.Polarity.On) {
            return lastOnTimestamps[x][y];
        } else if (pol == PolarityEvent.Polarity.Off) {
            return lastOffTimestamps[x][y];
        } else {
            log.info("getLastActivity: Wrong polarity");
            return DEFAULT_TIMESTAMP;
        }
    }

    /**
     *
     * @return
     */
    public Object getFilterState() {
        return null;
    }

    /**
     * Functions for options
     */
    /**
     * gets t0
     *
     * @return t0
     */
    public int getT0() {
        return this.t0;
    }

    /**
     * sets ts
     *
     * @see #getT0
     * @param t0 timespan between triggersignal an the laser to actually turn on
     */
    public void setT0(final int t0) {
        getPrefs().putInt("FilterSyncedEvents.t0", t0);
        getSupport().firePropertyChange("t0", this.t0, t0);
        this.t0 = t0;
    }

    /**
     *
     * @return
     */
    public int getMinT0() {
        return 0;
    }

    /**
     *
     * @return
     */
    public int getMaxT0() {
        return 1000;
    }

    /**
     * gets t1
     *
     * @return t1
     */
    public int getT1() {
        return this.t1;
    }

    /**
     * sets ts
     *
     * @see #getT1
     * @param t1 timewindow for on event of laserline pixels in us
     */
    public void setT1(final int t1) {
        getPrefs().putInt("FilterSyncedEvents.t1", t1);
        getSupport().firePropertyChange("t1", this.t1, t1);
        this.t1 = t1;
    }

    /**
     *
     * @return
     */
    public int getMinT1() {
        return 0;
    }

    /**
     *
     * @return
     */
    public int getMaxT1() {
        return 1000;
    }
//    /**
//     * gets useOnAndOff
//     *
//     * @return boolean whether to use the sync signal triggering a special event
//     */
//    public boolean getUseOnAndOff() {
//        return this.useOnAndOff;
//    }
//
//    /**
//     * sets useOnAndOff
//     *
//     * @see #getUseOnAndOff
//     * @param useOnAndOff boolean
//     */
//    public void setUseOnAndOff(final boolean useOnAndOff) {
//        getPrefs().putBoolean("TrackLaserLine.useOnAndOff", useOnAndOff);
//        getSupport().firePropertyChange("useOnAndOff", this.useOnAndOff, useOnAndOff);
//        this.useOnAndOff = useOnAndOff;
//        pxlMap.updateSettings();
//    }
}
