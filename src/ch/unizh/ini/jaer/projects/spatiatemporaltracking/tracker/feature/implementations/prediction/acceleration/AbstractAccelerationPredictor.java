/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.prediction.acceleration;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.Vector;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.AbstractFeatureExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.position.PositionExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.Color;
import com.sun.opengl.util.j2d.TextRenderer;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 * 
 * Provides some basic methods for concrete implementations of the interface
 * AccelerationPredictor.
 */
public abstract class AbstractAccelerationPredictor extends AbstractFeatureExtractor implements AccelerationPredictor {

    /** Stores the predicted acceleration of the object. */
    protected Vector acceleration;
    
    /**
     * Creates a new instance of AbstractAccelerationPredictor.
     */
    public AbstractAccelerationPredictor(Features interrupt,
                                         ParameterManager parameters, 
                                         FeatureManager features,
                                         AEChip chip) {
        super(interrupt, parameters, features, Features.AccelerationPredictor, Color.getYellow(), chip);
        
        this.init();
        this.reset();
    }
    
    @Override
    public void init() {
        super.init();
        
        this.acceleration = Vector.getDefault(3);
    }
    
    @Override
    public void reset() {
        super.reset();
        
        this.acceleration.reset();
    }
    
    @Override
    public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y) {
        if (this.isDebugged) {
            renderer.begin3DRendering();
            renderer.setColor(this.color.getFloat(0),
                              this.color.getFloat(1),
                              this.color.getFloat(2),
                              0.8f);
            renderer.draw3D(this.toString(), x, y, 0, 0.5f);
            renderer.end3DRendering();
        }
        
        GL gl = drawable.getGL();
        gl.glColor3f(this.color.getFloat(0),
                     this.color.getFloat(1),
                     this.color.getFloat(2));
        gl.glLineWidth(3);

        Vector p = ((PositionExtractor)this.features.get(Features.Position)).getPosition();
        gl.glBegin(GL.GL_LINES);
        {
            gl.glVertex2f(p.get(0), 
                          p.get(1));
            gl.glVertex2f(p.get(0) + this.acceleration.get(0) * SECOND, 
                          p.get(1));

            gl.glVertex2f(p.get(0), 
                          p.get(1));
            gl.glVertex2f(p.get(0), 
                          p.get(1) + this.acceleration.get(1) * SECOND);
        }
        gl.glEnd();

        gl.glLineWidth(1);
    }

    @Override
    public int getHeight() {
        if (this.isDebugged) return 4;
        return 0;
    }
    
    @Override
    public String toString() {
        return "acceleration: " + this.acceleration.toString();
    }
}
