/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.CircularList;

/**
 *
 * @author matthias
 * 
 * Stores a fixed number of Transitions of a signal.
 */
public class FixedLengthTransitionHistory extends AbstractTransitionHistory {

    /** Defines the number of Transitions stored in the History. */
    private int length;
    
    /** Stores the Transitions in a circular list. */
    private CircularList<Transition> transitions;
    
    /**
     * Creates a new FixedLengthTransitionHistory.
     * 
     * @param length The length of the TransitionHistory.
     */
    public FixedLengthTransitionHistory(int length) {
        this.length = length;
        
        this.transitions = new CircularList<Transition>(this.length);
        
        this.reset();
    }
    
    @Override
    public void add(Transition t) {
        this.transitions.add(t);
    }
    
    @Override
    public void add(int time, int state) {
        this.add(new Transition(time, state));
    }

    @Override
    public Transition getTransition(int index) {
        return this.transitions.get(index);
    }
    
    @Override
    public boolean isEmpty() {
        return this.transitions.getSize() == 0;
    }

    @Override
    public int getSize() {
        return this.transitions.getSize();
    }
    
    @Override
    public void reset() {
        this.transitions.reset();
    }
}
