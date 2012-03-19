/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.math;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.Signal;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.TransitionHistory;

/**
 *
 * @author matthias
 * 
 * The class provides methods to compute the cross-correlation between two
 * signals and the correlation between two transition histories.
 */
public class Correlation {
    
    /** Stores the instance of the class. */
    private static Correlation instance = null;
    
    /**
     * Computes the cross-correlation between two signals.
     * 
     * @param s1 The first signal.
     * @param s2 The second signal.
     * @return The cross-correlation between the two given signals.
     */
    public float crossCorrelation(Signal s1, Signal s2) {
        if (s1.getSize() == 0 || s2.getSize() == 0) return 0;
        
        if (s1.getSize() < s2.getSize()) {
            Signal t = s1;
            s1 = s2;
            s2 = t;
        }
        
        int stop1 = s1.getSize() - 1;
        int stop2 = s2.getSize() - 1;
        
        while (s1.getTransition(stop1).time < s2.getTransition(s2.getSize() - 1).time) {
            stop1++;
        }
        while (s2.getTransition(stop2).time < s1.getTransition(s1.getSize() - 1).time) {
            stop2++;
        }
        
        float max = -1;
        for (int phase = 0; phase < s2.getSize(); phase++) {
            float score = this.correlation(new CorrelationItem(s1, -1, stop1), new CorrelationItem(s2, -1 + phase, stop2 + phase));
            if (max < score) max = score;
        }
        return max;
    }
    
    /**
     * Computes the correlation between two transition histories.
     * 
     * @param c1 The first CorrelationItem specifing the transition history, its
     * duration and the phase.
     * @param c2 The second CorrelationItem.
     * @return  The value of the correlation between the two transition
     * histories.
     */
    public float correlation(CorrelationItem c1, CorrelationItem c2) {
        if (c1.history.getSize() <= 0 || c2.history.getSize() <= 0) return 0;
        
        float score = 0;
        
        int index1 = c1.start;
        int index2 = c2.start;
        
        int reference = c1.history.getTransition(index1).time;
        int mapping =  c2.history.getTransition(index2).time - c1.history.getTransition(index1).time;
        
        int last = reference;
        int current = 0;
        while (index1 < c1.end && index2 < c2.end) {
            boolean equalState = (c1.history.getTransition(index1).state == c2.history.getTransition(index2).state);
            
            // get next transition
            if (c1.history.getTransition(index1 + 1).time < c2.history.getTransition(index2 + 1).time - mapping) {
                index1++;
                current = c1.history.getTransition(index1).time;
            }
            else {
                index2++;
                current = c2.history.getTransition(index2).time - mapping;
            }
            
            // weight agreement
            int diff = current - last;
            if (diff >= 0) {
                if (equalState) {
                    score += diff;
                }
                else {
                    score -= diff;
                }
            }
            last = current;
        }
        return score / (current - reference);
    }
    
    /**
     * Generates the CorrelationItem according to the given data.
     * 
     * @param history The transition history used.
     * @param start The start of the transition history.
     * @param end The end of the transition history.
     * @return 
     */
    public CorrelationItem getItem(TransitionHistory history, int start, int end) {
        return new CorrelationItem(history, start, end);
    }
    
    /**
     * Stores the data used to compute the correlation.
     */
    public class CorrelationItem {
        
        /** The transition history used. */
        public TransitionHistory history;
        
        /** The start of the transition history. */
        public int start;
        
        /** The end of the transition history. */
        public int end;
        
        /**
         * Creates a new CorrelationItem.
         * 
         * @param history The transition history used.
         * @param start The start of the transition history.
         * @param end The end of the transition history.
         */
        private CorrelationItem(TransitionHistory history, int start, int end) {
            this.history = history;
            this.start = start;
            this.end = end;
        }
    }
    
    /**
     * Gets the instance of this class. It uses the singelton principle.
     * 
     * @return The instance of this class.
     */
    public static Correlation getInstance() {
        if (Correlation.instance == null) {
            Correlation.instance = new Correlation();
        }
        return Correlation.instance;
    }
}
