/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.object;

/**
 *
 * @author matthias
 * 
 * This interface represents the shape and contour of the observed object.
 */
public interface ObservedObject {
    
    /**
     * Returns the width of the observed object.
     * 
     * @return The width of the observed object.
     */
    public int getWidth();
    
    /**
     * Returns the area of the observed object.
     * 
     * @return The area of the observed object.
     */
    public float getArea();
    
    /**
     * Sets the area of the observed object.
     * 
     * @param area The area of the observed object.
     */
    public void setArea(float area);
    
    /**
     * Sets the shape of the observed object according to the given gray-scaled
     * image.
     * 
     * @param shape The shape of the observed object.
     */
    public void setShape(int[][] shape);
    
    /**
     * Sets the shape of the observed object according to the given binary
     * image.
     * 
     * @param shape The shape of the observed object.
     */
    public void setShape(byte[][] shape);
    
    /**
     * Returns the shape of the observedobject.
     * 
     * @return The shape of the object. 
     */
    public byte[][] getShape();
    
    /**
     * Clears the object and all data.
     */
    public void clear();
}
