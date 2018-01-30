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
import com.github.swrirobotics.bags.reader.exceptions.UninitializedFieldException;
import com.github.swrirobotics.bags.reader.messages.serialization.ArrayType;
import com.github.swrirobotics.bags.reader.messages.serialization.BoolType;
import com.github.swrirobotics.bags.reader.messages.serialization.Field;
import com.github.swrirobotics.bags.reader.messages.serialization.MessageType;
import com.github.swrirobotics.bags.reader.messages.serialization.MsgIterator;
import com.github.swrirobotics.bags.reader.messages.serialization.TimeType;
import com.github.swrirobotics.bags.reader.messages.serialization.UInt16Type;
import com.github.swrirobotics.bags.reader.records.ChunkInfo;
import com.github.swrirobotics.bags.reader.records.Connection;
import com.github.swrirobotics.bags.reader.records.MessageData;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.AEFileInputStreamInterface;

/**
 * Reads ROS bag files holding data from https://github.com/uzh-rpg/rpg_dvs_ros
 * recordings, in format http://wiki.ros.org/Bags .
 *
 * @author Tobi
 */
public class RosbagFileInputStream implements AEFileInputStreamInterface {

    private static Logger log = Logger.getLogger("RosbagFileInputStream");

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
    BagFile bagFile = null;
    // the most recently read event timestamp, the first one in file, and the last one in file
    private long mostRecentTimestamp, firstTimestamp, lastTimestamp;
    private long firstTimestampUsAbsolute; // the absolute (ROS) first timestamp in us
    private boolean firstTimestampWasRead=false;
    
    // marks the present read time for packets
    private int currentStartTimestamp;
    private long absoluteStartingTimeMs = 0; // in system time since 1970 in ms
    private int currentMessageNumber = 0;
    private final ApsDvsEventPacket<ApsDvsEvent> eventPacket = new ApsDvsEventPacket<ApsDvsEvent>(ApsDvsEvent.class);
    private AEPacketRaw aePacketRaw = null;
    private PropertyChangeSupport support = new PropertyChangeSupport(this);
    private FileChannel channel = null;
//    private static String[] TOPICS = {"/dvs/events", "/dvs/image_raw", "/dvs/imu"};
    private static String[] TOPICS = {"/dvs/events"};
    private MsgIterator msgIterator = null;
    private List<MessageData> messages = null;
    private List<Connection> conns = null;
    private List<ChunkInfo> chunkInfos = null;
    private int msgPosition=0, numMessages=0;

    public RosbagFileInputStream(File f, AEChip chip) throws BagReaderException {
        setFile(f);
        this.chip = chip;
        log.info("opening rosbag file " + f + " for chip " + chip);
        bagFile = BagReader.readFile(file);
//        bagFile.printInfo(); // debug
    }

    private MsgIterator getMsgIterator() throws BagReaderException {
//        messages=bagFile.getMessages();
        conns = bagFile.getConnections();
        ArrayList<Connection> myConnections = new ArrayList();
        for (Connection conn : conns) {

            String topic = conn.getTopic();
//            log.info("connection "+conn+" has topic "+topic);
            for (String t : TOPICS) {
                if (t.equals(topic)) {
                    log.info("topic matches " + t + "; adding this connection to myConnections. This message has definition " + conn.getMessageDefinition());
                    myConnections.add(conn);
                }
            }

        }
        chunkInfos = bagFile.getChunkInfos();
        try {
            channel = bagFile.getChannel();
        } catch (IOException ex) {
            Logger.getLogger(RosbagFileInputStream.class.getName()).log(Level.SEVERE, null, ex);
            throw new BagReaderException(ex.toString());
        }
        MsgIterator itr = new MsgIterator(chunkInfos, myConnections, channel);
        return itr;
    }

    synchronized private MessageType getNextMsg() throws BagReaderException {
        if (msgIterator == null) {
            msgIterator = getMsgIterator();
        }
        if (msgIterator.hasNext()) {
            msgPosition++;
            return msgIterator.next();
        } else {
            throw new BagReaderException("EOF");
        }
    }

