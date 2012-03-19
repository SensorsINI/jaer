/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.signal;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.Signal;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.SimpleSignal;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal.creator.SignalCreator;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.AbstractFeatureExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.Color;

import com.sun.opengl.util.j2d.TextRenderer;
import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 * 
 * The concrete implementations of this class have to extract the signal
 * out of the observed object based on its assigned events.
 */
public abstract class AbstractSignalExtractor extends AbstractFeatureExtractor implements SignalExtractor {

    /** Stores the signal of the observed object. */
    protected Signal signal;
    
    /** Stores the instance to create the signal. */
    protected SignalCreator creator = null;
    
    /**
     * Creates a new instance of a AbstractSignalExtractor.
     */
    public AbstractSignalExtractor(Features interrupt,
                                   ParameterManager parameters, 
                                   FeatureManager features,
                                   AEChip chip) {
        super(interrupt, parameters, features, Features.Signal, Color.getBlue(), chip);
        
        this.init();
        this.reset();
    }

    @Override
    public void init() {
        super.init();
        
        this.signal = new SimpleSignal();
    }

    @Override
    public void reset() {
        super.reset();
        
        this.signal.reset();
        if (this.creator != null) this.creator.reset();
    }
    
    @Override
    public Signal getSignal() {
        return this.signal;
    }

    @Override
    public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y) {
        if (this.isDebugged) {
            Signal s = new SimpleSignal(this.signal);
            s.draw(drawable, renderer, new Color(0, 0, 1), x, y, this.getHeight(), 100);
        }
    }

    @Override
    public int getHeight() {
        if (this.isDebugged) return 10;
        return 0;
    }
}
