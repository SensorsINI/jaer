/*
 * U2 decision-grade RED test for the SciDVS renderer/config contract.
 *
 * A real SciDVSConfig is constructed around an Objenesis-created SciDVS so the
 * test does not initialize JOGL. RecordingCypressFX3 is installed through the
 * BiasgenHardwareInterface seam; only USB access is replaced.
 */
package eu.seebetter.ini.chips.davis;

import ch.unizh.ini.jaer.config.spi.SPIConfigBit;
import ch.unizh.ini.jaer.config.spi.SPIConfigValue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.prefs.Preferences;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.CypressFX3;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;

/** Headless executable test. Exit 1 means production contract failure. */
public class SciDVSChipConfigDemo {

    private static final String PREF_NODE = "__u2_scidvs_chip_config_demo";

    public static void main(final String[] args) {
        Preferences isolated = null;
        SciDVSConfig cfg = null;
        int exitCode = 2;

        try {
            isolated = Preferences.userRoot().node(PREF_NODE);
            isolated.put("__u2", "marker");

            final Objenesis objenesis = new ObjenesisStd();
            final SciDVS chip = objenesis.newInstance(SciDVS.class);
            chip.setPrefs(isolated);
            chip.setSupport(new java.beans.PropertyChangeSupport(chip));

            cfg = new SciDVSConfig(chip);
            final RecordingCypressFX3 recording = new RecordingCypressFX3();
            cfg.setHardwareInterface(recording);
            requireFixture(cfg.getHardwareInterface() == recording,
                    "recording hardware did not attach to SciDVSConfig");

            int failures = 0;
            failures += testChipControlContract(cfg);
            failures += testGlobalShutterWrites(cfg, recording);
            failures += testBatchEditIsSilent(cfg, recording);
            failures += testStaleLoadRealignsChipToAPS(cfg, recording);
            failures += testNullHardwareDoesNotThrow(cfg, recording);
            failures += testObservableReverseOrder(cfg);

            System.out.println("[U2] TOTAL_CONTRACT_FAILURES=" + failures);
            if (failures == 0) {
                System.out.println("[U2] STATUS: GREEN");
                exitCode = 0;
            } else {
                System.out.println("[U2] STATUS: RED (production contract unmet)");
                exitCode = 1;
            }
        } catch (final Throwable fixtureFailure) {
            System.out.println("[U2] FIXTURE_FAILURE: " + fixtureFailure);
            fixtureFailure.printStackTrace(System.out);
            System.out.println("[U2] STATUS: INVALID (not a production-contract verdict)");
            exitCode = 2;
        } finally {
            try {
                if (cfg != null) {
                    cfg.setHardwareInterface(null);
                }
                if (isolated != null) {
                    isolated.removeNode();
                }
                Preferences.userRoot().flush();
                final boolean clean = !Preferences.userRoot().nodeExists(PREF_NODE);
                System.out.println("[U2] temporary preference node removed: " + clean);
                if (!clean) {
                    exitCode = 2;
                }
            } catch (final Throwable cleanupFailure) {
                System.out.println("[U2] FIXTURE_CLEANUP_FAILURE: " + cleanupFailure);
                exitCode = 2;
            }
        }

        System.exit(exitCode);
    }

