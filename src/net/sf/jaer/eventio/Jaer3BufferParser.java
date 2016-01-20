/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.eventio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.util.logging.Logger;
import net.sf.jaer.aemonitor.EventRaw;

/**
 * This class parses the buffer from the data files or network streams containing jAER 3.0
 * format data, as specified in http://inilabs.com/support/software/fileformat/
 * 
 * The most useful public interface for outside is the GetJaer2EventBuf(), by this method, the event
 * buffer just include addr and timestamp (like jAER 2.0 did) will be returned. After get this
 * buffer similar to jAER 2.0, all other things will be processed by AEFileInputStream. The stream
 * will be treated like it's a jAER 2.0 buffer. 
 * 
 * @author tobi
 */
public class Jaer3BufferParser {
    
    private static final Logger log = Logger.getLogger("net.sf.jaer.eventio");
    private int BUFFER_CAPACITY_BYTES = 8 * 1000000;
    private ByteBuffer in = ByteBuffer.allocate(BUFFER_CAPACITY_BYTES);
    // private ByteBuffer out = ByteBuffer.allocate(BUFFER_CAPACITY_BYTES);
    
    private PacketDescriptor packetHeader = new PacketDescriptor();
    private int currentPktPos = 0;
    
    private int PKT_HEADER_SIZE = 28;
    
    // private EventRaw tmpEventRaw = new EventRaw();
    
    public Jaer3BufferParser(MappedByteBuffer byteBuffer) throws IOException {
        in = byteBuffer;
        in.order(ByteOrder.LITTLE_ENDIAN);    //AER3.0 spec is little endian
        findAndSetPacketHeader(1);
    }

    public int GetCurrentEventOffset() throws IOException {
        int currentPosition = in.position();
        int eventSize = packetHeader.eventSize;
        int nextPktPos = currentPktPos + packetHeader.eventNumber * packetHeader.eventSize + PKT_HEADER_SIZE;
        
        if(currentPosition >= nextPktPos) {  // current position is not in the current packet, so we need to update currentPktPos
            findAndSetPacketHeader(-1);        // TODO: In fact, findAndSetPacketHeader() just work when currentPosition equals to nextPktPos
                                             // When currentPosition > nextPktPos, it can only find the next packet and not the current packet
                                             // So it should add a method to find the current packet even when the current position > current packet header position
        }
        
        // Now we get the correct currentPktPos
        if(currentPosition - currentPktPos <= PKT_HEADER_SIZE && currentPosition - currentPktPos >=0) {  //current position is in the packet header, then the offset is the first event offset
            return currentPktPos + PKT_HEADER_SIZE;
        }
        
        // currentPktPos + PKT_HEADER_SIZE is the first event offset, so (currentPosition - (currentPktPos + PKT_HEADER_SIZE))%eventSize is the distance
        // between current position and current event offset
        return currentPosition - (currentPosition - (currentPktPos + PKT_HEADER_SIZE))%eventSize;     
    }
    
    public int GetNextEventOffset() throws IOException {
        int eventSize = packetHeader.eventSize;
        int currentEventOffset = GetCurrentEventOffset();
        int currentPosition = in.position();
        int nextPktPos = currentPktPos + packetHeader.eventNumber * packetHeader.eventSize + PKT_HEADER_SIZE;        
        
        // It means it's on the event offset right now, so the next event of this position is itself
        // We can return the currentEventOffset directly, no matter it is in the middle events or the last event
        if(currentPosition == currentEventOffset) { 
            return currentEventOffset;
        }
        
        // The current position is in the last event of the current packet and not the event header, so the next event will be in the next packet.
        // We should update the currentPktPos first.
        if(currentEventOffset + eventSize >= nextPktPos) {
            findAndSetPacketHeader(1);
            return currentPktPos + PKT_HEADER_SIZE;
        }
        
        // Now we get the correct currentPktPos
        if(currentPosition - currentPktPos <= PKT_HEADER_SIZE && currentPosition - currentPktPos >=0) {  //current position is in the packet header, then the next offset is the first event offset
            return currentPktPos + PKT_HEADER_SIZE;
        }
        
        return currentEventOffset + eventSize;
    }
   
    public ByteBuffer GetJaer2EventBuf() throws IOException {
        int nextEventOffset = GetNextEventOffset();
        int addrOffset = GetAddrOffset();
        int tsOffset = packetHeader.eventTSOffset;
        ByteBuffer jaer2Buffer = ByteBuffer.allocate(8);
        
        in.position(nextEventOffset + addrOffset);
        int addr =  in.getInt();
        in.position(nextEventOffset + tsOffset);
        int ts = in.getInt();
        
        jaer2Buffer.putInt(addr);
        jaer2Buffer.putInt(ts);
        jaer2Buffer.flip();
        return jaer2Buffer;
    }
    
