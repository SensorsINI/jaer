package com.inilabs.jaer.gimbal;

import com.inilabs.hardware.gimbal.RS4ControllerInterface;
import com.inilabs.hardware.gimbal.RS4Controller;

import java.beans.PropertyChangeSupport;
import java.beans.PropertyChangeListener;

import java.util.ArrayList;
import java.util.List;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;


/**
 * PanTilt class that controls pan and tilt using RS4ControllerInterface for controlling the gimbal downstream.
 * This class includes jittering functionality, pan/tilt limits, and laser control.
 */


/** Encapsulates a pan tilt controller based on using DJI RS4 Gimbal.
 * PanTilt directly controls the gimbal servo settings, but does not implement a calibration 
 * that maps from visual coordinates to pan tilt settings. To control the pan tilt to aim at 
 * a particular visual location in the field of view of a silicon retina, see 
 * PanTiltTracker.
 * 
 * @author tobi, rjd
 * @see #DEFAULT_PAN_SERVO
 * @see #DEFAULT_TILT_SERVO
 * @see ch.unizh.ini.jaer.hardware.pantilt.PanTiltTracker 
 */

public class PanTilt implements PanTiltInterface, LaserOnOffControl {

    private static final Logger log = Logger.getLogger("net.sf.jaer");

    private Timer panTiltTimer;

 volatile boolean lockAcquired = false;
    java.util.Timer jitterTimer, followTimer;
    
    // Pan and tilt positions
    private float   pan             = .5f,   tilt             = .5f;
    private float   previousPan     = pan,   previousTilt     = tilt;
    private float   panTarget       = pan,   tiltTarget       = tilt;
    private float   panJitterTarget = pan,   tiltJitterTarget = tilt;
    private float   limitOfPan      = .5f,   limitOfTilt      = .5f;
    private boolean panInverted     = false, tiltInverted     = false;
    
     private float jitterAmplitude = 0.0f;
    private float jitterFreqHz = 0.0f;
    private boolean jittering = false;
     
     
    private boolean jitterEnabled   = false;
    private boolean linearSpeedEnabled = false;
    private final Trajectory panTiltTrajectory = new Trajectory();
    private final Trajectory panTiltTargetTrajectory = new Trajectory();
    
    // RS4 Gimbal
     private float defaultYaw = 0 ;
    private float defaultRoll = 0 ;
     private float defaultPitch= 0 ;
    
    
    float MaxMovePerUpdate = .1f; //Max amount of servochange per update
    float MinMovePerUpdate = 0.001f; //Min amount of servochange per update 
    int   MoveUpdateFreqHz = 1000; //in Hz how often does the targeted pantilt move
 
    
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    
     private static RS4ControllerInterface gimbal = RS4Controller.getInstance();
   
    private PanTilt() {
        // Start background task for updating pan/tilt (if needed)
        panTiltTimer = new Timer(true);
        
        
        panTiltTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updatePanTilt();
            }
        }, 0, 1000);
    
        // Ensure gimbal controller is ready
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                log.info("Shutting down RS4 Gimbal Controller");
                gimbal.shutdown();
            }
        });
    
    }

          // Inner static helper class responsible for holding the Singleton instance
        private static class SingletonHelper {
            private static final PanTilt INSTANCE = new PanTilt();
        }
        
        // Public method to provide access to the instance
        public static synchronized PanTilt getInstance() {
            return SingletonHelper.INSTANCE;
        }
   
        
        public void addPropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.removePropertyChangeListener(listener);
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
       
   
    /** Acquire ownership (from PanTiltInterface) */
    @Override
    public void acquire() {
        log.info("PanTilt acquired");
    }

    /** Check if lock is owned (from PanTiltInterface) */
    @Override
    public boolean isLockOwned() {
        return true; // Example logic for lock check
    }

    /** Release ownership (from PanTiltInterface) */
    @Override
    public void release() {
        log.info("PanTilt released");
    }

    
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
            } catch(Exception ex) {
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
   
        jittering = true;
        log.info("Jittering started with amplitude " + jitterAmplitude + " and frequency " + jitterFreqHz);
    }

    /** Stops the jittering */
    @Override public void stopJitter() {
        if (jitterTimer != null) {
            jitterTimer.cancel();
            jitterTimer = null;
            
            jittering = false;
            log.info("Jittering stopped");    
        }
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
    @Override synchronized public void setPanTiltValues(float newPan, float newTilt) {
       
        previousPan  = this.pan;
        previousTilt = this.tilt;

        newPan  = clipPan(newPan); //clip values according to set limits
        newTilt = clipTilt(newTilt);
        
        this.pan  = newPan;
        this.tilt = newTilt; //efferent copy
        
        if(panInverted) newPan  = 1-newPan; //invert values if selected
        if(tiltInverted)newTilt = 1-newTilt;

        
         // Use RS4Controller's setPosControl method
        gimbal.setPosition(newPan, defaultRoll, newTilt);
        log.info("Updated pan/tilt values to " + newPan + " / " + newTilt);
        
        setLaserOn(true);
        
        float[] PreviousValues = {previousPan,previousTilt};
        float[] NewValues      = {this.pan,this.tilt};
        panTiltTrajectory.add(this.pan, this.tilt);
        this.pcs.firePropertyChange("PanTiltValues", PreviousValues , NewValues);
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
   
    //  fail safe - while I get to understand tobi's code == rjd
    public float[] getPanTiltPosition() {
        return new float[]{ gimbal.getYaw(),  gimbal.getPitch() };
    }

    /** Clips the pan value based on the set limits. */
    private float clipPan(float pan) {
        return Math.max(0, Math.min(1, pan)); // Adjust limits as necessary
    }

    /** Clips the tilt value based on the set limits. */
    private float clipTilt(float tilt) {
        return Math.max(0, Math.min(1, tilt)); // Adjust limits as necessary
    }

    /**
     * Updates the pan and tilt values periodically using a timer.
     * Implements jittering effect if jitter is active.
     */
    private void updatePanTilt() {
        if (jittering) {
            float jitterValue = (float) (jitterAmplitude * Math.sin(2 * Math.PI * jitterFreqHz * System.currentTimeMillis() / 1000.0));
            float newPan = pan + jitterValue;
            float newTilt = tilt + jitterValue;
            newPan = clipPan(newPan);
            newTilt = clipTilt(newTilt);
            gimbal.setPosition(newPan, defaultRoll, newTilt);  // PanTilt does not considerRoll
            log.info("Jittering updated pan/tilt values to " + newPan + " / " + newTilt);
        }
    }

       
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

    public com.inilabs.jaer.gimbal.PanTilt.Trajectory getPanTiltTrajectory() {
        return panTiltTrajectory;
    }
    
    public void resetPanTiltTrajectory() {
        panTiltTrajectory.clear();
    }
    
    public com.inilabs.jaer.gimbal.PanTilt.Trajectory getPanTiltTargetTrajectory() {
        return panTiltTargetTrajectory;
    }
    
    public void resetPanTiltTargetTrajectory() {
        panTiltTargetTrajectory.clear();
    }
       
   /** Shutdown the PanTilt system */
    public void close() {
        log.info("Shutting down PanTilt system...");
        panTiltTimer.cancel();
        gimbal.shutdown();
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
    
  
    public void setPanInverted(boolean yes){
        panInverted = yes;
    }
    
      public void setTiltInverted(boolean yes){
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
