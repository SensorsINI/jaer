package net.sf.jaer.hardwareinterface.usb.prophesee;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeSupport;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import org.usb4java.Device;
import org.usb4java.DeviceDescriptor;
import org.usb4java.DeviceHandle;
import org.usb4java.LibUsb;

import net.sf.jaer.aemonitor.AEListener;
import net.sf.jaer.aemonitor.AEMonitorInterface;
import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.aemonitor.AEPacketRawPool;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.biasgen.BiasgenHardwareInterface;
import ch.unizh.ini.jaer.chip.prophesee.PropheseeConfig;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.ReaderBufferControl;
import net.sf.jaer.hardwareinterface.usb.USBInterface;
import net.sf.jaer.hardwareinterface.usb.prophesee.evk4.Evk4BoardCommand;
import net.sf.jaer.hardwareinterface.usb.prophesee.evk4.Imx636Init;

/**
 * LibUsb driver for Prophesee EVK4 HD (Sony IMX636, VID 0x04B4 PID 0x00F5).
 */
public class PropheseeHardwareInterface implements BiasgenHardwareInterface, AEMonitorInterface,
        ReaderBufferControl, USBInterface {

    public static final short VID = (short) 0x04B4;
    public static final short PID_EVK4_HD = (short) 0x00F5;

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final int AE_BUFFER_SIZE = 100000;
    private static final int MAX_AE_BUFFER_SIZE = 10_000_000;
    private static final PropertyChangeEvent NEW_EVENTS_PROPERTY_CHANGE =
            new PropertyChangeEvent(PropheseeHardwareInterface.class, "NewEvents", null, null);

    private final Preferences prefs = Preferences.userNodeForPackage(PropheseeHardwareInterface.class);
    private final Device device;
    private DeviceHandle deviceHandle;
    private DeviceDescriptor deviceDescriptor;
    private AEChip chip;
    private PropheseeAEReader aeReader;
    private final AEPacketRawPool aePacketRawPool = new AEPacketRawPool(this);
    private final PropertyChangeSupport support = new PropertyChangeSupport(this);

    private String serial = "";
    private PropheseeBiases biases = new PropheseeBiases();
    private PropheseeBiases chipFirmwareBiases = new PropheseeBiases();
    private boolean deviceInitialized;

    private boolean isOpened = false;
    private volatile boolean usbTransferFailed = false;
    private boolean eventAcquisitionEnabled = false;
    private int buffersize = prefs.getInt("Prophesee.aeBufferSize", AE_BUFFER_SIZE);
    private int eventCounter = 0;
    private int estimatedEventRate = 0;
    private String[] stringDescriptors = new String[3];

    public PropheseeHardwareInterface(Device device) {
        this.device = device;
    }

    public DeviceHandle getDeviceHandle() {
        return deviceHandle;
    }

    AEPacketRawPool getAePacketRawPool() {
        return aePacketRawPool;
    }

    PropertyChangeSupport getReaderSupportInternal() {
        return support;
    }

    int getEventCounter() {
        return eventCounter;
    }

    void setEventCounter(int eventCounter) {
        this.eventCounter = eventCounter;
    }

    public String getSerial() {
        return serial;
    }

    public PropheseeBiases getBiases() {
        return biases;
    }

    public PropheseeBiases getChipFirmwareBiases() {
        return chipFirmwareBiases;
    }

    public void setBiases(PropheseeBiases biases) throws HardwareInterfaceException {
        this.biases = biases.copy();
        if (isOpen() && deviceInitialized) {
            Imx636Init.applyBiases(deviceHandle, this.biases);
        }
    }

    void markUsbDisconnected(int transferStatus) {
        if (usbTransferFailed) {
            return;
        }
        usbTransferFailed = true;
        log.warning("Prophesee USB disconnected: " + LibUsb.errorName(transferStatus));
        new Thread(() -> {
            synchronized (PropheseeHardwareInterface.this) {
                if (isOpen()) {
                    close();
                }
            }
        }, "Prophesee-USB-disconnect").start();
    }

    boolean isUsbTransferFailed() {
        return usbTransferFailed;
    }

    @Override
    public synchronized void open() throws HardwareInterfaceException {
        if (isOpen()) {
            return;
        }
        deviceHandle = new DeviceHandle();
        int status = LibUsb.open(device, deviceHandle);
        if (status != LibUsb.SUCCESS) {
            deviceHandle = null;
            throw new HardwareInterfaceException("open(): " + LibUsb.errorName(status));
        }

        deviceDescriptor = new DeviceDescriptor();
        status = LibUsb.getDeviceDescriptor(device, deviceDescriptor);
        if (status != LibUsb.SUCCESS) {
            LibUsb.close(deviceHandle);
            deviceHandle = null;
            throw new HardwareInterfaceException("getDeviceDescriptor(): " + LibUsb.errorName(status));
        }

        if (deviceDescriptor.idProduct() != PID_EVK4_HD) {
            LibUsb.close(deviceHandle);
            deviceHandle = null;
            throw new HardwareInterfaceException("Unsupported Prophesee PID: "
                    + String.format("%04x", deviceDescriptor.idProduct()));
        }

        acquireDevice();

        try {
            for (int i = 0; i < stringDescriptors.length; i++) {
                stringDescriptors[i] = LibUsb.getStringDescriptor(deviceHandle, (byte) (i + 1));
            }
        } catch (Exception e) {
            log.warning("Could not read all USB string descriptors: " + e.getMessage());
        }

        serial = Evk4BoardCommand.readSerial(deviceHandle);
        Imx636Init.initializeAndStart(deviceHandle, biases);
        chipFirmwareBiases = Imx636Init.readDefaultBiases(deviceHandle);
        deviceInitialized = true;

        usbTransferFailed = false;
        isOpened = true;
        log.info("Prophesee EVK4 opened serial=" + serial + " VID:PID="
                + String.format("%04x:%04x", deviceDescriptor.idVendor(), deviceDescriptor.idProduct()));
    }

    private void acquireDevice() throws HardwareInterfaceException {
        if (LibUsb.kernelDriverActive(deviceHandle, 0) == 1) {
            final int detach = LibUsb.detachKernelDriver(deviceHandle, 0);
            if (detach != LibUsb.SUCCESS && detach != LibUsb.ERROR_NOT_SUPPORTED) {
                log.warning("detachKernelDriver: " + LibUsb.errorName(detach));
            }
        }
        final int status = LibUsb.claimInterface(deviceHandle, 0);
        if (status != LibUsb.SUCCESS) {
            throw new HardwareInterfaceException("claimInterface(): " + LibUsb.errorName(status));
        }
    }

    private void releaseDevice() throws HardwareInterfaceException {
        final int status = LibUsb.releaseInterface(deviceHandle, 0);
        if (status != LibUsb.SUCCESS) {
            throw new HardwareInterfaceException("releaseInterface(): " + LibUsb.errorName(status));
        }
    }

    @Override
    public synchronized void close() {
        if (!isOpen()) {
            return;
        }
        try {
            setEventAcquisitionEnabled(false);
        } catch (HardwareInterfaceException e) {
            log.warning("Error disabling event acquisition on close: " + e.getMessage());
        }
        if (deviceHandle != null) {
            Imx636Init.shutdown(deviceHandle);
        }
        try {
            releaseDevice();
        } catch (HardwareInterfaceException e) {
            log.warning("Error releasing device: " + e.getMessage());
        }
        if (deviceHandle != null) {
            LibUsb.close(deviceHandle);
            deviceHandle = null;
        }
        deviceDescriptor = null;
        deviceInitialized = false;
        aePacketRawPool.reset();
        isOpened = false;
    }

    @Override
    public boolean isOpen() {
        return isOpened;
    }

    @Override
    public String getTypeName() {
        return "Prophesee EVK4 HD";
    }

    @Override
    public AEPacketRaw acquireAvailableEventsFromDriver() throws HardwareInterfaceException {
        if (usbTransferFailed) {
            throw new HardwareInterfaceException("Prophesee USB device disconnected");
        }
        if (!isOpen()) {
            open();
        }
        if (!eventAcquisitionEnabled) {
            setEventAcquisitionEnabled(true);
        }
        aePacketRawPool.swap();
        final AEPacketRaw lastEventsAcquired = aePacketRawPool.readBuffer();
        final int nEvents = lastEventsAcquired.getNumEvents();
        eventCounter = 0;
        computeEstimatedEventRate(lastEventsAcquired);
        if (nEvents != 0) {
            support.firePropertyChange(NEW_EVENTS_PROPERTY_CHANGE);
        }
        return lastEventsAcquired;
    }

    private void computeEstimatedEventRate(AEPacketRaw events) {
        if (events == null || events.getNumEvents() < 2) {
            estimatedEventRate = 0;
            return;
        }
        final int[] ts = events.getTimestamps();
        final int n = events.getNumEvents();
        final int dt = ts[n - 1] - ts[0];
        estimatedEventRate = dt <= 0 ? 0 : (int) ((1e6f * n) / dt);
    }

    @Override
    public int getNumEventsAcquired() {
        return eventCounter;
    }

    @Override
    public AEPacketRaw getEvents() {
        throw new UnsupportedOperationException("Use acquireAvailableEventsFromDriver()");
    }

    @Override
    public void resetTimestamps() {
        if (aeReader != null) {
            aeReader.resetTimestamps();
        }
    }

    @Override
    public boolean overrunOccurred() {
        return aePacketRawPool.readBuffer().overrunOccuredFlag;
    }

    @Override
    public int getAEBufferSize() {
        return buffersize;
    }

    @Override
    public void setAEBufferSize(int size) {
        if (size < 1000 || size > MAX_AE_BUFFER_SIZE) {
            return;
        }
        buffersize = size;
        prefs.putInt("Prophesee.aeBufferSize", size);
        synchronized (aePacketRawPool) {
            aePacketRawPool.allocateMemory();
        }
    }

    @Override
    public void setEventAcquisitionEnabled(boolean enable) throws HardwareInterfaceException {
        if (enable) {
            if (aeReader == null) {
                aeReader = new PropheseeAEReader(this);
                synchronized (aePacketRawPool) {
                    aePacketRawPool.allocateMemory();
                }
            }
            aeReader.startThread();
        } else if (aeReader != null) {
            aeReader.stopThread();
            aeReader = null;
        }
        eventAcquisitionEnabled = enable;
    }

    @Override
    public boolean isEventAcquisitionEnabled() {
        return eventAcquisitionEnabled;
    }

    @Override
    public void addAEListener(AEListener listener) {
        support.addPropertyChangeListener(listener);
    }

    @Override
    public void removeAEListener(AEListener listener) {
        support.removePropertyChangeListener(listener);
    }

    @Override
    public int getMaxCapacity() {
        return MAX_AE_BUFFER_SIZE;
    }

    @Override
    public int getEstimatedEventRate() {
        return estimatedEventRate;
    }

    @Override
    public int getTimestampTickUs() {
        return 1;
    }

    @Override
    public void setChip(AEChip chip) {
        this.chip = chip;
    }

    @Override
    public AEChip getChip() {
        return chip;
    }

    @Override
    public String[] getStringDescriptors() {
        if (stringDescriptors == null) {
            return new String[]{"", "", ""};
        }
        return stringDescriptors.clone();
    }

    @Override
    public short getVID_THESYCON_FX2_CPLD() {
        return deviceDescriptor == null ? VID : deviceDescriptor.idVendor();
    }

    @Override
    public short getPID() {
        return deviceDescriptor == null ? 0 : deviceDescriptor.idProduct();
    }

    @Override
    public short getDID() {
        return deviceDescriptor == null ? 0 : deviceDescriptor.bcdDevice();
    }

    @Override
    public void setPowerDown(boolean powerDown) throws HardwareInterfaceException {
        log.info("setPowerDown(" + powerDown + ") not implemented for Prophesee EVK4");
    }

    @Override
    public void sendConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        if (biasgen instanceof PropheseeConfig config) {
            setBiases(config.getBiases());
        }
    }

    @Override
    public void flashConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        log.info("flashConfiguration not supported for Prophesee EVK4");
    }

    @Override
    public byte[] formatConfigurationBytes(Biasgen biasgen) {
        return new byte[0];
    }

    @Override
    public int getFifoSize() {
        return aeReader == null ? 0 : aeReader.getFifoSize();
    }

    @Override
    public void setFifoSize(int fifoSize) {
        if (aeReader != null) {
            aeReader.setFifoSize(fifoSize);
        }
    }

    @Override
    public int getNumBuffers() {
        return aeReader == null ? 0 : aeReader.getNumBuffers();
    }

    @Override
    public void setNumBuffers(int numBuffers) {
        if (aeReader != null) {
            aeReader.setNumBuffers(numBuffers);
        }
    }

    @Override
    public PropertyChangeSupport getReaderSupport() {
        return aeReader != null ? aeReader.getReaderSupport() : support;
    }
}
