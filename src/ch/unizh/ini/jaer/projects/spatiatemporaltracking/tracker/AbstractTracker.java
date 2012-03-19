/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.FeatureCluster;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.FeatureClusterStorage;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.SimpleCandidateCluster;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster.SimpleFeatureCluster;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.factory.ConcreteFeatureExtractorFactory;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.TrackingFeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.notifier.TrackingFeatureNotifier;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterListener;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.temporalpattern.TemporalPattern;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.temporalpattern.TemporalPatternStorage;
import com.sun.opengl.util.j2d.TextRenderer;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 * 
 * This abstract is used by Trackers to maintain the different FeauterClusters
 * and Parameters.
 */
public class AbstractTracker implements Tracker, FeatureClusterStorage {

    protected AEChip chip;
    
    /** Stores the number of FeauterClusters used by the Tracker. */
    protected int nFeatureClusters;
    
    /** Stores the number of CandidateClusters used by the Tracker. */
    protected int nCandidateClusters;
    
    /** Stores all FeatureClusters used by the Tracker. */
    protected List<FeatureCluster> clusters;
    
    /** Stores the FeatureClusters for the visualization. */
    protected List<FeatureCluster> visualization;
    
    /** The timestamp of the first event. */
    protected int first;
    
    /**
     * Creates a new instance of the class AbstractTracker.
     */
    public AbstractTracker() {
        this.init();
        this.reset();
    }
    
    @Override
    public void init() {
        this.clusters = new ArrayList<FeatureCluster>();
        
        this.listeners = new HashSet<ParameterListener>();
        
        this.visualization = new ArrayList<FeatureCluster>();
    }
    
    @Override
    public void reset() {
        this.nFeatureClusters = 0;
        this.nCandidateClusters = 0;
        
        this.listeners.clear();
        
        this.visualization.clear();
        
        TemporalPatternStorage.getInstance().reset();
    }
    
    @Override
    public List<FeatureCluster> getClusters() {
        return this.clusters;
    }

    @Override
    public FeatureCluster addCluster() {
        this.nFeatureClusters++;
        
        FeatureCluster cluster = new SimpleFeatureCluster(new TrackingFeatureManager(new TrackingFeatureNotifier(), ConcreteFeatureExtractorFactory.getInstance(this, this.chip), this.first), 
                                                          this);
        
        this.addCluster(cluster);
        return cluster;
    }

    @Override
    public FeatureCluster addCandidateCluster() {
        this.nCandidateClusters++;
        
        FeatureCluster cluster = new SimpleCandidateCluster(new TrackingFeatureManager(new TrackingFeatureNotifier(), ConcreteFeatureExtractorFactory.getInstance(this, this.chip), this.first), 
                                                            this);
        
        this.addCluster(cluster);
        return cluster;
    }
    
    /**
     * Adds a FeatureCluster to the tracker.
     * 
     * @param cluster The FeatureCluster to add.
     */
    private void addCluster(FeatureCluster cluster) {
        this.clusters.add(cluster);
    }
    
    @Override
    public void delete(FeatureCluster cluster) {
        if (this.clusters.contains(cluster)) {
            if (cluster.isCandidate()) {
                this.nCandidateClusters = Math.max(0, this.nCandidateClusters - 1);
            }
            else {
                this.nFeatureClusters = Math.max(0, this.nFeatureClusters - 1);
            }
            this.clusters.remove(cluster);
        }
        cluster.clear();
    }
    
    @Override
    public int getClusterNumber() {
        return this.nFeatureClusters;
    }

    @Override
    public int getCandidateClusterNumber() {
        return this.nCandidateClusters;
    }

    /*
     * Implementation of the parameter manager.
     */
    private Set<ParameterListener> listeners;
    
    @Override
    public void add(ParameterListener listener) {
        this.listeners.add(listener);
    }

    @Override
    public void remove(ParameterListener listener) {
        this.listeners.remove(listener);
    }
    
    @Override
    public void updateListeners() {
        for (ParameterListener listener : this.listeners) {
            listener.parameterUpdate();
        }
    }
    
    /*
     * drawing
     */
    private TextRenderer renderer = new TextRenderer(new Font("Arial",Font.PLAIN,7),true,true);
    
    @Override
    public void draw(GLAutoDrawable drawable) {
        int offset = 0;
        for (TemporalPattern pattern : TemporalPatternStorage.getInstance().getPatterns()) {
            pattern.draw(drawable, this.renderer, 128, 125 - offset);
            offset += pattern.getHeight();
        }
        
        this.visualization.clear();
        this.visualization.addAll(this.clusters);
        
        for (FeatureCluster cluster : this.visualization) {
            if (cluster != null) cluster.draw(drawable);
        }
    }
}
