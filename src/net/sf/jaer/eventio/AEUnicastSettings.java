/*
 * AEUnicastDialog.java
 *
 * Created on April 25, 2008, 8:40 AM
 */
/** Interface for AEUnicast connections, to allow use of common dialog setting
 *
 * @author tobi
 */
package net.sf.jaer.eventio;

import java.io.IOException;

public interface AEUnicastSettings {

    /**
     * Default address first (versus timestamp first) setting
     */
    public static final boolean DEFAULT_ADDRESS_FIRST = true;
    /**
     * Default is to use sequence numbers as first int32 (4 bytes) of each
     * packet
     */
    public static final boolean DEFAULT_USE_SEQUENCE_NUMBER = true;

    /**
     * jAER by default uses 4 byte raw addresses and timestamps
     */
    public static final boolean DEFAULT_USE_4_BYTE_ADDR_AND_TIMESTAMP = true;

    /**
     * jAER by default uses timestamps for UDP interfaces.
     */
    public static final boolean DEFAULT_TIMESTAMPS_ENABLED = true;

    /**
     * jAER by default uses the AE data timestamps
     */
    public static final boolean DEFAULT_USE_LOCAL_TIMESTAMPS_ENABLED = false;

    public static final String DEFAULT_HOST = "localhost";
    /**
     * Default jAER UDP port
     */
    public static final int DEFAULT_PORT = AENetworkInterfaceConstants.DATAGRAM_PORT;
    /**
     * Default swapping of bytes
     */
    public static final boolean DEFAULT_SWAPBYTES_ENABLED = false;

    /**
     * Default timestamp multiplier
     */
    public static final float DEFAULT_TIMESTAMP_MULTIPLIER = 1;

    /**
     * default port for streaming AE Events from ARC smarteye TDS sensor
     */
    public static final int ARC_TDS_STREAM_PORT = 20020;

    /**
     * default port for streaming AE Events from SEC DVS Gen2 sensor
     */
    public static final int SEC_DVS_STREAMER_PORT = 8991; // TODO set correctly

    /**
     * timestamp multiplier for ARC TDS smart eye sensor streaming data
     */
    public static final float ARC_TDS_TIMESTAMP_MULTIPLIER = 1000f; // TDS timestamps are 1ms

    /**
     * ARC TDS smarteye swaps byte order since it comes from a non-intel system
     */
    public static final boolean ARC_TDS_SWAPBYTES_ENABLED = true;

    /**
     * ARC TDS smarteye does not use sequence numbers
     */
    public static final boolean ARC_TDS_SEQUENCE_NUMBERS_ENABLED = false;

    /**
     * ARC TDS smarteye sends address bytes first
     */
    public static final boolean ARC_TDS_ADDRESS_BYTES_FIRST_ENABLED = true;

    /**
     * ARC TDS smarteye uses 2 byte address and timestamp data
     */
    public static final boolean ARC_TDS_4_BYTE_ADDR_AND_TIMESTAMPS = true;

    /**
     * ARC TDS smarteye uses 2 byte address and timestamp data
     */
    public static final int ARC_TDS_BUFFER_SIZE = 1500;

    /**
     * Opens or reopens the AEUnicast channel. If the channel is not open, open
     * it. If it is open, then close and reopen it.
     *
     * @throws IOException
     */
    public void open() throws IOException;

    /**
     * Closes the AEUnicast channel.
     */
    public void close();

    /**
     * Pauses the channel, so that for example the internal running thread does
     * not consume memory filling buffers. Has no effect if the channel is not
     * open.
     *
     * @param yes true to pause.
     */
    public void setPaused(boolean yes);

    /**
     * Returns the paused status of the channel.
     *
     * @return true if channel is paused. If the channel is closed returns
     * false.
     */
    public boolean isPaused();

    /**
     * Returns true if packets should include a leading sequence counter.
     */
    public boolean isSequenceNumberEnabled();

    /**
     * If set true (default), then an int32 sequence number is the first word of
     * the packet. Otherwise the first int32 is part of the first AE.
     *
     * @param sequenceNumberEnabled default true
     */
    public void setSequenceNumberEnabled(boolean sequenceNumberEnabled);

