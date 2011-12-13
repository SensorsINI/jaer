/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.boundary;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.Vector;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.AbstractFeatureExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.position.PositionExtractor;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.parameter.ParameterManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.Color;
import com.sun.opengl.util.j2d.TextRenderer;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.chip.AEChip;

/**
 *
 * @author matthias
 */
public abstract class AbstractBoundaryExtractor extends AbstractFeatureExtractor implements BoundaryExtractor {

    /** Stores the length of the observed object on the major axis. */
    protected float majorLength;
    
    /** Stores the length of the observed object on the minor axis. */
    protected float minorLength;
    
    /** Stores the angle between the major axis and the x-axis. */
    protected float angle;
    
    /**
     * Creates a new instance of a AbstractBoundaryExtractor.
     */
    public AbstractBoundaryExtractor(Features interrupt, 
                                     ParameterManager parameters, 
                                     FeatureManager features,
                                     AEChip chip) {
        super(interrupt, parameters, features, Features.Boundary, Color.getBlue(), chip);
        
        this.init();
        this.reset();
    }
    
    @Override
    public void reset() {
        super.reset();
        
        this.majorLength = 0;
        this.minorLength = 0;
        this.angle = 0;
    }
    
    @Override
    public float getMajorLength() {
        return this.majorLength;
    }

    @Override
    public float getMinorLength() {
        return this.minorLength;
    }

    @Override
    public float getAngle() {
        return this.angle;
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
        
        Vector p = ((PositionExtractor)this.features.get(Features.Position)).getPosition();
        gl.glLineWidth(2);
        gl.glBegin(GL.GL_LINE_LOOP);
        for (int i=0; i<60; i++) {
            gl.glVertex2d(p.get(0) + this.majorLength * Math.cos(6.0*i*Math.PI/180.0),
                          p.get(1) + this.minorLength * Math.sin(6.0*i*Math.PI/180.0));
        }
        gl.glEnd();
        
        gl.glBegin(GL.GL_LINES);
            gl.glVertex2d(p.get(0) + Math.cos(this.angle)*10, p.get(1) + Math.sin(this.angle)*10);
            gl.glVertex2d(p.get(0) - Math.cos(this.angle)*10, p.get(1) - Math.sin(this.angle)*10);
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
        return "boundary: [" + this.majorLength + ", " + this.minorLength + "], with angle " + this.angle;
    }
}
