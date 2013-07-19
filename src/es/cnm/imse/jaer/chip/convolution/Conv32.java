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
 *  Chip description for of conv32 imse raphael/bernabe convolution chip.
 * @author patrick/raphael
 */
public class Conv32 extends AEChip implements Serializable  {
    
    /** Creates a new instance of Tmpdiff128 */
    public Conv32() {
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
            setEventClass(TypedEvent.class);
             setXmask((short)0x001f);
            setXshift((byte)0);
            setYmask((short)0x1f00);
            setYshift((byte)8);
            setTypemask((short)1);
            setTypeshift((byte)0);
            setFlipx(true);
        }
     }
    
}
