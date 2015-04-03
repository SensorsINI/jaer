
package ch.unizh.ini.jaer.projects.bjoernbeyer.visualservo;

import ch.unizh.ini.jaer.projects.bjoernbeyer.pantiltscreencalibration.CalibrationPanTiltScreen;
import net.sf.jaer.util.Vector2D;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.graphics.FrameAnnotater;
import ch.unizh.ini.jaer.hardware.pantilt.*;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import net.sf.jaer.util.DrawGL;
import java.awt.Color;
import java.util.LinkedList;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker.Cluster;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
/**
 *
 * @author Bjoern
 */
@Description("Moves the DVS towards a target based on the cluster tracker")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class SimpleVS_Cluster extends EventFilter2D implements FrameAnnotater, SimpleVStrackerInterface {

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    
    private boolean TrackerEnabled = getBoolean("trackerEnabled", false);
    private final CalibrationPanTiltScreen retinaPTCalib;
    private Cluster followCluster       = null;
    private int   waitingTimeMS         = getInt("waitingTimeMS",200);
    private int   numberAllowedClusters = getInt("numberAllowedClusters",1);
    private float movementThresholdPt   = getFloat("movementThreshold",.03f);
    private float saccadeThresholdPx    = getFloat("saccadeThresholdPx",10f);
    private float saccadeSpeedPt        = getFloat("saccadeSpeed",.1f);
    private float pursuitSpeedPt        = getFloat("pursuitSpeed",.01f);
    private long  clusterLostTime       = 0;
    
    
    private final float[] deltaRet = new float[3];
    private final float[] newTarget = new float[2];
    private final Vector2D deltaPtReq  = new Vector2D(),
                           deltaPtAct  = new Vector2D(),
                           curPtTarget = new Vector2D(),   
                           curPtPos    = new Vector2D(),
                           clusterPos  = new Vector2D(),
                           ptChange    = new Vector2D();
    private LinkedList<Cluster> clusterList, visibleClusterList;
    
    RectangularClusterTracker tracker;
    PanTilt panTilt;
    
    public SimpleVS_Cluster(AEChip chip) {
        super(chip);
        
        retinaPTCalib = new CalibrationPanTiltScreen("retinaPTCalib");
        tracker       = new RectangularClusterTracker(chip);
            tracker.setAnnotationEnabled(false);
            tracker.setColorClustersDifferentlyEnabled(true);
            tracker.setClusterMassDecayTauUs(200000);
            tracker.setThresholdMassForVisibleCluster(50);
            tracker.setPathLength(300);
            tracker.setUseVelocity(false);
            tracker.setMaxNumClusters(getNumberAllowedClusters());
        panTilt       = PanTilt.getInstance(0);
            panTilt.setLimitOfPan(retinaPTCalib.getLimitOfPan());
            panTilt.setLimitOfTilt(retinaPTCalib.getLimitOfTilt());
            panTilt.setLinearSpeedEnabled(false);
            panTilt.setMaxMovePerUpdate(getPursuitSpeed());
            panTilt.setMoveUpdateFreqHz(100);

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
            visibleClusterList = tracker.getVisibleClusters();
            clusterList = (LinkedList<Cluster>)tracker.getClusters().clone();      
            
//TODO: For some reason, when more than one clusters are allowed the Cluster Location
// Does not update as long as the cluster is not changed. This means if there are
// two clusters allowed but only one cluster is visible, the .getLocation of this
// cluster will stay the same, even though it actually is not anymore. This is not
// the case when only one cluster is allowed.            
            followCluster = updateFollowCluster(visibleClusterList, followCluster);
            if(followCluster == null) return in;
            
            curPtTarget.setLocation(panTilt.getTarget());
               curPtPos.setLocation(panTilt.getPanTiltValues());
             clusterPos.setLocation(followCluster.getLocation());
            
            //If we are in saccadic region set the maximum speed very high so that
            // the tracker can catch up with the target
            if((chip.getSizeX() - clusterPos.x) < saccadeThresholdPx || clusterPos.x < saccadeThresholdPx || 
               (chip.getSizeY() - clusterPos.y) < saccadeThresholdPx || clusterPos.y < saccadeThresholdPx){
                panTilt.setMaxMovePerUpdate(saccadeSpeedPt);
            } else {
                panTilt.setMaxMovePerUpdate(pursuitSpeedPt);
            }
//             System.out.printf("ClusterLoc: (%.3f,%.3f), curPtTarget: (%.3f,%.3f), curPtPos: (%.3f,%.3f)\n", clusterPos.x,clusterPos.y,curPtTarget.x,curPtTarget.y,curPtPos.x,curPtPos.y);
           
            //As the retina-PanTilt calibration is a differential calibration we
            // need to give it the change vector in retinal coordinates and it will
            // give the corresponding change vector in panTilt coordinates.
            // We want to keep the detected Cluster in the center of the screen,
            // hence the change vector is the current detected cluster position minus the desired center position.
            deltaRet[0] = (chip.getSizeX()/2)-clusterPos.x;
            deltaRet[1] = (chip.getSizeY()/2)-clusterPos.y;
            deltaRet[2] = 0f;
            
            deltaPtReq.setLocation(retinaPTCalib.makeTransform(deltaRet));
            deltaPtAct.setDifference(curPtTarget, curPtPos); 
              ptChange.setDifference(deltaPtReq, deltaPtAct);
//            System.out.printf("measured delta: (%.2f,%.2f), actual delta: (%.2f,%.2f) --> ptChange: (%.4f,%.4f)\n",deltaPtReq.x,deltaPtReq.y,deltaPtAct.x,deltaPtAct.y,ptChange.x,ptChange.y);
            
            newTarget[0] = curPtTarget.x+ptChange.x;
            newTarget[1] = curPtTarget.y+ptChange.y;
            this.pcs.firePropertyChange("trackedTarget", null, newTarget);
            if(Math.abs(ptChange.x) > movementThresholdPt || Math.abs(ptChange.y) > movementThresholdPt ) {
                // We only actually move the PanTilt if the ptChange is above a
                // Threshold. This avoids oscillations around the target.
                // However we always report the actually measured target, including oscialltions.
                if(isTrackerEnabled()) {
                    panTilt.setTarget(newTarget[0],newTarget[1]);
                }
            }
        }
        return in;
    }
    
    private Cluster updateFollowCluster(LinkedList<Cluster> visibleList, Cluster currentCluster) {
        float maxMass = 0f;
        Cluster maxCluster = null;
        
        if(currentCluster != null){
            if(visibleList.contains(currentCluster)){
                return visibleList.get(visibleList.indexOf(currentCluster));
            } else {
                clusterLostTime = System.nanoTime();
            }
        }

        if(System.nanoTime() - clusterLostTime > getWaitingTimeMS()*1e6){
            for(Cluster c : visibleList) {
                if(c.getMass()>maxMass) {
                    maxMass = c.getMass();
                    maxCluster = c;
                }
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
    
    @Override public void doCenterPT() {
        setTrackerEnabled(false);
        panTilt.setTarget(.5f, .5f);
    }
    
    @Override public void doDisableServos() {
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
        if(clusterList == null) return;
        
        GL2 gl = drawable.getGL().getGL2(); // when we getString this we are already set up with updateShape 1=1 pixel, at LL corner
        if (gl == null) {
            log.warning("null GL in SimpleVS_Cluster.annotate");
            return;
        }
        
        //Draw a box for the saccadic region so that user knows where the speed will be increased
        gl.glPushMatrix();
            int sizeX = chip.getSizeX();
            int sizeY = chip.getSizeY();
            gl.glColor3f(1,0,0);
            DrawGL.drawBox(gl, sizeX/2, sizeY/2, sizeX-getSaccadeThresholdPx(), sizeY-getSaccadeThresholdPx(), 0);
        gl.glPopMatrix();
        
        try{
            gl.glPushMatrix();
            for(Cluster c : clusterList) {
                if(!c.isVisible()){
                    c.setColor(new Color(.3f, .3f, .3f));
                } else {
                    if(c.equals(followCluster)) {
                        c.setColor(Color.red);
                    } else {
                        c.setColor(Color.blue);
                    }
                }
            }
        } catch (java.util.ConcurrentModificationException e) {
            // this is in case cluster list is modified by real time filter during rendering of clusters
            log.warning("concurrent modification of cluster list while drawing clusters");
        } finally {
            gl.glPopMatrix();
        }
        tracker.annotate(drawable);
    }
    
    @Override public void addPropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.addPropertyChangeListener(listener);
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
    
    public float getMovementThreshold() {
        return movementThresholdPt;
    }

    public void setMovementThreshold(float movementThreshold) {
        this.movementThresholdPt = movementThreshold;
        putFloat("movementThreshold",movementThreshold);
    }

    public final int getNumberAllowedClusters() {
        return numberAllowedClusters;
    }

    public void setNumberAllowedClusters(int numberAllowedClusters) {
        this.numberAllowedClusters = numberAllowedClusters;
        tracker.setMaxNumClusters(getNumberAllowedClusters());
        putInt("numberAllowedClusters",numberAllowedClusters);
    }

    public float getSaccadeThresholdPx() {
        return saccadeThresholdPx;
    }

    public void setSaccadeThresholdPx(float saccadeThreshold) {
        this.saccadeThresholdPx = saccadeThreshold;
        putFloat("saccadeThresholdPx",saccadeThreshold);
    }

    public float getSaccadeSpeed() {
        return saccadeSpeedPt;
    }

    public void setSaccadeSpeed(float saccadeSpeed) {
        this.saccadeSpeedPt = saccadeSpeed;
        putFloat("saccadeSpeed",saccadeSpeed);
    }

    public final float getPursuitSpeed() {
        return pursuitSpeedPt;
    }

    public void setPursuitSpeed(float pursuitSpeed) {
        this.pursuitSpeedPt = pursuitSpeed;
        putFloat("pursuitSpeed",pursuitSpeed);
    }

    public int getWaitingTimeMS() {
        return waitingTimeMS;
    }

    public void setWaitingTimeMS(int waitingTimeMS) {
        int setValue = waitingTimeMS;
        if(setValue<0)setValue=0;
        this.waitingTimeMS = setValue;
        putInt("waitingTimeMS",waitingTimeMS);
    }
    
}
