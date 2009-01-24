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

import net.sf.jaer.aemonitor.*;
import net.sf.jaer.util.ByteSwapper;
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.*;
import java.util.prefs.*;

/**
 * Streams AE packets to network using UDP DatagramPacket's that are unicast. AEViewers can receive these packets to render them.
 * 
<p>
The implementation using a BlockingQueue to buffer the AEPacketRaw's that are offered.
 *
 * @author tobi
 */
public class AEUnicastOutput implements AEUnicastSettings {

    static Preferences prefs = Preferences.userNodeForPackage(AEUnicastOutput.class);
//    enum State {WAITING,CONNECTED};
//    State state=State.WAITING;
    int sendBufferSize = 0;
    final int QUEUE_LENGTH = 10;
    BlockingQueue<DatagramPacket> queue = new ArrayBlockingQueue<DatagramPacket>(QUEUE_LENGTH);
    static Logger log = Logger.getLogger("AEUnicastOutput");
//    protected DatagramChannel channel=null;
    protected DatagramSocket socket = null;
    DatagramPacket packet = null;
    int packetSequenceNumber = 0;
    InetAddress address = null;
    int packetSizeBytes;
    Thread consumerThread;
    private String host = prefs.get("AEUnicastOutput.host", "localhost");
    private int port = prefs.getInt("AEUnicastOutput.port", AENetworkInterfaceConstants.DATAGRAM_PORT);
    private boolean sequenceNumberEnabled = prefs.getBoolean("AEUnicastOutput.sequenceNumberEnabled", true);
    private boolean addressFirstEnabled = prefs.getBoolean("AEUnicastOutput.addressFirstEnabled", true);
    private float timestampMultiplier = prefs.getFloat("AEUnicastOutput.timestampMultiplier", DEFAULT_TIMESTAMP_MULTIPLIER);
    private float timestampMultiplierReciprocal = 1f / timestampMultiplier;
    private boolean swapBytesEnabled = prefs.getBoolean("AEUnicastOutput.swapBytesEnabled", false);
    private boolean use4ByteAddrTs = prefs.getBoolean("AEUnicastOutput.use4ByteAddrTs", DEFAULT_USE_4_BYTE_ADDR_AND_TIMESTAMP);

