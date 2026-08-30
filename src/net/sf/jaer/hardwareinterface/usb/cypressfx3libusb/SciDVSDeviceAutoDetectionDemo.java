package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Offline checks: FPGA-geometry classifier stays on the hardware interface, but
 * AEViewer must not open USB to distinguish SciDVS from Davis346 (same VID/PID).
 */
public final class SciDVSDeviceAutoDetectionDemo {

    private static final Path HARDWARE_SOURCE = Paths.get("src", "net", "sf", "jaer",
            "hardwareinterface", "usb", "cypressfx3libusb", "DAViSFX3HardwareInterface.java");
    private static final Path CYPRESS_SOURCE = Paths.get("src", "net", "sf", "jaer",
            "hardwareinterface", "usb", "cypressfx3libusb", "CypressFX3.java");
    private static final Path VIEWER_SOURCE = Paths.get("src", "net", "sf", "jaer",
            "graphics", "AEViewer.java");
    private static int assertions;

    private SciDVSDeviceAutoDetectionDemo() {
    }

    public static void main(String[] args) throws Exception {
        testExactGeometryClassifier();
        testProbeReadsOnlyDvsGeometry();
        testProbeUsesOneNormalOpenLifecycle();
        testViewerDoesNotProbeFpgaAndListsSciDvsAsCandidate();
        testBindingInstallsReverseAssociationFirst();
        System.out.println("SCIDVS_DEVICE_AUTO_DETECTION ASSERTIONS=" + assertions);
        System.out.println("SCIDVS_DEVICE_AUTO_DETECTION PASS");
    }

    private static void testExactGeometryClassifier() throws Exception {
        Method classifier = DAViSFX3HardwareInterface.class.getDeclaredMethod(
                "matchesSciDVSFpgaGeometry", int.class, int.class);
        require(Modifier.isPublic(classifier.getModifiers())
                && Modifier.isStatic(classifier.getModifiers()),
                "geometry classifier is public static and hardware-free");
        require(invoke(classifier, 126, 112), "validated raw 126x112 geometry identifies SciDVS");

        int[][] nonSciDvs = {
            {0, 0}, {112, 126},
            {128, 128},
            {240, 180}, {180, 240},
            {346, 260}, {260, 346},
            {640, 480}, {480, 640},
            {208, 192}, {192, 208}
        };
        for (int[] geometry : nonSciDvs) {
            require(!invoke(classifier, geometry[0], geometry[1]),
                    geometry[0] + "x" + geometry[1] + " does not identify SciDVS");
        }
    }

    private static void testProbeReadsOnlyDvsGeometry() throws Exception {
        String source = Files.readString(HARDWARE_SOURCE, StandardCharsets.UTF_8);
        String probe = between(source,
                "public synchronized boolean probeSciDVSByFpgaGeometry()",
                "public static boolean matchesSciDVSFpgaGeometry");
        require(count(probe, "spiConfigReceive(") == 2,
                "probe performs exactly two FPGA reads");
        require(probe.contains("CypressFX3.FPGA_DVS, (short) 0")
                && probe.contains("CypressFX3.FPGA_DVS, (short) 1"),
                "probe reads only raw DVS X and Y geometry registers");
        require(!probe.contains("spiConfigSend(") && !probe.contains("close()"),
                "probe performs no FPGA write or explicit close");
        require(source.contains("private Boolean sciDVSFpgaGeometryMatch;"),
                "probe result is cached per hardware-interface instance");
    }

    private static void testProbeUsesOneNormalOpenLifecycle() throws Exception {
        String source = Files.readString(CYPRESS_SOURCE, StandardCharsets.UTF_8);
        String receive = between(source,
                "synchronized public ByteBuffer sendVendorRequestIN(",
                "void recoverFailedBufferReconfig(Exception cause)");
        require(receive.contains("if (!isOpen())") && receive.contains("open();"),
                "first geometry read intentionally uses the normal Cypress open path");

        String open = between(source,
                "synchronized public void open()",
                "synchronized protected void open_minimal_close()");
        int alreadyOpenGuard = open.indexOf("if (isOpen())");
        int reset = open.indexOf("LibUsb.resetDevice(deviceHandle)");
        require(alreadyOpenGuard >= 0 && reset > alreadyOpenGuard,
                "normal open is idempotent before its USB reset");
        require(open.substring(alreadyOpenGuard, reset).contains("return;"),
                "the probe's still-open interface prevents a second reset at binding");
    }

    private static void testViewerDoesNotProbeFpgaAndListsSciDvsAsCandidate() throws Exception {
        String source = Files.readString(VIEWER_SOURCE, StandardCharsets.UTF_8);
        String method = between(source,
                "public void ensureChipCompatibleWithLiveDevice(HardwareInterface hw)",
                "private Class<? extends AEChip> loadRememberedLiveChip");
        require(!method.contains("probeSciDVSByFpgaGeometry()"),
                "ensureChip must not open USB to distinguish SciDVS from Davis346");
        require(!method.contains("found.remove(SciDVS.class)"),
                "SciDVS stays in the shared-VID/PID candidate list");
        require(method.contains("loadRememberedLiveChip("),
                "remembered AEChip is still applied before the chooser");
        require(method.contains("if (currentIsMatch)")
                && method.indexOf("if (currentIsMatch)") < method.indexOf("showOptionDialog"),
                "current matching AEChip skips the Davis346/SciDVS chooser");
        require(method.contains("Davis346 red vs blue vs SciDVS"),
                "chooser copy lists SciDVS as a same-VID/PID camera");
        require(source.contains("SciDVS.class.getName()"),
                "SciDVS is in DEFAULT_CHIP_CLASS_NAMES so leftover Customize menus include it");
    }

    private static void testBindingInstallsReverseAssociationFirst() throws Exception {
        String source = Files.readString(VIEWER_SOURCE, StandardCharsets.UTF_8);
        String bind = between(source,
                "private void bindLiveHardwareIfCompatible(HardwareInterface hw, String logPrefix)",
                "public boolean ensureChipCompatibleWithRecording(File file)");
        int reverseAssociation = bind.indexOf(".setChip(chip)");
        int forwardAssociation = bind.indexOf("chip.setHardwareInterface(hw)");
        require(reverseAssociation >= 0 && forwardAssociation > reverseAssociation,
                "live binding installs the monitor's chip association before bias-generator binding");
    }

    private static boolean invoke(Method method, int x, int y) throws Exception {
        return (Boolean) method.invoke(null, x, y);
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        require(from >= 0, "source contains start marker: " + start);
        int to = source.indexOf(end, from + start.length());
        require(to > from, "source contains end marker: " + end);
        return source.substring(from, to);
    }

    private static int count(String text, String needle) {
        int result = 0;
        int from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) {
            result++;
            from += needle.length();
        }
        return result;
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
