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
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.ChipRendererDisplayMethod;
import net.sf.jaer.util.histogram.SimpleHistogram;
import eu.seebetter.ini.chips.DavisChip;

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

    /**
     * PropertyChange
     */
    public static final String AGC_VALUES = "AGCValuesChanged";

    public DavisRGBW640Renderer(AEChip chip) {
        super(chip);
        if (chip.getNumPixels() == 0) {
            log.warning("chip has zero pixels; is the constuctor of AEFrameChipRenderer called before size of the AEChip is set?");
            return;
        }
        setAGCTauMs(chip.getPrefs().getFloat("agcTauMs", 1000));
        onColor = new float[4];
        offColor = new float[4];
        checkPixmapAllocation();
//        resetFrame(0.5f);
//        resetAnnotationFrame(0.0f); // don't call here because it depends on knowing desired rendering state, which requires chip configuration, which might not be set yet
    }

    @Override
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
            } else if (!backwards && isAdcSampleFlag && displayFrames && !paused) { // TODO need to handle single step updates here
                updateFrameBuffer(e);
            }
        }
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
    //@Override
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
            buf[index + 1] = val;
            buf[index + 2] = val;
        } else if (e.isSignalRead()) {
            int index = getIndex(e);
            if ((index < 0) || (index >= buf.length)) {
                return;
            }
            int val = e.getAdcSample();
            buf2[index] = val;
            buf2[index + 1] = val;
            buf2[index + 2] = val;
        } else if (e.isCpResetRead()) {
            int index = getIndex(e);
            if ((index < 0) || (index >= buf.length)) {
                return;
            }
            int val = 0;
            if (e.getColorFilter() == ApsDvsEventRGBW.ColorFilter.W) {
                val = ((int)buf[index] - (int)buf2[index])+(int)(7.35/2.13)*(e.getAdcSample()-(int)buf2[index]); 
                //(Vreset-Vsignal)+C*(Vcpreset-Vsiganl), C=7.35/2.13
            } else {
                val = ((int)buf[index] - (int)buf2[index]);
            }
            if (val < minValue) {
                minValue = val;
            } else if (val > maxValue) {
                maxValue = val;
            }
            if (computeHistograms) {
                nextHist.add(val);
            }
            float fval = normalizeFramePixel(val);
            fval=.5f;
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

    /**
     * returns code that says whether this ADC sample event is RGB or White
     * pixel
     *
     * @param e
     * @return int 0-3 encoding sample type
     */
    private int rgbwSampleType(ApsDvsEvent e) {
        return 0; // TODO fix for actual x,y RBBW mapping
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
//           System.out.println("offset="+offset+" range="+range+" value="+value+" v="+v);
        }
        if (v < 0) {
            v = 0;
        } else if (v > 1) {
            v = 1;
        }
        return v;
    }

}
