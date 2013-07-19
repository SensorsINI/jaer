/*
 * Tmpdiff128.java
 *
 * Created on October 5, 2005, 11:36 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package no.uio.ifi.jaer.chip.learning;

import java.io.Serializable;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.TypedEventExtractor;


/**
 * Describes phillips learning classifier chip.
 *
 * @author tobi
 */
public class Learning extends AEChip implements Serializable  {
    
    /** Creates a new instance of Tmpdiff128 */
    public Learning() {
        setName("Learning");
        setSizeX(32);
        setSizeY(2);
        setNumCellTypes(1);
        setEventExtractor(new Extractor(this));
        setBiasgen(null);
    }
    
    public class Extractor extends TypedEventExtractor implements java.io.Serializable{
        public Extractor(AEChip chip){
            super(chip);
            setXmask((short)0x001f);
            setXshift((byte)0);
            setYmask((short)0x4000);
            setYshift((byte)14);
            setTypemask((short)1);
            setTypeshift((byte)0);
            setFlipx(true);
//            setFliptype(true);
        }
     }
    
}
