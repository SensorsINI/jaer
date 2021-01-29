/*
 * ServoReaction.java
 *
 * Created on July 5, 2006, 1:17 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright July 5, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.jaer.chip.retina.sensorymotor;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;


import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.tracking.MedianTracker;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.ServoInterface;
import net.sf.jaer.hardwareinterface.usb.silabs.SiLabsC8051F320_USBIO_ServoController;

/**
 * Controls a servo motor to servo to some measuure of location computed from events. Used in Telluride to make a two-arm mimic that waves
 at you when you wave at the retina, and to make a fast goalie that swings an arm in the way of a ball rolling towards a goal box.

 * @author tobi
 */
public class ServoReaction extends EventFilter2D implements FrameAnnotater{

    private boolean flipX=getPrefs().getBoolean("ServoReaction.flipX",false);
    private boolean useClusterTracker=getPrefs().getBoolean("ServoReaction.useClusterTracker",false);
    private float gain=getPrefs().getFloat("ServoReaction.gain",1f);
    private boolean goalieMode=getPrefs().getBoolean("ServoReaction.goalieMode",false);
    private boolean useVelocityForGoalie=getPrefs().getBoolean("ServoReaction.useVelocityForGoalie",true);

    private float goaliePosition=.5f;  // servo motor control makes high servo values on left of picture when viewed looking from retina
    // 0.5f is in middle. 0 is far right, 1 is far left
    private long lastServoPositionTime=0;
    final long GOALIE_RELAX_SERVO_DELAY_TIME_MS=100;


    /**
     * Creates a new instance of ServoReaction
     */
    public ServoReaction(AEChip chip) {
        super(chip);

        medianTracker=new MedianTracker(chip);
        medianTracker.setFilterEnabled(false);
        tracker=new RectangularClusterTracker(chip);
        tracker.setFilterEnabled(false);
        tracker.setMaxNumClusters(2); // ball will be closest object
//        tracker.getEnclosedFilter().setFilterEnabled(false); // turn off Kalman filter

//        getEnclosedFilter().setFilterEnabled(false); // to prevent annotation of enclosed filter
        setGoalieMode(goalieMode); // set num cluster
    }

    /** sets goalie arm.
     @param f 1 for far right, 0 for far left as viewed from above, i.e. from retina. gain value is also applied here so
     that user calibrates system such that 0 means pixel 0, 1 means pixel chip.getSizeX()
     */
    void setGoalie(float f){
        f=((f-.5f)*gain)+.5f;
        goaliePosition=f;
        try{
            ServoInterface s=(ServoInterface)servo;
            s.setServoValue(1,1-goaliePosition); // servo is servo 1 for goalie
            lastServoPositionTime=System.currentTimeMillis();
        }catch(HardwareInterfaceException e){
            e.printStackTrace();
        }

    }

    MedianTracker medianTracker;
    RectangularClusterTracker tracker;

