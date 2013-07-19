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
import java.util.Observable;
import java.util.Observer;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
/**
 * An AE filter that filters out boring repetitive events.
 *It does this by maintaining an internal map of boring cells (x,y,type). These are boring because they are repetitive. An event is
 *passed through if its interspike interval (the time since the last event for this cell) is different by a factor of more than {@link #ratioShorter} to the
 *previous interspike interval. This average dt has a smoothing 'time constant' averagingSamples.
<p>
The switch passRepetitiousEvents passes only repetitious events in case those are the interesting ones.
<p>
Fires PropertyChangeEvent for the following
<ul>
<li> "averagingSamples"
<li> "minDtToStore"
<li> "ratioShorter"
<li> "ratioLonger"
</ul>

 *
 * @author tobi
 */
@Description("Filters out (or in) repetitious (boring) events")
public class RepetitiousFilter extends EventFilter2D implements Observer{

    /** factor different than previous dt for this cell to pass through filter */
    private int ratioShorter = getPrefs().getInt("RepetitiousFilter.ratioShorter",2);
    /** factor different than previous dt for this cell to pass through filter */
    private int ratioLonger = getPrefs().getInt("RepetitiousFilter.ratioLonger",2);
    /** the minimum dt to record, to help reject multiple events from much slower stimulus variation (e.g. 50/100 Hz) */
    private int minDtToStore = getPrefs().getInt("RepetitiousFilter.minDtToStore",1000);
    /** true to enable passing repetitious events  */
    private boolean passRepetitiousEvents = getPrefs().getBoolean("RepetitiousFilter.passRepetitiousEvents",false);

    private boolean excludeHarmonics=false;
    /** Array of last event timestamps. */
    private int[][][][] lastTimesMap;
    /** Array of average ISIs: [chip.sizeX+][chip.sizeY+2][chip.numCellTypes] */
    protected int[][][] avgDtMap;
    final int NUMTIMES = 2;
    /** the number of packets processed to average over */
    private int averagingSamples = getPrefs().getInt("RepetitiousFilter.averagingSamples",3);

    public RepetitiousFilter (AEChip chip){
        super(chip);
        chip.addObserver(this);
        setPropertyTooltip("ratioShorter","filter events with ISI shorter by this ratio");
        setPropertyTooltip("ratioLonger","filter events with ISI longer by this ratio");
        setPropertyTooltip("averagingSamples","Number of events to IIR-average over to compute ISI");
        setPropertyTooltip("minDtToStore","minimum delta timestamp to consider - use to filter bursts");
        setPropertyTooltip("passRepetitiousEvents","Enabled to flip sense so that repetitious events pass through");
    }

    void checkMap (){
        if ( lastTimesMap == null
                || lastTimesMap.length != chip.getSizeX() + 2
                || lastTimesMap[0].length != chip.getSizeY() + 2
                || lastTimesMap[0][0].length != chip.getNumCellTypes()
                || lastTimesMap[0][0][0].length != NUMTIMES ){
            allocateMap();
        }
    }

    private void allocateMap (){
        if ( !isFilterEnabled() ){
            lastTimesMap = null;
            avgDtMap = null;
        } else{
            log.info("RepetitiousFilter.allocateMaps");
            lastTimesMap = new int[ chip.getSizeX() + 2 ][ chip.getSizeY() + 2 ][ chip.getNumCellTypes() ][ NUMTIMES ];
            avgDtMap = new int[ chip.getSizeX() + 2 ][ chip.getSizeY() + 2 ][ chip.getNumCellTypes() ];
        }
    }

    synchronized public void resetFilter (){
        initFilter();
    }

    public int getAveragingSamples (){
        return this.averagingSamples;
    }

    /** sets the number of packets to smooth dt for a pixel over */
    public void setAveragingSamples (final int averagingSamples){
        if ( averagingSamples < 1 ){
            return;
        }
        getPrefs().putInt("RepetitiousFilter.averagingSamples",averagingSamples);
        getSupport().firePropertyChange("averagingSamples",this.averagingSamples,averagingSamples);
        this.averagingSamples = averagingSamples;
    }

    public int getMinDtToStore (){
        return this.minDtToStore;
    }

