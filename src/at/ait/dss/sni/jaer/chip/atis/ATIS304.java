package at.ait.dss.sni.jaer.chip.atis;
/*
created 8.5.2009 for ARC ATIS chip in sardinia at capo caccio workshop on cognitive neuromorphic engineering.

  * This is part of jAER
<a href="http://jaer.wiki.sourceforge.net">jaer.wiki.sourceforge.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.

 */
import ch.unizh.ini.jaer.chip.retina.AERetina;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.RetinaExtractor;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.graphics.DisplayMethod;

/**
 * ARC ATIS 320x240
 * @author tobi delbruck, martin lizzenberger
 *
 */
public class ATIS304 extends AERetina{
    public static String getDescription() {
        return "ATIS304 304x240 Asynchronous Time Based Image Sensor";
    }
    public ATIS304(){
        setName("ATIS304");
        setSizeX(304);
        setSizeY(240);
        setNumCellTypes(2); // two are polarity and last is intensity
        setPixelHeightUm(30);
        setPixelWidthUm(30);
        setEventExtractor(new ATIS304xtractor(this));
        DisplayMethod m = getCanvas().getCurrentDisplayMethod(); // get default method
        getCanvas().removeDisplayMethod(m);

    }
    /** The event extractor. Each pixel has two polarities 0 and 1.
     * <p>
     *The bits in the raw data coming from the device are as follows.
     * <p>
     */
    public class ATIS304xtractor extends RetinaExtractor {

        public static final int XMASK = 0x03fe,  XSHIFT = 1,  YMASK = 0x03fc00,  YSHIFT = 10, POLARITY_MASK=0x01;

        public ATIS304xtractor(ATIS304 chip) {
            super(chip);
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
            int[] a = in.getAddresses();
            int[] timestamps = in.getTimestamps();
            OutputEventIterator outItr = out.outputIterator();
            for (int i = 0; i < n; i += skipBy) { // bug here
                int addr = a[i];
                PolarityEvent e = (PolarityEvent) outItr.nextOutput();
                e.timestamp = (timestamps[i]);
                e.x = (short) (((addr & XMASK) >>> XSHIFT));
                e.y = (short) ((addr & YMASK) >>> YSHIFT);
                e.type = (byte) (addr & 1);
                e.polarity = e.type == 1 ? PolarityEvent.Polarity.Off : PolarityEvent.Polarity.On;
            }
            return out;
        }
    }

}
