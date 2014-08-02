
package ch.unizh.ini.jaer.projects.bjoernbeyer.stimulusdisplay;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;

/**
 *
 * @author Bjoern
 */
@Description("Provides a GUI for displaying various kinds of stimuli and motion for target tracking quantification")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class StimulusDisplay extends EventFilter2D{

    private StimulusDisplayGUI gui;
    
    public StimulusDisplay(AEChip chip) {
        super(chip);
        gui = new StimulusDisplayGUI();
    }
    
    public void doShowGUI() {
        getGui().setVisible(true);
    }
    
    /**
     * @return the gui */
    public StimulusDisplayGUI getGui() {
        if(gui == null) {
            gui = new StimulusDisplayGUI();
        }
        return gui;
    }

    @Override public EventPacket<?> filterPacket(EventPacket<?> in) {
        return in;
    }

    @Override public void resetFilter() { }
    @Override public void initFilter() { } 
}
