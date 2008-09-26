/*
 * Tmpdiff128.java
 *
 * Created on October 5, 2005, 11:36 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.dollbrain;

import ch.unizh.ini.caviar.aemonitor.*;
import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.chip.TypedEventExtractor;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.graphics.*;
import java.io.*;


/**
 *  Chip description for of conv32 imse raphael/bernabe convolution chip.
 * @author patrick/raphael
 */
public class ColorContrastRetinaTestChip extends AEChip implements Serializable  {
    
    /** Creates a new instance of Dollbrain */
    public ColorContrastRetinaTestChip() {
        setSizeX(2);
        setSizeY(2);
        setNumCellTypes(2);
        setEventClass(TypedEvent.class);
        setEventExtractor(new Extractor(this));
       // setRenderer(new Dollbrain1Renderer(this));
    }
    
    
    public class Extractor extends TypedEventExtractor implements java.io.Serializable{
        
        
        public Extractor(AEChip chip){
            super(chip);
            setEventClass(ColorEvent.class);
            setXmask(0x02);
            setXshift((byte)1);
            setYmask(0x01);
            setYshift((byte)0);
            setTypemask(0x01);
            setTypeshift((byte)0);
     
            //setFlipx(true);
        }
        
 
 
        
        
    
   
        }
    
}
