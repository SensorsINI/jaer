/*
 * SiLabsC8051F320_USBIO_ServoController.java
 *
 * Created on July 15, 2006, 1:48 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright July 15, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.jaer.projects.opticalflow.usbinterface;

import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import javax.swing.JPanel;

import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.IPot;
import net.sf.jaer.biasgen.PotArray;
import net.sf.jaer.biasgen.VDAC.VPot;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.util.HexString;
import ch.unizh.ini.jaer.projects.opticalflow.Chip2DMotion;
import ch.unizh.ini.jaer.projects.opticalflow.MotionData;
import ch.unizh.ini.jaer.projects.opticalflow.mdc2d.MDC2D;
import ch.unizh.ini.jaer.projects.opticalflow.mdc2d.MotionDataMDC2D;
import de.thesycon.usbio.PnPNotify;
import de.thesycon.usbio.PnPNotifyInterface;
import de.thesycon.usbio.UsbIo;
import de.thesycon.usbio.UsbIoBuf;
import de.thesycon.usbio.UsbIoErrorCodes;
import de.thesycon.usbio.UsbIoInterface;
import de.thesycon.usbio.UsbIoPipe;
import de.thesycon.usbio.UsbIoReader;
import de.thesycon.usbio.structs.USBIO_CLASS_OR_VENDOR_REQUEST;
import de.thesycon.usbio.structs.USBIO_DATA_BUFFER;
import de.thesycon.usbio.structs.USBIO_PIPE_PARAMETERS;
import de.thesycon.usbio.structs.USBIO_SET_CONFIGURATION;
import de.thesycon.usbio.structs.USB_DEVICE_DESCRIPTOR;
import de.thesycon.usbio.structs.USB_STRING_DESCRIPTOR;

/**
 * Servo motor controller using USBIO driver access to SiLabsC8051F320 device.
 *
 * @author tobi
 * 
 * changelog by andstein
 *   - moved various hardware specific parts from other code into this class
 * 
 * TODO
 *   - when the hardware interface is closed, it cannot be properly reopened
 *     because the MotionReader-thread cannot be reset in the open() method
 */
public class SiLabsC8051F320_OpticalFlowHardwareInterface implements MotionChipInterface, UsbIoErrorCodes, PnPNotifyInterface {
    Logger log=Logger.getLogger("SiLabsC8051F320_USBIO_ServoController");

    /** A "magic byte" marking the start of each frame */
    public static final byte FRAME_START_MARKER = (byte)0xac;
    
    static final int MAX_POTS=64;
    
    int interfaceNumber=0;// which interface to open as numbered by createDeviceList
    
    /** driver GUID (Globally unique ID, for this USB driver instance */
    public final static String GUID  = "{2013DFAA-ED13-4775-9967-8C3FEC412E2C}"; // tobi generated 12/2006 for OpticalFlowBoard, DeviceIdent
    
    /** Optical flow board vendor ID (This is Thesycon's VID) */
    static public final short VID=(short)0x0547;
    
    /**OpticalFlowBoard Product ID */
    static public final short PID=(short)0x8760; ///
    
    public final static short CONFIG_INDEX                       = 0;
    public final static short CONFIG_NB_OF_INTERFACES            = 1;
    public final static short CONFIG_INTERFACE                   = 0;
    public final static short CONFIG_ALT_SETTING                 = 0;
    public final static int CONFIG_TRAN_SIZE                     = 64;
    
    public final static byte ENDPOINT_OUT=(byte)0x02;
    public final static byte ENDPOINT_IN =(byte)0x81; /// code taken from firmware
    
    // the vender request cmd bytes that specify the type of command
    public final static byte VENDOR_REQUEST_START_STREAMING=0x1a;
    public final static byte VENDOR_REQUEST_STOP_STREAMING=0x1b;
    public final static byte VENDOR_REQUEST_SEND_BIASES=0x1c;
    public final static byte VENDOR_REQUEST_SEND_BIAS=0x1f;
    public final static byte VENDOR_REQUEST_SET_DATA_TO_SEND=0x1d;
    public final static byte VENDOR_REQUEST_REQUEST_FRAME=0x1e;
    public final static byte VENDOR_REQUEST_SEND_ONCHIP_BIAS=0x20;
    public final static byte VENDOR_REQUEST_SET_POWERDOWN_STATE = 0x21;
    public final static byte VENDOR_REQUEST_SET_DATA_TO_SEND_MDC2D=0x22;




