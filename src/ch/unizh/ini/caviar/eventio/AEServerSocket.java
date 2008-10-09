/*
 * AEServerSocket.java
 *
 * Created on January 5, 2007, 6:31 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright January 5, 2007 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.caviar.eventio;

import java.io.*;
import java.net.*;
import java.nio.channels.SocketChannel;
import java.util.logging.*;
import java.util.prefs.*;
import javax.net.ServerSocketFactory;

/**
 * Following is target functionality (right now this class only opens a single socket to most recent client connecting to it
 and events are only streamed to this one socket): This server socket allows a source host to listen for connections from other hosts and open AESockets to them to allow
 streaming AE data to them, so as a server, we stream events to the clients.
 These stream socket connections transmit data reliably.
 <p>
 Multile clients can recieve events from a single server through the use of java.nio channels and selectors.
 <p>
 The AESocket's are manufactured when a client connects to the AEViewer. The AESocket's are built with options that are
 set using the AEServerSocketOptionsDialog.
 This AEServerSocket is a Thread and it must be started after construction to allow incoming connections.
 
 * @author tobi
 */
public class AEServerSocket extends Thread {
    
    static Preferences prefs=Preferences.userNodeForPackage(AEServerSocket.class);
    static Logger log=Logger.getLogger("AEServerSocket");
    
    public static final int DEFAULT_BUFFERED_STREAM_SIZE_BYTES=8192;
    public static final int DEFAULT_SEND_BUFFER_SIZE_BYTES=8192;
    public static final int DEFAULT_RECIEVE_BUFFER_SIZE_BYTES=8192;
    
    private ServerSocket serverSocket;
    private AESocket socket=null;
    private int bufferedStreamSize=prefs.getInt("AEServerSocket.bufferedStreamSize",DEFAULT_BUFFERED_STREAM_SIZE_BYTES);
    private int sendBufferSize=prefs.getInt("AEServerSocket.sendBufferSize",DEFAULT_SEND_BUFFER_SIZE_BYTES);
    private int port=prefs.getInt("AEServerSocket.port",AENetworkInterfaceConstants.STREAM_PORT);
    private int receiveBufferSize=prefs.getInt("AEServerSocket.receiveBufferSize",DEFAULT_RECIEVE_BUFFER_SIZE_BYTES);
    private boolean flushPackets=prefs.getBoolean("AESocket.flushPackets",true);
    private Thread T=null;
    
    /** Creates a new instance of AEServerSocket
     @throws java.net.BindException when the socket is already bound (probably by another viewer)
     */
    public AEServerSocket() throws java.io.IOException {
        T=this;
        serverSocket=new ServerSocket();
        serverSocket.setReceiveBufferSize(receiveBufferSize);
        serverSocket.bind(new InetSocketAddress(port));
        /*}catch(java.net.BindException be){
            log.warning("server socket already bound to port (probably from another AEViewer)");
         */
        setName("AEServerSocket port="+port);
    }
    
    /** Accepts incoming connections and manufactures AESocket's for them
     */
    public void run(){
        if(serverSocket==null) return; // port was already bound
        while(!isInterrupted()){
            try{
                Socket newSocket=serverSocket.accept(); // makes a new socket to the connecting client
                if(socket!=null){
                    log.info("closing stream TCP output socket "+socket);
                    try{
                        socket.close();
                    }catch(IOException ioe){
                        log.warning("while closing old socket caught "+ioe.getMessage());
                    }
                }
                newSocket.setSendBufferSize(sendBufferSize);
                if(newSocket.getSendBufferSize()!=getSendBufferSize()){
                    log.warning("accepted connection and asked for sendBufferSize="+getSendBufferSize()+" but only got sendBufferSize="+newSocket.getSendBufferSize());
                }
                AESocket aeSocket=new AESocket(newSocket);
                aeSocket.setFlushPackets(isFlushPackets());
                synchronized(this){
                    setSocket(aeSocket);
                };
                log.info("accepted incoming stream TCP socket request to send events on socket "+newSocket);
            }catch(IOException e){
                log.warning(e.toString()+"\njAER server socket on port "+port+" is already bound - another viewer is probably running");
                break;
            }
        }
    }
    
    
    synchronized public AESocket getAESocket() {
        return socket;
    }
    
    public void setSocket(AESocket socket) {
        this.socket = socket;
    }
    
    /** Tests class by constructing a socket and starting the thread */
    public static void main(String[] a){
        AEServerSocket ss;
        try {
            ss = new AEServerSocket();
            ss.start();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    
    public void setBufferedStreamSize(int bufferedStreamSize) {
        this.bufferedStreamSize = bufferedStreamSize;
        prefs.putInt("AEServerSocket.bufferedStreamSize",bufferedStreamSize);
    }
    
    public void setSendBufferSize(int sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
        prefs.putInt("AEServerSocket.sendBufferSize",sendBufferSize);
    }
    
    public void setPort(int port) {
        this.port = port;
        prefs.putInt("AEServerSocket.port",port);
    }
    
    public int getSendBufferSize() {
        return sendBufferSize;
    }
    
    public int getBufferedStreamSize() {
        return bufferedStreamSize;
    }
    
    public int getReceiveBufferSize() {
        return receiveBufferSize;
    }
    
    public void setReceiveBufferSize(int receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
        prefs.putInt("AEServerSocket.receiveBufferSize",receiveBufferSize);
    }
    
    public int getPort() {
        return port;
    }
    
    public boolean isFlushPackets() {
        return flushPackets;
    }
    
    public void setFlushPackets(boolean flushPackets) {
        this.flushPackets = flushPackets;
        prefs.putBoolean("AESocket.flushPackets",flushPackets);
    }
    
    /** shuts down the server socket thread and closes the server socket */
    public void close() throws IOException{
        T.interrupt();
        try {
            T.join(50,0); // wait some ms for server thread to die
        } catch (InterruptedException ex) {
            log.warning("interrupted during thread shutdown");
        }
        serverSocket.close();
        log.info("closed server socket");
    }
}
