/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.caviar.hardwareinterface.usb.linux;

import ch.unizh.ini.caviar.hardwareinterface.HardwareInterface;
import ch.unizh.ini.caviar.hardwareinterface.HardwareInterfaceFactoryInterface;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import javax.usb.UsbDevice;
import javax.usb.UsbException;
import javax.usb.UsbHostManager;
import javax.usb.UsbHub;
import javax.usb.UsbServices;

/**
 * Makes hardware interfaces under linux.
 * @author tobi
 */
public class HardwareInterfaceFactoryLinux implements HardwareInterfaceFactoryInterface {
    static Logger log=Logger.getLogger("HardwareInterfaceFactoryLinux");
    private static HardwareInterfaceFactoryLinux instance=new HardwareInterfaceFactoryLinux();
    private ArrayList<HardwareInterface> interfaceList = null;
    private UsbHub virtualRootUsbHub = null;

    /** Creates a new instance of HardwareInterfaceFactoryLinux, private because this is a singleton factory class */
    private HardwareInterfaceFactoryLinux() {
    }
    
    /** Use this instance to access the methods, e.g. <code>HardwareInterfaceFactoryLinux.instance().getNumInterfacesAvailable()</code>.
     @return the singleton instance.
     */
    public static HardwareInterfaceFactoryLinux instance() {
        return instance;
    }
    
    
    private void buildInterfaceList(){
        if(!System.getProperty("os.name").startsWith("Linux")) return; // only under linux
        virtualRootUsbHub=getVirtualRootUsbHub();
         List usbDeviceList = getUsbDevicesWithId(virtualRootUsbHub, (short)0x0547, (short)0x8701);
         //usbDeviceList.addAll(getUsbDevicesWithId(virtualRootUsbHub, (short)0x0547, (short)0x8700));
		        
        // build a list of linux USB compatible devices, store it in interfaceList
        interfaceList = new ArrayList<HardwareInterface>();
        for (int i=0;i<usbDeviceList.size();i++)
            interfaceList.add(new CypressFX2RetinaLinux((UsbDevice)usbDeviceList.get(i)));
        log.info(interfaceList.size() + " retinas added.");
    }
    
    /** Says how many total of all types of hardware are available
     @return number of devices 
     */
    public int getNumInterfacesAvailable(){
        buildInterfaceList();
        return interfaceList.size();
    }
    
    /** @return first available interface, starting with CypressFX2 and then going to SiLabsC8051F320 */
    public HardwareInterface getFirstAvailableInterface(){
        return getInterface(0);
    }
    
    /** build list of devices and return the n'th one, 0 based */
    public HardwareInterface getInterface(int n) {
//        buildInterfaceList();
        if(interfaceList==null || interfaceList.size()==0) return null;
        if(n>interfaceList.size()-1) return null;
        else {
            HardwareInterface hw=interfaceList.get(n);
//            log.info("HardwareInterfaceFactoryLinux.getInterace("+n+")="+hw);
            return hw;
        }
    }
    
   /**
     * Get the virtual root UsbHub.
     * @return The virtual root UsbHub.
     */
    public static UsbHub getVirtualRootUsbHub()
    {
            UsbServices services = null;
            UsbHub virtualRootUsbHub = null;

            /* First we need to get the UsbServices.
             * This might throw either an UsbException or SecurityException.
             * A SecurityException means we're not allowed to access the USB bus,
             * while a UsbException indicates there is a problem either in
             * the javax.usb implementation or the OS USB support.
             */
            try {
                    services = UsbHostManager.getUsbServices();
            } catch ( Exception uE ) {
                    throw new RuntimeException("Error : " + uE.getMessage());
            }

            /* Now we need to get the virtual root UsbHub,
             * everything is connected to it.  The Virtual Root UsbHub
             * doesn't actually correspond to any physical device, it's
             * strictly virtual.  Each of the devices connected to one of its
             * ports corresponds to a physical host controller located in
             * the system.  Those host controllers are (usually) located inside
             * the computer, e.g. as a PCI board, or a chip on the mainboard,
             * or a PCMCIA card.  The virtual root UsbHub aggregates all these
             * host controllers.
             *
             * This also may throw an UsbException or SecurityException.
             */
            try {
                    virtualRootUsbHub = services.getRootUsbHub();
            } catch ( UsbException uE ) {
                    throw new RuntimeException("Error : " + uE.getMessage());
            } catch ( SecurityException sE ) {
                    throw new RuntimeException("Error : " + sE.getMessage());
            }

            return virtualRootUsbHub;
    }

    /**
     * Get a List of all devices that match the specified vendor and product id.
     * @param usbDevice The UsbDevice to check.
     * @param vendorId The vendor id to match.
     * @param productId The product id to match.
     * @param A List of any matching UsbDevice(s).
     */
    public static List getUsbDevicesWithId(UsbDevice usbDevice, short vendorId, short productId)
    {
            List list = new ArrayList();

            /* A device's descriptor is always available.  All descriptor
             * field names and types match exactly what is in the USB specification.
             * Note that Java does not have unsigned numbers, so if you are 
             * comparing 'magic' numbers to the fields, you need to handle it correctly.
             * For example if you were checking for Intel (vendor id 0x8086) devices,
             *   if (0x8086 == descriptor.idVendor())
             * will NOT work.  The 'magic' number 0x8086 is a positive integer, while
             * the _short_ vendor id 0x8086 is a negative number!  So you need to do either
             *   if ((short)0x8086 == descriptor.idVendor())
             * or
             *   if (0x8086 == UsbUtil.unsignedInt(descriptor.idVendor()))
             * or
             *   short intelVendorId = (short)0x8086;
             *   if (intelVendorId == descriptor.idVendor())
             * Note the last one, if you don't cast 0x8086 into a short,
             * the compiler will fail because there is a loss of precision;
             * you can't represent positive 0x8086 as a short; the max value
             * of a signed short is 0x7fff (see Short.MAX_VALUE).
             *
             * See javax.usb.util.UsbUtil.unsignedInt() for some more information.
             */
            if (vendorId == usbDevice.getUsbDeviceDescriptor().idVendor() &&
                    productId == usbDevice.getUsbDeviceDescriptor().idProduct())
                    list.add(usbDevice);

            /* this is just normal recursion.  Nothing special. */
            if (usbDevice.isUsbHub()) {
                    List devices = ((UsbHub)usbDevice).getAttachedUsbDevices();
                    for (int i=0; i<devices.size(); i++)
                            list.addAll(getUsbDevicesWithId((UsbDevice)devices.get(i), vendorId, productId));
            }

            return list;
    }

}
