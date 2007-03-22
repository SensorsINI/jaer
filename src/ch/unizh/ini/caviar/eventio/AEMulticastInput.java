package ch.unizh.ini.caviar.eventio;

import ch.unizh.ini.caviar.aemonitor.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.Exchanger;
import java.util.logging.*;

/** Receives input via multicast datagram packets from a server */
public class AEMulticastInput extends Thread {
    MulticastSocket socket = null;
    InetAddress address = null;
    ByteArrayInputStream bis=null;
    DataInputStream dis=null;
    boolean printedHost=false;
    volatile boolean stopMe=false;
    static int EVENT_BUFFER_SIZE=512;
    
    Exchanger<AEPacketRaw> exchanger=new Exchanger();
    
    AEPacketRaw initialEmptyBuffer = new AEPacketRaw(EVENT_BUFFER_SIZE); // the buffer to start capturing into
    AEPacketRaw initialFullBuffer = new AEPacketRaw(EVENT_BUFFER_SIZE);    // the buffer to render/process first
    AEPacketRaw currentBuffer=initialFullBuffer; // starting buffer
    
    static Logger log=Logger.getLogger("AESocketStream");
    
    public AEMulticastInput() throws IOException{
        socket = new MulticastSocket(AENetworkInterface.PORT);
        address = InetAddress.getByName(AENetworkInterface.INETADDR);
        socket.joinGroup(address);
        setName("AEMulticastInput");
    }
    
    public void run(){
        currentBuffer=initialEmptyBuffer;
        try{
            while(currentBuffer!=null){
                addToBuffer(currentBuffer);
                if(currentBuffer.getNumEvents()>=EVENT_BUFFER_SIZE){
                    currentBuffer=exchanger.exchange(currentBuffer); // get buffer to write to
                    currentBuffer.setNumEvents(0); // reset event counter
                }
            }
            try {
                socket.leaveGroup(InetAddress.getByName(AENetworkInterface.INETADDR));
            } catch (UnknownHostException ex) {
                ex.printStackTrace();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            socket.close();
        }catch(InterruptedException e){
            log.info("interrupted");
        }
    }
    
    public AEPacketRaw readPacket(){
        try{
            currentBuffer=exchanger.exchange(currentBuffer);
            return currentBuffer;
        }catch(InterruptedException e){
            return null;
        }
    }
    
    AEPacketRaw packet=null;
    byte[] buf=null;
    DatagramPacket datagram;
    int packetCounter=0;
    int packetSequenceNumber=0;
    EventRaw eventRaw=new EventRaw();
    
    /** adds to the buffer from received packets */
    private void addToBuffer(AEPacketRaw packet){
        if(buf==null){
            buf=new byte[AENetworkInterface.SOCKET_BUFFER_SIZE_BYTES];
        }
        if(datagram==null){
            datagram=new DatagramPacket(buf,buf.length);
        }
        if(bis==null){
            bis=new ByteArrayInputStream(buf);
            dis=new DataInputStream(bis);
        }
        try{
            socket.receive(datagram);
            if(!printedHost){
                printedHost=true;
                SocketAddress addr=datagram.getSocketAddress();
                log.info("received a packet from "+addr);
                socket.connect(addr);
            }
            if(datagram.getLength()<Integer.SIZE/8){
                log.warning(String.format("DatagramPacket only has %d bytes, and thus doesn't even have sequence number, returning empty packet",datagram.getLength()));
                packet.setNumEvents(0);
            }
            packetCounter++;
            int nEventsInPacket=(datagram.getLength()-Integer.SIZE/8)/AENetworkInterface.EVENT_SIZE_BYTES;
            dis.reset();
            packetSequenceNumber=dis.readInt();
            if(packetSequenceNumber!=packetCounter){
                log.warning(
                        String.format("Dropped %d packets. (Incoming packet sequence number (%d) doesn't match expected packetCounter (%d), resetting packetCounter to match present incoming sequence number)",
                        (packetSequenceNumber-packetCounter),
                        packetSequenceNumber,
                        packetCounter
                        ));
                packetCounter=packetSequenceNumber;
            }
//            short[] addr=packet.getAddresses();
//            int[] ts=packet.getTimestamps();
            for(int i=0;i<nEventsInPacket;i++){
                eventRaw.address=dis.readShort();
                eventRaw.timestamp=dis.readInt();
                packet.addEvent(eventRaw);
//                addr[i]=dis.readShort();
//                ts[i]=dis.readInt();
            }
//            packet.setNumEvents(nEventsInPacket);
        }catch(IOException e){
            e.printStackTrace();
            log.warning(e.getMessage());
            packet.setNumEvents(0);
            close();
        }
//        return packet;
    }
    
    @Override public String toString(){
        return "AESocketInputStream INETADDR="+AENetworkInterface.INETADDR+" at PORT="+AENetworkInterface.PORT;
    }
    
    synchronized public void close(){
            interrupt();
    }
}
