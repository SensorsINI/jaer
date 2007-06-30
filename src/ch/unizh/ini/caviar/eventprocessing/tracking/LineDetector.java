/*
 * LineDetector.java
 *
 * Created on June 29, 2007, 7:08 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.caviar.eventprocessing.tracking;

/**
 * EventFilters implement this interface when they detect a line in scene.
 
 * @author tobi
 */
public interface LineDetector {
    /**
     * returns the filtered Hough line radius estimate - the closest distance from the middle of the chip image.
     * 
     * @return the distance in pixels. If the chip size is sx by sy, can range over +-Math.sqrt( (sx/2)^2 + (sy/2)^2).
     *     This number is positive if the line is above the origin (center of chip)
     */
    float getRhoPixelsFiltered();

    /**
     *     returns the filtered angle of the line.
     * 
     * @return angle in degrees. 
     Ranges from 0 to 180 degrees, 
     where 0 and 180 represent a vertical line and 90 is a horizontal line
     */
    float getThetaDegFiltered();
    
}
