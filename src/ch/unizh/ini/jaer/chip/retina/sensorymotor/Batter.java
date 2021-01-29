/*
 * Batter.java
 *
 * Created on July 23, 2006, 11:48 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright July 23, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.jaer.chip.retina.sensorymotor;

import java.awt.Graphics2D;
import java.util.logging.Logger;


import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BinocularEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.ServoInterface;
import net.sf.jaer.hardwareinterface.usb.silabs.SiLabsC8051F320_USBIO_ServoController;
import net.sf.jaer.stereopsis.StereoClusterTracker;
import ch.unizh.ini.jaer.chip.stereopsis.Tmpdiff128StereoPair;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import net.sf.jaer.event.BasicEvent;

/**
 * Controls a batting robot that uses two servo motors and two retinas in stereo vision to hit a ball.
 <p>
 Ball tracking uses a modified ClusterTracker to simultaneously track
 the ball in two retinas. The ball is independently tracked
 in each retina and the tracking is constrained, or hinted, by the
 tracking in the other retina. Initially there are no visible clusters
 in either retina. As soon as there is a visible cluster in one retina the
 cluster in the other retina is initialized with the first retina's cluster.
 Then the two clusters have an average x,y location and a disparity that is used
 to compute the distance to the ball.
 
 * @author tobi
 */
public class Batter extends EventFilter2D implements FrameAnnotater {
    static protected Logger log=Logger.getLogger("Batter");
    
    final int SWING_DELAY_MS_DEFAULT=350;
    final int DISABLE_SERVOS_DELAY_MS=10000;
    
    ServoInterface servo=null;
    Tmpdiff128StereoPair stereoChip=null;
    BallTracker tracker=null;
    private float swingBallDistance=getPrefs().getFloat("Batter.swingBallDistance",0.6f); // distance of ball to start swinging bat
    private boolean swapServos; // true to swap swing and height servos
    private float swingHeight=getPrefs().getFloat("Batter.swingHeight",0f); // in servo coordinates
    private float eyeHeightM=getPrefs().getFloat("Batter.eyeHeight",0.3f); // in meters over batter zero position
    private float eyeAngleDeg=getPrefs().getFloat("Batter.eyeAngleDeg",0); // degrees angle of eyes looking down over batter bat
    private float swingHeightMin=getPrefs().getFloat("Batter.swingHeightMin",.35f);
    private float swingHeightMax=getPrefs().getFloat("Batter.swingHeightMax",.45f);
    private float swingHeightRest=(getSwingHeightMax()+getSwingHeightMin())/2;
    private float swingAngleMax=getPrefs().getFloat("Batter.swingAngleMax",1f);
    private boolean dynamicSwingHeightEnabled=getPrefs().getBoolean("Batter.dynamicSwingHeightEnabled",true);
    
    int levelServo=0, batServo=1;
    
    BatSwinger batSwinger=null;
    
    /** Creates a new instance of Batter */
    public Batter(AEChip chip) {
        super(chip);
        if(chip!=null && chip instanceof Tmpdiff128StereoPair){
            stereoChip=(Tmpdiff128StereoPair)chip;
        }
        setSwapServos(getPrefs().getBoolean("Batter.swapServos",false));
        tracker=new BallTracker(chip);
        setEnclosedFilter(tracker);
    }
    
    float lastBallDistance=Float.POSITIVE_INFINITY;
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        if(!isFilterEnabled()) return in;
        tracker.filterPacket(in);
        if(batSwinger==null){
            batSwinger=new BatSwinger(this);
            batSwinger.start();
        }
        
