/*
 * AEUnicastDialog.java
 *
 * Created on April 25, 2008, 8:40 AM
 */
package net.sf.jaer.eventio;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import net.sf.jaer.aemonitor.AENetworkRawPacket;
import net.sf.jaer.aemonitor.AEPacket;
import net.sf.jaer.aemonitor.EventRaw;

/**
 * Receives input via datagram (connectionless, UDP) packets from a server.
 * <p>
 * The socket binds to the port which comes initially from the Preferences for
 * AEUnicastInput. The port can be later changed.
 * <p>
 * Each packet consists of (by default)
 *
 * 1. a packet sequence integer (32 bits) which can be used to count missed
 * packets
 *
 * 2. AEs. Each AE is a pair int32 address, int32 timestamp. Timestamps are
 * assumed to have 1us tick.
 * <p>
 * Options allow different choices for use of sequence number, size of
 * address/timestamp, order of address/timestamp, and swapping byte order to
 * account for big/little endian peers.
 *
 * <p>
 * The datagram socket is not connected to the receiver, i.e., connect() is not
 * called on the socket.
 *
 * @see #setAddressFirstEnabled
 * @see #setSequenceNumberEnabled
 
 
 */
public class AEUnicastInput implements AEUnicastSettings, PropertyChangeListener {

    private int NBUFFERS=1000;

    // TODO If the remote host sends 16 bit timestamps, then a local unwrapping is done to extend the time range
    private static Preferences prefs = Preferences.userNodeForPackage(AEUnicastInput.class);
    private DatagramSocket datagramSocket = null;
    private boolean printedHost = false;
    private int port = prefs.getInt("AEUnicastInput.port", AENetworkInterfaceConstants.DATAGRAM_PORT);
    private boolean sequenceNumberEnabled = prefs.getBoolean("AEUnicastInput.sequenceNumberEnabled", true);
    private boolean addressFirstEnabled = prefs.getBoolean("AEUnicastInput.addressFirstEnabled", true);
    private ArrayBlockingQueue<ByteBuffer> exchanger = new ArrayBlockingQueue(NBUFFERS);
    private AENetworkRawPacket packet = new AENetworkRawPacket();
    private static final Logger log = Logger.getLogger("AESocketStream");
    private int bufferSize = prefs.getInt("AEUnicastInput.bufferSize", AENetworkInterfaceConstants.DATAGRAM_BUFFER_SIZE_BYTES);
    private boolean swapBytesEnabled = prefs.getBoolean("AEUnicastInput.swapBytesEnabled", false);
    private float timestampMultiplier = prefs.getFloat("AEUnicastInput.timestampMultiplier", DEFAULT_TIMESTAMP_MULTIPLIER);
    private boolean use4ByteAddrTs = prefs.getBoolean("AEUnicastInput.use4ByteAddrTs", DEFAULT_USE_4_BYTE_ADDR_AND_TIMESTAMP);
    private boolean localTimestampsEnabled = prefs.getBoolean("AEUnicastOutput.localTimestampsEnabled", false);
    boolean stopme = false;
    private DatagramChannel channel;
    private int datagramCounter = 0;
    private int datagramSequenceNumber = 0;
    private EventRaw eventRaw = new EventRaw();
    private int timeZero = 0; // used to store initial timestamp for 4 byte timestamp reads to subtract this value
    private boolean readTimeZeroAlready = false;
    private boolean timestampsEnabled = prefs.getBoolean("AEUnicastInput.timestampsEnabled", DEFAULT_TIMESTAMPS_ENABLED);
    private Semaphore pauseSemaphore = new Semaphore(1);
    private volatile boolean paused = false;
    private Reader readingThread = null;
 
    /**
     * Constructs an instance of AEUnicastInput and binds it to the default
     * port. The port preference value may have been modified from the
     * Preferences default by a previous setPort() call which stored the
     * preference value.
     * <p>
     * This Thread subclass must be started in order to receive event packets.
     *
     * @see AENetworkInterfaceConstants
     *
     */
    public AEUnicastInput() { // TODO basic problem here is that if port is unavailable, then we cannot construct and set port
    }

    /**
     * Constructs a new AEUnicastInput using the given port number.
     *
     * @param port the UDP port number.
     * @see AEUnicastInput#AEUnicastInput()
     */
    public AEUnicastInput(int port) {
        this();
        setPort(port);
    }

