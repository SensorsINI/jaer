/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventprocessing.FilterChain;

/**
 * Concrete CNN that classifies frames from DAVIS either of APS of DVS data
 *
 * @author tobi
 */
@Description("Classifies frames using CNN from DAVIS APS or DVS frames")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class DavisClassifierCNN extends DavisAbstractCNN {

    public DavisClassifierCNN(AEChip chip) {
        super(chip);
        dvsSubsampler = new DvsFramerSingleFrame(chip); // must be replaced by the right subsampler object by subclasses TODO not clean
        FilterChain chain = new FilterChain(chip);
        chain.add(dvsSubsampler); // only for control, we iterate with it here using the events we recieve by directly calling addEvent in the event processing loop, not by using the subsampler filterPacket method
        setEnclosedFilterChain(chain);
        initFilter();
    }

}
