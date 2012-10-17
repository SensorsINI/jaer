/*
created 26 Oct 2008 for new cDVSTest chip
 * adapted apr 2011 for cDVStest30 chip by tobi
 * adapted 25 oct 2011 for SeeBetter10/11 chips by tobi
 *
 */
package eu.seebetter.ini.chips.sbret10;

import ch.unizh.ini.jaer.chip.retina.*;
import com.sun.opengl.util.j2d.TextRenderer;
import eu.seebetter.ini.chips.*;
import eu.seebetter.ini.chips.config.*;
import eu.seebetter.ini.chips.sbret10.ApsDvsEvent.ReadoutType;
import eu.seebetter.ini.chips.sbret10.SBret10.SBret10Config.*;
import eu.seebetter.ini.chips.seebetter20.SeeBetter20;
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
import java.awt.Font;
import java.awt.Insets;
import java.awt.Rectangle;
import java.beans.PropertyChangeSupport;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.nio.FloatBuffer;
import java.text.ParseException;
import java.util.*;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.border.TitledBorder;
import net.sf.jaer.Description;
import net.sf.jaer.biasgen.Pot.Sex;
import net.sf.jaer.biasgen.Pot.Type;
import net.sf.jaer.biasgen.coarsefine.AddressedIPotCF;
import net.sf.jaer.biasgen.coarsefine.ShiftedSourceBiasCF;
import net.sf.jaer.biasgen.coarsefine.ShiftedSourceControlsCF;
import net.sf.jaer.event.PolarityEvent.Polarity;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.RetinaRenderer;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2;
import net.sf.jaer.util.ParameterControlPanel;
import net.sf.jaer.util.filter.LowpassFilter2d;
import net.sf.jaer.util.jama.Matrix;

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
@Description("SBret version 1.0")
public class SBret10 extends AETemporalConstastRetina {

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
    public static final PixelArray EntirePixelArray = new PixelArray(1, 0, 0, 240, 180);
    // following define bit masks for various hardware data types. 
    // The hardware interface translateEvents method packs the raw device data into 32 bit 'addresses' and timestamps.
    // timestamps are unwrapped and timestamp resets are handled in translateEvents. Addresses are filled with either AE or ADC data.
    // AEs are filled in according the XMASK, YMASK, XSHIFT, YSHIFT below.
    /**
     * bit masks/shifts for cDVS  AE data
     */
    public static final int POLMASK = 1,
            XSHIFT = Integer.bitCount(POLMASK),
            XMASK = 255 << XSHIFT, // 8 bits
            YSHIFT = 16, // so that y addresses don't overlap ADC bits and cause fake ADC events Integer.bitCount(POLMASK | XMASK),
            YMASK = 255 << YSHIFT; // 6 bits

    /*
     * data type fields
     */
    /** data type is either timestamp or data (AE address or ADC reading) */
    public static final int DATA_TYPE_MASK = 0xc000, DATA_TYPE_ADDRESS = 0x0000, DATA_TYPE_TIMESTAMP = 0x4000, DATA_TYPE_WRAP = 0x8000, DATA_TYPE_TIMESTAMP_RESET = 0xd000;
    /** Address-type refers to data if is it an "address". This data is either an AE address or ADC reading.*/
    public static final int ADDRESS_TYPE_MASK = 0x2000, EVENT_ADDRESS_MASK = POLMASK | XMASK | YMASK, ADDRESS_TYPE_EVENT = 0x0000, ADDRESS_TYPE_ADC = 0x2000;
    /** For ADC data, the data is defined by the ADC channel and whether it is the first ADC value from the scanner. */
    public static final int ADC_TYPE_MASK = 0x1000, ADC_DATA_MASK = 0x3ff, ADC_START_BIT = 0x1000, ADC_READCYCLE_MASK = 0x0C00; 
    public static final int MAX_ADC = (int) ((1 << 10) - 1);
    private SBret10DisplayMethod sbretDisplayMethod = null;
    private boolean displayIntensity;
    private int exposureB;
    private int exposureC;
    private int frameTime;
    private boolean displayLogIntensityChangeEvents;
    private boolean ignoreReadout;
    private boolean snapshot = false;
    private boolean resetOnReadout = false;
    SBret10DisplayControlPanel displayControlPanel = null;
    private IntensityFrameData frameData = new IntensityFrameData();
    private SBret10Config config;
    JFrame controlFrame = null;

