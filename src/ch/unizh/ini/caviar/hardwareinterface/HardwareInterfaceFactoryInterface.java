/*
 * HardwareInterfaceFactoryInterface.java
 *
 * Created on October 3, 2005, 11:45 AM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.caviar.hardwareinterface;


/**
 * A class that defines the interface a hardware interface factory has to have to be included in the list in HardwareInterfaceFactory.
 *
 * @author tobi
 */
public interface HardwareInterfaceFactoryInterface  {
    
   /** returns the number of available interfaces, i.e., the number of available hardware devices.
     * If the driver only supports one interface, then 1 will always be returned.
     * @return number of interfaces
     */
    public int getNumInterfacesAvailable();
        
    /** @return first available interface */
    public HardwareInterface getFirstAvailableInterface() throws HardwareInterfaceException ;
    
    public HardwareInterface getInterface(int n) throws HardwareInterfaceException ;
    
    
}
