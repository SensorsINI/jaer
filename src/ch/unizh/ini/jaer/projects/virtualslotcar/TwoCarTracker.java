/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.virtualslotcar;

import com.sun.opengl.util.GLUT;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Timer;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.BackgroundActivityFilter;
import net.sf.jaer.eventprocessing.tracking.*;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.filter.LowpassFilter;

/**
 * Tracks two slot cars using a SlotcarTrack model and determines which car is which.
 * 
 *
 * @author tobi
 */
public class TwoCarTracker extends RectangularClusterTracker implements FrameAnnotater, PropertyChangeListener, CarTracker {
   public static String getDescription(){ return "Slot car car tracker for finding closest car to track model";}

    // properties
    private boolean onlyFollowTrack = getBoolean("onlyFollowTrack", true);
    private float relaxToTrackFactor = getFloat("relaxToTrackFactor", 0.05f);
    private float distanceFromTrackMetricTauMs = getFloat("distanceFromTrackMetricTauMs", 200);
    private int minSegmentsToBeCarCluster = getInt("minSegmentsToBeCarCluster", 4);
    private float maxDistanceFromTrackPoint = getFloat("maxDistanceFromTrackPoint", 15); // pixels - need to set in track model
    protected float segmentSpeedTauMs = getFloat("segmentSpeedTauMs", 400);
    // vars
    private SlotcarTrack track;
    private TwoCarCluster currentCarCluster = null, crashedCar=null;
    private NearbyTrackEventFilter nearbyTrackFilter = null;
    private TwoCarCluster computerControlledCarCluster = null;

    public TwoCarTracker(AEChip chip) {
        super(chip);
        final String s="TwoCarTracker";
        setPropertyTooltip(s,"onlyFollowTrack", "If set, clusters will only follow the track. If false, clusters can follow car off the track.");
        setPropertyTooltip(s,"relaxToTrackFactor", "Tracking will normally only parallel the track. This factor control how much the cluster converges onto the track, i.e., the allowed normal motion as fraction of the parallel motion.");
        setPropertyTooltip(s,"distanceFromTrackMetricTauMs", "Each car cluster distance from track model is lowpass filtered with this time constant in ms; the closest one is chosen as the computer controlled car");
        setPropertyTooltip(s,"minSegmentsToBeCarCluster", "a CarCluster needs to pass at least this many segments to be marked as the car cluster");
        setPropertyTooltip(s,"maxDistanceFromTrackPoint", "Maximum allowed distance in pixels from track spline point to find nearest spline point; if currentTrackPos=-1 increase maxDistanceFromTrackPoint");
        setPropertyTooltip(s,"segmentSpeedTauMs", "time constant in ms for filtering segment speeed along track");

        // set reasonable defaults
        if (!isPreferenceStored("maxNumClusters")) {
            setMaxNumClusters(2);
        }
        if (!isPreferenceStored("highwayPerspectiveEnabled")) {
            setHighwayPerspectiveEnabled(false);
        }
        if (!isPreferenceStored("enableClusterExitPurging")) {
            setEnableClusterExitPurging(false);
        }
        if (!isPreferenceStored("dynamicSizeEnabled")) {
            setDynamicSizeEnabled(false);
        }
        if (!isPreferenceStored("aspectRatio")) {
            setAspectRatio(1);
        }
        if (!isPreferenceStored("clusterSize")) {
            setClusterSize(.09f);
        }
        if (!isPreferenceStored("dontMergeEver")) {
            setDontMergeEver(true);
        }
        if (!isPreferenceStored("angleFollowsVelocity")) {
            setAngleFollowsVelocity(false);
        }
        if (!isPreferenceStored("useVelocity")) {
            setUseVelocity(true);
        }
        if (!isPreferenceStored("useNearestCluster")) {
            setUseNearestCluster(true);
        }
        if (!isPreferenceStored("pathsEnabled")) {
            setPathsEnabled(true);
        }
        if (!isPreferenceStored("pathLength")) {
            setPathLength(100);
        }
        if (!isPreferenceStored("velocityTauMs")) {
            setVelocityTauMs(30);
        }
        if (!isPreferenceStored("mixingFactor")) {
            setMixingFactor(.016f);
        }
        if (!isPreferenceStored("showClusterVelocity")) {
            setShowClusterVelocity(true);
        }
        if (!isPreferenceStored("velocityVectorScaling")) {
            setVelocityVectorScaling(.016f);
        }
        if (!isPreferenceStored("colorClustersDifferentlyEnabled")) {
            setColorClustersDifferentlyEnabled(true);
        }
        if(!isPreferenceStored("onlyFollowTrack")) setOnlyFollowTrack(true);
        if(!isPreferenceStored("relaxToTrackFactor")) setRelaxToTrackFactor(.1f);

        FilterChain filterChain = new FilterChain(chip);
        filterChain.add(new BackgroundActivityFilter(chip));
        nearbyTrackFilter = new NearbyTrackEventFilter(chip);
        filterChain.add(nearbyTrackFilter);

        setEnclosedFilterChain(filterChain);

    }

