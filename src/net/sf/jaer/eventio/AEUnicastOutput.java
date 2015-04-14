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
package net.sf.jaer.eventio;
import java.io.IOException;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.Exchanger;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.util.ByteSwapper;
/**
 * Streams AE packets to network using UDP DatagramPacket's that are unicast.
 * AEViewers or external clients can receive these packets to render them.
 * 
<p>
The implementation using a BlockingQueue to buffer the AEPacketRaw's that are offered.
 * The packets are sent by a separate Consumer thread. The consumer has a queue length that determines how many packets
 * can be buffered before the writePacket method blocks.
 * <p>
 * The datagram socket is not 'connect'ed to the receiver.
 *
 * @author tobi
 */
public class AEUnicastOutput implements AEUnicastSettings{
    static Preferences prefs = Preferences.userNodeForPackage(AEUnicastOutput.class);
    private int sendBufferSize = prefs.getInt("AEUnicastOutput.bufferSize",1500);
    static Logger log = Logger.getLogger("AEUnicastOutput");
    protected DatagramChannel channel = null;
    protected DatagramSocket socket = null;
    int packetSequenceNumber = 0;
    InetSocketAddress client = null;
    Thread consumerThread;
    private String host = prefs.get("AEUnicastOutput.host","localhost");
    private int port = prefs.getInt("AEUnicastOutput.port",AENetworkInterfaceConstants.DATAGRAM_PORT);
    private boolean sequenceNumberEnabled = prefs.getBoolean("AEUnicastOutput.sequenceNumberEnabled",true);
    private boolean addressFirstEnabled = prefs.getBoolean("AEUnicastOutput.addressFirstEnabled",true);
    private float timestampMultiplier = prefs.getFloat("AEUnicastOutput.timestampMultiplier",DEFAULT_TIMESTAMP_MULTIPLIER);
    private float timestampMultiplierReciprocal = 1f / timestampMultiplier;
    private boolean swapBytesEnabled = prefs.getBoolean("AEUnicastOutput.swapBytesEnabled",false);
    private boolean use4ByteAddrTs = prefs.getBoolean("AEUnicastOutput.use4ByteAddrTs",DEFAULT_USE_4_BYTE_ADDR_AND_TIMESTAMP);
    private int bufferSize = prefs.getInt("AEUnicastOutput.bufferSize",AENetworkInterfaceConstants.DATAGRAM_BUFFER_SIZE_BYTES);
    private Exchanger<ByteBuffer> exchanger = new Exchanger();
    private ByteBuffer initialEmptyBuffer = ByteBuffer.allocateDirect(getBufferSize()); // the buffer to start capturing into
    private ByteBuffer initialFullBuffer = ByteBuffer.allocateDirect(getBufferSize());// the buffer to render/process first
    private ByteBuffer currentBuf = initialEmptyBuffer; // starting buffer for filling
    private boolean timestampsEnabled = prefs.getBoolean("AEUnicastOutput.timestampsEnabled",true);
    private boolean localTimestampsEnabled=prefs.getBoolean("AEUnicastOutput.localTimestampsEnabled", false);

//    /** Creates a new instance, binding any available local port (since we will be just sending from here)
//     * and using the last host and port.
//     * @param host the hostname to send to
//     * @param port the port to send to
//     * @see #setHost
//     * @see #setPort
//     */
//    public AEUnicastOutput (String host,int port){
//        this();
//        setHost(host);
//        setPort(port);
//        client = new InetSocketAddress(host,port);
//    }
    /** Creates a new instance, binding any available local port (since we will be just sending from here).
     * The port and host need to be sent before any packets will be sent.
     * @see #setHost
     * @see #setPort
     */
    public AEUnicastOutput (){
    }

