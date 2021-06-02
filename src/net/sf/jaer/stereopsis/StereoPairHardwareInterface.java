/*
 * StereoPairHardwareInterface.java
 *
 * Created on March 14, 2006, 7:12 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright March 14, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */
package net.sf.jaer.stereopsis;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.logging.Logger;

import net.sf.jaer.aemonitor.AEListener;
import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.aemonitor.EventRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.ReaderBufferControl;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2;
/**
 * A hardware interface to two stereo pair sensors.
This class merges the data from two streams to a single
 * unified stream, with events sorted by timestamp in 
the output packets, depending on the value of the ignoreTimestampNonmonotonicity flag.
 * <p>
 * This class also deals with the awkwardness of the fact
 * that you are not guaranteed to get all events in order of generation
from the devices owing to buffering.
 * <p>
 * Depending on the setting of
 * the flag in {@link #setIgnoreTimestampNonmonotonicity(boolean)}, events from one source (say the left eye)
 * are held back until it is assured there are no earlier events from the other source
(the right eye). Setting this flag to ignore non-monotonicity substantially reduces computational overhead,
 * but affects many other aspects of jAER regarding it's inbuilt assumption that time increases monotonically.
 *
 * @author tobi
 */
public class StereoPairHardwareInterface implements AEMonitorInterface,ReaderBufferControl,PropertyChangeListener{
    private PropertyChangeSupport support = new PropertyChangeSupport(this);
    protected AEChip chip;
    private AEMonitorInterface aemonLeft;
    private AEMonitorInterface aemonRight;
    final static Logger log = Logger.getLogger("HardwareInterface");
    private boolean ignoreTimestampNonmonotonicity = false;
    private int RESET_DELAY_MS = 200;

    public void setChip (AEChip chip){
        this.chip = chip;
        if ( getAemonLeft() != null ){
            getAemonLeft().setChip(chip);
        } else{
            log.warning("null left AEMonitorInterface, couldn't set chip for it");
        }
        if ( getAemonRight() != null ){
            getAemonRight().setChip(chip);
        } else{
            log.warning("null right AEMonitorInterface, couldn't set chip for it");
        }
    }

    public AEChip getChip (){
        return chip;
    }
    /** Initial capacity of output buffer that is reused for outputting merged event stream */
    public final int INITIAL_CAPACITY = CypressFX2.AE_BUFFER_SIZE;
    
    public StereoPairHardwareInterface (AEMonitorInterface left,AEMonitorInterface right){
        aemonLeft = left;
        aemonRight = right;
        aeLeft = new AEFifo(null);
        aeRight = new AEFifo(null);
    }
    /** These FIFOs hold packets which are references to the packets from each source */
    private AEFifo aeLeft, aeRight;    // this packet is re-used for outputting the merged events
    private AEPacketRaw aeOut = new AEPacketRaw(INITIAL_CAPACITY * 2);//    AEPacketRaw bufLeft=new AEPacketRaw(BUFFER_CAPACITY); // holds events that arrive after the last event from the other packet.

    public AEMonitorInterface getAemonLeft (){
        return aemonLeft;
    }

    public void setAemonLeft (AEMonitorInterface aemonLeft){
        this.aemonLeft = aemonLeft;
    }

    public AEMonitorInterface getAemonRight (){
        return aemonRight;
    }

    public void setAemonRight (AEMonitorInterface aemonRight){
        this.aemonRight = aemonRight;
    }

    /**
     * @return the support
     */
    public PropertyChangeSupport getSupport (){
        return support;
    }

    /** Fires off the pair's property change events. The event "readerStarted" is fired when the AEReader is started.
     *
     * @param evt from one of the pair of interfaces.
     */
    public void propertyChange (PropertyChangeEvent evt){
        getSupport().firePropertyChange(evt);
    }

    public PropertyChangeSupport getReaderSupport (){
        return support;
    }
    /** A FIFO for raw events. Encapsulates an AEPacketRaw so
     * that it is a kind of FIFO (at least FO).
     * We can popNextEvent events from
     * the AEFifo in their order and check if there are any more available.
     */
    private final class AEFifo{
        long timeAcquired = 0;
        AEPacketRaw ae;
        int next = 0;

        /** Constructs a new AE FIFO from an AEPacketRaw
        @param ae the raw packet to use as source of events
         */
        AEFifo (AEPacketRaw ae){
            this.ae = ae;
            next = 0;
            timeAcquired = System.currentTimeMillis();
        }

        /** @return next event, or null if there are no more */
        final EventRaw popNextEvent (){
            if ( next < ae.getNumEvents() ){
                return ae.getEvent(next++);
            } else{
                return null;
            }
        }

