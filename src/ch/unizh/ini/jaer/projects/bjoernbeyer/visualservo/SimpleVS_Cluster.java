
package ch.unizh.ini.jaer.projects.bjoernbeyer.visualservo;

import ch.unizh.ini.jaer.projects.bjoernbeyer.pantiltscreencalibration.CalibrationPanTiltScreen;
import net.sf.jaer.util.Vector2D;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.graphics.FrameAnnotater;
import ch.unizh.ini.jaer.hardware.pantilt.*;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker.Cluster;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
/**
 *
 * @author Bjoern
 */
//EXTENDS EventFilter2D as we deal with asynchroneous pixel data
//IMPLEMENTS FrameAnnotater so that we can show clusters
@Description("Moves the DVS towards a target based on the cluster tracker")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class SimpleVS_Cluster extends EventFilter2D implements FrameAnnotater, SimpleVStrackerInterface {

    private boolean TrackerEnabled = getBoolean("trackerEnabled", false);
    private final CalibrationPanTiltScreen retinaPTCalib;
    private final Vector2D clusterLoc = new Vector2D();
    private float currentClusterAdvantage = 1.5f;
    private float targetFactor = .1f;
    private Cluster followCluster = null;
    
    private float[] retChange = new float[3];
    private float[] newTarget = new float[2];
    private float[] ptChange  = new float[3];
    private float[] curTarget = new float[2];
    
    RectangularClusterTracker tracker;
    PanTilt panTilt;
    
    public SimpleVS_Cluster(AEChip chip) {
        super(chip);
        
        retinaPTCalib = new CalibrationPanTiltScreen("retinaPTCalib");
        tracker       = new RectangularClusterTracker(chip);
            tracker.setAnnotationEnabled(true);//we WANT to see trackers annotations!
            tracker.setClusterMassDecayTauUs(200000);
            tracker.setThresholdMassForVisibleCluster(50);
            tracker.setPathLength(300);
            tracker.setUseVelocity(false);
            tracker.setMaxNumClusters(1);
        panTilt       = PanTilt.getInstance(0);
            panTilt.setLimitOfPan(retinaPTCalib.getLimitOfPan());
            panTilt.setLimitOfTilt(retinaPTCalib.getLimitOfTilt());
            panTilt.setLinearSpeedEnabled(false);

        setEnclosedFilter(tracker);
        
        setPropertyTooltip("DisableServos", "disables servos, e.g. to turn off annoying servo hum");
        setPropertyTooltip("Center", "centers pan and tilt");
        setPropertyTooltip("TrackerEnabled", "Move the PanTilt to the target");
    }
    
    /*filterPacket is neccessary to extend 'EventFilter2D'*/
    @Override public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
   
        getEnclosedFilter().filterPacket(in);
        
        if(tracker.getNumVisibleClusters() > 0) {
            LinkedList<Cluster> visibleList = tracker.getVisibleClusters();
 
//TODO: maybe we should wait for a given amount of time after we lost a cluster.
// this could reduce the manic behaviour of this tracker somewhat. It would also
// reduce the self motion and hence made tracking more realistic! Maybe like 200ms            
            followCluster = updateFollowCluster(visibleList, followCluster, getCurrentClusterAdvantage());

            //As the retina-PanTilt calibration is a differential calibration we
            // need to give it the change vector in retinal coordinates and it will
            // give the corresponding change vesctor in panTilt coordinates.
            // We want to keep the detected Cluster in the center of the screen,
            // hence the change vector is the current detected cluster position minus the desired center position.
            retChange[0] = (chip.getSizeX()/2)-followCluster.getLocation().x;
            retChange[1] = (chip.getSizeY()/2)-followCluster.getLocation().y;
            retChange[2] = 0f;
            
            ptChange  = retinaPTCalib.makeTransform(retChange);
            curTarget = panTilt.getTarget();
            newTarget[0] = (curTarget[0]+getTargetFactor()*ptChange[0]);
            newTarget[1] = (curTarget[1]+getTargetFactor()*ptChange[1]);
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

            }
        }
        return in;
    }
    
    private Cluster updateFollowCluster(LinkedList<Cluster> visibleList, Cluster currentCluster, float currentClusterAdvantage) {
        float maxMass = 0f;
        Cluster maxCluster = null;
        
        //Only if there is a current non-null cluster AND if it is still visible
        // we can give this cluster more weight in the comparison. This is to
        // discourage fast switching of clusters just because the mass of one cluster
        // is slightly higher than that of another cluster.
        if(currentCluster != null &&  visibleList.contains(currentCluster)) {
            return currentCluster;
//            maxMass = currentClusterAdvantage*currentCluster.getMass();
//            maxCluster = currentCluster;
        }
        
        for(Cluster c : visibleList) {
            if(c.getMass()>maxMass) {
                maxMass = c.getMass();
                maxCluster = c;
            }
        }
        return maxCluster;
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
    
    public void doCheckCalibration() {
        if(!retinaPTCalib.isCalibrated()){
            System.out.println("No calibration found!");
        } else {
            retinaPTCalib.displayCalibration(10);
        }
    }
    
    public void doCenterPT() {
        setTrackerEnabled(false);
        panTilt.setTarget(.5f, .5f);
    }
    
    public void doDisableServos() {
        setTrackerEnabled(false);
        try {
            panTilt.disableAllServos();
        } catch (HardwareInterfaceException ex) {
            log.warning(ex.getMessage());
        }
    }
    
    public void doAim() {
        PanTiltAimerGUI aimerGui = new PanTiltAimerGUI(panTilt);
        aimerGui.setVisible(true);
    }
    
    @Override public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled()) return;

        tracker.annotate(drawable);
     }
    
    // <editor-fold defaultstate="collapsed" desc="getter-setter for --trackerEnabled--">
    @Override public boolean isTrackerEnabled() {
        return TrackerEnabled;
    }

    /**
     * @param TrackerEnabled the TrackerEnabled to set
     */
    @Override public void setTrackerEnabled(boolean TrackerEnabled) {
        putBoolean("trackerEnabled", TrackerEnabled);
        boolean oldValue = this.TrackerEnabled;
        this.TrackerEnabled = TrackerEnabled;
        support.firePropertyChange("trackerEnabled",oldValue,TrackerEnabled);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter-setter for --currentClusterAdvantage--">
    public float getCurrentClusterAdvantage() {
        return currentClusterAdvantage;
    }

    public void setCurrentClusterAdvantage(float currentClusterAdvantage) {
        this.currentClusterAdvantage = currentClusterAdvantage;
    }

    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter-setter for --targetFactor--">
    public float getTargetFactor() {
        return targetFactor;
    }

    public void setTargetFactor(float targetFactor) {
        this.targetFactor = targetFactor;
    }
    // </editor-fold>
    
}
