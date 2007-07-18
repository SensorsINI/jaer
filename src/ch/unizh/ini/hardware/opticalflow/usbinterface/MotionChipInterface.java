/*
 * MotionChipInterface.java
 *
 * Created on November 24, 2006, 6:48 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.hardware.opticalflow.usbinterface;

import ch.unizh.ini.caviar.biasgen.BiasgenHardwareInterface;
import ch.unizh.ini.caviar.hardwareinterface.*;
import ch.unizh.ini.hardware.opticalflow.chip.*;

/**
 * The hardware interface to the motion chip.
 
 * @author tobi
 */
public interface MotionChipInterface extends BiasgenHardwareInterface {
    
    /** sets the data to be captured in each frame. The mode bits define which data the device will capture (device dependent) 
     @param mode the mode 
     */
    public void setCaptureMode(int mode);
    
    /** Returns the latest data from the device
     @return the data
     @throws TimeOutException if the exchange with the device times out
     */
    public MotionData getData() throws java.util.concurrent.TimeoutException;
    
}
