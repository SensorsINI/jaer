package eu.seebetter.ini.chips.davis;

import net.sf.jaer.Description;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.ApsDvsEventRGBW;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.TypedEvent;

/**
 * Base camera for Tower Davis208PixelParade cameras
 *
 * @author Diederik Paul Moeys, Luca Longinotti
 */
@Description("Davis208PixelParade base class for 208x192 pixel sensitive APS-DVS DAVIS sensor")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class Davis208PixelParade extends DavisBaseCamera {

    public static final short WIDTH_PIXELS = 208;
    public static final short HEIGHT_PIXELS = 192;
    protected Davis208PixelParadeConfig davisConfig;

    /**
     * Creates a new instance.
     */
    public Davis208PixelParade() {
        setName("Davis208PixelParade");
        setDefaultPreferencesFile("biasgenSettings/Davis208PixelParade/Davis208PixelParade.xml");
        setSizeX(WIDTH_PIXELS);
        setSizeY(HEIGHT_PIXELS);
        setBiasgen(davisConfig = new Davis208PixelParadeConfig(this));
        setEventExtractor(new Davis208PixelParadeEventExtractor(this));
        apsDVSrenderer = new Davis208PixelParadeRenderer(this); // must be called after configuration is constructed, because it needs to know if frames are enabled to reset pixmap
        apsDVSrenderer.setMaxADC(DavisChip.MAX_ADC);
        setRenderer(apsDVSrenderer);
        setEventClass(ApsDvsEventRGBW.class);
    }

    /**
     * Creates a new instance
     *
     * @param hardwareInterface an existing hardware interface. This constructor
     * is preferred. It makes a new cDVSTest10Biasgen object to talk to the
     * on-chip biasgen.
     */
    public Davis208PixelParade(final HardwareInterface hardwareInterface) {
        this();
        setHardwareInterface(hardwareInterface);
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
    public class Davis208PixelParadeEventExtractor extends DavisBaseCamera.DavisEventExtractor {

        public Davis208PixelParadeEventExtractor(DavisBaseCamera chip) {
            super(chip);
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
			// NOTE we must make sure we write ApsDvsEventRGBWs when we want them, not reuse the IMUSamples

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
                    final ApsDvsEventRGBW e = nextApsDvsEvent(outItr);
                    if ((data & DavisChip.EVENT_TYPE_MASK) == DavisChip.EXTERNAL_INPUT_EVENT_ADDR) {
                        e.adcSample = -1; // TODO hack to mark as not an ADC sample
                        e.special = true; // TODO special is set here when capturing frames which will mess us up if
                        // this is an IMUSample used as a plain ApsDvsEventRGBW
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
                        
                        ApsDvsEventRGBW.ColorFilter ColorFilter = ApsDvsEventRGBW.ColorFilter.Null;
                        if (((e.x % 2) == 1) && ((e.y % 2) == 1)) {
                            ColorFilter = ApsDvsEventRGBW.ColorFilter.R;// R
                        } else if (((e.x % 2) == 0) && ((e.y % 2) == 1)) {
                            ColorFilter = ApsDvsEventRGBW.ColorFilter.G;// G
                        } else if (((e.x % 2) == 0) && ((e.y % 2) == 0)) {
                            ColorFilter = ApsDvsEventRGBW.ColorFilter.B;// B
                        } else if (((e.x % 2) == 1) && ((e.y % 2) == 0)) {
                            ColorFilter = ApsDvsEventRGBW.ColorFilter.W;// w
                        }
                        e.setColorFilter(ColorFilter);
                        // autoshot triggering
                        autoshotEventsSinceLastShot++; // number DVS events captured here
                    }
                } else if ((data & DavisChip.ADDRESS_TYPE_MASK) == DavisChip.ADDRESS_TYPE_APS) {
                    // APS event
                    // We first calculate the positions, so we can put events such as StartOfFrame at their
                    // right place, before the actual APS event denoting (0, 0) for example.
                    final int timestamp = timestamps[i];

                    short x = (short) (((data & DavisChip.XMASK) >>> DavisChip.XSHIFT));
                    short y = (short) ((data & DavisChip.YMASK) >>> DavisChip.YSHIFT);

                    ApsDvsEventRGBW.ColorFilter ColorFilter = ApsDvsEventRGBW.ColorFilter.Null;
                    if (((x % 2) == 1) && ((y % 2) == 1)) {
                        ColorFilter = ApsDvsEventRGBW.ColorFilter.R;// R
                    } else if (((x % 2) == 0) && ((y % 2) == 1)) {
                        ColorFilter = ApsDvsEventRGBW.ColorFilter.G;// G
                    } else if (((x % 2) == 0) && ((y % 2) == 0)) {
                        ColorFilter = ApsDvsEventRGBW.ColorFilter.B;// B
                    } else if (((x % 2) == 1) && ((y % 2) == 0)) {
                        ColorFilter = ApsDvsEventRGBW.ColorFilter.W;// w
                    }

                    final boolean pixFirst = firstFrameAddress(x, y); // First event of frame (addresses get flipped)
                    final boolean pixLast = lastFrameAddress(x, y); // Last event of frame (addresses get flipped)

                    ApsDvsEventRGBW.ReadoutType readoutType = ApsDvsEventRGBW.ReadoutType.Null;
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

                    if (pixFirst && (readoutType == ApsDvsEventRGBW.ReadoutType.ResetRead)) {
                        createApsFlagEvent(outItr, ApsDvsEventRGBW.ReadoutType.SOF, timestamp);

                        if (!getDavisConfig().getApsReadoutControl().isGlobalShutterMode()) {
                            // rolling shutter start of exposureControlRegister (SOE)
                            createApsFlagEvent(outItr, ApsDvsEventRGBW.ReadoutType.SOE, timestamp);
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

                    final ApsDvsEventRGBW e = nextApsDvsEvent(outItr);
                    e.adcSample = data & DavisChip.ADC_DATA_MASK;
                    e.readoutType = readoutType;
                    e.special = false;
                    e.timestamp = timestamp;
                    e.address = data;
                    e.x = x;
                    e.y = y;
                    e.type = (byte) (2);
                    e.setColorFilter(ColorFilter);

                    // TODO: figure out exposure for both GS and RS, and start of frame for GS.
                    if (pixLast && !getDavisConfig().getApsReadoutControl().isGlobalShutterMode() && (readoutType == ApsDvsEventRGBW.ReadoutType.CpResetRead)) {
			// if we use ResetRead+SignalRead+C readout, OR, if we use ResetRead-SignalRead readout and we
                        // are at last APS pixel, then write EOF event
                        // insert a new "end of frame" event not present in original data
                        createApsFlagEvent(outItr, ApsDvsEventRGBW.ReadoutType.EOF, timestamp);

                        if (snapshot) {
                            snapshot = false;
                            getDavisConfig().getApsReadoutControl().setAdcEnabled(false);
                        }

                        setFrameCount(getFrameCount() + 1);
                    }
                    
                    if (pixLast && getDavisConfig().getApsReadoutControl().isGlobalShutterMode() && (readoutType == ApsDvsEventRGBW.ReadoutType.CpResetRead)) {
			// if we use ResetRead+SignalRead+C readout, OR, if we use ResetRead-SignalRead readout and we
                        // are at last APS pixel, then write EOF event
                        // insert a new "end of frame" event not present in original data
                        createApsFlagEvent(outItr, ApsDvsEventRGBW.ReadoutType.EOF, timestamp);

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

        @Override
        protected ApsDvsEventRGBW nextApsDvsEvent(final OutputEventIterator outItr) {
            ApsDvsEvent e = super.nextApsDvsEvent(outItr);

            if (e instanceof ApsDvsEventRGBW) {
                ((ApsDvsEventRGBW) e).setColorFilter(null);
            }

            return (ApsDvsEventRGBW) e;
        }

        /**
         * To handle filtered ApsDvsEventRGBWs, this method rewrites the fields
         * of the raw address encoding x and y addresses to reflect the event's
         * x and y fields.
         *
         * @param e the ApsDvsEventRGBW
         * @return the raw address
         */
        @Override
        public int reconstructRawAddressFromEvent(final TypedEvent e) {
            int address = e.address;
            if (((ApsDvsEventRGBW) e).adcSample >= 0) {
                address = (address & ~DavisChip.XMASK) | (((e.x) / 2) << DavisChip.XSHIFT);
            } else {
                address = (address & ~DavisChip.XMASK) | ((getSizeX() - 1 - (e.x / 2)) << DavisChip.XSHIFT);
            }
                address = (address & ~DavisChip.YMASK) | ((e.y / 2) << DavisChip.YSHIFT);
            return address;
        }

        public boolean firstFrameAddress(short x, short y) {
            return (x == 0) && (y == 0);
        }

        public boolean lastFrameAddress(short x, short y) {
            return (x == (getSizeX() - 1)) && (y == (getSizeY() - 1));
        }
    } // extractor
}
