/*
 * AEUnicastDialog.java
 *
 * Created on April 25, 2008, 8:40 AM
 */
package net.sf.jaer.eventio;

import com.jogamp.opengl.util.GLDrawableUtil;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import net.sf.jaer.aemonitor.AENetworkRawPacket;
import net.sf.jaer.aemonitor.AEPacket;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.aemonitor.EventRaw;
import net.sf.jaer.aemonitor.EventRaw.EventType;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.EventExtractor2D;
import net.sf.jaer.event.EventPacket;
import static net.sf.jaer.eventio.AEFileInputStream.MAX_BUFFER_SIZE_EVENTS;
import net.sf.jaer.eventio.Jaer3BufferParser;
import net.sf.jaer.eventio.Jaer3BufferParser.PacketDescriptor;
import net.sf.jaer.eventio.Jaer3BufferParser.Jaer3EventExtractor;

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
 *
 *
 */
public class AEUnicastInput implements AEUnicastSettings, PropertyChangeListener {

    private int NBUFFERS = 100; // should match somehow the expected number of datagrams that come in a burst before the readPacket() method is called.

    // TODO If the remote host sends 16 bit timestamps, then a local unwrapping is done to extend the time range
    private static Preferences prefs = Preferences.userNodeForPackage(AEUnicastInput.class);
    private DatagramSocket datagramSocket = null;
    private boolean printedHost = false;
    private int port = prefs.getInt("AEUnicastInput.port", AENetworkInterfaceConstants.DATAGRAM_PORT);
    private boolean sequenceNumberEnabled = prefs.getBoolean("AEUnicastInput.sequenceNumberEnabled", true);
    private boolean cAERStreamEnabled = prefs.getBoolean("AEUnicastInput.cAERDisplayEnabled", true);
    private boolean addressFirstEnabled = prefs.getBoolean("AEUnicastInput.addressFirstEnabled", true);
    private ArrayBlockingQueue<ByteBuffer> filledBufferQueue = new ArrayBlockingQueue(NBUFFERS), availableBufferQueue = new ArrayBlockingQueue(NBUFFERS);
    private AENetworkRawPacket packet = new AENetworkRawPacket();
    private static final Logger log = Logger.getLogger("AESocketStream");
    private int bufferSize = prefs.getInt("AEUnicastInput.bufferSize", AENetworkInterfaceConstants.DATAGRAM_BUFFER_SIZE_BYTES);
    private boolean swapBytesEnabled = prefs.getBoolean("AEUnicastInput.swapBytesEnabled", false);
    private float timestampMultiplier = prefs.getFloat("AEUnicastInput.timestampMultiplier", DEFAULT_TIMESTAMP_MULTIPLIER);
    private boolean use4ByteAddrTs = prefs.getBoolean("AEUnicastInput.use4ByteAddrTs", DEFAULT_USE_4_BYTE_ADDR_AND_TIMESTAMP);
    private boolean localTimestampsEnabled = prefs.getBoolean("AEUnicastOutput.localTimestampsEnabled", false);
    private boolean spinnakerProtocolEnabled = prefs.getBoolean("AEUnicastInput.spinnakerProtocolEnabled", false);
    private boolean secDvsProtocolEnabled = prefs.getBoolean("AEUnicastInput.secDvsProtocolEnabled", false);
    boolean stopme = false;
    private DatagramChannel channel;
    private int datagramCounter = 0;
    private int datagramSequenceNumber = 0;
    private EventRaw eventRaw = new EventRaw();
    private int timeZero = 0; // used to store initial timestamp for 4 byte timestamp reads to subtract this value
    private boolean readTimeZeroAlready = true;
    private boolean timestampsEnabled = prefs.getBoolean("AEUnicastInput.timestampsEnabled", DEFAULT_TIMESTAMPS_ENABLED);
    private Semaphore pauseSemaphore = new Semaphore(1);
    private volatile boolean paused = false;
    private Reader readingThread = null;
    private AEChip chip = null; // needed to support cAER jaer3.0 decoding to jAER format
    private EventExtractor2D restoreEventExtractor = null; // The restore extractor
    private ByteBuffer wholePktBuffer = null;
    private int jaer3PktSize = 0, jaer3PktNum = 0;
    private long jaer3EventsNum = 0;
    private Jaer3BufferParser j3Parser;
    private int secGen2TimestampMSB = 0;
    private int secGen2TimestampLSB = 0;

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
    public AEUnicastInput(AEChip chip) { // TODO basic problem here is that if port is unavailable, then we cannot construct and set port
        this.chip = chip;
        restoreEventExtractor = chip.getEventExtractor();
    }