        final boolean isEmpty (){
            if ( ae == null || next >= ae.getNumEvents() ){
                return true;
            } else{
                return false;
            }
        }

        /** resets FIFO so that next event is first event
        @param ae the raw packet that will be the new source of events
         */
        final void reset (AEPacketRaw ae){
            this.ae = ae;
            next = 0;
            timeAcquired = System.currentTimeMillis();
        }

        final int peekNextTimestamp (){
            return ae.getEvent(next).timestamp;
        }

        final long getTimeAcquired (){
            return timeAcquired;
        }

        @Override
        final public String toString (){
            return "AEFifo with timeAcquired=" + timeAcquired + " next=" + next + " holding " + ae.toString();
        }

        final public int size (){
            if ( ae == null ){
                return 0;
            }
            return ae.getNumEvents();
        }
    }
    /** the two inputs have their timestamps reset when the first timestamp of each most recent packet differs by this much */
    public static final int RESET_TIMESTAMPS_THRESHOLD_DT_US = 100000;
    int[] timestamps, addresses;
    int count = 0;

    // helper methods for cheap additions to output packet, to avoid range checks and ensureCapacity calls on every event
    private void initAdd (){
        aeOut.clear();
        aeOut.ensureCapacity(aeLeft.size() + aeRight.size()); // preallocate arrays to ensure we can write to the timestamps/addressses arrays
        timestamps = aeOut.getTimestamps();
        addresses = aeOut.getAddresses();
        count = 0;
    }

    private void finishAdd (){
        aeOut.setNumEvents(count);
    }

    private void addEvent (AEFifo f){
        EventRaw event = f.popNextEvent();
        timestamps[count] = event.timestamp;
        addresses[count++] = event.address;
    }

    /** @return an AEPacketRaw of events from both devices, sorted by timestamp.
     * As a hack (hopefully temporary), the MSB is set on the raw addresses of the events from the right device. 
    @return packet of monotonic timestamp-ordered events from both interfaces
     */
    synchronized public AEPacketRaw acquireAvailableEventsFromDriver () throws HardwareInterfaceException{
        if ( !aemonLeft.isEventAcquisitionEnabled() ){
            getAemonLeft().setEventAcquisitionEnabled(true);
        }
        if ( !aemonRight.isEventAcquisitionEnabled() ){
            getAemonRight().setEventAcquisitionEnabled(true);
        }
        int nleft = 0, nright = 0;

        if ( requestTimestampReset ){
            log.info("resetting timestamps on both interfaces RIGHT=" + getAemonRight() + " LEFT=" + getAemonLeft());
            // TODO tricky here because it is not clear how to know that all data from each interface has been flushed after the
            // timestamp reset. If we don't flush enough data, we get old timestamps on one interface.

            // these next calls usually call VendorRequests to send USB control packets to the interfaces.
            // These calls are blocking.
            getAemonLeft().resetTimestamps(); // call for hardware reset of timestamps
            getAemonRight().resetTimestamps();  // on other interface too
            try{
                log.info("Sleeping " + RESET_DELAY_MS + " ms after resetTimestamps() on each interface");
                Thread.sleep(RESET_DELAY_MS); // sleep to let vendor requests get out there
            } catch ( InterruptedException e ){
            }

            getAemonLeft().acquireAvailableEventsFromDriver();
            getAemonRight().acquireAvailableEventsFromDriver(); // flush events that may be old

            aeRight.reset(null); // isEmpty will return true
            aeLeft.reset(null);

            try{
                Thread.sleep(RESET_DELAY_MS);
            } catch ( InterruptedException e ){
            }
            // sleep to let vendor requests get out there
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
        } // requestTimestampReset

        // we only popNextEvent data from an eye if we have used up all the data from it that we got before.
        // we can only output an event (to the user) if we know it is the next event, meaning it has to be
        // followed by an event from the other retina.

        initAdd();
        // popNextEvent available events from both sources
        if ( aeLeft.isEmpty() ){
            aeLeft.reset(getAemonLeft().acquireAvailableEventsFromDriver());
            labelLeftEye(aeLeft.ae);
            if ( requestTimestampReset ){
                log.info("after timestampsReset LEFT acquired " + aeLeft.ae + " t0,t1=" + aeLeft.ae.getFirstTimestamp() + ", " + aeLeft.ae.getLastTimestamp());
            }
        }
        if ( aeRight.isEmpty() ){
            aeRight.reset(getAemonRight().acquireAvailableEventsFromDriver());
            labelRightEye(aeRight.ae);
            if ( requestTimestampReset ){
                log.info("after timestampsReset RIGHT acquired " + aeRight.ae + " t0,t1=" + aeRight.ae.getFirstTimestamp() + ", " + aeRight.ae.getLastTimestamp());
            }
        }

        requestTimestampReset = false;

//       // if we haven't gotten events from one interface for too long, reset timestamps 
//        if( !aeRight.isEmpty()  && !aeLeft.isEmpty() && (aeRight.ae.getNumEvents()>2 || aeLeft.ae.getNumEvents()>2) ){ 
//            // && (Math.abs(aeRight.getTimeAcquired()-aeLeft.getTimeAcquired())<RESET_TIMESTAMPS_THRESHOLD_DT_US/1000)
//            int dt=Math.abs(aeRight.ae.getLastTimestamp()-aeLeft.ae.getLastTimestamp());
//            if(dt>RESET_TIMESTAMPS_THRESHOLD_DT_US){
//                log.warning("resetTimestamps because dt="+dt+" > "+RESET_TIMESTAMPS_THRESHOLD_DT_US);
//                resetTimestamps();
//            }
//        }

        if ( !ignoreTimestampNonmonotonicity ){
            // here we order the events and only pass out an event from an interface if there is a later event from the other interface
            while ( !aeLeft.isEmpty() && !aeRight.isEmpty() ){
                // while both packets still have events, pick the earliest event and write it out
                int tsLeft = aeLeft.peekNextTimestamp(), tsRight = aeRight.peekNextTimestamp(); // TODO if there is a big wrap in one interface then times can differ by 1 hour!
                if((long)tsLeft*(long)tsRight<0) {
                    log.warning("huge time difference between left and right eyes detected, flushing both eye event buffers");
                    aeLeft.reset(null);
                    aeRight.reset(null);
                    return aeOut;
                }
                if ( tsLeft < tsRight ){
                    addEvent(aeLeft);
                    nleft++;
                } else{
                    addEvent(aeRight);
                    nright++;
                }
            }
        } else{
            // here just igmore temporal ordering, pass out events from both interfaces, right first
            while ( !aeRight.isEmpty() ){
                addEvent(aeRight);
            }
            while ( !aeLeft.isEmpty() ){
                addEvent(aeLeft);
            }
        }
        finishAdd();
        return aeOut;
    }

