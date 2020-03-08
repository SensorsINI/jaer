package eu.seebetter.ini.chips.davis;

import java.awt.Point;

import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.graphics.DavisRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterface;

/** Chip class for Min Liu's Zynq FPGA camera. 
 * It sends data including extract keypoint and optical flow events over USB.
 * It currently does not have APS intensity frame output.
 * 
 * @author Tobi
 */
@Description("Min Liu's Zynq FPGA DAVIS346 346x260 pixel APS-DVS DAVIS USB 3.0")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class Davis346Zynq extends Davis346BaseCamera {
	public Davis346Zynq() {
		setName("Davis346Zynq");
		setDefaultPreferencesFile("biasgenSettings/Davis346b/Davis346Zynq.xml");

		davisRenderer = new DavisRenderer(this);
//		davisRenderer.setMaxADC(DavisChip.MAX_ADC);
		setRenderer(davisRenderer);

//		// Inverted with respect to other 346 cameras.
//		setApsFirstPixelReadOut(new Point(getSizeX() - 1, 0));
//		setApsLastPixelReadOut(new Point(0, getSizeY() - 1));
	}

	public Davis346Zynq(final HardwareInterface hardwareInterface) {
		this();
		setHardwareInterface(hardwareInterface);
	}
        
    public class DavisColorEventExtractor extends DavisBaseCamera.DavisEventExtractor {

        public DavisColorEventExtractor(final DavisBaseCamera chip) {
            super(chip);

        }

        /**
         * Extracts to event objects from the raw int32 timestamp/address events including KeyPointEvents and OpticalFlowEvents.
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

            final int[] datas = in.getAddresses();
            final int[] timestamps = in.getTimestamps();
            final OutputEventIterator outItr = out.outputIterator();
            final int sx1 = (getChip().getSizeX()) - 1;
            // NOTE we must make sure we write ApsDvsEvents when we want them, not reuse the IMUSamples

            // at this point the raw data from the USB IN packet has already been digested to extract timestamps,
            // including timestamp wrap events and timestamp resets.
            // The datas array holds the data, which consists of a mixture of AEs and ADC values.
            // Here we extract the datas and leave the timestamps alone.
            for (int i = 0; i < n; i++) { 
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
                    e.setImuSample(null);

                    if ((data & DavisChip.EVENT_TYPE_MASK) == DavisChip.EXTERNAL_INPUT_EVENT_ADDR) {
                        e.setReadoutType(ApsDvsEvent.ReadoutType.DVS);
                        e.setSpecial(true);

                        e.address = data;
                        e.timestamp = (timestamps[i]);
                    } else {
                        e.setReadoutType(ApsDvsEvent.ReadoutType.DVS);

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
                    // ignore, no APS output for now
 
                } else if ((data & DavisChip.ADDRESS_TYPE_MASK) == DavisChip.ADDRESS_TYPE_APS) {
                    // TODO handle the KayPoint and OpticalFlow events here
 
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
                    final int sx1 = getChip().getSizeX() - 1;
                    address = (address & ~DavisChip.XMASK) | ((sx1 - e.x) << DavisChip.XSHIFT);
                    address = (address & ~DavisChip.YMASK) | (e.y << DavisChip.YSHIFT);
            } else {
                address = (address & ~DavisChip.XMASK) | (e.x << DavisChip.XSHIFT);
                address = (address & ~DavisChip.YMASK) | (e.y << DavisChip.YSHIFT);
            }

            return address;
        }
    } // extractor
    

}
