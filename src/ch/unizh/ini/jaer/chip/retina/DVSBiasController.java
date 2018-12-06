/*
 *
 * Created on January 9, 2006, 10:41 AM
 * Cloned from DVSBiasController Feb 2011 by Tobi
 *
 */
package ch.unizh.ini.jaer.chip.retina;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.EventRateEstimator;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.EngineeringFormat;

import com.jogamp.opengl.util.gl2.GLUT;
import net.sf.jaer.DevelopmentStatus;

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
        LimitEventRate, MaximuizeSNR, FillBusBandwidth
    }
    private Goal goal = Goal.valueOf(getString("goal", Goal.LimitEventRate.toString()));

    private float maxEventRateHz = getFloat("maxEventeRateHz", 4e6f);
    private float eventRateHighHz = getFloat("eventRateHighHz", 1e6f);
    private float eventRateLowHz = getFloat("rateLowKeps", 100e3f);
    private float hysteresisFactor = getFloat("hysteresisFactor", 1.3f);
    private int minCommandIntervalMs = getInt("minCommandIntervalMs", 300);
    private long lastCommandTime = 0; // limits use of status messages that control biases
    private float tweakStepAmount = getFloat("tweakStepAmount", .01f);
    private boolean changeDvsEventThresholds = getBoolean("changeDvsEventThresholds", true);
    private boolean changeDvsRefractoryPeriod = getBoolean("changeDvsRefractoryPeriod", true);
    private boolean changeDvsPixelBandwidth = getBoolean("changeDvsPixelBandwidth", true);
    private boolean showAnnotation = getBoolean("showAnnotation", true);
    private EventRateEstimator rateEstimator;

    enum State {

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
                    return "Event rate too high, increasing threshold";
                case MEDIUM_RATE:
                    return "Event rate within bounds";
                case LOW_RATE:
                    return "Event rate too low: decreasing threshold";
                default:
                    return "Initial state";
            }
        }
    };
    State eventRateState = State.INITIAL, lastEventRateState = State.INITIAL;
    Writer logWriter;
    private boolean writeLogEnabled = false;
    long timeNowMs = 0;

    /**
     * Creates a new instance of DVS128BiasController
     */
    public DVSBiasController(AEChip chip) {
        super(chip);

        rateEstimator = new EventRateEstimator(chip);
        FilterChain chain = new FilterChain(chip);
        chain.add(rateEstimator);
        setEnclosedFilterChain(chain);
        final String rates = "2. Event rates", policy = "3. Policy parameters", type = "1. Type of control", display = "4. Display";

        setPropertyTooltip(rates, "eventRateLowHz", "event rate in keps for LOW state, where event threshold or refractory period are reduced");
        setPropertyTooltip(rates, "eventRateHighHz", "event rate in keps for HIGH state, where event threshold or refractory period are increased");
        setPropertyTooltip(policy, "rateHysteresis", "hysteresis for state change; after state entry, state exited only when avg rate changes by this factor from threshold");
        setPropertyTooltip(policy, "hysteresisFactor", "hysteresis for state change; after state entry, state exited only when avg rate changes by this factor from threshold");
        setPropertyTooltip(policy, "tweakStepAmount", "fraction by which to tweak bias by each step, e.g. 0.1 means tweak bias current by 10% for each step");
        setPropertyTooltip(policy, "minCommandIntervalMs", "minimum time in ms between changing biases; avoids noise from changing biases too frequently");
        setPropertyTooltip(type, "goal", "<html>Overall goal of bias control"
                + "<ul> "
                + "<li> <b>LimitEventRate</b>: prevents too high or too low event rate"
                + "<li> <b>MaximuizeSNR</b>: attempt to control biases to maximize some metric of SNR"
                + "<li> <b>FillBusBandwidth</b>: set thresholds and to fill up bus capacity all the time"
                + "</ul>");
        setPropertyTooltip(type, "changeDvsRefractoryPeriod", "enables changing DVS refractory period (time after each event that pixel is reset)");
        setPropertyTooltip(type, "changeDvsEventThresholds", "enables changing DVS refractory period (time after each event that pixel is reset)");
        setPropertyTooltip(type, "changeDvsPixelBandwidth", "enables changing DVS photoreceptor/source follower analog bandwidth");
        setPropertyTooltip(display, "showAnnotation", "enables showing controller state and actions on viewer");
        setPropertyTooltip(display, "writeLogEnabled", "writes a log file called DVSBiasController-xxx.txt to the startup folder (root of jaer) to allow analyzing controller dynamics");
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
            log.warning("Wrong type of biasgen object; should be DVS128.Biasgen but object is " + chip.getBiasgen() + "; disabled filter");
            return;
        }
        DVSTweaks biasgen = (DVSTweaks) getChip().getBiasgen();
        if (biasgen == null) {
            //            log.warning("null biasgen, not doing anything");
            return;
        }
