/*
 * StereoPairHardwareInterface.java
 *
 * Created on March 14, 2006, 7:12 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright March 30, 2011 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */
package net.sf.jaer.stereopsis;

import ch.unizh.ini.jaer.chip.multicamera.MultiDVS128CameraChip;
import ch.unizh.ini.jaer.chip.multicamera.MultiDavisCameraChip;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.logging.Logger;

import net.sf.jaer.aemonitor.AEListener;
import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.aemonitor.EventRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.MultiCameraApsDvsEvent;
import net.sf.jaer.event.MultiCameraEvent;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.HardwareInterfaceFactory;
import net.sf.jaer.hardwareinterface.usb.ReaderBufferControl;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.CypressFX3;

/**
 * A hardware interface to multiple merged sensors.
This class merges the data from several AEMonitorInterface's to a single
 * unified stereo stream, with events sorted by timestamp in 
the output packets.
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
public class MultiCameraHardwareInterface implements AEMonitorInterface, ReaderBufferControl, PropertyChangeListener {

    final static Logger log = Logger.getLogger("net.sf.jaer");  // define Logger first in case exception thrown in constructor that needs to log the error
    private PropertyChangeSupport support = new PropertyChangeSupport(this);
    protected AEChip chip;
    /** The multiple hardware interfaces. */ // TODO currently depends on global static MultiCameraEvent.NUM_CAMERAS
    private static boolean firstTimeReadHWInterface=true;
    public static int NUM_CAMERAS=MultiCameraHardwareInterface.getNumberOfCameraChip();
    private AEMonitorInterface[] aemons = new AEMonitorInterface[NUM_CAMERAS];
    private boolean ignoreTimestampNonmonotonicity = false;
    private int RESET_DELAY_MS = 200; 
    /** These FIFOs hold packets which are references to the packets from each source */
    /** Initial capacity of output buffer that is reused for outputting merged event stream */
    private AEFifo[] aeFifos = new AEFifo[NUM_CAMERAS];    // this packet is re-used for outputting the merged events
    public final int INITIAL_CAPACITY = CypressFX3.AE_BUFFER_SIZE;
    private AEPacketRaw aeOut = new AEPacketRaw(INITIAL_CAPACITY * NUM_CAMERAS);//    AEPacketRaw bufLeft=new AEPacketRaw(BUFFER_CAPACITY); // holds events that arrive after the last event from the other packet.
    boolean openMultipleView=true;
    
    public void setChip(AEChip chip) {
        this.chip = chip;
        for (AEMonitorInterface aemon : aemons) {
            if (aemon != null) {
                aemon.setChip(chip);
            } else {
                log.warning("null right AEMonitorInterface, couldn't set chip for it");
            }
        }
    }

    public AEChip getChip() {
        return chip;
    }

    public MultiCameraHardwareInterface(AEMonitorInterface[] aemons) {
        for (int i = 0; i < this.aemons.length; i++) {
            this.aemons[i] = aemons[i];
            aeFifos[i] = new AEFifo(null);
        }
    }

    /**
     * @return the support
     */
    public PropertyChangeSupport getSupport() {
        return support;
    }

    /** Fires off the multi camera property change events. The event "readerStarted" is fired when the AEReader is started.
     *
     * @param evt from one of the pair of interfaces.
     */
    public void propertyChange(PropertyChangeEvent evt) {
        getSupport().firePropertyChange(evt);
    }

    public PropertyChangeSupport getReaderSupport() {
        return support;
    }

    /** A FIFO for raw events. Encapsulates an AEPacketRaw so
     * that it is a kind of FIFO (at least FO).
     * We can popNextEvent events from
     * the AEFifo in their order and check if there are any more available.
     */
    private final class AEFifo {

        long timeAcquired = 0;
        AEPacketRaw ae;
        int next = 0;

        /** Constructs a new AE FIFO from an AEPacketRaw
        @param ae the raw packet to use as source of events
         */
        AEFifo(AEPacketRaw ae) {
            this.ae = ae;
            next = 0;
            timeAcquired = System.currentTimeMillis();
        }

        /** @return next event, or null if there are no more */
        final EventRaw popNextEvent() {
            if (next < ae.getNumEvents()) {
                return ae.getEvent(next++);
            } else {
                return null;
            }
        }

        final boolean isEmpty() {
            if (ae == null || next >= ae.getNumEvents()) {
                return true;
            } else {
                return false;
            }
        }

        /** resets FIFO so that next event is first event
        @param ae the raw packet that will be the new source of events
         */
        final void reset(AEPacketRaw ae) {
            this.ae = ae;
            next = 0;
            timeAcquired = System.currentTimeMillis();
        }

        final int peekNextTimestamp() {
            return ae.getEvent(next).timestamp;
        }

        final long getTimeAcquired() {
            return timeAcquired;
        }

        @Override
        final public String toString() {
            return "AEFifo with timeAcquired=" + timeAcquired + " next=" + next + " holding " + ae.toString();
        }

        final public int size() {
            if (ae == null) {
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
    private void initAdd() {
        aeOut.clear();
        int cap = 0;
        for (AEFifo f : aeFifos) {
            cap += f.size();
        }
        aeOut.ensureCapacity(cap); // preallocate arrays to ensure we can write to the timestamps/addressses arrays
        timestamps = aeOut.getTimestamps();
        addresses = aeOut.getAddresses();
        this.count = 0;
    }

    private void finishAdd() {
        aeOut.setNumEvents(count);
    }

    private void addEvent(AEFifo f) {
        EventRaw event = f.popNextEvent();     
        timestamps[count] = event.timestamp;        
        addresses[count++] = event.address;
    }

    /** @return an AEPacketRaw of events from both devices, sorted by timestamp.
     * As a hack (hopefully temporary), the MSB is set on the raw addresses of the events from the right device. 
    @return packet of monotonic timestamp-ordered events from both interfaces
     */
    synchronized public AEPacketRaw acquireAvailableEventsFromDriver() throws HardwareInterfaceException {
        for (AEMonitorInterface aemon : aemons) {
            if (!aemon.isEventAcquisitionEnabled()) {
                aemon.setEventAcquisitionEnabled(true);
            }
        }

        int[] numEvents = new int[aemons.length];

        if (requestTimestampReset) {
            log.info("resetting timestamps on all interfaces");
            // TODO tricky here because it is not clear how to know that all data from each interface has been flushed after the
            // timestamp reset. If we don't flush enough data, we get old timestamps on one interface.

            // these next calls usually call VendorRequests to send USB control packets to the interfaces.
            // These calls are blocking.
            for (AEMonitorInterface aemon : aemons) {
                aemon.resetTimestamps();
            }
            try {
                log.info("Sleeping " + RESET_DELAY_MS + " ms after resetTimestamps() on each interface");
                Thread.sleep(RESET_DELAY_MS); // sleep to let vendor requests get out there
            } catch (InterruptedException e) {
            }

            for (AEMonitorInterface aemon : aemons) {
                aemon.acquireAvailableEventsFromDriver();
            }

            for (AEFifo f : aeFifos) {
                f.reset(null); // isEmpty will return true after this reset
            }
            try {
                Thread.sleep(RESET_DELAY_MS);
            } catch (InterruptedException e) {
            }
        } // requestTimestampReset

        // we only popNextEvent data from a camera if we have used up all the data from it that we got before.
        // we can only output an event (to the user) if we know it is the next event, meaning it has to be
        // followed by an event from the other cameras.

        initAdd();
        // popNextEvent available events from all sources

        for (int i = 0; i < aemons.length; i++) {
            if (aeFifos[i].isEmpty()) {
                aeFifos[i].reset(aemons[i].acquireAvailableEventsFromDriver());
                labelCamera(aeFifos[i].ae, i);
                if (requestTimestampReset) {
                    log.info("after timestampsReset camera " + i + " acquired " + aeFifos[i].ae + " t0,t1=" + aeFifos[i].ae.getFirstTimestamp() + ", " + aeFifos[i].ae.getLastTimestamp());
                }
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

        if (ignoreTimestampNonmonotonicity) {
            // here just igmore temporal ordering, pass out events from both interfaces, in order of cameras
            for (AEFifo f : aeFifos) {
                while (!f.isEmpty()) {
                    addEvent(f);
                }
            }

        } else {
            // here we order the events and only pass out an event from an interface if there is a later event from the other interface
            // each time we pop event we also check if fifo is empty. If so, we break from this loop since there is the possibility that an earlier event
            // could come from this stream than from another one.
            boolean eventsAvailable = isEventsFromAllAvailable();
            while (eventsAvailable) {
                // while both packets still have events, pick the earliest event and write it out
                int tsMin = Integer.MAX_VALUE;
                int ind = 0;
                for (int i = 0; i < aeFifos.length; i++) {
                    AEFifo f = aeFifos[i];
                    int t = f.peekNextTimestamp();
                    if (t < tsMin) {
                        tsMin = t;
                        ind = i;                        
                    }
                }
                if (count==timestamps.length) {
                    break;
                }
                addEvent(aeFifos[ind]);
                if (aeFifos[ind].isEmpty()) {
                    eventsAvailable = false;
                    break;
                }
            }
        }
        finishAdd();
        return aeOut;
    }

    private boolean isEventsFromAllAvailable() {
        boolean allHaveEvent = true;
        for (AEFifo f : aeFifos) {
            if (f.isEmpty()) {
                allHaveEvent = false;
                break;
            }
        }
        return allHaveEvent;
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
        for (AEMonitorInterface a : aemons) {
            if (a.overrunOccurred()) {
                return false;
            }
        }
        return false;
    }

    /** @return the host AE buffer size. This is for the user buffer, not the device buffer.
    This returns the buffer size for the left device.
     */
    public int getAEBufferSize() {
        return aemons[0].getAEBufferSize();
    }

    /** sets the host AE buffer size. This is for the user buffer, not the device buffer.
    @param bufsize the size in events
     */
    public void setAEBufferSize(int bufsize) {
        for (AEMonitorInterface aemon : aemons) {
            aemon.setAEBufferSize(bufsize);
        }
    }

    boolean hasBufferControl() {
        for (AEMonitorInterface aemon : aemons) {
            if (aemon == null || !(aemon instanceof ReaderBufferControl)) {
                return false;
            }
        }
        return true;
    }

    public int getFifoSize() {
        if (!hasBufferControl()) {
            return 0;
        }
        return ((ReaderBufferControl) aemons[0]).getFifoSize();
    }

    public void setFifoSize(int fifoSize) {
        if (!hasBufferControl()) {
            return;
        }
        for (AEMonitorInterface aemon : aemons) {
            if (aemon == null) {
                continue;
            }
            if (!(aemon instanceof ReaderBufferControl)) {
                continue;
            }
            ReaderBufferControl rbc = (ReaderBufferControl) aemon;
            rbc.setFifoSize(fifoSize);
        }
        log.info("set multi camera AE buffer size to " + fifoSize);
    }

    public int getNumBuffers() {
        if (!hasBufferControl()) {
            return 0;
        }
        return ((ReaderBufferControl) aemons[0]).getNumBuffers();
    }

    public void setNumBuffers(int numBuffers) {
        if (!hasBufferControl()) {
            return;
        }
        for (AEMonitorInterface aemon : aemons) {
            if (aemon == null) {
                continue;
            }
            if (!(aemon instanceof ReaderBufferControl)) {
                continue;
            }
            ReaderBufferControl rbc = (ReaderBufferControl) aemon;
            rbc.setNumBuffers(numBuffers);
        }
    }

    /** sets both eyes to acquire events */
    public void setEventAcquisitionEnabled(boolean enable) throws HardwareInterfaceException {
//        log.info("Stereo setEventAcquisitionEnabled="+enable);
        for (AEMonitorInterface aemon : aemons) {
            if (aemon == null) {
                continue;
            }
            aemon.setEventAcquisitionEnabled(enable);
        }

    }

    /** @return true if both eyes are enabled */
    public boolean isEventAcquisitionEnabled() {
        for (AEMonitorInterface aemon : aemons) {
            if (aemon == null) {
                return false;
            }
            if (aemon.isEventAcquisitionEnabled() == false) {
                return false;
            }
        }
        return true;
    }

    public void addAEListener(AEListener listener) {
    }

    public void removeAEListener(AEListener listener) {
    }

    public int getMaxCapacity() {
        return aemons[0].getMaxCapacity();
    }

    public int getEstimatedEventRate() {
        int sum = 0;
        for (AEMonitorInterface aemon : aemons) {
            if (aemon == null) {
                return 0;
            }
            sum += aemon.getEstimatedEventRate();
        }
        sum /= aemons.length;
        return sum;
    }

    public int getTimestampTickUs() {
        return aemons[0].getTimestampTickUs();
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
        for (AEMonitorInterface aemon : aemons) {
            if (aemon != null) {
                aemon.close();
            }
        }
    }

    /** Opens both interfaces. Normally not called because
     * these interfaces are already constructed and opened in the
     * Tmpdiff128StereoPair getHardwareInterface method.
     */
    public void open() throws HardwareInterfaceException {
        for(AEMonitorInterface aemon:aemons){
            addSupport(aemon);
            aemon.open();
        }
    }

    private void addSupport(AEMonitorInterface aemon) {
        try {
            CypressFX2 fx2 = (CypressFX2) aemon;
            PropertyChangeSupport fx2Support = fx2.getSupport();
            // propertyChange method in this file deals with these events
            if (!fx2Support.hasListeners("readerStarted")) {
                fx2Support.addPropertyChangeListener("readerStarted", this); // when the reader starts running, we get called back to fix device control menu
            }
        } catch (Exception e) {
            log.warning("couldn't add readerChanged property change to " + aemon);
        }
    }

    /** Returns true if all interfaces are open.
     *
     * @return true if all are open.
     */
    public boolean isOpen() {
      for(AEMonitorInterface aemon:aemons){
          if(aemon==null || !aemon.isOpen()) return false;
      }
      return true;
    }

    /** Labels all events in raw packet as coming from particular camera.
     *
     * @param aeRawRight
     */
    private void labelCamera(AEPacketRaw aeRaw, int camera) {
        int[] adr = aeRaw.getAddresses();
        int n = aeRaw.getNumEvents();
        if (chip instanceof MultiDVS128CameraChip) {
            MultiCameraEvent mce= new MultiCameraEvent();
            for (int i = 0; i < n; i++) {
                adr[i]=mce.setCameraNumberToRawAddress(camera, adr[i]);
            }
        }else if (chip instanceof MultiDavisCameraChip) {
            MultiCameraApsDvsEvent mce= new MultiCameraApsDvsEvent();                           
            for (int i = 0; i < n; i++) {
                if (mce.isDVSEvent()){
//                    System.out.println("camera: "+camera+" old address: "+Integer.toBinaryString(adr[i]));
                    adr[i]=mce.setCameraNumberToRawAddressDVS(camera, adr[i]);
//                    System.out.println("new address: "+Integer.toBinaryString(adr[i]));
                }
                if (!mce.isApsData()){
//                    System.out.println("camera: "+camera+" old address: "+Integer.toBinaryString(adr[i]));
                    adr[i]=mce.setCameraNumberToRawAddressAPS(camera, adr[i]);
//                    System.out.println("new address: "+Integer.toBinaryString(adr[i]));
                }
            }
        }
    }

    public boolean isIgnoreTimestampNonmonotonicity() {
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
    public void setIgnoreTimestampNonmonotonicity(boolean yes) {
        ignoreTimestampNonmonotonicity = yes;
        log.info("ignoreTimestampNonmonotonicity=" + ignoreTimestampNonmonotonicity);

    }
    
    /**Return the number of HardwareInterface.
     * @return the number of hardware interface (number of cameras)
     */
    public static final int getNumberOfCameraChip() {
        int n;
        if (firstTimeReadHWInterface==true){
            NUM_CAMERAS=HardwareInterfaceFactory.instance().getNumInterfacesAvailable();
            n=NUM_CAMERAS;
            firstTimeReadHWInterface=false;
            log.info("Number of cameras: "+ NUM_CAMERAS);
            return n;
        }
        else{
            log.warning("Number of cameras found: 0");
            return 2;
            
        }        
    }
    
    /**Set the number of cameras
     */
    public void setNumberOfCameras(int numberCameras) {
        NUM_CAMERAS = numberCameras;       
    }
}