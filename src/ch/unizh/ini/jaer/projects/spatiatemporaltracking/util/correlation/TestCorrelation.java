/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.correlation;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.Signal;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.SimpleSignal;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.Transition;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.TransitionHistory;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.VariableLengthTransitionHistory;

/**
 *
 * @author matthias
 * 
 * Tests the class KMeans and its provided k-mean clustering algorithm,
 */
public class TestCorrelation {
    public static boolean control(float value, float expected) {
        if (value != expected) {
            System.out.println("FATAL ERROR: expected " + expected + " and found " + value + ".");
            return false;
        }
        return true;
    }
    
    public static void main(String [] args) {
        Signal s1 = new SimpleSignal();
        s1.add(new Transition(1000, 0));
        s1.add(new Transition(2000, 1));
        if (!control(Correlation.getInstance().crossCorrelation(s1, s1), 1.0f)) return;
        System.out.println("Test 1 terminated succesfully...");
        
        Signal s2 = new SimpleSignal();
        s2.add(new Transition(2000, 0));
        s2.add(new Transition(4000, 1));
        if (!control(Correlation.getInstance().crossCorrelation(s1, s2), 0.0f)) return;
        System.out.println("Test 2 terminated succesfully...");
        
        Signal s3 = new SimpleSignal();
        s3.add(new Transition(3000, 0));
        s3.add(new Transition(4000, 1));
        if (!control(Correlation.getInstance().crossCorrelation(s2, s3), 0.5f)) return;
        System.out.println("Test 3 terminated succesfully...");
        
        
        Correlation c = Correlation.getInstance();
        TransitionHistory h1 = new VariableLengthTransitionHistory();
        h1.add(new Transition(10000, 1));
        h1.add(new Transition(11000, 0));
        h1.add(new Transition(12000, 1));
        h1.add(new Transition(13000, 0));
        if (!control(c.correlation(c.getItem(h1, 0, h1.getSize() - 1), 
                                   c.getItem(s1, -1, Integer.MAX_VALUE)), 
                                   1.0f)) return;
        System.out.println("Test 4 terminated succesfully...");
        
        System.out.println("All tests terminated succesfully...");
    }
}
