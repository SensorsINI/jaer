/*
 * AEUnicastDialog.java
 *
 * Created on April 25, 2008, 8:40 AM
 */
package net.sf.jaer.eventio;
import java.nio.ByteBuffer;
import net.sf.jaer.aemonitor.*;
import net.sf.jaer.util.ByteSwapper;
import java.io.*;
import java.net.*;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.*;
import java.util.prefs.*;

/** Receives input via datagram (connectionless, UDP) packets from a server.
 * <p>
 * The socket binds to the port which comes initially from the Preferences for AEUnicastInput. The port
 * can be later changed.
 * <p>
Each packet consists of (by default)
 * 
1. a packet sequence integer (32 bits) which can be used to count missed packets
 * 
2. AEs. Each AE is a pair int32 address, int32 timestamp.
Timestamps are assumed to have 1us tick.
 * <p>
 * Options allow different choices for use of sequence number, size of address/timestamp,
 * order of address/timestamp, and swapping byte order to account for big/little endian peers.
 *
 * <p>
 * The datagram socket is not connected to the receiver, i.e., connect() is not called on the socket.
 * 
 * @see #setAddressFirstEnabled
 * @see #setSequenceNumberEnabled
 */
public class AEUnicastInput extends Thread implements AEUnicastSettings {

    // TODO If the remote host sends 16 bit timestamps, then a local unwrapping is done to extend the time range
    private static Preferences prefs=Preferences.userNodeForPackage(AEUnicastInput.class);
    private DatagramSocket datagramSocket=null;
   private boolean printedHost=false;
//    private String host=prefs.get("AEUnicastInput.host", "localhost");
    private int port=prefs.getInt("AEUnicastInput.port", AENetworkInterfaceConstants.DATAGRAM_PORT);
    private boolean sequenceNumberEnabled=prefs.getBoolean("AEUnicastInput.sequenceNumberEnabled", true);
    private boolean addressFirstEnabled=prefs.getBoolean("AEUnicastInput.addressFirstEnabled", true);
    private Exchanger<AEPacketRaw> exchanger=new Exchanger();
    private AEPacketRaw initialEmptyBuffer=new AEPacketRaw(); // the buffer to start capturing into
    private AEPacketRaw initialFullBuffer=new AEPacketRaw();    // the buffer to render/process first
    private AEPacketRaw currentFillingBuffer=initialEmptyBuffer; // starting buffer for filling
    private AEPacketRaw currentEmptyingBuffer=initialFullBuffer; // starting buffer to empty
    private static Logger log=Logger.getLogger("AESocketStream");
    private boolean swapBytesEnabled=prefs.getBoolean("AEUnicastInput.swapBytesEnabled", false);
    private float timestampMultiplier=prefs.getFloat("AEUnicastInput.timestampMultiplier", DEFAULT_TIMESTAMP_MULTIPLIER);
    private boolean use4ByteAddrTs=prefs.getBoolean("AEUnicastInput.use4ByteAddrTs", DEFAULT_USE_4_BYTE_ADDR_AND_TIMESTAMP);
    /** max size of event packet served to consumer by readPacket */
    public static int MAX_EVENT_BUFFER_SIZE=10000;
    /** Maximum time inteval in ms to exchange EventPacketRaw with consumer */
    static public final long MIN_INTERVAL_MS=30;
    final int TIMEOUT_MS=1000; // SO_TIMEOUT for receive in ms
    boolean stopme=false;
    boolean debugInput=false; // to print received amount of data
    private DatagramChannel channel;
    private int packetCounter=0;
    private int packetSequenceNumber=0;
    private EventRaw eventRaw=new EventRaw();
//    private int wrapCount=0;
    private int timeZero=0; // used to store initial timestamp for 4 byte timestamp reads to subtract this value
    private boolean readTimeZeroAlready=false;
    // receive buffer, allocated direct for speed
    private ByteBuffer buffer=ByteBuffer.allocateDirect(AENetworkInterfaceConstants.DATAGRAM_BUFFER_SIZE_BYTES);
//    Thread readingThread=null;

