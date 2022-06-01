/*
 * Copyright (C) 2022 tobi.
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

import ch.unizh.ini.jaer.projects.davis.frames.ApsFrameExtractor;
import ch.unizh.ini.jaer.projects.npp.DvsFramer.DvsFrame;
import static ch.unizh.ini.jaer.projects.npp.DvsFramer.log;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.awt.GLCanvas;
import eu.seebetter.ini.chips.DavisChip;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.logging.Level;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.eventprocessing.FilterChain;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.graphics.ImageDisplay;

/**
 * Sends DAVIS DVS and APS frames to a client over UDP
 *
 * @author tobi
 */
@Description("Sends DAVIS DVS and APS frames to a client over UDP")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class DavisUDPFramer extends EventFilter2D implements FrameAnnotater, PropertyChangeListener {

    public static final int CHANNEL_APS=0, CHANNEL_DVS=1;
    
    protected DvsFramer dvsFramer = null;
    private ApsFrameExtractor frameExtractor = null;

    private boolean udpOutputEnabled = getBoolean("UDPOutputEnabled", false);
    private DatagramSocket sendToSocket = null, listenOnSocket = null;
    private InetSocketAddress client = null;
    private String host = getString("hostname", "localhost");
    public static final int DEFAULT_SENDTO_PORT = 12000;
    private int portSendTo = getInt("portSendTo", DEFAULT_SENDTO_PORT);

    private boolean processAPSFrames = getBoolean("processAPSFrames", true);
    private boolean processDVSTimeSlices = getBoolean("processDVSTimeSlices", true);

    private APSDVSFrame apsDvsFrame = null;
    private ImageDisplay imageDisplay;
    private JFrame imageFrame = null;

    private boolean showFrames = getBoolean("showFrames", true);

    protected int lastProcessedEventTimestamp = 0;

    public DavisUDPFramer(AEChip chip) {
        super(chip);
        FilterChain chain = new FilterChain(chip);
        if (chip instanceof DavisChip) {
            frameExtractor = new ApsFrameExtractor(chip);
            chain.add(frameExtractor);
        }
        setEnclosedFilterChain(chain);
        dvsFramer = new DvsFramerSingleFrame(chip); // must be replaced by the right subsampler object by subclasses TODO not clean
        getEnclosedFilterChain().add(dvsFramer); // only for control, we iterate with it here using the events we recieve by directly calling addEvent in the event processing loop, not by using the subsampler filterPacket method
        setEnclosedFilterChain(getEnclosedFilterChain());

        String img = "1. Image", udp = "2. UDP", disp = "3. Display", dvs = "4: DVS";
        setPropertyTooltip(img, "processAPSFrames", "adds APS frames to frame on channel 0");
        setPropertyTooltip(img, "processDVSTimeSlices", "accumulates DVS events to frame on channel 1");
        setPropertyTooltip(img, "outputImageWidth", "width of output image in pixels");
        setPropertyTooltip(img, "outputImageHeight", "height of output image in pixels");

        setPropertyTooltip(udp, "hostname", "learning host name (IP or DNS)");
        setPropertyTooltip(udp, "portSendTo", "learning host port number that we send to");
        setPropertyTooltip(udp, "udpOutputEnabled", "enable UDP output");

        setPropertyTooltip(disp, "showFrames", "Show the accumulated frames");
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        super.propertyChange(evt);
        // new activationsFrame is available, process it
        switch (evt.getPropertyName()) {
            case ApsFrameExtractor.EVENT_NEW_FRAME:
                if (isFilterEnabled() && (isProcessAPSFrames())) {
                    updateAPSDVSFrame(frameExtractor);
                    if (udpOutputEnabled) {
                        sendApsDvsFrame();
                    }
                }
                break;
            case DvsFramer.EVENT_NEW_FRAME_AVAILABLE:
                if (isFilterEnabled() && isProcessDVSTimeSlices()) {
                    final DvsFramer.DvsFrame dvsFrame = (DvsFramer.DvsFrame) evt.getNewValue();
                    updateApsDvsFrame(dvsFrame);
                    if (isProcessDVSTimeSlices() && udpOutputEnabled) {
                        sendApsDvsFrame();
                    }
                }
        }
    }

    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        if (frameExtractor != null && isProcessAPSFrames()) {
            frameExtractor.filterPacket(in);
        }
        for (BasicEvent e : in) {
            lastProcessedEventTimestamp = e.getTimestamp();
            PolarityEvent p = (PolarityEvent) e;
            if (dvsFramer != null) {
                dvsFramer.addEvent(p); // generates event when full, which processes it in propertyChange() which computes CNN
            }
        }
        return in;
    }

    synchronized private void sendApsDvsFrame() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(300000);
            ObjectOutputStream out = new ObjectOutputStream(bos);
            // Method for serialization of object
            out.writeObject(apsDvsFrame);

            sendUDPBytes(bos.toByteArray());

        } catch (IOException ex) {
            log.warning(ex.toString());
        }
    }

    synchronized private void sendUDPMessageString(String string) throws IOException { // sync for thread safety on multiple senders

        log.info(String.format("sending string message to host=%s port=%s string=\"%s\"", host, portSendTo, string));

        byte[] buf = string.getBytes();
        sendUDPBytes(buf);
    }

    private void checkApsDvsFrame() {
        if (apsDvsFrame == null || apsDvsFrame.getHeight() != getOutputImageHeight() || apsDvsFrame.getWidth() != dvsFramer.getOutputImageWidth()) {
            apsDvsFrame = new APSDVSFrame(getOutputImageHeight(), getOutputImageWidth());
        }
    }

    synchronized private void updateAPSDVSFrame(ApsFrameExtractor frameExtractor) {
        checkApsDvsFrame();
        apsDvsFrame.setChannel(0);
        apsDvsFrame.setTimestampNow();
        apsDvsFrame.incrementCounter();
        int srcwidth = chip.getSizeX(), srcheight = chip.getSizeY(), targetwidth = getOutputImageWidth(), targetheight = getOutputImageHeight();
        float[] frame = frameExtractor.getNewFrame();
        for (int y = 0; y < targetheight; y++) {
            for (int x = 0; x < targetwidth; x++) {
                int xsrc = (int) Math.floor(x * (float) srcwidth / targetwidth), ysrc = (int) Math.floor(y * (float) srcheight / targetheight);
                float v = frame[frameExtractor.getIndex(xsrc, ysrc)];
                apsDvsFrame.setValue(x, y, v);
            }
        }
        
    }

    synchronized private void updateApsDvsFrame(DvsFrame dvsFrame) {
        checkApsDvsFrame();
        apsDvsFrame.setChannel(1);
        apsDvsFrame.setTimestampNow();
        apsDvsFrame.incrementCounter();
        int targetwidth = getOutputImageWidth(), targetheight = getOutputImageHeight();
        for (int y = 0; y < targetheight; y++) {
            for (int x = 0; x < targetwidth; x++) {
                float v = dvsFrame.getValueAtPixel(x, y);
                apsDvsFrame.setValue(x, y, v);
            }
        }
    }

    /**
     * A frame with 2 channels, DVS and APS. Each is gray scale.
     */
    public static class APSDVSFrame implements java.io.Serializable {

        public static long GUID = -1L;  // TODO update

        public final int width;
        public final int height;
        final byte[][] values;
        private int channel=0;
        long timestampMsEpoch = 0;
        int counter=0;

        public APSDVSFrame(int height, int width) {
            this.width = width;
            this.height = height;
            values = new byte[height][width];
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
        
        

        /** stores float value as unsigned byte by multiplying by 255
         * 
         * @param channel 0 for APS, 1 for DVS
         * @param x
         * @param y
         * @param v the float value
         */
        public void setValue(int x, int y, float v) {
            values[y][x] = (byte) (((int) (255 * v)) & 0xff);
        }

        public int getValue(int x, int y) {
            return values[y][x]&0xff;
        }

        private void reset() {
            Arrays.fill(values, 0);
            counter=0;
            timestampMsEpoch=0;
        }

        /**
         * @return the channel
         */
        public int getChannel() {
            return channel;
        }

        /**
         * @param channel the channel to set
         */
        public void setChannel(int channel) {
            this.channel = channel;
        }
        
        public void setTimestampNow(){
            timestampMsEpoch=System.currentTimeMillis();
        }
        
        public void incrementCounter(){
            counter++;
        }

    }

    synchronized private void sendUDPBytes(byte[] buf) throws IOException {

        if (sendToSocket == null) {
            try {
                log.info(String.format("opening socket to %s:%d to send frames to", host, portSendTo));
                client = new InetSocketAddress(host, portSendTo); // get address for remote client
                sendToSocket = new DatagramSocket(); // make a local socket using any port, will be used to send datagrams to the host/sendToPort
            } catch (IOException ex) {
                log.warning(String.format("cannot open socket to send to host=%s port=%d, got exception %s", host, portSendTo, ex.toString()));
                return;
            }
        }
        try {
            DatagramPacket datagram = new DatagramPacket(buf, buf.length, client.getAddress(), portSendTo); // construct datagram to send to host/sendToPort
            if(datagram.getLength()>64000){
                log.warning(String.format("Datagram length=%d is too long, more than 64000 bytes",datagram.getLength()));
                return;
            }
            sendToSocket.send(datagram);
        } catch (IOException ex) {
            log.warning("cannot send message " + ex.toString());
            return;
        }

    }

    public String getHostname() {
        return host;
    }

    /**
     * You need to setHost before this will send events.
     *
     * @param host the host
     */
    synchronized public void setHostname(String host) {
        this.host = host;
        putString("hostname", host);
    }

    public int getPortSendTo() {
        return portSendTo;
    }

    /**
     * You set the port to say which port the packet will be sent to.
     *
     * @param portSendTo the UDP port number.
     */
    public void setPortSendTo(int portSendTo) {
        this.portSendTo = portSendTo;
        putInt("portSendTo", portSendTo);
    }

    public int getOutputImageWidth() {
        return dvsFramer.getOutputImageWidth();
    }

    public int getOutputImageHeight() {
        return dvsFramer.getOutputImageHeight();
    }

    public synchronized void setOutputImageWidth(int width) {
        dvsFramer.setOutputImageWidth(width);
        apsDvsFrame=new APSDVSFrame(getOutputImageHeight(), getOutputImageWidth());
    }

    public synchronized void setOutputImageHeight(int height) {
        dvsFramer.setOutputImageHeight(height);
        apsDvsFrame=new APSDVSFrame(getOutputImageHeight(), getOutputImageWidth());
    }

    public boolean isShowFrames() {
        return showFrames;
    }

    /**
     * @return the processAPSFrames
     */
    public boolean isProcessAPSFrames() {
        return processAPSFrames;
    }

    /**
     * @param processAPSFrames the processAPSFrames to set
     */
    synchronized public void setProcessAPSFrames(boolean processAPSFrames) {
        this.processAPSFrames = processAPSFrames;
        putBoolean("processAPSFrames", processAPSFrames);
        apsDvsFrame.reset();
    }

    /**
     * @return the processDVSTimeSlices
     */
    public boolean isProcessDVSTimeSlices() {
        return processDVSTimeSlices;
    }

    /**
     * @param processDVSTimeSlices the processDVSTimeSlices to set
     */
    synchronized public void setProcessDVSTimeSlices(boolean processDVSTimeSlices) {
        this.processDVSTimeSlices = processDVSTimeSlices;
        putBoolean("processDVSTimeSlices", processDVSTimeSlices);
        apsDvsFrame.reset();
    }

    public void setShowFrames(boolean showFrames) {
        boolean old = this.showFrames;
        this.showFrames = showFrames;
        putBoolean("showFrames", showFrames);
        if (imageFrame != null) {
            imageFrame.setVisible(showFrames);
        }
        getSupport().firePropertyChange("showFrames", old, this.showFrames);
    }

    /**
     * @return the udpOutputEnabled
     */
    public boolean isUdpOutputEnabled() {
        return udpOutputEnabled;
    }

    /**
     * @param udpOutputEnabled the udpOutputEnabled to set
     */
    public void setUdpOutputEnabled(boolean udpOutputEnabled) {
        this.udpOutputEnabled = udpOutputEnabled;
        putBoolean("udpOutputEnabled", udpOutputEnabled);
    }

    @Override
    public void resetFilter() {
        getEnclosedFilterChain().reset();
        if (apsDvsFrame != null) {
            apsDvsFrame.reset();
        }
    }

    @Override
    public void initFilter() {
        dvsFramer.getSupport().addPropertyChangeListener(DvsFramer.EVENT_NEW_FRAME_AVAILABLE, this);
        frameExtractor.getSupport().addPropertyChangeListener(ApsFrameExtractor.EVENT_NEW_FRAME, this);
    }

    @Override
    synchronized public void annotate(GLAutoDrawable drawable) {
        if (isShowFrames() && apsDvsFrame != null) {
            if (imageDisplay == null) {
                imageDisplay = ImageDisplay.createOpenGLCanvas();
                imageDisplay.setBorderSpacePixels(10);
                imageDisplay.setSize(200, 200);
                imageDisplay.setImageSize(getOutputImageWidth(), getOutputImageHeight());
                imageFrame = new JFrame("ApsDvsFrame");
                imageFrame.setPreferredSize(new Dimension(600, 600));
                imageFrame.getContentPane().add(imageDisplay, BorderLayout.CENTER);
                imageFrame.pack();
                imageFrame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(final WindowEvent e) {
                        setShowFrames(false);
                    }
                });

            }

            // copy data from the ApsDvsFrame colllected here to the ImageDisplay TODO very stupid how this is done now
            int w = getOutputImageWidth(), h = getOutputImageHeight();
            if (imageDisplay.getSizeX() != getOutputImageWidth() || imageDisplay.getSizeY() != getOutputImageHeight()) {
                imageDisplay.setImageSize(getOutputImageWidth(), getOutputImageHeight());
            }
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int val = apsDvsFrame.getValue(x, y);
                    imageDisplay.setPixmapRGB(x, y, apsDvsFrame.channel==0?val/255f:0, apsDvsFrame.channel==1?val/255f:0, 0);
                }
            }
            if (!imageFrame.isVisible()) {
                imageFrame.setVisible(true);
            }
            imageDisplay.display();
        }
    }

}
