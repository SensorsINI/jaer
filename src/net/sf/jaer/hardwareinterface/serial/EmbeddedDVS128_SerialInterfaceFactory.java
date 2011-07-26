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
import net.sf.jaer.hardwareinterface.serial.eDVS128.eDVS128_HardwareInterface;


/**
 * Manufactures serial port FTDI interfaces to eDVS 128x128 cameras.
 * 
 * @author Lie Lou
 */
public class EmbeddedDVS128_SerialInterfaceFactory implements HardwareInterfaceFactoryInterface {
    private ArrayList<HardwareInterface> availableInterfaces = new ArrayList<HardwareInterface>();
    private static EmbeddedDVS128_SerialInterfaceFactory instance = new EmbeddedDVS128_SerialInterfaceFactory();

    public static final String DEVICE_FILE="/dev/ttyUSB0"; // TODO fix for multiple platforms and ports


    private EmbeddedDVS128_SerialInterfaceFactory() {
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
            availableInterfaces.add(new eDVS128_HardwareInterface(DEVICE_FILE)); // TODO hardcoded serial port here must allow choice
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