    /** Constructs an instance of AEUnicastInput and binds it to the default port.
     * The port preference value may have been modified from the Preferences
     * default by a previous setPort() call which
     * stored the preference value.
     * <p>
     * This Thread subclass must be started in order to receive event packets.
     * 
     */
    public AEUnicastInput() { // TODO basic problem here is that if port is unavailable, then we cannot construct and set port
        setName("AUnicastInput");
    }

    // TODO javadoc
    public AEUnicastInput(int port){
        super();
        setPort(port);
    }

    /** This run method loops forever, filling the current filling buffer so that readPacket can return data
     * that may be processed while the other buffer is being filled.
     */
    public void run() {
        while(!stopme) {
            if(!checkSocket()) {
                // if port cannot be bound, just try again in a bit
                try {
                    Thread.sleep(1000);
                } catch(Exception e) {
                    log.warning("tried to bind "+this+" but got "+e);
                }
                continue;
            }
            // try to receive a datagram packet and add it to the currentFillingBuffer - but this call will timeout after some ms
            try {
                receiveDatagramAndAddToCurrentEventPacket(currentFillingBuffer);
            } catch(NullPointerException e) {
                log.warning(e.toString());
                break;
            }
//            long t = System.currentTimeMillis();
//            if (currentFillingBuffer.getNumEvents() >= MAX_EVENT_BUFFER_SIZE || (t - lastExchangeTime > MIN_INTERVAL_MS)) {
////                    System.out.println("swapping buffer to rendering: " + currentFillingBuffer);
//                lastExchangeTime = t;
            // TODO if the rendering thread does not ask for a buffer, we just sit here - in the meantime incoming packets are lost
            // because we are not calling receive on them. 
            // currentBuffer starts as initialEmptyBuffer that we initially captured to, after exchanger,
            // current buffer is initialFullBuffer 
            try {
                currentFillingBuffer=exchanger.exchange(currentFillingBuffer, 1, TimeUnit.MILLISECONDS); // get buffer to write to from consumer thread
                // times out after 1 ms in which case we try again to read a datagram with addToBuffer
                currentFillingBuffer.setNumEvents(0); // reset event counter
            } catch(InterruptedException ex) {
                log.info("interrupted");
                stopme=true;
                break;
            } catch(TimeoutException ex) {
                // didn't exchange within timeout, just add more events since we didn't get exchange request from the consumer this time
            }
        }
        log.info("closing datagramSocket");
        cleanup();
    }

    /** Returns the latest buffer of events. If a timeout occurs occurs a null packet is returned.
     * @return the events collected since the last call to readPacket(), or null on a timeout or interrupt.
     */
    public AEPacketRaw readPacket() {
//        readingThread=Thread.currentThread();
        try {
            currentEmptyingBuffer=exchanger.exchange(currentEmptyingBuffer,TIMEOUT_MS,TimeUnit.MILLISECONDS);
            if(debugInput&&currentEmptyingBuffer.getNumEvents()>0) {
//                log.info("exchanged and returning readPacket="+currentEmptyingBuffer);
            }
            return currentEmptyingBuffer;
        } catch(InterruptedException e) {
            log.info("Interrupted exchange of buffers in AEUnicastInput: "+e.toString());
            return null;
        } catch(TimeoutException toe){
            return null;
        }
    }

