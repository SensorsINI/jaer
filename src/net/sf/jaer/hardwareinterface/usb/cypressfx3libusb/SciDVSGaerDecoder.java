package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.logging.Logger;

/** Hardware-free state machine for the non-FX10 SciDVS GAER wire format. */
final class SciDVSGaerDecoder {

    static final class Config {
        final int chipID;
        final int dvsSizeX;
        final int dvsSizeY;
        final boolean dvsInvertXY;
        final int apsSizeX;
        final int apsSizeY;
        final boolean apsInvertXY;
        final boolean apsFlipX;
        final boolean apsFlipY;
        final boolean imuFlipX;
        final boolean imuFlipY;
        final boolean imuFlipZ;

        Config(final int chipID, final int dvsSizeX, final int dvsSizeY,
                final boolean dvsInvertXY, final int apsSizeX, final int apsSizeY,
                final boolean apsInvertXY, final boolean apsFlipX, final boolean apsFlipY,
                final boolean imuFlipX, final boolean imuFlipY, final boolean imuFlipZ) {
            this.chipID = chipID;
            this.dvsSizeX = dvsSizeX;
            this.dvsSizeY = dvsSizeY;
            this.dvsInvertXY = dvsInvertXY;
            this.apsSizeX = apsSizeX;
            this.apsSizeY = apsSizeY;
            this.apsInvertXY = apsInvertXY;
            this.apsFlipX = apsFlipX;
            this.apsFlipY = apsFlipY;
            this.imuFlipX = imuFlipX;
            this.imuFlipY = imuFlipY;
            this.imuFlipZ = imuFlipZ;
        }
    }

    private static final Logger LOG = Logger.getLogger(SciDVSGaerDecoder.class.getName());
    private static final int GAER_EVENT_WIDTH = 4;
    private static final int APS_READOUT_TYPES_NUM = 2;
    private static final int APS_READOUT_RESET = 0;
    private static final int APS_READOUT_SIGNAL = 1;
    private static final int IMU_DATA_LENGTH = 7;
    private static final int IMU_TYPE_TEMP = 0x01;
    private static final int IMU_TYPE_GYRO = 0x02;
    private static final int IMU_TYPE_ACCEL = 0x04;
    private static final int CHIP_DAVISRGB = 7;

    private final Config config;
    private final String logIdentity;
    private final SciDVSGaerLogThrottle throttle;
    private int wrapAdd;
    private int lastTimestamp;
    private int currentTimestamp;
    private int dvsLastY;
    private int apsCurrentReadoutType;
    private int apsRGBPixelOffset;
    private boolean apsRGBPixelOffsetDirection;
    private final short[] apsCountX = new short[APS_READOUT_TYPES_NUM];
    private final short[] apsCountY = new short[APS_READOUT_TYPES_NUM];
    private final short[] imuEvents = new short[IMU_DATA_LENGTH];
    private int imuType;
    private int imuCount;
    private byte imuTmpData;
    private boolean rollingShutterFrame;

    SciDVSGaerDecoder(final Config config) {
        this(config, null);
    }

    SciDVSGaerDecoder(final Config config, final String logIdentity) {
        this(config, logIdentity, SciDVSGaerLogThrottle.ALWAYS);
    }

    SciDVSGaerDecoder(final Config config, final String logIdentity,
            final SciDVSGaerLogThrottle throttle) {
        this.config = config;
        this.logIdentity = logIdentity;
        this.throttle = throttle;
        initFrame();
    }

    void decode(final ByteBuffer input, final SciDVSGaerSink sink) {
        if ((input.limit() & 0x01) != 0) {
            LOG.severe(input.limit() + " bytes received via USB, which is not a multiple of two.");
            input.limit(input.limit() & ~0x01);
        }

        final ShortBuffer words = input.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        for (int i = 0; i < words.limit(); i++) {
            decodeWord(words.get(i), sink);
        }
    }

