/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.hardwareinterface.usb.silabs;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import net.sf.jaer.aemonitor.AEListener;
import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.aemonitor.AEPacketRawPool;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import net.sf.jaer.biasgen.IPot;
import net.sf.jaer.biasgen.IPotArray;
import net.sf.jaer.biasgen.Pot;
import net.sf.jaer.biasgen.PotArray;
import net.sf.jaer.biasgen.VDAC.VPot;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.ReaderBufferControl;
import net.sf.jaer.hardwareinterface.usb.USBInterface;
import net.sf.jaer.hardwareinterface.usb.UsbIoUtilities;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2;
import net.sf.jaer.util.HexString;
import ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1bHardwareInterface.AEReader;
import de.thesycon.usbio.PnPNotify;
import de.thesycon.usbio.UsbIo;
import de.thesycon.usbio.UsbIoBuf;
import de.thesycon.usbio.UsbIoErrorCodes;
import de.thesycon.usbio.UsbIoInterface;
import de.thesycon.usbio.UsbIoPipe;
import de.thesycon.usbio.UsbIoReader;
import de.thesycon.usbio.structs.USBIO_CONFIGURATION_INFO;
import de.thesycon.usbio.structs.USBIO_DATA_BUFFER;
import de.thesycon.usbio.structs.USBIO_PIPE_PARAMETERS;
import de.thesycon.usbio.structs.USBIO_SET_CONFIGURATION;
import de.thesycon.usbio.structs.USB_DEVICE_DESCRIPTOR;
import de.thesycon.usbio.structs.USB_STRING_DESCRIPTOR;

/**
 * USBIO interface to SiLabs C8051F3x based AE monitor and DVS128 chip; used for DVS128_PAER board.
 * @author tobi
 */