    @Override
    public Cluster createCluster() {
        return new TwoCarCluster();
    }

    @Override
    public Cluster createCluster(BasicEvent ev) {
        return new TwoCarCluster(ev);
    }

    @Override
    public Cluster createCluster(Cluster one, Cluster two) {
        return new TwoCarCluster(one, two);
    }

    @Override
    public Cluster createCluster(BasicEvent ev, OutputEventIterator itr) {
        return new TwoCarCluster(ev, itr);
    }

    @Override
    protected void updateClusterLocations(int t) {
        return; // don't move clusters between events to avoid clusters running off of track. TODO this may mess up the prediction step and perhaps we should predict *along* the track
    }

    /** The method that actually does the tracking.
     *
     * @param in the event packet.
     * @return a possibly filtered event packet passing only events contained in the tracked and visible Clusters, depending on filterEventsEnabled.
     */
    @Override
    synchronized protected EventPacket<? extends BasicEvent> track(EventPacket<BasicEvent> in) {
        boolean updatedClusterList = false;
        crashedCar=null; // before possible prune operation that could set this field to non-null
        out = getEnclosedFilterChain().filterPacket(in);

        // record cluster locations before packet is processed
        for (Cluster c : clusters) {
            c.getLastPacketLocation().setLocation(c.location);
        }

        // for each event, assign events to each cluster according probabalistically to the distance of the event from the cluster
        // if its too far from any cluster, make a new cluster if we can
        for (Object o : out) {
            BasicEvent ev=(BasicEvent)o;
            addEventToClustersOrSpawnNewCluster(ev);

            updatedClusterList = maybeCallUpdateObservers(in, ev.timestamp); // callback to update()
            if (isLogDataEnabled()) {
                logData(ev, in);
            }
        }
        // TODO update here again, relying on the fact that lastEventTimestamp was set by possible previous update according to
        // schedule; we have have double update of velocityPPT using same dt otherwise
        if (!updatedClusterList && in.getSize() > 0) { // make sure we have at least one event here to get a timestamp
            updateClusterList(in, in.getLastTimestamp()); // at laest once per packet update list
        }

        if (track == null) {
            log.warning("null track - perhaps deserialization failed or no track was saved?");
            return null;
        }

        // now accumulate votes for the car cluster, e.g. closest, oldest, nearest last choice as last; winner of voting is the CC cluster

        // iterate over clusters to find distance of each from track model.
        // Accumulate the results in a LowPassFilter for each cluster.
        TwoCarCluster closestAvgToTrack = null, oldest = null, sameAsLast = null, highestMass=null;
        float minDistFromTrack = Float.MAX_VALUE, maxAge = Integer.MIN_VALUE, minFromLast = Float.MAX_VALUE, maxMass=Float.MIN_VALUE;
        for (ClusterInterface c : clusters) {
            TwoCarCluster cc = (TwoCarCluster) c;
            cc.computerControlledCar = false; // mark all false
            cc.updateState();
            cc.updateSegmentInfo(in.getLastTimestamp());
            if (!cc.isVisible()) {
                continue;
            }
            float mass;
            if ((mass=cc.getMass()) > maxMass) {
                maxMass = mass;
                highestMass = cc;
            }
            if (cc.avgDistanceFromTrack < minDistFromTrack) {
                minDistFromTrack = cc.avgDistanceFromTrack;
                closestAvgToTrack = cc;
            }
            if (cc.getLifetime() > maxAge) {
                maxAge = cc.getLifetime();
                oldest = cc;
            }
            if (currentCarCluster != null) {
                if(cc==currentCarCluster){
                    sameAsLast=cc;
                }
//                float d;
//                if ((d = (float) cc.getLocation().distanceSq(currentCarCluster.getLocation())) < minFromLast) {
//                    minFromLast = d;
//                    sameAsLast = cc;
//                }
            }
        }
        int[] votes = new int[getNumClusters()];
        int idx = 0;
        for (ClusterInterface c : clusters) {
            TwoCarCluster cc = (TwoCarCluster) c;
            if (cc == sameAsLast) {
                votes[idx]+=4;
            }
            if (cc == closestAvgToTrack) {
                votes[idx]+=3;
            }
            if (cc == oldest) {
                votes[idx]+=1;
            }
            if(cc==highestMass){
                votes[idx]+=1;
            }
            idx++;
        }
        int maxVote = 0, maxbin = 0;
        for (int i = 0; i < votes.length; i++) {
            if (votes[i] > maxVote) {
                maxVote = votes[i];
                maxbin = i;
            }
        }
        if (clusters.size() > 0 && maxVote > 0) {
            computerControlledCarCluster = (TwoCarCluster) clusters.get(maxbin);
            computerControlledCarCluster.computerControlledCar = true;
//            if (compControlled != currentCarCluster) {
//                log.info("Switched CarClusterInterface from " + currentCarCluster + " \n to \n" + compControlled);
//            }
            currentCarCluster = (TwoCarCluster) computerControlledCarCluster;
        } else {
            currentCarCluster = null;
        }
        if (currentCarCluster != null) {
            computerControlledCarCluster = currentCarCluster;
        }
        return out;
    }

