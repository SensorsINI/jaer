/*
 * Tmpdiff128.java
 *
 * Created on October 5, 2005, 11:36 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package es.us.atc.jaer.chips.NodeBoard;

import ch.unizh.ini.jaer.chip.dollbrain.ColorEvent;
import java.io.*;
import net.sf.jaer.chip.*;
import net.sf.jaer.event.TypedEvent;
//import net.sf.jaer.event.*;


/**
 * Alejandro Linares-Barranco's 256x256 Framegrabber for AER NodeBoard.
 *
 * @author Alejandro Linares-Barranco
 */
public class Framegrabber extends AEChip implements Serializable  {
    
    /** Creates a new instance of Tmpdiff128 */
    public Framegrabber() {
        setName("Framebrabber");
        setSizeX(128);
        setSizeY(128);
        setNumCellTypes(2);
        setEventClass(TypedEvent.class);
        setEventExtractor(new Extractor(this));
        setBiasgen(null);
    }
    
    public class Extractor extends TypedEventExtractor implements java.io.Serializable{
        public Extractor(AEChip chip){
            super(chip);
            /* setXmask((short)0x003f); */            
            setXmask((short)0x00ff);
            //setXshift((byte)1);
            /* setYmask((short)0x3f00); */
            setYmask((short)0xff00);
            //setYshift((byte)8);
            //setTypemask((short)1);
            /* setTypeshift((byte)7); */
            //setTypeshift((byte)0);
            //setFlipx(false);
            //setFlipy(true);
            //setFliptype(true);
        }
     }
    
}
