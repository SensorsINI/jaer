/*
 * DVS128.java
 *
 * Created on October 5, 2005, 11:36 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */
package ch.unizh.ini.jaer.chip.retina.r10y;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeSupport;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

import net.sf.jaer.Description;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.biasgen.ChipControlPanel;
import net.sf.jaer.biasgen.IPot;
import net.sf.jaer.biasgen.IPotArray;
import net.sf.jaer.biasgen.Pot;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.chip.RetinaExtractor;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2DVS128HardwareInterface;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.HasResettablePixelArray;
import ch.unizh.ini.jaer.chip.retina.AETemporalConstastRetina;

/**
 * Describes R10Y retina and its event extractor and bias generator.
 *
 * @author tobi/junseok
 */
@Description("R10Y prototoype Dynamic Vision Sensor")
public class R10Y extends AETemporalConstastRetina implements Serializable, Observer {

    private JMenu chipMenu = null;
    private JMenuItem arrayResetMenuItem = null;
    private JMenuItem setArrayResetMenuItem = null;
    private PropertyChangeSupport support = new PropertyChangeSupport(this);
    public static final String CMD_TWEAK_THESHOLD = "threshold", CMD_TWEAK_ONOFF_BALANCE = "balance", CMD_TWEAK_BANDWIDTH = "bandwidth", CMD_TWEAK_MAX_FIRING_RATE = "maxfiringrate";
    private R10YBiasgen biasgen;
    JComponent helpMenuItem1 = null, helpMenuItem2 = null, helpMenuItem3 = null;

    /**
     * Creates a new instance of DVS128. No biasgen is constructed for this
     * constructor, because there is no hardware interface defined.
     */
    public R10Y() {
        setName("R10Y");
        setDefaultPreferencesFile("../../biasgenSettings/r10y/R10Y-Default.xml");
        setSizeX(128);
        setSizeY(128);
        setNumCellTypes(2);
        setPixelHeightUm(40);
        setPixelWidthUm(40);
        setEventExtractor(new Extractor(this));
        setBiasgen((biasgen = new R10Y.R10YBiasgen(this)));
        addObserver(this);

        if (!biasgen.isInitialized()) {
            maybeLoadDefaultPreferences();  // call *after* biasgen is built so that we check for unitialized biases as well.
        }//        if(c!=null)c.setBorderSpacePixels(5);// make border smaller than default
    }

    /**
     * Creates a new instance of DVS128
     *
     * @param hardwareInterface an existing hardware interface. This constructor
     * is preferred. It makes a new R10YBiasgen object to talk to the on-chip
     * biasgen.
     */
    public R10Y(HardwareInterface hardwareInterface) {
        this();
        setHardwareInterface(hardwareInterface);
    }

