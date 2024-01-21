/*
 * Driver.java
 *
 * Created on February 27, 2007, 9:51 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright February 27, 2007 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */
package ch.unizh.ini.jaer.projects.rccar;

import java.awt.Graphics2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.logging.Logger;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventio.AESocket;
import net.sf.jaer.eventprocessing.EventFilter;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.eventprocessing.filter.OnOffProximityLineFilter;
import net.sf.jaer.eventprocessing.filter.RotateFilter;
import net.sf.jaer.eventprocessing.filter.XYTypeFilter;
import net.sf.jaer.eventprocessing.label.SimpleOrientationFilter;
import net.sf.jaer.eventprocessing.tracking.HoughLineTracker;
import net.sf.jaer.eventprocessing.tracking.LineDetector;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.toradex.ToradexOakG3AxisAccelerationSensor;
import net.sf.jaer.hardwareinterface.usb.toradex.ToradexOakG3AxisAccelerationSensor.Acceleration;
import net.sf.jaer.hardwareinterface.usb.toradex.ToradexOakG3AxisAccelerationSensorGUI;
import net.sf.jaer.util.TobiLogger;
import net.sf.jaer.util.filter.LowpassFilter;


/**
 * Drives the RC car using the output from an enclosed line detector filter.
The enclosed filter chain is a MultiLineClusterTracker which itself encloses
a chain of XYTypeFilter - BackgroundActivityFilter - OnOffProximityFilter - SimpleOrientationFilter.
<p>
Preference values for these enclosed filters are stored by keys based on the enclosing Driver filter.
<p>
Driver uses the detected line to control steering and speed to drive along the line.
The line is parameritized by its normal form (rho, theta), where rho is the angle of the normal to the
line from the center of the chip image and theta is the length of the normal vector (closest passge to
the origin at the chip image center). The angle rho is 0 or 180 for a vertical line and is 90 or 270
for a horizontal line.
<p>
(rho,theta) control the steering
<p>
Fires PropertyChangeEvent for the following
<ul>
<li> "sendControlToBlenderEnabled" - when the control signaling to blender is enabled
</ul>
 * @author tobi
 */
public class Driver extends EventFilter2D implements FrameAnnotater {

    /** This filter chain is a common preprocessor for Driver line detectors */
    public class DriverPreFilter extends EventFilter2D implements PropertyChangeListener {

        private SimpleOrientationFilter oriFilter;
        private OnOffProximityLineFilter lineFilter;
        private BackgroundActivityFilter backgroundFilter;
        private XYTypeFilter xyTypeFilter;
        private RotateFilter rotateFilter;
        FilterChain filterChain;

        public DriverPreFilter(AEChip chip) {
            super(chip);

            // DriverPreFilter has a filter chain but DriverPreFilter overrides setFilterEnabled
            // so that it has private settings for the preferred enabled states of the enclosed
            // filters in the filter chain

            filterChain = new FilterChain(chip);
            xyTypeFilter = new XYTypeFilter(chip);
            oriFilter = new SimpleOrientationFilter(chip);
            backgroundFilter = new BackgroundActivityFilter(chip);
            lineFilter = new OnOffProximityLineFilter(chip);
            rotateFilter = new RotateFilter(chip);

            xyTypeFilter.setEnclosed(true, this);
            oriFilter.setEnclosed(true, this);
            backgroundFilter.setEnclosed(true, this);
            lineFilter.setEnclosed(true, this);
            rotateFilter.setEnclosed(true, this);

            xyTypeFilter.getSupport().addPropertyChangeListener("filterEnabled", this);
            oriFilter.getSupport().addPropertyChangeListener("filterEnabled", this);
            backgroundFilter.getSupport().addPropertyChangeListener("filterEnabled", this);
            lineFilter.getSupport().addPropertyChangeListener("filterEnabled", this);
            rotateFilter.getSupport().addPropertyChangeListener("filterEnabled", this);

            filterChain.add(rotateFilter);
            filterChain.add(xyTypeFilter);
            filterChain.add(backgroundFilter);
            filterChain.add(lineFilter);
            filterChain.add(oriFilter);

            setEnclosedFilterEnabledAccordingToPref(rotateFilter, null);
            setEnclosedFilterEnabledAccordingToPref(xyTypeFilter, null);
            setEnclosedFilterEnabledAccordingToPref(oriFilter, null);
            setEnclosedFilterEnabledAccordingToPref(backgroundFilter, null);
            setEnclosedFilterEnabledAccordingToPref(lineFilter, null);
            setEnclosedFilterChain(filterChain);
        }

