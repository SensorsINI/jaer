/*
 * Martin Ebner, igi, tu graz
 * linux driver for dvs
 * based upon cypressfx2 class
 * using jsr80 usb library
 */
package ch.unizh.ini.caviar.hardwareinterface.usb.linux;

import ch.unizh.ini.caviar.aemonitor.AEListener;
import ch.unizh.ini.caviar.aemonitor.AEMonitorInterface;
import ch.unizh.ini.caviar.aemonitor.AEPacketRaw;
import ch.unizh.ini.caviar.biasgen.*;
import ch.unizh.ini.caviar.chip.AEChip;
import ch.unizh.ini.caviar.hardwareinterface.HardwareInterfaceException;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeSupport;
import javax.usb.*;
import javax.usb.util.*;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * The Tmpdiff128 retina under linux using the JSR-80 
 * linux java USB library 
 * (<a href="http://sourceforge.net/projects/javax-usb">JSR-80 project</a>).
 * 
 * @author Martin Ebner (martin_ebner)
 */
public class CypressFX2RetinaLinux implements AEMonitorInterface, BiasgenHardwareInterface {

    UsbDevice usbDevice = null;
    protected AEChip chip;
    protected boolean inEndpointEnabled = false;
    protected Logger log = Logger.getLogger("CypressFX2RetinaLinux");
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

    public static final byte VENDOR_DEVICE_OUT_REQUEST =
            UsbConst.REQUESTTYPE_DIRECTION_OUT | UsbConst.REQUESTTYPE_TYPE_VENDOR | UsbConst.REQUESTTYPE_RECIPIENT_DEVICE;

    /** This instance is typically constructint interfaceNumbered by the factory instance (HardwareInterfaceFactoryLinux) */
    public CypressFX2RetinaLinux(UsbDevice usbDevice) {
        this.usbDevice = usbDevice;
    }

    public AEPacketRaw acquireAvailableEventsFromDriver() throws HardwareInterfaceException {
        open();
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
        }
       log.info("eventcounter="+eventCounter);
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

    public void resetTimestamps() {
        log.info(this+".resetTimestamps(): zeroing timestamps");
        vendorRequest(usbDevice, VENDOR_REQUEST_SEND_BIAS_BYTES, (short) 0, (short) 0, new byte[1]);
    }

    public boolean overrunOccurred() {
        return false;
    }

    public int getAEBufferSize() {
        return aeBufferSize;
    }

    void allocateAEBuffers(){
        synchronized(aePacketRawPool){
            aePacketRawPool.allocateMemory();
        }
    }

    public void setAEBufferSize(int size) {
        if(size<1000 || size>1000000){
            log.warning("ignoring unreasonable aeBufferSize of "+size+", choose a more reasonable size between 1000 and 1000000");
            return;
        }
        this.aeBufferSize=size;
        prefs.putInt("CypressFX2.aeBufferSize",aeBufferSize);
        allocateAEBuffers();
    }

    public void setEventAcquisitionEnabled(boolean enable) throws HardwareInterfaceException {
        if (enable) {
            allocateAEBuffers();
            startAer(usbDevice);
            inEndpointEnabled=true;
        } else {
            stopAer(usbDevice);
            inEndpointEnabled=false;
        }
    }

    public boolean isEventAcquisitionEnabled() {
        return inEndpointEnabled;
    }

    public void addAEListener(AEListener listener) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void removeAEListener(AEListener listener) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public int getMaxCapacity() {
        return -1;
    }

    public int getEstimatedEventRate() {
        return -1;
    }

    public int getTimestampTickUs() {
        return -1;
    }

    public void setChip(AEChip chip) {
        this.chip = chip;
    }

    public AEChip getChip() {
        return chip;
    }

    public String[] getStringDescriptors() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public int[] getVIDPID() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public short getVID() {

        throw new UnsupportedOperationException("Not supported yet.");
    }

    public short getPID() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public short getDID() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public String getTypeName() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void close() {
        //release interface 0 of device
        UsbInterface usbInterface = (UsbInterface) usbDevice.getActiveUsbConfiguration().getUsbInterfaces().get(0);
        if (usbInterface.isClaimed() == true) {
            releaseInterface((UsbInterface) usbDevice.getActiveUsbConfiguration().getUsbInterfaces().get(0));
        }
        try{
//            if (this.isEventAcquisitionEnabled()) {
            setEventAcquisitionEnabled(false);
//            stopAEReader();
//            }
//            if(asyncStatusThread!=null) asyncStatusThread.stopThread();
        }catch(HardwareInterfaceException e){
            e.printStackTrace();
        }
            }

