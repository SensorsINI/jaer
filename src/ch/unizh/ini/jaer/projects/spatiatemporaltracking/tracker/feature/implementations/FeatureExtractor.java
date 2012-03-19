/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.Features;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.listener.FeatureListener;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.manager.FeatureManager;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterListener;
import com.sun.opengl.util.j2d.TextRenderer;
import javax.media.opengl.GLAutoDrawable;

/**
 *
 * @author matthias
 */
public interface FeatureExtractor extends FeatureListener, ParameterListener {
    
    /**
     * Initializes the object and its data.
     */ 
    public void init();
    
    /**
     * Resets the object and its data.
     */ 
    public void reset();
    
    /**
     * Gets the type of the feature extracted by this feature.
     * 
     * @return The type of the feature.
     */ 
    public Features getIdentifier();
    
    /**
     * Gets the type of the interrupt for which this extractor will wait.
     * 
     * @return The interrupt for this extractor.
     */
    public Features getInterrupt();
    
    /**
     * Sets the source for the extractor. The source provides access to the 
     * other features of the object.
     * 
     * @param features The source for the extractor.
     */
    public void setSource(FeatureManager features);
    
    /**
     * Gets the timestamp of the last modification.
     * 
     * @return The timestamp of the last modification.
     */
    public int lastChange();
    
    /*
     * Returns true, if the feature will not change in the feature, otherwise
     * false.
     * 
     * @return True, if the feature is stable, otherwise false.
     */
    public boolean isStatic();
    
    /**
     * Deletes this Feature and Features that are needed
     * by this Feature.
     */
    public void delete();
    
    /**
     * Deletes this Feature and Features that are needed
     * by this Feature but stores its resuts.
     */
    public void store();
    
    /*
     * Draws the information extracted by this Feature.
     * 
     * @param drawable The object to draw.
     * @param renderer The object to write.
     * @param x Position in x direction of the object.
     * @param y Position in y direction of the object.
     */
    public void draw(GLAutoDrawable drawable, TextRenderer renderer, float x, float y);
    
    /*
     * Gets the height of the drawed object.
     * 
     * @return The height of the drawed object.
     */
    public int getHeight();
}
