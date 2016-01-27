/*
 * CypressFX2MonitorSequencerFactory.java
 *
 * Created on 29 de noviembre de 2005, 9:47
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */
package net.sf.jaer.hardwareinterface.usb.cypressfx2;

import java.util.ArrayList;
import java.util.logging.Logger;

import ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1bHardwareInterface;
import ch.unizh.ini.jaer.chip.cochlea.CochleaAMS1cHardwareInterface;
import de.thesycon.usbio.PnPNotify;
import de.thesycon.usbio.PnPNotifyInterface;
import de.thesycon.usbio.UsbIo;
import de.thesycon.usbio.UsbIoErrorCodes;
import de.thesycon.usbio.structs.USB_DEVICE_DESCRIPTOR;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.HardwareInterfaceFactoryInterface;
import net.sf.jaer.hardwareinterface.usb.USBInterface;
import net.sf.jaer.hardwareinterface.usb.UsbIoUtilities;
import net.sf.jaer.hardwareinterface.usb.silabs.SiLabsC8051F320_USBIO_AeSequencer;
import net.sf.jaer.hardwareinterface.usb.silabs.SiLabsC8051F320_USBIO_DVS128;
import net.sf.jaer.util.HexString;

/**
 * Manufactures USBIO-driver-based hardware interface; these are mostly CypressFX2-based but also include
 * recent SiLabs boards. This class is used in
HardwareInterfaceFactory or it can be directly accessed.
 * The appropriate device class is constructed based on the device PID; all devices share the common Thesycon VID owned by jAER.
 * All device here use a common GUID and device driver.
 *
 * @author tobi/raphael
 *@see net.sf.jaer.hardwareinterface.HardwareInterfaceFactory
 */
public class USBIOHardwareInterfaceFactory implements UsbIoErrorCodes, PnPNotifyInterface, HardwareInterfaceFactoryInterface {

    final static Logger log = Logger.getLogger("USBIOHardwareInterfaceFactory");
//    int status;
    PnPNotify pnp = null;    //static instance, by which this class can be accessed
    private static USBIOHardwareInterfaceFactory instance = new USBIOHardwareInterfaceFactory();
    static boolean firstUse = true;

    USBIOHardwareInterfaceFactory() {
        UsbIoUtilities.enablePnPNotification(this, GUID);
//        if (UsbIoUtilities.isLibraryLoaded()) {
//            pnp = new PnPNotify(this);
//            int status = pnp.enablePnPNotification(USBIOHardwareInterfaceFactory.GUID);
//            if (status != UsbIoErrorCodes.USBIO_ERR_SUCCESS) {
//                log.warning("Could not enable PnP notification for GUID " + GUID + ", got error " + UsbIo.errorText(status));
//            }
//        }
    }

    /** @return singleton instance */
    public static HardwareInterfaceFactoryInterface instance() {
        return instance;
    }

    @Override
    synchronized public void onAdd() {
        log.info("device added, rebuilding list");
        buildUsbIoList();
    }

    @Override
    synchronized public void onRemove() {
        log.info("device removed");
        firstUse = false;
//        buildUsbIoList(); // TODO why no rebuild?
    }
    /** driver guid (Globally unique ID, for this USB driver instance */
    public final static String GUID = CypressFX2.GUID; // see guid.txt at root of CypressFX2USB2

    @Override
    public String getGUID() {
        return GUID;
    }
    /** the UsbIo interface to the device. This is assigned when this particular instance is opened, after enumerating all devices */
    private UsbIo gUsbIo = null;
    private long gDevList; // 'handle' (an integer) to an internal device list static to UsbIo
    private ArrayList<UsbIo> usbioList = null;

    private void maybeBuildUsbIoList() {
        if (firstUse) {
            buildUsbIoList();
            firstUse = false;
        }
    }

