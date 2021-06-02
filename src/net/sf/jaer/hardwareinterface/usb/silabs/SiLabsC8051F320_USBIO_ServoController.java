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
package net.sf.jaer.hardwareinterface.usb.silabs;

import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
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
import de.thesycon.usbio.UsbIoWriter;
import de.thesycon.usbio.structs.USBIO_CONFIGURATION_INFO;
import de.thesycon.usbio.structs.USBIO_PIPE_PARAMETERS;
import de.thesycon.usbio.structs.USBIO_SET_CONFIGURATION;
import de.thesycon.usbio.structs.USB_DEVICE_DESCRIPTOR;
import de.thesycon.usbio.structs.USB_STRING_DESCRIPTOR;

/**
 * The USB servo controller board is controlled by this class. Use this class by
 * making new SiLabsC8051F320_USBIO_ServoController's from the
 * ServoInterfaceFactory. <p> Servo motor controller using USBIO driver access
 * to SiLabsC8051F320 device. To prevent blocking on the thread controlling the
 * servo, this class starts a consumer thread that communicates with the USB
 * interface. The producer (the user) communicates with the consumer thread
 * using an ArrayBlockingQueue. Therefore servo commands never produce hardware
 * exceptions; these are caught in the consumer thread by closing the device,
 * which should be reopened on the next command. <p> This class goes with the
 * USB servo board shown below, which also shows the pinout of the board and the
 * use of the jumpers. <br> <img src="doc-files/USBServoBoard.png"/>
 *
 * @author tobi
 */
public class SiLabsC8051F320_USBIO_ServoController implements UsbIoErrorCodes, PnPNotifyInterface, ServoInterface {

    static final Logger log = Logger.getLogger("SiLabsC8051F320_USBIO_ServoController");
    /**
     * driver GUID (Globally unique ID, for this USB driver instance
     */
    public final static String GUID = "{3B15398D-1EF2-44D7-A6B8-74A3FCCD29BF}"; // tobi generated in pasadena july 2006
    /**
     * The vendor ID
     */
    static public final short VID = (short) 0x0547;
    /**
     * The product ID
     */
    static public final short PID = (short) 0x8750;
    final static short CONFIG_INDEX = 0;
    final static short CONFIG_NB_OF_INTERFACES = 1;
    final static short CONFIG_INTERFACE = 0;
    final static short CONFIG_ALT_SETTING = 0;
    final static int CONFIG_TRAN_SIZE = 64;
    // out endpoint for servo commands
    final static int ENDPOINT_OUT = 0x02, ENDPOINT_IN = 0x81;
    /**
     * length of endpoints, ideally this value should be obtained from the pipe
     * bound to the endpoint but we know what it is for this device. It is set
     * to 16 bytes to minimize transmission time for the out end point and 1
     * byte for the in endpoint. At 12 Mbps, 16 bytes+header (13 bytes)=140 bits
     * requires about 30 us to transmit.
     */
    public final static int ENDPOINT_OUT_LENGTH = 0x10, ENDPOINT_IN_LENGTH = 1;
    /**
     * The board can control this many servos
     */
    public static int NUM_SERVOS = 4;
    PnPNotify pnp = null;
    private boolean isOpened;
    private float[] lastServoValues = new float[NUM_SERVOS];
    /**
     * number of servo commands that can be queued up. It is set to a small
     * number so that commands do not pile up. If the queue is full when a
     * command is given, then the old commands are discarded so that the latest
     * command is next to be processed. Note that this policy can have drawbacks
     * - if commands are sent to different servos successively, then new
     * commands can wipe out commands to older commands to set other servos to
     * some position.
     */
    public static final int SERVO_QUEUE_LENGTH = 20;
    ServoCommandWriter servoCommandWriter = null; // this worker thread asynchronously writes to device
    private volatile ArrayBlockingQueue<ServoCommand> servoQueue; // this queue is used for holding servo commands that must be sent out.
    AsyncStatusThread statusThread = null;
    /**
     * the device number, out of all potential compatible devices that could be
     * opened
     */
    protected int interfaceNumber = 0;
    private final int SYSCLK_MHZ = 12; // this is sysclock of SiLabs
    private float pcaClockFreqMHz = SYSCLK_MHZ / 2; // runs at 6 MHz by default with timer0 reload value of 255-1
    private boolean fullDutyCycleModeEnabled = false;  // reset state is false;
    volatile private String errorString = null;
    private volatile int port2value = 0;

