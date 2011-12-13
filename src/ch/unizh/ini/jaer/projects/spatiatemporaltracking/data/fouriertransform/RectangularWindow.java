/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.fouriertransform;

/**
 *
 * @author matthias
 * 
 * The implementation of the rectangular window function. For more information 
 * visite http://en.wikipedia.org/wiki/Window_function.
 */
public class RectangularWindow implements Window {
    
    /** The number of samples supported by this windowing function. */
    private int N;
    
    /** Stores the values of the function. */
    private double[] function;
    
    /**
     * Creates a new RectangularWindow.
     * 
     * @param N The number of samples supported by this windowing function.
     */
    public RectangularWindow(int N) {
        this.N = N;
        this.function = new double[this.N];
        
        for (int i = 0; i < this.N; i++) {
            this.function[i] = 1;
        }
    }
    
    @Override
    public double get(int sample) {
        if (sample < 0 || sample >= this.N) return 0;
        
        return this.function[sample];
    }
}
