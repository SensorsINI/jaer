/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.inilabs.jaer.projects.tracker;


/**
 *
 * @author rjd
 *
 * This is part of iniLabs 
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public interface GimbalControllerInterface{

    /** Finds and then Returns the best target, being tracked 3D space.
     *
     * @return the target, or null if there is no good target
     */
    public GimbalControllerInterface findBestTarget();


}
