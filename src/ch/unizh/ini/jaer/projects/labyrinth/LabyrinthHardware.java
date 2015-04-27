/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.labyrinth;

import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Random;
import java.util.TimerTask;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.ServoInterface;
import ch.unizh.ini.jaer.hardware.pantilt.PanTilt;

/**
 * The labyrinth hardware abstraction enables controlling the labyrinth table.
 *
 * @author Tobi Delbruck
 */
@Description("Low level hardware interface for Labyrinth game")
public class LabyrinthHardware extends EventFilter2D implements PropertyChangeListener {

    private PanTilt panTiltHardware;
    private boolean jitterEnabled = getBoolean("jitterEnabled", false);
    private float jitterFreqHz = getFloat("jitterFreqHz", 1);
    private float jitterAmplitude = getFloat("jitterAmplitude", .02f);
    private int panServoNumber = getInt("panServoNumber", 1);
    private int tiltServoNumber = getInt("tiltServoNumber", 2);
    private boolean invertPan = getBoolean("invertPan", false);
    private boolean invertTilt = getBoolean("invertTilt", false);
    private float panTiltLimitRad = getFloat("panTiltLimitRad", 0.1f);
    private float panOffset = getFloat("panOffset", 0);
    private float tiltOffset = getFloat("tiltOffset", 0);
    // constants
    final float servoArmAngleRadPerServoUnit = (float) (120f * Math.PI / 180); // TODO estimated angle in radians produced by each unit of servo control change
    final float servoUnitPerServoArmAngleRad = 1 / servoArmAngleRadPerServoUnit; //  equiv servo unit
    float panValue = 0, tiltValue = 0;
    // this is approx 5 deg per 0.1 unit change 
    public static final String PANTILT_CHANGE = "panTiltChange";