    public void open() throws HardwareInterfaceException {
        //check if device is configured
        if (usbDevice.isConfigured() == false) {
            throw new HardwareInterfaceException("CypressFX2RetinaLinux.open(): UsbDevice not configured!");
        //claim interface 0 of device
        }
        UsbInterface usbInterface = (UsbInterface) usbDevice.getActiveUsbConfiguration().getUsbInterfaces().get(0);
        if (usbInterface.isClaimed() == false) {
            claimInterface(usbInterface);
        //TODO: start endpoint 0 listener
        }
    }

    public boolean isOpen() {
        return usbDevice.isConfigured();
    }

    public void setPowerDown(boolean powerDown) throws HardwareInterfaceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void sendPotValues(Biasgen biasgen) throws HardwareInterfaceException {
        open();
        if (biasgen.getPotArray() == null) {
            log.warning("BiasgenUSBInterface.send(): potArray=null");
            return; // may not have been constructed yet.

        }

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

        //sendBiasBytes(toSend);
        vendorRequest(usbDevice, VENDOR_REQUEST_SEND_BIAS_BYTES, (short) 0, (short) 0, toSend);
        HardwareInterfaceException.clearException();

        UsbInterface usbInterface = (UsbInterface) usbDevice.getActiveUsbConfiguration().getUsbInterfaces().get(0);
        try {
            setEventAcquisitionEnabled(true);
        } catch (Exception uE) {

            log.warning("exception : " + uE.getMessage());
            return;
        }

        driveUsbAer(usbInterface);
    }

    public void flashPotValues(Biasgen biasgen) throws HardwareInterfaceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * Claim an interface
     * @param usbInterface The UsbInterface.
     */
    synchronized public void claimInterface(UsbInterface usbInterface) {
        try {
            usbInterface.claim();
        } catch (UsbException uE) {
            /* If we can't claim the interface, that means someone else is
             * using the interface.
             */
            log.warning("Could not claim interface of AER device : " + uE.getMessage());
            return;
        }
    }

    /**
     * Claim an interface
     * @param usbInterface The UsbInterface.
     */
    synchronized public  void releaseInterface(UsbInterface usbInterface) {
        try {
            usbInterface.release();
        } catch (UsbException uE) {
            /* If we can't claim the interface, that means someone else is
             * using the interface.
             */
            log.warning("Could not release interface of AER device : " + uE.getMessage());
            return;
        }
    }

    /**
     * Set LED on board. For Debugging.
     * @param usbDevice.
     * @return true, if successful.
     */
    public boolean setLed(UsbDevice usbDevice, boolean value) {
        return vendorRequest(usbDevice, VENDOR_REQUEST_SET_LED, (short) (value ? 0 : 1), (short) 0, new byte[0]);
    }

//	/**
//	 * Start AER data from IN endpoint 3
//	 * @param usbDevice.
//	 * @return true, if successful.
//	 */
//	public static boolean setBias(UsbDevice usbDevice)
//	{
//		//byte b = new byte[100];
//		System.out.println("setting bias...");
//		//Biasgen biasGen = new Biasgen();
//		String file = "slow.pot";
//		byte[] data = new byte[36];
//		try{
//		FileInputStream f = new FileInputStream(file);
//		f.read(data);
//		f.close();
//		} catch (Exception e) {System.out.println(e.getMessage());}
//		System.out.println(data.length + " bytes bias settings loaded from " + file);
////		
////		try{
////			System.out.println(System.getProperty("user.dir"));
////		biasGen.importPreferences(new java.io.FileInputStream("src/dvs128_slow.xml"));
////		} catch (Exception e) {System.out.println(e.getMessage());}
////		return true;
//		return vendorRequest(usbDevice,VENDOR_REQUEST_SEND_BIAS_BYTES,(short)0,(short)0, data);
//	}
    /**
     * Start AER data from IN endpoint 3
     * @param usbDevice.
     * @return true, if successful.
     */
    public boolean startAer(UsbDevice usbDevice) {
        return vendorRequest(usbDevice, VENDOR_REQUEST_START_TRANSFER, (short) 0, (short) 0, new byte[0]);
    }

