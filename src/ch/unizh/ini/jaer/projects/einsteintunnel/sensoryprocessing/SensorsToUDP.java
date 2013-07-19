/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.einsteintunnel.sensoryprocessing;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;


/**
 *
 * @author braendch
 */
@Description("A filter built for the Einstein tunnel that extracts information and sends it to a UDP port")
public class SensorsToUDP extends EventFilter2D {

    public int csx, maxHistogramX, packetCounter;
    public int commandPort = 20021;
    public int dsx = 504;
    public int dsy = 80;
    public short[] xHistogram;
    public double decayFactor = 0.9;

    public DatagramSocket socket;
    public InetAddress address;

    public SensorsToUDP(AEChip chip) {
        super(chip);

        initFilter();
    }

    public void initFilter() {
        resetFilter();
    }

    synchronized public void resetFilter() {
        if(chip!=null){
            csx = chip.getSizeX();
            xHistogram = new short[dsx];
        }
        maxHistogramX = 1; // not 0 to avoid division by 0
        short val = 0;
        Arrays.fill(xHistogram,val);
        packetCounter = 0;
        try {
            socket = new DatagramSocket();
        } catch (SocketException ex) {
            Logger.getLogger(SensorsToUDP.class.getName()).log(Level.SEVERE, null, ex);
        }
        try {
            address = InetAddress.getByName("localhost");
        } catch (UnknownHostException ex) {
            Logger.getLogger(SensorsToUDP.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {

        byte[] message = new byte[4+4+dsx*2];

        if(!isFilterEnabled()) return in;
        if(getEnclosedFilter()!=null) in=getEnclosedFilter().filterPacket(in);
        if(getEnclosedFilterChain()!=null) in=getEnclosedFilterChain().filterPacket(in);

        for(BasicEvent e:in){
            xHistogram[e.x*dsx/csx] += 1;
        }
        //sequence #
        message[0]=(byte)((packetCounter & 0xff000000)>>>24);
        message[1]=(byte)((packetCounter & 0x00ff0000)>>>16);
        message[2]=(byte)((packetCounter & 0x0000ff00)>>>8);
        message[3]=(byte)((packetCounter & 0x000000ff));
        //msg
        message[4]=(byte)'h';
        message[5]=(byte)'i';
        message[6]=(byte)'s';
        message[7]=(byte)'t';
        //data 
        for (int i=0; i<dsx; i++){
            message[8+2*i]=(byte)((xHistogram[i] & 0xff00>>>8));
            message[9+2*i]=(byte)((xHistogram[i] & 0x00ff));
        }

        try {
            sendPacket(message);
        } catch (IOException ex) {
            Logger.getLogger(SensorsToUDP.class.getName()).log(Level.SEVERE, null, ex);
        }

        for(int i = 0; i<xHistogram.length; i++){
            xHistogram[i] = (short)(xHistogram[i]*decayFactor);
        }
        packetCounter += 1;
        return in;

    }

    public void sendPacket(byte[] message) throws IOException {
  
        DatagramPacket packet = new DatagramPacket(message, message.length, address, commandPort);
        socket.send(packet);
    }
}
