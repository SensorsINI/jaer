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

package ch.unizh.ini.jaer.projects.rccar;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.ServoInterface;
import net.sf.jaer.hardwareinterface.usb.ServoTest;
import net.sf.jaer.hardwareinterface.usb.UsbIoUtilities;
import net.sf.jaer.util.HexString;
import de.thesycon.usbio.PnPNotify;
import de.thesycon.usbio.PnPNotifyInterface;
import de.thesycon.usbio.UsbIo;
import de.thesycon.usbio.UsbIoBuf;
import de.thesycon.usbio.UsbIoErrorCodes;
import de.thesycon.usbio.UsbIoInterface;
import de.thesycon.usbio.UsbIoPipe;
import de.thesycon.usbio.structs.USBIO_CONFIGURATION_INFO;
import de.thesycon.usbio.structs.USBIO_PIPE_PARAMETERS;
import de.thesycon.usbio.structs.USBIO_SET_CONFIGURATION;
import de.thesycon.usbio.structs.USB_DEVICE_DESCRIPTOR;
import de.thesycon.usbio.structs.USB_STRING_DESCRIPTOR;

/**
 * Servo motor controller using USBIO driver access to SiLabsC8051F320 device for controlling car servos.
 It can control two servos (e.g. steering and speed) while the device monitors two other servos (e.g. steering and
 speed outputs from a radio receiver). When the device detects non-zero radio outputs, it blocks out the computer values and
 sends a status message to the host.
 
 * @author tobi
 */
public class SiLabsC8051F320_USBIO_CarServoController implements UsbIoErrorCodes, PnPNotifyInterface, ServoInterface {
    static final Logger log=Logger.getLogger("SiLabsC8051F320_USBIO_ServoController");
    
    /** driver GUID (Globally unique ID, for this USB driver instance */
    public final static String GUID  = "{06A57244-C56B-4edb-892B-2ADABFB35E0B}"; // tobi generated in pasadena july 2006
    
    static public final short VID=(short)0x0547;
    static public final short PID=(short)0x8751;
    
    final static short CONFIG_INDEX                       = 0;
    final static short CONFIG_NB_OF_INTERFACES            = 1;
    final static short CONFIG_INTERFACE                   = 0;
    final static short CONFIG_ALT_SETTING                 = 0;
    final static int CONFIG_TRAN_SIZE                     = 64;
    
    
    final static int ENDPOINT_OUT=0x02; // out endpoint for servo commands
    
    final static int ENDPOINT_IN=0x81; // in endpoint for status messages
    
    // length of endpoint, ideally this value should be obtained from the pipe bound to the endpoint but we know what it is
    final static int ENDPOINT_OUT_LENGTH=0x10, ENDPOINT_IN_LENGTH=8;
    
    PnPNotify pnp=null;
    
    private boolean isOpened;
    
    UsbIoPipe outPipe=null; // the pipe used for writing to the device
    
    AsyncStatusThread statusThread=null;
    
    /** number of servo commands that can be queued up */
    final int SERVO_QUEUE_LENGTH=30;
    
    ServoCommandThread servoCommandThread=null;
    
    private final int C8051F320_SYSCLK_MHZ=12; // processer clock on C8051F320 in MHZ
    private final int C8051F320_PCA_COUNTER_CLK_MHZ=C8051F320_SYSCLK_MHZ/3; // PCA counter runs at 4 MHz on this firmware to give 61Hz servo update rate
    private final int C8051F320_PCA_ZERO_SERVO_COUNT=1500*C8051F320_PCA_COUNTER_CLK_MHZ; // 6000
    private final int C8051F320_PCA_SERVO_FULL_RANGE=1000*C8051F320_PCA_COUNTER_CLK_MHZ; // 4000 = 1ms
    
    private final int C8051F320_MAIN_CYCLE_TIME_US=9; // measured empirically, cycle time in us through main loop
    
    private float[] externalServoValues={0.5f, 0.5f}; // latest values read from S2, S3 pins (radio receiver) sent to host
    
    // servo channel
    public static final int STEERING_SERVO=0, SPEED_SERVO=1;
    
