package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import net.sf.jaer.chip.AEChip;

/**
 * Pure decision seam for cameras whose USB VID/PID is shared by DAVIS and
 * SciDVS. Hardware access is injected so callers can guarantee that probing
 * and post-open identity refresh never run on the Swing event-dispatch thread.
 */
public final class SciDVSReplugDecision {

    public enum SelectionReason {
        NONE,
        DEFINITIVE_SCIDVS,
        REMEMBERED
    }

    @FunctionalInterface
    public interface GeometryProbe {

        boolean matchesSciDVS() throws Exception;
    }

    public interface PreferenceStore {

        String get(String key);

        void remove(String key);
    }

    public static final class Result {

        private final List<Class<? extends AEChip>> candidates;
        private final Class<? extends AEChip> selectedChip;
        private final SelectionReason selectionReason;
        private final String deviceKey;
        private final boolean probeAttempted;
        private final boolean probeSucceeded;
        private final boolean sciDVSGeometry;
        private final boolean ordinaryFallbackRan;
        private final Exception probeFailure;

        private Result(List<Class<? extends AEChip>> candidates,
                Class<? extends AEChip> selectedChip,
                SelectionReason selectionReason,
                String deviceKey,
                boolean probeAttempted,
                boolean probeSucceeded,
                boolean sciDVSGeometry,
                boolean ordinaryFallbackRan,
                Exception probeFailure) {
            this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
            this.selectedChip = selectedChip;
            this.selectionReason = selectionReason;
            this.deviceKey = deviceKey;
            this.probeAttempted = probeAttempted;
            this.probeSucceeded = probeSucceeded;
            this.sciDVSGeometry = sciDVSGeometry;
            this.ordinaryFallbackRan = ordinaryFallbackRan;
            this.probeFailure = probeFailure;
        }

        public List<Class<? extends AEChip>> candidates() {
            return candidates;
        }

        public Class<? extends AEChip> selectedChip() {
            return selectedChip;
        }

        public SelectionReason selectionReason() {
            return selectionReason;
        }

        public String deviceKey() {
            return deviceKey;
        }

        public boolean probeAttempted() {
            return probeAttempted;
        }

        public boolean probeSucceeded() {
            return probeSucceeded;
        }

        public boolean sciDVSGeometry() {
            return sciDVSGeometry;
        }

        public boolean ordinaryFallbackRan() {
            return ordinaryFallbackRan;
        }

        public boolean chooserSuppressed() {
            return selectedChip != null;
        }

        public Exception probeFailure() {
            return probeFailure;
        }
    }

    public static final class StartupResult {

        private final boolean skippedSharedPidAmbiguity;
        private final Class<? extends AEChip> selectedChip;

        private StartupResult(boolean skippedSharedPidAmbiguity,
                Class<? extends AEChip> selectedChip) {
            this.skippedSharedPidAmbiguity = skippedSharedPidAmbiguity;
            this.selectedChip = selectedChip;
        }

        public boolean skippedSharedPidAmbiguity() {
            return skippedSharedPidAmbiguity;
        }

        public Class<? extends AEChip> selectedChip() {
            return selectedChip;
        }
    }

    private SciDVSReplugDecision() {
    }

    public static boolean isSharedPidAmbiguity(boolean sharedPidHardware,
            List<Class<? extends AEChip>> candidates,
            Class<? extends AEChip> sciDVSClass) {
        return sharedPidHardware
                && candidates != null
                && sciDVSClass != null
                && candidates.size() > 1
                && candidates.contains(sciDVSClass);
    }

    public static boolean shouldSkipStartupOptimization(boolean sharedPidHardware,
            List<Class<? extends AEChip>> candidates,
            Class<? extends AEChip> sciDVSClass) {
        return isSharedPidAmbiguity(sharedPidHardware, candidates, sciDVSClass);
    }

