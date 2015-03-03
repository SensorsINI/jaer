/*
 * created 26 Oct 2008 for new cDVSTest chip
 * adapted apr 2011 for cDVStest30 chip by tobi
 * adapted 25 oct 2011 for SeeBetter10/11 chips by tobi
 */
package eu.seebetter.ini.chips.davis;

import static ch.unizh.ini.jaer.chip.retina.DVS128.FIRMWARE_CHANGELOG;
import static ch.unizh.ini.jaer.chip.retina.DVS128.HELP_URL_RETINA;
import static ch.unizh.ini.jaer.chip.retina.DVS128.USER_GUIDE_URL_RETINA;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeSupport;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.glu.GLU;
import javax.media.opengl.glu.GLUquadric;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

import net.sf.jaer.Description;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.chip.RetinaExtractor;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.ChipRendererDisplayMethodRGBA;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.util.HasPropertyTooltips;
import net.sf.jaer.util.PropertyTooltipSupport;
import net.sf.jaer.util.RemoteControlCommand;
import net.sf.jaer.util.RemoteControlled;
import net.sf.jaer.util.histogram.AbstractHistogram;
import net.sf.jaer.util.histogram.SimpleHistogram;
import ch.unizh.ini.jaer.config.cpld.CPLDInt;

import com.jogamp.opengl.util.awt.TextRenderer;

import eu.seebetter.ini.chips.ApsDvsChip;
import eu.seebetter.ini.chips.davis.IMUSample.IncompleteIMUSampleException;
import javax.swing.JComponent;

/**
 * <p>
 * DAViS240a/b have 240x180 pixels and are built in 180nm technology. DAViS240a
 * has a rolling shutter APS readout and DAViS240b has global shutter readout
 * (but rolling shutter also possible with DAViS240b with different CPLD logic).
 * Both do APS CDS in digital domain off-chip, on host side, using difference
 * between reset and signal reads.
 * <p>
 *
 * Describes retina and its event extractor and bias generator. Two constructors
 * ara available, the vanilla constructor is used for event playback and the one
 * with a HardwareInterface parameter is useful for live capture.
 * {@link #setHardwareInterface} is used when the hardware interface is
 * constructed after the retina object. The constructor that takes a hardware
 * interface also constructs the biasgen interface.
 *
 * @author tobi, christian
 */
@Description("DAVIS240 base class for 240x180 pixel APS-DVS DAVIS sensor")
abstract public class DAVIS240BaseCamera extends ApsDvsChip implements RemoteControlled, Observer {

    private JMenu chipMenu = null;
    private JMenuItem syncEnabledMenuItem = null;
    private boolean isTimestampMaster = true;
    public static final String HELP_URL_RETINA = "http://inilabs.com/support/overview-of-dynamic-vision-sensors";
    public static final String USER_GUIDE_URL_RETINA = "http://www.inilabs.com/support/davis240";
    public static final String USER_GUIDE_URL_FLASHY = "https://docs.google.com/a/longi.li/document/d/1LuO-i8u-Y7Nf0zQ-N-Z2LRiKiQMO-EkD3Ln2bmnjhmQ/edit?usp=sharing";
//    public static final String FIRMWARE_CHANGELOG = "https://sourceforge.net/p/jaer/code/HEAD/tree/devices/firmware/CypressFX2/firmware_FX2LP_DVS128/CHANGELOG.txt";
    JComponent helpMenuItem1 = null, helpMenuItem2 = null, helpMenuItem3 = null;

    private final int ADC_NUMBER_OF_TRAILING_ZEROS = Integer.numberOfTrailingZeros(ApsDvsChip.ADC_READCYCLE_MASK);
	// speedup in loop
    // following define bit masks for various hardware data types.
    // The hardware interface translateEvents method packs the raw device data into 32 bit 'addresses' and timestamps.
    // timestamps are unwrapped and timestamp resets are handled in translateEvents. Addresses are filled with either AE
    // or ADC data.
    // AEs are filled in according the XMASK, YMASK, XSHIFT, YSHIFT below.
    /**
     * bit masks/shifts for cDVS AE data
     */
    private DAViS240DisplayMethod davisDisplayMethod = null;
    private final AEFrameChipRenderer apsDVSrenderer;
    private int frameExposureStartTimestampUs = 0; // timestamp of first sample from frame (first sample read after
    // reset released)
    private int frameExposureEndTimestampUs; // end of exposure (first events of signal read)
    private int exposureDurationUs; // internal measured variable, set during rendering. Duration of frame expsosure in
    // us.
    private int frameIntervalUs; // internal measured variable, set during rendering. Time between this frame and
    // previous one.
    /**
     * holds measured variable in Hz for GUI rendering of rate
     */
    protected float frameRateHz;
    /**
     * holds measured variable in ms for GUI rendering
     */
    protected float exposureMs;
    /**
     * Holds count of frames obtained by end of frame events
     */
    private int frameCount = 0;
    private boolean snapshot = false;
    private DAViS240Config config;
    JFrame controlFrame = null;
    public static final short WIDTH = 240;
    public static final short HEIGHT = 180;
    int sx1 = getSizeX() - 1, sy1 = getSizeY() - 1;
    private int autoshotThresholdEvents = getPrefs().getInt("DAViS240.autoshotThresholdEvents", 0);
    private IMUSample imuSample; // latest IMUSample from sensor
    private final String CMD_EXPOSURE = "exposure";
    private final String CMD_EXPOSURE_CC = "exposureCC";
    private final String CMD_RS_SETTLE_CC = "resetSettleCC";