    synchronized void buildUsbIoList() {
        usbioList = new ArrayList<UsbIo>();
        if (!UsbIoUtilities.isLibraryLoaded()) {
            return;
            /* from USBIO reference manual for C++ method Open
            Comments
            There are two options:
            (A) DeviceList != NULL
            The device list provided in DeviceList must have been built using
            CUsbIo::CreateDeviceList. The GUID that identifies the device interface must be
            provided in InterfaceGuid. DeviceNumber is used to iterate through the device list. It
            should start with zero and should be incremented after each call to Open. If no more
            instances of the interface are available then the status code
            USBIO_ERR_NO_SUCH_DEVICE_INSTANCE is returned.
            Note: This is the recommended way of implementing a device enumeration.
             */
        }
        final int MAXDEVS = 8;

        UsbIo dev;
        setGDevList(UsbIo.createDeviceList(GUID));
//        System.out.println("device list for " + this);
        for (int i = 0; i < MAXDEVS; i++) {
            dev = new UsbIo();
            int status = dev.open(i, getGDevList(), GUID);

            if (status == USBIO_ERR_NO_SUCH_DEVICE_INSTANCE) {
//                log.info("USBIO_ERR_NO_SUCH_DEVICE_INSTANCE: no device found at list address "+i);
                break;
            } else {
                //        System.out.println("CypressFX2.openUsbIo(): UsbIo opened the device");
                // get device descriptor (possibly before firmware download, when still bare cypress device or running off EEPROM firmware)
//                USB_DEVICE_DESCRIPTOR deviceDescriptor = new USB_DEVICE_DESCRIPTOR();
//                status = dev.getDeviceDescriptor(deviceDescriptor);
//                if (status != USBIO_ERR_SUCCESS) {
//                    UsbIo.destroyDeviceList(gDevList);
//                    log.warning(UsbIo.errorText(status));
//                } else {
                int status2 = dev.acquireDevice();
                if (status2 == USBIO_ERR_SUCCESS) { // only add device if it can be exclusively bound. If devices are alredy opened exclusively, then they will not appear in the list
                    usbioList.add(dev);
                    log.info("found device with UsbIo device handle "+dev+" at list address "+i);
                }
//                System.out.println("added "+dev);
//                }
                dev.close();
            }
        }
        UsbIo.destroyDeviceList(getGDevList()); // we got number of devices, done with list
    }

    /** returns the first interface in the list
     *@return reference to the first interface in the list
     */
    @Override
	synchronized public USBInterface getFirstAvailableInterface() {
        return getInterface(0);
    }

    /** returns the n-th interface in the list, either Tmpdiff128Retina, USBAERmini2 or USB2AERmapper, DVS320, or MonitorSequencer depending on PID,
     * <p>
     * For unknown or blank device PID a bare CypressFX2 is returned which should be discarded
     * after it is used to download to the device RAM some preferred default firmware.
     * A new CypressFX2 should then be manufactured that will be correctly constructed here.
     * <p>
     * This method hardcodes the mapping from VID/PID and the HardwareInterface object that is contructed for it.
     *
     *@param n the number to instance (0 based)
     */
    @Override
	synchronized public USBInterface getInterface(int n) {
        int numAvailable = getNumInterfacesAvailable();
        if (n > (numAvailable - 1)) {
            if (numAvailable == 0) {
                log.warning("You asked for interface number " + n + " but no interfaces are available. Check the Windows Device Manager to see if the device has been recognized. You may need to install a driver.");
            } else {
                log.warning("Only " + numAvailable + " interfaces available but you asked for number " + n + " (0 based)");
            }
            return null;
        }

        UsbIo dev = new UsbIo();

        setGDevList(UsbIo.createDeviceList(GUID));

        int status = dev.open(n, getGDevList(), GUID);

        if (status != USBIO_ERR_SUCCESS) {
            log.warning("interface " + n + ": opening device returned error " + UsbIo.errorText(status));
            dev.close();
            UsbIo.destroyDeviceList(getGDevList());
            return null;
        }

        USB_DEVICE_DESCRIPTOR deviceDescriptor = new USB_DEVICE_DESCRIPTOR();
        status = dev.getDeviceDescriptor(deviceDescriptor);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(getGDevList());
            log.warning("interface " + n + ": getting device descriptor (VID/PID/DID) returned error " + UsbIo.errorText(status));
            dev.close();
            return null;
        }

