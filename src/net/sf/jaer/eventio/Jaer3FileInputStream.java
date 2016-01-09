/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventio;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;
import net.sf.jaer.aemonitor.EventRaw;
import net.sf.jaer.chip.AEChip;

/**
 * This class parses the data files or network streams containing jAER 3.0
 * format data, as specified in http://inilabs.com/support/software/fileformat/
 *
 * @author tobi
 */
public class Jaer3FileInputStream {

    private static final Logger log = Logger.getLogger("net.sf.jaer.eventio");
    private int BUFFER_CAPACITY_BYTES = 8 * 1000000;
    private ByteBuffer localByteBuffer = ByteBuffer.allocate(BUFFER_CAPACITY_BYTES);
    private AEChip chip = null;
    private AEFileInputStream f;
    private EventRaw tmpEventRaw = new EventRaw();

    Method eventTranslator = null;

    public Jaer3FileInputStream(AEFileInputStream f, AEChip chip) {
        this.f = f;
        this.chip = chip;
        localByteBuffer.clear().flip(); // clears buffer and flips so that there is nothing in buffer
    }

    private void putInt(int addr) {
        localByteBuffer.putInt(addr);
    }

   public enum EventType {

        SpecialEvent, PolarityEvent, FrameEvent, Imu6Event, Imu9Event, SampleEvent, EarEvent, ConfigEvent // ordered according to id code
    }

   public class PacketDescriptor {

        EventType eventType;
        int eventSource;
        int eventSize;
        int eventTSOffset;
        int eventTSOverflow;
        int eventCapacity;
        int eventNumber;
        int eventValid;
    }
   
   public class FrameDescriptor{
       int frameInfo; // see AEDAT3.0 spec for frame event
       int startOfCaptureTimestamp;
       int endOfCaptureTimestamp;
       int startOfExposureTimestamp;
       int endOfExposureTimestamp;
       int xLength; // width
       int yLength; // height
       int xPosition; // offset of LL corner in pixels
       int yPosition; // offset of LL corner in pixels
   }

 
    public EventRaw readEventForwards() throws IOException {
        try {
            tmpEventRaw.address = localByteBuffer.getInt();
            tmpEventRaw.timestamp = localByteBuffer.getInt();
        } catch (BufferUnderflowException e) {
            readNextPacket();
            tmpEventRaw.address = localByteBuffer.getInt();
            tmpEventRaw.timestamp = localByteBuffer.getInt();
        }
        return tmpEventRaw;

    }

    private void readNextPacket() throws IOException {
        localByteBuffer.clear();
        PacketDescriptor packetDescriptor = readPacketHeader();
        switch (packetDescriptor.eventType) {
            case PolarityEvent:
                readPolarityEvents(packetDescriptor);
                break;
            case FrameEvent:
                readFrame(packetDescriptor);
                break;
            case SampleEvent:
                break;
            case ConfigEvent:
                break;
            case Imu6Event:
                break;
            case Imu9Event:
                break;
            default:
        }

    }

    private PacketDescriptor readPacketHeader() throws IOException {
        PacketDescriptor d = new PacketDescriptor();
        int eventTypeInt = readShort();
        d.eventType = EventType.values()[eventTypeInt];
        d.eventSource = readShort();
        d.eventSize = readInt();
        d.eventTSOffset = readInt();     //eventoverflow
        d.eventTSOverflow = readInt();     //eventcapacity
        d.eventCapacity = readInt();     //eventnumber
        d.eventNumber = readInt();     //eventnumber
        d.eventValid = readInt();     //eventnumber
        return d;
    }

    private short readShort() throws IOException {
        return swapByteOrder(f.getByteBuffer().getShort());
    }

    private int readInt() throws IOException {
        return swapByteOrder(f.getByteBuffer().getInt());
    }

    private void readPolarityEvents(PacketDescriptor packetDescriptor) throws IOException{
        for (int i = 0; i < packetDescriptor.eventNumber; i++) {
            if (i < packetDescriptor.eventValid) { // handle packets that are only valid to a certain point
                int addr = readInt();
                int ts = readInt();
                addr = chip.translateJaer3AddressToJaerAddress(addr);
                putInt(addr);
                putInt(ts);
            }
        }
        localByteBuffer.flip();

    }

    private void readEvent() {

    }

    private void readImu6() {

    }

    private void readImu9() {

    }

    private void readFrame(PacketDescriptor packetDescriptor) throws IOException {
        FrameDescriptor fd=new FrameDescriptor();
         fd.frameInfo=readInt(); // see AEDAT3.0 spec for frame event
        fd.startOfCaptureTimestamp=readInt();
        fd.endOfCaptureTimestamp=readInt();
        fd.startOfExposureTimestamp=readInt();
        fd.endOfExposureTimestamp=readInt();
        fd.xLength=readInt(); // width
        fd.yLength=readInt(); // height
        fd.xPosition=readInt(); // offset of LL corner in pixels
        fd.yPosition=readInt(); // offset of LL corner in pixels
        
        if(packetDescriptor.eventSize!=2){
            throw new IllegalStateException("wrong event size for frame");
        }
        for (int i = 0; i < packetDescriptor.eventNumber; i++) {
            short pixval=readShort();
            putInt(pixval);
        }
     
        
    }

    // swaps little to big endian int
    private static final int swapByteOrder(int value) {
        int b1 = (value >> 0) & 0xff;
        int b2 = (value >> 8) & 0xff;
        int b3 = (value >> 16) & 0xff;
        int b4 = (value >> 24) & 0xff;

        return b1 << 24 | b2 << 16 | b3 << 8 | b4 << 0;
    }

    // swaps little to big endian short
    private static final short swapByteOrder(short value) {
        int b1 = (value >> 0) & 0xff;
        int b2 = (value >> 8) & 0xff;

        return (short) (b1 << 8 | b2 << 0);
    }

}
