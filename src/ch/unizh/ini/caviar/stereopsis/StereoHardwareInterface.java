/*
 * NewClass.java
 *
 * Created on March 14, 2006, 7:12 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright March 14, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.caviar.stereopsis;

import ch.unizh.ini.caviar.aemonitor.*;
import ch.unizh.ini.caviar.aemonitor.AEListener;
import ch.unizh.ini.caviar.aemonitor.AEMonitorInterface;
import ch.unizh.ini.caviar.aemonitor.AEPacketRaw;
import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.hardwareinterface.HardwareInterfaceException;
import ch.unizh.ini.caviar.hardwareinterface.usb.*;
import ch.unizh.ini.caviar.hardwareinterface.usb.ReaderBufferControl;
import java.util.logging.Logger;

/**
 * A hardware interface to a stereo pair of sensors. 
 This class merges the data from two AEMonitorInterface's to a single
 * unified stereo stream, with events sorted by timestamp in 
 the output packets. This class also deals with the awkwardness of the fact
 * that you are not guarenteed to get all events in order of generation 
 from the devices owing to buffering. Events from one source (say the left eye)
 * are held back until it is assured there are no earlier events from the other source
 (the right eye).
 *
 * @author tobi
 */
public class StereoHardwareInterface implements AEMonitorInterface, ReaderBufferControl {
    
    protected AEChip chip;
    
    public void setChip(AEChip chip) {
        this.chip=chip;
    }
    
    public AEChip getChip() {
        return chip;
    }
    
    /** Initial capacity of output buffer that is reused for outputting merged event stream */
    public final int INITIAL_CAPACITY=CypressFX2.AE_BUFFER_SIZE;
    
    /** timeout in us for events that cannot be sent because no event with a later time has come from the other eye */
//    public final int TIMEOUT_US=10000;
    
    protected AEMonitorInterface aemonLeft, aemonRight;
    Logger log=Logger.getLogger("HardwareInterface");
    
    public StereoHardwareInterface(AEMonitorInterface left, AEMonitorInterface right){
        aemonLeft=left;
        aemonRight=right;
        aeLeft=new AEFifo(null);
        aeRight=new AEFifo(null);
    }
    
    // these packets are references to the packets from each source
    AEFifo aeLeft, aeRight;
    
    // this packet is re-used for outputting the merged events
    AEPacketRaw aeOut=new AEPacketRaw(INITIAL_CAPACITY*2);
    
    
//    AEPacketRaw bufLeft=new AEPacketRaw(BUFFER_CAPACITY); // holds events that arrive after the last event from the other packet.
//    AEPacketRaw bufRight=new AEPacketRaw(BUFFER_CAPACITY); // holds events that arrive after the last event from the other packet.
    
    // these are last timestamps from each source.
    int lastLeftTimestamp=0, lastRightTimestamp=0;
    
    // this class encapsulates an AEPacketRaw so that it is a kind of FIFO (at least FO). we can popNextEvent events from
    // the AEFifo in their order and check if there are any more available.
    
    /** A FIFO for raw events */
    class AEFifo{
        long timeAcquired=0;
        /** Constructs a new AE FIFO from an AEPacketRaw
         @param ae the raw packet to use as source of events
         */
        AEFifo(AEPacketRaw ae){
            this.ae=ae;
            next=0;
            timeAcquired=System.currentTimeMillis();
        }
        AEPacketRaw ae;
        int next=0;
        /** @return next event, or null if there are no more */
        EventRaw popNextEvent(){
            if(next<ae.getNumEvents()){
                return ae.getEvent(next++);
            }else return null;
        }
        boolean isEmpty(){
            if(ae==null || next>=ae.getNumEvents())
                return true;
            else
                return false;
        }
        /** resets FIFO so that next event is first event
         @param ae the raw packet that will be the new source of events
         */
        void reset(AEPacketRaw ae){
            this.ae=ae;
            next=0;
            timeAcquired=System.currentTimeMillis();
        }
        int peekNextTimestamp(){
            return ae.getEvent(next).timestamp;
        }
        long getTimeAcquired(){
            return timeAcquired;
        }
        public String toString(){
            return "AEFifo with "+ae.getNumEvents();
        }
    }
    
    /** the two inputs have their timestamps reset when the first timestamp of each most recent packet differs by this much */
    public static final int RESET_TIMESTAMPS_THRESHOLD_DT_US=100000;
    
