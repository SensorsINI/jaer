/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.hardware.pantilt.VisualServo;

import java.awt.geom.Point2D;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.graphics.FrameAnnotater;
import ch.unizh.ini.jaer.hardware.pantilt.*;
/**
 *
 * @author Bjoern
 */
//EXTENDS EventFilter2D as we deal with asynchroneous pixel data
//IMPLEMENTS FrameAnnotater so that we can show clusters
@Description("Moves the DVS towards a target based on the cluster tracker")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class SimpleVS_Cluster extends EventFilter2D implements FrameAnnotater {
    //TODO
    // - TrackerEnabled sollte nicht im code angetastet werden, weil sonnst das GUI nicht updated
    
    
    private boolean TrackerEnabled = getBoolean("TrackerEnabled", false);
    private CalibrationTransformation retinaPTCalib;
    private Vector2D clusterLoc = new Vector2D();
    
    RectangularClusterTracker tracker;
    PanTiltAimer PTAimer;
    
    public SimpleVS_Cluster(AEChip chip) {
        super(chip);
        
        tracker       = new RectangularClusterTracker(chip);
        PTAimer       = new PanTiltAimer(chip);
        retinaPTCalib = new CalibrationTransformation(chip,"retinaPTCalib");
        
        tracker.setAnnotationEnabled(true);//we WANT to see trackers annotations!
        
        FilterChain filterChain = new FilterChain(chip);
        setEnclosedFilterChain(filterChain);
            filterChain.add(PTAimer);
            filterChain.add(tracker);
        setEnclosedFilterChain(filterChain);  
        
        setPropertyTooltip("DisableServos", "disables servos, e.g. to turn off annoying servo hum");
        setPropertyTooltip("Center", "centers pan and tilt");
        setPropertyTooltip("TrackerEnabled", "Move the PanTilt to the target");
    }
    
    /*filterPacket is neccessary to extend 'EventFilter2D'*/
    @Override public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
   
        getEnclosedFilterChain().filterPacket(in);
        
        if(tracker.getNumClusters()>0) {
            RectangularClusterTracker.Cluster c = tracker.getClusters().get(0); //currently gets first cluster... should decide on other options: fastest, biggest etc.
            if(c.isVisible()) {
                clusterLoc.setLocation(c.getLocation());
                if(isTrackerEnabled()) {
                    
                    float[] ptChange  = retinaPTCalib.makeTransform(new float[] {clusterLoc.x-64,clusterLoc.y-64,0f});
                    float[] curTarget = PTAimer.getPanTiltTarget();
                    System.out.printf("current: (%.2f,%.2f) ; change: (%.2f,%.2f) ; clusterLoc: (%.2f,%.2f)\n",curTarget[0],curTarget[1],ptChange[0],ptChange[1],clusterLoc.x,clusterLoc.y);
                    
                    PTAimer.setPanTiltTarget(curTarget[0]+ptChange[0],curTarget[1]+ptChange[1]);
                    
                    
//                    float[] PanTiltPos = PTAimer.getPanTiltValues();
//                    float[] NewPos = {0.5f,0.5f};
//                    NewPos[0] = PanTiltPos[0]+0.1f*((p.x-64)/128);//PARAMETER
//                    NewPos[1] = PanTiltPos[1]+0.1f*((p.y-64)/128);
//                    System.out.println(Float.toString(NewPos[0])+" - "+Float.toString(NewPos[1]));
//
//                    PTAimer.setPanTiltTarget(NewPos[0],NewPos[1]);
                }
            }
        }
        return in;
    }
    
    @Override public void resetFilter() {
        tracker.resetFilter();
        PTAimer.resetFilter();
    }

    @Override public void initFilter() {
        resetFilter();
    }
    
    /**
     * Centers the Pan-Tilt.  
     * Built automatically into filter parameter panel as an action.
     */
    public void doCenter() {
        setTrackerEnabled(false); //So that PanTilt can move to center before tracking again
        PTAimer.doCenter();
    }
    
    public void doAim() {
        PanTiltAimerGUI aimerGui = new PanTiltAimerGUI(PTAimer.getPanTiltHardware());
        aimerGui.setVisible(true);
    }
    
    /**
     * Disables all Servos and stops jitter.  
     * Built automatically into filter parameter panel as an action.
     */
    public void doDisableServos() {
        PTAimer.doDisableServos();
    }
    
    @Override public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled()) return;

        tracker.annotate(drawable);
     }
    
    public boolean isTrackerEnabled() {
        return TrackerEnabled;
    }

    /**
     * @param TrackerEnabled the TrackerEnabled to set
     */
    public void setTrackerEnabled(boolean TrackerEnabled) {
        putBoolean("TrackerEnabled", TrackerEnabled);
        boolean oldValue = this.TrackerEnabled;
        this.TrackerEnabled = TrackerEnabled;
        support.firePropertyChange("TrackerEnabled",oldValue,TrackerEnabled);
    }
}
