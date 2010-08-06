/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.hardwareinterface.udp.smartEyeTDS;

import net.sf.jaer.hardwareinterface.*;
import net.sf.jaer.hardwareinterface.udp.*;

/**
 *
 * @author braendch
 */
public class SmartEyeTDS implements UDPInterface{

    public static final int UDP_CONSOLE_PORT = 20010;
    
    boolean isOpened=false;    

    public void open() throws HardwareInterfaceException {
        int status=0;
        if(status==0) {
            isOpened=true;
            HardwareInterfaceException.clearException();
            return;
        }else {
            isOpened=false;
            throw new HardwareInterfaceException("nativeOpen: can't open device, device returned status ");
        }
    }

    public boolean isOpen() {
        return isOpened;
    }

    public void close(){
        isOpened=false;
    }

    public String getTypeName() {
        return "AIT SmartEye Traffic Data Sensor";
    }

}