    private int eventSize() {
        if (use4ByteAddrTs) {
            if (timestampsEnabled) {
                return AENetworkInterfaceConstants.EVENT_SIZE_BYTES;
            } else {
                return 4;
            }
        } else {
            if (timestampsEnabled) {
                return 4;
            } else {
                return 2;
            }
        }
    }

    /**
     * Returns the latest buffer of events. If a timeout occurs occurs a null
     * packet is returned.
     *
     * @return the events collected since the last call to readPacket(), or null
     * on a timeout or interrupt.
     */
    public AENetworkRawPacket readPacket() {
        packet.clear();
        readingThread.maxSizeExceeded = false;
        try {
            while (exchanger.peek() != null) {
                ByteBuffer buffer = exchanger.take();
                extractEvents(buffer, packet);
             }
            return packet;
        } catch (InterruptedException e) {
            log.info("Interrupted exchange of buffers in AEUnicastInput: " + e.toString());
            return null;
        }
    }

    private void checkSequenceNumber(ByteBuffer buffer) {
        if (sequenceNumberEnabled) {
            datagramSequenceNumber = buffer.getInt(); // swab(buffer.getInt());
//                log.info("recieved packet with sequence number "+packetSequenceNumber);
            if (datagramSequenceNumber != datagramCounter) {
                log.warning(String.format("Dropped %d packets. (Incoming packet sequence number (%d) doesn't match expected packetCounter (%d), resetting packetCounter)", datagramSequenceNumber - datagramCounter, datagramSequenceNumber, datagramCounter));
                datagramCounter = datagramSequenceNumber;
            }
            datagramCounter++;
        }
    }

    /**
     * Receives a buffer from the UDP socket. Data is stored in internal buffer.
     *
     * @param packet used to set number of events to 0 if there is an
     * error and to store the source host(s) information.
     * @return client if successful, null if there is an IOException.
     */
    private SocketAddress receiveDatagramAndPutToExchanger(AENetworkRawPacket packet) {
        SocketAddress client = null;
        try {
            ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
            buffer.order(swapBytesEnabled ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);

            client = channel.receive(buffer); // fill buffer with data from datagram, blocks here until packet received
            if (!printedHost) {
                printedHost = true;
                log.info("received first packet from " + client + " of length " + buffer.position() + " bytes"); // , connecting channel
                // do not connect so that multiple clients can send us data on the same port
                //                channel.connect(client); // TODO connect the channel to avoid security check overhead. TODO check if handles reconnects correctly.
            }
            if (client instanceof InetSocketAddress) {
                packet.addClientAddress((InetSocketAddress) client, packet.getNumEvents());
            } else {
                if (client == null) {
                    paused = true;
                    log.warning("Device not connected or wrong configured. Datagrams have to be sent to port: " + port + " .Input stream paused.");
                } else {
                    log.warning("unknown type of client address - should be InetSocketAddress: " + client);
                }
            }
            buffer.flip();
            checkSequenceNumber(buffer);
            exchanger.put(buffer); // blocks here until readPacket clears the packet
        }catch(InterruptedException ie){
            log.warning(ie.toString());
            return null;
        } catch (SocketTimeoutException to) {
            // just didn't fill the buffer in time, ignore
            log.warning(to.toString());
            return null;
        } catch (IOException e) {
            log.warning(e.toString());
            packet.clear();
            return null;
        } catch (IllegalArgumentException eArg) {
            log.warning(eArg.toString());
            return null;
        }
        return client;
    }


