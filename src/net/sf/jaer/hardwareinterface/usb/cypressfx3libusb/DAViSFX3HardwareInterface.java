/*
 * CypressFX3Biasgen.java
 *
 * Created on 23 Jan 2008
 */
package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Arrays;

import org.usb4java.Device;

import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.DavisConfig;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Adds functionality of apsDVS sensors to based CypressFX3Biasgen class. The
 * key method is translateEvents that parses the data from the sensor to
 * construct jAER raw events.
 *
 * @author Christian/Tobi
 */
public class DAViSFX3HardwareInterface extends CypressFX3Biasgen {

    private int warningCount = 0;
    private static final int WARNING_INTERVAL = 100000;

    protected DAViSFX3HardwareInterface(final Device device) {
        super(device);
    }

    @Override
    synchronized public void sendConfiguration(final net.sf.jaer.biasgen.Biasgen biasgen) throws HardwareInterfaceException {
        if ((biasgen != null) && (biasgen instanceof DavisConfig)) {
            ((DavisConfig) biasgen).sendConfiguration();
        }
    }

    /**
     * The USB product ID of this device
     */
    static public final short PID_FX3 = (short) 0x841A;
    static public final short PID_FX2 = (short) 0x841B;
    static public final int REQUIRED_FIRMWARE_VERSION_FX3 = 6;
    static public final int REQUIRED_FIRMWARE_VERSION_FX2 = 4;
    static public final int REQUIRED_LOGIC_REVISION_FX3 = 18;
    static public final int REQUIRED_LOGIC_REVISION_FX2 = 18;

    private boolean updatedRealClockValues = false;
    public float logicClockFreq = 90.0f;
    public float adcClockFreq = 30.0f;
    public float usbClockFreq = 30.0f;

    /**
     * Starts reader buffer pool thread and enables in endpoints for AEs. This
     * method is overridden to construct our own reader with its translateEvents
     * method
     */
    @Override
    public void startAEReader() throws HardwareInterfaceException {
        log.info("starting AE reader thread");
        setAeReader(new RetinaAEReader(this));
        allocateAEBuffers();

        getAeReader().startThread(); // arg is number of errors before giving up
        HardwareInterfaceException.clearException();
    }

    private void getRealClockValues() {
        try {
            final int logicFreq = spiConfigReceive(CypressFX3.FPGA_SYSINFO, (short) 3);
            final int adcFreq = spiConfigReceive(CypressFX3.FPGA_SYSINFO, (short) 4);
            final int usbFreq = spiConfigReceive(CypressFX3.FPGA_SYSINFO, (short) 5);
            final int clockDeviation = spiConfigReceive(CypressFX3.FPGA_SYSINFO, (short) 6);

            logicClockFreq = (float) (logicFreq * (clockDeviation / 1000.0));
            adcClockFreq = (float) (adcFreq * (clockDeviation / 1000.0));
            usbClockFreq = (float) (usbFreq * (clockDeviation / 1000.0));
        } catch (final HardwareInterfaceException e) {
            // No clock update on failure.
        }

        CypressFX3.log
                .info(String.format("Device clock frequencies - Logic: %f, ADC: %f, USB: %f.", logicClockFreq, adcClockFreq, usbClockFreq));
    }

    @Override
    protected int adjustHWParam(final short moduleAddr, final short paramAddr, final int param) {
        if (!updatedRealClockValues) {
            getRealClockValues();
            updatedRealClockValues = true;
        }

        if ((moduleAddr == CypressFX3.FPGA_APS) && (paramAddr == 12)) {
            // Exposure multiplied by clock.
            return (int) (param * adcClockFreq);
        }

        if ((moduleAddr == CypressFX3.FPGA_APS) && (paramAddr == 13)) {
            // FrameInterval multiplied by clock.
            return (int) (param * adcClockFreq);
        }

        if ((moduleAddr == CypressFX3.FPGA_USB) && (paramAddr == 1)) {
            // Early packet delay is 125µs slices on host, but in cycles
            // @ USB_CLOCK_FREQ on FPGA, so we must multiply here.
            return (int) (param * (125.0f * usbClockFreq));
        }

        // No change by default.
        return (param);
    }

