/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.fouriertransform;

/**
 *
 * @author matthias
 * 
 * The interface window provides methods to compute the shape of a windowing
 * function used for the discrete fourier transformation.
 */
public interface Window {
    /**
     * Gets the value of the windowing function for the given sample.
     * 
     * @param sample The number of the sample.
     * @return The value of the windowing function.
     */
    public double get(int sample);
}
