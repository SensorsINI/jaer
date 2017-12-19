/* 
 * Copyright (C) 2017 Tobi.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package ch.unizh.ini.jaer.projects.npp;

import eu.visualize.ini.convnet.DeepLearnCnnNetwork_HJ;
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
    synchronized public void setFromNetwork(AbstractDavisCNN apsDvsNet) {
        if (apsDvsNet != null && apsDvsNet.getInputLayer() != null) {
            setWidth(apsDvsNet.getInputLayer().getWidth());
            setHeight(apsDvsNet.getInputLayer().getHeight());
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
        return dvsFrame.getNumPixels();
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
