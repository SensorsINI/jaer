/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.davis.frames;

import ch.unizh.ini.jaer.chip.retina.DVSTweaks;
import eu.seebetter.ini.chips.DavisChip;
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
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter;
import net.sf.jaer.eventprocessing.EventFilter2D;
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
@Description("DAVIS reconstruction using complementary filter from Cedric's algorithm")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class DavisComplementaryFilter extends ApsFrameExtractor {

    /**
     * A boolean to set whether the OpenGL acceleration should be used
     */
//    private boolean useOpenCV = getPrefs().getBoolean("ApsFrameExtrapolation.useOpenCV", false);
//    {setPropertyTooltip("useOpenCV", "A boolean to set whether the OpenGL acceleration should be used");}
//    private boolean revertLearning = getBoolean("revertLearning", false);
//    private boolean displayError = getBoolean("displayError", false);
//    private boolean freezeGains = getBoolean("freezeGains", false);
//    private boolean clipping = getBoolean("clipping", true);
//    private boolean fixThresholdsToAverage = getBoolean("fixThresholdsToAverage", true);
    private float onThreshold = getFloat("onThreshold", 0.001f);
    private float offThreshold = getFloat("offThreshold", -0.001f);

    private float[] logBaseFrame, logFinalFrame, displayed01Frame; // the log of the last APS frame and the final log CF frame

    protected float crossoverFrequencyHz = getFloat("crossoverFrequencyHz", 1f);
    protected float lambda = getFloat("lambda", .1f);
    protected float kappa = getFloat("lambda", 0.05f);
    protected float alpha0 = 2 * (float) Math.PI * crossoverFrequencyHz;
    protected float[] alphas = null;
    private float minBaseLogFrame = Float.MAX_VALUE, maxBaseLogFrame = Float.MIN_VALUE;

    private boolean thresholdsFromBiases = getBoolean("thresholdsFromBiases", true);

    private FilterChain filterChain;
    private SpatioTemporalCorrelationFilter baFilter;

    public int eventsSinceFrame;

    private int[] lastTimestamp; // the last timestamp any pixel was updated, either by event or frame

    private EngineeringFormat engFmt = new EngineeringFormat();
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

        setPropertyTooltip(cf, "crossoverFrequencyHz", "The alpha crossover frequency in Hz, increase to update more with APS frames");
        setPropertyTooltip(cf, "lambda", "Factor by which to increase crossoverFrequencyHz for low and high APS exposure values that are near toe or shoulder of APS response");
        setPropertyTooltip(cf, "kappa", "Fraction of entire Lmax-Lmin range to apply the decreased crossoverFrequencyHz");

//        setPropertyTooltip("revertLearning", "Revert gains back to manual settings");
//        setPropertyTooltip("clipping", "Clip the image to 0-1 range; use to reset reconstructed frame if it has diverged. Sometimes turning off clipping helps learn better gains, faster and more stably.");
//        setPropertyTooltip("displayError", "Show the pixel errors compared to last frame TODO");
//        setPropertyTooltip("freezeGains", "Freeze the gains");
//        setPropertyTooltip("perPixelAndTimeBinErrorUpdateFactor", "Sets the rate that error affects gains of individual pixels always (independent of fixThresholdsToAverage) and per time bin since last event. Ideally 1 should correct the gains from a single frame. Range 0-1, typical value 0.4.");
//        setPropertyTooltip("globalAverageGainMixingFactor", "mixing factor for average of time bins, range 0-1, increase to speed up learning.");
//        setPropertyTooltip("revertLearning", "If set, continually reverts gains to the manual settings.");
        filterChain = new FilterChain(chip);
        baFilter = new SpatioTemporalCorrelationFilter(chip);
        filterChain.add(baFilter);
        setUseExternalRenderer(true); // we set the ImageDisplay frame contents from here, don't let super do it
        setLogCompress(false);
        setLogDecompress(false);
        setEnclosedFilterChain(filterChain);
        getApsDisplay().removeMouseMotionListener(super.mouseInfo);
        mouseInfo = new MouseInfo(getApsDisplay());
        getApsDisplay().addMouseMotionListener(mouseInfo);
    }

