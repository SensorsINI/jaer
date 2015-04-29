/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.labyrinth;

import java.awt.geom.Point2D;
import java.util.Observable;
import java.util.Observer;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
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
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.eventprocessing.filter.XYTypeFilter;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker.Cluster;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import net.sf.jaer.util.filter.MedianLowpassFilter;
import ch.unizh.ini.jaer.projects.labyrinth.LabyrinthMap.PathPoint;
import ch.unizh.ini.jaer.projects.virtualslotcar.Histogram2DFilter;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import net.sf.jaer.eventprocessing.filter.RotateFilter;
import net.sf.jaer.graphics.AEFrameChipRenderer;

/**
 * Specialized tracker for ball location.
 *
 * @author tobi
 */
@Description("Ball tracker for labyrinth game")
public class LabyrinthBallTracker extends EventFilter2D implements FrameAnnotater, Observer, PropertyChangeListener {

    // filters and filter chain
    FilterChain filterChain;
    RectangularClusterTracker.Cluster ball = null;
    final RectangularClusterTracker tracker;
    LabyrinthMap map;
    // filtering
    protected int velocityMedianFilterNumSamples = getInt("velocityMedianFilterNumSamples", 3);
    // private fields, not properties
    BasicEvent startingEvent = new BasicEvent();
    int lastTimestamp = 0;
    private float[] compArray = new float[4];
    private LabyrinthBallController controller = null; // must check if null when used
    protected boolean addedPropertyChangeListener = false;  // must do lazy add of us as listener to chip because renderer is not there yet when this is constructed
    private float ballRadiusPixels = getFloat("ballRadiusPixels", 4);
    private float SUBFRAME_DIMENSION_PIXELS_MULTIPLE_OF_BALL_DIAMETER = 3;
    private StaticBallTracker staticBallTracker = null;

    public LabyrinthBallTracker(AEChip chip, LabyrinthBallController controller) {
        this(chip);
        this.controller = controller;
    }

