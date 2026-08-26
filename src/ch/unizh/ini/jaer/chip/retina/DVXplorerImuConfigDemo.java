package ch.unizh.ini.jaer.chip.retina;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import eu.seebetter.ini.chips.davis.imu.IMUSample;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.CypressFX3;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.DVXplorerFX3HardwareInterface;

/**
 * Headless checks that DVXplorer Micro IMU capture/display is wired after the
 * firmware-10 SPI skip. Mini/Micro is the same VID/PID as FX3 DVXplorer
 * ({@code 152a:8419}); CX3 is {@code bcdDevice} type 4.
 * Run after {@code ant compile}:
 * {@code java -cp build/classes:jars/* ch.unizh.ini.jaer.chip.retina.DVXplorerImuConfigDemo}
 */
public final class DVXplorerImuConfigDemo {

    private static int assertions;

    private DVXplorerImuConfigDemo() {
    }

    public static void main(String[] args) throws Exception {
        testSameVidPidAsFx3();
        testImuRunIsEightByteSpi();
        testHardwarePanelHasImuTab();
        testOverlayUsesDisplayPref();
        testEp81UrbRateLimitFrom6b726b46d();
        testImuSampleDtFrom952cfae41e();
        testUsbTypedDemuxSkipsAePacketRaw();
        testUsbBufferReconfigQuiescesDvsRun();
        System.out.println("DVXPLORER_IMU ASSERTIONS=" + assertions);
        System.out.println("DVXPLORER_IMU PASS");
    }

    private static void testSameVidPidAsFx3() {
        require(DVXplorerFX3HardwareInterface.PID_FX3 == (short) 0x8419,
                "DVXplorer Mini/Micro shares PID 0x8419 with FX3");
        require(DVXplorerFX3HardwareInterface.DEVICE_TYPE_CX3_MIPI == 4,
                "CX3 Mini/Micro is bcdDevice type 4");
        require(DVXplorer.DVX_IMU == CypressFX3.FPGA_IMU,
                "MODULE_IMU is 3 (same as DAVIS / DVXplorerM)");
        require(DVXplorer.DVX_IMU_RUN_ACCELEROMETER == 2
                        && DVXplorer.DVX_IMU_RUN_GYROSCOPE == 3
                        && DVXplorer.DVX_IMU_RUN_TEMPERATURE == 4,
                "IMU_RUN_* param addresses match dv-processing DVXplorerM");
    }

    private static void testImuRunIsEightByteSpi() {
        require(DVXplorerFX3HardwareInterface.isNextGenStreamingParam(
                        CypressFX3.FPGA_DVS, DVXplorer.DVX_DVS_RUN),
                "DVS_RUN is 8-byte SPI on firmware 10+");
        require(DVXplorerFX3HardwareInterface.isNextGenStreamingParam(
                        CypressFX3.FPGA_IMU, DVXplorer.DVX_IMU_RUN_ACCELEROMETER),
                "IMU_RUN_ACCELEROMETER is 8-byte SPI (was skipped, BMI160 stayed off)");
        require(DVXplorerFX3HardwareInterface.isNextGenStreamingParam(
                        CypressFX3.FPGA_IMU, DVXplorer.DVX_IMU_RUN_GYROSCOPE),
                "IMU_RUN_GYROSCOPE is 8-byte SPI");
        require(DVXplorerFX3HardwareInterface.isNextGenStreamingParam(
                        CypressFX3.FPGA_IMU, DVXplorer.DVX_IMU_RUN_TEMPERATURE),
                "IMU_RUN_TEMPERATURE is 8-byte SPI");
        require(!DVXplorerFX3HardwareInterface.isNextGenStreamingParam(
                        CypressFX3.FPGA_DVS, DVXplorer.DVS_FLATTEN),
                "DVS_FLATTEN stays skipped (WinUSB hang)");
        require(DVXplorerFX3HardwareInterface.isNextGenStreamingParam(
                        CypressFX3.FPGA_IMU, DVXplorer.DVX_IMU_ACCEL_DATA_RATE),
                "IMU ODR/range is 8-byte SPI (c6ec5a073 skip froze gyro at ODR 5)");
        require(DVXplorerFX3HardwareInterface.isNextGenStreamingParam(
                        CypressFX3.FPGA_IMU, DVXplorer.DVX_IMU_GYRO_DATA_RATE),
                "gyro ODR 11 (800 Hz) is 8-byte SPI");
        require(DVXplorerFX3HardwareInterface.isNextGenStreamingParam(
                        CypressFX3.FPGA_IMU, DVXplorer.DVX_IMU_GYRO_RANGE),
                "gyro range 500 dps is 8-byte SPI");
    }