    private void decodeWord(final short event, final SciDVSGaerSink sink) {
        if ((event & 0x8000) != 0) {
            lastTimestamp = currentTimestamp;
            currentTimestamp = wrapAdd + (event & 0x7FFF);
            checkMonotonicTimestamp();
            return;
        }

        final byte code = (byte) ((event & 0x7000) >>> 12);
        final short data = (short) (event & 0x0FFF);
        switch (code) {
            case 0:
                decodeSpecial(data, sink);
                break;

            case 1:
                if (data >= config.dvsSizeY) {
                    if (throttle.shouldLog()) {
                        LOG.severe("DVS: Y address out of range (0-" + (config.dvsSizeY - 1) + "): " + data + ".");
                    }
                    break;
                }
                dvsLastY = config.dvsSizeY - 1 - data;
                break;

            case 2:
            case 3:
                decodeGaerGroup(event, sink);
                break;

            case 4:
                decodeApsSample(data, sink);
                break;

            case 5:
                decodeMisc8(data);
                break;

            case 6:
                decodeMisc11(data, sink);
                break;

            case 7:
                wrapAdd += (0x8000L * data);
                lastTimestamp = currentTimestamp;
                currentTimestamp = wrapAdd;
                checkMonotonicTimestamp();
                LOG.fine(String.format("Timestamp wrap event received with multiplier of %d.", data));
                break;

            default:
                if (throttle.shouldLog()) {
                    LOG.severe("Caught event that can't be handled. code: " + code);
                }
                break;
        }
    }

    private void decodeSpecial(final short data, final SciDVSGaerSink sink) {
        switch (data) {
            case 0:
                if (throttle.shouldLog()) {
                    LOG.severe("Caught special reserved event!");
                }
                break;

            case 1:
                wrapAdd = 0;
                lastTimestamp = 0;
                currentTimestamp = 0;
                sink.onTimestampReset();
                break;

            case 2:
            case 3:
            case 4:
                LOG.fine("External input event received.");
                sink.onExternalInput(data, DavisChip.EXTERNAL_INPUT_EVENT_ADDR + data, currentTimestamp);
                break;

            case 5:
                LOG.fine("IMU6 Start event received.");
                imuCount = 0;
                imuType = 0;
                break;

            case 7:
                LOG.fine("IMU End event received.");
                if (imuCount == (2 * IMU_DATA_LENGTH)) {
                    sink.onImuSample(new IMUSample(currentTimestamp, imuEvents), currentTimestamp);
                } else if (throttle.shouldLog()) {
                    LOG.info("IMU End: failed to validate IMU sample count (" + imuCount
                            + "), discarding samples.");
                }
                break;

            case 8:
                LOG.fine("APS GS Frame Start event received.");
                rollingShutterFrame = false;
                initFrame();
                sink.onFrameStart(false, currentTimestamp);
                break;

            case 9:
                LOG.fine("APS RS Frame Start event received.");
                rollingShutterFrame = true;
                initFrame();
                sink.onFrameStart(true, currentTimestamp);
                break;

            case 10:
                LOG.fine("APS Frame End event received.");
                for (int i = 0; i < APS_READOUT_TYPES_NUM; i++) {
                    if (apsCountX[i] != config.apsSizeX) {
                        if (throttle.shouldLog()) {
                            LOG.severe("APS Frame End: wrong column count [" + i + " - " + apsCountX[i]
                                    + "/" + config.apsSizeX
                                    + "] (FPGA APS stream). Missing columns are not filled by WaitOnTransferStall.");
                        }
                    }
                }
                sink.onFrameEnd(rollingShutterFrame, currentTimestamp);
                break;

            case 11:
                LOG.fine("APS Reset Column Start event received.");
                apsCurrentReadoutType = APS_READOUT_RESET;
                apsCountY[apsCurrentReadoutType] = 0;
                apsRGBPixelOffsetDirection = false;
                apsRGBPixelOffset = 1;
                break;

            case 12:
                LOG.fine("APS Signal Column Start event received.");
                apsCurrentReadoutType = APS_READOUT_SIGNAL;
                apsCountY[apsCurrentReadoutType] = 0;
                apsRGBPixelOffsetDirection = false;
                apsRGBPixelOffset = 1;
                break;

            case 13:
                LOG.fine("APS Column End event received.");
                if (apsCountY[apsCurrentReadoutType] != config.apsSizeY) {
                    if (throttle.shouldLog()) {
                        LOG.severe("APS Column End: wrong row count [" + apsCurrentReadoutType + " - "
                                + apsCountY[apsCurrentReadoutType] + "/" + config.apsSizeY
                                + "] (0=reset, 1=signal). Empty or short columns: FPGA APS markers without ADC words, or host counters skipped samples.");
                    }
                }
                apsCountX[apsCurrentReadoutType]++;
                break;

            case 14:
                sink.onExposureStart(currentTimestamp);
                break;

            case 15:
                sink.onExposureEnd(currentTimestamp);
                break;

            case 16:
            case 17:
            case 32:
            case 33:
            case 34:
            case 35:
            case 48:
            case 49:
            case 50:
            case 51:
            case 52:
                break;

            default:
                if (throttle.shouldLog()) {
                    LOG.severe("Caught special event that can't be handled: " + data);
                }
                break;
        }
    }

