/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 *
 * @author matthias
 * 
 * Stores the Transitions of an observed signal. The stored Transitions are 
 * ordered according their timestamp.
 */
public class SimpleSignal extends AbstractSignal {
    
    /** Stores the Transitions of the observed signal. */
    private List<Transition> signal;
    
    /**
     * Creates a new SimpleSignal.
     */
    public SimpleSignal() {
        this.init();
        this.reset();
    }
    
    /**
     * Creates a new SimpleSignal.
     */
    public SimpleSignal(Signal signal) {
        this.init();
        this.reset();
        
        for (int i = 0; i < signal.getSize(); i++) {
            this.add(signal.getTransition(i));
        }
        this.update();
    }
    
    @Override
    public void init() {
        super.init();
        
        this.signal = new ArrayList<Transition>();
    }
    
    @Override
    public void reset() {
        super.reset();
        
        this.signal.clear();
    }
    
    @Override
    public void add(int time, int state) {
        this.add(new Transition(time, state));
    }

    /**
     * Adds the Transition to the signal. The timestamp of the Transition is
     * used to define an ordering on the Transitions.
     * 
     * @param transition The Transition to add.
     */
    @Override
    public void add(Transition transition) {
        this.signal.add(transition);
    }

    @Override
    public void update() {
        Transition[] t = this.signal.toArray(new Transition[0]);
        Arrays.sort(t, new TransitionComparator());
        
        for (int i = 0; i < this.signal.size(); i++) {
            this.signal.set(i, t[i]);
        }
        
        for (int i = 0; i < this.signal.size(); i++) {
            int last = i;
            int current = (i + 1) % this.signal.size();
            
            if (this.signal.get(last).state == this.signal.get(current).state) {
                this.signal.remove(current);
                i--;
            }
        }
        
        if (this.isEmpty()) {
            this.reset();
        }
        else {
            this.period = this.signal.get(this.signal.size() - 1).time;
        }
    }

    @Override
    public Transition getOriginalTransition(int index) {
        return this.signal.get(index);
    }
    
    @Override
    public Transition getTransition(int index) {
        if (index == -1) return new Transition(0, this.signal.get(this.signal.size() - 1).state);
        
        int rest = index % this.signal.size();
        int time = this.signal.get(this.signal.size() - 1).time * ((index - rest) / this.signal.size());
        time += this.signal.get(rest).time;

        return new Transition(time, this.signal.get(rest).state);
    }
    
    @Override
    public boolean isEmpty() {
        return this.signal.isEmpty();
    }

    @Override
    public int getSize() {
        return this.signal.size();
    }

    @Override
    public boolean isValid() {
        return this.signal.size() % 2 == 0;
    }
    
    /**
     * Is used to define a linear ordering on the set of Transitions.
     */
    public class TransitionComparator implements Comparator<Transition> {

        @Override
        public int compare(Transition o1, Transition o2) {
            return (int)Math.signum(o1.time - o2.time);
        }
    }
}
