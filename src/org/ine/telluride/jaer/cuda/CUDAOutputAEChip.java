/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.cuda;

import ch.unizh.ini.jaer.chip.retina.DVS128;
import net.sf.jaer.aemonitor.*;
import net.sf.jaer.chip.RetinaExtractor;
import net.sf.jaer.event.*;
import net.sf.jaer.event.EventPacket;

/**
 *  This specialized AEChip extracts spikes from the output of the jaercuda convolution tracker to render
 * them in different colors (for example).
 *
 * @author tobi
 */
public class CUDAOutputAEChip extends DVS128 {

     public static String getDescription(){
         return "jaercuda output chip";
     }

    /** Creates a new instance of DVS128. No biasgen is constructed for this constructor, because there is no hardware interface defined. */
    public CUDAOutputAEChip() {
        setName("CUDAOutputAEChip");
        setSizeX(128);
        setSizeY(128);
        setNumCellTypes(2);
        setEventExtractor(new Extractor(this));
        setBiasgen(null);
    }
    
     /** the event extractor for DVS128. DVS128 has two polarities 0 and 1. Here the polarity is flipped by the extractor so that the raw polarity 0 becomes 1
    in the extracted event. The ON events have raw polarity 0.
    1 is an ON event after event extraction, which flips the type. Raw polarity 1 is OFF event, which becomes 0 after extraction.
     */
    public class Extractor extends DVS128.Extractor {


        final short XMASK = 0xfe,  XSHIFT = 1,  YMASK = 0x7f00,  YSHIFT = 8;

        public Extractor(DVS128 chip) {
            super(chip);
            setXmask((short) 0x00fe);
            setXshift((byte) 1);
            setYmask((short) 0x7f00);
            setYshift((byte) 8);
            setTypemask((short) 1);
            setTypeshift((byte) 0);
            setFlipx(true);
            setFlipy(false);
            setFliptype(true);
        }

        /** extracts the meaning of the raw events.
         *@param in the raw events, can be null
         *@return out the processed events. these are partially processed in-place. empty packet is returned if null is supplied as in.
         */
        @Override
        synchronized public EventPacket extractPacket(AEPacketRaw in) {
            if (out == null) {
                out = new EventPacket<PolarityEvent>(chip.getEventClass());
            } else {
                out.clear();
            }
            if (in == null) {
                return out;
            }
            int n = in.getNumEvents(); //addresses.length;

            int skipBy = 1;
            if (isSubSamplingEnabled()) {
                while (n / skipBy > getSubsampleThresholdEventCount()) {
                    skipBy++;
                }
            }
            int sxm = sizeX - 1;
            int[] a = in.getAddresses();
            int[] timestamps = in.getTimestamps();
            OutputEventIterator outItr = out.outputIterator();
            for (int i = 0; i < n; i += skipBy) { // bug here
                PolarityEvent e = (PolarityEvent) outItr.nextOutput();
                int addr = a[i];
                e.timestamp = (timestamps[i]);
                e.x = (short) (sxm - ((short) ((addr & XMASK) >>> XSHIFT)));
                e.y = (short) ((addr & YMASK) >>> YSHIFT);
                e.type = (byte) (1 - addr & 1);
                e.polarity = e.type == 0 ? PolarityEvent.Polarity.Off : PolarityEvent.Polarity.On;
            }
            return out;
        }
    }
}
