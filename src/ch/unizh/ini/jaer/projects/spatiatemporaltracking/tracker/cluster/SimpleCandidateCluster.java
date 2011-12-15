/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.ParameterManager;
import java.util.Set;

/**
 *
 * @author matthias
 */
public class SimpleCandidateCluster extends AbstractFeatureCluster implements CandidateCluster {
    
    /**
     * Creates a new instance of SimpleFeatureCluster.
     */
    public SimpleCandidateCluster(FeatureManager features, 
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
        
        this.features.add(Features.Moment);
        this.features.add(Features.Position);
        this.features.add(Features.Assigned);
        
        this.features.add(Features.InformationSignal);
        this.features.add(Features.InformationPath);
        this.features.add(Features.InformationVelocity);
    }

    @Override
    public void convert(FeatureCluster cluster) {
        /*
         * store existing extractors
         */
        Set<Features> sf = this.features.getFeatures();
        for (Features f : sf) {
            this.features.get(f).setSource(cluster.getFeatures());
        }
    }
    
    @Override
    public boolean isCandidate() {
        return true;
    }
}