    /** @return an AEPacketRaw of events from both devices, sorted by timestamp.
     * As a hack (hopefully temporary), the MSB is set on the raw addresses of the events from the right device. */
    synchronized public AEPacketRaw acquireAvailableEventsFromDriver() throws HardwareInterfaceException {
        if(!aemonLeft.isEventAcquisitionEnabled()) aemonLeft.setEventAcquisitionEnabled(true);
        if(!aemonRight.isEventAcquisitionEnabled()) aemonRight.setEventAcquisitionEnabled(true);
        int nleft=0, nright=0;

        if(requestTimestampReset){
            requestTimestampReset=false;
            aeRight.reset(null); // isEmpty will return true
            aeLeft.reset(null);
            
            aemonLeft.resetTimestamps(); // call for hardware reset of timestamps
//        if(!chip.getAeViewer().getCaviarViewer().isElectricalSyncEnabled()){
//            log.info("electrical sync is not enalbed so resetTimestamps on right and left retina");
            aemonRight.resetTimestamps();
//        }
        }
        // we only popNextEvent data from an eye if we have used up all the data from it that we got before.
        // we can only output an event (to the user) if we know it is the next event, meaning it has to be
        // followed by an event from the other retina.
//        if(timestampsReset){
//            log.info("waiting for notifications from aemonLeft and aemonRight that timestamps have been reset");
//            synchronized(((CypressFX2)aemonLeft).getAeReader()){
//                try{
//                    wait(); // wait for notification from capture thread that it has reset timestamps
//                }catch(InterruptedException e){
//                    e.printStackTrace();
//                }
//            }
//            synchronized(((CypressFX2)aemonRight).getAeReader()){
//                try{
//                    wait();
//                }catch(InterruptedException e){
//                    e.printStackTrace();
//                }
//            }
//        }
        
        // popNextEvent available events from both sources
        if(aeLeft.isEmpty()){
            aeLeft.reset(aemonLeft.acquireAvailableEventsFromDriver());
            if(requestTimestampReset){
                log.info("after timestampsReset LEFT got "+aeLeft.ae+" t0,t1="+aeLeft.ae.getFirstTimestamp()+", "+aeLeft.ae.getLastTimestamp());
            }
        }
        if(aeRight.isEmpty()){
            aeRight.reset(aemonRight.acquireAvailableEventsFromDriver());
            labelRightEye(aeRight.ae);
            if(requestTimestampReset){
                log.info("after timestampsReset RIGHT got "+aeRight.ae+" t0,t1="+aeRight.ae.getFirstTimestamp()+", "+aeRight.ae.getLastTimestamp());
            }
        }
        
        if( !aeRight.isEmpty()  && !aeLeft.isEmpty() && (aeRight.ae.getNumEvents()>2 || aeLeft.ae.getNumEvents()>2) && (Math.abs(aeRight.getTimeAcquired()-aeLeft.getTimeAcquired())<RESET_TIMESTAMPS_THRESHOLD_DT_US/1000)){
            int dt=Math.abs(aeRight.ae.getLastTimestamp()-aeLeft.ae.getLastTimestamp());
            if(dt>RESET_TIMESTAMPS_THRESHOLD_DT_US){
                log.warning("resetTimestamps because dt="+dt+" > "+RESET_TIMESTAMPS_THRESHOLD_DT_US);
                resetTimestamps();
            }
        }
        
        aeOut.setNumEvents(0);
        while(!aeLeft.isEmpty() && !aeRight.isEmpty()){
            // while both packets still have events, pick the earliest event and write it out
            int tsLeft=aeLeft.peekNextTimestamp(), tsRight=aeRight.peekNextTimestamp();
            if(tsLeft<tsRight){
                aeOut.addEvent(aeLeft.popNextEvent());
                nleft++;
            }else{
                aeOut.addEvent(aeRight.popNextEvent());
                nright++;
            }
        }
        return aeOut;
    }
    
    public int getNumEventsAcquired() {
        return aeOut.getNumEvents();
    }
    
    public AEPacketRaw getEvents() {
        return aeOut;
    }
    
    
    // set to request timestamp reset on next acquireAvailableEventsFromDriver
    private volatile boolean requestTimestampReset=false;
    
    /** resets timestamps from both inputs, serially. The hardware timestamps may be offset by an undetermined amount
     *due to USB latency, context switching, etc. By using electrical synchronization between boards, it may be possible to
     *synchronize perfectly, but this is device-dependent.
     */
    synchronized public void resetTimestamps() {
        // clear out pending events
        requestTimestampReset=true; // this tells rendering thread (which calls acquireAvailableEventsFromDriver) that timestamps have been reset
    }
    