    /** Creates a new instance of cDVSTest20.  */
    public SBret10() {
        setName("SBret10");
        setEventClass(ApsDvsEvent.class);
        setSizeX(EntirePixelArray.width*EntirePixelArray.pitch);
        setSizeY(EntirePixelArray.height*EntirePixelArray.pitch);
        setNumCellTypes(3); // two are polarity and last is intensity
        setPixelHeightUm(18.5f);
        setPixelWidthUm(18.5f);

        setEventExtractor(new SBret10Extractor(this));

        setBiasgen(config = new SBret10.SBret10Config(this));

        displayIntensity = getPrefs().getBoolean("displayIntensity", true);
        displayLogIntensityChangeEvents = getPrefs().getBoolean("displayLogIntensityChangeEvents", true);

        setRenderer((new SBret10Renderer(this)));

        sbretDisplayMethod = new SBret10DisplayMethod(this);
        getCanvas().addDisplayMethod(sbretDisplayMethod);
        getCanvas().setDisplayMethod(sbretDisplayMethod);

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
//            AEViewer viewer = getAeViewer(); // must do lazy install here because viewer hasn't been registered with this chip at this point
            //JPanel imagePanel = viewer.getImagePanel();
            controlFrame = new JFrame("SBret10 display controls");
            JPanel imagePanel = new JPanel();
            imagePanel.add((displayControlPanel = new eu.seebetter.ini.chips.sbret10.SBret10DisplayControlPanel(this)), BorderLayout.SOUTH);
            imagePanel.revalidate();
            controlFrame.getContentPane().add(imagePanel);
            controlFrame.pack();
            controlFrame.setVisible(true);
        } catch (Exception e) {
            log.warning("could not register control panel: " + e);
        }
    }

    private void unregisterControlPanel() {
        try {
            displayControlPanel=null;
            if(controlFrame!=null) controlFrame.dispose();
//            AEViewer viewer = getAeViewer(); // must do lazy install here because viewer hasn't been registered with this chip at this point
//            JPanel imagePanel = viewer.getImagePanel();
//            imagePanel.remove(displayControlPanel);
//            imagePanel.revalidate();
        } catch (Exception e) {
            log.warning("could not unregister control panel: " + e);
        }
    }

    /** Creates a new instance of cDVSTest10
     * @param hardwareInterface an existing hardware interface. This constructor is preferred. It makes a new cDVSTest10Biasgen object to talk to the on-chip biasgen.
     */
    public SBret10(HardwareInterface hardwareInterface) {
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
    public class SBret10Extractor extends RetinaExtractor {

        private int firstFrameTs = 0;
        private short[] countX;
        private short[] countY;
        private int pixCnt=0; // TODO debug
        boolean ignore = false;
        
        public SBret10Extractor(SBret10 chip) {
            super(chip);
            resetCounters();
        }

        
        private void resetCounters(){
            int numReadoutTypes = 3;
            if(countX == null || countY == null){
                countX = new short[numReadoutTypes];
                countY = new short[numReadoutTypes];
            }
            Arrays.fill(countX, 0, numReadoutTypes, (short)0);
            Arrays.fill(countY, 0, numReadoutTypes, (short)0);
        }
        
        private void lastADCevent(){
            if (resetOnReadout){
                config.nChipReset.set(true);
            }
            ignore = false;
        }
        
        /** extracts the meaning of the raw events.
         *@param in the raw events, can be null
         *@return out the processed events. these are partially processed in-place. empty packet is returned if null is supplied as in.
         */
        @Override
        synchronized public EventPacket extractPacket(AEPacketRaw in) {
            if (out == null) {
                out = new ApsDvsEventPacket(chip.getEventClass());
            } else {
                out.clear();
            }
            if (in == null) {
                return out;
            }
            int n = in.getNumEvents(); //addresses.length;

            int[] datas = in.getAddresses();
            int[] timestamps = in.getTimestamps();
            OutputEventIterator outItr = out.outputIterator();

            // at this point the raw data from the USB IN packet has already been digested to extract timestamps, including timestamp wrap events and timestamp resets.
            // The datas array holds the data, which consists of a mixture of AEs and ADC values.
            // Here we extract the datas and leave the timestamps alone.
            
            for (int i = 0; i < n; i++) {  // TODO implement skipBy
                int data = datas[i];

                if ((data & ADDRESS_TYPE_MASK) == ADDRESS_TYPE_EVENT) {
                    //DVS event
                    if(!ignore){
                        ApsDvsEvent e = (ApsDvsEvent) outItr.nextOutput();
                        e.adcSample = -1; // TODO hack to mark as not an ADC sample
                        e.startOfFrame = false;
                        e.special = false;
                        e.address = data;
                        e.timestamp = (timestamps[i]);
                        e.polarity = (data & 1) == 1 ? ApsDvsEvent.Polarity.On : ApsDvsEvent.Polarity.Off;
                        e.x = (short) (chip.getSizeX()-1-((data & XMASK) >>> XSHIFT));
                        e.y = (short) ((data & YMASK) >>> YSHIFT); 
                        //System.out.println(data);
                    } 
                } else if ((data & ADDRESS_TYPE_MASK) == ADDRESS_TYPE_ADC) {
                    //APS event
                    ApsDvsEvent e = (ApsDvsEvent) outItr.nextOutput();
                    e.adcSample = data & ADC_DATA_MASK;
                    int sampleType = (data & ADC_READCYCLE_MASK)>>Integer.numberOfTrailingZeros(ADC_READCYCLE_MASK);
                    switch(sampleType){
                        case 0:
                            e.readoutType = ApsDvsEvent.ReadoutType.A;
                            break;
                        case 1:
                            e.readoutType = ApsDvsEvent.ReadoutType.B;
                            //log.info("got B event");
                            break;
                        case 2:
                            e.readoutType = ApsDvsEvent.ReadoutType.C;
                            //log.info("got C event");
                            break;
                        case 3:
                            log.warning("Event with cycle null was sent out!");
                            break;
                        default:
                            log.warning("Event with unknown cycle was sent out!");
                    }
                    e.special = false;
                    e.timestamp = (timestamps[i]);
                    e.address = data;
                    e.startOfFrame = (data & ADC_START_BIT) == ADC_START_BIT;
                    if(e.startOfFrame){
                        //if(pixCnt!=129600) System.out.println("New frame, pixCnt was incorrectly "+pixCnt+" instead of 129600 but this could happen at end of file");
                        if(ignoreReadout){
                            ignore = true;
                        }
                        //System.out.println("SOF - pixcount: "+pixCnt);
                        resetCounters();
                        pixCnt=0;
                        if(snapshot){
                            snapshot = false;
                            config.adc.setAdcEnabled(false);
                        }
                        frameTime = e.timestamp - firstFrameTs;
                        firstFrameTs = e.timestamp;
                    }
                    if(e.isB() && countX[1] == 0 && countY[2] == 0){
                        exposureB = e.timestamp-firstFrameTs;
                    }
                    if(e.isC() && countX[2] == 0 && countY[2] == 0){
                        exposureC = e.timestamp-firstFrameTs;
                    }
                    if(!(countY[sampleType]<chip.getSizeY())){
                        countY[sampleType] = 0;
                        countX[sampleType]++;
                    }
                    e.x=(short)(chip.getSizeX()-1-countX[sampleType]);
                    e.y=(short)(chip.getSizeY()-1-countY[sampleType]);
                    countY[sampleType]++;
                    pixCnt++;
                    if(((config.useC.isSet() && e.isC()) || (!config.useC.isSet() && e.isB()))  && e.x == (short)(chip.getSizeX()-1) && e.y == (short)(chip.getSizeY()-1)){
                        lastADCevent();
                    }
                    //if(e.x<0 || e.y<0)System.out.println("New ADC event: type "+sampleType+", x "+e.x+", y "+e.y);
//                    if(e.x>=0 && e.y>=0 && displayIntensity && !getAeViewer().isPaused()){
//                        frameData.putApsEvent(e);
//                    }
                }

            }
        /*    if (gotAEREvent) {
                lastEventTime = System.currentTimeMillis();
            } else if (config.autoResetEnabled && config.nChipReset.isSet() && System.currentTimeMillis() - lastEventTime > AUTO_RESET_TIMEOUT_MS && getAeViewer() != null && getAeViewer().getPlayMode() == AEViewer.PlayMode.LIVE) {
                config.resetChip();
            }*/

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
        SBret10.SBret10Config bg;
        try {
            if (getBiasgen() == null) {
                setBiasgen(bg = new SBret10.SBret10Config(this));
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
    public class SBret10Config extends SBretCPLDConfig { // extends Config to give us containers for various configuration data

        /** VR for handling all configuration changes in firmware */
        public static final byte VR_WRITE_CONFIG = (byte) 0xB8;
        ArrayList<HasPreference> hasPreferencesList = new ArrayList<HasPreference>();
        ChipConfigChain chipConfigChain = null;
        private ShiftedSourceBiasCF ssn, ssp;
        private ShiftedSourceBiasCF[] ssBiases = new ShiftedSourceBiasCF[2];
//        private VPot thermometerDAC;
        int address = 0;
        JPanel bPanel;
        JTabbedPane bgTabbedPane;
        // portA
        private PortBit runCpld = new PortBit(SBret10.this, "a3", "runCpld", "(A3) Set high to run CPLD which enables event capture, low to hold logic in reset", true);
        private PortBit extTrigger = new PortBit(SBret10.this, "a1", "extTrigger", "(A1) External trigger to debug APS statemachine", false);
        // portC
        private PortBit runAdc = new PortBit(SBret10.this, "c0", "runAdc", "(C0) High to run ADC", true);
        // portE
        /** Bias generator power down bit */
        PortBit powerDown = new PortBit(SBret10.this, "e2", "powerDown", "(E2) High to disable master bias and tie biases to default rails", false);
        PortBit nChipReset = new PortBit(SBret10.this, "e3", "nChipReset", "(E3) Low to reset AER circuits and hold pixels in reset, High to run", true); // shouldn't need to manipulate from host
        // CPLD shift register contents specified here by CPLDInt and CPLDBit
        private CPLDInt exposureB = new CPLDInt(SBret10.this, 15, 0, "exposureB", "time between reset and readout of a pixel", 0);
        private CPLDInt exposureC = new CPLDInt(SBret10.this, 31, 16, "exposureC", "time between reset and readout of a pixel for a second time (min 240!)", 240);
        private CPLDInt colSettle = new CPLDInt(SBret10.this, 47, 32, "colSettle", "time to settle a column select before readout", 0);
        private CPLDInt rowSettle = new CPLDInt(SBret10.this, 63, 48, "rowSettle", "time to settle a row select before readout", 0);
        private CPLDInt resSettle = new CPLDInt(SBret10.this, 79, 64, "resSettle", "time to settle a reset before readout", 0);
        private CPLDInt frameDelay = new CPLDInt(SBret10.this, 95, 80, "frameDelay", "time between two frames", 0);
        private CPLDInt padding = new CPLDInt(SBret10.this, 109, 96, "pad", "used to zeros", 0);
        private CPLDBit testPixAPSread = new CPLDBit(SBret10.this, 110, "testPixAPSread", "enables continuous scanning of testpixel", false);
        private CPLDBit useC = new CPLDBit(SBret10.this, 111, "useC", "enables a second readout", false);
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
        public SBret10Config(Chip chip) {
            super(chip);
            setName("SBret10Biasgen");

            // port bits
            addConfigValue(nChipReset);
            addConfigValue(powerDown);
            addConfigValue(runAdc);
            addConfigValue(runCpld);
            addConfigValue(extTrigger);

            // cpld shift register stuff
            addConfigValue(exposureB);
            addConfigValue(exposureC);
            addConfigValue(resSettle);
            addConfigValue(rowSettle);
            addConfigValue(colSettle);
            addConfigValue(frameDelay);
            addConfigValue(padding);
            addConfigValue(testPixAPSread);
            addConfigValue(useC);

            // masterbias
            getMasterbias().setKPrimeNFet(55e-3f); // estimated from tox=42A, mu_n=670 cm^2/Vs // TODO fix for UMC18 process
            getMasterbias().setMultiplier(4);  // =45  correct for dvs320
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

            setPotArray(new AddressedIPotArray(this));

            try {
                addAIPot("DiffBn,n,normal,differencing amp"); // at input end of shift register
                addAIPot("OnBn,n,normal,DVS brighter threshold");
                addAIPot("OffBn,n,normal,DVS darker threshold");
                addAIPot("ApsCasEpc,p,cascode,cascode between APS und DVS"); 
                addAIPot("DiffCasBnc,n,cascode,differentiator cascode bias");
                addAIPot("ApsROSFBn,n,normal,APS readout source follower bias");
                addAIPot("LocalBufBn,n,normal,Local buffer bias"); // TODO what's this?
                addAIPot("PixInvBn,n,normal,Pixel request inversion static inverter bias");
                addAIPot("PrBp,p,normal,Photoreceptor bias current");
                addAIPot("PrSFBp,p,normal,Photoreceptor follower bias current (when used in pixel type)");
                addAIPot("RefrBp,p,normal,DVS refractory period current");
                addAIPot("AEPdBn,n,normal,Request encoder pulldown static current");
                addAIPot("LcolTimeoutBn,n,normal,No column request timeout");
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
            
            // on-chip configuration chain
            chipConfigChain = new ChipConfigChain(chip);
            chipConfigChain.addObserver(this);

            // adc 
            adc = new ADC();
            adc.addObserver(this);

            // control of log readout
            apsReadoutControl = new ApsReadoutControl();

            setBatchEditOccurring(true);
            loadPreferences();
            setBatchEditOccurring(false);
            try {
                sendOnchipConfig();
            } catch (HardwareInterfaceException ex) {
                Logger.getLogger(SBret10.class.getName()).log(Level.SEVERE, null, ex);
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
            sendChipConfig();
            return true;
        }
        
        public boolean sendChipConfig() throws HardwareInterfaceException{
            
            String onChipConfigBits = chipConfigChain.getBitString();
            byte[] onChipConfigBytes = bitString2Bytes(onChipConfigBits);
            if(onChipConfigBits == null){
                return false;
            } else {
                BigInteger bi = new BigInteger(onChipConfigBits);
                //System.out.println("Send on chip config (length "+onChipConfigBits.length+" bytes): "+String.format("%0"+(onChipConfigBits.length<<1)+"X", bi));
                log.info("Send on chip config: "+onChipConfigBits);
                sendConfig(CMD_CHIP_CONFIG, 0, onChipConfigBytes);
                return true;
            }
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

        private byte[] bitString2Bytes(String bitString) {
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
                } else if (observable instanceof OutputMux || observable instanceof OnchipConfigBit) {
                    sendChipConfig();
                } else if (observable instanceof ChipConfigChain) {
                    sendChipConfig();
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
            
            log.info("Send CPLD Config: "+cpldConfig.toString());
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
         * Overrides the default method to addConfigValue the custom control panel for configuring the SBret10 output muxes
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
                {putValue(Action.SHORT_DESCRIPTION, "Resets the pixels and the AER logic momentarily");}

                @Override
                public void actionPerformed(ActionEvent evt) {
                    resetChip();
                }
            };

            final Action autoResetAction = new AbstractAction("Enable automatic chip reset") {
                {putValue(Action.SHORT_DESCRIPTION, "Enables reset after no activity when nChipReset is inactive");
                    putValue(Action.SELECTED_KEY,isAutoResetEnabled());}

                @Override
                public void actionPerformed(ActionEvent e) {
                    setAutoResetEnabled(!autoResetEnabled);
                }
            };
            
            JPanel specialButtons = new JPanel();
            specialButtons.setLayout(new BoxLayout(specialButtons, BoxLayout.X_AXIS));
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
            bgTabbedPane.addTab("Output MUX control", chipConfigChain.buildMuxControlPanel());
            
            JPanel apsReadoutPanel = new JPanel();
            apsReadoutPanel.setLayout(new BoxLayout(apsReadoutPanel, BoxLayout.Y_AXIS));
            bgTabbedPane.add("APS Readout", apsReadoutPanel);
            apsReadoutPanel.add(new ParameterControlPanel(adc));
            apsReadoutPanel.add(new ParameterControlPanel(apsReadoutControl));

            JPanel chipConfigPanel = chipConfigChain.getChipConfigPanel();

            bgTabbedPane.addTab("Chip configuration", chipConfigPanel);

            bPanel.add(bgTabbedPane, BorderLayout.CENTER);
            // only select panel after all added

            try {
                bgTabbedPane.setSelectedIndex(getPrefs().getInt("SBret10.bgTabbedPaneSelectedIndex", 0));
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
            getPrefs().putInt("SBret10.bgTabbedPaneSelectedIndex", bgTabbedPane.getSelectedIndex());
        }

        /** Controls the APS intensity readout by wrapping the relevant bits */
        public class ApsReadoutControl implements Observer {

            private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);
            public final String EVENT_TESTPIXEL = "testpixelEnabled";
            
            public ApsReadoutControl() {
                rowSettle.addObserver(this);
                colSettle.addObserver(this);
                exposureB.addObserver(this);
                exposureC.addObserver(this);
                resSettle.addObserver(this);
                frameDelay.addObserver(this);
                testPixAPSread.addObserver(this);
                useC.addObserver(this);
            }
            
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
                frameDelay.set(cc);
            }
            
            public void setExposureBDelayCC(int cc){
                exposureB.set(cc);
            }
            
            public void setExposureCDelayCC(int cc){
                exposureC.set(cc);
            }
            
            public boolean isTestpixelEnabled() {
                return SBret10Config.this.testPixAPSread.isSet();
            }

            public void setTestpixelEnabled(boolean testpixel) {
                SBret10Config.this.testPixAPSread.set(testpixel);
            }
            
            public boolean isUseC() {
                return SBret10Config.this.useC.isSet();
            }

            public void setUseC(boolean useC) {
                SBret10Config.this.useC.set(useC);
            }
            
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
                return frameDelay.get();
            }
            
            public int getExposureBDelayCC() {
                return exposureB.get();
            }
            
            public int getExposureCDelayCC() {
                return exposureC.get();
            }

            @Override
            public void update(Observable o, Object arg) {
                setChanged();
                notifyObservers(arg);
                if (o == testPixAPSread) {
                    getPropertyChangeSupport().firePropertyChange(EVENT_TESTPIXEL, null, testPixAPSread.isSet());
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
;
            int channel = getPrefs().getInt("ADC.channel", 3);
            public final String EVENT_ADC_ENABLED = "adcEnabled", EVENT_ADC_CHANNEL = "adcChannel";
            private PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

            public ADC() {
                runAdc.addObserver(this);
            }

            public boolean isAdcEnabled() {
                return runAdc.isSet();
            }
            
            public boolean isResetOnReadout() {
                return resetOnReadout;
            }
            
            public void setResetOnReadout(boolean reset) {
                resetOnReadout = reset;
            }

            public void setAdcEnabled(boolean yes) {
                if(resetOnReadout){
                    nChipReset.set(false);
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(SBret10.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                runAdc.set(yes);
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
        
        public class ChipConfigChain extends Observable implements HasPreference, Observer {
            
            Chip sbChip;
            
            //Config Bits
            OnchipConfigBit resetCalib = new OnchipConfigBit(SBret10.this, "resetCalib", 0, "turn the calibration neuron off", true),
                    typePCalib = new OnchipConfigBit(SBret10.this, "typePCalib", 1, "make the calibration neuron P type", false),
                    resetTestpixel = new OnchipConfigBit(SBret10.this, "resetTestpixel", 2, "keept the testpixel in reset", true),
                    hotPixelSuppression = new OnchipConfigBit(SBret10.this, "hotPixelSuppression", 3, "keept turn the hot pixel suppression on", false),
                    nArow = new OnchipConfigBit(SBret10.this, "nArow", 4, "use nArow in the AER state machine", false),
                    useAout = new OnchipConfigBit(SBret10.this, "useAout", 5, "turn the pads for the analog MUX outputs on", true)
                    ;
            OnchipConfigBit[] configBits = {resetCalib, typePCalib, resetTestpixel, hotPixelSuppression, nArow, useAout};
            int TOTAL_CONFIG_BITS = 24;
            
            //Muxes
            OutputMux[] amuxes = {new AnalogOutputMux(1), new AnalogOutputMux(2), new AnalogOutputMux(3)};
            OutputMux[] dmuxes = {new DigitalOutputMux(1), new DigitalOutputMux(2), new DigitalOutputMux(3), new DigitalOutputMux(4)};
            OutputMux[] bmuxes = {new DigitalOutputMux(0)};
            ArrayList<OutputMux> muxes = new ArrayList();
            MuxControlPanel controlPanel = null;
            
            public ChipConfigChain(Chip chip){  
                
                this.sbChip = chip;
                
                hasPreferencesList.add(this);
                for (OnchipConfigBit b : configBits) {
                    b.addObserver(this);
                }
            
                muxes.addAll(Arrays.asList(bmuxes)); 
                muxes.addAll(Arrays.asList(dmuxes)); // 4 digital muxes, first in list since at end of chain - bits must be sent first, before any biasgen bits
                muxes.addAll(Arrays.asList(amuxes)); // finally send the 3 voltage muxes

                for (OutputMux m : muxes) {
                    m.addObserver(this);
                    m.setChip(chip);
                }

                bmuxes[0].setName("BiasOutMux");

                bmuxes[0].put(0,"IFThrBn");
                bmuxes[0].put(1,"AEPuYBp");
                bmuxes[0].put(2,"AEPuXBp");
                bmuxes[0].put(3,"LColTimeout");
                bmuxes[0].put(4,"AEPdBn");
                bmuxes[0].put(5,"RefrBp");
                bmuxes[0].put(6,"PrSFBp");
                bmuxes[0].put(7,"PrBp");
                bmuxes[0].put(8,"PixInvBn");
                bmuxes[0].put(9,"LocalBufBn");
                bmuxes[0].put(10,"ApsROSFBn");
                bmuxes[0].put(11,"DiffCasBnc");
                bmuxes[0].put(12,"ApsCasBpc");
                bmuxes[0].put(13,"OffBn");
                bmuxes[0].put(14,"OnBn");
                bmuxes[0].put(15,"DiffBn");

                dmuxes[0].setName("DigMux3");
                dmuxes[1].setName("DigMux2");
                dmuxes[2].setName("DigMux1");
                dmuxes[3].setName("DigMux0");

                for (int i = 0; i < 4; i++) {
                    dmuxes[i].put(0, "AY179right");
                    dmuxes[i].put(1, "Acol");
                    dmuxes[i].put(2, "ColArbTopA");
                    dmuxes[i].put(3, "ColArbTopR");
                    dmuxes[i].put(4, "FF1");
                    dmuxes[i].put(5, "FF2");
                    dmuxes[i].put(6, "Rcarb");
                    dmuxes[i].put(7, "Rcol");
                    dmuxes[i].put(8, "Rrow");
                    dmuxes[i].put(9, "RxarbE");
                    dmuxes[i].put(10, "nAX0");
                    dmuxes[i].put(11, "nArowBottom");
                    dmuxes[i].put(12, "nArowTop");
                    dmuxes[i].put(13, "nRxOn");

                }

                dmuxes[0].put(14, "AY179");
                dmuxes[0].put(15, "RY179");
                dmuxes[1].put(14, "AY179");
                dmuxes[1].put(15, "RY179");
                dmuxes[2].put(14, "biasCalibSpike");
                dmuxes[2].put(15, "nRY179right");
                dmuxes[3].put(14, "nResetRxCol");
                dmuxes[3].put(15, "nRYtestpixel");

                amuxes[0].setName("AnaMux2");
                amuxes[1].setName("AnaMux1");
                amuxes[2].setName("AnaMux0");

                for (int i = 0; i < 3; i++) {
                    amuxes[i].put(0, "on");
                    amuxes[i].put(1, "off");
                    amuxes[i].put(2, "vdiff");
                    amuxes[i].put(3, "nResetPixel");
                    amuxes[i].put(4, "pr");
                    amuxes[i].put(5, "pd");
                }
                
                amuxes[0].put(6, "apsgate");
                amuxes[0].put(7, "apsout");

                amuxes[1].put(6, "apsgate");
                amuxes[1].put(7, "apsout");

                amuxes[2].put(6, "calibNeuron");
                amuxes[2].put(7, "nTimeout_AI");
            
            }
            
            class SBret10OutputMap extends OutputMap {

                HashMap<Integer, String> nameMap = new HashMap<Integer, String>();
                
                void put(int k, int v, String name) {
                    put(k, v);
                    nameMap.put(k, name);
                }

                void put(int k, String name) {
                    nameMap.put(k, name);
                }
            }

            class VoltageOutputMap extends SBret10OutputMap {

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

            class DigitalOutputMap extends SBret10OutputMap {

                DigitalOutputMap() {
                    for (int i = 0; i < 16; i++) {
                        put(i, i, "DigOut " + i);
                    }
                }
            }

            class AnalogOutputMux extends OutputMux {

                AnalogOutputMux(int n) {
                    super(sbChip, 4, 8, (OutputMap)(new VoltageOutputMap()));
                    setName("Voltages" + n);
                }
            }

            class DigitalOutputMux extends OutputMux {

                DigitalOutputMux(int n) {
                    super(sbChip, 4, 16, (OutputMap)(new DigitalOutputMap()));
                    setName("LogicSignals" + n);
                }
            }
            
            public String getBitString(){
                //System.out.print("dig muxes ");
                String dMuxBits = getMuxBitString(dmuxes);
                //System.out.print("config bits ");
                String configBits = getConfigBitString();
                //System.out.print("analog muxes ");
                String aMuxBits = getMuxBitString(amuxes);
                //System.out.print("bias muxes ");
                String bMuxBits = getMuxBitString(bmuxes);
                
                String chipConfigChain = (dMuxBits + configBits + aMuxBits + bMuxBits);
                //System.out.println("On chip config chain: "+chipConfigChain);

                return chipConfigChain; // returns bytes padded at end
            }
            
            String getMuxBitString(OutputMux[] muxs){
                StringBuilder s = new StringBuilder();
                for (OutputMux m : muxs) {
                    s.append(m.getBitString());
                }
                //System.out.println(s);
                return s.toString();
            }
            
            String getConfigBitString() {
                StringBuilder s = new StringBuilder();
                for (int i = 0; i < TOTAL_CONFIG_BITS - configBits.length; i++) {
                    s.append("0"); 
                }
                for (int i = configBits.length - 1; i >= 0; i--) {
                    s.append(configBits[i].isSet() ? "1" : "0");
                }
                //System.out.println(s);
                return s.toString();
            }
            
            public MuxControlPanel buildMuxControlPanel() {
                return new MuxControlPanel(muxes);
            }
            
            public JPanel getChipConfigPanel(){
                JPanel chipConfigPanel = new JPanel(new BorderLayout());

                //On-Chip config bits
                JPanel extraPanel = new JPanel();
                extraPanel.setLayout(new BoxLayout(extraPanel, BoxLayout.Y_AXIS));
                for (OnchipConfigBit b : configBits) {
                    extraPanel.add(new JRadioButton(b.getAction()));
                }
                extraPanel.setBorder(new TitledBorder("Extra on-chip bits"));
                chipConfigPanel.add(extraPanel, BorderLayout.NORTH);

                //FX2 port bits
                JPanel portBitsPanel = new JPanel();
                portBitsPanel.setLayout(new BoxLayout(portBitsPanel, BoxLayout.Y_AXIS));
                for (PortBit p : portBits) {
                    portBitsPanel.add(new JRadioButton(p.getAction()));
                }
                portBitsPanel.setBorder(new TitledBorder("Cypress FX2 port bits"));
                chipConfigPanel.add(portBitsPanel, BorderLayout.CENTER);
                
                return chipConfigPanel;
            }
            
            @Override
            public void loadPreference() {
                for (OnchipConfigBit b : configBits) {
                    b.loadPreference();
                }
            }

            @Override
            public void storePreference() {
                for (OnchipConfigBit b : configBits) {
                    b.storePreference();
                }
            }
            
            @Override
            public void update(Observable o, Object arg) {
                setChanged();
                notifyObservers(arg);
            }
        }
    }

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
     * @return the ignoreReadout
     */
    public boolean isIgnoreReadout() {
        return ignoreReadout;
    }

    /**
     * @param displayLogIntensityChangeEvents the displayLogIntensityChangeEvents to set
     */
    public void setIgnoreReadout(boolean ignoreReadout) {
        this.ignoreReadout = ignoreReadout;
        getPrefs().putBoolean("ignoreReadout", ignoreReadout);
        getAeViewer().interruptViewloop();
    }

    /**
     * Displays data from SeeBetter test chip SeeBetter10/11.
     * @author Tobi
     */
    public class SBret10DisplayMethod extends DVSWithIntensityDisplayMethod {

        private TextRenderer renderer = null;
        private TextRenderer exposureRenderer = null;

        public SBret10DisplayMethod(SBret10 chip) {
            super(chip.getCanvas());
        }

        @Override
        public void display(GLAutoDrawable drawable) {
            if (renderer == null) {
                renderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 18), true, true);
                renderer.setColor(1, .2f, .2f, 0.4f);
            }
            if (exposureRenderer == null) {
                exposureRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 8), true, true);
                exposureRenderer.setColor(1, 1, 1, 1);
            }
            super.display(drawable);
            GL gl = drawable.getGL();
            gl.glLineWidth(2f);
            gl.glColor3f(1, 1, 1);
            // draw boxes around arrays

            rect(gl, 0, 0, 240, 180, "apsDVS"); 
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
                if(displayIntensity){
                    exposureRenderer.begin3DRendering();
                    String frequency = "";
                    if(frameTime>0){
                         frequency = "("+(float)1000000/frameTime+" Hz)";
                    }
                    String expC = "";
                    if(config.useC.isSet()){
                         expC = " ms, exposure 2: "+(float)exposureC/1000;
                    }
                    exposureRenderer.draw3D("exposure 1: "+(float)exposureB/1000+expC+" ms, frame period: "+(float)frameTime/1000+" ms "+frequency, x, h, 0, .4f); // x,y,z, scale factor 
                    exposureRenderer.end3DRendering();
                }
                
            }
            gl.glPopMatrix();
        }
    }

    public IntensityFrameData getFrameData() {
        return frameData;
    }

    /**
     * Renders complex data from SeeBetter chip.
     *
     * @author tobi
     */
    public class SBret10Renderer extends RetinaRenderer {

        private SBret10 cDVSChip = null;
//        private final float[] redder = {1, 0, 0}, bluer = {0, 0, 1}, greener={0,1,0}, brighter = {1, 1, 1}, darker = {-1, -1, -1};
        private final float[] brighter = {0, 1, 0}, darker = {1, 0, 0};
        private int sizeX = 1;
        private LowpassFilter2d agcFilter = new LowpassFilter2d();  // 2 lp values are min and max log intensities from each frame
        private boolean agcEnabled;
        /** PropertyChange */
        public static final String AGC_VALUES = "AGCValuesChanged";
        /** PropertyChange when value is changed */
        public static final String APS_INTENSITY_GAIN = "apsIntensityGain", APS_INTENSITY_OFFSET = "apsIntensityOffset";
        /** Control scaling and offset of display of log intensity values. */
        int apsIntensityGain, apsIntensityOffset;

        public SBret10Renderer(SBret10 chip) {
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
        public synchronized void render(EventPacket pkt) {
            
            ApsDvsEventPacket packet = (ApsDvsEventPacket) pkt;
            
            checkPixmapAllocation();
            resetSelectedPixelEventCount(); // TODO fix locating pixel with xsel ysel

            if (packet == null) {
                return;
            }
            //packet.sortByTimeStamp();
            this.packet = packet;
            if (packet.getEventClass() != ApsDvsEvent.class) {
                log.warning("wrong input event class, got " + packet.getEventClass() + " but we need to have " + ApsDvsEvent.class);
                return;
            }
            float[] pm = getPixmapArray();
            sizeX = chip.getSizeX();
            //log.info("pm : "+pm.length+", sizeX : "+sizeX);
            if (!accumulateEnabled) {
                resetFrame(.5f);
            }
            
            String event = "";
            try {
                step = 1f / (colorScale);
                Iterator allItr = packet.fullIterator();
                while(allItr.hasNext()){
                    //The iterator only iterates over the DVS events
                    ApsDvsEvent e = (ApsDvsEvent) allItr.next();                        
                    int type = e.getType();
                    if(!e.isAdcSample()){
                        if(displayLogIntensityChangeEvents){
                            if (xsel >= 0 && ysel >= 0) { // find correct mouse pixel interpretation to make sounds for large pixels
                                int xs = xsel, ys = ysel;
                                if (e.x == xs && e.y == ys) {
                                    playSpike(type);
                                }
                            }
                            int x = e.x, y = e.y;
                            switch (e.polarity) {
                                case On:
                                    changePixel(x, y, pm, brighter, step);
                                    break;
                                case Off:
                                    changePixel(x, y, pm, darker, step);
                                    break;
                            }
                        }
                        if(frameData.useDVSExtrapolation){
                            frameData.putDvsEvent(e);
                        }
                    }else if(e.isAdcSample() && e.x>=0 && e.y>=0 && displayIntensity && !getAeViewer().isPaused()){
                        frameData.putApsEvent(e);
                    }
                }
                if (displayIntensity) {
                    double minADC = Integer.MAX_VALUE;
                    double maxADC = Integer.MIN_VALUE;
                    for (int y = 0; y < EntirePixelArray.height; y++) {
                        for (int x = 0; x < EntirePixelArray.width; x++) {
                            //event = "ADC x "+x+", y "+y;
                            double count = frameData.get(x, y);
                            if (agcEnabled) {
                                if (count < minADC) {
                                    minADC = count;
                                } else if (count > maxADC) {
                                    maxADC = count;
                                }
                            }
                            float v = (float)adc01normalized(count);
                            float[] vv = {v, v, v};
                            changePixel(x, y, pm, vv, 1);
                        }
                    }
                    if (agcEnabled && (minADC > 0 && maxADC > 0)) { // don't adapt to first frame which is all zeros
                        Float filter2d = agcFilter.filter2d((float)minADC, (float)maxADC, frameData.getTimestamp());
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
        }

        /** Changes pixmap location for pixel affected by this event. 
         * x,y refer to space of pixels 
         */
        private void changePixel(int x, int y, float[] f, float[] c, float step) {
            int ind = 3* (x + y * sizeX);
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

        private double adc01normalized(double count) {
            double v;
            if (!agcEnabled) {
                v = (float) (apsIntensityGain*count+apsIntensityOffset) / (float) MAX_ADC;
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
            int gain = (int) (SBret10.MAX_ADC / (f.y - f.x));
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
    public static enum Read{A, B, C, DIFF_B, DIFF_C, HDR};
    
    public class IntensityFrameData {

        /** The scanner is 240wide by 180 high  */
        public final int WIDTH = EntirePixelArray.width, HEIGHT = EntirePixelArray.height; // width is BDVS pixels not scanner registers
        private final int NUMSAMPLES = WIDTH * HEIGHT;
        private int timestamp = 0; // timestamp of starting sample
        private float[] aBuffer,bBuffer,cBuffer;
        private float[] displayBuffer, displayFrame,lastFrame;
        private float[] onMismatch, offMismatch;
        private int[] onCnt,offCnt,timestamps;
        /** Readers should access the current reading buffer. */
        private boolean useDVSExtrapolation = getPrefs().getBoolean("useDVSExtrapolation", false);
        private boolean useMismatchCorrection = getPrefs().getBoolean("useMismatchCorrection", true);
        private boolean invertADCvalues = getPrefs().getBoolean("invertADCvalues", true); // true by default for log output which goes down with increasing intensity
        
        public static final String STEP_CHANGE = "Step Values Changed";
        
        private Read displayRead = Read.DIFF_B;
        public float onStep, offStep;
        private final float updateFactor = 1f/100000f;
        
        public IntensityFrameData() {
            resetFrames();
        }
        
        public void resetFrames(){
            aBuffer = new float[WIDTH*HEIGHT];
            bBuffer = new float[WIDTH*HEIGHT];
            cBuffer = new float[WIDTH*HEIGHT];
            displayFrame = new float[WIDTH*HEIGHT];
            displayBuffer = new float[WIDTH*HEIGHT];
            lastFrame = new float[WIDTH*HEIGHT];
            onMismatch = new float[WIDTH*HEIGHT];
            offMismatch = new float[WIDTH*HEIGHT];
            onCnt = new int[WIDTH*HEIGHT];
            offCnt = new int[WIDTH*HEIGHT];
            timestamps = new int[WIDTH*HEIGHT];
            //scale = new float[WIDTH*HEIGHT];
            Arrays.fill(aBuffer, 0.0f);
            Arrays.fill(bBuffer, 0.0f);
            Arrays.fill(cBuffer, 0.0f);
            Arrays.fill(displayFrame, 0.0f);
            Arrays.fill(displayBuffer, 0.0f);
            Arrays.fill(lastFrame, 0.0f);
            Arrays.fill(onMismatch, 1.0f);
            Arrays.fill(offMismatch, 1.0f);
            Arrays.fill(onCnt, 0);
            Arrays.fill(offCnt, 0);
            Arrays.fill(offCnt, 0);
            //Arrays.fill(scale, 0.01f);
            onStep = 0.2f;
            offStep = -0.2f;
        }

        /** Gets the sample at a given pixel address (not scanner address) 
         * 
         * @param x pixel x from left side of array
         * @param y pixel y from top of array on chip
         * @return  value from ADC
         */
        
        public float get(int x, int y) {
            return displayFrame[getIndex(x,y)];
        }
        
                
        private int getIndex(int x, int y){
            return y*WIDTH+x;
        }
        
        public void putDvsEvent(ApsDvsEvent e){
            //update displayFrame
            int idx = getIndex(e.x,e.y);
            if(e.timestamp<timestamps[idx])return;
            if(e.polarity == Polarity.On){
                if(useMismatchCorrection){
                    displayFrame[idx] += onMismatch[idx]*onStep;
                }else{
                    displayFrame[idx] += onStep;
                }
                onCnt[idx]++;
            }else{
                if(useMismatchCorrection){
                    displayFrame[idx] += offMismatch[idx]*offStep;
                }else{
                    displayFrame[idx] += offStep;
                }
                offCnt[idx]++;
            }
            if(lastFrame[idx]!=displayBuffer[idx]){
                updateScale(idx);
            }
            lastFrame[idx]=displayBuffer[idx];
        }
        
        private void putApsEvent(ApsDvsEvent e){
            if(!e.isAdcSample()) return;
            if(e.isStartOfFrame())timestamp=e.timestamp;
            putNextSampleValue(e.adcSample, e.readoutType, e.x, e.y, e.timestamp);
        }
        
        private void putNextSampleValue(int val, ReadoutType type, int x, int y, int ts) {
            int idx = getIndex(x,y);
            switch(type){
                case C: 
                    cBuffer[idx] = val;
                    break;
                case B: 
                    bBuffer[idx] = val;
                    break;
                case A:
                default:
                    aBuffer[idx] = val;
                    break;
            }
            
            boolean pushDisplay=false;
            if(x==WIDTH-1 && y==HEIGHT-1)pushDisplay=true;
            switch (displayRead) {
                case A:
                    displayFrame[idx] = aBuffer[idx];
                    if(!(pushDisplay && type==ReadoutType.A))pushDisplay=false;
                    break;
                case B:
                    displayFrame[idx] = bBuffer[idx];
                    if(!(pushDisplay && type==ReadoutType.B))pushDisplay=false;
                    break;
                case C:
                    displayFrame[idx] = cBuffer[idx];
                    if(!(pushDisplay && type==ReadoutType.C))pushDisplay=false;
                    break;
                case DIFF_B:
                default:
                    displayFrame[idx] = aBuffer[idx]-bBuffer[idx];
                    if(!(pushDisplay && type==ReadoutType.B))pushDisplay=false;
                    break;
                case DIFF_C:
                    displayFrame[idx] = aBuffer[idx]-cBuffer[idx];
                    if(!(pushDisplay && type==ReadoutType.C))pushDisplay=false;
                    break;
                case HDR:
                    displayFrame[idx] = exposureB*Math.max((aBuffer[idx]-bBuffer[idx])/exposureB, (aBuffer[idx]-cBuffer[idx])/exposureC);
                    if(!(pushDisplay && type==ReadoutType.C))pushDisplay=false;
                    break;
            }
            if (invertADCvalues) {
                displayFrame[idx] = MAX_ADC-displayFrame[idx];
            }
            if(useDVSExtrapolation){
                displayFrame[idx] = (float)Math.log(displayFrame[idx]);
            }
            displayBuffer[idx]=displayFrame[idx];
            timestamps[idx]=ts;
            if(pushDisplay)pushDisplayFrame();
            //updateScale(idx);
        }
        
        private void pushDisplayFrame(){
            if(useDVSExtrapolation){
                //adaptScale();
                System.out.println("ON Step: "+onStep+", OFF Step: "+offStep);
            }
            
        }
        
        private void updateScale(int idx){
            float dI = displayBuffer[idx]-lastFrame[idx];
            if(dI<MAX_ADC && dI>-MAX_ADC){
                if(offCnt[idx] == 0 && onCnt[idx] > 0 && dI > 0){
                    for(int i=0;i<onCnt[idx];i++){
                        float thisStep = (float)dI/(float)onCnt[idx];
                        onStep = (1.0f-updateFactor)*onStep+updateFactor*thisStep;
                        if(thisStep>onMismatch[idx]*onStep){
                            onMismatch[idx]-=updateFactor;
                        }else{
                            onMismatch[idx]+=updateFactor;
                        }
                    }
                    //System.out.println("New ON step: "+onStep+", dI:"+dI);
                }else if(onCnt[idx] == 0 && offCnt[idx] > 0 && dI < 0){
                    for(int i=0;i<offCnt[idx];i++){
                        float thisStep = (float)dI/(float)offCnt[idx];
                        offStep = (1.0f-updateFactor)*offStep+updateFactor*thisStep;
                        if(thisStep>offMismatch[idx]*onStep){
                            offMismatch[idx]+=updateFactor;
                        }else{
                            offMismatch[idx]-=updateFactor;
                        }
                    }
                    //System.out.println("New OFF step: "+offStep+", dI:"+dI);
                }
            }
            onCnt[idx]=0;
            offCnt[idx]=0;
        }
        
        private void adaptScale(){
            for(int i=0;i<WIDTH*HEIGHT;i++){
                float trueDiff = displayBuffer[i]-lastFrame[i];
                float updatedDiff = displayFrame[i]-lastFrame[i];
                if(updatedDiff!=0.0f && trueDiff!=0.0f && updatedDiff != trueDiff){
                    if(trueDiff>0 && trueDiff<MAX_ADC){
                        onStep = (1.0f-updateFactor)*onStep+updateFactor*onStep*(trueDiff/updatedDiff);
                    }else if (trueDiff<0 && trueDiff>-MAX_ADC){
                        offStep = (1.0f-updateFactor)*offStep+updateFactor*offStep*(trueDiff/updatedDiff);
                    }
                }
            }
        }

        /**
         * @return the useDVSExtrapolation
         */
        public boolean isUseMismatchCorrection() {
            return useMismatchCorrection;
        }

        /**
         * @param useMismatchCorrection the useMismatchCorrection to set
         */
        public void setUseMismatchCorrection(boolean useMismatchCorrection) {
            this.useMismatchCorrection = useMismatchCorrection;
            getPrefs().putBoolean("useMismatchCorrection", useMismatchCorrection);
        }
        
        /**
         * @return the useDVSExtrapolation
         */
        public boolean isUseDVSExtrapolation() {
            return useDVSExtrapolation;
        }

        /**
         * @param useDVSExtrapolation the useOffChipCalibration to set
         */
        public void setUseDVSExtrapolation(boolean useDVSExtrapolation) {
            this.useDVSExtrapolation = useDVSExtrapolation;
            getPrefs().putBoolean("useDVSExtrapolation", useDVSExtrapolation);
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
        
        public int getTimestamp(){
            return timestamp;
        }

        @Override
        public String toString() {
            return "IntensityFrameData{" + "WIDTH=" + WIDTH + ", HEIGHT=" + HEIGHT + ", NUMSAMPLES=" + NUMSAMPLES  + '}';
        }

    }
    
}
