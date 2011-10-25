/*
created 26 Oct 2008 for new cDVSTest chip
 * adapted apr 2011 for cDVStest30 chip by tobi
 *
 */
package eu.seebetter.ini.chips.seebetter1011;

import ch.unizh.ini.jaer.chip.retina.*;
import ch.unizh.ini.jaer.chip.util.externaladc.ADCHardwareInterfaceProxy;
import ch.unizh.ini.jaer.chip.util.scanner.ScannerHardwareInterfaceProxy;
import eu.seebetter.ini.chips.*;
import eu.seebetter.ini.chips.cDVSEvent;
import eu.seebetter.ini.chips.config.*;
import java.awt.event.ActionEvent;
import java.util.Iterator;
import java.util.Observer;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JRadioButton;
import net.sf.jaer.aemonitor.*;
import net.sf.jaer.biasgen.*;
import net.sf.jaer.biasgen.VDAC.VPot;
import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.hardwareinterface.*;
import java.awt.BorderLayout;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Observable;
import java.util.StringTokenizer;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import net.sf.jaer.Description;
import net.sf.jaer.biasgen.Pot.Sex;
import net.sf.jaer.biasgen.Pot.Type;
import net.sf.jaer.biasgen.VDAC.DAC;
import net.sf.jaer.biasgen.VDAC.VPotGUIControl;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2;
import net.sf.jaer.util.ParameterControlPanel;
import net.sf.jaer.util.RemoteControlCommand;
import net.sf.jaer.util.RemoteControlled;

/**
 * Describes  retina and its event extractor and bias generator.
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
 *
 * @author tobi
 */
@Description("SeeBetter10 and 11 test chips")
public class SeeBetter1011 extends AETemporalConstastRetina implements HasIntensity {

    public static final int SIZEX_TOTAL = 140;
    public static final int SIZE_Y = 64;
    public static final int SIZE_Y_CDVS = 32;
    public static final int SIZE_X_CDVS = 32;
    public static final int SIZE_X_DVS = 64;
    public static final int COLOR_CHANGE_BIT = 1; // color change events are even pixels in x and y
    // following define bit masks for various hardware data types. 
    // The hardware interface translateEvents method packs the raw device data into 32 bit 'addresses' and timestamps.
    // timestamps are unwrapped and timestamp resets are handled in translateEvents. Addresses are filled with either AE or ADC data.
    // AEs are filled in according the XMASK, YMASK, XSHIFT, YSHIFT below.
    /**
     * bit masks/shifts for cDVS  AE data
     */
    public static final int POLMASK = 1,
            XSHIFT = Integer.bitCount(POLMASK),
            XMASK = 127 << XSHIFT, // 7 bits
            YSHIFT = 16, // so that y addresses don't overlap ADC bits and cause fake ADC events Integer.bitCount(POLMASK | XMASK),
            YMASK = 63 << YSHIFT, // 6 bits
            INTENSITYMASK = 0x40000000;

    /*
     * data type fields
     */
    /** data type is either timestamp or data (AE address or ADC reading) */
    public static final int DATA_TYPE_MASK = 0xc000, DATA_TYPE_ADDRESS = 0x0000, DATA_TYPE_TIMESTAMP = 0x4000, DATA_TYPE_WRAP = 0x8000, DATA_TYPE_TIMESTAMP_RESET = 0xd000;
    /** Address-type refers to data if is it an "address". This data is either an AE address or ADC reading.*/
    public static final int ADDRESS_TYPE_MASK = 0x2000, EVENT_ADDRESS_MASK = POLMASK | XMASK | YMASK, ADDRESS_TYPE_EVENT = 0x0000, ADDRESS_TYPE_ADC = 0x2000;
    /** For ADC data, the data is defined by the ADC channel and whether it is the first ADC value from the scanner. */
    public static final int ADC_TYPE_MASK = 0x1000, ADC_DATA_MASK = 0xfff, ADC_START_BIT = 0x1000, ADC_CHANNEL_MASK = 0x0000; // right now there is no channel info in the data word
    public static final int MAX_ADC = (int) ((1 << 12) - 1);
    /** The computed intensity value. */
    private float globalIntensity = 0;
    private CDVSLogIntensityFrameData frameData = new CDVSLogIntensityFrameData();
    private SeeBettter1011Renderer cDVSRenderer = null;
    private SeeBetter1011DisplayMethod cDVSDisplayMethod = null;
    private boolean displayLogIntensity;
    private boolean displayLogIntensityChangeEvents;

    /** Creates a new instance of cDVSTest10.  */
    public SeeBetter1011() {
        setName("SeeBetter1011");
        setEventClass(cDVSEvent.class);
        setSizeX(SIZEX_TOTAL);
        setSizeY(SIZE_Y);
        setNumCellTypes(3); // two are polarity and last is intensity
        setPixelHeightUm(14.5f);
        setPixelWidthUm(14.5f);

        setEventExtractor(new SeeBetter1011Extractor(this));

        setBiasgen(new SeeBetter1011.Biasgen(this));

        displayLogIntensity = getPrefs().getBoolean("displayLogIntensity", true);
        displayLogIntensityChangeEvents = getPrefs().getBoolean("displayLogIntensityChangeEvents", true);

        setRenderer((cDVSRenderer = new SeeBettter1011Renderer(this)));

        cDVSDisplayMethod = new SeeBetter1011DisplayMethod(this);
        getCanvas().addDisplayMethod(cDVSDisplayMethod);
        getCanvas().setDisplayMethod(cDVSDisplayMethod);
        cDVSDisplayMethod.setIntensitySource(this);

    }

    @Override
    public void onDeregistration() {
        cDVSDisplayMethod.unregisterControlPanel();
    }

    @Override
    public void onRegistration() {
        cDVSDisplayMethod.registerControlPanel();
    }

    /** Cleans up on renewal of chip. */
    @Override
    public void cleanup() {
        super.cleanup();
        cDVSDisplayMethod.unregisterControlPanel();
    }

    /** Creates a new instance of cDVSTest10
     * @param hardwareInterface an existing hardware interface. This constructor is preferred. It makes a new cDVSTest10Biasgen object to talk to the on-chip biasgen.
     */
    public SeeBetter1011(HardwareInterface hardwareInterface) {
        this();
        setHardwareInterface(hardwareInterface);
    }

    @Override
    public float getIntensity() {
        return globalIntensity;
    }

    @Override
    public void setIntensity(float f) {
        globalIntensity = f;
    }
    private boolean useOffChipCalibration = false;

    /**
     * @return the frameData
     */
    public CDVSLogIntensityFrameData getFrameData() {
        return frameData;
    }

    /**
     * @return the useOffChipCalibration
     */
    public boolean isUseOffChipCalibration() {
        return useOffChipCalibration;
    }

    /**
     * @param useOffChipCalibration the useOffChipCalibration to set
     */
    public void setUseOffChipCalibration(boolean useOffChipCalibration) {
        this.useOffChipCalibration = useOffChipCalibration;
        this.getFrameData().setUseOffChipCalibration(useOffChipCalibration);
        if (useOffChipCalibration) {
            this.getFrameData().setCalibData1();
        }
    }

    /** The event extractor. Each pixel has two polarities 0 and 1.
     * There is one extra neuron which signals absolute intensity.
     * <p>
     *The bits in the raw data coming from the device are as follows.
     * <p>
     *Bit 0 is polarity, on=1, off=0<br>
     *Bits 1-9 are x address (max value 320)<br>
     *Bits 10-17 are y address (max value 240) <br>
     *Bit 18 signals the special intensity neuron, 
     * but it always comes together with an x address.
     * It means there was an intensity spike AND a normal pixel spike.
     *<p>
     */
    public class SeeBetter1011Extractor extends RetinaExtractor {

