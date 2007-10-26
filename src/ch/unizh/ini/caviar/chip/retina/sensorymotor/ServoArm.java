/*
 * ServoArm.java
 *
 * Created on April 24, 2007, 3:08 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */



package ch.unizh.ini.caviar.chip.retina.sensorymotor;

//~--- non-JDK imports --------------------------------------------------------

import ch.unizh.ini.caviar.JAERDataViewer;
import ch.unizh.ini.caviar.JAERViewer;
import ch.unizh.ini.caviar.aemonitor.AEConstants;
import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.eventprocessing.EventFilter;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.eventprocessing.filter.XYTypeFilter;
import ch.unizh.ini.caviar.eventprocessing.tracking.RectangularClusterTracker;
import ch.unizh.ini.caviar.graphics.*;
import ch.unizh.ini.caviar.graphics.FrameAnnotater;
import ch.unizh.ini.caviar.hardwareinterface.*;
import ch.unizh.ini.caviar.hardwareinterface.ServoInterface;
import ch.unizh.ini.caviar.hardwareinterface.usb.ServoInterfaceFactory;
import ch.unizh.ini.caviar.hardwareinterface.usb.ServoTest;
import ch.unizh.ini.caviar.hardwareinterface.usb.SiLabsC8051F320_USBIO_ServoController;
import ch.unizh.ini.caviar.hardwareinterface.usb.UsbIoUtilities;
import ch.unizh.ini.caviar.util.filter.LowpassFilter;
import com.sun.opengl.util.GLUT;
import de.thesycon.usbio.PnPNotify;
import de.thesycon.usbio.PnPNotifyInterface;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Observable;
import java.util.Observer;

import spread.NULLAuth;

//~--- JDK imports ------------------------------------------------------------

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;

import java.io.*;

import java.util.logging.Level;

import javax.media.opengl.*;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.*;

import java.util.Timer;
import java.util.TimerTask;
import java.util.Date;


/**
 * Controls the servo arm in Goalie to decouple the motor actions from the sensory processing.
 * @author malang
 */
public class ServoArm extends EventFilter2D implements Observer, FrameAnnotater,  PnPNotifyInterface {
    
    // constants
    private final int SERVO_NUMBER = 0;    // the servo number on the controller
    public Object     learningLock = new Object();
    
    // Hardware Control
    private ServoInterface servo              = null;
    private int               warningcount_servo = 1;
    private int               position; // state position of arm in image space (pixels)
//    private Timer             EndPositionTimer = new Timer();
    
    // learning model parameters
    // linear  y = k * x + d
    private float          learned_k, learned_d;
    private LearningStates learningState;
    
    private final float SERVO_PULSE_FREQ_DEFAULT=180f;
    
    private float learningLeftSamplingBoundary = getPrefs().getFloat("ServoArm.learningLeftSamplingBoundary",0.3f);
    {setPropertyTooltip("learningLeftSamplingBoundary","sets limit for learning to contrain learning to linear region near center");}
    private float learningRightSamplingBoundary = getPrefs().getFloat("ServoArm.learningRightSamplingBoundary",0.6f);
    {setPropertyTooltip("learningRightSamplingBoundary","sets limit for learning to contrain learning to linear region near center");}
    
    private float servoLimitLeft=getPrefs().getFloat("ServoArm.servoLimitLeft",0);
    {setPropertyTooltip("servoLimitLeft","sets hard limit on left servo position for mechanical safety");}
    private float servoLimitRight=getPrefs().getFloat("ServoArm.servoLimitRight",1);
    {setPropertyTooltip("servoLimitRight","sets hard limit on left servo position for mechanical safety");}
    
    private boolean realtimeLoggingEnabled = getPrefs().getBoolean("ServoArm.realtimeLogging", false);
    {setPropertyTooltip("realtimeLogging","send desired and actual position to data window");}
    
    private float servoPulseFreqHz=getPrefs().getFloat("ServoArm.servoPulseFreqHz",SERVO_PULSE_FREQ_DEFAULT);
    {setPropertyTooltip("servoPulseFreqHz","the desired pulse frequency rate for servo control (limited by hardware)");}
    
