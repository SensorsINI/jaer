/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.path;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.Vector;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.path.PathLocation;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.position.PositionExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.ParameterManager;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 * 
 * Extracts the path of the observed object with a constant time interval
 * between each pair of positions.
 */
public class ConstantTimePathExtractor extends AbstractPathExtractor {

    /** Defiens the minimal time between to positions. */
    private int interval = 20000;
    
    /** Indicates whether the object was allready used or not. */
    private boolean isVirign;
    
    /** Stores the current timestamp. */
    private int current;
    
    /**
     * Creates a new instance of a ConstantTimePathExtractor.
     */
    public ConstantTimePathExtractor(ParameterManager parameters, 
                                     FeatureManager features, 
                                     AEChip chip) {
        super(Features.Position, parameters, features, chip);
        
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
        
        this.isVirign = true;
    }
    
    /*
     * Extracts the path of the observed object with a constant time interval
     * between each pair of positions.
     * 
     * @param timestamp The timestamp of the current time.
     */
    @Override
    public void update(int timestamp) {
        if (this.isVirign) {
            this.isVirign = false;
            this.current = timestamp;
        }
        
        if (this.current + this.interval < timestamp) {
            Vector position = ((PositionExtractor)this.features.get(Features.Position)).getPosition().copy();
            
            this.current = timestamp;
            this.path = new PathLocation(position, timestamp);
            this.visualization.add(this.path);
            
            this.features.getNotifier().notify(this.feature, timestamp);
        }
    }
}