    /**
     * Constructs a new AEUnicastInput using the given port number.
     *
     * @param port the UDP port number.
     * @see AEUnicastInput#AEUnicastInput()
     */
    public AEUnicastInput(int port, AEChip chip) {
        this(chip);
        setPort(port);
    }

    private void allocateBufffers() {
        availableBufferQueue.clear();
        for (int i = 0; i < NBUFFERS; i++) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
            buffer.order(swapBytesEnabled || spinnakerProtocolEnabled || secDvsProtocolEnabled ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN); //spinnaker always uses little endian
            availableBufferQueue.add(buffer);
        }
        filledBufferQueue.clear();
    }

    private void freeBuffers() {
        availableBufferQueue.clear();
        filledBufferQueue.clear(); // allow GC to collect these references
    }

    private int eventSize() {
        if (use4ByteAddrTs) {
            if (timestampsEnabled) {
                return AENetworkInterfaceConstants.EVENT_SIZE_BYTES;
            } else {
                return 4;
            }
        } else if (timestampsEnabled) {
            return 4;
        } else {
            return 2;
        }
    }

    /**
     * Returns the latest buffer of events. If a timeout occurs occurs a null
     * packet is returned.
     *
     * @return the events collected since the last call to readPacket(), or null
     * on a timeout or interrupt.
     */
    int nTmp = 0;
    int nEventCapacity = 0;

    public AENetworkRawPacket readPacket() {
        packet.clear();
        readingThread.maxSizeExceeded = false;
        try {
            returnearly:
            while (filledBufferQueue.peek() != null) {
                ByteBuffer buffer = filledBufferQueue.take();
                // buffer.clear();
                extractEvents(buffer, packet);
                buffer.clear();
                availableBufferQueue.put(buffer);
                if (packet.getNumEvents() >= 10000) {
                    break returnearly; // Set a threshold to avoid the big dealy caused by accumulating too many events in the packet.
                }
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
     * @param packet used to set number of events to 0 if there is an error and
     * to store the source host(s) information.
     * @return client if successful, null if there is an IOException.
     */
    private SocketAddress receiveDatagramAndPutToExchanger(AENetworkRawPacket packet) {
        SocketAddress client = null;
        try {
            ByteBuffer buffer = availableBufferQueue.take(); // buffer must be cleared by readPacket
//            ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
//            buffer.order(swapBytesEnabled ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);

            client = channel.receive(buffer); // fill buffer with data from datagram, blocks here until packet received
            if (!printedHost) {
                printedHost = true;
                log.info("received first packet from " + client + " of length " + buffer.position() + " bytes"); // , connecting channel
                // do not connect so that multiple clients can send us data on the same port
                //                channel.connect(client); // TODO connect the channel to avoid security check overhead. TODO check if handles reconnects correctly.
            }
            if (client instanceof InetSocketAddress) {
                packet.addClientAddress((InetSocketAddress) client, packet.getNumEvents());
            } else if (client == null) {
                paused = true;
                log.warning("Device not connected or wrong configured. Datagrams have to be sent to port: " + port + " .Input stream paused.");
            } else {
                log.warning("unknown type of client address - should be InetSocketAddress: " + client);
            }
            buffer.flip();
            if (!spinnakerProtocolEnabled && !secDvsProtocolEnabled) {
                checkSequenceNumber(buffer);
            }
//            if(exchanger.size()>10){
//                log.info("filled queue of datagrams has "+exchanger.size()+" buffers");
//            }
            filledBufferQueue.put(buffer); // blocks here until readPacket clears the packet
        } catch (InterruptedException ie) {
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

    private int maybeSwapByteOrder(int value) {
        if (!swapBytesEnabled) {
            return value;
        }
        int b1 = (value >> 0) & 0xff;
        int b2 = (value >> 8) & 0xff;
        int b3 = (value >> 16) & 0xff;
        int b4 = (value >> 24) & 0xff;

        return b1 << 24 | b2 << 16 | b3 << 8 | b4 << 0;
    }

    private short maybeSwapByteOrder(short value) {
        if (!swapBytesEnabled) {
            return value;
        }
        int b1 = (value >> 0) & 0xff;
        int b2 = (value >> 8) & 0xff;

        return (short) ((0xffff & (b1 << 8) | (0xffff & (b2 << 0))));
    }

    private static ByteBuffer clone(ByteBuffer original) {
        ByteBuffer clone = ByteBuffer.allocate(original.capacity());
        if (clone.capacity() < 90000) { // The max length of a whole frame packet is 86464, so the capacity must be bigger than 86464, here we use 90000 for convience. 
            clone = ByteBuffer.allocate(90000);
        }
        original.rewind(); //copy from the beginning
        clone.put(original);
        original.rewind();
        // clone.flip(); // We still need to put data in the buffer, we will flip it in the end buffer, so we don't need flip at the head buffer.
        return clone;
    }

    /**
     * Extracts data from internal buffer and adds to packet, according to all
     * the option flags.
     *
     * @param packet to add events to.
     */
    private void extractEvents(ByteBuffer buffer, AENetworkRawPacket packet) {

        if (isSpinnakerProtocolEnabled()) {
            // Read header
            byte byte0 = buffer.get();
            if ((byte0 & 0b10000000) != 0) {
                throw new UnsupportedOperationException("Command packets not supported yet.");
            }
            if (use4ByteAddrTs != ((byte0 & 0b01000000) != 0)) {
                throw new IllegalStateException("use4ByteAddrTs conflict btw header and user setting.");
            }
            if ((!timestampsEnabled || localTimestampsEnabled) != ((byte0 & 0b00100000) == 0)) {
                throw new IllegalStateException("timestamp enabling conflict btw header and user setting.");
            }
            //we ignore payloads, but need their sizes to skip them
            int payload_size; //payload size in bytes
            {
                boolean bit1, bit0;
                bit1 = ((byte0 & 0b00010000) != 0);
                bit0 = ((byte0 & 0b00001000) != 0);
                if (!bit0 && !bit1) {
                    payload_size = 0; //no payload
                } else if (bit0 && !bit1) {
                    payload_size = 2; //16bit
                } else if (!bit0 && bit1) {
                    payload_size = 4; //32bit
                } else {
                    payload_size = 16; //128bit
                }
            }
            //if(payload_size != 0) throw new UnsupportedOperationException("Payloads not supported.");
            //key prefix
            if ((byte0 & 0b00000100) != 0) {
                throw new UnsupportedOperationException("Key prefixes are not supported.");
            }
            //timestamp prefix
            if ((byte0 & 0b00000010) != 0) {
                throw new UnsupportedOperationException("Timestamp prefixes are not supported.");
            }
            //payload prefix
            if ((byte0 & 0b00000001) != 0) {
                throw new UnsupportedOperationException("Payload prefixes are not supported.");
            }

            //read number of events
            int nEventsInPacket = buffer.get() & 0xff;
            int eventSize = (2 * (use4ByteAddrTs ? 4 : 2)) + payload_size;
            int computednEventsInPacket = (buffer.limit() - 4) / eventSize;
            if (computednEventsInPacket != nEventsInPacket) {
                nEventsInPacket = Math.min(computednEventsInPacket, nEventsInPacket);
                log.warning("Mismatch between number of events claimed by header and the computed one from packet size. Using smallest one.");
            }

            //packet counter
            int packetNumber = buffer.get() & 0xff;
            if (sequenceNumberEnabled) {
                datagramSequenceNumber = packetNumber; // swab(buffer.getInt());
                //                log.info("recieved packet with sequence number "+packetSequenceNumber);
                if (datagramSequenceNumber != datagramCounter) {
                    log.warning(String.format("Dropped %d packets. (Incoming packet sequence number (%d) doesn't match expected packetCounter (%d), resetting packetCounter)", datagramSequenceNumber - datagramCounter, datagramSequenceNumber, datagramCounter));
                    datagramCounter = datagramSequenceNumber;
                }
                if (datagramCounter < 255) {
                    datagramCounter++;
                } else {
                    datagramCounter = 0;
                }
            }

            //Reserved byte, just ignore it
            buffer.get();

            //read events
            int ts = !timestampsEnabled || localTimestampsEnabled ? (int) (((System.nanoTime() / 1000) << 32) >> 32) : 0; // if no timestamps coming, add system clock for all.
            final int startingIndex = packet.getNumEvents();
            final int newPacketLength = startingIndex + nEventsInPacket;
            packet.ensureCapacity(newPacketLength);
            final int[] addresses = packet.getAddresses();
            final int[] timestamps = packet.getTimestamps();

            for (int i = 0; i < nEventsInPacket; i++) {
                if (use4ByteAddrTs) {
                    eventRaw.address = buffer.getInt();

                    //timestamps, only if enabled and non local
                    if (timestampsEnabled && !localTimestampsEnabled) {
                        int rawTime = buffer.getInt();

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
                        float floatFinalTime = timestampMultiplier * zeroedRawTime;
                        int finalTime;
                        if ((floatFinalTime >= Integer.MAX_VALUE) || (floatFinalTime <= Integer.MIN_VALUE)) {
                            timeZero = rawTime; // after overflow reset timezero
                            finalTime = Integer.MIN_VALUE + (int) (floatFinalTime - Integer.MAX_VALUE); // Change to -2k seconds now - was: wrap around at 2k seconds, back to 0 seconds. TODO different than hardware which wraps back to -2k seconds
                        } else {
                            finalTime = (int) floatFinalTime;
                        }
                        eventRaw.timestamp = finalTime;
                    } else { //ignore remote timestamp
                        eventRaw.timestamp = ts;
                        buffer.getInt();
                        //log.info("local timestamp " + ts);
                    }
                } else {
                    eventRaw.address = buffer.getShort();

//                    eventRaw.timestamp=(int) (timestampMultiplier*(int) swab(buffer.getShort()));
                    if (!localTimestampsEnabled && timestampsEnabled) {
                        eventRaw.timestamp = (int) (timestampMultiplier * buffer.getShort());
                    } else {
                        eventRaw.timestamp = ts;
                    }
                }

                //ignore payload
                for (int tmpi = 0; tmpi < payload_size; tmpi++) {
                    buffer.get();
                }

                // alternative is to directly add to arrays of packet for speed, to bypass the capacity checking
                //            packet.addEvent(eventRaw);
                addresses[startingIndex + i] = eventRaw.address;
                timestamps[startingIndex + i] = eventRaw.timestamp;
            }
            packet.setNumEvents(newPacketLength);
        }
        else if( isSecDvsProtocolEnabled() )
        {
            int nEventsInPacket = (buffer.limit() ) / 4;
            final int startingIndex = packet.getNumEvents();
            int newPacketLength = startingIndex + nEventsInPacket;
            packet.ensureCapacity(newPacketLength*8);
            int[] addresses = packet.getAddresses();
            int[] timestamps = packet.getTimestamps();
            
            int eventcounter=0;
            int column=-1;
            //for (int i = 0; i < nEventsInPacket; i++) 
            while(buffer.hasRemaining())
            {
                //get 4 byte word
                byte byte0 = buffer.get();
                byte byte1 = buffer.get();
                byte byte2 = buffer.get();
                byte byte3 = buffer.get();
                
                if(byte0==102)
                {
                    //int timestamp = 0;
                }
                else
                {
                    //interpretPstartGen2
                    if((byte0&0xff)==153)//start address packet
                    {
              		column = (byte3&0xff) + ((byte2 & 0x3) << 8);
        		//		timestamp = ((w3 & 252) >> 2) + (w2 << 6);
                	int timestamp = ((byte1 & 0xf) << 6) + ((byte2&0xff) >> 2);
                        if(timestamp < secGen2TimestampLSB)
                        {
                            secGen2TimestampMSB = secGen2TimestampMSB+1;
                        }
                        secGen2TimestampLSB = timestamp;
                    }
                    else if(column>-1)
                    {
                        if((byte0&0xff)==204)
                        {
                            int base_row = ( (byte1&0xff) & 0x3f) * 8 + 1;// bitand(word(mapp(2)), 63) * 8 + 1;
                            for (int ii = 0; ii < 8; ii++)
                            {
                                if (((byte3&0xff)&(1 << ii)) > 0)
                                {
                                    int row = base_row + ii;
                                    row = 480-row;
                                    int polarity = 1;
                                    int address = row<<11;
                                    address = address + (column<<1);
                                    address = address + polarity;
                                    
                                    if(row>=0 && row<480 && column>=0 && column<640)
                                    {
                                        addresses[startingIndex + eventcounter] = address;
                                        timestamps[startingIndex + eventcounter] = secGen2TimestampMSB*1000 + secGen2TimestampLSB;
                                        eventcounter=eventcounter+1;
                                    }

                                }
                                if (((byte2&0xff)&(1 << ii)) > 0)
                                {
                                    int row = base_row + ii;
                                    row = 480-row;
                                    int polarity = 0;
                                    int address = row<<11;
                                    address = address + (column<<1);
                                    address = address + polarity;
                                    
                                    if(row>=0 && row<480 && column>=0 && column<640)
                                    {
                                        addresses[startingIndex + eventcounter] = address;
                                        timestamps[startingIndex + eventcounter] = secGen2TimestampMSB*1000 + secGen2TimestampLSB;                                        
                                        eventcounter=eventcounter+1;
                                    }
                                }

                            }
                        
                        }
                        else {
                            // In this case, the buffer is misaligned with the 4-byte packet format; 
                            // Drop a byte and try again
                            byte dontCare = buffer.get();
                        }
                    }
                }
                
            }
            packet.setNumEvents(startingIndex + eventcounter);
            
        }
        else { // normal jAER/cAER packet
            // extract the ae data and add events to the packet we are presently filling
            int seqNumLength = sequenceNumberEnabled ? Integer.SIZE / 8 : 0;
            int eventSize = eventSize();
            //log.info("event size " + eventSize);
            int nEventsInPacket = (buffer.limit() - seqNumLength - (cAERStreamEnabled ? 28 : 0)) / eventSize;    // 28 is the byte numbers of cAER Header
            //log.info("nr of events " + nEventsInPacket);
            //deprecated ... int ts = !timestampsEnabled || localTimestampsEnabled ? (int)( System.nanoTime() / 1000 ) : 0; // if no timestamps coming, add system clock for all.
            int ts = !timestampsEnabled || localTimestampsEnabled ? (int) (((System.nanoTime() / 1000) << 32) >> 32) : 0; // if no timestamps coming, add system clock for all.
            //log.info("nanoTime shift " +(int)( ((System.nanoTime()/1000) <<32)>>32));
            final int startingIndex = packet.getNumEvents();
            int newPacketLength = startingIndex + nEventsInPacket;
            packet.ensureCapacity(newPacketLength);
            int[] addresses = packet.getAddresses();
            int[] timestamps = packet.getTimestamps();


            /*
             * All AEDAT 3.0 data extracting will just be processed in this if block.
             * If the frame events can't be displayed in jAER, please check "maxBytesPerPacket" in the UDP node of caer-config.xml. 
             * This value should be not 0 and not very big (better smaller than 90000). At the same time, this value should also be
             * bigger than maxBytesPerPacket, otherwise every jAER buffer (this value) can't copy the buffer (maxBytesPerPacket) from cAER.
             */
            if (cAERStreamEnabled) {
                try {
                    Jaer3BufferParser j3Parser = new Jaer3BufferParser(buffer, chip);
                    long nEventsNum = j3Parser.size();

                    if (nEventsNum != 0) {  // This is a valid packet's head buffer
                        jaer3PktSize = buffer.getInt(4);
                        jaer3PktNum = buffer.getInt(20);
                        jaer3EventsNum = nEventsNum;
                        wholePktBuffer = clone(buffer);

                        if (wholePktBuffer.position() == (jaer3PktSize * jaer3PktNum + 28)) { // This is the end buffer, the packet is finished.
                            wholePktBuffer.flip();
                            j3Parser = new Jaer3BufferParser(wholePktBuffer, chip);
                        } else {
                            return;
                        }
                    } else {
                        if (wholePktBuffer == null) {  // We still not get a valid buffer, return back to continue wait the valid head buffer
                            return;
                        }
                        try {
                            wholePktBuffer.put(buffer);
                        } catch (BufferOverflowException e) {  // Sometimes the buffer's order may be wrong which will result in the bufferoverflow, so just reset the wholePktBuffer in this case
                            wholePktBuffer = null;
                            return;
                        }
                        if (wholePktBuffer.position() == (jaer3PktSize * jaer3PktNum + 28)) { // This is the end buffer, the packet is finished.
                            wholePktBuffer.flip();
                            j3Parser = new Jaer3BufferParser(wholePktBuffer, chip);
                        } else {
                            return;
                        }
                    }

                    newPacketLength = (int) (startingIndex + jaer3EventsNum);
                    packet.ensureCapacity((int) newPacketLength);
                    EventRaw.EventType[] etypes = packet.getEventtypes(); // For jAER 3.0, no influence on jAER 2.0
                    int[] pixelDataArray = packet.getPixelDataArray();
                    addresses = packet.getAddresses();
                    timestamps = packet.getTimestamps();
                    for (int i = 0; i < jaer3EventsNum; i++) {
                        ByteBuffer tmpEventBuffer = ByteBuffer.allocate(16);
                        tmpEventBuffer = j3Parser.getJaer2EventBuf();
                        int etypeValue = tmpEventBuffer.getInt();
                        eventRaw.eventtype = EventRaw.EventType.values()[etypeValue];
                        eventRaw.address = tmpEventBuffer.getInt();
                        eventRaw.timestamp = tmpEventBuffer.getInt();
                        eventRaw.pixelData = tmpEventBuffer.getInt();
                        etypes[startingIndex + i] = eventRaw.eventtype;
                        addresses[startingIndex + i] = eventRaw.address;
                        timestamps[startingIndex + i] = eventRaw.timestamp;
                        pixelDataArray[startingIndex + i] = eventRaw.pixelData;
                    }
                    packet.setNumEvents((int) (startingIndex + jaer3EventsNum));
                } catch (IOException ex) {
                    Logger.getLogger(AEUnicastInput.class.getName()).log(Level.SEVERE, null, ex);
                }
                return;
            }

            for (int i = 0; i < nEventsInPacket; i++) {
                if (addressFirstEnabled) {
                    if (use4ByteAddrTs) {
                        eventRaw.address = maybeSwapByteOrder(buffer.getInt());
                        // if timestamps are enabled, they have to be read out even if they are not used because of local timestamps
                        if (timestampsEnabled && !localTimestampsEnabled) {
                            int rawTime = maybeSwapByteOrder(buffer.getInt()); //swab(v);
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
                        } else { // timestamps not enabled, using local timestamps
                            //SmartEyeTDS
                            eventRaw.timestamp = ts; // this is local timestamp computed earlier
                        }
                    } else { // 2 byte address and timestamp
                        eventRaw.address = maybeSwapByteOrder(buffer.getShort()) & 0xffff; // swab(buffer.getShort()); // swapInt is switched to handle big endian event sources (like ARC camera)
                        //                    eventRaw.timestamp=(int) (timestampMultiplier*(int) swab(buffer.getShort()));
                        if (!localTimestampsEnabled && timestampsEnabled) {
                            eventRaw.timestamp = (int) (timestampMultiplier * maybeSwapByteOrder(buffer.getShort()));
                        } else {
                            eventRaw.timestamp = ts;
                        }
                    }
                } else if (use4ByteAddrTs) { // timestamp first option
                    //                    eventRaw.timestamp=(int) (swab(buffer.getInt()));
                    //                    eventRaw.address=swab(buffer.getInt());
                    if (!localTimestampsEnabled && timestampsEnabled) {
                        eventRaw.timestamp = (maybeSwapByteOrder(buffer.getInt()));
                    } else {
                        eventRaw.timestamp = ts;
                    }
                    eventRaw.address = (maybeSwapByteOrder(buffer.getInt()));
                } else { // 2 byte values
                    //                    eventRaw.timestamp=(int) (swab(buffer.getShort()));
                    //                    eventRaw.address=(int) (timestampMultiplier*(int) swab(buffer.getShort()));
                    if (!localTimestampsEnabled && timestampsEnabled) {
                        eventRaw.timestamp = (maybeSwapByteOrder(buffer.getShort()));  // TODO check if need AND with 0xffff to avoid negative timestamps
                    } else {
                        eventRaw.timestamp = ts;
                    }
                    eventRaw.address = (int) (timestampMultiplier * (maybeSwapByteOrder(buffer.getShort()) & 0xffff));
                }

                // alternative is to directly add to arrays of packet for speed, to bypass the capacity checking
                //            packet.addEvent(eventRaw);
                addresses[startingIndex + i] = eventRaw.address;
                timestamps[startingIndex + i] = eventRaw.timestamp;

            }
            packet.setNumEvents(newPacketLength);
        }
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
        freeBuffers();
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
            /* The extractor might be changed in the AEUnicastInput, so we should restore it back. 
             * This operation must be after the datagramSocket.close() to make sure there're no packets
             * on the network now.
             * If there're still packets on the network, then jaer3BufferParser will be called again which
             * will result in the change of extractor again. 
             * So we should put the extractor restore after the datagrarSocket.close().
             */
            chip.setEventExtractor(restoreEventExtractor);
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
            datagramSocket.setReceiveBufferSize(bufferSize);
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
        allocateBufffers();
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

    @Override
    public boolean iscAERDisplayEnabled() {
        return cAERStreamEnabled;
    }

    /**
     * If set true (default), then cAER data can be displayed the packet.
     * Otherwise the first int32 is part of the first AE.
     *
     * @param cAERDisplayEnabled default true
     */
    @Override
    public void setCAERDisplayEnabled(boolean cAERDisplayEnabled) {
        this.cAERStreamEnabled = cAERDisplayEnabled;
        prefs.putBoolean("AEUnicastInput.cAERDisplayEnabled", cAERDisplayEnabled);
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

    @Override
    public boolean isSpinnakerProtocolEnabled() {
        return spinnakerProtocolEnabled;
    }

    @Override
    public void setSpinnakerProtocolEnabled(boolean yes) {
        spinnakerProtocolEnabled = yes;
        secDvsProtocolEnabled=false;
        prefs.putBoolean("AEUnicastInput.secDvsProtocolEnabled", yes);
    }

    /**
     * @return the secDvsProtocolEnabled
     */
    public boolean isSecDvsProtocolEnabled() {
        return secDvsProtocolEnabled;
    }

    /**
     * @param secDvsProtocolEnabled the secDvsProtocolEnabled to set
     */
    public void setSecDvsProtocolEnabled(boolean secDvsProtocolEnabled) {
        this.secDvsProtocolEnabled = secDvsProtocolEnabled;
        spinnakerProtocolEnabled=false;
        if(secDvsProtocolEnabled) setPort(SEC_DVS_STREAMER_PORT);
        prefs.putBoolean("AEUnicastInput.secDvsProtocolEnabled",secDvsProtocolEnabled);
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
