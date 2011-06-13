/*
Copyright June 13, 2011 Andreas Steiner, Inst. of Neuroinformatics, UNI-ETH Zurich

This file is part of dsPICserial.

dsPICserial is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

dsPICserial is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with dsPICserial.  If not, see <http://www.gnu.org/licenses/>.
*/



package ch.unizh.ini.jaer.projects.dspic.serial;

/**
 * message with statically allocated buffer for use with <code>StreamCommand</code>
 * providing some convenience methods to extract little endian data from the
 * received byte array.
 * 
 * @see StreamCommand
 * @author andstein
 */

public class StreamCommandMessage {
    
    /** the magic marker being the first two bytes of every message received
        (that's 4660 in decimal) */
    public static final int MARKER= 0x1234;

    /** maximum size of a single frame; the messages should not be too large
        the message's length field can get set to arbitrary values if the
        messages stream gets out of sync (although the probability of this
        should be minimized by the message length field following immediately the
        marker) -- an error is generated and the synchronization re-established
        if the message's length field is greater than <code>BUFSIZE</code>;
        a very large <code>BUFSIZE</code> can result in very large messages that
        take a very long time to being filled resulting in many "real" messages
        being lost.
        @see StreamCommandListener#MESSAGE_TOO_LARGE */
    public static final int BUFSIZE= 2048; // larger results in stream error...
    private byte[] buf;
    private int type;
    private int len;

    /**
     * create a new instance; sets length to zero
     */
    public StreamCommandMessage()
    {
        buf= new byte[BUFSIZE];
        len= 0;
    }
    
    /**
     * the <code>type</code> of a message, as being transmitted by the
     * firmware; identifies the application specific content of this message
     * 
     * @return message's <code>type</code> field (unsigned 16bit value)
     */
    public int getType() { return type; }
    
    /**
     * @see #getType
     */
    public void setType(int messageType) {
        type= messageType;
    }
    
    /**
     * sets the length of the message
     * @param length in bytes
     */
    public void setLength(int length) {
        if (len>BUFSIZE)
            throw new RuntimeException("message length exceeds BUFSIZE!");
        len= length;
    }

    /**
     * get the number of bytes contained in this message
     * @return length of this message's content in bytes
     */
    public int getLength() {
        return len;
    }

    /**
     * @param i index into array of 8bit values
     * @return the message's content at the given position
     */
    public byte getByteAt(int i) {
        return buf[i];
    }
    
    
    /**
     * returns a signed Q15 fractional (must be on even byte address inside
     * message)
     * 
     * @param i location (index into array of 16bit values)
     * @return 
     */
    public float getSignedFloatAt(int i) {
        return getSignedWordAt(i) / ((float) (1<<15));
    }
    
    /**
     * returns a signed Q15 fractional (must be on even byte address inside
     * message)
     * 
     * @param i location (index into array of 16bit values)
     * @return 
     */
    public int getSignedWordAt(int i) {
        return ((short) (getUnsignedWordAt(i) &0xFFFF));
    }
    
    /**
     * returns an unsigned 16bit value at the specified position
     * 
     * uses the dsPICs little endian format
     */
    public int getUnsignedWordAt(int i) {
        // dsPIC uses little endian format
        return ((buf[2*i])&0xff) + (((buf[2*i+1])&0xff)<<8);
    }
    
    /**
     * gets the message's whole content as a string
     * @see #getSubstring
     */
    public String getAsString() {
        StringBuilder ret= new StringBuilder(getLength());
        for(int i=0; i<getLength(); i++)
            ret.append((char) getByteAt(i));
        return ret.toString();
    }
    
    /**
     * returns a string sequence from the message content.
     * non-ASCII chars are handled via casting of byte to char...
     * 
     * @param pos byte position to start string extraction
     * @param length how many bytes to convert
     * @see #getAsString
     */
    public String getSubstring(int pos,int length) {
        StringBuilder ret= new StringBuilder();
        for(int i=pos; i<pos+length; i++)
            ret.append((char) getByteAt(i));
        return ret.toString();
    }
    
    public byte[] getBuffer() {
        return buf;
    }

    @Override
    public String toString() {
        return ".length="+getLength()+".type="+getType();
    }

    @Override
    public StreamCommandMessage clone() {
        StreamCommandMessage ret= new StreamCommandMessage();
        ret.copy(this);
        return ret;
    }

    /**
     * copies the contents of the given message into this message
     * @param src message whose content's (including type) are going to be
     *      copied into this instance
     */
    public void copy(StreamCommandMessage src) {
        setLength(src.getLength());
        setType(src.getType());
        System.arraycopy(src.getBuffer(), 0, getBuffer(), 0, getLength());
    }

}
