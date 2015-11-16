/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.util.histogram;

import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.awt.TextRenderer;
import java.io.PrintWriter;

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
     * Gets the number of elements added to the histogram. Incremented by each call to add.
     * 
     * @return The number of elements added to the histogram.
     */
    public int getN();
    
    /**
     * Gets the size of the histogram in bins.
     * 
     * @return The size of the histogram in bins.
     */
    public int getSize();
    
    /**
     * Gets the starting value of the histogram; the lower edge of the first bin value.
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
     * Sets whether all bins are drawn or just a range that includes 10-90% of the filled bins.
     * @param yes true to draw all bins.
     */
    public void setDrawAllBins(boolean yes);
   /**
     * Returns whether all bins are drawn or just a range that includes 10-90% of the filled bins.
     * @return true when drawing all bins.
     */
    public boolean isDrawAllBins();
    
    /**
     * Draws the histogram. The text labels show the sample limits and total count of the normalized histogram.
     * It also includes the entropy of the histogram.
     * 
     * @param drawable The instance to draw.
     * @param renderer The instance to write text with.
     * @param x The x-position of the drawing in GL drawing units, with 0 on left edge of screen.
     * @param y The y-position of the drawing in GL drawing units, with 0 on bottom edge of screen.
     * @param height The height of the drawing in GL drawing units.
     * @param resolution Defines the width of the drawing in GL drawing units. I.e. for chip displays it is in chip pixels when drawing with ChipCanvas using default scaling of chip pixels. The number of drawn bins is determined from this resolution by dividing the number of bins by the resolution; e.g. if there are 1000 bins and the resolution is 100 then 10 bins will be drawn.
     * @see #setDrawAllBins(boolean) 
     */
    public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y, int height, int resolution);
    
    /** Prints a string representation of the Histgram System.out */
    public void print();
}