    private static void testHardwarePanelHasImuTab() throws Exception {
        String cfg = Files.readString(Paths.get("src", "ch", "unizh", "ini", "jaer",
                "chip", "retina", "DVXplorerConfig.java"), StandardCharsets.UTF_8);
        require(cfg.contains("addTab(\"IMU Config\""),
                "Hardware Configuration has IMU Config tab");
        require(cfg.contains("PROPERTY_IMU_ENABLED"),
                "DVXplorerConfig exposes imuEnabled");
        require(cfg.contains("PROPERTY_IMU_DISPLAY"),
                "DVXplorerConfig exposes displayImu");
        require(cfg.contains("applyImuRun()"),
                "applyToHardware sends IMU_RUN");
        String panel = Files.readString(Paths.get("src", "ch", "unizh", "ini", "jaer",
                "chip", "retina", "DVXplorerControlPanel.java"), StandardCharsets.UTF_8);
        require(panel.contains("setImuEnabled"),
                "IMU Config Enable checkbox writes imuEnabled");
        require(panel.contains("setDisplayImu"),
                "IMU Config Display checkbox writes displayImu");
    }

    private static void testOverlayUsesDisplayPref() throws Exception {
        String src = Files.readString(Paths.get("src", "ch", "unizh", "ini", "jaer",
                "chip", "retina", "DVXplorer.java"), StandardCharsets.UTF_8);
        require(src.contains("if (isDisplayImu())"),
                "canvas overlay is gated on displayImu, not a stale ToggleIMU flag");
        require(src.contains("cfg.setImuEnabled(next)"),
                "Shift-I toggles capture through DVXplorerConfig");
        require(src.contains("cfg.setDisplayImu(next)"),
                "Shift-I toggles overlay through DVXplorerConfig");
        require(src.contains("applyImuRunFromConfig()"),
                "dvxDataStart does not force IMU_RUN=1 against the panel");
        require(src.contains("drainCx3Imu(bundleImu)"),
                "Mini/Micro IMU is drained to ImuPacket, not mixed into DVS AEPacketRaw");
        require(src.contains("ADDRESS_TYPE_IMU"),
                "extractPacket only treats ADDRESS_TYPE_IMU as IMU (6b726b46d)");
        require(src.contains("DVX_IMU_GYRO_DATA_RATE, 11"),
                "next-gen defaults set gyro ODR 11 (800 Hz); ODR 5 freezes ~0 dps");
        require(src.contains("DVX_IMU_GYRO_RANGE, 2"),
                "next-gen defaults set gyro 500 dps (IMUSample 65.5 LSB/dps)");
    }

    /**
     * {@code 6b726b46d}: CX3 debug EP completes at USB poll rate (~100 kHz) with
     * the same BMI160 snapshot. One URB, resubmit at 800 Hz, drop early completions.
     */
    private static void testEp81UrbRateLimitFrom6b726b46d() throws Exception {
        require(DVXplorerFX3HardwareInterface.CX3_IMU_TRANSFER_COUNT == 1,
                "one IMU URB (was 8, which flooded EP 0x81)");
        require(DVXplorerFX3HardwareInterface.CX3_IMU_PERIOD_NS == 1_250_000L,
                "resubmit period is 1.25 ms (BMI160 800 Hz)");
        String hw = Files.readString(Paths.get("src", "net", "sf", "jaer",
                "hardwareinterface", "usb", "cypressfx3libusb",
                "DVXplorerFX3HardwareInterface.java"), StandardCharsets.UTF_8);
        int onCx3 = hw.indexOf("private void onCx3ImuTransfer");
        int schedule = hw.indexOf("private void scheduleCx3ImuResubmit");
        require(onCx3 >= 0 && schedule > onCx3,
                "onCx3ImuTransfer exists before scheduleCx3ImuResubmit");
        String callback = hw.substring(onCx3, schedule);
        require(callback.contains("scheduleCx3ImuResubmit(transfer)"),
                "completed URB is delayed, not resubmitted on the USB event thread");
        require(!callback.contains("LibUsb.submitTransfer(transfer)"),
                "onCx3ImuTransfer must not LibUsb.submitTransfer immediately (pre-6b726b46d flood)");
        require(hw.contains("waitMs = Math.max(1L, waitNs / 1_000_000L)"),
                "Windows timer rounding must not schedule a 0 ms delay");
        require(hw.contains("if (cx3ImuLastHostNs != 0 && (nowNs - cx3ImuLastHostNs) < CX3_IMU_PERIOD_NS)"),
                "writeCx3ImuSample drops URBs that still complete early");
        require(hw.contains("offerCx3Imu(new IMUSample(ts, imuEvents))"),
                "CX3 side-queue uses tracked IMUSample constructor (952cfae41e dt overlay)");
        require(!hw.contains("offerCx3Imu(IMUSample.fromRawUntracked"),
                "fromRawUntracked skips updateStatistics so overlay last dt stays 0.0ms");
    }

