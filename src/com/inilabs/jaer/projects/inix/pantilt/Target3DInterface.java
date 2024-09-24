/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.inilabs.jaer.projects.inix.pantilt;


/**
 *
 * @author rjd
 *
 * This is part of iniLabs 
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public interface Target3DInterface{

    /** Target tracked 3D space.
     *
     * @return the target, or null if there is no target
     */
    public Target3DInterface findBestTarget();


}
