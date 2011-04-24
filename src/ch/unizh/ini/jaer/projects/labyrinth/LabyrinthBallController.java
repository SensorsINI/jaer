/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.labyrinth;

import ch.unizh.ini.jaer.hardware.pantilt.*;
import ch.unizh.ini.jaer.hardware.pantilt.PanTilt;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.GLU;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker.Cluster;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * This filter enables controlling the tracked labyrinth ball.
 * 
 * @author Tobi Delbruck
 */
public class LabyrinthBallController extends EventFilter2DMouseAdaptor implements PropertyChangeListener, Observer {

    public static final String getDescription() {
        return "Low level ball controller for Labyrinth game";
    }
    // properties
    private boolean mouseBallControlEnabled = getBoolean("mouseBallControl", true);
    private float xTilt = 0, yTilt = 0; // convention is that xTilt>0 rolls to right, yTilt>0 rolls ball up
    // control
    private float proportionalGain = getFloat("proportionalGain", 1);
    private float derivativeGain = getFloat("derivativeGain", 1);
    private float integralGain = getFloat("integralGain", 1);
    // constants
    private final float angleRadPerServoUnit = (float) (5 * Math.PI / 180 / 0.1); // angle in radians produced by each unit of servo control change
    // this is approx 5 deg per 0.1 unit change 
    // The ball acceleration will be g (grav constant) * sin(angle) which is approx g*angle with angle in radians =g*angleRadPerServoUnit*(servo-0.5f).
    private final float metersPerPixel = 0.22f / 128;  // pixels of 128 retina per meter
    private final float gravConstant = 9.8f; // meters per second^2
    // fields
    private LabyrinthHardware labyrinthHardware;
    private LabyrinthBallTracker tracker = null;
    private FilterChain filterChain;
    // errors
    volatile Point2D.Float posError = null;
    Point2D.Float integralError = new Point2D.Float(0, 0);
    int lastErrorUpdateTime = 0;
    // state stuff
    Point desiredPosition = null;
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
            if (desiredPosition != null) {
                posError = new Point2D.Float(desiredPosition.x - pos.x, desiredPosition.y - pos.y);
                if(lastErrorUpdateTime==0) lastErrorUpdateTime=timestamp; // initialize to avoid giant dt, will be zero on first pass
                int dt = timestamp - lastErrorUpdateTime;
                integralError.x += dt * posError.x*1e-6f;
                integralError.y += dt * posError.y*1e-6f;
                lastErrorUpdateTime = timestamp;
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
            lastErrorUpdateTime=0;
        }
    }

    @Override
    public void resetFilter() {
        integralError.setLocation(0,0);
        lastErrorUpdateTime=0;
        filterChain.reset();
    }

    @Override
    public void initFilter() {
        resetFilter();
    }

    /** Sets the pan and tilt servo values
    @param xTilt 0 to 1 value
    @param tilt 0 to 1 value
     */
    public void setTilts(float xtilt, float ytilt) throws HardwareInterfaceException {
        xTilt = xtilt;
        yTilt = ytilt;
        getPanTiltHardware().setPanTiltValues(tilt2servo(xtilt), tilt2servo(ytilt));
    }

    private float tilt2servo(float tilt) {
        return .5f + tilt;
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable);
        GL gl = drawable.getGL();
        if (desiredPosition != null) {
            // draw desired position disk
            gl.glColor4f(.25f, .25f, 0, .3f);
            gl.glPushMatrix();
            gl.glTranslatef(desiredPosition.x, desiredPosition.y, 0);
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
                gl.glVertex2f(desiredPosition.x, desiredPosition.y);
                gl.glEnd();
            }
            // draw table tilt values
            gl.glPushMatrix();
            gl.glTranslatef(chip.getSizeX() / 2, chip.getSizeY() / 2, 0);
            gl.glLineWidth(4f);
            gl.glColor4f(.25f, 0, 0, .3f);
            gl.glBegin(GL.GL_LINES);
            float xlen = chip.getMaxSize() * xTilt;
            float ylen = chip.getMaxSize() * yTilt;
            gl.glVertex2f(0, 0);
            gl.glVertex2f(xlen, ylen);
            gl.glEnd();
            gl.glPopMatrix();

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

    public enum Message {

        AbortRecording,
        ClearRecording,
        SetRecordingEnabled
    }

    private void mouseEvent(MouseEvent e) {
        desiredPosition = getMousePixel(e);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        mouseEvent(e);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        mouseEvent(e);
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

    public void doCenter() {
        try {
            setTilts(0, 0);
        } catch (HardwareInterfaceException ex) {
            log.warning(ex.toString());
        }
    }

    public void doDisableServos() {
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
}
