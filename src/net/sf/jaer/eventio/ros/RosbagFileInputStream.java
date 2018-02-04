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
import com.github.swrirobotics.bags.reader.TopicInfo;
import com.github.swrirobotics.bags.reader.exceptions.BagReaderException;
import com.github.swrirobotics.bags.reader.exceptions.UninitializedFieldException;
import com.github.swrirobotics.bags.reader.messages.serialization.ArrayType;
import com.github.swrirobotics.bags.reader.messages.serialization.BoolType;
import com.github.swrirobotics.bags.reader.messages.serialization.Field;
import com.github.swrirobotics.bags.reader.messages.serialization.Float64Type;
import com.github.swrirobotics.bags.reader.messages.serialization.MessageType;
import com.github.swrirobotics.bags.reader.messages.serialization.MsgIterator;
import com.github.swrirobotics.bags.reader.messages.serialization.TimeType;
import com.github.swrirobotics.bags.reader.messages.serialization.UInt16Type;
import com.github.swrirobotics.bags.reader.messages.serialization.UInt32Type;
import com.github.swrirobotics.bags.reader.records.ChunkInfo;
import com.github.swrirobotics.bags.reader.records.Connection;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import eu.seebetter.ini.chips.davis.imu.IMUSampleType;
import java.awt.Cursor;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Date;
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
    private int mostRecentTimestamp;
    private long firstTimestamp, lastTimestamp;
    private long firstTimestampUsAbsolute; // the absolute (ROS) first timestamp in us
    private boolean firstTimestampWasRead = false;

    // marks the present read time for packets
    private int currentStartTimestamp;
    private long absoluteStartingTimeMs = 0; // in system time since 1970 in ms
    private int currentMessageNumber = 0;
    private final ApsDvsEventPacket<ApsDvsEvent> eventPacket;
    private AEPacketRaw aePacketRawCollecting = null, aePacketRawOutput = null;
    private final PropertyChangeSupport support = new PropertyChangeSupport(this);
    private FileChannel channel = null;
//    private static final String[] TOPICS = {"/dvs/events"};\
    private static final String TOPIC_HEADER = "/dvs/", TOPIC_EVENTS = "events", TOPIC_IMAGE = "image_raw", TOPIC_IMU = "imu";
    private static String[] TOPICS = {TOPIC_HEADER + TOPIC_EVENTS, TOPIC_HEADER + TOPIC_IMAGE, TOPIC_HEADER + TOPIC_IMU};
    private ArrayList<String> topicList = new ArrayList();
    private ArrayList<String> topicFieldNames = new ArrayList();
    private MsgIterator msgIterator = null;
//    private List<MessageData> messages = null;
    private List<Connection> conns = null;
    private List<ChunkInfo> chunkInfos = null;
    private int msgPosition = 0, numMessages = 0;
    private boolean wasIndexed = false;
    private boolean nonMonotonicTimestampExceptionsChecked = true;
    List<BagFile.MessageIndex> msgIndexes = new ArrayList();

    public RosbagFileInputStream(File f, AEChip chip) throws BagReaderException {
        this.eventPacket = new ApsDvsEventPacket<>(ApsDvsEvent.class);
        setFile(f);
        this.chip = chip;
        for (String s : TOPICS) {
            topicList.add(s);
            topicFieldNames.add(s.substring(s.lastIndexOf("/") + 1)); // strip off header to get to field name for the ArrayType
        }
        log.info("reading rosbag file " + f + " for chip " + chip);
        bagFile = BagReader.readFile(file);
        StringBuilder sb = new StringBuilder("Bagfile information:\n");
        for (TopicInfo topic : bagFile.getTopics()) {
            sb.append(topic.getName() + " \t\t" + topic.getMessageCount()
                    + " msgs \t: " + topic.getMessageType() + " \t"
                    + (topic.getConnectionCount() > 1 ? ("(" + topic.getConnectionCount() + " connections)") : "")
                    + "\n");
        }
        sb.append("duration: " + bagFile.getDurationS() + "s\n");
        sb.append("Chunks: " + bagFile.getChunks().size() + "\n");
        sb.append("Num messages: " + bagFile.getMessageCount() + "\n");
        log.info(sb.toString());
//        bagFile.printInfo(); // debug
    }