    /** @return true if either device overran the buffer */
    public boolean overrunOccurred() {
        return aemonLeft.overrunOccurred()|| aemonRight.overrunOccurred();
    }
    
    
    /** @return the host AE buffer size. This is for the user buffer, not the device buffer.
     This returns the buffer size for the left device.
     */
    public int getAEBufferSize() {
        return aemonLeft.getAEBufferSize();
    }
    
    /** sets the host AE buffer size. This is for the user buffer, not the device buffer.
     @param AEBufferSize the size in events
     */
    public void setAEBufferSize(int AEBufferSize) {
        aemonLeft.setAEBufferSize(AEBufferSize);
        aemonRight.setAEBufferSize(AEBufferSize);
    }
    
    boolean hasBufferControl(){
        if(aemonLeft==null || aemonRight==null) return false;
        boolean yes=(aemonLeft instanceof ReaderBufferControl) && (aemonRight instanceof ReaderBufferControl);
        if(!yes) log.warning("device doesn't support ReaderBufferControl functionality");
        return yes;
    }
    
    public int getFifoSize() {
        if(!hasBufferControl()) return 0;
        return ((ReaderBufferControl)aemonLeft).getFifoSize();
    }
    
    public void setFifoSize(int fifoSize) {
        if(!hasBufferControl()) return;
        ((ReaderBufferControl)aemonLeft).setFifoSize(fifoSize);
        if(aemonRight==null) return;
        ((ReaderBufferControl)aemonRight).setFifoSize(fifoSize);
        log.info("set stereo ae buffer size to "+fifoSize);
    }
    
    public int getNumBuffers() {
        if(!hasBufferControl()) return 0;
        return ((ReaderBufferControl)aemonLeft).getNumBuffers();
    }
    
    public void setNumBuffers(int numBuffers) {
        if(!hasBufferControl()) return;
        ((ReaderBufferControl)aemonLeft).setNumBuffers(numBuffers);
        ((ReaderBufferControl)aemonRight).setNumBuffers(numBuffers);
    }
    
    /** sets both eyes to acquire events */
    public void setEventAcquisitionEnabled(boolean enable) throws HardwareInterfaceException {
//        log.info("Stereo setEventAcquisitionEnabled="+enable);
        aemonLeft.setEventAcquisitionEnabled(enable);
        aemonRight.setEventAcquisitionEnabled(enable);
    }
    
    /** @return true if both eyes are enabled */
    public boolean isEventAcquisitionEnabled() {
        return aemonLeft.isEventAcquisitionEnabled() && aemonRight.isEventAcquisitionEnabled();
    }
    
    public void addAEListener(AEListener listener) {
    }
    
    public void removeAEListener(AEListener listener) {
    }
    
    public int getMaxCapacity() {
        return aemonLeft.getMaxCapacity();
    }
    
    public int getEstimatedEventRate() {
        return (int)(aemonLeft.getEstimatedEventRate()+aemonRight.getEstimatedEventRate());
    }
    
    public int getTimestampTickUs() {
        return aemonLeft.getTimestampTickUs();
    }
    
    public String[] getStringDescriptors() {
        return aemonLeft.getStringDescriptors();
    }
    
    public int[] getVIDPID() {
        return new int[2];
    }
    
    public short getVID() {
        return 0;
    }
    
    public short getPID() {
        return 0;
    }
    
    public short getDID() {
        return 0;
    }
    
    public String getTypeName() {
        return "Stereo";
    }
    
    public void close() {
        if(aemonLeft!=null) aemonLeft.close();
        if(aemonRight!=null) aemonRight.close();
    }
    
    public void open() throws HardwareInterfaceException {
        aemonLeft.open();
        aemonRight.open();
    }
    
    public boolean isOpen() {
        if(aemonLeft==null || aemonRight==null) return false;
        return aemonLeft.isOpen() && aemonRight.isOpen();
    }
    
    private void labelRightEye(AEPacketRaw aeRawRight) {
        short[] adr=aeRawRight.getAddresses();
        int n=aeRawRight.getNumEvents();
        for(int i=0;i<n;i++){
            adr[i]=(short)(adr[i]|Stereopsis.MASK_RIGHT_ADDR);
        }
    }
    
    private void labelLeftEye(AEPacketRaw aeRawRight) {
        short[] adr=aeRawRight.getAddresses();
        int n=aeRawRight.getNumEvents();
        for(int i=0;i<n;i++){
            adr[i]=(short)(adr[i]&~Stereopsis.MASK_RIGHT_ADDR);
        }
        
    }
    
}
