/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.correlation;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.correlation.CorrelationItem;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.FixedLengthTransitionHistory;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.Signal;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.TransitionHistory;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.FeatureExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.signal.SignalExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.transition.TransitionHistoryExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.Parameters;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.math.Correlation;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 * 
 * Computes the correlation between the signal and the stored transition
 * history.
 */
public class SimpleSignalCorrelationExtractor extends AbstractSignalCorrelationExtractor {

    /**
     * Determines how much time the extractor can wait until it has to find
     * the correct phase again.
     */
    private int delta = SECOND / 100;
    
    /** Stores the transition history to compare the signal with. */
    private TransitionHistory compare;
    
    /** Counts the number of calls. */
    private int counter;
    
    /**
     * Creates a new instance of the class SingleEventSignalCorrelationExtractor;
     */
    public SimpleSignalCorrelationExtractor(ParameterManager parameters, 
                                                 FeatureManager features, 
                                                 AEChip chip) {
        super(Features.Transition, parameters, features, chip);
        
        this.init();
        this.reset();
    }
    
    @Override
    public void init() {
        super.init();
        
        this.compare = new FixedLengthTransitionHistory(this.nObservations * 2);
    }
    
    @Override
    public void reset() {
        super.reset();
        
        this.compare.reset();
        this.counter = 0;
    }
    
    @Override
    public void update(int timestamp) {
        /*
         * add the new transition to the comparision for the correlation 
         * function.
         */
        this.counter++;
        this.compare.add(((TransitionHistoryExtractor)this.features.get(Features.Transition)).getTransition());
        
        if (this.counter < this.nObservations) return;
        this.counter = 0;
        
        /*
         * check whether the signal is valid.
         */
        FeatureExtractor extractor = this.features.get(Features.Signal);
        if (!extractor.isStatic()) return;
        
        Signal signal = ((SignalExtractor)extractor).getSignal();
        
        this.timestamp = timestamp;
        
        if (this.compare.getSize() >= this.nObservations * 2) {
            this.correlation.clear();
            
            /*
             * correlates the extracted signal with the observed transition
             * history to determine the phase.
             */
            Correlation c = Correlation.getInstance();
            for (int i = 0; i < this.nObservations; i++) {
                float match = c.correlation(c.getItem(this.compare, i, Math.min(this.compare.getSize() - 1, i + this.nObservations)), 
                                            c.getItem(signal, -1, Integer.MAX_VALUE));

                this.correlation.add(new CorrelationItem(this.compare.getTransition(i).time, match));

                this.drawing.add(this.correlation.get(this.correlation.size() - 1));
            }
            /*
             * inform the notifier about the new result.
             */
            this.features.getNotifier().notify(this.feature, timestamp);
        }
    }
    
    @Override
    public void parameterUpdate() {
        super.parameterUpdate();
        
        if (Parameters.getInstance().hasKey(Parameters.PREDICTOR_SINGAL_CORRELATION)) this.delta = Parameters.getInstance().getAsInteger(Parameters.PREDICTOR_SINGAL_CORRELATION);
    }
}
