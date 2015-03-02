/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import java.util.Arrays;
import net.sf.jaer.event.PolarityEvent;

/**
 * Subsamples DVS input ON and OFF events to a desired "frame" resolution. By
 * subsampling (accumulation) of events it performs much better than
 * downsampling the sparse DVS output.
 *
 * @author tobi
 */
public class DvsSubsamplerToFrame {

    private final int width;
    private final int height;
    private final int nPixels;
    private final float[] pixmap;
    private final int[] eventSum;
    private int colorScale;
    private float colorScaleRecip;
    public final float GRAY_LEVEL = 0.5f;
    private int accumulatedEventCount = 0;
    private int lastTimestamp = Integer.MIN_VALUE;
    private int mostOffCount = Integer.MAX_VALUE, mostOnCount = Integer.MIN_VALUE;

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
        int x = (int) Math.floor(((float) e.x / srcWidth) * width);
        int y = (int) Math.floor(((float) e.y / srcHeight) * height);
        int k = getIndex(x, y);
        int sum = eventSum[k];
        sum += (e.polarity == PolarityEvent.Polarity.On ? 1 : -1);
        if (sum > mostOnCount) {
            mostOnCount = sum;
        } else if (sum < mostOffCount) {
            mostOffCount = sum;
        }
        eventSum[k] = sum;
        float pmv = .5f + sum * colorScaleRecip/2;
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
        return y + height * x;
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

}
