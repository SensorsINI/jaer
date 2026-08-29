package net.sf.jaer.hardwareinterface.usb;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Headless checks: Interface menu must not {@code LibUsb.open} an already-open
 * FX3 (EDT hang / CypressFX3 ghost), chip selection stays on the EDT, and
 * Davis → None → Davis close/reopen order is preserved. Cannot drive the
 * Swing EDT; those contracts are source order.
 * Run after {@code ant compile}:
 * {@code java -cp build/classes;jars/*;lib/* net.sf.jaer.hardwareinterface.usb.UsbEnumerationSafetyDemo}
 */
public final class UsbEnumerationSafetyDemo {

    private static int assertions;

    private UsbEnumerationSafetyDemo() {
    }

    public static void main(String[] args) throws Exception {
        testSamePhysicalDeviceNullSafe();
        testCypressToStringDoesNotOpen();
        testFx3FactoryListsWithoutOpen();
        testViewerSkipsOpenDeviceByIdentity();
        testViewerSelectsOnEdtWithFriendlyLabels();
        testUsbTransferSubmitHandshake();
        testDavisNoneDavisReopenSequence();
        testHotplugUnplugDoesNotBlockReplug();
        testChipSwitchKeepsHotplugListener();
        testNrvClaimAfterHotplug();
        testWindowsUsbPollSchedule();
        System.out.println("USB_ENUMERATION_SAFETY ASSERTIONS=" + assertions);
        System.out.println("USB_ENUMERATION_SAFETY PASS");
    }

    private static void testSamePhysicalDeviceNullSafe() {
        require(!UsbIds.samePhysicalDevice(null, null), "null devices are not the same");
        require(UsbIds.libUsbDevice(null) == null, "null hw has no libusb device");
        require("USB".equals(UsbIds.unopenedLabel(null, null).trim())
                || UsbIds.unopenedLabel(null, "USB").startsWith("USB"),
                "unopened label with null hw does not throw");
    }

    private static void testCypressToStringDoesNotOpen() throws Exception {
        String toString = methodBody(Paths.get("src", "net", "sf", "jaer",
                "hardwareinterface", "usb", "cypressfx3libusb", "CypressFX3.java"),
                "public String toString()",
                "public Device getLibUsbDevice()");
        require(!toString.contains("open_minimal_close()"),
                "CypressFX3.toString must not LibUsb.open for menu labels");
        require(toString.contains("UsbIds.unopenedLabel")
                || toString.contains("friendlyUnopenedTypeName"),
                "unopened FX3 label uses VID/PID without claiming the device");
    }

    private static void testFx3FactoryListsWithoutOpen() throws Exception {
        String list = methodBody(Paths.get("src", "net", "sf", "jaer",
                "hardwareinterface", "usb", "cypressfx3libusb", "LibUsb3HardwareInterfaceFactory.java"),
                "private List<Device> buildCompatibleDevicesList()",
                "public USBInterface getFirstAvailableInterface()");
        require(!list.contains("LibUsb.open("),
                "FX3 factory must not LibUsb.open during USB scan");
    }

    private static void testViewerSkipsOpenDeviceByIdentity() throws Exception {
        String menu = methodBody(Paths.get("src", "net", "sf", "jaer", "graphics", "AEViewer.java"),
                "public void buildInterfaceMenu(JMenu interfaceMenu, boolean forceUsbRescan)",
                "private volatile List<String> interfaceMenuDeviceLabels");
        require(menu.contains("UsbIds.samePhysicalDevice(hw, chip.getHardwareInterface())"),
                "Interface menu skips the already-open camera by USB bus/addr");
        require(!menu.contains("hw.toString().equals(chip.getHardwareInterface().toString())"),
                "menu skip is not product-string equality (ghost CypressFX3)");
    }

    private static void testViewerSelectsOnEdtWithFriendlyLabels() throws Exception {
        String src = Files.readString(Paths.get("src", "net", "sf", "jaer", "graphics", "AEViewer.java"),
                StandardCharsets.UTF_8);
        require(!src.contains("openSelectedHardwareInterfaceOffEdt("),
                "Interface selection must not abandon chip chooser off the EDT");
        require(src.contains("Stop WAITING from rebinding the same ghost device"),
                "USB open ACCESS must stop auto-rebind (None until user picks Interface)");
        require(!src.contains("openSelectedHardwareInterfaceOffEdt"),
                "no off-EDT selector that abandons the AEChip chooser");
        require(src.contains("interfaceMenuLabel(hw)"),
                "Interface menu uses AEChip-family labels for newbies");
        require(src.contains("chipFamilyMenuLabel("),
                "Davis346 variants collapse to a readable family name");
        String action = methodBody(Paths.get("src", "net", "sf", "jaer", "graphics", "AEViewer.java"),
                "public void buildInterfaceMenu(JMenu interfaceMenu, boolean forceUsbRescan)",
                "private volatile List<String> interfaceMenuDeviceLabels");
        require(action.contains("getCachedNumInterfacesAvailable()"),
                "Interface menu must not scan USB on the EDT");
        require(action.contains("Refresh to rescan USB"),
                "Interface menu points users at Refresh instead of scanning");
        require(!action.contains("markUsbEnumerationDirty()"),
                "opening the Interface menu must not dirty/rescan USB");
        require(action.contains("ensureChipCompatibleWithLiveDevice(hw)"),
                "Davis / SciDVS chooser runs on the EDT during Interface selection");
        require(action.contains("bindLiveHardwareIfCompatible(bind,"),
                "selected interface is bound on the EDT so ViewLoop can go LIVE");
        require(action.indexOf("hardwareSwitchInProgress = false")
                < action.indexOf("interruptViewloop();"),
                "interruptViewloop runs after switch flag clears, not during bind");
        require(action.contains("HARDWARE_CLOSE_JOIN_MS"),
                "Prophesee ISSD close uses the 20 s join, not the 3 s watcher");
        require(action.contains("requestOpenAbort()")
                || action.contains("isOpenInProgress()"),
                "Interface switch aborts an in-progress Prophesee open (not only isOpen)");
        String ensure = methodBody(Paths.get("src", "net", "sf", "jaer", "graphics", "AEViewer.java"),
                "public void ensureChipCompatibleWithLiveDevice(HardwareInterface hw)",
                "private Class<? extends AEChip> loadRememberedLiveChip");
        require(ensure.contains("!SwingUtilities.isEventDispatchThread()"),
                "SciDVS FPGA LibUsb probe must not run on the EDT");
        require(ensure.indexOf("loadRememberedLiveChip(")
                < ensure.indexOf("probeSciDVSByFpgaGeometry()"),
                "remembered AEChip wins before any USB probe");
        require(src.contains("jaer-aemon-open"),
                "aemon.open runs on a worker so ViewLoop can abandon a stuck open");
        require(src.contains("unbindAbandonedHardware("),
                "timed-out open unbinds the hung wrapper instead of retrying close/open");
        require(Files.readString(Paths.get("src", "net", "sf", "jaer",
                "hardwareinterface", "usb", "cypressfx3libusb", "CypressFX3.java"),
                StandardCharsets.UTF_8).contains("skipping USB string descriptors"),
                "CypressFX3 open must not issue USB string-descriptor transfers");
        String dvxOpen = methodBody(
                Paths.get("src", "net", "sf", "jaer", "hardwareinterface", "usb",
                        "cypressfx3libusb", "DVXplorerFX3HardwareInterface.java"),
                "synchronized public void open() throws HardwareInterfaceException {",
                "public synchronized void spiConfigSend(");
        require(!dvxOpen.contains("cleanupCx3DataBuffers()"),
                "DVXplorer open must not VR_DATA_CLEANUP 0xC6 (native hang)");
        require(!dvxOpen.contains("dvxConfig()"),
                "DVXplorer dvxConfig runs after USB open returns, not inside open()");
        require(dvxOpen.contains("waitForDataEndpoints()"),
                "Mini/Micro open waits for bulk IN 0x82 after claim (hotplug SuperSpeed)");
        String waitEps = methodBody(
                Paths.get("src", "net", "sf", "jaer", "hardwareinterface", "usb",
                        "cypressfx3libusb", "DVXplorerFX3HardwareInterface.java"),
                "private void waitForDataEndpoints() throws HardwareInterfaceException {",
                "synchronized public void open() throws HardwareInterfaceException {");
        require(waitEps.contains("AE_MONITOR_ENDPOINT_ADDRESS"),
                "endpoint wait looks for bulk IN 0x82");
        require(waitEps.contains("CX3_DEBUG_ENDPOINT"),
                "endpoint wait looks for IMU interrupt 0x81");
        require(waitEps.contains("setInterfaceAltSetting"),
                "endpoint wait selects the alt-setting that has bulk IN");
        require(waitEps.contains("LIBUSB_ERROR_NOT_FOUND"),
                "missing 0x82 fails open instead of starting a dead AEReader");
        require(Files.readString(Paths.get("src", "net", "sf", "jaer",
                "hardwareinterface", "usb", "cypressfx3libusb",
                "DVXplorerFX3HardwareInterface.java"), StandardCharsets.UTF_8)
                .contains("skipping VR_DATA_CLEANUP 0xC6"),
                "CX3 buffer cleanup must not issue vendor 0xC6");
        String spiIn = methodBody(
                Paths.get("src", "net", "sf", "jaer", "hardwareinterface", "usb",
                        "cypressfx3libusb", "DVXplorerFX3HardwareInterface.java"),
                "public synchronized int spiConfigReceive(final short moduleAddr, final short paramAddr)",
                "synchronized public void setPowerDown");
        require(spiIn.contains("skipping SPI IN on Mini/Micro firmware"),
                "firmware 10+ must not LibUsb SPI IN (native hang / UI freeze)");
        require(!spiIn.contains("sendVendorRequestIN("),
                "next-gen spiConfigReceive must not issue 8-byte vendor IN");
        String spiOut = methodBody(
                Paths.get("src", "net", "sf", "jaer", "hardwareinterface", "usb",
                        "cypressfx3libusb", "DVXplorerFX3HardwareInterface.java"),
                "static boolean isNextGenStreamingParam",
                "public synchronized int spiConfigReceive(final short moduleAddr, final short paramAddr)");
        require(spiOut.contains("skipping SPI OUT on Mini/Micro firmware"),
                "firmware 10+ must not 8-byte SPI OUT for DVS_FLATTEN (hung open 8 s)");
        require(spiOut.contains("sendNextGenDvsRun("),
                "Mini/Micro DVS_RUN uses 8-byte SPI (4-byte stalled PIPE 8:58:38)");
        require(spiOut.contains("new byte[8]"),
                "firmware 10 DVS_RUN payload is 8 bytes");
        require(!spiOut.contains("Mini/Micro 4-byte SPI OUT"),
                "firmware 10 DVS_RUN must not use 4-byte wLength");
        require(spiOut.contains("DVX_IMU_RUN_ACCELEROMETER"),
                "Mini/Micro IMU_RUN_* uses 8-byte SPI like DVXplorerM (not skipped)");
        require(spiOut.contains("DVX_IMU_GYRO_DATA_RATE"),
                "Mini/Micro BMI160 gyro ODR uses 8-byte SPI (c6ec5a073 skip froze gyros)");
        require(spiOut.contains("FPGA_IMU"),
                "next-gen streaming params include MODULE_IMU=3");
        require(spiOut.contains("DVS_EFPS_S5K231Y"),
                "Mini/Micro ReadoutFPS uses 8-byte DVS_EFPS_S5K231Y (was skipped)");
        require(spiOut.contains("SPI OUT confirmed"),
                "Mini/Micro DVS bias SPI logs after vendor request succeeds");
    }

    /**
     * Davis LIVE → Interface None → Davis again (jAER 12:57:03). Cannot drive
     * the EDT, but the source order that hung WinUSB is checkable: close must
     * stop the AEReader while still open, None must not {@code close()} on the
     * EDT, and reopen must configure on {@code jaer-aemon-open} before LIVE.
     */
    private static void testDavisNoneDavisReopenSequence() throws Exception {
        Path viewer = Paths.get("src", "net", "sf", "jaer", "graphics", "AEViewer.java");
        Path fx3 = Paths.get("src", "net", "sf", "jaer",
                "hardwareinterface", "usb", "cypressfx3libusb", "CypressFX3.java");

        String none = methodBody(viewer,
                "JRadioButtonMenuItem noneInterfaceButton = new JRadioButtonMenuItem(\"None\")",
                "noneInterfaceButton.setSelected(!interfaceAlreadyOpen)");
        require(none.contains("clearOpeningCameraOverlay()"),
                "None restores idle Welcome overlay");
        require(none.contains("chip.setHardwareInterface(null)"),
                "None detaches the AEChip so ViewLoop cannot keep the live wrapper");
        require(none.contains("aemon = null"),
                "None clears aemon so WAITING does not acquire the closed Davis");
        require(none.contains("nullInterface = true"),
                "None blocks auto-rebind until the user picks a device");
        require(none.contains("setPlayMode(PlayMode.WAITING)"),
                "None leaves LIVE so the next Davis select starts from WAITING");
        require(none.contains("closeHardwareInterfaceWithTimeout("),
                "None closes USB off the EDT");
        require(!none.contains("hw.close()"),
                "None must not call HardwareInterface.close on the EDT");

        String asyncClose = methodBody(viewer,
                "private Thread closeHardwareInterfaceWithTimeout(HardwareInterface hw, long timeoutMs, String actionLabel) {",
                "private String interfaceMenuLabel(HardwareInterface hw) {");
        require(asyncClose.contains("\"jaer-hw-close\""),
                "Davis close runs on jaer-hw-close, not the EDT");
        require(asyncClose.contains("closer.start()"),
                "async close starts before any join");
        require(asyncClose.contains("Do not block the EDT"),
                "close timeout join is on a watcher, not the menu click");
        require(asyncClose.contains("\"jaer-hw-close-watch\""),
                "EDT does not closer.join during None");
        require(asyncClose.contains("hardwareCloseThread = closer"),
                "closer is stored so the next open can join it");
        require(Files.readString(viewer, StandardCharsets.UTF_8).contains("awaitPendingHardwareClose()"),
                "ViewLoop waits for previous jaer-hw-close before aemon.open");
        String awaitClose = methodBody(viewer,
                "private void awaitPendingHardwareClose() {",
                "private Thread closeHardwareInterfaceWithTimeout(");
        require(awaitClose.contains("HARDWARE_CLOSE_JOIN_MS"),
                "ViewLoop joins previous close up to 20 s (Prophesee ISSD exceeds 3 s)");
        require(awaitClose.contains("interrupt during previous close wait; still waiting"),
                "interruptViewloop must not abort the previous-close join");
        require(!awaitClose.contains("unbindAbandonedHardware("),
                "close-wait interrupt must not unbind the next camera");
        String openWait = methodBody(viewer,
                "if (openDone.await(100, TimeUnit.MILLISECONDS)) {",
                "if (abandoned) {");
        require(openWait.contains("interrupt while waiting for open of"),
                "open wait logs Interface-switch interrupt");
        require(openWait.contains("; continuing"),
                "open wait continues after interruptViewloop instead of unbind");
        require(openWait.contains("ViewLoop stopping"),
                "only ViewLoop stop aborts open wait on interrupt");

        String stopAcq = methodBody(fx3,
                "public synchronized void setEventAcquisitionEnabled(final boolean enable) throws HardwareInterfaceException {",
                "public boolean isEventAcquisitionEnabled() {");
        require(stopAcq.contains("if (!isOpen())"),
                "setEventAcquisitionEnabled no-ops when isOpened is already false");
        require(stopAcq.indexOf("if (!isOpen())") < stopAcq.indexOf("stopAEReader()"),
                "clearing isOpened before close() skips AEReader stop");

        String fx3Close = methodBody(fx3,
                "synchronized public void close() {",
                "public boolean stopAEReader()");
        require(fx3Close.indexOf("stopAEReader()") < fx3Close.indexOf("isOpened = false"),
                "FX3 close stops AEReader while still open (None then Davis hung)");
        require(fx3Close.indexOf("setInEndpointEnabled(false)") < fx3Close.indexOf("isOpened = false"),
                "FX3 close disables IN endpoint before clearing isOpened");
        require(fx3Close.indexOf("isOpened = false") < fx3Close.indexOf("LibUsb.close("),
                "handle is released only after the AEReader is stopped");
        require(fx3Close.contains("abandonNativeHandle(readerDead"),
                "FX3 must not LibUsb.close while AEReader is still in native USB");
        require(Files.readString(fx3, StandardCharsets.UTF_8).contains("not starting USB-recover"),
                "failed AEReader join during close must not start a second LibUsb.close");

        String isOpen = methodBody(fx3,
                "public boolean isOpen() {",
                "final public int getTimestampTickUs()");
        require(!isOpen.contains("synchronized"),
                "isOpen must not take the USB monitor (EDT paint during hung SPI)");

        String select = methodBody(viewer,
                "if (currentHw != null && currentHw.isOpen()",
                "if (getPlayMode() != PlayMode.PLAYBACK && getPlayMode() != PlayMode.FILTER_INPUT) {");
        require(select.contains("nullInterface = false"),
                "selecting Davis after None clears nullInterface so ViewLoop may open");
        require(select.indexOf("nullInterface = false")
                < select.indexOf("bindLiveHardwareIfCompatible("),
                "nullInterface is cleared before bind, not after");
        require(select.contains("showOpeningCameraOverlay(hw)"),
                "Interface click shows Opening overlay before USB open");
        require(select.contains("currentHw != null && currentHw.isOpen()"),
                "after None currentHw is null, so Davis is not treated as already open");
        require(select.contains("bindLiveHardwareIfCompatible("),
                "Davis after None binds on the EDT");

        String opener = methodBody(viewer,
                "Thread opener = new Thread(() -> {",
                "}, \"jaer-aemon-open\");");
        require(opener.indexOf("opening.open()")
                < opener.indexOf("bg.sendConfiguration(bg)"),
                "reopen: USB open then sendConfiguration on the same worker");
        require(opener.contains("jaer-aemon-open sendConfiguration begin"),
                "bias sendConfiguration is logged on jaer-aemon-open before LIVE");
        String viewerSrc = Files.readString(viewer, StandardCharsets.UTF_8);
        require(!viewerSrc.contains("\"jaer-send-biases\""),
                "do not race LIVE with a parallel jaer-send-biases thread");
        require(viewerSrc.indexOf("bg.sendConfiguration(bg)")
                < viewerSrc.indexOf("setPlayMode(PlayMode.LIVE)"),
                "ViewLoop does not go LIVE until the open worker finished config");
        String[] opening = net.sf.jaer.Welcome.opening(null, "Davis346");
        require(String.join("\n", opening).contains("Welcome to jAER"),
                "opening overlay keeps the Welcome title");
        require(String.join("\n", opening).contains("Opening Davis346"),
                "Welcome.opening names the camera");
        require(!String.join("\n", opening).contains("Plug in a device"),
                "opening overlay does not tell the user to plug in a camera");
        String skip = methodBody(
                Paths.get("src", "net", "sf", "jaer", "graphics", "ChipCanvas.java"),
                "private boolean shouldSkipChipDisplay() {",
                "private void drawSkipChipRenderingOverlayIfNeeded");
        require(skip.contains("isWelcomeOverlayActive()"),
                "WAITING welcome/opening overlay blanks the chip pixmap");
    }

    /**
     * Unplug while LIVE closed the wrapper then ViewLoop reopened it
     * ({@code devicePointer is not initialized}), set {@code nullInterface}, and
     * ignored the next libusb ARRIVED (jAER-0.log 17:44:59 / 17:46:00).
     */
    private static void testHotplugUnplugDoesNotBlockReplug() throws Exception {
        Path viewer = Paths.get("src", "net", "sf", "jaer", "graphics", "AEViewer.java");
        String hotplug = methodBody(viewer,
                "private void onLibUsbHotplug(boolean arrived, int vid, int pid) {",
                "private static boolean isUsbDeviceGone(Throwable t) {");
        require(hotplug.contains("nullInterface = false"),
                "hotplug add must clear nullInterface so WAITING can open a replugged camera");
        require(hotplug.contains("nullifyHardware()"),
                "hotplug remove of the live camera unbinds the closed wrapper");
        require(hotplug.contains("PlayMode.LIVE"),
                "hotplug remove interrupts LIVE so ViewLoop does not reopen the dead wrapper");
        String open = methodBody(viewer,
                "private void openAEMonitor() {",
                "private void showUsbLinkOverlayAfterOpen()");
        require(open.contains("dropping closed hardware wrapper"),
                "openAEMonitor drops a closed wrapper instead of reopening devicePointer-null");
        require(open.contains("isUsbDeviceGone(e)"),
                "failed open of an unplugged device is distinguished from ACCESS");
        require(open.contains("nullInterface = false"),
                "device-gone open failure must not block the next plug");
        String gone = methodBody(viewer,
                "private static boolean isUsbDeviceGone(Throwable t) {",
                "private void stopLiveAcquisitionForExit()");
        require(gone.contains("devicePointer"),
                "devicePointer-not-initialized is treated as USB device gone");
        require(gone.contains("LIBUSB_ERROR_NO_DEVICE"),
                "LIBUSB_ERROR_NO_DEVICE is treated as USB device gone");
        require(gone.contains("LIBUSB_ERROR_NOT_FOUND"),
                "claimInterface NOT_FOUND after hotplug must not set nullInterface");
        require(gone.contains("LIBUSB_ERROR_IO"),
                "SPI/control LIBUSB_ERROR_IO on unplug mid-open is treated as USB device gone");
        require(gone.contains("LIBUSB_ERROR_PIPE"),
                "LIBUSB_ERROR_PIPE is treated as USB device gone");
        String welcome = methodBody(viewer,
                "public void showWelcomeOverlay() {",
                "public void showOpeningCameraOverlay(HardwareInterface hw) {");
        require(welcome.contains("clearUsbLinkOverlay()"),
                "Welcome overlay must hide the USB bus-speed overlay");
        String nullify = methodBody(viewer,
                "private void nullifyHardware() {",
                "private void openAEMonitor() {");
        require(nullify.contains("clearUsbLinkOverlay()"),
                "unplug nullifyHardware must hide the USB bus-speed overlay");
        String usbDraw = methodBody(
                Paths.get("src", "net", "sf", "jaer", "graphics", "ChipCanvas.java"),
                "private void drawUsbLinkOverlayIfNeeded(final GLAutoDrawable drawable) {",
                "public void displayChanged(final GLAutoDrawable drawable, final boolean modeChanged, final boolean deviceChanged) {");
        require(usbDraw.contains("isWelcomeOverlayActive()"),
                "USB bus-speed overlay is not painted over Welcome after unplug");
    }

    /**
     * setAeChipClass calls cleanup(); removing the hotplug listener there left
     * WAITING deaf after Davis→NRV (jAER-0.log 4:35:05).
     */
    private static void testChipSwitchKeepsHotplugListener() throws Exception {
        Path viewer = Paths.get("src", "net", "sf", "jaer", "graphics", "AEViewer.java");
        String cleanup = methodBody(viewer,
                "private void cleanup() {",
                "private boolean isWindows() {");
        require(!cleanup.contains("LibUsbHotplug.removeListener"),
                "chip switch cleanup must not drop the libusb hotplug listener");
        String closing = methodBody(viewer,
                "private void formWindowClosing(java.awt.event.WindowEvent evt) {",
                "private void refreshInterfaceMenuItemActionPerformed");
        require(closing.contains("LibUsbHotplug.removeListener"),
                "window close still unregisters the hotplug listener");
        String notify = methodBody(
                Paths.get("src", "net", "sf", "jaer", "hardwareinterface", "usb", "LibUsbHotplug.java"),
                "private static void notifyDeviceChange(boolean arrived, int vid, int pid) {",
                "private static void pumpEvents() {");
        require(notify.contains("getNumInterfacesAvailable()"),
                "hotplug notify rebuilds the Interface menu cache off the EDT");
    }

    /**
     * Linux NRV claimInterface NOT_FOUND after hotplug: unconfigured CX3, leaked
     * handle from EDT apply-on-bind, and no setConfiguration (unlike CypressFX3).
     */
    private static void testNrvClaimAfterHotplug() throws Exception {
        Path nrv = Paths.get("src", "nrv", "usb", "NRVHardwareInterface.java");
        String acquire = methodBody(nrv,
                "private void acquireDevice() throws HardwareInterfaceException {",
                "private void ensureUsbConfiguration() {");
        require(acquire.contains("ensureUsbConfiguration()"),
                "NRV claim sets USB configuration 1 before claimInterface");
        require(acquire.contains("CLAIM_RETRY_MS"),
                "NRV retries claimInterface after hotplug NOT_FOUND");
        String open = methodBody(nrv,
                "public synchronized void open() throws HardwareInterfaceException {",
                "private void closePartialOpen() {");
        require(open.contains("closePartialOpen()"),
                "failed NRV open closes the libusb handle before the next try");
        String cfg = methodBody(
                Paths.get("src", "nrv", "chip", "NRVConfig.java"),
                "public void setHardwareInterface(final BiasgenHardwareInterface hardwareInterface) {",
                "public List<NRVRegisterSetting> getLoadedSettings() {");
        require(!cfg.contains("ensureAppliedToHardware()"),
                "NRV bind must not open USB on the EDT");
        require(cfg.contains("tryEnsureSettingsParsedFromPreferences()"),
                "NRV bind still pre-parses settings without opening");
    }

    /**
     * USBTransferThread.allocateTransfers throws uncaught {@code LIBUSB_ERROR_NOT_FOUND}
     * when bulk IN is missing (Mini/Micro hotplug SuperSpeed). Do not go LIVE / DVS_RUN
     * until URBs are queued; close and WAITING if the reader dies.
     */
    private static void testUsbTransferSubmitHandshake() throws Exception {
        require(UsbTransferSubmit.isSubmitFailure(new IllegalStateException(
                "could not submit transfer libusb transfer 0x0, error: -5 - LIBUSB_ERROR_NOT_FOUND")),
                "NOT_FOUND submit wrap is a USBTransferThread start failure");
        require(UsbTransferSubmit.isUnrecoverableSubmitFailure(new IllegalStateException(
                "error: LIBUSB_ERROR_NO_DEVICE")),
                "NO_DEVICE is unrecoverable (do not shrink FIFO)");
        require(!UsbTransferSubmit.isSubmitFailure(new RuntimeException("unrelated")),
                "non-USB exceptions are not submit failures");
        Path submit = Paths.get("src", "net", "sf", "jaer", "hardwareinterface", "usb",
                "UsbTransferSubmit.java");
        require(Files.readString(submit, StandardCharsets.UTF_8).contains("awaitQueued"),
                "UsbTransferSubmit.awaitQueued joins until URBs are queued");
        Path fx3 = Paths.get("src", "net", "sf", "jaer",
                "hardwareinterface", "usb", "cypressfx3libusb", "CypressFX3.java");
        String start = methodBody(fx3,
                "public void startThread() throws HardwareInterfaceException {",
                "public boolean stopThread()");
        require(start.contains("startBulkTransferThread("),
                "AEReader.startThread uses the queued-URB handshake");
        require(start.contains("UsbTransferSubmit.awaitQueued"),
                "AEReader waits for USBTransferThread allocateTransfers before readerStarted");
        require(start.contains("installFailureHandler"),
                "AEReader catches uncaught allocateTransfers IllegalStateException");
        require(start.indexOf("startBulkTransferThread(")
                        < start.indexOf("firePropertyChange(\"readerStarted\""),
                "readerStarted fires only after URBs are queued");
        String bulk = methodBody(fx3,
                "private void startBulkTransferThread(long generation) throws HardwareInterfaceException {",
                "public boolean stopThread()");
        require(bulk.contains("recoverFailedBufferReconfig"),
                "reader death after LIVE closes the device (ViewLoop WAITING)");
        String session = methodBody(fx3,
                "public Config startSession(Config requested, long generation) throws HardwareInterfaceException {",
                "public void applyIdleConfig(Config config) {");
        require(session.indexOf("startBulkTransferThread(")
                        < session.indexOf("resumeStreamingAfterUsbRestart()"),
                "buffer reconfig must not DVS_RUN until new URBs are queued");
        String startAe = methodBody(
                Paths.get("src", "net", "sf", "jaer", "hardwareinterface", "usb",
                        "cypressfx3libusb", "DVXplorerFX3HardwareInterface.java"),
                "public void startAEReader() throws HardwareInterfaceException {",
                "protected void quiesceStreamingForUsbRestart() {");
        require(startAe.indexOf("getAeReader().startThread()")
                        < startAe.indexOf("chip.dvxDataStart()"),
                "Mini/Micro DVS_RUN only after startThread confirms bulk IN queued");
        Path prophesee = Paths.get("src", "prophesee", "usb", "PropheseeAEReader.java");
        require(Files.readString(prophesee, StandardCharsets.UTF_8).contains("UsbTransferSubmit.awaitQueued"),
                "Prophesee AEReader uses the same queued-URB handshake");
        Path nrv = Paths.get("src", "nrv", "usb", "NRVAEReader.java");
        require(Files.readString(nrv, StandardCharsets.UTF_8).contains("UsbTransferSubmit.awaitQueued"),
                "NRV AEReader uses the same queued-URB handshake");
    }

    /**
     * Windows libusb has no hotplug; WAITING discovery decays 1 s → 3 s → 15 s
     * and restarts on enumerated-device change.
     */
    private static void testWindowsUsbPollSchedule() throws Exception {
        final long t0 = 1_000_000L;
        WindowsUsbPollSchedule s = new WindowsUsbPollSchedule(t0);
        require(s.intervalMs(t0) == WindowsUsbPollSchedule.FAST_INTERVAL_MS,
                "startup poll is 1 s");
        require(s.intervalMs(t0 + WindowsUsbPollSchedule.FAST_DURATION_MS - 1)
                        == WindowsUsbPollSchedule.FAST_INTERVAL_MS,
                "still 1 s just before 1 min");
        require(s.intervalMs(t0 + WindowsUsbPollSchedule.FAST_DURATION_MS)
                        == WindowsUsbPollSchedule.MEDIUM_INTERVAL_MS,
                "3 s after the first minute");
        require(s.intervalMs(t0 + WindowsUsbPollSchedule.FAST_DURATION_MS
                        + WindowsUsbPollSchedule.MEDIUM_DURATION_MS - 1)
                        == WindowsUsbPollSchedule.MEDIUM_INTERVAL_MS,
                "still 3 s just before 11 min");
        require(s.intervalMs(t0 + WindowsUsbPollSchedule.FAST_DURATION_MS
                        + WindowsUsbPollSchedule.MEDIUM_DURATION_MS)
                        == WindowsUsbPollSchedule.SLOW_INTERVAL_MS,
                "15 s after 1 min + 10 min");
        java.util.logging.Logger log = java.util.logging.Logger.getLogger("net.sf.jaer");
        require(!s.noteScanResult("none", t0, log), "first fingerprint is baseline");
        require(!s.noteScanResult("none", t0 + 5_000, log), "unchanged list does not reset");
        long later = t0 + WindowsUsbPollSchedule.FAST_DURATION_MS
                + WindowsUsbPollSchedule.MEDIUM_DURATION_MS;
        require(s.intervalMs(later) == WindowsUsbPollSchedule.SLOW_INTERVAL_MS,
                "pre-change interval is 15 s");
        require(s.noteScanResult("DAViSFX3 152a:841a bus1-addr3", later, log),
                "device appearance resets the schedule");
        require(s.intervalMs(later) == WindowsUsbPollSchedule.FAST_INTERVAL_MS,
                "after plug, poll is 1 s again");
        require(s.waitingSleepMs(t0) <= 600 && s.waitingSleepMs(t0) >= 200,
                "WAITING sleep is between 200 ms and 600 ms");
        Path viewer = Paths.get("src", "net", "sf", "jaer", "graphics", "AEViewer.java");
        String openHw = methodBody(viewer,
                "private void openHardwareIfNonambiguous() {",
                "private boolean bindUnambiguousInterfaceIfPossible");
        require(openHw.contains("windowsUsbPoll.intervalMs"),
                "WAITING uses WindowsUsbPollSchedule when hotplug is absent");
        require(openHw.contains("noteScanResult(usbDeviceFingerprint"),
                "Windows poll resets when the enumerated set changes");
        require(openHw.contains("logPhaseIfChanged"),
                "Windows poll interval changes are logged");
        String waiting = methodBody(viewer,
                "WAITING suppressHardwareOpen sleep interrupted",
                "case FILTER_INPUT:");
        require(waiting.contains("waitingSleepMs"),
                "WAITING sleep shortens so 1 s scans are reachable");
        String openMon = methodBody(viewer,
                "private void openAEMonitor() {",
                "private void showUsbLinkOverlayAfterOpen()");
        require(openMon.contains("resetWindowsUsbPoll(\"device removed\")"),
                "Windows unplug restarts 1 s scans");
        String src = Files.readString(viewer, StandardCharsets.UTF_8);
        require(src.contains("windowGainedFocus"),
                "AEViewer focus gained restarts Windows 1 s USB scans");
        require(src.contains("onViewerWindowGainedFocus()"),
                "focus-gained handler is wired");
        require(src.contains("resetWindowsUsbPoll(\"window focus gained\")"),
                "focus gained resets the Windows USB poll to 1 s");
        require(src.contains("lastInterfaceCheckTime = 0"),
                "focus gained forces the next WAITING tick to scan immediately");
    }

    private static String methodBody(Path path, String start, String end) throws Exception {
        String source = Files.readString(path, StandardCharsets.UTF_8);
        int from = source.indexOf(start);
        require(from >= 0, "source contains " + start + " in " + path);
        int to = source.indexOf(end, from + start.length());
        require(to > from, "source contains " + end + " in " + path);
        return source.substring(from, to);
    }

    private static void require(boolean cond, String msg) {
        assertions++;
        if (!cond) {
            throw new AssertionError(msg);
        }
    }
}
