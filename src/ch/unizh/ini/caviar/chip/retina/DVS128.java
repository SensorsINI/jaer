/*
 * DVS128.java
 *
 * Created on October 5, 2005, 11:36 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.caviar.chip.retina;

import ch.unizh.ini.caviar.aemonitor.*;
import ch.unizh.ini.caviar.biasgen.*;
import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.graphics.*;
import ch.unizh.ini.caviar.hardwareinterface.*;
import ch.unizh.ini.caviar.hardwareinterface.usb.CypressFX2TmpdiffRetinaHardwareInterface;
import ch.unizh.ini.caviar.hardwareinterface.usb.linux.CypressFX2RetinaLinux;
import java.awt.BorderLayout;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;
import javax.swing.JPanel;


/**
 * Describes DVS128 retina and its event extractor and bias generator.
 * This camera is the Tmpdiff128 chip with certain biases tied to the rails to enhance AE bus bandwidth and
 * it achieves about 2 Meps, as opposed to the approx 500 keps using the onchip Tmpdiff128 biases.
 * <p>
 * Two constructors ara available, the vanilla constructor is used for event playback and the
 *one with a HardwareInterface parameter is useful for live capture.
 * {@link #setHardwareInterface} is used when the hardware interface is constructed after the retina object.
 *The constructor that takes a hardware interface also constructs the biasgen interface.
 *
 * @author tobi
 */
public class DVS128 extends AERetina implements Serializable {
    
    /** Creates a new instance of DVS128. No biasgen is constructed for this constructor, because there is no hardware interface defined. */
    public DVS128() {
        setName("DVS128");
        setSizeX(128);
        setSizeY(128);
        setNumCellTypes(2);
        setPixelHeightUm(40);
        setPixelWidthUm(40);
        setEventExtractor(new Extractor(this));
        setBiasgen(new DVS128.Biasgen(this));
    }
    
    /** Creates a new instance of DVS128
     * @param hardwareInterface an existing hardware interface. This constructer is preferred. It makes a new Biasgen object to talk to the on-chip biasgen.
     */
    public DVS128(HardwareInterface hardwareInterface) {
        this();
        setHardwareInterface(hardwareInterface);
    }
    
    /** the event extractor for DVS128. DVS128 has two polarities 0 and 1. Here the polarity is flipped by the extractor so that the raw polarity 0 becomes 1
     in the extracted event. The ON events have raw polarity 0.
     1 is an ON event after event extraction, which flips the type. Raw polarity 1 is OFF event, which becomes 0 after extraction.
     */
    public class Extractor extends RetinaExtractor implements java.io.Serializable{
        final short XMASK=0xfe, XSHIFT=1, YMASK=0x7f00, YSHIFT=8;
        public Extractor(DVS128 chip){
            super(chip);
            setXmask((short)0x00fe);
            setXshift((byte)1);
            setYmask((short)0x7f00);
            setYshift((byte)8);
            setTypemask((short)1);
            setTypeshift((byte)0);
            setFlipx(true);
            setFlipy(false);
            setFliptype(true);
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
                e.x=(short)(sxm-((short)((addr&XMASK)>>>XSHIFT)));
                e.y=(short)((addr&YMASK)>>>YSHIFT);
                e.type=(byte)(1-addr&1);
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
                setBiasgen(new DVS128.Biasgen(this));
            else
                getBiasgen().setHardwareInterface((BiasgenHardwareInterface)hardwareInterface);
        }catch(ClassCastException e){
            System.err.println(e.getMessage()+": probably this chip object has a biasgen but the hardware interface doesn't, ignoring");
        }
    }
    
//    /** Called when this DVS128 notified, e.g. by having its AEViewer set
//     @param the calling object
//     @param arg the argument passed
//     */
//    public void update(Observable o, Object arg) {
//        log.info("DVS128: received update from Observable="+o+", arg="+arg);
//    }
    
    @Override public void setAeViewer(AEViewer v){
        super.setAeViewer(v);
        if(v!=null){
            JMenuBar b=v.getJMenuBar();
            int n=b.getMenuCount();
            for(int i=0;i<n;i++){
                JMenu m=b.getMenu(i);
                if(m.getText().equals("DVS128")){
                    b.remove(m);
                }
            }
            JMenu m=new JMenu("DVS128");
            m.setToolTipText("Specialized menu for DVS128 chip");
            JMenuItem mi=new JMenuItem("Reset pixel array");
            mi.setToolTipText("Applies a momentary reset to the pixel array");
            mi.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent evt){
                    HardwareInterface hw=getHardwareInterface();
                    if(hw==null || !(hw instanceof CypressFX2TmpdiffRetinaHardwareInterface || hw instanceof CypressFX2RetinaLinux)) {
                        log.warning("cannot reset pixels with hardware interface="+hw);
                        return;
                    }
                    log.info("resetting pixels");
                    if (hw instanceof CypressFX2TmpdiffRetinaHardwareInterface)
                        ((CypressFX2TmpdiffRetinaHardwareInterface)hw).resetPixelArray();
                    if (hw instanceof CypressFX2RetinaLinux)
                        ((CypressFX2RetinaLinux)hw).resetPixelArray();       
                }
            });
            m.add(mi);
