/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.prediction.velocity;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.Vector;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.velocity.VelocityExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.prediction.acceleration.AccelerationPredictor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 * 
 * Uses the predicted acceleration to compute a synthetic velocity. The changes
 * of the predicted velocities should be much smoother than with non synthetic
 * velocities.
 */
public class SyntheticVelocityPredictor extends AbstractVelocityPredictor {
    
    /**
     * Stores the synthetic velocity of the observed object.
     */
    private Vector velocity;
    
    /**
     * Indicates whether the predictor was allready used.
     */
    private boolean isVirgin;
    
    /**
     * Creates a new SyntheticVelocityPredictor.
     */
    public SyntheticVelocityPredictor(ParameterManager parameters, 
                                      FeatureManager features, 
                                      AEChip chip) {
        super(Features.AccelerationPredictor, parameters, features, chip);
        
        this.init();
        this.reset();
    }
    
    @Override
    public void init() {
        super.init();
        
        this.velocity = Vector.getDefault(3);
    }
    
    @Override
    public void reset() {
        super.reset();
        
        this.velocity.reset();
        
        this.isVirgin = true;
    }
    
    @Override
    public void update(int timestamp) {
        Vector measured = ((VelocityExtractor)this.features.get(Features.Velocity)).getVelocity();
        if (this.isVirgin) {
            this.isVirgin = false;
            this.velocity = measured.copy();
        }
        else {
            this.velocity = this.getVelocityRelative(timestamp - this.timestamp);
            
            Vector projectedPrediction = this.velocity.copy().redimension(2);
            Vector projectedMeasured = measured.copy().redimension(2);
            
            if (projectedPrediction.normalizedDot(projectedMeasured) < 0.5) {
                this.features.get(Features.AccelerationPredictor).reset();
                
                this.velocity = measured.copy();
            }
        }
        this.timestamp = timestamp;
        this.features.getNotifier().notify(this.feature, timestamp);
    }
    
    @Override
    public Vector getVelocity(int timestamp) {
        return this.getVelocityRelative(timestamp - this.timestamp);
    }

    @Override
    public Vector getVelocityRelative(int delta) {
        return ((AccelerationPredictor)this.features.get(Features.AccelerationPredictor)).computeVelocity(this.getReferenceVelocity(), delta);
    }

    @Override
    public Vector getReferenceVelocity() {
        return this.velocity;
    }
}
