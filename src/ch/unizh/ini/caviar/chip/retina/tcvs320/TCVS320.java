/*
 created 4 Jan 2008 for new TCVS320 chip
 */

package ch.unizh.ini.caviar.chip.retina.tcvs320;

import ch.unizh.ini.caviar.chip.retina.*;
import ch.unizh.ini.caviar.aemonitor.*;
import ch.unizh.ini.caviar.biasgen.*;
import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.graphics.*;
import ch.unizh.ini.caviar.hardwareinterface.*;
import ch.unizh.ini.caviar.hardwareinterface.usb.CypressFX2TmpdiffRetina;
import java.awt.Menu;
import java.awt.event.*;
import java.io.*;
import java.util.Observable;
import java.util.Observer;
import javax.swing.*;
import javax.swing.JPanel;


/**
 * Describes tmpdiff128 retina and its event extractor and bias generator.
 * Two constructors ara available, the vanilla constructor is used for event playback and the
 *one with a HardwareInterface parameter is useful for live capture.
 * {@link #setHardwareInterface} is used when the hardware interface is constructed after the retina object.
 *The constructor that takes a hardware interface also constructs the biasgen interface.
 *
 * @author tobi
 */
public class TCVS320 extends AERetina implements Serializable {
    
    /** Creates a new instance of Tmpdiff128. No biasgen is constructed for this constructor, because there is no hardware interface defined. */
    public TCVS320() {
        setName("TCVS320");
        setSizeX(350); // this is for debugging!!! set back to 320 //setSizeX(320);
        setSizeY(240);
        setNumCellTypes(2);
        setPixelHeightUm(14);
        setPixelWidthUm(14);
        setEventExtractor(new TCVS320Extractor(this));
        setBiasgen(new TCVS320.Biasgen(this));
    }
    