    /**
     * Updates AEViewer specialized menu items according to capabilities of
     * HardwareInterface.
     *
     * @param o the observable, i.e. this Chip.
     * @param arg the argument (e.g. the HardwareInterface).
     */
    public void update(Observable o, Object arg) {
        if (!(arg instanceof HardwareInterface)) {
            return;
        }
        if (arrayResetMenuItem == null && getHardwareInterface() != null && getHardwareInterface() instanceof HasResettablePixelArray) {
            arrayResetMenuItem = new JMenuItem("Momentarily reset pixel array");
            arrayResetMenuItem.setToolTipText("Applies a momentary reset to the pixel array");
            arrayResetMenuItem.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent evt) {
                    HardwareInterface hw = getHardwareInterface();
                    if (hw == null || !(hw instanceof HasResettablePixelArray)) {
                        log.warning("cannot reset pixels with hardware interface=" + hw + " (class " + (hw != null ? hw.getClass() : null) + "), interface doesn't implement HasResettablePixelArray");
                        return;
                    }
                    log.info("resetting pixels");
                    ((HasResettablePixelArray) hw).resetPixelArray();
                    setArrayResetMenuItem.setSelected(false); // after this reset, the array will not be held in reset
                }
            });
            chipMenu.add(arrayResetMenuItem);

            setArrayResetMenuItem = new JCheckBoxMenuItem("Hold array in reset");
            setArrayResetMenuItem.setToolTipText("Sets the entire pixel array in reset");
            setArrayResetMenuItem.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent evt) {
                    HardwareInterface hw = getHardwareInterface();
                    if (hw == null || !(hw instanceof HasResettablePixelArray)) {
                        log.warning("cannot reset pixels with hardware interface=" + hw + " (class " + hw.getClass() + "), interface doesn't implement HasResettablePixelArray");
                        return;
                    }
                    log.info("setting pixel array reset=" + setArrayResetMenuItem.isSelected());
                    ((HasResettablePixelArray) hw).setArrayReset(setArrayResetMenuItem.isSelected());
                }
            });

            chipMenu.add(setArrayResetMenuItem);
        }


        // if hw interface is not correct type then disable menu items
        if (getHardwareInterface() == null) {
            if (arrayResetMenuItem != null) {
                arrayResetMenuItem.setEnabled(false);
            }
            if (setArrayResetMenuItem != null) {
                setArrayResetMenuItem.setEnabled(false);
            }
        } else {
            if (!(getHardwareInterface() instanceof HasResettablePixelArray)) {
                if (arrayResetMenuItem != null) {
                    arrayResetMenuItem.setEnabled(false);
                }
                if (setArrayResetMenuItem != null) {
                    setArrayResetMenuItem.setEnabled(false);
                }
            } else {
                arrayResetMenuItem.setEnabled(true);
                setArrayResetMenuItem.setEnabled(true);
            }
        }

    }

    @Override
    public void onDeregistration() {
        super.onRegistration();
        if (getAeViewer() == null) {
            return;
        }
        getAeViewer().removeHelpItem(helpMenuItem1);
        getAeViewer().removeHelpItem(helpMenuItem2);
    }

    @Override
    public void onRegistration() {
        super.onRegistration();
        if (getAeViewer() == null) {
            return;
        }
    }

    /**
     * the event extractor for DVS128. DVS128 has two polarities 0 and 1. Here
     * the polarity is flipped by the extractor so that the raw polarity 0
     * becomes 1 in the extracted event. The ON events have raw polarity 0. 1 is
     * an ON event after event extraction, which flips the type. Raw polarity 1
     * is OFF event, which becomes 0 after extraction.
     */
    public class Extractor extends RetinaExtractor {

        final short XMASK = 0xfe, XSHIFT = 1, YMASK = 0x7f00, YSHIFT = 8;

        public Extractor(R10Y chip) {
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

        /**
         * extracts the meaning of the raw events.
         *
         * @param in the raw events, can be null
         * @return out the processed events. these are partially processed
         * in-place. empty packet is returned if null is supplied as in. This
         * event packet is reused and should only be used by a single thread of
         * execution or for a single input stream, or mysterious results may
         * occur!
         */
        @Override
        synchronized public EventPacket extractPacket(AEPacketRaw in) {
            if (out == null) {
                out = new EventPacket<PolarityEvent>(chip.getEventClass());
            } else {
                out.clear();
            }
            extractPacket(in, out);
            return out;
        }
        private int printedSyncBitWarningCount = 3;

        /**
         * Extracts the meaning of the raw events. This form is used to supply
         * an output packet. This method is used for real time event filtering
         * using a buffer of output events local to data acquisition. An
         * AEPacketRaw may contain multiple events, not all of them have to sent
         * out as EventPackets. An AEPacketRaw is a set(!) of addresses and
         * corresponding timing moments.
         *
         * A first filter (independent from the other ones) is implemented by
         * subSamplingEnabled and getSubsampleThresholdEventCount. The latter
         * may limit the amount of samples in one package to say 50,000. If
         * there are 160,000 events and there is a sub sample threshold of
         * 50,000, a "skip parameter" set to 3. Every so now and then the
         * routine skips with 4, so we end up with 50,000. It's an
         * approximation, the amount of events may be less than 50,000. The
         * events are extracted uniform from the input.
         *
         * @param in the raw events, can be null
         * @param out the processed events. these are partially processed
         * in-place. empty packet is returned if null is supplied as input.
         */
        @Override
        synchronized public void extractPacket(AEPacketRaw in, EventPacket out) {

            if (in == null) {
                return;
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
                int addr = a[i]; // TODO handle special events from hardware correctly
                PolarityEvent e = (PolarityEvent) outItr.nextOutput();
                e.address = addr;
                e.timestamp = (timestamps[i]);

                if ((addr & (CypressFX2DVS128HardwareInterface.SYNC_EVENT_BITMASK | BasicEvent.SPECIAL_EVENT_BIT_MASK)) != 0) { // msb is set
                    e.setSpecial(true);
                    e.x = -1;
                    e.y = -1;
                    e.type = -1;
                    e.polarity = PolarityEvent.Polarity.On;
                    if (printedSyncBitWarningCount > 0) {
                        log.warning("raw address " + addr + " is >32767 (0xefff); either sync or stereo bit is set");
                        printedSyncBitWarningCount--;
                        if (printedSyncBitWarningCount == 0) {
                            log.warning("suppressing futher warnings about msb of raw address");
                        }
                    }
                } else {
                    e.setSpecial(false);
                    e.type = (byte) (1 - addr & 1);
                    e.polarity = e.type == 0 ? PolarityEvent.Polarity.Off : PolarityEvent.Polarity.On;
                    e.x = (short) (sxm - ((short) ((addr & XMASK) >>> XSHIFT)));
                    e.y = (short) ((addr & YMASK) >>> YSHIFT);
                }

            }
        }
    }

    /**
     * overrides the Chip setHardware interface to construct a biasgen if one
     * doesn't exist already. Sets the hardware interface and the bias
     * generators hardware interface
     *
     * @param hardwareInterface the interface
     */
    @Override
    public void setHardwareInterface(final HardwareInterface hardwareInterface) {
        super.setHardwareInterface(hardwareInterface);
        this.hardwareInterface = hardwareInterface;
        try {
            if (getBiasgen() == null) {
                setBiasgen(new R10Y.R10YBiasgen(this));
            } else {
                getBiasgen().setHardwareInterface((BiasgenHardwareInterface) hardwareInterface);
            }
        } catch (ClassCastException e) {
            System.err.println(e.getMessage() + ": probably this chip object has a biasgen but the hardware interface doesn't, ignoring");
        }
        setChanged();
        notifyObservers(hardwareInterface);
    }

//    /** Called when this DVS128 notified, e.g. by having its AEViewer set
//     @param the calling object
//     @param arg the argument passed
//     */
//    public void update(Observable o, Object arg) {
//        log.info("DVS128: received update from Observable="+o+", arg="+arg);
//    }
    /**
     * Called when the AEViewer is set for this AEChip. Here we add the menu to
     * the AEViewer.
     *
     * @param v the viewer
     */
    @Override
    public void setAeViewer(AEViewer v) {
        super.setAeViewer(v);
        if (v != null) {

            chipMenu = new JMenu("DVS128");
            chipMenu.getPopupMenu().setLightWeightPopupEnabled(false); // to paint on GLCanvas
            chipMenu.setToolTipText("Specialized menu for DVS128 chip");

            v.setMenu(chipMenu);
        }
    }

    /**
     * Describes IPots on R10Y retina chip. These are configured by a shift
     * register. This biasgen has one master bias control of 4 bits and each
     * bias has 3 bits of individual control.
     *
     * The table below is the default bias values for R10.
     *
     * Besides these values, we need to send 10 bits dummy (0000000000) before
     * sending the bias values.
     *
     * Thus, 45 bits, in total, should be sent to the shift register of R10.
     *
     * The order of bits to the shift register (for the default values) is like
     * this:
     *
     * 00000000 1 0111 100 100 100 ...
     *
     * <pre>
     * Pin mapping of the new PCB for R10 is like this:
     *
     * FX2                 R10 (see Fig. 3-2(b) of ParameterSerializer_v00.docx)
     *
     * CLOCK_B     ---->    PAD_BIAS_ENABLE
     *
     * BITIN_B     ---->    PAD_BIAS_DATA
     *
     * BITOUT_B    ---->    PAD_BIAS_OUT
     *
     * LATCH_B     ---->    open (not connected)
     *
     * POWERDOWN   ---->    PDA_PD
     *
     * </pre>
     *     
* Biases are as follows:
     * <pre>
     * (Total 35) 	Default 	Default (Variation/step) 	MAX/MIM 	Real BIAS (
     *
     * IREF_TUNE<3:0> 	0111
     * SEL_BIASX<2:0> 	100
     * SEL_BIASREQPD<2:0> 	100
     * SEL_BIASREQ<2:0> 	100
     * SEL_BIASREFR<2:0> 	100
     * SEL_BIASPR<2:0> 	100
     * SEL_BIASF<2:0> 	100
     * SEL_BIASDIFFOFF<2:0> 	100
     * SEL_BIASDIFFON<2:0> 	100
     * SEL_BIASDIFF<2:0> 	100
     * SEL_BIASCAS<2:0> 	100
     * </pre>
     *
     * @author tobi
     */
    public class R10YBiasgen extends net.sf.jaer.biasgen.Biasgen implements ChipControlPanel {

        private R10YBias diffOn, diffOff, refr, pr, sf, diff, cas;
        private R10YIRefTuneBias iRefTuneBias;
//        private Masterbias masterBias; //  there is default one in R10YBiasgen super class

        /**
         * Creates a new instance of R10YBiasgen for DVS128 with a given
         * hardware interface
         *
         * @param chip the chip this biasgen belongs to
         */
        public R10YBiasgen(Chip chip) {
            super(chip);
            setName("R10Y");
//            masterBias=new Masterbias(this);
            iRefTuneBias = new R10YIRefTuneBias(this, "IRef Tune", 10, Pot.Type.NORMAL, Pot.Sex.N, 7, 0, "IREF_TUNE: scales all biases by this current value");


//  /** Creates a new instance of IPot
//            @param tuneBias
//     *@param biasgen
//     *@param name
//     *@param shiftRegisterNumber the position in the shift register, 0 based, starting on end from which bits are loaded
//     *@param type (NORMAL, CASCODE)
//     *@param sex Sex (N, P)
//     * @param bitValue initial bitValue
//     *@param displayPosition position in GUI from top (logical order)
//     *@param tooltipString a String to display to user of GUI telling them what the pots does
//     */
////    public IPot(R10YBiasgen biasgen, String name, int shiftRegisterNumber, final Type type, Sex sex, int bitValue, int displayPosition, String tooltipString) {

            // create potArray according to our needs
            setPotArray(new IPotArray(this));

            getPotArray().addPot(iRefTuneBias);

            getPotArray().addPot(new R10YBias(iRefTuneBias,this, "AER Pullup", 9, IPot.Type.NORMAL, IPot.Sex.P, 4, 0, "pullup bias on arbiters"));
            getPotArray().addPot(new R10YBias(iRefTuneBias,this, "AER Pulldown", 8, IPot.Type.NORMAL, IPot.Sex.N, 4, 1, "AER request pulldown"));
            getPotArray().addPot(new R10YBias(iRefTuneBias,this, "Pixel OFF inverter", 7, IPot.Type.NORMAL, IPot.Sex.N, 4, 2, "OFF request inverter bias"));
            getPotArray().addPot(refr = new R10YBias(iRefTuneBias,this, "Refrac. Per.", 6, IPot.Type.NORMAL, IPot.Sex.P, 4, 3, "Refractory period"));
            getPotArray().addPot(pr = new R10YBias(iRefTuneBias,this, "Photoreceptor", 5, IPot.Type.NORMAL, IPot.Sex.P, 4, 4, "Photoreceptor"));
            getPotArray().addPot(sf = new R10YBias(iRefTuneBias,this, "Src Foll", 4, IPot.Type.NORMAL, IPot.Sex.P, 4, 5, "Src follower buffer between photoreceptor and differentiator"));
            getPotArray().addPot(diffOff = new R10YBias(iRefTuneBias,this, "OFF thresh", 3, IPot.Type.NORMAL, IPot.Sex.N, 4, 6, "OFF threshold, lower to raise threshold"));
            getPotArray().addPot(diffOn = new R10YBias(iRefTuneBias,this, "ON thresh", 2, IPot.Type.NORMAL, IPot.Sex.N, 4, 7, "ON threshold - higher to raise threshold"));
            getPotArray().addPot(diff = new R10YBias(iRefTuneBias,this, "Diff", 1, IPot.Type.NORMAL, IPot.Sex.N, 4, 8, "Differentiator"));
            getPotArray().addPot(cas = new R10YBias(iRefTuneBias,this, "DiffCas", 0, IPot.Type.CASCODE, IPot.Sex.N, 4, 9, "Differentiator cascode: optimizes gain in pixel differencing amplifier"));

            loadPreferences();

        }

        /**
         * Formats the data sent to the microcontroller to load bias and other
         * configuration.
         */
        @Override
        public byte[] formatConfigurationBytes(net.sf.jaer.biasgen.Biasgen biasgen) {
            StringBuilder sb = new StringBuilder();
            sb.append("0000000000"); // 10 leading dummy bits
            IPotArray ipa = (IPotArray) getPotArray();
            if (ipa == null) {
                log.warning("null pot array");
                return null;
            }
            sb.append(getMasterbias().isPowerDownEnabled() ? " 1" : " 0");
            Iterator<IPot> i = ipa.getShiftRegisterIterator();
            while (i.hasNext()) {
                IPot pot = i.next();
                // for each pot, get the number of bits and then write the bits of the bias a binary string
                String fmt = String.format("%%%ds", pot.getNumBits()); // e.g. %3s for regular bias, %4s for IRefTune
                String bits = String.format(fmt, Integer.toBinaryString(pot.getBitValue())).replace(" ", "0"); // e.g. "001" for bit value 1 
                sb.append(" ").append(bits);
            }
            // turn all the bits into bytes padded with zero bits at msb of first bias if needed.
            byte[] allBytes;
            allBytes = bitString2Bytes(sb.toString());
            StringBuilder infostring = new StringBuilder("configuration bit string is\n" + sb.toString() + "\nbytes returned are \n");
            for (byte b : allBytes) {
                infostring.append(String.format(" 0x%X", b));
            }
            log.info(infostring.toString());
            return allBytes; // configBytes may be padded with extra bits to make up a byte, board needs to know this to chop off these bits
        }

//        @Override
//        public void sendConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
//            byte[] b = formatConfigurationBytes(this); // for debugging
//            if (hardwareInterface == null) {
//                log.warning("no hardware interface");
//                return;
//            }
//            if (!isBatchEditOccurring() && hardwareInterface != null && hardwareInterface.isOpen()) {
//                hardwareInterface.;
//            }
//        }

        @Override
        public void update(Observable observable, Object object) {
            try {
                if (!isBatchEditOccurring()) {
                    sendConfiguration(this);
                }
            } catch (HardwareInterfaceException e) {
                log.warning("error sending pot values: " + e);
            }

        }
    } // biasgen

    /**
     * Fires PropertyChangeEvents when biases are tweaked according to
     * {@link ch.unizh.ini.jaer.chip.retina.DVSTweaks}.
     *
     * @return the support
     */
    public PropertyChangeSupport getSupport() {
        return support;
    }
}