    private float deadzoneForSpeed=0.1f; // default 0.1 of full scale on each side of radio zero
    
    private float deadzoneForSteering=0.1f; // default 0.1 of full scale on each side of zero
    
    private int radioTimeoutMs=1000; // default 1 second timeout
    
    // external (radio receiver) channel
    public static final int RADIO_STEER=1, RADIO_SPEED=0;
 
    /** the device number, out of all potential compatible devices that could be opened */
    protected int interfaceNumber=0;

    /**
     * Creates a new instance of SiLabsC8051F320_USBIO_ServoController
     @param n the interface number
     */
    public SiLabsC8051F320_USBIO_CarServoController(int n) {
        interfaceNumber=n;
    }
    
    /**
     * Creates a new instance of SiLabsC8051F320_USBIO_ServoController for interface number 0
     */
    public SiLabsC8051F320_USBIO_CarServoController() {
    }
    
    @Override
    public void onAdd() {
        log.info("SiLabsC8051F320_USBIO_ServoController: device added");
    }
    
    @Override
    public void onRemove() {
        log.info("SiLabsC8051F320_USBIO_ServoController: device removed");
    }
    
    /** Closes the device. Never throws an exception.
     */
    @Override
    synchronized public void close(){
        if(!isOpened){
//            log.warning("close(): not open");
            return;
        }
        
        if(servoCommandThread!=null) {
//            try{
//                disableAllServos(); // don't disable servos otherwise remote control wont do anything
                try{
                    Thread.currentThread().sleep(100);
                }catch(InterruptedException e){}
                servoCommandThread.stopThread();
                try{
                    synchronized(servoCommandThread){
                        while(servoCommandThread.isAlive()){
                            servoCommandThread.wait();
                        }
                    }
                }catch (InterruptedException e){}
                outPipe.unbind();
//            }catch(HardwareInterfaceException e){
//                e.printStackTrace();
//            }
        }
        
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
    
    /** constructs a new USB connection, opens it.
     */
    public void open() throws HardwareInterfaceException {
        openUsbIo();
        HardwareInterfaceException.clearException();
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
    synchronized protected void openUsbIo() throws HardwareInterfaceException {
        
        if(!UsbIoUtilities.isLibraryLoaded()) return;
        //device has already been UsbIo Opened by now, in factory
        
        // opens the USBIOInterface device, configures it, binds a reader thread with buffer pool to read from the device and starts the thread reading events.
        // we got a UsbIo object when enumerating all devices and we also made a device list. the device has already been
        // opened from the UsbIo viewpoint, but it still needs firmware download, setting up pipes, etc.
        
        if(isOpened){
            log.warning("CypressFX2.openUsbIo(): already opened interface and setup device");
            return;
        }
        
        int status;
        try{
            gUsbIo=new UsbIo();
        }catch(Exception e){
            log.warning("can't load USBIO - probably native code not on java.library.path or not running Windows");
            return;
        }
        
        gDevList=UsbIo.createDeviceList(GUID);
        status = gUsbIo.open(0,gDevList,GUID);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("can't open USB device: "+UsbIo.errorText(status));
        }
        
        if (status != USBIO_ERR_SUCCESS) {
            log.warning("couldn't acquire device for exclusive use");
            throw new HardwareInterfaceException("couldn't acquire device for exclusive use: "+UsbIo.errorText(status));
        }
        
        // get device descriptor
        status = gUsbIo.getDeviceDescriptor(deviceDescriptor);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("CypressFX2.openUsbIo(): getDeviceDescriptor: "+UsbIo.errorText(status));
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
        
        openPipes();
        
        statusThread=new AsyncStatusThread(this);
        statusThread.start();
        
        // initialize device so we are synchronized with it - no way to ask if for values
        setDeadzoneForSpeed(deadzoneForSpeed);
        setDeadzoneForSteering(deadzoneForSteering);
        setRadioTimeoutMs(radioTimeoutMs);
        
        isOpened=true;
    }
    
    
    
    void openPipes() throws HardwareInterfaceException{
        int status;
        
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
        
        outPipe=new UsbIoPipe();
        status=outPipe.bind(0,(byte)ENDPOINT_OUT,gDevList,GUID);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("can't bind pipe: "+UsbIo.errorText(status));
        }
        
    }
    
//    // unconfigure device in case it was still configured from a prior terminated process
//    synchronized void unconfigureDevice() throws HardwareInterfaceException {
//        int status;
//        status = gUsbIo.unconfigureDevice();
//        if (status != USBIO_ERR_SUCCESS) {
//            gUsbIo.destroyDeviceList(gDevList);
//            throw new HardwareInterfaceException("unconfigureDevice: "+UsbIo.errorText(status));
//        }
//    }
    
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
    @Override
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
    
