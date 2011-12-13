/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.indexedvalue;

import java.util.Arrays;

/**
 *
 * @author matthias
 */
public class MultiDimensionalIndexedValues extends AbstractIndexedValues {

    private int dimensions;
    private int limit;
    
    private int[][] history;
    private int[] sum;
    private int pointer;
    
    private int[] maximas;
    private int max;
    
    private int counter;
    
    public MultiDimensionalIndexedValues() {
        super();
        
        this.dimensions = 5;
        this.limit = 1000;
        
        this.init();
        this.reset();
    }
    
    public MultiDimensionalIndexedValues(int start, int step, int nBins, int dimensions, int limit) {
        super(start, step, nBins);
        
        this.dimensions = dimensions;
        this.limit = limit;
        
        this.init();
        this.reset();
    }
    
    @Override
    public void add(int key, int value) {
        if (key < this.start || key > this.end) return;
        
        value /= 1000;
        
        this.counter++;
        if (this.counter > this.limit) {
            this.counter = 0;
            this.pointer = (this.pointer + 1) % this.dimensions;
            
            for (int i = 0; i < this.nBins; i++) {
                this.sum[i] -= this.history[this.pointer][i];
            }
            Arrays.fill(this.history[this.pointer], 0);
            
            if (this.max == this.maximas[this.pointer]) {
                this.max = this.maximas[0];
                
                for (int i = 1; i < this.dimensions; i++) {
                    if (this.max < this.maximas[i]) {
                        this.max = this.maximas[i];
                    }
                }
            }
            this.maximas[this.pointer] = 0;
        }
        
        int index = (key - this.start) / this.step;
        
        this.sum[index] += value;
        this.history[this.pointer][index] += value;
        
        int absValue = Math.abs(this.history[this.pointer][index]);
        if (this.maximas[this.pointer] < absValue) {
            this.maximas[this.pointer] = absValue;
            
            if (this.max < absValue) {
                this.max = absValue;
            }
        }
    }

    @Override
    public int get(int index) {
        if (index < 0 && index >= this.nBins) return 0;
        return this.sum[index];
    }

    @Override
    public float getNormalized(int index) {
        return this.get(index) / (float)this.getMax();
    }

    @Override
    public int getMax() {
        return this.max;
    }

    @Override
    public int getSize() {
        return this.nBins;
    }

    @Override
    public void init() {
        this.history = new int[this.dimensions][this.nBins];
        this.sum = new int[this.nBins];
        this.maximas = new int[this.dimensions];
    }

    @Override
    public void reset() {
        Arrays.fill(this.sum, 0);
        for (int i = 0; i < this.history.length; i++) {
            Arrays.fill(this.history[i], 0);
        }
        Arrays.fill(this.maximas, 0);
        this.max = 0;
        this.pointer = 0;
        
        this.counter = 0;
    }
}
