
package ch.unizh.ini.jaer.hardware.pantilt;

import java.util.logging.Level;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.ServoInterface;
import net.sf.jaer.hardwareinterface.usb.ServoInterfaceFactory;
import java.util.TimerTask;
import java.util.logging.Logger;
import java.beans.PropertyChangeSupport;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

/** Encapsulates a pan tilt controller based on using SiLabsC8051F320_USBIO_ServoController.
 * Currently assumes that there is only one controller attached and that the 
 * pan and tilt servos are tied to the panServoNumber and DEFAULT_TILT_SERVO 
 * servo output channels on the board. Port 2 of the ServoUSB board is used 
 * to power a laser pointer that can be activated. PanTilt directly controls 
 * the servo settings, but does not implement a calibration that maps from 
 * visual coordinates to pan tilt settings. To control the pan tilt to aim at 
 * a particular visual location in the field of view of a silicon retina, see 
 * PanTiltTracker.
 * 
 * @author tobi
 * @see #DEFAULT_PAN_SERVO
 * @see #DEFAULT_TILT_SERVO
 * @see ch.unizh.ini.jaer.hardware.pantilt.PanTiltTracker */
public class PanTilt implements PanTiltInterface, LaserOnOffControl {
    
    private static List<PanTilt> InstanceList = new ArrayList<>();
    private static int numberInstances=0;
    private final int instanceID;
    private static final Logger log = Logger.getLogger("PanTilt");

    /** Servo output number on SiLabsC8051F320_USBIO_ServoController, 0 based. */
    public final int  DEFAULT_PAN_SERVO = 1,  
                      DEFAULT_TILT_SERVO = 2; // number of servo output on controller
    private final int CHECK_SERVO_INTERVAL = 100;
    
    ServoInterface servo;

    volatile boolean lockAcquired = false;
    java.util.Timer jitterTimer, followTimer;
    
    private float   pan             = .5f,   tilt             = .5f;
    private float   previousPan     = pan,   previousTilt     = tilt;
    private float   panTarget       = pan,   tiltTarget       = tilt;
    private float   panJitterTarget = pan,   tiltJitterTarget = tilt;
    private float   limitOfPan      = .5f,   limitOfTilt      = .5f;
    private boolean panInverted     = false, tiltInverted     = false;
    private int     panServoNumber  = DEFAULT_PAN_SERVO, 
                    tiltServoNumber = DEFAULT_TILT_SERVO;
    
    private float   jitterAmplitude = .01f;
    private float   jitterFreqHz    = 10f;
    private boolean jitterEnabled   = false;
    private boolean linearSpeedEnabled = false;
    private final Trajectory panTiltTrajectory = new Trajectory();
    private final Trajectory panTiltTargetTrajectory = new Trajectory();
    
    private int checkServoCount = 0;
    
    float MaxMovePerUpdate = .1f; //Max amount of servochange per update
    float MinMovePerUpdate = 0.001f; //Min amount of servochange per update 
    int   MoveUpdateFreqHz = 1000; //in Hz how often does the targeted pantilt move
    
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    public static synchronized PanTilt makeNewInstance() {
        PanTilt res = new PanTilt();
        InstanceList.add(res);
        numberInstances++;
        return res;
    }
    public static synchronized PanTilt makeNewInstance(ServoInterface servo) {
        PanTilt res = new PanTilt(servo);
        InstanceList.add(res);
        numberInstances++;
        return res;
    }
    public static synchronized PanTilt getLastInstance() {
        PanTilt res;
        int size = getNumInstances();
        if(size == 0) {
            res = new PanTilt();
            InstanceList.add(res);
            numberInstances++;
            return res;
        }
        if(size > 1) log.info("There are more than one instances of 'PanTilt' currently active. The last Instance is returned");
        return InstanceList.get(size-1);
    }
    public static synchronized PanTilt getInstance(int index) {
        int size = getNumInstances();
        if(size == index){
            PanTilt res = new PanTilt();
            InstanceList.add(res);
            numberInstances++;
            return res;
        } else if(size < index) {
            throw new IllegalStateException("There is no instance at index "+index+" in the InstanceList! The number of Instances is"+size);
        } 
        return InstanceList.get(index);
    }
    public static synchronized int getNumInstances() {
        if(InstanceList.size() != numberInstances) throw new IllegalStateException("The number of instances in the list does not match the number of instances recorded!");
        return numberInstances;
    }
    public static synchronized int getInstanceIndex(PanTilt instance) {
        return instance.instanceID;
    }
    
       
    public class Trajectory extends ArrayList<TrajectoryPoint> { 
        long lastTime;
        
