/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Iterator;
import java.util.logging.Level;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;
import com.jogamp.opengl.util.awt.TextRenderer;

import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.aemonitor.EventRaw;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.chip.RetinaExtractor;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEvent.ColorFilter;
import net.sf.jaer.event.ApsDvsEvent.ReadoutType;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.ChipRendererDisplayMethodRGBA;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.CypressFX3;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.CypressFX3.SPIConfigSequence;
import net.sf.jaer.util.RemoteControlCommand;
import net.sf.jaer.util.RemoteControlled;
import net.sf.jaer.util.TextRendererScale;
import net.sf.jaer.util.WarningDialogWithDontShowPreference;
import net.sf.jaer.util.histogram.AbstractHistogram;

/**
 * Abstract base camera class for SeeBetter DAVIS cameras.
 *
 * @author tobi
 */
abstract public class DavisBaseCamera extends DavisChip implements RemoteControlled {

    public static final String HELP_URL_HW_USERGUIDES = "http://inilabs.com/support/hardware/";
    public static final String USER_GUIDE_URL_FLASHY = "http://inilabs.com/support/software/reflashing/";
    public static final String USER_GUIDE_URL_DAVIS240 = "http://inilabs.com/support/hardware/davis240/";

    // Remote control support
    private final String CMD_EXPOSURE = "exposure";
    private final String CMD_GET_IMU_TEMPERATURE_C = "getImuTemperature";

    private final DavisDisplayMethod davisDisplayMethod;
    protected DavisConfig davisConfig;
    protected AEFrameChipRenderer davisRenderer;
    private final AutoExposureController autoExposureController;

    private int autoshotThresholdEvents = getPrefs().getInt("DavisBaseCamera.autoshotThresholdEvents", 0);
    private boolean showImageHistogram = getPrefs().getBoolean("DavisBaseCamera.showImageHistogram", false);
    private float exposureMs;
    protected int exposureDurationUs;
    protected int frameExposureEndTimestampUs; // end of exposureControlRegister (first events of signal read)
    protected int frameExposureStartTimestampUs; // timestamp of first sample from frame (first sample read after
    protected int frameIntervalUs; // internal measured variable, set during rendering. Time between this frame and
    private int frameCount;
    private float frameRateHz;

    protected IMUSample imuSample; // latest IMUSample from sensor

    private JComponent helpMenuItem1 = null;
    private JComponent helpMenuItem2 = null;
    private JComponent helpMenuItem3 = null;

    private JMenu davisMenu = null;

    /**
     * These points are the first and last pixel APS read out from the array.
     * Subclasses must set and use these values in the firstFrameAddress and
     * lastFrameAddress methods and event filters that transform the APS
     * addresses can modify these values to properly account for the order of
     * readout, e.g. in RotateFilter.
     *
     */
    private Point apsFirstPixelReadOut;
    private Point apsLastPixelReadOut;

