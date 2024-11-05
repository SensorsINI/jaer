package com.inilabs.jaer.gimbal;

import ch.unizh.ini.jaer.hardware.pantilt.PanTilt;
import com.inilabs.birdland.gimbal.RS4ControllerInterface;
import com.inilabs.birdland.gimbal.RS4ControllerV2;

import java.beans.PropertyChangeSupport;
import java.beans.PropertyChangeListener;

import java.util.ArrayList;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javax.swing.*;
import javafx.stage.Stage;


// import java.util.logging.Logger;

import java.lang.Math;
import net.sf.jaer.chip.AEChip;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PanTilt class that controls pan and tilt using RS4ControllerInterface for
 * controlling the gimbal downstream. This class includes jittering
 * functionality, pan/tilt limits, and laser control.
 */
/**
 * Encapsulates a pan tilt controller based on using DJI Ronin RS4 Gimbal.
 * PanTilt directly controls the gimbal settings, but does not implement a
 * calibration that maps from visual coordinates to pan tilt settings.
 *
 * This code has been adapted from PanTilt.java to mate with the DJI gimbal.
 * ***** The code is not yet optimized!! rjd Oct 2024
 *
 * The DJI Gimbal is unlike the original servo implementation of PanTilt. It is
 * accessed via a canbus. It is set directly in polar coordinates (degrees) with
 * respect to its own heading = 0,0,0
 *
 * The strategy in these revisions is to maintain the 0-1 pan and tilt ranges
 * introduced by Tobi, and do the conversion to degrees on at the final call to
 * the gimbal controller. Any references to pan and tilt imply tobi 0-1 ranges
 * in jaer 2D coord space. Any references to Yaw and Pitch imply degrees in
 * gimbal polar coord space (Roll is available but not used by jaer)
 *
 * Because we expect that the pose of the gimbal will be acted on also by other
 * agents, this code will add only deltas to the Gimbal pose (not absolute yaw,
 * pitch).
 *
 * @author tobi, rjd (2024)
 * @see ch.unizh.ini.jaer.hardware.pantilt.PanTiltTracker
 */
public class GimbalBase implements GimbalInterface, LaserOnOffControl {

  //  private static final Logger log = Logger.getLogger("net.sf.jaer");
 // Logger instance for logging messages
    private static final Logger log = (Logger) LoggerFactory.getLogger(GimbalBase.class);
    
    private Timer panTiltTimer;
    volatile boolean lockAcquired = false;
    private String lockOwner = "None";

    java.util.Timer jitterTimer, followTimer;

    // Pan and tilt positions
    private float pan = .5f, tilt = .5f;
    private float previousPan = pan, previousTilt = tilt;
    private float panTarget = pan, tiltTarget = tilt;
    private float panJitterTarget = pan, tiltJitterTarget = tilt;
    private float limitOfPan = .8f, limitOfTilt = .8f;
    private boolean panInverted = false, tiltInverted = false;

    private float jitterAmplitude = 0.0f;
    private float jitterFreqHz = 0.0f;
    private boolean jittering = false;

    private boolean jitterEnabled = false;
    private boolean linearSpeedEnabled = false;
    private final Trajectory panTiltTrajectory = new Trajectory();
    private final Trajectory panTiltTargetTrajectory = new Trajectory();

    // RS4 GimbalBase
    private float defaultYaw = 0.0f;
    private float defaultRoll = 0.0f;
    private float defaultPitch = 0.0f;
    private float yaw = 0.0f; // deg, in gimal polar space
    private float roll = 0.0f;
    private float pitch = 0.0f;

    private float chipXFOV = 30; // degrees
    private float chipYFOV = 30;

    float MaxMovePerUpdate = 0.8f; //Max amount of gimbal per update
    float MinMovePerUpdate = 0.1f; //Min amount of gimbal per update 
    int MoveUpdateFreqHz = 100; //in Hz how often does the targeted pantilt move

    private final float deltaThreshold = 0.1f;  // gimbal xmission threshold (deg) 

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    

    private final Object gimbalLock = new Object();
    
    private static volatile GimbalBase instance;
    private final RS4ControllerV2 rs4controller = RS4ControllerV2.getInstance();
    
    public static RS4ControllerGUISwingV1 rs4controllerGUI = null;
    
    private int poseSendInterval = 200 ; // ms
    private int poseFetchInterval = 200 ; // ms
      
    private float panSum = 0.0f;
    private float tiltSum = 0.0f;
    private int count = 0;
    
    private boolean targetEnabled = false;
    private boolean enableGimbal = false ; // debugging

