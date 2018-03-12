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
import com.github.swrirobotics.bags.reader.messages.serialization.UInt32Type;
import com.google.common.collect.HashMultimap;
import eu.seebetter.ini.chips.davis.DavisBaseCamera;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import eu.seebetter.ini.chips.davis.imu.IMUSampleType;
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
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Formatter;
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

/**
 * Reads ROS bag files holding data from https://github.com/uzh-rpg/rpg_dvs_ros
 * recordings, in format http://wiki.ros.org/Bags .
 *
 * @author Tobi
 */
public class RosbagFileInputStream implements AEFileInputStreamInterface, RosbagTopicMessageSupport {

    private static final Logger log = Logger.getLogger("RosbagFileInputStream");
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
    // the most recently read event timestamp, the first one in file, and the last one in file
    private int mostRecentTimestamp, largestTimestamp = Integer.MIN_VALUE;
    private long firstTimestamp, lastTimestamp;
    private long firstTimestampUsAbsolute; // the absolute (ROS) first timestamp in us
    private boolean firstTimestampWasRead = false;

    // marks the present read time for packets
    private int currentStartTimestamp;
    private final ApsDvsEventPacket<ApsDvsEvent> eventPacket;
    private AEPacketRaw aePacketRawBuffered = new AEPacketRaw(), aePacketRawOutput = new AEPacketRaw(), aePacketRawTmp = new AEPacketRaw();
    private AEPacketRaw emptyPacket = new AEPacketRaw();
    private int nextMessageNumber = 0, numMessages = 0;

    private long absoluteStartingTimeMs = 0; // in system time since 1970 in ms
    private FileChannel channel = null;

//    private static final String[] TOPICS = {"/dvs/events"};\
    private static final String RPG_TOPIC_HEADER = "/dvs/", MVSEC_TOPIC_HEADER = "/davis/left/"; // TODO arbitrarily choose left camera for MVSEC for now
    private static final String TOPIC_EVENTS = "events", TOPIC_IMAGE = "image_raw", TOPIC_IMU = "imu", TOPIC_EXPOSURE = "exposure";
    private static String[] STANARD_TOPICS = {TOPIC_EVENTS, TOPIC_IMAGE, TOPIC_IMU, TOPIC_EXPOSURE};
//    private static String[] TOPICS = {TOPIC_HEADER + TOPIC_EVENTS, TOPIC_HEADER + TOPIC_IMAGE};
    private ArrayList<String> topicList = new ArrayList();
    private ArrayList<String> topicFieldNames = new ArrayList();
    private boolean wasIndexed = false;
    private List<BagFile.MessageIndex> msgIndexes = new ArrayList();
    private HashMultimap<String, PropertyChangeListener> msgListeners = HashMultimap.create();
    private boolean firstReadCompleted = false;
    private ArrayList<String> extraTopics = null; // extra topics that listeners can subscribe to

    private boolean nonMonotonicTimestampExceptionsChecked = true;
    private boolean nonMonotonicTimestampDetected = false; // flag set by nonmonotonic timestamp if detection enabled
    private boolean rewindFlag = false;

    private enum RosbagFileType {
        RPG(RPG_TOPIC_HEADER), MVSEC(MVSEC_TOPIC_HEADER), Unknown("???");
        private String header;

        private RosbagFileType(String header) {
            this.header = header;
        }

    }

    // two types of topics depending on format of rosbag
    // RPG is from https://github.com/uzh-rpg/rpg_dvs_ros/tree/master/dvs_msgs/msg
    // MVSEC is from https://daniilidis-group.github.io/mvsec/data_format/
    RosbagFileType rosbagFileType = RosbagFileType.Unknown;

    /**
     * Interval for logging warnings about nonmonotonic timestamps.
     */
    public static final int NONMONOTONIC_TIMESTAMP_WARNING_INTERVAL = 1000000;
    private int nonmonotonicTimestampCounter = 0;

    private int lastExposureUs; // holds last exposure value (in us?) to use for creating SOE and EOE events for frames
    private int markIn = 0;
    private int markOut;
    private boolean repeatEnabled = true;

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

