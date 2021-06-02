package net.sf.jaer.eventio.ros;

import com.github.swrirobotics.bags.reader.BagFile;
import com.github.swrirobotics.bags.reader.BagReader;
import com.github.swrirobotics.bags.reader.MessageHandler;
import com.github.swrirobotics.bags.reader.TopicInfo;
import com.github.swrirobotics.bags.reader.exceptions.BagReaderException;
import com.github.swrirobotics.bags.reader.exceptions.UninitializedFieldException;
import com.github.swrirobotics.bags.reader.messages.serialization.ArrayType;
import com.github.swrirobotics.bags.reader.messages.serialization.BoolType;
import com.github.swrirobotics.bags.reader.messages.serialization.Field;
import com.github.swrirobotics.bags.reader.messages.serialization.MessageType;
import com.github.swrirobotics.bags.reader.messages.serialization.TimeType;
import com.github.swrirobotics.bags.reader.messages.serialization.UInt16Type;
import com.github.swrirobotics.bags.reader.records.Connection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.PolarityEvent.Polarity;

public class ExampleRosBagReader {

    /*
    [5:02:48 PM] HongMing Chen: There are also some message type defined by ROS
[5:03:06 PM] HongMing Chen: http://docs.ros.org/api/sensor_msgs/html/msg/Image.html
[5:03:20 PM] HongMing Chen: You could find Docs here
[5:03:51 PM] HongMing Chen: I take " sensor_msgs/Image "as example
[5:04:42 PM] HongMing Chen: It was raining yesterday
[5:05:25 PM] HongMing Chen: So we just went back to INI lobby to do more recording
[5:06:31 PM] HongMing Chen: Now, I set up experiment and running train script on machine
[5:34:46 PM] Yuhuang Hu: tobi, in case you are still searching for the message definition, here is for event and event array.
[5:34:47 PM] Yuhuang Hu: https://github.com/uzh-rpg/rpg_dvs_ros/tree/master/dvs_msgs/msg
[5:35:28 PM] Yuhuang Hu: so for example of event packet, each one will have a header that documents the sequence id (which is not so important) and timestamp for the packet
[5:35:39 PM] Yuhuang Hu: then camera resolution in height and width
[5:36:05 PM] Yuhuang Hu: and then an array of events which is in the field "events"
[5:36:38 PM] Yuhuang Hu: each element in that array is an event, which has four fields: x (int), y(int), pol(bool), and timestamp
[5:37:09 PM] Yuhuang Hu: the timestamp in ROS has sec part and nsec part, in this way, it can record message in nanosecond level.
[5:38:36 PM] Yuhuang Hu: and there should be two functions to each of the timestamp which are to_sec() and to_nsec() (in python), I recommend to use these functions (especially to_nsec()) instead of unpacking them yourself because they cut leading 0s for the nsec part.
[5:38:49 PM] Yuhuang Hu: I was trapped by this mistake.
[5:39:37 PM] Yuhuang Hu: the APS message is simply a sensor_msgs/Image object, which also has a header that documents timestamp, sequence id, and camera resolution.
[5:40:19 PM] Yuhuang Hu: and then there is another field for the image message to record all the image data in unsigned int8
[5:42:15 PM] Yuhuang Hu: one thing you might want to check is the timestamp in the header and timestamp for the message. this may sound very strange but I noticed one case that the timestamp in the header is wrong. this is indeed a rare case. however in that case, the timestamp is wrong by around 2000 secs.
[5:43:21 PM] Yuhuang Hu: You can also extract camera info that documents all bias settings and IMU data.
[5:43:46 PM] Yuhuang Hu: let me know if you have any other problems while unpacking them.
    
     */
    public static void main(String[] args) throws BagReaderException {
        BagFile file = null;
        try {
            file = BagReader.readFile("\\\\sensors-nas.lan.ini.uzh.ch\\sensors\\HongmingYuhaungTobiTraxxas\\rosbag\\ShortAngle\\INI foyer\\cw_foyer_record_12_12_17_test.bag");
        } catch (BagReaderException e) {
            file = BagReader.readFile("C:\\Users\\Tobi\\Downloads\\ccw_foyer_record_12_12_17_test.bag");
        }
        file.printInfo();
        System.out.println("Topics:");
        for (TopicInfo topic : file.getTopics()) {
            System.out.println(topic.getName() + " \t\t" + topic.getMessageCount()
                    + " msgs \t: " + topic.getMessageType() + " \t"
                    + (topic.getConnectionCount() > 1 ? ("(" + topic.getConnectionCount() + " connections)") : ""));
        }
        System.out.println("duration: " + file.getDurationS() + "s");
        System.out.println("Chunks: " + file.getChunks().size());
        System.out.println("Num messages: " + file.getMessageCount());
        final EventPacket<PolarityEvent> eventPacket = new EventPacket(PolarityEvent.class);
//        file.printInfo(); // seems to hang, maybe just slow over network
        file.forMessagesOnTopic("/dvs/events", new MessageHandler() {
            @Override
            public boolean process(MessageType message, Connection connection) {
                try {
                    Field f = message.getField("events");
                    List<String> fns = message.getFieldNames();
                    ArrayType data = message.<ArrayType>getField("events");
                    String type = data.getType(); // "Event[]"
                    List<Field> eventFields = data.getFields();
                    int nEvents = eventFields.size();
                    OutputEventIterator<PolarityEvent> outItr = eventPacket.outputIterator();
                    for (Field eventField : eventFields) {
                        MessageType eventMsg = (MessageType) eventField;
                        // https://github.com/uzh-rpg/rpg_dvs_ros/tree/master/dvs_msgs/msg]
                        PolarityEvent e = outItr.nextOutput();
                        int x = eventMsg.<UInt16Type>getField("x").getValue();
                        int y = eventMsg.<UInt16Type>getField("y").getValue();
                        boolean pol = eventMsg.<BoolType>getField("polarity").getValue();
                        int tsMs = (int) eventMsg.<TimeType>getField("ts").getValue().getTime();
                        int tsNs = (int) eventMsg.<TimeType>getField("ts").getValue().getNanos();
                        e.x = (short) x;
                        e.y = (short) y;
                        e.polarity = pol ? Polarity.On : Polarity.Off;
                        e.timestamp = tsMs * 1000 + tsNs / 1000;
                    }
                    System.out.println(eventPacket.toString());

//java.sql.Timestamp
                    return true;
                } catch (UninitializedFieldException ex) {
                    Logger.getLogger(ExampleRosBagReader.class.getName()).log(Level.SEVERE, null, ex);
                    return false;
                }
            }
        });
    }

}
