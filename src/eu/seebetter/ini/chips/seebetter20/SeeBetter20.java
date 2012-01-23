/*
created 26 Oct 2008 for new cDVSTest chip
 * adapted apr 2011 for cDVStest30 chip by tobi
 * adapted 25 oct 2011 for SeeBetter10/11 chips by tobi
 *
 */
package eu.seebetter.ini.chips.seebetter20;

import ch.unizh.ini.jaer.chip.retina.*;
import com.sun.opengl.util.j2d.TextRenderer;
import eu.seebetter.ini.chips.*;
import eu.seebetter.ini.chips.config.*;
import eu.seebetter.ini.chips.seebetter20.SeeBetter20.SeeBetter20DisplayMethod;
import eu.seebetter.ini.chips.seebetter20.SeeBetter20.SeeBetter20Renderer;
import java.awt.event.ActionEvent;
import java.awt.geom.Point2D.Float;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JRadioButton;
import net.sf.jaer.aemonitor.*;
import net.sf.jaer.biasgen.*;
import net.sf.jaer.biasgen.VDAC.VPot;
import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;
import net.sf.jaer.hardwareinterface.*;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Rectangle;
import java.beans.PropertyChangeSupport;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Observable;
import java.util.StringTokenizer;
import java.util.concurrent.Semaphore;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.border.TitledBorder;
import net.sf.jaer.Description;
import net.sf.jaer.biasgen.Pot.Sex;
import net.sf.jaer.biasgen.Pot.Type;
import net.sf.jaer.biasgen.VDAC.DAC;
import net.sf.jaer.biasgen.VDAC.VPotGUIControl;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.RetinaRenderer;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2;
import net.sf.jaer.util.ParameterControlPanel;
import net.sf.jaer.util.RemoteControlCommand;
import net.sf.jaer.util.RemoteControlled;
import net.sf.jaer.util.filter.LowpassFilter2d;

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
@Description("SeeBetter20 test chip")
public class SeeBetter20 extends AETemporalConstastRetina implements HasIntensity {

    /** Describes size of array of pixels on the chip, in the pixels address space */
    public static class PixelArray extends Rectangle {

        int pitch;

        /** Makes a new description. Assumes that Extractor already right shifts address to remove even and odd distinction of addresses.
         * 
         * @param pitch pitch of pixels in raw AER address space
         * @param x left corner origin x location of pixel in its address space
         * @param y bottom origin of array in its address space 
         * @param width width in pixels
         * @param height height in pixels.
         */
        public PixelArray(int pitch, int x, int y, int width, int height) {
            super(x, y, width, height);
            this.pitch = pitch;
        }
    }
    public static final PixelArray EntirePixelArray = new PixelArray(2, 0, 0, 64, 32);
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
    public static final int ADC_TYPE_MASK = 0x1000, ADC_DATA_MASK = 0x7ff, ADC_START_BIT = 0x1000, ADC_CHANNEL_MASK = 0x0000, ADC_MODE_BIT = 0x0800; // right now there is no channel info in the data word
    public static final int MAX_ADC = (int) ((1 << 11) - 1);
    /** The computed intensity value. */
    private float globalIntensity = 0;
    private FrameEventPacket frameEventPacket = new FrameEventPacket(PolarityADCSampleEvent.class);
    private SeeBetter20Renderer cDVSRenderer = null;
    private SeeBetter20DisplayMethod cDVSDisplayMethod = null;
    private boolean displayIntensity;
    private boolean displayLogIntensityChangeEvents;
    private boolean snapshot = false;
    SeeBetter20DisplayControlPanel displayControlPanel = null;
    private IntensityFrameData frameData = new IntensityFrameData();
    private SeeBetterConfig config;

    /** Creates a new instance of cDVSTest20.  */
    public SeeBetter20() {
        setName("SeeBetter20");
        setEventClass(PolarityADCSampleEvent.class);
        setSizeX(EntirePixelArray.width*EntirePixelArray.pitch);
        setSizeY(EntirePixelArray.height*EntirePixelArray.pitch);
        setNumCellTypes(3); // two are polarity and last is intensity
        setPixelHeightUm(14.5f);
        setPixelWidthUm(14.5f);

        setEventExtractor(new SeeBetter20Extractor(this));

        setBiasgen(config = new SeeBetter20.SeeBetterConfig(this));

        displayIntensity = getPrefs().getBoolean("displayIntensity", true);
        displayLogIntensityChangeEvents = getPrefs().getBoolean("displayLogIntensityChangeEvents", true);

        setRenderer((cDVSRenderer = new SeeBetter20Renderer(this)));

        cDVSDisplayMethod = new SeeBetter20DisplayMethod(this);
        getCanvas().addDisplayMethod(cDVSDisplayMethod);
        getCanvas().setDisplayMethod(cDVSDisplayMethod);
        cDVSDisplayMethod.setIntensitySource(this);

    }

    @Override
    public void onDeregistration() {
        unregisterControlPanel();
        getAeViewer().removeHelpItem(help1);
    }
    JComponent help1 = null;

    @Override
    public void onRegistration() {
        registerControlPanel();
        help1 = getAeViewer().addHelpURLItem("https://svn.ini.uzh.ch/repos/tobi/tretina/pcb/cDVSTest/cDVSTest.pdf", "cDVSTest PCB design", "shows pcb design");
    }

    private void registerControlPanel() {
        try {
            AEViewer viewer = getAeViewer(); // must do lazy install here because viewer hasn't been registered with this chip at this point
            JPanel imagePanel = viewer.getImagePanel();
            imagePanel.add((displayControlPanel = new eu.seebetter.ini.chips.seebetter20.SeeBetter20DisplayControlPanel(this)), BorderLayout.SOUTH);
            imagePanel.revalidate();
        } catch (Exception e) {
            log.warning("could not register control panel: " + e);
        }
    }

    private void unregisterControlPanel() {
        try {
            AEViewer viewer = getAeViewer(); // must do lazy install here because viewer hasn't been registered with this chip at this point
            JPanel imagePanel = viewer.getImagePanel();
            imagePanel.remove(displayControlPanel);
            imagePanel.revalidate();
        } catch (Exception e) {
            log.warning("could not unregister control panel: " + e);
        }
    }

    /** Creates a new instance of cDVSTest10
     * @param hardwareInterface an existing hardware interface. This constructor is preferred. It makes a new cDVSTest10Biasgen object to talk to the on-chip biasgen.
     */
    public SeeBetter20(HardwareInterface hardwareInterface) {
        this();
        setHardwareInterface(hardwareInterface);
    }

//        @Override
//    public AEFileInputStream constuctFileInputStream(File file) throws IOException {
//        return new SeeBetterFileInputStream(file);
//    }
//
    /** Returns the measured global intensity from the global intensity neuron processed events.
     * The intensity is computed from the ISIs in us of the intensity neuron from 
     * <pre>
     * if (dt > 50) {
    avdt = 0.05f * dt + 0.95f * avdt; // avg over time
    setIntensity(1000f / avdt); // ISI of this much, e.g. 1ms, gives intensity 1
    }
     * </pre>
     * 
     * @return an average spike rate in kHz. Note this breaks the interface contract which calls for 0-1 value.
     */
    @Override
    public float getIntensity() {
        return globalIntensity;
    }

    @Override
    public void setIntensity(float f) {
        globalIntensity = f;
    }

//    int pixcnt=0; // TODO debug
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
    public class SeeBetter20Extractor extends RetinaExtractor {

        private long lastEventTime = System.currentTimeMillis();
        private final long AUTO_RESET_TIMEOUT_MS = 1000;
        // according to  D:\Users\tobi\Documents\avlsi-svn\db\Firmware\cDVSTest20\cDVSTest_dataword_spec.pdf
//        public static final int XMASK = 0x3fe,  XSHIFT = 1,  YMASK = 0x000,  YSHIFT = 12,  INTENSITYMASK = 0x40000;
        private int lastIntenTs = 0;
        private short[] countX;
        private short[] countY;

        public SeeBetter20Extractor(SeeBetter20 chip) {
            super(chip);
            resetCounters();
        }
        private float avdt = 100; // used to compute rolling average of intensity

        
        private void resetCounters(){
            int numReadoutTypes = 2;
            if(countX == null || countY == null){
                countX = new short[numReadoutTypes];
                countY = new short[numReadoutTypes];
            }
            Arrays.fill(countX, 0, numReadoutTypes, (short)0);
            Arrays.fill(countY, 0, numReadoutTypes, (short)0);
        }
        
