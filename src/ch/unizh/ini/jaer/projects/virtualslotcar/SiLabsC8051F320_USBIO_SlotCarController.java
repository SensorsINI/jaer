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

package ch.unizh.ini.jaer.projects.virtualslotcar;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Logger;

import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.ServoTest;
import net.sf.jaer.hardwareinterface.usb.UsbIoUtilities;
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
 * The USB slot car controller board is controlled by this class.
 * Use this class by making new SiLabsC8051F320_USBIO_SlotCarController.
 * <p>
 * Slot car motor controller using USBIO driver access to SiLabsC8051F320 device. To prevent blocking on the thread controlling the
 *servo, this class starts a consumer thread that communicates with the USB interface. The producer (the user) communicates with the
 *consumer thread using an ArrayBlockingQueue. Therefore servo commands never produce hardware exceptions; these are caught in the consumer
 *thread by closing the device, which should be reopened on the next command.
 * <p>
 * This class goes with the USB slot car controller board.
 * <p>
 * This devices shares the USBIO driver and VID/PID with the USBServoController board.
 * @author tobi
 */
public class SiLabsC8051F320_USBIO_SlotCarController implements UsbIoErrorCodes, PnPNotifyInterface {
    
    static final Logger log=Logger.getLogger("SiLabsC8051F320_USBIO_SlotCarController");
    
    /** driver GUID (Globally unique ID, for this USB driver instance */
    public final static String GUID  = "{3B15398D-1EF2-44d7-A6B8-74A3FCCD29BF}"; // tobi generated in pasadena july 2006
    
    /** The vendor ID */
    static public final short VID=(short)0x0547;
    
    /** The product ID */
    static public final short PID=(short)0x8750;
    
    final static short CONFIG_INDEX                       = 0;
    final static short CONFIG_NB_OF_INTERFACES            = 1;
    final static short CONFIG_INTERFACE                   = 0;
    final static short CONFIG_ALT_SETTING                 = 0;
    final static int CONFIG_TRAN_SIZE                     = 64;
    
    // out endpoint for servo commands
    final static int ENDPOINT_OUT=0x02;
    
    /** length of endpoint, ideally this value should be obtained from the pipe bound to the endpoint but we know what it is for this
     * device. It is set to 16 bytes to minimize transmission time. At 12 Mbps, 16 bytes+header (13 bytes)=140 bits requires about 30 us to transmit.
     */
    public final static int ENDPOINT_OUT_LENGTH=0x10;
 
        /** The board can control this many servos */
    public static int NUM_SERVOS=4;

    PnPNotify pnp=null;
    
    private boolean isOpened;
    
    private float[] lastServoValues=new float[NUM_SERVOS];
    
//    UsbIoPipe outPipe=null; // the pipe used for writing to the device
    
    /** number of servo commands that can be queued up. It is set to a small number so that comands do not pile up. If the queue
     * is full when a command is given, then the old commands are discarded so that the latest command is next to be processed.
     * Note that this policy can have drawbacks - if commands are sent to different servos successively, then new commands can wipe out commands
     * to older commands to set other servos to some position.
     */
    public static final int SERVO_QUEUE_LENGTH=20;
    
    UsbCommandWriterThread servoCommandWriter=null; // this worker thread asynchronously writes to device
    private volatile ArrayBlockingQueue<USBCommand> servoQueue; // this queue is used for holding servo commands that must be sent out.
    
    /** the device number, out of all potential compatible devices that could be opened */
    protected int interfaceNumber=0;
    
    
    private final int SYSCLK_MHZ=12; // this is sysclock of SiLabs
    private float pcaClockFreqMHz=SYSCLK_MHZ/2; // runs at 6 MHz by default with timer0 reload value of 255-1
    
    private boolean fullDutyCycleModeEnabled=false;  // reset state is false;

