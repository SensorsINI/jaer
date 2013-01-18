/*
 * ChipRenderer.java
 *
 * Created on May 2, 2006, 1:49 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package net.sf.jaer.graphics;

import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import eu.seebetter.ini.chips.sbret10.SBret10;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.*;
import net.sf.jaer.util.SpikeSound;
import java.awt.Color;
import java.beans.PropertyChangeSupport;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.logging.Logger;
import javax.swing.JButton;
import net.sf.jaer.util.filter.LowpassFilter2d;

/**
 * Class adpated from AEChipRenderer to render not only AE events but also
 * frames.
 * 
 * The frame buffer is RGBA so four bytes per pixel
 *
 * @author christian, tobi
 * @see ChipRendererDisplayMethod
 */
public class AEFrameChipRenderer extends AEChipRenderer {
    
    public boolean displayEvents;
    public boolean displayFrames;
    public int textureWidth; //due to hardware acceloration reasons, has to be a 2^x with x a natural number
    public int textureHeight; //due to hardware acceloration reasons, has to be a 2^x with x a natural number
    
    private int sizeX, sizeY, maxADC;
    private int timestamp = 0;
    private LowpassFilter2d agcFilter = new LowpassFilter2d();  // 2 lp values are min and max log intensities from each frame
    private float minAGC, maxAGC;
    private boolean agcEnabled;
    public boolean textureRendering = true;
    private float[] onColor, offColor;
    
    protected FloatBuffer pixBuffer;
    protected FloatBuffer onMap, onBuffer;
    protected FloatBuffer offMap, offBuffer;
    
    /** PropertyChange */
    public static final String AGC_VALUES = "AGCValuesChanged";
    /** PropertyChange when value is changed */
    public static final String APS_INTENSITY_GAIN = "apsIntensityGain", APS_INTENSITY_OFFSET = "apsIntensityOffset";
    /** Control scaling and offset of display of log intensity values. */
    int apsIntensityGain, apsIntensityOffset;
    
    public AEFrameChipRenderer(AEChip chip) {
        super(chip);
        agcEnabled = chip.getPrefs().getBoolean("agcEnabled", false);
        setAGCTauMs(chip.getPrefs().getFloat("agcTauMs", 1000));
        apsIntensityGain = chip.getPrefs().getInt("apsIntensityGain", 1);
        apsIntensityOffset = chip.getPrefs().getInt("apsIntensityOffset", 0);
        displayEvents = chip.getPrefs().getBoolean("displayLogIntensityChangeEvents", true);
        displayFrames = chip.getPrefs().getBoolean("displayIntensity", true);
        onColor = new float[4];
        offColor = new float[4];
        resetFrame(0.5f);
    }


    /** Overridden to make gray buffer special for bDVS array */
    @Override
    protected void resetPixmapGrayLevel(float value) {
        maxAGC = Float.MIN_VALUE;
        minAGC = Float.MAX_VALUE;
        checkPixmapAllocation();
        final int n = 4 * textureWidth * textureHeight;
        boolean madebuffer = false;
        if (grayBuffer == null || grayBuffer.capacity() != n) {
            grayBuffer = FloatBuffer.allocate(n); // BufferUtil.newFloatBuffer(n);
            madebuffer = true;
        }
        if (madebuffer || value != grayValue) {
            grayBuffer.rewind();
            for (int y = 0; y < textureWidth; y++) {
                for (int x = 0; x < textureHeight; x++) {
                    if(displayEvents){
                        grayBuffer.put(0);
                        grayBuffer.put(0);
                        grayBuffer.put(0);
                        grayBuffer.put(1.0f);
                    } else {
                        grayBuffer.put(grayValue);
                        grayBuffer.put(grayValue);
                        grayBuffer.put(grayValue);
                        grayBuffer.put(1.0f);
                    }
                }
            }
            grayBuffer.rewind();
        }
        System.arraycopy(grayBuffer.array(), 0, pixmap.array(), 0, n);
        System.arraycopy(grayBuffer.array(), 0, pixBuffer.array(), 0, n);
        pixmap.rewind();
        pixBuffer.rewind();
        pixmap.limit(n);
        pixBuffer.limit(n);
        setColors();
        resetEventMaps();
    }
    