    PnPNotify pnp=null;
    
    private boolean isOpened;
    
    UsbIoPipe outPipe=null; // the pipe used for writing to the device
    UsbIoPipe inPipe=null; /// the pipe used for reiceiving values from the device
    
//    MotionUsbThread motionUsbThread = null;
    private MotionReader reader=null; // the async reader thread that gets data from the device
    
    private int[] vpotValues=null; // cache of pot values used for checking which ones to send
    private int[] ipotValues=null; // cache of pot values used for checking which ones to send
    
    private static final long DATA_TIMEOUT_MS=50000; // timeout for getting data from device
    private static final int MOTION_BUFFER_LENGTH=1<<14; // Size of UsbioBuf buffers. Make bigger to optimize?
    
    private Chip2DMotion chip=new MDC2D();

    private MotionData lastbuffer;



    /**
     * Creates a new instance of SiLabsC8051F320_USBIO_ServoController
     * @param n the number of the interface, in range returned by OpticalFlowHardwareInterfaceFactory.getNumInterfacesAvailable().
     *
     */
    public SiLabsC8051F320_OpticalFlowHardwareInterface(int n) {
        interfaceNumber=n;
    }

    private void generateMotionData() {
        initialEmptyBuffer = chip.getEmptyMotionData(); // the buffer to start capturing into
        initialFullBuffer = chip.getEmptyMotionData();    // the buffer to render/process first
        currentBuffer=initialFullBuffer;
    }

    public void setChip(Chip2DMotion chip){
        this.chip=chip;
        generateMotionData();
    }
    
//    public void startMotionUsbThread() {
//        motionUsbThread = new MotionUsbThread();
//        //motionUsbThread.start();
//        motionUsbThread.run();
//    }
    
    public void onAdd() {
        log.info("device added");
    }
    
    public void onRemove() {
        log.info("device removed");
    }
    
    /** Closes the device. Never throws an exception.
     */
    synchronized public void close(){
        if(!isOpened){
            log.warning("close(): not open");
            return;
        }

        //TODO the thread is not properly stopped -> the interface cannot
        //     be re-opened once it's closed withtout restarting the application
        if(reader!=null) {
//            reader.abortPipe();
            reader.shutdownThread();
            reader.unbind();
            reader.close();
            reader= null;
        }
        
//        if(motionUsbThread!=null) {
//            motionUsbThread.stopThread();
//            inPipe.unbind();
//        }
        gUsbIo.close();
        UsbIo.destroyDeviceList(gDevList);
        log.info("USBIOInterface.close(): device closed");
        isOpened=false;
    }
    
    /** the first USB string descriptor (Vendor name) (if available) */
    protected USB_STRING_DESCRIPTOR stringDescriptor1 = new USB_STRING_DESCRIPTOR();
    
    /** the second USB string descriptor (Product name) (if available) */
    protected USB_STRING_DESCRIPTOR stringDescriptor2 = new USB_STRING_DESCRIPTOR();
    
    /** the third USB string descriptor (Serial number) (if available) */
    protected USB_STRING_DESCRIPTOR stringDescriptor3 = new USB_STRING_DESCRIPTOR();
    
    protected int numberOfStringDescriptors=2;
    
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
    protected UsbIo gUsbIo=null;
    
    /** the devlist handle for USBIO */
    protected long gDevList; // 'handle' (an integer) to an internal device list static to UsbIo
    
    
    /** checks if device has a string identifier that is a non-empty string
     *@return false if not, true if there is one
     */
    protected boolean hasStringIdentifier(){
        // get string descriptor
        int status = gUsbIo.getStringDescriptor(stringDescriptor1,(byte)1,0);
        if (status != USBIO_ERR_SUCCESS) {
            return false;
        } else {
            if(stringDescriptor1.Str.length()>0) return true;
        }
        return false;
    }
    
