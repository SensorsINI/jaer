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
package ch.unizh.ini.jaer.chip.retina;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;
import com.jogamp.opengl.util.awt.TextRenderer;
import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.net.URL;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.UsbDevice;
import net.sf.jaer.UsbDevices;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.chip.RetinaExtractor;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.ImuPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.graphics.ChipRendererDisplayMethodRGBA;
import net.sf.jaer.graphics.DavisRenderer;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.UsbLog;
import net.sf.jaer.hardwareinterface.usb.UsbTransferSubmit;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.CypressFX3;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.DVXplorerFX3HardwareInterface;
import net.sf.jaer.util.TextRendererScale;

/**
 * Describe DVXplorer and its event extractor and configuration.
 * 
 * @author Pei Haoxiang, Tobi Delbruck
 */
@Description("DVXplorer, 640x480, 9um pitch, Samsung DVS FX3 (iniVation). Mini/Micro is DVXplorerMicro.")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
@UsbDevices({
    @UsbDevice(vid = CypressFX3.VID, pid = DVXplorerFX3HardwareInterface.PID_FX3)
})
public class DVXplorer extends AETemporalConstastRetina {
    
    private DVXExtractor dvxExtractor;
    private DVXRenderer dvxRenderer;
    private DVXDisplayMethod dvxDisplayMethod;
   
    private DVXplorerFX3HardwareInterface fx3;
    private JMenu dvxMenu = null;
    private ToggleIMU toggleIMU;
    private static final boolean debug = DVXplorerFX3HardwareInterface.debug;
    
    // DVXplorer Mini/Micro (CX3 MIPI) — detected from USB bcdDevice, not a compile-time constant.
    public boolean extInputHasGenerator = true;
    
    // DVXplorer params (should be right)
    public double logicClockActual = 104.0;
    public double usbClockActual = 83.199997;
    public short sizeX = 640;
    public short sizeY = 480;
    public short dvsInvertXY = 0;
    public short dvsFlipX = 1;
    public short dvsFlipY = 0;
    public boolean dvsDualBinning = false;
    public short imuFlipX = 0;
    public short imuFlipY = 0;
    public short imuFlipZ = 0;
    
    /**
     * Create a new instance of DVXplorer.
     */
    public DVXplorer() {
        setName("DVXplorer");
        setSizeX(sizeX);
        setSizeY(sizeY);
        setNumCellTypes(2);
        setPixelHeightUm(9);
        setPixelWidthUm(9);
        
        dvxExtractor = new DVXExtractor(this);
        setEventExtractor(dvxExtractor);
        
        dvxRenderer = new DVXRenderer(this);
        setRenderer(dvxRenderer);

        dvxDisplayMethod = new DVXDisplayMethod(this);
        getCanvas().addDisplayMethod(dvxDisplayMethod);
        getCanvas().setDisplayMethod(dvxDisplayMethod);

        setBiasgen(new DVXplorerConfig(this));
    }

    public DVXplorerConfig getDvxConfig() {
        if (getBiasgen() instanceof DVXplorerConfig cfg) {
            return cfg;
        }
        return null;
    }

    /** BMI160 capture (SPI IMU_RUN and Mini/Micro EP 0x81). Default on. */
    public boolean isImuCaptureEnabled() {
        final DVXplorerConfig cfg = getDvxConfig();
        return cfg == null || cfg.isImuEnabled();
    }

    /** Canvas overlay of the last IMU sample. */
    public boolean isDisplayImu() {
        final DVXplorerConfig cfg = getDvxConfig();
        return cfg != null && cfg.isDisplayImu();
    }
    
    // Module address
    public final static short DEVICE_DVS = 5;
    public final static short DVX_MUX = 0;
    public final static short DVX_DVS = 1;
    public final static short DVX_IMU = 3;
    public final static short DVX_EXTINPUT = 4;
    public final static short DVX_SYSINFO = 6;
    public final static short DVX_USB = 9;
    public final static short DVX_DVS_CHIP = 20;

    /** Next-gen Mini/Micro (firmware ≥10) DVS module params — same module as {@link #DVX_DVS}. */
    public final static short DVS_FLATTEN = 4;
    public final static short DVS_SUBSAMPLE_HORIZONTAL = 5;
    public final static short DVS_SUBSAMPLE_VERTICAL = 6;
    public final static short DVS_GLOBAL_HOLD = 8;
    public final static short DVS_GLOBAL_RESET = 9;
    public final static short DVS_GLOBAL_RESET_SKIP = 10;
    public final static short DVS_MIPI_TIMEOUT_ENABLE = 11;
    public final static short DVS_MIPI_TIMEOUT_VALUE = 12;
    public final static short DVS_EFPS_S5K231Y = 13;
    public final static short DVS_CONTRAST_THRESHOLD_ON = 14;
    public final static short DVS_CONTRAST_THRESHOLD_OFF = 15;
    public final static short DVS_CROP_X = 19;
    public final static short DVS_CROP_Y = 20;
    public final static short DVS_CROP_WIDTH = 21;
    public final static short DVS_CROP_HEIGHT = 22;
    public final static short DVS_CROP_APPLY = 23;
    public final static short DVS_FLIP_HORIZONTAL = 24;
    public final static short DVS_FLIP_VERTICAL = 25;
    
    // Param address
    public final static short REGISTER_CONTROL_CLOCK_DIVIDER_SYS = 0x3011;
    public final static short REGISTER_CONTROL_PARALLEL_OUT_CONTROL = 0x3019;
    public final static short REGISTER_CONTROL_PARALLEL_OUT_ENABLE = 0x301E;
    public final static short REGISTER_CONTROL_PACKET_FORMAT = 0x3067;
    
    public final static short DVX_MUX_RUN = 0;
    public final static short DVX_MUX_TIMESTAMP_RUN = 1;
    public final static short DVX_MUX_RUN_CHIP = 3;
    public final static short DVX_MUX_DROP_EXTINPUT_ON_TRANSFER_STALL = 4;
    public final static short DVX_MUX_DROP_DVS_ON_TRANSFER_STALL = 5;
    
    public final static short DVX_IMU_ORIENTATION_INFO = 1;
    public final static short DVX_IMU_RUN_ACCELEROMETER = 2;
    public final static short DVX_IMU_RUN_GYROSCOPE = 3;
    public final static short DVX_IMU_RUN_TEMPERATURE = 4;
    public final static short DVX_IMU_ACCEL_DATA_RATE = 5;
    public final static short DVX_IMU_ACCEL_FILTER = 6;
    public final static short DVX_IMU_ACCEL_RANGE = 7;
    public final static short DVX_IMU_GYRO_DATA_RATE = 8;
    public final static short DVX_IMU_GYRO_FILTER = 9;
    public final static short DVX_IMU_GYRO_RANGE = 10;
    
    public final static short DVX_EXTINPUT_RUN_DETECTOR = 0;
    public final static short DVX_EXTINPUT_DETECT_RISING_EDGES = 1;
    public final static short DVX_EXTINPUT_DETECT_FALLING_EDGES = 2;
    public final static short DVX_EXTINPUT_DETECT_PULSES = 3;
    public final static short DVX_EXTINPUT_DETECT_PULSE_POLARITY = 4;
    public final static short DVX_EXTINPUT_DETECT_PULSE_LENGTH = 5;
    public final static short DVX_EXTINPUT_RUN_GENERATOR = 11;
    
    public final static short DVX_USB_RUN = 0;
    public final static short DVX_USB_EARLY_PACKET_DELAY = 1;
            
    public final static short REGISTER_BIAS_CURRENT_RANGE_SELECT_LOGSFONREST = 0x000B;
    public final static short REGISTER_BIAS_CURRENT_RANGE_SELECT_LOGALOGD_MONITOR = 0x000C;
    public final static short REGISTER_BIAS_OTP_TRIM = 0x000D;
    public final static short REGISTER_BIAS_PINS_DBGP = 0x000F;
    public final static short REGISTER_BIAS_PINS_DBGN = 0x0010;
    public final static short REGISTER_BIAS_CURRENT_LEVEL_SFOFF = 0x0012;
    public final static short REGISTER_BIAS_PINS_BUFP = 0x0013;
    public final static short REGISTER_BIAS_PINS_BUFN = 0x0014;
    public final static short REGISTER_BIAS_PINS_DOB = 0x0015;
    public final static short REGISTER_BIAS_CURRENT_AMP = 0x0018;
    public final static short REGISTER_BIAS_CURRENT_ON = 0x001C;
    public final static short REGISTER_BIAS_CURRENT_OFF = 0x001E;
    
