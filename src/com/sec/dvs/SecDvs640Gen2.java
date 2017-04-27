/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.sec.dvs;

import ch.unizh.ini.jaer.chip.retina.AETemporalConstastRetina;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.TypedEventExtractor;
import net.sf.jaer.event.PolarityEvent;

/**
 * SEC DVS 640x480 Gen2 AEChip - just for displaying recorded data so far
 * 
 * @author Tobi Delbruck, Lior Zamir, CNE 2017
 */
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
@Description("SEC DVS 640x480 Gen2 AEChip - just for displaying recorded data")
public class SecDvs640Gen2 extends AETemporalConstastRetina{

    public SecDvs640Gen2() {
        setSizeX(640);
        setSizeY(480);
        setPixelHeightUm(9);
        setPixelWidthUm(9);
        setEventExtractor(new SecDvs640Gen2EventExtractor(this));
        
    }

    private final class SecDvs640Gen2EventExtractor extends TypedEventExtractor<PolarityEvent> {

        public SecDvs640Gen2EventExtractor(AEChip aechip) {
            super(aechip);
            setXshift((byte)1);
            setYshift((byte)11);
            setXmask(0x7ff);
            setYmask(0xff800);
            setTypemask(1);
        }
     
    }
    
    
}
