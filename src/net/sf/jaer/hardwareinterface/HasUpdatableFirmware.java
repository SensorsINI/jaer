/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.hardwareinterface;

/**
 * Interface has firmware that can be updated from the host, e.g. via USB. A hardware interface that implements this interface
 * can easily be updated.
 * 
 * @author tobi
 */
public interface HasUpdatableFirmware {
        
    /** Updates the firmware. Implementing classes must define how this is achieved, e.g. by 
     * downloading firmware from the project resources stored in the classpath as part of the project build.
     * 
     * @throws net.sf.jaer.hardwareinterface.HardwareInterfaceException if there is any error, including lack of support
     * or missing firmware file. The exception should detail the error as much as possible.
     */
    public void updateFirmware() throws HardwareInterfaceException;
    
    /**
     * returns the version of the current firmware.
     * 
     * Note: To obtain the version usually you can return the DID from the USBInterface 
     * 
     * @return version number
     */
    public int getVersion();       
}
