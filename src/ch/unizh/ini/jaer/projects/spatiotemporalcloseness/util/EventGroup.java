/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiotemporalcloseness.util;

import java.util.List;

import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.event.TypedEvent;

/**
 *
 * @author matthias
 */
public interface EventGroup {
    
    /**
     * Adds an event to the group.
     * 
     * @param e The event to add to the group.
     */
    public void add(TypedEvent e);
    
    /**
     * Adds the given group to this groups.
     * 
     * @param group The group to add.
     */
    public void add(EventGroup group);
    
    /**
     * Gets the type of the group.
     * 
     * @return The type of the group.
     */
    public int getType();
    
    /**
     * Gets the timestamp of the group.
     * 
     * @return The timestamp of the group.
     */
    public double getTimestamp();
    
    /**
     * Gets the maximal timestamp of the group.
     * 
     * @return The maximal timestamp of the group.
     */
    public int getMaxTimestamp();
    
    /**
     * Gets the size of the group.
     * 
     * @return The size of the group.
     */
    public int getSize();
    
    /**
     * Gets all events assigned to this group.
     * 
     * @return All assigned events assigned to this group.
     */
    public List<TypedEvent> getEvents();
    
    /**
     * Draws the group.
     * 
     * @param drawable The instance to draw with.
     * @param current The current timestamp of the algorithm.
     * @param resolution The temporal resolution used to draw.
     */
    public void draw(GLAutoDrawable drawable, int current, int resolution);
}
