/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import java.util.Arrays;

import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.util.filter.LowpassFilter;

/**
 * Subsamples DVS input ON and OFF events to a desired "frame" resolution. By
 * subsampling (accumulation) of events it performs much better than
 * downsampling the sparse DVS output.
 *
 * @author tobi
 */
public class DvsSubsamplerToFrame {

    private final int width; // width of output
    private final int height; // height of output
    private final int nPixels;
    private final float[] pixmap;
    private final int[] eventSum;
    private int colorScale;
    private float colorScaleRecip;
    public final float GRAY_LEVEL = 0.5f;
    private int accumulatedEventCount = 0;
    private int lastTimestamp = Integer.MIN_VALUE;
    private int mostOffCount = Integer.MAX_VALUE, mostOnCount = Integer.MIN_VALUE;
    LowpassFilter frameIntervalFilter = new LowpassFilter(1000);
    private int startTimestamp = 0;
    private boolean cleared = true;
    private int lastIntervalUs = 0;

    /**
     * Makes a new DvsSubsamplingTimesliceConvNetInput
     *
     * @param dimX
     * @param dimY
     * @param colorScale initial scale by which each ON or OFF event is added to
     * the pixmap.
     * @see #setColorScale(int)
     */
    public DvsSubsamplerToFrame(int dimX, int dimY, int colorScale) {
        this.width = dimX;
        this.height = dimY;
        this.colorScale = colorScale;
        colorScaleRecip = 1f / colorScale;
        nPixels = getWidth() * getHeight();
        pixmap = new float[getnPixels()];
        eventSum = new int[getnPixels()];
    }

    public void clear() {
        Arrays.fill(eventSum, 0);
        Arrays.fill(pixmap, GRAY_LEVEL);
        accumulatedEventCount = 0;
        mostOffCount = Integer.MAX_VALUE;
        mostOnCount = Integer.MIN_VALUE;
        frameIntervalFilter.reset();
        lastIntervalUs = 0;
        accumulatedEventCount = 0;
        lastTimestamp = Integer.MIN_VALUE;

        cleared = true;
    }

    /**
     * Adds event from a source event location to the new coordinates
     *
     * @param e
     * @param srcWidth width of originating source sensor, e.g. 240 for DAVIS240
     * @param srcHeight height of source address space
     */
    public void addEventInNewCoordinates(PolarityEvent e, int newx, int newy) {
        // find element here that contains this event
        if (e.isSpecial() || e.isFilteredOut()) {
            return;
        }
        initialize(e);
        if ((newx <= width) && (newy <= height) && (newx >= 0) && (newy >= 0)) {
            int x = e.x, y = e.y;
            int k = getIndex(newx, newy);
            int sum = eventSum[k];
            sum += (e.polarity == PolarityEvent.Polarity.On ? 1 : -1);
            if (sum > mostOnCount) {
                mostOnCount = sum;
            } else if (sum < mostOffCount) {
                mostOffCount = sum;
            }
            eventSum[k] = sum;
            float pmv = .5f + ((sum * colorScaleRecip) / 2);
            if (pmv > 1) {
                pmv = 1;
            } else if (pmv < 0) {
                pmv = 0;
            }
            pixmap[k] = pmv;
            accumulatedEventCount++;
        } else {
            throw new RuntimeException("index out of bounds for event " + e.toString() + " with newx=" + newx + " newy=" + newy);
        }

    }

    /**
     * Adds event from a source event location to the map
     *
     * @param e
     * @param srcWidth width of originating source sensor, e.g. 240 for DAVIS240
     * @param srcHeight height of source address space
     */
    public void addEvent(PolarityEvent e, int srcWidth, int srcHeight) {
        // find element here that contains this event
        if (e.isSpecial() || e.isFilteredOut()) {
            return;
        }
        initialize(e);
        int x = e.x, y = e.y;
        if (srcWidth != width) {
            x = (int) Math.floor(((float) e.x / srcWidth) * width);
        }
        if (srcHeight != height) {
            y = (int) Math.floor(((float) e.y / srcHeight) * height);
        }
        int k = getIndex(x, y);
        if ((k < 0) || (k > eventSum.length)) {
            throw new RuntimeException("index out of bounds for event " + e.toString() + " with srcWidth=" + srcWidth + " srcHeight=" + srcHeight);
        }
        int sum = eventSum[k];
        sum += (e.polarity == PolarityEvent.Polarity.On ? 1 : -1);
        if (sum > mostOnCount) {
            mostOnCount = sum;
        } else if (sum < mostOffCount) {
            mostOffCount = sum;
        }
        eventSum[k] = sum;
        float pmv = .5f + ((sum * colorScaleRecip) / 2);
        if (pmv > 1) {
            pmv = 1;
        } else if (pmv < 0) {
            pmv = 0;
        }
        pixmap[k] = pmv;
        accumulatedEventCount++;

    }

    public float getValueAtPixel(int x, int y) {
        return pixmap[getIndex(x, y)];
    }

    public int getIndex(int x, int y) {
        return y + (height * x);
    }

    /**
     * @return the width
     */
    public int getWidth() {
        return width;
    }

    /**
     * @return the height
     */
    public int getHeight() {
        return height;
    }

    /**
     * @return the nPixels
     */
    public int getnPixels() {
        return nPixels;
    }

    /**
     * @return the pixmap
     */
    public float[] getPixmap() {
        return pixmap;
    }

    /**
     * @return the colorScale
     */
    public int getColorScale() {
        return colorScale;
    }

    /**
     * @param colorScale the colorScale to set
     */
    public void setColorScale(int colorScale) {
        this.colorScale = colorScale;
        colorScaleRecip = 1f / colorScale;
    }

    /**
     * @return the accumulatedEventCount
     */
    public int getAccumulatedEventCount() {
        return accumulatedEventCount;
    }

    /**
     * @return the mostOffCount
     */
    public int getMostOffCount() {
        return mostOffCount;
    }

    /**
     * @return the mostOnCount
     */
    public int getMostOnCount() {
        return mostOnCount;
    }

    private void initialize(PolarityEvent e) {
        if (cleared) {
            cleared = false;
            int lastStartTimestamp = startTimestamp;
            startTimestamp = e.timestamp;
            lastIntervalUs = startTimestamp - lastStartTimestamp;
            if (lastStartTimestamp != 0) {
                frameIntervalFilter.filter(lastIntervalUs, startTimestamp);
            }
        }
    }

    public float getFilteredSubsamplerIntervalUs() {
        return frameIntervalFilter.getValue();
    }

    public int getLastSubsamplerFrameIntervalUs() {
        return lastIntervalUs;
    }

}
