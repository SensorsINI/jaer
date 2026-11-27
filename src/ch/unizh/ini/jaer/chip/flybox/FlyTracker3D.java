/*
 * FlyTracker3D.java
 *
 * One fly, two orthogonal pinhole cameras, one 3D track.
 */
package ch.unizh.ini.jaer.chip.flybox;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.Preferences;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.eventprocessing.EventFilter;
import net.sf.jaer.chip.multidevice.DeviceRelation;
import net.sf.jaer.chip.multidevice.DeviceSlot;
import net.sf.jaer.chip.multidevice.MultiDeviceChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.graphics.AEPlayer;
import net.sf.jaer.graphics.AbstractAEPlayer;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Tracks one fly in a {@link FlyBox2Cam}.
 * <p>
 * Two enclosed {@link RectangularClusterTracker}s see native events from one
 * camera each. The strongest visible cluster on each camera is a ray through
 * that pinhole. The 3D point is the closest point between those rays.
 * <p>
 * World frame: right-handed, z up, origin at the center of the box. Both
 * lenses look at the origin. Image offsets are pixels from the sensor center.
 * jAER y increases upward.
 * <ul>
 * <li><b>evk4</b> lens at {@code (0, 0, +distance)}. It looks along -z and
 * images xy. Image +x is world -x. Image +y is world +y.</li>
 * <li><b>nrv</b> lens at {@code (-distance, 0, 0)}. It looks along +x and
 * images yz. Image +x is world +y. Image +y is world +z.</li>
 * </ul>
 * {@code locationMixingFactor}, {@code velocityTauMs}, and {@code clusterSize}
 * are set here and written to both trackers. {@code clusterSize} is a fraction
 * of each sensor's longer side. A 3D point is recorded when a tracker fires
 * its update and the other camera's last update is still within that camera's
 * own update period. The two clocks can sit a millisecond or two apart. A
 * camera that misses its period, or loses its cluster, ends the flight.
 */