    /** Creates a new instance, binding any available port (since we will be just sending from here).
     * The port and host need to be sent before any packets will be sent.
     * @see #setHost
     * @see #setPort
     */
    public AEUnicastOutput() {
        try {
//            channel=DatagramChannel.open();
            socket = new DatagramSocket(); // bind to any available port because we will be sending datagrams with included host:port info
            socket.setTrafficClass(0x10 + 0x08); // low delay
//            log.info("output stream datagram traffic class is " + socket.getTrafficClass());
            socket.setSendBufferSize(AENetworkInterfaceConstants.DATAGRAM_BUFFER_SIZE_BYTES);
            sendBufferSize = socket.getSendBufferSize();
            if (sendBufferSize != AENetworkInterfaceConstants.DATAGRAM_BUFFER_SIZE_BYTES) {
                log.warning("socket could not be sized to hold MAX_EVENTS=" + AENetworkInterfaceConstants.MAX_DATAGRAM_EVENTS + " (" + AENetworkInterfaceConstants.DATAGRAM_BUFFER_SIZE_BYTES + " bytes), could only get sendBufferSize=" + sendBufferSize);
            } else {
                log.info("getSendBufferSize (bytes)=" + sendBufferSize);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        packetSizeBytes = AENetworkInterfaceConstants.MAX_DATAGRAM_EVENTS * AENetworkInterfaceConstants.EVENT_SIZE_BYTES + Integer.SIZE / 8;
        Consumer consumer = new Consumer(queue);
        consumerThread = new Thread(consumer, "AEUnicastOutput");
        consumerThread.start();
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
    If an empty packet is supplied as ae, then a packet is still written but it contains only a sequence number.
     * <p>
     * This method actually offers new Datagram packets to the consumer thread for later tranmission. The datagram address and port are taken from
     * the current settings for the AEUnicastOutput.
     * 
     *@param ae a raw addresse-event packet
     */
    synchronized public void writePacket(AEPacketRaw ae) throws IOException {

        if (address == null) {
            log.warning("no address (host) has been specified for this AEUnicastOutput");
            return;
        }

        int nEvents = ae.getNumEvents();
        if (nEvents == 0) {
            return;
        }

        int npackets = 1 + nEvents / AENetworkInterfaceConstants.MAX_DATAGRAM_EVENTS;
//        if(npackets>1){
//            log.info("splitting packet with "+nEvents+" events into "+npackets+" DatagramPackets each with "+AESocketInterface.MAX_EVENTS+" events, starting with sequence number "+packetSequenceNumber);
//        }

        int[] addr = ae.getAddresses();
        int[] ts = ae.getTimestamps();

//        reset(); // reset to start of byte array

//        ByteArrayOutputStream bos=new ByteArrayOutputStream(nEvents*AESocketStream.EVENT_SIZE_BYTES+Integer.SIZE/8

        int count = 0; // count of events sent in one DatagramPacket

        ByteArrayOutputStream bos = new ByteArrayOutputStream(packetSizeBytes);
        DataOutputStream dos = new DataOutputStream(bos);

        // write the sequence number for this DatagramPacket to the buf for this ByteArrayOutputStream
        if (isSequenceNumberEnabled()) {
            dos.writeInt(swab(packetSequenceNumber++));
        }

        for (int i = 0; i < nEvents; i++) {
            // writes values in big endian (MSB first)
            // write n events, but if we exceed DatagramPacket buffer size, then make a DatagramPacket and send it, then reset this ByteArrayOutputStream
            if (addressFirstEnabled) {
                if (use4ByteAddrTs) {
                    dos.writeInt(swab(addr[i]));
                    dos.writeInt(swab((int) (timestampMultiplierReciprocal * ts[i])));
                } else {
                    dos.writeShort(swab(addr[i]));
                    dos.writeShort(swab((int) (timestampMultiplierReciprocal * ts[i])));
                }
            } else {
                if (use4ByteAddrTs) {
                    dos.writeInt(swab((int) (timestampMultiplierReciprocal * ts[i])));
                    dos.writeInt(swab(addr[i]));
                } else {
                    dos.writeShort(swab((int) (timestampMultiplierReciprocal * ts[i])));
                    dos.writeShort(swab(addr[i]));
                }
            }
            if ((++count) == AENetworkInterfaceConstants.MAX_DATAGRAM_EVENTS) {
                // we break up into datagram packets of sendBufferSize
                packet = new DatagramPacket(bos.toByteArray(), bytePacketSizeFromNumEvents(count), address, getPort());
                boolean offered = queue.offer(packet);
                if (!offered) {
                    log.info("queue full (>" + QUEUE_LENGTH + " packets)");
                    packetSequenceNumber--;
                }
                count = 0;
                bos = new ByteArrayOutputStream(packetSizeBytes);
                dos = new DataOutputStream(bos);
                if (isSequenceNumberEnabled()) {
                    dos.writeInt(swab(packetSequenceNumber++));
                }
            }
        }
        // send the remainder, if there are no events or exactly MAX_EVENTS this will get sent anyhow with sequence number only
        packet = new DatagramPacket(bos.toByteArray(), bytePacketSizeFromNumEvents(count), address, getPort());
        queue.offer(packet);
    }

    @Override
    public String toString() {
        return "AESocketOutputStream host=" + host + " at PORT=" + getPort();
    }

    // returns size of datagram packet in bytes for count events, including sequence number
    private int bytePacketSizeFromNumEvents(int count) {
        return count * AENetworkInterfaceConstants.EVENT_SIZE_BYTES + (sequenceNumberEnabled ? (Integer.SIZE / 8) : 0);
    }

    public void close() {
        socket.disconnect();
        socket.close();
        if (consumerThread != null) {
            consumerThread.interrupt();
        }
    }

    class Consumer implements Runnable {

        private final BlockingQueue<DatagramPacket> q;

        Consumer(BlockingQueue<DatagramPacket> q) {
            this.q = q;
        }

        public void run() {
            try {
                while (true) {
//                    if(socket==null || (socket!=null && !socket.isConnected())){
//                        log.warning("trying to send datagram packet but socket is null or not connected yet");
//                        continue;
//                    }
                    DatagramPacket p = q.take();
                    if (!checkHost()) {
                        continue;
                    }
                    try {
//                        socket.connect(address,AESocketInterface.PORT);
                        if (socket == null) {
                            continue;
                        }
                        socket.send(p);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } catch (InterruptedException e) {
                log.info("Consumer interrupted");
            }
        }
    }

    public String getHost() {
        return host;
    }

    boolean checkHost() {
        if (socket != null && address != null) {
            return true;
        }
        try {
            address = InetAddress.getByName(host);
            log.info("host " + host + " resolved to " + address);
//            socket.connect(address,AESocketInterface.PORT);
            return true;
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return false;
        } catch (SecurityException se) {
            se.printStackTrace();
            address = null;
            return false;
        }
    }

    /** You need to setHost before this will send events.
    @param host the hostname
     */
    synchronized public void setHost(String host) {
        this.host = host;
        if (checkHost()) {
            prefs.put("AEUnicastOutput.host", host);
        }
    }

    public int getPort() {
        return port;
    }

    /** You set the port to say which port the packet will be sent to.
     *
     * @param port the UDP port number.
     */
    public void setPort(int port) {
        this.port = port;
        prefs.putInt("AEUnicastOutput.port", port);
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
        this.sequenceNumberEnabled = sequenceNumberEnabled;
        prefs.putBoolean("AEUnicastOutput.sequenceNumberEnabled", sequenceNumberEnabled);
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
        this.addressFirstEnabled = addressFirstEnabled;
        prefs.putBoolean("AEUnicastOutput.addressFirstEnabled", addressFirstEnabled);
    }

    /** Java is little endian and intel procesors are big endian. If we send to a big endian host, we can use this to swap the output ints to big endian. */
    public void setSwapBytesEnabled(boolean yes) {
        swapBytesEnabled = yes;
        prefs.putBoolean("AEUnicastOutput.swapBytesEnabled", swapBytesEnabled);
    }

    public boolean isSwapBytesEnabled() {
        return swapBytesEnabled;
    }

    public float getTimestampMultiplier() {
        return timestampMultiplier;
    }

    /** Sets the mutliplier of jAER timestamps/remote host timestamps. The outgoing timestamps are divided
     * by timestampMultiplier to generate outgoing timestamps. The default jAER timestamp tick is 1 us. If the remote host
     * uses a 1 ms tick, then set the mutliplier to 1000 so that jAER timestamps are output with 1 ms tick.
     * @param timestampMultiplier
     */
    public void setTimestampMultiplier(float timestampMultiplier) {
        this.timestampMultiplier = timestampMultiplier;
        prefs.putFloat("AEUnicastOutput.timestampMultiplier", timestampMultiplier);
        timestampMultiplierReciprocal = 1f / timestampMultiplier;
    }

    public void set4ByteAddrTimestampEnabled(boolean yes) {
        use4ByteAddrTs = yes;
        prefs.putBoolean("AEUnicastOutput.use4ByteAddrTs", yes);
    }

    public boolean is4ByteAddrTimestampEnabled() {
        return use4ByteAddrTs;
    }

    private int swab(int v) {
        if (swapBytesEnabled) {
            return ByteSwapper.swap(v);
        } else {
            return v;
        }
    }

    private short swab(short v) {
        if (swapBytesEnabled) {
            return ByteSwapper.swap(v);
        } else {
            return v;
        }
    }
}