    private void decodeGaerGroup(final short event, final SciDVSGaerSink sink) {
        final byte groupAddr = (byte) ((event & 0x1F00) >>> 8);
        final byte allEvents = (byte) (event & 0x00FF);
        final int dvsAddrOffsetX = groupAddr * GAER_EVENT_WIDTH;
        if (dvsAddrOffsetX >= config.dvsSizeX) {
            if (throttle.shouldLog()) {
                LOG.severe("DVS: X address out of range (0-" + (config.dvsSizeX - 1)
                        + "): groupAddr: " + groupAddr + ", addrY: " + dvsAddrOffsetX + ".");
            }
            return;
        }

        final int effectiveY = config.dvsSizeY - 1 - dvsLastY;
        for (byte iter = 0; iter < (2 * GAER_EVENT_WIDTH); iter++) {
            if ((allEvents & (1 << iter)) == 0) {
                continue;
            }
            final boolean on = iter >= GAER_EVENT_WIDTH;
            final int addrX = dvsAddrOffsetX + (on ? iter - GAER_EVENT_WIDTH : iter);
            final int packedAddress;
            if (config.dvsInvertXY) {
                packedAddress = (((config.dvsSizeX - 1 - addrX) << DavisChip.YSHIFT) & DavisChip.YMASK)
                        | ((effectiveY << DavisChip.XSHIFT) & DavisChip.XMASK)
                        | (((on ? 1 : 0) << DavisChip.POLSHIFT) & DavisChip.POLMASK);
            } else {
                packedAddress = ((effectiveY << DavisChip.YSHIFT) & DavisChip.YMASK)
                        | (((config.dvsSizeX - 1 - addrX) << DavisChip.XSHIFT) & DavisChip.XMASK)
                        | (((on ? 1 : 0) << DavisChip.POLSHIFT) & DavisChip.POLMASK);
            }
            sink.onPolarity(packedAddress, addrX, effectiveY, on, currentTimestamp);
        }
    }

    private void decodeApsSample(final short data, final SciDVSGaerSink sink) {
        if ((apsCountY[apsCurrentReadoutType] >= config.apsSizeY)
                || (apsCountX[apsCurrentReadoutType] >= config.apsSizeX)) {
            LOG.fine("APS ADC sample: row or column count is at maximum, discarding further samples.");
            return;
        }

        int xPos = config.apsFlipX
                ? config.apsSizeX - 1 - apsCountX[apsCurrentReadoutType]
                : apsCountX[apsCurrentReadoutType];
        int yPos = config.apsFlipY
                ? config.apsSizeY - 1 - apsCountY[apsCurrentReadoutType]
                : apsCountY[apsCurrentReadoutType];

        if (config.chipID == CHIP_DAVISRGB) {
            yPos += apsRGBPixelOffset;
        }
        if (config.apsInvertXY) {
            final int temp = xPos;
            xPos = yPos;
            yPos = temp;
        }
        yPos = config.apsInvertXY
                ? config.apsSizeX - 1 - yPos
                : config.apsSizeY - 1 - yPos;

        apsCountY[apsCurrentReadoutType]++;
        if (!apsRGBPixelOffsetDirection) {
            apsRGBPixelOffset++;
            if (apsRGBPixelOffset == 321) {
                apsRGBPixelOffsetDirection = true;
                apsRGBPixelOffset = 318;
            }
        } else {
            apsRGBPixelOffset -= 3;
        }

        final boolean pixelFirst = (apsCountX[apsCurrentReadoutType] == 0)
                && (apsCountY[apsCurrentReadoutType] == 1);
        final boolean pixelLast = (apsCountX[apsCurrentReadoutType] == (config.apsSizeX - 1))
                && (apsCountY[apsCurrentReadoutType] == config.apsSizeY);
        final boolean resetRead = apsCurrentReadoutType == APS_READOUT_RESET;
        final int packedAddress = DavisChip.ADDRESS_TYPE_APS
                | ((yPos << DavisChip.YSHIFT) & DavisChip.YMASK)
                | ((xPos << DavisChip.XSHIFT) & DavisChip.XMASK)
                | ((apsCurrentReadoutType << DavisChip.ADC_READCYCLE_SHIFT)
                        & DavisChip.ADC_READCYCLE_MASK)
                | (data & DavisChip.ADC_DATA_MASK);
        sink.onApsSample(packedAddress, data & DavisChip.ADC_DATA_MASK,
                xPos, yPos, resetRead, pixelFirst, pixelLast, currentTimestamp);
    }

