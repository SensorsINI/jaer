/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import java.util.Arrays;
import java.util.logging.Logger;

import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.util.filter.LowpassFilter;

/**
 * Subsamples DVS input ON and OFF events to a desired "frame" resolution in a
 * grayscale 2D histogram. By subsampling (accumulation) of events it performs
 * much better than downsampling the sparse DVS output. The output of the
 * subsampler is available as a float array that is scaled by the color scale
 * for each event (with sign for ON and OFF events) and clipped to 0-1 range.
 *
 * @author tobi
 */
public class DvsSubsamplerToFrame {

    private static Logger log = Logger.getLogger("DvsSubsamplerToFrame");

    private final int width; // width of output
    private final int height; // height of output
    private final int nPixels;
    private final int[] eventSum; // eventSum contains raw integer signed event count
    private final float[] pixmap; // pixmap contains the scaled event count centered on 0.5 and clipped to 0-1 range
    private int colorScale;
    private float colorScaleRecip;
    public final float GRAY_LEVEL = 0.5f;
    private int accumulatedEventCount = 0;
    private int mostOffCount = Integer.MAX_VALUE, mostOnCount = Integer.MIN_VALUE;
    LowpassFilter frameIntervalFilter = new LowpassFilter(1000);
    private int startTimestamp = 0;
    private boolean cleared = true;
    private int lastIntervalUs = 0;
    private float sparsity = 1;  // computed when frame is normalized
    private boolean rectifyPolarties = false;

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
        Arrays.fill(pixmap, rectifyPolarties ? 0 : GRAY_LEVEL);
        accumulatedEventCount = 0;
        mostOffCount = Integer.MAX_VALUE;
        mostOnCount = Integer.MIN_VALUE;
        lastIntervalUs = 0;
        accumulatedEventCount = 0;

        cleared = true;
    }

