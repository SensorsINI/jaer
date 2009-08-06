package edu.mplab.rubios.node;

import java.io.*;
import java.net.*;
import java.util.*;

/** 
 * RUBIOSConnectionHandler
 * <p>
 * Establishes connection with a RUBIOS robot: Opens a socket and
 * provides input and output channels in the form of a PrinterWriter (for
 * output) and a BufferedReader (for input).
 *
 * @Authors Javier R. Movellan 
 * @Copyright UCSD, Machine Perception Laboratory, and Javier R. Movellan
 * @License  GPL
 * @Date April 23, 2006
 *
 */
public class RUBIOSConnectionHandler extends Thread{

    PrintWriter sout = null;
    BufferedReader sin = null;
    String host="localhost"; 
    int portno=1357;         
    Socket socket = null;
    boolean connectionCompleted= false;
    /** Creates a new instance of RUBIOSConnectionHandler */


    public RUBIOSConnectionHandler(String myHost, int myPort) {
	
	host = myHost;
	portno = myPort;
	
	try{
	    //open a socket connection
	    System.out.println("Opening Socket Connection");
            socket = new Socket(host,portno);
            // open I/O streams for objects
	    sin =  new BufferedReader(new InputStreamReader(socket.getInputStream()));
	    
	    sout = new PrintWriter(socket.getOutputStream(),true);
	    connectionCompleted = true;
	} catch(Exception e){}

	
    }

}