        /** extracts the meaning of the raw events.
         *@param in the raw events, can be null
         *@return out the processed events. these are partially processed in-place. empty packet is returned if null is supplied as in.
         */
        @Override
        synchronized public EventPacket extractPacket(AEPacketRaw in) {
            if (out == null) {
                out = new FrameEventPacket(chip.getEventClass());
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
            boolean gotAEREvent = false;
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
                        PolarityADCSampleEvent e = (PolarityADCSampleEvent) outItr.nextOutput();
                        e.adcSample = -1; // TODO hack to mark as not an ADC sample
                        e.startOfFrame = false;
                        e.address = data;
                        e.timestamp = (timestamps[i]);
                        e.polarity = (data & 1) == 1 ? PolarityADCSampleEvent.Polarity.On : PolarityADCSampleEvent.Polarity.Off;
                        e.x = (short) (((data & XMASK) >>> XSHIFT));
                        e.y = (short) ((data & YMASK) >>> YSHIFT);

                        if (e.x < EntirePixelArray.width * EntirePixelArray.pitch) { // cDVS pixel array // *2 because size is defined to be 32 and event types are still different x's
                            e.x = (short) (e.x >>> 1);
                            e.y = (short) (e.y >>> 1); // cDVS array is clumped into 32x32
                        }
                        gotAEREvent = true;
                  //      String eventData = "address:"+Integer.toBinaryString(e.address)+"( x: "+Integer.toString(e.x)+", y: "+Integer.toString(e.y)+"), timestamp "+Integer.toString(e.timestamp);
                  //      System.out.println("Addressed Event: "+eventData);
                    }
                } else if ((data & ADDRESS_TYPE_MASK) == ADDRESS_TYPE_ADC) {
                    // if scanner is stopped on one place, then we never get a start bit. we then limit the number of samples
                    // to some maximum number of samples TODO not done yet.
                    // on the SeeBetter10/11/20 chips, the scanner has row and col clocks and inputs but no sync output, i.e., you need to 
                    // know how many pixels there are in order to properly scan the array by counting rows and columns and loading the
                    // correct bits into the input bit.
                    // The start bit is generated by the CPLD at the start of the frame on the first pixel.
                    // the pixels are read out from the chip in column parallel, starting from top left corner of BDVS array.
//                    if ((data & ADC_START_BIT) == ADC_START_BIT) {
//                        // the view loop thread (or hardware interface thread in case of real time mode)
//                        // here swaps the buffers to write to the other buffer.
//                        // The reading thread (could be same one) reads from the one that was previously written to.
//                        // The reading thread acquires the lock during reading to prevent the other thread from swapping during read.
//                        // Likewise, swapBuffers also acquires the lock during swap to prevent a swap during reading of the data.
//                        getFrameData().swapBuffers(); // so possibly other thread writes to the other buffer
//                        getFrameData().setTimestamp(timestamps[i]);
////                        System.out.println("SeeBetter20: start bit detected"); // TODO debug
////                        pixcnt=0; // TODO debug
//                    }
//                    getFrameData().put(data & ADC_DATA_MASK);
//                    System.out.print((data & ADC_DATA_MASK)+"\t"); // TODO debug
//                    if((++pixcnt)%32==0) System.out.println("");  // TODO debug
                    // adc sample data is saved in the packet so that we can most easily log it and play it back
                    PolarityADCSampleEvent e = (PolarityADCSampleEvent) outItr.nextOutput();
                    e.adcSample = data & ADC_DATA_MASK;
                    int sampleType = (data & ADC_MODE_BIT)>>Integer.numberOfTrailingZeros(ADC_MODE_BIT);
                    e.isB = sampleType == 1;
                    e.timestamp = (timestamps[i]);
                    e.address = data;
                    e.startOfFrame = (data & ADC_START_BIT) == ADC_START_BIT;
                    if(e.startOfFrame){
                        resetCounters();
                        if(snapshot){
                            snapshot = false;
                            config.adc.setAdcEnabled(false);
                        }
                    }
                    if(!(countY[sampleType]<chip.getSizeY()/2)){
                        countY[sampleType] = 0;
                        countX[sampleType]++;
                    }
//                    if(e.isB && countX[sampleType] > 0){
//                        e.x=(short)(countX[sampleType]-1); 
//                    } else {
                        e.x=countX[sampleType]; 
//                    }
                    e.y=countY[sampleType]++;
                    String type = "";
                    if(e.isB){type = "B";}else{type = "A";}
                    String eventData = "x: "+e.x+", y:"+e.y+", type: "+type+", start of frame: "+Boolean.toString(e.startOfFrame)+" timestamp: "+Integer.toString(e.timestamp)+", data "+Integer.toBinaryString(e.adcSample)+" ("+Integer.toString(e.adcSample)+")";
                    //System.out.println("ADC Event: "+eventData);
                }

            }
            if (gotAEREvent) {
                lastEventTime = System.currentTimeMillis();
            } else if (config.autoResetEnabled && config.nChipReset.isSet() && System.currentTimeMillis() - lastEventTime > AUTO_RESET_TIMEOUT_MS && getAeViewer() != null && getAeViewer().getPlayMode() == AEViewer.PlayMode.LIVE) {
                config.resetChip();
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
        SeeBetter20.SeeBetterConfig bg;
        try {
            if (getBiasgen() == null) {
                setBiasgen(bg = new SeeBetter20.SeeBetterConfig(this));
                // now we can addConfigValue the control panel

            } else {
                getBiasgen().setHardwareInterface((BiasgenHardwareInterface) hardwareInterface);

            }

        } catch (ClassCastException e) {
            log.warning(e.getMessage() + ": probably this chip object has a biasgen but the hardware interface doesn't, ignoring");
        }
    }

    /**
     * Describes ConfigurableIPots as well as the other configuration bits which control, for example, which
     * outputs are selected. These are all configured by a single shared shift register.
     * <p>
     * With Rx=82k, measured Im=2uA. With Rx=27k, measured Im=8uA.
     * <p>
     * cDVSTest has a pair of shared shifted sources, one for n-type and one for p-type. These sources supply a regulated voltaqe near the power rail.
     * The shifted sources are programmed by bits at the start of the global shared shift register.
     * They are not used on this SeeBetter10/11 chip.
     *
     * @author tobi
     */
    public class SeeBetterConfig extends SeeBetterChipConfig { // extends Config to give us containers for various configuration data

        /** Number of ADC clock cycles per us, for converting from GUIs to config values */
        public static final int ADC_CLK_CYCLES_PER_US = 15;
        /** Number of state transitions for one row without the readout of B (without wait times) */
        public static final int ROW_CC_WO_B = 7;
        /** Number of state transitions for one row with the readout of B (without wait times) */
        public static final int ROW_CC = 13;
        /** Number of additional state transitions (in addition to row readout) for one column (without wait times) */
        public static final int COL_CC = 6;
        /** Number of additional state transitions (in addition to row & col readout) for one frame (without wait times) */
        public static final int FRAME_CC = 4;
        /** VR for handling all configuration changes in firmware */
        public static final byte VR_WRITE_CONFIG = (byte) 0xB8;
        ArrayList<HasPreference> hasPreferencesList = new ArrayList<HasPreference>();
        AllMuxes allMuxes = null; // the output muxes
        private ShiftedSourceBiasCF ssn, ssp, ssnMid, sspMid;
        private ShiftedSourceBiasCF[] ssBiases = new ShiftedSourceBiasCF[2];
//        private VPot thermometerDAC;
        ExtraOnChipConfigBits extraOnchipConfigBits = null;
        ExtraBiasDiagBits extraBiasDiagBits = null;
        int address = 0;
        JPanel bPanel;
        JTabbedPane bgTabbedPane;
        // portA
        private PortBit runCpld = new PortBit(SeeBetter20.this, "a3", "runCpld", "Set high to run CPLD which enables event capture, low to hold logic in reset", true);
        // portC
        private PortBit runAdc = new PortBit(SeeBetter20.this, "c0", "runAdc", "High to run ADC", true);
        // portE
        /** Bias generator power down bit */
        PortBit powerDown = new PortBit(SeeBetter20.this, "e2", "powerDown", "High to disable master bias and tie biases to default rails", false);
        PortBit nChipReset = new PortBit(SeeBetter20.this, "e3", "nChipReset", "Low to reset AER circuits and hold pixels in reset, High to run", true); // shouldn't need to manipulate from host
//        PortBit nCPLDReset = new PortBit(SeeBetter20.this, "e7", "nCPLDReset", "Low to reset CPLD", true);// shouldn't manipulate from host
        //
        // adc and scanner configurations are stored in scanner and adc; updates to here should update CPLD config below
        // CPLD shift register contents specified here by CPLDInt and CPLDBit
        private CPLDInt adcConfig = new CPLDInt(SeeBetter20.this, 11, 0, "adcConfig", "ADC configuration bits; computed by ADC with channel and sequencing parameters", 352);
        private CPLDInt exposure = new CPLDInt(SeeBetter20.this, 27, 12, "exposure", "time between reset and readout of a pixel", 0);
        private CPLDInt colSettle = new CPLDInt(SeeBetter20.this, 43, 28, "colSettle", "time to settle a column select before readout", 0);
        private CPLDInt rowSettle = new CPLDInt(SeeBetter20.this, 59, 44, "rowSettle", "time to settle a row select before readout", 0);
        private CPLDInt resSettle = new CPLDInt(SeeBetter20.this, 75, 60, "resSettle", "time to settle a reset before readout", 0);
        private CPLDInt framePeriod = new CPLDInt(SeeBetter20.this, 107, 76, "framePeriod", "time between two frames", 0);
        private CPLDBit testpixel = new CPLDBit(SeeBetter20.this, 108, "testPixel", "enables continuous scanning of testpixel", false);
        //
        // lists of ports and CPLD config
        private ADC adc;
//        private Scanner scanner;
        private ApsReadoutControl apsReadoutControl;
        // other options
        private boolean autoResetEnabled; // set in loadPreferences

