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

package ch.unizh.ini.caviar.hardwareinterface.usb;

import ch.unizh.ini.caviar.JAERViewer;
import ch.unizh.ini.caviar.hardwareinterface.*;
import ch.unizh.ini.caviar.util.*;
import de.thesycon.usbio.*;
import de.thesycon.usbio.PnPNotifyInterface;
import de.thesycon.usbio.UsbIoErrorCodes;
import de.thesycon.usbio.structs.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 *
 * Use this class by making new SiLabsC8051F320_USBIO_ServoController with ServoInterfaceFactory.
 * <p>
 * Servo motor controller using USBIO driver access to SiLabsC8051F320 device. To prevent blocking on the thread controlling the
 *servo, this class starts a consumer thread that communicates with the USB interface. The producer (the user) communicates with the
 *consumer thread using an ArrayBlockingQueue. Therefore servo commands never produce hardware exceptions; these are caught in the consumer
 *thread by closing the device, which should be reopened on the next command.
 *
 * @author tobi
 */
public class SiLabsC8051F320_USBIO_ServoController implements UsbIoErrorCodes, PnPNotifyInterface, ServoInterface {
    
    static Logger log=Logger.getLogger("SiLabsC8051F320_USBIO_ServoController");
    
    /** driver guid (Globally unique ID, for this USB driver instance */
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
    
    // length of endpoint, ideally this value should be obtained from the pipe bound to the endpoint but we know what it is
    final static int ENDPOINT_OUT_LENGTH=0x10;
    
    PnPNotify pnp=null;
    
    private boolean isOpened;
    
    UsbIoPipe outPipe=null; // the pipe used for writing to the device
    
    /** number of servo commands that can be queued up. It is set to a small number so that comands do not pile up. If the queue
     is full when a command is given, then the old commands are discarded so that the latest command is next to be processed.
     */
    public final int SERVO_QUEUE_LENGTH=1;
    
    ServoCommandThread servoCommandThread=null;
    
    /** the device number, out of all potential compatible devices that could be opened */
    protected int interfaceNumber=0;
    
    
    private final int SYSCLK_MHZ=12; // this is sysclock of SiLabs
    private float pcaClockFreqMHz=SYSCLK_MHZ/2; // runs at 6 MHz by default with timer0 reload value of 255-1
    
    /**
     * Creates a new instance of SiLabsC8051F320_USBIO_ServoController using device 0 - the first
     * device in the list.
     */
    public SiLabsC8051F320_USBIO_ServoController() {
        interfaceNumber=0;
        if(UsbIoUtilities.usbIoIsAvailable){
            pnp=new PnPNotify(this);
            pnp.enablePnPNotification(GUID);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(){
            public void run(){
                if(isOpen()){
                    close();
                }
            }
        });
    }
    
    /** Creates a new instance of USBAEMonitor. Note that it is possible to construct several instances
     * and use each of them to open and read from the same device.
     *@param devNumber the desired device number, in range returned by CypressFX2Factory.getNumInterfacesAvailable
     *@see CypressFX2TmpdiffRetinaFactory
     */
    protected SiLabsC8051F320_USBIO_ServoController(int devNumber) {
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
        
        if(servoCommandThread!=null) {
            log.info("disabling all servos");
            disableAllServos();
            try{
                Thread.currentThread().sleep(100);
            }catch(InterruptedException e){}
            servoCommandThread.stopThread();
            servoCommandThread=null;  // force creation of new thread
            closeUsbIo();
        }
        
    }
    
