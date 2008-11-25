/*
 * AEInputStream.java
 *
 * Created on December 26, 2005, 1:03 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package net.sf.jaer.eventio;
import net.sf.jaer.aemonitor.*;
import java.beans.PropertyChangeSupport;
import java.io.*;
import java.net.*;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * Streams in packets of events from a stream socket network connection over a reliable TCP connection.
 *<p>
 Stream format is very simple:
 <pre>
 int16 address0
 int32 timestamp0
 int16 address1
 int32 timestamp1
 etc for n AEs.
 </pre>
 The timestamp tick is us. The addresses are raw device addresses. See the AEChip classes for their
 EventExtractor2D inner class extractor definitions for individual device address formats.
 * @author tobi
 */
public class AESocket{
    Selector selector=null;
    SocketChannel channel=null;
    public static final int DEFAULT_RECEIVE_BUFFER_SIZE_BYTES=8192;
    public static final int DEFAULT_SEND_BUFFER_SIZE_BYTES=8192;
    public static final int DEFAULT_BUFFERED_STREAM_SIZE_BYTES=8192;
    /** timeout in ms for connection attempts */
    public static final int CONNECTION_TIMEOUT_MS=3000;
    /** timeout in ms for read/write attempts */
    public static final int SO_TIMEOUT=1; // 1 means we should timeout as soon as there are no more events in the datainputstream
    private int receiveBufferSize=prefs.getInt("AESocket.receiveBufferSize",DEFAULT_RECEIVE_BUFFER_SIZE_BYTES);
    private int sendBufferSize=prefs.getInt("AESocket.sendBufferSize",DEFAULT_SEND_BUFFER_SIZE_BYTES);
    private int bufferedStreamSize=prefs.getInt("AESocket.bufferedStreamSize",DEFAULT_BUFFERED_STREAM_SIZE_BYTES);
    private boolean useBufferedStreams=prefs.getBoolean("AESocket.useBufferedStreams",true);
    private boolean flushPackets=prefs.getBoolean("AESocket.flushPackets",true);

    /** Creates a new instance of AESocket  using an existing Socket.
     @param s the socket to use.
     */
    public AESocket(Socket s) throws IOException{
        this.socket=s;
    }

    /** Creates a new instance of AESocket for connection to the host:port.
     Before calling {@link #connect}, options can be set on the socket like the {@link #setReceiveBufferSize buffer size}.
     @param host to connect to. Can have format host:port. If port is omitted, it defaults to AENetworkInterface.PORT.
     @see #connect
     */
    public AESocket(String host,int port){
        this();
        //        if (hostname.contains(":")) {
//            int i = hostname.lastIndexOf(":");
//            portNumberString = hostname.substring(i + 1);
//            hostname = hostname.substring(0, i);
//            try {
//                portNumber = Integer.parseInt(portNumberString);
//            } catch (NumberFormatException e) {
//                log.warning(e.toString());
//            }
//        }
        setHost(host);
        setPort(port);
    }
    public AESocket(){
        socket=new Socket();
    }
    public void setReceiveBufferSize(int sizeBytes){
        //        if (sizeBytes < 256) {
//            sizeBytes = 256;
//        } else if (sizeBytes > 0xffff) {
//            sizeBytes = 0xffff;
//        }
        this.receiveBufferSize=sizeBytes;
        prefs.putInt("AESocket.receiveBufferSize",sizeBytes);
    }
    public void setSendBufferSize(int sizeBytes){
        //        if (sizeBytes < 256) {
//            sizeBytes = 256;
//        } else if (sizeBytes > 0xffff) {
//            sizeBytes = 0xffff;
//        }
        this.sendBufferSize=sizeBytes;
        prefs.putInt("AESocket.sendBufferSize",sizeBytes);
    }
    public void setBufferedStreamSize(int sizeBytes){
        //        if (sizeBytes < 256) {
//            sizeBytes = 256;
//        } else if (sizeBytes > 0xffff) {
//            sizeBytes = 0xffff;
//        }
        this.bufferedStreamSize=sizeBytes;
        prefs.putInt("AESocket.bufferedStreamSize",sizeBytes);
    }
    public int getReceiveBufferSize(){
        return receiveBufferSize;
    }
    public int getSendBufferSize(){
        return sendBufferSize;
    }
    public int getBufferedStreamSize(){
        return bufferedStreamSize;
    }
    private PropertyChangeSupport support=new PropertyChangeSupport(this);
    private static Logger log=Logger.getLogger("net.sf.jaer.eventio");
    private static Preferences prefs=Preferences.userNodeForPackage(AESocket.class);
    private Socket socket;
    public final int MAX_NONMONOTONIC_TIME_EXCEPTIONS_TO_PRINT=10;
    private int numNonMonotonicTimeExceptionsPrinted=0;
    private String hostname=prefs.get("AESocket.hostname","localhost");
    private int portNumber=prefs.getInt("AESocket.port",AENetworkInterfaceConstants.STREAM_PORT);
    // mostRecentTimestamp is the last event sucessfully read
    // firstTimestamp, lastTimestamp are the first and last timestamps in the file (at least the memory mapped part of the file)

