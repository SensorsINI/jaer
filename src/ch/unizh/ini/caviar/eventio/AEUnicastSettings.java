/*
 * AEUnicastDialog.java
 *
 * Created on April 25, 2008, 8:40 AM
 */
/** Interface for AEUnicast connections, to allow use of common dialog setting
@author tobi
 */
package ch.unizh.ini.caviar.eventio;

public interface AEUnicastSettings {

    public static final boolean DEFAULT_ADDRESS_FIRST = true;
    public static final boolean DEFAULT_USE_SEQUENCE_NUMBER = true;
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = AENetworkInterface.DATAGRAM_PORT;
    public static final boolean DEFAULT_SWAPBYTES_ENABLED = false;
    public static final float DEFAULT_TIMESTAMP_MULTIPLIER = 1;

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

    /** You need to setHost before this will receive events
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

    public float getTimestampMultiplier();

    public void setTimestampMultiplier(float timestampMultiplier);
}
