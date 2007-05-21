/*
 * RetinaBackgrondActivityFilter.java
 *
 * Created on October 21, 2005, 12:33 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.caviar.eventprocessing.filter;

import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import java.util.*;

/**
 * An AE filter that filters out boring repetitive events.
 *It does this by maintaining an internal map of boring cells (x,y,type). These are boring because they are repetitive. An event is
 *passed through if its interspike interval (the time since the last event for this cell) is different by a factor of more than {@link #ratioShorter} to the
 *previous interspike interval. This average dt has a smoothing 'time constant' averagingSamples.
 <p>
 The switch passRepetitiousEvents passes only repetitious events in case those are the interesting ones.
 
 *
 * @author tobi
 */
public class RepetitiousFilter extends EventFilter2D implements Observer  {
    public boolean isGeneratingFilter(){ return false;}
    
    /** factor different than previous dt for this cell to pass through filter */
    protected int ratioShorter=prefs.getInt("RepetitiousFilter.ratioShorter", 2);
    {setPropertyTooltip("ratioShorter","filter events with ISI shorter by this ratio");}
    
    /** factor different than previous dt for this cell to pass through filter */
    protected int ratioLonger=prefs.getInt("RepetitiousFilter.ratioLonger", 2);
    {setPropertyTooltip("ratioLonger","filter events with ISI longer by this ratio");}
    
    /** the minimum dt to record, to help reject multiple events from much slower stimulus variation (e.g. 50/100 Hz) */
    protected int minDtToStore=prefs.getInt("RepetitiousFilter.minDtToStore", 1000);
    {setPropertyTooltip("minDtToStore","minimum delta timestamp to consider - use to filter bursts");}
    
    /** true to enable passing repetitious events
     */
    private boolean passRepetitiousEvents=prefs.getBoolean("RepetitiousFilter.passRepetitiousEvents",false);
    {setPropertyTooltip("passRepetitiousEvents","Enabled to flip sense so that repetitious events pass through");}
    
    int[][][][] lastTimesMap;
    int[][][] avgDtMap;
    
    final int NUMTIMES=2;
    
    /** the number of packets processed to average over */
    protected int averagingSamples=prefs.getInt("RepetitiousFilter.averagingSamples", 3);
    {setPropertyTooltip("averagingSamples","Number of events to IIR-average over to compute ISI");}
    
    public RepetitiousFilter(AEChip chip){
        super(chip);
        chip.addObserver(this);
    }
    
    void checkMap(){
        if(lastTimesMap==null
                || lastTimesMap.length!=chip.getSizeX()+2
                || lastTimesMap[0].length!=chip.getSizeY()+2
                || lastTimesMap[0][0].length!=chip.getNumCellTypes()
                || lastTimesMap[0][0][0].length!=NUMTIMES){
            allocateMap();
        }
    }
    
    private void allocateMap() {
        if(!isFilterEnabled()){
            lastTimesMap=null;
            avgDtMap=null;
        }else{
            log.info("RepetitiousFilter.allocateMaps");
            lastTimesMap=new int[chip.getSizeX()+2][chip.getSizeY()+2][chip.getNumCellTypes()][NUMTIMES];
            avgDtMap=new int[chip.getSizeX()+2][chip.getSizeY()+2][chip.getNumCellTypes()];
        }
    }
    
    /** returns array of last event times, x,y,type,[t0,t1], where t0/t1 are the last two event times, t0 first. */
    public int[][][][] getFilterState() {
        return lastTimesMap;
    }
    
    synchronized public void resetFilter() {
        initFilter();
    }
    
    public int getAveragingSamples() {
        return this.averagingSamples;
    }
    
    /** sets the number of packets to smooth dt for a pixel over */
    public void setAveragingSamples(final int averagingSamples) {
        if(averagingSamples<1) return;
        prefs.putInt("RepetitiousFilter.averagingSamples",averagingSamples);
        support.firePropertyChange("averagingSamples",this.averagingSamples,averagingSamples);
        this.averagingSamples = averagingSamples;
    }
    
    public int getMinDtToStore() {
        return this.minDtToStore;
    }
    
    public void setMinDtToStore(final int minDtToStore) {
        prefs.putInt("RepetitiousFilter.minDtToStore",minDtToStore);
        support.firePropertyChange("minDtToStore",this.minDtToStore,minDtToStore);
        this.minDtToStore = minDtToStore;
    }
    
    public int getRatioShorter() {
        return this.ratioShorter;
    }
    
    public void setRatioShorter(final int ratioShorter) {
        if(ratioShorter<1) return;
        support.firePropertyChange("ratioShorter",this.ratioLonger,ratioShorter);
        prefs.putInt("RepetitiousFilter.ratioShorter",ratioShorter);
        this.ratioShorter = ratioShorter;
    }
    
    public int getRatioLonger() {
        return this.ratioLonger;
    }
    
    public void setRatioLonger(final int ratioLonger) {
        if(ratioLonger<1) return;
        support.firePropertyChange("ratioLonger",this.ratioLonger,ratioLonger);
        prefs.putInt("RepetitiousFilter.ratioLonger",ratioLonger);
        this.ratioLonger = ratioLonger;
    }
    
    public void update(Observable o, Object arg) {
        initFilter();
    }
    
    public void initFilter() {
        if(!isFilterEnabled()) return;
        allocateMap();
    }
    
    public EventPacket filterPacket(EventPacket in) {
        if(!filterEnabled || in==null) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        checkOutputPacketEventType(in);
        checkMap();
        int n=in.getSize();
        if(n==0) return in;
                 float alpha=1/(float)averagingSamples; // the bigger averagingSamples, the smaller alpha
       
        // for each event only write it to the tmp buffers if it isn't boring
        // this means only write if the dt is sufficiently different than the previous dt
        OutputEventIterator o=out.outputIterator();
        for(Object i:in){
            TypedEvent e=(TypedEvent)i;
            int[] lasttimes=lastTimesMap[e.x][e.y][e.type]; // avoid indexing again and again
            int lastt=lasttimes[1];
            int lastdt=lastt-lasttimes[0];
            int thisdt=e.timestamp-lastt;
            int avgDt=avgDtMap[e.x][e.y][e.type];
            // if this dt is greater than last by threshold or less than last by threshold pass it
            boolean outside=thisdt>avgDt*ratioLonger || thisdt<avgDt/ratioShorter;
            if( (!passRepetitiousEvents&outside) || (passRepetitiousEvents&outside) ){
                o.nextOutput().copyFrom(e);
            }
            // update the map
            if(thisdt>minDtToStore) {
                lasttimes[0]=lastt;
                lasttimes[1]=e.timestamp;
                
                avgDtMap[e.x][e.y][e.type]=(int)(avgDt*(1-alpha)+lastdt*(alpha));
            }
        }
        return out;
    }

    public boolean getPassRepetitiousEvents() {
        return passRepetitiousEvents;
    }

    public void setPassRepetitiousEvents(boolean passRepetitiousEvents) {
        this.passRepetitiousEvents = passRepetitiousEvents;
        prefs.putBoolean("RepetitiousFilter.passRepetitiousEvents",passRepetitiousEvents);
    }
    
}
