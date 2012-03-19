/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.information;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.Vector;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.velocity.VelocityExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.FileHandler;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 */
public class VelocityInformationExtractor extends AbstractInformationExtractor {
    
    /**
     * Creates a new instance of a VelocityInformationExtractor.
     */
    public VelocityInformationExtractor(ParameterManager parameters, 
                                    FeatureManager features, 
                                    AEChip chip) {
        super(Features.Velocity, parameters, features, Features.InformationVelocity, chip);
        
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
        if (this.features.has(Features.Velocity)
                && this.features.get(Features.Signal).isStatic()) {
            Vector v = ((VelocityExtractor)this.features.get(Features.Velocity)).getVelocity();
            
            FileHandler.getInstance(PATH + "velocity_" + this.hashCode() + ".txt").writeLine(String.format("%d %d %f %f", this.feature.hashCode(), timestamp, v.get(0), v.get(1)));
        }
    }
}
