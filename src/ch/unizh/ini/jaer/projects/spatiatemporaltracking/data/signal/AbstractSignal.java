/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.Color;
import com.sun.opengl.util.j2d.TextRenderer;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GL;

/**
 *
 * @author matthias
 * 
 * The implementations of this abstract class store an observed signal.
 */
public abstract class AbstractSignal implements Signal {
    
    /** The period of the observed signal. */
    protected int period;
    
    /** The phase of the observed signal. */
    protected int phase;

    @Override
    public void init() {
        
    }
    
    @Override
    public void reset() {
        this.period = 0;
        this.phase = 0;
    }

    @Override
    public int getPeriod() {
        return this.period;
    }
    
    @Override
    public int getPhase() {
        return this.phase;
    }
    
    @Override
    public void setPhase(int phase) {
        this.phase = phase;
    }
    
    @Override
    public void draw(GLAutoDrawable drawable, TextRenderer renderer, Color color, float x, float y, int height, int resolution) {
        if (this.getSize() <= 0) return;
        
        GL gl = drawable.getGL();
                
        int from = -1;
        int to = this.getSize() - 1;
        
        renderer.begin3DRendering();
        renderer.draw3D(this.toString(), x, y, 0, 0.5f);
        renderer.end3DRendering();
        
        float pack = (this.getTransition(to).time - this.getTransition(from).time) / resolution;
        
        for (int i = from; i < to; i++) {
            float start = this.getTransition(i).time / pack;
            float end = this.getTransition(i + 1).time / pack;
            
            float stateOld = this.getTransition(i).state;
            float stateNew = this.getTransition(i + 1).state;
            
            gl.glBegin(GL.GL_LINES);
            {
                gl.glVertex2f(x + start, y - height + (height - 4) * stateOld + 3);
                gl.glVertex2f(x + end, y - height + (height - 4) * stateOld + 3);
            }
            gl.glEnd();

            gl.glBegin(GL.GL_LINES);
            {
                gl.glVertex2f(x + end, y - height + (height - 4) * stateOld + 3);
                gl.glVertex2f(x + end, y - height + (height - 4) * stateNew + 3);
            }
            gl.glEnd();
        }
    }
    
    @Override
    public String toString() {
        return "signal [us]: " + this.period + ".";
    }
}