        /** Creates a new instance of SeeBetterConfig for cDVSTest with a given hardware interface
         *@param chip the chip this biasgen belongs to
         */
        public SeeBetterConfig(Chip chip) {
            super(chip);
            setName("SeeBetter20Biasgen");
            
            // setup listeners


            // port bits
            addConfigValue(nChipReset);
            addConfigValue(powerDown);
            addConfigValue(runAdc);
            addConfigValue(runCpld);

            // cpld shift register stuff
            addConfigValue(adcConfig);
            addConfigValue(exposure);
            addConfigValue(resSettle);
            addConfigValue(rowSettle);
            addConfigValue(colSettle);
            addConfigValue(framePeriod);
            addConfigValue(testpixel);


            // masterbias
            getMasterbias().setKPrimeNFet(55e-3f); // estimated from tox=42A, mu_n=670 cm^2/Vs // TODO fix for UMC18 process
            getMasterbias().setMultiplier(9 * (24f / 2.4f) / (4.8f / 2.4f));  // =45  correct for dvs320
            getMasterbias().setWOverL(4.8f / 2.4f); // masterbias has nfet with w/l=2 at output
            getMasterbias().addObserver(this); // changes to masterbias come back to update() here

            // shifted sources (not used on SeeBetter10/11)
            ssn = new ShiftedSourceBiasCF(this);
            ssn.setSex(Pot.Sex.N);
            ssn.setName("SSN");
            ssn.setTooltipString("n-type shifted source that generates a regulated voltage near ground");
            ssn.addObserver(this);
            ssn.setAddress(21);

            ssp = new ShiftedSourceBiasCF(this);
            ssp.setSex(Pot.Sex.P);
            ssp.setName("SSP");
            ssp.setTooltipString("p-type shifted source that generates a regulated voltage near Vdd");
            ssp.addObserver(this);
            ssp.setAddress(20);

            ssBiases[1] = ssn;
            ssBiases[0] = ssp;


            // DAC object for simple voltage DAC
            final float Vdd = 1.8f;
//           public DAC(int numChannels, int resolutionBits, float refMinVolts, float refMaxVolts, float vdd){
//            DAC dac = new DAC(1, 8, 0, Vdd, Vdd);
//            //    public VPot(Chip chip, String name, DAC dac, int channel, Type type, Sex sex, int bitValue, int displayPosition, String tooltipString) {
//            thermometerDAC = new VPot(SeeBetter20.this, "LogAmpRef", dac, 0, Type.NORMAL, Sex.N, 9, 0, "Voltage DAC for log intensity switched cap amplifier");
//            thermometerDAC.addObserver(this);

            setPotArray(new AddressedIPotArray(this));

            // on SeeBetter20 pots are as follows starting from input end of shift register
            try {
                addAIPot("DiffBn,n,normal,differencing amp"); // at input end of shift register
                addAIPot("OnBn,n,normal,DVS brighter threshold");
                addAIPot("OffBn,n,normal,DVS darker threshold");
                addAIPot("ApsCasEpc,p,cascode,xxx"); // TODO what's this?
                addAIPot("PrCasBnc,n,cascode,Photoreceptor cascode (when used in pixel type bDVS sDVS and some of the small DVS pixels)");
                addAIPot("ApsROSF,n,normal,APS readout source follower bias");
                addAIPot("LocalBufBn,n,normal,xxx"); // TODO what's this?
                addAIPot("PixInvBn,n,normal,Pixel request inversion static inverter bias");
                addAIPot("PrBp,p,normal,Photoreceptor bias current");
                addAIPot("PrSFBp,p,normal,Photoreceptor follower bias current (when used in pixel type)");
                addAIPot("RefrBp,p,normal,DVS refractory period current");
                addAIPot("AEPdBn,n,normal,Request encoder pulldown static current");
                addAIPot("AERxEBn,n,normal,Handshake state machine pulldown bias current");
                addAIPot("AEPuXBp,p,normal,AER column pullup");
                addAIPot("AEPuYBp,p,normal,AER row pullup");
                addAIPot("IFThrBn,n,normal,Integrate and fire intensity neuron threshold");
                addAIPot("IFRefrBn,n,normal,Integrate and fire intensity neuron refractory period bias current");
                addAIPot("PadFollBn,n,normal,Follower-pad buffer bias current");
                addAIPot("apsOverflowLevel,n,normal,special overflow level bias ");
                addAIPot("biasBuffer,n,normal,special buffer bias ");
            } catch (Exception e) {
                throw new Error(e.toString());
            }
            // on-chip diagnose bits for the bias generator
            extraBiasDiagBits = new ExtraBiasDiagBits();
            extraBiasDiagBits.addObserver(this);
            
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
            apsReadoutControl = new ApsReadoutControl();

//            scanner = new Scanner(SeeBetter20.this);
//            scanner.addObserver(this);
            setBatchEditOccurring(true);
            loadPreferences();
            setBatchEditOccurring(false);
            try {
                sendOnchipConfig();
            } catch (HardwareInterfaceException ex) {
                Logger.getLogger(SeeBetter20.class.getName()).log(Level.SEVERE, null, ex);
            }
            byte[] b = formatConfigurationBytes(this);

        } // constructor
        
         /** Quick addConfigValue of an addressed pot from a string description, comma delimited
         * 
         * @param s , e.g. "Amp,n,normal,DVS ON threshold"; separate tokens for name,sex,type,tooltip\nsex=n|p, type=normal|cascode
         * @throws ParseException Error
         */
        private void addAIPot(String s) throws ParseException {
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

                getPotArray().addPot(new AddressedIPotCF(this, name, address++,
                        type, sex, false, true,
                        AddressedIPotCF.maxCoarseBitValue / 2, AddressedIPotCF.maxFineBitValue, address, tip));
            } catch (Exception e) {
                throw new Error(e.toString());
            }
        }

        /** Sends everything on the on-chip shift register 
         * 
         * @throws HardwareInterfaceException 
         * @return false if not sent because bytes are not yet initialized
         */
        private boolean sendOnchipConfig() throws HardwareInterfaceException {
            log.info("Send whole OnChip Config");
            
            //biases
            if(getPotArray() == null){
                return false;
            }
            AddressedIPotArray ipots = (AddressedIPotArray) potArray;
            Iterator i = ipots.getShiftRegisterIterator();
            while(i.hasNext()){
                AddressedIPot iPot = (AddressedIPot) i.next();
                if(!sendAIPot(iPot))return false;
            }
            
            //shifted sources
            for (ShiftedSourceBiasCF ss : ssBiases) {
                if(!sendAIPot(ss))return false;
            }   
            
            //diagnose SR
            sendMuxAndConfigBits();
            return true;
        }
        
        public boolean sendMuxAndConfigBits() throws HardwareInterfaceException{
            //diagnose SR
            String biasConfigBits = extraBiasDiagBits.getBitString();
            String configBitsBits = extraOnchipConfigBits.getBitString();
            String muxBitsBits = allMuxes.getBitString(); // the first nibble is the imux in big endian order, bit3 of the imux is the very first bit.
            String onChipConfigBits = ("0"+configBitsBits + muxBitsBits + biasConfigBits);
            byte[] muxAndConfigBytes = bitString2Bytes(onChipConfigBits); // returns bytes padded at end
            if (muxAndConfigBytes == null) {
                return false; // not ready yet, called by super
            }
            //System.out.println("Send on chip config (length"+onChipConfigBits.length()+"): "+onChipConfigBits);
            sendConfig(CMD_CHIP_CONFIG, 0, muxAndConfigBytes);
            return true;
        }

        /**
         * @return the autoResetEnabled
         */
        public boolean isAutoResetEnabled() {
            return autoResetEnabled;
        }

        /**
         * @param autoResetEnabled the autoResetEnabled to set
         */
        public void setAutoResetEnabled(boolean autoResetEnabled) {
            if (this.autoResetEnabled != autoResetEnabled) {
                setChanged();
            }
            this.autoResetEnabled = autoResetEnabled;
            notifyObservers();
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
                CMD_AIPOT = new ConfigCmd(2, "AIPOT"),
                CMD_SCANNER = new ConfigCmd(3, "SCANNER"),
                CMD_CHIP_CONFIG = new ConfigCmd(4, "CHIP"),
                CMD_SETBIT = new ConfigCmd(5, "SETBIT"),
                CMD_CPLD_CONFIG = new ConfigCmd(8, "CPLD");
        public final String[] CMD_NAMES = {"IPOT", "AIPOT", "SCANNER", "CHIP", "SET_BIT", "CPLD_CONFIG"};
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
            if (!sendOnchipConfig()) {
                return;
            }

            sendCPLDConfig();
            for (PortBit b : portBits) {
                update(b, null);
            }
        }
        
        private boolean sendAIPot(AddressedIPot pot) throws HardwareInterfaceException{            
            byte[] bytes = pot.getBinaryRepresentation();
            if (bytes == null) {
                return false; // not ready yet, called by super
            }
            String hex = String.format("%02X%02X%02X",bytes[2],bytes[1],bytes[0]);
            //log.info("Send AIPot for "+pot.getName()+" with value "+hex);
            sendConfig(CMD_AIPOT, 0, bytes); // the usual packing of ipots with other such as shifted sources, on-chip voltage dac, and diagnotic mux output and extra configuration
            return true;
        }

        /** Momentarily puts the pixels and on-chip AER logic in reset and then releases the reset.
         * 
         */
        private void resetChip() {
            log.info("resetting AER communication");
            nChipReset.set(false);
            nChipReset.set(true);
        }

