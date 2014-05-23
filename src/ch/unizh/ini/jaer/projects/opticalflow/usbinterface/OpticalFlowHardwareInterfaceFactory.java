/*
 * OpticalFlowHardwareInterfaceFactory.java
 *
 * Created on December 6, 2006, 1:33 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright December 6, 2006 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package ch.unizh.ini.jaer.projects.opticalflow.usbinterface;

import java.util.ArrayList;
import java.util.Collection;

import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.HardwareInterfaceFactoryInterface;
import net.sf.jaer.hardwareinterface.usb.UsbIoUtilities;
import de.thesycon.usbio.PnPNotify;
import de.thesycon.usbio.PnPNotifyInterface;
import de.thesycon.usbio.UsbIo;
import de.thesycon.usbio.UsbIoErrorCodes;
import de.thesycon.usbio.structs.USB_DEVICE_DESCRIPTOR;

/**
 * Makes OpticalFlowHardwareInterface's.
 * 
 * @author tobi
 * 
 * changes by andstein
 * <ul>
 * <li>added the new <cdoe>dsPIC33F_COM_ConfigurationPanel</code></li>
 * <li>added error message in <code>buildUsbIoList</code> that can arise when
 *     different driver version is used</li>
 * <li>added psEYE hardware interface for comparison reasons</li>
 * </ul>
 */
public class OpticalFlowHardwareInterfaceFactory implements UsbIoErrorCodes, PnPNotifyInterface, HardwareInterfaceFactoryInterface {
    
	//static instance, by which this class can be accessed
    private static OpticalFlowHardwareInterfaceFactory instance=new OpticalFlowHardwareInterfaceFactory();
    private String GUID=SiLabsC8051F320_OpticalFlowHardwareInterface.GUID;
    PnPNotify pnp=null;
    /** the UsbIo interface to the device. This is assigned when this particular instance is opened, after enumerating all devices */
    private UsbIo gUsbIo=null;
    
    private long gDevList; // 'handle' (an integer) to an internal device list static to UsbIo
    
    ArrayList<UsbIo> usbioList=null;
    MotionChipInterface[] psEyes;
    // interfaces are cached to re-use allocated ressources
    HardwareInterface cache[];


    /** private constructor for this singleton class.*/
    private OpticalFlowHardwareInterfaceFactory() {
        UsbIoUtilities.enablePnPNotification(this,GUID);
        buildUsbIoList();
        emptyCache();
    }

    private void emptyCache() {
        cache= new HardwareInterface[getNumInterfacesAvailable()];
        for(int i=0; i< getNumInterfacesAvailable(); i++)
            cache[i]= null;
    }

    /** @return singleton instance */
    public static HardwareInterfaceFactoryInterface instance(){
        return instance;
    }

    public int getNumInterfacesAvailable() {
        int ret=1; // the serial interface is "always available"
        if (usbioList != null) ret+= usbioList.size();
        if (psEyes != null) ret+= psEyes.length;
        return ret;
    }

    public HardwareInterface getFirstAvailableInterface() throws HardwareInterfaceException {
        if(getNumInterfacesAvailable()==0) throw new HardwareInterfaceException("no interfaces available");
        return getInterface(0);
    }

    private HardwareInterface createInterface(int n) throws HardwareInterfaceException {
        // we may have any number of SiLabsC8051F320_OpticalFlowHardwareInterface
        if(n< usbioList.size())
            return new SiLabsC8051F320_OpticalFlowHardwareInterface(n);
        n-= usbioList.size();
        // same is true for PS3 eyes
        if (psEyes!=null && n<psEyes.length)
            return psEyes[n];
        // the last hardware interface is by definition the dsPIC33F_COM_OpticalFlowHardwareInterface
        // (that may not be connected; error issued on opening)
        return new dsPIC33F_COM_OpticalFlowHardwareInterface();
    }

    public HardwareInterface getInterface(int n) throws HardwareInterfaceException {
        if(n>=getNumInterfacesAvailable())
            throw new HardwareInterfaceException("asked for interface "+n+" but only "+getNumInterfacesAvailable()+" interfaces are available");

        if (cache[n] == null)
            cache[n]= createInterface(n);

        return cache[n];
    }

    public void onAdd() {
        buildUsbIoList();
        emptyCache();
    }

    public void onRemove() {
        buildUsbIoList();
        emptyCache();
    }
    
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
            }else if (status != USBIO_ERR_SUCCESS) {
                // various errors such as USBIO_ERR_VERSION_MISMATCH
                System.err.println("OpticalFlowHardwareInterfaceFactory.openUsbIo(): open: "+UsbIo.errorText(status));
            } else {
                //        System.out.println("CypressFX2.openUsbIo(): UsbIo opened the device");
                // get device descriptor (possibly before firmware download, when still bare cypress device or running off EEPROM firmware)
                USB_DEVICE_DESCRIPTOR deviceDescriptor=new USB_DEVICE_DESCRIPTOR();
                status = dev.getDeviceDescriptor(deviceDescriptor);
                if (status != USBIO_ERR_SUCCESS) {
                    UsbIo.destroyDeviceList(gDevList);
                    System.err.println("OpticalFlowHardwareInterfaceFactory.openUsbIo(): getDeviceDescriptor: "+UsbIo.errorText(status));
                } else {
                        usbioList.add(dev);
                }
                dev.close();
            }
        }
        UsbIo.destroyDeviceList(gDevList); // we got number of devices, done with list
    }

    
    public Collection<HardwareInterface> getInterfaceList() throws HardwareInterfaceException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getGUID() {
        return GUID;
    }

}