    /** Creates a new instance of Tmpdiff128
     * @param hardwareInterface an existing hardware interface. This constructer is preferred. It makes a new Biasgen object to talk to the on-chip biasgen.
     */
    public TCVS320(HardwareInterface hardwareInterface) {
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
    public class TCVS320Extractor extends RetinaExtractor{
        final int XMASK=0x3fe, XSHIFT=1, YMASK=0xff000, YSHIFT=12;
        public TCVS320Extractor(TCVS320 chip){
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
        @Override synchronized public EventPacket extractPacket(AEPacketRaw in) {
            if(out==null){
                out=new EventPacket<PolarityEvent>(chip.getEventClass());
            }else{
                out.clear();
            }
            if(in==null) return out;
            int n=in.getNumEvents(); //addresses.length;
            
            int skipBy=1;
            if(isSubSamplingEnabled()){
                while(n/skipBy>getSubsampleThresholdEventCount()){
                    skipBy++;
                }
            }
            int sxm=sizeX-1;
            int[] a=in.getAddresses();
            int[] timestamps=in.getTimestamps();
            OutputEventIterator outItr=out.outputIterator();
            for(int i=0;i<n;i+=skipBy){ // bug here
                PolarityEvent e=(PolarityEvent)outItr.nextOutput();
                int addr=a[i];
                e.timestamp=(timestamps[i]);
                e.x=(short)(((addr&XMASK)>>>XSHIFT));
                if(e.x<0) e.x=0; else if(e.x>319) 
                    e.x=319; // TODO
                e.y=(short)((addr&YMASK)>>>YSHIFT);
                if(e.y>239) {
//                    log.warning("e.y="+e.y);
                    e.y=239; // TODO fix this
                }else if(e.y<0){
                    e.y=0; // TODO
                }
                e.type=(byte)(addr&1);
                e.polarity=e.type==0? PolarityEvent.Polarity.Off:PolarityEvent.Polarity.On;
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
        try{
            if(getBiasgen()==null)
                setBiasgen(new TCVS320.Biasgen(this));
            else
                getBiasgen().setHardwareInterface((BiasgenHardwareInterface)hardwareInterface);
        }catch(ClassCastException e){
            log.warning(e.getMessage()+": probably this chip object has a biasgen but the hardware interface doesn't, ignoring");
        }
    }
    
//    /** Called when this Tmpdiff128 notified, e.g. by having its AEViewer set
//     @param the calling object
//     @param arg the argument passed
//     */
//    public void update(Observable o, Object arg) {
//        log.info("Tmpdiff128: received update from Observable="+o+", arg="+arg);
//    }
    
//    @Override public void setAeViewer(AEViewer v){
//        super.setAeViewer(v);
//        if(v!=null){
//            JMenuBar b=v.getJMenuBar();
//            int n=b.getMenuCount();
//            for(int i=0;i<n;i++){
//                JMenu m=b.getMenu(i);
//                if(m.getText().equals("Tmpdiff128")){
//                    b.remove(m);
//                }
//            }
//            JMenu m=new JMenu("TCVS320");
//            m.setToolTipText("Specialized menu for TCVS320 chip");
//            JMenuItem mi=new JMenuItem("Reset pixel array");
//            mi.setToolTipText("Applies a momentary reset to the pixel array");
//            mi.addActionListener(new ActionListener(){
//                public void actionPerformed(ActionEvent evt){
//                    HardwareInterface hw=getHardwareInterface();
//                    if(hw==null || !(hw instanceof CypressFX2TmpdiffRetina)) {
//                        log.warning("cannot reset pixels with hardware interface="+hw);
//                        return;
//                    }
//                    log.info("resetting pixels");
//                    CypressFX2TmpdiffRetina retina=(CypressFX2TmpdiffRetina)hw;
//                    retina.resetPixelArray();
//                }
//            });
//            m.add(mi);
////       mi.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, java.awt.event.InputEvent.CTRL_MASK));
//            v.getJMenuBar().add(m);
//        }
//    }
    
    /**
     * Describes ConfigurableIPots on TCVS320 retina chip. These are configured by a shift register as shown here:
     * <p>
     * This bias generator also offers an abstracted FunctionalBiasgen interface that is used for a simplified user interface.
     *
     * @author tobi
     */
    public class Biasgen extends ch.unizh.ini.caviar.biasgen.Biasgen implements FunctionalBiasgen {
        
        private ConfigurableIPot diffOn, diffOff, refr, pr, sf, diff;
        
        /** Creates a new instance of Biasgen for Tmpdiff128 with a given hardware interface
         *@param chip the chip this biasgen belongs to
         */
        public Biasgen(Chip chip) {
            super(chip);
            setName("Tmpdiff128");
            getMasterbias().setKPrimeNFet(500e-6f); // estimated from tox=37A
            getMasterbias().setMultiplier(9*(24f/2.4f)/(4.8f/2.4f));  // masterbias current multiplier according to fet M and W/L
            getMasterbias().setWOverL(4.8f/2.4f);
            
/*
 *@param biasgen
 *@param name
 *@param shiftRegisterNumber the position in the shift register, 0 based, starting on end from which bits are loaded
 *@param type (NORMAL, CASCODE)
 *@param sex Sex (N, P)
     @param lowCurrentModeEnabled bias is normal (false) or in low current mode (true)
     @param enabled bias is enabled (true) or weakly tied to rail (false)
 * @param bitValue initial bitValue
     @param bufferBitValue buffer bias bit value
 *@param displayPosition position in GUI from top (logical order)
 *@param tooltipString a String to display to user of GUI telling them what the pots does
 */
            setPotArray(new IPotArray(this));
            
            getPotArray().addPot(pr=new ConfigurableIPot(this, "Pr", 0,IPot.Type.NORMAL, IPot.Sex.P,false, true, 100,ConfigurableIPot.maxBufferValue,1,"Photoreceptor"));
            getPotArray().addPot(sf=new ConfigurableIPot(this, "foll", 1,IPot.Type.NORMAL, IPot.Sex.P,false, true, 200,ConfigurableIPot.maxBufferValue,3,"Src follower buffer between photoreceptor and differentiator"));
            getPotArray().addPot(diff=new ConfigurableIPot(this, "diff", 2,IPot.Type.NORMAL, IPot.Sex.N,false, true, 1000,ConfigurableIPot.maxBufferValue,4,"Differentiator"));
            getPotArray().addPot(diffOn=new ConfigurableIPot(this, "diffOn", 3,IPot.Type.NORMAL, IPot.Sex.N,false, true, 2000,ConfigurableIPot.maxBufferValue,5,"ON threshold - higher to raise threshold"));
            getPotArray().addPot(diffOff=new ConfigurableIPot(this, "diffOff", 4,IPot.Type.NORMAL, IPot.Sex.N,false, true, 500,ConfigurableIPot.maxBufferValue,6,"OFF threshold, lower to raise threshold"));
            getPotArray().addPot(new ConfigurableIPot(this,"cas", 5, IPot.Type.CASCODE, IPot.Sex.N, false, true, 0,ConfigurableIPot.maxBufferValue, 2, "Photoreceptor cascode"));
            getPotArray().addPot(refr=new ConfigurableIPot(this, "refr", 6,IPot.Type.NORMAL, IPot.Sex.P,false, true, 50,ConfigurableIPot.maxBufferValue, 9, "Refractory period"));
            getPotArray().addPot(refr=new ConfigurableIPot(this, "bulk", 7,IPot.Type.NORMAL, IPot.Sex.N,false, true, 50,ConfigurableIPot.maxBufferValue, 9, "Bulk bias for pixel reset switch"));
            getPotArray().addPot(new ConfigurableIPot(this, "puY", 8,IPot.Type.NORMAL, IPot.Sex.P,false, true, ConfigurableIPot.maxBitValue, ConfigurableIPot.maxBufferValue,10,"1st dimension AER static pullup"));
            getPotArray().addPot(new ConfigurableIPot(this, "puX", 9,IPot.Type.NORMAL, IPot.Sex.P,false, true, 0,ConfigurableIPot.maxBufferValue, 11, "2nd dimension AER static pullup"));
            getPotArray().addPot(new ConfigurableIPot(this, "pdY", 10,IPot.Type.NORMAL, IPot.Sex.N,false, true, 0,ConfigurableIPot.maxBufferValue, 11, "2nd dimension AER static pulldown"));
            getPotArray().addPot(new ConfigurableIPot(this, "puReq", 11, IPot.Type.NORMAL, IPot.Sex.P,false, true, 300,ConfigurableIPot.maxBufferValue, 8, "OFF request inverter bias"));
            getPotArray().addPot(new ConfigurableIPot(this, "pdX", 12, IPot.Type.NORMAL, IPot.Sex.N,false, true, 0,ConfigurableIPot.maxBufferValue, 12, "AER request pulldown"));
            getPotArray().addPot(new ConfigurableIPot(this, "IFThr", 13, IPot.Type.NORMAL, IPot.Sex.N, false, true, 0,ConfigurableIPot.maxBufferValue, 7, "Photocurrent IF neuron threshold"));
            getPotArray().addPot(new ConfigurableIPot(this, "padFoll", 14, IPot.Type.NORMAL, IPot.Sex.N, false, true, 0,ConfigurableIPot.maxBufferValue, 15, "Instrumentation follower pad bias"));
            getPotArray().addPot(new ConfigurableIPot(this, "aePdLimit", 15, IPot.Type.NORMAL, IPot.Sex.N, false, true, 0,ConfigurableIPot.maxBufferValue, 15, "Instrumentation follower pad bias"));

            loadPreferences();
            
        }
        
        /** the change in current from an increase* or decrease* call */
        public final float RATIO=1.05f;
        
        /** the minimum on/diff or diff/off current allowed by decreaseThreshold */
        public final float MIN_THRESHOLD_RATIO=2f;
        
        public final float MAX_DIFF_ON_CURRENT=6e-6f;
        public final float MIN_DIFF_OFF_CURRENT=1e-9f;
        
        synchronized public void increaseThreshold() {
            if(diffOn.getCurrent()*RATIO>MAX_DIFF_ON_CURRENT)  return;
            if(diffOff.getCurrent()/RATIO<MIN_DIFF_OFF_CURRENT) return;
            diffOn.changeByRatio(RATIO);
            diffOff.changeByRatio(1/RATIO);
        }
        
        synchronized public void decreaseThreshold() {
            float diffI=diff.getCurrent();
            if(diffOn.getCurrent()/MIN_THRESHOLD_RATIO<diffI) return;
            if(diffOff.getCurrent()>diffI/MIN_THRESHOLD_RATIO) return;
            diffOff.changeByRatio(RATIO);
            diffOn.changeByRatio(1/RATIO);
        }
        
        synchronized public void increaseRefractoryPeriod() {
            refr.changeByRatio(1/RATIO);
        }
        
        synchronized public void decreaseRefractoryPeriod() {
            refr.changeByRatio(RATIO);
        }
        
        synchronized public void increaseBandwidth() {
            pr.changeByRatio(RATIO);
            sf.changeByRatio(RATIO);
        }
        
        synchronized public void decreaseBandwidth() {
            pr.changeByRatio(1/RATIO);
            sf.changeByRatio(1/RATIO);
        }
        
        synchronized public void moreONType() {
            diffOn.changeByRatio(1/RATIO);
            diffOff.changeByRatio(RATIO);
        }
        
        synchronized public void moreOFFType() {
            diffOn.changeByRatio(RATIO);
            diffOff.changeByRatio(1/RATIO);
        }
        
//        public void setBandwidth(int val) {
//
//        }
//
//        public void setThreshold(int val) {
//            if(val==50) {
//                diffOn.setBitValue(diffOn.getPreferedBitValue());
//                diffOff.setBitValue(diffOff.getPreferedBitValue());
//            }else{
//                float v=val-50;
//                if(v>0) v=v+1; // 1->2, 2->3
//                else v= 1/(-v+1); // -1 -> 1/2, -2 -> 1/3
//                System.out.println("v="+v);
//                diffOn.setBitValue((int)(diffOn.getBitValue()*v));
//                diffOff.setBitValue((int)(diffOff.getBitValue()/v));
//            }
//        }
//
//        public void setMaximumFiringRate(int val) {
//        }
        
        Tmpdiff128FunctionalBiasgenPanel controlPanel=null;
        /** @return a new or existing panel for controlling this bias generator functionally
         */
        public JPanel getControlPanel() {
//            if(controlPanel==null) controlPanel=new Tmpdiff128FunctionalBiasgenPanel(TCVS320.this);
            return controlPanel;
        }
        
        
    } // Tmpdiff128Biasgen
    
    
}