    public void setMinDtToStore (final int minDtToStore){
        getPrefs().putInt("RepetitiousFilter.minDtToStore",minDtToStore);
        getSupport().firePropertyChange("minDtToStore",this.minDtToStore,minDtToStore);
        this.minDtToStore = minDtToStore;
    }

    public int getRatioShorter (){
        return this.ratioShorter;
    }

    public void setRatioShorter (final int ratioShorter){
        if ( ratioShorter < 1 ){
            return;
        }
        getSupport().firePropertyChange("ratioShorter",this.ratioLonger,ratioShorter);
        getPrefs().putInt("RepetitiousFilter.ratioShorter",ratioShorter);
        this.ratioShorter = ratioShorter;
    }

    public int getRatioLonger (){
        return this.ratioLonger;
    }

    public void setRatioLonger (final int ratioLonger){
        if ( ratioLonger < 1 ){
            return;
        }
        getSupport().firePropertyChange("ratioLonger",this.ratioLonger,ratioLonger);
        getPrefs().putInt("RepetitiousFilter.ratioLonger",ratioLonger);
        this.ratioLonger = ratioLonger;
    }

    public void update (Observable o,Object arg){
        initFilter();
    }

    public void initFilter (){
        if ( !isFilterEnabled() ){
            return;
        }
        allocateMap();
    }

    public EventPacket<?> filterPacket (EventPacket<?> in){
        checkOutputPacketEventType(in);
        checkMap();
        int n = in.getSize();
        if ( n == 0 ){
            return in;
        }
        float alpha = 1 / (float)averagingSamples; // the bigger averagingSamples, the smaller alpha

        // for each event only write it to the tmp buffers if it isn't boring
        // this means only write if the dt is sufficiently different than the previous dt
        OutputEventIterator o = out.outputIterator();
        for ( Object i:in ){
            TypedEvent e = (TypedEvent)i;
            int[] lasttimes = lastTimesMap[e.x][e.y][e.type]; // avoid indexing again and again
            int lastt = lasttimes[1];
            int lastdt = lastt - lasttimes[0];
            int thisdt = e.timestamp - lastt;
            int avgDt = avgDtMap[e.x][e.y][e.type];
            // refractory period

            boolean repetitious=false;
            if(thisdt<minDtToStore){
                continue;
            }
            if(excludeHarmonics){
                double ratio=(double)thisdt/avgDt;
                double rem=Math.IEEEremainder(ratio,1);
                repetitious=ratio<=3 && Math.abs(rem)<1./ratioShorter;
            }else{
            // if this dt is greater than last by threshold or less than last by threshold pass it
                repetitious = thisdt < avgDt * ratioLonger && thisdt > avgDt / ratioShorter; // true if event is repetitious
            }
            if ( !passRepetitiousEvents ){
                if ( !repetitious ){
                    o.nextOutput().copyFrom(e);
                }
            } else{ // pass boring events
                if ( repetitious ){
                    o.nextOutput().copyFrom(e);
                }
            }
            // update the map
            if ( thisdt < 0 ){
                lasttimes[0] = e.timestamp;
                lasttimes[1] = e.timestamp;
                avgDtMap[e.x][e.y][e.type] = 0;

            } else if ( thisdt > minDtToStore ){
                lasttimes[0] = lastt;
                lasttimes[1] = e.timestamp;

                avgDtMap[e.x][e.y][e.type] = (int)( avgDt * ( 1 - alpha ) + thisdt * ( alpha ) );
            }
        }
        return out;
    }

    public boolean getPassRepetitiousEvents (){
        return passRepetitiousEvents;
    }

    public void setPassRepetitiousEvents (boolean passRepetitiousEvents){
        this.passRepetitiousEvents = passRepetitiousEvents;
        getPrefs().putBoolean("RepetitiousFilter.passRepetitiousEvents",passRepetitiousEvents);
    }

    /**
     * @return the excludeHarmonics
     */
    public boolean isExcludeHarmonics (){
        return excludeHarmonics;
    }

    /**
     * @param excludeHarmonics the excludeHarmonics to set
     */
    public void setExcludeHarmonics (boolean excludeHarmonics){
        this.excludeHarmonics = excludeHarmonics;
    }
}
