/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.correlation;

/**
 *
 * @author matthias
 * 
 * Stores one result of the correlation.
 */
public class CorrelationItem {
    
    /** The value used for the correlation. */
    public int value;
    
    /** The score of the correlation. */
    public float score;
    
    /**
     * Creates a new CorrelationItem.
     * 
     * @param value The value used for the correlation
     * @param score The score of the correlation.
     */
    public CorrelationItem(int value, float score) {
        this.value = value;
        this.score = score;
    }
}
