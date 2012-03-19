/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.event;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.FeatureClusterStorage;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.creation.ClusterCreation;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;

/**
 *
 * @author matthias
 * 
 * The concrete implementations of this abstract class have to assign a 
 * TypedEvent to a FeatureExtractable.
 */
public abstract class AbstractEventAssignment implements EventAssignment {
    
    /** Maintains all active FeatureClusters. */
    protected FeatureClusterStorage storage;
    
    /** The object used to create new CandidateClusters. */
    protected ClusterCreation creation;
    
    /**
     * Creates a new AbstractEventAssignment.
     * 
     * @param manager The parameter manager maintaining the parameters.
     * @param storage The storage maintaining the clusters.
     * @param creation The instance used to create new CandidateClusters.
     */
    public AbstractEventAssignment(ParameterManager manager, 
                                   FeatureClusterStorage storage, 
                                   ClusterCreation creation) {
        manager.add(this);
        
        this.storage = storage;
        this.creation = creation;
        
        this.parameterUpdate();
    }
}
