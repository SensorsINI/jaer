/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.virtualslotcar;

import com.sun.opengl.util.GLUT;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
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

    private boolean onlyFollowTrack=getBoolean("onlyFollowTrack", true);
    private float relaxToTrackFactor=getFloat("relaxToTrackFactor",0.05f);
    private float distanceFromTrackMetricTauMs=getFloat("distanceFromTrackMetricTauMs",200);
    private SlotcarTrack track;
    private SlotcarState carState;
    private TwoCarCluster currentCarCluster=null;
    private HashMap<TwoCarCluster,LowpassFilter> distanceMap=new HashMap();

    public TwoCarTracker(AEChip chip) {
        super(chip);
        setPropertyTooltip("onlyFollowTrack", "If set, clusters will only follow the track. If false, clusters can follow car off the track.");
        setPropertyTooltip("relaxToTrackFactor", "Tracking will normally only parallel the track. This factor control how much the cluster converges onto the track, i.e., the allowed normal motion as fraction of the parallel motion.");
        setPropertyTooltip("distanceFromTrackMetricTauMs", "Each car cluster distance from track model is lowpass filtered with this time constant in ms; the closest one is chosen as the computer controlled car");
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
        if(!isPreferenceStored("colorClustersDifferentlyEnabled")){
            setColorClustersDifferentlyEnabled(true);
        }

        FilterChain filterChain=new FilterChain(chip);
        filterChain.add(new BackgroundActivityFilter(chip));
       setEnclosedFilterChain(filterChain);

    }

    public TwoCarTracker(AEChip chip, SlotcarTrack track, SlotcarState carState) {
        this(chip);
        this.track = track;
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
        if (!updatedClusterList && in.getSize()>0) { // make sure we have at least one event here to get a timestamp
            updateClusterList(in, in.getLastTimestamp()); // at laest once per packet update list
        }
        
        // purge avg distance map
        ArrayList<TwoCarCluster> purgeList=new ArrayList();
        for(TwoCarCluster c:distanceMap.keySet()){
            if(!clusters.contains(c)) purgeList.add(c);
        }
        for(TwoCarCluster c:purgeList)  distanceMap.remove(c);
        
            return in;
    }

    /** Returns the putative car cluster.
     *
     * @return the car cluster, or null if there is no good cluster
     */
    public TwoCarCluster getCarCluster() {
        // defines the criteria for a visible car cluster
        // very simple now
        float minDist = Float.MAX_VALUE;
        ClusterInterface ret = null;

        // iterate over clusters to find distance of each from track model.
        // Accumulate the results in a LowPassFilter for each cluster.

        for (ClusterInterface cc : clusters) {
            if(!cc.isVisible()) continue;
            TwoCarCluster c=(TwoCarCluster)cc;
            if (c != null && c.isVisible()) {
                float dist= track.findDistanceToTrack(c.getLocation());
                if(!distanceMap.containsKey(c)){
                    LowpassFilter f;
                    distanceMap.put(c, f=new LowpassFilter());
                    f.setTauMs(distanceFromTrackMetricTauMs);
                    f.setInternalValue(dist);
                }else{
                    LowpassFilter f=distanceMap.get(c);
                    float lpdist=f.filter(dist, c.getLastEventTimestamp());
                     if(lpdist<minDist){
                        minDist=lpdist;
                        ret=c;
                    }
                }
            }
        }
        currentCarCluster=(TwoCarCluster) ret;
        return (TwoCarCluster)ret;
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
            int r=random.nextInt(addList.size());
            addList.get(r).cluster.addEvent(ev);
            
        } else if (clusters.size() < getMaxNumClusters()) { // start a new cluster
            Cluster newCluster = null;
            newCluster = createCluster(ev); // new Cluster(ev);
            clusters.add(newCluster);
        }

    }

    @Override
    public synchronized void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable);
        if(currentCarCluster!=null){
            currentCarCluster.draw(drawable);
        }

    }



    /** The cluster used for tracking cars. It extends the RectangularClusterTracker.Cluster with segment index and crashed status fields.
     * 
     */
    public class TwoCarCluster extends RectangularClusterTracker.Cluster implements CarCluster {
        private float factor;
        private int segmentIdx=-1;
        private boolean crashed = false;

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
                int idx=findNearestTrackIndex();
                // move cluster, but only along the track
                Point2D.Float v = findClosestTrackSegmentVector();
                if (v == null) {
                    if(!onlyFollowTrack) super.updatePosition(event, m);
                    return;
                }
                float vnorm = (float) v.distance(0, 0);
                if(vnorm<1){
                    log.warning("track segment vector is zero; track has idential track points. Edit the track to remove these identical points. carState="+carState); // warn about idential track points
                }
                float ex = event.x - location.x;
                float ey = event.y - location.y;
                float proj = m * (v.x * ex + v.y * ey) / vnorm;
                factor = relaxToTrackFactor * m;
                location.x += (proj * v.x)+factor*ex;
                location.y += (proj * v.y)+factor*ey;
            }
        }

        @Override
        public void draw(GLAutoDrawable drawable) {
            super.draw(drawable);
            if (this == currentCarCluster) {
                final float BOX_LINE_WIDTH = 4f; // in chip
                GL gl = drawable.getGL();
                int x = (int) getLocation().x;
                int y = (int) getLocation().y;


                int sy = (int) radiusY; // sx sy are (half) size of rectangle
                int sx = (int) radiusX;

                // set color and line width of cluster annotation
                gl.glColor3f(.8f, .8f, .8f);
                gl.glLineWidth(BOX_LINE_WIDTH);

                // draw cluster rectangle
                drawBox(gl, x, y, sx, sy, getAngle());
                chip.getCanvas().getGlut().glutBitmapString(
                        GLUT.BITMAP_HELVETICA_18,
                        String.format("dist=%.1f ", distanceMap.get(currentCarCluster).getValue()));

            }
        }


        private int findNearestTrackIndex(){
            if(track==null) return -1;
              int idx = track.findClosest(location, track.getPointTolerance(), true, segmentIdx);
              segmentIdx=idx;
              return idx;
        }

        private Point2D.Float findClosestTrackSegmentVector() {
            if (segmentIdx != -1) {
                return track.segmentVectors.get(segmentIdx);
            } else {
                return null;
            }
        }

        public String toString(){
            return super.toString()+" segmentIdx="+segmentIdx;
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

    } // TwoCarCluster

    /**
     * @param track the track to set
     */
    public void setTrack(SlotcarTrack track) {
        this.track = track;
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
        if(relaxToTrackFactor>1) relaxToTrackFactor=1; else if(relaxToTrackFactor<0) relaxToTrackFactor=0;
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
        if (evt.getPropertyName() == TrackdefineFilter.EVENT_TRACK_CHANGED) {
            try {
                track = (SlotcarTrack) evt.getNewValue();
                setTrack(track);
                log.info("new track with "+track.getNumPoints()+" points");
            } catch (Exception e) {
                log.warning("caught " + e + " when handling property change");
            }
        }
    }



}
