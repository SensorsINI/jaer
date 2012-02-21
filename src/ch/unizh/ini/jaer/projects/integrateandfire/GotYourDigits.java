/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.integrateandfire;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 *
 * @author Peter
 */
public class GotYourDigits extends EventFilter2D {

    public GotYourDigits(AEChip chip){
        super(chip);
        
    }
        
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> P) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void resetFilter() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void initFilter() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