        // according to  D:\Users\tobi\Documents\avlsi-svn\db\Firmware\cDVSTest20\cDVSTest_dataword_spec.pdf
//        public static final int XMASK = 0x3fe,  XSHIFT = 1,  YMASK = 0x000,  YSHIFT = 12,  INTENSITYMASK = 0x40000;
        private int lastIntenTs = 0;

        public SeeBetter1011Extractor(SeeBetter1011 chip) {
            super(chip);
        }
        private float avdt = 100; // used to compute rolling average of intensity

        /** extracts the meaning of the raw events.
         *@param in the raw events, can be null
         *@return out the processed events. these are partially processed in-place. empty packet is returned if null is supplied as in.
         */
        @Override
        synchronized public EventPacket extractPacket(AEPacketRaw in) {
            if (out == null) {
                out = new EventPacket(chip.getEventClass());
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
            int[] datas = in.getAddresses();
            int[] timestamps = in.getTimestamps();
            OutputEventIterator outItr = out.outputIterator();

            // at this point the raw data from the USB IN packet has already been digested to extract timestamps, including timestamp wrap events and timestamp resets.
            // The datas array holds the data, which consists of a mixture of AEs and ADC values.
            // Here we extract the datas and leave the timestamps alone.

            for (int i = 0; i < n; i++) {  // TODO implement skipBy
                int data = datas[i];

                if ((data & ADDRESS_TYPE_MASK) == ADDRESS_TYPE_EVENT) {
                    if ((data & INTENSITYMASK) == INTENSITYMASK) {// intensity spike
                        int dt = timestamps[i] - lastIntenTs;
                        if (dt > 50) {
                            avdt = 0.05f * dt + 0.95f * avdt; // avg over time
                            setIntensity(1000f / avdt); // ISI of this much, e.g. 1ms, gives intensity 1
                        }
                        lastIntenTs = timestamps[i];

                    } else {
                        cDVSEvent e = (cDVSEvent) outItr.nextOutput();
                        e.address = data & EVENT_ADDRESS_MASK;
                        e.timestamp = (timestamps[i]);
                        e.polarity = (byte) (data & POLMASK);
                        e.x = (short) (((data & XMASK) >>> XSHIFT));
                        if (e.x < 0) {
                            e.x = 0;
                        } else if (e.x > 319) {
                            //   e.x = 319; // TODO fix this artificial clamping of x address within space, masks symptoms
                        }
                        e.y = (short) ((data & YMASK) >>> YSHIFT);
                        if (e.y > 239) {
//                    log.warning("e.y="+e.y);
                            e.y = 239; // TODO fix this
                        } else if (e.y < 0) {
                            e.y = 0; // TODO
                        }

                        if (e.x < SIZE_X_CDVS * 2) { // cDVS pixel array // *2 because size is defined to be 32 and event types are still different x's
                            if ((e.y & 1) == 0) { // odd rows: log intensity change events
                                if (e.polarity == 1) { // off is 0, on is 1
                                    e.eventType = cDVSEvent.EventType.Brighter;
                                } else {
                                    e.eventType = cDVSEvent.EventType.Darker;
                                }
                            } else {  // even rows: color events
                                if (e.polarity == 0) { // bluer is 0, opposite to cDVSTest20
                                    e.eventType = cDVSEvent.EventType.Bluer;
                                } else {
                                    e.eventType = cDVSEvent.EventType.Redder;
                                }
                            }
                            e.x = (short) (e.x >>> 1);
                            e.y = (short) (e.y >>> 1); // cDVS array is clumped into 32x32
                        } else { // DVS test pixel arrays
//                               e.x = (short) (e.x >>> 1);
                            if (e.polarity == 1) {
                                e.eventType = cDVSEvent.EventType.Brighter;
                            } else {
                                e.eventType = cDVSEvent.EventType.Darker;
                            }
                        }
                    }
                } else if ((data & ADDRESS_TYPE_MASK) == ADDRESS_TYPE_ADC) {
                    // if scanner is stopped on one place, then we never get a start bit. we then limit the number of samples
                    // to some maximum number of samples TODO not done yet.
                    // TODO comment back in
                    if ((data & ADC_START_BIT) == ADC_START_BIT) {
                        getFrameData().swapBuffers();  // the hardware interface here swaps the reading and writing buffers so that new data goes into the other buffer and the old data will be displayed by the rendering thread
                        getFrameData().setTimestamp(timestamps[i]);
                    }
                    getFrameData().put(data & ADC_DATA_MASK);

                }

            }
            return out;
        } // extractPacket
    } // extractor

    /** overrides the Chip setHardware interface to construct a biasgen if one doesn't exist already.
     * Sets the hardware interface and the bias generators hardware interface
     *@param hardwareInterface the interface
     */
    @Override
    public void setHardwareInterface(final HardwareInterface hardwareInterface) {
        this.hardwareInterface = hardwareInterface;
        SeeBetter1011.Biasgen bg;
        try {
            if (getBiasgen() == null) {
                setBiasgen(bg = new SeeBetter1011.Biasgen(this));
                // now we can addConfigValue the control panel

            } else {
                getBiasgen().setHardwareInterface((BiasgenHardwareInterface) hardwareInterface);

            }

        } catch (ClassCastException e) {
            log.warning(e.getMessage() + ": probably this chip object has a biasgen but the hardware interface doesn't, ignoring");
        }
    }

    /**
     * Describes ConfigurableIPots on cDVSTest retina chip as well as the other configuration bits which control, for example, which
     * outputs are selected. These are all configured by a single shared shift register.
     * <p>
     * With Rx=82k, measured Im=2uA. With Rx=27k, measured Im=8uA.
     * <p>
     * cDVSTest has a pair of shared shifted sources, one for n-type and one for p-type. These sources supply a regulated voltaqe near the power rail.
     * The shifted sources are programed by bits at the start of the global shared shift register.
     *
     * <p>
     * TODO check following javadoc
     *
     * The pr, foll, and refr biases use the lowpower bias for their p src bias and the pr, foll and refr pfets
     * all have their psrcs wired to this shifted p src bias. Also, the pr, foll and refr biases also drive the same
     * shifted psrc bias with their own shifted psrc bias. Therefore all 4 of these biases (pr, foll, refr, and lowpower) should
     * have the same bufferbias bits. This constraint is enforced by software.
     * <p>
     * The output configuration bits follow the bias values and consist of 12 bits that select 3 outputs.
     * <nl>
     * <li> Bits 0-3 select the current output.
     * <li> 5 copies of digital Bits 4-8 select the digital output.
     * <li> Bits
     *
     * @author tobi
     */
    public class Biasgen extends SeeBetterChipConfig {

