/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.filter;
import java.util.Observable;
import java.util.Observer;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
/**
 *  Filters out events with ISI at specific intervals and their subharmonics.
 * E.g. if frequency is set to 100Hz (1/10ms), then filters out successive events with 10ms +- some percentage ISI, as well as those
 * with 20ms, 30ms, etc as selected by option.
 * Spikes that occur in bunches around an ISI have their times averaged to determine the starting time for the next ISI.
 * Spikes are regarded as being part of a bunch when the fall within the tolerance fraction of the ISI, e.g. 0.1 means all spikes that occur within
 * 10% of the specified interval (on either side) are filtered away.
 * Initialization of the filter starts with no per-pixel starting time determined.
 * 
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaerproject.net/">jaerproject.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
@Description("(Not yet working) Puts a notch at a chosen frequency.")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class NotchFilter extends EventFilter2D implements Observer{

    public static DevelopmentStatus getDevelopementStatus(){ return DevelopmentStatus.Alpha;}
    
    /** factor different than previous dt for this cell to pass through filter */
    private int ratioShorter = getPrefs().getInt("NotchFilter.ratioShorter",2);
    /** factor different than previous dt for this cell to pass through filter */
    private int ratioLonger = getPrefs().getInt("NotchFilter.ratioLonger",2);
    /** the minimum dt to record, to help reject multiple events from much slower stimulus variation (e.g. 50/100 Hz) */
    private int minDtToStore = getPrefs().getInt("NotchFilter.minDtToStore",1000);
    /** true to enable passing repetitious events  */
    private boolean passRepetitiousEvents = getPrefs().getBoolean("NotchFilter.passRepetitiousEvents",false);
    private boolean excludeHarmonics = false;
    /** Array of last event timestamps. */
    private int[][][][] lastTimesMap;
    /** Array of average ISIs: [chip.sizeX+][chip.sizeY+2][chip.numCellTypes] */
    private int[][][] avgDtMap;
    final int NUMTIMES = 2;
    /** the number of packets processed to average over */
    private int averagingSamples = getPrefs().getInt("NotchFilter.averagingSamples",3);
    private float tolerance=prefs().getFloat("NotchFilter.tolerance",.1f); // isi must be more than this off (+ or -) from notch interval or multiple to pass

    public NotchFilter (AEChip chip){
        super(chip);
         chip.addObserver(this);
        setPropertyTooltip("passRepetitiousEvents","Enabled to flip sense so that repetitious events pass through");
  }

    @Override
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

    @Override
    synchronized public void resetFilter (){
        initFilter();
    }

   public void update (Observable o,Object arg){
        initFilter();
    }
   
    @Override
    public void initFilter (){
        if ( !isFilterEnabled() ){
            return;
        }
        allocateMap();
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
            log.info("NotchFilter.allocateMaps");
            lastTimesMap = new int[ chip.getSizeX() + 2 ][ chip.getSizeY() + 2 ][ chip.getNumCellTypes() ][ NUMTIMES ];
            avgDtMap = new int[ chip.getSizeX() + 2 ][ chip.getSizeY() + 2 ][ chip.getNumCellTypes() ];
        }
    }

    public boolean getPassRepetitiousEvents (){
        return passRepetitiousEvents;
    }

    public void setPassRepetitiousEvents (boolean passRepetitiousEvents){
        this.passRepetitiousEvents = passRepetitiousEvents;
        getPrefs().putBoolean("NotchFilter.passRepetitiousEvents",passRepetitiousEvents);
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
