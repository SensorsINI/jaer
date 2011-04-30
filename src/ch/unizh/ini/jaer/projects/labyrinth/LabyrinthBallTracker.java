/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.labyrinth;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCanvas;
import javax.media.opengl.glu.*;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.*;
import net.sf.jaer.eventprocessing.filter.*;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker.Cluster;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;

/**
 * Specialized tracker for ball location.
 *
 * @author tobi
 */
public class LabyrinthBallTracker extends EventFilter2D implements FrameAnnotater, Observer {

    public static String getDescription() {
        return "Ball tracker for labyrinth game";
    }
    // filters and filter chain
    FilterChain filterChain;
    RectangularClusterTracker.Cluster ball = null;
    RectangularClusterTracker tracker;
    LabyrinthMap map;
    // starting ball location on reset
    private Point2D.Float startingLocation = new Point2D.Float(getFloat("startingX", 50), getFloat("startingY", 100));
    // private fields, not properties
    BasicEvent startingEvent = new BasicEvent();
    int lastTimestamp = 0;
    GLCanvas glCanvas = null;
    private ChipCanvas canvas;

    public LabyrinthBallTracker(AEChip chip) {
        super(chip);
        filterChain = new FilterChain(chip);
        filterChain.add(new XYTypeFilter(chip));
        filterChain.add(new net.sf.jaer.eventprocessing.filter.DepressingSynapseFilter(chip));
        map = new LabyrinthMap(chip);
        filterChain.add(map);
        filterChain.add(new BackgroundActivityFilter(chip));
//        filterChain.add(new CircularConvolutionFilter(chip));
//        filterChain.add(new SubSamplingBandpassFilter(chip)); // TODO preferences should save enabled state of filters
        filterChain.add((tracker = new RectangularClusterTracker(chip)));
        tracker.addObserver(this);
        setEnclosedFilterChain(filterChain);
        String s = " Labyrinth Tracker";
        setPropertyTooltip(s, "startingLocation", "pixel location of starting location for tracker cluster");
        if (chip.getCanvas() != null && chip.getCanvas().getCanvas() != null) {
            glCanvas = (GLCanvas) chip.getCanvas().getCanvas();
        }
        setPropertyTooltip("clusterSize", "size (starting) in fraction of chip max size");
        setPropertyTooltip("mixingFactor", "how much cluster is moved towards an event, as a fraction of the distance from the cluster to the event");
        setPropertyTooltip("velocityPoints", "the number of recent path points (one per packet of events) to use for velocity vector regression");
        setPropertyTooltip("velocityTauMs", "lowpass filter time constant in ms for velocity updates; effectively limits acceleration");
        setPropertyTooltip("frictionTauMs", "velocities decay towards zero with this time constant to mimic friction; set to NaN to disable friction");
        setPropertyTooltip("clearMap", "clears the map; use for bare table");
        setPropertyTooltip("loadMap","loads a map from an SVG file");
        setPropertyTooltip("controlTilts", "shows a GUI to directly control table tilts with mouse");
        setPropertyTooltip("centerTilts","centers the table tilts");
        setPropertyTooltip("disableServos","disables the servo motors by turning off the PWM control signals; digital servos may not relax however becuase they remember the previous settings");
         setPropertyTooltip( "maxNumClusters", "Sets the maximum potential number of clusters");
   }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        out = getEnclosedFilterChain().filterPacket(in);
        if (tracker.getNumClusters() > 0) {
            // find most likely ball cluster from all the clusters. This is the one with most mass.
            float max = Float.MIN_VALUE;
            for (Cluster c : tracker.getClusters()) {
                if (!c.isVisible()) {
                    continue;
                }
                Point2D.Float l = c.getLocation();
                final int b = 5;
                if (l.x < -b || l.x > chip.getSizeX() + b || l.y < -b || l.y > chip.getSizeY() + b) {
                    continue;
                }
                float mass = c.getMass();
                if (mass > max) {
                    max = mass;
                    ball = c;
                }
            }
        } else {
            ball = null;
        }
        if (!in.isEmpty()) {
            lastTimestamp = in.getLastTimestamp();
        }
        return out;
    }

    @Override
    public void resetFilter() {
        createBall(startingLocation);
    }

    protected void createBall(Point2D.Float location) {
        getEnclosedFilterChain().reset();
        // TODO somehow need to spawn an initial cluster at the starting location
        Cluster b = tracker.createCluster(new BasicEvent(lastTimestamp, (short) location.x, (short) location.y, (byte) 0));
        b.setMass(10000); // some big number
        tracker.getClusters().add(b);
    }

    @Override
    public void initFilter() {
    }
    private GLU glu = new GLU();
    private GLUquadric quad = null;

    @Override
    public void annotate(GLAutoDrawable drawable) {
        GL gl = drawable.getGL();
        if (glu == null) {
            glu = new GLU();
        }
        if (quad == null) {
            quad = glu.gluNewQuadric();
        }

        // annotate starting ball location
        gl.glColor3f(0, 0, 1);
        gl.glLineWidth(3f);
        gl.glBegin(GL.GL_LINES);
        final int CROSS_SIZE = 3;
        gl.glVertex2f(startingLocation.x - CROSS_SIZE, startingLocation.y - CROSS_SIZE);
        gl.glVertex2f(startingLocation.x + CROSS_SIZE, startingLocation.y + CROSS_SIZE);
        gl.glVertex2f(startingLocation.x - CROSS_SIZE, startingLocation.y + CROSS_SIZE);
        gl.glVertex2f(startingLocation.x + CROSS_SIZE, startingLocation.y - CROSS_SIZE);
        gl.glEnd();

        if (ball != null) {
            gl.glColor4f(.25f, .25f, .25f, .3f);
            gl.glPushMatrix();
            gl.glTranslatef(ball.location.x, ball.location.y, 0);
            glu.gluQuadricDrawStyle(quad, GLU.GLU_LINE);
            glu.gluDisk(quad, 0, ball.getRadius(), 16, 1);
            gl.glPopMatrix();
            Point2D.Float p = findNearestPathPoint();
            if (p != null) {
                gl.glPushMatrix();
                gl.glTranslatef(p.x, p.y, 1);
                gl.glColor4f(.7f, .25f, 0f, .5f);
                glu.gluQuadricDrawStyle(quad, GLU.GLU_FILL);
                glu.gluDisk(quad, 0, 2, 8, 1);
                gl.glPopMatrix();
            }
        }

        MultilineAnnotationTextRenderer.renderMultilineString(String.format("Ball tracker:\npoint=%d", ball == null ? -1 : map.findClosestIndex(ball.location, 10, true)));

        chip.getCanvas().checkGLError(gl, glu, "after tracker annotations");
    }

    /**
     * @return the ball
     */
    public RectangularClusterTracker.Cluster getBall() {
        return ball;
    }

    /**
     * @return the startingLocation
     */
    public Point2D.Float getStartingLocation() {
        return startingLocation;
    }

    /**
     * @param startingLocation the startingLocation to set
     */
    public void setStartingLocation(Point2D.Float startingLocation) {
        this.startingLocation = startingLocation;
        putFloat("startingX", startingLocation.x);
        putFloat("startingY", startingLocation.y);
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
        createBall(pf);
    }

    /** returns the path index, or -1 if there is no ball or is too far away from the path.
     * 
     * @return 
     */
    public int findNearestPathIndex() {
        if (ball == null || map == null) {
            return -1;
        }
        return map.findClosestIndex(ball.location, 15, true);
    }
    
    public int getNumPathVertices(){
        if(map==null || map.getBallPath()==null) return 0;
        return map.getBallPath().size();
    }

    public Point2D.Float findNearestPathPoint() {
        if (map == null || ball == null) {
            return null;
        }
        int ind = findNearestPathIndex();
        if (ind == -1) {
            return null;
        }
        return map.getBallPath().get(ind);
    }

    public Point2D.Float findNextPathPoint() {
        int ind = findNearestPathIndex();
        if (ind == -1) {
            return null;
        }
        if (ind == map.getBallPath().size() - 1) {
            return map.getBallPath().get(ind);
        }
        return map.getBallPath().get(ind + 1);
    }

    public float getVelocityTauMs() {
        return tracker.getVelocityTauMs();
    }

    public int getNumClusters() {
        return tracker.getNumClusters();
    }

    public float getMixingFactor() {
        return tracker.getMixingFactor();
    }

    public float getMinMixingFactor() {
        return tracker.getMinMixingFactor();
    }

    public final int getMaxNumClusters() {
        return tracker.getMaxNumClusters();
    }

    public void setVelocityTauMs(float velocityTauMs) {
        tracker.setVelocityTauMs(velocityTauMs);
    }

    public void setMaxNumClusters(int maxNumClusters) {
        tracker.setMaxNumClusters(maxNumClusters);
    }

    public void setFrictionTauMs(float frictionTauMs) {
        tracker.setFrictionTauMs(frictionTauMs);
    }


    public int getMinMaxNumClusters() {
        return tracker.getMinMaxNumClusters();
    }

    public float getMinClusterSize() {
        return tracker.getMinClusterSize();
    }


    public float getMaxMixingFactor() {
        return tracker.getMaxMixingFactor();
    }

    public int getMaxMaxNumClusters() {
        return tracker.getMaxMaxNumClusters();
    }

    public float getMaxClusterSize() {
        return tracker.getMaxClusterSize();
    }

    public final float getClusterSize() {
        return tracker.getClusterSize();
    }

    public void setClusterSize(float clusterSize) {
        tracker.setClusterSize(clusterSize);
    }
    
    

    public void doLoadMap() {
        map.doLoadMap();
    }

    public synchronized void doClearMap() {
        map.doClearMap();
    }
    
    public boolean isAtMazeStart(){
        if(ball==null) return false;
        if(findNearestPathIndex()==0) {
            return true;
        }
        return false;
    }
    
    public boolean isAtMazeEnd(){
        if(ball==null) return false;
        if(findNearestPathIndex()==getNumPathVertices()-1) {
            return true;
        }
        return false;
    }
    
    public boolean isLostTracking(){
         return ball==null;
    }
    
    public boolean isPathNotFound(){
        if(ball==null) return true;
        if(findNearestPathIndex()==-1) return true;
        return false;
    }
    
 
}
