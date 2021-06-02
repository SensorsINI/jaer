/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.hardwareinterface.usb.toradex;

import java.beans.PropertyChangeSupport;
import java.util.logging.Logger;

import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.UsbIoUtilities;
import de.thesycon.usbio.UsbIoBuf;
import de.thesycon.usbio.UsbIoErrorCodes;
import de.thesycon.usbio.UsbIoReader;
import de.thesycon.usbio.structs.USBIO_SET_CONFIGURATION;

/**
 * Interfaces to the <a href="http://www.toradex.com">Toradex</a> Oak G 3 axis acceleration sensor.
 * See <a href="http://www.toradex.com/downloads/Oak%20G%20Datasheet%20V0100.pdf">Oak G user guide</a>.)
 *
 * <p>
 * To use this sensor, you must update the driver to the Thesycon USBIO driver in drivers/driver_ToradexAccelerometer.
 * This takes a manual installation because Windows will always install the HID driver if it is allowed to.
 * This installation is a bit tricky. Follow these steps:
 * <ol>
 * <li>Plug in the sensor. Windows will install the default HID driver.</li>
 * <li>Open the device manager. 
 * Find the "Human Interface Devices" device named "USB Human Interface Device" with the VID of 0x1b67 and PID of 0x000a. 
 * You can find this by double-clicking the device and clicking on the "Details" tab. Don't choose your mouse by mistake. </li>
 * <li>Choose "Update driver" from the Driver tab. Choose "No, not this time" and 
 * "Install from a list or specific location". Then choose "Don't search,
 * I will choose the driver to install." Choose "Have Disk..." and navigate to the driver_ToradexAccelerometer" 
 * folder in the drivers folder of jAER. Choose the "usbio.inf" file there.</li>
 * <li>Now the "USBIO Toradex Oak G Accelerometer" should appear in the Hardware Update Wizard chooser. Select this device.
 * </ol>
 *
 * <p>
 * If you want to set sample rate or other parameters, then you need to install the default windows HID driver and use the OakG software to change
 * the settings. Toradex does not sufficiently document their report format to allow customization except through their own (C) library functions.
 * To avoid a JNI interface maintainance hassle, we use a pure java Thesycon interface here.
 * 
 * 
 * @author tobi
 */
public class ToradexOakG3AxisAccelerationSensor extends UsbIoReader implements HardwareInterface, UsbIoErrorCodes {

    /*
     * 	// Data that is available about each single data channel.
    // An array of this type is used as part of the tOakSensor struct below.
    typedef ALIGN8 struct
    {
    ALIGN1	bool		IsSigned;
    ALIGN1	BYTE		BitSize;
    ALIGN1	signed char UnitExponent;
    ALIGN4	ULONG		Unit;
    ALIGN8	TCHAR		UnitStr[24];
    ALIGN8	TCHAR		ChannelName[24];
    ALIGN8	TCHAR		UserChannelName[24];
    ALIGN8	TCHAR		UserChannelName_NV[24];
    ALIGN8  BYTE		RFU[64]; // reserved for future use
    } tChannel;
    // Information that is available about a connected sensor
    typedef ALIGN8 struct
    {
    ALIGN8	TCHAR		DevicePath [256];
    ALIGN2	WORD		VID;
    ALIGN2	WORD		PID;
    ALIGN2	WORD		REV;
    ALIGN8	TCHAR		SN[24];
    ALIGN8  BYTE		RFU[64]; // reserved for future use
    ALIGN8	TCHAR		DeviceName[24];
    ALIGN8	TCHAR		UserDeviceName[24];
    ALIGN8	TCHAR		UserDeviceName_NV[24];
    ALIGN8	WORD		NumChannels;
    ALIGN8	tChannel	Channel[MAX_NO_CHANNELS];
    } tOakSensor;
    //---------------------------------------------------------------------------
    // Functions to communicate with the sensors
    //---------------------------------------------------------------------------
    // Send and Receive a Feature Report to/from the sensor device
    //		- "RptBuf" will be sent to the sensor. 
    //		- The function then waits until the sensor has the result ready ( if there is any). 
    //		- "RptBuf" will finally be overwritten by the sensor's answer.
    extern "C" __declspec(dllexport) bool Oak_Feature (LPTSTR DevicePath, BYTE RptBuf[33], bool ExpectResult);
    //---------------------------------------------------------------------------
    // Functions to Read Sensor values
    //---------------------------------------------------------------------------
    // Toradex recommends to use standard windows functions: 
    //  DeviceHandle = Createfile (DevicePath, ...);
    //  ReadFile(DeviceHandle, ReadBuffer, ...);
    //  CloseHandle(DeviceHandle);
     */
    static Logger log = Logger.getLogger("ToradexOakG3AxisAccelerationSensor");
    int seqNum = 0;
    private long gDevList;
    public static String GUID = "{A08B149E-81AC-47b0-988F-52FDC1BB1E57}";
    private final int REP_LEN = 8;
    private byte ENDPOINT_ADDRESS = (byte) 0x82;
    byte[] bytes;
    public static final float TIME_SCALE = 1e-3f;
    public static final float ACCEL_SCALE = 1e-3f;
    public static final String TIME_UNIT = "s";
    public static final String ACCEL_UNIT = "m/s^2";
    private PropertyChangeSupport support = new PropertyChangeSupport(this);
    public static final float MAX_ACCEL = 20.48f; // m/s^2 ACCEL_SCALE * ((2 << 11) - 1)/2;
    Acceleration vec=new Acceleration();
    private float timeWrap=0;
    private final float TIME_WRAP=2.048f;
    private int lastSampleTime=0;
    
