/*
 * Martin Ebner, igi, tu graz
 * linux driver for dvs
 * using linux kernel driver 'retina'
 * 
 * INSTALLATION:
 * 
 * see jaer/trunk/drivers/linux/driverRetinaLinux/INSTALL
 * tested on opensuse, kubuntu and fedora core
 * 
 * hardware interface based upon cypressfx2 class
 * 
 */

package net.sf.jaer.hardwareinterface.usb.linux;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeSupport;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import net.sf.jaer.aemonitor.AEListener;
import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.biasgen.IPot;
import net.sf.jaer.biasgen.IPotArray;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
//import javax.usb.UsbConst;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.HasResettablePixelArray;

/**
 * The Tmpdiff128 retina under linux using the JSR-80 
 * linux java USB library 
 * (<a href="http://sourceforge.net/projects/javax-usb">JSR-80 project</a>).
 * 
 * @author Martin Ebner (martin_ebner)
 */
public class CypressFX2RetinaLinux implements AEMonitorInterface, BiasgenHardwareInterface, HasResettablePixelArray {

    protected AEChip chip;
    protected boolean inEndpointEnabled = false;
    static Logger log = Logger.getLogger("CypressFX2RetinaLinux");
    protected Preferences prefs = Preferences.userNodeForPackage(this.getClass());
    /** max number of bytes used for each bias. For 24-bit biasgen, only 3 bytes are used, but we oversize considerably for the future. */
    public static final int MAX_BYTES_PER_BIAS = 8;
    // vendor requests
    final static byte VENDOR_REQUEST_START_TRANSFER = (byte) 0xb3; // this is request to start sending events from FIFO endpoint

    final static byte VENDOR_REQUEST_STOP_TRANSFER = (byte) 0xb4; // this is request to stop sending events from FIFO endpoint

    final static byte VENDOR_REQUEST_EARLY_TRANFER = (byte) 0xb7; // this is request to transfer whatever you have now

    static final byte VENDOR_REQUEST_SEND_BIAS_BYTES = (byte) 0xb8; // vendor command to send bias bytes out on SPI interface

    final byte VENDOR_REQUEST_POWERDOWN = (byte) 0xb9; // vendor command to send bias bytes out on SPI interface

    final byte VENDOR_REQUEST_FLASH_BIASES = (byte) 0xba;  // vendor command to flash the bias values to EEPROM

    final byte VENDOR_REQUEST_RESET_TIMESTAMPS = (byte) 0xbb; // vendor command to reset timestamps

    final byte VENDOR_REQUEST_SET_ARRAY_RESET = (byte) 0xbc; // vendor command to set array reset of retina

    final byte VENDOR_REQUEST_DO_ARRAY_RESET = (byte) 0xbd; // vendor command to do an array reset (toggle arrayReset for a fixed time)
    //final byte VENDOR_REQUEST_WRITE_EEPROM=(byte)0xbe; // vendor command to write EEPROM

    final static byte VENDOR_REQUEST_SET_LED = (byte) 0xbf; // vendor command to set the board's LED
    //final byte VENDOR_REQUEST_READ_EEPROM=(byte)0xca; // vendor command to write EEPROM
    // #define VR_EEPROM		0xa2 // loads (uploads) EEPROM

    final byte VR_EEPROM = (byte) 0xa2;
    // #define	VR_RAM			0xa3 // loads (uploads) external ram
    final byte VR_RAM = (byte) 0xa3;

    // this is special hw vendor request for reading and writing RAM, used for firmware download
    static final byte VENDOR_REQUEST_FIRMWARE = (byte) 0xA0; // download/upload firmware -- built in to FX2

//    public static final byte VENDOR_DEVICE_OUT_REQUEST =
//            UsbConst.REQUESTTYPE_DIRECTION_OUT | UsbConst.REQUESTTYPE_TYPE_VENDOR | UsbConst.REQUESTTYPE_RECIPIENT_DEVICE;

    protected String devicName;
    protected FileInputStream retina;
    protected FileOutputStream retinaVendor;
    
    /** This instance is typically constructint interfaceNumbered by the factory instance (HardwareInterfaceFactoryLinux) */
    public CypressFX2RetinaLinux(String deviceName) throws FileNotFoundException {
        retina = new FileInputStream(deviceName);
        retinaVendor = new FileOutputStream(deviceName);
    }

