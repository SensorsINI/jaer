package net.sf.jaer.eventio;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Exchanger;
import java.util.logging.Logger;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.aemonitor.EventRaw;

/** 
 * Receives input via multicast datagram packets from a server. This input is a Thread that reads the MulticastSocket in the background and
 * exchanges data with a consumer using a double buffer. It must be started like any thread.
 *Closing the AEMulticastInput interrupts the thread and joins it.
 
 @author tobi
 
 */
public class AEMulticastInput extends Thread {
    MulticastSocket socket = null;
    InetAddress address = null;
    ByteArrayInputStream bis=null;
    DataInputStream dis=null;
    boolean printedHost=false;
    volatile boolean stopMe=false;
    static int EVENT_BUFFER_SIZE=2048;
    
    Exchanger<AEPacketRaw> exchanger=new Exchanger();
    
    AEPacketRaw initialEmptyBuffer = new AEPacketRaw(EVENT_BUFFER_SIZE); // the buffer to start capturing into
    AEPacketRaw initialFullBuffer = new AEPacketRaw(EVENT_BUFFER_SIZE);    // the buffer to render/process first
    AEPacketRaw fillingBuffer=initialEmptyBuffer, emptyingBuffer=initialFullBuffer; // starting buffer
    
    static Logger log=Logger.getLogger("AESocketStream");
    
    /** Constructs a new AEMulticastInput thread. This Thread must be started before it will
     *collect events from a source.
     *@throws IOException if there is a permission problem
     **/
    public AEMulticastInput() throws IOException{
        socket = new MulticastSocket(AENetworkInterfaceConstants.STREAM_PORT);
        address = InetAddress.getByName(AENetworkInterfaceConstants.MULTICAST_INETADDR);
        socket.joinGroup(address);
        setName("AEMulticastInput");
    }
    
    /** This method reads datagram packets and adds events to the current buffer. Calling readPacket
     *returns exchanges the buffers and returns the read events.
     **/
    public void run(){
        fillingBuffer=initialEmptyBuffer;
        try{
            while(fillingBuffer!=null){
                addToBuffer(fillingBuffer);
                if(fillingBuffer.getNumEvents()>=EVENT_BUFFER_SIZE){
                    fillingBuffer=exchanger.exchange(fillingBuffer); // get buffer to write to, pass current fillingBuffer to consumer
                    fillingBuffer.setNumEvents(0); // reset event counter
                }
            }
            try {
                socket.leaveGroup(InetAddress.getByName(AENetworkInterfaceConstants.MULTICAST_INETADDR));
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
    
    /** returns the present data received from multicast source
     *@return a packet with the latest data
     **/
    public AEPacketRaw readPacket(){
        try{
            emptyingBuffer=exchanger.exchange(emptyingBuffer); // get fillingBuffer, pass producer the emptyingBuffer
            return emptyingBuffer;
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
            buf=new byte[AENetworkInterfaceConstants.DATAGRAM_BUFFER_SIZE_BYTES];
        }
        if(datagram==null){
            datagram=new DatagramPacket(buf,buf.length);
        }
        if(bis==null){
            bis=new ByteArrayInputStream(buf);
            dis=new DataInputStream(bis);
        }
        try{
            socket.receive(datagram); // blocks until datagram received
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
            int nEventsInPacket=(datagram.getLength()-Integer.SIZE/8)/AENetworkInterfaceConstants.EVENT_SIZE_BYTES;
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
                eventRaw.address=dis.readInt();
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
        return "AESocketInputStream INETADDR="+AENetworkInterfaceConstants.MULTICAST_INETADDR+" at PORT="+AENetworkInterfaceConstants.STREAM_PORT;
    }
    
    /** Interrupts the producer thread, which ends the loop and closes the Multicast socket */
    synchronized public void close(){
            interrupt();
    }
}