    public LabyrinthBallTracker(AEChip chip) {
        super(chip);
        filterChain = new FilterChain(chip);
        map = new LabyrinthMap(chip);
        filterChain.add(map);
        filterChain.add(new RotateFilter(chip));
        filterChain.add(new XYTypeFilter(chip));
//        filterChain.add(new HotPixelFilter(chip));
//        filterChain.add(new LabyrinthDavisTrackFilter(chip));
        filterChain.add(new Histogram2DFilter(chip));
//        filterChain.add(new net.sf.jaer.eventprocessing.filter.DepressingSynapseFilter(chip));
        filterChain.add(new BackgroundActivityFilter(chip));
        //        filterChain.add(new CircularConvolutionFilter(chip));
        //        filterChain.add(new SubSamplingBandpassFilter(chip)); // TODO preferences should save enabled state of filters
        filterChain.add((tracker = new RectangularClusterTracker(chip)));
        tracker.addObserver(this);
        setEnclosedFilterChain(filterChain);
        String s = " Labyrinth Tracker";
        setPropertyTooltip("clusterSize", "size (starting) in fraction of chip max size");
        setPropertyTooltip("mixingFactor", "how much cluster is moved towards an event, as a fraction of the distance from the cluster to the event");
        setPropertyTooltip("velocityPoints", "the number of recent path points (one per packet of events) to use for velocity vector regression");
        setPropertyTooltip("velocityTauMs", "lowpass filter time constant in ms for velocity updates; effectively limits acceleration");
        setPropertyTooltip("frictionTauMs", "velocities decay towards zero with this time constant to mimic friction; set to NaN to disable friction");
        setPropertyTooltip("clearMap", "clears the map; use for bare table");
        setPropertyTooltip("loadMap", "loads a map from an SVG file");
        setPropertyTooltip("controlTilts", "shows a GUI to directly control table tilts with mouse");
        setPropertyTooltip("centerTilts", "centers the table tilts");
        setPropertyTooltip("disableServos", "disables the servo motors by turning off the PWM control signals; digital servos may not relax however becuase they remember the previous settings");
        setPropertyTooltip("maxNumClusters", "Sets the maximum potential number of clusters");
        setPropertyTooltip("velocityMedianFilterNumSamples", "number of velocity samples to median filter for ball velocity");
        setPropertyTooltip("ballRadiusPixels", "radius of ball in pixels used for locating ball in subframe static image");
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!addedPropertyChangeListener) {
            ((AEFrameChipRenderer) chip.getRenderer()).getSupport().addPropertyChangeListener(AEFrameChipRenderer.EVENT_NEW_FRAME_AVAILBLE, this);
            addedPropertyChangeListener = true;
        }
        out = getEnclosedFilterChain().filterPacket(in);
        if (tracker.getNumClusters() > 0) {
            // find most likely ball cluster from all the clusters. This is the one with most mass.
            float max = Float.MIN_VALUE;
            synchronized (tracker) {
                for (Cluster c : tracker.getClusters()) {
                    if (!c.isVisible()) {
                        continue;
                    }
                    Point2D.Float l = c.getLocation();
                    final int b = 5;
                    if ((l.x < -b) || (l.x > (chip.getSizeX() + b)) || (l.y < -b) || (l.y > (chip.getSizeY() + b))) {
                        continue;
                    }
                    float mass = c.getMass();
                    if (mass > max) {
                        max = mass;
                        ball = c;
                    }
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
        velxfilter.setInternalValue(0);
        velyfilter.setInternalValue(0);
        getEnclosedFilterChain().reset();
        //        createBall(startingLocation);
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
        GL2 gl = drawable.getGL().getGL2();
        if (glu == null) {
            glu = new GLU();
        }
        if (quad == null) {
            quad = glu.gluNewQuadric();
        }

        if (ball != null) {
            ball.getColor().getRGBComponents(compArray);
            gl.glColor4fv(compArray, 0);
            gl.glPushMatrix();
            gl.glTranslatef(ball.location.x, ball.location.y, 0);
            glu.gluQuadricDrawStyle(quad, GLU.GLU_LINE);
            gl.glLineWidth(2f);
            //            glu.gluDisk(quad, 0, ball.getAverageEventDistance(), 16, 1);
            float rad = ball.getMass() / tracker.getThresholdMassForVisibleCluster();
            if (rad > ball.getRadius()) {
                rad = ball.getRadius();
            }
            glu.gluDisk(quad, 0, rad, 16, 1);
            gl.glLineWidth(6f);
            gl.glBegin(GL.GL_LINE_STRIP);
            gl.glVertex2f(0, 0); // draw median-filtered velocity vector
            float f = tracker.getVelocityVectorScaling();
            gl.glVertex2f(velxfilter.getValue() * f, velyfilter.getValue() * f);
            gl.glEnd();
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

        if (staticBallTracker != null) {
            staticBallTracker.draw(gl);
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
    int velocityMedianFilterLengthSamples = getInt("velocityMedianFilterLengthSamples", 9);
    MedianLowpassFilter velxfilter = new MedianLowpassFilter(velocityMedianFilterLengthSamples);
    MedianLowpassFilter velyfilter = new MedianLowpassFilter(velocityMedianFilterLengthSamples);
    Point2D.Float ballVel = new Point2D.Float();

    public Point2D.Float getBallVelocity() {
        if (ball == null) {
            velxfilter.setInternalValue(0);
            velyfilter.setInternalValue(0);
            return null;
        }
        Point2D.Float vel = ball.getVelocityPPS();
        ballVel.setLocation(velxfilter.filter(vel.x), velyfilter.filter(vel.y));
        //        System.out.println(vel.x+"\t\t"+velxfilter.getValue());
        return ballVel;
    }

    @Override
    public void update(Observable o, Object arg) {
        if (arg instanceof UpdateMessage) {
            UpdateMessage m = (UpdateMessage) arg;
            callUpdateObservers(m.packet, m.timestamp); // pass on updates from tracker
        }
    }

    /**
     * Resets tracker and creates a ball at the specified location with large
     * initial mass.
     *
     * @param pf the starting location.
     */
    public void setBallLocation(Point2D.Float pf) {
        resetFilter();
        createBall(pf);
    }

    /**
     * returns the path index, or -1 if there is no ball or is too far away from
     * the path.
     *
     * @return
     */
    public int findNearestPathIndex() {
        return map.findNearestPathIndex(ball.location);
    }

    public int getNumPathVertices() {
        return map.getNumPathVertices();
    }

    public PathPoint findNearestPathPoint() {
        return map.findNearestPathPoint(ball.location);
    }

    public PathPoint findNextPathPoint() {
        return map.findNextPathPoint(ball.location);
    }

    PathPoint findNextPathPoint(PathPoint currenPathPoint) {
        return currenPathPoint.next();
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

    public void setMixingFactor(float mixingFactor) {
        tracker.setMixingFactor(mixingFactor);
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

    public boolean isAtMazeStart() {
        if (ball == null) {
            return false;
        }
        if (findNearestPathIndex() == 0) {
            return true;
        }
        return false;
    }

    public boolean isAtMazeEnd() {
        if (ball == null) {
            return false;
        }
        if (findNearestPathIndex() == (getNumPathVertices() - 1)) {
            return true;
        }
        return false;
    }

    public boolean isLostTracking() {
        return ball == null;
    }

    public boolean isPathNotFound() {
        if (ball == null) {
            return true;
        }
        if (findNearestPathIndex() == -1) {
            return true;
        }
        return false;
    }

    /**
     * Get the value of velocityMedianFilterNumSamples
     *
     * @return the value of velocityMedianFilterNumSamples
     */
    public int getVelocityMedianFilterNumSamples() {
        return velocityMedianFilterNumSamples;
    }

    /**
     * Set the value of velocityMedianFilterNumSamples
     *
     * @param velocityMedianFilterNumSamples new value of
     * velocityMedianFilterNumSamples
     */
    public void setVelocityMedianFilterNumSamples(int velocityMedianFilterNumSamples) {
        int old = this.velocityMedianFilterNumSamples;
        //        if(velocityMedianFilterNumSamples%2==0) velocityMedianFilterNumSamples++;
        this.velocityMedianFilterNumSamples = velocityMedianFilterNumSamples;
        velxfilter.setLength(velocityMedianFilterNumSamples);
        velyfilter.setLength(velocityMedianFilterNumSamples);
        putInt("velocityMedianFilterNumSamples", velocityMedianFilterNumSamples);
        getSupport().firePropertyChange("velocityMedianFilterNumSamples", old, velocityMedianFilterNumSamples);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName() == AEFrameChipRenderer.EVENT_NEW_FRAME_AVAILBLE) {
            if (staticBallTracker == null) {
                staticBallTracker = new StaticBallTracker();
            }
            staticBallTracker.locateBall();
        }
    }

    /**
     * @return the ballRadiusPixels
     */
    public float getBallRadiusPixels() {
        return ballRadiusPixels;
    }

    /**
     * @param ballRadiusPixels the ballRadiusPixels to set
     */
    public void setBallRadiusPixels(float ballRadiusPixels) {
        this.ballRadiusPixels = ballRadiusPixels;
        putFloat("ballRadiusPixels", ballRadiusPixels);
    }

    private class StaticBallTracker {

        private SubFrame subFrame = null;
        private final BallDetector ballDetector;

        private StaticBallTracker() {
            this.ballDetector = new BallDetector();
        }

        public void draw(GL2 gl) {
            if (subFrame != null) {
                subFrame.draw(gl);
            }
            ballDetector.draw(gl);
        }

        private void locateBall() {
            AEFrameChipRenderer renderer = (AEFrameChipRenderer) (chip.getRenderer());
            int dim = (int) (ballRadiusPixels * 2 * SUBFRAME_DIMENSION_PIXELS_MULTIPLE_OF_BALL_DIAMETER);
            if (subFrame == null || subFrame.dim != dim) {
                subFrame = new SubFrame(dim);
            }
            subFrame.fill(renderer);
            ballDetector.convolve(subFrame);

        }

        private class SubFrame {

            float[][] frame;
            int dim;
            int x0, x1, y0, y1;
            float sum;

            private SubFrame(int dim) {
                this.dim = dim;
                frame = new float[dim][dim];

            }

            private void fill(AEFrameChipRenderer renderer) {
                if (ball == null) {
                    return;
                }
                Point2D.Float bl = ball.getLocation();
                int sy = chip.getSizeY();
                int sx = chip.getSizeX();
                int cx = Math.round(bl.x);
                int cy = Math.round(bl.y);

                x0 = cx - dim / 2;
                if (x0 < 0) {
                    x0 = 0;
                } else if (x0 + dim > sx) {
                    x0 = sx - dim;
                }
                y0 = cy - dim / 2;
                if (y0 < 0) {
                    y0 = 0;
                } else if (y0 + dim > sy) {
                    y0 = sy - dim;
                }

                x1 = x0 + dim;
                y1 = y0 + dim;

                // extract ball subframe
                int xx = 0, yy = 0;
                sum = 0;
                for (int x = x0; x < x1; x++) {
                    yy = 0;
                    for (int y = y0; y < y1; y++) {  // take every xstride, ystride pixels as output
                        float v = 0;
                        v = renderer.getApsGrayValueAtPixel(x, y);
                        frame[xx][yy++] = v;
                        sum += v;
                    }
                    xx++;
                }
            }

            public String toString() {
                return String.format("SubFrame with %d x %d pixels", dim, dim);
            }

            public void draw(GL2 gl) {
                gl.glLineWidth(2);
                gl.glColor4f(0, 0, .3f, .1f);
                gl.glRectf(x0, y0, x0 + dim, y0 + dim);
            }
        }

        private class BallDetector {

            // basically a black spot of size of ball, with zero sum
            int kernelDim;
            float[][] output; // output of convolution, has dim subFrame.dim-ballRadius
            float[][] kernel; // size of ball, filled with zeros outside circle of ball
            int dim;
            float maxvalue;
            int maxx, maxy;

            void convolve(SubFrame subFrame) {
                // sums up pixel values in disk region and outputs the result
                updateKernel();
                if (output == null || dim != subFrame.dim - kernelDim) {
                    dim = subFrame.dim - kernelDim; // TODO check size
                    output = new float[dim][dim];
                }
                maxvalue = Float.NEGATIVE_INFINITY;
                for (int x = 0; x < dim; x++) {
                    for (int y = 0; y < dim; y++) {
                        float sum = 0;
                        for (int i = 0; i < kernelDim; i++) {
                            for (int j = 0; j < kernelDim; j++) {
                                sum += kernel[i][j] * subFrame.frame[x + i][y + j];
                            }
                        }
                        output[x][y] = subFrame.sum + sum;
                        if (sum > maxvalue) {
                            maxvalue = sum;
                            maxx = x + kernelDim / 2;
                            maxy = y + kernelDim / 2;
                        }
                    }
                }

            }

            void updateKernel() {
                if (kernel == null || (int) ballRadiusPixels != kernelDim) {
                    kernelDim = (int) ballRadiusPixels;
                    kernel = new float[kernelDim][kernelDim];
                    for (int x = 0; x < kernelDim; x++) {
                        for (int y = 0; y < kernelDim; y++) {
                            double dx = x - kernelDim / 2;
                            double dy = y - kernelDim / 2;
                            double r = Math.sqrt(dx * dx + dy * dy);
                            if (r <= kernelDim) {
                                kernel[x][y] = -1;
                            } else {
                                kernel[x][y] = 0;
                            }
                        }
                    }

                }

            }

            public void draw(GL2 gl) {
                if(maxvalue<=0) return;
                gl.glColor4f(1, 1, 1, .2f);
                float xc = subFrame.x0 + maxx, yc = subFrame.y0 + maxy;
                if (quad == null) {
                    quad = glu.gluNewQuadric();
                }

                gl.glPushMatrix();
                gl.glTranslatef(xc,yc, 0);
                glu.gluQuadricDrawStyle(quad, GLU.GLU_LINE);
                gl.glLineWidth(2f);
                glu.gluDisk(quad, 0, ballRadiusPixels, 16, 1);
                gl.glPopMatrix();

            }
        }
    }
}
