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
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import net.sf.jaer.UsbDevices;
import net.sf.jaer.util.StartupProfiler;
import net.sf.jaer.chip.Chip;
// import net.sf.jaer.hardwareinterface.serial.SpiNNaker.SpiNNaker_InterfaceFactory;
// import net.sf.jaer.hardwareinterface.serial.eDVS128.eDVS128_InterfaceFactory;
import net.sf.jaer.hardwareinterface.udp.UDPInterfaceFactory;
import net.sf.jaer.hardwareinterface.usb.LibUsbHotplug;
import net.sf.jaer.hardwareinterface.usb.MacosLibusbHelp;
import net.sf.jaer.hardwareinterface.usb.USBInterface;
import net.sf.jaer.hardwareinterface.usb.UsbHardwareRegistry;
import net.sf.jaer.hardwareinterface.usb.UsbIds;
import net.sf.jaer.hardwareinterface.usb.cypressfx2libusb.LibUsbHardwareInterfaceFactory;
import net.sf.jaer.hardwareinterface.usb.cypressfx3libusb.LibUsb3HardwareInterfaceFactory;
import nrv.usb.NRVHardwareInterfaceFactory;
import prophesee.usb.PropheseeHardwareInterfaceFactory;
import de.thesycon.usbio.PnPNotifyInterface;
// import es.us.atc.jaer.hardwareinterface.OpalKellyFX3Factory;
import java.util.logging.Level;

/**
 * This class builds a list of all available devices and lets you get one of them.
 * It is a singleton: get the instance() and ask it to make an interface for you.
 * You need to first call the expensive {@link #buildInterfaceList() } to enumerate all devices available.
 * Afterwards the list is stored and may be cheaply accessed.
 * <p>
 * Thesycon USBIO factories ({@code USBIOHardwareInterfaceFactory}, {@code SiLabs_USBIO_C8051F3xxFactory})
 * are intentionally not registered; see {@code docs/WIP-USBIO-purge.md}. Sources remain for libusb porting.
 * {@code eDVS128_InterfaceFactory}, {@code SpiNNaker_InterfaceFactory}, and
 * {@code OpalKellyFX3Factory} are also unregistered for now (sources remain).
 * They would otherwise appear as Interface-menu chooser dialogs. OpalKelly’s
 * {@code <clinit>} calls {@code System.loadLibrary("okjFrontPanel")} and warns
 * when that native library is missing.
 *
 * @author tobi
 */
