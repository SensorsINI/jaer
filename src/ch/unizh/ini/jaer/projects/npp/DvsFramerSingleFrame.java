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

import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import static net.sf.jaer.eventprocessing.EventFilter.log;

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
@Description("Makes single DVS frames from DVS events, and optionally sends them or possible DAVIS APS frames as serialized Java objects over UDP")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class DvsFramerSingleFrame extends DvsFramer {

    protected DvsFrame dvsFrame = null;

    public DvsFramerSingleFrame(AEChip chip) {
        super(chip);
        dvsFrame = new DvsFrame();
        dvsFrame.setWidth(getInt("outputImageWidth", 64));
        dvsFrame.setHeight(getInt("outputImageHeight", 64));
        dvsFrame.allocateMemory();
    }

//    public 
    /**
     * Adds event from a source event location to the map by integer division to
     * the correct location in the subsampled DVS frame.
     * <p>
     * If the frame is filled up, then it is normalized and the
     * EVENT_NEW_FRAME_AVAILABLE PropertyChangeEvent is fired.
     *
     *
     * @param e the event to add
     * @param srcWidth width of originating source sensor, e.g. 240 for DAVIS240
     * @param srcHeight height of source address space
     */
    public void addEvent(PolarityEvent e) {
        // find element here that contains this event
        if (e.isSpecial() || e.isFilteredOut()) {
            return;
        }
        int srcWidth = chip.getSizeX(), srcHeight = chip.getSizeY();
        initialize(e);
        int x = e.x, y = e.y;
        if (x <= frameCutLeft || x >= chip.getSizeX() - frameCutRight || y <= frameCutBottom || y >= chip.getSizeY() - frameCutTop) {
            return;
        }
        x -= (frameCutLeft);
        y -= (frameCutBottom); // shift address to start at 0,0 from cut frame
        if (srcWidth != dvsFrame.getWidth()) {
            x = (int) Math.floor(((float) x / (srcWidth - (frameCutLeft + frameCutRight))) * dvsFrame.getWidth());
        }
        if (srcHeight != dvsFrame.getHeight()) {
            y = (int) Math.floor(((float) y / (srcHeight - (frameCutBottom + frameCutTop))) * dvsFrame.getHeight());
        }
        dvsFrame.addEvent(x, y, e.polarity, e.timestamp);

    }


    @Override
    public void clear() {
        dvsFrame.clear();
    }

    synchronized public void setOutputImageWidth(int width) {
        super.setOutputImageWidth(width);
        dvsFrame.setWidth(width);
        allocateMemory();
    }

    synchronized public void setOutputImageHeight(int height) {
        super.setOutputImageHeight(height);
        dvsFrame.setHeight(height);
        allocateMemory();
    }

    @Override
    synchronized public void setFromNetwork(AbstractDavisCNN apsDvsNet) {
        if (apsDvsNet != null && apsDvsNet.getInputLayer() != null) {
            setOutputImageWidth(apsDvsNet.getInputLayer().getWidth());
            setOutputImageHeight(apsDvsNet.getInputLayer().getHeight());
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

    public int getNumPixels() {
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

    @Override
    public String toString() {
        return "DvsFramerSingleFrame{" + "dvsFrame=" + dvsFrame + '}';
    }

    @Override
    public void checkParameters() {
        super.checkParameters();
        if (dvsFrame.width * dvsFrame.height <= 0) {
            throw new RuntimeException("check width and height of DvsFramerSingleFrame.outputImage, currently zero sized");
        }
    }

  
}
