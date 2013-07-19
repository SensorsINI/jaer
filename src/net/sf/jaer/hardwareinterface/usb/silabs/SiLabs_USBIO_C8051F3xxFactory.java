/*
 * ServoInterfaceFactory.java
 *
 * Created on July 8, 2007, 4:10 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright July 8, 2007 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package net.sf.jaer.hardwareinterface.usb.silabs;

import java.util.ArrayList;
import java.util.logging.Logger;

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
 * The factory for ServoInterface's. You use the singleton instance to find devices and construct
 ServoInterface objects to control them.
 
 * @author tobi
 */
public class SiLabs_USBIO_C8051F3xxFactory implements
        UsbIoErrorCodes,
        PnPNotifyInterface,
        HardwareInterfaceFactoryInterface {
    static Logger log=Logger.getLogger("USB");
    
    int status;
    
    /** driver guid (Globally unique IDs, for this USB driver instance */
    public final static String GUID  = SiLabsC8051F320_USBIO_ServoController.GUID; // tobi generated in pasadena july 2006

    @Override
    public String getGUID() {
        return GUID;
    }
    
    
    
    PnPNotify pnp=null;
    
    // static instance, by which this class can be accessed
    private static SiLabs_USBIO_C8051F3xxFactory instance=new SiLabs_USBIO_C8051F3xxFactory();
    
//    public static Class[] SERVO_CLASSES={
//        SiLabsC8051F320_USBIO_ServoController.class
//    };
    
    /** Creates a new instance of ServoInterfaceFactory. This private constructer is used by the
     singleton.
     */
    private SiLabs_USBIO_C8051F3xxFactory() {
        UsbIoUtilities.enablePnPNotification(this, GUID);
        buildUsbIoList();
    }
    
    /** Returns the singleton instance that is used to construct instances
     @return singleton instance
     */
    public static HardwareInterfaceFactoryInterface instance(){
        return instance;
    }
    
    public void onAdd() {
        log.info("device added");
        buildUsbIoList();
    }
    
    public void onRemove() {
        log.info("device removed");
        buildUsbIoList();
    }
    
    public int getNumInterfacesAvailable() {
        buildUsbIoList();
//        System.out.println(instance.usbioList.size()+" CypressFX2 interfaces available ");
        return instance.usbioList.size();
    }
    
    public HardwareInterface getFirstAvailableInterface() throws HardwareInterfaceException {
        return getInterface(0);
    }
    
    public HardwareInterface getInterface(int n) throws HardwareInterfaceException {
        int numAvailable=getNumInterfacesAvailable();
        if(n>numAvailable-1){
            if(numAvailable>0){ // warn if there is at least one available but we asked for one higher than we have
                log.warning("only "+numAvailable+" interfaces available but you asked for number "+n);
            }
            return null;
        }
        UsbIo dev=new UsbIo();
        gDevList=UsbIo.createDeviceList(GUID);
        int status=dev.open(n, gDevList, GUID);
        if (status!=this.USBIO_ERR_SUCCESS) {
            System.err.println("unable to open: "+UsbIo.errorText(status));
            dev.close();
            UsbIo.destroyDeviceList(gDevList);
            return null;
        }
        USB_DEVICE_DESCRIPTOR deviceDescriptor=new USB_DEVICE_DESCRIPTOR();
        status = dev.getDeviceDescriptor(deviceDescriptor);
        if (status != USBIO_ERR_SUCCESS) {
            UsbIo.destroyDeviceList(gDevList);
            System.err.println("getDeviceDescriptor: "+UsbIo.errorText(status));
            dev.close();
            return null;
        }
        if (deviceDescriptor.idProduct==SiLabsC8051F320_USBIO_ServoController.PID){
            dev.close();
            UsbIo.destroyDeviceList(gDevList);
            return new SiLabsC8051F320_USBIO_ServoController(n);
        }
        return null;
    }
    
    
    private    ArrayList<UsbIo> usbioList=null;
    /** the UsbIo interface to the device. This is assigned when this particular instance is opened, after enumerating all devices */
    private UsbIo gUsbIo=null;
    
    private long gDevList; // 'handle' (an integer) to an internal device list static to UsbIo
    
    
    void buildUsbIoList(){
        usbioList=new ArrayList<UsbIo>();
        if(!UsbIoUtilities.isLibraryLoaded()) return;
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
                // get device descriptor (possibly before firmware download,
                // when still bare cypress device or running off EEPROM firmware)
                USB_DEVICE_DESCRIPTOR deviceDescriptor=new USB_DEVICE_DESCRIPTOR();
                status = dev.getDeviceDescriptor(deviceDescriptor);
                if (status != USBIO_ERR_SUCCESS) {
                    UsbIo.destroyDeviceList(gDevList);
                    System.err.println("getDeviceDescriptor: "+UsbIo.errorText(status));
                } else {
                    usbioList.add(dev);
                }
                //   numDevs++;
                dev.close();
            }
        }
        UsbIo.destroyDeviceList(gDevList); // we got number of devices, done with list
    }
    
    
}