        @Override
		public void propertyChange(PropertyChangeEvent evt) {
            if (!evt.getPropertyName().equals("filterEnabled")) {
                return;
            }
            try {
                setEnclosedFilterEnabledAccordingToPref((EventFilter) (evt.getSource()), (Boolean) (evt.getNewValue()));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /** Sets the given filter enabled state according to a
        private key based on DriverPreFilter
        and the enclosed filter class name.
        If enb is null, filter enabled state is set according
        to stored preferenc value.
        Otherwise, filter is set enabled according to enb.
         */
        private void setEnclosedFilterEnabledAccordingToPref(EventFilter filter, Boolean enb) {
            String key = "DriverPreFilter." + filter.getClass().getSimpleName() + ".filterEnabled";
            if (enb == null) {
                // set according to preference
                boolean en = getPrefs().getBoolean(key, true); // default enabled
                filter.setFilterEnabled(en);
            } else {
                boolean en = enb.booleanValue();
                getPrefs().putBoolean(key, en);
            }
        }

        @Override
		public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
            if (!isFilterEnabled()) {
                return in;
            }
            return filterChain.filterPacket(in);
        }

        public Object getFilterState() {
            return null;
        }

        @Override
		public void resetFilter() {
            filterChain.reset();
        }

        @Override
		public void initFilter() {
            filterChain.reset();
        }

        /** Overrides to avoid setting preferences for the enclosed filters */
        @Override
        public synchronized void setFilterEnabled(boolean yes) {
            this.filterEnabled = yes;
            getPrefs().putBoolean("filterEnabled", yes);
        }

        @Override
        public synchronized boolean isFilterEnabled() {
            return true; // force active
        }
    }
    static Logger log = Logger.getLogger("net.sf.jaer");
    private SiLabsC8051F320_USBIO_CarServoController servo;
    private LowpassFilter steeringFilter = new LowpassFilter(); // steering command filter
    private LowpassFilter steerAngleFilter = new LowpassFilter(); // steer angle filter, same as above but yulia's
    private float offsetGain = getPrefs().getFloat("Driver.offsetGain", 0.005f);
    /** Maximum constant speed of car under automatic driving via Driver property. Limited for safety. */
    public static final float MAX_SPEED = 0.05f;

    {
        setPropertyTooltip("offsetGain", "gain for moving back to the line");
    }
    private float angleGain = getPrefs().getFloat("Driver.angleGain", 0.5f);

    {
        setPropertyTooltip("angleGain", "gain for aligning with the line");
    }
//    private float lpCornerFreqHz=getPrefs().getFloat("Driver.lpCornerFreqHz",1);
//    {setPropertyTooltip("lpCornerFreqHz","corner freq in Hz for steering control");}
    private EventFilter2D lineTracker;
    private float steerInstantaneous = 0.5f; // instantaneous value, before filtering
    private float steerCommand = 0.5f; // actual command, as modified by filtering
    private float speed;
    private int sizex;
    private float radioSteer = 0.5f,  radioSpeed = 0.5f;
//    private float speedGain=getPrefs().getFloat("Driver.speedGain",1);
//    {setPropertyTooltip("speedGain","gain for reducing steering with speed");}
    private boolean flipSteering = getPrefs().getBoolean("Driver.flipSteering", true);

    {
        setPropertyTooltip("flipSteering", "flips the steering command for use with mirrored scene");
    }
    private boolean useMultiLineTracker = getPrefs().getBoolean("Driver.useMultiLineTracker", true);

    {
        setPropertyTooltip("useMultiLineTracker", "enable to use MultiLineClusterTracker, disable to use HoughLineTracker");
    }
    private float tauDynMs = getPrefs().getFloat("Driver.tauDynMs", 100);

