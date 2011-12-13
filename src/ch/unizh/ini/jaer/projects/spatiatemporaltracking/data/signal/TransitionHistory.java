/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.signal;

import ch.unizh.ini.jaer.projects.spatiatemporaltracking.util.Color;
import com.sun.opengl.util.j2d.TextRenderer;
import javax.media.opengl.GLAutoDrawable;

/**
 *
 * @author matthias
 * 
 * The TransitionHistory stores the Transitions of a signal.
 */
public interface TransitionHistory {
    
    /**
     * Adds the given Transition to the TransitionHistory.
     * 
     * @param t The Transition to add.
     */
    public void add(Transition t);
    
    /**
     * Adds a Transition to the TransitionHistory based on the given time and
     * state.
     * 
     * @param time The time of the Transition.
     * @param state The state of the signal after this Transition.
     */
    public void add(int time, int state);
    
    /**
     * Gets the Transition at the given position.
     * 
     * @param index The index corresponding to the position of the desired
     * Transition.
     * @return The Transition at the given position.
     */
    public Transition getTransition(int index);
    
    /**
     * Gets true if the history is empty, false otherwise.
     * 
     * @return True, if the history is empty, false otherwise.
     */
    public boolean isEmpty();
    
    /**
     * Gets the number of Transitions stored in the TransitionHistory.
     * 
     * @return The number of Transitions.
     */
    public int getSize();
    
    /**
     * Resets the TransitionHistory.
     */
    public void reset();
    
    
    /*
     * Draws the TransitionHistory.
     * 
     * @param drawable The object to draw.
     * @param renderer The object to write.
     * @param color The color used to draw and write.
     * @param x Position in x direction of the object.
     * @param y Position in y direction of the object.
     * @param height The height of the drawing.
     * @param resolution Defines the width of the drawing.
     */
    public void draw(GLAutoDrawable drawable, TextRenderer renderer, Color color, float x, float y, int height, int resolution);
}