    public final static short REGISTER_CONTROL_MODE = 0x3000;
    public final static short REGISTER_DIGITAL_ENABLE = 0x3200;
    public final static short REGISTER_DIGITAL_RESTART = 0x3201;
    public final static short REGISTER_DIGITAL_DUAL_BINNING = 0x3202;
    public final static short REGISTER_DIGITAL_SUBSAMPLE_RATIO = 0x3204;
    public final static short REGISTER_DIGITAL_AREA_BLOCK = 0x3205;
    public final static short REGISTER_DIGITAL_TIMESTAMP_SUBUNIT = 0x3234;
    public final static short REGISTER_DIGITAL_TIMESTAMP_REFUNIT = 0x3235;
    public final static short REGISTER_DIGITAL_DTAG_REFERENCE = 0x323D;
    public final static short REGISTER_DIGITAL_TIMESTAMP_RESET = 0x3238;
    public final static short REGISTER_TIMING_FIRST_SELX_START = 0x323C;
    public final static short REGISTER_TIMING_GH_COUNT = 0x3240;
    public final static short REGISTER_TIMING_GH_COUNT_FINE = 0x3243;
    public final static short REGISTER_TIMING_GRS_COUNT = 0x3244;
    public final static short REGISTER_TIMING_GRS_COUNT_FINE = 0x3247;
    public final static short REGISTER_DIGITAL_GLOBAL_RESET_READOUT = 0x3248;
    public final static short REGISTER_TIMING_NEXT_GH_CNT = 0x324B;
    public final static short REGISTER_TIMING_SELX_WIDTH = 0x324C;
    public final static short REGISTER_TIMING_AY_START = 0x324E;
    public final static short REGISTER_TIMING_AY_END = 0x324F;
    public final static short REGISTER_TIMING_MAX_EVENT_NUM = 0x3251;
    public final static short REGISTER_TIMING_R_START = 0x3253;
    public final static short REGISTER_TIMING_R_END = 0x3254;
    public final static short REGISTER_DIGITAL_MODE_CONTROL = 0x3255;
    public final static short REGISTER_TIMING_GRS_END = 0x3256;
    public final static short REGISTER_TIMING_GRS_END_FINE = 0x3259;
    public final static short REGISTER_DIGITAL_FIXED_READ_TIME = 0x325C;
    public final static short REGISTER_TIMING_READ_TIME_INTERVAL = 0x325D;
    public final static short REGISTER_DIGITAL_EXTERNAL_TRIGGER = 0x3260;
    public final static short REGISTER_TIMING_NEXT_SELX_START = 0x3261;
    public final static short REGISTER_DIGITAL_BOOT_SEQUENCE = 0x3266;
    
    public final static short REGISTER_CROPPER_BYPASS = 0x3300;
    public final static short REGISTER_ACTIVITY_DECISION_BYPASS = 0x3500;
    public final static short REGISTER_SPATIAL_HISTOGRAM_OFF = 0x3600;
    
    public final static short DVX_DVS_CHIP_AREA_BLOCKING_0 = 9;
    public final static short DVX_DVS_CHIP_AREA_BLOCKING_1 = 10;
    public final static short DVX_DVS_CHIP_AREA_BLOCKING_2 = 11;
    public final static short DVX_DVS_CHIP_AREA_BLOCKING_3 = 12;
    public final static short DVX_DVS_CHIP_AREA_BLOCKING_4 = 13;
    public final static short DVX_DVS_CHIP_AREA_BLOCKING_5 = 14;
    public final static short DVX_DVS_CHIP_AREA_BLOCKING_6 = 15;
    public final static short DVX_DVS_CHIP_AREA_BLOCKING_7 = 16;
    public final static short DVX_DVS_CHIP_AREA_BLOCKING_8 = 17;
    public final static short DVX_DVS_CHIP_AREA_BLOCKING_9 = 18;
    public final static short DVX_DVS_CHIP_AREA_BLOCKING_10 = 19;
    public final static short DVX_DVS_CHIP_AREA_BLOCKING_11 = 20;
    public final static short DVX_DVS_CHIP_AREA_BLOCKING_12 = 21;
    public final static short DVX_DVS_CHIP_AREA_BLOCKING_13 = 22;
    public final static short DVX_DVS_CHIP_AREA_BLOCKING_14 = 23;
    public final static short DVX_DVS_CHIP_AREA_BLOCKING_15 = 24;
    public final static short DVX_DVS_CHIP_AREA_BLOCKING_16 = 25;
    public final static short DVX_DVS_CHIP_AREA_BLOCKING_17 = 26;
    public final static short DVX_DVS_CHIP_AREA_BLOCKING_18 = 27;
    public final static short DVX_DVS_CHIP_AREA_BLOCKING_19 = 28;
    
    public final static short DVX_SYSINFO_LOGIC_VERSION = 0;
    public final static short DVX_SYSINFO_CHIP_IDENTIFIER = 1;
    public final static short DVX_SYSINFO_DEVICE_IS_MASTER = 2;
    public final static short DVX_SYSINFO_LOGIC_CLOCK = 3;
    public final static short DVX_SYSINFO_USB_CLOCK = 5;
    public final static short DVX_SYSINFO_CLOCK_DEVIATION = 6;
    
    public final static short DVX_DVS_SIZE_COLUMNS = 0;
    public final static short DVX_DVS_SIZE_ROWS = 1;
    public final static short DVX_DVS_ORIENTATION_INFO = 2;
    public final static short DVX_DVS_RUN = 3;
    
    public boolean isMipiCX3Device() {
        if (this instanceof DVXplorerMicro) {
            return true;
        }
        if (getHardwareInterface() instanceof DVXplorerFX3HardwareInterface d) {
            return d.isMipiCX3Device();
        }
        return false;
    }

    public boolean isNextGenFirmware() {
        if (getHardwareInterface() instanceof DVXplorerFX3HardwareInterface d) {
            return d.isNextGenFirmware();
        }
        return false;
    }

    /**
     * Configurate DVXplorer.
     */
    public void dvxConfig() {
        fx3 = (DVXplorerFX3HardwareInterface) this.getHardwareInterface();
        log.fine("dvxConfig begin " + UsbLog.t());
        dvxReceiveInitParams(fx3);
        log.fine("dvxConfig after dvxReceiveInitParams " + UsbLog.t());
        dvxSendOpeningConfig(fx3);
        log.fine("dvxConfig after dvxSendOpeningConfig " + UsbLog.t());
        dvxSendDefaultConfig(fx3);
        log.fine("dvxConfig after dvxSendDefaultConfig " + UsbLog.t());
    }
    
    /**
     * Refer to libcaer dvxplorer.c dvXplorerOpen() for details.
     * 
     * @param fx3 CypressFX3 interface
     */
    public void dvxReceiveInitParams(DVXplorerFX3HardwareInterface fx3) {
        if (!isMipiCX3Device()) {
            if (fx3 != null && fx3.classicSpiBlockedBySiblingReaders()) {
                sizeX = 640;
                sizeY = 480;
                setSizeX(sizeX);
                setSizeY(sizeY);
                DVXplorer.log.info("Classic DVX: skipping SPI IN init params (WinUSB hang with other LIVE cameras); using 640x480");
                return;
            }
            final int logicClock = spiConfigReceive(fx3, DVX_SYSINFO, DVX_SYSINFO_LOGIC_CLOCK);
            final int usbClock = spiConfigReceive(fx3, DVX_SYSINFO, DVX_SYSINFO_USB_CLOCK);
            final int clockDeviationFactor = spiConfigReceive(fx3, DVX_SYSINFO, DVX_SYSINFO_CLOCK_DEVIATION);
            logicClockActual = (double) logicClock * (double) clockDeviationFactor / 1000.0;
            usbClockActual = (double) usbClock * (double) clockDeviationFactor / 1000.0;
            DVXplorer.log.info(String.format("Device clock frequencies - Logic: %f, USB: %f.", logicClockActual, usbClockActual));
        } else {
            setName("DVXplorerMicro");
            if (debug) {
                DVXplorer.log.info(String.format(
                        "DVXplorer Mini/Micro CX3 MIPI firmware %d (%s)",
                        fx3.getFirmwareVersion(),
                        isNextGenFirmware() ? "dv-processing DVXplorerM protocol" : "libcaer pre-v10 protocol"));
            }
        }

        if (isNextGenFirmware()) {
            sizeX = 640;
            sizeY = 480;
            setSizeX(sizeX);
            setSizeY(sizeY);
            DVXplorer.log.info("Mini/Micro firmware 10+: using 640x480 (SPI size/orientation IN hangs)");
            return;
        }

        sizeX = (short)spiConfigReceive(fx3, DVX_DVS, DVX_DVS_SIZE_COLUMNS);
        sizeY = (short)spiConfigReceive(fx3, DVX_DVS, DVX_DVS_SIZE_ROWS);
        if (sizeX <= 0 || sizeY <= 0) {
            sizeX = 640;
            sizeY = 480;
            DVXplorer.log.warning("DVS resolution SPI failed, using 640x480");
        }
        setSizeX(sizeX);
        setSizeY(sizeY);

        final int dvsOrientation = spiConfigReceive(fx3, DVX_DVS, DVX_DVS_ORIENTATION_INFO);
        dvsInvertXY = (short)(dvsOrientation & 0x04);
        dvsFlipX = (short)(dvsOrientation & 0x02);
        dvsFlipY = (short)(dvsOrientation & 0x01);

        final int imuOrientation = spiConfigReceive(fx3, DVX_IMU, DVX_IMU_ORIENTATION_INFO);
        imuFlipX = (short)(imuOrientation & 0x04);
        imuFlipY = (short)(imuOrientation & 0x02);
        imuFlipZ = (short)(imuOrientation & 0x01);
    }
    