        /** The central point for communication with HW from biasgen. All objects in SeeBetterConfig are Observables
         * and addConfigValue SeeBetterConfig.this as Observer. They then call notifyObservers when their state changes.
         * Objects such as ADC store preferences for ADC, and update should update the hardware registers accordingly.
         * @param observable IPot, Scanner, etc
         * @param object notifyChange - not used at present
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
//            log.info("update with " + observable);
            try {
                if (observable instanceof IPot || observable instanceof VPot) { // must send all of the onchip shift register values to replace shift register contents
                    sendOnchipConfig();
                } else if (observable instanceof AllMuxes || observable instanceof OnchipConfigBit) {
                    sendMuxAndConfigBits();
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
                } else if (observable instanceof AddressedIPot) { 
                    sendAIPot((AddressedIPot)observable);
                } else {
                    super.update(observable, object);  // super (SeeBetterConfig) handles others, e.g. masterbias
                }
            } catch (HardwareInterfaceException e) {
                log.warning("On update() caught " + e.toString());
            }
        }

        private void sendCPLDConfig() throws HardwareInterfaceException {
            byte[] bytes = cpldConfig.getBytes();
            
            log.info("Send Chip Cofig: "+cpldConfig.toString());
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

//            StringBuilder sb = new StringBuilder(String.format("sending config cmd=0x%X (%s) index=0x%X with %d bytes", cmd.code, cmd.name, index, bytes.length));
            if (bytes == null || bytes.length == 0) {
            } else {
                int max = 50;
                if (bytes.length < max) {
                    max = bytes.length;
                }
//                sb.append(" = ");
//                for (int i = 0; i < max; i++) {
//                    sb.append(String.format("%X, ", bytes[i]));
//                }
//                log.info(sb.toString());
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
                for (ShiftedSourceBiasCF ss : ssBiases) {
                    ss.loadPreferences();
                }
            }
//            if (thermometerDAC != null) {
//                thermometerDAC.loadPreferences();
//            }
            setAutoResetEnabled(getPrefs().getBoolean("autoResetEnabled", false));
        }

        @Override
        public void storePreferences() {
            super.storePreferences();
            for (HasPreference hp : hasPreferencesList) {
                hp.storePreference();
            }
            if (ssBiases != null) {
                for (ShiftedSourceBiasCF ss : ssBiases) {
                    ss.storePreferences();
                }
            }
//            if (thermometerDAC != null) {
//                thermometerDAC.storePreferences();
//            }
            getPrefs().putBoolean("autoResetEnabled", autoResetEnabled);
        }

        /**
         *
         * Overrides the default method to addConfigValue the custom control panel for configuring the cDVSTest output muxes
         * and many other chip and board controls.
         *
         * @return a new panel for controlling this chip and board configuration
         */
        @Override
        public JPanel buildControlPanel() {
//            if(displayControlPanel!=null) return displayControlPanel;
            bPanel = new JPanel();
            bPanel.setLayout(new BorderLayout());
            // add a reset button on top of everything
            final Action resetChipAction = new AbstractAction("Reset chip") {

                {
                    putValue(Action.SHORT_DESCRIPTION, "Resets the pixels and the AER logic momentarily");
                }

                @Override
                public void actionPerformed(ActionEvent evt) {
                    resetChip();
                }
            };

            final Action autoResetAction = new AbstractAction("Enable automatic chip reset") {

                {
                    putValue(Action.SHORT_DESCRIPTION, "Enables reset after no activity when nChipReset is inactive");
                    putValue(Action.SELECTED_KEY,isAutoResetEnabled());
                }

                @Override
                public void actionPerformed(ActionEvent e) {
                    setAutoResetEnabled(!autoResetEnabled);
                }
            };
            JPanel specialButtons = new JPanel();
            specialButtons.setLayout(new BoxLayout(specialButtons, BoxLayout.X_AXIS));
//            specialButtons.add(new JButton(sendConfigAction));
            specialButtons.add(new JButton(resetChipAction));
            specialButtons.add(new JCheckBoxMenuItem(autoResetAction));
            bPanel.add(specialButtons, BorderLayout.NORTH);

            bgTabbedPane = new JTabbedPane();
            setBatchEditOccurring(true); // stop updates on building panel
            JPanel combinedBiasShiftedSourcePanel = new JPanel();
            combinedBiasShiftedSourcePanel.setLayout(new BoxLayout(combinedBiasShiftedSourcePanel, BoxLayout.Y_AXIS));
            combinedBiasShiftedSourcePanel.add(super.buildControlPanel());
            combinedBiasShiftedSourcePanel.add(new ShiftedSourceControlsCF(ssn));
            combinedBiasShiftedSourcePanel.add(new ShiftedSourceControlsCF(ssp));
            bgTabbedPane.addTab("Biases", combinedBiasShiftedSourcePanel);
            bgTabbedPane.addTab("Output MUX control", allMuxes.buildControlPanel());
            JPanel adcScannerLogPanel = new JPanel();
            adcScannerLogPanel.setLayout(new BoxLayout(adcScannerLogPanel, BoxLayout.Y_AXIS));
            bgTabbedPane.add("Analog output", adcScannerLogPanel);
            adcScannerLogPanel.add(new ParameterControlPanel(adc));
//            adcScannerLogPanel.add(new ParameterControlPanel(scanner));
            adcScannerLogPanel.add(new ParameterControlPanel(apsReadoutControl));

            JPanel moreConfig = new JPanel(new BorderLayout());

            JPanel extraPanel = extraOnchipConfigBits.makeControlPanel();
            extraPanel.setBorder(new TitledBorder("Extra on-chip bits"));
            moreConfig.add(extraPanel, BorderLayout.NORTH);
               
            JPanel diagPanel = extraBiasDiagBits.makeControlPanel();
            diagPanel.setBorder(new TitledBorder("Extra bias diagnose bits"));
            moreConfig.add(diagPanel, BorderLayout.WEST);

            JPanel portBitsPanel = new JPanel();
            portBitsPanel.setLayout(new BoxLayout(portBitsPanel, BoxLayout.Y_AXIS));
            for (PortBit p : portBits) {
                portBitsPanel.add(new JRadioButton(p.getAction()));
            }
            portBitsPanel.setBorder(new TitledBorder("Cypress FX2 port bits"));

            moreConfig.add(portBitsPanel, BorderLayout.CENTER);

//            final Action sendConfigAction = new AbstractAction("Send configuration") {
//
//                {
//                    putValue(Action.SHORT_DESCRIPTION, "Sends the complete configuration again");
//                }
//
//                // This method is called when the action is invoked
//                @Override
//                public void actionPerformed(ActionEvent evt) {
//                    try {
//                        sendConfiguration(SeeBetter20.SeeBetterConfig.this);
//                    } catch (HardwareInterfaceException ex) {
//                        log.warning(ex.toString());
//                    }
//                }
//            };



            bgTabbedPane.addTab("More config", moreConfig);

            bPanel.add(bgTabbedPane, BorderLayout.CENTER);
            // only select panel after all added

            try {
                bgTabbedPane.setSelectedIndex(getPrefs().getInt("SeeBetter20.bgTabbedPaneSelectedIndex", 0));
            } catch (IndexOutOfBoundsException e) {
                bgTabbedPane.setSelectedIndex(0);
            }
            // add listener to store last selected tab

            bgTabbedPane.addMouseListener(
                    new java.awt.event.MouseAdapter() {

                        @Override
                        public void mouseClicked(java.awt.event.MouseEvent evt) {
                            tabbedPaneMouseClicked(evt);
                        }
                    });
            setBatchEditOccurring(false);
            return bPanel;
        }

        private void tabbedPaneMouseClicked(java.awt.event.MouseEvent evt) {
            getPrefs().putInt("SeeBetter20.bgTabbedPaneSelectedIndex", bgTabbedPane.getSelectedIndex());
        }

        /** Formats the data sent to the microcontroller to load bias and other configuration to the chip (not FX2 or CPLD configuration). 
        <p>
         * Data is sent in bytes. Each byte is loaded into the shift register in big-endian bit order, starting with the msb and ending with the lsb.
         * Bytes are loaded starting with the first byte from formatConfigurationBytes (element 0). Therefore the last bit in the on-chip shift register (the one
         * that is furthest away from the bit input pin) should be in the msb of the first byte returned by formatConfigurationBytes.
         * @return byte array to be sent, which will be loaded into the chip starting with element 0 msb.
         * @param biasgen this SeeBetterConfig
         */
//        @Override
//        public byte[] formatConfigurationBytes(Biasgen biasgen) {
//            ByteBuffer bb = ByteBuffer.allocate(1000);
//
//            if (getPotArray() == null) {
//                return null; // array not yet contructed, we were called here by super()
//            }            // must return integral number of bytes and on-chip biasgen must be integral number of bytes, by method contract
////            ByteBuffer potbytes = ByteBuffer.allocate(300);
//
//
//            AddressedIPotArray ipots = (AddressedIPotArray) potArray;
//
//            byte[] bytes = new byte[potArray.getNumPots() * 8];
//            int byteIndex = 0;
//
//
//            Iterator i = ipots.getShiftRegisterIterator();
//            while (i.hasNext()) {
//                // for each bias starting with the first one (the one closest to the ** END ** of the shift register
//                // we get the binary representation in byte[] form and from MSB ro LSB stuff these values into the byte array
//                AddressedIPot iPot = (AddressedIPot) i.next();
//                byte[] thisBiasBytes = iPot.getBinaryRepresentation();
//                System.arraycopy(thisBiasBytes, 0, bytes, byteIndex, thisBiasBytes.length);
//                byteIndex += thisBiasBytes.length;
////                log.info("added bytes for "+iPot);
//            }
//            byte[] potbytes = new byte[byteIndex];
//            System.arraycopy(bytes, 0, potbytes, 0, byteIndex);
//
//
//
////        for (Pot p : getPotArray().getPots()) {
////                potbytes.put(p.getBinaryRepresentation());
////            }
////            potbytes.flip(); // written in order 
//
//            String configBitsBits = extraOnchipConfigBits.getBitString();
//            String muxBitsBits = allMuxes.getBitString(); // the first nibble is the imux in big endian order, bit3 of the imux is the very first bit.
//
//            byte[] muxAndConfigBytes = bitString2Bytes((configBitsBits + muxBitsBits)); // returns bytes padded at end
//
//            bb.put(muxAndConfigBytes); // loaded first to go to far end of shift register
//
//            // 256 value (8 bit) VDAC for amplifier reference
//            byte vdac = (byte) thermometerDAC.getBitValue(); //Byte.valueOf("9");
//            bb.put(vdac);   // VDAC needs 8 bits
//            bb.put(potbytes);
//
//            // the 4 shifted sources, each 2 bytes
//            for (ShiftedSourceBiasCF ss : ssBiases) {
//                bb.put(ss.getBinaryRepresentation());
//            }
//
//            // make buffer for all output bytes
//            byte[] allBytes = new byte[bb.position()];
//            bb.flip(); // flips to read them out in order of putting them in, i.e., get configBitBytes first
//            bb.get(allBytes); // we write these in vendor request
//
////            StringBuilder sb = new StringBuilder(allBytes.length + " bytes sent to FX2 to be loaded big endian into on-chip shift register for each byte in order \n");
////            for (byte b : allBytes) {
////                sb.append(String.format("%02x ", b));
////            }
////            log.info(sb.toString());
//            return allBytes; // configBytes may be padded with extra bits to make up a byte, board needs to know this to chop off these bits
//        }

        /** Controls the log intensity readout by wrapping the relevant bits */
        public class ApsReadoutControl implements Observer {

            private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);
            public final String EVENT_TESTPIXEL = "testpixelEnabled";
            
            public ApsReadoutControl() {
                rowSettle.addObserver(this);
                colSettle.addObserver(this);
                exposure.addObserver(this);
                resSettle.addObserver(this);
                framePeriod.addObserver(this);
                testpixel.addObserver(this);
            }
            
            private int getColCCwoB(){
                return(COL_CC+resSettle.get()+colSettle.get()+32*(ROW_CC_WO_B+rowSettle.get()));
            }
            