    /** Returns the putative car cluster.
     *
     * @return the car cluster, or null if there is no good cluster
     */
    @Override
    public TwoCarCluster findCarCluster() {
        return computerControlledCarCluster;
    }

    private void addEventToClustersOrSpawnNewCluster(BasicEvent ev) {
        class ClusterDistance {

            Cluster cluster;
            float distance;

            public ClusterDistance(Cluster cluster, float distance) {
                this.cluster = cluster;
                this.distance = distance;
            }
        }
        ArrayList<ClusterDistance> addList = new ArrayList();
        for (Cluster c : clusters) {
            float dist;
            if ((dist = c.distanceTo(ev)) < c.getRadius()) {
                addList.add(new ClusterDistance(c, dist));
            }
        }
        if (addList.size() > 0) {
            // we have a list of cluster that all contain the event.
            // We now partition the event randomly to the clusters.
            int r = random.nextInt(addList.size());
            addList.get(r).cluster.addEvent(ev);

        } else if (clusters.size() < getMaxNumClusters()) {
            // start a new cluster bu tonly if event in range of track
            if (track == null || track.findClosestIndex(new Point(ev.x, ev.y), track.getPointTolerance(), true) != -1) {
                Cluster newCluster = null;
                newCluster = createCluster(ev); // new Cluster(ev);
                clusters.add(newCluster);
            }
        }

    }

    @Override
    public synchronized void annotate(GLAutoDrawable drawable) {
//        super.annotate(drawable);
        for (Cluster c : clusters) {
            TwoCarCluster cc = (TwoCarCluster) c;
            if (isShowAllClusters() || cc.isVisible()) {
                cc.draw(drawable);
            }

        }

    }


    /**
     * @return the minSegmentsToBeCarCluster
     */
    public int getMinSegmentsToBeCarCluster() {
        return minSegmentsToBeCarCluster;
    }

    /**
     * @param minSegmentsToBeCarCluster the minSegmentsToBeCarCluster to set
     */
    public void setMinSegmentsToBeCarCluster(int minSegmentsToBeCarCluster) {
        this.minSegmentsToBeCarCluster = minSegmentsToBeCarCluster;
        putInt("minSegmentsToBeCarCluster", minSegmentsToBeCarCluster);
    }