        private final short ADC_CONFIG = (short) 0x100;   //normal power mode, single ended, sequencer unused : (short) 0x908;
        ArrayList<HasPreference> hasPreferencesList = new ArrayList<HasPreference>();
        private ConfigurableIPot32 pcas, diffOn, diffOff, diff, red, blue, amp;
        private ConfigurableIPot32 refr, pr, foll;
        SeeBetter1011OutputControlPanel controlPanel;
        AllMuxes allMuxes = new AllMuxes(); // the output muxes
        private ShiftedSourceBias ssn, ssp, ssnMid, sspMid;
        private ShiftedSourceBias[] ssBiases = new ShiftedSourceBias[4];
        private VPot thermometerDAC;
        ExtraOnChipConfigBits configBits = new ExtraOnChipConfigBits();
        int pos = 0;
        JPanel bPanel;
        JTabbedPane bgTabbedPane;
        // portC
        private PortBit runAdc = new PortBit(SeeBetter1011.this, "c0", "runAdc", "High to run ADC", true);
        //
        // adc and scanner configurations are stored in scanner and adc; updates to here should update CPLD config below
        // CPLD shift register contents specified here by CPLDInt and CPLDBit
        private CPLDBit use5TBuffer = new CPLDBit(SeeBetter1011.this, 0, "use5TBuffer", "enables 5T OTA vs Source-Follower in-pixel buffer", true);
        private CPLDBit useCalibration = new CPLDBit(SeeBetter1011.this, 1, "useCalibration", "enables on-chip per-pixel calibration current after log intenisty sample", true);
        private CPLDInt adcTrackTime = new CPLDInt(SeeBetter1011.this, 2, 17, "adcTrackTime", "ADC track time in clock cycles which are 15 cycles/us", 0);
        private CPLDInt adcRefOnTime = new CPLDInt(SeeBetter1011.this, 18, 33, "adcRefOnTime", "ADC Reference ON time in clock cycles which are 15 cycles/us", 0);
        private CPLDInt adcRefOffTime = new CPLDInt(SeeBetter1011.this, 34, 49, "adcRefOffTime", "ADC Reference OFF time in clock cycles which are 15 cycles/us", 0);
        private CPLDInt adcIdleTime = new CPLDInt(SeeBetter1011.this, 50, 65, "adcIdleTime", "ADC idle time after last acquisition in clock cycles which are 15 cycles/us", 0);
        private CPLDInt scanY = new CPLDInt(SeeBetter1011.this, 66, 81, "scanY", "cochlea tap to monitor when not scanning continuously", 0);
        private CPLDInt scanX = new CPLDInt(SeeBetter1011.this, 82, 97, "scanX", "cochlea tap to monitor when not scanning continuously", 0);
        private CPLDBit scanContinuouslyEnabled = new CPLDBit(SeeBetter1011.this, 98, "scanContinuouslyEnabled", "enables continuous scanning of on-chip scanner", true);
        //
        // lists of ports and CPLD config
        private ADC adc;
        private Scanner scanner;
        boolean dacPowered = getPrefs().getBoolean("Biasgen.DAC.powered", true);
        private CypressFX2 cypress = null;

        /** Creates a new instance of Biasgen for cDVSTest with a given hardware interface
         *@param chip the chip this biasgen belongs to
         */
        public Biasgen(Chip chip) {
            super(chip);
            setName("SeeBetter1011Biasgen");

            addConfigValue(runAdc);
            addConfigValue(adcTrackTime);
            addConfigValue(adcIdleTime);
            addConfigValue(scanX);

            getMasterbias().setKPrimeNFet(55e-3f); // estimated from tox=42A, mu_n=670 cm^2/Vs // TODO fix for UMC18 process
            getMasterbias().setMultiplier(9 * (24f / 2.4f) / (4.8f / 2.4f));  // =45  correct for dvs320
            getMasterbias().setWOverL(4.8f / 2.4f); // masterbias has nfet with w/l=2 at output

            ssn = new ShiftedSourceBias(this);
            ssn.setSex(Pot.Sex.N);
            ssn.setName("SSN");
            ssn.setTooltipString("n-type shifted source that generates a regulated voltage near ground");
            ssn.addObserver(this);

            ssp = new ShiftedSourceBias(this);
            ssp.setSex(Pot.Sex.P);
            ssp.setName("SSP");
            ssp.setTooltipString("p-type shifted source that generates a regulated voltage near Vdd");
            ssp.addObserver(this);

            ssnMid = new ShiftedSourceBias(this);
            ssnMid.setSex(Pot.Sex.N);
            ssnMid.setName("SSNMid");
            ssnMid.setTooltipString("n-type shifted source that generates a regulated voltage inside rail, about 2 diode drops from ground");
            ssnMid.addObserver(this);

            sspMid = new ShiftedSourceBias(this);
            sspMid.setSex(Pot.Sex.P);
            sspMid.setName("SSPMid");
            sspMid.setTooltipString("p-type shifted source that generates a regulated voltage about 2 diode drops from Vdd");
            sspMid.addObserver(this);

            ssBiases[0] = ssnMid;
            ssBiases[1] = ssn;
            ssBiases[2] = sspMid;
            ssBiases[3] = ssp;

//                public DAC(int numChannels, int resolutionBits, float refMinVolts, float refMaxVolts, float vdd){

            // DAC object for simple voltage DAC
            final float Vdd = 1.8f;
            DAC dac = new DAC(1, 8, 0, Vdd, Vdd);
            //    public VPot(Chip chip, String name, DAC dac, int channel, Type type, Sex sex, int bitValue, int displayPosition, String tooltipString) {
            thermometerDAC = new VPot(SeeBetter1011.this, "LogAmpRef", dac, 0, Type.NORMAL, Sex.N, 9, 0, "Voltage DAC for log intensity switched cap amplifier");
            thermometerDAC.addObserver(this);

            setPotArray(new IPotArray(this));
            /*
             *
             * on SeeBetter1011, shift register order is as follows TODO check these biases
            diff
            ON
            OFF
            Red
            Blue
            Amp
            pcas
            ncas
            pr
            fb
            refr
            AEReqPd
            AEReqEndPd
            AEPuX
            AEPuY
            If_threshold
            If_refractory
            FollPadBias
             */

            try {
                addIPot("diff,n,normal,differencing amp");
                addIPot("ON,n,normal,DVS brighter threshold");
                addIPot("OFF,n,normal,DVS darker threshold");
                addIPot("Red,n,normal,Redder threshold");
                addIPot("Blue,n,normal,Bluer threshold");
                addIPot("Amp,n,normal,DVS ON threshold");
                addIPot("pcas,p,cascode,DVS ON threshold");
                addIPot("pixInvB,n,normal,pixel inverter bias");
                addIPot("pr,p,normal,photoreceptor bias current");
                addIPot("fb,p,normal,photoreceptor follower bias current");
                addIPot("refr,p,normal,DVS refractory current");
                addIPot("AReqPd,n,normal,request pulldown threshold");
                addIPot("AReqEndPd,n,normal,handshake state machine pulldown bias current");
                addIPot("AEPuX,p,normal,AER column pullup");
                addIPot("AEPuY,p,normal,AER row pullup");
                addIPot("If_threshold,n,normal,integrate and fire intensity neuroon threshold");
                addIPot("If_refractory,n,normal,integrate and fire intensity neuron refractory period bias current");
                addIPot("FollPadBias,n,normal,follower pad buffer bias current");
                addIPot("ROgate,p,normal,bias voltage for log readout transistor ");
                addIPot("ROcas,p,normal,bias voltage for log readout cascode ");
                addIPot("refcurrent,p,normal,reference current for log readout ");
                addIPot("RObuffer,n,normal,buffer bias for log readout");
            } catch (Exception e) {
                throw new Error(e.toString());
            }

            adc = new ADC(chip);
            adc.addObserver(this);
            scanner = new Scanner(SeeBetter1011.this);
            scanner.addObserver(this);

            loadPreferences();

        }

        /** Quick addConfigValue of a pot from a string description, comma delimited
         * 
         * @param s , e.g. "Amp,n,normal,DVS ON threshold"; separate tokens for name,sex,type,tooltip\nsex=n|p, type=normal|cascode
         * @throws ParseException Error
         */
        private void addIPot(String s) throws ParseException {
            try {
                String d = ",";
                StringTokenizer t = new StringTokenizer(s, d);
                if (t.countTokens() != 4) {
                    throw new Error("only " + t.countTokens() + " tokens in pot " + s + "; use , to separate tokens for name,sex,type,tooltip\nsex=n|p, type=normal|cascode");
                }
                String name = t.nextToken();
                String a;
                a = t.nextToken();
                Sex sex = null;
                if (a.equalsIgnoreCase("n")) {
                    sex = Sex.N;
                } else if (a.equalsIgnoreCase("p")) {
                    sex = Sex.P;
                } else {
                    throw new ParseException(s, s.lastIndexOf(a));
                }

                a = t.nextToken();

                Type type = null;
                if (a.equalsIgnoreCase("normal")) {
                    type = Type.NORMAL;
                } else if (a.equalsIgnoreCase("cascode")) {
                    type = Type.CASCODE;
                } else {
                    throw new ParseException(s, s.lastIndexOf(a));
                }

                String tip = t.nextToken();

                /*     public ConfigurableIPot32(Biasgen biasgen, String name, int shiftRegisterNumber,
                Type type, Sex sex, boolean lowCurrentModeEnabled, boolean enabled,
                int bitValue, int bufferBitValue, int displayPosition, String tooltipString) {
                 */

                getPotArray().addPot(new ConfigurableIPot32(this, name, pos++,
                        type, sex, false, true,
                        ConfigurableIPot32.maxBitValue / 2, ConfigurableIPot32.maxBuffeBitValue, pos, tip));
            } catch (Exception e) {
                throw new Error(e.toString());
            }
        }

