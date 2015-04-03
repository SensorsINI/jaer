/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.labyrinthkalman;

import java.awt.geom.Point2D;
import java.util.Observable;
import java.util.Observer;

import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.tracking.HoughCircleTracker;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.FrameAnnotater;
import ch.unizh.ini.jaer.projects.labyrinth.LabyrinthMap;
import ch.unizh.ini.jaer.projects.labyrinth.LabyrinthMap.PathPoint;

/**
 * Specialized tracker for ball location.
 *
 * @author tobi
 */
@Description("Ball tracker for labyrinth game")
public class LabyrinthBallTracker extends EventFilter2D implements FrameAnnotater, Observer {

    // filters and filter chain
    FilterChain filterChain;
    LabyrinthMap map;
    KalmanEventFilter kalmanFilter;

    // private fields, not properties
    BasicEvent startingEvent = new BasicEvent();
    int lastTimestamp = 0;
    GLCanvas glCanvas = null;
    private ChipCanvas canvas;
    private float[] compArray = new float[4];
    private LabyrinthBallController controller=null; // must check if null when used

    public LabyrinthBallTracker(AEChip chip, LabyrinthBallController controller) {
        super(chip);
        this.controller = controller;
        map = new LabyrinthMap(chip);
        kalmanFilter = new KalmanEventFilter(chip,controller);

        filterChain = new FilterChain(chip);
        filterChain.add(map);
        filterChain.add(new HoughCircleTracker(chip));
        filterChain.add(kalmanFilter);
       setEnclosedFilterChain(filterChain);
        String s = " Labyrinth Tracker";
        if (chip.getCanvas() != null && chip.getCanvas().getCanvas() != null) {
            glCanvas = (GLCanvas) chip.getCanvas().getCanvas();
        }

        setPropertyTooltip("clearMap", "clears the map; use for bare table");
        setPropertyTooltip("loadMap", "loads a map from an SVG file");
        setPropertyTooltip("controlTilts", "shows a GUI to directly control table tilts with mouse");
        setPropertyTooltip("centerTilts", "centers the table tilts");
        setPropertyTooltip("disableServos", "disables the servo motors by turning off the PWM control signals; digital servos may not relax however becuase they remember the previous settings");

    }

    
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {

        out = getEnclosedFilterChain().filterPacket(in);

        if (!in.isEmpty()) {
            lastTimestamp = in.getLastTimestamp();
        }

        return out;
    }

    @Override
    public void resetFilter() {
//            velxfilter.setInternalValue(0);
//            velyfilter.setInternalValue(0);
            getEnclosedFilterChain().reset();
//        createBall(startingLocation);
    }

    @Override
    public void initFilter() {
    }
    private GLU glu = new GLU();
    private GLUquadric quad = null;

    @Override
    public void annotate(GLAutoDrawable drawable) {
    }
    
    public boolean positionEstimateValid()
    {
    	return kalmanFilter.positionEstimateValid();
    }

    
//    int velocityMedianFilterLengthSamples = getInt("velocityMedianFilterLengthSamples", 1);
//    MedianLowpassFilter velxfilter = new MedianLowpassFilter(velocityMedianFilterLengthSamples);
//    MedianLowpassFilter velyfilter = new MedianLowpassFilter(velocityMedianFilterLengthSamples);
    Point2D.Float ballVel = new Point2D.Float();

    public Point2D.Float getBallPosition() {

        Point2D.Float vel = kalmanFilter.getBallPosition();
//        System.out.println(vel.x+"\t\t"+velxfilter.getValue());
        return vel;
    }

    public Point2D.Float getBallVelocity() {
        
        Point2D.Float vel = kalmanFilter.getBallVelocity();
//        ballVel.setLocation(velxfilter.filter(vel.x), velyfilter.filter(vel.y));
//        System.out.println(vel.x+"\t\t"+velxfilter.getValue());
        return vel;
    }

    @Override
    public void update(Observable o, Object arg) {
        if (arg instanceof UpdateMessage) {
            UpdateMessage m = (UpdateMessage) arg;
            callUpdateObservers(m.packet, m.timestamp); // pass on updates from tracker
        }
    }

    /** Resets tracker and creates a ball at the specified location with large initial mass.
     * 
     * @param pf the starting location. 
     */
    public void setBallLocation(Point2D.Float pf) {
        resetFilter();
    }

    /** returns the path index, or -1 if there is no ball or is too far away from the path.
     * 
     * @return index of nearest path point
     */
    public int findNearestPathIndex() {
        return map.findNearestPathIndex(kalmanFilter.getBallPosition());
    }

    public int getNumPathVertices() {
        return map.getNumPathVertices();
    }

    public PathPoint findNearestPathPoint() {
        return map.findNearestPathPoint(kalmanFilter.getBallPosition());
    }

    public PathPoint findNextPathPoint() {
        return map.findNextPathPoint(kalmanFilter.getBallPosition());
    }
    
    PathPoint findNextPathPoint(PathPoint currenPathPoint) {
        return currenPathPoint.next();
    }

    public void doLoadMap() {
        map.doLoadMap();
    }

    public synchronized void doClearMap() {
        map.doClearMap();
    }

    public boolean isAtMazeStart() {
        if (findNearestPathIndex() == 0) {
            return true;
        }
        return false;
    }

    public boolean isAtMazeEnd() {
        if (findNearestPathIndex() == getNumPathVertices() - 1) {
            return true;
        }
        return false;
    }

    public boolean isLostTracking() {
        // TODO: find that out...
        return false;
    }

    public boolean isPathNotFound() {
        if (findNearestPathIndex() == -1) {
            return true;
        }
        return false;
    }


	public void accelerationChanged( double dax, double day, int timestamp )
	{
		kalmanFilter.accelerationChanged( dax, day, timestamp );
	}
}
