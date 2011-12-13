/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.phase;

/**
 *
 * @author matthias
 * 
 * This type of extract tries to determine the phase of the signal in
 * the extracted transition history.
 */
public interface PhaseExtractor {
    
    /**
     * Gets the phase of the signal.
     * 
     * @return The phase of the signal.
     */
    public int getPhase();
    
    /**
     * Gets true if the phase of the signal was found, false otherwise.
     * 
     * @return True if the phase of the signal was found, false otherwise.
     */
    public boolean isFound();
}
