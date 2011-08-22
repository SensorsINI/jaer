/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.stereo;

import ch.unizh.ini.jaer.projects.gesture.blurringFilter.BlurringFilter2DTracker;
import java.awt.Rectangle;
import java.util.LinkedList;
import net.sf.jaer.chip.AEChip;


/**
 *
 * @author Jun Haeng Lee
 */
public class BlurringFilterStereoTracker extends BlurringFilter2DTracker{
    /**
     * the threshold of LIF neuron of Blurring filter varies adaptively based on the disparity
     */
    private boolean enableAutoThreshold = getPrefs().getBoolean("BlurringFilterStereoTracker.enableAutoThreshold", true);


    /**
     * reference disparity to define the reference threshold
     */
    private int autoThresholdReferenceDisparity = getPrefs().getInt("BlurringFilterStereoTracker.autoThresholdReferenceDisparity", 0);
    
    /**
     * autoThresholdReferenceThreshold
     */
    private int autoThresholdReferenceThreshold = getPrefs().getInt("BlurringFilterStereoTracker.autoThresholdReferenceThreshold", 60);

    /**
     * threshold/disparity
     */
    private float autoThresholdSlope = getPrefs().getFloat("BlurringFilterStereoTracker.autoThresholdSlope", 1.0f);

    /**
     * if true, limits the range of disparity chagne in the vergence filter when there are live clusters.
     */
    private boolean enableVergenceAttention = getPrefs().getBoolean("BlurringFilterStereoTracker.enableVergenceAttention", false);

    /**
     * maximum disparity change in vergence filter when enableVergenceAttention is true
     */
    private int allowedMaxDisparityChange = getPrefs().getInt("BlurringFilterStereoTracker.allowedMaxDisparityChange", 20);

    /**
     * vergence attention will be released when this amount of time in msec is passed since the last cluster was dead.
     */
    private int vergenceAttentionLifetimeMs = getPrefs().getInt("BlurringFilterStereoTracker.vergenceAttentionLifetimeMs", 500);


    /**
     * constructor
     * @param chip
     */
    public BlurringFilterStereoTracker(AEChip chip) {
        super(chip);

        String stereo = "Stereo";
        setPropertyTooltip(stereo, "enableAutoThreshold", "if true, the threshold of LIF neuron of Blurring filter varies adaptively based on the disparity.");
        setPropertyTooltip(stereo, "autoThresholdReferenceDisparity", "reference disparity to define the reference threshold.");
        setPropertyTooltip(stereo, "autoThresholdReferenceThreshold", "Threshold of LIF neuron of Blurring filter at the reference disparity.");
        setPropertyTooltip(stereo, "autoThresholdSlope", "threshold/disparity.");
        setPropertyTooltip(stereo, "enableVergenceAttention", "if true, limits the range of disparity chagne in the vergence filter when there are live clusters.");
        setPropertyTooltip(stereo, "allowedMaxDisparityChange", "maximum disparity change in vergence filter when enableVergenceAttention is true.");
        setPropertyTooltip(stereo, "vergenceAttentionLifetimeMs", "vergence attention will be released when this amount of time in msec is passed since the last cluster was dead.");
    }

    /**
     * overides this method to replace BluringFilter2D with BluringFilterStereo
     */
    @Override
    protected void filterChainSetting() {
        super.bfilter = new BlurringFilterStereo(chip);
        super.bfilter.addObserver(this);
        setEnclosedFilter(super.bfilter);
    }

