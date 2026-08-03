/*
 * CypressFX2Biasgen.java
 *
 * Created on December 1, 2005, 2:00 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */
package net.sf.jaer.hardwareinterface.usb.cypressfx2libusb;

import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2DVS128HardwareInterfaceInterface;
import java.nio.ByteBuffer;
import java.util.prefs.Preferences;
import net.sf.jaer.JaerConstants;

import net.sf.jaer.aemonitor.AEPacketRaw;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.PacketBundle;
import net.sf.jaer.event.PacketBundlePool;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.UsbPolarityBundleBuilder;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.HasLEDControl;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.HasResettablePixelArray;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.HasSyncEventOutput;

import org.usb4java.Device;

/**
 * The LibUsb hardware interface for the DVS128 (second Tmpdiff128 board, with
 * CPLD) retina boards. Optionally demuxes polarity (+ sync specials) into a
 * typed {@link PacketBundle} on the USB thread.
 *
 * @author tobi/rapha
 * @see LibUsbHardwareInterfaceFactory
 */
public class CypressFX2LibUsbDVS128HardwareInterface extends CypressFX2Biasgen implements CypressFX2DVS128HardwareInterfaceInterface {

    protected static Preferences prefs = JaerConstants.PREFS_ROOT_HARDWARE;
    /** Pref kill-switch for USB→PacketBundle polarity demux. */
    public static final String PREF_USB_TYPED_DEMUX = "CypressFX2DVS128.usbTypedDemux";
    private boolean syncEventEnabled = CypressFX2LibUsbDVS128HardwareInterface.prefs.getBoolean(
            "CypressFX2DVS128HardwareInterface.syncEventEnabled", true); // default
    //  is true so that device is the timestamp master by default, necessary after firmware rev 11

    /**
     * Vendor request for setting LED
     */
    public final byte VENDOR_REQUEST_LED = (byte) 0xCD;
    private LEDState ledState = LEDState.UNKNOWN; // efferent copy, since we can't read it

    private final PacketBundlePool packetBundlePool = new PacketBundlePool();
    private PacketBundle lastPacketBundle = new PacketBundle();
    private volatile boolean usbTypedDemuxActive = prefs.getBoolean(PREF_USB_TYPED_DEMUX, true);
    private final UsbPolarityBundleBuilder polarityBuilder = new UsbPolarityBundleBuilder();

    /**
     * Creates a new instance of CypressFX2Biasgen
     */
    protected CypressFX2LibUsbDVS128HardwareInterface(final Device device) {
        super(device);
        CypressFX2.log.info("DVS128 libusb USB typed demux=" + usbTypedDemuxActive + " (pref " + PREF_USB_TYPED_DEMUX + ")");
    }

