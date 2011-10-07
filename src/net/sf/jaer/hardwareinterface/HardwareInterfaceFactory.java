/*
 * HardwareInterfaceFactory.java
 *
 * Created on October 2, 2005, 5:38 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */
package net.sf.jaer.hardwareinterface;

import cl.eye.CLEyeHardwareInterfaceFactory;
import de.thesycon.usbio.PnPNotify;
import de.thesycon.usbio.PnPNotifyInterface;
import java.util.*;
import java.lang.reflect.*;
import java.util.logging.Logger;
import net.sf.jaer.hardwareinterface.usb.UsbIoUtilities;

import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.USBIOHardwareInterfaceFactory;
import net.sf.jaer.hardwareinterface.usb.linux.HardwareInterfaceFactoryLinux;
import net.sf.jaer.hardwareinterface.usb.silabs.SiLabs_USBIO_C8051F3xxFactory;
import net.sf.jaer.hardwareinterface.usb.usbaermapper.USBAERatcFactory;
import net.sf.jaer.hardwareinterface.udp.*;

import net.sf.jaer.hardwareinterface.serial.*;

/**
 * This class builds a list of all available devices and lets you get one of them.
 *It is a singleton: get the instance() and ask it to make an interface for you.
 * You need to first call the expensive {@link #buildInterfaceList() } to enumerate all devices available.
 * Afterwards the list is stored and may be cheaply accessed.
 *
 * @author tobi
 */
public class HardwareInterfaceFactory extends HashSet<Class> implements HardwareInterfaceFactoryInterface, PnPNotifyInterface {

    HashSet<Class> factoryHashSet = new HashSet<Class>();
    volatile private ArrayList<HardwareInterface> interfaceList = new ArrayList<HardwareInterface>();
    private PnPNotify pnp = null; //new PnPNotify();
    static final Logger log = Logger.getLogger("HardwareInterfaceFactory");
    // these are devices that can be enumerated and opened
    // TODO fix to used scanned classpath as in filter menu or chip classes
    
    /** Factories that can be queried for interfaces. */
    final public static Class[] factories = {
        SiLabs_USBIO_C8051F3xxFactory.class,
        USBIOHardwareInterfaceFactory.class,
        HardwareInterfaceFactoryLinux.class,
        USBAERatcFactory.class,
        UDPInterfaceFactory.class,
        CLEyeHardwareInterfaceFactory.class,
        EmbeddedDVSSerialPortChooserFactory.class
    }; 
    private static HardwareInterfaceFactory instance = new HardwareInterfaceFactory();

    /** Creates a new instance of HardwareInterfaceFactory, private because this is a singleton factory class */
    private HardwareInterfaceFactory() {
        if (UsbIoUtilities.isLibraryLoaded()) {
            pnp = new PnPNotify(this);
            pnp.enablePnPNotification(SiLabs_USBIO_C8051F3xxFactory.GUID);
            pnp.enablePnPNotification(USBIOHardwareInterfaceFactory.GUID);
        }
    }

    /** Use this instance to access the methods, e.g. <code>HardwareInterfaceFactory.instance().getNumInterfacesAvailable()</code>.
    @return the singleton instance.
     */
    public static HardwareInterfaceFactory instance() {
        return instance;
    }

    /** Explicitly searches all interface types to build a list of available hardware interfaces. This method is expensive.
     * @see #getNumInterfacesAvailable() 
     */
    synchronized public void buildInterfaceList() {
        interfaceList.clear();
        HardwareInterface u;
//        System.out.println("****** HardwareInterfaceFactory.building interface list");
        for (int i = 0; i < factories.length; i++) {
            try {
                Method m = ((factories[i]).getMethod("instance")); // get singleton instance of factory
                HardwareInterfaceFactoryInterface inst = (HardwareInterfaceFactoryInterface) m.invoke(factories[i]);
                int num = inst.getNumInterfacesAvailable(); // ask it how many devices are out there
//                if(num>0) System.out.println("interface "+inst+" has "+num+" devices available"); // TODO comment
                for (int j = 0; j < num; j++) {
                    u = inst.getInterface(j); // for each one, construct the HardwareInterface and put it in a list
                    if(u==null) continue;
                    interfaceList.add(u);
//                    System.out.println("HardwareInterfaceFactory.buildInterfaceList: added "+u);// TODO comment
                }
            } catch (NoSuchMethodException e) {
                log.warning(factories[i] + " has no instance() method but it needs to be a singleton of this form");
                e.printStackTrace();
            } catch (IllegalAccessException e3) {
                e3.printStackTrace();
            } catch (InvocationTargetException e4) {
                e4.printStackTrace();
            } catch (HardwareInterfaceException e5) {
                e5.printStackTrace();
            }
        }
    }

    /** Says how many total of all types of hardware are available, assuming that {@link #buildInterfaceList() } has been called earlier.
     * 
    @return number of devices
     * @see #buildInterfaceList() 
     */
    @Override
    synchronized public int getNumInterfacesAvailable() {
//        buildInterfaceList(); // removed to make this call much cheaper
        return interfaceList.size();
    }

    /** @return first available interface, starting with CypressFX2 and then going to SiLabsC8051F320 */
    @Override
    synchronized public HardwareInterface getFirstAvailableInterface() {
        return getInterface(0);
    }

    /** build list of devices and return the n'th one, 0 based */
    @Override
    synchronized public HardwareInterface getInterface(int n) {
//        buildInterfaceList();
        if (interfaceList == null || interfaceList.isEmpty()) {
            return null;
        }
        if (n > interfaceList.size() - 1) {
            return null;
        } else {
            HardwareInterface hw = interfaceList.get(n);
//            System.out.println("HardwareInterfaceFactory.getInterace("+n+")="+hw);
            return hw;
        }
    }
//    public static void main(String  [] arg) {
//        HardwareInterfaceFactory.instance().getNumInterfacesAvailable();
//    }

//    public HardwareInterface getFirstAvailableInterfaceForChip(Chip chip) {
//        throw new UnsupportedOperationException("Not supported yet.");
//    }
    // TODO maybe use the following mechanism for factories to add themselves
//    /** Adds a factory to the list of factory classes. Subclasse can use this to add themselves.
//     
//     @param factoryClass the Class of the factory
//     */
//    public static void addFactory(Class<HardwareInterfaceFactory> factoryClass){
//        factoryHashSet.add(factoryClass);
//    }
    @Override
    public String getGUID() {
        return null;
    }

    @Override
    public void onAdd() {
        log.info("USBIO device added, rebuilding interface list");
        buildInterfaceList();
    }

    @Override
    public void onRemove() {
        log.info("USBIO device removed, rebuilding interface list");
        buildInterfaceList();
    }
}
