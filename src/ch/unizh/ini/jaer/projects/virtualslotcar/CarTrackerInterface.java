/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.virtualslotcar;
/**
 *
 * A slot car tracker implements this interface.
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaerproject.net/">jaerproject.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public interface CarTrackerInterface{

    /** Finds and then Returns the putative car cluster. This method may be expensive.
     *
     * @return the car cluster, or null if there is no good cluster
     */
    public CarClusterInterface findCarCluster();


}
