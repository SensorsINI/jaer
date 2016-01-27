package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.usb4java.Device;
import org.usb4java.DeviceDescriptor;
import org.usb4java.DeviceHandle;
import org.usb4java.DeviceList;
import org.usb4java.LibUsb;

import net.sf.jaer.hardwareinterface.HardwareInterfaceFactoryInterface;
import net.sf.jaer.hardwareinterface.usb.USBInterface;

public class LibUsb3HardwareInterfaceFactory implements HardwareInterfaceFactoryInterface {
	private final static Logger log = Logger.getLogger("LibUsb3HardwareInterfaceFactory");

	private static LibUsb3HardwareInterfaceFactory instance = new LibUsb3HardwareInterfaceFactory();

	/** @return singleton instance */
	public static HardwareInterfaceFactoryInterface instance() {
		return LibUsb3HardwareInterfaceFactory.instance;
	}

	private final Map<ImmutablePair<Short, Short>, Class<?>> vidPidToClassMap = new HashMap<>();
	private final List<Device> compatibleDevicesList = new ArrayList<>();

	private LibUsb3HardwareInterfaceFactory() {
		// Build a mapping of VID/PID pairs and corresponding
		// HardwareInterfaces.
		addDeviceToMap(CypressFX3.VID, DAViSFX3HardwareInterface.PID_FX3, DAViSFX3HardwareInterface.class);

		addDeviceToMap(CypressFX3.VID, DAViSFX3HardwareInterface.PID_FX2, DAViSFX3HardwareInterface.class);

		addDeviceToMap(CypressFX3.VID, CochleaFX3HardwareInterface.PID_FX3, CochleaFX3HardwareInterface.class);

		// Initialize LibUsb.
		LibUsb.init(null);

		// Build up first list of compatible devices.
		refreshCompatibleDevicesList();
	}

	private void addDeviceToMap(final short VID, final short PID, final Class<?> cls) {
		vidPidToClassMap.put(new ImmutablePair<>(VID, PID), cls);
	}

	private void refreshCompatibleDevicesList() {
		// Temporary storage to allow modification.
		final List<Device> tmpDrain = new ArrayList<>(buildCompatibleDevicesList());

		// Replace with new data in a non-destructive way, by not touching
		// values that were already present.
		final List<Device> removals = new ArrayList<>();

		for (final Device element : compatibleDevicesList) {
			if (tmpDrain.contains(element)) {
				tmpDrain.remove(element);
			}
			else {
				removals.add(element);
				LibUsb.unrefDevice(element);
			}
		}

		// Remove all items that need to be deleted and add all the new ones in
		// only one call each.
		compatibleDevicesList.removeAll(removals);
		compatibleDevicesList.addAll(tmpDrain);

		// Consume newContent fully.
		tmpDrain.clear();
	}

	private List<Device> buildCompatibleDevicesList() {
		final List<Device> compatibleDevicesListLocal = new ArrayList<>();

		final DeviceList devList = new DeviceList();
		LibUsb.getDeviceList(null, devList);

		final DeviceDescriptor devDesc = new DeviceDescriptor();

		for (final Device dev : devList) {
			LibUsb.getDeviceDescriptor(dev, devDesc);

			final ImmutablePair<Short, Short> vidPid = new ImmutablePair<>(devDesc.idVendor(), devDesc.idProduct());

			// Check that the device is not already bound to any other driver.
			final DeviceHandle devHandle = new DeviceHandle();
			int status = LibUsb.open(dev, devHandle);
			if (status != LibUsb.SUCCESS) {
				continue; // Skip device.
			}

			status = LibUsb.kernelDriverActive(devHandle, 0);

			LibUsb.close(devHandle);

			if (((status == LibUsb.ERROR_NOT_SUPPORTED) || (status == LibUsb.SUCCESS)) && vidPidToClassMap.containsKey(vidPid)) {
				// This is a VID/PID combination we support, so let's add the
				// device to the compatible
				// devices list and increase its reference count.
				compatibleDevicesListLocal.add(LibUsb.refDevice(dev));
			}
		}

		LibUsb.freeDeviceList(devList, true);

		return compatibleDevicesListLocal;
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
				LibUsb3HardwareInterfaceFactory.log
					.warning(String.format("You asked for interface number %d but no interfaces are available at all. Check your Device "
						+ "Manager to see if the device has been recognized. You may need to install a driver.", n));
			}
			else {
				LibUsb3HardwareInterfaceFactory.log
					.warning(String.format("Only %d interfaces are available, but you asked for number %d (0 based).", numAvailable, n));
			}

			return null;
		}

		// Get device from list.
		final Device dev = compatibleDevicesList.get(n);

		// Get device descriptor again and instantiate the correct class for the
		// device.
		final DeviceDescriptor devDesc = new DeviceDescriptor();
		LibUsb.getDeviceDescriptor(dev, devDesc);

		final ImmutablePair<Short, Short> vidPid = new ImmutablePair<>(devDesc.idVendor(), devDesc.idProduct());

		final Class<?> cls = vidPidToClassMap.get(vidPid);

		Constructor<?> constr = null;
		try {
			constr = cls.getDeclaredConstructor(Device.class);

			if (constr == null) {
				throw new NullPointerException();
			}
		}
		catch (NoSuchMethodException | SecurityException | NullPointerException e) {
			e.printStackTrace();
			return null;
		}

		Object iface = null;
		try {
			iface = constr.newInstance(dev);

			if (iface == null) {
				throw new NullPointerException();
			}
		}
		catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
			| NullPointerException e) {
			e.printStackTrace();
			return null;
		}

		return (USBInterface) iface;
	}

	/**
	 * @return the number of compatible devices attached to the driver
	 */
	@Override
	synchronized public int getNumInterfacesAvailable() {
		refreshCompatibleDevicesList();
		return compatibleDevicesList.size();
	}

	@Override
	public String getGUID() {
		return null;
	}
}