    public AEPacketRaw acquireAvailableEventsFromDriver() throws HardwareInterfaceException {
        if (!isOpened) {
            open();
        }
        // make sure event acquisition is running
        if (!inEndpointEnabled) {
            setEventAcquisitionEnabled(true);
        }
        int nEvents;
        aePacketRawPool.swap();
        lastEventsAcquired = aePacketRawPool.readBuffer();
        nEvents = lastEventsAcquired.getNumEvents();
        eventCounter = 0;
        computeEstimatedEventRate(lastEventsAcquired);
        if (nEvents != 0) {
            support.firePropertyChange(NEW_EVENTS_PROPERTY_CHANGE); // call listeners
//            log.info("numevents="+nEvents);

        }
        return lastEventsAcquired;
    }
    private int estimatedEventRate = 0;

    /** computes the estimated event rate for a packet of events */
    void computeEstimatedEventRate(AEPacketRaw events) {
        if (events == null || events.getNumEvents() < 2) {
            estimatedEventRate = 0;
        } else {
            int[] ts = events.getTimestamps();
            int n = events.getNumEvents();
            int dt = ts[n - 1] - ts[0];
            estimatedEventRate = (int) (1e6f * (float) n / (float) dt);
        }
    }

    public int getNumEventsAcquired() {
        return aePacketRawPool.readBuffer().getNumEvents();
    }

    public AEPacketRaw getEvents() {
        return this.lastEventsAcquired;
    }

    /** Sends a vendor request to reset the retina timestamps to zero */
    public void resetTimestamps() {
        try {
            vendorRequest(VENDOR_REQUEST_RESET_TIMESTAMPS, (short) 0, (short) 0, new byte[1]);        
            getAeReader().resetTimestamps();            
        } catch (Exception e) {
            log.warning(e.toString());
        }
    }

    /** momentarily reset the entire pixel array*/
    public void resetPixelArray() {
        vendorRequest(VENDOR_REQUEST_DO_ARRAY_RESET, (short) 0, (short) 0, new byte[1]);
    }

    public boolean overrunOccurred() {
        return aePacketRawPool.readBuffer().overrunOccuredFlag;
    }

    public int getAEBufferSize() {
        return aeBufferSize;
    }

    void allocateAEBuffers() {
        synchronized (aePacketRawPool) {
            aePacketRawPool.allocateMemory();
        }
    }

    public void setAEBufferSize(int size) {
        if (size < 1000 || size > 1000000) {
            log.warning("ignoring unreasonable aeBufferSize of " + size + ", choose a more reasonable size between 1000 and 1000000");
            return;
        }
        this.aeBufferSize = size;
        prefs.putInt("CypressFX2.aeBufferSize", aeBufferSize);
        allocateAEBuffers();
    }

    public void setEventAcquisitionEnabled(boolean enable) throws HardwareInterfaceException {
        if (enable) {
            startAer();
            startAEReader();

        } else {
            stopAer();
            stopAEReader();
        }
    }

    public boolean isEventAcquisitionEnabled() {
        return inEndpointEnabled;
    }

    public void addAEListener(AEListener listener) {
        support.addPropertyChangeListener(listener);
    }

    public void removeAEListener(AEListener listener) {
        support.removePropertyChangeListener(listener);
    }

    /** the max capacity of this USB2 bus interface is 24MB/sec/4 bytes/event
     */
    public int getMaxCapacity() {
        return 6000000;
    }

    /** @return event rate in events/sec as computed from last acquisition.
     *
     */
    public int getEstimatedEventRate() {
        return estimatedEventRate;
    }

    /** @return timestamp tick in us
     * NOTE: DOES NOT RETURN THE TICK OF THE USBAERmini2 board*/
    final public int getTimestampTickUs() {
        return TICK_US;
    }

    public void setChip(AEChip chip) {
        this.chip = chip;
    }

    public AEChip getChip() {
        return chip;
    }