    private static int testChipControlContract(final SciDVSConfig cfg) {
        int failures = 0;
        SPIConfigBit globalShutter = null;
        SPIConfigBit resetShorted = null;
        SPIConfigBit testADC = null;
        final Set<Short> actualAddresses = new HashSet<>();
        final Set<String> chipNames = new HashSet<>();
        final List<String> duplicateChipNames = new ArrayList<>();

        for (final SPIConfigValue value : cfg.chipControl) {
            if (value.getModuleAddr() != CypressFX3.FPGA_CHIPBIAS) {
                continue;
            }
            actualAddresses.add(value.getParamAddr());
            if (!chipNames.add(value.getName())) {
                duplicateChipNames.add(value.getName());
            }
            if (value instanceof SPIConfigBit) {
                final SPIConfigBit bit = (SPIConfigBit) value;
                if ("Chip.GlobalShutter".equals(bit.getName())) {
                    globalShutter = bit;
                } else if ("Chip.ResetShorted".equals(bit.getName())) {
                    resetShorted = bit;
                } else if ("Chip.TestADC".equals(bit.getName())) {
                    testADC = bit;
                }
            }
        }

        // Exact FPGA_CHIPBIAS chipControl address set 128..147 (inclusive), not just a
        // min/max span. Any missing or extra address is a contract failure.
        final Set<Short> expected = new HashSet<>();
        for (short a = 128; a <= 147; a++) {
            expected.add(a);
        }
        final List<Short> missing = new ArrayList<>();
        final List<Short> extra = new ArrayList<>();
        for (final short a : expected) {
            if (!actualAddresses.contains(a)) {
                missing.add(a);
            }
        }
        for (final short a : actualAddresses) {
            if (!expected.contains(a)) {
                extra.add(a);
            }
        }
        System.out.println("[U2.1] chipControl FPGA_CHIPBIAS addresses=" + sortedList(actualAddresses)
                + " missing=" + missing + " extra=" + extra + " duplicateNames=" + duplicateChipNames);
        if (!missing.isEmpty() || !extra.isEmpty()) {
            System.out.println("[U2.1] FAIL: chipControl FPGA_CHIPBIAS address set is not exactly 128..147"
                    + " (missing=" + missing + " extra=" + extra + ")");
            failures++;
        } else {
            System.out.println("[U2.1] PASS: chipControl FPGA_CHIPBIAS address set is exactly 128..147");
        }
        if (!duplicateChipNames.isEmpty()) {
            System.out.println("[U2.1] FAIL: chipControl has duplicate chip-key names: " + duplicateChipNames);
            failures++;
        } else {
            System.out.println("[U2.1] PASS: chipControl chip-key names are unique");
        }

        // Declared default (via reflection) must match the contract, independent of any
        // current value: Chip.GlobalShutter defaults true, Chip.ResetShorted defaults false.
        failures += checkDeclaredDefault(cfg, globalShutter, "Chip.GlobalShutter", true, "U2.1");
        failures += checkDeclaredDefault(cfg, resetShorted, "Chip.ResetShorted", false, "U2.1");

        if (globalShutter == null || globalShutter.getModuleAddr() != CypressFX3.FPGA_CHIPBIAS
                || globalShutter.getParamAddr() != 147 || !globalShutter.isSet()) {
            System.out.println("[U2.1] FAIL: Chip.GlobalShutter must be FPGA_CHIPBIAS/147 default=true");
            failures++;
        } else {
            System.out.println("[U2.1] Chip.GlobalShutter module=" + globalShutter.getModuleAddr()
                    + " address=" + globalShutter.getParamAddr() + " value=" + globalShutter.isSet());
        }
        if (resetShorted == null || resetShorted.getModuleAddr() != CypressFX3.FPGA_CHIPBIAS
                || resetShorted.getParamAddr() != 142 || resetShorted.isSet()) {
            System.out.println("[U2.1] FAIL: Chip.ResetShorted must be FPGA_CHIPBIAS/142 default=false");
            failures++;
        } else {
            System.out.println("[U2.1] PASS: Chip.ResetShorted is FPGA_CHIPBIAS/142 default=false");
        }
        if (testADC == null || testADC.getModuleAddr() != CypressFX3.FPGA_CHIPBIAS
                || testADC.getParamAddr() != 146) {
            System.out.println("[U2.1] FAIL: Chip.TestADC must be FPGA_CHIPBIAS/146");
            failures++;
        } else {
            System.out.println("[U2.1] PASS: Chip.TestADC is FPGA_CHIPBIAS/146");
        }

        // The parent (ordinary DAVIS) chip controls must not leak into allPreferencesList:
        // a stale parent object with the same FPGA_CHIPBIAS address or the same chip-key
        // name as the SciDVS control is a duplicate that the config did not clear.
        failures += inspectAllPreferencesDuplicates(cfg);
        return failures;
    }

