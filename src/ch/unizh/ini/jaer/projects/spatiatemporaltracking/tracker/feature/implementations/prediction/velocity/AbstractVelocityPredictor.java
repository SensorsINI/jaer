/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.prediction.velocity;

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
 * Concrete implementations of this abstract class have to provide methods to
 * predict the velocity of the observed object.
 */
public abstract class AbstractVelocityPredictor extends AbstractFeatureExtractor implements VelocityPredictor {
    
    /**
     * Creates a new instance of AbstractVelocityPredictor.
     */
    public AbstractVelocityPredictor(Features interrupt,
                                     ParameterManager parameters, 
                                     FeatureManager features,
                                     AEChip chip) {
        super(interrupt, parameters, features, Features.VelocityPredictor, Color.getYellow(), chip);
        
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
        gl.glLineWidth(3);
        gl.glColor3f(this.color.getFloat(0),
                     this.color.getFloat(1),
                     this.color.getFloat(2));
        
        Vector p = ((PositionExtractor)this.features.get(Features.Position)).getPosition();
        gl.glBegin(GL.GL_LINES);
        {
            Vector v = this.getVelocityRelative(0);
            gl.glVertex2f(p.get(0), p.get(1));
            gl.glVertex2f(p.get(0) + v.get(0) / 5 * SECOND, 
                          p.get(1) + v.get(1) / 5 * SECOND);
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
        return "velocity: " + this.getVelocityRelative(0).toString();
    }
}
