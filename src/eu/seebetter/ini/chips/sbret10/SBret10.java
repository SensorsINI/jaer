/*
 created 26 Oct 2008 for new cDVSTest chip
 * adapted apr 2011 for cDVStest30 chip by tobi
 * adapted 25 oct 2011 for SeeBetter10/11 chips by tobi
 *
 */
package eu.seebetter.ini.chips.sbret10;

import static ch.unizh.ini.jaer.chip.retina.DVS128.FIRMWARE_CHANGELOG;
import static ch.unizh.ini.jaer.chip.retina.DVS128.HELP_URL_RETINA;
import static ch.unizh.ini.jaer.chip.retina.DVS128.USER_GUIDE_URL_RETINA;
import java.awt.Font;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.swing.JFrame;

import net.sf.jaer.Description;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.chip.RetinaExtractor;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.eventprocessing.filter.ApsDvsEventFilter;
import net.sf.jaer.eventprocessing.filter.Info;
import net.sf.jaer.eventprocessing.filter.RefractoryFilter;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.ChipRendererDisplayMethodRGBA;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import ch.unizh.ini.jaer.projects.spatiatemporaltracking.data.histogram.AbstractHistogram;

import com.jogamp.opengl.util.awt.TextRenderer;

import eu.seebetter.ini.chips.ApsDvsChip;
import eu.seebetter.ini.chips.sbret10.IMUSample.IncompleteIMUSampleException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Rectangle2D;
import java.util.Observable;
import java.util.Observer;
import javax.media.opengl.glu.GLU;
import javax.media.opengl.glu.GLUquadric;
import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.ApsDvsHardwareInterface;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2DVS128HardwareInterface;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.HasLEDControl;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.HasResettablePixelArray;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.HasSyncEventOutput;
import net.sf.jaer.util.HexString;
import net.sf.jaer.util.RemoteControlCommand;
import net.sf.jaer.util.RemoteControlled;
import net.sf.jaer.util.WarningDialogWithDontShowPreference;

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
public class SBret10 extends ApsDvsChip implements RemoteControlled, Observer {

    private JMenu chipMenu = null;
    private JMenuItem syncEnabledMenuItem = null;
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
    /**
     *holds measured variable in Hz for GUI rendering of rate
     */
    protected float frameRateHz;
    /**
     * holds measured variable in ms for GUI rendering
     */
    protected float exposureMs;
    /**
     * Holds count of frames obtained by end of frame events
     */
    private int frameCount=0;
    private boolean snapshot = false;
    private boolean resetOnReadout = false;
    private SBret10config config;
    JFrame controlFrame = null;
    public static final short WIDTH = 240;
    public static final short HEIGHT = 180;
    int sx1 = getSizeX() - 1, sy1 = getSizeY() - 1;
    private int autoshotThresholdEvents=getPrefs().getInt("SBRet10.autoshotThresholdEvents",0);
    private IMUSample imuSample; // latest IMUSample from sensor 
    private final String CMD_EXPOSURE="exposure";
    private final String CMD_EXPOSURE_CC="exposureCC";
    private final String CMD_RS_SETTLE_CC="resetSettleCC";

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
        addDefaultEventFilter(HotPixelFilter.class);
        addDefaultEventFilter(RefractoryFilter.class);
        addDefaultEventFilter(Info.class);
        