    /**
     * @return the crashedCar
     */
    public TwoCarCluster getCrashedCar() {
        return crashedCar;
    }

    /** The cluster used for tracking cars. It extends the RectangularClusterTracker.Cluster with segment index and crashed status fields.
     * 
     */
    public class TwoCarCluster extends RectangularClusterTracker.Cluster implements CarClusterInterface {

        private final int SEGMENT_HISTORY_LENGTH = 150; // number of segments to keep track of in past
        private final int NUM_SEGMENTS_TO_BE_MARKED_RUNNING = 30;
        int segmentIdx = -1; // current segment
        int highestSegment=-1; // highwater mark for segment, for counting increases
        boolean crashed = false; // flag that we crashed
        boolean wasRunningSuccessfully = false; // marked true when car has been running successfully over segments for a while
        float lastDistanceFromTrack = 0, avgDistanceFromTrack = 0; // instantaneous and lowpassed distance from track model
        int birthSegmentIdx = -1; // which segment we were born on
        int numSegmentIncreases = 0; // how many times segment number increased
        int crashSegment = -1; // where we crashed
        int[] segmentHistory = new int[SEGMENT_HISTORY_LENGTH]; // ring buffer of segment history

        {
            Arrays.fill(segmentHistory, Integer.MIN_VALUE);
        }
        private float relaxToTrackFactor; // how fast the cluster relaxes onto the track model compared with following along it, when the onlyFollowTrack option is selected
        private int lastSegmentChangeTimestamp = 0;
        int segmentHistoryPointer = 0; // ring pointer, points to next location in ring buffer
        LowpassFilter distFilter = new LowpassFilter(distanceFromTrackMetricTauMs);
        boolean computerControlledCar = false;
        float segmentSpeedSPS = 0;  // segments per second traversed of the track
        private LowpassFilter segmentSpeedFilter = new LowpassFilter(segmentSpeedTauMs); // lowpass filter for segment speed

        public TwoCarCluster(BasicEvent ev, OutputEventIterator outItr) {
            super(ev, outItr);
        }

        public TwoCarCluster(Cluster one, Cluster two) {
            super(one, two);
        }

        public TwoCarCluster(BasicEvent ev) {
            super(ev);
        }

        public TwoCarCluster() {
            super();
        }

        /** Overrides updatePosition method to only allow movement along the track model, between spline points of the track.
         * Events that
         * @param m the mixing factor, 0 to not move, 1 to move cluster to location of event.
         * @param event
         */
        @Override
        protected void updatePosition(BasicEvent event, float m) {
            if (track == null || !onlyFollowTrack) {
                super.updatePosition(event, m);
                return;
            } else {
                 // move cluster, but only along the track
                Point2D.Float v = findClosestTrackSegmentVector();
                if (v == null) {
                    super.updatePosition(event, m);
                    return;
                }
                float vnorm = (float) v.distance(0, 0);
                if (vnorm < 1) {
                    log.warning("track segment vector is zero; track has idential track points. Edit the track to remove these identical points."); // warn about idential track points
                }
                float ex = event.x - location.x;
                float ey = event.y - location.y;
                float proj = m * (v.x * ex + v.y * ey) / vnorm;
                relaxToTrackFactor = relaxToTrackFactor * m;
                location.x += (proj * v.x) + relaxToTrackFactor * ex;
                location.y += (proj * v.y) + relaxToTrackFactor * ey;
            }
        }

        @Override
        public void draw(GLAutoDrawable drawable) {
            super.draw(drawable);
            final float BOX_LINE_WIDTH = 8f; // in chip
            GL gl = drawable.getGL();

            // set color and line width of cluster annotation
            if (computerControlledCar) {
                int x = (int) getLocation().x;
                int y = (int) getLocation().y;
                int sy = (int) radiusY; // sx sy are (half) size of rectangle
                int sx = (int) radiusX;
                gl.glColor3f(.8f, .8f, .8f);
                gl.glLineWidth(BOX_LINE_WIDTH);
                // draw cluster rectangle
                drawBox(gl, x, y, sx, sy, getAngle());
            } else {
                float[] rgb = getColor().getRGBColorComponents(null);
                gl.glColor3fv(rgb, 0);
            }

            gl.glRasterPos3f(location.x, location.y - 4, 0);
            chip.getCanvas().getGlut().glutBitmapString(
                    GLUT.BITMAP_HELVETICA_18,
                    String.format("dist=%.1f segSp=%.1f", distFilter.getValue(), segmentSpeedSPS));

        }