//    // causes huge memory usage by building hashmaps internally, using index instead by prescanning file
//    private MsgIterator getMsgIterator() throws BagReaderException {
////        messages=bagFile.getMessages();
//        conns = bagFile.getConnections();
//        ArrayList<Connection> myConnections = new ArrayList();
//        for (Connection conn : conns) {
//
//            String topic = conn.getTopic();
////            log.info("connection "+conn+" has topic "+topic);
//            for (String t : TOPICS) {
//                if (t.equals(topic)) {
////                    log.info("topic matches " + t + "; adding this connection to myConnections. This message has definition " + conn.getMessageDefinition());
//                    log.info("topic matches " + t + "; adding this connection to myConnections");
//                    myConnections.add(conn);
//                }
//            }
//
//        }
//        chunkInfos = bagFile.getChunkInfos();
//        try {
//            channel = bagFile.getChannel();
//        } catch (IOException ex) {
//            Logger.getLogger(RosbagFileInputStream.class.getName()).log(Level.SEVERE, null, ex);
//            throw new BagReaderException(ex.toString());
//        }
//        MsgIterator itr = new MsgIterator(chunkInfos, myConnections, channel);
//        return itr;
//    }
    private class MessageWithIndex {

        public MessageType messageType;
        public BagFile.MessageIndex messageIndex;

        public MessageWithIndex(MessageType messageType, BagFile.MessageIndex messageIndex) {
            this.messageType = messageType;
            this.messageIndex = messageIndex;
        }

    }

    synchronized private MessageWithIndex getNextMsg() throws BagReaderException {
        MessageType msg = null;
        maybeGenerateMessageIndexes();
        try {
            msg = bagFile.getMessageFromIndex(msgIndexes, msgPosition);
        } catch (ArrayIndexOutOfBoundsException e) {
            if (isRepeat()) {
                try {
                    rewind();
                    return getNextMsg();
                } catch (IOException ex) {

                }
            }
        }

        MessageWithIndex rtn = new MessageWithIndex(msg, msgIndexes.get(msgPosition));
        msgPosition++;
        return rtn;
//        if (msgIterator == null) {
//            msgIterator = getMsgIterator();
//        }
//        if (msgIterator.hasNext()) {
//            msgPosition++;
//            return msgIterator.next();
//        } else {
//            throw new BagReaderException("EOF");
//        }
    }

    /**
     * Typical file info about contents of a bag file recorded on Traxxas slash
     * platform INFO: Bagfile information:
     * /davis_ros_driver/parameter_descriptions 1 msgs :
     * dynamic_reconfigure/ConfigDescription /davis_ros_driver/parameter_updates
     * 1 msgs : dynamic_reconfigure/Config /dvs/events 8991 msgs :
     * dvs_msgs/EventArray /dvs/exposure 4751 msgs : std_msgs/Int32
     * /dvs/image_raw 4758 msgs : sensor_msgs/Image /dvs/imu 298706 msgs :
     * sensor_msgs/Imu /dvs_accumulated_events 124 msgs : sensor_msgs/Image
     * /dvs_accumulated_events_edges 124 msgs : sensor_msgs/Image /dvs_rendering
     * 4034 msgs : sensor_msgs/Image /events_off_mean_1 186 msgs :
     * std_msgs/Float32 /events_off_mean_5 56 msgs : std_msgs/Float32
     * /events_on_mean_1 186 msgs : std_msgs/Float32 /events_on_mean_5 56 msgs :
     * std_msgs/Float32 /raw_pwm 2996 msgs : rally_msgs/Pwm /rosout 12 msgs :
     * rosgraph_msgs/Log (4 connections) /rosout_agg 12 msgs : rosgraph_msgs/Log
     * duration: 299.578s Chunks: 0 Num messages: 324994
     */

    /**
     * Gets the next raw packet
     *
     * @return the packet
     */
    private AEPacketRaw getNextRawPacket() {
        OutputEventIterator<ApsDvsEvent> outItr = eventPacket.outputIterator();
        try {
            boolean gotEventsOrFrame = false;
            while (!gotEventsOrFrame) {
                MessageWithIndex message = getNextMsg();
                String pkg = message.messageType.getPackage();
                String type = message.messageType.getType();
                switch (pkg) {
                    case "sensor_msgs":
                        switch (type) {
                            case "Image": { // http://docs.ros.org/api/sensor_msgs/html/index-msg.html
                                MessageType messageType = message.messageType;
//                                List<String> fieldNames = messageType.getFieldNames();
                                MessageType header = messageType.getField("header"); // http://docs.ros.org/api/std_msgs/html/msg/Header.html
                                ArrayType data = messageType.<ArrayType>getField("data");
                                int width = (int) (messageType.<UInt32Type>getField("width").getValue()).intValue();
                                int height = (int) (messageType.<UInt32Type>getField("height").getValue()).intValue();
                                long tsMs = header.<TimeType>getField("stamp").getValue().getTime(); // it's called "stamp" for some reason, not "time" when in header
                                long tsNs = header.<TimeType>getField("stamp").getValue().getNanos();
                                long timestampUsAbsolute = tsMs * 1000 + tsNs / 1000;
                                if (!firstTimestampWasRead) {
                                    firstTimestampUsAbsolute = timestampUsAbsolute;
                                    firstTimestampWasRead = true;
                                }
                                int timestamp = (int) (timestampUsAbsolute - firstTimestampUsAbsolute);
                                if (data == null) {
                                    log.warning("got null data for field events in message " + message);
                                    continue;
                                }
                                gotEventsOrFrame = true;
                                byte[] bytes = data.getAsBytes();
                                int idx = 0;
                                int sizeY = chip.getSizeY();
                                ApsDvsEvent e = null;
                                // construct frames as events, so that reconstuction as raw packet results in frame again. what a hack...
                                e = outItr.nextOutput();
                                e.setReadoutType(ApsDvsEvent.ReadoutType.SOF);
                                e.x = (short) 0;
                                e.y = (short) 0;
                                e.setTimestamp(timestamp);
                                e = outItr.nextOutput();
                                e.setReadoutType(ApsDvsEvent.ReadoutType.SOE);
                                e.x = (short) 0;
                                e.y = (short) 0;
                                e.setTimestamp(timestamp);
                                for (int f = 0; f < 2; f++) { // reset/signal pixels samples
                                    for (int y = 0; y < height; y++) {
                                        for (int x = 0; x < width; x++) {
                                            e = outItr.nextOutput();
                                            e.setReadoutType(f == 0 ? ApsDvsEvent.ReadoutType.ResetRead : ApsDvsEvent.ReadoutType.SignalRead);
                                            e.x = (short) x;
                                            e.y = (short) (sizeY - y - 1);
                                            e.setAdcSample(f == 0 ? 255 : (255 - (0xff & bytes[idx++])));
                                            e.setTimestamp(timestamp);
                                        }
                                    }
                                }
                                e = outItr.nextOutput();
                                e.setReadoutType(ApsDvsEvent.ReadoutType.EOE);
                                e.x = (short) 0;
                                e.y = (short) 0;
                                e.setTimestamp(timestamp); // TODO should really be end of exposure timestamp, have to get that from last exposure message
                                e = outItr.nextOutput();
                                e.setReadoutType(ApsDvsEvent.ReadoutType.EOF);
                                e.x = (short) 0;
                                e.y = (short) 0;
                                e.setTimestamp(timestamp);

                            }
                            break;
                            case "Imu": { // http://docs.ros.org/api/sensor_msgs/html/index-msg.html
                                MessageType messageType = message.messageType;
//                                List<String> fieldNames = messageType.getFieldNames();
//                                for(String s:fieldNames){
//                                    System.out.println("fieldName: "+s);
//                                }
//                                List<String> fieldNames = messageType.getFieldNames();
                                MessageType header = messageType.getField("header"); // http://docs.ros.org/api/std_msgs/html/msg/Header.html
                                long tsMs = header.<TimeType>getField("stamp").getValue().getTime(); // it's called "stamp" for some reason, not "time" when in header
                                long tsNs = header.<TimeType>getField("stamp").getValue().getNanos();
                                long timestampUsAbsolute = tsMs * 1000 + tsNs / 1000;
                                if (!firstTimestampWasRead) {
                                    firstTimestampUsAbsolute = timestampUsAbsolute;
                                    firstTimestampWasRead = true;
                                }
                                int timestamp = (int) (timestampUsAbsolute - firstTimestampUsAbsolute);
                                List<Field> fields = null;
                                MessageType angular_velocity = messageType.getField("angular_velocity");
//                                List<String> angvelfields=angular_velocity.getFieldNames();
//                                for(String s:angvelfields){
//                                    System.out.println("angular_velocity field: "+s);
//                                }
                                float xrot = (float) (angular_velocity.<Float64Type>getField("x").getValue().doubleValue());
                                float yrot = (float) (angular_velocity.<Float64Type>getField("y").getValue().doubleValue());
                                float zrot = (float) (angular_velocity.<Float64Type>getField("z").getValue().doubleValue());
                                MessageType linear_acceleration = messageType.getField("linear_acceleration");
//                                List<String> linaccfields=linear_acceleration.getFieldNames();
//                                for(String s:linaccfields){
//                                    System.out.println("linaccfields field: "+s);
//                                }
                                float xacc = (float) (angular_velocity.<Float64Type>getField("x").getValue().doubleValue());
                                float yacc = (float) (angular_velocity.<Float64Type>getField("y").getValue().doubleValue());
                                float zacc = (float) (angular_velocity.<Float64Type>getField("z").getValue().doubleValue());
                                short[] buf = new short[7];
                                buf[IMUSampleType.ax.code] = (short) (xacc / IMUSample.getAccelSensitivityScaleFactorGPerLsb()); // TODO set these scales from caer parameter messages in stream
                                buf[IMUSampleType.ay.code] = (short) (yacc / IMUSample.getAccelSensitivityScaleFactorGPerLsb());
                                buf[IMUSampleType.az.code] = (short) (zacc / IMUSample.getAccelSensitivityScaleFactorGPerLsb());
                                buf[IMUSampleType.gx.code] = (short) (xrot / IMUSample.getGyroSensitivityScaleFactorDegPerSecPerLsb());
                                buf[IMUSampleType.gy.code] = (short) (yrot / IMUSample.getGyroSensitivityScaleFactorDegPerSecPerLsb());
                                buf[IMUSampleType.gz.code] = (short) (zrot / IMUSample.getGyroSensitivityScaleFactorDegPerSecPerLsb());
                                ApsDvsEvent e = null;
                                e = outItr.nextOutput();
                                IMUSample imuSample = new IMUSample(timestamp, buf);
                                e.setImuSample(imuSample);
                            }
                            break;
                        }
                        break;
                    case "dvs_msgs":
                        switch (type) {
                            case "EventArray":
                                MessageType messageType = message.messageType;
                                ArrayType data = messageType.<ArrayType>getField("events");
                                if (data == null) {
                                    log.warning("got null data for field events in message " + message);
                                    continue;
                                }
                                List<Field> eventFields = data.getFields();
                                gotEventsOrFrame = true;

                                int sizeY = chip.getSizeY();
                                int eventIdxThisPacket = 0;
                                //            int nEvents = eventFields.size();
                                for (Field eventField : eventFields) {
                                    MessageType eventMsg = (MessageType) eventField;
                                    // https://github.com/uzh-rpg/rpg_dvs_ros/tree/master/dvs_msgs/msg]
                                    int x = eventMsg.<UInt16Type>getField("x").getValue();
                                    int y = eventMsg.<UInt16Type>getField("y").getValue();
                                    boolean pol = eventMsg.<BoolType>getField("polarity").getValue();
                                    long tsMs = ((Date) eventMsg.<TimeType>getField("ts").getValue()).getTime(); // this Timestamp ms includes the nanos already!, So cast it to Date to get only the ms part from Date
                                    long tsNs = eventMsg.<TimeType>getField("ts").getValue().getNanos(); // gets the fractional seconds
                                    long timestampUsAbsolute = tsMs * 1000 + tsNs / 1000; // ms*1000 =us and ns/1000=us, not sure about overflow however TODO check
                                    if (!firstTimestampWasRead) {
                                        firstTimestampUsAbsolute = timestampUsAbsolute;
                                        firstTimestampWasRead = true;
                                    }
                                    int timestamp = (int) (timestampUsAbsolute - firstTimestampUsAbsolute);
                                    final int dt = timestamp - mostRecentTimestamp;
                                    if (dt < 0 && nonMonotonicTimestampExceptionsChecked) {
                                        log.warning("Discarding event with nonmonotonic timestamp detected for event " + eventIdxThisPacket + " in this message; delta time=" + dt);
                                        eventIdxThisPacket++;
                                        mostRecentTimestamp = timestamp;
                                        continue;
                                    }
                                    mostRecentTimestamp = timestamp;
                                    ApsDvsEvent e = outItr.nextOutput();
                                    e.setReadoutType(ApsDvsEvent.ReadoutType.DVS);
                                    e.timestamp = timestamp;
                                    e.x = (short) x;
                                    e.y = (short) (sizeY - y - 1);
                                    e.polarity = pol ? PolarityEvent.Polarity.On : PolarityEvent.Polarity.Off;
                                    e.type = (byte) (pol ? 1 : 0);
                                    eventIdxThisPacket++;
                                }
                        }
                        break;
                }

            }
        } catch (UninitializedFieldException ex) {
            Logger.getLogger(ExampleRosBagReader.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        } catch (BagReaderException bre) {
            log.info(bre.toString());
            return null;
        }
        aePacketRawCollecting = chip.getEventExtractor().reconstructRawPacket(eventPacket);
        return aePacketRawCollecting;
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
        return nonMonotonicTimestampExceptionsChecked;
    }

    @Override
    public void setNonMonotonicTimeExceptionsChecked(boolean yes) {
        nonMonotonicTimestampExceptionsChecked = yes;
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
        return (int) firstTimestamp;
    }

    @Override
    public PropertyChangeSupport getSupport() {
        return support;
    }

    @Override
    public float getFractionalPosition() {
        return (float) mostRecentTimestamp / getDurationUs();
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
        msgPosition = 0;
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
        return (int) lastTimestamp; // TODO, from last DVS event timestamp
    }

    @Override
    public int getMostRecentTimestamp() {
        return (int) mostRecentTimestamp;
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
        file = null;
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

    private void maybeGenerateMessageIndexes() throws BagReaderException {
        if (wasIndexed) {
            return;
        }
        log.info("creating index for all topics");
        if (chip.getAeViewer() != null) {
            chip.getAeViewer().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }
        msgIndexes = bagFile.generateIndexesForTopicList(topicList);
        if (chip.getAeViewer() != null) {
            chip.getAeViewer().setCursor(Cursor.getDefaultCursor());
        }
        wasIndexed = true;
    }

}
