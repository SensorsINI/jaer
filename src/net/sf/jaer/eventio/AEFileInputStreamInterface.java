/*
 * AEInputStreamInterface.java
 *
 * Created on February 3, 2006, 4:39 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package net.sf.jaer.eventio;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.IOException;
import java.time.ZoneId;

import net.sf.jaer.aemonitor.AEPacketRaw;

/**
 * The capabilities of the AEFileInputStream. Used in players that use these
 * input streams. This interface can rewind, go forwards as well as backwards,
 * etc, as when reading from a file.
 *
 * @author tobi
 */
public interface AEFileInputStreamInterface extends InputDataFileInterface {

    /**
     * Reads a raw event packet of n events
     *
     * @param n the number of events to read
     * @throws IOException if there is a problem, e.g. end of file
     */
    AEPacketRaw readPacketByNumber(int n) throws IOException;

    /**
     * returns an AEPacketRaw at least dt long up to the max size of the buffer
     * or until end-of-file. Events are read as long as the timestamp until (and
     * including) the event whose timestamp is greater (for dt>0) than
     * startTimestamp+dt, where startTimestamp is the last timestamp from the
     * previous call.
     *
     * @param dt the timestamp different in units of the timestamp (usually us)
     * @return the packet, always at least one event even if there is no event
     * in the interval dt.
     * @throws IOException if there is any problem
     */
    AEPacketRaw readPacketByTime(int dt) throws IOException;

//    /** Reads a packet up to and including {@code time} as its timestamp
//     *@param time in timestamp units
//     *@param forwards true to read forwards, false to read backwards
//     */
//    public AEPacketRaw readPacketToTime(int time, boolean forwards) throws IOException;
    /**
     * Checking for wrapped time exceptions can be disabled for reasons of speed
     * or corrupted data files.
     *
     * @return true if exceptions are checked (default)
     */
    public boolean isNonMonotonicTimeExceptionsChecked();

    /**
     * Sets whether the input stream is checked for timestamp nonmonotonicity.
     *
     * @param yes true to check (default).
     */
    public void setNonMonotonicTimeExceptionsChecked(boolean yes); // TODO should be a general property of any AE input stream, not just files. e.g. network too.

    /**
     * When the file is opened, the filename is parsed to try to extract the
     * date and time the file was created from the filename.
     *
     * @return the time logging was started in ms since 1970
     */
    public long getAbsoluteStartingTimeMs();

    /**
     * Returns the time zone ZoneID of this file
     */
    public ZoneId getZoneId();

    /**
     * @return the duration of the file in us.
     * <p>
     * Assumes data file is timestamped in us. This method fails to provide a
     * sensible value if the timestamp wwaps.
     */
    int getDurationUs();

    /**
     * returns the first timestamp in the stream
     *
     * @return the timestamp
     */
    int getFirstTimestamp();

    /**
     * AEFileInputStream has PropertyChangeSupport. This support fires events on
     * certain events such as "rewind".
     */
    PropertyChangeSupport getSupport();

    /**
     * Adds a listener for property changes
     *
     * @param listener the listener
     */
    public void addPropertyChangeListener(PropertyChangeListener listener);

    /**
     * Removes a listener
     *
     * @param listener the listener
     */
    public void removePropertyChangeListener(PropertyChangeListener listener);

    /**
     * Returns the File that is being read, or null if the instance is
     * constructed from a FileInputStream
     */
    File getFile();

    /**
     * returns the last timestamp in the stream
     *
     * @return last timestamp in file
     */
    int getLastTimestamp();

    /**
     * @return returns the most recent timestamp
     */
    int getMostRecentTimestamp();

    public void setFile(File file);

    /**
     * * Returns the bitmask that is OR'ed with raw addresses; if result is
     * nonzero then a new timestamp offset is memorized and subtracted from
     *
     * @return the timestampResetBitmask
     */
    int getTimestampResetBitmask();

    /**
     * Sets the bitmask that is OR'ed with raw addresses; if result is nonzero
     * then a new timestamp offset is memorized and subtracted from all
     * subsequent timestamps.
     *
     * @param timestampResetBitmask the timestampResetBitmask to set
     */
    public void setTimestampResetBitmask(int timestampResetBitmask);

    /**
     * Closes the stream
     */
    public void close() throws IOException;

    /**
     * @return the present value of the startTimestamp for reading data
     */
    public int getCurrentStartTimestamp();

    /**
     * @param currentStartTimestamp the present value of the startTimestamp for
     * reading data
     */
    public void setCurrentStartTimestamp(int currentStartTimestamp);

    /** Adds or removes a marker at current position() of AEFileInputStream.
     * 
     * @return true if marker added, false if one is removed.
     */
    public boolean toggleMarker();

    public boolean jumpToNextMarker();

    public boolean jumpToPrevMarker();

}