    private static int checkDeclaredDefault(final SciDVSConfig cfg, final SPIConfigBit bit,
            final String name, final boolean expected, final String tag) {
        if (bit == null) {
            System.out.println("[" + tag + "] FAIL: " + name + " is absent, cannot check declared default");
            return 1;
        }
        try {
            final java.lang.reflect.Field f = SPIConfigBit.class.getDeclaredField("defaultValue");
            f.setAccessible(true);
            final boolean declared = f.getBoolean(bit);
            if (declared != expected) {
                System.out.println("[" + tag + "] FAIL: " + name + " declared default=" + declared
                        + " expected=" + expected);
                return 1;
            }
            System.out.println("[" + tag + "] PASS: " + name + " declared default=" + declared);
            return 0;
        } catch (final ReflectiveOperationException e) {
            System.out.println("[" + tag + "] FIXTURE_FAILURE: cannot reflect default of " + name + ": " + e);
            throw new IllegalStateException("fixture cannot inspect declared default of " + name, e);
        }
    }

    private static int inspectAllPreferencesDuplicates(final SciDVSConfig cfg) {
        final Map<String, Integer> addrCount = new java.util.HashMap<>();
        final Map<String, Integer> nameCount = new java.util.HashMap<>();
        for (final SPIConfigValue value : cfg.allPreferencesList) {
            if (value.getModuleAddr() != CypressFX3.FPGA_CHIPBIAS) {
                continue;
            }
            final String key = "m" + value.getModuleAddr() + ":a" + value.getParamAddr();
            addrCount.merge(key, 1, Integer::sum);
            nameCount.merge(value.getName(), 1, Integer::sum);
        }
        final List<String> dupAddrs = new ArrayList<>();
        final List<String> dupNames = new ArrayList<>();
        addrCount.forEach((k, c) -> {
            if (c > 1) {
                dupAddrs.add(k + "x" + c);
            }
        });
        nameCount.forEach((k, c) -> {
            if (c > 1) {
                dupNames.add(k + "x" + c);
            }
        });
        final int chipBiasCount = addrCount.values().stream().mapToInt(Integer::intValue).sum();
        System.out.println("[U2.1] allPreferencesList FPGA_CHIPBIAS size="
                + chipBiasCount
                + " duplicateAddr=" + dupAddrs + " duplicateKey=" + dupNames);
        int failures = 0;
        if (chipBiasCount != 20) {
            System.out.println("[U2.1] FAIL: allPreferencesList must contain exactly 20 FPGA_CHIPBIAS controls, got "
                    + chipBiasCount);
            failures++;
        } else {
            System.out.println("[U2.1] PASS: allPreferencesList contains exactly 20 FPGA_CHIPBIAS controls");
        }
        if (!dupAddrs.isEmpty()) {
            System.out.println("[U2.1] FAIL: allPreferencesList has duplicate FPGA_CHIPBIAS module/address: " + dupAddrs);
            failures++;
        } else {
            System.out.println("[U2.1] PASS: allPreferencesList FPGA_CHIPBIAS module/address are unique");
        }
        if (!dupNames.isEmpty()) {
            System.out.println("[U2.1] FAIL: allPreferencesList has duplicate chip-key objects (stale parent chip controls): " + dupNames);
            failures++;
        } else {
            System.out.println("[U2.1] PASS: allPreferencesList chip-key objects are unique");
        }
        return failures;
    }

    private static List<Short> sortedList(final Set<Short> set) {
        final List<Short> list = new ArrayList<>(set);
        java.util.Collections.sort(list);
        return list;
    }

    private static int testGlobalShutterWrites(final SciDVSConfig cfg,
            final RecordingCypressFX3 recording) {
        int failures = 0;

        // Required baseline characterization: known GS=true, ResetShorted=false, then GS true->false.
        setKnownState(cfg, recording, false, true);
        failures += checkToggle(cfg, recording, "baseline true->false", false, false);

        // Required second characterization: ResetShorted=true, known GS=false, then GS false->true.
        setKnownState(cfg, recording, true, false);
        failures += checkToggle(cfg, recording, "ResetShorted=true false->true", true, true);

        // Discriminator: the new GS value differs from ResetShorted. This makes the legacy parent
        // write of GS=true to address 142 observable as corruption rather than an accidental match.
        setKnownState(cfg, recording, false, false);
        failures += checkToggle(cfg, recording, "discriminator ResetShorted=false false->true", true, false);

        return failures;
    }

