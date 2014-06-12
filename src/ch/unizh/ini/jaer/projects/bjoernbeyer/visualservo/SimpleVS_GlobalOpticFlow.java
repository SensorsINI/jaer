/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.bjoernbeyer.visualservo;

import java.awt.geom.Point2D;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;

import net.sf.jaer.eventprocessing.label.*;
import net.sf.jaer.graphics.FrameAnnotater;
import ch.unizh.ini.jaer.hardware.pantilt.*;
import net.sf.jaer.util.Matrix;

/**
 *
 * @author Bjoern
 */
//EXTENDS EventFilter2D as we deal with asynchroneous pixel data
@Description("Moves the DVS towards a target based on the global averaged motion estimation")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class SimpleVS_GlobalOpticFlow extends EventFilter2D implements FrameAnnotater {
    
    private float   ThreshDir      = getFloat("threshDir", 10f);
    private float   MaxMove        = getFloat("maxMove",0.5f);
    private float   FactorDec      = getFloat("factorDec",0.01f);
    private boolean TrackerEnabled = getBoolean("trackerEnabled", false);
    private CalibratedStimulusGUI calibGUI;
    private final PanTilt panTilt;
    
    private ScreenActionCanvas ActionGUI;
    //Can be final because Point2D is an object with components. 
    // The components are allowed to change although final
    private final Point2D.Float GlobalDir; 
    
    DvsDirectionSelectiveFilter DirFilter;
    PanTiltAimer PTAimer;
    CalibratedScreenPanTilt calibSPT;
    
    public SimpleVS_GlobalOpticFlow(AEChip chip) {
        super(chip);
        
        ActionGUI = new ScreenActionCanvas();
        
        DirFilter = new DvsDirectionSelectiveFilter(chip);
        panTilt = PanTilt.getLastInstance();
        PTAimer = new PanTiltAimer(chip,panTilt);
        calibSPT = new CalibratedScreenPanTilt(chip,panTilt,ActionGUI);
        GlobalDir = new Point2D.Float(0,0);
        
        //We always need Pan to be inverted as of design of the pan-tilt aimer
        PTAimer.setPanInverted(true); 
        
        FilterChain filterChain = new FilterChain(chip);
        setEnclosedFilterChain(filterChain);
            filterChain.add(PTAimer);
            filterChain.add(DirFilter);
            filterChain.add(calibSPT);
        setEnclosedFilterChain(filterChain);  

        setPropertyTooltip("threshDir", "Threshold when a global motion should be taken into account");
        setPropertyTooltip("maxMove", "maximum distance the pantilt moves per event");
        setPropertyTooltip("factorDec", "forgetfullness of old global motions");
        setPropertyTooltip("trackerEnabled", "Move the PanTilt to the target");
        
        setPropertyTooltip("disableServos", "disables servos, e.g. to turn off annoying servo hum");
        setPropertyTooltip("center", "centers pan and tilt");
        setPropertyTooltip("switchTracking","switches the tracking of the pantilt.");
    }
    
    
    @Override
    /*filterPacket is neccessary to extend 'EventFilter2D'*/
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
        
        getEnclosedFilterChain().filterPacket(in);
        
        Point2D.Float MotionVec = DirFilter.getTranslationVector();
        
        // System.out.println(MotionVec.toString());

        if(MotionVec.distance(0,0) > getThreshDir()) { //distance to origin gives absolut value which needs to be larger threshold for the direction to register
            GlobalDir.x = GlobalDir.x*getFactorDec() + MotionVec.x/2000; 
            GlobalDir.y = GlobalDir.y*getFactorDec() + MotionVec.y/2000;
            float GlobalSpeed = (float)GlobalDir.distance(0,0);
            
            Point2D.Float Move = new Point2D.Float(0,0);
            
            //        UnitVector        *Min of absolute speed and maxmove
            Move.x = (GlobalDir.x/GlobalSpeed)*Math.min(GlobalSpeed, MaxMove);
            Move.y = (GlobalDir.y/GlobalSpeed)*Math.min(GlobalSpeed, MaxMove);
            
            //System.out.println(Float.toString(GlobalSpeed));
            //System.out.println(Float.toString(Math.min(GlobalSpeed, MaxMove)));
            //System.out.println(Move.toString());
            
            if(isTrackerEnabled()) {
                float[] PanTiltPos = PTAimer.getPanTiltValues();
                float[] NewPos = {0.5f,0.5f};
                NewPos[0] = PanTiltPos[0]+Move.x; 
                NewPos[1] = PanTiltPos[1]+Move.y;
                //We dont need to pay attention to weather the new values
                // are larger 1 or smaller 0 as the values are clipped by
                // 'setPanTiltValues' anyway.
                
                PTAimer.setPanTiltTarget(NewPos[0],NewPos[1]); 
                //float[] NewPanTiltPos = PTAimer.getPanTiltValues();
                //System.out.println(Float.toString(NewPanTiltPos[0])+" - "+Float.toString(NewPanTiltPos[1]));
            }
        }
        return in;
    }
    
    @Override
    /*resetFilter is neccessary to extend 'EventFilter'*/
    public void resetFilter() {
        DirFilter.resetFilter();
        PTAimer.resetFilter();
    }

    @Override
    /*initFilter is neccessary to extend 'EventFilter'*/
    public void initFilter() {
        resetFilter();
    }
    
    @Override
    /*annotate is neccessary to implement 'FrameAnnotater'*/
    public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled()) {
            return;
        }
        DirFilter.annotate(drawable);
     }
    
    // <editor-fold defaultstate="collapsed" desc="All automatically created action buttons">
    /**
     * Centers the Pan-Tilt.  
     * Built automatically into filter parameter panel as an action.
     */
    public void doCenter() {
        setTrackerEnabled(false); //So that PanTilt can move to center before tracking again
        PTAimer.doCenter();
    }
    
    /**
     * Disables all Servos and stops jitter.  
     * Built automatically into filter parameter panel as an action.
     */
    public void doDisableServos() {
        PTAimer.doDisableServos();
    }
    
    /** Switch the tracking mode
     * Sets the tracking mode to the opposite of the current mode */
    public void doSwitchTracking() {
        if(isTrackerEnabled()) {
            doCenter();
            doDisableServos();
        } else {
            setTrackerEnabled(true);
        }
    }

    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --FactorDec--">
    public float getFactorDec(){
        return FactorDec;
    }
    
    public void setFactorDec(float FactorDec) {
        putFloat("factorDec",FactorDec);
        float OldValue = this.FactorDec;
        this.FactorDec = FactorDec;
        support.firePropertyChange("factorDec",OldValue,FactorDec);
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
    
    public CalibratedStimulusGUI getGUI() {
        return calibGUI;
    }
}
