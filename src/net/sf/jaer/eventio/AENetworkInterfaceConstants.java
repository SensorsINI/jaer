/*
 * AESocketStream.java
 *
 * Created on July 2, 2006, 11:29 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright July 2, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package net.sf.jaer.eventio;

/**
 * Holds static values for AE stream socket and datagram connection classes.
 <p>
 A useful page for multicasting (on which the AEs can be streamed) is <a href="http://www.multicasttech.com/faq/">this FAQ</a>.
 
 * @author tobi
 */
public interface AENetworkInterfaceConstants {
    
    /** the default port the stream socket is created on */
    static public final int STREAM_PORT=8990; // unassigned according to http://www.iana.org/assignments/port-numbers
    
    /** the default port that unicast connections use */
    static public final int DATAGRAM_PORT=8991;
    
    /** the default inet address we multicast to. Not used with unicast communications. */
    static public final String MULTICAST_INETADDR="230.3.1.4"; // ok according to http://www.29west.com/docs/THPM/multicast-address-assignment.html
    
    /** size of socket event in bytes */
    static public final int EVENT_SIZE_BYTES=8;
    
     /** the sockets are set up to try to get this size in bytes as the buffer size. The max number of events per packet is computed from
      this socket buffer size.
      */
    static public int DATAGRAM_BUFFER_SIZE_BYTES=63000; //1028; // 1300;  // 32k MAX_EVENTS*EVENT_SIZE_BYTES+Integer.SIZE/8;

    
    /** the maximum number deliverable over a socket per packet. The UDP buffers are sized according to this number. */
    static public int MAX_DATAGRAM_EVENTS=(DATAGRAM_BUFFER_SIZE_BYTES-Integer.SIZE/8)/EVENT_SIZE_BYTES;

 

}