public class HardwareInterfaceFactory extends HashSet<Class> implements
HardwareInterfaceFactoryInterface, PnPNotifyInterface {

	private static final long serialVersionUID = 6795768174203484869L;
//	HashSet<Class> factoryHashSet = new HashSet<Class>();
	private final ArrayList<HardwareInterface> interfaceList = new ArrayList<>();
	/**
	 * Last completed scan. Replaced atomically so Interface-menu / EDT reads
	 * never wait on {@code LibUsb.getDeviceList} (Windows WinUSB can hang that
	 * call after an NRV unplug; synchronized cache reads then froze Swing).
	 */
	private volatile List<HardwareInterface> interfaceSnapshot = List.of();
	/** True until a bus scan completes; libusb hotplug sets this so WAITING can skip periodic polls. */
	private volatile boolean usbEnumerationDirty = true;
	/** Coalesce WAITING background scans; a hung {@code getDeviceList} must not start another. */
	private final AtomicBoolean backgroundScanQueued = new AtomicBoolean(false);
	/**
	 * Native {@code open()}+config in progress ({@code USB_OPEN_SERIAL_LOCK}).
	 * WAITING must not {@code getDeviceList} then: WinUSB times out EVK4 ISSD
	 * bulk and stalls NRV I2C (jAER 10:14:08 Prophesee; 10:12:43 NRV).
	 */
	private final AtomicInteger usbNativeOpenCount = new AtomicInteger();
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
		// eDVS / SpiNNaker chooser factories unregistered for now (sources remain)
		// eDVS128_InterfaceFactory.class,
		// SpiNNaker_InterfaceFactory.class,
		// OpalKelly (University of Seville) unregistered: class init loads native okjFrontPanel
		// and logs "no okjFrontPanel in java.library.path" on every start.
		// To re-enable: uncomment the import and OpalKellyFX3Factory.class below, then
		// put OpalKelly FrontPanel Java + JNI on the runtime path:
		//   classpath: okjFrontPanel.jar (com.opalkelly.frontpanel.okFrontPanel) in jars/
		//   java.library.path: native okjFrontPanel (okjFrontPanel.dll / libokjFrontPanel.so
		//   / libokjFrontPanel.dylib) matching the JVM bitness, typically next to that jar
		//   or on PATH. SDK: https://docs.opalkelly.com/frontpanel-sdk-examples/java/
		// OpalKellyFX3Factory.class,
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
		StartupProfiler.mark("HardwareInterfaceFactory.buildInterfaceList start");
		final ArrayList<HardwareInterface> built = new ArrayList<>();
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

					built.add(u);
                                        // don't do following because to print device toString() requires opening it minimally. this causes hang on windows.
//					log.log(Level.INFO, "HardwareInterfaceFactory.buildInterfaceList: added device {0} with HardwareInterfaceFactory {1}", new Object[]{u, factorie});
				}
			}
			catch (final NoSuchMethodException e) {
				HardwareInterfaceFactory.log.log(Level.WARNING, "{0} has no instance() method but it needs to be a singleton of this form", factorie);
			}
			catch (final IllegalAccessException | InvocationTargetException | HardwareInterfaceException e3) {
				MacosLibusbHelp.maybeShowDialog(e3);
				e3.printStackTrace();
			}
			catch (final ExceptionInInitializerError e) {
				MacosLibusbHelp.maybeShowDialog(e);
				HardwareInterfaceFactory.log.log(Level.WARNING, "{0} failed to initialize: {1}",
						new Object[] { factorie, e.getCause() != null ? e.getCause() : e });
			}
		}
		// Publish only after the scan finishes so a hung getDeviceList leaves
		// the previous snapshot readable from the EDT.
		interfaceList.clear();
		interfaceList.addAll(built);
		interfaceSnapshot = List.copyOf(built);
		StartupProfiler.mark("HardwareInterfaceFactory.buildInterfaceList end n=" + built.size());
	}

	/**
	 * Force the next {@link #getNumInterfacesAvailable()} to rescan factories
	 * (hotplug, Interface menu, or fallback poll).
	 */
	public void markUsbEnumerationDirty() {
		usbEnumerationDirty = true;
	}

	/** True when a hotplug event (or Interface menu) has invalidated the cached list. */
	public boolean isUsbEnumerationDirty() {
		return usbEnumerationDirty;
	}

	/**
	 * Number of interfaces from the last completed {@link #buildInterfaceList()}.
	 * Does not scan the USB bus and does not wait for an in-flight scan. Use from
	 * the Interface menu / EDT.
	 *
	 * @return cached device count
	 * @see #getNumInterfacesAvailable()
	 */
	public int getCachedNumInterfacesAvailable() {
		return interfaceSnapshot.size();
	}

	/**
	 * Kick a USB bus scan on a daemon if one is not already running. Returns
	 * immediately. WAITING ViewLoop must use this plus
	 * {@link #getCachedNumInterfacesAvailable()} so a hung WinUSB
	 * {@code getDeviceList} cannot freeze that window (or the EDT via the
	 * factory monitor).
	 */
	public void requestBackgroundScan() {
		if (usbNativeOpenCount.get() > 0) {
			log.fine("background USB scan skipped; native open in progress");
			return;
		}
		if (!usbEnumerationDirty && LibUsbHotplug.isSupported()) {
			return;
		}
		if (!backgroundScanQueued.compareAndSet(false, true)) {
			return;
		}
		Thread t = new Thread(() -> {
			try {
				if (usbNativeOpenCount.get() > 0) {
					log.fine("background USB scan aborted; native open in progress");
					return;
				}
				getNumInterfacesAvailable();
			} catch (Throwable e) {
				log.log(Level.WARNING, "background USB scan failed: " + e, e);
			} finally {
				backgroundScanQueued.set(false);
			}
		}, "jaer-usb-scan");
		t.setDaemon(true);
		t.start();
	}

	/** {@code AEViewer} holds {@code USB_OPEN_SERIAL_LOCK} around native open+config. */
	public void noteUsbNativeOpenBegin() {
		usbNativeOpenCount.incrementAndGet();
	}

	public void noteUsbNativeOpenEnd() {
		usbNativeOpenCount.updateAndGet(n -> Math.max(0, n - 1));
	}

	/**
	 * Wait until an in-flight {@code jaer-usb-scan} finishes, or {@code timeoutMs}.
	 * Call after taking the open serializer so ISSD / I2C do not overlap
	 * {@code getDeviceList}.
	 */
	public void awaitBackgroundScanIdle(long timeoutMs) {
		final long deadline = System.currentTimeMillis() + Math.max(0L, timeoutMs);
		while (backgroundScanQueued.get()) {
			if (System.currentTimeMillis() >= deadline) {
				log.fine("background USB scan still running after " + timeoutMs
						+ " ms; continuing open");
				return;
			}
			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	/**
	 * Number of hardware interfaces currently known. With libusb hotplug this is
	 * cheap until a plug event (or {@link #markUsbEnumerationDirty()}) forces a rescan.
	 * Without hotplug this enumerates all factories every call.
	 *
	 * @return number of devices
	 * @see #buildInterfaceList()
	 */
	@Override
	synchronized public int getNumInterfacesAvailable() {
		// With libusb hotplug the list stays valid until a plug event (or explicit dirty).
		if (!usbEnumerationDirty && LibUsbHotplug.isSupported()) {
			return interfaceSnapshot.size();
		}
		// Rebuild until a hotplug arriving mid-scan is included (stale getDeviceList).
		int spins = 0;
		do {
			usbEnumerationDirty = false;
			buildInterfaceList();
		} while (usbEnumerationDirty && ++spins < 3);
		return interfaceSnapshot.size();
	}

	/** @return first available interface from the last completed scan. Does not wait for an in-flight scan. */
	@Override
	public HardwareInterface getFirstAvailableInterface() {
		return getInterface(0);
	}

	/** Return the n'th interface from the last completed scan. Does not wait for an in-flight scan. */
	@Override
	public HardwareInterface getInterface(final int n) {
		final List<HardwareInterface> snap = interfaceSnapshot;
		if (snap.isEmpty() || n < 0 || n > (snap.size() - 1)) {
			return null;
		}
		return snap.get(n);
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
