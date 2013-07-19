/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.moment;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.TypedEvent;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.IntegerSummedCircularList;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;

/**
 *
 * @author matthias
 * 
 * Computes the central moments of the observed object.
 */
public class SimpleMomentExtractor extends AbstractMomentExtractor {
    
    /** The number of time slots used to compute the position. */
    public final int nObservations = 100;
    
    /** The resolution for the time. */
    public final int resolution = 100;
    
    /** Stores the moments in a circular list. */
    private IntegerSummedCircularList[][] storage;
    
    /** Indicates whether the object was allready used or not. */
    private boolean isVirgin;
    
    /** The timestamp of the last event added to the sum. */
    private int last;
    
    /**
     * Creates a new instance of a SimpleActivityExtractor.
     */
    public SimpleMomentExtractor(ParameterManager parameters, 
                                 FeatureManager features, 
                                 AEChip chip) {
        super(Features.Event, parameters, features, chip);
        
        this.init();
        this.reset();
    }
    
    @Override
    public void init() {
        super.init();
        
        this.storage = new IntegerSummedCircularList[this.nMoments][this.nMoments];
        for (int i = 0; i < this.storage.length; i++) {
            for (int j = 0; j < this.storage[i].length; j++) {
                this.storage[i][j] = new IntegerSummedCircularList(this.nObservations);
            }
        }
    }
    
    @Override
    public void reset() {
        super.reset();
        
        for (int i = 0; i < this.storage.length; i++) {
            for (int j = 0; j < this.storage[i].length; j++) {
                this.storage[i][j].reset();
            }
        }
        
        this.isVirgin = true;
        this.last = MIN_TIMESTAMP;
    }
    
    /**
     * Computes the central moments of the observed object.
     * 
     * @param timestamp The timestamp of the current time.
     */
    @Override
    public void update(int timestamp) {
        this.timestamp = timestamp;
        
        //TypedEvent event = ((SingleEventExtractor)this.features.get(Features.Event)).getEvent();
        TypedEvent event = this.features.getEvent();
        
        if (this.isVirgin) {
            this.isVirgin = false;
            this.last = event.timestamp;
        }
        int change = (event.timestamp - this.last) / this.resolution;

        if (change >= 0) {
            for (int i = 0; i < this.storage.length; i++) {
                for (int j = 0; j < this.storage[i].length; j++) {

                    this.storage[i][j].add(change, (int)(Math.pow(event.x, i) * Math.pow(event.y, j)));
                }
            }
            if (change > 0) this.last = event.timestamp;
        }
        
        for (int i = 0; i < this.storage.length; i++) {
            for (int j = 0; j < this.storage[i].length; j++) {
                this.moments[i][j] = this.storage[i][j].getSum();
            }
        }
        
        this.features.getNotifier().notify(this.feature, timestamp);
    }
}