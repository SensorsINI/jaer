/*
 * Tmpdiff128.java
 *
 * Created on October 5, 2005, 11:36 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.jaer.chip.object;

import java.io.Serializable;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.TypedEventExtractor;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;


/**
 * Describes SC/mao's WTA/object chip.
 * @author tobi
 */
public class Tnc3 extends AEChip implements Serializable  {
    
    /** Creates a new instance of object chip */
    public Tnc3() {
        setEventClass(PolarityEvent.class); // events are labeled at On events if normal type, Off events if WTA global inhibitory cells
        setName("Tnc3");
        setSizeX(32);
        setSizeY(32);
        setNumCellTypes(2);
        setEventExtractor(new Extractor(this));
        setBiasgen(null);
    }
    
    public class Extractor extends TypedEventExtractor implements java.io.Serializable{
        public Extractor(AEChip chip){
            super(chip);
            setXmask((short)0x001f);
            setXshift((byte)0);
            setYmask((short)0x1f00);
            setYshift((byte)8);
            setTypemask((short)0);
            setTypeshift((byte)0);
            setFlipx(true);
        }
        
        /** extracts the meaning of the raw events.
         *@param in the raw events, can be null
         *@return out the processed events. these are partially processed in-place. empty packet is returned if null is supplied as in.
         */
        @Override synchronized public EventPacket extractPacket(AEPacketRaw in) {
            out=super.extractPacket(in);
            for(Object o:out){
                PolarityEvent e=(PolarityEvent)o;
                if(e.x==0 || e.x==16){
                    if( e.y==14 || e.y==15 || e.y==30 || e.y==31){
                        e.polarity=PolarityEvent.Polarity.Off;
                        e.type=0;
                    }else{
                        e.polarity=PolarityEvent.Polarity.On;
                        e.type=1;
                    }
                }else{
                    e.polarity=PolarityEvent.Polarity.On;
                    e.type=1;
                }
            }
            return out;
        }
    }
    
}