    /**
     * core of update process
     * @param msg
     */
    @Override
    public void updateCore(UpdateMessage msg) {
        super.updateCore(msg);

        DisparityUpdater du = ((BlurringFilterStereo) bfilter).disparityUpdater;

        // adaptive threshold
        if(enableAutoThreshold){
            float disparity = du.getVergenceDisparity();
            float thresholdAdptive = autoThresholdReferenceThreshold + ((disparity - autoThresholdReferenceDisparity)*autoThresholdSlope);
            if(thresholdAdptive < 1.0f)
                thresholdAdptive = 1.0f;

            super.bfilter.setMPThreshold((int) thresholdAdptive);
        }

        // checks all clusters for SV management
        for(Cluster cl:clusters){
            Rectangle refRect = cl.getClusterArea();
            int median = refRect.x + refRect.width/2;
            int radius = (int) calRadius(cl).radius;

            if(!du.containsClusterSV(cl.getClusterNumber())){ // if the cluster is not registered yet, registers it
                Rectangle area = new Rectangle(median - radius , refRect.y, 2*radius, refRect.height);
                du.addClusterSV(cl.getClusterNumber(), area, du.getVergenceDisparity(), du.lastTimestamp);
            } else { // if it's registered already, updates area
                // offset is used to compensate the latency caused by clustering algorithm
                float offset = 0.3f*radius*(float) Math.tanh((double) cl.getVelocityPPS().x/100)/2;

                Rectangle area = new Rectangle(median - radius + (int)offset , refRect.y, 2*radius, refRect.height);

                du.updateClusterSVArea(cl.getClusterNumber(), area);
            }
        }

        // cross-checks all SVs
        LinkedList<StereoDisparity> sdListDie = new LinkedList<StereoDisparity>();
        for(StereoDisparity sd:du.clusterDisparity.values()){
            Cluster matched = null;
            for(Cluster cl:clusters){
                if(cl.getClusterNumber() == sd.getId()){
                    matched = cl;
                    break;
                }
            }

            if(matched == null)
                sdListDie.add(sd);
        }

        // removes SVs for dead clusters
        for(StereoDisparity sd:sdListDie)
            du.removeClusterSV(sd.getId());

        // selective vergence attention
        if(enableVergenceAttention){
            if(clusters.isEmpty()){
                if(du.getIDforVergence() != -1){
                    du.setIDforVergence(-1);
                    du.setIDforAnnotation(-1);
                }
            } else {
                // gives attention to the oldest cluster
                // TODO : find a better way in selecting an cluster to make attention
                if(du.getIDforVergence() == -1){
                    int id = clusters.get(0).getClusterNumber();
                    du.setIDforVergence(id);
                    du.setIDforAnnotation(id);
                }
            }
        }
    }


    /**
     * overides this method to put disparity into cluster path
     *
     * @param t
     */
    @Override
    protected void updateClusterPaths(int t) {
        LinkedList<Cluster> matchedSCList = new LinkedList<Cluster>();

        // updates paths of shadow clusters first
        for(Cluster sc : shadowClusters){
            if(!sc.isDead() && sc.getUpdateStatus() != ClusterUpdateStatus.NOT_UPDATED){
                sc.updatePath(t, ((BlurringFilterStereo) bfilter).disparityUpdater.getGlobalDisparity());
                // reset the cluster's update status
                sc.setUpdated(ClusterUpdateStatus.NOT_UPDATED);
            }
        }

        // update paths of clusters
        for ( Cluster c:clusters ){
            boolean replaced = false;
            // if the cluster is doing subthreshold tracking for a long time and the life time of a shadow cluster is long enough,
            // replace the cluster with the shadow one.
            for(Cluster sc : shadowClusters){
                if(!matchedSCList.contains(sc) && c.getSubThTrackingTimeUs() > 2000000 && sc.getLifetime() > getSubThTrackingActivationTimeMs()*1000){
                    c.setLocation(sc.location);
                    matchedSCList.add(sc);
                    replaced = true;
                    break;
                }
            }

            if(!c.isDead() && c.getUpdateStatus() != ClusterUpdateStatus.NOT_UPDATED){
                if(replaced)
                    c.updatePath(t, ((BlurringFilterStereo) bfilter).disparityUpdater.getGlobalDisparity());
                else
                    c.updatePath(t, ((BlurringFilterStereo) bfilter).disparityUpdater.getDisparity(c.getClusterNumber()));
                c.setMinimumClusterSize(getMinimumClusterSizePixels() + (int)c.getDisparity(1));
                c.setUpdated(ClusterUpdateStatus.NOT_UPDATED); // resets update status
            }
        }

        shadowClusters.removeAll(matchedSCList);
    }

    /**
     * returns global disparity
     *
     * @param id
     * @return
     */
    public float getDisparity(int id) {
        return ((BlurringFilterStereo) bfilter).disparityUpdater.getDisparity(id);
    }

    /**
     * sets the limit of disparity value
     * 
     * @param disparityLimit
     * @param useLowLimit
     */
    public void setDisparityLimit(int disparityLimit, boolean useLowLimit){
        ((BlurringFilterStereo) super.bfilter).setDisparityLimit(disparityLimit, useLowLimit, -1);
    }

    /**
     * sets maxDisparityChangePixels of embedded vergence filter
     *
     * @param maxDisparityChangePixels
     */
    public void setMaxDisparityChangePixels(int maxDisparityChangePixels){
        ((BlurringFilterStereo) super.bfilter).setMaxDisparityChangePixels(maxDisparityChangePixels, -1);
    }

    /**
     * activates or deactivates the disparity limit
     *
     * @param enableDisparityLimit
     */
    public void setEnableDisparityLimit(boolean enableDisparityLimit){
        ((BlurringFilterStereo) super.bfilter).setDisparityLimitEnabled(enableDisparityLimit, -1);
    }

