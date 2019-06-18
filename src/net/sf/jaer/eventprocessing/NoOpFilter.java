/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;

/**
 * An event-processing method that does nothing, just to measure base performance of iteration and event copying
 * @author tobi
 */
@Description("A do-nothing filter used to measure cost of packet iteration and event-copying")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class NoOpFilter extends EventFilter2D {
    
    public boolean copyInputPacket=false;
    public boolean iterateOverPacket=true;

    public NoOpFilter(AEChip chip) {
        super(chip);
        setPropertyTooltip("copyInputPacket", "copies each event in input packet");
        setPropertyTooltip("iterateOverPacket", "runs iterator over packet");
    }

    
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        OutputEventIterator outItr=null;
        if(copyInputPacket){
            checkOutputPacketEventType(in);
            outItr=getOutputPacket().getOutputIterator();
        }
        if(iterateOverPacket){
            for(BasicEvent e:in){
                if(copyInputPacket){
                    BasicEvent oe=outItr.nextOutput();
                    oe.copyFrom(e);
                }
            }
        }
        if(iterateOverPacket && copyInputPacket){
            return getOutputPacket();
        }else{
            return in;
        }
    }

    @Override
    public void resetFilter() {
    }

    @Override
    public void initFilter() {
    }

    /**
     * @return the copyInputPacket
     */
    public boolean isCopyInputPacket() {
        return copyInputPacket;
    }

    /**
     * @param copyInputPacket the copyInputPacket to set
     */
    public void setCopyInputPacket(boolean copyInputPacket) {
        this.copyInputPacket = copyInputPacket;
    }

    /**
     * @return the iterateOverPacket
     */
    public boolean isIterateOverPacket() {
        return iterateOverPacket;
    }

    /**
     * @param iterateOverPacket the iterateOverPacket to set
     */
    public void setIterateOverPacket(boolean iterateOverPacket) {
        this.iterateOverPacket = iterateOverPacket;
    }
    
    
}