    /**
     * Creates a new instance of SiLabsC8051F320_USBIO_ServoController using
     * device 0 - the first device in the list.
     */
    public SiLabsC8051F320_USBIO_ServoController() {
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
        servoQueue = new ArrayBlockingQueue<ServoCommand>(SERVO_QUEUE_LENGTH);
    }

    /**
     * Creates a new instance of USBAEMonitor. Note that it is possible to
     * construct several instances and use each of them to open and read from
     * the same device.
     *
     * @param devNumber the desired device number, in range returned by
     * CypressFX2Factory.getNumInterfacesAvailable
     */
    public SiLabsC8051F320_USBIO_ServoController(int devNumber) {
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

    /**
     * Closes the device. Never throws an exception.
     */
    @Override
    public void close() {
        if (!isOpened) {
            log.warning("close(): not open");
            return;
        }

        if (servoCommandWriter != null) {
            servoCommandWriter.shutdownThread();
            servoCommandWriter.close(); // unbinds pipes too
        }
        if (statusThread != null) {
            statusThread.stopThread();
        }
        if (gUsbIo != null) {
            gUsbIo.close();
        }
        UsbIo.destroyDeviceList(gDevList);
        log.info("device closed");
        errorString = null;
        isOpened = false;

    }
    /**
     * the first USB string descriptor (Vendor name) (if available)
     */
    protected USB_STRING_DESCRIPTOR stringDescriptor1 = new USB_STRING_DESCRIPTOR();
    /**
     * the second USB string descriptor (Product name) (if available)
     */
    protected USB_STRING_DESCRIPTOR stringDescriptor2 = new USB_STRING_DESCRIPTOR();
    /**
     * the third USB string descriptor (Serial number) (if available)
     */
    protected USB_STRING_DESCRIPTOR stringDescriptor3 = new USB_STRING_DESCRIPTOR();
    protected int numberOfStringDescriptors = 2;

    /**
     * returns number of string descriptors
     *
     * @return number of string descriptors: 2 for TmpDiff128, 3 for
     * MonitorSequencer
     */
    public int getNumberOfStringDescriptors() {
        return numberOfStringDescriptors;
    }
    /**
     * the USBIO device descriptor
     */
    protected USB_DEVICE_DESCRIPTOR deviceDescriptor = new USB_DEVICE_DESCRIPTOR();
    /**
     * the UsbIo interface to the device. This is assigned on construction by
     * the factory which uses it to open the device. here is used for all USBIO
     * access to the device
     */
    protected UsbIo gUsbIo = null;
    /**
     * the devlist handle for USBIO
     */
    protected long gDevList; // 'handle' (an integer) to an internal device list static to UsbIo

    /**
     * checks if device has a string identifier that is a non-empty string
     *
     * @return false if not, true if there is one
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
     * This method does the hard work of opening the device, downloading the
     * firmware, making sure everything is OK. This method is synchronized to
     * prevent multiple threads from trying to open at the same time, e.g. a GUI
     * thread and the main thread.
     *
     * Opening the device after it has already been opened has no effect.
     *
     * @see #close
     * @throws HardwareInterfaceException if there is a problem. Diagnostics are
     * printed to stderr.
     */
    @Override
    public void open() throws HardwareInterfaceException {
        if (!UsbIoUtilities.isLibraryLoaded()) {
            return;
        }

        //device has already been UsbIo Opened by now, in factory

        // opens the USBIOInterface device, configures it, binds a reader thread with buffer pool to read from the device and starts the thread reading events.
        // we got a UsbIo object when enumerating all devices and we also made a device list. the device has already been
        // opened from the UsbIo viewpoint, but it still needs firmware download, setting up pipes, etc.

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
            log.log(Level.INFO, "getDeviceDescriptor: Vendor ID (VID) {0} Product ID (PID) {1}", new Object[]{HexString.toString((short) deviceDescriptor.idVendor), HexString.toString((short) deviceDescriptor.idProduct)});
        }

        status=gUsbIo.unconfigureDevice();
         if (status != USBIO_ERR_SUCCESS) {
            log.log(Level.WARNING, "unconfiguring device: {0}", UsbIo.errorText(status));
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
            log.log(Level.WARNING, "setting configuration: {0}", UsbIo.errorText(status));
        }

        // get device descriptor
        status = gUsbIo.getDeviceDescriptor(deviceDescriptor);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("getDeviceDescriptor: " + UsbIo.errorText(status));
        } else {
            log.log(Level.INFO, "getDeviceDescriptor: Vendor ID (VID) {0} Product ID (PID) {1}", new Object[]{HexString.toString((short) deviceDescriptor.idVendor), HexString.toString((short) deviceDescriptor.idProduct)});
        }