    private int GetAddrOffset() throws IOException {
        int eventAddrOffset = 0;
        
        switch (packetHeader.eventType) {
                case PolarityEvent:
                    eventAddrOffset = 0;
                    break;
                    //readNextPacket();                    ;
                case FrameEvent:
                    eventAddrOffset = 0;
                    // readFrame(packetHeader);
                    break;
                case SampleEvent:
                    eventAddrOffset = 0;
                    break;
                case ConfigEvent:
                    eventAddrOffset = 0;
                    break;
                case Imu6Event:
                    eventAddrOffset = 0;
                    break;
                case Imu9Event:
                    eventAddrOffset = 0;
                    break;
                default: eventAddrOffset = 0;
            }
 
        return eventAddrOffset;
    }
       
 

    private void findAndSetPacketHeader(int direction) throws IOException {
        PacketDescriptor d = new PacketDescriptor();
        int eventTypeInt;    
        int currentPosition = in.position();  //store current position, guarntee the position didn't change in this function


        if(direction != 1 && direction != -1) {
            log.warning("Search direction can only be 1(forward) or -1(backward!)");
            return;
        }
        /*
        // If in.position is 0, it indicates it's the first time to search the packet, we don't need to calculate the distance 
        // between current position to last currentPktPos, because we still don't have the currentPktPos yet. 
        // This is mainly used to accelerate the search speed by excluding the positions in the current packet.
        if(in.position() != 0) {            
            if((in.position()  - currentPktPos < PKT_HEADER_SIZE + packetHeader.eventNumber * packetHeader.eventSize)) {
                in.position(currentPktPos + PKT_HEADER_SIZE + packetHeader.eventNumber * packetHeader.eventSize);
            }    
        }
        */
        
        
        while((in.position() <= in.limit() - PKT_HEADER_SIZE) || in.position() >= 0) {               
            int currentSearchPosition = in.position();
            eventTypeInt = in.getShort(); 
            //  By default, eventTypeInt should range from 0 to 7
            if(eventTypeInt > 7 || eventTypeInt < 0) {
                in.position(currentSearchPosition + direction);
                continue;
            }
            d.eventType = EventType.values()[eventTypeInt];
            d.eventSource = in.getShort();
            d.eventSize = in.getInt();
            if(d.eventSize <= 0) {
                in.position(currentSearchPosition + direction);
                continue;
            }
            d.eventTSOffset = in.getInt();     //eventoverflow
            
            // timestamp offset can only be 6(Configuration Event), 12 (Frame Event) or 4(other events).
            if(d.eventType  == EventType.ConfigEvent) {
                if(d.eventTSOffset != 6) {
                    in.position(currentSearchPosition + direction);
                    continue;
                }
            }
            else if(d.eventType  == EventType.FrameEvent) {
                if(d.eventTSOffset != 12) {
                    in.position(currentSearchPosition + direction);
                    continue;
                }
            }
            else {
                if(d.eventTSOffset != 4) {
                    in.position(currentSearchPosition + direction);                    
                    continue;
                }
            }
            d.eventTSOverflow = in.getInt();    //eventTSOverflow
            d.eventCapacity = in.getInt();      //eventcapacity
            
            
            d.eventNumber = in.getInt();        //eventnumber
            if(d.eventNumber <= 0) {
                in.position(currentSearchPosition + direction);                
                continue;
            }
            // if(d.eventNumber != d.eventCapacity) continue;
                        
            d.eventValid = in.getInt();         //eventValid
            if(d.eventValid <= 0) {
                in.position(currentSearchPosition + direction);                
                continue;
            }
            
            currentPktPos = in.position() - PKT_HEADER_SIZE;

            if(d.eventNumber >= d.eventValid)  break;

        }
            
        if(in.position() > in.limit() - PKT_HEADER_SIZE) {
            d = null;
            currentPktPos = 0;
        }
            
        packetHeader = d;
        in.position(currentPosition);   //restore the position, because the byteBuffer in AEFileinputStream share the position with in.
    } // findAndSetPacketHeader
    
    public int GetCurrentPktPos() {
        return currentPktPos;
    }
    
    public PacketDescriptor GetPacketHeader() {
        return packetHeader;
    }

    public void setInBuffer(ByteBuffer BufferToBeProcessed) {
        in = BufferToBeProcessed; //To change body of generated methods, choose Tools | Templates.
    }
    
    public enum EventType {

        SpecialEvent, PolarityEvent, FrameEvent, Imu6Event, Imu9Event, SampleEvent, EarEvent, ConfigEvent // ordered according to id code
    }

   public class PacketDescriptor {

        EventType eventType = EventType.SpecialEvent;
        int eventSource = 0;
        int eventSize = 0;
        int eventTSOffset = 0;
        int eventTSOverflow = 0;
        int eventCapacity = 0;
        int eventNumber = 0;
        int eventValid = 0;
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
   
   
}
