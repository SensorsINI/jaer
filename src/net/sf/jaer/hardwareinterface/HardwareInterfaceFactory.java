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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.logging.Logger;

import net.sf.jaer.hardwareinterface.serial.SpiNNaker.SpiNNaker_InterfaceFactory;
import net.sf.jaer.hardwareinterface.serial.eDVS128.eDVS128_InterfaceFactory;
import net.sf.jaer.hardwareinterface.udp.UDPInterfaceFactory;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.USBIOHardwareInterfaceFactory;
import net.sf.jaer.hardwareinterface.usb.cypressfx2libusb.LibUsbHardwareInterfaceFactory;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.LibUsb3HardwareInterfaceFactory;
import net.sf.jaer.hardwareinterface.usb.silabs.SiLabs_USBIO_C8051F3xxFactory;
import de.thesycon.usbio.PnPNotifyInterface;

/**
 * This class builds a list of all available devices and lets you get one of them.
 * It is a singleton: get the instance() and ask it to make an interface for you.
 * You need to first call the expensive {@link #buildInterfaceList() } to enumerate all devices available.
 * Afterwards the list is stored and may be cheaply accessed.
 *
 * @author tobi
 */
public class HardwareInterfaceFactory extends HashSet<Class> implements
HardwareInterfaceFactoryInterface, PnPNotifyInterface {

	private static final long serialVersionUID = 6795768174203484869L;
	HashSet<Class> factoryHashSet = new HashSet<Class>();
	private final ArrayList<HardwareInterface> interfaceList = new ArrayList<HardwareInterface>();
	static final Logger log = Logger.getLogger("HardwareInterfaceFactory");

	// these are devices that can be enumerated and opened
	// TODO fix to used scanned classpath as in filter menu or chip classes

	/** Factories that can be queried for interfaces. */
	final public static Class[] factories = {
		SiLabs_USBIO_C8051F3xxFactory.class,
		USBIOHardwareInterfaceFactory.class,
		LibUsbHardwareInterfaceFactory.class,
		LibUsb3HardwareInterfaceFactory.class,
		UDPInterfaceFactory.class,
		eDVS128_InterfaceFactory.class,
		SpiNNaker_InterfaceFactory.class,
	};
	private static HardwareInterfaceFactory instance = new HardwareInterfaceFactory();

	/** Creates a new instance of HardwareInterfaceFactory, private because this is a singleton factory class */
	private HardwareInterfaceFactory() {
		// UsbIoUtilities.enablePnPNotification(this, SiLabs_USBIO_C8051F3xxFactory.GUID); // TODO not needed since we
		// rebuild list on each call for interfaces
		// UsbIoUtilities.enablePnPNotification(this, USBIOHardwareInterfaceFactory.GUID);
	}

	/**
	 * Use this instance to access the methods, e.g.
	 * <code>HardwareInterfaceFactory.instance().getNumInterfacesAvailable()</code>.
	 *
	 * @return the singleton instance.
	 */
	public static HardwareInterfaceFactory instance() {
		return HardwareInterfaceFactory.instance;
	}

	/**
	 * Explicitly searches all interface types to build a list of available hardware interfaces. This method is
	 * expensive.  The list should only includes devices that are not already opened.
	 *
	 * @see #getNumInterfacesAvailable()
	 */
	synchronized public void buildInterfaceList() {
		interfaceList.clear();
		HardwareInterface u;
		// System.out.println("****** HardwareInterfaceFactory.building interface list");

		for (final Class factorie : HardwareInterfaceFactory.factories) {
			try {
				final Method m = ((factorie).getMethod("instance")); // get singleton instance of factory
				final HardwareInterfaceFactoryInterface inst = (HardwareInterfaceFactoryInterface) m.invoke(factorie);
				final int num = inst.getNumInterfacesAvailable(); // ask it how many devices are out there

//				 if(num>0) System.out.println("interface "+inst+" has "+num+" devices available"); // TODO comment
				for (int j = 0; j < num; j++) {
					u = inst.getInterface(j); // for each one, construct the HardwareInterface and put it in a list

					if (u == null) {
						continue;
					}

					interfaceList.add(u);
					// System.out.println("HardwareInterfaceFactory.buildInterfaceList: added "+u);// TODO comment
				}
			}
			catch (final NoSuchMethodException e) {
				HardwareInterfaceFactory.log.warning(factorie
					+ " has no instance() method but it needs to be a singleton of this form");
				e.printStackTrace();
			}
			catch (final IllegalAccessException e3) {
				e3.printStackTrace();
			}
			catch (final InvocationTargetException e4) {
				e4.printStackTrace();
			}
			catch (final HardwareInterfaceException e5) {
				e5.printStackTrace();
			}
		}
	}

	/**
	 * Says how many total of all types of hardware are available, assuming that {@link #buildInterfaceList() } has been
	 * called earlier.  This method should only return devices that are not already opened, i.e. bound already.
	 *
	 * @return number of devices
	 * @see #buildInterfaceList()
	 */
	@Override
	synchronized public int getNumInterfacesAvailable() {
		buildInterfaceList(); // removed to make this call much cheaper
		return interfaceList.size();
	}

	/** @return first available interface, starting with CypressFX2 and then going to SiLabsC8051F320 */
	@Override
	synchronized public HardwareInterface getFirstAvailableInterface() {
		return getInterface(0);
	}

	/** build list of devices and return the n'th one, 0 based */
	@Override
	synchronized public HardwareInterface getInterface(final int n) {
		// buildInterfaceList();
		if ((interfaceList == null) || interfaceList.isEmpty()) {
			return null;
		}

		if (n > (interfaceList.size() - 1)) {
			return null;
		}
		else {
			final HardwareInterface hw = interfaceList.get(n);
			// System.out.println("HardwareInterfaceFactory.getInterace("+n+")="+hw);

			return hw;
		}
	}

	// public static void main(String [] arg) {
	// HardwareInterfaceFactory.instance().getNumInterfacesAvailable();
	// }

	// public HardwareInterface getFirstAvailableInterfaceForChip(Chip chip) {
	// throw new UnsupportedOperationException("Not supported yet.");
	// }
	// TODO maybe use the following mechanism for factories to add themselves
	// /** Adds a factory to the list of factory classes. Subclasse can use this to add themselves.
	//
	// @param factoryClass the Class of the factory
	// */
	// public static void addFactory(Class<HardwareInterfaceFactory> factoryClass){
	// factoryHashSet.add(factoryClass);
	// }
	@Override
	public String getGUID() {
		return null;
	}

	@Override
	public void onAdd() {
		HardwareInterfaceFactory.log.info("USBIO device added, rebuilding interface list");
		buildInterfaceList();
	}

	@Override
	public void onRemove() {
		HardwareInterfaceFactory.log.info("USBIO device removed, rebuilding interface list");
		buildInterfaceList();
	}
}
