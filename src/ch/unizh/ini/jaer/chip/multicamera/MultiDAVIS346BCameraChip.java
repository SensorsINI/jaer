/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.chip.multicamera;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import eu.seebetter.ini.chips.davis.Davis346B;
import eu.seebetter.ini.chips.davis.DavisTowerBaseConfig;
import java.awt.Point;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.MultiCameraDifferentColorDisplayRenderer;
import net.sf.jaer.graphics.MultiViewerFromMultiCamera;
import net.sf.jaer.hardwareinterface.HardwareInterface;

/**
 * A multi Davis346 retina each on it's own USB interface with merged and presumably aligned fields of view
 * @author Gemma
 */

@Description("A multi Davis346 retina each on it's own USB interface with merged and presumably aligned fields of view")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)

public class MultiDAVIS346BCameraChip extends MultiDavisCameraChip{
    
    public MultiDAVIS346BCameraChip() {
        super();
        Davis346B chip=new Davis346B();
        setCameraChip(chip);
        setSizeX(chip.WIDTH_PIXELS);
        setSizeY(chip.HEIGHT_PIXELS);
        
//        setRenderer(new MultiCameraDifferentColorDisplayRenderer (this));
        setRenderer(new MultiViewerFromMultiCamera (this));
        NUM_CAMERAS=((MultiViewerFromMultiCamera )this.renderer).getNumCam();
        setNumCameras(NUM_CAMERAS);
        setDisplayCamera(NUM_CAMERAS);
        
        setApsFirstPixelReadOut(new Point(0, chip.getSizeY() - 1));
        setApsLastPixelReadOut(new Point(chip.getSizeX() - 1, 0));
        setDefaultPreferencesFile("biasgenSettings/Davis346b/MultiDAVIS346BCameraChip.xml");
        setBiasgen(new Biasgen(this));
  
    }
    
    /** Creates a new instance of  */
              
     public MultiDAVIS346BCameraChip(final HardwareInterface hardwareInterface) {
            this();
            setHardwareInterface(hardwareInterface);
	}
     
     /**
     * A biasgen for this multicamera combination of DAVIS 346. The biases are simultaneously controlled.
     * @author tobi
     */
    public class Biasgen extends DavisTowerBaseConfig {

        /** Creates a new instance of Biasgen for DAVIS 346 with a given hardware interface
         *@param chip the hardware interface on this chip is used
         */
        public Biasgen(final Chip chip) {
            super(chip);         
            this.setCaptureFramesEnabled(false);
            this.setDisplayFrames(false);
            this.setImuEnabled(false);
            this.setDisplayImu(false);
            setName("MultiDAVIS346BCameraChip");
        }
    }
    
}


    
     

