/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.davis.frames;

import ch.unizh.ini.jaer.chip.retina.DVSTweaks;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Iterator;
import javax.swing.SwingUtilities;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.eventprocessing.EventFilter;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.eventprocessing.filter.SpatioTemporalCorrelationFilter;
import net.sf.jaer.graphics.ImageDisplay;
import net.sf.jaer.util.EngineeringFormat;

/**
 * Demonstrates the DAVIS reconstruction using complementary filter from
 * Cedric's algorithm in
 * <p>
 * Continuous-time Intensity Estimation Using Event Cameras. Cedric Scheerlinck
 * (2018). at
 * <https://cedric-scheerlinck.github.io/continuous-time-intensity-estimation>
 * </p>
 * From the paper
 * <p>
 * Scheerlinck, C., Barnes, N. & Mahony, R. Continuous-Time Intensity Estimation
 * Using Event Cameras. in Computer Vision – ACCV 2018 308–324 (Springer
 * International Publishing, 2019). doi:10.1007/978-3-030-20873-8_20
 * </p>
 *
 * Algorithm is from
 * https://www.cedricscheerlinck.com/files/2018_scheerlinck_continuous-time_intensity_estimation.pdf,
 * Algorithm 1
 *
 * @author Tobi Delbruck
 */
@Description("<html>DAVIS reconstruction using complementary filter algorithm. "
        + "<p>It combines a high-pass version of events LE(p, t) with a low-pass version of frames LF (p, t) to <br>"
        + "reconstruct an (approximate) all-pass version of L(p, t).<br>"
        + "<br> The crossoverFrequencyHz determines weighting. "
        + "<ul><li>Higher crossoverFrequencyHz increases dependence on frames."
        + "<li>Lower crossoverFrequencyHz increases dependence on events"
        + "</ul>See <a href=\"https://cedric-scheerlinck.github.io/continuous-time-intensity-estimation\">ComplementaryFilter on github</a>"
)
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class DavisComplementaryFilter extends ApsFrameExtractor {

    protected float onThreshold = getFloat("onThreshold", 0.25f);
    protected float offThreshold = getFloat("offThreshold", -0.25f);

    private float[] logBaseFrame, displayed01Frame;
    public float[] logFinalFrame;
    protected float crossoverFrequencyHz = getFloat("crossoverFrequencyHz", 1f);
    protected float lambda = getFloat("lambda", .1f);
    protected float kappa = getFloat("kappa", 0.05f);
    protected float alpha0 = 2 * (float) Math.PI * crossoverFrequencyHz;
    protected float[] alphas = null;
    private float minBaseLogFrame = Float.MAX_VALUE, maxBaseLogFrame = Float.MIN_VALUE;

    protected boolean thresholdsFromBiases = getBoolean("thresholdsFromBiases", true);

    protected boolean useEvents = getBoolean("useEvents", false);
    protected boolean useFrames = getBoolean("useFrames", false);
    protected boolean normalizeDisplayedFrameToMinMaxRange = getBoolean("normalizeDisplayedFrameToMinMaxRange", true);

    private FilterChain filterChain;
    private SpatioTemporalCorrelationFilter baFilter;

    protected int eventsSinceFrame;

    private int[] lastTimestamp; // the last timestamp any pixel was updated, either by event or frame
    private EventPacket<ApsDvsEvent> eventFifo = new EventPacket(ApsDvsEvent.class);
    private OutputEventIterator<ApsDvsEvent> eventFifoItr = null;
    private int eoeTimestamp = 0; // most recent end of exposure of frame
    private int soeTimestamp = 0; // most recent start of exposure of frame
    private int eofTimestamp = 0; // end of frame readout timestamp
    private int sofTimestamp = 0; // start of frame readout timestamp
    private boolean savingEvents = false; // boolean flag set to store events in eventFifo

    // We only process events up to SOE, since we know that no frame is being exposed. 
    // As soon as we get SOE, we buffer events until we get EOF (end of frame readout).
    // Then we apply all the buffered events and then the frame, using the time (SOE+EOE)/2.
    protected EngineeringFormat engFmt = new EngineeringFormat();

    /**
     * Shows pixel info
     */
    protected MouseInfo mouseInfo = null;

    public DavisComplementaryFilter(AEChip chip) {
        super(chip);
        String cf = "Complementary Filter";

        setPropertyTooltip(cf, "onThreshold", "OFF event temporal contrast threshold in log_e units");
        setPropertyTooltip(cf, "offThreshold", "OFF event temporal contrast threshold in log_e units");
        setPropertyTooltip(cf, "setThresholdsToBiasCurrentValues", "Reset ON and OFF thresholds to bias current values");

        setPropertyTooltip(cf, "crossoverFrequencyHz", "The alpha crossover frequency (but in Hz, not rad/s), increase to update more with APS frames, decrease to update more with events.");
        setPropertyTooltip(cf, "lambda", "Factor by which to decrease crossoverFrequencyHz for low and high APS exposure values that are near toe or shoulder of APS response.");
        setPropertyTooltip(cf, "kappa", "<html>Fraction of entire APS frame Lmax-Lmin range to apply the decreased crossoverFrequencyHz, i.e. to weight more with events."
                + "<p>Note that the range is not reset on each frame, so that it reflects the entire APS output range, not the range within one frame.");

        setPropertyTooltip(cf, "normalizeDisplayedFrameToMinMaxRange", "If set, then use limits to normalize output display, if unset, use maxADC limits to show output (after brightness/contrast adjustment)");
        setPropertyTooltip(cf, "linearizeOutput", "If set, linearize output. If unset, show logFinalFrame");
        setPropertyTooltip(cf, "useEvents", "Use events (see useFrames)");
        setPropertyTooltip(cf, "useFrames", "Use frames (see useEvents)");

        filterChain = new FilterChain(chip);
        baFilter = new SpatioTemporalCorrelationFilter(chip);
        filterChain.add(baFilter);
        setUseExternalRenderer(true); // we set the ImageDisplay frame contents from here, don't let super do it
        setLogCompress(false);  // to prevent ApsFrameExtractor from applying log to the raw ADC DN values
        setEnclosedFilterChain(filterChain);
        getApsDisplay().removeMouseMotionListener(super.mouseInfo);
        mouseInfo = new MouseInfo(getApsDisplay());
        getApsDisplay().addMouseMotionListener(mouseInfo);
    }

    @Override
    synchronized public void resetFilter() {
        filterChain.reset();
        if (logBaseFrame == null) {
            return;
        }
        Arrays.fill(logBaseFrame, 0);
        System.arraycopy(logBaseFrame, 0, logFinalFrame, 0, logBaseFrame.length);
        minBaseLogFrame = Float.MAX_VALUE;
        maxBaseLogFrame = Float.MIN_VALUE;
    }

    @Override
    public void initFilter() {
        super.initFilter();
        eventsSinceFrame = 0;
        filterChain.initFilters();
        final int nPixels = chip.getNumPixels();
        logBaseFrame = new float[nPixels];
        logFinalFrame = new float[nPixels];
        displayed01Frame = new float[nPixels];
        alphas = new float[nPixels];
        lastTimestamp = new int[nPixels];
        Arrays.fill(lastTimestamp, 0);
        Arrays.fill(alphas, alpha0);
        if (!isPreferenceStored("onThreshold") || !isPreferenceStored("offThreshold")) {
            setThresholdsFromBiases();
        }
        resetFilter();
    }

    public void setThresholdsFromBiases() {
        if (chip.getBiasgen() != null && chip.getBiasgen() instanceof DVSTweaks) {
            DVSTweaks tweaks = (DVSTweaks) chip.getBiasgen();
            setOnThreshold(tweaks.getOnThresholdLogE());
            setOffThreshold(-tweaks.getOffThresholdLogE()); // unsigned OFF threhsold, for mouse wheel ease
        }
    }

    public void doSetThresholdsToBiasCurrentValues() {
        setThresholdsFromBiases();
    }

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        in = getEnclosedFilterChain().filterPacket(in);  // denoise
        in = super.filterPacket(in); // extract frames, while do so, call our processDvsEvent and processEndOfFrameReadout to update CF
        updateDisplayedFrame(); // update the displayed output
        return in; // should be denoised output
    }

    @Override
    protected void processDvsEvent(ApsDvsEvent e) {
        if (useEvents && savingEvents) {
            ApsDvsEvent oe = eventFifoItr.nextOutput();
            oe.copyFrom(e);
            return;
        }
        if (!useEvents) {
            return; // only updating with frames
        }
        int k = getIndex(e.x, e.y);
        int lastT = lastTimestamp[k];
        lastTimestamp[k] = e.timestamp;
        if (rawFrame == null) {
            return; // no frame yet
        }
        int dtUs = e.timestamp - lastT;
        float dtS = 1e-6f * dtUs;
        int sign = e.getPolaritySignum(); // +1 for on, -1 for off
        float dlog = sign > 0 ? onThreshold : -offThreshold;
        float a = alphas[k];
        float oldFrameWeight = (float) (Math.exp(-a * dtS));
        if (dtUs < 0) {
            oldFrameWeight = 0;
        }
        logFinalFrame[k] = oldFrameWeight * logFinalFrame[k] + (1 - oldFrameWeight) * (logBaseFrame[k]); // correct the output
        logFinalFrame[k] += dlog; // add the event
    }

    @Override
    protected void processStartOfExposure(ApsDvsEvent e) {
        savingEvents = true; // now frame started exposing, so save DVS events in FIFO until we get the frame
        eventFifoItr = eventFifo.outputIterator(); // clears eventFifo and give us the output iterator to copy incoming events to this FIFO
    }

    @Override
    protected void processEndOfFrameReadout(ApsDvsEvent e) { // got the EOF event
        if (!useEvents) {
            Arrays.fill(logBaseFrame, 0);
            return;
        }
        savingEvents = false;  // stop saving events
        if (!useFrames) {
            return;
        }
        // Now we need process events up to the middle of the frame exposure, apply the frame, 
        // and then apply the rest of the events up to now. 
        final int frameExpAvgTimestamp = getAverageFrameExposureTimestamp();
        Iterator<ApsDvsEvent> savedEventsItr = eventFifo.inputIterator();
        while (savedEventsItr.hasNext()) {
            ApsDvsEvent se = savedEventsItr.next();
            if (se.timestamp > frameExpAvgTimestamp) {
                break;  // we reached the middle of exposure, so break out of this processing
            }
            processDvsEvent(se);
        }

        float exposureDuirationMs = 1000 * getExposureDurationS(), expHz = 1 / exposureDuirationMs;

        // Process the frame samples
        // compute new base log frame
        // and find its min/max
        final float[] f = rawFrame; // just get the buffer, don't clone it
        for (int i = 0; i < f.length; i++) {
            float v = f[i];  // DN value from 0-1023
            v = v * expHz;  // DN/ms exposure, in case there is autoexposure running
            if (v < 0) {
                v = 0;
            }
            if (v > 0) {
                v = (float) Math.log(v); // natural log
            }
            if (v < minBaseLogFrame) { // update min / max of base log frame
                minBaseLogFrame = v;
            } else if (v > maxBaseLogFrame) {
                maxBaseLogFrame = v;
            }
            logBaseFrame[i] = v;
        }
        // update alphas
        computeAlphas();
        // update model
        for (int k = 0; k < logBaseFrame.length; k++) {
            final int lastT = lastTimestamp[k];
            lastTimestamp[k] = frameExpAvgTimestamp; // TODO could overwrite a later update by an event to an earlier frame exposure time
            int dtUs = frameExpAvgTimestamp - lastT;
            final float dtS = 1e-6f * dtUs;
            final float a = alphas[k];
            float oldOutputWeight = (float) (Math.exp(-a * dtS));
            if (dtUs < 0) {
                oldOutputWeight = 0;
            }
            logFinalFrame[k] = oldOutputWeight * logFinalFrame[k] + (1 - oldOutputWeight) * (logBaseFrame[k]); // correct the output
        }
        // Process rest of saved events after the middle of exposure
        while (savedEventsItr.hasNext()) {
            ApsDvsEvent se = savedEventsItr.next();
            processDvsEvent(se);
        }

    }

    private void updateDisplayedFrame() {
        float scale, offset;
        float min = Float.MAX_VALUE, max = Float.MIN_VALUE;
        if (normalizeDisplayedFrameToMinMaxRange) {
            // find min/max of logFinalFrame
            for (final float v : logFinalFrame) {
                if (v < min) {
                    min = v;
                } else if (v > max) {
                    max = v;
                }
            }

            // linearize the log values, normalizing output to 0-1 range.
            // brightness and contrast are applied before clipping, so that we can show
            // the middle part of range even if there are outliers
            if (isLogDecompress()) {
                min = (float) Math.exp(min);
                max = (float) Math.exp(max);
            }
        } else {
            float maxVal = getMaxADC() / (1000 * getExposureDurationS());
            if (isLogDecompress()) {
                min = 0;
                max = maxVal;
            } else {
                min = 0;
                max = (float) Math.log(maxVal);
            }
        }
        scale = 1 / (max - min);
        offset = min;
        for (int i = 0; i < displayed01Frame.length; i++) {
            if (isLogDecompress()) {
                displayed01Frame[i] = (float) Math.exp(logFinalFrame[i]);
            } else {
                displayed01Frame[i] = logFinalFrame[i];
            }
            displayed01Frame[i] = scale * (displayed01Frame[i] - offset);
            displayed01Frame[i] = (displayed01Frame[i] + displayBrightness) * displayContrast;
            displayed01Frame[i] = clip01(displayed01Frame[i]);
        }

        setDisplayGrayFrame(displayed01Frame);
        if (showAPSFrameDisplay) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    getApsDisplay().repaint(30);
                }
            });
        }
    }

    private void computeAlphas() {
        final float diff = maxBaseLogFrame - minBaseLogFrame;
        final float l1 = minBaseLogFrame + getKappa() * diff;
        final float l2 = maxBaseLogFrame - getKappa() * diff;
        final float lambda1 = 1 - lambda;
        for (int i = 0; i < alphas.length; i++) {
            final float logBaseValue = logBaseFrame[i];
            if (logBaseValue < l1) {
                alphas[i] = alpha0 * (lambda + lambda1 * ((logBaseValue - minBaseLogFrame) / (l1 - minBaseLogFrame)));
            } else if (logBaseValue > l2) {
                alphas[i] = alpha0 * (lambda + lambda1 * ((logBaseValue - maxBaseLogFrame) / (l2 - maxBaseLogFrame)));

            } else {
                alphas[i] = alpha0;
            }
        }
    }

    /**
     * @return the onThreshold
     */
    public float getOnThreshold() {
        return onThreshold;
    }

    /**
     * @param onThreshold the onThreshold to set
     */
    synchronized public void setOnThreshold(float onThreshold) {
        float old = this.onThreshold;
        this.onThreshold = onThreshold;
        putFloat("onThreshold", onThreshold);
        getSupport().firePropertyChange("onThreshold", old, this.onThreshold);
    }

    /**
     * @return the offThreshold
     */
    public float getOffThreshold() {
        return offThreshold;
    }

    /**
     * @param offThreshold the offThreshold to set
     */
    public void setOffThreshold(float offThreshold) {
        float old = this.offThreshold;
        this.offThreshold = offThreshold;
        putFloat("offThreshold", offThreshold);
        getSupport().firePropertyChange("offThreshold", old, this.offThreshold);
    }

    private float clip01(float val) {
        if (val > 1) {
            val = 1;
        } else if (val < 0) {
            val = 0;
        }
        return val;
    }

    /**
     * @return the crossoverFrequencyHz
     */
    public float getCrossoverFrequencyHz() {
        return crossoverFrequencyHz;
    }

    /**
     * @param crossoverFrequencyHz the crossoverFrequencyHz to set
     */
    public void setCrossoverFrequencyHz(float crossoverFrequencyHz) {
        this.crossoverFrequencyHz = crossoverFrequencyHz;
        putFloat("crossoverFrequencyHz", crossoverFrequencyHz);
        alpha0 = 2 * (float) Math.PI * crossoverFrequencyHz;
    }

    /**
     * @return the lambda
     */
    public float getLambda() {
        return lambda;
    }

    /**
     * @param lambda the lambda to set
     */
    public void setLambda(float lambda) {
        this.lambda = lambda;
        putFloat("lambda", lambda);
    }

    /**
     * @return the kappa
     */
    public float getKappa() {
        return kappa;
    }

    /**
     * @param kappa the kappa to set
     */
    public void setKappa(float kappa) {
        this.kappa = kappa;
        putFloat("kappa", kappa);
    }

    /**
     * @return the useEvents
     */
    public boolean isUseEvents() {
        return useEvents;
    }

    /**
     * @param useEvents the useEvents to set
     */
    public void setUseEvents(boolean useEvents) {
        this.useEvents = useEvents;
        putBoolean("useEvents", useEvents);
    }

    /**
     * @return the useFrames
     */
    public boolean isUseFrames() {
        return useFrames;
    }

    /**
     * @param useFrames the useFrames to set
     */
    public void setUseFrames(boolean useFrames) {
        this.useFrames = useFrames;
        putBoolean("useFrames", useEvents);
    }

    /**
     * @return the normalizeDisplayedFrameToMinMaxRange
     */
    public boolean isNormalizeDisplayedFrameToMinMaxRange() {
        return normalizeDisplayedFrameToMinMaxRange;
    }

    /**
     * @param normalizeDisplayedFrameToMinMaxRange the
     * normalizeDisplayedFrameToMinMaxRange to set
     */
    public void setNormalizeDisplayedFrameToMinMaxRange(boolean normalizeDisplayedFrameToMinMaxRange) {
        this.normalizeDisplayedFrameToMinMaxRange = normalizeDisplayedFrameToMinMaxRange;
    }

    public void setLinearizeOutput(boolean linearizeOutput) {
        super.setLogDecompress(linearizeOutput);
    }

    public boolean isLinearizeOutput() {
        return super.isLogDecompress();
    }

    protected class FloatArrayRange {

        float[] f;
        public float min = Float.MAX_VALUE, max = Float.MIN_VALUE;

        public FloatArrayRange(float[] f) {
            this.f = f;
            compute();
        }

        private void compute() {
            for (float v : f) {
                if (v < min) {
                    min = v;
                } else if (v > max) {
                    max = v;
                }
            }
        }
    }

    private class MouseInfo extends MouseMotionAdapter {

        ImageDisplay apsImageDisplay;

        public MouseInfo(final ImageDisplay display) {
            apsImageDisplay = display;
        }

        @Override
        public void mouseMoved(final MouseEvent e) {
            final Point2D.Float p = apsImageDisplay.getMouseImagePosition(e);
            if ((p.x >= 0) && (p.x < chip.getSizeX()) && (p.y >= 0) && (p.y < chip.getSizeY())) {
                final int idx = getIndex((int) p.x, (int) p.y);
                if (rawFrame == null || logFinalFrame == null || idx < 0 || idx >= rawFrame.length) {
                    return;
                }
                EventFilter.log.info(String.format("logBaseFrame=%s logFinalFrame=%s f3dB(alpha/2pi)=%sHz tau=%ss",
                        engFmt.format(logBaseFrame[idx]), engFmt.format(logFinalFrame[idx]), engFmt.format(alphas[idx] / ((float) Math.PI * 2)),
                        engFmt.format(1 / alphas[idx])));
            }
        }
    }

}
