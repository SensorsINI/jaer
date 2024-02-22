package ch.unizh.ini.jaer.projects.tobi.goalie;

/*
 * ServoArm.java
 *
 * Created on April 24, 2007, 3:08 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
import java.awt.Color;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.JAERViewer;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.filter.XYTypeFilter;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.JAERDataViewer;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.ServoInterface;
import net.sf.jaer.hardwareinterface.usb.ServoInterfaceFactory;
import net.sf.jaer.hardwareinterface.usb.silabs.SiLabsC8051F320_USBIO_ServoController;
import net.sf.jaer.util.PlayWavFile;
import net.sf.jaer.util.filter.LowpassFilter;

import com.jogamp.opengl.util.gl2.GLUT;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import javax.swing.JFrame;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.util.chart.Axis;
import net.sf.jaer.util.chart.Category;
import net.sf.jaer.util.chart.Series;
import net.sf.jaer.util.chart.XYChart;

/**
 * Controls the servo arm in StereoGoalie to decouple the motor actions from the
 * sensory processing and manages self-calibration of the arm. This arm can also
 * be controlled directly with visual feedback control using the tracked arm
 * position. In this mode, the servo command is formed from an error signal that
 * is the difference between the actual position and the desired position. The
 * actual arm position comes from the cluster tracker that tracks the arm, while
 * the desired position is the pixel position that the arm should go to.
 *
 * @author malang, tobi
 */