    private final AutoExposureController autoExposureController = new AutoExposureController();

    /**
     * Creates a new instance of cDVSTest20.
     */
    public DAVIS240BaseCamera() {
        setName("DAVIS240BaseCamera");
        setDefaultPreferencesFile("biasgenSettings/Davis240a/David240aBasic.xml");
        setEventClass(ApsDvsEvent.class);
        setSizeX(DAVIS240BaseCamera.WIDTH);
        setSizeY(DAVIS240BaseCamera.HEIGHT);
        setNumCellTypes(3); // two are polarity and last is intensity
        setPixelHeightUm(18.5f);
        setPixelWidthUm(18.5f);

        setEventExtractor(new DAViS240Extractor(this));

        setBiasgen(config = new DAViS240Config(this));

        // hardware interface is ApsDvsHardwareInterface
        apsDVSrenderer = new AEFrameChipRenderer(this);
        apsDVSrenderer.setMaxADC(ApsDvsChip.MAX_ADC);
        setRenderer(apsDVSrenderer);

        davisDisplayMethod = new DAViS240DisplayMethod(this);
        getCanvas().addDisplayMethod(davisDisplayMethod);
        getCanvas().setDisplayMethod(davisDisplayMethod);

        if (getRemoteControl() != null) {
            getRemoteControl()
                    .addCommandListener(this, CMD_EXPOSURE, CMD_EXPOSURE + " val - sets exposure. val in ms.");
            getRemoteControl().addCommandListener(this, CMD_EXPOSURE_CC,
                    CMD_EXPOSURE_CC + " val - sets exposure. val in clock cycles");
            getRemoteControl().addCommandListener(this, CMD_RS_SETTLE_CC,
                    CMD_RS_SETTLE_CC + " val - sets reset settling time. val in clock cycles");
        }
        addObserver(this); // we observe ourselves so that if hardware interface for example calls notifyListeners we

        // get informed
    }

    @Override
    public String processRemoteControlCommand(final RemoteControlCommand command, final String input) {
        Chip.log.info("processing RemoteControlCommand " + command + " with input=" + input);
        if (command == null) {
            return null;
        }
        final String[] tokens = input.split(" ");
        if (tokens.length < 2) {
            return input + ": unknown command - did you forget the argument?";
        }
        if ((tokens[1] == null) || (tokens[1].length() == 0)) {
            return input + ": argument too short - need a number";
        }
        float v = 0;
        try {
            v = Float.parseFloat(tokens[1]);
        } catch (final NumberFormatException e) {
            return input + ": bad argument? Caught " + e.toString();
        }
        final String c = command.getCmdName();
        if (c.equals(CMD_EXPOSURE)) {
            config.setExposureDelayMs((int) v);
        } else if (c.equals(CMD_EXPOSURE_CC)) {
            config.exposure.set((int) v);
        } else if (c.equals(CMD_RS_SETTLE_CC)) {
            config.resSettle.set((int) v);
        } else {
            return input + ": unknown command";
        }
        return "successfully processed command " + input;
    }

