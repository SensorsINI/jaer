/*
 * ChipRenderer.java
 *
 * Created on May 2, 2006, 1:49 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package net.sf.jaer.graphics;

import java.beans.PropertyChangeEvent;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;

import ch.unizh.ini.jaer.chip.retina.DvsDisplayConfigInterface;
import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.DavisBaseCamera;
import eu.seebetter.ini.chips.davis.DavisVideoContrastController;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.PolarityEvent.Polarity;
import net.sf.jaer.event.orientation.OrientationEventInterface;
import net.sf.jaer.util.filter.LowpassFilter2D;
import net.sf.jaer.util.histogram.SimpleHistogram;

/**
 * Class adapted from AEChipRenderer to render not only AE events but also
 * frames.
 *
 * The frame buffer is RGBA so four bytes per pixel. The rendering uses a
 * texture which is a power of two multiple of image size, so watch out for
 * getWidth and getHeight; they return this value and not the number of pixels
 * being rendered from the chip.
 *
 * Besides the pixmaps for APS samples and ON and OFF events, an additional
 * pixmap is provided for pixel annotation; see {@link #getAnnotateMap() }.
 *
 * @author christian, tobi
 * @see ChipRendererDisplayMethod
 */
public class AEFrameChipRenderer extends AEChipRenderer {

    /**
     * PropertyChange events
     */
    public static final String EVENT_NEW_FRAME_AVAILBLE = "newFrameAvailable";

    /**
     * Set true after we have added a property change listener
     */
    protected boolean addedPropertyChangeListener = false;
    public int textureWidth; // due to hardware acceleration reasons, has to be a 2^x with x a natural number
    public int textureHeight; // due to hardware acceleration reasons, has to be a 2^x with x a natural number

    /**
     * Fields used to reduce method calls
     */
    protected int sizeX, sizeY, maxADC, numEventTypes;

    /**
     * Used to mark time of frame event
     */
    protected int timestampFrameStart = 0;

    /**
     * Used to mark time of frame event
     */
    protected int timestampFrameEnd = 0;

    /**
     * low pass temporal filter that computes time-averaged min and max gray
     * values
     */
    protected LowpassFilter2D autoContrast2DLowpassRangeFilter = new LowpassFilter2D(); // 2 lp values are min and max
    // log intensities from each
    // frame
    /**
     * min and max values of rendered gray values
     */
    protected float minValue, maxValue, annotateAlpha;
    /**
     * RGBA rendering colors for ON and OFF DVS events
     */
    protected float[] onColor, offColor;

    /**
     * The linear buffer of RGBA pixel colors of image frame brightness values
     */
    protected FloatBuffer pixBuffer;
    protected FloatBuffer onMap, onBuffer;
    protected FloatBuffer offMap, offBuffer;
    /**
     * pix map used for annotation overlay.
     *
     * @see #setAnnotateAlpha(float)
     * @see #setDisplayAnnotation(boolean)
     * @see #setAnnotateColorRGBA(int, int, float[]) and similar methods
     */
    protected FloatBuffer annotateMap;
    // double buffered histogram so we can accumulate new histogram while old one is still being rendered and returned
    // to caller
    private final int histStep = 4; // histogram bin step in ADC counts of 1024 levels
    private final SimpleHistogram adcSampleValueHistogram1 = new SimpleHistogram(0, histStep, (DavisChip.MAX_ADC + 1) / histStep, 0);
    private final SimpleHistogram adcSampleValueHistogram2 = new SimpleHistogram(0, histStep, (DavisChip.MAX_ADC + 1) / histStep, 0);
    /**
     * Histogram objects used to collect APS statistics
     */
    protected SimpleHistogram currentHist = adcSampleValueHistogram1, nextHist = adcSampleValueHistogram2;

    protected DavisVideoContrastController contrastController = null;

    /**
     * Boolean on whether to compute the histogram of gray levels
     */
    protected boolean computeHistograms = false;
    private boolean displayAnnotation = false;

    /**
     * downsampling of DVS to speed up rendering at high frame rate
     */
    private int dvsDownsamplingValue = 0, dvsDownsamplingCount = 0;

    public AEFrameChipRenderer(final AEChip chip) {
        super(chip);

        if (chip.getNumPixels() == 0) {
            log.warning("chip has zero pixels; is the constuctor of AEFrameChipRenderer called before size of the AEChip is set?");
            return;
        }

        onColor = new float[4];
        offColor = new float[4];

        checkPixmapAllocation();

        if (chip instanceof DavisChip) {
            contrastController = new DavisVideoContrastController((DavisChip) chip);
            contrastController.getSupport().addPropertyChangeListener(this);
        } else {
            log.warning("cannot make a DavisVideoContrastController for this chip because it does not extend DavisChip");
        }
        // when contrast controller properties change, inform this so this can pass on to the chip
    }

