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
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

import org.usb4java.Device;

import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.DavisConfig;
import eu.seebetter.ini.chips.davis.DavisUsbPacketBundleBuilder;
import eu.seebetter.ini.chips.davis.SciDVS;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import net.sf.jaer.JaerConstants;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * Adds functionality of apsDVS sensors to based CypressFX3Biasgen class. The
 * key method is translateEvents that parses the data from the sensor to
 * construct jAER raw events. When {@code usbTypedDemux} is enabled (prefs),
 * USB decode also fills a typed {@link PacketBundle} for ViewLoop (no second
 * {@code extractBundle}).
 *
 * @author Christian/Tobi
 */
public class DAViSFX3HardwareInterface extends CypressFX3Biasgen {

    private int warningCount = 0;
    private static final long STARTUP_TIMESTAMP_RESET_TIMEOUT_MS = 1_000L;
    private static final long QUIESCENT_DRAIN_QUIET_MS = 100L;
    private static final long STARTUP_SOURCE_DRAIN_TIMEOUT_MS = 3_000L;
    private static final long QUIESCENT_DRAIN_TIMEOUT_MS = 500L;
    private static final int WARNING_INTERVAL = 100000;

    private static final Preferences PREFS = JaerConstants.PREFS_ROOT_HARDWARE.node("DAViSFX3");
    /**
     * Pref kill-switch: when false, ViewLoop uses AEPacketRaw + extractBundle.
     * Default true for mono APS+DVS; color RGB remains on legacy extract until
     * a color frame assembler exists.
     */
    public static final String PREF_USB_TYPED_DEMUX = "usbTypedDemux";
    /**
     * When demux is active and this is false (default), APS ADC samples and IMU
     * are not dual-written into AEPacketRaw (largest live memory win). Polarity
     * and external events are still written for AEDAT-2 reconstruct if needed.
     */
    public static final String PREF_DUAL_WRITE_APS_IMU_AE = "dualWriteApsImuAe";

    /** When demux active: also write APS/IMU synthetic AEs into raw (validation). */
    private volatile boolean dualWriteApsImuAe;