        /**
         * Formats bits represented in a string as '0' or '1' as a byte array to be sent over the interface to the firmware, for loading
         * in big endian bit order, in order of the bytes sent.
         * <p>
         * Because the firmware writes integral bytes are always an integral number of bytes returned it is important that the 
         * bytes sent to the device are padded with leading bits 
         * (at msbs of first byte) that are finally shifted out of the on-chip shift register.
         * 
         * Therefore <code>bitString2Bytes</code> should only be called ONCE, after the complete bit string has been assembled, unless it is known
         * the other bits are an integral number of bytes.
         * 
         * @param bitString in msb to lsb order from left end, where msb will be in msb of first output byte
         * @return array of bytes to send
         */
        protected byte[] bitString2Bytes(String bitString) {
            int nBits = bitString.length();
            // compute needed number of bytes
            int nbytes = (nBits % 8 == 0) ? (nBits / 8) : (nBits / 8 + 1); // 4->1, 8->1, 9->2
            byte[] byteArray = new byte[nbytes];
            int bit = 0;
            for (int bite = 0; bite < nbytes; bite++) { // for each byte
                for (int i = 0; i < 8; i++) { // iterate over each bit of this byte
                    byteArray[bite] = (byte) ((0xff&byteArray[bite]) << 1); // first left shift previous value, with 0xff to avoid sign extension
                    if (bit < nBits && bitString.charAt(bit) == '1') { // if there is a 1 at this position of string (starting from left side) 
                        // this conditional and loop structure ensures we count in bytes and that we left shift for each bit position in the byte, padding on the right with 0's
                        byteArray[bite] |= 1; // put a 1 at the lsb of the byte
                    }
                    bit++; // go to next bit of string to the right

                }
            }
            return byteArray;
        }
        
        
        /** Vendor request command understood by the cochleaAMS1c firmware in connection with  VENDOR_REQUEST_SEND_BIAS_BYTES */
        public final short CMD_IPOT = 1, CMD_RESET_EQUALIZER = 2,
                CMD_SCANNER = 3, CMD_EQUALIZER = 4,
                CMD_SETBIT = 5, CMD_VDAC = 6, CMD_INITDAC = 7,
                CMD_CPLD_CONFIG = 8;
        public final String[] CMD_NAMES = {"IPOT", "RESET_EQUALIZER", "SCANNER", "EQUALIZER", "SET_BIT", "VDAC", "INITDAC", "CPLD_CONFIG"};
        final byte[] emptyByteArray = new byte[0];

        /** The central point for communication with HW from biasgen. All objects in Biasgen are Observables
        and addConfigValue Biasgen.this as Observer. They then call notifyObservers when their state changes.
         * Objects such as adcProxy store preferences for ADC, and update should update the hardware registers accordingly.
         * @param observable IPot, Scanner, etc
         * @param object notifyChange used at present
         */
        @Override
        synchronized public void update(Observable observable, Object object) {  // thread safe to ensure gui cannot retrigger this while it is sending something
//            if (!(observable instanceof CochleaAMS1c.Biasgen.Equalizer.EqualizerChannel)) {
//                log.info("Observable=" + observable + " Object=" + object);
//            }
//            if (cypress == null) { // TODO only really for debugging do we need to do even if no hardware
//                return;
//            }
            // sends a vendor request depending on type of update
            // vendor request is always VR_CONFIG
            // value is the type of update
            // index is sometimes used for 16 bitmask updates
            // bytes are the rest of data
            try {
                if (observable instanceof IPot) { // must send all IPot values and set the select to the ipot shift register, this is done by the cypress
                    byte[] bytes = new byte[1 + getPotArray().getNumPots() * getPotArray().getPots().get(0).getNumBytes()];
                    int ind = 0;
                    Iterator itr = ((IPotArray) getPotArray()).getShiftRegisterIterator();
                    while (itr.hasNext()) {
                        IPot p = (IPot) itr.next(); // iterates in order of shiftregister index, from Vbpf to VAGC
                        byte[] b = p.getBinaryRepresentation();
                        System.arraycopy(b, 0, bytes, ind, b.length);
                        ind += b.length;
                    }
                    sendConfig(CMD_IPOT, 0, bytes); // the usual packing of ipots
                } else if (observable instanceof VPot) {
                    // There are 2 16-bit AD5391 DACs daisy chained; we need to send data for both 
                    // to change one of them. We can send all zero bytes to the one we're notifyChange changing and it will notifyChange affect any channel
                    // on that DAC. We also take responsibility to formatting all the bytes here so that they can just be piped out
                    // surrounded by nSync low during the 48 bit write on the controller.
                    VPot p = (VPot) observable;
                    sendDAC(p);
                } else if (observable instanceof TriStateablePortBit) { // tristateable should come first before configbit since it is subclass
                    TriStateablePortBit b = (TriStateablePortBit) observable;
                    byte[] bytes = {(byte) ((b.isSet() ? (byte) 1 : (byte) 0) | (b.isHiZ() ? (byte) 2 : (byte) 0))};
                    sendConfig(CMD_SETBIT, b.getPortbit(), bytes); // sends value=CMD_SETBIT, index=portbit with (port(b=0,d=1,e=2)<<8)|bitmask(e.g. 00001000) in MSB/LSB, byte[0]= OR of value (1,0), hiZ=2/0, bit is set if tristate, unset if driving port
                } else if (observable instanceof PortBit) {
                    PortBit b = (PortBit) observable;
                    byte[] bytes = {b.isSet() ? (byte) 1 : (byte) 0};
                    sendConfig(CMD_SETBIT, b.getPortbit(), bytes); // sends value=CMD_SETBIT, index=portbit with (port(b=0,d=1,e=2)<<8)|bitmask(e.g. 00001000) in MSB/LSB, byte[0]=value (1,0)
                } else if (observable instanceof CPLDConfigValue) {
                    sendCPLDConfig();
                    // sends value=CMD_SETBIT, index=portbit with (port(b=0,d=1,e=2)<<8)|bitmask(e.g. 00001000) in MSB/LSB, byte[0]=value (1,0)
                } else if (observable instanceof ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1c.Biasgen.Scanner) {// TODO resolve with scannerProxy
// already handled by bits
//                    scanSel.set(true);
//                    scanX.set(scanner.getScanX());
//                    scanContinuouslyEnabled.set(scanner.isScanContinuouslyEnabled()); // must update cpld config bits from software scanner object
//                    byte[] bytes = cpld.getBytes();
//                    sendConfig(CMD_CPLD_CONFIG, 0, bytes);
                } else if (observable instanceof ADCHardwareInterfaceProxy) {
                    adcIdleTime.set(adc.getIdleTime() * 15); // multiplication with 15 to get from us to clockcycles
                    adcTrackTime.set(adc.getTrackTime() * 15); // multiplication with 15 to get from us to clockcycles
                    int lastChan = adc.getADCChannel();
                    boolean seq = adc.isSequencingEnabled();
                    // from AD7933/AD7934 datasheet
                    int config = (1 << 8) + (lastChan << 5) + (seq ? 6 : 0);
                    sendCPLDConfig();
                    runAdc.setChanged();
                    runAdc.notifyObservers();
                } else {
                    super.update(observable, object);  // super (Biasgen) handles others, e.g. masterbias
                }
            } catch (HardwareInterfaceException e) {
                log.warning("On update() caught " + e.toString());
            }
        }