    /** constrcuts a new USB connection, opens it.
     */
    public void open() {
        try{
            openUsbIo();
        }catch(HardwareInterfaceException e) {
            e.printStackTrace();
            close();
        }
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
    //synchronized protected void openUsbIo() throws HardwareInterfaceException {
    synchronized public void openUsbIo() throws HardwareInterfaceException {
        
        //device has already been UsbIo Opened by now, in factory
        
        // opens the USBIOInterface device, configures it, binds a reader thread with buffer pool to read from the device and starts the thread reading events.
        // we got a UsbIo object when enumerating all devices and we also made a device list. the device has already been
        // opened from the UsbIo viewpoint, but it still needs firmware download, setting up pipes, etc.
        
        if(isOpened){
            log.warning("CypressFX2.openUsbIo(): already opened interface and setup device");
            return;
        }
        
        int status;
        
        gUsbIo=new UsbIo();
        gDevList=UsbIo.createDeviceList(GUID);
        
        status = gUsbIo.open(interfaceNumber,gDevList,GUID);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("can't open USB device: "+UsbIo.errorText(status));
        }
        
        // get device descriptor
        status = gUsbIo.getDeviceDescriptor(deviceDescriptor);
        if (status != USBIO_ERR_SUCCESS) {
            gUsbIo.resetDevice();
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("CypressFX2.openUsbIo(): getDeviceDescriptor: "+UsbIo.errorText(status));
        } else {
            log.info("getDeviceDescriptor: Vendor ID (VID) "
                    + HexString.toString((short)deviceDescriptor.idVendor)
                    + " Product ID (PID) " + HexString.toString((short)deviceDescriptor.idProduct));
        }
        
        // unconfigure device in case it was still configured from a prior terminated process
//        gUsbIo.unconfigureDevice();
        try {
            int status2;
    //        System.out.println("CypressFX2RetinaBiasgen.unconfigureDevice()");
            status2 = gUsbIo.unconfigureDevice();
            if (status2 != USBIO_ERR_SUCCESS) {
                UsbIo.destroyDeviceList(gDevList);
                //System.out.println("unconfigureDevice: "+UsbIo.errorText(status2));
                //            throw new USBAEMonitorException("getStringDescriptor: "+gUsbIo.errorText(status2));
                throw new HardwareInterfaceException("unconfigureDevice: " + UsbIo.errorText(status2));
            //            System.out.println("getConfigurationInfo ok");
            }
        } catch (HardwareInterfaceException e) {
            log.warning("can't unconfigure,will try simulated disconnect");
            int cycleStatus = gUsbIo.cyclePort();
            if (cycleStatus != USBIO_ERR_SUCCESS) {
                throw new HardwareInterfaceException("Error cycling port: " + UsbIo.errorText(cycleStatus));
            }
            throw new HardwareInterfaceException("couldn't unconfigure device");
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
            log.warning("setting configuration: "+UsbIo.errorText(status));
        }
        
        // get device descriptor
        status = gUsbIo.getDeviceDescriptor(deviceDescriptor);
        
       isOpened=true;

       if (reader==null) {
           reader=new MotionReader();
            reader.startThread(3); // start with 3 errors allowed
        } else {
           log.warning("MotionReader was still running !");
           reader.resetPipe();
        }
     }
    
    
    
    
    /** return the string USB descriptors for the device
     *@return String[] of length 2 of USB descriptor strings.
     */
    public String[] getStringDescriptors() {
        if(stringDescriptor1==null) {
            log.warning("USBAEMonitor: getStringDescriptors called but device has not been opened");
            String[] s=new String[numberOfStringDescriptors];
            for (int i=0;i<numberOfStringDescriptors;i++) {
                s[i]="";
            }
            return s;
        }
        String[] s=new String[numberOfStringDescriptors];
        s[0]=stringDescriptor1.Str;
        s[1]=stringDescriptor2.Str;
        if (numberOfStringDescriptors==3) {
            s[2]=stringDescriptor3.Str;
        }
        return s;
    }
    
    /** return the USB VID/PID of the interface
     *@return int[] of length 2 containing the Vendor ID (VID) and Product ID (PID) of the device. First element is VID, second element is PID.
     */
    public int[] getVIDPID() {
        if(deviceDescriptor==null) {
            log.warning("USBAEMonitor: getVIDPID called but device has not been opened");
            return new int[2];
        }
        int[] n=new int[2];
        n[0]=deviceDescriptor.idVendor;
        n[1]=deviceDescriptor.idProduct;
        return n;
    }
    
    
    public short getVID() {
        if(deviceDescriptor==null) {
            log.warning("USBAEMonitor: getVID called but device has not been opened");
            return 0;
        }
        // int[] n=new int[2]; n is never used
        return (short)deviceDescriptor.idVendor;
    }
    
