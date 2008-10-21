/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.caviar.hardwareinterface;

/**
 * Signals a blank or unprogrammed device.
 * 
 * @author tobi
 */
public class BlankDeviceException extends HardwareInterfaceException {
    public BlankDeviceException(String s){
        super(s);
    }
}
