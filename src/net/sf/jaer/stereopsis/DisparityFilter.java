/*
 * DisparityFilter.java
 *
 * Created on 28. Juni 2006, 09:05
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package net.sf.jaer.stereopsis;
import java.io.IOException;
import java.util.Arrays;
import java.util.Observable;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.orientation.BinocularDisparityEvent;
import net.sf.jaer.event.BinocularEvent;
import net.sf.jaer.event.orientation.BinocularOrientationEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.label.SimpleOrientationFilter;
import net.sf.jaer.graphics.FrameAnnotater;

import com.jogamp.opengl.util.gl2.GLUT;
/**
 * The filter calculates x-coordinate disparities for every single event, by averaging over disparities of previous events in the local
 * neighborhood. Therefore the disparites of single events should be spatially and temporally smooth.
 *
 * First, the algorithm calculates a weighted average of previous disparity values in the neighborhood (= oldMeanDisp). The weight
 * depends on the time difference between the actual and the previous events, s.t. recent events will have more weight. Depending
 * on the mean time difference from the previous events, the search range for the actual stereomatching will be restricted around
 * oldMeanDisp. The disparity of the actual event is then calculated by a linear interpolation between the best match in the restricted
 * search range and oldMeanDisp.
 *
 * Because looking at all points in a certain radius is too expansive for neighborhood computation, only a sparse set of surrounding
 * points will be evaluated. These points are stored in the file 'neighbors.dat'. New sets of points can be generated easyly
 * by using the matlab 'function neighborhoodCreator.m'.
 *
 * @author Peter Hess
 */
public class DisparityFilter extends EventFilter2D implements FrameAnnotater{
    GLUT glut;
    int meanSearchRange;
    private int maxDisp = getPrefs().getInt("DisparityFilter.maxDisp",40);
    /** When looking at the neighborhood, don't consider all surrounding pixels, because there are too many for efficient computing.
     * This matrix holds the offsets to a prototype set of pixels that should be considered.
     */
    private int[][] prototypeNeighbors;
    /** Enclosed orientation filter */
    private SimpleOrientationFilter oriFilter;
    // array dimensions are ordered this way because you have to iterate mainly over x coorinates
    /** Stores the timestamp of previous events at lastEvent[eye][y][x]. */
    private int[][][] lastTime;
    /** Stores the polarity of previous events at lastIsOn[eye][y][x]. */
    private boolean[][][] lastIsOn;
    /** Stores the orientation of previous events at lastOrientation[eye][y][x]. */
    private byte[][][] lastOrientation;
    /** Stores the previously calculated disparities of events at lastDisp[eye][y][x]. */
    private short[][][] lastDisp;
    private float distFactor = getPrefs().getFloat("DisparityFilter.distFactor",0.01f);
    private float rangeFactor = getPrefs().getFloat("DisparityFilter.rangeFactor",0.01f);
    private float smoothFactor = getPrefs().getFloat("DisparityFilter.smoothFactor",0.5f);

    /** Creates a new instance of GlobalXDisparityFilter3 */
    public DisparityFilter (AEChip chip){
        super(chip);
        oriFilter = new SimpleOrientationFilter(chip);
//        oriFilter.setFilterEnabled(true); // this will set orientation filtering active next time program is started, so don't set it now
        setEnclosedFilter(oriFilter);
        initFilter();
        setPropertyTooltip("distFactor","The larger this value, the more a time distance reduces matching");
        try{
            //load prototype neighbors from text file
            String p=getClass().getPackage().getName().replace(".","/");
            String path="src/"+p+"/neighbors.dat";
            prototypeNeighbors = MatrixLoader.loadInt(path);
        } catch ( IOException ex ){
            log.warning(ex.toString());
        }

        glut = chip.getCanvas().getGlut();
    }

