/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.prediction.position;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.Vector;

/**
 *
 * @author matthias
 * 
 * This type of predictor has to predict the position of the observed object
 * in the future.
 */
public interface PositionPredictor {
    
    /**
     * Gets the position of the observed object in the future according to the
     * given timestamp.
     * 
     * @param timestamp The timestamp of the temporal position in the future.
     * 
     * @return The position of the observed object in the future.
     */
    public Vector getPosition(int timestamp);
    
    /**
     * Gets the position of the observed object in the future according to the
     * given timestamp. The timestamp is composed out of the time of the
     * predictor plus the given delta.
     * 
     * @param delta The time to add to the time of the predictor.
     * 
     * @return The position of the observed object in the future.
     */
    public Vector getPositionRelative(int delta);
}
