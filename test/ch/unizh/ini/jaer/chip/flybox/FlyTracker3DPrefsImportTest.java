package ch.unizh.ini.jaer.chip.flybox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;

/**
 * RCT-flies.xml was exported from RectangularClusterTracker on NRVS5KRC1S.
 * Import must apply matching properties to the trackers enclosed in FlyTracker3D.
 */
public class FlyTracker3DPrefsImportTest {

    private AEChip chip;
    private final Map<Preferences, Map<String, String>> saved = new LinkedHashMap<>();

    @Before
    public void rememberPrefs() throws Exception {
        chip = new AEChip();
    }

    @After
    public void restorePrefs() throws Exception {
        for (Map.Entry<Preferences, Map<String, String>> entry : saved.entrySet()) {
            Preferences prefs = entry.getKey();
            for (String key : prefs.keys()) {
                prefs.remove(key);
            }
            for (Map.Entry<String, String> value : entry.getValue().entrySet()) {
                if (value.getValue() != null) {
                    prefs.put(value.getKey(), value.getValue());
                }
            }
        }
    }

    @Test
    public void importOntoOneEnclosedTracker() throws Exception {
        File file = settings();
        FlyTracker3D fly = new FlyTracker3D(chip);
        RectangularClusterTracker nrv = tracker(fly, "nrvTracker");
        RectangularClusterTracker evk = tracker(fly, "evk4Tracker");
        keep(fly.getPrefs());
        keep(nrv.getPrefs());
        keep(evk.getPrefs());

        nrv.getPrefs().put("NrvTracker.maxNumClusters", "2");
        nrv.getPrefs().put("NrvTracker.fontSize", "11");
        nrv.getPrefs().put("NrvTracker.showPaths", "true");
        nrv.getPrefs().put("NrvTracker.limitSpeedByNearbyClusters", "true");
        nrv.getPrefs().put("NrvTracker.showClusterVelocityVector", "false");
        evk.getPrefs().put("Evk4Tracker.maxNumClusters", "3");
        evk.getPrefs().put("Evk4Tracker.fontSize", "12");
        fly.getPrefs().put("FlyTracker3D.locationMixingFactor", "0.5");
        fly.getPrefs().put("FlyTracker3D.velocityTauMs", "100");
        fly.getPrefs().put("FlyTracker3D.filterEnabled", "false");
        flush(nrv.getPrefs());

        nrv.importPrefs(file);
        flush(nrv.getPrefs());

        assertFalse(nrv.getPrefs().absolutePath().contains("NRVS5KRC1S"));
        assertEquals("5", nrv.getPrefs().get("NrvTracker.maxNumClusters", null));
        assertEquals("7", nrv.getPrefs().get("NrvTracker.fontSize", null));
        assertEquals("false", nrv.getPrefs().get("NrvTracker.showPaths", null));
        assertEquals("false", nrv.getPrefs().get("NrvTracker.limitSpeedByNearbyClusters", null));
        assertEquals("InitialEstimate", nrv.getPrefs().get("NrvTracker.velocityInitialization", null));
        assertEquals("3", evk.getPrefs().get("Evk4Tracker.maxNumClusters", null));
        assertEquals("0.010976593", fly.getPrefs().get("FlyTracker3D.locationMixingFactor", null));
        assertEquals("19.277668", fly.getPrefs().get("FlyTracker3D.velocityTauMs", null));
        assertEquals("0.038905207", fly.getPrefs().get("FlyTracker3D.clusterSize", null));
        assertEquals("false", fly.getPrefs().get("FlyTracker3D.filterEnabled", null));

        FlyTracker3D reloaded = new FlyTracker3D(chip);
        RectangularClusterTracker nrv2 = tracker(reloaded, "nrvTracker");
        RectangularClusterTracker evk2 = tracker(reloaded, "evk4Tracker");
        assertEquals(5, nrv2.getMaxNumClusters());
        assertEquals(7, nrv2.getFontSize());
        assertFalse(nrv2.isShowPaths());
        assertFalse(nrv2.isLimitSpeedByNearbyClusters());
        assertTrue(nrv2.isShowClusterVelocityVector());
        assertEquals(3, evk2.getMaxNumClusters());
        assertEquals(12, evk2.getFontSize());
        assertEquals(0.010976593f, reloaded.getLocationMixingFactor(), 1e-6f);
        assertEquals(19.277668f, reloaded.getVelocityTauMs(), 1e-4f);
        assertEquals(0.038905207f, reloaded.getClusterSize(), 1e-6f);
        assertEquals(0.010976593f, nrv2.getLocationMixingFactor(), 1e-5f);
        assertEquals(0.010976593f, evk2.getLocationMixingFactor(), 1e-5f);
        assertEquals(19.277668f, nrv2.getVelocityTauMs(), 1e-3f);
        assertEquals(19.277668f, evk2.getVelocityTauMs(), 1e-3f);
    }

