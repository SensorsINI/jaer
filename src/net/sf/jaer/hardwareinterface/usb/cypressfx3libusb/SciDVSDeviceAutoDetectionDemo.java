package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import eu.seebetter.ini.chips.davis.Davis346blue;
import eu.seebetter.ini.chips.davis.Davis346red;
import eu.seebetter.ini.chips.davis.SciDVS;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/** Offline and behavioral acceptance checks for SciDVS auto-detection. */
public final class SciDVSDeviceAutoDetectionDemo {

    private static final Path HARDWARE_SOURCE = Paths.get("src", "net", "sf", "jaer",
            "hardwareinterface", "usb", "cypressfx3libusb", "DAViSFX3HardwareInterface.java");
    private static final Path CYPRESS_SOURCE = Paths.get("src", "net", "sf", "jaer",
            "hardwareinterface", "usb", "cypressfx3libusb", "CypressFX3.java");
    private static final Path VIEWER_SOURCE = Paths.get("src", "net", "sf", "jaer",
            "graphics", "AEViewer.java");
    private static final String REMEMBERED_PREFIX = "AEViewer.liveChipOffer.chip.";
    private static final String DEFAULT_PREFIX = "AEViewer.liveChipOffer.default.";
    private static final String BROAD_KEY = "152a:841a";
    private static final String SERIAL_KEY = BROAD_KEY + "#SCIDVS-TEST";
    private static int assertions;
    private static int behavioralAssertions;
    private static boolean countBehavioralAssertions;

    private SciDVSDeviceAutoDetectionDemo() {
    }

    public static void main(String[] args) throws Exception {
        testExactGeometryClassifier();
        testProbeReadsOnlyDvsGeometry();
        testProbeUsesOneNormalOpenLifecycle();
        countBehavioralAssertions = true;
        testOffEdtPositiveGeometryOverridesBroadRemembered();
        testEdtGateDefersAllInjectedIo();
        testOtherGeometryRetainsDavisAndUsesRemembered();
        testProbeFailurePreservesFallback();
        testStartupAmbiguitySkipsPreferenceRead();
        countBehavioralAssertions = false;
        testViewerUsesProductionDecisionSeam();
        testBindingInstallsReverseAssociationFirst();
        System.out.println("SCIDVS_DEVICE_AUTO_DETECTION ASSERTIONS=" + assertions);
        System.out.println("SCIDVS_DEVICE_AUTO_DETECTION BEHAVIORAL_ASSERTIONS="
                + behavioralAssertions);
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

    private static void testOffEdtPositiveGeometryOverridesBroadRemembered() {
        MemoryPreferences prefs = new MemoryPreferences();
        prefs.put(REMEMBERED_PREFIX + BROAD_KEY, Davis346blue.class.getName());
        prefs.put(DEFAULT_PREFIX + BROAD_KEY, Davis346red.class.getName());
        prefs.put(REMEMBERED_PREFIX + SERIAL_KEY, Davis346red.class.getName());
        prefs.put("unrelated.preference", "keep");
        AtomicInteger probeCalls = new AtomicInteger();
        AtomicInteger identityRefreshCalls = new AtomicInteger();
        List<Class<? extends AEChip>> candidates = sharedCandidates();

        SciDVSReplugDecision.Result result = SciDVSReplugDecision.resolve(
                true, false, candidates, SciDVS.class,
                () -> {
                    probeCalls.incrementAndGet();
                    return DAViSFX3HardwareInterface.matchesSciDVSFpgaGeometry(126, 112);
                },
                () -> {
                    identityRefreshCalls.incrementAndGet();
                    return SERIAL_KEY;
                },
                BROAD_KEY, BROAD_KEY, prefs, REMEMBERED_PREFIX, DEFAULT_PREFIX);

        require(probeCalls.get() == 1, "off-EDT shared identity executes one geometry probe");
        require(identityRefreshCalls.get() == 1,
                "successful probe refreshes the serial-qualified device key once");
        require(result.selectionReason()
                == SciDVSReplugDecision.SelectionReason.DEFINITIVE_SCIDVS,
                "positive geometry wins before stale remembered DAVIS fallback");
        require(result.selectedChip() == SciDVS.class, "positive geometry selects SciDVS");
        require(result.chooserSuppressed(), "definitive SciDVS selection suppresses chooser");
        require(!result.ordinaryFallbackRan(),
                "definitive SciDVS selection returns before remembered fallback");
        require(result.deviceKey().equals(SERIAL_KEY),
                "post-open serial-qualified key replaces the broad key");
        require(result.candidates().equals(candidates),
                "positive geometry does not discard any candidate before direct selection");
        require(!prefs.contains(REMEMBERED_PREFIX + BROAD_KEY),
                "conflicting broad remembered DAVIS mapping is deleted");
        require(!prefs.contains(DEFAULT_PREFIX + BROAD_KEY),
                "conflicting broad DAVIS dialog default is deleted");
        require(Davis346red.class.getName().equals(
                prefs.getWithoutCounting(REMEMBERED_PREFIX + SERIAL_KEY)),
                "serial-qualified mapping is preserved");
        require("keep".equals(prefs.getWithoutCounting("unrelated.preference")),
                "unrelated preference is preserved");
        require(prefs.removed.equals(Arrays.asList(
                REMEMBERED_PREFIX + BROAD_KEY, DEFAULT_PREFIX + BROAD_KEY)),
                "only the two conflicting broad preferences are removed");

        MemoryPreferences matching = new MemoryPreferences();
        matching.put(REMEMBERED_PREFIX + BROAD_KEY, SciDVS.class.getName());
        matching.put(DEFAULT_PREFIX + BROAD_KEY, SciDVS.class.getName());
        SciDVSReplugDecision.resolve(true, false, candidates, SciDVS.class,
                () -> true, () -> SERIAL_KEY, BROAD_KEY, BROAD_KEY,
                matching, REMEMBERED_PREFIX, DEFAULT_PREFIX);
        require(matching.removed.isEmpty(),
                "broad preferences already naming definitive SciDVS are not deleted");
    }

    private static void testEdtGateDefersAllInjectedIo() throws Exception {
        MemoryPreferences prefs = new MemoryPreferences();
        prefs.put(REMEMBERED_PREFIX + BROAD_KEY, Davis346blue.class.getName());
        prefs.put(DEFAULT_PREFIX + BROAD_KEY, Davis346red.class.getName());
        Map<String, String> before = prefs.snapshot();
        AtomicInteger geometryProbeCalls = new AtomicInteger();
        AtomicInteger openCalls = new AtomicInteger();
        AtomicInteger enumerationCalls = new AtomicInteger();
        AtomicInteger identityRefreshCalls = new AtomicInteger();
        AtomicReference<SciDVSReplugDecision.Result> resultRef = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            require(SwingUtilities.isEventDispatchThread(),
                    "EDT scenario executes on the real Swing event-dispatch thread");
            resultRef.set(SciDVSReplugDecision.resolve(
                    true, SwingUtilities.isEventDispatchThread(), sharedCandidates(), SciDVS.class,
                    () -> {
                        geometryProbeCalls.incrementAndGet();
                        openCalls.incrementAndGet();
                        enumerationCalls.incrementAndGet();
                        return true;
                    },
                    () -> {
                        identityRefreshCalls.incrementAndGet();
                        return SERIAL_KEY;
                    },
                    BROAD_KEY, BROAD_KEY, prefs, REMEMBERED_PREFIX, DEFAULT_PREFIX));
        });

