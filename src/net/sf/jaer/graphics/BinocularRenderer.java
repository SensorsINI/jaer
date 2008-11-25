/*
 * BinocularRenderer.java
 *
 * Created on December 23, 2005, 2:43 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package net.sf.jaer.graphics;

import net.sf.jaer.chip.*;
import net.sf.jaer.event.*;

/**
 * Renders a stereo pair of retinas. 
 * Each eye is rendered in a different color, 
 * and each eye uses a monochrome scale like the one for normal single eye input.
 * There is only one rendering method which is contrast based, and it uses one common contrast scale.
 *
 * @author tobi
 */
public class BinocularRenderer extends AEChipRenderer {
    protected float disparityColors[][];
    protected int NOF_DISPARITY_COLORS = 32;
    
    protected int minValue = Integer.MAX_VALUE;
    protected int maxValue = Integer.MIN_VALUE;
    
    public enum StereoColorMode {RedGreen, Disparity};
    StereoColorMode[] stereoColorModes=StereoColorMode.values(); // array of mode enums
    StereoColorMode stereoColorMode;
    {
        StereoColorMode oldStereoMode=StereoColorMode.valueOf(prefs.get("ChipRenderer.stereoColorMode",StereoColorMode.RedGreen.toString()));
        for(StereoColorMode c:stereoColorModes){
            if(c==oldStereoMode) stereoColorMode=c;
        }
    }
        
    /**
     * Creates a new instance of BinocularRenderer.
     * 
     * @param chip the chip we're rendering for
     */
    public BinocularRenderer(AEChip chip) {
        super(chip);
        createDisparityColors();
    }
    
    public float[][][] render(EventPacket packet) {
        if(packet==null) return fr;
        if(!(packet.getEventPrototype() instanceof BinocularEvent)) return super.render(packet);
        int n=packet.getSize();
        int skipBy=1;
        if(isSubsamplingEnabled()){
            while(n/skipBy>getSubsampleThresholdEventCount()){
                skipBy++;
            }
        }
        checkFr();
        selectedPixelEventCount = 0; // init it for this packet
        float a;
        float  step = 1f / (colorScale); // amount to step rendering gray level up or down for each event

        try{
            float eventContrastRecip=1/eventContrast;
            float sc=1f/getColorScale();
            int rgbChan=0;
            // leave disparity mode if input is not instanceof BinocularDisparityEventEvent
            if (stereoColorMode == StereoColorMode.Disparity && !(packet.getEventPrototype() instanceof BinocularDisparityEvent)) {
                setColorMode(StereoColorMode.RedGreen);
            }
            // reset the min/max values for disparity rendering every time you leave the disparity rendering mode
            if (stereoColorMode != StereoColorMode.Disparity) {
                minValue = Integer.MAX_VALUE;
                maxValue = Integer.MIN_VALUE;
            }
            
            switch(stereoColorMode) {
                case RedGreen:
                    // default rendering mode, rendering binocular events without disparity    
                    boolean igpol=isIgnorePolarityEnabled();
                    if(!accumulateEnabled) {
                        if(!igpol) resetFrame(0.5f); else resetFrame(0f);
                    }
                    for(int i=0;i<packet.getSize();i+=skipBy){
                        BinocularEvent e=(BinocularEvent)packet.getEvent(i);
                        if(e.eye==BinocularEvent.Eye.RIGHT) {
                            rgbChan=0;
                        }else {
                            rgbChan=1; // red right
                        }
                            if (e.x == xsel && e.y == ysel)
                                playSpike(e.getType());
                        a=(fr[e.y][e.x][rgbChan]);
                        if(!igpol){
                            switch(e.polarity){
                                case Off:
                                    a-=step; // eventContrastRecip; // off cell divides gray
                                    break;
                                case On:
                                default:
                                    a+=step; //eventContrast; // on multiplies gray
                                    break;
                            }
                        }else{
                            a+=sc;
                        }
                        fr[e.y][e.x][rgbChan]=a;
                    }
                break;
                case Disparity:
                    // disparity event rendering mode: blue is far, red is near
                    resetFrame(0f);
                    for(int i=0;i<n;i+=skipBy){
                        BinocularDisparityEvent e=(BinocularDisparityEvent)packet.getEvent(i);
                           if (e.x == xsel && e.y == ysel)
                                playSpike(e.getType());
                        if (e.disparity < minValue) minValue = e.disparity;
                        if (e.disparity > maxValue) maxValue = e.disparity;
                        float tmp = (float)NOF_DISPARITY_COLORS/(float)(maxValue - minValue);
                        int idx = (int)((e.disparity - minValue)*tmp);
                        if (idx >= NOF_DISPARITY_COLORS) idx = NOF_DISPARITY_COLORS - 1; else if (idx < 0) idx = 0;
                        fr[e.y][e.x][0] = disparityColors[idx][0];
                        fr[e.y][e.x][1] = disparityColors[idx][1];
                        fr[e.y][e.x][2] = disparityColors[idx][2];
                    }
                    
                    //display the color scale in the lower left corner
                    for (int i=0;i<NOF_DISPARITY_COLORS;i++) {
                        fr[0][i][0] = disparityColors[i][0];
                        fr[0][i][1] = disparityColors[i][1];
                        fr[0][i][2] = disparityColors[i][2];
                        fr[1][i][0] = disparityColors[i][0];
                        fr[1][i][1] = disparityColors[i][1];
                        fr[1][i][2] = disparityColors[i][2];
                    }
                break;
                default:
            }
            
            autoScaleFrame(fr,grayValue);
//            annotate();
        }catch(ArrayIndexOutOfBoundsException e){
            e.printStackTrace();
            log.warning(e.getCause()+": some event out of bounds for this chip type?");
        }
        return fr;
    }
    
    /** Creates a color map for disparities */
    protected void createDisparityColors(){
        disparityColors = new float[NOF_DISPARITY_COLORS][3];
        int i = 0;
        for(; i < NOF_DISPARITY_COLORS/2; i++) {
            disparityColors[i][0] = 0f;
            disparityColors[i][1] = (float)i/(NOF_DISPARITY_COLORS/2f);
            disparityColors[i][2] = 1f - (float)i/(NOF_DISPARITY_COLORS/2f);
        }
        for(; i < NOF_DISPARITY_COLORS; i++) {
            disparityColors[i][0] = ((float)i - NOF_DISPARITY_COLORS/2f)/(NOF_DISPARITY_COLORS/2f);
            disparityColors[i][1] = 1f - ((float)i - NOF_DISPARITY_COLORS/2f)/(NOF_DISPARITY_COLORS/2f);
            disparityColors[i][2] = 0f;
        }
    }
    
    /**@param stereoColorMode the rendering method, e.g. gray, red/green opponency, time encoded.
     */
    public synchronized void setColorMode(StereoColorMode stereoColorMode) {
        this.stereoColorMode=stereoColorMode;
        prefs.put("BinocularRenderer.stereoColorMode",stereoColorMode.toString());
        log.info("set stereoColorMode="+stereoColorMode);
    }
    
    /** go on to next rendering method */
    @Override public synchronized void cycleColorMode(){
        int m=stereoColorMode.ordinal();
        if(++m>=stereoColorModes.length) m=0;
        setColorMode(stereoColorModes[m]);
    }
}
