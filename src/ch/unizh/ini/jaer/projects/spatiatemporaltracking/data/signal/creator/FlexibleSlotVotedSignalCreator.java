/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.creator;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.Signal;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.SimpleSignal;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.Transition;
import java.util.Arrays;

/**
 *
 * @author matthias
 * 
 * Provides a Signal consisting of a variable amount of time slots. For each
 * timeslot the class votes for the state at the particular time. Instead of 
 * storing the Transitions the class stores the state of the signal based
 * on the voted time slots.
 */
public class FlexibleSlotVotedSignalCreator extends AbstractSignalCreator {

    /** Defines the maximum number of votes stored. */
    public final int threshold = 100;
    
    /** Defines the temporal resolution of the time slots. */
    public final float resolution = 100;
    
    /** Stores the votes for each time slot. */
    private int[] votes;
    
    /** Stores the number of iterations the slot was not used. */
    private int[] times;
    
    /** Contains the timestamp of the last added Transition. */
    private int last;
    
    /**
     * Creates a new FlexibleSlotVotedSignalCreator.
     */
    public FlexibleSlotVotedSignalCreator() {
        this.init();
        this.reset();
    }
    
    @Override
    public void init() {
        super.init();
        
        this.votes = new int[1];
        this.times = new int[this.votes.length];
    }
    
    @Override
    public void reset() {
        super.reset();
        
        Arrays.fill(this.votes, 0);
        Arrays.fill(this.times, 0);
        this.last = 0;
    }
    
    /**
     * Resizes the observed interval for the SignalCreator.
     * 
     * @param to The new size of the observed interval.
     */
    private void resize(int to) {
        int length = to;
        
        if (this.votes.length < length) {
            this.votes = Arrays.copyOf(this.votes, length * 2);
            this.times = Arrays.copyOf(this.times, this.votes.length);
        }
    }
    
    @Override
    public void add(Transition t) {
        this.add(t.time, t.state);
    }

    /**
     * Votes in each time slot affected by the added Transition for the state.
     * 
     * @param time The time of the Transition.
     * @param state The state of the signal after the Transition.
     */
    @Override
    public void add(int time, int state) {
        if (time < 0) return;
        
        if (time <= this.last) {
            this.last = time;
            return;
        }
        
        int start = Math.round(this.last / this.resolution);
        int end = Math.round(time / this.resolution);
        
        this.resize(end);
        
        for (int i = 0; i < this.times.length; i++) {
            if (i < end) this.times[i] = 0;
            this.times[i]++;
        }
        
        if (state == 1) {
            for (int i = start; i < end; i++) {
                if (this.votes[i] < threshold) this.votes[i]++;
            }
        }
        else {
            for (int i = start; i < end; i++) {
                if (this.votes[i] > -threshold) this.votes[i]--;
            }
        }
        this.last = time;
    }
    
    /**
     * Computes the state at the time slot corresponding to the given index.
     * 
     * @param index The index of the time slot.
     * @return The state at the time slot corresponding to the given index.
     */
    private int getState(int index, int N) {
        /*
         * Computes the state at the time slot. In the case where signum returns 
         * zero no majority exists.
         */
        switch((int)Math.signum(this.votes[index % N])) {
            case 1:
                return 1;
            case -1:
                return 0;
            default:
                return 0;
        }
    }

    /**
     * Based on the list of states the signal has to be extracted.
     * 
     * @return The signal based on the list of states.
     */
    @Override
    public Signal getSignal() {
        Signal s = new SimpleSignal();
        
        int N = 0;
        while (N < this.times.length && this.times[N] < 5) {
            N++;
        }
        
        int start = 0;
        int end = 0;
        
        while (end < N) {
            while (end < N && this.getState(start, N) == this.getState(end, N)) {
                end++;
            }
            s.add(Math.round(end * this.resolution), this.getState(end, N));
            start = end;
        }
        s.update();
        
        return s;
    }
}
