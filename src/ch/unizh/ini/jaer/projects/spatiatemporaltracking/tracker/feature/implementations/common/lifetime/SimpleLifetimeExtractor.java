/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.lifetime;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 */
public class SimpleLifetimeExtractor extends AbstractLifetimeExtractor {
    
    /** Stores the timestamp of the first event assigned to this cluster. */
    private int birthtime;
    
    /** Stores whether the object was allready used or not. */
    private boolean isVirgin;
    
    /**
     * Creates a new instance of a SimpleLifetimeExtractor.
     * 
     * @param parameters The instances managing the parameters used by this feature.
     * @param features The instance managing the features uesd by this feature.
     * @param chip The AEChip used by the DVS.
     */
    public SimpleLifetimeExtractor(ParameterManager parameters, 
                                   FeatureManager features,  
                                   AEChip chip) {
        super(Features.Packet, parameters, features, chip);
        
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

    @Override
    public void update(int timestamp) {
        this.timestamp = timestamp;
        
        if (this.isVirgin) { 
            this.birthtime = timestamp;
            this.creationtime = this.birthtime - this.features.getReferenceTime();
            this.isVirgin = false;
        }
        
        this.lifetime = timestamp - this.birthtime;
        
        this.features.getNotifier().notify(this.feature, timestamp);
    }
}