    @Override
    public AEPacketRaw acquireAvailableEventsFromDriver() throws HardwareInterfaceException {
        if (!isOpen()) {
            open();
        }
        if (!inEndpointEnabled) {
            setEventAcquisitionEnabled(true);
        }
        final AEPacketRaw lastEventsAcquired;
        synchronized (aePacketRawPool) {
            aePacketRawPool.swap();
            packetBundlePool.swap();
            lastEventsAcquired = aePacketRawPool.readBuffer();
            lastPacketBundle = packetBundlePool.readBuffer();
            eventCounter = 0;
        }
        this.lastEventsAcquired = lastEventsAcquired;
        computeEstimatedEventRate(lastEventsAcquired);
        if (lastEventsAcquired.getNumEvents() != 0) {
            support.firePropertyChange(CypressFX2.PROPERTY_CHANGE_NEW_EVENTS, null, lastEventsAcquired);
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

    /**
     * Overrides open() to also set sync event mode.
     */
    @Override
    public synchronized void open() throws HardwareInterfaceException {
        super.open();
        setSyncEventEnabled(syncEventEnabled);
    }

    /**
     * Starts reader buffer pool thread and enables in endpoints for AEs. This
     * method is overridden to construct our own reader with its translateEvents
     * method
     */
    @Override
    public void startAEReader() throws HardwareInterfaceException { // raphael: changed from private to protected,
        // because i need to access this method
        setAeReader(new RetinaAEReader(this));
        allocateAEBuffers();
        getAeReader().startThread();
        HardwareInterfaceException.clearException();
    }

    @Override
    synchronized public void resetTimestamps() {
        CypressFX2.log
                .info(this
                        + ".resetTimestamps(): zeroing timestamps by sending vendor request to hardware which should return timestamp-reset event and reset wrap counter");

        try {
            this.sendVendorRequest(VENDOR_REQUEST_RESET_TIMESTAMPS);
        } catch (final HardwareInterfaceException e) {
            CypressFX2.log.warning(e.toString());
        }

    }

    @Override
    public void setSyncEventEnabled(final boolean yes) {
        CypressFX2.log.info("setting " + yes);

        try {
            this.sendVendorRequest(VENDOR_REQUEST_SET_SYNC_ENABLED, yes ? (byte) 1 : (byte) 0, (byte) 0);
            syncEventEnabled = yes;
            CypressFX2LibUsbDVS128HardwareInterface.prefs.putBoolean("CypressFX2DVS128HardwareInterface.syncEventEnabled",
                    yes);
        } catch (final HardwareInterfaceException e) {
            CypressFX2.log.warning(e.toString());
        }
    }

    @Override
    public boolean isSyncEventEnabled() {
        return syncEventEnabled;
    }

    int lastTimestampTmp = 0; // TODO debug remove

    /**
     * Returns 1
     *
     * @return 1
     */
    @Override
    public int getNumLEDs() {
        return 1;
    }

    /**
     * Sets the LED state. Throws no exception, just prints warning on hardware
     * exceptions.
     *
     * @param led only 0 in this case
     * @param state the new state
     */
    @Override
    public void setLEDState(final int led, final LEDState state) {
        if (led != 0) {
            throw new RuntimeException(led + " is not valid LED number; only 1 LED");
        }
        // log.info("setting LED="+led+" to state="+state);

        short cmd = 0;
        switch (state) {
            case OFF:
                cmd = 0;
                break;
            case ON:
                cmd = 1;
                break;
            case FLASHING:
                cmd = 2;
                break;
            default:
                cmd = 0;
                break;
        }
        try {
            this.sendVendorRequest(VENDOR_REQUEST_LED, cmd, (byte) 0);
            ledState = state;
        } catch (final HardwareInterfaceException e) {
            CypressFX2.log
                    .warning(e.toString()
                            + ": LED control request ignored. Probably your DVS128 firmware version is too old; LED control was added at revision 12. See \\devices\\firmware\\CypressFX2\\firmware_FX2LP_DVS128\\CHANGELOG.txt");
        }

    }

    /**
     * Returns the last set LED state
     *
     * @param led ignored
     * @return the last set state, or UNKNOWN if never set from host.
     */
    @Override
    public LEDState getLEDState(final int led) {
        return ledState;
    }

    /**
     * This reader understands the format of raw USB data and translates to the
     * AEPacketRaw
     */
    public class RetinaAEReader extends CypressFX2.AEReader {

        private int printedSyncEventWarningCount = 0; // only print this many sync events
        private int resetTimestampWarningCount = 0;
        private final int RESET_TIMESTAMPS_INITIAL_PRINTING_LIMIT = 10;
        private final int RESET_TIMESTAMPS_WARNING_INTERVAL = 100000;

        /**
         * Constructs a new reader for the interface.
         *
         * @param cypress The CypressFX2 interface.
         * @throws HardwareInterfaceException on any hardware error.
         */
        public RetinaAEReader(final CypressFX2 cypress) throws HardwareInterfaceException {
            super(cypress);
        }

        /**
         * Does the translation, timestamp unwrapping and reset. Prints a
         * message when a SYNC event is detected.
         *
         * @param b the raw buffer
         */
        @Override
        protected void translateEvents(final ByteBuffer b) {
            synchronized (aePacketRawPool) {
                final AEPacketRaw buffer = aePacketRawPool.writeBuffer();
                if (usbTypedDemuxActive) {
                    polarityBuilder.attach(packetBundlePool.writeBuffer());
                }
                int shortts;

                int bytesSent = b.limit();

                if ((bytesSent % 4) != 0) {
                    CypressFX2.log.warning("CypressFX2.AEReader.translateEvents(): warning: " + bytesSent
                            + " bytes sent, which is not multiple of 4");
                    bytesSent = (bytesSent / 4) * 4; // truncate off any extra part-event
                }

                final int[] addresses = buffer.getAddresses();
                final int[] timestamps = buffer.getTimestamps();

                // write the start of the packet
                buffer.lastCaptureIndex = eventCounter;

                for (int i = 0; i < bytesSent; i += 4) {
                    // if(eventCounter>aeBufferSize-1){
                    // buffer.overrunOccuredFlag=true;
                    // // log.warning("overrun");
                    // return; // return, output event buffer is full and we cannot add any more events to it.
                    // //no more events will be translated until the existing events have been consumed by
                    // acquireAvailableEventsFromDriver
                    // }

                    if ((b.get(i + 3) & 0x80) == 0x80) { // timestamp bit 15 is one -> wrap
                        // now we need to increment the wrapAdd

                        wrapAdd += 0x4000L; // uses only 14 bit timestamps

                        // System.out.println("received wrap event, index:" + eventCounter + " wrapAdd: "+ wrapAdd);
                        // NumberOfWrapEvents++;
                    } else if ((b.get(i + 3) & 0x40) == 0x40) { // timestamp bit 14 is one -> wrapAdd reset
                        // this firmware version uses reset events to reset timestamps
                        resetTimestamps();
                        lastTimestampTmp = 0; // Also reset this one to avoid spurious warnings.
                        if ((resetTimestampWarningCount < RESET_TIMESTAMPS_INITIAL_PRINTING_LIMIT)
                                || ((resetTimestampWarningCount % RESET_TIMESTAMPS_WARNING_INTERVAL) == 0)) {
                            CypressFX2.log.info(this + ".translateEvents got reset event from hardware, timestamp "
                                    + (0xffff & ((b.get(i + 2) & 0xff) | ((b.get(i + 3) & 0x3f) << 8))));
                        }
                        if (resetTimestampWarningCount == RESET_TIMESTAMPS_INITIAL_PRINTING_LIMIT) {
                            CypressFX2.log
                                    .warning("will only print reset timestamps message every "
                                            + RESET_TIMESTAMPS_WARNING_INTERVAL
                                            + " times now\nCould it be that you are trying to inject sync events using the DVS128 IN pin?\nIf so, select the \"Enable sync events output\" option in the DVS128 menu");
                        }
                        resetTimestampWarningCount++;
                    } else if ((eventCounter > (aeBufferSize - 1)) || (buffer.overrunOccuredFlag)) { // just do nothing,
                        // throw away events
                        buffer.overrunOccuredFlag = true;
                    } else {
                        // address is LSB MSB
                        addresses[eventCounter] = (b.get(i) & 0xFF) | ((b.get(i + 1) & 0xFF) << 8);

                        // same for timestamp, LSB MSB
                        shortts = ((b.get(i + 2) & 0xff) | ((b.get(i + 3) & 0xff) << 8)); // this is 15 bit value
                        // of timestamp in
                        // TICK_US tick

                        timestamps[eventCounter] = TICK_US * (shortts + wrapAdd); // *TICK_US; //add in the wrap offset
                        // and convert to 1us tick

                        if (timestamps[eventCounter] < lastTimestampTmp) {
                            CypressFX2.log.info(String.format("nonmonotonic timestamp: lastTimestamp=%,d, timestamp=%,d, dt=%,d",
                                    lastTimestampTmp,
                                    timestamps[eventCounter],
                                    (lastTimestampTmp - timestamps[eventCounter])));
                        }
                        lastTimestampTmp = timestamps[eventCounter];
                        // this is USB2AERmini2 or StereoRetina board which have 1us timestamp tick
                        final int addr = addresses[eventCounter];
                        final int ts = timestamps[eventCounter];
                        final boolean sync = (addr & CypressFX2LibUsbDVS128HardwareInterface.SYNC_EVENT_BITMASK) != 0;
                        if (sync) {
                            if (printedSyncEventWarningCount < 10) {
                                if (printedSyncEventWarningCount < 10) {
                                    CypressFX2.log.info("sync event at timestamp=" + ts);
                                } else {
                                    CypressFX2.log.warning("disabling further printing of sync events");
                                }
                                printedSyncEventWarningCount++;
                            }
                        }
                        if (usbTypedDemuxActive) {
                            if (sync || (addr & BasicEvent.SPECIAL_EVENT_BIT_MASK) != 0) {
                                polarityBuilder.addSpecial(addr, ts);
                            } else {
                                // Match DVS128.Extractor: x flipped, type = (1-addr)&1
                                final AEChip chip = getChip();
                                final int sizeX = chip != null ? chip.getSizeX() : 128;
                                final int sxm = sizeX - 1;
                                final int x = sxm - ((addr & 0xfe) >>> 1);
                                final int y = (addr & 0x7f00) >>> 8;
                                final boolean on = ((1 - addr) & 1) != 0;
                                polarityBuilder.addPolarity(x, y, on, ts);
                            }
                        }
                        eventCounter++;
                        buffer.setNumEvents(eventCounter);
                    }
                } // end for

                // write capture size
                buffer.lastCaptureLength = eventCounter - buffer.lastCaptureIndex;
                buffer.systemModificationTimeNs = System.nanoTime();
                if (usbTypedDemuxActive) {
                    polarityBuilder.flushAll();
                    packetBundlePool.writeBuffer().setRawPacket(buffer);
                }

                // if (NumberOfWrapEvents!=0) {
                // System.out.println("Number of wrap events received: "+ NumberOfWrapEvents);
                // }
                // System.out.println("wrapAdd : "+ wrapAdd);
            } // sync on aePacketRawPool

        }
    }

    /**
     * set the pixel array reset
     *
     * @param value true to reset the pixels, false to let them run normally
     */
    @Override
    synchronized public void setArrayReset(final boolean value) {
        arrayResetEnabled = value;
        // send vendor request for device to reset array
        if (deviceHandle == null) {
            throw new RuntimeException("device must be opened before sending this vendor request");
        }

        try {
            sendVendorRequest(VENDOR_REQUEST_SET_ARRAY_RESET, (short) ((value) ? (1) : (0)), (short) 0);
        } catch (final HardwareInterfaceException e) {
            System.err.println("CypressFX2.resetPixelArray: couldn't send vendor request to reset array");
        }
    }

    @Override
    public boolean isArrayReset() {
        return arrayResetEnabled;
    }
}