    /** Adds to the packet supplied as argument by receiving
     * a single datagram and processing the data in it.
    @param packet the packet to add to.
     */
    private void receiveDatagramAndAddToCurrentEventPacket(AEPacketRaw packet) throws NullPointerException {
        try {
            if(datagramSocket==null) {
                throw new NullPointerException("datagram socket became null for "+AEUnicastInput.this);
            }
            SocketAddress client=channel.receive(buffer);
            if(!printedHost) {
                printedHost=true;
                log.info("received first packet from "+client+" of length "+buffer.position()+" bytes");
            }
        } catch(SocketTimeoutException to) {
            // just didn't fill the buffer in time, ignore
            log.warning(to.toString());
            return;
        } catch(IOException e) {
            log.warning(e.toString());
            packet.setNumEvents(0);
            return;
        }
//        log.info("received buffer="+buffer);
        buffer.flip();
        if(buffer.limit()<Integer.SIZE/8) {
            log.warning(String.format("DatagramPacket only has %d bytes, and thus doesn't even have sequence number, returning empty packet", buffer.limit()));
            packet.setNumEvents(0);
        }
        packetCounter++;
        int eventSize=use4ByteAddrTs?AENetworkInterfaceConstants.EVENT_SIZE_BYTES:4;
        int seqNumLength=sequenceNumberEnabled?Integer.SIZE/8:0;
        int nEventsInPacket=(buffer.limit()-seqNumLength)/eventSize;
        if(sequenceNumberEnabled) {
            packetSequenceNumber=swab(buffer.getInt());
//                log.info("recieved packet with sequence number "+packetSequenceNumber);
            if(packetSequenceNumber!=packetCounter) {
                log.warning(
                        String.format("Dropped %d packets. (Incoming packet sequence number (%d) doesn't match expected packetCounter (%d), resetting packetCounter to match present incoming sequence number)",
                        (packetSequenceNumber-packetCounter),
                        packetSequenceNumber,
                        packetCounter));
                packetCounter=packetSequenceNumber;
            }
        }
        // extract the ae data and add events to the packet we are presently filling
        for(int i=0; i<nEventsInPacket; i++) {
            if(addressFirstEnabled) {
                if(use4ByteAddrTs) {
                    eventRaw.address=swab(buffer.getInt()); // swapInt is switched to handle big endian event sources (like ARC camera)
                    int v=buffer.getInt();
                    int rawTime=swab(v);
                    int zeroedRawTime;
                    if(readTimeZeroAlready) {
                        // TDS sends 32 bit timestamp which overflows after multiplication
                        // by timestampMultiplier and cast to int jaer timestamp
                        zeroedRawTime=rawTime-timeZero;
                    } else {
                        readTimeZeroAlready=true;
                        timeZero=rawTime;
                        zeroedRawTime=0;
                    }
//                        int v3 = 0xffff & v2; // TODO hack for TDS sensor which uses all 32 bits causing overflow after multiplication by multiplier and int cast
                    float floatFinalTime=timestampMultiplier*zeroedRawTime;
                    int finalTime;
                    if(floatFinalTime>=Integer.MAX_VALUE) {
                        timeZero=rawTime; // after overflow reset timezero
                        finalTime=0; // wrap around at 2k seconds, back to 0 seconds. TODO different than hardware which wraps back to -2k seconds
                    } else {
                        finalTime=(int) floatFinalTime;
                    }
                    eventRaw.timestamp=finalTime;
                } else {
                    eventRaw.address=swab(buffer.getShort()); // swapInt is switched to handle big endian event sources (like ARC camera)
                    eventRaw.timestamp=(int) (timestampMultiplier*(int) swab(buffer.getShort()));
                }
            } else {
                if(use4ByteAddrTs) {
                    eventRaw.timestamp=(int) (swab(buffer.getInt()));
                    eventRaw.address=swab(buffer.getInt());
                } else {
                    eventRaw.timestamp=(int) (swab(buffer.getShort()));
                    eventRaw.address=(int) (timestampMultiplier*(int) swab(buffer.getShort()));
                }
            }
            packet.addEvent(eventRaw);
        }
        buffer.clear();
    }

    @Override
    public String toString() {
        return "AEUnicastInput at PORT="+getPort();
    }

    /** Interrupts the thread which is acquiring data and closes the underlying DatagramSocket.
     * 
     */
    synchronized public void close() {
        interrupt(); // TODO this interrupts the thread reading from the socket, but not the one that is blocked on the exchange
//        if(readingThread!=null) readingThread.interrupt();
    }

    /**@ return "localhost". */
    public String getHost() {
        return "localhost";
    }

    private void cleanup() {
        try {
            datagramSocket.close();
        } catch(Exception e) {
            log.warning("on closing caught "+e);
        }
    }