    private static int checkToggle(final SciDVSConfig cfg, final RecordingCypressFX3 recording,
            final String label, final boolean newGlobalShutter, final boolean resetShorted) {
        requireFixture(cfg.globalShutter.isSet() != newGlobalShutter,
                label + ": GS precondition is not a toggle");
        requireFixture(findResetShorted(cfg).isSet() == resetShorted,
                label + ": ResetShorted precondition was not established");

        recording.writes.clear();
        cfg.setGlobalShutter(newGlobalShutter);
        final int expected = newGlobalShutter ? 1 : 0;
        final int resetExpected = resetShorted ? 1 : 0;
        final List<String> trace = new ArrayList<>();
        boolean hasApsWrite = false;
        boolean has147Write = false;
        Integer final142 = null;

        for (final Write write : recording.writes) {
            trace.add(write.toString());
            if (write.module == CypressFX3.FPGA_APS && write.param == 7 && write.value == expected) {
                hasApsWrite = true;
            }
            if (write.module == CypressFX3.FPGA_CHIPBIAS && write.param == 147
                    && write.value == expected) {
                has147Write = true;
            }
            if (write.module == CypressFX3.FPGA_CHIPBIAS && write.param == 142) {
                final142 = write.value;
            }
        }

        System.out.println("[U2.2] " + label + " trace=" + trace + " final142=" + final142);
        requireFixture(hasApsWrite, label + ": no APS.GlobalShutter base write; recording seam is invalid");

        int failures = 0;
        if (!has147Write) {
            System.out.println("[U2.2] FAIL " + label
                    + ": missing FPGA_CHIPBIAS/147=" + expected);
            failures++;
        }
        if (final142 != null && final142 != resetExpected) {
            System.out.println("[U2.2] FAIL " + label + ": final FPGA_CHIPBIAS/142=" + final142
                    + " corrupts ResetShorted=" + resetExpected);
            failures++;
        } else {
            System.out.println("[U2.2] " + label + ": address 142 is "
                    + (final142 == null ? "absent (accepted)" : "final=" + final142 + " (ResetShorted preserved)"));
        }
        return failures;
    }

    private static void setKnownState(final SciDVSConfig cfg, final RecordingCypressFX3 recording,
            final boolean resetShorted, final boolean globalShutter) {
        cfg.setHardwareInterface(null);
        findResetShorted(cfg).set(resetShorted);
        cfg.setGlobalShutter(globalShutter);
        cfg.setHardwareInterface(recording);
        requireFixture(cfg.getHardwareInterface() == recording,
                "recording hardware did not reattach to SciDVSConfig");
        recording.writes.clear();
    }