    /**
     * Extracts data from internal buffer and adds to packet, according to all
     * the option flags.
     *
     * @param packet to add events to.
     */
    private void extractEvents(ByteBuffer buffer, AENetworkRawPacket packet) {
        // extract the ae data and add events to the packet we are presently filling
        int seqNumLength = sequenceNumberEnabled ? Integer.SIZE / 8 : 0;
        int eventSize = eventSize();
        //log.info("event size " + eventSize);
        int nEventsInPacket = (buffer.limit() - seqNumLength) / eventSize;
        //log.info("nr of events " + nEventsInPacket);
        //deprecated ... int ts = !timestampsEnabled || localTimestampsEnabled ? (int)( System.nanoTime() / 1000 ) : 0; // if no timestamps coming, add system clock for all.
        int ts = !timestampsEnabled || localTimestampsEnabled ? (int) (((System.nanoTime() / 1000) << 32) >> 32) : 0; // if no timestamps coming, add system clock for all.
        //log.info("nanoTime shift " +(int)( ((System.nanoTime()/1000) <<32)>>32));
        final int startingIndex = packet.getNumEvents();
        final int newPacketLength = startingIndex + nEventsInPacket;
        packet.ensureCapacity(newPacketLength);
        final int[] addresses = packet.getAddresses();
        final int[] timestamps = packet.getTimestamps();

        for (int i = 0; i < nEventsInPacket; i++) {
            if (addressFirstEnabled) {
                if (use4ByteAddrTs) {
                    eventRaw.address = buffer.getInt(); // swab(buffer.getInt()); // swapInt is switched to handle big endian event sources (like ARC camera)
                    //log.info("address " + eventRaw.address);
                    //int v=buffer.getInt();
                    // if timestamps are enabled, they have to be read out even if they are not used because of local timestamps
                    if (timestampsEnabled && !localTimestampsEnabled) {
                        int rawTime = buffer.getInt(); //swab(v);
                        //                  if(rawTime<lastts) {
                        //                      System.out.println("backwards timestamp at event "+i+"of "+nEventsInPacket);
                        //                  }else if(rawTime!=lastts){
                        //                      System.out.println("time jump at event "+i+"of "+nEventsInPacket);
                        //                  }
                        //                  lastts=rawTime;
                        int zeroedRawTime;
                        if (readTimeZeroAlready) {
                            // TODO TDS sends 32 bit timestamp which overflows after multiplication
                            // by timestampMultiplier and cast to int jaer timestamp
                            zeroedRawTime = rawTime - timeZero;
                        } else {
                            readTimeZeroAlready = true;
                            timeZero = rawTime;
                            zeroedRawTime = 0;
                        }
//                        int v3 = 0xffff & v2; // TODO hack for TDS sensor which uses all 32 bits causing overflow after multiplication by multiplier and int cast
                        float floatFinalTime = timestampMultiplier * zeroedRawTime;
                        int finalTime;
                        if ((floatFinalTime >= Integer.MAX_VALUE) || (floatFinalTime <= Integer.MIN_VALUE)) {
                            timeZero = rawTime; // after overflow reset timezero
                            finalTime = Integer.MIN_VALUE + (int) (floatFinalTime - Integer.MAX_VALUE); // Change to -2k seconds now - was: wrap around at 2k seconds, back to 0 seconds. TODO different than hardware which wraps back to -2k seconds
                        } else {
                            finalTime = (int) floatFinalTime;
                        }
                        eventRaw.timestamp = finalTime;
                    } else {
                        //SmartEyeTDS
                        eventRaw.timestamp = ts;
                        //read out buffer with timestamp
                        buffer.getInt();
                        //log.info("local timestamp " + ts);
                    }
                } else {
                    eventRaw.address = buffer.getShort() & 0xffff; // swab(buffer.getShort()); // swapInt is switched to handle big endian event sources (like ARC camera)
//                    eventRaw.timestamp=(int) (timestampMultiplier*(int) swab(buffer.getShort()));
                    if (!localTimestampsEnabled && timestampsEnabled) {
                        eventRaw.timestamp = (int) (timestampMultiplier * buffer.getShort());
                    } else {
                        eventRaw.timestamp = ts;
                    }
                }
            } else {
                if (use4ByteAddrTs) {
//                    eventRaw.timestamp=(int) (swab(buffer.getInt()));
//                    eventRaw.address=swab(buffer.getInt());
                    if (!localTimestampsEnabled && timestampsEnabled) {
                        eventRaw.timestamp = ((buffer.getInt()));
                    } else {
                        eventRaw.timestamp = ts;
                    }
                    eventRaw.address = (buffer.getInt());
                } else {
//                    eventRaw.timestamp=(int) (swab(buffer.getShort()));
//                    eventRaw.address=(int) (timestampMultiplier*(int) swab(buffer.getShort()));
                    if (!localTimestampsEnabled && timestampsEnabled) {
                        eventRaw.timestamp = ((buffer.getShort()));  // TODO check if need AND with 0xffff to avoid negative timestamps
                    } else {
                        eventRaw.timestamp = ts;
                    }
                    eventRaw.address = (int) (timestampMultiplier * (buffer.getShort() & 0xffff));
                }
            }

            // alternative is to directly add to arrays of packet for speed, to bypass the capacity checking
//            packet.addEvent(eventRaw);
            addresses[startingIndex + i] = eventRaw.address;
            timestamps[startingIndex + i] = eventRaw.timestamp;

        }
        packet.setNumEvents(newPacketLength);

    }

    @Override
    public String toString() {
        return "AEUnicastInput at PORT=" + getPort();
    }

