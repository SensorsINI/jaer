/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.stereo;

import ch.unizh.ini.jaer.projects.gesture.virtualdrummer.BlurringFilter2DTracker;
import net.sf.jaer.chip.AEChip;


/**
 *
 * @author Jun Haeng Lee
 */
public class BlurringFilterStereoTracker extends BlurringFilter2DTracker{

    public BlurringFilterStereoTracker(AEChip chip) {
        super(chip);
    }

    @Override
    protected void filterChainSetting() {
        super.bfilter = new BlurringFilterStereo(chip);
        super.bfilter.addObserver(this);
        setEnclosedFilter(bfilter);
    }

    public float getDisparity(){
        return 0; // TODO
    }
}
