/*
 * TypeCoincidenceFilter.java
 *
 * Created on 27.1.2006 Tobi
 *
 */

package ch.unizh.ini.caviar.eventprocessing.label;

import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import java.util.*;

/**
 * Computes coincidences betweeen different types of events at the same location in its input. Intended for e.g., a corner detector that works by
 *simulatanous vertical and horizontal edges.
 *
 * @author tobi
 */
public class TypeCoincidenceFilter extends EventFilter2D implements Observer {
    public boolean isGeneratingFilter(){ return true;}
    
    /** events must occur within this time along orientation in us to generate an event */
//    protected int maxDtThreshold=prefs.getInt("SimpleOrientationFilter.maxDtThreshold",Integer.MAX_VALUE);
    protected int minDtThreshold=prefs.getInt("TypeCoincidenceFilter.minDtThreshold",10000);
    
    static final int MAX_DIST=5;
    private int dist=prefs.getInt("TypeCoincidenceFilter.dist",0);
    
    static final int NUM_INPUT_CELL_TYPES=4;
    int[][][] lastTimesMap;
    
    /** the number of cell output types */
    public final int NUM_TYPES=4; // we make it big so rendering is in color
    
    /** Creates a new instance of TypeCoincidenceFilter */
    public TypeCoincidenceFilter(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        resetFilter();
    }
    
    public Object getFilterState() {
        return lastTimesMap;
    }
    
    SimpleOrientationFilter oriFilter;
    
    synchronized public void resetFilter() {
        allocateMap();
        if(oriFilter==null) oriFilter=new SimpleOrientationFilter(chip);
        setEnclosedFilter(oriFilter);
    }
    
    static final int PADDING=MAX_DIST*2, P=MAX_DIST;
    
    void checkMap(){
        if(lastTimesMap==null) allocateMap();
    }
    
    private void allocateMap() {
        if(!isFilterEnabled()) return;
        lastTimesMap=new int[chip.getSizeX()+PADDING][chip.getSizeY()+PADDING][NUM_INPUT_CELL_TYPES];
    }
    
    int[][] dts=new int[4][2]; // delta times to neighbors in each direction
    int[] maxdts=new int[4]; // max times to neighbors in each dir
    
    public int getMinDtThreshold() {
        return this.minDtThreshold;
    }
    
    public void setMinDtThreshold(final int minDtThreshold) {
        this.minDtThreshold = minDtThreshold;
        prefs.putInt("TypeCoincidenceFilter.minDtThreshold", minDtThreshold);
    }
    
    public void initFilter() {
        resetFilter();
    }
    
    public void update(Observable o, Object arg) {
        initFilter();
    }
    
    EventPacket<OrientationEvent> oriPacket;
    
    synchronized public EventPacket filterPacket(EventPacket in) {
        if(in==null) return null;
        if(!filterEnabled) return in;
        if(in.getEventClass()!=PolarityEvent.class){
            log.warning("wrong input cell type "+in+", disabling filter");
            setFilterEnabled(false);
            return in;
        }
        oriPacket=(EventPacket<OrientationEvent>)(enclosedFilter.filterPacket(in));
        checkMap();
        checkOutputPacketEventType(in);
        int n=in.getSize();
        
        // for each orientation event that has been output from the orifilter
        // write out a PolarityEvent event from the orievent
        // iff there has been an orievent of 90 angle to this one in immediate neighborhood within past minDtThreshold
        OutputEventIterator outItr=out.outputIterator();
        for(Object o:oriPacket){
            OrientationEvent e=(OrientationEvent)o;  // the orievent
            // save time of event in lastTimesMap
            lastTimesMap[e.x+P][e.y+P][e.orientation]=e.timestamp;
            
            // compute orthogonal orientation
            int orthOri=(e.orientation+2)%4;
            breakOut:
            for(int x=-dist;x<=dist;x++){
                for(int y=-dist;y<=dist;y++){
                    // in neighborhood, compute dt between this event and prior events at orthog orientation
                    int dt=e.timestamp-lastTimesMap[e.x+x+P][e.y+y+P][orthOri];
                    // now write output cell if previous event within minDtThreshold
                    if( dt<minDtThreshold ){
                        PolarityEvent oe=(PolarityEvent)outItr.nextOutput();
                        oe.copyFrom((PolarityEvent)e);
                        break breakOut;
                    }
                }
            }
        }
        return out;
    }

    public int getDist() {
        return dist;
    }

    /** sets neighborhood distance */
    public void setDist(int dist) {
        if(dist>MAX_DIST) dist=MAX_DIST; else if(dist<0) dist=0;
        this.dist = dist;
        prefs.putInt("TypeCoincidenceFilter.dist",dist);
    }
    
}
