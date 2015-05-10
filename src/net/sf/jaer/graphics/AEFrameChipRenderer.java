/*
 * ChipRenderer.java
 *
 * Created on May 2, 2006, 1:49 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package net.sf.jaer.graphics;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Iterator;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.orientation.OrientationEventInterface;
import net.sf.jaer.util.filter.LowpassFilter2d;
import net.sf.jaer.util.histogram.SimpleHistogram;
import ch.unizh.ini.jaer.chip.retina.DvsDisplayConfigInterface;
import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.DAVIS240BaseCamera;
import eu.seebetter.ini.chips.davis.DavisAutoShooter;
import eu.seebetter.ini.chips.davis.DavisVideoContrastController;
import java.beans.PropertyChangeEvent;

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
    public int textureWidth; //due to hardware acceloration reasons, has to be a 2^x with x a natural number
    public int textureHeight; //due to hardware acceloration reasons, has to be a 2^x with x a natural number

    /**
     * Fields used to reduce method calls
     */
    protected int sizeX, sizeY, maxADC, numEventTypes;

    /**
     * Used to mark time of frame event
     */
    protected int timestamp = 0;
    /**
     * low pass temporal filter that computes time-averaged min and max gray
     * values
     */
    protected LowpassFilter2d autoContrast2DLowpassRangeFilter = new LowpassFilter2d();  // 2 lp values are min and max log intensities from each frame
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
    protected FloatBuffer annotateMap;
    // double buffered histogram so we can accumulate new histogram while old one is still being rendered and returned to caller
    private final int histStep = 4; // histogram bin step in ADC counts of 1024 levels
    private SimpleHistogram adcSampleValueHistogram1 = new SimpleHistogram(0, histStep, (DavisChip.MAX_ADC + 1) / histStep, 0);
    private SimpleHistogram adcSampleValueHistogram2 = new SimpleHistogram(0, histStep, (DavisChip.MAX_ADC + 1) / histStep, 0);
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

    public AEFrameChipRenderer(AEChip chip) {
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
    protected void resetPixmapGrayLevel(float value) {
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
        //Fill maps with fully transparent values
        Arrays.fill(grayBuffer.array(), 0.0f);
        System.arraycopy(grayBuffer.array(), 0, onMap.array(), 0, n);
        System.arraycopy(grayBuffer.array(), 0, offMap.array(), 0, n);
//       if(displayAnnotation) Arrays.fill(annotateMap.array(), 0);

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
    public synchronized void resetAnnotationFrame(float resetValue) {
        checkPixmapAllocation();
        final int n = 4 * textureWidth * textureHeight;
        if ((grayBuffer == null) || (grayBuffer.capacity() != n)) {
            grayBuffer = FloatBuffer.allocate(n); // BufferUtil.newFloatBuffer(n);
        }

        grayBuffer.rewind();
        //Fill maps with fully transparent values
        Arrays.fill(grayBuffer.array(), resetValue);
        System.arraycopy(grayBuffer.array(), 0, annotateMap.array(), 0, n);

        grayBuffer.rewind();
        annotateMap.rewind();
        annotateMap.limit(n);
    }

    @Override
    public synchronized void setColorMode(ColorMode colorMode) {
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
    public synchronized void render(EventPacket pkt) {

        if (!addedPropertyChangeListener) {
            if (chip instanceof AEChip) {
                AEChip aeChip = chip;
                if (aeChip.getAeViewer() != null) {
                    aeChip.getAeViewer().addPropertyChangeListener(this);
                    addedPropertyChangeListener = true;
                }
            }
        }
        numEventTypes = pkt.getNumCellTypes();
        if (pkt instanceof ApsDvsEventPacket) {
            renderApsDvsEvents(pkt);
        } else {
            renderDvsEvents(pkt);
        }
    }

    protected void renderApsDvsEvents(EventPacket pkt) {

        if (getChip() instanceof DAVIS240BaseCamera) {
            computeHistograms = ((DAVIS240BaseCamera) chip).isShowImageHistogram() || ((DavisChip) chip).isAutoExposureEnabled();
        }

        if (!accumulateEnabled) {
            resetMaps();
            if (numEventTypes > 2) {
                resetAnnotationFrame(0.0f);
            }
        }
        ApsDvsEventPacket packet = (ApsDvsEventPacket) pkt;

        checkPixmapAllocation();
        resetSelectedPixelEventCount(); // TODO fix locating pixel with xsel ysel

        this.packet = packet;
        if (!(packet.getEventPrototype() instanceof ApsDvsEvent)) {
            if ((warningCount++ % WARNING_INTERVAL) == 0) {
                log.warning("wrong input event class, got " + packet.getEventPrototype() + " but we need to have " + ApsDvsEvent.class);
            }
            return;
        }
        boolean displayEvents = isDisplayEvents(),
                displayFrames = isDisplayFrames(),
                paused = chip.getAeViewer().isPaused(), backwards = packet.getDurationUs() < 0;

        Iterator allItr = packet.fullIterator();
        setSpecialCount(0);
        while (allItr.hasNext()) {
            //The iterator only iterates over the DVS events
            ApsDvsEvent e = (ApsDvsEvent) allItr.next();
            if (e.isSpecial()) {
                setSpecialCount(specialCount + 1); // TODO optimize special count increment
                continue;
            }
            int type = e.getType();
            boolean isAdcSampleFlag = e.isSampleEvent();
            if (!isAdcSampleFlag) {
                if (displayEvents) {
                    if ((xsel >= 0) && (ysel >= 0)) { // find correct mouse pixel interpretation to make sounds for large pixels
                        int xs = xsel, ys = ysel;
                        if ((e.x == xs) && (e.y == ys)) {
                            playSpike(type);
                        }
                    }
                    updateEventMaps(e);
                }
            } else if (!backwards && isAdcSampleFlag && displayFrames) { // TODO need to handle single step updates here
                updateFrameBuffer(e);
            }
        }
    }

    protected void renderDvsEvents(EventPacket pkt) {
        checkPixmapAllocation();
        resetSelectedPixelEventCount(); // TODO fix locating pixel with xsel ysel

        if (!accumulateEnabled) {
            resetMaps();
            if (numEventTypes > 2) {
                resetAnnotationFrame(0.0f);
            }
        }

        setSpecialCount(0);
        this.packet = pkt;
        Iterator itr = packet.inputIterator();
        while (itr.hasNext()) {
            //The iterator only iterates over the DVS events
            PolarityEvent e = (PolarityEvent) itr.next();
            if (e.isSpecial()) {
                setSpecialCount(specialCount + 1); // TODO optimize special count increment
                continue;
            }
            int type = e.getType();
            if ((xsel >= 0) && (ysel >= 0)) { // find correct mouse pixel interpretation to make sounds for large pixels
                int xs = xsel, ys = ysel;
                if ((e.x == xs) && (e.y == ys)) {
                    playSpike(type);
                }
            }
            updateEventMaps(e);
        }
    }

    protected void updateFrameBuffer(ApsDvsEvent e) {
        float[] buf = pixBuffer.array();
        // TODO if playing backwards, then frame will come out white because B sample comes before A
        if (e.isStartOfFrame()) {
            startFrame(e.timestamp);
        } else if (e.isResetRead()) {
            int index = getIndex(e);
            if ((index < 0) || (index >= buf.length)) {
                return;
            }
            float val = e.getAdcSample();
            buf[index] = val;
            buf[index + 1] = val;
            buf[index + 2] = val;
        } else if (e.isSignalRead()) {
            int index = getIndex(e);
            if ((index < 0) || (index >= buf.length)) {
                return;
            }
            int val = ((int) buf[index] - e.getAdcSample());
            if ((val >= 0) && (val < minValue)) { // tobi only update min if it is >0, to deal with sensors with bad column read, like 240C
                minValue = val;
            } else if (val > maxValue) {
                maxValue = val;
            }
            // right here sample-reset value of this pixel is in val

            if (computeHistograms) {
                nextHist.add(val);
            }
            float fval = normalizeFramePixel(val);
//            fval=.5f;
            buf[index] = fval;
            buf[index + 1] = fval;
            buf[index + 2] = fval;
            buf[index + 3] = 1;
        } else if (e.isEndOfFrame()) {
            endFrame();
            SimpleHistogram tmp = currentHist;
            if (computeHistograms) {
                currentHist = nextHist;
                nextHist = tmp;
                nextHist.reset();
            }
            ((DavisChip) chip).controlExposure();

        }
    }

    protected void startFrame(int ts) {
        timestamp = ts;
        maxValue = Float.MIN_VALUE;
        minValue = Float.MAX_VALUE;
        System.arraycopy(grayBuffer.array(), 0, pixBuffer.array(), 0, pixBuffer.array().length);

    }

    protected void endFrame() {
        System.arraycopy(pixBuffer.array(), 0, pixmap.array(), 0, pixBuffer.array().length);
        if (contrastController != null && minValue != Float.MAX_VALUE && maxValue != Float.MIN_VALUE) {
            contrastController.endFrame(minValue, maxValue, timestamp);
        }
        getSupport().firePropertyChange(EVENT_NEW_FRAME_AVAILBLE, null, this); // TODO document what is sent and send something reasonable
    }

    /**
     * changes alpha of ON map
     *
     * @param index 0-(size of pixel array-1) of pixel
     */
    protected void updateEventMaps(PolarityEvent e) {
        float[] map;
        int index = getIndex(e);
        if (packet.getNumCellTypes() > 2) {
            map = onMap.array();
        } else if (e.polarity == ApsDvsEvent.Polarity.On) {
            map = onMap.array();
        } else {
            map = offMap.array();
        }
        if ((index < 0) || (index >= map.length)) {
            return;
        }
        if (packet.getNumCellTypes() > 2) {
            checkTypeColors(packet.getNumCellTypes());
            if (e.special) {
                setSpecialCount(specialCount + 1); // TODO optimize special count increment
                return;
            }
            int type = e.getType();
            if ((e.x == xsel) && (e.y == ysel)) {
                playSpike(type);
            }
            int ind = getPixMapIndex(e.x, e.y);
            float[] c = typeColorRGBComponents[type];
            float alpha = map[index + 3] + (1.0f / colorScale);
            alpha = normalizeEvent(alpha);
            if ((e instanceof OrientationEventInterface) && (((OrientationEventInterface) e).isHasOrientation() == false)) {
                // if event is orientation event but orientation was not set, just draw as gray level
                map[ind] = 1.0f; //if(f[0]>1f) f[0]=1f;
                map[ind + 1] = 1.0f; //if(f[1]>1f) f[1]=1f;
                map[ind + 2] = 1.0f; //if(f[2]>1f) f[2]=1f;
            } else {
                // if color scale is 1, then last value is used as the pixel value, which quantizes the color to full scale.
                map[ind] = c[0]; //if(f[0]>1f) f[0]=1f;
                map[ind + 1] = c[1]; //if(f[1]>1f) f[1]=1f;
                map[ind + 2] = c[2]; //if(f[2]>1f) f[2]=1f;
            }
            map[index + 3] += alpha;
        } else if (colorMode == ColorMode.ColorTime) {
            int ts0 = packet.getFirstTimestamp();
            float dt = packet.getDurationUs();
            int ind = (int) Math.floor(((NUM_TIME_COLORS - 1) * (e.timestamp - ts0)) / dt);
            if (ind < 0) {
                ind = 0;
            } else if (ind >= timeColors.length) {
                ind = timeColors.length - 1;
            }
            map[index] = timeColors[ind][0];
            map[index + 1] = timeColors[ind][1];
            map[index + 2] = timeColors[ind][2];
            map[index + 3] = 0.5f;
        } else if (colorMode == ColorMode.GrayTime) {
            int ts0 = packet.getFirstTimestamp();
            float dt = packet.getDurationUs();
            float v = 0.95f - (0.95f * ((e.timestamp - ts0) / dt));
            map[index] = v;
            map[index + 1] = v;
            map[index + 2] = v;
            map[index + 3] = 1.0f;
        } else {
            float alpha = map[index + 3] + (1.0f / colorScale);
            alpha = normalizeEvent(alpha);
            if ((e.polarity == PolarityEvent.Polarity.On) || ignorePolarityEnabled) {
                map[index] = onColor[0];
                map[index + 1] = onColor[1];
                map[index + 2] = onColor[2];
            } else {
                map[index] = offColor[0];
                map[index + 1] = offColor[1];
                map[index + 2] = offColor[2];
            }
            map[index + 3] = alpha;
        }
    }

    protected final int INTERVAL_BETWEEEN_OUT_OF_BOUNDS_EXCEPTIONS_PRINTED_MS = 1000;
    protected long lastWarningPrintedTimeMs = Integer.MAX_VALUE;

    /**
     * Returns index to R (red) value in RGBA pixmap
     *
     * @param e
     * @return index to red entry in RGBA pixmap
     */
    protected int getIndex(BasicEvent e) {
        int x = e.x, y = e.y;
        if ((x < 0) || (y < 0) || (x >= sizeX) || (y >= sizeY)) {
            if ((System.currentTimeMillis() - lastWarningPrintedTimeMs) > INTERVAL_BETWEEEN_OUT_OF_BOUNDS_EXCEPTIONS_PRINTED_MS) {
                log.warning(String.format("Event %s out of bounds and cannot be rendered in bounds sizeX=%d sizeY=%d - delaying next warning for %dms", e.toString(), sizeX, sizeY, INTERVAL_BETWEEEN_OUT_OF_BOUNDS_EXCEPTIONS_PRINTED_MS));
                lastWarningPrintedTimeMs = System.currentTimeMillis();
            }
            return -1;
        }
        return 4 * (x + (y * textureWidth));
    }

    @Override
    protected void checkPixmapAllocation() {
        if ((sizeX != chip.getSizeX()) || (sizeY != chip.getSizeY())) {
            sizeX = chip.getSizeX();
            textureWidth = ceilingPow2(sizeX);
            sizeY = chip.getSizeY();
            textureHeight = ceilingPow2(sizeY);
        }
        final int n = 4 * textureWidth * textureHeight;
        if ((pixmap == null) || (pixmap.capacity() < n) || (pixBuffer.capacity() < n) || (onMap.capacity() < n) || (offMap.capacity() < n) || (annotateMap.capacity() < n)) {
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
        int old = this.colorScale;
        if (colorScale < 1) {
            colorScale = 1;
        }
        if (colorScale > 128) {
            colorScale = 128;
        }
        this.colorScale = colorScale;
        prefs.putInt("Chip2DRenderer.colorScale", colorScale);
        getSupport().firePropertyChange(EVENT_COLOR_SCALE_CHANGE, old, colorScale);
    }

    /**
     * computes power of two value that is equal to or greater than argument
     *
     * @param n value, e.g. 3
     * @return power of two that is >=n, e.g. 4
     */
    protected static int ceilingPow2(int n) {
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
    public FloatBuffer getOnMap() {
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
    public FloatBuffer getOffMap() {
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
    public FloatBuffer getAnnotateMap() {
        annotateMap.rewind();
        checkPixmapAllocation();
        return annotateMap;
    }

    /**
     * Returns index into pixmap
     *
     * @param x
     * @param y
     * @return the index
     */
    @Override
    public int getPixMapIndex(int x, int y) {
        return 4 * (x + (y * textureWidth));
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
    public float[] getDvsRenderedValuesAtPixel(int x, int y) {
        int k = getPixMapIndex(x, y);
        float[] f = new float[3];
        f[0] = onMap.get(k + 3);
        f[1] = offMap.get(k + 3);
        f[2] = 0; // return alpha channel which is the ON and OFF value that is rendered (RGB are 1 for ON and OFF maps)
        return f; //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * Overridden to combine ON and OFF map values to a gray value by averaging
     * them. Note that this method returns rendering of events; it disregards
     * APS frame values.
     *
     * @param x
     * @param y
     * @return
     */
    public float getApsGrayValueAtPixel(int x, int y) {
        int k = getPixMapIndex(x, y);
        float[] pm = pixmap.array();
        return (pm[k] + pm[k + 1] + pm[k + 2]) / 3;
    }

    /** Sets RGB color components all to same gray value g
     * 
     * @param x pixel x, 0,0 is LL corner
     * @param y pixel y
     * @param g gray value in range 0-1
     */
    public void setApsGrayValueAtPixel(int x, int y, float g) {
        int k = getPixMapIndex(x, y);
        float[] pm = pixmap.array();
        pm[k] = g;
        pm[k + 1] = g;
        pm[k + 2] = g;
    }

    /**
     * Returns the buffer holding the image frame brightness values in RGBA
     * order
     */
    public FloatBuffer getPixBuffer() {
        return pixBuffer;
    }

    /**
     * sets a specific value of the pixmap
     *
     * @param index
     * @param value
     */
    @Override
    public void setAnnotateValue(int index, float value) {
        annotateMap.put(index, value);
    }

    /**
     * sets a specific color (rgb float 0-1) of the pixmap
     *
     * @param index
     * @param value
     */
    @Override
    public void setAnnotateColorRGB(int index, float[] value) {
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
    public void setAnnotateColorRGBA(int index, float[] value) {
        annotateMap.put(index, value[0]);
        annotateMap.put(index + 1, value[1]);
        annotateMap.put(index + 2, value[2]);
        annotateMap.put(index + 3, value[3]);
    }

    /**
     * sets a specific color (rgb float 0-1) of the pixmap
     *
     * @param x
     * @param y
     * @param value
     */
    @Override
    public void setAnnotateColorRGB(int x, int y, float[] value) {
        int index = getPixMapIndex(x, y);
        annotateMap.put(index, value[0]);
        annotateMap.put(index + 1, value[1]);
        annotateMap.put(index + 2, value[2]);
        annotateMap.put(index + 3, getAnnotateAlpha());
    }

    /**
     * sets a specific color (rgb float 0-1) of the pixmap
     *
     * @param x
     * @param y
     * @param value
     */
    public void setAnnotateColorRGBA(int x, int y, float[] value) {
        int index = getPixMapIndex(x, y);
        annotateMap.put(index, value[0]);
        annotateMap.put(index + 1, value[1]);
        annotateMap.put(index + 2, value[2]);
        annotateMap.put(index + 3, value[3]);
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
    private float normalizeFramePixel(float value) {
        if (contrastController != null) {
            return contrastController.normalizePixelGrayValue(value, maxADC);
        } else {
            return 0;
        }
    }

    private float normalizeEvent(float value) {
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

    public void setMaxADC(int max) {
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
        return this.grayValue;
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
     */
    public void setAnnotateAlpha(float annotateAlpha) {
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
    public void setDisplayAnnotation(boolean displayAnnotation) {
        this.displayAnnotation = displayAnnotation;
    }

    /**
     * Sets whether an external renderer adds data to the array and resets it
     *
     * @param extRender
     */
    @Override
    public void setExternalRenderer(boolean extRender) {
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
    public void setContrastController(DavisVideoContrastController contrastController) {
        this.contrastController = contrastController;
    }

    public boolean isUseAutoContrast() {
        return contrastController.isUseAutoContrast();
    }

    public void setUseAutoContrast(boolean useAutoContrast) {
        contrastController.setUseAutoContrast(useAutoContrast);
    }

    public float getContrast() {
        return contrastController.getContrast();
    }

    public void setContrast(float contrast) {
        contrastController.setContrast(contrast);
    }

    public float getBrightness() {
        return contrastController.getBrightness();
    }

    public void setBrightness(float brightness) {
        contrastController.setBrightness(brightness);
    }

    public float getGamma() {
        return contrastController.getGamma();
    }

    public void setGamma(float gamma) {
        contrastController.setGamma(gamma);
    }

    public void setAutoContrastTimeconstantMs(float tauMs) {
        contrastController.setAutoContrastTimeconstantMs(tauMs);
    }

    @Override
    public void propertyChange(PropertyChangeEvent pce) {
        super.propertyChange(pce); //To change body of generated methods, choose Tools | Templates.
        chip.getBiasgen().getSupport().firePropertyChange(pce); // pass on events to chip configuration
    }

}
