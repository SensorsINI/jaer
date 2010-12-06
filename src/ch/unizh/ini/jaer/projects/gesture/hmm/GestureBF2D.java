/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.hmm;

import ch.unizh.ini.jaer.projects.gesture.virtualdrummer.BlurringFilter2DTracker;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import javax.media.opengl.GLAutoDrawable;
import javax.swing.*;
import javax.swing.Timer;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.*;
import net.sf.jaer.eventprocessing.tracking.ClusterPathPoint;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.filter.LowpassFilter2d;

/**
 * Gesture recognition system using a single DVS sensor.
 * BluringFilter2DTracker is used to obtain the trajectory of moving object (eg, hand)
 * HMM is used for classification. But, HMM is not used for spoting gestures (i.e., finding the start and end timing of gestures)
 * Gesture spotting is done by the tracker by assuming that there is slow movement between gestures.
 *
 * @author Jun Haeng Lee
 */
public class GestureBF2D extends EventFilter2D implements FrameAnnotater,Observer{
    /**
     * a cluster with points more than this amount will be checked for gesture recognition.
     */
    private int numPointsThreshold = getPrefs().getInt("GestureBF2D.numPointsThreshold", 100);

    /**
     * retries HMM after this percents of head points is removed from the trajectory when the first tiral is failed.
     */
    private int headTrimmingPercents = getPrefs().getInt("GestureBF2D.headTrimmingPercents", 30);

    /**
     * retries HMM after this percents of tail points is removed from the trajectory when the first tiral is failed.
     */
    private int tailTrimmingPercents = getPrefs().getInt("GestureBF2D.tailTrimmingPercents", 10);

    /**
     * speed threshold of the cluster to be a gesture candidate (in kPPT).
     */
    private float maxSpeedThreshold_kPPT = getPrefs().getFloat("GestureBF2D.maxSpeedThreshold_kPPT", 0.1f);

    /**
     * enables lowpass filter to smooth the gesture trajectory
     */
    private boolean enableLPF = getPrefs().getBoolean("GestureBF2D.enableLPF", true);

    /**
     * lowpass filter time constant for gesture trajectory in ms
     */
    private float tauPathMs = getPrefs().getFloat("GestureBF2D.tauPathMs",5.0f);

    /**
     * refractory time in ms between gestures.
     */
    private int refractoryTimeMs = getPrefs().getInt("GestureBF2D.refractoryTimeMs", 700);


    /**
     * lowpass filter time constant for gesture trajectory in ms
     */
    private float GTCriterion = getPrefs().getFloat("GestureBF2D.GTCriterion",3.0f);
    /**
     * enables using prevPath to find gesture pattern
     */
    private boolean usePrevPath = getPrefs().getBoolean("GestureBF2D.usePrevPath", true);

    /**
     * time to wait for auto logout
     */
    private int autoLogoutTimeMs = getPrefs().getInt("GestureBFStereo.autoLogoutTimeMs", 10000);
    private boolean enableAutoLogout = getPrefs().getBoolean("GestureBFStereo.enableAutoLogout", true);


    
    /**
     * true if the gesture recognition system is activated.
     */
    private boolean login = false;

    /**
     * path of gesture picture files
     */
    public static String pathGesturePictures = "C:/Users/jun/Documents/gesture pictures/";

    /**
     * images for gestures
     */
    private Image imgHi, imgBye, imgLeft, imgRight, imgUp, imgDown, imgCW, imgCCW, imgCheck, imgPush;

    /**
     * timmings in the current and previous gestures
     */
    protected int startTimeGesture, endTimeGesture, endTimePrevGesture = 0;

    /**
     * 'Check' gesture is recognized by a check shape or a sequence of 'SlashDown' and 'SlashUp'
     * checkActivated is true if 'SlashDown' is detected. It's false otherwise.
     */
    private boolean checkActivated = false;

    /**
     * time duration limit between 'SlashDown' and 'SlashUp' to make a valid 'Check' gesture
     */
    private static int checkActivationTimeUs = 400000;

    /**
     * previous path
     */
    private ArrayList<ClusterPathPoint> prevPath;



    /**
     * moving object tracker
     */
    protected BlurringFilter2DTracker tracker;

    /**
     * feature extractor
     */
    FeatureExtraction fve = new FeatureExtraction(16, 16);

    /**
     * Hand drawing panel with gesture HMM module
     */
    HmmDrawingPanel hmmDP;

    /**
     * low pass filter to smoothe the trajectory of gestures
     */
    LowpassFilter2d lpf;

    /**
     * timer for auto logout
     */
    protected Timer autoLogoutTimer;




    /**
     * constructor
     * 
     * @param chip
     */
    @SuppressWarnings({"LeakingThisInConstructor", "OverridableMethodCallInConstructor"})
    public GestureBF2D(AEChip chip) {
        super(chip);
        this.chip = chip;
        chip.addObserver(this);

        autoLogoutTimer = new Timer(autoLogoutTimeMs, autoLogoutAction);

        String trimming = "Trimming", selection  = "Selection", lpfilter = "Low pass filter", gesture = "Gesture", autoLogout = "Auto logout";
        setPropertyTooltip(selection,"numPointsThreshold","a cluster with points more than this amount will be checked for gesture recognition.");
        setPropertyTooltip(selection,"maxSpeedThreshold_kPPT","speed threshold of the cluster to be a gesture candidate (in kPPT).");
        setPropertyTooltip(trimming,"headTrimmingPercents","retries HMM after this percents of head points is removed from the trajectory when the first tiral is failed.");
        setPropertyTooltip(trimming,"tailTrimmingPercents","retries HMM after this percents of tail points is removed from the trajectory when the first tiral is failed.");
        setPropertyTooltip(lpfilter,"enableLPF","enables lowpass filter to smooth the gesture trajectory");
        setPropertyTooltip(lpfilter,"tauPathMs","lowpass filter time constant for gesture trajectory in ms");
        setPropertyTooltip(gesture,"refractoryTimeMs","refractory time in ms between gestures");
        setPropertyTooltip(gesture,"GTCriterion","criterion of Gaussian threshold");
        setPropertyTooltip(gesture,"usePrevPath","enables using prevPath to find gesture pattern");
        setPropertyTooltip(autoLogout,"autoLogoutTimeMs","time in ms to wait before auto logout");
        setPropertyTooltip(autoLogout,"enableAutoLogout","if true, auto logout is done if it's been more than autoLogoutTimeMs since the last gesture");

        // low pass filter
        this.lpf = new LowpassFilter2d();

        // hand drawing panel with gesture HMM
        String [] bNames = {"Add", "Remove", "Reset", "Show", "Learn", "Guess"};
        hmmDP = new HmmDrawingPanel("HMM based gesture recognition test using hand drawing panel", bNames);
        hmmDP.setVisible(false);

        // load gesture images into the memory
        loadGestureImages();

        // encloses tracker
        filterChainSetting ();
    }