    /**
     * Overridden to make gray buffer special for bDVS array
     */
    @Override
    protected void resetPixmapGrayLevel(final float value) {
        maxValue = Float.MIN_VALUE;
        minValue = Float.MAX_VALUE;
        setAnnotateAlpha(1.0f);
        checkPixmapAllocation();
        final int n = 4 * textureWidth * textureHeight;
        boolean madebuffer = false;
        if ((grayBuffer == null) || (grayBuffer.capacity() != n)) {
            grayBuffer = FloatBuffer.allocate(n); // BufferUtil.newFloatBuffer(n);
            madebuffer = true;
        }
        if (madebuffer || (value != grayValue)) {
            grayBuffer.rewind();
            for (int y = 0; y < textureWidth; y++) {
                for (int x = 0; x < textureHeight; x++) {
                    if (isDisplayFrames()) {
                        grayBuffer.put(0);
                        grayBuffer.put(0);
                        grayBuffer.put(0);
                        grayBuffer.put(1.0f);
                    } else if (colorMode == ColorMode.GrayTime) {
                        grayBuffer.put(1.0f);
                        grayBuffer.put(1.0f);
                        grayBuffer.put(1.0f);
                        grayBuffer.put(1.0f);
                    } else if (colorMode == ColorMode.HotCode) {
                        grayBuffer.put(0);
                        grayBuffer.put(0);
                        grayBuffer.put(.5f);
                        grayBuffer.put(.5f);
                    } else {
                        grayBuffer.put(grayValue);
                        grayBuffer.put(grayValue);
                        grayBuffer.put(grayValue);
                        grayBuffer.put(1.0f);
                    }
                }
            }
        }
        grayBuffer.rewind();
        System.arraycopy(grayBuffer.array(), 0, pixmap.array(), 0, n);
        System.arraycopy(grayBuffer.array(), 0, pixBuffer.array(), 0, n);
        pixmap.rewind();
        pixBuffer.rewind();
        pixmap.limit(n);
        pixBuffer.limit(n);
        setColors();
        resetMaps();
    }

    protected void resetMaps() {
        setColors();
        checkPixmapAllocation();
        final int n = 4 * textureWidth * textureHeight;
        if ((grayBuffer == null) || (grayBuffer.capacity() != n)) {
            grayBuffer = FloatBuffer.allocate(n); // BufferUtil.newFloatBuffer(n);
        }

        grayBuffer.rewind();
        // Fill maps with fully transparent values
        Arrays.fill(grayBuffer.array(), 0.0f);
        System.arraycopy(grayBuffer.array(), 0, onMap.array(), 0, n);
        System.arraycopy(grayBuffer.array(), 0, offMap.array(), 0, n);
        // if(displayAnnotation) Arrays.fill(annotateMap.array(), 0);

        grayBuffer.rewind();
        onMap.rewind();
        offMap.rewind();
        onMap.limit(n);
        offMap.limit(n);
    }

    public synchronized void clearAnnotationMap() {
        resetAnnotationFrame(0);
    }

    @Override
    public synchronized void resetAnnotationFrame(final float resetValue) {
        checkPixmapAllocation();
        final int n = 4 * textureWidth * textureHeight;
        if ((grayBuffer == null) || (grayBuffer.capacity() != n)) {
            grayBuffer = FloatBuffer.allocate(n); // BufferUtil.newFloatBuffer(n);
        }

        grayBuffer.rewind();
        // Fill maps with fully transparent values
        Arrays.fill(grayBuffer.array(), resetValue);
        System.arraycopy(grayBuffer.array(), 0, annotateMap.array(), 0, n);

        grayBuffer.rewind();
        annotateMap.rewind();
        annotateMap.limit(n);
    }

    @Override
    public synchronized void setColorMode(final ColorMode colorMode) {
        super.setColorMode(colorMode);
        setColors();
    }

