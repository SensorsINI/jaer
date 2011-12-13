/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.prediction.occurance;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.Signal;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.Transition;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.phase.PhaseExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.signal.SignalExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.ParameterManager;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 * 
 * This predictor uses the temporal pattern of the observed object to predict
 * the occurance of on- and off-events.
 */
public class TemporalPatternOccurancePredictor extends AbstractOccurancePredictor {
    
    /**
     * Indicates whether all data for the predictor are available or not.
     */
    private boolean isPredictable;
    
    /** Stores the signal extracted from the observed object. */
    private Signal signal;
    
    /** Stores the phase of the signal. */
    private int phase;
    
    /**
     * Creates a new instance of the class TemporalPatternOccurancePredictor.
     */
    public TemporalPatternOccurancePredictor(ParameterManager parameters, 
                                             FeatureManager features, 
                                             AEChip chip) {
        super(Features.Phase, parameters, features, chip);
        
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
        
        this.isPredictable = false;
    }
    
    @Override
    public boolean isPredictable() {
        return this.isPredictable;
    }
    
    @Override
    public void update(int timestamp) {
        this.timestamp = timestamp;
        
        PhaseExtractor pe = ((PhaseExtractor)this.features.get(Features.Phase));
        if (!pe.isFound()) return;
        this.phase = pe.getPhase();
        
        if (this.isPredictable) {
            if (!this.features.get(Features.Signal).isStatic()) {
                this.reset();
            }
        }
        else {
            if (this.features.get(Features.Signal).isStatic()) {
                this.isPredictable = true;
                
                this.signal = ((SignalExtractor)this.features.get(Features.Signal)).getSignal();
            }
        }
    }
    
    @Override
    public int getDistance(int type, int timestamp) {
        if (!this.isPredictable) return 0;
        
        /**
         * Compute relative timestamp within timestamp.
         */
        int relative = (timestamp - this.phase) % this.signal.getPeriod();
        if (relative < 0) relative = this.phase - relative;
        
        /**
         * Find transition with minimal temporal distance
         */
        int min = Integer.MAX_VALUE;
        for (int i = -1; i < this.signal.getSize(); i++) {
            Transition t = this.signal.getTransition(i);
            if (type == t.state) {
                int difference = Math.abs(relative - t.time);
                
                if (min > difference) {
                    min = difference;
                }
            }
        }
        
        return min;
    }
    
    @Override
    public int getRelativeDistance(int type, int timestamp) {
        return this.getDistance(type, timestamp + this.phase);
    }
}
