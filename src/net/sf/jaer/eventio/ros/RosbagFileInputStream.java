/*
 * Copyright (C) 2018 Tobi.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package net.sf.jaer.eventio.ros;

import com.github.swrirobotics.bags.reader.BagFile;
import com.github.swrirobotics.bags.reader.BagReader;
import com.github.swrirobotics.bags.reader.exceptions.BagReaderException;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import org.apache.log4j.Logger;

/**
 * Reads ROS bag files holding data from https://github.com/uzh-rpg/rpg_dvs_ros
 * recordings, in format http://wiki.ros.org/Bags .
 *
 * @author Tobi
 */
public class RosbagFileInputStream implements AEFileInputStreamInterface {

    private static Logger log = Logger.getLogger(RosbagFileInputStream.class);

    /**
     * File name extension for ROS bag files, excluding ".", i.e. "bag". Note
     * this lack of . is different than for AEDataFile.DATA_FILE_EXTENSION.
     */
    public static final String DATA_FILE_EXTENSION = "bag";

    /**
     * The AEChip object associated with this stream. This field was added for
     * supported jAER 3.0 format files to support translating bit locations in
     * events.
     */
    private AEChip chip = null;
    private File file = null;
    BagReader bagReader = null;
    BagFile bagFile = null;
    private int mostRecentTimestamp, firstTimestamp, lastTimestamp;
    // marks the present read time for packets
    private int currentStartTimestamp;
    private long absoluteStartingTimeMs = 0;
    private int currentMessageNumber = 0;
    private final EventPacket<PolarityEvent> eventPacket = new EventPacket(PolarityEvent.class);
    private final AEPacketRaw rawPacket = new AEPacketRaw();
    private PropertyChangeSupport support=new PropertyChangeSupport(this);

    public RosbagFileInputStream(File f, AEChip chip) throws BagReaderException {
        setFile(f);
        this.chip = chip;
        log.info("opening rosbag file " + f + " for chip " + chip);
        bagFile = BagReader.readFile(file);
//        bagFile.printInfo();
    }

    @Override
    public AEPacketRaw readPacketByNumber(int n) throws IOException {
        return rawPacket;
    }

    @Override
    public AEPacketRaw readPacketByTime(int dt) throws IOException {
        return rawPacket;
    }

    @Override
    public boolean isNonMonotonicTimeExceptionsChecked() {
return false;    }

    @Override
    public void setNonMonotonicTimeExceptionsChecked(boolean yes) {
    }

    @Override
    public long getAbsoluteStartingTimeMs() {
        return bagFile.getStartTime().getTime();
    }

    @Override
    public int getDurationUs() {
        return (int) (bagFile.getDurationS() * 1e6);
    }

    @Override
    public int getFirstTimestamp() {
        return 0; // TODO from first DVS packet
    }

    @Override
    public PropertyChangeSupport getSupport() {
        return support;
    }

    @Override
    public float getFractionalPosition() {
        return 0;
    }

    @Override
    public long position() {
        return 0;
    }

    @Override
    public void position(long n) {
    }

    @Override
    public void rewind() throws IOException {
    }

    @Override
    public void setFractionalPosition(float frac) {
    }

    @Override
    public long size() {
        return 0;
    }

    @Override
    public void clearMarks() {
    }

    @Override
    public long setMarkIn() {
        return 0;
    }

    @Override
    public long setMarkOut() {
        return 0;
    }

    @Override
    public long getMarkInPosition() {
        return 0;
    }

    @Override
    public long getMarkOutPosition() {
        return 0;
    }

    @Override
    public boolean isMarkInSet() {
        return false;
    }

    @Override
    public boolean isMarkOutSet() {
        return false;
    }

    @Override
    public void setRepeat(boolean repeat) {
    }

    @Override
    public boolean isRepeat() {
        return true;
    }

    /**
     * Returns the File that is being read, or null if the instance is
     * constructed from a FileInputStream
     */
    public File getFile() {
        return file;
    }

    /**
     * Sets the File reference but doesn't open the file
     */
    public void setFile(File f) {
        this.file = f;
    }

    @Override
    public int getLastTimestamp() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public int getMostRecentTimestamp() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public int getTimestampResetBitmask() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setTimestampResetBitmask(int timestampResetBitmask) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public int getCurrentStartTimestamp() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setCurrentStartTimestamp(int currentStartTimestamp) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
