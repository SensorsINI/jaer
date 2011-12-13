/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.prediction.velocity;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.Vector;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.velocity.VelocityExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.ParameterManager;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.util.filter.LowpassFilter;

/**
 *
 * @author matthias
 * 
 * Uses a low pass filter in each direction to reduce noise within the
 * predicted velocity.
 */
public class LowPassVelocityPredictor extends AbstractVelocityPredictor {
    
    /**
     * Stores the low pass filter for each direction.
     */
    private LowpassFilter[] filters;
    
    /** The predicted velocity. */
    private Vector velocity;
    
    /**
     * The timestamp of the last measured velocity.
     */
    private int last;
    
    /**
     * Indicates whether the predictor was allready used.
     */
    private boolean isVirgin;
    
    /**
     * Creates a new LowPassVelocityPredictor.
     */
    public LowPassVelocityPredictor(ParameterManager parameters, 
                                    FeatureManager features, 
                                    AEChip chip) {
        super(Features.Velocity, parameters, features, chip);
        
        this.init();
        this.reset();
    }
    
    @Override
    public void init() {
        super.init();
        
        this.velocity = Vector.getDefault(3);
        
        this.filters = new LowpassFilter[2];
        for (int i = 0; i < this.filters.length; i++) this.filters[i] = new LowpassFilter();
    }
    
    @Override
    public void reset() {
        super.reset();
        
        this.velocity.reset();
        this.isVirgin= true;
        
        for (int i = 0; i < this.filters.length; i++) this.filters[i].reset();
    }
    
    /**
     * Uses a low pass filter in each direction to reduce noise within the
     * predicted velocity.
     * 
     * @param timestamp The timestamp of the current time.
     */
    @Override
    public void update(int timestamp) {
        Vector target = ((VelocityExtractor)this.features.get(Features.Velocity)).getVelocity().copy();
        
        if (this.isVirgin) {
            this.isVirgin = false;
            this.velocity = target;
            return;
        }
        
        for (int i = 0; i < this.filters.length; i++) {
            this.velocity.set(i, this.filters[i].filter(target.get(i), timestamp));
        }
    }
    
    @Override
    public Vector getVelocity(int timestamp) {
        return this.getReferenceVelocity();
    }

    @Override
    public Vector getVelocityRelative(int delta) {
        return this.getReferenceVelocity();
    }

    @Override
    public Vector getReferenceVelocity() {
        return this.velocity;
    }
}
