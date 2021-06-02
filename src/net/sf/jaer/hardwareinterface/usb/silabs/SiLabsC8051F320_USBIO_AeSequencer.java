package net.sf.jaer.hardwareinterface.usb.silabs;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.aesequencer.AESequencerInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.USBInterface;
import net.sf.jaer.hardwareinterface.usb.UsbIoUtilities;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2;
import net.sf.jaer.util.HexString;
import de.thesycon.usbio.PnPNotify;
import de.thesycon.usbio.PnPNotifyInterface;
import de.thesycon.usbio.UsbIo;
import de.thesycon.usbio.UsbIoBuf;
import de.thesycon.usbio.UsbIoErrorCodes;
import de.thesycon.usbio.UsbIoInterface;
import de.thesycon.usbio.UsbIoWriter;
import de.thesycon.usbio.structs.USBIO_CONFIGURATION_INFO;
import de.thesycon.usbio.structs.USBIO_PIPE_PARAMETERS;
import de.thesycon.usbio.structs.USBIO_SET_CONFIGURATION;
import de.thesycon.usbio.structs.USB_DEVICE_DESCRIPTOR;
import de.thesycon.usbio.structs.USB_STRING_DESCRIPTOR;

/**
 * The USB simplemonitor board is used to sequence out events using this class and appropriate firmware on the board.
 *
 * <p>
 * Servo motor controller using USBIO driver access to SiLabsC8051F320 device. To prevent blocking on the thread controlling the
 *servo, this class starts a consumer thread that communicates with the USB interface. The producer (the user) communicates with the
 *consumer thread using an ArrayBlockingQueue. Therefore servo commands never produce hardware exceptions; these are caught in the consumer
 *thread by closing the device, which should be reopened on the next command.
 * <p>
 * This class goes with the USB simplemonitor board.
 *
 * @author tobi
 */
public class SiLabsC8051F320_USBIO_AeSequencer implements UsbIoErrorCodes, PnPNotifyInterface, AESequencerInterface, USBInterface {

    static Logger log = Logger.getLogger("SiLabsC8051F320_USBIO_ServoController");
    /** driver guid (Globally unique ID, for this USB driver instance */
    public final static String GUID = CypressFX2.GUID; // "{7794C79A-40A7-4a6c-8A29-DA141C20D78C}"; // this GUID is for the devices in driverUSBIO_Tmpdiff128_USBAERmini2
    /** The vendor ID */
    static public final short VID = USBInterface.VID_THESYCON;
    static public final short PID = (short) 0x8410; // USBInterface.PID_THESYCON_START+10;
    final static short CONFIG_INDEX = 0;
    final static short CONFIG_NB_OF_INTERFACES = 1;
    final static short CONFIG_INTERFACE = 0;
    final static short CONFIG_ALT_SETTING = 0;
    final static int CONFIG_TRAN_SIZE = 64;    // out endpoint for servo commands
    final static int ENDPOINT_OUT = 0x02;
    /** length of endpoint, ideally this value should be obtained from the pipe bound to the endpoint but we know what it is for this
     * device. It is set to 16 bytes to minimize transmission time. At 12 Mbps, 16 bytes+header (13 bytes)=140 bits requires about 30 us to transmit.
     */
    public final static int ENDPOINT_OUT_LENGTH = 64;
    public final static int HOST_BUFFER_LENGTH = 10000 * 8; // TODO
    PnPNotify pnp = null;
    private boolean isOpened;
    /** number of packets that can be queued up. It is set to a small number so that comands do not pile up. If the queue
     * is full when a command is given, then the old commands are discarded so that the latest command is next to be processed.
     * Note that this policy can have drawbacks - if commands are sent to different servos successively, then new commands can wipe out commands
     * to older commands to set other servos to some position.
     */
    public static final int PACKET_QUEUE_LENGTH = 1;
    AePacketWriter aePacketWriter = null; // this worker thread asynchronously writes to device
    private volatile ArrayBlockingQueue<AEPacketRaw> packetQueue; // this queue is used for holding packets that must be sent out.
    /** the device number, out of all potential compatible devices that could be opened */
    protected int interfaceNumber = 0;
    volatile private int numEventsToSend = 0; // volatile because shared between producer/consumer threads
    volatile private int numEventsSent = 0;

