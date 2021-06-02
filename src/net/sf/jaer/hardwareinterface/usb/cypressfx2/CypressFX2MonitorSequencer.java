/*
 * CypressFX2MonitorSequencer.java
 *
 * Created on November 11, 2005, 2:28 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */
package net.sf.jaer.hardwareinterface.usb.cypressfx2;

import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import net.sf.jaer.aemapper.AEMapper;
import net.sf.jaer.aemapper.AESoftMapper;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.aesequencer.AEMonitorSequencerInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.HasUpdatableFirmware;
import de.thesycon.usbio.UsbIo;
import de.thesycon.usbio.UsbIoBuf;
import de.thesycon.usbio.UsbIoInterface;
import de.thesycon.usbio.UsbIoWriter;
import de.thesycon.usbio.structs.USBIO_CLASS_OR_VENDOR_REQUEST;
import de.thesycon.usbio.structs.USBIO_DATA_BUFFER;
import de.thesycon.usbio.structs.USBIO_PIPE_PARAMETERS;

/**
 * Extends CypressFX2 to add functionality for sequencing and monitoring events.
 *
 * @author raphael
 */
public class CypressFX2MonitorSequencer extends CypressFX2 implements AEMonitorSequencerInterface, AESoftMapper, HasUpdatableFirmware {

    protected static Logger log = Logger.getLogger("CypressFX2MonitorSequencer");
    // consts
    final static byte ENDPOINT_OUT = (byte) 0x02;  // this is endpoint of AE fifo on Cypress FX2, 0x02 means OUT endpoint EP2.
    public static final byte VR_ENABLE_AE_OUT = (byte) 0xD0;  // vendor request to start sequencing
    public static final byte VR_DISABLE_AE_OUT = (byte) 0xC1; // vendor request to stop sequencing
    public static final byte VR_OPERATION_MODE = (byte) 0xC3; // config timestamp tick: either 1us or 33ns
    public static final byte VR_ENABLE_AE = (byte) 0xC6;  // start monitor and sequencer
    public static final byte VR_DISABLE_AE = (byte) 0xC7; // stop monitor and sequencer
    //  static final byte VR_WRITE_EEPROM_BYTES =(byte)0xC8;  // write the first 8 EEPROM bytes (VID,PID,DID), same functionality exists in CypressFX2, has to be cleaned up
    public static final byte VR_IS_TIMESTAMP_MASTER = (byte) 0xCB;
    public static final byte VR_MISSED_EVENTS = (byte) 0xCC;
    public static final byte VR_ENABLE_MISSED_EVENTS = (byte) 0xCD;

    public final static String CPLD_FIRMWARE_MONSEQ = "/net/sf/jaer/hardwareinterface/usb/cypressfx2/USBAERmini2.xsvf";
    protected AEWriter aeWriter;
    private BlockingQueue<AEPacketRaw> sequencingQueue; // this queue holds packets that should be sequenced in order
    int numOutEvents = 0;
    private boolean outEndpointEnabled = false;
    private float tick = 1f;
    private boolean Master = true;

    /** Creates a new instance of CypressFX2MonitorSequencer. Note that it is possible to construct several instances
     * and use each of them to open and read from the same device.
     *@param devNumber the desired device number, in range returned by CypressFX2MonitorSequencerFactory.getNumInterfacesAvailable
     */
    protected CypressFX2MonitorSequencer(int devNumber) {
        super(devNumber);

        TICK_US_BOARD = 1;

        this.EEPROM_SIZE = 0x8000;
    }
    private int estimateOutEventRate = 0;

    /** Computes an estimation of the out event rate
     *@return the estimated out rate
     */
    int computeEstimatedOutEventRate(AEPacketRaw events) {
        if ((events == null) || (events.getNumEvents() < 2)) {
            return 0;
        } else {
            int[] ts = events.getTimestamps();
            int n = events.getNumEvents();
            int dt = ts[n - 1] - ts[0];
            return (int) ((1e6f * n) / dt);
        }
    }

    @Override
    public String getTypeName() {
        return "CypressFX2MonitorSequencer";
    }

    /** Closes the device. Never throws an exception.
     */
    @Override
    synchronized public void close() {
        if (!isOpened) {
            log.warning("warning: close(): not open");
            return;
        }

        try {
            if (this.inEndpointEnabled && this.outEndpointEnabled) {
                stopMonitoringSequencing(); // disable monitoring and sequencing if they are still running
            } else if (this.inEndpointEnabled) {
                this.setEventAcquisitionEnabled(false);
            } else if (this.outEndpointEnabled) {
                this.disableEventSequencing();
            }
        } catch (HardwareInterfaceException e) {
            log.warning(e.getMessage());
        }
        gUsbIo.close();
        UsbIo.destroyDeviceList(gDevList);
        log.fine("CypressFX2MonitorSequencer.close(): device closed");
        isOpened = false;
    }

    @Override
    public void open() throws HardwareInterfaceException {
        super.open();

      //  tick = this.getOperationMode();

      //  this.isTimestampMaster();
    }

    /** @return returns true if EP2 is enabled
     */
    public boolean isOutEndpointEnabled() {
        return this.outEndpointEnabled;
    }
   // int cnt = 0;

