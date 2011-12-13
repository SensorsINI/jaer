/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.moment;

/**
 *
 * @author matthias
 * 
 * Computes the central moments of the images. For more information about
 * moments visite http://en.wikipedia.org/wiki/Image_moment.
 */
public interface MomentExtractor {
    
    /**
     * Gets the i,j-moment of the image.
     * 
     * Returns the i,j-moment of the image.
     */
    public int getMoment(int i, int j);
}
