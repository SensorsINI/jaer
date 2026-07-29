/*
 * Copyright (C) SensorsINI / jAER.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */
package net.sf.jaer.eventprocessing.filter;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.Arrays;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.Preferred;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2DMouseROI;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;
import net.sf.jaer.util.DrawGL;
import net.sf.jaer.util.EngineeringFormat;
import net.sf.jaer.util.filter.LowpassFilter;

/**
 * Measures RPM / RPS (Hz) from periodicity of event rate inside a mouse-selected
 * ROI. Tuned for fundamental frequencies up to 1&nbsp;kHz.
 * <p>
 * Usage: enable the filter, open its controls (so mouse selection is active),
 * then drag on the display to select an ROI that sees a repeating feature of
 * the rotating object (mark, blade tip, LED, etc.). The filter bins ROI event
 * rate and estimates the fundamental period via autocorrelation.
 *
 * @author tobi
 */
@Description("<html>Measures RPM/RPS (Hz) from event-rate periodicity in a mouse ROI.<br>"
        + "Enable filter, select Controls, drag ROI over a repeating feature; tuned up to 1&nbsp;kHz.")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class RpmMeter extends EventFilter2DMouseROI implements FrameAnnotater {

    private static final int DEFAULT_BIN_US = 250; // 4 kHz sampling → Nyquist 2 kHz
    private static final float DEFAULT_MAX_FREQ_HZ = 1000f;

    @Preferred
    private int binWidthUs = getInt("binWidthUs", DEFAULT_BIN_US);
    @Preferred
    private float historyDurationMs = getFloat("historyDurationMs", 500f);
    @Preferred
    private float minFreqHz = getFloat("minFreqHz", 5f);
    @Preferred
    private float maxFreqHz = getFloat("maxFreqHz", DEFAULT_MAX_FREQ_HZ);
    @Preferred
    private float pulsesPerRevolution = getFloat("pulsesPerRevolution", 1f);
    @Preferred
    private float peakSignificance = getFloat("peakSignificance", 0.15f);
    @Preferred
    private boolean showRpm = getBoolean("showRpm", true);
    @Preferred
    private boolean showRps = getBoolean("showRps", true);
    @Preferred
    private boolean showRateTrace = getBoolean("showRateTrace", true);
    @Preferred
    private int fontSize = getInt("fontSize", 10);
    private float rateDisplayTauMs = getFloat("rateDisplayTauMs", 50f);
    private float frequencyDisplayTauMs = getFloat("frequencyDisplayTauMs", 200f);
    private int updateIntervalMs = getInt("updateIntervalMs", 50);

    private float[] rateBins = new float[0]; // circular buffer of event rates (eps) per bin
    private int rateWriteIdx = 0;
    private int rateCount = 0; // how many valid samples written since reset
    private int currentBinEventCount = 0;
    private int currentBinStartTs = Integer.MIN_VALUE;
    private boolean binningInitialized = false;

    private volatile float instantaneousRoiRateHz = 0;
    private volatile float filteredRoiRateHz = 0;
    private volatile float fundamentalFreqHz = Float.NaN; // before pulsesPerRevolution
    private volatile float filteredRps = Float.NaN;
    private volatile float filteredRpm = Float.NaN;
    private volatile float lastPeakCorrelation = 0;
    private volatile boolean measurementValid = false;
    private volatile String statusMessage = "Select ROI with mouse";

    private final LowpassFilter rateLp = new LowpassFilter();
    private final LowpassFilter freqLp = new LowpassFilter();
    private final EngineeringFormat engFmt = new EngineeringFormat();

    private int lastFreqUpdateTs = Integer.MIN_VALUE;
    private float[] acfWork = new float[0]; // scratch for ACF lag scores (display)
    private int acfMinLag = 0, acfMaxLag = 0;
    private int acfBestLag = -1;

    // Snapshot of rate history for annotation (copied under lock)
    private float[] rateTraceSnapshot = new float[0];
    private int rateTraceSnapshotLen = 0;

    public RpmMeter(AEChip chip) {
        super(chip);
        setShowCrossHairCursor(true);
        setMultiROI(false);

        final String meas = "1: Measurement", disp = "2: Display", adv = "3: Advanced";
        setPropertyTooltip(meas, "binWidthUs",
                "<html>Rate histogram bin width in µs.<br>Default 250 µs (4 kHz) supports fundamentals up to ~1 kHz (Nyquist 2 kHz).");
        setPropertyTooltip(meas, "historyDurationMs",
                "Duration of rate history used for autocorrelation (ms). Longer helps low RPM; shorter tracks changes faster.");
        setPropertyTooltip(meas, "minFreqHz", "Lowest fundamental frequency searched (Hz). Sets maximum ACF lag.");
        setPropertyTooltip(meas, "maxFreqHz", "Highest fundamental frequency searched (Hz). Should be ≤ 0.5 / binWidth.");
        setPropertyTooltip(meas, "pulsesPerRevolution",
                "<html>How many rate peaks (pulses) occur per mechanical revolution.<br>"
                + "E.g. 3 for a 3-blade fan if each blade sweeps the ROI once per rev. RPS = f_peak / pulsesPerRevolution.");
        setPropertyTooltip(meas, "peakSignificance",
                "Minimum ACF peak / zero-lag ratio to accept a period estimate (0–1). Raise if noisy; lower if weak modulation.");
        setPropertyTooltip(meas, "updateIntervalMs", "How often to recompute autocorrelation (ms of event time).");
        setPropertyTooltip(meas, "rateDisplayTauMs", "Lowpass time constant (ms) for displayed ROI event rate.");
        setPropertyTooltip(meas, "frequencyDisplayTauMs", "Lowpass time constant (ms) for displayed RPS/RPM.");
        setPropertyTooltip(disp, "showRpm", "Show revolutions per minute");
        setPropertyTooltip(disp, "showRps", "Show revolutions per second (Hz)");
        setPropertyTooltip(disp, "showRateTrace", "Overlay recent ROI event-rate trace and ACF peak marker");
        setPropertyTooltip(disp, "fontSize", "Annotation font size");
        setPropertyTooltip(disp, "clearROI", "Clears the ROI selection");
        setPropertyTooltip(disp, "freezeRoi", "Freeze ROI so it cannot be changed by accident");

        rateLp.setTauMs(rateDisplayTauMs);
        freqLp.setTauMs(frequencyDisplayTauMs);
        allocateBuffers();
    }

    private synchronized void allocateBuffers() {
        int n = Math.max(32, (int) Math.ceil((historyDurationMs * 1000f) / Math.max(1, binWidthUs)));
        rateBins = new float[n];
        Arrays.fill(rateBins, 0);
        rateWriteIdx = 0;
        rateCount = 0;
        acfWork = new float[n];
        rateTraceSnapshot = new float[n];
        rateTraceSnapshotLen = 0;
        binningInitialized = false;
        currentBinEventCount = 0;
        measurementValid = false;
        fundamentalFreqHz = Float.NaN;
        filteredRps = Float.NaN;
        filteredRpm = Float.NaN;
        rateLp.reset();
        freqLp.reset();
        lastFreqUpdateTs = Integer.MIN_VALUE;
    }

    private boolean hasRoi() {
        return roiRects != null && !roiRects.isEmpty();
    }

    /** True only when event is inside a defined ROI (unlike base class, empty ROI does not match all). */
    private boolean isInSelectedRoi(BasicEvent e) {
        if (!hasRoi()) {
            return false;
        }
        for (Rectangle r : roiRects) {
            if (r.contains(e.x, e.y)) {
                return true;
            }
        }
        return false;
    }

    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        if (in == null || in.isEmpty()) {
            return in;
        }
        if (!hasRoi()) {
            statusMessage = isSelected()
                    ? "Drag mouse to select ROI for RPM/RPS"
                    : "Open Controls, then drag mouse to select ROI";
            measurementValid = false;
            return in;
        }

        for (BasicEvent e : in) {
            if (e.isSpecial() || e.isFilteredOut()) {
                continue;
            }
            processEventTimestamp(e.timestamp);
            if (isInSelectedRoi(e)) {
                currentBinEventCount++;
            }
        }
        // Advance empty bins to packet end so gaps are represented
        if (binningInitialized) {
            flushBinsUpTo(in.getLastTimestamp());
        }
        maybeUpdateFrequency(in.getLastTimestamp());
        return in;
    }

    private void processEventTimestamp(int ts) {
        if (!binningInitialized) {
            currentBinStartTs = ts;
            currentBinEventCount = 0;
            binningInitialized = true;
            return;
        }
        if (ts < currentBinStartTs) {
            // nonmonotonic / rewind
            resetBinningState(ts);
            return;
        }
        flushBinsUpTo(ts);
    }

    private void resetBinningState(int ts) {
        currentBinStartTs = ts;
        currentBinEventCount = 0;
        rateWriteIdx = 0;
        rateCount = 0;
        Arrays.fill(rateBins, 0);
        lastFreqUpdateTs = Integer.MIN_VALUE;
        measurementValid = false;
        rateLp.reset();
        freqLp.reset();
    }

    private void flushBinsUpTo(int ts) {
        final int bw = Math.max(1, binWidthUs);
        while (ts - currentBinStartTs >= bw) {
            pushRateBin(currentBinEventCount, bw);
            currentBinEventCount = 0;
            currentBinStartTs += bw;
            // prevent infinite loop on huge gaps
            if (ts - currentBinStartTs > bw * rateBins.length) {
                currentBinStartTs = ts - bw;
                break;
            }
        }
    }

    private void pushRateBin(int eventCount, int binUs) {
        float rateHz = 1e6f * eventCount / binUs;
        rateBins[rateWriteIdx] = rateHz;
        rateWriteIdx = (rateWriteIdx + 1) % rateBins.length;
        if (rateCount < rateBins.length) {
            rateCount++;
        }
        instantaneousRoiRateHz = rateHz;
        int midTs = currentBinStartTs + binUs / 2;
        filteredRoiRateHz = rateLp.filter(rateHz, midTs);
    }

    private void maybeUpdateFrequency(int ts) {
        if (rateCount < 16) {
            statusMessage = "Collecting rate history…";
            measurementValid = false;
            return;
        }
        if (lastFreqUpdateTs != Integer.MIN_VALUE) {
            int dtMs = (ts - lastFreqUpdateTs) / 1000;
            if (dtMs >= 0 && dtMs < updateIntervalMs) {
                return;
            }
            if (dtMs < 0) {
                lastFreqUpdateTs = ts;
                return;
            }
        }
        lastFreqUpdateTs = ts;
        estimateFundamentalFrequency(ts);
    }

    /**
     * Autocorrelation of mean-subtracted ROI rate; first significant peak → period.
     */
    private void estimateFundamentalFrequency(int ts) {
        final int n = rateCount;
        final int len = rateBins.length;
        if (n < 16) {
            return;
        }

        // Linearize circular buffer into acfWork temporarily as signal (reuse then overwrite with ACF)
        if (acfWork.length < n) {
            acfWork = new float[len];
        }
        float[] sig = acfWork;
        int start = (rateCount < len) ? 0 : rateWriteIdx;
        float mean = 0;
        for (int i = 0; i < n; i++) {
            float v = rateBins[(start + i) % len];
            sig[i] = v;
            mean += v;
        }
        mean /= n;
        float energy = 0;
        for (int i = 0; i < n; i++) {
            sig[i] -= mean;
            energy += sig[i] * sig[i];
        }
        if (energy < 1e-6f) {
            statusMessage = "No rate modulation in ROI";
            measurementValid = false;
            snapshotRateTrace(n); // still show flat trace
            return;
        }

        final float sampleRateHz = 1e6f / Math.max(1, binWidthUs);
        final float fMax = Math.min(maxFreqHz, 0.45f * sampleRateHz);
        final float fMin = Math.max(0.1f, Math.min(minFreqHz, fMax * 0.5f));
        acfMinLag = Math.max(1, (int) Math.floor(sampleRateHz / fMax));
        acfMaxLag = Math.min(n / 2, (int) Math.ceil(sampleRateHz / fMin));
        if (acfMaxLag <= acfMinLag + 2) {
            statusMessage = "History too short for minFreqHz — increase historyDurationMs";
            measurementValid = false;
            return;
        }

        snapshotRateTrace(n);

        final float zeroLag = energy;
        final float thresh = peakSignificance * zeroLag;
        int bestLag = -1;
        float bestCorr = 0;

        // Single-pass ACF over lag range; first significant local max ≈ fundamental
        float[] acf = new float[acfMaxLag + 2];
        for (int lag = Math.max(0, acfMinLag - 1); lag <= acfMaxLag + 1 && lag < n; lag++) {
            acf[lag] = acfAt(sig, n, lag);
        }
        for (int lag = acfMinLag; lag <= acfMaxLag; lag++) {
            float curr = acf[lag];
            if (curr >= thresh && curr >= acf[lag - 1] && curr >= acf[lag + 1]) {
                bestLag = lag;
                bestCorr = curr;
                break;
            }
        }

        acfBestLag = bestLag;
        lastPeakCorrelation = (zeroLag > 0) ? bestCorr / zeroLag : 0;

        if (bestLag < 1) {
            statusMessage = "No significant period found";
            measurementValid = false;
            return;
        }

        // Parabolic interpolation around discrete peak
        float c0 = acf[bestLag - 1];
        float c1 = acf[bestLag];
        float c2 = acf[bestLag + 1];
        float denom = (c0 - 2 * c1 + c2);
        float delta = 0;
        if (Math.abs(denom) > 1e-12f) {
            delta = 0.5f * (c0 - c2) / denom;
            if (delta > 0.5f) {
                delta = 0.5f;
            } else if (delta < -0.5f) {
                delta = -0.5f;
            }
        }
        float periodBins = bestLag + delta;
        float periodS = periodBins * binWidthUs * 1e-6f;
        if (periodS <= 0) {
            measurementValid = false;
            return;
        }
        float peakFreqHz = 1f / periodS;
        fundamentalFreqHz = peakFreqHz;
        float ppr = Math.max(1e-6f, pulsesPerRevolution);
        float rps = peakFreqHz / ppr;
        filteredRps = freqLp.filter(rps, ts);
        filteredRpm = filteredRps * 60f;
        measurementValid = true;
        statusMessage = null;
    }

    private static float acfAt(float[] sig, int n, int lag) {
        if (lag < 0 || lag >= n) {
            return 0;
        }
        float corr = 0;
        int m = n - lag;
        for (int i = 0; i < m; i++) {
            corr += sig[i] * sig[i + lag];
        }
        return corr * ((float) n / m);
    }

    private void snapshotRateTrace(int n) {
        if (rateTraceSnapshot.length < n) {
            rateTraceSnapshot = new float[rateBins.length];
        }
        int len = rateBins.length;
        int start = (rateCount < len) ? 0 : rateWriteIdx;
        for (int i = 0; i < n; i++) {
            rateTraceSnapshot[i] = rateBins[(start + i) % len];
        }
        rateTraceSnapshotLen = n;
    }

    @Override
    synchronized public void resetFilter() {
        allocateBuffers();
        statusMessage = hasRoi() ? "Collecting rate history…" : "Select ROI with mouse";
    }

    @Override
    public void initFilter() {
        allocateBuffers();
    }

    @Override
    synchronized public void annotate(GLAutoDrawable drawable) {
        super.annotate(drawable); // ROI box + selection rubber-band
        if (!isFilterEnabled()) {
            return;
        }
        GL2 gl = drawable.getGL().getGL2();
        final int sx = chip.getSizeX();
        final int sy = chip.getSizeY();

        if (!hasRoi()) {
            String s = isSelected()
                    ? "RpmMeter: drag mouse to select ROI over a repeating feature"
                    : "RpmMeter: open Controls, then drag mouse to select ROI";
            DrawGL.drawStringDropShadow(fontSize + 2, sx * 0.5f, sy * 0.55f, 0.5f, Color.yellow, s);
            DrawGL.drawStringDropShadow(fontSize, sx * 0.5f, sy * 0.48f, 0.5f, Color.white,
                    String.format("Measures RPS/RPM from event-rate periodicity (up to %s Hz)", engFmt.format(maxFreqHz)));
            return;
        }

        if (showRateTrace && rateTraceSnapshotLen > 4) {
            drawRateTrace(gl, sx, sy);
        }

        MultilineAnnotationTextRenderer.setFontSize(fontSize);
        MultilineAnnotationTextRenderer.setColor(Color.white);
        MultilineAnnotationTextRenderer.resetToYPositionPixels(sy * 0.95f);
        MultilineAnnotationTextRenderer.setXPosition(2);

        StringBuilder sb = new StringBuilder("RpmMeter\n");
        sb.append(String.format("ROI rate: %s eps\n", engFmt.format(filteredRoiRateHz)));
        if (measurementValid && !Float.isNaN(filteredRps)) {
            if (showRps) {
                sb.append(String.format("RPS: %s Hz\n", engFmt.format(filteredRps)));
            }
            if (showRpm) {
                sb.append(String.format("RPM: %s\n", engFmt.format(filteredRpm)));
            }
            sb.append(String.format("peak f: %s Hz (corr %.2f, ppr=%.2g)",
                    engFmt.format(fundamentalFreqHz), lastPeakCorrelation, pulsesPerRevolution));
        } else if (statusMessage != null) {
            sb.append(statusMessage);
        }
        MultilineAnnotationTextRenderer.renderMultilineString(sb.toString());
    }

    private void drawRateTrace(GL2 gl, int sx, int sy) {
        final int n = rateTraceSnapshotLen;
        if (n < 2) {
            return;
        }
        float y0 = sy * 0.08f;
        float yh = sy * 0.18f;
        float maxR = 1e-3f;
        for (int i = 0; i < n; i++) {
            if (rateTraceSnapshot[i] > maxR) {
                maxR = rateTraceSnapshot[i];
            }
        }
        gl.glPushMatrix();
        gl.glColor3f(0.3f, 0.3f, 0.3f);
        gl.glLineWidth(1);
        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex2f(0, y0);
        gl.glVertex2f(sx, y0);
        gl.glVertex2f(sx, y0 + yh);
        gl.glVertex2f(0, y0 + yh);
        gl.glEnd();

        gl.glColor3f(0.2f, 0.9f, 0.4f);
        gl.glLineWidth(1.5f);
        gl.glBegin(GL.GL_LINE_STRIP);
        for (int i = 0; i < n; i++) {
            float x = (i / (float) (n - 1)) * sx;
            float y = y0 + (rateTraceSnapshot[i] / maxR) * yh;
            gl.glVertex2f(x, y);
        }
        gl.glEnd();

        // Mark one estimated period from the end of the trace
        if (measurementValid && acfBestLag > 0 && acfBestLag < n) {
            float x1 = sx;
            float x0 = sx * (1f - acfBestLag / (float) (n - 1));
            gl.glColor3f(1f, 0.85f, 0.2f);
            gl.glLineWidth(2);
            gl.glBegin(GL.GL_LINES);
            gl.glVertex2f(x0, y0);
            gl.glVertex2f(x0, y0 + yh);
            gl.glVertex2f(x1, y0);
            gl.glVertex2f(x1, y0 + yh);
            gl.glEnd();
        }
        gl.glPopMatrix();
        DrawGL.drawString(Math.max(8, fontSize - 2), 2, y0 + yh + 2, 0, Color.lightGray,
                String.format("ROI rate (max %s eps)", engFmt.format(maxR)));
    }

    // --- getters / setters for FilterPanel ---
    public int getBinWidthUs() {
        return binWidthUs;
    }

    public synchronized void setBinWidthUs(int binWidthUs) {
        int old = this.binWidthUs;
        if (binWidthUs < 50) {
            binWidthUs = 50; // ≥20 kHz sample rate upper extreme
        }
        if (binWidthUs > 5000) {
            binWidthUs = 5000;
        }
        this.binWidthUs = binWidthUs;
        putInt("binWidthUs", binWidthUs);
        allocateBuffers();
        getSupport().firePropertyChange("binWidthUs", old, this.binWidthUs);
    }

    public float getHistoryDurationMs() {
        return historyDurationMs;
    }

    public synchronized void setHistoryDurationMs(float historyDurationMs) {
        float old = this.historyDurationMs;
        if (historyDurationMs < 50) {
            historyDurationMs = 50;
        }
        if (historyDurationMs > 5000) {
            historyDurationMs = 5000;
        }
        this.historyDurationMs = historyDurationMs;
        putFloat("historyDurationMs", historyDurationMs);
        allocateBuffers();
        getSupport().firePropertyChange("historyDurationMs", old, this.historyDurationMs);
    }

    public float getMinFreqHz() {
        return minFreqHz;
    }

    public void setMinFreqHz(float minFreqHz) {
        float old = this.minFreqHz;
        if (minFreqHz < 0.1f) {
            minFreqHz = 0.1f;
        }
        this.minFreqHz = minFreqHz;
        putFloat("minFreqHz", minFreqHz);
        getSupport().firePropertyChange("minFreqHz", old, this.minFreqHz);
    }

    public float getMaxFreqHz() {
        return maxFreqHz;
    }

    public void setMaxFreqHz(float maxFreqHz) {
        float old = this.maxFreqHz;
        if (maxFreqHz < 1) {
            maxFreqHz = 1;
        }
        this.maxFreqHz = maxFreqHz;
        putFloat("maxFreqHz", maxFreqHz);
        getSupport().firePropertyChange("maxFreqHz", old, this.maxFreqHz);
    }

    public float getPulsesPerRevolution() {
        return pulsesPerRevolution;
    }

    public void setPulsesPerRevolution(float pulsesPerRevolution) {
        float old = this.pulsesPerRevolution;
        if (pulsesPerRevolution < 0.01f) {
            pulsesPerRevolution = 0.01f;
        }
        this.pulsesPerRevolution = pulsesPerRevolution;
        putFloat("pulsesPerRevolution", pulsesPerRevolution);
        getSupport().firePropertyChange("pulsesPerRevolution", old, this.pulsesPerRevolution);
    }

    public float getPeakSignificance() {
        return peakSignificance;
    }

    public void setPeakSignificance(float peakSignificance) {
        float old = this.peakSignificance;
        if (peakSignificance < 0.01f) {
            peakSignificance = 0.01f;
        }
        if (peakSignificance > 0.95f) {
            peakSignificance = 0.95f;
        }
        this.peakSignificance = peakSignificance;
        putFloat("peakSignificance", peakSignificance);
        getSupport().firePropertyChange("peakSignificance", old, this.peakSignificance);
    }

    public boolean isShowRpm() {
        return showRpm;
    }

    public void setShowRpm(boolean showRpm) {
        this.showRpm = showRpm;
        putBoolean("showRpm", showRpm);
    }

    public boolean isShowRps() {
        return showRps;
    }

    public void setShowRps(boolean showRps) {
        this.showRps = showRps;
        putBoolean("showRps", showRps);
    }

    public boolean isShowRateTrace() {
        return showRateTrace;
    }

    public void setShowRateTrace(boolean showRateTrace) {
        this.showRateTrace = showRateTrace;
        putBoolean("showRateTrace", showRateTrace);
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
        putInt("fontSize", fontSize);
    }

    public float getRateDisplayTauMs() {
        return rateDisplayTauMs;
    }

    public void setRateDisplayTauMs(float rateDisplayTauMs) {
        this.rateDisplayTauMs = rateDisplayTauMs;
        putFloat("rateDisplayTauMs", rateDisplayTauMs);
        rateLp.setTauMs(rateDisplayTauMs);
    }

    public float getFrequencyDisplayTauMs() {
        return frequencyDisplayTauMs;
    }

    public void setFrequencyDisplayTauMs(float frequencyDisplayTauMs) {
        this.frequencyDisplayTauMs = frequencyDisplayTauMs;
        putFloat("frequencyDisplayTauMs", frequencyDisplayTauMs);
        freqLp.setTauMs(frequencyDisplayTauMs);
    }

    public int getUpdateIntervalMs() {
        return updateIntervalMs;
    }

    public void setUpdateIntervalMs(int updateIntervalMs) {
        if (updateIntervalMs < 10) {
            updateIntervalMs = 10;
        }
        this.updateIntervalMs = updateIntervalMs;
        putInt("updateIntervalMs", updateIntervalMs);
    }

    /** Instantaneous RPS after pulsesPerRevolution scaling (filtered). */
    public float getRps() {
        return filteredRps;
    }

    /** Instantaneous RPM after pulsesPerRevolution scaling (filtered). */
    public float getRpm() {
        return filteredRpm;
    }

    /** Detected peak frequency of ROI rate modulation before pulsesPerRevolution (Hz). */
    public float getFundamentalFreqHz() {
        return fundamentalFreqHz;
    }

    public float getFilteredRoiRateHz() {
        return filteredRoiRateHz;
    }

    public boolean isMeasurementValid() {
        return measurementValid;
    }
}
