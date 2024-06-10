package net.sf.jaer.hardwareinterface.usb.cypressfx2libusb;

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
import net.sf.jaer.hardwareinterface.usb.silabs.SiLabsC8051F320_LibUsb_PAER;
import org.usb4java.Context;
import org.usb4java.HotplugCallback;
import org.usb4java.LibUsbException;

/**
 * Generates correct HardwareInterface for boards that use LibUsb driver.
 *
 * @author luca
 */
public class LibUsbHardwareInterfaceFactory implements HardwareInterfaceFactoryInterface {

    private final static Logger log = Logger.getLogger("net.sf.jaer");

    private static LibUsbHardwareInterfaceFactory instance = new LibUsbHardwareInterfaceFactory();

    /**
     * @return singleton instance
     */
    public static HardwareInterfaceFactoryInterface instance() {
        return LibUsbHardwareInterfaceFactory.instance;
    }

    private final Map<ImmutablePair<Short, Short>, Class<?>> vidPidToClassMap = new HashMap<>();
    private final List<Device> compatibleDevicesList = new ArrayList<>();

    private LibUsbHardwareInterfaceFactory() {

        // Initialize LibUsb.
        try {
            // Initialize LibUsb.
            int result = LibUsb.init(null);
            if (result != LibUsb.SUCCESS) {
                throw new LibUsbException("Unable to initialize libusb", result);
            }

            if (!LibUsb.hasCapability(LibUsb.CAP_HAS_HOTPLUG)) {
                log.warning("LibUsb cannot register HotPlug callbacks on this platform");
            }
        } catch (UnsatisfiedLinkError | LibUsbException ule) {
            UnsatisfiedLinkError u = new UnsatisfiedLinkError("Failed to initialize libusb4java native library."
                    + "\nOn OS-X you might need to install with 'brew install libusb'."
                    + "\nOn Linux, do you have noexec on your /tmp folder?\n"
                    + ule.getLocalizedMessage());
            u.setStackTrace(ule.getStackTrace());
            throw u;
        }

        // Build a mapping of VID/PID pairs and corresponding
        // HardwareInterfaces.
        // addDeviceToMap(CypressFX2.VID, CypressFX2.PID_USB2AERmapper,
        // CypressFX2Mapper.class);
        addDeviceToMap(CypressFX2.VID_THESYCON_FX2_CPLD, CypressFX2.PID_DVS128_REV0, CypressFX2LibUsbDVS128HardwareInterface.class);
        addDeviceToMap(CypressFX2.VID_DVS128_ORIG_FX2_ONLY, CypressFX2.PID_TMPDIFF128_RETINA, CypressFX2LibUsbDVS128HardwareInterface.class); // tobi added for Tmpdiff128 StereoBoard VID/PID 0x547/0x8700
// maybe need to comment back in to handle very first Tmpdiff128 board
//        addDeviceToMap(CypressFX2.VID_THESYCON_FX2_CPLD, CypressFX2.PID_TMPDIFF128_RETINA, CypressFX2TmpdiffRetinaHardwareInterface.class);
//        addDeviceToMap(CypressFX2.VID_DVS128_ORIG_FX2_ONLY, CypressFX2.PID_TMPDIFF128_RETINA, CypressFX2TmpdiffRetinaHardwareInterface.class);
        addDeviceToMap(CypressFX2.VID_THESYCON_FX2_CPLD, CypressFX2.PID_COCHLEAAMS, CochleaAMS1cHardwareInterface.class);

        // Linux Drivers for PAER retina.
        if (System.getProperty("os.name").startsWith("Linux")) {
            addDeviceToMap(CypressFX2.VID_THESYCON_FX2_CPLD, SiLabsC8051F320_LibUsb_PAER.PID_PAER, SiLabsC8051F320_LibUsb_PAER.class);
        }

        // Add blank device for flashing.
        addDeviceToMap(CypressFX2.VID_BLANK, CypressFX2.PID_BLANK, CypressFX2.class);
        // Build up first list of compatible devices.
        refreshCompatibleDevicesList();

    }

