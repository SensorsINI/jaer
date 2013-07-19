/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker;

import javax.media.opengl.GLAutoDrawable;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.parameter.ParameterManager;

/**
 *
 * @author matthias
 */
public interface Tracker extends ParameterManager {
    
    /**
     * Initializes the tracker.
     */
    public void init();
    
    /**
     * Resets the tracker.
     */
    public void reset();
    
    /**
     * Draws all information provided by the Tracker.
     * 
     * @param drawable The instance used to draw.
     */
    public void draw(GLAutoDrawable drawable);
}