    /**
     * returns true if the disparity limit is enabled
     * @return
     */
    public boolean isDisparityLimitEnabled(){
        return ((BlurringFilterStereo) super.bfilter).isDisparityLimitEnabled(-1);
    }

    /**
     * returns autoThresholdReferenceDisparity
     *
     * @return
     */
    public int getAutoThresholdReferenceDisparity() {
        return autoThresholdReferenceDisparity;
    }

    /**
     * sets autoThresholdReferenceDisparity
     *
     * @param autoThresholdReferenceDisparity
     */
    public void setAutoThresholdReferenceDisparity(int autoThresholdReferenceDisparity) {
        this.autoThresholdReferenceDisparity = autoThresholdReferenceDisparity;
        getPrefs().putInt("BlurringFilterStereoTracker.autoThresholdReferenceDisparity", autoThresholdReferenceDisparity);
    }

    /**
     * returns autoThresholdReferenceThreshold
     *
     * @return
     */
    public int getAutoThresholdReferenceThreshold() {
        return autoThresholdReferenceThreshold;
    }

    /**
     * sets autoThresholdReferenceThreshold
     *
     * @param autoThresholdReferenceThreshold
     */
    public void setAutoThresholdReferenceThreshold(int autoThresholdReferenceThreshold) {
        this.autoThresholdReferenceThreshold = autoThresholdReferenceThreshold;
        getPrefs().putInt("BlurringFilterStereoTracker.autoThresholdReferenceThreshold", autoThresholdReferenceThreshold);
    }

    /**
     * returns autoThresholdSlope
     *
     * @return
     */
    public float getAutoThresholdSlope() {
        return autoThresholdSlope;
    }

    /**
     * sets autoThresholdSlope
     *
     * @param autoThresholdSlope
     */
    public void setAutoThresholdSlope(float autoThresholdSlope) {
        this.autoThresholdSlope = autoThresholdSlope;
        getPrefs().putFloat("BlurringFilterStereoTracker.autoThresholdSlope", autoThresholdSlope);
    }

    /**
     * returns enableAutoThreshold
     *
     * @return
     */
    public boolean isEnableAutoThreshold() {
        return enableAutoThreshold;
    }

    /**
     * sets enableAutoThreshold
     *
     * @param enableAutoThreshold
     */
    public void setEnableAutoThreshold(boolean enableAutoThreshold) {
        this.enableAutoThreshold = enableAutoThreshold;
        getPrefs().putBoolean("BlurringFilterStereoTracker.enableAutoThreshold", enableAutoThreshold);
    }

    /**
     * returns allowedMaxDisparityChange
     *
     * @return
     */
    public int getAllowedMaxDisparityChange() {
        return allowedMaxDisparityChange;
    }

    /**
     * sets allowedMaxDisparityChange
     *
     * @param allowedMaxDisparityChange
     */
    public void setAllowedMaxDisparityChange(int allowedMaxDisparityChange) {
        this.allowedMaxDisparityChange = allowedMaxDisparityChange;
        getPrefs().putInt("BlurringFilterStereoTracker.allowedMaxDisparityChange", allowedMaxDisparityChange);
    }

    /**
     * returns enableVergenceAttention
     * @return
     */
    public boolean isEnableVergenceAttention() {
        return enableVergenceAttention;
    }

    /**
     * sets enableVergenceAttention
     *
     * @param enableVergenceAttention
     */
    public void setEnableVergenceAttention(boolean enableVergenceAttention) {
        this.enableVergenceAttention = enableVergenceAttention;
        getPrefs().putBoolean("BlurringFilterStereoTracker.enableVergenceAttention", enableVergenceAttention);

        ((BlurringFilterStereo) bfilter).disparityUpdater.setMaxDisparityChangeEnabled(false, -1);
    }

    /**
     * returns vergenceAttentionLifetimeMs
     *
     * @return
     */
    public int getVergenceAttentionLifetimeMs() {
        return vergenceAttentionLifetimeMs;
    }

    /**
     * sets vergenceAttentionLifetimeMs
     * 
     * @param vergenceAttentionLifetimeMs
     */
    public void setVergenceAttentionLifetimeMs(int vergenceAttentionLifetimeMs) {
        this.vergenceAttentionLifetimeMs = vergenceAttentionLifetimeMs;
        getPrefs().putInt("BlurringFilterStereoTracker.vergenceAttentionLifetimeMs", vergenceAttentionLifetimeMs);
    }
}