    private void setColors() {
        checkPixmapAllocation();
        switch (colorMode) {
            case GrayLevel:
            case Contrast:
                onColor[0] = 1.0f;
                onColor[1] = 1.0f;
                onColor[2] = 1.0f;
                onColor[3] = 0.0f;
                offColor[0] = 0.0f;
                offColor[1] = 0.0f;
                offColor[2] = 0.0f;
                offColor[3] = 0.0f;
                break;
            case HotCode:
                onColor[0] = 1.0f;
                onColor[1] = 1.0f;
                onColor[2] = 1.0f;
                onColor[3] = 0.0f;
                offColor[0] = 0.0f;
                offColor[1] = 0.0f;
                offColor[2] = 0.0f;
                offColor[3] = 0.0f;
                break;
            case RedGreen:
            default:
                onColor[0] = 0.0f;
                onColor[1] = 1.0f;
                onColor[2] = 0.0f;
                onColor[3] = 0.0f;
                offColor[0] = 1.0f;
                offColor[1] = 0.0f;
                offColor[2] = 0.0f;
                offColor[3] = 0.0f;
                break;
        }
    }

    /**
     * warning counter for some warnings
     */
    protected int warningCount = 0;

    /**
     * interval to print warning messages
     */
    protected static int WARNING_INTERVAL = 100;

    @Override
    public synchronized void render(final EventPacket pkt) {
        if (!addedPropertyChangeListener) {
            if (chip != null) {
                if (chip.getAeViewer() != null) {
                    chip.getAeViewer().addPropertyChangeListener(this);
                    addedPropertyChangeListener = true;
                }
            }
        }
//        adaptDvsDownsampling();

        numEventTypes = pkt.getNumCellTypes();

        if (pkt instanceof ApsDvsEventPacket) {
            renderApsDvsEvents(pkt);
        } else {
            renderDvsEvents(pkt);
        }
    }

    protected void renderApsDvsEvents(final EventPacket pkt) {
        if (getChip() instanceof DavisBaseCamera) {
            computeHistograms = ((DavisBaseCamera) chip).isShowImageHistogram() || ((DavisChip) chip).isAutoExposureEnabled();
        }

        if (!accumulateEnabled) {
            resetMaps();

            if (numEventTypes > 2) {
                resetAnnotationFrame(0.0f);
            }
        }

        final ApsDvsEventPacket packetAPS = (ApsDvsEventPacket) pkt;
        packet = packetAPS;

        checkPixmapAllocation();
        resetSelectedPixelEventCount(); // TODO fix locating pixel with xsel ysel
        setSpecialCount(0);

        if (!(packetAPS.getEventPrototype() instanceof ApsDvsEvent)) {
            if ((warningCount++ % AEFrameChipRenderer.WARNING_INTERVAL) == 0) {
                log.warning("wrong input event class, got " + packetAPS.getEventPrototype() + " but we need to have " + ApsDvsEvent.class);
            }
            return;
        }

        final boolean displayEvents = isDisplayEvents();
        final boolean displayFrames = isDisplayFrames();
        final boolean backwards = packetAPS.getDurationUs() < 0;

        final Iterator allItr = packetAPS.fullIterator();
        packetAPS.setTimeLimitEnabled(false);
        while (allItr.hasNext()) {
            // The iterator only iterates over the DVS events
            final ApsDvsEvent e = (ApsDvsEvent) allItr.next();

            if (e.isSpecial()) {
                incrementSpecialCount(1);
                continue;

            }

            final int type = e.getType();
            final boolean isAPSPixel = e.isApsData();

            if (!isAPSPixel) {
                if (displayEvents) {
                    if ((xsel >= 0) && (ysel >= 0)) { // find correct mouse pixel interpretation to make sounds for
                        // large pixels
                        if ((e.x == xsel) && (e.y == ysel)) {
                            playSpike(type);
                        }
                    }

                    updateEventMaps(e);
                }
            } else if (!backwards && isAPSPixel && displayFrames) { // TODO need to handle single step updates
                // here
                updateFrameBuffer(e);
            }
        }
    }

    protected void renderDvsEvents(final EventPacket pkt) {
        if (!accumulateEnabled) {
            resetMaps();

            if (numEventTypes > 2) {
                resetAnnotationFrame(0.0f);
            }
        }

        packet = pkt;

        checkPixmapAllocation();
        resetSelectedPixelEventCount(); // TODO fix locating pixel with xsel ysel
        setSpecialCount(0);

        final boolean displayEvents = isDisplayEvents();

        final Iterator itr = packet.inputIterator();
        while (itr.hasNext()) {
            // The iterator only iterates over the DVS events
            final PolarityEvent e = (PolarityEvent) itr.next();

            if (e.isSpecial()) {
                incrementSpecialCount(1);
                continue;
            }

            final int type = e.getType();

            if (displayEvents) {
                if ((xsel >= 0) && (ysel >= 0)) { // find correct mouse pixel interpretation to make sounds for large
                    // pixels
                    final int xs = xsel, ys = ysel;
                    if ((e.x == xs) && (e.y == ys)) {
                        playSpike(type);
                    }
                }

                updateEventMaps(e);
            }
        }
    }

