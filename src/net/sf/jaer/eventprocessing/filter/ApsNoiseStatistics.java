/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventprocessing.filter;

import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Observable;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.fixedfunc.GLMatrixFunc;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2DMouseAdaptor;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.DisplayMethod2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.EngineeringFormat;
import ch.unizh.ini.jaer.projects.davis.frames.ApsFrameExtractor;
import com.jogamp.opengl.GLException;

import com.jogamp.opengl.util.awt.TextRenderer;
import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.DavisConfig;
import eu.seebetter.ini.chips.davis.DavisVideoContrastController;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.graphics.MultilineAnnotationTextRenderer;

/**
 * Displays noise statistics for APS frames from DAVIS sensors.
 *
 * @author tobi
 */
@Description("Collects and displays APS noise statistics for a selected range of pixels, including PTC (photon transfer characteristics) and temporal noise caused by kTC, 1/f or other noise. ")
@DevelopmentStatus(DevelopmentStatus.Status.Stable)
public class ApsNoiseStatistics extends EventFilter2DMouseAdaptor implements FrameAnnotater, PropertyChangeListener {

    ApsFrameExtractor frameExtractor;
    public boolean temporalNoiseEnabled = getBoolean("temporalNoiseEnabled", true);
    public boolean spatialHistogramEnabled = getBoolean("spatialHistogramEnabled", true);
    public boolean scaleHistogramsIncludingOverflow = getBoolean("scaleHistogramsIncludingOverflow", true);
    protected boolean resetOnBiasChange = getBoolean("resetOnBiasChange", true);
    public int histNumBins = getInt("histNumBins", 300);
    int startx, starty, endx, endy; // mouse selection points
    private Point startPoint = null, endPoint = null, clickedPoint = null;
    protected Rectangle selectionRectangle = null;
    private static float lineWidth = 1f;
    private GLCanvas glCanvas;
    private ChipCanvas canvas;
    volatile boolean selecting = false;
    private Point currentMousePoint = null;
    private int[] currentAddress = null;
    EngineeringFormat engFmt = new EngineeringFormat();
    final private static float[] SELECT_COLOR = {.8f, 0, 0, .5f};
    private TextRenderer renderer = null;
    PixelStatistics stats = new PixelStatistics();
    final private static float[] TEMPORAL_HIST_COLOR = {0, 0, .8f, .3f},
            SPATIAL_HIST_COLOR = {.6f, .4f, .2f, .6f},
            HIST_OVERFLOW_COLOR = {.8f, .3f, .2f, .6f};
    final float textScale = .4f;
    private boolean resetCalled = true;
    private float adcVref = getFloat("vreadcVreff", 1.5f);
    private int adcResolutionCounts = getInt("adcResolutionCounts", 1023);
    private boolean useZeroOriginForTemporalNoise = getBoolean("useZeroOriginForTemporalNoise", false);
    private float lastMeasuredExposureMs = Float.NaN, lastExposureDelayMs = Float.NaN;
    protected float discardPixelsWithVarianceLargerThan = getFloat("discardPixelsWithVarianceLargerThan", Float.NaN);
    protected boolean drawPtcLineWithMouse = false, drawingPtcLine = false;

