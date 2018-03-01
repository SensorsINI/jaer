/*
 * UioColorOctopus22.java
 *
 * Created on May 2011
 *
 * Describes a Octopus Color Retina
 *
 * Authors: Juan A. Lenero and Philipp Hafliger
 *
 */

package no.uio.ifi.jaer.chip.coloroctopus22;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.TypedEventExtractor;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.graphics.AdaptiveIntensityRendererColor;
import net.sf.jaer.graphics.ChipRendererDisplayMethod;
import net.sf.jaer.graphics.DisplayMethod;



public class UioColorOctopus22 extends AEChip {
    
    /** Creates a new instance of UioStaticBioVis */
    public UioColorOctopus22() {
        setSizeX(22);
        setSizeY(22);
        setNumCellTypes(4); // there are actually three types (RGB color) of events from each x,y but the bits indicating the type are 00 01 11, i.e type 10 does not mean anything
        setEventClass(TypedEvent.class); // the chip puts out these kinds of events
        setEventExtractor(new Extractor(this)); // we use the default extractor here
        setBiasgen(null); // don't have one on this chip
        setRenderer(new AdaptiveIntensityRendererColor(this));

        /* To not bother everyone with my own display methods. */
        DisplayMethod cdm = new ChipRendererDisplayMethod(getCanvas());
        getCanvas().addDisplayMethod(cdm);
        getCanvas().setDisplayMethod(cdm);       

    }
    
    public class Extractor extends TypedEventExtractor implements java.io.Serializable{
        public Extractor(AEChip chip){
            super(chip);
            setEventClass(TypedEvent.class);
            setXmask((short)0x003e); // mask this part for x
            setXshift((byte)1);
            setYmask((short)0x3e00);
            setYshift((byte)9);
            setTypemask((short)0x0101);
            setTypeshift((byte)0);
            setFlipx(true);
           }

        //In our particular case, the two color bits are not consequtive. We have to
        //modify the function getTypeFromAddress to get the two color bits.

      @Override public byte getTypeFromAddress(int addr){

        if ((((addr>>>8)&0x0001)==0)&&((addr&0x0001)==0)) return (byte) 1;//RG
        if ((((addr>>>8)&0x0001)==1)&&((addr&0x0001)==0)) return (byte) 3;//GB
        if ((((addr>>>8)&0x0001)==1)&&((addr&0x0001)==1)) return (byte) 2;//B
        else return (byte) 0;
        }
    }


}