    protected void resetEventMaps() {
        setColors();
        checkPixmapAllocation();
        final int n = 4 * textureWidth * textureHeight;
        if (grayBuffer == null || grayBuffer.capacity() != n) {
            grayBuffer = FloatBuffer.allocate(n); // BufferUtil.newFloatBuffer(n);
        }
        
        grayBuffer.rewind();
        for (int y = 0; y < textureWidth; y++) {
            for (int x = 0; x < textureHeight; x++) {
                grayBuffer.put(onColor[0]);
                grayBuffer.put(onColor[1]);
                grayBuffer.put(onColor[2]);
                grayBuffer.put(onColor[3]);
            }
        }
        System.arraycopy(grayBuffer.array(), 0, onMap.array(), 0, n);
        
        grayBuffer.rewind();
        for (int y = 0; y < textureWidth; y++) {
            for (int x = 0; x < textureHeight; x++) {
                grayBuffer.put(offColor[0]);
                grayBuffer.put(offColor[1]);
                grayBuffer.put(offColor[2]);
                grayBuffer.put(offColor[3]);
            }
        }
        System.arraycopy(grayBuffer.array(), 0, offMap.array(), 0, n);
        
        grayBuffer.rewind();
        onMap.rewind();
        offMap.rewind();
        onMap.limit(n);
        offMap.limit(n);
    }
    
    @Override
    public synchronized void setColorMode(ColorMode colorMode) {
        super.setColorMode(colorMode);
        setColors();
    }
    
    private void setColors(){
        checkPixmapAllocation();
        switch (colorMode) {
            case GrayLevel:
            case Contrast:
                onColor[0] = 1.0f;
                onColor[1] = 1.0f;
                onColor[2] = 1.0f;
                onColor[3] = 0.0f;
                offColor[0] = 0.0f;
                offColor[1] = 0.0f;
                offColor[2] = 0.0f;
                offColor[3] = 0.0f;
                break;
            case RedGreen:
            default:
                onColor[0] = 0.0f;
                onColor[1] = 1.0f;
                onColor[2] = 0.0f;
                onColor[3] = 0.0f;
                offColor[0] = 1.0f;
                offColor[1] = 0.0f;
                offColor[2] = 0.0f;
                offColor[3] = 0.0f;
                break;
        }
    }

    @Override
    public synchronized void render(EventPacket pkt) {

        if (!(pkt instanceof ApsDvsEventPacket)) return;

        ApsDvsEventPacket packet = (ApsDvsEventPacket) pkt;

        checkPixmapAllocation();
        resetSelectedPixelEventCount(); // TODO fix locating pixel with xsel ysel

        if (packet == null) {
            return;
        }
        this.packet = packet;
        if (packet.getEventClass() != ApsDvsEvent.class) {
            log.warning("wrong input event class, got " + packet.getEventClass() + " but we need to have " + ApsDvsEvent.class);
            return;
        }
        if (!accumulateEnabled){
//            resetFrame(0.5f);
            resetEventMaps();
        }
            
        Iterator allItr = packet.fullIterator();
        while(allItr.hasNext()){
            //The iterator only iterates over the DVS events
            ApsDvsEvent e = (ApsDvsEvent) allItr.next();                        
            int type = e.getType();
            if(!e.isAdcSample()){
                if(displayEvents){
                    if (xsel >= 0 && ysel >= 0) { // find correct mouse pixel interpretation to make sounds for large pixels
                        int xs = xsel, ys = ysel;
                        if (e.x == xs && e.y == ys) {
                            playSpike(type);
                        }
                    }
                    int index = getIndex(e.x, e.y);
                    switch (e.polarity) {
                        case On:
                            updateOnMap(index);
                            break;
                        case Off:
                            updateOffMap(index);
                            break;
                    }
                }
            }else if(e.isAdcSample() && displayFrames && !chip.getAeViewer().isPaused()){
                updateFrameBuffer(e);
            }
        }
//        pixmap.rewind();
//        pixBuffer.rewind();
//        onMap.rewind();
//        offMap.rewind();
    }
    
    private void updateFrameBuffer(ApsDvsEvent e){
        int index = getIndex(e.x, e.y);
        float[] buf = pixBuffer.array();
        if(index > buf.length)return;
        if(e.isA()){
            float val = e.getAdcSample();
            buf[index] = val;
            buf[index+1] = val;
            buf[index+2] = val;
            if(e.isStartOfFrame())startFrame(e.timestamp);
        }else if(e.isB()){
            float val = ((float)buf[index]-(float)e.getAdcSample());
            val = normalizeFramePixel(val);
            buf[index] = val;
            buf[index+1] = val;
            buf[index+2] = val;
            if(agcEnabled){
                if (val < minAGC) {
                    minAGC = val;
                } else if (val > maxAGC) {
                    maxAGC = val;
                }
            }
        }
        if(e.isEOF()){
            endFrame();
        }
    }
    
