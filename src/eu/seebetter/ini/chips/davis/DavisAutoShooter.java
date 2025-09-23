/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import java.awt.Font;
import java.util.HashMap;
import java.util.LinkedList;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.awt.TextRenderer;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
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
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class DavisAutoShooter extends EventFilter2D implements FrameAnnotater {

    private final EventRateEstimator eventRateEstimator = new EventRateEstimator(chip);
    private final RectangularClusterTracker tracker = new RectangularClusterTracker(chip);
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
        setEnclosedFilterChain(chain);
        final String count = "Event Count", rate = "Event Rate", track = "Tracker";
        setPropertyTooltip("showAnnotation", "draws the bars to show frame capture status");
        setPropertyTooltip(count, "eventCountThresholdKEvents", "shots are triggered every this many thousand DVS events");
        setPropertyTooltip(count, "useEventCount", "use an accumulated event count criteria");
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
                || (useEventCount && (eventsSinceLastShot > (eventCountThresholdKEvents << 10))) || (newClusterFound)
                || (maxDistance > getTrackerMovementPixelsForNewFrame())) {
            // trigger shot
            eventsSinceLastShot = 0;
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
        super.setFilterEnabled(yes);
        // TODO reenable fixed frame rate capture here

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