    @Override
	synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        if(!isFilterEnabled()) {
			return in;
		}
        checkHardware();
        if(servo==null) {
			return in;
		}
        getEnclosedFilter().setFilterEnabled(true);
        if(useClusterTracker){
            tracker.getEnclosedFilter().setFilterEnabled(false);
        }
        getEnclosedFilter().filterPacket(in);
        if(useClusterTracker){
            // each of two clusters are used to control one servo
            final int servoLeft=0, servoRight=1;
            RectangularClusterTracker.Cluster clusterLeft, clusterRight;
            if(tracker.getNumClusters()==0)
			 {
				return in; // don't bother if no clusters
			}
            if(tracker.getNumClusters()==1){
                // if there is one cluster we control the servo on the appropriate side
                RectangularClusterTracker.Cluster cluster=getClosestCluster();
                Point2D p=cluster.getLocation();
                if(!cluster.isVisible() && ((System.currentTimeMillis()-lastServoPositionTime)>GOALIE_RELAX_SERVO_DELAY_TIME_MS)){
                    // not enough support
                    try{
                        ServoInterface s=(ServoInterface)servo;
                        s.disableServo(0);
                        s.disableServo(1);
                    }catch(HardwareInterfaceException e){
                        e.printStackTrace();
                    }
                }else{
                    float f;
                    if(!goalieMode){
                        // if not goalie, set servo according to y position of single cluster
                        f=(float)p.getY()/chip.getSizeY();
                    }else{
                        // goalie:
                        // compute intersection of velocity vector of cluster with bottom of view
                        // this is the place we should putString the goalie
                        // this is computed from time to reach bottom (y/vy) times vx plus the x location
                        float x=(float)p.getX();
                        if(useVelocityForGoalie){
                            Point2D.Float vel=cluster.getVelocityPPS();
                            if(vel.y<0){ // don't use vel unless ball is rolling towards goal
                                x-=((float)p.getY()/vel.y)*vel.x; // we need minus sign here because vel.y is negative
                            }
                        }
                        f=x/chip.getSizeX();
                    }
                    int ser;
                    if(goalieMode){
                        setGoalie(f);
                    }else{
                        if( p.getX()>(chip.getSizeX()/2)){
                            ser=servoRight;
                        }else{
                            ser=servoLeft;
                        }
                        f=((f-.5f)*gain)+.5f;
                        if(flipX) {
                            f=1-f;
                        }
                        try{
                            ServoInterface s=(ServoInterface)servo;
                            s.setServoValue(ser,f); // servo is servo 1 for goalie
                            lastServoPositionTime=System.currentTimeMillis();
                        }catch(HardwareInterfaceException e){
                            e.printStackTrace();
                        }
                    }
                }
            }else if(tracker.getNumClusters()==2){
                // if there are two clusters we use the left cluster's y position to control servoLeft
                // and right cluster's y position to control servoRight

                RectangularClusterTracker.Cluster cluster1=tracker.getClusters().get(0);
                RectangularClusterTracker.Cluster cluster2=tracker.getClusters().get(1);
                if(cluster1.getLocation().getX()>cluster2.getLocation().getX()){
                    clusterLeft=cluster1;
                    clusterRight=cluster2;
                }else{
                    clusterLeft=cluster2;
                    clusterRight=cluster1;
                }
                float fLeft=(float)clusterLeft.getLocation().getY()/chip.getSizeY();
                float fRight=(float)clusterRight.getLocation().getY()/chip.getSizeY();

                fLeft=1-fLeft; // counterrotating
                fLeft=applyGain(fLeft);
                fRight=applyGain(fRight);

                try{
                    ServoInterface s=(ServoInterface)servo;
                    s.setServoValue(servoLeft,fLeft);
                    s.setServoValue(servoRight,fRight);
                }catch(HardwareInterfaceException e){
                    e.printStackTrace();
                }
            }
        }else{
            Point2D p=medianTracker.getMedianPoint();
            float f=(float)p.getX()/chip.getSizeX();
            if(isFlipX()) {
				f=1-f;
			}
            f=((f-.5f)*gain)+.5f;
            try{
                ServoInterface s=(ServoInterface)servo;
                s.setServoValue(0,f);
            }catch(HardwareInterfaceException e){
                e.printStackTrace();
            }
        }
        return in;
    }


    // returns cluster with min y, assumed closest to viewer. This should filter out a lot of hands that roll the ball towards the goal.
    RectangularClusterTracker.Cluster getClosestCluster(){
        if(tracker.getNumClusters()==1) {
			return tracker.getClusters().get(0);
		}
        float minDistance=Float.POSITIVE_INFINITY, f;
        RectangularClusterTracker.Cluster closest=null;
        for(RectangularClusterTracker.Cluster c:tracker.getClusters()){
            if((f=(float)c.getLocation().getY())<minDistance) {
                minDistance=f;
                closest=c;
            }
        }
        return closest;
    }

    float applyGain(float f){
        return .5f+((f-.5f)*gain);
    }

    public Object getFilterState() {
        return null;
    }

    @Override
	public void resetFilter() {
        medianTracker.resetFilter();
        tracker.resetFilter();
    }

    @Override
	public void initFilter() {
    }

    @Override public synchronized void setFilterEnabled(boolean yes){
        super.setFilterEnabled(yes);
        if(yes){
            setUseClusterTracker(useClusterTracker);
        }
        if(!yes && (servo!=null)){
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

    HardwareInterface servo=null;

    void checkHardware(){
        if(servo==null){
            servo=new SiLabsC8051F320_USBIO_ServoController();
            try{
                servo.open();
            }catch(HardwareInterfaceException e){
                servo=null;
                e.printStackTrace();
            }
        }
    }

    public boolean isFlipX() {
        return flipX;
    }

    synchronized public void setFlipX(boolean flipX) {
        this.flipX = flipX;
        getPrefs().putBoolean("ServoReaction.flipX",flipX);
    }

    public boolean isUseClusterTracker() {
        return useClusterTracker;
    }

    synchronized public void setUseClusterTracker(boolean useClusterTracker) {
        this.useClusterTracker = useClusterTracker;
        getPrefs().putBoolean("ServoReaction.useClusterTracker",useClusterTracker);
        EventFilter2D f=getEnclosedFilter();
        if(useClusterTracker) {
            setEnclosedFilter(tracker);
            medianTracker.setFilterEnabled(false);
            tracker.setFilterEnabled(true); // for annotations
        }else{
            setEnclosedFilter(medianTracker);
            medianTracker.setFilterEnabled(true);
            tracker.setFilterEnabled(false);
        }
//        if(f!=getEnclosedFilter()){
            if(getChip().getFilterFrame()!=null)
			 {
				getChip().getFilterFrame().rebuildContents();
//        }
			}
    }

    public float getGain() {
        return gain;
    }

    public void setGain(float gain) {
        if(gain<0) {
			gain=0;
		}
		else if(gain>3) {
			gain=3;
		}
        this.gain = gain;
        getPrefs().putFloat("ServoReaction.gain",gain);
    }

    public boolean isGoalieMode() {
        return goalieMode;
    }

    public void setGoalieMode(boolean goalieMode) {
        this.goalieMode = goalieMode;
        getPrefs().putBoolean("ServoReaction.goalieMode",goalieMode);
        if(goalieMode){
            tracker.setMaxNumClusters(2);
        }else{
            tracker.setMaxNumClusters(2);
        }
    }

    public void annotate(float[][][] frame) {
    }

    public void annotate(Graphics2D g) {
    }

    @Override
	public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled() || !goalieMode) {
			return;
		}
        GL2 gl=drawable.getGL().getGL2();
        gl.glPushMatrix();
        gl.glColor3f(0,0,1);
        float f=chip.getSizeX()*goaliePosition;
        gl.glRectf(f-6,0,f+6,3);
        gl.glPopMatrix();
    }

    public boolean isUseVelocityForGoalie() {
        return useVelocityForGoalie;
    }

    public void setUseVelocityForGoalie(boolean useVelocityForGoalie) {
        this.useVelocityForGoalie = useVelocityForGoalie;
        getPrefs().putBoolean("ServoReaction.useVelocityForGoalie",useVelocityForGoalie);
    }

}
