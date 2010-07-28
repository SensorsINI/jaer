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

/**
 * An AE background that filters slow background activity by only passing inPacket that are
 * supported by another event in the past {@link #setDt dt} in the immediate spatial neighborhood, defined
 * by a subsampling bit shift.
 * @author tobi
 */
public class BackgroundActivityFilter extends EventFilter2D implements Observer  {

    public static String getDescription() {
        return "Filters out uncorrelated background activity";
    }
    
    final int DEFAULT_TIMESTAMP=Integer.MIN_VALUE;
    
    /** the time in timestamp ticks (1us at present) that a spike
     * needs to be supported by a prior event in the neighborhood by to pass through
     */
    protected int dt=getPrefs().getInt("BackgroundActivityFilter.dt",30000);
    {setPropertyTooltip("dt","Events with less than this delta time in us to neighbors pass through");}
  
    /** the amount to subsample x and y event location by in bit shifts when writing to past event times
     *map. This effectively increases the range of support. E.g. setting subSamplingShift to 1 quadruples range
     *because both x and y are shifted right by one bit */
    private int subsampleBy=getPrefs().getInt("BackgroundActivityFilter.subsampleBy",0);
    {setPropertyTooltip("subsampleBy","Past events are spatially subsampled (address right shifted) by this many bits");}

    
    int[][] lastTimestamps;
    
    public BackgroundActivityFilter(AEChip chip){
        super(chip);
        chip.addObserver(this);
        initFilter();
        resetFilter();
    }
    
    void allocateMaps(AEChip chip){
        lastTimestamps=new int[chip.getSizeX()][chip.getSizeY()];
    }
    
    int ts=0; // used to reset filter
    
    /**
     * filters in to out. if filtering is enabled, the number of out may be less
     * than the number put in
     *@param in input events can be null or empty.
     *@return the processed events, may be fewer in number. filtering may occur in place in the in packet.
     */
    synchronized public EventPacket filterPacket(EventPacket in) {
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        checkOutputPacketEventType(in);
        if(lastTimestamps==null) allocateMaps(chip);
            // for each event only write it to the out buffers if it is within dt of the last time an event happened in neighborhood
            OutputEventIterator outItr=out.outputIterator();
            int sx=chip.getSizeX()-1;
            int sy=chip.getSizeY()-1;
            for(Object e:in){
                BasicEvent i=(BasicEvent)e;
                ts=i.timestamp;
                short x=(short)(i.x>>>subsampleBy), y=(short)(i.y>>>subsampleBy);
                int lastt=lastTimestamps[x][y];
                int deltat=(ts-lastt);
                if(deltat<dt && lastt!=DEFAULT_TIMESTAMP){
                    BasicEvent o=(BasicEvent)outItr.nextOutput();
//                    m.invoke(o,i);
                    o.copyFrom(i);
                }
                
                try{
                    // for each event stuff the event's timestamp into the lastTimestamps array at neighboring locations
                    //lastTimestamps[x][y][type]=ts; // don't write to ourselves, we need support from neighbor for next event
                    // bounds checking here to avoid throwing expensive exceptions, even though we duplicate java's bound checking...
                    if(x>0) lastTimestamps[x-1][y]=ts;
                    if(x<sx) lastTimestamps[x+1][y]=ts;
                    if(y>0) lastTimestamps[x][y-1]=ts;
                    if(y<sy) lastTimestamps[x][y+1]=ts;
                    if(x>0 && y>0) lastTimestamps[x-1][y-1]=ts;
                    if(x<sx && y<sy) lastTimestamps[x+1][y+1]=ts;
                    if(x>0 && y<sy) lastTimestamps[x-1][y+1]=ts;
                    if(x<sx && y>0) lastTimestamps[x+1][y-1]=ts;
                }catch(ArrayIndexOutOfBoundsException eoob){
                    allocateMaps(chip);
                }  // boundaries
                
            }
//        }catch(Exception e){
//            e.printStackTrace();
//        }
        return out;
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
        getPrefs().putInt("BackgroundActivityFilter.dt",dt);
        getSupport().firePropertyChange("dt",this.dt,dt);
        this.dt = dt;
    }

    public int getMinDt(){
        return 10;
    }

    public int getMaxDt(){
        return 100000;
    }

    public Object getFilterState() {
        return lastTimestamps;
    }
    
    void resetLastTimestamps(){
        for(int i=0;i<lastTimestamps.length;i++)
            Arrays.fill(lastTimestamps[i],DEFAULT_TIMESTAMP);
    }
    
    synchronized public void resetFilter() {
        // set all lastTimestamps to max value so that any event is soon enough, guarenteed to be less than it
        resetLastTimestamps();
    }
    
    
    public void update(Observable o, Object arg) {
//        if(!isFilterEnabled()) return;
        initFilter();
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
        if(subsampleBy<0) subsampleBy=0; else if(subsampleBy>4) subsampleBy=4;
        this.subsampleBy = subsampleBy;
        getPrefs().putInt("BackgroundActivityFilter.subsampleBy",subsampleBy);
    }
    
    
}