        // following is rule for starting dwing
        if( batSwinger.swinging==false && tracker.isBall()){
            BallTracker.StereoCluster ball=tracker.getBall();
            float ballDistance=tracker.getBallDistanceM();
            float disparityVelocity=ball.getDisparityVelocity();
            if( ballDistance<getSwingBallDistance() && disparityVelocity>0){
//                System.out.println(System.currentTimeMillis()+" swing at ballDistance="+ballDistance+" disparityVelocity="+disparityVelocity+" swingHeight="+swingHeight);
                computeDynamicSwingHeight(ball);
                log.info("starting swing with ball "+tracker.getBall());
                batSwinger.swing();
//                tracker.initializeBall(); // to try to avoid tracking bat, bad idea
            }
            lastBallDistance=ballDistance;
        }
        batSwinger.checkRelaxServos();
        return in;
    }
    
    private void computeDynamicSwingHeight(BallTracker.StereoCluster ball) {
        // the ball exists and we are set to go to swing bat, now we set swing height
        // using ball y location
        float y=ball.location.y/(chip.getSizeY()); // fraction up in picture
        float s=getSwingHeightMin()+(getSwingHeightMax()-getSwingHeightMin())*y;
//        s=1-s; // servo is flipped so that larger value actually swings lower
        batSwinger.setDynamicSwingHeight(s);
    }
    
    void swingBat(){
        if(batSwinger==null) return;
        batSwinger.swing();
    }
    
    class BatSwinger extends Thread {
        Object caller;
        boolean stopme=false;
        volatile boolean swinging=false;
        private long lastSwingStartTimeMs=System.currentTimeMillis();
        boolean servosDisabled=false;
        
        public Object getCaller() {
            return caller;
        }
        private float dynamicSwingHeight=.5f;
        
        public BatSwinger(Object caller){
            this.caller=caller;
            setPriority(Thread.MAX_PRIORITY);
            setDaemon(true);
        }
        
        synchronized void swing(){
            if(swinging) return;
            notify(); // notify this to run (the run method)
        }
        
        synchronized void checkSwingHeight(float f){
            if(swinging) return;
            if(checkHardware()){
                try{
                    servo.setServoValue(levelServo,1-f);
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        }
        
        public void run(){
            while(!stopme){
                synchronized(this){
                    try{
                        wait(); // wait until we are notified
                        swinging=true;
                    }catch(InterruptedException e){
                        e.printStackTrace();
                    }
                }
                if(checkHardware()){
                    try{
                        if(isDynamicSwingHeightEnabled()){
                            servo.setServoValue(levelServo,1-dynamicSwingHeight); // set dynammic height of swing
                        }else{
                            servo.setServoValue(levelServo,1-swingHeight); // set height of swing
                        }
                        servo.setServoValue(batServo,swingAngleMax); // swing forward
                        delayMs(SWING_DELAY_MS_DEFAULT); // delay for swing
                        servo.setServoValue(batServo,0); // rtn to start
                        checkSwingHeight(swingHeightRest);
                        lastSwingStartTimeMs=System.currentTimeMillis();
                        servosDisabled=false;
                        delayMs(SWING_DELAY_MS_DEFAULT*2); // wait for this to happen, wait twice as long to try to inhibit swings based on seeing bat move
                    }catch(Exception e){
                        e.printStackTrace();
                    }finally{
                    }
                }
                swinging=false;
            }
        }
        
        public float getDynamicSwingHeight() {
            return dynamicSwingHeight;
        }
        
        public void setDynamicSwingHeight(float dynamicSwingHeight) {
            this.dynamicSwingHeight = dynamicSwingHeight;
        }
        
        public long getLastSwingStartTimeMs() {
            return lastSwingStartTimeMs;
        }
        
        synchronized private void disableServos() {
            if(checkHardware()){
                try{
                    log.info("disabling servos after inactivity");
                    servo.disableAllServos();
                    servosDisabled=true;
                }catch(Exception e){
                    e.printStackTrace();
                }finally{
                }
            }
            swinging=false;
        }
        
        synchronized private void checkRelaxServos(){
            if(servosDisabled) return;
            if(System.currentTimeMillis()-batSwinger.getLastSwingStartTimeMs()>DISABLE_SERVOS_DELAY_MS){
                batSwinger.disableServos();
            }
        }
    }
    
    
    
    void delayMs(int ms){
        try{
            Thread.currentThread().sleep(ms);
        }catch(InterruptedException e){}
    }
    
    public Object getFilterState() {
        return null;
    }
    
    public void resetFilter() {
    }
    
    public void initFilter() {
    }
    
    @Override synchronized public void setFilterEnabled(boolean yes){
        super.setFilterEnabled(yes);
        if(!yes && servo!=null){
            ServoInterface s=(ServoInterface)servo;
            try{
                s.disableAllServos();
                s.close();
            }catch(HardwareInterfaceException e){
                e.printStackTrace();
            }
            servo=null;
        }
    }
    
    boolean checkHardware(){
        if(servo==null){
            servo=new SiLabsC8051F320_USBIO_ServoController();
        }
        if(!servo.isOpen()){
            try{
                servo.open();
            }catch(HardwareInterfaceException e){
                servo=null;
//                e.printStackTrace();
            }
        }
        if(servo!=null && servo.isOpen()) return true; else return false;
    }
    
    public void annotate(float[][][] frame) {
    }
    
    public void annotate(Graphics2D g) {
    }
    
    public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled()) return;
        GL2 gl=drawable.getGL().getGL2();
    }
    
    class BallTracker extends StereoClusterTracker{
        
        private StereoCluster newBallCluster;
        BallTracker(AEChip chip){
            super(chip);
            BinocularEvent e=new BinocularEvent();
            e.x=64;
            e.y=127;
            e.eye=BinocularEvent.Eye.RIGHT;
            newBallCluster=new StereoCluster(e);
        }
        /**
         @return ball if there is one, or null otherwise
         */
        StereoCluster getBall(){
            if(getNumClusters()==0) return null;
            StereoCluster ballCluster=tracker.getNearestCluster();
            if(ballCluster==null || !ballCluster.isVisible()) return null;
            return ballCluster;
        }
        /** @return true if a ball is detected
         */
        boolean isBall(){
            if(getNumClusters()==0) return false;
            StereoCluster ballCluster=tracker.getNearestCluster();
            if(ballCluster==null || !ballCluster.isVisible()) return false;
            return true;
        }
        /** @return ball distance in meters, or Float.POSITIVE_INFINITY if there is no ball
         */
        float getBallDistanceM(){
            if(!isBall()) return Float.POSITIVE_INFINITY;
            StereoCluster ball=getBall();
            float distanceM=ball.location3dm.z;
            return distanceM;
        }
        
        /** @return elevation in meters in eye-centered coordinates or zero if there is no ball */
        float getBallElevationM(){
            if(!isBall()) return 0;
            StereoCluster ball=getBall();
            float elevationM=ball.location3dm.y;
            return elevationM;
        }
        
        private void initializeBall() {
            getClusters().clear();
            getClusters().add(newBallCluster);
        }
        
    }
    
    public float getSwingBallDistance() {
        return swingBallDistance;
    }
    
    public void setSwingBallDistance(float swingDistance) {
        this.swingBallDistance = swingDistance;
        getPrefs().putFloat("Batter.swingBallDistance",swingDistance);
        swingBat();
    }
    
    public boolean isSwapServos() {
        return swapServos;
    }
    
    public void setSwapServos(boolean swapServos) {
        this.swapServos = swapServos;
        getPrefs().putBoolean("Batter.swapServos",swapServos);
        if(swapServos){
            levelServo=1;
            batServo=0;
        }else{
            levelServo=0;
            batServo=1;
        }
    }
    
    public float getSwingHeightMin() {
        return swingHeightMin;
    }
    
    public void setSwingHeightMin(float swingHeightMin) {
        if(swingHeightMin>swingHeightMax) swingHeightMin=swingHeightMax;
        swingHeightMin=clip01(swingHeightMin);
        this.swingHeightMin = swingHeightMin;
        getPrefs().putFloat("Batter.swingHeightMin",swingHeightMin);
        swingHeightRest=(getSwingHeightMax()+getSwingHeightMin())/2;
        if(batSwinger!=null) batSwinger.checkSwingHeight(swingHeightMin);
    }
    
    public float getSwingHeightMax() {
        return swingHeightMax;
    }
    
    public void setSwingHeightMax(float swingHeightMax) {
        if(swingHeightMax<swingHeightMin) swingHeightMax=swingHeightMin;
        swingHeightMax=clip01(swingHeightMax);
        this.swingHeightMax = swingHeightMax;
        getPrefs().putFloat("Batter.swingHeightMax",swingHeightMax);
        swingHeightRest=(getSwingHeightMax()+getSwingHeightMin())/2;
        if(batSwinger!=null) batSwinger.checkSwingHeight(swingHeightMax);
    }
    
    public float getSwingAngleMax() {
        return swingAngleMax;
    }
    
    /** sets max extent of swing */
    public void setSwingAngleMax(float swingAngleMax) {
        this.swingAngleMax = swingAngleMax;
        swingAngleMax=clip01(swingAngleMax);
        getPrefs().putFloat("Batter.swingAngleMax",swingAngleMax);
        swingBat();
    }
    
    float clip01(float f){
        if(f>1) f=1; else if(f<0) f=0;
        return f;
    }
    
    public boolean isDynamicSwingHeightEnabled() {
        return dynamicSwingHeightEnabled;
    }
    
    public void setDynamicSwingHeightEnabled(boolean dynamicSwingHeightEnabled) {
        this.dynamicSwingHeightEnabled = dynamicSwingHeightEnabled;
        getPrefs().putBoolean("Batter.dynamicSwingHeightEnabled",dynamicSwingHeightEnabled);
    }
    
}