    // servo command bytes recognized by microcontroller
    static final int CMD_SET_SERVO=7,
            CMD_DISABLE_SERVO=8,
            CMD_SET_ALL_SERVOS=9,
            CMD_DISABLE_ALL_SERVOS=10,
            CMD_SET_DEADZONE_SPEED=11,
            CMD_SET_DEADZONE_STEERING=12,
            CMD_SET_LOCKOUT_TIME=13;
    
    /** @return 2: this controller can conrol two servos */
    public int getNumServos() {
        return 2;
    }
    
    
    public String getTypeName() {
        return "ServoController";
    }
    
    
    // this queue is used for holding servo commands that must be sent out.
    volatile ArrayBlockingQueue<ServoCommand> servoQueue;
    
    private int MAX_QUEUE_FULL_MESSAGES=100;
    
    private void submitCommand(final ServoCommand cmd){
        if(!servoQueue.offer(cmd) && MAX_QUEUE_FULL_MESSAGES-->0){
            log.info("servoQueue full, couldn't add command "+cmd);
        }
//        try{
//            Thread.currentThread().sleep(1); // give servo writer thread a chance to write command ASAP
//        }catch(InterruptedException e){}
    }
    
    private void checkServoCommandThread(){
        if(servoQueue==null){
            servoQueue=new ArrayBlockingQueue<ServoCommand>(SERVO_QUEUE_LENGTH);
        }
        if(servoCommandThread==null ){
            servoCommandThread=new ServoCommandThread();
            servoCommandThread.start();
            log.info("started servo command thread "+servoCommandThread);
        }
    }
    
    /**
     Computes the 2-byte pwm value to be sent to the ucontroller for a particular servo pulse width.
     @param value the servo value 0-1 float
     @return byte[] with 2 bytes in big endian format, MSB is first followed by LSB
     */
    private byte[] pwmValue(float value){
        if(value<0) value=0; else if(value>1) value=1;
        // we want 0 to map to 900 us, 1 to map to 2100 us.
        // PCA clock runs at C8051F320_PCA_COUNTER_CLK_MHZ MHz so each count is 1/MHZ us
        
        // count to load to PCA registers is low count
        float f=65536-C8051F320_PCA_COUNTER_CLK_MHZ*( ((2000-1000)*value) + 1000 );
        
        int v=(int)(f);
        
        byte[] b=new byte[2];
        
        b[0]=(byte)((v>>>8)&0xff);  // big endian format
        b[1]=(byte)(v&0xff);
        
//        System.out.println("value="+value+" 64k-f="+(65536-v+" f="+f+" v="+v+"="+HexString.toString((short)v)+" bMSB="+HexString.toString(b[0])+" bLSB="+HexString.toString(b[1]));
        return b;
    }
    
