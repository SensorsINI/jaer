/*
 *
 * Created on January 9, 2006, 10:41 AM
 * Cloned from DVSBiasController Feb 2011 by Tobi
 *
 */
package ch.unizh.ini.jaer.chip.retina;

import ch.unizh.ini.jaer.projects.npp.RoShamBoCNN;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.EventRateEstimator;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.EngineeringFormat;

import com.jogamp.opengl.util.gl2.GLUT;
import gnu.io.NRSerialPort;
import java.beans.PropertyChangeEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.sf.jaer.DevelopmentStatus;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.filter.SpatioTemporalCorrelationFilter;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.util.TobiLogger;

/**
 * Controls the rate of events from the retina by controlling retina biases. The
 * event threshold is increased if rate exceeds eventRateHighHz until rate drops
 * below eventRateHighHz. The threshold is decreased if rate is lower than
 * eventRateLowHz. Hysterisis limits crossing noise. A lowpass filter smooths
 * the rate measurements.
 *
 * @author tobi
 */
@Description("Adaptively controls biases on DVS sensors (that implement DVSTweaks on their bias generator) to control event rate")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class DVSBiasController extends EventFilter2D implements FrameAnnotater {

    public enum Goal {
        None, BoundEventRate, TargetSNR, LimitNoise, LimitEventRate
    }
    private Goal goal = Goal.None;

    private float eventRateHighHz = getFloat("eventRateHighHz", 1e6f);
    private float eventRateLowHz = getFloat("rateLowKeps", 100e3f);
    private float eventRateBoundsHysteresisFactor = getFloat("eventRateBoundsHysteresisFactor", 1.3f);
    protected float snrHysteresis = getFloat("snrHysteresis", .2f);
    private int minCommandIntervalMs = getInt("minCommandIntervalMs", 300);
    protected int ignoreEventsAfterBiasChangeMs = getInt("ignoreEventsAfterBiasChangeMs", 100);
    private long lastBiasChangeTimeMs = 0; // limits use of status messages that control biases
    private float tweakStepAmount = getFloat("tweakStepAmount", .01f);
//    private boolean changeDvsEventThresholds = getBoolean("changeDvsEventThresholds", true);
//    private boolean changeDvsRefractoryPeriod = getBoolean("changeDvsRefractoryPeriod", true);
//    private boolean changeDvsPixelBandwidth = getBoolean("changeDvsPixelBandwidth", true);
    private boolean showAnnotation = getBoolean("showAnnotation", true);
    protected boolean outputRawInput = getBoolean("outputRawInput", false);
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

    NRSerialPort serialPort = null;
    private int serialBaudRate = getInt("serialBaudRate", 115200); // note firmware is programmed for max 2Mbaud
    private String serialPortName = getString("serialPortName", "COM3");
    private DataOutputStream serialPortOutputStream = null;
    private DataInputStream serialPortInputStream = null;
    private DVSTweaks dvsTweaks = null;

    protected int motorSpeed = 0;

    enum EventRateState {

        INITIAL, LOW_RATE, MEDIUM_RATE, HIGH_RATE;
        private long timeChanged = 0;

        long getTimeChanged() {
            return timeChanged;
        }

        void setTimeChanged(long t) {
            timeChanged = t;
        }

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

    /**
     * Creates a new instance of DVS128BiasController
     */
    public DVSBiasController(AEChip chip) {
        super(chip);

        try {
            goal = Goal.valueOf(getString("goal", Goal.BoundEventRate.toString()));
        } catch (Exception e) {
            goal = Goal.None; // might happen after rename
        }
        inputRateEstimator = new EventRateEstimator(chip);
        noiseFilter = new SpatioTemporalCorrelationFilter(chip);
        noiseFilter.setLetFirstEventThrough(false); // makes sure evenr rate closely reflects input
        denoisedRateEstimator = new EventRateEstimator(chip);
        FilterChain chain = new FilterChain(chip);
        chain.add(inputRateEstimator);
        chain.add(noiseFilter);
        chain.add(denoisedRateEstimator);
        setEnclosedFilterChain(chain);
        final String tw = "0. Tweaks", type = "1. Type of control", rates = "2. Control Event rates, SNR", policy = "3. Bang-bang policy", display = "4. Display", options = "5. Options", expt = "6. Experiments";

        setPropertyTooltip(tw, "maxFiringRateTweak", "refractory tweak");
        setPropertyTooltip(tw, "thresholdTweak", "threshold tweak");
        setPropertyTooltip(tw, "bandwidthTweak", "bandwidth tweak");
        setPropertyTooltip(options, "revertAllTweaks", "revert all bias tweaks to zero");
        setPropertyTooltip(options, "outputRawInput", "output un-denoised input. If unselected, output has been denoised.");
        setPropertyTooltip(rates, "eventRateLowHz", "event rate in keps for LOW state, where event threshold or refractory period are reduced");
        setPropertyTooltip(rates, "eventRateHighHz", "event rate in keps for HIGH state, where event threshold or refractory period are increased");
        setPropertyTooltip(rates, "targetSNR", "For goal TargetSNR: minimum SNR to target by bandwidth control");
        setPropertyTooltip(rates, "noiseLimitHzPerPixel", "For goal LimitNoise, target noise rate per pixel");
        setPropertyTooltip(rates, "eventRateTauMs", "Time windows over which to measure event rates");
        setPropertyTooltip(policy, "snrHysteresis", "hysteresis for SNR for bandwidth change; after state entry, state exited only when SNR changes by this much in opposite direction");
        setPropertyTooltip(policy, "eventRateBoundsHysteresisFactor", "hysteresis for state change; after state entry, state exited only when avg rate changes by this factor from threshold");
        setPropertyTooltip(policy, "tweakStepAmount", "fraction by which to tweak bias by each step, e.g. 0.1 means tweak bias current by 10% for each step");
        setPropertyTooltip(policy, "minCommandIntervalMs", "minimum time in ms between changing biases; avoids noise from changing biases too frequently");
        setPropertyTooltip(policy, "ignoreEventsAfterBiasChangeMs", "time interval in ms to ignore events after bias change (which causes noise events)");
        setPropertyTooltip(type, "goal", "<html>Overall goal of bias control"
                + "<ul> "
                + "<li> <b>LimitEventRate</b>: prevents too high event rate"
                + "<li> <b>TargetSNR</b>: attempt to control biases to target a specific SNR"
                + "<li> <b>BoundEventRate</b>: bound event rate between two limits"
                + "</ul>");
//        setPropertyTooltip(type, "changeDvsRefractoryPeriod", "enables changing DVS refractory period (time after each event that pixel is reset)");
//        setPropertyTooltip(type, "changeDvsEventThresholds", "enables changing DVS refractory period (time after each event that pixel is reset)");
//        setPropertyTooltip(type, "changeDvsPixelBandwidth", "enables changing DVS photoreceptor/source follower analog bandwidth");
        setPropertyTooltip(display, "showAnnotation", "enables showing controller state and actions on viewer");
        setPropertyTooltip(options, "writeLogEnabled", "writes a log file called DVSBiasController-xxx.txt to the startup folder (root of jaer) to allow analyzing controller dynamics");
        setPropertyTooltip(options, "correlationTimeS", "sets correlation time for noise filter");
        setPropertyTooltip(expt, "serialPortName", "Name of serial port to send Arduino Nano commands to");
        setPropertyTooltip(expt, "serialBaudRate", "Baud rate (default 115200), upper limit 12000000");
        setPropertyTooltip(expt, "motorSpeed", "Sent to serial port for controlling stimulus speed, logged to CSV file");
        setPropertyTooltip(expt, "motorSpeed", "Sent to serial port for controlling stimulus speed, logged to CSV file");
    }

    public Object getFilterState() {
        return null;
    }

    @Override
    synchronized public void resetFilter() {
        if (chip.getHardwareInterface() == null) {
            return;  // avoid sending hardware commands unless the hardware is there and we are active
        }
        if (chip.getBiasgen() == null) {
            setFilterEnabled(false);
            log.warning("null biasgen object to operate on, disabled filter");
            return;
        }
        if (!(chip.getBiasgen() instanceof DVSTweaks)) {
            setFilterEnabled(false);
            log.warning("Wrong type of biasgen object; should be DvsTweaks but is " + chip.getBiasgen() + "; disabled filter");
            return;
        }
        if (dvsTweaks == null) {
            return;
        }
        eventRateState = EventRateState.INITIAL;
        noiseEventRateState = EventRateState.INITIAL;
        snrState = SNRState.INITIAL;
    }

    public float getEventRateHighHz() {
        return eventRateHighHz;
    }

    synchronized public void setEventRateHighHz(float eventRateHighHz) {
        this.eventRateHighHz = eventRateHighHz;
        putFloat("eventRateHighHz", eventRateHighHz);
    }

    public float getEventRateLowHz() {
        return eventRateLowHz;
    }

    synchronized public void setEventRateLowHz(float eventRateLowHz) {
        this.eventRateLowHz = eventRateLowHz;
        putFloat("eventRateLowHz", eventRateLowHz);
    }

    @Override
    synchronized public EventPacket filterPacket(EventPacket in) {
        // TODO reenable check for LIVE mode here
        //        if (chip.getAeViewer().getPlayMode() != AEViewer.PlayMode.LIVE) {
        //            return in;  // don't servo on recorded data!
        //        }

        long dtMs = System.currentTimeMillis() - lastBiasChangeTimeMs;
        if (dtMs < ignoreEventsAfterBiasChangeMs) {
            inputRateEstimator.resetFilter();
            denoisedRateEstimator.resetFilter();
            noiseFilter.resetFilter();
            return in;
        }
        EventPacket out = getEnclosedFilterChain().filterPacket(in); // filters out noise

//        System.out.println(String.format("in: %.1f, filtered: %.1f, noise: %.1f, snr: %.3f",
//                1e-3f * inputEventRate, 1e-3f * eventRate, 1e-3f * noiseRate, snr));
        setEventRateStates();
        setSNRState();
        if (dtMs >= minCommandIntervalMs) {
            setBiases();
        }
        if (writeLogEnabled && dvsTweaks != null) {
            try {
//           tobiLogger.setColumnHeaderLine("timestamp(us),eventRate(Hz),snr,lowRate(Hz),highRate(Hz),targetSNR,state,thresholdTweak,bandwidthTweak,maxFiringRateTweak");

                tobiLogger.log(String.format("%d,%f,%f,%f,%f,%f,%f,%f,%f,%d,%d,%d,%d,%f,%f,%f,%d",
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
                        dvsTweaks.getMaxFiringRateTweak(),
                        getMotorSpeed()
                ));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return outputRawInput ? in : out;
    }

    private void setEventRateStates() {
        inputEventRate = inputRateEstimator.getFilteredEventRate();
        signalEventRate = denoisedRateEstimator.getFilteredEventRate();
        float newNoiseRate = inputEventRate - signalEventRate;
        if (newNoiseRate >= 0) {
            noiseEventRate = newNoiseRate;
        } else {
            noiseEventRate = noiseEventRate;
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
        float nr = noiseEventRate / chip.getNumPixels();
        switch (noiseEventRateState) {
            case LOW_RATE:
                if (nr > (noiseLimitHzPerPixel)) {;
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
                if (nr < (noiseLimitHzPerPixel)) {
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
        } else if (snr > targetSNR) {
            snrState = SNRState.ABOVE_TARGET;
        } else {
            snrState = SNRState.BELOW_TARGET;
        }
    }

    void setBiases() {
        timeNowMs = System.currentTimeMillis();
        long dt = timeNowMs - lastBiasChangeTimeMs;
        if ((dt > 0) && (dt < getMinCommandIntervalMs())) {
            return; // don't saturate setup packet bandwidth and stall on blocking USB writes
        }

        if (dvsTweaks == null) {
            log.warning("null biasgen, not doing anything");
            return;
        }
        float thr = dvsTweaks.getThresholdTweak(), refr = dvsTweaks.getMaxFiringRateTweak(), bw = dvsTweaks.getBandwidthTweak();
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
                }
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
    }

    @Override
    public void initFilter() {
        setDvsTweaksInstance();
        setEventRateTauMs(getEventRateTauMs()); // set both estimators
        if (chip.getAeViewer() != null) {
            chip.getAeViewer().getSupport().addPropertyChangeListener(AEViewer.EVENT_CHIP, this);
        }
    }

    private void setDvsTweaksInstance() {
        if (!(chip.getBiasgen() instanceof DVSTweaks)) {
            log.warning("Biasgen is not DVSTweaks, cannot control. Disabling filter");
            setFilterEnabled(false);
        }
        dvsTweaks = (DVSTweaks) chip.getBiasgen();
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
        eventRateBoundsHysteresisFactor = h;
        putFloat("eventRateBoundsHysteresisFactor", eventRateBoundsHysteresisFactor);
    }

    /**
     * @return the tweakStepAmount
     */
    public float getTweakStepAmount() {
        return tweakStepAmount;
    }

    /**
     * @param tweakStepAmount the tweakStepAmount to set
     */
    public void setTweakStepAmount(float tweakStepAmount) {
        this.tweakStepAmount = tweakStepAmount;
        putFloat("tweakStepAmount", tweakStepAmount);
    }

    EngineeringFormat fmt = new EngineeringFormat();

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (!showAnnotation) {
            return;
        }
        if (dvsTweaks == null) {
            log.warning("null biasgen, not doing anything");
            return;
        }
        GL2 gl = drawable.getGL().getGL2();

        gl.glPushMatrix();
        int ypos = (int) (chip.getSizeY() * .2); // start at bottom, move up line by line
        int ystep = 8;
        gl.glColor3f(1, 1, 1); // must set color before raster position (raster position is like glVertex)
        gl.glRasterPos3f(0, ypos, 0);
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_12, String.format("goal=%s, eventRateState=%s noiseState=%s snrState=%s", goal.toString(), eventRateState.toString(), noiseEventRateState.toString(), snrState.toString()));
        gl.glPopMatrix();
        final int xmin = 120, xmax = chip.getSizeX(), xwid = xmax - xmin, xmid = xmin + xwid / 2;

        {
            // draw noise rate with limit
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
        {           // draw event rate with bounds
            ypos += ystep;
            gl.glPushMatrix();
            gl.glColor3f(1, 1, 1);
            gl.glRasterPos3f(0, ypos, 0);
            glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18,
                    String.format("Noise/Limit Hz/pix: %6sHz/%6sHz",
                            fmt.format(noiseEventRate / chip.getNumPixels()),
                            fmt.format(noiseLimitHzPerPixel)
                    ));
            gl.glLineWidth(2);
            float rate = (float) (noiseEventRate / chip.getNumPixels());
            float rateLimit = (float) (noiseLimitHzPerPixel);
            float rateMax = noiseLimitHzPerPixel * 5;
            float x;
            gl.glBegin(GL.GL_LINES);
            x = xmin + xwid * noiseLimitHzPerPixel / rateMax;
            gl.glVertex2f(x, ypos - 3);
            gl.glVertex2f(x, ypos + 3);
            gl.glEnd();
            x = xmin + xwid * (rate) / rateMax;
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
            // draw SNR bar
            ypos += ystep;
            gl.glPushMatrix();
            gl.glColor3f(1, 1, 1);
            gl.glRasterPos3f(0, ypos, 0);
            float snrDB = 20 * (float) Math.log10(snr);
            glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("SNR=%10s(%sdB)", fmt.format(snr), fmt.format(snrDB)));
            // draw tick at targetSNR
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

            // draw tweaks
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

            tobiLogger.setColumnHeaderLine("timestamp(us),inputEventRate(Hz),signalEventRate(Hz),noiseEventRate(Hz),snr,lowRate(Hz),highRate(Hz),targetSNR,noiseLimitHzPerPixel,goal,eventRateState,noiseEventRateState,snrState,thresholdTweak,bandwidthTweak,maxFiringRateTweak,motorSpeed");
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

    /**
     * @return the minCommandIntervalMs
     */
    public int getMinCommandIntervalMs() {
        return minCommandIntervalMs;
    }

    /**
     * @param minCommandIntervalMs the minCommandIntervalMs to set
     */
    public void setMinCommandIntervalMs(int minCommandIntervalMs) {
        this.minCommandIntervalMs = minCommandIntervalMs;
        putInt("minCommandIntervalMs", minCommandIntervalMs);
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
        putBoolean("showAnnotation", showAnnotation);
    }

    /**
     * @return the goal
     */
    public Goal getGoal() {
        return goal;
    }

    /**
     * @param goal the goal to set
     */
    public void setGoal(Goal goal) {
        this.goal = goal;
        putString("goal", goal.toString());
    }

    /**
     * @return the targetSNR
     */
    public float getTargetSNR() {
        return targetSNR;
    }

    /**
     * @param targetSNR the targetSNR to set
     */
    public void setTargetSNR(float targetSNR) {
        if (targetSNR > 1) {
            targetSNR = 1;
        } else if (targetSNR < -1) {
            targetSNR = -1;
        }
        this.targetSNR = targetSNR;
        putFloat("targetSNR", targetSNR);
    }

    /**
     * @return the snrHysteresis
     */
    public float getSnrHysteresis() {
        return snrHysteresis;
    }

    /**
     * @param snrHysteresis the snrHysteresis to set
     */
    public void setSnrHysteresis(float snrHysteresis) {
        this.snrHysteresis = snrHysteresis;
        putFloat("snrHysteresis", snrHysteresis);
    }

    // since denoising marks events filtered out, cannot just return denoised packet without touching it again
//    /** 
//     * @return the outputRawInput
//     */
//    public boolean isOutputRawInput() {
//        return outputRawInput;
//    }
//
//    /**
//     * @param outputRawInput the outputRawInput to set
//     */
//    public void setOutputRawInput(boolean outputRawInput) {
//        this.outputRawInput = outputRawInput;
//        putBoolean("outputRawInput", outputRawInput);
//    }
    public void setCorrelationTimeS(float dtS) {
        noiseFilter.setCorrelationTimeS(dtS);
    }

    public float getCorrelationTimeS() {
        return noiseFilter.getCorrelationTimeS();
    }

    /**
     * @return the ignoreEventsAfterBiasChangeMs
     */
    public int getIgnoreEventsAfterBiasChangeMs() {
        return ignoreEventsAfterBiasChangeMs;
    }

    /**
     * @param ignoreEventsAfterBiasChangeMs the ignoreEventsAfterBiasChangeMs to
     * set
     */
    public void setIgnoreEventsAfterBiasChangeMs(int ignoreEventsAfterBiasChangeMs) {
        this.ignoreEventsAfterBiasChangeMs = ignoreEventsAfterBiasChangeMs;
        putInt("ignoreEventsAfterBiasChangeMs", ignoreEventsAfterBiasChangeMs);
    }

    private void sendByte(int cmd) {
        if (serialPortOutputStream != null) {
            try {
                serialPortOutputStream.write((byte) cmd);
            } catch (IOException ex) {
                log.warning(ex.toString());
            }
        }
    }

    private void openSerial() throws IOException {
        if (serialPort != null) {
            closeSerial();
        }
        StringBuilder sb = new StringBuilder("List of all available serial ports: ");
        final Set<String> availableSerialPorts = NRSerialPort.getAvailableSerialPorts();
        if (availableSerialPorts.isEmpty()) {
            sb.append("\nNo ports found, sorry.  If you are on linux, serial port support may suffer");
        } else {
            for (String s : availableSerialPorts) {
                sb.append(s).append(" ");
            }
        }
        log.info(sb.toString());
        if (!availableSerialPorts.contains(serialPortName)) {
            final String warningString = serialPortName + " is not in avaiable " + sb.toString();
            log.warning(warningString);
            showWarningDialogInSwingThread(warningString, "Serial port not available");
            return;
        }

        serialPort = new NRSerialPort(serialPortName, serialBaudRate);
        if (serialPort == null) {
            final String warningString = "null serial port returned when trying to open " + serialPortName + "; available " + sb.toString();
            log.warning(warningString);
            showWarningDialogInSwingThread(warningString, "Serial port not available");
            return;
        }
        serialPort.connect();
        serialPortOutputStream = new DataOutputStream(serialPort.getOutputStream());
        serialPortInputStream = new DataInputStream(serialPort.getInputStream());
        log.info("opened serial port " + serialPortName + " with baud rate=" + serialBaudRate);
        // drain serial port chars from Arduino startup
        try {
            sendByte('p'); // stimulute a pong response, should result in at least a p return
            log.info("Draining startup serial port output from Arduino:");
            long start = System.currentTimeMillis();
            while (!Thread.interrupted() && System.currentTimeMillis() - start < 900L) {
                while (serialPortInputStream.available() > 0) {
                    int c = serialPortInputStream.read();
                    System.out.print((char) c);
                }
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                }
            }
        } catch (IOException ex) {
            log.warning(ex.toString());
        }

    }

    private void closeSerial() {
        if (serialPortOutputStream != null) {
            try {
                serialPortOutputStream.close();
            } catch (IOException ex) {
                Logger.getLogger(RoShamBoCNN.class.getName()).log(Level.SEVERE, null, ex);
            }
            serialPortOutputStream = null;
        }
        if ((serialPort != null) && serialPort.isConnected()) {
            serialPort.disconnect();
            serialPort = null;
        }
//        log.info("closed serial port");
    }

    /**
     * @return the serialBaudRate
     */
    public int getSerialBaudRate() {
        return serialBaudRate;
    }

    /**
     * @param serialBaudRate the serialBaudRate to set
     */
    public void setSerialBaudRate(int serialBaudRate) {
        try {
            this.serialBaudRate = serialBaudRate;
            putInt("serialBaudRate", serialBaudRate);
            openSerial();
        } catch (IOException ex) {
            Logger.getLogger(RoShamBoCNN.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * @return the serialPortName
     */
    public String getSerialPortName() {
        return serialPortName;
    }

    /**
     * @param serialPortName the serialPortName to set
     */
    public void setSerialPortName(String serialPortName) {
        try {
            this.serialPortName = serialPortName;
            putString("serialPortName", serialPortName);
            openSerial();
        } catch (IOException ex) {
            Logger.getLogger(RoShamBoCNN.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * @return the motorSpeed
     */
    public int getMotorSpeed() {
        return motorSpeed;
    }

    /**
     * @param motorSpeed the motorSpeed to set
     */
    public void setMotorSpeed(int motorSpeed) {
        if (serialPort == null) {
            log.warning("serial port not open");
            return;
        }
        this.motorSpeed = motorSpeed;
        sendByte('0' + motorSpeed);
    }

    /**
     * @return the noiseLimitHzPerPixel
     */
    public float getNoiseLimitHzPerPixel() {
        return noiseLimitHzPerPixel;
    }

    /**
     * @param noiseLimitHzPerPixel the noiseLimitHzPerPixel to set
     */
    public void setNoiseLimitHzPerPixel(float noiseLimitHzPerPixel) {
        this.noiseLimitHzPerPixel = noiseLimitHzPerPixel;
        putFloat("noiseLimitHzPerPixel", noiseLimitHzPerPixel);
    }

    public float getEventRateTauMs() {
        return inputRateEstimator.getEventRateTauMs();
    }

    public void setEventRateTauMs(float eventRateTauMs) {
        inputRateEstimator.setEventRateTauMs(eventRateTauMs);
        denoisedRateEstimator.setEventRateTauMs(eventRateTauMs);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        if (evt.getPropertyName() == AEViewer.EVENT_CHIP) {
            setDvsTweaksInstance();
        }
    }

    public void setBandwidthTweak(float val) {
        if (dvsTweaks == null) {
            return;
        }
        dvsTweaks.setBandwidthTweak(val);
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
        dvsTweaks.setMaxFiringRateTweak(val);
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
        dvsTweaks.setThresholdTweak(val);
    }

    public float getThresholdTweak() {
        if (dvsTweaks == null) {
            return Float.NaN;
        }
        return dvsTweaks.getThresholdTweak();
    }

}