    private int mostRecentTimestamp;
    //    private int firstTimestamp;
//    private int lastTimestamp;

    public static final int MAX_PACKET_SIZE_EVENTS=100000;
    // the packet used for reading events

    private AEPacketRaw packet=new AEPacketRaw(MAX_PACKET_SIZE_EVENTS);
    EventRaw tmpEvent=new EventRaw();
    private DataInputStream dis;
    private DataOutputStream dos;
    
    /** returns events from reading thread. An IOException closes the socket. A timeout just return the whatever events have
     * been received. An EOF exception returns events that have been recieved.
     @return the read packet
     */
    public synchronized AEPacketRaw readPacket() throws IOException{
        checkDataInputStream();

        //        int n = dis.available() / AENetworkInterface.EVENT_SIZE_BYTES;
        packet.setNumEvents(0);
        try{
            while(true){
                packet.addEvent(readEventForwards());
            }
        }catch(EOFException e){
            return packet;
        }catch(SocketTimeoutException eto){
            // ok, this packet done
            return packet;
        }/*catch(IOException e2){ // removed since other errors should be handled by the user
            log.warning(e2.toString()+" closing socket");
            close();
            return packet;
        }*/
    }
    public synchronized void writePacket(AEPacketRaw p) throws IOException{
        checkDataOutputStream();
        int[] a=p.getAddresses();
        int[] ts=p.getTimestamps();
        int n=p.getNumEvents();
        for(int i=0;i<n;i++){
            dos.writeInt(a[i]);
            dos.writeInt(ts[i]);
        }
        if(flushPackets){
            dos.flush();
        }
    }

    //    byte[] tmpBuffer=new byte[0];

    private void checkDataInputStream() throws IOException{
        if(dis==null){
            if(useBufferedStreams){
                dis=new java.io.DataInputStream(new BufferedInputStream(socket.getInputStream(),bufferedStreamSize));
            }else{
                dis=new java.io.DataInputStream((socket.getInputStream()));
            }
        }
    //        socket.getInputStream().read(tmpBuffer, 0, 0); // should throw exception if remote side has closed socket
    }
    private void checkDataOutputStream() throws IOException{
        if(dos==null){
            if(useBufferedStreams){
                dos=new java.io.DataOutputStream(new BufferedOutputStream(socket.getOutputStream(),bufferedStreamSize));
            }else{
                dos=new java.io.DataOutputStream((socket.getOutputStream()));
            }
        }
    }

    /** reads next event, but if there is a timeout, socket is closed */
    private EventRaw readEventForwards() throws IOException{
        int ts=0;
        int addr=0;
        addr=dis.readInt();
        ts=dis.readInt();
        // check for non-monotonic increasing timestamps, if we get one, reset our notion of the starting time
        if(isWrappedTime(ts,mostRecentTimestamp,1)){
        //                throw new WrappedTimeException(ts,mostRecentTimestamp);
        }
        if(ts<mostRecentTimestamp){
//            log.warning("AEInputStream.readEventForwards returned ts="+ts+" which goes backwards in time (mostRecentTimestamp="+mostRecentTimestamp+")");
        //                throw new NonMonotonicTimeException(ts,mostRecentTimestamp);
        }
        tmpEvent.address=addr;
        tmpEvent.timestamp=ts;
        mostRecentTimestamp=ts;
        return tmpEvent;
    }

