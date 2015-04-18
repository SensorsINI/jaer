/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.eventprocessing.filter;
import java.util.Random;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
/**
 * This filter lets through events with some fixed probability.
 * @author tobi
 */
@Description("Passes events probabilistically")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class ProbabalisticPassageFilter extends EventFilter2D{
    
    private float passProb=getPrefs().getFloat("TestFilter.passProb",.5f);
    Random r=new Random();
    
    public ProbabalisticPassageFilter(AEChip chip){
        super(chip);
        setPropertyTooltip("passProb","probability that event passes through filter");
    }
    
    /** This filterPacket method assumes the events have PolarityEvent type
     * 
     * @param in the input packet
     * @return the output packet, where events have possibly been deleted from the input
     */
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!isFilterEnabled()) return in;
        checkOutputPacketEventType(in); // make sure the built-in output packet has same event type as input packet
        OutputEventIterator outItr=out.outputIterator(); // getString the built in iterator for output events
        for(Object o:in){ // iterate over input events
            PolarityEvent e=(PolarityEvent)o; // cast to asssumed input type
            if(r.nextFloat()<getPassProb()){
                PolarityEvent oe=(PolarityEvent) outItr.nextOutput(); // if we pass input, obtain next output event
                oe.copyFrom(e); // copy fields from input event
            }
        }
        return out;
    }

    @Override
    public void resetFilter() {
        
    }

    @Override
    public void initFilter() {
        
    }

    public

    float getPassProb() {
        return passProb;
    }

    public void setPassProb(float passProb) {
        passProb=passProb>1?1:passProb;
        passProb=passProb<0?0:passProb;
        this.passProb=passProb;
        getPrefs().putFloat("TestFilter.passProb", passProb);
    }

}