    private void addDeviceToMap(final short VID, final short PID, final Class<?> cls) {
        vidPidToClassMap.put(new ImmutablePair<>(VID, PID), cls);
        log.info(String.format("Put mapping from USB VID/PID=0x%x/0x%x to class %s", VID, PID, cls));
        if (LibUsb.hasCapability(LibUsb.CAP_HAS_HOTPLUG)) {
            HotplugCallback callback = (Context cntxt, Device device, int event, Object userData) -> {
                DeviceDescriptor descriptor = new DeviceDescriptor();
                int errCode = LibUsb.getDeviceDescriptor(device, descriptor);
                if (errCode != LibUsb.SUCCESS) {
                    log.warning(String.format("Unable to read device descriptor: got error code %d", errCode));
                } else {
                    log.info(String.format("LibUsb: %s VID:PID=%04x:%04x",
                            event == LibUsb.HOTPLUG_EVENT_DEVICE_ARRIVED ? "Connected" : "Disconnected",
                            descriptor.idVendor(), descriptor.idProduct()));
                }
                return 0;
            };
            int errCode = LibUsb.hotplugRegisterCallback(null,
                    LibUsb.HOTPLUG_EVENT_DEVICE_ARRIVED | LibUsb.HOTPLUG_EVENT_DEVICE_LEFT,
                    LibUsb.HOTPLUG_ENUMERATE,
                    VID, PID, LibUsb.HOTPLUG_MATCH_ANY,
                    callback, null, null);
            if (errCode != LibUsb.SUCCESS) {
                log.warning(String.format("Could not register LibUsb hot plug callback for VID=%d, PID=%d, got error code %d", VID, PID, errCode));
            }
        }
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
            } else {
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

        String devicesFound = "LibUsbHardwareInterfaceFactory: devices found:";
        int nFound = 0;
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

            log.fine(String.format("probing libusb device with VID/PID 0x%x/0x%x to see if it maps to a compatible LibUsbHardwareInterface", vidPid.left, vidPid.right));

            LibUsb.close(devHandle);

            if (((status == LibUsb.ERROR_NOT_SUPPORTED) || (status == LibUsb.SUCCESS))
                    && vidPidToClassMap.containsKey(vidPid)) {
                // This is a VID/PID combination we support, so let's add the
                // device to the compatible
                // devices list and increase its reference count.
                compatibleDevicesListLocal.add(LibUsb.refDevice(dev));
                nFound++;
                devicesFound += String.format("\n %s for USB VID/PID 0x%x/0x%x", vidPidToClassMap.get(vidPid).toString(), vidPid.left, vidPid.right);
            }
        }
        if (nFound > 0) {
            log.info(devicesFound);
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
     * For unknown or blank device PID a bare Cypress FX2 is returned, which
     * should be discarded after it is used to download to the device RAM some
     * preferred default firmware. A new Cypress FX2 should then be manufactured
     * that will be correctly constructed here.
     * <p>
     * This method hard-codes the mapping from VID/PID and the HardwareInterface
     * object that is constructed for it.
     *
     * @param n the number to instantiate (0 based)
     */
    @Override
    synchronized public USBInterface getInterface(final int n) {
        final int numAvailable = getNumInterfacesAvailable();

        if (n > (numAvailable - 1)) {
            if (numAvailable == 0) {
                LibUsbHardwareInterfaceFactory.log.warning(String.format(
                        "You asked for interface number %d but no interfaces are available at all. Check your Device "
                        + "Manager to see if the device has been recognized. You may need to install a driver.", n));
            } else {
                LibUsbHardwareInterfaceFactory.log.warning(String.format(
                        "Only %d interfaces are available, but you asked for number %d (0 based).", numAvailable, n));
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
        } catch (NoSuchMethodException | SecurityException | NullPointerException e) {
            e.printStackTrace();
            return null;
        }

        Object iface = null;
        try {
            iface = constr.newInstance(dev);

            if (iface == null) {
                throw new NullPointerException();
            }
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
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
