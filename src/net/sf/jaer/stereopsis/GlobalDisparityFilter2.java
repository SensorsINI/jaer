/*
 * GlobalXDisparityFilter2.java
 *
 * Created on 20. Juni 2006, 07:07
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.jaer.stereopsis;

import java.util.Arrays;
import java.util.Observable;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.orientation.BinocularDisparityEvent;
import net.sf.jaer.event.BinocularEvent;
import net.sf.jaer.event.orientation.BinocularOrientationEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.label.SimpleOrientationFilter;
import net.sf.jaer.util.filter.LowpassFilter;

/**
 * (GlobalDisparityFilter2 is an optimized version of GlobalDisparityFilter without
 * the additional viewer.) The filter assumes a single frontal object and calculates the global x-coordinate disparity. The global disparity is found by correlating
 * each event in a packet with its neighbors. The disparity with highest sum of correlations is then assigned to all events in the packet.
 * Events are only matched if they have same polarity and orientation. 
 * @author Peter Hess
 */
public class GlobalDisparityFilter2 extends EventFilter2D  {
    
    /** Global x direction disparity computed by method filter().
     * Positive disparity means that the object is far away (not sure if there are any conventions).
     */
    private int disparity;
    
    private int maxDisp = getPrefs().getInt("GlobalXDisparityFilter2.maxDisp", 40);
        
    /** Used for lowpassfiltering the resulting disparity from filter(). */
    private LowpassFilter lpFilter = new LowpassFilter();
    
    /** enclosed filter */
    private SimpleOrientationFilter oriFilter;
    
    // array dimensions are ordered this way because you have to iterate mainly over x coorinates
    /** Stores the timestamp of previous events at lastEvent[LEFT/RIGHT][y][x]. */
    private int lastTime[][][];
    /** Stores the polarity of previous events at lastIsOn[LEFT/RIGHT][y][x]. */
    private boolean lastIsOn[][][];
    /** Stores the orientation of previous events at lastOrientation[LEFT/RIGHT][y][x]. */
    private byte lastOrientation[][][];
    
    /** Accumulates the weights for every possible disparity value. The best disparity is determined by WTA. */
    private float[] dispWeights = new float[2*maxDisp + 1];
        
    private float distFactor = getPrefs().getFloat("GlobalXDisparityFilter2.distFactor", 0.01f);
    
    private int yRes = getPrefs().getInt("GlobalXDisparityFilter2.yRes", 8);
    
    /** Creates a new instance of GlobalXDisparityFilter2 */
    public GlobalDisparityFilter2(AEChip chip) {
        super(chip);
        oriFilter = new SimpleOrientationFilter(chip);
        oriFilter.setFilterEnabled(true);
        setEnclosedFilter(oriFilter);
        initFilter();
    }
    
    /** Calculates the temporal distance between two timestamps. The return value lies in [0,1] and a time difference of 0 will
     * return 1.
     * Make sure that t1 <= t2 !!!
     */
    private final float distance(int t1, int t2) {
        /* linear */
        return 1f/(distFactor*(t2 - t1) + 1f);   
   
        /* squared */
//        return 1f/(distFactor*distFactor*(t2 - t1)*(t2 - t1) + 1f);
        
        //too slow to be usefull
        /* gaussian-like: no normalization of the integral, instead the maximum will be 1 */
//        return (float)(Math.exp(-(t2-t1)*(t2-t1)/distFactor/distFactor));
        
        /* heaviside-like */
//        return t2 - t1 > distFactor ? 0f : 1f;
    }
    
    public void initFilter() {        
        lastTime = new int[2][(chip.getSizeY()/yRes)+1][chip.getSizeX()];
        lastIsOn = new boolean[2][(chip.getSizeY()/yRes)+1][chip.getSizeX()];
        lastOrientation = new byte[2][(chip.getSizeY()/yRes)+1][chip.getSizeX()];
        resetFilter();      
    }
    
    synchronized public void resetFilter() {
        for (int i = 0; i < lastTime.length; i++) {
            for (int j = 0; j < lastTime[i].length; j++) {
                Arrays.fill(lastTime[i][j], 0);
            }
        }
        for (int i = 0; i < lastIsOn.length; i++) {
            for (int j = 0; j < lastIsOn[i].length; j++) {
                Arrays.fill(lastIsOn[i][j], true);
            }
        }
        for (int i = 0; i < lastOrientation.length; i++) {
            for (int j = 0; j < lastOrientation[i].length; j++) {
                Arrays.fill(lastOrientation[i][j], (byte)0);
            }
        }
    }
    
    public Object getFilterState() {
        return null;
    }
    
