/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.gesture.wakeup;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 * Activity and simple gesture-based wakeup detector.
 * @author tobi
 */
@Description("Activity-level and simple gesture wakeup detector")
public class WakeupDetector extends EventFilter2D{

    public WakeupDetector(AEChip chip) {
        super(chip);
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
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