    public String[] getStringDescriptors() {
        String[] s = new String[2];
//        try {
//            s[0] = usbDevice.getManufacturerString();
//            s[1] = usbDevice.getProductString();
//        } catch (Exception uE) {
//            log.warning("getStringDescriptors(): " + uE.getMessage());
//        }
        return s;
    }

    public int[] getVIDPID() {
        int[] nVIDPID = new int[2];
//        if (isOpened) {
//            nVIDPID[0] = usbDevice.getUsbDeviceDescriptor().idVendor();
//            nVIDPID[1] = usbDevice.getUsbDeviceDescriptor().idProduct();
//        } else {
//            log.warning("USBAEMonitor: getVIDPID called but device has not been opened");
//        }
        return nVIDPID;
    }

    public short getVID() {
//        if (isOpened) {
//            return usbDevice.getUsbDeviceDescriptor().idVendor();
//        } else {
//            log.warning("USBAEMonitor: getVID called but device has not been opened");
            return 0;
//        }
    }

    public short getPID() {
//        if (isOpened) {
//            return usbDevice.getUsbDeviceDescriptor().idProduct();
//        } else {
//            log.warning("USBAEMonitor: getPID called but device has not been opened");
            return 0;
//        }
    }

    public short getDID() {
//        if (isOpened) {
//            return usbDevice.getUsbDeviceDescriptor().bcdDevice();
//        } else {
//            log.warning("USBAEMonitor: getDID called but device has not been opened");
            return 0;
//        }
    }

    public String getTypeName() {
        return "CypressFX2RetinaLinux";
    }

    public void close() {
        if (!isOpened) {
            return;
        }
//        //release interface 0 of device
//        if (usbInterface.isClaimed() == true) {
//            releaseInterface();
//        }
        try {
            if (this.isEventAcquisitionEnabled()) {
                setEventAcquisitionEnabled(false);
                stopAEReader();
            }
//            if (asyncStatusThread != null) {
//                asyncStatusThread.stopThread();
//            }
        } catch (HardwareInterfaceException e) {
            e.printStackTrace();
        }
        isOpened = false;
    }

    public void open() throws HardwareInterfaceException {
        //check if device is configured
//        if (usbDevice.isConfigured() == false) {
//            throw new HardwareInterfaceException("CypressFX2RetinaLinux.open(): UsbDevice not configured!");
//        }
//        if (usbInterface.isClaimed() == false) {
//            claimInterface();
//        }
//        if (asyncStatusThread == null) {
//            asyncStatusThread = new AsyncStatusThread(this);
//            asyncStatusThread.start();
//        }
    }

    public boolean isOpen() {
        return true;//usbDevice.isConfigured();
    }

    public void setPowerDown(boolean powerDown) throws HardwareInterfaceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /** Sends a vendor request with the new bias values */
    public void sendConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        open();
        if (biasgen.getPotArray() == null) {
            log.warning("BiasgenUSBInterface.send(): potArray=null");
            return; // may not have been constructed yet.

        }

