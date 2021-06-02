/*
 * AEInputStream.java
 *
 * Created on December 26, 2005, 1:03 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package net.sf.jaer.eventio;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.aemonitor.EventRaw;

/**
 * Streams in or out packets of events from or to a stream socket
 * network connection over a reliable TCP connection.
 *<p>
 Stream format is very simple:
 <pre>
 int32 timestamp0
 int32 address0
 int32 timestamp1
 int32 address1
 etc for n AEs.
 </pre>
 The timestamp tick is us. The addresses are raw device addresses. See the AEChip classes for their
 EventExtractor2D inner class extractor definitions for individual device address formats.
 * @author tobi
 */
public class AESocket implements AESocketSettings{
    Selector selector=null;
    SocketChannel channel=null;
    private static int t0_ref = -1;


    private int receiveBufferSize=prefs.getInt("AESocket.receiveBufferSize",DEFAULT_RECEIVE_BUFFER_SIZE_BYTES);
    private int sendBufferSize=prefs.getInt("AESocket.sendBufferSize",DEFAULT_SEND_BUFFER_SIZE_BYTES);
    private int bufferedStreamSize=prefs.getInt("AESocket.bufferedStreamSize",DEFAULT_BUFFERED_STREAM_SIZE_BYTES);
    private boolean useBufferedStreams=prefs.getBoolean("AESocket.useBufferedStreams",true);
    private boolean flushPackets=prefs.getBoolean("AESocket.flushPackets",true);
    static Preferences prefs = Preferences.userNodeForPackage(AESocket.class);
    private boolean sequenceNumberEnabled = prefs.getBoolean("AESocket.sequenceNumberEnabled",true);
    private boolean addressFirstEnabled = prefs.getBoolean("AESocket.addressFirstEnabled",true);
    private float timestampMultiplier = prefs.getFloat("AESocket.timestampMultiplier", DEFAULT_TIMESTAMP_MULTIPLIER);
    private float timestampMultiplierReciprocal = 1f / timestampMultiplier;
    private boolean swapBytesEnabled = prefs.getBoolean("AESocket.swapBytesEnabled",false);
    private boolean use4ByteAddrTs = prefs.getBoolean("AESocket.use4ByteAddrTs", DEFAULT_USE_4_BYTE_ADDR_AND_TIMESTAMP);   
    private boolean timestampsEnabled = prefs.getBoolean("AESocket.timestampsEnabled",true);
    private boolean localTimestampsEnabled=prefs.getBoolean("AESocket.localTimestampsEnabled", false);
    public static boolean isiEnabled = prefs.getBoolean("AESocket.isiEnabled", DEFAULT_USE_ISI_ENABLED);
//    private PropertyChangeSupport support=new PropertyChangeSupport(this);
    private static Logger log=Logger.getLogger("net.sf.jaer.eventio");
    private Socket socket;
    
    private String hostname=prefs.get("AESocket.hostname",DEFAULT_HOST);
    private int portNumber=prefs.getInt("AESocket.port",DEFAULT_PORT);
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
    @Override
    public int getReceiveBufferSize(){
        return receiveBufferSize;
    }
    @Override
    public int getSendBufferSize(){
        return sendBufferSize;
    }
    public int getBufferedStreamSize(){
        return bufferedStreamSize;
    }
    
