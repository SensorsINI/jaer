/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.multicamera;

/**
 *
 * @author Gemma
 *
 * MultiDAVIS240CCameraChip.java
 *
 */

import net.sf.jaer.Description;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import eu.seebetter.ini.chips.davis.DAVIS240C;
import eu.seebetter.ini.chips.davis.Davis240Config;
import java.awt.Point;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.graphics.DavisRenderer;
import net.sf.jaer.graphics.MultiCameraDifferentColorDisplayRenderer;
import net.sf.jaer.graphics.MultiViewerFromMultiCamera;


@Description("A multi Davis retina each on it's own USB interface with merged and presumably aligned fields of view")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class MultiDAVIS240CCameraChip extends MultiDavisCameraChip {
    
    public int NUM_CAMERAS;
    
    /** Creates a new instance of  */
    public MultiDAVIS240CCameraChip() {
        super();

        setCameraChip(new DAVIS240C());
        setSizeX(DAVIS240C.WIDTH_PIXELS);
        setSizeY(DAVIS240C.HEIGHT_PIXELS);
        setADCMax(DAVIS240C.MAX_ADC);

//        setRenderer(new MultiCameraDifferentColorDisplayRenderer (this));
        setRenderer(new MultiViewerFromMultiCamera (this));
        NUM_CAMERAS=((MultiViewerFromMultiCamera )this.renderer).getNumCam();
        setNumCameras(NUM_CAMERAS);
        setDisplayCamera(NUM_CAMERAS);
        
        setApsFirstPixelReadOut(new Point(0, DAVIS240C.WIDTH_PIXELS - 1));
        setApsLastPixelReadOut(new Point(DAVIS240C.HEIGHT_PIXELS - 1, 0));        
        setDefaultPreferencesFile("biasgenSettings/Davis240bc/MultiDAVIS240CCameraChip.xml");
        setBiasgen(new Biasgen(this)); 
  
    }
           
     public MultiDAVIS240CCameraChip(final HardwareInterface hardwareInterface) {
            this();
            setHardwareInterface(hardwareInterface);
	}
     
      /**
     * A biasgen for this multicamera combination of DAVIS 240 C. The biases are simultaneously controlled.
     * @author tobi
     */
    public class Biasgen extends Davis240Config {

        /** Creates a new instance of Biasgen for DAVIS 240 C with a given hardware interface
         *@param chip the hardware interface on this chip is used
         */
        public Biasgen(final Chip chip) {
            super(chip);         
//            this.setCaptureFramesEnabled(false);
            this.setDisplayFrames(false);
            this.setImuEnabled(false);
            this.setDisplayImu(false);
            setName("MultiDAVIS240CCameraChip");
        }
    }
}