    /** Constructs a new ToradexOakG3AxisAccelerationSensor. Use open() to start reading valoues. */
    public ToradexOakG3AxisAccelerationSensor() {
    }

    @Override
    public void processBuffer(UsbIoBuf buf) {
//        System.out.println("process buffer");
        buf.NumberOfBytesToTransfer = buf.Size;
        buf.BytesTransferred = 0;
        buf.OperationFinished = false;
    }

    @Override
    public void processData(UsbIoBuf buf) {
        int t, v;
//        System.out.println("process data");
        if (buf.Status == USBIO_ERR_SUCCESS || buf.Status == USBIO_ERR_CANCELED) {
            if (buf.BytesTransferred < REP_LEN) {
                log.warning("only transferred " + buf.BytesTransferred + " bytes, should have gotten " + REP_LEN);
                return;
            }
//            System.out.println("transferred " + buf.BytesTransferred + " bytes");
            byte[] b = buf.BufferMem;
            t = ((0xff & b[1]) << 8) + (0xff & b[0]); // force int casts on signed chars, samples come by default every 100 counts = 100 ms = 10 Hz
            vec.t=TIME_SCALE * t; // time wraps every 2048 = 11 bits
            if(t<lastSampleTime){
                timeWrap+=TIME_WRAP;
            }
            vec.t+=timeWrap;
            lastSampleTime=t;
            // data is sent little endian, 16 bit ints, signed with sign bit in msb of MSB
            vec.x=((0xff & b[2]) + (b[3] << 8))*ACCEL_SCALE;
            vec.y=((0xff & b[4]) + (b[5] << 8))*ACCEL_SCALE;
            vec.z=((0xff & b[6]) + (b[7] << 8))*ACCEL_SCALE;
//            System.out.println(String.format("%10.3f: %10.2f  %10.2f %10.2f", vec.t, vec.x, vec.y, vec.z));
        } else {
            log.warning(errorText(buf.Status));
        }
        support.firePropertyChange("acceleration", null, vec);
    }

    @Override
    public void bufErrorHandler(UsbIoBuf buf) {
        log.warning(errorText(buf.Status));
    }

    @Override
    public void onThreadExit() {
        freeBuffers();
    }

    public String getTypeName() {
        return "3-axis acceleration sensor";
    }