    @Test
    public void importOntoEnclosingFilterUpdatesBothTrackers() throws Exception {
        File file = settings();
        FlyTracker3D fly = new FlyTracker3D(chip);
        RectangularClusterTracker nrv = tracker(fly, "nrvTracker");
        RectangularClusterTracker evk = tracker(fly, "evk4Tracker");
        keep(fly.getPrefs());
        keep(nrv.getPrefs());
        keep(evk.getPrefs());
        nrv.getPrefs().put("NrvTracker.maxNumClusters", "2");
        evk.getPrefs().put("Evk4Tracker.maxNumClusters", "3");
        flush(nrv.getPrefs());

        fly.importPrefs(file);
        flush(nrv.getPrefs());

        FlyTracker3D reloaded = new FlyTracker3D(chip);
        assertEquals(5, tracker(reloaded, "nrvTracker").getMaxNumClusters());
        assertEquals(5, tracker(reloaded, "evk4Tracker").getMaxNumClusters());
        assertEquals(7, tracker(reloaded, "nrvTracker").getFontSize());
        assertEquals(7, tracker(reloaded, "evk4Tracker").getFontSize());
        assertEquals(0.010976593f, reloaded.getLocationMixingFactor(), 1e-6f);
    }

    @Test
    public void propertyChangeOnOneTrackerUpdatesTheOther() throws Exception {
        FlyTracker3D fly = new FlyTracker3D(chip);
        RectangularClusterTracker nrv = tracker(fly, "nrvTracker");
        RectangularClusterTracker evk = tracker(fly, "evk4Tracker");
        keep(nrv.getPrefs());
        keep(evk.getPrefs());
        float evkClusterSize = evk.getClusterSize();

        evk.setMaxNumClusters(1);
        nrv.setMaxNumClusters(4);
        flush(nrv.getPrefs());
        assertEquals(4, evk.getMaxNumClusters());
        nrv.setFontSize(8);
        evk.setFontSize(9);
        flush(evk.getPrefs());
        assertEquals(9, nrv.getFontSize());
        evk.setShowPaths(true);
        nrv.setShowPaths(false);
        flush(nrv.getPrefs());
        assertFalse(evk.isShowPaths());

        nrv.setClusterSize(0.2f);
        assertEquals(0.2f, nrv.getClusterSize(), 1e-6f);
        assertEquals(evkClusterSize, evk.getClusterSize(), 1e-6f);
    }

    @Test
    public void importOntoTrackerFromAnotherChip() throws Exception {
        File file = settings();
        RectangularClusterTracker tracker = new RectangularClusterTracker(chip);
        keep(tracker.getPrefs());
        tracker.getPrefs().put("RectangularClusterTracker.maxNumClusters", "2");
        tracker.getPrefs().put("RectangularClusterTracker.fontSize", "18");

        tracker.importPrefs(file);

        assertTrue(tracker.getPrefs().absolutePath().contains("AEChip"));
        assertFalse(tracker.getPrefs().absolutePath().contains("NRVS5KRC1S"));
        RectangularClusterTracker reloaded = new RectangularClusterTracker(chip);
        assertEquals(5, reloaded.getMaxNumClusters());
        assertEquals(7, reloaded.getFontSize());
        assertEquals(73358, reloaded.getClusterMassDecayTauUs());
        assertEquals(0.010976593f, reloaded.getLocationMixingFactor(), 1e-6f);
        assertEquals("InitialEstimate", reloaded.getPrefs().get("RectangularClusterTracker.velocityInitialization", null));
        assertFalse(reloaded.isShowPaths());
        assertEquals("true", reloaded.getPrefs().get("RectangularClusterTracker.filterEnabled", null));
        reloaded.setPreferredEnabledState();
        assertTrue(reloaded.isFilterEnabled());
    }

    private void keep(Preferences prefs) throws Exception {
        saved.computeIfAbsent(prefs, key -> {
            try {
                Map<String, String> values = new LinkedHashMap<>();
                for (String name : key.keys()) {
                    values.put(name, key.get(name, null));
                }
                return values;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static void flush(Preferences prefs) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        String key = "FlyTracker3DTest.flush";
        PreferenceChangeListener listener = evt -> {
            if (key.equals(evt.getKey())) {
                latch.countDown();
            }
        };
        prefs.addPreferenceChangeListener(listener);
        try {
            prefs.put(key, Long.toString(System.nanoTime()));
            assertTrue("preference event thread did not drain", latch.await(2, TimeUnit.SECONDS));
            // The mirror itself queues one more event after this sentinel.
            CountDownLatch second = new CountDownLatch(1);
            PreferenceChangeListener again = evt -> {
                if (key.equals(evt.getKey())) {
                    second.countDown();
                }
            };
            prefs.addPreferenceChangeListener(again);
            try {
                prefs.put(key, Long.toString(System.nanoTime()));
                assertTrue("preference mirror did not finish", second.await(2, TimeUnit.SECONDS));
            } finally {
                prefs.removePreferenceChangeListener(again);
            }
        } finally {
            prefs.removePreferenceChangeListener(listener);
            prefs.remove(key);
        }
    }

    private static File settings() {
        File file = new File("filterSettings/RCT-flies.xml");
        assertTrue(file.getAbsolutePath() + " is missing", file.isFile());
        return file;
    }

    private static RectangularClusterTracker tracker(FlyTracker3D fly, String field) throws Exception {
        Field f = FlyTracker3D.class.getDeclaredField(field);
        f.setAccessible(true);
        return (RectangularClusterTracker) f.get(fly);
    }
}
