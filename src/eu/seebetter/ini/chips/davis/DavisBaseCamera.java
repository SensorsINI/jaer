/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Rectangle2D;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

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
import net.sf.jaer.graphics.ChipRendererDisplayMethodRGBA;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.util.RemoteControlCommand;
import net.sf.jaer.util.RemoteControlled;
import net.sf.jaer.util.histogram.AbstractHistogram;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;
import com.jogamp.opengl.util.awt.TextRenderer;

import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import java.awt.Color;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import net.sf.jaer.util.TextRendererScale;

/**
 * Abstract base camera class for SeeBetter DAVIS cameras.
 *
 * @author tobi
 */
abstract public class DavisBaseCamera extends DavisChip implements RemoteControlled {

    public static final String HELP_URL_RETINA = "http://inilabs.com/support/overview-of-dynamic-vision-sensors";
    public static final String USER_GUIDE_URL_FLASHY = "https://docs.google.com/a/longi.li/document/d/1LuO-i8u-Y7Nf0zQ-N-Z2LRiKiQMO-EkD3Ln2bmnjhmQ/edit?usp=sharing";
    public static final String USER_GUIDE_URL_DAVIS240 = "http://www.inilabs.com/support/davis240";
    protected final int ADC_NUMBER_OF_TRAILING_ZEROS = Integer.numberOfTrailingZeros(DavisChip.ADC_READCYCLE_MASK);
    protected final String CMD_EXPOSURE = "exposure";
//    protected final String CMD_EXPOSURE_CC = "exposureCC";
//    protected final String CMD_RS_SETTLE_CC = "resetSettleCC"; // can be added to sub cameras again if needed, or exposed in DavisDisplayConfigInterface by methods
    protected AEFrameChipRenderer apsDVSrenderer;
    protected final AutoExposureController autoExposureController;
    protected int autoshotThresholdEvents = getPrefs().getInt("autoshotThresholdEvents", 0);
    protected JMenu chipMenu = null;
    JFrame controlFrame = null;
    protected int exposureDurationUs; // internal measured variable, set during rendering. Duration of frame expsosure in
    /**
     * holds measured variable in ms for GUI rendering
     */
    protected float exposureMs;
    /**
     * Holds count of frames obtained by end of frame events
     */
    protected int frameCount = 0;
    // reset released)
    protected int frameExposureEndTimestampUs; // end of exposureControlRegister (first events of signal read)
    protected int frameExposureStartTimestampUs = 0; // timestamp of first sample from frame (first sample read after
    // us.
    protected int frameIntervalUs; // internal measured variable, set during rendering. Time between this frame and
    // previous one.
    /**
     * holds measured variable in Hz for GUI rendering of rate
     */
    protected float frameRateHz;
    //    public static final String FIRMWARE_CHANGELOG = "https://sourceforge.net/p/jaer/code/HEAD/tree/devices/firmware/CypressFX2/firmware_FX2LP_DVS128/CHANGELOG.txt";
    JComponent helpMenuItem1 = null;
    JComponent helpMenuItem2 = null;
    JComponent helpMenuItem3 = null;
    protected IMUSample imuSample; // latest IMUSample from sensor
    protected boolean isTimestampMaster = true;
    protected boolean showImageHistogram = getPrefs().getBoolean("showImageHistogram", false);
    protected boolean snapshot = false;
    protected JMenuItem syncEnabledMenuItem = null;
    protected DavisDisplayMethod davisDisplayMethod = null;

