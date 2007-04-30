/*
 * AEInputStream.java
 *
 * Created on December 26, 2005, 1:03 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.caviar.eventio;

import ch.unizh.ini.caviar.aemonitor.*;
import ch.unizh.ini.caviar.util.EngineeringFormat;
import java.beans.PropertyChangeSupport;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * Class to stream in packets of events from a stream socket network connection.
 *<p>
 *Stream format is very simple:
 *<pre>
 * int16 address
 *int32 timestamp
 *
 * int16 address
 *int32 timestamp
 *</pre>
 
 * @author tobi
 */
public class AESocket {
//    public final static long MAX_FILE_SIZE=200000000;
    private PropertyChangeSupport support=new PropertyChangeSupport(this);
    private static Logger log=Logger.getLogger("ch.unizh.ini.caviar.eventio");
    private static Preferences prefs=Preferences.userNodeForPackage(AESocket.class);
    private Socket socket;
    public final int MAX_NONMONOTONIC_TIME_EXCEPTIONS_TO_PRINT=10;
    private int numNonMonotonicTimeExceptionsPrinted=0;
    private String host=prefs.get("AESocket.host","localhost");
    public final int BUFFERED_STREAM_SIZE_BYTES=10000;
    
    //    private int numEvents,currentEventNumber;
    
    // mostRecentTimestamp is the last event sucessfully read
    // firstTimestamp, lastTimestamp are the first and last timestamps in the file (at least the memory mapped part of the file)
    private int mostRecentTimestamp, firstTimestamp, lastTimestamp;
    
    public static final int MAX_BUFFER_SIZE_EVENTS=300000;
    public static final int EVENT_SIZE=Short.SIZE/8+Integer.SIZE/8;
    
    // the packet used for reading events
    private AEPacketRaw packet=new AEPacketRaw(MAX_BUFFER_SIZE_EVENTS);
    
    EventRaw tmpEvent=new EventRaw();

//    private ByteBuffer eventByteBuffer=ByteBuffer.allocateDirect(EVENT_SIZE); // the ByteBuffer that a single event is written into from the fileChannel and read from to get the addr & timestamp
    private DataInputStream dis;
    private DataOutputStream dos;

    /** Creates a new instance of AESocket
     @param s the socket to use
     */
    public AESocket(Socket s) throws IOException {
        this.socket=s;
        dis=new DataInputStream(new BufferedInputStream(s.getInputStream(), BUFFERED_STREAM_SIZE_BYTES));
//        dis=new DataInputStream(s.getInputStream());
        dos=new DataOutputStream(new BufferedOutputStream(s.getOutputStream(), BUFFERED_STREAM_SIZE_BYTES));
    }
    
    /** Creates a new instance of AESocket
     @param host to connect to
     */
    public AESocket(String host) throws IOException {
        socket=new Socket();
//        socket.setPerformancePreferences(0,1,0); // low latency
//        socket.setTcpNoDelay(true); // disable aggregation of data into full packets
        socket.setReceiveBufferSize(60000);
        
        socket.connect(new InetSocketAddress(host,AENetworkInterface.PORT),300);
        
        setHost(host);
        dis=new DataInputStream(socket.getInputStream());
        dos=new DataOutputStream(socket.getOutputStream());
    }
    
    
    synchronized public AEPacketRaw readPacket() throws IOException{
        int n=dis.available()/EVENT_SIZE;
        packet.setNumEvents(0);
        for(int i=0;i<n;i++){
            packet.addEvent(readEventForwards());
        }
        return packet;
    }
    

    synchronized public void writePacket(AEPacketRaw p) throws IOException{
        short[] a=p.getAddresses();
        int[] ts=p.getTimestamps();
        int n=p.getNumEvents();
        for(int i=0;i<n;i++){
            dos.writeShort(a[i]);
            dos.writeInt(ts[i]);
        }
        dos.flush();
    }
    
    
    private EventRaw readEventForwards() throws IOException{
        int ts=0;
        short addr=0;
        try{
            addr=dis.readShort();
            ts=dis.readInt();
            // check for non-monotonic increasing timestamps, if we get one, reset our notion of the starting time
            if(isWrappedTime(ts,mostRecentTimestamp,1)){
//                throw new WrappedTimeException(ts,mostRecentTimestamp);
            }
            if(ts<mostRecentTimestamp){
                log.warning("AEInputStream.readEventForwards returned ts="+ts+" which goes backwards in time (mostRecentTimestamp="+mostRecentTimestamp+")");
//                throw new NonMonotonicTimeException(ts,mostRecentTimestamp);
            }
            tmpEvent.address=addr;
            tmpEvent.timestamp=ts;
            mostRecentTimestamp=ts;
            return tmpEvent;
        }catch(IOException e) {
            return null;
        }
    }
    
    
    
    /** @return returns the most recent timestamp
     */
    public int getMostRecentTimestamp() {
        return mostRecentTimestamp;
    }
    
    public void setMostRecentTimestamp(int mostRecentTimestamp) {
        this.mostRecentTimestamp = mostRecentTimestamp;
    }
    
    /** class used to signal a backwards read from input stream */
    public class NonMonotonicTimeException extends Exception{
        protected int timestamp, lastTimestamp;
        public NonMonotonicTimeException(){
            super();
        }
        public NonMonotonicTimeException(String s){
            super(s);
        }
        public NonMonotonicTimeException(int ts){
            this("NonMonotonicTime read from AE data file, read "+ts);
            this.timestamp=ts;
        }
        public NonMonotonicTimeException(int readTs, int lastTs){
            this("NonMonotonicTime read from AE data file, read="+readTs+" last="+lastTs);
            this.timestamp=readTs;
            this.lastTimestamp=lastTs;
        }
        public int getTimestamp(){
            return timestamp;
        }
        public int getLastTimestamp(){
            return lastTimestamp;
        }
        public String toString(){
            return "NonMonotonicTimeException: timestamp="+timestamp+" lastTimestamp="+lastTimestamp+" jumps backwards by "+(timestamp-lastTimestamp);
        }
    }
    
    public class WrappedTimeException extends NonMonotonicTimeException{
        
        public WrappedTimeException(int readTs, int lastTs){
            super("WrappedTimeException read from AE data file, read="+readTs+" last="+lastTs);
        }
    }
    
    
    final boolean isWrappedTime(int read, int prevRead, int dt){
        if(dt>0 && read<0 && prevRead>0) return true;
        if(dt<0 && read>0 && prevRead<0) return true;
        return false;
    }
    
    public void close() throws IOException{
        socket.close();
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
        prefs.put("AESocket.host",host);
    }
    
    /** @return the last host successfully connected to */
    public static String getLastHost(){
        return prefs.get("AESocket.host","localhost");
    }
    
    public String toString(){
        return "AESocket to host="+host;
    }
    
}