    /** returns a string containing the class name and the serial number (if the device has been opened) */
 //   @Override
  /*  public String toString() {
        if (cnt++ > 500) // ugly hack to make sure that vendor request is sent only once in a few seconds
        {
            try {
                this.isTimestampMaster();
            } catch (HardwareInterfaceException e) {
                e.printStackTrace();
            }
            cnt = 0;
        }

        if (this.isOpened) {
            if (this.Master) {
                return (getStringDescriptors()[1] + ": " + getStringDescriptors()[this.numberOfStringDescriptors - 1] + " tick " + this.getTick() + " us");
            } else {
                return (getStringDescriptors()[1] + ": " + getStringDescriptors()[this.numberOfStringDescriptors - 1] + " slave");
            }
        }

        return (super.toString());
    }*/

    public float getTick() {
        return tick;
    }

    /** disables event sequencing: stops AEWriter thread and sends vendor request to device
     */
    public void disableEventSequencing() throws HardwareInterfaceException {
        stopAEWriter();
        sendVendorRequest(VR_DISABLE_AE_OUT);
        outEndpointEnabled = false;
    }

    /** returns the estimated out event rate
     */
    @Override
	public int getEstimatedOutEventRate() {
        return estimateOutEventRate;
    }

    /** not yet implemented */
    @Override
	public int getNumEventsSent() {
        if (aeWriter == null) {
            log.warning("null aeWriter, returning 0 events sent");
            return 0;
        }
        return this.aeWriter.getPosition();
    }

    /** not yet implemented */
    @Override
	public int getNumEventsToSend() {
        if (aeWriter == null) {
            log.warning("null aeWriter, returning 0 events to send");
            return 0;
        }
        return this.aeWriter.getNumEventsToSequence();
    }

    /** @return returns true if sequencing is enabled
     */
    @Override
	public boolean isEventSequencingEnabled() {
        return this.isOutEndpointEnabled();
    }

    /** starts sequencing and monitoring of events, starts AEReader and AEWriter and sends vendor request to device
    @param eventsToSend the events that should be sequenced, timestamps are realtive to last event,
    inter spike interval must not be bigger than 2^16
     */
    @Override
	public void startMonitoringSequencing(AEPacketRaw eventsToSend) throws HardwareInterfaceException {
        startMonitoringSequencing(eventsToSend, true);
    }

    /** starts sequencing and monitoring of events, starts AEReader and AEWriter
    @param eventsToSend the events that should be sequenced, timestamps are realtive to last event,
    inter spike interval must not be bigger than 2^16
    @param startDevice whether a vendor request should be sent to the device. usually this is set
     * to true or {@link #startMonitoringSequencing(AEPacketRaw eventsToSend)} is used, set to false only if multiple devices
     * should start synchronously, then it is better to start all AEReaders before sending the vendor requests.
     */
    public void startMonitoringSequencing(AEPacketRaw eventsToSend, boolean startDevice) throws HardwareInterfaceException {
        if (!isOpened) {
            open();
        }

        HardwareInterfaceException.clearException();

        numOutEvents = eventsToSend.getNumEvents();

        if (numOutEvents == 0) {
            estimateOutEventRate = computeEstimatedOutEventRate(null);
            startAEReader();
        } else {
            estimateOutEventRate = computeEstimatedOutEventRate(eventsToSend);
            startAEWriter(eventsToSend);
            if (!this.isEventAcquisitionEnabled()) {
                startAEReader();
            }
        }

        // wait a few ms to send the first packets before starting the device
        try {
            Thread.currentThread();
			Thread.sleep(10);
        } catch (InterruptedException e) {
        }

        if (startDevice) {
            sendVendorRequest(VR_ENABLE_AE);
        }
        outEndpointEnabled = true;
        inEndpointEnabled = true;
        log.fine("Montoring and sequencing enabled");
    }

    /** sends vendor request to device to enable sequencing and monitoring, used if
     * {@link #startMonitoringSequencing(AEPacketRaw eventsToSend, boolean startDevice)} is called
     * with startDevice equal to false
     */
    public void startDevice() throws HardwareInterfaceException {
        sendVendorRequest(VR_ENABLE_AE);
    }

    /** starts AEWriter Thread
     */
    protected void startAEWriter(AEPacketRaw eventsToSend) throws HardwareInterfaceException {
        aeWriter = new AEWriter(this, eventsToSend);

        gDevList = UsbIo.createDeviceList(GUID);

        //  aeWriter.unbind();

        int status = aeWriter.bind(this.interfaceNumber, ENDPOINT_OUT, this.gDevList, GUID);


        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("Could not bind out pipe: " + UsbIo.errorText(status));
        }

