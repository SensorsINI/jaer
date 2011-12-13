/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;

/**
 *
 * @author matthias
 * 
 * Implementations of this interface are able to extract features.
 */
public interface FeatureExtractable {
    
    /**
     * Gets the objects FeatureManager.
     * 
     * @return The FeatureManager of the object.
     */
    public FeatureManager getFeatures();
}