    public static final int CHIP_DAVIS240A = 0;
    public static final int CHIP_DAVIS240B = 1;
    public static final int CHIP_DAVIS240C = 2;
    public static final int CHIP_DAVIS128 = 3;
    public static final int CHIP_DAVIS346A = 4;
    public static final int CHIP_DAVIS346B = 5;
    public static final int CHIP_DAVIS640 = 6;
    public static final int CHIP_DAVISRGB = 7;
    public static final int CHIP_DAVIS208 = 8;
    public static final int CHIP_DAVIS346C = 9;

    /**
     * This reader understands the format of raw USB data and translates to the
     * AEPacketRaw
     */
    public class RetinaAEReader extends CypressFX3.AEReader implements PropertyChangeListener {

        private final int chipID;

        private int wrapAdd;
        private int lastTimestamp;
        private int currentTimestamp;

        private int dvsLastY;
        private final boolean dvsInvertXY;
        private final int dvsSizeX;
        private final int dvsSizeY;

        private static final int APS_READOUT_TYPES_NUM = 2;
        private static final int APS_READOUT_RESET = 0;
        private static final int APS_READOUT_SIGNAL = 1;
        private int apsCurrentReadoutType;
        private int apsRGBPixelOffset;
        private boolean apsRGBPixelOffsetDirection;
        private final short[] apsCountX;
        private final short[] apsCountY;
        private final boolean apsInvertXY;
        private final boolean apsFlipX;
        private final boolean apsFlipY;
        private final int apsSizeX;
        private final int apsSizeY;

        private static final int IMU_DATA_LENGTH = 7;
        private static final int IMU_TYPE_TEMP = 0x01;
        private static final int IMU_TYPE_GYRO = 0x02;
        private static final int IMU_TYPE_ACCEL = 0x04;
        private final short[] imuEvents;
        private final boolean imuFlipX;
        private final boolean imuFlipY;
        private final boolean imuFlipZ;
        private int imuType;
        private int imuCount;
        private byte imuTmpData;

        public RetinaAEReader(final CypressFX3 cypress) throws HardwareInterfaceException {
            super(cypress);

            if (getPID() == DAViSFX3HardwareInterface.PID_FX2) {
                // FX2 firmware now emulates the same interface as FX3 firmware, so we support it here too.
                checkFirmwareLogic(DAViSFX3HardwareInterface.REQUIRED_FIRMWARE_VERSION_FX2,
                        DAViSFX3HardwareInterface.REQUIRED_LOGIC_REVISION_FX2);
            } else {
                checkFirmwareLogic(DAViSFX3HardwareInterface.REQUIRED_FIRMWARE_VERSION_FX3,
                        DAViSFX3HardwareInterface.REQUIRED_LOGIC_REVISION_FX3);
            }

            apsCountX = new short[RetinaAEReader.APS_READOUT_TYPES_NUM];
            apsCountY = new short[RetinaAEReader.APS_READOUT_TYPES_NUM];

            initFrame();

            imuEvents = new short[RetinaAEReader.IMU_DATA_LENGTH];

            chipID = spiConfigReceive(CypressFX3.FPGA_SYSINFO, (short) 1);

            apsSizeX = spiConfigReceive(CypressFX3.FPGA_APS, (short) 0);
            apsSizeY = spiConfigReceive(CypressFX3.FPGA_APS, (short) 1);

            final int chipAPSStreamStart = spiConfigReceive(CypressFX3.FPGA_APS, (short) 2);
            apsInvertXY = (chipAPSStreamStart & 0x04) != 0;
            apsFlipX = (chipAPSStreamStart & 0x02) != 0;
            apsFlipY = (chipAPSStreamStart & 0x01) != 0;

            dvsSizeX = spiConfigReceive(CypressFX3.FPGA_DVS, (short) 0);
            dvsSizeY = spiConfigReceive(CypressFX3.FPGA_DVS, (short) 1);

            dvsInvertXY = (spiConfigReceive(CypressFX3.FPGA_DVS, (short) 2) & 0x04) != 0;

            final int imuOrientation = spiConfigReceive(CypressFX3.FPGA_IMU, (short) 1);
            imuFlipX = (imuOrientation & 0x04) != 0;
            imuFlipY = (imuOrientation & 0x02) != 0;
            imuFlipZ = (imuOrientation & 0x01) != 0;

            updateTimestampMasterStatus();
        }