    private void startFrame(int ts){
        timestamp=ts;
        maxAGC = Float.MIN_VALUE;
        minAGC = Float.MAX_VALUE;
    }
    
    private void endFrame(){
        System.arraycopy(pixBuffer.array(), 0, pixmap.array(), 0, pixmap.array().length);
        if (minAGC > 0 && maxAGC > 0) { // don't adapt to first frame which is all zeros
            java.awt.geom.Point2D.Float filter2d = agcFilter.filter2d((float)minAGC, (float)maxAGC, timestamp);
            getSupport().firePropertyChange(AGC_VALUES, null, filter2d); // inform listeners (GUI) of new AGC min/max filterd log intensity values
        }
    }
    
    private void updateOnMap(int index){
        int alphaIdx = index + 3;
        float[] map = onMap.array();
        float alpha = map[alphaIdx]+(1.0f/(float)colorScale);
        alpha = normalizeEvent(alpha);
        map[alphaIdx] = alpha;
    }
    
    private void updateOffMap(int index){
        int alphaIdx = index + 3;
        float[] map = offMap.array();
        float alpha = map[alphaIdx]+(1.0f/(float)colorScale);
        alpha = normalizeEvent(alpha);
        map[alphaIdx] = alpha;
    }
    
    private int getIndex(int x, int y){
        if(textureRendering){
            return  4* (x + y * textureWidth);
        }else{
            return 4* (x + y * sizeX);
        }
    }
    
    @Override
    protected void checkPixmapAllocation() {
        if(sizeX != chip.getSizeX() || sizeY != chip.getSizeY()){
            sizeX = chip.getSizeX();
            textureWidth = ceilingPow2(sizeX);
            sizeY = chip.getSizeY();
            textureHeight = ceilingPow2(sizeY);
        }
        final int n = 4 * textureWidth * textureHeight;
        if (pixmap == null || pixmap.capacity() < n || pixBuffer.capacity() < n || onMap.capacity() < n || offMap.capacity() < n) {
            pixmap = FloatBuffer.allocate(n); // BufferUtil.newFloatBuffer(n);
            pixBuffer = FloatBuffer.allocate(n);
            onMap = FloatBuffer.allocate(n);
            offMap = FloatBuffer.allocate(n);
        }
    }
    
    /** Overrides color scale setting to update the stored accumulated pixmap when the color scale is changed.
    
     * */
    @Override
    synchronized public void setColorScale(int colorScale) {
        int old = this.colorScale;
        if (colorScale < 1) {
            colorScale = 1;
        }
        if (colorScale > 64) {
            colorScale = 64;
        }
        this.colorScale = colorScale;
        // we set eventContrast so that colorScale events takes us from .5 to 1, i.e., .5*(eventContrast^cs)=1, so eventContrast=2^(1/cs)
        eventContrast = (float) (Math.pow(2, 1.0 / colorScale)); // e.g. cs=1, eventContrast=2, cs=2, eventContrast=2^0.5, etc
        prefs.putInt("Chip2DRenderer.colorScale", colorScale);
        
        if (old == this.colorScale) {
            return;
        }
        float r = (float) old / colorScale; // e.g. r=0.5 when scale changed from 1 to 2
        if (onMap == null && offMap == null) {
            return;
        }
        
        switch (colorMode) {
            case GrayLevel:
            case Contrast:
                
                break;
            case RedGreen:
                    
                break;
            default:
                // rendering method unknown, reset to default value
                log.warning("colorMode " + colorMode + " unknown, reset to default value 0");
                setColorMode(ColorMode.GrayLevel);
        }
        getSupport().firePropertyChange(COLOR_SCALE, old, colorScale);
    }
    
     private static int ceilingPow2(int n) {
        int pow2 = 1;
        while (n > pow2) {
        pow2 = pow2<<1;
        }
        return pow2;
    }
     
     public FloatBuffer getOnMap(){
         onMap.rewind();
         checkPixmapAllocation();
         return onMap;
     }
     
     public FloatBuffer getOffMap(){
         offMap.rewind();
         checkPixmapAllocation();
         return offMap;
     }
     
    @Override
    public int getPixMapIndex(int x, int y) {
        return 4 * (x + y * sizeX);
    }
    
    @Override
    public int getWidth(){
        return textureWidth;
    }
    
    @Override
    public int getHeight(){
        return textureHeight;
    }

