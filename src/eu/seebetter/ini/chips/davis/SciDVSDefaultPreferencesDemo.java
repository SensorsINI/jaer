/*
 * U1 decision-grade RED test for the SciDVS canonical-config TDD contract
 * (plan todo 6: "Import exactly SciDVS_sensitive_highVgMfb.xml ... set it as the
 * SciDVS default ... first-use load").
 *
 * A real SciDVSConfig is constructed around an Objenesis-created SciDVS so the
 * test never initializes JOGL, mirroring the SciDVSChipConfigDemo fixture.
 * The config is installed as the chip's biasgen so that actual
 * Chip.maybeLoadDefaultPreferences routes its import through
 * Biasgen.importPreferences (batch-edit, pot load) rather than the raw
 * Preferences.importPreferences fallback.
 *
 * The canonical asset biasgenSettings/SciDVS/SciDVS_sensitive_highVgMfb.xml is
 * ABSENT on this base branch (SciDVS.java sets setDefaultPreferencesFile(null)),
 * so the explicit-path and fresh-first-use scenarios correctly fail RED against
 * the unchanged base. The remaining scenarios characterize base behaviour that
 * already holds and must keep holding.
 *
 * Usage: SciDVSDefaultPreferencesDemo <scenario> [<scenario> ...]
 *   explicit-path | fresh-first-use | no-overwrite | second-call-idempotent
 *   | missing-file | malformed-file
 *
 * Exit 0 all scenarios pass (GREEN), 1 production contract unmet (RED),
 * 2 fixture/infrastructure failure (INVALID, not a contract verdict).
 */
package eu.seebetter.ini.chips.davis;

import java.beans.PropertyChangeSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import net.sf.jaer.chip.Chip;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;

/** Headless executable test. Exit 1 => production contract failure (RED). */
public class SciDVSDefaultPreferencesDemo {

    private static final String PREFS_PATH = "jaer/chips/SciDVS";
    private static final String DESIRED_PATH = "biasgenSettings/SciDVS/SciDVS_sensitive_highVgMfb.xml";
    private static final String EXPOSURE_KEY = "SciDVS.APS.Exposure";
    private static final String EXPECTED_EXPOSURE = "5000";
    private static final int EXPECTED_IMPORTED_KEYS = 273;
    private static final Logger LOG = Logger.getLogger("net.sf.jaer");

    private static final class ScenarioResult {
        int failures;
        boolean fixtureFailure;
        String fixtureMessage;
    }

    public static void main(final String[] args) {
        prefsRootsBanner();
        if (args.length == 0) {
            System.out.println("[U1] usage: SciDVSDefaultPreferencesDemo <scenario> [...]");
            System.out.println("[U1] STATUS: INVALID (no scenario supplied)");
            System.exit(2);
        }

        int totalFailures = 0;
        boolean anyFixtureFailure = false;
        final List<String> verdicts = new ArrayList<>();

        for (final String scenario : args) {
            final ScenarioResult result = new ScenarioResult();
            try {
                result.failures = runScenario(scenario, result);
            } catch (final Throwable fixtureFailure) {
                result.fixtureFailure = true;
                result.fixtureMessage = String.valueOf(fixtureFailure);
                System.out.println("[U1] FIXTURE_FAILURE scenario=" + scenario + ": " + fixtureFailure);
                fixtureFailure.printStackTrace(System.out);
            }

            if (result.fixtureFailure) {
                verdicts.add(scenario + "=INVALID");
                anyFixtureFailure = true;
            } else {
                verdicts.add(scenario + (result.failures == 0 ? "=PASS" : "=FAIL(" + result.failures + ")"));
                totalFailures += result.failures;
            }
        }

        System.out.println("[U1] SCENARIO_VERDICTS=" + verdicts);
        System.out.println("[U1] TOTAL_CONTRACT_FAILURES=" + totalFailures);

        // Final sweep cleanup of the test preference node across the JVM.
        cleanPrefs();

        if (anyFixtureFailure) {
            System.out.println("[U1] STATUS: INVALID (fixture/infrastructure failure, not a production verdict)");
            System.exit(2);
        } else if (totalFailures > 0) {
            System.out.println("[U1] STATUS: RED (production contract unmet)");
            System.exit(1);
        } else {
            System.out.println("[U1] STATUS: GREEN");
            System.exit(0);
        }
    }

