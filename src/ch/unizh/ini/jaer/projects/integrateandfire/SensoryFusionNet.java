/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.integrateandfire;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.MultiSourceProcessor;

/**
 *
 * @author Peter
 */
public class SensoryFusionNet extends MultiSourceProcessor {

    SensoryFusionNet(AEChip chip)
    {   super(chip);
        
    }
    
    @Override
    public String[] getInputNames() {
        return new String[] {"Retina","Cochlea"};
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
