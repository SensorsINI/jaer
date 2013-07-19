/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.spatiatemporaltracking.tracker.feature.implementations.event.packet;

import java.util.List;

import net.sf.jaer.event.TypedEvent;

/**
 *
 * @author matthias
 * 
 * This interface is used to notifiy all extractor about a new available event.
 */
public interface PacketEventExtractor {
    
    /**
     * Gets the packet stored on this extractor.
     * 
     * @return The packet stored on this extractor.
     */
    public List<TypedEvent> getPacket();
}
