/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import java.util.Observable;
import java.util.logging.Level;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.graphics.AEViewer;
import net.sf.jaer.util.HasPropertyTooltips;
import net.sf.jaer.util.PropertyTooltipSupport;
import net.sf.jaer.util.histogram.SimpleHistogram;

/**
 * Controls APS exposure time from the APS histogram of measured digital numbers
 * (DN).
 * <p>
 * Full scale is the learned analog [min, max] of occupied histogram bins, not
 * the 10-bit ADC length. The controller is camera/libcamera-style: a single
 * target-grey metric (histogram mean vs midpoint of {@code [lowBoundary,
 * highBoundary]}), with highlights as a one-sided clamp (never increase while
 * {@code fracHigh} is near {@code underOverFractionThreshold}). After each
 * exposure write, several APS frames are skipped so the next decision uses the
 * new integration, not the in-flight frame. There is no I or D term.
 */
public class AutoExposureController extends Observable implements HasPropertyTooltips {

    private static final float STAT_LP_ALPHA = 0.3f;
    /** Max |Δexposure|/exposure per accepted update (libcamera-style clamp). */
    private static final float MAX_STEP = 0.5f;
    /**
     * APS frames to ignore after an exposure write. The histogram at
     * {@code controlExposure} is from the frame that just finished; that frame
     * (and often the next) still used the previous exposure register.
     */
    private static final int SETTLE_FRAMES = 3;

    private final DavisBaseCamera davisChip;

    private boolean autoExposureEnabled;
    private float expDelta;
    private float underOverFractionThreshold; // threshold for fraction of total pixels that are underexposed or overexposed
    private float lowBoundary;
    private float highBoundary;
    private float hysteresis;
    private boolean pidControllerEnabled;
    protected boolean centerWeighted;
    private boolean debuggingLogEnabled = false;

    private final PropertyTooltipSupport tooltipSupport = new PropertyTooltipSupport();
    SimpleHistogram hist = null;
    SimpleHistogram.Statistics stats = null;
    /** Learned analog floor/ceiling in histogram bins; survives histogram double-buffering. */
    private int analogMinBin = 0;
    private int analogMaxBin = 0;
    private boolean analogRangeInitialized = false;
    private float lpFracLow;
    private float lpFracHigh;
    private float lpMeanBin;
    private boolean fracLpInitialized = false;
    /** Remaining APS frames to skip after the last exposure write. */
    private int settleFramesRemaining = 0;
    /** True while highlights are at/near the clip limit (Schmitt on fracHigh). */
    private boolean clipLimited = false;
    private boolean loadingPreferences = false;

    private static final String PREF = "AutoExposureController.";

    public AutoExposureController(final DavisBaseCamera davisChip) {
        super();
        this.davisChip = davisChip;
        loadPreferences();

        tooltipSupport.setPropertyTooltip("expDelta",
                "max |Δexposure|/exposure per update (also clamped to 50%). 0.1 is about 10% per settled frame.");
        tooltipSupport.setPropertyTooltip("underOverFractionThreshold",
                "highlight clamp: if this fraction of samples is in the high analog band, exposure may decrease and will not increase until fracHigh is hysteresis below this value. Shadows never force an increase.");
        tooltipSupport.setPropertyTooltip("hysteresis",
                "deadband on normalized mean error, and the Schmitt gap on the highlight clamp. 0 chatters; 0.05 is typical.");
        tooltipSupport.setPropertyTooltip("lowBoundary",
                "low edge of the target-grey band, as a fraction of the learned analog min-max DN range. Target mean is the midpoint of low/highBoundary.");
        tooltipSupport.setPropertyTooltip("highBoundary",
                "high edge of the target-grey band, as a fraction of the learned analog min-max DN range. Also the start of the highlight/clip band.");
        tooltipSupport.setPropertyTooltip("autoExposureEnabled", "Exposure time is automatically controlled when this flag is true");
        tooltipSupport.setPropertyTooltip("pidControllerEnabled",
                "<html>If set, new exposure = current * clamp(targetMean / measuredMean) (linear APS gain, libcamera-style). If cleared, step is ±expDelta outside the deadband. Highlights still clamp both modes. No I or D term.");
        tooltipSupport.setPropertyTooltip("centerWeighted",
                "<html>Weight the histogram toward the image center so peripheral pixels affect exposure less");
        tooltipSupport.setPropertyTooltip("debuggingLogEnabled",
                "Enable logging of autoexposure control. See console for this output.");
    }

