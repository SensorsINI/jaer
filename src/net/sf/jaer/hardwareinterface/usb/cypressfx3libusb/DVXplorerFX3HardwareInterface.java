/*
 * Copyright (C) 2023 Pei Haoxiang.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import org.usb4java.Device;
import ch.unizh.ini.jaer.chip.retina.DVXplorer;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Adds functionality of DVXplorer sensors to based CypressFX3 class. 
 * The key method is translateEvents that parses
 * the data from the sensor to construct jAER raw events.
 * 
 * @author Pei Haoxiang
 */
public class DVXplorerFX3HardwareInterface extends CypressFX3 {
    
    /** The USB product ID of this device */
    static public final short PID_FX3 = (short) 0x8419;
    static public final int REQUIRED_FIRMWARE_VERSION_FX3 = 8;
    static public final int REQUIRED_LOGIC_REVISION_FX3 = 18;

    protected DVXplorerFX3HardwareInterface(final Device device) {
        super(device);
    }
    
    @Override
	synchronized public void open() throws HardwareInterfaceException {
		super.open();
        
        // Configurate DVXplorer chip
        if (getChip() != null) {
            DVXplorer chip = (DVXplorer) getChip();
			chip.dvxConfig();
		}
	}
    
    @Override
    synchronized protected void enableINEndpoint() throws HardwareInterfaceException {
        if (deviceHandle == null) {
			CypressFX3.log.warning("CypressFX3.enableINEndpoint(): null USBIO device");
			return;
		}
        
        if (getChip() != null) {
            DVXplorer chip = (DVXplorer) getChip();
			chip.dvxDataStart();
		}
        
        inEndpointEnabled = true;
    }
    
    @Override
    synchronized protected void disableINEndpoint() {
        if (getChip() != null) {
            DVXplorer chip = (DVXplorer) getChip();
            chip.dvxDataStop();
        }

		inEndpointEnabled = false;
    }

    /**
     * Starts reader buffer pool thread and enables in endpoints for AEs. This
     * method is overridden to construct
     * our own reader with its translateEvents method
     */
    @Override
    public void startAEReader() throws HardwareInterfaceException {
        setAeReader(new RetinaAEReader(this));
        allocateAEBuffers();

        getAeReader().startThread(); // arg is number of errors before giving up
        HardwareInterfaceException.clearException();
    }

    /**
     * This reader understands the format of raw USB data and translates to the
     * AEPacketRaw
     */
    public class RetinaAEReader extends CypressFX3.AEReader implements PropertyChangeListener {
        private final int chipID;

        // aedat2 format
        public static final int AEDAT2_Y_ADDR_MASK = 0x000001FF;
        public static final int AEDAT2_Y_ADDR_SHIFT = 22;
        public static final int AEDAT2_X_ADDR_MASK = 0x000003FF;
        public static final int AEDAT2_X_ADDR_SHIFT = 12;
        public static final int AEDAT2_POLARITY_MASK = 0x00000001;
        public static final int AEDAT2_POLARITY_SHIFT = 11;

        // timestamps_state_new_logic
        private int wrapOverflow;
        private int wrapAdd = 0;
        private int lastTimestamp;
        private int currentTimestamp;

        private int dvsLastX;
        private int dvsLastYG1;
        private int dvsLastYG2;
        private final boolean dvsInvertXY;
        private final int dvsSizeX;
        private final int dvsSizeY;

        // DVXplorer specific
        private final boolean dvsDualBinning = false;
        private final boolean dvsFlipX = true;
        private final boolean dvsFlipY = false;

        private static final int IMU_DATA_LENGTH = 7;
        private static final int IMU_TYPE_TEMP = 0x01;
        private static final int IMU_TYPE_GYRO = 0x02;
        private static final int IMU_TYPE_ACCEL = 0x04;
        private final short[] imuEvents;
        private final boolean imuFlipX;
        private final boolean imuFlipY;
        private final boolean imuFlipZ;
        private float imuAccelScale;
        private float imuGyroScale;
        private int imuType;
        private int imuCount;
        private byte imuTmpData;
        private boolean imuIgnoreEvents = true;
        
        private final float ACCEL_G_PER_LSB = 1f / 8192;
        private final float GYRO_DPS_PER_LSB = 1f / 65.5f;
        private final float TEMP_DEGC_PER_LSB = 1f / 340;
        private final float TEMP_DEGC_OFFSET = 35;

