/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal;

import java.util.Arrays;

/**
 *
 * @author matthias
 * 
 * Stores a variable number of Transitions of a signal.
 */
public class VariableLengthTransitionHistory extends AbstractTransitionHistory {
    
    /** Stores the Transitions in list. */
    private Transition[] transitions;
    
    /** The number of elements stored in the list. */
    private int N;
    
    /**
     * Creates a new VariableLengthTransitionHistory.
     */
    public VariableLengthTransitionHistory() {
        this(10);
    }
    
    /**
     * Creates a new VariableLengthTransitionHistory.
     * 
     * @param length The initial length of the TransitionHistory.
     */
    public VariableLengthTransitionHistory(int length) {
        this.transitions = new Transition[length];
        
        this.reset();
    }
    
    @Override
    public void reset() {
        this.N = 0;
    }
    
    @Override
    public void add(Transition t) {
        if (this.N >= this.transitions.length) {
            this.transitions = Arrays.copyOf(this.transitions, this.transitions.length * 2);
        }
        this.transitions[this.N] = t;
        
        this.N++;
    }
    
    @Override
    public void add(int time, int state) {
        this.add(new Transition(time, state));
    }

    @Override
    public Transition getTransition(int index) {
        return this.transitions[index];
    }
    
    @Override
    public boolean isEmpty() {
        return this.N == 0;
    }

    @Override
    public int getSize() {
        return this.N;
    }
}