    public short getPID() {
        if(deviceDescriptor==null) {
            log.warning("USBAEMonitor: getPID called but device has not been opened");
            return 0;
        }
        return (short)deviceDescriptor.idProduct;
    }
    
    /** @return bcdDevice (the binary coded decimel device version */
    public short getDID() { // this is not part of USB spec in device descriptor.
        return (short)deviceDescriptor.bcdDevice;
    }
    
    /** reports if interface is {@link #open}.
     * @return true if already open
     */
    public boolean isOpen() {
        return isOpened;
    }
    

    
    /** sends a vender request without any data. Thread safe.
     *@param request the vendor request byte, identifies the request on the device
     *@param value the value of the request (bValue USB field)
     *@param index the "index" of the request (bIndex USB field)
     */
    synchronized public void sendVendorRequest(byte request, short value, short index) throws HardwareInterfaceException {
        sendVendorRequest(request, value, index, null);
    }
    
    /** sends a vender request with data. Thread-safe.
     *@param request the vendor request byte, identifies the request on the device
     *@param value the value of the request (bValue USB field)
     *@param index the "index" of the request (bIndex USB field)
     *@param dataBuffer the data which is to be transmitted to the device
     */
    synchronized public void sendVendorRequest(byte request, short value, short index, USBIO_DATA_BUFFER dataBuffer) throws HardwareInterfaceException {
        if (!isOpen()) {
            throw new HardwareInterfaceException("Tried to send vendor request but device not open");
        }
        
        // make vendor request structure and populate it
        USBIO_CLASS_OR_VENDOR_REQUEST VendorRequest=new USBIO_CLASS_OR_VENDOR_REQUEST();
        int status;
        
        VendorRequest.Flags=UsbIoInterface.USBIO_SHORT_TRANSFER_OK;
        VendorRequest.Type=UsbIoInterface.RequestTypeVendor;
        VendorRequest.Recipient=UsbIoInterface.RecipientDevice;
        VendorRequest.RequestTypeReservedBits=0;
        VendorRequest.Request= request;
        VendorRequest.Index= index;
        VendorRequest.Value= value;
        
        //System.out.println("request= " + request + " value: " + value);
        
        if (dataBuffer==null) {
            dataBuffer=new USBIO_DATA_BUFFER(0);
            dataBuffer.setNumberOfBytesToTransfer(dataBuffer.Buffer().length);
        } else {
            dataBuffer.setNumberOfBytesToTransfer(dataBuffer.Buffer().length);
        }
        
        status=gUsbIo.classOrVendorOutRequest(dataBuffer,VendorRequest);
        
        if(status!=USBIO_ERR_SUCCESS){
            throw new HardwareInterfaceException("Unable to send vendor request "+ request + ": " + UsbIo.errorText(status));
        }
        
        HardwareInterfaceException.clearException();
    }
    
    
    /** the concurrent object to exchange data between rendering and the MotionReader capture thread */
    Exchanger<MotionData> exchanger = new Exchanger();
    MotionData initialEmptyBuffer=chip.getEmptyMotionData(); // the buffer to start capturing into
    MotionData initialFullBuffer=chip.getEmptyMotionData();    // the buffer to render/process first
    MotionData currentBuffer=initialFullBuffer;

    @Override
    public JPanel getConfigPanel() {
        return null;
    }

    @Override
    public int getRawDataIndex(int bit)
    {
        switch(bit)
        {
            case MotionDataMDC2D.PHOTO:         return 0;
            case MotionDataMDC2D.LMC1:          return 1;
            case MotionDataMDC2D.LMC2:          return 2;
            case MotionDataMDC2D.ON_CHIP_ADC:   return 3;

            default: return -1;
        }
    }

    @Override
    public void setChannel(int bit, boolean onChip) throws HardwareInterfaceException {
        // since we always send LMC1+LMC2+PHOTO we only need to set the
        // on-chip-ADC channel if on-chip-ADC values are wanted
        
        if (onChip && bit == MotionDataMDC2D.PHOTO)
            sendVendorRequest(VENDOR_REQUEST_SET_DATA_TO_SEND,(short)0x0d,(short)0); // 1101b
        if (onChip && bit == MotionDataMDC2D.LMC1)
            sendVendorRequest(VENDOR_REQUEST_SET_DATA_TO_SEND,(short)0x0b,(short)0); // 1011b
        if (onChip && bit == MotionDataMDC2D.LMC2)
            sendVendorRequest(VENDOR_REQUEST_SET_DATA_TO_SEND,(short)0x07,(short)0); // 0111b
    }


