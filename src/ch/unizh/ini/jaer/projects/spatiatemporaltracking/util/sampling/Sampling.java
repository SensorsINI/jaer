/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.sampling;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.TransitionHistory;

/**
 *
 * @author matthias
 * 
 * The class samples a transition history to discretize the signal.
 */
public class Sampling {
    /** The second represented in micro-seconds. */
    public static final int SECOND = 1000000;
    
    /** Stores the instance of the class. */
    private static Sampling instance = null;
    
    /**
     * Creates a new Sampling.
     */
    private Sampling() {
        
    }
    
    /**
     * Gets the instance of this class. It uses the singelton principle.
     * 
     * @return The instance of this class.
     */
    public static Sampling getInstance() {
        if (Sampling.instance == null) {
            Sampling.instance = new Sampling();
        }
        return Sampling.instance;
    }
    
    /**
     * Samples the transition history.
     * 
     * @param transitions The transition history to sample.
     * @param from The start of the sampling process.
     * @param to The end of the sampling process.
     * @param frequency The sampling frequency used to sample the transition
     * history.
     * @return The samples of the transition history.
     */
    public int[] samplingFrequencyBased(TransitionHistory transitions, int from, int to, float frequency) {
        int step = Math.round(SECOND / frequency);
        
        return this.samplingStepBased(transitions, from, to, step);
    }
    
    /**
     * Samples the transition history.
     * 
     * @param transitions The transition history to sample.
     * @param from The start of the sampling process.
     * @param to The end of the sampling process.
     * @param step The step size used to sample the transition history.
     * @return The samples of the transition history.
     */
    public int[] samplingStepBased(TransitionHistory transitions, int from, int to, int step) {      
        int [] samples = new int[(to - from) / step];
        int timestamp = from;
        int index = 0;
        for (int i = 0; i < samples.length; i++) {
            while (transitions.getTransition(index).time < timestamp) {
                index++;
            }
            if (index == 0) {
                index = 0;
            }
            samples[i] = transitions.getTransition(index - 1).state;
            timestamp += step;
        }
        return samples;
    }
}
