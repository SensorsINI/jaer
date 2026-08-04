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

import net.sf.jaer.UsbDevices;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.hardwareinterface.serial.SpiNNaker.SpiNNaker_InterfaceFactory;
import net.sf.jaer.hardwareinterface.serial.eDVS128.eDVS128_InterfaceFactory;
import net.sf.jaer.hardwareinterface.udp.UDPInterfaceFactory;
import net.sf.jaer.hardwareinterface.usb.USBInterface;
import net.sf.jaer.hardwareinterface.usb.UsbHardwareRegistry;
import net.sf.jaer.hardwareinterface.usb.UsbIds;
import net.sf.jaer.hardwareinterface.usb.cypressfx2libusb.LibUsbHardwareInterfaceFactory;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.LibUsb3HardwareInterfaceFactory;
import nrv.usb.NRVHardwareInterfaceFactory;
import prophesee.usb.PropheseeHardwareInterfaceFactory;
import de.thesycon.usbio.PnPNotifyInterface;
import es.us.atc.jaer.hardwareinterface.OpalKellyFX3Factory;
import java.util.logging.Level;

/**
 * This class builds a list of all available devices and lets you get one of them.
 * It is a singleton: get the instance() and ask it to make an interface for you.
 * You need to first call the expensive {@link #buildInterfaceList() } to enumerate all devices available.
 * Afterwards the list is stored and may be cheaply accessed.
 * <p>
 * Thesycon USBIO factories ({@code USBIOHardwareInterfaceFactory}, {@code SiLabs_USBIO_C8051F3xxFactory})
 * are intentionally not registered; see {@code docs/WIP-USBIO-purge.md}. Sources remain for libusb porting.
 *
 * @author tobi
 */
public class HardwareInterfaceFactory extends HashSet<Class> implements
HardwareInterfaceFactoryInterface, PnPNotifyInterface {

	private static final long serialVersionUID = 6795768174203484869L;
//	HashSet<Class> factoryHashSet = new HashSet<Class>();
	private final ArrayList<HardwareInterface> interfaceList = new ArrayList<>();
	static final Logger log = Logger.getLogger("net.sf.jaer");

	// these are devices that can be enumerated and opened
	// TODO fix to used scanned classpath as in filter menu or chip classes

	/** Factories that can be queried for interfaces. */
	final public static Class[] factories = {
		// USBIO / Thesycon factories unregistered — see docs/WIP-USBIO-purge.md
		// SiLabs_USBIO_C8051F3xxFactory.class,
		// USBIOHardwareInterfaceFactory.class,
		LibUsbHardwareInterfaceFactory.class,
		LibUsb3HardwareInterfaceFactory.class,
		NRVHardwareInterfaceFactory.class,
		PropheseeHardwareInterfaceFactory.class,
		UDPInterfaceFactory.class,
		eDVS128_InterfaceFactory.class,
		SpiNNaker_InterfaceFactory.class,
                OpalKellyFX3Factory.class,
	};
	private static HardwareInterfaceFactory instance = new HardwareInterfaceFactory();

	/** Creates a new instance of HardwareInterfaceFactory, private because this is a singleton factory class */
	private HardwareInterfaceFactory() {
		// USBIO PnP notification disabled — see docs/WIP-USBIO-purge.md
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
                                        // don't do following because to print device toString() requires opening it minimally. this causes hang on windows.
//					log.log(Level.INFO, "HardwareInterfaceFactory.buildInterfaceList: added device {0} with HardwareInterfaceFactory {1}", new Object[]{u, factorie});
				}
			}
			catch (final NoSuchMethodException e) {
				HardwareInterfaceFactory.log.log(Level.WARNING, "{0} has no instance() method but it needs to be a singleton of this form", factorie);
			}
			catch (final IllegalAccessException | InvocationTargetException | HardwareInterfaceException e3) {
				e3.printStackTrace();
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

	/**
	 * Peek USB VID/PID from an enumerated interface without requiring a full open.
	 *
	 * @return VID/PID pair; {@link UsbIds.Pair#isKnown()} is false if unavailable
	 */
	public static UsbIds.Pair getUsbVidPid(HardwareInterface hw) {
		return UsbIds.peek(hw);
	}

	/**
	 * First available interface whose VID/PID is declared on the chip class via
	 * {@link UsbDevices}, or {@link #getFirstAvailableInterface()} if the chip
	 * has no USB annotation or no match is found.
	 */
	public HardwareInterface getFirstAvailableInterfaceForChip(Chip chip) {
		if (chip == null) {
			return getFirstAvailableInterface();
		}
		Class<?> chipClass = chip.getClass();
		UsbDevices devices = chipClass.getAnnotation(UsbDevices.class);
		if (devices == null || devices.value().length == 0) {
			return getFirstAvailableInterface();
		}
		int n = getNumInterfacesAvailable();
		for (int i = 0; i < n; i++) {
			HardwareInterface hw = getInterface(i);
			if (hw == null || !(hw instanceof USBInterface)) {
				continue;
			}
			UsbIds.Pair ids = UsbIds.peek(hw);
			if (!ids.isKnown()) {
				continue;
			}
			for (net.sf.jaer.UsbDevice d : devices.value()) {
				if (d.vid() == ids.vid && d.pid() == ids.pid) {
					return hw;
				}
			}
		}
		return getFirstAvailableInterface();
	}

	/** @see UsbHardwareRegistry#interfaceClassFor(short, short) */
	public static Class<? extends HardwareInterface> interfaceClassForUsb(short vid, short pid) {
		return UsbHardwareRegistry.instance().interfaceClassFor(vid, pid);
	}
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
