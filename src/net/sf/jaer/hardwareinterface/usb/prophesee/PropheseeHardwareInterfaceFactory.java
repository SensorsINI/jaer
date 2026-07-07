package net.sf.jaer.hardwareinterface.usb.prophesee;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.usb4java.Context;
import org.usb4java.Device;
import org.usb4java.DeviceDescriptor;
import org.usb4java.DeviceHandle;
import org.usb4java.DeviceList;
import org.usb4java.HotplugCallback;
import org.usb4java.LibUsb;
import org.usb4java.LibUsbException;

import net.sf.jaer.hardwareinterface.HardwareInterfaceFactoryInterface;
import net.sf.jaer.hardwareinterface.usb.USBInterface;

/**
 * Enumerates Prophesee EVK4 HD cameras (Cypress 0x04B4:0x00F5).
 */
public class PropheseeHardwareInterfaceFactory implements HardwareInterfaceFactoryInterface {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static PropheseeHardwareInterfaceFactory instance = new PropheseeHardwareInterfaceFactory();

    public static HardwareInterfaceFactoryInterface instance() {
        return instance;
    }

    private final Map<ImmutablePair<Short, Short>, Class<?>> vidPidToClassMap = new HashMap<>();
    private final List<Device> compatibleDevicesList = new ArrayList<>();

    private PropheseeHardwareInterfaceFactory() throws LibUsbException {
        try {
            final int result = LibUsb.init(null);
            if (result != LibUsb.SUCCESS) {
                throw new LibUsbException("Unable to initialize libusb", result);
            }
        } catch (UnsatisfiedLinkError | LibUsbException ule) {
            UnsatisfiedLinkError u = new UnsatisfiedLinkError(
                    "Failed to initialize libusb4java for Prophesee factory: " + ule.getLocalizedMessage());
            u.setStackTrace(ule.getStackTrace());
            throw u;
        }

        if (!LibUsb.hasCapability(LibUsb.CAP_HAS_HOTPLUG)) {
            log.info("Prophesee: LibUsb hotplug not supported on this platform");
        }
        addDeviceToMap(PropheseeHardwareInterface.VID, PropheseeHardwareInterface.PID_EVK4_HD,
                PropheseeHardwareInterface.class);
        refreshCompatibleDevicesList();
    }

    private void addDeviceToMap(final short vid, final short pid, final Class<?> cls) {
        vidPidToClassMap.put(new ImmutablePair<>(vid, pid), cls);
        if (LibUsb.hasCapability(LibUsb.CAP_HAS_HOTPLUG)) {
            HotplugCallback callback = (Context cntxt, Device device, int event, Object userData) -> {
                DeviceDescriptor descriptor = new DeviceDescriptor();
                int errCode = LibUsb.getDeviceDescriptor(device, descriptor);
                if (errCode == LibUsb.SUCCESS) {
                    log.info(String.format("Prophesee LibUsb: %s VID:PID=%04x:%04x",
                            event == LibUsb.HOTPLUG_EVENT_DEVICE_ARRIVED ? "Connected" : "Disconnected",
                            descriptor.idVendor(), descriptor.idProduct()));
                }
                return 0;
            };
            LibUsb.hotplugRegisterCallback(null,
                    LibUsb.HOTPLUG_EVENT_DEVICE_ARRIVED | LibUsb.HOTPLUG_EVENT_DEVICE_LEFT,
                    LibUsb.HOTPLUG_ENUMERATE,
                    vid, pid, LibUsb.HOTPLUG_MATCH_ANY,
                    callback, null, null);
        }
    }

    private void refreshCompatibleDevicesList() {
        final List<Device> tmpDrain = new ArrayList<>(buildCompatibleDevicesList());
        final List<Device> removals = new ArrayList<>();
        for (final Device element : compatibleDevicesList) {
            if (tmpDrain.contains(element)) {
                tmpDrain.remove(element);
            } else {
                removals.add(element);
                LibUsb.unrefDevice(element);
            }
        }
        compatibleDevicesList.removeAll(removals);
        compatibleDevicesList.addAll(tmpDrain);
        tmpDrain.clear();
    }

    private List<Device> buildCompatibleDevicesList() {
        final List<Device> list = new ArrayList<>();
        final DeviceList devList = new DeviceList();
        LibUsb.getDeviceList(null, devList);
        final DeviceDescriptor devDesc = new DeviceDescriptor();

        for (final Device dev : devList) {
            LibUsb.getDeviceDescriptor(dev, devDesc);
            final ImmutablePair<Short, Short> vidPid =
                    new ImmutablePair<>(devDesc.idVendor(), devDesc.idProduct());
            if (!vidPidToClassMap.containsKey(vidPid)) {
                continue;
            }

            final DeviceHandle devHandle = new DeviceHandle();
            final int openStatus = LibUsb.open(dev, devHandle);
            if (openStatus == LibUsb.SUCCESS) {
                final int driverStatus = LibUsb.kernelDriverActive(devHandle, 0);
                LibUsb.close(devHandle);
                if (driverStatus != LibUsb.ERROR_NOT_SUPPORTED && driverStatus != LibUsb.SUCCESS) {
                    log.warning(String.format(
                            "Prophesee EVK4 HD %04x:%04x found but a kernel driver is bound. "
                                    + "Install the Prophesee WinUSB driver (wdi-simple) or replace with WinUSB via Zadig.",
                            devDesc.idVendor(), devDesc.idProduct()));
                }
            } else {
                log.warning(String.format(
                        "Prophesee EVK4 HD %04x:%04x detected but LibUsb.open failed: %s. "
                                + "On Windows, install WinUSB with: wdi-simple.exe -n \"EVK\" -m \"Prophesee\" -v 0x04b4 -p 0x00f5 "
                                + "(admin Command Prompt), or use Zadig to assign WinUSB to the EVK4.",
                        devDesc.idVendor(), devDesc.idProduct(), LibUsb.errorName(openStatus)));
            }

            // List matching devices even when open fails so they appear in Interface menu.
            list.add(LibUsb.refDevice(dev));
        }
        LibUsb.freeDeviceList(devList, true);
        return list;
    }

    @Override
    synchronized public USBInterface getFirstAvailableInterface() {
        return getInterface(0);
    }

    @Override
    synchronized public USBInterface getInterface(final int n) {
        refreshCompatibleDevicesList();
        final int numAvailable = compatibleDevicesList.size();
        if (n > numAvailable - 1) {
            if (numAvailable == 0) {
                log.warning("No Prophesee EVK4 HD interfaces available (0x04B4:0x00F5)");
            } else {
                log.warning("Requested Prophesee interface " + n + " but only " + numAvailable + " available");
            }
            return null;
        }

        final Device dev = compatibleDevicesList.get(n);
        final DeviceDescriptor devDesc = new DeviceDescriptor();
        LibUsb.getDeviceDescriptor(dev, devDesc);
        final Class<?> cls = vidPidToClassMap.get(new ImmutablePair<>(devDesc.idVendor(), devDesc.idProduct()));
        try {
            final Constructor<?> constr = cls.getDeclaredConstructor(Device.class);
            return (USBInterface) constr.newInstance(dev);
        } catch (NoSuchMethodException | SecurityException | InstantiationException
                | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            log.warning("Failed to construct PropheseeHardwareInterface: " + e.getMessage());
            return null;
        }
    }

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
