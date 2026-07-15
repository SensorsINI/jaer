package nrv.usb;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeSupport;
import java.util.List;
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
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.JaerConstants;
import net.sf.jaer.util.VendorPrefsMigration;
import nrv.chip.NRVConfig;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.ReaderBufferControl;
import net.sf.jaer.hardwareinterface.usb.USBInterface;

/**
 * LibUsb driver for NRV DVS cameras (Cypress VID 0x04B4, PID 0x00F0 / 0x00F1).
 *
 * @see https://nrv.kr/
 */
public class NRVHardwareInterface implements BiasgenHardwareInterface, AEMonitorInterface, ReaderBufferControl, USBInterface {

    public static final short VID = (short) 0x04B4;
    public static final short PID_FX20 = (short) 0x00F0;
    public static final short PID_CX3 = (short) 0x00F1;

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final int AE_BUFFER_SIZE = 500_000;
    private static final int MAX_AE_BUFFER_SIZE = 10_000_000;
    private static final PropertyChangeEvent NEW_EVENTS_PROPERTY_CHANGE =
            new PropertyChangeEvent(NRVHardwareInterface.class, "NewEvents", null, null);

    private static final Preferences PREFS = JaerConstants.PREFS_ROOT_HARDWARE.node("NRV");

    static {
        VendorPrefsMigration.migrateHardwarePrefs(VendorPrefsMigration.LEGACY_NRV_HW_PACKAGE, PREFS);
    }

    private final Preferences prefs = PREFS;
    private final Device device;
    private DeviceHandle deviceHandle;
    private DeviceDescriptor deviceDescriptor;
    private AEChip chip;
    private NRVI2CTransport i2cTransport;
    private NRVAEReader aeReader;
    private int buffersize = loadAeBufferSizePref();
    private final AEPacketRawPool aePacketRawPool = new AEPacketRawPool(this);
    private final PropertyChangeSupport support = new PropertyChangeSupport(this);

    private boolean isOpened = false;
    private volatile boolean usbTransferFailed = false;
    private boolean eventAcquisitionEnabled = false;
    private boolean settingsApplied = false;
    private int eventCounter = 0;
    private int estimatedEventRate = 0;
    private String[] stringDescriptors = new String[3];
    private List<NRVRegisterSetting> loadedSettings;

    public NRVHardwareInterface(Device device) {
        this.device = device;
    }