    /** returns events from AESocket. An IOException closes the socket. A timeout just return the whatever events have
     * been received. An EOF exception returns events that have been recieved.
     @return the read packet
     */
    int nTmp = 0;
    int nEventCapacity =0;
    int nTmpCount = 0;
    public synchronized AEPacketRaw readPacket() throws IOException{
        checkDataInputStream();

        //        int n = dis.available() / AENetworkInterface.EVENT_SIZE_BYTES;
        packet.setNumEvents(0);

        try{
            if(((nTmpCount != 0) && (nTmpCount < nEventCapacity)) || ((nTmpCount == 0) && (nEventCapacity != 0))){
                while(nTmpCount < nEventCapacity){
                    packet.addEvent(readEventForwards());
                    nTmpCount += 1;
                }
            }
            else{
                nTmp = swapByteOrder(dis.readInt());
                if(nTmp != 0x10001)
                {
                    log.warning("!!!!!!!!!!The first byte of the packet is not 0x10001, is" + nTmp);
                }
                nTmp = swapByteOrder(dis.readInt());     //eventsize
                nTmp = swapByteOrder(dis.readInt());     //eventoffset
                nTmp = swapByteOrder(dis.readInt());     //eventoverflow
                nTmpCount = 0;
                nEventCapacity = swapByteOrder(dis.readInt());     //eventcapacity
                nTmp = swapByteOrder(dis.readInt());     //eventnumber
                nTmp = swapByteOrder(dis.readInt());     //eventvalid
            }
            return packet;
        }catch(EOFException e){
            return packet;
        }/*catch(SocketTimeoutException eto){
            // ok, this packet done
            return packet;
        }catch(IOException e2){ // removed since other errors should be handled by the user
            log.warning(e2.toString()+" closing socket");
            close();
            return packet;
        }*/
    }
    /** Writes the packet to the stream. Returns doing nothing if packet is null or empty.
     *
     * @param p the packet
     * @throws IOException
     */
    public synchronized void writePacket(AEPacketRaw packet) throws IOException{
        if(packet==null) return;

        checkDataOutputStream();
        int n=packet.getNumEvents();
        if(n==0) return;

        int[] a=packet.getAddresses();
        int[] ts=packet.getTimestamps();
        for(int i=0;i<n;i++){
            if (this.isSwapBytesEnabled()){
            dos.writeInt(swapByteOrder(normalize(ts[i])));
            dos.writeInt(swapByteOrder(a[i]));
            } else {
            dos.writeInt(normalize(ts[i]));
            dos.writeInt(a[i]);
            }
            
            
        }
        if(flushPackets){
            dos.flush();
        }
    }