    /**
     * Start AER data from IN endpoint 3
     * @param usbDevice.
     * @return true, if successful.
     */
    public boolean stopAer(UsbDevice usbDevice) {
        return vendorRequest(usbDevice, VENDOR_REQUEST_STOP_TRANSFER, (short) 0, (short) 0, new byte[0]);
    }

    /**
     * Submit Vendor Request
     * @param usbDevice
     * @return true, if successful.
     */
    synchronized public boolean vendorRequest(UsbDevice usbDevice, byte request, short value, short index, byte[] data) {
        /* To check the usage, communication via the Default Control Pipe is required.
         * Normally the DCP is not an exclusive-access pipe, but in this case
         * the recipient of the communication is an interface.  So,
         * the communication may fail if that UsbInterface has not been claim()ed.
         * If you think that is a strange way to design things, go complain to the
         * USB designers ;)
         */

        log.info("preparing vendor request 0x" + UsbUtil.toHexString(request));
        byte bmRequestType = VENDOR_DEVICE_OUT_REQUEST;
        UsbControlIrp vendorRequestIrp = usbDevice.createUsbControlIrp(bmRequestType, request, value, index);
        vendorRequestIrp.setData(data);
        log.info("length=" + vendorRequestIrp.getLength());
        vendorRequestIrp.setLength(data.length);
        vendorRequestIrp.setAcceptShortPacket(true);

        try {
            /* submit vendor request
             */
            log.info("submitting vendor request.");
            usbDevice.syncSubmit(vendorRequestIrp);
            //vendorRequestIrp.complete();
            log.info("ActualLength : " + vendorRequestIrp.getActualLength());
            log.info("isComplete   : " + vendorRequestIrp.isComplete());
            return vendorRequestIrp.isComplete();
        } catch (UsbException uE) {
            log.info("UsbExcepton at Vendor Request: " + uE.getMessage());
            return false;
        } finally {
            /* Make sure to try and release the interface. */
            //??try { usbInterface.release(); }
            //catch ( UsbException uE ) { /* FIXME - define why this may happen */ }
        }
    }

    /**
     * Drive the AER device in endpoint
     * @param usbInterface.
     */
    public void driveUsbAer(UsbInterface usbInterface) {


        /* This is a list of all this interface's endpoints. */
        List usbEndpoints = usbInterface.getUsbEndpoints();

        UsbEndpoint usbEndpoint = null;
        int i;

        for (i = 0; i < usbEndpoints.size(); i++) {
            usbEndpoint = (UsbEndpoint) usbEndpoints.get(i);

            /* Use the bulk IN endpoint with 64 bytes packets 
             */
            if (UsbConst.ENDPOINT_TYPE_BULK == usbEndpoint.getType() && UsbConst.ENDPOINT_DIRECTION_IN == usbEndpoint.getDirection() && usbEndpoint.getUsbEndpointDescriptor().wMaxPacketSize() == 512) {
                break;
            } else {
                usbEndpoint = null;
            }
        }

        /* If the endpoint is null, we didn't find any endpoints we can use; this device does not
         * meet the HID spec (it is fundamentally broken!).
         */
        if (null == usbEndpoint) {
            log.warning("interface does not have the required bulk-in 512 byte endpoint.");
            return;
        } else {
            log.info("Endpoint" + i + " chosen.");
        }
        claimInterface(usbInterface);
        UsbPipe usbPipe = usbEndpoint.getUsbPipe();

        /* We need to open the endpoint's pipe. */
        try {
            usbPipe.open();
        } catch (UsbException uE) {
            /* If we couldn't open the pipe, we can't talk to the HID interface.
             * This is not a usualy condition, so error recovery needs to look at
             * the specific error to determine what to do now.
             * We will just bail out.
             */
            log.warning("Could not open endpoint to communicate with AER device : " + uE.getMessage());
            try {
                usbInterface.release();
            } catch (UsbException uE2) { /* FIXME - define why this might happen. */ }
            return;
        }

        UsbAerRunnable hmR = new UsbAerRunnable(usbPipe);
        Thread t = new Thread(hmR);

        log.info("Driving AER device..");
//        log.info("Press Enter when done.");

        t.start();
//TODO:stop thread
//		try {
//			/* This just waits for Enter to get pressed. */
//			System.in.read();
//		} catch ( Exception e ) {
//			System.out.println("Exception while waiting for Enter : " + e.getMessage());
//		}
//
//		hmR.stop();
//
//		try {
//			usbPipe.close();
//			usbInterface.release();
//		} catch ( UsbException uE ) { /* FIXME - define why this might happen. */ }
//
        log.info("Done driving AER device.");
    }

