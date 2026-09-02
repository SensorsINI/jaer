/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import java.awt.Font;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.LinkedList;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.awt.TextRenderer;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.Help;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.AreaEventCountExposer;
import net.sf.jaer.eventprocessing.filter.EventRateEstimator;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker.Cluster;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 * Triggers snapshots of APS frames based on sensor data stream.
 *
 * @author Tobi
 */
@Description("Triggers snapshots of DAVIS APS frames based on DVS data stream")
@Help("""
<html>
<body>
<h2>DavisAutoShooter</h2>
<p>Triggers DAVIS <b>APS intensity frames</b> from the live DVS event stream so you
do not need a fixed frame rate. 
Typical uses: Boring survelliance or observation of bursty activity scenss 
like human or animal sleep, human spaces, wildlife. 
Requires a DAVIS chip 
(<code>DavisChip</code> / <code>DavisBaseCamera</code>).
(And later, newer HVS like CDAVIS or recent ISSCC publications from
Sony or Omnivision)</p>
<p>Enclosed filters: <code>EventRateEstimator</code> (rate and event count),
<code>RectangularClusterTracker</code> (optional motion trigger), and
<code>AreaEventCountExposer</code> (kept visible; enable via
<code>useAreaEventCount</code>, then set numAreas / eventCount / showAreas
on the enclosed filter).</p>
<hr>
<h3>Operating modes</h3>
<p>Two mutually exclusive styles of capture:</p>
<ol>
<li><b>Snapshot mode</b> (default) &mdash; calls
<code>DavisChip.takeSnapshot()</code> when any enabled criterion fires.
Criteria are OR&rsquo;d. The first packet after reset always shoots
(<code>uninitialized</code>).</li>
<li><b>Quiet-scene continuous capture</b> &mdash; if
<code>shootFramesWhenDVSEventRateBelowThreshold</code> is on, the other
triggers are ignored. APS capture and APS display are enabled only while
the filtered DVS rate is <i>below</i> <code>eventRateThresholdHz</code>
(scene is still). Above that rate, frames are turned off.</li>
</ol>
<h3>Snapshot trigger criteria</h3>
<p>Enable any combination. A snapshot fires when <b>any</b> enabled
condition is true:</p>
<ul>
<li><code>useEventRateThreshold</code> &mdash; two complementary rate
rules:
<ul>
<li><b>Activity window:</b> shoot while the filtered rate is
<i>above</i> <code>eventRateThresholdHz</code> and <i>below</i>
<code>blurEventRateThresholdHz</code> (enough motion to be interesting,
not so much that the APS exposure would blur).</li>
<li><b>Settle after blur:</b> if the rate exceeds
<code>blurEventRateThresholdHz</code>, an internal flag is set and
shooting is delayed; a snapshot is taken once the rate later drops
<i>below</i> <code>eventRateThresholdHz</code>.</li>
</ul>
</li>
<li><code>useEventCount</code> &mdash; shoot every
<code>eventCountThresholdKEvents</code> thousand DVS events
(threshold is in kevents; the filter counts raw events,
<code>&times; 1024</code>).</li>
<li><code>useAreaEventCount</code> &mdash; shoot when any of the
enclosed <code>AreaEventCountExposer</code> spatial cells reaches
its event count (default 32 areas, 1000 events).</li>
<li><code>useTracker</code> &mdash; shoot when the enclosed
<code>RectangularClusterTracker</code> finds a <b>new</b> visible
cluster, or when an existing cluster has moved at least
<code>trackerMovementPixelsForNewFrame</code> pixels since the last
comparison.</li>
</ul>
<h3>Parameters</h3>
<ul>
<li><code>eventRateThresholdHz</code> &mdash; lower rate gate (default
50&nbsp;kHz). In snapshot mode, start of the activity window (and
end of the settle-after-blur wait). In quiet-scene mode, APS is on
only below this rate.</li>
<li><code>blurEventRateThresholdHz</code> &mdash; upper rate gate
(default 100&nbsp;kHz). Above this, snapshots are postponed until
activity falls again.</li>
<li><code>eventCountThresholdKEvents</code> &mdash; kevents between
count-triggered snapshots (default 100).</li>
<li><code>trackerMovementPixelsForNewFrame</code> &mdash; cluster
travel (pixels) that counts as &ldquo;moved enough&rdquo; (default 5).</li>
<li><code>showAnnotation</code> &mdash; overlay: a bar for accumulated
events vs. the count threshold, a bar for rate vs.
<code>eventRateThresholdHz</code>, and text
(<code>kevents accum.</code>, rate in keps, whether a snapshot was
just triggered).</li>
</ul>
<h3>How to use</h3>
<ol>
<li>Select a DAVIS chip and open the camera (APS must be available).</li>
<li>Add <b>DavisAutoShooter</b> and enable it. Leave
<code>shootFramesWhenDVSEventRateBelowThreshold</code> off unless you
want continuous frames only when the DVS is quiet.</li>
<li>Pick criteria: rate window, event count, and/or tracker. Turn off
the ones you do not want so they do not fire extra shots.</li>
<li>Tune <code>eventRateThresholdHz</code> /
<code>blurEventRateThresholdHz</code> against the on-screen rate bar
and the keps readout. Raise the blur threshold if shots never fire
because the scene is always &ldquo;too busy&rdquo;; lower it if frames
are smeared.</li>
<li>For object-driven capture, enable <code>useTracker</code> and
adjust the enclosed tracker (cluster size, threshold, etc.) as well
as <code>trackerMovementPixelsForNewFrame</code>.</li>
</ol>
<p>This filter does not process or rewrite events; the input packet is
passed through. It only decides when to trigger an APS frame capture.</p>
<p> Note that autoexposure may not work well 
with this filter, because it will be triggered by the bursty activity.</p>
</body>
</html>
""")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class DavisAutoShooter extends EventFilter2D implements FrameAnnotater {

    private final EventRateEstimator eventRateEstimator = new EventRateEstimator(chip);
    private final RectangularClusterTracker tracker = new RectangularClusterTracker(chip);
    private final AreaEventCountExposer areaEventCountExposer = new AreaEventCountExposer(chip);
    private TextRenderer textRenderer = null;
    private float eventRateThresholdHz = getFloat("eventRateThresholdHz", 50000);
    private float blurEventRateThresholdHz = getFloat("blurEventRateThresholdHz", 100000);
    private int eventCountThresholdKEvents = getInt("eventCountThresholdKEvents", 100);
    private boolean showAnnotation = getBoolean("showAnnotation", true);
    private int eventsSinceLastShot = 0;
    private boolean snapshotTriggered = false;
    private boolean uninitialized = true;
    private boolean activityFlag = false;
    private boolean useTracker = getBoolean("useTracker", false);
    private boolean useEventCount = getBoolean("useEventCount", true);
    private boolean useAreaEventCount = getBoolean("useAreaEventCount", false);
    /** Guards useAreaEventCount ↔ enclosed exposer filterEnabled so the two setters do not recurse. */
    private boolean linkingAreaEventCountEnable;
    private boolean useEventRateThreshold = getBoolean("useEventRateThreshold", true);
    private int trackerMovementPixelsForNewFrame = getInt("trackerMovementPixelsForNewFrame", 5);
    private boolean shootFramesWhenDVSEventRateBelowThreshold = getBoolean("shootFramesWhenDVSEventRateBelowThreshold", false);
    private final HashMap<Cluster, Cluster> oldClusters = new HashMap();

    public DavisAutoShooter(final AEChip chip) {
        super(chip);
        if (!(chip instanceof DavisChip)) {
            throw new RuntimeException("AEChip needs to be ApsDvsChip to use ApsDvsAutoShooter");
        }
        final FilterChain chain = new FilterChain(chip);
        chain.add(eventRateEstimator);
        chain.add(tracker);
        chain.add(areaEventCountExposer);
        setEnclosedFilterChain(chain);
        setHideNonEnabledEnclosedFilters(false); // keep AreaEventCountExposer visible so its parameters can be edited
        areaEventCountExposer.setEventExposureMode(AreaEventCountExposer.EventExposureMode.AreaEventCount);
        areaEventCountExposer.setFilterEnabled(useAreaEventCount);
        areaEventCountExposer.getSupport().addPropertyChangeListener("filterEnabled", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (linkingAreaEventCountEnable) {
                    return;
                }
                Object nv = evt.getNewValue();
                if (nv instanceof Boolean && useAreaEventCount != (Boolean) nv) {
                    setUseAreaEventCount((Boolean) nv);
                }
            }
        });
        final String count = "Event Count", rate = "Event Rate", track = "Tracker", area = "Area Event Count";
        setPropertyTooltip("showAnnotation", "draws the bars to show frame capture status");
        setPropertyTooltip(count, "eventCountThresholdKEvents", "shots are triggered every this many thousand DVS events");
        setPropertyTooltip(count, "useEventCount", "use an accumulated event count criteria");
        setPropertyTooltip(area, "useAreaEventCount",
                "shoot when any spatial area in the enclosed AreaEventCountExposer reaches its event count (enables that enclosed filter; expand it for numAreas, eventCount, showAreas)");
        setPropertyTooltip(rate, "eventRateThresholdHz", "shots are triggered whenever the DVS event rate in Hz is above this value");
        setPropertyTooltip(rate, "blurEventRateThresholdHz", "shots are delayed whenever the DVS event rate in Hz is above this value");
        setPropertyTooltip(rate, "useEventRateThreshold", "use an event rate criteria");
        setPropertyTooltip(rate, "shootFramesWhenDVSEventRateBelowThreshold", "only turn on APS frame capture when DVS event rate is below eventRateThresholdHz (disables all other frame capture modes)");
        setPropertyTooltip(track, "useTracker", "use the object tracker to determine whether to trigger new frame capture");
        setPropertyTooltip(track, "trackerMovementPixelsForNewFrame",
                "at least one tracker cluster must move this many pixels (or any new visible cluster must be found) to trigger a new frame capture");
        tracker.setFilterEnabled(useTracker);
    }

    @Override
    synchronized public void annotate(final GLAutoDrawable drawable) {
        if (!showAnnotation) {
            return;
        }
        final GL2 gl = drawable.getGL().getGL2();
        gl.glColor3f(0, 0, 1);
        final float x1 = (chip.getSizeX() * ((float) (eventsSinceLastShot >> 10))) / eventCountThresholdKEvents;
        gl.glRectf(0, 0, x1, 2);
        final float x2 = (chip.getSizeX() * ((eventRateEstimator.getFilteredEventRate()))) / eventRateThresholdHz;
        gl.glRectf(0, 4, x2, 6);
        if (useAreaEventCount && areaEventCountExposer.getEventCount() > 0) {
            gl.glColor3f(0, 0.6f, 0);
            final float x3 = (chip.getSizeX() * ((float) areaEventCountExposer.getMaxAreaCount())) / areaEventCountExposer.getEventCount();
            gl.glRectf(0, 8, x3, 10);
        }

        textRenderer=new TextRenderer(new Font("Monospaced", Font.BOLD, 24));
        textRenderer.setColor(1, 1, 1, 0.4f); // rgba
        textRenderer.begin3DRendering();
        final String s = String.format("kevents accum.: %4d, rate keps: %6.2f, snapshot triggered=%s", eventsSinceLastShot >> 10,
                eventRateEstimator.getFilteredEventRate() * 1e-3f, snapshotTriggered);
        textRenderer.draw3D(s, 0, 0, 0, .25f);
        textRenderer.end3DRendering();
    }

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(final EventPacket<? extends BasicEvent> in) {
        tracker.setFilterEnabled(useTracker); // have to set again because setting is set by FilterFrame or something
        checkOutputPacketEventType(in);
        getEnclosedFilterChain().filterPacket(in);
        eventsSinceLastShot += eventRateEstimator.getNumEventsInLastPacket();
        final float maxDistance = 0;
        boolean newClusterFound = false;
        if (isUseTracker()) {
            final LinkedList<Cluster> clusters = tracker.getVisibleClusters();
            for (final Cluster c : clusters) {
                if (oldClusters.containsKey(c)) {
                    float d = oldClusters.get(c).distanceTo(c);
                    if (d > maxDistance) {
                        d = maxDistance;
                    }
                } else {
                    newClusterFound = true;
                }
            }
        }

        if (shootFramesWhenDVSEventRateBelowThreshold) {
            if(eventRateEstimator.getFilteredEventRate()<eventRateThresholdHz){
                 ((DavisBaseCamera) chip).getDavisConfig().setCaptureFramesEnabled(true);
                 ((DavisBaseCamera) chip).getDavisConfig().setDisplayFrames(true);
            }else{
                 ((DavisBaseCamera) chip).getDavisConfig().setCaptureFramesEnabled(false);
                 ((DavisBaseCamera) chip).getDavisConfig().setDisplayFrames(false);
            }

        } else if (uninitialized || (useEventRateThreshold && (eventRateEstimator.getFilteredEventRate() < eventRateThresholdHz) && activityFlag)
                || (useEventRateThreshold && (eventRateEstimator.getFilteredEventRate() > eventRateThresholdHz)
                && (eventRateEstimator.getFilteredEventRate() < blurEventRateThresholdHz))
                || (useEventCount && (eventsSinceLastShot > (eventCountThresholdKEvents << 10)))
                || (useAreaEventCount && areaEventCountExposer.isExposed()) || (newClusterFound)
                || (maxDistance > getTrackerMovementPixelsForNewFrame())) {
            // trigger shot
            eventsSinceLastShot = 0;
            areaEventCountExposer.resetAccumulation();
            snapshotTriggered = true;
            ((DavisChip) chip).takeSnapshot();
            uninitialized = false;
            activityFlag = false;
        } else if (useEventRateThreshold && (eventRateEstimator.getFilteredEventRate() > blurEventRateThresholdHz)) {
            activityFlag = true;
        } else {
            snapshotTriggered = false;
        }
        return in;
    }

    @Override
    public void resetFilter() {
        eventRateEstimator.resetFilter();
        areaEventCountExposer.resetFilter();
        eventsSinceLastShot = 0;
        uninitialized = true;
    }

    @Override
    public void initFilter() {
        resetFilter();
    }

    /**
     * @return the eventRateThresholdHz
     */
    public float getEventRateThresholdHz() {
        return eventRateThresholdHz;
    }

    public void setEventRateThresholdHz(final float eventRateThresholdHz) {
        this.eventRateThresholdHz = eventRateThresholdHz;
        putFloat("eventRateThresholdHz", eventRateThresholdHz);
    }

    public float getBlurEventRateThresholdHz() {
        return blurEventRateThresholdHz;
    }

    public void setBlurEventRateThresholdHz(final float blurEventRateThresholdHz) {
        this.blurEventRateThresholdHz = blurEventRateThresholdHz;
        putFloat("blurEventRateThresholdHz", blurEventRateThresholdHz);
    }

    /**
     * @return the eventCountThresholdKEvents
     */
    public int getEventCountThresholdKEvents() {
        return eventCountThresholdKEvents;
    }

    /**
     * @param eventCountThresholdKEvents the eventCountThresholdKEvents to set
     */
    public void setEventCountThresholdKEvents(final int eventCountThresholdKEvents) {
        this.eventCountThresholdKEvents = eventCountThresholdKEvents;
        putInt("eventCountThresholdKEvents", eventCountThresholdKEvents);
    }

    /**
     * @return the showAnnotation
     */
    public boolean isShowAnnotation() {
        return showAnnotation;
    }

    /**
     * @param showAnnotation the showAnnotation to set
     */
    public void setShowAnnotation(final boolean showAnnotation) {
        this.showAnnotation = showAnnotation;
        putBoolean("showAnnotation", showAnnotation);
    }

    /**
     * @return the useTracker
     */
    public boolean isUseTracker() {
        return useTracker;
    }

    /**
     * @param useTracker the useTracker to set
     */
    public void setUseTracker(final boolean useTracker) {
        this.useTracker = useTracker;
        putBoolean("useTracker", useTracker);
        if (!useTracker && (tracker != null)) {
            tracker.resetFilter();
        }
        if (tracker != null) {
            tracker.setFilterEnabled(useTracker);
        }
    }

    /**
     * @return the trackerMovementPixelsForNewFrame
     */
    public int getTrackerMovementPixelsForNewFrame() {
        return trackerMovementPixelsForNewFrame;
    }

    /**
     * @param trackerMovementPixelsForNewFrame the
     * trackerMovementPixelsForNewFrame to set
     */
    public void setTrackerMovementPixelsForNewFrame(final int trackerMovementPixelsForNewFrame) {
        this.trackerMovementPixelsForNewFrame = trackerMovementPixelsForNewFrame;
        putInt("trackerMovementPixelsForNewFrame", trackerMovementPixelsForNewFrame);
    }

    /**
     * @return the useEventCount
     */
    public boolean isUseEventCount() {
        return useEventCount;
    }

    /**
     * @param useEventCount the useEventCount to set
     */
    public void setUseEventCount(final boolean useEventCount) {
        this.useEventCount = useEventCount;
        putBoolean("useEventCount", useEventCount);
    }

    public boolean isUseAreaEventCount() {
        return useAreaEventCount;
    }

    public void setUseAreaEventCount(final boolean useAreaEventCount) {
        boolean old = this.useAreaEventCount;
        this.useAreaEventCount = useAreaEventCount;
        putBoolean("useAreaEventCount", useAreaEventCount);
        if (areaEventCountExposer != null && !linkingAreaEventCountEnable) {
            linkingAreaEventCountEnable = true;
            try {
                areaEventCountExposer.setFilterEnabled(useAreaEventCount);
                if (!useAreaEventCount) {
                    areaEventCountExposer.resetAccumulation();
                }
            } finally {
                linkingAreaEventCountEnable = false;
            }
        }
        getSupport().firePropertyChange("useAreaEventCount", old, this.useAreaEventCount);
    }

    /**
     * @return the useEventRateThreshold
     */
    public boolean isUseEventRateThreshold() {
        return useEventRateThreshold;
    }

    /**
     * @param useEventRateThreshold the useEventRateThreshold to set
     */
    public void setUseEventRateThreshold(final boolean useEventRateThreshold) {
        this.useEventRateThreshold = useEventRateThreshold;
        putBoolean("useEventRateThreshold", useEventRateThreshold);
    }

    @Override
    public synchronized void setFilterEnabled(final boolean yes) {
        linkingAreaEventCountEnable = true;
        try {
            super.setFilterEnabled(yes);
            if (areaEventCountExposer != null) {
                areaEventCountExposer.setFilterEnabled(yes && useAreaEventCount);
            }
            if (tracker != null) {
                tracker.setFilterEnabled(yes && useTracker);
            }
        } finally {
            linkingAreaEventCountEnable = false;
        }
    }

    /**
     * @return the shootFramesWhenDVSEventRateBelowThreshold
     */
    public boolean isShootFramesWhenDVSEventRateBelowThreshold() {
        return shootFramesWhenDVSEventRateBelowThreshold;
    }

    /**
     * @param shootFramesWhenDVSEventRateBelowThreshold the
     * shootFramesWhenDVSEventRateBelowThreshold to set
     */
    public void setShootFramesWhenDVSEventRateBelowThreshold(boolean shootFramesWhenDVSEventRateBelowThreshold) {
        this.shootFramesWhenDVSEventRateBelowThreshold = shootFramesWhenDVSEventRateBelowThreshold;
        putBoolean("shootFramesWhenDVSEventRateBelowThreshold", selected);
    }

}