    @Override
    public String getPropertyTooltip(final String propertyName) {
        return tooltipSupport.getPropertyTooltip(propertyName);
    }

    public void controlExposure() {
        if (!autoExposureEnabled) {
            return;
        }
        if ((davisChip.getAeViewer() != null) && (davisChip.getAeViewer().getPlayMode() != null)
                && (davisChip.getAeViewer().getPlayMode() != AEViewer.PlayMode.LIVE)) {
            return;
        }
        hist = davisChip.davisRenderer.getAdcSampleValueHistogram();
        if (hist == null) {
            return;
        }
        stats = hist.getStatistics();
        if (stats == null) {
            return;
        }
        stats.setLowBoundary(lowBoundary);
        stats.setHighBoundary(highBoundary);
        stats.setLearnedAnalogRange(analogMinBin, analogMaxBin, analogRangeInitialized);
        hist.computeStatistics();
        if (stats.isAnalogRangeInitialized()) {
            analogMinBin = stats.minNonZeroBin;
            analogMaxBin = stats.maxNonZeroBin;
            analogRangeInitialized = true;
        }

        if (!fracLpInitialized) {
            lpFracLow = stats.fracLow;
            lpFracHigh = stats.fracHigh;
            lpMeanBin = stats.meanBin;
            fracLpInitialized = true;
        } else {
            lpFracLow += STAT_LP_ALPHA * (stats.fracLow - lpFracLow);
            lpFracHigh += STAT_LP_ALPHA * (stats.fracHigh - lpFracHigh);
            lpMeanBin += STAT_LP_ALPHA * (stats.meanBin - lpMeanBin);
        }

        if (settleFramesRemaining > 0) {
            settleFramesRemaining--;
            return;
        }

        final int analogRange = analogMaxBin - analogMinBin;
        if (analogRange < 4) {
            return;
        }

        // Camera-style target grey: mean DN at the midpoint of the "good" analog band.
        final float targetNorm = 0.5f * (lowBoundary + highBoundary);
        float meanNorm = (lpMeanBin - analogMinBin) / (float) analogRange;
        if (meanNorm < 0.02f) {
            meanNorm = 0.02f;
        } else if (meanNorm > 0.98f) {
            meanNorm = 0.98f;
        }
        final float error = meanNorm - targetNorm; // >0 too bright

        final float thresh = underOverFractionThreshold;
        final float deadband = Math.max(0f, hysteresis);
        // Schmitt on highlights: enter clip-limited when over thresh, leave only
        // after fracHigh is hysteresis below thresh. While limited, never increase.
        if (lpFracHigh >= thresh) {
            clipLimited = true;
        } else if (lpFracHigh < (thresh - deadband)) {
            clipLimited = false;
        }

        if (!clipLimited && (Math.abs(error) <= deadband)) {
            return;
        }
        if (clipLimited && (lpFracHigh <= thresh) && (error <= deadband)) {
            // Highlight-limited hold: mean may still be dark (HDR); do not hunt.
            return;
        }

        final float maxStep = Math.min(Math.max(expDelta, 0.01f), MAX_STEP);
        float ratio;
        if (pidControllerEnabled) {
            // Linear APS: DN ~ exposure * irradiance, so scale exposure by target/mean.
            ratio = targetNorm / meanNorm;
        } else if ((error > 0) || (lpFracHigh > thresh)) {
            ratio = 1f - maxStep;
        } else {
            ratio = 1f + maxStep;
        }
        if (lpFracHigh > thresh) {
            final float clipErr = (lpFracHigh - thresh) / Math.max(thresh, 0.05f);
            final float clipStep = pidControllerEnabled ? Math.min(maxStep, expDelta * clipErr) : maxStep;
            if (ratio > (1f - clipStep)) {
                ratio = 1f - clipStep;
            }
        } else if (clipLimited && (ratio > 1f)) {
            ratio = 1f;
        }
        if (ratio > (1f + maxStep)) {
            ratio = 1f + maxStep;
        } else if (ratio < (1f - maxStep)) {
            ratio = 1f - maxStep;
        }
        if (Math.abs(ratio - 1f) < 1e-4f) {
            return;
        }

        final DavisConfig davisConfig = davisChip.getDavisConfig();
        final float quantizationMs = davisConfig.getExposureFrameDelayQuantizationMs();
        final float currentExposure = davisConfig.getExposureDelayMs();
        float newExposure = currentExposure * ratio;
        if (newExposure < 0) {
            newExposure = 0;
        }
        if (Math.abs(newExposure - currentExposure) < (0.5f * quantizationMs)) {
            return;
        }
        davisConfig.setExposureDelayMs(newExposure);
        settleFramesRemaining = SETTLE_FRAMES;
        if (debuggingLogEnabled) {
            final float actualExposure = davisConfig.getExposureDelayMs();
            davisChip.getLog().log(Level.INFO, "{0}",
                    String.format("%s err=%.3f meanN=%.2f tgt=%.2f lpHigh=%.2f clip=%s ratio=%.3f (old=%.3f ms new=%.3f ms) %s",
                            ratio < 1f ? "DECREASE" : "INCREASE",
                            error, meanNorm, targetNorm, lpFracHigh, clipLimited, ratio,
                            currentExposure, actualExposure, stats.toString()));
        }
    }

