/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.hardwareinterface.udp;

import java.util.*;
import java.util.logging.*;

import net.sf.jaer.hardwareinterface.*;
import net.sf.jaer.hardwareinterface.udp.SmartEyeTDS.*;

/**
 *
 * @author braendch
 */
public class UDPInterfaceFactory implements HardwareInterfaceFactoryInterface {
    //TODO Implement a nice way to build the list of available UDPInterfaces

    static Logger log = Logger.getLogger("USBIOHardwareInterfaceFactory");

    private ArrayList<String> availableInterfaces = new ArrayList<String>();

    private static UDPInterfaceFactory instance = new UDPInterfaceFactory();

    //private static UDPInterfaceFactory instance = null;

    UDPInterfaceFactory() {
        availableInterfaces.add("SmartEyeTDS");
    }

    /** @return singleton instance */
    public static HardwareInterfaceFactoryInterface instance() {
        return instance;
    }

    /** returns the first interface in the list
     *@return reference to the first interface in the list
     */
    @Override
    synchronized public UDPInterface getFirstAvailableInterface() {
        return getInterface(0);
    }

     /** returns the n-th interface in the list
     *
     *@param n the number to instance (0 based)
     */
    @Override
    synchronized public UDPInterface getInterface(int n) {
        int numAvailable=getNumInterfacesAvailable();
        if(n>numAvailable-1){
            if(numAvailable>0){ // warn if there is at least one available but we asked for one higher than we have
                log.warning("only "+numAvailable+" interfaces available but you asked for number "+n);
            }
            return null;
        }
        if(n==0){
            return new SmartEyeTDS(0);
        } else {
            return null;
        }
    }

    /** @return the number of compatible monitor/sequencer attached to the driver
     */
    @Override
    synchronized public int getNumInterfacesAvailable() {
        return availableInterfaces.size();
    }
}