    /**
     * Refer to libcaer dvxplorer.c dvXplorerOpen() for details.
     * 
     * @param fx3 CypressFX3 interface
     */
    public void dvxSendOpeningConfig(DVXplorerFX3HardwareInterface fx3) {
        if (!isMipiCX3Device()) {
            if (fx3 != null && fx3.classicSpiBlockedBySiblingReaders()) {
                DVXplorer.log.info("Classic DVX: skipping SPI OUT opening config (WinUSB hang with other LIVE cameras)");
                return;
            }
            // Initialize Samsung DVS chip.
            spiConfigSendAndCheck(fx3, DVX_MUX, DVX_MUX_RUN_CHIP, 1);

            // Wait 10ms for DVS to start.
            try {
                Thread.sleep(10);
            }
            catch (InterruptedException e) {
                DVXplorer.log.warning("DVXplorer didn't wait for DVS to start.");
            }

            // Bias reset.
            spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_BIAS_OTP_TRIM, 0x24);

            // Bias enable.
            spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_BIAS_PINS_DBGP, 0x07);
            spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_BIAS_PINS_DBGN, 0xFF);
            spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_BIAS_PINS_BUFP, 0x03);
            spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_BIAS_PINS_BUFN, 0x7F);
            spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_BIAS_PINS_DOB, 0x00);

            spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_BIAS_CURRENT_AMP, 0x04);
            spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_BIAS_CURRENT_RANGE_SELECT_LOGALOGD_MONITOR, 0x14);

            // DVX_DVS_CHIP_BIAS_SIMPLE_DEFAULT (overwritten by DVXplorerConfig after open)
            spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_BIAS_CURRENT_RANGE_SELECT_LOGSFONREST, 0x06);
            spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_BIAS_CURRENT_LEVEL_SFOFF, 0x7D);
            spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_BIAS_CURRENT_ON, 0x00);
            spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_BIAS_CURRENT_OFF, 0x08);

            // System settings.
            spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_CONTROL_CLOCK_DIVIDER_SYS, 0xA0);
            spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_CONTROL_PARALLEL_OUT_CONTROL, 0x00);
            spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_CONTROL_PARALLEL_OUT_ENABLE, 0x01);
            spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_CONTROL_PACKET_FORMAT, 0x80);

            // Digital settings.
            spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_DIGITAL_MODE_CONTROL, 0x0C);
            spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_DIGITAL_BOOT_SEQUENCE, 0x08);

            spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_DIGITAL_TIMESTAMP_REFUNIT, 0x03);
            spiConfigSendAndCheck(fx3, DEVICE_DVS, U16T(REGISTER_DIGITAL_TIMESTAMP_REFUNIT + 1), 0xE7);

            // Fine clock counts based on clock frequency.
            spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_DIGITAL_TIMESTAMP_SUBUNIT, 49);
            spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_DIGITAL_DTAG_REFERENCE, 50);
            spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_TIMING_GH_COUNT_FINE, 50);
            spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_TIMING_GRS_COUNT_FINE, 50);
            spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_TIMING_GRS_END_FINE, 50);

            // Disable histogram, not currently used/mapped.
            spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_SPATIAL_HISTOGRAM_OFF, 0x01);
        }
    }
    
    /**
     * Refer to libcaer dvxplorer.c dvXplorerSendDefaultConfig() for details.
     * 
     * @param fx3 CypressFX3 interface
     */
    public void dvxSendDefaultConfig(DVXplorerFX3HardwareInterface fx3) {
        if (!isMipiCX3Device()) {
            // If not MipiCX3 device, set DVX_MUX
            spiConfigSendAndCheck(fx3, DVX_MUX, DVX_MUX_DROP_EXTINPUT_ON_TRANSFER_STALL, 1);
            spiConfigSendAndCheck(fx3, DVX_MUX, DVX_MUX_DROP_DVS_ON_TRANSFER_STALL, 0);
        }

        // DVX_IMU (FX3 and pre-v10 CX3; Mini/Micro firmware 10+ keeps device defaults)
        if (!isNextGenFirmware()) {
            spiConfigSendAndCheck(fx3, DVX_IMU, DVX_IMU_ACCEL_DATA_RATE, 6);
            spiConfigSendAndCheck(fx3, DVX_IMU, DVX_IMU_ACCEL_FILTER, 2);
            spiConfigSendAndCheck(fx3, DVX_IMU, DVX_IMU_ACCEL_RANGE, 1);
            spiConfigSendAndCheck(fx3, DVX_IMU, DVX_IMU_GYRO_DATA_RATE, 5);
            spiConfigSendAndCheck(fx3, DVX_IMU, DVX_IMU_GYRO_FILTER, 2);
            spiConfigSendAndCheck(fx3, DVX_IMU, DVX_IMU_GYRO_RANGE, 2);
        }
            
        if (!isMipiCX3Device()) {
            // If not MipiCX3 device, set DVX_EXTINPUT
            spiConfigSendAndCheck(fx3, DVX_EXTINPUT, DVX_EXTINPUT_DETECT_RISING_EDGES, 0);
            spiConfigSendAndCheck(fx3, DVX_EXTINPUT, DVX_EXTINPUT_DETECT_FALLING_EDGES, 0);
            spiConfigSendAndCheck(fx3, DVX_EXTINPUT, DVX_EXTINPUT_DETECT_PULSES, 1);
            spiConfigSendAndCheck(fx3, DVX_EXTINPUT, DVX_EXTINPUT_DETECT_PULSE_POLARITY, 1);
            double timeCC = 10 * logicClockActual;
            spiConfigSendAndCheck(fx3, DVX_EXTINPUT, DVX_EXTINPUT_DETECT_PULSE_LENGTH, (int)timeCC);

            if (extInputHasGenerator) {
                // If not MipiCX3 device and external input has generator, disable generator by default
                spiConfigSendAndCheck(fx3, DVX_EXTINPUT, DVX_EXTINPUT_RUN_GENERATOR, 0);
            }

            double delayCC = 8 * 125.0F * usbClockActual;
            spiConfigSendAndCheck(fx3, DVX_USB, DVX_USB_EARLY_PACKET_DELAY, (int)delayCC);
        }

        if (isNextGenFirmware()) {
            sendNextGenDefaultConfig(fx3);
            return;
        }
            
        // DVX_DVS_CHIP_BIAS_SIMPLE_DEFAULT
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_BIAS_CURRENT_RANGE_SELECT_LOGSFONREST, 0x06);
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_BIAS_CURRENT_LEVEL_SFOFF, 0x7D);
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_BIAS_CURRENT_ON, 0x00);
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_BIAS_CURRENT_OFF, 0x08);
            
        // DVX_DVS_CHIP_EXTERNAL_TRIGGER_MODE_TIMESTAMP_RESET
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_DIGITAL_EXTERNAL_TRIGGER, 0);
            
        // DVX_DVS_CHIP_GLOBAL_HOLD_ENABLE
        int currVal = spiConfigReceive(fx3, DEVICE_DVS, REGISTER_DIGITAL_MODE_CONTROL);
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_DIGITAL_MODE_CONTROL, U8T(currVal | 0x01));

        // DVX_DVS_CHIP_GLOBAL_RESET_ENABLE
        currVal = spiConfigReceive(fx3, DEVICE_DVS, REGISTER_DIGITAL_MODE_CONTROL);
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_DIGITAL_MODE_CONTROL, U8T(U8T(currVal) & ~0x02));

        // DVX_DVS_CHIP_GLOBAL_RESET_DURING_READOUT
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_DIGITAL_GLOBAL_RESET_READOUT, 0x00);

        // DVX_DVS_CHIP_FIXED_READ_TIME_ENABLE
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_DIGITAL_FIXED_READ_TIME, 0x00);

        // DVX_DVS_CHIP_EVENT_FLATTEN
        currVal = spiConfigReceive(fx3, DEVICE_DVS, REGISTER_CONTROL_PACKET_FORMAT);
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_CONTROL_PACKET_FORMAT, U8T(U8T(currVal) & ~0x40));

        // DVX_DVS_CHIP_EVENT_ON_ONLY
        currVal = spiConfigReceive(fx3, DEVICE_DVS, REGISTER_CONTROL_PACKET_FORMAT);
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_CONTROL_PACKET_FORMAT, U8T(U8T(currVal) & ~0x20));

        // DVX_DVS_CHIP_EVENT_OFF_ONLY
        currVal = spiConfigReceive(fx3, DEVICE_DVS, REGISTER_CONTROL_PACKET_FORMAT);
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_CONTROL_PACKET_FORMAT, U8T(U8T(currVal) & ~0x10));

        // DVX_DVS_CHIP_SUBSAMPLE_ENABLE
        currVal = spiConfigReceive(fx3, DEVICE_DVS, REGISTER_DIGITAL_ENABLE);
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_DIGITAL_ENABLE, U8T(currVal | 0x04));

        // DVX_DVS_CHIP_AREA_BLOCKING_ENABLE
        currVal = spiConfigReceive(fx3, DEVICE_DVS, REGISTER_DIGITAL_ENABLE);
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_DIGITAL_ENABLE, U8T(currVal | 0x02));
            
        // DVX_DVS_CHIP_DUAL_BINNING_ENABLE
        dvsDualBinning = (0x00 > 0);
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_DIGITAL_DUAL_BINNING, 0x00);
            
        // DVX_DVS_CHIP_SUBSAMPLE_VERTICAL
        currVal = spiConfigReceive(fx3, DEVICE_DVS, REGISTER_DIGITAL_SUBSAMPLE_RATIO);
        currVal = U8T(U8T(currVal) & ~0x38) | U8T(0 << 3);
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_DIGITAL_SUBSAMPLE_RATIO, currVal);
            
        // DVX_DVS_CHIP_SUBSAMPLE_HORIZONTAL
        currVal = spiConfigReceive(fx3, DEVICE_DVS, REGISTER_DIGITAL_SUBSAMPLE_RATIO);
        currVal = U8T(U8T(currVal) & ~0x07) | U8T(0);
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_DIGITAL_SUBSAMPLE_RATIO, currVal);

        dvxConfigSet(fx3, DVX_DVS_CHIP, DVX_DVS_CHIP_AREA_BLOCKING_0, 0x7FFF);
        dvxConfigSet(fx3, DVX_DVS_CHIP, DVX_DVS_CHIP_AREA_BLOCKING_1, 0x7FFF);
        dvxConfigSet(fx3, DVX_DVS_CHIP, DVX_DVS_CHIP_AREA_BLOCKING_2, 0x7FFF);
        dvxConfigSet(fx3, DVX_DVS_CHIP, DVX_DVS_CHIP_AREA_BLOCKING_3, 0x7FFF);
        dvxConfigSet(fx3, DVX_DVS_CHIP, DVX_DVS_CHIP_AREA_BLOCKING_4, 0x7FFF);
        dvxConfigSet(fx3, DVX_DVS_CHIP, DVX_DVS_CHIP_AREA_BLOCKING_5, 0x7FFF);
        dvxConfigSet(fx3, DVX_DVS_CHIP, DVX_DVS_CHIP_AREA_BLOCKING_6, 0x7FFF);
        dvxConfigSet(fx3, DVX_DVS_CHIP, DVX_DVS_CHIP_AREA_BLOCKING_7, 0x7FFF);
        dvxConfigSet(fx3, DVX_DVS_CHIP, DVX_DVS_CHIP_AREA_BLOCKING_8, 0x7FFF);
        dvxConfigSet(fx3, DVX_DVS_CHIP, DVX_DVS_CHIP_AREA_BLOCKING_9, 0x7FFF);
        dvxConfigSet(fx3, DVX_DVS_CHIP, DVX_DVS_CHIP_AREA_BLOCKING_10, 0x7FFF);
        dvxConfigSet(fx3, DVX_DVS_CHIP, DVX_DVS_CHIP_AREA_BLOCKING_11, 0x7FFF);
        dvxConfigSet(fx3, DVX_DVS_CHIP, DVX_DVS_CHIP_AREA_BLOCKING_12, 0x7FFF);
        dvxConfigSet(fx3, DVX_DVS_CHIP, DVX_DVS_CHIP_AREA_BLOCKING_13, 0x7FFF);
        dvxConfigSet(fx3, DVX_DVS_CHIP, DVX_DVS_CHIP_AREA_BLOCKING_14, 0x7FFF);
        dvxConfigSet(fx3, DVX_DVS_CHIP, DVX_DVS_CHIP_AREA_BLOCKING_15, 0x7FFF);
        dvxConfigSet(fx3, DVX_DVS_CHIP, DVX_DVS_CHIP_AREA_BLOCKING_16, 0x7FFF);
        dvxConfigSet(fx3, DVX_DVS_CHIP, DVX_DVS_CHIP_AREA_BLOCKING_17, 0x7FFF);
        dvxConfigSet(fx3, DVX_DVS_CHIP, DVX_DVS_CHIP_AREA_BLOCKING_18, 0x7FFF);
        dvxConfigSet(fx3, DVX_DVS_CHIP, DVX_DVS_CHIP_AREA_BLOCKING_19, 0x7FFF);
            
        // DVX_DVS_CHIP_TIMING_ED
        int msec = 2 / 1000;
        int usec = 2 % 1000;
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_TIMING_GH_COUNT, U8T(msec));
        spiConfigSendAndCheck(fx3, DEVICE_DVS, U16T(REGISTER_TIMING_GH_COUNT + 1), U8T(usec >>> 8));
        spiConfigSendAndCheck(fx3, DEVICE_DVS, U16T(REGISTER_TIMING_GH_COUNT + 2), U8T(usec));
            
        // DVX_DVS_CHIP_TIMING_GH2GRS
        msec = 0 / 1000;
        usec = 0 % 1000;
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_TIMING_GRS_COUNT, U8T(msec));
        spiConfigSendAndCheck(fx3, DEVICE_DVS, U16T(REGISTER_TIMING_GRS_COUNT + 1), U8T(usec >>> 8));
        spiConfigSendAndCheck(fx3, DEVICE_DVS, U16T(REGISTER_TIMING_GRS_COUNT + 2), U8T(usec));

        // DVX_DVS_CHIP_TIMING_GRS
        msec = 1 / 1000;
        usec = 1 % 1000;
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_TIMING_GRS_END, U8T(msec));
        spiConfigSendAndCheck(fx3, DEVICE_DVS, U16T(REGISTER_TIMING_GRS_END + 1), U8T(usec >>> 8));
        spiConfigSendAndCheck(fx3, DEVICE_DVS, U16T(REGISTER_TIMING_GRS_END + 2), U8T(usec));

        // DVX_DVS_CHIP_TIMING_GH2SEL
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_TIMING_FIRST_SELX_START, U8T(4));

        // DVX_DVS_CHIP_TIMING_SELW
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_TIMING_SELX_WIDTH, U8T(6));

        // DVX_DVS_CHIP_TIMING_SEL2AY_R
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_TIMING_AY_START, U8T(4));

        // DVX_DVS_CHIP_TIMING_SEL2AY_F
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_TIMING_AY_END, U8T(6));

        // DVX_DVS_CHIP_TIMING_SEL2R_R
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_TIMING_R_START, U8T(8));

        // DVX_DVS_CHIP_TIMING_SEL2R_F
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_TIMING_R_END, U8T(10));

        // DVX_DVS_CHIP_TIMING_NEXT_SEL
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_TIMING_NEXT_SELX_START, U8T(15 >>> 8));
        spiConfigSendAndCheck(fx3, DEVICE_DVS, U16T(REGISTER_TIMING_NEXT_SELX_START + 1), U8T(15));
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_TIMING_MAX_EVENT_NUM, U8T(10));

        // DVX_DVS_CHIP_TIMING_NEXT_GH
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_TIMING_NEXT_GH_CNT, U8T(4));
            
        // DVX_DVS_CHIP_TIMING_READ_FIXED
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_TIMING_READ_TIME_INTERVAL, U8T(45000 >>> 8));
        spiConfigSendAndCheck(fx3, DEVICE_DVS, U16T(REGISTER_TIMING_READ_TIME_INTERVAL + 1), U8T(45000));

        // DVX_DVS_CHIP_CROPPER_ENABLE
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_CROPPER_BYPASS, 0x01);

        // DVX_DVS_CHIP_ACTIVITY_DECISION_ENABLE
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_ACTIVITY_DECISION_BYPASS, 0x01);

        // DTAG restart after config.
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_DIGITAL_RESTART, 2);
    }

    /** dv-processing {@code DVXplorerM} constructor defaults (firmware ≥10). */
    private void sendNextGenDefaultConfig(DVXplorerFX3HardwareInterface fx3) {
        // c6ec5a073 skipped every firmware-10 SPI OUT because DVS_FLATTEN hung
        // WinUSB. That also dropped BMI160 ODR/range; factory gyro ODR 5 is
        // reserved and freezes ~0 dps (Steadicam dead, overlay gyros stuck).
        // Restore IMU 8-byte SPI. Keep DVS_FLATTEN / size IN skipped.
        // Contrast / ReadoutFPS / global hold are 8-byte MODULE_DVS OUT via DVXplorerConfig.
        // Firmware ≥10 SPI uses BMI160 ODR register values (dv-processing
        // BoschBMI160* enums). Gyro ODR 5 is reserved and yields frozen ~0 dps.
        spiConfigSend(fx3, DVX_IMU, DVX_IMU_ACCEL_DATA_RATE, 11); // 800 Hz
        spiConfigSend(fx3, DVX_IMU, DVX_IMU_ACCEL_FILTER, 2); // NORMAL
        spiConfigSend(fx3, DVX_IMU, DVX_IMU_ACCEL_RANGE, 1); // 4 g
        spiConfigSend(fx3, DVX_IMU, DVX_IMU_GYRO_DATA_RATE, 11); // 800 Hz
        spiConfigSend(fx3, DVX_IMU, DVX_IMU_GYRO_FILTER, 2); // NORMAL
        spiConfigSend(fx3, DVX_IMU, DVX_IMU_GYRO_RANGE, 2); // 500 dps (MPU-6150 65.5 LSB/dps)
        DVXplorer.log.info("Mini/Micro firmware 10+: BMI160 800 Hz, accel 4 g, gyro 500 dps");
    }
    
    /**
     * Refer to libcaer dvxplorer.c dvXplorerDataStart() for details.
     * 
     * @param fx3 CypressFX3 interface
     */
    public void dvxDataStart() {
        fx3 = (DVXplorerFX3HardwareInterface) this.getHardwareInterface();

        if (isNextGenFirmware()) {
            spiConfigSendAndCheck(fx3, DVX_DVS, DVX_DVS_RUN, 1);
            applyImuRunFromConfig();
            if (debug) {
                final int run = spiConfigReceive(fx3, DVX_DVS, DVX_DVS_RUN);
                DVXplorer.log.info("Mini/Micro DVS_RUN readback=" + run);
            }
            return;
        }
        
        // Ensure no data is left over from previous runs, if the camera
        // wasn't shut-down properly. First ensure it is shut down completely.
        spiConfigSendAndCheck(fx3, DVX_DVS, DVX_DVS_RUN, 0);
        spiConfigSendAndCheck(fx3, DVX_IMU, DVX_IMU_RUN_ACCELEROMETER, 0);
        spiConfigSendAndCheck(fx3, DVX_IMU, DVX_IMU_RUN_GYROSCOPE, 0);
        spiConfigSendAndCheck(fx3, DVX_IMU, DVX_IMU_RUN_TEMPERATURE, 0);
        spiConfigSendAndCheck(fx3, DVX_EXTINPUT, DVX_EXTINPUT_RUN_DETECTOR, 0);

        if (!isMipiCX3Device()) {
            spiConfigSendAndCheck(fx3, DVX_MUX, DVX_MUX_RUN, 0);
            spiConfigSendAndCheck(fx3, DVX_MUX, DVX_MUX_TIMESTAMP_RUN, 0);
            spiConfigSendAndCheck(fx3, DVX_USB, DVX_USB_RUN, 0);
        }
        
        // Then wait 10ms for FPGA device side buffers to clear.
        try {
            Thread.sleep(10);
        }
        catch (InterruptedException e) {
            DVXplorer.log.warning("DVXplorer didn't wait for FPGA device side buffers to clear.");
        }
        
        
        if (!isMipiCX3Device()) {
            // Enable data transfer on USB end-point 2.
            spiConfigSendAndCheck(fx3, DVX_USB, DVX_USB_RUN, 1);
            spiConfigSendAndCheck(fx3, DVX_MUX, DVX_MUX_TIMESTAMP_RUN, 1);
            spiConfigSendAndCheck(fx3, DVX_MUX, DVX_MUX_RUN, 1);

            // Wait 50 ms for data transfer to be ready.
            try {
                Thread.sleep(50);
            }
            catch (InterruptedException e) {
                DVXplorer.log.warning("DVXplorer didn't wait for data transfer to be ready.");
            }

            spiConfigSendAndCheck(fx3, DVX_DVS, DVX_DVS_RUN, 1);
        }

        applyImuRunFromConfig();
        spiConfigSendAndCheck(fx3, DVX_EXTINPUT, DVX_EXTINPUT_RUN_DETECTOR, 1);

        // Enable streaming from DVS chip.
        spiConfigSend(fx3, DEVICE_DVS, REGISTER_DIGITAL_TIMESTAMP_RESET, 0x01);
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_DIGITAL_TIMESTAMP_RESET, 0x00);
        spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_CONTROL_MODE, U8T(2));
    }
    
    /** SPI IMU_RUN from Hardware Configuration / Shift-I. Firmware 10+ is 8-byte. */
    void applyImuRunFromConfig() {
        final DVXplorerConfig cfg = getDvxConfig();
        if (cfg != null) {
            try {
                cfg.applyImuRun();
                return;
            } catch (HardwareInterfaceException e) {
                DVXplorer.log.warning("IMU_RUN: " + e);
                return;
            }
        }
        spiConfigSendAndCheck(fx3, DVX_IMU, DVX_IMU_RUN_ACCELEROMETER, 1);
        spiConfigSendAndCheck(fx3, DVX_IMU, DVX_IMU_RUN_GYROSCOPE, 1);
        spiConfigSendAndCheck(fx3, DVX_IMU, DVX_IMU_RUN_TEMPERATURE, 1);
    }

    /**
     * Refer to libcaer dvxplorer.c dvXplorerDataStop() and dvXplorerClose() for details.
     */
    public void dvxDataStop() {
        fx3 = (DVXplorerFX3HardwareInterface) this.getHardwareInterface();

        if (isNextGenFirmware()) {
            spiConfigSendAndCheck(fx3, DVX_DVS, DVX_DVS_RUN, 0);
            spiConfigSendAndCheck(fx3, DVX_IMU, DVX_IMU_RUN_ACCELEROMETER, 0);
            spiConfigSendAndCheck(fx3, DVX_IMU, DVX_IMU_RUN_GYROSCOPE, 0);
            spiConfigSendAndCheck(fx3, DVX_IMU, DVX_IMU_RUN_TEMPERATURE, 0);
            return;
        }
        
        // Disable streaming from DVS chip.
		spiConfigSendAndCheck(fx3, DEVICE_DVS, REGISTER_CONTROL_MODE, U8T(0));
        
        // Disable data transfer on USB end-point 2. Reverse order of enabling.
        spiConfigSendAndCheck(fx3, DVX_DVS, DVX_DVS_RUN, 0);
        spiConfigSendAndCheck(fx3, DVX_IMU, DVX_IMU_RUN_ACCELEROMETER, 0);
        spiConfigSendAndCheck(fx3, DVX_IMU, DVX_IMU_RUN_GYROSCOPE, 0);
        spiConfigSendAndCheck(fx3, DVX_IMU, DVX_IMU_RUN_TEMPERATURE, 0);
        spiConfigSendAndCheck(fx3, DVX_EXTINPUT, DVX_EXTINPUT_RUN_DETECTOR, 0);
        
        if (!isMipiCX3Device()) {
            spiConfigSendAndCheck(fx3, DVX_MUX, DVX_MUX_RUN, 0);
            spiConfigSendAndCheck(fx3, DVX_MUX, DVX_MUX_TIMESTAMP_RUN, 0);
            spiConfigSendAndCheck(fx3, DVX_USB, DVX_USB_RUN, 0);
        }
        
        if (!isMipiCX3Device()) {
            spiConfigSendAndCheck(fx3, DVX_MUX, DVX_MUX_RUN_CHIP, 0); // Put DVS in reset.
        }
    }
    
    public void dvxConfigSet(DVXplorerFX3HardwareInterface fx3, final short moduleAddr, final short paramAddr, int param) {
        switch (moduleAddr) {  
            case DVX_DVS_CHIP:
                switch (paramAddr) {
                    case DVX_DVS_CHIP_AREA_BLOCKING_0:
                    case DVX_DVS_CHIP_AREA_BLOCKING_1:
                    case DVX_DVS_CHIP_AREA_BLOCKING_2:
                    case DVX_DVS_CHIP_AREA_BLOCKING_3:
                    case DVX_DVS_CHIP_AREA_BLOCKING_4:
                    case DVX_DVS_CHIP_AREA_BLOCKING_5:
                    case DVX_DVS_CHIP_AREA_BLOCKING_6:
                    case DVX_DVS_CHIP_AREA_BLOCKING_7:
                    case DVX_DVS_CHIP_AREA_BLOCKING_8:
                    case DVX_DVS_CHIP_AREA_BLOCKING_9:
                    case DVX_DVS_CHIP_AREA_BLOCKING_10:
                    case DVX_DVS_CHIP_AREA_BLOCKING_11:
                    case DVX_DVS_CHIP_AREA_BLOCKING_12:
                    case DVX_DVS_CHIP_AREA_BLOCKING_13:
                    case DVX_DVS_CHIP_AREA_BLOCKING_14:
                    case DVX_DVS_CHIP_AREA_BLOCKING_15:
                    case DVX_DVS_CHIP_AREA_BLOCKING_16:
                    case DVX_DVS_CHIP_AREA_BLOCKING_17:
                    case DVX_DVS_CHIP_AREA_BLOCKING_18:
                    case DVX_DVS_CHIP_AREA_BLOCKING_19:
                        final int regAddr = REGISTER_DIGITAL_AREA_BLOCK + (int)(2 * (paramAddr - DVX_DVS_CHIP_AREA_BLOCKING_0));
                        spiConfigSendAndCheck(fx3, DEVICE_DVS, U16T(regAddr), U8T(param >>> 8));
                        spiConfigSendAndCheck(fx3, DEVICE_DVS, U16T(regAddr + 1), U8T(param));
                        break;
                    default:
                        DVXplorer.log.severe(String.format("DVXplorer dvxConfigSet unsupported param: moduleAddr = %x, paramAddr = %x", moduleAddr, paramAddr));
                        break;
                }
                break;
            default:
                DVXplorer.log.severe(String.format("DVXplorer dvxConfigSet unsupported module: moduleAddr = %x", moduleAddr));
                break;
        }
    }
    
    public int dvxConfigGet(DVXplorerFX3HardwareInterface fx3, final short moduleAddr, final short paramAddr) {
        switch (moduleAddr) {  
            case DVX_DVS_CHIP:
                switch (paramAddr) {
                    case DVX_DVS_CHIP_AREA_BLOCKING_0:
                    case DVX_DVS_CHIP_AREA_BLOCKING_1:
                    case DVX_DVS_CHIP_AREA_BLOCKING_2:
                    case DVX_DVS_CHIP_AREA_BLOCKING_3:
                    case DVX_DVS_CHIP_AREA_BLOCKING_4:
                    case DVX_DVS_CHIP_AREA_BLOCKING_5:
                    case DVX_DVS_CHIP_AREA_BLOCKING_6:
                    case DVX_DVS_CHIP_AREA_BLOCKING_7:
                    case DVX_DVS_CHIP_AREA_BLOCKING_8:
                    case DVX_DVS_CHIP_AREA_BLOCKING_9:
                    case DVX_DVS_CHIP_AREA_BLOCKING_10:
                    case DVX_DVS_CHIP_AREA_BLOCKING_11:
                    case DVX_DVS_CHIP_AREA_BLOCKING_12:
                    case DVX_DVS_CHIP_AREA_BLOCKING_13:
                    case DVX_DVS_CHIP_AREA_BLOCKING_14:
                    case DVX_DVS_CHIP_AREA_BLOCKING_15:
                    case DVX_DVS_CHIP_AREA_BLOCKING_16:
                    case DVX_DVS_CHIP_AREA_BLOCKING_17:
                    case DVX_DVS_CHIP_AREA_BLOCKING_18:
                    case DVX_DVS_CHIP_AREA_BLOCKING_19:
                        final int regAddr = REGISTER_DIGITAL_AREA_BLOCK + (int)(2 * (paramAddr - DVX_DVS_CHIP_AREA_BLOCKING_0));
                        int currVal = spiConfigReceive(fx3, DEVICE_DVS, U16T(regAddr));
                        int retVal = currVal << 8;
                        currVal = spiConfigReceive(fx3, DEVICE_DVS, U16T(regAddr + 1));
                        retVal |= currVal;
                        return retVal;
                    default:
                        DVXplorer.log.severe(String.format("DVXplorer dvxConfigGet unsupported param: moduleAddr = %x, paramAddr = %x", moduleAddr, paramAddr));
                        return -1;
                }
            default:
                DVXplorer.log.severe(String.format("DVXplorer dvxConfigGet unsupported module: moduleAddr = %x", moduleAddr));
                return -1;
        }
    }
    
    public int spiConfigReceive(DVXplorerFX3HardwareInterface fx3, final short moduleAddr, final short paramAddr) {
        if(fx3==null){
            log.warning("null DVXplorerFX3HardwareInterface");
            return -1;
        }
        try {
            final int ret = fx3.spiConfigReceive(moduleAddr, paramAddr);
            return ret;
        }
        catch (final HardwareInterfaceException e) {
            DVXplorer.log.severe(String.format("DVXplorer spi config receiving failed: moduleAddr = %x, paramAddr = %x", moduleAddr, paramAddr));
            if (UsbTransferSubmit.isUnrecoverableSubmitFailure(e)) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            return -1;
        }
    }
    
    public boolean spiConfigSend(DVXplorerFX3HardwareInterface fx3, final short moduleAddr, final short paramAddr, int param) {
        if(fx3==null){
            log.warning("null DVXplorerFX3HardwareInterface");
            return false;
        }
        try {
            fx3.spiConfigSend(moduleAddr, paramAddr, param);
            return true;
        }
        catch (final HardwareInterfaceException e) {
            DVXplorer.log.severe(String.format(
                    "DVXplorer spi config sending failed: moduleAddr = %x, paramAddr = %x, param = %x: %s",
                    moduleAddr, paramAddr, param, e.getMessage()));
            if (UsbTransferSubmit.isUnrecoverableSubmitFailure(e)) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            return false;
        }
    }
    
    public boolean spiConfigSendAndCheck(DVXplorerFX3HardwareInterface fx3, final short moduleAddr, final short paramAddr, int param) {
        if (fx3 == null) {
            return false;
        }
        if (fx3.classicSpiBlockedBySiblingReaders()) {
            return true;
        }
        spiConfigSend(fx3, moduleAddr, paramAddr, param);
        if (isNextGenFirmware() || fx3.skipSpiReadbackOnClose()) {
            return true;
        }
        final int ret = spiConfigReceive(fx3, moduleAddr, paramAddr);
        if (ret != param) {
            DVXplorer.log.severe(String.format("DVXplorer spi config checking error: moduleAddr = %x, paramAddr = %x, param = %x, ret = %x", moduleAddr, paramAddr, param, ret));
            return false;
        }
        return true;
    }
    
    /**
     * Convert int to unsigned 16 bits number. For parameter address conversion.
     * 
     * @param x input int number
     * @return output short number
     */
    public short U16T(int x) {
        return (short)(x & 0x0000FFFF);
    }
    
    /**
     * Convert int to unsigned 8 bits number. For parameter conversion.
     * 
     * @param x input int number
     * @return output int number (only 8 bits are valid, the other bits are 0)
     */
    public int U8T(int x) {
        return x & 0x00FF;
    }
    
    @Override
    public void onRegistration() {
        super.onRegistration();
        if (getAeViewer() == null) {
            return;
        }

        dvxMenu = new JMenu("DVXplorer");
        toggleIMU = new ToggleIMU();
        dvxMenu.add(new JMenuItem(toggleIMU));
        dvxMenu.add(new JMenuItem(new ToggleEventsAction()));
        dvxMenu.getPopupMenu().setLightWeightPopupEnabled(false);
        getAeViewer().addMenu(dvxMenu);
    }

    /** Overlay / AEDAT-4 playback: last IMU sample (Mini/Micro and FX3). */
    public void setLatestImuSample(IMUSample sample) {
        if (dvxExtractor != null) {
            dvxExtractor.imuSample = sample;
        }
    }

    /** Show IMU overlay (Shift-I). Used when Mini/Micro EP 0x81 IMU is running. */
    public void setImuOverlayEnabled(boolean yes) {
        final DVXplorerConfig cfg = getDvxConfig();
        if (cfg != null) {
            cfg.setDisplayImu(yes);
        }
        if (toggleIMU != null) {
            toggleIMU.putValue(Action.SELECTED_KEY, isImuCaptureEnabled());
        }
    }

    @Override
    public void onDeregistration() {
        if (getAeViewer() == null) {
            return;
        }
        super.onDeregistration(); 
        getAeViewer().removeMenu(dvxMenu);
    }
    
    
    
    /**
     * The event extractor for DVXplorer.
     */
    public class DVXExtractor extends RetinaExtractor {

        // aedat2 format
        final int POLARITY_SHIFT = 11;
        final int POLARITY_MASK = 0x01 << POLARITY_SHIFT;
        final int YSHIFT = 22;
        final int YMASK = 0x01FF << YSHIFT;
        final int XSHIFT = 12;
        final int XMASK = 0x03FF << XSHIFT;
        final int DVS_IMU_MASK = 0x01 << 31;
        final int SPECIAL_EVENT_MASK = 0x01 << 10;
        
        public IMUSample imuSample; // latest IMUSample from sensor
        private static final int IMU_WARNING_INTERVAL = 1000;
        private IMUSample.IncompleteIMUSampleException incompleteIMUSampleException = null;
        private int missedImuSampleCounter = 0;
        private int badImuDataCounter = 0;

        public DVXExtractor(DVXplorer chip) {
            super(chip);
            
            setXmask(XMASK);
            setXshift((byte) XSHIFT);
            setYmask(YMASK);
            setYshift((byte) YSHIFT);
            // Must match AEDAT-2 / live USB packing and extractPacket POLARITY_MASK (bit 11).
            // typemask=1 / typeshift=0 put polarity at bit 0, so AEDAT-4 getAddressFromCell
            // packing made every event look like Off.
            setTypemask(POLARITY_MASK);
            setTypeshift((byte) POLARITY_SHIFT);

            // extractPacket always maps raw Y (sensor / top origin) to jAER bottom-origin
            // and never flips X. getAddressFromCell must be that inverse so AEDAT-4
            // packAddress → extractPacket matches live view. FPGA dvsFlipX/Y bits are
            // not applied in extractPacket (only dual-binning in the USB decoder).
            setFlipx(false);
            setFlipy(true);
            setFliptype(false);
        }

        /**
         * Inverse of {@link #extractPacket}: display (x, y, On=1) → USB / AEDAT-2 raw.
         * Live extraction sets {@code e.y = (sizeY-1) - rawY}; packing must undo that
         * or jAER-written AEDAT-4 plays back upside down.
         */
        @Override
        public int getAddressFromCell(int x, int y, int type) {
            final int rawY = (sizeY - 1) - y;
            return ((x << XSHIFT) & XMASK)
                    | ((rawY << YSHIFT) & YMASK)
                    | ((type << POLARITY_SHIFT) & POLARITY_MASK);
        }

        /**
         * Extracts the meaning of the raw events.
         *
         * @param in the raw events, can be null
         * @return out the processed events. these are partially processed
         * in-place. empty packet is returned if null is supplied as in. This
         * event packet is reused and should only be used by a single thread of
         * execution or for a single input stream, or mysterious results may
         * occur!
         */
        @Override
        synchronized public EventPacket extractPacket(AEPacketRaw in) {
            if (out == null) {
                out = new EventPacket<PolarityEvent>(getChip().getEventClass());
            } else {
                out.clear();
            }
            extractPacket(in, out);
            return out;
        }

        private PacketBundle reusedBundle;
        private ImuPacket bundleImu;
        /** Reused polarity slice for {@link #extractBundle}; do not {@code new} each packet. */
        private EventPacket polarityOut;

        /**
         * Demux IMU into {@link ImuPacket} so AEDAT-4 recording and typed
         * filters ({@code processImu}, e.g. Steadicam) see gyro samples.
         * A mixed polarity EventPacket skips IMU on the AEDAT-4 writer.
         * Live USB with {@code hardware/DVXplorerFX3/usbTypedDemux} skips this
         * path (reader already cooked the bundle); this remains the kill-switch
         * and file-playback extract.
         */
        @Override
        synchronized public PacketBundle extractBundle(AEPacketRaw in) {
            if (reusedBundle == null) {
                reusedBundle = new PacketBundle();
            } else {
                reusedBundle.clear();
            }
            final boolean sub = isSubsamplingEnabled();
            setSubsamplingEnabled(false);
            EventPacket cooked;
            try {
                cooked = extractPacket(in);
            } finally {
                setSubsamplingEnabled(sub);
            }
            if (cooked == null) {
                reusedBundle.setRawPacket(in);
                return reusedBundle;
            }
            cooked.setRawPacket(in);
            if (polarityOut == null) {
                polarityOut = new EventPacket<>(PolarityEvent.class);
            } else {
                polarityOut.clear();
            }
            polarityOut.allocate(cooked.getSize());
            if (bundleImu == null) {
                bundleImu = new ImuPacket();
            } else {
                bundleImu.clear();
            }
            boolean hwImu = false;
            if (getChip().getHardwareInterface() instanceof DVXplorerFX3HardwareInterface dvx
                    && dvx.isNextGenFirmware()) {
                hwImu = dvx.drainCx3Imu(bundleImu) > 0;
                if (hwImu && !bundleImu.isEmpty()) {
                    imuSample = bundleImu.get(bundleImu.getSize() - 1);
                }
            }
            final OutputEventIterator polItr = polarityOut.outputIterator();
            for (Object o : cooked) {
                if (o instanceof ApsDvsEvent aps && aps.isImuSample() && aps.getImuSample() != null) {
                    if (!hwImu) {
                        bundleImu.appendCopy(aps.getImuSample());
                    }
                } else if (o instanceof PolarityEvent pe) {
                    final PolarityEvent e = (PolarityEvent) polItr.nextOutput();
                    e.copyFrom(pe);
                }
            }
            if (!bundleImu.isEmpty()) {
                reusedBundle.add(bundleImu);
                bundleImu = new ImuPacket();
            }
            if (!polarityOut.isEmpty()) {
                reusedBundle.add(polarityOut);
            } else if (reusedBundle.isEmpty()) {
                reusedBundle.addAllowEmpty(cooked);
            }
            reusedBundle.setRawPacket(in);
            return reusedBundle;
        }
        
        /**
         * Extracts the meaning of the raw events. This form is used to supply
         * an output packet. This method is used for real time event filtering
         * using a buffer of output events local to data acquisition. An
         * AEPacketRaw may contain multiple events, not all of them have to sent
         * out as EventPackets. An AEPacketRaw is a set(!) of addresses and
         * corresponding timing moments.
         *
         * A first filter (independent from the other ones) is implemented by
         * subSamplingEnabled and getSubsampleThresholdEventCount. The latter
         * may limit the amount of samples in one package to say 50,000. If
         * there are 160,000 events and there is a sub sample threshold of
         * 50,000, a "skip parameter" set to 3. Every so now and then the
         * routine skips with 4, so we end up with 50,000. It's an
         * approximation, the amount of events may be less than 50,000. The
         * events are extracted uniform from the input.
         *
         * @param in the raw events, can be null
         * @param out the processed events. these are partially processed
         * in-place. empty packet is returned if null is supplied as input.
         */
        @Override
        synchronized public void extractPacket(AEPacketRaw in, EventPacket out) {
            if (in == null) {
                return;
            }
            
            int n = in.getNumEvents(); //addresses.length;
            out.systemModificationTimeNs = in.systemModificationTimeNs;

            int skipBy = 1;
            if (isSubSamplingEnabled()) {
                while ((n / skipBy) > getSubsampleThresholdEventCount()) {
                    skipBy++;
                }
            }
            out.allocate((n + skipBy - 1) / Math.max(1, skipBy));
            
            int sym = (sizeY - 1);
            int[] a = in.getAddresses();
            int[] timestamps = in.getTimestamps();
            OutputEventIterator outItr = out.outputIterator();
            for (int i = 0; i < n; i += skipBy) {
                int addr = a[i];
                
                // aedat2 format specific
                if ((incompleteIMUSampleException != null)
                        || ((addr & DavisChip.ADDRESS_TYPE_IMU) == DavisChip.ADDRESS_TYPE_IMU)) {
                    if (IMUSample.extractSampleTypeCode(addr) == 0) { // / only start getting an IMUSample at code 0,
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
                        } catch (final IMUSample.IncompleteIMUSampleException ex) {
                            incompleteIMUSampleException = ex;
                            if ((missedImuSampleCounter++ % IMU_WARNING_INTERVAL) == 0) {
                                Chip.log.warning(
                                    String.format("%s (obtained %d partial samples so far)", ex.toString(), missedImuSampleCounter));
                            }
                            break; // break out of loop because this packet only contained part of an IMUSample and
                            // formed the end of the packet anyhow. Next time we come back here we will complete
                            // the IMUSample
                        } catch (final IMUSample.BadIMUDataException ex2) {
                            if ((badImuDataCounter++ % IMU_WARNING_INTERVAL) == 0) {
                                Chip.log.warning(String.format("%s (%d bad samples so far)", ex2.toString(), badImuDataCounter));
                            }
                            incompleteIMUSampleException = null;
                            continue; // continue because there may be other data
                        }
                    }
                }
                else if ((addr & (DVS_IMU_MASK | SPECIAL_EVENT_MASK)) == 0) {
                    PolarityEvent e = (PolarityEvent) outItr.nextOutput();
                    e.address = addr;
                    e.timestamp = (timestamps[i]);
                    e.setSpecial(false);
                    e.polarity = ((addr & POLARITY_MASK) == 0)? PolarityEvent.Polarity.Off : PolarityEvent.Polarity.On;
                    e.x = (short) (((addr & XMASK) >>> XSHIFT));
                    e.y = (short) (sym - ((addr & YMASK) >>> YSHIFT));
                }
            }
        }
    }
    
    abstract public class DVXMenuAction extends AbstractAction {

        protected final String path = "/eu/seebetter/ini/chips/davis/icons/";

        public DVXMenuAction() {
            super();
        }

        public DVXMenuAction(String name, String tooltip, String icon) {
            putValue(Action.NAME, name);
            URL url = getClass().getResource(path + icon + ".gif");
            if (url != null) {
                putValue(Action.SMALL_ICON, new javax.swing.ImageIcon(url));
            }
            putValue("hideActionText", "true");
            putValue(Action.SHORT_DESCRIPTION, tooltip);
        }
    }
    
    final public class ToggleIMU extends DVXMenuAction {

        public ToggleIMU() {
            super("Toggle IMU",
                "<html>Toggles IMU (inertial measurement unit) capture and display<p>See <i>IMU Config</i> tab in HW configuration panel for more control",
                "ToggleIMU");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_I, java.awt.event.InputEvent.SHIFT_MASK));
            putValue(Action.SELECTED_KEY, isImuCaptureEnabled());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            final DVXplorerConfig cfg = getDvxConfig();
            final boolean next = !isImuCaptureEnabled();
            if (cfg != null) {
                cfg.setImuEnabled(next);
                cfg.setDisplayImu(next);
            }
            dvxDisplayMethod.showActionText("IMU enabled = " + isImuCaptureEnabled());
            putValue(Action.SELECTED_KEY, isImuCaptureEnabled());
        }
    }
    
    final public class ToggleEventsAction extends DVXMenuAction {
        
        public boolean isEventsEnabled = getPrefs().getBoolean("isEventsEnabled", true);
        
        public ToggleEventsAction() {
            super("ToggleEvents", "Toggle DAVIS event capture and display", "ToggleEvents");
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_E, java.awt.event.InputEvent.SHIFT_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            setCaptureEvents(!isEventsEnabled);
            setDisplayEvents(!isEventsEnabled);
            log.info(String.format("capturing and displaying events = %b", isEventsEnabled));
            dvxDisplayMethod.showActionText(String.format("events = %b", isEventsEnabled));
            putValue(Action.SELECTED_KEY, true);
            isEventsEnabled = !isEventsEnabled;
        }
        
        public void setCaptureEvents(boolean yes) {
            getPrefs().putBoolean("isEventsEnabled", yes);
            int param = yes? 1: 0;
            spiConfigSendAndCheck(fx3, DVX_DVS, DVX_DVS_RUN, param);
        }
        
        public void setDisplayEvents(boolean yes) {
            if (aeViewer != null) {
                aeViewer.interruptViewloop();
            }
            if (yes != isEventsEnabled) {
                setChanged();
                notifyObservers(); // inform ParameterControlPanel
            }
        }
    }
    
    public class DVXDisplayMethod extends ChipRendererDisplayMethodRGBA {
        
        public DVXDisplayMethod(final DVXplorer chip) {
            super(chip.getCanvas());
            getCanvas().setBorderSpacePixels(getPrefs().getInt("borderSpacePixels", 10));
        }
        
        @Override
        public void display(final GLAutoDrawable drawable) {
            super.display(drawable);

            // Draw last IMU output
            if (isDisplayImu()) {
                IMUSample overlay = dvxExtractor.imuSample;
                final PacketBundle last = chip.getLastBundle();
                if (last != null) {
                    final ImuPacket imuPkt = last.getFirstImuPacket();
                    if (imuPkt != null && imuPkt.getSize() > 0) {
                        overlay = imuPkt.get(imuPkt.getSize() - 1);
                        dvxExtractor.imuSample = overlay;
                    }
                }
                if (overlay != null) {
                    imuRender(drawable, overlay);
                }
            }
        }

        GLUquadric accelCircle = null;
        private TextRenderer imuTextRenderer = null;

        private void imuRender(final GLAutoDrawable drawable, final IMUSample imuSampleRender) {
            // System.out.println("on rendering: "+imuSample.toString());
            final GL2 gl = drawable.getGL().getGL2();
            if (imuTextRenderer == null) {
                imuTextRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 36)); // recreate, memory hog if instance variable
            }
            gl.glPushMatrix();

            gl.glTranslatef(chip.getSizeX() / 2, chip.getSizeY() / 2, 0);
            gl.glLineWidth(3);

            final float vectorScale = 1f;
            final float textScale = TextRendererScale.draw3dScale(imuTextRenderer,
                    "XXX.XXf,%XXX.XXf dps", getChipCanvas().getScale(), getSizeX(), .3f);
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
            imuTextRenderer.draw3D(String.format("%.2f,%.2f g", imuSampleRender.getAccelX(), imuSampleRender.getAccelY()), x, y, 0, textScale);
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
            final float gyroVectorScale = 5;
            x = ((gyroVectorScale * vectorScale * imuSampleRender.getGyroYawY() * getMinSize()) / 2) / IMUSample.getFullScaleGyroDegPerSec();
            y = ((gyroVectorScale * vectorScale * imuSampleRender.getGyroTiltX() * getMinSize()) / 2) / IMUSample.getFullScaleGyroDegPerSec();
            gl.glVertex2f(x, y);
            gl.glEnd();

            imuTextRenderer.begin3DRendering();
            imuTextRenderer.setColor(1f, 0, 1, trans);
            imuTextRenderer.draw3D(String.format("%.2f,%.2f dps", imuSampleRender.getGyroYawY(), imuSampleRender.getGyroTiltX()), x, y + 5,
                    0, textScale); // x,y,z, scale factor
            imuTextRenderer.end3DRendering();

            // gyro roll
            x = ((gyroVectorScale * vectorScale * imuSampleRender.getGyroRollZ() * getMinSize()) / 2) / IMUSample.getFullScaleGyroDegPerSec();
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
            final String ratestr = String.format("IMU: last dt=%6.1fms temperature=%5.1fC",
                    imuSampleRender.getDeltaTimeUs() * .001f,
                    imuSampleRender.getTemperature());
            final Rectangle2D raterect = imuTextRenderer.getBounds(ratestr);
            imuTextRenderer.draw3D(ratestr, -(float) raterect.getWidth() * textScale * 0.5f * 1f, -20, 0, textScale * 1f);
            imuTextRenderer.end3DRendering();

            gl.glPopMatrix();
        }
    }
    
    private class DVXRenderer extends DavisRenderer {

        public DVXRenderer(AEChip chip) {
            super(chip);
        }

        @Override
        public boolean isDisplayEvents() {
            return true;
        }

        @Override
        public boolean isDisplayFrames() {
            return false;
        }
    }
}