        dev.close();
        UsbIo.destroyDeviceList(getGDevList());
        short vid = (short) (0xffff & deviceDescriptor.idVendor);
        short pid = (short) (0xffff & deviceDescriptor.idProduct); // for some reason returns 0xffff8613 from blank cypress fx2
        short did = (short) (0xffff & deviceDescriptor.bcdDevice); // for some reason returns 0xffff8613 from blank cypress fx2
        // TODO fix this so that PID is parsed by reflection or introspection from hardwareinterface classes

        // here the PID switches to construct the appropriate hardwareinterface.
        // add new PIDs here for this VID
        switch (pid) {
            case CypressFX2.PID_USB2AERmapper:
                return new CypressFX2Mapper(n);
            case CypressFX2.PID_DVS128_REV0:
                //    case CypressFX2.PID_TMPDIFF128_FX2_SMALL_BOARD:  // VID/PID replaced with the ones from thesycon
                return new CypressFX2DVS128HardwareInterface(n);
            case CypressFX2.PID_TMPDIFF128_RETINA:

                if (did == CypressFX2.DID_STEREOBOARD) {
                    return new CypressFX2StereoBoard(n);
                    //System.out.println(did);
                }
                return new CypressFX2TmpdiffRetinaHardwareInterface(n);
            case CypressFX2.PID_USBAERmini2:
                return new CypressFX2MonitorSequencer(n);
            case SiLabsC8051F320_USBIO_AeSequencer.PID:
                return new SiLabsC8051F320_USBIO_AeSequencer(n);
            case CochleaAMS1bHardwareInterface.PID:
                return new CochleaAMS1bHardwareInterface(n);
            case CochleaAMS1cHardwareInterface.PID:
                return new CochleaAMS1cHardwareInterface(n);
            case SiLabsC8051F320_USBIO_DVS128.PID:
                return new SiLabsC8051F320_USBIO_DVS128(n);
            default:
                log.warning("PID=" + HexString.toString(pid) + " doesn't match any device, returning bare CypressFX2 instance");
                return new CypressFX2(n);
        }
    }

    /** @return the number of compatible monitor/sequencer attached to the driver
     */
    @Override
	synchronized public int getNumInterfacesAvailable() {

        maybeBuildUsbIoList();
        firstUse = true;
//        System.out.println(instance.usbioList.size()+" CypressFX2 interfaces available ");
        return instance.usbioList.size();
    }

    /** display all available CypressFX2 devices controlled by USBIO driver
     */
    synchronized public void listDevices() {
        maybeBuildUsbIoList();

        int numberOfDevices = instance.usbioList.size();

//        System.out.println(numberOfDevices + " CypressFX2 interfaces available ");

        CypressFX2 dev;
        int[] VIDPID;
        String[] strings;
        setGDevList(UsbIo.createDeviceList(GUID));
        for (int i = 0; i < numberOfDevices; i++) {
            dev = new CypressFX2(i);

            try {
                // printing the device information not necessary, gets printed in dev.open()
                System.out.println("----------------------------");
                System.out.println("  Device number: " + i);
                System.out.println("----------------------------");
                dev.open();
                /*VIDPID = dev.getVIDPID();
                System.out.println(", VID: "+ VIDPID[0] +", PID: "+ VIDPID[1]);

                strings=dev.getStringDescriptors();

                System.out.println("Manufacturer: " + strings[0] + ", device type: " + strings[1]);

                if (dev.getNumberOfStringDescriptors()==3)
                {
                System.out.println("Device name: " + strings[2]);
                }*/
                System.out.println("----------------------------");
                System.out.println("");
                dev.close();
            } catch (HardwareInterfaceException e) {
                System.out.println("Error handling device " + i + ": " + e);
            }
        }
        UsbIo.destroyDeviceList(getGDevList());
    }

    /** Handle used to refer to UsbIo's internal list of devices, built here on construction and for each add and remove
    @return the the handle
     */
    synchronized public long getGDevList() {
        return gDevList;
    }

    synchronized public void setGDevList(long gDevList) {
        this.gDevList = gDevList;
    }
//    public HardwareInterface getFirstAvailableInterfaceForChip(Chip chip) {
//        throw new UnsupportedOperationException("Not supported yet.");
//    }
}