        log.info("reading rosbag file " + f + " for chip " + chip);
        bagFile = BagReader.readFile(file);
        StringBuilder sb = new StringBuilder("Bagfile information:\n");
        for (TopicInfo topic : bagFile.getTopics()) {
            sb.append(topic.getName() + " \t\t" + topic.getMessageCount()
                    + " msgs \t: " + topic.getMessageType() + " \t"
                    + (topic.getConnectionCount() > 1 ? ("(" + topic.getConnectionCount() + " connections)") : "")
                    + "\n");
            if (topic.getName().contains(RosbagFileType.MVSEC.header)) {
                rosbagFileType = RosbagFileType.MVSEC;
            } else if (topic.getName().contains(RosbagFileType.RPG.header)) {
                rosbagFileType = RosbagFileType.RPG;
            }
        }
        sb.append("Duration: " + bagFile.getDurationS() + "s\n");
        sb.append("Chunks: " + bagFile.getChunks().size() + "\n");
        sb.append("Num messages: " + bagFile.getMessageCount() + "\n");
        sb.append("File type is detected as " + rosbagFileType);
        log.info(sb.toString());

        for (String s : STANARD_TOPICS) {
            String topic = rosbagFileType.header + s;
            topicList.add(topic);
            topicFieldNames.add(topic.substring(topic.lastIndexOf("/") + 1)); // strip off header to get to field name for the ArrayType
        }

        generateMessageIndexes(progressMonitor);
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
    /**
     * Adds information to a message from the index entry for it
     */
    public class MessageWithIndex {

        public MessageType messageType;
        public BagFile.MessageIndex messageIndex;

        public MessageWithIndex(MessageType messageType, BagFile.MessageIndex messageIndex) {
            this.messageType = messageType;
            this.messageIndex = messageIndex;
        }

    }

    synchronized private MessageWithIndex getNextMsg() throws BagReaderException, EOFException {
        MessageType msg = null;
        try {
            msg = bagFile.getMessageFromIndex(msgIndexes, nextMessageNumber);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new EOFException();
        }

        MessageWithIndex rtn = new MessageWithIndex(msg, msgIndexes.get(nextMessageNumber));
        nextMessageNumber++;
        return rtn;
    }

