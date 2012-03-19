/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.velocity;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.path.PathLocation;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.path.PathExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 * 
 * Computes the current velocity of the observed object by considering the
 * current and last position of the object.
 */
public class SimpleVelocityExtractor extends AbstractVelocityExtractor {

    /**
     * Stores the last location of the object on its path.
     */
    private PathLocation last;
    
    /**
     * Indicates whether the predictor was allready used.
     */
    private boolean isVirgin;
    
    /**
     * Creates a new instance of a SimpleVelocityExtractor.
     */
    public SimpleVelocityExtractor(ParameterManager parameters, 
                                   FeatureManager features, 
                                   AEChip chip) {
        super(Features.Path, parameters, features, chip);
        
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
        
        this.isVirgin = true;
    }
    
    /**
     * Computes the current velocity of the observed object by considering the
     * current and last position of the object.
     * 
     * @param events The events for this extractor.
     */
    @Override
    public void update(int timestamp) {
        PathLocation p = ((PathExtractor)this.features.get(Features.Path)).getPath();
        
        if (this.isVirgin) {
            this.isVirgin = false;
        }
        else {
            this.timestamp = p.timestamp;

            int delta = p.timestamp - this.last.timestamp;
            for (int i = 0; i < 2; i++) {
                this.velocity.set(i, (p.location.get(i) - this.last.location.get(i)) / delta);
            }
            this.features.getNotifier().notify(this.feature, timestamp);
        }
        this.last = p;
    }
}