    /**
     * Returns true if the data is in the cAER format
     */
    public boolean iscAERDisplayEnabled();

    /**
     * If set true (default), then an int32 sequence number is the first word of
     * the packet. Otherwise the first int32 is part of the first AE.
     *
     * @param sequenceNumberEnabled default true
     */
    public void setCAERDisplayEnabled(boolean cAERDisplayEnabled);

    /**
     * @see #setAddressFirstEnabled
     */
    public boolean isAddressFirstEnabled();

    /**
     * If set true, the first int32 of each AE is the address, and the second is
     * the timestamp. If false, the first int32 is the timestamp, and the second
     * is the address. This parameter is stored as a preference.
     *
     * @param addressFirstEnabled default true.
     */
    public void setAddressFirstEnabled(boolean addressFirstEnabled);

    /**
     * You need to setHost to send unicast packets to a host. Ignored for
     * receiving events.
     *
     * @param host the hostname
     */
    public void setHost(String host);

    public String getHost();

    public int getPort();

    public void setPort(int port);

    /**
     * To handle big endian event sources/sinks (e.g. intel code) the address
     * and timestamp bytes can be swapped from big to little endian format
     */
    public void setSwapBytesEnabled(boolean yes);

    public boolean isSwapBytesEnabled();

    /**
     * @see #setTimestampMultiplier
     */
    public float getTimestampMultiplier();

    /**
     * Sets the timestamp multiplier. Timestamps in the incoming stream are
     * multiplied by this value to generate the internal timestamps used in
     * jAER, by default each 1 us. If the remote host uses timestamps of 1 ms,
     * then set the multiplier to 1000 to turn each remote timestamp into 1000
     * us. Timestamps in outgoing streams are divided by the timestamp
     * multiplier.
     *
     * @param timestampMultiplier
     */
    public void setTimestampMultiplier(float timestampMultiplier);

    /**
     * Sets whether to use 4 byte address and 4 byte timestamp or 2 byte address
     * and 2 byte timestamp. Set true to use 4 bytes for each.
     *
     * @param yes
     */
    public void set4ByteAddrTimestampEnabled(boolean yes);

    public boolean is4ByteAddrTimestampEnabled();

    /**
     * Sets the buffer size in bytes used for the underlying datagrams.
     *
     * @param size in bytes.
     */
    public void setBufferSize(int size);

    /**
     * Gets the buffer size in bytes used for the datagrams.
     *
     * @return the size in bytes.
     */
    public int getBufferSize();

    /**
     * If timestamps are disabled then unicast interfaces only send or receive
     * address data.
     *
     * @return true if using timestamps.
     * @see #setLocalTimestampEnabled(boolean)
     */
    public boolean isTimestampsEnabled();

    /**
     * Enables transmission/reception of timestamp data over this Unicast
     * interface.
     *
     * @param yes true to use timestamps, false to send/recv only addresses.
     */
    public void setTimestampsEnabled(boolean yes);

    /**
     * Sets whether the AE data supplies the event timestamps or whether they
     * come from System.nanoTime()/1000.
     *
     * @param yes true to use System.nanoTime/1000, false to use source
     * timestamps.
     */
    public void setLocalTimestampEnabled(boolean yes);

    /**
     * Says whether the AE data supplies the event timestamps or whether they
     * come from System.nanoTime()/1000.
     *
     * @return true if using System.nanoTime/1000, false if using source
     * timestamps.
     */
    public boolean isLocalTimestampEnabled();

    /**
     * Is the special SpiNNaker mode enabled, as specified in
     * https://capocaccia.ethz.ch/capo/wiki/2015/universalaer15
     *
     * @return true if enabled
     */
    public boolean isSpinnakerProtocolEnabled();

    /**
     * Sets SpiNNaker protocol enabled as specified in
     * https://capocaccia.ethz.ch/capo/wiki/2015/universalaer15
     *
     * @param yes true
     */
    public void setSpinnakerProtocolEnabled(boolean yes);

    /**
     * Sets SEC DVS protocol enabled.
     *
     * @param yes true
     */
    public void setSecDvsProtocolEnabled(boolean yes);

    /**
     * Is SEC DVS protocol enabled
     *
     * @return true if enabled
     */
    public boolean isSecDvsProtocolEnabled();

}
