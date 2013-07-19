/*
 * Tmpdiff128.java
 *
 * Created on October 5, 2005, 11:36 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package es.cnm.imse.jaer.chip.convolution;

import java.io.Serializable;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.TypedEvent;


/**
 *  Chip description for quad combination of conv32 imse raphael/bernabe convolution chip that doesn't extract negative correlation events.
 *These events are not extracted so that the rendering is less distracting.
 *
 * @author tobi
 */
public class Conv64NoNegativeEvents extends Conv64 implements Serializable  {
    
    /** Creates a new instance of Tmpdiff128 */
    public Conv64NoNegativeEvents() {
        super();
//        setSizeX(64);
//        setSizeY(64);
//        setNumCellTypes(2);
        setEventExtractor(new Extractor(this)); // we want THIS extractor, not the supertype's
//        setBiasgen(null);
    }
    
    public class Extractor extends Conv64.Extractor implements java.io.Serializable{
        public Extractor(AEChip chip){
            super(chip);
//            setXmask((short)0x003f);
//            setXshift((byte)0);
//            setYmask((short)0x3f00);
//            setYshift((byte)8);
//            setTypemask((short)0x80);
//            setTypeshift((byte)7);
//            setFlipx(true);
//            setFliptype(false);
        }
        
        static final int NEG_TYPE=0;
        
        EventPacket outNew=null;
        
        /** extracts the meaning of the raw events. Overrides base method to throw away some events.
         *@param in the raw events, can be null
         *@return out the processed events. empty packet is returned if null is supplied as in.
         */
        synchronized public EventPacket extractPacket(AEPacketRaw in) {
            EventPacket out=super.extractPacket(in);
            if(outNew==null) outNew=new EventPacket(getEventClass());
            OutputEventIterator oi=outNew.outputIterator();
            for(Object o:out){
                TypedEvent e=(TypedEvent)o;
                if(e.type!=NEG_TYPE){
                    TypedEvent oe=(TypedEvent)oi.nextOutput();
                    oe.copyFrom(e);
                }
            }
            return outNew;
        }
        
        
    }
    
}