        USBIO_PIPE_PARAMETERS pipeParams = new USBIO_PIPE_PARAMETERS();
        pipeParams.Flags = UsbIoInterface.USBIO_SHORT_TRANSFER_OK;
        status = aeWriter.setPipeParameters(pipeParams);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("startAEWriter: can't set pipe parameters: " + UsbIo.errorText(status));
        }
        //else {
        //       log.info("out pipe bound");
        //}

        aeWriter.startThread(3);
    }

    /** stops AEWriter thread and deletes the aeWriter instance
     */
    protected void stopAEWriter() {
        if (aeWriter != null) {
            aeWriter.abortPipe();
            aeWriter.unbind();
            //aeWriter.shutdownThread();
            aeWriter = null;
        }
    }

    /** stops monitoring and sequencing of events, gets and returns the last events
     * from the driver
     * @return AEPacketRaw: the last events
     */
    @Override
	public AEPacketRaw stopMonitoringSequencing() throws HardwareInterfaceException {
        AEPacketRaw packet, tmp1, tmp2;
        int numEvents;
        int ts[];
        int addr[];

        sendVendorRequest(VR_DISABLE_AE);

        // wait a few ms for device to send last events
        try {
            Thread.currentThread();
			Thread.sleep(8);
        } catch (InterruptedException e) {
        }

        tmp1 = this.acquireAvailableEventsFromDriver(); // empty both buffers of the buffer pool
        tmp2 = this.acquireAvailableEventsFromDriver();

        numEvents = tmp1.getNumEvents() + tmp2.getNumEvents();

        addr = new int[numEvents];
        ts = new int[numEvents];

        System.arraycopy(tmp1.getAddresses(), 0, addr, 0, tmp1.getNumEvents());
        System.arraycopy(tmp2.getAddresses(), 0, addr, tmp1.getNumEvents(), tmp2.getNumEvents());

        System.arraycopy(tmp1.getTimestamps(), 0, ts, 0, tmp1.getNumEvents());
        System.arraycopy(tmp2.getTimestamps(), 0, ts, tmp1.getNumEvents(), tmp2.getNumEvents());

        packet = new AEPacketRaw(addr, ts);

        stopAEWriter();
        stopAEReader();
        inEndpointEnabled = false;
        outEndpointEnabled = false;
        log.fine("Monitoring and Sequencing stopped");

        return packet;
    }