    public void open() throws HardwareInterfaceException {
        if (!UsbIoUtilities.isLibraryLoaded()) {
            return;
        }
        if(isOpen()) {
            log.info("already open");
            return;
        }
        int status = 0;
        gDevList = createDeviceList(GUID);
        if (gDevList == 0) {
            throw new HardwareInterfaceException("error on createDeviceList, perhaps Thesycon driver not installed?");
        }

//        status = bind(0, ENDPOINT_ADDRESS, gDevList, GUID);
        status = open(0, gDevList, GUID);
        if (status != USBIO_ERR_SUCCESS) {
            destroyDeviceList(gDevList);
            throw new HardwareInterfaceException(errorText(status));
        }
        status = acquireDevice(); // exclusive access
        if (status != USBIO_ERR_SUCCESS) {
            destroyDeviceList(gDevList);
            throw new HardwareInterfaceException(errorText(status));
        }

        USBIO_SET_CONFIGURATION Conf = new USBIO_SET_CONFIGURATION();
        Conf.ConfigurationIndex = 0;
        Conf.NbOfInterfaces = 1;
        Conf.InterfaceList[0].InterfaceIndex = 0;
        Conf.InterfaceList[0].AlternateSettingIndex = 0;
        Conf.InterfaceList[0].MaximumTransferSize = 64;
        status = setConfiguration(Conf);
        if (status != USBIO_ERR_SUCCESS) {
            log.warning("setting configuration: " + errorText(status));
        }

        status = bind(0, ENDPOINT_ADDRESS, gDevList, GUID);
        if(status!=USBIO_ERR_SUCCESS){
            throw new HardwareInterfaceException(errorText(status));
        }

        // following is preliminary attempt to penetrate the obsucre feature report format of the sensor.  not successful.
        // you must install the device using the default HID driver and use the Toradoex Oak demo application to access the registers,
        // then install the UsbIo driver to use it in jAER.
        
//        for (int k = 0; k < 10; k++) {
//            USBIO_DATA_BUFFER buf = new USBIO_DATA_BUFFER(256);
//            buf.setNumberOfBytesToTransfer(256);
//            USBIO_CLASS_OR_VENDOR_REQUEST req = new USBIO_CLASS_OR_VENDOR_REQUEST();
//            req.Flags = USBIO_SHORT_TRANSFER_OK;
//            req.Request = 0x01; // get report
//            req.Value = (short) 0x0100;
//            req.Index = (short) k; // 0x0002;
//            req.Type = 1; // class
//            req.Recipient = 1; // interface
//            status = classOrVendorInRequest(buf, req);
//            if (status != USBIO_ERR_SUCCESS) {
//                log.warning(errorText(status));
//            }
//            System.out.println("index="+k+" result="+new String(buf.Buffer()));
//        }
        
        startThread(0);
        log.info("Opened, max acceleration="+MAX_ACCEL);
    }

    public void startThread(int MaxIoErrorCount) {
        allocateBuffers(REP_LEN, 4);
        super.startThread(MaxIoErrorCount);
        T.setPriority(Thread.NORM_PRIORITY); // very important that this thread have priority or the acquisition will stall on device side for substantial amounts of time!
        T.setName("ToradexOakG3AxisAccelerationSensor");
    }

    public static final void main(String[] args) {
        ToradexOakG3AxisAccelerationSensor sensor;
        try {
            sensor = new ToradexOakG3AxisAccelerationSensor();
            sensor.open();
            Thread.currentThread().sleep(30000);
            sensor.shutdownThread();
            sensor.close();
        } catch (InterruptedException ex) {

        } catch (HardwareInterfaceException ex) {
            ex.printStackTrace();
        }
    }
    
    /** Returns the latest sampled acceleration values. Note that this method is thread-safe but the underlying Acceleration object can change
     * its contents if a new reading is captured.
     @return the (reused) acceleration object
     */
   synchronized public Acceleration getAcceleration(){
        return vec;
    }

    /** Get the support and add yourself as a property change listener to obtain notifications of new sensor readings */
    public PropertyChangeSupport getSupport() {
        return support;
    }
    
    /** Encapsulates the most recent acceleration vector. */
    public class Acceleration{
        /** The time in seconds (arbitrary starting time) */
        public float t=0;
        /** The acceleration in m/s^2 */
        public float x=0,y=0,z=0;
        public String toString(){
            return String.format("%f %f %f %f", t,x,y,z);
        }
    }
}
