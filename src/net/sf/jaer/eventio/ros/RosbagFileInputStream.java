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
import com.github.swrirobotics.bags.reader.messages.serialization.Float32Type;
import com.github.swrirobotics.bags.reader.messages.serialization.Float64Type;
import com.github.swrirobotics.bags.reader.messages.serialization.Int32Type;
import com.github.swrirobotics.bags.reader.messages.serialization.MessageType;
import com.github.swrirobotics.bags.reader.messages.serialization.TimeType;
import com.github.swrirobotics.bags.reader.messages.serialization.UInt16Type;
import com.google.common.collect.HashMultimap;
import eu.seebetter.ini.chips.davis.DavisBaseCamera;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import eu.seebetter.ini.chips.davis.imu.IMUSampleType;
import java.awt.Point;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ProgressMonitor;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.AEFileInputStreamInterface;
import net.sf.jaer.eventio.AEInputStream;
import net.sf.jaer.eventio.NonMonotonicTimeException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;

/**
 * Reads ROS bag files holding data from https://github.com/uzh-rpg/rpg_dvs_ros
 * recordings, in format http://wiki.ros.org/Bags .
 *
 * @author Tobi
 */
public class RosbagFileInputStream implements AEFileInputStreamInterface, RosbagTopicMessageSupport {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private final PropertyChangeSupport support = new PropertyChangeSupport(this);

    /**
     * File name extension for ROS bag files, excluding ".", i.e. "bag". Note
     * this lack of . is different than for AEDataFile.DATA_FILE_EXTENSION.
     */
    public static final String DATA_FILE_EXTENSION = "bag";
    // for converting ROS units to jAER units
    private static final float DEG_PER_RAD = 180f / (float) Math.PI, G_PER_MPS2 = 1 / 9.8f;

    /**
     * The AEChip object associated with this stream. This field was added for
     * supported jAER 3.0 format files to support translating bit locations in
     * events.
     */
    private AEChip chip = null;
    private File file = null;
    BagFile bagFile = null;
    // the most recently read event timestamp and the largest one read so far in this playback cycle
    private int mostRecentTimestamp, largestTimestampReadSinceRewind = Integer.MIN_VALUE;
    // first and last timestamp in the entire recording
    private long firstTimestampUs, lastTimestampUs;
    private double startAbsoluteTimeS, endAbsoluteTimeS, durationS;
    private Timestamp startAbsoluteTimestamp;
    private long firstTimestampUsAbsolute; // the absolute (ROS) first timestamp in us, set in determineEarliestTimestampsAndDataTypesPresent
    private boolean firstTimestampWasRead = false;
    private int firstDvsTimestampRelativeUs = 0, firstApsTimestampRelativeUs = 0, firstImuTimestampRelativeUs = 0; // TODO check if better Integer.MIN_VALUE

    // marks the present read time for packets
    private int currentStartTimestamp;
    private final ApsDvsEventPacket<ApsDvsEvent> eventPacket;
    private AEPacketRaw aePacketRawBuffered = new AEPacketRaw(), aePacketRawOutput = new AEPacketRaw(), aePacketRawTmp = new AEPacketRaw();
    private AEPacketRaw emptyPacket = new AEPacketRaw();
    private int nextMessageNumber = 0, numMessages = 0;

    private long absoluteStartingTimeMs = 0; // in system time since 1970 in ms
    /**
     * The ZoneID of this file; for ROS bag files there is no recorded time zone
     * so the zoneId is systemDefault()
     */
    private ZoneId zoneId = ZoneId.systemDefault();
    private FileChannel channel = null;

//    private static final String[] TOPICS = {"/dvs/events"};\
//    private static String[] TOPICS = {TOPIC_HEADER + TOPIC_EVENTS, TOPIC_HEADER + TOPIC_IMAGE};
    private HashSet<String> topicSet = new HashSet();
    private ArrayList<String> topicFieldNames = new ArrayList();
    private boolean wasIndexed = false;
    private List<BagFile.MessageIndex> msgIndexes = new ArrayList();
    private HashMultimap<String, PropertyChangeListener> msgListeners = HashMultimap.create();
    private boolean firstReadCompleted = false;
    private ArrayList<String> extraTopics = null; // extra topics that listeners can subscribe to
    private String rosbagInfoString = null;

    private boolean nonMonotonicTimestampExceptionsChecked = true;
    private boolean nonMonotonicTimestampDetected = false; // flag set by nonmonotonic timestamp if detection enabled
    private boolean rewindFlag = false;

    // FIFOs to buffer data so that we only let out data from any stream if there is definitely later data from both other streams
    private MutableBoolean hasDvs = new MutableBoolean(false), hasAps = new MutableBoolean(false), hasImu = new MutableBoolean(false); // flags to say what data is in this stream
    private int lastDvsTimestamp = 0, lastApsTimestamp = 0, lastImuTimestamp = 0; // used to check monotonicity
    private AEFifo dvsFifo = new AEFifo("DVS"), apsFifo = new AEFifo("APS"), imuFifo = new AEFifo("IMU");
    private MutableBoolean[] hasDvsApsImu = {hasDvs, hasAps, hasImu};
    private AEFifo[] aeFifos = {dvsFifo, apsFifo, imuFifo};
    private int numStreamsWithData = 0; // how many types does this file actually have?
    private int MAX_RAW_EVENTS_BUFFER_SIZE = 1000000;

    private static final String TOPIC_EVENTS = "events", TOPIC_IMAGE_RAW = "image_raw", TOPIC_IMU = "imu", TOPIC_EXPOSURE = "exposure";
//    private static String[] STANARD_TOPICS = {TOPIC_EVENTS, TOPIC_IMAGE_RAW/*, TOPIC_IMU, TOPIC_EXPOSURE*/}; // tobi 4.1.21 commented out the IMU and EXPOSURE (never seen) topics since they cause problems with nonmonotonic timestamps in the MVSEC recrordings. Cause unknown. TODO fix IMU reading.
    private static String[] STANDARD_TOPICS = {TOPIC_EVENTS, TOPIC_IMAGE_RAW, TOPIC_IMU, TOPIC_EXPOSURE}; // tobi 4.1.21 commented out the IMU and EXPOSURE (never seen) topics since they cause problems with nonmonotonic timestamps in the MVSEC recrordings. Cause unknown. TODO fix IMU reading.

    private static final String RPG_TOPIC_HEADER = "/dvs/", MVSEC_TOPIC_HEADER = "/davis/left/", EV_IMO_TOPIC_HEADER = "/samsung/camera/"; // TODO arbitrarily choose left camera for MVSEC for now

    private enum RosbagFileType {
        RPG(RPG_TOPIC_HEADER), MVSEC(MVSEC_TOPIC_HEADER), EV_IMO(EV_IMO_TOPIC_HEADER), Unknown("???");
        private String header;

        private RosbagFileType(String header) {
            this.header = header;
        }

        @Override
        public String toString() {
            return String.format("RosbagFileType: %s with topic header \"%s\"", super.toString(), header);
        }

    }

    // two types of topics depending on format of rosbag
    // RPG is from https://github.com/uzh-rpg/rpg_dvs_ros/tree/master/dvs_msgs/msg
    // MVSEC is from https://daniilidis-group.github.io/mvsec/data_format/
    RosbagFileType rosbagFileType = RosbagFileType.Unknown;

    /**
     * Interval for logging warnings about nonmonotonic timestamps.
     */
    public static final int NONMONOTONIC_TIMESTAMP_WARNING_INTERVAL = 10000;
    private int nonmonotonicTimestampCounter = 0;

    private int lastExposureUs; // holds last exposure value (in us?) to use for creating SOE and EOE events for frames
    private int markIn = 0;
    private int markOut;
    private boolean repeatEnabled = true;
    private Timestamp lastEndOfExposureTimestamp = null;  // stores an end-of-frame-exposure timestamp to put on APS frame events