    public static StartupResult resolveStartup(boolean sharedPidHardware,
            List<Class<? extends AEChip>> candidates,
            Class<? extends AEChip> sciDVSClass,
            PreferenceStore preferences,
            String rememberedPrefix,
            String deviceKey) {
        if (shouldSkipStartupOptimization(sharedPidHardware, candidates, sciDVSClass)) {
            return new StartupResult(true, null);
        }
        return new StartupResult(false, loadRememberedChip(
                preferences, rememberedPrefix, deviceKey, candidates));
    }

    public static Result resolve(boolean sharedPidHardware,
            boolean eventDispatchThread,
            List<Class<? extends AEChip>> candidates,
            Class<? extends AEChip> sciDVSClass,
            GeometryProbe geometryProbe,
            Supplier<String> postProbeDeviceKey,
            String broadDeviceKey,
            String initialDeviceKey,
            PreferenceStore preferences,
            String rememberedPrefix,
            String defaultPrefix) {
        ArrayList<Class<? extends AEChip>> remaining = new ArrayList<>(candidates);
        String deviceKey = initialDeviceKey;
        boolean probeAttempted = false;
        boolean probeSucceeded = false;
        boolean sciDVSGeometry = false;
        Exception probeFailure = null;

        if (isSharedPidAmbiguity(sharedPidHardware, remaining, sciDVSClass)
                && !eventDispatchThread) {
            probeAttempted = true;
            try {
                sciDVSGeometry = geometryProbe.matchesSciDVS();
                probeSucceeded = true;
                deviceKey = refreshedDeviceKey(postProbeDeviceKey, deviceKey);
                if (sciDVSGeometry) {
                    removeConflictingBroadPreference(
                            preferences, rememberedPrefix, broadDeviceKey, sciDVSClass);
                    removeConflictingBroadPreference(
                            preferences, defaultPrefix, broadDeviceKey, sciDVSClass);
                    return new Result(remaining, sciDVSClass,
                            SelectionReason.DEFINITIVE_SCIDVS, deviceKey,
                            true, true, true, false, null);
                }
                remaining.remove(sciDVSClass);
            } catch (Exception e) {
                probeFailure = e;
                deviceKey = refreshedDeviceKey(postProbeDeviceKey, deviceKey);
            }
        }

        Class<? extends AEChip> remembered = loadRememberedChip(
                preferences, rememberedPrefix, deviceKey, remaining);
        return new Result(remaining, remembered,
                remembered == null ? SelectionReason.NONE : SelectionReason.REMEMBERED,
                deviceKey, probeAttempted, probeSucceeded, sciDVSGeometry,
                true, probeFailure);
    }

    private static String refreshedDeviceKey(Supplier<String> supplier, String fallback) {
        if (supplier == null) {
            return fallback;
        }
        String refreshed = supplier.get();
        return refreshed == null || refreshed.isBlank() ? fallback : refreshed;
    }

    private static void removeConflictingBroadPreference(PreferenceStore preferences,
            String prefix,
            String broadDeviceKey,
            Class<? extends AEChip> identifiedChip) {
        if (preferences == null || prefix == null || broadDeviceKey == null) {
            return;
        }
        String key = prefix + broadDeviceKey;
        String fqcn = preferences.get(key);
        if (fqcn != null && !identifiedChip.getName().equals(fqcn)) {
            preferences.remove(key);
        }
    }

    private static Class<? extends AEChip> loadRememberedChip(
            PreferenceStore preferences,
            String rememberedPrefix,
            String deviceKey,
            List<Class<? extends AEChip>> candidates) {
        if (preferences == null || rememberedPrefix == null || deviceKey == null) {
            return null;
        }
        String key = rememberedPrefix + deviceKey;
        String fqcn = preferences.get(key);
        if (fqcn == null || fqcn.isEmpty()) {
            return null;
        }
        for (Class<? extends AEChip> candidate : candidates) {
            if (candidate.getName().equals(fqcn)) {
                return candidate;
            }
        }
        preferences.remove(key);
        return null;
    }
}
