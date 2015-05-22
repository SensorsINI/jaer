/*
created 26 Oct 2008 for new cDVSTest chip
 * adapted apr 2011 for cDVStest30 chip by tobi
 * adapted 25 oct 2011 for SeeBetter10/11 chips by tobi
 *
 */
package eu.seebetter.ini.chips.seebetter30;

import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFrame;

import net.sf.jaer.Description;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.chip.RetinaExtractor;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.graphics.RetinaRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import ch.unizh.ini.jaer.chip.retina.AETemporalConstastRetina;
import net.sf.jaer.DevelopmentStatus;

/**
 * Describes retina and its event extractor and bias generator.
 * Two constructors ara available, the vanilla constructor is used for event playback and the
 *one with a HardwareInterface parameter is useful for live capture.
 * {@link #setHardwareInterface} is used when the hardware interface is constructed after the retina object.
 *The constructor that takes a hardware interface also constructs the biasgen interface.
 * <p>
 * SeeBetter 10 and 11 feature several arrays of pixels, a fully configurable bias generator,
 * and a configurable output selector for digital and analog current and voltage outputs for characterization.
 * The output is word serial and includes an intensity neuron which rides onto the other addresses.
 * <p>
 * SeeBetter 10 and 11 are built in UMC18 CIS process and has 14.5u pixels.
 * SeeBetter 30 is build in UMC18 RF/MM process and has a 31.2u pixel pitch.
 * @author tobi, christian, minhao
 */
