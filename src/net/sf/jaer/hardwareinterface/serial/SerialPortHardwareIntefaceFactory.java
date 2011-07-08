/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.hardwareinterface.serial;

import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.HardwareInterfaceFactory;
import net.sf.jaer.hardwareinterface.HardwareInterfaceFactoryInterface;

/**
 * Factory for serial port interfaces.
 * 
 * @author tobi
 */
public class SerialPortHardwareIntefaceFactory implements HardwareInterfaceFactoryInterface {

    @Override
    public int getNumInterfacesAvailable() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public HardwareInterface getFirstAvailableInterface() throws HardwareInterfaceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public HardwareInterface getInterface(int n) throws HardwareInterfaceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getGUID() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
