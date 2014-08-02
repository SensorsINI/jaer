
package ch.unizh.ini.jaer.projects.bjoernbeyer.visualservo;

import java.awt.geom.Point2D;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.util.Vector2D;

import net.sf.jaer.eventprocessing.label.*;
import net.sf.jaer.graphics.FrameAnnotater;
import ch.unizh.ini.jaer.hardware.pantilt.*;
import ch.unizh.ini.jaer.projects.bjoernbeyer.pantiltscreencalibration.CalibrationPanTiltScreen;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 *
 * @author Bjoern
 */
@Description("Moves the DVS towards a target based on the global averaged motion estimation")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class SimpleVS_GlobalOpticFlow extends EventFilter2D implements FrameAnnotater, SimpleVStrackerInterface {
    
    private float   ThreshDir      = getFloat("threshDir", 10f);
    private float   MaxMove        = getFloat("maxMove",0.5f);
    private float   mixingFactor      = getFloat("mixingFactor",0.1f);
    private boolean TrackerEnabled = getBoolean("trackerEnabled", false);
    private final PanTilt panTilt;
    private final CalibrationPanTiltScreen retinaPTCalib;
    
//    private final Point2D.Float GlobalDir; 
    private Vector2D velocityPPS = new Vector2D();
    private Vector2D smoothVelocityPPS = new Vector2D();
    private float[] retChange = new float[3];
    private float[] curPos;
    private Vector2D ptSpeedPerSecond = new Vector2D();
    
    DvsDirectionSelectiveFilter DirFilter;
    
    public SimpleVS_GlobalOpticFlow(AEChip chip) {
        super(chip);
        
        retinaPTCalib = new CalibrationPanTiltScreen("retinaPTCalib");
        DirFilter = new DvsDirectionSelectiveFilter(chip);
            DirFilter.setAnnotationEnabled(false);
            DirFilter.setShowRawInputEnabled(false);
        panTilt = PanTilt.getInstance(0);
            panTilt.setLimitOfPan(retinaPTCalib.getLimitOfPan());
            panTilt.setLimitOfTilt(retinaPTCalib.getLimitOfTilt());
            panTilt.setLinearSpeedEnabled(true); //we need the speed to be linear so that we are able to set a target speed instead of a fraction of the distance that should be covered.
            
            
//        GlobalDir = new Point2D.Float(0,0);
        
        setEnclosedFilter(DirFilter);

        setPropertyTooltip("threshDir", "Threshold when a global motion should be taken into account");
        setPropertyTooltip("maxMove", "maximum distance the pantilt moves per event");
        setPropertyTooltip("factorDec", "forgetfullness of old global motions");
        setPropertyTooltip("trackerEnabled", "Move the PanTilt to the target");
        
        setPropertyTooltip("switchTracking","switches the tracking of the pantilt.");
    }
    
    @Override public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
        
        getEnclosedFilter().filterPacket(in);
        
        velocityPPS.setLocation(DirFilter.getTranslationVector());
        
         System.out.println("velocity: "+velocityPPS.toString());

        if(velocityPPS.length() > getThreshDir()) { //distance to origin gives absolut value which needs to be larger threshold for the direction to register
            //smoothing the velocity with a mixing factor from the last averaged value
            smoothVelocityPPS.mult(getMixingFactor());
            smoothVelocityPPS.addFraction(velocityPPS, (1-getMixingFactor()));
            System.out.println("SmoothVelocity: "+smoothVelocityPPS.toString());
            //As the retina-PanTilt calibration is a differential calibration we
            // need to give it the change vector in retinal coordinates and it will
            // give the corresponding change vector in panTilt coordinates.
            // We want to set the maximum Speed the panTilt can move to the speed
            // in panTiltPerSecond and set the target far in the direction of the 
            // global opticFlow velocity. This should make the panTilt move with
            // the speed of the optic flow in the direction of the optic flow
            // Hence if a object where to start in the center of the retina and
            // where the only thing visible and opticflow estimation would be
            // perfect it would stay centered.
            retChange[0] = -smoothVelocityPPS.x;
            retChange[1] = -smoothVelocityPPS.y;
            retChange[2] = 0f;
            
            //calculate the speed of the optic flow in panTilt Value change per Second
            // by transforming the detected retinal optic flow velocity with the 
            // calibration.
            ptSpeedPerSecond.setLocation(retinaPTCalib.makeTransform(retChange)); 
            System.out.println("ptSpeed: "+ptSpeedPerSecond.toString());
            if(isTrackerEnabled()) {
                //By dividing the length with the update frequency of the panTilt 
                panTilt.setMaxMovePerUpdate((float) ptSpeedPerSecond.length()/panTilt.getMoveUpdateFreqHz());
                curPos = panTilt.getPanTiltValues();
                panTilt.setTarget(curPos[0]+ptSpeedPerSecond.x, curPos[1]+ptSpeedPerSecond.y);
            }
//            GlobalDir.x = GlobalDir.x*getMixingFactor() + velocityPPS.x/2000; 
//            GlobalDir.y = GlobalDir.y*getMixingFactor() + velocityPPS.y/2000;
//            float GlobalSpeed = (float)GlobalDir.distance(0,0);
//            
//            Point2D.Float Move = new Point2D.Float(0,0);
//            
//            //        UnitVector        *Min of absolute speed and maxmove
//            Move.x = (GlobalDir.x/GlobalSpeed)*Math.min(GlobalSpeed, MaxMove);
//            Move.y = (GlobalDir.y/GlobalSpeed)*Math.min(GlobalSpeed, MaxMove);
//            
//            //System.out.println(Float.toString(GlobalSpeed));
//            //System.out.println(Float.toString(Math.min(GlobalSpeed, MaxMove)));
//            //System.out.println(Move.toString());
//            
//            if(isTrackerEnabled()) {
//                float[] PanTiltPos = panTilt.getPanTiltValues();
//                float[] NewPos = {0.5f,0.5f};
//                NewPos[0] = PanTiltPos[0]-Move.x; 
//                NewPos[1] = PanTiltPos[1]+Move.y;
//                //We dont need to pay attention to weather the new values
//                // are larger 1 or smaller 0 as the values are clipped by
//                // 'setPanTiltValues' anyway.
//                
//               panTilt.setTarget(NewPos[0],NewPos[1]); 
//                //float[] NewPanTiltPos = PTAimer.getPanTiltValues();
//                //System.out.println(Float.toString(NewPanTiltPos[0])+" - "+Float.toString(NewPanTiltPos[1]));
//            }
        }
        return in;
    }
    
    @Override public void resetFilter() {
        DirFilter.resetFilter();
    }

    @Override public void initFilter() {
        resetFilter();
    }

    @Override public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled()) return;

        DirFilter.annotate(drawable);
     }
    
    public void doAim() {
        PanTiltAimerGUI aimerGui = new PanTiltAimerGUI(PanTilt.getInstance(0));
        aimerGui.setVisible(true);
    }
    
    /** Switch the tracking mode
     * Sets the tracking mode to the opposite of the current mode */
    public void doSwitchTracking() {
        setTrackerEnabled(!isTrackerEnabled());
    }
    
    public void doCenterPT() {
        setTrackerEnabled(false);
        panTilt.setTarget(.5f, .5f);
    }
    
    public void doCheckCalibration() {
        if(!retinaPTCalib.isCalibrated()){
            System.out.println("No calibration found!");
        } else {
            retinaPTCalib.displayCalibration(10);
        }
    }
    
    public void doDisableServos() {
        setTrackerEnabled(false);
        try {
            panTilt.disableAllServos();
        } catch (HardwareInterfaceException ex) {
            log.warning(ex.getMessage());
        }
    }
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --FactorDec--">
    public float getMixingFactor(){
        return mixingFactor;
    }
    
    public void setMixingFactor(float FactorDec) {
        putFloat("mixingFactor",FactorDec);
        float OldValue = this.mixingFactor;
        this.mixingFactor = FactorDec;
        support.firePropertyChange("mixingFactor",OldValue,FactorDec);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter-setter for --ThreshDir--">
    public float getThreshDir(){
        return ThreshDir;
    }
    
    public void setThreshDir(float TreshDir) {
        putFloat("threshDir",ThreshDir);
        float OldValue = this.ThreshDir;
        this.ThreshDir = TreshDir;
        support.firePropertyChange("threshDir",OldValue,ThreshDir);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter-setter for --MaxMove--">
    public float getMaxMove(){
        return MaxMove;
    }
    
    public void setMaxMove(float MaxMove) {
        putFloat("maxMove",MaxMove);
        float OldValue = this.MaxMove;
        this.MaxMove = MaxMove;
        support.firePropertyChange("maxMove",OldValue,MaxMove);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="checker-setter for --TrackerEnabled--">
    public boolean isTrackerEnabled() {
        return TrackerEnabled;
    }

    /**
     * @param EnableTracker
     */
    public void setTrackerEnabled(boolean EnableTracker) {
        putBoolean("trackerEnabled", EnableTracker);
        boolean OldValue = this.TrackerEnabled;
        this.TrackerEnabled = EnableTracker;
        support.firePropertyChange("trackerEnabled",OldValue,EnableTracker);
    }
    // </editor-fold>  
}
