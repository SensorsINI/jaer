/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.indexedvalue;

import com.sun.opengl.util.j2d.TextRenderer;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GL;

/**
 *
 * @author matthias
 */
public abstract class AbstractIndexedValues implements IndexedValues {
    protected int start;
    protected int step;
    protected int end;
    
    protected int nBins;
    
    public AbstractIndexedValues() {
        this(1000, 1000, 10000);
    }
    
    public AbstractIndexedValues(int start, int step, int nBins) {
        this.start = start;
        this.step = step;
        this.end = start + step * nBins;
        
        this.nBins = nBins;
    }

    @Override
    public int getStart() {
        return this.start;
    }

    @Override
    public int getStep() {
        return this.step;
    }
    
    @Override
    public void draw(GLAutoDrawable drawable, TextRenderer renderer, int x, int y, int height, int resolution) {
        GL gl = drawable.getGL();
        
        int to = this.nBins - 1;
        while (this.getNormalized(to) < 0.01) {
            to--;
        }
        to = Math.min(to + 2, this.nBins);
        
        int pack = to / resolution + 1;
        
        float [] sum = new float[resolution];
        int counter = 0;
        for (int i = 0; i < to; i++) {
            sum[counter] += this.getNormalized(i);
            
            if (i % pack == 0 && i != 0) {
                counter++;
            }
        }
        
        float max = 0;
        for (int i = 0; i < sum.length; i++) {
            if (max < sum[i]) {
                max = sum[i];
            }
        }
        
        for (int i = 0; i < sum.length; i++) {
            float h = sum[i] / max * ((height - 5) / 2.0f);
            gl.glBegin(GL.GL_LINE_LOOP);
            {
                gl.glVertex2f(x + i, y - height / 2.0f + 1);
                gl.glVertex2f(x + i, y - height / 2.0f + h + 1);

                gl.glVertex2f(x + i + 1, y - height / 2.0f + h + 1);
                gl.glVertex2f(x + i + 1, y - height / 2.0f + 1);
            }
            gl.glEnd();
        }
        
        renderer.begin3DRendering();
        renderer.setColor(0,0,1,0.8f);
        renderer.draw3D("interval [au]: 0, " + (this.start + to * this.step) + ".", x, y, 0, 0.5f);
        renderer.end3DRendering();
    }
}
