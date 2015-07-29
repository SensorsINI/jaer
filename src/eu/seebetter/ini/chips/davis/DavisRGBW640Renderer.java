/*
 * ChipRenderer.java
 *
 * Created on May 2, 2006, 1:49 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import java.nio.FloatBuffer;
import java.util.Iterator;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.ApsDvsEventRGBW;
import net.sf.jaer.event.ApsDvsEventRGBW.ColorFilter;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.ChipRendererDisplayMethod;
import net.sf.jaer.util.histogram.SimpleHistogram;
import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.orientation.OrientationEventInterface;
import static net.sf.jaer.graphics.AEChipRenderer.NUM_TIME_COLORS;

/**
 * Class adapted from AEFrameChipRenderer to render CDAVIS=rgbDAVIS output.
 *
 * The frame buffer is RGBA so four bytes per pixel. The rendering uses a
 * texture which is a power of two multiple of image size, so watch out for
 * getWidth and getHeight; they return this value and not the number of pixels
 * being rendered from the chip.
 *
 * @author christian, tobi
 * @see ChipRendererDisplayMethod
 */
public class DavisRGBW640Renderer extends AEFrameChipRenderer {

    protected FloatBuffer pixBuffer2;

    public DavisRGBW640Renderer(AEChip chip) {
        super(chip);
        if (chip.getNumPixels() == 0) {
            log.warning("chip has zero pixels; is the constuctor of AEFrameChipRenderer called before size of the AEChip is set?");
            return;
        }
        onColor = new float[4];
        offColor = new float[4];
        checkPixmapAllocation();
        // resetFrame(0.5f);
        // resetAnnotationFrame(0.0f); // don't call here because it depends on knowing desired rendering state, which
        // requires chip configuration, which might not be set yet
    }

    @Override
    protected void renderApsDvsEvents(EventPacket pkt) {

        if (getChip() instanceof DavisBaseCamera) {
            computeHistograms = ((DavisBaseCamera) chip).isShowImageHistogram()
                    || ((DavisChip) chip).isAutoExposureEnabled();
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
                log.warning("wrong input event class, got " + packet.getEventPrototype() + " but we need to have "
                        + ApsDvsEvent.class);
            }
            return;
        }
        boolean displayEvents = isDisplayEvents(), displayFrames = isDisplayFrames(), paused = chip.getAeViewer()
                .isPaused(), backwards = packet.getDurationUs() < 0;

