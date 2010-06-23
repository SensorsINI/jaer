/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.stereo;

import ch.unizh.ini.jaer.projects.gesture.virtualdrummer.BlurringFilter2D.NeuronGroup;
import ch.unizh.ini.jaer.projects.gesture.virtualdrummer.BlurringFilter2DTracker;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;


/**
 *
 * @author Jun Haeng Lee
 */
public class BlurringFilterStereoTracker extends BlurringFilter2DTracker{

    protected int globalDisparity;

    protected int validDisparityRange = getPrefs().getInt("BlurringFilterStereoTracker.validDisparityRange", 5);
    protected boolean removeInvalidClusters = getPrefs().getBoolean("BlurringFilterStereoTracker.removeInvalidClusters", true);

    public BlurringFilterStereoTracker(AEChip chip) {
        super(chip);
        setPropertyTooltip("Stereo", "validDisparityRange", "range of valid disparity.");
        setPropertyTooltip("Stereo", "removeInvalidClusters", "removes clusters with invalid disparity.");
    }

    @Override
    protected void filterChainSetting() {
        super.bfilter = new BlurringFilterStereo(chip);
        super.bfilter.addObserver(this);
        setEnclosedFilter(bfilter);
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        out = super.filterPacket(in);
        globalDisparity = ((BlurringFilterStereo) bfilter).getGlobalDisparity();

        return out;
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

    public int getDisparity() {
        return globalDisparity;
    }

    public int getValidDisparityRange() {
        return validDisparityRange;
    }

    public void setValidDisparityRange(int validDisparityRange) {
        this.validDisparityRange = validDisparityRange;
        getPrefs().putInt("BlurringFilterStereoTracker.validDisparityRange", validDisparityRange);
    }

    public boolean isRemoveInvalidClusters() {
        return removeInvalidClusters;
    }

    public void setRemoveInvalidClusters(boolean removeInvalidClusters) {
        this.removeInvalidClusters = removeInvalidClusters;
        getPrefs().putBoolean("BlurringFilterStereoTracker.removeInvalidClusters", removeInvalidClusters);
    }

}
