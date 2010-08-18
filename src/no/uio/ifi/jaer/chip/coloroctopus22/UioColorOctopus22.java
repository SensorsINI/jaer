/*
 * UioStaticBioVis.java
 *
 * Created on 13. november 2007, 13:12
 *
 * Describes the StaticBioVis Chip by Jenny Anna Maria Olsson
 */

package no.uio.ifi.jaer.chip.coloroctopus22;

import no.uio.ifi.jaer.chip.staticbiovis.*;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.TypedEventExtractor;
import net.sf.jaer.event.*;
import net.sf.jaer.graphics.*;

/**
 * The chip is a 92x92 pixels imager which converts illumination into a 
 * frequency of events.
 *
 * The class uses UioCameraRenderer to display frames with Gray-Levels on a 
 * black background and UioStaticBioVisDisplayMethod to generate an image of 
 * the pixel output.
 *
 * @author Jenny Anna Maria Olsson (jaolsson@ifi.uio.no)
 */
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
            setXmask((short)0x007c); // mask this part for x
            setXshift((byte)2); //
            setYmask((short)0x1f00);
            setYshift((byte)8);
            setTypemask((short)0x3);
            setTypeshift((byte)0);
            setFlipx(true);
        }
    }
    
}