    /**
     * Dispatches one scenario. Runs against its own fresh, isolated preference
     * node (removed and recreated), so no scenario shares state with another.
     */
    private static int runScenario(final String scenario, final ScenarioResult result) throws Exception {
        final Preferences node = freshPrefs();
        final int failures;
        switch (scenario) {
            case "explicit-path":
                failures = scenarioExplicitPath(node);
                break;
            case "fresh-first-use":
                failures = scenarioFreshFirstUse(node);
                break;
            case "no-overwrite":
                failures = scenarioNoOverwrite(node);
                break;
            case "second-call-idempotent":
                failures = scenarioSecondCallIdempotent(node);
                break;
            case "missing-file":
                failures = scenarioMissingFile(node);
                break;
            case "malformed-file":
                failures = scenarioMalformedFile(node);
                break;
            default:
                result.fixtureFailure = true;
                result.fixtureMessage = "unknown scenario: " + scenario;
                return 0;
        }
        cleanNode(node);
        return failures;
    }

    /**
     * Explicit preference path set on the chip resolves to the canonical asset
     * and a first call to maybeLoadDefaultPreferences imports it.
     */
    private static int scenarioExplicitPath(final Preferences node) {
        final SciDVS chip = freshChip(node);
        chip.setDefaultPreferencesFile(DESIRED_PATH);
        int failures = 0;

        final String resolved = chip.resolveDefaultPreferencesFile();
        System.out.println("[U1.explicit-path] resolveDefaultPreferencesFile=" + resolved);
        if (!DESIRED_PATH.equals(resolved)) {
            System.out.println("[U1.explicit-path] FAIL: resolved path != " + DESIRED_PATH);
            failures++;
        }

        final boolean loaded = chip.maybeLoadDefaultPreferences();
        System.out.println("[U1.explicit-path] maybeLoadDefaultPreferences=" + loaded);
        if (!loaded) {
            System.out.println("[U1.explicit-path] FAIL: did not import canonical settings "
                    + "(asset absent / default preferences not wired)");
            failures++;
        } else if (countSciDVSKeys(node) != EXPECTED_IMPORTED_KEYS) {
            System.out.println("[U1.explicit-path] FAIL: imported key count "
                    + countSciDVSKeys(node) + " != " + EXPECTED_IMPORTED_KEYS);
            failures++;
        }
        return failures;
    }

    /** Fresh first use loads the canonical profile into a clean preference node. */
    private static int scenarioFreshFirstUse(final Preferences node) {
        final SciDVS chip = freshChip(node);
        chip.setDefaultPreferencesFile(DESIRED_PATH);
        int failures = 0;

        final boolean loaded = chip.maybeLoadDefaultPreferences();
        System.out.println("[U1.fresh-first-use] maybeLoadDefaultPreferences=" + loaded);
        if (!loaded) {
            System.out.println("[U1.fresh-first-use] FAIL: did not import canonical settings on first use");
            failures++;
            return failures;
        }

        final String exposure = node.get(EXPOSURE_KEY, null);
        System.out.println("[U1.fresh-first-use] " + EXPOSURE_KEY + "=" + exposure);
        if (!EXPECTED_EXPOSURE.equals(exposure)) {
            System.out.println("[U1.fresh-first-use] FAIL: Exposure=" + exposure
                    + " expected " + EXPECTED_EXPOSURE);
            failures++;
        }

        final boolean marker = node.getBoolean(Chip.PREFERENCES_LOADED_ONCE_KEY, false);
        System.out.println("[U1.fresh-first-use] " + Chip.PREFERENCES_LOADED_ONCE_KEY + "=" + marker);
        if (!marker) {
            System.out.println("[U1.fresh-first-use] FAIL: defaultPreferencesWereLoaded marker not set");
            failures++;
        }

        final int keys = countSciDVSKeys(node);
        System.out.println("[U1.fresh-first-use] imported SciDVS key count=" + keys);
        if (keys != EXPECTED_IMPORTED_KEYS) {
            System.out.println("[U1.fresh-first-use] FAIL: imported keys " + keys
                    + " != " + EXPECTED_IMPORTED_KEYS);
            failures++;
        }
        return failures;
    }

