/*
 * DVS128StereoBoard.java
 *
 * Created on September 10, 2007, 2:57 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.stereopsis;

import ch.unizh.ini.jaer.chip.retina.DVS128;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.aemonitor.EventRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BinocularEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.BinocularDVSRenderer;
import net.sf.jaer.stereopsis.StereoChipInterface;
import net.sf.jaer.stereopsis.Stereopsis;

/**
 * The AEChip object representing the Tmpdiff128 Stereo Board. This board holds
 * two Tmpdiff128 chips and uses the CypressFX2LP with the Xilinx Coolrunner
 * CPLD to capture both retinas and send the events down a single,
 * time-synchronized USB pipe. The events are tagged left or right eye by the
 * msb (bit15) of the raw device address. This object provides a unified
 * interface to both Tmpdiff128 chips and delivers AEPacket's with binocular
 * retina events.
 * <p>
 * Note that this AEChip object has it's own set of biasgen settings on its own
 * preference node.
 * 
 * The HardwareInterface for this board 
 * is {@link net.sf.jaer.hardwareinterface.usb.cypressfx2libusb.CypressFX2LibUsbDVS128HardwareInterface}.
 *
 * @author tobi
 * @see net.sf.jaer.hardwareinterface.usb.cypressfx2libusb.CypressFX2LibUsbDVS128HardwareInterface
 */
@Description("A stereo pair of Tmpdiff128 retinas on a board with a single USB interface")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class DVS128StereoBoard extends DVS128 implements StereoChipInterface {

    /**
     * Creates a new instance of Tmpdiff128StereoBoard
     */
    public DVS128StereoBoard() {
        setEventClass(BinocularEvent.class);
        setRenderer(new BinocularDVSRenderer(this));
        setEventExtractor(new Extractor(this));
    }

    @Override
    public void setAeViewer(AEViewer aeViewer) {
        super.setAeViewer(aeViewer);
        aeViewer.setLogFilteredEventsEnabled(false); // not supported for binocular reconstruction yet TODO
    }

    @Override
    public int getNumCellTypes() {
        return 4;
    }

    @Override
    public AEChip getLeft() {
        return this;
    }

    public AEChip getRight() {
        return this;
    }

    @Override
    public void setLeft(AEChip left) {
        log.warning("setLeft doesn't do anything");
    }

    @Override
    public void setRight(AEChip right) {
        log.warning("setRight doesn't do anything");
    }

    @Override
    public void swapEyes() {
        log.warning("swapEyes doesn't do anything");
    }

    /**
     * the event extractor for the stereo chip pair. It extracts from each event
     * the x,y,type of the event and in addition, it adds getNumCellTypes to
     * each type to signal a right event (as opposed to a left event)
     */
    public class Extractor extends DVS128.Extractor {

        public Extractor(DVS128 chip) {
            super(chip);
            out=new EventPacket(BinocularEvent.class);
        }
        
        /**
         * extracts the meaning of the raw events.
         *
         * @param in the raw events, can be null
         * @return out the processed events. these are partially processed
         * in-place. empty packet is returned if null is supplied as in.
         */
        @Override
        synchronized public EventPacket extractPacket(AEPacketRaw in) {
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
            int[] a = in.getAddresses();
            int[] timestamps = in.getTimestamps();
            OutputEventIterator outItr = out.outputIterator();
            for (int i = 0; i < n; i += skipBy) { // bug here
                BinocularEvent e = (BinocularEvent) outItr.nextOutput();
                e.address = a[i];
                e.timestamp = timestamps[i];
                e.x = getXFromAddress(a[i]);
                e.y = getYFromAddress(a[i]);
                e.type = getTypeFromAddress(a[i]);
                e.polarity = e.type == 0 ? PolarityEvent.Polarity.Off : PolarityEvent.Polarity.On;
                e.eye = Stereopsis.isRightRawAddress(a[i]) ? BinocularEvent.Eye.RIGHT : BinocularEvent.Eye.LEFT;
            }
            return out;
        }

//        /** @return the type of the event.
//         */
//        public byte getTypeFromAddress(short addr){
//            byte type=super.getTypeFromAddress(addr);
//            if(Stereopsis.isRightRawAddress(addr)) {
////                System.out.println("type right type");
//                type=Stereopsis.setRightType(type);
//            }
//            return type;
//        }
        /**
         * Reconstructs the raw packet after event filtering to include the
         * binocular information
         *
         * @param packet the filtered packet
         * @return the reconstructed packet
         */
        @Override
        public AEPacketRaw reconstructRawPacket(EventPacket packet) {
            AEPacketRaw p = super.reconstructRawPacket(packet);
            // we also need to append binocularity (eye) to raw events
            for (int i = 0; i < packet.getSize(); i++) {
                BinocularEvent be = (BinocularEvent) packet.getEvent(i);
                if (be.eye == BinocularEvent.Eye.RIGHT) {
                    EventRaw event = p.getEvent(i);
                    event.address &= Stereopsis.MASK_RIGHT_ADDR;
                }
            }
            return p;
        }

    }

}
