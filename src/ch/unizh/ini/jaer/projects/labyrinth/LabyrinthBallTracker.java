/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.labyrinth;

import java.awt.geom.Point2D;
import java.util.Observable;
import java.util.Observer;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.awt.GLCanvas;
import javax.media.opengl.glu.GLU;
import javax.media.opengl.glu.GLUquadric;

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

/**
 * Specialized tracker for ball location.
 *
 * @author tobi
 */
@Description("Ball tracker for labyrinth game")
public class LabyrinthBallTracker extends EventFilter2D implements FrameAnnotater, Observer {

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
	GLCanvas glCanvas = null;
	private ChipCanvas canvas;
	private float[] compArray = new float[4];
	private LabyrinthBallController controller=null; // must check if null when used

	public LabyrinthBallTracker(AEChip chip, LabyrinthBallController controller) {
		this(chip);
		this.controller = controller;
	}


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
		if ((chip.getCanvas() != null) && (chip.getCanvas().getCanvas() != null)) {
			glCanvas = (GLCanvas) chip.getCanvas().getCanvas();
		}
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
	}

	@Override
	public EventPacket<?> filterPacket(EventPacket<?> in) {
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
	 * @param velocityMedianFilterNumSamples new value of velocityMedianFilterNumSamples
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




}
