/*
 * RetinaExtractor.java
 *
 * Created on May 31, 2006, 1:16 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright May 31, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package net.sf.jaer.chip;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;

/**
 * Extracts polarity of cells from the type of event. Built on top of legacy extractor.
 * @author tobi
 */
public class RetinaExtractor extends TypedEventExtractor {
    
    /** Creates a new instance of RetinaExtractor */
    public RetinaExtractor(AEChip chip) {
        super(chip);
    }
    
        /** extracts the meaning of the raw events.
     *@param in the raw events, can be null
     *@return out the processed events. these are partially processed in-place. empty packet is returned if null is supplied as in.
     */
    @Override synchronized public EventPacket extractPacket(AEPacketRaw in) {
        out=super.extractPacket(in);
        
        int n=in.getNumEvents();
        if(n==0) return out;
        for(Object obj:out){
            PolarityEvent e=(PolarityEvent)obj;
            e.polarity=e.type==0? PolarityEvent.Polarity.Off:PolarityEvent.Polarity.On;
        }
        return out;
    }

}