        Iterator allItr = packet.fullIterator();
        setSpecialCount(0);
        while (allItr.hasNext()) {
            // The iterator only iterates over the DVS events
            ApsDvsEventRGBW e = (ApsDvsEventRGBW) allItr.next();
            if (e.isSpecial()) {
                setSpecialCount(specialCount + 1); // TODO optimize special count increment
                continue;
            }
            int type = e.getType();
            boolean isAdcSampleFlag = e.isSampleEvent();
            if (!isAdcSampleFlag) {
                if (displayEvents) {
                    if ((xsel >= 0) && (ysel >= 0)) { // find correct mouse pixel interpretation to make sounds for
                        // large pixels
                        int xs = (xsel >>> 1) << 1, ys = (ysel >>> 1) << 1;
                        if ((e.x == xs) && (e.y == ys)) {
                            playSpike(type);
                        }
                    }
                    updateEventMaps(e);
                }
            } else if (!backwards && isAdcSampleFlag && displayFrames && !paused) { // TODO need to handle single step
                // updates here
                updateFrameBuffer(e);
            }
        }
    }

    @Override
    protected void updateEventMaps(PolarityEvent e) {
        float[] map;
        int index = getIndex(e);
        boolean fill = !isSeparateAPSByColor();
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
                if (fill) {
                    map[getPixMapIndex(e.x + 1, e.y)] = 1.0f;
                    map[getPixMapIndex(e.x + 1, e.y) + 1] = 1.0f;
                    map[getPixMapIndex(e.x + 1, e.y) + 2] = 1.0f;
                    map[getPixMapIndex(e.x, e.y + 1)] = 1.0f;
                    map[getPixMapIndex(e.x, e.y + 1) + 1] = 1.0f;
                    map[getPixMapIndex(e.x, e.y + 1) + 2] = 1.0f;
                    map[getPixMapIndex(e.x + 1, e.y + 1)] = 1.0f;
                    map[getPixMapIndex(e.x + 1, e.y + 1) + 1] = 1.0f;
                    map[getPixMapIndex(e.x + 1, e.y + 1) + 2] = 1.0f;
                }
            } else {
                // if color scale is 1, then last value is used as the pixel value, which quantizes the color to full scale.
                map[ind] = c[0]; //if(f[0]>1f) f[0]=1f;
                map[ind + 1] = c[1]; //if(f[1]>1f) f[1]=1f;
                map[ind + 2] = c[2]; //if(f[2]>1f) f[2]=1f;
                if (fill) {
                    map[getPixMapIndex(e.x + 1, e.y)] = c[0];
                    map[getPixMapIndex(e.x + 1, e.y) + 1] = c[1];
                    map[getPixMapIndex(e.x + 1, e.y) + 2] = c[2];
                    map[getPixMapIndex(e.x, e.y + 1)] = c[0];
                    map[getPixMapIndex(e.x, e.y + 1) + 1] = c[1];
                    map[getPixMapIndex(e.x, e.y + 1) + 2] = c[2];
                    map[getPixMapIndex(e.x + 1, e.y + 1)] = c[0];
                    map[getPixMapIndex(e.x + 1, e.y + 1) + 1] = c[1];
                    map[getPixMapIndex(e.x + 1, e.y + 1) + 2] = c[2];
                }
            }
            map[index + 3] += alpha;
            if (fill) {
                map[getPixMapIndex(e.x + 1, e.y) + 3] += alpha;
                map[getPixMapIndex(e.x, e.y + 1) + 3] += alpha;
                map[getPixMapIndex(e.x + 1, e.y + 1) + 3] += alpha;
            }
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
            if (fill) {
                map[getPixMapIndex(e.x + 1, e.y)] = timeColors[ind][0];
                map[getPixMapIndex(e.x + 1, e.y) + 1] = timeColors[ind][1];
                map[getPixMapIndex(e.x + 1, e.y) + 2] = timeColors[ind][2];
                map[getPixMapIndex(e.x, e.y + 1)] = timeColors[ind][0];
                map[getPixMapIndex(e.x, e.y + 1) + 1] = timeColors[ind][1];
                map[getPixMapIndex(e.x, e.y + 1) + 2] = timeColors[ind][2];
                map[getPixMapIndex(e.x + 1, e.y + 1)] = timeColors[ind][0];
                map[getPixMapIndex(e.x + 1, e.y + 1) + 1] = timeColors[ind][1];
                map[getPixMapIndex(e.x + 1, e.y + 1) + 2] = timeColors[ind][2];
                map[getPixMapIndex(e.x + 1, e.y) + 3] = 0.5f;
                map[getPixMapIndex(e.x, e.y + 1) + 3] = 0.5f;
                map[getPixMapIndex(e.x + 1, e.y + 1) + 3] = 0.5f;
            }
        } else if (colorMode == ColorMode.GrayTime) {
            int ts0 = packet.getFirstTimestamp();
            float dt = packet.getDurationUs();
            float v = 0.95f - (0.95f * ((e.timestamp - ts0) / dt));
            map[index] = v;
            map[index + 1] = v;
            map[index + 2] = v;
            map[index + 3] = 1.0f;
            if (fill) {
                map[getPixMapIndex(e.x + 1, e.y)] = v;
                map[getPixMapIndex(e.x + 1, e.y) + 1] = v;
                map[getPixMapIndex(e.x + 1, e.y) + 2] = v;
                map[getPixMapIndex(e.x, e.y + 1)] = v;
                map[getPixMapIndex(e.x, e.y + 1) + 1] = v;
                map[getPixMapIndex(e.x, e.y + 1) + 2] = v;
                map[getPixMapIndex(e.x + 1, e.y + 1)] = v;
                map[getPixMapIndex(e.x + 1, e.y + 1) + 1] = v;
                map[getPixMapIndex(e.x + 1, e.y + 1) + 2] = v;
                map[getPixMapIndex(e.x + 1, e.y) + 3] = 1.0f;
                map[getPixMapIndex(e.x, e.y + 1) + 3] = 1.0f;
                map[getPixMapIndex(e.x + 1, e.y + 1) + 3] = 1.0f;
            }
        } else {
            float alpha = map[index + 3] + (1.0f / colorScale);
            alpha = normalizeEvent(alpha);
            if ((e.polarity == PolarityEvent.Polarity.On) || ignorePolarityEnabled) {
                map[index] = onColor[0];
                map[index + 1] = onColor[1];
                map[index + 2] = onColor[2];
                if (fill) {
                    map[getPixMapIndex(e.x + 1, e.y)] = onColor[0];
                    map[getPixMapIndex(e.x + 1, e.y) + 1] = onColor[1];
                    map[getPixMapIndex(e.x + 1, e.y) + 2] = onColor[2];
                    map[getPixMapIndex(e.x, e.y + 1)] = onColor[0];
                    map[getPixMapIndex(e.x, e.y + 1) + 1] = onColor[1];
                    map[getPixMapIndex(e.x, e.y + 1) + 2] = onColor[2];
                    map[getPixMapIndex(e.x + 1, e.y + 1)] = onColor[0];
                    map[getPixMapIndex(e.x + 1, e.y + 1) + 1] = onColor[1];
                    map[getPixMapIndex(e.x + 1, e.y + 1) + 2] = onColor[2];
                }
            } else {
                map[index] = offColor[0];
                map[index + 1] = offColor[1];
                map[index + 2] = offColor[2];
                if (fill) {
                    map[getPixMapIndex(e.x + 1, e.y)] = offColor[0];
                    map[getPixMapIndex(e.x + 1, e.y) + 1] = offColor[1];
                    map[getPixMapIndex(e.x + 1, e.y) + 2] = offColor[2];
                    map[getPixMapIndex(e.x, e.y + 1)] = offColor[0];
                    map[getPixMapIndex(e.x, e.y + 1) + 1] = offColor[1];
                    map[getPixMapIndex(e.x, e.y + 1) + 2] = offColor[2];
                    map[getPixMapIndex(e.x + 1, e.y + 1)] = offColor[0];
                    map[getPixMapIndex(e.x + 1, e.y + 1) + 1] = offColor[1];
                    map[getPixMapIndex(e.x + 1, e.y + 1) + 2] = offColor[2];
                }
            }
            map[index + 3] = alpha;
            if (fill) {
                map[getPixMapIndex(e.x + 1, e.y) + 3] = alpha;
                map[getPixMapIndex(e.x, e.y + 1) + 3] = alpha;
                map[getPixMapIndex(e.x + 1, e.y + 1) + 3] = alpha;
            }
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

    @Override
    protected void checkPixmapAllocation() {
        super.checkPixmapAllocation();

        final int n = 4 * textureWidth * textureHeight;
        if ((pixBuffer2 == null) || (pixBuffer2.capacity() < n)) {
            pixBuffer2 = FloatBuffer.allocate(n);
        }
    }

    /**
     * Overridden to do CDAVIS rendering
     *
     * @param e the ADC sample event
     */
    // @Override
    protected void updateFrameBuffer(ApsDvsEventRGBW e) {
        float[] buf = pixBuffer.array();
        float[] buf2 = pixBuffer2.array();

        // TODO if playing backwards, then frame will come out white because B sample comes before A
        if (e.isStartOfFrame()) {
            startFrame(e.timestamp);
        } else if (e.isResetRead()) {
            int index = getIndex(e);
            if ((index < 0) || (index >= buf.length)) {
                return;
            }
            int val = e.getAdcSample();
            buf[index] = val;
            // buf[index + 1] = val;
            // buf[index + 2] = val;
        } else if (e.isSignalRead()) {
            int index = getIndex(e);
            if ((index < 0) || (index >= buf.length)) {
                return;
            }
            int val = e.getAdcSample();
            buf2[index] = val;
            // buf2[index + 1] = val;
            // buf2[index + 2] = val;
            //val = (int) (buf[index] - buf2[index]);
            //if (val < minValue) {
            //    minValue = val;
            //} else if (val > maxValue) {
            //    maxValue = val;
            //}
            //if (computeHistograms) {
            //    nextHist.add(val);
            //}
            //float fval = normalizeFramePixel(val);
            //buf[index] = fval;
            //buf[index + 1] = fval;
            //buf[index + 2] = fval;
            //buf[index + 3] = 1;
        } else if (e.isCpResetRead()) {
            int index = getIndex(e);
            if ((index < 0) || (index >= buf.length)) {
                return;
            }
            int val = 0;
            if (e.getColorFilter() == ApsDvsEventRGBW.ColorFilter.W) {
                val = (int) ((buf[index] - buf2[index]) + ((7.35f / 2.13f) * (e.getAdcSample() - buf2[index])));
                // (Vreset-Vsignal)+C*(Vcpreset-Vsignal), C=7.35/2.13
            } else {
                val = (int) (buf[index] - buf2[index]);
            }
            if (val < minValue) {
                minValue = val;
            } else if (val > maxValue) {
                maxValue = val;
            }
            if (computeHistograms && e.getColorFilter()==ColorFilter.W) {
                nextHist.add(val);
            }
            float fval = normalizeFramePixel(val);
            buf[index] = fval;
            buf[index + 1] = fval;
            buf[index + 2] = fval;
            buf[index + 3] = 1;
        } else if (e.isEndOfFrame()) {
            endFrame(e.timestamp);
            SimpleHistogram tmp = currentHist;
            if (computeHistograms) {
                currentHist = nextHist;
                nextHist = tmp;
                nextHist.reset();
            }
            ((DavisChip) chip).controlExposure();

        }
    }

    /**
     * returns code that says whether this ADC sample event is RGB or White
     * pixel
     *
     * @param e
     * @return int 0-3 encoding sample type
     */
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
        float v;
        if (!isUseAutoContrast()) { // fixed rendering computed here
            float gamma = getGamma();
            if (gamma == 1.0f) {
                v = ((getContrast() * value) + getBrightness()) / maxADC;
            } else {
                v = (float) (Math.pow((((getContrast() * value) + getBrightness()) / maxADC), gamma));
            }
        } else {
            java.awt.geom.Point2D.Float filter2d = autoContrast2DLowpassRangeFilter.getValue2d();
            float offset = filter2d.x;
            float range = (filter2d.y - filter2d.x);
            v = ((value - offset)) / (range);
            // System.out.println("offset="+offset+" range="+range+" value="+value+" v="+v);
        }
        if (v < 0) {
            v = 0;
        } else if (v > 1) {
            v = 1;
        }
        return v;
    }

    protected int getIndex(int x, int y) {
        return 4 * (x + (y * textureWidth));
    }

    /**
     * Returns index into pixmap according to separateAPSByColor flag
     *
     * @param x
     * @param y
     * @param color
     * @return the index
     */
    @Override
    protected int getIndex(BasicEvent e) {
        int x = e.x, y = e.y;

        if ((x < 0) || (y < 0) || (x >= sizeX) || (y >= sizeY)) {
            if ((System.currentTimeMillis() - lastWarningPrintedTimeMs) > INTERVAL_BETWEEEN_OUT_OF_BOUNDS_EXCEPTIONS_PRINTED_MS) {
                log.warning(String
                        .format(
                                "Event with x=%d y=%d out of bounds and cannot be rendered in bounds sizeX=%d sizeY=%d - delaying next warning for %dms",
                                x, y, sizeX, sizeY, INTERVAL_BETWEEEN_OUT_OF_BOUNDS_EXCEPTIONS_PRINTED_MS));
                lastWarningPrintedTimeMs = System.currentTimeMillis();
            }
            return -1;
        }
        if (isSeparateAPSByColor()) {
            ColorFilter color = ((ApsDvsEventRGBW) e).getColorFilter();

            if (color == ColorFilter.G) {
                x = x / 2;
                y = (y / 2) + 240;
            } else if (color == ColorFilter.R) {
                x = (x / 2) + 320;
                y = (y / 2) + 240;
            } else if (color == ColorFilter.W) {
                x = (x / 2) + 320;
                y = y / 2;
            } else { // B
                x = x / 2;
                y = y / 2;
            }
        }
        return 4 * (x + (y * textureWidth));
    }

    public boolean isSeparateAPSByColor() {
        return ((DavisDisplayConfigInterface) chip.getBiasgen()).isSeparateAPSByColor();
    }

    @Override
    protected void endFrame(int ts) {
        if (!isSeparateAPSByColor()) {
            //color interpolation
            float[] image = pixBuffer.array();
            for (int y = 0; y < chip.getSizeY(); y++) {
                for (int x = 0; x < chip.getSizeX(); x++) {
                    if ((y % 2) == 0) {
                        //row 0, 2, 4 ... 478, from bottom of the image, contianing W and B
                        if ((x % 2) == 1) { //W
                            //interpolating R for W
                            if (y == 0) {
                                //bottom egde of W
                                image[getIndex(x, y)] = image[getIndex(x, y + 1)];
                            } else {
                                //rest of W
                                image[getIndex(x, y)] = 0.5f * (image[getIndex(x, y + 1)] + image[getIndex(x, y - 1)]);
                            }
                            //interpolating B for W
                            if (x == chip.getSizeX() - 1) {
                                //right edge of W
                                image[getIndex(x, y) + 2] = image[getIndex(x - 1, y) + 2];
                            } else {
                                //rest of W
                                image[getIndex(x, y) + 2] = 0.5f * (image[getIndex(x - 1, y) + 2] + image[getIndex(x + 1, y) + 2]);
                            }
                            //interpolating G for W
                            if (y == 0) {
                                //bottom edge of W
                                if (x == chip.getSizeX() - 1) {
                                    //bottom right corner of W
                                    image[getIndex(x, y) + 1] = image[getIndex(x - 1, y + 1) + 1];
                                } else {
                                    //rest of the bottom edge of W
                                    image[getIndex(x, y) + 1] = 0.5f * (image[getIndex(x + 1, y + 1) + 1] + image[getIndex(x - 1, y + 1) + 1]);
                                }
                            } else if (x == chip.getSizeX() - 1) {
                                //right edge of W excluding bottom right corner
                                image[getIndex(x, y) + 1] = 0.5f * (image[getIndex(x - 1, y + 1) + 1] + image[getIndex(x - 1, y - 1) + 1]);
                            } else {
                                // rest of W
                                image[getIndex(x, y) + 1] = 0.25f * (image[getIndex(x + 1, y + 1) + 1] + image[getIndex(x + 1, y - 1) + 1]
                                        + image[getIndex(x - 1, y + 1) + 1] + image[getIndex(x - 1, y - 1) + 1]);
                            }
                        } else { //B
                            //interpolating R for B
                            if (y == 0) {
                                //bottom edge of B
                                if (x == 0) {
                                    //bottom left corner of B
                                    image[getIndex(x, y)] = image[getIndex(x + 1, y + 1)];
                                } else {
                                    //rest of the bottom edge of B
                                    image[getIndex(x, y)] = 0.5f * (image[getIndex(x - 1, y + 1)] + image[getIndex(x + 1, y + 1)]);
                                }
                            } else if (x == 0) {
                                //left edge of B excluding bottom left corner
                                image[getIndex(x, y)] = 0.5f * (image[getIndex(x + 1, y + 1)] + image[getIndex(x + 1, y - 1)]);
                            } else {
                                // rest of B
                                image[getIndex(x, y)] = 0.25f * (image[getIndex(x - 1, y - 1)] + image[getIndex(x - 1, y + 1)]
                                        + image[getIndex(x + 1, y - 1)] + image[getIndex(x + 1, y + 1)]);
                            }
                            //interpolating G for B
                            if (y == 0) {
                                //bottom egde of B
                                image[getIndex(x, y) + 1] = image[getIndex(x, y + 1) + 1];
                            } else {
                                //rest of B
                                image[getIndex(x, y) + 1] = 0.5f * (image[getIndex(x, y - 1) + 1] + image[getIndex(x, y + 1) + 1]);
                            }
                        }
                    } else {
                        //row 1, 3, 5 ... 479, from bottom of the image, contianing R and G
                        if ((x % 2) == 1) { //R
                            //interpolation B for R
                            if (y == chip.getSizeY() - 1) {
                                //top edge of R
                                if (x == chip.getSizeX() - 1) {
                                    //top right corner of R
                                    image[getIndex(x, y) + 2] = image[getIndex(x - 1, y - 1) + 2];
                                } else {
                                    //rest of the top edge of R
                                    image[getIndex(x, y) + 2] = 0.5f * (image[getIndex(x - 1, y - 1) + 2] + image[getIndex(x + 1, y - 1) + 2]);
                                }
                            } else if (x == chip.getSizeX() - 1) {
                                //right edge of R excluding top right corner
                                image[getIndex(x, y) + 2] = 0.5f * (image[getIndex(x - 1, y + 1) + 2] + image[getIndex(x - 1, y - 1) + 2]);
                            } else {
                                // rest of R
                                image[getIndex(x, y) + 2] = 0.25f * (image[getIndex(x - 1, y - 1) + 2] + image[getIndex(x - 1, y + 1) + 2]
                                        + image[getIndex(x + 1, y - 1) + 2] + image[getIndex(x + 1, y + 1) + 2]);
                            }
                            //interpolating G for R
                            if (x == chip.getSizeX() - 1) {
                                //right egde of R
                                image[getIndex(x, y) + 1] = image[getIndex(x - 1, y) + 1];
                            } else {
                                //rest of R
                                image[getIndex(x, y) + 1] = 0.5f * (image[getIndex(x - 1, y) + 1] + image[getIndex(x + 1, y) + 1]);
                            }
                        } else { //G
                            //interpolating R for G
                            if (x == 0) {
                                //left egde of G
                                image[getIndex(x, y)] = image[getIndex(x + 1, y)];
                            } else {
                                //rest of G
                                image[getIndex(x, y)] = 0.5f * (image[getIndex(x - 1, y)] + image[getIndex(x + 1, y)]);
                            }
                            //interpolating B for G
                            if (y == chip.getSizeY() - 1) {
                                //top egde of G
                                image[getIndex(x, y) + 2] = image[getIndex(x, y - 1) + 2];
                            } else {
                                //rest of G
                                image[getIndex(x, y) + 2] = 0.5f * (image[getIndex(x, y - 1) + 2] + image[getIndex(x, y + 1) + 2]);
                            }
                        }
                    }
                    image[getIndex(x, y) + 3] = 1;
                }
            }
            System.arraycopy(pixBuffer.array(), 0, pixmap.array(), 0, pixBuffer.array().length);
        } else {
            System.arraycopy(pixBuffer.array(), 0, pixmap.array(), 0, pixBuffer.array().length);
        }
        if (contrastController != null) {
            contrastController.endFrame(minValue, maxValue, timestampFrameStart);
        }
        getSupport().firePropertyChange(EVENT_NEW_FRAME_AVAILBLE, null, this); // TODO document what is sent and send something reasonable
    }
}
