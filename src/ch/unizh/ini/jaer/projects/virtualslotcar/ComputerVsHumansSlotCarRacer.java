/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.virtualslotcar;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 * Races computer vs humans on slot car track.
 * 
 * @author tobi
 */
public class ComputerVsHumansSlotCarRacer extends EventFilter2D{

    public ComputerVsHumansSlotCarRacer(AEChip chip) {
        super(chip);
        setPropertyTooltip("defineHumanTrack", "define the human controlled track by enabling the human TrackHistogramFilter");
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        return in;
    }
    
    public void doDefineComputerTrack(){
        
    }
    
    public void doDefineHumanTrack(){
        
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }
    
}
