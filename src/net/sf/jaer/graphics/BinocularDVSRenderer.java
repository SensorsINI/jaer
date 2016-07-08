/*
 * BinocularDVSRenderer.java
 *
 * Created on December 23, 2005, 2:43 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */
package net.sf.jaer.graphics;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.orientation.BinocularDisparityEvent;
import net.sf.jaer.event.BinocularEvent;
import net.sf.jaer.event.EventPacket;
/**
 * Renders a stereo pair of retinas.
 * Each eye is rendered in a different color,
 * and each eye uses a monochrome scale like the one for normal single eye input.
 * There is only one rendering method which is contrast based, and it uses one common contrast scale.
 *
 * @author tobi
 */
public class BinocularDVSRenderer extends AEFrameChipRenderer{
    protected float disparityColors[][];
    protected int NOF_DISPARITY_COLORS = 32;
    protected int minValue = Integer.MAX_VALUE;
    protected int maxValue = Integer.MIN_VALUE;
    public enum StereoColorMode{
        RedGreen, RedGreenBlack, Disparity
    };
    StereoColorMode[] stereoColorModes = StereoColorMode.values(); // array of mode enums
    StereoColorMode stereoColorMode;

    {
        StereoColorMode oldStereoMode = StereoColorMode.valueOf(prefs.get("BinocularRenderer.stereoColorMode",StereoColorMode.RedGreen.toString()));
        for ( StereoColorMode c:stereoColorModes ){
            if ( c == oldStereoMode ){
                stereoColorMode = c;
            }
        }
    }

    /**
     * Creates a new instance of BinocularRenderer.
     *
     * @param chip the chip we're rendering for
     */
    public BinocularDVSRenderer (AEChip chip){
        super(chip);
        createDisparityColors();
    }

    @Override
    public synchronized void render (EventPacket packet){
        if ( packet == null ){
            return;
        }
        if ( !( packet.getEventPrototype() instanceof BinocularEvent ) ){
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
            if ( (stereoColorMode == StereoColorMode.Disparity) && !( packet.getEventPrototype() instanceof BinocularDisparityEvent ) ){
                log.info("Setting color mode to RedGreen rather than Disparity because packet does not contain disparity events");
                setColorMode(StereoColorMode.RedGreen);
            }
            // reset the min/max values for disparity rendering every time you leave the disparity rendering mode
            if ( stereoColorMode != StereoColorMode.Disparity ){
                minValue = Integer.MAX_VALUE;
                maxValue = Integer.MIN_VALUE;
            }

            boolean igpol = isIgnorePolarityEnabled();
            switch ( stereoColorMode ){
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
                        BinocularEvent e = (BinocularEvent)packet.getEvent(i);
                        if ( e.eye == BinocularEvent.Eye.RIGHT ){
                            rgbChan = 0;
                        } else{
                            rgbChan = 1; // red right
                        }
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
                        BinocularDisparityEvent e = (BinocularDisparityEvent)packet.getEvent(i);
                        if ( (e.x == xsel) && (e.y == ysel) ){
                            playSpike(e.getType());
                        }
                        if ( e.disparity < minValue ){
                            minValue = e.disparity;
                        }
                        if ( e.disparity > maxValue ){
                            maxValue = e.disparity;
                        }
                        float tmp = (float)NOF_DISPARITY_COLORS / (float)( maxValue - minValue );
                        int idx = (int)( ( e.disparity - minValue ) * tmp );
                        if ( idx >= NOF_DISPARITY_COLORS ){
                            idx = NOF_DISPARITY_COLORS - 1;
                        } else if ( idx < 0 ){
                            idx = 0;
                        }
                        int ind = getPixMapIndex(e.x,e.y);
                        f[ind] = disparityColors[idx][0];
                        f[ind + 1] = disparityColors[idx][1];
                        f[ind + 2] = disparityColors[idx][2];
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
                        BinocularEvent e = (BinocularEvent)packet.getEvent(i);
                        if ( e.eye == BinocularEvent.Eye.RIGHT ){
                            rgbChan = 0;
                        } else{
                            rgbChan = 1; // red right
                        }
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
        for ( ; i < (NOF_DISPARITY_COLORS / 2) ; i++ ){
            disparityColors[i][0] = 0f;
            disparityColors[i][1] = i / ( NOF_DISPARITY_COLORS / 2f );
            disparityColors[i][2] = 1f - (i / ( NOF_DISPARITY_COLORS / 2f ));
        }
        for ( ; i < NOF_DISPARITY_COLORS ; i++ ){
            disparityColors[i][0] = ( i - (NOF_DISPARITY_COLORS / 2f) ) / ( NOF_DISPARITY_COLORS / 2f );
            disparityColors[i][1] = 1f - (( i - (NOF_DISPARITY_COLORS / 2f) ) / ( NOF_DISPARITY_COLORS / 2f ));
            disparityColors[i][2] = 0f;
        }
    }

    /**@param stereoColorMode the rendering method, e.g. gray, red/green opponency, time encoded.
     */
    public synchronized void setColorMode (StereoColorMode stereoColorMode){
        this.stereoColorMode = stereoColorMode;
        prefs.put("BinocularRenderer.stereoColorMode",stereoColorMode.toString());
        log.info("set stereoColorMode=" + stereoColorMode);
    }

    /** go on to next rendering method */
    @Override
    public synchronized void cycleColorMode (){
        int m = stereoColorMode.ordinal();
        if ( ++m >= stereoColorModes.length ){
            m = 0;
        }
        setColorMode(stereoColorModes[m]);
    }
}
