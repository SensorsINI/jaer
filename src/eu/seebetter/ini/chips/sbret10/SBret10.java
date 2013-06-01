/*
 created 26 Oct 2008 for new cDVSTest chip
 * adapted apr 2011 for cDVStest30 chip by tobi
 * adapted 25 oct 2011 for SeeBetter10/11 chips by tobi
 *
 */
package eu.seebetter.ini.chips.sbret10;

import ch.unizh.ini.jaer.config.cpld.CPLDInt;
import com.sun.opengl.util.j2d.TextRenderer;
import eu.seebetter.ini.chips.APSDVSchip;
import static eu.seebetter.ini.chips.APSDVSchip.ADC_DATA_MASK;
import static eu.seebetter.ini.chips.APSDVSchip.ADC_READCYCLE_MASK;
import static eu.seebetter.ini.chips.APSDVSchip.ADDRESS_TYPE_APS;
import static eu.seebetter.ini.chips.APSDVSchip.ADDRESS_TYPE_DVS;
import static eu.seebetter.ini.chips.APSDVSchip.ADDRESS_TYPE_MASK;
import static eu.seebetter.ini.chips.APSDVSchip.POLMASK;
import static eu.seebetter.ini.chips.APSDVSchip.XMASK;
import static eu.seebetter.ini.chips.APSDVSchip.XSHIFT;
import static eu.seebetter.ini.chips.APSDVSchip.YMASK;
import static eu.seebetter.ini.chips.APSDVSchip.YSHIFT;
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
import net.sf.jaer.eventprocessing.filter.ApsDvsEventFilter;
import net.sf.jaer.eventprocessing.filter.Info;
import net.sf.jaer.eventprocessing.filter.RefractoryFilter;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.ChipRendererDisplayMethodRGBA;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Describes retina and its event extractor and bias generator. Two constructors
 * ara available, the vanilla constructor is used for event playback and the one
 * with a HardwareInterface parameter is useful for live capture.
 * {@link #setHardwareInterface} is used when the hardware interface is
 * constructed after the retina object. The constructor that takes a hardware
 * interface also constructs the biasgen interface.
 * <p>
 * SBRet10 has 240x180 pixels and is built in 180nm technology. It has a rolling
 * shutter APS readout with CDS in digital domain.
 *
 * @author tobi, christian
 */
@Description("SBret version 1.0")
public class SBret10 extends APSDVSchip {

    private final int ADC_NUMBER_OF_TRAILING_ZEROS = Integer.numberOfTrailingZeros(ADC_READCYCLE_MASK); // speedup in loop
    // following define bit masks for various hardware data types. 
    // The hardware interface translateEvents method packs the raw device data into 32 bit 'addresses' and timestamps.
    // timestamps are unwrapped and timestamp resets are handled in translateEvents. Addresses are filled with either AE or ADC data.
    // AEs are filled in according the XMASK, YMASK, XSHIFT, YSHIFT below.
    /**
     * bit masks/shifts for cDVS AE data
     */
    private SBret10DisplayMethod sbretDisplayMethod = null;
    private AEFrameChipRenderer apsDVSrenderer;
    private int exposure; // internal measured variable, set during rendering
    private int frameTime; // internal measured variable, set during rendering
    protected float frameRateHz; // holds measured variable in Hz for GUI rendering of rate
    protected float exposureMs; // holds measured variable in ms for GUI rendering
    private boolean snapshot = false;
    private boolean resetOnReadout = false;
    SBret10DisplayControlPanelold displayControlPanel = null;
    private SBret10config config;
    JFrame controlFrame = null;
    public static short WIDTH = 240;
    public static short HEIGHT = 180;
    int sx1 = getSizeX() - 1, sy1 = getSizeY() - 1;
    private int autoshotThresholdEvents=getPrefs().getInt("SBRet10.autoshotThresholdEvents",0);

    /**
     * Creates a new instance of cDVSTest20.
     */
    public SBret10() {
        setName("SBret10");
        setDefaultPreferencesFile("../../biasgenSettings/sbret10/SBRet10.xml");
        setEventClass(ApsDvsEvent.class);
        setSizeX(WIDTH);
        setSizeY(HEIGHT);
        setNumCellTypes(3); // two are polarity and last is intensity
        setPixelHeightUm(18.5f);
        setPixelWidthUm(18.5f);

        setEventExtractor(new SBret10Extractor(this));

        setBiasgen(config = new SBret10config(this));

        // hardware interface is ApsDvsHardwareInterface

        apsDVSrenderer = new AEFrameChipRenderer(this);
        apsDVSrenderer.setMaxADC(MAX_ADC);
        setRenderer(apsDVSrenderer);

        sbretDisplayMethod = new SBret10DisplayMethod(this);
        getCanvas().addDisplayMethod(sbretDisplayMethod);
        getCanvas().setDisplayMethod(sbretDisplayMethod);
        addDefaultEventFilter(ApsDvsEventFilter.class);
        addDefaultEventFilter(HotPixelSupressor.class);
        addDefaultEventFilter(RefractoryFilter.class);
        addDefaultEventFilter(Info.class);


    }

    @Override
    public void setPowerDown(boolean powerDown) {
        config.powerDown.set(powerDown);
        try {
            config.sendOnChipConfigChain();
        } catch (HardwareInterfaceException ex) {
            Logger.getLogger(SBret10.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Creates a new instance of SBRet10
     *
     * @param hardwareInterface an existing hardware interface. This constructor
     * is preferred. It makes a new cDVSTest10Biasgen object to talk to the
     * on-chip biasgen.
     */
    public SBret10(HardwareInterface hardwareInterface) {
        this();
        setHardwareInterface(hardwareInterface);
    }

//    int pixcnt=0; // TODO debug
    /**
     * The event extractor. Each pixel has two polarities 0 and 1.
     *
     * <p>
     * The bits in the raw data coming from the device are as follows.
     * <p>
     * Bit 0 is polarity, on=1, off=0<br>
     * Bits 1-9 are x address (max value 320)<br>
     * Bits 10-17 are y address (max value 240) <br>
     * <p>
     */
    public class SBret10Extractor extends RetinaExtractor {

        private int firstFrameTs = 0;
        private int autoshotEventsSinceLastShot=0; // autoshot counter

        public SBret10Extractor(SBret10 chip) {
            super(chip);
        }

        private void lastADCevent() {
            //releases the reset after the readout of a frame if the DVS is suppressed during the DVS readout
            if (resetOnReadout) {
                config.nChipReset.set(true);
            }
        }

        /**
         * extracts the meaning of the raw events.
         *
         * @param in the raw events, can be null
         * @return out the processed events. these are partially processed
         * in-place. empty packet is returned if null is supplied as in.
         */
        @Override
        synchronized public EventPacket extractPacket(AEPacketRaw in) {
            if (!(chip instanceof APSDVSchip)) {
                return null;
            }
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
            sx1 = chip.getSizeX() - 1;
            sy1 = chip.getSizeY() - 1;

            int[] datas = in.getAddresses();
            int[] timestamps = in.getTimestamps();
            OutputEventIterator outItr = out.outputIterator();

            // at this point the raw data from the USB IN packet has already been digested to extract timestamps, including timestamp wrap events and timestamp resets.
            // The datas array holds the data, which consists of a mixture of AEs and ADC values.
            // Here we extract the datas and leave the timestamps alone.


            // TODO entire rendering / processing approach is not very efficient now

            for (int i = 0; i < n; i++) {  // TODO implement skipBy/subsampling, but without missing the frame start/end events and still delivering frames
                int data = datas[i];

                if ((data & ADDRESS_TYPE_MASK) == ADDRESS_TYPE_DVS) {
                    //DVS event
                    ApsDvsEvent e = (ApsDvsEvent) outItr.nextOutput();
                    e.adcSample = -1; // TODO hack to mark as not an ADC sample
                    e.startOfFrame = false;
                    e.special = false;
                    e.address = data;
                    e.timestamp = (timestamps[i]);
                    e.polarity = (data & POLMASK) == POLMASK ? ApsDvsEvent.Polarity.On : ApsDvsEvent.Polarity.Off;
                    e.x = (short) (sx1 - ((data & XMASK) >>> XSHIFT));
                    e.y = (short) ((data & YMASK) >>> YSHIFT);
                    //System.out.println(data);
                    // autoshot triggering
                    autoshotEventsSinceLastShot++; // number DVS events captured here
                } else if ((data & ADDRESS_TYPE_MASK) == ADDRESS_TYPE_APS) {
                    //APS event
                    ApsDvsEvent e = (ApsDvsEvent) outItr.nextOutput();
                    e.adcSample = data & ADC_DATA_MASK;
                    int sampleType = (data & ADC_READCYCLE_MASK) >> ADC_NUMBER_OF_TRAILING_ZEROS;
                    switch (sampleType) {
                        case 0:
                            e.readoutType = ApsDvsEvent.ReadoutType.ResetRead;
                            break;
                        case 1:
                            e.readoutType = ApsDvsEvent.ReadoutType.SignalRead;
                            //log.info("got SignalRead event");
                            break;
                        case 3:
                            log.warning("Event with readout cycle null was sent out!");
                            break;
                        default:
                            log.warning("Event with unknown readout cycle was sent out!");
                    }
                    e.special = false;
                    e.timestamp = (timestamps[i]);
                    e.address = data;
                    e.x = (short) (((data & XMASK) >>> XSHIFT));
                    e.y = (short) ((data & YMASK) >>> YSHIFT);
                    boolean pixZero = e.x == 0 && e.y == 0;
                    e.startOfFrame = (e.readoutType == ApsDvsEvent.ReadoutType.ResetRead) && pixZero;
                    if (e.startOfFrame) {
                        //if(pixCnt!=129600) System.out.println("New frame, pixCnt was incorrectly "+pixCnt+" instead of 129600 but this could happen at end of file");
                        frameTime = e.timestamp - firstFrameTs;
                        firstFrameTs = e.timestamp;
                    }
                    if (pixZero && e.isSignalRead()) {
                        exposure = e.timestamp - firstFrameTs;
                    }
                    if (e.isSignalRead() && e.x == 0 && e.y == sy1) {
                        // if we use ResetRead+SignalRead+C readout, OR, if we use ResetRead-SignalRead readout and we are at last APS pixel, then write EOF event
                        lastADCevent(); // TODO what does this do?
                        //insert a new "end of frame" event not present in original data
                        ApsDvsEvent a = (ApsDvsEvent) outItr.nextOutput();
                        a.startOfFrame = false;
                        a.adcSample = 0; // set this effectively as ADC sample even though fake
                        a.timestamp = (timestamps[i]);
                        a.x = -1;
                        a.y = -1;
                        a.readoutType = ApsDvsEvent.ReadoutType.EOF;
                        if (snapshot) {
                            snapshot = false;
                            config.apsReadoutControl.setAdcEnabled(false);
                        }
                    }
                }
            }
            if(getAutoshotThresholdEvents()>0 && autoshotEventsSinceLastShot>getAutoshotThresholdEvents()){
                takeSnapshot();
                autoshotEventsSinceLastShot=0;
            }
            return out;
        } // extractPacket

        @Override
        public AEPacketRaw reconstructRawPacket(EventPacket packet) {
            if (raw == null) {
                raw = new AEPacketRaw();
            }
            if (!(packet instanceof ApsDvsEventPacket)) {
                return null;
            }
            ApsDvsEventPacket apsDVSpacket = (ApsDvsEventPacket) packet;
            raw.ensureCapacity(packet.getSize());
            raw.setNumEvents(0);
            int[] a = raw.addresses;
            int[] ts = raw.timestamps;
            int n = apsDVSpacket.getSize();
            Iterator evItr = apsDVSpacket.fullIterator();
            int k = 0;
            while (evItr.hasNext()) {
                ApsDvsEvent e = (ApsDvsEvent) evItr.next();
                // not writing out these EOF events (which were synthesized on extraction) results in reconstructed packets with giant time gaps, reason unknown
                if (e.isEndOfFrame()) {
                    continue;  // these EOF events were synthesized from data in first place
                }
                ts[k] = e.timestamp;
                a[k++] = reconstructRawAddressFromEvent(e);
            }
            raw.setNumEvents(k);
            return raw;
        }

        /**
         * To handle filtered ApsDvsEvents, this method rewrites the fields of
         * the raw address encoding x and y addresses to reflect the event's x
         * and y fields.
         *
         * @param e the ApsDvsEvent
         * @return the raw address
         */
        @Override
        public int reconstructRawAddressFromEvent(TypedEvent e) {
            int address = e.address;
//            if(e.x==0 && e.y==0){
//                log.info("start of frame event "+e);
//            }
//            if(e.x==-1 && e.y==-1){
//                log.info("end of frame event "+e);
//            }
            // e.x came from  e.x = (short) (chip.getSizeX()-1-((data & XMASK) >>> XSHIFT)); // for DVS event, no x flip if APS event
            if (((ApsDvsEvent) e).adcSample >= 0) {
                address = (address & ~XMASK) | ((e.x) << XSHIFT);
            } else {
                address = (address & ~XMASK) | ((sx1 - e.x) << XSHIFT);
            }
            // e.y came from e.y = (short) ((data & YMASK) >>> YSHIFT);
            address = (address & ~YMASK) | (e.y << YSHIFT);
            return address;
        }
    } // extractor

    /**
     * overrides the Chip setHardware interface to construct a biasgen if one
     * doesn't exist already. Sets the hardware interface and the bias
     * generators hardware interface
     *
     * @param hardwareInterface the interface
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
     * Displays data from SeeBetter test chip SeeBetter10/11.
     *
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
            if (config.videoControl != null && config.videoControl.displayFrames) {
                GL gl = drawable.getGL();
                exposureRender(gl);
            }
        }

        private void exposureRender(GL gl) {
            gl.glPushMatrix();
            exposureRenderer.begin3DRendering();  // TODO make string rendering more efficient here using String.format or StringBuilder
            String frequency = "";
            if (frameTime > 0) {
                setFrameRateHz((float) 1000000 / frameTime);
                frequency = String.format("(%.2f Hz)", frameRateHz);
            }
            setExposureMs((float) exposure / 1000);
            exposureRenderer.draw3D("exposure: " + exposureMs + " ms, frame period: " + (float) frameTime / 1000 + " ms " + frequency, 0, HEIGHT + 1, 0, .4f); // x,y,z, scale factor 
            exposureRenderer.end3DRendering();
            gl.glPopMatrix();
        }
    }

    /**
     * Returns the preferred DisplayMethod, or ChipRendererDisplayMethod if null
     * preference.
     *
     * @return the method, or null.
     * @see #setPreferredDisplayMethod
     */
    @Override
    public DisplayMethod getPreferredDisplayMethod() {
        return new ChipRendererDisplayMethodRGBA(getCanvas());
    }

    @Override
    public int getMaxADC() {
        return MAX_ADC;
    }

    /**
     * Sets the measured frame rate. Does not change parameters, only used for
     * recording measured quantity and informing GUI listeners.
     *
     * @param frameRateHz the frameRateHz to set
     */
    private void setFrameRateHz(float frameRateHz) {
        float old = this.frameRateHz;
        this.frameRateHz = frameRateHz;
        getSupport().firePropertyChange(PROPERTY_FRAME_RATE_HZ, old, this.frameRateHz);
    }

    /**
     * Sets the measured exposure. Does not change parameters, only used for
     * recording measured quantity.
     *
     * @param exposureMs the exposureMs to set
     */
    private void setExposureMs(float exposureMs) {
        float old = this.exposureMs;
        this.exposureMs = exposureMs;
        getSupport().firePropertyChange(PROPERTY_EXPOSURE_MS, old, this.exposureMs);
    }

    @Override
    public float getFrameRateHz() {
        return frameRateHz;
    }

    @Override
    public float getExposureMs() {
        return exposureMs;
    }

    /**
     * Triggers shot of one APS frame
     */
    @Override
    public void takeSnapshot() {
        snapshot = true;
        config.apsReadoutControl.setAdcEnabled(true);
    }

    /** Sets threshold for shooting a frame automatically
     * 
     * @param thresholdEvents the number of events to trigger shot on. Less than or equal to zero disables auto-shot. 
     */
    @Override
    public void setAutoshotThresholdEvents(int thresholdEvents) {
        if(thresholdEvents<0) thresholdEvents=0;
        autoshotThresholdEvents=thresholdEvents;
        getPrefs().putInt("SBret10.autoshotThresholdEvents",thresholdEvents);
        if(autoshotThresholdEvents==0) config.runAdc.set(true);
    }

    /** Returns threshold for auto-shot.
     * 
     * @return events to shoot frame 
     */
    @Override
    public int getAutoshotThresholdEvents() {
        return autoshotThresholdEvents;
    }
}