    /**
     Computes the 2-byte deadzone value to be sent to the ucontroller for a particular dead zone.
     The deadzone is the range of measured pwm pulse width coming from the radio receiver which is
     considered to be "zero" (not actuated by pressing throttle trigger or turning steering wheel).
     The larger the deadzone, the less control the human driver has over small steering or throttle.
     The smaller the deadzone, the more control the human driver has, but the more the car will react to noisy
     radio input and ignore what the computer control is trying to do.
     @param value the deadzone as a float value 0-1. 0 means no deadzone, the computer almost never controls the car, 
     1 means complete deadzone, meaning the computer always controls the car and the remote control is disabled
     @return byte[] with 2 bytes in big endian format, MSB is first followed by LSB. The controller PCA counter counts
     at 4 MHz = period of 250 ns. On the controller 
     this value is added and subtracted from the zero count value of
     6000 (1.5ms) to see if the received pulses are outside the deadzone. A deadzone of 1 means that the value sent to the controller should
     be 0.5ms =2000 counts.
     */
    protected byte[] deadzoneValue(float value){
        if(value<0) value=0; else if(value>1) value=1;
        // we want 0 to map to 900 us, 1 to map to 2100 us.
        // PCA clock runs at C8051F320_PCA_COUNTER_CLK_MHZ MHz so each count is 1/MHZ us
        
        float f=value*C8051F320_PCA_SERVO_FULL_RANGE/2; // will give 2000 for value=1
        
        int v=(int)(f);
        
        byte[] b=new byte[2];
        
        b[0]=(byte)((v>>>8)&0xff);  // big endian format
        b[1]=(byte)(v&0xff);
        
//        System.out.println("value="+value+" 64k-f="+(65536-v+" f="+f+" v="+v+"="+HexString.toString((short)v)+" bMSB="+HexString.toString(b[0])+" bLSB="+HexString.toString(b[1]));
        log.info("computed deadzone byte values for deadzone="+value+" to be "+v+" counts at "+C8051F320_PCA_COUNTER_CLK_MHZ+" MHz PCA clk freq");
        return b;
    }
    
    /** directly sends a particular short value to the servo, bypassing conversion from float.
     The value is subtracted from 65536 and written so that the value you write encodes the HIGH time of the
     PWM pulse.
     @param servo the servo number
     @param pwmValue the value written to servo controller is 64k minus this value
     */
    public void setServoValuePWM(final int servo, int pwmValue) throws HardwareInterfaceException{
        pwmValue=65536-pwmValue;
        checkServoCommandThread();
        ServoCommand cmd=new ServoCommand();
        cmd.bytes=new byte[4];
        cmd.bytes[0]=CMD_SET_SERVO;
        cmd.bytes[1]=(byte)getServo(servo);
        cmd.bytes[2]=(byte)((pwmValue>>>8)&0xff);
        cmd.bytes[3]=(byte)(pwmValue&0xff);
        submitCommand(cmd);
        
    }
    
    // corrects for mislabling of servo board, writing servo 0 will activate servo0-labeled output although this is really pwm3
    private byte getServo(final int servo){
        if(servo<0 || servo>getNumServos()-1){
            throw new IllegalArgumentException(servo+": only "+getNumServos()+" on this controller");
        }
        return (byte)servo; // to account for the fact that they are flipped on the PCB
    }
    
    /** sets servo position. The float value is translated to a value that is written to the device thar results in s pulse width
     that varies from 0.9 ms to 2.1 ms.
     @param servo the servo motor, 0 based
     @param value the value from 0 to 1. Values out of these bounds are clipped. Special value -1f turns off the servos.
     */
    @Override
    public void setServoValue(final int servo, final float value) {
        checkServoCommandThread();
        // the message consists of
        // msg header: the command code (1 byte)
        // servo to control, 1 byte
        // servo PWM PCA capture-compare register value, 2 bytes, this encodes the LOW time of the PWM output
        // 				this is send MSB, then LSB (big endian)
        ServoCommand cmd=new ServoCommand();
        cmd.bytes=new byte[4];
        cmd.bytes[0]=CMD_SET_SERVO;
        try{
            cmd.bytes[1]=(byte)getServo(servo);
        }catch(IllegalArgumentException e){
            log.warning(e.getMessage());
        }
        byte[] b=pwmValue(value);
        cmd.bytes[2]=b[0];
        cmd.bytes[3]=b[1];
        submitCommand(cmd);
    }
    
    public void disableAllServos() throws HardwareInterfaceException {
        checkServoCommandThread();
        ServoCommand cmd=new ServoCommand();
        cmd.bytes=new byte[1];
        cmd.bytes[0]=CMD_DISABLE_ALL_SERVOS;
        submitCommand(cmd);
    }
    
    /** sends a servo value to disable the servo
     @param servo the servo number, 0 based
     */
    @Override
    public void disableServo(final int servo) throws HardwareInterfaceException{
        checkServoCommandThread();
        ServoCommand cmd=new ServoCommand();
        cmd.bytes=new byte[2];
        cmd.bytes[0]=CMD_DISABLE_SERVO;
        cmd.bytes[1]=(byte)getServo(servo);
        submitCommand(cmd);
    }
    
