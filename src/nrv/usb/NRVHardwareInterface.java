package nrv.usb;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeSupport;
import java.nio.IntBuffer;
import java.util.List;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import org.usb4java.BufferUtils;
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
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PacketBundlePool;
import net.sf.jaer.util.VendorPrefsMigration;
import nrv.chip.NRVConfig;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.LibUsbLinkInfo;
import net.sf.jaer.hardwareinterface.usb.UsbIds;
import net.sf.jaer.hardwareinterface.usb.ReaderBufferControl;
import net.sf.jaer.hardwareinterface.usb.USBInterface;
import net.sf.jaer.hardwareinterface.usb.UsbAsyncBulkReaderLifecycle;
import net.sf.jaer.hardwareinterface.usb.UsbReaderBufferSettings;

/**
 * LibUsb driver for NRV DVS cameras (Cypress VID 0x04B4, PID 0x00F0 / 0x00F1).
 * DELTA01 engineering samples enumerate as a generic Cypress FX3 in Windows
 * Device Manager (name is not “NRV” or “DELTA01”).
 *
 * @see https://nrv.kr/
 */
public class NRVHardwareInterface implements BiasgenHardwareInterface, AEMonitorInterface, ReaderBufferControl, USBInterface {

    public static final short VID = (short) 0x04B4;
    public static final short PID_FX20 = (short) 0x00F0;
    /** CX3 bridge. Engineering-sample DELTA01 often appears in Device Manager as FX3. */
    public static final short PID_CX3 = (short) 0x00F1;

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final int AE_BUFFER_SIZE = 2_097_152;
    private static final int MAX_AE_BUFFER_SIZE = 10_000_000;
    private static final int DEFAULT_USB_FIFO_SIZE = 524288;
    private static final int DEFAULT_USB_NUM_BUFFERS = 16;
    /** After hotplug, Linux can list the CX3 before config 1 / iface 0 exist. */
    private static final long CLAIM_RETRY_MS = 2000L;
    private static final PropertyChangeEvent NEW_EVENTS_PROPERTY_CHANGE =
            new PropertyChangeEvent(NRVHardwareInterface.class, "NewEvents", null, null);

    private static final Preferences PREFS = JaerConstants.PREFS_ROOT_HARDWARE.node("NRV");
    /** Pref kill-switch for USB→PacketBundle polarity demux. */
    public static final String PREF_USB_TYPED_DEMUX = "usbTypedDemux";

    static {
        VendorPrefsMigration.migrateHardwarePrefs(VendorPrefsMigration.LEGACY_NRV_HW_PACKAGE, PREFS);
        UsbReaderBufferSettings.migrateLegacyRootKey(
                JaerConstants.PREFS_ROOT_HARDWARE, "NRV.AEReader.fifoSize", PREFS, UsbReaderBufferSettings.PREF_KEY_FIFO_SIZE);
        UsbReaderBufferSettings.migrateLegacyRootKey(
                JaerConstants.PREFS_ROOT_HARDWARE, "NRV.AEReader.numBuffers", PREFS, UsbReaderBufferSettings.PREF_KEY_NUM_BUFFERS);
    }

