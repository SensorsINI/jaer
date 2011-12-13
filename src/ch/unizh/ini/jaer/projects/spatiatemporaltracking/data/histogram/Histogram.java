/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.histogram;

import com.sun.opengl.util.j2d.TextRenderer;
import javax.media.opengl.GLAutoDrawable;

/**
 *
 * @author matthias
 * 
 * The interface Histogram provides methods to work with a histogram.
 */
public interface Histogram {
    
    /**
     * Increments the bin corresponding to the given value.
     * 
     * @param value Defines the corresponding bin of the value.
     */
    public void add(int value);
    
    /**
     * Gets the value at the specified bin.
     * 
     * @param index The bin of the histogram.
     * @return The value at the specified bin.
     */
    public float get(int index);
    
    /**
     * Gets the normalized value at the specified bin.
     * 
     * @param index The bin of the histogram.
     * @return The normalized value at the specified bin.
     */
    public float getNormalized(int index);
    
    /**
     * Gets the number of elements in the histogram.
     * 
     * @return The number of elements in the histogram.
     */
    public int getN();
    
    /**
     * Gets the size of the histogram.
     * 
     * @return The size of the histogram.
     */
    public int getSize();
    
    /**
     * Gets the start of the histogram.
     * 
     * @return The start of the histogram.
     */
    public int getStart();
    
    /**
     * Gets the step size of the histogram. This corresponds to the resolution
     * of the bins.
     * 
     * @return The step size of the histogram.
     */
    public int getStep();
    
    /**
     * Initializes the histogram.
     */
    public void init();
    
    /**
     * Resets the histogram.
     */
    public void reset();
    
    /**
     * Returns true, if the histogram contains a representative amount of data,
     * false otherwise.
     * 
     * @return True, if the histogram contains a representative amount of data,
     * false otherwise.
     */
    public boolean isExpressable();
    
    /**
     * Draws the histogram.
     * 
     * @param drawable The instance to draw.
     * @param renderer The instance to write.
     * @param x The x-position of the drawing.
     * @param y The y-position of the drawing.
     * @param height The height of the drawing.
     * @param resolution Defines the width of the drawing.
     */
    public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y, int height, int resolution);
}