        void start() { start(System.nanoTime()); }
        void start(long startTime) {
            if(!isEmpty()) super.clear();
            lastTime = startTime;
        }
                
        void add(float pan, float tilt) {
            if (isEmpty()) start();
            
            long now = System.nanoTime(); //We want this in nanotime, as the panTilt values do change very fast and millis is often not accurate enough.
            add(new TrajectoryPoint(now-lastTime, pan, tilt));
            lastTime = now;
        }
    }

    public class TrajectoryPoint {
        private final long timeNanos;
        private final float pan, tilt;

        public TrajectoryPoint(long timeNanos, float pan, float tilt) {
            this.timeNanos = timeNanos;
            this.pan = pan;
            this.tilt = tilt;
        }
        
        public long getTime() { return timeNanos; }
        public float getPan() { return pan; }
        public float getTilt() { return tilt; }
    }
    
    private PanTilt() {
        // Sets a ShutDown Hook that is fired everytime the JVM terminates.
        // This includes normal ending of jAER as well as terminations such as
        // system shutdown. This Hook makes sure that the laser does not stay 
        // on in this event.
        Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
            public void run(){
                log.info("disabling laser");
                setLaserEnabled(false);
            }
        });
        instanceID = numberInstances;
    }
    
    /** Constructs instance with previously constructed SiLabsC8051F320_USBIO_ServoController
     * @param servo */
    private PanTilt(ServoInterface servo){
        this();
        this.servo=servo;
    }
        
    public void close(){
        if(getServoInterface()!=null) getServoInterface().close();
    }
    
    private void checkServos() throws HardwareInterfaceException {
        if (checkServoCount++ % CHECK_SERVO_INTERVAL == 0) {
            if (servo == null) {
                try {
                    servo = (ServoInterface) ServoInterfaceFactory.instance().getFirstAvailableInterface();
                } catch (ClassCastException cce) {
                    throw new HardwareInterfaceException("Wrong type of interface: " + cce.getMessage());
                }
            }
        }
        if (servo == null) {
            throw new HardwareInterfaceException("no servo controller found");
        }
        if (!servo.isOpen()) {
            servo.open();
            float pwmFreq=servo.setServoPWMFrequencyHz(180);
            log.log(Level.INFO, "opened servo interface and set PWM frequency to {0}", pwmFreq);
        }
    }
    
    /** Returns previous pan and tilt values.
     * @return  the previous pantilt values*/
    public float[] getPreviousPanTiltValues(){
        float[] r={previousPan,previousTilt};
        return r;
    }
    
    /** Returns last change of pan and tilt values.
     * @return the change of current pantilt values to the last values*/
    public float[] getPreviousPanTiltChange(){
        float[] r={pan-previousPan,tilt-previousTilt};
        return r;
    }

    /** Returns the last value set, even if the servo interface is not functional. 
     * The servo could still be moving to this location. 
     * @return a float[] array with the 0 component being the pan value, 
     * and the 1 component being the tilt */
    @Override public float[] getPanTiltValues(){
        float[] r={pan,tilt};
        return r;
    }
    
    /** Simultaneously sets pan and tilt values. 
     * The directions here depend on the servo polarities, which could vary.
     * These values apply to HiTec digital servos. If selected the values for 
     * pan or tilt respectively get inverted. If set the values for pan/tilt 
     * get clipped by the limitOfPan/limitOfTilt
     * 
     * @param newPan the pan value from 0 to 1 inclusive, 0.5f is the center 
     *        position. 1 is full right.
     * @param newTilt the tilt value from 0 to 1. 1 is full down.
     * @throws HardwareInterfaceException
     *  If this exception is thrown, the interface should be closed. 
     *  The next attempt to set the pan/tilt values will reopen the interface.
     * @see #panServoNumber
     * @see #tiltServoNumber */
    @Override synchronized public void setPanTiltValues(float newPan, float newTilt) throws HardwareInterfaceException {
        checkServos();
        previousPan  = this.pan;
        previousTilt = this.tilt;

        newPan  = clipPan(newPan); //clip values according to set limits
        newTilt = clipTilt(newTilt);
        
        this.pan  = newPan;
        this.tilt = newTilt; //efferent copy
        
        if(panInverted) newPan  = 1-newPan; //invert values if selected
        if(tiltInverted)newTilt = 1-newTilt;

        servo.setServoValue(panServoNumber,newPan);
        servo.setServoValue(tiltServoNumber,newTilt);
        
        setLaserOn(true);
        
        float[] PreviousValues = {previousPan,previousTilt};
        float[] NewValues      = {this.pan,this.tilt};
        panTiltTrajectory.add(this.pan, this.tilt);
        this.pcs.firePropertyChange("PanTiltValues", PreviousValues , NewValues);
    }
    
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.removePropertyChangeListener(listener);
    }
    
    // <editor-fold defaultstate="collapsed" desc="Helper Methods --clipPan-- and --clipTilt--">
    private float clipPan(float pan) {
        float panLimit = getLimitOfPan();
        if (pan > 0.5f + panLimit) {
            pan = 0.5f + panLimit;
        } else if (pan < .5f - panLimit) {
            pan = .5f - panLimit;
        }
        return pan;
    }

    private float clipTilt(float tilt) {
        float tiltLimit = getLimitOfTilt();
        if (tilt > 0.5f + tiltLimit) {
            tilt = 0.5f + tiltLimit;
        } else if (tilt < .5f - tiltLimit) {
            tilt = .5f - tiltLimit;
        }
        return tilt;
    }
    // </editor-fold>

    /** A method can set this flag to tell other objects that the servo is "owned" */
    @Override public void acquire() {
        lockAcquired = true;
    }

    /** Releases the "acquired" flag */
    @Override public void release() {
        lockAcquired = false;
    }

    /** A method can check this to see if it can use the servo
     * @return  lockAcquired*/
    @Override public boolean isLockOwned() {
        return lockAcquired;
    }
    
    public void disableAllServos() throws HardwareInterfaceException {
        checkServos();
        setJitterEnabled(false);
        stopFollow();
        servo.disableAllServos();
    }
    
    private class FollowerTask extends TimerTask {
        boolean cancelMe = false;
        float[] MoveVec = {0,0};
        float Distance = 0f;
        float Speed;
        
        FollowerTask() {
            super();
        }
        
        @Override
        public void run() {
            float[] Target = getJitterTarget();
            float[] Current = getPanTiltValues();
            Target[0] = clipPan(Target[0]); //Need to clip target, as saved pantilt values are also cliped
            Target[1] = clipTilt(Target[1]);
                
            MoveVec[0] = Target[0] - Current[0];
            MoveVec[1] = Target[1] - Current[1];
            Distance = (float)Math.sqrt(MoveVec[0]*MoveVec[0]+MoveVec[1]*MoveVec[1]);

            if(MoveVec[0] != 0) MoveVec[0] = MoveVec[0]/Distance;//UnitVector
            if(MoveVec[1] != 0) MoveVec[1] = MoveVec[1]/Distance;
            if(MoveVec[0] ==0 && MoveVec[1] == 0) stopFollow(); //If target is reached we do not need to continue to follow. If a new target is set the follower will start again

            try {
                if(isLinearSpeedEnabled()){
                    Speed = (float) Math.min(MaxMovePerUpdate,Distance);
                } else {
                    // As the servo values are between 0 and 1 the maximum distance
                    // is sqrt(1+1), hence we normalize by sqrt(2)
                    Speed = (float) Math.max((Distance/Math.sqrt(2))*MaxMovePerUpdate,Math.min(MinMovePerUpdate,Distance));
                }
                setPanTiltValues(Current[0]+MoveVec[0]*Speed,Current[1]+MoveVec[1]*Speed);
            } catch(HardwareInterfaceException ex) {
               log.warning(ex.toString());
            }
        }
    }  

    private class JittererTask extends TimerTask {
        long startTime=System.currentTimeMillis();

        JittererTask() {
            super();
        }

        @Override
        public void run() {
            long t=System.currentTimeMillis()-startTime;
            double phase=Math.PI*2*(double)t/1000*jitterFreqHz;
            float dx=(float)(jitterAmplitude*Math.sin(phase));
            float dy=(float)(jitterAmplitude*Math.cos(phase));
            float[] Target = getTarget();
            setJitterTarget(Target[0] + dx, Target[1] + dy);//setPanTiltValues(pantiltvalues[0] + dx, pantiltvalues[1] + dy);
        }
    }

    /** Starts the servo jittering around its set position at an update 
     * frequency of 50 Hz with an amplitude set by 'jitterAmplitude'
     * @see #setJitterAmplitude */
    @Override public void startJitter() {
        jitterTimer = new java.util.Timer();
        // Repeat the JitterTask without delay and with 20ms between executions
        jitterTimer.scheduleAtFixedRate(new JittererTask(), 0, 20); 
    }

    /** Stops the jittering */
    @Override public void stopJitter() {
        if (jitterTimer != null) {
            jitterTimer.cancel();
            jitterTimer = null;
        }
    }
    
    public void startFollow() {
        if (followTimer == null){
            followTimer = new java.util.Timer();
            // Repeat the JitterTask without delay and with 20ms between executions
            followTimer.scheduleAtFixedRate(new FollowerTask(), 0, 1000/getMoveUpdateFreqHz());
        }
    }
    
    public void stopFollow() {
        if (followTimer != null) {
            followTimer.cancel();
            followTimer = null;
        }
    }
    
    /** Hack to control laser pointer power through pin 2 opendrain pulldown pins (multiple to share laser
     * current of about 200mA
     * @param yes to turn on laser */
      public  void setLaserOn(boolean yes){
        if(servo!=null){
            servo.setPort2(yes? 0:0xff);
        }
    }

    @Override public void setLaserEnabled(boolean yes) {
        setLaserOn(yes);
    }
     
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --ServoInterface--">  
    @Override public ServoInterface getServoInterface() {
        return servo;
    }
    
    @Override public void setServoInterface(ServoInterface servo) {
        this.servo=servo;
    }
    // </editor-fold> 
    

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --PanInverted--">
    /**
     * @return the panInverted */
    public boolean isPanInverted() {
        return panInverted;
    }

    /**
     * @param panInverted the panInverted to set */
    public void setPanInverted(boolean panInverted) {
        this.panInverted = panInverted;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --TiltInverted--">
    /**
     * @return the tiltInverted */
    public boolean isTiltInverted() {
        return tiltInverted;
    }

    /**
     * @param tiltInverted the tiltInverted to set */
    public void setTiltInverted(boolean tiltInverted) {
        this.tiltInverted = tiltInverted;
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --LimitOfPan--">
    /** gets the limit of the pan for the hardware
     * @return the panLimit */
    public float getLimitOfPan() {
        return limitOfPan;
    }

    /** sets the limit of the pan for the hardware
     * @param PanLimit the PanLimit to set */
    public void setLimitOfPan(float PanLimit) {
        float setValue = PanLimit;
        if (setValue < 0) {
            setValue = 0;
            log.info("The panLimit must be a value between 0 and 0.5");
        } else if (setValue > 0.5f) {
            setValue = 0.5f;
            log.info("The panLimit must be a value between 0 and 0.5");
        }
        this.limitOfPan = setValue;
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --LimitOfTilt--">
    /** gets the limit of the tilt for the hardware
     * @return the tiltLimit */
    public float getLimitOfTilt() {
        return limitOfTilt;
    }

    /** sets the limit of the tilt for the hardware
     * @param TiltLimit the TiltLimit to set */
    public void setLimitOfTilt(float TiltLimit) {
        float setValue = TiltLimit;
        if (setValue < 0) {
            setValue = 0;
            log.info("The tiltLimit must be a value between 0 and 0.5");
        } else if (setValue > 0.5f) {
            setValue = 0.5f;
            log.info("The tiltLimit must be a value between 0 and 0.5");
        }
        this.limitOfTilt = setValue;
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --PanServoNumber--">
    /**
     * @return the panServoNumber */
    public int getPanServoNumber() {
        return panServoNumber;
    }

    /**
     * @param panServoNumber the panServoNumber to set */
    public void setPanServoNumber(int panServoNumber) {
        this.panServoNumber = panServoNumber;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --TiltServoNumber--">
    /**
     * @return the tiltServoNumber */
    public int getTiltServoNumber() {
        return tiltServoNumber;
    }

    /**
     * @param tiltServoNumber the tiltServoNumber to set */
    public void setTiltServoNumber(int tiltServoNumber) {
        this.tiltServoNumber = tiltServoNumber;
    }
    // </editor-fold> 
    
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --JitterEnabled--">
    /**
     * @return the jitterEnabled */
    public boolean isJitterEnabled() {
        return jitterEnabled;
    }

    /**
     * @param jitterEnabled the jitterEnabled to set */
    public void setJitterEnabled(boolean jitterEnabled) {
        this.jitterEnabled = jitterEnabled;
        if(jitterEnabled) {
            float[] current = getPanTiltValues();
            startJitter();
            setTarget(current[0],current[1]);//set target so that pantilt starts tracking
        } else stopJitter();
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --JitterAmplitude--">
    @Override
    public float getJitterAmplitude() {
        return jitterAmplitude;
    }

    /** Sets the amplitude (1/2 of peak to peak) of circular jitter of pan tilt during jittering
     * 
     * @param jitterAmplitude the amplitude
     */
    @Override
    public void setJitterAmplitude(float jitterAmplitude) {
        this.jitterAmplitude = jitterAmplitude;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --JitterFreqHz--">
    @Override
    public float getJitterFreqHz() {
        return jitterFreqHz;
    }

    /** Sets the frequency of the jitter.
     * @param jitterFreqHz in Hz. */
    @Override
    public void setJitterFreqHz(float jitterFreqHz) {
        this.jitterFreqHz = jitterFreqHz;
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --JitterTarget--">
    private float[] getJitterTarget() {
        float[] r={panJitterTarget,tiltJitterTarget};
        return r;
    }
    
    private void setJitterTarget(float PanTarget,float TiltTarget) {
        float[] oldJitterTarget = {this.panJitterTarget,this.tiltJitterTarget};
        this.panJitterTarget = PanTarget;
        this.tiltJitterTarget = TiltTarget;
        startFollow();//automatically start following target. This will initialize the servo if it is not already.
        this.pcs.firePropertyChange("JitterTarget", oldJitterTarget , new float[] {PanTarget,TiltTarget});
    }
    // </editor-fold>
    
        
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --Target--">
    public float[] getTarget() {
        float[] r={panTarget,tiltTarget};
        return r;
    }
    
    public void setTarget(float PanTarget,float TiltTarget) {
        float[] oldTarget = {this.panTarget,this.tiltTarget};
        
        this.panTarget = PanTarget;
        this.tiltTarget = TiltTarget;
        //If the Jitter is not enabled, we need to set the Jittertarget 
        // directly, because in the end the PanTilt follows the JitterTarget, 
        // not the real Target
        if(!isJitterEnabled()){ 
            setJitterTarget(PanTarget,TiltTarget);
        }
        panTiltTargetTrajectory.add(PanTarget,TiltTarget);
        this.pcs.firePropertyChange("Target", oldTarget , new float[] {PanTarget,TiltTarget});
    }
    
    public void setTargetChange(float panTargetChange, float tiltTargetChange) {
        float[] oldTarget = {this.panTarget,this.tiltTarget};
        
        setTarget(oldTarget[0] + panTargetChange, oldTarget[1] + tiltTargetChange);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --UpdateFreq--">
    public int getMoveUpdateFreqHz() {
        return this.MoveUpdateFreqHz;
    }
    
    public void setMoveUpdateFreqHz(int UpdateFreq) {
        if(UpdateFreq > 1000) UpdateFreq = 1000;
        this.MoveUpdateFreqHz = UpdateFreq;
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --MaxMovePerUpdate--">
    public float getMaxMovePerUpdate() {
        return this.MaxMovePerUpdate;
    }
    
    public void setMaxMovePerUpdate(float MaxMove) {
        this.MaxMovePerUpdate = MaxMove;
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --MinMovePerUpdate--">
    public float getMinMovePerUpdate() {
        return this.MinMovePerUpdate;
    }
    
    public void setMinMovePerUpdate(float MinMove) {
        this.MinMovePerUpdate = MinMove;
    }

    // </editor-fold>
    
    public boolean isLinearSpeedEnabled() {
        return linearSpeedEnabled;
    }

    public void setLinearSpeedEnabled(boolean linearSpeedEnabled) {
        this.linearSpeedEnabled = linearSpeedEnabled;
    }

    public Trajectory getPanTiltTrajectory() {
        return panTiltTrajectory;
    }
    
    public void resetPanTiltTrajectory() {
        panTiltTrajectory.clear();
    }
    
    public Trajectory getPanTiltTargetTrajectory() {
        return panTiltTargetTrajectory;
    }
    
    public void resetPanTiltTargetTrajectory() {
        panTiltTargetTrajectory.clear();
    }
}
