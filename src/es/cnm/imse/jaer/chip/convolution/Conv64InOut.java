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

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.TypedEventExtractor;
import net.sf.jaer.event.TypedEvent;


/**
 * Chip description for quad combination of conv32 imse, displaying input and output simultaneous, 
 * by additional x bit.
 * @author raphael
 */
public class Conv64InOut extends AEChip implements Serializable  {
    
    /** Creates a new instance of Tmpdiff128 */
    public Conv64InOut() {
        setName("Conv64");
        setSizeX(128);
        setSizeY(64);
        setEventClass(TypedEvent.class);
        setNumCellTypes(2);
        setEventExtractor(new Extractor(this));
        setBiasgen(null);
    }
    
    public class Extractor extends TypedEventExtractor implements java.io.Serializable{
        public Extractor(AEChip chip){
            super(chip);
            setXmask((short)0x007f);
            setXshift((byte)0);
            setYmask((short)0x3f00);
            setYshift((byte)8);
            setTypemask((short)0x80);
            setTypeshift((byte)7);
            setFlipx(true);
            setFliptype(false);
        }
     }
    
}
