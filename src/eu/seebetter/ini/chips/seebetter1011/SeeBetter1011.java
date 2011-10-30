/*
created 26 Oct 2008 for new cDVSTest chip
 * adapted apr 2011 for cDVStest30 chip by tobi
 * adapted 25 oct 2011 for SeeBetter10/11 chips by tobi
 *
 */
package eu.seebetter.ini.chips.seebetter1011;

import ch.unizh.ini.jaer.chip.retina.*;
import eu.seebetter.ini.chips.*;
import eu.seebetter.ini.chips.cDVSEvent;
import eu.seebetter.ini.chips.config.*;
import java.awt.event.ActionEvent;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import javax.swing.JRadioButton;
import net.sf.jaer.aemonitor.*;
import net.sf.jaer.biasgen.*;
import net.sf.jaer.biasgen.VDAC.VPot;
import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.hardwareinterface.*;
import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Observable;
import java.util.StringTokenizer;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.border.TitledBorder;
import net.sf.jaer.Description;
import net.sf.jaer.biasgen.Pot.Sex;
import net.sf.jaer.biasgen.Pot.Type;
import net.sf.jaer.biasgen.VDAC.DAC;
import net.sf.jaer.biasgen.VDAC.VPotGUIControl;
import net.sf.jaer.graphics.AEViewer;
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
    private SeeBetter1011Renderer cDVSRenderer = null;
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

        setBiasgen(new SeeBetter1011.SeeBetterConfig(this));

        displayLogIntensity = getPrefs().getBoolean("displayLogIntensity", true);
        displayLogIntensityChangeEvents = getPrefs().getBoolean("displayLogIntensityChangeEvents", true);

        setRenderer((cDVSRenderer = new SeeBetter1011Renderer(this)));

        cDVSDisplayMethod = new SeeBetter1011DisplayMethod(this);
        getCanvas().addDisplayMethod(cDVSDisplayMethod);
        getCanvas().setDisplayMethod(cDVSDisplayMethod);
        cDVSDisplayMethod.setIntensitySource(this);

    }

    @Override
    public void onDeregistration() {
        unregisterControlPanel();
    }

    @Override
    public void onRegistration() {
        registerControlPanel();
    }
    SeeBetter1011DisplayControlPanel controlPanel = null;

    private void registerControlPanel() {
        try {
            AEViewer viewer = getAeViewer(); // must do lazy install here because viewer hasn't been registered with this chip at this point
            JPanel imagePanel = viewer.getImagePanel();
            imagePanel.add((controlPanel = new SeeBetter1011DisplayControlPanel(this)), BorderLayout.SOUTH);
            imagePanel.revalidate();
        } catch (Exception e) {
            log.warning("could not register control panel: " + e);
        }
    }

    private void unregisterControlPanel() {
        try {
            AEViewer viewer = getAeViewer(); // must do lazy install here because viewer hasn't been registered with this chip at this point
            JPanel imagePanel = viewer.getImagePanel();
            imagePanel.remove(controlPanel);
            imagePanel.revalidate();
        } catch (Exception e) {
            log.warning("could not unregister control panel: " + e);
        }
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
                        e.y = (short) ((data & YMASK) >>> YSHIFT);

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
        SeeBetter1011.SeeBetterConfig bg;
        try {
            if (getBiasgen() == null) {
                setBiasgen(bg = new SeeBetter1011.SeeBetterConfig(this));
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
    public class SeeBetterConfig extends SeeBetterChipConfig { // extends Config to give us containers for various configuration data

        /** Number of ADC clock cycles per us, for converting from GUIs to config values */
        public static final int ADC_CLK_CYCLES_PER_US = 15;
        /** VR for handling all configuration changes in firmware */
        public static final byte VR_WRITE_CONFIG = (byte) 0xB8;
        ArrayList<HasPreference> hasPreferencesList = new ArrayList<HasPreference>();
        private ConfigurableIPot32 pcas, diffOn, diffOff, diff, red, blue, amp;
        private ConfigurableIPot32 refr, pr, foll;
        SeeBetter1011OutputControlPanel controlPanel;
        AllMuxes allMuxes = null; // the output muxes
        private ShiftedSourceBias ssn, ssp, ssnMid, sspMid;
        private ShiftedSourceBias[] ssBiases = new ShiftedSourceBias[4];
        private VPot thermometerDAC;
        ExtraOnChipConfigBits extraOnchipConfigBits = null;
        int pos = 0;
        JPanel bPanel;
        JTabbedPane bgTabbedPane;
        // portA
        private PortBit runCpld = new PortBit(SeeBetter1011.this, "a3", "runCpld", "Set high to run CPLD which enables event capture, low to hold logic in reset", true);
        // portC
        private PortBit runAdc = new PortBit(SeeBetter1011.this, "c0", "runAdc", "High to run ADC", true);
        // portE
        /** Bias generator power down bit */
        PortBit powerDown = new PortBit(SeeBetter1011.this, "e2", "powerDown", "High to disable master bias and tie biases to default rails", false);
        PortBit nChipReset = new PortBit(SeeBetter1011.this, "e3", "nChipReset", "Low to reset AER circuits and hold pixels in reset, High to run", true); // shouldn't need to manipulate from host
        PortBit nCPLDReset = new PortBit(SeeBetter1011.this, "e7", "nCPLDReset", "Low to reset CPLD", true);// shouldn't need to manipulate from host
        //
        // adc and scanner configurations are stored in scanner and adc; updates to here should update CPLD config below
        // CPLD shift register contents specified here by CPLDInt and CPLDBit
        private CPLDBit use5TBuffer = new CPLDBit(SeeBetter1011.this, 0, "use5TBuffer", "enables 5T OTA vs Source-Follower in-pixel buffer", true);
        private CPLDBit useCalibration = new CPLDBit(SeeBetter1011.this, 1, "useCalibration", "enables on-chip per-pixel calibration current after log intenisty sample", true);
        private CPLDInt adcConfig = new CPLDInt(SeeBetter1011.this, 13, 2, "adcConfig", "ADC configuration bits; computed by ADC with channel and sequencing parameters", 0);
        private CPLDInt adcTrackTime = new CPLDInt(SeeBetter1011.this, 29, 14, "adcTrackTime", "ADC track time in clock cycles which are 15 cycles/us", 0);
        private CPLDInt adcRefOnTime = new CPLDInt(SeeBetter1011.this, 45, 30, "adcRefOnTime", "ADC Reference ON time in clock cycles which are 15 cycles/us", 0);
        private CPLDInt adcRefOffTime = new CPLDInt(SeeBetter1011.this, 61, 46, "adcRefOffTime", "ADC Reference OFF time in clock cycles which are 15 cycles/us", 0);
        private CPLDInt adcIdleTime = new CPLDInt(SeeBetter1011.this, 77, 62, "adcIdleTime", "ADC idle time after last acquisition in clock cycles which are 15 cycles/us", 0);
        private CPLDInt scanY = new CPLDInt(SeeBetter1011.this, 83, 78, "scanY", "cochlea tap to monitor when not scanning continuously", 0);
        private CPLDInt scanX = new CPLDInt(SeeBetter1011.this, 89, 84, "scanX", "cochlea tap to monitor when not scanning continuously", 0);
        private CPLDBit scanContinuouslyEnabled = new CPLDBit(SeeBetter1011.this, 90, "scanContinuouslyEnabled", "enables continuous scanning of on-chip scanner", false);
        //
        // lists of ports and CPLD config
        private ADC adc;
        private Scanner scanner;
        private LogReadoutControl logReadoutControl;

        /** Creates a new instance of SeeBetterConfig for cDVSTest with a given hardware interface
         *@param chip the chip this biasgen belongs to
         */
        public SeeBetterConfig(Chip chip) {
            super(chip);
            setName("SeeBetter1011Biasgen");

            // setup listeners


            // port bits
            addConfigValue(runCpld);
            addConfigValue(runAdc);
            addConfigValue(powerDown);
            addConfigValue(nCPLDReset);

            // cpld shift register stuff
            addConfigValue(adcConfig);
            addConfigValue(adcTrackTime);
            addConfigValue(adcIdleTime);
            addConfigValue(use5TBuffer);
            addConfigValue(useCalibration);
            addConfigValue(adcRefOffTime);
            addConfigValue(adcRefOnTime);
            addConfigValue(scanY);
            addConfigValue(scanX);
            addConfigValue(scanContinuouslyEnabled);


            // masterbias
            getMasterbias().setKPrimeNFet(55e-3f); // estimated from tox=42A, mu_n=670 cm^2/Vs // TODO fix for UMC18 process
            getMasterbias().setMultiplier(9 * (24f / 2.4f) / (4.8f / 2.4f));  // =45  correct for dvs320
            getMasterbias().setWOverL(4.8f / 2.4f); // masterbias has nfet with w/l=2 at output
            getMasterbias().addObserver(this); // changes to masterbias come back to update() here

            // shifted sources (not used on SeeBetter10/11)
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


            // DAC object for simple voltage DAC
            final float Vdd = 1.8f;
//           public DAC(int numChannels, int resolutionBits, float refMinVolts, float refMaxVolts, float vdd){
            DAC dac = new DAC(1, 8, 0, Vdd, Vdd);
            //    public VPot(Chip chip, String name, DAC dac, int channel, Type type, Sex sex, int bitValue, int displayPosition, String tooltipString) {
            thermometerDAC = new VPot(SeeBetter1011.this, "LogAmpRef", dac, 0, Type.NORMAL, Sex.N, 9, 0, "Voltage DAC for log intensity switched cap amplifier");
            thermometerDAC.addObserver(this);

            setPotArray(new IPotArray(this));

            // on SeeBetter1011 pots are as follows starting from input end of shift register
            try {
                addIPot("DiffBn,n,normal,differencing amp"); // at input end of shift register
                addIPot("OnBn,n,normal,DVS brighter threshold");
                addIPot("OffBn,n,normal,DVS darker threshold");
                addIPot("PrOTABp,p,normal,Photoreceptor OTA used in bDVS pixels"); // TODO what's this?
                addIPot("PrCasBnc,n,cascode,Photoreceptor cascode (when used in pixel type bDVS sDVS and some of the small DVS pixels)");
                addIPot("RODiffAmpBn,n,normal,Log intensity readout OTA bias current");
                addIPot("PrLvlShiftBn,p,cascode,Photoreceptor level shifter bias");
                addIPot("PixInvBn,n,normal,Pixel request inversion static inverter bias");
                addIPot("PrBp,p,normal,Photoreceptor bias current");
                addIPot("PrSFBp,p,normal,Photoreceptor follower bias current (when used in pixel type)");
                addIPot("RefrBp,p,normal,DVS refractory period current");
                addIPot("AEPdBn,n,normal,Request encoder pulldown static current");
                addIPot("AERxEBn,n,normal,Handshake state machine pulldown bias current");
                addIPot("AEPuXBp,p,normal,AER column pullup");
                addIPot("AEPuYBp,p,normal,AER row pullup");
                addIPot("IFThrBn,n,normal,Integrate and fire intensity neuron threshold");
                addIPot("IFRefrBn,n,normal,Integrate and fire intensity neuron refractory period bias current");
                addIPot("PadFollBn,n,normal,Follower-pad buffer bias current");
                addIPot("ROGateBn,n,normal,Bias voltage for log readout transistor ");
                addIPot("ROCasBnc,n,cascode,Bias voltage for log readout cascode ");
                addIPot("RefCurrentBn,n,normal,Reference current for log readout ");
                addIPot("LocalBufBn,n,normal,Local OTA voltage follower buffer bias current"); // at far end of shift register
            } catch (Exception e) {
                throw new Error(e.toString());
            }

            // on-chip output muxes
            allMuxes = new AllMuxes();
            allMuxes.addObserver(this);

            // extra configuration bits
            extraOnchipConfigBits = new ExtraOnChipConfigBits();
            extraOnchipConfigBits.addObserver(this);

            // adc 
            adc = new ADC();
            adc.addObserver(this);

            // control of log readout
            logReadoutControl = new LogReadoutControl();

            scanner = new Scanner(SeeBetter1011.this);
//            scanner.addObserver(this);
            setBatchEditOccurring(true);
            loadPreferences();
            setBatchEditOccurring(false);
            byte[] b = formatConfigurationBytes(this);

        } // constructor

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

                /*     public ConfigurableIPot32(SeeBetterConfig biasgen, String name, int shiftRegisterNumber,
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

        /** Command sent to firmware by vendor request */
        public class ConfigCmd {

            short code;
            String name;

            public ConfigCmd(int code, String name) {
                this.code = (short) code;
                this.name = name;
            }

            @Override
            public String toString() {
                return "ConfigCmd{" + "code=" + code + ", name=" + name + '}';
            }
        }
        /** Vendor request command understood by the firmware in connection with  VENDOR_REQUEST_SEND_BIAS_BYTES */
        public final ConfigCmd CMD_IPOT = new ConfigCmd(1, "IPOT"),
                CMD_SCANNER = new ConfigCmd(3, "SCANNER"),
                CMD_SETBIT = new ConfigCmd(5, "SETBIT"),
                CMD_CPLD_CONFIG = new ConfigCmd(8, "CPLD");
        public final String[] CMD_NAMES = {"IPOT", "SCANNER", "SET_BIT", "CPLD_CONFIG"};
        final byte[] emptyByteArray = new byte[0];

        /** Sends complete configuration to hardware by calling several updates with objects
         * 
         * @param biasgen this object
         * @throws HardwareInterfaceException on some error
         */
        @Override
        public void sendConfiguration(Biasgen biasgen) throws HardwareInterfaceException {

            if (isBatchEditOccurring()) {
                log.info("batch edit occurring, not sending configuration yet");
                return;
            }
            log.info("sending full configuration");
            // on-chip shift register
            byte[] bytes = formatConfigurationBytes(this);
            if (bytes == null) {
                return; // not ready yet, called by super
            }
            sendConfig(CMD_IPOT, 0, bytes); // the usual packing of ipots with other such as shifted sources, on-chip voltage dac, and diagnotic mux output and extra configuration

//            // port bits
//            for(PortBit b:portBits){
//                if(b!=nCPLDReset) update(b, null);
//            }

            // CPLD registers
            update(adc, null);
//            update(scanner, null);

            // enables acquisition
//            update(runCpld, null);
        }

        /** The central point for communication with HW from biasgen. All objects in SeeBetterConfig are Observables
         * and addConfigValue SeeBetterConfig.this as Observer. They then call notifyObservers when their state changes.
         * Objects such as adcProxy store preferences for ADC, and update should update the hardware registers accordingly.
         * @param observable IPot, Scanner, etc
         * @param object notifyChange used at present
         */
        @Override
        synchronized public void update(Observable observable, Object object) {  // thread safe to ensure gui cannot retrigger this while it is sending something
            // sends a vendor request depending on type of update
            // vendor request is always VR_CONFIG
            // value is the type of update
            // index is sometimes used for 16 bitmask updates
            // bytes are the rest of data
            if (isBatchEditOccurring()) {
                return;
            }
            try {
                if (observable instanceof IPot || observable instanceof AllMuxes || observable instanceof ExtraOnChipConfigBits || observable instanceof VPot || observable instanceof ShiftedSourceBias) { // must send all of the onchip shift register values to replace shift register contents
                    // handle everything on the on-chip shift register 
                    byte[] bytes = formatConfigurationBytes(this);
                    sendConfig(CMD_IPOT, 0, bytes); // the usual packing of ipots with other such as shifted sources, on-chip voltage dac, and diagnotic mux output and extra configuration

                } else if (observable instanceof Masterbias) {
                    powerDown.set(getMasterbias().isPowerDownEnabled());
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
                } else if (observable instanceof ADC) {
                    sendCPLDConfig(); // CPLD register updates on device side save and restore the RUN_ADC flag
//                    update(runAdc, null);
                } //                else if (observable instanceof Scanner) {
                //                    sendCPLDConfig();
                //                } 
                else {
                    super.update(observable, object);  // super (SeeBetterConfig) handles others, e.g. masterbias
                }
            } catch (HardwareInterfaceException e) {
                log.warning("On update() caught " + e.toString());
            }
        }

        private void sendCPLDConfig() throws HardwareInterfaceException {
            byte[] bytes = cpldConfig.getBytes();
            sendConfig(CMD_CPLD_CONFIG, 0, bytes);
        }

        /** convenience method for sending configuration to hardware. Sends vendor request VR_WRITE_CONFIG with subcommand cmd, index index and bytes bytes.
         * 
         * @param cmd the subcommand to set particular configuration, e.g. CMD_CPLD_CONFIG
         * @param index unused
         * @param bytes the payload
         * @throws HardwareInterfaceException 
         */
        void sendConfig(ConfigCmd cmd, int index, byte[] bytes) throws HardwareInterfaceException {

            StringBuilder sb = new StringBuilder(String.format("sending config cmd=0x%X (%s) index=0x%X with %d bytes", cmd.code, cmd.name, index, bytes.length));
            if (bytes == null || bytes.length == 0) {
            } else {
                int max = 50;
                if (bytes.length < max) {
                    max = bytes.length;
                }
                sb.append(" = ");
                for (int i = 0; i < max; i++) {
                    sb.append(String.format("%X, ", bytes[i]));
                }
                log.info(sb.toString());
            } // end debug

            if (bytes == null) {
                bytes = emptyByteArray;
            }
//            log.info(String.format("sending command vendor request cmd=%d, index=%d, and %d bytes", cmd, index, bytes.length));
            if (getHardwareInterface() != null && getHardwareInterface() instanceof CypressFX2) {
                ((CypressFX2) getHardwareInterface()).sendVendorRequest(VR_WRITE_CONFIG, (short) (0xffff & cmd.code), (short) (0xffff & index), bytes); // & to prevent sign extension for negative shorts
            }
        }

        /** 
         * Convenience method for sending configuration to hardware. Sends vendor request VENDOR_REQUEST_SEND_BIAS_BYTES with subcommand cmd, index index and empty byte array.
         * 
         * @param cmd the subcommand
         * @param index data
         * @throws HardwareInterfaceException 
         */
        void sendConfig(ConfigCmd cmd, int index) throws HardwareInterfaceException {
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
            setBatchEditOccurring(true); // stop updates on building panel
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
            JPanel adcScannerLogPanel = new JPanel();
            adcScannerLogPanel.setLayout(new BoxLayout(adcScannerLogPanel, BoxLayout.Y_AXIS));
            bgTabbedPane.add("Analog output", adcScannerLogPanel);
            adcScannerLogPanel.add(new ParameterControlPanel(adc));
            adcScannerLogPanel.add(new ParameterControlPanel(scanner));
            adcScannerLogPanel.add(new ParameterControlPanel(logReadoutControl));

            JPanel moreConfig = new JPanel(new BorderLayout());

            JPanel extraPanel = extraOnchipConfigBits.makeControlPanel();
            extraPanel.setBorder(new TitledBorder("Extra on-chip bits"));
            moreConfig.add(extraPanel, BorderLayout.WEST);

            JPanel portBitsPanel = new JPanel();
            portBitsPanel.setLayout(new BoxLayout(portBitsPanel, BoxLayout.Y_AXIS));
            for (PortBit p : portBits) {

                portBitsPanel.add(new JRadioButton(p.getAction()));
            }
            portBitsPanel.setBorder(new TitledBorder("Cypress FX2 port bits"));

            moreConfig.add(portBitsPanel, BorderLayout.CENTER);
            
            JPanel specialButtons=new JPanel();
            JButton resendButton=new JButton("sendConfiguration");
            resendButton.addActionListener(new ActionListener(){

                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        sendConfiguration(SeeBetterConfig.this);
                    } catch (HardwareInterfaceException ex) {
                        log.warning(ex.toString());
                    }
                }
                
            });
            specialButtons.add(resendButton,BorderLayout.CENTER);
            moreConfig.add(specialButtons,BorderLayout.NORTH);
            bgTabbedPane.addTab("More config", moreConfig);

            bPanel.add(bgTabbedPane, BorderLayout.CENTER);
            // only select panel after all added
            try {
                bgTabbedPane.setSelectedIndex(getPrefs().getInt("SeeBetter1011.bgTabbedPaneSelectedIndex", 0));
            } catch (IndexOutOfBoundsException e) {
                bgTabbedPane.setSelectedIndex(0);
            }
            // add listener to store last selected tab
            bgTabbedPane.addMouseListener(new java.awt.event.MouseAdapter() {

                @Override
                public void mouseClicked(java.awt.event.MouseEvent evt) {
                    tabbedPaneMouseClicked(evt);
                }
            });
            setBatchEditOccurring(false);

            return bPanel;
        }

        private void tabbedPaneMouseClicked(java.awt.event.MouseEvent evt) {
            getPrefs().putInt("SeeBetter1011.bgTabbedPaneSelectedIndex", bgTabbedPane.getSelectedIndex());
        }

        /** Formats the data sent to the microcontroller to load bias and other configuration to the chip (not FX2 or CPLD configuration). 
        <p>
         * Data is sent in bytes. Each byte is loaded into the shift register in big-endian bit order, starting with the msb and ending with the lsb.
         * Bytes are loaded starting with the first byte from formatConfigurationBytes (element 0). Therefore the last bit in the on-chip shift register (the one
         * that is furthest away from the bit input pin) should be in the msb of the first byte returned by formatConfigurationBytes.
         * @return byte array to be sent, which will be loaded into the chip starting with element 0 msb.
         * @param biasgen this SeeBetterConfig
         */
        @Override
        public byte[] formatConfigurationBytes(Biasgen biasgen) {
            ByteBuffer bb = ByteBuffer.allocate(1000);

            if (getPotArray() == null) {
                return null; // array not yet contructed, we were called here by super()
            }            // must return integral number of bytes and on-chip biasgen must be integral number of bytes, by method contract
//            ByteBuffer potbytes = ByteBuffer.allocate(300);


            IPotArray ipots = (IPotArray) potArray;

            byte[] bytes = new byte[potArray.getNumPots() * 8];
            int byteIndex = 0;


            Iterator i = ipots.getShiftRegisterIterator();
            while (i.hasNext()) {
                // for each bias starting with the first one (the one closest to the ** END ** of the shift register
                // we get the binary representation in byte[] form and from MSB ro LSB stuff these values into the byte array
                IPot iPot = (IPot) i.next();
                byte[] thisBiasBytes = iPot.getBinaryRepresentation();
                System.arraycopy(thisBiasBytes, 0, bytes, byteIndex, thisBiasBytes.length);
                byteIndex += thisBiasBytes.length;
//                log.info("added bytes for "+iPot);
            }
            byte[] potbytes = new byte[byteIndex];
            System.arraycopy(bytes, 0, potbytes, 0, byteIndex);



//        for (Pot p : getPotArray().getPots()) {
//                potbytes.put(p.getBinaryRepresentation());
//            }
//            potbytes.flip(); // written in order 

            String configBitsBits = extraOnchipConfigBits.getBitString();
            String muxBitsBits = allMuxes.getBitString(); // the first nibble is the imux in big endian order, bit3 of the imux is the very first bit.

            byte[] muxAndConfigBytes = bitString2Bytes((configBitsBits + muxBitsBits)); // returns bytes padded at end

            bb.put(muxAndConfigBytes); // loaded first to go to far end of shift register

            // 256 value (8 bit) VDAC for amplifier reference
            byte vdac = (byte) thermometerDAC.getBitValue(); //Byte.valueOf("9");
            bb.put(vdac);   // VDAC needs 8 bits
            bb.put(potbytes);

            // the 4 shifted sources, each 2 bytes
            for (ShiftedSourceBias ss : ssBiases) {
                bb.put(ss.getBinaryRepresentation());
            }

            // make buffer for all output bytes
            byte[] allBytes = new byte[bb.position()];
            bb.flip(); // flips to read them out in order of putting them in, i.e., get configBitBytes first
            bb.get(allBytes); // we write these in vendor request

            StringBuilder sb = new StringBuilder(allBytes.length + " bytes sent to FX2 to be loaded big endian into on-chip shift register for each byte in order \n");
            for (byte b : allBytes) {
                sb.append(String.format("%02x ", b));
            }
            log.info(sb.toString());
            return allBytes; // configBytes may be padded with extra bits to make up a byte, board needs to know this to chop off these bits
        }

        /** Controls the log intensity readout by wrapping the relevant bits */
        public class LogReadoutControl {

            public boolean isSelect5Tbuffer() {
                return use5TBuffer.isSet();
            }

            public boolean isUseCalibration() {
                return useCalibration.isSet();
            }

            public void setSelect5Tbuffer(boolean se) {
                use5TBuffer.set(se);
            }

            public void setUseCalibration(boolean se) {
                useCalibration.set(se);
            }

            public void setRefOnTime(int timeUs) {
                adcRefOnTime.set(timeUs * ADC_CLK_CYCLES_PER_US);
            }

            public void setRefOffTime(int timeUs) {
                adcRefOffTime.set(timeUs * ADC_CLK_CYCLES_PER_US);
            }

            public int getRefOnTime() {
                return adcRefOnTime.get() / ADC_CLK_CYCLES_PER_US;
            }

            public int getRefOffTime() {
                return adcRefOffTime.get() / ADC_CLK_CYCLES_PER_US;
            }
        }

        public class ADC extends Observable {

            private final int ADCchannelshift = 5;
            private final short ADCconfig = (short) 0x100;   //normal power mode, single ended, sequencer unused : (short) 0x908;
            int channel = getPrefs().getInt("ADC.channel", 0);

            public boolean isADCEnabled() {
                return runAdc.isSet();
            }

            public void setADCEnabled(boolean yes) {
                runAdc.set(yes);
            }

            public int getIdleTime() {
                return adcIdleTime.get() / ADC_CLK_CYCLES_PER_US;
            }

            public int getTrackTime() {
                return adcTrackTime.get() / ADC_CLK_CYCLES_PER_US;
            }

            public void setIdleTime(int timeUs) {
                adcIdleTime.set(timeUs * ADC_CLK_CYCLES_PER_US);
            }

            public void setTrackTime(int timeUs) {
                adcTrackTime.set(timeUs * ADC_CLK_CYCLES_PER_US);
            }

            public int getAdcChannel() {
                return channel;
            }

            public void setAdcChannel(int chan) {
                if (chan <= 0) {
                    chan = 0;
                } else if (chan > 3) {
                    chan = 3;
                }
                if (this.channel != chan) {
                    setChanged();
                }
                this.channel = chan;
                getPrefs().putInt("ADC.channel", chan);
                adcConfig.set((ADCconfig | (chan << ADCchannelshift)));
                notifyObservers();
            }
        }

        /** Extends base scanner class to control the relevant bits and parameters of the hardware */
        public class Scanner implements PreferenceChangeListener, HasPreference, Observer {

            private final int minScanX = 0;
            private final int maxScanX = 63;
            private final int minScanY = 0;
            private final int maxScanY = 63;

            public Scanner(SeeBetter1011 chip) {
                loadPreference();
                getPrefs().addPreferenceChangeListener(this);
                hasPreferencesList.add(this); // not really used since all preferences are in the CPLD configuration bits used here
            }

//            public int getPeriod() {
//                return adc.getIdleTime() + adc.getTrackTime();
//            }
//            /** Sets the scan rate using the ADC idleTime setting, indirectly. 
//             * 
//             * @param period 
//             */
//            public void setPeriod(int period) {
//                boolean old = adc.isADCEnabled();
//                adc.setADCEnabled(false);
//                adc.setIdleTime(period); // TODO fix period units using track time + conversion time + idleTime
//                if (old) {
//                    adc.setADCEnabled(old);
//                }
//            }
            public void setScanX(int scanX) {
                SeeBetterConfig.this.scanX.set(scanX);
            }

            public int getScanX() {
                return SeeBetterConfig.this.scanX.get();
            }

            public void setScanY(int scanX) {
                SeeBetterConfig.this.scanY.set(scanX);
            }

            public int getScanY() {
                return SeeBetterConfig.this.scanY.get();
            }

            public boolean isScanContinuouslyEnabled() {
                return SeeBetterConfig.this.scanContinuouslyEnabled.isSet();
            }

            public void setScanContinuouslyEnabled(boolean scanContinuouslyEnabled) {
                SeeBetterConfig.this.scanContinuouslyEnabled.set(scanContinuouslyEnabled);
            }

            public void preferenceChange(PreferenceChangeEvent e) {
            }

            @Override
            public void loadPreference() {
                // ocnfig loads itself
//                setScanX(SeeBetterConfig.this.scanX.get());
//                setScanY(SeeBetterConfig.this.scanY.get());
//                setScanContinuouslyEnabled(SeeBetterConfig.this.scanContinuouslyEnabled.isSet());
            }

            @Override
            public void storePreference() {
            }

            @Override
            public String toString() {
                return "Scanner{" + "x=" + getScanX() + " y=" + getScanY() + " scanContinuouslyEnabled=" + isScanContinuouslyEnabled() + '}';
            }

            @Override
            public void update(Observable o, Object arg) {
//                if (o == SeeBetterConfig.this.scanContinuouslyEnabled) {
//                    setScanContinuouslyEnabled(SeeBetterConfig.this.scanContinuouslyEnabled.isSet());
//                } else if (o == SeeBetterConfig.this.scanX) {
//                    setScanX(SeeBetterConfig.this.scanX.get());
//                } else if (o == SeeBetterConfig.this.scanY) {
//                    setScanX(SeeBetterConfig.this.scanY.get());
//                }
            }

            /**
             * @return the minScanX
             */
            public int getMinScanX() {
                return minScanX;
            }

            /**
             * @return the maxScanX
             */
            public int getMaxScanX() {
                return maxScanX;
            }

            /**
             * @return the minScanY
             */
            public int getMinScanY() {
                return minScanY;
            }

            /**
             * @return the maxScanY
             */
            public int getMaxScanY() {
                return maxScanY;
            }
        } // Scanner

        /**
         * Formats bits represented in a string as '0' or '1' as a byte array to be sent over the interface to the firmware, for loading
         * in big endian bit order, in order of the bytes sent starting with byte 0.
         * <p>
         * Because the firmware writes integral bytes it is important that the 
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
            int nbits = bitString.length();
            // compute needed number of bytes
            int nbytes = (nbits % 8 == 0) ? (nbits / 8) : (nbits / 8 + 1); // 4->1, 8->1, 9->2
            // for simplicity of following, left pad with 0's right away to get integral byte string
            int npad = nbytes * 8 - nbits;
            String pad = new String(new char[npad]).replace("\0", "0"); // http://stackoverflow.com/questions/1235179/simple-way-to-repeat-a-string-in-java
            bitString = pad + bitString;
            byte[] byteArray = new byte[nbytes];
            int bit = 0;
            for (int bite = 0; bite < nbytes; bite++) { // for each byte
                for (int i = 0; i < 8; i++) { // iterate over each bit of this byte
                    byteArray[bite] = (byte) ((0xff & byteArray[bite]) << 1); // first left shift previous value, with 0xff to avoid sign extension
                    if (bitString.charAt(bit) == '1') { // if there is a 1 at this position of string (starting from left side) 
                        // this conditional and loop structure ensures we count in bytes and that we left shift for each bit position in the byte, padding on the right with 0's
                        byteArray[bite] |= 1; // put a 1 at the lsb of the byte
                    }
                    bit++; // go to next bit of string to the right

                }
            }
            return byteArray;
        }

        /** Bits on the on-chip shift register but not an output mux control, added to end of shift register. Control
         * holding different pixel arrays in reset and how the RC delays are configured.
        
         */
        public class ExtraOnChipConfigBits extends Observable implements HasPreference { // TODO fix for config bit of pullup

            final int TOTAL_NUM_BITS = 24;  // number of these bits on this chip, at end of biasgen shift register
            boolean value = false;

            public ExtraOnChipConfigBits() {
                hasPreferencesList.add(this);
            }
            OnchipConfigBit pullupX = new OnchipConfigBit(SeeBetter1011.this, "useStaticPullupX", 0, "turn on static pullup for X addresses (columns)", false),
                    pullupY = new OnchipConfigBit(SeeBetter1011.this, "useStaticPullupY", 1, "turn on static pullup for Y addresses (rows)", true),
                    delayY0 = new OnchipConfigBit(SeeBetter1011.this, "delayY0", 2, "RC delay columns, 1x", false),
                    delayY1 = new OnchipConfigBit(SeeBetter1011.this, "delayY1", 3, "RC delay columns, 2x", false),
                    delayY2 = new OnchipConfigBit(SeeBetter1011.this, "delayY2", 4, "RC delay columns 4x", false),
                    delayX0 = new OnchipConfigBit(SeeBetter1011.this, "delayX0", 5, "RC delay rows, 1x", false),
                    delayX1 = new OnchipConfigBit(SeeBetter1011.this, "delayX1", 6, "RC delay rows, 2x", false),
                    delayX2 = new OnchipConfigBit(SeeBetter1011.this, "delayX2", 7, "RC delay rows, 4x", false),
                    sDVSReset = new OnchipConfigBit(SeeBetter1011.this, "sDVSReset", 8, "holds sensitive DVS (sDVS) array in reset", false),
                    bDVSReset = new OnchipConfigBit(SeeBetter1011.this, "bDVSReset", 9, "holds big DVS + log intensity (bDVS) array in reset", false),
                    ros = new OnchipConfigBit(SeeBetter1011.this, "ROS", 10, "reset on scan enabled", false),
                    delaySM0 = new OnchipConfigBit(SeeBetter1011.this, "delaySM0", 11, "adds delay to state machine, 1x", false),
                    delaySM1 = new OnchipConfigBit(SeeBetter1011.this, "delaySM1", 12, "adds delay to state machine, 2x", false),
                    delaySM2 = new OnchipConfigBit(SeeBetter1011.this, "delaySM2", 13, "adds delay to state machine, 4x", false);
            OnchipConfigBit[] bits = {pullupX, pullupY, delayY0, delayY1, delayY2, delayX0, delayX1, delayX2, sDVSReset, bDVSReset, ros, delaySM0, delaySM1, delaySM2};

            @Override
            public void loadPreference() {
                for (OnchipConfigBit b : bits) {
                    b.loadPreference();
                }
            }

            @Override
            public void storePreference() {
                for (OnchipConfigBit b : bits) {
                    b.storePreference();
                }
            }

            /** Returns the bit string to send to the firmware to load a bit sequence for the config bits in the shift register.
             * 
             * Bytes sent to FX2 are loaded big endian into shift register (msb first). 
             * Here returned string has named config bits at right end and unused bits at left end. Right most character is pullupX.
             * Think of the entire on-chip shift register laid out from right to left with input at right end and extra config bits at left end.
             * Bits are loaded in order of bit string here starting from left end (the unused registers)
             * 
             * @return string of 0 and 1 with first element of extraOnchipConfigBits at right hand end, and starting with padding bits to fill unused registers.
             */
            String getBitString() {
                StringBuilder s = new StringBuilder();
                // iterate over list
                for (int i = 0; i < TOTAL_NUM_BITS - bits.length; i++) {
                    s.append("1"); // loaded first into unused parts of final shift register
                }
                for (int i = bits.length - 1; i >= 0; i--) {
                    s.append(bits[i].isSet() ? "1" : "0");
                }
                log.info(s.length() + " extra config bits with unused registers at left end =" + s);
                return s.toString();
            }

            /** Returns a control panel for setting the bits, using the Actions
             * 
             * @return the panel, with BoxLayout.Y_AXIS layout
             */
            JPanel makeControlPanel() {
                JPanel pan = new JPanel();
                pan.setLayout(new BoxLayout(pan, BoxLayout.Y_AXIS));
                for (OnchipConfigBit b : bits) {
                    JRadioButton but = new JRadioButton(b.getAction());
                    pan.add(but);
                }
                return pan;
            }
        } // ExtraOnChipConfigBits

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
                if (this.selectedChannel != i) {
                    setChanged();
                }
                this.selectedChannel = i;
                notifyObservers();
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
        } // OutputMux

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
                put(7, ADC_CLK_CYCLES_PER_US);
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

        /** the output multiplexors for on-chip diagnostic output */
        public class AllMuxes extends Observable implements Observer {

            OutputMux[] vmuxes = {new VoltageOutputMux(1), new VoltageOutputMux(2), new VoltageOutputMux(3), new VoltageOutputMux(4)};
            OutputMux[] dmuxes = {new LogicMux(1), new LogicMux(2), new LogicMux(3), new LogicMux(4), new LogicMux(5)};
            ArrayList<OutputMux> muxes = new ArrayList();

            String getBitString() {
                int nBits = 0;
                StringBuilder s = new StringBuilder();
                for (OutputMux m : muxes) {
                    s.append(m.getBitString());
                    nBits += m.nSrBits;
                }

                return s.toString();
            }

            AllMuxes() {

                muxes.addAll(Arrays.asList(dmuxes)); // 5 logic muxes, first in list since at end of chain - bits must be sent first, before any biasgen bits
                muxes.addAll(Arrays.asList(vmuxes)); // finally send the 3 voltage muxes

                for (OutputMux m : muxes) {
                    m.addObserver(this);
                }

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
                    dmuxes[i].put(ADC_CLK_CYCLES_PER_US, "RCarb");
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

            /** Passes on notifies from muxes
             * 
             * @param o ignored
             * @param arg passed on to Observers
             */
            @Override
            public void update(Observable o, Object arg) {
                setChanged();
                notifyObservers(arg);
            }
        } // AllMuxes
    } // SeeBetterConfig

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
