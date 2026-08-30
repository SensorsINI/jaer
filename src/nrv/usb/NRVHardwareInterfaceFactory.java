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
import org.usb4java.DeviceList;
import org.usb4java.LibUsb;
import org.usb4java.LibUsbException;

import net.sf.jaer.hardwareinterface.HardwareInterfaceFactoryInterface;
import net.sf.jaer.hardwareinterface.usb.LibUsbHotplug;
import net.sf.jaer.hardwareinterface.usb.MacosLibusbHelp;
import net.sf.jaer.hardwareinterface.usb.USBInterface;
import net.sf.jaer.hardwareinterface.usb.UsbHardwareRegistry;

/**
 * Enumerates NRV DVS cameras (Cypress 0x04B4:0x00F0 and 0x04B4:0x00F1).
 * Engineering-sample DELTA01 shows in Windows Device Manager as a cryptic
 * Cypress FX3; match by VID/PID, not the Device Manager string.
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
            MacosLibusbHelp.maybeShowDialog(ule);
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

            // List by VID/PID only. LibUsb.open during scan races with a live handle.
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
