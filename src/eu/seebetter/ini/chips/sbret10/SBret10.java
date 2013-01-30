/*
created 26 Oct 2008 for new cDVSTest chip
 * adapted apr 2011 for cDVStest30 chip by tobi
 * adapted 25 oct 2011 for SeeBetter10/11 chips by tobi
 *
 */
package eu.seebetter.ini.chips.sbret10;

import com.sun.opengl.util.j2d.TextRenderer;
import eu.seebetter.ini.chips.APSDVSchip;
import java.awt.Font;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.swing.JFrame;
import net.sf.jaer.Description;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.chip.RetinaExtractor;
import net.sf.jaer.event.*;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.ChipRendererDisplayMethodRGBA;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

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
 * @author tobi, christian
 */
@Description("SBret version 1.0")
public class SBret10 extends APSDVSchip {
    
    // following define bit masks for various hardware data types. 
    // The hardware interface translateEvents method packs the raw device data into 32 bit 'addresses' and timestamps.
    // timestamps are unwrapped and timestamp resets are handled in translateEvents. Addresses are filled with either AE or ADC data.
    // AEs are filled in according the XMASK, YMASK, XSHIFT, YSHIFT below.
    /**
     * bit masks/shifts for cDVS  AE data
     */
    private SBret10DisplayMethod sbretDisplayMethod = null;
    private AEFrameChipRenderer apsDVSrenderer;
    private int exposureB;
    private int exposureC;
    private int frameTime;
    private boolean ignoreReadout;
    private boolean snapshot = false;
    private boolean resetOnReadout = false;
    SBret10DisplayControlPanelold displayControlPanel = null;
    private SBret10config config;
    JFrame controlFrame = null;
    public static short WIDTH = 240;
    public static short HEIGHT = 180;

    /** Creates a new instance of cDVSTest20.  */
    public SBret10() {
        setName("SBret10");
        setEventClass(ApsDvsEvent.class);
        setSizeX(WIDTH);
        setSizeY(HEIGHT);
        setNumCellTypes(3); // two are polarity and last is intensity
        setPixelHeightUm(18.5f);
        setPixelWidthUm(18.5f);

        setEventExtractor(new SBret10Extractor(this));

        setBiasgen(config = new SBret10config(this));

        apsDVSrenderer = new AEFrameChipRenderer(this);
        apsDVSrenderer.setMaxADC(MAX_ADC);
        setRenderer(apsDVSrenderer);

        sbretDisplayMethod = new SBret10DisplayMethod(this);
        getCanvas().addDisplayMethod(sbretDisplayMethod);
        getCanvas().setDisplayMethod(sbretDisplayMethod);

    }
    
