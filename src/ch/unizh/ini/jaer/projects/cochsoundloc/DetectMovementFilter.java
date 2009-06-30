/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.cochsoundloc;

import java.awt.Graphics2D;
import java.util.List;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker.Cluster;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * This is a filter for the retina with enclosed cluster tracker. It can send the tracking information to the panTiltThread
 * 
 * @author Holger
 */
public class DetectMovementFilter extends EventFilter2D implements FrameAnnotater {

    private RectangularClusterTracker clusterTracker;
    private boolean connectToPanTiltThread = false;
    private int confidenceThreshold = getPrefs().getInt("ITDFilter.confidenceThreshold", 100);
    private boolean wasMoving = false;

    public DetectMovementFilter(AEChip chip) {
        super(chip);
        setPropertyTooltip("confidenceThreshold", "Clusters with confidence below this threshold are neglected");

        clusterTracker = new RectangularClusterTracker(chip);
        this.setEnclosedFilter(clusterTracker);
        clusterTracker.setEnclosed(true, this);
        initFilter();
    }

    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!filterEnabled) {
            return in;
        }
        if (connectToPanTiltThread && (PanTilt.isMoving() || PanTilt.isWasMoving())) {
            this.wasMoving = true;
            return in;
        }
        if (this.wasMoving == true) {
            this.wasMoving = false;
            clusterTracker.resetFilter();
            log.info("clear clusters!");
        }
        in = clusterTracker.filterPacket(in);
        List<RectangularClusterTracker.Cluster> clusterList = clusterTracker.getClusters();
        for (int i = 0; i < clusterList.size(); i++) {
            Cluster clst = clusterList.get(i);
            int confidence = clst.getNumEvents();
            if (confidence > this.confidenceThreshold) {
                java.awt.geom.Point2D.Float location = clst.getLocation();
                //log.info("ClusterNumEvents: " + clst.getNumEvents() + " AvgEventRate: "+clst.getAvgEventRate());
                //log.info("ClusterLocation: ( " + location.x + " , " + location.y + " )");
                if (connectToPanTiltThread == true) {
                    CommObjForPanTilt filterOutput = new CommObjForPanTilt();
                    filterOutput.setFromRetina(true);
                    filterOutput.setPanOffset(location.x-64);
                    filterOutput.setTiltOffset(location.y-64);
                    filterOutput.setConfidence(confidence);
                    PanTilt.offerBlockingQ(filterOutput);
                }
            }
        }
        return in;
    }

    @Override
    public Object getFilterState() {
        return null;
    }

    @Override
    public void resetFilter() {
        initFilter();
        clusterTracker.resetFilter();
    }

    @Override
    public void initFilter() {
        clusterTracker.setFilterEnabled(false);
        clusterTracker.setDynamicSizeEnabled(true);
        clusterTracker.setDynamicAspectRatioEnabled(true);
        clusterTracker.setMaxNumClusters(1);
        clusterTracker.setFilterEnabled(true);
    }

    public void annotate(float[][][] frame) {
        //clusterTracker.annotate(frame);
    }

    public void annotate(Graphics2D g) {
        //clusterTracker.annotate(g);
    }

    public void annotate(GLAutoDrawable drawable) {
        //clusterTracker.annotate(drawable);
    }

    public int getConfidenceThreshold() {
        return this.confidenceThreshold;
    }

    public void setConfidenceThreshold(int confidenceThreshold) {
        getPrefs().putInt("ITDFilter.confidenceThreshold", confidenceThreshold);
        support.firePropertyChange("confidenceThreshold", this.confidenceThreshold, confidenceThreshold);
        this.confidenceThreshold = confidenceThreshold;
    }

    public void doConnectToPanTiltThread(){
        PanTilt.initPanTilt();
        this.connectToPanTiltThread = true;
    }
}
