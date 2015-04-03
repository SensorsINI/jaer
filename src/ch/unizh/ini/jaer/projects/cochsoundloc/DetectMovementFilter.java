/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.cochsoundloc;

import com.jogamp.opengl.GLAutoDrawable;
import java.awt.Graphics2D;
import java.util.List;


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
    public PanTilt panTilt = null;
    private boolean invertHorizontalOutput = getPrefs().getBoolean("ITDFilter.invertHorizontalOutput", false);

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
        if (connectToPanTiltThread && (panTilt.isMoving() || panTilt.isWasMoving())) {
            this.wasMoving = true;
            return in;
        }
        if (this.wasMoving == true) {
            this.wasMoving = false;
            clusterTracker.resetFilter();
            //log.info("clear clusters!");
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
                    float panOutput = (location.x-64f)/64f;
                    if (invertHorizontalOutput)
                    {
                        panOutput=-panOutput;
                    }
                    filterOutput.setPanOffset(panOutput);
                    filterOutput.setTiltOffset((location.y-64f)/64f);
                    filterOutput.setConfidence(confidence);
                    panTilt.offerBlockingQ(filterOutput);
                }
            }
        }
        return in;
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
        getSupport().firePropertyChange("confidenceThreshold", this.confidenceThreshold, confidenceThreshold);
        this.confidenceThreshold = confidenceThreshold;
    }

    public void doConnectToPanTiltThread(){
        panTilt = PanTilt.findExistingPanTiltThread(chip.getAeViewer());
        if (panTilt==null) {
            panTilt = new PanTilt();
            panTilt.initPanTilt();
        }
        this.connectToPanTiltThread = true;
    }


    public boolean isInvertHorizontalOutput() {
        return this.invertHorizontalOutput;
    }

    public void setInvertHorizontalOutput(boolean invertHorizontalOutput) {
        getPrefs().putBoolean("ITDFilter.invertHorizontalOutput", invertHorizontalOutput);
        getSupport().firePropertyChange("invertHorizontalOutput", this.invertHorizontalOutput, invertHorizontalOutput);
        this.invertHorizontalOutput = invertHorizontalOutput;
    }
}