//		 biasgen.loadPreferences();
        eventRateState = State.INITIAL;
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
        getEnclosedFilterChain().filterPacket(in);

        float r = rateEstimator.getFilteredEventRate();

        setEventRateState(r);
        setBiases();
        if (writeLogEnabled) {
            if (logWriter == null) {
                logWriter = openLoggingOutputFile();
            }
            try {
                logWriter.write(in.getLastTimestamp() + " " + r + " " + eventRateState.ordinal() + "\n");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return in;
    }

    private void setEventRateState(float r) {
        lastEventRateState = eventRateState;
        switch (eventRateState) {
            case LOW_RATE:
                if (r > (eventRateLowHz * hysteresisFactor)) {
                    eventRateState = State.MEDIUM_RATE;
                }
                break;
            case MEDIUM_RATE:
                if (r < (eventRateLowHz / hysteresisFactor)) {
                    eventRateState = State.LOW_RATE;
                } else if (r > (eventRateHighHz * hysteresisFactor)) {
                    eventRateState = State.HIGH_RATE;
                }
                break;
            case HIGH_RATE:
                if (r < (eventRateHighHz / hysteresisFactor)) {
                    eventRateState = State.MEDIUM_RATE;
                }
                break;
            default:
                eventRateState = State.MEDIUM_RATE;
        }
    }

    void setBiases() {
        timeNowMs = System.currentTimeMillis();
        long dt = timeNowMs - lastCommandTime;
        if ((dt > 0) && (dt < getMinCommandIntervalMs())) {
            return; // don't saturate setup packet bandwidth and stall on blocking USB writes
        }
        lastCommandTime = timeNowMs;
        DVSTweaks biasgen = (DVSTweaks) getChip().getBiasgen();
        if (biasgen == null) {
            log.warning("null biasgen, not doing anything");
            return;
        }
        float thr = biasgen.getThresholdTweak(), refr = biasgen.getMaxFiringRateTweak(), bw = biasgen.getBandwidthTweak();
        switch (eventRateState) {
            case LOW_RATE:
                if (changeDvsEventThresholds) {
                    biasgen.setThresholdTweak(thr - getTweakStepAmount());
                }
                if (changeDvsRefractoryPeriod) {
                    biasgen.setMaxFiringRateTweak(refr + getTweakStepAmount());
                }
                if (changeDvsPixelBandwidth) {
                    biasgen.setBandwidthTweak(bw + getTweakStepAmount());
                }

                //                biasgen.decreaseThreshold();
                break;
            case HIGH_RATE:
                if (changeDvsEventThresholds) {
                    biasgen.setThresholdTweak(thr + getTweakStepAmount());
                }
                if (changeDvsRefractoryPeriod) {
                    biasgen.setMaxFiringRateTweak(refr - getTweakStepAmount());
                }
                if (changeDvsPixelBandwidth) {
                    biasgen.setBandwidthTweak(bw - getTweakStepAmount());
                }

                break;
            default:
        }

    }

    @Override
    public void initFilter() {
    }

    public float getHysteresisFactor() {
        return hysteresisFactor;
    }

    synchronized public void setHysteresisFactor(float h) {
        if (h < 1) {
            h = 1;
        } else if (h > 5) {
            h = 5;
        }
        hysteresisFactor = h;
        putFloat("hysteresisFactor", hysteresisFactor);
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

    Writer openLoggingOutputFile() {
        DateFormat loggingFilenameDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ssZ");
        String dateString = loggingFilenameDateFormat.format(new Date());
        String className = "DVS128BiasController";
        int suffixNumber = 0;
        boolean suceeded = false;
        String filename;
        Writer writer = null;
        File loggingFile;
        do {
            filename = className + "-" + dateString + "-" + suffixNumber + ".txt";
            loggingFile = new File(filename);
            if (!loggingFile.isFile()) {
                suceeded = true;
            }
        } while ((suceeded == false) && (suffixNumber++ <= 5));
        if (suceeded == false) {
            log.warning("could not open a unigue new file for logging after trying up to " + filename);
            return null;
        }
        try {
            writer = new FileWriter(loggingFile);
            log.info("starting logging bias control at " + dateString + " to file " + loggingFile.getAbsolutePath());
            writer.write("# time rate lpRate state\n");
            writer.write(String.format("# rateLowKeps=%f rateHighKeps=%f hysteresisFactor=%f\n", eventRateLowHz, eventRateHighHz, hysteresisFactor));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return writer;
    }

    EngineeringFormat fmt = new EngineeringFormat();

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (!showAnnotation) {
            return;
        }
        GL2 gl = drawable.getGL().getGL2();
        gl.glPushMatrix();
        final GLUT glut = new GLUT();
        gl.glColor3f(1, 1, 1); // must set color before raster position (raster position is like glVertex)
        gl.glRasterPos3f(0, 0, 0);
        glut.glutBitmapString(GLUT.BITMAP_HELVETICA_12, String.format("Low passed event rate=%s Hz, state=%s", fmt.format(rateEstimator.getFilteredEventRate()), eventRateState.toString()));
        gl.glPopMatrix();
    }

    public boolean isWriteLogEnabled() {
        return writeLogEnabled;
    }

    public void setWriteLogEnabled(boolean writeLogEnabled) {
        this.writeLogEnabled = writeLogEnabled;
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
     * @return the changeDvsEventThresholds
     */
    public boolean isChangeDvsEventThresholds() {
        return changeDvsEventThresholds;
    }

    /**
     * @param changeDvsEventThresholds the changeDvsEventThresholds to set
     */
    public void setChangeDvsEventThresholds(boolean changeDvsEventThresholds) {
        this.changeDvsEventThresholds = changeDvsEventThresholds;
        putBoolean("changeDvsEventThresholds", changeDvsEventThresholds);
    }

    /**
     * @return the changeDvsRefractoryPeriod
     */
    public boolean isChangeDvsRefractoryPeriod() {
        return changeDvsRefractoryPeriod;
    }

    /**
     * @param changeDvsRefractoryPeriod the changeDvsRefractoryPeriod to set
     */
    public void setChangeDvsRefractoryPeriod(boolean changeDvsRefractoryPeriod) {
        this.changeDvsRefractoryPeriod = changeDvsRefractoryPeriod;
        putBoolean("changeDvsRefractoryPeriod", changeDvsRefractoryPeriod);
    }

    /**
     * @return the changeDvsPixelBandwidth
     */
    public boolean isChangeDvsPixelBandwidth() {
        return changeDvsPixelBandwidth;
    }

    /**
     * @param changeDvsPixelBandwidth the changeDvsPixelBandwidth to set
     */
    public void setChangeDvsPixelBandwidth(boolean changeDvsPixelBandwidth) {
        this.changeDvsPixelBandwidth = changeDvsPixelBandwidth;
        putBoolean("changeDvsPixelBandwidth", changeDvsPixelBandwidth);
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
}