    {
        setPropertyTooltip("tauDynMs", "time constant in ms for driving to far-away line");
    }
    private float defaultSpeed = getPrefs().getFloat("Driver.defaultSpeed", 0f); // speed of car when filter is turned on

    {
        setPropertyTooltip("defaultSpeed", "Car will drive with this fwd speed when filter is enabled");
    }
    private boolean sendControlToBlenderEnabled = true;

    {
        setPropertyTooltip("sendControlToBlenderEnabled", "sends steering (controlled) and speed (from radio) to albert's blender client");
    }
    private boolean loggingEnabled = false;

    {
        setPropertyTooltip("loggingEnabled", "enables logging to driverLog.txt in startup folder (java)");
    }
    private boolean showAccelerometerGUI = false;

    {
        setPropertyTooltip("showAccelerometerGUI", "shows the GUI output for the accelerometer");
    }
    int lastt = 0;
    DrivingController controller;
    private ToradexOakG3AxisAccelerationSensor accelerometer;
    private ToradexOakG3AxisAccelerationSensorGUI acceleromterGUI=null;

    TobiLogger tobiLogger = new TobiLogger("driverLog", "#data from Driver\n#timems radioSpeed radioSteering accelTime xAccel yAccel zAccel");

    /** Creates a new instance of Driver */
    public Driver(AEChip chip) {
        super(chip);
        initFilter();
        controller = new DrivingController();
        accelerometer = new ToradexOakG3AxisAccelerationSensor();
        try {
            accelerometer.open();
        } catch (HardwareInterfaceException ex) {
            log.warning(ex.toString());
        }

    }

    private class DrivingController {

        void control(EventPacket in) {
            // getString values from radio receiver (user sets speed or steers)
            if (servo != null) {
                radioSteer = servo.getRadioSteer();
                radioSpeed = servo.getRadioSpeed();
            }
            if (accelerometer != null) {
                Acceleration accel = accelerometer.getAcceleration();
            }
            sizex = getChip().getSizeX();// must do this here in case chip has changed
            // compute instantaneous position of line according to hough line tracker (which has its own lowpass filter)
            double rhoPixels = ((LineDetector) lineTracker).getRhoPixelsFiltered();
            // distance of line from center of image, could be negative for example for line of 30 deg running up to lower right of origin

            double thetaRad = (float) (Math.toRadians(((LineDetector) lineTracker).getThetaDegFiltered()));

            // we want to control to theta=0, rho=0
            // we change the cut to -pi/2 to pi/2 instead of 0,pi for better control around atractor now at theta=0.
            // after this theta>0 for line angled to left, theta=0 for vertical line (not normal to line), theta<0 for line to right
            if (thetaRad > (Math.PI / 2)) {
                thetaRad -= Math.PI;
            }
            // same thing with rho, we want to control rho=0 so now rho>0 for line to right of centerline and rho<0 to left, instead
            // of rho>0 above centerline. now rho won't flip sign under normal driving.
            if (thetaRad < 0) {
                rhoPixels = -rhoPixels;
            }

            float deltaTMs = (in.getLastTimestamp() - lastt) / 1000f; // time ms since last packet
            lastt = in.getLastTimestamp();

            // attractor set on line
            steerInstantaneous = steerInstantaneous + (float) ((deltaTMs / tauDynMs) * (((-steerInstantaneous + 0.5f) - (offsetGain * rhoPixels)) + (angleGain * thetaRad)));
            if (steerInstantaneous < 0) {
//                log.info("steerInstantaneous was reset from "+steerInstantaneous+" to 0");
                steerInstantaneous = 0;
            }
            if (steerInstantaneous > 1) {
//                log.info("steerInstantaneous was reset from "+steerInstantaneous+" to 1");
                steerInstantaneous = 1;
            }

//            float speedFactor=(radioSpeed-0.5f)*speedGain; // is zero for halted, positive for fwd, negative for reverse
//            if(speedFactor<0)
//                speedFactor= 0;
//            else if(speedFactor<0.1f) {
//                speedFactor=10; // going slowly, limit factor
//            }else
//                speedFactor=1/speedFactor; // faster, then reduce steering more
            steerCommand = steerInstantaneous;
            if ((servo != null) && servo.isOpen()) {
                servo.setSteering(getSteerCommand()); // 1 steer right, 0 steer left
                servo.setSpeed(getDefaultSpeed() + 0.5f); // set fwd speed
            }
            // send controls over socket to blender
            sendControlToBlender();
        }
    }