    @Override
    public void setPowerDown(boolean powerDown){
        config.powerDown.set(powerDown);
        try {
            config.sendOnChipConfigChain();
        } catch (HardwareInterfaceException ex) {
            Logger.getLogger(SBret10.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /** Creates a new instance of SBRet10
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
        boolean ignore = false;
        
        public SBret10Extractor(SBret10 chip) {
            super(chip);
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
            if(!(chip instanceof APSDVSchip))return null;
            if (out == null) {
                out = new ApsDvsEventPacket(chip.getEventClass());
            } else {
                out.clear();
            }
            out.setRawPacket(in);
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

                if ((data & ADDRESS_TYPE_MASK) == ADDRESS_TYPE_DVS) {
                    //DVS event
                    if(!ignore){
                        ApsDvsEvent e = (ApsDvsEvent) outItr.nextOutput();
                        e.adcSample = -1; // TODO hack to mark as not an ADC sample
                        e.startOfFrame = false;
                        e.special = false;
                        e.address = data;
                        e.timestamp = (timestamps[i]);
                        e.polarity = (data & POLMASK) == POLMASK ? ApsDvsEvent.Polarity.On : ApsDvsEvent.Polarity.Off;
                        e.x = (short) (chip.getSizeX()-1-((data & XMASK) >>> XSHIFT));
                        e.y = (short) ((data & YMASK) >>> YSHIFT); 
                        //System.out.println(data);
                    } 
                } else if ((data & ADDRESS_TYPE_MASK) == ADDRESS_TYPE_APS) {
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
                    e.x = (short) (((data & XMASK) >>> XSHIFT));
                    e.y = (short) ((data & YMASK) >>> YSHIFT); 
                    e.startOfFrame = (e.readoutType == ApsDvsEvent.ReadoutType.A && e.x == 0 && e.y == 0);
                    if(e.startOfFrame){
                        //if(pixCnt!=129600) System.out.println("New frame, pixCnt was incorrectly "+pixCnt+" instead of 129600 but this could happen at end of file");
                        if(ignoreReadout){
                            ignore = true;
                        }
                        frameTime = e.timestamp - firstFrameTs;
                        firstFrameTs = e.timestamp;
                    }
                    if(e.isB() && e.x == 0 && e.y == 0){
                        exposureB = e.timestamp-firstFrameTs;
                    }
                    if(e.isC() && e.x == 0 && e.y == 0){
                        exposureC = e.timestamp-firstFrameTs;
                    }
                    if(((config.useC.isSet() && e.isC()) || (!config.useC.isSet() && e.isB()))  && e.x == (short)(chip.getSizeX()-1) && e.y == (short)(chip.getSizeY()-1)){
                        lastADCevent();
                        //insert and end of frame event
                        ApsDvsEvent a = (ApsDvsEvent) outItr.nextOutput();
                        a.adcSample = 0;
                        a.timestamp = (timestamps[i]);
                        a.x = -1;
                        a.y = -1;
                        a.readoutType = ApsDvsEvent.ReadoutType.EOF;
                        if(snapshot){
                            snapshot = false;
                            config.apsReadoutControl.setAdcEnabled(false);
                        }
                    }
                }
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
        SBret10config config;
        try {
            if (getBiasgen() == null) {
                setBiasgen(config = new SBret10config(this));
                // now we can addConfigValue the control panel

            } else {
                getBiasgen().setHardwareInterface((BiasgenHardwareInterface) hardwareInterface);
            }

        } catch (ClassCastException e) {
            log.warning(e.getMessage() + ": probably this chip object has a biasgen but the hardware interface doesn't, ignoring");
        }
    }

    
    
    /**
     * @return the displayLogIntensity
     */
    public void takeSnapshot() {
        snapshot = true;
        config.apsReadoutControl.setAdcEnabled(true);
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

    /**
     * Displays data from SeeBetter test chip SeeBetter10/11.
     * @author Tobi
     */
    public class SBret10DisplayMethod extends ChipRendererDisplayMethodRGBA {

        private TextRenderer exposureRenderer = null;

        public SBret10DisplayMethod(SBret10 chip) {
            super(chip.getCanvas());
        }

        @Override
        public void display(GLAutoDrawable drawable) {
            if (exposureRenderer == null) {
                exposureRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 8), true, true);
                exposureRenderer.setColor(1, 1, 1, 1);
            }
            super.display(drawable);
            if(config.videoControl != null && config.videoControl.displayFrames){
                GL gl = drawable.getGL();
                exposureRender(gl); 
            }
        }

        private void exposureRender(GL gl) {
            gl.glPushMatrix();
            exposureRenderer.begin3DRendering();
            String frequency = "";
            if(frameTime>0){
                frequency = "("+(float)1000000/frameTime+" Hz)";
            }
            String expC = "";
            if(config.useC.isSet()){
                expC = " ms, exposure 2: "+(float)exposureC/1000;
            }
            exposureRenderer.draw3D("exposure 1: "+(float)exposureB/1000+expC+" ms, frame period: "+(float)frameTime/1000+" ms "+frequency, 0, HEIGHT+1, 0, .4f); // x,y,z, scale factor 
            exposureRenderer.end3DRendering();
            gl.glPopMatrix();
        }
    }

    
    /** Returns the preferred DisplayMethod, or ChipRendererDisplayMethod if null preference.
     *
     * @return the method, or null.
     * @see #setPreferredDisplayMethod
     */
    @Override
    public DisplayMethod getPreferredDisplayMethod() {
        return new ChipRendererDisplayMethodRGBA(getCanvas());
    }

    @Override
    public int getMaxADC (){
        return MAX_ADC;
    }
    
}
