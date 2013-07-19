/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.information;

import net.sf.jaer.chip.AEChip;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.path.PathLocation;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.path.PathExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.FileHandler;

/**
 *
 * @author matthias
 */
public class PathInformationExtractor extends AbstractInformationExtractor {
    
    /**
     * Creates a new instance of a PathInformationExtractor.
     */
    public PathInformationExtractor(ParameterManager parameters, 
                                    FeatureManager features, 
                                    AEChip chip) {
        super(Features.Path, parameters, features, Features.InformationPath, chip);
        
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
        if (this.features.has(Features.Path) 
                && this.features.get(Features.Signal).isStatic()) {
            PathLocation p = ((PathExtractor)this.features.get(Features.Path)).getPath();
            
            FileHandler.getInstance(PATH + "path_" + this.hashCode() + ".txt").writeLine(String.format("%d %d %f %f", this.feature.hashCode(), timestamp, p.location.get(0), p.location.get(1)));
        }
    }
}