    /** Applies the enclosed LineDetector filter to extract the lowpassed dominant line feature
    in the scene, then computes steering and speed based on the filter output.
    The driver only controls the car speed via the throttle control, but the speed is reduced automatically
    by the controller according to the controller steering command (more steer = lower speed).
    The instantaneous steering command is based on the horizontal position of the line in the scene; if the line
    is to the right, steer left, and vice versa. In addition, the instantaneous steering command is lowpass filtered
    to produce the actual steering command.
    @param in the input packet
    @return the output packet, which is the output of the enclosed filter chain.
     */
    @Override
	public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        // debug, for state estimation
        tobiLogger.log(radioSpeed + " " + radioSteer + " " + accelerometer.getAcceleration());
        if (!isFilterEnabled()) {
            return in;
        }
        if (!isSendControlToBlenderEnabled()) {
            checkServo();
        } // don't bother with servo if in simulation
//        in=getEnclosedFilterChain().filterPacket(in);
        in = getEnclosedFilter().filterPacket(in);

        int n = in.getSize();
        if (n == 0) {
            return in;
        }

        controller.control(in);

        return in;
    }
    long lastWarningMessageTime = System.currentTimeMillis();

    private void checkServo() {
        if (servo == null) {
            servo = new SiLabsC8051F320_USBIO_CarServoController();
        }
        if (!servo.isOpen()) {
            try {
                servo.open();
            } catch (HardwareInterfaceException e) {
                if (System.currentTimeMillis() > (lastWarningMessageTime + 20000)) {
                    log.warning(e.getMessage());
                    lastWarningMessageTime = System.currentTimeMillis();
                }
            }
        }
    }

    public Object getFilterState() {
        return null;
    }

    @Override
	public void resetFilter() {
        getEnclosedFilter().resetFilter();
        steerInstantaneous = 0.5f; // reset steering to middle
    }

    @Override
	synchronized public void initFilter() {
//        steeringFilter.set3dBFreqHz(lpCornerFreqHz);
        lineTracker = null;
        
            lineTracker = new HoughLineTracker(chip);
        lineTracker.setEnclosedFilter(new DriverPreFilter(chip));
//        lineTracker.getEnclosedFilter().setEnclosed(true, lineTracker);
        setEnclosedFilter(lineTracker);
    }

    public void annotate(float[][][] frame) {
    }

    public void annotate(Graphics2D g) {
    }
    GLU glu = null;
    GLUquadric wheelQuad;

    @Override
	public void annotate(GLAutoDrawable drawable) {
        if (!isAnnotationEnabled()) {
            return;
        }

//        ((FrameAnnotater)lineTracker).annotate(drawable);

        GL2 gl = drawable.getGL().getGL2();
        if (gl == null) {
            return;
        }
        final int radius = 30;

        // draw steering wheel
        if (glu == null) {
            glu = new GLU();
        }
        if (wheelQuad == null) {
            wheelQuad = glu.gluNewQuadric();
        }
        gl.glPushMatrix();
        {
            gl.glTranslatef(chip.getSizeX() / 2, (chip.getSizeY()) / 2, 0);
            gl.glLineWidth(6f);
            glu.gluQuadricDrawStyle(wheelQuad, GLU.GLU_FILL);
            glu.gluDisk(wheelQuad, radius, radius + 1, 16, 1);
        }
        gl.glPopMatrix();

        // draw steering vector, including external radio input value

        gl.glPushMatrix();
        {
            gl.glColor3f(1, 1, 1);
            gl.glTranslatef(chip.getSizeX() / 2, chip.getSizeY() / 2, 0);
            gl.glLineWidth(6f);
            gl.glBegin(GL.GL_LINES);
            {
                gl.glVertex2f(0, 0);
                double a = 2 * (getSteerCommand() - 0.5f); // -1 to 1
                a = Math.atan(a);
                float x = radius * (float) Math.sin(a);
                float y = radius * (float) Math.cos(a);
                gl.glVertex2f(x, y);
                if ((servo != null) && servo.isOpen()) {
                    gl.glColor3f(1, 0, 0);
                    gl.glVertex2f(0, 0);
                    a = 2 * (radioSteer - 0.5f); // -1 to 1
                    a = Math.atan(a);
                    x = radius * (float) Math.sin(a);
                    y = radius * (float) Math.cos(a);
                    gl.glVertex2f(x, y);
                }
            }
            gl.glEnd();
        }
        gl.glPopMatrix();

        // draw external speed value
        if ((servo != null) && servo.isOpen()) {
            gl.glPushMatrix();
            {
                gl.glColor3f(1, 1, 1);
                gl.glTranslatef(1, chip.getSizeY() / 2, 0);
                gl.glLineWidth(15f);
                gl.glBegin(GL.GL_LINES);
                {
                    gl.glVertex2f(0, 0);
                    gl.glVertex2f(0, chip.getSizeY() * (radioSpeed - 0.5f));
                }
                gl.glEnd();
            }
            gl.glPopMatrix();
        }


    }