public class SiLabsC8051F320_USBIO_DVS128 extends UsbIoReader implements
        UsbIoErrorCodes, USBInterface, BiasgenHardwareInterface, AEMonitorInterface, ReaderBufferControl {

    @Override
    public String toString() {
        return "SiLabsC8051F320_USBIO_DVS128";
    }
    static Logger log = Logger.getLogger("SiLabsC8051F320_USBIO_DVS128");
    /** Used to store preferences, e.g. the default firmware download file for blank devices */
    protected static Preferences prefs = Preferences.userNodeForPackage(SiLabsC8051F320_USBIO_DVS128.class);
    PnPNotify pnp = null;
    private int interfaceNumber = 0;
    /** driver guid (Globally unique ID, for this USB driver instance. This GUID maps to the windows driver used. */
    public final static String GUID = CypressFX2.GUID;
    final static short CONFIG_INDEX = 0;
    final static short CONFIG_NB_OF_INTERFACES = 1;
    final static short CONFIG_INTERFACE = 0;
    final static short CONFIG_ALT_SETTING = 0;
    final static int CONFIG_TRAN_SIZE = 64;    // out endpoint for servo commands
    /** The vendor ID */
    static public final short VID = USBInterface.VID_THESYCON;
    /** The product ID for devices programmed with latest PAER firmware*/
    static public final short PID = (short) 0x8411;
    /** The product ID for Tmpdiff128 boards with SiLabsF320 chip developed in CAVIAR, programmed with older firmware and using USBXpress VID/PID. These
     * devices are also specified in the inf files for installing the usbaermon.sys driver in drivers/Windows/driverDVS_USBAERmini2.
     */
    static public final short PID_LEGACY_SILABS_USBXPRESS = (short) 0xEA61;
    private final int ENDPOINT_IN = 0x82, ENDPOINT_OUT = 0x2; // both endpoint 2, but IN has bit 7 set. Verified from Thesycon Demo App
    private boolean isOpened = false;
    /** the USBIO device descriptor */
    private USB_DEVICE_DESCRIPTOR deviceDescriptor = new USB_DEVICE_DESCRIPTOR();
    private UsbIoPipe outPipe = null;  // this pipe used to write to device (biases, etc)
    /** this is the size of the AEPacketRaw that are part of AEPacketRawPool that double buffer the translated events between rendering and capture threads */
    protected int aeBufferSize = prefs.getInt("CypressFX2.aeBufferSize", AE_BUFFER_SIZE);
    /** default size of AE buffer for user processes. This is the buffer that is written by the hardware capture thread that holds events
     * that have not yet been transferred via {@link #acquireAvailableEventsFromDriver} to another thread
     * @see #acquireAvailableEventsFromDriver
     * @see AEReader
     * @see #setAEBufferSize
     */
    public static final int AE_BUFFER_SIZE = 100000; // should handle 5Meps at 30FPS
    private boolean eventAcquisitionEnabled = false;
    private final int TICK_US = 1;
    private boolean aeReaderRunning = false;

    public SiLabsC8051F320_USBIO_DVS128(int interfaceNumber) {
        this.interfaceNumber = interfaceNumber;
//        Runtime.getRuntime().addShutdownHook(new Thread() {
//
//            @Override
//            public void run() {
//                if (isOpen()) {
//                    close();
//                }
//            }
//        });  // TODO should not be needed to shutdown
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

    /** called before buffer is submitted to driver. Prepares buffer to submit for read. */
    @Override
    public void processBuffer(UsbIoBuf Buf) {
        Buf.NumberOfBytesToTransfer = Buf.Size;
        Buf.BytesTransferred = 0;
        Buf.OperationFinished = false;
        //            System.out.println("ProcessBuffer (before read)");
    }

    /** Called on completion of read on a data buffer is received from USBIO driver.
     * @param Buf the data buffer with raw data
     */
    @Override
    public void processData(UsbIoBuf Buf) {
        // instrument cycle times
//            long thisTime=System.nanoTime();
//            times[cycleCounter%NTIMES]=thisTime-lastTime;
//            lastTime=thisTime;
//            if(cycleCounter%NTIMES==0){
//                try {
//                    if(timeWriter==null){
//                        timeWriter=new PrintWriter("cycleTimes.csv");
//                    }
//                    for(long t:times){
//                        timeWriter.format("%d\n",t);
//                    }
//                    timeWriter.flush();
//                } catch (FileNotFoundException ex) {
//                    ex.printStackTrace();
//                }
//            }

        if ((Buf.Status == USBIO_ERR_SUCCESS) || (Buf.Status == USBIO_ERR_CANCELED)) {
            translateEvents(Buf);
        } else {
            log.warning("ProcessData: Bytes transferred: " + Buf.BytesTransferred + "  Status: " + UsbIo.errorText(Buf.Status));
            close(); // watch out, this can call synchronized method
        }
//            if (timestampsReset) {
////                    log.info("timestampsReset: flushing aePacketRawPool buffers");
////                    don't flush, just let the old data through since it is still data
////                    aePacketRawPool.reset();  //this is already done in resetTimestamps() why do it again here?
//                timestampsReset = false;
//            }
    }        // sync so that we don't try to copy events while buffer is being translated
    /** The count of events acquired but not yet passed to user via acquireAvailableEventsFromDriver */
    volatile private int eventCounter = 0;  // counts events acquired but not yet passed to user
    final int WRAP_START = 0; //(int)(0xFFFFFFFFL&(2147483648L-0x400000L)); // set high to test big wrap 1<<30;
    /** wrapAdd is the time to add to short timestamp to unwrap it */
    private int wrapAdd = WRAP_START; //0;
    private int wrapsSinceLastEvent=0;
    private final int WRAPS_TO_PRINT_NO_EVENT=500;
    private boolean gotEvent=false;

    /** Does the translation, timestamp unwrapping and reset
     * @param b the raw buffer
     */
    private void translateEvents(UsbIoBuf b) {
//            System.out.println("buf has "+b.BytesTransferred+" bytes");
        synchronized (aePacketRawPool) {
            AEPacketRaw buffer = aePacketRawPool.writeBuffer();
            if (buffer.overrunOccuredFlag) {
                return;  // don't bother if there's already an overrun, consumer must get the events to clear this flag before there is more room for new events
            }
            int shortts;
//            int NumberOfWrapEvents;
//            NumberOfWrapEvents = 0;

            byte[] aeBuffer = b.BufferMem;
            //            byte lsb,msb;
            int bytesSent = b.BytesTransferred;
            if ((bytesSent % 4) != 0) {
                log.warning("warning: " + bytesSent + " bytes sent, which is not multiple of 4");
                bytesSent = (bytesSent / 4) * 4; // truncate off any extra part-event
                }

            int[] addresses = buffer.getAddresses();
            int[] timestamps = buffer.getTimestamps();

            buffer.lastCaptureIndex = eventCounter; // write the start of the packet
            gotEvent=false;

            for (int i = 0; i < bytesSent; i += 4) {
                if (eventCounter > (aeBufferSize - 1)) {
                    buffer.overrunOccuredFlag = true;
//                                        log.warning("overrun");
                    return; // return, output event buffer is full and we cannot add any more events to it.
                    //no more events will be translated until the existing events have been consumed by acquireAvailableEventsFromDriver
                }

                addresses[eventCounter] = (aeBuffer[i + 1] & 0xFF) | ((aeBuffer[i] & 0xFF) << 8);
                shortts = ((aeBuffer[i + 3] & 0xff) | ((aeBuffer[i + 2] & 0xff) << 8));

                if (addresses[eventCounter] == 0xFFFF) { // changed to handle this address as special wrap event
                    wrapAdd += 0x10000;	// if we wrapped then increment wrap value by 2^16
                    if(!gotEvent) {
						wrapsSinceLastEvent++;
					}
                    if(wrapsSinceLastEvent>=WRAPS_TO_PRINT_NO_EVENT){
                        log.warning("got "+wrapsSinceLastEvent+" timestamp wraps without any events");
                        wrapsSinceLastEvent=0;
                    }
                    continue; // skip timestamp and continue to next address without incrementing eventCounter
                    }
                timestamps[eventCounter] = TICK_US * (shortts + wrapAdd); //*TICK_US; //add in the wrap offset and convert to 1us tick
                eventCounter++;
                buffer.setNumEvents(eventCounter);
                gotEvent=true;
                wrapsSinceLastEvent=0;
            }

            // write capture size
            buffer.lastCaptureLength = eventCounter - buffer.lastCaptureIndex;
//            System.out.print(buffer.lastCaptureLength + " events");
//            for(int i=buffer.lastCaptureIndex;i<buffer.lastCaptureIndex+buffer.lastCaptureLength;i++){
//                System.out.print(" "+buffer.addresses[i]);
//            }
//            System.out.println("");

        } // sync on aePacketRawPool
    }

    @Override
    public void bufErrorHandler(UsbIoBuf buf) {
        if ((buf.Status != USBIO_ERR_CANCELED) && (buf.Status != USBIO_ERR_SUCCESS)) {
            log.warning(UsbIo.errorText(buf.Status));
        }
    }

    @Override
    public void onThreadExit() {
        freeBuffers();
        // System.out.println(this+" event capture worker-thread terminated.\n");
        aeReaderRunning = false;
    }

    /** return the string USB descriptors for the device
     *@return String[] of length 2 or 3 of USB descriptor strings.
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
        s[0] = stringDescriptor1.Str == null ? "" : stringDescriptor1.Str;
        s[1] = stringDescriptor2.Str == null ? "" : stringDescriptor2.Str;
        if (numberOfStringDescriptors == 3) {
            s[2] = stringDescriptor3.Str == null ? "" : stringDescriptor3.Str;
        }
        return s;
    }

    @Override
	public short getVID_THESYCON_FX2_CPLD() {
        return VID;
    }

    @Override
	public short getPID() {
        return PID;
    }

    /** @return always 0 */
    @Override
	public short getDID() {
        return 0;
    }

    @Override
	public String getTypeName() {
        return "SiLabsC8051F320";
    }

    /** Closes the device. Never throws an exception.
     */
    @Override
    public void close() {
        if (!isOpened) {
            log.warning("close(): not open");
            return;
        }
        shutdownThread();
        int status = resetDevice();
        if (status != USBIO_ERR_SUCCESS) {
            log.warning("can't reset USB device: " + UsbIo.errorText(status));
        }
        super.close();

        isOpened = false;

    }

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
        long gDevList = UsbIo.createDeviceList(GUID);
        status = open(interfaceNumber, gDevList, GUID);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("can't open USB device: " + UsbIo.errorText(status));
        }

        // get device descriptor
        status = getDeviceDescriptor(deviceDescriptor);
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
        status = setConfiguration(Conf);
        if (status != USBIO_ERR_SUCCESS) {
//            gUsbIo.destroyDeviceList(gDevList);
            //   if (status !=0xE0001005)
            log.warning("setting configuration: " + UsbIo.errorText(status));
        }

        // get device descriptor
        status = getDeviceDescriptor(deviceDescriptor);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("getDeviceDescriptor: " + UsbIo.errorText(status));
        } else {
            log.info("getDeviceDescriptor: Vendor ID (VID) " + HexString.toString((short) deviceDescriptor.idVendor) + " Product ID (PID) " + HexString.toString((short) deviceDescriptor.idProduct));
        }

        if (deviceDescriptor.iSerialNumber != 0) {
            this.numberOfStringDescriptors = 3;        // get string descriptor
        }

        status = getStringDescriptor(stringDescriptor1, (byte) 1, 0);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("getStringDescriptor: " + UsbIo.errorText(status));
        } else {
            log.info("getStringDescriptor 1: " + stringDescriptor1.Str);
        }

        // get string descriptor
        status = getStringDescriptor(stringDescriptor2, (byte) 2, 0);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("getStringDescriptor: " + UsbIo.errorText(status));
        } else {
            log.info("getStringDescriptor 2: " + stringDescriptor2.Str);
        }

        if (this.numberOfStringDescriptors == 3) {
            // get serial number string descriptor
            status = getStringDescriptor(stringDescriptor3, (byte) 3, 0);
            if (status != USBIO_ERR_SUCCESS) {
                UsbIo.destroyDeviceList(gDevList);
                throw new HardwareInterfaceException("getStringDescriptor: " + UsbIo.errorText(status));
            } else {
                log.info("getStringDescriptor 3: " + stringDescriptor3.Str);
            }
        }

        // get outPipe information and extract the FIFO size
        USBIO_CONFIGURATION_INFO configurationInfo = new USBIO_CONFIGURATION_INFO();
        status = getConfigurationInfo(configurationInfo);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("getConfigurationInfo: " + UsbIo.errorText(status));
        }

        if (configurationInfo.NbOfPipes == 0) {
//            gUsbIo.cyclePort();
            throw new HardwareInterfaceException("didn't find any pipes to bind to");
        }


        status = bind(interfaceNumber, (byte) ENDPOINT_IN, gDevList, GUID);  // binds this pipe (the reader) to the IN endpoint. We'll make another pipe to write config.

        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("can't bind reader to endpoint: " + UsbIo.errorText(status));
        }
        USBIO_PIPE_PARAMETERS pipeParams = new USBIO_PIPE_PARAMETERS();
        pipeParams.Flags = UsbIoInterface.USBIO_SHORT_TRANSFER_OK;
        status = setPipeParameters(pipeParams);
        if (status != USBIO_ERR_SUCCESS) {
            destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("can't set IN pipe parameters: " + UsbIo.errorText(status));
        }

        startThread(3);  // start the reader thread of this

        outPipe = new UsbIoPipe();  // make just a basic pipe for sync io for the biases/configuration
        status = outPipe.bind(interfaceNumber, (byte) ENDPOINT_OUT, gDevList, GUID);
        if (status != USBIO_ERR_SUCCESS) {
            destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("can't bind OUT pipe: " + UsbIo.errorText(status));
        }

        pipeParams = new USBIO_PIPE_PARAMETERS();
        pipeParams.Flags = UsbIoInterface.USBIO_SHORT_TRANSFER_OK;
        status = setPipeParameters(pipeParams);
        if (status != USBIO_ERR_SUCCESS) {
            destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("can't set OUT pipe parameters: " + UsbIo.errorText(status));
        }

        setEventAcquisitionEnabled(true);
        isOpened = true;
    }

    void ensureOpen() throws HardwareInterfaceException {
        if (!isOpen()) {
            open();
        }
    }

    @Override
	public void sendConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        if (biasgen.getPotArray() == null) {
            log.info("BiasgenUSBInterface.send(): iPotArray=null, no biases to send");
            return; // may not have been constructed yet.
        }

        ensureOpen();

        //        throw new IPotException("null USBIO interface");
        //        if(iPotArray==null) throw new IPotException("null iPotArray");
        //
        // we make an array of bytes to hold the values sent, then we fill the array, copy it to a
        // new array of the proper size, and pass it to the routine that actually sends a vendor request
        // with a data buffer that is the bytes

        byte[] dataBytes;
        // construct the byte array to send to hardware interface depending on wheter the PotArray is actually an IPotArray
        if (biasgen.getPotArray() instanceof net.sf.jaer.biasgen.IPotArray) {
            dataBytes = getBiasBytes(biasgen);

        } else {
            // asssume we only have VPots
            VPot p = null;

            ArrayList<Pot> pots = biasgen.getPotArray().getPots();

            dataBytes = new byte[pots.size() * 3];
            int i = 0;
            for (Pot pot : pots) {
                p = (VPot) pot;
                dataBytes[i] = (byte) p.getChannel(); //address
                dataBytes[i + 1] = (byte) ((p.getBitValue() & 0x0F00) >> 8);  //value msb
                dataBytes[i + 2] = (byte) (p.getBitValue() & 0x00FF); //value lsb
                i += 3;
            }

            //    for (int k=0;k<pots.size();k++)
            //      System.out.println("bias  " + k +": channel " + toSend[3*k] + " value " +toSend[3*k+1] +" "+ toSend[3*k+2]);
        }

        byte[] allBytes = new byte[2 + dataBytes.length]; // need header and length
        allBytes[0] = BIAS_BIASES;
        allBytes[1] = (byte) (0xff & dataBytes.length);  // avoid signed byte by masking
        System.arraycopy(dataBytes, 0, allBytes, 2, dataBytes.length);
        sendBytes(allBytes);
    }

    private void sendBytes(byte[] b) throws HardwareInterfaceException {
        USBIO_DATA_BUFFER buf = new USBIO_DATA_BUFFER(b.length);
        System.arraycopy(b, 0, buf.Buffer(), 0, b.length);
        buf.setNumberOfBytesToTransfer(b.length);
        int status = outPipe.writeSync(buf);
        if (buf.getBytesTransferred() != b.length) {
            throw new HardwareInterfaceException("only transferred " + buf.getBytesTransferred() + " of " + b.length + " bytes");
        }
        if (status == 0) {
            HardwareInterfaceException.clearException();
            return;
        } else {
            close();
            throw new HardwareInterfaceException("can't send biases: " + errorText(status));
        }
    }

    @Override
    public void startThread(int MaxIoErrorCount) {
        boolean wasNotAllocatedAlready = allocateBuffers(getFifoSize(), getNumBuffers());
        if (!wasNotAllocatedAlready) {
            log.warning("buffers were already allocated");
        }
        aePacketRawPool=new AEPacketRawPool(SiLabsC8051F320_USBIO_DVS128.this);
        super.startThread(MaxIoErrorCount);
        T.setPriority(MONITOR_PRIORITY); // very important that this thread have priority or the acquisition will stall on device side for substantial amounts of time!
        T.setName("AEReader");
        aeReaderRunning = true;
        support.firePropertyChange("readerStarted", false, true); // used in AEViewer to set up menus for changing buffer size
    }
    /** the priority for this monitor acquisition thread. This should be set high (e.g. Thread.MAX_PRIORITY) so that the thread can
     * start new buffer reads in a timely manner so that the sender does not get blocked
     * */
    public static final int MONITOR_PRIORITY = Thread.MAX_PRIORITY; // Thread.NORM_PRIORITY+2
    /** size of USBIO USB host fifo's in bytes. */
    public static final int FIFO_SIZE = 512;
    /** the default number of USB read buffers used in the reader */
    public static final int NUM_BUFFERS = 4;
    /** the number of capture buffers for the buffer pool for the translated address-events.
     * These buffers allow for smoother access to buffer space by the event capture thread */
    private int numBuffers = prefs.getInt("SiLabsC8051F320_USBIO_DVS128.numBuffers", NUM_BUFFERS);
    /** size of FIFOs in bytes used in AEReader for event capture from device.
     * This does not have to be the same size as the FIFOs in the CypressFX2 (512 bytes). If it is too small, then there
     * are frequent thread context switches that can greatly slow down rendering loops.
     */
    private int fifoSize = prefs.getInt("SiLabsC8051F320_USBIO_DVS128.fifoSize", FIFO_SIZE); // 64;

    @Override
	public int getFifoSize() {
        return fifoSize;
    }

    @Override
	public void setFifoSize(int fifoSize) {
        if (fifoSize < FIFO_SIZE) {
            log.warning("SiLabsC8051F320_USBIO_DVS128 fifo size clipped to device FIFO size " + FIFO_SIZE);
            fifoSize = FIFO_SIZE;
        }
        this.fifoSize = fifoSize;
        freeBuffers();
        boolean wasNotAllocatedAlready = allocateBuffers(getFifoSize(), getNumBuffers());
        if (!wasNotAllocatedAlready) {
            log.warning("buffers were already allocated");
        }
        prefs.putInt("SiLabsC8051F320_USBIO_DVS128.fifoSize", fifoSize);
    }

    @Override
	public int getNumBuffers() {
        return numBuffers;
    }

    @Override
	public void setNumBuffers(int numBuffers) {
        this.numBuffers = numBuffers;
        freeBuffers();
        boolean wasNotAllocatedAlready = allocateBuffers(getFifoSize(), getNumBuffers());
        if (!wasNotAllocatedAlready) {
            log.warning("buffers were already allocated");
        }
        prefs.putInt("SiLabsC8051F320_USBIO_DVS128.numBuffers", numBuffers);
    }

    @Override
	public void flashConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        if (biasgen.getPotArray() == null) {
            log.info("iPotArray=null, no biases to send");
            return; // may not have been constructed yet.
        }
        ensureOpen();
        byte[] dataBytes = getBiasBytes(biasgen);
        byte[] toSend = new byte[2 + dataBytes.length];
        toSend[0] = BIAS_FLASH;
        toSend[1] = (byte) (0xff & dataBytes.length);  // avoid signed byte by masking
        System.arraycopy(dataBytes, 0, toSend, 2, dataBytes.length);
        sendBytes(toSend);
    }

    /** This implementation treats the biasgen as a simple array of IPots each of which provides bytes to send.
     * Subclasses can override formatConfigurationBytes in case they have additional information to format.
     * If the biasgen potArray is an IPotArray, the bytes are formatted and sent. Otherwise nothing is sent.
     * @param biasgen the source of configuration information.
     * @return the bytes to send
     */
    @Override
	public byte[] formatConfigurationBytes(Biasgen biasgen) {
        // we need to cast from PotArray to IPotArray, because we need the shift register stuff
        PotArray potArray = biasgen.getPotArray();

        // we make an array of bytes to hold the values sent, then we fill the array, copy it to a
        // new array of the proper size, and pass it to the routine that actually sends a vendor request
        // with a data buffer that is the bytes

        if (potArray instanceof IPotArray) {
            IPotArray ipots = (IPotArray) potArray;
            byte[] bytes = new byte[potArray.getNumPots() * MAX_BYTES_PER_BIAS];
            int byteIndex = 0;


            Iterator i = ipots.getShiftRegisterIterator();
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
        return null;
    }



    /** Allocates internal memory for transferring data from reader to consumer, e.g. rendering. */
    protected void allocateAEBuffers() {
        synchronized (aePacketRawPool) {
            aePacketRawPool.allocateMemory();
        }
    }
    /** The pool of raw AE packets, used for data transfer */
    private AEPacketRawPool aePacketRawPool =null;
    /** the last events from {@link #acquireAvailableEventsFromDriver}, This packet is reused. */
    protected AEPacketRaw lastEventsAcquired = new AEPacketRaw();
    /**
     * event supplied to listeners when new events are collected. this is final because it is just a marker for the listeners that new events are available
     */
    public final PropertyChangeEvent NEW_EVENTS_PROPERTY_CHANGE = new PropertyChangeEvent(this, "NewEvents", null, null);

    /** Gets available events from driver and return them in a new AEPacketRaw.
     *{@link #overrunOccurred} will be true if these was an overrun of the host USBXPress driver buffers (>16k events).
     *<p>
     *AEListeners are called if new events have been collected.
     *
     * @return packet of raw events
     *@throws USBAEMonitorException
     *@see #addAEListener
     * .
     */
    @Override
	public AEPacketRaw acquireAvailableEventsFromDriver() throws HardwareInterfaceException {
        if (!isOpened) {
            open();
        }

        // get the 'active' buffer for events (the one that has just been written by the hardware thread)
        synchronized (aePacketRawPool) { // synchronize on aeReader so that we don't try to access the events at the same time
            aePacketRawPool.swap();
            lastEventsAcquired = aePacketRawPool.readBuffer();
        }
        eventCounter = 0;
        computeEstimatedEventRate(lastEventsAcquired);
        int nEvents = lastEventsAcquired.getNumEvents();
        if (nEvents != 0) {
            support.firePropertyChange(NEW_EVENTS_PROPERTY_CHANGE); // call listeners
        }
//        System.out.println("returning " + lastEventsAcquired);
        return lastEventsAcquired;
    }

    /** Returns the number of events acquired by the last call to {@link
     * #acquireAvailableEventsFromDriver }
     * @return number of events acquired
     */
    @Override
	public int getNumEventsAcquired() {
        return aePacketRawPool.readBuffer().getNumEvents();
    }

    /** returns last events from {@link #acquireAvailableEventsFromDriver}
     *@return the event packet
     */
    @Override
	public AEPacketRaw getEvents() {
        return this.lastEventsAcquired;
    }

    @Override
	public void resetTimestamps() {
        try {
            sendBooleanCommand(CMD_RESETTIMESTAMPS, true);
        } catch (HardwareInterfaceException ex) {
            log.warning(ex.toString());
        }
        wrapAdd = WRAP_START;
    }

    /** Is true if an overrun occured in the driver (><code> AE_BUFFER_SIZE</code> events) during the period before the last time {@link
     * #acquireAvailableEventsFromDriver } was called. This flag is cleared by {@link #acquireAvailableEventsFromDriver}, so you need to
     * check it before you acquire the events.
     *<p>
     *If there is an overrun, the events grabbed are the most ancient; events after the overrun are discarded. The timestamps continue on but will
     *probably be lagged behind what they should be.
     * @return true if there was an overrun.
     */
    @Override
	public boolean overrunOccurred() {
        return aePacketRawPool.readBuffer().overrunOccuredFlag;
    }

    /** @return the size of the double buffer raw packet for AEs */
    @Override
	public int getAEBufferSize() {
        return aeBufferSize; // aePacketRawPool.writeBuffer().getCapacity();
    }

    /** set the size of the raw event packet buffer. Default is AE_BUFFER_SIZE. You can set this larger if you
     *have overruns because your host processing (e.g. rendering) is taking too long.
     *<p>
     *This call discards collected events.
     * @param size of buffer in events
     */
    @Override
	public void setAEBufferSize(int size) {
        if ((size < 1000) || (size > 1000000)) {
            log.warning("ignoring unreasonable aeBufferSize of " + size + ", choose a more reasonable size between 1000 and 1000000");
            return;
        }
        this.aeBufferSize = size;
        prefs.putInt("CypressFX2.aeBufferSize", aeBufferSize);
        allocateAEBuffers();
    }
    private final int BIAS_BIASES = 1;	// send biases out on SPI
    private final int BIAS_SETPOWER = 2;	// set the biasgen powerDown input
    private final int BIAS_FLASH = 4;	//  flash the biases
    private final int AE_EVENT_ACQUISITION_ENABLED = 5; // set event aquisition enabled
    private final int CMD_RESETTIMESTAMPS = 6; // reset the device timestamps

    private void sendBooleanCommand(int command, boolean value) throws HardwareInterfaceException {
        USBIO_DATA_BUFFER buf = new USBIO_DATA_BUFFER(2);
        buf.Buffer()[0] = (byte) command;
        buf.Buffer()[1] = value ? (byte) 1 : (byte) 0;
        buf.setNumberOfBytesToTransfer(2);
        int status = outPipe.writeSync(buf);
        if (status != USBIO_ERR_SUCCESS) {
            throw new HardwareInterfaceException("Trying to send boolean command, got error " + UsbIo.errorText(status));
        }
    }

    @Override
	public void setPowerDown(boolean powerDown) throws HardwareInterfaceException {
        if (!isOpen()) {
            log.info("SiLabsC8051F320.setPowerDown(): device not open, opening it");
            open();
        }
        sendBooleanCommand(BIAS_SETPOWER, powerDown);
    }

    @Override
	public void setEventAcquisitionEnabled(boolean enable) throws HardwareInterfaceException {
        if (!isOpen()) {
            log.info("SiLabsC8051F320.setEventAcquisitionEnabled(): device not open, opening it");
            open();
        }
        sendBooleanCommand(AE_EVENT_ACQUISITION_ENABLED, enable);
        this.eventAcquisitionEnabled = enable;
    }

    @Override
	public boolean isEventAcquisitionEnabled() {
        return eventAcquisitionEnabled;
    }
    /** This support can be used to register this interface for property change events */
    public PropertyChangeSupport support = new PropertyChangeSupport(this);    // consts

    /** adds a listener for new events captured from the device.
     * Actually gets called whenever someone looks for new events and there are some using
     * acquireAvailableEventsFromDriver, not when data is actually captured by AEReader.
     * Thus it will be limited to the users sampling rate, e.g. the game loop rendering rate.
     *
     * @param listener the listener. It is called with a PropertyChangeEvent when new events
     * are received by a call to {@link #acquireAvailableEventsFromDriver}.
     * These events may be accessed by calling {@link #getEvents}.
     */
    @Override
	public void addAEListener(AEListener listener) {
        support.addPropertyChangeListener(listener);
    }

    @Override
	public void removeAEListener(AEListener listener) {
        support.removePropertyChangeListener(listener);
    }

    /** the max capacity of this USB2 full (not high) speed bus interface is about 100keps.
     */
    @Override
	public int getMaxCapacity() {
        return 100000;
    }
    private int estimatedEventRate = 0;

    /** @return event rate in events/sec as computed from last acquisition.
     *
     */
    @Override
	public int getEstimatedEventRate() {
        return estimatedEventRate;
    }

    /** computes the estimated event rate for a packet of events */
    void computeEstimatedEventRate(AEPacketRaw events) {
        if ((events == null) || (events.getNumEvents() < 2)) {
            estimatedEventRate = 0;
        } else {
            int[] ts = events.getTimestamps();
            int n = events.getNumEvents();
            int dt = ts[n - 1] - ts[0];
            estimatedEventRate = (int) ((1e6f * n) / dt);
        }
    }

    @Override
	public int getTimestampTickUs() {
        return 1;
    }
    /** The AEChip we're talking to */
    protected AEChip chip;

    @Override
	public void setChip(AEChip chip) {
        this.chip = chip;
    }

    @Override
	public AEChip getChip() {
        return chip;
    }
    public static final int MAX_BYTES_PER_BIAS = 4;

    // this method can only be used for IPotArrays
    private byte[] getBiasBytes(final Biasgen biasgen) {
        IPotArray iPotArray = (IPotArray) biasgen.getPotArray();
        byte[] bytes = new byte[iPotArray.getNumPots() * MAX_BYTES_PER_BIAS]; // oversize this for now, later we copy to actual array
        int byteIndex = 0;
        byte[] toSend;
        Iterator i = iPotArray.getShiftRegisterIterator();
        while (i.hasNext()) {
            IPot iPot = (IPot) i.next();
            // for each bias starting with the first one (the one closest to the ** END ** of the shift register
            // we get the bitValue and from MSB ro LSB stuff these values into the byte array

            for (int k = iPot.getNumBytes() - 1; k >= 0; k--) { // for k=2..0
                bytes[byteIndex++] = (byte) ((iPot.getBitValue() >>> (k * 8)) & 0xff);
            }
        }
        toSend = new byte[byteIndex];
        System.arraycopy(bytes, 0, toSend, 0, byteIndex);

        return toSend;
    }

    @Override
	public PropertyChangeSupport getReaderSupport() {
        return support;
    }
}
