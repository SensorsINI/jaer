/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.hmm;

import ch.unizh.ini.jaer.projects.gesture.stereo.BlurringFilterStereoTracker;
import ch.unizh.ini.jaer.projects.gesture.virtualdrummer.BlurringFilter2DTracker;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Observable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.tracking.ClusterPathPoint;

/**
 *
 * @author Jun Haeng Lee
 */
public class GestureBFStereo extends GestureBF2D{
    protected BlurringFilterStereoTracker stereoTracker = null;

    protected LinkedList<Integer> disparityQueue = new LinkedList<Integer>();
    protected int disparityQueueMaxSize = 10;

    public final static int IDLE_STATE_DISPARITY = -1000;
    protected int meanDisparityOfStartGesture = IDLE_STATE_DISPARITY;
    protected int disparityThresholdPushGesture = 10;
    private boolean pushDetected = false; 

    public GestureBFStereo(AEChip chip) {
        super(chip);
        
        // deactivates lower disparity limit
        ((BlurringFilterStereoTracker) super.tracker).setEnableDisparityLimit(false);
    }

    

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

            if(path == null)
                return;

            // default trimming
            ArrayList<ClusterPathPoint> trimmedPath = trajectoryTrimmingPointBase(path, 1, 1);

            // doesn't have to classify short trajectroies
            if(trimmedPath.size() < getNumPointsThreshold())
            {
                storePath(trimmedPath, true);
                return;
            }

            // checks 2D gestures
            String bmg = null;
            if(isLogin()){
                // estimates the best matching gesture
                bmg = estimateGesture(trimmedPath);

                // tries again with prevPath if bmg is null
                if(isUsePrevPath() && bmg == null)
                    bmg = estimateGestureWithPrevPath(trimmedPath, getMaxTimeDiffCorrSegmentsUs());
                    
                System.out.println("Best matching gesture is " + bmg);

                if(bmg != null){
                    if(afterRecognitionProcess(bmg, trimmedPath)){
                        endTimePrevGesture = endTimeGesture;

                        // starts or restarts the auto logout timer
                        if(isLogin()){ // has to check if the gesture recognition system is still active (to consider logout)
                            if(autoLogoutTimer.isRunning())
                                autoLogoutTimer.restart();
                            else
                                autoLogoutTimer.start();
                        }

                        pushDetected = false;
                        }
                } else
                    savePath = true;

            } else { // if the gesture recognition system is inactive, checks the start gesture only
                if(detectStartingGesture(trimmedPath)){
                    System.out.println("Gesture recognition system is enabled.");
                    afterRecognitionProcess("Infinite", trimmedPath);
                    endTimePrevGesture = endTimeGesture;

                    // set maximum disparity of the vergence filter based on mean disparity of the start gesture
                    meanDisparityOfStartGesture = findMeanDisparity(trimmedPath);
                    BlurringFilterStereoTracker tmpTracker = (BlurringFilterStereoTracker) super.tracker;
                    if(tmpTracker.getAutoThresholdSlope() >= 0){
                        tmpTracker.setDisparityLimit(meanDisparityOfStartGesture-2, true);
                    }else{
                        tmpTracker.setDisparityLimit(meanDisparityOfStartGesture+2, false);
                    }
                    tmpTracker.setEnableDisparityLimit(true);
                     pushDetected = false;
                }
            }
            if(savePath)
                storePath(trimmedPath, false);
            else
                resetPrevPath();
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



    

    @Override
    protected void doLogin() {
        super.doLogin();
    }

    @Override
    protected void doLogout() {
        super.doLogout();

        meanDisparityOfStartGesture = IDLE_STATE_DISPARITY;

        // deactivates lower disparity limit
        ((BlurringFilterStereoTracker) super.tracker).setEnableDisparityLimit(false);

        // releases maximum disparity change limit
//        ((BlurringFilterStereoTracker) super.tracker).setMaxDisparityChangePixels(100);
    }

    @Override
    protected void doPush() {
        super.doPush(); 
    }
}
