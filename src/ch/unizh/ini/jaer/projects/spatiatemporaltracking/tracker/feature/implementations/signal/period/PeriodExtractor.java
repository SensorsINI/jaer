/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.period;

/**
 *
 * @author matthias
 * 
 * The concrete implementations of this interface have to extract the period
 * of the signal of the observed object as well as its phase.
 */
public interface PeriodExtractor {
    
    /**
     * Gets the period of the signal.
     * 
     * @return The period of the signal.
     */
    public int getPeriod();
    
    /**
     * Gets true, if the the extractor has found a valid period, false 
     * otherwise.
     * 
     * @return Tue, if the the extractor has found a valid period, false 
     * otherwise
     */
    public boolean isValid();
}
