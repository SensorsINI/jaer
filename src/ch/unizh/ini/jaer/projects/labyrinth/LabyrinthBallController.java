/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.labyrinth;

import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.LinkedList;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;

import net.sf.jaer.Description;
import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker.Cluster;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import ch.unizh.ini.jaer.projects.labyrinth.LabyrinthMap.PathPoint;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.util.TobiLogger;

/**
 * This filter enables controlling the tracked labyrinth ball.
 *
 * @author Tobi Delbruck
 */
@Description("Low level ball controller for Labyrinth game")
public class LabyrinthBallController extends EventFilter2DMouseAdaptor implements PropertyChangeListener, LabyrinthBallControllerInterface {

    private int jiggleTimeMs = getInt("jiggleTimeMs", 1000);

    volatile private Point2D.Float tiltsRad = new Point2D.Float(0, 0);
    // control
    private float proportionalGain = getFloat("proportionalGain", 1);
    private float derivativeGain = getFloat("derivativeGain", 1);
    private float integralGain = getFloat("integralGain", 1);
    // controller delay
    private float controllerDelayMs = getFloat("controllerDelayMs", 0);
    // error signals
    // errors
    Point2D.Float pControl = new Point2D.Float(0, 0);
    Point2D.Float iControl = new Point2D.Float(0, 0);
    Point2D.Float dControl = new Point2D.Float(0, 0);
    int lastErrorUpdateTime = 0;
    boolean controllerInitialized = false;
    // components of the controller output
    //    Point2D.Float pTilt = new Point2D.Float();
    //    Point2D.Float iTilt = new Point2D.Float();
    //    Point2D.Float dTilt = new Point2D.Float();
    // The ball acceleration will be g (grav constant) * sin(angle) which is approx g*angle with angle in radians =g*angleRadPerServoUnit*(servo-0.5f).
    final float metersPerPixel = 0.22f / 128;  // TODO estimated pixels of 128 retina per meter, assumes table fills retina vertically
    final float gravConstantMperS2 = 9.8f; // meters per second^2
    final float gravConstantPixPerSec2 = gravConstantMperS2 / metersPerPixel; // g in pixel units
    // fields
    LabyrinthHardware labyrinthHardware;
    LabyrinthBallTracker tracker = null;
//    HandDetector handDetector = null;
    // filter chain
    FilterChain filterChain;
    // state stuff
    Point2D.Float mousePosition = null, target = null; // manually selected ball position and actual target location (maybe from path following)
    // history
    Trajectory trajectory = new Trajectory();
    private LabyrinthTableTiltControllerGUI gui;
    //    boolean navigateMaze = getBoolean("navigateMaze", true);
    private boolean controllerEnabled = getBoolean("controllerEnabled", true);
    protected boolean integralControlUsesPropDerivErrors = getBoolean("integralControlUsesPropDerivErrors", true);
    // path navigation
    PathNavigator nav = new PathNavigator();
    private float dwellTimePathPointMs = getFloat("dwellTimePathPointMs", 100);
    private int timeToTriggerJiggleAfterBallLostMs = getInt("timeToTriggerJiggleAfterBallLostMs", 3000);
    private Thread jiggleThread = null;

    private TobiLogger tobiLogger = null;

