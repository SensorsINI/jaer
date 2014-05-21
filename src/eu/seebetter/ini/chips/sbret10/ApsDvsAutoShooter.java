/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.sbret10;

import java.awt.Font;

import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.EventRateEstimator;
import net.sf.jaer.graphics.FrameAnnotater;

import com.jogamp.opengl.util.awt.TextRenderer;

import eu.seebetter.ini.chips.ApsDvsChip;
import javax.media.opengl.GL2;

/**
 * Triggers snapshots of APS frames based on sensor data stream.
 *
 * @author Tobi
 */
@Description("Triggers snapshots of APS frames based on sensor data stream.")
public class ApsDvsAutoShooter extends EventFilter2D implements FrameAnnotater {

    private EventRateEstimator eventRateEstimator = new EventRateEstimator(chip);
    private TextRenderer textRenderer = new TextRenderer(new Font("Monospaced", Font.BOLD, 24));
    private float eventRateThresholdHz = getFloat("eventRateThresholdHz", 50000);
    private int eventCountThresholdKEvents = getInt("eventCountThresholdKEvents", 100);
    private boolean showAnnotation=getBoolean("showAnnotation", true);
    private int eventsSinceLastShot = 0;
    private boolean snapshotTriggered = false;
    private boolean uninitialized=true;

    public ApsDvsAutoShooter(AEChip chip) {
        super(chip);
        if (!(chip instanceof ApsDvsChip)) {
            throw new RuntimeException("AEChip needs to be ApsDvsChip to use ApsDvsAutoShooter");
        }
        FilterChain chain = new FilterChain(chip);
        chain.add(eventRateEstimator);
        setEnclosedFilterChain(chain);
        setPropertyTooltip("eventCountThresholdKEvents", "shots are triggered every this many thousand DVS events");
        setPropertyTooltip("eventRateThresholdHz", "shots are triggered whenever the DVS event rate in Hz is above this value");
        setPropertyTooltip("showAnnotation", "draws the bars to show frame capture status");
    }

    @Override
    synchronized public void annotate(GLAutoDrawable drawable) {
        if(!showAnnotation) return;
        GL2 gl = drawable.getGL().getGL2();
        gl.glColor3f(0,0,1);
        float x1=chip.getSizeX()*(float)(eventsSinceLastShot>>10)/eventCountThresholdKEvents;
        gl.glRectf(0,0, x1, 2);
        float x2=chip.getSizeX()*(float)(eventRateEstimator.getFilteredEventRate())/eventRateThresholdHz;
        gl.glRectf(0,4, x2, 6);
        
        textRenderer.setColor(1, 1, 1, 0.4f); //rgba
        textRenderer.begin3DRendering();
        String s = String.format("kevents accum.: %6d, rate keps: %8.2f, snapshot triggered=%s", eventsSinceLastShot >> 10, eventRateEstimator.getFilteredEventRate()*1e-3f, snapshotTriggered);
        textRenderer.draw3D(s, 0, 0, 0, .25f);
        textRenderer.end3DRendering();
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        checkOutputPacketEventType(in);
        eventRateEstimator.filterPacket(in);
        eventsSinceLastShot += eventRateEstimator.getNumEventsInLastPacket();
        if (uninitialized || (eventRateEstimator.getFilteredEventRate() > eventRateThresholdHz) || (eventsSinceLastShot > (eventCountThresholdKEvents << 10))) {
            // trigger shot
            eventsSinceLastShot = 0;
            snapshotTriggered = true;
            ((ApsDvsChip) chip).takeSnapshot();
            uninitialized=false;
        } else {
            snapshotTriggered = false;
        }
        return in;
    }

    @Override
    public void resetFilter() {
        eventRateEstimator.resetFilter();
        eventsSinceLastShot = 0;
        uninitialized=true;
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

    /**
     * @param eventRateThresholdHz the eventRateThresholdHz to set
     */
    public void setEventRateThresholdHz(float eventRateThresholdHz) {
        this.eventRateThresholdHz = eventRateThresholdHz;
        putFloat("eventRateThresholdHz", eventRateThresholdHz);
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
    public void setEventCountThresholdKEvents(int eventCountThresholdKEvents) {
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
    public void setShowAnnotation(boolean showAnnotation) {
        this.showAnnotation = showAnnotation;
        putBoolean("showAnnotation",showAnnotation);
    }
}