    /**
     * Creates a new instance of SiLabsC8051F320_USBIO_ServoController using device 0 - the first
     * device in the list.
     */
    public SiLabsC8051F320_USBIO_AeSequencer() {
        interfaceNumber = 0;
        UsbIoUtilities.enablePnPNotification(this, GUID);
        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
			public void run() {
                if (isOpen()) {
                    close();
                }
            }
        });
        packetQueue = new ArrayBlockingQueue<AEPacketRaw>(PACKET_QUEUE_LENGTH);
    }

    /** Creates a new instance of USBAEMonitor. Note that it is possible to construct several instances
     * and use each of them to open and read from the same device.
     *@param devNumber the desired device number, in range returned by CypressFX2Factory.getNumInterfacesAvailable
     */
    public SiLabsC8051F320_USBIO_AeSequencer(int devNumber) {
        this();
        this.interfaceNumber = devNumber;
    }

    @Override
	public void onAdd() {
        log.info("SiLabsC8051F320_USBIO_ServoController: device added");
    }

    @Override
	public void onRemove() {
        log.info("SiLabsC8051F320_USBIO_ServoController: device removed");
        close();
    }

    /** Closes the device. Never throws an exception.
     */
    @Override
	public void close() {
        if (!isOpened) {
            log.warning("close(): not open");
            return;
        }

        if (aePacketWriter != null) {
            try {
                Thread.currentThread();
				Thread.sleep(10);
            } catch (InterruptedException e) {
            }
            aePacketWriter.shutdownThread();
        }
        aePacketWriter.close(); // unbinds pipes too
        if (gUsbIo != null) {
            gUsbIo.close();
        }
        UsbIo.destroyDeviceList(gDevList);
        log.info("device closed");
        isOpened = false;

    }
    /** the first USB string descriptor (Vendor name) (if available) */
    protected USB_STRING_DESCRIPTOR stringDescriptor1 = new USB_STRING_DESCRIPTOR();
    /** the second USB string descriptor (Product name) (if available) */
    protected USB_STRING_DESCRIPTOR stringDescriptor2 = new USB_STRING_DESCRIPTOR();
    /** the third USB string descriptor (Serial number) (if available) */
    protected USB_STRING_DESCRIPTOR stringDescriptor3 = new USB_STRING_DESCRIPTOR();
    protected int numberOfStringDescriptors = 2;

    /** returns number of string descriptors
     * @return number of string descriptors: 2 for TmpDiff128, 3 for MonitorSequencer */
    public int getNumberOfStringDescriptors() {
        return numberOfStringDescriptors;
    }
    /** the USBIO device descriptor */
    protected USB_DEVICE_DESCRIPTOR deviceDescriptor = new USB_DEVICE_DESCRIPTOR();
    /** the UsbIo interface to the device. This is assigned on construction by the
     * factory which uses it to open the device. here is used for all USBIO access
     * to the device*/
    protected UsbIo gUsbIo = null;
    /** the devlist handle for USBIO */
    protected long gDevList; // 'handle' (an integer) to an internal device list static to UsbIo

    /** checks if device has a string identifier that is a non-empty string
     *@return false if not, true if there is one
     */
    protected boolean hasStringIdentifier() {
        // get string descriptor
        int status = gUsbIo.getStringDescriptor(stringDescriptor1, (byte) 1, 0);
        if (status != USBIO_ERR_SUCCESS) {
            return false;
        } else {
            if (stringDescriptor1.Str.length() > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * This method does the hard work of opening the device, downloading the firmware, making sure everything is OK.
     * This method is synchronized to prevent multiple threads from trying to open at the same time, e.g. a GUI thread and the main thread.
     *
     * Opening the device after it has already been opened has no effect.
     *
     * @see #close
     *@throws HardwareInterfaceException if there is a problem. Diagnostics are printed to stderr.
     */
    @Override
	public void open() throws HardwareInterfaceException {
        if (!UsbIoUtilities.isLibraryLoaded()) {
            return;        //device has already been UsbIo Opened by now, in factory
            // opens the USBIOInterface device, configures it, binds a reader thread with buffer pool to read from the device and starts the thread reading events.
            // we got a UsbIo object when enumerating all devices and we also made a device list. the device has already been
            // opened from the UsbIo viewpoint, but it still needs firmware download, setting up pipes, etc.
        }
        if (isOpened) {
            log.warning("already opened interface and setup device");
            return;
        }

        int status;

        gUsbIo = new UsbIo();
        gDevList = UsbIo.createDeviceList(GUID);
        status = gUsbIo.open(0, gDevList, GUID);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("can't open USB device: " + UsbIo.errorText(status));
        }

        // get device descriptor
        status = gUsbIo.getDeviceDescriptor(deviceDescriptor);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("getDeviceDescriptor: " + UsbIo.errorText(status));
        } else {
            log.info("getDeviceDescriptor: Vendor ID (VID) " + HexString.toString((short) deviceDescriptor.idVendor) + " Product ID (PID) " + HexString.toString((short) deviceDescriptor.idProduct));
        }

        // set configuration -- must do this BEFORE downloading firmware!
        USBIO_SET_CONFIGURATION Conf = new USBIO_SET_CONFIGURATION();
        Conf.ConfigurationIndex = CONFIG_INDEX;
        Conf.NbOfInterfaces = CONFIG_NB_OF_INTERFACES;
        Conf.InterfaceList[0].InterfaceIndex = CONFIG_INTERFACE;
        Conf.InterfaceList[0].AlternateSettingIndex = CONFIG_ALT_SETTING;
        Conf.InterfaceList[0].MaximumTransferSize = CONFIG_TRAN_SIZE;
        status = gUsbIo.setConfiguration(Conf);
        if (status != USBIO_ERR_SUCCESS) {
//            gUsbIo.destroyDeviceList(gDevList);
            //   if (status !=0xE0001005)
            log.warning("setting configuration: " + UsbIo.errorText(status));
        }

        // get device descriptor
        status = gUsbIo.getDeviceDescriptor(deviceDescriptor);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("getDeviceDescriptor: " + UsbIo.errorText(status));
        } else {
            log.info("getDeviceDescriptor: Vendor ID (VID) " + HexString.toString((short) deviceDescriptor.idVendor) + " Product ID (PID) " + HexString.toString((short) deviceDescriptor.idProduct));
        }

        if (deviceDescriptor.iSerialNumber != 0) {
            this.numberOfStringDescriptors = 3;        // get string descriptor
        }
        status = gUsbIo.getStringDescriptor(stringDescriptor1, (byte) 1, 0);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("getStringDescriptor: " + UsbIo.errorText(status));
        } else {
            log.info("getStringDescriptor 1: " + stringDescriptor1.Str);
        }

        // get string descriptor
        status = gUsbIo.getStringDescriptor(stringDescriptor2, (byte) 2, 0);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("getStringDescriptor: " + UsbIo.errorText(status));
        } else {
            log.info("getStringDescriptor 2: " + stringDescriptor2.Str);
        }

        if (this.numberOfStringDescriptors == 3) {
            // get serial number string descriptor
            status = gUsbIo.getStringDescriptor(stringDescriptor3, (byte) 3, 0);
            if (status != USBIO_ERR_SUCCESS) {
                UsbIo.destroyDeviceList(gDevList);
                throw new HardwareInterfaceException("getStringDescriptor: " + UsbIo.errorText(status));
            } else {
                log.info("getStringDescriptor 3: " + stringDescriptor3.Str);
            }
        }

        // get outPipe information and extract the FIFO size
        USBIO_CONFIGURATION_INFO configurationInfo = new USBIO_CONFIGURATION_INFO();
        status = gUsbIo.getConfigurationInfo(configurationInfo);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("getConfigurationInfo: " + UsbIo.errorText(status));
        }

        if (configurationInfo.NbOfPipes == 0) {
//            gUsbIo.cyclePort();
            throw new HardwareInterfaceException("didn't find any pipes to bind to");
        }

        aePacketWriter = new AePacketWriter();
        status = aePacketWriter.bind(0, (byte) ENDPOINT_OUT, gDevList, GUID);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("can't bind command writer to endpoint: " + UsbIo.errorText(status));
        }
        USBIO_PIPE_PARAMETERS pipeParams = new USBIO_PIPE_PARAMETERS();
        pipeParams.Flags = UsbIoInterface.USBIO_SHORT_TRANSFER_OK;
        status = aePacketWriter.setPipeParameters(pipeParams);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("startAEWriter: can't set pipe parameters: " + UsbIo.errorText(status));
        }

        aePacketWriter.startThread(3);
        isOpened = true;
    }

    /** return the string USB descriptors for the device
     *@return String[] of length 2 of USB descriptor strings.
     */
    @Override
	public String[] getStringDescriptors() {
        if (stringDescriptor1 == null) {
            log.warning("USBAEMonitor: getStringDescriptors called but device has not been opened");
            String[] s = new String[numberOfStringDescriptors];
            for (int i = 0; i < numberOfStringDescriptors; i++) {
                s[i] = "";
            }
            return s;
        }
        String[] s = new String[numberOfStringDescriptors];
        s[0] = stringDescriptor1.Str;
        s[1] = stringDescriptor2.Str;
        if (numberOfStringDescriptors == 3) {
            s[2] = stringDescriptor3.Str;
        }
        return s;
    }

    @Override
	public short getVID_THESYCON_FX2_CPLD() {
        if (deviceDescriptor == null) {
            log.warning("USBAEMonitor: getVIDPID called but device has not been opened");
            return 0;
        }
        // int[] n=new int[2]; n is never used
        return (short) deviceDescriptor.idVendor;
    }

    @Override
	public short getPID() {
        if (deviceDescriptor == null) {
            log.warning("USBAEMonitor: getVIDPID called but device has not been opened");
            return 0;
        }
        return (short) deviceDescriptor.idProduct;
    }

    /** @return bcdDevice (the binary coded decimel device version */
    @Override
	public short getDID() { // this is not part of USB spec in device descriptor.
        return (short) deviceDescriptor.bcdDevice;
    }

    /** reports if interface is {@link #open}.
     * @return true if already open
     */
    @Override
	public boolean isOpen() {
        return isOpened;
    }

    @Override
	public String getTypeName() {
        return "AESequencer";
    }

    /** Submits the packet to the writer thread queue that sends them to the device
    @param packet the packet, which consists of EventRaw's to be sent to the device with absolute timestamps
     */
    protected void submitPacket(AEPacketRaw packet) {
        if (packet == null) {
            log.warning("null packet submitted to queue");
            return;
        }
        try {
            if (!packetQueue.offer(packet, 100, TimeUnit.MILLISECONDS)) { // if queue is full, just clear it and replace with latest command
                log.warning("AEPacketRaw queue stalled, packet discarded");
                return;
            }
            numEventsToSend += packet.getNumEvents();
            Thread.currentThread();
			Thread.sleep(20); //yield(); // let writer thread get it and submit a write
        } catch (InterruptedException e) {
        }
    }

    /** This thread actually talks to the hardware */
    private class AePacketWriter extends UsbIoWriter {

        private int index = 0;  // next event to write from the current packet
        private AEPacketRaw packet = null; // the current packet being written
        private int lastTimestamp = 0;  // last timestamp written, used to compute dt's to sequence
        private int[] timestamps;
        private int[] addresses;
        private int numOutEvents;

        // overridden to change priority
        @Override
		public void startThread(int MaxIoErrorCount) {
            allocateBuffers(ENDPOINT_OUT_LENGTH, 2);
            if (T == null) {
                MaxErrorCount = MaxIoErrorCount;
                T = new Thread(this);
                T.setPriority(Thread.MAX_PRIORITY); // very important that this thread have priority or the acquisition will stall on device side for substantial amounts of time!
                T.setName("AEPacketWriter");
                T.start();
            }
        }

        /**
         * waits and takes commands from the queue and submits them to the device. Called automatically by the writer.
         */
        @Override
		public void processBuffer(UsbIoBuf buf) {
            // we may be called here while we are still writing a previous packet that was submitted.
            // in that case we simply continue with the existing packet.
            // once we finish a packet, we get a new one from the queue.
            // if the data left from a packet is too large for the UsbIoBuf we only
            // write the buffer full and next time around we continue with the same packet.
            // or we can get a small packet or a small number of events - in that case we write a short packet.


            if ((packet == null) || (index >= packet.getNumEvents())) {
                // either starting or done with previous packet
                try {
                    packet = packetQueue.take();  // wait forever until there is some data
                    index = 0; // reset counter
                    numOutEvents = packet.getNumEvents();
                    addresses = packet.getAddresses();
                    timestamps = packet.getTimestamps();
                } catch (InterruptedException e) {
                    log.info("packet queue wait interrupted");
                    T.interrupt(); // important to call again so that isInterrupted in run loop see that thread should terminate
                }
                if (packet == null) {
                    return;
                }
            }

            // we have a packet and we
            buf.NumberOfBytesToTransfer = packet.getNumEvents() * 8; // TODO must send full buffer because that is what controller expects for interrupt transfers
            buf.OperationFinished = false; // setting true will finish all transfers and end writer thread
            buf.BytesTransferred = 0;

//                       if (index >= numOutEvents) // no more events to send
//            {
//                if (device.loopedSequencingEnabled) {
//                    index = 0; // wrap around and start again from the beginning
//                } else {
//                    Buf.NumberOfBytesToTransfer = 0;
//                    Buf.BytesTransferred = 0;
//                    Buf.OperationFinished = true;
//                    return;
//                }
//            }
//
//            // log.info("Processing Buffer, current index: " + index);
//
            // set the number of bytes to transfer
            if (((numOutEvents - index) * 4) < buf.Size) // the buffer size is bigger than needed for the events to send;
            {
                buf.NumberOfBytesToTransfer = (numOutEvents - index) * 4;
            } else {
                if ((buf.Size % 4) != 0) // the buffer size is not a multiplicative of four, but we only send multiplicatives of four
                {
                    buf.NumberOfBytesToTransfer = (buf.Size / 4) * 4;
                } else {
                    buf.NumberOfBytesToTransfer = buf.Size;
                }
            }

            //log.info("Numberofbytestotranser: " + buf.NumberOfBytesToTransfer);

            buf.BytesTransferred = 0;
            buf.OperationFinished = false;
            if ((addresses == null) || (timestamps == null)) {
                log.warning("null addresses or timestamps, not sequencing");
                return;
            }

            for (int i = 0; i < buf.NumberOfBytesToTransfer; i += 4) {
                int add = addresses[index];
                buf.BufferMem[i] = (byte) (0x00FF & add);
                buf.BufferMem[i + 1] = (byte) ((0xFF00 & add) >> 8);
                int ts = timestamps[index];
                buf.BufferMem[i + 2] = (byte) (0x00FF & ts);
                buf.BufferMem[i + 3] = (byte) ((0xFF00 & ts) >> 8);
                index++;
                numEventsToSend--;
                numEventsSent++;
            }

            log.info("processBuffer: numEventsSent=" + numEventsSent);

        }

        @Override
		public void bufErrorHandler(UsbIoBuf usbIoBuf) {
            log.warning(UsbIo.errorText(usbIoBuf.Status));
        }

        @Override
		public void onThreadExit() {
            log.info("sequencer writer done");
        }
    }

    protected void checkWtiterThread() {
        try {
            if (!isOpen()) {
                open();
            }
        } catch (HardwareInterfaceException ex) {
            log.warning(ex.toString());
        }
    }

    @Override
	public int getNumEventsSent() {
        return numEventsSent;
    }

    @Override
	public int getNumEventsToSend() {
        return numEventsToSend;
    }

    @Override
	public void resetTimestamps() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
	public boolean isEventSequencingEnabled() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
	public int getMaxCapacity() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
	public int getEstimatedOutEventRate() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
	public int getTimestampTickUs() {
        return AEConstants.TICK_DEFAULT_US;
    }

    @Override
	public void offerPacketToSequencer(AEPacketRaw packet) {
        checkWtiterThread();
        submitPacket(packet);
    }

    @Override
	public void setLoopedSequencingEnabled(boolean set) {
        log.warning("not supported yet"); // TODO
    }

    @Override
	public boolean isLoopedSequencingEnabled() {
        return false;
    }

    @Override
	public void startSequencing(AEPacketRaw eventsToSend) throws HardwareInterfaceException {
        log.info("Starting sequencing of " + eventsToSend + " on interface " + this);
        offerPacketToSequencer(eventsToSend);

    }

    @Override
	public void stopSequencing() throws HardwareInterfaceException {
        log.info("stopping sequencing");
        if (aePacketWriter != null) {
            aePacketWriter.shutdownThread();
        } else {
            log.warning("aePacketWriter was null when stopSequencing was called");
        }
    }
}