        private void sendCPLDConfig() throws HardwareInterfaceException {
            byte[] bytes = cpldConfig.getBytes();
            sendConfig(CMD_CPLD_CONFIG, 0, bytes);
        }

        /** convenience method for sending configuration to hardware. Sends vendor request VENDOR_REQUEST_SEND_BIAS_BYTES with subcommand cmd, index index and bytes bytes.
         * 
         * @param cmd the subcommand to set particular configuration, e.g. CMD_CPLD_CONFIG
         * @param index unused
         * @param bytes the payload
         * @throws HardwareInterfaceException 
         */
        void sendConfig(int cmd, int index, byte[] bytes) throws HardwareInterfaceException {

            // debug
            System.out.print(String.format("sending config cmd 0x%X, index=0x%X, with %d bytes", cmd, index, bytes.length));
            if (bytes == null || bytes.length == 0) {
                System.out.println("");
            } else {
                int max = 8;
                if (bytes.length < max) {
                    max = bytes.length;
                }
                System.out.print(" = ");
                for (int i = 0; i < max; i++) {
                    System.out.print(String.format("%X, ", bytes[i]));
                }
                System.out.println("");
            } // end debug


            if (bytes == null) {
                bytes = emptyByteArray;
            }
//            log.info(String.format("sending command vendor request cmd=%d, index=%d, and %d bytes", cmd, index, bytes.length));
            if (getHardwareInterface() != null && getHardwareInterface() instanceof CypressFX2) {
                ((CypressFX2) getHardwareInterface()).sendVendorRequest(CypressFX2.VENDOR_REQUEST_SEND_BIAS_BYTES, (short) (0xffff & cmd), (short) (0xffff & index), bytes); // & to prevent sign extension for negative shorts
            }
        }

        /** 
         * Convenience method for sending configuration to hardware. Sends vendor request VENDOR_REQUEST_SEND_BIAS_BYTES with subcommand cmd, index index and empty byte array.
         * 
         * @param cmd the subcommand
         * @param index data
         * @throws HardwareInterfaceException 
         */
        void sendConfig(int cmd, int index) throws HardwareInterfaceException {
            sendConfig(cmd, index, emptyByteArray);
        }

        @Override
        public void loadPreferences() {
            super.loadPreferences();
            if (hasPreferencesList != null) {
                for (HasPreference hp : hasPreferencesList) {
                    hp.loadPreference();
                }
            }
            if (ssBiases != null) {
                for (ShiftedSourceBias ss : ssBiases) {
                    ss.loadPreferences();
                }
            }
            if (thermometerDAC != null) {
                thermometerDAC.loadPreferences();
            }
        }

        @Override
        public void storePreferences() {
            super.storePreferences();
            for (HasPreference hp : hasPreferencesList) {
                hp.storePreference();
            }
            if (ssBiases != null) {
                for (ShiftedSourceBias ss : ssBiases) {
                    ss.storePreferences();
                }
            }
            if (thermometerDAC != null) {
                thermometerDAC.storePreferences();
            }
        }

        // sends VR to init DAC
        public void initDAC() throws HardwareInterfaceException {
            sendConfig(CMD_INITDAC, 0);
        }

        void sendDAC(VPot pot) throws HardwareInterfaceException {
            int chan = pot.getChannel();
            int value = pot.getBitValue();
            byte[] b = new byte[6]; // 2*24=48 bits
// original firmware code
//            unsigned char dat1 = 0x00; //00 00 0000;
//            unsigned char dat2 = 0xC0; //Reg1=1 Reg0=1 : Write output data
//            unsigned char dat3 = 0x00;
//
//            dat1 |= (address & 0x0F);
//            dat2 |= ((msb & 0x0F) << 2) | ((lsb & 0xC0)>>6) ;
//            dat3 |= (lsb << 2) | 0x03; // DEBUG; the last 2 bits are actually don't care
            byte msb = (byte) (0xff & ((0xf00 & value) >> 8));
            byte lsb = (byte) (0xff & value);
            byte dat1 = 0;
            byte dat2 = (byte) 0xC0;
            byte dat3 = 0;
            dat1 |= (0xff & ((chan % 16) & 0xf));
            dat2 |= ((msb & 0xf) << 2) | ((0xff & (lsb & 0xc0) >> 6));
            dat3 |= (0xff & ((lsb << 2)));
            if (chan < 16) { // these are first VPots in list; they need to be loaded first to isSet to the second DAC in the daisy chain
                b[0] = dat1;
                b[1] = dat2;
                b[2] = dat3;
                b[3] = 0;
                b[4] = 0;
                b[5] = 0;
            } else { // second DAC VPots, loaded second to end up at start of daisy chain shift register
                b[0] = 0;
                b[1] = 0;
                b[2] = 0;
                b[3] = dat1;
                b[4] = dat2;
                b[5] = dat3;
            }
//            System.out.print(String.format("value=%-6d channel=%-6d ",value,chan));
//            for(byte bi:b) System.out.print(String.format("%2h ", bi&0xff));
//            System.out.println();
            sendConfig(CMD_VDAC, 0, b); // value=CMD_VDAC, index=0, bytes as above
        }

        /** Sets the VDACs on the board to be powered or high impedance output. This is a global operation.
         * 
         * @param yes true to power up DACs
         * @throws net.sf.jaer.hardwareinterface.HardwareInterfaceException
         */
        public void setDACPowered(boolean yes) throws HardwareInterfaceException {
            putPref("Biasgen.DAC.powered", yes);
            byte[] b = new byte[6];
            Arrays.fill(b, (byte) 0);
            final byte up = (byte) 9, down = (byte) 8;
            if (yes) {
                b[0] = up;
                b[3] = up; // sends 09 00 00 to each DAC which is soft powerup
            } else {
                b[0] = down;
                b[3] = down;
            }
            sendConfig(CMD_VDAC, 0, b);
        }

        /** Returns the DAC powered state
         * 
         * @return true if powered up
         */
        public boolean isDACPowered() {
            return dacPowered;
        }

        /**
         *
         * Overrides the default method to addConfigValue the custom control panel for configuring the cDVSTest output muxes.
         *
         * @return a new panel for controlling this bias generator functionally
         */
        @Override
        public JPanel buildControlPanel() {
//            if(controlPanel!=null) return controlPanel;
            bPanel = new JPanel();
            bPanel.setLayout(new BorderLayout());
            bgTabbedPane = new JTabbedPane();

            JPanel combinedBiasShiftedSourcePanel = new JPanel();
            combinedBiasShiftedSourcePanel.setLayout(new BoxLayout(combinedBiasShiftedSourcePanel, BoxLayout.Y_AXIS));
            combinedBiasShiftedSourcePanel.add(super.buildControlPanel());
            combinedBiasShiftedSourcePanel.add(new ShiftedSourceControls(ssn));
            combinedBiasShiftedSourcePanel.add(new ShiftedSourceControls(ssp));
            combinedBiasShiftedSourcePanel.add(new ShiftedSourceControls(ssnMid));
            combinedBiasShiftedSourcePanel.add(new ShiftedSourceControls(sspMid));
            combinedBiasShiftedSourcePanel.add(new VPotGUIControl(thermometerDAC));
            bgTabbedPane.addTab("Biases", combinedBiasShiftedSourcePanel);
            bgTabbedPane.addTab("Output control", new SeeBetter1011OutputControlPanel(SeeBetter1011.this));
            final String tabTitle = "ADC control";
            bgTabbedPane.addTab(tabTitle, new ParameterControlPanel(adc));
            bPanel.add(bgTabbedPane, BorderLayout.CENTER);
            bgTabbedPane.addMouseListener(new java.awt.event.MouseAdapter() {

                @Override
                public void mouseClicked(java.awt.event.MouseEvent evt) {
                    tabbedPaneMouseClicked(evt);
                }
            });
            bgTabbedPane.addTab("More config", configBits.makeControlPanel());
            // only select panel after all added
            bgTabbedPane.setSelectedIndex(getPrefs().getInt("cDVSTest30.bgTabbedPaneSelectedIndex", 0));
            return bPanel;
        }

