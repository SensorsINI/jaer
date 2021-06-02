/*
 * LineDetector.java
 *
 * Created on June 29, 2007, 7:08 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.jaer.eventprocessing.tracking;

/**
 * EventFilters implement this interface when they detect a line in scene. The line is characterized by the normal form (rho,theta) where
 theta is the angle of the line and rho is the closest distance of the line from the origin. These coordinates are illustrated below.
 Note that rho can be positive or negative but theta is bound to (0,180). Theta is cut at 0 and 180 degrees and 0 and 180 degrees can both represent
 the same line with negated rho. Note also that theta is the angle of the <strong>normal</strong> to the line, not the angle of the line itself.
 <p>
 <img src="doc-files/LineDetector.png" />
 
 * @author tobi
 */
public interface LineDetector {
    /**
     * returns the filtered Hough line radius estimate - the closest distance from the middle of the chip image.
     @return the distance in pixels. If the chip size is sx by sy, can range over +-Math.sqrt( (sx/2)^2 + (sy/2)^2).
     This number is positive if the line is above the origin (center of chip).
     */
    float getRhoPixelsFiltered();

    /**
     *     returns the filtered angle of the normal to the line. Ranges from 0 to 180 degrees, 
     where 0 and 180 represent a vertical line and 90 is a horizontal line. Rho can be negative and 0 and 180 can represent 
     the same line.
     * 
     * @return angle in degrees. 
     
     */
    float getThetaDegFiltered();
    
}
