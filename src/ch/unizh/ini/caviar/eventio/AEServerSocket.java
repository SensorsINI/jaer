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
import java.util.logging.*;

/**
 * This server socket allows a host to listen for connections from other hosts and opens a Socket to them to allow
 streaming AE data to them. The resulting stream socket connections transmit data reliably.
 At present only a single socket is allowed at one time - if a new connection is made, the old socket is closed.
 
 * @author tobi
 */
public class AEServerSocket extends Thread {
    
    static Logger log=Logger.getLogger("AEServerSocket");
    ServerSocket serverSocket;
    private AESocket socket=null;
    
    /** Creates a new instance of AEServerSocket */
    public AEServerSocket() {
        try{
            serverSocket=new ServerSocket(AENetworkInterface.PORT);
        }catch(java.net.BindException be){
            log.warning("server socket already bound to port (probably from another AEViewer)");
        }catch(IOException ioe){
            log.warning(ioe.getMessage());
        }
        setName("AEServerSocket");
    }
    
    public void run(){
        if(serverSocket==null) return; // port was already bound
        while(true){
            try{
                Socket newSocket=serverSocket.accept();
                if(getSocket()!=null){
                    log.info("closing socket "+getSocket());
                    try{
                        getSocket().close();
                    }catch(IOException ioe){
                        log.warning("while closing old socket caught "+ioe.getMessage());
                    }
                }
                AESocket aeSocket=new AESocket(newSocket);
                synchronized(this){
                    setSocket(aeSocket);
                };
                log.info("opened socket "+newSocket);
            }catch(IOException e){
                e.printStackTrace();
            }
        }
    }
    
    
    synchronized public AESocket getSocket() {
        return socket;
    }
    
    public void setSocket(AESocket socket) {
        this.socket = socket;
    }
    
    public static void main(String[] a){
        AEServerSocket ss=new AEServerSocket();
        ss.start();
    }
    
}
