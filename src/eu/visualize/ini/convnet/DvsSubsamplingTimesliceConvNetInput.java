/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import java.util.Arrays;
import net.sf.jaer.event.PolarityEvent;

/**
 * Subsamples DVS input ON and OFF events to a desired input "frame" for the
 * DeepLearnCnnNetwork. By subsampling (accumulation) of events it performs much
 * better than downsampling the sparse DVS output.
 *
 * @author tobi
 */
public class DvsSubsamplingTimesliceConvNetInput {

    private final int width;
    private final int height;
    private final int nPixels;
    private final float[] pixmap;
    private int colorScale;
    private float colorScaleRecip;
    public final float GRAY_LEVEL = 0.5f;
    private int accumulatedEventCount = 0;

    /**
     * Makes a new DvsSubsamplingTimesliceConvNetInput
     *
     * @param dimX
     * @param dimY
     * @param colorScale initial scale by which each ON or OFF event is added to
     * the pixmap.
     * @see #setColorScale(int)
     */
    public DvsSubsamplingTimesliceConvNetInput(int dimX, int dimY, int colorScale) {
        this.width = dimX;
        this.height = dimY;
        this.colorScale = colorScale;
        colorScaleRecip = 1f / colorScale;
        nPixels = getWidth() * getHeight();
        pixmap = new float[getnPixels()];
    }

    public void clear() {
        Arrays.fill(pixmap, GRAY_LEVEL);
        accumulatedEventCount = 0;
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
        int x = (int) Math.floor(((float) e.x / srcWidth) * width);
        int y = (int) Math.floor(((float) e.y / srcHeight) * height);
        int k = getIndex(x, y);
        float f = pixmap[k];
        f += colorScaleRecip * (e.polarity == PolarityEvent.Polarity.On ? 1 : -1);
        if (f < 0) {
            f = 0;
        } else if (f > 1) {
            f = 1;
        }
        pixmap[k] = f;
        accumulatedEventCount++;

    }

    public float getValueAtPixel(int x, int y) {
        return pixmap[getIndex(x, y)];
    }

    public int getIndex(int x, int y) {
        return y + width * x;
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

}
