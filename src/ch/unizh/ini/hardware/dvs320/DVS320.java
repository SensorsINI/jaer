/*
created 26 Oct 2008 for new DVS320 chip
 */
package ch.unizh.ini.hardware.dvs320;

import ch.unizh.ini.caviar.chip.retina.*;
import ch.unizh.ini.caviar.aemonitor.*;
import ch.unizh.ini.caviar.biasgen.*;
import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.hardwareinterface.*;
import java.io.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import javax.swing.JPanel;

/**
 * Describes DVS320 retina and its event extractor and bias generator.
 * Two constructors ara available, the vanilla constructor is used for event playback and the
 *one with a HardwareInterface parameter is useful for live capture.
 * {@link #setHardwareInterface} is used when the hardware interface is constructed after the retina object.
 *The constructor that takes a hardware interface also constructs the biasgen interface.
 * <p>
 * The DVS320 features 320x240 pixels, a fully configurable bias generator, 
 * and a configurable output selector for digital and analog current and voltage outputs for characterization.
 * The output is word serial and includes an intensity neuron which rides onto the other addresses.
 * DVS320 is built in UMC18 CIS process and has 14.5u pixels.
 *
 * @author tobi
 */
public class DVS320 extends AERetina implements Serializable {

    /** Creates a new instance of DVS320.  */
    public DVS320() {
        setName("DVS320");
        setSizeX(320);
        setSizeY(240);
        setNumCellTypes(2);
        setPixelHeightUm(14.5f);
        setPixelWidthUm(14.5f);
        setEventExtractor(new DVS320Extractor(this));
        setBiasgen(new DVS320.Biasgen(this));
    }

    /** Creates a new instance of DVS320
     * @param hardwareInterface an existing hardware interface. This constructer is preferred. It makes a new Biasgen object to talk to the on-chip biasgen.
     */
    public DVS320(HardwareInterface hardwareInterface) {
        this();
        setHardwareInterface(hardwareInterface);
    }

    /** The event extractor. Each pixel has two polarities 0 and 1. 
     * There is one extra neuron which signals absolute intensity.
     *The bits in the raw data coming from the device are as follows.
     *Bit 0 is polarity, on=1, off=0<br>
     *Bits 1-9 are x address (max value 320)<br>
     *Bits 10-17 are y address (max value 240) <br>
     *Bit 18 signals the special intensity neuron, 
     * but it always comes together with an x address. It means there was an intensity spike AND a normal pixel spike.
     *<p>
     */
    public class DVS320Extractor extends RetinaExtractor {

        final int XMASK = 0x3fe,  XSHIFT = 1,  YMASK = 0xff000,  YSHIFT = 12;