    /**
     * This reader reads data from the motion chip
     */
    protected class MotionReader extends UsbIoReader implements UsbIoErrorCodes{
        MotionData currentBuffer;
        int sequenceNumber=0;
//        UsbIoBuf buf=new UsbIoBuf(MotionData.getLength()*2);
        
        private int NUM_MOTION_BUFFERS=2;
        
        public MotionReader() throws HardwareInterfaceException {
            int status;
            status = bind(0,ENDPOINT_IN, gDevList, GUID); // device has already been opened so we don't need all the params
            if (status != USBIO_ERR_SUCCESS) {
                throw new HardwareInterfaceException("can't bind pipe: "+UsbIo.errorText(status));
            }
            
            USBIO_PIPE_PARAMETERS pipeParams=new USBIO_PIPE_PARAMETERS();
            pipeParams.Flags=UsbIoInterface.USBIO_SHORT_TRANSFER_OK;
            status=setPipeParameters(pipeParams);
            if (status != USBIO_ERR_SUCCESS) {
                throw new HardwareInterfaceException("can't set pipe parameters: "+UsbIo.errorText(status));
            }
            allocateBuffers(MOTION_BUFFER_LENGTH,NUM_MOTION_BUFFERS); // 16k buffers times 2
            currentBuffer=initialEmptyBuffer;
        }
        
        /** Starts the thread running with some tolerated error count and sends a vendor request to start streaming data
         @param i the number of errors before throwing exception
         */
        public void startThread(int i) {
            super.startThread(i);
            log.info("started MotionReader thread");
            try{
                sendVendorRequest(VENDOR_REQUEST_START_STREAMING, (short)0, (short)0);
            }catch(HardwareInterfaceException e){
                log.warning(e.getMessage());
            }
            
        }
        
        /** called to prepare buffer for capture */
        public void processBuffer(UsbIoBuf usbIoBuf) {
            usbIoBuf.NumberOfBytesToTransfer = usbIoBuf.Size;
            usbIoBuf.BytesTransferred = 0;
            usbIoBuf.OperationFinished = false;
//            log.info("processed buffer "+usbIoBuf);


            /* the current buffer will be exchanged. Before it is copied and the
             * MotionData is put into the Array which keeps track of the past
             * MotionData. The oldest one is disposed. The Array now contains all
             * older Data. After getting new Data from the exchanger the Array of
             * the new Buffer is filled with the one which contains all data.
             */
            lastbuffer = currentBuffer.clone(); // copy current buffer
            try {
                currentBuffer = exchanger.exchange(currentBuffer); // on the first call, the main rendering loop should probably already have called exchange via the getData() method
                requestData();
                lastbuffer.setLastMotionData(lastbuffer); // put the lastBuffer itself to the newest Position of the PastMotionData Array
                currentBuffer.setPastMotionData(lastbuffer.getPastMotionData()); //set the updated Array in the current buffer.
            } catch (InterruptedException ex) {
            }
        }
        
        /** called when xfer is finished */
        public void processData(UsbIoBuf usbIoBuf) {
//            log.info("received UsbIoBuf from device");
            unpackData(usbIoBuf, currentBuffer);
            
        }
        
        int[] buf;
        
