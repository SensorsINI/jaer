/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.virtualdrummer;

/**
 *
 * @author tobi
 */
public interface SoundPlayerInterface {

    void close ();

    /**
     * plays the spike sound once, by notifying the player thread to send the data to the line.
     */
    void play ();

}
