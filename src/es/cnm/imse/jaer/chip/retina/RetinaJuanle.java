/*
 * Tmpdiff128.java
 *
 * Created on October 5, 2005, 11:36 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package es.cnm.imse.jaer.chip.retina;

import java.io.Serializable;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.TypedEventExtractor;
import net.sf.jaer.event.TypedEvent;


/**
 * Retina 32x32 sensible al contraste
 * @author juan antonio
 */
public class RetinaJuanle extends AEChip implements Serializable  {
    
    /** Creates a new instance of Tmpdiff128 */
    public RetinaJuanle() {
        setName("RetinaJuanle");
        setSizeX(32);
        setSizeY(32);
        setNumCellTypes(2);
        setEventClass(TypedEvent.class);
        setEventExtractor(new Extractor(this));
        setBiasgen(null);
    }
    
    public class Extractor extends TypedEventExtractor implements java.io.Serializable{
        public Extractor(AEChip chip){
            super(chip);
                       
            setXmask((short)0x3e00);
            setXshift((byte)9);
          
            setYmask((short)0x001f);
            setYshift((byte)0);
            setTypemask((short)0x0100);
            
            setTypeshift((byte)8);
            setFlipx(true);
            setFliptype(false);
        }
     }
    
}
