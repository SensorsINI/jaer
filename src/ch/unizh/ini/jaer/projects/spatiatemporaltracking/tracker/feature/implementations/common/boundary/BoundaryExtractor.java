/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.common.boundary;

/**
 *
 * @author matthias
 * 
 * This type of FeatureExtractor has to extract the boundry of the observed
 * object. This boundry defines an area in which most of the events of the
 * assigned object is occured.
 */
public interface BoundaryExtractor {
    
    /**
     * Gets the length of the object on the major axis.
     * 
     * @return The length on the major axis.
     */
    public float getMajorLength();
    
    /**
     * Gets the length of the object on the minor axis.
     * 
     * @return The length on the minor axis.
     */
    public float getMinorLength();
    
    /**
     * Gets the angle between the major axis and the x-axis.
     * 
     * @return The angle between the major axis and the x-axis.
     */
    public float getAngle();
}