    private final Random random = new Random();

    protected void updateFrameBuffer(final ApsDvsEvent e) {
        final float[] buf = pixBuffer.array();
        // TODO if playing backwards, then frame will come out white because B sample comes before A

        if (e.isStartOfFrame()) {
            startFrame(e.timestamp);
        } else if (e.isResetRead()) {
            final int index = getIndex(e);
            if ((index < 0) || (index >= buf.length)) {
                return;
            }

            final float val = e.getAdcSample();
            buf[index] = val;
        } else if (e.isSignalRead()) {
            final int index = getIndex(e);
            if ((index < 0) || (index >= buf.length)) {
                return;
            }

            int val = ((int) buf[index] - e.getAdcSample());

            if (val < 0) {
                val = 0;
            }
            if ((val >= 0) && (val < minValue)) {
                minValue = val;
            } else if (val > maxValue) {
                maxValue = val;
            }

            // right here sample-reset value of this pixel is in val
            if (computeHistograms) {
                if (!((DavisChip) chip).getAutoExposureController().isCenterWeighted()) {
                    nextHist.add(val);
                } else {
                    // randomly add histogram values to histogram depending on distance from center of image
                    // to implement a simple form of center weighting of the histogram
                    float d = (1 - Math.abs(((float) e.x - (sizeX / 2)) / sizeX)) + Math.abs(((float) e.y - (sizeY / 2)) / sizeY);
                    // d is zero at center, 1 at corners
                    d *= d;

                    final float r = random.nextFloat();
                    if (r > d) {
                        nextHist.add(val);
                    }
                }
            }

            final float fval = normalizeFramePixel(val);
            buf[index] = fval;
            buf[index + 1] = fval;
            buf[index + 2] = fval;
            buf[index + 3] = 1;
        } else if (e.isEndOfFrame()) {
            endFrame(e.timestamp);

            final SimpleHistogram tmp = currentHist;
            if (computeHistograms) {
                currentHist = nextHist;
                nextHist = tmp;
                nextHist.reset();
            }

            ((DavisChip) chip).controlExposure();
        }
    }

    protected void startFrame(final int ts) {
        timestampFrameStart = ts;
        maxValue = Float.MIN_VALUE;
        minValue = Float.MAX_VALUE;
        System.arraycopy(grayBuffer.array(), 0, pixBuffer.array(), 0, pixBuffer.array().length);

    }

    protected void endFrame(final int ts) {
        timestampFrameEnd = ts;
        System.arraycopy(pixBuffer.array(), 0, pixmap.array(), 0, pixBuffer.array().length);

        if ((contrastController != null) && (minValue != Float.MAX_VALUE) && (maxValue != Float.MIN_VALUE)) {
            contrastController.endFrame(minValue, maxValue, timestampFrameEnd);
        }

        getSupport().firePropertyChange(AEFrameChipRenderer.EVENT_NEW_FRAME_AVAILBLE, null, this);
    }

