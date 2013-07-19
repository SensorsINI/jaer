/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.temporalpattern;

import java.util.ArrayList;
import java.util.List;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.Color;

/**
 *
 * @author matthias
 * 
 * Stores the registred TemporalPatterns.
 */
public class TemporalPatternStorage {
    /** Stores the instance of the class. */
    private static TemporalPatternStorage instance = null;
    
    /** Stores the registered temporal patterns. */
    private List<TemporalPattern> patterns;
    
    /**
     * Gets the instance of this class. It uses the singelton principle.
     * 
     * @return The instance of this class.
     */
    public static TemporalPatternStorage getInstance() {
        if (instance == null) instance = new TemporalPatternStorage();
        
        return instance;
    }
    
    /**
     * Creates a new TemporalPatternStorage.
     */
    private TemporalPatternStorage() {
        this.patterns = new ArrayList<TemporalPattern>();
    }
    
    /**
     * Resets the storage.
     */
    public void reset() {
        this.patterns.clear();
        Color.reset();
    }
    
    /**
     * Adds the given temporal pattern to the list of registered temporal
     * patterns.
     * 
     * @param pattern The temporal pattern to add.
     */
    public void add(TemporalPattern pattern) {
        this.patterns.add(pattern);
    }
    
    /**
     * Gets the list of the registered temporal patterns.
     * 
     * @return The list of the registered temporal patterns.
     */
    public List<TemporalPattern> getPatterns() {
        return this.patterns;
    }
    
    /**
     * Adds some predefined generic temporal patterns to the list.
     */
    public void addDefaults() {
        byte[] squared = {0, 1};
        byte[] arb = {1, 0, 1, 1, 0, 0};
        
        this.add(new GenericTemporalPattern(arb, 30,  "arb @ 30Hz"));
        this.add(new GenericTemporalPattern(arb, 50,  "arb @ 50Hz"));
        this.add(new GenericTemporalPattern(squared, 20,  "s @ 20Hz"));
        this.add(new GenericTemporalPattern(squared, 70,  "s @ 70Hz"));
    }
}