    private float acceptableAccuracyPixels=getPrefs().getFloat("ServoArm.acceptableAccuracyPixels",5);
    {setPropertyTooltip("acceptableAccuracyPixels","acceptable error of arm after learning");}
    
    // learning
    private LearningTask learningTask;
    private Thread learningThread;
    
    // logging
    private LoggingThread loggingThread;
    private ServoArmState state;
    
    private RectangularClusterTracker  tracker;
    
    private enum LearningStates { notlearning, learning, stoplearning }
    
    // common fields
    private enum ServoArmState { relaxed, active, learning }
    
    ;
    
    PnPNotify pnp;
    
    /** Creates a new instance of ServoArm */
    public ServoArm(AEChip chip) {
        super(chip);
        
        tracker = new RectangularClusterTracker(chip);
        setEnclosedFilter(tracker); // to avoid storing enabled prefs for this filter set it to be the enclosed filter before enabling
        
        // only bottom filter
        XYTypeFilter xyfilter = new XYTypeFilter(chip);
        tracker.setEnclosedFilter(xyfilter); // to avoid storing enabled prefs for this filter set it to be the enclosed filter for tracker before enabling it
        
//        tracker.setFilterEnabled(true);  // don't enable it, because enabling filter will enable enclosed filters automatically
        
//      tracker.setMaxNumClusters(NUM_CLUSTERS_DEFAULT); // ball will be closest object
        
        
//        xyfilter.setFilterEnabled(true); // don't enable it - enabling tracker will enable xyfilter
        
        chip.getCanvas().addAnnotator(this);
        if(UsbIoUtilities.usbIoIsAvailable){
            pnp=new PnPNotify(this);
            pnp.enablePnPNotification(SiLabsC8051F320_USBIO_ServoController.GUID);
        }
        
        state = state.relaxed;
    }
    
    protected void finalize() throws Throwable {
        super.finalize();
        closeHardware();
    }
    
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
        if(in==null) return in;
        if(enclosedFilter != null)
            in = enclosedFilter.filterPacket(in);
        // EventPacket relevant = tracker.getEnclosedFilter().filterPacket(in);
        synchronized (tracker) {
            tracker.filterPacket(in);
        }
        