        public DVS320Extractor(DVS320 chip) {
            super(chip);
//            setXmask(0x00000);
//            setXshift((byte)1);
//            setYmask(0x00007f00);
//            setYshift((byte)8);
//            setTypemask(0x00000001);
//            setTypeshift((byte)0);
//            setFlipx(true);
//            setFlipy(false);
//            setFliptype(true);
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
                e.x = (short) (((addr & XMASK) >>> XSHIFT));
                if (e.x < 0) {
                    e.x = 0;// else if(e.x>319) 
                // e.x=319; // TODO
                }
                e.y = (short) ((addr & YMASK) >>> YSHIFT);
                if (e.y > 239) {
//                    log.warning("e.y="+e.y);
                    e.y = 239; // TODO fix this
                } else if (e.y < 0) {
                    e.y = 0; // TODO
                }
                e.type = (byte) (addr & 1);
                e.polarity = e.type == 0 ? PolarityEvent.Polarity.Off : PolarityEvent.Polarity.On;
            }
            return out;
        }
    }

    /** overrides the Chip setHardware interface to construct a biasgen if one doesn't exist already.
     * Sets the hardware interface and the bias generators hardware interface
     *@param hardwareInterface the interface
     */
    public void setHardwareInterface(final HardwareInterface hardwareInterface) {
        this.hardwareInterface = hardwareInterface;
        try {
            if (getBiasgen() == null) {
                setBiasgen(new DVS320.Biasgen(this));
            } else {
                getBiasgen().setHardwareInterface((BiasgenHardwareInterface) hardwareInterface);
            }
        } catch (ClassCastException e) {
            log.warning(e.getMessage() + ": probably this chip object has a biasgen but the hardware interface doesn't, ignoring");
        }
    }

    /**
     * Describes ConfigurableIPots on DVS320 retina chip as well as the other configuration bits which control, for example, which
     * outputs are selected. These are configured by a shift register. 
     * <p>
     * The pr, foll, and refr biases use the lowpower bias for their p src bias and the pr, foll and refr pfets
     * all have their psrcs wired to this shifted p src bias. Also, the pr, foll and refr biases also drive the same
     * shifted psrc bias with their own shifted psrc bias. Therefore all 4 of these biases (pr, foll, refr, and lowpower) should
     * have the same bufferbias bits. This constraint is enforced by software.
     * <p>
     * The output configuration bits follow the bias values and consist of 12 bits that select 3 outputs.
     * <nl>
     * <li> Bits 0-3 select the analog output.
     * <li> Bits 4-8 select the digital output.
     * <li> Bits 
     * 
     * @author tobi
     */
    public class Biasgen extends ch.unizh.ini.caviar.biasgen.Biasgen implements ChipControlPanel {

        private ConfigurableIPot cas,  diffOn,  diffOff,  diff,  bulk;
        private ConstrainedConfigurableIPot refr,  pr,  foll,  lowpower;
        private ArrayList<ConstrainedConfigurableIPot> sharedBufferBiasList = new ArrayList<ConstrainedConfigurableIPot>();
        DVS320ControlPanel controlPanel;
        
                /** Creates a new instance of Biasgen for DVS320 with a given hardware interface
         *@param chip the chip this biasgen belongs to
         */
        public Biasgen(Chip chip) {
            super(chip);
            setName("DVS320");
            getMasterbias().setKPrimeNFet(500e-6f); // estimated from tox=37A // TODO fix for UMC18 process
            getMasterbias().setMultiplier(9 * (24f / 2.4f) / (4.8f / 2.4f));  // TODO fix numbers from layout masterbias current multiplier according to fet M and W/L
            getMasterbias().setWOverL(4.8f / 2.4f); // TODO fix from layout
            controlPanel = new DVS320ControlPanel(DVS320.this);


            /*
            @param biasgen
            @param name
            @param shiftRegisterNumber the position in the shift register, 0 based, starting on end from which bits are loaded
            @param type (NORMAL, CASCODE)
            @param sex Sex (N, P)
            @param lowCurrentModeEnabled bias is normal (false) or in low current mode (true)
            @param enabled bias is enabled (true) or weakly tied to rail (false)
            @param bitValue initial bitValue
            @param bufferBitValue buffer bias bit value
            @param displayPosition position in GUI from top (logical order)
            @param tooltipString a String to display to user of GUI telling them what the pots does
             */
            setPotArray(new IPotArray(this));
            /*
             * pr
             * cas
             * foll
             * bulk
             * diff
             * on
             * off
             * refr
             * lowpower
             * pux
             * puy
             * pd
             * padfoll
             * ifThr
             * test
             */

            getPotArray().addPot(pr = new ConstrainedConfigurableIPot(this, "pr", 0, IPot.Type.NORMAL, IPot.Sex.P, false, true, 100, ConfigurableIPot.maxBufferValue, 1, "Photoreceptor", sharedBufferBiasList));
            getPotArray().addPot(cas = new ConfigurableIPot(this, "cas", 1, IPot.Type.CASCODE, IPot.Sex.N, false, true, 200, ConfigurableIPot.maxBufferValue, 2, "Photoreceptor cascode"));
            getPotArray().addPot(foll = new ConstrainedConfigurableIPot(this, "foll", 2, IPot.Type.NORMAL, IPot.Sex.P, false, true, 1000, ConfigurableIPot.maxBufferValue, 3, "Src follower buffer between photoreceptor and differentiator", sharedBufferBiasList));
            getPotArray().addPot(bulk = new ConfigurableIPot(this, "bulk", 3, IPot.Type.NORMAL, IPot.Sex.N, false, true, 1000, ConfigurableIPot.maxBufferValue, 4, "Differentiator switch bulk bias"));
            getPotArray().addPot(diff = new ConfigurableIPot(this, "diff", 4, IPot.Type.NORMAL, IPot.Sex.N, false, true, 2000, ConfigurableIPot.maxBufferValue, 5, "Differentiator"));
            getPotArray().addPot(diffOn = new ConfigurableIPot(this, "on", 5, IPot.Type.NORMAL, IPot.Sex.N, false, true, 500, ConfigurableIPot.maxBufferValue, 6, "ON threshold - higher to raise threshold"));
            getPotArray().addPot(diffOff = new ConfigurableIPot(this, "off", 6, IPot.Type.NORMAL, IPot.Sex.N, false, true, 0, ConfigurableIPot.maxBufferValue, 7, "OFF threshold, lower to raise threshold"));
            getPotArray().addPot(refr = new ConstrainedConfigurableIPot(this, "refr", 7, IPot.Type.NORMAL, IPot.Sex.P, false, true, 50, ConfigurableIPot.maxBufferValue, 8, "Refractory period", sharedBufferBiasList));
            getPotArray().addPot(lowpower = new ConstrainedConfigurableIPot(this, "lowpower", 8, IPot.Type.NORMAL, IPot.Sex.N, false, true, 50, ConfigurableIPot.maxBufferValue, 9, "Source bias for low current biases (pr, foll, refr)", sharedBufferBiasList));
            getPotArray().addPot(new ConfigurableIPot(this, "pux", 9, IPot.Type.NORMAL, IPot.Sex.P, false, true, ConfigurableIPot.maxBitValue, ConfigurableIPot.maxBufferValue, 11, "2nd dimension AER static pullup"));
            getPotArray().addPot(new ConfigurableIPot(this, "puy", 10, IPot.Type.NORMAL, IPot.Sex.P, false, true, 0, ConfigurableIPot.maxBufferValue, 10, "1st dimension AER static pullup"));
            getPotArray().addPot(new ConfigurableIPot(this, "pd", 11, IPot.Type.NORMAL, IPot.Sex.N, false, true, 0, ConfigurableIPot.maxBufferValue, 11, "AER request pulldown"));
            getPotArray().addPot(new ConfigurableIPot(this, "padfoll", 12, IPot.Type.NORMAL, IPot.Sex.P, false, true, 300, ConfigurableIPot.maxBufferValue, 20, "voltage follower pads"));
            getPotArray().addPot(new ConfigurableIPot(this, "ifthr", 13, IPot.Type.NORMAL, IPot.Sex.N, false, true, 0, ConfigurableIPot.maxBufferValue, 30, "intensity (total photocurrent) IF neuron threshold"));
            getPotArray().addPot(new ConfigurableIPot(this, "test", 14, IPot.Type.NORMAL, IPot.Sex.N, false, true, 0, ConfigurableIPot.maxBufferValue, 100, "test bias - no functionality"));

            sharedBufferBiasList.add(pr);
            sharedBufferBiasList.add(foll);
            sharedBufferBiasList.add(refr);
            sharedBufferBiasList.add(lowpower);

            loadPreferences();

        }

        public byte[] formatConfigurationBytes(Biasgen biasgen) {
            byte[] biasBytes=super.formatConfigurationBytes(biasgen);
            byte[] configBytes=allMuxes.formatConfigurationBytes().bytes;
            byte[] allBytes=new byte[biasBytes.length+configBytes.length];
            System.arraycopy(configBytes, 0, allBytes, 0, configBytes.length);
            System.arraycopy(biasBytes, 0, allBytes, configBytes.length, biasBytes.length);
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


        /** @return a new or existing panel for controlling this bias generator functionally
         */
        public JPanel getControlPanel() {
//            if(controlPanel==null) controlPanel=new Tmpdiff128FunctionalBiasgenPanel(DVS320.this);
            return controlPanel;
        }

        /** A mux for selecting output */
        class OutputMux{

            int nSrBits;
            int nInputs;
            OutputMap map;
            private String name="OutputMux";
            int selectedChannel=-1; // defaults to no input selected in the case of voltage and current, and channel 0 in the case of logic
            
            OutputMux(int nsr, int nin, OutputMap m) {
                nSrBits = nsr;
                nInputs = nin;
                map = m;
            }
            
            void select(int i){
                selectedChannel=i;
                try {
                    sendConfiguration(DVS320.Biasgen.this);
                } catch (HardwareInterfaceException ex) {
                    log.warning("selecting output: "+ex);
                }
            }

            void put(int k, String name) {
                map.put(k, name);
            }
            
            OutputMap getMap(){
                return map;
            }
            
            int getCode(int i){
                return map.get(i);
            }
            
            String getBitString(){
                StringBuilder s=new StringBuilder();
                int code=selectedChannel!=-1?getCode(selectedChannel):0; // code 0 if no channel selected
                int k=nSrBits-1;
                while(k>=0){
                    int x=code&(1<<k);
                    boolean b=(x==0);
                    s.append(b?'0':'1');
                } // construct big endian string e.g. code=14, s='1011'
                return s.toString();
            }
            
            String getName(int i){
                return map.nameMap.get(i);
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }
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

            void put(int k, int v){
                put(k, v,"Voltage "+k);
            }
            
            VoltageOutputMap() {
                put(0, 1);
                put(1, 3);
                put(2, 5);
                put(3, 7);
                put(4, 9);
                put(5, 11);
                put(6, 14);
                put(7, 15);
            }
        }

        class DigitalOutputMap extends OutputMap {

            DigitalOutputMap() {
                for (int i = 0; i < 16; i++) {
                    put(i, i, "DigOut "+i);
                }
            }
        }

        class CurrentOutputMap extends OutputMap {

            void put(int k, int v){
                put(k, v,"Current "+k);
            }
           CurrentOutputMap() {
                put(0, 3);
                put(1, 7);
                put(2, 14);
                put(3, 15);
            }
        }

        class VoltageOutputMux extends OutputMux {

            VoltageOutputMux() {
                super(4, 8, new VoltageOutputMap());
                setName("Voltages");
            }
        }

        class LogicMux extends OutputMux {

            LogicMux() {
                super(4, 16, new DigitalOutputMap());
                 setName("Digital Signals");
           }
        }

        class CurrentOutputMux extends OutputMux {

            CurrentOutputMux() {
                super(4, 4, new CurrentOutputMap());
                 setName("Currents");
           }
        }

        class AllMuxes extends ArrayList<OutputMux>{
            OutputMux[] anaMuxes={new VoltageOutputMux(), new VoltageOutputMux(), new VoltageOutputMux()};
            OutputMux[] digMuxes={new LogicMux(), new LogicMux(), new LogicMux(), new LogicMux()};
            OutputMux curMux=new CurrentOutputMux();
            class BytesAndBitCount{
                byte[] bytes;
                int nbits;
            }
            
            AllMuxes() {
                add(curMux); // first in list since at end of chain - bits must be sent first, before any biasgen bits
                addAll(Arrays.asList(digMuxes)); // next are 4 logic muxes
                addAll(Arrays.asList(anaMuxes)); // finally send the 3 analog muxes
                anaMuxes[0].put(0, "testPr");
            }
            
            BytesAndBitCount formatConfigurationBytes(){
                int nBits=0;
                StringBuilder s=new StringBuilder();
                for(OutputMux m:this){
                    s.append(m.getBitString());
                    nBits+=m.nSrBits;
                }
                BigInteger bi=new BigInteger(s.toString(),2);
                byte[] byteArray=bi.toByteArray();
                BytesAndBitCount bytes=new BytesAndBitCount();
                bytes.bytes=byteArray;
                bytes.nbits=nBits;
                return bytes;
            }
        }
        
        AllMuxes allMuxes=new AllMuxes();
        
    }
} 
    
    