    /**
     * Constructs instance of the new 'filter' CalibratedPanTilt. The only time
     * events are actually used is during calibration. The PanTilt hardware
     * interface is also constructed.
     *
     * @param chip
     */
    public LabyrinthBallController(AEChip chip) {
        super(chip);

//        handDetector = new HandDetector(chip);
        tracker = new LabyrinthBallTracker(chip, this);
        labyrinthHardware = new LabyrinthHardware(chip);
        labyrinthHardware.getSupport().addPropertyChangeListener(this);

        filterChain = new FilterChain(chip);

        filterChain.add(labyrinthHardware);
//        filterChain.appendCopy(handDetector);
        filterChain.add(tracker);

        setEnclosedFilterChain(filterChain);

        //        String control = "Control";
        setPropertyTooltip("disableServos", "disables servos, e.g. to turn off annoying servo hum");
        setPropertyTooltip("center", "centers pan and tilt controls");
        setPropertyTooltip("integralGain", "integral error gain: tilt(rad)=error(pixels)*integralGain(1/(pixels*sec))");
        setPropertyTooltip("proportionalGain", "proportional error gain: tilt(rad)=error(pixels)*proportionalGain(1/pixels)");
        setPropertyTooltip("derivativeGain", "-derivative gain: damps overshoot. tilt(rad)=-vel(pix/sec)*derivativeGain(sec/pixel)");
        //        setPropertyTooltip("navigateMaze", "follow the path in the maze");
        setPropertyTooltip("tiltLimitRad", "limit of tilt of table in radians; 1 rad=57 deg");
        setPropertyTooltip("jiggleTable", "jiggle the table for jiggleTimeMs ms according to the jitter settings for the LabyrinthHardware");
        setPropertyTooltip("jiggleTimeMs", "jiggle the table for jiggleTimeMs ms according to the jitter settings for the LabyrinthHardware");
        setPropertyTooltip("controllerEnabled", "enables the controller to control table tilts based on error signal between target and ball position");
        setPropertyTooltip("integralControlUsesPropDerivErrors", "the integral error integrates both position and velocity terms, not just position error");
        setPropertyTooltip("controllerDelayMs", "controller delay in ms; control is computed on position this many ms ahead of current position");
        setPropertyTooltip("dwellTimePathPointMs", "time that ball should dwell at a path point before aiming for next one");
        setPropertyTooltip("timeToTriggerJiggleAfterBallLostMs", "time to wait after ball is lost to trigger a jiggle");
        computePoles();
    }

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        if (isControllerEnabled()) {
            control(in, timeUs()); //in.getLastTimestamp()
        } // control is also called from callback via update from tracker
        out = getEnclosedFilterChain().filterPacket(in);// TODO real practical problem here that if there is no retina input that survives to tracker, we get no updates here to control on
        if (tobiLogger != null && tobiLogger.isEnabled()) {
            float ballX = Float.NaN, ballY = Float.NaN;
            float ballVelX = Float.NaN, ballVelY = Float.NaN;
            if (tracker.ball != null && tracker.ball.getLocation() != null) {
                ballX = tracker.ball.getLocation().x;
                ballY = tracker.ball.getLocation().y;
                if (tracker.ball.getVelocityPPS() != null) {
                    ballVelX = tracker.ball.getVelocityPPS().x;
                    ballVelY = tracker.ball.getVelocityPPS().y;
                }
            }
            //         timestamp pan tilt targetX targetY ballX ballY ballVelX ballVelY");
            tobiLogger.log(String.format("%d %f %f %f %f %f %f %f %f",
                    in.getLastTimestamp(),
                    labyrinthHardware.panValue, labyrinthHardware.tiltValue,
                    target.x, target.y,
                    ballX, ballY,
                    ballVelX, ballVelY
            ));
        }
        return out;
    }
    private Point2D.Float futurePosErrPix = new Point2D.Float();
    private Point2D.Float derivErrorPPS = new Point2D.Float();
    private Point2D.Float futurePos = new Point2D.Float();
    long lastTimeBallDetected = 0;

    private void control(EventPacket in, int timestamp) {
//        if (handDetector.isHandDetected()) {
//            return;
//        }
        if (tracker.getBall() != null) {
            lastTimeBallDetected = System.currentTimeMillis();
            Cluster ball = tracker.getBall();

            target = nav.findTarget();

            if (target != null) {
                futurePos.setLocation(tracker.getBallLocation());
                Point2D.Float velPPS = tracker.getBallVelocity();
                // future position of ball is given by ball velocity times delay
                if (controllerDelayMs > 0) {
                    float delSec = 1e-3f * controllerDelayMs;
                    futurePos.setLocation(futurePos.x + (velPPS.x * delSec), futurePos.y + (velPPS.y * delSec));
                }

                futurePosErrPix.setLocation(target.x - futurePos.x, target.y - futurePos.y); // vector pointing towards target from future position

                pControl.setLocation(proportionalGain * futurePosErrPix.x, proportionalGain * futurePosErrPix.y); // towards target

                // derivative error is vector rate of change of position error which is related to ball velocity by projection of ball
                // velocity onto position error vector, not just velocity
                //                float dotVelPos=velPPS.x*futurePosErrPix.x+velPPS.y*futurePosErrPix.y; // positive if vel in direction of vector connecting from future pos to target
                //                float futurePosErrLength=(float)futurePosErrPix.distance(0,0); // length of future pos error vector
                //                if(futurePosErrLength<1e-1f){
                //                    futurePosErrLength=1e-1f;
                //                }
                //
                //                float cosAngle=dotVelPos/futurePosErrLength;
                // projection of ball velocity onto pos error vector
                //                derivErrorPPS.setLocation(futurePosErrPix.x*cosAngle, futurePosErrPix.y*cosAngle); // points in direction of ball motion projected onto pos error vector
                derivErrorPPS.setLocation(velPPS.x, velPPS.y); // points in direction of ball motion

                //                dControl.setLocation(-derivErrorPPS.x * derivativeGain * tau, -derivErrorPPS.y * derivativeGain * tau); // dControl is derivative error term of control signal. It points along line connecting predicated position in future with target.
                dControl.setLocation(-derivErrorPPS.x * derivativeGain * tau, -derivErrorPPS.y * derivativeGain * tau);
                if (!controllerInitialized) {
                    lastErrorUpdateTime = timestamp; // initialize to avoid giant dt, will be zero on first pass
                    controllerInitialized = true;
                }
                int dtUs = timestamp - lastErrorUpdateTime;
                float dtSec = dtUs * 1e-6f * AEConstants.TICK_DEFAULT_US;
                float iFac = (integralGain * dtSec) / tau;
                if (integralControlUsesPropDerivErrors) {
                    iControl.x += iFac * (futurePosErrPix.x - (derivErrorPPS.x * tau));
                    iControl.y += iFac * (futurePosErrPix.y - (derivErrorPPS.y * tau));
                } else {
                    iControl.x += iFac * futurePosErrPix.x;
                    iControl.y += iFac * futurePosErrPix.y;
                }
                lastErrorUpdateTime = timestamp;
                // anti windup control
                float intLim = getTiltLimitRad();
                iControl.x = windupLimit(iControl.x, intLim);
                iControl.y = windupLimit(iControl.y, intLim);
				//                System.out.println("ierr= "+iControl);

                //                pTilt.setLocation(pControl.x, pControl.y);
                //                iTilt.setLocation(iControl.x, iControl.y);
                //                dTilt.setLocation(dControl.x, dControl.y);
                float xtilt = pControl.x + dControl.x + iControl.x;
                float ytilt = pControl.y + dControl.y + iControl.y;
                try {
                    setTilts(xtilt, ytilt);
                } catch (HardwareInterfaceException ex) {
                    // TODO ignore for now - need to handle hardware errors in any case in servo class better
                }
            }
        } else {
            long timeNow = System.currentTimeMillis();
            long timeSinceBallLost = timeNow - lastTimeBallDetected;
            if (timeSinceBallLost > getTimeToTriggerJiggleAfterBallLostMs()) {
                timeSinceBallLost = timeNow + getJiggleTimeMs();
                doJiggleTable();
            }
            resetControllerState();
        }
    }

    //    private void findTarget() {
    //        if (mousePosition != null) {
    //            target = mousePosition;
    //        } else {
    //            target = tracker.findNextPathPoint();
    //        }
    //    }
    float windupLimit(float intErr, float lim) {
        if (intErr > lim) {
            intErr = lim;
        } else if (intErr < -lim) {
            intErr = -lim;
        }
        return intErr;
    }

    @Override
    public void resetFilter() {
        resetControllerState();
        filterChain.reset();
        nav.reset();
    }

    @Override
    public void initFilter() {
        resetFilter();
    }

    public void doStartLogging() {
        if (tobiLogger == null) {
            tobiLogger = new TobiLogger("LabyrinthBallController",
                    " LabyrinthBallController logging data\nt timestamp pan tilt targetX targetY ballX ballY ballVelX ballVelY");
        }
        tobiLogger.setEnabled(true);

    }

    public void doStopLogging() {
        if (tobiLogger != null) {
            tobiLogger.setEnabled(false);
        }

    }

    /**
     * Sets the pan and tilt servo values, clipped to limits, and sets internal
     * values.
     *
     * @param xtiltRad in radians, positive to tilt to right towards positive x
     * @param ytiltRad in radians, positive to tilt up towards positive y
     */
    @Override
    public void setTilts(float xtiltRad, float ytiltRad) throws HardwareInterfaceException {
        float xTiltRad = clipPanTilts((xtiltRad));
        float yTiltRad = clipPanTilts((ytiltRad));
        tiltsRad.setLocation(xTiltRad, yTiltRad);
        labyrinthHardware.setPanTiltValues(tiltsRad.x, tiltsRad.y);
    }

    private float clipPanTilts(float tilt) {
        if (tilt > getTiltLimitRad()) {
            return getTiltLimitRad();
        } else if (tilt < -getTiltLimitRad()) {
            return -getTiltLimitRad();
        }
        return tilt;
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {

        super.annotate(drawable);
        GL2 gl = drawable.getGL().getGL2();
        //        findTarget();
        if (target != null) {
            // draw desired position disk
            gl.glColor4f(.5f, .5f, 0, .8f);
            gl.glPushMatrix();
            gl.glTranslatef(target.x, target.y, 0);
            if (quad == null) {
                quad = glu.gluNewQuadric();
            }
            glu.gluQuadricDrawStyle(quad, GLU.GLU_FILL);
            glu.gluDisk(quad, 0, 3, 32, 1);
            gl.glPopMatrix();
            // draw error vector if ball is tracked also
            if (tracker.getBall() != null) {
                gl.glLineWidth(2f);
                gl.glColor4f(.25f, .25f, 0, .3f);
                gl.glBegin(GL.GL_LINES);
                gl.glVertex2f(tracker.getBallLocation().x, tracker.getBallLocation().y);
                gl.glVertex2f(target.x, target.y);
                gl.glEnd();
            }
        }
        // draw table tilt values
        final float size = .1f;
        float sx = chip.getSizeX(), sy = chip.getSizeY();
        chip.getCanvas().checkGLError(gl, glu, "before controller annotations");
        {
            gl.glPushMatrix();

            gl.glTranslatef(-sx * size * 1.1f, sy / 2, 0); // move outside chip array to lower left
            gl.glScalef(sx * size, sx * size, sx * size); // scale everything so that when we draw 1 unit we cover this size*sx pixels

            chip.getCanvas().checkGLError(gl, glu, "in control box  controller annotations");
            gl.glLineWidth(3f);
            gl.glColor3f(1, 1, 1);

            {
                gl.glBegin(GL.GL_LINE_LOOP);
                gl.glVertex2f(0, 0);
                gl.glVertex2f(1, 0);
                gl.glVertex2f(1, 1);
                gl.glVertex2f(0, 1);
                gl.glEnd();
            }
            gl.glPointSize(4f);
            {
                gl.glBegin(GL.GL_POINTS);
                gl.glVertex2f(.5f, .5f);
                gl.glEnd();
            }
            chip.getCanvas().checkGLError(gl, glu, "drawing tilt box");
            // draw tilt box
            float sc = getTiltLimitRad() * 2;
            float xlen = tiltsRad.x / sc;
            float ylen = tiltsRad.y / sc;

            gl.glLineWidth(4f);
            if ((Math.abs(xlen) < .5f) && (Math.abs(ylen) < .5f)) {
                gl.glColor4f(0, 1, 0, 1);
            } else {
                gl.glColor4f(1, 0, 0, 1);
            }
            gl.glPointSize(4f);
            {
                gl.glTranslatef(.5f, .5f, 0);
                gl.glBegin(GL.GL_POINTS);
                gl.glVertex2f(0, 0);
                gl.glEnd();
            }
            // draw tilt vector
            float x = pControl.x / sc;
            float y = pControl.y / sc;
            gl.glLineWidth(4);
            {
                gl.glBegin(GL.GL_LINE_STRIP);
                gl.glVertex2f(0, 0);
                gl.glColor3f(1, 0, 0);
                gl.glVertex2f(x, y);
                gl.glColor3f(0, 1, 0);
                x += iControl.x / sc;
                y += iControl.y / sc;
                gl.glVertex2f(x, y);
                gl.glColor3f(0, 0, 1);
                x += dControl.x / sc;
                y += dControl.y / sc;
                gl.glVertex2f(x, y);
                gl.glEnd();
                gl.glBegin(GL.GL_LINES);
                gl.glColor3f(1, 1, 1);
                gl.glVertex2f(0, 0);
                gl.glVertex2f(xlen, ylen);
                gl.glEnd();
            }
            chip.getCanvas().checkGLError(gl, glu, "drawing tilt box");
            gl.glPopMatrix();
        }

        // print some stuff
        StringBuilder s = new StringBuilder("Ball controller:\nLeft-Click to hint ball location\nMiddle-Click/drag to set desired ball position\nRight click to set next path vertex\nCtl-click outside chip frame to clear desired ball posiition");
        s.append(String.format("\nController dynamics:\ntau=%.1fms\nQ=%.2f", tau * 1000, Q));
        s.append(isControllerEnabled() ? "\nController is ENABLED" : "\nController is DISABLED");
        s.append("\n").append(nav.toString());
        MultilineAnnotationTextRenderer.setScale(.2f);
        MultilineAnnotationTextRenderer.renderMultilineString(s.toString());
        chip.getCanvas().checkGLError(gl, glu, "after controller annotations");
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        if (evt.getSource() instanceof LabyrinthHardware) {
            if (evt.getPropertyName() == LabyrinthHardware.PANTILT_CHANGE) {
                tiltsRad.setLocation((Point2D.Float) evt.getNewValue());
            }
        }
        getSupport().firePropertyChange(evt);
    }

    /**
     * @return the proportionalGain
     */
    public float getProportionalGain() {
        return proportionalGain;
    }

    /**
     * @param proportionalGain the proportionalGain to set
     */
    public void setProportionalGain(float proportionalGain) {
        this.proportionalGain = proportionalGain;
        putFloat("proportionalGain", proportionalGain);
        computePoles();
    }

    /**
     * @return the derivativeGain
     */
    public float getDerivativeGain() {
        return derivativeGain;
    }

    /**
     * @param derivativeGain the derivativeGain to set
     */
    public void setDerivativeGain(float derivativeGain) {
        this.derivativeGain = derivativeGain;
        putFloat("derivativeGain", derivativeGain);
        computePoles();
    }

    /**
     * @return the integralGain
     */
    public float getIntegralGain() {
        return integralGain;
    }

    /**
     * @param integralGain the integralGain to set
     */
    public void setIntegralGain(float integralGain) {
        this.integralGain = integralGain;
        putFloat("integralGain", integralGain);
        iControl.setLocation(0, 0);
        computePoles();
    }

    int timeUs() {
        return (int) (System.nanoTime() >> 10);
    }
    //    /**
    //     * @return the mousePosition
    //     */
    //    private Point2D.Float getDesiredPosition() {
    //        return mousePosition;
    //    }
    //
    //    /**
    //     * @param mousePosition the mousePosition to set
    //     */
    //    private void setDesiredPosition(Point2D.Float desiredPosition) {
    //        this.mousePosition = desiredPosition;
    //        resetControllerState();
    //    }
    private float tau, Q;

    private void computePoles() {
        // compute the pole locations and resulting tau and Q given PID parameters
        // TODO doesn't include integral term yet
        tau = (float) Math.sqrt(1 / (gravConstantPixPerSec2 * proportionalGain));
        Q = proportionalGain / derivativeGain;
    }

    /**
     * @return the xTiltRad
     */
    public float getxTiltRad() {
        return tiltsRad.x;
    }

    /**
     * @return the yTiltRad
     */
    public float getyTiltRad() {
        return tiltsRad.y;
    }

    /**
     * @return the tilts in radians, pan is x and tilt is y. pan tilts table
     * horizontally to affect ball acceleration along x axis and tilt affects y
     * axis acceleration.
     */
    public Point2D.Float getTiltsRad() {
        return tiltsRad;
    }

    /**
     * @return the tiltLimitRad
     */
    @Override
    public float getTiltLimitRad() {
        return labyrinthHardware.getPanTiltLimitRad();
    }

    /**
     * @param tiltLimitRad the tiltLimitRad to set
     */
    public void setTiltLimitRad(float tiltLimitRad) {
        if (tiltLimitRad < 0.001f) {
            tiltLimitRad = 0.001f; // prevent / by 0
        }
        labyrinthHardware.setPanTiltLimitRad(tiltLimitRad);
    }

    private void resetControllerState() {
        controllerInitialized = false;
        iControl.setLocation(0, 0);
        //        centerTilts();
    }

    /**
     * @return the jiggleRunning
     */
    public boolean isJiggleRunning() {
        return jiggleRunning;
    }

    /**
     * @param jiggleRunning the jiggleRunning to set
     */
    public void setJiggleRunning(boolean jiggleRunning) {
        this.jiggleRunning = jiggleRunning;
    }

    private void setTrackPositionFromMouseEvent(MouseEvent e) {
        Point p = getMousePixel(e);
        if (p == null) {
            return;
        }
        Point2D.Float pf = new Point2D.Float(p.x, p.y);
        nav.reset();
        nav.setCurrenPathPoint(tracker.map.findNearestPathPoint(pf));
        nav.setNextPathPoint(nav.getCurrenPathPoint().next());
    }

    public enum Message {

        AbortRecording,
        ClearRecording,
        SetRecordingEnabled
    }

    synchronized private void setBallLocationFromMouseEvent(MouseEvent e) {
        Point p = getMousePixel(e);
        if (p == null) {
            return;
        }
        Point2D.Float pf = new Point2D.Float(p.x, p.y);
        tracker.setBallLocation(pf);
    }

    synchronized private void setDesiredPositionFromMouseEvent(MouseEvent e) {
        Point p = getMousePixel(e);
        if (p == null) {
            mousePosition = null;
            resetControllerState();
            return;
        }
        if (mousePosition == null) {
            mousePosition = new Point2D.Float(p.x, p.y);
        } else {
            mousePosition.x = p.x;
            mousePosition.y = p.y;
        }
        resetControllerState();
        //        log.info("desired position from mouse=" + mousePosition);
    }

    void processMouseEvent(MouseEvent e) {
        int left = InputEvent.BUTTON1_DOWN_MASK;
        int middle = InputEvent.BUTTON2_DOWN_MASK;
        int right = InputEvent.BUTTON3_DOWN_MASK;
        //        log.info("e.getModifiersEx()="+HexString.toString(e.getModifiersEx()));
        if ((e.getModifiersEx() & (middle)) == middle) {
            setDesiredPositionFromMouseEvent(e);
        } else if ((e.getModifiersEx() & left) == left) {
            setBallLocationFromMouseEvent(e);
        } else if ((e.getModifiersEx() & (right)) == right) {
            setTrackPositionFromMouseEvent(e);
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        processMouseEvent(e);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        processMouseEvent(e);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        processMouseEvent(e);
    }

    class Trajectory extends LinkedList<TrajectoryPoint> {

        final int MAX_POINTS = 1000;

        public void add(long millis, float pan, float tilt) {
            if (size() > MAX_POINTS) {
                removeFirst();
            }
            add(new TrajectoryPoint(millis, pan, tilt));
        }
    }

    class TrajectoryPoint {

        long timeMillis;
        float pan, tilt;

        public TrajectoryPoint(long timeMillis, float pan, float tilt) {
            this.timeMillis = timeMillis;
            this.pan = pan;
            this.tilt = tilt;
        }
    }

    @Override
    public void centerTilts() {
        try {
            setTilts(0, 0);
        } catch (HardwareInterfaceException ex) {
            log.warning(ex.toString());
        }
    }

    @Override
    public void disableServos() {
        if ((labyrinthHardware != null) && (labyrinthHardware.getServoInterface() != null)) {
            try {
                labyrinthHardware.stopJitter();
                labyrinthHardware.getServoInterface().disableAllServos();
            } catch (HardwareInterfaceException ex) {
                log.warning(ex.toString());
            }
        }
    }

    public LabyrinthHardware getPanTiltHardware() {
        return labyrinthHardware;
    }

    @Override
    public void controlTilts() {
        if ((gui == null) || !gui.isDisplayable()) {
            gui = new LabyrinthTableTiltControllerGUI(this);
            gui.addPropertyChangeListener(this);
            gui.setPanTiltLimit(getTiltLimitRad());
        }
        gui.setVisible(true);
    }

    public void loadMap() {
        tracker.doLoadMap();
    }

    public synchronized void clearMap() {
        tracker.doClearMap();
    }

    protected volatile boolean jiggleRunning = false;

    public void doStopJiggle() {
        if (jiggleThread != null && jiggleThread.isAlive()) {
            jiggleThread.interrupt();
        }
    }

    @Override
    public void doJiggleTable() {
        Runnable r = new Runnable() {

            @Override
            public void run() {
                if (isJiggleRunning()) {
                    return;
                }
                setJiggleRunning(true);
                log.info("starting jiggle");
                setControllerDisabledTemporarily(true);
                tracker.resetFilter();
                centerTilts();
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ex) {
                    setJiggleRunning(false);
                    return;
                }

                labyrinthHardware.startJitter();
                try {
                    Thread.sleep(getJiggleTimeMs());
                } catch (InterruptedException ex) {
                    setJiggleRunning(false);
                    labyrinthHardware.stopJitter();
                    return;
                }
                labyrinthHardware.stopJitter();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                }
                centerTilts();
                setControllerDisabledTemporarily(false);
                setJiggleRunning(false);
            }
        };
        jiggleThread = new Thread(r);
        jiggleThread.setName("TableJiggler");
        jiggleThread.start();
    }

    public boolean isLostTracking() {
        return tracker.isLostTracking();
    }

    public boolean isAtMazeStart() {
        return tracker.isAtMazeStart();
    }

    public boolean isAtMazeEnd() {
        return tracker.isAtMazeEnd();
    }

    public boolean isPathNotFound() {
        return tracker.isPathNotFound();
    }

    /**
     * @return the controllerEnabled
     */
    public boolean isControllerEnabled() {
        return controllerEnabled && !temporarilyDisabled;
    }

    /**
     * @param controllerEnabled the controllerEnabled to set
     */
    public void setControllerEnabled(boolean controllerEnabled) {
        boolean old = this.controllerEnabled;
        this.controllerEnabled = controllerEnabled;
        putBoolean("controllerEnabled", controllerEnabled);
        getSupport().firePropertyChange("controllerEnabled", old, this.controllerEnabled);
    }
    private boolean temporarilyDisabled = false;

    /**
     * @param disabled the controllerEnabled to set. This method does not store
     * a preference value.
     */
    @Override
    public void setControllerDisabledTemporarily(boolean disabled) {
        temporarilyDisabled = disabled;
    }

    @Override
    public boolean isControllerTemporarilyDisabled() {
        return temporarilyDisabled;
    }

    /**
     * @return the jiggleTimeMs
     */
    public int getJiggleTimeMs() {
        return jiggleTimeMs;
    }

    /**
     * @param jiggleTimeMs the jiggleTimeMs to set
     */
    public void setJiggleTimeMs(int jiggleTimeMs) {
        int old = this.jiggleTimeMs;
        this.jiggleTimeMs = jiggleTimeMs;
        putInt("jiggleTimeMs", jiggleTimeMs);
        getSupport().firePropertyChange("jiggleTimeMs", old, this.jiggleTimeMs);
    }

    /**
     * Get the value of integralControlUsesPropDerivErrors
     *
     * @return the value of integralControlUsesPropDerivErrors
     */
    public boolean isIntegralControlUsesPropDerivErrors() {
        return integralControlUsesPropDerivErrors;
    }

    /**
     * Set the value of integralControlUsesPropDerivErrors
     *
     * @param integralControlUsesPropDerivErrors new value of
     * integralControlUsesPropDerivErrors
     */
    public void setIntegralControlUsesPropDerivErrors(boolean integralControlUsesPropDerivErrors) {
        this.integralControlUsesPropDerivErrors = integralControlUsesPropDerivErrors;
        iControl.setLocation(0, 0); // reset error signal
        putBoolean("integralControlUsesPropDerivErrors", integralControlUsesPropDerivErrors);
    }

    /**
     * @return the controllerDelayMs
     */
    public float getControllerDelayMs() {
        return controllerDelayMs;
    }

    /**
     * @param controllerDelayMs the controllerDelayMs to set
     */
    public void setControllerDelayMs(float controllerDelayMs) {
        if (controllerDelayMs < 0) {
            controllerDelayMs = 0;
        } else if (controllerDelayMs > 200) {
            controllerDelayMs = 200;
        }
        this.controllerDelayMs = controllerDelayMs;
        putFloat("controllerDelayMs", controllerDelayMs);

    }

    /**
     * @return the dwellTimePathPointMs
     */
    public float getDwellTimePathPointMs() {
        return dwellTimePathPointMs;
    }

    /**
     * @param dwellTimePathPointMs the dwellTimePathPointMs to set
     */
    public void setDwellTimePathPointMs(float dwellTimePathPointMs) {
        this.dwellTimePathPointMs = dwellTimePathPointMs;
        putFloat("dwellTimePathPointMs", dwellTimePathPointMs);
    }

    /**
     * @return the timeToTriggerJiggleAfterBallLostMs
     */
    public int getTimeToTriggerJiggleAfterBallLostMs() {
        return timeToTriggerJiggleAfterBallLostMs;
    }

    /**
     * @param timeToTriggerJiggleAfterBallLostMs the
     * timeToTriggerJiggleAfterBallLostMs to set
     */
    public void setTimeToTriggerJiggleAfterBallLostMs(int timeToTriggerJiggleAfterBallLostMs) {
        this.timeToTriggerJiggleAfterBallLostMs = timeToTriggerJiggleAfterBallLostMs;
        putInt("timeToTriggerJiggleAfterBallLostMs", timeToTriggerJiggleAfterBallLostMs);
    }

    enum NavigatorState {

        ReachingNext, Settling
    };

    class PathNavigator {

        long timeReachedNextPathPointMs, timeNowMs, timeSinceReachedMs;
        protected PathPoint currenPathPoint;
        protected PathPoint nextPathPoint = null;
        PathPoint previousPathPoint = null;
        float fractionToNextPoint = 0;
        private int NUM_PATH_POINTS_AHEAD_TO_ACCEPT = 3;

        public PathNavigator() {
            timeNowMs = System.currentTimeMillis();
            timeReachedNextPathPointMs = timeNowMs;
        }

        @Override
        public String toString() {
            return String.format("%3d -> %3d (%4dms here)",
                    getCurrenPathPoint() == null ? -1 : getCurrenPathPoint().index,
                    getNextPathPoint() == null ? -1 : getNextPathPoint().index,
                    timeNowMs - timeReachedNextPathPointMs);
        }

        synchronized Point2D.Float findTarget() {
            if (mousePosition != null) {
                return mousePosition;
            } else {
                if (getNextPathPoint() == null) {
                    setNextPathPoint(tracker.findNextPathPoint());
                }
                timeNowMs = System.currentTimeMillis();
                setCurrenPathPoint(tracker.findNearestPathPoint());
                if (getCurrenPathPoint() == null) {
                    return getNextPathPoint();
                }
                if (getCurrenPathPoint().equals(getNextPathPoint())) { // we reached next point
                    previousPathPoint = getCurrenPathPoint(); // set previous one
                    setNextPathPoint(getNextPathPoint().next()); // and select next point
                    timeReachedNextPathPointMs = timeNowMs; // set time that we reached this one
                }
                if (previousPathPoint == null) {
                    previousPathPoint = getCurrenPathPoint();
                }

                timeSinceReachedMs = timeNowMs - timeReachedNextPathPointMs;  // at all times that we have a target, compute time since we reached previous point
                fractionToNextPoint = timeSinceReachedMs / getDwellTimePathPointMs(); // compute fraction of time we allocate to each segment
                if (fractionToNextPoint > 1) {
                    fractionToNextPoint = 1;
                }
                Point2D.Float target = previousPathPoint.getPointFractionToNext(fractionToNextPoint); // so return a point part way to next point to aim for
                return target;
            }
        }

        synchronized private void reset() {
            setCurrenPathPoint(null);
            setNextPathPoint(null);
            previousPathPoint = null;
        }

        /**
         * @return the currenPathPoint
         */
        public PathPoint getCurrenPathPoint() {
            return currenPathPoint;
        }

        /**
         * @param currenPathPoint the currenPathPoint to set
         */
        private void setCurrenPathPoint(PathPoint currenPathPoint) {
            this.currenPathPoint = currenPathPoint;
        }

        /**
         * @return the nextPathPoint
         */
        public PathPoint getNextPathPoint() {
            return nextPathPoint;
        }

        /**
         * @param nextPathPoint the nextPathPoint to set
         */
        public void setNextPathPoint(PathPoint nextPathPoint) {
            this.nextPathPoint = nextPathPoint;
        }
    }

//    public synchronized void doCollectHistogram() {
//        tracker.doCollectHistogram();
//    }
//
//    public synchronized void doFreezeHistogram() {
//        tracker.doFreezeHistogram();
//    }
    
    
    
}
