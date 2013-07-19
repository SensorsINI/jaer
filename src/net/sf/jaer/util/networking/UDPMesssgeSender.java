/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.util.networking;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.Exchanger;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 *  Utility class for sending UDP string messages to a port at a different IP address. The messages are Strings
 * and they are sent on a DatagramChannel.
 *
 * @author tobi
 */
public class UDPMesssgeSender {

    final static Preferences prefs = Preferences.userNodeForPackage(UDPMesssgeSender.class);
    static final Logger log = Logger.getLogger("UDPMesssgeSender");
    protected DatagramChannel channel = null;
    protected DatagramSocket socket = null;
    int sequenceNumber = 0;  // increments on each message, used to detect dropped packets at receiver
    InetSocketAddress client = null;
    Thread consumerThread;
    private String host = prefs.get("UDPMesssgeSender.host", "localhost");
    public static final int DEFAULT_PORT = 14334;
    private int port = DEFAULT_PORT;
    /** Buffer size (max) in bytes */
    final public static int BUFFER_SIZE_BYTES = 1500;
    private Exchanger<CharBuffer> exchanger = new Exchanger();
    private CharBuffer initialEmptyBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE_BYTES).asCharBuffer(); // the buffer to start capturing into
    private CharBuffer initialFullBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE_BYTES).asCharBuffer();// the buffer to render/process first
    private CharBuffer currentBuf = initialEmptyBuffer; // starting buffer for filling
    long msgTime;
    public static final char SEP = ' ';
    boolean isOpen = false;

    public UDPMesssgeSender() {
    }

    /**
     * Writes the message out as String with prepended sequence number and timestamp in System.currentTimeMillis().
     * The fields are tab separated.
     * <p>
     * This method actually offers new Datagram packets to the consumer thread for later tranmission. The datagram address and port are taken from
     * the current settings for the AEUnicastOutput.
     *
     *@param msg a String message
     */
    synchronized public void sendMessage(String msg) throws IOException {
//        StringBuilder sb=new StringBuilder();
        try {
            // write the sequence number for this DatagramPacket to the buf for this ByteArrayOutputStream
            msgTime = System.currentTimeMillis();
            currentBuf.put(String.format("%6d%c%20d%c", sequenceNumber, SEP, msgTime, SEP));
            sequenceNumber++;
            // then the message
            currentBuf.put(msg).put('\n');
            currentBuf = exchanger.exchange(currentBuf);
            currentBuf.clear();
        } catch (Exception e) {
            log.warning("Exception writing message, will not send it: " + e.toString());
        }
    }

    /** Returns true if open has succeeded and close has not been called.
     * 
     * @return true if open() has succeeded and close has not been called.
     */
    public boolean isOpen(){
        return isOpen;
    }

    /** Opens or reopens the channel. If the channel is not open, open it. If it is open then just return.
     *
     * @throws IOException
     */
    public void open() throws IOException {
        if (isOpen) {
            log.info("already open, not opening");
            return;
        }
        channel = DatagramChannel.open();
        socket = channel.socket(); // bind to any available port because we will be sending datagrams with included host:port info
        socket.setTrafficClass(0x10 + 0x08); // low delay
        socket.setSendBufferSize(BUFFER_SIZE_BYTES); // TODO chyanging buffer size later doesn't change this initial value
        allocateBuffers();
        consumerThread = new Thread(new Consumer(exchanger, initialFullBuffer));
        consumerThread.setName("UDPMessageSender");
        consumerThread.setPriority(Thread.NORM_PRIORITY + 1);
        consumerThread.start();
        isOpen = true;
        log.info("opened UDPMessageSender on local port=" + socket.getLocalPort() + " with bufferSize=" + BUFFER_SIZE_BYTES);
    }

    synchronized private void allocateBuffers() {
        initialEmptyBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE_BYTES).asCharBuffer(); // the buffer to start capturing into
        initialFullBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE_BYTES).asCharBuffer();// the buffer to render/process first
        currentBuf = initialEmptyBuffer; // starting buffer for filling
        try {
            setSocketBufferSize();
        } catch (SocketException ex) {
            log.warning("setting buffer size, caught " + ex.toString());
        }
    }

    private void setSocketBufferSize() throws SocketException {
        if (socket == null) {
            log.warning("socket is null, cannot set its buffer size");
            return;
        }
        socket.setSendBufferSize(BUFFER_SIZE_BYTES); // TODO chyanging buffer size later doesn't change this initial value
        int sendBufferSize = socket.getSendBufferSize();
        if (sendBufferSize != BUFFER_SIZE_BYTES) {
            log.warning("socket could not be sized to hold " + BUFFER_SIZE_BYTES + " bytes" + BUFFER_SIZE_BYTES + " bytes, could only get sendBufferSize=" + sendBufferSize);
        } else {
            log.info("getSendBufferSize (bytes)=" + sendBufferSize);
        }
    }

    class Consumer implements Runnable {

        private final Exchanger<CharBuffer> exchanger;
        private CharBuffer buf;

        Consumer(Exchanger<CharBuffer> exchanger, CharBuffer initBuf) {
            this.exchanger = exchanger;
            this.buf = initBuf;
        }

        public void run() {
            try {
                while (true) {
                    buf = exchanger.exchange(buf);
                    buf.flip();
                    if (!buf.hasRemaining()) {
                        continue; // don't write empty packets
                    }
                    if (!checkClient()) { // if client not there, just continue - maybe it comes back
                        continue;
                    }
                    try {
//                        log.info("sending buf="+buf+" to client="+client);
                        ByteBuffer b = ByteBuffer.wrap(buf.toString().getBytes());
                        channel.send(b, client);
                    } catch (IOException e) {
                        log.warning("caught when sending buffer: " + e);
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

    public void close() {
        isOpen = false;
        if (socket == null) {
            return;
        }
        socket.close();
        if (consumerThread != null) {
            consumerThread.interrupt();
        }
        log.info("closed socket and interrupted consumer thread");
    }

    /** returns true if socket exists and is bound */
    private boolean checkClient() {
        if (socket == null) {
            return false;
        }

        try {
            if (socket.isBound()) {
                return true;
            }
            client = new InetSocketAddress(host, port);
            return true;
        } catch (Exception se) { // IllegalArgumentException or SecurityException
            log.warning("While checking client host=" + host + " port=" + port + " caught " + se.toString());
            return false;
        }
    }

    /** You need to setHost before this will send events.
    @param host the host
     */
    synchronized public void setHost(String host) {
        this.host = host;
//        if ( checkClient() ){
        prefs.put("UDPMessageSender.host", host);
//        }else{
//            log.warning("checkClient() returned false, not storing "+host+" in preferences");
//        }
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
        prefs.putInt("UDPMessageSender.port", port);
    }
}