    public DavisBaseCamera() {
        setName("DAVISBaseCamera");
        setEventClass(ApsDvsEvent.class);
        setNumCellTypes(3); // two are polarity and last is intensity
        setPixelHeightUm(18.5f);
        setPixelWidthUm(18.5f);

        setEventExtractor(new DavisEventExtractor(this));

        davisDisplayMethod = new DavisDisplayMethod(this);
        getCanvas().addDisplayMethod(davisDisplayMethod);
        getCanvas().setDisplayMethod(davisDisplayMethod);

        if (getRemoteControl() != null) {
            getRemoteControl()
                    .addCommandListener(this, CMD_EXPOSURE, CMD_EXPOSURE + " val - sets exposure. val in ms.");
//            getRemoteControl().addCommandListener(this, CMD_EXPOSURE_CC,
//                    CMD_EXPOSURE_CC + " val - sets exposureControlRegister. val in clock cycles");
//            getRemoteControl().addCommandListener(this, CMD_RS_SETTLE_CC,
//                    CMD_RS_SETTLE_CC + " val - sets reset settling time. val in clock cycles");
        }
        autoExposureController = new AutoExposureController(this);

    }

    @Override
    public void controlExposure() {
        getAutoExposureController().controlExposure();
    }

    /**
     * Enables or disable DVS128 menu in AEViewer
     *
     * @param yes true to enable it
     */
    protected void enableChipMenu(final boolean yes) {
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
                getAeViewer().addMenu(chipMenu);
            }
        } else {
            // disable menu
            if (chipMenu != null) {
                getAeViewer().removeMenu(chipMenu);
            }
        }
    }

    /**
     * @return the autoExposureController
     */
    public AutoExposureController getAutoExposureController() {
        return autoExposureController;
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

    /**
     * Returns measured exposure time.
     *
     * @return exposure time in ms
     */
    @Override
    public float getMeasuredExposureMs() {
        return exposureMs;
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

    @Override
    public int getFrameExposureEndTimestampUs() {
        return frameExposureEndTimestampUs;
    }

    @Override
    public int getFrameExposureStartTimestampUs() {
        return frameExposureStartTimestampUs;
    }

    @Override
    public float getFrameRateHz() {
        return frameRateHz;
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
    public int getMaxADC() {
        return DavisChip.MAX_ADC;
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
    public boolean isAutoExposureEnabled() {
        return getAutoExposureController().isAutoExposureEnabled();
    }

    @Override
    public boolean isShowImageHistogram() {
        return showImageHistogram;
    }

    public boolean firstFrameAddress(short x, short y) {
        return (x == (getSizeX() - 1)) && (y == (getSizeY() - 1));
    }

    public boolean lastFrameAddress(short x, short y) {
        return (x == 0) && (y == 0); //To change body of generated methods, choose Tools | Templates.
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
        helpMenuItem2 = getAeViewer().addHelpURLItem(USER_GUIDE_URL_DAVIS240, "DAVIS240 user guide", "Opens DAVIS240 user guide");
        helpMenuItem3 = getAeViewer().addHelpURLItem(USER_GUIDE_URL_FLASHY, "Flashy user guide", "User guide for external tool flashy for firmware/logic updates to devices using the libusb driver");
        enableChipMenu(true);
    }

    @Override
    public String processRemoteControlCommand(final RemoteControlCommand command, final String input) {
        Chip.log.log(Level.INFO, "processing RemoteControlCommand {0} with input={1}", new Object[]{command, input});
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
            getDavisConfig().setExposureDelayMs(v);
        } else {
            return input + ": unknown command";
        }
        return "successfully processed command " + input;
    }

    @Override
    public void setAutoExposureEnabled(final boolean yes) {
        getAutoExposureController().setAutoExposureEnabled(yes);
    }

    /**
     * Sets the measured exposureControlRegister. Does not change parameters,
     * only used for recording measured quantity.
     *
     * @param exposureMs the exposureMs to set
     */
    protected void setMeasuredExposureMs(final float exposureMs) {
        final float old = this.exposureMs;
        this.exposureMs = exposureMs;
        getSupport().firePropertyChange(DavisChip.PROPERTY_MEASURED_EXPOSURE_MS, old, this.exposureMs);
    }

    /**
     * Sets the measured frame rate. Does not change parameters, only used for
     * recording measured quantity and informing GUI listeners.
     *
     * @param frameRateHz the frameRateHz to set
     */
    protected void setFrameRateHz(final float frameRateHz) {
        final float old = this.frameRateHz;
        this.frameRateHz = frameRateHz;
        getSupport().firePropertyChange(DavisChip.PROPERTY_FRAME_RATE_HZ, old, this.frameRateHz);
    }

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
                setBiasgen(new DavisConfig(this));
                // now we can addConfigValue the control panel
            } else {
                getBiasgen().setHardwareInterface((BiasgenHardwareInterface) hardwareInterface);
            }
        } catch (final ClassCastException e) {
            Chip.log.warning(e.getMessage() + ": probably this chip object has a biasgen but the hardware interface doesn't, ignoring");
        }
    }

    @Override
    public void setShowImageHistogram(final boolean yes) {
        showImageHistogram = yes;
        getPrefs().putBoolean("showImageHistogram", yes);
    }

    protected void updateTSMasterState() {
        // Check which logic we are and send the TS Master/Slave command if we are old logic.
        // TODO: this needs to be done.
    }

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
    public class DavisEventExtractor extends RetinaExtractor {

        /**
         *
         */
        protected static final long serialVersionUID = 3890914720599660376L;
        protected int autoshotEventsSinceLastShot = 0; // autoshot counter
        protected int warningCount = 0;
        protected static final int WARNING_COUNT_DIVIDER = 10000;

        public DavisEventExtractor(final DavisBaseCamera chip) {
            super(chip);
        }

        protected IMUSample.IncompleteIMUSampleException incompleteIMUSampleException = null;
        protected static final int IMU_WARNING_INTERVAL = 1000;
        protected int missedImuSampleCounter = 0;
        protected int badImuDataCounter = 0;

        /**
         * extracts the meaning of the raw events.
         *
         * @param in the raw events, can be null
         * @return out the processed events. these are partially processed
         * in-place. empty packet is returned if null is supplied as in.
         */
        @Override
        synchronized public EventPacket extractPacket(final AEPacketRaw in) {
            if (!(chip instanceof DavisChip)) {
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
            int sx1 = chip.getSizeX() - 1;
            int sy1 = chip.getSizeY() - 1;

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
                        || ((DavisChip.ADDRESS_TYPE_IMU & data) == DavisChip.ADDRESS_TYPE_IMU)) {
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
                            if ((missedImuSampleCounter++ % DavisEventExtractor.IMU_WARNING_INTERVAL) == 0) {
                                Chip.log.warning(String.format("%s (obtained %d partial samples so far)",
                                        ex.toString(), missedImuSampleCounter));
                            }
                            break; // break out of loop because this packet only contained part of an IMUSample and
                            // formed the end of the packet anyhow. Next time we come back here we will complete
                            // the IMUSample
                        } catch (final IMUSample.BadIMUDataException ex2) {
                            if ((badImuDataCounter++ % DavisEventExtractor.IMU_WARNING_INTERVAL) == 0) {
                                Chip.log.warning(String.format("%s (%d bad samples so far)", ex2.toString(),
                                        badImuDataCounter));
                            }
                            incompleteIMUSampleException = null;
                            continue; // continue because there may be other data
                        }
                    }

                } else if ((data & DavisChip.ADDRESS_TYPE_MASK) == DavisChip.ADDRESS_TYPE_DVS) {
                    // DVS event
                    final ApsDvsEvent e = nextApsDvsEvent(outItr);
                    if ((data & DavisChip.EVENT_TYPE_MASK) == DavisChip.EXTERNAL_INPUT_EVENT_ADDR) {
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
                        e.polarity = (data & DavisChip.POLMASK) == DavisChip.POLMASK ? ApsDvsEvent.Polarity.On
                                : ApsDvsEvent.Polarity.Off;
                        e.type = (byte) ((data & DavisChip.POLMASK) == DavisChip.POLMASK ? 1 : 0);
                        e.x = (short) (sx1 - ((data & DavisChip.XMASK) >>> DavisChip.XSHIFT));
                        e.y = (short) ((data & DavisChip.YMASK) >>> DavisChip.YSHIFT);
                        e.setIsDVS(true);
                        // System.out.println(data);
                        // autoshot triggering
                        autoshotEventsSinceLastShot++; // number DVS events captured here
                    }
                } else if ((data & DavisChip.ADDRESS_TYPE_MASK) == DavisChip.ADDRESS_TYPE_APS) {
                    // APS event
                    // We first calculate the positions, so we can put events such as StartOfFrame at their
                    // right place, before the actual APS event denoting (0, 0) for example.
                    final int timestamp = timestamps[i];

                    final short x = (short) (((data & DavisChip.XMASK) >>> DavisChip.XSHIFT));
                    final short y = (short) ((data & DavisChip.YMASK) >>> DavisChip.YSHIFT);

                    final boolean pixFirst = firstFrameAddress(x, y); // First event of frame (addresses get flipped)
                    final boolean pixLast = lastFrameAddress(x, y); // Last event of frame (addresses get flipped)

                    ApsDvsEvent.ReadoutType readoutType = ApsDvsEvent.ReadoutType.Null;
                    switch ((data & DavisChip.ADC_READCYCLE_MASK) >> ADC_NUMBER_OF_TRAILING_ZEROS) {
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
                            if ((warningCount < 10) || ((warningCount % DavisEventExtractor.WARNING_COUNT_DIVIDER) == 0)) {
                                Chip.log
                                        .warning("Event with unknown readout cycle was sent out! You might be reading a file that had the deprecated C readout mode enabled.");
                            }
                            warningCount++;
                            break;
                    }

                    if (pixFirst && (readoutType == ApsDvsEvent.ReadoutType.ResetRead)) {
                        createApsFlagEvent(outItr, ApsDvsEvent.ReadoutType.SOF, timestamp);

                        if (!getDavisConfig().getApsReadoutControl().isGlobalShutterMode()) {
                            // rolling shutter start of exposureControlRegister (SOE)
                            createApsFlagEvent(outItr, ApsDvsEvent.ReadoutType.SOE, timestamp);
                            frameIntervalUs = timestamp - frameExposureStartTimestampUs;
                            frameExposureStartTimestampUs = timestamp;
                        }
                    }

                    if (pixLast && (readoutType == ApsDvsEvent.ReadoutType.ResetRead)
                            && getDavisConfig().getApsReadoutControl().isGlobalShutterMode()) {
                        // global shutter start of exposureControlRegister (SOE)
                        createApsFlagEvent(outItr, ApsDvsEvent.ReadoutType.SOE, timestamp);
                        frameIntervalUs = timestamp - frameExposureStartTimestampUs;
                        frameExposureStartTimestampUs = timestamp;
                    }

                    final ApsDvsEvent e = nextApsDvsEvent(outItr);
                    e.adcSample = data & DavisChip.ADC_DATA_MASK;
                    e.readoutType = readoutType;
                    e.special = false;
                    e.timestamp = timestamp;
                    e.address = data;
                    e.x = x;
                    e.y = y;
                    e.type = (byte) (2);

                    // end of exposureControlRegister, same for both
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
                            getDavisConfig().getApsReadoutControl().setAdcEnabled(false);
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
        protected ApsDvsEvent nextApsDvsEvent(final OutputEventIterator outItr) {
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
        protected ApsDvsEvent createApsFlagEvent(final OutputEventIterator outItr, final ApsDvsEvent.ReadoutType flag,
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
                address = (address & ~DavisChip.XMASK) | ((e.x) << DavisChip.XSHIFT);
            } else {
                address = (address & ~DavisChip.XMASK) | ((getSizeX() - 1 - e.x) << DavisChip.XSHIFT);
            }
            // e.y came from e.y = (short) ((data & YMASK) >>> YSHIFT);
            address = (address & ~DavisChip.YMASK) | (e.y << DavisChip.YSHIFT);
            return address;
        }

        protected void setFrameCount(final int i) {
            frameCount = i;
        }

    } // extractor

    /**
     * Displays data from DAVIS camera
     *
     * @author Tobi
     */
    public class DavisDisplayMethod extends ChipRendererDisplayMethodRGBA {

        private static final int FONTSIZE = 10;
        private static final int FRAME_COUNTER_BAR_LENGTH_FRAMES = 10;

        private TextRenderer exposureRenderer = null;

        public DavisDisplayMethod(final DavisBaseCamera chip) {
            super(chip.getCanvas());
        }

        @Override
        public void display(final GLAutoDrawable drawable) {
            getCanvas().setBorderSpacePixels(50);

            super.display(drawable);

            if (exposureRenderer == null) {
                exposureRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN,
                        DavisDisplayMethod.FONTSIZE), true, true);
            }

            if (isTimestampMaster == false) {
                exposureRenderer.setColor(Color.WHITE);
                exposureRenderer.begin3DRendering();
                exposureRenderer.draw3D("Slave camera", 0, -(DavisDisplayMethod.FONTSIZE / 2), 0, .5f);
                exposureRenderer.end3DRendering();
            }

            if ((getDavisConfig().getVideoControl() != null) && getDavisConfig().getVideoControl().isDisplayFrames()) {
                final GL2 gl = drawable.getGL().getGL2();
                exposureRender(gl);
            }

            // draw sample histogram
            if (showImageHistogram && getDavisConfig().isDisplayFrames() && (renderer instanceof AEFrameChipRenderer)) {
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
            if ((getDavisConfig() != null) && getDavisConfig().isDisplayImu() && (chip instanceof DavisBaseCamera)) {
                final IMUSample imuSample = ((DavisBaseCamera) chip).getImuSample();
                if (imuSample != null) {
                    imuRender(drawable, imuSample);
                }
            }
        }

        TextRenderer imuTextRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 36));
        GLUquadric accelCircle = null;

        private void imuRender(final GLAutoDrawable drawable, final IMUSample imuSample) {
            // System.out.println("on rendering: "+imuSample.toString());
            final GL2 gl = drawable.getGL().getGL2();
            gl.glPushMatrix();

            gl.glTranslatef(chip.getSizeX() / 2, chip.getSizeY() / 2, 0);
            gl.glLineWidth(3);

            final float vectorScale = 1.5f;
            final float textScale = TextRendererScale.draw3dScale(imuTextRenderer, "XXX.XXf,%XXX.XXf dps", getChipCanvas().getScale(), getSizeX(), .3f);
            final float trans = .7f;
            float x, y;

 
            // acceleration x,y
            x = (vectorScale * imuSample.getAccelX() * getSizeX()) / IMUSample.getFullScaleAccelG();
            y = (vectorScale * imuSample.getAccelY() * getSizeY()) / IMUSample.getFullScaleAccelG();
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
            final float az = ((vectorScale * imuSample.getAccelZ() * getSizeY())) / IMUSample.getFullScaleAccelG();
            final float rim = .5f;
            glu.gluQuadricDrawStyle(accelCircle, GLU.GLU_FILL);
            glu.gluDisk(accelCircle, az - rim, az + rim, 16, 1);

            imuTextRenderer.begin3DRendering();
            imuTextRenderer.setColor(0, .5f, 0, trans);
            final String saz = String.format("%.2f g", imuSample.getAccelZ());
            final Rectangle2D rect = imuTextRenderer.getBounds(saz);
            imuTextRenderer.draw3D(saz, az, -(float) rect.getHeight() * textScale * 0.5f, 0, textScale);
            imuTextRenderer.end3DRendering();

           // gyro pan/tilt
            gl.glColor3f(1f, 0, 1);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(0, 0);
            x = (vectorScale * imuSample.getGyroYawY() * getSizeY()) / IMUSample.getFullScaleGyroDegPerSec();
            y = (vectorScale * imuSample.getGyroTiltX() * getSizeX()) / IMUSample.getFullScaleGyroDegPerSec();
            gl.glVertex2f(x, y);
            gl.glEnd();

            imuTextRenderer.begin3DRendering();
            imuTextRenderer.setColor(1f, 0, 1, trans);
            imuTextRenderer.draw3D(String.format("%.2f,%.2f dps", imuSample.getGyroYawY(), imuSample.getGyroTiltX()),
                    x, y + 5, 0, textScale); // x,y,z, scale factor
            imuTextRenderer.end3DRendering();

            // gyro roll
            x = (vectorScale * imuSample.getGyroRollZ() * getSizeY()) / IMUSample.getFullScaleGyroDegPerSec();
            y = chip.getSizeY() * .25f;
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(0, y);
            gl.glVertex2f(x, y);
            gl.glEnd();

            imuTextRenderer.begin3DRendering();
            imuTextRenderer.draw3D(String.format("%.2f dps", imuSample.getGyroRollZ()), x, y, 0, textScale);
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

            exposureRenderer.setColor(Color.WHITE);
            exposureRenderer.begin3DRendering();
            if (frameIntervalUs > 0) {
                setFrameRateHz((float) 1000000 / frameIntervalUs);
            }
            setMeasuredExposureMs((float) exposureDurationUs / 1000);
            final String s = String.format("Frame: %d; Exposure %.2f ms; Frame rate: %.2f Hz", getFrameCount(),
                    exposureMs, frameRateHz);
            float scale = TextRendererScale.draw3dScale(exposureRenderer, s, getChipCanvas().getScale(), getSizeX(), .75f);
            // determine width of string in pixels and scale accordingly
            exposureRenderer.draw3D(s, 0, getSizeY() + (DavisDisplayMethod.FONTSIZE / 2), 0, scale);
            exposureRenderer.end3DRendering();

            final int nframes = frameCount % DavisDisplayMethod.FRAME_COUNTER_BAR_LENGTH_FRAMES;
            final int rectw = getSizeX() / DavisDisplayMethod.FRAME_COUNTER_BAR_LENGTH_FRAMES;
            gl.glColor4f(1, 1, 1, .5f);
            for (int i = 0; i < nframes; i++) {
                gl.glRectf(nframes * rectw, getSizeY() + 1, ((nframes + 1) * rectw) - 3,
                        (getSizeY() + (DavisDisplayMethod.FONTSIZE / 2)) - 1);
            }
            gl.glPopMatrix();
        }
    }

    /**
     * A convenience method that returns the Biasgen object cast to DavisConfig.
     * This object contains all configuration of the camera. This method was
     * added for use in all configuration classes of subclasses fo
     * DavisBaseCamera.
     *
     * @return the configuration object
     * @author tobi
     */
    protected DavisConfig getDavisConfig() {
        return (DavisConfig) getBiasgen();
    }

    @Override
    public void setPowerDown(final boolean powerDown) {
        getDavisConfig().powerDown.set(powerDown);
        try {
            getDavisConfig().sendOnChipConfigChain();
        } catch (final HardwareInterfaceException ex) {
            Logger.getLogger(DavisBaseCamera.class.getName()).log(Level.SEVERE, null, ex);
        }
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
            getDavisConfig().runAdc.set(true);
        }
    }

    @Override
    public void setADCEnabled(final boolean adcEnabled) {
        getDavisConfig().getApsReadoutControl().setAdcEnabled(adcEnabled);
    }

    /**
     * Triggers shot of one APS frame
     */
    @Override
    public void takeSnapshot() {
        snapshot = true;
        getDavisConfig().getApsReadoutControl().setAdcEnabled(true);
    }

}
