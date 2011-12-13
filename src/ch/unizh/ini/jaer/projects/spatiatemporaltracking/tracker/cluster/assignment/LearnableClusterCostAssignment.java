/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.assignment;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.assignable.AssignableCluster;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.FeatureClusterStorage;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.assignment.cost.ClusterCostFunction;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.Parameters;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.temporalpattern.TemporalPattern;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.temporalpattern.TemporalPatternStorage;

/**
 *
 * @author matthias
 * 
 * To assign a FeatureCluster to a TemporalPattern this approach computes
 * the average costs to compute a lower bound for the assignment. If the best
 * possible solution is below this lower bound the assignment of the 
 * FeatureCluster to the minimizing TemporalPattern is valid. Otherwise the 
 * assignment is not valid and a new TemporalPattern is created.
 */
public class LearnableClusterCostAssignment extends AbstractClusterAssignment {
    private float threshold;
    
    /*
     * Creates a new LearnableClusterCostAssignment.
     */
    public LearnableClusterCostAssignment(ParameterManager manager, FeatureClusterStorage storage, ClusterCostFunction function) {
        super(manager, function);
    }
    
    /*
     * Assigns a FeatureCluster to a TemporalPattern using the average cost as
     * a lower bound for the assignment. If the best possible solution is below 
     * this lower bound the assignment of the  FeatureCluster to the minimizing 
     * TemporalPattern is valid. Otherwise the assignment is not valid and a 
     * new TemporalPattern is created.
     * 
     * @param f The FeatureCluster to assign to a TemporalPattern.
     */
    @Override
    public void assign(AssignableCluster a) {
        if (!a.isAssigned()) {
            if (a.getFeatures().get(Features.Signal).isStatic()) {
                double min = Double.MAX_VALUE;
                TemporalPattern best = null;

                for (TemporalPattern pattern : TemporalPatternStorage.getInstance().getPatterns()) {
                    double cost = this.function.cost(a, pattern);

                    if (min > cost) {
                        min = cost;
                        best = pattern;
                    }

                    a.add("cost based on function '" + pattern.getName() + "'" , "" + cost);
                }

                a.add("fixed threshold for cost ", "" + this.threshold);

                if (min < this.threshold) {
                    a.assign(best);
                }
                else {
                    TemporalPattern pattern = this.function.add(a);
                }
            }
        }
    }

    @Override
    public void parameterUpdate() {
        if (Parameters.getInstance().hasKey(Parameters.CLUSTER_ASSIGNMENT_THRESHOLD)) this.threshold = Parameters.getInstance().getAsFloat(Parameters.CLUSTER_ASSIGNMENT_THRESHOLD);
    }
}
