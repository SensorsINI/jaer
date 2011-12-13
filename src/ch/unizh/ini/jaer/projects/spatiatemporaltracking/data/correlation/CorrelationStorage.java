/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.correlation;

/**
 *
 * @author matthias
 * 
 * The Correlation stores a computed cross-correlation with its times and the
 * corresponding resuls.
 */
public class CorrelationStorage {
    /** Stores the values of the correlation. */
    private CorrelationItem[] items;
    
    /**
     * Creates a new Correlation.
     * 
     * @param items The values of the correlation.
     */
    public CorrelationStorage(CorrelationItem[] items) {
        this.items = items;
    }
    
    /**
     * Gets the computed value of the auto correlation function.
     * 
     * @return The values of the auto correlation function.
     */
    public CorrelationItem[] getItems() {
        return this.items;
    }
    
    /**
     * Gets the number of observations used to compute the cross-correlation.
     * 
     * @return The number of observations used to compute the cross-corrleation.
     */
    public int getObservations() {
        return this.items.length;
    }
}
