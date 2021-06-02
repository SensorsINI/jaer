/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package net.sf.jaer.hardwareinterface.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.logging.Logger;

import net.sf.jaer.eventio.AENetworkInterfaceConstants;
import net.sf.jaer.hardwareinterface.HardwareInterfaceFactoryInterface;
import net.sf.jaer.hardwareinterface.udp.smartEyeTDS.SmartEyeTDS;

/**
 * Simple interface factory for the UDP devices.
 *
 * @author braendch
 */
public class UDPInterfaceFactory implements HardwareInterfaceFactoryInterface {
    //TODO Implement a nice way to build the list of available UDPInterfaces

    private static final Logger log = Logger.getLogger("USBIOHardwareInterfaceFactory");
    /**
     * Each call to {@link #getNumInterfacesAvailable() } waits this long for a data packet.
     */
    public static final int TIMEOUT_MS = 50;

    private ArrayList<String> availableInterfaces = new ArrayList<String>();

    private static UDPInterfaceFactory instance = new UDPInterfaceFactory();
     private  byte[] buf = null;  // init when needed, null afterward
    private        DatagramPacket packet = null; // init when needed

    //private static UDPInterfaceFactory instance = null;

    private UDPInterfaceFactory() {
        buildUdpIoList();
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
        buildUdpIoList();
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

    private void buildUdpIoList(){
        long startTime=System.nanoTime();
        try {
            DatagramSocket socket = new DatagramSocket(SmartEyeTDS.STREAM_PORT);
            socket.setReuseAddress(true);
            if(buf==null)  buf= new byte[AENetworkInterfaceConstants.DATAGRAM_BUFFER_SIZE_BYTES];
            if(packet==null) packet=new DatagramPacket(buf, buf.length);
            packet.setData(buf);
            try {
//                log.info("waiting 100ms for UDP clients to connect");
                socket.setSoTimeout(TIMEOUT_MS);
                socket.receive(packet);
                if(packet != null && !availableInterfaces.contains("SmartEyeTDS")){
                    availableInterfaces.add("SmartEyeTDS");
                    buf=null;  // reclaim memory
                    packet=null;
                    log.info("UDP Interface 'SmartEyeTDS' found");
                }
            } catch (IOException ex) {
                //TODO:ugly exception handling
            }
            socket.close();
            socket=null; // garbage collect, don't allow weak references to build up
        } catch (SocketException ex) {
            //TODO:ugly exception handling
        }
        long totalTimeNs=System.nanoTime()-startTime;
//        log.info(String.format("It required %.3fs to find %d UDP interfaces",1e-9f*totalTimeNs,availableInterfaces.size()));
    }

    /** Computes and then returns number of interfaces available. Takes macroscopic time for datagram sockets to time out.
     * @return the number of compatible monitor/sequencer attached to the driver
     */
    @Override
    synchronized public int getNumInterfacesAvailable() {
        buildUdpIoList();
        return availableInterfaces.size();
    }

    @Override
    public String getGUID() {
        return null;
    }
}
