/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.path;

import net.sf.jaer.chip.AEChip;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.Vector;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.path.PathLocation;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.position.PositionExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;

/**
 *
 * @author matthias
 * 
 * Extracts the path of the observed object while observing the elapsed time
 * and distance between two measured positions. It has to guarante a spatial
 * and temporal closness.
 */
public class ConstantTimeDistancePathExtractor extends AbstractPathExtractor {

    /** Defiens the minimal time between to positions. */
    private int interval = 20000;
    
    /** Defines the maximal squared distance between to positions. */
    private int distance = 4;
    
    /** Indicates whether the object was allready used or not. */
    private boolean isVirign;
    
    /** Stores the current timestamp. */
    private int current;
    
    /**
     * Creates a new instance of a ConstantTimePathExtractor.
     */
    public ConstantTimeDistancePathExtractor(ParameterManager parameters, 
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
     * Extracts the path of the observed object while observing the elapsed 
     * time and distance between two measured positions.
     * 
     * @param timestamp The timestamp of the current time.
     */
    @Override
    public void update(int timestamp) {
        if (this.isVirign) {
            this.isVirign = false;
            
            this.path = new PathLocation(((PositionExtractor)this.features.get(Features.Position)).getPosition().copy(), timestamp);
            return;
        }
        
        Vector position = ((PositionExtractor)this.features.get(Features.Position)).getPosition().copy();
        
        if (this.path.timestamp + this.interval < timestamp || 
                position.copy().substract(this.path.location).squaredNorm() > this.distance) {
            
            this.path = new PathLocation(position, timestamp);
            this.visualization.add(this.path);
            
            this.features.getNotifier().notify(this.feature, timestamp);
        }
    }
}
