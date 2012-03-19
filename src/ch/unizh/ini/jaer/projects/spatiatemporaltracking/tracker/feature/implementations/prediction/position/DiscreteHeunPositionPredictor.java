/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.prediction.position;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.Vector;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.path.PathLocation;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.path.PathExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.position.PositionExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.prediction.velocity.VelocityPredictor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 * 
 * Predictes the position of the observed object according to the predicted
 * velocity. To compute the position the Heun integration schema is used.
 */
public class DiscreteHeunPositionPredictor extends AbstractPositionExtractor {
    
    /**
     * Defines the maximal timestamp of the predicted velocities.
     */
    private int future = SECOND / 10;
    
    /**
     * Defines the resolution for the predicted velocities.
     */
    private int resolution = 10000;
    
    /**
     * Stores the last position on the path of the observed object.
     */
    protected PathLocation last;
    
    /**
     * Indicates whether the predictor was allready used.
     */
    private boolean isVirgin;
    
    /**
     * Stores the precomputed predicted velocities.
     */
    private Vector[] velocities;
    
    /**
     * Creates a new instance of the class EventDiscreteHeunPositionPredictor.
     */
    public DiscreteHeunPositionPredictor(ParameterManager parameters, 
                                         FeatureManager features, 
                                         AEChip chip) {
        super(Features.VelocityPredictor, parameters, features, chip);
        
        this.init();
        this.reset();
    }
    
    @Override
    public void init() {
        super.init();
        
        this.velocities = new Vector[this.future / this.resolution + 1];
    }
    
    @Override
    public void reset() {
        super.reset();
        
        this.isVirgin = true;
    }
    
    @Override
    public void update(int timestamp) {
        this.last = ((PathExtractor)this.features.get(Features.Path)).getPath();
        
        VelocityPredictor predictor = ((VelocityPredictor)this.features.get(Features.VelocityPredictor));
        Vector v0 = predictor.getVelocityRelative(0);
        for (int i = 0; i <= this.future / this.resolution; i++) {
            int time = i * this.resolution;
            
            this.velocities[i] = predictor.getVelocityRelative(time).add(v0).multiply(0.5f);
        }
        this.isVirgin = false;
    }
    
    @Override
    public Vector getPosition(int timestamp) {
        if (this.isVirgin) return ((PositionExtractor)this.features.get(Features.Position)).getPosition();
        
        return this.getPositionRelative(timestamp - this.last.timestamp);
    }

    @Override
    public Vector getPositionRelative(int delta) {
        if (this.isVirgin) return ((PositionExtractor)this.features.get(Features.Position)).getPosition();
        
        /*
         * computes the elapsed time between the current position and our
         * estimated velocity.
         */
        int elapsed = (this.last.timestamp + delta) - this.features.get(Features.Position).lastChange();
        Vector p = ((PositionExtractor)this.features.get(Features.Position)).getPosition();
        
        int index = Math.min(delta / this.resolution, this.velocities.length - 1);
        return this.velocities[index].copy().multiply(elapsed).add(p);
    }
}
