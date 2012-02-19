/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.lifetime;

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
 * The concrete implementations of this class have to extract the lifetime
 * of the observed object out of the given events.
 */
public abstract class AbstractLifetimeExtractor extends AbstractFeatureExtractor implements LifetimeExtractor {
    
    /** Stores the lifetime. */
    protected int lifetime;
    
    /** Stores the time of the creation of the object. */
    protected int creationtime;
            
    /**
     * Creates a new instance of a AbstractLifetimeExtractor.
     */
    public AbstractLifetimeExtractor(Features interrupt,
                                     ParameterManager parameters, 
                                     FeatureManager features,
                                     AEChip chip) {
        super(interrupt, parameters, features, Features.Lifetime, Color.getBlue(), chip);
    }
    
    @Override
    public void reset() {
        super.reset();
        
        this.lifetime = 0;
        this.creationtime = 0;
    }

    @Override
    public int getLifetime() {
        return this.lifetime;
    }
    
    @Override
    public int getCreationTime() {
        return this.creationtime;
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
        return "lifetime [us]: " + this.lifetime;
    }
}