            private int getColCC(){
                return(COL_CC+resSettle.get()+colSettle.get()+32*(ROW_CC+rowSettle.get()));
            }
            
            private int getFrameCC(){
                return(exposure.get()*getColCCwoB()+64*getColCC());
            }

//            public void setColSettleTime(int timeUs) {
//                colSettle.set(timeUs * ADC_CLK_CYCLES_PER_US);
//            }
//
//            public void setRowSettleTime(int timeUs) {
//                rowSettle.set(timeUs * ADC_CLK_CYCLES_PER_US);
//            }
//            
//            public void setResSettleTime(int timeUs) {
//                colSettle.set(timeUs * ADC_CLK_CYCLES_PER_US);
//            }
//            
//            public void setFrameDelay(int timeUs){
//                framePeriod.set(timeUs * ADC_CLK_CYCLES_PER_US);
//            }
//            
//            public void setExposureDelay(int timeUs){
//                exposure.set(timeUs * ADC_CLK_CYCLES_PER_US);
//            }
            
            public void setColSettleCC(int cc) {
                colSettle.set(cc);
            }

            public void setRowSettleCC(int cc) {
                rowSettle.set(cc);
            }
            
            public void setResSettleCC(int cc) {
                resSettle.set(cc);
            }
            
            public void setFrameDelayCC(int cc){
                framePeriod.set(cc);
            }
            
            public void setExposureDelayCC(int cc){
                exposure.set(cc);
            }
            
            public boolean isTestpixelEnabled() {
                return SeeBetterConfig.this.testpixel.isSet();
            }

            public void setTestpixelEnabled(boolean testpixel) {
                SeeBetterConfig.this.testpixel.set(testpixel);
            }

//            public void setExposureTime(int timeUs) {
//                int desiredCC = timeUs * ADC_CLK_CYCLES_PER_US;
//                int actualCC = getColCCwoB();
//                if(desiredCC < actualCC){
//                    exposure.set(0);
//                } else {
//                    int diff = desiredCC-actualCC;
//                    exposure.set((int)diff/(actualCC));
//                }
//            }
            
//            public void setFramePeriodTime(int timeUs) {
//                int desiredCC = timeUs * ADC_CLK_CYCLES_PER_US;
//                int actualCC = getFrameCC();
//                if(desiredCC < actualCC){
//                    framePeriod.set(0);
//                } else {
//                    int diff = desiredCC-actualCC;
//                    framePeriod.set((int)diff/(actualCC));
//                }
//            }
            
//            public void setFrameRefreshFrequency(int hz){
//                setFramePeriodTime((int) 1000000/hz);
//            }
            

//            public int getColSettleTime() {
//                return colSettle.get() / ADC_CLK_CYCLES_PER_US;
//            }
//
//            public int getRowSettleTime() {
//                return rowSettle.get() / ADC_CLK_CYCLES_PER_US;
//            }
//            
//            public int getResSettleTime() {
//                return resSettle.get() / ADC_CLK_CYCLES_PER_US;
//            }
//            
//            public int getFrameDelay() {
//                return framePeriod.get() / ADC_CLK_CYCLES_PER_US;
//            }
//            
//            public int getExposureDelay() {
//                return exposure.get() / ADC_CLK_CYCLES_PER_US;
//            }
            
            public int getColSettleCC() {
                return colSettle.get();
            }

            public int getRowSettleCC() {
                return rowSettle.get();
            }
            
            public int getResSettleCC() {
                return resSettle.get();
            }
            
            public int getFrameDelayCC() {
                return framePeriod.get();
            }
            
            public int getExposureDelayCC() {
                return exposure.get();
            }
            
//            public int getExposureTime() {
//                return exposure.get()*getColCCwoB() / ADC_CLK_CYCLES_PER_US;
//            }
            
            
//            public int getFrameTime() {
//                return (framePeriod.get()+getFrameCC()) / ADC_CLK_CYCLES_PER_US;
//            }
            
//            public int getFrameRefreshFrequency() {
//                return (int)(1000000/getFrameTime());
//            }

            @Override
            public void update(Observable o, Object arg) {
                setChanged();
                notifyObservers(arg);
                if (o == testpixel) {
                    getPropertyChangeSupport().firePropertyChange(EVENT_TESTPIXEL, null, testpixel.isSet());
                }
            }