    private int loadAeBufferSizePref() {
        final int saved = prefs.getInt("NRV.aeBufferSize", AE_BUFFER_SIZE);
        if (saved == 100_000) {
            prefs.putInt("NRV.aeBufferSize", AE_BUFFER_SIZE);
            return AE_BUFFER_SIZE;
        }
        if (saved < 1000 || saved > MAX_AE_BUFFER_SIZE) {
            log.warning("Invalid NRV.aeBufferSize " + saved + ", using " + AE_BUFFER_SIZE);
            prefs.putInt("NRV.aeBufferSize", AE_BUFFER_SIZE);
            return AE_BUFFER_SIZE;
        }
        return saved;
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

    private NRVAEReader ensureAeReader() {
        if (aeReader == null) {
            aeReader = new NRVAEReader(this);
        }
        return aeReader;
    }

    int getEventCounter() {
        return eventCounter;
    }

    void setEventCounter(int eventCounter) {
        this.eventCounter = eventCounter;
    }

    public boolean isSettingsApplied() {
        return settingsApplied;
    }

    public void setSettingsApplied(boolean settingsApplied) {
        this.settingsApplied = settingsApplied;
    }

    public List<NRVRegisterSetting> getLoadedSettings() {
        return loadedSettings;
    }

    public void applySettings(List<NRVRegisterSetting> settings) throws HardwareInterfaceException {
        if (!isOpen()) {
            open();
        }
        if (i2cTransport == null) {
            throw new HardwareInterfaceException("I2C transport not initialized");
        }
        for (NRVRegisterSetting setting : settings) {
            setting.setApplied(false);
            if (setting.isWait()) {
                try {
                    Thread.sleep(setting.getValue());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new HardwareInterfaceException("Interrupted during settings wait");
                }
                setting.setApplied(true);
            } else {
                i2cTransport.writeReg(setting.getSlaveAddr(), setting.getRegAddr(), setting.getValue());
                setting.setApplied(true);
            }
        }
        loadedSettings = settings;
        settingsApplied = true;
        support.firePropertyChange("settingsApplied", false, true);
        log.info("Applied " + settings.size() + " NRV register settings");
    }

    /**
     * Writes one I2C register without modifying the loaded settings file.
     */
    public void writeRegister(int slaveAddr, int regAddr, int value) throws HardwareInterfaceException {
        if (!isOpen()) {
            open();
        }
        if (i2cTransport == null) {
            throw new HardwareInterfaceException("I2C transport not initialized");
        }
        i2cTransport.writeReg(slaveAddr, regAddr, value);
    }

    /**
     * Notifies the USB parser to drop stale ref/full timestamp state after timing I2C writes.
     */
    public void notifyTimingRegisterChanged(int regAddr, String reason) {
        if (aeReader != null) {
            syncParserTimestampScale();
            aeReader.resyncTimingAfterRegisterChange(regAddr, reason);
        }
    }

    /** Push TSTAMP_REF / TSTAMP_SUB from loaded settings into the live USB parser. */
    public void syncParserTimestampScale() {
        if (aeReader == null || chip == null || !(chip.getBiasgen() instanceof NRVConfig config)) {
            return;
        }
        aeReader.getParser().setTimestampScale(
                config.getTstampRefUnitVal(),
                config.getTimestampSubUnit());
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

        acquireDevice();
        selectI2CTransport(deviceDescriptor.idProduct());

        try {
            for (int i = 0; i < stringDescriptors.length; i++) {
                stringDescriptors[i] = LibUsb.getStringDescriptor(deviceHandle, (byte) (i + 1));
            }
        } catch (Exception e) {
            log.warning("Could not read all USB string descriptors: " + e.getMessage());
        }

        usbTransferFailed = false;
        isOpened = true;
        log.info("NRV device opened VID:PID="
                + String.format("%04x:%04x", deviceDescriptor.idVendor(), deviceDescriptor.idProduct()));
    }

    /**
     * Called from the USB transfer thread when bulk reads fail (e.g. unplug).
     * Must not call {@link #close()} on the transfer thread (would deadlock).
     */
    void markUsbDisconnected(int transferStatus) {
        if (usbTransferFailed) {
            return;
        }
        usbTransferFailed = true;
        log.warning("NRV USB disconnected: " + LibUsb.errorName(transferStatus));
        new Thread(() -> {
            synchronized (NRVHardwareInterface.this) {
                if (isOpen()) {
                    close();
                }
            }
        }, "NRV-USB-disconnect").start();
    }

    boolean isUsbTransferFailed() {
        return usbTransferFailed;
    }

    private void selectI2CTransport(short pid) throws HardwareInterfaceException {
        if (pid == PID_FX20) {
            i2cTransport = new NRVI2CFX20Transport(deviceHandle);
        } else if (pid == PID_CX3) {
            i2cTransport = new NRVI2CCX3Transport(deviceHandle);
        } else {
            throw new HardwareInterfaceException("Unsupported NRV PID: " + String.format("%04x", pid));
        }
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
        i2cTransport = null;
        settingsApplied = false;
        aePacketRawPool.reset();
        isOpened = false;
    }

    @Override
    public boolean isOpen() {
        return isOpened;
    }

    @Override
    public String getTypeName() {
        if (deviceDescriptor == null) {
            return "NRV DVS";
        }
        if (deviceDescriptor.idProduct() == PID_FX20) {
            return "NRV DVS FX20";
        }
        if (deviceDescriptor.idProduct() == PID_CX3) {
            return "NRV DVS CX3";
        }
        return "NRV DVS";
    }

    @Override
    public AEPacketRaw acquireAvailableEventsFromDriver() throws HardwareInterfaceException {
        if (usbTransferFailed) {
            throw new HardwareInterfaceException("NRV USB device disconnected");
        }
        if (!isOpen()) {
            open();
        }
        ensureSettingsBeforeAcquisition();
        if (!settingsApplied) {
            synchronized (aePacketRawPool) {
                aePacketRawPool.swap();
                eventCounter = 0;
            }
            if (aeReader != null) {
                aeReader.onWriteBufferConsumed();
            }
            return aePacketRawPool.readBuffer();
        }
        if (!eventAcquisitionEnabled) {
            setEventAcquisitionEnabled(true);
        }
        final AEPacketRaw lastEventsAcquired;
        synchronized (aePacketRawPool) {
            aePacketRawPool.swap();
            eventCounter = 0;
            lastEventsAcquired = aePacketRawPool.readBuffer();
        }
        if (aeReader != null) {
            aeReader.onWriteBufferConsumed();
        }
        final int nEvents = lastEventsAcquired.getNumEvents();
        computeEstimatedEventRate(lastEventsAcquired);
        if (nEvents != 0) {
            support.firePropertyChange(NEW_EVENTS_PROPERTY_CHANGE);
        } else if (lastEventsAcquired.overrunOccuredFlag) {
            support.firePropertyChange(NEW_EVENTS_PROPERTY_CHANGE);
        }
        return lastEventsAcquired;
    }

    private void ensureSettingsBeforeAcquisition() throws HardwareInterfaceException {
        if (settingsApplied) {
            return;
        }
        if (loadedSettings != null) {
            applySettings(loadedSettings);
            return;
        }
        if (chip != null && chip.getBiasgen() instanceof NRVConfig config) {
            if (!config.ensureAppliedToHardware()) {
                log.warning("NRV: register settings not applied — load biasgenSettings/NRV/S5KRC1S_300_CX3.txt "
                        + "via Biases > File > Load settings");
            }
        }
    }

    private void computeEstimatedEventRate(AEPacketRaw events) {
        if (events == null || events.getNumEvents() < 2) {
            estimatedEventRate = 0;
            return;
        }
        final int[] ts = events.getTimestamps();
        final int n = events.getNumEvents();
        final int dt = ts[n - 1] - ts[0];
        if (dt <= 0) {
            estimatedEventRate = 0;
        } else {
            estimatedEventRate = (int) ((1e6f * n) / dt);
        }
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
        log.info("NRV resetTimestamps(): zeroing jAER time at current device time (no hardware reset on CX3/FX20)");
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
            log.warning("Ignoring unreasonable aeBufferSize " + size
                    + " (allowed range 1000.." + MAX_AE_BUFFER_SIZE + ")");
            return;
        }
        buffersize = size;
        prefs.putInt("NRV.aeBufferSize", size);
        allocateAEBuffers();
    }

