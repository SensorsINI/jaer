/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.tell2011;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 * Tracks median event location
 * @author tobi
 */
public class MedianTrackerTell2011 extends EventFilter2D{

    private int filterLength=getInt("filterLength",50);
    
    public MedianTrackerTell2011(AEChip chip) {
        super(chip);
    }

    
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        return in;
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }
    
}
