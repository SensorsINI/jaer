/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.signal.period;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.AbstractFeatureExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.Color;
import com.sun.opengl.util.j2d.TextRenderer;
import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 * 
 * The concrete implementations of this class have to extract the period
 * of the signal of the observed object as well as its phase.
 */
public abstract class AbstractPeriodExtractor extends AbstractFeatureExtractor implements PeriodExtractor {
    
    /** Stores the period of the signa. */
    protected int period;
    
    /**
     * Creates a new instance of a AbstractPeriodExtractor.
     */
    public AbstractPeriodExtractor(Features interrupt,
                                   ParameterManager parameters, 
                                   FeatureManager features,
                                   AEChip chip) {
        super(interrupt, parameters, features, Features.Period, Color.getBlue(), chip);
    }

    @Override
    public void init() {
        super.init();
    }

    @Override
    public void reset() {
        super.reset();
        
        this.period = 0;
    }
    
    @Override
    public int getPeriod() {
        return this.period;
    }
    
    @Override
    public boolean isValid() {
        return this.period > 0;
    }

    @Override
    public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y) {
        if (this.isDebugged) {
            renderer.begin3DRendering();
            renderer.setColor(0,0,1,0.8f);
            renderer.draw3D(this.toString(), x, y, 0, 0.5f);
            renderer.end3DRendering();
        }
    }

    @Override
    public int getHeight() {
        if (this.isDebugged) return 4;
        return 0;
    }
    
    @Override
    public String toString() {
        return "period: " + this.period + " us";
    }
}
