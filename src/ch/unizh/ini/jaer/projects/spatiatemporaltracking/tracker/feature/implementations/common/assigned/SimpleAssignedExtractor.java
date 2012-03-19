/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.assigned;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import java.util.Arrays;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.TypedEvent;

/**
 *
 * @author matthias
 * 
 * Counts the number of events using a specified resolution.
 */
public class SimpleAssignedExtractor extends AbstractAssignedExtractor {

    /** Indicates whether the object was allready used or not. */
    private boolean isVirgin;
    
    /** Stores the current timestamp. */
    private int current;
    
    /** Stores the number of events in the current time slot. */
    private int[] sum;
    
    /**
     * Creates a new instance of a SimpleAssignedExtractor.
     */
    public SimpleAssignedExtractor (ParameterManager parameters, 
                                    FeatureManager features, 
                                    AEChip chip) {
        super(Features.Packet, parameters, features, chip);
        
        this.init();
        this.reset();
    }
    
    @Override
    public void init() {
        super.init();
        
        this.sum = new int[2];
    }
    
    @Override
    public void reset() {
        super.reset();
        
        this.isVirgin = true;
        Arrays.fill(this.sum, 0);
    }
    
    /**
     * Counts the number of events using a specified resolution.
     * 
     * @param timestamp The timestamp of the current time.
     */
    @Override
    public void update(int timestamp) {
        for (TypedEvent e : this.features.getPacket()) {
            if (this.isVirgin) {
                this.isVirgin = false;
                this.current = e.timestamp - (e.timestamp % this.resolution);
            }
            
            int change = (e.timestamp - this.current) / this.resolution;
            if (change >= 0) {
                if (change == 0) {
                    this.sum[e.type]++;
                }
                else {
                    for (int i = 0; i < this.storage.size(); i++) this.storage.get(i).add(new EventStorage(this.current, this.sum[i]));
                    for (int i = 0; i < this.visualization.size(); i++) this.visualization.get(i).add(new EventStorage(this.current, this.sum[i]));
                    Arrays.fill(this.sum, 0);
                    
                    this.current += this.resolution;
                    
                    for (int i = 1; i < change; i++) {
                        for (int j = 0; j < this.storage.size(); j++) this.storage.get(j).add(new EventStorage(this.current, 0));
                        for (int j = 0; j < this.visualization.size(); j++) this.visualization.get(j).add(new EventStorage(this.current, 0));
                        this.current += this.resolution;
                    }
                }
            }
        }
    }
}
