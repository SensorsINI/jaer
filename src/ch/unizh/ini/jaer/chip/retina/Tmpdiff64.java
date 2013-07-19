/*
 * Tmpdiff128.java
 *
 * Created on October 5, 2005, 11:36 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.jaer.chip.retina;

import java.io.Serializable;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.RetinaExtractor;


/**
 *
 * @author tobi
 */
public class Tmpdiff64 extends AETemporalConstastRetina implements Serializable  {
    
    /** Creates a new instance of Tmpdiff128 */
    public Tmpdiff64() {
        setName("Tmpdiff64");
        setSizeX(64);
        setSizeY(64);
        setNumCellTypes(2);
        setEventExtractor(new Extractor(this));
        setBiasgen(null);
    }
    
    public class Extractor extends RetinaExtractor implements java.io.Serializable{
        public Extractor(AEChip chip){
            super(chip);
            setXmask((short)0x007e);
            setXshift((byte)1);
            setYmask((short)0x3f00);
            setYshift((byte)8);
            setTypemask((short)1);
            setTypeshift((byte)0);
            setFlipx(true);
            setFliptype(true);
        }
     }
    
}