    private void decodeMisc8(final short data) {
        final byte misc8Code = (byte) ((data & 0x0F00) >>> 8);
        final byte misc8Data = (byte) (data & 0x00FF);
        switch (misc8Code) {
            case 0:
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

                    case 1:
                        imuEvents[0] = assembleImuWord(misc8Data);
                        if (config.imuFlipX) {
                            imuEvents[0] = (short) -imuEvents[0];
                        }
                        break;

                    case 3:
                        imuEvents[1] = assembleImuWord(misc8Data);
                        if (config.imuFlipY) {
                            imuEvents[1] = (short) -imuEvents[1];
                        }
                        break;

                    case 5:
                        imuEvents[2] = assembleImuWord(misc8Data);
                        if (config.imuFlipZ) {
                            imuEvents[2] = (short) -imuEvents[2];
                        }
                        if ((imuType & IMU_TYPE_TEMP) == 0) {
                            if ((imuType & IMU_TYPE_GYRO) != 0) {
                                imuCount += 2;
                            } else {
                                imuCount += 8;
                            }
                        }
                        break;

                    case 7:
                        imuEvents[3] = assembleImuWord(misc8Data);
                        if ((imuType & IMU_TYPE_GYRO) == 0) {
                            imuCount += 6;
                        }
                        break;

                    case 9:
                        imuEvents[4] = assembleImuWord(misc8Data);
                        if (config.imuFlipX) {
                            imuEvents[4] = (short) -imuEvents[4];
                        }
                        break;

                    case 11:
                        imuEvents[5] = assembleImuWord(misc8Data);
                        if (config.imuFlipY) {
                            imuEvents[5] = (short) -imuEvents[5];
                        }
                        break;

                    case 13:
                        imuEvents[6] = assembleImuWord(misc8Data);
                        if (config.imuFlipZ) {
                            imuEvents[6] = (short) -imuEvents[6];
                        }
                        break;

                    default:
                        break;
                }
                imuCount++;
                break;

            case 1:
            case 2:
                break;

            case 3:
                imuType = (data >> 5) & 0x07;
                if ((imuType & IMU_TYPE_ACCEL) != 0) {
                    imuCount = 0;
                } else if ((imuType & IMU_TYPE_TEMP) != 0) {
                    imuCount = 6;
                } else if ((imuType & IMU_TYPE_GYRO) != 0) {
                    imuCount = 8;
                } else {
                    imuCount = 14;
                }
                break;

            default:
                if (throttle.shouldLog()) {
                    LOG.severe("Caught Misc8 event that can't be handled.");
                }
                break;
        }
    }

    private short assembleImuWord(final byte lowByte) {
        return (short) (((imuTmpData & 0x00FF) << 8) | (lowByte & 0x00FF));
    }

    private void decodeMisc11(final short data, final SciDVSGaerSink sink) {
        final byte misc11Code = (byte) ((data & 0x0800) >> 11);
        switch (misc11Code) {
            case 0:
                break;
            case 1:
                sink.onAddressPatch(data & 0x07FF);
                break;
            default:
                if (throttle.shouldLog()) {
                    LOG.severe("Caught Misc10 event that can't be handled.");
                }
                break;
        }
    }

    private void checkMonotonicTimestamp() {
        if (currentTimestamp <= lastTimestamp) {
            if (throttle.shouldLog()) {
                LOG.severe((logIdentity == null ? toString() : logIdentity)
                        + ": non strictly-monotonic timestamp detected: lastTimestamp="
                        + lastTimestamp + ", currentTimestamp=" + currentTimestamp
                        + ", difference=" + (lastTimestamp - currentTimestamp) + ".");
            }
        }
    }

    private void initFrame() {
        apsCurrentReadoutType = APS_READOUT_RESET;
        Arrays.fill(apsCountX, 0, APS_READOUT_TYPES_NUM, (short) 0);
        Arrays.fill(apsCountY, 0, APS_READOUT_TYPES_NUM, (short) 0);
    }

    /** Clear APS column counters. USB reader restart must not keep a half-frame. */
    void resetApsState() {
        initFrame();
        rollingShutterFrame = false;
    }
}