        private void checkMonotonicTimestamp() {
            if (currentTimestamp <= lastTimestamp && warningCount % WARNING_INTERVAL == 0) {
                CypressFX3.log.severe(toString() + ": non strictly-monotonic timestamp detected: lastTimestamp=" + lastTimestamp
                        + ", currentTimestamp=" + currentTimestamp + ", difference=" + (lastTimestamp - currentTimestamp) + ".");
            }
            warningCount++;
        }

        private void initFrame() {
            apsCurrentReadoutType = RetinaAEReader.APS_READOUT_RESET;
            Arrays.fill(apsCountX, 0, RetinaAEReader.APS_READOUT_TYPES_NUM, (short) 0);
            Arrays.fill(apsCountY, 0, RetinaAEReader.APS_READOUT_TYPES_NUM, (short) 0);
        }

        private boolean ensureCapacity(final AEPacketRaw buffer, final int capacity) {
            if (buffer.getCapacity() > getAEBufferSize()) {
                if (buffer.overrunOccuredFlag || (capacity > buffer.getCapacity())) {
                    buffer.overrunOccuredFlag = true;
                    return (false);
                }

                return (true);
            }

            buffer.ensureCapacity(capacity);
            return (true);
        }

        @Override
        protected void translateEvents(final ByteBuffer b) {
            synchronized (aePacketRawPool) {
                final AEPacketRaw buffer = aePacketRawPool.writeBuffer();

                // Truncate off any extra partial event.
                if ((b.limit() & 0x01) != 0) {
                    CypressFX3.log.severe(b.limit() + " bytes received via USB, which is not a multiple of two.");
                    b.limit(b.limit() & ~0x01);
                }

                buffer.lastCaptureIndex = eventCounter;

                final ShortBuffer sBuf = b.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();

                for (int i = 0; i < sBuf.limit(); i++) {
                    final short event = sBuf.get(i);

                    // Check if timestamp
                    if ((event & 0x8000) != 0) {
                        // Is a timestamp! Expand to 32 bits. (Tick is 1us already.)
                        lastTimestamp = currentTimestamp;
                        currentTimestamp = wrapAdd + (event & 0x7FFF);

                        // Check monotonicity of timestamps.
                        checkMonotonicTimestamp();
                    } else {
                        // Look at the code, to determine event and data
                        // type
                        final byte code = (byte) ((event & 0x7000) >>> 12);
                        final short data = (short) (event & 0x0FFF);

                        switch (code) {
                            case 0: // Special event
                                switch (data) {
                                    case 0: // Ignore this, but log it.
                                        CypressFX3.log.severe("Caught special reserved event!");
                                        break;

                                    case 1: // Timetamp reset
                                        wrapAdd = 0;
                                        lastTimestamp = 0;
                                        currentTimestamp = 0;

                                        updateTimestampMasterStatus();

                                        CypressFX3.log.info("Timestamp reset event received on " + super.toString()
                                                + " at System.currentTimeMillis()=" + System.currentTimeMillis());
                                        break;

                                    case 2: // External input (falling edge)
                                    case 3: // External input (rising edge)
                                    case 4: // External input (pulse)
                                        CypressFX3.log.finer("External input event received.");

                                        // Check that the buffer has space for this event. Enlarge if needed.
                                        if (ensureCapacity(buffer, eventCounter + 1)) {
                                            // tobi added data to pass thru rising falling and pulse events
                                            buffer.getAddresses()[eventCounter] = DavisChip.EXTERNAL_INPUT_EVENT_ADDR + data;
                                            buffer.getTimestamps()[eventCounter++] = currentTimestamp;
                                        }
                                        break;

                                    case 5: // IMU Start (6 axes)
                                        CypressFX3.log.finest("IMU6 Start event received.");

                                        imuCount = 0;
                                        imuType = 0;

                                        break;

                                    case 7: // IMU End
                                        CypressFX3.log.finest("IMU End event received.");

                                        if (imuCount == (2 * RetinaAEReader.IMU_DATA_LENGTH)) {
                                            if (ensureCapacity(buffer, eventCounter + IMUSample.SIZE_EVENTS)) {
                                                // Check for buffer space is also done inside writeToPacket().
                                                final IMUSample imuSample = new IMUSample(currentTimestamp, imuEvents);
                                                eventCounter += imuSample.writeToPacket(buffer, eventCounter);
                                            }
                                        } else {
                                            if (warningCount % WARNING_INTERVAL == 0) {
                                                CypressFX3.log.info(
                                                        "IMU End: failed to validate IMU sample count (" + imuCount + "), discarding samples.");
                                                warningCount++;
                                            }
                                        }
                                        break;

                                    case 8: // APS Global Shutter Frame Start
                                        CypressFX3.log.finest("APS GS Frame Start event received.");

                                        initFrame();

                                        break;

                                    case 9: // APS Rolling Shutter Frame Start
                                        CypressFX3.log.finest("APS RS Frame Start event received.");

                                        initFrame();

                                        break;

                                    case 10: // APS Frame End
                                        CypressFX3.log.finest("APS Frame End event received.");

                                        for (int j = 0; j < RetinaAEReader.APS_READOUT_TYPES_NUM; j++) {
                                            if (apsCountX[j] != apsSizeX && warningCount % WARNING_INTERVAL == 0) {
                                                CypressFX3.log.severe("APS Frame End: wrong column count [" + j + " - " + apsCountX[j]
                                                        + "] detected. You might want to enable 'Ensure APS data transfer' under 'HW Configuration -> Chip Configuration' to improve this.");
                                            }
                                            warningCount++;
                                        }

                                        break;

                                    case 11: // APS Reset Column Start
                                        CypressFX3.log.finest("APS Reset Column Start event received.");

                                        apsCurrentReadoutType = RetinaAEReader.APS_READOUT_RESET;
                                        apsCountY[apsCurrentReadoutType] = 0;

                                        apsRGBPixelOffsetDirection = false;
                                        apsRGBPixelOffset = 1; // RGB support, first pixel of row always even.

                                        break;

                                    case 12: // APS Signal Column Start
                                        CypressFX3.log.finest("APS Signal Column Start event received.");

                                        apsCurrentReadoutType = RetinaAEReader.APS_READOUT_SIGNAL;
                                        apsCountY[apsCurrentReadoutType] = 0;

                                        apsRGBPixelOffsetDirection = false;
                                        apsRGBPixelOffset = 1; // RGB support, first pixel of row always even.

                                        break;

                                    case 13: // APS Column End
                                        CypressFX3.log.finest("APS Column End event received.");

                                        if (apsCountY[apsCurrentReadoutType] != apsSizeY && warningCount % WARNING_INTERVAL == 0) {
                                            CypressFX3.log.severe("APS Column End: wrong row count [" + apsCurrentReadoutType + " - "
                                                    + apsCountY[apsCurrentReadoutType]
                                                    + "] detected. You might want to enable 'Ensure APS data transfer' under 'HW Configuration -> Chip Configuration' to improve this.");
                                            warningCount++;
                                        }

                                        apsCountX[apsCurrentReadoutType]++;

                                        break;

                                    case 14: // APS Exposure Start
                                        // Ignore, exposure is calculated from frame timings.
                                        break;

                                    case 15: // APS Exposure End
                                        // Ignore, exposure is calculated from frame timings.
                                        break;

                                    case 16: // External generator (falling edge)
                                        // Ignore, not supported.
                                        break;

                                    case 17: // External generator (rising edge)
                                        // Ignore, not supported.
                                        break;

                                    default:
                                        CypressFX3.log.severe("Caught special event that can't be handled.");
                                        break;
                                }
                                break;

                            case 1: // Y address
                                // Check range conformity.
                                if (data >= dvsSizeY) {
                                    CypressFX3.log.severe("DVS: Y address out of range (0-" + (dvsSizeY - 1) + "): " + data + ".");
                                    break; // Skip invalid Y address (don't update lastY).
                                }

                                dvsLastY = data;

                                break;

                            case 2: // X address, Polarity OFF
                            case 3: // X address, Polarity ON
                                // Check range conformity.
                                if (data >= dvsSizeX && warningCount % WARNING_INTERVAL == 0) {
                                    CypressFX3.log.severe("DVS: X address out of range (0-" + (dvsSizeX - 1) + "): " + data + ".");
                                    warningCount++;
                                    break; // Skip invalid event.
                                }

                                // Check that the buffer has space for this event. Enlarge if needed.
                                if (ensureCapacity(buffer, eventCounter + 1)) {
                                    // The X address comes out of the new logic such that the (0, 0) address
                                    // is, as expected by most, in the lower left corner. Since the DAVIS240
                                    // chip class data format assumes that this is still flipped, as in the
                                    // old logic, we have to flip it here, so that the chip class extractor
                                    // can flip it back. Backwards compatibility with recordings is the main
                                    // motivation to do this hack.
                                    // NOTE 09.2017: logic now uses upper left (CG format) as output.

                                    // Invert polarity for PixelParade high gain pixels (DavisSense), because of
                                    // negative gain from pre-amplifier.
                                    // tobi commented out because it seems that array is now flipped horizontally (oct 2018)
//                                                                       final byte polarity =code;
//									final byte polarity = ((chipID == DAViSFX3HardwareInterface.CHIP_DAVIS208) && (data < 192))
//										? ((byte) (~code))
//										: (code);
                                    final byte polarity = ((chipID == DAViSFX3HardwareInterface.CHIP_DAVIS208) && (data <= 16))
                                            ? ((byte) (~code))
                                            : (code);
                                    if (dvsInvertXY) {
                                        buffer
                                                .getAddresses()[eventCounter] = (((dvsSizeX - 1 - data) << DavisChip.YSHIFT) & DavisChip.YMASK)
                                                | (((dvsSizeY - 1 - dvsLastY) << DavisChip.XSHIFT) & DavisChip.XMASK)
                                                | (((polarity & 0x01) << DavisChip.POLSHIFT) & DavisChip.POLMASK);
                                    } else {
                                        buffer.getAddresses()[eventCounter] = (((dvsSizeY - 1 - dvsLastY) << DavisChip.YSHIFT)
                                                & DavisChip.YMASK) | (((dvsSizeX - 1 - data) << DavisChip.XSHIFT) & DavisChip.XMASK)
                                                | (((polarity & 0x01) << DavisChip.POLSHIFT) & DavisChip.POLMASK);
                                    }

                                    buffer.getTimestamps()[eventCounter++] = currentTimestamp;
                                }

                                break;

                            case 4: // APS ADC sample
                                // Let's check that apsCountY is not above the maximum. This could happen
                                // if start/end of column events are discarded (no wait on transfer stall).
                                if (((apsCountY[apsCurrentReadoutType] >= apsSizeY) || (apsCountX[apsCurrentReadoutType] >= apsSizeX)) && warningCount % WARNING_INTERVAL == 0) {
                                    CypressFX3.log.fine("APS ADC sample: row or column count is at maximum, discarding further samples.");
                                    warningCount++;
                                    break;
                                }

                                // The DAVIS240c chip is flipped along the X axis. This means it's first reading
                                // out the leftmost columns, and not the rightmost ones as in all the other chips.
                                // So, if a 240c is detected, we don't do the artificial sign flip here.
                                int xPos;
                                int yPos;

                                if (apsFlipX) {
                                    xPos = apsSizeX - 1 - apsCountX[apsCurrentReadoutType];
                                } else {
                                    xPos = apsCountX[apsCurrentReadoutType];
                                }

                                if (apsFlipY) {
                                    yPos = apsSizeY - 1 - apsCountY[apsCurrentReadoutType];
                                } else {
                                    yPos = apsCountY[apsCurrentReadoutType];
                                }

                                if (chipID == DAViSFX3HardwareInterface.CHIP_DAVISRGB) {
                                    yPos += apsRGBPixelOffset;
                                }

                                if (apsInvertXY) {
                                    final int temp = xPos;
                                    xPos = yPos;
                                    yPos = temp;
                                }

                                // NOTE 09.2017: logic now uses upper left (CG format) as output.
                                yPos = (apsInvertXY) ? (apsSizeX - 1 - yPos) : (apsSizeY - 1 - yPos);

                                apsCountY[apsCurrentReadoutType]++;

                                // RGB support: first 320 pixels are even, then odd.
                                if (!apsRGBPixelOffsetDirection) { // Increasing
                                    apsRGBPixelOffset++;

                                    if (apsRGBPixelOffset == 321) {
                                        // Switch to decreasing after last even pixel.
                                        apsRGBPixelOffsetDirection = true;
                                        apsRGBPixelOffset = 318;
                                    }
                                } else { // Decreasing
                                    apsRGBPixelOffset -= 3;
                                }

                                // Check that the buffer has space for this event. Enlarge if needed.
                                if (ensureCapacity(buffer, eventCounter + 1)) {
                                    buffer.getAddresses()[eventCounter] = DavisChip.ADDRESS_TYPE_APS
                                            | ((yPos << DavisChip.YSHIFT) & DavisChip.YMASK) | ((xPos << DavisChip.XSHIFT) & DavisChip.XMASK)
                                            | ((apsCurrentReadoutType << DavisChip.ADC_READCYCLE_SHIFT) & DavisChip.ADC_READCYCLE_MASK)
                                            | (data & DavisChip.ADC_DATA_MASK);
                                    buffer.getTimestamps()[eventCounter++] = currentTimestamp;
                                }
                                break;

                            case 5: // Misc 8bit data.
                                final byte misc8Code = (byte) ((data & 0x0F00) >>> 8);
                                final byte misc8Data = (byte) (data & 0x00FF);

                                switch (misc8Code) {
                                    case 0:
                                        // IMU data event.
                                        switch (imuCount) {
                                            case 0:
                                            case 2:
                                            case 4:
                                            case 6:
                                            case 8:
                                            case 10:
                                            case 12:
                                                imuTmpData = misc8Data;
                                                break;

                                            case 1: // Accel X
                                                imuEvents[0] = (short) (((imuTmpData & 0x00FF) << 8) | (misc8Data & 0x00FF));
                                                if (imuFlipX) {
                                                    imuEvents[0] = (short) -imuEvents[0];
                                                }
                                                break;

                                            case 3: // Accel Y
                                                imuEvents[1] = (short) (((imuTmpData & 0x00FF) << 8) | (misc8Data & 0x00FF));
                                                if (imuFlipY) {
                                                    imuEvents[1] = (short) -imuEvents[1];
                                                }
                                                break;

                                            case 5: // Accel Z
                                                imuEvents[2] = (short) (((imuTmpData & 0x00FF) << 8) | (misc8Data & 0x00FF));
                                                if (imuFlipZ) {
                                                    imuEvents[2] = (short) -imuEvents[2];
                                                }

                                                // IMU parser count depends on which data is present.
                                                if ((imuType & RetinaAEReader.IMU_TYPE_TEMP) == 0) {
                                                    if ((imuType & RetinaAEReader.IMU_TYPE_GYRO) != 0) {
                                                        // No temperature, but gyro.
                                                        imuCount += 2;
                                                    } else {
                                                        // No others enabled.
                                                        imuCount += 8;
                                                    }
                                                }
                                                break;

                                            case 7: // Temperature
                                                imuEvents[3] = (short) (((imuTmpData & 0x00FF) << 8) | (misc8Data & 0x00FF));

                                                // IMU parser count depends on which data is present.
                                                if ((imuType & RetinaAEReader.IMU_TYPE_GYRO) == 0) {
                                                    // No others enabled.
                                                    imuCount += 6;
                                                }
                                                break;

                                            case 9: // Gyro X
                                                imuEvents[4] = (short) (((imuTmpData & 0x00FF) << 8) | (misc8Data & 0x00FF));
                                                if (imuFlipX) {
                                                    imuEvents[4] = (short) -imuEvents[4];
                                                }
                                                break;

                                            case 11: // Gyro Y
                                                imuEvents[5] = (short) (((imuTmpData & 0x00FF) << 8) | (misc8Data & 0x00FF));
                                                if (imuFlipY) {
                                                    imuEvents[5] = (short) -imuEvents[5];
                                                }
                                                break;

                                            case 13: // Gyro Z
                                                imuEvents[6] = (short) (((imuTmpData & 0x00FF) << 8) | (misc8Data & 0x00FF));
                                                if (imuFlipZ) {
                                                    imuEvents[6] = (short) -imuEvents[6];
                                                }
                                                break;
                                        }

                                        imuCount++;

                                        break;

                                    case 1: // APS ROI Size Part 1 (bits 15-8).
                                    case 2: // APS ROI Size Part 2 (bits 7-0).
                                        // Ignore ROI events, not supported.
                                        break;

                                    case 3:
                                        // Scale for accel/gyro come from its configuration directly.
                                        // Set expected type of data to come from IMU (accel, gyro, temp).
                                        imuType = (data >> 5) & 0x07;

                                        // IMU parser start count depends on which data is present.
                                        if ((imuType & RetinaAEReader.IMU_TYPE_ACCEL) != 0) {
                                            // Accelerometer.
                                            imuCount = 0;
                                        } else if ((imuType & RetinaAEReader.IMU_TYPE_TEMP) != 0) {
                                            // Temperature
                                            imuCount = 6;
                                        } else if ((imuType & RetinaAEReader.IMU_TYPE_GYRO) != 0) {
                                            // Gyroscope.
                                            imuCount = 8;
                                        } else {
                                            // Nothing, should never happen.
                                            imuCount = 14;
                                        }

                                        break;

                                    default:
                                        CypressFX3.log.severe("Caught Misc8 event that can't be handled.");
                                        break;
                                }

                                break;

                            case 6:  // Misc 11bit data.
                                final byte misc11Code = (byte) ((data & 0x0800) >> 11);

                                switch (misc11Code) {
                                    case 0:
                                        // APS Exposure Information, ignore for now.
                                        break;
                                    case 1:
                                        // Used by davis346Zynq to send hardware ip calculation result
                                        // Every hw_ip result is appened to the x address.
                                        // Current eventCounter is already added by 1 while extracting x address,
                                        // thus we need to substract 1 here.
                                        if (eventCounter >= 1 && ensureCapacity(buffer, eventCounter)) {
                                            buffer.getAddresses()[eventCounter - 1] |= data & 0x7ff;
                                        }
                                        break;

                                    default:
                                        CypressFX3.log.severe("Caught Misc10 event that can't be handled.");
                                        break;
                                }

                                break;

                            case 7: // Timestamp wrap
                                // Each wrap is 2^15 us (~32ms), and we have
                                // to multiply it with the wrap counter,
                                // which is located in the data part of this
                                // event.
                                wrapAdd += (0x8000L * data);

                                lastTimestamp = currentTimestamp;
                                currentTimestamp = wrapAdd;

                                // Check monotonicity of timestamps.
                                checkMonotonicTimestamp();

                                CypressFX3.log.finer(
                                        String.format("Timestamp wrap event received on %s with multiplier of %d.", super.toString(), data));
                                break;

                            default:
                                CypressFX3.log.severe("Caught event that can't be handled.");
                                break;
                        }
                    }
                } // end loop over usb data buffer

                buffer.setNumEvents(eventCounter);
                // write capture size
                buffer.lastCaptureLength = eventCounter - buffer.lastCaptureIndex;
            } // sync on aePacketRawPool
        }

        @Override
        public void propertyChange(final PropertyChangeEvent arg0) {
            // Do nothing here, IMU comes directly via event-stream.
        }
    }
}
