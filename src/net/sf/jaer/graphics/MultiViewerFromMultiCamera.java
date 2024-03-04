/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.graphics;


import ch.unizh.ini.jaer.chip.multicamera.MultiDavisCameraChip;
import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.DavisVideoContrastController;
import java.awt.Frame;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.MultiCameraApsDvsEvent;

/**
 * Displays events from different cameras in separate window in the same AEViewer
 * @author Gemma
 */
public class MultiViewerFromMultiCamera extends DavisRenderer{
    
//    public Point2D.Float translationPixels = new Point2D.Float(0, 0); 
//    final float rotationRad=0.0f;
//    ImageTransform imageTransform=new ImageTransform(translationPixels, rotationRad);
    int NumCam;//=((MultiDavisCameraChip)chip).NUM_CAMERAS;
    
   /**
     * Creates a new instance of DavisDisplayMethod
     * @param chip
     */
    public MultiViewerFromMultiCamera(AEChip chip) {
        super(chip);  
        
        if (chip.getNumPixels() == 0) {
            log.warning("chip has zero pixels; is the constuctor of AEFrameChipRenderer called before size of the AEChip is set?");
            return;
        }
        
        Frame frame=new Frame();
        try{
                NumCam = Integer.parseInt((String) JOptionPane.showInputDialog(frame,"Define the number of Cameras","Number of Cameras",JOptionPane.QUESTION_MESSAGE,null,null,null));
            } catch(Exception ex){
                log.log(Level.SEVERE,"number cameras not defined, setting to 1 camera",ex);
                NumCam=1;
            }
        chip.setSizeX(sizeX*NumCam);

        checkPixmapAllocation();
        
        if (chip instanceof MultiDavisCameraChip) {
            contrastController = new DavisVideoContrastController((MultiDavisCameraChip) chip);
            contrastController.getSupport().addPropertyChangeListener(this);
        } else {
            log.warning("cannot make a DavisVideoContrastController for this chip because it does not extend DavisChip");
        }
        
    }
      
    private int lastPrintedBadEventCount = 0;
    
