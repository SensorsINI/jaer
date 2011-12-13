/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data;

import java.util.Arrays;

/**
 *
 * @author matthias
 */
public class MaxCircularList {
    
    /** Structure for the circular list. */
    protected float[] list;
    
    /** Pointer to the current position in the circular list. */
    private int index;
    
    /** 
     * Stores the current state of the circular list. The pointer has to be
     * changed according to this value.
     */
    private int reference;
    
    /** The maxima of all stored values. */
    private float max;
    
    /** Indicates whether the circular list has a full cycle or not. */
    private boolean isFull;
    
    /** Indicates whether the object was allready used or not. */
    private boolean isVirgin;
    
    /**
     * Creates a new MaxCircularList.
     * 
     * @param size The size of the circular list.
     * @param window The window defining the size of the intervals.
     */
    public MaxCircularList(int size) {
        this.list = new float[size];
    }
    
    /**
     * Resets the circular list.
     */
    public void reset() {        
        this.index = 0;
        
        this.isFull = false;
        Arrays.fill(this.list, 0);
        this.max = 0;
        
        this.isVirgin = true;
    }
    
    /**
     * Adds a value to the circular list. The max is directly updated.
     * 
     * @param change Indicates how much the pointer has to move in the circular
     * list.
     * @param value The value to add to the circular list.
     */
    public void add(int reference, float value) {
        if (this.isVirgin) {
            this.isVirgin = false;
            this.reference = reference;
        }
        
        if (reference < this.reference) return;
        
        int change = reference - this.reference;
        this.reference = reference;
        
        if (change == 0) {
            this.list[this.index] = Math.max(this.list[this.index], value);
            this.max = Math.max(this.list[this.index], this.max);
        }
        else {
            this.clean(change);
            
            this.list[this.index] = Math.max(this.list[this.index], value);

            /*
             * find maxima in list
             */
            this.max = 0;
            for (int i = 0; i < this.list.length; i++) {
                this.max = Math.max(this.list[i], this.max);
            }
        }
    }
    
    /**
     * Gets the maxima of all values in the circular list.
     * 
     * @return The maxima of all values in the circular list.
     */
    public float getMax() {
        return this.max;
    }
    
    /**
     * Indicates whether the circular list is full or not.
     * 
     * @return True, if the circular list is full, false otherwise.
     */
    public boolean isFull() {
        return this.isFull;
    }
    
    /**
     * Advances the pointer in the circular list and cleans the list at the
     * same time.
     * 
     * @param change The number of position the pointer has to move.
     */
    private void clean(int change) {
        if (!this.isFull) {
            if (this.index + change >= this.list.length) this.isFull = true;
        }
        
        if (change >= this.list.length) {
            Arrays.fill(this.list, 0);
        }
        else {
            for (int i = 0; i < change; i++) {
                this.index = (this.index + 1) % this.list.length;
                this.list[this.index] = 0;
            }
        }
    }
}