    private int normalize(int t){
        int tt = 0;
        if (t0_ref == -1) {
            t0_ref = t;
            tt = 0;
            log.info("starting time at "+t0_ref);
        } else {
            tt = t-t0_ref;
        }

        if(this.isISIEnabled()){
            t0_ref=t;
            if(tt>100000){
            log.info("warning: very large isi "+tt);
            }
        }
        return tt;

    }
    private static int swapByteOrder(int value) {
        int b1 = (value >> 0) & 0xff;
        int b2 = (value >> 8) & 0xff;
        int b3 = (value >> 16) & 0xff;
        int b4 = (value >> 24) & 0xff;

        return b1 << 24 | b2 << 16 | b3 << 8 | b4 << 0;
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

    /** reads next event, but if there is a timeout, socket is closed
     @return the raw event
     */
    private EventRaw readEventForwards() throws IOException{
        int ts=0;
        int addr=0;
        byte pTmp[] = new byte[2000];
        if(isSwapBytesEnabled()){
            ts=this.swapByteOrder(normalize(dis.readInt()));
            addr=this.swapByteOrder(dis.readInt());
        } else {
            // dis.read(pTmp);
            addr=this.swapByteOrder(dis.readInt());
            ts=this.swapByteOrder((dis.readInt()));
            // ts=normalize(dis.readInt());
            // addr=dis.readInt();
        }
        // check for non-monotonic increasing timestamps, if we get one, reset our notion of the starting time
        if(isWrappedTime(ts,mostRecentTimestamp,1)){
        //                throw new WrappedTimeException(ts,mostRecentTimestamp);
        }
        if(ts<mostRecentTimestamp){
//            log.warning("AEInputStream.readEventForwards returned ts="+ts+" which goes backwards in time (mostRecentTimestamp="+mostRecentTimestamp+")");
        //                throw new NonMonotonicTimeException(ts,mostRecentTimestamp);
        }
        // tmpEvent.address=addr;
        // tmpEvent.timestamp=ts;
        //                        x_addr                          y_addr          on_off event
        tmpEvent.address = ((addr & 0xfe0000) >> 16) + ((addr  & 0x1fc) << 6) + ((addr & 2) >> 1);     //just for DVS128 data format convertion
        // tmpEvent.address = ((addr & 0x7fe0000) >> 5) + ((addr  & 0x7fc) << 20) + ((addr & 2) >> 1);     //just for DAVIS data format convertion
        tmpEvent.timestamp=ts;
        mostRecentTimestamp=ts;
        // System.out.printf("timestamp = %d,%x\n",ts,addr);
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

    @Override
    public boolean isSequenceNumberEnabled() {
        return this.sequenceNumberEnabled;
    }

    @Override
    public void setSequenceNumberEnabled(boolean sequenceNumberEnabled) {
        this.sequenceNumberEnabled=sequenceNumberEnabled;
    }

    @Override
    public void setSwapBytesEnabled(boolean yes) {
        this.swapBytesEnabled=yes;
    }

    @Override
    public boolean isSwapBytesEnabled() {
        return this.swapBytesEnabled;
    }

    @Override
    public float getTimestampMultiplier() {
        return this.timestampMultiplier;
    }

    @Override
    public void setTimestampMultiplier(float timestampMultiplier) {
        this.timestampMultiplier=timestampMultiplier;
    }

    @Override
    public void set4ByteAddrTimestampEnabled(boolean yes) {
        this.use4ByteAddrTs=yes;
    }

    @Override
    public boolean is4ByteAddrTimestampEnabled() {
        return this.use4ByteAddrTs;
    }

    @Override
    public boolean isTimestampsEnabled() {
        return this.timestampsEnabled;
    }

    @Override
    public void setTimestampsEnabled(boolean yes) {
        this.timestampsEnabled=yes;
    }

    @Override
    public void setLocalTimestampEnabled(boolean yes) {
        this.localTimestampsEnabled=yes;
    }

    @Override
    public boolean isLocalTimestampEnabled() {
        return this.localTimestampsEnabled;
    }

    @Override
    public void setISIEnabled(boolean yes) {
        this.isiEnabled=yes;
    }

    @Override
    public boolean isISIEnabled() {
        return this.isiEnabled;
    }

    @Override
    public void setAddressFirstEnabled(boolean yes) {
        this.addressFirstEnabled=yes;
    }

    @Override
    public boolean isAddressFirstEnabled() {
        return this.addressFirstEnabled;
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
        t0_ref=-1;
    }
    public String getHost(){
        return hostname;
    }

    /** Sets the preferred host:port string. Set on successful completion of input stream connection. */
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

    /** Returns the underlying Socket 
     @return the socket
     */
    public Socket getSocket(){
        return socket;
    }

    /** Connects to the socket, using the specified host and port specified in the constructor
     @throws IOException if underlying socket cannot connect
     */
    @Override
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

   /** Says whether stream is buffered locally 
     * 
     * @return  true if using buffered streams
     */    
    @Override
    public boolean isUseBufferedStreams(){
        return useBufferedStreams;
    }
    
    /** Sets whether stream is buffered locally 
     * 
     * @param useBufferedStreams true to use buffered streams
     */
    @Override
    synchronized public void setUseBufferedStreams(boolean useBufferedStreams){
        if(useBufferedStreams!=this.useBufferedStreams && (dis!=null || dos!=null)){
            dis=null; // so that buffering is enabled or disabled next time read or write happens
            dos=null;
            log.info("nulled data input and data output streams to change to useBufferedStreams="+useBufferedStreams);
        }
        this.useBufferedStreams=useBufferedStreams;
        prefs.putBoolean("AESocket.useBufferedStreams",useBufferedStreams);
    }
    
    /** Says whether output stream is flushed on each write
     * 
     * @return true if stream is being flushed
     */
    public boolean isFlushPackets(){
        return flushPackets;
    }
    
    /** Sets whether data output stream is flushed after each write
     * 
     * @param flushPackets true to flush stream on each write
     */
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