    private AEPacketRaw getNextRawPacket() {
        try {
            MessageType message = getNextMsg();
            Field f = message.getField("events");
            ArrayType data = message.<ArrayType>getField("events");
            List<Field> eventFields = data.getFields();
//            int nEvents = eventFields.size();
            OutputEventIterator<ApsDvsEvent> outItr = eventPacket.outputIterator();
            int sizeY = chip.getSizeY();
            for (Field eventField : eventFields) {
                MessageType eventMsg = (MessageType) eventField;
                // https://github.com/uzh-rpg/rpg_dvs_ros/tree/master/dvs_msgs/msg]
                PolarityEvent e = outItr.nextOutput();
                int x = eventMsg.<UInt16Type>getField("x").getValue();
                int y = eventMsg.<UInt16Type>getField("y").getValue();
                boolean pol = eventMsg.<BoolType>getField("polarity").getValue();
                long tsMs =  eventMsg.<TimeType>getField("ts").getValue().getTime();
                long tsNs =  eventMsg.<TimeType>getField("ts").getValue().getNanos();
                e.x = (short) x;
                e.y = (short) (sizeY - y - 1);
                e.polarity = pol ? PolarityEvent.Polarity.On : PolarityEvent.Polarity.Off;
                e.type = (byte) (pol ? 1 : 0);
                long timestampUsAbsolute = tsMs * 1000 + tsNs / 1000;
                if(!firstTimestampWasRead){
                    firstTimestampUsAbsolute=timestampUsAbsolute;
                    firstTimestampWasRead=true;
                }
                e.timestamp = (int)(timestampUsAbsolute-firstTimestampUsAbsolute);
                mostRecentTimestamp=e.timestamp;
            }
            aePacketRaw = chip.getEventExtractor().reconstructRawPacket(eventPacket);
//            System.out.println(eventPacket.toString());
        } catch (UninitializedFieldException ex) {
            Logger.getLogger(ExampleRosBagReader.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        } catch (BagReaderException bre) {
            log.info(bre.toString());
            return null;
        }
        return aePacketRaw;
    }

    @Override
    synchronized public AEPacketRaw readPacketByNumber(int n) throws IOException {
        return getNextRawPacket();
    }

    @Override
    synchronized public AEPacketRaw readPacketByTime(int dt) throws IOException {
        return getNextRawPacket();
    }

    @Override
    public boolean isNonMonotonicTimeExceptionsChecked() {
        return false;
    }

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
        return (int)firstTimestamp;
    }

    @Override
    public PropertyChangeSupport getSupport() {
        return support;
    }

    @Override
    public float getFractionalPosition() {
        return (float)mostRecentTimestamp/getDurationUs();
    }

    @Override
    public long position() {
        return 0;
    }

    @Override
    public void position(long n) {
        // TODO, will be hard
    }

    @Override
    synchronized public void rewind() throws IOException {
        msgIterator=null;
    }

    @Override
    synchronized public void setFractionalPosition(float frac) {
        // TODO
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
        return (int)lastTimestamp; // TODO, from last DVS event timestamp
    }

    @Override
    public int getMostRecentTimestamp() {
        return (int)mostRecentTimestamp;
    }

    @Override
    public int getTimestampResetBitmask() {
        return 0; // TODO
    }

    @Override
    public void setTimestampResetBitmask(int timestampResetBitmask) {
        // TODO
    }

    @Override
    synchronized public void close() throws IOException {
        if (channel != null && channel.isOpen()) {
            try {
                channel.close();
            } catch (IOException e) {
                // ignore this error
            }
        }
        bagFile = null;
        msgIterator = null;
        conns = null;
        chunkInfos = null;
        file=null;
        System.gc();
    }

    @Override
    public int getCurrentStartTimestamp() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setCurrentStartTimestamp(int currentStartTimestamp) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        close();
    }

}