    /**
     * {@code 952cfae41e}: the short-array constructor must call updateStatistics
     * so the overlay {@code last dt} is not 0. Mini/Micro IMU never goes through
     * {@code constructFromAEPacketRaw} after {@code 6b726b46d}.
     */
    private static void testImuSampleDtFrom952cfae41e() throws Exception {
        String imu = Files.readString(Paths.get("src", "eu", "seebetter", "ini", "chips",
                "davis", "imu", "IMUSample.java"), StandardCharsets.UTF_8);
        int ctor = imu.indexOf("public IMUSample(final int ts, final short[] buf)");
        require(ctor >= 0, "short-array IMUSample constructor exists");
        String body = imu.substring(ctor, imu.indexOf("public static IMUSample fromRawUntracked"));
        require(body.contains("updateStatistics(ts)"),
                "952cfae41e: constructor computes deltaTimeUs for USB demux / CX3 queue");
        final short[] zeros = new short[7];
        IMUSample first = new IMUSample(1_000, zeros);
        IMUSample second = new IMUSample(2_250, zeros);
        require(second.getDeltaTimeUs() == 1_250,
                "tracked constructor sets last dt (got " + second.getDeltaTimeUs() + " us)");
        IMUSample untracked = IMUSample.fromRawUntracked(3_500, zeros);
        require(untracked.getDeltaTimeUs() == 0,
                "fromRawUntracked must not set deltaTimeUs (encode-then-decode path)");
        require(first.getTimestampUs() == 1_000 && second.getTimestampUs() == 2_250,
                "timestamps are stored on the sample");
    }

    /**
     * Live DVX USB should cook {@code PacketBundle} in the reader (no
     * {@code AEPacketRaw} → {@code extractBundle} on the default path).
     */
    private static void testUsbTypedDemuxSkipsAePacketRaw() throws Exception {
        String hw = Files.readString(Paths.get("src", "net", "sf", "jaer",
                "hardwareinterface", "usb", "cypressfx3libusb",
                "DVXplorerFX3HardwareInterface.java"), StandardCharsets.UTF_8);
        require(hw.contains("PREF_USB_TYPED_DEMUX = \"usbTypedDemux\""),
                "DVXplorerFX3 kill-switch pref is usbTypedDemux");
        require(hw.contains("UsbPolarityBundleBuilder"),
                "reader uses UsbPolarityBundleBuilder for cooked polarity");
        require(hw.contains("emitPackedPolarity"),
                "MIPI and classic FX3 emit cooked polarity from one helper");
        require(hw.contains("buffer.setNumEvents(0)"),
                "typed demux does not fill AEPacketRaw (skip intermediate)");
        require(hw.contains("drainImuIntoLiveBundle"),
                "acquireAvailablePacketBundle attaches IMU after USB polarity demux");
        require(hw.contains("offerCx3Imu(new IMUSample(currentTimestamp, imuEvents))"),
                "classic FX3 IMU End queues ImuPacket samples when demux is on");
        require("usbTypedDemux".equals(DVXplorerFX3HardwareInterface.PREF_USB_TYPED_DEMUX),
                "PREF_USB_TYPED_DEMUX constant is usbTypedDemux");
    }

    /**
     * ViewLoop pause does not stop DVS_RUN; buffer reconfig must quiesce
     * streaming before joining USBTransferThread (jAER-0.log 6:37:06).
     */
    private static void testUsbBufferReconfigQuiescesDvsRun() throws Exception {
        String fx3 = Files.readString(Paths.get("src", "net", "sf", "jaer",
                "hardwareinterface", "usb", "cypressfx3libusb",
                "CypressFX3.java"), StandardCharsets.UTF_8);
        int stop = fx3.indexOf("public boolean stopSession");
        int start = fx3.indexOf("public Config startSession");
        require(stop >= 0 && start > stop, "BufferHost stopSession exists before startSession");
        String stopBody = fx3.substring(stop, start);
        require(stopBody.contains("quiesceStreamingForUsbRestart()"),
                "stopSession must DVS_RUN=0 / quiesce before interruptAndJoin");
        require(stopBody.indexOf("quiesceStreamingForUsbRestart()")
                        < stopBody.indexOf("interruptAndJoin"),
                "quiesceStreamingForUsbRestart must run before interruptAndJoin");
        String startBody = fx3.substring(start, fx3.indexOf("public void applyIdleConfig"));
        require(startBody.contains("resumeStreamingAfterUsbRestart()"),
                "startSession must restore DVS_RUN after the new transfer thread starts");
        String hw = Files.readString(Paths.get("src", "net", "sf", "jaer",
                "hardwareinterface", "usb", "cypressfx3libusb",
                "DVXplorerFX3HardwareInterface.java"), StandardCharsets.UTF_8);
        require(hw.contains("void quiesceStreamingForUsbRestart()"),
                "DVX overrides quiesceStreamingForUsbRestart");
        require(hw.contains("chip.dvxDataStop()"),
                "DVX quiesce sends DVS_RUN=0 (ViewLoop pause does not)");
    }

    private static void require(boolean cond, String msg) {
        assertions++;
        if (!cond) {
            throw new AssertionError(msg);
        }
    }
}