       if (getRemoteControl() != null) {
            getRemoteControl().addCommandListener(this, CMD_EXPOSURE, CMD_EXPOSURE + " val - sets exposure. val in ms.");
            getRemoteControl().addCommandListener(this, CMD_EXPOSURE_CC, CMD_EXPOSURE_CC + " val - sets exposure. val in clock cycles");
            getRemoteControl().addCommandListener(this, CMD_RS_SETTLE_CC, CMD_RS_SETTLE_CC + " val - sets reset settling time. val in clock cycles");
        }
       addObserver(this);  // we observe ourselves so that if hardware interface for example calls notifyListeners we get informed
    }

    @Override
    public String processRemoteControlCommand(RemoteControlCommand command, String input) {
        log.info("processing RemoteControlCommand " + command + " with input=" + input);
        if (command == null) {
            return null;
        }
        String[] tokens = input.split(" ");
        if (tokens.length < 2) {
            return input + ": unknown command - did you forget the argument?";
        }
        if (tokens[1] == null || tokens[1].length() == 0) {
            return input + ": argument too short - need a number";
        }
        float v = 0;
        try {
            v = Float.parseFloat(tokens[1]);
        } catch (NumberFormatException e) {
            return input + ": bad argument? Caught " + e.toString();
        }
        String c = command.getCmdName();
        if (c.equals(CMD_EXPOSURE)) {
            config.setExposureDelayMs((int) v);
        } else if(c.equals(CMD_EXPOSURE_CC)) {
            config.exposure.set((int) v);
        } else if(c.equals(CMD_RS_SETTLE_CC)) {
            config.resSettle.set((int) v);
        } else {
            return input + ": unknown command";
        }
        return "successfully processed command " + input;
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
        private int warningCount=0;
        private static final int  WARNING_COUNT_DIVIDER=10000;

        public SBret10Extractor(SBret10 chip) {
            super(chip);
        }

        private void lastADCevent() {
            //releases the reset after the readout of a frame if the DVS is suppressed during the DVS readout
            if (resetOnReadout) {
                config.nChipReset.set(true);
            }
        }

        private IncompleteIMUSampleException incompleteIMUSampleException=null;
        private static final int IMU_WARNING_INTERVAL=1000;
        private int missedImuSampleCounter=0;
        private int badImuDataCounter=0;
        
        /**
         * extracts the meaning of the raw events.
         *
         * @param in the raw events, can be null
         * @return out the processed events. these are partially processed
         * in-place. empty packet is returned if null is supplied as in.
         */
        @Override
        synchronized public EventPacket extractPacket(AEPacketRaw in) {
            if (!(chip instanceof ApsDvsChip)) {
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
            // NOTE we must make sure we write ApsDvsEvents when we want them, not reuse the IMUSamples

            // at this point the raw data from the USB IN packet has already been digested to extract timestamps, including timestamp wrap events and timestamp resets.
            // The datas array holds the data, which consists of a mixture of AEs and ADC values.
            // Here we extract the datas and leave the timestamps alone.


            // TODO entire rendering / processing approach is not very efficient now
//            System.out.println("Extracting new packet "+out);
            for (int i = 0; i < n; i++) {  // TODO implement skipBy/subsampling, but without missing the frame start/end events and still delivering frames
                int data = datas[i];
                
                if (incompleteIMUSampleException != null || (ApsDvsChip.ADDRESS_TYPE_IMU & data) == ApsDvsChip.ADDRESS_TYPE_IMU) {
                    if (IMUSample.extractSampleTypeCode(data) == 0) { /// only start getting an IMUSample at code 0, the first sample type
                        try {
                            IMUSample possibleSample = IMUSample.constructFromAEPacketRaw(in, i, incompleteIMUSampleException);
                            i += IMUSample.SIZE_EVENTS;
                            incompleteIMUSampleException = null;
                            imuSample = possibleSample;  // asking for sample from AEChip now gives this value, but no access to intermediate IMU samples
                            imuSample.imuSampleEvent = true;
                            outItr.writeToNextOutput(imuSample); // also write the event out to the next output event slot
//                           System.out.println("at position "+(out.size-1)+" put "+imuSample); 
                            continue;
                        } catch (IMUSample.IncompleteIMUSampleException ex) {
                            incompleteIMUSampleException = ex;
                            if (missedImuSampleCounter++ % IMU_WARNING_INTERVAL == 0) {
                                log.warning(String.format("%s (obtained %d partial samples so far)", ex.toString(), missedImuSampleCounter));
                            }
                            break; // break out of loop because this packet only contained part of an IMUSample and formed the end of the packet anyhow. Next time we come back here we will complete the IMUSample
                        } catch (IMUSample.BadIMUDataException ex2) {
                            if (badImuDataCounter++ % IMU_WARNING_INTERVAL == 0) {
                                log.warning(String.format("%s (%d bad samples so far)", ex2.toString(), badImuDataCounter));
                            }
                            incompleteIMUSampleException = null;
                            continue; // continue because there may be other data
                        }
                    }
                    
                } else if ((data & ApsDvsChip.ADDRESS_TYPE_MASK) == ApsDvsChip.ADDRESS_TYPE_DVS) {
                    //DVS event
                    ApsDvsEvent e = nextApsDvsEvent(outItr);
                    if ((data & ApsDvsChip.TRIGGERMASK) == ApsDvsChip.TRIGGERMASK) {
                        e.adcSample = -1; // TODO hack to mark as not an ADC sample
                        e.special = true; // TODO special is set here when capturing frames which will mess us up if this is an IMUSample used as a plain ApsDvsEvent
                        e.address = data;
                        e.timestamp = (timestamps[i]);
                        e.setIsDVS(true);
                    } else {
                        e.adcSample = -1; // TODO hack to mark as not an ADC sample
                        e.special = false;
                        e.address = data;
                        e.timestamp = (timestamps[i]);
                        e.polarity = (data & POLMASK) == POLMASK ? ApsDvsEvent.Polarity.On : ApsDvsEvent.Polarity.Off;
                        e.type = (byte) ((data & POLMASK) == POLMASK ? 1 : 0);
                        e.x = (short) (sx1 - ((data & XMASK) >>> XSHIFT));
                        e.y = (short) ((data & YMASK) >>> YSHIFT);
                        e.setIsDVS(true);
                        //System.out.println(data);
                        // autoshot triggering
                        autoshotEventsSinceLastShot++; // number DVS events captured here
                    }
                } else if ((data & ApsDvsChip.ADDRESS_TYPE_MASK) == ApsDvsChip.ADDRESS_TYPE_APS) {
                    //APS event
                    ApsDvsEvent e = nextApsDvsEvent(outItr);
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
                            if ((warningCount < 10) || ((warningCount % WARNING_COUNT_DIVIDER) == 0)) {
                                log.warning("Event with unknown readout cycle was sent out! You might be reading a file that had the deprecated C readout mode enabled.");
                            }
                            warningCount++;
                    }
                    e.special = false;
                    e.timestamp = (timestamps[i]);
                    e.address = data;
                    e.x = (short) (((data & XMASK) >>> XSHIFT));
                    e.y = (short) ((data & YMASK) >>> YSHIFT);
                    e.type = (byte) (2);
                    boolean pixZero = (e.x == sx1) && (e.y == sy1);//first event of frame (addresses get flipped)
                    if ((e.readoutType == ApsDvsEvent.ReadoutType.ResetRead) && pixZero) {
                        createApsFlagEvent(outItr,ApsDvsEvent.ReadoutType.SOF,timestamps[i]);
                        if(!config.chipConfigChain.configBits[6].isSet()){
                            //rolling shutter start of exposure (SOE)
                            createApsFlagEvent(outItr,ApsDvsEvent.ReadoutType.SOE,timestamps[i]);
                            frameTime = e.timestamp - firstFrameTs;
                            firstFrameTs = e.timestamp;
                        }
                    }    
                    if (config.chipConfigChain.configBits[6].isSet() && e.isResetRead() && (e.x == 0) && (e.y == sy1)) {
                        //global shutter start of exposure (SOE)
                        createApsFlagEvent(outItr,ApsDvsEvent.ReadoutType.SOE,timestamps[i]);
                        frameTime = e.timestamp - firstFrameTs;
                        firstFrameTs = e.timestamp;
                    }
                    //end of exposure
                    if (pixZero && e.isSignalRead()) {
                        createApsFlagEvent(outItr,ApsDvsEvent.ReadoutType.EOE,timestamps[i]);
                        exposure = e.timestamp - firstFrameTs;
                    }
                    if (e.isSignalRead() && (e.x == 0) && (e.y == 0)) {
                        // if we use ResetRead+SignalRead+C readout, OR, if we use ResetRead-SignalRead readout and we are at last APS pixel, then write EOF event
                        lastADCevent();
                        //insert a new "end of frame" event not present in original data
                        createApsFlagEvent(outItr,ApsDvsEvent.ReadoutType.EOF,timestamps[i]);
                        if (snapshot) {
                            snapshot = false;
                            config.apsReadoutControl.setAdcEnabled(false);
                        }
                        setFrameCount(getFrameCount() + 1);
                    }
                }
            }
            if ((getAutoshotThresholdEvents() > 0) && (autoshotEventsSinceLastShot > getAutoshotThresholdEvents())) {
                takeSnapshot();
                autoshotEventsSinceLastShot = 0;
            }
//            int imuEventCount=0, realImuEventCount=0;
//            for(Object e:out){
//                if(e instanceof IMUSample){
//                    imuEventCount++;
//                    IMUSample i=(IMUSample)e;
//                    if(i.imuSampleEvent) realImuEventCount++;
//                }
//            }
//            System.out.println(String.format("packet has \ttotal %d, \timu type=%d, \treal imu data=%d events", out.getSize(), imuEventCount, realImuEventCount));
            return out;
        } // extractPacket

        // TODO hack to reuse IMUSample events as ApsDvsEvents holding only APS or DVS data by using the special flags
        private ApsDvsEvent nextApsDvsEvent(OutputEventIterator outItr) {
            ApsDvsEvent e = (ApsDvsEvent) outItr.nextOutput();
            e.special = false;
            if(e instanceof IMUSample) ((IMUSample)e).imuSampleEvent=false;
            return e;
        }
        
        /** creates a special ApsDvsEvent in output packet just for flagging APS frame markers such as start of frame, reset, end of frame.
         * 
         * @param outItr
         * @param flag
         * @param timestamp
         * @return 
         */
        private ApsDvsEvent createApsFlagEvent(OutputEventIterator outItr, ApsDvsEvent.ReadoutType flag, int timestamp) {
            ApsDvsEvent a = nextApsDvsEvent(outItr);
            a.adcSample = 0; // set this effectively as ADC sample even though fake
            a.timestamp = timestamp;
            a.x = -1;
            a.y = -1;
            a.readoutType = flag;
            return a;
        }
        
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
        private static final int FONTSIZE = 10;
        private static final int FRAME_COUNTER_BAR_LENGTH_FRAMES=10;

        private TextRenderer exposureRenderer = null;

        public SBret10DisplayMethod(SBret10 chip) {
            super(chip.getCanvas());
        }

        @Override
        public void display(GLAutoDrawable drawable) {
            if (exposureRenderer == null) {
                exposureRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, FONTSIZE), true, true);
                exposureRenderer.setColor(1, 1, 1, 1);
            }
            super.display(drawable);
            if (config.syncTimestampMasterEnabled.isSet() == false) {
                exposureRenderer.begin3DRendering();  // TODO make string rendering more efficient here using String.format or StringBuilder
                exposureRenderer.draw3D("Slave camera", 0, -(FONTSIZE / 2), 0, .5f); // x,y,z, scale factor
                exposureRenderer.end3DRendering();
            }
            if ((config.videoControl != null) && config.videoControl.displayFrames) {
                GL2 gl = drawable.getGL().getGL2();
                exposureRender(gl);
            }
            // draw sample histogram
            if(showImageHistogram && (renderer instanceof AEFrameChipRenderer)){
//                System.out.println("drawing hist");
                final int size=100;
                AbstractHistogram hist=((AEFrameChipRenderer)renderer).getAdcSampleValueHistogram();
                hist.draw(drawable,
                        exposureRenderer,
                        (sizeX/2)-(size/2), (sizeY/2)+(size/2), size, size);
            }

            if(showIMU && (chip instanceof SBret10)){
                IMUSample imuSample=((SBret10)chip).getImuSample();
                if(imuSample!=null){
                    imuRender(drawable, imuSample);
                }
            }
        }

        TextRenderer imuTextRenderer=new TextRenderer(new Font("SansSerif", Font.PLAIN, 36));
    GLU glu=null;
    GLUquadric accelCircle=null;

        private void imuRender(GLAutoDrawable drawable, IMUSample imuSample) {
//            System.out.println("on rendering: "+imuSample.toString());
            GL2 gl = drawable.getGL().getGL2();
            gl.glPushMatrix();
            gl.glTranslatef(chip.getSizeX()/2,chip.getSizeY()/2,0);
            gl.glLineWidth(3);

            final float vectorScale=.8f;
            final float textScale = .2f;
            final float trans = .7f;
            float x,y;
            // gyro pan/tilt
            gl.glColor3f(1, 0, 0);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(0, 0);
            x = (vectorScale * imuSample.getGyroYawY() * HEIGHT) / IMUSample.FULL_SCALE_GYRO_DEG_PER_SEC;
            y = (vectorScale * imuSample.getGyroTiltX() * HEIGHT) / IMUSample.FULL_SCALE_GYRO_DEG_PER_SEC;
            gl.glVertex2f(x, y);
            gl.glEnd();

            imuTextRenderer.begin3DRendering();
            imuTextRenderer.setColor(1, 0, 0, trans);
            imuTextRenderer.draw3D(String.format("%.2f,%.2f dps", imuSample.getGyroYawY()+5, imuSample.getGyroTiltX()), x, y, 0, textScale); // x,y,z, scale factor
            imuTextRenderer.end3DRendering();

            // gyro roll
            x = (vectorScale * imuSample.getGyroRollZ() * HEIGHT) / IMUSample.FULL_SCALE_GYRO_DEG_PER_SEC;
            y = chip.getSizeY() * .25f;
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(0, y);
            gl.glVertex2f(x, y);
            gl.glEnd();
            imuTextRenderer.begin3DRendering();
            imuTextRenderer.setColor(1, 0, 0, trans);
            imuTextRenderer.draw3D(String.format("%.2f dps", imuSample.getGyroRollZ()), x, y, 0, textScale); // x,y,z, scale factor
            imuTextRenderer.end3DRendering();

            //acceleration x,y
            x = (vectorScale * imuSample.getAccelX() * HEIGHT) / IMUSample.FULL_SCALE_ACCEL_G/2;
            y = (vectorScale * imuSample.getAccelY() * HEIGHT) / IMUSample.FULL_SCALE_ACCEL_G/2;
            gl.glColor3f(0, 1, 0);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(0, 0);
            gl.glVertex2f(x, y);
            gl.glEnd();
            imuTextRenderer.begin3DRendering();
            imuTextRenderer.setColor(0,1,0, trans);
            imuTextRenderer.draw3D(String.format("%.2f,%.2f g", imuSample.getAccelX(), imuSample.getAccelY()), x, y, 0, textScale); // x,y,z, scale factor
            imuTextRenderer.end3DRendering();

            // acceleration z, drawn as circle
            if (glu == null) {
                glu = new GLU();
            }
            if (accelCircle == null) {
                accelCircle = glu.gluNewQuadric();
            }
            final float az = (vectorScale * imuSample.getAccelZ() * HEIGHT/2) / IMUSample.FULL_SCALE_ACCEL_G/2;
            final float rim = .5f;
            glu.gluQuadricDrawStyle(accelCircle, GLU.GLU_FILL);
            glu.gluDisk(accelCircle, az - rim, az + rim, 16, 1);
            imuTextRenderer.begin3DRendering();
            imuTextRenderer.setColor(0, 1, 0, trans);
            final String saz=String.format("%.2f g", imuSample.getAccelZ());
            Rectangle2D rect=imuTextRenderer.getBounds(saz);
            imuTextRenderer.draw3D(saz, az, -(float)rect.getHeight()*textScale*0.5f, 0, textScale); // x,y,z, scale factor
            imuTextRenderer.end3DRendering();
         
            // color annotation to show what is being rendered
            imuTextRenderer.begin3DRendering();
//            imuTextRenderer.setColor(1,0,0, trans);
//            imuTextRenderer.draw3D("G", -6, -6,0, textScale); // x,y,z, scale factor
//            imuTextRenderer.setColor(0,1,0, trans);
//            imuTextRenderer.draw3D("A", +6, -6,0, textScale); // x,y,z, scale factor
            imuTextRenderer.setColor(1, 1, 1, trans);
            final String ratestr = String.format("IMU: timestamp=%-+9.3fs last dtMs=%-6.1fms  avg dtMs=%-6.1fms", 1e-6f*imuSample.getTimestampUs(), imuSample.getDeltaTimeUs() * .001f, IMUSample.getAverageSampleIntervalUs() / 1000);
            Rectangle2D raterect = imuTextRenderer.getBounds(ratestr);
            imuTextRenderer.draw3D(ratestr, -(float) raterect.getWidth() * textScale * 0.5f *.7f, -12, 0, textScale*.7f); // x,y,z, scale factor

            imuTextRenderer.end3DRendering();
          gl.glPopMatrix();
         }

        private void exposureRender(GL2 gl) {
            gl.glPushMatrix();
            exposureRenderer.begin3DRendering();  // TODO make string rendering more efficient here using String.format or StringBuilder
            if (frameTime > 0) {
                setFrameRateHz((float) 1000000 / frameTime);
            }
            setExposureMs((float) exposure / 1000);
            String s=String.format("Frame: %d; Exposure %.2f ms; Frame rate: %.2f Hz", getFrameCount(),exposureMs,frameRateHz);
            exposureRenderer.draw3D(s, 0, HEIGHT + (FONTSIZE/2), 0, .5f); // x,y,z, scale factor
            exposureRenderer.end3DRendering();
            int nframes=frameCount%FRAME_COUNTER_BAR_LENGTH_FRAMES;
            int rectw=WIDTH/FRAME_COUNTER_BAR_LENGTH_FRAMES;
            gl.glColor4f(1,1,1,.5f);
            for(int i=0;i<nframes;i++){
                gl.glRectf(nframes*rectw, HEIGHT+1, ((nframes+1)*rectw)-3, (HEIGHT+(FONTSIZE/2))-1);
            }
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
    * Returns the frame counter. This value is set on each end-of-frame sample.
    *
     * @return the frameCount
     */
    public int getFrameCount() {
        return frameCount;
    }

    /**
     * Sets the frame counter.
     * @param frameCount the frameCount to set
     */
    public void setFrameCount(int frameCount) {
        this.frameCount = frameCount;
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
        if(thresholdEvents<0) {
			thresholdEvents=0;
		}
        autoshotThresholdEvents=thresholdEvents;
        getPrefs().putInt("SBret10.autoshotThresholdEvents",thresholdEvents);
        if(autoshotThresholdEvents==0) {
			config.runAdc.set(true);
		}
    }

    /** Returns threshold for auto-shot.
     *
     * @return events to shoot frame
     */
    @Override
    public int getAutoshotThresholdEvents() {
        return autoshotThresholdEvents;
    }

    @Override
    public void setAutoExposureEnabled(boolean yes) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean isAutoExposureEnabled() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private boolean showImageHistogram=getPrefs().getBoolean("SBRet10.showImageHistogram", false);
    @Override
	public boolean isShowImageHistogram(){
        return showImageHistogram;
    }
    @Override
	public void setShowImageHistogram(boolean yes){
        showImageHistogram=yes;
        getPrefs().putBoolean("SBRet10.showImageHistogram", yes);
    }

    /** Controls exposure automatically to try to optimize captured gray levels
     *
     */
    private class AutoExposureController { // TODO not implemented yet

        public void controlExposure(){
            AbstractHistogram hist=apsDVSrenderer.getAdcSampleValueHistogram();

        }
    }

      /**
     * Returns the current Inertial Measurement Unit sample.
     * @return the imuSample, or null if there is no sample
     */
    public IMUSample getImuSample() {
        return imuSample;
    }

     private boolean showIMU=getPrefs().getBoolean("SBRet10.showIMU", false);
  @Override
    public void setShowIMU(boolean yes) {
        showIMU=yes;
        getPrefs().putBoolean("SBRet10.showIMU",showIMU);
    }

    @Override
    public boolean isShowIMU() {
        return showIMU;
    }
    
        /**
     * Updates AEViewer specialized menu items according to capabilities of
     * HardwareInterface.
     *
     * @param o the observable, i.e. this Chip.
     * @param arg the argument (e.g. the HardwareInterface).
     */
    @Override
    public void update(Observable o, Object arg) {
        if (o == config.syncTimestampMasterEnabled) {
            if (syncEnabledMenuItem != null) {
                syncEnabledMenuItem.setSelected(config.syncTimestampMasterEnabled.isSet());
            }
        }
    }


    /**
     * Enables or disable DVS128 menu in AEViewer
     *
     * @param yes true to enable it
     */
    private void enableChipMenu(boolean yes) {
        if (yes) {
            if (chipMenu == null) {
                chipMenu = new JMenu(this.getClass().getSimpleName());
                chipMenu.getPopupMenu().setLightWeightPopupEnabled(false); // to paint on GLCanvas
                chipMenu.setToolTipText("Specialized menu for chip");
            }


            if (syncEnabledMenuItem == null) {
                syncEnabledMenuItem = new JCheckBoxMenuItem("Timestamp master / Enable sync event output");
                syncEnabledMenuItem.setToolTipText("<html>Sets this device as timestamp master and enables sync event generation on external IN pin falling edges (disables slave clock input).<br>Falling edges inject special sync events with bitmask " + HexString.toString(ApsDvsHardwareInterface.SYNC_EVENT_BITMASK) + " set<br>These events are not rendered but are logged and can be used to synchronize an external signal to the recorded data.<br>If you are only using one camera, enable this option.<br>If you want to synchronize two DVS128, disable this option in one of the cameras and connect the OUT pin of the master to the IN pin of the slave and also connect the two GND pins.");

                syncEnabledMenuItem.addActionListener(new ActionListener() {

                    public void actionPerformed(ActionEvent evt) {
                        log.info("setting sync/timestamp master to "+syncEnabledMenuItem.isSelected());
                        config.syncTimestampMasterEnabled.set(syncEnabledMenuItem.isSelected());
                    }
                });
                syncEnabledMenuItem.setSelected(config.syncTimestampMasterEnabled.isSet());
                chipMenu.add(syncEnabledMenuItem);
                config.syncTimestampMasterEnabled.addObserver(this);
            }

            if (getAeViewer() != null) {
                getAeViewer().setMenu(chipMenu);
            }

        } else { // disable menu
            if (chipMenu != null) {
                getAeViewer().removeMenu(chipMenu);
            }
        }
    }

    
    @Override
    public void onDeregistration() {
        super.onDeregistration();
        if (getAeViewer() == null) {
            return;
        }

        enableChipMenu(false);
    }

    @Override
    public void onRegistration() {
        super.onRegistration();
        if (getAeViewer() == null) {
            return;
        }
        enableChipMenu(true);
    }


}
