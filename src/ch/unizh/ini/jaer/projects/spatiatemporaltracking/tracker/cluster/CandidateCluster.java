/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster;

/**
 *
 * @author matthias
 */
public interface CandidateCluster extends FeatureCluster {
    
    /**
     * Converts the CandidateCluster into a FeatureCluster.
     * 
     * @param cluster The new FeatureCluster which will represent the
     * CandidateCluster.
     */
    public void convert(FeatureCluster cluster);
}