    /**
     * Makes a new instance for a file and chip. A progressMonitor can pop up a
     * dialog for the long indexing operation
     *
     * @param f the file
     * @param chip the AEChip
     * @param progressMonitor an optional ProgressMonitor, set to null if not
     * desired
     * @throws BagReaderException
     * @throws java.lang.InterruptedException if constructing the stream which
     * requires indexing the topics takes a long time and the operation is
     * canceled
     */
    public RosbagFileInputStream(File f, AEChip chip, ProgressMonitor progressMonitor) throws BagReaderException, InterruptedException {
        this.eventPacket = new ApsDvsEventPacket<>(ApsDvsEvent.class);
        setFile(f);
        this.chip = chip;
        log.setLevel(Level.FINE);

        log.info("reading rosbag file " + f + " for chip " + chip);
        bagFile = BagReader.readFile(file);
        StringBuilder sb = new StringBuilder("Bagfile information:\nTopic_name\tMessage_count\tMessage_type\n------------------------\n");
        for (TopicInfo topic : bagFile.getTopics()) {
            sb.append(topic.getName() + " \t\t" + topic.getMessageCount()
                    + " msgs \t: " + topic.getMessageType() + " \t"
                    + (topic.getConnectionCount() > 1 ? ("(" + topic.getConnectionCount() + " connections)") : "")
                    + "\n");
            if (topic.getName().contains(RosbagFileType.MVSEC.header)) {
                rosbagFileType = RosbagFileType.MVSEC;
                log.warning("MVSEC bag: Arbitarily using camera input " + MVSEC_TOPIC_HEADER);
                for (String s : STANDARD_TOPICS) {
                    String t = rosbagFileType.header + s;
                    topicSet.add(t);
                    topicFieldNames.add(t.substring(t.lastIndexOf("/") + 1)); // strip off header to get to field name for the ArrayType
                }
            } else if (topic.getName().contains(RosbagFileType.RPG.header)) {
                rosbagFileType = RosbagFileType.RPG;
                log.warning("RPG dataset bag: Arbitarily using camera input " + RPG_TOPIC_HEADER);
                for (String s : STANDARD_TOPICS) {
                    String t = rosbagFileType.header + s;
                    topicSet.add(t);
                    topicFieldNames.add(t.substring(t.lastIndexOf("/") + 1)); // strip off header to get to field name for the ArrayType
                }
            } else if (topic.getName().contains(RosbagFileType.EV_IMO.header)) {
                rosbagFileType = RosbagFileType.EV_IMO;
                log.warning("EV_IMO bag: Arbitarily using camera input " + EV_IMO_TOPIC_HEADER);
                String t = rosbagFileType.header + TOPIC_EVENTS;
                topicSet.add(t);
                topicFieldNames.add(t.substring(t.lastIndexOf("/") + 1)); // strip off header to get to field name for the ArrayType
            }
        }
        sb.append("Duration: " + bagFile.getDurationS() + "s\n");
        sb.append("Chunks: " + bagFile.getChunks().size() + "\n");
        sb.append("Num messages: " + bagFile.getMessageCount() + "\n");
        sb.append("RosBag File type is detected as " + rosbagFileType);
        rosbagInfoString = sb.toString();

        log.info(rosbagInfoString);
        generateMessageIndexes(progressMonitor);
        try {
            determineEarliestTimestampsAndDataTypesPresent();
            firstTimestampWasRead = true;
        } catch (UninitializedFieldException ex) {
            log.log(Level.WARNING, "could not determine earliest timestamp, caught {0}", ex.toString());
        }
    }

