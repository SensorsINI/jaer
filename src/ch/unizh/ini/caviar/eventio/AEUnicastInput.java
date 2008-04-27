/*
 * AEUnicastDialog.java
 *
 * Created on April 25, 2008, 8:40 AM
 */
package ch.unizh.ini.caviar.eventio;

import ch.unizh.ini.caviar.aemonitor.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.Exchanger;
import java.util.logging.*;
import java.util.prefs.*;

/** Receives input via datagram (connectionless, UDP) packets from a server.
Each packet consists of (by default)
 * 
1. a packet sequence integer (32 bits) which can be used to count missed packets
 * 
2. AEs. Each AE is a pair int32 address, int32 timestamp.
Timestamps are assumed to have 1us tick. 
 * @see #setAddressFirstEnabled
 * @see #setSequenceNumberEnabled
 */
public class AEUnicastInput extends Thread implements AEUnicastSettings {

    private static Preferences prefs = Preferences.userNodeForPackage(AEUnicastInput.class);
    private DatagramSocket datagramSocket = null;
    private InetAddress address = null;
    private ByteArrayInputStream bis = null;
    private DataInputStream dis = null;
    private boolean printedHost = false;
    private String host = prefs.get("AEUnicastInput.host", "localhost");
    private int port = prefs.getInt("AEUnicastInput.port", AENetworkInterface.DATAGRAM_PORT);
    private boolean sequenceNumberEnabled = prefs.getBoolean("AEUnicastInput.sequenceNumberEnabled", true);
    private boolean addressFirstEnabled = prefs.getBoolean("AEUnicastInput.addressFirstEnabled", true);
    public static int EVENT_BUFFER_SIZE = 1000; // size of AEPackets that are served by readPacket
    private Exchanger<AEPacketRaw> exchanger = new Exchanger();
    private AEPacketRaw initialEmptyBuffer = new AEPacketRaw(); // the buffer to start capturing into
    private AEPacketRaw initialFullBuffer = new AEPacketRaw();    // the buffer to render/process first
    private AEPacketRaw currentFillingBuffer = initialEmptyBuffer; // starting buffer for filling
    private AEPacketRaw currentEmptyingBuffer = initialFullBuffer; // starting buffer to empty
    private long lastExchangeTime = System.currentTimeMillis();
    private static Logger log = Logger.getLogger("AESocketStream");
    private boolean swapBytesEnabled=prefs.getBoolean("AEUnicastInput.swapBytesEnabled", false);
    private float timestampMultiplier=prefs.getFloat("AEUnicastInput.timestampMultiplier",DEFAULT_TIMESTAMP_MULTIPLIER);

    public AEUnicastInput() throws IOException {
        datagramSocket = new DatagramSocket(getPort());
        setName("AUnicastInput");
    }

    public void run() {
        try {
            while (currentFillingBuffer != null) {
                if (!checkHost()) {
                    try {
                        Thread.currentThread().sleep(100);
                    } catch (Exception e) {
                    }
                    ;
                    continue;
                }
                addToBuffer(currentFillingBuffer);
                long t = System.currentTimeMillis();
                if (currentFillingBuffer.getNumEvents() >= EVENT_BUFFER_SIZE || (t - lastExchangeTime > AENetworkInterface.MIN_INTERVAL_MS)) {
//                    System.out.println("swapping buffer to rendering: " + currentFillingBuffer);
                    lastExchangeTime = t;
                    currentFillingBuffer = exchanger.exchange(currentFillingBuffer); // get buffer to write to
                    // currentBuffer starts as initialEmptyBuffer that we initially captured to, after exchanger,
                    // current buffer is initialFullBuffer 
                    currentFillingBuffer.setNumEvents(0); // reset event counter
                }
            }
            datagramSocket.close();
        } catch (InterruptedException e) {
            log.info("interrupted");
        }
    }

    /** Returns the latest buffer of events 
     * @return the events collected since the last call to readPacket()
     */
    public AEPacketRaw readPacket() {
        try {
            currentEmptyingBuffer = exchanger.exchange(currentEmptyingBuffer);
//            System.out.println("returning readPacket=" + currentEmptyingBuffer);
            return currentEmptyingBuffer;
        } catch (InterruptedException e) {
            return null;
        }
    }
    