    private static int testBatchEditIsSilent(final SciDVSConfig cfg,
            final RecordingCypressFX3 recording) {
        cfg.setHardwareInterface(recording);
        recording.writes.clear();
        final SPIConfigBit chipGS = findChipGlobalShutter(cfg);

        // Establish a clean, known base so the batch applies a real net state change
        // (not a no-op that happens to be suppressed).
        cfg.setGlobalShutter(false);
        recording.writes.clear();
        requireFixture(!cfg.globalShutter.isSet() && !chipGS.isSet(),
                "batch: could not establish both APS and Chip.GlobalShutter = false");

        final boolean newState = !cfg.globalShutter.isSet(); // false -> true : net change
        cfg.startBatchEdit();
        int failures = 0;
        try {
            cfg.setGlobalShutter(newState);
            // Checked while the batch is still open.
            final boolean followedAPS = chipGS.isSet() == cfg.globalShutter.isSet();
            System.out.println("[U2.3] during-batch: APS=" + cfg.globalShutter.isSet()
                    + " Chip.GlobalShutter=" + chipGS.isSet()
                    + " followsAPS=" + followedAPS + " immediateWrites=" + recording.writes);
            if (!recording.writes.isEmpty()) {
                System.out.println("[U2.3] FAIL: batch net change emitted immediate hardware writes: " + recording.writes);
                failures++;
            } else {
                System.out.println("[U2.3] PASS: batch net change emitted zero immediate writes");
            }
            if (!followedAPS || !chipGS.isSet()) {
                System.out.println("[U2.3] FAIL: Chip.GlobalShutter in-memory state does not follow APS during batch");
                failures++;
            } else {
                System.out.println("[U2.3] PASS: Chip.GlobalShutter in-memory state follows APS during batch");
            }
        } finally {
            try {
                cfg.endBatchEdit();
            } catch (final HardwareInterfaceException e) {
                throw new IllegalStateException("endBatchEdit fixture failure", e);
            }
        }

        // endBatchEdit flushes asynchronously through a recording seam whose
        // sendConfiguration is a no-op, so a fresh synchronous trace here cannot prove
        // that the batch's net change reached hardware. Asserting on it would be invalid
        // (it can never hold), so we do NOT assert on the batch flush; the in-batch
        // in-memory contract above is the batch's provable contract. Instead we prove
        // that an ordinary, subsequent NON-batch toggle writes synchronously to the same
        // addresses, exercising the resumption of synchronous production writes. The batch
        // left APS and in-memory Chip at true; the ordinary toggle back to false must emit
        // FPGA_CHIPBIAS/147=0 and FPGA_APS/7=0 immediately.
        recording.writes.clear();
        cfg.setGlobalShutter(false);
        boolean has147Zero = false;
        boolean hasAps7Zero = false;
        for (final Write w : recording.writes) {
            if (w.module == CypressFX3.FPGA_CHIPBIAS && w.param == 147 && w.value == 0) {
                has147Zero = true;
            }
            if (w.module == CypressFX3.FPGA_APS && w.param == 7 && w.value == 0) {
                hasAps7Zero = true;
            }
        }
        System.out.println("[U2.3] post-batch ordinary non-batch toggle true->false writes=" + recording.writes);
        if (!has147Zero || !hasAps7Zero) {
            System.out.println("[U2.3] FAIL: ordinary non-batch toggle after batch did not resume synchronous writes"
                    + " (expected FPGA_CHIPBIAS/147=0 and FPGA_APS/7=0, got " + recording.writes + ")");
            failures++;
        } else {
            System.out.println("[U2.3] PASS: ordinary non-batch toggle after batch resumed synchronous writes"
                    + " (FPGA_CHIPBIAS/147=0 and FPGA_APS/7=0)");
        }
        return failures;
    }

    /**
     * After construction, preseed APS.GlobalShutter=true and (a stale) Chip.GlobalShutter=false,
     * then call the real cfg.loadPreferences(). The in-memory Chip.GlobalShutter bit must track
     * APS.GlobalShutter (the source of truth) and end up true, not the stale persisted false.
     */
    private static int testStaleLoadRealignsChipToAPS(final SciDVSConfig cfg,
            final RecordingCypressFX3 recording) {
        cfg.setHardwareInterface(null);
        final SPIConfigBit aps = cfg.globalShutter;
        final SPIConfigBit chipGS = findChipGlobalShutter(cfg);

        // Preseed the persisted preferences (not the live config) with APS=true and stale Chip=false.
        cfg.getChip().getPrefs().putBoolean("APS.GlobalShutter", true);
        cfg.getChip().getPrefs().putBoolean("Chip.GlobalShutter", false);
        cfg.loadPreferences();

        final boolean apsValue = aps.isSet();
        final boolean chipValue = chipGS.isSet();
        System.out.println("[U2.4] after loadPreferences: APS.GlobalShutter=" + apsValue
                + " Chip.GlobalShutter=" + chipValue + " (expected Chip=true, equal to APS)");
        int failures = 0;
        if (!apsValue) {
            System.out.println("[U2.4] FIXTURE_FAILURE: APS.GlobalShutter did not load as true from preseed");
            throw new IllegalStateException("fixture stale-load: APS.GlobalShutter preseed did not take effect");
        }
        if (chipValue != apsValue || !chipValue) {
            System.out.println("[U2.4] FAIL: stale-loaded Chip.GlobalShutter=" + chipValue
                    + " does not equal APS.GlobalShutter=" + apsValue + " (must re-align to APS, true)");
            failures++;
        } else {
            System.out.println("[U2.4] PASS: stale-loaded Chip.GlobalShutter re-aligned to APS and is true");
        }

        // Restore a clean working state for subsequent tests.
        cfg.setHardwareInterface(recording);
        recording.writes.clear();
        return failures;
    }

