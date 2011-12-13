/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.prediction.acceleration;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.Vector;

/**
 *
 * @author matthias
 * 
 * Estimates the acceleration of the observed object.
 */
public interface AccelerationPredictor {
    
    /**
     * Gets the acceleration of the observed object.
     * 
     * @return The acceleration of the observed object.
     */
    //public Vector getAcceleration();
    
    /**
     * Gets the old acceleration of the observed object.
     * 
     * @return The old acceleration of the observed object.
     */
    //public Vector getPreviousAcceleration();
    
    /**
     * Returns true, if the velocity of the observed object has to be reseted,
     * false if the correction with th acceleration is enough.
     * 
     * @return True, if the velocity of the observed object has to be reseted,
     * false if the correction with th acceleration is enough.
     */
    //public boolean hasHighDeviation();
    
    /**
     * Computes the new velocity using the predicted acceleration and the given
     * velocity and time interval.
     * 
     * @param velocity The velocity to which the acceleration has to be added.
     * @param delta The time interval for the acceleration.
     * 
     * @return The new velocity.
     */
    public Vector computeVelocity(Vector velocity, int delta);
}