    @Override
	public synchronized EventPacket filterPacket (EventPacket in){

        in = enclosedFilter.filterPacket(in);
        if ( !( in.getEventPrototype() instanceof BinocularOrientationEvent ) ){
            return in;
        }
        if ( in.isEmpty() ){
            return in;    // enclosed filter may change number of events from in packet
        }
        checkOutputPacketEventType(BinocularDisparityEvent.class);
        OutputEventIterator outIt = out.outputIterator();

        int sumRange = 0;
        int normRange = 0;

        // main loop
        for ( Object obj:in ){
            BinocularOrientationEvent e = (BinocularOrientationEvent)obj;

            if ( !e.hasOrientation ){
                continue;
            }
            // determine event type
            int x = e.x;
            int y = e.y;
            int time = e.timestamp;
            int eye = e.eye == BinocularEvent.Eye.LEFT ? 0 : 1;
            boolean isOn = e.polarity == PolarityEvent.Polarity.On;
            byte ori = e.orientation;

            // look at neighbourhood
            float sumDisp = 0f;
            float normDisp = 0f;
            int sumTime = 0;
            int normTime = 0;
            for (int[] prototypeNeighbor : prototypeNeighbors) {
                int nx = x + prototypeNeighbor[0];
                int ny = y + prototypeNeighbor[1];
                if ( (nx < 0) || (ny < 0) || (ny >= lastDisp[eye].length) || (nx >= lastDisp[eye][ny].length) ){
                    continue;
                }
                float w = distance(lastTime[eye][ny][nx],time);
                sumDisp += w * lastDisp[eye][ny][nx];
                normDisp += w;
                sumTime += lastTime[eye][ny][nx];
                normTime++;
            }

            int oldMeanDisp = (int)( sumDisp / normDisp );
            // this shouldn't happen, but it may be caused due to nummerical errors if the distance between lastTime[][][] and
            // time is too large
            if ( (oldMeanDisp < 0) || (oldMeanDisp > maxDisp) ){
                oldMeanDisp = 0;
            }
            int meanTime = sumTime / normTime;


            // find best match in a range around previous results
            int bestDt = Integer.MAX_VALUE;
            int bestDisp = oldMeanDisp;
            int r = range(meanTime,time);
            sumRange += r;
            normRange++;

            for ( int j = oldMeanDisp - r ; j <= (oldMeanDisp + r) ; j++ ){
                // check disparity range
                if ( (j < 0) || (j > maxDisp) ){
                    continue;
                }
                int nx = eye == 0 ? x + j : x - j;
                // check array bounds
                if ( (nx < 0) || (nx >= lastTime[eye][y].length) ){
                    continue;
                }
                // only match events of same polarity and orientation
                if ( lastIsOn[1 - eye][y][nx] != isOn ){
                    continue;
                }
                if ( lastOrientation[1 - eye][y][nx] != ori ){
                    continue;
                }

                int dt = time - lastTime[1 - eye][y][nx];
                if ( dt < bestDt ){
                    bestDt = dt;
                    bestDisp = j;
                }
            }

            // store event data
            lastTime[eye][y][x] = time;
            lastIsOn[eye][y][x] = isOn;
            lastOrientation[eye][y][x] = ori;
            lastDisp[eye][y][x] = (short)bestDisp;
            // try to symmetrize events from left and right eye, by storing the event at the other eye with the assumed disparity
            int nx = eye == 0 ? x + bestDisp : x - bestDisp;
            if ( (nx >= 0) && (nx < lastTime[eye][y].length) ){
                lastTime[1 - eye][y][nx] = time;
                lastIsOn[1 - eye][y][nx] = isOn;
                lastOrientation[1 - eye][y][nx] = ori;
                lastDisp[1 - eye][y][nx] = (short)bestDisp;
            }

            // linear interpolation between old and new disparity values
            bestDisp = (int)( (( 1f - smoothFactor ) * bestDisp) + (smoothFactor * oldMeanDisp) );

            // create output events
            BinocularDisparityEvent outE = (BinocularDisparityEvent)outIt.nextOutput();
            outE.copyFrom(e);
            outE.disparity = (byte)bestDisp;


        }
        if ( normRange > 0 ){
            meanSearchRange = sumRange / normRange;
        }
        return out;
    }

