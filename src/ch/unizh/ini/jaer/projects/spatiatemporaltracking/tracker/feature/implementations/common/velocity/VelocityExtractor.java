/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.velocity;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.Vector;

/**
 *
 * @author matthias
 * 
 * This type of extractor has to determine the current velocity of the 
 * observed object.
 */
public interface VelocityExtractor {
    
    /**
     * Gets the current velocity of the observed object.
     * 
     * @return The current velocity of the observed object.
     */
    public Vector getVelocity();
}