    /**
     * A marker already present (preferences loaded before) plus a user-set
     * Exposure must be left untouched: fresh first-use must never overwrite
     * existing user preferences.
     */
    private static int scenarioNoOverwrite(final Preferences node) throws Exception {
        node.putBoolean(Chip.PREFERENCES_LOADED_ONCE_KEY, true);
        node.put(EXPOSURE_KEY, "12345");
        node.flush();

        final SciDVS chip = freshChip(node);
        chip.setDefaultPreferencesFile(DESIRED_PATH);
        int failures = 0;

        final boolean loaded = chip.maybeLoadDefaultPreferences();
        System.out.println("[U1.no-overwrite] maybeLoadDefaultPreferences=" + loaded);
        if (loaded) {
            System.out.println("[U1.no-overwrite] FAIL: reloaded despite marker already present");
            failures++;
        }
        final String exposure = node.get(EXPOSURE_KEY, "");
        System.out.println("[U1.no-overwrite] " + EXPOSURE_KEY + "=" + exposure);
        if (!"12345".equals(exposure)) {
            System.out.println("[U1.no-overwrite] FAIL: user Exposure overwritten -> " + exposure);
            failures++;
        }
        final boolean marker = node.getBoolean(Chip.PREFERENCES_LOADED_ONCE_KEY, false);
        if (!marker) {
            System.out.println("[U1.no-overwrite] FAIL: marker lost");
            failures++;
        }
        return failures;
    }

    /** A second call is a no-op: returns false and leaves the key set untouched. */
    private static int scenarioSecondCallIdempotent(final Preferences node) {
        final SciDVS chip = freshChip(node);
        chip.setDefaultPreferencesFile(DESIRED_PATH);
        int failures = 0;

        chip.maybeLoadDefaultPreferences();
        final String digest1 = keyDigest(node);
        final boolean second = chip.maybeLoadDefaultPreferences();
        final String digest2 = keyDigest(node);
        System.out.println("[U1.second-call-idempotent] second call=" + second
                + " digest1=" + digest1 + " digest2=" + digest2);
        if (second) {
            System.out.println("[U1.second-call-idempotent] FAIL: second call returned true");
            failures++;
        }
        if (!digest1.equals(digest2)) {
            System.out.println("[U1.second-call-idempotent] FAIL: key digest changed between calls");
            failures++;
        }
        return failures;
    }

    /** A missing explicit file: returns false, no marker, no escaped exception. */
    private static int scenarioMissingFile(final Preferences node) {
        final SciDVS chip = freshChip(node);
        chip.setDefaultPreferencesFile("biasgenSettings/SciDVS/does-not-exist-canary.xml");
        int failures = 0;

        final boolean loaded = chip.maybeLoadDefaultPreferences();
        System.out.println("[U1.missing-file] maybeLoadDefaultPreferences=" + loaded);
        if (loaded) {
            System.out.println("[U1.missing-file] FAIL: unexpectedly loaded a missing file");
            failures++;
        }
        final boolean marker = node.getBoolean(Chip.PREFERENCES_LOADED_ONCE_KEY, false);
        if (marker) {
            System.out.println("[U1.missing-file] FAIL: marker set for a missing file");
            failures++;
        }
        return failures;
    }