        private void tabbedPaneMouseClicked(java.awt.event.MouseEvent evt) {
            getPrefs().putInt("cDVSTest30.bgTabbedPaneSelectedIndex", bgTabbedPane.getSelectedIndex());
        }

        /** Formats the data sent to the microcontroller to load bias and other configuration to the chip (not FX2 or CPLD configuration). 
        <p>
         * Data is sent in bytes. Each byte is loaded into the shift register in big-endian bit order, starting with the msb and ending with the lsb.
         * Bytes are loaded starting with the first byte from formatConfigurationBytes (element 0). Therefore the last bit in the on-chip shift register (the one
         * that is furthest away from the bit input pin) should be in the msb of the first byte returned by formatConfigurationBytes.
         * @return byte array to be sent, which will be loaded into the chip starting with element 0 msb.
         * @param biasgen this Biasgen
         */
        @Override
        public byte[] formatConfigurationBytes(net.sf.jaer.biasgen.Biasgen biasgen) {
            ByteBuffer bb = ByteBuffer.allocate(1000);
            byte[] biasBytes = super.formatConfigurationBytes(biasgen);
            byte[] configBytes = allMuxes.formatConfigurationBytes(); // the first nibble is the imux in big endian order, bit3 of the imux is the very first bit.
            bb.put(configBytes);

            // 256 value (8 bit) VDAC for amplifier reference
            byte vdac = (byte) thermometerDAC.getBitValue(); //Byte.valueOf("9");
            bb.put(vdac);   // VDAC needs 8 bits
            bb.put(biasBytes);

            for (ShiftedSourceBias ss : ssBiases) {
                bb.put(ss.getBinaryRepresentation());
            }

            byte[] configBitBytes = configBits.formatConfigurationBytes(); // the first nibble is the imux in big endian order, bit3 of the imux is the very first bit.
            bb.put(configBitBytes);


            byte[] allBytes = new byte[bb.position()];
            bb.flip();
            bb.get(allBytes);

            return allBytes; // configBytes may be padded with extra bits to make up a byte, board needs to know this to chop off these bits
        }
        /** the change in current from an increase* or decrease* call */
        public final float RATIO = 1.05f;
        /** the minimum on/diff or diff/off current allowed by decreaseThreshold */
        public final float MIN_THRESHOLD_RATIO = 2f;
        public final float MAX_DIFF_ON_CURRENT = 6e-6f;
        public final float MIN_DIFF_OFF_CURRENT = 1e-9f;

        synchronized public void increaseThreshold() {
            if (diffOn.getCurrent() * RATIO > MAX_DIFF_ON_CURRENT) {
                return;
            }
            if (diffOff.getCurrent() / RATIO < MIN_DIFF_OFF_CURRENT) {
                return;
            }
            diffOn.changeByRatio(RATIO);
            diffOff.changeByRatio(1 / RATIO);
        }

        synchronized public void decreaseThreshold() {
            float diffI = diff.getCurrent();
            if (diffOn.getCurrent() / MIN_THRESHOLD_RATIO < diffI) {
                return;
            }
            if (diffOff.getCurrent() > diffI / MIN_THRESHOLD_RATIO) {
                return;
            }
            diffOff.changeByRatio(RATIO);
            diffOn.changeByRatio(1 / RATIO);
        }

        synchronized public void increaseRefractoryPeriod() {
            refr.changeByRatio(1 / RATIO);
        }

        synchronized public void decreaseRefractoryPeriod() {
            refr.changeByRatio(RATIO);
        }

        synchronized public void increaseBandwidth() {
            pr.changeByRatio(RATIO);
            foll.changeByRatio(RATIO);
        }

        synchronized public void decreaseBandwidth() {
            pr.changeByRatio(1 / RATIO);
            foll.changeByRatio(1 / RATIO);
        }

        synchronized public void moreONType() {
            diffOn.changeByRatio(1 / RATIO);
            diffOff.changeByRatio(RATIO);
        }

        synchronized public void moreOFFType() {
            diffOn.changeByRatio(RATIO);
            diffOff.changeByRatio(1 / RATIO);
        }        // TODO fix functional biasgen panel to be more usable

        public class ADC extends ADCHardwareInterfaceProxy implements Observer {

            public ADC(Chip chip) {
                super(chip);
            }

            @Override
            public void update(Observable o, Object arg) {
                throw new UnsupportedOperationException("Not supported yet.");
            }
        }

        /** Extends base scanner class to control the relevant bits and parameters of the hardware */
        public class Scanner extends ScannerHardwareInterfaceProxy implements PreferenceChangeListener, HasPreference, Observer {

            public final int nstages = 64;
            public final int minPeriod = 10; // to avoid FX2 getting swamped by interrupts for scanclk
            public final int maxPeriod = 255;

            public Scanner(SeeBetter1011 chip) {
                super(chip);
                loadPreference();
                getPrefs().addPreferenceChangeListener(this);
                hasPreferencesList.add(this);
            }

            public int getPeriod() {
                return adc.getIdleTime() + adc.getTrackTime();
            }

            /** Sets the scan rate using the ADC idleTime setting, indirectly. 
             * 
             * @param period 
             */
            public void setPeriod(int period) {
                boolean old = adc.isADCEnabled();
                adc.setADCEnabled(false);
                adc.setIdleTime(period); // TODO fix period units using track time + conversion time + idleTime
                if (old) {
                    adc.setADCEnabled(old);
                }
            }

            @Override
            public int getScanX() {
                return Biasgen.this.scanX.get();
            }

            @Override
            public boolean isScanContinuouslyEnabled() {
                return Biasgen.this.scanContinuouslyEnabled.isSet();
            }

            @Override
            public void setScanContinuouslyEnabled(boolean scanContinuouslyEnabled) {
                super.setScanContinuouslyEnabled(scanContinuouslyEnabled);
                Biasgen.this.scanContinuouslyEnabled.set(scanContinuouslyEnabled);
            }

            @Override
            public void setScanX(int scanX) {
                super.setScanX(scanX);
                Biasgen.this.scanX.set(scanX);
            }

            @Override
            public void preferenceChange(PreferenceChangeEvent e) {
//                if (e.getKey().equals("CochleaAMS1c.Biasgen.Scanner.currentStage")) {
//                    setCurrentStage(Integer.parseInt(e.getNewValue()));
//                } else if (e.getKey().equals("CochleaAMS1c.Biasgen.Scanner.currentStage")) {
//                    setContinuousScanningEnabled(Boolean.parseBoolean(e.getNewValue()));
//                }
            }

            @Override
            public void loadPreference() {
                setScanX(Biasgen.this.scanX.get());
                setScanContinuouslyEnabled(Biasgen.this.scanContinuouslyEnabled.isSet());
            }

            @Override
            public void storePreference() {
            }

            @Override
            public String toString() {
                return "Scanner{" + "currentStage=" + getScanX() + ", scanContinuouslyEnabled=" + isScanContinuouslyEnabled() + ", period=" + getPeriod() + '}';
            }

            @Override
            public void update(Observable o, Object arg) {
                if (o == Biasgen.this.scanContinuouslyEnabled) {
                    setScanContinuouslyEnabled(Biasgen.this.scanContinuouslyEnabled.isSet());
                } else if (o == Biasgen.this.scanX) {
                    setScanX(Biasgen.this.scanX.get());
                }
            }
        }

        /** Bits on the on-chip shift register but not an output mux control, added to end of shift register. Control
         * holding different pixel arrays in reset and how the RC delays are configured.
        
         */
        class ExtraOnChipConfigBits extends Observable implements HasPreference { // TODO fix for config bit of pullup

