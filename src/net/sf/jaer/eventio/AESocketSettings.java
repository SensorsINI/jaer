/*
 * AESocketDialog.java
 *
 * Created on April 25, 2008, 8:40 AM
 */
/** Interface for AESocket connections, to allow use of common dialog setting
 *  Modified from AEUnicastDialog.java
@author emre
 */
package net.sf.jaer.eventio;
import java.io.IOException;
public interface AESocketSettings {

    public static final int DEFAULT_RECEIVE_BUFFER_SIZE_BYTES=8192;

    public static final int DEFAULT_SEND_BUFFER_SIZE_BYTES=8192;

    public static final int DEFAULT_BUFFERED_STREAM_SIZE_BYTES=8192;

    public static final boolean DEFAULT_USE_BUFFERED_STREAM=false;

    /** timeout in ms for connection attempts */
    public static final int CONNECTION_TIMEOUT_MS=3000;
    /** timeout in ms for read/write attempts */
    public static final int SO_TIMEOUT=1; // 1 means we should timeout as soon as there are no more events in the datainputstream

    /** Default address first (versus timestamp first) setting */
    public static final boolean DEFAULT_ADDRESS_FIRST = true;
    /** Default is to use sequence numbers as first int32 (4 bytes) of each packet */
    public static final boolean DEFAULT_USE_SEQUENCE_NUMBER = true;
    
    /** jAER by default uses 4 byte raw addresses and timestamps */
    public static final boolean DEFAULT_USE_4_BYTE_ADDR_AND_TIMESTAMP=true;

    /** jAER by default uses timestamps for UDP interfaces. */
    public static final boolean DEFAULT_TIMESTAMPS_ENABLED=true;

    /** jAER by default uses the AE data timestamps */
    public static final boolean DEFAULT_USE_LOCAL_TIMESTAMPS_ENABLED=false;

    /** Incoming or outgoing events times should be treated as timestamps **/
    public static final boolean DEFAULT_USE_ISI_ENABLED=true;

    public final int MAX_NONMONOTONIC_TIME_EXCEPTIONS_TO_PRINT=10;

    public static final String DEFAULT_HOST = "localhost";
    /** Default TCP port */
    public static final int DEFAULT_PORT = AENetworkInterfaceConstants.STREAM_PORT;
    /** Default swapping of bytes */

    public static final boolean DEFAULT_SWAPBYTES_ENABLED = false;
    
    /** Default timestamp multiplier */
    public static final float DEFAULT_TIMESTAMP_MULTIPLIER = 1;   

    /** Opens or reopens the AEUnicast channel. If the channel is not open, open it. If it is open, then close and reopen it.
     * 
     * @throws IOException
     */
    public void connect() throws IOException;

    /** Closes the AEUnicast channel. */

    public void close() throws IOException;

    /** Returns true if packets should include a leading sequence counter. */
    public boolean isSequenceNumberEnabled();

    /** If set true (default), then an int32 sequence number is the first word of the packet. Otherwise the
     * first int32 is part of the first AE. 
     * 
     * @param sequenceNumberEnabled default true
     */
    public void setSequenceNumberEnabled(boolean sequenceNumberEnabled);

    /** @see #setAddressFirstEnabled */
    public boolean isAddressFirstEnabled();

    /** If set true, the first int32 of each AE is the address, and the second is the timestamp. If false,
     * the first int32 is the timestamp, and the second is the address.
     * This parameter is stored as a preference.
     * @param addressFirstEnabled default true. 
     */
    public void setAddressFirstEnabled(boolean addressFirstEnabled);

    /** You need to setHost to send unicast packets to a host. Ignored for receiving events.
    @param host the hostname
     */
    public void setHost(String host);

    public String getHost();

    public int getPort();

    public void setPort(int port);

    /** To handle big endian event sources/sinks 
     * (e.g. intel code) the address and timestamp bytes can be swapped from big to little endian format */
    public void setSwapBytesEnabled(boolean yes);

    public boolean isSwapBytesEnabled();

    /** @see #setTimestampMultiplier */
    public float getTimestampMultiplier();

    /** Sets the timestamp multiplier. Timestamps in the incoming stream are multiplied by this value
     * to generate the internal timestamps used in jAER, by default each 1 us. If the remote host uses
     * timestamps of 1 ms, then set the multiplier to 1000 to turn each remote timestamp into 1000 us.
     * Timestamps in outgoing streams are divided by the timestamp multiplier.
     * @param timestampMultiplier
     */
    public void setTimestampMultiplier(float timestampMultiplier);
    
    /** Sets whether to use 4 byte address and 4 byte timestamp or 2 byte address and 2 byte timestamp.
     * Set true to use 4 bytes for each.
     * @param yes
     */
    public void set4ByteAddrTimestampEnabled(boolean yes);
    
    public boolean is4ByteAddrTimestampEnabled();

    /** Sets the buffer size in bytes used for the underlying packets.
     *
     * @param size in bytes.
     */
    public void setSendBufferSize(int size);

    /** Gets the buffer size in bytes used for the packets.
     *
     * @return the size in bytes.
     */
    public int getSendBufferSize();

        /** Sets the buffer size in bytes used for the underlying datagrams.
     *
     * @param size in bytes.
     */
    public void setReceiveBufferSize(int size);

    /** Gets the buffer size in bytes used for the packets.
     *
     * @return the size in bytes.
     */
    public int getReceiveBufferSize();

    /** If timestamps are disabled then interfaces only send or receive address data.
     * @return true if using timestamps.
     * @see #setLocalTimestampEnabled(boolean)
     */
    public boolean isTimestampsEnabled();

    /** Enables transmission/reception of timestamp data over this interface.
     * @param yes true to use timestamps, false to send/recv only addresses.
     */
    public void setTimestampsEnabled(boolean yes);

    /** Sets whether the AE data supplies the event timestamps or whether they come from System.nanoTime()/1000.
     *
     * @param yes true to use System.nanoTime/1000, false to use source timestamps.
     */
    public void setLocalTimestampEnabled(boolean yes);

   /** Says whether the AE data supplies the event timestamps or whether they come from System.nanoTime()/1000.
     *
     * @return  true if using System.nanoTime/1000, false if using source timestamps.
     */
    public boolean isLocalTimestampEnabled();

     /** Says whether the AE data supplies the event timestamps as interspike intervals (ISI).
     *
     * @param yes true to use ISIs.
     */
    public void setISIEnabled(boolean yes);

     /** Says whether the AE data supplies the event timestamps as interspike intervals (ISI).
     *
     * @return true if uses ISIs.
     */
    public boolean isISIEnabled();

    public boolean isUseBufferedStreams();

    public void setUseBufferedStreams(boolean useBufferedStreams);
     
}
