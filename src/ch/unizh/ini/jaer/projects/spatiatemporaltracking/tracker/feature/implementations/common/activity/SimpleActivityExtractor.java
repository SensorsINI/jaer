/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.activity;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.IntegerSummedCircularList;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.boundary.BoundaryExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.ParameterManager;
import java.util.List;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.TypedEvent;

/**
 *
 * @author matthias
 * 
 * This extractor measures the activity of the observed object and the
 * corresponding standard error to give an indication about the variation
 * of the measured activity.
 */
public class SimpleActivityExtractor extends AbstractActivityExtractor {
    
    /** The number of timeslots avaiable for this extractor. */
    public final int nObservations = 100;
    
    /** The resolution for the time. */
    public final int resolution = 1000;
    
    /** 
     * The circular list used to store the activity in the different time 
     * slots. 
     */
    private IntegerSummedCircularList sum;
    
    /** Stores the number of events in a slot. */
    private int slot;
    
    /** The timestamp of the last event added to the slot. */
    private int last;
    
    /** True, if no event is added to the extractor, false otherwise. */
    private boolean isVirgin;
    
    /**
     * Creates a new instance of a SimpleActivityExtractor.
     */
    public SimpleActivityExtractor(ParameterManager parameters, 
                                   FeatureManager features, 
                                   AEChip chip) {
        super(Features.Packet, parameters, features, chip);
        
        this.init();
        this.reset();
    }
    
    @Override
    public void init() {
        super.init();
        
        this.sum = new IntegerSummedCircularList(this.nObservations);
    }
    
    @Override
    public void reset() {
        super.reset();
        
        this.sum.reset();
        this.slot = 0;
        
        this.last = MIN_TIMESTAMP;
        this.isVirgin = true;
    }
    
    /**
     * Computes the activity of the observed object based on the number of
     * events assigned to the object.
     * @param timestamp The timestapm of the algorithm.
     */
    @Override
    public void update(int timestamp) {
        this.timestamp = timestamp;
        
        //List<TypedEvent> events = ((PacketEventExtractor)this.features.get(Features.Packet)).getPacket();
        List<TypedEvent> events = this.features.getPacket();
        
        if (this.isVirgin) {
            this.last = timestamp;
            this.isVirgin = false;
        }
        
        for (TypedEvent e : events) {
            int change = (e.timestamp - this.last) / this.resolution;
            
            if (change >= 0) {
                if (change == 0) {
                    this.slot++;
                }
                else {
                    this.sum.add(change, this.slot);
                    this.slot = 0;
                    
                    this.last = e.timestamp;
                }
            }
        }
        
        /*
         * advance the pointer in the circular list to the current timestamp
         */
        int change = (timestamp - this.last) / this.resolution;    
        if (change > 0) {
            this.sum.add(change, this.slot);
            this.slot = 0;

            this.last = timestamp;
        }
        
        /*
         * compute standard error of the number of events in each slot
         */
        BoundaryExtractor be = (BoundaryExtractor)this.features.get(Features.Boundary);
        
        this.activity = this.sum.getSum() / (be.getMajorLength() * be.getMinorLength() * Math.PI);
        
        this.features.getNotifier().notify(this.feature, timestamp);
    }
}