    @Override
    public Collection<String> getMessageListenerTopics() {
        return msgListeners.keySet();
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
//                    myConnections.appendCopyOfEventReferences(conn);
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
    /**
     * Adds information to a message from the index entry for it
     */
    public class MessageWithIndex {

        public MessageType messageType;
        public BagFile.MessageIndex messageIndex;
        public int ourIndex;

        public MessageWithIndex(MessageType messageType, BagFile.MessageIndex messageIndex, int ourIndex) {
            this.messageType = messageType;
            this.messageIndex = messageIndex;
            this.ourIndex = ourIndex;
        }

        @Override
        public String toString() {
            return String.format("MessagesWithIndex with ourIndex=%d, MessageType [package=%s, name=%s, type=%s], MessageIndex %s",
                    ourIndex,
                    messageType.getPackage(), messageType.getName(), messageType.getType(),
                    messageIndex.toString()); //To change body of generated methods, choose Tools | Templates.
        }

    }

    /**
     * Return a particular message with its index in the file
     *
     * @param msgIndex the index of the message
     * @return the MessageWithIndex
     * @throws BagReaderException
     * @throws EOFException
     */
    synchronized private MessageWithIndex getMsg(int msgIndex) throws BagReaderException, EOFException {
        if (msgIndex < 0 || msgIndex >= numMessages) {
            throw new IllegalArgumentException(String.format("msgIndex=%,d is out of bounds (0,%,d)", msgIndex, numMessages));
        }
        MessageType msg = null;
        try {
            if (nextMessageNumber == markOut) { // TODO check exceptions here for markOut set before markIn
                throw new EOFException("Hit OUT marker at messange number " + markOut);
            }
            msg = bagFile.getMessageFromIndex(msgIndexes, msgIndex);
        } catch (IndexOutOfBoundsException e) {
            throw new EOFException(String.format("Hit IndexOutOfBoundsException: %s", e.toString()));
        } catch (IOException e) {
            throw new EOFException("Hit IOException at end of file");
        }

        MessageWithIndex rtn = new MessageWithIndex(msg, msgIndexes.get(msgIndex), msgIndex);
        return rtn;
    }

    private MessageWithIndex getNextMsg() throws BagReaderException, EOFException {
        if (nextMessageNumber >= numMessages) {
            throw new EOFException(String.format("tried to read message %,d past end of file", nextMessageNumber));
        }
        MessageWithIndex rtn = getMsg(nextMessageNumber);
        nextMessageNumber++; // inc after read so nexx read gets next msg
        return rtn;
    }

    private MessageWithIndex getPrevMsg() throws BagReaderException, EOFException {
        nextMessageNumber--; // dec before read so we get the previous packet, fwd-rev-fwd gives the same packet 3 times
        if (nextMessageNumber < 0) {
            throw new EOFException(String.format("trying to read message from %,d before start of file", nextMessageNumber));
        }
        MessageWithIndex rtn = getMsg(nextMessageNumber);
        return rtn;
    }

    private void determineEarliestTimestampsAndDataTypesPresent() throws UninitializedFieldException {
        int numMsgsToRead = 1000;
        if (numMsgsToRead >= numMessages) {
            numMsgsToRead = numMessages;
        }
        log.info(String.format("Scanning first %,d out of total %,d messages to determine data types present and their earliest timestamps", numMsgsToRead, numMessages));

        log.info(String.format("Determining earliest timestamp by scanning first %d messages", numMsgsToRead));
        long earliest = Long.MAX_VALUE;
        Timestamp firstDvsTimestampROS = null, firstApsTimestampROS = null, firstImuTimestampROS = null;
        for (int i = 0; i < numMsgsToRead; i++) {
            try {
                MessageWithIndex m = getMsg(i);
                switch (m.messageType.getType()) {
                    case "EventArray":
                        hasDvs.setTrue();
                        firstDvsTimestampROS = m.messageIndex.timestamp;
                        break;
                    case "Imu":
                        hasImu.setTrue();
                        firstImuTimestampROS = m.messageIndex.timestamp;
                        break;
                    case "Image":
                        hasAps.setTrue();
                        firstApsTimestampROS = m.messageIndex.timestamp;
                        break;
                    default:
                        log.info("got unexpected type " + m);
                }
                try {
                    long tsUs = getMessageTimestamp(m);
                    if (tsUs < earliest) {
                        earliest = tsUs;
                        log.info(String.format("earlist timestamp updated to %,d us", earliest));
                    }
                } catch (UninitializedFieldException e) {
                    log.warning(String.format("Message %s did not set timestamp", m.toString()));
                    continue;
                }
                if (hasDvs.isTrue() && hasAps.isTrue() && hasImu.isTrue()) {
                    log.info("breaking out because we got Dvs,Imu,Aps messages already");
                    break; // stop now because we got all types of possible things with timestamps
                }
            } catch (BagReaderException | EOFException ex) {
                break;
            }
        }
        firstTimestampUsAbsolute = earliest;
        numStreamsWithData = 0;
        if (firstApsTimestampROS != null) {
            numStreamsWithData++;
            firstApsTimestampRelativeUs = getTimestampUsRelative(firstApsTimestampROS, false, false);
        }
        if (firstDvsTimestampROS != null) {
            numStreamsWithData++;
            firstDvsTimestampRelativeUs = getTimestampUsRelative(firstDvsTimestampROS, false, false);
        }
        if (firstImuTimestampROS != null) {
            numStreamsWithData++;
            firstImuTimestampRelativeUs = getTimestampUsRelative(firstImuTimestampROS, false, false);
        }
        lastDvsTimestamp = firstDvsTimestampRelativeUs; // important to set first frame timestamp to first DVS timestamp (zero if no DVS present)
        log.info(String.format("%s: hasDvs=%s (earliest timestamp %,d) hasAPS=%s (earliest timestamp %,d) hasIMU=%s (earliest timestamp %,d)",
                file.toString(),
                hasDvs, firstDvsTimestampRelativeUs,
                hasAps, firstApsTimestampRelativeUs,
                hasImu, firstImuTimestampRelativeUs
        ));

    }

    /**
     * Get timestamp of message in absolute time in us since 1970
     *
     * @param message
     * @return us timestamp since 1970
     * @throws UninitializedFieldException if the timestamp is null or 0
     */
    private long getMessageTimestamp(MessageWithIndex message) throws UninitializedFieldException {
        String pkg = message.messageType.getPackage();
        String type = message.messageType.getType();
        Timestamp timestamp = null;
        switch (pkg) {
            case "std_msgs": {
                timestamp = message.messageIndex.timestamp;
            }
            break;
            case "sensor_msgs": // for RPG and MVSEC
                switch (type) {
                    case "Image": { // http://docs.ros.org/api/sensor_msgs/html/index-msg.html
                        // Make sure the image is from APS, otherwise some other image topic will be also processed here.
                        MessageType header = message.messageType.getField("header"); // http://docs.ros.org/api/std_msgs/html/msg/Header.html
                        timestamp = header.<TimeType>getField("stamp").getValue();

                    }
                    break;
                    case "Imu": { // http://docs.ros.org/api/sensor_msgs/html/index-msg.html
                        MessageType messageType = message.messageType;
                        MessageType header = messageType.getField("header"); // http://docs.ros.org/api/std_msgs/html/msg/Header.html
                        timestamp = header.<TimeType>getField("stamp").getValue();

                    }
                    break;
                }
                break;
            case "dvs_msgs":
            case "samsung_event_msgs": // for EV_IMO
                switch (type) {
                    case "EventArray":
                        MessageType messageType = message.messageType;
                        ArrayType data = messageType.<ArrayType>getField("events");
                        if (data == null) {
                            log.warning("got null data for field events in message " + message);
                            break;
                        }
                        List<Field> eventFields = data.getFields();
                        for (Field eventField : eventFields) {
                            MessageType eventMsg = (MessageType) eventField;
                            timestamp = (Timestamp) eventMsg.<TimeType>getField("ts").getValue();
                            break; // just get first timestamp
                        }
                        break;
                }
                break;
        }
        if (timestamp == null) {
            throw new UninitializedFieldException();
        }
        long tsNs = timestamp.getNanos(); // gets the fractional seconds in ns
        // https://docs.oracle.com/javase/8/docs/api/java/sql/Timestamp.html "Only integral seconds are stored in the java.util.Date component. The fractional seconds - the nanos - are separate."
        long tsMs = timestamp.getTime(); // the time in ms including ns, i.e. time(s)*1000+ns/1000000. 
        long timestampUsAbsolute = (1000000L * (tsMs / 1000)) + tsNs / 1000L; // truncate ms back to s, then turn back to us, then append fractional part of s in us

//        log.info(String.format("Message %s timestampUsAbsolute=%,d",
//                message, timestampUsAbsolute));
        return timestampUsAbsolute;
    }

    /**
     * Given ROS Timestamp, this method computes the us timestamp relative to
     * the first timestamp in the recording
     *
     * @param timestamp a ROS Timestamp from a Message, either header or DVS
     * event. Not the same as
     * {@link com.github.swrirobotics.bags.reader.BagFile#getStartTime()}
     * @param updateLargestTimestamp true if we want to update the largest
     * timestamp with this value, false if we leave it unchanged
     * @param checkNonmonotonic checks if timestamp is earlier that last one
     * read. Set false for reverse mode.
     *
     * @return timestamp for jAER in us
     */
    private int getTimestampUsRelative(Timestamp timestamp, boolean updateLargestTimestamp, boolean checkNonmonotonic) {
//        updateLargestTimestamp = true; // TODO hack before removing
        long tsNs = timestamp.getNanos(); // gets the fractional seconds in ns
        // https://docs.oracle.com/javase/8/docs/api/java/sql/Timestamp.html "Only integral seconds are stored in the java.util.Date component. The fractional seconds - the nanos - are separate."
        long tsMs = timestamp.getTime(); // the time in ms including ns, i.e. time(s)*1000+ns/1000000. 
        long timestampUsAbsolute = (1000000L * (tsMs / 1000)) + tsNs / 1000L; // truncate ms back to s, then turn back to us, then append fractional part of s in us
        if (!firstTimestampWasRead) {
            firstTimestampUsAbsolute = timestampUsAbsolute;
            firstTimestampWasRead = true;
        }
        int ts = (int) (timestampUsAbsolute - firstTimestampUsAbsolute);
//        if (ts == 0) {
//            log.warning("zero timestamp detected for Image ");
//        }
        if (updateLargestTimestamp && ts >= largestTimestampReadSinceRewind) {
            largestTimestampReadSinceRewind = ts;
        }
        final int dt = ts - mostRecentTimestamp;
        if (checkNonmonotonic && dt < 0 && nonMonotonicTimestampExceptionsChecked) {
            if (nonmonotonicTimestampCounter % NONMONOTONIC_TIMESTAMP_WARNING_INTERVAL == 0) {
                log.warning(String.format("Nonmonotonic timestamp=%s with dt=%,d us; skipping next %,d warnings", timestamp, dt, NONMONOTONIC_TIMESTAMP_WARNING_INTERVAL));
            }
            nonmonotonicTimestampCounter++;
//            ts = largestTimestampReadSinceRewind; // replace actual timestamp with largest one so far
// removed since it causes endless problems with all timestamps being set the same after playing backwards and then going forrwards
            nonMonotonicTimestampDetected = true;
        } else {
            nonMonotonicTimestampDetected = false;
        }
        mostRecentTimestamp = ts;
        return ts;
    }

    /**
     * This is the main method that parses input bag file to jAER raw events.
     * <p>
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
     * Gets the next raw packet. The packets are buffered to ensure that all the
     * data is monotonic in time.
     *
     * @param forwards true to look forwards, false to look backwards
     *
     * @return the packet
     */
    synchronized private AEPacketRaw getNextRawPacket(boolean forwards) throws EOFException, BagReaderException {
        DavisBaseCamera davisCamera = null;
        if (chip instanceof DavisBaseCamera) {
            davisCamera = (DavisBaseCamera) chip;
        }
        OutputEventIterator<ApsDvsEvent> outItr = eventPacket.outputIterator(); // to hold output events
        ApsDvsEvent e = new ApsDvsEvent();
        MessageWithIndex message = null;

        try {
            boolean gotEventsOrFrame = false;
            while (!gotEventsOrFrame) {
                message = forwards ? getNextMsg() : getPrevMsg();
//                log.fine(String.format("Message is %s",message.toString()));
                // send to listeners if topic is one we have subscribers for
                String topic = message.messageIndex.topic;
                Set<PropertyChangeListener> listeners = msgListeners.get(topic);
                if (!listeners.isEmpty()) {
                    for (PropertyChangeListener l : listeners) {
                        l.propertyChange(new PropertyChangeEvent(this, topic, null, message));
                    }
                }

                // now deal with standard DAVIS data
                String pkg = message.messageType.getPackage();
                String type = message.messageType.getType();
                switch (pkg) {
                    case "std_msgs": { // exposure
                        MessageType messageType = message.messageType;
//                         List<String> fieldNames = messageType.getFieldNames();
                        if (topic.equals("/dvs/exposure")) { // informs about frame start/end exposure, start/end readout (in original DAVIS346blue logic at least)
                            try {
                                int exposureUs = messageType.<Int32Type>getField("data").getValue(); // message seems to be exposure in ms as float although https://github.com/uzh-rpg/rpg_dvs_ros/blob/master/davis_ros_driver/src/driver.cpp publishes as Int32, very confusing
                                lastExposureUs = (int) (exposureUs);
                                lastEndOfExposureTimestamp = message.messageIndex.timestamp;
                            } catch (Exception ex) {
                                float exposureUs = messageType.<Float32Type>getField("data").getValue(); // message seems to be exposure in ms as float although https://github.com/uzh-rpg/rpg_dvs_ros/blob/master/davis_ros_driver/src/driver.cpp publishes as Int32, very confusing
                                lastExposureUs = (int) (exposureUs); // hack to deal with recordings made with pre-Int32 version of rpg-ros-dvs
                                lastEndOfExposureTimestamp = message.messageIndex.timestamp;
                            }
                        } else {
                            log.warning(String.format("std_msgs recieved that is not /dvs/exposure message, don't know what to to. Message: %s", message.toString()));
                        }

                    }
                    break;
                    case "sensor_msgs": // for RPG and MVSEC that come from DAVIS camera with frames and IMU samples
                        switch (type) {
                            case "Image": { // http://docs.ros.org/api/sensor_msgs/html/index-msg.html
                                // Make sure the image is from APS, otherwise some other image topic will be also processed here.
                                if (!(topic.equalsIgnoreCase(MVSEC_TOPIC_HEADER + "image_raw") || topic.equalsIgnoreCase(RPG_TOPIC_HEADER + "image_raw"))) {
                                    log.info(String.format("Topic %s in package %s is of type 'Image' but cannot be processed here", topic, pkg));
                                    continue;
                                }
                                // since frame comes during DVS events, we do the following
                                // we have the entire frame here already as intensity samples, so here we emit the entire frame as the original
                                // reset read and then sample read samples as events.
                                // This very awkward proceedure is to attempt to mimic the original format from camera of raw events, which is what the reader must produce
                                // as its (very inefficient) output.
                                // Since DVS events are also arriving (presumably) we only emit the frame APS events once we have the whole frame, which is here.
                                // (We already lost the original order of data from the camera in the ROS frame event format.)
                                // To keep monotonic time, we give all pixels of the frame the same timestamp as the most recent DVS event.
                                // the exposure events (SOE, EOE) are important to recover the correct frame exposure time. The frame readout start and end (SOF, EOF) are less important and are set to the EOE)
                                // We emit on SOE at the frame time, and one EOE at frame time plus the exposure time (which we got from std_msgs message, probably after we got the frame).

                                hasAps.setTrue();
                                MessageType messageType = message.messageType;
//                                List<String> fieldNames = messageType.getFieldNames();
                                MessageType header = messageType.getField("header"); // http://docs.ros.org/api/std_msgs/html/msg/Header.html
                                ArrayType data = messageType.<ArrayType>getField("data");
//                                int width = (int) (messageType.<UInt32Type>getField("width").getValue()).intValue();
//                                int height = (int) (messageType.<UInt32Type>getField("height").getValue()).intValue();
//                                Timestamp timestamp = header.<TimeType>getField("stamp").getValue(); // this is the actual timestamp in wall clock time of this ROS message, but it is not really useful.
                                Timestamp timestamp = message.messageIndex.timestamp; // this is the actual timestamp in wall clock time of this ROS message, but it is not really useful.

                                int ts = getTimestampUsRelative(timestamp, true, forwards); // don't check nonmonotonic for reverse mode
//                                if (lastEndOfExposureTimestamp != null) {
//                                    ts = getTimestampUsRelative(lastEndOfExposureTimestamp, false, false);
//                                }
//                                int ts = lastDvsTimestamp; // give the whole frame the last DVS timestamp, that way the output stream will be monotonic
                                gotEventsOrFrame = true;
                                byte[] bytes = data.getAsBytes();
                                final int sizey1 = chip.getSizeY() - 1, sizex = chip.getSizeX();
                                // construct frames as events, so that reconstuction as raw packet results in frame again. 
                                // what a hack...
                                // There is no real point to writing out thes special flag events since they are discarded when the packet is reconstructed as raw packet,
                                // but we leave them here for historical reasons.
                                // start of frame
                                e.setReadoutType(ApsDvsEvent.ReadoutType.SOF);
                                e.x = (short) 0;
                                e.y = (short) 0;
                                e.setTimestamp(ts);
                                maybePushEvent(e, apsFifo, outItr, forwards);
                                // start of exposure
                                e.setReadoutType(ApsDvsEvent.ReadoutType.SOE);
                                e.x = (short) 0;
                                e.y = (short) 0;
                                e.setTimestamp(ts);
                                maybePushEvent(e, apsFifo, outItr, forwards);
                                Point firstPixel = new Point(0, 0), lastPixel = new Point(chip.getSizeX() - 1, chip.getSizeY() - 1);
                                if (davisCamera != null) {
                                    firstPixel.setLocation(davisCamera.getApsFirstPixelReadOut());
                                    lastPixel.setLocation(davisCamera.getApsLastPixelReadOut());
                                }
                                int xinc = firstPixel.x < lastPixel.x ? 1 : -1;
                                int yinc = firstPixel.y < lastPixel.y ? 1 : -1;
                                // see AEFrameChipRenderer for putting event streams into textures to be drawn,
                                //    in particular AEFrameChipRenderer.renderApsDvsEvents 
                                //    and AEFrameChipRenderer.updateFrameBuffer for how cooked event stream is interpreted
                                // see ChipRendererDisplayMethodRGBA for OpenGL drawing of textures of frames
                                // see DavisBaseCamera.DavisEventExtractor for extraction of event stream
                                // See specific chip classes, e.g. Davis346mini for setup of first and last pixel adddresses for readout order of APS images
                                // The order is important because in AEFrameChipRenderer the frame buffer is cleared at the first
                                // pixel and copied to output after the last pixel, so the pixels must be written in correct order
                                // to the packet...
                                // Also, y is flipped for the rpg-dvs driver which is based on libcaer where the frame starts at
                                // upper left corner as in most computer vision, 
                                // unlike jaer that starts like in cartesian coordinates at lower left.

                                /* From inivation docs  https://docs.inivation.com/software/software-advanced-usage/file-formats/aedat-2.0.html
                                Frames, in this format, are laid out as a sequence of events, one for each pixel. 
                                The timestamp for each pixel is also stored and corresponds to the start of the readout of the column where the pixel resides. 
                                To get start and end of frame and exposure timestamps, 
                                one has to look at the timestamp of the pixel readouts closest to those moments:
                                    Start of Frame: first reset read pixel.
                                    Start of Exposure: last reset read pixel for GlobalShutter mode, first reset read pixel for RollingShutter mode.
                                    End of Exposure: first signal read pixel.
                                    End of Frame: last signal read pixel.
                                 */
                                for (int f = 0; f < 2; f++) { // First reset reads, then signal reads
                                    int tsFrame = ts;
                                    if (f == 1) {
                                        ts += lastExposureUs; // on first signal read, increment timestamp by measured exposure, not quite correct, should be ts up to last reset read pixel
                                    }
                                    e.setTimestamp(ts);
                                    // now we start at 
                                    for (int y = firstPixel.y; (yinc > 0 ? y <= lastPixel.y : y >= lastPixel.y); y += yinc) {
                                        for (int x = firstPixel.x; (xinc > 0 ? x <= lastPixel.x : x >= lastPixel.x); x += xinc) {
                                            // above x and y are in jAER image space
                                            final int yrpg = sizey1 - y; // flips y to get to rpg from jaer coordinates (jaer uses 0,0 as lower left, rpg-dvs uses 0,0 as upper left)
                                            final int idx = yrpg * sizex + x;
                                            e.setReadoutType(f == 0 ? ApsDvsEvent.ReadoutType.ResetRead : ApsDvsEvent.ReadoutType.SignalRead);
                                            e.x = (short) x;
                                            e.y = (short) y;
                                            e.setAdcSample(f == 0 ? 255 : (255 - (0xff & bytes[idx])));
//                                            if (davisCamera == null) {
//                                                e.setTimestamp(ts);
//                                            } else {
//                                                if (davisCamera.lastFrameAddress((short) x, (short) y)) {
//                                                    e.setTimestamp(ts + lastExposureUs); // set timestamp of last event written out to the frame end timestamp, TODO complete hack to have 1 pixel with larger timestamp
//                                                } else {
//                                                    e.setTimestamp(ts);
//                                                }
//                                            }
                                            maybePushEvent(e, apsFifo, outItr, forwards);
                                            // debug
//                                            if(x==firstPixel.x && y==firstPixel.y){
//                                                log.info("pushed first frame pixel event "+e);
//                                            }else if(x==lastPixel.x && y==lastPixel.y){
//                                                log.info("pushed last frame pixel event "+e);
//                                            }
                                        }
                                    }
                                }

                                // end of frame event
//                                e = outItr.nextOutput();
                                e.setReadoutType(ApsDvsEvent.ReadoutType.EOF);
                                e.x = (short) 0;
                                e.y = (short) 0;
                                e.setTimestamp(ts + lastExposureUs);
                                maybePushEvent(e, apsFifo, outItr, forwards);

                                // end of exposure event
//                                e = outItr.nextOutput();
                                e.setReadoutType(ApsDvsEvent.ReadoutType.EOE); // TODO now the exposure time of all frames will be 0 since we don't have the SOE event in the DVS stream 
                                e.x = (short) 0;
                                e.y = (short) 0;
                                e.setTimestamp(ts + lastExposureUs); // NOTE this EOE event is this many us after, this will cause the frame to be buffered in AEFifo
                                maybePushEvent(e, apsFifo, outItr, forwards);

                            }
                            break;
                            case "Imu": { // http://docs.ros.org/api/sensor_msgs/html/index-msg.html
                                hasImu.setTrue();
                                MessageType messageType = message.messageType;
//                                List<String> fieldNames = messageType.getFieldNames();
//                                for(String s:fieldNames){
//                                    System.out.println("fieldName: "+s);
//                                }
//                                List<String> fieldNames = messageType.getFieldNames();
//                                MessageType header = messageType.getField("header"); // http://docs.ros.org/api/std_msgs/html/msg/Header.html
//                                Timestamp timestamp = header.<TimeType>getField("stamp").getValue();
                                Timestamp timestamp = message.messageIndex.timestamp;

                                int ts = getTimestampUsRelative(timestamp, true, forwards); // do update largest timestamp with IMU time
                                MessageType angular_velocity = messageType.getField("angular_velocity");
//                                List<String> angvelfields=angular_velocity.getFieldNames();
//                                for(String s:angvelfields){
//                                    System.out.println("angular_velocity field: "+s);
//                                }
                                // units rad/s http://docs.ros.org/api/sensor_msgs/html/msg/Imu.html
                                float xrot = (float) (angular_velocity.<Float64Type>getField("x").getValue().doubleValue());
                                float yrot = (float) (angular_velocity.<Float64Type>getField("y").getValue().doubleValue());
                                float zrot = (float) (angular_velocity.<Float64Type>getField("z").getValue().doubleValue());
                                MessageType linear_acceleration = messageType.getField("linear_acceleration");
//                                List<String> linaccfields=linear_acceleration.getFieldNames();
//                                for(String s:linaccfields){
//                                    System.out.println("linaccfields field: "+s);
//                                }
                                // units m/s^2 http://docs.ros.org/api/sensor_msgs/html/msg/Imu.html
                                float xacc = (float) (linear_acceleration.<Float64Type>getField("x").getValue().doubleValue());
                                float yacc = (float) (linear_acceleration.<Float64Type>getField("y").getValue().doubleValue());
                                float zacc = (float) (linear_acceleration.<Float64Type>getField("z").getValue().doubleValue());
                                short[] buf = new short[7];

                                buf[IMUSampleType.ax.code] = (short) encodeImuAccel(xacc); // TODO set these scales from caer parameter messages in stream
                                buf[IMUSampleType.ay.code] = (short) encodeImuAccel(yacc);
                                buf[IMUSampleType.az.code] = (short) encodeImuAccel(zacc);

                                buf[IMUSampleType.gx.code] = (short) encodeImuGyro(xrot);
                                buf[IMUSampleType.gy.code] = (short) encodeImuGyro(yrot);
                                buf[IMUSampleType.gz.code] = (short) encodeImuGyro(zrot);
//                                ApsDvsEvent e = null;
//                                e = outItr.nextOutput();
                                IMUSample imuSample = new IMUSample(ts, buf);
                                e.setImuSample(imuSample);
                                e.setTimestamp(ts);
                                log.finest(imuSample.toString());
                                maybePushEvent(e, imuFifo, outItr, forwards);
                                gotEventsOrFrame = true;
                            }
                            break;
                        }
                        break;
                    case "dvs_msgs": // for RPG and MVSEC that also has DVS events
                    case "samsung_event_msgs": // for EV_IMO that uses Samsung inivation camera with only DVS events
                        hasDvs.setTrue();
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
//                                int nEvents = eventFields.size();
//                                System.out.println(String.format("%,d events: idx:dt, ... ",nEvents));
                                for (Field eventField : eventFields) {
//                                    if(eventIdxThisPacket==0){
//                                        eventIdxThisPacket++;
//                                        continue;
//                                    }
                                    MessageType eventMsg = (MessageType) eventField;
                                    // https://github.com/uzh-rpg/rpg_dvs_ros/tree/master/dvs_msgs/msg]
                                    int x = eventMsg.<UInt16Type>getField("x").getValue();
                                    int y = eventMsg.<UInt16Type>getField("y").getValue();
                                    boolean pol = eventMsg.<BoolType>getField("polarity").getValue(); // false==off, true=on
                                    Timestamp timestamp = (Timestamp) eventMsg.<TimeType>getField("ts").getValue();
                                    int ts = getTimestampUsRelative(timestamp, true, forwards); // sets nonMonotonicTimestampDetected flag, faster than throwing exception, updates largest timestamp
//                                    ApsDvsEvent e = outItr.nextOutput();
                                    e.setReadoutType(ApsDvsEvent.ReadoutType.DVS);
                                    e.timestamp = ts;
                                    int dt = (ts - lastDvsTimestamp);
                                    if (dt < 0) {
                                        log.warning(String.format("nonmonotonic #%,d: dt=%,d", eventIdxThisPacket, dt));
                                    }
//                                    else if(eventIdxThisPacket<30 || eventIdxThisPacket>nEvents-40){
//                                        System.out.print(String.format("%,d: %,d, ",eventIdxThisPacket,dt));
//                                        if(eventIdxThisPacket%10==1){
//                                            System.out.println("");
//                                        }
//                                    }
                                    lastDvsTimestamp = ts;
                                    e.x = (short) x;
                                    e.y = (short) (sizeY - y - 1);
                                    e.polarity = pol ? PolarityEvent.Polarity.Off : PolarityEvent.Polarity.On;
                                    e.type = (byte) (pol ? 0 : 1);
                                    maybePushEvent(e, dvsFifo, outItr, forwards);
                                    eventIdxThisPacket++;
                                }
//                                System.out.println("done");
                                break;
                            default:
                                log.warning(String.format("Unknown message type %s for DVS message %s", type, message));
                                break;

                        }
                        break;
                }

            }
        } catch (UninitializedFieldException ex) {
            Logger.getLogger(ExampleRosBagReader.class.getName()).log(Level.SEVERE, null, ex);
            throw new BagReaderException(ex);
        }
        // if we were not checking time order, then events were directly copied to output packet oe already
        // also, don't bother with FIFO if playing backwards
        if (nonMonotonicTimestampExceptionsChecked && forwards) {
            // now pop events in time order from the FIFOs to the output packet and then reconstruct the raw packet
            ApsDvsEvent ev;
            try {
                while ((ev = popOldestEvent()) != null) {
                    ApsDvsEvent oe = outItr.nextOutput();
                    oe.copyFrom(ev);
                }
            } catch (NonMonotonicTimeException ex) {
                Logger.getLogger(RosbagFileInputStream.class.getName()).log(Level.WARNING, null, ex);
            }
        }
        AEPacketRaw aePacketRawCollecting = chip.getEventExtractor().reconstructRawPacket(eventPacket);
        fireInitPropertyChange();
        return aePacketRawCollecting;
    }

