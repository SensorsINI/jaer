/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.autocorrelation;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.correlation.CorrelationStorage;

/**
 *
 * @author matthias
 * 
 * The concrete implementations of this interface have to extract the auto 
 * correlation function out of the data provided by the observed object. 
 */
public interface AutoCorrelationExtractor {
    
    /**
     * Gets the cross-correlation computed out of the signal extracted from
     * the observed object.
     * 
     * @return The cross-correlation computed out of the signal.
     */
    public CorrelationStorage getCorrelation();
}