    public void storePreferences() {
        final java.util.prefs.Preferences p = davisChip.getPrefs();
        p.putFloat(PREF + "expDelta", expDelta);
        p.putBoolean(PREF + "autoExposureEnabled", autoExposureEnabled);
        p.putFloat(PREF + "underOverFractionThreshold", underOverFractionThreshold);
        p.putFloat(PREF + "lowBoundary", lowBoundary);
        p.putFloat(PREF + "highBoundary", highBoundary);
        p.putFloat(PREF + "hysteresis", hysteresis);
        p.putBoolean(PREF + "pidControllerEnabled", pidControllerEnabled);
        p.putBoolean(PREF + "centerWeighted", centerWeighted);
        p.putBoolean(PREF + "debuggingLogEnabled", debuggingLogEnabled);
    }

    final public void loadPreferences() {
        loadingPreferences = true;
        try {
            final java.util.prefs.Preferences p = davisChip.getPrefs();
            setAutoExposureEnabled(p.getBoolean(PREF + "autoExposureEnabled", p.getBoolean("autoExposureEnabled", true)));
            setExpDelta(p.getFloat(PREF + "expDelta", p.getFloat("expDelta", 0.5F)));
            setUnderOverFractionThreshold(p.getFloat(PREF + "underOverFractionThreshold", p.getFloat("underOverFractionThreshold", 0.2F)));
            setDebuggingLogEnabled(p.getBoolean(PREF + "debuggingLogEnabled", p.getBoolean("AutoExposureController.debuggingLogEnabled", false)));
            setLowBoundary(p.getFloat(PREF + "lowBoundary", p.getFloat("AutoExposureController.lowBoundary", 0.25F)));
            setHighBoundary(p.getFloat(PREF + "highBoundary", p.getFloat("AutoExposureController.highBoundary", 0.75F)));
            setHysteresis(p.getFloat(PREF + "hysteresis", 0.05F));
            setPidControllerEnabled(p.getBoolean(PREF + "pidControllerEnabled", p.getBoolean("pidControllerEnabled", true)));
            setCenterWeighted(p.getBoolean(PREF + "centerWeighted", p.getBoolean("centerWeighted", false)));
        } finally {
            loadingPreferences = false;
        }
    }

    /** Marks hardware configuration dirty so Renew/Quit offers to save XML (and storePreferences). */
    private void onUserChange() {
        if (loadingPreferences) {
            return;
        }
        if ((davisChip.getAeViewer() != null) && (davisChip.getAeViewer().getBiasgenFrame() != null)) {
            davisChip.getAeViewer().getBiasgenFrame().setFileModified(true);
        }
    }

    public void setAutoExposureEnabled(final boolean yes) {
        final boolean old = autoExposureEnabled;
        autoExposureEnabled = yes;
        davisChip.getSupport().firePropertyChange(DavisChip.PROPERTY_AUTO_EXPOSURE_ENABLED, old, yes);
        if (old != yes) {
            setChanged();
            notifyObservers();
            onUserChange();
        }
        if (!yes) {
            analogRangeInitialized = false;
            analogMinBin = 0;
            analogMaxBin = 0;
            fracLpInitialized = false;
            settleFramesRemaining = 0;
            clipLimited = false;
            if (stats != null) {
                stats.reset();
            }
            if (hist != null) {
                hist.reset();
            }
        }
    }

    /**
     * Sets by what relative amount the exposureControlRegister is changed on
     * each frame if under or over exposed.
     *
     * @param expDelta the expDelta to set
     */
    public void setExpDelta(final float expDelta) {
        float old = this.expDelta;
        this.expDelta = expDelta;
        davisChip.getSupport().firePropertyChange(DavisChip.PROPERTY_AUTO_EXP_DELTA, old, expDelta);
        if (old != expDelta) {
            setChanged();
            notifyObservers();
            onUserChange();
        }
    }