    @Override
	public void annotate (GLAutoDrawable drawable){
        if ( !isFilterEnabled() ){
            return;
        }
        GL2 gl = drawable.getGL().getGL2();
        if ( gl == null ){
            return;
        }

        gl.glColor3f(1f,1f,1f);
        gl.glRasterPos3f(0f,3f,0f);
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,"DisparityFilter search range: " + meanSearchRange);
    }

    /** Calculates correlation between two events depending on their temporal distance. The return value lies in [0,1] with
     * a maximum at 0 time difference.
     * Make sure that t1 <= t2 !!!
     */
    private final float distance (int t1,int t2){
        /* linear */
        return 1f / ( (distFactor * ( t2 - t1 )) + 1f );

        /* squared */
//        return 1f/(distFactor*distFactor*(t2 - t1)*(t2 - t1) + 1f);

        //too slow to be usefull
        /* gaussian-like: no normalization of the integral, instead the maximum will be 1 */
//        return (float)(Math.exp(-(t2-t1)*(t2-t1)/distFactor/distFactor));

        /* heaviside-like */
//        return t2 - t1 > distFactor ? 0f : 1f;
    }

    /** Calculates the allowed search range.
    The return value lies in [.2*maxDisp, maxDisp] with a minimum at 0 time difference.
     * Make sure that t1 <= t2 !!!
     */
    private final int range (int t1,int t2){
        /* linear */
//        return (int)(.8f*maxDisp*(1f - 1f/(rangeFactor*(t2 - t1) + 1f)) + .2f*maxDisp);
         /* squared */
        return (int)( (.8f * maxDisp * ( 1f - (1f / ( (rangeFactor * ( t2 - t1 ) * ( t2 - t1 )) + 1f )) )) + (.2f * maxDisp) );
    }

    @Override
	synchronized public void initFilter (){
        lastTime = new int[ 2 ][ chip.getSizeY() ][ chip.getSizeX() ];
        lastIsOn = new boolean[ 2 ][ chip.getSizeY() ][ chip.getSizeX() ];
        lastOrientation = new byte[ 2 ][ chip.getSizeY() ][ chip.getSizeX() ];
        lastDisp = new short[ 2 ][ chip.getSizeY() ][ chip.getSizeX() ];
        resetFilter();
    }

    @Override
	synchronized public void resetFilter (){
        for (int[][] element : lastTime) {
            for (int[] element2 : element) {
                Arrays.fill(element2,0);
            }
        }
        for (boolean[][] element : lastIsOn) {
            for (boolean[] element2 : element) {
                Arrays.fill(element2,true);
            }
        }
        for (byte[][] element : lastOrientation) {
            for (byte[] element2 : element) {
                Arrays.fill(element2,(byte)0);
            }
        }
        for (short[][] element : lastDisp) {
            for (short[] element2 : element) {
                Arrays.fill(element2,(short)0);
            }
        }
    }

    public Object getFilterState (){
        return null;
    }

    public boolean isGeneratingFilter (){
        return false;
    }

    /** Set the scaling factor for the event correlation depending on time difference. */
    public synchronized void setDistFactor (float distFactor){
        getPrefs().putFloat("DisparityFilter.distFactor",distFactor);
        getSupport().firePropertyChange("distFactor",this.distFactor,distFactor);
        this.distFactor = distFactor;
    }

    public float getDistFactor (){
        return distFactor;
    }

    public synchronized void setRangeFactor (float rf){
        getPrefs().putFloat("DisparityFilter.rangeFactor",rf);
        getSupport().firePropertyChange("rangeFactor",rangeFactor,rf);
        rangeFactor = rf;
    }

    public float getRangeFactor (){
        return rangeFactor;
    }

    /** This factor weights the influence of disparities from neigbouring events.
     * A higher value will result in more smoothing, but reaction to changing object distance will also be slower.
     */
    public synchronized void setSmoothFactor (float smoothFactor){
        if ( smoothFactor < 0f ){
            smoothFactor = 0f;
        }
        if ( smoothFactor > .95f ){
            smoothFactor = .95f;
        }
        getPrefs().putFloat("DisparityFilter.smoothFactor",smoothFactor);
        getSupport().firePropertyChange("smoothFactor",this.smoothFactor,smoothFactor);
        this.smoothFactor = smoothFactor;
    }

    public float getSmoothFactor (){
        return smoothFactor;
    }

    /** Maximal disparity which is considered for event matching. */
    public synchronized void setMaxDisp (int maxDisp){
        if ( maxDisp < 1 ){
            maxDisp = 1;
        }
        if ( maxDisp > chip.getSizeX() ){
            maxDisp = chip.getSizeX();
        }
        getPrefs().putInt("DisparityFilter.maxDisp",maxDisp);
        getSupport().firePropertyChange("maxDisp",this.maxDisp,maxDisp);
        this.maxDisp = maxDisp;
    }

    public int getMaxDisp (){
        return maxDisp;
    }

    public int getDisparity (){
        return 0;
    }
}