    @Override
    public void setPowerDown(final boolean powerDown) {
        config.powerDown.set(powerDown);
        try {
            config.sendOnChipConfigChain();
        } catch (final HardwareInterfaceException ex) {
            Logger.getLogger(DAVIS240BaseCamera.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void setADCEnabled(final boolean adcEnabled) {
        config.apsReadoutControl.setAdcEnabled(adcEnabled);
    }

    /**
     * Creates a new instance of DAViS240
     *
     * @param hardwareInterface an existing hardware interface. This constructor
     * is preferred. It makes a new cDVSTest10Biasgen object to talk to the
     * on-chip biasgen.
     */
    public DAVIS240BaseCamera(final HardwareInterface hardwareInterface) {
        this();
        setHardwareInterface(hardwareInterface);
    }

    @Override
    public void controlExposure() {
        getAutoExposureController().controlExposure();
    }

    /**
     * @return the autoExposureController
     */
    public AutoExposureController getAutoExposureController() {
        return autoExposureController;
    }

    // int pixcnt=0; // TODO debug
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
    public class DAViS240Extractor extends RetinaExtractor {

        /**
         *
         */
        private static final long serialVersionUID = 3890914720599660376L;
        private int autoshotEventsSinceLastShot = 0; // autoshot counter
        private int warningCount = 0;
        private static final int WARNING_COUNT_DIVIDER = 10000;

        public DAViS240Extractor(final DAVIS240BaseCamera chip) {
            super(chip);
        }

        private IncompleteIMUSampleException incompleteIMUSampleException = null;
        private static final int IMU_WARNING_INTERVAL = 1000;
        private int missedImuSampleCounter = 0;
        private int badImuDataCounter = 0;

        /**
         * extracts the meaning of the raw events.
         *
         * @param in the raw events, can be null
         * @return out the processed events. these are partially processed
         * in-place. empty packet is returned if null is supplied as in.
         */
        @Override
        synchronized public EventPacket extractPacket(final AEPacketRaw in) {
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
            final int n = in.getNumEvents(); // addresses.length;
            sx1 = chip.getSizeX() - 1;
            sy1 = chip.getSizeY() - 1;

            final int[] datas = in.getAddresses();
            final int[] timestamps = in.getTimestamps();
            final OutputEventIterator outItr = out.outputIterator();
			// NOTE we must make sure we write ApsDvsEvents when we want them, not reuse the IMUSamples

			// at this point the raw data from the USB IN packet has already been digested to extract timestamps,
            // including timestamp wrap events and timestamp resets.
            // The datas array holds the data, which consists of a mixture of AEs and ADC values.
            // Here we extract the datas and leave the timestamps alone.
            // TODO entire rendering / processing approach is not very efficient now
            // System.out.println("Extracting new packet "+out);
            for (int i = 0; i < n; i++) { // TODO implement skipBy/subsampling, but without missing the frame start/end
                // events and still delivering frames
                final int data = datas[i];

                if ((incompleteIMUSampleException != null)
                        || ((ApsDvsChip.ADDRESS_TYPE_IMU & data) == ApsDvsChip.ADDRESS_TYPE_IMU)) {
                    if (IMUSample.extractSampleTypeCode(data) == 0) { // / only start getting an IMUSample at code 0,
                        // the first sample type
                        try {
                            final IMUSample possibleSample = IMUSample.constructFromAEPacketRaw(in, i,
                                    incompleteIMUSampleException);
                            i += IMUSample.SIZE_EVENTS - 1;
                            incompleteIMUSampleException = null;
                            imuSample = possibleSample; // asking for sample from AEChip now gives this value, but no
                            // access to intermediate IMU samples
                            imuSample.imuSampleEvent = true;
                            outItr.writeToNextOutput(imuSample); // also write the event out to the next output event
                            // slot
                            continue;
                        } catch (final IMUSample.IncompleteIMUSampleException ex) {
                            incompleteIMUSampleException = ex;
                            if ((missedImuSampleCounter++ % DAViS240Extractor.IMU_WARNING_INTERVAL) == 0) {
                                Chip.log.warning(String.format("%s (obtained %d partial samples so far)",
                                        ex.toString(), missedImuSampleCounter));
                            }
                            break; // break out of loop because this packet only contained part of an IMUSample and
                            // formed the end of the packet anyhow. Next time we come back here we will complete
                            // the IMUSample
                        } catch (final IMUSample.BadIMUDataException ex2) {
                            if ((badImuDataCounter++ % DAViS240Extractor.IMU_WARNING_INTERVAL) == 0) {
                                Chip.log.warning(String.format("%s (%d bad samples so far)", ex2.toString(),
                                        badImuDataCounter));
                            }
                            incompleteIMUSampleException = null;
                            continue; // continue because there may be other data
                        }
                    }

                } else if ((data & ApsDvsChip.ADDRESS_TYPE_MASK) == ApsDvsChip.ADDRESS_TYPE_DVS) {
                    // DVS event
                    final ApsDvsEvent e = nextApsDvsEvent(outItr);
                    if ((data & ApsDvsChip.EVENT_TYPE_MASK) == ApsDvsChip.EXTERNAL_INPUT_EVENT_ADDR) {
                        e.adcSample = -1; // TODO hack to mark as not an ADC sample
                        e.special = true; // TODO special is set here when capturing frames which will mess us up if
                        // this is an IMUSample used as a plain ApsDvsEvent
                        e.address = data;
                        e.timestamp = (timestamps[i]);
                        e.setIsDVS(true);
                    } else {
                        e.adcSample = -1; // TODO hack to mark as not an ADC sample
                        e.special = false;
                        e.address = data;
                        e.timestamp = (timestamps[i]);
                        e.polarity = (data & ApsDvsChip.POLMASK) == ApsDvsChip.POLMASK ? ApsDvsEvent.Polarity.On
                                : ApsDvsEvent.Polarity.Off;
                        e.type = (byte) ((data & ApsDvsChip.POLMASK) == ApsDvsChip.POLMASK ? 1 : 0);
                        e.x = (short) (sx1 - ((data & ApsDvsChip.XMASK) >>> ApsDvsChip.XSHIFT));
                        e.y = (short) ((data & ApsDvsChip.YMASK) >>> ApsDvsChip.YSHIFT);
                        e.setIsDVS(true);
						// System.out.println(data);
                        // autoshot triggering
                        autoshotEventsSinceLastShot++; // number DVS events captured here
                    }
                } else if ((data & ApsDvsChip.ADDRESS_TYPE_MASK) == ApsDvsChip.ADDRESS_TYPE_APS) {
					// APS event
                    // We first calculate the positions, so we can put events such as StartOfFrame at their
                    // right place, before the actual APS event denoting (0, 0) for example.
                    final int timestamp = timestamps[i];

                    final short x = (short) (((data & ApsDvsChip.XMASK) >>> ApsDvsChip.XSHIFT));
                    final short y = (short) ((data & ApsDvsChip.YMASK) >>> ApsDvsChip.YSHIFT);

                    final boolean pixFirst = firstFrameAddress(x, y); // First event of frame (addresses get flipped)
                    final boolean pixLast = lastFrameAddress(x, y); // Last event of frame (addresses get flipped)

                    ApsDvsEvent.ReadoutType readoutType = ApsDvsEvent.ReadoutType.Null;
                    switch ((data & ApsDvsChip.ADC_READCYCLE_MASK) >> ADC_NUMBER_OF_TRAILING_ZEROS) {
                        case 0:
                            readoutType = ApsDvsEvent.ReadoutType.ResetRead;
                            break;

                        case 1:
                            readoutType = ApsDvsEvent.ReadoutType.SignalRead;
                            break;

                        case 3:
                            Chip.log.warning("Event with readout cycle null was sent out!");
                            break;

                        default:
                            if ((warningCount < 10) || ((warningCount % DAViS240Extractor.WARNING_COUNT_DIVIDER) == 0)) {
                                Chip.log
                                        .warning("Event with unknown readout cycle was sent out! You might be reading a file that had the deprecated C readout mode enabled.");
                            }
                            warningCount++;
                            break;
                    }

                    if (pixFirst && (readoutType == ApsDvsEvent.ReadoutType.ResetRead)) {
                        createApsFlagEvent(outItr, ApsDvsEvent.ReadoutType.SOF, timestamp);

                        if (!config.chipConfigChain.configBits[6].isSet()) {
                            // rolling shutter start of exposure (SOE)
                            createApsFlagEvent(outItr, ApsDvsEvent.ReadoutType.SOE, timestamp);
                            frameIntervalUs = timestamp - frameExposureStartTimestampUs;
                            frameExposureStartTimestampUs = timestamp;
                        }
                    }

                    if (pixLast && (readoutType == ApsDvsEvent.ReadoutType.ResetRead)
                            && config.chipConfigChain.configBits[6].isSet()) {
                        // global shutter start of exposure (SOE)
                        createApsFlagEvent(outItr, ApsDvsEvent.ReadoutType.SOE, timestamp);
                        frameIntervalUs = timestamp - frameExposureStartTimestampUs;
                        frameExposureStartTimestampUs = timestamp;
                    }

                    final ApsDvsEvent e = nextApsDvsEvent(outItr);
                    e.adcSample = data & ApsDvsChip.ADC_DATA_MASK;
                    e.readoutType = readoutType;
                    e.special = false;
                    e.timestamp = timestamp;
                    e.address = data;
                    e.x = x;
                    e.y = y;
                    e.type = (byte) (2);

                    // end of exposure, same for both
                    if (pixFirst && (readoutType == ApsDvsEvent.ReadoutType.SignalRead)) {
                        createApsFlagEvent(outItr, ApsDvsEvent.ReadoutType.EOE, timestamp);
                        frameExposureEndTimestampUs = timestamp;
                        exposureDurationUs = timestamp - frameExposureStartTimestampUs;
                    }

                    if (pixLast && (readoutType == ApsDvsEvent.ReadoutType.SignalRead)) {
						// if we use ResetRead+SignalRead+C readout, OR, if we use ResetRead-SignalRead readout and we
                        // are at last APS pixel, then write EOF event
                        // insert a new "end of frame" event not present in original data
                        createApsFlagEvent(outItr, ApsDvsEvent.ReadoutType.EOF, timestamp);

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

            return out;
        } // extractPacket

        // TODO hack to reuse IMUSample events as ApsDvsEvents holding only APS or DVS data by using the special flags
        private ApsDvsEvent nextApsDvsEvent(final OutputEventIterator outItr) {
            final ApsDvsEvent e = (ApsDvsEvent) outItr.nextOutput();
            e.special = false;
            e.adcSample = -1;
            if (e instanceof IMUSample) {
                ((IMUSample) e).imuSampleEvent = false;
            }
            return e;
        }

        /**
         * creates a special ApsDvsEvent in output packet just for flagging APS
         * frame markers such as start of frame, reset, end of frame.
         *
         * @param outItr
         * @param flag
         * @param timestamp
         * @return
         */
        private ApsDvsEvent createApsFlagEvent(final OutputEventIterator outItr, final ApsDvsEvent.ReadoutType flag,
                final int timestamp) {
            final ApsDvsEvent a = nextApsDvsEvent(outItr);
            a.adcSample = 0; // set this effectively as ADC sample even though fake
            a.timestamp = timestamp;
            a.x = -1;
            a.y = -1;
            a.readoutType = flag;
            return a;
        }

        @Override
        public AEPacketRaw reconstructRawPacket(final EventPacket packet) {
            if (raw == null) {
                raw = new AEPacketRaw();
            }
            if (!(packet instanceof ApsDvsEventPacket)) {
                return null;
            }
            final ApsDvsEventPacket apsDVSpacket = (ApsDvsEventPacket) packet;
            raw.ensureCapacity(packet.getSize());
            raw.setNumEvents(0);
            final int[] a = raw.addresses;
            final int[] ts = raw.timestamps;
            apsDVSpacket.getSize();
            final Iterator evItr = apsDVSpacket.fullIterator();
            int k = 0;
            while (evItr.hasNext()) {
                final ApsDvsEvent e = (ApsDvsEvent) evItr.next();
				// not writing out these EOF events (which were synthesized on extraction) results in reconstructed
                // packets with giant time gaps, reason unknown
                if (e.isEndOfFrame()) {
                    continue; // these EOF events were synthesized from data in first place
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
        public int reconstructRawAddressFromEvent(final TypedEvent e) {
            int address = e.address;
			// if(e.x==0 && e.y==0){
            // log.info("start of frame event "+e);
            // }
            // if(e.x==-1 && e.y==-1){
            // log.info("end of frame event "+e);
            // }
            // e.x came from e.x = (short) (chip.getSizeX()-1-((data & XMASK) >>> XSHIFT)); // for DVS event, no x flip
            // if APS event
            if (((ApsDvsEvent) e).adcSample >= 0) {
                address = (address & ~ApsDvsChip.XMASK) | ((e.x) << ApsDvsChip.XSHIFT);
            } else {
                address = (address & ~ApsDvsChip.XMASK) | ((sx1 - e.x) << ApsDvsChip.XSHIFT);
            }
            // e.y came from e.y = (short) ((data & YMASK) >>> YSHIFT);
            address = (address & ~ApsDvsChip.YMASK) | (e.y << ApsDvsChip.YSHIFT);
            return address;
        }

        private void setFrameCount(final int i) {
            frameCount = i;
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
        try {
            if (getBiasgen() == null) {
                setBiasgen(new DAViS240Config(this));
                // now we can addConfigValue the control panel
            } else {
                getBiasgen().setHardwareInterface((BiasgenHardwareInterface) hardwareInterface);
            }
        } catch (final ClassCastException e) {
            Chip.log.warning(e.getMessage()
                    + ": probably this chip object has a biasgen but the hardware interface doesn't, ignoring");
        }
    }

    /**
     * Displays data from SeeBetter test chip SeeBetter10/11.
     *
     * @author Tobi
     */
    public class DAViS240DisplayMethod extends ChipRendererDisplayMethodRGBA {

        private static final int FONTSIZE = 10;
        private static final int FRAME_COUNTER_BAR_LENGTH_FRAMES = 10;

        private final TextRenderer exposureRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN,
                DAViS240DisplayMethod.FONTSIZE), true, true);

        public DAViS240DisplayMethod(final DAVIS240BaseCamera chip) {
            super(chip.getCanvas());
        }

        @Override
        public void display(final GLAutoDrawable drawable) {
            getCanvas().setBorderSpacePixels(50);

            super.display(drawable);

            if (isTimestampMaster == false) {
                exposureRenderer.begin3DRendering();
                exposureRenderer.draw3D("Slave camera", 0, -(DAViS240DisplayMethod.FONTSIZE / 2), 0, .5f);
                exposureRenderer.end3DRendering();
            }

            if ((config.videoControl != null) && config.videoControl.displayFrames) {
                final GL2 gl = drawable.getGL().getGL2();
                exposureRender(gl);
            }

            // draw sample histogram
            if (showImageHistogram && (renderer instanceof AEFrameChipRenderer)) {
                // System.out.println("drawing hist");
                final int size = 100;
                final AbstractHistogram hist = ((AEFrameChipRenderer) renderer).getAdcSampleValueHistogram();
                final GL2 gl = drawable.getGL().getGL2();
                gl.glPushAttrib(GL.GL_COLOR_BUFFER_BIT);
                gl.glColor3f(0, 0, 1);
                gl.glLineWidth(1f);
                hist.draw(drawable, exposureRenderer, (sizeX / 2) - (size / 2), (sizeY / 2) + (size / 2), size, size);
                gl.glPopAttrib();
            }

            // Draw last IMU output
            if (config.isDisplayImu() && (chip instanceof DAVIS240BaseCamera)) {
                final IMUSample imuSample = ((DAVIS240BaseCamera) chip).getImuSample();
                if (imuSample != null) {
                    imuRender(drawable, imuSample);
                }
            }
        }

        TextRenderer imuTextRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 36));
        GLU glu = null;
        GLUquadric accelCircle = null;

        private void imuRender(final GLAutoDrawable drawable, final IMUSample imuSample) {
            // System.out.println("on rendering: "+imuSample.toString());
            final GL2 gl = drawable.getGL().getGL2();
            gl.glPushMatrix();

            gl.glTranslatef(chip.getSizeX() / 2, chip.getSizeY() / 2, 0);
            gl.glLineWidth(3);

            final float vectorScale = 1.5f;
            final float textScale = .2f;
            final float trans = .7f;
            float x, y;

            // gyro pan/tilt
            gl.glColor3f(1f, 0, 1);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(0, 0);
            x = (vectorScale * imuSample.getGyroYawY() * DAVIS240BaseCamera.HEIGHT) / IMUSample.getFullScaleGyroDegPerSec();
            y = (vectorScale * imuSample.getGyroTiltX() * DAVIS240BaseCamera.HEIGHT) / IMUSample.getFullScaleGyroDegPerSec();
            gl.glVertex2f(x, y);
            gl.glEnd();

            imuTextRenderer.begin3DRendering();
            imuTextRenderer.setColor(1f, 0, 1, trans);
            imuTextRenderer.draw3D(String.format("%.2f,%.2f dps", imuSample.getGyroYawY(), imuSample.getGyroTiltX()),
                    x, y + 5, 0, textScale); // x,y,z, scale factor
            imuTextRenderer.end3DRendering();

            // gyro roll
            x = (vectorScale * imuSample.getGyroRollZ() * DAVIS240BaseCamera.HEIGHT) / IMUSample.getFullScaleGyroDegPerSec();
            y = chip.getSizeY() * .25f;
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(0, y);
            gl.glVertex2f(x, y);
            gl.glEnd();

            imuTextRenderer.begin3DRendering();
            imuTextRenderer.draw3D(String.format("%.2f dps", imuSample.getGyroRollZ()), x, y, 0, textScale);
            imuTextRenderer.end3DRendering();

            // acceleration x,y
            x = (vectorScale * imuSample.getAccelX() * DAVIS240BaseCamera.HEIGHT) / IMUSample.getFullScaleAccelG();
            y = (vectorScale * imuSample.getAccelY() * DAVIS240BaseCamera.HEIGHT) / IMUSample.getFullScaleAccelG();
            gl.glColor3f(0, 1, 0);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(0, 0);
            gl.glVertex2f(x, y);
            gl.glEnd();

            imuTextRenderer.begin3DRendering();
            imuTextRenderer.setColor(0, .5f, 0, trans);
            imuTextRenderer.draw3D(String.format("%.2f,%.2f g", imuSample.getAccelX(), imuSample.getAccelY()), x, y, 0,
                    textScale); // x,y,z, scale factor
            imuTextRenderer.end3DRendering();

            // acceleration z, drawn as circle
            if (glu == null) {
                glu = new GLU();
            }
            if (accelCircle == null) {
                accelCircle = glu.gluNewQuadric();
            }
            final float az = ((vectorScale * imuSample.getAccelZ() * DAVIS240BaseCamera.HEIGHT)) / IMUSample.getFullScaleAccelG();
            final float rim = .5f;
            glu.gluQuadricDrawStyle(accelCircle, GLU.GLU_FILL);
            glu.gluDisk(accelCircle, az - rim, az + rim, 16, 1);

            imuTextRenderer.begin3DRendering();
            imuTextRenderer.setColor(0, .5f, 0, trans);
            final String saz = String.format("%.2f g", imuSample.getAccelZ());
            final Rectangle2D rect = imuTextRenderer.getBounds(saz);
            imuTextRenderer.draw3D(saz, az, -(float) rect.getHeight() * textScale * 0.5f, 0, textScale);
            imuTextRenderer.end3DRendering();

            // color annotation to show what is being rendered
            imuTextRenderer.begin3DRendering();
            imuTextRenderer.setColor(1, 1, 1, trans);
            final String ratestr = String.format("IMU: timestamp=%-+9.3fs last dtMs=%-6.1fms  avg dtMs=%-6.1fms",
                    1e-6f * imuSample.getTimestampUs(), imuSample.getDeltaTimeUs() * .001f,
                    IMUSample.getAverageSampleIntervalUs() / 1000);
            final Rectangle2D raterect = imuTextRenderer.getBounds(ratestr);
            imuTextRenderer.draw3D(ratestr, -(float) raterect.getWidth() * textScale * 0.5f * .7f, -12, 0,
                    textScale * .7f); // x,y,z, scale factor
            imuTextRenderer.end3DRendering();

            gl.glPopMatrix();
        }

        private void exposureRender(final GL2 gl) {
            gl.glPushMatrix();

            exposureRenderer.begin3DRendering();
            if (frameIntervalUs > 0) {
                setFrameRateHz((float) 1000000 / frameIntervalUs);
            }
            setExposureMs((float) exposureDurationUs / 1000);
            final String s = String.format("Frame: %d; Exposure %.2f ms; Frame rate: %.2f Hz", getFrameCount(),
                    exposureMs, frameRateHz);
            exposureRenderer.draw3D(s, 0, DAVIS240BaseCamera.HEIGHT + (DAViS240DisplayMethod.FONTSIZE / 2), 0, .5f);
            exposureRenderer.end3DRendering();

            final int nframes = frameCount % DAViS240DisplayMethod.FRAME_COUNTER_BAR_LENGTH_FRAMES;
            final int rectw = DAVIS240BaseCamera.WIDTH / DAViS240DisplayMethod.FRAME_COUNTER_BAR_LENGTH_FRAMES;
            gl.glColor4f(1, 1, 1, .5f);
            for (int i = 0; i < nframes; i++) {
                gl.glRectf(nframes * rectw, DAVIS240BaseCamera.HEIGHT + 1, ((nframes + 1) * rectw) - 3,
                        (DAVIS240BaseCamera.HEIGHT + (DAViS240DisplayMethod.FONTSIZE / 2)) - 1);
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
        return ApsDvsChip.MAX_ADC;
    }

    /**
     * Sets the measured frame rate. Does not change parameters, only used for
     * recording measured quantity and informing GUI listeners.
     *
     * @param frameRateHz the frameRateHz to set
     */
    private void setFrameRateHz(final float frameRateHz) {
        final float old = this.frameRateHz;
        this.frameRateHz = frameRateHz;
        getSupport().firePropertyChange(ApsDvsChip.PROPERTY_FRAME_RATE_HZ, old, this.frameRateHz);
    }

    /**
     * Sets the measured exposure. Does not change parameters, only used for
     * recording measured quantity.
     *
     * @param exposureMs the exposureMs to set
     */
    private void setExposureMs(final float exposureMs) {
        final float old = this.exposureMs;
        this.exposureMs = exposureMs;
        getSupport().firePropertyChange(ApsDvsChip.PROPERTY_EXPOSURE_MS, old, this.exposureMs);
    }

    @Override
    public float getFrameRateHz() {
        return frameRateHz;
    }

    @Override
    public float getExposureMs() {
        return exposureMs;
    }

    @Override
    public int getFrameExposureStartTimestampUs() {
        return frameExposureStartTimestampUs;
    }

    @Override
    public int getFrameExposureEndTimestampUs() {
        return frameExposureEndTimestampUs;
    }

    /**
     * Returns the frame counter. This value is set on each end-of-frame sample.
     * It increases without bound and is not affected by rewinding a played-back
     * recording, for instance.
     *
     * @return the frameCount
     */
    @Override
    public int getFrameCount() {
        return frameCount;
    }

    /**
     * Triggers shot of one APS frame
     */
    @Override
    public void takeSnapshot() {
        snapshot = true;
        config.apsReadoutControl.setAdcEnabled(true);
    }

    /**
     * Sets threshold for shooting a frame automatically
     *
     * @param thresholdEvents the number of events to trigger shot on. Less than
     * or equal to zero disables auto-shot.
     */
    @Override
    public void setAutoshotThresholdEvents(int thresholdEvents) {
        if (thresholdEvents < 0) {
            thresholdEvents = 0;
        }
        autoshotThresholdEvents = thresholdEvents;
        getPrefs().putInt("DAViS240.autoshotThresholdEvents", thresholdEvents);
        if (autoshotThresholdEvents == 0) {
            config.runAdc.set(true);
        }
    }

    /**
     * Returns threshold for auto-shot.
     *
     * @return events to shoot frame
     */
    @Override
    public int getAutoshotThresholdEvents() {
        return autoshotThresholdEvents;
    }

    @Override
    public void setAutoExposureEnabled(final boolean yes) {
        getAutoExposureController().setAutoExposureEnabled(yes);
    }

    @Override
    public boolean isAutoExposureEnabled() {
        return getAutoExposureController().isAutoExposureEnabled();
    }

    private boolean showImageHistogram = getPrefs().getBoolean("DAViS240.showImageHistogram", false);

    @Override
    public boolean isShowImageHistogram() {
        return showImageHistogram;
    }

    @Override
    public void setShowImageHistogram(final boolean yes) {
        showImageHistogram = yes;
        getPrefs().putBoolean("DAViS240.showImageHistogram", yes);
    }

    /**
     * Controls exposure automatically to try to optimize captured gray levels
     *
     */
    public class AutoExposureController implements HasPropertyTooltips { // TODO not implemented yet

        private boolean autoExposureEnabled = getPrefs().getBoolean("autoExposureEnabled", false);

        private float expDelta = getPrefs().getFloat("expDelta", .1f); // exposure change if incorrectly exposed
        private float underOverFractionThreshold = getPrefs().getFloat("underOverFractionThreshold", 0.2f); // threshold for fraction of total pixels that are underexposed
        // or overexposed
        private final PropertyTooltipSupport tooltipSupport = new PropertyTooltipSupport();
        private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);
        SimpleHistogram hist = null;
        SimpleHistogram.Statistics stats = null;
        private float lowBoundary = getPrefs().getFloat("AutoExposureController.lowBoundary", 0.25f);
        private float highBoundary = getPrefs().getFloat("AutoExposureController.highBoundary", 0.75f);
        private boolean pidControllerEnabled = getPrefs().getBoolean("pidControllerEnabled", false);

        public AutoExposureController() {
            tooltipSupport.setPropertyTooltip("expDelta", "fractional change of exposure when under or overexposed");
            tooltipSupport.setPropertyTooltip("underOverFractionThreshold",
                    "fraction of pixel values under xor over exposed to trigger exposure change");
            tooltipSupport.setPropertyTooltip("lowBoundary", "Upper edge of histogram range considered as low values");
            tooltipSupport
                    .setPropertyTooltip("highBoundary", "Lower edge of histogram range considered as high values");
            tooltipSupport.setPropertyTooltip("autoExposureEnabled",
                    "Exposure time is automatically controlled when this flag is true");
            tooltipSupport.setPropertyTooltip("pidControllerEnabled",
                    "<html>Enable proportional integral derivative (actually just proportional) controller rather than fixed-size step control. <p><i>expDelta</i> is multiplied by the fractional error from mid-range exposure when <i>pidControllerEnabled</i> is set");
        }

        @Override
        public String getPropertyTooltip(final String propertyName) {
            return tooltipSupport.getPropertyTooltip(propertyName);
        }

        public void setAutoExposureEnabled(final boolean yes) {
            final boolean old = autoExposureEnabled;
            autoExposureEnabled = yes;
            propertyChangeSupport.firePropertyChange("autoExposureEnabled", old, yes);
            getPrefs().putBoolean("autoExposureEnabled", yes);
            if (!yes) {
                stats.reset(); // ensure toggling enabled resets the maxBin stat
            }
        }

        public boolean isAutoExposureEnabled() {
            return autoExposureEnabled;
        }

        public void controlExposure() {
            if (!autoExposureEnabled) {
                return;
            }
            if ((getAeViewer() != null) && (getAeViewer().getPlayMode() != null)
                    && (getAeViewer().getPlayMode() != AEViewer.PlayMode.LIVE)) {
                return;
            }
            hist = apsDVSrenderer.getAdcSampleValueHistogram();
            if (hist == null) {
                return;
            }
            stats = hist.getStatistics();
            if (stats == null) {
                return;
            }
            stats.setLowBoundary(lowBoundary);
            stats.setHighBoundary(highBoundary);
            hist.computeStatistics();
            final CPLDInt exposure = config.exposure;

            final int currentExposure = exposure.get();
            int newExposure = 0;
            float expChange = expDelta;
            if (pidControllerEnabled && stats.maxNonZeroBin > 0) {
                // compute error signsl from meanBin relative to actual range of bins
                float err = (stats.meanBin - (stats.maxNonZeroBin / 2)) / (float) stats.maxNonZeroBin; // fraction of range exposure is above middle bin
                expChange = expDelta * Math.abs(err);
            }
            if ((stats.fracLow >= underOverFractionThreshold) && (stats.fracHigh < underOverFractionThreshold)) {
                newExposure = Math.round(currentExposure * (1 + expChange));
                if (newExposure == currentExposure) {
                    newExposure++; // ensure increase
                }
                if (newExposure > exposure.getMax()) {
                    newExposure = exposure.getMax();
                }
                if (newExposure != currentExposure) {
                    exposure.set(newExposure);
                }
                Chip.log.log(
                        Level.INFO,
                        "Underexposed: {0} {1}",
                        new Object[]{stats.toString(),
                            String.format("expChange=%.2f (oldExposure=%8d newExposure=%8d)", expChange, currentExposure, newExposure)});
            } else if ((stats.fracLow < underOverFractionThreshold) && (stats.fracHigh >= underOverFractionThreshold)) {
                newExposure = Math.round(currentExposure * (1 - expChange));
                if (newExposure == currentExposure) {
                    newExposure--; // ensure decrease even with rounding.
                }
                if (newExposure < exposure.getMin()) {
                    newExposure = exposure.getMin();
                }
                if (newExposure != currentExposure) {
                    exposure.set(newExposure);
                }
                Chip.log.log(
                        Level.INFO,
                        "Overexposed: {0} {1}",
                        new Object[]{stats.toString(),
                            String.format("expChange=%.2f (oldExposure=%8d newExposure=%8d)", expChange, currentExposure, newExposure)});
            } else {
                // log.info(stats.toString());
            }
        }

        /**
         * Gets by what relative amount the exposure is changed on each frame if
         * under or over exposed.
         *
         * @return the expDelta
         */
        public float getExpDelta() {
            return expDelta;
        }

        /**
         * Sets by what relative amount the exposure is changed on each frame if
         * under or over exposed.
         *
         * @param expDelta the expDelta to set
         */
        public void setExpDelta(final float expDelta) {
            this.expDelta = expDelta;
            getPrefs().putFloat("expDelta", expDelta);
        }

        /**
         * Gets the fraction of pixel values that must be under xor over exposed
         * to change exposure automatically.
         *
         * @return the underOverFractionThreshold
         */
        public float getUnderOverFractionThreshold() {
            return underOverFractionThreshold;
        }

        /**
         * Gets the fraction of pixel values that must be under xor over exposed
         * to change exposure automatically.
         *
         * @param underOverFractionThreshold the underOverFractionThreshold to
         * set
         */
        public void setUnderOverFractionThreshold(final float underOverFractionThreshold) {
            this.underOverFractionThreshold = underOverFractionThreshold;
            getPrefs().putFloat("underOverFractionThreshold", underOverFractionThreshold);
        }

        public float getLowBoundary() {
            return lowBoundary;
        }

        public void setLowBoundary(final float lowBoundary) {
            this.lowBoundary = lowBoundary;
            getPrefs().putFloat("AutoExposureController.lowBoundary", lowBoundary);
        }

        public float getHighBoundary() {
            return highBoundary;
        }

        public void setHighBoundary(final float highBoundary) {
            this.highBoundary = highBoundary;
            getPrefs().putFloat("AutoExposureController.highBoundary", highBoundary);
        }

        /**
         * @return the propertyChangeSupport
         */
        public PropertyChangeSupport getPropertyChangeSupport() {
            return propertyChangeSupport;
        }

        /**
         * @return the pidControllerEnabled
         */
        public boolean isPidControllerEnabled() {
            return pidControllerEnabled;
        }

        /**
         * @param pidControllerEnabled the pidControllerEnabled to set
         */
        public void setPidControllerEnabled(boolean pidControllerEnabled) {
            this.pidControllerEnabled = pidControllerEnabled;
            getPrefs().putBoolean("pidControllerEnabled", pidControllerEnabled);
        }
    }

    /**
     * Returns the current Inertial Measurement Unit sample.
     *
     * @return the imuSample, or null if there is no sample
     */
    public IMUSample getImuSample() {
        return imuSample;
    }

    @Override
    public void update(final Observable o, final Object arg) {
        // TODO Auto-generated method stub
    }

    private void updateTSMasterState() {
		// Check which logic we are and send the TS Master/Slave command if we are old logic.
        // TODO: this needs to be done.
    }

    /**
     * Enables or disable DVS128 menu in AEViewer
     *
     * @param yes true to enable it
     */
    private void enableChipMenu(final boolean yes) {
        if (yes) {
            if (chipMenu == null) {
                chipMenu = new JMenu(this.getClass().getSimpleName());
                chipMenu.getPopupMenu().setLightWeightPopupEnabled(false); // to paint on GLCanvas
                chipMenu.setToolTipText("Specialized menu for DAVIS chip");
            }

            if (syncEnabledMenuItem == null) {
                syncEnabledMenuItem = new JCheckBoxMenuItem("Timestamp master");
                syncEnabledMenuItem.setToolTipText("<html>Sets this device as timestamp master");

                syncEnabledMenuItem.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(final ActionEvent evt) {
                        Chip.log.info("setting sync/timestamp master to " + syncEnabledMenuItem.isSelected());
                        isTimestampMaster = syncEnabledMenuItem.isSelected();

                        updateTSMasterState();
                    }
                });

                syncEnabledMenuItem.setSelected(isTimestampMaster);
                updateTSMasterState();

                chipMenu.add(syncEnabledMenuItem);
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
        getAeViewer().removeHelpItem(helpMenuItem1);
        getAeViewer().removeHelpItem(helpMenuItem2);
        getAeViewer().removeHelpItem(helpMenuItem3);

        enableChipMenu(false);
    }

    @Override
    public void onRegistration() {
        super.onRegistration();

        if (getAeViewer() == null) {
            return;
        }
        helpMenuItem1 = getAeViewer().addHelpURLItem(HELP_URL_RETINA, "Product overview", "Opens product overview guide");
        helpMenuItem2 = getAeViewer().addHelpURLItem(USER_GUIDE_URL_RETINA, "DAVIS240 user guide", "Opens DAVIS240 user guide");
        helpMenuItem3 = getAeViewer().addHelpURLItem(USER_GUIDE_URL_FLASHY, "Flashy user guide", "User guide for external tool flashy for firmware/logic updates to devices using the libusb driver");

        enableChipMenu(true);
    }

    abstract protected boolean firstFrameAddress(short x, short y);

    abstract protected boolean lastFrameAddress(short x, short y);

}
