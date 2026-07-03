package net.sf.jaer.hardwareinterface.usb.nrv;

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
 * Enumerates NRV DVS cameras (Cypress 0x04B4:0x00F0 and 0x04B4:0x00F1).
 */
public class NRVHardwareInterfaceFactory implements HardwareInterfaceFactoryInterface {

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static NRVHardwareInterfaceFactory instance = new NRVHardwareInterfaceFactory();

    public static HardwareInterfaceFactoryInterface instance() {
        return instance;
    }

    private final Map<ImmutablePair<Short, Short>, Class<?>> vidPidToClassMap = new HashMap<>();
    private final List<Device> compatibleDevicesList = new ArrayList<>();

    private NRVHardwareInterfaceFactory() throws LibUsbException {
        try {
            final int result = LibUsb.init(null);
            if (result != LibUsb.SUCCESS) {
                throw new LibUsbException("Unable to initialize libusb", result);
            }
        } catch (UnsatisfiedLinkError | LibUsbException ule) {
            UnsatisfiedLinkError u = new UnsatisfiedLinkError(
                    "Failed to initialize libusb4java for NRV factory: " + ule.getLocalizedMessage());
            u.setStackTrace(ule.getStackTrace());
            throw u;
        }

        if (!LibUsb.hasCapability(LibUsb.CAP_HAS_HOTPLUG)) {
            log.info("NRV: LibUsb hotplug not supported on this platform; replug recovery relies on AEViewer WAITING poll");
        }
        addDeviceToMap(NRVHardwareInterface.VID, NRVHardwareInterface.PID_FX20, NRVHardwareInterface.class);
        addDeviceToMap(NRVHardwareInterface.VID, NRVHardwareInterface.PID_CX3, NRVHardwareInterface.class);
        refreshCompatibleDevicesList();
    }

    private void addDeviceToMap(final short vid, final short pid, final Class<?> cls) {
        vidPidToClassMap.put(new ImmutablePair<>(vid, pid), cls);
        if (LibUsb.hasCapability(LibUsb.CAP_HAS_HOTPLUG)) {
            HotplugCallback callback = (Context cntxt, Device device, int event, Object userData) -> {
                DeviceDescriptor descriptor = new DeviceDescriptor();
                int errCode = LibUsb.getDeviceDescriptor(device, descriptor);
                if (errCode == LibUsb.SUCCESS) {
                    log.info(String.format("NRV LibUsb: %s VID:PID=%04x:%04x",
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
            int status = LibUsb.open(dev, devHandle);
            if (status != LibUsb.SUCCESS) {
                continue;
            }
            status = LibUsb.kernelDriverActive(devHandle, 0);
            LibUsb.close(devHandle);
            if ((status == LibUsb.ERROR_NOT_SUPPORTED || status == LibUsb.SUCCESS)) {
                list.add(LibUsb.refDevice(dev));
            }
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
                log.warning("No NRV interfaces available (0x04B4:0x00F0 / 0x00F1)");
            } else {
                log.warning("Requested NRV interface " + n + " but only " + numAvailable + " available");
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
            log.warning("Failed to construct NRVHardwareInterface: " + e.getMessage());
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