    protected DAViSFX3HardwareInterface(final Device device) {
        super(device);
        usbTypedDemuxActive = PREFS.getBoolean(PREF_USB_TYPED_DEMUX, true);
        dualWriteApsImuAe = PREFS.getBoolean(PREF_DUAL_WRITE_APS_IMU_AE, false);
        CypressFX3.log.fine(String.format(
                "DAViSFX3 USB typed demux=%s dualWriteApsImuAe=%s (prefs %s/%s)",
                usbTypedDemuxActive, dualWriteApsImuAe, PREF_USB_TYPED_DEMUX, PREF_DUAL_WRITE_APS_IMU_AE));
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

    /** Cached exact FPGA-geometry fingerprint for the shared DAVIS/SciDVS PID. */
    private Boolean sciDVSFpgaGeometryMatch;
    /** Prior active DVS.Run state retained until a gated GAER startup succeeds. */
    private boolean gaerStartupDvsRunRestorePending;
    /** First timestamp-order failure caught at the native USB callback boundary. */
    private final AtomicReference<SciDVSGaerTimestampOrderGuard.ValidationException>
            gaerTimestampCallbackFailure = new AtomicReference<>();

    /**
     * Probes the two read-only FPGA DVS geometry registers and caches whether
     * they match the validated SciDVS bitstream. The register axes are the raw
     * stream axes, hence {@code 126x112} for the logical {@code 112x126} chip.
     *
     * <p>The first read uses the normal Cypress open path when necessary,
     * including its one USB reset and interface claim. The same interface is
     * left open, so later chip binding does not perform a second reset. This
     * method does not start acquisition, write an FPGA register, or close the
     * device.
     *
     * @return true only for the exact validated SciDVS FPGA geometry
     * @throws HardwareInterfaceException if the FPGA geometry cannot be read
     */
    public synchronized boolean probeSciDVSByFpgaGeometry() throws HardwareInterfaceException {
        if (sciDVSFpgaGeometryMatch != null) {
            return sciDVSFpgaGeometryMatch;
        }
        final int dvsSizeX = spiConfigReceive(CypressFX3.FPGA_DVS, (short) 0);
        final int dvsSizeY = spiConfigReceive(CypressFX3.FPGA_DVS, (short) 1);
        sciDVSFpgaGeometryMatch = matchesSciDVSFpgaGeometry(dvsSizeX, dvsSizeY);
        CypressFX3.log.info(String.format(
                "DAViSFX3 FPGA DVS geometry fingerprint=%dx%d SciDVS=%s",
                dvsSizeX, dvsSizeY, sciDVSFpgaGeometryMatch));
        return sciDVSFpgaGeometryMatch;
    }

    /** Hardware-free exact classifier used by the probe and regression test. */
    public static boolean matchesSciDVSFpgaGeometry(final int dvsSizeX, final int dvsSizeY) {
        return dvsSizeX == 126 && dvsSizeY == 112;
    }

    @Override
    public synchronized void setEventAcquisitionEnabled(final boolean enable)
            throws HardwareInterfaceException {
        if (!enable || !(getChip() instanceof SciDVS)
                || !SciDVSGaerMode.resolveFromSystemProperty(true, CypressFX3.log)) {
            super.setEventAcquisitionEnabled(enable);
            return;
        }

        try {
            pauseDvsForGaerStartup();
        } catch (final HardwareInterfaceException | RuntimeException pauseFailure) {
            failClosedAfterGaerStartup(pauseFailure);
            throw pauseFailure;
        }

        try {
            super.setEventAcquisitionEnabled(true);
        } catch (final HardwareInterfaceException | RuntimeException startupFailure) {
            failClosedAfterGaerStartup(startupFailure);
            throw startupFailure;
        }

        try {
            restoreDvsAfterGaerStartup();
        } catch (final HardwareInterfaceException | RuntimeException restoreFailure) {
            failClosedAfterGaerStartup(restoreFailure);
            throw restoreFailure;
        }
    }

    private void pauseDvsForGaerStartup() throws HardwareInterfaceException {
        final int dvsRun = spiConfigReceive(CypressFX3.FPGA_DVS, (short) 3);
        if (dvsRun != 0) {
            gaerStartupDvsRunRestorePending = true;
        }
        if (gaerStartupDvsRunRestorePending) {
            spiConfigSend(CypressFX3.FPGA_DVS, (short) 3, 0);
        }
    }

    private void restoreDvsAfterGaerStartup() throws HardwareInterfaceException {
        if (gaerStartupDvsRunRestorePending) {
            spiConfigSend(CypressFX3.FPGA_DVS, (short) 3, 1);
            gaerStartupDvsRunRestorePending = false;
        }
    }

    private void failClosedAfterGaerStartup(final Throwable originalFailure) {
        try {
            spiConfigSend(CypressFX3.FPGA_DVS, (short) 3, 0);
        } catch (final HardwareInterfaceException | RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }

        boolean delegatedStop = false;
        try {
            super.setEventAcquisitionEnabled(false);
            delegatedStop = true;
        } catch (final HardwareInterfaceException | RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
        if (!delegatedStop) {
            try {
                stopAEReader();
            } catch (final RuntimeException cleanupFailure) {
                originalFailure.addSuppressed(cleanupFailure);
            }
        }
    }

    public long getGaerNonMonotonicTimestampCount() {
        final AEReader reader = getAeReader();
        return reader instanceof RetinaAEReader retinaReader
                ? retinaReader.gaerDecoder.getNonMonotonicTimestampCount() : 0L;
    }

    public int getGaerMaxBackwardTimestampUs() {
        final AEReader reader = getAeReader();
        return reader instanceof RetinaAEReader retinaReader
                ? retinaReader.gaerDecoder.getMaxBackwardTimestampUs() : 0;
    }

    private void retainGaerTimestampCallbackFailure(
            final SciDVSGaerTimestampOrderGuard.ValidationException failure) {
        if (gaerTimestampCallbackFailure.compareAndSet(null, failure)) {
            final byte[] snapshot = failure.getTransferSnapshot();
            CypressFX3.log.log(java.util.logging.Level.SEVERE,
                    "SCIDVS_GAER_TIMESTAMP_CALLBACK_FAILURE byteOffset="
                    + failure.getByteOffset() + " transferBytes="
                    + (snapshot == null ? "unavailable" : snapshot.length),
                    failure);
        }
    }

    private void clearGaerTimestampCallbackFailureAfterOwnedRestartAndReset() {
        gaerTimestampCallbackFailure.set(null);
    }

    private void throwIfGaerTimestampCallbackFailure()
            throws HardwareInterfaceException {
        final SciDVSGaerTimestampOrderGuard.ValidationException failure
                = gaerTimestampCallbackFailure.get();
        if (failure != null) {
            throw new HardwareInterfaceException(
                    "SciDVS GAER timestamp callback failure retained at byte offset "
                    + failure.getByteOffset(), failure);
        }
    }

    @Override
    public AEPacketRaw acquireAvailableEventsFromDriver()
            throws HardwareInterfaceException {
        throwIfGaerTimestampCallbackFailure();
        final AEPacketRaw events = super.acquireAvailableEventsFromDriver();
        throwIfGaerTimestampCallbackFailure();
        return events;
    }

    @Override
    public PacketBundle acquireAvailablePacketBundle()
            throws HardwareInterfaceException {
        throwIfGaerTimestampCallbackFailure();
        final PacketBundle bundle = super.acquireAvailablePacketBundle();
        throwIfGaerTimestampCallbackFailure();
        return bundle;
    }

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
        final RetinaAEReader reader = new RetinaAEReader(this);
        final boolean gaerActive = SciDVSGaerMode.resolveFromSystemProperty(
                getChip() instanceof SciDVS, CypressFX3.log);
        reader.gaerTimestampCallbackRecoveryPending = gaerActive
                && gaerTimestampCallbackFailure.get() != null;
        setAeReader(reader);
        allocateAEBuffers();

        reader.startThread(); // arg is number of errors before giving up
        if (gaerActive) {
            if (getChip() instanceof SciDVS) {
                try {
                    awaitGaerStartupSourceQuiescence(reader);
                } catch (final HardwareInterfaceException e) {
                    abortStartupTimestampReset(reader);
                    throw e;
                }
            }
            // A new decoder starts with no wrap state while the device endpoint can
            // still contain pre-stop words. Put a positive reset marker behind that
            // backlog, wait until this exact reader decodes it, then discard every
            // packet accumulated through the marker. Acquisition is exposed only
            // from the next transfer, which belongs wholly to the new epoch.
            final boolean resetObserved;
            try {
                reader.armStartupTimestampReset();
                resetTimestamps();
                resetObserved = reader.awaitStartupTimestampReset(
                        STARTUP_TIMESTAMP_RESET_TIMEOUT_MS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                abortStartupTimestampReset(reader);
                throw new HardwareInterfaceException(
                        "Interrupted waiting for SciDVS startup timestamp reset", e);
            } finally {
                reader.disarmStartupTimestampReset();
            }
            if (!resetObserved) {
                abortStartupTimestampReset(reader);
                throw new HardwareInterfaceException(
                        "Timed out waiting for SciDVS startup timestamp reset marker");
            }
            // The marker callback and this state transition use the same
            // packet-pool lock, so they cannot interleave with another callback.
            synchronized (aePacketRawPool) {
                allocateAEBuffers();
                reader.gaerTimestampOrderGuard.clearAfterOwnedRestartAndReset();
                reader.gaerTimestampCallbackRecoveryPending = false;
                clearGaerTimestampCallbackFailureAfterOwnedRestartAndReset();
            }
            log.info("SciDVS startup timestamp reset observed; discarded pre-boundary packets");
        }
        HardwareInterfaceException.clearException();
    }

    private void awaitGaerStartupSourceQuiescence(final RetinaAEReader reader)
            throws HardwareInterfaceException {
        reader.quiescentDrain.beginDrain();
        try {
            final boolean quiescent = reader.quiescentDrain.awaitQuiescence(
                    QUIESCENT_DRAIN_QUIET_MS, STARTUP_SOURCE_DRAIN_TIMEOUT_MS);
            if (!quiescent) {
                throw new HardwareInterfaceException(
                        "Timed out waiting for paused SciDVS source payload to drain");
            }
            log.info("Paused SciDVS source payload reached quiescence before timestamp reset");
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HardwareInterfaceException(
                    "Interrupted waiting for paused SciDVS source payload to drain", e);
        } finally {
            reader.quiescentDrain.endDrain();
            logStartupSourceDrainTimeline(reader);
        }
    }

    private void logStartupSourceDrainTimeline(final RetinaAEReader reader) {
        final int retainedCount
                = reader.quiescentDrain.getCompletedTransferCount();
        final boolean truncated
                = reader.quiescentDrain.isTransferTimelineTruncated();
        log.info(String.format(
                "SciDVS startup source-drain transfer timeline: retained=%d truncated=%s",
                retainedCount, truncated));
        for (int index = 0; index < retainedCount; index++) {
            final long elapsedMicros
                    = reader.quiescentDrain.getCompletedTransferElapsedNanos(index) / 1_000L;
            final int actualLength
                    = reader.quiescentDrain.getCompletedTransferLength(index);
            final boolean sourcePayload
                    = reader.quiescentDrain.getCompletedTransferSourcePayload(index);
            log.info(String.format(
                    "SciDVS startup source-drain transfer: index=%d elapsedUs=%d actualLength=%d sourcePayload=%s",
                    index, elapsedMicros, actualLength, sourcePayload));
        }
    }

    private void abortStartupTimestampReset(final RetinaAEReader reader) {
        try {
            setInEndpointEnabled(false);
        } catch (final HardwareInterfaceException e) {
            log.warning("Could not disable endpoint after startup timestamp-reset failure: " + e);
        }
        if (getAeReader() == reader) {
            stopAEReader();
        }
    }

    @Override
    protected void beforeDisableINEndpoint() throws HardwareInterfaceException {
        if (!(getAeReader() instanceof RetinaAEReader reader)
                || !SciDVSGaerMode.resolveFromSystemProperty(
                        getChip() instanceof SciDVS, CypressFX3.log)) {
            return;
        }

        reader.quiescentDrain.beginDrain();
        try {
            // Stop all event producers and timestamp generation while leaving
            // the mux and FPGA USB output running so queued payload can drain.
            spiConfigSend(CypressFX3.FPGA_EXTINPUT, (short) 0, 0);
            spiConfigSend(CypressFX3.FPGA_IMU, (short) 2, 0);
            spiConfigSend(CypressFX3.FPGA_IMU, (short) 3, 0);
            spiConfigSend(CypressFX3.FPGA_IMU, (short) 4, 0);
            spiConfigSend(CypressFX3.FPGA_APS, (short) 4, 0);
            spiConfigSend(CypressFX3.FPGA_DVS, (short) 3, 0);
            spiConfigSend(CypressFX3.FPGA_MUX, (short) 1, 0);

            final boolean quiescent = reader.quiescentDrain.awaitQuiescence(
                    QUIESCENT_DRAIN_QUIET_MS, QUIESCENT_DRAIN_TIMEOUT_MS);
            if (!quiescent) {
                throw new HardwareInterfaceException(
                        "Timed out waiting for SciDVS USB payload to drain");
            }
            log.info("SciDVS USB payload reached quiescence before endpoint disable");
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HardwareInterfaceException(
                    "Interrupted waiting for SciDVS USB payload to drain", e);
        } finally {
            reader.quiescentDrain.endDrain();
        }
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

        /** jAER 3.0 typed demux into PacketBundle (alongside AEPacketRaw for DVS). */
        private final DavisUsbPacketBundleBuilder typedBuilder = new DavisUsbPacketBundleBuilder();
        private boolean rollingShutterFrame;

        /** SciDVS GAER decode path: eager decoder and sinks, lazy resolved mode flag. */
        private final SciDVSGaerDecoder gaerDecoder;
        private final SciDVSGaerTimestampOrderGuard gaerTimestampOrderGuard
                = new SciDVSGaerTimestampOrderGuard();
        private final SciDVSGaerRawSink gaerRawSink;
        private final SciDVSGaerTypedSink gaerTypedSink;
        private final SciDVSGaerSink gaerTimestampResetOnlySink
                = new SciDVSGaerSink() {
                    @Override
                    public void onTimestampReset() {
                        handleGaerTimestampReset();
                    }
                };
        private final Fx3StartupTimestampResetBarrier startupTimestampReset
                = new Fx3StartupTimestampResetBarrier();
        private final Fx3QuiescentDrainBarrier quiescentDrain
                = new Fx3QuiescentDrainBarrier();
        private int completedTransferActualLength;
        private Boolean gaerResolved;
        private boolean gaerTimestampCallbackRecoveryPending;
        private volatile boolean gaerStartupTimestampResetArmed;

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
            // Color APS assembler not ready — keep legacy extractBundle for RGB chips
            if (chipID == DAViSFX3HardwareInterface.CHIP_DAVISRGB && usbTypedDemuxActive) {
                usbTypedDemuxActive = false;
                CypressFX3.log.info("DAViSFX3: USB typed demux disabled for DAVIS RGB (use extractBundle)");
            }

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

            gaerDecoder = new SciDVSGaerDecoder(new SciDVSGaerDecoder.Config(
                    chipID, dvsSizeX, dvsSizeY, dvsInvertXY,
                    apsSizeX, apsSizeY, apsInvertXY, apsFlipX, apsFlipY,
                    imuFlipX, imuFlipY, imuFlipZ), super.toString(), this::shouldLogGaerWarning);
            gaerRawSink = new SciDVSGaerRawSink(
                    DAViSFX3HardwareInterface.this::getAEBufferSize,
                    this::handleGaerTimestampReset,
                    () -> true);
            typedBuilder.setHostCapacitySupplier(
                    DAViSFX3HardwareInterface.this::getAEBufferSize);
            gaerTypedSink = new SciDVSGaerTypedSink(
                    typedBuilder,
                    () -> getChip() != null ? getChip().getSizeX() : dvsSizeX,
                    this::handleGaerTimestampReset);
        }

        private boolean shouldLogGaerWarning() {
            return (warningCount++ % WARNING_INTERVAL) == 0;
        }

        private void handleGaerTimestampReset() {
            if (gaerStartupTimestampResetArmed) {
                gaerStartupTimestampResetArmed = false;
                startupTimestampReset.markResetObserved();
            }
            updateTimestampMasterStatus();
            CypressFX3.log.info("Timestamp reset event received on " + super.toString()
                    + " at System.currentTimeMillis()=" + System.currentTimeMillis());
        }

        private void armStartupTimestampReset() {
            gaerStartupTimestampResetArmed = true;
        }

        private void disarmStartupTimestampReset() {
            gaerStartupTimestampResetArmed = false;
        }

        private boolean awaitStartupTimestampReset(final long timeoutMs)
                throws InterruptedException {
            return startupTimestampReset.awaitReset(timeoutMs);
        }

        @Override
        protected void noteCompletedTransfer(final int actualLength) {
            completedTransferActualLength = actualLength;
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
            final boolean gaerModeUnresolved = gaerResolved == null;
            if (gaerModeUnresolved && getChip() != null) {
                gaerResolved = SciDVSGaerMode.resolveFromSystemProperty(
                        getChip() instanceof SciDVS, CypressFX3.log);
                CypressFX3.log.info("DAViSFX3 SciDVS GAER selected=" + gaerResolved
                        + " rawProperty=" + System.getProperty(SciDVSGaerMode.PROPERTY)
                        + " chipClass=" + getChip().getClass().getName());
                if (getChip().getSizeX() != dvsSizeX || getChip().getSizeY() != dvsSizeY) {
                    CypressFX3.log.warning("DAViSFX3 chip geometry " + getChip().getSizeX() + "x"
                            + getChip().getSizeY() + " differs from FPGA DVS geometry "
                            + dvsSizeX + "x" + dvsSizeY);
                }
            }

            if (Boolean.TRUE.equals(gaerResolved)) {
                if (gaerTimestampCallbackRecoveryPending
                        && gaerTimestampCallbackFailure.get() != null) {
                    decodeGaerTimestampResetOnly(b);
                    return;
                }
                if (gaerTimestampCallbackFailure.get() != null) {
                    return;
                }
                try {
                    gaerTimestampOrderGuard.validate(b);
                } catch (final SciDVSGaerTimestampOrderGuard.ValidationException failure) {
                    retainGaerTimestampCallbackFailure(failure);
                    gaerTimestampCallbackRecoveryPending = true;
                    decodeGaerTimestampResetOnly(b);
                    return;
                }
            }

            if (quiescentDrain.isDraining()) {
                quiescentDrain.noteCompletedTransfer(
                        completedTransferActualLength,
                        SciDVSGaerDecoder.containsSourcePayload(b));
            }

            if (isAuthoritativeTypedDelivery()) {
                synchronized (packetBundlePool) {
                    final PacketBundle typedOut = packetBundlePool.writeBuffer();
                    prepareAuthoritativeTypedBundle(typedOut);
                    typedBuilder.attach(typedOut, getChip(), apsSizeX, apsSizeY);

                    if (Boolean.TRUE.equals(gaerResolved)) {
                        gaerDecoder.decode(b, gaerTypedSink);
                        typedBuilder.flushAll();
                        return;
                    }

                    translateStandardTyped(b);
                    typedBuilder.flushAll();
                    return;
                }
            }

            synchronized (aePacketRawPool) {
                final AEPacketRaw buffer = aePacketRawPool.writeBuffer();
                final PacketBundle typedOut = null;

                if (Boolean.TRUE.equals(gaerResolved)) {
                    gaerRawSink.begin(buffer, eventCounter);
                    gaerDecoder.decode(b, gaerRawSink);
                    eventCounter = gaerRawSink.end();
                    return;
                }

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

                                        if (typedOut != null) {
                                            typedBuilder.addExternal(data, currentTimestamp);
                                        }
                                        if (ensureCapacity(buffer, eventCounter + 1)) {
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
                                            final IMUSample imuSample = new IMUSample(currentTimestamp, imuEvents);
                                            if (typedOut != null) {
                                                typedBuilder.addImu(imuSample);
                                            }
                                            // Dual-write IMU AEs only when demux off or prefs request validation path
                                            if ((typedOut == null || dualWriteApsImuAe)
                                                    && ensureCapacity(buffer, eventCounter + IMUSample.SIZE_EVENTS)) {
                                                eventCounter += imuSample.writeToPacket(buffer, eventCounter);
                                            }
                                        } else {
                                            if (warningCount % WARNING_INTERVAL == 0) {
                                                CypressFX3.log.info(
                                                        "IMU End: failed to validate IMU sample count (" + imuCount + "), discarding samples.");
                                            }
                                            warningCount++;
                                        }
                                        break;

                                    case 8: // APS Global Shutter Frame Start
                                        CypressFX3.log.finest("APS GS Frame Start event received.");
                                        rollingShutterFrame = false;
                                        if (typedOut != null) {
                                            typedBuilder.onFrameStart(false, currentTimestamp);
                                        }
                                        initFrame();
                                        break;

                                    case 9: // APS Rolling Shutter Frame Start
                                        CypressFX3.log.finest("APS RS Frame Start event received.");
                                        rollingShutterFrame = true;
                                        if (typedOut != null) {
                                            typedBuilder.onFrameStart(true, currentTimestamp);
                                        }
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
                                // Check range conformity (break must not depend on warning throttle).
                                if (data >= dvsSizeX) {
                                    if (warningCount % WARNING_INTERVAL == 0) {
                                        CypressFX3.log.severe("DVS: X address out of range (0-" + (dvsSizeX - 1) + "): " + data + ".");
                                    }
                                    warningCount++;
                                    break;
                                }

                                {
                                    final byte polarity = ((chipID == DAViSFX3HardwareInterface.CHIP_DAVIS208) && (data <= 16))
                                            ? ((byte) (~code))
                                            : (code);
                                    final boolean on = (polarity & 0x01) != 0;
                                    final int packedAddr;
                                    if (dvsInvertXY) {
                                        packedAddr = (((dvsSizeX - 1 - data) << DavisChip.YSHIFT) & DavisChip.YMASK)
                                                | (((dvsSizeY - 1 - dvsLastY) << DavisChip.XSHIFT) & DavisChip.XMASK)
                                                | (((polarity & 0x01) << DavisChip.POLSHIFT) & DavisChip.POLMASK);
                                    } else {
                                        packedAddr = (((dvsSizeY - 1 - dvsLastY) << DavisChip.YSHIFT) & DavisChip.YMASK)
                                                | (((dvsSizeX - 1 - data) << DavisChip.XSHIFT) & DavisChip.XMASK)
                                                | (((polarity & 0x01) << DavisChip.POLSHIFT) & DavisChip.POLMASK);
                                    }
                                    // Match DavisEventExtractor.extractBundleTyped: unflip X with chip sizeX,
                                    // not FPGA dvsSizeX (they differ when dvsInvertXY swaps stream axes).
                                    if (typedOut != null) {
                                        final int sx1 = (getChip() != null ? getChip().getSizeX() : dvsSizeX) - 1;
                                        final int addrX = sx1 - ((packedAddr & DavisChip.XMASK) >>> DavisChip.XSHIFT);
                                        final int addrY = (packedAddr & DavisChip.YMASK) >>> DavisChip.YSHIFT;
                                        typedBuilder.addPolarity(addrX, addrY, on, currentTimestamp, packedAddr);
                                    }

                                    if (ensureCapacity(buffer, eventCounter + 1)) {
                                        buffer.getAddresses()[eventCounter] = packedAddr;
                                        buffer.getTimestamps()[eventCounter++] = currentTimestamp;
                                    }
                                }

                                break;

                            case 4: // APS ADC sample
                                // Let's check that apsCountY is not above the maximum. This could happen
                                // if start/end of column events are discarded (no wait on transfer stall).
                                if ((apsCountY[apsCurrentReadoutType] >= apsSizeY)
                                        || (apsCountX[apsCurrentReadoutType] >= apsSizeX)) {
                                    if (warningCount % WARNING_INTERVAL == 0) {
                                        CypressFX3.log.fine("APS ADC sample: row or column count is at maximum, discarding further samples.");
                                    }
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

                                if (typedOut != null) {
                                    // pixFirst/Last only for SOF/EOF timestamps; frame completion is count-based
                                    final boolean pixFirst = (apsCountX[apsCurrentReadoutType] == 0)
                                            && (apsCountY[apsCurrentReadoutType] == 1);
                                    final boolean pixLast = (apsCountX[apsCurrentReadoutType] == (apsSizeX - 1))
                                            && (apsCountY[apsCurrentReadoutType] == apsSizeY);
                                    final boolean resetRead = apsCurrentReadoutType == RetinaAEReader.APS_READOUT_RESET;
                                    typedBuilder.setRollingShutter(rollingShutterFrame);
                                    typedBuilder.addApsSample(data & DavisChip.ADC_DATA_MASK, currentTimestamp, xPos, yPos,
                                            resetRead, pixFirst, pixLast);
                                }

                                // Dual-write APS AE only for legacy extract or validation (prefs)
                                if ((typedOut == null || dualWriteApsImuAe)
                                        && ensureCapacity(buffer, eventCounter + 1)) {
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

        private void decodeGaerTimestampResetOnly(final ByteBuffer b) {
            if (quiescentDrain.isDraining()) {
                quiescentDrain.noteCompletedTransfer(
                        completedTransferActualLength,
                        SciDVSGaerDecoder.containsSourcePayload(b));
            }
            gaerDecoder.decode(b, gaerTimestampResetOnlySink);
        }

        @Override
        public void propertyChange(final PropertyChangeEvent arg0) {
            // Do nothing here, IMU comes directly via event-stream.
        }

        /** Standard DAVIS parser used only by authoritative typed delivery. */
        private void translateStandardTyped(final ByteBuffer b) {
            if ((b.limit() & 0x01) != 0) {
                CypressFX3.log.severe(b.limit()
                        + " bytes received via USB, which is not a multiple of two.");
                b.limit(b.limit() & ~0x01);
            }

            final ShortBuffer sBuf = b.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
            for (int i = 0; i < sBuf.limit(); i++) {
                final short event = sBuf.get(i);
                if ((event & 0x8000) != 0) {
                    typedBuilder.invalidatePolarityPatchEligibility();
                    lastTimestamp = currentTimestamp;
                    currentTimestamp = wrapAdd + (event & 0x7FFF);
                    checkMonotonicTimestamp();
                    continue;
                }

                final byte code = (byte) ((event & 0x7000) >>> 12);
                final short data = (short) (event & 0x0FFF);
                final boolean addressPatchWord = code == 6 && (data & 0x0800) != 0;
                if (!addressPatchWord) {
                    typedBuilder.invalidatePolarityPatchEligibility();
                }
                switch (code) {
                    case 0:
                        decodeStandardTypedSpecial(data);
                        break;

                    case 1:
                        if (data >= dvsSizeY) {
                            CypressFX3.log.severe("DVS: Y address out of range (0-"
                                    + (dvsSizeY - 1) + "): " + data + ".");
                            break;
                        }
                        dvsLastY = data;
                        break;

                    case 2:
                    case 3:
                        decodeStandardTypedPolarity(code, data);
                        break;

                    case 4:
                        decodeStandardTypedAps(data);
                        break;

                    case 5:
                        decodeStandardTypedMisc8(data);
                        break;

                    case 6:
                        decodeStandardTypedMisc11(data);
                        break;

                    case 7:
                        wrapAdd += (0x8000L * data);
                        lastTimestamp = currentTimestamp;
                        currentTimestamp = wrapAdd;
                        checkMonotonicTimestamp();
                        CypressFX3.log.finer(String.format(
                                "Timestamp wrap event received on %s with multiplier of %d.",
                                super.toString(), data));
                        break;

                    default:
                        CypressFX3.log.severe("Caught event that can't be handled.");
                        break;
                }
            }
        }

        private void decodeStandardTypedSpecial(final short data) {
            switch (data) {
                case 0:
                    CypressFX3.log.severe("Caught special reserved event!");
                    break;

                case 1:
                    typedBuilder.onTimestampReset(false);
                    wrapAdd = 0;
                    lastTimestamp = 0;
                    currentTimestamp = 0;
                    imuCount = 0;
                    imuType = 0;
                    initFrame();
                    handleGaerTimestampReset();
                    break;

                case 2:
                case 3:
                case 4:
                    CypressFX3.log.finer("External input event received.");
                    typedBuilder.addExternal(data, currentTimestamp);
                    break;

                case 5:
                    CypressFX3.log.finest("IMU6 Start event received.");
                    imuCount = 0;
                    imuType = 0;
                    typedBuilder.onImuStart();
                    break;

                case 7:
                    CypressFX3.log.finest("IMU End event received.");
                    if (imuCount == (2 * RetinaAEReader.IMU_DATA_LENGTH)) {
                        typedBuilder.addImu(new IMUSample(currentTimestamp, imuEvents));
                    } else {
                        typedBuilder.onIncompleteImuSample(
                                "IMU end discarded one incomplete sample with byte count "
                                + imuCount);
                        if (warningCount % WARNING_INTERVAL == 0) {
                            CypressFX3.log.info(
                                    "IMU End: failed to validate IMU sample count ("
                                    + imuCount + "), discarding samples.");
                        }
                        warningCount++;
                    }
                    break;

                case 8:
                    rollingShutterFrame = false;
                    typedBuilder.onFrameStart(false, currentTimestamp);
                    initFrame();
                    break;

                case 9:
                    rollingShutterFrame = true;
                    typedBuilder.onFrameStart(true, currentTimestamp);
                    initFrame();
                    break;

                case 10:
                    for (int i = 0; i < RetinaAEReader.APS_READOUT_TYPES_NUM; i++) {
                        if (apsCountX[i] != apsSizeX
                                && warningCount % WARNING_INTERVAL == 0) {
                            CypressFX3.log.severe("APS Frame End: wrong column count ["
                                    + i + " - " + apsCountX[i] + "] detected.");
                        }
                        warningCount++;
                    }
                    break;

                case 11:
                    apsCurrentReadoutType = RetinaAEReader.APS_READOUT_RESET;
                    apsCountY[apsCurrentReadoutType] = 0;
                    apsRGBPixelOffsetDirection = false;
                    apsRGBPixelOffset = 1;
                    break;

                case 12:
                    apsCurrentReadoutType = RetinaAEReader.APS_READOUT_SIGNAL;
                    apsCountY[apsCurrentReadoutType] = 0;
                    apsRGBPixelOffsetDirection = false;
                    apsRGBPixelOffset = 1;
                    break;

                case 13:
                    if (apsCountY[apsCurrentReadoutType] != apsSizeY
                            && warningCount % WARNING_INTERVAL == 0) {
                        CypressFX3.log.severe("APS Column End: wrong row count ["
                                + apsCurrentReadoutType + " - "
                                + apsCountY[apsCurrentReadoutType] + "] detected.");
                    }
                    warningCount++;
                    apsCountX[apsCurrentReadoutType]++;
                    break;

                case 14:
                case 15:
                case 16:
                case 17:
                    break;

                default:
                    CypressFX3.log.severe("Caught special event that can't be handled.");
                    break;
            }
        }

        private void decodeStandardTypedPolarity(final byte code, final short data) {
            if (data >= dvsSizeX) {
                if (warningCount % WARNING_INTERVAL == 0) {
                    CypressFX3.log.severe("DVS: X address out of range (0-"
                            + (dvsSizeX - 1) + "): " + data + ".");
                }
                warningCount++;
                return;
            }

            final byte polarity = ((chipID == DAViSFX3HardwareInterface.CHIP_DAVIS208)
                    && (data <= 16)) ? ((byte) (~code)) : code;
            final boolean on = (polarity & 0x01) != 0;
            final int packedAddr;
            if (dvsInvertXY) {
                packedAddr = (((dvsSizeX - 1 - data) << DavisChip.YSHIFT)
                        & DavisChip.YMASK)
                        | (((dvsSizeY - 1 - dvsLastY) << DavisChip.XSHIFT)
                                & DavisChip.XMASK)
                        | (((polarity & 0x01) << DavisChip.POLSHIFT)
                                & DavisChip.POLMASK);
            } else {
                packedAddr = (((dvsSizeY - 1 - dvsLastY) << DavisChip.YSHIFT)
                        & DavisChip.YMASK)
                        | (((dvsSizeX - 1 - data) << DavisChip.XSHIFT)
                                & DavisChip.XMASK)
                        | (((polarity & 0x01) << DavisChip.POLSHIFT)
                                & DavisChip.POLMASK);
            }
            final int sx1 = (getChip() != null ? getChip().getSizeX() : dvsSizeX) - 1;
            final int addrX = sx1
                    - ((packedAddr & DavisChip.XMASK) >>> DavisChip.XSHIFT);
            final int addrY = (packedAddr & DavisChip.YMASK) >>> DavisChip.YSHIFT;
            typedBuilder.addPolarity(addrX, addrY, on, currentTimestamp, packedAddr);
        }

        private void decodeStandardTypedAps(final short data) {
            if ((apsCountY[apsCurrentReadoutType] >= apsSizeY)
                    || (apsCountX[apsCurrentReadoutType] >= apsSizeX)) {
                if (warningCount % WARNING_INTERVAL == 0) {
                    CypressFX3.log.fine(
                            "APS ADC sample: row or column count is at maximum, discarding further samples.");
                }
                warningCount++;
                return;
            }

            int xPos = apsFlipX
                    ? apsSizeX - 1 - apsCountX[apsCurrentReadoutType]
                    : apsCountX[apsCurrentReadoutType];
            int yPos = apsFlipY
                    ? apsSizeY - 1 - apsCountY[apsCurrentReadoutType]
                    : apsCountY[apsCurrentReadoutType];
            if (chipID == DAViSFX3HardwareInterface.CHIP_DAVISRGB) {
                yPos += apsRGBPixelOffset;
            }
            if (apsInvertXY) {
                final int temp = xPos;
                xPos = yPos;
                yPos = temp;
            }
            yPos = apsInvertXY ? apsSizeX - 1 - yPos : apsSizeY - 1 - yPos;

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

            final boolean pixFirst = apsCountX[apsCurrentReadoutType] == 0
                    && apsCountY[apsCurrentReadoutType] == 1;
            final boolean pixLast = apsCountX[apsCurrentReadoutType] == apsSizeX - 1
                    && apsCountY[apsCurrentReadoutType] == apsSizeY;
            typedBuilder.setRollingShutter(rollingShutterFrame);
            typedBuilder.addApsSample(data & DavisChip.ADC_DATA_MASK,
                    currentTimestamp, xPos, yPos,
                    apsCurrentReadoutType == RetinaAEReader.APS_READOUT_RESET,
                    pixFirst, pixLast);
        }

        private void decodeStandardTypedMisc8(final short data) {
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
                            imuEvents[0] = assembleStandardImuWord(misc8Data);
                            if (imuFlipX) {
                                imuEvents[0] = (short) -imuEvents[0];
                            }
                            break;
                        case 3:
                            imuEvents[1] = assembleStandardImuWord(misc8Data);
                            if (imuFlipY) {
                                imuEvents[1] = (short) -imuEvents[1];
                            }
                            break;
                        case 5:
                            imuEvents[2] = assembleStandardImuWord(misc8Data);
                            if (imuFlipZ) {
                                imuEvents[2] = (short) -imuEvents[2];
                            }
                            if ((imuType & RetinaAEReader.IMU_TYPE_TEMP) == 0) {
                                imuCount += (imuType & RetinaAEReader.IMU_TYPE_GYRO) != 0
                                        ? 2 : 8;
                            }
                            break;
                        case 7:
                            imuEvents[3] = assembleStandardImuWord(misc8Data);
                            if ((imuType & RetinaAEReader.IMU_TYPE_GYRO) == 0) {
                                imuCount += 6;
                            }
                            break;
                        case 9:
                            imuEvents[4] = assembleStandardImuWord(misc8Data);
                            if (imuFlipX) {
                                imuEvents[4] = (short) -imuEvents[4];
                            }
                            break;
                        case 11:
                            imuEvents[5] = assembleStandardImuWord(misc8Data);
                            if (imuFlipY) {
                                imuEvents[5] = (short) -imuEvents[5];
                            }
                            break;
                        case 13:
                            imuEvents[6] = assembleStandardImuWord(misc8Data);
                            if (imuFlipZ) {
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
                    if ((imuType & RetinaAEReader.IMU_TYPE_ACCEL) != 0) {
                        imuCount = 0;
                    } else if ((imuType & RetinaAEReader.IMU_TYPE_TEMP) != 0) {
                        imuCount = 6;
                    } else if ((imuType & RetinaAEReader.IMU_TYPE_GYRO) != 0) {
                        imuCount = 8;
                    } else {
                        imuCount = 14;
                    }
                    break;

                default:
                    CypressFX3.log.severe("Caught Misc8 event that can't be handled.");
                    break;
            }
        }

        private void decodeStandardTypedMisc11(final short data) {
            final byte misc11Code = (byte) ((data & 0x0800) >> 11);
            switch (misc11Code) {
                case 0:
                    // APS exposure information is intentionally ignored, as in the raw parser.
                    break;
                case 1:
                    typedBuilder.patchLastPolarityAddress(data & 0x07ff);
                    break;
                default:
                    // The one-bit selector makes this unreachable; retain raw-parser diagnostics.
                    CypressFX3.log.severe("Caught Misc10 event that can't be handled.");
                    break;
            }
        }

        private short assembleStandardImuWord(final byte lowByte) {
            return (short) (((imuTmpData & 0x00FF) << 8)
                    | (lowByte & 0x00FF));
        }
    }
}
