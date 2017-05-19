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
 * Controls exposureControlRegister automatically to try to optimize captured
 * gray levels
 *
 */
public class AutoExposureController extends Observable implements HasPropertyTooltips {

    // TODO not implemented yet
    private final DavisBaseCamera davisChip;
    private boolean autoExposureEnabled;
    private float expDelta;
    private float underOverFractionThreshold; // threshold for fraction of total pixels that are underexposed
    // or overexposed
    private final PropertyTooltipSupport tooltipSupport = new PropertyTooltipSupport();
    SimpleHistogram hist = null;
    SimpleHistogram.Statistics stats = null;
    private float lowBoundary;
    private float highBoundary;
    private boolean pidControllerEnabled;
    protected boolean centerWeighted;
    private boolean debuggingLogEnabled = false;

    public AutoExposureController(final DavisBaseCamera davisChip) {
        super();
        this.davisChip = davisChip;
        autoExposureEnabled = davisChip.getPrefs().getBoolean("autoExposureEnabled", false);
        expDelta = davisChip.getPrefs().getFloat("expDelta", 0.1F); // exposureControlRegister change if incorrectly
        // exposed
        underOverFractionThreshold = davisChip.getPrefs().getFloat("underOverFractionThreshold", 0.2F); // threshold for
        // fraction of
        // total pixels
        // that are
        // underexposed
        debuggingLogEnabled = davisChip.getPrefs().getBoolean("AutoExposureController.debuggingLogEnabled", false);
        lowBoundary = davisChip.getPrefs().getFloat("AutoExposureController.lowBoundary", 0.25F);
        highBoundary = davisChip.getPrefs().getFloat("AutoExposureController.highBoundary", 0.75F);
        pidControllerEnabled = davisChip.getPrefs().getBoolean("pidControllerEnabled", false);
        centerWeighted = davisChip.getPrefs().getBoolean("centerWeighted", false);
        tooltipSupport.setPropertyTooltip("expDelta", "fractional change of exposure when under or overexposed");
        tooltipSupport.setPropertyTooltip("underOverFractionThreshold",
                "fraction of pixel values under xor over exposed to trigger exposure change");
        tooltipSupport.setPropertyTooltip("lowBoundary", "Upper edge of histogram range considered as low values");
        tooltipSupport.setPropertyTooltip("highBoundary", "Lower edge of histogram range considered as high values");
        tooltipSupport.setPropertyTooltip("autoExposureEnabled", "Exposure time is automatically controlled when this flag is true");
        tooltipSupport.setPropertyTooltip("pidControllerEnabled",
                "<html>Enable proportional integral derivative (actually just proportional) controller rather than fixed-size step control. <p><i>expDelta</i> is multiplied by the fractional error from mid-range exposure when <i>pidControllerEnabled</i> is set");
        tooltipSupport.setPropertyTooltip("centerWeighted",
                "<html>Enable center-weighted control so that center of image is weighted more heavily in controlling exposure");
        tooltipSupport.setPropertyTooltip("debuggingLogEnabled",
                "Enable logging of autoexposure control. See console for this output.");
    }

    @Override
    public String getPropertyTooltip(final String propertyName) {
        return tooltipSupport.getPropertyTooltip(propertyName);
    }

    public void setAutoExposureEnabled(final boolean yes) {
        final boolean old = autoExposureEnabled;
        autoExposureEnabled = yes;
        davisChip.getSupport().firePropertyChange(DavisChip.PROPERTY_AUTO_EXPOSURE_ENABLED, old, yes);
        if (old != yes) {
            setChanged();
            notifyObservers();
        }
        davisChip.getPrefs().putBoolean("autoExposureEnabled", yes);
        if (!yes && (stats != null)) {
            stats.reset(); // ensure toggling enabled resets the maxBin stat
        }
    }