//    /** starts sequencing of events, starts AEWriter thread and sends vendor request to device
//     @param events the events that should be sequenced, timestamps are realtive to last event,
//     inter spike intervals must not be bigger than 2^16
//     */
//    public void sendEventsToDevice(AEPacketRaw events) throws HardwareInterfaceException {
//        if(!isOpened){
//            open();
//        }
//
//        HardwareInterfaceException.clearException();
//
//        numOutEvents = events.getNumEvents();
//
//        if(numOutEvents==0){
//            estimateOutEventRate = computeEstimatedOutEventRate(null);
//            return;
//        }
//
//        estimateOutEventRate = computeEstimatedOutEventRate(events);
//
//        startAEWriter(events);
//
//        // wait a few ms to send the first pakets before starting the device
//        try{
//            Thread.currentThread().sleep(2);
//        } catch(InterruptedException e){}
//
//        sendVendorRequest(VR_ENABLE_AE_OUT);
//        outEndpointEnabled=true;
//    }

    /** Resets timestamps to start from zero.
     *
     */
    @Override
	synchronized public void resetTimestamps() {
        try {
            sendVendorRequest(this.VENDOR_REQUEST_RESET_TIMESTAMPS);

            if (this.getDID() != (short) 0x0001) // checking firmware version
            {
                this.getAeReader().resetTimestamps();
            //  log.info("old cpld firmware, no reset events");
            }
        //log.info("new cpld firmware, waiting for reset event to reset wrapAdd");

        } catch (HardwareInterfaceException e) {
            log.warning(e.toString());
        }
    }

    /** sets the serial number string, which is used to distinguish multiple devices and writes this string to the EEPROM
    @param name new serial number string
     */
    /** set the timestamp tick on the device
    @param mode
    0: Trigger: Host, Tick 1us;
    1: Trigger: Host, Tick 33.3ns;
    2: Trigger: Slave, Tick 1us;
    3: Trigger: Slave, Tick 33.3ns;
     */
    public void setOperationMode(int mode) throws HardwareInterfaceException {
        if ((mode < 0) || (mode > 3)) {
            log.warning("Invalid mode. Valid modes: \n0: Trigger: Host (Master), Tick 1us\n1: Trigger: Host (Master), Tick 33.3ns\n2: Trigger: Slave, Tick 1us\n3: Trigger: Slave, Tick 33.3ns");
            return;
        }
        sendVendorRequest(VR_OPERATION_MODE, (short) mode, (short) 0);

        // log.info("Timestamp Tick is now " + getOperationMode() +"us");
        tick = this.getOperationMode();
    }

    /**
     *  This method lets you configure how the USBAERmini2 handles events when the host computer is not fast enough to collect them.
     * If MissedEvents is disabled, the device will not handshake to the sender until it can write events to the FIFO, so it does not
     * lose events but blocks the sender.
     * If enabled, the device will discard events as long as it can not write to the FIFOs
     * (it will still pass them to the pass-through port though). The method {@link #getNumMissedEvents()} will return an estimate of
     * the number of events discarded.
     * @param yes wheter missed events counting should be enabled to unblock chain
     * @throws net.sf.jaer.hardwareinterface.HardwareInterfaceException
     */
    public void enableMissedEvents(boolean yes) throws HardwareInterfaceException {
        if (yes) {
            sendVendorRequest(VR_ENABLE_MISSED_EVENTS, (short) 1, (short) 0);
        } else {
            sendVendorRequest(VR_ENABLE_MISSED_EVENTS, (short) 0, (short) 0);
        }
    }


    /** gets the timestamp mode from the device, prints out if slave or master mode and returns the tick
    @return returns the timestamp tick on the device in us, either 1us or 0.0333us
     */
    public float getOperationMode() throws HardwareInterfaceException {

        if (!isOpen()) {
            open();
        }

        // make vendor request structure and populate it
        USBIO_CLASS_OR_VENDOR_REQUEST VendorRequest = new USBIO_CLASS_OR_VENDOR_REQUEST();
        int status;

        VendorRequest.Flags = UsbIoInterface.USBIO_SHORT_TRANSFER_OK;
        VendorRequest.Type = UsbIoInterface.RequestTypeVendor;
        VendorRequest.Recipient = UsbIoInterface.RecipientDevice;
        VendorRequest.RequestTypeReservedBits = 0;
        VendorRequest.Request = VR_OPERATION_MODE;
        VendorRequest.Index = 0;
        VendorRequest.Value = 0;

        USBIO_DATA_BUFFER dataBuffer = new USBIO_DATA_BUFFER(2);

        dataBuffer.setNumberOfBytesToTransfer(2);
        status = gUsbIo.classOrVendorInRequest(dataBuffer, VendorRequest);

        if (status != USBIO_ERR_SUCCESS) {
            throw new HardwareInterfaceException("Unable to get timestamp tick: " + UsbIo.errorText(status));
        }

        HardwareInterfaceException.clearException();

        if (dataBuffer.getBytesTransferred() == 0) {
            log.warning("Could not get timestamp tick, zero bytes transferred");
            return 0;
        }

        if (dataBuffer.Buffer()[1] == 0x00) {
            log.info("Trigger mode: Host (Master)");
            tick = 1f;
        } else if (dataBuffer.Buffer()[1] == 0x01) {
            log.info("Trigger mode: Host (Master)");
            tick = 0.2f;
        } else if (dataBuffer.Buffer()[1] == 0x02) {
            log.info("Trigger mode: Slave");
            tick = 1f;
        } else if (dataBuffer.Buffer()[1] == 0x03) {
            log.info("Trigger mode: Slave");
            tick = 0.2f;
        } else {
            log.warning("invalid operation mode: " + dataBuffer.Buffer()[1]);
            tick = 0;
        }

        return tick;
    }

    /** is this device acting as timestamp master
     * @return true if this device is acting as timestamp master device. note that this can
     * be the case even when the device is connected in slave mode, if the master is not active
     */
    public boolean isTimestampMaster() throws HardwareInterfaceException {
        if (!isOpen()) {
            open();
        }

        // make vendor request structure and populate it
        USBIO_CLASS_OR_VENDOR_REQUEST VendorRequest = new USBIO_CLASS_OR_VENDOR_REQUEST();
        int status;

        VendorRequest.Flags = UsbIoInterface.USBIO_SHORT_TRANSFER_OK;
        VendorRequest.Type = UsbIoInterface.RequestTypeVendor;
        VendorRequest.Recipient = UsbIoInterface.RecipientDevice;
        VendorRequest.RequestTypeReservedBits = 0;
        VendorRequest.Request = VR_IS_TIMESTAMP_MASTER;
        VendorRequest.Index = 0;
        VendorRequest.Value = 0;

        USBIO_DATA_BUFFER dataBuffer = new USBIO_DATA_BUFFER(2);

        dataBuffer.setNumberOfBytesToTransfer(2);
        status = gUsbIo.classOrVendorInRequest(dataBuffer, VendorRequest);

        if (status != USBIO_ERR_SUCCESS) {
            throw new HardwareInterfaceException("Unable to get timestamp status: " + UsbIo.errorText(status));
        }

        HardwareInterfaceException.clearException();

        if (dataBuffer.getBytesTransferred() == 0) {
            log.warning("Could not get status, zero bytes transferred");
            return false;
        }

        if (dataBuffer.Buffer()[1] == 0) {
            this.Master = false;
            return false;
        } else if (dataBuffer.Buffer()[1] == 1) {
            this.Master = true;
            return true;
        } else {
            log.warning("invalid value: " + dataBuffer.Buffer()[1]);
            return false;
        }

    }

    /** returns an estimation of the number of events that were missed due to full fifos
     * @return an estimation of the number of events that were missed due to full fifos
     */
    public long getNumMissedEvents() throws HardwareInterfaceException {
        long missedEvents;
        if (!isOpen()) {
            open();
        }

        // make vendor request structure and populate it
        USBIO_CLASS_OR_VENDOR_REQUEST VendorRequest = new USBIO_CLASS_OR_VENDOR_REQUEST();
        int status;

        VendorRequest.Flags = UsbIoInterface.USBIO_SHORT_TRANSFER_OK;
        VendorRequest.Type = UsbIoInterface.RequestTypeVendor;
        VendorRequest.Recipient = UsbIoInterface.RecipientDevice;
        VendorRequest.RequestTypeReservedBits = 0;
        VendorRequest.Request = VR_MISSED_EVENTS;
        VendorRequest.Index = 0;
        VendorRequest.Value = 0;

        USBIO_DATA_BUFFER dataBuffer = new USBIO_DATA_BUFFER(5);

        dataBuffer.setNumberOfBytesToTransfer(5);
        status = gUsbIo.classOrVendorInRequest(dataBuffer, VendorRequest);

        if (status != USBIO_ERR_SUCCESS) {
            throw new HardwareInterfaceException("Unable to get number of missed events: " + UsbIo.errorText(status));
        }

        HardwareInterfaceException.clearException();

        if (dataBuffer.getBytesTransferred() < 5) {
            log.warning("Could not get number of missed Events");
            return 0;
        }

        missedEvents = dataBuffer.Buffer()[1];
        missedEvents += (dataBuffer.Buffer()[2] << 8);
        missedEvents += (dataBuffer.Buffer()[3] << 16);
        missedEvents += (dataBuffer.Buffer()[4] << 24);

        // for (int i=1;i<=4;i++)
        //     log.info(dataBuffer.Buffer()[i]);

        return missedEvents * 16;
    }
    private boolean loopedSequencingEnabled = false;

    /** enables continuous sequencing, if enabled the AEWriter rewinds if it reaches the
     * end of the packet and restarts sending from the beginning. otherwise it just stops sequencing.
    @param set true to loop packet, false to sequence a single packet
     **/
    @Override
	public void setLoopedSequencingEnabled(boolean set) {
        this.loopedSequencingEnabled = set;
    }

    /**
    @return true if sequencing will loop back to start at end of data
     */
    @Override
	public boolean isLoopedSequencingEnabled() {
        return this.loopedSequencingEnabled;
    }

    /** AEWriter class, used to send events to the device
     */
    public class AEWriter extends UsbIoWriter {

        protected CypressFX2MonitorSequencer device;
        protected int index = 0; // which event is the next to send
        final int MAX_BUFFER_SIZE = 0xffff;
        final int MAX_NUMBER_OF_BUFFERS = 16; // maximum memory usage: 16*64k
        int[] timestamps;
        int[] addresses;
        int numOutEvents;

        /** constructor of the AEWriter class
        @param mon calling FX2 device
        @param events events to send to the device
         */
        public AEWriter(CypressFX2MonitorSequencer mon, AEPacketRaw events) {
            super();
            this.device = mon;

            checkSequencingQueue();
            sequencingQueue.add(events);

            numOutEvents = events.getNumEvents();
            addresses = events.getAddresses();
            timestamps = events.getTimestamps();

            log.info("Number of events to send: " + numOutEvents);


            freeBuffers();

            // allocate buffers, size depending on number of events to send
            if ((numOutEvents * 4) <= MAX_BUFFER_SIZE) { // one buffer is sufficient
                allocateBuffers(numOutEvents * 4, 1);
            } else if ((((numOutEvents * 4) / MAX_BUFFER_SIZE) + 1) > MAX_NUMBER_OF_BUFFERS) { // more space needed than max memory, so allocate max memory
                allocateBuffers(MAX_BUFFER_SIZE, MAX_NUMBER_OF_BUFFERS);
            } else {
                allocateBuffers(MAX_BUFFER_SIZE, ((numOutEvents * 4) / MAX_BUFFER_SIZE) + 1); // allocate as much buffers as necessary
            }
        }
        // checks to create queue if needed
        private void checkSequencingQueue() {
            if (sequencingQueue == null) {
                sequencingQueue = new LinkedBlockingQueue<AEPacketRaw>();
            }
        }

        /**
        Adds a packet to be sequenced to the sequencer output. Calling this automatically disables looping the sequenced data.
        @param packet the packet to add to the queue.
         */
        synchronized void pushPacketToSequence(AEPacketRaw packet) {
            checkSequencingQueue();
            if (isLoopedSequencingEnabled()) {
                log.warning("looped sequencing was enabled but a packet was added to the queue to sequence, disabling looping");
                setLoopedSequencingEnabled(false);
            }
            sequencingQueue.add(packet);
        }

        public int getPosition() {
            return index;
        }

        public int getNumEventsToSequence() {
            return this.numOutEvents;
        }

        synchronized private void popPacketToSequence() {
            AEPacketRaw packet = sequencingQueue.poll();
            if (packet == null) {
                return;
            }
            addresses = lastEventsAcquired.getAddresses();
            timestamps = lastEventsAcquired.getTimestamps();
        }
        //public void setPosition(int pos)
        //{
        //  index=pos;
        //}
        @Override
		public void startThread(int MaxIoErrorCount) {

            super.startThread(MaxIoErrorCount);
            try {
                T.setPriority(Thread.MAX_PRIORITY - 2); // very important that this thread have priority or the acquisition will stall on device side for substantial amounts of time!
            } catch (Exception e) {
                log.warning("could not set AEWriter thread priority");
            }
        //log.info(this+ " event seq worker-thread started");
        //T.setName("EventSequencer");
        // log.info("buffer pool event capture thread name is "+T.getName());
        }

        /** this function copies the addresses and timestamps to the UsbIoBuffer, it is called from the IO thread,
        the user doesn't have to call this function himself
         */
        @Override
		public synchronized void processBuffer(UsbIoBuf Buf) {
            if (index >= numOutEvents) // no more events to send
            {
                if (device.loopedSequencingEnabled) {
                    index = 0; // wrap around and start again from the beginning
                } else {
                    Buf.NumberOfBytesToTransfer = 0;
                    Buf.BytesTransferred = 0;
                    Buf.OperationFinished = true;
                    return;
                }
            }

            // log.info("Processing Buffer, current index: " + index);

            // set the number of bytes to transfer
            if (((numOutEvents - index) * 4) < Buf.Size) // the buffer size is bigger than needed for the events to send;
            {
                Buf.NumberOfBytesToTransfer = (numOutEvents - index) * 4;
            } else {
                if ((Buf.Size % 4) != 0) // the buffer size is not a multiplicative of four, but we only send multiplicatives of four
                {
                    Buf.NumberOfBytesToTransfer = (Buf.Size / 4) * 4;
                } else {
                    Buf.NumberOfBytesToTransfer = Buf.Size;
                }
            }

            //log.info("Numberofbytestotranser: " + Buf.NumberOfBytesToTransfer);

            Buf.BytesTransferred = 0;
            Buf.OperationFinished = false;
            if ((addresses == null) || (timestamps == null)) {
                log.warning("null addresses or timestamps, not sequencing");
                return;
            }

            for (int i = 0; i < Buf.NumberOfBytesToTransfer; i += 4) {
                int add = addresses[index];
                Buf.BufferMem[i] = (byte) (0x00FF & add);
                Buf.BufferMem[i + 1] = (byte) ((0xFF00 & add) >> 8);
                int ts = timestamps[index];
                Buf.BufferMem[i + 2] = (byte) (0x00FF & ts);
                Buf.BufferMem[i + 3] = (byte) ((0xFF00 & ts) >> 8);
                index++;
            }
        }

        @Override
		public void bufErrorHandler(UsbIoBuf Buf) {
            if (Buf.Status != USBIO_ERR_SUCCESS) {
                // print error
                // suppress CANCELED because it is caused by ABORT_PIPE
                if (Buf.Status != USBIO_ERR_CANCELED) {
                    log.warning("Buf Error: " + UsbIo.errorText(Buf.Status));
                }
            }
        }
        // virtual function, called in the context of worker thread
        @Override
		public void onThreadExit() {
            log.fine("AEWriter Worker-thread terminated.");
        }
    } // AEWriter

    /** writes USBAERmini2 firmware to EEPROM
    can for example be used from matlab */
    public void writeMonitorSequencerFirmware() {
        try {
            byte[] fw;
            if (this.getPID() == PID_USB2AERmapper) {
                fw = this.loadBinaryFirmwareFile(CypressFX2.FIRMWARE_FILENAME_MAPPER_IIC);
            } else {
                fw = this.loadBinaryFirmwareFile(CypressFX2.FIRMWARE_FILENAME_MONITOR_SEQUENCER_IIC);
            }
            this.writeEEPROM(0, fw);
            log.info("New firmware written to EEPROM");
        } catch (Exception e) {
            log.warning("couldn't write firmware: "+e.toString());
        }
    }

        /** Updates the firmware by downloading to the board's EEPROM */
    @Override
	public void updateFirmware() throws HardwareInterfaceException {
        if (this.getDID()<2)
        {
            throw new HardwareInterfaceException("This device may not support automatic firmware update. Please update manually!");
        }
        this.writeCPLDfirmware(CPLD_FIRMWARE_MONSEQ);
        log.info("New firmware written to CPLD");
        byte[] fw;
        try {
            fw = this.loadBinaryFirmwareFile(CypressFX2.FIRMWARE_FILENAME_MONITOR_SEQUENCER_JTAG_IIC);
        } catch (java.io.IOException e) {
            throw new HardwareInterfaceException("Could not load firmware file "+CypressFX2.FIRMWARE_FILENAME_MONITOR_SEQUENCER_JTAG_IIC+": "+e.toString());
        }
        this.writeEEPROM(0, fw);
        log.info("New firmware written to EEPROM");
    }

    @Override
    public int getVersion()
    {
        return getDID();
    }

    public void writeMonitorSequencerJTAGFirmware() {
        try {
            byte[] fw;
            if (this.getPID() == PID_USB2AERmapper) {
                fw = this.loadBinaryFirmwareFile(CypressFX2.FIRMWARE_FILENAME_MAPPER_IIC);
            } else {
                fw = this.loadBinaryFirmwareFile(CypressFX2.FIRMWARE_FILENAME_MONITOR_SEQUENCER_JTAG_IIC);
            }
            this.writeEEPROM(0, fw);
            log.info("New firmware written to EEPROM");
        } catch (Exception e) {
            log.warning(e.toString());
        }
    }

    /**
    Pushes a packet to be sequenced to the sequencer output. Calling this automatically disables looping the sequenced data.
    @param packet the packet to add to the tail of the queue.
     */
    @Override
	public void offerPacketToSequencer(AEPacketRaw packet) {
        aeWriter.pushPacketToSequence(packet);
    }
