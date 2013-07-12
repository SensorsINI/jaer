package net.sf.jaer.hardwareinterface.usb.cypressfx2libusb;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import net.sf.jaer.hardwareinterface.HardwareInterfaceFactoryInterface;
import net.sf.jaer.hardwareinterface.usb.USBInterface;

import org.apache.commons.lang3.tuple.ImmutablePair;

import de.ailis.usb4java.libusb.Device;
import de.ailis.usb4java.libusb.DeviceDescriptor;
import de.ailis.usb4java.libusb.DeviceHandle;
import de.ailis.usb4java.libusb.DeviceList;
import de.ailis.usb4java.libusb.LibUsb;

public class LibUsbHardwareInterfaceFactory implements HardwareInterfaceFactoryInterface {
	private final static Logger log = Logger.getLogger("LibUsbHardwareInterfaceFactory");

	private static LibUsbHardwareInterfaceFactory instance = new LibUsbHardwareInterfaceFactory();

	/** @return singleton instance */
	public static HardwareInterfaceFactoryInterface instance() {
		return LibUsbHardwareInterfaceFactory.instance;
	}

	private final Map<ImmutablePair<Short, Short>, Class<?>> vidPidToClassMap = new HashMap<ImmutablePair<Short, Short>, Class<?>>();
	private final List<Device> compatibleDevicesList = new ArrayList<Device>();

	private LibUsbHardwareInterfaceFactory() {
		// Thesycon Vendor ID.
		final short VID_Thesycon = 0x152A;

		// Build a mapping of VID/PID pairs and corresponding HardwareInterfaces.
		// addDeviceToMap(VID_Thesycon, CypressFX2.PID_USB2AERmapper, CypressFX2Mapper.class);
		addDeviceToMap(VID_Thesycon, CypressFX2.PID_DVS128_REV0, CypressFX2DVS128HardwareInterface.class);
		addDeviceToMap(VID_Thesycon, CypressFX2.PID_TMPDIFF128_RETINA, CypressFX2TmpdiffRetinaHardwareInterface.class);
		// addDeviceToMap(VID_Thesycon, CypressFX2.PID_USBAERmini2, CypressFX2MonitorSequencer.class);
		addDeviceToMap(VID_Thesycon, ApsDvsHardwareInterface.PID, ApsDvsHardwareInterface.class);

		// Initialize LibUsb.
		LibUsb.init(null);

		// Build up first list of compatible devices.
		buildCompatibleDevicesList();
	}

	private void addDeviceToMap(final short VID, final short PID, final Class<?> cls) {
		vidPidToClassMap.put(new ImmutablePair<Short, Short>(VID, PID), cls);
	}

	private void buildCompatibleDevicesList() {
		// First let's make sure the list is empty, as we're going to re-scan everything.
		if (!compatibleDevicesList.isEmpty()) {
			for (final Device dev : compatibleDevicesList) {
				// Unreference each device once, as to be here, it must have been referenced exactly once later on.
				LibUsb.unrefDevice(dev);
			}

			// Clear out the list.
			compatibleDevicesList.clear();
		}

		final DeviceList devList = new DeviceList();
		LibUsb.getDeviceList(null, devList);

		for (final Device dev : devList) {
			final DeviceDescriptor devDesc = new DeviceDescriptor();
			LibUsb.getDeviceDescriptor(dev, devDesc);

			final ImmutablePair<Short, Short> vidPid = new ImmutablePair<Short, Short>(devDesc.idVendor(),
				devDesc.idProduct());

			LibUsb.freeDeviceDescriptor(devDesc);

			// Check that the device is not already bound to any other driver.
			final DeviceHandle devHandle = new DeviceHandle();
			int status = LibUsb.open(dev, devHandle);
			if (status != LibUsb.SUCCESS) {
				continue; // Skip device.
			}

			status = LibUsb.kernelDriverActive(devHandle, 0);

			LibUsb.close(devHandle);

			if ((status == LibUsb.SUCCESS) && vidPidToClassMap.containsKey(vidPid)) {
				// This is a VID/PID combination we support, so let's add the device to the compatible
				// devices list and increase its reference count.
				compatibleDevicesList.add(LibUsb.refDevice(dev));
			}
		}

		LibUsb.freeDeviceList(devList, true);
	}

	/**
	 * returns the first interface in the list
	 * 
	 * @return reference to the first interface in the list
	 */
	@Override
	synchronized public USBInterface getFirstAvailableInterface() {
		return getInterface(0);
	}

	/**
	 * returns the n-th interface in the list, the model depends on the PID.
	 * <p>
	 * For unknown or blank device PID a bare Cypress FX2 is returned, which should be discarded after it is used to
	 * download to the device RAM some preferred default firmware. A new Cypress FX2 should then be manufactured that
	 * will be correctly constructed here.
	 * <p>
	 * This method hard-codes the mapping from VID/PID and the HardwareInterface object that is constructed for it.
	 * 
	 * @param n
	 *            the number to instantiate (0 based)
	 */
	@Override
	synchronized public USBInterface getInterface(final int n) {
		final int numAvailable = getNumInterfacesAvailable();

		if (n > (numAvailable - 1)) {
			if (numAvailable == 0) {
				LibUsbHardwareInterfaceFactory.log.warning(String.format(
					"You asked for interface number %d but no interfaces are available at all. Check your Device "
						+ "Manager to see if the device has been recognized. You may need to install a driver.", n));
			}
			else {
				LibUsbHardwareInterfaceFactory.log.warning(String.format(
					"Only %d interfaces are available, but you asked for number %d (0 based).", numAvailable, n));
			}

			return null;
		}

		// Get device from list.
		final Device dev = compatibleDevicesList.get(n);

		// Get device descriptor again and instantiate the correct class for the device.
		final DeviceDescriptor devDesc = new DeviceDescriptor();
		LibUsb.getDeviceDescriptor(dev, devDesc);

		final ImmutablePair<Short, Short> vidPid = new ImmutablePair<Short, Short>(devDesc.idVendor(),
			devDesc.idProduct());

		LibUsb.freeDeviceDescriptor(devDesc);

		final Class<?> cls = vidPidToClassMap.get(vidPid);

		Constructor<?> constr = null;
		try {
			constr = cls.getDeclaredConstructor(Device.class);
		}
		catch (final NoSuchMethodException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1);
		}
		catch (final SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1);
		}

		Object iface = null;
		try {
			iface = constr.newInstance(dev);
		}
		catch (final InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (final IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (final IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (final InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return (USBInterface) iface;
	}

	/**
	 * @return the number of compatible devices attached to the driver
	 */
	@Override
	synchronized public int getNumInterfacesAvailable() {
		return compatibleDevicesList.size();
	}

	@Override
	public String getGUID() {
		return null;
	}
}