    private final static short encodeImuGyro(float rotationDegPerSec) {
        float f = DEG_PER_RAD * rotationDegPerSec / IMUSample.getGyroSensitivityScaleFactorDegPerSecPerLsb();
        if (f > Short.MAX_VALUE || f<Short.MIN_VALUE) {
            log.warning(String.format("IMU Rate gyro value %.1f deg/s when encoded is %,d which exceeds 16b short encoding range; increase max IMU rate gyro scale in Hardware Configuration panel",rotationDegPerSec,(int)f));
            return f>0? Short.MAX_VALUE:Short.MIN_VALUE;
        }
        return (short) f;
    }

    private final static short encodeImuAccel(float accelerationG) {
        float f = G_PER_MPS2 * accelerationG / IMUSample.getAccelSensitivityScaleFactorGPerLsb();
        if (f > Short.MAX_VALUE || f<Short.MIN_VALUE) {
            log.warning(String.format("IMU Acceleration value %.1fg when encoded is %,d which exceeds 16b short encoding range; increase max IMU acceleration scale in Hardware Configuration panel",accelerationG,(int)f));
            return  f>0? Short.MAX_VALUE:Short.MIN_VALUE;
        }
        return (short) f;
    }

    /**
     * Either pushes event to FIFO or just directly writes it to output packet,
     * depending on flag nonMonotonicTimestampExceptionsChecked.
     *
     * @param ev the event
     * @param fifo the fifo to write to
     * @param outItr the output packet iterator, if events are directly written
     * @param forwards flag to only use FIFO if playing forwards, don't bother
     * for reverse to output
     */
    private void maybePushEvent(ApsDvsEvent ev, AEFifo fifo, OutputEventIterator<ApsDvsEvent> outItr, boolean forwards) {
        if (nonMonotonicTimestampExceptionsChecked && forwards) {
            fifo.pushEvent(ev);
        } else {
            ApsDvsEvent oe = outItr.nextOutput();
            oe.copyFrom(ev);
        }
    }