    /** Opens or reopens the AEUnicast channel. If the channel is not open, open it. If it is open, then close and reopen it.
     *
     * @throws IOException
     */
    public void open () throws IOException{
        close();
        channel = DatagramChannel.open();
        socket = channel.socket(); // bind to any available port because we will be sending datagrams with included host:port info
        socket.setTrafficClass(0x10 + 0x08); // low delay
        setSocketBufferSize();
        allocateBuffers();
        consumerThread = new Thread(new Consumer(exchanger,initialFullBuffer));
        consumerThread.setName("AEUnicastOutput");
        consumerThread.setPriority(Thread.NORM_PRIORITY + -1);
        consumerThread.start();
        log.info("opened AEUnicastOutput on local port="+socket.getLocalPort()+" with bufferSize="+getBufferSize());
    }

    /**
     * Writes the packet out as sequence of address/timestamp's, just as they came as input from the device.
     * <p>
     * The notion of a packet is discarded
     *to simplify later reading an input stream from the output stream result.
     * The AEPacketRaw is written in chunks of AESocketStream.SOCKET_BUFFER_SIZE bytes (which
    must be a multiple of AESocketStream.EVENT_SIZE_BYTES).
     * Each DatagramPacket has a sequence number as the first Integer value which is used on the reciever to
    detect dropped packets.
    <p>
    If a null or empty packet is supplied as ae then this method returns doing nothing and no sequence number is sent.
     * <p>
     * This method actually offers new Datagram packets to the consumer thread for later tranmission. The datagram address and port are taken from
     * the current settings for the AEUnicastOutput.
     * 
     *@param ae a raw address-event packet
     */
    synchronized public void writePacket (AEPacketRaw ae) throws IOException{

        if(ae==null) return;
        int nEvents = ae.getNumEvents();
        if ( nEvents == 0 ){
            return;
        }

        int[] addr = ae.getAddresses();
        int[] ts = ae.getTimestamps();

        try{
            // write the sequence number for this DatagramPacket to the buf for this ByteArrayOutputStream
            maybeWriteSequenceNumber(currentBuf);

            for ( int i = 0 ; i < nEvents ; i++ ){
                // writes values in big endian (MSB first)
                // write n events, but if we exceed DatagramPacket buffer size, then make a DatagramPacket and send it, then reset this ByteArrayOutputStream
                int t=0;
                if(timestampsEnabled){
                    if(!localTimestampsEnabled){
                        t=ts[i];
                    }else{
                        t=(int)System.nanoTime()/1000;
                    }
                }
                if ( addressFirstEnabled ){
                    if ( use4ByteAddrTs ){
                        currentBuf.putInt(swab(addr[i]));
                        if ( timestampsEnabled ){
                            currentBuf.putInt(swab((int)( timestampMultiplierReciprocal * t )));
                        }
                    } else{
                        currentBuf.putShort((short)swab(addr[i]));
                        if ( timestampsEnabled ){
                            currentBuf.putInt(swab((int)( timestampMultiplierReciprocal * t )));
                        }
                    }
                } else{
                    if ( use4ByteAddrTs ){
                        if ( timestampsEnabled ){
                            currentBuf.putInt(swab((int)( timestampMultiplierReciprocal * t )));
                        }
                        currentBuf.putInt(swab(addr[i]));
                    } else{
                        if ( timestampsEnabled ){
                            currentBuf.putShort((short)swab((int)( timestampMultiplierReciprocal * t )));
                        }
                        currentBuf.putInt(swab(addr[i]));
                    }
                }
                if ( currentBuf.remaining() < AENetworkInterfaceConstants.EVENT_SIZE_BYTES ){
//                log.info("breaking packet to fit max datagram");
                    // we break up into datagram packets of sendBufferSize
                    sendPacket();
                    maybeWriteSequenceNumber(currentBuf);
                }
            }
        } catch ( BufferOverflowException e ){
            log.warning(e.toString());
        }
        // send the remainder, if there are no events or exactly MAX_EVENTS this will get sent anyhow with sequence number only
        sendPacket();
    }

    synchronized private void allocateBuffers (){
        initialEmptyBuffer = ByteBuffer.allocateDirect(getBufferSize()); // the buffer to start capturing into
        initialFullBuffer = ByteBuffer.allocateDirect(getBufferSize());// the buffer to render/process first
        currentBuf = initialEmptyBuffer; // starting buffer for filling
        try{
            setSocketBufferSize();
        } catch ( SocketException ex ){
            log.warning("setting buffer size, caught " + ex.toString());
        }
    }

