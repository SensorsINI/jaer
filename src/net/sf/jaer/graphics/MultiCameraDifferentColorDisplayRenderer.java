/*
 * MultiCameraDisplayRenderer.java
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.graphics;
    
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.MultiCameraApsDvsEvent;
import net.sf.jaer.event.MultiCameraEvent;
import ch.unizh.ini.jaer.chip.multicamera.MultiDavisCameraChip;
import static java.lang.Math.abs;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import net.sf.jaer.event.PolarityEvent;
import java.util.BitSet;




/**
 * Renderer for multiple cameras set up.
 * Each camera is rendered in a different color on the same Display.
 * Each camera uses a monochrome scale like the one for normal single eye input.
 *
 * @author Gemma
 */
public class MultiCameraDifferentColorDisplayRenderer extends DavisRenderer{
    
    int camera;
    int numCam=1; //default value
    

    /**
     * Creates a new instance of MultiCameraDisplayRenderer.
     *
     * @param chip the chip we're rendering for
     */
    public MultiCameraDifferentColorDisplayRenderer (AEChip chip){
        super(chip);
        if (chip instanceof MultiDavisCameraChip){
            numCam=((MultiDavisCameraChip)chip).NUM_CAMERAS;
        } 
        onColor = new float[4];
        offColor = new float[4];

    }

    @Override
    public synchronized void render (EventPacket packet){
   
        if ( packet == null ){
            return;
        }
        
        if ( !(packet.getEventPrototype() instanceof MultiCameraApsDvsEvent )){
            super.render(packet);
            return;
        }
        
        setColors();
        if (!accumulateEnabled) {
            Arrays.fill(dvsEventsMap.array(), 0.0f);
//            Arrays.fill(offMap.array(), 0.0f);
        }

        checkPixmapAllocation();
        resetSelectedPixelEventCount(); // TODO fix locating pixel with xsel ysel
        setSpecialCount(0);
           
        final boolean displayEvents = isDisplayEvents();
        final Iterator itr = packet.inputIterator();
        while (itr.hasNext()) {
            final MultiCameraApsDvsEvent e = (MultiCameraApsDvsEvent) itr.next();
            camera=e.camera;
            if (e.isSpecial()) {
                incrementSpecialCount(1);
                continue;
            }
            final int type = e.getType();
            if (displayEvents) {
                if ((xsel >= 0) && (ysel >= 0)) { // find correct mouse pixel interpretation to make sounds for large
                    // pixels
                    final int xs = xsel, ys = ysel;
                    if ((e.x == xs) && (e.y == ys)) {
                        playSpike(type);
                    }
                }
                
            }
            final int index = getIndex(e);
            if ((index < 0) || (index >= annotateMap.array().length)) {
                return;
            }else{
                float[] map;
//                if (e.polarity == PolarityEvent.Polarity.On) {
                    map = dvsEventsMap.array();
//                } else {
//                    map = offMap.array();
//                }
                if(!ignorePolarityEnabled){
                    if (e.polarity == PolarityEvent.Polarity.On) {
                        map[index] = onColor[0]+(float)camera/numCam;
                        map[index + 1] = onColor[1]-(float)camera/numCam;
                        map[index + 2] = onColor[2]+(float)camera/numCam;
                    } else {
                        map[index] = offColor[0]-(float)camera/numCam;
                        map[index + 1] = offColor[1]+(float)camera/numCam;
                        map[index + 2] = offColor[2]+(float)camera/numCam;
                    }
                }else{
                    map[index] = (float)(numCam-camera)/numCam;
                    map[index + 1] = abs((float)(numCam-camera-0.5f))/numCam;
                    map[index + 2] = (float)camera/numCam; 
                }   
                final float alpha = map[index + 3] + (1.0f / colorScale);
                map[index + 3] = normalizeEvent(alpha);
            }
        }
    }
       
    private void setColors() {
        checkPixmapAllocation();
        switch (colorMode) {
            case GrayLevel:
//            case Contrast:
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

 }

