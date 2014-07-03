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

import net.sf.jaer.eventprocessing.label.*;
import net.sf.jaer.graphics.FrameAnnotater;
import ch.unizh.ini.jaer.hardware.pantilt.*;

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
    private final PanTilt panTilt;
    
    private final Point2D.Float GlobalDir; 
    
    DvsDirectionSelectiveFilter DirFilter;
    
    public SimpleVS_GlobalOpticFlow(AEChip chip) {
        super(chip);
        
        DirFilter = new DvsDirectionSelectiveFilter(chip);
        DirFilter.setAnnotationEnabled(false);
        DirFilter.setShowRawInputEnabled(false);
        panTilt = PanTilt.getInstance(0);
        GlobalDir = new Point2D.Float(0,0);
        
        setEnclosedFilter(DirFilter);

        setPropertyTooltip("threshDir", "Threshold when a global motion should be taken into account");
        setPropertyTooltip("maxMove", "maximum distance the pantilt moves per event");
        setPropertyTooltip("factorDec", "forgetfullness of old global motions");
        setPropertyTooltip("trackerEnabled", "Move the PanTilt to the target");
        
        setPropertyTooltip("switchTracking","switches the tracking of the pantilt.");
    }
    
    
    @Override
    /*filterPacket is neccessary to extend 'EventFilter2D'*/
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
        
        getEnclosedFilter().filterPacket(in);
        
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
                float[] PanTiltPos = panTilt.getPanTiltValues();
                float[] NewPos = {0.5f,0.5f};
                NewPos[0] = PanTiltPos[0]-Move.x; 
                NewPos[1] = PanTiltPos[1]+Move.y;
                //We dont need to pay attention to weather the new values
                // are larger 1 or smaller 0 as the values are clipped by
                // 'setPanTiltValues' anyway.
                
               panTilt.setTarget(NewPos[0],NewPos[1]); 
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
    }

    @Override
    /*initFilter is neccessary to extend 'EventFilter'*/
    public void initFilter() {
        resetFilter();
    }
    
    @Override
    /*annotate is neccessary to implement 'FrameAnnotater'*/
    public void annotate(GLAutoDrawable drawable) {
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
        panTilt.setTarget(.5f, .5f);
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
}
