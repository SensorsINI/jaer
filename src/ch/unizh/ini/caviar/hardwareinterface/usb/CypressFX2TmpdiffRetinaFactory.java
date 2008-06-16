/*
 * USBAEMon.java
 *
 * Created on February 17, 2005, 7:54 AM
 */
package ch.unizh.ini.caviar.hardwareinterface.usb;

import de.thesycon.usbio.PnPNotifyInterface;
import de.thesycon.usbio.*;
import de.thesycon.usbio.structs.USB_DEVICE_DESCRIPTOR;
import java.util.*;
import ch.unizh.ini.caviar.hardwareinterface.*;


/**
 *
 *Builds the USBInterface to the CypressFX2 Tmpdiff retina board.
 Singleton class, use instance() to get to the factory methods.
 *This relationship is somewhat complicated for the Cypress
 because the UsbIo object apparently cannot be used again to Open after the device
 *is Close'd.
 *
 * @author  tobi
 @deprecated Use the CypressFX2Factory instead,
 otherwise you cannot build multiple devices of different types based on CypressFX2 driver.
 */
public class CypressFX2TmpdiffRetinaFactory implements UsbIoErrorCodes, PnPNotifyInterface, HardwareInterfaceFactoryInterface {
    
    int status;
    //    UsbIoPipe gUsbIoPipe=null;
    
    PnPNotify pnp=null;
    private static CypressFX2TmpdiffRetinaFactory instance=new CypressFX2TmpdiffRetinaFactory();
    
    /** Creates a new instance of USBAEMonitor. Note that it is possible to construct several instances
     * and use each of them to open and read from the same device.
     */
    CypressFX2TmpdiffRetinaFactory() {
        if(UsbIoUtilities.usbIoIsAvailable){
            pnp=new PnPNotify(this);
            pnp.enablePnPNotification(GUID);
            buildUsbIoList();
        }
    }
    
    /** @return singleton instance */
    public static HardwareInterfaceFactoryInterface instance(){
        return instance;
    }
    
    public void onAdd() {
        System.err.println("CypressFX2Factory.onAdd(): device added");
        buildUsbIoList();
    }
    
    public void onRemove() {
        System.err.println("CypressFX2Factory.onRemove(): device removed");
        buildUsbIoList();
    }
    
//    public void OnAdd() {
//        System.err.println("CypressFX2Factory.onAdd(): device added");
//        buildUsbIoList();
//    }
//
//    public void OnRemove() {
//        System.err.println("CypressFX2Factory.onRemove(): device removed");
//        buildUsbIoList();
//    }
    
    
    /** driver guid (Globally unique ID, for this USB driver instance */
    public final static String GUID = CypressFX2.GUID;//"{325ddf96-938c-11d3-9e34-0080c82727f4}";  // working from MouseSimple
    //    String guid = "{7794C79A-40A7-4a6c-8A29-DA141C20D78C}"; // see guid.txt at root of CypressFX2USB2
    //    String guid="{96e73b6e-7a5a-11d4-9f24-0080c82727f4}";  // from default usbiowiz.inf file
    
    int gDevList; // 'handle' (an integer) to an internal device list static to UsbIo
    
    ArrayList<UsbIo> usbioList=null;
    
    void buildUsbIoList(){
        usbioList=new ArrayList<UsbIo>();
        if(!UsbIoUtilities.usbIoIsAvailable) return;
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
        final int MAXDEVS=8;
        
        UsbIo dev;
        gDevList=UsbIo.createDeviceList(GUID);
        int numDevs=0;
        for(int i=0;i<MAXDEVS;i++){
            dev=new UsbIo();
            int status=dev.open(i, gDevList, GUID);
            
            if(status==USBIO_ERR_NO_SUCH_DEVICE_INSTANCE) {
                numDevs=i;
                break;
            }else{
                //        System.out.println("CypressFX2.openUsbIo(): UsbIo opened the device");
                // get device descriptor (possibly before firmware download, when still bare cypress device or running off EEPROM firmware)
                USB_DEVICE_DESCRIPTOR deviceDescriptor=new USB_DEVICE_DESCRIPTOR();
                status = dev.getDeviceDescriptor(deviceDescriptor);
                if (status != USBIO_ERR_SUCCESS) {
                    UsbIo.destroyDeviceList(gDevList);
                    System.err.println("CypressFX2TmpdiffRetinaFactory.openUsbIo(): getDeviceDescriptor: "+UsbIo.errorText(status));
                } else if(deviceDescriptor.idVendor==CypressFX2.VID && deviceDescriptor.idProduct==CypressFX2.PID_TMPDIFF128_RETINA) {
                    // we check here to see if this device has VID/PID of retina, if so, we add it to the list of this type of interface
                    usbioList.add(dev);
                } else if(deviceDescriptor.idVendor==CypressFX2.VID_THESYCON && deviceDescriptor.idProduct==CypressFX2.PID_DVS128_REV0) {
                    // we check here to see if this device has VID/PID of retina, if so, we add it to the list of this type of interface
                    usbioList.add(dev);
                }
                
                dev.close();
            }
        }
        UsbIo.destroyDeviceList(gDevList); // we got number of devices, done with list
    }
    
    public USBInterface getFirstAvailableInterface() {
        return getInterface(0);
    }
    
    /**@param n the number to instance (0 based) */
    public  USBInterface getInterface(int n){
        int numAvailable=getNumInterfacesAvailable();
        if(n>numAvailable-1){
            System.err.println("CypressFX2Factory.getInterface(int): only "+numAvailable+" interfaces available but you asked for number "+n);
            return null;
        }
        // open device
        
        return new CypressFX2TmpdiffRetina(n); // leave all the UsbIo stuff inside the device class
//
//        UsbIo usbio=usbioList.get(n);
//
//        int status = usbio.Open(n,gDevList,CypressFX2.GUID);
//        if (status != USBIO_ERR_SUCCESS) {
//            UsbIo.DestroyDeviceList(gDevList);
//            System.err.println("CypressFX2Factory.getInterface(int): can't open USB device: "+UsbIo.ErrorText(status));
//            return null;
//        }
//        USBInterface u=new CypressFX2(usbio); // we pass the UsbIo object to the constructor to use for all usb calls.
//        return u;
    }
    
    public int getNumInterfacesAvailable() {
        buildUsbIoList();
//        System.out.println(instance.usbioList.size()+" CypressFX2 interfaces available ");
        return instance.usbioList.size();
    }
    
}

