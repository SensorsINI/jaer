

package net.sf.jaer.eventprocessing.filter;

import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import java.util.*;

/**
 * Adds a refractory period to pixels so that they events only pass if there is sufficient 
 * time since the last event from that pixel. Basicall just knocks out 
 redundant events.
 
 * @author tobi
 */
public class RefractoryFilter extends EventFilter2D implements Observer  {
 public static String getDescription() {
        return "Adds a refractory period to pixels so that they events only pass if there is sufficient time since the last event from that pixel.";
    }    
    final int DEFAULT_TIMESTAMP=Integer.MIN_VALUE;
    
    /** the time in timestamp ticks (1us at present) that a spike
     * needs to be supported by a prior event in the neighborhood by to pass through
     */
    protected int refractoryPeriodUs=getPrefs().getInt("RefractoryFilter.refractoryPeriodUs",1000);
    {setPropertyTooltip("refractoryPeriodUs","Events with less than this delta time in us are blocked");}
  
    /** the amount to subsample x and y event location by in bit shifts when writing to past event times
     *map. This effectively increases the range of support. E.g. setting subSamplingShift to 1 quadruples range
     *because both x and y are shifted right by one bit */
    private int subsampleBy=getPrefs().getInt("RefractoryFilter.subsampleBy",0);
    {setPropertyTooltip("subsampleBy","Past event addresses are subsampled by this many bits");}

    
    int[][] lastTimestamps;
    
    public RefractoryFilter(AEChip chip){
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
            // for each event only write it to the out buffers if it is 
            // more than refractoryPeriodUs after the last time an event happened in neighborhood
            OutputEventIterator outItr=out.outputIterator();
            int sx=chip.getSizeX()-1;
            int sy=chip.getSizeY()-1;
            for(Object e:in){
                BasicEvent i=(BasicEvent)e;
                ts=i.timestamp;
                short x=(short)(i.x>>>subsampleBy), y=(short)(i.y>>>subsampleBy);
                int lastt=lastTimestamps[x][y];
                int deltat=(ts-lastt);
                if(deltat>refractoryPeriodUs && lastt!=DEFAULT_TIMESTAMP){
                    BasicEvent o=(BasicEvent)outItr.nextOutput();
                    o.copyFrom(i);
                }
                lastTimestamps[x][y]=ts;
            }
        return out;
    }
    
    /**
     * gets the refractory period
     * @return delay allowed for spike since last in neighborhood to pass (us)
     */
    public int getRefractoryPeriodUs() {
        return this.refractoryPeriodUs;
    }
    
    /**
     * sets the refractory delay in us
     *     <p>
     *     Fires a PropertyChangeEvent "refractoryPeriodUs"
     * 
     * @param refractoryPeriodUs the address is refractory for this long in us after an event
     */
    public void setRefractoryPeriodUs(final int refractoryPeriodUs) {
        this.refractoryPeriodUs=refractoryPeriodUs;
        getPrefs().putInt("RefractoryFilter.refractoryPeriodUs",refractoryPeriodUs);
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
        getPrefs().putInt("RefractoryFilter.subsampleBy",subsampleBy);
    }
    
    
}
