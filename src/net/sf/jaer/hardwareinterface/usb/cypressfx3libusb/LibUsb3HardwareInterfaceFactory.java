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
import org.usb4java.DeviceList;
import org.usb4java.LibUsb;

import net.sf.jaer.hardwareinterface.HardwareInterfaceFactoryInterface;
import net.sf.jaer.hardwareinterface.usb.LibUsbHotplug;
import net.sf.jaer.hardwareinterface.usb.MacosLibusbHelp;
import net.sf.jaer.hardwareinterface.usb.USBInterface;
import net.sf.jaer.hardwareinterface.usb.UsbHardwareRegistry;
import net.sf.jaer.hardwareinterface.usb.UsbIds;
import org.usb4java.LibUsbException;

public class LibUsb3HardwareInterfaceFactory implements HardwareInterfaceFactoryInterface {

    private final static Logger log = Logger.getLogger("net.sf.jaer");

    private static LibUsb3HardwareInterfaceFactory instance = new LibUsb3HardwareInterfaceFactory();

    /**
     * @return singleton instance
     */
    public static HardwareInterfaceFactoryInterface instance() {
        return LibUsb3HardwareInterfaceFactory.instance;
    }

    private final Map<ImmutablePair<Short, Short>, Class<?>> vidPidToClassMap = new HashMap<>();
    private final List<Device> compatibleDevicesList = new ArrayList<>();

    private LibUsb3HardwareInterfaceFactory() throws LibUsbException {

        // Initialize LibUsb.
        try {
            // Initialize LibUsb.
            int result = LibUsb.init(null);
            if (result != LibUsb.SUCCESS) {
                throw new LibUsbException("Unable to initialize libusb", result);
            }
            LibUsbHotplug.ensureStarted();
        } catch (UnsatisfiedLinkError | LibUsbException ule) {
            MacosLibusbHelp.maybeShowDialog(ule);
            UnsatisfiedLinkError u = new UnsatisfiedLinkError("Failed to initialize libusb4java!"
                    + "\nOn OS-X you might need to install with brew install libusb."
                    + "\nOn Linux, do you have noexec on your /tmp?\n"
                    + ule.getLocalizedMessage());
            u.setStackTrace(ule.getStackTrace());
            throw u;
        }

        // Build a mapping of VID/PID pairs and corresponding
        // HardwareInterfaces.
        // Classic FX3 vs Mini/Micro CX3 share 152a:8419; getInterface picks
        // the class from bcdDevice (no LibUsb.open).
        addDeviceToMap(CypressFX3.VID, DVXplorerFX3HardwareInterface.PID_FX3, DVXplorerFX3HardwareInterface.class);

        // Includes SciDVS boards that share these PIDs (GAER SciDVSHardwareInterface is not registered).
        addDeviceToMap(CypressFX3.VID, DAViSFX3HardwareInterface.PID_FX3, DAViSFX3HardwareInterface.class);

        addDeviceToMap(CypressFX3.VID, DAViSFX3HardwareInterface.PID_FX2, DAViSFX3HardwareInterface.class);

        addDeviceToMap(CypressFX3.VID, CochleaFX3HardwareInterface.PID_FX3, CochleaFX3HardwareInterface.class);

        // Build up first list of compatible devices.
        refreshCompatibleDevicesList();
    }

    private void addDeviceToMap(final short VID, final short PID, final Class<?> cls) {
        vidPidToClassMap.put(new ImmutablePair<>(VID, PID), cls);
        UsbHardwareRegistry.instance().register(VID, PID, cls);
        LibUsbHotplug.register(VID, PID);
    }

    private void refreshCompatibleDevicesList() {
        // Temporary storage to allow modification.
        final List<Device> tmpDrain = new ArrayList<>(buildCompatibleDevicesList());
        // Device.equals is native-pointer identity; match bus/addr or a rescan
        // unrefs the Device a LIVE CypressFX3 still holds (AEViewer #1 DVX bind loop).
        UsbIds.mergeLibUsbDeviceScan(compatibleDevicesList, tmpDrain);
    }

    private List<Device> buildCompatibleDevicesList() {
        final List<Device> compatibleDevicesListLocal = new ArrayList<>();

        final DeviceList devList = new DeviceList();
        LibUsb.getDeviceList(null, devList);

        final DeviceDescriptor devDesc = new DeviceDescriptor();

        for (final Device dev : devList) {
            LibUsb.getDeviceDescriptor(dev, devDesc);

            final ImmutablePair<Short, Short> vidPid = new ImmutablePair<>(devDesc.idVendor(), devDesc.idProduct());
            if (!vidPidToClassMap.containsKey(vidPid)) {
                continue;
            }

            // List by VID/PID only. LibUsb.open during scan races with a live
            // handle on Windows (ACCESS spam / WinUSB hang on the EDT).
            compatibleDevicesListLocal.add(LibUsb.refDevice(dev));
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
                LibUsb3HardwareInterfaceFactory.log
                        .warning(String.format("You asked for interface number %d but no interfaces are available at all. Check your Device "
                                + "Manager to see if the device has been recognized. You may need to install a driver.", n));
            } else {
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

        Class<?> cls = vidPidToClassMap.get(vidPid);
        if (vidPid.left == CypressFX3.VID && vidPid.right == DVXplorerFX3HardwareInterface.PID_FX3) {
            cls = DVXplorerFX3HardwareInterface.hardwareClassForBcdDevice(devDesc.bcdDevice());
        }

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
