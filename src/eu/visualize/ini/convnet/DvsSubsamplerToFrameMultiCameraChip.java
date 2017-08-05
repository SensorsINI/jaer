/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import java.util.logging.Logger;
import net.sf.jaer.event.MultiCameraApsDvsEvent;
import net.sf.jaer.util.filter.LowpassFilter;

/**
 * Subsamples DVS Multicamerachip input ON and OFF events to a desired "frame" resolution in a
 * grayscale 2D histogram. By subsampling (accumulation) of events it performs
 * much better than downsampling the sparse DVS output. The output of the
 * subsampler is available as a float array that is scaled by the color scale
 * for each event (with sign for ON and OFF events) and clipped to 0-1 range.
 *
 * @author Gemma
 */
public class DvsSubsamplerToFrameMultiCameraChip  extends DvsSubsamplerToFrame{
    
    private static Logger log = Logger.getLogger("DvsSubsamplerToFrameMultiCameraChip");
    private int warningsBadEvent = 0;
    private int accumulatedEventCount = 0;
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
    public DvsSubsamplerToFrameMultiCameraChip(int dimX, int dimY, int colorScale) {
        super(dimX, dimY, colorScale);
    }
    
    public void clear() {
        super.clear();
        mostOffCount = Integer.MAX_VALUE;
        mostOnCount = Integer.MIN_VALUE;
        accumulatedEventCount = 0;
        cleared = true;
    }
    
    /**
     * Adds event from a source event location to the map
     *
     * @param e
     * @param srcWidth width of originating source sensor, e.g. 240 for DAVIS240
     * @param srcHeight height of source address space
     */
    public void addEvent(MultiCameraApsDvsEvent e, int srcWidth, int srcHeight) {
        // find element here that contains this event
        int colorScale=super.getColorScale();
        int colorScaleRecip=super.getColorScale();
        
        if (e.isSpecial() || e.isFilteredOut()) {
            return;
        }
        initialize(e);
        int x = e.x, y = e.y;
        int camera = e.camera;
        //Redisposition of the 4 images from the 4 cameras in a square
        //   |camera0 camera2|
        //   |camera1 camera3|
        int X,Y; // X and Y are the coordinates in the reshaped image        
        
        int column=0;
        int row=0;
        if (camera>=2){
            column= 1;
        }
        if (camera%2==0){
            row= 1;
        }
        int srcWidthSigleCamera=srcWidth/4; //srcWidth=chip.width; for the MultiCameraChip it is numCameras*width
        int srcHeightSigleCamera=srcHeight; //srcHeigth=chip.heigth; it doesn't change, horizontal display in the AEViewer
        
        X=x+column*srcWidthSigleCamera;
        Y=y+row*srcHeightSigleCamera;
        
        int srcWidthReshaped=srcWidthSigleCamera*2; //reshapde size in the square configuration
        int srcHeightReshaped=srcHeight*2;
        
        int xReshaped = (int) Math.floor(((float) X / srcWidthReshaped) * super.getWidth()); //coordinates in the reshaped configuration
        int yReshaped = (int) Math.floor(((float) Y / srcHeightReshaped) * super.getHeight());

        int k = getIndex(xReshaped, yReshaped);
        if (((k < 0) || (k >= super.getnPixels()))) {
            if (warningsBadEvent < 2) {
                log.warning("ignoring event with index out of bounds for event " + e.toString() + " with srcWidth=" + srcWidth + " srcHeight=" + srcHeight);
            }
            if (warningsBadEvent == 2) {
                log.warning("supressing further warnings");
            }
            warningsBadEvent++;
            return;
        }
        int sum = super.getEventSumAtPixel(xReshaped,yReshaped);
        sum += super.isRectifyPolarties() ? 1 : (e.polarity == MultiCameraApsDvsEvent.Polarity.On ? 1 : -1);
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
        super.setEventSumAtPixel(sum,xReshaped,yReshaped); // eventSum contains raw integer signed event count
        // compute the pixmap value (pmv) using gray level and colorScale
        float pmv = 0;
        if (!super.isRectifyPolarties()) {
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
        setValueAtPixel(pmv,xReshaped,yReshaped);
        accumulatedEventCount++;
    }
    
    private void initialize(MultiCameraApsDvsEvent e) {
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
    
    /** Sets the value of pixmap.
     * 
     * @param value
     * @param x
     * @param y 
     *
     */
    public void setValueAtPixel(float value, int x, int y){
        super.getPixmap()[getIndex(x, y)]=value;
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

}
