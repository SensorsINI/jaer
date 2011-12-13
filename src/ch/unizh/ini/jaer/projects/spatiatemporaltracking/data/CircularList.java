/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data;

/**
 *
 * @author matthias
 * 
 * Stores the object in a circular list.
 */
public class CircularList<Element> {
    /** Structure for the circular list. */
    private Element[] list;
    
    /** Stores the number of elements stored in the circular list. */
    private int N;
    
    /** Indicates whether the circular list is full or not. */
    private boolean isFull;
    
    /** Pointer to the current position in the circular list. */
    private int index;
    
    /**
     * Creates a new CircularList.
     * 
     * @param size The size of the circular list.
     */
    public CircularList(int size) {
        this.list = (Element[])new Object[size];
    }
    
    /**
     * Resets the circular list.
     */
    public void reset() {
        this.N = 0;
        this.isFull = false;
        
        this.index = 0;
    }
    
    /**
     * Adds a value to the circular list.
     * 
     * @param value The value to add to the circular list.
     */
    public void add(Element value) {
        this.list[this.index] = value;
        
        this.index = (this.index + 1) % this.list.length;
        if (!this.isFull) {
            this.N++;
            
            if (this.N >= this.list.length) {
                this.isFull = true;
            }
        }
    }
    
    /**
     * Gets the Element stored at the given position.
     * 
     * @param index The position in the circular list.
     * @return The Element stored at the given position.
     */
    public Element get(int index) {
        if (this.isFull) {
            return this.list[(this.index + index) % this.list.length];
        }
        else {
            return this.list[index];
        }
    }
    
    /**
     * Gets the number of elements in the circular list.
     * 
     * @return The number of elements in the circular list.
     */
    public int getSize() {
        if (!this.isFull) return this.N;
        return this.list.length;
    }
}