    /** closes just the usb connection but doesn't do anything with the servocommandthread or disabling servos */
    private void closeUsbIo(){
        if(outPipe!=null) outPipe.unbind();
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
    protected int gDevList; // 'handle' (an integer) to an internal device list static to UsbIo
    
    
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
        
        if(!UsbIoUtilities.usbIoIsAvailable) return;
        
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
        
        openPipes();
        
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
    
    // servo command bytes recognized by microcontroller, defined in F32x_USB_Main.c on firmware
    static final int CMD_SET_SERVO=7, CMD_DISABLE_SERVO=8, CMD_SET_ALL_SERVOS=9, CMD_DISABLE_ALL_SERVOS=10, CMD_SET_TIMER0_RELOAD_VALUE=11;
    
    public int getNumServos() {
        return 4;
    }
    
    
    public String getTypeName() {
        return "ServoController";
    }
    
    
    // this queue is used for holding servo commands that must be sent out.
    private volatile ArrayBlockingQueue<ServoCommand> servoQueue;
    
    protected void submitCommand(ServoCommand cmd){
        if(cmd==null){
            log.warning("null cmd submitted to servo command queue");
            return;
        }
        if(servoQueue==null){
            log.warning("servoQueue is null, you must open the device before submitting commands");
            return;
        }
        if(!servoQueue.offer(cmd)){
//            log.info("servoQueue full, couldn't add command "+cmd+" clearing it and queuing cmd");
            servoQueue.clear();
            servoQueue.offer(cmd);
        }
//        try{
//            Thread.currentThread().sleep(1); // give servo writer thread a chance to write command ASAP
//        }catch(InterruptedException e){}
    }
    
    protected void checkServoCommandThread(){
        if(servoQueue==null){
            servoQueue=new ArrayBlockingQueue<ServoCommand>(SERVO_QUEUE_LENGTH);
        }
        if(servoCommandThread==null ){
            servoCommandThread=new ServoCommandThread();
            servoCommandThread.start();
            log.info("started servo command thread "+servoCommandThread);
        }
    }
    
    private byte[] pwmValue(float value){
        if(value<0) value=0; else if(value>1) value=1;
        // we want 0 to map to 900 us, 1 to map to 2100 us.
        // PCA clock runs at pcaClockFreqMHz
        
        // count to load to PCA registers is low count
        float f=65536-pcaClockFreqMHz*( ((2100-900)*value) + 900 );
        
        int v=(int)(f);
        
        byte[] b=new byte[2];
        
        b[0]=(byte)((v>>>8)&0xff);  // big endian format
        b[1]=(byte)(v&0xff);
        
//        System.out.println("value="+value+" 64k-f="+(65536-v+" f="+f+" v="+v+"="+HexString.toString((short)v)+" bMSB="+HexString.toString(b[0])+" bLSB="+HexString.toString(b[1]));
        return b;
    }
    
    /** directly sends a particular short value to the servo, bypassing conversion from float.
     * The value is subtracted from 65536 and written so that the value you write encodes the HIGH time of the
     * PWM pulse.
     * @param servo the servo number
     * @param pwmValue the value written to servo controller is 64k minus this value
     */
    private void setServoValuePWM(int servo, int pwmValue) {
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
    
    /** Attempts to set the PWM frequency. Most analog hobby servos accept from 50-100Hz
     *and digital hobby servos can accept up to 250Hz, although this information is usually not specified
     * in the product information.
     *The SiLabsUSB board can drive servos at 180, 90, 60 or 45 Hz. The frequency is based on the
     *overflow time for a 16 bit counter that is clocked by overflow of an automatically reloaded counter/timer that is clocked
     *by the system clock of 12 MHz. With a timer reload of 1 (requiring 2 cycles to overflow), the 16 bit counter is clocked
     *at 6 MHz, leading to a frequency of 91 Hz.
     *<p>
     *The default frequency is 91Hz.
     *@param freq the desired frequency in Hz. The actual value is returned.
     *@return the actual value or 0 if there is an error.
     */
    public float setServoPWMFrequencyHz(float freq){
        checkServoCommandThread();
        if(freq<=0) {
            log.warning("freq="+freq+" is not a valid value");
            return 0;
        }
        int n=Math.round(SYSCLK_MHZ*1e6f/65536f/freq); // we get about 2 here with freq=90Hz
        if(n==0) {
            log.warning("freq="+freq+" too high, setting max possible of 183Hz");
            n=1;
        }
        float freqActual=SYSCLK_MHZ*1e6f/65536/n; // n=1, we get 183Hz
        ServoCommand cmd=new ServoCommand();
        cmd.bytes=new byte[2];
        cmd.bytes[0]=CMD_SET_TIMER0_RELOAD_VALUE;
        cmd.bytes[1]=(byte)(n-1); // now we use n-1 to give us a reload value of 0 for max freq
        submitCommand(cmd);
        pcaClockFreqMHz=SYSCLK_MHZ/n;
        return freqActual;
    }
    
    // corrects for mislabling of servo board, writing servo 0 will activate servo0-labeled output although this is really pwm3
    private byte getServo(int servo){
        return (byte)(getNumServos()-servo-1);
    }
    
    /** sets servo position. The float value is translated to a value that is written to the device thar results in s pulse width
     * that varies from 0.9 ms to 2.1 ms.
     * @param servo the servo motor, 0 based
     * @param value the value from 0 to 1. Values out of these bounds are clipped. Special value -1f turns off the servos.
     */
    public void setServoValue(int servo, float value){
        checkServoCommandThread();
        // the message consists of
        // msg header: the command code (1 byte)
        // servo to control, 1 byte
        // servo PWM PCA capture-compare register value, 2 bytes, this encodes the LOW time of the PWM output
        // 				this is send MSB, then LSB (big endian)
        ServoCommand cmd=new ServoCommand();
        cmd.bytes=new byte[4];
        cmd.bytes[0]=CMD_SET_SERVO;
        cmd.bytes[1]=(byte)getServo(servo);
        byte[] b=pwmValue(value);
        cmd.bytes[2]=b[0];
        cmd.bytes[3]=b[1];
        submitCommand(cmd);
    }
    
    public void disableAllServos() {
        checkServoCommandThread();
        ServoCommand cmd=new ServoCommand();
        cmd.bytes=new byte[1];
        cmd.bytes[0]=CMD_DISABLE_ALL_SERVOS;
        submitCommand(cmd);
    }
    
    /** sends a servo value to disable the servo
     * @param servo the servo number, 0 based
     */
    public void disableServo(int servo) {
        checkServoCommandThread();
        ServoCommand cmd=new ServoCommand();
        cmd.bytes=new byte[2];
        cmd.bytes[0]=CMD_DISABLE_SERVO;
        cmd.bytes[1]=(byte)getServo(servo);
        submitCommand(cmd);
    }
    
    /** sets all servos to values in one transfer
     * @param values array of value, must have length of number of servos
     */
    public void setAllServoValues(float[] values)  {
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
    
    /** encapsulates the servo command bytes that are sent */
    protected class ServoCommand{
        byte[] bytes;
    }
    
    /** This thread actually talks to the hardware */
    private class ServoCommandThread extends java.lang.Thread{
        UsbIoBuf servoBuf=new UsbIoBuf(ENDPOINT_OUT_LENGTH);
        volatile boolean stop=false;
        Thread T;
        public ServoCommandThread(){
            setDaemon(true);
            setName("ServoCommandThread");
            setPriority(Thread.MAX_PRIORITY);
            T=this;
        }
        
        public void stopThread(){
            log.info("set stop for ServoCommandThread");
            stop=true;
            try {
                outPipe.abortPipe();
                if(T!=null){
                    T.interrupt();
                    T.join();
                    T=null;
                }
            } catch (InterruptedException ex) {
                log.info("interrupted");
            }
//            if(isAlive()) {
//                try {
//                    join();
//                } catch (InterruptedException ex) {
//                    ex.printStackTrace();
//                }
//            }
        }
        
        /** Processes the servo commands in the queue */
        public void run(){
            ServoCommand cmd=null;
            while(stop==false){
//                log.info("polling for servoCommand");
                try{
                    cmd=servoQueue.poll(300L,TimeUnit.MILLISECONDS);
                }catch(InterruptedException e){
                    log.info("queue processing interrupted, probably stop");
                    continue;
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
                    
                    //could be used to calculate send delay
                    //if(JAERViewer.globalTime3 == 0 && JAERViewer.globalTime1 != 0)
                    //    JAERViewer.globalTime3 = System.nanoTime();
                    
                    if (status != USBIO_ERR_SUCCESS) {
                        throw new HardwareInterfaceException("waiting for completion of write request: "+UsbIo.errorText(status));
                    }
                }catch(HardwareInterfaceException e){
                    log.warning(e.toString());
                    closeUsbIo();
                    break;
                }
            }
            log.info("ServoCommandThread run loop ended");
        }
    }
    
    
    /**
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


