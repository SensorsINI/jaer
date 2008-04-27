/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.caviar.hardwareinterface.usb.toradex;

import ch.unizh.ini.caviar.hardwareinterface.HardwareInterface;
import ch.unizh.ini.caviar.hardwareinterface.HardwareInterfaceException;
import ch.unizh.ini.caviar.hardwareinterface.usb.UsbIoUtilities;
import de.thesycon.usbio.UsbIoBuf;
import de.thesycon.usbio.UsbIoErrorCodes;
import de.thesycon.usbio.UsbIoReader;
import de.thesycon.usbio.structs.USBIO_SET_CONFIGURATION;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.Observable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Interfaces to the <a href="http://www.toradex.com">Toradex</a> Oak G 3 axis acceleration sensor.
 * See <a href="http://www.toradex.com/downloads/Oak%20G%20Datasheet%20V0100.pdf">Oak G user guide</a>.)
 * 
 * @author tobi
 */
public class ToradexOakG3AxisAccelerationSensor extends UsbIoReader implements HardwareInterface, UsbIoErrorCodes{
    
    static Logger log = Logger.getLogger("ToradexOakG3AxisAccelerationSensor");
    private float[] accel = new float[3];
    int seqNum = 0;
    int gDevList;
    public static String GUID = "{A08B149E-81AC-47b0-988F-52FDC1BB1E57}";
    private final int REP_LEN = 8;
    private byte ENDPOINT_ADDRESS = (byte) 0x82;
    byte[] bytes;
    public static final float MAX_ACCEL=(2<<16)-1;
    float sampleTime=0;
    public static final float TIME_SCALE=1e-3f;
    public static final float ACCEL_SCALE=1e-4f;
    public static final String TIME_UNIT="s";
    public static final String ACCEL_UNIT="m/s^2";
    private PropertyChangeSupport support=new PropertyChangeSupport(this);

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
//        System.out.println("process data");
        if (buf.Status == USBIO_ERR_SUCCESS || buf.Status == USBIO_ERR_CANCELED) {
            if (buf.BytesTransferred < REP_LEN) {
                log.warning("only transferred " + buf.BytesTransferred + " bytes, should have gotten " + REP_LEN);
                return;
            }
//            System.out.println("transferred " + buf.BytesTransferred + " bytes");
            byte[] b = buf.BufferMem;
            sampleTime = TIME_SCALE*((0xff&b[1]) << 8) + (0xff&b[0]); // force int casts on signed chars 
            int ind = 0; // channel
            // data is sent little endian, 16 bit ints
            for (int i = 0; i < 3; i++) {
                accel[ind++] = ACCEL_SCALE*((0xff&b[i+2]) + ((0xff&b[i + 3]) << 8));
            }
            System.out.println(String.format("%.3f: \t%.2f  \t%.2f \t%.2f", sampleTime, getAccel()[0], getAccel()[1], getAccel()[2]));
//            
//            for (int i = 0; i < buf.BytesTransferred; i++) {
//                System.out.print(buf.BufferMem[i] + " ");
//            }
//            System.out.println("");
        } else {
            log.warning(errorText(buf.Status));
        }
        support.firePropertyChange("acceleration", accel, accel);
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
        if (!UsbIoUtilities.usbIoIsAvailable) {
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
        status=acquireDevice(); // exclusive access
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
        startThread(0);
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

    /** Returns the latest sampled acceleration values 
     * @return a 3 vector of acceleration values from 0 to 32k
     */
    public float[] getAccel() {
        return accel;
    }

    public PropertyChangeSupport getSupport() {
        return support;
    }
}
