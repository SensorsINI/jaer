/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.assignment;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.assignable.AssignableCluster;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.ParameterListener;

/**
 *
 * @author matthias
 * 
 * The interface provides methods to assign an object to a TemporalPattern.
 */
public interface ClusterAssignment extends ParameterListener {
    
    /*
     * Assigns an object to a TemporalPattern.
     * 
     * @param f The object to assign to a TemporalPattern.
     */
    public void assign(AssignableCluster a);
}