            final int TOTAL_NUM_BITS = 24;  // number of these bits on this chip, at end of biasgen shift register
            boolean value = false;

            class ConfigBit implements HasPreference {

                int position;
                boolean value = false;
                SelectAction action = new SelectAction();

                class SelectAction extends AbstractAction {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        select(!value);
                    }
                }

                /** Makes a new on-chip extra config bit.
                 * 
                 * @param name label
                 * @param position along shift register. Each loaded bit produces complementary output pair that is tapped off inside chip. We load positive (uncomplemented) value here.
                 * @param desc tooltip and hint
                 */
                ConfigBit(String name, int position, String desc) {
                    this.position = position;
                    action.putValue(Action.SHORT_DESCRIPTION, desc);
                    action.putValue(Action.NAME, name);
                }

                private String key() {
                    return "SeeBetter1011." + getClass().getSimpleName() + "." + name + ".value";
                }

                @Override
                public void loadPreference() {
                    select(getPrefs().getBoolean(key(), false));
                }

                @Override
                public void storePreference() {
                    getPrefs().putBoolean(key(), value);
                }

                void select(boolean yes) {
                    selectWithoutNotify(yes);
                    setChanged();
                    notifyObservers();
                }

                void selectWithoutNotify(boolean v) {
                    value = v;
                    try {
                        sendConfiguration(SeeBetter1011.Biasgen.this);
                    } catch (HardwareInterfaceException ex) {
                        log.warning("selecting output: " + ex);
                    }
                }
            }
            ConfigBit pullupX = new ConfigBit("useStaticPullupX", 0, "turn on static pullup for X addresses (columns)"),
                    pullupY = new ConfigBit("useStaticPullupY", 1, "turn on static pullup for Y addresses (rows)"),
                    delayY0 = new ConfigBit("delayY0", 2, "RC delay columns, 1x"),
                    delayY1 = new ConfigBit("delayY1", 3, "RC delay columns, 2x"),
                    delayY2 = new ConfigBit("delayY2", 4, "RC delay columns 4x"),
                    delayX0 = new ConfigBit("delayX0", 5, "RC delay rows, 1x"),
                    delayX1 = new ConfigBit("delayX1", 6, "RC delay rows, 2x"),
                    delayX2 = new ConfigBit("delayX2", 7, "RC delay rows, 4x"),
                    sDVSReset = new ConfigBit("sDVSReset", 8, "holds sensitive DVS (sDVS) array in reset"),
                    bDVSReset = new ConfigBit("bDVSReset", 9, "holds big DVS + log intensity (bDVS) array in reset"),
                    ros = new ConfigBit("ROS", 10, "reset on scan enabled"),
                    delaySM0 = new ConfigBit("delaySM0", 11, "adds delay to state machine, 1x"),
                    delaySM1 = new ConfigBit("delaySM1", 12, "adds delay to state machine, 2x"),
                    delaySM2 = new ConfigBit("delaySM2", 13, "adds delay to state machine, 4x");
            ConfigBit[] configBits = {pullupX, pullupY, delayY0, delayY1, delayY2, delayX0, delayX1, delayX2, sDVSReset, bDVSReset, ros, delaySM0, delaySM1, delaySM2};

            @Override
            public void loadPreference() {
                for (ConfigBit b : configBits) {
                    b.loadPreference();
                }
            }

            @Override
            public void storePreference() {
                for (ConfigBit b : configBits) {
                    b.storePreference();
                }
            }

            /** Returns the bit string to send to the firmware to load a bit sequence for the config bits in the shift register.
             * 
             * Bytes sent to FX2 are loaded big endian into shift register (msb first) and here returned string has msb at left-most position, i.e. left end of string.
             * @return string of 0 and 1 with first element of configBits at left hand end, and ending with padded 0s.
             */
            String getBitString() {
                StringBuilder s = new StringBuilder();
                // iterate over list
                for (int i = 0; i < configBits.length; i++) {
                    s.append(configBits[i].value ? "1" : "0");
                }
                for (int i = 0; i < TOTAL_NUM_BITS - configBits.length; i++) {
                    s.append("1"); // loaded first into unused parts of final shift register
                }
                log.info(s.length() + " configBits=" + s);
                return s.toString();
            }

            byte[] formatConfigurationBytes() {
                String s = getBitString(); // in msb to lsb order from left end
                int nBits = s.length();
                int nbytes = (nBits % 8 == 0) ? (nBits / 8) : (nBits / 8 + 1); // 8->1, 9->2
                byte[] byteArray = new byte[nbytes];
                int bit = 0;
                for (int bite = 0; bite < nbytes; bite++) {
                    for (int i = 0; i < 8; i++) {
                        if (s.charAt(bit) == '1') {
                            byteArray[bite] |= 1;
                        }
                       byteArray[bite] = (byte) ((0xff&byteArray[bite]) << 1); // then left shift the byte, and with 0xff to avoid sign extension
                      bit++;
                    }
                }
                return byteArray;
            }

            JPanel makeControlPanel() {
                JPanel pan = new JPanel();
                pan.setLayout(new BoxLayout(pan, BoxLayout.Y_AXIS));
                for (ConfigBit b : configBits) {
                    JRadioButton but = new JRadioButton(b.action);
                    but.setSelected(b.value);
                    pan.add(but);
                }
                return pan;
            }
        }

        /** A mux for selecting output on the on-chip configuration/biasgen shift register. */
        class OutputMux extends Observable implements HasPreference, RemoteControlled {

            int nSrBits;
            int nInputs;
            OutputMap map;
            private String name = "OutputMux";
            int selectedChannel = -1; // defaults to no input selected in the case of voltage and current, and channel 0 in the case of logic
            String bitString = null;
            final String CMD_SELECTMUX = "selectMux_";

            /**
             *  A set of output mux channels.
             * @param nsr number of shift register bits
             * @param nin number of input ports to mux
             * @param m the map where the info is stored
             */
            OutputMux(int nsr, int nin, OutputMap m) {
                nSrBits = nsr;
                nInputs = nin;
                map = m;
                hasPreferencesList.add(this);
            }

            @Override
            public String toString() {
                return "OutputMux name=" + name + " nSrBits=" + nSrBits + " nInputs=" + nInputs + " selectedChannel=" + selectedChannel + " channelName=" + getChannelName(selectedChannel) + " code=" + getCode(selectedChannel) + " getBitString=" + bitString;
            }

            void select(int i) {
                selectWithoutNotify(i);
                setChanged();
                notifyObservers();
            }

            void selectWithoutNotify(int i) {
                selectedChannel = i;
                try {
                    sendConfiguration(SeeBetter1011.Biasgen.this);
                } catch (HardwareInterfaceException ex) {
                    log.warning("selecting output: " + ex);
                }
            }

            void put(int k, String name) { // maps from channel to string name
                map.put(k, name);
            }

            OutputMap getMap() {
                return map;
            }

            int getCode(int i) { // returns shift register binary code for channel i
                return map.get(i);
            }

            /** Returns the bit string to send to the firmware to load a bit sequence for this mux in the shift register;
             * bits are loaded big endian, msb first but returned string has msb at right-most position, i.e. end of string.
             * @return big endian string e.g. code=11, s='1011', code=7, s='0111' for nSrBits=4.
             */
            String getBitString() {
                StringBuilder s = new StringBuilder();
                int code = selectedChannel != -1 ? getCode(selectedChannel) : 0; // code 0 if no channel selected
                int k = nSrBits - 1;
                while (k >= 0) {
                    int x = code & (1 << k); // start with msb
                    boolean b = (x == 0); // get bit
                    s.append(b ? '0' : '1'); // append to string 0 or 1, string grows with msb on left
                    k--;
                } // construct big endian string e.g. code=14, s='1011'
                bitString = s.toString();
                return bitString;
            }

            String getChannelName(int i) { // returns this channel name
                return map.nameMap.get(i);
            }

