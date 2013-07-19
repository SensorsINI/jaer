/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.information;

import net.sf.jaer.chip.AEChip;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.path.PathLocation;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.position.PositionExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.FileHandler;

/**
 *
 * @author matthias
 */
public class InterruptedPositionInformationExtractor extends AbstractInformationExtractor {
    
    /**
     * Creates a new instance of a PathInformationExtractor.
     */
    public InterruptedPositionInformationExtractor(ParameterManager parameters, 
                                                   FeatureManager features, 
                                                   AEChip chip) {
        super(Features.Interrupt, parameters, features, Features.InformationPosition, chip);
        
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
     * Extracts the information of a FeatureExtractor and writes the information
     * to a file.
     * 
     * @param timestamp The timestamp of the current time.
     */
    @Override
    public void update(int timestamp) {
        /*
         * writes the signal to the file.
         */
        if (this.features.has(Features.Position)) {
            PathLocation current = ((PositionExtractor)this.features.get(Features.Position)).getCurrentLocation();
            PathLocation previous = ((PositionExtractor)this.features.get(Features.Position)).getPreviousLocation();
            
            PathLocation used;
            if (previous == null) {
                if (current == null) return;
                used = current;
            }
            else {
                if (Math.abs(current.timestamp - timestamp) < Math.abs(previous.timestamp - timestamp)) {
                    used = current;
                }
                else {
                    used = previous;
                }
            }
            
            FileHandler.getInstance(PATH + "position_" + this.hashCode() + ".txt").writeLine(String.format("%d %d %f %f", this.feature.hashCode(), used.timestamp, used.location.get(0), used.location.get(1)));
        }
    }
}