    private final float chipFOV = 30.0f; // degrees
 
    
    public GimbalBase() {
        super();
  //       Start background task for updating pan/tilt (if needed)
        panTiltTimer = new Timer(true);
        panTiltTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updatePanTilt();
            }
        }, 0, 20);

        // Register a shutdown hook to clean up resources when the JVM shuts down
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        
      
//SwingUtilities.invokeLater(RS4ControllerGUISwingV1::new);
rs4controllerGUI = new RS4ControllerGUISwingV1();
      
        init();
        startTasks();
    }

    public static GimbalBase getInstance() {
    if (instance == null) {
        synchronized (GimbalBase.class) {
            if (instance == null) {
                instance = new GimbalBase();
            }
        }
    }
    return instance;
}


    private void init() {
       sendDefaultGimbalPose();  
} 
    

    
 public void sendDefaultGimbalPose(){   
      rs4controller.setPose(0, 0, -30);
 }
 
 
    private void startTasks() {
        // Schedule a task to periodically request data
        // Schedule the 100ms timer thread
        try {
        scheduler.scheduleAtFixedRate(this::applyAveragePose, poseSendInterval, poseSendInterval, TimeUnit.MILLISECONDS);    
        scheduler.scheduleAtFixedRate(this::fetchGimbalData, poseFetchInterval, poseFetchInterval, TimeUnit.MILLISECONDS);
        log.info("Scheduled tasks started....");
        } catch(Exception e ) { 
            log.error("Error starting scheduled tasks : {}", e.getMessage(), e);
        }
    }

    // Method to gracefully shut down the controller and release resources
        private volatile boolean shutdown = false;

        
    private void shutdown() {
    synchronized (this) {
        if (shutdown) return;  // Prevent repeated shutdown
        shutdown = true;
    }
    log.info("Shutting down GimbalBase...");
    stopFollow();
    stopJitter();
    scheduler.shutdownNow();
        log.info("GimbalBase scheduler shut down successfully.");
} 
     
        public void showControllerGUI() {
       rs4controllerGUI.setVisible(true);
    }
    
         public void hideControllerGUI() {
       rs4controllerGUI.setVisible(false);
    }
    
    public RS4ControllerGUISwingV1 getRS4ControllerGUI() {
        return rs4controllerGUI;
    }
    
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.removePropertyChangeListener(listener);
    }

    public class Trajectory extends ArrayList<TrajectoryPoint> {

        long lastTime;

        void start() {
            start(System.nanoTime());
        }

        void start(long startTime) {
            if (!isEmpty()) {
                super.clear();
            }
            lastTime = startTime;
        }

        void add(float pan, float tilt) {
            if (isEmpty()) {
                start();
            }

            long now = System.nanoTime(); //We want this in nanotime, as the panTilt values do change very fast and millis is often not accurate enough.
            add(new TrajectoryPoint(now - lastTime, pan, tilt));
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

        public long getTime() {
            return timeNanos;
        }

        public float getPan() {
            return pan;
        }

        public float getTilt() {
            return tilt;
        }
    }

    public RS4ControllerV2 getGimbal() {
        return rs4controller;
    }
    
    /**
     * Acquire ownership (from PanTiltInterface)
     */
    public void acquire(String who) {
        if (!lockAcquired) {
            lockAcquired = true;
            lockOwner = who;
            log.info("Gimbal acquired");
        }
    }

    /**
     * Check if lock is owned (from PanTiltInterface)
     */
    @Override
    public boolean isLockOwned() {
        return lockAcquired; // Example logic for lock check
    }

    /**
     * Release ownership (from PanTiltInterface)
     */
    public void release(String who) {
        if (lockAcquired = true && lockOwner.equals(who)) {
            lockOwner = "None";
            lockAcquired = false;
            log.info("PanTilt released");
        }
    }

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --JitterEnabled--">
    /**
     * @return the jitterEnabled
     */
    public boolean isJitterEnabled() {
        return jitterEnabled;
    }

    /**
     * @param jitterEnabled the jitterEnabled to set
     */
    public void setJitterEnabled(boolean jitterEnabled) {
        this.jitterEnabled = jitterEnabled;
        if (jitterEnabled) {
            float[] current = getPanTiltValues();
            startJitter();
            setTarget(current[0], current[1]);//set target so that pantilt starts tracking
        } else {
            stopJitter();
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --JitterAmplitude--">
    @Override
    public float getJitterAmplitude() {
        return jitterAmplitude;
    }

    /**
     * Sets the amplitude (1/2 of peak to peak) of circular jitter of pan tilt
     * during jittering
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

    /**
     * Sets the frequency of the jitter.
     *
     * @param jitterFreqHz in Hz.
     */
    @Override
    public void setJitterFreqHz(float jitterFreqHz) {
        this.jitterFreqHz = jitterFreqHz;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --JitterTarget--">
    private float[] getJitterTarget() {
        float[] r = {panJitterTarget, tiltJitterTarget};
        return r;
    }

    private void setJitterTarget(float PanTarget, float TiltTarget) {
        float[] oldJitterTarget = {this.panJitterTarget, this.tiltJitterTarget};
        this.panJitterTarget = PanTarget;
        this.tiltJitterTarget = TiltTarget;
        startFollow();//automatically start following target. This will initialize the servo if it is not already.
        this.pcs.firePropertyChange("JitterTarget", oldJitterTarget, new float[]{PanTarget, TiltTarget});
    }
    // </editor-fold>

    public void startFollow() {
        if (followTimer == null) {
            followTimer = new java.util.Timer();
            // Repeat the JitterTask without delay and with 20ms between executions
            followTimer.scheduleAtFixedRate(new FollowerTask(), 0, 1000 / getMoveUpdateFreqHz());
        }
    }

    public void stopFollow() {
        if (followTimer != null) {
            followTimer.cancel();
            followTimer = null;
        }
    }

    private class FollowerTask extends TimerTask {

        boolean cancelMe = false;
        float[] MoveVec = {0, 0};
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
            Distance = (float) Math.sqrt(MoveVec[0] * MoveVec[0] + MoveVec[1] * MoveVec[1]);

            if (MoveVec[0] != 0) {
                MoveVec[0] = MoveVec[0] / Distance;//UnitVector
            }
            if (MoveVec[1] != 0) {
                MoveVec[1] = MoveVec[1] / Distance;
            }
            if (MoveVec[0] == 0 && MoveVec[1] == 0) {
                stopFollow(); //If target is reached we do not need to continue to follow. If a new target is set the follower will start again
            }
            try {
                if (isLinearSpeedEnabled()) {
                    Speed = (float) Math.min(MaxMovePerUpdate, Distance);
                } else {
                    // As the servo values are between 0 and 1 the maximum distance
                    // is sqrt(1+1), hence we normalize by sqrt(2)
                    Speed = (float) Math.max((Distance / Math.sqrt(2)) * MaxMovePerUpdate, Math.min(MinMovePerUpdate, Distance));
                }
                setPanTiltValues(Current[0] + MoveVec[0] * Speed, Current[1] + MoveVec[1] * Speed);
            } catch (Exception ex) {
                log.warn(ex.toString());
            }
        }
    }

    private class JittererTask extends TimerTask {

        long startTime = System.currentTimeMillis();

        JittererTask() {
            super();
        }

        @Override
        public void run() {
            long t = System.currentTimeMillis() - startTime;
            double phase = Math.PI * 2 * (double) t / 1000 * jitterFreqHz;
            float dx = (float) (jitterAmplitude * Math.sin(phase));
            float dy = (float) (jitterAmplitude * Math.cos(phase));
            float[] Target = getTarget();
            setJitterTarget(Target[0] + dx, Target[1] + dy);//setPanTiltValues(pantiltvalues[0] + dx, pantiltvalues[1] + dy);
        }
    }

    /**
     * Starts the servo jittering around its set position at an update frequency
     * of 50 Hz with an amplitude set by 'jitterAmplitude'
     *
     * @see #setJitterAmplitude
     */
    @Override
    public void startJitter() {
        jitterTimer = new java.util.Timer();
        // Repeat the JitterTask without delay and with 20ms between executions
        jitterTimer.scheduleAtFixedRate(new JittererTask(), 0, 20);
        jittering = true;
        log.info("Jittering started with amplitude " + jitterAmplitude + " and frequency " + jitterFreqHz);
    }

    /**
     * Stops the jittering
     */
    @Override
    public void stopJitter() {
        if (jitterTimer != null) {
            jitterTimer.cancel();
            jitterTimer = null;
            jittering = false;
            log.info("Jittering stopped");
        }
    }

    /**
     * @param newPan the pan value from 0 to 1 inclusive, 0.5f is the center
     * position. 1 is full right.
     * @param newTilt the tilt value from 0 to 1. 1 is full down.
     * @throws HardwareInterfaceException If this exception is thrown, the
     * interface should be closed. The next attempt to set the pan/tilt values
     * will reopen the interface.
     * @see #panServoNumber
     * @see #tiltServoNumber
     */
    @Override
    public void setPanTiltValues(float newPan, float newTilt) {

        previousPan = this.pan;
        previousTilt = this.tilt;

        newPan = clipPan(newPan); //clip values according to set limits
        newTilt = clipTilt(newTilt);

        this.pan = newPan;
        this.tilt = newTilt; //efferent copy

        if (panInverted) {
            newPan = 1 - newPan; //invert values if selected
        }
        if (tiltInverted) {
            newTilt = 1 - newTilt;
        }

        // Use RS4Controller's setPosControl method
        this.updateGimbalPose(newPan, newTilt);
        log.info("Updated pan/tilt values to " + newPan + " / " + newTilt);

        //setLaserOn(true);
        float[] PreviousValues = {previousPan, previousTilt};
        float[] NewValues = {this.pan, this.tilt};
        panTiltTrajectory.add(this.pan, this.tilt);

        this.pcs.firePropertyChange("PanTiltValues", PreviousValues, NewValues);
    }

    /**
     * Returns the last value set, even if the servo interface is not
     * functional. The servo could still be moving to this location.
     *
     * @return a float[] array with the 0 component being the pan value, and the
     * 1 component being the tilt
     */
    @Override
    public float[] getPanTiltValues() {
        float[] r = {pan, tilt};
        return r;
    }

    /**
     * Clips the pan value based on the set limits.
     */
    private float clipPan(float pan) {
        return Math.max(0, Math.min(1, pan)); // Adjust limits as necessary
    }

    /**
     * Clips the tilt value based on the set limits.
     */
    private float clipTilt(float tilt) {
        return Math.max(0, Math.min(1, tilt)); // Adjust limits as necessary
    }

    /**
     * Updates the pan and tilt values periodically using a timer. Implements
     * jittering effect if jitter is active.
     */
    private void updatePanTilt() {
        float newPan = pan;
        float newTilt = tilt;
        if (jittering) {
            float jitterValue = (float) (jitterAmplitude * Math.sin(2 * Math.PI * jitterFreqHz * System.currentTimeMillis() / 1000.0));
            newPan = pan + jitterValue;
            newTilt = tilt + jitterValue;
        }
        newPan = clipPan(newPan);
        newTilt = clipTilt(newTilt);
        updateGimbalPose(newPan, newTilt);
        log.info("updatePanTilt: pan/tilt values to " + newPan + " / " + newTilt);
    }

    /**
     * The gimbal is an independent device whose absolute pose may be set by
     * various agents. Therefore we add only our required deltas to its current
     * pose. We do not set its absolute pose.
     *
     * @param pan
     * @param tilt
     */
     // Method to accumulate pan/tilt inputs for averaging
    public synchronized void updateGimbalPose(float pan, float tilt) {
        panSum += pan;
        tiltSum += tilt;
        count++;
    }

    //  jAER often provides more data than DJI RS4  gimbal (RS4Controller) can absorb,.
    // This restriction is due to limited RS4 slewrate, canbus callback update rate etc.
    // So here we need to install a buffer of sorts, from which we can sample at a rate with which
    // RS4 is comfortable.
    // 
   //  This buffer could be sophisicated. But we start with something simple, which is to average the 
    // the inputs (in pantilt 0-1 coordinates)
    // Method to apply the averaged pose at each scheduled interval
    private void applyAveragePose() {
        float avgPan, avgTilt;
        
        synchronized (this) {
            if (count == 0) return; // No updates to apply
            avgPan = panSum / count;
            avgTilt = tiltSum / count;

            // Reset accumulators
            panSum = 0.0f;
            tiltSum = 0.0f;
            count = 0;
        }

        // Apply averaged pan/tilt to set gimbal pose
        sendGimbalPose(avgPan, avgTilt);
    }

    private void sendGimbalPose(float pan, float tilt) {
        if (rs4controller.isShutdown()) {
            log.error("RS4ControllerV2 is shut down; skipping setGimbalPose.");
            return;
        }

        // Convert pan/tilt to deltaYaw and deltaPitch in degrees
        float deltaYaw = (pan - 0.5f) * chipFOV;
        float deltaPitch = (tilt - 0.5f) * chipFOV;
       
        // Only send significant updates of pose
        if ((Math.abs(deltaYaw) > deltaThreshold || Math.abs(deltaPitch) > deltaThreshold ) && isTargetEnabled() && enableGimbal) {
           float sendYaw = getYaw() + deltaYaw;
           float sendPitch = getPitch() + deltaPitch;
            rs4controller.setPose(sendYaw, 0.0f, sendPitch); // PanTilt does not consider Roll 
            log.info("Sent gimbal pose deltas: deltaYaw=" + deltaYaw + " deg, deltaPitch=" + deltaPitch + " deg");
         } else 
        {  
            // go to home pose
            rs4controller.setPose(0.0f, 0.0f, -30.0f); 
        }      
    }

    public void enableGimbal(boolean yes) {
        enableGimbal = yes;
    }
    
    
    public void setTargetEnabled(boolean yes) {
        targetEnabled = yes;
    }
    
    public boolean isTargetEnabled(){
        return targetEnabled;
    }    
    
    
    
    // GimbalLock  Force fetchGimbalData to always retrieve fresh data directly from RS4ControllerV2 at each scheduled interval, 
    // regardless of whether data has changed.
    // This ensures GimbalBase consistently reflects the latest data from RS4ControllerV2, 
    // minimizing any discrepancies between the controller’s actual and reported poses.  
    private void fetchGimbalData() {
        //  synchronized (gimbalLock) {
           this. yaw = rs4controller.getYaw();  // (deg, in gimbal polar space)
            this.roll = rs4controller.getRoll();
           this. pitch = rs4controller.getPitch();
          log.info("Fetched current RS4Controller pose (deg): yaw:  " + yaw + "  roll:  " + roll + " pitch: "  + pitch );
      // }
    }

    // return  local values, to prevent gmbal overload
    public float[] getGimbalPose() {
        return new float[]{yaw, roll, pitch};
    }

    public float getYaw() {
        return this.yaw;
    }

    public float getRoll() {
        return this.roll;
    }

    public float getPitch() {
        return this.pitch;
    }

    //        degs=(180/math.PI)*chip.getPixelWidthUm()*1e-6f/(lensFocalLengthMm*1e-3f)*pixels
    public void setChipXFOV(float angle) {
        this.chipXFOV = angle;
    }

    public void setChipYFOV(float angle) {
        this.chipYFOV = angle;
    }

    public float getchipXFOV() {
        return this.chipXFOV;
    }

    public float getchipYFOV() {
        return this.chipYFOV;
    }

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --Target--">
    public float[] getTarget() {
        float[] r = {panTarget, tiltTarget};
        return r;
    }

    public void setTarget(float PanTarget, float TiltTarget) {
        float[] oldTarget = {this.panTarget, this.tiltTarget};

        this.panTarget = PanTarget;
        this.tiltTarget = TiltTarget;
        //If the Jitter is not enabled, we need to set the Jittertarget 
        // directly, because in the end the PanTilt follows the JitterTarget, 
        // not the real Target
        if (!isJitterEnabled()) {
            setJitterTarget(PanTarget, TiltTarget);
        }
        panTiltTargetTrajectory.add(PanTarget, TiltTarget);
        this.pcs.firePropertyChange("Target", oldTarget, new float[]{PanTarget, TiltTarget});
    }

    public void setTargetChange(float panTargetChange, float tiltTargetChange) {
        float[] oldTarget = {this.panTarget, this.tiltTarget};
        setTarget(oldTarget[0] + panTargetChange, oldTarget[1] + tiltTargetChange);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --UpdateFreq--">
    public int getMoveUpdateFreqHz() {
        return this.MoveUpdateFreqHz;
    }

    public void setMoveUpdateFreqHz(int UpdateFreq) {
        if (UpdateFreq > 100) {
            UpdateFreq = 100;  // ** rjd 
        }
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

    /**
     * Shutdown the PanTilt system
     */
    public void close() {
        log.info("Call to close() GimbalBase...");
        //       panTiltTimer.cancel();
    //    shutdown();
    }

    public void setLimitOfPan(float limit) {
        limitOfPan = limit;
    }

    public void setLimitOfTilt(float limit) {
        limitOfTilt = limit;
    }

    public float getLimitOfPan() {
        return limitOfPan;
    }

    public float getLimitOfTilt() {
        return limitOfTilt;
    }

    public void setPanInverted(boolean yes) {
        panInverted = yes;
    }

    public void setTiltInverted(boolean yes) {
        panInverted = yes;
    }

    public boolean getPanInverted() {
        return panInverted;
    }

    public boolean getTiltInverted() {
        return tiltInverted;
    }

    public void setLaserEnabled(boolean yes) {
        //dummy TODO implement rjd
        //laserEnabled = yes;
    }

    public void setLaserOn(boolean yes) {
        //dummy TODO implement rjd
    }

}
