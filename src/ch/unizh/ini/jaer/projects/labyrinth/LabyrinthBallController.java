/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.labyrinth;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.LinkedList;
import java.util.Observable;
import java.util.Observer;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.GLU;
import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker.Cluster;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * This filter enables controlling the tracked labyrinth ball.
 * 
 * @author Tobi Delbruck
 */
public class LabyrinthBallController extends EventFilter2DMouseAdaptor implements PropertyChangeListener, Observer {
    public static final float MAX_INTEGRAL_CONTROL_RAD = 20 * (float)Math.PI / 180;

    public static final String getDescription() {
        return "Low level ball controller for Labyrinth game";
    }
    // properties
    private float xTilt = 0, yTilt = 0; // convention is that xTilt>0 rolls to right, yTilt>0 rolls ball up
    // control
    private float proportionalGain = getFloat("proportionalGain", 1);
    private float derivativeGain = getFloat("derivativeGain", 1);
    private float integralGain = getFloat("integralGain", 1);
    // constants
    private final float angleRadPerServoUnit = (float) (5 * Math.PI / 180 / 0.1); // TODO estimated angle in radians produced by each unit of servo control change
    private final float servoUnitPerAngleRad = 1 / angleRadPerServoUnit; //  equiv servo unit
    // this is approx 5 deg per 0.1 unit change 
    // The ball acceleration will be g (grav constant) * sin(angle) which is approx g*angle with angle in radians =g*angleRadPerServoUnit*(servo-0.5f).
    private final float metersPerPixel = 0.22f / 128;  // TODO estimated pixels of 128 retina per meter, assumes table fills retina vertically
    private final float gravConstantMperS2 = 9.8f; // meters per second^2
    private final float gravConstantPixPerSec2 = gravConstantMperS2 / metersPerPixel; // g in pixel units
    // fields
    private LabyrinthHardware labyrinthHardware;
    private LabyrinthBallTracker tracker = null;
    // filter chain
    private FilterChain filterChain;
    // errors
    volatile Point2D.Float posError = null;
    Point2D.Float integralError = new Point2D.Float(0, 0);
    int lastErrorUpdateTime = 0;
    // state stuff
    private Point desiredPosition = null;
    // history
    Trajectory trajectory = new Trajectory();

    /** Constructs instance of the new 'filter' CalibratedPanTilt. The only time events are actually used
     * is during calibration. The PanTilt hardware interface is also constructed.
     * @param chip
     */
    public LabyrinthBallController(AEChip chip) {
        super(chip);

        tracker = new LabyrinthBallTracker(chip);
        tracker.addObserver(this);
        labyrinthHardware = new LabyrinthHardware(chip);

        filterChain = new FilterChain(chip);

        filterChain.add(labyrinthHardware);
        filterChain.add(tracker);

        setEnclosedFilterChain(filterChain);

        String control = "Control";
        setPropertyTooltip("disableServos", "disables servos, e.g. to turn off annoying servo hum");
        setPropertyTooltip("center", "centers pan and tilt controls");
        setPropertyTooltip("integralGain", "integral error gain: tilt(rad)=error(pixels)*integralGain(1/(pixels*sec))");
        setPropertyTooltip("proportionalGain", "proportional error gain: tilt(rad)=error(pixels)*proportionalGain(1/pixels)");
        setPropertyTooltip("derivativeGain", "-derivative gain: damps overshoot. tilt(rad)=-vel(pix/sec)*derivativeGain(sec/pixel)");
        computePoles();
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        out = getEnclosedFilterChain().filterPacket(in);
        control(in, in.getLastTimestamp());

        return out;
    }

    private void control(EventPacket in, int timestamp) {
        if (tracker.getBall() != null) {
            Cluster ball = tracker.getBall();
            Point2D.Float pos = ball.getLocation();
            trajectory.add(new TrajectoryPoint(ball.getLastEventTimestamp(), pos.x, pos.y));
            Point2D.Float vel = ball.getVelocityPPS();
            if (getDesiredPosition() != null) {
                posError = new Point2D.Float(getDesiredPosition().x - pos.x, getDesiredPosition().y - pos.y);
                if (lastErrorUpdateTime == 0) {
                    lastErrorUpdateTime = timestamp; // initialize to avoid giant dt, will be zero on first pass
                }
                int dt = timestamp - lastErrorUpdateTime;
                integralError.x += dt * posError.x * 1e-6f * AEConstants.TICK_DEFAULT_US;
                integralError.y += dt * posError.y * 1e-6f * AEConstants.TICK_DEFAULT_US;
                lastErrorUpdateTime = timestamp;
                // anti windup control
                integralError.x = windupLimit(integralError.x);
                integralError.y = windupLimit(integralError.y);
                if (integralError.x * integralGain > MAX_INTEGRAL_CONTROL_RAD) {
                    integralError.x = MAX_INTEGRAL_CONTROL_RAD / integralGain;
                }
                float xtilt = posError.x * proportionalGain - vel.x * derivativeGain + integralError.x * integralGain;
                float ytilt = posError.y * proportionalGain - vel.y * derivativeGain + integralError.y * integralGain;
                try {
                    setTilts(xtilt, ytilt);
                } catch (HardwareInterfaceException ex) {
                    // TODO ignore for now - need to handle hardware errors in any case in servo class better
                }
            }
        } else {
            integralError.setLocation(0, 0);
            lastErrorUpdateTime = 0;
        }
    }

