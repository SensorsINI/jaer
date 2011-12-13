/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.listener;

/**
 *
 * @author matthias
 */
public interface FeatureListener {
    
    /**
     * This method is used whenever the feature represented by this listener 
     * has to be updated.
     * 
     * @param timestamp The timestamp of the algorithm.
     */
    public void update(int timestamp);
}