        /** unpacks ADC data into float values ranging 0-1 */
        void unpackData(UsbIoBuf usbBuf, MotionData motionBuf) {
            if(buf==null || buf.length!=usbBuf.BytesTransferred*2){
                buf= new int[usbBuf.BytesTransferred * 2];
            }
            int count = 0;
            int i=0;
            byte bitOffset=0; //for byte unpacking
            int a, b=0; //for byte unpacking
            int posX=0, posY=0;
            byte packetDescriptor = usbBuf.BufferMem[1];
            
            try{
                if( usbBuf.BufferMem[0] != FRAME_START_MARKER) {
                    log.warning("Frame start marker does not match, unpacking failed");
                    return;
                }
                motionBuf.setContents(packetDescriptor);
                motionBuf.setSequenceNumber(sequenceNumber++);
                motionBuf.setTimeCapturedMs(System.currentTimeMillis());
                
                // unpack contents into tmp buf shifting 10 bit adc results into int format
                for(i = 2; i < usbBuf.BytesTransferred;i++) {
                    a = usbBuf.BufferMem[i];
                    b = usbBuf.BufferMem[i+1];
                    if(a<0) {
                        a = (a & 0x7F)+ 0x80; // Make it unsigned
                    }
                    if(b<0) {
                        b = (b & 0x7F)+ 0x80;
                    }
                    
                    // count keeps track of total data items in buffer
                    buf[count] = ((a<<(2+bitOffset))&0x3FF) | ((b>>>(6-bitOffset))&0xFF);
                   // buf[count] = buf[count] >> 2; //FOR DEBUGGING ONLY!
                    count++;
                    bitOffset += 2;
                    if(bitOffset == 8) {
                        bitOffset=0;
                        i++;
                    }
                    if(buf[count]<0) {
                        log.warning("sign error while unpacking");
                    }
                }
                
                // now unpack contents of buf into MotionData
                // all computations related to display of raw ADC values (here scaled to float 0-1 range) are done in
                // OpticalFlowDisplayMethod
                i=0;
                /* first write all global data (needs to be sent first) to the
                 * globalRaw array of MotionData
                 */
                float[]globalRaw = motionBuf.getRawDataGlobal();
                for(int j=0; j<motionBuf.getNumGlobalChannels();j++){
                    globalRaw[i]=chip.convert10bitToFloat(buf[i]);
                    i++;
                }
                motionBuf.setRawDataGlobal(globalRaw);

                /* Now write all channels read for each pixel to a 3D array of type
                 *  [channel][posX][posY]
                 */

                posX=0;
                posY=0;
                float[][][] pixelRaw= motionBuf.getRawDataPixel();
                while(i < count) {
                    for(int j=0; j<motionBuf.getNumLocalChannels();j++){
                        pixelRaw[j][posY][posX] = chip.convert10bitToFloat(buf[i]);
                        i++;
                    }
                    posX++;
                    if(posX==chip.NUM_COLUMNS) {
                        posX=0;
                        posY++;
                        if(posY==chip.NUM_ROWS && i < count )
                            log.warning("position y too big while unpacking");
                    }
                }
                motionBuf.setRawDataPixel(pixelRaw);

            }catch(ArrayIndexOutOfBoundsException e){
                log.warning(e.getMessage());
            }
            motionBuf.collectMotionInfo(); //this computes the motionData for display depending on the MotionData subclass
        }
        
        
        @Override
        public void bufErrorHandler(UsbIoBuf usbIoBuf) {
            log.warning("bufferError: "+UsbIo.errorText(usbIoBuf.Status));
// prevent deadlock
//            SiLabsC8051F320_OpticalFlowHardwareInterface.this.close();
        }
        
        @Override
        public void onThreadExit() {
            try{
                sendVendorRequest(VENDOR_REQUEST_STOP_STREAMING, (short)0, (short)0);
            }catch(HardwareInterfaceException e){
                log.warning(e.getMessage());
            }
// prevent deadlock
//            if(isOpen()) close(); // these call the UsbIo methods, not the containing class methods - TODO check if this is OK
        }
        
        void requestData(){
            // no need to do anything because streaming is enabled when the reader is started.
        }
    }
    
    /** returns MotionData from the device
     * @return MotionData for one frame
     @throws TimeOutException when request for exchange with Reader thread times out. Timeout duration is set by DATA_TIMEOUT_MS.
     */
    @Override
    public MotionData getData() throws java.util.concurrent.TimeoutException {
        try{ 
            currentBuffer=exchanger.exchange(currentBuffer,DATA_TIMEOUT_MS,TimeUnit.MILLISECONDS);
            return currentBuffer;
        }catch(InterruptedException e){
            return null;
        }catch(java.util.concurrent.TimeoutException to){
            throw new TimeoutException("didn't get data after "+DATA_TIMEOUT_MS+" ms");
        }
    }
    
    @Override
    public void setPowerDown(boolean powerDown) throws HardwareInterfaceException {
        int pd;
        if(powerDown){
            pd=1;
        }else{
            pd=0;
        }
        this.sendVendorRequest(VENDOR_REQUEST_SET_POWERDOWN_STATE, (short)pd, (short)0);
    }
    
