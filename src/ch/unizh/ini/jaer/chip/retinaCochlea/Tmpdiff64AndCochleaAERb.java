/*
 * Tmpdiff64AndCochleaAERb.java
 *
 * Created on November 16, 2006, 10:32 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.chip.retinaCochlea;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.EventExtractor2D;
import net.sf.jaer.chip.TypedEventExtractor;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.TypedEvent;
import ch.unizh.ini.jaer.chip.cochlea.CochleaAERb;
import ch.unizh.ini.jaer.chip.retina.Tmpdiff64;

/**
 * Simultaneously displays Tmpdiff64 and CochleaAERb in one ChipCanvas. A real hack.
 *
 * @author tobi/rico
 */
public class Tmpdiff64AndCochleaAERb extends AEChip {
    
    /** Creates a new instance of Tmpdiff64AndCochleaAERb */
    public Tmpdiff64AndCochleaAERb() {
         setName("Tmpdiff64AndCochleaAERb");
        setSizeX(64);
        setSizeY(68);
        setNumCellTypes(2);
        setEventExtractor(new Extractor(this));
        setBiasgen(null);
        setEventClass(TypedEvent.class);
    }
    
    public class Extractor extends TypedEventExtractor {
        // uses both chip's extractors
        EventExtractor2D cochleaExtractor=new CochleaAERb().getEventExtractor();
        EventExtractor2D retinaExtractor=new Tmpdiff64().getEventExtractor();
        
        public Extractor(AEChip chip){
            super(chip);
        }
        

     /**gets X from raw address. declared final for speed, cannot be overridden in subclass.
     *@param addr the raw address.
     *@return physical address
     */
    public short getXFromAddress(short addr){
        if(isRetina(addr)){
            return retinaExtractor.getXFromAddress(addr);
        }else{
            return cochleaExtractor.getXFromAddress(addr);
        }
    }
    
    /**  Splits retina and cochlea events to different Y addresses.
     * Gets Y from raw address. 
     *@param addr the raw address.
     *@return physical address
     */
    public short getYFromAddress(short addr){
        if(isRetina(addr)){
            return retinaExtractor.getYFromAddress(addr);
        }else{
            return (short)(cochleaExtractor.getYFromAddress(addr)+64);
        }
    }
    
    /** gets type from raw address. declared final for speed, cannot be overridden in subclass.
     *@param addr the raw address.
     *@return physical address
     */
    public byte getTypeFromAddress(short addr){
        if(isRetina(addr)){
            return retinaExtractor.getTypeFromAddress(addr);
        }else{
            return cochleaExtractor.getTypeFromAddress(addr);
        }
    }
    
    /** extracts the meaning of the raw events.
     *@param in the raw events, can be null
     *@return out the processed events. these are partially processed in-place. empty packet is returned if null is supplied as in.
     */
    @Override synchronized public EventPacket extractPacket(AEPacketRaw in) {
        int n=in.getNumEvents();
        out=super.extractPacket(in);
        
        return out;
    }
     }
    
    private boolean isRetina(short addr){
        boolean check=(addr&0x8000)==0;
//        System.out.println("retina decision "+check);
        return (addr&0x8000)==0; // cochlea has MSB set, retina does not
    }
     
}
