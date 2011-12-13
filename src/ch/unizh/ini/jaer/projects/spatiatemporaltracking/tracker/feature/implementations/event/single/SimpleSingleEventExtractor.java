/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.event.single;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.ParameterManager;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 * 
 * This class is used to notifiy all extractor about a new available event.
 */
public class SimpleSingleEventExtractor extends AbstractSingleEventExtractor {

    /**
     * Creates a new instance of the class SimpleSingleEventExtractor.
     */
    public SimpleSingleEventExtractor(ParameterManager parameters, 
                                      FeatureManager features, 
                                      AEChip chip) {
        super(Features.Event, parameters, features, chip);
        
        this.init();
        this.reset();
    }
    
    /**
     * Gets the last event add notifies all features that there is a new
     * event available.
     * 
     * @param timestamp The timestapm of the algorithm.
     */
    @Override
    public void update(int timestamp) {
        this.event = this.features.getEvent();
        
        this.features.getNotifier().notify(Features.Event, timestamp);
    }
}
