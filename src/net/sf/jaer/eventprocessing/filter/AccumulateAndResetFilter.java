/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.filter;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.AEChipRenderer;

/**
 * 
 * Sets accumulate mode on AEViewer display and resets at fixed DVS event count
 * @author tobi
 */
@Description("Sets accumulate mode on AEViewer display and resets at fixed DVS event count")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class AccumulateAndResetFilter extends EventFilter2D{

    private int numDvsEventsToResetAccumulation=getInt("numDvsEventsToResetAccumulation",5000);
    
    public AccumulateAndResetFilter(AEChip chip) {
        super(chip);
        setPropertyTooltip("numDvsEventsToResetAccumulation", "sets number of dvs events to reset accumulation of image");
    }

    int numEventsAccumulated=0;
    
    
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        AEChipRenderer renderer=chip.getRenderer();
        if(numEventsAccumulated>=getNumDvsEventsToResetAccumulation()){
            renderer.resetFrame(renderer.getGrayValue());
            numEventsAccumulated=0;
        }
        numEventsAccumulated+=in.getSize();
        return in;
    }
    

    @Override
    public void resetFilter() {
        numEventsAccumulated=getNumDvsEventsToResetAccumulation();
    }

    @Override
    public void initFilter() {
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes); 
        chip.getRenderer().setAccumulateEnabled(yes);
    }
    
    /**
     * @return the numDvsEventsToResetAccumulation
     */
    public int getNumDvsEventsToResetAccumulation() {
        return numDvsEventsToResetAccumulation;
    }

    /**
     * @param numDvsEventsToResetAccumulation the numDvsEventsToResetAccumulation to set
     */
    public void setNumDvsEventsToResetAccumulation(int numDvsEventsToResetAccumulation) {
        this.numDvsEventsToResetAccumulation = numDvsEventsToResetAccumulation;
        putInt("numDvsEventsToResetAccumulation",numDvsEventsToResetAccumulation);
    }
    
}
