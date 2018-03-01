package edu.mplab.rubios.node;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.util.Date;
import java.util.GregorianCalendar;


/** 
 *RUBIOSInputHandler
 *<p>
 *Provides methods for monitoring the inputs to the node received via
 *sockets When a new message arrives, it first checks whether it is a
 *RUBIOS message. If not, it is treated as a inter-node message and put
 *into a thread-safe double buffer. Down the line other methods can read
 *from the buffer.
 *
 * @author Javier R. Movellan
 * Copyright UCSD, Machine Perception Laboratory, and Javier R. Movellan
 * License  GPL
 * Date April 23, 2006
 */
public class RUBIOSInputHandler extends Thread{
    String fileName =null;

    PrintWriter sout = null;
    BufferedReader sin = null;

    DoubleStringBuffer doubleBuffer=null; // provides thread safe read
					  // and write of incoming
					  // messages

    
    long tOrigin; // Time origin to measure elapsed time in millisecs

    /** Creates a new instance of RUBIOSInputHandler */
    public RUBIOSInputHandler(BufferedReader myin) {
	// use 200 strings as the default buffer size
	this(myin, 200) ;
    }

    public RUBIOSInputHandler(BufferedReader myin, int bufSize) {
	doubleBuffer = new DoubleStringBuffer(bufSize);
	sin = myin;


	// use Jan 1 2006 at 0 hours, 0 min, 0 secs as time origin
	GregorianCalendar gc1 = new GregorianCalendar(2006, 1, 1, 0, 0, 0);
	Date d1 = gc1.getTime();
	tOrigin = d1.getTime();
	this.start();

    }

    public int getNumRemainingMessages()
    {
	return doubleBuffer.getNumRemainingMessages();
    }
    
    public void run(){
	long t;
	String str; 
	try{
	    while((str = sin.readLine())!= null){
		    doubleBuffer.write(str);
	    }
      	} catch(Exception e){}
	System.exit(1);
    }
    

}