    private void sendPacket () throws IOException{
        try{
//            log.info("exchanging "+currentBuf);
            currentBuf = exchanger.exchange(currentBuf);
            currentBuf.clear();
            Thread.currentThread().yield();
        } catch ( Exception e ){
            log.warning(e.toString());
        }
    }

    private void setSocketBufferSize () throws SocketException{
        if ( socket == null ){
            log.warning("socket is null, cannot set its buffer size");
            return;
        }
        socket.setSendBufferSize(bufferSize); // TODO chyanging buffer size later doesn't change this initial value
        sendBufferSize = socket.getSendBufferSize();
        if ( sendBufferSize != bufferSize ){
            log.warning("socket could not be sized to hold " + bufferSize / AENetworkInterfaceConstants.EVENT_SIZE_BYTES + " events (" + bufferSize + " bytes), could only get sendBufferSize=" + sendBufferSize);
        } else{
            log.info("getSendBufferSize (bytes)=" + sendBufferSize);
        }
    }

    private void maybeWriteSequenceNumber (ByteBuffer buf) throws IOException{
        if ( isSequenceNumberEnabled() ){
//            log.info("sequence number="+packetSequenceNumber);
            buf.putInt(swab(packetSequenceNumber++));
        }
    }

    @Override
    public String toString (){
        return "AESocketOutputStream host=" + host + " at PORT=" + getPort();
    }

    public void close (){
        if ( socket == null ){
            return;
        }
        socket.close();
        if ( consumerThread != null ){
            consumerThread.interrupt();
        }
    }

    /**
     * Returns the buffer size for datagrams in bytes.
     * @return the bufferSize in bytes. This is the actual value reported by the underlying socket.
     */
    public int getBufferSize (){
        return sendBufferSize;
    }

    /**
     * Sets the desired maximum datagram size sent in bytes.
     * @param bufferSize the bufferSize to set in bytes.
     * @see #getBufferSize()
     */
    synchronized public void setBufferSize (int bufferSize){
        this.bufferSize = bufferSize;
        prefs.putInt("AEUnicastOutput.bufferSize",bufferSize);
        allocateBuffers();
//        try{
//            currentBuf = exchanger.exchange(currentBuf);
//        } catch ( InterruptedException ex ){
//            log.warning("during exchange of buffers from resize of buffers caught " + ex.toString());
//        }
    }

    public boolean isTimestampsEnabled (){
        return timestampsEnabled;
    }

    public void setTimestampsEnabled (boolean yes){
        this.timestampsEnabled = yes;
        prefs.putBoolean("AEUnicastOutput.timestampsEnabled",yes);
    }

    public void setPaused (boolean yes){
        // does nothing here
    }

    public boolean isPaused (){
        return false;
    }

    public void setLocalTimestampEnabled(boolean yes) {
        localTimestampsEnabled=yes;
        prefs.putBoolean("AEUnicastOutput.localTimestampsEnabled",localTimestampsEnabled);
    }

    public boolean isLocalTimestampEnabled() {
        return localTimestampsEnabled;
    }


    class Consumer implements Runnable{
        private final Exchanger<ByteBuffer> exchanger;
        private ByteBuffer buf;

        Consumer (Exchanger<ByteBuffer> exchanger,ByteBuffer initBuf){
            this.exchanger = exchanger;
            this.buf = initBuf;
        }

        public void run (){
            try{
                while ( true ){
                    buf = exchanger.exchange(buf);
                    buf.flip();
                    if(!buf.hasRemaining()) continue; // don't write empty packets
                    if ( !checkClient() ){ // if client not there, just continue - maybe it comes back
                        continue;
                    }
                    try{
//                        log.info("sending buf="+buf+" to client="+client);
                        channel.send(buf,client);
                        Thread.currentThread().yield(); // give up to possible receiver thread; see http://www.javamex.com/tutorials/threads/yield.shtml
                    } catch ( IOException e ){
                        e.printStackTrace();
                    }
                }
            } catch ( InterruptedException e ){
                log.info("Consumer interrupted");
            }
        }
    }

