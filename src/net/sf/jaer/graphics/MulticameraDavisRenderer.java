/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.graphics;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.orientation.BinocularDisparityEvent;
import net.sf.jaer.event.BinocularEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.MultiCameraEvent;

/**
 *
 * @author Gemma
 */
public class MulticameraDavisRenderer extends DavisRenderer{
    
    protected float disparityColors[][];
    protected int NOF_DISPARITY_COLORS = 32;
    protected int NOF_CAMERAS = 3;
    protected int minValue = Integer.MAX_VALUE;
    protected int maxValue = Integer.MIN_VALUE;
    public enum MultiCameraColorMode{
        RedGreen, RedGreenBlack, Disparity
    };
    MultiCameraColorMode[] multiCameraColorModes = MultiCameraColorMode.values(); // array of mode enums
    MultiCameraColorMode multiCameraColorMode;

    {
        MultiCameraColorMode oldMode = MultiCameraColorMode.valueOf(prefs.get("MultiCameraRenderer.MultiCameraColorMode",MultiCameraColorMode.RedGreen.toString()));
        for ( MultiCameraColorMode c:multiCameraColorModes ){
            if ( c == oldMode ){
                multiCameraColorMode = c;
            }
        }
    }

    /**
     * Creates a new instance of BinocularRenderer.
     *
     * @param chip the chip we're rendering for
     */
    public MulticameraDavisRenderer (AEChip chip){
        super(chip);
        createDisparityColors();
    }

    @Override
    public synchronized void render (EventPacket packet){
        if ( packet == null ){
            return;
        }
        if ( !( packet.getEventPrototype() instanceof MultiCameraEvent ) ){
            super.render(packet);
            return;
        }
        int n = packet.getSize();
        int skipBy = 1;
        if ( isSubsamplingEnabled() ){
            while ( (n / skipBy) > getSubsampleThresholdEventCount() ){
                skipBy++;
            }
        }
        checkPixmapAllocation();
        resetSelectedPixelEventCount();
        float a;
        float step = 1f / ( colorScale ); // amount to step rendering gray level up or down for each event
        float[] f = getPixmapArray();
        try{
            float sc = 1f / getColorScale();
            int rgbChan = 0;
            // leave disparity mode if input is not instanceof BinocularDisparityEvent
            if ( (multiCameraColorMode == MultiCameraColorMode.Disparity) && !( packet.getEventPrototype() instanceof MultiCameraEvent ) ){
                log.info("Setting color mode to RedGreen rather than Disparity because packet does not contain disparity events");
                setColorMode(MultiCameraColorMode.RedGreen);
            }
            // reset the min/max values for disparity rendering every time you leave the disparity rendering mode
            if ( multiCameraColorMode != MultiCameraColorMode.Disparity ){
                minValue = Integer.MAX_VALUE;
                maxValue = Integer.MIN_VALUE;
            }

            boolean igpol = isIgnorePolarityEnabled();
            switch ( multiCameraColorMode ){
                case RedGreen:
                    // default rendering mode, rendering binocular events without disparity, right=red, left=green
                    if ( !accumulateEnabled ){
                        if ( !igpol ){
                            resetFrame(0.5f);
                        } else{
                            resetFrame(0f);
                        }
                    }
                    for ( int i = 0 ; i < packet.getSize() ; i += skipBy ){
                        MultiCameraEvent e = (MultiCameraEvent)packet.getEvent(i);
                        rgbChan = e.camera;
                        if ( (e.x == xsel) && (e.y == ysel) ){
                            playSpike(e.getType());
                        }
                        int ind = getPixMapIndex(e.x,e.y) + rgbChan;
                        if ( !igpol ){
                            switch ( e.polarity ){
                                case Off:
                                    f[ind] -= step; // eventContrastRecip; // off cell divides gray
                                    break;
                                case On:
                                default:
                                    f[ind] += step; //eventContrast; // on multiplies gray
                                    break;
                            }
                        } else{
                            f[ind] += sc;
                        }
                    }
                    break;
                case Disparity:
                    // disparity event rendering mode: blue is far, red is near
                    resetFrame(0f);
                    for ( int i = 0 ; i < n ; i += skipBy ){
//                        MultiCameraEvent e = (MultiCameraEvent)packet.getEvent(i);
//                        if ( (e.x == xsel) && (e.y == ysel) ){
//                            playSpike(e.getType());
//                        }
//                        if ( e.disparity < minValue ){
//                            minValue = e.disparity;
//                        }
//                        if ( e.disparity > maxValue ){
//                            maxValue = e.disparity;
//                        }
//                        float tmp = (float)NOF_DISPARITY_COLORS / (float)( maxValue - minValue );
//                        int idx = (int)( ( e.disparity - minValue ) * tmp );
//                        if ( idx >= NOF_DISPARITY_COLORS ){
//                            idx = NOF_DISPARITY_COLORS - 1;
//                        } else if ( idx < 0 ){
//                            idx = 0;
//                        }
//                        int ind = getPixMapIndex(e.x,e.y);
//                        f[ind] = disparityColors[idx][0];
//                        f[ind + 1] = disparityColors[idx][1];
//                        f[ind + 2] = disparityColors[idx][2];
                   }

//                    //display the color scale in the lower left corner
//                    for (int i=0;i<NOF_DISPARITY_COLORS;i++) {
//                        fr[0][i][0] = disparityColors[i][0];
//                        fr[0][i][1] = disparityColors[i][1];
//                        fr[0][i][2] = disparityColors[i][2];
//                        fr[1][i][0] = disparityColors[i][0];
//                        fr[1][i][1] = disparityColors[i][1];
//                        fr[1][i][2] = disparityColors[i][2];
//                    }
                    break;
                case RedGreenBlack:
                    // disparity event rendering mode: blue is far, red is near
                    if ( !accumulateEnabled ){
                            resetFrame(0f);
                    }
                    for ( int i = 0 ; i < packet.getSize() ; i += skipBy ){
                        MultiCameraEvent e = (MultiCameraEvent)packet.getEvent(i);
                        rgbChan = e.camera;
                        if ( (e.x == xsel) && (e.y == ysel) ){
                            playSpike(e.getType());
                        }
                        int ind = getPixMapIndex(e.x,e.y) + rgbChan;
                        f[ind] += sc;
//                        f[ind+3]=1; // alpha
                    }
                    break;
                default:
            }

            autoScaleFrame(f);
        } catch ( ArrayIndexOutOfBoundsException e ){
            e.printStackTrace();
            log.warning(e.getCause() + ": some event out of bounds for this chip type?");
        }
    }

