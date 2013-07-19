/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.sensoryfusion;

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
 * 
 * 
 * @author braendch
 */
public class SoundLocalization extends EventFilter2D implements FrameAnnotater {

    private RectangularClusterTracker clusterTracker;
    private int massThreshold = getPrefs().getInt("SoundLocalization.massThreshold", 100);
    private PidPanTiltControllerUSB panTiltControl = null;

    public SoundLocalization(AEChip chip) {
        super(chip);
        setPropertyTooltip("massThreshold", "Clusters with mass below this threshold are neglected");

        clusterTracker = new RectangularClusterTracker(chip);
        this.setEnclosedFilter(clusterTracker);
        clusterTracker.setEnclosed(true, this);
        connectToPanTilt();
        initFilter();
    }

    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if (!filterEnabled) {
            return in;
        }

        in = clusterTracker.filterPacket(in);
        List<RectangularClusterTracker.Cluster> clusterList = clusterTracker.getClusters();
        float maxMass = 0;
        Cluster clusterToTrack = null;
        for (int i = 0; i < clusterList.size(); i++) {
            Cluster clst = clusterList.get(i);
            if (massThreshold < clst.getMass() && maxMass < clst.getMass()) {
                maxMass = clst.getMass();
                clusterToTrack = clst;
            }
        }
        if(clusterToTrack != null){
            aimCluster(clusterToTrack);
        }
        return in;
    }

    public void connectToPanTilt(){
        panTiltControl = new PidPanTiltControllerUSB(chip);
    }

    public void aimCluster(Cluster target){

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

    public int getMassThreshold() {
        return this.massThreshold;
    }

    public void setMassThreshold(int massThreshold) {
        getPrefs().putInt("ITDFilter.massThreshold", massThreshold);
        getSupport().firePropertyChange("massThreshold", this.massThreshold, massThreshold);
        this.massThreshold = massThreshold;
    }

}