    /**
     * changes alpha of ON map
     *
     * @param index 0-(size of pixel array-1) of pixel
     */
    protected void updateEventMaps(final PolarityEvent e) {
//        if (dvsDownsamplingCount++ < dvsDownsamplingValue) {
//            return;
//        }
        dvsDownsamplingCount = 0;
        float[] map;
        if (packet.getNumCellTypes() > 2) {
            map = onMap.array();
        } else if (e.polarity == Polarity.On) {
            map = onMap.array();
        } else {
            map = offMap.array();
        }

        final int index = getIndex(e);
        if ((index < 0) || (index >= map.length)) {
            return;
        }

        if (packet.getNumCellTypes() > 2) {
            checkTypeColors(packet.getNumCellTypes());

            if ((e instanceof OrientationEventInterface) && (((OrientationEventInterface) e).isHasOrientation() == false)) {
                // if event is orientation event but orientation was not set, just draw as gray level
                map[index] = 1.0f; // if(f[0]>1f) f[0]=1f;
                map[index + 1] = 1.0f; // if(f[1]>1f) f[1]=1f;
                map[index + 2] = 1.0f; // if(f[2]>1f) f[2]=1f;
            } else {
                // if color scale is 1, then last value is used as the pixel value, which quantizes the color to full
                // scale.
                final float[] c = typeColorRGBComponents[e.getType()];
                map[index] = c[0]; // if(f[0]>1f) f[0]=1f;
                map[index + 1] = c[1]; // if(f[1]>1f) f[1]=1f;
                map[index + 2] = c[2]; // if(f[2]>1f) f[2]=1f;
            }

            final float alpha = map[index + 3] + (1.0f / colorScale);
            map[index + 3] += normalizeEvent(alpha);
        } else if (colorMode == ColorMode.ColorTime) {
            final int ts0 = packet.getFirstTimestamp();
            final float dt = packet.getDurationUs();
            int ind = (int) Math.floor(((AEChipRenderer.NUM_TIME_COLORS - 1) * (e.timestamp - ts0)) / dt);

            if (ind < 0) {
                ind = 0;
            } else if (ind >= timeColors.length) {
                ind = timeColors.length - 1;
            }

            map[index] = timeColors[ind][0];
            map[index + 1] = timeColors[ind][1];
            map[index + 2] = timeColors[ind][2];
            map[index + 3] = 0.5f;
        } else if (colorMode == ColorMode.HotCode) {
            final float alpha = map[index + 3] + (1.0f / colorScale);
            map[index + 3] = normalizeEvent(alpha);
            int ind = (int) Math.floor(((AEChipRenderer.NUM_TIME_COLORS - 1) * alpha));

            if (ind < 0) {
                ind = 0;
            } else if (ind >= timeColors.length) {
                ind = timeColors.length - 1;
            }

            map[index] = timeColors[ind][0];
            map[index + 1] = timeColors[ind][1];
            map[index + 2] = timeColors[ind][2];
        } else if (colorMode == ColorMode.GrayTime) {
            final int ts0 = packet.getFirstTimestamp();
            final float dt = packet.getDurationUs();
            final float v = 0.95f - (0.95f * ((e.timestamp - ts0) / dt));

            map[index] = v;
            map[index + 1] = v;
            map[index + 2] = v;
            map[index + 3] = 1.0f;
        } else {
            if ((e.polarity == PolarityEvent.Polarity.On) || ignorePolarityEnabled) {
                map[index] = onColor[0];
                map[index + 1] = onColor[1];
                map[index + 2] = onColor[2]; // if using gray/contrast rendering, then just use onMap and onColor, and set alpaha up or down from .5 below
            } else {
                map[index] = offColor[0];
                map[index + 1] = offColor[1];
                map[index + 2] = offColor[2];
            }

            final float alpha = map[index + 3] + (1.0f / colorScale);
            map[index + 3] = normalizeEvent(alpha);
        }
    }

    protected final int INTERVAL_BETWEEEN_OUT_OF_BOUNDS_EXCEPTIONS_PRINTED_MS = 1000;
    protected long lastWarningPrintedTimeMs = Integer.MAX_VALUE;
    protected int badEventCount = 0;
    private int lastPrintedBadEventCount = 0;

    /**
     * Returns index to R (red) value in RGBA pixmap
     *
     * @param e
     * @return index to red entry in RGBA pixmap
     */
    protected int getIndex(final BasicEvent e) {
        final int x = e.x, y = e.y;

        if ((x < 0) || (y < 0) || (x >= sizeX) || (y >= sizeY)) {
            badEventCount++;
            if ((System.currentTimeMillis() - lastWarningPrintedTimeMs) > INTERVAL_BETWEEEN_OUT_OF_BOUNDS_EXCEPTIONS_PRINTED_MS) {
                int newBadEventCount = badEventCount - lastPrintedBadEventCount;
                log.warning(String.format(
                        "Event %s out of bounds and cannot be rendered in bounds sizeX=%d sizeY=%d\n delaying next warning for %dms\n %d bad events since last warning",
                        e.toString(), sizeX, sizeY, INTERVAL_BETWEEEN_OUT_OF_BOUNDS_EXCEPTIONS_PRINTED_MS, newBadEventCount));
                lastPrintedBadEventCount = badEventCount;
                lastWarningPrintedTimeMs = System.currentTimeMillis();

            }

            return -1;
        }

        return getPixMapIndex(x, y);
    }

    @Override
    protected void checkPixmapAllocation() {
        if ((sizeX != chip.getSizeX()) || (sizeY != chip.getSizeY())) {
            sizeX = chip.getSizeX();
            textureWidth = AEFrameChipRenderer.ceilingPow2(sizeX);

            sizeY = chip.getSizeY();
            textureHeight = AEFrameChipRenderer.ceilingPow2(sizeY);
        }

        final int n = 4 * textureWidth * textureHeight;
        if ((pixmap == null) || (pixmap.capacity() < n) || (pixBuffer.capacity() < n) || (onMap.capacity() < n) || (offMap.capacity() < n)
                || (annotateMap.capacity() < n)) {
            pixmap = FloatBuffer.allocate(n); // BufferUtil.newFloatBuffer(n);
            pixBuffer = FloatBuffer.allocate(n);
            onMap = FloatBuffer.allocate(n);
            offMap = FloatBuffer.allocate(n);
            annotateMap = FloatBuffer.allocate(n);
        }
    }