    float windupLimit(float intErr){
        if(Math.abs(intErr*integralGain)>MAX_INTEGRAL_CONTROL_RAD){
            intErr=MAX_INTEGRAL_CONTROL_RAD/integralGain*Math.signum(intErr);
        }
        return intErr;
    }

    @Override
    public void resetFilter() {
        integralError.setLocation(0, 0);
        lastErrorUpdateTime = 0;
        filterChain.reset();
    }

    @Override
    public void initFilter() {
        resetFilter();
    }

    /** Sets the pan and tilt servo values
    @param xtiltRad in radians, positive to tilt to right towards positive x
    @param ytiltRad in radians, positive to tilt up towards positive y
     */
    public void setTilts(float xtiltRad, float ytiltRad) throws HardwareInterfaceException {
        xTilt = xtiltRad;
        yTilt = ytiltRad;
        getPanTiltHardware().setPanTiltValues(tilt2servo(xtiltRad), tilt2servo(yTilt));
    }

    private float tilt2servo(float tiltRad) {
        return .5f + tiltRad * servoUnitPerAngleRad;
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {

        super.annotate(drawable);
        GL gl = drawable.getGL();
        if (getDesiredPosition() != null) {
            // draw desired position disk
            gl.glColor4f(0, .25f, 0, .3f);
            gl.glPushMatrix();
            gl.glTranslatef(getDesiredPosition().x, getDesiredPosition().y, 0);
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
                gl.glVertex2f(tracker.getBall().getLocation().x, tracker.getBall().getLocation().y);
                gl.glVertex2f(getDesiredPosition().x, getDesiredPosition().y);
                gl.glEnd();
            }
            // draw table tilt values
            gl.glPushMatrix();
            gl.glTranslatef(chip.getSizeX() / 2, chip.getSizeY() / 2, 0);
            gl.glLineWidth(4f);
            gl.glColor4f(.25f, 0, 0, .3f);
            gl.glBegin(GL.GL_LINES);
            float xlen = (float) (chip.getMaxSize() * xTilt / Math.PI);
            float ylen = (float) (chip.getMaxSize() * yTilt / Math.PI); // length of tilt vector in units of 1 radian
            gl.glVertex2f(0, 0);
            gl.glVertex2f(xlen, ylen);
            gl.glEnd();
            gl.glPopMatrix();

            // print some stuff
            MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY());
            MultilineAnnotationTextRenderer.renderMultilineString("Click to hint ball location\nCtl-Click/drag to set desired ball position\nCtl-click outside chip frame to clear desired ball posiition");
            String s = String.format("Controller dynamics:\ntau=%.1fms\nQ=%.2f", tau * 1000, Q);
            MultilineAnnotationTextRenderer.renderMultilineString(s);

        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        throw new UnsupportedOperationException("not implemented yet");
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
        computePoles();
    }

    /** Handles control for updates from the tracker.
     * 
     * @param o the calling filter
     * @param arg the UpdateMessage (or other message).
     */
    @Override
    public void update(Observable o, Object arg) {
        if (arg instanceof UpdateMessage) {
            UpdateMessage m = (UpdateMessage) arg;
            control(m.packet, m.timestamp);
        }
    }

    /**
     * @return the desiredPosition
     */
    public Point getDesiredPosition() {
        return desiredPosition;
    }

    /**
     * @param desiredPosition the desiredPosition to set
     */
    public void setDesiredPosition(Point desiredPosition) {
        this.desiredPosition = desiredPosition;
        integralError.setLocation(0, 0);
        lastErrorUpdateTime = 0;
    }
    private float tau, Q;

    private void computePoles() {
        // compute the pole locations and resulting tau and Q given PID parameters
        // TODO doesn't include integral term yet
        tau = (float) Math.sqrt(1 / (gravConstantPixPerSec2 * proportionalGain));
        Q = tau * proportionalGain / derivativeGain;
    }

    public enum Message {

        AbortRecording,
        ClearRecording,
        SetRecordingEnabled
    }

    private void setBallLocationFromMouseEvent(MouseEvent e) {
        Point p = getMousePixel(e);
        Point2D.Float pf = new Point2D.Float(p.x, p.y);
        tracker.setBallLocation(pf);
    }

    private void setDesiredPositionFromMouseEvent(MouseEvent e) {
        setDesiredPosition(getMousePixel(e));
        log.info("desired position=" + desiredPosition);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.isControlDown()) {
            setDesiredPositionFromMouseEvent(e);
        } else {
            setBallLocationFromMouseEvent(e);
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
       if (e.isControlDown()) {
            setDesiredPositionFromMouseEvent(e);
        } else {
            setBallLocationFromMouseEvent(e);
        }
    }

    class Trajectory extends LinkedList<TrajectoryPoint> {

        final int MAX_POINTS = 1000;

        void add(long millis, float pan, float tilt) {
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

    public void centerTilts() {
        try {
            setTilts(0, 0);
        } catch (HardwareInterfaceException ex) {
            log.warning(ex.toString());
        }
    }

    public void disableServos() {
        if (labyrinthHardware != null && labyrinthHardware.getServoInterface() != null) {
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

    public void controlTilts() {
        labyrinthHardware.doControlTilts();
    }
}