//    synchronized public void doRevertLearning() {
//        revertLearning();
//    }
    @Override
    synchronized public void resetFilter() {
        filterChain.reset();
        if (logBaseFrame == null) {
            return;
        }
        System.arraycopy(logBaseFrame, 0, logFinalFrame, 0, logBaseFrame.length);
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
        in = super.filterPacket(in);
        updateDisplayedFrame();
        return in;
    }

    @Override
    protected void processDvsEvent(ApsDvsEvent e) {
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
        float decay = (float) (Math.exp(-a * dtS));
        logFinalFrame[k] = decay * logFinalFrame[k] + (1 - decay) * (logBaseFrame[k]); // correct the output
        logFinalFrame[k] += dlog; // add the event
    }

    @Override
    protected void processNewFrame() {

        // compute new base log frame
        // and find its min/max
        minBaseLogFrame = Float.MAX_VALUE;
        maxBaseLogFrame = Float.MIN_VALUE;
        for (int i = 0; i < rawFrame.length; i++) {
            float v = rawFrame[i];  // DN value from 0-1023
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
        int timestamp = getAverageFrameExposureTimestamp();
        for (int k = 0; k < logBaseFrame.length; k++) {
            int lastT = lastTimestamp[k];
            lastTimestamp[k] = timestamp;
            if (rawFrame == null) {
                return; // no frame yet
            }
            int dtUs = timestamp - lastT;
            float dtS = 1e-6f * dtUs;
            float a = alphas[k];
            float decay = (float) (Math.exp(-a * dtS));
            logFinalFrame[k] = decay * logFinalFrame[k] + (1 - decay) * (logBaseFrame[k]); // correct the output
        }

    }

    private void updateDisplayedFrame() {
        float min = Float.MAX_VALUE, max = Float.MIN_VALUE;
        for (int i = 0; i < logFinalFrame.length; i++) {
            float v = logFinalFrame[i];

            if (v < min) {
                min = v;
            } else if (v > max) {
                max = v;
            }
        }

        float scale = 1 / (max - min);

        for (int i = 0; i < displayed01Frame.length; i++) {
            displayed01Frame[i] = scale * (logFinalFrame[i] - min);
            displayed01Frame[i]=(displayed01Frame[i]+displayBrightness)*displayContrast;
            displayed01Frame[i]=clip01(displayed01Frame[i]);
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
        final int n = alphas.length;
        final float diff = maxBaseLogFrame - minBaseLogFrame;
        final float l1 = minBaseLogFrame + lambda * diff;
        final float l2 = maxBaseLogFrame - lambda * diff;
        final float lambda1 = 1 - lambda;
        for (int i = 0; i < n; i++) {
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

//    synchronized private void revertLearning() {
//    }
//    /**
//     * @return the displayError
//     */
//    public boolean isDisplayError() {
//        return displayError;
//    }
//    /**
//     * @param displayError the displayError to set
//     */
//    synchronized public void setDisplayError(boolean displayError) {
//        this.displayError = displayError;
//        putBoolean("displayError", displayError);
//    }
//
//    public boolean isFixThresholdsToAverage() {
//        return fixThresholdsToAverage;
//    }
//
//    /**
//     * @param fixThresholdsToAverage the fixThresholdsToAverage to set
//     */
//    synchronized public void setFixThresholdsToAverage(boolean fixThresholdsToAverage) {
//        this.fixThresholdsToAverage = fixThresholdsToAverage;
//    }
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

    /**
     * // * @return the freezeGains //
     */
//    public boolean isFreezeGains() {
//        return freezeGains;
//    }
//
//    /**
//     * @param freezeGains the freezeGains to set
//     */
//    public void setFreezeGains(boolean freezeGains) {
//        this.freezeGains = freezeGains;
//    }
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