    /**
     * Returns the oldest event (earliest in time) from all of the AEFifo's
     * (DVS, APS, IMU) but only if there is at least one younger or equally old
     * event in some other AEFifo. That way, we can be sure to keep all
     * timestamps monotonic.
     *
     * @return oldest valid event (and first one pushed to any one particular
     * sub-fifo for identical timestamps) or null if there is none
     */
    private ApsDvsEvent popOldestEvent() throws NonMonotonicTimeException {
        // find oldest event over all fifos
        ApsDvsEvent ev = null;

        int fifoIdx = -1;
        // from all aeFifos with data, find the index and timestamp of the one with the oldest event
        int earliestTs = Integer.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            boolean hasData = hasDvsApsImu[i].isTrue();  // set True if during input we get any of this data type
            if (hasData) {
                int t;
                if (!aeFifos[i].isEmpty() && (t = aeFifos[i].peekNextTimestamp()) <= /* check <= */ earliestTs) {
                    // if it has data and its next timestamp is younger than other aeFifo then save it
                    fifoIdx = i;
                    earliestTs = t;
                }
            }
        }
        // fifoIdx is the index and ts is the timestmp of the earliest (oldest) event available from any stream
        if (fifoIdx < 0) {
            return null; // no fifo has event
        }
        // if only one stream has data then just return it
        if (numStreamsWithData == 1) {
            ev = aeFifos[fifoIdx].popEvent();
        } else {
            // We alkready found the oldest data available.
            // We only return this event if ALL the other FIFOs which can have data actually have data.
            // We can't return this oldest event until we know there is no younger event in any other stream
            boolean allOtherStreamsHaveYoungerData = true;
            for (int i = 0; i < 3; i++) {
                if (i == fifoIdx) { // don't compare with ourselves because we need to be sure that some other stream has younger data
                    continue;
                }
                if (hasDvsApsImu[i].isTrue() // if there is data at all from this stream in the file
                        && aeFifos[i].isEmpty()) { // and this FIFI happens to be empty
                    allOtherStreamsHaveYoungerData = false; // then we don't know we are really oldest
                    break;
                }
            }
            if (allOtherStreamsHaveYoungerData) {
                ev = aeFifos[fifoIdx].popEvent();
            } else {
                ev = null;
            }
        }
        return ev;
    }

    /**
     * Called to signal first read from file. Fires PropertyChange
     * AEInputStream.EVENT_INIT, with new value this.
     */
    protected void fireInitPropertyChange() {
        if (firstReadCompleted) {
            return;
        }
        getSupport().firePropertyChange(AEInputStream.EVENT_INIT, null, this);
        firstReadCompleted = true;
    }

    @Override
    synchronized public AEPacketRaw readPacketByNumber(int numEventsToRead) throws IOException {
        int oldPosition = nextMessageNumber;
        if (numEventsToRead < 0) {
            return emptyPacket;
        }
        aePacketRawOutput.setNumEvents(0);
        while (aePacketRawBuffered.isEmpty() || aePacketRawBuffered.getNumEvents() < numEventsToRead && aePacketRawBuffered.getNumEvents() < AEPacketRaw.MAX_PACKET_SIZE_EVENTS) {
            try {
                AEPacketRaw p = getNextRawPacket(numEventsToRead >= 0);
                if (p.isEmpty()) {
                    continue;
                }
                try {
                    aePacketRawBuffered.append(p); // reaching EOF here will throw EOFException
                } catch (NonMonotonicTimeException e) {
                    if (isNonMonotonicTimeExceptionsChecked()) {
                        log.warning(e.toString());
                    }
                }
            } catch (BagReaderException ex) {
                throw new IOException(ex);
            }
        }
        aePacketRawOutput.clear();
        try {
            AEPacketRaw.copy(aePacketRawBuffered, 0, aePacketRawOutput, 0, numEventsToRead); // copy over collected events
        } catch (NonMonotonicTimeException e) {
            if (isNonMonotonicTimeExceptionsChecked()) {
                log.warning(e.toString());
            }
        }
        // now use tmp packet to copy rest of buffered to
        aePacketRawTmp.setNumEvents(0);
        try {
            AEPacketRaw.copy(aePacketRawBuffered, numEventsToRead, aePacketRawTmp, 0, aePacketRawBuffered.getNumEvents() - numEventsToRead);
        } catch (NonMonotonicTimeException e) {
            if (isNonMonotonicTimeExceptionsChecked()) {
                log.warning(e.toString());
            }
        }
        AEPacketRaw tmp = aePacketRawBuffered;
        aePacketRawBuffered = aePacketRawTmp;
        aePacketRawTmp = tmp;
        getSupport().firePropertyChange(AEInputStream.EVENT_POSITION, oldPosition, position());
        if (aePacketRawOutput.getNumEvents() > 0) {
            currentStartTimestamp = aePacketRawOutput.getLastTimestamp();
        }
        maybeSendRewoundEvent(oldPosition);
        return aePacketRawOutput;
    }

    private boolean lastDirectionForwards = true;

    @Override
    synchronized public AEPacketRaw readPacketByTime(int dt) throws IOException {
        int oldPosition = nextMessageNumber;
        int newEndTime = currentStartTimestamp + dt;
        boolean directionForwards = dt > 0;
        boolean changedDirection = directionForwards ^ lastDirectionForwards;

        if (changedDirection) { // xor them, if different then clear events so far
            clearAccumulatedEvents();
        }
        if (newEndTime < 0) {
            newEndTime = 0; // so we don't trh to read before 0
        }
        // keep getting ros messages until we get a timestamp later (or earlier for negative dt) the desired new end time
        if (directionForwards) {
            while (aePacketRawBuffered.isEmpty() || aePacketRawBuffered.getLastTimestamp() < newEndTime
                    && aePacketRawBuffered.getNumEvents() < MAX_RAW_EVENTS_BUFFER_SIZE) {
                if (rewindFlag) {
                    break;
                }
                try {
                    AEPacketRaw p = getNextRawPacket(directionForwards);
                    if (p.isEmpty()) {
                        continue;
                    }
                    try {
                        aePacketRawBuffered.append(p); // reaching EOF here will throw EOFException
                    } catch (NonMonotonicTimeException ex) {
                        if (nonMonotonicTimestampExceptionsChecked) {
                            Logger.getLogger(RosbagFileInputStream.class.getName()).log(Level.WARNING, null, ex);
                        }
                    }
                } catch (EOFException e) {
                    log.info(e.toString());
                    rewind();
                } catch (BagReaderException ex) {
                    if (ex.getCause() instanceof ClosedByInterruptException) { // ignore, caussed by interrupting ViewLoop to change rendering mode 
                        return emptyPacket;
                    }
                    throw new IOException(ex);
                }
            }

            if (!changedDirection) {
                // copy off events that are too late
                int[] ts = aePacketRawBuffered.getTimestamps();
                int idx = aePacketRawBuffered.getNumEvents() - 1;
                while (idx > 0 && ts[idx] > newEndTime) {
                    idx--;
                }
                aePacketRawOutput.clear(); // just in case 0 copied to packet
                //public static void copy(AEPacketRaw src, int srcPos, AEPacketRaw dest, int destPos, int length) {
                try {
                    AEPacketRaw.copy(aePacketRawBuffered, 0, aePacketRawOutput, 0, idx); // copy over collected events
                } catch (NonMonotonicTimeException e) {
                    if (isNonMonotonicTimeExceptionsChecked()) {
                        log.warning(e.toString());
                    }
                }
                // now use tmp packet to copy rest of buffered to, and then make that the new buffered
                aePacketRawTmp.setNumEvents(0); // in case we copy 0 events over
                try {
                    AEPacketRaw.copy(aePacketRawBuffered, idx, aePacketRawTmp, 0, aePacketRawBuffered.getNumEvents() - idx);
                } catch (NonMonotonicTimeException e) {
                    if (isNonMonotonicTimeExceptionsChecked()) {
                        log.warning(e.toString());
                    }
                }
                AEPacketRaw tmp = aePacketRawBuffered;
                aePacketRawBuffered = aePacketRawTmp;
                aePacketRawTmp = tmp;
            } else {
                aePacketRawOutput.clear(); // just in case 0 copied to packet
                //public static void copy(AEPacketRaw src, int srcPos, AEPacketRaw dest, int destPos, int length) {
                try {
                    AEPacketRaw.copy(aePacketRawBuffered, 0, aePacketRawOutput, 0, aePacketRawBuffered.getNumEvents()); // copy over collected events
                } catch (NonMonotonicTimeException e) {
                    if (isNonMonotonicTimeExceptionsChecked()) {
                        log.warning(e.toString());
                    }
                }
                clearAccumulatedEvents();
            }
            if (aePacketRawOutput.isEmpty()) {
                currentStartTimestamp = newEndTime;
            } else {
                currentStartTimestamp = aePacketRawOutput.getLastTimestamp();
            }
        } else { // backwards, only read one packet
            try {
                aePacketRawOutput = getNextRawPacket(false);// read one packet backwards. leaving nextMessageNumber at this same messsage. Reaching EOF here will throw EOFException that AEPlayer will handle
                if (aePacketRawOutput != null && !aePacketRawOutput.isEmpty()) {
                    currentStartTimestamp = aePacketRawOutput.getLastTimestamp(); // update according to what we actually got
                }
            } catch (BagReaderException ex) {
                if (ex.getCause() instanceof ClosedByInterruptException) { // ignore, caussed by interrupting ViewLoop to change rendering mode 
                    return emptyPacket;
                }
                throw new IOException(ex);
            }
        }
        getSupport().firePropertyChange(AEInputStream.EVENT_POSITION, oldPosition, nextMessageNumber);
        maybeSendRewoundEvent(oldPosition);
        lastDirectionForwards = directionForwards;
        return aePacketRawOutput;
    }

    @Override
    public boolean isNonMonotonicTimeExceptionsChecked() {
        return nonMonotonicTimestampExceptionsChecked;
    }

    /**
     * If this option is set, then non-monotonic timestamps are replaced with
     * the newest timestamp in the file, ensuring monotonicity. It seems that
     * the frames and IMU samples are captured or timestamped out of order in
     * the file, resulting in DVS packets that contain timestamps younger than
     * earlier frames or IMU samples.
     *
     * @param yes to guarantee monotonicity
     */
    @Override
    public void setNonMonotonicTimeExceptionsChecked(boolean yes) {
        nonMonotonicTimestampExceptionsChecked = yes;
    }

    /**
     * Returns starting time of file since epoch, in UTC time
     *
     * @return the time in ms since epoch (1970)
     */
    @Override
    public long getAbsoluteStartingTimeMs() {
        return bagFile.getStartTime().getTime();
    }

    @Override
    public ZoneId getZoneId() {
        return zoneId; // TODO implement setting time zone from file
    }

    @Override
    public int getDurationUs() {
        return (int) (bagFile.getDurationS() * 1e6);
    }

    @Override
    public int getFirstTimestamp() {
        return (int) firstTimestampUs;
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
        return nextMessageNumber;
    }

    @Override
    public void position(long n) {
        if (n < 0) {
            n = 0;
        } else if (n >= numMessages) {
            n = numMessages - 1;
        }
        nextMessageNumber = (int) n;
    }

    @Override
    synchronized public void rewind() throws IOException {
        position(isMarkInSet() ? getMarkInPosition() : 0);
        clearAccumulatedEvents();
        largestTimestampReadSinceRewind = 0; // timestamps start at 0 by constuction of timestamps
        try {
            MessageWithIndex msg = getNextMsg();
            if (msg != null) {
                currentStartTimestamp = getTimestampUsRelative(msg.messageIndex.timestamp, true, false);  // reind, so update the largest timestamp read in this cycle to first timestamp
                position(isMarkInSet() ? getMarkInPosition() : 0);
            }
        } catch (BagReaderException e) {
            log.warning(String.format("Exception %s getting timestamp after rewind from ROS message", e.toString()));
        }
        clearAccumulatedEvents();
        rewindFlag = true;
    }

    private void maybeSendRewoundEvent(long oldPosition) {
        if (rewindFlag) {
            getSupport().firePropertyChange(AEInputStream.EVENT_REWOUND, oldPosition, position());
            rewindFlag = false;
        }
    }

    private void clearAccumulatedEvents() {
        aePacketRawBuffered.clear();
        for (AEFifo f : aeFifos) {
            f.clear();
        }
    }

    @Override
    synchronized public void setFractionalPosition(float frac) {
        int messageNumber = (int) (frac * numMessages);
        log.info(String.format("Setting fractional position %.1f%% which is message %,d out of total %,d messages", frac * 100, messageNumber, numMessages));
        position(messageNumber); // must also clear partially accumulated events in collecting packet and reset the timestamp

        clearAccumulatedEvents();
        try {
            AEPacketRaw raw = getNextRawPacket(true);
            while (raw == null) {
                log.warning(String.format("got null packet at fractional position %.2f which should have been message number %d, trying next packet", frac, nextMessageNumber));
                raw = getNextRawPacket(true);
            }
            aePacketRawBuffered.append(raw); // reaching EOF here will throw EOFException
        } catch (NonMonotonicTimeException e) {
            if (isNonMonotonicTimeExceptionsChecked()) {
                log.warning(e.toString());
            }
        } catch (EOFException ex) {
            try {
                aePacketRawBuffered.clear();
                rewind();
            } catch (IOException ex1) {
                Logger.getLogger(RosbagFileInputStream.class.getName()).log(Level.SEVERE, null, ex1);
            }
        } catch (BagReaderException ex) {
            Logger.getLogger(RosbagFileInputStream.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public long size() {
        return 0;
    }

    @Override
    public void clearMarks() {
        markIn = -1;
        markOut = -1;
        getSupport().firePropertyChange(AEInputStream.EVENT_MARKS_CLEARED, null, null);
    }

    @Override
    public long setMarkIn() {
        int old = markIn;
        markIn = nextMessageNumber;
        getSupport().firePropertyChange(AEInputStream.EVENT_MARK_IN_SET, old, markIn);
        return markIn;
    }

    @Override
    public long setMarkOut() {
        int old = markOut;
        markOut = nextMessageNumber;
        getSupport().firePropertyChange(AEInputStream.EVENT_MARK_OUT_SET, old, markOut);
        return markOut;
    }

    @Override
    public long getMarkInPosition() {
        return markIn;
    }

    @Override
    public long getMarkOutPosition() {
        return markOut;
    }

    @Override
    public boolean isMarkInSet() {
        return markIn > 0;
    }

    @Override
    public boolean isMarkOutSet() {
        return markOut <= numMessages;
    }

    @Override
    public void setRepeat(boolean repeat) {
        this.repeatEnabled = repeat;
    }

    @Override
    public boolean isRepeat() {
        return repeatEnabled;
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
        return (int) lastTimestampUs; // TODO, from last DVS event timestamp
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
        file = null;
        System.gc();
    }

    @Override
    public int getCurrentStartTimestamp() {
        return (int) lastTimestampUs;
    }

    @Override
    synchronized public void setCurrentStartTimestamp(int currentStartTimestamp) {
        this.currentStartTimestamp = currentStartTimestamp;
        nextMessageNumber = (int) (numMessages * ((float) currentStartTimestamp) / getDurationUs()); // TODO only very approximate
        aePacketRawBuffered.clear();
    }

    private void generateMessageIndexes(ProgressMonitor progressMonitor) throws BagReaderException, InterruptedException {
        if (wasIndexed) {
            return;
        }
        log.info(String.format("creating or loading cached index for all topics in topicList=%s", topicSet));
        if (!maybeLoadCachedMsgIndexes(progressMonitor)) {
            msgIndexes = bagFile.generateIndexesForTopicList(new ArrayList(topicSet), progressMonitor);
            cacheMsgIndexes();
        }
        numMessages = msgIndexes.size();
        markIn = 0;
        markOut = numMessages;
        startAbsoluteTimestamp = msgIndexes.get(0).timestamp;
        startAbsoluteTimeS = 1e-3 * startAbsoluteTimestamp.getTime();
        endAbsoluteTimeS = 1e-3 * msgIndexes.get(numMessages - 1).timestamp.getTime();
        durationS = endAbsoluteTimeS - startAbsoluteTimeS;
        firstTimestampUs = getTimestampUsRelative(msgIndexes.get(0).timestamp, true, false);
        lastTimestampUs = getTimestampUsRelative(msgIndexes.get(numMessages - 1).timestamp, false, false); // don't update largest timestamp with last timestamp
        wasIndexed = true;
        StringBuilder sb = new StringBuilder();
        String s = String.format("%nRecording start Date %s%nRecording start time since epoch %,.3fs%nDuration %,.3fs",
                startAbsoluteTimestamp.toString(),
                startAbsoluteTimeS,
                durationS);
        rosbagInfoString = rosbagInfoString + s;
    }

    @Override
    public String toString() {
        return super.toString() + Character.LINE_SEPARATOR + rosbagInfoString;
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        this.support.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        this.support.removePropertyChangeListener(listener);
    }

    /**
     * Adds a topic for which listeners should be informed. The PropertyChange
     * has this as the source and the new value is the MessageType message.
     *
     * @param topics a string topic, e.g. "/dvs/events"
     * @param listener a PropertyChangeListener that gets the messages
     * @throws java.lang.InterruptedException since generating the topic index
     * is a long-running process, the method throws
     * @throws com.github.swrirobotics.bags.reader.exceptions.BagReaderException
     * if there is an exception for the BagFile
     * @see MessageType
     */
    @Override
    synchronized public void addSubscribers(List<String> topics, PropertyChangeListener listener, ProgressMonitor progressMonitor) throws InterruptedException, BagReaderException {
        List<String> topicsToAdd = new ArrayList();
        for (String topic : topics) {
            if (msgListeners.containsKey(topic) && msgListeners.get(topic).contains(listener)) {
                log.warning("topic " + topic + " and listener " + listener + " already added, ignoring");
                continue;
            }
            topicsToAdd.add(topic);
        }
        if (topicsToAdd.isEmpty()) {
            log.warning("nothing to add");
            return;
        }

        addPropertyChangeListener(listener);
        List<BagFile.MessageIndex> idx = bagFile.generateIndexesForTopicList(topicsToAdd, progressMonitor);
        msgIndexes.addAll(idx);
        for (String topic : topics) {
            msgListeners.put(topic, listener);
        }
        Collections.sort(msgIndexes);
    }

    /**
     * Removes a topic
     *
     * @param topic
     * @param listener
     */
    @Override
    public void removeTopic(String topic, PropertyChangeListener listener) {
        log.warning("cannot remove topic parsing, just removing listener");
        if (msgListeners.containsValue(listener)) {
            log.info("removing listener " + listener);
            removePropertyChangeListener(listener);
        }
    }

    /**
     * Set if nonmonotonic timestamp was detected. Cleared at start of
     * collecting each new packet.
     *
     * @return the nonMonotonicTimestampDetected true if a nonmonotonic
     * timestamp was detected.
     */
    public boolean isNonMonotonicTimestampDetected() {
        return nonMonotonicTimestampDetected;
    }

    synchronized private void cacheMsgIndexes() {
        try {
            File file = new File(messageIndexesCacheFileName());
            if (file.exists() && file.canRead() && file.isFile()) {
                log.info("cached indexes " + file + " for file " + getFile() + " already exists, not storing it again");
                return;
            }
            log.info("caching the index for rosbag file " + getFile() + " in " + file);
            FileOutputStream out = new FileOutputStream(file);
            ObjectOutputStream oos = new ObjectOutputStream(out);
            oos.writeObject(msgIndexes);
            oos.flush();
            log.info(String.format("cached the message index for %,d messages for rosbag file %s in %s", msgIndexes.size(), getFile(), file));
        } catch (Exception e) {
            log.warning("could not cache the message index to disk: " + e.toString());
        }
    }

    synchronized private boolean maybeLoadCachedMsgIndexes(ProgressMonitor progressMonitor) {
        try {
            File file = new File(messageIndexesCacheFileName());
            if (!file.exists() || !file.canRead() || !file.isFile()) {
                log.info("cached indexes " + file + " for file " + getFile() + " does not exist");
                return false;
            }
            log.info("reading cached index for rosbag file " + getFile() + " from " + file);
            long startTime = System.currentTimeMillis();
            if (progressMonitor != null) {
                if (progressMonitor.isCanceled()) {
                    progressMonitor.setNote("canceling");
                    throw new InterruptedException("canceled loading caches");
                }
                progressMonitor.setNote("reading cached index from " + file);
            }
            FileInputStream in = new FileInputStream(file);
            ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(in));
            List<BagFile.MessageIndex> tmpIdx = (List<BagFile.MessageIndex>) ois.readObject();
            msgIndexes = tmpIdx;
            long ms = System.currentTimeMillis() - startTime;
            log.info("done after " + ms + "ms with reading cached index for rosbag file " + getFile() + " from " + file);
            if (progressMonitor != null) {
                if (progressMonitor.isCanceled()) {
                    progressMonitor.setNote("canceling");
                    throw new InterruptedException("canceled loading caches");
                }
                progressMonitor.setNote("done reading cached index");
                progressMonitor.setProgress(progressMonitor.getMaximum());
            }
            if (msgIndexes.size() == 0) {
                log.warning(String.format("cached index file %s has size zero; rebuilding it", file));
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warning("could load cached message index from disk: " + e.toString());
            return false;
        }
    }

    private String messageIndexesCacheFileName() {
        return System.getProperty("java.io.tmpdir") + File.separator + getFile().getName() + ".rosbagidx";
    }

    private int lastAeFifoTimestampPopped = Integer.MIN_VALUE; // to check for nonmonotonic in popping events

    /**
     * A FIFO for ApsDvsEvent events.
     */
    private final class AEFifo extends ApsDvsEventPacket {

        private final int MAX_EVENTS = 1 << 22;
        int nextToPopIndex = 0;
        private boolean full = false;
        private int lastTimestampPushed = Integer.MIN_VALUE;
        private String name;

        public AEFifo(String name) {
            super(ApsDvsEvent.class);
            this.name = name;
        }

        /**
         * Adds a new event to end
         *
         * @param event
         */
        public final void pushEvent(ApsDvsEvent event) {
            if (full) {
                return;
            }
            if (!isEmpty()) {
                int dt = event.timestamp - lastTimestampPushed;
                if (size > 0 && dt < 0) {
//                    log.warning(String.format("clearing %,d events from AeFifo for event %n%s%nthat is younger by dt=%,d us than last event %n%s", getSize(), event, dt, getLastEvent()));
                    log.warning(String.format("pushing event %n%s%nthat is younger by dt=%,d us than last event %n%s", event, dt, getLastEvent()));
//                    clear();
                }
            }
            if (size >= MAX_EVENTS) {
                full = true;
                log.warning(String.format("FIFO has reached capacity RosbagFileInputStream.MAX_EVENTS=%,d events: %s", MAX_EVENTS, toString()));
                for (AEFifo f : aeFifos) {
                    if (!f.isEmpty()) {
                        System.out.println(f.toString());
                    }
                }
                return;
            }
            appendCopyOfEvent(event);
            lastTimestampPushed = event.timestamp;
        }

        @Override
        public boolean isEmpty() {
            return size == 0 || nextToPopIndex >= size; //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void clear() {
            super.clear();
            nextToPopIndex = 0;
            lastTimestampPushed = Integer.MIN_VALUE;
            full = false;
        }

        /**
         * @return next event, or null if there are no more
         */
        public final ApsDvsEvent popEvent() throws NonMonotonicTimeException {
            if (isEmpty()) {
                clear();
                return null;
            }
            ApsDvsEvent event = (ApsDvsEvent) getEvent(nextToPopIndex++);
            if (isEmpty()) {
                clear();
            }
            int ts = event.timestamp;
            int dt = ts - lastAeFifoTimestampPopped;
            if (ts < lastAeFifoTimestampPopped) {
                if (isNonMonotonicTimeExceptionsChecked()) {
                    log.warning(String.format("Nonmonotonic timestamp popped: This ts (%,d) is earlier than last one (%,d) by dt=%,d us", ts, lastAeFifoTimestampPopped, dt));
//                    throw new NonMonotonicTimeException(String.format("Nonmonotonic timestamp popped: This ts (%,d) is earlier than last one (%,d) by dt=%,d us", ts, lastAeFifoTimestampPopped, dt));
                }
            }
            lastAeFifoTimestampPopped = ts;
            return event;
        }

        public final int peekNextTimestamp() {
            return getEvent(nextToPopIndex).timestamp;
        }

        @Override
        final public String toString() {
            if (size == 0) {
                return String.format("Empty AEFifo named %s", name);
            }
            return String.format("AEFifo %s with next timestamp %,d, size=%,d and capacity=%,d nextToPopIndex=%,d for packet %s", name, peekNextTimestamp(), getSize(), MAX_EVENTS, nextToPopIndex, super.toString());
        }

    }

    /**
     *
     * Returns the absolute time since the 1979 unix epoch in double seconds.
     *
     * @return the startAbsoluteTimeS
     */
    public double getStartAbsoluteTimeS() {
        return startAbsoluteTimeS;
    }

    /**
     * Returns the absolute time of first message in unix absolute time since
     * 1970 epoch
     *
     * @return the startAbsoluteTimestamp
     */
    public Timestamp getStartAbsoluteTimestamp() {
        return startAbsoluteTimestamp;
    }

}