    /**
     * Creates a new instance of SiLabsC8051F320_USBIO_ServoController using device 0 - the first
     * device in the list.
     */
    public SiLabsC8051F320_USBIO_SlotCarController() {
        interfaceNumber = 0;
        UsbIoUtilities.enablePnPNotification(this, GUID);
        Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
            public void run(){
                if(isOpen()){
                    close();
                }
            }
        });
        servoQueue=new ArrayBlockingQueue<USBCommand>(SERVO_QUEUE_LENGTH);
    }
    
    /** Creates a new instance of USBAEMonitor. Note that it is possible to construct several instances
     * and use each of them to open and read from the same device.
     *@param devNumber the desired device number, in range returned by CypressFX2Factory.getNumInterfacesAvailable
     */
    public SiLabsC8051F320_USBIO_SlotCarController(int devNumber) {
        this();
        this.interfaceNumber=devNumber;
    }
    
    public void onAdd() {
        log.info("SiLabsC8051F320_USBIO_ServoController: device added");
    }
    
    public void onRemove() {
        log.info("SiLabsC8051F320_USBIO_ServoController: device removed");
        close();
    }
    
    /** Closes the device. Never throws an exception.
     */
    public void close(){
        if(!isOpened){
            log.warning("close(): not open");
            return;
        }
        
        if(servoCommandWriter!=null) {
//            log.info("zeroing all throttles and turning off brakes");
//            disableAllServos();
//            try{
//                Thread.sleep(100);
//            }catch(InterruptedException e){
//
//            }
            servoCommandWriter.shutdownThread();
        }
        servoCommandWriter.close(); // unbinds pipes too
        if(gUsbIo!=null) gUsbIo.close();
        UsbIo.destroyDeviceList(gDevList);
        log.info("device closed");
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
    
    /**
     * This method does the hard work of opening the device, downloading the firmware, making sure everything is OK.
     * This method is synchronized to prevent multiple threads from trying to open at the same time, e.g. a GUI thread and the main thread.
     *
     * Opening the device after it has already been opened has no effect.
     *
     * @see #close
     *@throws HardwareInterfaceException if there is a problem. Diagnostics are printed to stderr.
     */
    public void open() throws HardwareInterfaceException {
        if(!UsbIoUtilities.isLibraryLoaded()) return;
        
        //device has already been UsbIo Opened by now, in factory
        
        // opens the USBIOInterface device, configures it, binds a reader thread with buffer pool to read from the device and starts the thread reading events.
        // we got a UsbIo object when enumerating all devices and we also made a device list. the device has already been
        // opened from the UsbIo viewpoint, but it still needs firmware download, setting up pipes, etc.
        
        if(isOpened){
            log.warning("already opened interface and setup device");
            return;
        }
        
        int status;
        
        gUsbIo=new UsbIo();
        gDevList=UsbIo.createDeviceList(GUID);
        status = gUsbIo.open(0,gDevList,GUID);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("can't open USB device: "+UsbIo.errorText(status));
        }
        
        // get device descriptor
        status = gUsbIo.getDeviceDescriptor(deviceDescriptor);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("getDeviceDescriptor: "+UsbIo.errorText(status));
        } else {
            log.info("getDeviceDescriptor: Vendor ID (VID) "
                    + HexString.toString((short)deviceDescriptor.idVendor)
                    + " Product ID (PID) " + HexString.toString((short)deviceDescriptor.idProduct));
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
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("getDeviceDescriptor: "+UsbIo.errorText(status));
        } else {
            log.info("getDeviceDescriptor: Vendor ID (VID) "
                    + HexString.toString((short)deviceDescriptor.idVendor)
                    + " Product ID (PID) " + HexString.toString((short)deviceDescriptor.idProduct));
        }
        
        if (deviceDescriptor.iSerialNumber!=0)
            this.numberOfStringDescriptors=3;
        
        // get string descriptor
        status = gUsbIo.getStringDescriptor(stringDescriptor1,(byte)1,0);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("getStringDescriptor: "+UsbIo.errorText(status));
        } else {
            log.info("getStringDescriptor 1: " + stringDescriptor1.Str);
        }
        
        // get string descriptor
        status = gUsbIo.getStringDescriptor(stringDescriptor2,(byte)2,0);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("getStringDescriptor: "+UsbIo.errorText(status));
        } else {
            log.info("getStringDescriptor 2: " + stringDescriptor2.Str);
        }
        
        if (this.numberOfStringDescriptors==3) {
            // get serial number string descriptor
            status = gUsbIo.getStringDescriptor(stringDescriptor3,(byte)3,0);
            if (status != USBIO_ERR_SUCCESS) {
                UsbIo.destroyDeviceList(gDevList);
                throw new HardwareInterfaceException("getStringDescriptor: "+UsbIo.errorText(status));
            } else {
                log.info("getStringDescriptor 3: " + stringDescriptor3.Str);
            }
        }
        
        // get outPipe information and extract the FIFO size
        USBIO_CONFIGURATION_INFO configurationInfo = new USBIO_CONFIGURATION_INFO();
        status = gUsbIo.getConfigurationInfo(configurationInfo);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("getConfigurationInfo: "+UsbIo.errorText(status));
        }
        
        if(configurationInfo.NbOfPipes==0){
//            gUsbIo.cyclePort();
            throw new HardwareInterfaceException("didn't find any pipes to bind to");
        }
        
        servoCommandWriter=new UsbCommandWriterThread();
        status=servoCommandWriter.bind(0,(byte)ENDPOINT_OUT,gDevList,GUID);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("can't bind command writer to endpoint: "+UsbIo.errorText(status));
        }
        USBIO_PIPE_PARAMETERS pipeParams=new USBIO_PIPE_PARAMETERS();
        pipeParams.Flags=UsbIoInterface.USBIO_SHORT_TRANSFER_OK;
        status=servoCommandWriter.setPipeParameters(pipeParams);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("startAEWriter: can't set pipe parameters: "+UsbIo.errorText(status));
        }
        
        servoCommandWriter.startThread(3);
        isOpened=true;
        submittedCmdAfterOpen=false;
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
            log.warning("USBAEMonitor: getVIDPID called but device has not been opened");
            return 0;
        }
        // int[] n=new int[2]; n is never used
        return (short)deviceDescriptor.idVendor;
    }
    
    public short getPID() {
        if(deviceDescriptor==null) {
            log.warning("USBAEMonitor: getVIDPID called but device has not been opened");
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
    
    /* *********************************************************************************************** /
     
     /*
        // define command codes
        #define CMD_SET_SERVO 7
        #define CMD_DISABLE_SERVO 8
        #define CMD_SET_ALL_SERVOS 9
        #define CMD_DISABLE_ALL_SERVOS 10
     */
    
    /** servo command bytes recognized by microcontroller, defined in F32x_USB_Main.c on firmware
     *
     */
    private static final int CMD_SET_THROTTLE=7,
            CMD_DISABLE_SERVO=8, 
            CMD_SET_ALL_SERVOS=9, 
            CMD_DISABLE_ALL_SERVOS = 10,
            CMD_SET_TIMER0_RELOAD_VALUE = 11,
            CMD_SET_PORT2 = 12,
            CMD_SET_PORT_DOUT=14,
            CMD_SET_PCA0MD_CPS=15,
            CMD_SET_BRAKE=16;

    public int getNumServos() {
        return NUM_SERVOS;
    }
    
    
    public String getTypeName() {
        return "SlotCarController";
    }

    private boolean submittedCmdAfterOpen=false; // flag that is set once a command has been sent after open.
    private USBCommand lastCmd=null;
    
    /** Submits the command to the writer thread queue that sends them to the device
     @param cmd the command, which consists of bytes sent to the device.
     */
    protected void submitCommand(USBCommand cmd){
        if(cmd==null){
            log.warning("null cmd submitted to servo command queue");
            return;
        }
        if(submittedCmdAfterOpen && cmd.equals(lastCmd)){
            return; // don't just duplicate command already sent since open
        }
        if(!servoQueue.offer(cmd)){ // if queue is full, just clear it and replace with latest command
            servoQueue.clear();
            servoQueue.offer(cmd);
            submittedCmdAfterOpen=true;
            log.warning("cleared queue to submit latest command");
        }
        lastCmd=cmd;
        Thread.yield(); // let writer thread get it and submit a write
    }

    /** Returns last servo values sent.These are in order of PCA outputs on the SiLabs chip, which are opposite the labeling on the board. */
    public float[] getLastServoValues() {
        return lastServoValues;
    }
    
    /** Returns last servo value sent (0 before sending a value) */
    public float getLastServoValue(int servo){
        return lastServoValues[getPCAChannelNumber(servo)];
    }

    public void setFullDutyCycleMode (boolean yes){
        this.fullDutyCycleModeEnabled=yes;
    }

    public boolean isFullDutyCycleMode (){
        return fullDutyCycleModeEnabled;
    }
    
    /** This thread actually talks to the hardware */
    private class UsbCommandWriterThread extends UsbIoWriter{
        
        // overridden to change priority
        @Override
        public void startThread(int MaxIoErrorCount) {
            allocateBuffers(ENDPOINT_OUT_LENGTH, 2);
            if (T == null) {
                MaxErrorCount = MaxIoErrorCount;
                T = new Thread(this);
                T.setPriority(Thread.MAX_PRIORITY); // very important that this thread have priority or the acquisition will stall on device side for substantial amounts of time!
                T.setName("ServoCommandWriter");
                T.start();
            }
        }
        
        
        /** waits and takes commands from the queue and submits them to the device.
         */
        public void processBuffer(UsbIoBuf servoBuf){
            USBCommand cmd=null;
            servoBuf.NumberOfBytesToTransfer=ENDPOINT_OUT_LENGTH; // must send full buffer because that is what controller expects for interrupt transfers
            servoBuf.OperationFinished=false; // setting true will finish all transfers and end writer thread
            servoBuf.BytesTransferred=0;
            try{
                cmd=servoQueue.take();  // wait forever until there is a command
            }catch(InterruptedException e){
                log.info("servo queue wait interrupted");
                T.interrupt(); // important to call again so that isInterrupted in run loop see that thread should terminate
            }
            if(cmd==null) {
                return;
            }
            System.arraycopy(cmd.bytes,0,servoBuf.BufferMem,0,cmd.bytes.length);
        }
        
        public void bufErrorHandler(UsbIoBuf usbIoBuf) {
            log.warning(UsbIo.errorText(usbIoBuf.Status));
            SiLabsC8051F320_USBIO_SlotCarController.this.close();
            isOpened=false;
        }
        
        public void onThreadExit() {
            log.info("servo command writer done");
        }
    }

    /** Checks if servo is not open and opens it
     * 
     * @return true if open
     */
    protected boolean checkUsbCommandThread(){
        try {
            if(!isOpen()) open();
            return true;
        } catch (HardwareInterfaceException ex) {
            log.warning(ex.toString());
            return false;
        }
    }
    
    private byte pwmValue(float value) {
        if (value < 0) {
            value = 0;
        } else if (value > 1) {
            value = 1;
        }
        byte val = (byte) (255 - (0xff & (int) (255 * value))); // value sent is the PWM output LOW time, so we subtract from 0xffff to send the proper value to get the desired HIGH duty cycle
        return val;
    }
    
    /** directly sends a particular short value to the PWM generator, bypassing conversion from float.
     * The value is subtracted from 65536 and written so that the value you write encodes the HIGH time of the
     * PWM pulse; 0=low always, 65535=high always.
     * @param servo the servo number
     * @param pwmValue the value written to servo controller is 64k minus this value
     */
    public void setPWMValue(int servo, int pwmValue) {
        pwmValue=255-pwmValue;
        checkUsbCommandThread();
        USBCommand cmd=new USBCommand();
        cmd.bytes=new byte[3];
        cmd.bytes[0]=CMD_SET_THROTTLE;
        cmd.bytes[1]=(byte)getPCAChannelNumber(servo);
        cmd.bytes[2]=(byte)((pwmValue)&0xff);
        submitCommand(cmd);
        
    }

    /** Sets the brake bits on the hardware. The bits are right aligned so that bit0 is brake for channel 0 and bit3 is brake for channel 3.
     * Other bits are masked out.
     *
     * @param brake true to activate brake
     */
    public void setBrake(int brake){
        checkUsbCommandThread();
        USBCommand cmd=new USBCommand();
        cmd.bytes=new byte[2];
        cmd.bytes[0]=CMD_SET_BRAKE;
        cmd.bytes[1]=(byte)brake;
        submitCommand(cmd);
    }

    /** Attempts to set the PWM output frequency;
     * assumes that setPCAClockSource has been called to
     * set clock source for PCA to Timer0 overflow clock source.
     *<p>
     *
     *@param freq the desired frequency in Hz. The actual value is returned.
     *@return the actual value or 0 if there is an error.
     */
    public float setPWMFreqHz(float freq){
        checkUsbCommandThread();
        if(freq<=0) {
            log.warning("freq="+freq+" is not a valid value");
            return 0;
        }
        // Timer0 is clocked by Sysclk=12MHz always.
        // Timer0 overflows after 255-reload cycles and this clocks the PCA counter.
        // The 8 bit PCA counter overflows after counting to 255.
        int n=Math.round(SYSCLK_MHZ*1e6f/256f/freq); //
        if(n==0) {
            log.warning("freq="+freq+" too high, setting max possible frequency.");
            n=1;
        }
        float freqActual=SYSCLK_MHZ*1e6f/256f/n; // n=1, we get 183Hz
        USBCommand cmd=new USBCommand();
        cmd.bytes=new byte[2];
        cmd.bytes[0]=CMD_SET_TIMER0_RELOAD_VALUE;
        cmd.bytes[1]=(byte)(n-1); // now we use n-1 to give us a reload value of 0 for max freq
        submitCommand(cmd);
        pcaClockFreqMHz=SYSCLK_MHZ/n;
        return freqActual;
    }
    
    /** This board is correctly labeled and doesn't require correction.
     * 
     * @param throttle is the labeled output port
     * @return the actual servo number
     */
    public byte getPCAChannelNumber(int throttle){
        return (byte)throttle;
    }
    
    /** sets servo position. The float value is translated to a value that is written to the device thar results in s pulse width
     * that varies from 0.9 ms to 2.1 ms.
     * @param throttle the servo motor, 0 based
     * @param value the value from 0 to 1. Values out of these bounds are clipped. Special value -1f turns off the servos.
     */
    public void setThrottleValue(int throttle, float value){
        checkUsbCommandThread();
        // the message consists of
        // msg header: the command code (1 byte)
        // servo to control, 1 byte
        // servo PWM PCA capture-compare register value, 2 bytes, this encodes the LOW time of the PWM output
        // 				this is send MSB, then LSB (big endian)
        USBCommand cmd=new USBCommand();
        cmd.bytes=new byte[3];
        cmd.bytes[0]=CMD_SET_THROTTLE;
        cmd.bytes[1]=(byte)getPCAChannelNumber(throttle);
        byte b=pwmValue(value);
        cmd.bytes[2]=b;
        submitCommand(cmd);
        lastServoValues[getPCAChannelNumber(throttle)]=value;
    }
    
    public void disableAllServos() {
        checkUsbCommandThread();
        USBCommand cmd=new USBCommand();
        cmd.bytes=new byte[1];
        cmd.bytes[0]=CMD_DISABLE_ALL_SERVOS;
        submitCommand(cmd);
    }
    
    /** sends a servo value to disable the servo
     * @param servo the servo number, 0 based
     */
    public void disableServo(int servo) {
        checkUsbCommandThread();
        USBCommand cmd=new USBCommand();
        cmd.bytes=new byte[2];
        cmd.bytes[0]=CMD_DISABLE_SERVO;
        cmd.bytes[1]=(byte)getPCAChannelNumber(servo);
        submitCommand(cmd);
    }
    
    /** sets all servos to values in one transfer
     * @param values array of value, must have length of number of servos. 
     * Order of values is order given by getPCAChannelNumber(i), where i is the labeled output on the servo board.
     */
    public void setAllServoValues(float[] values)  {
        throw new UnsupportedOperationException("cannot set all servos, not supported for SlotCarController");
//        if(values==null || values.length!=getNumServos()) throw new IllegalArgumentException("wrong number of servo values, need "+getNumServos());
//        checkUsbCommandThread();
//        USBCommand cmd=new USBCommand();
//        cmd.bytes=new byte[1+getNumServos()*2];
//        cmd.bytes[0]=CMD_SET_ALL_SERVOS;
//        int index=1;
//        for(int i=0;i<getNumServos();i++){
//            byte[] b=pwmValue(values[getPCAChannelNumber(i)]); // must correct here for flipped labeling on PCB
//            cmd.bytes[index++]=b[0];
//            cmd.bytes[index++]=b[1];
//            lastServoValues[getPCAChannelNumber(i)]=values[i];
//        }
//        submitCommand(cmd);
    }
    
     /** sends a command to set the port 2 output (on the side of the original board) to portValue.
      * This port is presently set to open-drain mode on all bits.
     * @param portValue the bits to set
     */
    public void setPort2(int portValue) {
        checkUsbCommandThread();
        USBCommand cmd=new USBCommand();
        cmd.bytes=new byte[2];
        cmd.bytes[0]=CMD_SET_PORT2;
        cmd.bytes[1]=(byte)(0xff&portValue);
        submitCommand(cmd);
    }

    /** Sets the accessible ports PXMDOUT bits to set the port pins in either push-pull or open-drain configuration.
     * The first nibble of p1 are the servo S0-S3 bits (in reverse order), and p2 is the port on the side of the ServoUSB board.
     * p1.0=S3, p1.3=S0.  Setting the bit to 1 sets the port pin to push-pull and setting it to 0 sets it in open-drain.
     * If the pin is set to open drain mode, then writing a 1 to the port turns off the pull down.
     * <p>
     * The default (reset state) is that only the servo output pins are set to push-pull mode. P2 is set to open drain.
     *
     * @param p1 port 1 - the first nibble are the servo output pins in reverse order. The upper nibble is masked out to leave bits 4:7 in open drain mode.
     * @param p2 port 2 - on the side of the board.
     */
    public void setPortDOutRegisters(byte p1, byte p2){
          checkUsbCommandThread();
        USBCommand cmd=new USBCommand();
        cmd.bytes=new byte[3];
        cmd.bytes[0]=CMD_SET_PORT_DOUT;
        cmd.bytes[1]=(byte)(0x0f&p1);
        cmd.bytes[2]=(byte)(0xff&p2);
        submitCommand(cmd);

    }

    /** Sets the bits of the PCA register that controls PCA clock source.
     *
     * @param source the clock source, e.g. Sysclk
     */
    public void setPCAClockSource(PCA_ClockSource source){
        checkUsbCommandThread();
        USBCommand cmd=new USBCommand();
        cmd.bytes=new byte[2];
        cmd.bytes[0]=CMD_SET_PCA0MD_CPS;
        cmd.bytes[1]=(byte)(0x07&source.code());
        submitCommand(cmd);
    }

    /** The PCA counter/timer clock source. Sysclk is 12MHz. SysclkOver12 is 1MHz. SysclkOver4 is 3MHz.
     *
     * Timer0 is an 8 bit counter that overflows according to the Timer0 reload value; Timer0 is clocked by Sysclk=12MHz.
     */
    public enum PCA_ClockSource {

        SysclkOver12(0),
        SysclkOver4(1),
        Timer0Overflow(2),
        Sysclk(4);
        private final int code;

        PCA_ClockSource(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }
    };

    /** encapsulates the servo command bytes that are sent.
     The first byte is the command specifier, the rest of the bytes are the command itself.
     */
    public class USBCommand{
        public byte[] bytes;

        @Override
        public boolean equals(Object obj) {
            if(obj==null || !(obj instanceof USBCommand)) return false;
            byte[] otherBytes=((USBCommand) obj).bytes;
            if(otherBytes.length!=bytes.length) return false;
            for(int i=0;i<bytes.length;i++){
                if(bytes[i]!=otherBytes[i]) return false;
            }
            return true;
        }
    }
    
    
    
    /** Tests by making the testing GUI.
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new ServoTest().setVisible(true);
            }
        });
    }
    
}