    /**
     * Overrides color scale setting to NOT update the stored accumulated pixmap
     * when the color scale is changed.
     *
     *
     */
    @Override
    synchronized public void setColorScale(int colorScale) {
        final int old = this.colorScale;

        if (colorScale < 1) {
            colorScale = 1;
        } else if (colorScale > 128) {
            colorScale = 128;
        }

        this.colorScale = colorScale;
        prefs.putInt("Chip2DRenderer.colorScale", colorScale);
        getSupport().firePropertyChange(AEChipRenderer.EVENT_COLOR_SCALE_CHANGE, old, colorScale);
    }

    /**
     * computes power of two value that is equal to or greater than argument
     *
     * @param n value, e.g. 3
     * @return power of two that is >=n, e.g. 4
     */
    protected static int ceilingPow2(final int n) {
        int pow2 = 1;
        while (n > pow2) {
            pow2 = pow2 << 1;
        }
        return pow2;
    }

    /**
     * Returns pixmap for ON events
     *
     * @return a float buffer. Obtain a pixel from it using getPixMapIndex
     * @see #getPixMapIndex(int, int)
     */
    protected FloatBuffer getOnMap() {
        onMap.rewind();
        checkPixmapAllocation();
        return onMap;
    }

    /**
     * Returns pixmap for OFF events
     *
     * @return a float buffer. Obtain a pixel from it using getPixMapIndex
     * @see #getPixMapIndex(int, int)
     */
    protected FloatBuffer getOffMap() {
        offMap.rewind();
        checkPixmapAllocation();
        return offMap;
    }

    /**
     * Returns pixmap for annotated pixels
     *
     * @return a float buffer. Obtain a pixel from it using getPixMapIndex
     * @see #getPixMapIndex(int, int)
     */
    protected FloatBuffer getAnnotateMap() {
        annotateMap.rewind();
        checkPixmapAllocation();
        return annotateMap;
    }

    /**
     * Returns index into pixmap. To access RGB values, just add 0,1, or 2 to
     * the returned index.
     *
     * @param x
     * @param y
     * @return the index
     * @see #getPixmapArray()
     * @see #getPixmap()
     */
    @Override
    final public int getPixMapIndex(final int x, final int y) {
        return 4 * ((y * textureWidth) + x);
    }

    /**
     * Overridden to return ON and OFF map values as R and G channels. B channel
     * is returned 0. Note that this method returns rendering of events; it
     * disregards APS frame values.
     *
     * @param x
     * @param y
     * @return
     */
    public float[] getDvsRenderedValuesAtPixel(final int x, final int y) {
        final int k = getPixMapIndex(x, y);
        final float[] f = new float[3];
        f[0] = onMap.get(k + 3);
        f[1] = offMap.get(k + 3);
        f[2] = 0; // return alpha channel which is the ON and OFF value that is rendered (RGB are 1 for ON and OFF maps)
        return f; // To change body of generated methods, choose Tools | Templates.
    }

    /**
     * Overridden to combine RGB values to a gray value by averaging them. Note
     * that this method returns rendering of frame image; it returns the
     * rendered APS samples and not the raw ADC values.
     *
     * @param x
     * @param y
     * @return
     */
    public float getApsGrayValueAtPixel(final int x, final int y) {
        final int k = getPixMapIndex(x, y);
        final float[] pm = pixmap.array();
        return (pm[k] + pm[k + 1] + pm[k + 2]) / 3;
    }

    /**
     * Sets RGB color components all to same gray value g
     *
     * @param x pixel x, 0,0 is LL corner
     * @param y pixel y
     * @param g gray value in range 0-1
     */
    public void setApsGrayValueAtPixel(final int x, final int y, final float g) {
        final int k = getPixMapIndex(x, y);
        final float[] pm = pixmap.array();
        pm[k] = g;
        pm[k + 1] = g;
        pm[k + 2] = g;
    }

    /**
     * Returns the buffer holding the image frame brightness values in RGBA
     * order
     */
    protected FloatBuffer getPixBuffer() {
        return pixBuffer;
    }

