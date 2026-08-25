package prophesee.usb;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeSupport;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

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
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PacketBundlePool;
import net.sf.jaer.util.VendorPrefsMigration;
import net.sf.jaer.util.TimestampSpread;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.HasLiveDisplayEventCap;
import net.sf.jaer.hardwareinterface.usb.LibUsbLinkInfo;
import net.sf.jaer.hardwareinterface.usb.ReaderBufferControl;
import net.sf.jaer.hardwareinterface.usb.USBInterface;
import net.sf.jaer.hardwareinterface.usb.UsbLog;
import net.sf.jaer.hardwareinterface.usb.UsbAsyncBulkReaderLifecycle;
import net.sf.jaer.hardwareinterface.usb.UsbReaderBufferSettings;
import prophesee.usb.evt3.Evt3Parser;
import prophesee.usb.evk4.Imx636Init;

/**
 * LibUsb driver for Prophesee EVK4 HD (Sony IMX636, VID 0x04B4 PID 0x00F5).
 *
 * @see https://www.prophesee.ai/
 */
public class PropheseeHardwareInterface implements BiasgenHardwareInterface, AEMonitorInterface,
        ReaderBufferControl, HasLiveDisplayEventCap, USBInterface {

    public static final short VID = (short) 0x04B4;
    public static final short PID_EVK4_HD = (short) 0x00F5;

    private static final Logger log = Logger.getLogger("net.sf.jaer");
    private static final int AE_BUFFER_SIZE = 2_097_152;
    private static final int MAX_AE_BUFFER_SIZE = 10_000_000;
    private static final int DEFAULT_USB_FIFO_SIZE = 2 * 1024 * 1024;
    private static final int DEFAULT_USB_NUM_BUFFERS = 4;
    private static final int DEFAULT_LIVE_DISPLAY_EVENT_CAP = 2_097_152;
    /** Bump when default fifo/buffer tuning changes; migrates stored hardware prefs once. */
    private static final int USB_READER_PREFS_VERSION = 1;
    private static final PropertyChangeEvent NEW_EVENTS_PROPERTY_CHANGE =
            new PropertyChangeEvent(PropheseeHardwareInterface.class, "NewEvents", null, null);

    private static final Preferences PREFS = JaerConstants.PREFS_ROOT_HARDWARE.node("Prophesee");
    /** Pref kill-switch for USB→PacketBundle polarity demux. */
    public static final String PREF_USB_TYPED_DEMUX = "usbTypedDemux";
    public static final String PREF_LIVE_DISPLAY_EVENT_CAP = "liveDisplayEventCap";

    /** Shown at most once per JVM; ViewLoop retries open every few hundred ms. */
    private static final AtomicBoolean LINUX_UDEV_DIALOG_SHOWN = new AtomicBoolean();
    static final String LINUX_UDEV_RULES_FILE = "/etc/udev/rules.d/99-prophesee-evk4.rules";
    static final String LINUX_UDEV_RULE =
            "SUBSYSTEM==\"usb\", ATTR{idVendor}==\"04b4\", ATTR{idProduct}==\"00f5\", MODE=\"0666\"";

    static {
        VendorPrefsMigration.migrateHardwarePrefs(VendorPrefsMigration.LEGACY_PROPHESEE_HW_PACKAGE, PREFS);
        UsbReaderBufferSettings.migrateLegacyRootKey(
                JaerConstants.PREFS_ROOT_HARDWARE, "Prophesee.AEReader.fifoSize", PREFS, UsbReaderBufferSettings.PREF_KEY_FIFO_SIZE);
        UsbReaderBufferSettings.migrateLegacyRootKey(
                JaerConstants.PREFS_ROOT_HARDWARE, "Prophesee.AEReader.numBuffers", PREFS, UsbReaderBufferSettings.PREF_KEY_NUM_BUFFERS);
        UsbReaderBufferSettings.migrateLegacyRootKey(
                JaerConstants.PREFS_ROOT_HARDWARE, "Prophesee.AEReader.prefsVersion", PREFS, "AEReader.prefsVersion");
        migrateUsbReaderPrefs();
    }

    private static void migrateUsbReaderPrefs() {
        if (PREFS.getInt("AEReader.prefsVersion", 0) >= USB_READER_PREFS_VERSION) {
            UsbReaderBufferSettings.loadFifoSize(PREFS, UsbReaderBufferSettings.PREF_KEY_FIFO_SIZE,
                    DEFAULT_USB_FIFO_SIZE, log, "Prophesee");
            UsbReaderBufferSettings.loadNumBuffers(PREFS, UsbReaderBufferSettings.PREF_KEY_NUM_BUFFERS,
                    DEFAULT_USB_NUM_BUFFERS,
                    PREFS.getInt(UsbReaderBufferSettings.PREF_KEY_FIFO_SIZE, DEFAULT_USB_FIFO_SIZE), log, "Prophesee");
            return;
        }
        PREFS.putInt(UsbReaderBufferSettings.PREF_KEY_FIFO_SIZE, DEFAULT_USB_FIFO_SIZE);
        PREFS.putInt(UsbReaderBufferSettings.PREF_KEY_NUM_BUFFERS, DEFAULT_USB_NUM_BUFFERS);
        PREFS.putInt("AEReader.prefsVersion", USB_READER_PREFS_VERSION);
    }

    private final Preferences prefs = PREFS;
    private final Device device;
    private DeviceHandle deviceHandle;
    private DeviceDescriptor deviceDescriptor;
    private AEChip chip;
    private volatile boolean closing;
    private PropheseeAEReader aeReader;
    private int buffersize = loadAeBufferSizePref();
    private volatile int liveDisplayEventCap = loadLiveDisplayEventCapPref();
    private int usbFifoSize = UsbReaderBufferSettings.loadFifoSize(
            PREFS, UsbReaderBufferSettings.PREF_KEY_FIFO_SIZE, DEFAULT_USB_FIFO_SIZE, log, "Prophesee");
    private int usbNumBuffers = UsbReaderBufferSettings.loadNumBuffers(
            PREFS, UsbReaderBufferSettings.PREF_KEY_NUM_BUFFERS, DEFAULT_USB_NUM_BUFFERS, usbFifoSize, log, "Prophesee");
    private final AEPacketRawPool aePacketRawPool = new AEPacketRawPool(this);
    private final PacketBundlePool packetBundlePool = new PacketBundlePool();
    private PacketBundle lastPacketBundle = new PacketBundle();
    private volatile boolean usbTypedDemuxActive = PREFS.getBoolean(PREF_USB_TYPED_DEMUX, true);
    private final PropertyChangeSupport support = new PropertyChangeSupport(this);

    private String serial = "";
    private PropheseeBiases biases = new PropheseeBiases();
    private PropheseeBiases chipFirmwareBiases = new PropheseeBiases();
    private boolean deviceInitialized;

    private boolean isOpened = false;
    /** True while {@link #open()} is between LibUsb.open and isOpened=true. */
    private volatile boolean openInProgress = false;
    /** Set by Interface switch so a stuck/slow open can bail out between steps. */
    private volatile boolean openAbortRequested = false;
    private volatile boolean usbTransferFailed = false;
    private boolean eventAcquisitionEnabled = false;
    /** True after {@link Imx636Init#startStreaming}; USB URBs must be queued before this. */
    private volatile boolean sensorStreaming = false;
    private int eventCounter = 0;
    private int estimatedEventRate = 0;
    private long lastPacketTimestampLogMs;
    private String[] stringDescriptors = new String[3];

    public PropheseeHardwareInterface(Device device) {
        this.device = device;
        log.fine("Prophesee USB typed demux=" + usbTypedDemuxActive + " (pref " + PREF_USB_TYPED_DEMUX + ")");
    }

    /**
     * Ask an in-progress {@link #open()} to abort at the next checkpoint (Interface switch).
     * Safe from any thread; does not block on the open monitor.
     */
    public void requestOpenAbort() {
        openAbortRequested = true;
        log.fine("Prophesee requestOpenAbort " + UsbLog.t());
    }

    public boolean isOpenInProgress() {
        return openInProgress;
    }

    private void checkOpenAbort() throws HardwareInterfaceException {
        if (openAbortRequested || Thread.currentThread().isInterrupted()) {
            throw new HardwareInterfaceException("Prophesee open aborted (interface switched)");
        }
    }

    private void releasePartialOpen() {
        log.fine("Prophesee releasePartialOpen " + UsbLog.t());
        try {
            if (deviceHandle != null) {
                try {
                    Imx636Init.shutdown(deviceHandle);
                } catch (Exception e) {
                    log.fine("shutdown during abort: " + e.getMessage());
                }
                try {
                    releaseDevice();
                } catch (Exception e) {
                    log.fine("release during abort: " + e.getMessage());
                }
                try {
                    LibUsb.close(deviceHandle);
                } catch (Exception e) {
                    log.fine("LibUsb.close during abort: " + e.getMessage());
                }
                deviceHandle = null;
            }
        } finally {
            deviceInitialized = false;
            sensorStreaming = false;
            isOpened = false;
            openInProgress = false;
        }
    }

    boolean isUsbTypedDemuxActive() {
        return usbTypedDemuxActive;
    }

    PacketBundlePool getPacketBundlePool() {
        return packetBundlePool;
    }

    private int loadAeBufferSizePref() {
        final int saved = prefs.getInt("Prophesee.aeBufferSize", AE_BUFFER_SIZE);
        if (saved == 100_000) {
            prefs.putInt("Prophesee.aeBufferSize", AE_BUFFER_SIZE);
            return AE_BUFFER_SIZE;
        }
        return saved;
    }

    private int loadLiveDisplayEventCapPref() {
        final int saved = prefs.getInt(PREF_LIVE_DISPLAY_EVENT_CAP, DEFAULT_LIVE_DISPLAY_EVENT_CAP);
        return clampLiveDisplayEventCap(saved);
    }

    private int clampLiveDisplayEventCap(int events) {
        return Math.max(getMinLiveDisplayEventCap(), Math.min(getMaxLiveDisplayEventCap(), events));
    }

    @Override
    public int getLiveDisplayEventCap() {
        return liveDisplayEventCap;
    }

    @Override
    public void setLiveDisplayEventCap(int events) {
        final int clamped = clampLiveDisplayEventCap(events);
        if (clamped == liveDisplayEventCap) {
            return;
        }
        liveDisplayEventCap = clamped;
        prefs.putInt(PREF_LIVE_DISPLAY_EVENT_CAP, liveDisplayEventCap);
        log.info("Prophesee live display keep limit set to " + liveDisplayEventCap + " events/frame"
                + " (USB thread will grow polarity capacity on next transfer)");
        // Do not allocate hundreds of thousands of PolarityEvents on the EDT — that races with
        // the USB reader and freezes the UI. The reader calls ensureCapacity before the next fill.
        support.firePropertyChange("liveDisplayEventCap", null, liveDisplayEventCap);
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
        final PropheseeBiases previous = this.biases;
        this.biases = biases.copy();
        if (isOpen() && deviceInitialized && !closing) {
            Imx636Init.applyChangedBiases(deviceHandle, previous, this.biases);
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

    void recoverFailedBufferReconfig(Exception cause) {
        log.warning("Prophesee USB reader session failed (" + cause + "); closing device instead of overlapping transfers");
        if (closing) {
            return;
        }
        // A reader that would not stop still owns endpoint 0x81, and closing the handle under it
        // leaves the device unusable until replug. Stopping the sensor lets its transfers drain, so
        // give the join one more chance before the device is closed.
        stopSensorStreaming();
        final PropheseeAEReader reader = aeReader;
        if (reader != null) {
            reader.prepareForStop();
            if (!reader.finishStop()) {
                log.warning("Prophesee AEReader still alive after stopping the sensor; USB may need a replug");
            }
        }
        markUsbDisconnected(LibUsb.ERROR_IO);
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
            stopSensorStreaming();
            if (reader != null) {
                reader.prepareForStop();
            }
        }
        if (reader != null) {
            if (!reader.finishStop()) {
                recoverFailedBufferReconfig(new HardwareInterfaceException(
                        "Prophesee AEReader did not stop before streaming reinit"));
                return;
            }
        }
        synchronized (this) {
            if (!isOpen() || deviceHandle == null || closing) {
                return;
            }
            if (includeHandshake) {
                final Imx636Init.InitResult result = Imx636Init.initialize(deviceHandle, biases);
                chipFirmwareBiases = result.chipBiases;
                sensorStreaming = false;
            } else {
                Imx636Init.stopStreaming(deviceHandle);
                sensorStreaming = false;
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
            startSensorStreaming();
        }
    }

    @Override
    public void open() throws HardwareInterfaceException {
        synchronized (this) {
            if (isOpened) {
                return;
            }
            if (openInProgress) {
                throw new HardwareInterfaceException("Prophesee open already in progress");
            }
            openAbortRequested = false;
            openInProgress = true;
        }
        final long t0 = System.currentTimeMillis();
        try {
            log.info("Prophesee EVK4 open: LibUsb.open + claim (ISSD init follows, often several seconds)");
            log.fine("Prophesee open() begin " + UsbLog.t());
            deviceHandle = new DeviceHandle();
            int status = LibUsb.open(device, deviceHandle);
            log.fine("Prophesee LibUsb.open status=" + status + " " + UsbLog.t());
            if (status != LibUsb.SUCCESS) {
                deviceHandle = null;
                throw new HardwareInterfaceException("open(): " + LibUsb.errorName(status) + libUsbOpenHint(status));
            }

            deviceDescriptor = new DeviceDescriptor();
            status = LibUsb.getDeviceDescriptor(device, deviceDescriptor);
            if (status != LibUsb.SUCCESS) {
                throw new HardwareInterfaceException("getDeviceDescriptor(): " + LibUsb.errorName(status));
            }

            if (deviceDescriptor.idProduct() != PID_EVK4_HD) {
                throw new HardwareInterfaceException("Unsupported Prophesee PID: "
                        + String.format("%04x", deviceDescriptor.idProduct()));
            }

            checkOpenAbort();
            acquireDevice();
            log.info(String.format("Prophesee open: USB claimed after %d ms", System.currentTimeMillis() - t0));
            log.fine("Prophesee claim done " + UsbLog.t());

            // Do NOT call LibUsb.getStringDescriptor here: on Windows it can hang with no
            // timeout (jAER log 10:49:00 — claimed, never reached ISSD; ViewLoop stuck, next
            // Interface selection only showed "Switch interface done"). Serial comes from ISSD.
            stringDescriptors[0] = "Prophesee";
            stringDescriptors[1] = "EVK4 HD";
            stringDescriptors[2] = null;

            checkOpenAbort();
            log.info("Prophesee open: ISSD init (includes ~2.5s sleep)");
            log.fine("Prophesee Imx636Init.initialize() enter " + UsbLog.t());
            final Imx636Init.InitResult initResult = Imx636Init.initialize(deviceHandle, biases);
            log.fine("Prophesee Imx636Init.initialize() returned " + UsbLog.t());
            checkOpenAbort();
            serial = initResult.serial;
            chipFirmwareBiases = initResult.chipBiases;
            stringDescriptors[2] = serial;
            deviceInitialized = true;
            sensorStreaming = false;

            usbTransferFailed = false;
            closing = false;
            synchronized (this) {
                if (openAbortRequested) {
                    throw new HardwareInterfaceException("Prophesee open aborted (interface switched)");
                }
                isOpened = true;
            }
            log.info("Prophesee EVK4 opened serial=" + serial + " VID:PID="
                    + String.format("%04x:%04x", deviceDescriptor.idVendor(), deviceDescriptor.idProduct())
                    + " in " + (System.currentTimeMillis() - t0) + " ms");
            LibUsbLinkInfo.logOnOpen(log, "Prophesee EVK4", device, deviceDescriptor);
        } catch (HardwareInterfaceException | RuntimeException e) {
            releasePartialOpen();
            throw (e instanceof HardwareInterfaceException)
                    ? (HardwareInterfaceException) e
                    : new HardwareInterfaceException("Prophesee open failed: " + e, e);
        } finally {
            openInProgress = false;
        }
    }

    /**
     * Hint after {@link LibUsb#open} failure. WinUSB/Zadig is Windows-only;
     * Linux ACCESS is udev permissions or another process holding the device.
     */
    static String libUsbOpenHint(int status) {
        if (status != LibUsb.ERROR_ACCESS && status != LibUsb.ERROR_NOT_SUPPORTED
                && status != LibUsb.ERROR_BUSY) {
            return "";
        }
        final String os = System.getProperty("os.name", "");
        if (os.startsWith("Windows")) {
            return " Install WinUSB for EVK4 (Prophesee wdi-simple or Zadig, VID 04B4 PID 00F5).";
        }
        if (os.contains("Linux")) {
            if (status == LibUsb.ERROR_ACCESS) {
                return " Linux: udev rule for 04b4:00f5 (MODE=0666) then unplug/replug;"
                        + " or another process holds the device.";
            }
            return " Linux: close Metavision/flashy/caer/another jAER that holds the device.";
        }
        return " Close other processes using the camera (Metavision, another jAER).";
    }

    static String kernelDriverHint() {
        final String os = System.getProperty("os.name", "");
        if (os.startsWith("Windows")) {
            return "Install the Prophesee WinUSB driver (wdi-simple) or replace with WinUSB via Zadig.";
        }
        if (os.contains("Linux")) {
            return "Check lsusb -t / udev for 04b4:00f5, or close Metavision if it claimed the interface.";
        }
        return "A kernel driver may be bound; close other software using the camera.";
    }

    static String linuxUdevInstallCommands() {
        return "sudo tee " + LINUX_UDEV_RULES_FILE + " >/dev/null <<'EOF'\n"
                + LINUX_UDEV_RULE + "\n"
                + "EOF\n"
                + "sudo udevadm control --reload-rules\n"
                + "sudo udevadm trigger\n";
    }

    /**
     * Once per JVM, on Linux {@code LIBUSB_ERROR_ACCESS}, post a udev how-to dialog on the EDT.
     * Safe to call from ViewLoop.
     */
    public static void maybeShowLinuxUdevAccessDialog(final Component parent, final Throwable error) {
        if (error == null || error.getMessage() == null
                || !error.getMessage().contains("LIBUSB_ERROR_ACCESS")) {
            return;
        }
        if (!System.getProperty("os.name", "").contains("Linux")) {
            return;
        }
        if (!LINUX_UDEV_DIALOG_SHOWN.compareAndSet(false, true)) {
            return;
        }
        final Runnable r = () -> showLinuxUdevAccessDialog(parent);
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    private static void showLinuxUdevAccessDialog(final Component parent) {
        final String commands = linuxUdevInstallCommands();
        final JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        final JLabel intro = new JLabel("<html>"
                + "jAER cannot open the Prophesee EVK4 (USB <code>04b4:00f5</code>)."
                + " Linux denied libusb access (<code>LIBUSB_ERROR_ACCESS</code>).<br><br>"
                + "<b>1.</b> Copy the commands below.<br>"
                + "<b>2.</b> Paste them into a terminal and run them (enter your sudo password).<br>"
                + "<b>3.</b> Unplug the EVK4, plug it back in, then choose it again from the"
                + " Interface menu (or restart jAER).<br><br>"
                + "If it still fails, close Metavision, flashy, caer, or another jAER instance."
                + "</html>");
        intro.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(intro);
        panel.add(Box.createVerticalStrut(8));

        final JTextArea area = new JTextArea(commands);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setLineWrap(false);
        area.setTabSize(4);
        area.selectAll();
        final JScrollPane scroll = new JScrollPane(area);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.setPreferredSize(new Dimension(560, 110));
        panel.add(scroll);
        panel.add(Box.createVerticalStrut(8));

        final JButton copy = new JButton("Copy commands");
        copy.setAlignmentX(Component.LEFT_ALIGNMENT);
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(area.getText()), null);
            copy.setText("Copied");
        });
        panel.add(copy);

        final JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        wrap.add(panel, BorderLayout.CENTER);

        JOptionPane.showMessageDialog(parent, wrap,
                "Linux USB permission for Prophesee EVK4",
                JOptionPane.WARNING_MESSAGE);
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
    public void close() {
        openAbortRequested = true;
        synchronized (this) {
            if (!isOpened && !openInProgress) {
                return;
            }
            // Mid-open: open() checks abort and releasePartialOpen; still force-release here
            // if the opener is stuck in native USB without reaching a checkpoint.
            if (openInProgress && !isOpened) {
                log.info("Prophesee close during in-progress open; forcing USB release");
                releasePartialOpen();
                return;
            }
            closing = true;
            boolean readerDead = true;
            if (aeReader != null) {
                stopSensorStreaming();
                aeReader.prepareForStop();
                readerDead = aeReader.finishStop();
            }
            eventAcquisitionEnabled = false;
            sensorStreaming = false;
            if (UsbAsyncBulkReaderLifecycle.abandonNativeHandle(readerDead, log, "Prophesee")) {
                deviceHandle = null;
                deviceDescriptor = null;
                deviceInitialized = false;
                aePacketRawPool.reset();
                isOpened = false;
                openInProgress = false;
                return;
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
            openInProgress = false;
        }
    }

    @Override
    public boolean isOpen() {
        return isOpened;
    }

    @Override
    public String getTypeName() {
        return "Prophesee EVK4 HD";
    }

    /** Libusb device pointer for identity compares; does not open the handle. */
    public Device getLibUsbDevice() {
        return device;
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
            packetBundlePool.swap();
            lastPacketBundle = packetBundlePool.readBuffer();
            eventCounter = 0;
            lastEventsAcquired = aePacketRawPool.readBuffer();
        }
        if (usbTypedDemuxActive) {
            computeEstimatedEventRate(lastPacketBundle);
        } else {
            computeEstimatedEventRate(lastEventsAcquired);
        }
        maybeLogPacketTimestampStats(lastEventsAcquired);
        final int nEvents = usbTypedDemuxActive
                ? lastPacketBundle.getNumPolarityEvents()
                : lastEventsAcquired.getNumEvents();
        if (nEvents != 0) {
            support.firePropertyChange(NEW_EVENTS_PROPERTY_CHANGE);
        } else if (lastEventsAcquired.overrunOccuredFlag) {
            // Buffer filled before swap; still notify so the viewer keeps polling.
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
        final int dt = polarity.getEvent(n - 1).timestamp - polarity.getEvent(0).timestamp;
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
        if (aeReader != null) {
            aeReader.onAeBufferSizeChanged(size);
        }
    }

    @Override
    public void setEventAcquisitionEnabled(boolean enable) throws HardwareInterfaceException {
        if (enable) {
            if (closing || !isOpen()) {
                return;
            }
            if (aeReader == null) {
                aeReader = new PropheseeAEReader(this);
                synchronized (aePacketRawPool) {
                    aePacketRawPool.allocateMemory();
                }
            }
            log.fine("Prophesee open: starting event reader thread");
            aeReader.startThread();
            startSensorStreaming();
        } else if (aeReader != null) {
            stopSensorStreaming();
            aeReader.prepareForStop();
            if (!aeReader.finishStop() && !closing) {
                recoverFailedBufferReconfig(new HardwareInterfaceException(
                        "Prophesee AEReader did not stop when disabling acquisition"));
            }
        }
        eventAcquisitionEnabled = enable;
    }

    /** True after {@link Imx636Init#startStreaming}, i.e. the sensor is pushing data into 0x81. */
    boolean isSensorStreaming() {
        return sensorStreaming;
    }

    void stopSensorStreaming() {
        if (!sensorStreaming || deviceHandle == null) {
            return;
        }
        try {
            Imx636Init.stopStreaming(deviceHandle);
        } catch (HardwareInterfaceException e) {
            log.warning("Prophesee ISSD stop: " + e.getMessage());
        }
        sensorStreaming = false;
    }

    void startSensorStreaming() throws HardwareInterfaceException {
        if (sensorStreaming || deviceHandle == null || closing) {
            return;
        }
        Imx636Init.startStreaming(deviceHandle);
        sensorStreaming = true;
        log.info("Prophesee ISSD streaming started (events on 0x81)");
    }

    void persistUsbFifoSize(int fifoSize) {
        this.usbFifoSize = UsbReaderBufferSettings.applyFifoSize(
                prefs, UsbReaderBufferSettings.PREF_KEY_FIFO_SIZE, fifoSize, log, "Prophesee");
        this.usbNumBuffers = UsbReaderBufferSettings.applyNumBuffers(
                prefs, UsbReaderBufferSettings.PREF_KEY_NUM_BUFFERS, usbNumBuffers, this.usbFifoSize, log, "Prophesee");
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
            log.warning("Could not read Prophesee USB device descriptor: " + LibUsb.errorName(status));
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
        return usbFifoSize;
    }

    @Override
    public void setFifoSize(int fifoSize) {
        this.usbFifoSize = UsbReaderBufferSettings.applyFifoSize(
                prefs, UsbReaderBufferSettings.PREF_KEY_FIFO_SIZE, fifoSize, log, "Prophesee");
        this.usbNumBuffers = UsbReaderBufferSettings.applyNumBuffers(
                prefs, UsbReaderBufferSettings.PREF_KEY_NUM_BUFFERS, usbNumBuffers, this.usbFifoSize, log, "Prophesee");
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
                prefs, UsbReaderBufferSettings.PREF_KEY_NUM_BUFFERS, numBuffers, usbFifoSize, log, "Prophesee");
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