    /**
     * A malformed explicit XML file: returns false, no marker, no escaped
     * exception, and a SEVERE-level log line from the import path.
     */
    private static int scenarioMalformedFile(final Preferences node) throws Exception {
        final Path malformed = Files.createTempFile("u1-malformed-", ".xml");
        Files.write(malformed, ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<this-is-not-a-preferences-document>").getBytes("UTF-8"));

        final SciDVS chip = freshChip(node);
        chip.setDefaultPreferencesFile(malformed.toString());
        int failures = 0;

        final SevereCapture capture = new SevereCapture();
        LOG.addHandler(capture);
        final boolean loaded;
        try {
            loaded = chip.maybeLoadDefaultPreferences();
        } finally {
            LOG.removeHandler(capture);
            Files.deleteIfExists(malformed);
        }

        System.out.println("[U1.malformed-file] maybeLoadDefaultPreferences=" + loaded);
        if (loaded) {
            System.out.println("[U1.malformed-file] FAIL: unexpectedly loaded a malformed file");
            failures++;
        }
        final boolean marker = node.getBoolean(Chip.PREFERENCES_LOADED_ONCE_KEY, false);
        if (marker) {
            System.out.println("[U1.malformed-file] FAIL: marker set for a malformed file");
            failures++;
        }
        System.out.println("[U1.malformed-file] severe log records seen=" + capture.count());
        if (capture.count() == 0) {
            System.out.println("[U1.malformed-file] FAIL: no SEVERE logged for malformed import");
            failures++;
        }
        return failures;
    }

    /** Objenesis SciDVS + a real SciDVSConfig installed as the chip biasgen. */
    private static SciDVS freshChip(final Preferences node) {
        final Objenesis objenesis = new ObjenesisStd();
        final SciDVS chip = objenesis.newInstance(SciDVS.class);
        chip.setPrefs(node);
        chip.setSupport(new PropertyChangeSupport(chip));
        final SciDVSConfig cfg = new SciDVSConfig(chip);
        chip.setBiasgen(cfg);
        return chip;
    }

    private static Preferences freshPrefs() {
        final Preferences userRoot = Preferences.userRoot();
        try {
            if (userRoot.nodeExists(PREFS_PATH)) {
                userRoot.node(PREFS_PATH).removeNode();
            }
        } catch (final Exception e) {
            // ignore; fresh node creation below will still work
        }
        return userRoot.node(PREFS_PATH);
    }

    private static void cleanNode(final Preferences node) {
        try {
            node.removeNode();
            Preferences.userRoot().flush();
        } catch (final Exception e) {
            System.out.println("[U1] CLEANUP_FAILURE: " + e);
        }
    }

    private static void cleanPrefs() {
        try {
            final Preferences userRoot = Preferences.userRoot();
            if (userRoot.nodeExists(PREFS_PATH)) {
                userRoot.node(PREFS_PATH).removeNode();
            }
            userRoot.flush();
        } catch (final Exception e) {
            System.out.println("[U1] FINAL_CLEANUP_FAILURE: " + e);
        }
    }

    private static int countSciDVSKeys(final Preferences node) {
        try {
            int n = 0;
            for (final String key : node.keys()) {
                if (key.startsWith("SciDVS.")) {
                    n++;
                }
            }
            return n;
        } catch (final Exception e) {
            return -1;
        }
    }

    private static String keyDigest(final Preferences node) {
        final List<String> keys = new ArrayList<>();
        try {
            for (final String key : node.keys()) {
                keys.add(key + "=" + node.get(key, ""));
            }
        } catch (final Exception e) {
            return "ERR:" + e;
        }
        java.util.Collections.sort(keys);
        return Integer.toHexString(keys.toString().hashCode()) + ":" + keys.size();
    }

    /** Captures SEVERE messages from the jAER logger under test. */
    private static final class SevereCapture extends Handler {
        private int count;

        @Override
        public void publish(final LogRecord record) {
            if (record.getLevel().intValue() >= Level.SEVERE.intValue()) {
                count++;
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws SecurityException {
        }

        int count() {
            return count;
        }
    }

    private static void prefsRootsBanner() {
        System.out.println("[U1] user prefs root="
                + System.getProperty("java.util.prefs.userRoot", "<default>"));
        System.out.println("[U1] system prefs root="
                + System.getProperty("java.util.prefs.systemRoot", "<default>"));
        System.out.println("[U1] desired path=" + DESIRED_PATH
                + " assetExists=" + new java.io.File(DESIRED_PATH).isFile());
    }
}