//       mi.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, java.awt.event.InputEvent.CTRL_MASK));
            v.getJMenuBar().add(m);
        }
    }
    
    /**
     * Describes IPots on DVS128 retina chip. These are configured by a shift register as shown here:
     *<p>
     *<img src="doc-files/tmpdiff128biasgen.gif" alt="tmpdiff128 shift register arrangement"/>
     
     <p>
     This bias generator also offers an abstracted ChipControlPanel interface that is used for a simplified user interface.
     *
     * @author tobi
     */
    public class Biasgen extends ch.unizh.ini.caviar.biasgen.Biasgen implements ChipControlPanel {
        
        private IPot diffOn, diffOff, refr, pr, sf, diff;
        
        /** Creates a new instance of Biasgen for DVS128 with a given hardware interface
         *@param chip the chip this biasgen belongs to
         */
        public Biasgen(Chip chip) {
            super(chip);
            setName("DVS128");
            
            
//  /** Creates a new instance of IPot
//     *@param biasgen
//     *@param name
//     *@param shiftRegisterNumber the position in the shift register, 0 based, starting on end from which bits are loaded
//     *@param type (NORMAL, CASCODE)
//     *@param sex Sex (N, P)
//     * @param bitValue initial bitValue
//     *@param displayPosition position in GUI from top (logical order)
//     *@param tooltipString a String to display to user of GUI telling them what the pots does
//     */
////    public IPot(Biasgen biasgen, String name, int shiftRegisterNumber, final Type type, Sex sex, int bitValue, int displayPosition, String tooltipString) {
            
            // create potArray according to our needs
            setPotArray(new IPotArray(this));
            
            getPotArray().addPot(new IPot(this,"cas", 11, IPot.Type.CASCODE, IPot.Sex.N, 0, 2, "Photoreceptor cascode"));
            getPotArray().addPot(new IPot(this, "injGnd", 10, IPot.Type.CASCODE, IPot.Sex.P, 0, 7, "Differentiator switch level, higher to turn on more"));
            getPotArray().addPot(new IPot(this, "reqPd", 9, IPot.Type.NORMAL, IPot.Sex.N,0, 12, "AER request pulldown"));
            getPotArray().addPot(new IPot(this, "puX", 8,IPot.Type.NORMAL, IPot.Sex.P,0, 11, "2nd dimension AER static pullup"));
            getPotArray().addPot(diffOff=new IPot(this, "diffOff", 7,IPot.Type.NORMAL, IPot.Sex.N,0,6,"OFF threshold, lower to raise threshold"));
            getPotArray().addPot(new IPot(this, "req", 6, IPot.Type.NORMAL, IPot.Sex.N,0, 8, "OFF request inverter bias"));
            getPotArray().addPot(refr=new IPot(this, "refr", 5,IPot.Type.NORMAL, IPot.Sex.P,0, 9, "Refractory period"));
            getPotArray().addPot(new IPot(this, "puY", 4,IPot.Type.NORMAL, IPot.Sex.P,0, 10,"1st dimension AER static pullup"));
            getPotArray().addPot(diffOn=new IPot(this, "diffOn", 3,IPot.Type.NORMAL, IPot.Sex.N,0,5,"ON threshold - higher to raise threshold"));
            getPotArray().addPot(diff=new IPot(this, "diff", 2,IPot.Type.NORMAL, IPot.Sex.N,0,4,"Differentiator"));
            getPotArray().addPot(sf=new IPot(this, "foll", 1,IPot.Type.NORMAL, IPot.Sex.P,0,3,"Src follower buffer between photoreceptor and differentiator"));
            getPotArray().addPot(pr=new IPot(this, "Pr", 0,IPot.Type.NORMAL, IPot.Sex.P,0,1,"Photoreceptor"));
            
            loadPreferences();
            
        }
     /** sends the ipot values over the hardware interface if there is not a batch edit occuring.
     *@param biasgen the bias generator object.
     * This parameter is necessary because the same method is used in the hardware interface,
     * which doesn't know about the particular bias generator instance.
     *@throws HardwareInterfaceException if there is a hardware error. If there is no interface, prints a message and just returns.
     *@see #startBatchEdit
     *@see #endBatchEdit
     **/
    public void sendConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        if(hardwareInterface==null){
//            log.warning("Biasgen.sendIPotValues(): no hardware interface");
            return;
        }
        if(!isBatchEditOccurring() && hardwareInterface!=null ) {
//            log.info("calling hardwareInterface.sendConfiguration");
//            hardwareInterface.se(this);
        }
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
                
        /** @return a new panel for controlling this bias generator functionally
         */
        @Override
        public JPanel buildControlPanel() {
            JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout());
            JTabbedPane pane = new JTabbedPane();

            pane.addTab("Biases", super.buildControlPanel());
            pane.addTab("User friendly controls", new DVS128FunctionalBiasgenPanel(DVS128.this));
            panel.add(pane, BorderLayout.CENTER);
            return panel;
        }
        
        
    } // Tmpdiff128Biasgen
    
    
}
