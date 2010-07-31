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
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
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

    private boolean onlyFollowTrack = getBoolean("onlyFollowTrack", true);
    private float relaxToTrackFactor = getFloat("relaxToTrackFactor", 0.05f);
    private float distanceFromTrackMetricTauMs = getFloat("distanceFromTrackMetricTauMs", 200);
    private int minSegmentsToBeCarCluster = getInt("minSegmentsToBeCarCluster", 20);
    private SlotcarTrack track;
    private SlotcarState carState;
    private TwoCarCluster currentCarCluster = null;
    private NearbyTrackEventFilter nearbyTrackFilter = null;
    private float maxDistanceFromTrackPoint = getFloat("maxDistanceFromTrackPoint", 15); // pixels - need to set in track model

    public TwoCarTracker(AEChip chip) {
        super(chip);
        setPropertyTooltip("onlyFollowTrack", "If set, clusters will only follow the track. If false, clusters can follow car off the track.");
        setPropertyTooltip("relaxToTrackFactor", "Tracking will normally only parallel the track. This factor control how much the cluster converges onto the track, i.e., the allowed normal motion as fraction of the parallel motion.");
        setPropertyTooltip("distanceFromTrackMetricTauMs", "Each car cluster distance from track model is lowpass filtered with this time constant in ms; the closest one is chosen as the computer controlled car");
        setPropertyTooltip("minSegmentsToBeCarCluster", "a CarCluster needs to pass at least this many segments to be marked as the car cluster");
        setPropertyTooltip("maxDistanceFromTrackPoint", "Maximum allowed distance in pixels from track spline point to find nearest spline point; if currentTrackPos=-1 increase maxDistanceFromTrackPoint");
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
            setAspectRatio(.78f);
        }
        if (!isPreferenceStored("clusterSize")) {
            setClusterSize(.07f);
        }
        if (!isPreferenceStored("dontMergeEver")) {
            setDontMergeEver(true);
        }
        if (!isPreferenceStored("angleFollowsVelocity")) {
            setAngleFollowsVelocity(true);
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
            setPathLength(50);
        }
        if (!isPreferenceStored("velocityTauMs")) {
            setVelocityTauMs(15);
        }
        if (!isPreferenceStored("showClusterVelocity")) {
            setShowClusterVelocity(true);
        }
        if (!isPreferenceStored("colorClustersDifferentlyEnabled")) {
            setColorClustersDifferentlyEnabled(true);
        }

        FilterChain filterChain = new FilterChain(chip);
        filterChain.add(new BackgroundActivityFilter(chip));
        nearbyTrackFilter = new NearbyTrackEventFilter(chip);
        filterChain.add(nearbyTrackFilter);

        setEnclosedFilterChain(filterChain);

    }

    public TwoCarTracker(AEChip chip, SlotcarTrack track, SlotcarState carState) {
        this(chip);
        setTrack(track);
        this.carState = carState;
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
        in = getEnclosedFilterChain().filterPacket(in);

        // record cluster locations before packet is processed
        for (Cluster c : clusters) {
            c.getLastPacketLocation().setLocation(c.location);
        }

        // for each event, assign events to each cluster according probabalistically to the distance of the event from the cluster
        // if its too far from any cluster, make a new cluster if we can
        for (BasicEvent ev : in) {
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

        return in;
    }

    /** Returns the putative car cluster.
     *
     * @return the car cluster, or null if there is no good cluster
     */
    @Override
    public TwoCarCluster findCarCluster() {
        if (track == null) {
            log.warning("null track - perhaps deserialization failed or no track was saved?");
            return null;
        }
        // defines the criteria for a visible car cluster
        // very simple now
        float minDist = Float.MAX_VALUE;
        TwoCarCluster ret = null;

        // iterate over clusters to find distance of each from track model.
        // Accumulate the results in a LowPassFilter for each cluster.

        for (ClusterInterface c : clusters) {
            TwoCarCluster cc = (TwoCarCluster) c;
            if (!cc.isVisible()) {
                cc.computerControlledCar = false;
                continue;
            }
            if (cc != null) {
                cc.lastDistanceFromTrack = track.findDistanceToTrack(cc.getLocation());
                cc.computerControlledCar = false; // mark all false
                if (Float.isNaN(cc.lastDistanceFromTrack)) {
//                    System.out.println(cc + " is crashed");
                    if (cc.crashed == false) {
                        cc.determineCrashLocation();
                    }
                    cc.crashed = true;
                    cc.distFilter.reset();
                    cc.avgDistanceFromTrack = Float.NaN;
                } else {
                    cc.crashed = false;
                    cc.crashSegment = -1;
                    cc.avgDistanceFromTrack = cc.distFilter.filter(cc.lastDistanceFromTrack, cc.getLastEventTimestamp());
                    if (cc.avgDistanceFromTrack < minDist && cc.numSegmentIncreases > minSegmentsToBeCarCluster) {
                        minDist = cc.avgDistanceFromTrack;
                        ret = cc;
                    }
                }
            }
        }

        if (ret != null) {
            ret.computerControlledCar = true; // closest avg to track is computer controlled car
        }
        if (ret != currentCarCluster) {
            log.info("chose new CarCluster " + ret);
        }
        currentCarCluster = (TwoCarCluster) ret;
        return (TwoCarCluster) ret;
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

    /** The cluster used for tracking cars. It extends the RectangularClusterTracker.Cluster with segment index and crashed status fields.
     * 
     */
    public class TwoCarCluster extends RectangularClusterTracker.Cluster implements CarCluster {

        private float factor;
        private int segmentIdx = -1; // current segment
        private boolean crashed = false; // flag that we crashed
        float lastDistanceFromTrack = 0, avgDistanceFromTrack = 0; // instantaneous and lowpassed distance from track model
        int birthSegmentIdx = -1; // which segment we were born on
        int numSegmentIncreases = 0; // how many times segment number increased
        int crashSegment = -1; // where we crashed
        final int SEGMENT_HISTORY_LENGTH = 50; // number of segments to keep track of in past
        int[] segmentHistory = new int[SEGMENT_HISTORY_LENGTH]; // ring buffer of segment history

        {
            Arrays.fill(segmentHistory, Integer.MIN_VALUE);
        }
        int segmentHistoryPointer = 0; // ring pointer, points to next location in ring buffer
        LowpassFilter distFilter = new LowpassFilter();

        {
            distFilter.setTauMs(distanceFromTrackMetricTauMs);
        }
        boolean computerControlledCar = false;

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
            if (track == null) {
                super.updatePosition(event, m);
                return;
            } else {
                int idx = updateSegmentInfo();
                // move cluster, but only along the track
                Point2D.Float v = findClosestTrackSegmentVector();
                if (v == null) {
                    if (!onlyFollowTrack) {
                        super.updatePosition(event, m);
                    }
                    return;
                }
                float vnorm = (float) v.distance(0, 0);
                if (vnorm < 1) {
                    log.warning("track segment vector is zero; track has idential track points. Edit the track to remove these identical points. carState=" + carState); // warn about idential track points
                }
                float ex = event.x - location.x;
                float ey = event.y - location.y;
                float proj = m * (v.x * ex + v.y * ey) / vnorm;
                factor = relaxToTrackFactor * m;
                location.x += (proj * v.x) + factor * ex;
                location.y += (proj * v.y) + factor * ey;
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
                    String.format("dist=%.1f ", distFilter.getValue()));

        }

        private int updateSegmentInfo() {
            if (track == null) {
                return -1;
            }
            int idx = track.findClosestIndex(location, track.getPointTolerance(), true);
            if (birthSegmentIdx == -1 && idx != -1) {
                birthSegmentIdx = idx;
            }
            if (idx != segmentIdx) {
                segmentHistory[segmentHistoryPointer] = idx;
                segmentHistoryPointer = (segmentHistoryPointer + 1) % SEGMENT_HISTORY_LENGTH; // LENGTH=2,pointer =0, 1, 0, 1, etc
            }
            if (idx > segmentIdx) {
                numSegmentIncreases++;
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
            return super.toString() + " segmentIdx=" + segmentIdx;
        }

        /**
         * @return the segmentIdx
         */
        public int getSegmentIdx() {
            return segmentIdx;
        }

        /**
         * @param segmentIdx the segmentIdx to set
         */
        public void setSegmentIdx(int segmentIdx) {
            this.segmentIdx = segmentIdx;
        }

        /**
         * @return the crashed
         */
        public boolean isCrashed() {
            return crashed;
        }

        /**
         * @param crashed the crashed to set
         */
        public void setCrashed(boolean crashed) {
            this.crashed = crashed;
        }

        private void determineCrashLocation() {
            // looks over segment history to find last index of increasing sequence of track points - this is crash point
            // march up the history (first point is then the oldest) until we stop increasing (counting wraparound as an increase).
            // the last point of increase is the crash location.
            final int LOOKING_FOR_LAST = 0, COUNTING = 1, FOUND_CRASH = 2;
            final int SEGMENTS_BEFORE_CRASH = 5; // need this many upwards to see a crash

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
                        }else if (thisSeg == -1) {
                            state = LOOKING_FOR_LAST;
                            count = 0;
                            continue;
                        } else if ((thisSeg <= lastValidSeg+1) // if this segment less that last one, accounting for jiggle around nearby points
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
            switch (state) {
                case LOOKING_FOR_LAST:
                    sb.append("could't find last crash segment, using lastValidSeg=" + lastValidSeg);
                    crashSegment = lastValidSeg;
                    break;
                case COUNTING:
                    sb.append("could't find last crash segment, using startSeg=" + startSeg);
                    crashSegment = startSeg;
                    break;
                case FOUND_CRASH:
                    sb.append("\ndetermined crash was at segment " + crashSeg);
                    crashSegment = crashSeg;
                    break;
                default:
                    sb.append("\ncoulnt' determine crash segment; invalid state=" + state);

            }
            log.info(sb.toString());
        } // determineCrashLocation
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
     * @param carState the carState to set
     */
    public void setCarState(SlotcarState carState) {
        this.carState = carState;
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
}