    public boolean isGeneratingFilter() {
        return false;
    }
        
    /** Set the scaling factor for the event correlation depending on time difference. */
    public synchronized void setDistFactor(float distFactor) {
        getPrefs().putFloat("GlobalXDisparityFilter2.distFactor", distFactor);
        getSupport().firePropertyChange("distFactor", this.distFactor, distFactor);
        this.distFactor = distFactor;
    }
    
    public float getDistFactor() {
        return distFactor;
    }
    
    /** yRes determines the pixel resolution in y direction, i.e. all y coordinates are divides by this value, which
     * means that every event matches to his yRes nearest neighbours in y direction.
     * Smaller values will give you a higher resolution in y direction, but eventually the algorithm won't be able to find any
     * matches because the tolerance in y direction is too small.
     */
    public synchronized void setYRes(int yRes) {
        if (yRes < 1) yRes = 1;
        getPrefs().putInt("GlobalXDisparityFilter2.yRes", yRes);
        getSupport().firePropertyChange("yRes", this.yRes, yRes);
        this.yRes = yRes;
        
        lastTime = new int[2][(chip.getSizeY()/yRes)+1][chip.getSizeX()];
        lastIsOn = new boolean[2][(chip.getSizeY()/yRes)+1][chip.getSizeX()];
        lastOrientation = new byte[2][(chip.getSizeY()/yRes)+1][chip.getSizeX()];
    }
    
    public int getYRes() {
        return yRes;
    }
    
    /** Maximal disparity which is considered for event matching. */
    public synchronized void setMaxDisp(int maxDisp) {
        if (maxDisp < 1) maxDisp = 1;
        getPrefs().putInt("GlobalXDisparityFilter2.maxDisp", maxDisp);
        getSupport().firePropertyChange("maxDisp", this.maxDisp, maxDisp);
        this.maxDisp = maxDisp;
        
        dispWeights = new float[2*maxDisp + 1];
    }
    
    public int getMaxDisp() {
        return maxDisp;
    }
    
    public synchronized EventPacket filterPacket(EventPacket in) {
        if (in == null) return null;
        if (!filterEnabled) return in;
        if (enclosedFilter != null) in = enclosedFilter.filterPacket(in);
        if(!(in.getEventPrototype() instanceof BinocularOrientationEvent)) return in;
        if (in.isEmpty()) return in;    // enclosed filter may change number of events from in packet
        checkOutputPacketEventType(BinocularDisparityEvent.class);
        OutputEventIterator outIt = out.outputIterator();
        
        // resets
        Arrays.fill(dispWeights, 0);

        // main loop
        for(Object obj:in){
            BinocularOrientationEvent e=(BinocularOrientationEvent)obj;

            // determine event type
            int x = e.x;
            int y = e.y/yRes;            
            int time = e.timestamp;
            int eye = e.eye == BinocularEvent.Eye.LEFT ? 0 : 1;
            boolean isOn = e.polarity == PolarityEvent.Polarity.On;
            byte ori = e.orientation;
            
            // store event timestamp
            lastTime[eye][y][x] = time;
            lastIsOn[eye][y][x] = isOn;
            lastOrientation[eye][y][x] = ori;
            
            // try to match event
            for (int j = -maxDisp; j <= maxDisp; j++) {
                int nx = x + j;
                // check array bounds
                if (nx < 0 || nx >= lastTime[eye][y].length) continue;
                // only match events of same polarity
                if (lastIsOn[1 - eye][y][nx] != isOn) continue;
                // only match events of same orientation
                if (lastOrientation[1 - eye][y][nx] != ori) continue;
                
                float w = distance(lastTime[1 - eye][y][nx], time);
                if (eye == 0) {
                    dispWeights[maxDisp + j] += w;
                } else {
                    dispWeights[maxDisp - j] += w;
                }
            }
        }
        
        // WTA approach
        float bestWeight = Float.MIN_VALUE;
        int bestDisp = 0;
        for (int i = 0; i < dispWeights.length; i++) {
            if (dispWeights[i] > bestWeight) {
                bestWeight = dispWeights[i];
                bestDisp = i - maxDisp;
            }
        }

        // lowpass filter output
        disparity = (int)lpFilter.filter(bestDisp, in.getLastEvent().timestamp);
        
        for(Object obj:in){
            BinocularOrientationEvent e=(BinocularOrientationEvent)obj;
            BinocularDisparityEvent outE = (BinocularDisparityEvent)outIt.nextOutput();
            outE.copyFrom(e);
            outE.disparity = (byte)disparity;
        }
        
        return out;
    }
}