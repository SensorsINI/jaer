/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.Parameters;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.Color;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 */
public abstract class AbstractFeatureExtractor implements FeatureExtractor {

    /**
     * Defines whether the feature has to be drawed or not.
     */
    protected boolean isDebugged = true;
    
    public static final int MIN_TIMESTAMP = Integer.MIN_VALUE;
    
    public static final int SECOND = 1000000;
    
    protected Color color;
    
    protected AEChip chip;
    
    protected Features feature;
    
    /**
     * Defines on which event the extractor waits until it is active.
     */
    protected Features interrupt;
    
    /** The manager for the parameters. */
    protected ParameterManager parameters;
    
    /** The manager for this feature. */
    protected FeatureManager features;
    
    /** The timestamp of the last modification on the feature. */
    protected int timestamp;
    
    /**
     * Creates a new instance of a AbstractFeatureExtractor.
     * 
     * @param interrupt Defines the interrupt for which the extractor will wait.
     * @param parameters The instances managing the parameters used by this feature.
     * @param features The instance managing the features uesd by this feature.
     * @param chip The AEChip used by the DVS.
     * @param feature The type of the feature extracted by this Feature.
     */
    public AbstractFeatureExtractor(Features interrupt,
                                    ParameterManager parameters, 
                                    FeatureManager features,
                                    Features feature,
                                    Color color,
                                    AEChip chip){
        this.interrupt = interrupt;
        
        this.parameters = parameters;
        this.features = features;
        
        this.chip = chip;
        this.feature = feature;
        this.color = color;
    }
    
    @Override
    public void init() {
        this.parameters.add(this);
        
        this.setSource(this.features);
    }

    @Override
    public void reset() {
        this.timestamp = MIN_TIMESTAMP;
        
        this.parameterUpdate();
    }

    @Override
    public Features getIdentifier() {
        return this.feature;
    }
    
    @Override
    public Features getInterrupt() {
        return this.interrupt;
    }
    
    @Override
    public int lastChange() {
        return this.timestamp;
    }
    
    @Override
    public void setSource(FeatureManager features) {
        this.features.remove(this);
        
        this.features = features;
        
        this.features.add(this);
        if (!this.features.has(this.interrupt)) {
            this.features.add(this.interrupt);
        }
        
        this.features.getNotifier().add(this, this.interrupt);
    }

    @Override
    public boolean isStatic() {
        return false;
    }

    @Override
    public void delete() {
        this.parameters.remove(this);
        this.features.remove(this);
    }

    @Override
    public void store() {
        this.delete();
    }
    
    @Override
    public void parameterUpdate() {
        if (Parameters.getInstance().hasKey(Parameters.DEBUG_MODE)) this.isDebugged = Parameters.getInstance().getAsBoolean(Parameters.DEBUG_MODE);
    }
}
