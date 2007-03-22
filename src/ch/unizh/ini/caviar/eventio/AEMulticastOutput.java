/*
 * AESocketOutputStream.java
 *
 * Created on June 30, 2006, 2:23 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright June 30, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.caviar.eventio;

import ch.unizh.ini.caviar.aemonitor.*;
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.*;

/**
 * Streams AE packets to network socket using DatagramPacket's that are multicast. AEViewers can receive these packets to render them.
 <p>
 The implementation using a BlockingQueue to buffer the AEPacketRaw's that are offered.
 *
 * @author tobi
 */
public class AEMulticastOutput {
    
//    enum State {WAITING,CONNECTED};
//    State state=State.WAITING;
    
    int sendBufferSize=0;
    final int QUEUE_LENGTH=100;
    BlockingQueue<DatagramPacket> queue=new ArrayBlockingQueue<DatagramPacket>(QUEUE_LENGTH);
    
    Logger log=Logger.getLogger("AESocketStream");
    protected DatagramChannel channel=null;
    protected MulticastSocket socket = null;
    InetAddress group=null;
    DatagramPacket packet = null;
    int packetSequenceNumber=0;
    InetAddress address = null;
    int packetSizeBytes;
    Thread consumerThread;
    
    /** Creates a new instance of AESocketOutputStream */
    public AEMulticastOutput() {
        try{
//            channel=DatagramChannel.open();
            socket=new MulticastSocket(AENetworkInterface.PORT);
            socket.setTimeToLive(1); // same LAN
            socket.setLoopbackMode(false);
            socket.setTrafficClass(0x10+0x08); // low delay
            log.info("output stream datagram traffic class is "+socket.getTrafficClass());
            address = InetAddress.getByName(AENetworkInterface.INETADDR);
            socket.joinGroup(address);
//            channel.configureBlocking(true);
//            socket=channel.socket();
//            socket = new DatagramSocket(AESocketStream.PORT);
            socket.setSendBufferSize(AENetworkInterface.SOCKET_BUFFER_SIZE_BYTES);
            sendBufferSize=socket.getSendBufferSize();
            if(sendBufferSize!=AENetworkInterface.SOCKET_BUFFER_SIZE_BYTES){
                log.warning("socket could not be sized to hold MAX_EVENTS="
                        +AENetworkInterface.MAX_EVENTS+" ("
                        +AENetworkInterface.SOCKET_BUFFER_SIZE_BYTES
                        +" bytes), could only get sendBufferSize="+sendBufferSize);
            }else{
                log.info("AESocketOutputStream.getSendBufferSize (bytes)="+sendBufferSize);
            }
        }catch(IOException e){
            e.printStackTrace();
        }
        try{
            group = InetAddress.getByName(AENetworkInterface.INETADDR);
        }catch(UnknownHostException e){
            e.printStackTrace();
        }
        packetSizeBytes=AENetworkInterface.MAX_EVENTS*AENetworkInterface.EVENT_SIZE_BYTES+Integer.SIZE/8;
        Consumer consumer=new Consumer(queue);
        consumerThread=new Thread(consumer,"AEMulticastOutput");
        consumerThread.start();
    }
    
    void init(){
        
    }
    
    /**
     * Writes the packet out as sequence of address/timestamp's, just as they came as input from the device. The notion of a packet is discarded
     *to simplify later reading an input stream from the output stream result. The AEPacketRaw is written in chunks of AESocketStream.SOCKET_BUFFER_SIZE bytes (which
     must be a multiple of AESocketStream.EVENT_SIZE_BYTES). Each DatagramPacket has a sequence number as the first Integer value which is used on the reciever to
     detect dropped packets.
     <p>
     If an empty packet is supplied as ae, then a packet is still written but it contains only a sequence number.
     
     *@param ae a raw addresse-event packet
     */
    public void writePacket(AEPacketRaw ae) throws IOException {
        if(socket==null) return;
        int nEvents=ae.getNumEvents();
        
        int npackets=1+nEvents/AENetworkInterface.MAX_EVENTS;
        if(npackets>1){
            log.info("splitting packet with "+nEvents+" events into "+npackets+" DatagramPackets each with "+AENetworkInterface.MAX_EVENTS+" events, starting with sequence number "+packetSequenceNumber);
        }
        
        short[] addr=ae.getAddresses();
        int[] ts=ae.getTimestamps();
        
//        reset(); // reset to start of byte array
        
//        ByteArrayOutputStream bos=new ByteArrayOutputStream(nEvents*AESocketStream.EVENT_SIZE_BYTES+Integer.SIZE/8
        
        int count=0; // count of events sent in one DatagramPacket
        
        ByteArrayOutputStream bos=new ByteArrayOutputStream(packetSizeBytes);
        DataOutputStream dos=new DataOutputStream(bos);
        
        // write the sequence number for this DatagramPacket to the buf for this ByteArrayOutputStream
        dos.writeInt(packetSequenceNumber++);
        
        for(int i=0;i<nEvents;i++){
            // writes values in big endian (MSB first)
            // write n events, but if we exceed DatagramPacket buffer size, then make a DatagramPacket and send it, then reset this ByteArrayOutputStream
            dos.writeShort(addr[i]);
            dos.writeInt(ts[i]);
            if((++count)==AENetworkInterface.MAX_EVENTS){
                // we break up into datagram packets of sendBufferSize
                packet=new DatagramPacket(bos.toByteArray(), packetSizeBytes, group, AENetworkInterface.PORT);
                queue.offer(packet);
                count=0;
                bos=new ByteArrayOutputStream(packetSizeBytes);
                dos=new DataOutputStream(bos);
                dos.writeInt(packetSequenceNumber++); // write the new sequence number for the next DatagramPacket
            }
        }
        // send the remainder, if there are no events or exactly MAX_EVENTS this will get sent anyhow with sequence number only
        packet=new DatagramPacket(bos.toByteArray(), count*AENetworkInterface.EVENT_SIZE_BYTES+Integer.SIZE/8, group, AENetworkInterface.PORT);
        queue.offer(packet);
    }
    
    @Override public String toString(){
        return "AESocketOutputStream INETADDR="+AENetworkInterface.INETADDR+" at PORT="+AENetworkInterface.PORT;
    }
    
    
    public void close(){
        try{
            socket.leaveGroup(InetAddress.getByName(AENetworkInterface.INETADDR));
            socket.close();
            if(consumerThread!=null){
                consumerThread.interrupt();
            }
        }catch(IOException e){
        }
    }
    
    class Consumer implements Runnable{
        private final BlockingQueue<DatagramPacket> q;
        Consumer(BlockingQueue<DatagramPacket> q){
            this.q=q;
        }
        public void run(){
            try{
                while(true){
                    DatagramPacket p=q.take();
                    try{
                        socket.send(p);
                    }catch(IOException e){
                        e.printStackTrace();
                    }
                }
            }catch(InterruptedException e){
                log.info("Consumer interrupted");
            }
        }
    }
    
    
    
}