        public RetinaAEReader(final CypressFX3 cypress) throws HardwareInterfaceException {
            super(cypress);

            checkFirmwareLogic(REQUIRED_FIRMWARE_VERSION_FX3, REQUIRED_LOGIC_REVISION_FX3);

            imuEvents = new short[RetinaAEReader.IMU_DATA_LENGTH];

            chipID = spiConfigReceive(CypressFX3.FPGA_SYSINFO, (short) 1);
            
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
            if (currentTimestamp <= lastTimestamp) {
                CypressFX3.log.severe(String.format("%s: non strictly-monotonic timestamp detected: lastTimestamp=%d, currentTimestamp=%d, difference=%d.",
                    toString(), lastTimestamp, currentTimestamp, (lastTimestamp - currentTimestamp)));
            }
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
                    CypressFX3.log.severe(String.format("%d bytes received via USB, which is not a multiple of two.", b.limit()));
                    b.limit(b.limit() & ~0x01);
                }

                buffer.lastCaptureIndex = eventCounter;

                final ShortBuffer sBuf = b.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();                                

                for (int bufPos = 0; bufPos < sBuf.limit(); bufPos++) {
                    final short event = sBuf.get(bufPos);

                    // Check if timestamp
                    if ((event & 0x8000) != 0) {
                        // Is a timestamp! Expand to 32 bits. (Tick is 1us already.)
                        lastTimestamp = currentTimestamp;
                        currentTimestamp = wrapAdd + (event & 0x7FFF);

                        // Check monotonicity of timestamps.
                        // There are some bugs here. Temporarily, I comment it to avoid its impact on performance, but these bugs need to be fixed.
                        // checkMonotonicTimestamp();
                    }
                    else {
                        // Look at the code, to determine event and data type
                        final byte code = (byte) ((event & 0x7000) >>> 12);
                        final short data = (short) (event & 0x0FFF);

                        switch (code) {
                            case 0: // Special event
                                switch (data) {
                                    case 0: // Ignore this, but log it.
                                        CypressFX3.log.severe("Caught special reserved event!");
                                        break;

                                    case 1: // Timetamp reset
                                        wrapOverflow = 0;
                                        wrapAdd = 0;
                                        lastTimestamp = 0;
                                        currentTimestamp = 0;

                                        updateTimestampMasterStatus();

                                        CypressFX3.log.info(String.format("Timestamp reset event received on %s at System.currentTimeMillis() = %d",
                                            super.toString(), System.currentTimeMillis()));
                                        
                                        break;

                                    case 5: // IMU Start (6 axes)
                                        CypressFX3.log.fine("IMU6 Start event received.");

                                        imuIgnoreEvents = false;
                                        imuCount = 0;
                                        imuType = 0;

                                        break;
                                        
                                    case 7: // IMU End
                                        if (imuIgnoreEvents) {
                                            break;
                                        }
                                        CypressFX3.log.fine("IMU End event received.");

                                        if (imuCount == (2 * IMU_DATA_LENGTH)) {
                                            if (ensureCapacity(buffer, eventCounter + IMUSample.SIZE_EVENTS)) {
                                                // Check for buffer space is also done inside writeToPacket().
                                                final IMUSample imuSample = new IMUSample(currentTimestamp, imuEvents);
                                                eventCounter += imuSample.writeToPacket(buffer, eventCounter);
                                            }
                                        }
                                        else {
                                            CypressFX3.log.info(
                                                String.format("IMU End: failed to validate IMU sample count (%d), discarding samples.", imuCount));
                                        }
                                        break;
                                        
                                    default:
                                        CypressFX3.log.severe("Caught special event that can't be handled.");
                                        break;
                                }
                                break;

                            case 1: // X column address. 10 bits (9 - 0) contain address, bit 11 Start of Frame marker.
                                final int columnAddr = data & 0x03FF;

                                if (columnAddr >= dvsSizeX) {
                                    CypressFX3.log.severe(String.format("DVS: X address out of range (0-%d): %d.", (dvsSizeX - 1), columnAddr));
                                    break; // Skip invalid X address (don't update lastX).
                                }

                                dvsLastX = columnAddr;
                                break;

                            case 2:
                            case 3: 
                                // 8-pixel group event presence and polarity.
                                // Code 2 is MGROUP Group 2 (SGROUP OFF), Code 3 is MGROUP Group 1 (SGROUP ON).
                                if (!ensureCapacity(buffer, eventCounter + 8)) {
                                    break;
                                }

                                final int polarity = ((data & 0x0100) != 0)? 1 : 0;
                                final int lastY = (code == 3)? dvsLastYG1 : dvsLastYG2;

                                for (int i = 0, mask = 0x0001; i < 8; i++, mask <<= 1) {
                                    // Check if event present first.
                                    if ((data & mask) == 0) {
                                        continue;
                                    }

                                    int xAddr = dvsLastX;
                                    int yAddr = lastY + i;

                                    if (dvsDualBinning) {
                                        if (dvsFlipX && (xAddr >= (dvsSizeX / 2))) {
                                            xAddr -= (int)(dvsSizeX / 2);
                                        }
                                        if (dvsFlipY && (yAddr >= (dvsSizeY / 2))) {
                                            yAddr -= (int)(dvsSizeY / 2);
                                        }
                                    }

                                    if (dvsInvertXY) {
                                        final int temp = xAddr;
                                        xAddr = yAddr;
                                        yAddr = temp;
                                    }

                                    buffer.getTimestamps()[eventCounter] = currentTimestamp;

                                    // aedat2 format
                                    final int y_addr_bits = ((yAddr & AEDAT2_Y_ADDR_MASK) << AEDAT2_Y_ADDR_SHIFT);
                                    final int x_addr_bits = ((xAddr & AEDAT2_X_ADDR_MASK) << AEDAT2_X_ADDR_SHIFT);
                                    final int polarity_bits = ((polarity & AEDAT2_POLARITY_MASK) << AEDAT2_POLARITY_SHIFT);
                                    final int bits32 = y_addr_bits | x_addr_bits | polarity_bits;

                                    buffer.getAddresses()[eventCounter] = bits32;
                                    eventCounter++;
                                }

                                break;

                            case 4:
                                // Decode address.
                                int group1Address = data & 0x003F;
                                final int group2Offset = ((data >>> 6) & 0x001F);
                                int group2Address = ((data & 0x0800) != 0)? (group1Address - group2Offset) : (group1Address + group2Offset);
                                // 8 pixels per group.
                                group1Address *= 8;
                                group2Address *= 8;

                                // Check range conformity.
                                if (group1Address >= dvsSizeY) {
                                    CypressFX3.log.severe(String.format("DVS: Group1 Y address out of range (0-%d): %d.", (dvsSizeY - 1), group1Address));
                                    break;  // Skip invalid G1 Y address (don't update lastYs).
                                }
                                if (group2Address >= dvsSizeY) {
                                    CypressFX3.log.severe(String.format("DVS: Group2 Y address out of range (0-%d): %d.", (dvsSizeY - 1), group2Address));
                                    break;  // Skip invalid G2 Y address (don't update lastYs).
                                }

                                dvsLastYG1 = group1Address;
                                dvsLastYG2 = group2Address;

                                break;

                            case 5: // Misc 8bit data.
                                final byte misc8Code = (byte) ((data & 0x0F00) >>> 8);
                                final byte misc8Data = (byte) (data & 0x00FF);

                                switch (misc8Code) {
                                    case 0:
                                        // IMU data event.
                                        if (imuIgnoreEvents) {
                                            break;
                                        }
                                        CypressFX3.log.fine("IMU Data event received.");

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
                                                short accelY = (short)(((imuTmpData & 0x00FF) << 8) | (misc8Data & 0x00FF));
                                                if (imuFlipY) {
                                                    accelY = (short)(-accelY);
                                                }
                                                float ay = (float)accelY / imuAccelScale;
                                                imuEvents[1] = (short)(ay / ACCEL_G_PER_LSB);
                                                break;

                                            case 3:
                                                short accelX = (short)(((imuTmpData & 0x00FF) << 8) | (misc8Data & 0x00FF));
                                                if (imuFlipX) {
                                                    accelX = (short)(-accelX);
                                                }
                                                float ax = (float)accelX / imuAccelScale;
                                                imuEvents[0] = (short)(ax / ACCEL_G_PER_LSB);
                                                break;

                                            case 5:
                                                short accelZ = (short)(((imuTmpData & 0x00FF) << 8) | (misc8Data & 0x00FF));
                                                if (imuFlipZ) {
                                                    accelZ = (short)(-accelZ);
                                                }
                                                float az = (float)accelZ / imuAccelScale;
                                                imuEvents[2] = (short)(az / ACCEL_G_PER_LSB);

                                                // IMU parser count depends on which data is present.
                                                if ((imuType & IMU_TYPE_TEMP) == 0) {
                                                    if ((imuType & IMU_TYPE_GYRO) != 0) {
                                                        // No temperature, but gyro.
                                                        imuCount += 2;
                                                    }
                                                    else {
                                                        // No others enabled.
                                                        imuCount += 8;
                                                    }
                                                }
                                                break;

                                            case 7: // Temperature
                                                short temp = (short)(((imuTmpData & 0x00FF) << 8) | (misc8Data & 0x00FF));
                                                float t = ((float)temp / 512.0F) + 23.0F;
                                                imuEvents[3] = (short)((t - TEMP_DEGC_OFFSET) / TEMP_DEGC_PER_LSB);

                                                // IMU parser count depends on which data is present.
                                                if ((imuType & IMU_TYPE_GYRO) == 0) {
                                                    // No others enabled.
                                                    imuCount += 6;
                                                }
                                                break;

                                            case 9: // Gyro Y
                                                short gyroY = (short)(((imuTmpData & 0x00FF) << 8) | (misc8Data & 0x00FF));
                                                if (imuFlipY) {
                                                    gyroY = (short)(-gyroY);
                                                }
                                                float gy = (float)gyroY / imuGyroScale;
                                                imuEvents[5] = (short)(gy / GYRO_DPS_PER_LSB);
                                                break;

                                            case 11: // Gyro X
                                                short gyroX = (short)(((imuTmpData & 0x00FF) << 8) | (misc8Data & 0x00FF));
                                                if (imuFlipX) {
                                                    gyroX = (short)(-gyroX);
                                                }
                                                float gx = (float)gyroX / imuGyroScale;
                                                imuEvents[4] = (short)(gx / GYRO_DPS_PER_LSB);
                                                break;

                                            case 13: // Gyro Z
                                                short gyroZ = (short)(((imuTmpData & 0x00FF) << 8) | (misc8Data & 0x00FF));
                                                if (imuFlipZ) {
                                                    gyroZ = (short)(-gyroZ);
                                                }
                                                float gz = (float)gyroZ / imuGyroScale;
                                                imuEvents[6] = (short)(gz / GYRO_DPS_PER_LSB);
                                                break;
                                                
                                            default:
                                                CypressFX3.log.severe("Got invalid IMU update sequence.");
                                                break;
                                        }

                                        imuCount++;
                                        break;

                                    case 3:
                                        if (imuIgnoreEvents) {
                                            break;
                                        }
                                        CypressFX3.log.fine("IMU Scale Config event received.");

                                        // Set correct IMU accel and gyro scales, used to interpret subsequent
                                        // IMU samples from the device.
                                        final int accelScale = (data >>> 3) & 0x03;
                                        imuAccelScale = 65536.0F / (float)(4 * (1 << accelScale));

                                        final int gyroScale = (data & 0x07);
                                        final int gyroScaleAsc = 4 - gyroScale;
                                        imuGyroScale = 65536.0F / (float)(250 * (1 << gyroScaleAsc));

                                        // Set expected type of data to come from IMU (accel, gyro, temp).
                                        imuType = (data >>> 5) & 0x07;

                                        // IMU parser start count depends on which data is present.
                                        if ((imuType & IMU_TYPE_ACCEL) != 0) {
                                            // Accelerometer.
                                            imuCount = 0;
                                        }
                                        else if ((imuType & IMU_TYPE_TEMP) != 0) {
                                            // Temperature
                                            imuCount = 6;
                                        }
                                        else if ((imuType & IMU_TYPE_GYRO) != 0) {
                                            // Gyroscope.
                                            imuCount = 8;
                                        }
                                        else {
                                            // Nothing, should never happen.
                                            imuCount = 14;
                                            CypressFX3.log.severe("IMU Scale Config: no IMU sensors enabled.");
                                        }

                                        break;

                                    default:
                                        CypressFX3.log.severe("Caught Misc8 event that can't be handled.");
                                        break;
                                }

                                break;

                            case 7: 
                                // Timestamp wrap
                                // Each wrap is 2^15 us (~32ms), and we have
                                // to multiply it with the wrap counter,
                                // which is located in the data part of this
                                // event.

                                // handleTimestampWrapNewLogic
                                final long TS_WRAP_ADD = 0x8000;
                                long wrapJump = TS_WRAP_ADD * (long)data;
                                long wrapSum = (long)wrapAdd + wrapJump;

                                if (wrapSum > (long)Integer.MAX_VALUE) {
                                    long wrapRemainder = wrapSum - (long)Integer.MAX_VALUE - 1L;
                                    wrapAdd = (int)wrapRemainder;

                                    lastTimestamp = 0;
                                    currentTimestamp = wrapAdd;

                                    wrapOverflow++;
                                }
                                else {
                                    wrapAdd = (int)wrapSum;

                                    lastTimestamp = currentTimestamp;
                                    currentTimestamp = wrapAdd;

                                    // Check monotonicity of timestamps.
                                    checkMonotonicTimestamp();

                                    CypressFX3.log.fine(
                                        String.format("Timestamp wrap event received on %s with multiplier of %d.", super.toString(), data));
                                }

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

