/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.position;

import net.sf.jaer.chip.AEChip;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.path.PathLocation;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.moment.MomentExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;

/**
 *
 * @author matthias
 * 
 * Computes the position of the observed object based on the central moments
 * computed for the observed object.
 */
public class MomentPositionExtractor extends AbstractPositionExtractor {

    /**
     * Creates a new instance of the class MomentPositionExtractor.
     */
    public MomentPositionExtractor(ParameterManager parameters, 
                                   FeatureManager features, 
                                   AEChip chip) {
        super(Features.Moment, parameters, features, chip);
        
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
    }
    
    /**
     * Computes the position of the observed object based on the central 
     * moments computed for the observed object
     * 
     * @param timestamp The timestamp of the algorithm.
     */
    @Override
    public void update(int timestamp) {
        this.timestamp = timestamp;
        
        float m00 = ((MomentExtractor)this.features.get(Features.Moment)).getMoment(0, 0);
        float m10 = ((MomentExtractor)this.features.get(Features.Moment)).getMoment(1, 0);
        float m01 = ((MomentExtractor)this.features.get(Features.Moment)).getMoment(0, 1);
        
        this.position.set(0, m10 / m00);
        this.position.set(1, m01 / m00);
        
        this.previous = this.current;
        this.current = new PathLocation(this.position.copy(), timestamp);
        
        this.features.getNotifier().notify(Features.Position, timestamp);
    }
}
