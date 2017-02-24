/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.graphics;


import ch.unizh.ini.jaer.chip.multicamera.MultiDVS128CameraChip;
import ch.unizh.ini.jaer.chip.multicamera.MultiDavisCameraChip;
import com.jogamp.opengl.GL;
import static com.jogamp.opengl.GL.GL_COLOR_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_BUFFER_BIT;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2ES1;
import static com.jogamp.opengl.GL2ES3.GL_COLOR;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;

import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.DisplayMethod;
import net.sf.jaer.graphics.DisplayMethod3D;
import net.sf.jaer.chip.Chip2D;

import com.jogamp.opengl.util.gl2.GLUT;
import eu.seebetter.ini.chips.davis.DavisBaseCamera;
import eu.seebetter.ini.chips.davis.DavisBaseCamera.DavisDisplayMethod;
import java.awt.geom.Point2D;
import static java.lang.Math.abs;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Iterator;
import net.sf.jaer.JAERViewer;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.MultiCameraApsDvsEvent;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.graphics.ChipCanvas.Zoom;
import net.sf.jaer.stereopsis.MultiCameraHardwareInterface;

/**
 * Displays events from different cameras in separate window in the same AEViewer
 * @author Gemma
 */
public class MultiViewerFromMultiCamera extends AEFrameChipRenderer{
    
//    public Point2D.Float translationPixels = new Point2D.Float(0, 0); 
//    final float rotationRad=0.0f;
//    ImageTransform imageTransform=new ImageTransform(translationPixels, rotationRad);
    int NumCam=4;//((MultiDavisCameraChip)chip).NUM_CAMERAS;
    
   /**
     * Creates a new instance of DavisDisplayMethod
     * @param chip
     */
    public MultiViewerFromMultiCamera(AEChip chip) {
        super(chip);
        NumCam=4;
        chip.setSizeX(sizeX*NumCam);
        checkPixmapAllocation();
        
    }
    
    @Override
    protected void checkPixmapAllocation() {
        
        if ((sizeX != chip.getSizeX()) || (sizeY != chip.getSizeY())) {
            sizeX = chip.getSizeX();
            sizeY = chip.getSizeY();            
        }
        
        textureWidth = AEFrameChipRenderer.ceilingPow2(sizeX);
        textureHeight = AEFrameChipRenderer.ceilingPow2(sizeY);
        System.out.println(textureWidth);
        System.out.println(textureHeight);
        final int n = 4 * textureWidth * textureHeight;
        System.out.println(n);
        if ((pixmap == null) || (pixmap.capacity() < n) || (pixBuffer.capacity() < n) || (onMap.capacity() < n) || (offMap.capacity() < n)
                || (annotateMap.capacity() < n)) {
            pixmap = FloatBuffer.allocate(n); // BufferUtil.newFloatBuffer(n);
            pixBuffer = FloatBuffer.allocate(n);
            onMap = FloatBuffer.allocate(n);
            offMap = FloatBuffer.allocate(n);
            annotateMap = FloatBuffer.allocate(n);
        }
    }
    
    
    protected int getIndex(final MultiCameraApsDvsEvent e) {
        final int x = e.x, y = e.y;
        final int ID=e.camera;
        return getPixMapIndexMultiCamera(x, y, ID);
    }
    
    /**
     * Returns index into pixmap. To access RGB values, just add 0,1, or 2 to
     * the returned index.
     *
     * @param x
     * @param y
     * @param ID
     * @return the index
     * @see #getPixmapArray()
     * @see #getPixmap()
     */

    public int getPixMapIndexMultiCamera(final int x, final int y, final int ID) {
        return 4 * ((y* textureWidth) + x +ID*(sizeX/NumCam));
    }
    
    @Override
    public synchronized void render(final EventPacket pkt) {
        EventPacket packet=pkt;
        
        setColors();
        checkPixmapAllocation();
        
        if ( packet == null ){
            return;
        }
        
        if ( !(packet.getEventPrototype() instanceof MultiCameraApsDvsEvent )){
            super.render(packet);
            return;
        }
        
        if (!accumulateEnabled) {
            Arrays.fill(onMap.array(), 0.0f);
            Arrays.fill(offMap.array(), 0.0f);
        }
           
        final boolean displayEvents = isDisplayEvents();
        final Iterator itr = packet.inputIterator();
        while (itr.hasNext()) {
            final MultiCameraApsDvsEvent e = (MultiCameraApsDvsEvent) itr.next();
            int camera=e.camera;
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
            System.out.println("index:" +index+ " camera: "+ e.camera+ " x: "+ e.x+" y: "+e.y);
            if ((index < 0) || (index >= annotateMap.array().length)) {
                return;
            }else{
                float[] map;
                if (e.polarity == PolarityEvent.Polarity.On) {
                    map = onMap.array();
                } else {
                    map = offMap.array();
                }
                if(!ignorePolarityEnabled){
                    if (e.polarity == PolarityEvent.Polarity.On) {
                        map[index] = onColor[0]+(float)camera/NumCam;
                        map[index + 1] = onColor[1]-(float)camera/NumCam;
                        map[index + 2] = onColor[2]+(float)camera/NumCam;
                    } else {
                        map[index] = offColor[0]-(float)camera/NumCam;
                        map[index + 1] = offColor[1]+(float)camera/NumCam;
                        map[index + 2] = offColor[2]+(float)camera/NumCam;
                    }
                }else{
                    map[index] = (float)(NumCam-camera)/NumCam;
                    map[index + 1] = abs((float)(NumCam-camera-0.5f))/NumCam;
                    map[index + 2] = (float)camera/NumCam; 
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
}