    /**
     * sets a specific value of the pixmap
     *
     * @param index
     * @param value
     */
    @Override
    public void setAnnotateValue(final int index, final float value) {
        annotateMap.put(index, value);
    }

    /**
     * sets a specific color (rgb float 0-1) of the pixmap
     *
     * @param index
     * @param value
     */
    @Override
    public void setAnnotateColorRGB(final int index, final float[] value) {
        annotateMap.put(index, value[0]);
        annotateMap.put(index + 1, value[1]);
        annotateMap.put(index + 2, value[2]);
        annotateMap.put(index + 3, getAnnotateAlpha());
    }

    /**
     * sets a specific color (rgb float 0-1) of the pixmap
     *
     * @param index
     * @param value
     */
    public void setAnnotateColorRGBA(final int index, final float[] value) {
        annotateMap.put(index, value[0]);
        annotateMap.put(index + 1, value[1]);
        annotateMap.put(index + 2, value[2]);
        annotateMap.put(index + 3, value[3]);
    }

    /**
     * sets a specific color (rgb float 0-1) of the pixmap with default alpha
     * value
     *
     * @param x
     * @param y
     * @param value a float[3] array of RGB values
     * @see #setAnnotateAlpha(float)
     */
    @Override
    public void setAnnotateColorRGB(final int x, final int y, final float[] value) {
        final int index = getPixMapIndex(x, y);
        annotateMap.put(index, value[0]);
        annotateMap.put(index + 1, value[1]);
        annotateMap.put(index + 2, value[2]);
        annotateMap.put(index + 3, getAnnotateAlpha());
    }

    /**
     * sets a specific color (rgb float 0-1) of the pixmap including alpha
     *
     * @param x
     * @param y
     * @param value a float[4] array of RGBA values
     */
    public void setAnnotateColorRGBA(final int x, final int y, final float[] value) {
        final int index = getPixMapIndex(x, y);
        annotateMap.put(index, value[0]);
        annotateMap.put(index + 1, value[1]);
        annotateMap.put(index + 2, value[2]);
        annotateMap.put(index + 3, value[3]);
    }

    /**
     * sets the alpha value of a specific pixel
     *
     * @param x
     * @param y
     * @param value a float alpha value
     */
    public void setAnnotateAlpha(final int x, final int y, final float alpha) {
        final int index = getPixMapIndex(x, y);
        annotateMap.put(index + 3, alpha);
    }

    /**
     * Returns the width of the texture used to render output. Note this is NOT
     * the chip dimension; it is a power of 2 multiple that is next larger to
     * chip size.
     *
     * @return power of 2 multiple that is next larger to chip size
     */
    @Override
    public int getWidth() {
        return textureWidth;
    }

    /**
     * Returns the height of the texture used to render output. Note this is NOT
     * the chip dimension; it is a power of 2 multiple that is next larger to
     * chip size.
     *
     * @return power of 2 multiple that is next larger to chip size
     */
    @Override
    public int getHeight() {
        return textureHeight;
    }

    /**
     * Computes the normalized gray value from an ADC sample value using
     * brightness (offset), contrast (multiplier), and gamma (power law). Takes
     * account of the autoContrast setting which attempts to set value
     * automatically to get image in range of display.
     *
     * @param value the ADC value
     * @return the gray value
     */
    protected float normalizeFramePixel(final float value) {
        if (contrastController != null) {
            return contrastController.normalizePixelGrayValue(value, maxADC);
        }

        // Return unchanged in absence of contrast controller.
        return value;
    }

    final protected float normalizeEvent(float value) {
        if (value < 0) {
            value = 0;
        } else if (value > 1) {
            value = 1;
        }

        return value;
    }

    public int getMaxADC() {
        return maxADC;
    }

    public void setMaxADC(final int max) {
        maxADC = max;
    }

    /**
     * @return the gray level of the rendered data; used to determine whether a
     * pixel needs to be drawn
     */
    @Override
    public float getGrayValue() {
        if (isDisplayFrames() || (colorMode == ColorMode.Contrast) || (colorMode == ColorMode.GrayLevel)) {
            grayValue = 0.5f;
        } else if (colorMode == ColorMode.GrayTime) {
            grayValue = 1.0f;
        } else {
            grayValue = 0.0f;
        }

        return grayValue;
    }

    /**
     * Returns the current valid histogram of ADC sample values. New ADC values
     * are accumulated to another histogram and the histograms are swapped when
     * a new frame has finished being captured.
     *
     * @return the adcSampleValueHistogram
     */
    public SimpleHistogram getAdcSampleValueHistogram() {
        return currentHist;
    }

    public int getWidthInPixels() {
        return getWidth();
    }