        private int updateSegmentInfo(int lastTimestamp) {
            if (track == null) {
                return -1;
            }
            int idx = track.findClosestIndex(location, 0, true);
            if (birthSegmentIdx == -1 && idx != -1) {
                birthSegmentIdx = idx;
            }
            if (idx != segmentIdx) {
                segmentHistory[segmentHistoryPointer] = idx;
                segmentHistoryPointer = (segmentHistoryPointer + 1) % SEGMENT_HISTORY_LENGTH; // LENGTH=2,pointer =0, 1, 0, 1, etc
            }
            // compute segment speed - this is speed *along* the track, which should not go to zero like the vector speed around turns
            if (lastSegmentChangeTimestamp == 0) {
                this.lastSegmentChangeTimestamp = lastTimestamp;
            } else {
                int dseg = idx - segmentIdx;
                if (dseg < -4) {
                    // very negative on wraparound, just memorize this segment value
//                    log.info("passed segment 0");
                } else if (dseg != 0) {
                    int dt = lastTimestamp - this.lastSegmentChangeTimestamp;
                    if (dt <= 0) {
                        dt = 1;
                    }
                    float instantaneousSegSp = (float) 1e6f * AEConstants.TICK_DEFAULT_US * dseg / dt;
//                    if(instantaneousSegSp<0){
//                        log.info("negative speed");
//                    }
                    segmentSpeedSPS = segmentSpeedFilter.filter(instantaneousSegSp, lastTimestamp);
                    lastSegmentChangeTimestamp = lastTimestamp;
                }
            }
            if (idx > highestSegment || idx<=2 && highestSegment>track.getNumPoints()-2) {
                numSegmentIncreases++;
                if (numSegmentIncreases> track.getNumPoints() / 4 ) {
                    wasRunningSuccessfully = true;
                }
                highestSegment=idx;
            }
            segmentIdx = idx;
            return idx;
        }

        private Point2D.Float findClosestTrackSegmentVector() {
            if (segmentIdx != -1) {
                return track.segmentVectors.get(segmentIdx);
            } else {
                return null;
            }
        }

        public String toString() {
            return "CarCluster segmentIdx=" + segmentIdx + " segmentSpeedSPS=" + segmentSpeedSPS + " crashed=" + crashed + "computerControlledCar="+computerControlledCar+" numSegmentIncreases=" + numSegmentIncreases + " wasRunningSuccessfully=" + wasRunningSuccessfully + " " + super.toString();
        }

        /**
         * @return the segmentIdx
         */
        @Override
        public int getSegmentIdx() {
            return segmentIdx;
        }

        /**
         * @param segmentIdx the segmentIdx to set
         */
        @Override
        public void setSegmentIdx(int segmentIdx) {
            this.segmentIdx = segmentIdx;
        }

        /**
         * @return the crashed
         */
        @Override
        public boolean isCrashed() {
            return crashed;
        }

        /**
         * @param crashed the crashed to set
         */
        @Override
        public void setCrashed(boolean crashed) {
            this.crashed = crashed;
        }

        @Override
        protected void prune() {
            super.prune();
            if (wasRunningSuccessfully && computerControlledCar) {
                determineIfcrashed();
            }
        }

