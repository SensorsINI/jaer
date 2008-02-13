/*
 * CypressFX2MonitorSequencerFactory.java
 *
 * Created on 29 de noviembre de 2005, 9:47
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.caviar.hardwareinterface.usb;

import de.thesycon.usbio.PnPNotifyInterface;
import de.thesycon.usbio.*;
import de.thesycon.usbio.structs.*;
import java.util.*;
import ch.unizh.ini.caviar.hardwareinterface.*;
import java.util.logging.Logger;

/**
 * Manufactures CypressFX2MonitorSequencer objects. This class is used in HardwareInterfaceFactory or it can be directly accessed
 *
 * @author raphael
 *@see ch.unizh.ini.caviar.hardwareinterface.HardwareInterfaceFactory
 @deprecated Use the CypressFX2Factory instead, otherwise you cannot build multiple devices of different types based on CypressFX2 driver.
 */
public class CypressFX2MonitorSequencerFactory implements UsbIoErrorCodes, PnPNotifyInterface, HardwareInterfaceFactoryInterface {
    static Logger log=Logger.getLogger("CypressFX2MonitorSequencerFactory");
    int status;
    
    PnPNotify pnp=null;
	
	//static instance, by which this class can be accessed
    private static CypressFX2MonitorSequencerFactory instance=new CypressFX2MonitorSequencerFactory();
    
    CypressFX2MonitorSequencerFactory()  {
        pnp=new PnPNotify(this);
        pnp.enablePnPNotification(GUID);
        buildUsbIoList();
    }
    
    /** @return singleton instance */
    public static HardwareInterfaceFactoryInterface instance(){
        return instance;
    }
    
    public void onAdd() {
        log.info("CypressFX2MonitorSequencerFactory.onAdd(): device added");
        buildUsbIoList();
    }
    
    public void onRemove() {
        log.info("CypressFX2MonitorSequencerFactory.onRemove(): device removed");
        buildUsbIoList();
    }

    /** driver guid (Globally unique ID, for this USB driver instance */
    //public final static String GUID = "{325ddf96-938c-11d3-9e34-0080c82727f4}";  // working from MouseSimple
    public final static    String GUID = CypressFX2.GUID; // see guid.txt at root of CypressFX2USB2
    //    String guid="{96e73b6e-7a5a-11d4-9f24-0080c82727f4}";  // from default usbiowiz.inf file
    
    /** the UsbIo interface to the device. This is assigned when this particular instance is opened, after enumerating all devices */
    private UsbIo gUsbIo=null;
    
    int gDevList; // 'handle' (an integer) to an internal device list static to UsbIo
    
    ArrayList<UsbIo> usbioList=null;
    
    void buildUsbIoList(){
        usbioList=new ArrayList<UsbIo>();
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
                    log.warning("CypressFX2MonitorSequencer.openUsbIo(): getDeviceDescriptor: "+UsbIo.errorText(status));
                } else if(deviceDescriptor.idVendor==CypressFX2.VID && deviceDescriptor.idProduct==CypressFX2.PID_USBAERmini2) {
                    usbioList.add(dev);                  
                } else if(deviceDescriptor.idVendor==CypressFX2.VID && deviceDescriptor.idProduct==CypressFX2.PID_USBAERmini2_without_firmware) {
                    usbioList.add(dev);
                } else if(deviceDescriptor.idVendor==CypressFX2.VID && deviceDescriptor.idProduct==CypressFX2.PID_USB2AERmapper) {
                    usbioList.add(dev);
                }
             //   numDevs++;
                dev.close();
            }
        }
        UsbIo.destroyDeviceList(gDevList); // we got number of devices, done with list
    }
    
	/** returns the first interface in the list
	 *@return refernence to the first interface in the list
	 */
    public USBInterface getFirstAvailableInterface() {
        return getInterface(0);
    }
    
    /** returns the n-th interface in the list, either USBAERmini2 or USB2AERmapper, depending on PID
	 *@param n the number to instance (0 based) 
	 */
    public  USBInterface getInterface(int n){
        int numAvailable=getNumInterfacesAvailable();
        if(n>numAvailable-1){
            log.warning("CypressFX2Factory.getInterface(int): only "+numAvailable+" interfaces available but you asked for number "+n);
            return null;
        }
        
        UsbIo dev=new UsbIo();
             
        gDevList=UsbIo.createDeviceList(GUID);
     
        int status=dev.open(n, gDevList, GUID);
        
        if (status!=this.USBIO_ERR_SUCCESS)
        {
            log.warning("CypressFX2MonitorSequencerFactory.getInterface: unable to open: "+UsbIo.errorText(status));
            dev.close();
            UsbIo.destroyDeviceList(gDevList);
            return null;
        }
  
        USB_DEVICE_DESCRIPTOR deviceDescriptor=new USB_DEVICE_DESCRIPTOR();
        status = dev.getDeviceDescriptor(deviceDescriptor);
        if (status != USBIO_ERR_SUCCESS) {
              UsbIo.destroyDeviceList(gDevList);
              log.warning("CypressFX2MonitorSequencerFactory.getInterface(): getDeviceDescriptor: "+UsbIo.errorText(status));
              dev.close();
              return null;
        } 
        
        if (deviceDescriptor.idProduct==CypressFX2.PID_USB2AERmapper){
            dev.close();
            UsbIo.destroyDeviceList(gDevList);
            return new CypressFX2Mapper(n);
        } 
        
        dev.close();
        UsbIo.destroyDeviceList(gDevList);
        return new CypressFX2MonitorSequencer(n); // leave all the UsbIo stuff inside the device class
    }
    
    /** @return the number of compatible monitor/sequencer attached to the driver
     */
    public int getNumInterfacesAvailable() {
        buildUsbIoList();
//        System.out.println(instance.usbioList.size()+" CypressFX2 interfaces available ");
        return instance.usbioList.size();
    }
    
	/** display all available CypressFX2MonitorSequencer devices controlled by USBIO driver 
	 */
    public void listDevices() {
        buildUsbIoList();
        
        int numberOfDevices=instance.usbioList.size();
        
        System.out.println(numberOfDevices +" CypressFX2MonitorSequencer interfaces available ");
        
        CypressFX2 dev;
        int[] VIDPID;
        String[] strings;
        gDevList=UsbIo.createDeviceList(GUID);
        int numDevs=0;
        for(int i=0;i<numberOfDevices;i++){
            dev=new CypressFX2(i);
            
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
            }
            catch (HardwareInterfaceException e)
            {
                System.out.println("Error handling device " + i + ": " + e);
			}
        }
        UsbIo.destroyDeviceList(gDevList); 
    }
}