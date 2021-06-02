/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.visualize.ini.convnet;

import ch.unizh.ini.jaer.projects.npp.DvsFramerSingleFrame;
import java.util.logging.Logger;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.MultiCameraApsDvsEvent;
import net.sf.jaer.util.filter.LowpassFilter;

/**
 * Subsamples DVS Multicamerachip input ON and OFF events to a desired "frame" resolution in a
 * grayscale 2D histogram. By subsampling (accumulation) of events it performs
 * much better than downsampling the sparse DVS output. The output of the
 * subsampler is available as a float array that is scaled by the color scale
 * for each event (with sign for ON and OFF events) and clipped to 0-1 range.
 *
 * @author Gemma
 */
public class DvsFramerSingleFrameMultiCameraChip  extends DvsFramerSingleFrame{
    
    
    /**
     * Makes a new DvsSubsamplingTimesliceConvNetInput
     *
     * @param dimX
     * @param dimY
     * @param colorScale initial scale by which each ON or OFF event is added to
     * the pixmap.
     * @see #setDvsGrayScale(int)
     */
    public DvsFramerSingleFrameMultiCameraChip(AEChip chip) {
        super(chip);
    }
    
    /**
     * Adds event from a source event location to the map
     *
     * @param e
     * @param srcWidth width of originating source sensor, e.g. 240 for DAVIS240
     * @param srcHeight height of source address space
     */
    public void addEvent(MultiCameraApsDvsEvent e) {

        if (e.isSpecial() || e.isFilteredOut()) {
            return;
        }
        initialize(e);
        int x = e.x, y = e.y;
        int camera = e.camera;
        //Redisposition of the 4 images from the 4 cameras in a square
        //   |camera0 camera2|
        //   |camera1 camera3|
        int X,Y; // X and Y are the coordinates in the reshaped image        
        
        int column=0;
        int row=0;
        if (camera>=2){
            column= 1;
        }
        if (camera%2==0){
            row= 1;
        }
        int srcWidthSigleCamera=chip.getSizeX()/4; //srcWidth=chip.width; for the MultiCameraChip it is numCameras*width
        int srcHeightSigleCamera=chip.getSizeY(); //srcHeigth=chip.heigth; it doesn't change, horizontal display in the AEViewer
        
        X=x+column*srcWidthSigleCamera;
        Y=y+row*srcHeightSigleCamera;
        
        int srcWidthReshaped=srcWidthSigleCamera*2; //reshapde size in the square configuration
        int srcHeightReshaped=chip.getSizeY()*2;
        
        int xReshaped = (int) Math.floor(((float) X / srcWidthReshaped) * super.getOutputImageWidth()); //coordinates in the reshaped configuration
        int yReshaped = (int) Math.floor(((float) Y / srcHeightReshaped) * super.getOutputImageHeight());

        dvsFrame.addEvent(xReshaped, yReshaped, e.polarity, e.timestamp);
    }
}