//    AEMapper mapper=new AbstractAEMapper(){
//        int[] mapping=new int[1];
//        public int[] getMapping(int src){
//            mapping[0]=src;
//            return mapping;
//        }
//    };
//
//    public int[] getMapping(int src) {
//        return mapper.getMapping(src);
//    }
//
//    public void setMappingPassThrough(boolean yes) {
//        mapper.setMappingPassThrough(yes);
//    }
//
//    public void setMappingEnabled(boolean yes) {
//        mapper.setMappingEnabled(yes);
//    }
//
//    public boolean isMappingEnabled(){
//        return mapper.isMappingEnabled();
//    }
//
//    public boolean isMappingPassThrough(){
//        return mapper.isMappingPassThrough();
//    }
//
    @Override
	public Collection<AEMapper> getAEMappers() {
        return null;
    }

    /**
     * Starts reader buffer pool thread and enables in endpoints for AEs. This method is overridden to construct
    our own reader with its translateEvents method
     */
    @Override
    public void startAEReader() throws HardwareInterfaceException {  // raphael: changed from private to protected, because i need to access this method
        setAeReader(new MonSeqAEReader(this));
        allocateAEBuffers();
        getAeReader().startThread(3); // arg is number of errors before giving up
    }

    /** This reader understands the format of raw USB data and translates to the AEPacketRaw */
    public class MonSeqAEReader extends CypressFX2.AEReader {

        public MonSeqAEReader(CypressFX2 cypress) throws HardwareInterfaceException {
            super(cypress);
        }

        /** This method overrides the super implementation to
         * translate events from several types of interfaces: the
         * USBAERMini2 with a programmed serial number.
         *
         * @param b
         */
        @Override
		protected void translateEvents(UsbIoBuf b) {
            if ((monitor.getPID() == PID_USBAERmini2) && (monitor.getDID() > 0)) {
                translateEventsWithCPLDEventCode(b);
//                        CypressFX2MonitorSequencer seq=(CypressFX2MonitorSequencer)(CypressFX2.this);
            //                    seq.mapPacket(captureBufferPool.active());

            } else {
                translateEventsFromOriginalUSB2AERmini2WithOriginalFirmware(b);
            }
        }

        /** Method that translates the UsbIoBuffer when a board that has a CPLD to timestamp events and that uses the CypressFX2 in slave
         * FIFO mode, such as the USBAERmini2 board or StereoRetinaBoard, is used.
         * <p>
         *On these boards, the msb of the timestamp is used to signal a wrap (the actual timestamp is only 14 bits).
         * The timestamp is also used to signal a timestamp reset
         *
         * The wrapAdd is incremented when an empty event is received which has the timestamp bit 15
         * set to one.
         * The timestamp is reset when an event is received which has the timestamp bit 14 set.
         *<p>
         * Therefore for a valid event only 14 bits of the 16 transmitted timestamp bits are valid, bits 14 and 15
         * are the status bits. overflow happens every 16 ms.
         * This way, no roll-overs go by undetected, and the problem of invalid wraps doesn't arise.
         *@param b the data buffer
         *@see #translateEvents
         */
        protected void translateEventsWithCPLDEventCode(UsbIoBuf b) {

//            System.out.println("buf has "+b.BytesTransferred+" bytes");
            synchronized (aePacketRawPool) {
                AEPacketRaw buffer = aePacketRawPool.writeBuffer();
                //    if(buffer.overrunOccuredFlag) return;  // don't bother if there's already an overrun, consumer must get the events to clear this flag before there is more room for new events
                int shortts;
                int NumberOfWrapEvents;
                NumberOfWrapEvents = 0;

                byte[] aeBuffer = b.BufferMem;
                //            byte lsb,msb;
                int bytesSent = b.BytesTransferred;
                if ((bytesSent % 4) != 0) {
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

//                    // debug stuck bit
//                    for(int z=0;z<4;z++){
//                        System.out.println(aeBuffer[i+z]&(0x1<<5));
//                    }

                    if ((aeBuffer[i + 3] & 0x80) == 0x80) { // timestamp bit 16 is one -> wrap
                        // now we need to increment the wrapAdd

                        wrapAdd += 0x4000L; //uses only 14 bit timestamps

                        //System.out.println("received wrap event, index:" + eventCounter + " wrapAdd: "+ wrapAdd);
                        NumberOfWrapEvents++;
                    } else if ((aeBuffer[i + 3] & 0x40) == 0x40) { // timestamp bit 15 is one -> wrapAdd reset
                        // this firmware version uses reset events to reset timestamps
                        this.resetTimestamps();
                    // log.info("got reset event, timestamp " + (0xffff&((short)aeBuffer[i]&0xff | ((short)aeBuffer[i+1]&0xff)<<8)));
                    } else if ((eventCounter > (aeBufferSize - 1)) || (buffer.overrunOccuredFlag)) { // just do nothing, throw away events
                        buffer.overrunOccuredFlag = true;
                    } else {
                        // address is LSB MSB
                        addresses[eventCounter] = (aeBuffer[i] & 0xFF) | ((aeBuffer[i + 1] & 0xFF) << 8);

                        // same for timestamp, LSB MSB
                        shortts = ((aeBuffer[i + 2] & 0xff) | ((aeBuffer[i + 3] & 0xff) << 8)); // this is 15 bit value of timestamp in TICK_US tick

                        timestamps[eventCounter] = TICK_US * (shortts + wrapAdd); //*TICK_US; //add in the wrap offset and convert to 1us tick
                        // this is USB2AERmini2 or StereoRetina board which have 1us timestamp tick
                        eventCounter++;
                        buffer.setNumEvents(eventCounter);
                    }
                } // end for

                // write capture size
                buffer.lastCaptureLength = eventCounter - buffer.lastCaptureIndex;
                    buffer.systemModificationTimeNs = System.nanoTime();

            // if (NumberOfWrapEvents!=0) {
            //System.out.println("Number of wrap events received: "+ NumberOfWrapEvents);
            //}
            //System.out.println("wrapAdd : "+ wrapAdd);
            } // sync on aePacketRawPool
        }

        /** Method that translates the UsbIoBuffer when a board that has a CPLD with old firmware to timestamp events and that uses the CypressFX2 in slave
         * FIFO mode, such as the USBAERmini2 board, is used.
         * <p>
         *On these boards, the msb of the timestamp is used to signal a wrap (the actual timestamp is only 15 bits).
         *
         * The wrapAdd is incremented when an emtpy event is received which has the timestamp bit 15
         * set to one.
         *<p>
         * Therefore for a valid event only 15 bits of the 16 transmitted timestamp bits are valid, bit 15
         * is the status bit. overflow happens every 32 ms.
         * This way, no roll overs go by undetected, and the problem of invalid wraps doesn't arise.
         *@param b the data buffer
         *@see #translateEvents
         */
        protected void translateEventsFromOriginalUSB2AERmini2WithOriginalFirmware(UsbIoBuf b) {

//            System.out.println("buf has "+b.BytesTransferred+" bytes");
            synchronized (aePacketRawPool) {
                AEPacketRaw buffer = aePacketRawPool.writeBuffer();
//                if(buffer.overrunOccuredFlag) return;  // don't bother if there's already an overrun, consumer must get the events to clear this flag before there is more room for new events
                int shortts;
                int NumberOfWrapEvents;
                NumberOfWrapEvents = 0;

                byte[] aeBuffer = b.BufferMem;
                //            byte lsb,msb;
                int bytesSent = b.BytesTransferred;
                if ((bytesSent % 4) != 0) {
//                System.out.println("CypressFX2.AEReader.translateEvents(): warning: "+bytesSent+" bytes sent, which is not multiple of 4");
                    bytesSent = (bytesSent / 4) * 4; // truncate off any extra part-event
                }

                int[] addresses = buffer.getAddresses();
                int[] timestamps = buffer.getTimestamps();

                // write the start of the packet
                buffer.lastCaptureIndex = eventCounter;

                for (int i = 0; i < bytesSent; i += 4) {
//                    if(eventCounter>aeBufferSize-1){
//                        buffer.overrunOccuredFlag=true;
////                                        log.warning("overrun");
//                        return; // return, output event buffer is full and we cannot add any more events to it.
//                        //no more events will be translated until the existing events have been consumed by acquireAvailableEventsFromDriver
//                    }

                    if ((aeBuffer[i + 3] & 0x80) == 0x80) { // timestamp bit 16 is one -> wrap
                        // now we need to increment the wrapAdd

                        wrapAdd += 0x8000L;	// This is 0x7FFF +1; if we wrapped then increment wrap value by 2^15

                        //System.out.println("received wrap event, index:" + eventCounter + " wrapAdd: "+ wrapAdd);
                        NumberOfWrapEvents++;
                    } else if ((eventCounter > (aeBufferSize - 1)) || (buffer.overrunOccuredFlag)) { // just do nothing, throw away events
                        buffer.overrunOccuredFlag = true;
                    } else {
                        // address is LSB MSB
                        addresses[eventCounter] = (aeBuffer[i] & 0xFF) | ((aeBuffer[i + 1] & 0xFF) << 8);

                        // same for timestamp, LSB MSB
                        shortts = ((aeBuffer[i + 2] & 0xff) | ((aeBuffer[i + 3] & 0xff) << 8)); // this is 15 bit value of timestamp in TICK_US tick

                        timestamps[eventCounter] = TICK_US * (shortts + wrapAdd); //*TICK_US; //add in the wrap offset and convert to 1us tick
                        // this is USB2AERmini2 or StereoRetina board which have 1us timestamp tick
                        eventCounter++;
                        buffer.setNumEvents(eventCounter);
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
    }

    @Override
	public void startSequencing(AEPacketRaw eventsToSend) throws HardwareInterfaceException {
        startSequencing(eventsToSend);
    }

    @Override
	public void stopSequencing() throws HardwareInterfaceException {
        stopMonitoringSequencing();
    }
}

