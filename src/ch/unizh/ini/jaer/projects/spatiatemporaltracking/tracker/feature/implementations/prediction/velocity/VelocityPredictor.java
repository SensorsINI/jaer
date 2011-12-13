/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.prediction.velocity;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.Vector;

/**
 *
 * @author matthias
 * 
 * This predictor has to predict the velocity of the observed object.
 */
public interface VelocityPredictor {
    
    /**
     * Gets the predicted velocity of the observed object at the given time.
     * 
     * @param timestamp The time in the future to predict the velocity.
     * @return  The predicted velocity.
     */
    public Vector getVelocity(int timestamp);
    
    /**
     * Gets the predicted velocity of the observed object at the current time
     * of the predictor plus the given time.
     * 
     * @param delta The time to add to the predictor.
     * @return  The predicted velocity.
     */
    public Vector getVelocityRelative(int delta);
    
    /**
     * Gets the velocity which is used as reference. This means the velocity
     * without any correction using the acceleration.
     * 
     * @return  The reference velocity.
     */
    public Vector getReferenceVelocity();
}
