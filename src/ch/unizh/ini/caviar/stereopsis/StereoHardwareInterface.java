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
import ch.unizh.ini.caviar.event.BinocularEvent;
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
    private AEMonitorInterface aemonLeft;
    private AEMonitorInterface aemonRight;
    Logger log = Logger.getLogger("HardwareInterface");
    private boolean ignoreTimestampNonmonotonicity = false;

    public void setChip(AEChip chip) {
        this.chip = chip;
        if (getAemonLeft() != null) {
            getAemonLeft().setChip(chip);
        } else {
            log.warning("null left AEMonitorInterface, couldn't set chip for it");
        }
        if (getAemonRight() != null) {
            getAemonRight().setChip(chip);
        } else {
            log.warning("null right AEMonitorInterface, couldn't set chip for it");
        }
    }

    public AEChip getChip() {
        return chip;
    }
    /** Initial capacity of output buffer that is reused for outputting merged event stream */
    public final int INITIAL_CAPACITY = CypressFX2.AE_BUFFER_SIZE;

    public StereoHardwareInterface(AEMonitorInterface left, AEMonitorInterface right) {
        aemonLeft = left;
        aemonRight = right;
        aeLeft = new AEFifo(null);
        aeRight = new AEFifo(null);
    }    // these packets are references to the packets from each source
    AEFifo aeLeft, aeRight;    // this packet is re-used for outputting the merged events
    AEPacketRaw aeOut = new AEPacketRaw(INITIAL_CAPACITY * 2);//    AEPacketRaw bufLeft=new AEPacketRaw(BUFFER_CAPACITY); // holds events that arrive after the last event from the other packet.