    private void allocateAEBuffers() {
        synchronized (aePacketRawPool) {
            aePacketRawPool.allocateMemory();
        }
    }

    @Override
    public void setEventAcquisitionEnabled(boolean enable) throws HardwareInterfaceException {
        if (enable) {
            ensureSettingsBeforeAcquisition();
            if (!settingsApplied) {
                log.warning("NRV: event reader not started until settings are applied "
                        + "(Biases > File > Load settings, or biasgenSettings/NRV/S5KRC1S_300_CX3.txt)");
                return;
            }
            if (aeReader == null) {
                aeReader = new NRVAEReader(this);
                allocateAEBuffers();
            }
            syncParserTimestampScale();
            aeReader.startThread();
        } else if (aeReader != null) {
            aeReader.stopThread();
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
        log.info("setPowerDown(" + powerDown + ") not implemented for NRV devices");
    }

    @Override
    public void sendConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        if (biasgen instanceof NRVConfig config && config.getLoadedSettings() != null) {
            applySettings(config.getLoadedSettings());
            return;
        }
        if (loadedSettings != null) {
            applySettings(loadedSettings);
        }
    }

    @Override
    public void flashConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
        log.info("flashConfiguration not supported for NRV devices");
    }

    @Override
    public byte[] formatConfigurationBytes(Biasgen biasgen) {
        return new byte[0];
    }

    @Override
    public int getFifoSize() {
        return ensureAeReader().getFifoSize();
    }

    @Override
    public void setFifoSize(int fifoSize) {
        ensureAeReader().setFifoSize(fifoSize);
    }

    @Override
    public int getNumBuffers() {
        return ensureAeReader().getNumBuffers();
    }

    @Override
    public void setNumBuffers(int numBuffers) {
        ensureAeReader().setNumBuffers(numBuffers);
    }

    @Override
    public PropertyChangeSupport getReaderSupport() {
        return ensureAeReader().getReaderSupport();
    }
}
