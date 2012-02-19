/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.interrupt;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.file.MatrixParser;
import java.util.ArrayList;
import java.util.List;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 * 
 * Uses a text-file to read the timestamps to create an interrupt. The class
 * monitors the timestamp of the algorithm and causes an interrupt whenever
 * the algorithms timestamps exceeds a timestamp of the list.
 */
public class ExternalInterruptExtractor extends AbstractInterruptExtractor {

    /** 
     * Stores the list of timestamps at which an interrupt has to be created. 
     */
    private List<Integer> timestamps;
    
    /** Stores the pointer to the currently used timestamp of the list. */
    private int index;
    
    /**
     * Creates a new instance of a ExternalPostionInterruptExtractor.
     */
    public ExternalInterruptExtractor(ParameterManager parameters, 
                                             FeatureManager features, 
                                             AEChip chip) {
        super(Features.Event, parameters, features, chip);
        this.init();
        this.reset();
    }
    
    @Override
    public void init() {
        super.init();
        
        this.timestamps = new ArrayList<Integer>();
        
        List<List<Double>> m = MatrixParser.parse("C:\\Users\\matthias\\Documents\\02_eth\\05_semester\\01_master\\02_repo\\doc\\experiments\\tracking\\results\\path\\hand_circle_100ms.txt", ",");
        for (List<Double> l : m) {
            Long r = Math.round(l.get(0));
            this.timestamps.add(r.intValue());
        }
    }
    
    @Override
    public void reset() {
        super.reset();
        
        this.index = 0;
    }
    
    /**
     * Monitors the timestamps and creates an interrupt whenever the algorithms
     * timestamp exceeds one of the predefined timestamps of the list.
     * 
     * @param timestamp The algorithms timestamp.
     */
    @Override
    public void update(int timestamp) {
        boolean hasChanged = false;
        while (this.index < this.timestamps.size() &&
                this.timestamps.get(this.index) < timestamp) {
            this.index++;
            
            hasChanged = true;
        }
        
        if (hasChanged) {
            this.features.getNotifier().notify(this.feature, this.timestamps.get(this.index - 1));
        }
    }   
}