    @Override
    protected int getIndex(final BasicEvent ep) {
        MultiCameraApsDvsEvent e = (MultiCameraApsDvsEvent) ep;
        final int x = e.x, y = e.y;
        final int ID=e.camera;
        
        if ((x < 0) || (y < 0) || (x >= sizeX) || (y >= sizeY)) {
            badEventCount++;
            if ((System.currentTimeMillis() - lastWarningPrintedTimeMs) > INTERVAL_BETWEEEN_OUT_OF_BOUNDS_EXCEPTIONS_PRINTED_MS) {
                int newBadEventCount = badEventCount - lastPrintedBadEventCount;
                log.warning(String.format(
                        "Event %s out of bounds and cannot be rendered in bounds sizeX=%d sizeY=%d\n delaying next warning for %dms\n %d bad events since last warning",
                        e.toString(), sizeX, sizeY, INTERVAL_BETWEEEN_OUT_OF_BOUNDS_EXCEPTIONS_PRINTED_MS, newBadEventCount));
                lastPrintedBadEventCount = badEventCount;
                lastWarningPrintedTimeMs = System.currentTimeMillis();

            }

            return -1;
        }
        
        return getPixMapIndex(x, y, ID);
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

    public int getPixMapIndex(final int x, final int y, final int ID) {
        return 4 * ((y* textureWidth) + x +ID*(sizeX/NumCam));
    }
    
    /**
     * Overridden to return ON and OFF map values as R and G channels. B channel
     * is returned 0. Note that this method returns rendering of events; it
     * disregards APS frame values.
     *
     * @param x
     * @param y
     * @param ID
     * @return
     */
    public float[] getDvsRenderedValuesAtPixel(final int x, final int y, final int ID) {
        final int k = getPixMapIndex(x, y, ID);
        final float[] f = new float[3];
        f[0] = dvsEventsMap.get(k + 0);
        f[1] = dvsEventsMap.get(k + 1);
        f[2] = 0; // return alpha channel which is the ON and OFF value that is rendered (RGB are 1 for ON and OFF maps)
        return f; // To change body of generated methods, choose Tools | Templates.
    }
    
    /**
     * Overridden to combine RGB values to a gray value by averaging them. Note
     * that this method returns rendering of frame image; it returns the
     * rendered APS samples and not the raw ADC values.
     *
     * @param x
     * @param y
     * @return
     */
    public float getApsGrayValueAtPixel(final int x, final int y, final int ID) {
        final int k = getPixMapIndex(x, y, ID);
        final float[] pm = pixmap.array();
        return (pm[k] + pm[k + 1] + pm[k + 2]) / 3;
    }
    
    /**
     * Sets RGB color components all to same gray value g
     *
     * @param x pixel x, 0,0 is LL corner
     * @param y pixel y
     * @param g gray value in range 0-1
     */
    public void setApsGrayValueAtPixel(final int x, final int y, final float g, final int ID) {
        final int k = getPixMapIndex(x, y, ID);
        final float[] pm = pixmap.array();
        pm[k] = g;
        pm[k + 1] = g;
        pm[k + 2] = g;
    }
    
    /**
     * sets a specific color (rgb float 0-1) of the pixmap with default alpha
     * value
     *
     * @param x
     * @param y
     * @param value a float[3] array of RGB values
     * @see #setAnnotateAlpha(float)
     */

    public void setAnnotateColorRGB(final int x, final int y, final float[] value, final int ID) {
        final int index = getPixMapIndex(x, y, ID);
        annotateMap.put(index, value[0]);
        annotateMap.put(index + 1, value[1]);
        annotateMap.put(index + 2, value[2]);
        annotateMap.put(index + 3, getAnnotateAlpha());
    }
    
    /**
     * sets the alpha value of a specific pixel
     *
     * @param x
     * @param y
     * @param value a float alpha value
     */
    public void setAnnotateAlpha(final int x, final int y, final float alpha, final int ID) {
        final int index = getPixMapIndex(x, y, ID);
        annotateMap.put(index + 3, alpha);
    }

    
//    @Override
//    public synchronized void render(final EventPacket pkt) {
//        EventPacket packet=pkt;
//        
//        setColors();
//        checkPixmapAllocation();
//        
//        if ( packet == null ){
//            return;
//        }
//        
//        if ( !(packet.getEventPrototype() instanceof MultiCameraApsDvsEvent )){
//            super.render(packet);
//            return;
//        }
//        
//        if (!accumulateEnabled) {
//            Arrays.fill(dvsEventsMap.array(), 0.0f);
//            Arrays.fill(offMap.array(), 0.0f);
//        }
//           
//        final boolean displayEvents = isDisplayEvents();
//        final Iterator itr = packet.inputIterator();
//        while (itr.hasNext()) {
//            final MultiCameraApsDvsEvent e = (MultiCameraApsDvsEvent) itr.next();
//            int camera=e.camera;
//            final int type = e.getType();
//            if (displayEvents) {
//                if ((xsel >= 0) && (ysel >= 0)) { // find correct mouse pixel interpretation to make sounds for large
//                    // pixels
//                    final int xs = xsel, ys = ysel;
//                    if ((e.x == xs) && (e.y == ys)) {
//                        playSpike(type);
//                    }
//                }  
//            }
//            
//            final int index = getIndex(e);
////            System.out.println("index:" +index+ " camera: "+ e.camera+ " x: "+ e.x+" y: "+e.y);
//            if ((index < 0) || (index >= annotateMap.array().length)) {
//                return;
//            }else{
//                float[] map;
//                if (e.polarity == PolarityEvent.Polarity.On) {
//                    map = dvsEventsMap.array();
//                } else {
//                    map = offMap.array();
//                }
//                if(!ignorePolarityEnabled){
//                    if (e.polarity == PolarityEvent.Polarity.On) {
//                        map[index] = onColor[0]+(float)camera/NumCam;
//                        map[index + 1] = onColor[1]-(float)camera/NumCam;
//                        map[index + 2] = onColor[2]+(float)camera/NumCam;
//                    } else {
//                        map[index] = offColor[0]-(float)camera/NumCam;
//                        map[index + 1] = offColor[1]+(float)camera/NumCam;
//                        map[index + 2] = offColor[2]+(float)camera/NumCam;
//                    }
//                }else{
//                    map[index] = (float)(NumCam-camera)/NumCam;
//                    map[index + 1] = abs((float)(NumCam-camera-0.5f))/NumCam;
//                    map[index + 2] = (float)camera/NumCam; 
//                }   
//                final float alpha = map[index + 3] + (1.0f / colorScale);
//                map[index + 3] = normalizeEvent(alpha);
//            }
//        }
//    }
//        
//    private void setColors() {
//        checkPixmapAllocation();
//        switch (colorMode) {
//            case GrayLevel:
//            case Contrast:
//                onColor[0] = 1.0f;
//                onColor[1] = 1.0f;
//                onColor[2] = 1.0f;
//                onColor[3] = 0.0f;
//                offColor[0] = 0.0f;
//                offColor[1] = 0.0f;
//                offColor[2] = 0.0f;
//                offColor[3] = 0.0f;
//                break;
//            case RedGreen:
//            default:    
//                onColor[0] = 0.0f;
//                onColor[1] = 1.0f;
//                onColor[2] = 0.0f;
//                onColor[3] = 0.0f;
//                offColor[0] = 1.0f;
//                offColor[1] = 0.0f;
//                offColor[2] = 0.0f;
//                offColor[3] = 0.0f;
//                break;
//        }
//    }
    
    public int getNumCam() {
        return NumCam;
    }
}