/*
 * USBInterface.java
 *
 * Created on September 20, 2005, 10:45 PM
 */

package ch.unizh.ini.caviar.hardwareinterface.usb;

import ch.unizh.ini.caviar.hardwareinterface.*;


/**
 * Interface to a USB device. Includes constants such as assigned VID and PID range for jAER devices.
 *
 * @author tobi
 */
public interface USBInterface extends HardwareInterface {
    
    // new VID/PID assigned from Thesycon 16.6.2008 (stored in jAER/docs/USBIO_VID_PID_Assignments_Neuroinformatik.pdf
    // are as follows
    // VID = 0x152A (thesycon)
    // PID =0x8400 to 0x841f (32 PIDs)
    
    /** The vendor ID that goes with VID/PIDs bought from Thesycon */
    static public final short VID_THESYCON=0x152a;
    /** The starting PID that goes with VID_THESYCON */
    static public final short PID_THESYCON_START=(short)0x8400;
    /** The ending PID that goes with VID_THESYCON */
    static public final short PID_THESYCON_END=(short)0x841f;

     
   /** return the string USB descriptors for the device
     *@return String[] of USB descriptors
     */
    public String[] getStringDescriptors();
    
    /** return the USB VID/PID of the interface
     *@return int[] of length 2 containing the Vendor ID (VID) and Product ID (PID) of the device. First element is VID, second element is PID.
     *@deprecated use getVID and getPID instead
     */
    public int[] getVIDPID();
    
    /**@return PID (vendor ID) */
    public short getVID();
    
    /**@return PID (product ID) */
    public short getPID();
    
    /**@return DID (device ID). A device may not implement this or may not have had it set. Returns 0 then. */
    public short getDID();
    
     
}
