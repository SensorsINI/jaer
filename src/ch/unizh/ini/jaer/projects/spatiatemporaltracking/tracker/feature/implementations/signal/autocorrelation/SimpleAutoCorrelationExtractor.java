/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.autocorrelation;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.correlation.CorrelationItem;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.correlation.CorrelationStorage;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.FixedLengthTransitionHistory;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.Transition;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.transition.TransitionHistoryExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.math.Correlation;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 * 
 * Computes the auto-correlation function for a fixed number of transitions.
 */
public class SimpleAutoCorrelationExtractor extends AbstractAutoCorrelationExtractor {
    
    /** Stores the required Transitions for the auto-correlation function. */
    private FixedLengthTransitionHistory compare;
    
    /**
     * Creates a new instance of a SimpleAutoCorrelationExtractor.
     */
    public SimpleAutoCorrelationExtractor(ParameterManager parameters, 
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
    } 
    
    /**
     *
     * @author matthias
     * 
     * Computes the auto-correlation function for a fixed number of transitions.
     * The TransitionHistory extracted from the events assigned to the observed
     * object is used as input for the computation.
     * 
     * @param timestamp The timestamp of the current time.
     */
    @Override
    public void update(int timestamp) {
        Transition transition = ((TransitionHistoryExtractor)this.features.get(Features.Transition)).getTransition();
        
        this.compare.add(transition);

        if (this.compare.getSize() >= this.nObservations * 2) {
            this.timestamp = timestamp;

            CorrelationItem[] items = new CorrelationItem[this.nObservations];
            for (int t = 0; t < this.nObservations; t++) {
                Correlation c = Correlation.getInstance();

                items[t] = new CorrelationItem(this.compare.getTransition(t).time,
                                               c.correlation(c.getItem(this.compare, 0, this.nObservations),
                                                             c.getItem(this.compare, t, this.nObservations + t)));
            }
            this.correlation = new CorrelationStorage(items);
            this.drawing = this.correlation;

            /*
             * notifies the extractors about the new correlation.
             */
            this.features.getNotifier().notify(this.feature, timestamp);
        }
    }
}
