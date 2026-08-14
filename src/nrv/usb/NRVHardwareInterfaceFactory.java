package nrv.usb;

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
import org.usb4java.LibUsbException;

import net.sf.jaer.hardwareinterface.HardwareInterfaceFactoryInterface;
import net.sf.jaer.hardwareinterface.usb.LibUsbHotplug;
import net.sf.jaer.hardwareinterface.usb.USBInterface;
import net.sf.jaer.hardwareinterface.usb.UsbHardwareRegistry;

/**
 * Enumerates NRV DVS cameras (Cypress 0x04B4:0x00F0 and 0x04B4:0x00F1).
 *
 * @see https://nrv.kr/
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

        LibUsbHotplug.ensureStarted();
        addDeviceToMap(NRVHardwareInterface.VID, NRVHardwareInterface.PID_FX20, NRVHardwareInterface.class);
        addDeviceToMap(NRVHardwareInterface.VID, NRVHardwareInterface.PID_CX3, NRVHardwareInterface.class);
        refreshCompatibleDevicesList();
    }

    private void addDeviceToMap(final short vid, final short pid, final Class<?> cls) {
        vidPidToClassMap.put(new ImmutablePair<>(vid, pid), cls);
        UsbHardwareRegistry.instance().register(vid, pid, cls);
        LibUsbHotplug.register(vid, pid);
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

            // Match Prophesee: list by VID/PID even when open fails. Requiring a successful
            // open here hides the device from the Interface menu whenever WinUSB already has
            // an exclusive handle (or open fails with ACCESS for other reasons).
            final DeviceHandle devHandle = new DeviceHandle();
            final int openStatus = LibUsb.open(dev, devHandle);
            if (openStatus == LibUsb.SUCCESS) {
                final int driverStatus = LibUsb.kernelDriverActive(devHandle, 0);
                LibUsb.close(devHandle);
                if (driverStatus != LibUsb.ERROR_NOT_SUPPORTED && driverStatus != LibUsb.SUCCESS) {
                    log.warning(String.format(
                            "NRV %04x:%04x found but a kernel driver is bound (status=%d). "
                                    + "On Windows use Zadig to install WinUSB (not libusb-win32) for this device.",
                            devDesc.idVendor() & 0xffff, devDesc.idProduct() & 0xffff, driverStatus));
                }
            } else if (openStatus == LibUsb.ERROR_ACCESS || openStatus == LibUsb.ERROR_BUSY) {
                log.info(String.format(
                        "NRV %04x:%04x present but LibUsb.open=%s (often already open by jAER, or wrong driver). "
                                + "Still listing in Interface menu. Prefer WinUSB via Zadig for usb4java.",
                        devDesc.idVendor() & 0xffff, devDesc.idProduct() & 0xffff,
                        LibUsb.errorName(openStatus)));
            } else {
                log.warning(String.format(
                        "NRV %04x:%04x detected but LibUsb.open failed: %s. "
                                + "On Windows, Zadig → WinUSB for USB ID 04B4:00F0 (FX20) or 04B4:00F1 (CX3).",
                        devDesc.idVendor() & 0xffff, devDesc.idProduct() & 0xffff,
                        LibUsb.errorName(openStatus)));
            }

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