    public String getHost (){
        return host;
    }

    /** returns true if socket exists and is bound */
    private boolean checkClient (){
        if ( socket == null ){
            return false;
        }

        try{
            if ( socket.isBound() ){
                return true;
            }
            client = new InetSocketAddress(host,port);
            return true;
        } catch ( Exception se ){ // IllegalArgumentException or SecurityException
            log.warning("While checking client host=" + host + " port=" + port + " caught " + se.toString());
            return false;
        }
    }

    /** You need to setHost before this will send events.
    @param host the hostname
     */
    synchronized public void setHost (String host){
        this.host = host;
//        if ( checkClient() ){
            prefs.put("AEUnicastOutput.host",host);
//        }else{
//            log.warning("checkClient() returned false, not storing "+host+" in preferences");
//        }
    }

    public int getPort (){
        return port;
    }

    /** You set the port to say which port the packet will be sent to.
     *
     * @param port the UDP port number.
     */
    public void setPort (int port){
        this.port = port;
        prefs.putInt("AEUnicastOutput.port",port);
    }

    public boolean isSequenceNumberEnabled (){
        return sequenceNumberEnabled;
    }

    /** If set true (default), then an int32 sequence number is the first word of the packet. Otherwise the
     * first int32 is part of the first AE. 
     * 
     * @param sequenceNumberEnabled default true
     */
    public void setSequenceNumberEnabled (boolean sequenceNumberEnabled){
        this.sequenceNumberEnabled = sequenceNumberEnabled;
        prefs.putBoolean("AEUnicastOutput.sequenceNumberEnabled",sequenceNumberEnabled);
    }

    /** @see #setAddressFirstEnabled */
    public boolean isAddressFirstEnabled (){
        return addressFirstEnabled;
    }

    /** If set true, the first int32 of each AE is the address, and the second is the timestamp. If false,
     * the first int32 is the timestamp, and the second is the address.
     * This parameter is stored as a preference.
     * @param addressFirstEnabled default true. 
     */
    public void setAddressFirstEnabled (boolean addressFirstEnabled){
        this.addressFirstEnabled = addressFirstEnabled;
        prefs.putBoolean("AEUnicastOutput.addressFirstEnabled",addressFirstEnabled);
    }

    /** Java is little endian (in linear memory, LSB comes first, at lower address) and intel procesors are big endian.
     * If we send to a big endian host or native code, we can use this to swap the output ints to big endian. */
    public void setSwapBytesEnabled (boolean yes){
        swapBytesEnabled = yes;
        prefs.putBoolean("AEUnicastOutput.swapBytesEnabled",swapBytesEnabled);
    }

    public boolean isSwapBytesEnabled (){
        return swapBytesEnabled;
    }

    public float getTimestampMultiplier (){
        return timestampMultiplier;
    }

    /** Sets the mutliplier of jAER timestamps/remote host timestamps. The outgoing timestamps are divided
     * by timestampMultiplier to generate outgoing timestamps. The default jAER timestamp tick is 1 us. If the remote host
     * uses a 1 ms tick, then set the mutliplier to 1000 so that jAER timestamps are output with 1 ms tick.
     * @param timestampMultiplier
     */
    public void setTimestampMultiplier (float timestampMultiplier){
        this.timestampMultiplier = timestampMultiplier;
        prefs.putFloat("AEUnicastOutput.timestampMultiplier",timestampMultiplier);
        timestampMultiplierReciprocal = 1f / timestampMultiplier;
    }

    public void set4ByteAddrTimestampEnabled (boolean yes){
        use4ByteAddrTs = yes;
        prefs.putBoolean("AEUnicastOutput.use4ByteAddrTs",yes);
    }

    public boolean is4ByteAddrTimestampEnabled (){
        return use4ByteAddrTs;
    }

    private int swab (int v){
        if ( swapBytesEnabled ){
            return ByteSwapper.swap(v);
        } else{
            return v;
        }
    }

    private short swab (short v){
        if ( swapBytesEnabled ){
            return ByteSwapper.swap(v);
        } else{
            return v;
        }
    }
}