    /** sets all servos to values in one transfer
     @param values array of value, must have length of number of servos
     */
    @Override
    public void setAllServoValues(final float[] values) throws HardwareInterfaceException {
        if(values==null || values.length!=getNumServos()) throw new IllegalArgumentException("wrong number of servo values, need "+getNumServos());
        checkServoCommandThread();
        ServoCommand cmd=new ServoCommand();
        cmd.bytes=new byte[1+getNumServos()*2];
        cmd.bytes[0]=CMD_SET_ALL_SERVOS;
        int index=1;
        for(int i=0;i<getNumServos();i++){
            byte[] b=pwmValue(values[getServo(i)]); // must correct here for flipped labeling on PCB
            cmd.bytes[index++]=b[0];
            cmd.bytes[index++]=b[1];
        }
        submitCommand(cmd);
    }

    @Override
    public void setFullDutyCycleMode (boolean yes){
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean isFullDutyCycleMode (){
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public float setServoPWMFrequencyHz(float freq) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int getPort2() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    // encaps the servo command bytes that are sent
    private class ServoCommand{
        byte[] bytes;
    }
    
    
    class ServoCommandThread extends java.lang.Thread{
        UsbIoBuf servoBuf=new UsbIoBuf(ENDPOINT_OUT_LENGTH);
        volatile boolean stop=false;
        public ServoCommandThread(){
            setDaemon(true);
            setName("ServoCommandThread");
            setPriority(Thread.MAX_PRIORITY);
        }
        
        public void stopThread(){
            log.info("set stop for ServoCommandThread");
            stop=true;
            interrupt();
        }
        
        @Override
        public void run(){
            ServoCommand cmd=null;
            while(stop==false){
//                log.info("polling for servoCommand");
                try{
                    cmd=servoQueue.poll(3000L,TimeUnit.MILLISECONDS);
                }catch(InterruptedException e){
                    log.info("interrupted, stopping ServoCommandThread");
                    if(stop==true) break;
                }
                if(cmd==null) {
                    continue;
                }
                try{
                    if(!isOpen()) open();
                    System.arraycopy(cmd.bytes,0,servoBuf.BufferMem,0,cmd.bytes.length);
                    servoBuf.NumberOfBytesToTransfer=ENDPOINT_OUT_LENGTH; // must send full buffer because that is what controller expects
                    
                    int status=outPipe.write(servoBuf);
                    if (status == 0) { // false if not successfully submitted
                        throw new HardwareInterfaceException("writing servo command: "+UsbIo.errorText(servoBuf.Status)); // error code is stored in buffer
                    }
//                    System.out.println(System.currentTimeMillis()+" servo command written to hardware");
                    
                    status=outPipe.waitForCompletion(servoBuf);
                    if (status != USBIO_ERR_SUCCESS) {
                        throw new HardwareInterfaceException("waiting for completion of write request: "+UsbIo.errorText(status));
                    }
                    
                    
                }catch(HardwareInterfaceException e){
                    e.printStackTrace();
                    close();
                    try{
                        Thread.currentThread().sleep(100); // sleep before trying another command
                    }catch(InterruptedException e2){}
                }catch(Exception e){
                    e.printStackTrace();
                    return;
                }
            }
            log.info("ServoCommandThread run loop ended");
            synchronized(this){
                notify(); // tell this to go
            }
        }
    }
    
    
    
    /** this inner class is a thread that reads the status endpoint and sets values in the instancing class to reflect the status
     messages that are sent
     */
    class AsyncStatusThread extends Thread {
        final int MSG_PULSE_WIDTH=1;
        
        UsbIoPipe pipe;
        SiLabsC8051F320_USBIO_CarServoController enclosingThread;
        boolean stop=false;
        byte msg;
        
        AsyncStatusThread(SiLabsC8051F320_USBIO_CarServoController monitor) {
            this.enclosingThread=monitor;
            setName("AsyncStatusThread");
        }
        
        public void stopThread(){
            if (pipe!=null)
                pipe.abortPipe();
            interrupt();
        }
        
        @Override
        public void run(){
            int status;
            UsbIoBuf buffer=new UsbIoBuf(ENDPOINT_IN_LENGTH); // size of EP1
            pipe=new UsbIoPipe();
            status=pipe.bind(0, (byte)ENDPOINT_IN, gDevList, GUID);
            if(status!=USBIO_ERR_SUCCESS){
                log.warning("error binding to pipe for EP1 for device status: "+UsbIo.errorText(status));
            }
            USBIO_PIPE_PARAMETERS pipeParams=new USBIO_PIPE_PARAMETERS();
            pipeParams.Flags=UsbIoInterface.USBIO_SHORT_TRANSFER_OK;
            status=pipe.setPipeParameters(pipeParams);
            if (status != USBIO_ERR_SUCCESS) {
                log.warning("can't set pipe parameters: "+UsbIo.errorText(status));
            }
            while(!stop && !isInterrupted()){
                buffer.NumberOfBytesToTransfer=ENDPOINT_IN_LENGTH;
                status=pipe.read(buffer);
//                log.info("started read, waiting for completion");
                if (status == 0) {
                    log.warning("Stopping status thread: error reading status pipe: "+UsbIo.errorText(buffer.Status));
                    break;
                }
                status=pipe.waitForCompletion(buffer);
                if (status != 0 && buffer.Status!=UsbIoErrorCodes.USBIO_ERR_CANCELED) {
                    log.warning("Stopping status thread: error waiting for completion of read on status pipe: "+UsbIo.errorText(buffer.Status));
                    break;
                }
                if(buffer.BytesTransferred>0){
//                    StringBuilder s=new StringBuilder("bytes ");
//                    for(int i=0;i<buffer.BytesTransferred;i++){
//                        s.append(buffer.BufferMem[i]+" ");
//                    }
//                    log.info(s.toString());
//                    if(msg==STATUS_MSG_OVERRIDE_DETECTED){
//                            log.info("RC controller override detected");
//                    }
                    int msg=buffer.BufferMem[0];
                    if(msg==MSG_PULSE_WIDTH){
                        /*
                                In_Packet[0]=MSG_PULSE_WIDTH;
                                In_Packet[1]=0; // pcb input channel (S2 or S3)
                                In_Packet[2]=MSB;
                                In_Packet[3]=LSB;
                         */
                        int chan=buffer.BufferMem[1];
                        int pulseDuration=(buffer.BufferMem[3]&0xff)+((buffer.BufferMem[2]&0xff)<<8); // sent big endian, 2 is LSB, 3 is MSB
                        float servoValue=((pulseDuration-C8051F320_PCA_ZERO_SERVO_COUNT)/(float)C8051F320_PCA_SERVO_FULL_RANGE)+0.5f;  // 0 is full left (e.g.), 0.5 is centered, 1 is full right
                        if(servoValue<0) servoValue=0; else if(servoValue>1) servoValue=1;
                        externalServoValues[3-chan]=servoValue;
//                        log.info("chan="+chan+" pulseDuration="+pulseDuration+" radio input servo value="+servoValue);
                    }
                }else{
                    log.warning("warning, 0 bytes in asyncStatusThread");
                }
            }
//            System.out.println("Status reader thread terminated.");
        } // run()
    }
    
    public static final void main(String[] args){
        final ServoInterface servo=new SiLabsC8051F320_USBIO_CarServoController();
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new ServoTest(servo).setVisible(true);
            }
        });
    }
    
    /** Sets the steering angle
     @param f 0-1 value, 0.5f sets straight ahead, 0 is full left, 1 is full right
     */
    public void setSteering(float f){
        setServoValue(STEERING_SERVO, f);
    }
    
    /** Sets the speed controller value.
     @param f 0-1 value, 0.5 is stopped, -.5f is full reverse, 1 is full ahead
     */
    public void setSpeed(float f){
        setServoValue(SPEED_SERVO,f);
    }
    
    /** Sets the radio "dead zone". In this range the car ignores radio input and controls
     speed based on the computer input. Outside this range, the radio outputs on the car
     (which are generated by signals recieved from the car's remote control) control  speed.
     This capability allows for user control of the car even while under computer control.
     @param f range a value such that (0.5-range) to (0.5+range) radio inputs are ignored by the car. The larger
     this value, the more the user must press throttle or turn steering wheel to control car. The smaller this
     value, the finer the control is possible, but the more likely that noisy radio signals will result in
     spurious control.
     */
    public void setDeadzoneForSpeed(float f){
        log.warning("this function not implemented on controller correctly yet - ignored on controller");
        deadzoneForSpeed=f;
        checkServoCommandThread();
        ServoCommand cmd=new ServoCommand();
        cmd.bytes=new byte[3];
        cmd.bytes[0]=CMD_SET_DEADZONE_SPEED;
        byte[] b=deadzoneValue(f);
        cmd.bytes[1]=b[0];
        cmd.bytes[2]=b[1];
        submitCommand(cmd);
        
    }
    
    /** Sets the radio "dead zone". In this range the car ignores radio input and controls
     speed based on the computer input. Outside this range, the radio outputs on the car
     (which are generated by signals recieved from the car's remote control) control  speed.
     This capability allows for user control of the car even while under computer control.
     @param f range a value such that (0.5-range) to (0.5+range) radio inputs are ignored by the car. The larger
     this value, the more the user must press throttle or turn steering wheel to control car. The smaller this
     value, the finer the control is possible, but the more likely that noisy radio signals will result in
     spurious control.
     */
    public void setDeadzoneForSteering(float f){
        log.warning("this function not implemented on controller correctly yet - ignored on controller");
        deadzoneForSteering=f;
        checkServoCommandThread();
        ServoCommand cmd=new ServoCommand();
        cmd.bytes=new byte[3];
        cmd.bytes[0]=CMD_SET_DEADZONE_STEERING;
        byte[] b=deadzoneValue(f);
        cmd.bytes[1]=b[0];
        cmd.bytes[2]=b[1];
        submitCommand(cmd);
    }
    
    public float getDeadzoneForSpeed(){
        return deadzoneForSpeed;
    }
    
    public float getDeadzoneForSteering(){
        return deadzoneForSteering;
    }
    
    /** Returns the last steering value received from radio receiver
     @return 0-1 value, 0.5f is straight ahead, 0 is full left, 1 is full right
     */
    public float getRadioSteer(){
        return externalServoValues[RADIO_STEER];
    }
    
    /** Returns the last steering value received from radio receiver
     @return 0-1 value, 0.5 is stopped, -.5f is full reverse, 1 is full ahead
     */
    public float getRadioSpeed(){
        return externalServoValues[RADIO_SPEED];
    }

    public int getRadioTimeoutMs() {
        return radioTimeoutMs;
    }

    /**
     Sets the timeout for radio control override of computer control. The computer control of the car will be locked out
     for this many ms after the car receives a non-zero steering or speed value.
     @param radioTimeoutMs the lockout time in ms
     */
    public void setRadioTimeoutMs(int radioTimeoutMs) {
        log.warning("this function not implemented on controller correctly yet - ignored on controller");
        this.radioTimeoutMs = radioTimeoutMs;
        
        checkServoCommandThread();
        ServoCommand cmd=new ServoCommand();
        cmd.bytes=new byte[5];
        cmd.bytes[0]=CMD_SET_LOCKOUT_TIME;
        int t=radioTimeoutMs*1000/C8051F320_MAIN_CYCLE_TIME_US; // reset timeout counter to this many cycles to get radioTimeoutMs
        cmd.bytes[1]=(byte)((t&0xff000000)>>>24);
        cmd.bytes[2]=(byte)((t&0x00ff0000)>>>16);
        cmd.bytes[3]=(byte)((t&0x0000ff00)>>>8);
        cmd.bytes[4]=(byte)((t&0x000000ff));
        submitCommand(cmd);
        log.info("set radioTimeoutMs="+radioTimeoutMs+" corresponding to main loop cycle count of "+t);
   }

    @Override
    public float[] getLastServoValues() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public float getLastServoValue(int servo) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void setPort2(int portValue) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
