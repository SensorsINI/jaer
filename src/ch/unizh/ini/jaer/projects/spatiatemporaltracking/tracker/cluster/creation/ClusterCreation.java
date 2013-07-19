/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.creation;

import net.sf.jaer.event.TypedEvent;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterListener;

/**
 *
 * @author matthias
 * 
 * Implementations of this interface have to create new CandidateClusters based
 * on given input events.
 */
public interface ClusterCreation extends ParameterListener {
    
    /**
     * Creates a new CandidateCluster based on the given event.
     * 
     * @param e The event for which a new CandidateCluster has to be created.
     */
    public void create(TypedEvent e);
}
