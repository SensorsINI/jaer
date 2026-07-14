package prophesee.usb;

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
import prophesee.chip.PropheseeConfig;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.JaerConstants;
import net.sf.jaer.util.VendorPrefsMigration;
import net.sf.jaer.util.TimestampSpread;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.ReaderBufferControl;
import net.sf.jaer.hardwareinterface.usb.USBInterface;
import prophesee.usb.evt3.Evt3Parser;
import prophesee.usb.evk4.Imx636Init;

/**
 * LibUsb driver for Prophesee EVK4 HD (Sony IMX636, VID 0x04B4 PID 0x00F5).
 *
 * @see https://www.prophesee.ai/
 */
public class PropheseeHardwareInterface implements BiasgenHardwareInterface, AEMonitorInterface,
        ReaderBufferControl, USBInterface {

    public static final short VID = (short) 0x04B4;
    public static final short PID_EVK4_HD = (short) 0x00F5;

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final int AE_BUFFER_SIZE = 500_000;
    private static final int MAX_AE_BUFFER_SIZE = 10_000_000;
    private static final PropertyChangeEvent NEW_EVENTS_PROPERTY_CHANGE =
            new PropertyChangeEvent(PropheseeHardwareInterface.class, "NewEvents", null, null);

    private static final Preferences PREFS = JaerConstants.PREFS_ROOT_HARDWARE.node("Prophesee");

    static {
        VendorPrefsMigration.migrateHardwarePrefs(VendorPrefsMigration.LEGACY_PROPHESEE_HW_PACKAGE, PREFS);
    }

    private final Preferences prefs = PREFS;
    private final Device device;
    private DeviceHandle deviceHandle;
    private DeviceDescriptor deviceDescriptor;
    private AEChip chip;
    private volatile boolean closing;
    private PropheseeAEReader aeReader;
    private int buffersize = loadAeBufferSizePref();
    private final AEPacketRawPool aePacketRawPool = new AEPacketRawPool(this);
    private final PropertyChangeSupport support = new PropertyChangeSupport(this);

    private String serial = "";
    private PropheseeBiases biases = new PropheseeBiases();
    private PropheseeBiases chipFirmwareBiases = new PropheseeBiases();
    private boolean deviceInitialized;

    private boolean isOpened = false;
    private volatile boolean usbTransferFailed = false;
    private boolean eventAcquisitionEnabled = false;
    private int eventCounter = 0;
    private int estimatedEventRate = 0;
    private long lastPacketTimestampLogMs;
    private String[] stringDescriptors = new String[3];

    public PropheseeHardwareInterface(Device device) {
        this.device = device;
    }

    private int loadAeBufferSizePref() {
        final int saved = prefs.getInt("Prophesee.aeBufferSize", AE_BUFFER_SIZE);
        if (saved == 100_000) {
            prefs.putInt("Prophesee.aeBufferSize", AE_BUFFER_SIZE);
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

    private PropheseeAEReader ensureAeReader() {
        if (aeReader == null) {
            aeReader = new PropheseeAEReader(this);
        }
        return aeReader;
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

    void restartEventStreaming() throws HardwareInterfaceException {
        reinitializeStreaming(false);
    }

    void reinitializeStreaming(boolean includeHandshake) throws HardwareInterfaceException {
        PropheseeAEReader reader;
        final boolean wasAcquiring;
        synchronized (this) {
            if (!isOpen() || deviceHandle == null || closing) {
                return;
            }
            wasAcquiring = eventAcquisitionEnabled;
            reader = aeReader;
            if (reader != null) {
                reader.prepareForStop();
            }
        }
        if (reader != null) {
            reader.finishStop();
        }
        synchronized (this) {
            if (!isOpen() || deviceHandle == null || closing) {
                return;
            }
            if (includeHandshake) {
                final Imx636Init.InitResult result = Imx636Init.initializeAndStart(deviceHandle, biases);
                chipFirmwareBiases = result.chipBiases;
            } else {
                Imx636Init.restartStreaming(deviceHandle, biases);
                chipFirmwareBiases = Imx636Init.readDefaultBiases(deviceHandle);
            }
        }
        synchronized (this) {
            if (!isOpen() || closing || !wasAcquiring) {
                return;
            }
            if (aeReader == null) {
                aeReader = new PropheseeAEReader(this);
            }
            aeReader.startThread();
        }
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
            final String driverHint = (status == LibUsb.ERROR_ACCESS
                    || status == LibUsb.ERROR_NOT_SUPPORTED
                    || status == LibUsb.ERROR_BUSY)
                    ? " Install WinUSB for EVK4 (Prophesee wdi-simple or Zadig, VID 04B4 PID 00F5)."
                    : "";
            throw new HardwareInterfaceException("open(): " + LibUsb.errorName(status) + driverHint);
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
        log.fine("Prophesee open: USB interface claimed");

        try {
            for (int i = 0; i < stringDescriptors.length; i++) {
                stringDescriptors[i] = LibUsb.getStringDescriptor(deviceHandle, (byte) (i + 1));
            }
        } catch (Exception e) {
            log.warning("Could not read all USB string descriptors: " + e.getMessage());
        }

        log.fine("Prophesee open: running ISSD init pipeline");
        final Imx636Init.InitResult initResult = Imx636Init.initializeAndStart(deviceHandle, biases);
        serial = initResult.serial;
        chipFirmwareBiases = initResult.chipBiases;
        deviceInitialized = true;

        usbTransferFailed = false;
        closing = false;
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
        closing = true;
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
        final AEPacketRaw lastEventsAcquired;
        synchronized (aePacketRawPool) {
            aePacketRawPool.swap();
            eventCounter = 0;
            lastEventsAcquired = aePacketRawPool.readBuffer();
        }
        final int nEvents = lastEventsAcquired.getNumEvents();
        computeEstimatedEventRate(lastEventsAcquired);
        maybeLogPacketTimestampStats(lastEventsAcquired);
        if (nEvents != 0) {
            support.firePropertyChange(NEW_EVENTS_PROPERTY_CHANGE);
        } else if (lastEventsAcquired.overrunOccuredFlag) {
            // Buffer filled before swap; still notify so the viewer keeps polling.
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

    private void maybeLogPacketTimestampStats(AEPacketRaw packet) {
        if (!PropheseeTrace.TIMESTAMP_ENABLED || packet == null || packet.getNumEvents() < 2) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (now - lastPacketTimestampLogMs < 2000L) {
            return;
        }
        lastPacketTimestampLogMs = now;
        final int[] ts = packet.getTimestamps();
        final int n = packet.getNumEvents();
        final TimestampSpread spread = TimestampSpread.compute(ts, 0, n);
        long parserTUs = -1;
        long parserOrigin = -1;
        int parserOverflows = 0;
        if (aeReader != null) {
            final Evt3Parser parser = aeReader.getParser();
            parserTUs = parser.getTUs();
            parserOrigin = parser.getTimestampOriginUs();
            parserOverflows = parser.getOverflows();
        }
        PropheseeTrace.fine(log,
                "Prophesee packet ts: events={0} span={1}us unique={2} first={3} last={4} "
                        + "parser tUs={5} origin={6} overflows={7}",
                n, spread.spanUs, spread.uniqueTs, ts[0], ts[n - 1],
                parserTUs, parserOrigin, parserOverflows);
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
            log.info("Prophesee resetTimestamps(): zeroing jAER time at current EVT3 time");
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
            log.fine("Prophesee open: starting event reader thread");
            aeReader.startThread();
        } else if (aeReader != null) {
            aeReader.prepareForStop();
            aeReader.finishStop();
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

    @Override
    public String toString() {
        if (deviceDescriptor != null) {
            return String.format("Prophesee EVK4 HD %04x:%04x%s",
                    deviceDescriptor.idVendor(), deviceDescriptor.idProduct(),
                    serial.isEmpty() ? "" : " serial=" + serial);
        }
        if (!serial.isEmpty()) {
            return "Prophesee EVK4 HD serial=" + serial;
        }
        return "Prophesee EVK4 HD";
    }
}