public class ServoArm extends EventFilter2D implements Observer, FrameAnnotater/*
 * ,
 * PnPNotifyInterface
 */ {

    JFrame samplesFrame = null;
    // constants

    public Object learningLock = new Object();
    // Hardware Control

    private ServoInterface servo = null;
    private int position; // state position of arm in image space (pixels)

    private boolean learningFailed = false; // set true if we've given up trying
    // to learn (something mechanically
    // or optically wrong with setup)

	// private Timer EndPositionTimer = new Timer();
	// learning model parameters
    // linear y = k * x + d
    private float learned_k, learned_d;
    private LearningStates learningState;
    private final float SERVO_PULSE_FREQ_DEFAULT = 180f;
    final int POINTS_TO_REGRESS_INITIAL = 10, POINTS_TO_REGRESS_ADDITIONAL = 5, POINTS_TO_REGRESS_MAX = 20;
    final int POINTS_TO_CHECK = 5; // for checking learning

    private final int LEARN_POSITION_DELAY_MS = 200; // settling time ms

    private final int SWEEP_DELAY_MS = 250; // arm does sweep at start to
    // capture tracking from any noise,
    // this is delay between sweep left
    // and sweep right

    private final int SWEEP_COUNT = 3; // arm, sweeps this many times to capture
    // tracking from noisy input

    final float SHAKE_AMOUNT = 0.004f; // 1/2 total amount to shake by out of
    // 0-1 range

    final int SHAKE_COUNT = 10; // 1/2 total number of shakes

    final int SHAKE_PAUSE_MS = 70;
    final int NUM_LEARNING_ATTEMPTS = 5 * POINTS_TO_REGRESS_MAX; // max number
    // of points
    // to
    // collect
    // to try to
    // learn
    // before
    // manual
    // reset
    // required

    private float learningLeftSamplingBoundary = getPrefs().getFloat("ServoArm.learningLeftSamplingBoundary", 0.3f);

    {
        setPropertyTooltip("learningLeftSamplingBoundary",
                "sets limit for learning to contrain learning to linear region near center");
    }
    private float learningRightSamplingBoundary = getPrefs().getFloat("ServoArm.learningRightSamplingBoundary", 0.6f);

    {
        setPropertyTooltip("learningRightSamplingBoundary",
                "sets limit for learning to contrain learning to linear region near center");
    }
    private float servoLimitLeft = getPrefs().getFloat("ServoArm.servoLimitLeft", .45f);

    {
        setPropertyTooltip("servoLimitLeft", "sets hard limit on left servo position for mechanical safety");
    }
    private float servoLimitRight = getPrefs().getFloat("ServoArm.servoLimitRight", .55f);

    {
        setPropertyTooltip("servoLimitRight", "sets hard limit on left servo position for mechanical safety");
    }
    private boolean realtimeLoggingEnabled = false; // getPrefs().getBoolean("ServoArm.realtimeLogging",
    // false);

    {
        setPropertyTooltip("realtimeLogging", "send desired and actual position to data window");
    }
    private float servoPulseFreqHz = getPrefs().getFloat("ServoArm.servoPulseFreqHz", SERVO_PULSE_FREQ_DEFAULT);

    {
        setPropertyTooltip("servoPulseFreqHz",
                "the desired pulse frequency rate for servo control (limited by hardware)");
    }
    private float acceptableAccuracyPixels = getPrefs().getFloat("ServoArm.acceptableAccuracyPixels", 5);

    {
        setPropertyTooltip("acceptableAccuracyPixels", "acceptable error of arm after learning");
    }
    private boolean visualFeedbackControlEnabled = getPrefs()
            .getBoolean("ServoArm.visualFeedbackControlEnabled", false);

    {
        setPropertyTooltip("visualFeedbackControlEnabled", "enables direct visual feedback control of arm");
    }
    private float visualFeedbackProportionalGain = getPrefs().getFloat("ServoArm.visualFeedbackProportionalGain", 1);

    {
        setPropertyTooltip(
                "visualFeedbackProportionalGain",
                "under visual feedback control, the pixel error in servo position is multiplied by this factor to form the change in servo motor command");
    }
    private float visualFeedbackPIDControllerTauMs = getPrefs()
            .getFloat("ServoArm.visualFeedbackPIDControllerTauMs", 5);

    {
        setPropertyTooltip("visualFeedbackPIDControllerTauMs",
                "time constant in ms of visual feedback PID controller IIR low- and high-pass filters");
    }
    private int servoNumber = getInt("servoNumber", 0); // the servo number on
    // the controller

    {
        setPropertyTooltip("servoNumber", "index of servo of arm");
    }

	// learning
    private LearningTask learningTask;
    private Thread learningThread;

	// logging
    private LoggingThread loggingThread;
    private ServoArmState state;
    private float servoValue = 0.5f; // last value written to servo

    VisualFeedbackController visualFeedbackController = null;
    int lastTimestamp = 0;

    private RectangularClusterTracker armTracker;
    private XYTypeFilter xyfilter;

    public boolean isVisualFeedbackControlEnabled() {
        return visualFeedbackControlEnabled;
    }

    public void setVisualFeedbackControlEnabled(boolean visualFeedbackControlEnabled) {
        this.visualFeedbackControlEnabled = visualFeedbackControlEnabled;
        getPrefs().putBoolean("ServoArm.visualFeedbackControlEnabled", visualFeedbackControlEnabled);
    }

    public float getVisualFeedbackProportionalGain() {
        return visualFeedbackProportionalGain;
    }

    public void setVisualFeedbackProportionalGain(float visualFeedbackProportionalGain) {
        this.visualFeedbackProportionalGain = visualFeedbackProportionalGain;
        getPrefs().putFloat("ServoArm.visualFeedbackProportionalGain", visualFeedbackProportionalGain);
    }

    public float getVisualFeedbackPIDControllerTauMs() {
        return visualFeedbackPIDControllerTauMs;
    }

    public void setVisualFeedbackPIDControllerTauMs(float visualFeedbackPIDControllerTauMs) {
        this.visualFeedbackPIDControllerTauMs = visualFeedbackPIDControllerTauMs;
        getPrefs().putFloat("ServoArm.visualFeedbackPIDControllerTauMs", visualFeedbackPIDControllerTauMs);
        if (visualFeedbackController == null) {
            visualFeedbackController = new VisualFeedbackController();
        }
        visualFeedbackController.setTauMs(visualFeedbackPIDControllerTauMs);
    }

    public ServoInterface getServoInterface() {
        return servo;
    }

    public void setServoInterface(ServoInterface servo) {
        this.servo = servo;
    }

    /**
     * @return the servoNumber
     */
    public int getServoNumber() {
        return servoNumber;
    }

    /**
     * @param servoNumber the servoNumber to set
     */
    public void setServoNumber(int servoNumber) {
        if (servoNumber < 0) {
            servoNumber = 0;
        } else if (servoNumber > 3) {
            servoNumber = 3;
        }
        this.servoNumber = servoNumber;
        putInt("servoNumber", servoNumber);
    }

    private enum LearningStates {

        notlearning,
        learning,
        stoplearning
    }

    private enum ServoArmState {

        relaxed,
        active,
        learning
    }

	// PnPNotify pnp;
    private boolean playedSound = false;

    /**
     * Creates a new instance of ServoArm
     */
    public ServoArm(AEChip chip) {
        super(chip);
        chip.addObserver(this); // to getString chip sizes correct in initFilter

        armTracker = new RectangularClusterTracker(chip);
        setEnclosedFilter(armTracker); // to avoid storing enabled prefs for
        // this filter set it to be the enclosed
        // filter before enabling

        // only bottom filter
        xyfilter = new XYTypeFilter(chip);
        xyfilter.setXEnabled(true);
        xyfilter.setYEnabled(true);
        armTracker.setEnclosedFilter(xyfilter); // to avoid storing enabled
        // prefs for this filter set it
        // to be the enclosed filter for
        // tracker before enabling it

		// TODO tobi commented out because when running chip in applet we don't
        // have USBIO but we might have the goalie in the chip's preferred
        // filters.
		// if(UsbIoUtilities.usbIoIsAvailable){
        // pnp=new PnPNotify(this);
        // pnp.enablePnPNotification(SiLabsC8051F320_USBIO_ServoController.GUID);
        // }
        state = ServoArmState.relaxed;
    }

    @Override
    protected void finalize() throws Throwable {
        closeHardware();
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!isFilterEnabled()) {
            return in;
        }
        if (in == null) {
            return in;
        }
        if (in.getSize() > 0) {
            lastTimestamp = in.getLastTimestamp();
        }
        if (enclosedFilter != null) {
            in = enclosedFilter.filterPacket(in);
        }
        synchronized (armTracker) {
            armTracker.filterPacket(in);
        }
        if ((servo != null) && servo.isOpen()) {
            if (((servo.getPort2() & 1) == 1) && !playedSound) {
                // log.info("*********************blocked !!!!!!");
                soundPlayer.playRandom();
                playedSound = true;
            }
            if (servo.getPort2() == 0) {
                playedSound = false;
            }
        }
        return in;
    }

    Random random = new Random();

    public Object getFilterState() {
        return null;
    }

    @Override
    public void resetFilter() {
        armTracker.resetFilter();
    }

    @Override
    public void initFilter() {
        initLearning();
        ((XYTypeFilter) armTracker.getEnclosedFilter()).setTypeEnabled(false);
        this.setCaptureRange(0, 0, chip.getSizeX(), 0);
        armTracker.setMaxNumClusters(1);
		// armTracker.setAspectRatio(1.2f);
        // armTracker.setClusterSize(0.2f);
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes);
        if (yes) {
            if (realtimeLoggingEnabled) {
                startLogging();
            }
            startLearning();
        } else {
            relax();
            closeHardware();
        }

    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (!isAnnotationEnabled()) {
            return;
        }
        armTracker.annotate(drawable);
        ((XYTypeFilter) armTracker.getEnclosedFilter()).annotate(drawable);
        GL2 gl = drawable.getGL().getGL2();
        switch (state) {
            case active:
                gl.glColor3d(1.0, 0.0, 0.0);
                break;
            case relaxed:
                gl.glColor3d(0.0, 0.0, 1.0);
                break;
            case learning:
                gl.glColor3d(0.0, 1.0, 0.0);
                break;
        }

        gl.glPushMatrix();
        int font = GLUT.BITMAP_HELVETICA_18;
        gl.glRasterPos3f((chip.getSizeX() / 2) - 15, 3, 0);

        // annotate the cluster with the arm state, e.g. relaxed or learning
        chip.getCanvas().getGlut().glutBitmapString(font, state.toString());

        gl.glPopMatrix();

    }

    private int getposition_lastpos = -1;

    /**
     * A tracker tracks the arm; this method returns the arm position.
     *
     * @return arm x position in image space, or the last measurement if no arm
     * is tracked
     */
    public synchronized int getActualPosition() {
        if ((armTracker.getNumClusters() > 0) && armTracker.getClusters().get(0).isVisible()) {
            getposition_lastpos = (int) armTracker.getClusters().get(0).location.x;
        }
        return getposition_lastpos;
    }

    /**
     * The desired position of the arm
     */
    public int getDesiredPosition() {
        return position;
    }

    /**
     * Sets the arm position in pixel space. Also immediately aborts learning
     * and sets the <code>state</code> to be <code>ServoArmState.active</code>.
     *
     * @param position the position of the arm in pixels in image space in the x
     * coordinate (along the bottom of the scene in the arm tracking region).
     */
    public void setPosition(int position) {
        stopLearning();
		// if we limit the arm position here, we cannot block balls outside our
        // own view.... don't do this.
        // arm position is limited by servoLimitLeft and servoLimitRight which
        // user sets by hand using GUI
        state = ServoArmState.active;
        setPositionDirect(position);
    }

    /**
     * Sets the position without affecting state, using the learned pixel to
     * servo mapping.
     *
     * @param position the position of the arm in image space (pixels)
     */
    private void setPositionDirect(int position) {
        // check if hardware is still valid
        checkHardware();
        if (!isVisualFeedbackControlEnabled()) {
			// under direct control, the motor position is set to the calibrated
            // value
            // calculate motor output from desired input
            float motor = getOutputFromPosition(position);
            setServo(motor);
            this.position = position;
        } else {
            if (visualFeedbackController == null) {
                visualFeedbackController = new VisualFeedbackController();
            }
            visualFeedbackController.setServo(position, lastTimestamp);
        }
    }

    public void relax() {
        if (state == ServoArmState.learning) {
            stopLearning(); // warning: recursion!
            // stopLearning has done the relax already.
            // so we are finished now. (yes that is a kind
            // of hack)
            return;
        }

        // do it in all cases (important for stopLearning)
        if (state != ServoArmState.relaxed) {
            state = ServoArmState.relaxed;
        }

        checkHardware(); // but do not connect if we are not connected
        disableServo();
    }

	// default parameters map pixel servo=k*pixel+d so pixel 0 goes to .21,
    // pixel 127 goes to 0.81
    final float DEFAULT_K = 1 / 500f, // 1f/210,
            DEFAULT_D = 0; // .21f;

    /**
     * resets parameters in case they are off someplace weird that results in no
     * arm movement, e.g. k=0
     */
    void resetLearning() {
        // setLearnedParam(DEFAULT_K,DEFAULT_D);
        if (learningTask != null) {
            learningTask.pointHistory.clear();
        }
        setLearningFailed(false);
    }

	// learning algorithm (and learning thread control)
    private void initLearning() {
        learned_k = getPrefs().getFloat("ServoArm.learned_k", DEFAULT_K);
        if (Float.isNaN(learned_k)) {
            log.warning("reset learned_k from NaN to default");
            learned_k = DEFAULT_K;
        }
        learned_d = getPrefs().getFloat("ServoArm.learned_d", DEFAULT_D);
        if (Float.isNaN(learned_d)) {
            log.warning("reset learned_d from NaN to default");
            learned_d = DEFAULT_D;
        }
    }

    private void setLearnedParam(float k, float d) {
        synchronized (learningLock) {
            learned_k = k;
            learned_d = d;
        }
        if (Float.isNaN(learned_k)) {
            log.warning("learned k (slope) is NaN");
        }
        if (Float.isNaN(learned_d)) {
            log.warning("learned d (intercept) is NaN");
        }

        getPrefs().putFloat("ServoArm.learned_k", learned_k);
        getPrefs().putFloat("ServoArm.learned_d", learned_d);
    }

    /**
     * Applies the learned model to return the learned motor value from the
     * desired pixel position of the arm.
     *
     * @param position the desired position in pixels [0,chip.getSizeX()-1]
     * @return the learned motor value to move to this position in servo units
     * [0,1]
     */
    public float getOutputFromPosition(int position) {
        synchronized (learningLock) {
			// return (float)(0.39 + Math.asin(((double)position - 64.0)/
            // -120.0));
            return (learned_k * position) + learned_d;
        }
    }

    /**
     * Computes the pixel position of the arm from the given servo setting using
     * current learning parameters
     *
     * @param output pixel position of arm
     * @return resulting pixel using current parameter
	 *
     */
    private int getPositionFromOutput(float output) {
        return Math.round((output - learned_d) / learned_k);
    }

    public void stopLearning() {
        if (state != ServoArmState.learning) {
            return;
        }

        // tell the thread to stop
        synchronized (learningLock) {
            learningState = LearningStates.stoplearning;
        }

        if (learningThread != null) {

            // wake our thread in case it is sleeping
            learningThread.interrupt();

            // wait for the tread to be finsished
            try {
                if (learningThread.isAlive()) {
                    learningThread.join();
                }
            } catch (InterruptedException ex) {
            }
        }

		// relax is the state we change to after learning
        // state should not be learning for relax()
        state = ServoArmState.relaxed;

        relax();
    }

    public void startLearning() {
        if (chip.getAeViewer().getPlayMode() != AEViewer.PlayMode.LIVE) {
            return;
        } // don't learn if not live
        if (learningFailed) {
            log.warning("cannot start learning because learning flagged as failed; learning must be manually reset");
            return;
        }
		// set father goalie immediately to SLEEPING state to discourage noise
        // from stopping learning
        if(getGoalie()!=null) getGoalie().setState(Goalie.State.SLEEPING);
        synchronized (learningLock) {
            if (state == ServoArmState.learning) {
                return;
            }

            learningState = LearningStates.learning;
            state = ServoArmState.learning;
        }

        if (learningTask == null) {
            learningTask = new LearningTask(this);
        }

        learningThread = new Thread(learningTask);
        learningThread.setName("LearningThread");
        // set parameter for this learning task
        learningTask.leftBoundary = getLearningLeftSamplingBoundary();
        learningTask.rightBoundary = getLearningRightSamplingBoundary();
        learningThread.start();
    }

    public void startLogging() {
        stopLogging();

        try {
            loggingThread = new LoggingThread(this, 20, "ServoArmLogging.csv"); // logs
            // to
            // default
            // folder
            // which
            // is
            // java
            // (startup
            // folder)
        } catch (Exception ex) {
            ex.printStackTrace();

            return;
        }

        loggingThread.start();
        realtimeLoggingEnabled = true;
    }

    public void stopLogging() {
        if (loggingThread == null) {
            return;
        }

        loggingThread.exit = true;
        loggingThread.interrupt();
        realtimeLoggingEnabled = false;
    }

    private synchronized boolean checkHardware() {
        if ((servo == null) || !servo.isOpen()) // return false; // leave servo
        // assignment to higher level so
        // it can be shared
        {
            if (ServoInterfaceFactory.instance().getNumInterfacesAvailable() == 0) {
                return false;
            }
            try {
                servo = (ServoInterface) (ServoInterfaceFactory.instance().getInterface(0));
                if (servo == null) {
                    return false;
                }
                servo.open();
                if (servo instanceof SiLabsC8051F320_USBIO_ServoController) {
                    SiLabsC8051F320_USBIO_ServoController silabs = (SiLabsC8051F320_USBIO_ServoController) servo;
                }
            } catch (HardwareInterfaceException e) {
                servo = null;
                log.warning(e.toString());
                return false;
            }
        }
        return true;
    }

    private void closeHardware() {
        if (servo != null) {
            servo.close();
            servo = null;
        }
    }

	// public void SetPort2ValueSet(int i){
    // ServoInterface p=(ServoInterface)servo;
    // p.setPort2(i);
    // }
    /**
     * sets goalie arm.
     *
     * @param f 1 for far right, 0 for far left as viewed from above, i.e. from
     * retina. If f is NaN, then the arm is set to 0.5f (around the middle).
     */
    synchronized private void setServo(float f) {

        // check for hardware limits
        if (f < servoLimitLeft) {
            f = servoLimitLeft;
        } else if (f > servoLimitRight) {
            f = servoLimitRight;
        } else if (Float.isNaN(f)) {
            f = 0.5f;
            log.warning("tried to set servo to NaN, setting to 0.5f");
        }

        // System.out.println(String.format("t= %d in= %5.2f out= %5.2f",timestamp,f,goaliePosition));
        if (servo != null) {
            try {
                ServoInterface s = servo;
				// if(JAERViewer.globalTime2 == 0)
                // JAERViewer.globalTime2 = System.nanoTime();
                s.setServoValue(getServoNumber(), f); // servo is servo 1 for
                // goalie
                setLastServoSetting(f);
                // System.out.println('.');
            } catch (HardwareInterfaceException e) {
                e.printStackTrace();
            }
        }

        // lastServoPositionTime=System.currentTimeMillis();
    }

    private void disableServo() {
        if (servo == null) {
            return;
        }

        try {
            ServoInterface s = servo;

            s.disableServo(getServoNumber());
        } catch (HardwareInterfaceException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sets the arm capture range for tracking
     */
    void setCaptureRange(int startx, int starty, int endx, int endy) {
        XYTypeFilter xyt = ((XYTypeFilter) armTracker.getEnclosedFilter());
        xyt.setStartX(startx);
        xyt.setEndX(endx);
        xyt.setStartY(starty);
        xyt.setEndY(endy);
        // the goalie gets the rest of the scene for ball tracking
        Goalie g = getGoalie();
        if (g != null) {
            XYTypeFilter f = g.getXYFilter();
            if (f != null) {
                f.setStartY(endy);
                f.setEndY(chip.getSizeY());
                f.setStartX(startx);
                f.setEndX(endx);
            }
        }
    }

    public float getLearningLeftSamplingBoundary() {
        return this.learningLeftSamplingBoundary;
    }

    public void setLearningLeftSamplingBoundary(float value) {
        if (learningLeftSamplingBoundary < 0) {
            learningLeftSamplingBoundary = 0;
        } else if (learningLeftSamplingBoundary > learningRightSamplingBoundary) {
            learningLeftSamplingBoundary = learningRightSamplingBoundary;
        }
        learningLeftSamplingBoundary = value;
        getPrefs().putFloat("ServoArm.learningLeftSamplingBoundary", value);
        return;
    }

    public float getLearningRightSamplingBoundary() {
        return this.learningRightSamplingBoundary;
    }

    public void setLearningRightSamplingBoundary(float value) {
        if (learningRightSamplingBoundary < learningLeftSamplingBoundary) {
            learningRightSamplingBoundary = learningLeftSamplingBoundary;
        } else if (learningRightSamplingBoundary > 1) {
            learningRightSamplingBoundary = 1;
        }
        learningRightSamplingBoundary = value;
        getPrefs().putFloat("ServoArm.learningRightSamplingBoundary", value);
        return;
    }

    public float getServoLimitLeft() {
        return servoLimitLeft;
    }

    public void setServoLimitLeft(float servoLimitLeft) {
        if (servoLimitLeft < 0) {
            servoLimitLeft = 0;
        } else if (servoLimitLeft > servoLimitRight) {
            servoLimitLeft = servoLimitRight;
        }
        this.servoLimitLeft = servoLimitLeft;
        getPrefs().putFloat("ServoArm.servoLimitLeft", servoLimitLeft);
        stopLearning();
        disableGoalieTrackerMomentarily();
        setServo(servoLimitLeft); // to check value
    }

    private void disableGoalieTrackerMomentarily() {
        Goalie g = getGoalie();
        if (g != null) {
            RectangularClusterTracker f = g.getTracker();
            f.setFilterEnabled(false);
            f.resetFilter();
            Timer t = new Timer();
            t.schedule(new RenableFilterTask(f), 2000);
        }
    }

    private Goalie getGoalie() {
        if (getEnclosingFilter() instanceof Goalie) {
            Goalie g = (Goalie) getEnclosingFilter();
            return g;
        }
        log.warning("enclosing filter is " + getEnclosedFilter()
                + " which is not instanceof StereoGoalie - returning null instance");
        return null;
    }

    class RenableFilterTask extends TimerTask {

        EventFilter f;

        RenableFilterTask(EventFilter f) {
            this.f = f;
        }

        @Override
        public void run() {
            f.setFilterEnabled(true);
        }
    }

    public float getServoLimitRight() {
        return servoLimitRight;
    }

    public void setServoLimitRight(float servoLimitRight) {
        if (servoLimitRight < servoLimitLeft) {
            servoLimitRight = servoLimitLeft;
        } else if (servoLimitRight > 1) {
            servoLimitRight = 1;
        }
        this.servoLimitRight = servoLimitRight;
        getPrefs().putFloat("ServoArm.servoLimitRight", servoLimitRight);
        stopLearning();
        disableGoalieTrackerMomentarily();
        setServo(servoLimitRight); // to check value
    }

    public boolean isRealtimeLogging() {
        return realtimeLoggingEnabled;
    }

    public void setRealtimeLogging(boolean v) {
        if (v) {
            startLogging();
        } else {
            stopLogging();
        }

		// getPrefs().putBoolean("ServoArm.realtimeLogging",v);
    }

    public void onAdd() {
    }

    public synchronized void onRemove() {
        servo = null;
    }

    @Override
    public void update(Observable o, Object arg) {
        initFilter();
    }

	// filter actions
    public void doShowSamples() {
        ArrayList<Double> x = new ArrayList<Double>();
        ArrayList<Double> y = new ArrayList<Double>();

        if(learningTask==null){
            log.warning("no learning to show");
            return;
        }
        learningTask.getSamples(x, y);
        XYChart chart = new XYChart("learning samples");

        if (samplesFrame == null) {
            samplesFrame = new JFrame("samples");
        }
        samplesFrame.setPreferredSize(new Dimension(400, 200));
        Container pane = samplesFrame.getContentPane();
        int nSamples = x.size();
        Series series = new Series(2, nSamples);
        for (int i = 0; i < nSamples; i++) {
            series.add((float) (x.get(i).doubleValue()), (float) (y.get(i).doubleValue()));
        }
        Axis xAxis = new Axis(-1, 1);
        Axis yAxis = new Axis(-1, 1);
        Category category = new Category(series, new Axis[]{xAxis, yAxis});

        category.setColor(new float[]{1f, 1f, 1f}); // white for visibility
        category.setLineWidth(3f);

        chart = new XYChart("ISIs");
        chart.setBackground(Color.black);
        chart.setForeground(Color.white);
        chart.setGridEnabled(false);
        chart.addCategory(category);

        pane.setLayout(new BorderLayout());
        pane.add(chart, BorderLayout.CENTER);

        samplesFrame.setVisible(true);
    }

	// threads and tasks
	// private class EndPositionTask extends TimerTask {
    // private ServoArm father;
    // private float motor;
    // private int position;
    // private int precondition;
    //
    // /** Creates a new EndPositionTask
    // * @param father the ServoArm that owns the servo
    // * @param precondition ??
    // * @param position the desired position ??
    // * @param motor the servo position [0-1]
    // */
    // public EndPositionTask(ServoArm father, int precondition, int position,
    // float motor) {
    // this.motor = motor;
    // this.father = father;
    // this.position = position;
    // this.precondition = precondition;
    // }
    //
    // public void run() {
    // //only set new endposition if we still have
    // // the right intermediate position
    // if(father.position == precondition) {
    // father.position = position;
    // father.setServoInterface(motor);
    // }
    // }
    // }
    /**
     * This Runnable does the calibration of the arm
     */
    private class LearningTask implements Runnable {

        class Point {

            public double x, y;
        }

        private ServoArm father;
        LinkedList<Point> pointHistory = new LinkedList<Point>();
        public float leftBoundary = 0.45f;
        public float rightBoundary = 0.55f;

        public LearningTask(ServoArm father) {
            this.father = father;
            // this.learningState = learningState;
        }

		// learning attempts to collect enough points for a good fit that allows
        // accuracy test to pass
        @Override
        public void run() {
            int next = 0;
            checkHardware();
            try {
                if ((getServoInterface() != null) && getServoInterface().isOpen()) {
                    log.info("sweeping servo to capture tracking");
                    for (int i = 0; i < SWEEP_COUNT; i++) {
                        father.setServo(0);
                        // wait for the motor to move
                        synchronized (Thread.currentThread()) {
                            Thread.currentThread().wait(SWEEP_DELAY_MS);
                        }
                        father.setServo(1);
                        synchronized (Thread.currentThread()) {
                            Thread.currentThread().wait(SWEEP_DELAY_MS);
                        }
                    }
                }
            } catch (InterruptedException ex) {
                log.info("learning interrupted during sweep");
            }
            for (int attemptNumber = 0; attemptNumber < NUM_LEARNING_ATTEMPTS; attemptNumber++) {
				// check if we should exit thread
                // log.info("learning attempt sample #"+(1+attemptNumber)+"/"+NUM_LEARNING_ATTEMPTS);
                synchronized (father.learningLock) {
                    if (father.learningState == LearningStates.stoplearning) {
                        father.learningState = LearningStates.notlearning;
                        log.info("stopped learning");
                        return;
                    }
                }

                // do the regression if we have enough samples
                if (next == 0) {
                    try {
                        if (isAccurate()) {
                            father.learningState = LearningStates.notlearning;
                            father.state = ServoArmState.relaxed;
                            father.relax();
                            return;
                        }
                    } catch (InterruptedException ex) {
                        log.info("checking interrupted: " + ex.toString());
                        continue; // go up to the exit if (no code replication)
                    }

                    if (pointHistory.size() > POINTS_TO_REGRESS_INITIAL) {
                        log.info("got " + pointHistory.size() + " points, doing regression");
                        doLinearRegression();
                        next = POINTS_TO_REGRESS_ADDITIONAL;
                    } else {
                        next = POINTS_TO_REGRESS_INITIAL + 1;
                    }

                } else {
                    next--;
                }

                try {

                    // random number between left and right boundary
                    Point p = new Point();
                    p.y = (Math.random() * (servoLimitRight - servoLimitLeft)) + servoLimitLeft;
                    checkHardware();
                    if ((getServoInterface() != null) && getServoInterface().isOpen()) {
                        father.setServo((float) p.y);

                        // wait for the motor to move
                        synchronized (Thread.currentThread()) {
                            Thread.currentThread().wait(LEARN_POSITION_DELAY_MS);
                        }
                        // getString the captured position
                        p.x = measureArmPosition((float) p.y);
						// log.info("learning: set servo="+p.y+", read position x="+p.x);

                        // stop motor
                        father.disableServo();

                        // add point to list; max elements in list
                        if (pointHistory.size() > POINTS_TO_REGRESS_MAX) {
                            pointHistory.removeFirst();
                        }

                        pointHistory.addLast(p);
                        log.info("added learning point attempt #" + attemptNumber + " to result in pixel=" + p.x
                                + " for servo=" + p.y);
                    }

                } catch (InterruptedException e) {
					// we were interrupted. so just check
                    // next time if we have to exit
                }
            }
            log.warning("learning didn't finish after NUM_LEARNING_ATTEMPTS=" + NUM_LEARNING_ATTEMPTS
                    + ", learning failed, disabling until restart or manual reset");
            learningFailed = true;
        }

        private void doLinearRegression() {
            double ux = 0, uy = 0;
            int n = 0;
            double sx = 0, sxy = 0;
            Iterator<Point> it;
			// ArrayList<Double> logx = new ArrayList();
            // ArrayList<Double> logy = new ArrayList();

			// caclulate ux and uy, the mean x (input servo setting 0-1 range)
            // and y (output measured arm cluster location pixels)
            StringBuilder sb = new StringBuilder();
            for (it = pointHistory.iterator(); it.hasNext();) {
                Point p = it.next();

                ux += p.x;
                uy += p.y;
				// logx.add(p.x);
                // logy.add(p.y);
                sb.append(String.format("%f\t%f\n", p.x, p.y));
                n++;
            }
			// log.info(sb.toString());

			// JAERViewer.GlobalDataViewer.addDataSet("Servo Arm Mapping", logx,
            // logy);
            ux /= n;
            uy /= n;
			// calculate sx and sy, the summed variance of x and the cross
            // variance of y with x
            for (it = pointHistory.iterator(); it.hasNext();) {
                Point p = it.next();

                sx += (p.x - ux) * (p.x - ux);
                sxy += (p.x - ux) * (p.y - uy);
            }

            // calculate and set linear paramters
            father.setLearnedParam((float) (sxy / sx), (float) (uy - ((sxy / sx) * ux)));

            log.info(String.format("learned mapping from arm pixel x to servo motor setting y is\n   y=%f*x+%f\n",
                    father.learned_k, father.learned_d));

        }

        private boolean isAccurate() throws InterruptedException {
            log.info("checking learning, points set,meas,err:");
            // okay lets see how good we are
            int n;
            int error = 0;
            float xmin = 30;
            float xmax = father.chip.getSizeX() - 30;
            float dx = (xmax - xmin) / POINTS_TO_CHECK;
            for (n = 0; n < POINTS_TO_CHECK; n++) {
                int pointToCheck = (int) (xmin + (n * dx)); // choose a pixel x
                // position for arm

                int measuredPoint = measureArmPosition(getOutputFromPosition(pointToCheck)); // putString
                // the
                // arm
                // there,
                // wiggle
                // it,
                // and
                // measure
                // the
                // arm
                // cluster
                // location
                int thisErr = Math.abs(pointToCheck - measuredPoint);
                error += thisErr;
                System.out.println(String.format("%d,%d,%d", pointToCheck, measuredPoint, thisErr));
            }

            if ((error / POINTS_TO_CHECK) < getAcceptableAccuracyPixels()) {
                log.info("learning OK: \navg abs error=" + (error / POINTS_TO_CHECK) + " pixels");
                return true;
            } else {
                log.warning("learning *NOT* OK: \navg abs error=" + (error / POINTS_TO_CHECK) + " pixels");
                return false;
            }
        }

        /**
         * Shakes the arm a bit and reads the tracked arm postion from the
         * tracker
         *
         * @param motpos the position in servo space
         */
        private int measureArmPosition(float motpos) throws InterruptedException {
            // shake around and read the position
            int position = 0;
            for (int i = 0; i < SHAKE_COUNT; i++) {
                father.setServo(motpos + SHAKE_AMOUNT);
                position += father.getActualPosition();
                sleep(SHAKE_PAUSE_MS);
                position += father.getActualPosition();
                father.setServo(motpos - SHAKE_AMOUNT);
                sleep(SHAKE_PAUSE_MS);
            }

            int ret = position / (2 * SHAKE_COUNT);
            // log.info("for motor position="+motpos+" read arm pixel postion="+ret);
            return ret;

        }

        private void sleep(long ms) throws InterruptedException {
            synchronized (Thread.currentThread()) {
                Thread.currentThread().wait(ms);
            }
            // stop learning as fast as possible when a ball is coming
            if (father.learningState == LearningStates.stoplearning) {
                throw (new InterruptedException());
            }
        }

        private void getSamples(ArrayList x, ArrayList y) {
            // we could have a race here
            for (Point p : pointHistory) {
                x.add(p.x);
                y.add(p.y);
            }
        }
    }

	// logging
    private class LoggingThread extends Thread {

        public boolean exit;
        private ServoArm father;
        private FileOutputStream file;
        private int interval;
        private long starttime;
        private ArrayList<Double> actPos;
        private ArrayList<Double> desPos;

        public LoggingThread(ServoArm father, int interval, String filename) throws IOException {
            file = new FileOutputStream(filename);
            this.interval = interval;
            this.father = father;
            exit = false;
            starttime = System.currentTimeMillis();
            actPos = new ArrayList();
            desPos = new ArrayList();
            setName("LoggingThread");
        }

        @Override
        public void run() {
            PrintStream p = new PrintStream(file);
            int i = 0;
            int clusterpos = -1;
            p.printf("# servo arm logging\n" + "# timems,actualPosition,desiredPosition\n");
            while (!exit) {
                try {
                    Thread.sleep(interval);
                    long t = System.currentTimeMillis() - starttime;
                    ;
                    float actualPosition = father.getActualPosition();
                    float desiredPosition = getPositionFromOutput(getLastServoSetting());
                    p.printf("%d,%g,%g\n", t, desiredPosition, actualPosition);

                    synchronized (actPos) {
                        if (actPos.size() > 20000) {
                            // save memory
                            actPos.clear();
                        }
                        actPos.add((double) actualPosition);
                    }
                    synchronized (desPos) {
                        if (desPos.size() > 20000) {
                            // save memory
                            desPos.clear();
                        }
                        desPos.add((double) desiredPosition);
                    }

                    if (i++ > 10) {
                        // p.flush();
                        i = 0;
                    }
                } catch (InterruptedException ex) {
                    break;
                } catch (Exception ex) {
                    ex.printStackTrace();

                    break;
                }
				// used to write out software timing
				/*
                 * if(JAERViewer.globalTime3 != 0) { //print out debug times
                 * p.print(JAERViewer.globalTime1); p.print(",");
                 * p.print(JAERViewer.globalTime2); p.print(",");
                 * p.print(JAERViewer.globalTime3); p.print(",");
                 * p.print(JAERViewer.globalTime2 - JAERViewer.globalTime1);
                 * p.print(","); p.print(JAERViewer.globalTime3 -
                 * JAERViewer.globalTime1); p.println(); JAERViewer.globalTime1
                 * = 0; JAERViewer.globalTime2 = 0; JAERViewer.globalTime3 = 0;
                 * }
                 */
            }
            try {
                file.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }

        }

        @Override
        protected void finalize() throws Throwable {
			// JAERViewer.GlobalDataViewer.removeDataSet("Actual Pos (StereoGoalie)");
            // JAERViewer.GlobalDataViewer.removeDataSet("Desired Pos (StereoGoalie)");
        }
    }

    public float getServoPulseFreqHz() {
        if ((servo != null) && (servo instanceof SiLabsC8051F320_USBIO_ServoController)) {
            float actualFreq = ((SiLabsC8051F320_USBIO_ServoController) servo).setServoPWMFrequencyHz(servoPulseFreqHz); // we
            // need
            // to
            // set
            // to
            // getString
            // it
            servoPulseFreqHz = actualFreq;
        }
        return servoPulseFreqHz;
    }

    public void setServoPulseFreqHz(float servoPulseFreqHz) {
        this.servoPulseFreqHz = servoPulseFreqHz;
        if ((servo != null) && (servo instanceof SiLabsC8051F320_USBIO_ServoController)) {
            float actualFreq = ((SiLabsC8051F320_USBIO_ServoController) servo).setServoPWMFrequencyHz(servoPulseFreqHz);
            servoPulseFreqHz = actualFreq;
        }
        // dont store in prefs to ensure max speed for now.
    }

    public float getAcceptableAccuracyPixels() {
        return acceptableAccuracyPixels;
    }

    public void setAcceptableAccuracyPixels(float acceptableAccuracyPixels) {
        if (acceptableAccuracyPixels > (chip.getSizeX() / 2)) {
            acceptableAccuracyPixels = chip.getSizeX() / 2;
        }
        this.acceptableAccuracyPixels = acceptableAccuracyPixels;
        getPrefs().putFloat("ServoArm.acceptableAccuracyPixels", acceptableAccuracyPixels);

    }

    /**
     * Returns the last servo command value, default 0.5f if nothing has been
     * commanded yet.
     */
    public float getLastServoSetting() {
        return servoValue;
    }

    private void setLastServoSetting(float lastServoSetting) {
        this.servoValue = lastServoSetting;
    }

    public RectangularClusterTracker getArmTracker() {
        return armTracker;
    }

    public boolean isLearningFailed() {
        return learningFailed;
    }

    public void setLearningFailed(boolean learningFailed) {
        this.learningFailed = learningFailed;
    }

    /**
     * Does PID control on arm using the desired and tracked position of arm.
     * The error signal err1 is the difference between actual and desired arm
     * positions and err1 is multiplied by the current learned value of gain
     * learned_k to form a signal that modifies the current servo command.
     */
    private class VisualFeedbackController {

        float desiredPosition = 0;
        private LowpassFilter errorLowpass = new LowpassFilter();

        VisualFeedbackController() {
            setTauMs(getVisualFeedbackPIDControllerTauMs());
        }

        /**
         * Moves the servo to the desired pixel position using visual feedback
         * control.
         *
         * @param position the x position in pixels
         * @param timestamp the last timestamp of a spike (used for temporal
         * filtering operations)
         */
        private void setServo(int position, int timestamp) {
            desiredPosition = position;
            int actPos = getActualPosition(); // from arm tracker
            int err = position - actPos; // if desired 'position' is larger (to
            // right) of actual position
            // 'actPos' than err is positive
            float lowpassError = errorLowpass.filter(err, timestamp);
            float correction = getVisualFeedbackProportionalGain() * lowpassError;
            float newMotor = getOutputFromPosition(Math.round(desiredPosition + correction));
            ServoArm.this.setServo(newMotor);
        }

        private void setTauMs(float visualFeedbackPIDControllerTauMs) {
            errorLowpass.setTauMs(visualFeedbackPIDControllerTauMs);
        }
    }

    private SoundPlayer soundPlayer = new SoundPlayer();

    private class SoundPlayer {

        // sound effects are located at root of jaer/sounds folder for goalie, currently. the root of jaer is the standard startup folder
        private String[] soundFiles = {"sounds/ow.wav", "sounds/oof.wav", "sounds/hah.wav", "sounds/oof.wav"};
        PlayWavFile player;
        Random r = new Random();
        PlayWavFile T;

        void playRandom() {
            if ((T != null) && T.isAlive()) {
                return;
            }
            T = new PlayWavFile(soundFiles[r.nextInt(soundFiles.length)]);
            T.start();
        }
    }
}
