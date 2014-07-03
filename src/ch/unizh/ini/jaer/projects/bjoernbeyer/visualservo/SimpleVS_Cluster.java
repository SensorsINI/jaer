/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.bjoernbeyer.visualservo;

import net.sf.jaer.util.Vector2D;
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

    private boolean TrackerEnabled = getBoolean("TrackerEnabled", false);
    private final CalibrationPanTiltScreen retinaPTCalib;
    private final Vector2D clusterLoc = new Vector2D();
    private float targetFactor = .1f;
    
    RectangularClusterTracker tracker;
    PanTilt panTilt;
    
    public SimpleVS_Cluster(AEChip chip) {
        super(chip);
        
        tracker       = new RectangularClusterTracker(chip);
        panTilt       = PanTilt.getInstance(0);
        retinaPTCalib = new CalibrationPanTiltScreen("retinaPTCalib");
        
        tracker.setAnnotationEnabled(true);//we WANT to see trackers annotations!
        
        setEnclosedFilter(tracker);
        
        setPropertyTooltip("DisableServos", "disables servos, e.g. to turn off annoying servo hum");
        setPropertyTooltip("Center", "centers pan and tilt");
        setPropertyTooltip("TrackerEnabled", "Move the PanTilt to the target");
    }
    
    /*filterPacket is neccessary to extend 'EventFilter2D'*/
    @Override public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
   
        getEnclosedFilter().filterPacket(in);
        
        if(tracker.getNumClusters()>0) {
            RectangularClusterTracker.Cluster c = tracker.getClusters().get(0); //currently gets first cluster... should decide on other options: fastest, biggest etc.
            if(c.isVisible()) {
                clusterLoc.setLocation(c.getLocation());
                float[] ptChange  = retinaPTCalib.makeTransform(new float[] {clusterLoc.x-64,clusterLoc.y-64,0f});
                float[] curTarget = panTilt.getTarget();
                float[] newTarget = {(curTarget[0]+ptChange[0]),(curTarget[1]+ptChange[1])};
                System.out.println(newTarget[0]+" -- " +newTarget[1]);
                if(newTarget[0] > 1) {
                    newTarget[0]  = 1;
                }else if(newTarget[0] < 0) {
                    newTarget[0] =0;
                }
                
                if(newTarget[1] > 1) {
                    newTarget[1]  = 1;
                }else if(newTarget[1] < 0){
                    newTarget[1] = 0;
                }
                
                System.out.printf("current: (%.2f,%.2f) ; ptchange: (%.2f,%.2f) ; clusterLoc: (%.2f,%.2f)\n",curTarget[0],curTarget[1],ptChange[0],ptChange[1],clusterLoc.x,clusterLoc.y);
                if(isTrackerEnabled()) {
                    panTilt.setTarget(newTarget[0],newTarget[1]);
                    
                    
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
    }

    @Override public void initFilter() {
        resetFilter();
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
    
    public void doAim() {
        PanTiltAimerGUI aimerGui = new PanTiltAimerGUI(panTilt);
        aimerGui.setVisible(true);
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

    public float getTargetFactor() {
        return targetFactor;
    }

    public void setTargetFactor(float targetFactor) {
        this.targetFactor = targetFactor;
    }
}