        private void determineIfcrashed() {
            // called from prune in super, when cluster is about to be deleted.
            // first check to see if there a nearby cluster that might take over the car cluster role.
            // if there is a cluster then just leave crashed=false;
//            for(Cluster c:clusters){
//                if(c==this) continue;
//                if(distanceTo(c)<getRadius()) {
//                    crashed=false;
//                    return;
//                }
//            }
//         final float SPEED_FOR_CRASH = 10;
            if (!wasRunningSuccessfully) {
                crashed = false;
                return;
            }
            crashed = false;
            // looks over segment history to find last index of increasing sequence of track points - this is crash point
            // march up the history (first point is then the oldest) until we stop increasing (counting wraparound as an increase).
            // the last point of increase is the crash location.
            final int LOOKING_FOR_LAST = 0, COUNTING = 1, FOUND_CRASH = 2;
            final int SEGMENTS_BEFORE_CRASH = 15; // need this many upwards to see a crash

            int state = LOOKING_FOR_LAST;
            int crashSeg = -1;
            int count = 0;
            int lastValidSeg = Integer.MAX_VALUE;
            int startSeg = -1;

            StringBuilder sb = new StringBuilder("Pre-crash segment history, counting backwards in time = ");
            search:
            for (int i = 0; i < SEGMENT_HISTORY_LENGTH; i++) { // for all the recorded segments
                int segPointer = segmentHistoryPointer - i - 1;
                if (segPointer < 0) {
                    segPointer = SEGMENT_HISTORY_LENGTH + segPointer; // wrap back on ring buffer
                }
                int thisSeg = segmentHistory[segPointer];
                sb.append(Integer.toString(thisSeg)).append(" ");

                switch (state) {
                    case LOOKING_FOR_LAST:
                        if (thisSeg == Integer.MIN_VALUE) {
                            break search; // done with valid points that have had something put in them
                        }
                        if (thisSeg == -1) {
                            continue; // not initialized yet or in crash state
                        }
                        lastValidSeg = thisSeg;
                        startSeg = thisSeg;
                        count = 1;
                        state = COUNTING;
                        break;
                    case COUNTING:
                        if (thisSeg == Integer.MIN_VALUE) {
                            state = LOOKING_FOR_LAST;
                            count = 0;
                            break search;
                        } else if (thisSeg == -1) {
                            state = LOOKING_FOR_LAST;
                            count = 0;
                            continue;
                        } else if ((thisSeg <= lastValidSeg) // if this segment less that last one, accounting for jiggle around nearby points
                                || ( // OR wrap backwards:
                                thisSeg > track.getNumPoints() - SEGMENTS_BEFORE_CRASH // this seg at end of track
                                && lastValidSeg < SEGMENTS_BEFORE_CRASH // previuos was at start of track
                                )) { // normal decrement or wraparound
                            count++; // then count this
                            lastValidSeg = thisSeg;

                            if (count >= SEGMENTS_BEFORE_CRASH) {
                                state = FOUND_CRASH;
                                crashSeg = startSeg; // mark this one as crash point
                                break search;
                            }
                        } else { // either backwards or -1 (off track)
                            state = LOOKING_FOR_LAST;
                            count = 0;
                            startSeg = thisSeg;
                        }
                        break;
                    case FOUND_CRASH:
                        break search;
                    default:
                        throw new Error("invalid state=" + state);
                }
            }
            // TODO need check here to  make sure there is not some other cluster that took over for the crashed car cluster. Also need an "other car" cluster.
            switch (state) {
                case LOOKING_FOR_LAST:
                    sb.append("could't find last crash segment while looking for last segment, using lastValidSeg=" + lastValidSeg);
                    crashed = true;
                    crashSegment = lastValidSeg;
                    break;
                case COUNTING:
                    sb.append("was still counting decreasing segments but could't find last crash segment, using startSeg=" + startSeg);
                    crashed = true;
                    crashSegment = startSeg;
                    break;
                case FOUND_CRASH:
                    sb.append("\ndetermined crash was at segment " + crashSeg);
                    crashSegment = crashSeg;
                    crashed = true;
                    computerControlledCarCluster=this;
                    break;
                default:
                   throw new RuntimeException("invalid state "+state+"reached in determineIfcrashed() - this should not happen");

            }
            if(crashed && computerControlledCar){
                crashedCar=this;
            }
            sb.append(" for ").append(this.toString());
            log.info(sb.toString());
        } // determineCrashLocation