        if (deviceDescriptor.iSerialNumber != 0) {
            this.numberOfStringDescriptors = 3;
        }

        // get string descriptor
        status = gUsbIo.getStringDescriptor(stringDescriptor1, (byte) 1, 0);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("getStringDescriptor: " + UsbIo.errorText(status));
        } else {
            log.log(Level.INFO, "getStringDescriptor 1: {0}", stringDescriptor1.Str);
        }

        // get string descriptor
        status = gUsbIo.getStringDescriptor(stringDescriptor2, (byte) 2, 0);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("getStringDescriptor: " + UsbIo.errorText(status));
        } else {
            log.log(Level.INFO, "getStringDescriptor 2: {0}", stringDescriptor2.Str);
        }

        if (this.numberOfStringDescriptors == 3) {
            // get serial number string descriptor
            status = gUsbIo.getStringDescriptor(stringDescriptor3, (byte) 3, 0);
            if (status != USBIO_ERR_SUCCESS) {
                UsbIo.destroyDeviceList(gDevList);
                throw new HardwareInterfaceException("getStringDescriptor: " + UsbIo.errorText(status));
            } else {
                log.log(Level.INFO, "getStringDescriptor 3: {0}", stringDescriptor3.Str);
            }
        }

        // get outPipe information and extract the FIFO size
        USBIO_CONFIGURATION_INFO configurationInfo = new USBIO_CONFIGURATION_INFO();
        status = gUsbIo.getConfigurationInfo(configurationInfo);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("getConfigurationInfo: " + UsbIo.errorText(status));
        }

        if (configurationInfo.NbOfPipes < 2) {
//            gUsbIo.cyclePort();
            throw new HardwareInterfaceException("didn't find any pipes to bind to");
        }

        servoCommandWriter = new ServoCommandWriter();
        status = servoCommandWriter.bind(0, (byte) ENDPOINT_OUT, gDevList, GUID);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("can't bind command writer to endpoint: " + UsbIo.errorText(status));
        }
        USBIO_PIPE_PARAMETERS pipeParams = new USBIO_PIPE_PARAMETERS();
        pipeParams.Flags = UsbIoInterface.USBIO_SHORT_TRANSFER_OK;
        status = servoCommandWriter.setPipeParameters(pipeParams);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            throw new HardwareInterfaceException("startAEWriter: can't set pipe parameters: " + UsbIo.errorText(status));
        }

        servoCommandWriter.startThread(3);

        statusThread = new AsyncStatusThread(this);
        statusThread.start();

        isOpened = true;
        submittedCmdAfterOpen = false;
    }

    /**
     * return the string USB descriptors for the device
     *
     * @return String[] of length 2 of USB descriptor strings.
     */
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

    public short getVID() {
        if (deviceDescriptor == null) {
            log.warning("USBAEMonitor: getVIDPID called but device has not been opened");
            return 0;
        }
        // int[] n=new int[2]; n is never used
        return (short) deviceDescriptor.idVendor;
    }

    public short getPID() {
        if (deviceDescriptor == null) {
            log.warning("USBAEMonitor: getVIDPID called but device has not been opened");
            return 0;
        }
        return (short) deviceDescriptor.idProduct;
    }

    /**
     * @return bcdDevice (the binary coded decimel device version
     */
    public short getDID() { // this is not part of USB spec in device descriptor.
        return (short) deviceDescriptor.bcdDevice;
    }

    /**
     * reports if interface is {@link #open}.
     *
     * @return true if already open
     */
    @Override
    public boolean isOpen() {
        return isOpened;
    }
    /*
     * ***********************************************************************************************
     * /
     *
     * /*
     * // define command codes #define CMD_SET_SERVO 7 #define
     * CMD_DISABLE_SERVO 8 #define CMD_SET_ALL_SERVOS 9 #define
     * CMD_DISABLE_ALL_SERVOS 10
     */
    /**
     * servo command bytes recognized by microcontroller, defined in
     * F32x_USB_Main.c on firmware
     *
     */
    private static final int CMD_SET_SERVO = 7,
            CMD_DISABLE_SERVO = 8,
            CMD_SET_ALL_SERVOS = 9,
            CMD_DISABLE_ALL_SERVOS = 10,
            CMD_SET_TIMER0_RELOAD_VALUE = 11,
            CMD_SET_PORT2 = 12,
            CMD_SEND_WOWWEE_RS_CMD = 13,
            CMD_SET_PORT_DOUT = 14,
            CMD_SET_PCA0MD_CPS = 15,
            CMD_GET_PORT2 = 16;

    @Override
    public int getNumServos() {
        return NUM_SERVOS;
    }

    @Override
    public String getTypeName() {
        return "ServoController";
    }
    int clearedQueueWarningCount = 0;
    final int PRINT_QUEUE_CLEARED_INTERVAL = 100;
    private boolean submittedCmdAfterOpen = false; // flag that is set once a command has been sent after open.
    private ServoCommand lastCmd = null;

    /**
     * Submits the command to the writer thread queue that sends them to the
     * device
     *
     * @param cmd the command, which consists of bytes sent to the device.
     */
    protected void submitCommand(ServoCommand cmd) {
        if (cmd == null) {
            log.warning("null cmd submitted to servo command queue");
            return;
        }
        if (submittedCmdAfterOpen && cmd.equals(lastCmd)) {
            return; // don't just duplicate command already sent since open
        }
        if (errorString != null) {
            log.warning(errorString);
            close();
            return;
        }
        if (!servoQueue.offer(cmd)) { // if queue is full, just clear it and replace with latest command
            servoQueue.clear();
            servoQueue.offer(cmd);
            submittedCmdAfterOpen = true;
            if ((clearedQueueWarningCount++ % PRINT_QUEUE_CLEARED_INTERVAL) == 0) {
                log.warning("cleared queue to submit latest command (only logging this warning every " + PRINT_QUEUE_CLEARED_INTERVAL + " times)"); // TODO add limited number of warnings here
            }
        }
        lastCmd = cmd;
        Thread.yield(); // let writer thread get it and submit a write
    }

    /**
     * Returns last servo values sent.These are in order of PCA outputs on the
     * SiLabs chip, which are opposite the labeling on the board.
     */
    @Override
    public float[] getLastServoValues() {
        return lastServoValues;
    }

    /**
     * Returns last servo value sent (0 before sending a value)
     */
    @Override
    public float getLastServoValue(int servo) {
        return lastServoValues[getServo(servo)];
    }

    @Override
    public void setFullDutyCycleMode(boolean yes) {
        this.fullDutyCycleModeEnabled = yes;
    }

    @Override
    public boolean isFullDutyCycleMode() {
        return fullDutyCycleModeEnabled;
    }

    /**
     * This thread actually talks to the hardware
     */
    private class ServoCommandWriter extends UsbIoWriter {

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

        /**
         * waits and takes commands from the queue and submits them to the
         * device.
         */
        @Override
        public void processBuffer(UsbIoBuf servoBuf) {
            ServoCommand cmd = null;
            servoBuf.NumberOfBytesToTransfer = ENDPOINT_OUT_LENGTH; // must send full buffer because that is what controller expects for interrupt transfers
            servoBuf.OperationFinished = false; // setting true will finish all transfers and end writer thread
            servoBuf.BytesTransferred = 0;
            try {
                cmd = servoQueue.take();  // wait forever until there is a command
            } catch (InterruptedException e) {
                log.info("servo queue wait interrupted");
                T.interrupt(); // important to call again so that isInterrupted in run loop see that thread should terminate
            }
            if (cmd == null) {
                return;
            }
            System.arraycopy(cmd.bytes, 0, servoBuf.BufferMem, 0, cmd.bytes.length);
        }

        @Override
        public void bufErrorHandler(UsbIoBuf usbIoBuf) {
            errorString = UsbIo.errorText(usbIoBuf.Status);
            log.warning(errorString); // TODO set a flag or close the device to allow reopen try, needs caller to get HardwareInterfaceException
        }

        @Override
        public void onThreadExit() {
            log.info("servo command writer done");
        }
    }

    /**
     * Checks if servo is not open and opens it
     *
     * @return true if it is there and device is open
     */
    protected boolean checkServoCommandThread() {
        try {
            if (!isOpen()) {
                open();
            }
            return true;
        } catch (HardwareInterfaceException ex) {
            log.warning(ex.toString());
            return false;
        }
    }

    private byte[] pwmValue(float value) {
        if (value < 0) {
            value = 0;
        } else if (value > 1) {
            value = 1;
        }
        if (!fullDutyCycleModeEnabled) {
            // we want 0 to map to 900 us, 1 to map to 2100 us.
            // PCA clock runs at pcaClockFreqMHz

            // count to load to PCA registers is low count
            float f = 65536 - (pcaClockFreqMHz * (((2100 - 900) * value) + 900));

            int v = (int) (f);

            byte[] b = new byte[2];

            b[0] = (byte) ((v >>> 8) & 0xff);  // big endian format
            b[1] = (byte) (v & 0xff);

//        System.out.println("value="+value+" 64k-f="+(65536-v+" f="+f+" v="+v+"="+HexString.toString((short)v)+" bMSB="+HexString.toString(b[0])+" bLSB="+HexString.toString(b[1]));
            return b;
        } else {
            short val = (short) (65535 - (0xffff & (int) (65535 * value))); // value sent is the PWM output LOW time, so we subtract from 0xffff to send the proper value to get the desired HIGH duty cycle
            byte[] b = new byte[2];
            b[0] = (byte) ((val >>> 8) & 0xff);
            b[1] = (byte) (val & 0xff);
            return b;
        }
    }

    /**
     * directly sends a particular short value to the servo, bypassing
     * conversion from float. The value is subtracted from 65536 and written so
     * that the value you write encodes the HIGH time of the PWM pulse; 0=low
     * always, 65535=high always.
     *
     * @param servo the servo number
     * @param pwmValue the value written to servo controller is 64k minus this
     * value
     */
    public void setServoValuePWM(int servo, int pwmValue) {
        pwmValue = 65535 - pwmValue;
        checkServoCommandThread();
        ServoCommand cmd = new ServoCommand();
        cmd.bytes = new byte[4];
        cmd.bytes[0] = CMD_SET_SERVO;
        cmd.bytes[1] = getServo(servo);
        cmd.bytes[2] = (byte) ((pwmValue >>> 8) & 0xff);
        cmd.bytes[3] = (byte) (pwmValue & 0xff);
        submitCommand(cmd);

    }

    /**
     * Attempts to set the PWM frequency. Most analog hobby servos accept from
     * 50-100Hz and digital hobby servos can accept up to 250Hz, although this
     * information is usually not specified in the product information. The
     * SiLabsUSB board can drive servos at 180, 90, 60 or 45 Hz. The frequency
     * is based on the overflow time for a 16 bit counter that is clocked by
     * overflow of an automatically reloaded counter/timer that is clocked by
     * the system clock of 12 MHz. With a timer reload of 1 (requiring 2 cycles
     * to overflow), the 16 bit counter is clocked at 6 MHz, leading to a
     * frequency of 91 Hz. <p> The default frequency is 91Hz.
     *
     * @param freq the desired frequency in Hz. The actual value is returned.
     * @return the actual value or 0 if there is an error.
     */
    @Override
	public float setServoPWMFrequencyHz(float freq) {
        checkServoCommandThread();
        if (freq <= 0) {
            log.log(Level.WARNING, "freq={0} is not a valid value", freq);
            return 0;
        }
        int n = Math.round((SYSCLK_MHZ * 1e6f) / 65536f / freq); // we get about 2 here with freq=90Hz
        if (n == 0) {
            log.log(Level.WARNING, "freq={0} too high, setting max possible of 183Hz", freq);
            n = 1;
        }
        float freqActual = (SYSCLK_MHZ * 1e6f) / 65536 / n; // n=1, we get 183Hz
        ServoCommand cmd = new ServoCommand();
        cmd.bytes = new byte[2];
        cmd.bytes[0] = CMD_SET_TIMER0_RELOAD_VALUE;
        cmd.bytes[1] = (byte) (n - 1); // now we use n-1 to give us a reload value of 0 for max freq
        submitCommand(cmd);
        pcaClockFreqMHz = SYSCLK_MHZ / n;
        return freqActual;
    }

    /**
     * corrects for mislabeling of servo board compared with PCA output port on
     * SiLabs, i.e. S0 on board is actually PCA3 output and S3 is PCA0.
     *
     * @param servo is the labeled output port
     * @return the index into the array that is passed to setAllServoValues to
     * set all the servos simultaneously
     */
    public byte getServo(int servo) {
        return (byte) (getNumServos() - servo - 1);
    }

    /**
     * sets servo position. The float value is translated to a value that is
     * written to the device thar results in s pulse width that varies from 0.9
     * ms to 2.1 ms.
     *
     * @param servo the servo motor, 0 based
     * @param value the value from 0 to 1. Values out of these bounds are
     * clipped. Special value -1f turns off the servos.
     */
    @Override
    public void setServoValue(int servo, float value) {
        checkServoCommandThread();
        // the message consists of
        // msg header: the command code (1 byte)
        // servo to control, 1 byte
        // servo PWM PCA capture-compare register value, 2 bytes, this encodes the LOW time of the PWM output
        // 				this is send MSB, then LSB (big endian)
        ServoCommand cmd = new ServoCommand();
        cmd.bytes = new byte[4];
        cmd.bytes[0] = CMD_SET_SERVO;
        cmd.bytes[1] = getServo(servo);
        byte[] b = pwmValue(value);
        cmd.bytes[2] = b[0];
        cmd.bytes[3] = b[1];
        submitCommand(cmd);
        lastServoValues[getServo(servo)] = value;
    }

    @Override
    public void disableAllServos() {
        checkServoCommandThread();
        ServoCommand cmd = new ServoCommand();
        cmd.bytes = new byte[1];
        cmd.bytes[0] = CMD_DISABLE_ALL_SERVOS;
        submitCommand(cmd);
    }

    /**
     * sends a servo value to disable the servo
     *
     * @param servo the servo number, 0 based
     */
    @Override
    public void disableServo(int servo) {
        checkServoCommandThread();
        ServoCommand cmd = new ServoCommand();
        cmd.bytes = new byte[2];
        cmd.bytes[0] = CMD_DISABLE_SERVO;
        cmd.bytes[1] = getServo(servo);
        submitCommand(cmd);
    }

    /**
     * sets all servos to values in one transfer
     *
     * @param values array of value, must have length of number of servos. Order
     * of values is order given by getServo(i), where i is the labeled output on
     * the servo board.
     */
    @Override
    public void setAllServoValues(float[] values) {
        if ((values == null) || (values.length != getNumServos())) {
            throw new IllegalArgumentException("wrong number of servo values, need " + getNumServos());
        }
        checkServoCommandThread();
        ServoCommand cmd = new ServoCommand();
        cmd.bytes = new byte[1 + (getNumServos() * 2)];
        cmd.bytes[0] = CMD_SET_ALL_SERVOS;
        int index = 1;
        for (int i = 0; i < getNumServos(); i++) {
            byte[] b = pwmValue(values[getServo(i)]); // must correct here for flipped labeling on PCB
            cmd.bytes[index++] = b[0];
            cmd.bytes[index++] = b[1];
            lastServoValues[getServo(i)] = values[i];
        }
        submitCommand(cmd);
    }

    /**
     * sends a command to set the port 2 output (on the side of the original
     * board) to portValue. This port is presently set to open-drain mode on all
     * bits.
     *
     * @param portValue the bits to set
     */
    @Override
    public void setPort2(int portValue) {
        checkServoCommandThread();
        ServoCommand cmd = new ServoCommand();
        cmd.bytes = new byte[2];
        cmd.bytes[0] = CMD_SET_PORT2;
        cmd.bytes[1] = (byte) (0xff & portValue);
        submitCommand(cmd);
    }

    @Override
    public int getPort2() {
        if (!isOpen()) {
            return 0;
        }
        return port2value;
    }

    /**
     * Sets the accessible ports PXMDOUT bits to set the port pins in either
     * push-pull or open-drain configuration. The first nibble of p1 are the
     * servo S0-S3 bits (in reverse order), and p2 is the port on the side of
     * the ServoUSB board. p1.0=S3, p1.3=S0. Setting the bit to 1 sets the port
     * pin to push-pull and setting it to 0 sets it in open-drain. If the pin is
     * set to open drain mode, then writing a 1 to the port turns off the pull
     * down. <p> The default (reset state) is that only the servo output pins
     * are set to push-pull mode. P2 is set to open drain.
     *
     * @param p1 port 1 - the first nibble are the servo output pins in reverse
     * order. The upper nibble is masked out to leave bits 4:7 in open drain
     * mode.
     * @param p2 port 2 - on the side of the board.
     */
    public void setPortDOutRegisters(byte p1, byte p2) {
        checkServoCommandThread();
        ServoCommand cmd = new ServoCommand();
        cmd.bytes = new byte[3];
        cmd.bytes[0] = CMD_SET_PORT_DOUT;
        cmd.bytes[1] = (byte) (0x0f & p1);
        cmd.bytes[2] = (byte) (0xff & p2);
        submitCommand(cmd);

    }

    /**
     * Sets the bits of the PCA register that controls PCA clock source.
     *
     * @param source the clock source, e.g. Sysclk
     */
    public void setPCA0MD_CPS_Bits(PCA_ClockSource source) {
        checkServoCommandThread();
        ServoCommand cmd = new ServoCommand();
        cmd.bytes = new byte[2];
        cmd.bytes[0] = CMD_SET_PCA0MD_CPS;
        cmd.bytes[1] = (byte) (0x07 & source.code());
        submitCommand(cmd);
    }

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

    /**
     * encapsulates the servo command bytes that are sent. The first byte is the
     * command specifier, the rest of the bytes are the command itself.
     */
    public class ServoCommand {

        public byte[] bytes;

        @Override
        public boolean equals(Object obj) {
            if ((obj == null) || !(obj instanceof ServoCommand)) {
                return false;
            }
            byte[] otherBytes = ((ServoCommand) obj).bytes;
            if (otherBytes.length != bytes.length) {
                return false;
            }
            for (int i = 0; i < bytes.length; i++) {
                if (bytes[i] != otherBytes[i]) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = (29 * hash) + Arrays.hashCode(this.bytes);
            return hash;
        }
    }

    /**
     * this inner class is a thread that reads the status endpoint and sets
     * values in the instancing class to reflect the status messages that are
     * sent
     */
    private class AsyncStatusThread extends Thread {

        UsbIoPipe inPipe;
        SiLabsC8051F320_USBIO_ServoController enclosingThread;
        boolean stop = false;
        byte msg;

        AsyncStatusThread(SiLabsC8051F320_USBIO_ServoController monitor) {
            this.enclosingThread = monitor;
            setName("AsyncStatusThread");
        }

        public void stopThread() {
            stop = true;
            statusThread.interrupt();
        }

        @Override
        public void run() {
            int status;
            UsbIoBuf buffer = new UsbIoBuf(ENDPOINT_IN_LENGTH); // size of EP1
            inPipe = new UsbIoPipe();
            status = inPipe.bind(0, (byte) ENDPOINT_IN, gDevList, GUID);
            if (status != USBIO_ERR_SUCCESS) {
                log.warning("error binding to pipe for EP1 for device status: " + UsbIo.errorText(status));
            }
            USBIO_PIPE_PARAMETERS pipeParams = new USBIO_PIPE_PARAMETERS();
            pipeParams.Flags = UsbIoInterface.USBIO_SHORT_TRANSFER_OK;
            status = inPipe.setPipeParameters(pipeParams);
            if (status != USBIO_ERR_SUCCESS) {
                log.warning("can't set pipe parameters: " + UsbIo.errorText(status));
            }
            while (!stop && !isInterrupted()) {
                buffer.NumberOfBytesToTransfer = ENDPOINT_IN_LENGTH;
                status = inPipe.read(buffer);
//                log.info("started read, waiting for completion");
                if (status != 0) {
                    log.warning("Stopping status thread: error reading status pipe: " + UsbIo.errorText(status));
                    break;
                }
                status = inPipe.waitForCompletion(buffer);
                if ((status != 0) && (buffer.Status != UsbIoErrorCodes.USBIO_ERR_CANCELED)) {
                    log.warning("Stopping status thread: error waiting for completion of read on status pipe: " + UsbIo.errorText(buffer.Status));
                    break;
                }
                if (buffer.BytesTransferred > 0) {
                    int msg = buffer.BufferMem[0];
                    port2value = msg;
                    log.info("read port2=" + port2value);
                } else {
                    log.warning("warning, 0 bytes in asyncStatusThread");
                }
            }
            log.info("ending thread, closing pipe");
            inPipe.close();

        } // run() status thread
    }

    /**
     * Tests by making the testing GUI.
     *
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {

            @Override
            public void run() {
                new ServoTest().setVisible(true);
            }
        });
    }
}
