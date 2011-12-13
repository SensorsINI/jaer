/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.factory;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.FeatureExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;

/**
 *
 * @author matthias
 * 
 * This interface provides methods to create new FeatureExtractors.
 */
public interface FeatureExtractorFactory {
    
    /*
     * Adds the FeatureExtractor according to the given identifier to the given
     * instance of the interface FeatureExtractable.
     * 
     * @param features The instance used to manage the new extractor.
     * 
     * @return The new created FeatureExtractor.
     */
    public FeatureExtractor addFeature(FeatureManager features, Features feature);
}
