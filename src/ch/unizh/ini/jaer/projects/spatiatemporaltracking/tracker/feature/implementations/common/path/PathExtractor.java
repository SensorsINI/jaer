/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.path;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.path.PathLocation;

/**
 *
 * @author matthias
 * 
 * The concrete implementations of this interface have to extract the path
 * of the observed object.
 */
public interface PathExtractor {
    
    /**
     * Returns the path of the observed object.
     * 
     * @return The path of the observed object.
     */
    public PathLocation getPath();
}
