/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.position;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.Vector;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.path.PathLocation;

/**
 *
 * @author matthias
 * 
 * The concrete implementations of this interface have to extract the position
 * out of the given events.
 */
public interface PositionExtractor {
    
    /**
     * Gets the position of the object.
     * 
     * @return The position of the object.
     */
    public Vector getPosition();
    
    /**
     * Gets the current location of the object.
     * 
     * @return The current location of the object.
     */
    public PathLocation getCurrentLocation();
    
    /**
     * Gets the previous location of the object.
     * 
     * @return The previous location of the object.
     */
    public PathLocation getPreviousLocation();
}