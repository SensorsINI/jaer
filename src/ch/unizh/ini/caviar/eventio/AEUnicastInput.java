/*
 * AEUnicastDialog.java
 *
 * Created on April 25, 2008, 8:40 AM
 */
package ch.unizh.ini.caviar.eventio;

import ch.unizh.ini.caviar.aemonitor.*;
import ch.unizh.ini.caviar.util.ByteSwapper;
import java.io.*;
import java.net.*;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.*;
import java.util.prefs.*;

/** Receives input via datagram (connectionless, UDP) packets from a server.
 * <p>
 * The socket binds to the port which is 
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
    private int port = prefs.getInt("AEUnicastInput.port", AENetworkInterfaceConstants.DATAGRAM_PORT);
    private boolean sequenceNumberEnabled = prefs.getBoolean("AEUnicastInput.sequenceNumberEnabled", true);
    private boolean addressFirstEnabled = prefs.getBoolean("AEUnicastInput.addressFirstEnabled", true);
    private Exchanger<AEPacketRaw> exchanger = new Exchanger();
    private AEPacketRaw initialEmptyBuffer = new AEPacketRaw(); // the buffer to start capturing into
    private AEPacketRaw initialFullBuffer = new AEPacketRaw();    // the buffer to render/process first
    private AEPacketRaw currentFillingBuffer = initialEmptyBuffer; // starting buffer for filling
    private AEPacketRaw currentEmptyingBuffer = initialFullBuffer; // starting buffer to empty
    private long lastExchangeTime = System.currentTimeMillis();
    private static Logger log = Logger.getLogger("AESocketStream");
    private boolean swapBytesEnabled = prefs.getBoolean("AEUnicastInput.swapBytesEnabled", false);
    private float timestampMultiplier = prefs.getFloat("AEUnicastInput.timestampMultiplier", DEFAULT_TIMESTAMP_MULTIPLIER);
    private boolean use4ByteAddrTs = prefs.getBoolean("AEUnicastInput.use4ByteAddrTs", DEFAULT_USE_4_BYTE_ADDR_AND_TIMESTAMP);
    /** max size of event packet served to consumer by readPacket */
    public static int MAX_EVENT_BUFFER_SIZE = 10000;
    /** Maximum time inteval in ms to exchange EventPacketRaw with consumer */
    static public final long MIN_INTERVAL_MS = 30;
    final int TIMEOUT_MS = 20; // SO_TIMEOUT for receive in ms
    boolean stopme=false;
    boolean debugInput=false; // to print received amount of data
    private AEPacketRaw packet = null;
    private byte[] buf = null;
    private DatagramPacket datagram;
    private int packetCounter = 0;
    private int packetSequenceNumber = 0;
    private EventRaw eventRaw = new EventRaw();
    
    /** Constructs an instance of AEUnicastInput and binds it to the default port.
     * The port preference value may have been modified from the Preferences default by a previous setPort() call which
     * stored the preference value.
     * <p>
     * This Thread subclass must be started in order to receive event packets.
     * 
     * @throws java.io.IOException if the port is already bound.
     */
    public AEUnicastInput() throws IOException {
        datagramSocket = new DatagramSocket(getPort());
        datagramSocket.setSoTimeout(TIMEOUT_MS);
        if(datagramSocket.getSoTimeout()!=TIMEOUT_MS){
            log.warning("datagram socket read timeout value read="+datagramSocket.getSoTimeout()+" which is different than timeout value of "+TIMEOUT_MS+" that we tried to set - perhaps timeout is not supported?");
        }
        setName("AUnicastInput");
    }

    /** This run method loops forever, filling the current filling buffer so that readPacket can return data
     * that may be processed while the other buffer is being filled.
     */
    public void run() {
        while (!stopme) {
            if (!checkHost()) {
                // if host cannot be resolved, just try again in a bit
                try {
                    Thread.currentThread().sleep(100);
                } catch (Exception e) {
                }
                continue;
            }
            // try to receive a datagram packet and add it to the currentFillingBuffer - but this call will timeout after some ms
            addToBuffer(currentFillingBuffer);
//            long t = System.currentTimeMillis();
//            if (currentFillingBuffer.getNumEvents() >= MAX_EVENT_BUFFER_SIZE || (t - lastExchangeTime > MIN_INTERVAL_MS)) {
////                    System.out.println("swapping buffer to rendering: " + currentFillingBuffer);
//                lastExchangeTime = t;
            // TODO if the rendering thread does not ask for a buffer, we just sit here - in the meantime incoming packets are lost
            // because we are not calling receive on them. 
            // currentBuffer starts as initialEmptyBuffer that we initially captured to, after exchanger,
            // current buffer is initialFullBuffer 
            try {
                currentFillingBuffer = exchanger.exchange(currentFillingBuffer, 1, TimeUnit.MILLISECONDS); // get buffer to write to from consumer thread
                // times out after 1 ms in which case we try again to read a datagram with addToBuffer
                currentFillingBuffer.setNumEvents(0); // reset event counter
            } catch (InterruptedException ex) {
                log.info("interrupted");
                stopme=true;
                break;
            } catch (TimeoutException ex) {
                // didn't exchange within timeout, just add more events since we didn't get exchange request from the consumer this time
            }
        }
        log.info("closing datagramSocket");
        datagramSocket.disconnect();
        datagramSocket.close();
    }

    /** Returns the latest buffer of events. If a timeout occurs occurs a null packet is returned.
     * @return the events collected since the last call to readPacket(), or null on a timeout.
     */
    public AEPacketRaw readPacket() {
        try {
            currentEmptyingBuffer = exchanger.exchange(currentEmptyingBuffer, 1000, TimeUnit.MILLISECONDS);
            if(debugInput &&currentEmptyingBuffer.getNumEvents()>0) System.out.println("exchanged and returning readPacket=" + currentEmptyingBuffer);
            return currentEmptyingBuffer;
        } catch (InterruptedException e) {
            log.info(e.toString());
            return null;
        } catch (TimeoutException to) {
            log.warning("readPacket timed out: " + to.toString());
            return null;
        }
    }

    /** adds to the buffer from a single received datagram */
    private void addToBuffer(AEPacketRaw packet) {
        if (buf == null) {
            buf = new byte[AENetworkInterfaceConstants.DATAGRAM_BUFFER_SIZE_BYTES];
        }
        if (datagram == null) {
            datagram = new DatagramPacket(buf, buf.length);
        }
        if (bis == null) {
            bis = new ByteArrayInputStream(buf);
            dis = new DataInputStream(bis);
        }
        datagram.setLength(buf.length);
        try {
            datagramSocket.receive(datagram); // wait for so_timeout for a datagram
             if (!printedHost) {
                printedHost = true;
                SocketAddress addr = datagram.getSocketAddress();
                log.info("received the first packet from " + addr+" of length "+datagram.getLength()+" bytes, connecting datagram socket now");
                datagramSocket.connect(addr);  // should already be connected by checkHost()
            }
       } catch (SocketTimeoutException to) {
            // just didn't fill the buffer in time, ignore
//            log.warning(to.toString());
            return;
        } catch (IOException e) {
            log.warning(e.toString());
            packet.setNumEvents(0);
            return;
        }
        try {
            if (datagram.getLength() < Integer.SIZE / 8) {
                log.warning(String.format("DatagramPacket only has %d bytes, and thus doesn't even have sequence number, returning empty packet", datagram.getLength()));
                packet.setNumEvents(0);
            }
            packetCounter++;
            int eventSize = use4ByteAddrTs ? AENetworkInterfaceConstants.EVENT_SIZE_BYTES : 4;
            int seqNumLength = sequenceNumberEnabled ? Integer.SIZE / 8 : 0;
            int nEventsInPacket = (datagram.getLength() - seqNumLength) / eventSize;
//            System.out.println(nEventsInPacket + " events");
            dis.reset();
            if (sequenceNumberEnabled) {
                packetSequenceNumber = swab(dis.readInt());
                if (packetSequenceNumber != packetCounter) {
                    log.warning(
                            String.format("Dropped %d packets. (Incoming packet sequence number (%d) doesn't match expected packetCounter (%d), resetting packetCounter to match present incoming sequence number)",
                            (packetSequenceNumber - packetCounter),
                            packetSequenceNumber,
                            packetCounter));
                    packetCounter = packetSequenceNumber;
                }
            }
            // extract the ae data and add events to the packet we are presently filling
            for (int i = 0; i < nEventsInPacket; i++) {
                if (addressFirstEnabled) {
                    if (use4ByteAddrTs) {
                        eventRaw.address = swab(dis.readInt()); // swapInt is switched to handle big endian event sources (like ARC camera)
                        int v = dis.readInt();
                        int v2 = swab(v);
                        int v3 = 0xffff & v2; // TODO hack for TDS sensor which sets bits it shouldn't in the 4 byte timestamp
                        float f = timestampMultiplier * v3;
                        int ts = (int) f;
                        eventRaw.timestamp = ts;
                    } else {
                        eventRaw.address = swab(dis.readShort()); // swapInt is switched to handle big endian event sources (like ARC camera)
                        eventRaw.timestamp = (int) (timestampMultiplier * (int) swab(dis.readShort()));
                    }
                } else {
                    if (use4ByteAddrTs) {
                        eventRaw.timestamp = (int) (swab(dis.readInt()));
                        eventRaw.address = swab(dis.readInt());
                    } else {
                        eventRaw.timestamp = (int) (swab(dis.readShort()));
                        eventRaw.address = (int) (timestampMultiplier * (int) swab(dis.readShort()));
                    }
                }
                packet.addEvent(eventRaw);
            }
        } catch (IOException e) {
            log.warning(e.toString());
            packet.setNumEvents(0);
        }
    }

    @Override
    public String toString() {
        return "AESocketInputStream host=" + host + " at PORT=" + getPort();
    }

    /** Interrupts the thread which is acquiring data and closes the underlying DatagramSocket.
     * 
     */
    synchronized public void close() {
        interrupt();
//        datagramSocket.close(); // should already be closed by thread exit
    }

    public String getHost() {
        return host;
    }

    /** resolves host, returns true if it succeeds */
    private boolean checkHost() {
        if (address != null) {
            return true;
        }
        try {
            address = InetAddress.getByName(host);
            log.info("host " + host + " resolved to " + address);
//            datagramSocket.connect(address, port); // do not connect here, wait till we receive a packet in addToBuffer and then connect to the source address/port
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
        if (datagramSocket == null) {
            return;
        }
        if(port==this.port){
             log.info("port "+port+" is already the bound port for "+this);
             return;
         }
        try {
            datagramSocket.disconnect();
            datagramSocket.bind(new InetSocketAddress(getPort()));
        } catch (Exception e) {
            log.warning("tried to use port="+port+" and got "+e.toString());
        }
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

    public void setSwapBytesEnabled(boolean yes) {
        swapBytesEnabled = yes;
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
        this.timestampMultiplier = timestampMultiplier;
        prefs.putFloat("AEUnicastInput.timestampMultiplier", timestampMultiplier);
    }

    public void set4ByteAddrTimestampEnabled(boolean yes) {
        use4ByteAddrTs = yes;
        prefs.putBoolean("AEUnicastInput.use4ByteAddrTs", yes);
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