    public ApsNoiseStatistics(AEChip chip) {
        super(chip);
        if ((chip.getCanvas() != null) && (chip.getCanvas().getCanvas() != null)) {
            canvas = chip.getCanvas();
            glCanvas = (GLCanvas) chip.getCanvas().getCanvas();
        }
        currentAddress = new int[chip.getNumCellTypes()];
        Arrays.fill(currentAddress, -1);
        frameExtractor = new ApsFrameExtractor(chip);
        FilterChain chain = new FilterChain(chip);
        chain.add(frameExtractor);
        setEnclosedFilterChain(chain);
        setPropertyTooltip("scaleHistogramsIncludingOverflow", "Scales histograms to include overflows for ISIs that are outside of range");
        setPropertyTooltip("histNumBins", "number of bins in the spatial (FPN) histogram");
        setPropertyTooltip("spatialHistogramEnabled", "shows the spatial (FPN) histogram for mouse-selected region");
        setPropertyTooltip("temporalNoiseEnabled", "<html>shows the temporal noise (AC RMS) of pixels in mouse-selected region. <br>The AC RMS is computed for each pixel separately and the grand average AC RMS is displayed.<\br>Use left-mouse to drag select a range of pixels.<\br>Use right-mouse to drag a line along temporal noise measurements to estimate conversion gain.");
        setPropertyTooltip("resetOnBiasChange", "Resets filter on any PropertyChangeEvent from the chip's configuration");
        setPropertyTooltip("adcVref", "Input voltage range of ADC; on DAVIS240 the range is 1.5V with AdcLow=.21, AdcHigh=1.81");
        setPropertyTooltip("adcResolutionCounts", "Resolution of ADC in DN (digital number) counts");
        setPropertyTooltip("useZeroOriginForTemporalNoise", "Sets origin for temporal noise plot to 0,0 to help see structure more easily");
        setPropertyTooltip("discardPixelsWithVarianceLargerThan", "For temporal noise PTC, discards pixels with variance larger than this threshold, to make PTC work more reliablly");
        setPropertyTooltip("drawPtcLineWithMouse", "<html>If selected, "
                + "mouse left click and drag draws the temporal noise PTC line which you can use to estimate the conversion gain"
                + "<p>If not selected, mouse drag draws ROI box");

    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if (canvas.getDisplayMethod() instanceof DisplayMethod2D) {
            displayStats(drawable);
        }
    }

    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        frameExtractor.filterPacket(in);
        if (frameExtractor.hasNewFrameAvailable()) {
            if (resetCalled) {
//                frameExtractor.resetFilter(); // TODO we cannot call this before getting frame, because then frame will be set to zero in the frameExtractor and we'll get zeros
                stats.reset();
                resetCalled = false;
            }
            float[] frame = frameExtractor.getRawFrame();
            stats.updateStatistics(frame);
        }
        return in;
    }

    public void displayStats(GLAutoDrawable drawable) {
        synchronized (glCanvas) { // sync on mouse listeners that call opengl methods
            if ((drawable == null) || (getSelectionRectangle() == null) || (chip.getCanvas() == null)) {
                return;
            }
            canvas = chip.getCanvas();
            glCanvas = (GLCanvas) canvas.getCanvas();
            int sx = chip.getSizeX(), sy = chip.getSizeY();
            Rectangle chipRect = new Rectangle(sx, sy);
            GL2 gl = drawable.getGL().getGL2();
            try {
                gl.glEnable(GL.GL_BLEND);
                gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
                gl.glBlendEquation(GL.GL_FUNC_ADD);
            } catch (GLException e) {
                e.printStackTrace();
            }
            if (!chipRect.intersects(selectionRectangle)) {
                return;
            }
            drawSelectionRectangle(gl, getSelectionRectangle(), SELECT_COLOR);
            stats.draw(gl);
        }
    }

    /**
     * gets the selected region of pixels from the endpoint mouse event that is
     * passed in.
     *
     * @param e the endpoint (rectangle corner) of the selectionRectangle
     * rectangle
     */
    private void setSelectionRectangleFromMouseEvent(MouseEvent e) {
        Point p = getMousePoint(e);
        endPoint = p;
        startx = min(startPoint.x, endPoint.x);
        starty = min(startPoint.y, endPoint.y);
        endx = max(startPoint.x, endPoint.x);
        endy = max(startPoint.y, endPoint.y);
        int w = endx - startx;
        int h = endy - starty;
        setSelectionRectangle(new Rectangle(startx, starty, w, h));
    }

    /**
     * tests if address is in selectionRectangle
     *
     * @param e an event
     * @return true if rectangle contains the event (is strictly inside the
     * rectangle)
     */
    private boolean isInSelectionRectangle(BasicEvent e) {
        if (getSelectionRectangle().contains(e.x, e.y)) {
            return true;
        }
        return false;
    }

    /**
     * draws selectionRectangle rectangle on annotation
     *
     * @param gl GL context
     * @param r the rectangle
     * @param c the 3 vector RGB color to draw rectangle
     */
    private void drawSelectionRectangle(GL2 gl, Rectangle r, float[] c) {
        gl.glPushMatrix();
        gl.glColor3fv(c, 0);
        gl.glLineWidth(lineWidth);
        gl.glTranslatef(-.5f, -.5f, 0);
        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex2f(getSelectionRectangle().x, getSelectionRectangle().y);
        gl.glVertex2f(getSelectionRectangle().x + getSelectionRectangle().width, getSelectionRectangle().y);
        gl.glVertex2f(getSelectionRectangle().x + getSelectionRectangle().width, getSelectionRectangle().y + getSelectionRectangle().height);
        gl.glVertex2f(getSelectionRectangle().x, getSelectionRectangle().y + getSelectionRectangle().height);
        gl.glEnd();
        gl.glPopMatrix();

    }

    @Override
    synchronized public void resetFilter() {
        resetCalled = true;  // to handle reset during some point of iteration that gets partial new frame or something strange

    }

    @Override
    public void initFilter() {
        currentAddress = new int[chip.getNumCellTypes()];
        Arrays.fill(currentAddress, -1);
        frameExtractor.resetFilter();
        if (chip.getBiasgen() != null) {
            chip.getBiasgen().getSupport().addPropertyChangeListener(this);
        }
    }

    private int min(int a, int b) {
        return a < b ? a : b;
    }

    private int max(int a, int b) {
        return a > b ? a : b;
    }

    private float min(float a, float b) {
        return a < b ? a : b;
    }

    private float max(float a, float b) {
        return a > b ? a : b;
    }

    private double min(double a, double b) {
        return a < b ? a : b;
    }

    private double max(double a, double b) {
        return a > b ? a : b;
    }

    /**
     * Sets the selection rectangle
     *
     * @param e
     */
    @Override
    public void mouseReleased(MouseEvent e) {
        if (isDontProcessMouse()) {
            return;
        }
        if (startPoint == null) {
            return;
        }
        if ((e.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) == MouseEvent.BUTTON1_DOWN_MASK) {
            setSelectionRectangleFromMouseEvent(e);
        } else if ((e.getModifiersEx() & MouseEvent.BUTTON3_DOWN_MASK) == MouseEvent.BUTTON3_DOWN_MASK) {
            stats.temporalNoise.setTemporalNoiseLineFromMouseEvent(e);
        }
        selecting = false;
    }

    /**
     * Starts the selection rectangle
     *
     * @param e
     */
    @Override
    public void mousePressed(MouseEvent e) {
        Point p = getMousePoint(e);
        startPoint = p;
        if (!drawPtcLineWithMouse) {
            selecting = true;
        } else {
            drawingPtcLine = true;
        }
    }

    /**
     * Sets the currentMousePoint and currentAddress[] array
     *
     * @param e
     */
    @Override
    public void mouseMoved(MouseEvent e) {
        if (isDontProcessMouse()) {
            return;
        }
        currentMousePoint = getMousePoint(e);
        if (drawPtcLineWithMouse) {
            return;
        }
        for (int k = 0; k < chip.getNumCellTypes(); k++) {
            currentAddress[k] = chip.getEventExtractor().getAddressFromCell(currentMousePoint.x, currentMousePoint.y, k);
//            System.out.println(currentMousePoint+" gives currentAddress["+k+"]="+currentAddress[k]);
        }
    }

    @Override
    public void mouseExited(MouseEvent e) {
        if (isDontProcessMouse()) {
            return;
        }
        selecting = false;
        drawingPtcLine = false;
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    /**
     * Sets the selection rectangle
     *
     * @param e
     */
    @Override
    public void mouseDragged(MouseEvent e) {
        if (isDontProcessMouse()) {
            return;
        }
        if (startPoint == null) {
            return;
        }

        if ((e.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) == MouseEvent.BUTTON1_DOWN_MASK) {
            if (!drawPtcLineWithMouse) {
                setSelectionRectangleFromMouseEvent(e);
            } else {
                stats.temporalNoise.setTemporalNoiseLineFromMouseEvent(e);
            }
        }
    }

    /**
     * Sets the clickedPoint field
     *
     * @param e
     */
    @Override
    public void mouseClicked(MouseEvent e
    ) {
        if (isDontProcessMouse()) {
            return;
        }
        Point p = getMousePoint(e);
        clickedPoint = p;
    }

    /**
     * Overridden so that when this EventFilter2D is selected/deselected to be
     * controlled in the FilterPanel the mouse listeners are installed/removed
     *
     * @param yes
     */
    @Override
    public void setSelected(boolean yes
    ) {
        super.setSelected(yes);
        if (glCanvas == null) {
            return;
        }
        if (yes) {
            glCanvas.addMouseListener(this);
            glCanvas.addMouseMotionListener(this);

        } else {
            glCanvas.removeMouseListener(this);
            glCanvas.removeMouseMotionListener(this);
        }
    }

    /**
     * @return the selectionRectangle
     */
    public Rectangle getSelectionRectangle() {
        return selectionRectangle;
    }

    /**
     * @param selectionRectangle the selectionRectangle to set
     */
    synchronized public void setSelectionRectangle(Rectangle selectionRectangle) {
        if ((this.selectionRectangle == null) || !this.selectionRectangle.equals(selectionRectangle)) {
            stats.reset();
        }
        this.selectionRectangle = selectionRectangle;
    }

    private Point getMousePoint(MouseEvent e) {
        synchronized (glCanvas) { // sync here on opengl canvas because getPixelFromMouseEvent calls opengl and we don't want that during rendering
            return canvas.getPixelFromMouseEvent(e);
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (isFilterEnabled() && resetOnBiasChange && evt.getSource() instanceof AEChip
                && evt.getPropertyName() != DavisChip.PROPERTY_FRAME_RATE_HZ
                && evt.getPropertyName() != DavisChip.PROPERTY_MEASURED_EXPOSURE_MS
                && evt.getPropertyName() != DavisVideoContrastController.AGC_VALUES) {
            resetFilter();
            log.info("statistics reset because of event " + evt.getPropertyName());
        }
        if (isFilterEnabled() && evt.getSource() instanceof AEChip
                && evt.getPropertyName() == DavisChip.PROPERTY_MEASURED_EXPOSURE_MS) {
            lastMeasuredExposureMs = (float) evt.getNewValue();
            if (chip instanceof DavisChip) {
                DavisChip davisChip = (DavisChip) chip;
                DavisConfig davisConfig = (DavisConfig) davisChip.getBiasgen();
                lastExposureDelayMs = davisConfig.getExposureDelayMs();
            }
        }
    }

    /**
     * @return the adcVref
     */
    public float getAdcVref() {
        return adcVref;
    }

    /**
     * @param adcVref the adcVref to set
     */
    public void setAdcVref(float adcVref) {
        this.adcVref = adcVref;
        putFloat("adcVref", adcVref);
    }

    /**
     * @return the adcResolutionCounts
     */
    public int getAdcResolutionCounts() {
        return adcResolutionCounts;
    }

    /**
     * @param adcResolutionCounts the adcResolutionCounts to set
     */
    public void setAdcResolutionCounts(int adcResolutionCounts) {
        this.adcResolutionCounts = adcResolutionCounts;
        putInt("adcResolutionCounts", adcResolutionCounts);
    }

    /**
     * @return the useZeroOriginForTemporalNoise
     */
    public boolean isUseZeroOriginForTemporalNoise() {
        return useZeroOriginForTemporalNoise;
    }

    /**
     * @param useZeroOriginForTemporalNoise the useZeroOriginForTemporalNoise to
     * set
     */
    public void setUseZeroOriginForTemporalNoise(boolean useZeroOriginForTemporalNoise) {
        this.useZeroOriginForTemporalNoise = useZeroOriginForTemporalNoise;
        putBoolean("useZeroOriginForTemporalNoise", useZeroOriginForTemporalNoise);
    }

    /**
     * Keeps track of pixel statistics
     */
    class PixelStatistics {

        APSHist apsHist = new APSHist();
        TemporalNoise temporalNoise = new TemporalNoise();
        final float PLOT_OFFSETS = .1f;

        public PixelStatistics() {
        }

        /**
         * draws all statistics
         */
        void draw(GL2 gl) {
            MultilineAnnotationTextRenderer.setColor(Color.BLUE);
            MultilineAnnotationTextRenderer.setScale(((float) chip.getSizeX() / 240) * .25f); // scaled to fit 240 sensor, scale up for larger sensors
            MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * 0.8f);
            engFmt.setPrecision(2);
            MultilineAnnotationTextRenderer.renderMultilineString(String.format("Exposure: set=%ss, measured=%ss", engFmt.format(lastExposureDelayMs * .001f), engFmt.format(lastMeasuredExposureMs * .001f)));
            apsHist.draw(gl);
            temporalNoise.draw(gl);
        }

        void reset() {
            apsHist.reset();
            temporalNoise.reset();
        }

        void updateStatistics(float[] frame) {
            if (selecting || (selectionRectangle == null)) {
                return; // don't bother if we haven't selected a region TODO maybe do entire frame
            }
            // itereate over pixels of selection rectangle to get pixels from frame
            int selx = 0, sely = 0, selIdx = 0;
            for (int x = selectionRectangle.x; x < (selectionRectangle.x + selectionRectangle.width); x++) {
                for (int y = selectionRectangle.y; y < (selectionRectangle.y + selectionRectangle.height); y++) {
                    int idx = frameExtractor.getIndex(x, y);
                    if (idx >= frame.length) {
//                        log.warning(String.format("index out of range: x=%d y=%d, idx=%d frame.length=%d", x, y, idx, frame.length));
                        return;
                    }
                    int sample = (int) (frame[idx]);
                    if (spatialHistogramEnabled) {
                        apsHist.addSample(sample);
                    }
                    if (temporalNoiseEnabled) {
                        temporalNoise.addSample(selIdx, sample);
                    }
                    sely++;
                    selIdx++;
                }
                selx++;
            }
            if (temporalNoiseEnabled) {
                temporalNoise.compute();
            }
        }

        private class TemporalNoise {

            private double[] sums, sum2s, means, vars; // variance computations for pixels, each entry is one pixel cummulation
            private int[] pixelSampleCounts; // how many times each pixel has been sampled (should be same for all pixels)
            int nPixels = 0; // total number of pixels
            float mean = 0, var = 0, rmsAC = 0;
            float meanvar = 0;
            float meanmean = 0;
            float minmean, maxmean, minvar, maxvar;
            float meanrange = maxmean - minmean;
            float varrange = maxvar - minvar;
            private Point2D.Float msp = null, mep = null;
            private Point2D.Float dsp = new Point2D.Float(), dep = new Point2D.Float();

            /**
             * Adds one pixel sample to temporal noise statistics
             *
             * @param idx index of pixel
             * @param sample sample value
             */
            synchronized void addSample(int idx, int sample) {
                if (idx >= sums.length) {
                    return; // TODO some problem with selecting rectangle which gives 1x1 rectangle sometimes
                }
                sums[idx] += sample;
                sum2s[idx] += sample * sample;
                pixelSampleCounts[idx]++;
            }

            /**
             * computes temporal noise stats from collected pixel sample values
             */
            synchronized void compute() {
                if (pixelSampleCounts == null) {
                    return;
                }
                float sumvar = 0, summean = 0;
                minmean = Float.POSITIVE_INFINITY;
                minvar = Float.POSITIVE_INFINITY;
                maxmean = Float.NEGATIVE_INFINITY;
                maxvar = Float.NEGATIVE_INFINITY;
                // computes means and variances for each pixel over all frames
                nPixels = 0;
                for (int i = 0; i < pixelSampleCounts.length; i++) {
                    if (pixelSampleCounts[i] < 2) {
                        continue;
                    }
                    if (vars[i] > discardPixelsWithVarianceLargerThan) {
                        continue;
                    }
                    nPixels++;
                    means[i] = sums[i] / pixelSampleCounts[i];
                    vars[i] = (sum2s[i] / pixelSampleCounts[i]) - (means[i] * means[i]);
//                    if (vars[i] > 1000) {
//                        log.warning("suspiciously high variance");
//                    }
                }
                // compute grand averages
                for (int i = 0; i < pixelSampleCounts.length; i++) {
                    if (pixelSampleCounts[i] < 2) {
                        continue;
                    }
                    if (vars[i] > discardPixelsWithVarianceLargerThan) {
                        continue;
                    }
                    sumvar += vars[i];
                    summean += means[i];
                    minmean = (float) min(means[i], minmean);
                    maxmean = (float) max(means[i], maxmean);
                    minvar = (float) min(vars[i], minvar);
                    maxvar = (float) max(vars[i], maxvar);
                }
                meanvar = sumvar / nPixels;
                meanmean = summean / nPixels;
                rmsAC = (float) Math.sqrt(sumvar / nPixels);
            }

            synchronized void reset() {
                if (selectionRectangle == null) {
                    return;
                }
                nPixels = 0;
                final int length = selectionRectangle.width * selectionRectangle.height;
                if ((sums == null) || (sums.length != length)) {
                    sums = new double[length];
                    sum2s = new double[length];
                    means = new double[length];
                    vars = new double[length];
                    pixelSampleCounts = new int[length];
                } else {
                    Arrays.fill(sums, 0);
                    Arrays.fill(sum2s, 0);
                    Arrays.fill(means, 0);
                    Arrays.fill(vars, 0);
                    Arrays.fill(pixelSampleCounts, 0);
                }
            }

            /**
             * Draws the temporal noise statistics, including global averages
             * and plot of var vs mean
             */
            synchronized void draw(GL2 gl) {
                if (!temporalNoiseEnabled || (selectionRectangle == null) || (means == null) || (vars == null)) {
                    return;
                }
                renderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 24), true, true);
                renderer.setSmoothing(true);
                final float x0 = chip.getSizeX() * PLOT_OFFSETS, y0 = chip.getSizeY() * PLOT_OFFSETS, x1 = chip.getSizeX() * (1 - PLOT_OFFSETS), y1 = chip.getSizeY() * (1 - PLOT_OFFSETS);

                // overall statistics
                float kdn = (float) (meanvar / meanmean);
                float keuV = kdn * adcVref / adcResolutionCounts * 1e6f;
                String s = String.format("Temporal noise: %.1f+/-%.2f var=%.1f COV=%.1f%% var/mean=k=%.2f DN/e, k=%s uV/e N=%d", meanmean, rmsAC, meanvar, (100 * rmsAC) / meanmean, kdn, engFmt.format(keuV), nPixels);
                MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * .6f);
                MultilineAnnotationTextRenderer.renderMultilineString(s);
//
//                renderer.begin3DRendering();
//                renderer.setColor(GLOBAL_HIST_COLOR[0], GLOBAL_HIST_COLOR[1], GLOBAL_HIST_COLOR[2], 1f);
//                renderer.draw3D(s, 1.5f * x0, y1, 0, textScale);
//                renderer.end3DRendering();
                // draw plot
                gl.glLineWidth(lineWidth);
                gl.glColor3fv(TEMPORAL_HIST_COLOR, 0);
                // axes
                gl.glBegin(GL.GL_LINES);
                gl.glVertex2f(x0, y0);
                gl.glVertex2f(x1, y0);
                gl.glVertex2f(x0, y0);
                gl.glVertex2f(x0, y1);
                gl.glEnd();
                // axes labels
                renderer.begin3DRendering();
                renderer.setColor(TEMPORAL_HIST_COLOR[0], TEMPORAL_HIST_COLOR[1], TEMPORAL_HIST_COLOR[2], 1f);
                renderer.draw3D("signal", (x0 + x1) / 2, y0 / 2, 0, textScale);
                renderer.end3DRendering();
                gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
                gl.glPushMatrix();
                gl.glTranslatef(x0 / 2, (y0 + y1) / 2, 0);
//                gl.glRotatef(90,0,0, 0); // TODO doesn't work to rotate axes label
                renderer.begin3DRendering();
                renderer.draw3D("variance", 0, 0, 0, textScale);
                renderer.end3DRendering();
                gl.glPopMatrix();

                if (useZeroOriginForTemporalNoise) {
                    minmean = 0;
                    minvar = 0;
                }
                // axes limits
                renderer.begin3DRendering();
                renderer.setColor(TEMPORAL_HIST_COLOR[0], TEMPORAL_HIST_COLOR[1], TEMPORAL_HIST_COLOR[2], 1f);
                renderer.draw3D(String.format("%.1f", minvar), x0 / 2, y0, 0, textScale);
                renderer.draw3D(String.format("%.1f", maxvar), x0 / 2, y1, 0, textScale);
                renderer.draw3D(String.format("%.1f", minmean), x0, y0 / 2, 0, textScale);
                renderer.draw3D(String.format("%.1f", maxmean), x1, y0 / 2, 0, textScale);
                renderer.end3DRendering();

                // data points
                gl.glColor4fv(TEMPORAL_HIST_COLOR, 0);
                gl.glPointSize(6);
                gl.glBegin(GL.GL_POINTS);

                meanrange = maxmean - minmean;
                varrange = maxvar - minvar;
                for (int i = 0; i < means.length; i++) {
                    final double x = x0 + (((x1 - x0) * (means[i] - minmean)) / meanrange);
                    final double y = y0 + (((y1 - y0) * (vars[i] - minvar)) / varrange);
                    gl.glVertex2d(x, y);
                }

                gl.glEnd();

                // noise line
                if (msp != null && mep != null) {
                    gl.glColor3fv(new float[]{0, .5f, .8f}, 0);
                    gl.glLineWidth(3);
                    gl.glBegin(GL.GL_LINES);
                    if (!selecting) { // compute pixel points from data points
                        setTemporalNoiseLinePointsFromDataPoints(dsp, dep);
                    }
                    gl.glVertex2f(msp.x, msp.y);
                    gl.glVertex2f(mep.x, mep.y);
                    gl.glEnd();
                    // compute line points in mean/var space

                    float dmean = dep.x - dsp.x;
                    float dvar = dep.y - dsp.y;
                    float kdnslope = dvar / dmean;
                    float kuVperElec = 1e6f * kdnslope * adcVref / adcResolutionCounts;
                    renderer.begin3DRendering();
                    renderer.setColor(0, .5f, .8f, 1f);
                    renderer.draw3D(String.format("%s DN/e, %s uV/e", engFmt.format(kdnslope), engFmt.format(kuVperElec)), mep.x, mep.y, 0, textScale);
                    renderer.end3DRendering();
                }
            }

            // mouse has been pressed, set line from mouse point
            private void setTemporalNoiseLineFromMouseEvent(MouseEvent e) {
                Point p = getMousePoint(e);
                endPoint = p;
                startx = min(startPoint.x, endPoint.x);
                starty = min(startPoint.y, endPoint.y);
                endx = max(startPoint.x, endPoint.x);
                endy = max(startPoint.y, endPoint.y);
                setTemporalNoiseLinePointsFromMousePoints(new Point2D.Float(startx, starty), new Point2D.Float(endx, endy));
            }

            // sets the line from the mouse points
            private void setTemporalNoiseLinePointsFromMousePoints(Point2D.Float start, Point2D.Float end) {
                final float x0 = chip.getSizeX() * PLOT_OFFSETS, y0 = chip.getSizeY() * PLOT_OFFSETS, x1 = chip.getSizeX() * (1 - PLOT_OFFSETS), y1 = chip.getSizeY() * (1 - PLOT_OFFSETS);
                msp = start;
                mep = end;
                dsp.x = (float) meanrange * (msp.x - x0) / (x1 - x0);
                dep.x = (float) meanrange * (mep.x - x0) / (x1 - x0);
                dsp.y = (float) varrange * (msp.y - y0) / (y1 - y0);
                dep.y = (float) varrange * (mep.y - y0) / (y1 - y0);
            }

            // sets the line pixel ends from the line data points
            private void setTemporalNoiseLinePointsFromDataPoints(Point2D.Float dsp, Point2D.Float dep) {
                final float x0 = chip.getSizeX() * PLOT_OFFSETS, y0 = chip.getSizeY() * PLOT_OFFSETS, x1 = chip.getSizeX() * (1 - PLOT_OFFSETS), y1 = chip.getSizeY() * (1 - PLOT_OFFSETS);

                msp.x = x0 + (float) (x1 - x0) * (dsp.x - minmean) / (meanrange);
                msp.y = y0 + (float) (y1 - y0) * (dsp.y - minvar) / (varrange);
                mep.x = x0 + (float) (x1 - x0) * (dep.x - minmean) / (meanrange);
                mep.y = y0 + (float) (y1 - y0) * (dep.y - minvar) / (varrange);
            }
        }

        private class APSHist {

            int[] bins = new int[histNumBins];
            int lessCount = 0, moreCount = 0;
            int maxCount = 0;
            boolean virgin = true;
            int sampleMin = 0, sampleMax = 1023;
            float mean = 0, var = 0, std = 0, cov = 0;
            long sum = 0, sum2 = 0;
            int count;

            public APSHist() {
            }

            void addSample(int sample) {
                int bin = getSampleBin(sample);
                if (bin < 0) {
                    lessCount++;
                    if (scaleHistogramsIncludingOverflow && (lessCount > maxCount)) {
                        maxCount = lessCount;
                    }
                } else if (bin >= histNumBins) {
                    moreCount++;
                    if (scaleHistogramsIncludingOverflow && (moreCount > maxCount)) {
                        maxCount = moreCount;
                    }
                } else {
                    int v = ++bins[bin];
                    if (v > maxCount) {
                        maxCount = v;
                    }
                }
                count++;
                sum += sample;
                sum2 += sample * sample;
                if (count > 2) {
                    mean = (float) sum / count;
                    var = (float) sum2 / count - mean * mean;
                    std = (float) Math.sqrt(var);
                    cov = std / mean;
                }

            }

            /**
             * Draws the histogram
             *
             */
            void draw(GL2 gl) {
                if (!spatialHistogramEnabled) {
                    return;
                }
                float dx = (float) (chip.getSizeX() - 2) / (histNumBins + 2);
                float sy = (float) (chip.getSizeY() - 2) / maxCount;

                gl.glBegin(GL.GL_LINES);
                gl.glVertex2f(1, 1);
                gl.glVertex2f(chip.getSizeX() - 1, 1);
                gl.glEnd();

                if (lessCount > 0) {
                    gl.glPushAttrib(GL2.GL_COLOR | GL.GL_LINE_WIDTH);
                    gl.glColor4fv(HIST_OVERFLOW_COLOR, 0);
                    gl.glBegin(GL.GL_LINE_STRIP);

                    float y = 1 + (sy * lessCount);
                    float x1 = -dx, x2 = x1 + dx;
                    gl.glVertex2f(x1, 1);
                    gl.glVertex2f(x1, y);
                    gl.glVertex2f(x2, y);
                    gl.glVertex2f(x2, 1);
                    gl.glEnd();
                    gl.glPopAttrib();
                }
                if (moreCount > 0) {
                    gl.glPushAttrib(GL2.GL_COLOR | GL.GL_LINE_WIDTH);
                    gl.glColor4fv(HIST_OVERFLOW_COLOR, 0);
                    gl.glBegin(GL.GL_LINE_STRIP);

                    float y = 1 + (sy * moreCount);
                    float x1 = 1 + (dx * (histNumBins + 2)), x2 = x1 + dx;
                    gl.glVertex2f(x1, 1);
                    gl.glVertex2f(x1, y);
                    gl.glVertex2f(x2, y);
                    gl.glVertex2f(x2, 1);
                    gl.glEnd();
                    gl.glPopAttrib();
                }
                if (maxCount > 0) {
                    gl.glPushAttrib(GL2.GL_COLOR | GL.GL_LINE_WIDTH);
                    gl.glColor4fv(SPATIAL_HIST_COLOR, 0);
                    gl.glBegin(GL.GL_LINE_STRIP);
                    for (int i = 0; i < bins.length; i++) {
                        float y = 1 + (sy * bins[i]);
                        float x1 = 1 + (dx * i), x2 = x1 + dx;
                        gl.glVertex2f(x1, 1);
                        gl.glVertex2f(x1, y);
                        gl.glVertex2f(x2, y);
                        gl.glVertex2f(x2, 1);
                    }
                    gl.glEnd();
                    gl.glPopAttrib();
                }
                if (currentMousePoint != null) {
                    if (currentMousePoint.y <= 0) {
                        float sampleValue = ((float) currentMousePoint.x / chip.getSizeX()) * frameExtractor.getMaxADC();
                        gl.glColor3fv(SELECT_COLOR, 0);
                        renderer.begin3DRendering();
                        renderer.draw3D(String.format("%.0f", sampleValue), currentMousePoint.x, -8, 0, textScale);
                        renderer.end3DRendering();
                        gl.glLineWidth(3);
                        gl.glColor3fv(SELECT_COLOR, 0);
                        gl.glBegin(GL.GL_LINES);
                        gl.glVertex2f(currentMousePoint.x, 0);
                        gl.glVertex2f(currentMousePoint.x, chip.getSizeY());
                        gl.glEnd();
                    }
                }
                MultilineAnnotationTextRenderer.resetToYPositionPixels(chip.getSizeY() * .7f);
                float expRateDNperMs = mean / lastMeasuredExposureMs;
                float expDNperMsStd = expRateDNperMs / lastMeasuredExposureMs;
                MultilineAnnotationTextRenderer.renderMultilineString(String.format("Spatial histogram: mean=%.1f+/-%.3f DN (%.2f +/- %.3f DN/ms), COV=%.3f%%", mean, std, expRateDNperMs, expDNperMsStd, cov * 100));

            }

            void draw(GL2 gl, float lineWidth, float[] color) {
                gl.glPushAttrib(GL2.GL_COLOR | GL.GL_LINE_WIDTH);
                gl.glLineWidth(lineWidth);
                gl.glColor4fv(color, 0);
                draw(gl);
                gl.glPopAttrib();
            }

            private void reset() {
                if ((bins == null) || (bins.length != histNumBins)) {
                    bins = new int[histNumBins];
                } else {
                    Arrays.fill(bins, 0);
                }
                lessCount = 0;
                moreCount = 0;
                maxCount = 0;
                virgin = true;
                count = 0;
                sum = 0;
                sum2 = 0;
            }

            private int getSampleBin(int sample) {
                int bin = (int) Math.floor((histNumBins * ((float) sample - sampleMin)) / (sampleMax - sampleMin));
                return bin;
            }
        }
    }

    /**
     * @return the temporalNoiseEnabled
     */
    public boolean isTemporalNoiseEnabled() {
        return temporalNoiseEnabled;
    }

    /**
     * @param temporalNoiseEnabled the temporalNoiseEnabled to set
     */
    public void setTemporalNoiseEnabled(boolean temporalNoiseEnabled) {
        this.temporalNoiseEnabled = temporalNoiseEnabled;
        putBoolean("temporalNoiseEnabled", temporalNoiseEnabled);
    }

    /**
     * @return the spatialHistogramEnabled
     */
    public boolean isSpatialHistogramEnabled() {
        return spatialHistogramEnabled;
    }

    /**
     * @param spatialHistogramEnabled the spatialHistogramEnabled to set
     */
    public void setSpatialHistogramEnabled(boolean spatialHistogramEnabled) {
        this.spatialHistogramEnabled = spatialHistogramEnabled;
        putBoolean("spatialHistogramEnabled", spatialHistogramEnabled);
    }

    /**
     * @return the scaleHistogramsIncludingOverflow
     */
    public boolean isScaleHistogramsIncludingOverflow() {
        return scaleHistogramsIncludingOverflow;
    }

    /**
     * @param scaleHistogramsIncludingOverflow the
     * scaleHistogramsIncludingOverflow to set
     */
    public void setScaleHistogramsIncludingOverflow(boolean scaleHistogramsIncludingOverflow) {
        this.scaleHistogramsIncludingOverflow = scaleHistogramsIncludingOverflow;
        putBoolean("scaleHistogramsIncludingOverflow", scaleHistogramsIncludingOverflow);
    }

    /**
     * @return the histNumBins
     */
    public int getHistNumBins() {
        return histNumBins;
    }

    /**
     * @param histNumBins the histNumBins to set
     */
    synchronized public void setHistNumBins(int histNumBins) {
        if (histNumBins < 2) {
            histNumBins = 2;
        } else if (histNumBins > (frameExtractor.getMaxADC() + 1)) {
            histNumBins = frameExtractor.getMaxADC() + 1;
        }
        this.histNumBins = histNumBins;
        putInt("histNumBins", histNumBins);
        stats.reset();
    }

    /**
     * @return the resetOnBiasChange
     */
    public boolean isResetOnBiasChange() {
        return resetOnBiasChange;
    }

    /**
     * @param resetOnBiasChange the resetOnBiasChange to set
     */
    public void setResetOnBiasChange(boolean resetOnBiasChange) {
        this.resetOnBiasChange = resetOnBiasChange;
        putBoolean("resetOnBiasChange", resetOnBiasChange);
    }

    /**
     * @return the discardPixelsWithVarianceLargerThan
     */
    public float getDiscardPixelsWithVarianceLargerThan() {
        return discardPixelsWithVarianceLargerThan;
    }

    /**
     * @param discardPixelsWithVarianceLargerThan the
     * discardPixelsWithVarianceLargerThan to set
     */
    public void setDiscardPixelsWithVarianceLargerThan(float discardPixelsWithVarianceLargerThan) {
        this.discardPixelsWithVarianceLargerThan = discardPixelsWithVarianceLargerThan;
        putFloat("discardPixelsWithVarianceLargerThan", discardPixelsWithVarianceLargerThan);
    }

    /**
     * @return the drawPtcLineWithMouse
     */
    public boolean isDrawPtcLineWithMouse() {
        return drawPtcLineWithMouse;
    }

    /**
     * @param drawPtcLineWithMouse the drawPtcLineWithMouse to set
     */
    public void setDrawPtcLineWithMouse(boolean drawPtcLineWithMouse) {
        this.drawPtcLineWithMouse = drawPtcLineWithMouse;
    }
}
