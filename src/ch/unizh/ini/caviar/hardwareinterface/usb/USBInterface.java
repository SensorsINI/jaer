/*
 * USBInterface.java
 *
 * Created on September 20, 2005, 10:45 PM
 */

package ch.unizh.ini.caviar.hardwareinterface.usb;

import ch.unizh.ini.caviar.hardwareinterface.*;


/**
 * Interface to a USB device.
 *
 * @author tobi
 */
public interface USBInterface extends HardwareInterface {
    
     
   /** return the string USB descriptors for the device
     *@return String[] of USB descriptors
     */
    public String[] getStringDescriptors();
    
    /** return the USB VID/PID of the interface
     *@return int[] of length 2 containing the Vendor ID (VID) and Product ID (PID) of the device. First element is VID, second element is PID.
     */
    public int[] getVIDPID();
    
    /**@return PID (vendor ID) */
    public short getVID();
    
    /**@return PID (product ID) */
    public short getPID();
    
    /**@return DID (device ID). A device may not implement this or may not have had it set. Returns 0 then. */
    public short getDID();
    
     
}
