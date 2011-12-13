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
 * The abstract class is used to store the Transitions of a signal.
 */
public abstract class AbstractTransitionHistory implements TransitionHistory {
    
    @Override
    public void reset() {
        
    }

    @Override
    public void draw(GLAutoDrawable drawable, TextRenderer renderer, Color color, float x, float y, int height, int resolution) {
        if (this.getSize() <= 0) return;
        
        GL gl = drawable.getGL();
        
        renderer.begin3DRendering();
        renderer.draw3D(this.toString(), x, y, 0, 0.5f);
        renderer.end3DRendering();
        
        gl.glColor3d(color.getFloat(0), color.getFloat(1), color.get(2));
        
        int offset = this.getTransition(0).time;
        for (int i = 0; i < this.getSize() - 1; i++) {
            float start = (this.getTransition(i).time - offset) / 1000.0f;
            float end = (this.getTransition(i + 1).time - offset) / 1000.0f;
            
            if (start > end) return;
            if (start > resolution) return;
            
            float stateOld = this.getTransition(i).state;
            float stateNew = this.getTransition(i + 1).state;
            
            if (end > resolution) {
                end = resolution;
                stateNew = stateOld;
            }
            
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
        return "transition history [us]: " + this.getTransition(0).time + " - " + this.getTransition(this.getSize() - 1).time + ".";
    }
}

