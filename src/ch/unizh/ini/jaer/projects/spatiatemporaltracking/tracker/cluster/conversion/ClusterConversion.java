/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.conversion;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.CandidateCluster;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterListener;

/**
 *
 * @author matthias
 */
public interface ClusterConversion extends ParameterListener {
    
    /**
     * Evalutes the given CandidateCluster and converts it if the support is
     * high enough.
     * 
     * @param cluster The CandidateCluster to evaluate.
     * 
     * @return True, if the CandidateCluster was converted, false otherwise.
     */
    public boolean convert(CandidateCluster cluster);
}
