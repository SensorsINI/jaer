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

import net.sf.jaer.Description;

/**
 * Holds static values for AE stream socket and datagram connection classes.
 <p>
 A useful page for multicasting (on which the AEs can be streamed) is <a href="http://www.multicasttech.com/faq/">this FAQ</a>.
 
 * @author tobi
 */
@Description("""
        <html>
        <b>Network (Remote) AE streaming</b><br>
        Send or receive address-events between AEViewers or other programs on this machine or the LAN.<br>
        <p><b>UDP unicast</b> — low-latency datagrams to one host (including localhost). Sequence numbers report drops.<br>
        <b>BlockingQueue</b> — in-process handoff: enable <i>input</i> on the receiver, then <i>output</i> on the sender (same jAER process).<br>
        <p>Default UDP port 8991.
        </html>
        """)
public interface AENetworkInterfaceConstants {
    
    /** the default port the stream socket is created on */
    static public final int STREAM_PORT=8990; // unassigned according to http://www.iana.org/assignments/port-numbers
    
    /** the default port that unicast connections use */
    static public final int DATAGRAM_PORT=8991;
    
    /** Archival multicast group for {@link AEMulticastInput}/{@link AEMulticastOutput} (IoT-camera prototype). Not used by AEViewer. */
    static public final String MULTICAST_INETADDR="230.3.1.4"; // ok according to http://www.29west.com/docs/THPM/multicast-address-assignment.html
    
    /** size of socket event in bytes */
    static public final int EVENT_SIZE_BYTES=8;
    
     /** Max UDP datagram payload in bytes (one on-wire packet). Distinct from the kernel socket queue
      * ({@link #DATAGRAM_SOCKET_BUFFER_SIZE_BYTES}): a 63 kB datagram of events is a single UDP packet,
      * while a 32 k-event AE packet is several such datagrams sent back-to-back.
      */
    static public int DATAGRAM_BUFFER_SIZE_BYTES=63000; //1028; // 1300;  // 32k MAX_EVENTS*EVENT_SIZE_BYTES+Integer.SIZE/8;

    /**
     * Kernel UDP socket queue (SO_SNDBUF / SO_RCVBUF) in bytes. Must hold a burst of datagrams.
     * If this is only one datagram large, localhost loopback drops packets: UDP send() still
     * succeeds, but the receiver's socket queue overflows before the reader thread wakes.
     * 8 MiB holds ~130 max-size datagrams (~1e6 events).
     */
    static public final int DATAGRAM_SOCKET_BUFFER_SIZE_BYTES = 8 * 1024 * 1024;

    /**
     * Kernel socket queue size for a given max datagram payload. Always at least
     * {@link #DATAGRAM_SOCKET_BUFFER_SIZE_BYTES}, and at least 32 datagrams.
     */
    static public int datagramSocketBufferSizeBytes(int datagramPayloadBytes) {
        if (datagramPayloadBytes <= 0) {
            return DATAGRAM_SOCKET_BUFFER_SIZE_BYTES;
        }
        long scaled = (long) datagramPayloadBytes * 32L;
        if (scaled > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return Math.max(DATAGRAM_SOCKET_BUFFER_SIZE_BYTES, (int) scaled);
    }

    /** the maximum number deliverable over a socket per packet. The UDP buffers are sized according to this number. */
    static public int MAX_DATAGRAM_EVENTS=(DATAGRAM_BUFFER_SIZE_BYTES-Integer.SIZE/8)/EVENT_SIZE_BYTES;

 

}
