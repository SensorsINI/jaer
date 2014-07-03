/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.bjoernbeyer.visualservo;

import ch.unizh.ini.jaer.hardware.pantilt.PanTiltAimerGUI;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 *
 * @author Bjoern
 */
public class CalibratedStimulus extends EventFilter2D{

    private CalibratedStimulusGUI gui;
    
    public CalibratedStimulus(AEChip chip) {
        super(chip);
        gui = new CalibratedStimulusGUI();
    }
    
    public void doShowGUI() {
        getGui().setVisible(true);
    }
    
    /**
     * @return the gui */
    public CalibratedStimulusGUI getGui() {
        if(gui == null) {
            gui = new CalibratedStimulusGUI();
        }
        return gui;
    }

    @Override public EventPacket<?> filterPacket(EventPacket<?> in) {
        return in;
    }

    @Override public void resetFilter() {
        
    }

    @Override public void initFilter() {
        resetFilter();
    }
    
}
