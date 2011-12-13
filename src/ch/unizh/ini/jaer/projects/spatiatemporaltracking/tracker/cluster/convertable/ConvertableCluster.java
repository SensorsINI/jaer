/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.convertable;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.FeatureExtractable;

/**
 *
 * @author matthias
 */
public interface ConvertableCluster extends FeatureExtractable {
    
    /**
     * Gets true, if the cluster is convertable, false otherwise.
     * 
     * @return True, if the cluster is convertable, false otherwise.
     */
    public boolean isConvertable();
    
    /**
     * Converts the candidate cluster into a valid cluster.
     */
    public void convert();
}
