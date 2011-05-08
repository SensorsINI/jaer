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

import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import java.util.*;
import net.sf.jaer.Description;

/**
 * An AE background that filters slow background activity by only passing inPacket that are
 * supported by another event in the past {@link #setDt dt} in the immediate spatial neighborhood, defined
 * by a subsampling bit shift.
 * @author tobi
 */
@Description("Counts relation of Y only events to correct events for cDVSTest chips")
public class cDVSTestFilter extends EventFilter2D implements Observer  {

    /** the time in timestamp ticks (1us at present) that a spike
     * needs to be supported by a prior event in the neighborhood by to pass through
     */

  
    /** the amount to subsample x and y event location by in bit shifts when writing to past event times
     *map. This effectively increases the range of support. E.g. setting subSamplingShift to 1 quadruples range
     *because both x and y are shifted right by one bit */


    
    int yOnly;
    
    public cDVSTestFilter(AEChip chip){
        super(chip);
        chip.addObserver(this);
   //     initFilter();
   //     resetFilter();
    }
    

    
    
    /**
     * filters in to out. if filtering is enabled, the number of out may be less
     * than the number putString in
     *@param in input events can be null or empty.
     *@return the processed events, may be fewer in number. filtering may occur in place in the in packet.
     */
    synchronized public EventPacket filterPacket(EventPacket in) {
        if (!filterEnabled) {
            return in;
        }
        if (enclosedFilter != null) {
            in = enclosedFilter.filterPacket(in);
        }
        checkOutputPacketEventType(in);

        OutputEventIterator outItr = out.outputIterator();
        yOnly = 0;

        for (Object e : in) {
            BasicEvent i = (BasicEvent) e;
            if (i.x > 128) {
                yOnly++;
            }
            if (isMaskColorChangeEvents()) {
                if (!((i.x < 64) && (i.y % 2 == 0))) {
                    BasicEvent o = (BasicEvent) outItr.nextOutput();
//                    m.invoke(o,i);
                    o.copyFrom(i);
                }
            }
        }
//        }catch(Exception e){
//            e.printStackTrace();
//        }
        if (isDisplayYonlyInfo()) {
            log.info("num yOnly Events: " + yOnly + ", ratio " + (float) yOnly / ((float) in.getSize()));
        }
        if (isMaskColorChangeEvents()) {
            return out;
        }
        return in;
    }
    
    /**
     * gets the background allowed delay in us
     * @return delay allowed for spike since last in neighborhood to pass (us)
     */

    
    /**
     * sets the background delay in us
     <p>
     Fires a PropertyChangeEvent "dt"
     
     * @see #getDt
     * @param dt delay in us
     */

    boolean displayYonlyInfo;

    public boolean isDisplayYonlyInfo (){
        return displayYonlyInfo;
    }

    public void setDisplayYonlyInfo (boolean disp){
        this.displayYonlyInfo = disp;
        //getPrefs().putBoolean("RotateFilter.invertX",invertX);
    }
 
    boolean maskColorChangeEvents;
    
    public boolean isMaskColorChangeEvents (){
        return maskColorChangeEvents;
    }

    public void setMaskColorChangeEvents (boolean disp){
        this.maskColorChangeEvents = disp;
        //getPrefs().putBoolean("RotateFilter.invertX",invertX);
    }

    public Object getFilterState() {
        return yOnly;
    }
    

    
    synchronized public void resetFilter() {
        // set all lastTimestamps to max value so that any event is soon enough, guarenteed to be less than it
        yOnly=0;
    }
    
    
    public void update(Observable o, Object arg) {
//        if(!isFilterEnabled()) return;
        initFilter();
    }
    
    public void initFilter() {
    
    }    
}