//    AEPacketRaw bufRight=new AEPacketRaw(BUFFER_CAPACITY); // holds events that arrive after the last event from the other packet.
    // these are last timestamps from each source.
    int lastLeftTimestamp = 0, lastRightTimestamp = 0;

    public AEMonitorInterface getAemonLeft() {
        return aemonLeft;
    }

    public void setAemonLeft(AEMonitorInterface aemonLeft) {
        this.aemonLeft = aemonLeft;
    }

    public AEMonitorInterface getAemonRight() {
        return aemonRight;
    }

    public void setAemonRight(AEMonitorInterface aemonRight) {
        this.aemonRight = aemonRight;
    }
    // this class encapsulates an AEPacketRaw so that it is a kind of FIFO (at least FO). we can popNextEvent events from
    // the AEFifo in their order and check if there are any more available.
    /** A FIFO for raw events */
    class AEFifo {

        long timeAcquired = 0;

        /** Constructs a new AE FIFO from an AEPacketRaw
        @param ae the raw packet to use as source of events
         */
        AEFifo(AEPacketRaw ae) {
            this.ae = ae;
            next = 0;
            timeAcquired = System.currentTimeMillis();
        }
        AEPacketRaw ae;
        int next = 0;

        /** @return next event, or null if there are no more */
        EventRaw popNextEvent() {
            if (next < ae.getNumEvents()) {
                return ae.getEvent(next++);
            } else {
                return null;
            }
        }

        boolean isEmpty() {
            if (ae == null || next >= ae.getNumEvents()) {
                return true;
            } else {
                return false;
            }
        }

        /** resets FIFO so that next event is first event
        @param ae the raw packet that will be the new source of events
         */
        void reset(AEPacketRaw ae) {
            this.ae = ae;
            next = 0;
            timeAcquired = System.currentTimeMillis();
        }

        int peekNextTimestamp() {
            return ae.getEvent(next).timestamp;
        }

        long getTimeAcquired() {
            return timeAcquired;
        }

        public String toString() {
            return "AEFifo with " + ae.getNumEvents();
        }
    }
    /** the two inputs have their timestamps reset when the first timestamp of each most recent packet differs by this much */
    public static final int RESET_TIMESTAMPS_THRESHOLD_DT_US = 100000;

    /** @return an AEPacketRaw of events from both devices, sorted by timestamp.
     * As a hack (hopefully temporary), the MSB is set on the raw addresses of the events from the right device. 
    @return packet of monotonic timestamp-ordered events from both interfaces
     */
    synchronized public AEPacketRaw acquireAvailableEventsFromDriver() throws HardwareInterfaceException {
        if (!aemonLeft.isEventAcquisitionEnabled()) {
            getAemonLeft().setEventAcquisitionEnabled(true);
        }
        if (!aemonRight.isEventAcquisitionEnabled()) {
            getAemonRight().setEventAcquisitionEnabled(true);
        }
        int nleft = 0, nright = 0;

        if (requestTimestampReset) {

            getAemonLeft().resetTimestamps(); // call for hardware reset of timestamps
            getAemonRight().resetTimestamps();  // on other interface too
            aeRight.reset(null); // isEmpty will return true
            aeLeft.reset(null);
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
        }
        // we only popNextEvent data from an eye if we have used up all the data from it that we got before.
        // we can only output an event (to the user) if we know it is the next event, meaning it has to be
        // followed by an event from the other retina.

        // popNextEvent available events from both sources
        if (aeLeft.isEmpty()) {
            aeLeft.reset(getAemonLeft().acquireAvailableEventsFromDriver());
            if (requestTimestampReset) {
                log.info("after timestampsReset LEFT got " + aeLeft.ae + " t0,t1=" + aeLeft.ae.getFirstTimestamp() + ", " + aeLeft.ae.getLastTimestamp());
            }
        }
        if (aeRight.isEmpty()) {
            aeRight.reset(getAemonRight().acquireAvailableEventsFromDriver());
            labelRightEye(aeRight.ae);
            if (requestTimestampReset) {
                log.info("after timestampsReset RIGHT got " + aeRight.ae + " t0,t1=" + aeRight.ae.getFirstTimestamp() + ", " + aeRight.ae.getLastTimestamp());
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

        aeOut.setNumEvents(0);
        if (!ignoreTimestampNonmonotonicity) {
            // here we order the events and only pass out an event from an interface if there is a later event from the other interface
            while (!aeLeft.isEmpty() && !aeRight.isEmpty()) {
                // while both packets still have events, pick the earliest event and write it out
                int tsLeft = aeLeft.peekNextTimestamp(), tsRight = aeRight.peekNextTimestamp();
                if (tsLeft < tsRight) {
                    aeOut.addEvent(aeLeft.popNextEvent());
                    nleft++;
                } else {
                    aeOut.addEvent(aeRight.popNextEvent());
                    nright++;
                }
            }
        } else {
            // here just igmore temporal ordering, pass out events from both interfaces
            while(!aeRight.isEmpty()){
                aeOut.addEvent(aeRight.popNextEvent());
            }
            while(!aeLeft.isEmpty()){
                aeOut.addEvent(aeLeft.popNextEvent());
            }
        }
        return aeOut;
    }

    public int getNumEventsAcquired() {
        return aeOut.getNumEvents();
    }

    public AEPacketRaw getEvents() {
        return aeOut;
    }    // set to request timestamp reset on next acquireAvailableEventsFromDriver
    private volatile boolean requestTimestampReset = false;

    /** resets timestamps from both inputs, serially. The hardware timestamps may be offset by an undetermined amount
     *due to USB latency, context switching, etc. By using electrical synchronization between boards, it may be possible to
     *synchronize perfectly, but this is device-dependent.
     */
    synchronized public void resetTimestamps() {
        // clear out pending events
        requestTimestampReset = true; // this tells rendering thread (which calls acquireAvailableEventsFromDriver) that timestamps have been reset
    }

    /** @return true if either device overran the buffer */
    public boolean overrunOccurred() {
        return getAemonLeft().overrunOccurred() || getAemonRight().overrunOccurred();
    }

    /** @return the host AE buffer size. This is for the user buffer, not the device buffer.
    This returns the buffer size for the left device.
     */
    public int getAEBufferSize() {
        return getAemonLeft().getAEBufferSize();
    }

    /** sets the host AE buffer size. This is for the user buffer, not the device buffer.
    @param AEBufferSize the size in events
     */
    public void setAEBufferSize(int AEBufferSize) {
        getAemonLeft().setAEBufferSize(AEBufferSize);
        getAemonRight().setAEBufferSize(AEBufferSize);
    }

    boolean hasBufferControl() {
        if (getAemonLeft() == null || getAemonRight() == null) {
            return false;
        }
        boolean yes = (getAemonLeft() instanceof ReaderBufferControl) && (getAemonRight() instanceof ReaderBufferControl);
        if (!yes) {
            log.warning("device doesn't support ReaderBufferControl functionality");
        }
        return yes;
    }

    public int getFifoSize() {
        if (!hasBufferControl()) {
            return 0;
        }
        return ((ReaderBufferControl) getAemonLeft()).getFifoSize();
    }

    public void setFifoSize(int fifoSize) {
        if (!hasBufferControl()) {
            return;
        }
        ((ReaderBufferControl) getAemonLeft()).setFifoSize(fifoSize);
        if (getAemonRight() == null) {
            return;
        }
        ((ReaderBufferControl) getAemonRight()).setFifoSize(fifoSize);
        log.info("set stereo ae buffer size to " + fifoSize);
    }

    public int getNumBuffers() {
        if (!hasBufferControl()) {
            return 0;
        }
        return ((ReaderBufferControl) getAemonLeft()).getNumBuffers();
    }

    public void setNumBuffers(int numBuffers) {
        if (!hasBufferControl()) {
            return;
        }
        ((ReaderBufferControl) getAemonLeft()).setNumBuffers(numBuffers);
        ((ReaderBufferControl) getAemonRight()).setNumBuffers(numBuffers);
    }

    /** sets both eyes to acquire events */
    public void setEventAcquisitionEnabled(boolean enable) throws HardwareInterfaceException {
//        log.info("Stereo setEventAcquisitionEnabled="+enable);
        getAemonLeft().setEventAcquisitionEnabled(enable);
        getAemonRight().setEventAcquisitionEnabled(enable);
    }

    /** @return true if both eyes are enabled */
    public boolean isEventAcquisitionEnabled() {
        return getAemonLeft().isEventAcquisitionEnabled() && getAemonRight().isEventAcquisitionEnabled();
    }

    public void addAEListener(AEListener listener) {
    }

    public void removeAEListener(AEListener listener) {
    }

    public int getMaxCapacity() {
        return getAemonLeft().getMaxCapacity();
    }

    public int getEstimatedEventRate() {
        return (int) (getAemonLeft().getEstimatedEventRate() + getAemonRight().getEstimatedEventRate());
    }

    public int getTimestampTickUs() {
        return getAemonLeft().getTimestampTickUs();
    }

    public String[] getStringDescriptors() {
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
    }

    public String getTypeName() {
        return "Stereo";
    }

    public void close() {
        if (getAemonLeft() != null) {
            getAemonLeft().close();
        }
        if (getAemonRight() != null) {
            getAemonRight().close();
        }
    }

    public void open() throws HardwareInterfaceException {
        getAemonLeft().open();
        getAemonRight().open();
    }

    public boolean isOpen() {
        if (getAemonLeft() == null || getAemonRight() == null) {
            return false;
        }
        return getAemonLeft().isOpen() && getAemonRight().isOpen();
    }

    /** Labels all events in raw packet as coming from right eye
     * 
     * @param aeRawRight
     */
    public void labelRightEye(AEPacketRaw aeRawRight) {
        int[] adr = aeRawRight.getAddresses();
        int n = aeRawRight.getNumEvents();
        for (int i = 0; i < n; i++) {
            adr[i] = (int) (adr[i] | Stereopsis.MASK_RIGHT_ADDR);
        }
    }

    /** Labels events as coming from left eye
     * 
     * @param aeRaw
     */
    public void labelLeftEye(AEPacketRaw aeRaw) {
        int[] adr = aeRaw.getAddresses();
        int n = aeRaw.getNumEvents();
        for (int i = 0; i < n; i++) {
            adr[i] = (int) (adr[i] & ~Stereopsis.MASK_RIGHT_ADDR);
        }

    }

    public boolean isIgnoreTimestampNonmonotonicity() {
        return ignoreTimestampNonmonotonicity;
    }

    /** If this flag is set true, then packets are returned from acquireAvailableEventsFromDriver as soon as they 
     * are delivered, regardless of any timestamp ordering problems. No attempt is made to ensure that the timestamps are
     * ordered correctly. Playback of logged data will likely not work well since there is an assumption that time increases monotonically
     * in much of jAER event processing.
     * 
     * @param yes true to not order timestamps from the two interfaces.
     */
    public void setIgnoreTimestampNonmonotonicity(boolean yes) {
        ignoreTimestampNonmonotonicity = yes;
    }
}
