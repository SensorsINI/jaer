/*
 * GlobalXDisparityFilter2.java
 *
 * Created on 27. Juni 2006, 22:10
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
 * The filter assumes a single frontal object and calculates the global x-coordinate disparity. The global disparity is found by correlating
 * each event in a packet with its neighbors. The disparity with highest sum of correlations is then assigned to all events in the packet.
 * Events are only matched if they have same polarity and orientation. An additional viewer window shows the matching matrix.
 * @author Peter Hess
 */
public class GlobalDisparityFilter extends EventFilter2D {
    
    /* Global x direction disparity computed by method filter().
     * Positive disparity means that the object is far away (not sure if there are any conventions).
     */
    private int disparity;
    
    private int maxDisp = getPrefs().getInt("GlobalDisparityFilter.maxDisp", 40);
        
    /* Used for lowpassfiltering the resulting disparity from filter(). */
    private LowpassFilter lpFilter = new LowpassFilter();
    
    /* Stores the timestamp of previous events at lastEvent[eye][y][x]. */
    private int lastTime[][][];
    /* Stores the polarity of previous events at lastIsOn[eye][y][x]. */
    private boolean lastIsOn[][][];
    /* Stores the orientation of previous events at lastOrientation[eye][y][x]. */
    private byte lastOrientation[][][];
    
    private float distFactor = getPrefs().getFloat("GlobalDisparityFilter.distFactor", 0.01f);
    
    private int yRes = getPrefs().getInt("GlobalDisparityFilter.yRes", 4);
    
    /* Viewer which shows a 2D projection of the stereo matching cube and the summed weights of all possible disparities */
    StereoMatchingFrame smf = null;
    /* 2D projection of the matching cube. Cells with same y coordinates are accumulated */
    private float M2D[][];
    private float[] dispWeights;
    private boolean showMatchingMatrix = getPrefs().getBoolean("GlobalDisparityFilter.showMatchingMatrix", false);
        
    /** Creates a new instance of GlobalXDisparityFilter */
    public GlobalDisparityFilter(AEChip chip) {
        super(chip);
        SimpleOrientationFilter oriFilter = new SimpleOrientationFilter(chip);
        oriFilter.setFilterEnabled(true);
        setEnclosedFilter(oriFilter);
        
        setShowMatchingFrame(false);

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
        lastTime = new int[chip.getSizeX()][chip.getSizeY()/yRes+1][2];
        lastIsOn = new boolean[chip.getSizeX()][chip.getSizeY()/yRes+1][2];
        lastOrientation = new byte[chip.getSizeX()][chip.getSizeY()/yRes+1][2];
        M2D = new float[chip.getSizeX()][chip.getSizeX()];
        dispWeights = new float[2*maxDisp + 1];
        
        resetFilter();
    }
    
    synchronized public void resetFilter() {
        for (int i = 0; i < lastTime.length; i++) {
            for (int j = 0; j < lastTime[i].length; j++) {
                lastTime[i][j][0] = 0;
                lastTime[i][j][1] = 0;
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
    public void setDistFactor(float distFactor) {
        getPrefs().putFloat("GlobalDisparityFilter.distFactor", distFactor);
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
    public synchronized  void setYRes(int yRes) {
        if (yRes < 1) yRes = 1;
        getPrefs().putInt("GlobalDisparityFilter.yRes", yRes);
        getSupport().firePropertyChange("yRes", this.yRes, yRes);
        this.yRes = yRes;
        
        lastTime = new int[chip.getSizeX()][chip.getSizeY()/yRes+1][2];
        lastIsOn = new boolean[chip.getSizeX()][chip.getSizeY()/yRes+1][2];
        lastOrientation = new byte[chip.getSizeX()][chip.getSizeY()/yRes+1][2];
    }
    
    public int getYRes() {
        return yRes;
    }
    
    /** Maximal disparity which is considered for event matching. */
    public synchronized void setMaxDisp(int maxDisp) {
        if (maxDisp < 1) maxDisp = 1;
        getPrefs().putInt("GlobalDisparityFilter.maxDisp", maxDisp);
        getSupport().firePropertyChange("maxDisp", this.maxDisp, maxDisp);
        this.maxDisp = maxDisp;
        
        dispWeights = new float[2*maxDisp + 1];
        smf.dispose();
        smf = new StereoMatchingFrame(chip, maxDisp);
        smf.setVisible(filterEnabled && showMatchingMatrix);
    }
    
    public int getMaxDisp() {
        return maxDisp;
    }
    
    /** Show the additional viewer window with the matching matrix visalization. */ 
    public void setShowMatchingFrame(boolean show) {
        getPrefs().putBoolean("GlobalDisparityFilter.showMatchingMatrix", show);
        getSupport().firePropertyChange("showMatchingMatrix", showMatchingMatrix, show);
        showMatchingMatrix = show;
        
        // create smf if not done yet
        if (smf == null) smf = new StereoMatchingFrame(chip, maxDisp);
        smf.setVisible(filterEnabled && showMatchingMatrix);
    }
    
    public boolean isShowMatchingFrame() {
        return showMatchingMatrix;
    }
    
    public synchronized EventPacket filterPacket(EventPacket in) {
        if (in == null) return null;
        if (!filterEnabled) return in;
        if (enclosedFilter != null) in = enclosedFilter.filterPacket(in);
        if(!(in.getEventPrototype() instanceof BinocularEvent)) return in;
        if (in.isEmpty()) return in;
        checkOutputPacketEventType(BinocularDisparityEvent.class);
        OutputEventIterator outIt = out.outputIterator();
        
        // reset
        for (int i = 0; i < M2D.length; i++) {
            Arrays.fill(M2D[i], 0);
        }
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
            lastTime[x][y][eye] = time;
            lastIsOn[x][y][eye] = isOn;
            lastOrientation[x][y][eye] = ori;

            // try to match event
            for (int j = -maxDisp; j <= maxDisp; j++) {
                int nx = e.x + j;
                // check array bounds
                if (nx < 0 || nx >= lastTime.length) continue;
                // only match events of same polarity
                if (lastIsOn[nx][y][1 - eye] != isOn) continue;
                // only match events of same orientation
                if (lastOrientation[nx][y][1 - eye] != ori) continue;
                
                float w = distance(lastTime[nx][y][1 - eye], time);
                if (eye == 0) {
                    M2D[nx][x] += w;
                    dispWeights[maxDisp + j] += w;
                } else {
                    M2D[x][nx] += w;
                    dispWeights[maxDisp - j] += w;
                }
            }
        }

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
          
        if (smf != null && smf.isVisible()) {
            smf.visualize(disparity, M2D, dispWeights);
        }
        
        for(Object obj:in){
            BinocularOrientationEvent e=(BinocularOrientationEvent)obj;
            BinocularDisparityEvent outE = (BinocularDisparityEvent)outIt.nextOutput();
            outE.copyFrom(e);
            outE.disparity = (byte)disparity;
        }
        
        return out;
    }
}