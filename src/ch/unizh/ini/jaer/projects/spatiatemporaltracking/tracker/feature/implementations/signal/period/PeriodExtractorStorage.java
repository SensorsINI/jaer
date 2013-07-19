/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.period;

import net.sf.jaer.chip.AEChip;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;

/**
 *
 * @author matthias
 * 
 * Stores the extracted period of the observed signal.
 */
public class PeriodExtractorStorage extends AbstractPeriodExtractor {

    /**
     * Creates a new PeriodExtractorStorage.
     */
    public PeriodExtractorStorage (int period,
                                   ParameterManager parameters, 
                                   FeatureManager features, 
                                   AEChip chip) {
        super(Features.None, parameters, features, chip);
        
        this.init();
        this.reset();
        
        this.period = period;
    }
    
    @Override
    public void update(int timestamp) { }
    
    @Override
    public String toString() {
        return "<storage> " + super.toString();
    }
}