    /**
     * Interrupts the thread which is acquiring data and closes the underlying
     * DatagramSocket.
     *
     */
    @Override
    public void close() {
        if ((channel != null) && channel.isOpen()) {
            try {
                stopme = true;
                channel.close();
                datagramSocket.close();
            } catch (IOException ex) {
                log.warning("on closing DatagramChannel caught " + ex);
            }
        }
    }

    /**
     * @return "localhost".
     */
    @Override
    public String getHost() {
        return "localhost";
    }

    private void cleanup() {
        try {
            datagramSocket.close();
        } catch (Exception e) {
            log.warning("on closing caught " + e);
        }
    }

    /**
     * resolves host, builds socket, returns true if it succeeds
     */
    private boolean checkSocket() {
        if ((channel != null) && channel.isOpen()) { //if(datagramSocket!=null && datagramSocket.isBound()) {
            return true;
        }
        try {
            channel = DatagramChannel.open();
            datagramSocket = channel.socket();
            datagramSocket.setReuseAddress(true);
            // disable timeout so that receive just waits for data forever (until interrupted)
//            datagramSocket.setSoTimeout(TIMEOUT_MS);
//            if (datagramSocket.getSoTimeout() != TIMEOUT_MS) {
//                log.warning("datagram socket read timeout value read=" + datagramSocket.getSoTimeout() + " which is different than timeout value of " + TIMEOUT_MS + " that we tried to set - perhaps timeout is not supported?");
//            }
            SocketAddress address = new InetSocketAddress(getPort());
            datagramSocket.bind(address);
            log.info("bound " + this);
            datagramSocket.setSoTimeout(0); // infinite timeout
            return true;
        } catch (IOException e) {
            log.warning("caught " + e + ", datagramSocket will be constructed later");
            return false;
        }
    }

    /**
     * Opens the input. Binds the port and starts the background receiver
     * thread.
     *
     * @throws java.io.IOException
     */
    @Override
    public void open() throws IOException {  // TODO cannot really throw exception because socket is opened in Reader
        close();
        readingThread = new Reader();
        readingThread.start();
    }

    /**
     * @param host the hostname
     * @deprecated doesn't do anything here because we only set local port
     */
    @Deprecated
    @Override
    public void setHost(String host) {
        // TODO all wrong, doesn't need host since receiver
        log.log(Level.WARNING, "setHost({0}) ignored for AEUnicastInput", host);
    }

    @Override
    public int getPort() {
        return port;
    }

    /**
     * Set the local port number for receiving events.
     *
     * @param port
     */
    @Override
    public void setPort(int port) { // TODO all wrong
        this.port = port;
        prefs.putInt("AEUnicastInput.port", port);
        if (port == this.port) {
            log.info("port " + port + " is already the bound port for " + this);
            return;
        }
        readTimeZeroAlready = false;
    }

    @Override
    public boolean isSequenceNumberEnabled() {
        return sequenceNumberEnabled;
    }

    /**
     * If set true (default), then an int32 sequence number is the first word of
     * the packet. Otherwise the first int32 is part of the first AE.
     *
     * @param sequenceNumberEnabled default true
     */
    @Override
    public void setSequenceNumberEnabled(boolean sequenceNumberEnabled) {
        this.sequenceNumberEnabled = sequenceNumberEnabled;
        prefs.putBoolean("AEUnicastInput.sequenceNumberEnabled", sequenceNumberEnabled);
    }

    /**
     * @see #setAddressFirstEnabled
     */
    @Override
    public boolean isAddressFirstEnabled() {
        return addressFirstEnabled;
    }

    /**
     * If set true, the first int32 of each AE is the address, and the second is
     * the timestamp. If false, the first int32 is the timestamp, and the second
     * is the address. This parameter is stored as a preference.
     *
     * @param addressFirstEnabled default true.
     */
    @Override
    public void setAddressFirstEnabled(boolean addressFirstEnabled) {
        this.addressFirstEnabled = addressFirstEnabled;
        prefs.putBoolean("AEUnicastInput.addressFirstEnabled", addressFirstEnabled);
    }

    /**
     * Java is big endian but intel native is little endian. If
     * setSwapBytesEnabled(true), then the bytes are swapped. Use false for java
     * to java transfers, or for java/native transfers where the native side
     * does not swap the data.
     *
     * @param yes true to swap big/little endian address and timestamp data.
     */
    @Override
    public void setSwapBytesEnabled(boolean yes) {
        swapBytesEnabled = yes;
        prefs.putBoolean("AEUnicastInput.swapBytesEnabled", swapBytesEnabled);
    }

