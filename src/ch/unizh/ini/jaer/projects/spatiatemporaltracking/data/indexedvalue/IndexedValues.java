/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.indexedvalue;

import com.sun.opengl.util.j2d.TextRenderer;
import javax.media.opengl.GLAutoDrawable;

/**
 *
 * @author matthias
 */
public interface IndexedValues {
    public void add(int key, int value);
    public int get(int index);
    public float getNormalized(int index);
    public int getMax();
    
    public int getSize();
    public int getStart();
    public int getStep();
    
    public void init();
    public void reset();
    
    public void draw(GLAutoDrawable drawable, TextRenderer renderer, int x, int y, int height, int resolution);
}
