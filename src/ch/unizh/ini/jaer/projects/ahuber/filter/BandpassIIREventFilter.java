/*
 * Copyright (C) 2019 Tobi.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package ch.unizh.ini.jaer.projects.ahuber.filter;

import com.jogamp.opengl.GLAutoDrawable;
import eu.seebetter.ini.chips.davis.DavisConfig;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.PolarityEvent.Polarity;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.ImageDisplay;
import scala.actors.threadpool.Arrays;

/**
 * Implementation of IIR bandpass filter for DVS event streams. Produces output
 * image of filtered values that are updated on each DVS event. 
 * The IIR filter order can be set for both highpass (corner) and lowpass (cutoff) frequencies.
 *
 * @author Tobi Delbruck <tobi@ini.uzh.ch>, Huber Adrian
 * <huberad@student.ethz.ch>
 */
@Description("IIR bandpass filter for DVS event streams. Produces output\n"
        + " * image of filtered values that are updated on each pixel event.\n"
        + "Produces output\n" +
" * image of filtered values that are updated on each DVS event. \n" +
" * The IIR filter order can be set for both highpass (corner) and lowpass (cutoff) frequencies.")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class BandpassIIREventFilter extends EventFilter2DMouseAdaptor implements FrameAnnotater {

    private final static float PI2 = (float) (Math.PI * 2);
    private float tauMsCutoff = getFloat("tauMsCutoff", 1000 / (PI2 * 100)); // 100Hz
    private float tauMsCorner = getFloat("tauMsCorner", 1000 / (PI2 * 1)); // 1Hz
    private boolean showFilterOutput = getBoolean("showFilterOutput", true);
    private float contrast = getFloat("contrast", 0.1f);
    private int cutoffOrder = getInt("cutoffOrder", 1);
    private int cornerOrder = getInt("cornerOrder", 1);

    private boolean balanceThresholdsAutomatically = getBoolean("balanceThresholdsAutomatically", false);
    private float thresholdBalanceTauMs = getFloat("thresholdBalanceTauMs", 10000);

    // rotating bufffer memory to hold past events at each pixel, index is [x][y][events]; index to last event is stored in pointers
    private int[] timestamps = null;
    private float[] input = null, display = null; // state of first and second stage
    private float[][] lowpass = null, highpass = null;
    private boolean[] initialized = null;

    private int sx = 0, sy = 0, npix; // updated on each packet
    private float thrOn = 1, thrOff = -1; // log intensity change thresholds, from biasgen if available

    private ImageDisplay outputDisplay = null;
    private JFrame outputFrame = null;

    public BandpassIIREventFilter(AEChip chip) {
        super(chip);
//        setPropertyTooltip("tauMsCorner", "time constant in ms of highpass corner");
//        setPropertyTooltip("tauMsCutoff", "time constant in ms of lowpass cutoff");
        setPropertyTooltip("3dBCornerFrequencyHz", "corner frequency of highpass filter in Hz");
        setPropertyTooltip("3dBCutoffFrequencyHz", "cutoff frequency of lowpass filter in Hz");
        setPropertyTooltip("showFilterOutput", "show ImageDisplay of filter output");
        setPropertyTooltip("contrast", "contrast of each event, reduce for more gray scale. Events are automatically scaled by estimated DVS event thresholds.");
        setPropertyTooltip("cutoffOrder", "Number for first order filters to cascade, default 1");
        setPropertyTooltip("cornerOrder", "Number for first order filters to cascade, default 1");
    }

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        checkMemory();
        for (Object o : in) {
            if (!(o instanceof PolarityEvent)) {
                throw new RuntimeException("must be PolarityEvent to process");
            }
            PolarityEvent e = (PolarityEvent) o;
            filterEvent(e);
        }
        filterOutput(in);
        return in;
    }

    private int computeIdx(int x, int y) {
        return x + (sx * y);
    }

    @Override
    synchronized public void resetFilter() {
        if (highpass != null) {
            Arrays.fill(initialized, false);
            Arrays.fill(input, 0);
            for (float[] f : lowpass) {
                Arrays.fill(f, 0);
            }
            for (float[] f : highpass) {
                Arrays.fill(f, 0);
            }
            Arrays.fill(display, .5f);
        }
    }

    @Override
    public void initFilter() {
        checkMemory();
    }

    private void checkMemory() {
        sx = chip.getSizeX();
        sy = chip.getSizeY();
        npix = sx * sy;
        if (highpass == null || highpass.length != npix) {
            initialized = new boolean[npix];
            input = new float[npix];
            highpass = new float[cornerOrder][npix];
            lowpass = new float[cutoffOrder][npix];
            display = new float[npix];
            timestamps = new int[npix];
        }
        if (chip.getBiasgen() != null && chip.getBiasgen() instanceof DavisConfig) {
            DavisConfig config = (DavisConfig) chip.getBiasgen();
            thrOn = config.getOnThresholdLogE() * contrast;
            thrOff = config.getOffThresholdLogE() * contrast; // will be hegative
        }
    }

    /**
     * Filters the incoming signal according to /br      <code>
     * float ret= hpFilter.filter(lpFilter.filter(val,time),time);
     * </code>
     *
     * @param val the incoming sample
     * @param time the time of the sample in us
     * @return the filter output value
     */
    public void filterEvent(PolarityEvent ev) {
        int idx = computeIdx(ev.x, ev.y);
        Polarity pol = ev.getPolarity();
        int timestamp = ev.timestamp;
        updateFilter(idx, pol, timestamp);
        return;
    }

    private void updateFilter(int idx, Polarity pol, int timestamp) {
        input[idx] += (pol == Polarity.On ? thrOn : thrOff); // reconstructed input, using event polarity and estimated logE threshold
        int lastTs = timestamps[idx];
        int dtUs = timestamp - lastTs;
        timestamps[idx] = timestamp;
        if (!initialized[idx]) {
            initialized[idx] = true;
            return;
        }
        if (dtUs < 0) {
//            log.warning(String.format("ignoring nonmonotonic timestamp dtUs=%d for event %s", dtUs, ev.toString()));
            return;
        }

        updateLowpass(dtUs, idx);
        updateHighpass(dtUs, idx);
    }

    private void updateHighpass(int dtUs, int idx) {
        // compute chain of highpass to filter out low frequencies
        float epsCorner = (0.001f * dtUs) / tauMsCorner; // dt as fraction of lowpass (cutoff) tau
        if (epsCorner > 1) {
            epsCorner = 1; // also too big, clip
        }
        for (int i = 0; i < cornerOrder; i++) {
            float in = i == 0 ? lowpass[cutoffOrder - 1][idx] : highpass[i - 1][idx];
            highpass[i][idx] = (1 - epsCorner) * highpass[i][idx] + (in - highpass[i][idx]);
        }
    }

    private void updateLowpass(int dtUs, int idx) {
        // compute chain of lowpass to cutoff high frequencies
        float epsCutoff = (0.001f * dtUs) / tauMsCutoff; // dt as fraction of lowpass (cutoff) tau
        if (epsCutoff > 1) {
            epsCutoff = 1; // step too big, clip it
        }
        for (int i = 0; i < cutoffOrder; i++) {
            float in = i == 0 ? input[idx] : lowpass[i - 1][idx];
            lowpass[i][idx] = (1 - epsCutoff) *  lowpass[i][idx] + epsCutoff * in;
        }
    }

    // filter output to update all values even if they never got an event
    private void filterOutput(EventPacket<? extends BasicEvent> in) {
        if (in.isEmpty()) {
            return;
        }
        int lastTs = in.getLastTimestamp();
        for (int idx = 0; idx < npix; idx++) {

            int dtUs = lastTs - timestamps[idx];
            updateHighpass(dtUs, idx);
            display[idx] = highpass[cornerOrder - 1][idx] + 0.5f;
        }
    }

    /**
     * Set the highpass corner frequency
     *
     * @param hz the frequency in Hz
     */
    public void set3dBCornerFrequencyHz(float hz) {
        setTauMsCorner(1000 / (PI2 * hz));

    }

    /**
     * Set the lowpass (rolloff) cutoff frequency
     *
     * @param hz the frequency in Hz
     */
    public void set3dBCutoffFrequencyHz(float hz) {
        setTauMsCutoff(1000 / (PI2 * hz));
    }

    public float get3dBCornerFrequencyHz() {
        float freq = 1000 / (PI2 * getTauMsCorner());
        return freq;
    }

    public float get3dBCutoffFrequencyHz() {
        float freq = 1000 / (PI2 * getTauMsCutoff());
        return freq;
    }

    /**
     * @return the tauMsCutoff
     */
    private float getTauMsCutoff() {
        return tauMsCutoff;
    }

    /**
     * @param tauMsCutoff the tauMsCutoff to set
     */
    private void setTauMsCutoff(float tauMsCutoff) {
        this.tauMsCutoff = tauMsCutoff;
        putFloat("tauMsCutoff", tauMsCutoff);
    }

    /**
     * @return the tauMsCorner
     */
    private float getTauMsCorner() {
        return tauMsCorner;
    }

    /**
     * @param tauMsCorner the tauMsCorner to set
     */
    private void setTauMsCorner(float tauMsCorner) {
        this.tauMsCorner = tauMsCorner;
        putFloat("tauMsCorner", tauMsCorner);
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if(!showFilterOutput) return;
        // if the filter output exists, show it in separate ImageDisplay
        if (highpass != null) {
            if (outputDisplay == null) {
                outputDisplay = ImageDisplay.createOpenGLCanvas();
                outputDisplay.setImageSize(chip.getSizeX(), chip.getSizeY());
                outputDisplay.setGrayValue(0.5f);
                outputFrame = new JFrame("BandpassEventFilter");
                outputFrame.setPreferredSize(new Dimension(sx, sy));
                outputFrame.getContentPane().add(outputDisplay, BorderLayout.CENTER);
                outputFrame.pack();
                outputFrame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(final WindowEvent e) {
                        setShowFilterOutput(false);
                    }

                });
            }
        }
        if (showFilterOutput && !outputFrame.isVisible()) {
            outputFrame.setVisible(true);
        }
        outputDisplay.setImageSize(chip.getSizeX(), chip.getSizeY());
        outputDisplay.setPixmapFromGrayArray(display);
        outputDisplay.repaint();
    }

    /**
     * @return the showFilterOutput
     */
    public boolean isShowFilterOutput() {
        return showFilterOutput;
    }

    /**
     * @param showFilterOutput the showFilterOutput to set
     */
    public void setShowFilterOutput(boolean showFilterOutput) {
        boolean old=this.showFilterOutput;
        this.showFilterOutput = showFilterOutput;
        if (showFilterOutput && outputFrame != null && !outputFrame.isVisible()) {
            outputFrame.setVisible(true);
        }
        getSupport().firePropertyChange("showFilterOutput", old, showFilterOutput);
    }

    /**
     * @return the contrast
     */
    public float getContrast() {
        return contrast;
    }

    /**
     * @param contrast the contrast to set
     */
    public void setContrast(float contrast) {
        this.contrast = contrast;
        putFloat("contrast", contrast);
    }

    /**
     * @return the cutoffOrder
     */
    public int getCutoffOrder() {
        return cutoffOrder;
    }

    /**
     * @param cutoffOrder the cutoffOrder to set
     */
    synchronized public void setCutoffOrder(int cutoffOrder) {
        if (cutoffOrder < 1) {
            cutoffOrder = 1;
        }
        if (cutoffOrder > 6) {
            cutoffOrder = 6;
        }
        this.cutoffOrder = cutoffOrder;
        putInt("cutoffOrder", cutoffOrder);
    }

    /**
     * @return the cornerOrder
     */
    public int getCornerOrder() {
        return cornerOrder;
    }

    /**
     * @param cornerOrder the cornerOrder to set
     */
    synchronized public void setCornerOrder(int cornerOrder) {
        if (cornerOrder < 1) {
            cornerOrder = 1;
        }
        if (cornerOrder > 6) {
            cornerOrder = 6;
        }
        this.cornerOrder = cornerOrder;
        putInt("cornerOrder", cornerOrder);
    }

}
