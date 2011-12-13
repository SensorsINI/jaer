/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker;

import net.sf.jaer.event.EventPacket;

/**
 *
 * @author matthias
 * 
 * Tracks blinking LEDs by using single events.
 */
public interface EventTracker extends Tracker {
    
    /**
     * Tracks blinking LEDs by using single events.
     * 
     * @param in The packet containing the used events.
     */
    public void track(EventPacket<?> in);
}
