/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;


/**
 *
 * @author matthias
 * 
 * This class represents a cluster which is able to extract features out of 
 * the events assigned to it. Furthermore the cluster is able predict several
 * features of the cluster.
 */
public class SimpleFeatureCluster extends AbstractFeatureCluster {
    
    /**
     * Creates a new instance of SimpleFeatureCluster.
     */
    public SimpleFeatureCluster(FeatureManager features, 
                                ParameterManager manager) {
        super(features, manager);
        
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
        
        //this.features.add(Features.InformationEvent);
    }
}
