/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;

/**
 *
 * Concrete class for subsampling DVS input ON and OFF events to a single
 * "frame"
 *
 * The DVSFrame is a grayscale 2D histogram. By subsampling (accumulation) of
 * events it performs much better than downsampling the sparse DVS output. The
 * output of the subsampler is available as a float array that is scaled by the
 * color scale for each event (with sign for ON and OFF events) and clipped to
 * 0-1 range.
 *
 * @author Tobi
 */
public class DvsFramerSingleFrame extends DvsFramer {

    protected DvsFrame dvsFrame = null;

    public DvsFramerSingleFrame(AEChip chip) {
        super(chip);
        dvsFrame = new DvsFrame();
        dvsFrame.setWidth(getInt("width", 64));
        dvsFrame.setHeight(getInt("height", 64));
        dvsFrame.allocateMemory();
        setPropertyTooltip("width", "width of output DVS frame in pixels");
        setPropertyTooltip("height", "height of output DVS frame in pixels");
    }

 
    /**
     * Adds event from a source event location to the map by integer division to
     * the correct location in the subsampled DVS frame. 
     *<p>
     * If the frame is filled up, then it is normalized and teh EVENT_NEW_FRAME_AVAILABLE PropertyChangeEvent is fired.
     *
     *
     * @param e the event to add
     * @param srcWidth width of originating source sensor, e.g. 240 for DAVIS240
     * @param srcHeight height of source address space
     */
    synchronized public void addEvent(PolarityEvent e) {
        // find element here that contains this event
        if (e.isSpecial() || e.isFilteredOut()) {
            return;
        }
        int srcWidth = chip.getSizeX(), srcHeight = chip.getSizeY();
        initialize(e);
        int x = e.x, y = e.y;
        if (srcWidth != dvsFrame.getWidth()) {
            x = (int) Math.floor(((float) e.x / srcWidth) * dvsFrame.getWidth());
        }
        if (srcHeight != dvsFrame.getHeight()) {
            y = (int) Math.floor(((float) e.y / srcHeight) * dvsFrame.getHeight());
        }
        dvsFrame.addEvent(x, y, e.polarity);

    }

    @Override
    public void clear() {
        dvsFrame.clear();
    }

    public int getWidth() {
        return dvsFrame.getWidth();
    }

    public int getHeight() {
        return dvsFrame.getHeight();
    }

    synchronized public void setWidth(int width) {
        dvsFrame.setWidth(width);
        putInt("width", width);
        allocateMemory();
    }

    synchronized public void setHeight(int height) {
        dvsFrame.setHeight(height);
        putInt("height", height);
        allocateMemory();
    }

    @Override
    synchronized public void setFromNetwork(DavisCNN apsDvsNet) {
        if (apsDvsNet != null && apsDvsNet.inputLayer != null) {
            setWidth(apsDvsNet.inputLayer.dimx);
            setHeight(apsDvsNet.inputLayer.dimy);
        } else {
            log.warning("null network, cannot set dvsFrame size");
        }
    }

    @Override
    synchronized public void setFromNetwork(DeepLearnCnnNetwork_HJ apsDvsNet) {
        if (apsDvsNet != null && apsDvsNet.inputLayer != null) {
            setWidth(apsDvsNet.inputLayer.dimx);
            setHeight(apsDvsNet.inputLayer.dimy);
        } else {
            log.warning("null network, cannot set dvsFrame size");
        }
    }

    @Override
    synchronized public boolean allocateMemory() {
        if (dvsFrame == null) {
            dvsFrame = new DvsFrame();
        }
        return dvsFrame.allocateMemory();
    }

    synchronized public void normalizeFrame() {
        dvsFrame.normalizeFrame();
    }

    public float getValueAtPixel(int x, int y) {
        return dvsFrame.getValueAtPixel(x, y);
    }

    public float getZeroCountPixelValue() {
        return dvsFrame.getZeroCountPixelValue();
    }

    public int getEventSumAtPixel(int x, int y) {
        return dvsFrame.getEventSumAtPixel(x, y);
    }

    public int getnPixels() {
        return dvsFrame.getnPixels();
    }

    public int getAccumulatedEventCount() {
        return dvsFrame.getAccumulatedEventCount();
    }

    public int getMostOffCount() {
        return dvsFrame.getMostOffCount();
    }

    public int getMostOnCount() {
        return dvsFrame.getMostOnCount();
    }

    public float getSparsity() {
        return dvsFrame.getSparsity();
    }

    public DvsFrame getDvsFrame() {
        return dvsFrame;
    }

    public int getIndex(int x, int y) {
        return dvsFrame.getIndex(x, y);
    }

}