    private float normalizeFramePixel(float value) {
        float v;
        if (!agcEnabled) {
            v = (float) (apsIntensityGain*value+apsIntensityOffset) / (float) maxADC;
        } else {
            java.awt.geom.Point2D.Float filter2d = agcFilter.getValue2d();
            float offset = filter2d.x;
            float range = (filter2d.y - filter2d.x);
            v = ((value - offset)) / range;
//                System.out.println("offset="+offset+" range="+range+" value="+value+" v="+v);
        }
        if (v < 0) {
            v = 0;
        } else if (v > 1) {
            v = 1;
        }
        return v;
    }
    
    private float normalizeEvent(float value) {
        if (value < 0) {
            value = 0;
        } else if (value > 1) {
            value = 1;
        }
        return value;
    }

    public float getAGCTauMs() {
        return agcFilter.getTauMs();
    }

    public void setAGCTauMs(float tauMs) {
        if (tauMs < 10) {
            tauMs = 10;
        }
        agcFilter.setTauMs(tauMs);
        chip.getPrefs().putFloat("agcTauMs", tauMs);
    }

    /**
        * @return the agcEnabled
        */
    public boolean isAgcEnabled() {
        return agcEnabled;
    }

    /**
        * @param agcEnabled the agcEnabled to set
        */
    public void setAgcEnabled(boolean agcEnabled) {
        this.agcEnabled = agcEnabled;
        chip.getPrefs().putBoolean("agcEnabled", agcEnabled);
    }

    public void applyAGCValues() {
        java.awt.geom.Point2D.Float f = agcFilter.getValue2d();
        setApsIntensityOffset(agcOffset());
        setApsIntensityGain(agcGain());
    }

    private int agcOffset() {
        return (int) agcFilter.getValue2d().x;
    }

    private int agcGain() {
        java.awt.geom.Point2D.Float f = agcFilter.getValue2d();
        float diff = f.y - f.x;
        if (diff < 1) {
            return 1;
        }
        int gain = (int) (maxADC / (f.y - f.x));
        return gain;
    }
    
    public int getMaxADC(){
        return maxADC;
    }
    
    public void setMaxADC(int max){
        maxADC = max;
    }

    /**
        * Value from 1 to maxADC. Gain of 1, offset of 0 turns full scale ADC to 1. Gain of maxADC makes a single count go full scale.
        * @return the apsIntensityGain
        */
    public int getApsIntensityGain() {
        return apsIntensityGain;
    }

    /**
        * Value from 1 to maxADC. Gain of 1, offset of 0 turns full scale ADC to 1.
        * Gain of maxADC makes a single count go full scale.
        * @param apsIntensityGain the apsIntensityGain to set
        */
    public void setApsIntensityGain(int apsIntensityGain) {
        int old = this.apsIntensityGain;
        if (apsIntensityGain < 1) {
            apsIntensityGain = 1;
        } else if (apsIntensityGain > maxADC) {
            apsIntensityGain = maxADC;
        }
        this.apsIntensityGain = apsIntensityGain;
        chip.getPrefs().putInt("apsIntensityGain", apsIntensityGain);
        if (chip.getAeViewer() != null) {
            chip.getAeViewer().interruptViewloop();
        }
        getSupport().firePropertyChange(APS_INTENSITY_GAIN, old, apsIntensityGain);
    }

    /**
        * Value subtracted from ADC count before gain multiplication. Ranges from 0 to maxADC.
        * @return the apsIntensityOffset
        */
    public int getApsIntensityOffset() {
        return apsIntensityOffset;
    }

    /**
        * Sets value subtracted from ADC count before gain multiplication. Clamped between 0 to maxADC.
        * @param apsIntensityOffset the apsIntensityOffset to set
        */
    public void setApsIntensityOffset(int apsIntensityOffset) {
        int old = this.apsIntensityOffset;
        if (apsIntensityOffset < 0) {
            apsIntensityOffset = 0;
        } else if (apsIntensityOffset > maxADC) {
            apsIntensityOffset = maxADC;
        }
        this.apsIntensityOffset = apsIntensityOffset;
        chip.getPrefs().putInt("apsIntensityOffset", apsIntensityOffset);
        if (chip.getAeViewer() != null) {
            chip.getAeViewer().interruptViewloop();
        }
        getSupport().firePropertyChange(APS_INTENSITY_OFFSET, old, apsIntensityOffset);
    }
    
    public void setDisplayEvents(boolean displayEvents) {
        this.displayEvents = displayEvents;
    }

    public void setDisplayFrames(boolean displayFrames) {
       this.displayFrames = displayFrames;
    }

    public boolean isDisplayEvents() {
        return displayEvents;
    }

    public boolean isDisplayIntensity() {
        return displayFrames;
    }
    
    

}