        SciDVSReplugDecision.Result immediate = resultRef.get();
        require(geometryProbeCalls.get() == 0, "EDT gate performs no geometry probe");
        require(openCalls.get() == 0, "EDT gate performs no injected device open");
        require(enumerationCalls.get() == 0, "EDT gate performs no injected enumeration");
        require(identityRefreshCalls.get() == 0,
                "EDT gate performs no post-open serial identity refresh");
        require(!immediate.probeAttempted(), "EDT result records that no probe was attempted");
        require(immediate.ordinaryFallbackRan(), "EDT result runs immediate ordinary fallback");
        require(immediate.selectedChip() == Davis346blue.class,
                "EDT fallback may safely use the current broad remembered DAVIS choice");
        require(immediate.candidates().equals(sharedCandidates()),
                "EDT fallback preserves all shared-PID candidates");
        require(prefs.snapshot().equals(before), "EDT fallback leaves preferences unchanged");

        SciDVSReplugDecision.Result later = SciDVSReplugDecision.resolve(
                true, false, sharedCandidates(), SciDVS.class,
                () -> {
                    geometryProbeCalls.incrementAndGet();
                    openCalls.incrementAndGet();
                    enumerationCalls.incrementAndGet();
                    return true;
                },
                () -> {
                    identityRefreshCalls.incrementAndGet();
                    return SERIAL_KEY;
                },
                BROAD_KEY, BROAD_KEY, prefs, REMEMBERED_PREFIX, DEFAULT_PREFIX);
        require(later.selectedChip() == SciDVS.class,
                "later off-EDT discovery definitively corrects the immediate DAVIS fallback");
        require(geometryProbeCalls.get() == 1 && openCalls.get() == 1
                && enumerationCalls.get() == 1 && identityRefreshCalls.get() == 1,
                "deferred injected I/O executes only during the later off-EDT pass");
        require(!prefs.contains(REMEMBERED_PREFIX + BROAD_KEY)
                && !prefs.contains(DEFAULT_PREFIX + BROAD_KEY),
                "later definitive pass invalidates conflicting broad choices");
    }

    private static void testOtherGeometryRetainsDavisAndUsesRemembered() {
        MemoryPreferences prefs = new MemoryPreferences();
        prefs.put(REMEMBERED_PREFIX + BROAD_KEY, Davis346blue.class.getName());
        prefs.put(REMEMBERED_PREFIX + SERIAL_KEY, Davis346red.class.getName());
        prefs.put(DEFAULT_PREFIX + BROAD_KEY, Davis346blue.class.getName());
        Map<String, String> before = prefs.snapshot();
        AtomicInteger probeCalls = new AtomicInteger();
        AtomicInteger identityRefreshCalls = new AtomicInteger();

        SciDVSReplugDecision.Result result = SciDVSReplugDecision.resolve(
                true, false, sharedCandidates(), SciDVS.class,
                () -> {
                    probeCalls.incrementAndGet();
                    return DAViSFX3HardwareInterface.matchesSciDVSFpgaGeometry(346, 260);
                },
                () -> {
                    identityRefreshCalls.incrementAndGet();
                    return SERIAL_KEY;
                },
                BROAD_KEY, BROAD_KEY, prefs, REMEMBERED_PREFIX, DEFAULT_PREFIX);

        require(result.probeAttempted() && result.probeSucceeded(),
                "other geometry is a successful definitive probe");
        require(!result.sciDVSGeometry(), "346x260 geometry is not SciDVS");
        require(result.candidates().equals(Arrays.asList(
                Davis346blue.class, Davis346red.class)),
                "other geometry removes only SciDVS and retains all DAVIS candidates");
        require(result.selectedChip() == Davis346red.class,
                "fallback uses the valid serial-qualified remembered DAVIS mapping");
        require(result.selectionReason() == SciDVSReplugDecision.SelectionReason.REMEMBERED,
                "non-SciDVS result records remembered fallback selection");
        require(result.ordinaryFallbackRan(), "non-SciDVS result runs ordinary fallback");
        require(result.chooserSuppressed(), "valid remembered DAVIS mapping suppresses chooser");
        require(result.deviceKey().equals(SERIAL_KEY),
                "remembered lookup migrates to the post-open serial-qualified key");
        require(probeCalls.get() == 1 && identityRefreshCalls.get() == 1,
                "other geometry probes and refreshes identity exactly once");
        require(prefs.snapshot().equals(before),
                "successful non-SciDVS geometry does not delete preferences");
    }

    private static void testProbeFailurePreservesFallback() {
        MemoryPreferences prefs = new MemoryPreferences();
        prefs.put(REMEMBERED_PREFIX + SERIAL_KEY, Davis346red.class.getName());
        prefs.put(DEFAULT_PREFIX + BROAD_KEY, Davis346blue.class.getName());
        prefs.put("unrelated.preference", "keep");
        Map<String, String> before = prefs.snapshot();
        HardwareInterfaceException failure = new HardwareInterfaceException("injected geometry failure");
        AtomicInteger identityRefreshCalls = new AtomicInteger();

        SciDVSReplugDecision.Result result = SciDVSReplugDecision.resolve(
                true, false, sharedCandidates(), SciDVS.class,
                () -> {
                    throw failure;
                },
                () -> {
                    identityRefreshCalls.incrementAndGet();
                    return SERIAL_KEY;
                },
                BROAD_KEY, BROAD_KEY, prefs, REMEMBERED_PREFIX, DEFAULT_PREFIX);

        require(result.probeAttempted() && !result.probeSucceeded(),
                "probe exception is recorded as attempted but unsuccessful");
        require(result.probeFailure() == failure, "exact probe exception is retained");
        require(result.candidates().equals(sharedCandidates()),
                "probe exception preserves every candidate");
        require(prefs.snapshot().equals(before), "probe exception preserves all preferences");
        require(prefs.removed.isEmpty(), "probe exception performs no preference deletion");
        require(result.ordinaryFallbackRan(), "probe exception runs ordinary fallback");
        require(result.selectedChip() == Davis346red.class,
                "ordinary fallback accepts a valid remembered DAVIS mapping");
        require(result.chooserSuppressed(),
                "valid remembered mapping suppresses chooser after probe failure");
        require(result.deviceKey().equals(SERIAL_KEY) && identityRefreshCalls.get() == 1,
                "failed post-open probe refreshes serial identity before fallback");

        MemoryPreferences noRememberedPreference = new MemoryPreferences();
        SciDVSReplugDecision.Result chooserFallback = SciDVSReplugDecision.resolve(
                true, false, sharedCandidates(), SciDVS.class,
                () -> {
                    throw failure;
                },
                () -> SERIAL_KEY,
                BROAD_KEY, BROAD_KEY, noRememberedPreference,
                REMEMBERED_PREFIX, DEFAULT_PREFIX);
        require(chooserFallback.selectedChip() == null
                && !chooserFallback.chooserSuppressed(),
                "probe failure suppresses the chooser only when fallback finds a valid remembered mapping");
        require(chooserFallback.candidates().equals(sharedCandidates())
                && noRememberedPreference.snapshot().isEmpty(),
                "chooser fallback after probe failure preserves candidates and empty preferences");
    }

    private static void testStartupAmbiguitySkipsPreferenceRead() {
        MemoryPreferences prefs = new MemoryPreferences();
        prefs.put(REMEMBERED_PREFIX + BROAD_KEY, Davis346blue.class.getName());
        Map<String, String> before = prefs.snapshot();

        SciDVSReplugDecision.StartupResult ambiguous = SciDVSReplugDecision.resolveStartup(
                true, sharedCandidates(), SciDVS.class,
                prefs, REMEMBERED_PREFIX, BROAD_KEY);
        require(ambiguous.skippedSharedPidAmbiguity(),
                "startup marks shared DAVIS/SciDVS identity as deferred");
        require(ambiguous.selectedChip() == null,
                "startup does not apply the broad remembered DAVIS mapping");
        require(prefs.getCalls == 0,
                "startup shared-PID ambiguity skips preference lookup entirely");
        require(prefs.snapshot().equals(before),
                "startup deferral leaves the broad mapping untouched for later fingerprinting");

        SciDVSReplugDecision.StartupResult unique = SciDVSReplugDecision.resolveStartup(
                true, List.of(Davis346blue.class), SciDVS.class,
                prefs, REMEMBERED_PREFIX, BROAD_KEY);
        require(!unique.skippedSharedPidAmbiguity(),
                "unique candidate retains startup optimization");
        require(unique.selectedChip() == Davis346blue.class,
                "unique candidate may use its valid remembered startup mapping");
        require(prefs.getCalls == 1, "unique startup mapping performs one preference lookup");
    }

    private static void testViewerUsesProductionDecisionSeam() throws Exception {
        String source = Files.readString(VIEWER_SOURCE, StandardCharsets.UTF_8);
        String ensure = between(source,
                "public void ensureChipCompatibleWithLiveDevice(HardwareInterface hw)",
                "private SciDVSReplugDecision.PreferenceStore liveChipPreferenceStore()");
        require(ensure.contains("SciDVSReplugDecision.resolve("),
                "AEViewer live compatibility delegates to the behaviorally tested decision seam");
        require(ensure.contains("SwingUtilities.isEventDispatchThread()"),
                "AEViewer passes the real EDT state into the production gate");
        require(ensure.contains("decision.selectionReason()"),
                "AEViewer applies the production decision result");

        String startup = between(source,
                "private void maybeUseRememberedLiveChipAtStartup()",
                "protected void setInputFile(File f)");
        require(startup.contains("SciDVSReplugDecision.resolveStartup("),
                "AEViewer startup delegates ambiguity and remembered selection to the tested seam");
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

    private static List<Class<? extends AEChip>> sharedCandidates() {
        return Arrays.asList(SciDVS.class, Davis346blue.class, Davis346red.class);
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
        if (countBehavioralAssertions) {
            behavioralAssertions++;
        }
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class MemoryPreferences
            implements SciDVSReplugDecision.PreferenceStore {

        private final LinkedHashMap<String, String> values = new LinkedHashMap<>();
        private final ArrayList<String> removed = new ArrayList<>();
        private int getCalls;

        void put(String key, String value) {
            values.put(key, value);
        }

        boolean contains(String key) {
            return values.containsKey(key);
        }

        String getWithoutCounting(String key) {
            return values.get(key);
        }

        Map<String, String> snapshot() {
            return new LinkedHashMap<>(values);
        }

        @Override
        public String get(String key) {
            getCalls++;
            return values.get(key);
        }

        @Override
        public void remove(String key) {
            removed.add(key);
            values.remove(key);
        }
    }
}