    /** @return returns the most recent timestamp
     */
    public int getMostRecentTimestamp(){
        return mostRecentTimestamp;
    }
    public void setMostRecentTimestamp(int mostRecentTimestamp){
        this.mostRecentTimestamp=mostRecentTimestamp;
    }

    /** class used to signal a backwards read from input stream */
    public class NonMonotonicTimeException extends Exception{
        protected int timestamp;
        protected int lastTimestamp;
        public NonMonotonicTimeException(){
        }
        public NonMonotonicTimeException(String s){
            super(s);
        }
        public NonMonotonicTimeException(int ts){
            this("NonMonotonicTime read from AE data file, read "+ts);
            this.timestamp=ts;
        }
        public NonMonotonicTimeException(int readTs,int lastTs){
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
        public WrappedTimeException(int readTs,int lastTs){
            super("WrappedTimeException read from AE data file, read="+readTs+" last="+lastTs);
        }
    }
    final boolean isWrappedTime(int read,int prevRead,int dt){
        if(dt>0&&read<0&&prevRead>0){
            return true;
        }
        if(dt<0&&read>0&&prevRead<0){
            return true;
        }
        return false;
    }

    /** Closes the AESocket and nulls the data input and output streams */
    public synchronized void close() throws IOException{
        socket.close();
        dis=null;
        dos=null;
    }
    public String getHost(){
        return hostname;
    }

    /** Sets the preferred host:port string. Set on sucessful completion of input stream connection. */
    public void setHost(String host){
        this.hostname=host;
        prefs.put("AESocket.hostname",host);
    }
    public int getPort(){
        return portNumber;
    }
    public void setPort(int port){
        portNumber=port;
        prefs.putInt("AESocket.port",port);
    }

    /** @return the last host successfully connected to */
    public static String getLastHost(){
        return prefs.get("AESocket.hostname","localhost");
    }
    @Override
    public String toString(){
        return "AESocket to host="+hostname+":"+portNumber;
    }

    /** returns the underlying Socket */
    public Socket getSocket(){
        return socket;
    }

    /** Connects to the socket, using the specified host and port specified in the constructor
     @throws IOException if underlying socket cannot connect
     */
    public void connect() throws IOException{
        // socket has already been created either elsewhere or in the default constuctor
        // we now also make a selector for it to enable checking if it is really still working
        //        socket.setPerformancePreferences(0,1,0); // low latency
//        socket.setTcpNoDelay(true); // disable aggregation of data into full packets
        socket.setReceiveBufferSize(receiveBufferSize);
        socket.setSendBufferSize(sendBufferSize);
        socket.setSoTimeout(SO_TIMEOUT);
        socket.connect(new InetSocketAddress(hostname,portNumber),CONNECTION_TIMEOUT_MS);
        if(socket.getReceiveBufferSize()!=getReceiveBufferSize()){
            log.warning("requested sendBufferSize="+getSendBufferSize()+" but got sendBufferSize="+socket.getSendBufferSize());
        }
        if(socket.getSendBufferSize()!=getSendBufferSize()){
            log.warning("requested receiveBufferSize="+getReceiveBufferSize()+" but got receiveBufferSize="+socket.getReceiveBufferSize());
        }
        //        selector=Selector.open();
//        channel=socket.getChannel();
//        SelectionKey register = channel.register(selector, channel.validOps());
        Runtime.getRuntime().addShutdownHook(new Thread(){
                    public void run(){
                        log.info("closing "+socket);
                        try{
                            socket.close();
                        }catch(Exception e){
                            log.warning(e.toString());
                        }
                    }
                });
    }
    public boolean isUseBufferedStreams(){
        return useBufferedStreams;
    }
    public void setUseBufferedStreams(boolean useBufferedStreams){
        this.useBufferedStreams=useBufferedStreams;
        prefs.putBoolean("AESocket.useBufferedStreams",useBufferedStreams);
    }
    public boolean isFlushPackets(){
        return flushPackets;
    }
    public void setFlushPackets(boolean flushPackets){
        this.flushPackets=flushPackets;
        prefs.putBoolean("AESocket.flushPackets",flushPackets);
    }

    /** Is this socket connected.
     @return false if underlying socket is null or is not connected
     */
    public boolean isConnected(){
        if(socket==null){
            return false;
        }
        return socket.isConnected();
    }
}