@Description("3D fly track from NRV (yz) and EVK4 (xy) pinhole cameras")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class FlyTracker3D extends EventFilter2D implements FrameAnnotater, Observer {

    /** NRV Samsung S5KRC1S pixel pitch. */
    public static final float NRV_PITCH_MM = 0.009f;
    /** Sony IMX636 pixel pitch. */
    public static final float EVK4_PITCH_MM = 0.005f;

    private float locationMixingFactor = getFloat("locationMixingFactor", 0.05f);
    private float velocityTauMs = getFloat("velocityTauMs", 100f);
    private float clusterSize = getFloat("clusterSize", 0.1f);
    private float nrvDistanceMm = getFloat("nrvDistanceMm", 100f);
    private float nrvFocalLengthMm = getFloat("nrvFocalLengthMm", 6f);
    private float evk4DistanceMm = getFloat("evk4DistanceMm", 100f);
    private float evk4FocalLengthMm = getFloat("evk4FocalLengthMm", 6f);
    /** Full size of the box. The origin is its center. */
    private float boxWidth = getFloat("boxWidth", 100f);
    private float boxDepth = getFloat("boxDepth", 100f);
    private float boxHeight = getFloat("boxHeight", 100f);
    private boolean showBox = getBoolean("showBox", true);
    private int pathLength = getInt("pathLength", 300);
    private int trajectoriesToShow = getInt("trajectoriesToShow", 8);
    /** Seconds. Flights shorter than this are not shown. */
    private float minFlightDuration = getFloat("minFlightDuration", 0.1f);
    private boolean markFlights = getBoolean("markFlights", false);
    /** Playback marker is placed this far before the flight's first point. */
    private static final int FLIGHT_MARK_LEAD_US = 100_000;

    /**
     * {@code clusterSize} is a fraction of the chip the tracker was built on
     * (the tiled parent). Each camera is given its own fraction in
     * {@link #pushClusterSize}.
     */
    private static final Set<String> NOT_MIRRORED = Set.of("clusterSize");
    private static final Map<String, PrefWriter> WRITERS = writers();
    private static final Map<Preferences, WeakReference<RectangularClusterTracker>> ACTIVE = new ConcurrentHashMap<>();
    private static final Set<Preferences> WATCHED = ConcurrentHashMap.newKeySet();
    /** Last tracker that changed each property during one burst of preference events. */
    private static final Map<String, WeakReference<RectangularClusterTracker>> PENDING = new LinkedHashMap<>();
    private static final String MIRROR_FLUSH = "FlyTracker3D.mirrorFlush";
    private static boolean mirrorFlushQueued;

    private final NrvTracker nrvTracker;
    private final Evk4Tracker evk4Tracker;
    private final EventPacket<PolarityEvent> nrvPacket = new EventPacket<>(PolarityEvent.class);
    private final EventPacket<PolarityEvent> evk4Packet = new EventPacket<>(PolarityEvent.class);
    private final List<Trajectory> flights = new ArrayList<>();
    private final List<Fix> pendingFixes = new ArrayList<>();
    /** Guards {@link #pendingFixes} only. The update callback must not lock this filter. */
    private final Object fixesLock = new Object();
    private Trajectory openFlight;
    private int nextColor;
    private float[] current;
    private Fix lastNrv;
    private Fix lastEvk;
    private boolean haveSample;
    private int lastSampleUs;
    /** Gap between the last two ticks of each camera. Zero until it has ticked twice. */
    private int nrvPeriodUs;
    private int evkPeriodUs;
    private boolean loggedRays;
    private FlyTracker3DViewer viewer;
    private boolean showViewer;
    private boolean warnedGeometry;

    public FlyTracker3D(AEChip chip) {
        super(chip);
        nrvTracker = new NrvTracker(chip);
        evk4Tracker = new Evk4Tracker(chip);
        prepareTracker(nrvTracker);
        prepareTracker(evk4Tracker);
        FilterChain chain = new FilterChain(chip);
        chain.add(nrvTracker);
        chain.add(evk4Tracker);
        setEnclosedFilterChain(chain);
        setPropertyTooltip("tracker", "locationMixingFactor",
                "How far each event pulls a cluster, as a fraction of the distance to the event. Applied to both cameras.");
        setPropertyTooltip("tracker", "velocityTauMs",
                "Time constant of the velocity lowpass on both cameras, in milliseconds.");
        setPropertyTooltip("tracker", "clusterSize",
                "Starting cluster size as a fraction of each sensor's longer side.");
        setPropertyTooltip("Geometry", "boxWidth", "Box size along x, in mm. The origin is the center.");
        setPropertyTooltip("Geometry", "boxDepth", "Box size along y, in mm. The origin is the center.");
        setPropertyTooltip("Geometry", "boxHeight", "Box size along z, in mm. The origin is the center.");
        setPropertyTooltip("Geometry", "showBox", "Draw the box edges in the 3D window.");
        setPropertyTooltip("Geometry", "nrvDistanceMm",
                "Distance of the NRV lens from the center of the box, in mm. The lens is on the -x side and looks toward +x.");
        setPropertyTooltip("Geometry", "nrvFocalLengthMm", "NRV lens focal length in mm.");
        setPropertyTooltip("Geometry", "evk4DistanceMm",
                "Distance of the EVK4 lens from the center of the box, in mm. The lens is on the +z side and looks toward -z.");
        setPropertyTooltip("Geometry", "evk4FocalLengthMm", "EVK4 lens focal length in mm.");
        setPropertyTooltip("display", "pathLength", "Points kept on each flight.");
        setPropertyTooltip("display", "trajectoriesToShow",
                "Flights kept in the 3D window. A flight ends when either camera loses its cluster. The oldest flight is dropped.");
        setPropertyTooltip("display", "minFlightDuration",
                "Discard a flight shorter than this, in seconds. 0.1 is 100 ms.");
        setPropertyTooltip("display", "markFlights",
                "When a flight reaches minFlightDuration, put a playback marker 100 ms before it started.");
        setPropertyTooltip("ClearFlightMarkers",
                "Remove playback markers placed by markFlights. Manual markers and IN/OUT stay.");
        setPropertyTooltip("ShowViewer", "Open or close the 3D path window.");
        pushSharedParameters();
        watch(nrvTracker);
        watch(evk4Tracker);
        observeAfterClusterUpdate(nrvTracker);
        observeAfterClusterUpdate(evk4Tracker);
    }

    /**
     * {@link java.util.Observable} notifies in reverse registration order.
     * The tracker registers itself first and would otherwise refresh the
     * cluster list after this snapshot. Register it last so the visible
     * cluster is the one just updated.
     */
    private void observeAfterClusterUpdate(RectangularClusterTracker tracker) {
        tracker.deleteObserver(tracker);
        tracker.addObserver(this);
        tracker.addObserver(tracker);
    }

    /** Latest tracker for this preferences node. Renewing the chain replaces it. */
    private static void watch(RectangularClusterTracker tracker) {
        Preferences prefs = tracker.getPrefs();
        ACTIVE.put(prefs, new WeakReference<>(tracker));
        if (WATCHED.add(prefs)) {
            prefs.addPreferenceChangeListener(FlyTracker3D::mirrorPreference);
        }
    }

    /**
     * Preference events arrive on one background thread, after the put has
     * returned, and an import queues one event per key. Events in a burst are
     * collected and the last writer of each property is copied once. Keys
     * written by {@link net.sf.jaer.eventprocessing.EventFilter#importPrefs}
     * are not copied: that import already chooses which filters receive them.
     */
    private static void mirrorPreference(PreferenceChangeEvent evt) {
        String key = evt.getKey();
        if (key == null) {
            return;
        }
        if (MIRROR_FLUSH.equals(key)) {
            applyPending();
            return;
        }
        RectangularClusterTracker from = current(evt.getNode());
        if (from == null || !(from.getEnclosingFilter() instanceof FlyTracker3D fly)) {
            return;
        }
        String header = from.getClass().getSimpleName() + ".";
        if (!key.startsWith(header)) {
            return;
        }
        String prop = key.substring(header.length());
        if (NOT_MIRRORED.contains(prop)) {
            return;
        }
        String value = from.getPrefs().get(key, null);
        if (value == null || (evt.getNewValue() != null && !value.equals(evt.getNewValue()))) {
            return;
        }
        if (EventFilter.consumeImportedPreference(from.getPrefs(), key, value)) {
            return;
        }
        RectangularClusterTracker to = fly.other(from);
        if (to == null) {
            return;
        }
        String peerKey = to.getClass().getSimpleName() + "." + prop;
        if (value.equals(to.getPrefs().get(peerKey, null))) {
            return;
        }
        if (WRITERS.get(prop) == null) {
            return;
        }
        PENDING.put(prop, new WeakReference<>(from));
        if (!mirrorFlushQueued) {
            mirrorFlushQueued = true;
            from.getPrefs().put(MIRROR_FLUSH, Long.toString(System.nanoTime()));
        }
    }

    /** Copy the last writer of each property in this burst onto the other tracker. */
    private static void applyPending() {
        mirrorFlushQueued = false;
        if (PENDING.isEmpty()) {
            return;
        }
        List<Map.Entry<String, WeakReference<RectangularClusterTracker>>> batch = new ArrayList<>(PENDING.entrySet());
        PENDING.clear();
        for (Map.Entry<String, WeakReference<RectangularClusterTracker>> entry : batch) {
            RectangularClusterTracker from = entry.getValue().get();
            if (from == null || !(from.getEnclosingFilter() instanceof FlyTracker3D fly)) {
                continue;
            }
            RectangularClusterTracker to = fly.other(from);
            if (to == null) {
                continue;
            }
            String prop = entry.getKey();
            String value = from.getPrefs().get(from.getClass().getSimpleName() + "." + prop, null);
            if (value == null) {
                continue;
            }
            String peerKey = to.getClass().getSimpleName() + "." + prop;
            if (value.equals(to.getPrefs().get(peerKey, null))) {
                continue;
            }
            PrefWriter writer = WRITERS.get(prop);
            if (writer == null) {
                continue;
            }
            try {
                Object parsed = writer.parse(value);
                if (parsed == null) {
                    continue;
                }
                writer.write.invoke(to, parsed);
                Object now = writer.read == null ? parsed : writer.read.invoke(to);
                to.getSupport().firePropertyChange(prop, null, now);
            } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                log.warning("FlyTracker3D could not copy " + prop + " to " + to.getShortName() + ": " + cause);
            }
        }
    }

    private static RectangularClusterTracker current(Preferences prefs) {
        if (prefs == null) {
            return null;
        }
        WeakReference<RectangularClusterTracker> ref = ACTIVE.get(prefs);
        if (ref == null) {
            return null;
        }
        RectangularClusterTracker tracker = ref.get();
        if (tracker == null) {
            ACTIVE.remove(prefs, ref);
        }
        return tracker;
    }

    private RectangularClusterTracker other(RectangularClusterTracker from) {
        if (from == nrvTracker) {
            return evk4Tracker;
        }
        if (from == evk4Tracker) {
            return nrvTracker;
        }
        return null;
    }

    private static Map<String, PrefWriter> writers() {
        Map<String, PrefWriter> map = new HashMap<>();
        try {
            for (PropertyDescriptor property : Introspector.getBeanInfo(RectangularClusterTracker.class).getPropertyDescriptors()) {
                if (property.getWriteMethod() == null) {
                    continue;
                }
                map.put(property.getName(), new PrefWriter(property.getWriteMethod(), property.getReadMethod(), property.getPropertyType()));
            }
        } catch (IntrospectionException e) {
            log.warning(e.toString());
        }
        return map;
    }

    private static final class PrefWriter {
        final Method write;
        final Method read;
        final Class<?> type;

        PrefWriter(Method write, Method read, Class<?> type) {
            this.write = write;
            this.read = read;
            this.type = type;
        }

        Object parse(String value) {
            if (type == boolean.class || type == Boolean.class) {
                return Boolean.valueOf(value);
            }
            if (type == int.class || type == Integer.class) {
                return Integer.valueOf(value);
            }
            if (type == float.class || type == Float.class) {
                return Float.valueOf(value);
            }
            if (type == double.class || type == Double.class) {
                return Double.valueOf(value);
            }
            if (type == long.class || type == Long.class) {
                return Long.valueOf(value);
            }
            if (type == String.class) {
                return value;
            }
            if (type.isEnum()) {
                return enumValue(type, value);
            }
            return null;
        }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> type, String value) {
        return Enum.valueOf((Class<? extends Enum>) type, value);
    }
}

    private static void prepareTracker(RectangularClusterTracker tracker) {
        tracker.setAnnotationEnabled(false);
        tracker.setFilterEventsEnabled(false);
        tracker.setZoomOnTrackedCluster(false);
        tracker.hideProperty("locationMixingFactor");
        tracker.hideProperty("velocityTauMs");
        tracker.hideProperty("clusterSize");
    }

    /**
     * Closest point of the two pinhole rays, in millimeters. Pixel arguments
     * are offsets from the sensor center. Returns null when a focal length or
     * a distance is not positive, or the rays are parallel.
     */
    public static float[] intersectRays(
            float evk4DuPx, float evk4DvPx, float evk4FocalMm, float evk4DistanceMm,
            float nrvDuPx, float nrvDvPx, float nrvFocalMm, float nrvDistanceMm) {
        if (evk4FocalMm <= 0f || nrvFocalMm <= 0f || evk4DistanceMm <= 0f || nrvDistanceMm <= 0f) {
            return null;
        }
        double ax = (evk4DuPx * EVK4_PITCH_MM) / evk4FocalMm;
        double ay = (evk4DvPx * EVK4_PITCH_MM) / evk4FocalMm;
        double by = (nrvDuPx * NRV_PITCH_MM) / nrvFocalMm;
        double bz = (nrvDvPx * NRV_PITCH_MM) / nrvFocalMm;
        // EVK4 looks along -z: image +x is world -x, image +y is world +y.
        // NRV looks along +x: image +x is world +y, image +y is world +z.
        return closestPoint(
                0, 0, evk4DistanceMm, -ax, ay, -1,
                -nrvDistanceMm, 0, 0, 1, by, bz);
    }

    /**
     * Midpoint of the shortest segment between line {@code p + s d} and line
     * {@code q + t e}. Returns null when the lines are parallel.
     */
    static float[] closestPoint(
            double px, double py, double pz, double dx, double dy, double dz,
            double qx, double qy, double qz, double ex, double ey, double ez) {
        double wx = px - qx;
        double wy = py - qy;
        double wz = pz - qz;
        double a = dx * dx + dy * dy + dz * dz;
        double b = dx * ex + dy * ey + dz * ez;
        double c = ex * ex + ey * ey + ez * ez;
        double d = dx * wx + dy * wy + dz * wz;
        double e = ex * wx + ey * wy + ez * wz;
        double denom = a * c - b * b;
        if (a <= 0 || c <= 0 || Math.abs(denom) <= 1e-12 * a * c) {
            return null;
        }
        double s = (b * e - c * d) / denom;
        double t = (a * e - b * d) / denom;
        double x1 = px + s * dx;
        double y1 = py + s * dy;
        double z1 = pz + s * dz;
        double x2 = qx + t * ex;
        double y2 = qy + t * ey;
        double z2 = qz + t * ez;
        return new float[] {
            (float) (0.5 * (x1 + x2)),
            (float) (0.5 * (y1 + y2)),
            (float) (0.5 * (z1 + z2))
        };
    }

    @Override
    public void initFilter() {
        nrvTracker.initFilter();
        evk4Tracker.initFilter();
        pushSharedParameters();
    }

    @Override
    public void resetFilter() {
        // Playback holds each cluster tracker, then this monitor (the update
        // callback). Rewind runs on the EDT and must take the trackers first,
        // or the two threads deadlock.
        nrvTracker.resetFilter();
        evk4Tracker.resetFilter();
        synchronized (this) {
            synchronized (fixesLock) {
                pendingFixes.clear();
            }
            flights.clear();
            openFlight = null;
            nextColor = 0;
            current = null;
            lastNrv = null;
            lastEvk = null;
            haveSample = false;
            nrvPeriodUs = 0;
            evkPeriodUs = 0;
            loggedRays = false;
        }
        repaintViewer();
    }

    @Override
    public void cleanup() {
        nrvTracker.deleteObserver(this);
        evk4Tracker.deleteObserver(this);
        super.cleanup();
        FlyTracker3DViewer closing;
        synchronized (this) {
            closing = viewer;
            viewer = null;
        }
        if (closing != null) {
            javax.swing.SwingUtilities.invokeLater(closing::dispose);
        }
    }

    @Override
    public void setFilterEnabled(boolean enabled) {
        super.setFilterEnabled(enabled);
        if (!enabled) {
            hideViewer();
        } else if (showViewer) {
            openViewer();
        }
    }

    /** Toggle button on the filter panel. */
    public void doToggleOnShowViewer() {
        showViewer = true;
        if (isFilterEnabled()) {
            openViewer();
        }
    }

    /** Toggle button on the filter panel. Closing the window calls this too. */
    public void doToggleOffShowViewer() {
        showViewer = false;
        hideViewer();
        getSupport().firePropertyChange("doToggleOffShowViewer", Boolean.TRUE, Boolean.FALSE);
    }

    void viewerClosedByUser() {
        if (showViewer) {
            doToggleOffShowViewer();
        }
    }

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        if (!isFilterEnabled() || in == null) {
            return in;
        }
        DeviceSlot nrv = slot("nrv", 0);
        DeviceSlot evk4 = slot("evk4", 1);
        if (nrv == null || evk4 == null || nrv.getChild() == null || evk4.getChild() == null) {
            if (!warnedGeometry) {
                log.warning("FlyTracker3D needs two vision slots (nrv, evk4)");
                warnedGeometry = true;
            }
            return in;
        }
        warnedGeometry = false;
        fill(nrvPacket, in, nrv.getSource());
        fill(evk4Packet, in, evk4.getSource());
        // Cluster tracking can take a long packet. Do not hold this monitor across it:
        // the 3D view paints on the EDT and needs the same monitor to copy the path.
        if (nrvPacket.getSize() > 0) {
            nrvTracker.filterPacket(nrvPacket);
        }
        if (evk4Packet.getSize() > 0) {
            evk4Tracker.filterPacket(evk4Packet);
        }
        List<Trajectory> toMark = new ArrayList<>();
        synchronized (this) {
            replayFixes(toMark);
        }
        for (Trajectory flight : toMark) {
            markFlightStart(flight);
        }
        return in;
    }

    /**
     * A tracker ticked at the filter-chain update interval. The cluster list
     * for that camera has already been refreshed. The fix is stored and
     * applied after both cameras in this packet have run, so a tick is matched
     * with the other camera's latest fix.
     */
    @Override
    public void update(Observable o, Object arg) {
        if (!(o instanceof RectangularClusterTracker source) || !(arg instanceof EventFilter2D.UpdateMessage msg)) {
            return;
        }
        boolean nrv = source == nrvTracker;
        if (!nrv && source != evk4Tracker) {
            return;
        }
        RectangularClusterTracker.Cluster cluster = strongest(source);
        Fix fix = cluster == null
                ? new Fix(msg.timestamp, 0f, 0f, false, nrv)
                : new Fix(msg.timestamp, cluster.getLocation().x, cluster.getLocation().y, true, nrv);
        // The cluster tracker is already locked by filterPacket. Do not lock this
        // filter here: disabling it on the EDT locks this filter, then the tracker.
        synchronized (fixesLock) {
            pendingFixes.add(fix);
        }
    }

    private void replayFixes(List<Trajectory> toMark) {
        List<Fix> batch;
        synchronized (fixesLock) {
            if (pendingFixes.isEmpty()) {
                return;
            }
            batch = new ArrayList<>(pendingFixes);
            pendingFixes.clear();
        }
        batch.sort((a, b) -> Integer.compare(a.t, b.t));
        Fix nrv = lastNrv;
        Fix evk = lastEvk;
        int interval = updateIntervalUs();
        for (Fix fix : batch) {
            if (fix.nrv) {
                int period = periodUs(nrv, fix, interval);
                if (period > 0) {
                    nrvPeriodUs = period;
                }
                nrv = fix;
                lastNrv = fix;
            } else {
                int period = periodUs(evk, fix, interval);
                if (period > 0) {
                    evkPeriodUs = period;
                }
                evk = fix;
                lastEvk = fix;
            }
            consider(fix.t, nrv, evk, interval, toMark);
        }
    }

    /**
     * Gap between two ticks of one camera, ignoring a pause long enough to be
     * a missed track rather than the normal period.
     */
    private static int periodUs(Fix previous, Fix next, int intervalUs) {
        if (previous == null) {
            return 0;
        }
        int dt = next.t - previous.t;
        if (dt <= 0 || dt > intervalUs * 10) {
            return 0;
        }
        return dt;
    }

    /**
     * Record a point when both cameras have a visible cluster and each has
     * updated within two of its own recent periods. One point per interval.
     * A lost cluster ends the flight immediately. One skipped update does not.
     */
    private void consider(int timestamp, Fix nrv, Fix evk, int interval, List<Trajectory> toMark) {
        if (nrv == null || evk == null) {
            return;
        }
        int sinceNrv = timestamp - nrv.t;
        int sinceEvk = timestamp - evk.t;
        int limitNrv = freshLimit(nrvPeriodUs, interval);
        int limitEvk = freshLimit(evkPeriodUs, interval);
        boolean nrvStale = sinceNrv < -limitNrv || sinceNrv > limitNrv;
        boolean evkStale = sinceEvk < -limitEvk || sinceEvk > limitEvk;
        if (nrvStale || evkStale) {
            String cause = nrvStale ? staleCause("NRV", sinceNrv, limitNrv) : null;
            if (evkStale) {
                String evkCause = staleCause("EVK4", sinceEvk, limitEvk);
                cause = cause == null ? evkCause : cause + "; " + evkCause;
            }
            endFlight(cause);
            return;
        }
        if (sinceNrv < 0 || sinceEvk < 0) {
            return;
        }
        if (!nrv.visible || !evk.visible) {
            String which = !nrv.visible && !evk.visible ? "both clusters lost" : !nrv.visible ? "NRV cluster lost" : "EVK4 cluster lost";
            endFlight(which);
            return;
        }
        if (haveSample) {
            int sinceSample = timestamp - lastSampleUs;
            if (sinceSample >= 0 && sinceSample < interval) {
                return;
            }
        }
        DeviceSlot nrvSlot = slot("nrv", 0);
        DeviceSlot evkSlot = slot("evk4", 1);
        if (nrvSlot == null || evkSlot == null || nrvSlot.getChild() == null || evkSlot.getChild() == null) {
            return;
        }
        float[] xyz = intersectRays(
                evk.x - (evkSlot.getChild().getSizeX() - 1) * 0.5f,
                evk.y - (evkSlot.getChild().getSizeY() - 1) * 0.5f,
                evk4FocalLengthMm, evk4DistanceMm,
                nrv.x - (nrvSlot.getChild().getSizeX() - 1) * 0.5f,
                nrv.y - (nrvSlot.getChild().getSizeY() - 1) * 0.5f,
                nrvFocalLengthMm, nrvDistanceMm);
        if (xyz == null) {
            if (!loggedRays) {
                loggedRays = true;
                log.fine("FlyTracker3D no point: rays are parallel or a lens distance or focal length is not positive");
            }
            return;
        }
        if (openFlight == null) {
            openFlight = new Trajectory(nextColor++);
            openFlight.startUs = timestamp;
        }
        current = xyz;
        openFlight.lastUs = timestamp;
        openFlight.points.add(xyz);
        trimFlightPoints(openFlight);
        if (flightLongEnough(openFlight) && !flights.contains(openFlight)) {
            flights.add(openFlight);
            trimFlightCount();
        }
        if (markFlights && flightLongEnough(openFlight) && !openFlight.marked) {
            openFlight.marked = true;
            toMark.add(openFlight);
        }
        haveSample = true;
        lastSampleUs = timestamp;
        repaintViewer();
    }

    /**
     * How old the other camera's last tick may be. Two measured periods cover a
     * clock offset and one skipped update. Until a period is known, four update
     * intervals stand in for that.
     */
    private static int freshLimit(int periodUs, int intervalUs) {
        if (periodUs <= 0) {
            return intervalUs * 4;
        }
        return periodUs * 2 + intervalUs;
    }

    private static String staleCause(String camera, int ageUs, int limitUs) {
        if (ageUs < 0) {
            return String.format("%s update %,d us ahead (allowed %,d us)", camera, -ageUs, limitUs);
        }
        return String.format("%s update %,d us old (allowed %,d us)", camera, ageUs, limitUs);
    }

    /** Same clock as {@link EventFilter2D#maybeCallUpdateObservers}. */
    private int updateIntervalUs() {
        float ms = 1f;
        if (chip != null && chip.getFilterChain() != null) {
            ms = chip.getFilterChain().getUpdateIntervalMs();
        }
        int ticks = (int) ((ms * 1000f) / AEConstants.TICK_DEFAULT_US);
        return Math.max(1, ticks);
    }

    /** Stop appending. The flight stays in the list until a newer one displaces it. */
    private void endFlight(String cause) {
        Trajectory ended = openFlight;
        if (ended == null && current == null) {
            return;
        }
        if (ended != null && !ended.points.isEmpty()) {
            long durationMs = Math.round(((long) ended.lastUs - ended.startUs) / 1000.0);
            String msg = String.format("FlyTracker3D flight %d ended: %d pts, %d ms, %s",
                    ended.colorIndex, ended.points.size(), durationMs, cause);
            if (!flightLongEnough(ended)) {
                flights.remove(ended);
                log.fine(msg + String.format(" (shorter than %.0f ms)", minFlightDuration * 1000f));
            } else {
                log.info(msg);
            }
        }
        openFlight = null;
        current = null;
        repaintViewer();
    }

    /**
     * Marker 100 ms before the first point. Event timestamps wrap every 2^32 µs,
     * so the low 32 bits are placed on the current unwrapped playhead. A marker
     * already within {@link #minFlightDuration} of that time is left as it is.
     */
    private void markFlightStart(Trajectory flight) {
        if (chip == null || chip.getAeViewer() == null) {
            return;
        }
        AbstractAEPlayer player = chip.getAeViewer().getAePlayer();
        if (!(player instanceof AEPlayer aePlayer)) {
            return;
        }
        long atUs = unwrappedFlightUs(aePlayer, flight.startUs) - FLIGHT_MARK_LEAD_US;
        if (atUs < 0L) {
            atUs = 0L;
        }
        long windowUs = Math.max(0L, (long) (minFlightDuration * 1_000_000f));
        long index = aePlayer.addFlightMarkerAtTimestamp(atUs, windowUs);
        if (index < 0L) {
            return;
        }
        log.fine(String.format("FlyTracker3D flight %d marker 100 ms before start t=%,d us",
                flight.colorIndex, flight.startUs));
    }

    /**
     * File playback time of {@code timestampUs}. Values from events are the low
     * 32 bits; a recording longer than ~71 min repeats them. The current playhead
     * supplies the high bits.
     */
    private static long unwrappedFlightUs(AEPlayer player, int timestampUs) {
        long wrapped = timestampUs & 0xffffffffL;
        if (!(player.getAEInputStream() instanceof net.sf.jaer.eventio.aedat4.Aedat4FileInputStream a4)) {
            return wrapped;
        }
        long now = a4.currentPlaybackTimeUs();
        long unwrapped = (now & ~0xffffffffL) + wrapped;
        if (unwrapped > now + 1_000_000L) {
            unwrapped -= 0x1_0000_0000L;
        }
        return unwrapped < 0L ? 0L : unwrapped;
    }

    /** Remove markers placed by {@link #markFlights}. Manual markers and IN/OUT stay. */
    public void doClearFlightMarkers() {
        if (chip == null || chip.getAeViewer() == null) {
            return;
        }
        AbstractAEPlayer player = chip.getAeViewer().getAePlayer();
        if (!(player instanceof AEPlayer aePlayer)) {
            return;
        }
        int n = aePlayer.clearFlightMarkers();
        log.info(String.format("FlyTracker3D cleared %d flight marker%s", n, n == 1 ? "" : "s"));
    }

    /** Event-time span of the flight, compared with {@link #minFlightDuration}. */
    private boolean flightLongEnough(Trajectory flight) {
        long durationUs = (long) flight.lastUs - flight.startUs;
        return durationUs >= (long) (minFlightDuration * 1_000_000f);
    }

    private void trimFlightCount() {
        int keep = Math.max(1, trajectoriesToShow);
        while (flights.size() > keep) {
            flights.remove(0);
        }
    }

    private void trimFlightPoints(Trajectory flight) {
        int keep = Math.max(2, pathLength);
        while (flight.points.size() > keep) {
            flight.points.remove(0);
        }
    }

    private DeviceSlot slot(String id, int visionFallback) {
        if (!(chip instanceof MultiDeviceChip multi)) {
            return null;
        }
        for (DeviceSlot slot : multi.getSlots()) {
            if (id.equals(slot.getId())) {
                return slot;
            }
        }
        List<DeviceSlot> vision = multi.visionSlots();
        if (visionFallback < 0 || visionFallback >= vision.size()) {
            return null;
        }
        return vision.get(visionFallback);
    }

    private static void fill(EventPacket<PolarityEvent> dst, EventPacket<? extends BasicEvent> src, byte source) {
        dst.clear();
        int n = src.getSize();
        OutputEventIterator<PolarityEvent> out = dst.outputIterator();
        for (int i = 0; i < n; i++) {
            BasicEvent event = src.getEvent(i);
            if (!(event instanceof PolarityEvent) || event.source != source) {
                continue;
            }
            out.nextOutput().copyFrom(event);
        }
    }

    private static RectangularClusterTracker.Cluster strongest(RectangularClusterTracker tracker) {
        RectangularClusterTracker.Cluster best = null;
        float mass = Float.NEGATIVE_INFINITY;
        for (RectangularClusterTracker.Cluster cluster : tracker.getVisibleClusters()) {
            if (cluster == null || !cluster.isVisible()) {
                continue;
            }
            if (cluster.getMass() > mass) {
                mass = cluster.getMass();
                best = cluster;
            }
        }
        return best;
    }

    private void pushSharedParameters() {
        nrvTracker.setLocationMixingFactor(locationMixingFactor);
        evk4Tracker.setLocationMixingFactor(locationMixingFactor);
        nrvTracker.setVelocityTauMs(velocityTauMs);
        evk4Tracker.setVelocityTauMs(velocityTauMs);
        pushClusterSize(nrvTracker, slot("nrv", 0));
        pushClusterSize(evk4Tracker, slot("evk4", 1));
    }

    /**
     * {@link RectangularClusterTracker} scales {@code clusterSize} by the chip
     * it was built with, which is the tiled parent. Write the fraction of the
     * child sensor instead.
     */
    private void pushClusterSize(RectangularClusterTracker tracker, DeviceSlot slot) {
        int parent = chip == null ? 1 : Math.max(1, chip.getMaxSize());
        int child = parent;
        if (slot != null && slot.getChild() != null) {
            child = Math.max(1, slot.getChild().getMaxSize());
        }
        tracker.setClusterSize(clusterSize * child / (float) parent);
    }

    private void openViewer() {
        javax.swing.SwingUtilities.invokeLater(() -> {
            if (viewer == null) {
                viewer = new FlyTracker3DViewer(this);
            }
            viewer.setVisible(true);
        });
    }

    private void hideViewer() {
        if (viewer != null) {
            javax.swing.SwingUtilities.invokeLater(() -> viewer.setVisible(false));
        }
    }

    private void repaintViewer() {
        if (viewer != null) {
            viewer.repaint();
        }
    }

    /** Flights still on screen. Each point is {@code {x, y, z}} millimeters. */
    public synchronized List<Trajectory> copyTrajectories() {
        List<Trajectory> copy = new ArrayList<>(flights.size());
        for (Trajectory flight : flights) {
            List<float[]> points = new ArrayList<>(flight.points.size());
            for (float[] point : flight.points) {
                points.add(point.clone());
            }
            copy.add(new Trajectory(flight.colorIndex, points));
        }
        return copy;
    }

    public synchronized float[] getCurrentMm() {
        return current == null ? null : current.clone();
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (!isFilterEnabled() || !(chip instanceof MultiDeviceChip multi)) {
            return;
        }
        DeviceRelation relation = multi.getRelation();
        if (relation == null) {
            return;
        }
        GL2 gl = drawable.getGL().getGL2();
        drawCluster(gl, multi, relation, slot("nrv", 0), nrvTracker, 0.2f, 0.8f, 1f);
        drawCluster(gl, multi, relation, slot("evk4", 1), evk4Tracker, 1f, 0.6f, 0.1f);
    }

    private static void drawCluster(GL2 gl, MultiDeviceChip multi, DeviceRelation relation,
            DeviceSlot slot, RectangularClusterTracker tracker, float r, float g, float b) {
        if (slot == null) {
            return;
        }
        RectangularClusterTracker.Cluster cluster = strongest(tracker);
        if (cluster == null) {
            return;
        }
        float x = cluster.getLocation().x + relation.tileOriginX(multi, slot);
        float y = cluster.getLocation().y + relation.tileOriginY(multi, slot);
        float rad = Math.max(1f, cluster.getRadius());
        gl.glColor3f(r, g, b);
        gl.glLineWidth(1.5f);
        gl.glBegin(GL2.GL_LINE_LOOP);
        gl.glVertex2f(x - rad, y - rad);
        gl.glVertex2f(x + rad, y - rad);
        gl.glVertex2f(x + rad, y + rad);
        gl.glVertex2f(x - rad, y + rad);
        gl.glEnd();
    }

    public float getLocationMixingFactor() {
        return locationMixingFactor;
    }

    public void setLocationMixingFactor(float locationMixingFactor) {
        if (locationMixingFactor < 0f) {
            locationMixingFactor = 0f;
        } else if (locationMixingFactor > 1f) {
            locationMixingFactor = 1f;
        }
        this.locationMixingFactor = locationMixingFactor;
        putFloat("locationMixingFactor", locationMixingFactor);
        nrvTracker.setLocationMixingFactor(locationMixingFactor);
        evk4Tracker.setLocationMixingFactor(locationMixingFactor);
    }

    public float getVelocityTauMs() {
        return velocityTauMs;
    }

    public void setVelocityTauMs(float velocityTauMs) {
        if (velocityTauMs < 0f) {
            velocityTauMs = 0f;
        }
        this.velocityTauMs = velocityTauMs;
        putFloat("velocityTauMs", velocityTauMs);
        nrvTracker.setVelocityTauMs(velocityTauMs);
        evk4Tracker.setVelocityTauMs(velocityTauMs);
    }

    public float getClusterSize() {
        return clusterSize;
    }

    public void setClusterSize(float clusterSize) {
        if (clusterSize < 0f) {
            clusterSize = 0f;
        } else if (clusterSize > 1f) {
            clusterSize = 1f;
        }
        this.clusterSize = clusterSize;
        putFloat("clusterSize", clusterSize);
        pushClusterSize(nrvTracker, slot("nrv", 0));
        pushClusterSize(evk4Tracker, slot("evk4", 1));
    }

    public float getNrvDistanceMm() {
        return nrvDistanceMm;
    }

    public void setNrvDistanceMm(float nrvDistanceMm) {
        this.nrvDistanceMm = Math.max(0f, nrvDistanceMm);
        putFloat("nrvDistanceMm", this.nrvDistanceMm);
    }

    public float getNrvFocalLengthMm() {
        return nrvFocalLengthMm;
    }

    public void setNrvFocalLengthMm(float nrvFocalLengthMm) {
        this.nrvFocalLengthMm = Math.max(0f, nrvFocalLengthMm);
        putFloat("nrvFocalLengthMm", this.nrvFocalLengthMm);
    }

    public float getEvk4DistanceMm() {
        return evk4DistanceMm;
    }

    public void setEvk4DistanceMm(float evk4DistanceMm) {
        this.evk4DistanceMm = Math.max(0f, evk4DistanceMm);
        putFloat("evk4DistanceMm", this.evk4DistanceMm);
    }

    public float getEvk4FocalLengthMm() {
        return evk4FocalLengthMm;
    }

    public void setEvk4FocalLengthMm(float evk4FocalLengthMm) {
        this.evk4FocalLengthMm = Math.max(0f, evk4FocalLengthMm);
        putFloat("evk4FocalLengthMm", this.evk4FocalLengthMm);
    }

    public float getBoxWidth() {
        return boxWidth;
    }

    public void setBoxWidth(float boxWidth) {
        this.boxWidth = Math.max(0f, boxWidth);
        putFloat("boxWidth", this.boxWidth);
        repaintViewer();
    }

    public float getBoxDepth() {
        return boxDepth;
    }

    public void setBoxDepth(float boxDepth) {
        this.boxDepth = Math.max(0f, boxDepth);
        putFloat("boxDepth", this.boxDepth);
        repaintViewer();
    }

    public float getBoxHeight() {
        return boxHeight;
    }

    public void setBoxHeight(float boxHeight) {
        this.boxHeight = Math.max(0f, boxHeight);
        putFloat("boxHeight", this.boxHeight);
        repaintViewer();
    }

    public boolean isShowBox() {
        return showBox;
    }

    public void setShowBox(boolean showBox) {
        this.showBox = showBox;
        putBoolean("showBox", showBox);
        repaintViewer();
    }

    public int getPathLength() {
        return pathLength;
    }

    public void setPathLength(int pathLength) {
        this.pathLength = Math.max(2, pathLength);
        putInt("pathLength", this.pathLength);
        synchronized (this) {
            for (Trajectory flight : flights) {
                trimFlightPoints(flight);
            }
        }
        repaintViewer();
    }

    public boolean isMarkFlights() {
        return markFlights;
    }

    public void setMarkFlights(boolean markFlights) {
        this.markFlights = markFlights;
        putBoolean("markFlights", markFlights);
    }

    public float getMinFlightDuration() {
        return minFlightDuration;
    }

    public void setMinFlightDuration(float minFlightDuration) {
        this.minFlightDuration = Math.max(0f, minFlightDuration);
        putFloat("minFlightDuration", this.minFlightDuration);
        synchronized (this) {
            flights.removeIf(flight -> flight != openFlight && !flightLongEnough(flight));
        }
        repaintViewer();
    }

    public int getTrajectoriesToShow() {
        return trajectoriesToShow;
    }

    public void setTrajectoriesToShow(int trajectoriesToShow) {
        this.trajectoriesToShow = Math.max(1, trajectoriesToShow);
        putInt("trajectoriesToShow", this.trajectoriesToShow);
        synchronized (this) {
            trimFlightCount();
        }
        repaintViewer();
    }

    /** One camera at one update tick. Location is native pixels. */
    private static final class Fix {
        final int t;
        final float x;
        final float y;
        final boolean visible;
        final boolean nrv;

        Fix(int t, float x, float y, boolean visible, boolean nrv) {
            this.t = t;
            this.x = x;
            this.y = y;
            this.visible = visible;
            this.nrv = nrv;
        }
    }

    /** One flight. {@code colorIndex} selects the rotating hue and does not change when older flights are dropped. */
    public static final class Trajectory {
        public final int colorIndex;
        public final List<float[]> points;
        /** Timestamp of the first sample, in the same ticks as the events. */
        private int startUs;
        /** Timestamp of the latest sample. */
        private int lastUs;
        /** A playback marker was already written for this flight. */
        private boolean marked;

        Trajectory(int colorIndex) {
            this(colorIndex, new ArrayList<>());
        }

        Trajectory(int colorIndex, List<float[]> points) {
            this.colorIndex = colorIndex;
            this.points = points;
        }
    }

    /** Enclosed NRV tracker. The panel title is this class name. */
    public static final class NrvTracker extends RectangularClusterTracker {
        public NrvTracker(AEChip chip) {
            super(chip);
        }

        @Override
        public String getShortName() {
            return "RCT nrv";
        }
    }

    /** Enclosed EVK4 tracker. The panel title is this class name. */
    public static final class Evk4Tracker extends RectangularClusterTracker {
        public Evk4Tracker(AEChip chip) {
            super(chip);
        }

        @Override
        public String getShortName() {
            return "RCT evk4";
        }
    }

}
