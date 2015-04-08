/*
 * UioFoveatedImager.java
 *
 * Created on 11. mai 2006, 16:46
 *
 * Describes the Foveated Imager Chip by Mehdi Azadmehr
 *
 */

package no.uio.ifi.jaer.chip.foveated;

import java.io.Serializable;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.TypedEventExtractor;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.graphics.AdaptiveIntensityRenderer;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.graphics.RetinaCanvas;
import ch.unizh.ini.jaer.chip.retina.AETemporalConstastRetina;

/**
 * To describe Mehdi Azadmehrs Foveated Imager Chip
 * This chip has an adress space of 83x87. 
 * The perifery has 4 rows/columns of adaptive (motion-sensitive) pixels. These 
 * pixels are twice as wide and twice as tall as the pixels in the center. These 
 * adresses have been mapped so every second adress carries pixel information.
 * The fovea contains 67x70 pixels where the last y-row has been shifted one row
 * down, thus the adress space of the fovea is from coordinates x=9,y=9 to 
 * x=75,y=79
 *
 * The class uses UioCameraRenderer to display frames with Gray-Levels on a 
 * black background and UioFoveatedImagerDisplayMethod to draw the special 
 * foveated Imager Pixel Layout.
 *
 * @author hansbe@ifi.uio.no
 */
public class UioFoveatedImager extends AETemporalConstastRetina implements Serializable {
    
    /** Creates a new instance of UioFoveatedImager and sets up a few constants (e.g. size)*/
    public UioFoveatedImager() {
        //setName("UioFoveatedImager_v1");
        setSizeX(83); // 83 added 1 for filter
        setSizeY(87); // 87 added 1
        setNumCellTypes(1);
        setEventClass(TypedEvent.class); // the chip puts out these kinds of events
        setEventExtractor(new Extractor(this));
        setBiasgen(null);
        init();
    }
    
    /** Sets up the EventCellExtractor with Adress space mask, Flipx is set to true*/
    public class Extractor extends TypedEventExtractor implements java.io.Serializable{
        public Extractor(AEChip chip){
            super(chip);
            setXmask((short)0x007f);    // X mask
            setXshift((byte)0);         // Number of bits to shift mask right
            setYmask((short)0x7f00);    // Y mask
            setYshift((byte)8);         // Number of bits to shift mask right
            setTypemask((short)0);
            setTypeshift((byte)0);
            setFlipx(true);             // Flips image on X axis so it appears like a mirror with the camera in front
//          setFliptype(true);
        }
        
     }
    
    protected void init(){
        
        // these are subclasses of ChipRenderer and ChipCanvas
        // these need to be added *before* the filters are made or the filters will not annotate the results!!!
        setRenderer(new AdaptiveIntensityRenderer(this));
        ((AdaptiveIntensityRenderer)this.renderer).setAdaptiveArea(8,83-8,8,87-9);
//        setCanvas(new RetinaCanvas(this)); // already done in AEChip
        // Add and set the custom display method
        DisplayMethod cdm = new UioFoveatedImagerDisplayMethod(getCanvas());
        getCanvas().addDisplayMethod(cdm);
        getCanvas().setDisplayMethod(cdm);       


//      There used to be a filter before I made the DisplayMethod which proved much better.
//        if(filterFrame!=null) filterFrame.dispose();
//        filterFrame=new FilterFrame(filterChain);
        
        
     } 
}
