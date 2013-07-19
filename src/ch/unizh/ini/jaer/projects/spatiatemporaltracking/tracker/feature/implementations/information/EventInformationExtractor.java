/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.information;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.TypedEvent;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.FileHandler;

/**
 *
 * @author matthias
 */
public class EventInformationExtractor extends AbstractInformationExtractor {
    
    /**
     * Creates a new instance of a EventInformationExtractor.
     */
    public EventInformationExtractor(ParameterManager parameters, 
                                     FeatureManager features, 
                                     AEChip chip) {
        super(Features.Event, parameters, features, Features.InformationEvent, chip);
        
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
        TypedEvent e = this.features.getEvent();
        
        FileHandler.getInstance(PATH + "events.txt").writeLine(String.format("%d %d %d", e.timestamp, e.x, e.y));
    }
}
