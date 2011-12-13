/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.improver;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.FloatSummedCircularList;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.Transition;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.phase.PhaseExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.signal.SignalExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.transition.TransitionHistoryExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.ParameterManager;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 * 
 * This extractor uses an phase-looked-loop approach to improve the temporal
 * position of each transition of the signal.
 */
public class PhaseLockedSignaImproverlExtractor extends AbstractSignalImproverExtractor {
    
    /** Defines the number of observations considered to compute the jitter. */
    public int nObservations = 40;
    
    /** Defines how fast the transitions are shifted. */
    public float factor = 0.01f;
    
    /** 
     * Stores the jitter of the temporal position for each transition of the 
     * signal.
     */
    private FloatSummedCircularList[] jitters;
    
    /** Stores the timestamp for each transition. */
    private float[] timestamps;
    
    /**
     * Creates a new instance of a PhaseLockedSignalExtractor.
     */
    public PhaseLockedSignaImproverlExtractor(ParameterManager parameters, 
                                              FeatureManager features, 
                                              AEChip chip) {
        super(Features.Transition, parameters, features, chip);
        
        this.init();
        this.reset();
    }
    
    @Override
    public void init() {
        super.init();
        
        this.signal = ((SignalExtractor)this.features.get(Features.Signal)).getSignal();
        
        this.jitters = new FloatSummedCircularList[this.signal.getSize()];
        for (int i = 0; i < this.jitters.length; i++) this.jitters[i] = new FloatSummedCircularList(this.nObservations);
        
        this.timestamps = new float[this.signal.getSize()];
    }
    
    @Override
    public void reset() {
        super.reset();
        
        for (int i = 0; i < this.jitters.length; i++) this.jitters[i].reset();
        
        for (int i = 0; i < this.timestamps.length; i++) this.timestamps[i] = this.signal.getTransition(i).time;
    }

    @Override
    public void update(int timestamp) {
        Transition transition = ((TransitionHistoryExtractor)this.features.get(Features.Transition)).getTransition();
        
        int phase = ((PhaseExtractor)this.features.get(Features.Phase)).getPhase();

        int relative = (transition.time - phase) % this.signal.getPeriod();
        if (relative < 0) relative = phase - relative;

        float min = Integer.MAX_VALUE;
        float jitter = 0;
        int best = -1;
        for (int j = 0; j < this.timestamps.length; j++) {
            Transition m = this.signal.getOriginalTransition(j);

            if (m.state == transition.state) {
                float error1 = relative - this.timestamps[j];
                float error2 = (relative + this.timestamps[this.timestamps.length - 1]) - this.timestamps[j];

                float error;
                if (Math.abs(error1) < Math.abs(error2)) {
                    error = error1;
                }
                else {
                    error = error2;
                }

                if (Math.abs(error) < 100) {
                    if (min > Math.abs(error)) {
                        min = Math.abs(error);
                        jitter = error;
                        best = j;
                    }
                }
            }
        }
        if (best >= 0) {
            this.jitters[best].add(1, jitter);
        }
        
        for (int i = 0; i < this.jitters.length; i++) {
            if (this.jitters[i].isFull()) {
                float change = this.factor * this.jitters[i].getAverage();
                
                if (Math.abs(change) > 1) {
                    change = Math.signum(change);
                }
                this.timestamps[i] += change;
                
                this.signal.getOriginalTransition(i).time = Math.round(this.timestamps[i]);
                this.signal.update();
                
                this.jitters[i].reset();
            }
        }
    }
}