    @Override
    public boolean isSwapBytesEnabled() {
        return swapBytesEnabled;
    }

    @Override
    public float getTimestampMultiplier() {
        return timestampMultiplier;
    }

    /**
     * Timestamps from the remote host are multiplied by this value to become
     * jAER timestamps. If the remote host uses 1 ms timestamps, set timestamp
     * multiplier to 1000.
     *
     * @param timestampMultiplier
     */
    @Override
    public void setTimestampMultiplier(float timestampMultiplier) {
        this.timestampMultiplier = timestampMultiplier;
        prefs.putFloat("AEUnicastInput.timestampMultiplier", timestampMultiplier);
    }

    // TODO javadoc
    @Override
    public void set4ByteAddrTimestampEnabled(boolean yes) {
        use4ByteAddrTs = yes;
        prefs.putBoolean("AEUnicastInput.use4ByteAddrTs", yes);
    }

    @Override
    public boolean is4ByteAddrTimestampEnabled() {
        return use4ByteAddrTs;
    }

    @Override
    public void setLocalTimestampEnabled(boolean yes) {
        localTimestampsEnabled = yes;
        prefs.putBoolean("AEUnicastOutput.localTimestampsEnabled", localTimestampsEnabled);
    }

    @Override
    public boolean isLocalTimestampEnabled() {
        return localTimestampsEnabled;
    }

    /**
     * Returns the desired buffer size for datagrams in bytes.
     *
     * @return the bufferSize in bytes.
     */
    @Override
    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * Sets the maximum datagram size that can be received in bytes. It is much
     * faster to use small (e.g. 8k) packets than large ones to minimize CPU
     * usage.
     *
     * @param bufferSize the bufferSize to set in bytes.
     */
    @Override
    synchronized public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
        prefs.putInt("AEUnicastInput.bufferSize", bufferSize);
    }

    @Override
    public boolean isTimestampsEnabled() {
        return timestampsEnabled;
    }

    /**
     * If timestamps are enabled, then they are parsed from the input stream. If
     * disabled, timestamps are added using the System.nanoTime()/1000 system
     * clock for all events in the packet.
     *
     * @param yes true to enable parsing of timestamps.
     */
    @Override
    public void setTimestampsEnabled(boolean yes) {
        timestampsEnabled = yes;
        prefs.putBoolean("AEUnicastInput.timestampsEnabled", yes);
    }

    @Override
    public void setPaused(boolean yes) {
        paused = yes;
        readingThread.interrupt();
        // following deadlocks with exchanger
//        if ( yes ){
//            try{
//                pauseSemaphore.acquire();
//            } catch ( InterruptedException ex ){
//                log.info(ex.toString());
//            }
//        } else{
//            pauseSemaphore.release();
//        }
    }

    @Override
    public boolean isPaused() {
        return paused;
//        return pauseSemaphore.availablePermits() == 0;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
//        log.info(evt.toString());
//        if ( evt.getPropertyName().equals("paused") ){
//            setPaused((Boolean)evt.getNewValue());
//        }
    }

    private class Reader extends Thread {

        volatile boolean maxSizeExceeded = false;

        /**
         * Bumps priority and names thread
         */
        public Reader() {
            super("AEUnicastInput.Reader");
            setPriority(Thread.NORM_PRIORITY + 4);
        }

        /**
         * This run method loops forever, filling the current filling buffer so
         * that readPacket can return data that may be processed while the other
         * buffer is being filled.
         */
        @Override
        public void run() {
            while (!stopme) {
                if (!checkSocket()) {
                    // if port cannot be bound, just try again in a bit
                    try {
                        Thread.sleep(1000);
                    } catch (Exception e) {
                        log.warning("tried to bind " + this + " but got " + e);
                    }
                    continue;
                }
                if (packet.getNumEvents() >= AEPacket.MAX_PACKET_SIZE_EVENTS) {
                    if (!maxSizeExceeded) {
                        log.warning("packet " + packet + " has more than " + AEPacket.MAX_PACKET_SIZE_EVENTS + " disabling filling until packet is read");
                        maxSizeExceeded = true;
                    }
                }
                // recieve datagrams and put them to exchanger
                if (!maxSizeExceeded && !paused) { // if paused, don't overrun memory
                    try {
                        receiveDatagramAndPutToExchanger(packet); // also save source hosts to packet
                    } catch (NullPointerException e) {
                        log.warning(e.toString());
                        break;
                    }
                }
            }
            log.info("closing datagramSocket");
            cleanup();
        }
    };
}
