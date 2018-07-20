/* RetinaBackgrondActivityFilter.java
 *
 * Created on October 21, 2005, 12:33 PM */
package net.sf.jaer.eventprocessing.filter;

import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip2D;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;


import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import java.util.*;




/**
 * An filter that filters slow background activity by only passing
 * events that are supported by another event in the past {@link #setDt dt} in
 * the immediate spatial neighborhood, defined by a subsampling bit shift.
 *
 * @author tobi
 */
@Description("Filters out uncorrelated background activity noise")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class FoosballFilter extends EventFilter2D implements Observer {

    final int MAX_DT = 100000, MIN_DT = 10;
    final int DEFAULT_TIMESTAMP = Integer.MIN_VALUE;


    /**
     * the amount to subsample x and y event location by in bit shifts when
     * writing to past event times map. This effectively increases the range of
     * support. E.g. setting subSamplingShift to 1 quadruples range because both
     * x and y are shifted right by one bit
     */
    private int subsampleBy = getInt("subsampleBy", 0);

    private int ts = 0; // used to reset filter
    private int sx;
    private int sy;
    // gap between player for each bar : DEF ATT MID MID ATT DEF
    int[] arrGap = {63, 44, 26, 26, 42, 62};
    
    // position of each bar
    int[] arrPos = {40, 72, 105, 138, 172, 201};
    
    // Inibation constant (in us)
    int inibCst = 2000; 
    
    // If not equal to 0, then the pixel is inibited
    int[][] inibMap;
    
    // Half size of the inibiation area (inibits [-delta + delta]
    int deltaInib = 2;
    
    // last timestamp update 
    int lastTs =0;

    public FoosballFilter(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        initFilter();
        setPropertyTooltip("inbtime", "each time a pixel spike it inibates its neighbours during inibtime (us)");
        setPropertyTooltip("delta", "size of the inibition map (px)");
        setPropertyTooltip("gapAtt", "gap between attaquants");
        setPropertyTooltip("gapDef", "gap between defenders");
        setPropertyTooltip("gapMid", "gap between mid players");
        
    }
    
        /*
        Finds the closest bar 
    */   
    int getBarNumber(int x){
        int ind =-1;
        int returndDist = 999999;
        int curDist;
        for(int j=0; j < 6; j++){
            curDist = Math.abs(x-arrPos[j]);
            if( curDist < returndDist){
                returndDist = curDist;
                ind = j ;
            }
        }
        return ind;
    }
   
    /*
        Set inibition Map : if an event arrive in position (x, y), it inibates neighbours
        according to players positions
    */
    void setInibMap(int x, int y, int ts){
        int indBar = getBarNumber(x);
        int yP = y + arrGap[indBar];
        int yM = y - arrGap[indBar];
        int i,j;
        if(yP < sy - deltaInib - 1){
            for(i = -deltaInib; i < deltaInib ; i++ )
                for(j = -deltaInib; j < deltaInib ; j++ )
                    inibMap[ x + j][yP + i] = ts;
            
        }
        
        if(yM > deltaInib){
            for(i = -deltaInib; i < deltaInib ; i++ )
                for(j = -deltaInib; j < deltaInib ; j++ )
                    inibMap[ x + j][yM+ i] = ts;
        }
    }

    /* 
        Check inibiation Map
    */
    int testInibMap(int x, int y, int ts){
        if(ts - inibMap[x][y] < inibCst)
            return 1;
        else
            return 0;
    }

    /**
     * filters in to out. if filtering is enabled, the number of out may be less
     * than the number putString in
     *
     * @param in input events can be null or empty.
     * @return the processed events, may be fewer in number. filtering may occur
     * in place in the in packet.
     */
    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        if (inibMap == null) {
            allocateMaps(chip);
        }
        //checkOutputPacketEventType(in);
        //OutputEventIterator outItr=out.outputIterator();
        
        // for each event only keep it if it is within dt of the last time
        // an event happened in the direct neighborhood
        for (Object eIn : in) {
            if (eIn == null) {
                break;  // this can occur if we are supplied packet that has data (eIn.g. APS samples) but no events
            }
            BasicEvent e = (BasicEvent) eIn;
            if (e.isSpecial()) {
                continue;
            }
            
            short x = (short) (e.x >>> subsampleBy), y = (short) (e.y >>> subsampleBy);
            
            if ((x < deltaInib) || (x > sx-deltaInib-1) || (y < deltaInib) || (y > sy-deltaInib-1)) {
                continue;
            }

            ts = e.timestamp;
            // Inibate
            setInibMap(x,y,ts);
            
            // Test if pixel is authorized to spike        
            if (testInibMap(x, y, ts)==0) {
                e.setFilteredOut(false);
                //BasicEvent o=(BasicEvent)outItr.nextOutput();  // make an output event
                //o.copyFrom(e); // copy the BasicEvent fields from the input event
            }
            else{
                e.setFilteredOut(true);
            }
            
                
            

        }

        return in;
    }

    @Override
    public synchronized final void resetFilter() {
        initFilter();
    }

    @Override
    public void update(Observable o, Object arg) {
        if ((arg != null) && ((arg == Chip2D.EVENT_SIZEX) || (arg == Chip2D.EVENT_SIZEY))) {
            resetFilter();
        }
    }

    @Override
    public final void initFilter() {
        allocateMaps(chip);
        sx = chip.getSizeX() - 1;
        sy = chip.getSizeY() - 1;
    }

    private void allocateMaps(AEChip chip) {
        if ((chip != null) && (chip.getNumCells() > 0)) {
            inibMap = new int[chip.getSizeX()][chip.getSizeY()];
            for (int[] arrayRow : inibMap) {
                Arrays.fill(arrayRow, 0);
            }
        }
    }

    // <editor-fold defaultstate="collapsed" desc="getter-setter / Min-Max for --Dt--">

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter-setter for --SubsampleBy--">
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
     */
    public void setSubsampleBy(int subsampleBy) {
        if (subsampleBy < 0) {
            subsampleBy = 0;
        } else if (subsampleBy > 4) {
            subsampleBy = 4;
        }
        this.subsampleBy = subsampleBy;
        putInt("subsampleBy", subsampleBy);
    }
    // </editor-fold>



}