    private final Preferences prefs = PREFS;
    private final Device device;
    private DeviceHandle deviceHandle;
    private DeviceDescriptor deviceDescriptor;
    private AEChip chip;
    private NRVI2CTransport i2cTransport;
    private NRVAEReader aeReader;
    private int buffersize = loadAeBufferSizePref();
    private int usbFifoSize = UsbReaderBufferSettings.loadFifoSize(
            PREFS, UsbReaderBufferSettings.PREF_KEY_FIFO_SIZE, DEFAULT_USB_FIFO_SIZE, log, "NRV");
    private int usbNumBuffers = UsbReaderBufferSettings.loadNumBuffers(
            PREFS, UsbReaderBufferSettings.PREF_KEY_NUM_BUFFERS, DEFAULT_USB_NUM_BUFFERS, usbFifoSize, log, "NRV");
    private final AEPacketRawPool aePacketRawPool = new AEPacketRawPool(this);
    private final PacketBundlePool packetBundlePool = new PacketBundlePool();
    private PacketBundle lastPacketBundle = new PacketBundle();
    private volatile boolean usbTypedDemuxActive = PREFS.getBoolean(PREF_USB_TYPED_DEMUX, true);
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
        log.fine("NRV USB typed demux=" + usbTypedDemuxActive + " (pref " + PREF_USB_TYPED_DEMUX + ")");
    }

    boolean isUsbTypedDemuxActive() {
        return usbTypedDemuxActive;
    }

    PacketBundlePool getPacketBundlePool() {
        return packetBundlePool;
    }

    private int loadAeBufferSizePref() {
        final int saved = prefs.getInt("NRV.aeBufferSize", AE_BUFFER_SIZE);
        if (saved == 100_000 || saved == 500_000) {
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
            throw new HardwareInterfaceException("open(): " + LibUsb.errorName(status) + libUsbOpenHint(status));
        }

        try {
            deviceDescriptor = new DeviceDescriptor();
            status = LibUsb.getDeviceDescriptor(device, deviceDescriptor);
            if (status != LibUsb.SUCCESS) {
                throw new HardwareInterfaceException("getDeviceDescriptor(): " + LibUsb.errorName(status));
            }

            acquireDevice();
            selectI2CTransport(deviceDescriptor.idProduct());

            // Never issue USB string-descriptor control transfers during open.
            // On Windows WinUSB, LibUsb.getStringDescriptor has no timeout and
            // can hang jaer-aemon-open (same class as CypressFX3 / Prophesee).
            stringDescriptors[0] = "NRV";
            stringDescriptors[1] = getTypeName();
            if (device != null) {
                stringDescriptors[2] = String.format("bus%d-addr%d",
                        LibUsb.getBusNumber(device), LibUsb.getDeviceAddress(device));
            }
            log.info("NRV device opened VID:PID="
                    + String.format("%04x:%04x", deviceDescriptor.idVendor(), deviceDescriptor.idProduct())
                    + " (skipping USB string descriptors)");

            usbTransferFailed = false;
            isOpened = true;
            LibUsbLinkInfo.logOnOpen(log, "NRV", device, deviceDescriptor);
        } catch (HardwareInterfaceException | RuntimeException e) {
            closePartialOpen();
            throw e;
        }
    }

    /** Drop a libusb handle from a failed {@link #open()} so the next open is not NOT_FOUND. */
    private void closePartialOpen() {
        final DeviceHandle handle = deviceHandle;
        deviceHandle = null;
        deviceDescriptor = null;
        i2cTransport = null;
        isOpened = false;
        if (handle == null) {
            return;
        }
        try {
            LibUsb.close(handle);
        } catch (Exception e) {
            log.fine("NRV close after failed open: " + e.getMessage());
        }
    }

    /**
     * Hint after {@link LibUsb#open} failure. WinUSB/Zadig is Windows-only;
     * ACCESS/BUSY often means another process holds the device.
     */
    static String libUsbOpenHint(int status) {
        if (status != LibUsb.ERROR_ACCESS && status != LibUsb.ERROR_NOT_SUPPORTED
                && status != LibUsb.ERROR_BUSY) {
            return "";
        }
        final String os = System.getProperty("os.name", "");
        if (os.startsWith("Windows")) {
            return " Install WinUSB via Zadig (not libusb-win32) for VID 04B4 PID 00F0/00F1.";
        }
        if (os.contains("Linux")) {
            return " Close other jAER/SDK instances, or check udev permissions for 04b4:00f0/00f1.";
        }
        return " Close other processes using the camera.";
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
        // Do not hold this monitor across close(): AEReader stop + LibUsb.close
        // join would block synchronized open() / ViewLoop for the whole teardown.
        new Thread(this::close, "NRV-USB-disconnect").start();
    }

    void recoverFailedBufferReconfig(Exception cause) {
        log.warning("NRV USB reader session failed (" + cause + "); closing device instead of overlapping transfers");
        if (!isOpen()) {
            return;
        }
        markUsbDisconnected(LibUsb.ERROR_IO);
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
        final int autoDetach = LibUsb.setAutoDetachKernelDriver(deviceHandle, true);
        if (autoDetach != LibUsb.SUCCESS && autoDetach != LibUsb.ERROR_NOT_SUPPORTED) {
            log.fine("setAutoDetachKernelDriver: " + LibUsb.errorName(autoDetach));
        }
        if (LibUsb.kernelDriverActive(deviceHandle, 0) == 1) {
            final int detach = LibUsb.detachKernelDriver(deviceHandle, 0);
            if (detach != LibUsb.SUCCESS && detach != LibUsb.ERROR_NOT_SUPPORTED) {
                log.warning("detachKernelDriver: " + LibUsb.errorName(detach));
            }
        }
        ensureUsbConfiguration();
        int status = LibUsb.ERROR_OTHER;
        int attempt = 0;
        final long deadline = System.currentTimeMillis() + CLAIM_RETRY_MS;
        while (System.currentTimeMillis() < deadline) {
            status = LibUsb.claimInterface(deviceHandle, 0);
            if (status == LibUsb.SUCCESS) {
                if (attempt > 0) {
                    log.info("NRV claimInterface succeeded after " + attempt + " retries");
                }
                return;
            }
            if (status != LibUsb.ERROR_NOT_FOUND && status != LibUsb.ERROR_BUSY
                    && status != LibUsb.ERROR_NO_DEVICE) {
                break;
            }
            attempt++;
            log.fine("NRV claimInterface " + LibUsb.errorName(status) + " attempt " + attempt
                    + "; setting config 1 and retrying");
            ensureUsbConfiguration();
            try {
                Thread.sleep(50);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        String hint = (status == LibUsb.ERROR_ACCESS || status == LibUsb.ERROR_BUSY)
                ? " Another process may hold the CX3/FX20, or the driver is not WinUSB."
                : (status == LibUsb.ERROR_NOT_FOUND
                        ? " Device may still be enumerating after hotplug; try Interface → Refresh."
                        : "");
        throw new HardwareInterfaceException("claimInterface(): " + LibUsb.errorName(status) + hint);
    }

    /** CX3 after hotplug is often still config 0; claim iface 0 then returns NOT_FOUND. */
    private void ensureUsbConfiguration() {
        final IntBuffer activeConfig = BufferUtils.allocateIntBuffer();
        try {
            final int rc = LibUsb.getConfiguration(deviceHandle, activeConfig);
            if (rc == LibUsb.SUCCESS && activeConfig.get() == 1) {
                return;
            }
            if (rc != LibUsb.SUCCESS) {
                log.fine("NRV getConfiguration: " + LibUsb.errorName(rc));
            }
        } catch (Exception e) {
            log.fine("NRV getConfiguration: " + e.getMessage());
        }
        final int set = LibUsb.setConfiguration(deviceHandle, 1);
        if (set != LibUsb.SUCCESS) {
            log.fine("NRV setConfiguration(1): " + LibUsb.errorName(set));
        }
    }

    private void releaseDevice() throws HardwareInterfaceException {
        final int status = LibUsb.releaseInterface(deviceHandle, 0);
        if (status != LibUsb.SUCCESS) {
            throw new HardwareInterfaceException("releaseInterface(): " + LibUsb.errorName(status));
        }
    }

    private static final long LIBUSB_CLOSE_TIMEOUT_MS = 2000L;

    @Override
    public void close() {
        final NRVAEReader currentReader = aeReader;
        if (currentReader != null && currentReader.isTransferThread()) {
            log.warning("NRV close() from AEReader; deferring off that thread so join can succeed");
            UsbAsyncBulkReaderLifecycle.closeHostOffReaderThread(this::close);
            return;
        }
        final NRVAEReader reader;
        final DeviceHandle handle;
        boolean readerDead = true;
        synchronized (this) {
            if (!isOpen()) {
                return;
            }
            // Mark closed first so ViewLoop / acquire stop using this interface
            // even if USB teardown blocks in native code.
            isOpened = false;
            eventAcquisitionEnabled = false;
            reader = aeReader;
            aeReader = null;
            handle = deviceHandle;
            deviceHandle = null;
            deviceDescriptor = null;
            i2cTransport = null;
            settingsApplied = false;
            usbTransferFailed = false;
            aePacketRawPool.reset();
        }
        if (reader != null) {
            try {
                readerDead = reader.stopThread();
            } catch (Exception e) {
                log.warning("Error stopping NRV AEReader on close: " + e.getMessage());
                readerDead = false;
            }
        }
        if (handle == null) {
            return;
        }
        if (UsbAsyncBulkReaderLifecycle.abandonNativeHandle(readerDead, log, "NRV")) {
            return;
        }
        // releaseInterface / LibUsb.close can hang forever on Windows WinUSB; bound it.
        Thread usbClose = new Thread(() -> {
            try {
                final int status = LibUsb.releaseInterface(handle, 0);
                if (status != LibUsb.SUCCESS) {
                    log.warning("releaseInterface on close: " + LibUsb.errorName(status));
                }
            } catch (Exception e) {
                log.warning("Error releasing NRV interface on close: " + e.getMessage());
            }
            try {
                LibUsb.close(handle);
            } catch (Exception e) {
                log.warning("Error in LibUsb.close: " + e.getMessage());
            }
        }, "NRV-LibUsb-close");
        usbClose.setDaemon(true);
        usbClose.start();
        try {
            usbClose.join(LIBUSB_CLOSE_TIMEOUT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (usbClose.isAlive()) {
            log.warning("NRV LibUsb.close/releaseInterface timed out after "
                    + LIBUSB_CLOSE_TIMEOUT_MS + " ms; abandoning daemon teardown thread");
        }
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

    /** Libusb device pointer for identity compares; does not open the handle. */
    public Device getLibUsbDevice() {
        return device;
    }

    @Override
    public String toString() {
        if (isOpened && stringDescriptors != null && stringDescriptors[1] != null
                && !stringDescriptors[1].isBlank()) {
            String sn = (stringDescriptors[2] != null && !stringDescriptors[2].isBlank())
                    ? " " + stringDescriptors[2] : "";
            return stringDescriptors[1] + sn;
        }
        return UsbIds.unopenedLabel(this, getTypeName());
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
                packetBundlePool.swap();
                lastPacketBundle = packetBundlePool.readBuffer();
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
            packetBundlePool.swap();
            lastPacketBundle = packetBundlePool.readBuffer();
            eventCounter = 0;
            lastEventsAcquired = aePacketRawPool.readBuffer();
        }
        if (aeReader != null) {
            aeReader.onWriteBufferConsumed();
        }
        final int nEvents = usbTypedDemuxActive
                ? lastPacketBundle.getNumPolarityEvents()
                : lastEventsAcquired.getNumEvents();
        if (usbTypedDemuxActive) {
            computeEstimatedEventRate(lastPacketBundle);
        } else {
            computeEstimatedEventRate(lastEventsAcquired);
        }
        if (nEvents != 0 || lastEventsAcquired.overrunOccuredFlag) {
            support.firePropertyChange(NEW_EVENTS_PROPERTY_CHANGE);
        }
        return lastEventsAcquired;
    }

    @Override
    public PacketBundle acquireAvailablePacketBundle() throws HardwareInterfaceException {
        if (!usbTypedDemuxActive) {
            return null;
        }
        acquireAvailableEventsFromDriver();
        return lastPacketBundle;
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
                log.warning("NRV: register settings not applied — load deviceSettings/NRV/S5KRC1S_300_CX3.txt "
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

    private void computeEstimatedEventRate(PacketBundle bundle) {
        if (bundle == null) {
            estimatedEventRate = 0;
            return;
        }
        final EventPacket<?> polarity = bundle.getFirstPolarityPacket();
        if (polarity == null || polarity.getSize() < 2) {
            estimatedEventRate = 0;
            return;
        }
        final int n = polarity.getSize();
        final int t0 = polarity.getEvent(0).timestamp;
        final int t1 = polarity.getEvent(n - 1).timestamp;
        final int dt = t1 - t0;
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
                        + "(Biases > File > Load settings, or deviceSettings/NRV/S5KRC1S_300_CX3.txt)");
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

    /**
     * Populate device descriptor from libusb without claiming the interface.
     */
    public void ensureUsbDeviceDescriptor() {
        if (deviceDescriptor != null || device == null) {
            return;
        }
        deviceDescriptor = new DeviceDescriptor();
        int status = LibUsb.getDeviceDescriptor(device, deviceDescriptor);
        if (status != LibUsb.SUCCESS) {
            log.warning("Could not read NRV USB device descriptor: " + LibUsb.errorName(status));
            deviceDescriptor = null;
        }
    }

    @Override
    public short getVID_THESYCON_FX2_CPLD() {
        ensureUsbDeviceDescriptor();
        return deviceDescriptor == null ? VID : deviceDescriptor.idVendor();
    }

    @Override
    public short getPID() {
        ensureUsbDeviceDescriptor();
        return deviceDescriptor == null ? 0 : deviceDescriptor.idProduct();
    }

    @Override
    public short getDID() {
        ensureUsbDeviceDescriptor();
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
        return usbFifoSize;
    }

    @Override
    public void setFifoSize(int fifoSize) {
        this.usbFifoSize = UsbReaderBufferSettings.applyFifoSize(
                prefs, UsbReaderBufferSettings.PREF_KEY_FIFO_SIZE, fifoSize, log, "NRV");
        this.usbNumBuffers = UsbReaderBufferSettings.applyNumBuffers(
                prefs, UsbReaderBufferSettings.PREF_KEY_NUM_BUFFERS, usbNumBuffers, this.usbFifoSize, log, "NRV");
        if (aeReader != null) {
            aeReader.applyBufferSettingsAndRestart(usbFifoSize, usbNumBuffers);
        }
    }

    @Override
    public int getNumBuffers() {
        return usbNumBuffers;
    }

    @Override
    public void setNumBuffers(int numBuffers) {
        this.usbNumBuffers = UsbReaderBufferSettings.applyNumBuffers(
                prefs, UsbReaderBufferSettings.PREF_KEY_NUM_BUFFERS, numBuffers, usbFifoSize, log, "NRV");
        if (aeReader != null) {
            aeReader.applyBufferSettingsAndRestart(usbFifoSize, usbNumBuffers);
        }
    }

    @Override
    public boolean isUsbBufferReconfigPending() {
        return aeReader != null && aeReader.isBufferReconfigPending();
    }

    @Override
    public int getActiveFifoSize() {
        return aeReader != null ? aeReader.getActiveFifoSize() : usbFifoSize;
    }

    @Override
    public int getActiveNumBuffers() {
        return aeReader != null ? aeReader.getActiveNumBuffers() : usbNumBuffers;
    }

    @Override
    public UsbAsyncBulkReaderLifecycle.Status getUsbBufferConfigStatus() {
        return aeReader != null ? aeReader.getBufferConfigStatus() : null;
    }

    @Override
    public PropertyChangeSupport getReaderSupport() {
        return support;
    }
}