        return in;
    }
    
    public Object getFilterState() {
        return null;
    }
    
    public void resetFilter() {
        
    }
    
    public void initFilter() {
        LearningInit();
        //XYTypeFilter xyfilter = ((XYTypeFilter) tracker.getEnclosedFilter());
        
        ((XYTypeFilter)tracker.getEnclosedFilter()).setTypeEnabled(false);
        this.setCaptureRange(0,0, chip.getSizeX(), 0);
        tracker.setMaxNumClusters(1);
        
        tracker.setAspectRatio(1.2f);
        tracker.setClusterSize(0.2f);
        
    }
    
    @Override public void setFilterEnabled(boolean yes){
        super.setFilterEnabled(yes);
        if(yes) {
            if(realtimeLoggingEnabled)
                startLogging();
            startLearning();
        } else {
            relax();
            closeHardware();
        }
        
    }
    
    public void annotate(float[][][] frame) {}
    
    public void annotate(Graphics2D g) {
        if(!isAnnotationEnabled()) return;
        tracker.annotate(g);
        ((XYTypeFilter) tracker.getEnclosedFilter()).annotate(g);
        
    }
    
    public void annotate(GLAutoDrawable drawable) {
        if( !isAnnotationEnabled()) return;
        tracker.annotate(drawable);
        ((XYTypeFilter) tracker.getEnclosedFilter()).annotate(drawable);
        GL gl = drawable.getGL();
        switch(state) {
            case active:
                gl.glColor3d(1.0,0.0,0.0);
                break;
            case relaxed:
                gl.glColor3d(0.0,0.0,1.0);
                break;
            case learning:
                gl.glColor3d(0.0,1.0,0.0);
                break;
        }
        
        gl.glPushMatrix();
        int font = GLUT.BITMAP_HELVETICA_18;
        gl.glRasterPos3f(chip.getSizeX() / 2-15, 3,0);
        
        // annotate the cluster with the arm state, e.g. relaxed or learning
        chip.getCanvas().getGlut().glutBitmapString(font, state.toString());
        
        gl.glPopMatrix();
        
    }
    
    private int getposition_lastpos = -1;
    
    /** A tracker tracks the arm; this method returns the arm position.
     * @return arm x position in image space, or the last measurement if no arm is tracked
     */
    public synchronized int getActualPosition() {
        if (tracker.getClusters().size() > 0 &&
                tracker.getClusters().get(0).isVisible()) {
            getposition_lastpos = (int) tracker.getClusters().get(0).location.x;
        }
        return getposition_lastpos;
    }
    
    /** The desired position of the arm */
    public int getDesiredPosition() {
        return position;
    }
    
    
    /**
     * Sets the arm position in pixel space. Also immediately aborts learning.
     *
     * @param position the position of the arm in pixels in image space
     */
    public void setPosition(int position) {
        stopLearning();
        // if we limit the arm position here, we cannot block balls outside our own view.... don't do this.
        // arm position is limited by servoLimitLeft and servoLimitRight which user sets by hand using GUI
        state = ServoArmState.active;
        setPositionDirect(position);
    }
    
    /**
     * Sets the position without affecting state, using the learned pixel to servo mapping.
     * @param position the position of the arm in image space (pixels)
     */
    private void setPositionDirect(int position) {
        // check if hardware is still valid
        checkHardware();
        // calculate motor output from desired input
        float motor = PositionToOutput(position);
        setServo(motor);
        this.position = position;
    }
    
    public void relax() {
        if (state == ServoArmState.learning) {
            stopLearning(); //warning: recursion!
            //stopLearning has done the relax already.
            // so we are finished now. (yes that is a kind
            // of hack)
            return;
        }
        
        //do it in all cases (important for stopLearning)
        if (state != state.relaxed) {
            state = state.relaxed;
        }
        
        checkHardware(false); //but do not connect if we are not connected
        disableServo();
    }
    
    // default parameters map pixel servo=k*pixel+d so pixel 0 goes to .21, pixel 127 goes to 0.81
    final float DEFAULT_K=1f/210,
            DEFAULT_D=.21f;
    
    /** resets parameters in case they are off someplace wierd that results in no arm movement, e.g. k=0 */
    void resetLearning(){
        setLearnedParam(DEFAULT_K,DEFAULT_D);
        if(learningTask!=null){
            learningTask.pointHistory.clear();
        }
    }
    
    // learning algorithm (and learning thread control)
    private void LearningInit() {
        learned_k = getPrefs().getFloat("ServoArm.learned_k", DEFAULT_K);
        learned_d = getPrefs().getFloat("ServoArm.learned_d", DEFAULT_D);
    }
    
    private void setLearnedParam(float k, float d) {
        synchronized (learningLock) {
            learned_k = k;
            learned_d = d;
        }
        if(Float.isNaN(learned_k)) log.warning("learned k (slope) is NaN");
        if(Float.isNaN(learned_d)) log.warning("learned d (intercept) is NaN");
        
        getPrefs().putFloat("ServoArm.learned_k", learned_k);
        getPrefs().putFloat("ServoArm.learned_d", learned_d);
    }
    
    /**
     * Applies the learned model to return the learned motor value from the desired pixel position of the arm.
     *
     * @param position the diseired postion in pixels [0,chip.getSizeX()-1]
     * @return the learned motor value to move to this postion in servo units [0,1]
     */
    public float PositionToOutput(int position) {
        synchronized (learningLock) {
            //return (float)(0.39 + Math.asin(((double)position - 64.0)/ -120.0));
            return learned_k * position + learned_d;
        }
    }
    
    public void stopLearning() {
        if (state != ServoArmState.learning) {
            return;
        }
        
        // tell the thread to stop
        synchronized (learningLock) {
            learningState = learningState.stoplearning;
        }
        
        if (learningThread != null) {
            
            // wake our thread in case it is sleeping
            learningThread.interrupt();
            
            // wait for the tread to be finsished
            try {
                if(learningThread.isAlive())
                    learningThread.join();
            } catch (InterruptedException ex) {}
        }
        
        //relax is the state we change to after learning
        //state should not be learning for relax()
        state = ServoArmState.relaxed;
        
        relax();
    }
    
    public void startLearning() {
        if(chip.getAeViewer().getPlayMode()!=AEViewer.PlayMode.LIVE) return;  // don't learn if not live
        synchronized (learningLock) {
            if (state == state.learning) {
                return;
            }
            
            learningState = learningState.learning;
            state         = state.learning;
        }
        
        if (learningTask == null) {
            learningTask = new LearningTask(this);
        }
        
        learningThread = new Thread(learningTask);
        learningThread.setName("LearningThread");
        //set parameter for this learning task
        learningTask.leftBoundary = getLearningLeftSamplingBoundary();
        learningTask.rightBoundary = getLearningRightSamplingBoundary();
        learningThread.start();
    }
    
    public void startLogging() {
        stopLogging();
        
        try {
            loggingThread = new LoggingThread(this, 20, "logging.txt"); // logs to default folder which is java (startup folder)
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
    
    
    // Hardware Control
    private synchronized void checkHardware() {
        checkHardware(true);
    }
    
    private synchronized boolean checkHardware(boolean doReconnect) {
        if(servo==null || !servo.isOpen()){
            if(ServoInterfaceFactory.instance().getNumInterfacesAvailable()==0){
                return false;
            }
            try{
                servo=(ServoInterface)(ServoInterfaceFactory.instance().getInterface(0));
                if(servo==null) return false;
                servo.open();
            }catch(HardwareInterfaceException e){
                servo=null;
                log.warning(e.toString());
                return false;
            }
        }
//        if (servo == null) {
//            try {
//                if(doReconnect)
//                    servo = (ServoInterface) ServoInterfaceFactory.instance().getFirstAvailableInterface();
//
//                if (servo == null ) {
//                    return false;
//                }
//            } catch (HardwareInterfaceException ex) {
//                ex.printStackTrace();
//                return false;
//            }
//        }
//
//        if (!servo.isOpen()) {
//            try {
//                if (doReconnect) {
//                    servo.open();
//                    ((SiLabsC8051F320_USBIO_ServoController)servo).setServoPWMFrequencyHz(servoPulseFreqHz);
//                } else
//                    return false;
//            } catch (HardwareInterfaceException e) {
//                servo = null;
//                //if (warningcount_servo++ % 1000 == 0) {
//                log.warning(e.toString());
//                //}
//                return false;
//            }
//        }
//        //okay evrything worked;
        return true;
    }
    
    private void closeHardware() {
        if(servo != null) {
            servo.close();
            servo = null;
        }
    }
    
    
    /**
     * sets goalie arm.
     * @param f 1 for far right, 0 for far left as viewed from above, i.e. from retina.
     * If f is NaN, then the arm is set to 0.5f (around the middle).
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
        
//      System.out.println(String.format("t= %d in= %5.2f out= %5.2f",timestamp,f,goaliePosition));
        if (servo != null) {
            try {
                ServoInterface s = (ServoInterface) servo;
                if(JAERViewer.globalTime2 == 0)
                    JAERViewer.globalTime2 = System.nanoTime();
                s.setServoValue(SERVO_NUMBER, 1 - f);    // servo is servo 1 for goalie
                //System.out.println('.');
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
            ServoInterface s = (ServoInterface) servo;
            
            s.disableServo(SERVO_NUMBER);
        } catch (HardwareInterfaceException e) {
            e.printStackTrace();
        }
    }
    
    void setCaptureRange(int startx, int starty, int endx, int endy) {
        XYTypeFilter xyt = ((XYTypeFilter)tracker.getEnclosedFilter());
        xyt.setStartX(startx);
        xyt.setEndX(endx);
        xyt.setStartY(starty);
        xyt.setEndY(endy);
    }
    
    public float getLearningLeftSamplingBoundary() {
        return this.learningLeftSamplingBoundary;
    }
    
    public void setLearningLeftSamplingBoundary(float value) {
        if(learningLeftSamplingBoundary<0)learningLeftSamplingBoundary=0; else if(learningLeftSamplingBoundary>learningRightSamplingBoundary) learningLeftSamplingBoundary=learningRightSamplingBoundary;
        learningLeftSamplingBoundary = value;
        getPrefs().putFloat("ServoArm.learningLeftSamplingBoundary", value);
        return;
    }
    
    public float getLearningRightSamplingBoundary() {
        return this.learningRightSamplingBoundary;
    }
    
    public void setLearningRightSamplingBoundary(float value) {
        if(learningRightSamplingBoundary<learningLeftSamplingBoundary) learningRightSamplingBoundary=learningLeftSamplingBoundary; else if(learningRightSamplingBoundary>1) learningRightSamplingBoundary=1;
        learningRightSamplingBoundary = value;
        getPrefs().putFloat("ServoArm.learningRightSamplingBoundary", value);
        return;
    }
    
    public float getServoLimitLeft() {
        return servoLimitLeft;
    }
    
    public void setServoLimitLeft(float servoLimitLeft) {
        if(servoLimitLeft<0) servoLimitLeft=0;else if(servoLimitLeft>servoLimitRight)servoLimitLeft=servoLimitRight;
        this.servoLimitLeft = servoLimitLeft;
        getPrefs().putFloat("ServoArm.servoLimitLeft",servoLimitLeft);
        stopLearning();
        disableGoalieTrackerMomentarily();
        setServo(servoLimitLeft); // to check value
    }
    
    private void disableGoalieTrackerMomentarily() {
        if(getEnclosingFilter() instanceof Goalie){
            Goalie g=(Goalie)getEnclosingFilter();
            RectangularClusterTracker f=g.getTracker();
            f.setFilterEnabled(false);
            f.resetFilter();
            Timer t=new Timer();
            t.schedule(new RenableFilterTask(f),2000);
        }
    }
    
    class RenableFilterTask extends TimerTask{
        EventFilter f;
        RenableFilterTask(EventFilter f){
            this.f=f;
        }
        public void run(){
            f.setFilterEnabled(true);
        }
    }
    
    public float getServoLimitRight() {
        return servoLimitRight;
    }
    
    public void setServoLimitRight(float servoLimitRight) {
        if(servoLimitRight<servoLimitLeft)servoLimitRight=servoLimitLeft;else if(servoLimitRight>1)servoLimitRight=1;
        this.servoLimitRight = servoLimitRight;
        getPrefs().putFloat("ServoArm.servoLimitRight",servoLimitRight);
        stopLearning();
        disableGoalieTrackerMomentarily();
        setServo(servoLimitRight); // to check value
    }
    
    public boolean isRealtimeLogging() {
        return realtimeLoggingEnabled;
    }
    
    public void setRealtimeLogging(boolean v) {
        if(v)
            startLogging();
        else
            stopLogging();
        
        getPrefs().putBoolean("ServoArm.realtimeLogging",v);
        
    }
    
    public void onAdd() {
    }
    
    public synchronized void onRemove() {
        servo=null;
    }
    
    public void update(Observable o, Object arg) {
        initFilter();
    }
    
//filter actions
    
    public void doShowSamples() {
        ArrayList<Double> x = new ArrayList<Double>();
        ArrayList<Double> y = new ArrayList<Double>();
        
        learningTask.getSamples(x,y);
        
        JAERViewer.GlobalDataViewer.addDataSet("Learning Samples",x,y,0,
                JAERDataViewer._DataType.XY,
                JAERDataViewer.LineStyle.Point,
                Color.RED);
        
        JAERViewer.GlobalDataViewer.setVisible(true);
        
    }
    
// threads and tasks
    
//    private class EndPositionTask extends TimerTask {
//        private ServoArm father;
//        private float motor;
//        private int position;
//        private int precondition;
//
//        /** Creates a new EndPositionTask
//         * @param father the ServoArm that owns the servo
//         * @param precondition ??
//         * @param position the desired position ??
//         * @param motor the servo position [0-1]
//         */
//        public EndPositionTask(ServoArm father, int precondition, int position, float motor) {
//            this.motor = motor;
//            this.father = father;
//            this.position = position;
//            this.precondition = precondition;
//        }
//
//        public void run() {
//            //only set new endposition if we still have
//            // the right intermediate position
//            if(father.position == precondition) {
//                father.position = position;
//                father.setServo(motor);
//            }
//        }
//    }
    
    /** This Runnable does the calibration of the arm */
    private class LearningTask implements Runnable {
        final int POINTS_TO_REGRESS_INITIAL=10, POINTS_TO_REGRESS_ADDITIONAL=5, POINTS_TO_REGRESS_MAX=20;
        final int POINTS_TO_CHECK=5; // for checking learning
        private final int LEARN_POSITION_DELAY_MS=400; // settling time ms
        private final int SWEEP_DELAY_MS=250; // arm does sweep at start to capture tracking from any noise, this is delay between sweep left and sweep right
        private final int SWEEP_COUNT=3; // arm sweeps this many times to capture tracking from noisy input
        
        class Point {
            public double x,y;
        }
        private ServoArm       father;
        LinkedList<Point> pointHistory = new LinkedList<Point>();
        public float leftBoundary = 0.45f;
        
        
        public float rightBoundary = 0.55f;
        //private LearningStates learningState;
        
        public LearningTask(ServoArm father) {
            this.father        = father;
            //this.learningState = learningState;
        }
        
        public void run() {
            int next = 0;
            
            checkHardware();
            try {
                if(servo!=null && servo.isOpen()){
                    log.info("sweeping servo to capture tracking");
                    for(int i=0;i<SWEEP_COUNT;i++){
                        father.setServo(0);
                        //wait for the motor to move
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
            while (true) {
                //check if we should exit thread
                synchronized (father.learningLock) {
                    if (father.learningState == LearningStates.stoplearning) {
                        father.learningState = learningState.notlearning;
                        log.info("stopped learning");
                        return;
                    }
                }
                
                
                //do the regression if we have enough samples
                if(next == 0) {
                    try {
                        if(isAccurate()) {
                            father.learningState = learningState.notlearning;
                            father.state = state.relaxed;
                            father.relax();
                            return;
                        }
                    } catch (InterruptedException ex) {
                        log.info("checking interrupted: "+ex.toString());
                        continue; //go up to the exit if (no code replication)
                    }
                    
                    
                    if(pointHistory.size() > POINTS_TO_REGRESS_INITIAL) {
                        log.info("got "+pointHistory.size()+" points, doing regression");
                        doLinearRegression();
                        next = POINTS_TO_REGRESS_ADDITIONAL;
                    } else
                        next = POINTS_TO_REGRESS_INITIAL+1;
                    
                } else {
                    next--;
                }
                
                try {
                    
                    //random number between left and right boundary
                    Point p = new Point();
                    p.y = Math.random()*(rightBoundary - leftBoundary) + leftBoundary;
                    checkHardware();
                    if(servo!=null && servo.isOpen()){
                        father.setServo((float)p.y);
                        
                        //wait for the motor to move
                        synchronized (Thread.currentThread()) {
                            Thread.currentThread().wait(LEARN_POSITION_DELAY_MS);
                        }
                        //get the captured position
                        p.x = (float)readPos((float)p.y);
//                        log.info("learning: set servo="+p.y+", read position x="+p.x);
                        
                        //stop motor
                        father.disableServo();
                        
                        //add point to list; max 100 elements in list
                        if (pointHistory.size() > POINTS_TO_REGRESS_MAX)
                            pointHistory.removeFirst();
                        
                        pointHistory.addLast(p);
                    }
                    
                } catch (InterruptedException e) {
                    //we were interrupted. so just check
                    //next time if we have to exit
                }
            }
        }
        
        private void doLinearRegression() {
            double ux=0, uy=0;
            int n = 0;
            double sx=0, sxy = 0;
            Iterator<Point> it;
            //ArrayList<Double> logx = new ArrayList();
            //ArrayList<Double> logy = new ArrayList();
            
            //caclulate ux and uy, the mean x (input servo setting 0-1 range) and y (output measured arm cluster location pixels)
            StringBuilder sb=new StringBuilder();
            for(it = pointHistory.iterator(); it.hasNext();) {
                Point p = it.next();
                
                ux += p.x;
                uy += p.y;
                //    logx.add(p.x);
                //  logy.add(p.y);
                sb.append(String.format("%f\t%f\n",p.x,p.y));
                n++;
            }
//            log.info(sb.toString());
            
            //JAERViewer.GlobalDataViewer.addDataSet("Servo Arm Mapping", logx, logy);
            
            ux /= n;
            uy /= n;
            //calculate sx and sy, the summed variance of x and the cross variance of y with x
            for(it = pointHistory.iterator(); it.hasNext();) {
                Point p = it.next();
                
                sx +=  (p.x - ux)*(p.x - ux);
                sxy += (p.x - ux)*(p.y - uy);
            }
            
            //calculate and set linear paramters
            father.setLearnedParam((float) (sxy / sx), (float) (uy - (sxy / sx) * ux));
            
            log.info(String.format("learned mapping from arm pixel x to servo motor setting y is\n   y=%f*x+%f\n", father.learned_k, father.learned_d));
            
            
        }
        
        private boolean isAccurate() throws InterruptedException  {
            log.info("checking learning");
            //okay lets see how good we are
            int n;
            int error = 0;
            
            for(n = 0; n < POINTS_TO_CHECK;n ++) {
                int pointToCheck=(int)(Math.random()* (double)father.chip.getSizeX());
                int measuredPoint=readPos(PositionToOutput(pointToCheck));
//                father.setPositionDirect(pointToCheck);
//                sleep(LEARN_POSITION_DELAY_MS);
                
//                error += Math.abs(father.getDesiredPosition() - father.getActualPosition());
                error += Math.abs(pointToCheck - measuredPoint);
            }
            
            if(error/POINTS_TO_CHECK < getAcceptableAccuracyPixels()) {
                log.info("done checking learning, error="+error/POINTS_TO_CHECK+" pixels, which is OK");
                return true;
            } else {
                log.info("done checking learning, error="+error+" pixels, which is not OK");
                return false;
            }
        }
        
        /** Shakes the arm a bit and reads the tracked arm postion from the tracker
         * @param motpos the position in servo space
         */
        private int readPos(float motpos) throws InterruptedException  {
            //shake around and read the position
            final float SHAKE_AMOUNT=0.01f; // 1/2 total amount to shake by out of 0-1 range
            final int SHAKE_COUNT=5; // 1/2 total number of shakes
            final int SHAKE_PAUSE_MS=50;
            int position = 0;
            for(int i = 0; i < SHAKE_COUNT; i++ ) {
                father.setServo(motpos + SHAKE_AMOUNT);
                position += father.getActualPosition();
                sleep(SHAKE_PAUSE_MS);
                position += father.getActualPosition();
                father.setServo(motpos - SHAKE_AMOUNT);
                sleep(SHAKE_PAUSE_MS);
            }
            
            int ret=position/(2*SHAKE_COUNT);
//            log.info("for motor position="+motpos+" read arm pixel postion="+ret);
            return ret;
            
            
        }
        
        private void sleep(long ms)  throws InterruptedException {
            synchronized (Thread.currentThread()) {
                Thread.currentThread().wait(ms);
            }
            //stop learning as fast as possible when a ball is coming
            if(father.learningState == LearningStates.stoplearning) {
                throw(new InterruptedException());
            }
        }
        
        
        private void getSamples(ArrayList x, ArrayList y) {
            //we could have a race here
            for(Point p:pointHistory) {
                x.add(p.x);
                y.add(p.y);
            }
        }
    }
    
    
// logging
    private class LoggingThread extends Thread {
        public boolean           exit;
        private ServoArm         father;
        private FileOutputStream file;
        private int              interval;
        private long             starttime;
        
        private ArrayList<Double> actPos;
        private ArrayList<Double> desPos;
        
        public LoggingThread(ServoArm father, int interval, String filename) throws IOException {
            file          = new FileOutputStream(filename);
            this.interval = interval;
            this.father   = father;
            exit          = false;
            starttime     = System.currentTimeMillis();
            actPos = new ArrayList();
            desPos = new ArrayList();
            setName("LoggingThread");
        }
        
        public void run() {
            PrintStream p          = new PrintStream(file);
            int         i          = 0;
            int         clusterpos = -1;
            JAERViewer.GlobalDataViewer.addDataSet("Actual Pos (ServoArm)", actPos, (double) interval, true);
            JAERViewer.GlobalDataViewer.addDataSet("Desired Pos (ServoArm)", desPos, (double) interval, true);
            
            while (!exit) {
                try {
                    this.sleep(interval);
                    
                    //p.printf("%d\t%d\t%d", father.getDesiredPosition(), father.getActualPosition(), System.currentTimeMillis()- starttime );
                    //p.println();
                    
                    synchronized (actPos) {
                        if(actPos.size() > 20000) {
                            //save memory
                            actPos.clear();
                        }
                        actPos.add((double)father.getActualPosition());
                    }
                    synchronized (desPos) {
                        if(desPos.size() > 20000) {
                            //save memory
                            desPos.clear();
                        }
                        desPos.add((double)father.getDesiredPosition());
                    }
                    
                    if (i++ > 10) {
                        //    p.flush();
                        i = 0;
                    }
                } catch (InterruptedException ex) {
                    break;
                } catch (Exception ex) {
                    ex.printStackTrace();
                    
                    break;
                }
                //used to write out software timing
                /*
                if(JAERViewer.globalTime3 != 0) {
                    //print out debug times
                    p.print(JAERViewer.globalTime1); p.print(",");
                    p.print(JAERViewer.globalTime2); p.print(",");
                    p.print(JAERViewer.globalTime3);  p.print(",");
                    p.print(JAERViewer.globalTime2 - JAERViewer.globalTime1); p.print(",");
                    p.print(JAERViewer.globalTime3 - JAERViewer.globalTime1);
                    p.println();
                    JAERViewer.globalTime1 = 0;
                    JAERViewer.globalTime2 = 0;
                    JAERViewer.globalTime3 = 0;
                }
                 **/
            }
            JAERViewer.GlobalDataViewer.removeDataSet("Actual Pos (ServoArm)");
            JAERViewer.GlobalDataViewer.removeDataSet("Desired Pos (ServoArm)");
            try {
                file.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            
        }
        
        protected void finalize() throws Throwable {
            //JAERViewer.GlobalDataViewer.removeDataSet("Actual Pos (Goalie)");
            //JAERViewer.GlobalDataViewer.removeDataSet("Desired Pos (Goalie)");
        }
        
    }
    
    public float getServoPulseFreqHz() {
        return servoPulseFreqHz;
    }
    
    public void setServoPulseFreqHz(float servoPulseFreqHz) {
        this.servoPulseFreqHz = servoPulseFreqHz;
        if(servo!=null && servo instanceof SiLabsC8051F320_USBIO_ServoController){
            float actualFreq=((SiLabsC8051F320_USBIO_ServoController)servo).setServoPWMFrequencyHz(servoPulseFreqHz);
            servoPulseFreqHz=actualFreq;
        }
        // dont store in prefs to ensure max speed for now.
    }
    
    public float getAcceptableAccuracyPixels() {
        return acceptableAccuracyPixels;
    }
    
    public void setAcceptableAccuracyPixels(float acceptableAccuracyPixels) {
        if(acceptableAccuracyPixels>chip.getSizeX()/2) acceptableAccuracyPixels=chip.getSizeX()/2;
        this.acceptableAccuracyPixels = acceptableAccuracyPixels;
        getPrefs().putFloat("ServoArm.acceptableAccuracyPixels",acceptableAccuracyPixels);
        
    }
    
}