    public float getOffsetGain() {
        return offsetGain;
    }

    /** Sets steering offsetGain */
    public void setOffsetGain(float offsetGain) {
        this.offsetGain = offsetGain;
        getPrefs().putFloat("Driver.offsetGain", offsetGain);
    }

    public float getAngleGain() {
        return angleGain;
    }

    /** Sets steering angleGain */
    public void setAngleGain(float angleGain) {
        this.angleGain = angleGain;
        getPrefs().putFloat("Driver.angleGain", angleGain);
    }

//    public float getLpCornerFreqHz() {
//        return lpCornerFreqHz;
//    }
//
//    public void setLpCornerFreqHz(float lpCornerFreqHz) {
//        this.lpCornerFreqHz = lpCornerFreqHz;
//        getPrefs().putFloat("Driver.lpCornerFreqHz",lpCornerFreqHz);
//        steeringFilter.set3dBFreqHz(lpCornerFreqHz);
//    }
    public boolean isFlipSteering() {
        return flipSteering;
    }

    /** If set true, then drive towards events (road is textured), if false, drive away from events (side is textured). */
    public void setFlipSteering(boolean flipSteering) {
        this.flipSteering = flipSteering;
        getPrefs().putBoolean("Driver.flipSteering", flipSteering);
    }

//    public float getSpeedGain() {
//        return speedGain;
//    }
//
//    /** Sets the gain for reducing steering with speed.
//     The higher this value, the more steering is reduced by speed.
//     @param speedGain - higher is more reduction in steering with speed
//     */
//    public void setSpeedGain(float speedGain) {
//        if(speedGain<1e-1f) speedGain=1e-1f; else if(speedGain>100) speedGain=100;
//        this.speedGain = speedGain;
//        getPrefs().putFloat("Driver.speedGain",speedGain);
//    }
    /** Gets the actual steering command based on flipSteering
     */
    public float getSteerCommand() {
        if (flipSteering) {
            return 1 - steerCommand;
        }
        return steerCommand;
    }

    public boolean isUseMultiLineTracker() {
        return useMultiLineTracker;
    }