            public String getName() { // returns name of entire mux
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            private String key() {
                return "cDVSTest." + getClass().getSimpleName() + "." + name + ".selectedChannel";
            }

            @Override
            public void loadPreference() {
                select(getPrefs().getInt(key(), -1));
            }

            @Override
            public void storePreference() {
                getPrefs().putInt(key(), selectedChannel);
            }

            /** Command is e.g. "selectMux_Currents 1".
             *
             * @param command the first token which dispatches the command here for this class of Mux.
             * @param input the command string.
             * @return some informative string for debugging bad commands.
             */
            @Override
            public String processRemoteControlCommand(RemoteControlCommand command, String input) {
                String[] t = input.split("\\s");
                if (t.length < 2) {
                    return "? " + this + "\n";
                } else {
                    String s = t[0], a = t[1];
                    try {
                        select(Integer.parseInt(a));
                        return this + "\n";
                    } catch (NumberFormatException e) {
                        log.warning("Bad number format: " + input + " caused " + e);
                        return e.toString() + "\n";
                    } catch (Exception ex) {
                        log.warning(ex.toString());
                        return ex.toString();
                    }
                }
            }
//            public void preferenceChange(PreferenceChangeEvent evt) {
//                if(evt.getKey().equals(key())){
//                    select(Integer.parseInt(evt.getNewValue()));
//                }
//            }
        }

        class OutputMap extends HashMap<Integer, Integer> {

            HashMap<Integer, String> nameMap = new HashMap<Integer, String>();

            void put(int k, int v, String name) {
                put(k, v);
                nameMap.put(k, name);
            }

            void put(int k, String name) {
                nameMap.put(k, name);
            }
        }

        class VoltageOutputMap extends OutputMap {

            final void put(int k, int v) {
                put(k, v, "Voltage " + k);
            }

            VoltageOutputMap() {
                put(0, 1);
                put(1, 3);
                put(2, 5);
                put(3, 7);
                put(4, 9);
                put(5, 11);
                put(6, 13);
                put(7, 15);
            }
        }

        class DigitalOutputMap extends OutputMap {

            DigitalOutputMap() {
                for (int i = 0; i < 16; i++) {
                    put(i, i, "DigOut " + i);
                }
            }
        }

        class VoltageOutputMux extends OutputMux {

            VoltageOutputMux(int n) {
                super(4, 8, new VoltageOutputMap());
                setName("Voltages" + n);
            }
        }

        class LogicMux extends OutputMux {

            LogicMux(int n) {
                super(4, 16, new DigitalOutputMap());
                setName("LogicSignals" + n);
            }
        }

        // the output muxes
        class AllMuxes extends ArrayList<OutputMux> {

            OutputMux[] vmuxes = {new VoltageOutputMux(1), new VoltageOutputMux(2), new VoltageOutputMux(3), new VoltageOutputMux(4)};
            OutputMux[] dmuxes = {new LogicMux(1), new LogicMux(2), new LogicMux(3), new LogicMux(4), new LogicMux(5)};

            byte[] formatConfigurationBytes() {
                int nBits = 0;
                StringBuilder s = new StringBuilder();
                for (OutputMux m : this) {
                    s.append(m.getBitString());
                    nBits += m.nSrBits;
                }
                BigInteger bi = new BigInteger(s.toString(), 2);
                byte[] byteArray = bi.toByteArray(); // finds minimal set of bytes in big endian format, with MSB as first element
                // we need to pad out to nbits worth of bytes
                int nbytes = (nBits % 8 == 0) ? (nBits / 8) : (nBits / 8 + 1); // 8->1, 9->2
                byte[] bytes = new byte[nbytes];
                System.arraycopy(byteArray, 0, bytes, nbytes - byteArray.length, byteArray.length);
                //System.out.println(String.format("%d bytes holding %d actual bits", bytes.length, nBits));
//                for (int i=0;i<nbytes;i++)
//                {
//                  System.out.println("byte " + i + " : " + Integer.toHexString(bytes[i]));
//                }
                return bytes;
            }

            AllMuxes() {

                addAll(Arrays.asList(dmuxes)); // 5 logic muxes, first in list since at end of chain - bits must be sent first, before any biasgen bits
                addAll(Arrays.asList(vmuxes)); // finally send the 3 voltage muxes

                dmuxes[0].setName("DigMux4");
                dmuxes[1].setName("DigMux3");
                dmuxes[2].setName("DigMux2");
                dmuxes[3].setName("DigMux1");
                dmuxes[4].setName("DigMux0");

                for (int i = 0; i < 5; i++) {
                    dmuxes[i].put(0, "nRxcolE");
                    dmuxes[i].put(1, "nAxcolE");
                    dmuxes[i].put(2, "nRY0");
                    dmuxes[i].put(3, "AY0");
                    dmuxes[i].put(4, "nAX0");
                    dmuxes[i].put(5, "nRXon");
                    dmuxes[i].put(6, "arbtopR");
                    dmuxes[i].put(7, "arbtopA");
                    dmuxes[i].put(8, "FF1");
                    dmuxes[i].put(9, "Acol");
                    dmuxes[i].put(10, "Rcol");
                    dmuxes[i].put(11, "Rrow");
                    dmuxes[i].put(12, "RxcolG");
                    dmuxes[i].put(13, "nArow");
                    dmuxes[i].put(14, "FF2");
                    dmuxes[i].put(15, "RCarb");
                }


                vmuxes[0].setName("AnaMux3");
                vmuxes[1].setName("AnaMux2");
                vmuxes[2].setName("AnaMux1");
                vmuxes[3].setName("AnaMux0");

                for (int i = 0; i < 4; i++) {
                    vmuxes[i].put(0, "readout");
                    vmuxes[i].put(1, "DiffAmpOut");
                    vmuxes[i].put(2, "InPh");
                }

                vmuxes[0].put(3, "refcurrent");
                vmuxes[0].put(4, "DiffAmpRef");
                vmuxes[0].put(5, "log");
                vmuxes[0].put(6, "Vt");
                vmuxes[0].put(7, "top");

                vmuxes[1].put(3, "refcurrent");
                vmuxes[1].put(4, "DiffAmpRef");
                vmuxes[1].put(5, "log");
                vmuxes[1].put(6, "Vt");
                vmuxes[1].put(7, "top");

                vmuxes[2].put(3, "phi1");
                vmuxes[2].put(4, "phi2");
                vmuxes[2].put(5, "Vcoldiff");
                vmuxes[2].put(6, "Vs");
                vmuxes[2].put(7, "sum");

                vmuxes[3].put(3, "phi1");
                vmuxes[3].put(4, "phi2");
                vmuxes[3].put(5, "Vcoldiff");
                vmuxes[3].put(6, "Vs");
                vmuxes[3].put(7, "sum");
            }
        }
    }

    /**
     * @return the displayLogIntensity
     */
    public boolean isDisplayLogIntensity() {
        return displayLogIntensity;
    }

    /**
     * @param displayLogIntensity the displayLogIntensity to set
     */
    public void setDisplayLogIntensity(boolean displayLogIntensity) {
        this.displayLogIntensity = displayLogIntensity;
        getPrefs().putBoolean("displayLogIntensity", displayLogIntensity);
        getAeViewer().interruptViewloop();
    }

    /**
     * @return the displayLogIntensityChangeEvents
     */
    public boolean isDisplayLogIntensityChangeEvents() {
        return displayLogIntensityChangeEvents;
    }

    /**
     * @param displayLogIntensityChangeEvents the displayLogIntensityChangeEvents to set
     */
    public void setDisplayLogIntensityChangeEvents(boolean displayLogIntensityChangeEvents) {
        this.displayLogIntensityChangeEvents = displayLogIntensityChangeEvents;
        getPrefs().putBoolean("displayLogIntensityChangeEvents", displayLogIntensityChangeEvents);
        getAeViewer().interruptViewloop();
    }
}