    public int getHeightInPixels() {
        return getHeight();
    }

    /**
     * @return the annotateAlpha
     */
    public float getAnnotateAlpha() {
        return annotateAlpha;
    }

    /**
     * Sets the alpha of the annotation layer. This alpha determines the
     * transparency of the annotation.
     *
     * @param annotateAlpha the annotateAlpha to set
     * @see #setDisplayAnnotation(boolean)
     * @see #setAnnotateColorRGBA(int, int, float[]) and similar methods
     */
    public void setAnnotateAlpha(final float annotateAlpha) {
        this.annotateAlpha = annotateAlpha;
    }

    /**
     * Returns whether the annotation layer is displayed
     *
     * @return the displayAnnotation
     */
    public boolean isDisplayAnnotation() {
        return displayAnnotation;
    }

    /**
     * Sets whether the annotation layer is displayed.
     *
     * @param displayAnnotation the displayAnnotation to set
     */
    public void setDisplayAnnotation(final boolean displayAnnotation) {
        this.displayAnnotation = displayAnnotation;
    }

    /**
     * Sets whether an external renderer adds data to the array and resets it
     *
     * @param extRender
     */
    @Override
    public void setExternalRenderer(final boolean extRender) {
        externalRenderer = extRender;
        displayAnnotation = extRender;
    }

    public boolean isDisplayFrames() {
        return ((DvsDisplayConfigInterface) chip.getBiasgen()).isDisplayFrames();
    }

    public boolean isDisplayEvents() {
        return ((DvsDisplayConfigInterface) chip.getBiasgen()).isDisplayEvents();
    }

    /**
     * @return the contrastController
     */
    public DavisVideoContrastController getContrastController() {
        return contrastController;
    }

    /**
     * @param contrastController the contrastController to set
     */
    public void setContrastController(final DavisVideoContrastController contrastController) {
        this.contrastController = contrastController;
    }

    public boolean isUseAutoContrast() {
        return contrastController.isUseAutoContrast();
    }

    public void setUseAutoContrast(final boolean useAutoContrast) {
        contrastController.setUseAutoContrast(useAutoContrast);
    }

    public float getContrast() {
        return contrastController.getContrast();
    }

    public void setContrast(final float contrast) {
        contrastController.setContrast(contrast);
    }

    public float getBrightness() {
        return contrastController.getBrightness();
    }

    public void setBrightness(final float brightness) {
        contrastController.setBrightness(brightness);
    }

    public float getGamma() {
        return contrastController.getGamma();
    }

    public void setGamma(final float gamma) {
        contrastController.setGamma(gamma);
    }

    public void setAutoContrastTimeconstantMs(final float tauMs) {
        contrastController.setAutoContrastTimeconstantMs(tauMs);
    }

    @Override
    public void propertyChange(final PropertyChangeEvent pce) {
        super.propertyChange(pce); // To change body of generated methods, choose Tools | Templates.
        if (chip != null && chip.getBiasgen() != null) {
            chip.getBiasgen().getSupport().firePropertyChange(pce); // pass on events to chip configuration
        }
    }

    /**
     * Returns the timestamp of the pixel read out at the start of the frame
     * readout (not the exposure start)
     *
     * @return the timestampFrameStart
     */
    public int getTimestampFrameStart() {
        return timestampFrameStart;
    }

    /**
     * Returns the timestamp of the pixel read out at end of the frame (not the
     * exposure end)
     *
     * @return the timestampFrameEnd
     */
    public int getTimestampFrameEnd() {
        return timestampFrameEnd;
    }

    private void adaptDvsDownsampling() {
        if (chip.getAeViewer() == null || chip.getAeViewer().isPaused()) {
            return;
        }
        final float averageFPS = chip.getAeViewer().getFrameRater().getAverageFPS();
        final int desiredFrameRate = chip.getAeViewer().getDesiredFrameRate();
        boolean skipMore = averageFPS < (int) (0.75f * desiredFrameRate);
        boolean skipLess = averageFPS > (int) (0.25f * desiredFrameRate);
        if (skipMore) {
            dvsDownsamplingValue = (Math.round(2 * dvsDownsamplingValue + 1));
            if (dvsDownsamplingValue > 5) {
                dvsDownsamplingValue = 5;
            }
        } else if (skipLess) {
            dvsDownsamplingValue = (int) (0.5f * dvsDownsamplingValue);
            if (dvsDownsamplingValue < 0) {
                dvsDownsamplingValue = 0;
            }
        }
//        System.out.println("downsampling "+dvsDownsamplingValue);
    }
}
