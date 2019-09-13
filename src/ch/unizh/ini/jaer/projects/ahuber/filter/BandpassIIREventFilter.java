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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.util.logging.Level;
import javax.swing.JFrame;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.PolarityEvent.Polarity;
import net.sf.jaer.eventio.AEFileInputStream;
import net.sf.jaer.eventio.AEInputStream;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.ImageDisplay;
import net.sf.jaer.util.chart.Axis;
import net.sf.jaer.util.chart.Category;
import net.sf.jaer.util.chart.Series;
import net.sf.jaer.util.chart.XYChart;
import scala.actors.threadpool.Arrays;

/**
 * Implementation of IIR bandpass filter for DVS event streams. Produces output
 * image of filtered values that are updated on each DVS event. The IIR filter
 * order can be set for both highpass (corner) and lowpass (cutoff) frequencies.
 *
 * @author Tobi Delbruck <tobi@ini.uzh.ch>, Huber Adrian
 * <huberad@student.ethz.ch>
 */
@Description("IIR bandpass filter for DVS event streams. Produces output\n"
        + " * image of filtered values that are updated on each pixel event.\n"
        + "Produces output\n"
        + " * image of filtered values that are updated on each DVS event. \n"
        + " * The IIR filter order can be set for both highpass (corner) and lowpass (cutoff) frequencies.")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class BandpassIIREventFilter extends EventFilter2DMouseAdaptor implements FrameAnnotater {

    private final static float PI2 = (float) (Math.PI * 2);
    private float tauMsCutoff = getFloat("tauMsCutoff", 1000 / (PI2 * 100)); // 100Hz
    private float tauMsCorner = getFloat("tauMsCorner", 1000 / (PI2 * 1)); // 1Hz
    private float contrast = getFloat("contrast", 0.1f);
    private int cutoffOrder = getInt("cutoffOrder", 1);
    private int cornerOrder = getInt("cornerOrder", 1);
    private int maxDtMsForUpdate = getInt("maxDtMsForUpdate", 100), lastUpdateTimestamp = Integer.MAX_VALUE;

//    private boolean balanceThresholdsAutomatically = getBoolean("balanceThresholdsAutomatically", false);
//    private float thresholdBalanceTauMs = getFloat("thresholdBalanceTauMs", 10000);
    private boolean showFilterOutput = getBoolean("showFilterOutput", true);
    private boolean showFilterPlot = false;
    private int numSamplesToPlot = getInt("numSamplesToPlot", 300), numSamplesCollected = 0;

// rotating bufffer memory to hold past events at each pixel, index is [x][y][events]; index to last event is stored in pointers
    private int[] timestamps = null;
    private float[] input = null, display = null; // state of first and second stage
    private float[][] lowpass = null, highpass = null, pastHighpassInputs = null;
    private boolean[] initialized = null;

    private int sx = 0, sy = 0, npix = 0; // updated on each packet
    private float thrOn = 1, thrOff = -1; // log intensity change thresholds, from biasgen if available

    private ImageDisplay outputDisplay = null;
    private JFrame outputFrame = null;

    private XYChart plotChart = null;
    private JFrame plotFrame = null;
    private Point mousePoint = new Point(-1, -1); // selected point in sensor pixel coordinates in image display of filter state
    private int mouseIdx = -1;
    private Axis timeAxis = null;
    private Axis valueAxis = null;
    protected boolean autoScalePlotValue = getBoolean("autoScalePlotValue", true);
    protected float minPlotAxisValue = getFloat("minPlotAxisValue", -2);
    protected float maxPlotAxisValue = getFloat("maxPlotAxisValue", -2);
    private Axis[] axes = null;
    private Series filterOutputSeries = new Series(2), inputSeries = new Series(2);
    private int minPlotTime = Integer.MAX_VALUE, maxPlotTime = Integer.MIN_VALUE;
    private float minPlotValue = Float.POSITIVE_INFINITY, maxPlotValue = Float.NEGATIVE_INFINITY;
    private Category filterOutputCategory = null, inputCategory = null;
    private boolean addedViewerPropertyChangeListener;

    public BandpassIIREventFilter(AEChip chip) {
        super(chip);
//        setPropertyTooltip("tauMsCorner", "time constant in ms of highpass corner");
//        setPropertyTooltip("tauMsCutoff", "time constant in ms of lowpass cutoff");
        String filterString = "Filter", displayString = "Display", plotString = "Plot";
        setPropertyTooltip(filterString, "3dBCornerFrequencyHz", "corner frequency of highpass filter in Hz");
        setPropertyTooltip(filterString, "3dBCutoffFrequencyHz", "cutoff frequency of lowpass filter in Hz");
        setPropertyTooltip(filterString, "cutoffOrder", "Number for first order filters to cascade, default 1");
        setPropertyTooltip(filterString, "cornerOrder", "Number for first order filters to cascade, default 1");
        setPropertyTooltip(filterString, "maxDtMsForUpdate", "Maximum time in ms between filter updates; if no event arrives in pixel, then filter is updated with current input state and last packet event timestamp");

        setPropertyTooltip(displayString, "contrast", "contrast of each event, reduce for more gray scale. Events are automatically scaled by estimated DVS event thresholds.");
        setPropertyTooltip(displayString, "showFilterOutput", "show ImageDisplay of filter output");

        setPropertyTooltip(plotString, "numSamplesToPlot", "max number of samples of filter output to display in trace");
        setPropertyTooltip(plotString, "showFilterPlot", "display a rolling plot view of the filter output from selected pixel");
        setPropertyTooltip(plotString, "autoScalePlotValue", "automatically scale filter output value plot");
        setPropertyTooltip(plotString, "minPlotAxisValue", "min value of plot vertical axis");
        setPropertyTooltip(plotString, "maxPlotAxisValue", "max value of plot vertical axis");

        filterOutputSeries.setCapacity(numSamplesToPlot);
        inputSeries.setCapacity(numSamplesToPlot);
    }

    private int computeIdx(int x, int y) {
        return x + (sx * y);
    }

    private void updateTimestamp(int idx, int timestamp) {
        timestamps[idx] = timestamp;
    }

    @Override
    synchronized public void resetFilter() {
        checkMemory();
        if (highpass != null) {
            Arrays.fill(initialized, false);
            Arrays.fill(input, 0);
            for (float[] f : lowpass) {
                Arrays.fill(f, 0);
            }
            for (float[] f : highpass) {
                Arrays.fill(f, 0);
            }
            for (float[] f : pastHighpassInputs) {
                Arrays.fill(f, 0);
            }
            Arrays.fill(display, .5f);
            filterOutputSeries.clear();
            minPlotTime = Integer.MAX_VALUE;
            numSamplesCollected = 0;
        }
    }

    @Override
    public void initFilter() {
        checkMemory();
    }

    private void checkMemory() {
        sx = chip.getSizeX();
        sy = chip.getSizeY();
        if (npix != sx * sy || highpass == null || cornerOrder != highpass.length || lowpass == null || cutoffOrder != lowpass.length) {
            npix = sx * sy;
            initialized = new boolean[npix];
            input = new float[npix];
            highpass = new float[cornerOrder][npix];
            pastHighpassInputs = new float[cornerOrder][npix];
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

    @Override
    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
        checkMemory();
        if (!addedViewerPropertyChangeListener) {
            if (chip.getAeViewer() != null) {
                chip.getAeViewer().getSupport().addPropertyChangeListener(this); // AEViewer refires these events for convenience
                addedViewerPropertyChangeListener = true;
            }
        }
        for (final Object o : in) {
            if (!(o instanceof PolarityEvent)) {
                throw new RuntimeException("must be PolarityEvent to process");
            }
            final PolarityEvent e = (PolarityEvent) o;
            filterEvent(e);
        }

        updateNonActiveFilters(in);

        updateDisplay();
        return in;
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
        final int idx = computeIdx(ev.x, ev.y);
        Polarity pol = ev.getPolarity();
        int timestamp = ev.timestamp;
        updateFilter(idx, pol, timestamp);
        if (ev.x == mousePoint.x && ev.y == mousePoint.y) {
            addPlotPoint(timestamps[idx], highpass[cornerOrder - 1][idx]);

        }
        return;
    }

    private void updateFilter(int idx, Polarity pol, int timestamp) {
        input[idx] += (pol == Polarity.On ? thrOn : thrOff); // reconstructed input, using event polarity and estimated logE threshold
        final int lastTs = timestamps[idx];
        final int dtUs = timestamp - lastTs;
        if (!initialized[idx]) {
            initialized[idx] = true;
            updateTimestamp(idx, timestamp);
            initializeLowpass(idx);
            initializeHighpass(idx);
            return;
        }
        if (dtUs < 0) {
            log.warning(String.format("resetting filter on nonmonotonic timestamp dtUs=%d for timestamp %d", dtUs, timestamp));

            resetFilter();
            return;
        }

        updateLowpass(dtUs, idx);
        updateHighpass(dtUs, idx);
        updateTimestamp(idx, timestamp);
    }

    private void updateHighpass(int dtUs, int idx) {
        // compute chain of highpass to filter out low frequencies
        float epsCorner = (0.001f * dtUs) / tauMsCorner; // dt as fraction of lowpass (cutoff) tau
        if (epsCorner > 1) {
            epsCorner = 1; // also too big, clip
        }

        final float eps1 = 1 - epsCorner;
        for (int i = 0; i < cornerOrder; i++) {
            final float in = i == 0 ? lowpass[cutoffOrder - 1][idx] : highpass[i - 1][idx];
            highpass[i][idx] = (eps1) * highpass[i][idx] + (in - pastHighpassInputs[i][idx]);
            pastHighpassInputs[i][idx] = in;
        }
    }

    private void updateLowpass(int dtUs, int idx) {
        // compute chain of lowpass to cutoff high frequencies
        float epsCutoff = (0.001f * dtUs) / tauMsCutoff; // dt as fraction of lowpass (cutoff) tau
        if (epsCutoff > 1) {
            epsCutoff = 1; // step too big, clip it
        }
        final float eps1 = 1 - epsCutoff;
        for (int i = 0; i < cutoffOrder; i++) {
            final float in = i == 0 ? input[idx] : lowpass[i - 1][idx];
            lowpass[i][idx] = (eps1) * lowpass[i][idx] + epsCutoff * in;
        }
    }

    private void initializeLowpass(int idx) {
        for (int i = 0; i < cutoffOrder; i++) {
            lowpass[i][idx] = input[idx];
        }
    }

    private void initializeHighpass(int idx) {
        for (int i = 0; i < cornerOrder; i++) {
            pastHighpassInputs[i][idx] = i == 0 ? input[idx] : 0;
        }
    }

    private void updateDisplay() {
        if (!showFilterOutput) {
            return;
        }
        final int fidx = cornerOrder - 1;
        for (int idx = 0; idx < npix; idx++) {
            display[idx] = highpass[fidx][idx] + 0.5f;
        }
    }

    // filter output to update all values even if they never got an event
    private void updateNonActiveFilters(EventPacket<? extends BasicEvent> in) {
        if (in.isEmpty()) {
            return; // we need a timestamp, at least one
        }
        final int lastTs = in.getLastTimestamp();
        final int maxDtUs = maxDtMsForUpdate * 1000;
        final int deltaTimeSinceUpdate = lastTs - lastUpdateTimestamp;
        if (deltaTimeSinceUpdate < 0 || deltaTimeSinceUpdate > maxDtUs) {
            lastUpdateTimestamp = lastTs;  // prevent excessive CPU updating a bunch of idle pixels too often
            for (int idx = 0; idx < npix; idx++) {

                int dtUs = lastTs - timestamps[idx];
                if (dtUs < 0 || dtUs > maxDtUs) {
                    updateLowpass(dtUs, idx);
                    updateHighpass(dtUs, idx);
                    updateTimestamp(idx, lastTs);
                    if (idx == mouseIdx) {
                        addPlotPoint(timestamps[idx], highpass[cornerOrder - 1][idx]);
                    }
                }
            }
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
        if (!showFilterOutput) {
            return;
        }
        // if the filter output exists, show it in separate ImageDisplay
        if (display != null) {
            if (outputDisplay == null) {
                outputDisplay = ImageDisplay.createOpenGLCanvas();
                outputDisplay.setImageSize(chip.getSizeX(), chip.getSizeY());
                outputDisplay.setGrayValue(0.5f);
                new ImageKeyMouseHandler(outputDisplay, mousePoint, Thread.currentThread());
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

        if (mousePoint != null && showFilterPlot) {
            if (plotFrame == null) {
                plotChart = new XYChart("filter output");
                plotFrame = new JFrame("BandpassIIREventFilter trace");
                plotFrame.setPreferredSize(new Dimension(sx, sy));
                plotFrame.getContentPane().add(plotChart, BorderLayout.CENTER);
                plotFrame.pack();
                plotFrame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(final WindowEvent e) {
                        setShowFilterPlot(false);
                    }

                });

                plotChart.setInsets(new Insets(10, 10, 10, 10)); // top left bottom right
                plotChart.setBackground(Color.WHITE);
                plotChart.setGridEnabled(true);
                timeAxis = new Axis();
                timeAxis.setTitle("time");
                timeAxis.setUnit("us");
                valueAxis = new Axis(0, 1);
                valueAxis.setTitle("out");
                valueAxis.setUnit("log");
                axes = new Axis[]{timeAxis, valueAxis};
                filterOutputCategory = new Category(filterOutputSeries, axes);
                filterOutputCategory.setColor(new float[]{0.0f, 0.0f, 0.0f});
                //        series.setLineWidth(4);
                plotChart.addCategory(filterOutputCategory);
            }
            timeAxis.setMinimum(minPlotTime);
            timeAxis.setMaximum(maxPlotTime);
            if (autoScalePlotValue) {
                valueAxis.setMinimum(minPlotValue);
                valueAxis.setMaximum(maxPlotValue);
            } else {
                valueAxis.setMinimum(minPlotAxisValue);
                valueAxis.setMaximum(maxPlotAxisValue);
            }
            plotChart.repaint();
        }
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
        boolean old = this.showFilterOutput;
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

    /**
     * @return the maxDtMsForUpdate
     */
    public int getMaxDtMsForUpdate() {
        return maxDtMsForUpdate;
    }

    /**
     * @param maxDtMsForUpdate the maxDtMsForUpdate to set
     */
    public void setMaxDtMsForUpdate(int maxDtMsForUpdate) {
        this.maxDtMsForUpdate = maxDtMsForUpdate;
        putInt("maxDtMsForUpdate", maxDtMsForUpdate);
    }

    /**
     * @return the showFilterPlot
     */
    public boolean isShowFilterPlot() {
        return showFilterPlot;
    }

    /**
     * @param showFilterPlot the showFilterPlot to set
     */
    public void setShowFilterPlot(boolean showFilterPlot) {
        boolean old = this.showFilterPlot;
        this.showFilterPlot = showFilterPlot;
        if (showFilterPlot && plotFrame != null && !plotFrame.isVisible()) {
            plotFrame.setVisible(true);
        }
        getSupport().firePropertyChange("showFilterPlot", old, showFilterPlot);
    }

    /**
     * @return the numSamplesToPlot
     */
    public int getNumSamplesToPlot() {
        return numSamplesToPlot;
    }

    /**
     * @param numSamplesToPlot the numSamplesToPlot to set
     */
    public void setNumSamplesToPlot(int numSamplesToPlot) {
        this.numSamplesToPlot = numSamplesToPlot;
        putInt("numSamplesToPlot", numSamplesToPlot);
        filterOutputSeries.setCapacity(numSamplesToPlot);
    }

    private void addPlotPoint(int timestamp, float value) {
        if (!showFilterPlot) {
            return;
        }
        if (numSamplesCollected > numSamplesToPlot) {
            filterOutputSeries.clear();
            minPlotTime = Integer.MAX_VALUE;
            maxPlotTime = Integer.MIN_VALUE;
            minPlotValue = Float.POSITIVE_INFINITY;
            maxPlotValue = Float.NEGATIVE_INFINITY;
            numSamplesCollected = 0;
        }
        filterOutputSeries.add(timestamp, value);
        numSamplesCollected++;
        if (value < minPlotValue) {
            minPlotValue = value;
        } else if (value > maxPlotValue) {
            maxPlotValue = value;
        }
        if (timestamp < minPlotTime) {
            minPlotTime = timestamp;
        } else if (timestamp > maxPlotTime) {
            maxPlotTime = timestamp;
        }
    }

    /**
     * @return the autoScalePlotValue
     */
    public boolean isAutoScalePlotValue() {
        return autoScalePlotValue;
    }

    /**
     * @param autoScalePlotValue the autoScalePlotValue to set
     */
    public void setAutoScalePlotValue(boolean autoScalePlotValue) {
        this.autoScalePlotValue = autoScalePlotValue;
        putBoolean("autoScalePlotValue", autoScalePlotValue);
    }

    /**
     * @return the minPlotAxisValue
     */
    public float getMinPlotAxisValue() {
        return minPlotAxisValue;
    }

    /**
     * @param minPlotAxisValue the minPlotAxisValue to set
     */
    public void setMinPlotAxisValue(float minPlotAxisValue) {
        this.minPlotAxisValue = minPlotAxisValue;
        putFloat("minPlotAxisValue", minPlotAxisValue);
    }

    /**
     * @return the maxPlotAxisValue
     */
    public float getMaxPlotAxisValue() {
        return maxPlotAxisValue;
    }

    /**
     * @param maxPlotAxisValue the maxPlotAxisValue to set
     */
    public void setMaxPlotAxisValue(float maxPlotAxisValue) {
        this.maxPlotAxisValue = maxPlotAxisValue;
        putFloat("maxPlotAxisValue", maxPlotAxisValue);

    }

    public class ImageKeyMouseHandler {

        final ImageDisplay disp;
        final Point mousePoint;
        Thread thread;

        public ImageKeyMouseHandler(ImageDisplay d, Point mp, Thread t) {
            this.disp = d;
            this.mousePoint = mp;
            this.thread = t;
            this.disp.addKeyListener(new KeyAdapter() { // add some key listeners to the ImageDisplay

                @Override
                public void keyReleased(KeyEvent e) {
                    int k = e.getKeyCode();
                    if ((k == KeyEvent.VK_ESCAPE) || (k == KeyEvent.VK_X)) {
                    }
                }
            });

            this.disp.addMouseListener(
                    new MouseAdapter() {

                @Override
                public void mouseClicked(MouseEvent e
                ) {
                    super.mouseClicked(e);
                    Point2D.Float p = disp.getMouseImagePosition(e); // save the mouse point in image coordinates
                    mousePoint.x = (int) p.x;
                    mousePoint.y = (int) p.y;
                    mouseIdx = computeIdx(mousePoint.x, mousePoint.y);
                    log.info(mousePoint.toString());
                    filterOutputSeries.clear();
                    minPlotTime = Integer.MAX_VALUE;
                    maxPlotTime = Integer.MIN_VALUE;
                    minPlotValue = Float.POSITIVE_INFINITY;
                    maxPlotValue = Float.NEGATIVE_INFINITY;
                    showFilterPlot = true;
                }
            }
            );

        }

    }

    /**
     * Handle rewind events
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        switch (evt.getPropertyName()) {
            case AEInputStream.EVENT_REWOUND:
            case AEInputStream.EVENT_WRAPPED_TIME:
                try {
                    resetFilter();
                } catch (Exception e) {
                    log.log(Level.SEVERE, "For EventFilter2D " + this.getClass() + " caught exception in initFilter(): " + e.toString(), e);
                }
                break;
            default:
        }

    }

}