    /** Creates a color map for disparities */
    protected void createDisparityColors (){
        disparityColors = new float[ NOF_DISPARITY_COLORS ][ 3 ];
        int i = 0;
        int k = 0;
        for ( ; k < NOF_CAMERAS ; k++){
            for ( ; i < (NOF_DISPARITY_COLORS / NOF_CAMERAS) ; i++ ){
                disparityColors[i][k%3] = 0f;
                disparityColors[i][(k+1)%3] = i / ( NOF_DISPARITY_COLORS / NOF_CAMERAS );
                disparityColors[i][(k+2)%3] = 1f - (i / ( NOF_DISPARITY_COLORS / NOF_CAMERAS ));  
            }                      
        }
        
    }

    /**@param stereoColorMode the rendering method, e.g. gray, red/green opponency, time encoded.
     */
    public synchronized void setColorMode (MultiCameraColorMode multiCameraColorMode){
        this.multiCameraColorMode = multiCameraColorMode;
        prefs.put("MultiCameraRenderer.MultiCameraColorMode",multiCameraColorMode.toString());
        log.info("set multiCameraColorMode=" + multiCameraColorMode);
    }

    /** go on to next rendering method */
    @Override
    public synchronized void cycleColorMode (){
        int m = multiCameraColorMode.ordinal();
        if ( ++m >= multiCameraColorModes.length ){
            m = 0;
        }
        setColorMode(multiCameraColorModes[m]);
    }
    
    public synchronized int getNumCameras (){
        return NOF_CAMERAS;
    }
    
    public synchronized void setNumCameras (int n){
        NOF_CAMERAS=n;
    }
}
