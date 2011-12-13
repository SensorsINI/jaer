/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster;

import java.util.List;

/**
 *
 * @author matthias
 * 
 * This interface provides methods to maintain a set of FeatureClusters.
 */
public interface FeatureClusterStorage {
    /*
     * Returns a list containing all FeatureClusters maintained by a particular
     * instance.
     * 
     * @return List containing all FeatureClusters.
     */
    public List<FeatureCluster> getClusters();
    
    /*
     * Adds a FeatureCluster to the instance.
     * 
     * @return The added FeatureCluster.
     */
    public FeatureCluster addCluster();
    
    /*
     * Adds a CandidateCluster to the instance.
     * 
     * @return The added CandidateCluster.
     */
    public FeatureCluster addCandidateCluster();
    
    /*
     * Removes a FeatureCluster from the object and deletes it.
     * 
     * @param cluster The FeatureCluster to remove.
     */
    public void delete(FeatureCluster cluster);
    
    /**
     * Gets the number of FeatureClusters.
     * 
     * @param The number of FeatureClusters.
     */
    public int getClusterNumber();
    
    /**
     * Gets the number of CandidateClusters.
     * 
     * @return The number of CandidateClusters.
     */
    public int getCandidateClusterNumber();
}