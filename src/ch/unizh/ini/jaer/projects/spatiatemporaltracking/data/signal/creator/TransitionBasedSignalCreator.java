/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.creator;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.Signal;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.SimpleSignal;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.Transition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 *
 * @author matthias
 * 
 * Computes the signal out of the added Transition. The class has to remove 
 * outliers in the set of transitions and considers only Transition having 
 * enough support by other Transitions.
 */
public class TransitionBasedSignalCreator extends AbstractSignalCreator {
    
    
    /** Defines the support can deviate from the global maximum */
    public final float deviation = 0.5f;
    
    /** Defines the temporal resolution. */
    public final int resolution = 100;
    
    /** Defines the squared temporal resolution. */
    private int squaredResolution;
    
    /** Stores the Transitions from an off- to an on-state and from an on- to an off-state. */
    private List<List<SupportedTransition>> transitions;
    
    /**
     * Creates a new TransitionBasedSignal.
     */
    public TransitionBasedSignalCreator() {
        this.init();
        this.reset();
    }
    
    @Override
    public void init() {
        super.init();
        
        this.squaredResolution = (int)Math.pow(this.resolution, 2.0);
        
        this.transitions = new ArrayList<List<SupportedTransition>>();
        for (int i = 0; i < 2; i++) {
            this.transitions.add(new ArrayList<SupportedTransition>());
        }
    }
    
    @Override
    public void reset() {
        super.reset();
        
        for (int i = 0; i < this.transitions.size(); i++) {
            this.transitions.get(i).clear();
        }
    }

    @Override
    public void add(Transition t) {
        this.add(t.time, t.state);
    }

    @Override
    public void add(int time, int state) {
        if (state >= 0 && state < this.transitions.size()) {
            double min = Double.MAX_VALUE;
            int best = -1;
            
            for (int i = 0; i < this.transitions.get(state).size(); i++) {
                double difference = Math.pow(this.transitions.get(state).get(i).time - time, 2.0);
                if (min > difference) {
                    min = difference;
                    best = i;
                }
            }
            
            int affected = -1;
            if (min < this.squaredResolution) {
                affected = best;

                this.transitions.get(state).get(best).time = this.transitions.get(state).get(best).time * this.transitions.get(state).get(best).support + time;
                this.transitions.get(state).get(best).support++;
                this.transitions.get(state).get(best).time /= this.transitions.get(state).get(best).support;
            }
            else {
                this.transitions.get(state).add(new SupportedTransition(time));
            }
            
            /*
             * Changes the set of transition until a stable state is reached.
             */
            while (affected >= 0) {
                int index = affected;
                affected = -1;
                
                min = Double.MAX_VALUE;
                for (int i = 0; i < this.transitions.get(state).size(); i++) {
                    if (i != index) {
                        double difference = Math.pow(this.transitions.get(state).get(index).time - this.transitions.get(state).get(i).time, 2.0);
                        if (min > difference) {
                            min = difference;
                            best = i;
                        }
                    }
                }
                if (min < this.squaredResolution) {
                    /*
                     * merge if two SupportedTransitions are close together.
                     */
                    SupportedTransition s = this.transitions.get(state).remove(best);
                    if (best < index) index--;
                    
                    this.transitions.get(state).get(index).time = this.transitions.get(state).get(index).time * this.transitions.get(state).get(index).support + s.time * s.support;
                    this.transitions.get(state).get(index).support += s.support;
                    this.transitions.get(state).get(index).time /= this.transitions.get(state).get(index).support;
                    
                    affected = index;
                }
            }
        }
    }

    /**
     * Based on the list of possible transitions the signal has to be extracted.
     * 
     * @return The signal based on the possible transitions.
     */
    @Override
    public Signal getSignal() {
        Signal s = new SimpleSignal();
        
        int max = 0;
        for (int state = 0; state < this.transitions.size(); state++) {
            for (int j = 0; j < this.transitions.get(state).size(); j++) {
                if (max < this.transitions.get(state).get(j).support) {
                    max = this.transitions.get(state).get(j).support;
                }
            }
        }
        max = (int)Math.floor(max * this.deviation);
        for (int state = 0; state < this.transitions.size(); state++) {
            for (int j = 0; j < this.transitions.get(state).size(); j++) {
                if (this.transitions.get(state).get(j).support > max) {
                    s.add(this.transitions.get(state).get(j).getTimeAsInt(), state);
                }
            }
        }
        s.update();
        
        return s;
    }
    
    /**
     * Stores the values of a supported Transition.
     */
    public class SupportedTransition {
        /** Number of transitions represented by this instance. */
        public int support;
        
        /** Temporal position of the instance. */
        public double time;
        
        /**
         * Creates a new SupportedTransition.
         * 
         * @param time The initial temporal position.
         */
        public SupportedTransition(int time) {
            this.support = 1;
            
            this.time = time;
        }
        
        /**
         * Gets the time as an integer.
         * 
         * @return The time as an integer.
         */
        public int getTimeAsInt() {
            return (int)Math.round(this.time);
        }
    }
    
    /**
     * Defines a linear ordering on a set of elements of type SupportedTransition.
     */
    public class SupportedTransitionComperator implements Comparator<SupportedTransition> {

        @Override
        public int compare(SupportedTransition o1, SupportedTransition o2) {
            return (int)Math.signum(o1.time - o2.time);
        } 
    }
}
