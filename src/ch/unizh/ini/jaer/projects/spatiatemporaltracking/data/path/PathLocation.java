/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.path;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.Vector;

/**
 *
 * @author matthias
 * 
 * This class stores a location in space. It is used to form a path.
 */
public class PathLocation {
    
    /** Stores the location. */
    public Vector location;
    
    /** Stores the timestamp of the creation of the location. */
    public int timestamp;
    
    /**
     * Creates a new instance of PathLocation.
     * 
     * @param location The location on the path..
     * @param timestamp The timestapm of the creation of the location.
     */
    public PathLocation(Vector location, int timestamp) {
        this.location = location;
        this.timestamp = timestamp;
    }
}