    public int getNumEventsAcquired (){
        return aeOut.getNumEvents();
    }

    public AEPacketRaw getEvents (){
        return aeOut;
    }    // set to request timestamp reset on next acquireAvailableEventsFromDriver
    private volatile boolean requestTimestampReset = false;

    /** resets timestamps from both inputs, serially. The hardware timestamps may be offset by an undetermined amount
     *due to USB latency, context switching, etc. By using electrical synchronization between boards, it may be possible to
     *synchronize perfectly, but this is device-dependent.
     */
    synchronized public void resetTimestamps (){
        // clear out pending events
        requestTimestampReset = true; // this tells rendering thread (which calls acquireAvailableEventsFromDriver) that timestamps have been reset
        aemonLeft.resetTimestamps();
        aemonRight.resetTimestamps();
    }

    /** @return true if either device overran the buffer */
    public boolean overrunOccurred (){
        return getAemonLeft().overrunOccurred() || getAemonRight().overrunOccurred();
    }

    /** @return the host AE buffer size. This is for the user buffer, not the device buffer.
    This returns the buffer size for the left device.
     */
    public int getAEBufferSize (){
        return getAemonLeft().getAEBufferSize();
    }

    /** sets the host AE buffer size. This is for the user buffer, not the device buffer.
    @param AEBufferSize the size in events
     */
    public void setAEBufferSize (int AEBufferSize){
        getAemonLeft().setAEBufferSize(AEBufferSize);
        getAemonRight().setAEBufferSize(AEBufferSize);
    }

    boolean hasBufferControl (){
        if ( getAemonLeft() == null || getAemonRight() == null ){
            return false;
        }
        boolean yes = ( getAemonLeft() instanceof ReaderBufferControl ) && ( getAemonRight() instanceof ReaderBufferControl );
        if ( !yes ){
            log.warning("device doesn't support ReaderBufferControl functionality");
        }
        return yes;
    }

    public int getFifoSize (){
        if ( !hasBufferControl() ){
            return 0;
        }
        return ( (ReaderBufferControl)getAemonLeft() ).getFifoSize();
    }

    public void setFifoSize (int fifoSize){
        if ( !hasBufferControl() ){
            return;
        }
        ( (ReaderBufferControl)getAemonLeft() ).setFifoSize(fifoSize);
        if ( getAemonRight() == null ){
            return;
        }
        ( (ReaderBufferControl)getAemonRight() ).setFifoSize(fifoSize);
        log.info("set stereo ae buffer size to " + fifoSize);
    }

    public int getNumBuffers (){
        if ( !hasBufferControl() ){
            return 0;
        }
        return ( (ReaderBufferControl)getAemonLeft() ).getNumBuffers();
    }