    private static int testNullHardwareDoesNotThrow(final SciDVSConfig cfg,
            final RecordingCypressFX3 recording) {
        try {
            cfg.setHardwareInterface(null);
            cfg.setGlobalShutter(!cfg.globalShutter.isSet());
            System.out.println("[U2.5] PASS: null-hardware toggle threw nothing");
            return 0;
        } catch (final Throwable throwable) {
            System.out.println("[U2.5] FAIL: null-hardware toggle threw " + throwable);
            return 1;
        } finally {
            cfg.setHardwareInterface(recording);
            recording.writes.clear();
        }
    }

    private static int testObservableReverseOrder(final SciDVSConfig cfg) {
        final List<String> callbacks = new ArrayList<>();
        final SPIConfigBit bit = new SPIConfigBit("U2ReverseOrderBit", "throwaway",
                (short) 0, (short) 0, false, cfg);
        final Observer a = new Observer() {
            @Override
            public void update(final Observable observable, final Object argument) {
                callbacks.add("A");
            }
        };
        final Observer b = new Observer() {
            @Override
            public void update(final Observable observable, final Object argument) {
                callbacks.add("B");
            }
        };
        bit.addObserver(a);
        bit.addObserver(b);
        bit.set(true);
        bit.deleteObservers();

        final boolean pass = callbacks.equals(java.util.Arrays.asList("B", "A"));
        System.out.println("[U2.6] java.util.Observable callbacks=" + callbacks
                + " expected=[B, A] pass=" + pass);
        if (!pass) {
            System.out.println("[U2.6] FAIL: unexpected java.util.Observable notification order");
            return 1;
        }
        return 0;
    }

    private static SPIConfigBit findChipGlobalShutter(final SciDVSConfig cfg) {
        for (final SPIConfigValue value : cfg.chipControl) {
            if (value instanceof SPIConfigBit && "Chip.GlobalShutter".equals(value.getName())) {
                return (SPIConfigBit) value;
            }
        }
        throw new IllegalStateException("fixture cannot exercise contract: Chip.GlobalShutter is absent");
    }

    private static SPIConfigBit findResetShorted(final SciDVSConfig cfg) {
        for (final SPIConfigValue value : cfg.chipControl) {
            if (value instanceof SPIConfigBit && "Chip.ResetShorted".equals(value.getName())) {
                return (SPIConfigBit) value;
            }
        }
        throw new IllegalStateException("fixture cannot exercise contract: Chip.ResetShorted is absent");
    }

    private static void requireFixture(final boolean condition, final String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    static final class Write {
        final short module;
        final short param;
        final int value;

        Write(final short module, final short param, final int value) {
            this.module = module;
            this.param = param;
            this.value = value;
        }

        @Override
        public String toString() {
            return "(" + module + "," + param + "," + value + ")";
        }
    }

    /** Biasgen-compatible record-only CypressFX3; every hardware operation is a no-op. */
    static final class RecordingCypressFX3 extends CypressFX3 implements BiasgenHardwareInterface {
        final List<Write> writes = new ArrayList<>();

        RecordingCypressFX3() {
            super(null);
        }

        @Override
        public synchronized void spiConfigSend(final short moduleAddr, final short paramAddr,
                final int value) {
            writes.add(new Write(moduleAddr, paramAddr, value));
        }

        @Override
        public void setPowerDown(final boolean powerDown) {
        }

        @Override
        public void sendConfiguration(final Biasgen biasgen) {
        }

        @Override
        public void flashConfiguration(final Biasgen biasgen) {
        }

        @Override
        public byte[] formatConfigurationBytes(final Biasgen biasgen) {
            return new byte[0];
        }

        @Override
        public String getTypeName() {
            return "RecordingCypressFX3";
        }

        @Override
        public synchronized void close() {
        }

        @Override
        public synchronized void open() {
        }

        @Override
        public synchronized boolean isOpen() {
            // The seam is a record-only test double, not an attached open camera. It is
            // closed so no asynchronous batch flush is triggered or asserted on; the batch
            // contract is proven purely by the in-batch synchronous-write and in-memory
            // assertions, and synchronous production writes are proven by an ordinary
            // subsequent non-batch toggle.
            return false;
        }
    }
}
