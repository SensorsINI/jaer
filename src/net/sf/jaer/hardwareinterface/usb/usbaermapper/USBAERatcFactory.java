/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.hardwareinterface.usb.usbaermapper;

import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.HardwareInterfaceFactoryInterface;

/**
 *
 * @author Manuel Dominguez
 */
public class USBAERatcFactory implements HardwareInterfaceFactoryInterface{

    static USBAERatcFactory instance=new USBAERatcFactory();
    static USBAERatc usbaeratc=new USBAERatc();
    HardwareInterface interfaces[];

    USBAERatcFactory(){}

    public static HardwareInterfaceFactoryInterface instance(){
        return instance;
    }

    public int getNumInterfacesAvailable() {
        int status=0;
        try {
            status = usbaeratc.getNumDevices();
            
        } catch (InterruptedException ex) {
            Logger.getLogger(USBAERatcFactory.class.getName()).log(Level.SEVERE, null, ex);
        }
//        System.out.println(silabs.getNumDevices()+" SiLabsC8051F320 interfaces available ");
        if(status<0) {
            System.err.println("USBAERats.getNumInterfacesAvailable(): couldn't determine number of devices, "+status);
            return 0;
        }else{
            interfaces=new HardwareInterface[status];
        }
        return status;
    }

    public HardwareInterface getFirstAvailableInterface() throws HardwareInterfaceException {
        return getInterface(0);
    }

    public HardwareInterface getInterface(int n) throws HardwareInterfaceException {
        if(n>getNumInterfacesAvailable()-1) {
            System.err.println("USBAERatcFactory.getInterface(): couldn't get interface number "+n);
            return null;
        }
        USBAERatc u=new USBAERatc();
        u.setInterfaceNumber(n);
        return u;
    }

}