        private void updateState() {
            lastDistanceFromTrack = track.findDistanceToTrack(getLocation());
            if(Float.isNaN(avgDistanceFromTrack)) distFilter.reset();
            avgDistanceFromTrack = distFilter.filter(lastDistanceFromTrack, getLastEventTimestamp());
//            determineIfcrashed();

        }
    } // TwoCarCluster

    /**
     * @param track the track to set
     */
    public final void setTrack(SlotcarTrack track) {
        SlotcarTrack old = this.track;
        this.track = track;
        nearbyTrackFilter.setTrack(track);
        if (this.track != old) {
            if (this.track != null) {
                this.track.setPointTolerance(maxDistanceFromTrackPoint);
            }
            log.info("new track with " + track.getNumPoints() + " points");
            getSupport().firePropertyChange("track", old, this.track);
        }
    }

    /**
     * @return the onlyFollowTrack
     */
    public boolean isOnlyFollowTrack() {
        return onlyFollowTrack;
    }

    /**
     * @param onlyFollowTrack the onlyFollowTrack to set
     */
    public void setOnlyFollowTrack(boolean onlyFollowTrack) {
        this.onlyFollowTrack = onlyFollowTrack;
        putBoolean("onlyFollowTrack", onlyFollowTrack);
    }

    /**
     * @return the relaxToTrackFactor
     */
    public float getRelaxToTrackFactor() {
        return relaxToTrackFactor;
    }

    /**
     * @param relaxToTrackFactor the relaxToTrackFactor to set
     */
    public void setRelaxToTrackFactor(float relaxToTrackFactor) {
        if (relaxToTrackFactor > 1) {
            relaxToTrackFactor = 1;
        } else if (relaxToTrackFactor < 0) {
            relaxToTrackFactor = 0;
        }
        this.relaxToTrackFactor = relaxToTrackFactor;
        putFloat("relaxToTrackFactor", relaxToTrackFactor);
    }

    /**
     * @return the distanceFromTrackMetricTauMs
     */
    public float getDistanceFromTrackMetricTauMs() {
        return distanceFromTrackMetricTauMs;
    }

    /**
     * @param distanceFromTrackMetricTauMs the distanceFromTrackMetricTauMs to set
     */
    public void setDistanceFromTrackMetricTauMs(float distanceFromTrackMetricTauMs) {
        this.distanceFromTrackMetricTauMs = distanceFromTrackMetricTauMs;
        putFloat("distanceFromTrackMetricTauMs", distanceFromTrackMetricTauMs);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName() == SlotcarTrack.EVENT_TRACK_CHANGED) {
            try {
                track = (SlotcarTrack) evt.getNewValue();
                setTrack(track);
            } catch (Exception e) {
                log.warning("caught " + e + " when handling property change");
            }
        }
    }

    /**
     * @return the maxDistanceFromTrackPoint
     */
    public float getMaxDistanceFromTrackPoint() {
        return maxDistanceFromTrackPoint;
    }

    /**
     * @param maxDistanceFromTrackPoint the maxDistanceFromTrackPoint to set
     */
    public void setMaxDistanceFromTrackPoint(float maxDistanceFromTrackPoint) {
        float old = this.maxDistanceFromTrackPoint;
        // Define tolerance for track model
        if (track != null) {
            this.maxDistanceFromTrackPoint = maxDistanceFromTrackPoint;
            track.setPointTolerance(maxDistanceFromTrackPoint);
        } else {
            log.warning("cannot set point tolerance on track yet - track is null");
        }
        putFloat("maxDistanceFromTrackPoint", maxDistanceFromTrackPoint);
        getSupport().firePropertyChange("maxDistanceFromTrackPoint", old, maxDistanceFromTrackPoint);

    }

    /**
     * @return the segmentSpeedTauMs
     */
    public float getSegmentSpeedTauMs() {
        return segmentSpeedTauMs;
    }

    /**
     * @param segmentSpeedTauMs the segmentSpeedTauMs to set
     */
    public void setSegmentSpeedTauMs(float segmentSpeedTauMs) {
        this.segmentSpeedTauMs = segmentSpeedTauMs;
        putFloat("segmentSpeedTauMs", segmentSpeedTauMs);
    }
}
