/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data;

import java.util.Arrays;

/**
 *
 * @author matthias
 * 
 * Computes the sum of a circular list.
 */
public class IntegerSummedCircularList {
    /** Structure for the circular list. */
    protected int[] list;
    
    /** Stores the computed sum. */
    protected int sum;
    
    /** Stores the number of elements stored in the circular list. */
    protected int N;
    
    /** Indicates whether the circular list is full or not. */
    protected boolean isFull;
    
    /** Pointer to the current position in the circular list. */
    protected int index;
    
    /**
     * Creates a new IntegerSummedCircularList.
     * 
     * @param size The size of the circular list.
     */
    public IntegerSummedCircularList(int size) {
        this.list = new int[size];
    }
    
    /**
     * Resets the circular list.
     */
    public void reset() {
        Arrays.fill(this.list, 0);
        this.sum = 0;
        this.N = 0;
        this.isFull = false;
        
        this.index = 0;
    }
    
    /**
     * Adds a value to the circular list. The sum is directly updated.
     * 
     * @param change Indicates how much the pointer has to move in the circular
     * list.
     * @param value The value to add to the circular list.
     */
    public void add(int change, int value) {
        this.clean(change);

        this.sum += value;
        this.list[this.index] += value;
        
        if (!this.isFull) {
            this.N += change;
            
            if (this.N > this.list.length) {
                this.isFull = true;
                this.N = this.list.length;
            }
        }
    }
    
    /**
     * Gets the sum of all values in the circular list.
     * 
     * @return The sum of all values in the circular list.
     */
    public int getSum() {
        return this.sum;
    }
    
    /**
     * Gets the number of elements in the circular list.
     * 
     * @return The number of elements in the circular list.
     */
    public int getSize() {
        return this.N;
    }
    
    /**
     * Advances the pointer in the circular list and cleans the list at the
     * same time.
     * 
     * @param change The number of position the pointer has to move.
     */
    private void clean(int change) {
        if (change >= this.list.length) {
            Arrays.fill(this.list, 0);
            this.sum = 0;
        }
        else {
            for (int i = 0; i < change; i++) {
                this.index = (this.index + 1) % this.list.length;
                this.sum -= this.list[this.index];
                this.list[this.index] = 0;
            }
        }
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
     * Gets the capacity of the circular list.
     * 
     * @return The capacity of the circular list.
     */
    public int getCapacity() {
        return this.list.length;
    }
}