    /** resolves host, builds socket, returns true if it succeeds */
    private boolean checkSocket() {
        if(datagramSocket!=null&&datagramSocket.isBound()) {
            return true;
        }
        try {
            channel=DatagramChannel.open();
            datagramSocket=channel.socket();
            datagramSocket.setSoTimeout(TIMEOUT_MS);
            if(datagramSocket.getSoTimeout()!=TIMEOUT_MS) {
                log.warning("datagram socket read timeout value read="+datagramSocket.getSoTimeout()+" which is different than timeout value of "+TIMEOUT_MS+" that we tried to set - perhaps timeout is not supported?");
            }
            SocketAddress address=new InetSocketAddress(getPort());
            datagramSocket.bind(address);
            log.info("bound "+this);
            return true;
        } catch(IOException e) {
            log.warning("caught "+e+", datagramSocket will be constructed later");
            return false;
        }
    }

    /** Opens the input. Binds the port and starts the background receiver thread.
     * 
     * @throws java.io.IOException
     */
    public void open() throws IOException{
        // TODO do something here
    }

    /** 
    @param host the hostname
     * @deprecated doesn't do anything here because we only set local port
     */
    public void setHost(String host) { // TODO all wrong, doesn't need host since receiver
        log.warning("setHost("+host+") ignored for AEUnicastInput");
        // TODO should make new socket here too since we may have changed the host since we connected the socket
//        this.host=host;
//        prefs.put("AEUnicastInput.host", host);
    }

    public int getPort() {
        return port;
    }

    /** Set the local port for receiving events.
     * 
     * @param port
     */
    public void setPort(int port) { // TODO all wrong
        this.port=port;
        prefs.putInt("AEUnicastInput.port", port);
        if(port==this.port) {
            log.info("port "+port+" is already the bound port for "+this);
            return;
        }
        readTimeZeroAlready=false;
    }

    public boolean isSequenceNumberEnabled() {
        return sequenceNumberEnabled;
    }

    /** If set true (default), then an int32 sequence number is the first word of the packet. Otherwise the
     * first int32 is part of the first AE. 
     * 
     * @param sequenceNumberEnabled default true
     */
    public void setSequenceNumberEnabled(boolean sequenceNumberEnabled) {
        this.sequenceNumberEnabled=sequenceNumberEnabled;
        prefs.putBoolean("AEUnicastInput.sequenceNumberEnabled", sequenceNumberEnabled);
    }

    /** @see #setAddressFirstEnabled */
    public boolean isAddressFirstEnabled() {
        return addressFirstEnabled;
    }

    /** If set true, the first int32 of each AE is the address, and the second is the timestamp. If false,
     * the first int32 is the timestamp, and the second is the address.
     * This parameter is stored as a preference.
     * @param addressFirstEnabled default true. 
     */
    public void setAddressFirstEnabled(boolean addressFirstEnabled) {
        this.addressFirstEnabled=addressFirstEnabled;
        prefs.putBoolean("AEUnicastInput.addressFirstEnabled", addressFirstEnabled);
    }

    // TODO javadoc
    public void setSwapBytesEnabled(boolean yes) {
        swapBytesEnabled=yes;
        prefs.putBoolean("AEUnicastInput.swapBytesEnabled", swapBytesEnabled);
    }

    public boolean isSwapBytesEnabled() {
        return swapBytesEnabled;
    }

    public float getTimestampMultiplier() {
        return timestampMultiplier;
    }

    /** Timestamps from the remote host are multiplied by this value to become jAER timestamps.
     * If the remote host uses 1 ms timestamps, set timestamp multiplier to 1000.
     * @param timestampMultiplier
     */
    public void setTimestampMultiplier(float timestampMultiplier) {
        this.timestampMultiplier=timestampMultiplier;
        prefs.putFloat("AEUnicastInput.timestampMultiplier", timestampMultiplier);
    }

    // TODO javadoc
    public void set4ByteAddrTimestampEnabled(boolean yes) {
        use4ByteAddrTs=yes;
        prefs.putBoolean("AEUnicastInput.use4ByteAddrTs", yes);
    }

    public boolean is4ByteAddrTimestampEnabled() {
        return use4ByteAddrTs;
    }

    private int swab(int v) {
        if(swapBytesEnabled) {
            return ByteSwapper.swap(v);
        } else {
            return v;
        }
    }

    private short swab(short v) {
        if(swapBytesEnabled) {
            return ByteSwapper.swap(v);
        } else {
            return v;
        }
    }
}