    public boolean isAutoExposureEnabled() {
        return autoExposureEnabled;
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
        hist.computeStatistics();
        final DavisConfig davisConfig = davisChip.getDavisConfig();
        final float exposureFrameDelayQuantizationMs = davisConfig.getExposureFrameDelayQuantizationMs();
        final float currentExposure = davisConfig.getExposureDelayMs();
        float newExposure = 0;
        float expChange = expDelta;
        if (pidControllerEnabled && (stats.maxNonZeroBin > 0)) {
            // compute error signsl from meanBin relative to actual range of bins
            final float err = (stats.meanBin - (stats.maxNonZeroBin / 2)) / (float) stats.maxNonZeroBin;
            // fraction of range exposureControlRegister is above middle bin
            expChange = expDelta * Math.abs(err);
        }
        if ((stats.fracLow >= underOverFractionThreshold) && (stats.fracHigh < underOverFractionThreshold)) {
            newExposure = currentExposure * (1 + expChange);
            if (newExposure < currentExposure + exposureFrameDelayQuantizationMs) {
                newExposure = currentExposure + exposureFrameDelayQuantizationMs; // ensure increase
            }
            if (newExposure != currentExposure) {
                davisConfig.setExposureDelayMs(newExposure);
            }
            float actualExposure = davisConfig.getExposureDelayMs();
            if (debuggingLogEnabled) {
                davisChip.getLog().log(Level.INFO, "Underexposed: {0} {1}", new Object[]{stats.toString(),
                    String.format("expChange=%.2f (oldExposure=%10.6fs newExposure=%10.6fs)", expChange, currentExposure, actualExposure)});
            }
        } else if ((stats.fracLow < underOverFractionThreshold) && (stats.fracHigh >= underOverFractionThreshold)) {
            newExposure = currentExposure * (1 - expChange);
            if (newExposure > currentExposure - exposureFrameDelayQuantizationMs) {
                newExposure = currentExposure - exposureFrameDelayQuantizationMs; // ensure decrease even with rounding.
            }
            if (newExposure < 0) {
                newExposure = 0;
            }
            if (newExposure != currentExposure) {
                davisConfig.setExposureDelayMs(newExposure);
            }
            float actualExposure = davisConfig.getExposureDelayMs();
            if (debuggingLogEnabled) {
                davisChip.getLog().log(Level.INFO, "Overexposed: {0} {1}", new Object[]{stats.toString(),
                    String.format("expChange=%.2f (oldExposure=%10.6fs newExposure=%10.6fs)", expChange, currentExposure, actualExposure)});
            }
        } else {
            // log.info(stats.toString());
        }
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

    /**
     * Sets by what relative amount the exposureControlRegister is changed on
     * each frame if under or over exposed.
     *
     * @param expDelta the expDelta to set
     */
    public void setExpDelta(final float expDelta) {
        this.expDelta = expDelta;
        davisChip.getPrefs().putFloat("expDelta", expDelta);
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
     * Gets the fraction of pixel values that must be under xor over exposed to
     * change exposureControlRegister automatically.
     *
     * @param underOverFractionThreshold the underOverFractionThreshold to set
     */
    public void setUnderOverFractionThreshold(final float underOverFractionThreshold) {
        this.underOverFractionThreshold = underOverFractionThreshold;
        davisChip.getPrefs().putFloat("underOverFractionThreshold", underOverFractionThreshold);
    }

    public float getLowBoundary() {
        return lowBoundary;
    }

    public void setLowBoundary(final float lowBoundary) {
        this.lowBoundary = lowBoundary;
        davisChip.getPrefs().putFloat("AutoExposureController.lowBoundary", lowBoundary);
    }

    public float getHighBoundary() {
        return highBoundary;
    }

    public void setHighBoundary(final float highBoundary) {
        this.highBoundary = highBoundary;
        davisChip.getPrefs().putFloat("AutoExposureController.highBoundary", highBoundary);
    }

    /**
     * @return the pidControllerEnabled
     */
    public boolean isPidControllerEnabled() {
        return pidControllerEnabled;
    }

    /**
     * @param pidControllerEnabled the pidControllerEnabled to set
     */
    public void setPidControllerEnabled(final boolean pidControllerEnabled) {
        this.pidControllerEnabled = pidControllerEnabled;
        davisChip.getPrefs().putBoolean("pidControllerEnabled", pidControllerEnabled);
    }

    /**
     * @return the centerWeighted
     */
    public boolean isCenterWeighted() {
        return centerWeighted;
    }

    /**
     * @param centerWeighted the centerWeighted to set
     */
    public void setCenterWeighted(final boolean centerWeighted) {
        this.centerWeighted = centerWeighted;
    }

    /**
     * @return the debuggingLogEnabled
     */
    public boolean isDebuggingLogEnabled() {
        return debuggingLogEnabled;
    }

    /**
     * @param debuggingLogEnabled the debuggingLogEnabled to set
     */
    public void setDebuggingLogEnabled(boolean debuggingLogEnabled) {
        this.debuggingLogEnabled = debuggingLogEnabled;
        if (davisChip != null) {
            davisChip.getPrefs().putBoolean("AutoExposureController.debuggingLogEnabled", debuggingLogEnabled);
        }
    }

}