    private AEPacketRaw packet = null;
    private byte[] buf = null;
    private DatagramPacket datagram;
    private int packetCounter = 0;
    private int packetSequenceNumber = 0;
    private EventRaw eventRaw = new EventRaw();

    /** adds to the buffer from received packets */
    private void addToBuffer(AEPacketRaw packet) {
        if (buf == null) {
            buf = new byte[AENetworkInterface.DATAGRAM_BUFFER_SIZE_BYTES];
        }
        if (datagram == null) {
            datagram = new DatagramPacket(buf, buf.length);
        }
        if (bis == null) {
            bis = new ByteArrayInputStream(buf);
            dis = new DataInputStream(bis);
        }
        try {
            datagramSocket.receive(datagram);
            if (!printedHost) {
                printedHost = true;
                SocketAddress addr = datagram.getSocketAddress();
                log.info("received a packet from " + addr);
                datagramSocket.connect(addr);
            }
            if (datagram.getLength() < Integer.SIZE / 8) {
                log.warning(String.format("DatagramPacket only has %d bytes, and thus doesn't even have sequence number, returning empty packet", datagram.getLength()));
                packet.setNumEvents(0);
            }
            packetCounter++;
            int nEventsInPacket = (datagram.getLength() - Integer.SIZE / 8) / AENetworkInterface.EVENT_SIZE_BYTES;
//            System.out.println(nEventsInPacket + " events");
            dis.reset();
            if (sequenceNumberEnabled) {
                packetSequenceNumber = dis.readInt();
                if (packetSequenceNumber != packetCounter) {
                    log.warning(
                            String.format("Dropped %d packets. (Incoming packet sequence number (%d) doesn't match expected packetCounter (%d), resetting packetCounter to match present incoming sequence number)",
                            (packetSequenceNumber - packetCounter),
                            packetSequenceNumber,
                            packetCounter));
                    packetCounter = packetSequenceNumber;
                }
            }
            for (int i = 0; i < nEventsInPacket; i++) {
                if (addressFirstEnabled) {
                    eventRaw.address = swabInt(dis.readInt()); // swapInt is switched to handle big endian event sources (like ARC camera)
                    eventRaw.timestamp = (int)( timestampMultiplier*swabInt(dis.readInt()));
                } else {
                    eventRaw.timestamp = (int)(swabInt(dis.readInt()));
                    eventRaw.address = swabInt(dis.readInt());
                }
                packet.addEvent(eventRaw);
            }
        } catch (IOException e) {
            log.warning(e.getMessage());
            packet.setNumEvents(0);
            close();
        }
    }

    @Override
    public String toString() {
        return "AESocketInputStream host=" + host + " at PORT=" + getPort();
    }

    synchronized public void close() {
        interrupt();
        datagramSocket.close();
    }

    public String getHost() {
        return host;
    }

    private boolean checkHost() {
        if (address != null) {
            return true;
        }
        try {
            address = InetAddress.getByName(host);
            log.info("host " + host + " resolved to " + address);
            datagramSocket.connect(address, port);
            return true;
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** You need to setHost before this will receive events
    @param host the hostname
     */
    public void setHost(String host) {
        this.host = host;
        prefs.put("AEUnicastInput.host", host);
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
        prefs.putInt("AEUnicastInput.port", port);
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
        this.addressFirstEnabled = addressFirstEnabled;
        prefs.putBoolean("AEUnicastInput.addressFirstEnabled", addressFirstEnabled);
    }

    /** swaps the int32 bytes for big/little endianness */
    public final int swabInt(int v) {
        if (swapBytesEnabled) {
            return (v >>> 24) | (v << 24) |
                    ((v << 8) & 0x00FF0000) | ((v >> 8) & 0x0000FF00);
        } else {
            return v;
        }
    }

    public void setSwapBytesEnabled(boolean yes) {
        swapBytesEnabled=yes;
        prefs.putBoolean("AEUnicastInput.swapBytesEnabled",swapBytesEnabled);
    }

    public boolean isSwapBytesEnabled() {
        return swapBytesEnabled;
    }

    public float getTimestampMultiplier() {
        return timestampMultiplier;
    }

    public void setTimestampMultiplier(float timestampMultiplier) {
        this.timestampMultiplier = timestampMultiplier;
        prefs.putFloat("AEUnicastInput.timestampMultiplier",timestampMultiplier);
    }
}