    /**
     * Given ROS Timestamp, this method computes the us timestamp relative to
     * the first timestamp in the recording
     *
     * @param timestamp a ROS Timestamp from a Message, either header or DVS
     * event
     * @return timestamp for jAER in us
     */
    private int getTimestampUsRelative(Timestamp timestamp) {
        long tsNs = timestamp.getNanos(); // gets the fractional seconds in ns
        // https://docs.oracle.com/javase/8/docs/api/java/sql/Timestamp.html "Only integral seconds are stored in the java.util.Date component. The fractional seconds - the nanos - are separate."
        long tsMs = timestamp.getTime(); // the time in ms including ns, i.e. time(s)*1000+ns/1000000. 
        long timestampUsAbsolute = (1000000 * (tsMs / 1000)) + tsNs / 1000; // truncate ms back to s, then turn back to us, then add fractional part of s in us
        if (!firstTimestampWasRead) {
            firstTimestampUsAbsolute = timestampUsAbsolute;
            firstTimestampWasRead = true;
        }
        int ts = (int) (timestampUsAbsolute - firstTimestampUsAbsolute);
//        if (ts == 0) {
//            log.warning("zero timestamp detected for Image ");
//        }
        if (ts > largestTimestamp) {
            largestTimestamp = ts;
        }
        final int dt = ts - mostRecentTimestamp;
        if (dt < 0 && nonMonotonicTimestampExceptionsChecked) {
            if (nonmonotonicTimestampCounter % NONMONOTONIC_TIMESTAMP_WARNING_INTERVAL == 0) {
                log.warning("Nonmonotonic timestamp=" + timestamp + " with dt=" + dt + "; replacing with largest timestamp=" + largestTimestamp + "; skipping next " + NONMONOTONIC_TIMESTAMP_WARNING_INTERVAL + " warnings");
            }
            nonmonotonicTimestampCounter++;
            ts = largestTimestamp; // replace actual timestamp with largest one so far
            nonMonotonicTimestampDetected = true;
        } else {
            nonMonotonicTimestampDetected = false;
        }
        mostRecentTimestamp = ts;
        return ts;
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
    synchronized private AEPacketRaw getNextRawPacket() throws EOFException, BagReaderException {
        DavisBaseCamera davisCamera = null;
        if (chip instanceof DavisBaseCamera) {
            davisCamera = (DavisBaseCamera) chip;
        }
        OutputEventIterator<ApsDvsEvent> outItr = eventPacket.outputIterator();
        try {
            boolean gotEventsOrFrame = false;
            while (!gotEventsOrFrame) {
                MessageWithIndex message = getNextMsg();
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
                        try {
                            int exposureUs = messageType.<Int32Type>getField("data").getValue(); // message seems to be exposure in ms as float although https://github.com/uzh-rpg/rpg_dvs_ros/blob/master/davis_ros_driver/src/driver.cpp publishes as Int32, very confusing
                            lastExposureUs = (int) (exposureUs);
                        } catch (Exception e) {
                            float exposureUs = messageType.<Float32Type>getField("data").getValue(); // message seems to be exposure in ms as float although https://github.com/uzh-rpg/rpg_dvs_ros/blob/master/davis_ros_driver/src/driver.cpp publishes as Int32, very confusing
                            lastExposureUs = (int) (exposureUs); // hack to deal with recordings made with pre-Int32 version of rpg-ros-dvs
                        }

                    }
                    break;
                    case "sensor_msgs":
                        switch (type) {
                            case "Image": { // http://docs.ros.org/api/sensor_msgs/html/index-msg.html
                                MessageType messageType = message.messageType;
//                                List<String> fieldNames = messageType.getFieldNames();
                                MessageType header = messageType.getField("header"); // http://docs.ros.org/api/std_msgs/html/msg/Header.html
                                ArrayType data = messageType.<ArrayType>getField("data");
                                int width = (int) (messageType.<UInt32Type>getField("width").getValue()).intValue();
                                int height = (int) (messageType.<UInt32Type>getField("height").getValue()).intValue();
                                Timestamp timestamp = header.<TimeType>getField("stamp").getValue();
                                int ts = getTimestampUsRelative(timestamp);
                                gotEventsOrFrame = true;
                                byte[] bytes = data.getAsBytes();
                                int idx = 0;
                                int sizeY = chip.getSizeY();
                                ApsDvsEvent e = null;
                                // construct frames as events, so that reconstuction as raw packet results in frame again. what a hack...
                                // start of frame
                                e = outItr.nextOutput();
                                e.setReadoutType(ApsDvsEvent.ReadoutType.SOF);
                                e.x = (short) 0;
                                e.y = (short) 0;
                                e.setTimestamp(ts);
                                // start of exposure
                                e = outItr.nextOutput();
                                e.setReadoutType(ApsDvsEvent.ReadoutType.SOE);
                                e.x = (short) 0;
                                e.y = (short) 0;
                                e.setTimestamp(ts);
                                for (int f = 0; f < 2; f++) { // reset/signal pixels samples
                                    for (int y = 0; y < height; y++) {
                                        for (int x = 0; x < width; x++) {
                                            e = outItr.nextOutput();
                                            e.setReadoutType(f == 0 ? ApsDvsEvent.ReadoutType.ResetRead : ApsDvsEvent.ReadoutType.SignalRead);
                                            e.x = (short) x;
                                            e.y = (short) (sizeY - y - 1);
                                            e.setAdcSample(f == 0 ? 255 : (255 - (0xff & bytes[idx++])));
                                            if (davisCamera == null) {
                                                e.setTimestamp(ts);
                                            } else {
                                                if (davisCamera.lastFrameAddress((short) x, (short) y)) {
                                                    e.setTimestamp(ts + lastExposureUs); // set timestamp of last event written out to the frame end timestamp, TODO complete hack to have 1 pixel with larger timestamp
                                                } else {
                                                    e.setTimestamp(ts);
                                                }
                                            }
                                        }
                                    }
                                }

                                // end of exposure event
                                e = outItr.nextOutput();
                                e.setReadoutType(ApsDvsEvent.ReadoutType.EOE);
                                e.x = (short) 0;
                                e.y = (short) 0;
                                e.setTimestamp(ts + lastExposureUs); // TODO should really be end of exposure timestamp, have to get that from last exposure message
                                // end of frame event
                                e = outItr.nextOutput();
                                e.setReadoutType(ApsDvsEvent.ReadoutType.EOF);
                                e.x = (short) 0;
                                e.y = (short) 0;
                                e.setTimestamp(ts + lastExposureUs);

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
                                Timestamp timestamp = header.<TimeType>getField("stamp").getValue();
                                int ts = getTimestampUsRelative(timestamp);
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
                                float xacc = (float) (angular_velocity.<Float64Type>getField("x").getValue().doubleValue());
                                float yacc = (float) (angular_velocity.<Float64Type>getField("y").getValue().doubleValue());
                                float zacc = (float) (angular_velocity.<Float64Type>getField("z").getValue().doubleValue());
                                short[] buf = new short[7];

                                buf[IMUSampleType.ax.code] = (short) (G_PER_MPS2 * xacc / IMUSample.getAccelSensitivityScaleFactorGPerLsb()); // TODO set these scales from caer parameter messages in stream
                                buf[IMUSampleType.ay.code] = (short) (G_PER_MPS2 * yacc / IMUSample.getAccelSensitivityScaleFactorGPerLsb());
                                buf[IMUSampleType.az.code] = (short) (G_PER_MPS2 * zacc / IMUSample.getAccelSensitivityScaleFactorGPerLsb());

                                buf[IMUSampleType.gx.code] = (short) (DEG_PER_RAD * xrot / IMUSample.getGyroSensitivityScaleFactorDegPerSecPerLsb());
                                buf[IMUSampleType.gy.code] = (short) (DEG_PER_RAD * yrot / IMUSample.getGyroSensitivityScaleFactorDegPerSecPerLsb());
                                buf[IMUSampleType.gz.code] = (short) (DEG_PER_RAD * zrot / IMUSample.getGyroSensitivityScaleFactorDegPerSecPerLsb());
                                ApsDvsEvent e = null;
                                e = outItr.nextOutput();
                                IMUSample imuSample = new IMUSample(ts, buf);
                                e.setImuSample(imuSample);
                                e.setTimestamp(ts);
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
                                    Timestamp timestamp = (Timestamp) eventMsg.<TimeType>getField("ts").getValue();
                                    int ts = getTimestampUsRelative(timestamp); // sets nonMonotonicTimestampDetected flag, faster than throwing exception
                                    ApsDvsEvent e = outItr.nextOutput();
                                    e.setReadoutType(ApsDvsEvent.ReadoutType.DVS);
                                    e.timestamp = ts;
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
            throw new BagReaderException(ex);
        }
        AEPacketRaw aePacketRawCollecting = chip.getEventExtractor().reconstructRawPacket(eventPacket);
        fireInitPropertyChange();
        return aePacketRawCollecting;
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
        while (aePacketRawBuffered.getNumEvents() < numEventsToRead && aePacketRawBuffered.getNumEvents() < AEPacketRaw.MAX_PACKET_SIZE_EVENTS) {
            try {
                aePacketRawBuffered.append(getNextRawPacket());
            } catch (EOFException ex) {
                rewind();
                return readPacketByNumber(numEventsToRead);
            } catch (BagReaderException ex) {
                throw new IOException(ex);
            }
        }
        AEPacketRaw.copy(aePacketRawBuffered, 0, aePacketRawOutput, 0, numEventsToRead); // copy over collected events
        // now use tmp packet to copy rest of buffered to, and then make that the new buffered
        aePacketRawTmp.setNumEvents(0);
        AEPacketRaw.copy(aePacketRawBuffered, numEventsToRead, aePacketRawTmp, 0, aePacketRawBuffered.getNumEvents() - numEventsToRead);
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

    @Override
    synchronized public AEPacketRaw readPacketByTime(int dt) throws IOException {
        int oldPosition = nextMessageNumber;
        if (dt < 0) {
            return emptyPacket;
        }
        int newEndTime = currentStartTimestamp + dt;
        aePacketRawOutput.setNumEvents(0);
        while (aePacketRawBuffered.isEmpty()
                || (aePacketRawBuffered.getLastTimestamp() < newEndTime
                && aePacketRawBuffered.getNumEvents() < AEPacketRaw.MAX_PACKET_SIZE_EVENTS)) {
            try {
                aePacketRawBuffered.append(getNextRawPacket()); // reaching EOF here will throw EOFException
            } catch (EOFException ex) {
                aePacketRawBuffered.clear();
                rewind();
                return readPacketByTime(dt);
            } catch (BagReaderException ex) {
                throw new IOException(ex);
            }
        }
        int[] ts = aePacketRawBuffered.getTimestamps();
        int idx = 0;
        while (ts[idx] < newEndTime && idx < aePacketRawBuffered.getNumEvents()) {
            idx++;
        }
        // idx counts event with higher timestamp than we want to end, i.e. one more than we want
        idx--; // make it exactly number we want
        if (idx < 0) {
            idx = 0; // make sure we don't do something illegal here
        }
        AEPacketRaw.copy(aePacketRawBuffered, 0, aePacketRawOutput, 0, idx); // copy over collected events
        // now use tmp packet to copy rest of buffered to, and then make that the new buffered
        aePacketRawTmp.setNumEvents(0);
        AEPacketRaw.copy(aePacketRawBuffered, idx, aePacketRawTmp, 0, aePacketRawBuffered.getNumEvents() - idx);
        AEPacketRaw tmp = aePacketRawBuffered;
        aePacketRawBuffered = aePacketRawTmp;
        aePacketRawTmp = tmp;
        currentStartTimestamp = newEndTime;
        getSupport().firePropertyChange(AEInputStream.EVENT_POSITION, oldPosition, nextMessageNumber);
        maybeSendRewoundEvent(oldPosition);
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
        return nextMessageNumber;
    }

    @Override
    public void position(long n) {
        // TODO, will be hard
    }

    @Override
    synchronized public void rewind() throws IOException {
        nextMessageNumber = 0;
        currentStartTimestamp = (int) firstTimestamp;
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
        largestTimestamp = Integer.MIN_VALUE;
        aePacketRawBuffered.clear();
    }

    @Override
    synchronized public void setFractionalPosition(float frac) {
        nextMessageNumber = (int) (frac * numMessages); // must also clear partially accumulated events in collecting packet and reset the timestamp
        clearAccumulatedEvents();
        try {
            aePacketRawBuffered.append(getNextRawPacket()); // reaching EOF here will throw EOFException
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
    }

    @Override
    public long setMarkIn() {
        markIn = nextMessageNumber;
        return markIn;
    }

    @Override
    public long setMarkOut() {
        markOut = nextMessageNumber;
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
        file = null;
        System.gc();
    }

    @Override
    public int getCurrentStartTimestamp() {
        return (int) lastTimestamp;
    }

    @Override
    synchronized public void setCurrentStartTimestamp(int currentStartTimestamp) {
        this.currentStartTimestamp = currentStartTimestamp;
        nextMessageNumber = (int) (numMessages * (float) currentStartTimestamp / getDurationUs());
        aePacketRawBuffered.clear();
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        close();
    }

    private void generateMessageIndexes(ProgressMonitor progressMonitor) throws BagReaderException, InterruptedException {
        if (wasIndexed) {
            return;
        }
        log.info("creating index for all topics");
        if (!maybeLoadCachedMsgIndexes(progressMonitor)) {
            msgIndexes = bagFile.generateIndexesForTopicList(topicList, progressMonitor);
            cacheMsgIndexes();
        }
        numMessages = msgIndexes.size();
        markIn = 0;
        markOut = numMessages;
        firstTimestamp = getTimestampUsRelative(msgIndexes.get(0).timestamp);
        lastTimestamp = getTimestampUsRelative(msgIndexes.get(numMessages - 1).timestamp);
        wasIndexed = true;
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
            log.info("cached the index for rosbag file " + getFile() + " in " + file);
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
            return true;
        } catch (Exception e) {
            log.warning("could load cached message index from disk: " + e.toString());
            return false;
        }
    }

    private String messageIndexesCacheFileName() {
        return System.getProperty("java.io.tmpdir") + File.separator + getFile().getName() + ".rosbagidx";
    }

}
