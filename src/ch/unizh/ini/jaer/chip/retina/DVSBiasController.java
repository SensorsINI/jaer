/*
 *
 * Created on January 9, 2006, 10:41 AM
 * Cloned from DVSBiasController Feb 2011 by Tobi
 *
 */
package ch.unizh.ini.jaer.chip.retina;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.SwingUtilities;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.gl2.GLUT;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.Help;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PacketType;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.FilterFrame;
import net.sf.jaer.eventprocessing.filter.EventRateEstimator;
import net.sf.jaer.eventprocessing.filter.SpatioTemporalCorrelationFilter;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.TobiLogger;

/**
 * Controls the rate of events from the retina by controlling retina biases. The
 * event threshold is increased if rate exceeds eventRateHighHz until rate drops
 * below eventRateHighHz. The threshold is decreased if rate is lower than
 * eventRateLowHz. Hysteresis limits crossing noise. A lowpass filter smooths
 * the rate measurements.
 *
 * @author tobi
 */
@Description("Adaptively controls biases on DVS sensors (that implement DVSTweaks on their bias generator) to control event rate")
@Help("""
<html>
<body>
<h2>DVSBiasController</h2>
<p>Closed-loop <b>DVSTweaks</b> on chips that support them: raises threshold / refractory
when the rate is too high, lowers them when too low, or targets SNR / noise limits.
A low-pass rate estimate and hysteresis avoid chatter.</p>
<p>Also on the Biasgen <b>DVS Auto Controller</b> tab for DVS128, DAVIS, Prophesee, and NRV.
That path auto-installs this filter <b>disabled</b> and with <code>outputRawInput=true</code>
so enabling auto-control does not denoise the live stream. Experts can still open the
same instance from the Filter menu.</p>
<hr>
<h3>How to use</h3>
<ol>
<li>Open a live DVS/DAVIS (or playback; rates are measured either way, but biases
are sent only when hardware is present).</li>
<li>Enable this filter (or the Biasgen tab Enable checkbox).</li>
<li>Set <code>goal</code>: BoundEventRate (threshold), LimitEventRate (refractory),
TargetSNR / LimitNoise (bandwidth), or None.</li>
<li>For rate bounds: <code>eventRateLowHz</code> / <code>eventRateHighHz</code>
and <code>eventRateBoundsHysteresisFactor</code>.</li>
<li><code>tweakStepAmount</code> / <code>minCommandIntervalMs</code> /
<code>ignoreEventsAfterBiasChangeMs</code> limit how fast biases move (bias changes cause noise).</li>
<li><code>revertAllTweaks</code> returns threshold/bandwidth/refractory tweaks to 0.</li>
</ol>
<p>Enclosed <code>SpatioTemporalCorrelationFilter</code> estimates noise for SNR goals
(<code>correlationTimeS</code>). With <code>outputRawInput</code> (default), the live
packet is passed through and <code>filteredOut</code> flags are cleared after the
noise estimate. <code>showAnnotation</code> overlays state. Optional CSV logging
from FilterFrame Options.</p>
</body>
</html>
""")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class DVSBiasController extends EventFilter2D implements FrameAnnotater {

    public static final String EVENT_INPUT_RATE = "inputEventRate";
    public static final String EVENT_SIGNAL_RATE = "signalEventRate";
    public static final String EVENT_NOISE_RATE = "noiseEventRate";
    public static final String EVENT_SNR = "snr";
    public static final String EVENT_GOAL = "goal";
    public static final String EVENT_RATE_STATE = "eventRateState";
    public static final String EVENT_NOISE_STATE = "noiseEventRateState";
    public static final String EVENT_SNR_STATE = "snrState";
    public static final String EVENT_THRESHOLD_TWEAK = "thresholdTweak";
    public static final String EVENT_BANDWIDTH_TWEAK = "bandwidthTweak";
    public static final String EVENT_MAX_FIRING_RATE_TWEAK = "maxFiringRateTweak";
    public static final String EVENT_CONTROL_STATE = "controlState";

    public enum Goal {
        None, BoundEventRate, TargetSNR, LimitNoise, LimitEventRate
    }

    private Goal goal = Goal.None;

    private float eventRateHighHz = getFloat("eventRateHighHz", 1e6f);
    private float eventRateLowHz = getFloat("eventRateLowHz", 100e3f);
    private float eventRateBoundsHysteresisFactor = getFloat("eventRateBoundsHysteresisFactor", 1.3f);
    protected float snrHysteresis = getFloat("snrHysteresis", .2f);
    private int minCommandIntervalMs = getInt("minCommandIntervalMs", 300);
    protected int ignoreEventsAfterBiasChangeMs = getInt("ignoreEventsAfterBiasChangeMs", 100);
    private long lastBiasChangeTimeMs = 0;
    private float tweakStepAmount = getFloat("tweakStepAmount", .01f);
    private boolean showAnnotation = getBoolean("showAnnotation", true);
    protected boolean outputRawInput = getBoolean("outputRawInput", true);
    private EventRateEstimator denoisedRateEstimator, inputRateEstimator;
    private SpatioTemporalCorrelationFilter noiseFilter;
    final GLUT glut = new GLUT();
    TobiLogger tobiLogger;
    private boolean writeLogEnabled = false;
    long timeNowMs = 0;
    private float signalEventRate = Float.NaN;
    private float inputEventRate = Float.NaN;
    private float noiseEventRate = Float.NaN;
    private float snr = Float.NaN;
    protected float targetSNR = getFloat("targetSNR", 0);
    protected float noiseLimitHzPerPixel = getFloat("noiseLimitHzPerPixel", .2f);
    private DVSTweaks dvsTweaks = null;
    private long lastUiFireMs;

    enum EventRateState {

        INITIAL, LOW_RATE, MEDIUM_RATE, HIGH_RATE;
        private long timeChanged = 0;

        long getTimeChanged() {
            return timeChanged;
        }

        void setTimeChanged(long t) {
            timeChanged = t;
        }

        @Override
        public String toString() {
            switch (this) {
                case HIGH_RATE:
                    return "Event rate above high bound";
                case MEDIUM_RATE:
                    return "Event rate within bounds";
                case LOW_RATE:
                    return "Event rate below low bound";
                default:
                    return "Initial state";
            }
        }
    }

    EventRateState eventRateState = EventRateState.INITIAL, noiseEventRateState = EventRateState.INITIAL;
    EventRateState lastEventRateState = EventRateState.INITIAL;

    enum SNRState {

        INITIAL, BELOW_TARGET, ABOVE_TARGET;
        private long timeChanged = 0;

        long getTimeChanged() {
            return timeChanged;
        }

        void setTimeChanged(long t) {
            timeChanged = t;
        }

        @Override
        public String toString() {
            switch (this) {
                case BELOW_TARGET:
                    return "SNR below target";
                case ABOVE_TARGET:
                    return "SNR above target";
                default:
                    return "Initial state";
            }
        }
    }

    SNRState snrState = SNRState.INITIAL;

    public DVSBiasController(AEChip chip) {
        super(chip);

        try {
            goal = Goal.valueOf(getString("goal", Goal.BoundEventRate.toString()));
        } catch (Exception e) {
            goal = Goal.None;
        }
        inputRateEstimator = new EventRateEstimator(chip);
        noiseFilter = new SpatioTemporalCorrelationFilter(chip);
        noiseFilter.setLetFirstEventThrough(false);
        denoisedRateEstimator = new EventRateEstimator(chip);
        FilterChain chain = new FilterChain(chip);
        chain.add(inputRateEstimator);
        chain.add(noiseFilter);
        chain.add(denoisedRateEstimator);
        setEnclosedFilterChain(chain);
        final String tw = "0. Tweaks", type = "1. Type of control", rates = "2. Control Event rates, SNR",
                policy = "3. Bang-bang policy", display = "4. Display", options = "5. Options";

        setPropertyTooltip(tw, "maxFiringRateTweak", "refractory tweak");
        setPropertyTooltip(tw, "thresholdTweak", "threshold tweak");
        setPropertyTooltip(tw, "bandwidthTweak", "bandwidth tweak");
        setPropertyTooltip(options, "revertAllTweaks", "revert all bias tweaks to zero");
        setPropertyTooltip(options, "outputRawInput",
                "Pass the live stream through (clears filteredOut). Unselected outputs the denoised packet.");
        setPropertyTooltip(rates, "eventRateLowHz", "event rate in Hz for LOW state, where event threshold or refractory period are reduced");
        setPropertyTooltip(rates, "eventRateHighHz", "event rate in Hz for HIGH state, where event threshold or refractory period are increased");
        setPropertyTooltip(rates, "targetSNR", "For goal TargetSNR: minimum SNR to target by bandwidth control");
        setPropertyTooltip(rates, "noiseLimitHzPerPixel", "For goal LimitNoise, target noise rate per pixel");
        setPropertyTooltip(rates, "eventRateTauMs", "Time windows over which to measure event rates");
        setPropertyTooltip(policy, "snrHysteresis",
                "hysteresis for SNR; after state entry, state exited only when SNR crosses target by this much");
        setPropertyTooltip(policy, "eventRateBoundsHysteresisFactor", "hysteresis for state change; after state entry, state exited only when avg rate changes by this factor from threshold");
        setPropertyTooltip(policy, "tweakStepAmount", "fraction by which to tweak bias by each step, e.g. 0.1 means tweak bias current by 10% for each step");
        setPropertyTooltip(policy, "minCommandIntervalMs", "minimum time in ms between changing biases; avoids noise from changing biases too frequently");
        setPropertyTooltip(policy, "ignoreEventsAfterBiasChangeMs", "time interval in ms to ignore events after bias change (which causes noise events)");
        setPropertyTooltip(type, "goal", "<html>Overall goal of bias control"
                + "<ul> "
                + "<li> <b>BoundEventRate</b>: bound event rate between two limits (threshold)</li>"
                + "<li> <b>LimitEventRate</b>: prevents too high event rate (refractory)</li>"
                + "<li> <b>TargetSNR</b>: control bandwidth to target a specific SNR</li>"
                + "<li> <b>LimitNoise</b>: control bandwidth to a per-pixel noise limit</li>"
                + "</ul>");
        setPropertyTooltip(display, "showAnnotation", "enables showing controller state and actions on viewer");
        setPropertyTooltip(options, "writeLogEnabled", "writes a log file called DVSBiasController-xxx.txt to the startup folder (root of jaer) to allow analyzing controller dynamics");
        setPropertyTooltip(options, "correlationTimeS", "sets correlation time for noise filter");
    }

    public static DVSBiasController find(AEChip chip) {
        if (chip == null || chip.getFilterChain() == null) {
            return null;
        }
        return (DVSBiasController) chip.getFilterChain().findFilter(DVSBiasController.class);
    }

    /**
     * Find or append this filter on the chip chain, start disabled, pass events
     * through, persist preferred filters, and rebuild FilterFrame if it exists.
     */
    public static void ensurePresent(AEChip chip) {
        if (chip == null) {
            return;
        }
        FilterChain chain = chip.getFilterChain();
        if (chain == null || find(chip) != null) {
            return;
        }
        try {
            DVSBiasController f = new DVSBiasController(chip);
            chain.add(f);
            f.initFilter();
            f.setOutputRawInput(true);
            f.setFilterEnabled(false);
            ArrayList<String> names = new ArrayList<>();
            for (EventFilter2D x : chain) {
                names.add(x.getClass().getName());
            }
            chain.storePreferredFiltersForChip(names);
            FilterFrame ff = chip.getFilterFrame();
            if (ff != null) {
                if (SwingUtilities.isEventDispatchThread()) {
                    ff.rebuildContents();
                } else {
                    SwingUtilities.invokeLater(ff::rebuildContents);
                }
            }
            log.info("Appended DVSBiasController to filter chain for " + chip.getClass().getSimpleName());
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not add DVSBiasController: " + e, e);
        }
    }

    public Object getFilterState() {
        return null;
    }

    @Override
    synchronized public void resetFilter() {
        eventRateState = EventRateState.INITIAL;
        noiseEventRateState = EventRateState.INITIAL;
        snrState = SNRState.INITIAL;
        if (chip.getBiasgen() != null && !(chip.getBiasgen() instanceof DVSTweaks)) {
            log.warning("Wrong type of biasgen object; should be DVSTweaks but is " + chip.getBiasgen());
        }
    }

    public float getEventRateHighHz() {
        return eventRateHighHz;
    }

    synchronized public void setEventRateHighHz(float eventRateHighHz) {
        float old = this.eventRateHighHz;
        this.eventRateHighHz = eventRateHighHz;
        putFloat("eventRateHighHz", eventRateHighHz);
        getSupport().firePropertyChange("eventRateHighHz", old, eventRateHighHz);
    }

    public float getEventRateLowHz() {
        return eventRateLowHz;
    }

    synchronized public void setEventRateLowHz(float eventRateLowHz) {
        float old = this.eventRateLowHz;
        this.eventRateLowHz = eventRateLowHz;
        putFloat("eventRateLowHz", eventRateLowHz);
        getSupport().firePropertyChange("eventRateLowHz", old, eventRateLowHz);
    }

    @Override
    public boolean accepts(PacketType type) {
        return type == PacketType.POLARITY;
    }

    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        return processPolarity(in);
    }

    @Override
    synchronized public EventPacket<? extends BasicEvent> processPolarity(EventPacket<? extends BasicEvent> in) {
        if (in == null) {
            return in;
        }
        long dtMs = System.currentTimeMillis() - lastBiasChangeTimeMs;
        if (dtMs < ignoreEventsAfterBiasChangeMs) {
            inputRateEstimator.resetFilter();
            denoisedRateEstimator.resetFilter();
            noiseFilter.resetFilter();
            return in;
        }
        EventPacket<? extends BasicEvent> out = getEnclosedFilterChain().filterPacket(in);
        setEventRateStates();
        setSNRState();
        if (dtMs >= minCommandIntervalMs) {
            setBiases();
        }
        fireControlState();
        if (writeLogEnabled && dvsTweaks != null) {
            try {
                tobiLogger.log(String.format("%d,%f,%f,%f,%f,%f,%f,%f,%f,%d,%d,%d,%d,%f,%f,%f",
                        in.getLastTimestamp(),
                        inputEventRate,
                        signalEventRate,
                        noiseEventRate,
                        snr,
                        eventRateLowHz,
                        eventRateHighHz,
                        getTargetSNR(),
                        noiseLimitHzPerPixel,
                        goal.ordinal(),
                        eventRateState.ordinal(),
                        noiseEventRateState.ordinal(),
                        snrState.ordinal(),
                        dvsTweaks.getThresholdTweak(),
                        dvsTweaks.getBandwidthTweak(),
                        dvsTweaks.getMaxFiringRateTweak()
                ));
            } catch (Exception e) {
                log.log(Level.WARNING, e.toString(), e);
            }
        }
        if (outputRawInput) {
            for (BasicEvent e : in) {
                e.setFilteredOut(false);
            }
            return in;
        }
        return out;
    }

    private void setEventRateStates() {
        inputEventRate = inputRateEstimator.getFilteredEventRate();
        signalEventRate = denoisedRateEstimator.getFilteredEventRate();
        float newNoiseRate = inputEventRate - signalEventRate;
        if (newNoiseRate >= 0) {
            noiseEventRate = newNoiseRate;
        }
        lastEventRateState = eventRateState;
        float r = inputEventRate;
        switch (eventRateState) {
            case LOW_RATE:
                if (r > (eventRateLowHz * eventRateBoundsHysteresisFactor)) {
                    eventRateState = EventRateState.MEDIUM_RATE;
                }
                break;
            case MEDIUM_RATE:
                if (r < (eventRateLowHz / eventRateBoundsHysteresisFactor)) {
                    eventRateState = EventRateState.LOW_RATE;
                } else if (r > (eventRateHighHz * eventRateBoundsHysteresisFactor)) {
                    eventRateState = EventRateState.HIGH_RATE;
                }
                break;
            case HIGH_RATE:
                if (r < (eventRateHighHz / eventRateBoundsHysteresisFactor)) {
                    eventRateState = EventRateState.MEDIUM_RATE;
                }
                break;
            default:
                eventRateState = EventRateState.MEDIUM_RATE;
        }
        float nr = noiseEventRate / Math.max(1, chip.getNumPixels());
        switch (noiseEventRateState) {
            case LOW_RATE:
                if (nr > noiseLimitHzPerPixel) {
                    noiseEventRateState = EventRateState.MEDIUM_RATE;
                }
                break;
            case MEDIUM_RATE:
                if (nr < (noiseLimitHzPerPixel / eventRateBoundsHysteresisFactor)) {
                    noiseEventRateState = EventRateState.LOW_RATE;
                } else if (nr > (noiseLimitHzPerPixel * eventRateBoundsHysteresisFactor)) {
                    noiseEventRateState = EventRateState.HIGH_RATE;
                }
                break;
            case HIGH_RATE:
                if (nr < noiseLimitHzPerPixel) {
                    noiseEventRateState = EventRateState.MEDIUM_RATE;
                }
                break;
            default:
                noiseEventRateState = EventRateState.MEDIUM_RATE;
        }
    }

    private void setSNRState() {
        snr = (signalEventRate - noiseEventRate) / Math.max(signalEventRate, noiseEventRate);
        if (Float.isNaN(snr)) {
            snrState = SNRState.INITIAL;
            return;
        }
        switch (snrState) {
            case ABOVE_TARGET:
                if (snr < targetSNR - snrHysteresis) {
                    snrState = SNRState.BELOW_TARGET;
                }
                break;
            case BELOW_TARGET:
                if (snr > targetSNR + snrHysteresis) {
                    snrState = SNRState.ABOVE_TARGET;
                }
                break;
            default:
                snrState = snr > targetSNR ? SNRState.ABOVE_TARGET : SNRState.BELOW_TARGET;
        }
    }

    void setBiases() {
        timeNowMs = System.currentTimeMillis();
        long dt = timeNowMs - lastBiasChangeTimeMs;
        if ((dt > 0) && (dt < getMinCommandIntervalMs())) {
            return;
        }
        if (chip.getHardwareInterface() == null) {
            return;
        }
        if (dvsTweaks == null) {
            return;
        }
        float thr = dvsTweaks.getThresholdTweak(), refr = dvsTweaks.getMaxFiringRateTweak(),
                bw = dvsTweaks.getBandwidthTweak();
        switch (goal) {
            case BoundEventRate:
                switch (eventRateState) {
                    case LOW_RATE:
                        if (thr > -1) {
                            dvsTweaks.setThresholdTweak(thr - getTweakStepAmount());
                            lastBiasChangeTimeMs = timeNowMs;
                        }
                        break;
                    case HIGH_RATE:
                        if (thr < 1) {
                            dvsTweaks.setThresholdTweak(thr + getTweakStepAmount());
                            lastBiasChangeTimeMs = timeNowMs;
                        }
                        break;
                    default:
                }
                break;
            case LimitEventRate:
                switch (eventRateState) {
                    case HIGH_RATE:
                        if (refr > -1) {
                            dvsTweaks.setMaxFiringRateTweak(refr - getTweakStepAmount());
                            lastBiasChangeTimeMs = timeNowMs;
                        }
                        break;
                    case MEDIUM_RATE:
                    case LOW_RATE:
                        if (dvsTweaks.getMaxFiringRateTweak() < 0) {
                            dvsTweaks.setMaxFiringRateTweak(refr + getTweakStepAmount());
                            lastBiasChangeTimeMs = timeNowMs;
                        }
                        break;
                    default:
                }
                break;
            case TargetSNR:
                if (snr < targetSNR) {
                    dvsTweaks.setBandwidthTweak(bw - getTweakStepAmount());
                    lastBiasChangeTimeMs = timeNowMs;
                } else {
                    dvsTweaks.setBandwidthTweak(bw + getTweakStepAmount());
                    lastBiasChangeTimeMs = timeNowMs;
                }
                break;
            case LimitNoise:
                switch (noiseEventRateState) {
                    case HIGH_RATE:
                        if (bw > -1) {
                            dvsTweaks.setBandwidthTweak(bw - getTweakStepAmount());
                            lastBiasChangeTimeMs = timeNowMs;
                        }
                        break;
                    case MEDIUM_RATE:
                        break;
                    case LOW_RATE:
                        if (bw < 1) {
                            dvsTweaks.setBandwidthTweak(bw + getTweakStepAmount());
                            lastBiasChangeTimeMs = timeNowMs;
                        }
                        break;
                    default:
                }
                break;
            case None:
        }
    }

    public void doRevertAllTweaks() {
        if (dvsTweaks == null) {
            return;
        }
        dvsTweaks.setThresholdTweak(0);
        dvsTweaks.setBandwidthTweak(0);
        dvsTweaks.setMaxFiringRateTweak(0);
        lastBiasChangeTimeMs = System.currentTimeMillis();
        fireControlState();
    }

    @Override
    public void initFilter() {
        setDvsTweaksInstance();
        setEventRateTauMs(getEventRateTauMs());
        if (chip.getAeViewer() != null) {
            chip.getAeViewer().getSupport().addPropertyChangeListener(AEViewer.EVENT_CHIP, this);
        }
    }

    private void setDvsTweaksInstance() {
        if (!(chip.getBiasgen() instanceof DVSTweaks)) {
            log.warning("Biasgen is not DVSTweaks, cannot control. Disabling filter");
            setFilterEnabled(false);
            dvsTweaks = null;
            return;
        }
        dvsTweaks = (DVSTweaks) chip.getBiasgen();
    }

    public DVSTweaks getDvsTweaks() {
        return dvsTweaks;
    }

    public float getEventRateBoundsHysteresisFactor() {
        return eventRateBoundsHysteresisFactor;
    }

    synchronized public void setEventRateBoundsHysteresisFactor(float h) {
        if (h < 1) {
            h = 1;
        } else if (h > 5) {
            h = 5;
        }
        float old = eventRateBoundsHysteresisFactor;
        eventRateBoundsHysteresisFactor = h;
        putFloat("eventRateBoundsHysteresisFactor", eventRateBoundsHysteresisFactor);
        getSupport().firePropertyChange("eventRateBoundsHysteresisFactor", old, eventRateBoundsHysteresisFactor);
    }

    public float getTweakStepAmount() {
        return tweakStepAmount;
    }

    public void setTweakStepAmount(float tweakStepAmount) {
        float old = this.tweakStepAmount;
        this.tweakStepAmount = tweakStepAmount;
        putFloat("tweakStepAmount", tweakStepAmount);
        getSupport().firePropertyChange("tweakStepAmount", old, tweakStepAmount);
    }

    EngineeringFormat fmt = new EngineeringFormat();

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (!showAnnotation) {
            return;
        }
        if (dvsTweaks == null) {
            return;
        }
        GL2 gl = drawable.getGL().getGL2();

        gl.glPushMatrix();
        int ypos = (int) (chip.getSizeY() * .2);
        int ystep = 8;
        gl.glColor3f(1, 1, 1);
        gl.glRasterPos3f(0, ypos, 0);
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_12, String.format("goal=%s, eventRateState=%s noiseState=%s snrState=%s", goal.toString(), eventRateState.toString(), noiseEventRateState.toString(), snrState.toString()));
        gl.glPopMatrix();
        final int xmin = 120, xmax = chip.getSizeX(), xwid = xmax - xmin, xmid = xmin + xwid / 2;

        {
            ypos += ystep;
            gl.glPushMatrix();
            gl.glColor3f(1, 1, 1);
            gl.glRasterPos3f(0, ypos, 0);
            float logRate = (float) Math.log10(inputEventRate);
            float logRateLow = (float) Math.log10(eventRateLowHz);
            float logRateHigh = (float) Math.log10(eventRateHighHz);
            float logRateMin = logRateLow - 1, logRateMax = logRateHigh + 1;
            float logRangeTotal = logRateMax - logRateMin;
            glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,
                    String.format("Inp/Sig/Noise Hz: %6s/%6s/%6sHz",
                            fmt.format(inputEventRate),
                            fmt.format(signalEventRate),
                            fmt.format(inputEventRate - signalEventRate)
                    ));
            gl.glLineWidth(2);
            float x;
            gl.glBegin(GL.GL_LINES);
            x = xmin + xwid * (logRateLow - logRateMin) / logRangeTotal;
            gl.glVertex2f(x, ypos - 3);
            gl.glVertex2f(x, ypos + 3);
            x = xmin + xwid * (logRateHigh - logRateMin) / logRangeTotal;
            gl.glVertex2f(x, ypos - 3);
            gl.glVertex2f(x, ypos + 3);
            gl.glEnd();
            x = xmin + xwid * (logRate - logRateMin) / logRangeTotal;
            switch (eventRateState) {
                case LOW_RATE:
                    gl.glColor3f(0, 0, 1);
                    break;
                case HIGH_RATE:
                    gl.glColor3f(1, 0, 0);
                    break;
                case MEDIUM_RATE:
                    gl.glColor3f(0, 1, 0);
                    break;
                default:
                    gl.glColor3f(.5f, .5f, 0);
            }
            gl.glLineWidth(4);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(xmin, ypos);
            gl.glVertex2f(x, ypos);
            gl.glEnd();
            gl.glPopMatrix();
        }
        {
            ypos += ystep;
            gl.glPushMatrix();
            gl.glColor3f(1, 1, 1);
            gl.glRasterPos3f(0, ypos, 0);
            glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,
                    String.format("Noise/Limit Hz/pix: %6sHz/%6sHz",
                            fmt.format(noiseEventRate / Math.max(1, chip.getNumPixels())),
                            fmt.format(noiseLimitHzPerPixel)
                    ));
            gl.glLineWidth(2);
            float rate = noiseEventRate / Math.max(1, chip.getNumPixels());
            float rateMax = noiseLimitHzPerPixel * 5;
            float x;
            gl.glBegin(GL.GL_LINES);
            x = xmin + xwid * noiseLimitHzPerPixel / rateMax;
            gl.glVertex2f(x, ypos - 3);
            gl.glVertex2f(x, ypos + 3);
            gl.glEnd();
            x = xmin + xwid * rate / rateMax;
            switch (noiseEventRateState) {
                case LOW_RATE:
                    gl.glColor3f(0, 0, 1);
                    break;
                case HIGH_RATE:
                    gl.glColor3f(1, 0, 0);
                    break;
                case MEDIUM_RATE:
                    gl.glColor3f(0, 1, 0);
                    break;
                default:
                    gl.glColor3f(.5f, .5f, 0);
            }
            gl.glLineWidth(4);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(xmin, ypos);
            gl.glVertex2f(x, ypos);
            gl.glEnd();
            gl.glPopMatrix();
        }
        {
            ypos += ystep;
            gl.glPushMatrix();
            gl.glColor3f(1, 1, 1);
            gl.glRasterPos3f(0, ypos, 0);
            float snrDB = 20 * (float) Math.log10(snr);
            glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("SNR=%10s(%sdB)", fmt.format(snr), fmt.format(snrDB)));
            gl.glLineWidth(2);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(xmid, ypos - 3);
            gl.glVertex2f(xmid, ypos + 3);
            gl.glEnd();
            if (snr < targetSNR) {
                gl.glColor3f(1, 0, 0);
            } else {
                gl.glColor3f(0, 1, 0);
            }
            float x = xmid + xwid / 2 * (snr - targetSNR);
            gl.glLineWidth(4);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(xmid, ypos);
            gl.glVertex2f(x, ypos);
            gl.glEnd();
            gl.glPopMatrix();

            ypos += ystep;
            drawTweak(gl, ypos, dvsTweaks.getThresholdTweak(), "Thr");
            ypos += ystep;
            drawTweak(gl, ypos, dvsTweaks.getBandwidthTweak(), "BW");
            ypos += ystep;
            drawTweak(gl, ypos, dvsTweaks.getMaxFiringRateTweak(), "Refr");
        }
    }

    private void drawTweak(GL2 gl, float ypos, float tweak, String name) {
        gl.glPushMatrix();
        gl.glColor3f(1, 1, 1);
        gl.glRasterPos3f(0, ypos, 0);
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("%s: %10s", name, fmt.format(tweak)));
        gl.glLineWidth(2);
        int xmid = chip.getSizeX() / 2;
        gl.glBegin(GL.GL_LINES);
        gl.glVertex2f(xmid, ypos - 3);
        gl.glVertex2f(xmid, ypos + 3);
        gl.glEnd();
        gl.glColor3f(0, 0, 1);
        float xpt = xmid + chip.getSizeX() * tweak / 2;
        gl.glRectf(xpt, ypos - 1, xmid, ypos + 1);
        gl.glPopMatrix();
    }

    synchronized public boolean isWriteLogEnabled() {
        return writeLogEnabled;
    }

    synchronized public void setWriteLogEnabled(boolean writeLogEnabled) {
        if (tobiLogger == null) {
            tobiLogger = new TobiLogger("DVSBiasController", "DVSBiasController");
            String gs = Stream.of(Goal.values()).
                    map(Goal::name).
                    collect(Collectors.joining(", "));
            String erss = Stream.of(EventRateState.values()).
                    map(EventRateState::name).
                    collect(Collectors.joining(", "));

            String snrss = Stream.of(SNRState.values()).
                    map(SNRState::name).
                    collect(Collectors.joining(", "));

            tobiLogger.setColumnHeaderLine("timestamp(us),inputEventRate(Hz),signalEventRate(Hz),noiseEventRate(Hz),snr,lowRate(Hz),highRate(Hz),targetSNR,noiseLimitHzPerPixel,goal,eventRateState,noiseEventRateState,snrState,thresholdTweak,bandwidthTweak,maxFiringRateTweak");
            tobiLogger.setFileCommentString("Recording of DVS bias control\n"
                    + "goal: " + gs + "\n"
                    + "eventRateState: " + erss + "\n"
                    + "snrState: " + snrss);
        }
        this.writeLogEnabled = writeLogEnabled;
        tobiLogger.setEnabled(writeLogEnabled);
        if (!writeLogEnabled) {
            tobiLogger.showFolderInDesktop();
        }
    }

    public int getMinCommandIntervalMs() {
        return minCommandIntervalMs;
    }

    public void setMinCommandIntervalMs(int minCommandIntervalMs) {
        int old = this.minCommandIntervalMs;
        this.minCommandIntervalMs = minCommandIntervalMs;
        putInt("minCommandIntervalMs", minCommandIntervalMs);
        getSupport().firePropertyChange("minCommandIntervalMs", old, minCommandIntervalMs);
    }

    public boolean isShowAnnotation() {
        return showAnnotation;
    }

    public void setShowAnnotation(boolean showAnnotation) {
        this.showAnnotation = showAnnotation;
        putBoolean("showAnnotation", showAnnotation);
    }

    public Goal getGoal() {
        return goal;
    }

    public void setGoal(Goal goal) {
        Goal old = this.goal;
        this.goal = goal;
        putString("goal", goal.toString());
        getSupport().firePropertyChange(EVENT_GOAL, old, goal);
    }

    public float getTargetSNR() {
        return targetSNR;
    }

    public void setTargetSNR(float targetSNR) {
        if (targetSNR > 1) {
            targetSNR = 1;
        } else if (targetSNR < -1) {
            targetSNR = -1;
        }
        float old = this.targetSNR;
        this.targetSNR = targetSNR;
        putFloat("targetSNR", targetSNR);
        getSupport().firePropertyChange("targetSNR", old, targetSNR);
    }

    public float getSnrHysteresis() {
        return snrHysteresis;
    }

    public void setSnrHysteresis(float snrHysteresis) {
        float old = this.snrHysteresis;
        this.snrHysteresis = snrHysteresis;
        putFloat("snrHysteresis", snrHysteresis);
        getSupport().firePropertyChange("snrHysteresis", old, snrHysteresis);
    }

    public boolean isOutputRawInput() {
        return outputRawInput;
    }

    public void setOutputRawInput(boolean outputRawInput) {
        boolean old = this.outputRawInput;
        this.outputRawInput = outputRawInput;
        putBoolean("outputRawInput", outputRawInput);
        getSupport().firePropertyChange("outputRawInput", old, outputRawInput);
    }

    public void setCorrelationTimeS(float dtS) {
        noiseFilter.setCorrelationTimeS(dtS);
    }

    public float getCorrelationTimeS() {
        return noiseFilter.getCorrelationTimeS();
    }

    public int getIgnoreEventsAfterBiasChangeMs() {
        return ignoreEventsAfterBiasChangeMs;
    }

    public void setIgnoreEventsAfterBiasChangeMs(int ignoreEventsAfterBiasChangeMs) {
        int old = this.ignoreEventsAfterBiasChangeMs;
        this.ignoreEventsAfterBiasChangeMs = ignoreEventsAfterBiasChangeMs;
        putInt("ignoreEventsAfterBiasChangeMs", ignoreEventsAfterBiasChangeMs);
        getSupport().firePropertyChange("ignoreEventsAfterBiasChangeMs", old, ignoreEventsAfterBiasChangeMs);
    }

    public float getNoiseLimitHzPerPixel() {
        return noiseLimitHzPerPixel;
    }

    public void setNoiseLimitHzPerPixel(float noiseLimitHzPerPixel) {
        float old = this.noiseLimitHzPerPixel;
        this.noiseLimitHzPerPixel = noiseLimitHzPerPixel;
        putFloat("noiseLimitHzPerPixel", noiseLimitHzPerPixel);
        getSupport().firePropertyChange("noiseLimitHzPerPixel", old, noiseLimitHzPerPixel);
    }

    public float getEventRateTauMs() {
        return inputRateEstimator.getEventRateTauMs();
    }

    public void setEventRateTauMs(float eventRateTauMs) {
        float old = inputRateEstimator.getEventRateTauMs();
        inputRateEstimator.setEventRateTauMs(eventRateTauMs);
        denoisedRateEstimator.setEventRateTauMs(eventRateTauMs);
        getSupport().firePropertyChange("eventRateTauMs", old, eventRateTauMs);
    }

    public float getInputEventRate() {
        return inputEventRate;
    }

    public float getSignalEventRate() {
        return signalEventRate;
    }

    public float getNoiseEventRate() {
        return noiseEventRate;
    }

    public float getSnr() {
        return snr;
    }

    public String getEventRateStateText() {
        return eventRateState.toString();
    }

    public String getNoiseEventRateStateText() {
        return noiseEventRateState.toString();
    }

    public String getSnrStateText() {
        return snrState.toString();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        if (AEViewer.EVENT_CHIP.equals(evt.getPropertyName())) {
            setDvsTweaksInstance();
        }
    }

    public void setBandwidthTweak(float val) {
        if (dvsTweaks == null) {
            return;
        }
        float old = dvsTweaks.getBandwidthTweak();
        dvsTweaks.setBandwidthTweak(val);
        getSupport().firePropertyChange(EVENT_BANDWIDTH_TWEAK, old, dvsTweaks.getBandwidthTweak());
    }

    public float getBandwidthTweak() {
        if (dvsTweaks == null) {
            return Float.NaN;
        }
        return dvsTweaks.getBandwidthTweak();
    }

    public void setMaxFiringRateTweak(float val) {
        if (dvsTweaks == null) {
            return;
        }
        float old = dvsTweaks.getMaxFiringRateTweak();
        dvsTweaks.setMaxFiringRateTweak(val);
        getSupport().firePropertyChange(EVENT_MAX_FIRING_RATE_TWEAK, old, dvsTweaks.getMaxFiringRateTweak());
    }

    public float getMaxFiringRateTweak() {
        if (dvsTweaks == null) {
            return Float.NaN;
        }
        return dvsTweaks.getMaxFiringRateTweak();
    }

    public void setThresholdTweak(float val) {
        if (dvsTweaks == null) {
            return;
        }
        float old = dvsTweaks.getThresholdTweak();
        dvsTweaks.setThresholdTweak(val);
        getSupport().firePropertyChange(EVENT_THRESHOLD_TWEAK, old, dvsTweaks.getThresholdTweak());
    }

    public float getThresholdTweak() {
        if (dvsTweaks == null) {
            return Float.NaN;
        }
        return dvsTweaks.getThresholdTweak();
    }

    private void fireControlState() {
        long now = System.currentTimeMillis();
        if (now - lastUiFireMs < 100) {
            return;
        }
        lastUiFireMs = now;
        getSupport().firePropertyChange(EVENT_INPUT_RATE, null, inputEventRate);
        getSupport().firePropertyChange(EVENT_SIGNAL_RATE, null, signalEventRate);
        getSupport().firePropertyChange(EVENT_NOISE_RATE, null, noiseEventRate);
        getSupport().firePropertyChange(EVENT_SNR, null, snr);
        getSupport().firePropertyChange(EVENT_RATE_STATE, null, eventRateState);
        getSupport().firePropertyChange(EVENT_NOISE_STATE, null, noiseEventRateState);
        getSupport().firePropertyChange(EVENT_SNR_STATE, null, snrState);
        if (dvsTweaks != null) {
            getSupport().firePropertyChange(EVENT_THRESHOLD_TWEAK, null, dvsTweaks.getThresholdTweak());
            getSupport().firePropertyChange(EVENT_BANDWIDTH_TWEAK, null, dvsTweaks.getBandwidthTweak());
            getSupport().firePropertyChange(EVENT_MAX_FIRING_RATE_TWEAK, null, dvsTweaks.getMaxFiringRateTweak());
        }
        getSupport().firePropertyChange(EVENT_CONTROL_STATE, null, this);
    }

}
