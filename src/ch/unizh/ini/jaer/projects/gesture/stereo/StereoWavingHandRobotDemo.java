/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.gesture.stereo;
import ch.unizh.ini.jaer.hardware.pantilt.PanTilt;
import ch.unizh.ini.jaer.projects.gesture.virtualdrummer.BlurringFilter2DTracker.Cluster;
import com.sun.opengl.util.GLUT;
import java.awt.geom.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.hardwareinterface.*;
import net.sf.jaer.util.filter.LowpassFilter2d;
/**
 *  Simple demonstration of stereo disparity using pantilt servo to wave hand to show output of stereo blob tracking.
 * @author tobi delbruck, junheang lee
 *
 * This is part of jAER
<a href="http://jaer.wiki.sourceforge.net">jaer.wiki.sourceforge.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public class StereoWavingHandRobotDemo extends EventFilter2D implements FrameAnnotater,Observer, PropertyChangeListener{
    private float servoLimit = getPrefs().getFloat("StereoWavingHandRobotDemo.servoLimit",0.25f);
    private float disparityScaling = getPrefs().getFloat("StereoWavingHandRobotDemo.disparityScaling",0.05f);
    private float tauArmMs = getPrefs().getFloat("StereoWavingHandRobotDemo.tauArmMs",20);
    private FilterChain chain = null;
    BlurringFilterStereoTracker tracker = null;
    PanTilt panTilt = null;
    LowpassFilter2d filter = new LowpassFilter2d(tauArmMs);
    private final int SERVO_RETRY_INTERVAL = 300;
    private int sizeX = 0, sizeY = 0;
    private boolean servosEnabled=getPrefs().getBoolean("StereoWavingHandRobotDemo.servosEnabled",true);
    private float panOffset=getPrefs().getFloat("StereoWavingHandRobotDemo.panOffset",.5f);
    private float tiltOffset=getPrefs().getFloat("StereoWavingHandRobotDemo.tiltOffset",.5f);

    public StereoWavingHandRobotDemo (AEChip chip){
        super(chip);
        setPropertyTooltip("servoLimit","limits servos to this value around 0.5f");
        setPropertyTooltip("disparityScaling","disparity values are scaled by this factor and divided by disparity limit of BlurringFilterStereoTracker to arrive at servo position");
        setPropertyTooltip("servosEnabled","enable servo outputs");
        setPropertyTooltip("tauArmMs","lowpass filter time constant for pan-tilt outputs in ms");
        setPropertyTooltip("panOffset","center position of pan in 0-1 space");
        setPropertyTooltip("tiltOffset","center position of tilt in 0-1 space");
        tracker = new BlurringFilterStereoTracker(chip);
        tracker.addObserver(this);
        chain = new FilterChain(chip);
        chain.add(tracker);
        setEnclosedFilterChain(chain);
        chip.addObserver(this);
       
    }

    @Override
    synchronized public EventPacket<?> filterPacket (EventPacket<?> in){
        if(sizeX == 0){
            sizeX = chip.getSizeX();
            sizeY = chip.getSizeY();
        }
        in = tracker.filterPacket(in);
//        moveArm();  // rely on updates from tracker at updateIntervalMs to move arm
        return in;
    }

    @Override
    synchronized public void resetFilter (){
        setPanTilt(.5f,.5f);
        filter.setInternalValue2d(.5f,.5f);
        setPanTilt(.5f,.5f);
        getEnclosedFilterChain().reset();
    }

    private float clipServo (float f){
        if ( f < 0.5f - servoLimit ){
            f = .5f - servoLimit;
        } else if ( f > .5f + servoLimit ){
            f = .5f + servoLimit;
        }
        return f;
    }
    private int servoRetryCounter = 0;
    private boolean printedServoWarning = false;

    private void setPanTilt (float pan,float tilt){
        if ( !servosEnabled ){
            if ( !printedServoWarning ){
                printedServoWarning = true;
                log.warning("Set servosEnabled true to enable servo output");
            }
            return;
        }
        pan = clipServo(pan);
        tilt = clipServo(tilt);
        if ( panTilt != null && servoRetryCounter < 0 ){ // don't keep retrying every time
            try{
                panTilt.setPanTiltValues(pan,tilt);
            } catch ( HardwareInterfaceException e ){
                log.warning(e.toString());
                servoRetryCounter = SERVO_RETRY_INTERVAL;
            }
        }
        servoRetryCounter--;
    }

    @Override
    public void initFilter (){
        panTilt = new PanTilt();
        resetFilter();
    }
    private GLUT glut = new GLUT();

    public void annotate (GLAutoDrawable drawable){
        GL gl = drawable.getGL();
        gl.glPushMatrix();
        gl.glRasterPos3f(1,chip.getSizeY() - 1,0);
        Point2D.Float p = filter.getValue2d();
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,String.format("pan=%.2f tilt=%.2f",p.x,p.y));
//       gl.glColor3f(0,0,1);
//        gl.glLineWidth(5);
//        final int W=10;
//        float x=p.x*chip.getSizeX();
//        float y=p.y*chip.getSizeY();
//        gl.glRectf(x-W,y-W,x+W,y+W);

        gl.glPopMatrix();
    }

    @Override
    public synchronized void setFilterEnabled (boolean yes){
        super.setFilterEnabled(yes);
        if ( yes ){
            if ( panTilt == null ){
                panTilt = new PanTilt();
            }
        } else{
            if ( panTilt != null ){
                panTilt.close();
            }
        }
    }

    private Cluster findCluster (){
        List<Cluster> clusters = tracker.getClusters();
        Cluster sc = null;
        for ( Cluster c:clusters ){

            if ( c.isVisible() ){
                sc = c;
                break;
            }
        }
        return sc;
    }

    private void moveArm (int timestamp){
        Cluster sc = findCluster();
        if ( sc != null ){
            Point2D.Float p = sc.getLocation();
            float disparity = (float)(((tracker.getDisparity() ) / (chip.getSizeX()*.7f))+1)/2 * disparityScaling;
            float tilt = (float)p.getY() / sizeY;
            float pan = (float)p.getX() / sizeX;

            tilt=tilt+(tiltOffset*2-0.5f);
            pan=pan+(panOffset*2-.5f);

            tilt = 0.5f + 2.0f*servoLimit * ((1.0f - 0.8f*disparity)*tilt - 0.5f);
            pan = 0.5f + 2.0f*servoLimit * ((1.0f - 0.8f*disparity)*pan - 0.5f);

            Point2D.Float pt = filter.filter2d(pan,tilt,timestamp);
            setPanTilt(pt.x,pt.y);
        }
    }

    synchronized public void update (Observable o,Object arg){
        if ( arg instanceof UpdateMessage ){
            UpdateMessage msg = (UpdateMessage)arg;
            moveArm(msg.timestamp);
//            log.info("update from "+o+" with message "+arg.toString());
        }
//         if(chip.getAeViewer()!=null){
//             log.info("adding "+this+" to propertyChangeListeners for AEViewer");
//            chip.getAeViewer().getSupport().addPropertyChangeListener(AEViewer.EVENT_TIMESTAMPS_RESET,this);
//        }
    }

    /**
     * @return the servoLimit
     */
    public float getServoLimit (){
        return servoLimit;
    }

    /**
     * @param servoLimit the servoLimit to set
     */
    synchronized public void setServoLimit (float servoLimit){
        servoLimit = servoLimit < 0 ? 0 : servoLimit;
        servoLimit = servoLimit > 1 ? 1 : servoLimit;
        this.servoLimit = servoLimit;
        getPrefs().putFloat("StereoWavingHandRobotDemo.servoLimit",servoLimit);
    }

    /**
     * @return the disparityScaling
     */
    public float getDisparityScaling (){
        return disparityScaling;
    }

    /**
     * @param disparityScaling the disparityScaling to set
     */
    synchronized public void setDisparityScaling (float disparityScaling){
        this.disparityScaling = disparityScaling;
        getPrefs().putFloat("StereoWavingHandRobotDemo.disparityScaling",disparityScaling);
    }

    /**
     * @return the tauMs
     */
    public float getTauArmMs (){
        return tauArmMs;
    }

    /**
     * The lowpass time constant of the arm.
     *
     * @param tauMs the tauMs to set
     */
    synchronized public void setTauArmMs (float tauMs){
        this.tauArmMs = tauMs;
        getPrefs().putFloat("StereoWavingHandRobotDemo.tauArmMs",tauMs);
        filter.setTauMs(tauArmMs);
    }

    /**
     * @return the servosEnabled
     */
    public boolean isServosEnabled (){
        return servosEnabled;
    }

    /**
     * @param servosEnabled the servosEnabled to set
     */
    synchronized public void setServosEnabled (boolean servosEnabled){
        this.servosEnabled = servosEnabled;
        getPrefs().putBoolean("StereoWavingHandRobotDemo.servosEnabled",servosEnabled);
    }

    public void propertyChange (PropertyChangeEvent evt){
        if(evt.getPropertyName().equals(AEViewer.EVENT_TIMESTAMPS_RESET)){
            log.info("resetting filter after receiving timestamp reset event from AEViewr");
            resetFilter();
        }
    }

    /**
     * @return the panOffset
     */
    public float getPanOffset (){
        return panOffset;
    }

    private float clip01(float f){
        if(f<0) f=0; else if(f>1) f=1;
        return f;
    }
    /**
     * @param panOffset the panOffset to set
     */
    public void setPanOffset (float panOffset){
        panOffset=clip01(panOffset);
        this.panOffset = panOffset;
        getPrefs().putFloat("StereoWavingHandRobotDemo.panOffset",panOffset);
    }

    /**
     * @return the tiltOffset
     */
    public float getTiltOffset (){
        return tiltOffset;
    }

    /**
     * @param tiltOffset the tiltOffset to set
     */
    public void setTiltOffset (float tiltOffset){
        tiltOffset=clip01(tiltOffset);
        this.tiltOffset = tiltOffset;
        getPrefs().putFloat("StereoWavingHandRobotDemo.tiltOffset",tiltOffset);
    }

    public float getMaxPanOffset(){return 1;}
    public float getMinPanOffset(){return 0;}
   public float getMaxTiltOffset(){return 1;}
    public float getMinTiltOffset(){return 0;}

}
