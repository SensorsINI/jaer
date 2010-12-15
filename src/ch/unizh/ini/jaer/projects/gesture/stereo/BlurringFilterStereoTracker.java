/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.stereo;

import ch.unizh.ini.jaer.projects.gesture.virtualdrummer.BlurringFilter2D.NeuronGroup;
import ch.unizh.ini.jaer.projects.gesture.virtualdrummer.BlurringFilter2DTracker;
import java.util.Observable;
import net.sf.jaer.chip.AEChip;


/**
 *
 * @author Jun Haeng Lee
 */
public class BlurringFilterStereoTracker extends BlurringFilter2DTracker{

    /**
     * global disparity
     */
    protected int globalDisparity;

    /**
     * Stereo blurring filter
     */
    protected BlurringFilterStereo stereoBF = null;

    /**
     * range of valid disparity
     */
    protected int validDisparityRange = getPrefs().getInt("BlurringFilterStereoTracker.validDisparityRange", 5);

    /**
     * removes clusters with invalid disparity
     */
    protected boolean removeInvalidClusters = getPrefs().getBoolean("BlurringFilterStereoTracker.removeInvalidClusters", true);

    /**
     * the threshold of LIF neuron of Blurring filter varies adaptively based on the disparity
     */
    protected boolean enableAutoThreshold = getPrefs().getBoolean("BlurringFilterStereoTracker.enableAutoThreshold", true);


    /**
     * reference disparity to define the reference threshold
     */
    protected int autoThresholdReferenceDisparity = getPrefs().getInt("BlurringFilterStereoTracker.autoThresholdReferenceDisparity", 0);
    
    /**
     * autoThresholdReferenceThreshold
     */
    protected int autoThresholdReferenceThreshold = getPrefs().getInt("BlurringFilterStereoTracker.autoThresholdReferenceThreshold", 35);

    /**
     * threshold/disparity
     */
    protected float autoThresholdSlope = getPrefs().getFloat("BlurringFilterStereoTracker.autoThresholdSlope", 0.3f);


    /**
     * constructor
     * @param chip
     */
    public BlurringFilterStereoTracker(AEChip chip) {
        super(chip);

        String stereo = "Stereo";
        setPropertyTooltip(stereo, "validDisparityRange", "range of valid disparity.");
        setPropertyTooltip(stereo, "removeInvalidClusters", "removes clusters with invalid disparity.");
        setPropertyTooltip(stereo, "enableAutoThreshold", "if true, the threshold of LIF neuron of Blurring filter varies adaptively based on the disparity.");
        setPropertyTooltip(stereo, "autoThresholdReferenceDisparity", "reference disparity to define the reference threshold.");
        setPropertyTooltip(stereo, "autoThresholdReferenceThreshold", "Threshold of LIF neuron of Blurring filter at the reference disparity.");
        setPropertyTooltip(stereo, "autoThresholdSlope", "threshold/disparity.");
    }

    /**
     * overides this method to replace BluringFilter2D with BluringFilterStereo
     */
    @Override
    protected void filterChainSetting() {
        stereoBF = new BlurringFilterStereo(chip);
        super.bfilter = stereoBF;
        super.bfilter.addObserver(this);
        setEnclosedFilter(bfilter);
    }

    /**
     * overides this method to update global disparity
     *
     * @param o
     * @param arg
     */
    @Override
    public void update(Observable o, Object arg) {
        super.update(o, arg);
        globalDisparity = ((BlurringFilterStereo) bfilter).getGlobalDisparity();

        // adaptive threshold
        if(enableAutoThreshold){
            float thresholdAdptive = autoThresholdReferenceThreshold + ((globalDisparity - autoThresholdReferenceDisparity)*autoThresholdSlope);
            if(thresholdAdptive < 1.0f)
                thresholdAdptive = 1.0f;

            super.bfilter.setMPThreshold((int) thresholdAdptive);
        }
    }

    /**
     * overides this method to put disparity into cluster path
     *
     * @param t
     */
    @Override
    protected void updateClusterPaths(int t) {
        // update paths of clusters
        for ( Cluster c:clusters ){
            if(c.isDead())
                continue;
            c.updatePath(t, globalDisparity);
            c.setUpdated(false);
        }
    }

    @Override
    protected void track(NeuronGroup cellGroup, int initialAge) {
        if (cellGroup.getNumMemberNeurons() == 0) {
            return;
        }

        Cluster closest = null;
        closest = getNearestCluster(cellGroup); // find cluster that event falls within (or also within surround if scaling enabled)

        if (closest != null) {
            closest.addGroup(cellGroup);
        } else { // start a new cluster
            if(removeInvalidClusters){
                if(((BlurringFilterStereo) bfilter).isDisparityValid((int) cellGroup.getLocation().y))
                    if(((BlurringFilterStereo) bfilter).getDisparity((int) cellGroup.getLocation().y) > globalDisparity - validDisparityRange)
                        clusters.add(new Cluster(cellGroup, initialAge));
            } else {
                clusters.add(new Cluster(cellGroup, initialAge));
            }
        }
    }

    /**
     * returns global disparity
     * @return
     */
    public int getDisparity() {
        return globalDisparity;
    }

    /**
     * returns validDisparityRange
     * @return
     */
    public int getValidDisparityRange() {
        return validDisparityRange;
    }

    /**
     * sets validDisparityRange
     *
     * @param validDisparityRange
     */
    public void setValidDisparityRange(int validDisparityRange) {
        this.validDisparityRange = validDisparityRange;
        getPrefs().putInt("BlurringFilterStereoTracker.validDisparityRange", validDisparityRange);
    }

    /**
     * returns removeInvalidClusters
     * @return
     */
    public boolean isRemoveInvalidClusters() {
        return removeInvalidClusters;
    }

    /**
     * sets removeInvalidClusters
     * 
     * @param removeInvalidClusters
     */
    public void setRemoveInvalidClusters(boolean removeInvalidClusters) {
        this.removeInvalidClusters = removeInvalidClusters;
        getPrefs().putBoolean("BlurringFilterStereoTracker.removeInvalidClusters", removeInvalidClusters);
    }

    /**
     * sets the limit of disparity value
     * 
     * @param disparityLimit
     * @param useLowLimit
     */
    public void setDisparityLimit(int disparityLimit, boolean useLowLimit){
        ((BlurringFilterStereo) super.bfilter).setDisparityLimit(disparityLimit, useLowLimit);
    }

    /**
     * sets maxDisparityChangePixels of embedded vergence filter
     *
     * @param maxDisparityChangePixels
     */
    public void setMaxDisparityChangePixels(int maxDisparityChangePixels){
        ((BlurringFilterStereo) super.bfilter).svf.setMaxDisparityChangePixels(maxDisparityChangePixels);
    }

    /**
     * activates or deactivates the disparity limit
     *
     * @param enableDisparityLimit
     */
    public void setEnableDisparityLimit(boolean enableDisparityLimit){
        ((BlurringFilterStereo) super.bfilter).setEnableDisparityLimit(enableDisparityLimit);
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
}
