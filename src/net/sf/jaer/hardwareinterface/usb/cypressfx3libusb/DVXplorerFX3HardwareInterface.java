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
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import org.usb4java.BufferUtils;
import org.usb4java.ConfigDescriptor;
import org.usb4java.Device;
import org.usb4java.DeviceDescriptor;
import org.usb4java.EndpointDescriptor;
import org.usb4java.Interface;
import org.usb4java.InterfaceDescriptor;
import org.usb4java.LibUsb;
import org.usb4java.Transfer;
import org.usb4java.TransferCallback;
import ch.unizh.ini.jaer.chip.retina.DVXplorer;
import ch.unizh.ini.jaer.chip.retina.DVXplorerConfig;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import net.sf.jaer.JaerConstants;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.hardwareinterface.usb.UsbLog;
import net.sf.jaer.hardwareinterface.usb.UsbPolarityBundleBuilder;
import net.sf.jaer.event.ImuPacket;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Adds functionality of DVXplorer / DVXplorer Mini/Micro sensors to CypressFX3.
 * Mini/Micro share VID/PID {@code 152a:8419} with DVXplorer; they are CX3 MIPI
 * ({@code bcdDevice} high byte {@link #DEVICE_TYPE_CX3_MIPI}). Firmware 10+ uses
 * 8-byte SPI like dv-processing {@code DVXplorerM}; USB events are 32-bit MIPI words.
 * Live USB demux (pref {@code hardware/DVXplorerFX3/usbTypedDemux}, default true)
 * writes cooked {@link PacketBundle} polarity in the reader and skips
 * {@link AEPacketRaw} → {@code extractBundle}.
 *
 * @author Pei Haoxiang
 * @see <a href="https://gitlab.com/inivation/dv/dv-processing/-/blob/master/include/dv-processing/io/camera/dvxplorer_m.hpp">dvxplorer_m.hpp</a>
 */
public class DVXplorerFX3HardwareInterface extends CypressFX3 implements BiasgenHardwareInterface {
    
    /** The USB product ID of this device (DVXplorer and DVXplorer Mini/Micro). */
    static public final short PID_FX3 = (short) 0x8419;
    static public final int REQUIRED_FIRMWARE_VERSION_FX3 = 8;
    static public final int REQUIRED_LOGIC_REVISION_FX3 = 18;
    /** USB {@code bcdDevice} high byte for Cypress CX3 MIPI (Mini/Micro). */
    static public final int DEVICE_TYPE_CX3_MIPI = 4;
    /** dv-processing 2.0 {@code DVXplorerM} required firmware. */
    static public final int REQUIRED_FIRMWARE_VERSION_CX3 = 10;
    /** dv-processing USBDeviceNextGen defaults (not the DAVIS 128 KiB CypressFX3 prefs). */
    static public final int CX3_USB_FIFO_SIZE = 8192;
    /** dv-processing {@code mDataTransfersNumberNLCK} default. */
    static public final int CX3_USB_NUM_BUFFERS = 32;
    static public final byte CX3_DEBUG_ENDPOINT = (byte) 0x81;
    /** One URB; firmware returns the latest BMI160 sample whenever the host asks. */
    static public final int CX3_IMU_TRANSFER_COUNT = 1;
    static public final int CX3_IMU_TRANSFER_SIZE = 64;
    /** BMI160 gyro/accel ODR 800 Hz; resubmit EP 0x81 at this period. */
    static public final long CX3_IMU_PERIOD_NS = 1_250_000L;
    static public final byte VR_DATA_CLEANUP = (byte) 0xC6;
    /** Extra Mini/Micro USB/MIPI/IMU logs: {@code -Djaer.dvx.debug=true}. */
    public static final boolean debug = Boolean.getBoolean("jaer.dvx.debug");
    private static final Preferences PREFS = JaerConstants.PREFS_ROOT_HARDWARE.node("DVXplorerFX3");
    /** Pref kill-switch for USB→PacketBundle polarity demux. Default true. */
    public static final String PREF_USB_TYPED_DEMUX = "usbTypedDemux";
    private final UsbPolarityBundleBuilder polarityBuilder = new UsbPolarityBundleBuilder();
    private ImuPacket liveImuDrain;

    protected DVXplorerFX3HardwareInterface(final Device device) {
        super(device);
        usbTypedDemuxActive = PREFS.getBoolean(PREF_USB_TYPED_DEMUX, true);
        CypressFX3.log.fine("DVXplorerFX3 USB typed demux=" + usbTypedDemuxActive
                + " (pref " + PREF_USB_TYPED_DEMUX + ")");
    }
    
    /** USB device type from {@code bcdDevice} high byte (FX3=1–3, CX3 MIPI=4). */
    public int getUsbDeviceType() {
        return (getDID() >> 8) & 0xFF;
    }

    /** Firmware version from {@code bcdDevice} low byte. */
    public int getFirmwareVersion() {
        return getDID() & 0xFF;
    }

    /** True for DVXplorer Mini/Micro (CX3 MIPI), same VID/PID as FX3 DVXplorer. */
    public boolean isMipiCX3Device() {
        return getUsbDeviceType() == DEVICE_TYPE_CX3_MIPI;
    }

    /** Firmware 10+ Mini/Micro: 8-byte SPI and high-level {@code MODULE_DVS} params. */
    public boolean isNextGenFirmware() {
        return isMipiCX3Device() && getFirmwareVersion() >= REQUIRED_FIRMWARE_VERSION_CX3;
    }

    @Override
    protected boolean shouldResetUsbDevice() {
        if (deviceDescriptor == null && device != null) {
            deviceDescriptor = new DeviceDescriptor();
            LibUsb.getDeviceDescriptor(device, deviceDescriptor);
        }
        return !isMipiCX3Device();
    }

    private int usbNoteCount;
    private int usbCompleteCount;
    private int usbErrorCount;
    private long usbBytes;
    private long lastUsbNoteMs;

    void noteUsbTransfer(final int status, final int actualLength) {
        usbNoteCount++;
        if (status == LibUsb.TRANSFER_COMPLETED) {
            usbCompleteCount++;
            usbBytes += actualLength;
        } else {
            usbErrorCount++;
        }
        if (status != LibUsb.TRANSFER_COMPLETED) {
            CypressFX3.log.warning(String.format(
                    "Mini/Micro USB: n=%d complete=%d err=%d lastStatus=%s lastBytes=%d",
                    usbNoteCount, usbCompleteCount, usbErrorCount, LibUsb.errorName(status), actualLength));
            return;
        }
        if (debug) {
            final long now = System.currentTimeMillis();
            if (usbNoteCount <= 8 || (now - lastUsbNoteMs) >= 2000) {
                lastUsbNoteMs = now;
                CypressFX3.log.info(String.format(
                        "Mini/Micro USB: n=%d complete=%d err=%d lastBytes=%d totalBytes=%d fifo=%d x %d",
                        usbNoteCount, usbCompleteCount, usbErrorCount, actualLength, usbBytes,
                        getFifoSize(), getNumBuffers()));
            }
        }
    }

    private void logUsbLayout() {
        if (!debug) {
            return;
        }
        try {
            final int speed = LibUsb.getDeviceSpeed(device);
            CypressFX3.log.info("Mini/Micro USB speed=" + speedName(speed));
        } catch (IllegalStateException e) {
            CypressFX3.log.info("USB speed unavailable: " + e.getMessage());
        }
        final ConfigDescriptor config = new ConfigDescriptor();
        final int status = LibUsb.getActiveConfigDescriptor(device, config);
        if (status != LibUsb.SUCCESS) {
            CypressFX3.log.warning("Could not read USB config descriptor: " + LibUsb.errorName(status));
            return;
        }
        try {
            final StringBuilder sb = new StringBuilder("Mini/Micro USB config:");
            for (final Interface iface : config.iface()) {
                for (final InterfaceDescriptor alt : iface.altsetting()) {
                    sb.append(String.format(" iface=%d alt=%d eps=%d",
                            alt.bInterfaceNumber() & 0xFF, alt.bAlternateSetting() & 0xFF, alt.bNumEndpoints() & 0xFF));
                    for (final EndpointDescriptor ep : alt.endpoint()) {
                        sb.append(String.format(" [ep=0x%02x attr=0x%02x max=%d]",
                                ep.bEndpointAddress() & 0xFF, ep.bmAttributes() & 0xFF, ep.wMaxPacketSize() & 0xFFFF));
                    }
                }
            }
            CypressFX3.log.info(sb.toString());
        } finally {
            LibUsb.freeConfigDescriptor(config);
        }
    }

    private static String speedName(final int speed) {
        if (speed == LibUsb.SPEED_LOW) {
            return "LOW";
        }
        if (speed == LibUsb.SPEED_FULL) {
            return "FULL";
        }
        if (speed == LibUsb.SPEED_HIGH) {
            return "HIGH";
        }
        if (speed == LibUsb.SPEED_SUPER) {
            return "SUPER";
        }
        return String.valueOf(speed);
    }

    private void cleanupCx3DataBuffers() {
        // Skip VR 0xC6: on Windows it can block forever in native controlTransfer
        // even with VENDOR_REQUEST_TIMEOUT_MS=500 (jAER-0.log 12:32:06–17).
        log.fine("cleanupCx3DataBuffers: skipping VR_DATA_CLEANUP 0xC6 " + UsbLog.t());
        usbControlResetDataEndpoint();
    }

    private final List<Transfer> cx3ImuTransfers = new ArrayList<>();
    private volatile boolean cx3ImuRunning;
    private int cx3ImuPackets;
    private int cx3ImuWritten;
    private long cx3ImuLastSubmitNs;
    private ScheduledExecutorService cx3ImuScheduler;
    private ScheduledFuture<?> cx3ImuResubmitTask;
    private final ConcurrentLinkedQueue<IMUSample> cx3ImuQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger cx3ImuQueued = new AtomicInteger();
    private static final int CX3_IMU_QUEUE_MAX = 2000;

    /**
     * Mini/Micro IMU is on interrupt EP {@code 0x81} (dv-processing debug
     * endpoint). Transfers are completed by the bulk AEReader event loop.
     */
    private void startCx3ImuTransfers() {
        if (!isNextGenFirmware() || cx3ImuRunning) {
            return;
        }
        stopCx3ImuTransfers();
        cx3ImuRunning = true;
        cx3ImuPackets = 0;
        cx3ImuWritten = 0;
        cx3ImuLastSubmitNs = 0;
        final TransferCallback callback = this::onCx3ImuTransfer;
        for (int i = 0; i < CX3_IMU_TRANSFER_COUNT; i++) {
            final Transfer transfer = LibUsb.allocTransfer();
            if (transfer == null) {
                CypressFX3.log.warning("Mini/Micro IMU: allocTransfer failed");
                break;
            }
            final ByteBuffer buffer = BufferUtils.allocateByteBuffer(CX3_IMU_TRANSFER_SIZE);
            LibUsb.fillInterruptTransfer(transfer, deviceHandle, CX3_DEBUG_ENDPOINT, buffer, callback, null, 0);
            final int status = LibUsb.submitTransfer(transfer);
            if (status != LibUsb.SUCCESS) {
                CypressFX3.log.warning("Mini/Micro IMU submit EP 0x81: " + LibUsb.errorName(status));
                LibUsb.freeTransfer(transfer);
                continue;
            }
            cx3ImuLastSubmitNs = System.nanoTime();
            cx3ImuTransfers.add(transfer);
        }
        if (debug) {
            CypressFX3.log.info(String.format(
                    "Mini/Micro IMU: interrupt EP 0x81 %d x %d bytes",
                    cx3ImuTransfers.size(), CX3_IMU_TRANSFER_SIZE));
        }
    }

    /**
     * Mini/Micro firmware 10+: start or stop interrupt EP {@code 0x81} IMU
     * capture. SPI {@code IMU_RUN_*} is sent separately (8-byte, like {@code DVS_RUN}).
     */
    public void setCx3ImuCaptureEnabled(final boolean yes) {
        if (!isNextGenFirmware()) {
            return;
        }
        if (yes) {
            startCx3ImuTransfers();
        } else {
            stopCx3ImuTransfers();
        }
    }

    public boolean isCx3ImuCaptureEnabled() {
        return cx3ImuRunning;
    }

    private void onCx3ImuTransfer(final Transfer transfer) {
        if (transfer.status() == LibUsb.TRANSFER_COMPLETED && transfer.actualLength() > 0
                && getAeReader() instanceof RetinaAEReader reader) {
            reader.writeCx3ImuSample(transfer.buffer(), transfer.actualLength());
        } else if (transfer.status() != LibUsb.TRANSFER_COMPLETED && transfer.status() != LibUsb.TRANSFER_CANCELLED) {
            CypressFX3.log.warning("Mini/Micro IMU EP 0x81: " + LibUsb.errorName(transfer.status()));
        }
        if (cx3ImuRunning && transfer.status() == LibUsb.TRANSFER_COMPLETED) {
            scheduleCx3ImuResubmit(transfer);
        }
    }

    /**
     * Do not resubmit on the USB event thread immediately: the CX3 debug EP
     * completes as fast as the host asks. Wait ~800 Hz so DVS bulk is not starved.
     */
    private void scheduleCx3ImuResubmit(final Transfer transfer) {
        final long now = System.nanoTime();
        final long waitNs = CX3_IMU_PERIOD_NS - (now - cx3ImuLastSubmitNs);
        if (waitNs <= 0) {
            submitCx3ImuNow(transfer);
            return;
        }
        if (cx3ImuScheduler == null || cx3ImuScheduler.isShutdown()) {
            cx3ImuScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                final Thread t = new Thread(r, "DVXplorer-CX3-IMU");
                t.setDaemon(true);
                return t;
            });
        }
        // Windows timers are ms-resolution; NANOSECONDS can round to a 0 delay.
        final long waitMs = Math.max(1L, waitNs / 1_000_000L);
        cx3ImuResubmitTask = cx3ImuScheduler.schedule(() -> submitCx3ImuNow(transfer), waitMs, TimeUnit.MILLISECONDS);
    }

    private void submitCx3ImuNow(final Transfer transfer) {
        if (!cx3ImuRunning) {
            return;
        }
        cx3ImuLastSubmitNs = System.nanoTime();
        final int status = LibUsb.submitTransfer(transfer);
        if (status != LibUsb.SUCCESS) {
            CypressFX3.log.warning("Mini/Micro IMU resubmit: " + LibUsb.errorName(status));
        }
    }

    /**
     * Live Mini/Micro (and classic FX3 when typed demux is on) IMU samples.
     * Called from {@link #acquireAvailablePacketBundle()} on the live path and
     * from {@code extractBundle} when the kill-switch restores raw extract.
     */
    public int drainCx3Imu(final ImuPacket dest) {
        if (dest == null) {
            return 0;
        }
        int n = 0;
        IMUSample s;
        while ((s = cx3ImuQueue.poll()) != null) {
            cx3ImuQueued.decrementAndGet();
            dest.appendCopy(s);
            if (++n >= CX3_IMU_QUEUE_MAX) {
                break;
            }
        }
        return n;
    }

    /**
     * jAER 3: USB demux already filled polarity; attach Mini/classic IMU that
     * arrived on a side channel (not in the DVS bulk stream).
     */
    @Override
    public PacketBundle acquireAvailablePacketBundle() throws HardwareInterfaceException {
        final PacketBundle bundle = super.acquireAvailablePacketBundle();
        if (bundle != null) {
            drainImuIntoLiveBundle(bundle);
        }
        return bundle;
    }

    private void drainImuIntoLiveBundle(final PacketBundle bundle) {
        if (liveImuDrain == null) {
            liveImuDrain = new ImuPacket();
        } else {
            liveImuDrain.clear();
        }
        if (drainCx3Imu(liveImuDrain) > 0) {
            bundle.add(liveImuDrain);
            liveImuDrain = new ImuPacket();
        }
    }

    private void offerCx3Imu(final IMUSample sample) {
        while (cx3ImuQueued.get() >= CX3_IMU_QUEUE_MAX) {
            if (cx3ImuQueue.poll() == null) {
                break;
            }
            cx3ImuQueued.decrementAndGet();
        }
        cx3ImuQueue.offer(sample);
        cx3ImuQueued.incrementAndGet();
    }

    private void stopCx3ImuTransfers() {
        cx3ImuRunning = false;
        final ScheduledFuture<?> pending = cx3ImuResubmitTask;
        cx3ImuResubmitTask = null;
        if (pending != null) {
            pending.cancel(false);
        }
        cx3ImuQueue.clear();
        cx3ImuQueued.set(0);
        for (final Transfer transfer : cx3ImuTransfers) {
            final int status = LibUsb.cancelTransfer(transfer);
            if (debug && status != LibUsb.SUCCESS && status != LibUsb.ERROR_NOT_FOUND) {
                CypressFX3.log.info("Mini/Micro IMU cancel: " + LibUsb.errorName(status));
            }
        }
        if (!cx3ImuTransfers.isEmpty()) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            for (final Transfer transfer : cx3ImuTransfers) {
                LibUsb.freeTransfer(transfer);
            }
            cx3ImuTransfers.clear();
        }
    }

    /**
     * After claim, Mini/Micro bulk IN {@code 0x82} (and IMU interrupt {@code 0x81})
     * can be missing until WinUSB SuperSpeed finishes, or they live on another
     * alt-setting. Starting AEReader then throws uncaught {@code LIBUSB_ERROR_NOT_FOUND}.
     */
    private void waitForDataEndpoints() throws HardwareInterfaceException {
        if (!isMipiCX3Device()) {
            return;
        }
        final long deadline = System.currentTimeMillis() + 2000L;
        boolean haveBulk = false;
        boolean haveImu = false;
        int lastAlt = -1;
        int attempt = 0;
        while (true) {
            attempt++;
            final EndpointScan scan = scanDataEndpoints();
            haveBulk = scan.bulk82Alt >= 0;
            haveImu = scan.interrupt81Alt >= 0;
            if (attempt == 1 || !haveBulk || !haveImu) {
                CypressFX3.log.info("Mini/Micro USB endpoints (attempt " + attempt + "): " + scan.summary);
            }
            if (haveBulk) {
                final int iface = (haveImu && scan.bothIface >= 0) ? scan.bothIface : scan.bulk82Iface;
                final int alt = (haveImu && scan.bothAlt >= 0) ? scan.bothAlt : scan.bulk82Alt;
                if (alt != lastAlt) {
                    final int status = LibUsb.setInterfaceAltSetting(deviceHandle, iface, alt);
                    CypressFX3.log.info(String.format(
                            "Mini/Micro setInterfaceAltSetting iface=%d alt=%d for bulk 0x82: %s",
                            iface, alt, LibUsb.errorName(status)));
                    if (status == LibUsb.SUCCESS) {
                        lastAlt = alt;
                    }
                }
                if (lastAlt >= 0 && (haveImu || System.currentTimeMillis() >= deadline)) {
                    if (!haveImu) {
                        CypressFX3.log.warning(
                                "Mini/Micro interrupt EP 0x81 not present; IMU capture skipped until replug");
                    }
                    return;
                }
            }
            if (System.currentTimeMillis() >= deadline) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new HardwareInterfaceException("interrupted waiting for Mini/Micro USB data endpoints");
            }
        }
        if (haveBulk && lastAlt >= 0) {
            CypressFX3.log.warning("Mini/Micro interrupt EP 0x81 not present; IMU capture skipped until replug");
            return;
        }
        throw new HardwareInterfaceException(
                "Mini/Micro bulk IN 0x82 not present after USB claim (AEReader would get LIBUSB_ERROR_NOT_FOUND)");
    }

    private EndpointScan scanDataEndpoints() {
        final EndpointScan scan = new EndpointScan();
        final ConfigDescriptor config = new ConfigDescriptor();
        final int status = LibUsb.getActiveConfigDescriptor(device, config);
        if (status != LibUsb.SUCCESS) {
            scan.summary = "no config descriptor: " + LibUsb.errorName(status);
            return scan;
        }
        try {
            final StringBuilder sb = new StringBuilder();
            for (final Interface iface : config.iface()) {
                for (final InterfaceDescriptor alt : iface.altsetting()) {
                    final int ifaceNum = alt.bInterfaceNumber() & 0xFF;
                    final int altNum = alt.bAlternateSetting() & 0xFF;
                    boolean has82 = false;
                    boolean has81 = false;
                    sb.append(String.format(" iface=%d alt=%d eps=%d",
                            ifaceNum, altNum, alt.bNumEndpoints() & 0xFF));
                    for (final EndpointDescriptor ep : alt.endpoint()) {
                        final int addr = ep.bEndpointAddress() & 0xFF;
                        final int type = ep.bmAttributes() & 0x03;
                        sb.append(String.format(" [ep=0x%02x attr=0x%02x max=%d]",
                                addr, ep.bmAttributes() & 0xFF, ep.wMaxPacketSize() & 0xFFFF));
                        if (addr == (CypressFX3.AE_MONITOR_ENDPOINT_ADDRESS & 0xFF)
                                && type == LibUsb.TRANSFER_TYPE_BULK) {
                            has82 = true;
                            if (scan.bulk82Alt < 0) {
                                scan.bulk82Iface = ifaceNum;
                                scan.bulk82Alt = altNum;
                            }
                        }
                        if (addr == (CX3_DEBUG_ENDPOINT & 0xFF)
                                && type == LibUsb.TRANSFER_TYPE_INTERRUPT) {
                            has81 = true;
                            if (scan.interrupt81Alt < 0) {
                                scan.interrupt81Iface = ifaceNum;
                                scan.interrupt81Alt = altNum;
                            }
                        }
                    }
                    if (has82 && has81 && scan.bothAlt < 0) {
                        scan.bothIface = ifaceNum;
                        scan.bothAlt = altNum;
                    }
                }
            }
            scan.summary = sb.length() == 0 ? "(empty config)" : sb.toString().trim();
            return scan;
        } finally {
            LibUsb.freeConfigDescriptor(config);
        }
    }

    private static final class EndpointScan {
        int bulk82Iface = -1;
        int bulk82Alt = -1;
        int interrupt81Iface = -1;
        int interrupt81Alt = -1;
        int bothIface = -1;
        int bothAlt = -1;
        String summary = "";
    }

    @Override
	synchronized public void open() throws HardwareInterfaceException {
		super.open();
        log.fine("DVXplorerFX3 super.open() returned " + UsbLog.t());
        waitForDataEndpoints();
        final int did = getDID() & 0xFFFF;
        final int type = getUsbDeviceType();
        final boolean cx3 = isMipiCX3Device();
        CypressFX3.log.info(String.format(
                "DVXplorer USB open: bcdDevice=0x%04x (high byte type=%d %s, firmware=%d) %s",
                did, type,
                cx3 ? "CX3 MIPI Mini/Micro" : "FX3 DVXplorer (type 1-3)",
                getFirmwareVersion(), UsbLog.t()));
        // Do not VR_DATA_CLEANUP 0xC6 or dvxConfig here. 0xC6 stuck in native
        // LibUsb.controlTransfer (timeout 500 ms never returned; jAER 12:32:06).
        // Logic config runs on jaer-send-biases after this method returns.
	}

    /**
     * Firmware 10 rejects 4-byte {@code VR_FPGA_CONFIG} ({@code LIBUSB_ERROR_PIPE},
     * jAER 8:58:38). 8-byte is required. {@code DVS_FLATTEN} and SPI IN still hang
     * on WinUSB. Allowed 8-byte OUT: {@code DVS_RUN}, Hardware Configuration DVS
     * params ({@code DVS_EFPS_S5K231Y}, contrast, global hold/reset), {@code IMU_RUN_*},
     * and BMI160 ODR/range/filter (without those, factory gyro ODR 5 freezes ~0 dps).
     */
    public static boolean isNextGenStreamingParam(final short moduleAddr, final short paramAddr) {
        if (moduleAddr == CypressFX3.FPGA_DVS) {
            return paramAddr == DVXplorer.DVX_DVS_RUN
                    || paramAddr == DVXplorer.DVS_GLOBAL_HOLD
                    || paramAddr == DVXplorer.DVS_GLOBAL_RESET
                    || paramAddr == DVXplorer.DVS_EFPS_S5K231Y
                    || paramAddr == DVXplorer.DVS_CONTRAST_THRESHOLD_ON
                    || paramAddr == DVXplorer.DVS_CONTRAST_THRESHOLD_OFF;
        }
        if (moduleAddr == CypressFX3.FPGA_IMU) {
            return paramAddr == DVXplorer.DVX_IMU_RUN_ACCELEROMETER
                    || paramAddr == DVXplorer.DVX_IMU_RUN_GYROSCOPE
                    || paramAddr == DVXplorer.DVX_IMU_RUN_TEMPERATURE
                    || paramAddr == DVXplorer.DVX_IMU_ACCEL_DATA_RATE
                    || paramAddr == DVXplorer.DVX_IMU_ACCEL_FILTER
                    || paramAddr == DVXplorer.DVX_IMU_ACCEL_RANGE
                    || paramAddr == DVXplorer.DVX_IMU_GYRO_DATA_RATE
                    || paramAddr == DVXplorer.DVX_IMU_GYRO_FILTER
                    || paramAddr == DVXplorer.DVX_IMU_GYRO_RANGE;
        }
        return false;
    }

    /** Hardware Configuration DVS writes: log.info after USB confirms (not DVS_RUN). */
    public static boolean isNextGenDvsBiasParam(final short moduleAddr, final short paramAddr) {
        if (moduleAddr != CypressFX3.FPGA_DVS) {
            return false;
        }
        return paramAddr == DVXplorer.DVS_GLOBAL_HOLD
                || paramAddr == DVXplorer.DVS_GLOBAL_RESET
                || paramAddr == DVXplorer.DVS_EFPS_S5K231Y
                || paramAddr == DVXplorer.DVS_CONTRAST_THRESHOLD_ON
                || paramAddr == DVXplorer.DVS_CONTRAST_THRESHOLD_OFF;
    }

    @Override
    public synchronized void spiConfigSend(final short moduleAddr, final short paramAddr, int param)
            throws HardwareInterfaceException {
        if (!isNextGenFirmware()) {
            super.spiConfigSend(moduleAddr, paramAddr, param);
            return;
        }
        if (isNextGenStreamingParam(moduleAddr, paramAddr)) {
            sendNextGenDvsRun(moduleAddr, paramAddr, param);
            return;
        }
        // Firmware 10 CX3: 8-byte SPI OUT hangs in LibUsb.controlTransfer the
        // same way SPI IN does (jAER 7:42:25 open, timeout 8 s on DVS_FLATTEN
        // in sendNextGenDefaultConfig). Leave those device firmware defaults.
        log.info(String.format(
                "skipping SPI OUT on Mini/Micro firmware %d module=0x%x param=0x%x (%s) %s",
                getFirmwareVersion(), moduleAddr, paramAddr, nextGenParamName(moduleAddr, paramAddr), UsbLog.t()));
    }

    /** Big-endian uint64 payload; firmware 10 stalls 4-byte wLength. */
    private void sendNextGenDvsRun(final short moduleAddr, final short paramAddr, int param)
            throws HardwareInterfaceException {
        param = adjustHWParam(moduleAddr, paramAddr, param);
        final byte[] configBytes = new byte[8];
        final long p = param & 0xFFFFFFFFL;
        for (int i = 0; i < 8; i++) {
            configBytes[i] = (byte) ((p >>> ((7 - i) * 8)) & 0xFF);
        }
        log.fine(String.format(
                "Mini/Micro 8-byte SPI OUT firmware %d module=0x%x param=0x%x value=%d %s",
                getFirmwareVersion(), moduleAddr, paramAddr, param, UsbLog.t()));
        try {
            sendVendorRequest(CypressFX3.VR_FPGA_CONFIG, moduleAddr, paramAddr, configBytes);
        } catch (HardwareInterfaceException e) {
            if (deviceHandle != null) {
                LibUsb.clearHalt(deviceHandle, (byte) 0);
            }
            log.warning(String.format(
                    "Mini/Micro 8-byte SPI OUT failed firmware %d module=0x%x param=0x%x (%s) value=%d: %s %s",
                    getFirmwareVersion(), moduleAddr, paramAddr, nextGenParamName(moduleAddr, paramAddr),
                    param, e.getMessage(), UsbLog.t()));
            throw e;
        }
        if (isNextGenDvsBiasParam(moduleAddr, paramAddr)) {
            log.info(String.format(
                    "Mini/Micro SPI OUT confirmed firmware %d VR_FPGA_CONFIG module=%d param=%d (%s) value=%d %s",
                    getFirmwareVersion(), moduleAddr, paramAddr, nextGenParamName(moduleAddr, paramAddr),
                    param, UsbLog.t()));
        }
    }

    static String nextGenParamName(final short moduleAddr, final short paramAddr) {
        if (moduleAddr == CypressFX3.FPGA_DVS) {
            if (paramAddr == DVXplorer.DVX_DVS_RUN) {
                return "DVS_RUN";
            }
            if (paramAddr == DVXplorer.DVS_FLATTEN) {
                return "DVS_FLATTEN";
            }
            if (paramAddr == DVXplorer.DVS_GLOBAL_HOLD) {
                return "DVS_GLOBAL_HOLD";
            }
            if (paramAddr == DVXplorer.DVS_GLOBAL_RESET) {
                return "DVS_GLOBAL_RESET";
            }
            if (paramAddr == DVXplorer.DVS_EFPS_S5K231Y) {
                return "DVS_EFPS_S5K231Y";
            }
            if (paramAddr == DVXplorer.DVS_CONTRAST_THRESHOLD_ON) {
                return "DVS_CONTRAST_THRESHOLD_ON";
            }
            if (paramAddr == DVXplorer.DVS_CONTRAST_THRESHOLD_OFF) {
                return "DVS_CONTRAST_THRESHOLD_OFF";
            }
        }
        if (moduleAddr == CypressFX3.FPGA_IMU) {
            return "IMU param " + paramAddr;
        }
        return "unknown";
    }

    @Override
    public synchronized int spiConfigReceive(final short moduleAddr, final short paramAddr)
            throws HardwareInterfaceException {
        if (!isNextGenFirmware()) {
            return super.spiConfigReceive(moduleAddr, paramAddr);
        }
        // Firmware 10 CX3: 8-byte SPI IN (req 0xBF) never returns from
        // LibUsb.controlTransfer even with a 500 ms timeout (jAER 12:39 Micro).
        // ViewLoop then blocks on the same CypressFX3 monitor in LIVE acquire.
        log.fine(String.format(
                "skipping SPI IN on Mini/Micro firmware %d module=0x%x param=0x%x %s",
                getFirmwareVersion(), moduleAddr, paramAddr, UsbLog.t()));
        return 0;
    }

    @Override
    synchronized public void setPowerDown(final boolean powerDown) throws HardwareInterfaceException {
    }

    @Override
    synchronized public void sendConfiguration(final Biasgen biasgen) throws HardwareInterfaceException {
        if (biasgen instanceof DVXplorerConfig cfg) {
            cfg.applyToHardware();
        }
    }

    @Override
    synchronized public void flashConfiguration(final Biasgen biasgen) throws HardwareInterfaceException {
        JOptionPane.showMessageDialog(null,
                isMipiCX3Device()
                        ? "Flashing biases is not supported on DVXplorer Mini/Micro"
                        : "Flashing biases is not supported on DVXplorer");
    }

    @Override
    public byte[] formatConfigurationBytes(final Biasgen biasgen) {
        return new byte[0];
    }
    
    @Override
    synchronized protected void enableINEndpoint() throws HardwareInterfaceException {
        if (deviceHandle == null) {
			CypressFX3.log.warning("CypressFX3.enableINEndpoint(): null USBIO device");
			return;
		}

        if (getChip() instanceof DVXplorer chip && !chip.isNextGenFirmware()) {
            chip.dvxDataStart();
        }

        inEndpointEnabled = true;
    }
    
    @Override
    synchronized protected void disableINEndpoint() {
        if (getChip() != null) {
            DVXplorer chip = (DVXplorer) getChip();
            chip.dvxDataStop();
            if (chip.isNextGenFirmware()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

		inEndpointEnabled = false;
    }
    
    /**
     * Reset data endpoint of the USB side.
     */
    final public void usbControlResetDataEndpoint() {
        LibUsb.clearHalt(deviceHandle, CypressFX3.AE_MONITOR_ENDPOINT_ADDRESS);
    }

    /**
     * Starts reader buffer pool thread and enables in endpoints for AEs. This
     * method is overridden to construct
     * our own reader with its translateEvents method
     */
    @Override
    public void startAEReader() throws HardwareInterfaceException {
        if (isMipiCX3Device()) {
            setSessionUsbBuffers(CX3_USB_FIFO_SIZE, CX3_USB_NUM_BUFFERS);
            if (debug) {
                CypressFX3.log.info(String.format(
                        "Mini/Micro USB buffers %d x %d bytes",
                        CX3_USB_NUM_BUFFERS, CX3_USB_FIFO_SIZE));
            }
        }
        setAeReader(new RetinaAEReader(this));
        allocateAEBuffers();

        if (!isNextGenFirmware()) {
            usbControlResetDataEndpoint();
        }

        getAeReader().startThread();
        if (isNextGenFirmware() && getChip() instanceof DVXplorer chip) {
            if (chip.isImuCaptureEnabled()) {
                startCx3ImuTransfers();
            }
            // startThread already joined until bulk IN 0x82 URBs queued (or threw).
            // Do not spawn+join here: setEventAcquisitionEnabled holds this
            // monitor, so the worker cannot enter spiConfigSend (jAER 8:58:36
            // 2 s "native USB" wait was that deadlock; 4-byte then PIPE).
            CypressFX3.log.info("Mini/Micro: USB IN queued, sending DVS_RUN / IMU_RUN (8-byte SPI)");
            chip.dvxDataStart();
        }
        HardwareInterfaceException.clearException();
    }

    /**
     * {@code USBTransferThread} never joins while Mini/Micro is filling bulk IN.
     * ViewLoop pause only skips ViewLoop consume; it does not send {@code DVS_RUN=0}.
     */
    @Override
    protected void quiesceStreamingForUsbRestart() {
        if (!(getChip() instanceof DVXplorer chip)) {
            return;
        }
        CypressFX3.log.info("Mini/Micro: DVS_RUN=0 before USB buffer-session replace");
        chip.dvxDataStop();
        if (chip.isNextGenFirmware()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        stopCx3ImuTransfers();
    }

    @Override
    protected void resumeStreamingAfterUsbRestart() {
        if (!(getChip() instanceof DVXplorer chip)) {
            return;
        }
        if (isNextGenFirmware()) {
            if (chip.isImuCaptureEnabled()) {
                startCx3ImuTransfers();
            }
            CypressFX3.log.info("Mini/Micro: USB IN queued after buffer reconfig, sending DVS_RUN / IMU_RUN");
            chip.dvxDataStart();
            return;
        }
        chip.dvxDataStart();
    }

    @Override
    public boolean stopAEReader() {
        stopCx3ImuTransfers();
        return super.stopAEReader();
    }

    /**
     * This reader understands the format of raw USB data and translates to the
     * AEPacketRaw
     */
    public class RetinaAEReader extends CypressFX3.AEReader implements PropertyChangeListener {
        private final int chipID;

        // aedat2 format
        public static final int AEDAT2_Y_ADDR_MASK = 0x000001FF;
        public static final int AEDAT2_X_ADDR_MASK = 0x000003FF;
        public static final int AEDAT2_Y_ADDR_SHIFT = 22;
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
        private final boolean mipiCx3;

        // MIPI CX3 parser (libcaer mipiCx3EventTranslator / S5K231Y)
        private int mipiLastColumn = -1;
        private long mipiReferenceUs = -1;
        private int mipiLastReference = -1;
        private int mipiLastUsedSub = -1;
        private long mipiLastUsedReference = -1;
        private long mipiCurrTimestamp;
        private long mipiLastTimestamp;
        private int mipiReferenceOverflow;
        private long cx3ImuLastTimestamp;
        private long cx3ImuLastHostNs;
        private int cx3ImuDropped;

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

            imuEvents = new short[RetinaAEReader.IMU_DATA_LENGTH];

            int resolvedChipID = 0;
            int resolvedSizeX = 640;
            int resolvedSizeY = 480;
            boolean resolvedInvertXY = false;
            boolean resolvedMipi = false;
            boolean resolvedImuFlipX = false;
            boolean resolvedImuFlipY = false;
            boolean resolvedImuFlipZ = false;

            if (cypress instanceof DVXplorerFX3HardwareInterface dvx && dvx.isMipiCX3Device()) {
                resolvedMipi = true;
                if (debug) {
                    CypressFX3.log.info(String.format(
                            "DVXplorer Mini/Micro USB decoder: CX3 MIPI firmware %d (%s)",
                            dvx.getFirmwareVersion(),
                            dvx.isNextGenFirmware() ? "DVXplorerM 8-byte SPI" : "libcaer 4-byte SPI"));
                }
                if (!dvx.isNextGenFirmware()) {
                    try {
                        final int rx = spiConfigReceive(CypressFX3.FPGA_DVS, (short) 0);
                        final int ry = spiConfigReceive(CypressFX3.FPGA_DVS, (short) 1);
                        if (rx > 0 && ry > 0) {
                            resolvedSizeX = rx;
                            resolvedSizeY = ry;
                        }
                    } catch (HardwareInterfaceException e) {
                        CypressFX3.log.warning("CX3 DVS resolution SPI failed, using 640x480: " + e.getMessage());
                    }
                }
                if (!dvx.isNextGenFirmware()) {
                    try {
                        resolvedInvertXY = (spiConfigReceive(CypressFX3.FPGA_DVS, (short) 2) & 0x04) != 0;
                        final int imuOrientation = spiConfigReceive(CypressFX3.FPGA_IMU, (short) 1);
                        resolvedImuFlipX = (imuOrientation & 0x04) != 0;
                        resolvedImuFlipY = (imuOrientation & 0x02) != 0;
                        resolvedImuFlipZ = (imuOrientation & 0x01) != 0;
                    } catch (HardwareInterfaceException e) {
                        CypressFX3.log.warning("CX3 orientation SPI failed: " + e.getMessage());
                    }
                }
            } else {
                checkFirmwareLogic(REQUIRED_FIRMWARE_VERSION_FX3, REQUIRED_LOGIC_REVISION_FX3);
                resolvedChipID = spiConfigReceive(CypressFX3.FPGA_SYSINFO, (short) 1);
                resolvedSizeX = spiConfigReceive(CypressFX3.FPGA_DVS, (short) 0);
                resolvedSizeY = spiConfigReceive(CypressFX3.FPGA_DVS, (short) 1);
                resolvedInvertXY = (spiConfigReceive(CypressFX3.FPGA_DVS, (short) 2) & 0x04) != 0;
                final int imuOrientation = spiConfigReceive(CypressFX3.FPGA_IMU, (short) 1);
                resolvedImuFlipX = (imuOrientation & 0x04) != 0;
                resolvedImuFlipY = (imuOrientation & 0x02) != 0;
                resolvedImuFlipZ = (imuOrientation & 0x01) != 0;
            }

            chipID = resolvedChipID;
            dvsSizeX = resolvedSizeX;
            dvsSizeY = resolvedSizeY;
            dvsInvertXY = resolvedInvertXY;
            mipiCx3 = resolvedMipi;
            imuFlipX = resolvedImuFlipX;
            imuFlipY = resolvedImuFlipY;
            imuFlipZ = resolvedImuFlipZ;

            updateTimestampMasterStatus();
        }

        private void checkMonotonicTimestamp() {
            if (currentTimestamp <= lastTimestamp) {
                CypressFX3.log.severe(String.format("%s: non strictly-monotonic timestamp detected: lastTimestamp=%d, currentTimestamp=%d, difference=%d.",
                    toString(), lastTimestamp, currentTimestamp, (lastTimestamp - currentTimestamp)));
            }
        }

        private boolean ensureCapacity(final AEPacketRaw buffer, final int capacity) {
            if (usbTypedDemuxActive) {
                if (capacity > getAEBufferSize()) {
                    buffer.overrunOccuredFlag = true;
                    return false;
                }
                return true;
            }
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

        private void beginTypedDemux() {
            if (!usbTypedDemuxActive) {
                return;
            }
            polarityBuilder.ensureCapacity(getAEBufferSize());
            polarityBuilder.attach(packetBundlePool.writeBuffer());
        }

        private void endTypedDemux(final AEPacketRaw buffer) {
            buffer.lastCaptureLength = eventCounter - buffer.lastCaptureIndex;
            if (usbTypedDemuxActive) {
                polarityBuilder.flushAll();
                buffer.setNumEvents(0);
                return;
            }
            buffer.setNumEvents(eventCounter);
        }

        /**
         * Cooked polarity matching {@code DVXExtractor.extractPacket}: display
         * {@code y = (sizeY-1) - packedY}. When demux is on, skip {@link AEPacketRaw}.
         */
        private void emitPackedPolarity(final AEPacketRaw buffer, final int xAddr, final int yAddr, final int polarity) {
            final int addr = ((yAddr & AEDAT2_Y_ADDR_MASK) << AEDAT2_Y_ADDR_SHIFT)
                    | ((xAddr & AEDAT2_X_ADDR_MASK) << AEDAT2_X_ADDR_SHIFT)
                    | ((polarity & AEDAT2_POLARITY_MASK) << AEDAT2_POLARITY_SHIFT);
            if (usbTypedDemuxActive) {
                final int sizeY = getChip() != null ? getChip().getSizeY() : dvsSizeY;
                polarityBuilder.addPolarity(xAddr, (sizeY - 1) - yAddr, polarity != 0, currentTimestamp, addr);
                eventCounter++;
                return;
            }
            buffer.getTimestamps()[eventCounter] = currentTimestamp;
            buffer.getAddresses()[eventCounter] = addr;
            eventCounter++;
        }

        @Override
        protected void translateEvents(final ByteBuffer b) {
            if (mipiCx3) {
                translateMipiCx3Events(b);
                return;
            }
            synchronized (aePacketRawPool) {
                final AEPacketRaw buffer = aePacketRawPool.writeBuffer();
                beginTypedDemux();

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
                                            if (usbTypedDemuxActive) {
                                                offerCx3Imu(new IMUSample(currentTimestamp, imuEvents));
                                            } else if (ensureCapacity(buffer, eventCounter + IMUSample.SIZE_EVENTS)) {
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

                                    emitPackedPolarity(buffer, xAddr, yAddr, polarity);
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

                endTypedDemux(buffer);
            } // sync on aePacketRawPool
        }

        private int mipiWords;
        private int mipiZeros;
        private int mipiGroups;
        private int mipiColumns;
        private int mipiTimestampRefs;
        private int mipiUnknown;
        private int mipiEmitted;
        private long mipiLastLogMs;

        /**
         * S5K231Y / CX3 MIPI 32-bit words (libcaer {@code mipiCx3EventTranslator}).
         */
        private void translateMipiCx3Events(final ByteBuffer b) {
            synchronized (aePacketRawPool) {
                final AEPacketRaw buffer = aePacketRawPool.writeBuffer();
                beginTypedDemux();
                final int byteLen = b.remaining() > 0 ? b.remaining() : b.limit();
                if ((byteLen & 0x03) != 0) {
                    CypressFX3.log.warning(String.format(
                            "%d bytes received via USB, which is not a multiple of four.", byteLen));
                    b.limit(b.position() + (byteLen & ~0x03));
                }
                buffer.lastCaptureIndex = eventCounter;
                final int eventsBefore = eventCounter;
                final ByteBuffer view = b.duplicate();
                view.order(ByteOrder.LITTLE_ENDIAN);
                final IntBuffer iBuf = view.asIntBuffer();
                if (debug && mipiWords == 0 && iBuf.remaining() > 0) {
                    final int n = Math.min(8, iBuf.remaining());
                    final StringBuilder hex = new StringBuilder("Mini/Micro first USB words:");
                    for (int i = 0; i < n; i++) {
                        hex.append(String.format(" %08x", iBuf.get(i)));
                    }
                    iBuf.rewind();
                    CypressFX3.log.info(hex.toString());
                }
                for (int bufPos = 0; bufPos < iBuf.limit(); bufPos++) {
                    final int event = iBuf.get(bufPos);
                    mipiWords++;
                    if (event == 0) {
                        mipiZeros++;
                        continue;
                    }
                    if ((event & 0x80000000) != 0) {
                        mipiGroups++;
                        parseMipiGroup(buffer, event);
                    } else if ((event & 0x04000000) != 0) {
                        mipiColumns++;
                        parseMipiColumn(event);
                    } else if ((event & 0x08000000) != 0) {
                        mipiTimestampRefs++;
                        parseMipiTimestampRef(event);
                    } else {
                        mipiUnknown++;
                        if (debug && mipiUnknown <= 8) {
                            CypressFX3.log.info(String.format("Mini/Micro unknown MIPI word 0x%08x", event));
                        }
                    }
                }
                mipiEmitted += eventCounter - eventsBefore;
                endTypedDemux(buffer);
                if (debug) {
                    final long now = System.currentTimeMillis();
                    if ((now - mipiLastLogMs) >= 2000) {
                        mipiLastLogMs = now;
                        CypressFX3.log.info(String.format(
                                "Mini/Micro MIPI: words=%d zero=%d group=%d col=%d tsref=%d unk=%d emitted=%d lastCol=%d tref=%d",
                                mipiWords, mipiZeros, mipiGroups, mipiColumns, mipiTimestampRefs, mipiUnknown, mipiEmitted,
                                mipiLastColumn, mipiReferenceUs));
                    }
                }
            }
        }

        private void parseMipiGroup(final AEPacketRaw buffer, final int event) {
            if (mipiLastColumn < 0) {
                return;
            }
            int group1Address = (event >>> 18) & 0x003F;
            int group2Address = group1Address + ((event >>> 26) & 0x001F);
            group1Address *= 8;
            group2Address *= 8;
            if (group1Address >= dvsSizeY || group2Address >= dvsSizeY) {
                return;
            }
            if (!ensureCapacity(buffer, eventCounter + 16)) {
                return;
            }
            final int group1Events = event & 0x00FF;
            final int group1Polarity = ((event >>> 16) & 0x01) == 0 ? 1 : 0;
            emitMipiGroupPixels(buffer, group1Events, group1Polarity, group1Address);
            final int group2Events = (event >>> 8) & 0x00FF;
            final int group2Polarity = ((event >>> 17) & 0x01) == 0 ? 1 : 0;
            emitMipiGroupPixels(buffer, group2Events, group2Polarity, group2Address);
        }

        private void emitMipiGroupPixels(final AEPacketRaw buffer, final int bits, final int polarity, final int yBase) {
            for (int i = 0, mask = 0x01; i < 8; i++, mask <<= 1) {
                if ((bits & mask) == 0) {
                    continue;
                }
                int xAddr = mipiLastColumn;
                int yAddr = yBase + i;
                if (dvsInvertXY) {
                    final int temp = xAddr;
                    xAddr = yAddr;
                    yAddr = temp;
                }
                emitPackedPolarity(buffer, xAddr, yAddr, polarity);
            }
        }

        private void parseMipiColumn(final int event) {
            if (mipiReferenceUs < 0) {
                return;
            }
            final boolean startOfFrame = ((event >>> 21) & 0x01) != 0;
            final int columnAddr = event & 0x03FF;
            if (columnAddr >= dvsSizeX) {
                return;
            }
            if (startOfFrame) {
                final int timestampSub = (event >>> 11) & 0x03FF;
                if (mipiReferenceUs == mipiLastUsedReference && timestampSub <= mipiLastUsedSub) {
                    resetMipiParser("timestamp reference lost");
                    return;
                }
                mipiLastTimestamp = mipiCurrTimestamp;
                mipiCurrTimestamp = mipiReferenceUs + timestampSub;
                mipiLastUsedReference = mipiReferenceUs;
                mipiLastUsedSub = timestampSub;
                lastTimestamp = currentTimestamp;
                currentTimestamp = (int) (mipiCurrTimestamp & 0x7FFFFFFFL);
            } else if (mipiLastColumn < 0) {
                return;
            } else if (columnAddr <= mipiLastColumn) {
                resetMipiParser("column address illegal jump");
                return;
            }
            mipiLastColumn = columnAddr;
        }

        private void parseMipiTimestampRef(final int event) {
            final int timestampRef = event & 0x003FFFFF;
            if (mipiLastReference >= 0 && timestampRef <= mipiLastReference) {
                mipiReferenceOverflow++;
            }
            mipiLastReference = timestampRef;
            mipiReferenceUs = ((long) mipiReferenceOverflow << 22) + timestampRef;
            mipiReferenceUs *= 1000L;
        }

        private void resetMipiParser(final String reason) {
            mipiLastColumn = -1;
            mipiReferenceUs = -1;
            mipiLastUsedSub = -1;
            mipiLastUsedReference = -1;
            if (debug) {
                CypressFX3.log.info("DVXplorer Micro MIPI parser reset: " + reason);
            }
        }

        /**
         * DVXplorerM debug EP IMU packet (code 0x01), same layout as
         * {@code usbDebugCallback} in dv-processing.
         */
        void writeCx3ImuSample(final ByteBuffer raw, final int length) {
            if (length < 1) {
                return;
            }
            final int code = raw.get(0) & 0xFF;
            if (code != 0x01) {
                if (debug && code == 0 && length > 6) {
                    CypressFX3.log.info("Mini/Micro device log on EP 0x81, " + length + " bytes");
                }
                return;
            }
            if (length < 16) {
                return;
            }
            // Safety net if a URB still completes early.
            final long nowNs = System.nanoTime();
            if (cx3ImuLastHostNs != 0 && (nowNs - cx3ImuLastHostNs) < CX3_IMU_PERIOD_NS) {
                cx3ImuDropped++;
                return;
            }
            cx3ImuPackets++;
            final int flags = raw.get(1) & 0xFF;
            final int accelSel = (flags >>> 3) & 0x03;
            final int gyroSel = flags & 0x07;
            final float accelScale = 65536.0f / (4 * (1 << accelSel));
            final int gyroScaleAsc = Math.max(0, 4 - gyroSel);
            final float gyroScale = 65536.0f / (250 * (1 << gyroScaleAsc));
            final short rawGx = le16(raw, 2);
            final short rawGy = le16(raw, 4);
            final short rawGz = le16(raw, 6);
            final short rawAx = le16(raw, 8);
            final short rawAy = le16(raw, 10);
            final short rawAz = le16(raw, 12);
            final short rawTemp = le16(raw, 14);
            if ((flags & 0x80) != 0) {
                imuEvents[0] = toImuAccelLsb(rawAx / accelScale, imuFlipX);
                imuEvents[1] = toImuAccelLsb(rawAy / accelScale, imuFlipY);
                imuEvents[2] = toImuAccelLsb(rawAz / accelScale, imuFlipZ);
            }
            if ((flags & 0x40) != 0) {
                imuEvents[4] = toImuGyroLsb(rawGx / gyroScale, imuFlipX);
                imuEvents[5] = toImuGyroLsb(rawGy / gyroScale, imuFlipY);
                imuEvents[6] = toImuGyroLsb(rawGz / gyroScale, imuFlipZ);
            }
            if ((flags & 0x20) != 0) {
                final float tempC = (rawTemp / 512.0f) + 23.0f;
                imuEvents[3] = clampShort((tempC - 35.0f) / TEMP_DEGC_PER_LSB);
            }
            int dtUs = cx3ImuLastHostNs == 0 ? 1250
                    : (int) ((nowNs - cx3ImuLastHostNs) / 1000L);
            if (dtUs < 1) {
                dtUs = 1;
            } else if (dtUs > 20_000) {
                dtUs = 1250;
            }
            cx3ImuLastHostNs = nowNs;
            int ts = cx3ImuLastTimestamp == 0
                    ? (currentTimestamp > 0 ? currentTimestamp : dtUs)
                    : (int) (cx3ImuLastTimestamp + dtUs);
            cx3ImuLastTimestamp = ts;
            // Direct ImuPacket (no AEPacketRaw round-trip): use the tracked
            // constructor so updateStatistics fills deltaTimeUs (952cfae41e).
            // fromRawUntracked is only for encode-then-constructFromAEPacketRaw.
            DVXplorerFX3HardwareInterface.this.offerCx3Imu(new IMUSample(ts, imuEvents));
            cx3ImuWritten++;
            if (debug && (cx3ImuPackets <= 4 || (cx3ImuPackets % 400) == 0)) {
                CypressFX3.log.info(String.format(
                        "Mini/Micro IMU: packets=%d dropped=%d flags=0x%02x gyroScale=%.1f rawG=%d,%d,%d dps=%.1f,%.1f,%.1f",
                        cx3ImuPackets, cx3ImuDropped, flags, gyroScale, rawGx, rawGy, rawGz,
                        rawGx / gyroScale, rawGy / gyroScale, rawGz / gyroScale));
            }
        }

        private static short le16(final ByteBuffer b, final int offset) {
            return (short) ((b.get(offset) & 0xFF) | (b.get(offset + 1) << 8));
        }

        private short toImuAccelLsb(final float g, final boolean flip) {
            float v = g / ACCEL_G_PER_LSB;
            if (flip) {
                v = -v;
            }
            return clampShort(Math.round(v));
        }

        private short toImuGyroLsb(final float dps, final boolean flip) {
            float v = dps / GYRO_DPS_PER_LSB;
            if (flip) {
                v = -v;
            }
            return clampShort(Math.round(v));
        }

        private static short clampShort(final float v) {
            if (v > Short.MAX_VALUE) {
                return Short.MAX_VALUE;
            }
            if (v < Short.MIN_VALUE) {
                return Short.MIN_VALUE;
            }
            return (short) v;
        }

        @Override
        public void propertyChange(final PropertyChangeEvent arg0) {
            // FX3 IMU is in the bulk event stream; Mini/Micro IMU is EP 0x81.
        }
    }
}