    /**
     * Gets the fraction of pixel values that must be under xor over exposed to
     * change exposureControlRegister automatically.
     *
     * @param underOverFractionThreshold the underOverFractionThreshold to set
     */
    public void setUnderOverFractionThreshold(final float underOverFractionThreshold) {
        final float old = this.underOverFractionThreshold;
        this.underOverFractionThreshold = underOverFractionThreshold;
        davisChip.getSupport().firePropertyChange(DavisChip.PROPERTY_UNDER_OVER_DELTA, old, underOverFractionThreshold);
        if (old != underOverFractionThreshold) {
            setChanged();
            notifyObservers();
            onUserChange();
        }
    }

    public void setLowBoundary(final float lowBoundary) {
        final float old = this.lowBoundary;
        this.lowBoundary = lowBoundary;
        davisChip.getSupport().firePropertyChange(DavisChip.PROPERTY_LOW_BOUNDARY, old, lowBoundary);
        if (old != lowBoundary) {
            setChanged();
            notifyObservers();
            onUserChange();
        }
    }

    public void setHighBoundary(final float highBoundary) {
        final float old = this.highBoundary;
        this.highBoundary = highBoundary;
        davisChip.getSupport().firePropertyChange(DavisChip.PROPERTY_HIGH_BOUNDARY, old, highBoundary);
        if (old != highBoundary) {
            setChanged();
            notifyObservers();
            onUserChange();
        }
    }

    public void setHysteresis(final float hysteresis) {
        final float old = this.hysteresis;
        this.hysteresis = hysteresis < 0 ? 0 : hysteresis;
        if (old != this.hysteresis) {
            setChanged();
            notifyObservers();
            onUserChange();
        }
    }

    /**
     * @param pidControllerEnabled the pidControllerEnabled to set
     */
    public void setPidControllerEnabled(final boolean pidControllerEnabled) {
        final boolean old = this.pidControllerEnabled;
        this.pidControllerEnabled = pidControllerEnabled;
        davisChip.getSupport().firePropertyChange(DavisChip.PROPERTY_PID_CONTROLLER_ENABLED, old, pidControllerEnabled);
        if (old != pidControllerEnabled) {
            setChanged();
            notifyObservers();
            onUserChange();
        }
    }

    /**
     * @param centerWeighted the centerWeighted to set
     */
    public void setCenterWeighted(final boolean centerWeighted) {
        final boolean old = this.centerWeighted;
        this.centerWeighted = centerWeighted;
        davisChip.getSupport().firePropertyChange(DavisChip.PROPERTY_CENTER_WEIGHTED, old, centerWeighted);
        if (old != centerWeighted) {
            setChanged();
            notifyObservers();
            onUserChange();
        }
    }

    /**
     * @param debuggingLogEnabled the debuggingLogEnabled to set
     */
    public void setDebuggingLogEnabled(boolean debuggingLogEnabled) {
        final boolean old = this.debuggingLogEnabled;
        this.debuggingLogEnabled = debuggingLogEnabled;
        if (old != debuggingLogEnabled) {
            setChanged();
            notifyObservers();
            onUserChange();
        }
    }

    /**
     * @return the centerWeighted
     */
    public boolean isCenterWeighted() {
        return centerWeighted;
    }

    /**
     * @return the debuggingLogEnabled
     */
    public boolean isDebuggingLogEnabled() {
        return debuggingLogEnabled;
    }

    /**
     * @return the pidControllerEnabled
     */
    public boolean isPidControllerEnabled() {
        return pidControllerEnabled;
    }

    public float getHighBoundary() {
        return highBoundary;
    }

    public float getHysteresis() {
        return hysteresis;
    }

    public float getLowBoundary() {
        return lowBoundary;
    }

    /**
     * Gets the fraction of pixel values that must be under xor over exposed to
     * change exposureControlRegister automatically.
     *
     * @return the underOverFractionThreshold
     */
    public float getUnderOverFractionThreshold() {
        return underOverFractionThreshold;
    }

    /**
     * Gets by what relative amount the exposureControlRegister is changed on
     * each frame if under or over exposed.
     *
     * @return the expDelta
     */
    public float getExpDelta() {
        return expDelta;
    }

    public boolean isAutoExposureEnabled() {
        return autoExposureEnabled;
    }

}
