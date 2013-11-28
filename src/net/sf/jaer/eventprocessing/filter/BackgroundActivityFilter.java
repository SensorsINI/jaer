/*
 * RetinaBackgrondActivityFilter.java
 *
 * Created on October 21, 2005, 12:33 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */
package net.sf.jaer.eventprocessing.filter;

import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 * An AE background that filters slow background activity by only passing inPacket that are
 * supported by another event in the past {@link #setDt dt} in the immediate spatial neighborhood, defined
 * by a subsampling bit shift.
 * @author tobi
 */
@Description("Filters out uncorrelated background activity")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class BackgroundActivityFilter extends EventFilter2D implements Observer {

    final int DEFAULT_TIMESTAMP = Integer.MIN_VALUE;
    /** the time in timestamp ticks (1us at present) that a spike
     * needs to be supported by a prior event in the neighborhood by to pass through
     */
    protected int dt = getInt("dt", 30000);

     /** the amount to subsample x and y event location by in bit shifts when writing to past event times
     *map. This effectively increases the range of support. E.g. setting subSamplingShift to 1 quadruples range
     *because both x and y are shifted right by one bit */
    private int subsampleBy = getInt("subsampleBy", 0);
    
    
    private boolean filterInPlace=getBoolean("filterInPlace", true);

     int[][] lastTimestamps;

    public BackgroundActivityFilter(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        initFilter();
        resetFilter();
        setPropertyTooltip("dt", "Events with less than this delta time in us to neighbors pass through");
        setPropertyTooltip("subsampleBy", "Past events are spatially subsampled (address right shifted) by this many bits");
   }

    private void allocateMaps(AEChip chip) {
        if (chip != null && chip.getNumCells() > 0) {
            lastTimestamps = new int[chip.getSizeX()][chip.getSizeY()];
        }
    }
    private int ts = 0; // used to reset filter

    /**
     * filters in to out. if filtering is enabled, the number of out may be less
     * than the number putString in
     *@param in input events can be null or empty.
     *@return the processed events, may be fewer in number. filtering may occur in place in the in packet.
     */
    synchronized public EventPacket filterPacket(EventPacket in) {
        // Make sure that this filter's built-in output packet is of same type as input packet in. 
        // This also sets up output packet for bypassing input events that should not be processed here.
//        checkOutputPacketEventType(in); 
        if (lastTimestamps == null) {
            allocateMaps(chip);
        }
        // for each event only write it to the out buffers if it is within dt of the last time an event happened in neighborhood
//        OutputEventIterator outItr = getOutputPacket().outputIterator(); // gets the iterator to write out events we want to keep
        int sx = chip.getSizeX() - 1;
        int sy = chip.getSizeY() - 1;
        for (Object e : in) {
            if(e==null) break;  // this can occur if we are supplied packet that has data (e.g. APS samples) but no events
            BasicEvent i = (BasicEvent) e;
            if (i.special) continue;
//            {
//                outItr.writeToNextOutput(i);
//            }
            ts = i.timestamp;
            short x = (short) (i.x >>> subsampleBy), y = (short) (i.y >>> subsampleBy);
            if (x < 0 || x > sx || y < 0 || y > sy) {
                continue;
            }
            int lastt = lastTimestamps[x][y];
            int deltat = (ts - lastt);
            if ((deltat < dt && lastt != DEFAULT_TIMESTAMP)) {
//                outItr.writeToNextOutput(i);
                //System.out.println("x: "+i.x+" x: "+i.y+" dt: "+dt);
//                BasicEvent o = (BasicEvent) outItr.nextOutput();
////                    m.invoke(o,i);
//                o.copyFrom(i);
            }else{
                i.setFilteredOut(true);
            }

            try {
                // for each event stuff the event's timestamp into the lastTimestamps array at neighboring locations
                //lastTimestamps[x][y][type]=ts; // don't write to ourselves, we need support from neighbor for next event
                // bounds checking here to avoid throwing expensive exceptions, even though we duplicate java's bound checking...
                if (x > 0) {
                    lastTimestamps[x - 1][y] = ts;
                }
                if (x < sx) {
                    lastTimestamps[x + 1][y] = ts;
                }
                if (y > 0) {
                    lastTimestamps[x][y - 1] = ts;
                }
                if (y < sy) {
                    lastTimestamps[x][y + 1] = ts;
                }
                if (x > 0 && y > 0) {
                    lastTimestamps[x - 1][y - 1] = ts;
                }
                if (x < sx && y < sy) {
                    lastTimestamps[x + 1][y + 1] = ts;
                }
                if (x > 0 && y < sy) {
                    lastTimestamps[x - 1][y + 1] = ts;
                }
                if (x < sx && y > 0) {
                    lastTimestamps[x + 1][y - 1] = ts;
                }
            } catch (ArrayIndexOutOfBoundsException eoob) {
                allocateMaps(chip);
            }  // boundaries

        }
//        }catch(Exception e){
//            e.printStackTrace();
//        }
//        if (in.isEmpty()) {
//            return in; // handle case that packet contains APS samples but no DVS events
//        }
        int filteredOutCount=in.getFilteredOutCount();
        return in;
//        return getOutputPacket(); // return the events not filtered away, along with events that have been bypassed by the built-in packet input iterator
    }

    /**
     * gets the background allowed delay in us
     * @return delay allowed for spike since last in neighborhood to pass (us)
     */
    public int getDt() {
        return this.dt;
    }

    /**
     * sets the background delay in us
    <p>
    Fires a PropertyChangeEvent "dt"
    
     * @see #getDt
     * @param dt delay in us
     */
    public void setDt(final int dt) {
        putInt("dt", dt);
        getSupport().firePropertyChange("dt", this.dt, dt);
        this.dt = dt;
    }

    public int getMinDt() {
        return 10;
    }

    public int getMaxDt() {
        return 100000;
    }

    public Object getFilterState() {
        return lastTimestamps;
    }

    void resetLastTimestamps() {
        if (lastTimestamps == null) {
            return;
        }
        for (int i = 0; i < lastTimestamps.length; i++) {
            Arrays.fill(lastTimestamps[i], DEFAULT_TIMESTAMP);
        }
    }

    synchronized public void resetFilter() {
        // set all lastTimestamps to max value so that any event is soon enough, guarenteed to be less than it
        resetLastTimestamps();
    }

    public void update(Observable o, Object arg) {
        if (arg != null && (arg == AEChip.EVENT_SIZEX || arg == AEChip.EVENT_SIZEY)) {
            initFilter();
        }
    }

    public void initFilter() {
        allocateMaps(chip);
    }

    public int getSubsampleBy() {
        return subsampleBy;
    }

    /** Sets the number of bits to subsample by when storing events into the map of past events.
     *Increasing this value will increase the number of events that pass through and will also allow
     *passing events from small sources that do not stimulate every pixel.
     *@param subsampleBy the number of bits, 0 means no subsampling, 1 means cut event time map resolution by a factor of two in x and in y
     **/
    public void setSubsampleBy(int subsampleBy) {
        if (subsampleBy < 0) {
            subsampleBy = 0;
        } else if (subsampleBy > 4) {
            subsampleBy = 4;
        }
        this.subsampleBy = subsampleBy;
        putInt("subsampleBy", subsampleBy);
    }
}