    public void setNumBuffers (int numBuffers){
        if ( !hasBufferControl() ){
            return;
        }
        ( (ReaderBufferControl)getAemonLeft() ).setNumBuffers(numBuffers);
        ( (ReaderBufferControl)getAemonRight() ).setNumBuffers(numBuffers);
    }

    /** sets both eyes to acquire events */
    public void setEventAcquisitionEnabled (boolean enable) throws HardwareInterfaceException{
//        log.info("Stereo setEventAcquisitionEnabled="+enable);
        getAemonLeft().setEventAcquisitionEnabled(enable);
        getAemonRight().setEventAcquisitionEnabled(enable);
    }

    /** @return true if both eyes are enabled */
    public boolean isEventAcquisitionEnabled (){
        return getAemonLeft().isEventAcquisitionEnabled() && getAemonRight().isEventAcquisitionEnabled();
    }

    public void addAEListener (AEListener listener){
    }

    public void removeAEListener (AEListener listener){
    }

    public int getMaxCapacity (){
        return getAemonLeft().getMaxCapacity();
    }

    public int getEstimatedEventRate (){
        return (int)( getAemonLeft().getEstimatedEventRate() + getAemonRight().getEstimatedEventRate() );
    }

    public int getTimestampTickUs (){
        return getAemonLeft().getTimestampTickUs();
    }

    /*public String[] getStringDescriptors() {
    return getAemonLeft().getStringDescriptors();
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
    }*/
    public String getTypeName (){
        return "Stereo";
    }

    public void close (){
        if ( getAemonLeft() != null ){
            getAemonLeft().close();
        }
        if ( getAemonRight() != null ){
            getAemonRight().close();
        }
    }

    /** Opens both interfaces. Normally not called because
     * these interfaces are already constructed and opened in the
     * Tmpdiff128StereoPair getHardwareInterface method.
     */
    public void open () throws HardwareInterfaceException{
        addSupport(aemonLeft);
        addSupport(aemonRight);
        getAemonLeft().open();
        getAemonRight().open();
        resetTimestamps(); // tobi added to synchronize two inputs on open

    }

    private void addSupport (AEMonitorInterface aemon){
        try{
            CypressFX2 fx2 = (CypressFX2)aemon;
            PropertyChangeSupport fx2Support = fx2.getSupport();
            // propertyChange method in this file deals with these events
            if ( !fx2Support.hasListeners("readerStarted") ){
                fx2Support.addPropertyChangeListener("readerStarted",this); // when the reader starts running, we get called back to fix device control menu
            }
        } catch ( Exception e ){
            log.warning("couldn't add readerChanged property change to " + getAemonRight());
        }
    }

    /** Returns true if both interfaces are open.
     *
     * @return true if both open.
     */
    public boolean isOpen (){
        if ( getAemonLeft() == null || getAemonRight() == null ){
            return false;
        }
        return getAemonLeft().isOpen() && getAemonRight().isOpen();
    }

    /** Labels all events in raw packet as coming from right eye
     * 
     * @param aeRawRight
     */
    public void labelRightEye (AEPacketRaw aeRawRight){
        int[] adr = aeRawRight.getAddresses();
        int n = aeRawRight.getNumEvents();
        for ( int i = 0 ; i < n ; i++ ){
            adr[i] = (int)( adr[i] | Stereopsis.MASK_RIGHT_ADDR );
        }
    }

    /** Labels events as coming from left eye
     * 
     * @param aeRaw
     */
    public void labelLeftEye (AEPacketRaw aeRaw){
        int[] adr = aeRaw.getAddresses();
        int n = aeRaw.getNumEvents();
        for ( int i = 0 ; i < n ; i++ ){
            adr[i] = (int)( adr[i] & ~Stereopsis.MASK_RIGHT_ADDR );
        }

    }

    public boolean isIgnoreTimestampNonmonotonicity (){
        return ignoreTimestampNonmonotonicity;
    }

    /** If this flag is set true, then packets are returned from
     * acquireAvailableEventsFromDriver as soon as they
     * are delivered, regardless of any timestamp ordering problems.
     * No attempt is made to ensure that the timestamps are
     * ordered correctly.
     * <p>
     * Setting this flag true will save substantial computation and decrease the average
     * latency, but
     * Playback of logged data will likely not work well since there is an
     * integral (and natural) assumption that time increases monotonically
     * in much of jAER event processing.
     * 
     * @param yes true to not order timestamps from the two interfaces.
     */
    public void setIgnoreTimestampNonmonotonicity (boolean yes){
        ignoreTimestampNonmonotonicity = yes;
        log.info("ignoreTimestampNonmonotonicity=" + ignoreTimestampNonmonotonicity);
    }
}