@Description("SeeBetter30 version 1.0, DVS with ADM encoder and high gain preamplifiers, Minhao Yang INI")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class SeeBetter30 extends AETemporalConstastRetina {
    
    // following define bit masks for various hardware data types. 
    // The hardware interface translateEvents method packs the raw device data into 32 bit 'addresses' and timestamps.
    // timestamps are unwrapped and timestamp resets are handled in translateEvents. Addresses are filled with either AE or ADC data.
    // AEs are filled in according the XMASK, YMASK, XSHIFT, YSHIFT below.
    /**
     * bit masks/shifts for cDVS  AE data
     */
  public static final int POLMASK = 1,
            XSHIFT = Integer.bitCount(POLMASK),
            XMASK = 63 << XSHIFT, // 6 bits
            YSHIFT = 16, // so that y addresses don't overlap ADC bits and cause fake ADC events Integer.bitCount(POLMASK | XMASK),
            YMASK = 31 << YSHIFT, // 5 bits
            INTENSITYMASK = 0x40000000;
//   public static final int 
//            YSHIFT = SeeBetter20.YSHIFT,
//            YMASK = SeeBetter20.YMASK, // 9 bits
//            XSHIFT = SeeBetter20.XSHIFT, 
//            XMASK = SeeBetter20.XMASK, // 10 bits
//            POLSHIFT = 0, 
//            POLMASK = 1 << POLSHIFT; 

    /*
     * data type fields
     */
    
    /* Address-type refers to data if is it an "address". This data is either an AE address or ADC reading.*/
    public static final int ADDRESS_TYPE_MASK = 0x80000000, ADDRESS_TYPE_DVS = 0x00000000, ADDRESS_TYPE_APS = 0x80000000;
    /** Maximal ADC value */
    public static final int ADC_BITS = 10, MAX_ADC = (int) ((1 << ADC_BITS) - 1);
    /** For ADC data, the data is defined by the reading cycle (0:reset read, 1 first read, 2 second read). */
    public static final int ADC_DATA_MASK = MAX_ADC, ADC_READCYCLE_SHIFT = 10, ADC_READCYCLE_MASK = 0xC00; 
    
    private boolean ignoreReadout;
    private SeeBetter30config config;
    JFrame controlFrame = null;
    public static short WIDTH = 60;
    public static short HEIGHT = 30;

    /** Creates a new instance of cDVSTest20.  */
    public SeeBetter30() {
        setName("SeeBetter30");
        setEventClass(PolarityEvent.class);
        setSizeX(WIDTH);
        setSizeY(HEIGHT);
        setNumCellTypes(3); // two are polarity and last is intensity
        setPixelHeightUm(31.2f);
        setPixelWidthUm(31.2f);

        setEventExtractor(new SeeBetter30Extractor(this));

        setBiasgen(config = new SeeBetter30config(this));

        setRenderer(new RetinaRenderer(this));

    }
    
    public void setPowerDown(boolean powerDown){
        config.powerDown.set(powerDown);
        try {
            config.sendOnChipConfigChain();
        } catch (HardwareInterfaceException ex) {
            Logger.getLogger(SeeBetter30.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /** Creates a new instance of SBRet10
     * @param hardwareInterface an existing hardware interface. This constructor is preferred. It makes a new cDVSTest10Biasgen object to talk to the on-chip biasgen.
     */
    public SeeBetter30(HardwareInterface hardwareInterface) {
        this();
        setHardwareInterface(hardwareInterface);
    }

//    int pixcnt=0; // TODO debug
    /** The event extractor. Each pixel has two polarities 0 and 1.
     * 
     * <p>
     *The bits in the raw data coming from the device are as follows.
     * <p>
     *Bit 0 is polarity, on=1, off=0<br>
     *Bits 1-9 are x address (max value 320)<br>
     *Bits 10-17 are y address (max value 240) <br>
     *<p>
     */
    public class SeeBetter30Extractor extends RetinaExtractor {

        boolean ignore = false;
        
        public SeeBetter30Extractor(SeeBetter30 chip) {
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
            out.systemModificationTimeNs = in.systemModificationTimeNs;

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
            for (int i = 0; i < n; i += skipBy) { // TODO bug here?
                int addr = a[i];
                PolarityEvent e = (PolarityEvent) outItr.nextOutput();
                e.address = addr;
                e.timestamp = (timestamps[i]);
                e.setSpecial(false);
                e.type = (byte) (1 - addr & 1);
                e.polarity = e.type == 0 ? PolarityEvent.Polarity.Off : PolarityEvent.Polarity.On;
                e.x = (short) (sxm - ((short) ((addr & XMASK) >>> XSHIFT)));
                e.y = (short) ((addr & YMASK) >>> YSHIFT);
            }
            return out;
        } // extractPacket

        @Override
        public AEPacketRaw reconstructRawPacket(EventPacket packet) {
            if(raw==null) raw=new AEPacketRaw();
            if(!(packet instanceof ApsDvsEventPacket)) return null;
            ApsDvsEventPacket apsDVSpacket = (ApsDvsEventPacket) packet;
            raw.ensureCapacity(packet.getSize());
            raw.setNumEvents(0);
            int[] a=raw.addresses;
            int [] ts=raw.timestamps;
            int n=apsDVSpacket.getSize();
            Iterator evItr = apsDVSpacket.fullIterator();
            int k=0;
            while(evItr.hasNext()){
                TypedEvent e=(ApsDvsEvent)evItr.next();
                ts[k]=e.timestamp;
                a[k++]=e.address; 
            }
            raw.setNumEvents(n);
            return raw;
        } 
        
    } // extractor

    /** overrides the Chip setHardware interface to construct a biasgen if one doesn't exist already.
     * Sets the hardware interface and the bias generators hardware interface
     *@param hardwareInterface the interface
     */
    @Override
    public void setHardwareInterface(final HardwareInterface hardwareInterface) {
        this.hardwareInterface = hardwareInterface;
        SeeBetter30config config;
        try {
            if (getBiasgen() == null) {
                setBiasgen(config = new SeeBetter30config(this));
                // now we can addConfigValue the control panel

            } else {
                getBiasgen().setHardwareInterface((BiasgenHardwareInterface) hardwareInterface);
            }

        } catch (ClassCastException e) {
            log.warning(e.getMessage() + ": probably this chip object has a biasgen but the hardware interface doesn't, ignoring");
        }
    }
    
    /**
     * @return the ignoreReadout
     */
    public boolean isIgnoreReadout() {
        return ignoreReadout;
    }

    /**
     * @param displayEvents the displayEvents to set
     */
    public void setIgnoreReadout(boolean ignoreReadout) {
        this.ignoreReadout = ignoreReadout;
        getPrefs().putBoolean("ignoreReadout", ignoreReadout);
        getAeViewer().interruptViewloop();
    }
    
}