    synchronized public void setUseMultiLineTracker(boolean useMultiLineTracker) {
        boolean init = useMultiLineTracker != this.useMultiLineTracker;
        this.useMultiLineTracker = useMultiLineTracker;
        if (init) {
            initFilter(); // must rebuild enclosed filter
            if (getChip().getFilterFrame() != null) {
                getChip().getFilterFrame().rebuildContents(); // new enclosed filter, rebuild gui
            }
        }
        getPrefs().putBoolean("Driver.useMultiLineTracker", useMultiLineTracker);
    }

//        /** Overrides to set enclosed filters enabled according to prefs.
//     When this is enabled, all enclosed
//     filters are automatically enabled, thus generating
//     propertyChangeEvents and setting the prefs.
//     To getString around this we set the flag for filterEnabled and
//     don't call the super which sets the enclosed filter chain enabled.
//     */
//    @Override public void setFilterEnabled(boolean yes) {
//        if(!isEnclosed()){
//            String key=prefsEnabledKey();
//            getPrefs().putBoolean(key, yes);
//        }
//        getSupport().firePropertyChange("filterEnabled",new Boolean(filterEnabled),new Boolean(yes));
////        setEnclosedFilterEnabledAccordingToPref(xyTypeFilter,null);
////        setEnclosedFilterEnabledAccordingToPref(oriFilter,null);
////        setEnclosedFilterEnabledAccordingToPref(backgroundFilter,null);
////        setEnclosedFilterEnabledAccordingToPref(lineFilter,null);
//        filterEnabled=yes;
//    }
    public float getTauDynMs() {
        return tauDynMs;
    }

    public void setTauDynMs(float tauDynMs) {
        this.tauDynMs = tauDynMs;
        getPrefs().putFloat("Driver.tauDynMs", tauDynMs);
    }

    public float getDefaultSpeed() {
        return defaultSpeed;
    }

    public void setDefaultSpeed(float defaultSpeed) {
        if (defaultSpeed < 0) {
            defaultSpeed = 0;
        } else if (defaultSpeed > MAX_SPEED) {
            defaultSpeed = MAX_SPEED;
        }
        this.defaultSpeed = defaultSpeed;
        getPrefs().putFloat("Driver.defaultSpeed", defaultSpeed);
    }
    DataOutputStream dos;

    /** sends current speed and steering to alberto cardona's blender over the a stream socket opened to blender
     */
    synchronized private void sendControlToBlender() {
        if (!sendControlToBlenderEnabled) {
            return;
        }
        try {
            if (dos == null) {
                net.sf.jaer.graphics.AEViewer v = chip.getAeViewer();
                if (v == null) {
                    throw new RuntimeException("no viewer");
                }
                AESocket aeSocket = v.getAeSocket();
                if (aeSocket == null) {
                    throw new RuntimeException("no aeSocket has been opened to a server of events, no one to send controls to");
                }
                Socket s = aeSocket.getSocket();
                if (s == null) {
                    throw new RuntimeException("socket inside AESocket is null, something funny");
                }
                dos = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
            }
            dos.writeFloat(2f); // header for albert
            dos.writeFloat(getSteerCommand());
            dos.writeFloat(radioSpeed);
            dos.flush();
//            System.out.println("sent controls steer="+getSteerCommand());
        } catch (Exception e) {
            log.warning(e.toString() + ": disabling sendControlToBlenderEnabled");
            sendControlToBlenderEnabled = false;
            getSupport().firePropertyChange("sendControlToBlenderEnabled", true, false);
        }
    }

    public boolean isSendControlToBlenderEnabled() {
        return sendControlToBlenderEnabled;
    }

    synchronized public void setSendControlToBlenderEnabled(boolean sendControlToBlenderEnabled) {
        this.sendControlToBlenderEnabled = sendControlToBlenderEnabled;
        if (!sendControlToBlenderEnabled) {
            if (dos != null) {
                // don't close the outputstream (which would close that of the socket),
                // just set the thin wrapper to null as a flag to recreate it later (Albert)
                dos = null;
            }
        }
    }

    public boolean isLoggingEnabled() {
        return loggingEnabled;
    }

    public void setLoggingEnabled(boolean loggingEnabled) {
        this.loggingEnabled = loggingEnabled;
        tobiLogger.setEnabled(loggingEnabled);
    }

    public boolean isShowAccelerometerGUI() {
        return showAccelerometerGUI;
    }

    public void setShowAccelerometerGUI(boolean showAccelerometerGUI) {
        this.showAccelerometerGUI = showAccelerometerGUI;
        if (showAccelerometerGUI) {
            if(acceleromterGUI==null){
                acceleromterGUI=new ToradexOakG3AxisAccelerationSensorGUI(accelerometer);
            }
            acceleromterGUI.setVisible(true);
        }else{
            if(acceleromterGUI!=null) {
				acceleromterGUI.setVisible(false);
			}
        }
    }
    }

