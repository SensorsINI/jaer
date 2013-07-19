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

import net.sf.jaer.chip.RetinaExtractor;
import ch.unizh.ini.jaer.chip.cochlea.CochleaGramDisplayMethod;
import ch.unizh.ini.jaer.chip.cochlea.RollingCochleaGramDisplayMethod;


/**
 *
 * @author tobi
 */
public class TestchipARCSLineSensor extends AETemporalConstastRetina implements Serializable  {
    
    /** Creates a new instance of Tmpdiff128 */
    public TestchipARCSLineSensor() {
        setSizeX(128);
        setSizeY(2);
        setNumCellTypes(2);
        setBiasgen(new Tmpdiff128().getBiasgen());
        setEventExtractor(new Extractor(this));
        new CochleaGramDisplayMethod(getCanvas());
        new RollingCochleaGramDisplayMethod(getCanvas());
    }
    
    /** the event extractor for double line sensor. Has two polarities 0 and 1.
     *The X address is flipped so 63 becomes 0, 0 becomes 63.
     * The polarity is flipped by the extractor so that the raw polarity 0 becomes 1
     * in the extracted event. The ON events have raw polarity 0.
     * 1 is an ON event after event extraction, which flips the type. Raw polarity 1 is OFF event, which becomes 0 after extraction.
     */
    public class Extractor extends RetinaExtractor implements java.io.Serializable{
        public Extractor(TestchipARCSLineSensor chip){
            super(chip);
            
            setXmask((short)0x3f); // 0011 1111,  x are 6 bits (64 cols) ranging from bit 0-5
            setXshift((byte)0);
            setYmask((short)0x40); // 0100 0000
            setYshift((byte)6);
            setTypemask((short)0x80); // 1000 0000
            setTypeshift((byte)7);
//            setFlipx(true);
            setFliptype(true);
        }
        public short getXFromAddress(short addr){
            short x=super.getXFromAddress(addr);
            short y=super.getYFromAddress(addr);
            if(y==0) return x; else return (short)(x+64);
        }
    }
    
}
