/*
 * AESpreadInterface.java
 *
 * Created on January 22, 2007, 10:31 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright January 22, 2007 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package net.sf.jaer.eventio;

import net.sf.jaer.aemonitor.*;
import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.*;
import java.util.Random;
import java.util.logging.*;
import spread.*;

/**
 * Implements Spread connections to input or output AEPacketRaw packets to and other objects to machines via the
 (<a href="http://www.spread.org/index.html">Spread Toolit</a>.
 
 * @author tobi
 */
public class AESpreadInterface {
    private static Logger log=Logger.getLogger("AESpreadInterface");
    private SpreadConnection connection;
    private SpreadGroup group;
    private AEPacketRaw packet=new AEPacketRaw(1024);
    private int sendSequenceNumber=0, receiveSequenceNumber=0;
    private boolean useReliableTransport=false;
    private AEPacketRawMessageFactory messageFactory=null;
    
//    DataInputStream dis=new DataInputStream();
//    DataOutputStream dos=new DataOutputStream();
    
    /** Creates a new instance of AESpreadInterface */
    public AESpreadInterface() {
    }
    
    public void connect() throws UnknownHostException, SpreadException{
        connection=new SpreadConnection();
        int i=new Random().nextInt();
        String myName=System.getProperty("user.name")+Integer.toString(i);
        connection.connect(InetAddress.getByName("localhost"), 0, myName, false, false);
        group = new SpreadGroup();
        group.join(connection, "group");
    }
    
    public void writePacket(AEPacketRaw packet) throws SpreadException{
        if(connection==null) {
            log.warning("no Spread connection");
            return;
        }
        int n=packet.getNumEvents();
        if(n==0) return;
        ByteBuffer buf=ByteBuffer.allocate(computeByteSizeOfPacket(packet));
        int[] ts=packet.getTimestamps();
        int[] addr=packet.getAddresses();
        buf.putInt(sendSequenceNumber++);
        for(int i=0;i<n;i++){
            buf.putShort((short)addr[i]);
            buf.putInt(ts[i]);
        }
//        if(messageFactory==null){
//            SpreadMessage msg=new SpreadMessage();
//            msg.addGroup("group");
//            msg.setFifo();
////            if(useReliableTransport) msg.setReliable();
//            messageFactory=new AEPacketRawMessageFactory(msg);
//        }
//        SpreadMessage msg=messageFactory.createMessage(buf.array());
        SpreadMessage msg=new SpreadMessage();
        msg.addGroup("group");
        msg.setFifo();
        msg.setData(buf.array());
        connection.multicast(msg);
    }
    
    private int computeByteSizeOfPacket(AEPacketRaw packet){
        int n=packet.getNumEvents();
        return n*AENetworkInterfaceConstants.EVENT_SIZE_BYTES+Integer.SIZE/8;
    }
    
    /** @return size of byte[] as used for spread in events, rounding down to nearest events if the packet is chopped off */
    private int computeEventSizeOfPacket(int numBytes){
        return (numBytes-Integer.SIZE/8)/AENetworkInterfaceConstants.EVENT_SIZE_BYTES;
    }
    
    public AEPacketRaw readPacket() throws SpreadException {
        if(connection==null) return null;
        if(connection.poll()==false) return null;
        SpreadMessage msg=null;
        try{
            msg=connection.receive();
        }catch(InterruptedIOException e){
            return null;
        }
        if(!msg.isRegular()){
            log.info(msg.toString());
            return null;
        }
        byte[] bytes=msg.getData();
        ByteBuffer buf=ByteBuffer.wrap(bytes);
        int n=computeEventSizeOfPacket(bytes.length);
        packet.ensureCapacity(n);
        packet.setNumEvents(0);
        int[] ts=packet.getTimestamps();
        int[] addr=packet.getAddresses();
        int thisSequenceNumber=buf.getInt();
        if(thisSequenceNumber!=receiveSequenceNumber+1){
            log.warning("dropped "+(thisSequenceNumber-receiveSequenceNumber-1)+" packets");
        }
        receiveSequenceNumber=thisSequenceNumber;
        for(int i=0;i<n;i++){
            addr[i]=buf.getShort();
            ts[i]=buf.getInt();
        }
        packet.setNumEvents(n);
        return packet;
    }
    
    public void disconnect(){
        try {
            connection.disconnect();
        } catch (SpreadException ex) {
            ex.printStackTrace();
        }finally{
            connection=null;
        }
    }
    
    public boolean isUseReliableTransport() {
        return useReliableTransport;
    }
    
    public void setUseReliableTransport(boolean useReliableTransport) {
        this.useReliableTransport = useReliableTransport;
    }
    
    private class AEPacketRawMessageFactory extends MessageFactory{
        public AEPacketRawMessageFactory(SpreadMessage msg){
            super(msg);
        }
        public SpreadMessage createMessage(byte[] data) {
            SpreadMessage message = super.createMessage();
            message.setData(data);
            return message;
        }
    }
}
