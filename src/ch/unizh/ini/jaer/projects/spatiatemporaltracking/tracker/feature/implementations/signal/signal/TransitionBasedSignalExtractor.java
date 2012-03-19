/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.signal;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.SimpleSignal;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.Transition;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.TransitionHistory;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.VariableLengthTransitionHistory;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.creator.TransitionBasedSignalCreator;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.period.PeriodExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.transition.TransitionHistoryExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.Parameters;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.math.Correlation;
import java.util.ArrayList;
import java.util.List;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 * 
 * The algorithm extracts the signal by grouping Transition with almost the
 * same temporal position together.
 * Furthermore this implementation correlates the extracted signal with
 * the TransitionHistory to estimate the quality of the extracted signal.
 */
public class TransitionBasedSignalExtractor extends AbstractSignalExtractor {

    /** The minimal required number of observations. */
    public final int minObservations = 50;
    
    /** The maximal allowed number of observations. */
    public final int maxObservations = 200;
    
    /** The required quality of the extracted signal. */
    private float reliability;
    
    /** 
     * If the duration between two events is below this threshold it is 
     * considered as noise. 
     */
    private int threshold;
    
    /** Stores the period of the signal. */
    private int period;
    
    /** Stores the candidate transitions of the signal. */
    private List<Transition> transitions;
    
    /*
     * Stores the transition history to compare the extracted signal with it.
     */
    private TransitionHistory compare;
    
    /** The relative difference from the start time. */
    private double min;
    
    /**
     * Creates a new instance of a TransitionBasedSignalExtractor.
     */
    public TransitionBasedSignalExtractor(ParameterManager parameters, 
                                          FeatureManager features, 
                                          AEChip chip) {
        super(Features.Transition, parameters, features, chip);
        
        this.init();
        this.reset();
    }
    
    @Override
    public void init() {
        super.init();
        
        this.creator = new TransitionBasedSignalCreator();
        
        this.transitions = new ArrayList<Transition>();
        this.compare = new VariableLengthTransitionHistory();
    }
    
    @Override
    public void reset() {
        super.reset();
        
        this.parameterUpdate();
        
        this.signal.reset();
        
        this.period = 0;
        
        this.transitions.clear();
        this.compare.reset();
        
        this.min = Double.MAX_VALUE;
    }
    
    /*
     * The algorithm extracts the signal by grouping Transition with almost 
     * the same temporal position together.
     * Furthermore this implementation correlates the extracted 
     * signal with the TransitionHistory to estimate the quality of the 
     * extracted signal.
     * 
     * @param timestamp The timestamp of the current time.
     */
    @Override
    public void update(int timestamp) {
        Transition transition = ((TransitionHistoryExtractor)this.features.get(Features.Transition)).getTransition();
        
        PeriodExtractor pe = ((PeriodExtractor)this.features.get(Features.Period));
        if (!pe.isValid()) return;
        
        int candidate = pe.getPeriod();
        if (this.period != candidate) {
            if (candidate > this.threshold) {
                this.reset();
                
                this.period = candidate;
            }
        }
        
        if (this.period > 0) {
            int offset;
            if (this.transitions.size() > 0) {
                offset = this.transitions.get(0).time;
            }
            else {
                offset = transition.time;
            }

            this.compare.add(transition);

            if (offset <= transition.time) {
                /*
                 * the socre is minimal if the Transition corresponds to 
                 * the start of the signal.
                 */
                int relative = transition.time - offset;
                double score = Math.pow(((relative) - this.period) / 1000.0, 2.0);

                /*
                 * if the Transition is not the start of the signal just
                 * continoue, otherwise store the extracted time period
                 * and add it to the super positioned signal.
                 */
                if (this.min > score) {
                    this.min = score;
                }
                else {
                    this.timestamp = timestamp;
                    this.min = Double.MAX_VALUE;

                    if (this.transitions.size() > 0) {
                        offset = this.transitions.get(0).time;

                        Transition last = this.transitions.get(this.transitions.size() - 1);
                        for (int j = 1; j < this.transitions.size(); j++) {
                            this.creator.add(this.transitions.get(j).time - offset, this.transitions.get(j).state);
                        }
                        this.signal = this.creator.getSignal();

                        this.transitions.clear();
                        this.transitions.add(last);
                        offset = this.transitions.get(0).time;

                        if (this.compare.getSize() > this.minObservations) {
                            /*
                             * check whether the extracted signal is valid or not.
                             */
                            if (this.signal.getSize() >= this.compare.getSize() / 2 ||
                                    this.signal.getSize() % 2 == 1) {

                                    this.reset();
                            }
                            else {
                                /*
                                 * estimates the quality of the signal by correlacting
                                 * the signal with the TransitionHistory.
                                 */
                                Correlation c = Correlation.getInstance();
                                float match = c.correlation(c.getItem(this.compare, 0, Math.min(this.compare.getSize() - 1, 20)), 
                                                            c.getItem(this.signal, -1, Integer.MAX_VALUE));
                                if (match > this.reliability) {
                                    this.signal = new SimpleSignal(this.signal);
                                    
                                    /*
                                     * notifies the other extractors about the new signal.
                                     */
                                    this.features.getNotifier().notify(this.feature, timestamp);
                                    
                                    this.store();

                                    return;
                                }
                            }
                        }
                    }
                }
                this.transitions.add(transition);
            }
            
            if (this.compare.getSize() > this.maxObservations) {
                this.reset();
            }
        }
    }
    
    @Override
    public void delete() {
        super.delete();
        
        this.features.delete(Features.Period);
    }
    
    @Override
    public void store() {
        this.delete();
        
        new SignalExtractorStorage(this.signal, this.parameters, this.features, this.chip);
        this.features.add(Features.Improver);
    }
    
    @Override
    public void parameterUpdate() {
        super.parameterUpdate();
        
        if (Parameters.getInstance().hasKey(Parameters.NOISE_DURATION)) this.threshold = Parameters.getInstance().getAsInteger(Parameters.NOISE_DURATION);
        if (Parameters.getInstance().hasKey(Parameters.SIGNAL_QUALITY)) this.reliability = Parameters.getInstance().getAsFloat(Parameters.SIGNAL_QUALITY);
    }
}
