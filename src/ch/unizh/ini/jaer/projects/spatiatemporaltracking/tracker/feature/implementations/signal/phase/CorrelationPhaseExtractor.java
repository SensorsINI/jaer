/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.phase;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.correlation.CorrelationItem;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.correlation.SignalCorrelationExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import java.util.List;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 * 
 * Computes the phase of the signal by using the correlation between the
 * observed transition history and the extracted signal.
 */
public class CorrelationPhaseExtractor extends AbstractPhaseExtractor {

    /**
     * Determines how much a local maxima can deviate from the global maxima
     * to be choosen as a possible phase.
     */
    public final float deviation = 0.9f;
    
    /**
     * Stores the global maxima found so far.
     */
    private float max;
    
    /**
     * Creates a new instance of a CorrelationPhaseExtractor.
     */
    public CorrelationPhaseExtractor(ParameterManager parameters, 
                                     FeatureManager features, 
                                     AEChip chip) {
        super(Features.Correlation, parameters, features, chip);
        
        this.init();
        this.reset();
    }
    
    @Override
    public void init() {
        super.init();
    }
    
    @Override
    public void reset() {
        super.reset();
        
        this.max = 0;
    }
    
    /**
     *
     * Computes the phase of the signal by using the correlation between the
     * observed transition history and the extracted signal.
     * 
     * @param timestamp The timestamp of the algorithm.
     */
    @Override
    public void update(int timestamp) {
        List<CorrelationItem> items = ((SignalCorrelationExtractor)this.features.get(Features.Correlation)).getCorrelation();
        if (items.isEmpty()) return;
        
        boolean hasChanged = false;
        for (CorrelationItem item : items) {
            if (this.max * this.deviation < item.score) {
                this.isFound = true;
                hasChanged = true;
                
                this.max = item.score;
                this.phase = item.value;
            }
        }
        if (hasChanged) this.features.getNotifier().notify(this.feature, timestamp);
    }
    
    @Override
    public String toString() {
        return super.toString() + ", max = " + this.max;
    }
}
