/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.object;

import java.util.Arrays;

/**
 *
 * @author matthias
 * 
 * The class provides some basic methods used by all implementations of 
 * the interface ObservedObject.
 */
public abstract class AbstractObservedObject implements ObservedObject {
    
    /**
     * The width of the observed object.
     */
    protected int width;
    
    /**
     * Stores the shape of the observed object as a binary image.
     */
    protected byte[][] shape;
    
    /**
     * Stores the area of the observed object.
     */
    protected float area;
    
    /**
     * Creates a new ObservedObject.
     * 
     * @param size The maximal size of the object.
     */
    public AbstractObservedObject(int width) {
        this.width = width;
        this.shape = new byte[2 * width + 1][2* width + 1];
    }
    
    @Override
    public void clear() {
        for (int i = 0; i < this.shape.length; i++) {
            Arrays.fill(this.shape[i], (byte)0);
        }
        this.area = 0;
    }
    
    @Override
    public int getWidth() {
        return this.width;
    }
    
    @Override
    public void setShape(byte[][] shape) {
        this.shape = shape;
        
        this.area = 0;
        for (int x = 0; x < this.shape.length; x++) {
            for (int y = 0; y < this.shape[x].length; y++) {
                this.area += this.shape[x][y];
            }
        }
    }
    
    @Override
    public byte[][] getShape() {
        return this.shape;
    }
    
    @Override
    public void setArea(float area) {
        this.area = area;
    }
    
    @Override
    public float getArea() {
        return Math.max(1, this.area);
    }
}
