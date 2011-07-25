/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.hardwareinterface.serial;

import java.io.*;
//import java.net.*;
import java.util.*;
//import java.util.logging.*;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;


import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
 

import net.sf.jaer.eventio.*;
import net.sf.jaer.hardwareinterface.*;
import net.sf.jaer.hardwareinterface.serial.eDVS128.eDVS128;


/**
 *
 * @author lou
 */
public class SerialInterfaceFactory implements HardwareInterfaceFactoryInterface {
    private ArrayList<HardwareInterface> availableInterfaces = new ArrayList<HardwareInterface>();
    private static SerialInterfaceFactory instance = new SerialInterfaceFactory();

    public static final String DEVICE_FILE="/dev/ttyUSB0";


    private SerialInterfaceFactory() {
        buildSerialIoList();
    }

    public static HardwareInterfaceFactoryInterface instance(){
        return instance;
    }

    @Override
    synchronized public HardwareInterface getFirstAvailableInterface(){
        return getInterface(0);
    }

    @Override
    synchronized public HardwareInterface getInterface(int n){
        return availableInterfaces.get(n);
    }

    private void buildSerialIoList() {
        try{    
            availableInterfaces.add(new eDVS128(DEVICE_FILE));
        
        }
        catch (Exception e){
            e.printStackTrace();
        }


    }

    @Override
    synchronized public int getNumInterfacesAvailable(){

        //buildSerialIoList();
 
        return availableInterfaces.size();
    }

    @Override
    public String getGUID(){return null;}

}