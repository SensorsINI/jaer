/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.davis.frames;

import eu.seebetter.ini.chips.ApsDvsChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.Arrays;
import java.util.Iterator;
import javax.swing.JFrame;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.ImageDisplay;
import net.sf.jaer.graphics.ImageDisplay.Legend;

/**
 * Extracts CIS APS frames from SBRet10/20 DAVIS sensors.
 * Use
 * <ul>
<li>hasNewFrame() to check whether a new frame is available
<li>getDisplayBuffer() to get the latest float buffer displayed
<li>getNewFrame() to get the latest double buffer
</ul>
 *
 * @author Christian Brändli
 */
@Description("Method to acquire a frame from a stream of APS sample events")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class ApsFrameExtractor extends EventFilter2D {

    private JFrame apsFrame = null;
    public ImageDisplay apsDisplay;
    private ApsDvsChip apsChip = null;
    private boolean newFrame, useExtRender=false; // useExtRender means using something like OpenCV to render the data. If false, the displayBuffer is displayed
    private float[] resetBuffer, signalBuffer;
    private float[] displayBuffer;
    private float[] apsDisplayPixmapBuffer;
    private double[] displayFrame;
    public int width, height, maxADC, maxIDX;
    private float grayValue;
    public final float logSafetyOffset = 10000.0f;
    protected boolean showAPSFrameDisplay=getBoolean("showAPSFrameDisplay", true);
    private Legend apsDisplayLegend;
  

    public static enum Extraction {

        ResetFrame, SignalFrame, CDSframe
    };
    private boolean invertIntensity = getBoolean("invertIntensity", false);
    private boolean preBufferFrame = getBoolean("preBufferFrame", true);
    private boolean logCompress = getBoolean("logCompress", false);
    private boolean logDecompress = getBoolean("logDecompress", false);
    private float displayContrast = getFloat("displayContrast", 1.0f);
    private float displayBrightness = getFloat("displayBrightness", 0.0f);
    public Extraction extractionMethod = Extraction.valueOf(getString("extractionMethod", "CDSframe"));


    public ApsFrameExtractor(AEChip chip) {
        super(chip);
        apsDisplay = ImageDisplay.createOpenGLCanvas();
        apsFrame = new JFrame("APS Frame");
        apsFrame.setPreferredSize(new Dimension(width * 4, height * 4));
        apsFrame.getContentPane().add(apsDisplay, BorderLayout.CENTER);
        apsFrame.pack();
        apsDisplayLegend = apsDisplay.addLegend("", 0 ,0);
        float[] displayColor = new float[3];
        displayColor[0] = 1.0f;
        displayColor[1] = 1.0f;
        displayColor[2] = 1.0f;
        apsDisplayLegend.color = displayColor;
        initFilter();

        setPropertyTooltip("invertIntensity", "Inverts grey scale, e.g. for raw samples of signal level");
        setPropertyTooltip("preBufferFrame", "Only display and use complete frames; otherwise display APS samples as they arrive");
        setPropertyTooltip("logCompress", "Should the displayBuffer be log compressed");
        setPropertyTooltip("logDecompress", "Should the logComressed displayBuffer be rendered normal");
        setPropertyTooltip("displayContrast", "Gain for the rendering of the APS display");
        setPropertyTooltip("displayBrightness", "Offset for the rendering of the APS display");
        setPropertyTooltip("extractionMethod", "Method to extract a frame");
        setPropertyTooltip("showAPSFrameDisplay", "Shows the JFrame frame display if true");
    }

    @Override
    public void initFilter() {
        resetFilter();
    }

    @Override
    public void resetFilter() {
        if (ApsDvsChip.class.isAssignableFrom(chip.getClass())) {
            apsChip = (ApsDvsChip) chip;
        } else {
            log.warning("The filter ApsFrameExtractor can only be used for chips that extend the ApsDvsChip class");
        }
        newFrame = false;
        width = chip.getSizeX();
        height = chip.getSizeY();
        maxIDX = width * height;
        maxADC = apsChip.getMaxADC();
        apsDisplay.setImageSize(width, height);
        resetBuffer = new float[width * height];
        signalBuffer = new float[width * height];
        displayFrame = new double[width * height];
        displayBuffer = new float[width * height];
        apsDisplayPixmapBuffer = new float[3 * width * height];
        Arrays.fill(resetBuffer, 0.0f);
        Arrays.fill(signalBuffer, 0.0f);
        Arrays.fill(displayFrame, 0.0f);
        Arrays.fill(displayBuffer, 0.0f);
        Arrays.fill(apsDisplayPixmapBuffer, 0.0f);
    }

    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        checkMaps();

        ApsDvsEventPacket packet = (ApsDvsEventPacket) in;
        if (packet == null) {
            return null;
        }
        if (packet.getEventClass() != ApsDvsEvent.class) {
            log.warning("wrong input event class, got " + packet.getEventClass() + " but we need to have " + ApsDvsEvent.class);
            return null;
        }
        Iterator apsItr = packet.fullIterator();
        while (apsItr.hasNext()) {
            ApsDvsEvent e = (ApsDvsEvent) apsItr.next();
            if (e.isSampleEvent()) {
                putAPSevent(e);
            }
        }

        if(showAPSFrameDisplay){
        apsDisplay.repaint();
        }
        return in;
    }

    private void checkMaps() {
        apsDisplay.checkPixmapAllocation();
        if (showAPSFrameDisplay && !apsFrame.isVisible()) {
            apsFrame.setVisible(true);
        }
    }

    public void putAPSevent(ApsDvsEvent e) {
        if (!e.isSampleEvent()) {
            return;
        }
        //if(e.isStartOfFrame())timestamp=e.timestamp;
        ApsDvsEvent.ReadoutType type = e.getReadoutType();
        float val = e.getAdcSample();
        int idx = getIndex(e.x, e.y);
        if (idx >= maxIDX) {
            return;
        }
        if (e.isStartOfFrame()) {
            if (newFrame && useExtRender) {
                log.warning("Acquistion of new frame started even though old frame was never delivered to ext renderer");
            }
        }
        if (idx < 0) {
            if (e.isEndOfFrame()) {
                if (preBufferFrame && displayBuffer != null && !useExtRender && showAPSFrameDisplay) {
                    displayPreBuffer();
                }
                newFrame = true;
            }
            return;
        }
        switch (type) {
            case SignalRead:
                signalBuffer[idx] = val;
                break;
            case ResetRead:
            default:
                resetBuffer[idx] = val;
                break;
        }
        switch (extractionMethod) {
            case ResetFrame:
                displayBuffer[idx] = resetBuffer[idx];
                break;
            case SignalFrame:
                displayBuffer[idx] = signalBuffer[idx];
                break;
            case CDSframe:
            default:
                displayBuffer[idx] = resetBuffer[idx] - signalBuffer[idx];
                break;
        }
        if (invertIntensity) {
            displayBuffer[idx] = maxADC - displayBuffer[idx];
        }
        if (logCompress) {
            displayBuffer[idx] = (float) Math.log(displayBuffer[idx] + logSafetyOffset);
        }
        if (logCompress && logDecompress) {
            grayValue = scaleGrayValue((float) (Math.exp(displayBuffer[idx]) - logSafetyOffset));
        } else {
            grayValue = scaleGrayValue(displayBuffer[idx]);
        }
        displayFrame[idx] = (double) grayValue;
        if (!preBufferFrame && !useExtRender && showAPSFrameDisplay) {
            apsDisplay.setPixmapGray(e.x, e.y, grayValue);
        } else {
            apsDisplayPixmapBuffer[3 * idx] = grayValue;
            apsDisplayPixmapBuffer[3 * idx + 1] = grayValue;
            apsDisplayPixmapBuffer[3 * idx + 2] = grayValue;
        }
    }

    private float scaleGrayValue(float value) {
        float v;
        v = (displayContrast * value + displayBrightness) / (float) maxADC;
        if (v < 0) {
            v = 0;
        } else if (v > 1) {
            v = 1;
        }
        return v;
    }

    public void updateDisplayValue(int xAddr, int yAddr, float value) {
        if (logCompress && logDecompress) {
            grayValue = scaleGrayValue((float) (Math.exp(value) - logSafetyOffset));
        } else {
            grayValue = scaleGrayValue(value);
        }
        apsDisplay.setPixmapGray(xAddr, yAddr, grayValue);
    }
    
    public void setPixmapArray(float[] pixmapArray){
        apsDisplay.setPixmapArray(pixmapArray);
    }
    
    public void displayPreBuffer(){
        apsDisplay.setPixmapArray(apsDisplayPixmapBuffer);
    }

    /** returns the index into pixel arrays for a given x,y location where 
     * x is horizontal address and y is vertical and it starts at lower 
     * left corner with x,y=0,0 and x and y increase to right and up.
     * 
     * @param x
     * @param y
     * @param idx the array index
     * @see #getWidth() 
     * @see #getHeight() 
     */
    public int getIndex(int x, int y) {
        return y * width + x;
    }

    /** Checks if new frame is available.
     * 
     * @return true if new frame is available
     */
    public boolean hasNewFrame() {
        return newFrame;
    }

    /** Returns a double[] buffer of latest frame. 
     * To access a particular pixel, use getIndex()
     * 
     * @return the double[] frame
     */
    public double[] getNewFrame() {
        newFrame = false;
        return displayFrame;
    }

    /** Returns  the latest float buffer. 
     * To access a particular pixel, use getIndex()
     * 
     * @return the float[] of pixel values
     * @see #getIndex(int, int) 
     */
    public float[] getDisplayBuffer() {
        newFrame = false;
        return displayBuffer.clone();
    }

    /** Tell chip to acquire new frame, return immediately.
     * 
     */
    public void acquireNewFrame() {
        apsChip.takeSnapshot();
    }
    
    public float getMinBufferValue(){
        float minBufferValue = 0.0f;
        if (logCompress) {
            minBufferValue = (float) Math.log(minBufferValue + logSafetyOffset);
        }
        return minBufferValue;
    }
    
    public float getMaxBufferValue(){
        float maxBufferValue = (float)maxADC;
        if (logCompress) {
            maxBufferValue = (float) Math.log(maxBufferValue + logSafetyOffset);
        }
        return maxBufferValue;
    }

    public void setExtRender(boolean setExt) {
        this.useExtRender = setExt;
    }
    
    public void setLegend(String legend) {
        this.apsDisplayLegend.s = legend;
    }

    public void setDisplayGrayFrame(double[] frame) {
        int xc = 0;
        int yc = 0;
        for (int i = 0; i < frame.length; i++) {
            apsDisplay.setPixmapGray(xc, yc, (float) frame[i]);
            xc++;
            if (xc == width) {
                xc = 0;
                yc++;
            }
        }
    }

    public void setDisplayFrameRGB(double[] frame) {
        int xc = 0;
        int yc = 0;
        for (int i = 0; i < frame.length; i += 3) {
            apsDisplay.setPixmapRGB(xc, yc, (float) frame[i + 2], (float) frame[i + 1], (float) frame[i]);
            xc++;
            if (xc == width) {
                xc = 0;
                yc++;
            }
        }
    }

    /**
     * @return the invertIntensity
     */
    public boolean isInvertIntensity() {
        return invertIntensity;
    }

    /**
     * @param invertIntensity the invertIntensity to set
     */
    public void setInvertIntensity(boolean invertIntensity) {
        this.invertIntensity = invertIntensity;
        putBoolean("invertIntensity", invertIntensity);
    }

    /**
     * @return the invertIntensity
     */
    public boolean isPreBufferFrame() {
        return preBufferFrame;
    }

    /**
     * @param invertIntensity the invertIntensity to set
     */
    public void setPreBufferFrame(boolean preBuffer) {
        this.preBufferFrame = preBuffer;
        putBoolean("preBufferFrame", preBufferFrame);
    }

    /**
     * @return the logDecompress
     */
    public boolean isLogDecompress() {
        return logDecompress;
    }

    /**
     * @param logDecompress the logDecompress to set
     */
    public void setLogDecompress(boolean logDecompress) {
        this.logDecompress = logDecompress;
        putBoolean("logDecompress", logDecompress);
    }

    /**
     * @return the logCompress
     */
    public boolean isLogCompress() {
        return logCompress;
    }

    /**
     * @param logCompress the logCompress to set
     */
    public void setLogCompress(boolean logCompress) {
        this.logCompress = logCompress;
        putBoolean("logCompress", logCompress);
    }

    /**
     * @return the displayContrast
     */
    public float getDisplayContrast() {
        return displayContrast;
    }

    /**
     * @param displayContrast the displayContrast to set
     */
    public void setDisplayContrast(float displayContrast) {
        this.displayContrast = displayContrast;
        putFloat("displayContrast", displayContrast);
        resetFilter();
    }

    /**
     * @return the displayBrightness
     */
    public float getDisplayBrightness() {
        return displayBrightness;
    }

    /**
     * @param displayBrightness the displayBrightness to set
     */
    public void setDisplayBrightness(float displayBrightness) {
        this.displayBrightness = displayBrightness;
        putFloat("displayBrightness", displayBrightness);
        resetFilter();
    }

    public Extraction getExtractionMethod() {
        return extractionMethod;
    }

    synchronized public void setExtractionMethod(Extraction extractionMethod) {
        getSupport().firePropertyChange("extractionMethod", this.extractionMethod, extractionMethod);
        putString("edgePixelMethod", extractionMethod.toString());
        this.extractionMethod = extractionMethod;
        resetFilter();
    }
      /**
     * @return the showAPSFrameDisplay
     */
    public boolean isShowAPSFrameDisplay() {
        return showAPSFrameDisplay;
    }

    /**
     * @param showAPSFrameDisplay the showAPSFrameDisplay to set
     */
    public void setShowAPSFrameDisplay(boolean showAPSFrameDisplay) {
        this.showAPSFrameDisplay = showAPSFrameDisplay;
        putBoolean("showAPSFrameDisplay", showAPSFrameDisplay);
        if(apsFrame!=null) apsFrame.setVisible(showAPSFrameDisplay);
    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
        super.setFilterEnabled(yes); //To change body of generated methods, choose Tools | Templates.
        if(!isFilterEnabled()){
            if(apsFrame!=null)apsFrame.setVisible(false);
        }
    }
    
    
       /** returns frame width in pixels.
     * @return the width
     */
    public int getWidth() {
        return width;
    }

 
    /**
     * returns frame height in pixels
     * @return the height
     */
    public int getHeight() {
        return height;
    }

    /**
     * returns max ADC value
     * @return the maxADC
     */
    public int getMaxADC() {
        return maxADC;
    }

    /**
     * returns max index into frame buffer arrays
     * @return the maxIDX
     */
    public int getMaxIDX() {
        return maxIDX;
    }

    
}