    /**
     * action listener for timer events
     */
    ActionListener autoLogoutAction = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent evt) {
            doLogout();
        }
    };

    /**
     * sets the BlurringFilter2DTracker as a enclosed filter to find cluster
     */
   protected void filterChainSetting (){
        tracker = new BlurringFilter2DTracker(chip);
        ( (EventFilter2D)tracker ).addObserver(this);
        setEnclosedFilterChain(new FilterChain(chip));
        getEnclosedFilterChain().add((EventFilter2D)tracker);
        ( (EventFilter2D)tracker ).setEnclosed(true,this);
        ( (EventFilter2D)tracker ).setFilterEnabled(isFilterEnabled());
    }


    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        out = tracker.filterPacket(in);
        return out;
    }

    @Override
    public void initFilter() {
        tracker.initFilter();
        endTimePrevGesture = 0;
        lpf.setTauMs(tauPathMs);
    }

    @Override
    public void resetFilter() {
        tracker.resetFilter();
        endTimePrevGesture = 0;
        lpf.setTauMs(tauPathMs);
    }

    @Override
    public synchronized void setFilterEnabled (boolean filterEventsEnabled){
        super.setFilterEnabled(filterEventsEnabled);
        
        if ( hmmDP != null ){
            if ( filterEventsEnabled ){
                hmmDP.setVisible(true);
            } else{
                hmmDP.setVisible(false);
            }
        }
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        // do nothing
    }

    /**
     * load gesture Images
     */
    protected final void loadGestureImages(){
        Toolkit myToolkit = Toolkit.getDefaultToolkit();
        
        imgHi = myToolkit.getImage(pathGesturePictures + "hi.jpg");
        hmmDP.putImage(imgHi);
        imgBye = myToolkit.getImage(pathGesturePictures + "bye.jpg");
        hmmDP.putImage(imgBye);
        imgLeft = myToolkit.getImage(pathGesturePictures + "left.jpg");
        hmmDP.putImage(imgLeft);
        imgRight = myToolkit.getImage(pathGesturePictures + "right.jpg");
        hmmDP.putImage(imgRight);
        imgUp = myToolkit.getImage(pathGesturePictures + "up.jpg");
        hmmDP.putImage(imgUp);
        imgDown = myToolkit.getImage(pathGesturePictures + "Down.jpg");
        hmmDP.putImage(imgDown);
        imgCW = myToolkit.getImage(pathGesturePictures + "clockwise.jpg");
        hmmDP.putImage(imgCW);
        imgCCW = myToolkit.getImage(pathGesturePictures + "counterclockwise.jpg");
        hmmDP.putImage(imgCCW);
        imgCheck = myToolkit.getImage(pathGesturePictures + "check.jpg");
        hmmDP.putImage(imgCheck);
//        imgPush = myToolkit.getImage(pathGesturePictures + "push.jpg");
//        hmmDP.putImage(imgPush);
    }

    @Override
    public void update(Observable o, Object arg) {
        if ( o instanceof BlurringFilter2DTracker ){
            List<BlurringFilter2DTracker.Cluster> cl = tracker.getClusters();
            ArrayList<ClusterPathPoint> path = selectClusterTrajectory(cl);

            if(path == null)
                return;

             // default trimming
            ArrayList<ClusterPathPoint> trimmedPath = trajectoryTrimmingPointBase(path, 2, 2);
//            ArrayList<ClusterPathPoint> trimmedPath = path;

            // doesn't have to classify short trajectroies
            if(trimmedPath.size() < numPointsThreshold)
            {
                if(prevPath == null || doesAccumulate(trimmedPath, checkActivationTimeUs)){
                    storePath(trimmedPath, true);
                }
                return;
            }

            String bmg = null;
            if(login){
                // estimates the best matching gesture
                bmg = estimateGesture(trimmedPath);

                if(usePrevPath && bmg == null)
                    bmg = estimateGestureWithPrevPath(trimmedPath, checkActivationTimeUs);

                System.out.println("Best matching gesture is " + bmg);

                if(afterRecognitionProcess(bmg, trimmedPath)){
                    endTimePrevGesture = endTimeGesture;

                    // starts or restarts the auto logout timer
                    if(isLogin()){ // has to check if the gesture recognition system is still active (to consider logout)
                        if(autoLogoutTimer.isRunning())
                            autoLogoutTimer.restart();
                        else
                            autoLogoutTimer.start();
                    }

                }
//                else {
//                    storePath(trimmedPath, false);
//                }

            } else {
                if(detectStartingGesture(trimmedPath)){
                    System.out.println("Gesture recognition system is enabled.");
                    afterRecognitionProcess("Infinite", trimmedPath);
                    endTimePrevGesture = endTimeGesture;
                }
//                else {
//                    storePath(trimmedPath, false);
//                }
            }
            if(bmg == null || (bmg == null && !bmg.startsWith("Infinite")))
                storePath(trimmedPath, false);
        }
    }

    public void storePath(ArrayList<ClusterPathPoint> path, boolean accumulate){
        if(!accumulate || prevPath == null)
            prevPath = new ArrayList<ClusterPathPoint>();

        for(ClusterPathPoint pt:path){
           ClusterPathPoint clonePt = new ClusterPathPoint(pt.x, pt.y, pt.t, pt.getNEvents());
            clonePt.stereoDisparity = pt.stereoDisparity;
            if(clonePt.velocityPPT != null){
                clonePt.velocityPPT.x = pt.velocityPPT.x;
                clonePt.velocityPPT.y = pt.velocityPPT.y;
            }
            prevPath.add(clonePt);
//            prevPath.add((ClusterPathPoint) pt.clone());
        }
    }

    /**
     * detects the startinf gesture (ie. 'Infinite' shape)
     * It tries several times by trimming the input trajectory.
     * 
     * @param path
     * @return
     */
    protected boolean detectStartingGesture(ArrayList<ClusterPathPoint> path){
        boolean ret = false;
        String bmg = estimateGesture(path);

        if(bmg != null && bmg.startsWith("Infinite"))
            ret = true;
        else {
            if(usePrevPath){
                bmg = estimateGestureWithPrevPath(path, checkActivationTimeUs);
                if(bmg != null && bmg.startsWith("Infinite")){
                    ret = true;
                }
            }
        }

        return ret;
    }


    public boolean tryGestureWithPrevPath(ArrayList<ClusterPathPoint> path, int prevPathTrimmingPercent, String gestureName, int timeDiffTolerenceUs){
        boolean ret = false;
        if(doesAccumulate(path, timeDiffTolerenceUs)){
            ArrayList<ClusterPathPoint> trimmedPath = trajectoryTrimming(prevPath, prevPathTrimmingPercent, 0, FeatureExtraction.calTrajectoryLength(prevPath));

            trimmedPath.addAll(path);
            String bmg = estimateGesture(trimmedPath);
            if(bmg != null && bmg.startsWith(gestureName))
                ret = true;
        }

        return ret;
    }

    /**
     * estimates best matching gesture
     * It tries several times by trimming the input trajectory.
     *
     * @param path
     * @return
     */
    protected String estimateGesture(ArrayList<ClusterPathPoint> path){
        if(path.size() < numPointsThreshold)
            return null;

        String bmg = getBestmatchingGesture(path, -200);

        if(bmg == null){
            double pathLength = FeatureExtraction.calTrajectoryLength(path);
            
            for(int i = 1; i <= 2 ; i++){
                for(int j = 0; j<=1; j++){
                    // retries with the head trimming if failed
                    ArrayList<ClusterPathPoint> trimmedPath = trajectoryTrimming(path, i*headTrimmingPercents/2, j*tailTrimmingPercents, pathLength);
                    if(checkSpeedCriterion(trimmedPath)){
                        bmg = getBestmatchingGesture(trimmedPath, -200 + ((i-1)*2+j+1)*100);
                    } else {
//                        System.out.println("Under speed limit");
//                        break;
                    }

                    if(bmg != null)
                        return bmg;
                }
            }
        }

        return bmg;
    }

    protected String estimateGestureWithPrevPath(ArrayList<ClusterPathPoint> path, int timeDiffTolerenceUs){
        String bmg = null;

        if(doesAccumulate(path, timeDiffTolerenceUs)){
            // saws two segments
            ArrayList<ClusterPathPoint> path2 = new ArrayList<ClusterPathPoint>();
            path2.addAll(prevPath);
            path2.addAll(path);
            bmg = estimateGesture(path2);
            prevPath = null; // makes it null since it's used
        }

        return bmg;
    }

    public boolean doesAccumulate(ArrayList<ClusterPathPoint> path, int timeDiffTolerenceUs){
        boolean ret = false;

        if(prevPath != null){
            ClusterPathPoint lastPointPrevPath = prevPath.get(prevPath.size()-1);
            ClusterPathPoint firstPointPath = path.get(0);

            // checks time diffence
            if((firstPointPath.t - lastPointPrevPath.t) <= timeDiffTolerenceUs){
                // checks distance
                float distance = (float) FeatureExtraction.distance(new Point2D.Float(lastPointPrevPath.x, lastPointPrevPath.y),
                                                                    new Point2D.Float(firstPointPath.x, firstPointPath.y));
                if(distance <= chip.getSizeX()*0.20f)
                    ret = true;
                else
                    System.out.println("distance constraint");
            } else
                System.out.println("time constraint");
        } else
            System.out.println("null constraint");

        return ret;
    }

    /**
     * returns the best matching gesture
     *
     * @param path
     * @return
     */
    private String getBestmatchingGesture(ArrayList<ClusterPathPoint> path, int offset){
        String[] codewards = fve.convTrajectoryToCodewords(path);
        String bmg = hmmDP.ghmm.getBestMatchingGesture(codewards, fve.vectorAngleSeq);

/*
        // draws the quantized vectors
        if(offset == -200)
            hmmDP.clearImage();
        hmmDP.drawTrajectory(FeatureExtraction.convAnglesToTrajectoryInScaledArea(new Point2D.Float(hmmDP.centerX+offset, hmmDP.centerY+offset), hmmDP.centerY/2, fve.vectorAngleSeq));

        // draws the trajectory
        ArrayList<Point2D.Float> tmpPath = new ArrayList<Point2D.Float>();
        for(ClusterPathPoint pt:path)
            tmpPath.add(new Point2D.Float(pt.x*2 + 200 + offset, pt.y*2));
        hmmDP.drawTrajectoryDot(tmpPath);

        hmmDP.repaint();
        System.out.println(offset + ": " + bmg);
*/
        return bmg;
    }

    /**
     * puts an image on the screen based on the result of gesture recognition
     *
     * @param bmg
     * @return
     */
    protected boolean afterRecognitionProcess(String bmg, ArrayList<ClusterPathPoint> path){
        if(bmg == null)
            return false;

        boolean ret = true;
//        boolean nullifyPrevPath = true;

        if(login){
             if(bmg.startsWith("Infinite")){
                doLogout();
            } else if(bmg.startsWith("Push")){
                doPush();
            } else if(bmg.startsWith("SlashUp")){
                ret = doSlashUp(path);
                // if it's not a part of check-gesture, it should be store to be used later
//                if(!ret){
//                    storePath(path, false);
//                    nullifyPrevPath = false;
//                }
            } else {
                if(bmg.startsWith("SlashDown")){
                    doSlashDown();
                    // Slashdown is not a complete gesture. So, it can be a part of other gestures
 //                  storePath(path, false);
 //                  nullifyPrevPath = false;
                } else {
                    // doesn't have consider refractory time for CW and CCW
                    if(bmg.startsWith("CW")){
                        doCW(path);
                    }else if(bmg.startsWith("CCW")){
                        doCCW(path);
                    }

                    // has to consider refractory time for Left, Right, Up, Down, and Check
                    // doesn't have to consider refractory time if checkActivated is true (i.e. SlashDown is detected) becase SlashDown is a partial gesture
                    if(checkActivated || startTimeGesture >= endTimePrevGesture + refractoryTimeMs*1000){
                        if(bmg.startsWith("Left")){
                            doLeft();
                        }else if(bmg.startsWith("Right")){
                            doRight();
                        }else if(bmg.startsWith("Up")){
                            doUp();
                        }else if(bmg.startsWith("Down")){
                            doDown();
                        }else if(bmg.startsWith("Check")){
                            doCheck();
                        }
                    } else {
                        endTimePrevGesture -= (refractoryTimeMs*1000);
                        ret = false;
                    }

                    checkActivated = false;
                }
            }
        } else {
            if(bmg.startsWith("Infinite")){
                doLogin();
            }
        }

//        if(nullifyPrevPath)
//            prevPath = null;

        return ret;
    }


    /**
     * selects the best trajectory from clusters
     *
     * @param cl
     * @return
     */
    protected ArrayList<ClusterPathPoint> selectClusterTrajectory(List<BlurringFilter2DTracker.Cluster> cl){
        ArrayList<ClusterPathPoint> selectedTrj = null;
        BlurringFilter2DTracker.Cluster selectedCluster = null;

        int maxNumPoint = 0;

        // select a candidate trajectory
        for (BlurringFilter2DTracker.Cluster c: cl){
            // doesn't have to check alive cluster
            if (!c.isDead()){
                continue;
            }
          
            // checks number of points
            if(c.getPath().size() < numPointsThreshold){
                continue;
            } else {
                // search the largest cluster
                ArrayList<ClusterPathPoint> path = c.getPath();
                if(path.size() > maxNumPoint){
                    selectedTrj = path;
                    maxNumPoint = path.size();
                    selectedCluster = c;
                }
            }
        }
        if(selectedTrj == null)
            return null;
        
        // gesture speed check
        if(!checkSpeedCriterion(selectedTrj)){
            return null;
        }

        // low-pass filtering
        ArrayList<ClusterPathPoint> retTrj = null;
        if(enableLPF)
            retTrj = lowPassFiltering(selectedTrj);
        else
            retTrj = selectedTrj;

        // records start and end time of the selected trajectory
        if(retTrj != null){
            startTimeGesture = selectedCluster.getBirthTime();
            endTimeGesture = selectedCluster.getLastEventTimestamp();
        }

        return retTrj;
    }

    /**
     * checks speed criterion.
     * returns true if a certain number of points have velocity higher than maxSpeedThreshold_kPPT
     *
     * @param path
     * @return
     */
    private boolean checkSpeedCriterion(ArrayList<ClusterPathPoint> path){
        boolean ret = true;

        // gesture speed check, At least 5% of the points velocity have to exceed speed threshold.
        int numValidPoints = Math.max(1, (int) (path.size()*0.05));
        for(int i=0; i<path.size(); i++){
            ClusterPathPoint point = path.get(i);
            if(point.velocityPPT != null){
                double speed = 1000*Math.sqrt(Math.pow(point.velocityPPT.x, 2.0)+Math.pow(point.velocityPPT.y, 2.0));
                if(speed >= maxSpeedThreshold_kPPT)
                    numValidPoints--;
            }
        }
        if(numValidPoints > 0)
            ret = false;

        return ret;
    }

    /**
     * trims a trajectory
     *
     * @param trajectory
     * @param headTrimmingPercets
     * @param tailTrimmingPercets
     * @return
     */
    protected ArrayList<ClusterPathPoint> trajectoryTrimming(ArrayList<ClusterPathPoint> trajectory, int headTrimmingPercets, int tailTrimmingPercets, double trjLength){
        ArrayList<ClusterPathPoint> trimmedTrj;
        int numPointsHeadTrimming = 0;
        int numPointsTailTrimming = 0;

//        int numPointsHeadTrimming = (int) (trajectory.size()*0.01*headTrimmingPercets);
//        int numPointsTailTrimming = (int) (trajectory.size()*0.01*tailTrimmingPercets);


        if(headTrimmingPercets > 0)
            numPointsHeadTrimming = FeatureExtraction.getTrajectoryPositionForward(trajectory, trjLength*0.01*headTrimmingPercets);
        if(tailTrimmingPercets > 0)
            numPointsTailTrimming = trajectory.size() - 1 - FeatureExtraction.getTrajectoryPositionBackward(trajectory, trjLength*0.01*tailTrimmingPercets);

        if(numPointsHeadTrimming + numPointsTailTrimming > 0 && numPointsHeadTrimming + numPointsTailTrimming < trajectory.size()){
            trimmedTrj = new ArrayList<ClusterPathPoint>(trajectory.size() - numPointsHeadTrimming - numPointsTailTrimming);
            for(int j=numPointsHeadTrimming; j<trajectory.size()-numPointsTailTrimming; j++)
                trimmedTrj.add(trajectory.get(j));
        } else
            trimmedTrj = trajectory;

        return trimmedTrj;
    }

        protected ArrayList<ClusterPathPoint> trajectoryTrimmingPointBase(ArrayList<ClusterPathPoint> trajectory, int headTrimmingPercets, int tailTrimmingPercets){
        ArrayList<ClusterPathPoint> trimmedTrj;

        int numPointsHeadTrimming = (int) (trajectory.size()*0.01*headTrimmingPercets);
        int numPointsTailTrimming = (int) (trajectory.size()*0.01*tailTrimmingPercets);

        if(numPointsHeadTrimming + numPointsTailTrimming > 0 && numPointsHeadTrimming + numPointsTailTrimming < trajectory.size()){
            trimmedTrj = new ArrayList<ClusterPathPoint>(trajectory.size() - numPointsHeadTrimming - numPointsTailTrimming);
            for(int j=numPointsHeadTrimming; j<trajectory.size()-numPointsTailTrimming; j++)
                trimmedTrj.add(trajectory.get(j));
        } else
            trimmedTrj = trajectory;

        return trimmedTrj;
    }

    /**
     * does low-pass filtering to smoothe the trajectory
     *
     * @param path
     * @return
     */
    private ArrayList<ClusterPathPoint> lowPassFiltering(ArrayList<ClusterPathPoint> path){
        ArrayList<ClusterPathPoint> lpfPath = new ArrayList<ClusterPathPoint>(path.size());
        ClusterPathPoint p = (ClusterPathPoint) path.get(0).clone();

        lpfPath.add(p);
        lpf.setInternalValue2d(path.get(0).x, path.get(0).y);
        for(int i=1; i<path.size(); i++){
            p = (ClusterPathPoint) path.get(i).clone();
            Point2D.Float pt = lpf.filter2d(p.x, p.y, p.t);
            p.x = pt.x;
            p.y = pt.y;
            lpfPath.add(p);
        }

        return lpfPath;
    }

    /**
     * returns maxSpeedThreshold_kPPT
     *
     * @return
     */
    public float getMaxSpeedThreshold_kPPT() {
        return maxSpeedThreshold_kPPT;
    }

    /** sets maxSpeedThreshold_kPPT
     *
     * @param maxSpeedThreshold_kPPT
     */
    public void setMaxSpeedThreshold_kPPT(float maxSpeedThreshold_kPPT) {
        float old = this.maxSpeedThreshold_kPPT;
        this.maxSpeedThreshold_kPPT = maxSpeedThreshold_kPPT;
        getPrefs().putFloat("GestureBF2D.maxSpeedThreshold_kPPT",maxSpeedThreshold_kPPT);
        support.firePropertyChange("maxSpeedThreshold_kPPT",old,this.maxSpeedThreshold_kPPT);
    }

    /** returns numPointsThreshold
     *
     * @return
     */
    public int getNumPointsThreshold() {
        return numPointsThreshold;
    }

    /** sets numPointsThreshold
     *
     * @param numPointsThreshold
     */
    public void setNumPointsThreshold(int numPointsThreshold) {
        int old = this.numPointsThreshold;
        this.numPointsThreshold = numPointsThreshold;
        getPrefs().putInt("GestureBF2D.numPointsThreshold",numPointsThreshold);
        support.firePropertyChange("numPointsThreshold",old,this.numPointsThreshold);
    }

    /** returns headTrimmingPercents
     *
     * @return
     */
    public int getHeadTrimmingPercents() {
        return headTrimmingPercents;
    }

    /** sets headTrimmingPercents
     *
     * @param headTrimmingPercents
     */
    public void setHeadTrimmingPercents(int headTrimmingPercents) {
        int old = this.headTrimmingPercents;
        this.headTrimmingPercents = headTrimmingPercents;
        getPrefs().putInt("GestureBF2D.headTrimmingPercents",headTrimmingPercents);
        support.firePropertyChange("headTrimmingPercents",old,this.headTrimmingPercents);
    }

    /** returns tailTrimmingPercents
     *
     * @return
     */
    public int getTailTrimmingPercents() {
        return tailTrimmingPercents;
    }

    /** sets tailTrimmingPercents
     *
     * @param tailTrimmingPercents
     */
    public void setTailTrimmingPercents(int tailTrimmingPercents) {
        int old = this.tailTrimmingPercents;
        this.tailTrimmingPercents = tailTrimmingPercents;
        getPrefs().putInt("GestureBF2D.tailTrimmingPercents",tailTrimmingPercents);
        support.firePropertyChange("tailTrimmingPercents",old,this.tailTrimmingPercents);
    }

    /** returns enableLPF
     *
     * @return
     */
    public boolean isEnableLPF() {
        return enableLPF;
    }

    /** sets enableLPF
     * 
     * @param enableLPF
     */
    public void setEnableLPF(boolean enableLPF) {
        boolean old = this.enableLPF;
        this.enableLPF = enableLPF;
        getPrefs().putBoolean("GestureBF2D.enableLPF", enableLPF);
        support.firePropertyChange("enableLPF",old,this.enableLPF);
    }


    /**
     * @return the tauMs
     */
    public float getTauPathMs (){
        return tauPathMs;
    }

    /**
     * The lowpass time constant of the trajectory.
     *
     * @param tauPathMs the tauMs to set
     */
    synchronized public void setTauPathMs (float tauPathMs){
        float old = this.tauPathMs;
        this.tauPathMs = tauPathMs;
        getPrefs().putFloat("GestureBF2D.tauPathMs",tauPathMs);
        support.firePropertyChange("tauPathMs",old,this.tauPathMs);
        lpf.setTauMs(tauPathMs);
    }

    /**
     * returns refractoryTimeMs
     *
     * @return
     */
    public int getRefractoryTimeMs() {
        return refractoryTimeMs;
    }

    /**
     * sets refractoryTimeMs
     * 
     * @param refractoryTimeMs
     */
    public void setRefractoryTimeMs(int refractoryTimeMs) {
        int old = this.refractoryTimeMs;
        this.refractoryTimeMs = refractoryTimeMs;
        getPrefs().putInt("GestureBF2D.refractoryTimeMs", refractoryTimeMs);
        support.firePropertyChange("refractoryTimeMs",old,this.refractoryTimeMs);
    }

    public float getGTCriterion() {
        return GTCriterion;
    }

    public void setGTCriterion(float GTCriterion) {
        float old = this.GTCriterion;
        this.GTCriterion = GTCriterion;
        getPrefs().putFloat("GestureBF2D.GTCriterion", GTCriterion);
        support.firePropertyChange("GTCriterion",old,this.GTCriterion);

        hmmDP.setGTCriterion(GTCriterion);
    }



    public boolean isUsePrevPath() {
        return usePrevPath;
    }

    public void setUsePrevPath(boolean usePrevPath) {
        boolean old = this.usePrevPath;
        this.usePrevPath = usePrevPath;
        getPrefs().putBoolean("GestureBF2D.usePrevPath", usePrevPath);
        support.firePropertyChange("usePrevPath",old,this.usePrevPath);
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
        getPrefs().putBoolean("GestureBF2D.enableAutoLogout",enableAutoLogout);
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
        getPrefs().putInt("GestureBF2D.autoLogoutTimeMs",autoLogoutTimeMs);
        support.firePropertyChange("autoLogoutTimeMs",old,this.autoLogoutTimeMs);

        if(autoLogoutTimer.isRunning()){
            autoLogoutTimer.stop();
            autoLogoutTimer = new Timer(autoLogoutTimeMs, autoLogoutAction);
            autoLogoutTimer.start();
        } else {
            autoLogoutTimer = new Timer(autoLogoutTimeMs, autoLogoutAction);
        }
    }

    public ArrayList<ClusterPathPoint> getPrevPath() {
        return prevPath;
    }

    public static int getCheckActivationTimeUs() {
        return checkActivationTimeUs;
    }


    /**
     * Class for HMM and GUI
     */
    class HmmDrawingPanel extends TrajectoryDrawingPanel implements ItemListener{
        /**
         * Button names
         */
        public final String REMOVE = "Remove";
        public final String ADD = "Add";
        public final String SHOW = "Show";
        public final String RESET = "Reset";
        public final String LEARN = "Learn";
        public final String GUESS = "Guess";

        /**
         * Optional HMM models
         */
        public final String ERGODIC = "ERGODIC";
        public final String LR = "LR";
        public final String LRB = "LRB";
        public final String LRC = "LRC";
        public final String LRBC = "LRBC";

        /**
         * Stores gesture names in a set to guarantee the uniqueness of names
         */
        public HashSet<String> gestureItems = new HashSet<String>();

        /**
         * combo box for choosing a gesture from the registered gesture set
         */
        protected JComboBox gestureChoice;
        /**
         * combo box for choosing a HMM model
         */
        protected JComboBox hmmModelChoice;
        /**
         * text field for entering the name of a new gesture to register
         */
        protected JTextField newGesture;
        /**
         * for saving and loading of gesture HMM
         */
        protected JFileChooser fileChooser;
        /**
         * make it true to manually activate gesture recognition system
         */
        protected JCheckBoxMenuItem checkGestureAction;

        /**
         * All gestures have the same number of states.
         */
        protected int numState = 5;

        /**
         *  Feature vector space consists of 16 quantized vectors.
         */
        protected final String[] featureVectorSpace = new String[] {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15"};

        /**
         * use dynamic threshold model. If you set 'false' instead of 'true', you can use a static threshold model.
         */
        protected GestureHmm ghmm = new GestureHmm(featureVectorSpace, GestureHmm.GAUSSIAN_THRESHOLD);

        /**
         * Output statement buffer
         */
        protected String msg = "";

        /**
         * x of the center x of image panel
         */
        protected float centerX = imgPanelWidth/2;
        /**
         * y of the center of image panel
         */
        protected float centerY = imgPanelHeight/2;
        /**
         * size of show panel
         */
        protected float showPanelSize = Math.min(centerX, centerY);

        /**
         * timer for image load
         */
        protected Timer timer;

        /**
         * folder 
         */
        private String defaultFolder = "";

        /**
         * constructor
         * @param title
         * @param buttonNames
         */
        public HmmDrawingPanel(String title, String[] buttonNames) {
            super(title, 700, 700, buttonNames);

            //creates a file chooser
            fileChooser = new JFileChooser();

            // creates a timer
            timer = new Timer(700, clearImageAction);
            setGTCriterion(GTCriterion);
        }

        @Override
        public void buttonLayout(String[] componentNames) {
            gestureChoice = new JComboBox();
            gestureChoice.setName("gestureChoice");
            gestureChoice.addItem("Select a gesture");
            hmmModelChoice = new JComboBox();
            hmmModelChoice.setName("hmmModelChoice");
            hmmModelChoice.addItem("Select HMM model");
            hmmModelChoice.addItem(ERGODIC);
            hmmModelChoice.addItem(LR);
            hmmModelChoice.addItem(LRB);
            hmmModelChoice.addItem(LRC);
            hmmModelChoice.addItem(LRBC);
            newGesture = new JTextField();
            newGesture.setText("New gesture name");

            // configuration of button panel
            buttonPanel.setLayout(new GridLayout(2, (componentNames.length+3)/2));

            // adds gesture choice
            buttonPanel.add(gestureChoice, "1");
            gestureChoice.addItemListener(this);

            // adds new gesture name
            buttonPanel.add(newGesture, "2");

            // adds HMM model choice
            buttonPanel.add(hmmModelChoice, "3");
            hmmModelChoice.addItemListener(this);

            // adds buttons
            JButton newButton;
            for(int i = 0; i< componentNames.length; i++){
                newButton = new JButton(componentNames[i]);
                buttonPanel.add(newButton, ""+(i+4));
                newButton.addActionListener(buttonActionListener);
            }
            JButton clearButton = new JButton(clearButtonName);
            buttonPanel.add(clearButton, ""+ (componentNames.length + 4));
            clearButton.addActionListener(buttonActionListener);
        }

        @Override
        public void menuLayout() {
            // creates and adds drop down menus to the menu bar
            JMenu fileMenu = new JMenu("File");
            menuBar.add(fileMenu);
            JMenu gestureMenu = new JMenu("Gesture");
            menuBar.add(gestureMenu);

            // creates and adds menu items to menus
            JMenuItem newAction = new JMenuItem("New");
            JMenuItem loadAction = new JMenuItem("Load");
            JMenuItem saveAction = new JMenuItem("Save");
            fileMenu.add(newAction);
            fileMenu.add(loadAction);
            fileMenu.add(saveAction);

            // Create and add CheckButton for enabling gesture recognition
            checkGestureAction = new JCheckBoxMenuItem("Activates Gesture Recognition");
            checkGestureAction.setState(login);
            gestureMenu.add(checkGestureAction);

            // add action listeners
            newAction.addActionListener(menuActionListener);
            loadAction.addActionListener(menuActionListener);
            saveAction.addActionListener(menuActionListener);
            checkGestureAction.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    AbstractButton aButton = (AbstractButton) e.getSource();
                    if(aButton.getModel().isSelected()){
                        login = true;
                        System.out.println("Gesture recognition is mannually activated.");
                    }else{
                        doLogout();
                        System.out.println("Gesture recognition is mannually Inactivated.");
                    }

                    clearImage();
                }
            });
        }

        @Override
        public void buttonAction(String buttonName) {
            if(buttonName.equals(LEARN)){
                doLearn();
                clearImage();
            } else if(buttonName.equals(ADD)){
                doAddGesture();
            } else if(buttonName.equals(REMOVE)){
                doRemoveGesture();
            } else if(buttonName.equals(GUESS)){
                doGuess();
            } else if(buttonName.equals(RESET)){
                doReset();
                clearImage();
            } else if(buttonName.equals(SHOW)){
                doShow();
            }
        }

        @Override
        @SuppressWarnings("CallToThreadDumpStack")
        public void menuAction(String menuName) {
            if(menuName.equals("New")){
                doNew();
            } else if(menuName.equals("Load")){
                try{
                    doLoad();
                } catch(ClassNotFoundException e){
                    e.printStackTrace();
                }
            } else if(menuName.equals("Save")){
                doSave();
            }

            repaint();
        }


        /**
         * excutes Remove button
         */
        public void doRemoveGesture(){
            String gesName = (String) gestureChoice.getSelectedItem();
            if(gesName == null || gesName.equals("") || gesName.equals("Select a gesture")){
                System.out.println("Warning: Gesture is not selected.");
                return;
            }

            ghmm.removeGesture(gesName);
            gestureChoice.removeItem(gesName);
            gestureItems.remove(gesName);
            System.out.println(gesName + " was removed.");
        }

        /**
         * excutes Add button
         */
        public void doAddGesture(){
            String newGestName = newGesture.getText();
            if(newGestName.equals("")){
                System.out.println("Warning: Gesture name is not specified.");
                return;
            }

            if(((String) hmmModelChoice.getSelectedItem()).startsWith("Select HMM model")) {
                System.out.println("Warning: HMM model is not specified.");
                return;
            }

            String gestName = newGestName+"_"+hmmModelChoice.getSelectedItem();

            if(!gestureItems.contains(gestName)){
                gestureItems.add(gestName);
                gestureChoice.addItem(gestName);
                HiddenMarkovModel.ModelType selectedModel;
                if(hmmModelChoice.getSelectedItem().equals("ERGODIC"))
                    selectedModel = HiddenMarkovModel.ModelType.ERGODIC_RANDOM;
                else if(hmmModelChoice.getSelectedItem().equals("LR"))
                    selectedModel = HiddenMarkovModel.ModelType.LR_RANDOM;
                else if(hmmModelChoice.getSelectedItem().equals("LRB"))
                    selectedModel = HiddenMarkovModel.ModelType.LRB_RANDOM;
                else if(hmmModelChoice.getSelectedItem().equals("LRC"))
                    selectedModel = HiddenMarkovModel.ModelType.LRC_RANDOM;
                else if(hmmModelChoice.getSelectedItem().equals("LRBC"))
                    selectedModel = HiddenMarkovModel.ModelType.LRBC_RANDOM;
                else{
                    System.out.println("Warning: Failed to add a new gesture.");
                    return;
                }

                ghmm.addGesture(gestName, numState,  selectedModel);
                ghmm.initializeGestureRandom(gestName);

                System.out.println("A new gesture ("+ gestName + ") is added.");
            }
            gestureChoice.setSelectedItem(gestName);
            newGesture.setText("");
        }

        /**
         * excutes Learn button
         */
        public void doLearn(){
            String gesName = (String) gestureChoice.getSelectedItem();
            if(gesName == null || gesName.equals("") || gesName.equals("Select a gesture")){
                System.out.println("Warning: Gesture is not selected.");
                return;
            }

            String[] fv = fve.convTrajectoryToCodewords(trajectory);
            if(fv[0] == null){
                System.out.println("Warning: No trajectory is dected.");
                return;
            }
            System.out.println("Learning " + gesName);

            boolean learningSuccess;
            HiddenMarkovModel.ModelType modelType = ghmm.getGestureHmm(gesName).getModelType();

            // for LRC & LRBC, we don't have to update start probability
            if(modelType == HiddenMarkovModel.ModelType.LRC_RANDOM ||  modelType == HiddenMarkovModel.ModelType.LRBC_RANDOM)
                learningSuccess = ghmm.learnGesture(gesName, fv, fve.vectorAngleSeq, false, true, true);
            else
                learningSuccess = ghmm.learnGesture(gesName, fv, fve.vectorAngleSeq, true, true, true);

            if(learningSuccess){
                if(ghmm.getGestureHmm(gesName).getNumTraining() == 1)
                    System.out.println(gesName+" is properly registered. Log{P(O|model)} = " + Math.log10(ghmm.getGestureLikelyhood(gesName, fv)));
                else if(ghmm.getGestureHmm(gesName).getNumTraining() == 2)
                    System.out.println(gesName+" has been trained twice. Log{P(O|model)} = " + Math.log10(ghmm.getGestureLikelyhood(gesName, fv)));
                else
                    System.out.println(gesName+" has been trained " + ghmm.getGestureHmm(gesName).getNumTraining() + " times. Log{P(O|model)} = " + Math.log10(ghmm.getGestureLikelyhood(gesName, fv)));

//                ghmm.printGesture(gesName);
//                ghmm.printThresholdModel();
//                ghmm.getGestureHmm(gesName).viterbi(fv);
//                System.out.println("Viterbi path : " + ghmm.getGestureHmm(gesName).getViterbiPathString(fv.length));
            }
        }

        /**
         * excutes Guess button
         */
        public void doGuess(){
            String[] fv = fve.convTrajectoryToCodewords(trajectory);

            if(fv[0] == null){
                System.out.println("Warning: No trajectory is dected.");
                return;
            }

            String bmg = ghmm.getBestMatchingGesture(fv, fve.vectorAngleSeq);
            gImg.setFont(new Font("Arial", Font.PLAIN, 24));

            // erase previous message
            Color tmpColor = getColor();
            gImg.setColor(this.getBackground());
            gImg.drawString(msg, 40 + imgPanelWidth/2 - msg.length()*12/2, imgPanelHeight - 20);
            gImg.setColor(tmpColor);

            if(bmg == null){
                msg = "No gesture is found.";
                System.out.println(msg);
            }else{
                msg = String.format("Best matching gesture is %s", bmg);
                System.out.println(msg +" with probability "+Math.log10(ghmm.getGestureLikelyhood(bmg, fv)));
//                ghmm.getGestureHmm(bmg).viterbi(fv);
//                System.out.println("Viterbi path : " + ghmm.getGestureHmm(bmg).getViterbiPathString(fv.length));
            }
            gImg.drawString(msg, 40 + imgPanelWidth/2 - msg.length()*12/2, imgPanelHeight - 20);
            repaint();

            resetTrajectory();
        }


        /**
         * excutes Show button
         */
        public void doShow(){
            String gesName = (String) gestureChoice.getSelectedItem();
            if(gesName == null || gesName.equals("") || gesName.equals("Select a gesture")){
                System.out.println("Warning: Gesture is not selected.");
                return;
            }

            double[] meanFVarray = ghmm.getAverageFeaturesToArray(gesName);

            clearImage();

            // draws frame
            int margin = 30;
            int shadow = 10;
            Color tmp = getColor();
            gImg.setColor(Color.DARK_GRAY);
            gImg.fillRect((int) (centerX - showPanelSize/2) - margin + shadow, (int) (centerY - showPanelSize/2) - margin + shadow, (int) showPanelSize + 2*margin, (int) showPanelSize + 2*margin);
            gImg.setColor(Color.WHITE);
            gImg.fillRect((int) (centerX - showPanelSize/2) - margin, (int) (centerY - showPanelSize/2) - margin, (int) showPanelSize + 2*margin, (int) showPanelSize + 2*margin);
            gImg.setColor(Color.BLACK);
            gImg.drawRect((int) (centerX - showPanelSize/2) - margin, (int) (centerY - showPanelSize/2) - margin, (int) showPanelSize + 2*margin, (int) showPanelSize + 2*margin);
            gImg.setFont(new Font("Arial", Font.PLAIN, 24));
            gImg.drawString(gesName+" (# of training: "+ghmm.getGestureHmm(gesName).getNumTraining()+")", (int) centerX - (int) showPanelSize/2 - margin, (int) centerY - (int) showPanelSize/2 - margin - 10);
            gImg.setColor(tmp);

            // draws trajectory
            if(ghmm.getGestureHmm(gesName).getNumTraining() > 0)
                drawTrajectory(FeatureExtraction.convAnglesToTrajectoryInScaledArea(new Point2D.Float(centerX, centerY), showPanelSize, meanFVarray));
            else
                gImg.drawString("Hey, man.", (int) centerX - 50, (int) centerY);

            repaint();
        }

        /**
         * excutes Reset button
         */
        public void doReset(){
            String gesName = (String) gestureChoice.getSelectedItem();
            if(gesName == null || gesName.equals("") || gesName.equals("Select a gesture")){
                System.out.println("Warning: Gesture is not selected.");
                return;
            }

            ghmm.resetGesture(gesName);
            System.out.println(gesName + " is reset now.");
        }

        /**
         * excutes New menu
         */
        public void doNew(){
            ghmm = new GestureHmm(featureVectorSpace, GestureHmm.GAUSSIAN_THRESHOLD);
            gestureItems.clear();
            gestureChoice.removeAllItems();
            gestureChoice.addItem("Select a gesture");

            System.out.println("Created a new gesture set.");
      }

        /**
         * excutes Save menu
         */
        @SuppressWarnings("CallToThreadDumpStack")
        public void doSave(){
            int returnVal = fileChooser.showSaveDialog(HmmDrawingPanel.this);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();

                // do saving things here
                try{
                    FileOutputStream fos = new FileOutputStream(file.getAbsoluteFile());
                    BufferedOutputStream bos = new BufferedOutputStream(fos);
                    ObjectOutputStream oos = new ObjectOutputStream(bos);

                    oos.writeObject(ghmm);
                    oos.close();
                    log.log(Level.WARNING, "Gesture HMM has been saved in {0}", file.getAbsoluteFile());
                } catch (IOException e){
                    e.printStackTrace();
                }
            } else {
                // canceled
            }
        }

        /**
         * excutes Save menu
         *
         * @throws ClassNotFoundException
         */
        @SuppressWarnings("CallToThreadDumpStack")
        public void doLoad() throws ClassNotFoundException{
            int returnVal = fileChooser.showOpenDialog(HmmDrawingPanel.this);

            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();

                // do loading things here
                try{
                    FileInputStream fis = new FileInputStream(file.getAbsoluteFile());
                    BufferedInputStream bis = new BufferedInputStream(fis);
                    ObjectInputStream ois = new ObjectInputStream(bis);

                    ghmm = (GestureHmm) ois.readObject();
                    gestureItems.clear();
                    gestureItems.addAll(ghmm.getGestureNames());
                    gestureChoice.removeAllItems();
                    gestureChoice.addItem("Select a gesture");
                    for(String gname:gestureItems)
                        gestureChoice.addItem(gname);
                    
                    ois.close();
                    log.log(Level.WARNING, "Gesture HMM has been loaded in {0}", file.getAbsoluteFile());


//                    String[] bestSeq = new String[] {"6", "15", "10", "3", "13", "6", "15", "10", "3", "13", "6", "15", "10", "3", "13", "6"};
//                    String[] idealSeq = new String[] {"12", "13", "14", "15", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"};
//                    String[] localSeq = new String[] {"12", "13", "14", "15", "0", "1", "2", "3", "13", "14", "15", "0", "1", "2", "3", "4"};
//                    System.out.println("bestSeq = " + ghmm.getGestureHmm("CW_LRBC").forward(bestSeq));
//                    System.out.println("idealSeq = " + ghmm.getGestureHmm("CW_LRBC").forward(idealSeq));
//                    System.out.println("localSeq = " + ghmm.getGestureHmm("CW_LRBC").forward(localSeq));
                } catch (IOException e){
                    e.printStackTrace();
                }
            } else {
                // canceled
            }
        }

        /**
         * puts an image on the drawing panel
         *
         * @param img
         */
        public void putImage(Image img){
            clearImage();
            gImg.drawImage(img, (int) centerX - img.getWidth(this)/2, (int) centerY - img.getHeight(this)/2, this);
            repaint();

            if(timer.isRunning())
                timer.restart();
            else
                timer.start();
        }

        /**
         * action listener for timer events
         */
        ActionListener clearImageAction = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
               clearImage();
               timer.stop();
            }
        };

        @Override
        protected void initialDeco() {
            super.initialDeco();
            Color tmpColor = getColor();
            Font tmpFont = getFont();
            gImg.setFont(new Font("Arial", Font.BOLD|Font.ITALIC, 20));
            if(login){
                gImg.setColor(Color.RED);
                gImg.drawString("Active", imgPanelWidth - 100, 20);
            }else{
                gImg.setColor(Color.GRAY);
                gImg.drawString("Inactive", imgPanelWidth - 100, 20);
            }
            gImg.setColor(tmpColor);
            gImg.setFont(tmpFont);

        }


        /**
         * processes Choice events
         * @param e
         */
        @Override
        public void itemStateChanged(ItemEvent e) {
            if(String.valueOf(e.getSource()).contains("gestureChoice")){
                if(e.getStateChange() == ItemEvent.SELECTED && !String.valueOf(e.getItem()).equals("Select a gesture")){
                    System.out.println("Gesture selection : " + e.getItem() + " is selected.");
                }
            } else {
                if(e.getStateChange() == ItemEvent.SELECTED && !String.valueOf(e.getItem()).equals("Select HMM model")){
                    System.out.println("HMM model selection: " + e.getItem() + " is selected.");
                }
            }
        }

        @Override
        public void windowClosing(WindowEvent we) {
            // set the window just invisible
            hmmDP.setVisible(false);
        }

        public final void setGTCriterion(float criterion) {
            ghmm.setGTCriterion(criterion);
        }
    }

    /**
     * returns true if login is true
     * 
     * @return
     */
    public boolean isLogin() {
        return login;
    }


    /**
     * Definition of after-gesture processes
     */

    protected void doLogin(){
        hmmDP.putImage(imgHi);
        login = true;
        hmmDP.checkGestureAction.setState(login);

        // starts the auto logout timer
        autoLogoutTimer.start();
    }

    protected void doLogout(){
        hmmDP.putImage(imgBye);
        login = false;
        hmmDP.checkGestureAction.setState(login);

        // stop auto-logout timer
        autoLogoutTimer.stop();
    }

    protected void doPush(){
        // for stereo vision
//        hmmDP.putImage(imgPush);
    }

    protected boolean doSlashUp(ArrayList<ClusterPathPoint> path){
        boolean ret = true;

        // checks over-segmentation
        if(!checkActivated && prevPath != null){
            System.out.print("Check the previous segment ---> ");

            if(tryGestureWithPrevPath(path, 60, "Check", checkActivationTimeUs))
            {
                System.out.println("Check");
                checkActivated = true;
            } else {
                System.out.println("null");
                ret = false;
            }
        }

        if(ret && checkActivated)
            hmmDP.putImage(imgCheck);

        checkActivated = false;

        return ret;
    }

    protected void doSlashDown(){
        checkActivated = true;
    }

    protected void doCW(ArrayList<ClusterPathPoint> path){
        // to detect broken infinite shaped gestures
        if(prevPath != null && tryGestureWithPrevPath(path, 0, "Infinite", checkActivationTimeUs)){
            System.out.println("----> might be an infinite-shaped gesture");
            doLogout();
        } else
            hmmDP.putImage(imgCW);
    }

    protected void doCCW(ArrayList<ClusterPathPoint> path){
        // to detect broken infinite shaped gestures
        if(prevPath != null && tryGestureWithPrevPath(path, 0, "Infinite", checkActivationTimeUs)){
            System.out.println("----> might be an infinite-shaped gesture");
            doLogout();
        } else
            hmmDP.putImage(imgCCW);
    }

    protected void doLeft(){
        hmmDP.putImage(imgLeft);
    }

    protected void doRight(){
        hmmDP.putImage(imgRight);
    }

    protected void doUp(){
//        if(checkActivated && startTimeGesture <= endTimePrevGesture + checkActivationTimeUs)
//            hmmDP.putImage(imgCheck);
//        else
            hmmDP.putImage(imgUp);
    }

    protected void doDown(){
        hmmDP.putImage(imgDown);
    }

    protected void doCheck(){
        hmmDP.putImage(imgCheck);
    }


}