//    /**
//     * Adds event from a source event location to the new coordinates
//     *
//     * @param e
//     * @param srcWidth width of originating source sensor, e.g. 240 for DAVIS240
//     * @param srcHeight height of source address space
//     */
//    public void addEventInNewCoordinates(PolarityEvent e, int newx, int newy) {
//        // find element here that contains this event
//        if (e.isSpecial() || e.isFilteredOut()) {
//            return;
//        }
//        initialize(e);
//        if ((newx <= width) && (newy <= height) && (newx >= 0) && (newy >= 0)) {
//            int x = e.x, y = e.y;
//            int k = getIndex(newx, newy);
//            int sum = eventSum[k];
//            sum += (e.polarity == PolarityEvent.Polarity.On ? 1 : -1);
//            if (sum > mostOnCount) {
//                mostOnCount = sum;
//            } else if (sum < mostOffCount) {
//                mostOffCount = sum;
//            }
//            eventSum[k] = sum;
//            float pmv = .5f + ((sum * colorScaleRecip) / 2);
//            if (pmv > 1) {
//                pmv = 1;
//            } else if (pmv < 0) {
//                pmv = 0;
//            }
//            pixmap[k] = pmv;
//            accumulatedEventCount++;
//        } else {
//            throw new RuntimeException("index out of bounds for event " + e.toString() + " with newx=" + newx + " newy=" + newy);
//        }
//
//    }
    private int warningsBadEvent = 0;

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
        if (((k < 0) || (k >= eventSum.length))) {
            if (warningsBadEvent < 2) {
                log.warning("ignoring event with index out of bounds for event " + e.toString() + " with srcWidth=" + srcWidth + " srcHeight=" + srcHeight);
            }
            if (warningsBadEvent == 2) {
                log.warning("supressing further warnings");
            }
            warningsBadEvent++;
            return;
        }
        int sum = eventSum[k];
        sum += rectifyPolarties ? 1 : (e.polarity == PolarityEvent.Polarity.On ? 1 : -1);
        // clip count at full scale
        if (sum > colorScale) {
            sum = colorScale;
        } else if (sum < -colorScale) {
            sum = colorScale;
        }
        // keep track of largest and smallest count
        if (sum > mostOnCount) {
            mostOnCount = sum;
        } else if (sum < mostOffCount) {
            mostOffCount = sum;
        }
        eventSum[k] = sum; // eventSum contains raw integer signed event count
        // compute the pixmap value (pmv) using gray level and colorScale
        float pmv = 0;
        if (!rectifyPolarties) {
            pmv = GRAY_LEVEL + ((sum * colorScaleRecip) / 2); // full scale is +/- colorScale events
        } else {
            pmv = sum * colorScaleRecip; // full scale is just exactly colorScale events
        }
        if (pmv > 1) {
            pmv = 1;
        } else if (pmv < 0) {
            pmv = 0;
        }
        // pixmap contains the scaled event count clipped to 0-1 range and either starting at zero or centered on 0.5, depending on rectifyPolarties
        // This pixmap value is OVERWRITEN later if the frame is normalized, but otherwise if normalizeFrame is not called, then
        // the pixmap value set here is the one that is returned (and typically used for rendering image) 
        pixmap[k] = pmv;
        accumulatedEventCount++;

    }

    /**
     * Returns value of resulting pixmap after normalization for pixels with
     * zero net event count
     *
     * @return rectifyPolarties?0: 127f / 255;
     */
    public float getZeroCountPixelValue() {
        return rectifyPolarties ? 0 : 127f / 255;
    }

    /**
     * Returns the float value of the histogram clipped to 0-1 range and scaled
     * by colorScale, or normalized. To have these values normalized, call
     * normalizeFrame.
     *
     * Returns the scaled event count clipped to 0-1 range and either starting
     * at zero or centered on 0.5, depending on rectifyPolarties first.
     *
     * @param x
     * @param y
     * @return the value of the subsampled map
     * @see #normalizeFrame()
     */
    public float getValueAtPixel(int x, int y) {
        return pixmap[getIndex(x, y)];
    }
    

    /**
     * Returns the integer event sum of the histogram clipped to +/- colorScale range. 
     * If rectifyPolarties==true, then the events are rectified to +1, otherwise the events are accumulated with +/-1 polarity value.
     *
     * @param x
     * @param y
     * @return the value of the subsampled map
     * @see #getValueAtPixel(int, int) 
     */
    public int getEventSumAtPixel(int x, int y) {
        return eventSum[getIndex(x, y)];
    }
    
    /** Sets the value of eventSum array. Utility method used for debugging normalization.
     * 
     * @param x
     * @param y 
     */
    public void setEventSumAtPixel(int value, int x, int y){
        eventSum[getIndex(x, y)]=value;
    }
    
    /**
     * Gets the index into the maps
     *
     * @param x
     * @param y
     * @return the index into the 1d arrays
     */
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
     * Returns the float[] 0-1 clipped map.
     *
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
     * Sets the amount by which the pixmap is updated by each ON/OFF event.
     *
     * @param colorScale the colorScale to set
     */
    public void setColorScale(int colorScale) {
        this.colorScale = colorScale;
        colorScaleRecip = 1f / colorScale;
    }

    /**
     * Returns total event count accumulated since clear
     *
     * @return the accumulatedEventCount
     */
    public int getAccumulatedEventCount() {
        return accumulatedEventCount;
    }

    /**
     * Returns index of pixel with most OFF count
     *
     * @return the mostOffCount
     */
    public int getMostOffCount() {
        return mostOffCount;
    }

    /**
     * Returns index of pixel with most ON count
     *
     * @return the mostOnCount
     */
    public int getMostOnCount() {
        return mostOnCount;
    }

    private void initialize(PolarityEvent e) {
        if (cleared) {
            cleared = false;
            int lastStartTimestamp = startTimestamp;
            if (e.timestamp < startTimestamp) {
                frameIntervalFilter.reset();
                lastStartTimestamp = e.timestamp;
            }
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

    /**
     * Call this method to normalize accumulated frame to have zero mean and
     * range 0-1 using 3-sigma values, as is used in DeepLearnCnnNetwork.
     */
    public void normalizeFrame() {
        // net trained gets 0-1 range inputs, so make our input so
        int n = eventSum.length;
        float sum = 0, var = 0, count = 0;
        // sum and average only non-zero pixels
        for (int i = 0; i < n; i++) {
            if (eventSum[i] != 0) {
                sum += eventSum[i];
                count++;
            }
        }
        float mean = sum / count; // compute mean of all signed event counts

        for (int i = 0; i < n; i++) {
            if (eventSum[i] != 0) {
                float f = (eventSum[i] - mean);
                var += f * f;
            }
        }
        var = (var / count);
        float sig = (float) Math.sqrt(var); // compute 1-sigma of signed counts
        if (sig < (0.1f / 255.0f)) {
            sig = 0.1f / 255.0f;  // restrict sigma to reasonable range
        }
        // if rectifyPolarties is false, pixels with count zero should end up with this 0-1 range value so that they come out to 127 in PNG file range of 0-255
        // if rectifyPolarties is true, pixels with zero count should end up with zero, and we use only 3 sigma range
        final float numSDevs = 3;
        final float mean_png_gray = rectifyPolarties ? 0 : 127f / 255;
        final float range = rectifyPolarties ? numSDevs * sig : 2 * numSDevs * sig, halfRange = rectifyPolarties ? 0 : numSDevs * sig;
        final float rangenew = 1;
        int nonZeroCount = 0;
        //Now pixels with zero count go to 127/255, pixels with -3sigma or larger negative count go to 0,
        // and pixels with +3sigma or larger positive count go to 1. each count contributes +/- 1/6sigma to pixmap.
        for (int i = 0; i < n; i++) {
            if (eventSum[i] == 0) {
                pixmap[i] = mean_png_gray;
            } else {
                nonZeroCount++;
                // rectifyPolarties=false: shift up by 3 sigma and divide by 6 sigma to get in range 0-1
                // rectifyPolarties=true: don't shift (already origin at zero) and divide by 3 sigma to get in range 0-1
                float f = ((eventSum[i] + halfRange) * rangenew) / range;
                if (f > 1) {
                    f = 1;
                } else if (f < 0) {
                    f = 0;
                }
                pixmap[i] = f;
            }
        }
        sparsity = (float) (n - nonZeroCount) / n;
    }

    /**
     * Returns the computed sparsity (fraction of nonzero pixels) if
     * normalizeFrame is called beforehand.
     *
     * @return the sparsity
     */
    public float getSparsity() {
        return sparsity;
    }

    /**
     * @return the rectifyPolarties
     */
    public boolean isRectifyPolarties() {
        return rectifyPolarties;
    }

    /**
     * True: Events of both ON and OFF type produce positive pixel values;
     * starting frame is set to 0.
     * <br>
     * False: Events produce negative (for OFF events) and positive (for ON
     * events); starting frame is set to 0.5.
     *
     * @param rectifyPolarties the rectifyPolarties to set
     */
    public void setRectifyPolarties(boolean rectifyPolarties) {
        this.rectifyPolarties = rectifyPolarties;
    }

}
