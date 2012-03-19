/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.event.packet;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 * 
 * This class is used to notifiy all extractor about a new available event.
 */
public class SimplePacketEventExtractor extends AbstractPacketEventExtractor {

    /**
     * Creates a new instance of the class SimplePacketEventExtractor.
     */
    public SimplePacketEventExtractor(ParameterManager parameters, 
                                      FeatureManager features, 
                                      AEChip chip) {
        super(Features.Packet, parameters, features, chip);
        
        this.init();
        this.reset();
    }
    
    /**
     * Gets the last packet add notifies all features that there is a new
     * event available.
     * 
     * @param timestamp The timestapm of the algorithm.
     */
    @Override
    public void update(int timestamp) {
        this.packet = this.features.getPacket();
    }
}
