/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.stereo;

import ch.unizh.ini.jaer.projects.gesture.virtualdrummer.BlurringFilter2D;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;

/**
 *
 * @author Jun Haeng Lee
 */
public class BlurringFilterStereo extends BlurringFilter2D{
    
    StereoVergenceFilter svf;

    /**
     * Constructor
     * @param chip
     */
    public BlurringFilterStereo(AEChip chip) {
        super(chip);
        svf = new StereoVergenceFilter(chip);
        setEnclosedFilter(svf);
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        out = super.filterPacket(in);
        return out;
    }

    @Override
    public void resetFilter() {
        svf.resetFilter();
        super.resetFilter();
    }
}
