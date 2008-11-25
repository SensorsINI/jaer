/*
 * SiLabsC8051F320Factory.java
 *
 * Created on October 3, 2005, 2:04 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package net.sf.jaer.hardwareinterface.usb.silabs;

import net.sf.jaer.hardwareinterface.usb.*;
import net.sf.jaer.hardwareinterface.*;

/**
 * A factory for SiLabsC8051F320 devices. These are based (at least in these early versions) on the USBXPress driver from SiLabs. They don't have
 a GUID like the ones accessed by the UsbIo driver.
 
 * @author tobi
 */
public class SiLabsC8051F320Factory implements HardwareInterfaceFactoryInterface {
    
    static SiLabsC8051F320Factory instance=new SiLabsC8051F320Factory();
    static SiLabsC8051F320 silabs=new SiLabsC8051F320();
    
    /** Creates a new instance of SiLabsC8051F320Factory */
    SiLabsC8051F320Factory() {
    }
    
    /** @return singleton instance */
    public static HardwareInterfaceFactoryInterface instance(){
        return instance;
    }
    
    /** returns the number of available interfaces, i.e., the number of available hardware devices.
     * If the driver only supports one interface, then 1 will always be returned.
     * @return number of interfaces
     */
    /** gets the number of this type of interface available
     *@return number available
     */
    public int getNumInterfacesAvailable() {
        int status=silabs.getNumDevices();
//        System.out.println(silabs.getNumDevices()+" SiLabsC8051F320 interfaces available ");
        if(status<0) {
            System.err.println("SiLabsC8051F320.getNumInterfacesAvailable(): couldn't determine number of devices, "+SiLabsC8051F320.errorText(status));
            return 0;
        }
        return status; // this number could be 0,1,2 etc but only one device can be opened at a time using the present JVM
    }
    
    /** @return first available interface */
    public USBInterface getFirstAvailableInterface() {
        return getInterface(0);
    }
    
    public USBInterface getInterface(int n) {
        if(n>getNumInterfacesAvailable()-1) {
            System.err.println("SiLabsC8051F320Factory.getInterface(): couldn't get interface number "+n);
            return null;
        }
        SiLabsC8051F320 u=new SiLabsC8051F320();
        u.setInterfaceNumber(n);
        return u;
    }
    
    
}
