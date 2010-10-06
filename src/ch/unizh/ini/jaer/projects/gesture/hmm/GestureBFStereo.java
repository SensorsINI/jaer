/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.hmm;

import ch.unizh.ini.jaer.projects.gesture.stereo.BlurringFilterStereoTracker;
import ch.unizh.ini.jaer.projects.gesture.virtualdrummer.BlurringFilter2DTracker;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Observable;
import javax.swing.Timer;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.tracking.ClusterPathPoint;

/**
 *
 * @author Jun Haeng Lee
 */
public class GestureBFStereo extends GestureBF2D{
    /**
     * time to wait for auto logout
     */
    private int autoLogoutTimeMs = getPrefs().getInt("GestureBFStereo.autoLogoutTimeMs", 10000);
    private boolean enableAutoLogout = getPrefs().getBoolean("GestureBFStereo.enableAutoLogout", true);

    protected BlurringFilterStereoTracker stereoTracker = null;

    protected LinkedList<Integer> disparityQueue = new LinkedList<Integer>();
    protected int disparityQueueMaxSize = 10;

    public final static int IDLE_STATE_DISPARITY = -1000;
    protected int meanDisparityOfStartGesture = IDLE_STATE_DISPARITY;
    protected int disparityThresholdPushGesture = 10;
    private boolean pushDetected = false;

    /**
     * timer for auto logout
     */
    protected Timer autoLogoutTimer;

    public GestureBFStereo(AEChip chip) {
        super(chip);
        autoLogoutTimer = new Timer(autoLogoutTimeMs, autoLogoutAction);
        
        // deactivates lower disparity limit
        ((BlurringFilterStereoTracker) super.tracker).setEnableLowerDisparityLimit(false);
        
        // releases maximum disparity change limit
//        ((BlurringFilterStereoTracker) super.tracker).setMaxDisparityChangePixels(100);

        setPropertyTooltip("Auto logout","autoLogoutTimeMs","time in ms to wait before auto logout");
        setPropertyTooltip("Auto logout","enableAutoLogout","if true, auto logout is done if it's been more than autoLogoutTimeMs since the last gesture");
    }

    /**
     * action listener for timer events
     */
    ActionListener autoLogoutAction = new ActionListener() {
        public void actionPerformed(ActionEvent evt) {
            doLogout();            
        }
    };

    @Override
    protected void filterChainSetting() {
        stereoTracker = new BlurringFilterStereoTracker(chip);
        super.tracker = stereoTracker;
        ( (EventFilter2D) stereoTracker ).addObserver(this);
        setEnclosedFilterChain(new FilterChain(chip));
        getEnclosedFilterChain().add((EventFilter2D)stereoTracker);
        ( (EventFilter2D) stereoTracker ).setEnclosed(true,this);
        ( (EventFilter2D) stereoTracker ).setFilterEnabled(isFilterEnabled());
    }

    @Override
    public void update(Observable o, Object arg) {
        if ( o instanceof BlurringFilterStereoTracker ){
            UpdateMessage msg = (UpdateMessage)arg;
            List<BlurringFilter2DTracker.Cluster> cl = tracker.getClusters();
            ArrayList<ClusterPathPoint> path = selectClusterTrajectory(cl);

            // checks 2D gestures
            String bmg = null;
            if(path != null){
                if(isLogin()){
                    // estimates the best matching gesture
                    bmg = estimateGesture(path);
                    System.out.println("Best matching gesture is " + bmg);

                    if(afterRecognitionProcess(bmg)){
                        endTimePrevGesture = endTimeGesture;

                        // starts or restarts the auto logout timer
                        if(isLogin()){ // has to check if the gesture recognition system is still active (to consider logout)
                            if(autoLogoutTimer.isRunning())
                                autoLogoutTimer.restart();
                            else
                                autoLogoutTimer.start();
                        }

                        pushDetected = false;
                    }else
                        storePath(path);

                } else { // if the gesture recognition system is inactive, checks the start gesture only
                    if(detectStartingGesture(path)){
                        System.out.println("Gesture recognition system is enabled.");
                        afterRecognitionProcess("Infinite");
                        endTimePrevGesture = endTimeGesture;

                        meanDisparityOfStartGesture = findMeanDisparity(path);

                        // set maximum disparity of the vergence filter based on mean disparity of the start gesture
                        ((BlurringFilterStereoTracker) super.tracker).setLowerDisparityLimit(meanDisparityOfStartGesture-3);
                        ((BlurringFilterStereoTracker) super.tracker).setEnableLowerDisparityLimit(true);
    //                    ((BlurringFilterStereoTracker) super.tracker).setMaxDisparityChangePixels(20);

                        // starts the auto logout timer
                        autoLogoutTimer.start();

                        pushDetected = false;
                    }
                }
            }

        } 
    }

    /**
     * calculates mean disparity of a trajectory
     *
     * @param path
     * @return
     */
    private int findMeanDisparity(ArrayList<ClusterPathPoint> path){
        int meanDisparity = 0;

        // calculates mean disparity using medial points only
        ArrayList<ClusterPathPoint> trimmedPath = trajectoryTrimming(path, 25, 25, FeatureExtraction.calTrajectoryLength(path));
        for(int i=0; i<trimmedPath.size(); i++){
            meanDisparity += trimmedPath.get(i).stereoDisparity;
        }

        return meanDisparity/trimmedPath.size();
    }

    /**
     * returns enableAutoLogout
     *
     * @return
     */
    public boolean isEnableAutoLogout() {
        return enableAutoLogout;
    }

    /**
     * sets enableAutoLogout
     *
     * @param enableAutoLogout
     */
    public void setEnableAutoLogout(boolean enableAutoLogout) {
        boolean old = this.enableAutoLogout;
        this.enableAutoLogout = enableAutoLogout;
        getPrefs().putBoolean("GestureBFStereo.enableAutoLogout",enableAutoLogout);
        support.firePropertyChange("enableAutoLogout",old,this.enableAutoLogout);
    }


    /**
     * returns autoLogoutTimeMs
     * 
     * @return
     */
    public int getAutoLogoutTimeMs() {
        return autoLogoutTimeMs;
    }

    /**
     * sets autoLogoutTimeMs
     *
     * @param autoLogoutTimeMs
     */
    public void setAutoLogoutTimeMs(int autoLogoutTimeMs) {
        int old = this.autoLogoutTimeMs;
        this.autoLogoutTimeMs = autoLogoutTimeMs;
        getPrefs().putInt("GestureBFStereo.autoLogoutTimeMs",autoLogoutTimeMs);
        support.firePropertyChange("autoLogoutTimeMs",old,this.autoLogoutTimeMs);

        if(autoLogoutTimer.isRunning()){
            autoLogoutTimer.stop();
            autoLogoutTimer = new Timer(autoLogoutTimeMs, autoLogoutAction);
            autoLogoutTimer.start();
        } else {
            autoLogoutTimer = new Timer(autoLogoutTimeMs, autoLogoutAction);
        }
    }

    @Override
    protected void doLogin() {
        super.doLogin();
    }

    @Override
    protected void doLogout() {
        super.doLogout();

        meanDisparityOfStartGesture = IDLE_STATE_DISPARITY;

        // deactivates lower disparity limit
        ((BlurringFilterStereoTracker) super.tracker).setEnableLowerDisparityLimit(false);

        // releases maximum disparity change limit
//        ((BlurringFilterStereoTracker) super.tracker).setMaxDisparityChangePixels(100);

        // stop auto-logout timer
        autoLogoutTimer.stop();
    }

    @Override
    protected void doPush() {
        super.doPush(); 
    }
}