    /**
     * Class to listen to aer events
     */
    public class UsbAerRunnable implements Runnable {
       
        private byte[] buffer=null;
        
        public UsbAerRunnable(UsbPipe pipe) {
            usbPipe = pipe;
            buffer = new byte[UsbUtil.unsignedInt(usbPipe.getUsbEndpoint().getUsbEndpointDescriptor().wMaxPacketSize())];
            
        }

        public void run() {
            int length = 0;
            UsbIrp usbIrp = null;

            while (running) {
//                log.info("ae pipe sync read.");
                try {
                    length = usbPipe.syncSubmit(buffer);
                } catch (UsbException uE) {
                     if (running) {
                        log.warning("Unable to submit data buffer to AER device : " + uE.getMessage());
                        break;
                    }
                }

                if (running) {
                    if (length > 0) {
//                        System.out.print("Got " + length + " bytes of data from AER device :");
//                        for (int i = 0; i < length; i++) {
//                            System.out.print(" 0x" + UsbUtil.toHexString(buffer[i]));
//                        }
                        
                        translateEvents_code(buffer,length);
                    }
                }
            }
        }

        /**
         * Stop/abort listening for data events.
         */
        public void stop() {
            running = false;
            usbPipe.abortAllSubmissions();
        }
        public boolean running = true;
        public UsbPipe usbPipe = null;
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
     *@see #translateEvents
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

                    wrapAdd += 0x4000L; //uses only 14 bit timestamps

                    //System.out.println("received wrap event, index:" + eventCounter + " wrapAdd: "+ wrapAdd);
                    NumberOfWrapEvents++;
                } else if ((aeBuffer[i + 3] & 0x40) == 0x40) { // timestamp bit 15 is one -> wrapAdd reset
                    // this firmware version uses reset events to reset timestamps

                    this.resetTimestamps();
                // log.info("got reset event, timestamp " + (0xffff&((short)aeBuffer[i]&0xff | ((short)aeBuffer[i+1]&0xff)<<8)));
                } else if ((eventCounter > aeBufferSize - 1) || (buffer.overrunOccuredFlag)) { // just do nothing, throw away events

                    buffer.overrunOccuredFlag = true;
                } else {
                    // address is LSB MSB
                    try  {
//                        log.info("buffer size = " + addresses.length + ", eventcounter = " + eventCounter);
                    addresses[eventCounter] = (int) ((aeBuffer[i] & 0xFF) | ((aeBuffer[i + 1] & 0xFF) << 8));

                    // same for timestamp, LSB MSB
                    shortts = (aeBuffer[i + 2] & 0xff | ((aeBuffer[i + 3] & 0xff) << 8)); // this is 15 bit value of timestamp in TICK_US tick

                    timestamps[eventCounter] = (int) (TICK_US * (shortts + wrapAdd)); //*TICK_US; //add in the wrap offset and convert to 1us tick
                    // this is USB2AERmini2 or StereoRetina board which have 1us timestamp tick

                    eventCounter++;
                    buffer.setNumEvents(eventCounter);
                    } catch (ArrayIndexOutOfBoundsException uE)  {
                            log.warning("Buffer overrun in translateEvents_code():" + uE.getMessage());
                    }                              
                }
            } // end for

            // write capture size
            buffer.lastCaptureLength = eventCounter - buffer.lastCaptureIndex;
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

    public static final int AE_BUFFER_SIZE = 100000; // should handle 5Meps at 30FPS

    /** this is the size of the AEPacketRaw that are part of AEPacketRawPool that double buffer the translated events between rendering and capture threads */
    private int aeBufferSize = prefs.getInt("CypressFX2.aeBufferSize", AE_BUFFER_SIZE);
    final short TICK_US = 1; // time in us of each timestamp count here on host, could be different on board

    protected AEPacketRaw lastEventsAcquired = new AEPacketRaw();
    /**
     * event supplied to listeners when new events are collected. this is final because it is just a marker for the listeners that new events are available
     */
    public final PropertyChangeEvent NEW_EVENTS_PROPERTY_CHANGE = new PropertyChangeEvent(this, "NewEvents", null, null);
    PropertyChangeSupport support = new PropertyChangeSupport(this);
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
}