        byte[] toSend=formatConfigurationBytes(biasgen);
        //sendBiasBytes(toSend);
        vendorRequest(VENDOR_REQUEST_SEND_BIAS_BYTES, (short) 0, (short) 0, toSend);
        HardwareInterfaceException.clearException();

    }

    public void flashConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * Claim this interface for exclusive access.
     */
    public void claimInterface() {
//        try {
////            usbInterface.claim();
//        } catch (UsbException uE) {
//            /* If we can't claim the interface, that means someone else is
//             * using the interface.
//             */
//            log.warning("Could not claim interface of AER device : " + uE.getMessage());
//            return;
//        }
    }

    /**
     * Release this interface from exclusive access.
     */
    synchronized public void releaseInterface() {
//        try {
//            usbInterface.release();
//        } catch (UsbException uE) {
//            /* If we can't claim the interface, that means someone else is
//             * using the interface.
//             */
//            log.warning("Could not release interface of AER device : " + uE.getMessage());
//            return;
//        }
    }

    /**
     * Set LED on board. For Debugging.
     * @param value true to turn on
     * @return true, if successful.
     */
    public boolean setLed(boolean value) {
        return vendorRequest(VENDOR_REQUEST_SET_LED, (short) (value ? 0 : 1), (short) 0, new byte[0]);
    }

    /**
     * Start AER data from IN endpoint 3
     * @return true, if successful.
     */
    public boolean startAer() {
        boolean b = vendorRequest(VENDOR_REQUEST_START_TRANSFER, (short) 0, (short) 0, new byte[0]);
        if (b) {
            inEndpointEnabled = true;
        }
        return b;
    }

    /**
     * Start AER data from IN endpoint 3
     * @return true, if successful.
     */
    public boolean stopAer() {
        boolean b = vendorRequest(VENDOR_REQUEST_STOP_TRANSFER, (short) 0, (short) 0, new byte[0]);
        if (b) {
            inEndpointEnabled = false;
        }
        log.info("data aquisition stopped:" + b);
        return b;
    }

    /**
     * Submit Vendor Request
     * @param request the request number
     * @param value the request value
     * @param index the index of the request
     * @param data the request data byte array
     * @return true, if successful.
     */
    synchronized public boolean vendorRequest(byte request, short value, short index, byte[] data) {
        byte bvendor[] = new byte[5+data.length];
        bvendor[0] = request;
        bvendor[1] = (byte) (((value << 8) >> 8) & 0x00ff);
        bvendor[2] = (byte) (value >> 8 & 0x00ff);
        bvendor[3] = (byte) (((index << 8) >> 8) & 0x00ff);
        bvendor[4] = (byte) (index >> 8 & 0x00ff);
        System.arraycopy(data, 0, bvendor, 5, data.length);
        try {
            retinaVendor.write(bvendor);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
//        byte bmRequestType = VENDOR_DEVICE_OUT_REQUEST;
//        UsbControlIrp vendorRequestIrp = usbDevice.createUsbControlIrp(bmRequestType, request, value, index);
//        vendorRequestIrp.setData(data);
////        log.info("length=" + vendorRequestIrp.getLength());
//        vendorRequestIrp.setLength(data.length);
//        vendorRequestIrp.setAcceptShortPacket(true);
//
//        try {
//            usbDevice.syncSubmit(vendorRequestIrp);
//            log.info("vendor request 0x" + UsbUtil.toHexString(request) + ", submitted bytes =" + vendorRequestIrp.getActualLength());
//            return vendorRequestIrp.isComplete();
//        } catch (UsbException uE) {
//            log.info("UsbExcepton at Vendor Request: " + uE.getMessage());
//            return false;
//        } finally {
//            /* Make sure to try and release the interface. */
//            try {
//                usbInterface.release();
//            } catch (UsbException uE) { /* FIXME - define why this may happen */ }
//        }
    }

    public AEReader getAeReader() {
        return aeReader;
    }

    public void setAeReader(AEReader aeReader) {
        this.aeReader = aeReader;
    }

    // start the aereader thread
    public void startAEReader() {
        setAeReader(new AEReader(this));
        log.info("Start AE reader..");
        getAeReader().start();
    }

    // stop the aereader thread
    public void stopAEReader() {
        if (getAeReader() != null) {
            // close device
            getAeReader().finish();
            setAeReader(null);
            releaseInterface();
        }
    }

    public void setArrayReset(boolean value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * Class to listen to aer events
     */
    public class AEReader extends Thread implements Runnable {

        private byte[] buffer = null;
        CypressFX2RetinaLinux monitor;

        public AEReader(CypressFX2RetinaLinux monitor) {
            this.monitor = monitor;
            /* This is a list of all this interface's endpoints. */
            allocateAEBuffers();

            buffer = new byte[512];//UsbUtil.unsignedInt(usbPipe.getUsbEndpoint().getUsbEndpointDescriptor().wMaxPacketSize())];
        }

        public void run() {
            int length = 0;
            int swap=0;
            while (running) {
                try{
                length = retina.read(buffer);
                }catch(IOException e){e.printStackTrace();}
                if (running) {
                  //  log.info(length + " ae bytes read:");//+Integer.toHexString(buffer[0])+","+Integer.toHexString(buffer[1])+","+Integer.toHexString(buffer[2])+","+Integer.toHexString(buffer[3]));

                    if (length > 0) {
                        translateEvents_code(buffer, length);
                    }
                    if (timestampsReset) {
                        log.info("timestampsReset: flushing aePacketRawPool buffers");
                        aePacketRawPool.reset();
                        timestampsReset = false;
                    }

                }
            }
        }

        synchronized private void submit(int BufNumber) {
        }

        /**
         * Stop/abort listening for data events.
         */
        synchronized public void finish() {
            running = false;
        }

        synchronized public void resetTimestamps() {
            log.info(CypressFX2RetinaLinux.this + ": wrapAdd=" + wrapAdd + ", zeroing it");
            wrapAdd = WRAP_START;
            timestampsReset = true; // will inform reader thread that timestamps are reset

        }
        protected boolean running = true;
        volatile boolean timestampsReset = false; // used to tell processData that another thread has reset timestamps

    }

    /** Method that translates the UsbIoBuffer when a board that has a CPLD to timetamp events and that uses the CypressFX2 in slave 
     * FIFO mode, such as the USBAERmini2 board or StereoRetinaBoard, is used. 
     * <p>
     *On these boards, the msb of the timestamp is used to signal a wrap (the actual timestamp is only 14 bits).
     * The timestamp is also used to signal a timestamp reset
     *
     * The wrapAdd is incremented when an emtpy event is received which has the timestamp bit 15
     * set to one.
     * The timestamp is reset when an event is received which has the timestamp bit 14 set.
     *<p>
     * Therefore for a valid event only 14 bits of the 16 transmitted timestamp bits are valid, bits 14 and 15
     * are the status bits. overflow happens every 16 ms.
     * This way, no roll overs go by undetected, and the problem of invalid wraps doesn't arise.
     *@param b the data buffer
     */
    protected void translateEvents_code(byte[] b, int bytesSent) {

//            System.out.println("buf has "+b.BytesTransferred+" bytes");
        synchronized (aePacketRawPool) {

            AEPacketRaw buffer = aePacketRawPool.writeBuffer();
            //if buffer.
            //    if(buffer.overrunOccuredFlag) return;  // don't bother if there's already an overrun, consumer must get the events to clear this flag before there is more room for new events
            int shortts;
            int NumberOfWrapEvents;
            NumberOfWrapEvents = 0;

            byte[] aeBuffer = b;
            //            byte lsb,msb;
//            int bytesSent = b.length;
            if (bytesSent % 4 != 0) {
//                System.out.println("CypressFX2.AEReader.translateEvents(): warning: "+bytesSent+" bytes sent, which is not multiple of 4");
                bytesSent = (bytesSent / 4) * 4; // truncate off any extra part-event

            }

            int[] addresses = buffer.getAddresses();
            int[] timestamps = buffer.getTimestamps();

            // write the start of the packet
            buffer.lastCaptureIndex = eventCounter;

            for (int i = 0; i < bytesSent; i += 4) {
//                        if(eventCounter>aeBufferSize-1){
//                            buffer.overrunOccuredFlag=true;
//    //                                        log.warning("overrun");
//                            return; // return, output event buffer is full and we cannot add any more events to it.
//                            //no more events will be translated until the existing events have been consumed by acquireAvailableEventsFromDriver
//                        }

                if ((aeBuffer[i + 3] & 0x80) == 0x80) { // timestamp bit 16 is one -> wrap
                    // now we need to increment the wrapAdd

                   // log.info("translateEvents_code: 0x80 wrap");
                    wrapAdd += 0x4000L; //uses only 14 bit timestamps

                    //System.out.println("received wrap event, index:" + eventCounter + " wrapAdd: "+ wrapAdd);
                    NumberOfWrapEvents++;
                } else if ((aeBuffer[i + 3] & 0x40) == 0x40) { // timestamp bit 15 is one -> wrapAdd reset
                    // this firmware version uses reset events to reset timestamps

                    log.info("translateEvents_code: 0x40 wrap reset");
                //this.resetTimestamps();
                // log.info("got reset event, timestamp " + (0xffff&((short)aeBuffer[i]&0xff | ((short)aeBuffer[i+1]&0xff)<<8)));
                } else if ((eventCounter > aeBufferSize - 1) || (buffer.overrunOccuredFlag)) { // just do nothing, throw away events

                    log.info("translateEvents_code: overrun="+buffer.overrunOccuredFlag+",eventCounter="+eventCounter+",aeBufferSize"+aeBufferSize);

                    //buffer.overrunOccuredFlag = false;
                    try{
                    setEventAcquisitionEnabled(false);
                    }catch(HardwareInterfaceException e){}
                    
                } else {
                    // address is LSB MSB
                    try {
//                        log.info("buffer size = " + addresses.length + ", eventcounter = " + eventCounter);
                        addresses[eventCounter] = (int) ((aeBuffer[i] & 0xFF) | ((aeBuffer[i + 1] & 0xFF) << 8));

                        // same for timestamp, LSB MSB
                        shortts = (aeBuffer[i + 2] & 0xff | ((aeBuffer[i + 3] & 0xff) << 8)); // this is 15 bit value of timestamp in TICK_US tick

                        timestamps[eventCounter] = (int) wrapAdd + (TICK_US * (shortts));// + wrapAdd)); //*TICK_US; //add in the wrap offset and convert to 1us tick
                        // this is USB2AERmini2 or StereoRetina board which have 1us timestamp tick

                        eventCounter++;
                        buffer.setNumEvents(eventCounter);
                    } catch (ArrayIndexOutOfBoundsException uE) {
                        log.warning("Buffer overrun in translateEvents_code():" + uE.getMessage());
                    }
                }
            } // end for

            // write capture size
            buffer.lastCaptureLength = eventCounter - buffer.lastCaptureIndex;
//            // debug negative time differences!!
//            String s="";
//            log.info("timestamps, buffer.lastCaptureLength="+buffer.lastCaptureLength);
////            for (int i = buffer.lastCaptureIndex; i < eventCounter; i ++)
////                s = s+timestamps[i]+",";
//            log.info(""+timestamps[buffer.lastCaptureIndex]);
//            log.info(""+timestamps[eventCounter-1]);
        // if (NumberOfWrapEvents!=0) {
        //System.out.println("Number of wrap events received: "+ NumberOfWrapEvents);
        //}
        //System.out.println("wrapAdd : "+ wrapAdd);
        } // sync on aePacketRawPool

    }
    /** The pool of raw AE packets, used for data transfer */
    protected AEPacketRawPool aePacketRawPool = new AEPacketRawPool();
    // wrapAdd is the time to add to short timestamp to unwrap it
    final int WRAP_START = 0; //(int)(0xFFFFFFFFL&(2147483648L-0x400000L)); // set high to test big wrap 1<<30;

    volatile int wrapAdd = WRAP_START; //0;

    int eventCounter = 0;  // counts events acquired but not yet passed to user

    public static final int AE_BUFFER_SIZE = 1000000; // should handle 5Meps at 30FPS

    /** this is the size of the AEPacketRaw that are part of AEPacketRawPool that double buffer the translated events between rendering and capture threads */
    private int aeBufferSize = prefs.getInt("CypressFX2.aeBufferSize", AE_BUFFER_SIZE);
    final short TICK_US = 1; // time in us of each timestamp count here on host, could be different on board

    protected AEPacketRaw lastEventsAcquired = new AEPacketRaw();
    /** the event reader - a buffer pool thread from USBIO subclassing */
    protected AEReader aeReader = null;
    /** the thread that reads device status messages on EP1 */
//    protected AsyncStatusThread asyncStatusThread = null;
    /**
     * event supplied to listeners when new events are collected. this is final because it is just a marker for the listeners that new events are available
     */
    public final PropertyChangeEvent NEW_EVENTS_PROPERTY_CHANGE = new PropertyChangeEvent(this, "NewEvents", null, null);
    PropertyChangeSupport support = new PropertyChangeSupport(this);
    volatile boolean dontwrap = false; // used for resetTimestamps

    /** device open status */
    protected boolean isOpened = false;
    //  

    /**
     * Object that holds pool of AEPacketRaw that handles data interchange between capture and other (rendering) threads.
     * While the capture thread (AEReader.processData) captures events into one buffer (an AEPacketRaw) the other thread (AEViewer.run()) can
     * render the events. The only time the monitor on the pool needs to be acquired is when swapping or initializing the buffers, to prevent
     * either referencing unrelated data or having memory change out from under you.
     */
    private class AEPacketRawPool {

        int capacity;
        AEPacketRaw[] buffers;
        AEPacketRaw lastBufferReference;
        volatile int readBuffer = 0,  writeBuffer = 1; // this buffer is the one currently being read from


        AEPacketRawPool() {
            allocateMemory();
            reset();
        }

        /** swap the buffers so that the buffer that was getting written is now the one that is read from, and the one that was read from is
         * now the one written to. Thread safe.
         */
        synchronized final void swap() {
            lastBufferReference = buffers[readBuffer];
            if (readBuffer == 0) {
                readBuffer = 1;
                writeBuffer = 0;
            } else {
                readBuffer = 0;
                writeBuffer = 1;
            }
            writeBuffer().clear();
            writeBuffer().overrunOccuredFlag = false; // mark new write buffer clean, no overrun happened yet. writer sets this if it happens

        }

        /** @return buffer to read from */
        synchronized final AEPacketRaw readBuffer() {
            return buffers[readBuffer];
        }

        /** @return buffer to write to */
        synchronized final AEPacketRaw writeBuffer() {
            return buffers[writeBuffer];
        }

        /** Set the current buffer to be the first one and clear the write buffer */
        synchronized final void reset() {
            readBuffer = 0;
            writeBuffer = 1;
            buffers[writeBuffer].clear(); // new events go into this buffer which should be empty

            buffers[readBuffer].clear();  // clear read buffer in case this buffer was reset by resetTimestamps
//            log.info("buffers reset");

        }

        // allocates AEPacketRaw each with capacity AE_BUFFER_SIZE
        private void allocateMemory() {
            buffers = new AEPacketRaw[2];
            for (int i = 0; i < buffers.length; i++) {
                buffers[i] = new AEPacketRaw();
                buffers[i].ensureCapacity(getAEBufferSize()); // preallocate this memory for capture thread and to try to make it contiguous

            }
        }
    }

    public byte[] formatConfigurationBytes(Biasgen biasgen) {
        // we need to cast from PotArray to IPotArray, because we need the shift register stuff
        IPotArray iPotArray = (IPotArray) biasgen.getPotArray();

        //        throw new IPotException("null USBIO interface");
        //        if(iPotArray==null) throw new IPotException("null iPotArray");
        //
        // we make an array of bytes to hold the values sent, then we fill the array, copy it to a
        // new array of the proper size, and pass it to the routine that actually sends a vendor request
        // with a data buffer that is the bytes

        byte[] bytes = new byte[iPotArray.getNumPots() * MAX_BYTES_PER_BIAS];
        int byteIndex = 0;
        //        System.out.print("BiasgenUSBInterface.send()");


        Iterator i = iPotArray.getShiftRegisterIterator();
        while (i.hasNext()) {
            // for each bias starting with the first one (the one closest to the ** END ** of the shift register
            // we get the binary representation in byte[] form and from MSB ro LSB stuff these values into the byte array
            IPot iPot = (IPot) i.next();
            byte[] thisBiasBytes = iPot.getBinaryRepresentation();
            System.arraycopy(thisBiasBytes, 0, bytes, byteIndex, thisBiasBytes.length);
            byteIndex += thisBiasBytes.length;
        }
        byte[] toSend = new byte[byteIndex];
        System.arraycopy(bytes, 0, toSend, 0, byteIndex);
        return toSend;
    }

//    class AsyncStatusThread extends Thread {
//
//        private byte[] buffer = null;
//        protected boolean running = true;
//        protected UsbPipe usbPipe = null;
//        protected UsbIrp usbIrp = null;
////       UsbIoPipe pipe;
//        CypressFX2RetinaLinux monitor;
////        boolean stop=false;
//        byte msg;
//
//        AsyncStatusThread(CypressFX2RetinaLinux monitor) {
//            this.monitor = monitor;
//            /* This is a list of all this interface's endpoints. */
//            List usbEndpoints = usbInterface.getUsbEndpoints();
//
//            UsbEndpoint usbEndpoint = null;
//            int i;
//
//            for (i = 0; i < usbEndpoints.size(); i++) {
//                usbEndpoint = (UsbEndpoint) usbEndpoints.get(i);
//
//                /* Use the bulk IN endpoint with 64 bytes packets 
//                 */
//                if (UsbConst.ENDPOINT_TYPE_BULK == usbEndpoint.getType() && UsbConst.ENDPOINT_DIRECTION_IN == usbEndpoint.getDirection() && usbEndpoint.getUsbEndpointDescriptor().wMaxPacketSize() == 64) {
//                    break;
//                } else {
//                    usbEndpoint = null;
//                }
//            }
//
//            /* If the endpoint is null, we didn't find any endpoints we can use; this device does not
//             * meet the HID spec (it is fundamentally broken!).
//             */
//            if (null == usbEndpoint) {
//                log.warning("interface does not have the required bulk-in 64 byte endpoint.");
//                return;
//            } else {
//                log.info("Endpoint" + i + " chosen for status pipe.");
//            }
////            claimInterface();
//            usbPipe = usbEndpoint.getUsbPipe();
//
//            /* We need to open the endpoint's pipe. */
//            try {
//                if (!usbPipe.isOpen()) {
//                    usbPipe.open();
//                }
//            } catch (UsbException uE) {
//                /* If we couldn't open the pipe, we can't talk to the HID interface.
//                 * This is not a usualy condition, so error recovery needs to look at
//                 * the specific error to determine what to do now.
//                 * We will just bail out.
//                 */
//                log.warning("Could not open endpoint to communicate with AER device : " + uE.getMessage());
//                return;
//            }
//            allocateAEBuffers();
//
//            buffer = new byte[UsbUtil.unsignedInt(usbPipe.getUsbEndpoint().getUsbEndpointDescriptor().wMaxPacketSize())];
//            usbIrp = new DefaultUsbIrp(buffer);
//        }
//
//        public void run() {
//            int length = 0;
//            while (running) {
//                submit();
//                usbIrp.waitUntilComplete();
//                length = usbIrp.getActualLength();
//                if (running) {
//                    if (length > 0) {
//                        log.info(length + " status bytes read.");
//                        msg = buffer[0];
//                        if (msg == 1) {
//                            AEReader rd = getAeReader();
//                            if (rd != null) {
//                                log.info("*********************************** CypressFX2.AsyncStatusThread.run(): timestamps externally reset");
//                                rd.resetTimestamps();
//                            } else {
//                                log.info("Received timestamp external reset message, but monitor is not running");
//                            }
//                        }
//                    }
//                }
//            }
//        }
//
//        synchronized private void submit() {
//            usbIrp.setComplete(false);
//            try {
//                //sync submit irp to pipe
//                usbPipe.asyncSubmit(usbIrp);
//            } catch (Exception uE) {
//                if (running) {
//                    log.warning("Unable to submit data buffer to AER device : " + uE.getMessage());
//                }
//            }
//        }
//
//        /**
//         * Stop/abort listening for data events.
//         */
//        synchronized public void stopThread() {
//            running = false;
//            try {
//                //wait for current pipe submission to finish
////            usbIrp.waitUntilComplete();
//                usbPipe.abortAllSubmissions();
//                usbPipe.close();
//            } catch (Exception uE) {
//                log.warning("AsyncStatusThread.close():" + uE.getMessage());
//            }
//        }
//        public void stopThread() {
////            if (pipe != null) {
////                pipe.abortPipe();
////            }
////            interrupt();
//        }

//        public void run() {
//            setName("AsyncStatusThread");
//            int status;
//            while (running && !isInterrupted()) {
//                buffer.NumberOfBytesToTransfer = 64;
//                status = pipe.read(buffer);
//                if (status == 0) {
//                    if (stop) {
//                        log.info("Error submitting read on status pipe: " + UsbIo.errorText(buffer.Status));
//                    }
//                    break;
//                }
//                status = pipe.waitForCompletion(buffer);
//                if (status != 0 && buffer.Status != UsbIoErrorCodes.USBIO_ERR_CANCELED) {
//                    if (!stop && !isInterrupted()) {
//                        log.warning("Error waiting for completion of read on status pipe: " + UsbIo.errorText(buffer.Status));
//                    }
//                    break;
//                }
//                if (buffer.BytesTransferred > 0) {
//                } // we get 0 byte read on stopping device
//
//            }
////            System.out.println("Status reader thread terminated.");
//        } // run()
//    }
}
