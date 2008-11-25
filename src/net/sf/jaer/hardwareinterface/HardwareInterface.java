/*
 * HardwareInterface.java
 *
 * Created on October 10, 2005, 2:16 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package net.sf.jaer.hardwareinterface;

/**
 * General interface to hardware
 *
 * @author tobi
 */
public interface HardwareInterface {
    
    /** get text name of interface, e.g. "CypressFX2" or "SiLabsC8051F320" */
    public String getTypeName();
    
    /**
     * Closes the device and frees the internal device handle. Never throws an exception.
     */
    public void close();
    
    /** Opens the device driver and gets a handle to the device which is internally
     * maintained.
     *@throws HardwareInterfaceException if there is a problem. Diagnostics are printeds.
     */
    public void open() throws HardwareInterfaceException;
    
    /** @return true if interface is open, false otherwise */
    public boolean isOpen();
    
    
}
