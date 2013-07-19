/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker;

import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.TypedEvent;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.Parameters;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.CandidateCluster;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.FeatureCluster;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.assignment.ClusterAssignment;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.assignment.ClusterCorrelationCostFunction;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.assignment.LearnableClusterCostAssignment;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.conversion.ClusterConversion;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.conversion.CostClusterConversion;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.conversion.LifetimeConversionCostFunction;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.creation.CostClusterCreation;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.creation.DistanceCreationCostFunction;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.deletion.ClusterDeletion;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.deletion.CostClusterDeletion;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.deletion.LifetimeDeletionCostFunction;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.merge.ClusterMerge;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.merge.DistanceMergeCostFunction;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.merge.LifeTimeCandidateClusterMerge;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.event.BoltzmanEventAssignment;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.event.BoundaryEventCostFunction;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.event.EventAssignable;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.event.EventAssignment;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.event.EventCostFunction;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.event.FastSpatialEventCostFunction;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.event.OccuranceEventCostFunction;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.event.PredictedBoundaryEventCostFunction;

/**
 *
 * @author matthias
 */
public class SimpleEventTracker extends AbstractTracker implements EventTracker {

    /** Assigns an event to a cluster. */
    private EventAssignment eventAssignment = null;
    
    /** Assigns a cluster to a temporal pattern. */
    private ClusterAssignment clusterAssignment = null;
    
    /** Converts a CandidateCluster into a FeatureCluster. */
    private ClusterConversion clusterConversion = null;
    
    /** Deletes clusters that are no longer used. */
    private ClusterDeletion clusterDeletion = null;
    
    /** Merges cluster if they are too close. */
    private ClusterMerge clusterMerge = null;
    
    /** The flag is used to indicated that the filter was reseted. */
    private boolean isReseted;
    
    /** Indicates whether the object was allready used or not. */
    private boolean isVirgin;
    
    /**
     * Creates a new instance of the class SimpleEventTracker.
     */
    public SimpleEventTracker() {
        this.init();
        this.reset();
    }
    
    @Override
    public void init() {
        super.init();
    }
    
    @Override
    public void reset() {
        super.reset();
        
        this.isReseted = true;
        
        this.eventAssignment = new BoltzmanEventAssignment(this, this, new FastSpatialEventCostFunction(), 
                                                                       CostFunctionFactory.getInstance().getSpatialEventCostFunction(), 
                                                                       CostFunctionFactory.getInstance().getTemporalEventCostFunction(), 
                                                                       new CostClusterCreation(this, this, new DistanceCreationCostFunction()));
        
        this.clusterAssignment = new LearnableClusterCostAssignment(this, this, new ClusterCorrelationCostFunction());
        this.clusterConversion = new CostClusterConversion(this, this, new LifetimeConversionCostFunction());
        this.clusterDeletion = new CostClusterDeletion(this, this, new LifetimeDeletionCostFunction());
        this.clusterMerge = new LifeTimeCandidateClusterMerge(this, this, new DistanceMergeCostFunction());
    }
    
    @Override
    public void track(EventPacket<?> in) {
        if (this.isReseted) {
            this.isReseted = false;
            this.isVirgin = true;
            
            while (!this.clusters.isEmpty()) {
                this.delete(this.clusters.get(0));
            }
        }
        
        if (in.isEmpty()) return;
        
        if (this.isVirgin) {
            this.isVirgin = false;
            this.first = in.getFirstTimestamp();
        }
        
        /*
         * assigns each EventGroup to the best matching FeatureCluster
         */
        int timestamp = 0;
        for (Object o : in) {
            TypedEvent e = (TypedEvent)o;
            
            timestamp = e.timestamp;
            this.eventAssignment.assign(e);
        }
        
        /*
         * updates all FeatureClusters.
         */
        for (FeatureCluster cluster : this.clusters) {
            cluster.packet(timestamp);
        }
        
        /*
         * merges clusters that are too close
         */
        for (int i = 0; i < this.clusters.size(); i++) {
            for (int j = i + 1; j < this.clusters.size(); j++) {
                if (this.clusterMerge.merge(this.clusters.get(i), this.clusters.get(j))) {
                    i = 0;
                    j = this.clusters.size();
                }
            }
        }
        
        /*
         * converts CandidateClusters to FeatureCluster if the criterions are
         * satisfied.
         */
        for (int i = 0; i < this.clusters.size(); i++) {
            if (this.clusters.get(i).isCandidate()) {
                if (this.clusterConversion.convert((CandidateCluster)this.clusters.get(i))) {
                    i--;
                }
            }
        }
        
        /*
         * deletes unused FeatureClusters.
         */
        for (int i = 0; i < this.clusters.size(); i++) {
            if (this.clusterDeletion.delete(this.clusters.get(i))) {
                i--;
            }
        }
        /*
         * tries to assign FeatureClusters to a TemporalPattern.
         */
        for (FeatureCluster cluster : this.clusters) {
            if (!cluster.isCandidate()) this.clusterAssignment.assign(cluster);
        }
    }
    
    /**
     * This class is used to generate the cost functions used by the tracker
     * algorithm.
     */
    public static class CostFunctionFactory {
        
        /** Stores the instance of the factory. */
        private static CostFunctionFactory instance = null;
        
        /**
         * Generates the cost function for the spatial evaluation.
         * 
         * @return The generated cost function for the spatial evaluation.
         */
        public EventCostFunction getSpatialEventCostFunction() {
            if (Parameters.getInstance().hasKey(Parameters.PREDICTOR_ACCELERATION_TYPE) &&
                    Parameters.getInstance().get(Parameters.PREDICTOR_ACCELERATION_TYPE).equals("none")) {
                return new BoundaryEventCostFunction();
            }
            return new PredictedBoundaryEventCostFunction();
        }
        
        /**
         * Generates the cost function for the temporal evaluation.
         * 
         * @return The generated cost function for the temporal evaluation.
         */
        public EventCostFunction getTemporalEventCostFunction() {
            if (Parameters.getInstance().hasKey(Parameters.PREDICTOR_TEMPORAL_TYPE) &&
                    Parameters.getInstance().get(Parameters.PREDICTOR_TEMPORAL_TYPE).equals("none")) {
                
                /*
                 * creates a dummy cost function.
                 */
                return new EventCostFunction() {
                    
                    @Override
                    public double cost(EventAssignable assignable, TypedEvent e) {
                        return 0.5;
                    }
                    
                    @Override
                    public boolean isComputable(EventAssignable assignable) {
                        return true;
                    }
                };
            }
            return new OccuranceEventCostFunction();
        }
        
        /**
         * Gets the instance of the factory.
         * 
         * @return The instance of the factory.
         */
        public static CostFunctionFactory getInstance() {
            if (instance ==  null) instance = new CostFunctionFactory();
            
            return instance;
        }
    }
}
