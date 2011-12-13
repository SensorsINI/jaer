/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.position;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.Vector;
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
 * The concrete implementations of this class have to extract the position
 * out of the given events.
 */
public abstract class AbstractPositionExtractor extends AbstractFeatureExtractor implements PositionExtractor {

    /** Stores the position of the object. */
    protected Vector position;
    
    /**
     * Creates a new instance of a AbstractPositionExtractor.
     */
    public AbstractPositionExtractor(Features interrupt, 
                                     ParameterManager parameters, 
                                     FeatureManager features,
                                     AEChip chip) {
        super(interrupt, parameters, features, Features.Position, Color.getBlue(), chip);
        
        this.init();
        this.reset();
    }
    
    @Override
    public void init() {
        super.init();
        
        this.position = new Vector(3);
    }

    @Override
    public void reset() {
        super.reset();
        
        this.position.reset();
    }
    
    @Override
    public Vector getPosition() {
        return this.position;
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
        return "position: " + this.position.toString();
    }
}