    /** sends the pot values, but uses a local cache to only send those values that have changed
     * @param biasgen the biasgen we are sending for
     */
    @Override
    public void sendConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        
        PotArray potArray=biasgen.getPotArray();
        if(vpotValues==null){
            vpotValues=new int[MAX_POTS];
            for(int i=0;i<vpotValues.length;i++){
                vpotValues[i]=-1; // init values to value that will generate a vendor request for it automatically.
            }
        }
        
        for(short i=0;i<biasgen.getNumPots();i++){
            VPot vpot=(VPot)potArray.getPotByNumber(i);
            int chan=vpot.getChannel(); // DAC channel for pot
            if(vpotValues[chan]!=vpot.getBitValue()){
                // new value or not sent yet, send it
                sendVendorRequest(VENDOR_REQUEST_SEND_BIAS, (short)vpot.getBitValue(), (short)chan);
                vpotValues[chan]=vpot.getBitValue();
                log.info("set VPot value "+vpot.getBitValue()+" ("+vpot.getPhysicalValue()+vpot.getPhysicalValueUnits()+") for channel "+chan);
            }
        }           

        if(ipotValues==null){
            ipotValues=new int[38];
            for(int i=0;i<ipotValues.length;i++){
                ipotValues[i]=-1; // init values to value that will generate a vendor request for it automatically.
            }
        }

        PotArray ipots= ((MDC2D.MDC2DBiasgen) biasgen).getIPotArray();
        for(short i=0;i<ipots.getNumPots();i++){
            IPot ipot=(IPot)ipots.getPotByNumber(i);
            int chan=ipot.getShiftRegisterNumber();
            if(ipotValues[chan]!=ipot.getBitValue()){
                // new value or not sent yet, send it
                ipotValues[chan]=ipot.getBitValue();
                byte[] bin =ipot.getBinaryRepresentation();

                byte request= VENDOR_REQUEST_SEND_ONCHIP_BIAS;
                short value = (short)(((chan<<8)&0xFF00)| ((bin[0])&0x00FF));
                short index = (short)(((bin[1]<<8)&0xFF00) | (bin[2]&0x00FF));
                sendVendorRequest(request, value,index);//value, index);
                log.info("set IPot value "+ipot.getBitValue()+" ("+ipot.getPhysicalValue()+ipot.getPhysicalValueUnits()+") into SR pos "+chan);
            }
        }
    }
    
    @Override
    public void flashConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        log.warning("not implemented yet");
    }


    @Override
    public void setCaptureMode(int mode) throws HardwareInterfaceException {

        generateMotionData();
        
        /*
        if(!isOpen()) {
            log.warning("device not open");
            return;
        }

        // we can only provide certain capture modes -> reset capture mode in
        // chip since it will define number of arrays in MotionData !!
        int oldMode= mode;
        mode &= MotionDataMDC2D.PHOTO | MotionDataMDC2D.LMC1 | MotionDataMDC2D.LMC2 | MotionDataMDC2D.ON_CHIP_ADC;
        mode |= MotionDataMDC2D.PHOTO | MotionDataMDC2D.LMC1 | MotionDataMDC2D.LMC2;

        // it sends all data in any case
        try {
            sendVendorRequest(VENDOR_REQUEST_SET_DATA_TO_SEND,(short)captureMode,(short)0);
        } catch (HardwareInterfaceException ex) {
            log.warning("could not set captureMode : " + ex);
        }

        if ((mode & MotionDataMDC2D.ON_CHIP_ADC) != 0)
        {
            if ((mode & MotionDataMDC2D.PHOTO) != 0)
                sendVendorRequest(VENDOR_REQUEST_SET_DATA_TO_SEND,(short)0x0d,(short)0); // 1101b
            if ((mode & MotionDataMDC2D.LMC1) != 0)
                sendVendorRequest(VENDOR_REQUEST_SET_DATA_TO_SEND,(short)0x0b,(short)0); // 1011b
            if ((mode & MotionDataMDC2D.LMC2) != 0)
                sendVendorRequest(VENDOR_REQUEST_SET_DATA_TO_SEND,(short)0x07,(short)0); // 0111b
        }
        this.captureMode=mode;
         */
    }


    @Override
    public byte[] formatConfigurationBytes(Biasgen biasgen) {
        return null; // each bias is handled independently for this kind of off-chip, channel-addressable DAC
    }

     /** get text name of interface, e.g. "CypressFX2" or "SiLabsC8051F320" */
    @Override
    public String getTypeName(){
        return "SiLabsC8051F320";
    }
    
    
}
