/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.cluster;

/**
 *
 * @author matthias
 * 
 * Adds notices to the object. Helps to display additional information during
 * the process.
 * 
 */
public interface Noticeable {
    /*
     * Adds a notice to the object with a specific key to identify the notice.
     * 
     * @param key The key to identify the notice.
     * @param value The value of the notice.
     */
    public void add(String key, String value);
}