            /**
             * @return the propertyChangeSupport
             */
            public PropertyChangeSupport getPropertyChangeSupport() {
                return propertyChangeSupport;
            }
        }

        public class ADC extends Observable implements Observer {

            private final int ADCchannelshift = 5;
            private final short ADCconfig = (short) 0x160;   //normal power mode, single ended, channel 3, sequencer unused : (short) 0x908;
            int channel = getPrefs().getInt("ADC.channel", 3);
            public final String EVENT_ADC_ENABLED = "adcEnabled", EVENT_ADC_CHANNEL = "adcChannel";
            private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

            public ADC() {
                runAdc.addObserver(this);
            }

            public boolean isAdcEnabled() {
                return runAdc.isSet();
            }

            public void setAdcEnabled(boolean yes) {
                runAdc.set(yes);
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

            @Override
            public void update(Observable o, Object arg) {
                setChanged();
                notifyObservers(arg);
                if (o == runAdc) {
                    propertyChangeSupport.firePropertyChange(EVENT_ADC_ENABLED, null, runAdc.isSet());
                } // TODO
            }

            /**
             * @return the propertyChangeSupport
             */
            public PropertyChangeSupport getPropertyChangeSupport() {
                return propertyChangeSupport;
            }
        }

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
        public class ExtraOnChipConfigBits extends Observable implements HasPreference, Observer { // TODO fix for config bit of pullup

            final int TOTAL_NUM_BITS = 24;  // number of these bits on this chip, at end of biasgen shift register
            boolean value = false;
            OnchipConfigBit pullupX = new OnchipConfigBit(SeeBetter20.this, "useStaticPullupX", 0, "turn on static pullup for X addresses (columns)", false),
                    pullupY = new OnchipConfigBit(SeeBetter20.this, "useStaticPullupY", 1, "turn on static pullup for Y addresses (rows)", true);
            OnchipConfigBit[] bits = {pullupX, pullupY};

            public ExtraOnChipConfigBits() {
                hasPreferencesList.add(this);
                for (OnchipConfigBit b : bits) {
                    b.addObserver(this);
                }
            }

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
//                log.info(s.length() + " extra config bits with unused registers at left end =" + s);
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

            @Override
            public void update(Observable o, Object arg) {
                SeeBetterConfig.this.update(o, arg); // pass update up to biasgen
            }
        } // ExtraOnChipConfigBits
        
        public class ExtraBiasDiagBits extends Observable implements HasPreference, Observer { // TODO fix for config bit of pullup

            final int TOTAL_NUM_BITS = 7;  
            boolean value = false;
            OnchipConfigBit 
                    resetTestPixel = new OnchipConfigBit(SeeBetter20.this, "resetTestPixel", 4, "Reset the test pixel", true),
                    resetCalibCircuit = new OnchipConfigBit(SeeBetter20.this, "resetCalibCircuit", 5, "Reset the calibration circuit", true),
                    nTypeCalibCircuit = new OnchipConfigBit(SeeBetter20.this, "nTypeCalibCircuit", 7, "N type calibration circuit", true);
            OnchipConfigBit[] bits = { resetTestPixel, resetCalibCircuit, nTypeCalibCircuit};

            public ExtraBiasDiagBits() {
                hasPreferencesList.add(this);
                for (OnchipConfigBit b : bits) {
                    b.addObserver(this);
                }
            }

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
                for (int i = bits.length - 1; i >= 0; i--) {
                    s.append(bits[i].isSet() ? "1" : "0");
                }
                //add bias mux
                s.append(allMuxes.bmuxes[0].getBitString());
//                log.info(s.length() + " extra config bits with unused registers at left end =" + s);
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

            @Override
            public void update(Observable o, Object arg) {
                SeeBetterConfig.this.update(o, arg); // pass update up to biasgen
            }
        } // ExtraOnChipConfigBits

        /** the output multiplexors for on-chip diagnostic output */
        public class AllMuxes extends Observable implements Observer {

            OutputMux[] vmuxes = {new VoltageOutputMux(1), new VoltageOutputMux(2), new VoltageOutputMux(3)};
            OutputMux[] dmuxes = {new LogicMux(1), new LogicMux(2), new LogicMux(3), new LogicMux(4), new LogicMux(5)};
            OutputMux[] bmuxes = {new LogicMux(0)};
            ArrayList<OutputMux> muxes = new ArrayList();
            MuxControlPanel controlPanel = null;

            /** A MUX for selecting output on the on-chip configuration/biasgen shift register. */
            public class OutputMux extends Observable implements HasPreference, RemoteControlled {

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
                    return getClass().getSimpleName() + "." + name + ".selectedChannel";
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

            String getBitString() {
                int nBits = 0;
                StringBuilder s = new StringBuilder();
                for (OutputMux m : muxes) {
                    if(m.getName() != "BiasOutMux"){
                        s.append(m.getBitString());
                        nBits += m.nSrBits;
                    }
                }

                return s.toString();
            }

            AllMuxes() {
                
                muxes.addAll(Arrays.asList(bmuxes)); 
                muxes.addAll(Arrays.asList(dmuxes)); // 5 logic muxes, first in list since at end of chain - bits must be sent first, before any biasgen bits
                muxes.addAll(Arrays.asList(vmuxes)); // finally send the 3 voltage muxes
                
                for (OutputMux m : muxes) {
                    m.addObserver(this);
                }
                
                bmuxes[0].setName("BiasOutMux");
                
                bmuxes[0].put(0,"IFThrBn");
                bmuxes[0].put(1,"AEPuYBp");
                bmuxes[0].put(2,"AEPuXBp");
                bmuxes[0].put(3,"AERxEBn");
                bmuxes[0].put(4,"AEPdBn");
                bmuxes[0].put(5,"RefrBp");
                bmuxes[0].put(6,"PrSFBp");
                bmuxes[0].put(7,"PrBp");
                bmuxes[0].put(8,"PixInvBn");
                bmuxes[0].put(9,"LocalBufBn");
                bmuxes[0].put(10,"ApsROSFBn");
                bmuxes[0].put(11,"PrCasBnc");
                bmuxes[0].put(12,"ApsCasBnc");
                bmuxes[0].put(13,"OffBn");
                bmuxes[0].put(14,"OnBn");
                bmuxes[0].put(15,"DiffBn");

                dmuxes[0].setName("DigMux4");
                dmuxes[1].setName("DigMux3");
                dmuxes[2].setName("DigMux2");
                dmuxes[3].setName("DigMux1");
                dmuxes[4].setName("DigMux0");

                for (int i = 0; i < 5; i++) {
                    dmuxes[i].put(0, "nRxcolE");
                    dmuxes[i].put(1, "nAxcolE");
                    dmuxes[i].put(2, "nRY0");
                    dmuxes[i].put(3, "biasCalibSpikes");
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

                }

                dmuxes[0].put(14, "nResetRxcol");
                dmuxes[0].put(15, "nArowBottom");
                dmuxes[1].put(14, "AY1right");
                dmuxes[1].put(15, "nRY1right");
                dmuxes[2].put(14, "AY1right");
                dmuxes[2].put(15, "nRY1right");
                dmuxes[3].put(14, "FF2");
                dmuxes[3].put(15, "RCarb");
                dmuxes[4].put(14, "FF2");
                dmuxes[4].put(15, "RCarb");

                vmuxes[0].setName("AnaMux2");
                vmuxes[1].setName("AnaMux1");
                vmuxes[2].setName("AnaMux0");

                vmuxes[0].put(0, "vDiff_test");
                vmuxes[0].put(1, "photocurrent");
                vmuxes[0].put(2, "CalibVm");
                vmuxes[0].put(3, "-");
                vmuxes[0].put(4, "-");
                vmuxes[0].put(5, "- ");
                vmuxes[0].put(6, "colorColDiff");
                vmuxes[0].put(7, "colorVs");

                vmuxes[1].put(0, "pr_test");
                vmuxes[1].put(1, "prc_test");
                vmuxes[1].put(2, "pd_test");
                vmuxes[1].put(3, "-");
                vmuxes[1].put(4, "-");
                vmuxes[1].put(5, "-");
                vmuxes[1].put(6, "colorDiff");
                vmuxes[1].put(7, "colorTop");

                vmuxes[2].put(0, "apsOut_test");
                vmuxes[2].put(1, "apsGate_test");
                vmuxes[2].put(2, "-");
                vmuxes[2].put(3, "-");
                vmuxes[2].put(4, "-");
                vmuxes[2].put(5, "-");
                vmuxes[2].put(6, "colorVt");
                vmuxes[2].put(7, "colorSum");
                
            }

            /** Passes on notifies from MUX's
             * 
             * @param o ignored
             * @param arg passed on to Observers
             */
            @Override
            public void update(Observable o, Object arg) {
                setChanged();
                notifyObservers(arg);
            }

            public MuxControlPanel buildControlPanel() {
                return new MuxControlPanel();
            }

            /**
             * Control panel for cDVSTest10 diagnostic output configuration.
             * @author  tobi
             */
            public class MuxControlPanel extends javax.swing.JPanel {

                class OutputSelectionAction extends AbstractAction implements Observer {

                    OutputMux mux;
                    int channel;
                    JRadioButton button;

                    OutputSelectionAction(OutputMux m, int i) {
                        super(m.getChannelName(i));
                        mux = m;
                        channel = i;
                        m.addObserver(this);
                    }

                    void setButton(JRadioButton b) {
                        button = b;
                    }

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        mux.select(channel);
                        log.info("Selected " + mux);
                    }

                    @Override
                    public void update(Observable o, Object arg) {
                        if (channel == mux.selectedChannel) {
                            button.setSelected(true);
                        }
                    }
                }

                /** Creates new control panel for this MUX
                 * 
                 * @param chip the chip
                 */
                public MuxControlPanel() {
                    for (OutputMux m : muxes) {
                        JPanel p = new JPanel();
                        p.setAlignmentY(0);
                        p.setBorder(new TitledBorder(m.getName()));
                        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
                        ButtonGroup group = new ButtonGroup();
                        final Insets insets = new Insets(0, 0, 0, 0);
                        for (int i = 0; i < m.nInputs; i++) {

                            OutputSelectionAction action = new OutputSelectionAction(m, i);
                            JRadioButton b = new JRadioButton(action);
                            action.setButton(b); // needed to update button state
                            b.setSelected(i == m.selectedChannel);
                            b.setFont(b.getFont().deriveFont(10f));
                            b.setToolTipText(b.getText());
                            b.setMargin(insets);
//                b.setMinimumSize(new Dimension(30, 14));
                            group.add(b);
                            p.add(b);
                        }
                        add(p);
                    }
                }
            }
        } // AllMuxes
    } // SeeBetterConfig

    /**
     * @return the displayLogIntensity
     */
    public boolean isDisplayIntensity() {
        return displayIntensity;
    }
    
    /**
     * @return the displayLogIntensity
     */
    public void takeSnapshot() {
        snapshot = true;
        config.adc.setAdcEnabled(true);
    }

    /**
     * @param displayLogIntensity the displayLogIntensity to set
     */
    public void setDisplayIntensity(boolean displayIntensity) {
        this.displayIntensity = displayIntensity;
        getPrefs().putBoolean("displayIntensity", displayIntensity);
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

    /**
     * Displays data from SeeBetter test chip SeeBetter10/11.
     * @author Tobi
     */
    public class SeeBetter20DisplayMethod extends DVSWithIntensityDisplayMethod {

        private TextRenderer renderer = null;

        public SeeBetter20DisplayMethod(SeeBetter20 chip) {
            super(chip.getCanvas());
        }

        @Override
        public void display(GLAutoDrawable drawable) {
            if (renderer == null) {
                renderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 18), true, true);
                renderer.setColor(1, .2f, .2f, 0.4f);
            }
            super.display(drawable);
            GL gl = drawable.getGL();
            gl.glLineWidth(2f);
            gl.glColor3f(1, 1, 1);
            // draw boxes around arrays

            rect(gl, 0, 0, 128, 64, "apsDVS"); // big DVS + log
//            rect(gl, 128, 0, 2, 64); /// whole chip + extra to right
            // show scanned pixel if we are not continuously scanning
//            if (!config.scanContinuouslyEnabled.isSet()) {
//                rect(gl, 2 * config.scanX.get(), 2 * config.framePeriod.get(), 2, 2, null); // 2* because pixel pitch is 2 pixels for bDVS array
//            }
        }

        private void rect(GL gl, float x, float y, float w, float h, String txt) {
            gl.glPushMatrix();
            gl.glTranslatef(-.5f, -.5f, 0);
            gl.glLineWidth(2f);
            gl.glColor3f(1, 1, 1);
            gl.glBegin(GL.GL_LINE_LOOP);
            gl.glVertex2f(x, y);
            gl.glVertex2f(x + w, y);
            gl.glVertex2f(x + w, y + h);
            gl.glVertex2f(x, y + h);
            gl.glEnd();
            // label arrays
            if (txt != null) {
                renderer.begin3DRendering();
                renderer.draw3D(txt, x, y, 0, .4f); // x,y,z, scale factor
                renderer.end3DRendering();
            }
            gl.glPopMatrix();
        }
    }

    public IntensityFrameData getFrameData() {
        return frameEventPacket.getFrameData();
    }

    /** Extends EventPacket to add the log intensity frame data */
    public class FrameEventPacket extends EventPacket {

        public FrameEventPacket(Class eventClass) {
            super(eventClass);
        }

        /**
         * @return the frameData
         */
        public IntensityFrameData getFrameData() {
            return frameData;
        }

        @Override
        public boolean isEmpty() {
            if (!frameData.isNewData() && super.isEmpty()) {
                return true;
            }
            return false;
        }

        @Override
        public String toString() {
            return "FrameEventPacket{" + "frameData=" + frameData + " " + super.toString() + '}';
        }
    }

    /**
     * Renders complex data from SeeBetter chip.
     *
     * @author tobi
     */
    public class SeeBetter20Renderer extends RetinaRenderer {

        private SeeBetter20 cDVSChip = null;
//        private final float[] redder = {1, 0, 0}, bluer = {0, 0, 1}, greener={0,1,0}, brighter = {1, 1, 1}, darker = {-1, -1, -1};
        private final float[] brighter = {1, 0, 0}, darker = {0, 1, 0};
        private int sizeX = 1;
        private LowpassFilter2d agcFilter = new LowpassFilter2d();  // 2 lp values are min and max log intensities from each frame
        private boolean agcEnabled;
        /** PropertyChange */
        public static final String AGC_VALUES = "AGCValuesChanged";
        /** PropertyChange when value is changed */
        public static final String APS_INTENSITY_GAIN = "apsIntensityGain", APS_INTENSITY_OFFSET = "apsIntensityOffset";
        /** Control scaling and offset of display of log intensity values. */
        int apsIntensityGain, apsIntensityOffset;

        public SeeBetter20Renderer(SeeBetter20 chip) {
            super(chip);
            cDVSChip = chip;
            agcEnabled = chip.getPrefs().getBoolean("agcEnabled", false);
            setAGCTauMs(chip.getPrefs().getFloat("agcTauMs", 1000));
            apsIntensityGain = chip.getPrefs().getInt("apsIntensityGain", 1);
            apsIntensityOffset = chip.getPrefs().getInt("apsIntensityOffset", 0);
        }

        /** Overridden to make gray buffer special for bDVS array */
        @Override
        protected void resetPixmapGrayLevel(float value) {
            checkPixmapAllocation();
            final int n = 3 * chip.getNumPixels();
            boolean madebuffer = false;
            if (grayBuffer == null || grayBuffer.capacity() != n) {
                grayBuffer = FloatBuffer.allocate(n); // BufferUtil.newFloatBuffer(n);
                madebuffer = true;
            }
            if (madebuffer || value != grayValue) {
                grayBuffer.rewind();
                for (int y = 0; y < sizeY; y++) {
                    for (int x = 0; x < sizeX; x++) {
                        if(displayLogIntensityChangeEvents){
                            grayBuffer.put(0);
                            grayBuffer.put(0);
                            grayBuffer.put(0);
                        } else {
                            grayBuffer.put(grayValue);
                            grayBuffer.put(grayValue);
                            grayBuffer.put(grayValue);
                        }
                    }
                }
                grayBuffer.rewind();
            }
            System.arraycopy(grayBuffer.array(), 0, pixmap.array(), 0, n);
            pixmap.rewind();
            pixmap.limit(n);
//        pixmapGrayValue = grayValue;
        }
  
        @Override
        public synchronized void render(EventPacket packet) {

            checkPixmapAllocation();
            resetSelectedPixelEventCount(); // TODO fix locating pixel with xsel ysel
            // rendering is a hack to use the standard pixmap to duplicate pixels on left side (where 32x32 cDVS array lives) with superimposed Brighter, Darker, Redder, Bluer, and log intensity values,
            // and to show DVS test pixel events on right side (where the 64x64 total consisting of 4x 32x32 types of pixels live)

            if (packet == null) {
                return;
            }
            this.packet = packet;
            if (packet.getEventClass() != PolarityADCSampleEvent.class) {
                log.warning("wrong input event class, got " + packet.getEventClass() + " but we need to have " + PolarityADCSampleEvent.class);
                return;
            }
            float[] pm = getPixmapArray();
            sizeX = chip.getSizeX();
            //log.info("pm : "+pm.length+", sizeX : "+sizeX);
            if (!accumulateEnabled) {
                resetFrame(.5f);
            }
            //String eventData = "NULL";
            boolean putADCData=displayIntensity && !getAeViewer().isPaused(); // don't keep reputting the ADC data into buffer when paused and rendering packet over and over again
            String event = "";
            try {
                step = 1f / (colorScale);
                for (Object obj : packet) {
                    PolarityADCSampleEvent e = (PolarityADCSampleEvent) obj;
                    //eventData = "address:"+Integer.toBinaryString(e.address)+"( x: "+Integer.toString(e.x)+", y: "+Integer.toString(e.y)+"), data "+Integer.toBinaryString(e.adcSample)+" ("+Integer.toString(e.adcSample)+")";
                    //System.out.println("Event: "+eventData);
                    if (putADCData && e.isAdcSample()) { // hack to detect ADC sample events
                        // ADC 'event'
                        frameData.putEvent(e);
                        //log.info("put "+e.toString());
                    } else if (displayLogIntensityChangeEvents && !e.isAdcSample()) {   
                        // real AER event
                        int type = e.getType();
                        if (xsel >= 0 && ysel >= 0) { // find correct mouse pixel interpretation to make sounds for large pixels
                            int xs = xsel, ys = ysel;
                            xs >>= 1;
                            ys >>= 1;

                            if (e.x == xs && e.y == ys) {
                                playSpike(type);
                            }
                        }
                        int x = e.x, y = e.y;
                        switch (e.polarity) {
                            case On:
                                changeCDVSPixel(x, y, pm, brighter, step);
                                break;
                            case Off:
                                changeCDVSPixel(x, y, pm, darker, step);
                                break;
                        }
                    }
                }
                if (displayIntensity) {
                    int minADC = Integer.MAX_VALUE;
                    int maxADC = Integer.MIN_VALUE;
                    for (int y = 0; y < EntirePixelArray.height; y++) {
                        for (int x = 0; x < EntirePixelArray.width; x++) {
                            //event = "ADC x "+x+", y "+y;
                            int count = frameData.get(x, y);
                            if (agcEnabled) {
                                if (count < minADC) {
                                    minADC = count;
                                } else if (count > maxADC) {
                                    maxADC = count;
                                }
                            }
                            float v = adc01normalized(count);
                            float[] vv = {v, v, v};
                            changeCDVSPixel(x, y, pm, vv, 1);
                        }
                    }
                    if (agcEnabled && (minADC > 0 && maxADC > 0)) { // don't adapt to first frame which is all zeros
                        Float filter2d = agcFilter.filter2d(minADC, maxADC, frameData.getTimestamp());
//                        System.out.println("agc minmax=" + filter2d + " minADC=" + minADC + " maxADC=" + maxADC);
                        getSupport().firePropertyChange(AGC_VALUES, null, filter2d); // inform listeners (GUI) of new AGC min/max filterd log intensity values
                    }

                }
                autoScaleFrame(pm);
            } catch (IndexOutOfBoundsException e) {
                log.warning(e.toString() + ": ChipRenderer.render(), some event out of bounds for this chip type? Event: "+event);//log.warning(e.toString() + ": ChipRenderer.render(), some event out of bounds for this chip type? Event: "+eventData);
            }
            pixmap.rewind();
        }

        /** Changes scanned pixel value according to scan-out order
         * 
         * @param ind the pixel to change, which marches from LL corner to right, then to next row up and so on. Physically on chip this is actually from UL corner.
         * @param f the pixmap RGB array
         * @param c the colors
         * @param step the step size which multiplies each color component
         */
        private void changeCDVSPixel(int ind, float[] f, float[] c, float step) {
            float r = c[0] * step, g = c[1] * step, b = c[2] * step;
            f[ind] += r;
            f[ind + 1] += g;
            f[ind + 2] += b;

            ind += 3;
            f[ind] += r;
            f[ind + 1] += g;
            f[ind + 2] += b;

            ind += sizeX * 3;
            f[ind] += r;
            f[ind + 1] += g;
            f[ind + 2] += b;

            ind -= 3;
            f[ind] += r;
            f[ind + 1] += g;
            f[ind + 2] += b;
        }

        /** Changes all 4 pixmap locations for each large pixel affected by this event. 
         * x,y refer to space of large pixels each of which is 2x2 block of pixmap pixels
         * 
         */
        private void changeCDVSPixel(int x, int y, float[] f, float[] c, float step) {
            int ind = 3 * (2 * x + 2 * y * sizeX);
            changeCDVSPixel(ind, f, c, step);
        }

        public void setDisplayLogIntensityChangeEvents(boolean displayIntensityChangeEvents) {
            cDVSChip.setDisplayLogIntensityChangeEvents(displayIntensityChangeEvents);
        }

        public void setDisplayIntensity(boolean displayIntensity) {
            cDVSChip.setDisplayIntensity(displayIntensity);
        }

        public boolean isDisplayLogIntensityChangeEvents() {
            return cDVSChip.isDisplayLogIntensityChangeEvents();
        }

        public boolean isDisplayIntensity() {
            return cDVSChip.isDisplayIntensity();
        }

        private float adc01normalized(int count) {
            float v;
            if (!agcEnabled) {
                v = (float) (apsIntensityGain * (count - apsIntensityOffset)) / (float) MAX_ADC;
            } else {
                Float filter2d = agcFilter.getValue2d();
                float offset = filter2d.x;
                float range = (filter2d.y - filter2d.x);
                v = ((count - offset)) / range;
//                System.out.println("offset="+offset+" range="+range+" count="+count+" v="+v);
            }
            if (v < 0) {
                v = 0;
            } else if (v > 1) {
                v = 1;
            }
            return v;
        }

        public float getAGCTauMs() {
            return agcFilter.getTauMs();
        }

        public void setAGCTauMs(float tauMs) {
            if (tauMs < 10) {
                tauMs = 10;
            }
            agcFilter.setTauMs(tauMs);
            chip.getPrefs().putFloat("agcTauMs", tauMs);
        }

        /**
         * @return the agcEnabled
         */
        public boolean isAgcEnabled() {
            return agcEnabled;
        }

        /**
         * @param agcEnabled the agcEnabled to set
         */
        public void setAgcEnabled(boolean agcEnabled) {
            this.agcEnabled = agcEnabled;
            chip.getPrefs().putBoolean("agcEnabled", agcEnabled);
        }

        void applyAGCValues() {
            Float f = agcFilter.getValue2d();
            setApsIntensityOffset(agcOffset());
            setApsIntensityGain(agcGain());
        }

        private int agcOffset() {
            return (int) agcFilter.getValue2d().x;
        }

        private int agcGain() {
            Float f = agcFilter.getValue2d();
            float diff = f.y - f.x;
            if (diff < 1) {
                return 1;
            }
            int gain = (int) (SeeBetter20.MAX_ADC / (f.y - f.x));
            return gain;
        }

        /**
         * Value from 1 to MAX_ADC. Gain of 1, offset of 0 turns full scale ADC to 1. Gain of MAX_ADC makes a single count go full scale.
         * @return the apsIntensityGain
         */
        public int getApsIntensityGain() {
            return apsIntensityGain;
        }

        /**
         * Value from 1 to MAX_ADC. Gain of 1, offset of 0 turns full scale ADC to 1.
         * Gain of MAX_ADC makes a single count go full scale.
         * @param apsIntensityGain the apsIntensityGain to set
         */
        public void setApsIntensityGain(int apsIntensityGain) {
            int old = this.apsIntensityGain;
            if (apsIntensityGain < 1) {
                apsIntensityGain = 1;
            } else if (apsIntensityGain > MAX_ADC) {
                apsIntensityGain = MAX_ADC;
            }
            this.apsIntensityGain = apsIntensityGain;
            chip.getPrefs().putInt("apsIntensityGain", apsIntensityGain);
            if (chip.getAeViewer() != null) {
                chip.getAeViewer().interruptViewloop();
            }
            getSupport().firePropertyChange(APS_INTENSITY_GAIN, old, apsIntensityGain);
        }

        /**
         * Value subtracted from ADC count before gain multiplication. Ranges from 0 to MAX_ADC.
         * @return the apsIntensityOffset
         */
        public int getApsIntensityOffset() {
            return apsIntensityOffset;
        }

        /**
         * Sets value subtracted from ADC count before gain multiplication. Clamped between 0 to MAX_ADC.
         * @param apsIntensityOffset the apsIntensityOffset to set
         */
        public void setApsIntensityOffset(int apsIntensityOffset) {
            int old = this.apsIntensityOffset;
            if (apsIntensityOffset < 0) {
                apsIntensityOffset = 0;
            } else if (apsIntensityOffset > MAX_ADC) {
                apsIntensityOffset = MAX_ADC;
            }
            this.apsIntensityOffset = apsIntensityOffset;
            chip.getPrefs().putInt("apsIntensityOffset", apsIntensityOffset);
            if (chip.getAeViewer() != null) {
                chip.getAeViewer().interruptViewloop();
            }
            getSupport().firePropertyChange(APS_INTENSITY_OFFSET, old, apsIntensityOffset);
        }
    }

    /**
     * 
     * Holds the frame of log intensity values to be display for a chip with log intensity readout.
     * Applies calibration values to get() the values and supplies put() and resetWriteCounter() to put the values.
     * 
     * @author Tobi
     */
    public static enum Read{A, B, DIFF};
    
    public class IntensityFrameData implements HasPreference {

        /** The scanner is 64wide by 32 high  */
        public final int WIDTH = EntirePixelArray.width, HEIGHT = EntirePixelArray.height; // width is BDVS pixels not scanner registers
        private final int NUMSAMPLES = WIDTH * HEIGHT;
        private int timestamp = 0; // timestamp of starting sample
        private int[] data = new int[NUMSAMPLES];
        private int[] aData, bData;
        /** Readers should access the current reading buffer. */
        private int writeCounterA = 0;
         private int writeCounterB = 0;
        private int[] calibData1 = new int[NUMSAMPLES];
        private int[] calibData2 = new int[NUMSAMPLES];
        private float[] gain = new float[NUMSAMPLES];
        private int[] offset = new int[NUMSAMPLES];
        private boolean useOffChipCalibration = getPrefs().getBoolean("useOffChipCalibration", false);
        private boolean invertADCvalues = getPrefs().getBoolean("invertADCvalues", true); // true by default for log output which goes down with increasing intensity
        private boolean twoPointCalibration = getPrefs().getBoolean("twoPointCalibration", false);
        private int lasttimestamp=-1;
        private Read displayRead = Read.DIFF;
        
        public IntensityFrameData() {
            aData = new int[NUMSAMPLES];
            bData = new int[NUMSAMPLES];
            Arrays.fill(aData, 0);
            Arrays.fill(bData, 0);
            loadPreference();
        }
        
        private int index(int x, int y){
            final int idx = y + HEIGHT * x; 
            return idx;
        }

        /** Gets the sample at a given pixel address (not scanner address) 
         * 
         * @param x pixel x from left side of array
         * @param y pixel y from top of array on chip
         * @return  value from ADC
         */
        private int displayData;
        
        public int get(int x, int y) {
            final int idx = index(x,y); // values are written by row for each column (row parallel readout in this chip, with columns addressed one by one)
            switch (displayRead) {
                case A:
                    displayData = aData[idx];
                    break;
                case B:
                    displayData = bData[idx];
                    break;
                case DIFF:
                default:
                    displayData = data[idx];
                    break;
            }
            if (invertADCvalues) {
                if (useOffChipCalibration) {
                    return MAX_ADC - (int) (gain[idx] * (displayData - offset[idx]));
                }
                return MAX_ADC - displayData;
            } else {
                if (useOffChipCalibration) {
                    return ((int) gain[idx] * (displayData - offset[idx]));
                }
                return displayData;
            }
        }

        private void putEvent(PolarityADCSampleEvent e) {
            if(!e.isAdcSample() || e.timestamp==lasttimestamp) return;
            if(e.startOfFrame) {
                resetWriteCounter();
                setTimestamp(e.timestamp);
                putNextSampleValue(e.adcSample, e.isB, index(e.x, e.y));
//                putNextSampleValue(e.adcSample, e.isB);
            }else{
                putNextSampleValue(e.adcSample, e.isB, index(e.x, e.y));
//                putNextSampleValue(e.adcSample, e.isB);
            }
            lasttimestamp=e.timestamp; // so we don't put the same sample over and over again
        }
        
        /** Put a value to the next writing position and increments the writingCounter. 
         * Writes wrap around to the start position. 
         * @param val the sample
         */
        private void putNextSampleValue(int val, boolean isB) {
            if(isB){
                if (writeCounterB >= bData.length) {
    //            log.info("buffer overflowed - missing start frame bit?");
                    return;
                }
                bData[writeCounterB] = val;
                data[writeCounterB] = aData[writeCounterB]-bData[writeCounterB];
                writeCounterB++;
            } else {
                if (writeCounterA >= aData.length) {
    //            log.info("buffer overflowed - missing start frame bit?");
                    return;
                }
                aData[writeCounterA++] = val;
            }
        }
        
        private void putNextSampleValue(int val, boolean isB, int index) {
            if(isB){
                if (index >= bData.length) {
    //            log.info("buffer overflowed - missing start frame bit?");
                    return;
                }
                bData[index] = val;
                data[index] = aData[index]-bData[index];
            } else {
                if (index >= aData.length) {
    //            log.info("buffer overflowed - missing start frame bit?");
                    return;
                }
                aData[index] = val;
            }
        }

        /**
         * @return the timestamp
         */
        public int getTimestamp() {
            return timestamp;
        }

        /**
         * Sets the buffer timestamp. 
         * @param timestamp the timestamp to set
         */
        public void setTimestamp(int timestamp) {
            this.timestamp = timestamp;
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
            getPrefs().putBoolean("useOffChipCalibration", useOffChipCalibration);
        }

        public void calculateCalibration() {
            if (calibData1 == null) {
                calibData1 = new int[NUMSAMPLES];
            }
            if (calibData2 == null) {
                calibData2 = new int[NUMSAMPLES];
            }
            if (twoPointCalibration) {
                int mean1 = getMean(calibData1);
                int mean2 = getMean(calibData2);
                for (int i = 0; i < NUMSAMPLES; i++) {
                    gain[i] = ((float) (mean2 - mean1)) / ((float) (calibData2[i] - calibData1[i]));
                    offset[i] = (calibData1[i] - (int) (mean1 / gain[i]));
                }
            } else {
                for (int i = 0; i < NUMSAMPLES; i++) {
                    gain[i] = 1;
                }
                subtractMean(calibData1, offset);
            }
        }

        /**
         * uses the current writing buffer as calibration data and subtracts the mean
         */
        public void setCalibData1() {
            System.arraycopy(data, 0, calibData1, 0, NUMSAMPLES);
            calculateCalibration();
            storePreference();
        }

        public void setCalibData2() {
            System.arraycopy(data, 0, calibData2, 0, NUMSAMPLES);
            calculateCalibration();
            storePreference();
            //substractMean();
        }

        private int getMean(int[] dataIn) {
            int mean = 0;
            for (int i = 0; i < dataIn.length; i++) {
                mean += dataIn[i];
            }
            mean = mean / dataIn.length;
            return mean;
        }

        private void subtractMean(int[] dataIn, int[] dataOut) {
            int mean = getMean(dataIn);

            for (int i = 0; i < dataOut.length; i++) {
                dataOut[i] = dataIn[i] - mean;
            }
        }
        
        public void setDisplayRead(Read displayRead){
            this.displayRead = displayRead;
        }
        
        public Read getDisplayRead(){
            return displayRead;
        }

        /**
         * @return the invertADCvalues
         */
        public boolean isInvertADCvalues() {
            return invertADCvalues;
        }

        /**
         * @param invertADCvalues the invertADCvalues to set
         */
        public void setInvertADCvalues(boolean invertADCvalues) {
            this.invertADCvalues = invertADCvalues;
            getPrefs().putBoolean("invertADCvalues", invertADCvalues);
        }

        /**
         * @return the twoPointCalibration
         */
        public boolean isTwoPointCalibration() {
            return twoPointCalibration;
        }

        /**
         * @param twoPointCalibration the twoPointCalibration to set
         */
        public void setTwoPointCalibration(boolean twoPointCalibration) {
            this.twoPointCalibration = twoPointCalibration;
            getPrefs().putBoolean("twoPointCalibration", twoPointCalibration);
        }

        public boolean isNewData() {
            return true; // dataWrittenSinceLastSwap; // TODO not working yet
        }

        @Override
        public String toString() {
            return "IntensityFrameData{" + "WIDTH=" + WIDTH + ", HEIGHT=" + HEIGHT + ", NUMSAMPLES=" + NUMSAMPLES + ", timestamp=" + timestamp + ", writeCounter=" + writeCounterA + '}';
        }

        public void resetWriteCounter() {
            writeCounterA = 0;
            writeCounterB = 0;
        }
        final String CALIB1_KEY = "IntensityFrameData.calibData1", CALIB2_KEY = "IntensityFrameData.calibData2";

        private void putArray(int[] array, String key) {
            if (array == null || key == null) {
                log.warning("null array or key");
                return;
            }
            try {
                // Serialize to a byte array
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutput out = new ObjectOutputStream(bos);
                out.writeObject(array);
                out.close();

                // Get the bytes of the serialized object
                byte[] buf = bos.toByteArray();
                getPrefs().putByteArray(key, buf);
            } catch (Exception e) {
                log.warning(e.toString());
            }

        }

        private int[] getArray(String key) {
            int[] ret = null;
            try {
                byte[] bytes = getPrefs().getByteArray(key, null);
                if (bytes != null) {
                    ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
                    ret = (int[]) in.readObject();
                    in.close();
                }
            } catch (Exception e) {
                log.warning(e.toString());
            }
            return ret;
        }

        @Override
        public void loadPreference() {
            calibData1 = getArray(CALIB1_KEY);
            calibData2 = getArray(CALIB2_KEY);
            calculateCalibration();
        }

        @Override
        public void storePreference() {
            putArray(calibData1, CALIB1_KEY);
            putArray(calibData2, CALIB2_KEY);
        }


    }
}
