/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.improver;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.Signal;

/**
 *
 * @author matthias
 * 
 * This type of extractor has to improve the quality of the signal after a 
 * rough estimation of the signal was computed.
 */
public interface SignalImproverExtractor {
    
    /*
     * Gets the signal of the observed object based on its assigned events.
     * 
     * @return The signal of the observed object.
     */
    public Signal getSignal();
}