    public DavisBaseCamera() {
        super();

        setName("DavisBaseCamera");
        setEventClass(ApsDvsEvent.class);

        setNumCellTypes(3); // two are polarity and last is intensity
        setPixelHeightUm(18.5f);
        setPixelWidthUm(18.5f);

        setEventExtractor(new DavisEventExtractor(this));

        davisDisplayMethod = new DavisDisplayMethod(this);
        getCanvas().addDisplayMethod(davisDisplayMethod);
        getCanvas().setDisplayMethod(davisDisplayMethod);

        davisConfig = null; // Biasgen is assigned in child classes. Needs X/Y sizes.
        setBiasgen(davisConfig);

        davisRenderer = null; // Renderer is assigned in child classes. Needs X/Y sizes.
        setRenderer(davisRenderer);

        autoExposureController = new AutoExposureController(this);

        setApsFirstPixelReadOut(null); // FirstPixel Point assigned in child classes. Needs X/Y sizes.
        setApsLastPixelReadOut(null); // LastPixel Point assigned in child classes. Needs X/Y sizes.

        if (getRemoteControl() != null) {
            getRemoteControl().addCommandListener(this, CMD_EXPOSURE, CMD_EXPOSURE + " val - sets exposure. val in ms.");
            getRemoteControl().addCommandListener(this, CMD_GET_IMU_TEMPERATURE_C, CMD_GET_IMU_TEMPERATURE_C + "returns string value of IMU temperature in Celsius.");
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
        if (davisMenu != null) {
            getAeViewer().removeMenu(davisMenu);
            davisMenu = null;
        }
    }

    @Override
    public void onRegistration() {
        super.onRegistration();
        if (getAeViewer() == null) {
            return;
        }
        helpMenuItem1 = getAeViewer().addHelpURLItem(DavisBaseCamera.HELP_URL_HW_USERGUIDES, "inilabs hardware user guides overview", "Opens product overview guide");
        helpMenuItem2 = getAeViewer().addHelpURLItem(DavisBaseCamera.USER_GUIDE_URL_DAVIS240, "DAVIS240 user guide",
                "Opens DAVIS240 user guide");
        helpMenuItem3 = getAeViewer().addHelpURLItem(DavisBaseCamera.USER_GUIDE_URL_FLASHY, "Flashy user guide",
                "User guide for external tool flashy for firmware/logic updates to devices using the libusb driver");

        davisMenu = new JMenu("DAVIS");
        davisMenu.add(new JMenuItem(new ToggleEventsAction()));
        davisMenu.add(new JMenuItem(new ToggleFrameCaptureDisplayAction()));
        davisMenu.add(new JSeparator());
        davisMenu.add(new JMenuItem(new ToggleAutoContrast()));
        davisMenu.add(new JMenuItem(new ToggleHistogram()));
        davisMenu.add(new JSeparator());
        davisMenu.add(new JMenuItem(new ToggleGlobalRollingShutter()));
        davisMenu.add(new JSeparator());
        davisMenu.add(new JMenuItem(new ToggleAutoExposure()));
        davisMenu.add(new JMenuItem(new IncreaseAPSExposure()));
        davisMenu.add(new JMenuItem(new DecreaseExposureAction()));
        davisMenu.add(new JSeparator());
        davisMenu.add(new JMenuItem(new IncreaseFrameRateAction()));
        davisMenu.add(new JMenuItem(new DecreaseFrameRateAction()));
        davisMenu.add(new JSeparator());
        davisMenu.add(new JMenuItem(new ToggleIMU()));
        davisMenu.getPopupMenu().setLightWeightPopupEnabled(false);
        getAeViewer().addMenu(davisMenu);
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
     * Sets threshold for shooting a frame automatically
     *
     * @param thresholdEvents the number of events to trigger shot on. Less than
     * or equal to zero disables auto-shot.
     */
    @Override
    public void setAutoshotThresholdEvents(int thresholdEvents) {
        if (thresholdEvents < 0) {
            thresholdEvents = 0;
        } else if ((thresholdEvents >> 10) >= 1000) {
            WarningDialogWithDontShowPreference d = new WarningDialogWithDontShowPreference(null, false, "Long autoshot threshold",
                    "<html>You have selected <i>Auto-Shot kevents/frame</i> mode, so that an APS frame is triggered every " + (thresholdEvents >> 10) + " <b>thousand<b> events. <p>Selecting a large"
                    + " number here will make it appear that frame capture is not working");
            d.setVisible(true);
        }

        autoshotThresholdEvents = thresholdEvents;
        getPrefs().putInt("DavisBaseCamera.autoshotThresholdEvents", thresholdEvents);

        if (autoshotThresholdEvents == 0) {
            getDavisConfig().setCaptureFramesEnabled(true);
        }
    }

    @Override
    public boolean isShowImageHistogram() {
        return showImageHistogram;
    }

    @Override
    public void setShowImageHistogram(final boolean yes) {
        showImageHistogram = yes;
        getPrefs().putBoolean("DavisBaseCamera.showImageHistogram", yes);
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
    public void setPowerDown(final boolean powerDown) {
        getDavisConfig().setCaptureEvents(!powerDown);
    }

    @Override
    public void setADCEnabled(final boolean adcEnabled) {
        getDavisConfig().setCaptureFramesEnabled(adcEnabled);
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
        frameCount = 0;

        this.hardwareInterface = hardwareInterface;
        try {
            if (getBiasgen() == null) {
                setBiasgen(new DavisConfig(this));
            } else {
                getBiasgen().setHardwareInterface((BiasgenHardwareInterface) hardwareInterface);
            }
        } catch (final ClassCastException e) {
            Chip.log.warning(e.getMessage() + ": probably this chip object has a biasgen but the hardware interface doesn't, ignoring");
        }
    }

    @Override
    public AEFileInputStreamInterface constuctFileInputStream(final File file) throws IOException {
        frameCount = 0;

        return (super.constuctFileInputStream(file));
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

        protected static final long serialVersionUID = 3890914720599660376L;
        protected static final int WARNING_COUNT_DIVIDER = 10000;
        protected int warningCount = 0;

        protected static final int IMU_WARNING_INTERVAL = 1000;
        protected IMUSample.IncompleteIMUSampleException incompleteIMUSampleException = null;
        protected int missedImuSampleCounter = 0;
        protected int badImuDataCounter = 0;

        protected int autoshotEventsSinceLastShot = 0; // autoshot counter

        public DavisEventExtractor(final DavisBaseCamera chip) {
            super(chip);
        }

        int lastImuTs = 0; // DEBUG

        /**
         * extracts the meaning of the raw events.
         *
         * @param in the raw events, can be null
         * @return out the processed events. these are partially processed
         * in-place. empty packet is returned if null is supplied as in.
         */
        @Override
        synchronized public EventPacket extractPacket(final AEPacketRaw in) {
            if (!(getChip() instanceof DavisChip)) {
                return null;
            }
            if (out == null) {
                out = new ApsDvsEventPacket(getChip().getEventClass());
            } else {
                out.clear();
            }
            out.setRawPacket(in);
            if (in == null) {
                return out;
            }
            final int n = in.getNumEvents(); // addresses.length;
            final int sx1 = getChip().getSizeX() - 1;
            final boolean rollingShutter = !getDavisConfig().isGlobalShutter();

            final int[] datas = in.getAddresses();
            final int[] timestamps = in.getTimestamps();
            final OutputEventIterator outItr = out.outputIterator();
            // NOTE we must make sure we write ApsDvsEvents when we want them, not reuse the IMUSamples

            // at this point the raw data from the USB IN packet has already been digested to extract timestamps,
            // including timestamp wrap events and timestamp resets.
            // The datas array holds the data, which consists of a mixture of AEs and ADC values.
            // Here we extract the datas and leave the timestamps alone.
            // TODO entire rendering / processing approach is not very efficient now
            final int resetReadCode = ApsDvsEvent.ReadoutType.ResetRead.code, signalReadCode = ApsDvsEvent.ReadoutType.SignalRead.code;

            for (int i = 0; i < n; i++) { // TODO implement skipBy/subsampling, but without missing the frame start/end
                // events and still delivering frames
                final int data = datas[i];

                if ((incompleteIMUSampleException != null) || ((DavisChip.ADDRESS_TYPE_IMU & data) == DavisChip.ADDRESS_TYPE_IMU)) {
                    if (IMUSample.extractSampleTypeCode(data) == 0) { // / only start getting an IMUSample at code 0,
                        // the first sample type
                        try {
                            final IMUSample possibleSample = IMUSample.constructFromAEPacketRaw(in, i, incompleteIMUSampleException);
                            i += IMUSample.SIZE_EVENTS - 1;
                            incompleteIMUSampleException = null;
                            imuSample = possibleSample; // asking for sample from AEChip now gives this value
                            final ApsDvsEvent imuEvent = new ApsDvsEvent(); // this davis event holds the IMUSample
                            imuEvent.setTimestamp(imuSample.getTimestampUs());
                            imuEvent.setImuSample(imuSample);
                            outItr.writeToNextOutput(imuEvent); // also write the event out to the next output event
                            // System.out.println("lastImu dt="+(imuSample.timestamp-lastImuTs));
                            // lastImuTs=imuSample.timestamp;
                            continue;
                        } catch (final IMUSample.IncompleteIMUSampleException ex) {
                            incompleteIMUSampleException = ex;
                            if ((missedImuSampleCounter++ % DavisEventExtractor.IMU_WARNING_INTERVAL) == 0) {
                                Chip.log.warning(
                                        String.format("%s (obtained %d partial samples so far)", ex.toString(), missedImuSampleCounter));
                            }
                            break; // break out of loop because this packet only contained part of an IMUSample and
                            // formed the end of the packet anyhow. Next time we come back here we will complete
                            // the IMUSample
                        } catch (final IMUSample.BadIMUDataException ex2) {
                            if ((badImuDataCounter++ % DavisEventExtractor.IMU_WARNING_INTERVAL) == 0) {
                                Chip.log.warning(String.format("%s (%d bad samples so far)", ex2.toString(), badImuDataCounter));
                            }
                            incompleteIMUSampleException = null;
                            continue; // continue because there may be other data
                        }
                    }

                } // not part of IMU sample follows
                else if ((data & DavisChip.ADDRESS_TYPE_MASK) == DavisChip.ADDRESS_TYPE_DVS) {
                    // DVS event
                    final ApsDvsEvent e = nextApsDvsEvent(outItr); // imu sample possibly contained here set to null by
                    // this method
                    if ((data & DavisChip.EXTERNAL_INPUT_EVENT_ADDR) != 0) { // tobi changed to detect just bit set to transmit rising falling and pulse events
//                    if ((data & DavisChip.EVENT_TYPE_MASK) == DavisChip.EXTERNAL_INPUT_EVENT_ADDR) {
                        e.setReadoutType(ReadoutType.DVS);
                        e.setSpecial(true);

                        e.address = data;
                        e.timestamp = (timestamps[i]);
                    } else {
                        e.setReadoutType(ReadoutType.DVS);

                        e.address = data;
                        e.timestamp = (timestamps[i]);
                        e.polarity = (data & DavisChip.POLMASK) == DavisChip.POLMASK ? ApsDvsEvent.Polarity.On : ApsDvsEvent.Polarity.Off;
                        e.type = (byte) ((data & DavisChip.POLMASK) == DavisChip.POLMASK ? 1 : 0);
                        e.x = (short) (sx1 - ((data & DavisChip.XMASK) >>> DavisChip.XSHIFT));
                        e.y = (short) ((data & DavisChip.YMASK) >>> DavisChip.YSHIFT);

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

                    switch ((data & DavisChip.ADC_READCYCLE_MASK) >> DavisChip.ADC_NUMBER_OF_TRAILING_ZEROS) {
                        case 0: // ApsDvsEvent.ReadOutType.ResetRead.code
                            readoutType = ApsDvsEvent.ReadoutType.ResetRead;
                            break;

                        case 1: // ApsDvsEvent.ReadOutType.SignalRead.code
                            readoutType = ApsDvsEvent.ReadoutType.SignalRead;
                            break;

                        case 3:
                            Chip.log.warning("Event with readout cycle null was sent out!");
                            break;

                        default:
                            if ((warningCount < 10) || ((warningCount % DavisEventExtractor.WARNING_COUNT_DIVIDER) == 0)) {
                                Chip.log.warning(
                                        "Event with unknown readout cycle was sent out! You might be reading a file that had the deprecated C readout mode enabled.");
                            }
                            warningCount++;
                            break;
                    }

                    if (pixFirst && (readoutType == ApsDvsEvent.ReadoutType.ResetRead)) {
                        createApsFlagEvent(outItr, ApsDvsEvent.ReadoutType.SOF, timestamp);

                        if (rollingShutter) {
                            // rolling shutter start of exposure (SOE)
                            createApsFlagEvent(outItr, ApsDvsEvent.ReadoutType.SOE, timestamp);
                            frameIntervalUs = timestamp - frameExposureStartTimestampUs;
                            frameExposureStartTimestampUs = timestamp;
                        }
                    }

                    if (pixLast && (readoutType == ApsDvsEvent.ReadoutType.ResetRead) && !rollingShutter) {
                        // global shutter start of exposure (SOE)
                        createApsFlagEvent(outItr, ApsDvsEvent.ReadoutType.SOE, timestamp);
                        frameIntervalUs = timestamp - frameExposureStartTimestampUs;
                        frameExposureStartTimestampUs = timestamp;
                    }

                    final ApsDvsEvent e = nextApsDvsEvent(outItr);
                    e.setReadoutType(readoutType);
                    e.setAdcSample(data & DavisChip.ADC_DATA_MASK);
                    e.address = data;
                    e.timestamp = timestamp;
                    e.type = (byte) (2);
                    e.x = x;
                    e.y = y;

                    // end of exposure, same for both
                    if (pixFirst && (readoutType == ApsDvsEvent.ReadoutType.SignalRead)) {
                        createApsFlagEvent(outItr, ApsDvsEvent.ReadoutType.EOE, timestamp);
                        frameExposureEndTimestampUs = timestamp;
                        exposureDurationUs = timestamp - frameExposureStartTimestampUs;
                    }

                    if (pixLast && (readoutType == ApsDvsEvent.ReadoutType.SignalRead)) {
                        createApsFlagEvent(outItr, ApsDvsEvent.ReadoutType.EOF, timestamp);

                        increaseFrameCount(1);
                    }
                }
            } // loop over raw packet

            if ((getAutoshotThresholdEvents() > 0) && (autoshotEventsSinceLastShot > getAutoshotThresholdEvents())) {
                takeSnapshot();
                autoshotEventsSinceLastShot = 0;
            }

            return out;
        } // extractPacket

        protected ApsDvsEvent nextApsDvsEvent(final OutputEventIterator outItr) {
            final ApsDvsEvent e = (ApsDvsEvent) outItr.nextOutput();
            e.reset();
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
            a.timestamp = timestamp;
            a.setReadoutType(flag);
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
            raw.ensureCapacity(packet.getSize()); // TODO must handle extra capacity needed for inserting multiple raw
            // events for each IMU sample below
            raw.setNumEvents(0);
            final Iterator evItr = apsDVSpacket.fullIterator();
            int k = 0;
            final EventRaw tmpRawEvent = new EventRaw();
//            boolean detectedZeroTimestamp=false;
            while (evItr.hasNext()) {
                final ApsDvsEvent e = (ApsDvsEvent) evItr.next();
                // not writing out these EOF events (which were synthesized on extraction) results in reconstructed
                // packets with giant time gaps, reason unknown
                if (e.isFilteredOut() || e.isEndOfFrame() || e.isStartOfFrame() || e.isStartOfExposure() || e.isEndOfExposure()) {
                    continue; // these flag events were synthesized from data in first place
                }

                if (e.isImuSample()) {
                    final IMUSample imuSample = e.getImuSample();
                    k += imuSample.writeToPacket(raw, k);
                } else {
                    tmpRawEvent.timestamp = e.timestamp;
//                    if(e.timestamp==0 && !detectedZeroTimestamp){
//                        log.warning("zero timestamp detected for event "+e);
//                        detectedZeroTimestamp=true;
//                    }
                    tmpRawEvent.address = reconstructRawAddressFromEvent(e);
                    raw.addEvent(tmpRawEvent);
                    k++;
                }
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
        public int reconstructRawAddressFromEvent(final TypedEvent te) {
            if (!(te instanceof ApsDvsEvent)) {
                throw new RuntimeException("event is not an ApsDvsEvent, cannot reconstruct from it");
            }
            ApsDvsEvent e = (ApsDvsEvent) te;
            int address = te.address;

            if (e.isDVSEvent()) {
                // Do inversion for DVS events.
                final int sx1 = getChip().getSizeX() - 1;
                address = (address & ~DavisChip.XMASK) | ((sx1 - e.x) << DavisChip.XSHIFT);
                address = (address & ~DavisChip.YMASK) | (e.y << DavisChip.YSHIFT);
                address = (address & ~DavisChip.POLMASK) | (e.type << DavisChip.POLSHIFT);
            } else if (e.isApsData()) {
                /* switch ((data & DavisChip.ADC_READCYCLE_MASK) >> DavisChip.ADC_NUMBER_OF_TRAILING_ZEROS) {
                        case 0:
                            readoutType = ApsDvsEvent.ReadoutType.ResetRead;
                            break;

                        case 1:
                            readoutType = ApsDvsEvent.ReadoutType.SignalRead;
                            break;
                 */
                address = (address & ~DavisChip.XMASK) | (e.x << DavisChip.XSHIFT);
                address = (address & ~DavisChip.YMASK) | (e.y << DavisChip.YSHIFT);
                address = (address & ~DavisChip.ADC_DATA_MASK) | (e.getAdcSample() & DavisChip.ADC_DATA_MASK);
                address = (address & ~DavisChip.ADDRESS_TYPE_MASK) | DavisChip.ADDRESS_TYPE_APS;
                switch (e.getReadoutType()) {
                    case ResetRead:
                        address = (address & ~DavisChip.ADC_READCYCLE_MASK) | (ApsDvsEvent.ReadoutType.ResetRead.code << DavisChip.ADC_READCYCLE_SHIFT);
                        break;
                    case SignalRead:
                        address = (address & ~DavisChip.ADC_READCYCLE_MASK) | (ApsDvsEvent.ReadoutType.SignalRead.code << DavisChip.ADC_READCYCLE_SHIFT);
                        break;
                    default:
                        address = (address & ~DavisChip.ADC_READCYCLE_MASK) | (ApsDvsEvent.ReadoutType.Null.code << DavisChip.ADC_READCYCLE_SHIFT);
                }
            } else {
                address = (address & ~DavisChip.XMASK) | (e.x << DavisChip.XSHIFT);
            }

            return address;
        }

        public final void increaseFrameCount(final int i) {
            frameCount += i;
        }

    } // extractor

    public class DavisColorEventExtractor extends DavisBaseCamera.DavisEventExtractor {

        private static final long serialVersionUID = -4739546277540104560L;

        // Special pixel arrangement, where DVS is only found once every four pixels.
        private final boolean isDVSQuarterOfAPS;

        // Wether the DVS pixels also have a color filter or not.
        private final boolean isDVSColorFilter;

        // Color filter pattern arrangement.
        // First lower left, then lower right, then upper right, then upper left.
        private final ColorFilter[] colorFilterSequence;

        // Whether the APS readout follows normal procedure (reset then signal read), or
        // the special readout: signal then readout mix.
        private final boolean isAPSSpecialReadout;

        public DavisColorEventExtractor(final DavisBaseCamera chip, final boolean isDVSQuarterOfAPS, final boolean isDVSColorFilter,
                final ColorFilter[] colorFilterSequence, final boolean isAPSSpecialReadout) {
            super(chip);

            this.isDVSQuarterOfAPS = isDVSQuarterOfAPS;
            this.isDVSColorFilter = isDVSColorFilter;
            this.colorFilterSequence = colorFilterSequence;
            this.isAPSSpecialReadout = isAPSSpecialReadout;
        }

        /**
         * extracts the meaning of the raw events.
         *
         * @param in the raw events, can be null
         * @return out the processed events. these are partially processed
         * in-place. empty packet is returned if null is supplied as in.
         */
        @Override
        synchronized public EventPacket extractPacket(final AEPacketRaw in) {
            if (!(getChip() instanceof DavisChip)) {
                return null;
            }
            if (out == null) {
                out = new ApsDvsEventPacket(getChip().getEventClass());
            } else {
                out.clear();
            }
            out.setRawPacket(in);
            if (in == null) {
                return out;
            }
            final int n = in.getNumEvents(); // addresses.length;
            final int sx1 = ((isDVSQuarterOfAPS) ? (getChip().getSizeX() / 2) : (getChip().getSizeX())) - 1;
            final boolean rollingShutter = !getDavisConfig().isGlobalShutter();

            final int[] datas = in.getAddresses();
            final int[] timestamps = in.getTimestamps();
            final OutputEventIterator outItr = out.outputIterator();
            // NOTE we must make sure we write ApsDvsEvents when we want them, not reuse the IMUSamples

            // at this point the raw data from the USB IN packet has already been digested to extract timestamps,
            // including timestamp wrap events and timestamp resets.
            // The datas array holds the data, which consists of a mixture of AEs and ADC values.
            // Here we extract the datas and leave the timestamps alone.
            // TODO entire rendering / processing approach is not very efficient now
            for (int i = 0; i < n; i++) { // TODO implement skipBy/subsampling, but without missing the frame start/end
                // events and still delivering frames
                final int data = datas[i];

                if ((incompleteIMUSampleException != null) || ((DavisChip.ADDRESS_TYPE_IMU & data) == DavisChip.ADDRESS_TYPE_IMU)) {
                    if (IMUSample.extractSampleTypeCode(data) == 0) { // / only start getting an IMUSample at code 0,
                        // the first sample type
                        try {
                            final IMUSample possibleSample = IMUSample.constructFromAEPacketRaw(in, i, incompleteIMUSampleException);
                            i += IMUSample.SIZE_EVENTS - 1;
                            incompleteIMUSampleException = null;
                            imuSample = possibleSample; // asking for sample from AEChip now gives this value
                            final ApsDvsEvent imuEvent = new ApsDvsEvent(); // this davis event holds the IMUSample
                            imuEvent.setTimestamp(imuSample.getTimestampUs());
                            imuEvent.setImuSample(imuSample);
                            outItr.writeToNextOutput(imuEvent); // also write the event out to the next output event
                            // System.out.println("lastImu dt="+(imuSample.timestamp-lastImuTs));
                            // lastImuTs=imuSample.timestamp;
                            continue;
                        } catch (final IMUSample.IncompleteIMUSampleException ex) {
                            incompleteIMUSampleException = ex;
                            if ((missedImuSampleCounter++ % DavisEventExtractor.IMU_WARNING_INTERVAL) == 0) {
                                Chip.log.warning(
                                        String.format("%s (obtained %d partial samples so far)", ex.toString(), missedImuSampleCounter));
                            }
                            break; // break out of loop because this packet only contained part of an IMUSample and
                            // formed the end of the packet anyhow. Next time we come back here we will complete
                            // the IMUSample
                        } catch (final IMUSample.BadIMUDataException ex2) {
                            if ((badImuDataCounter++ % DavisEventExtractor.IMU_WARNING_INTERVAL) == 0) {
                                Chip.log.warning(String.format("%s (%d bad samples so far)", ex2.toString(), badImuDataCounter));
                            }
                            incompleteIMUSampleException = null;
                            continue; // continue because there may be other data
                        }
                    }

                } else if ((data & DavisChip.ADDRESS_TYPE_MASK) == DavisChip.ADDRESS_TYPE_DVS) {
                    // DVS event
                    final ApsDvsEvent e = nextApsDvsEvent(outItr);

                    if ((data & DavisChip.EVENT_TYPE_MASK) == DavisChip.EXTERNAL_INPUT_EVENT_ADDR) {
                        e.setReadoutType(ReadoutType.DVS);
                        e.setSpecial(true);

                        e.address = data;
                        e.timestamp = (timestamps[i]);
                    } else {
                        e.setReadoutType(ReadoutType.DVS);

                        e.address = data;
                        e.timestamp = (timestamps[i]);
                        e.polarity = (data & DavisChip.POLMASK) == DavisChip.POLMASK ? ApsDvsEvent.Polarity.On : ApsDvsEvent.Polarity.Off;
                        e.type = (byte) ((data & DavisChip.POLMASK) == DavisChip.POLMASK ? 1 : 0);
                        e.x = (short) (sx1 - ((data & DavisChip.XMASK) >>> DavisChip.XSHIFT));
                        e.y = (short) ((data & DavisChip.YMASK) >>> DavisChip.YSHIFT);

                        if (isDVSQuarterOfAPS) {
                            e.x *= 2;
                            e.y *= 2;
                        }

                        // DVS COLOR SUPPORT.
                        if (isDVSColorFilter) {
                            if ((e.y % 2) == 0) {
                                if ((e.x % 2) == 0) {
                                    // Lower left.
                                    e.setColorFilter(colorFilterSequence[0]);
                                } else {
                                    // Lower right.
                                    e.setColorFilter(colorFilterSequence[1]);
                                }
                            } else if ((e.x % 2) == 0) {
                                // Upper left.
                                e.setColorFilter(colorFilterSequence[3]);
                            } else {
                                // Upper right.
                                e.setColorFilter(colorFilterSequence[2]);
                            }
                        }

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

                    ApsDvsEvent.ColorFilter ColorFilter = ApsDvsEvent.ColorFilter.W;

                    if ((y % 2) == 0) {
                        if ((x % 2) == 0) {
                            // Lower left.
                            ColorFilter = colorFilterSequence[0];
                        } else {
                            // Lower right.
                            ColorFilter = colorFilterSequence[1];
                        }
                    } else if ((x % 2) == 0) {
                        // Upper left.
                        ColorFilter = colorFilterSequence[3];
                    } else {
                        // Upper right.
                        ColorFilter = colorFilterSequence[2];
                    }

                    final boolean pixFirst = firstFrameAddress(x, y); // First event of frame (addresses get flipped)
                    final boolean pixLast = lastFrameAddress(x, y); // Last event of frame (addresses get flipped)

                    ApsDvsEvent.ReadoutType readoutType = ApsDvsEvent.ReadoutType.Null;

                    switch ((data & DavisChip.ADC_READCYCLE_MASK) >>> DavisChip.ADC_NUMBER_OF_TRAILING_ZEROS) {
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
                                Chip.log.warning("Event with unknown readout cycle was sent out!.");
                            }
                            warningCount++;
                            break;
                    }

                    if (!isAPSSpecialReadout) {
                        if (pixFirst && (readoutType == ApsDvsEvent.ReadoutType.ResetRead)) {
                            createApsFlagEvent(outItr, ApsDvsEvent.ReadoutType.SOF, timestamp);

                            if (rollingShutter) {
                                // rolling shutter start of exposure (SOE)
                                createApsFlagEvent(outItr, ApsDvsEvent.ReadoutType.SOE, timestamp);
                                frameIntervalUs = timestamp - frameExposureStartTimestampUs;
                                frameExposureStartTimestampUs = timestamp;
                            }
                        }

                        if (pixLast && (readoutType == ApsDvsEvent.ReadoutType.ResetRead) && !rollingShutter) {
                            // global shutter start of exposure (SOE)
                            createApsFlagEvent(outItr, ApsDvsEvent.ReadoutType.SOE, timestamp);
                            frameIntervalUs = timestamp - frameExposureStartTimestampUs;
                            frameExposureStartTimestampUs = timestamp;
                        }
                    } else {
                        // Start of Frame (SOF)
                        // TODO: figure out exposure/interval for both GS and RS.
                        if (pixFirst && rollingShutter && (readoutType == ApsDvsEvent.ReadoutType.ResetRead)) { // RS
                            createApsFlagEvent(outItr, ApsDvsEvent.ReadoutType.SOF, timestamp);

                            frameIntervalUs = timestamp - frameExposureStartTimestampUs;
                            frameExposureStartTimestampUs = timestamp; // TODO: incorrect, not exposure start!
                        }

                        if (pixFirst && !rollingShutter && (readoutType == ApsDvsEvent.ReadoutType.SignalRead)) { // GS
                            createApsFlagEvent(outItr, ApsDvsEvent.ReadoutType.SOF, timestamp);

                            frameIntervalUs = timestamp - frameExposureStartTimestampUs;
                            frameExposureStartTimestampUs = timestamp; // TODO: incorrect, not exposure start!
                        }
                    }

                    final ApsDvsEvent e = nextApsDvsEvent(outItr);
                    e.setReadoutType(readoutType);
                    e.setAdcSample(data & DavisChip.ADC_DATA_MASK);
                    e.address = data;
                    e.timestamp = timestamp;
                    e.type = (byte) (2);
                    e.x = x;
                    e.y = y;

                    // APS COLOR SUPPORT.
                    e.setColorFilter(ColorFilter);

                    if (!isAPSSpecialReadout) {
                        // end of exposure, same for both
                        if (pixFirst && (readoutType == ApsDvsEvent.ReadoutType.SignalRead)) {
                            createApsFlagEvent(outItr, ApsDvsEvent.ReadoutType.EOE, timestamp);
                            frameExposureEndTimestampUs = timestamp;
                            exposureDurationUs = timestamp - frameExposureStartTimestampUs;
                        }

                        if (pixLast && (readoutType == ApsDvsEvent.ReadoutType.SignalRead)) {
                            createApsFlagEvent(outItr, ApsDvsEvent.ReadoutType.EOF, timestamp);

                            increaseFrameCount(1);
                        }
                    } else {
                        // End of Frame (EOF)
                        // TODO: figure out exposure/interval for both GS and RS.
                        if (pixLast && rollingShutter && (readoutType == ApsDvsEvent.ReadoutType.SignalRead)) {
                            // if we use ResetRead+SignalRead+C readout, OR, if we use ResetRead-SignalRead readout and
                            // we
                            // are at last APS pixel, then write EOF event
                            // insert a new "end of frame" event not present in original data
                            createApsFlagEvent(outItr, ApsDvsEvent.ReadoutType.EOF, timestamp);

                            increaseFrameCount(1);
                        }

                        if (pixLast && !rollingShutter && (readoutType == ApsDvsEvent.ReadoutType.ResetRead)) {
                            // if we use ResetRead+SignalRead+C readout, OR, if we use ResetRead-SignalRead readout and
                            // we
                            // are at last APS pixel, then write EOF event
                            // insert a new "end of frame" event not present in original data
                            createApsFlagEvent(outItr, ApsDvsEvent.ReadoutType.EOF, timestamp);

                            increaseFrameCount(1);
                        }
                    }
                }
            }

            if ((getAutoshotThresholdEvents() > 0) && (autoshotEventsSinceLastShot > getAutoshotThresholdEvents())) {
                takeSnapshot();
                autoshotEventsSinceLastShot = 0;
            }

            return out;
        } // extractPacket

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

            if (((ApsDvsEvent) e).isDVSEvent()) {
                // Do inversion for DVS events.
                if (isDVSQuarterOfAPS) {
                    final int sx1 = (getChip().getSizeX() / 2) - 1;
                    address = (address & ~DavisChip.XMASK) | ((sx1 - (e.x / 2)) << DavisChip.XSHIFT);
                    address = (address & ~DavisChip.YMASK) | ((e.y / 2) << DavisChip.YSHIFT);
                } else {
                    final int sx1 = getChip().getSizeX() - 1;
                    address = (address & ~DavisChip.XMASK) | ((sx1 - e.x) << DavisChip.XSHIFT);
                    address = (address & ~DavisChip.YMASK) | (e.y << DavisChip.YSHIFT);
                }
            } else {
                address = (address & ~DavisChip.XMASK) | (e.x << DavisChip.XSHIFT);
                address = (address & ~DavisChip.YMASK) | (e.y << DavisChip.YSHIFT);
            }

            return address;
        }
    } // extractor

    /**
     * Displays data from DAVIS camera
     *
     * @author Tobi
     */
    public class DavisDisplayMethod extends ChipRendererDisplayMethodRGBA {

        private static final int FONTSIZE = 24;
        private static final int FRAME_COUNTER_BAR_LENGTH_FRAMES = 10;

//        private TextRenderer exposureRenderer = null; // memory hog

        public DavisDisplayMethod(final DavisBaseCamera chip) {
            super(chip.getCanvas());
            getCanvas().setBorderSpacePixels(getPrefs().getInt("borderSpacePixels", 70));
        }

        @Override
        public void display(final GLAutoDrawable drawable) {
            super.display(drawable);

//            if (exposureRenderer == null) { // recreate every frame, memory hog otherwise
                final TextRenderer exposureTextRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, DavisDisplayMethod.FONTSIZE), true, true);
//            }

            if ((getHardwareInterface() != null) && (getHardwareInterface() instanceof CypressFX3)) {
                final CypressFX3 fx3HwIntf = (CypressFX3) getHardwareInterface();

                if (fx3HwIntf.isTimestampMaster() == false) {
                    exposureTextRenderer.setColor(Color.WHITE);
                    exposureTextRenderer.begin3DRendering();
                    exposureTextRenderer.draw3D("Slave camera", 0, -(DavisDisplayMethod.FONTSIZE / 2), 0, 0.4f);
                    exposureTextRenderer.end3DRendering();
                }
            }

            if ((getDavisConfig().getVideoControl() != null) && getDavisConfig().getVideoControl().isDisplayFrames()) {
                final GL2 gl = drawable.getGL().getGL2();
                exposureRender(gl, exposureTextRenderer);
            }

            // draw sample histogram
            if (isShowImageHistogram() && getDavisConfig().isDisplayFrames() && (renderer instanceof AEFrameChipRenderer)) {
                // System.out.println("drawing hist");
                final int size = 100;
                final AbstractHistogram hist = ((AEFrameChipRenderer) renderer).getAdcSampleValueHistogram();
                final GL2 gl = drawable.getGL().getGL2();
                gl.glPushAttrib(GL.GL_COLOR_BUFFER_BIT);
                gl.glColor3f(0, 0, 1);
                gl.glLineWidth(1f);
                hist.draw(drawable, exposureTextRenderer, (sizeX / 2) - (size / 2), (sizeY / 2) + (size / 2), size, size);
                gl.glPopAttrib();
            }

            // Draw last IMU output
            if ((getDavisConfig() != null) && getDavisConfig().isDisplayImu() && (chip instanceof DavisBaseCamera)) {
                final IMUSample imuSampleRender = ((DavisBaseCamera) chip).getImuSample();
                if (imuSampleRender != null) {
                    imuRender(drawable, imuSampleRender);
                }
            }

            displayStatusChangeText(drawable);
        }

        
        GLUquadric accelCircle = null;

        private void imuRender(final GLAutoDrawable drawable, final IMUSample imuSampleRender) {
            // System.out.println("on rendering: "+imuSample.toString());
            final GL2 gl = drawable.getGL().getGL2();
            final TextRenderer imuTextRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 36)); // recreate, memory hog if instance variable
            gl.glPushMatrix();

            gl.glTranslatef(chip.getSizeX() / 2, chip.getSizeY() / 2, 0);
            gl.glLineWidth(3);

            final float vectorScale = 1f;
            final float textScale = TextRendererScale.draw3dScale(imuTextRenderer,
                    "XXX.XXf,%XXX.XXf dps", getChipCanvas().getScale(),
                    getSizeX(), .3f);
            final float trans = .9f;
            float x, y;

            // acceleration x,y
            x = ((vectorScale * imuSampleRender.getAccelX() * getSizeY()) / 2) / IMUSample.getFullScaleAccelG();
            y = ((vectorScale * imuSampleRender.getAccelY() * getSizeY()) / 2) / IMUSample.getFullScaleAccelG();
            gl.glColor3f(0, 1, 0);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(0, 0);
            gl.glVertex2f(x, y);
            gl.glEnd();

            imuTextRenderer.begin3DRendering();
            imuTextRenderer.setColor(0, .5f, 0, trans);
            imuTextRenderer.draw3D(String.format("%.2f,%.2f g", imuSampleRender.getAccelX(), imuSampleRender.getAccelY()), x, y, 0,
                    textScale); // x,y,z,
            // scale
            // factor
            imuTextRenderer.end3DRendering();

            // acceleration z, drawn as circle
            if (glu == null) {
                glu = new GLU();
            }
            if (accelCircle == null) {
                accelCircle = glu.gluNewQuadric();
            }
            final float az = ((vectorScale * imuSampleRender.getAccelZ() * getSizeY())) / IMUSample.getFullScaleAccelG();
            final float rim = .5f;
            glu.gluQuadricDrawStyle(accelCircle, GLU.GLU_FILL);
            glu.gluDisk(accelCircle, az - rim, az + rim, 16, 1);

            imuTextRenderer.begin3DRendering();
            imuTextRenderer.setColor(0, .5f, 0, trans);
            final String saz = String.format("%.2f g", imuSampleRender.getAccelZ());
            final Rectangle2D rect = imuTextRenderer.getBounds(saz);
            imuTextRenderer.draw3D(saz, az, -(float) rect.getHeight() * textScale * 0.5f, 0, textScale);
            imuTextRenderer.end3DRendering();

            // gyro pan/tilt
            gl.glColor3f(1f, 0, 1);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(0, 0);
            x = ((vectorScale * imuSampleRender.getGyroYawY() * getMinSize()) / 2) / IMUSample.getFullScaleGyroDegPerSec();
            y = ((vectorScale * imuSampleRender.getGyroTiltX() * getMinSize()) / 2) / IMUSample.getFullScaleGyroDegPerSec();
            gl.glVertex2f(x, y);
            gl.glEnd();

            imuTextRenderer.begin3DRendering();
            imuTextRenderer.setColor(1f, 0, 1, trans);
            imuTextRenderer.draw3D(String.format("%.2f,%.2f dps", imuSampleRender.getGyroYawY(), imuSampleRender.getGyroTiltX()), x, y + 5,
                    0, textScale); // x,y,z, scale factor
            imuTextRenderer.end3DRendering();

            // gyro roll
            x = ((vectorScale * imuSampleRender.getGyroRollZ() * getMinSize()) / 2) / IMUSample.getFullScaleGyroDegPerSec();
            y = chip.getSizeY() * .25f;
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(0, y);
            gl.glVertex2f(x, y);
            gl.glEnd();

            imuTextRenderer.begin3DRendering();
            imuTextRenderer.draw3D(String.format("%.2f dps", imuSampleRender.getGyroRollZ()), x, y, 0, textScale);
            imuTextRenderer.end3DRendering();

            // color annotation to show what is being rendered
            imuTextRenderer.begin3DRendering();
            imuTextRenderer.setColor(1f, 1f, 1f, trans);
//            final String ratestr = String.format("IMU: timestamp=%+9.3fs last dtMs=%6.1fms  avg dtMs=%6.1fms deg C=%5.1fC",
//                    1e-6f * imuSampleRender.getTimestampUs(), imuSampleRender.getDeltaTimeUs() * .001f,
//                    IMUSample.getAverageSampleIntervalUs() / 1000, imuSampleRender.getTemperature());
            final String ratestr = String.format("IMU: last dt=%6.1fms temperature=%5.1fC",
                    imuSampleRender.getDeltaTimeUs() * .001f,
                    imuSampleRender.getTemperature());
            final Rectangle2D raterect = imuTextRenderer.getBounds(ratestr);
            imuTextRenderer.draw3D(ratestr, -(float) raterect.getWidth() * textScale * 0.5f * 1f, -20, 0, textScale * 1f); // x,y,z,
            // scale
            // factor
            imuTextRenderer.end3DRendering();

            gl.glPopMatrix();
        }

        private void exposureRender(final GL2 gl, final TextRenderer textRenderer) {
            gl.glPushMatrix();

            textRenderer.setColor(Color.WHITE);
            textRenderer.begin3DRendering();
            if (frameIntervalUs > 0) {
                setFrameRateHz((float) 1000000 / frameIntervalUs);
            }
            setMeasuredExposureMs((float) exposureDurationUs / 1000);
            final String s = String.format("Frame: %d; Exposure %.2f ms; Frame rate: %.2f Hz", getFrameCount(), getMeasuredExposureMs(),
                    getFrameRateHz());
            final float scale = TextRendererScale.draw3dScale(textRenderer, s, getChipCanvas().getScale(), getSizeX(), 1f);
            // determine width of string in pixels and scale accordingly
            textRenderer.draw3D(s, 0, getSizeY() + ((DavisDisplayMethod.FONTSIZE / 2) * scale), 0, scale);
            textRenderer.end3DRendering();

            final int nframes = getFrameCount() % DavisDisplayMethod.FRAME_COUNTER_BAR_LENGTH_FRAMES;
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

    /**
     * Triggers shot of one APS frame
     */
    @Override
    public void takeSnapshot() {
        // Use a multi-command to send enable and then disable in quickest possible
        // succession to the APS state machine.
        if ((getHardwareInterface() != null) && (getHardwareInterface() instanceof CypressFX3)) {
            final CypressFX3 fx3HwIntf = (CypressFX3) getHardwareInterface();
            final SPIConfigSequence configSequence = fx3HwIntf.new SPIConfigSequence();

            try {
                configSequence.addConfig(CypressFX3.FPGA_APS, (short) 4, 1);
                configSequence.addConfig(CypressFX3.FPGA_APS, (short) 4, 0);

                configSequence.sendConfigSequence();
            } catch (final HardwareInterfaceException e) {
                // Ignore.
            }
        }
    }

    /**
     * Subclasses should set the apsFirstPixelReadOut and apsLastPixelReadOut
     *
     * @param x the x location of APS readout
     * @param y the y location of APS readout
     * @see #apsFirstPixelReadOut
     */
    public boolean firstFrameAddress(final short x, final short y) {
        return (x == getApsFirstPixelReadOut().x) && (y == getApsFirstPixelReadOut().y);
    }

    /**
     * Subclasses should set the apsFirstPixelReadOut and apsLastPixelReadOut
     *
     * @param x the x location of APS readout
     * @param y the y location of APS readout
     * @see #apsLastPixelReadOut
     */
    public boolean lastFrameAddress(final short x, final short y) {
        return (x == getApsLastPixelReadOut().x) && (y == getApsLastPixelReadOut().y);
    }

    /**
     * @return the apsFirstPixelReadOut
     */
    public Point getApsFirstPixelReadOut() {
        return apsFirstPixelReadOut;
    }

    /**
     * @param apsFirstPixelReadOut the apsFirstPixelReadOut to set
     */
    public void setApsFirstPixelReadOut(final Point apsFirstPixelReadOut) {
        this.apsFirstPixelReadOut = apsFirstPixelReadOut;
    }

    /**
     * @return the apsLastPixelReadOut
     */
    public Point getApsLastPixelReadOut() {
        return apsLastPixelReadOut;
    }

    /**
     * @param apsLastPixelReadOut the apsLastPixelReadOut to set
     */
    public void setApsLastPixelReadOut(final Point apsLastPixelReadOut) {
        this.apsLastPixelReadOut = apsLastPixelReadOut;
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
        } else if (c.equals(CMD_GET_IMU_TEMPERATURE_C)) {
            return String.format("%.3f", getImuSample().getTemperature());
        }
        return "successfully processed command " + input;
    }

    /**
     * @return the autoExposureController
     */
    @Override
    public AutoExposureController getAutoExposureController() {
        return autoExposureController;
    }

    @Override
    public boolean isAutoExposureEnabled() {
        return getAutoExposureController().isAutoExposureEnabled();
    }

    @Override
    public void controlExposure() {
        getAutoExposureController().controlExposure();
    }

    @Override
    public void setAutoExposureEnabled(final boolean yes) {
        getAutoExposureController().setAutoExposureEnabled(yes);
    }

    /**
     * Used to add custom Menu items and keyboard accelerators for DAVIS cameras
     */
    abstract public class DavisMenuAction extends AbstractAction {

        protected final String path = "/eu/seebetter/ini/chips/davis/icons/"; // C:\Users\tobi\Documents\~jaer2\jAER\trunk\build\classes\net\sf\jaer\graphics\icons

        public DavisMenuAction() {
            super();
        }

        public DavisMenuAction(String name, String tooltip, String icon) {
            putValue(Action.NAME, name);
            URL url = getClass().getResource(path + icon + ".gif");
            if (url != null) {
                putValue(Action.SMALL_ICON, new javax.swing.ImageIcon(url));
            }
            putValue("hideActionText", "true");
            putValue(Action.SHORT_DESCRIPTION, tooltip);
        }
    }

    /**
     * Adds frame capture/display toggle
     */
    final public class ToggleFrameCaptureDisplayAction extends DavisMenuAction {

        public ToggleFrameCaptureDisplayAction() {
            super("ToggleFrames", "Toggle DAVIS frame capture and display", "ToggleFrames");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_F, java.awt.event.InputEvent.SHIFT_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            boolean old = getDavisConfig().isDisplayFrames();
            getDavisConfig().setCaptureFramesEnabled(!old);
            getDavisConfig().setDisplayFrames(!old);
            log.info("capturing and displaying frames = " + getDavisConfig().isCaptureFramesEnabled());
            davisDisplayMethod.showStatusChangeText("frames=" + getDavisConfig().isCaptureFramesEnabled());
            putValue(Action.SELECTED_KEY, true);
        }
    }

    /**
     * Adds event capture/display option
     */
    final public class ToggleEventsAction extends DavisMenuAction {

        public ToggleEventsAction() {
            super("ToggleEvents", "Toggle DAVIS event capture and display", "ToggleEvents");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_E, java.awt.event.InputEvent.SHIFT_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            boolean old = getDavisConfig().isDisplayEvents();
            getDavisConfig().setCaptureEvents(!old);
            getDavisConfig().setDisplayEvents(!old);
            log.info("capturing and displaying events = " + getDavisConfig().isCaptureEventsEnabled());
            davisDisplayMethod.showStatusChangeText("events=" + getDavisConfig().isCaptureEventsEnabled());
            putValue(Action.SELECTED_KEY, true);
        }
    }

    /**
     * Adds event capture/display option
     */
    final public class ToggleHistogram extends DavisMenuAction {

        public ToggleHistogram() {
            super("Toggle APS Histogram Display", "Toggles whether the histogram of APS levels is display", "ToggleHistogram");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_H, java.awt.event.InputEvent.SHIFT_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            setShowImageHistogram(!isShowImageHistogram());
            log.info("autoContrast = " + isShowImageHistogram());
            putValue(Action.SELECTED_KEY, true);
        }
    }

    /**
     * Adds event capture/display option
     */
    final public class ToggleAutoContrast extends DavisMenuAction {

        public ToggleAutoContrast() {
            super("Toggle APS AutoContrast",
                    "<html>Toggles whether automatic display contrast control is enabled<p>See <i>Auto contrast</i>  and <i>Constrat</i> controls in the <i>User-Friendly Controls</i> tab in HW Configuration panel for full control."
                    + "<p>Note that this control is only for displayed image rendering.",
                    "ToggleAutoContrast");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_C, java.awt.event.InputEvent.SHIFT_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            DavisVideoContrastController controller = ((AEFrameChipRenderer) getRenderer()).getContrastController();
            controller.setUseAutoContrast(!controller.isUseAutoContrast());
            log.info("autoContrast = " + controller.isUseAutoContrast());
            davisDisplayMethod.showStatusChangeText("autoContrast = " + controller.isUseAutoContrast());
            putValue(Action.SELECTED_KEY, true);
        }
    }

    /**
     * Adds event capture/display option
     */
    final public class ToggleGlobalRollingShutter extends DavisMenuAction {

        public ToggleGlobalRollingShutter() {
            super("Toggle Global/Rolling shutter mode",
                    "<html>Toggles global vs. rolling shutter mode. See <i>User Friendly Controls</i> or <i>APS Config</i> tab in HW configuration panel for full control",
                    "ToggleGlobalRollingShutter");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_G, java.awt.event.InputEvent.SHIFT_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            getDavisConfig().setGlobalShutter(!getDavisConfig().isGlobalShutter());
            log.info("globalShutter = " + getDavisConfig().isGlobalShutter());
            davisDisplayMethod.showStatusChangeText("globalShutter = " + getDavisConfig().isGlobalShutter());
            putValue(Action.SELECTED_KEY, true);
        }
    }

    /**
     * Adds event capture/display option
     */
    final public class ToggleAutoExposure extends DavisMenuAction {

        public ToggleAutoExposure() {
            super("Toggle APS Autoexposure",
                    "<html>Toggles whether autoexposure control is enabled<p>See <i>APS AutoExposure Control</i> tab in HW configuration panel for full control",
                    "ToggleAutoExposure");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_A, java.awt.event.InputEvent.SHIFT_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            setAutoExposureEnabled(!isAutoExposureEnabled());
            log.info("autoExposure = " + isAutoExposureEnabled());
            davisDisplayMethod.showStatusChangeText("autoExposure = " + isAutoExposureEnabled());
            putValue(Action.SELECTED_KEY, true);
        }
    }

    final float exposureChangeFactor = (float) Math.sqrt(2);

    /**
     * Adds event capture/display option
     */
    final public class DecreaseExposureAction extends DavisMenuAction {

        public DecreaseExposureAction() {
            super("Decrease APS exposure",
                    "<html>Decreases APS exposure<p>See <i>User-Friendly Controls</i> tab in HW configuration panel for more control",
                    "DecreaseExposure");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, java.awt.event.InputEvent.SHIFT_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            try {
                getDavisConfig().setExposureDelayMs(getDavisConfig().getExposureDelayMs() / exposureChangeFactor);
            } catch (IllegalArgumentException ex) {
            }
            final String s = "set exposure delay = " + getDavisConfig().getExposureDelayMs() + " ms";
            log.info(s);
            davisDisplayMethod.showStatusChangeText(s);
            putValue(Action.SELECTED_KEY, true);
        }
    }

    final public class IncreaseAPSExposure extends DavisMenuAction {

        public IncreaseAPSExposure() {
            super("Increase APS exposure",
                    "<html>Increases APS exposure<p>See <i>User-Friendly Controls</i> tab in HW configuration panel for more control",
                    "IncreaseExposure");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_UP, java.awt.event.InputEvent.SHIFT_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            try {
                int expUsNow = (int) (1000 * getDavisConfig().getExposureDelayMs());
                int expUsNew = (int) (expUsNow * exposureChangeFactor);
                if (expUsNew == expUsNow) {
                    expUsNew++; // solve problem of being stuck as 1us
                }
                getDavisConfig().setExposureDelayMs(0.001f * expUsNew);
            } catch (IllegalArgumentException ex) {
            }
            final String s = "set exposure delay = " + getDavisConfig().getExposureDelayMs() + " ms";
            log.info(s);
            davisDisplayMethod.showStatusChangeText(s);
            putValue(Action.SELECTED_KEY, true);
        }
    }

    /**
     * Adds event capture/display option
     */
    final public class DecreaseFrameRateAction extends DavisMenuAction {

        public DecreaseFrameRateAction() {
            super("Decrease APS frame rate",
                    "<html>Decreases APS rate by increasing frame delay<p>See <i>User-Friendly Controls</i> tab in HW configuration panel for more control",
                    "DecreaseFrameRate");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, java.awt.event.InputEvent.SHIFT_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            try {
                float d = getDavisConfig().getFrameDelayMs() * exposureChangeFactor;
                if (d < .1f) {
                    d = .1f;
                }
                getDavisConfig().setFrameDelayMs(d);
            } catch (IllegalArgumentException ex) {

            }
            final String s = "set frame delay = " + getDavisConfig().getFrameDelayMs() + " ms";
            log.info(s);
            davisDisplayMethod.showStatusChangeText(s);
            putValue(Action.SELECTED_KEY, true);
        }
    }

    /**
     * Adds event capture/display option
     */
    final public class IncreaseFrameRateAction extends DavisMenuAction {

        public IncreaseFrameRateAction() {
            super("Increase APS frame rate",
                    "<html>Increases APS rate by descrasing frame delay<p>See <i>User-Friendly Controls</i> tab in HW configuration panel for more control",
                    "IncreaseFrameRate");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, java.awt.event.InputEvent.SHIFT_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            try {
                getDavisConfig().setFrameDelayMs(getDavisConfig().getFrameDelayMs() / exposureChangeFactor);
            } catch (IllegalArgumentException ex) {
            }
            final String s = "set frame delay = " + getDavisConfig().getFrameDelayMs() + " ms";
            log.info(s);
            davisDisplayMethod.showStatusChangeText(s);
            putValue(Action.SELECTED_KEY, true);
        }
    }

    final public class ToggleIMU extends DavisMenuAction {

        public ToggleIMU() {
            super("Toggle IMU",
                    "<html>Toggles IMU (inertial measurement unit) capture and display<p>See <i>IMU Config</i> tab in HW configuration panel for more control",
                    "ToggleIMU");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_I, java.awt.event.InputEvent.SHIFT_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            boolean old = getDavisConfig().isImuEnabled();
            getDavisConfig().setImuEnabled(!old);
            getDavisConfig().setDisplayImu(!old);
            davisDisplayMethod.showStatusChangeText("IMU enabled = " + getDavisConfig().isImuEnabled());
            putValue(Action.SELECTED_KEY, true);
        }
    }

}