    /**
     * Constructs instance of the new 'filter' CalibratedPanTilt. The only time
     * events are actually used is during calibration. The PanTilt hardware
     * interface is also constructed.
     *
     * @param chip
     */
    public LabyrinthHardware(AEChip chip) {
        super(chip);

        panTiltHardware = PanTilt.getLastInstance();
        if (panTiltHardware != null) {
            panTiltHardware.setPanServoNumber(panServoNumber);
            panTiltHardware.setTiltServoNumber(tiltServoNumber);
            panTiltHardware.setJitterAmplitude(jitterAmplitude);
            panTiltHardware.setJitterFreqHz(jitterFreqHz);
            panTiltHardware.setJitterEnabled(jitterEnabled);
            panTiltHardware.setPanInverted(invertPan);
            panTiltHardware.setTiltInverted(invertTilt);
        }

        String servo = "Servos", control = "Control";
        setPropertyTooltip("controlTilts", "shows GUI for controlling table tilts with mouse");
        setPropertyTooltip("disableServos", "disables servos, e.g. to turn off annoying servo hum");
        setPropertyTooltip("center", "centers pan and tilt controls");

        setPropertyTooltip(servo, "jitterAmplitude", "Jitter of pantilt amplitude for circular motion");
        setPropertyTooltip(servo, "jitterFreqHz", "Jitter frequency in Hz of circular motion");
        setPropertyTooltip(servo, "jitterEnabled", "enables servo jitter to produce microsaccadic movement");
        setPropertyTooltip(servo, "panServoNumber", "servo channel for pan (0-3)");
        setPropertyTooltip(servo, "tiltServoNumber", "servo channel for tilt (0-3)");
        setPropertyTooltip(servo, "tiltInverted", "flips the tilt");
        setPropertyTooltip(servo, "panInverted", "flips the pan");
        setPropertyTooltip(servo, "panTiltLimitRad", "limits pan and tilt around 0.5 by this amount to protect hardware");
        setPropertyTooltip(servo, "panOffset", "offset to center pan");
        setPropertyTooltip(servo, "tiltOffset", "offset to center tilt");
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {

        return in; // only handles control commands, no event processing
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
        resetFilter();
    }

    synchronized public void doCenter() {
        try {
            setPanTiltValues(0, 0);
        } catch (HardwareInterfaceException ex) {
            log.warning(ex.toString());
        }
    }

    synchronized public void doDisableServos() {
        if (panTiltHardware != null && panTiltHardware.getServoInterface() != null) {
            try {
                panTiltHardware.stopJitter();
                panTiltHardware.getServoInterface().disableAllServos();
            } catch (HardwareInterfaceException ex) {
                log.warning(ex.toString());
            }
        }
    }

    public PanTilt getPanTiltHardware() {
        return panTiltHardware;
    }

    public void setPanTiltHardware(PanTilt panTilt) {
        this.panTiltHardware = panTilt;
    }

    public float getJitterAmplitude() {
        return jitterAmplitude;
    }

    /**
     * Sets the amplitude (1/2 of peak to peak) of circular jitter of pan tilt
     * during jittering
     *
     * @param jitterAmplitude the amplitude
     */
    public void setJitterAmplitude(float jitterAmplitude) {
        this.jitterAmplitude = jitterAmplitude;
        putFloat("jitterAmplitude", jitterAmplitude);
        if (panTiltHardware == null) {
            return;
        }
        panTiltHardware.setJitterAmplitude(jitterAmplitude);
    }

    public float getJitterFreqHz() {
        return jitterFreqHz;
    }

    /**
     * The frequency of the jitter
     *
     * @param jitterFreqHz in Hz
     */
    public void setJitterFreqHz(float jitterFreqHz) {
        this.jitterFreqHz = jitterFreqHz;
        putFloat("jitterFreqHz", jitterFreqHz);
    }

    public void acquire() {
        getPanTiltHardware().acquire();
    }

    public float[] getPanTiltValues() {
        return new float[]{panValue, tiltValue};
    }

    public boolean isLockOwned() {
        return getPanTiltHardware().isLockOwned();
    }

    public void release() {
        getPanTiltHardware().release();
    }

    synchronized public void startJitter() {
        log.info("starting jitter");
        if (timer != null) {
            stopJitter(); //  running, must stop to get new position correct
        }
        timer = new java.util.Timer();
        timer.schedule(new JittererTask(new float[]{0, 0}), 0, 20); // every 20 ms update jitter
    }

    synchronized public void stopJitter() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    /**
     * Sets the pan and tilt servo values in radians with 0 being flat. Fires a
     * PANTILT_CHANGE if either value changes with a Point2D.Float of the new
     * pan and tilt values.
     *
     * @param pan in radians
     * @param tilt in radians
     */
    synchronized public void setPanTiltValues(float pan, float tilt) throws HardwareInterfaceException {
//        float[] old = getPanTiltHardware().getPanTiltValues();
        Point2D.Float old = new Point2D.Float(panValue, tiltValue);
        panValue = clipPanTilt(pan);
        tiltValue = clipPanTilt(tilt);
        getSupport().firePropertyChange(PANTILT_CHANGE, old, new Point2D.Float(panValue, tiltValue));
        getPanTiltHardware().setPanTiltValues(
                tilt2servo(panValue) + panOffset,
                tilt2servo(tiltValue) + tiltOffset);
    }

    private float clipPanTilt(float in) {
        float out = in;
        if (out > panTiltLimitRad) {
            out = panTiltLimitRad;
        } else if (out < -panTiltLimitRad) {
            out = -panTiltLimitRad;
        }
        return out;
    }

    /**
     * Input is desired table tilt in radians, output is actual servo interface
     * value 0-1 range. Tilt is clipped to +/- panTiltLimitRad.
     *
     * @param tiltRad in radians from 0 for flat
     * @return servo value.
     */
    private float tilt2servo(float tiltRad) {
        if (tiltRad > panTiltLimitRad) {
            tiltRad = panTiltLimitRad;
        } else if (tiltRad < -panTiltLimitRad) {
            tiltRad = panTiltLimitRad;
        }
        float f = .5f + knob2arm(tiltRad * servoUnitPerServoArmAngleRad);
//        System.out.println("tiltDeg="+(tiltRad*180f/3.14f)+" servo="+(f-.5f));
        return f;
    }

    /**
     * converts from desired angle of table knob to needed servo arm angle
     * value, based on geometry of arm connected by rod to table knob and fact
     * that servo turns 120 deg when servo software value ranges from 0 to 1.
     *
     * @param knob
     * @return servo value ranging over
     */
    private float knob2arm(float knob) {
        final float SERVO_ARM_KNOB_RADIUS_RATIO = 2f / 1f; // affects the actual angle produced.
        float arm = (float) Math.asin(SERVO_ARM_KNOB_RADIUS_RATIO * knob);
        return arm;
    }

    public void setServoInterface(ServoInterface servo) {
        getPanTiltHardware().setServoInterface(servo);
    }

    public ServoInterface getServoInterface() {
        return getPanTiltHardware().getServoInterface();
    }

    public void setLaserEnabled(boolean yes) {
        getPanTiltHardware().setLaserEnabled(yes);
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (yes) {
            panTiltHardware.setPanServoNumber(panServoNumber);
            panTiltHardware.setTiltServoNumber(tiltServoNumber);
            panTiltHardware.setJitterAmplitude(jitterAmplitude);
            panTiltHardware.setJitterFreqHz(jitterFreqHz);
            panTiltHardware.setJitterEnabled(jitterEnabled);
            panTiltHardware.setPanInverted(invertPan);
            panTiltHardware.setTiltInverted(invertTilt);
        } else {
            try {
                panTiltHardware.stopJitter();
                if (panTiltHardware.getServoInterface() != null) {
                    panTiltHardware.getServoInterface().disableAllServos();
                }
                panTiltHardware.close();
            } catch (Exception ex) {
                log.warning(ex.toString());
            }
        }
    }
    java.util.Timer timer;

    /**
     * @return the jitterEnabled
     */
    public boolean isJitterEnabled() {
        return jitterEnabled;
    }

    /**
     * @param jitterEnabled the jitterEnabled to set
     */
    synchronized public void setJitterEnabled(boolean jitterEnabled) {
        this.jitterEnabled = jitterEnabled;
        putBoolean("jitterEnabled", jitterEnabled);
        panTiltHardware.setJitterEnabled(jitterEnabled);
    }

    private class JittererTask extends TimerTask {

        int delayMs = 1000;
        float low = 0;
        float high = 1;
        Random r = new Random();
        float[] pantiltvalues;
        long startTime = System.currentTimeMillis();

        JittererTask(float[] ptv) {
            super();
            pantiltvalues = ptv;
        }

        @Override
        public void run() {
            long t = System.currentTimeMillis() - startTime;
            double phase = Math.PI * 2 * ((double) t / 1000) * jitterFreqHz;
            float dx = (float) (jitterAmplitude * Math.sin(phase));
            float dy = (float) (jitterAmplitude * Math.cos(phase));
//            System.out.println("t="+t+" phase="+(phase/2/Math.PI)+" dx,dy="+dx+", "+dy);
            try {
                setPanTiltValues(pantiltvalues[0] + dx, pantiltvalues[1] + dy);
            } catch (HardwareInterfaceException ex) {
            }
        }
    }

    public void setPanServoNumber(int panServoNumber) {
        if (panServoNumber < 0) {
            panServoNumber = 0;
        } else if (panServoNumber > 3) {
            panServoNumber = 3;
        }
        panTiltHardware.setPanServoNumber(panServoNumber);
        putInt("panServoNumber", panServoNumber);
    }

    public void setTiltServoNumber(int tiltServoNumber) {
        if (tiltServoNumber < 0) {
            tiltServoNumber = 0;
        } else if (tiltServoNumber > 3) {
            tiltServoNumber = 3;
        }
        panTiltHardware.setTiltServoNumber(tiltServoNumber);
        putInt("tiltServoNumber", tiltServoNumber);
    }

    public void setTiltInverted(boolean tiltInverted) {
        panTiltHardware.setTiltInverted(tiltInverted);
        putBoolean("invertTilt", tiltInverted);
    }

    public void setPanInverted(boolean panInverted) {
        panTiltHardware.setPanInverted(panInverted);
        putBoolean("invertPan", panInverted);
    }

    public boolean isTiltInverted() {
        return panTiltHardware.isTiltInverted();
    }

    public boolean isPanInverted() {
        return panTiltHardware.isPanInverted();
    }

    public int getTiltServoNumber() {
        return panTiltHardware.getTiltServoNumber();
    }

    public int getPanServoNumber() {
        return panTiltHardware.getPanServoNumber();
    }

    /**
     * @return the panTiltLimitRad
     */
    public float getPanTiltLimitRad() {
        return panTiltLimitRad;
    }

    /**
     * @param panTiltLimitRad the panTiltLimitRad to set
     */
    public void setPanTiltLimitRad(float panTiltLimitRad) {
        if (panTiltLimitRad < 0) {
            panTiltLimitRad = 0;
        } else if (panTiltLimitRad > 0.5f) {
            panTiltLimitRad = 0.5f;
        }
        this.panTiltLimitRad = panTiltLimitRad;
        putFloat("panTiltLimitRad", panTiltLimitRad);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * @return the panOffset
     */
    public float getPanOffset() {
        return panOffset;
    }

    /**
     * @param panOffset the panOffset to set
     */
    public void setPanOffset(float panOffset) {
        this.panOffset = panOffset;
        putFloat("panOffset", panOffset);
        try {
            setPanTiltValues(panValue, tiltValue);
        } catch (HardwareInterfaceException ex) {
        }
    }

    /**
     * @return the tiltOffset
     */
    public float getTiltOffset() {
        return tiltOffset;
    }

    /**
     * @param tiltOffset the tiltOffset to set
     */
    public void setTiltOffset(float tiltOffset) {
        this.tiltOffset = tiltOffset;
        putFloat("tiltOffset", tiltOffset);
        try {
            setPanTiltValues(panValue, tiltValue);
        } catch (HardwareInterfaceException ex) {
        }
    }

    public void setPanTiltChange() {
        // for FilterPanel to avoid warnings
    }
}
