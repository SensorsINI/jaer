package ch.unizh.ini.caviar.eventio;

import ch.unizh.ini.caviar.aemonitor.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.Exchanger;
import java.util.logging.*;
import java.util.prefs.*;

/** Receives input via multicast datagram packets from a server */
public class AEUnicastInput extends Thread {
    
    private static Preferences prefs=Preferences.userNodeForPackage(AEUnicastInput.class);
    
    private DatagramSocket socket = null;
    private InetAddress address = null;
    private ByteArrayInputStream bis=null;
    private DataInputStream dis=null;
    private boolean printedHost=false;
    private String host=prefs.get("AEUnicastInput.host","localhost");
    
    public static int EVENT_BUFFER_SIZE=512; // size of AEPackets that are served by readPacket
    
    private Exchanger<AEPacketRaw> exchanger=new Exchanger();
    
    private AEPacketRaw initialEmptyBuffer = new AEPacketRaw(EVENT_BUFFER_SIZE); // the buffer to start capturing into
    private AEPacketRaw initialFullBuffer = new AEPacketRaw(EVENT_BUFFER_SIZE);    // the buffer to render/process first
    private AEPacketRaw currentBuffer=initialFullBuffer; // starting buffer
    private long lastExchangeTime=System.currentTimeMillis();
    
    private static Logger log=Logger.getLogger("AESocketStream");
    
    public AEUnicastInput() throws IOException {
        socket = new DatagramSocket(AENetworkInterface.PORT);
        setName("AUnicastInput");
    }
    
    public void run(){
        currentBuffer=initialEmptyBuffer;
        try{
            while(currentBuffer!=null){
                if(!checkHost()) {
                    try{Thread.currentThread().sleep(100);} catch(Exception e){};
                    continue;
                }
                addToBuffer(currentBuffer);
                long t=System.currentTimeMillis();
                if(currentBuffer.getNumEvents()>=EVENT_BUFFER_SIZE || (t-lastExchangeTime>AENetworkInterface.MIN_INTERVAL_MS)){
                    lastExchangeTime=t;
                    currentBuffer=exchanger.exchange(currentBuffer); // get buffer to write to
                    currentBuffer.setNumEvents(0); // reset event counter
                }
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
    
    private AEPacketRaw packet=null;
    private byte[] buf=null;
    private DatagramPacket datagram;
    private int packetCounter=0;
    private int packetSequenceNumber=0;
    private EventRaw eventRaw=new EventRaw();
    
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
        return "AESocketInputStream host="+host+" at PORT="+AENetworkInterface.PORT;
    }
    
    synchronized public void close(){
        interrupt();
        socket.close();
    }
    
    public String getHost() {
        return host;
    }
    
    private boolean checkHost(){
        if(address!=null) return true;
        try{
            address = InetAddress.getByName(host);
            log.info("host "+host+" resolved to "+address);
            socket.connect(address,AENetworkInterface.PORT);
            return true;
        }catch(UnknownHostException e){
            e.printStackTrace();
            return false;
        }
    }
    
    /** You need to setHost before this will receive events
     @param host the hostname
     */
    public void setHost(String host) {
        this.host = host;
        prefs.put("AEUnicastInput.host",host);
    }
    
}
