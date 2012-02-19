/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.lifetime;

/**
 *
 * @author matthias
 * 
 * The concrete implementations of this interface have to extract the lifetime
 * of the observed object out of the assigned events.
 */
public interface LifetimeExtractor {
    
    /**
     * Gets the lifetime of the observed object.
     * 
     * @return The lifetime of the object.
     */
    public int getLifetime();
    
    /** 
     * Gets the difference between the time of the creation of the observed
     * object and the time the tracker started to work.
     * 
     * @return The difference between the creation time and the existence of the
     * tracker.
     */
    public int getCreationTime();
}
