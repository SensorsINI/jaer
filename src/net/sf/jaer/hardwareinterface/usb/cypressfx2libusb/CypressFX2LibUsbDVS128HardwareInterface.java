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
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.HasLEDControl;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.HasResettablePixelArray;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.HasSyncEventOutput;

import org.usb4java.Device;

/**
 * The LibUsb hardware interface for the DVS128 (second Tmpdiff128 board, with
 * CPLD) retina boards.
 *
 * @author tobi/rapha
 * @see LibUsbHardwareInterfaceFactory
 */
public class CypressFX2LibUsbDVS128HardwareInterface extends CypressFX2Biasgen implements CypressFX2DVS128HardwareInterfaceInterface {

    protected static Preferences prefs = JaerConstants.PREFS_ROOT_HARDWARE;
    private static final String SYNC_EVENT_PREF_KEY = "CypressFX2DVS128HardwareInterface.syncEventEnabled";
    private static final String DEBUG_WORD_DUMP_PROPERTY = "jaer.usbaermini2.debug";
    private static final String DEBUG_WORD_DUMP_LIMIT_PROPERTY = "jaer.usbaermini2.debugWords";
    private static final int DEFAULT_DEBUG_WORD_DUMP_LIMIT = 64;
    private static final int PROFILE_BOOTSTRAP_WORDS = 1024;
    private static final short USBAERMINI2_VID = (short) 0x0547;
    private static final byte VENDOR_REQUEST_OPERATION_MODE = (byte) 0xC3;
    private boolean syncEventEnabled = CypressFX2LibUsbDVS128HardwareInterface.prefs.getBoolean(
            SYNC_EVENT_PREF_KEY, true); // default
    //  is true so that device is the timestamp master by default, necessary after firmware rev 11
    private boolean disableSyncVendorRequest = false;
    private boolean isUsbaermini2 = false;
    private short usbaermini2Did = 0;

    /**
     * USBAERmini2 decode/timestamp behavior ported from SensorsINI/jaer 20180323
     * (CypressFX2MonitorSequencer): DID-based parser split and startup timestamp init.
     */
    private enum DecodeProfile {
        CPLD_LE,
        ORIGINAL_LE,
        CPLD_BE,
        ORIGINAL_BE
    }

    /**
     * Vendor request for setting LED
     */
    public final byte VENDOR_REQUEST_LED = (byte) 0xCD;
    private LEDState ledState = LEDState.UNKNOWN; // efferent copy, since we can't read it

    /**
     * Creates a new instance of CypressFX2Biasgen
     */
    protected CypressFX2LibUsbDVS128HardwareInterface(final Device device) {
        super(device);
    }

    /**
     * Overrides open() to also set sync event mode.
     */
    @Override
    public synchronized void open() throws HardwareInterfaceException {
        super.open();
        isUsbaermini2 = (getVID_THESYCON_FX2_CPLD() == USBAERMINI2_VID) && (getPID() == CypressFX2.PID_USBAERmini2);
        usbaermini2Did = isUsbaermini2 ? getDID() : 0;

        if (isUsbaermini2) {
            CypressFX2.log.info(String.format(
                    "USBAERmini2 detected (VID/PID 0x%04X/0x%04X, DID=0x%04X)",
                    (getVID_THESYCON_FX2_CPLD() & 0xFFFF), (getPID() & 0xFFFF), (usbaermini2Did & 0xFFFF)));
            // Startup timestamp init sequence for USBAERmini2 firmware, independent of sync-event requests.
            try {
                sendVendorRequest(VENDOR_REQUEST_OPERATION_MODE, (short) 0, (short) 0); // host/master, 1us tick
            } catch (final HardwareInterfaceException e) {
                CypressFX2.log.warning("USBAERmini2 operation-mode init request failed: " + e.toString());
            }
            try {
                sendVendorRequest(VENDOR_REQUEST_RESET_TIMESTAMPS, (short) 0, (short) 0); // one-time startup reset
            } catch (final HardwareInterfaceException e) {
                CypressFX2.log.warning("USBAERmini2 startup timestamp reset request failed: " + e.toString());
            }
            if (syncEventEnabled) {
                CypressFX2.log.info("USBAERmini2 detected: defaulting sync-event generation to disabled");
                syncEventEnabled = false;
                CypressFX2LibUsbDVS128HardwareInterface.prefs.putBoolean(SYNC_EVENT_PREF_KEY, false);
            }
        }
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
        if ((getPID() == CypressFX2.PID_USBAERmini2) && !yes) {
            // Many USBAERmini2 firmwares don't support vendor request 0xBE.
            // Keep local state disabled and avoid sending the request.
            syncEventEnabled = false;
            CypressFX2LibUsbDVS128HardwareInterface.prefs.putBoolean(SYNC_EVENT_PREF_KEY, false);
            return;
        }
        if (disableSyncVendorRequest && (getPID() == CypressFX2.PID_USBAERmini2)) {
            syncEventEnabled = false;
            CypressFX2LibUsbDVS128HardwareInterface.prefs.putBoolean(SYNC_EVENT_PREF_KEY, false);
            return;
        }

        try {
            this.sendVendorRequest(VENDOR_REQUEST_SET_SYNC_ENABLED, yes ? (byte) 1 : (byte) 0, (byte) 0);
            syncEventEnabled = yes;
            CypressFX2LibUsbDVS128HardwareInterface.prefs.putBoolean(SYNC_EVENT_PREF_KEY,
                    yes);
        } catch (final HardwareInterfaceException e) {
            CypressFX2.log.warning(e.toString()
                    + "; forcing syncEventEnabled=false so acquisition can continue");
            syncEventEnabled = false;
            CypressFX2LibUsbDVS128HardwareInterface.prefs.putBoolean(SYNC_EVENT_PREF_KEY, false);
            if (getPID() == CypressFX2.PID_USBAERmini2) {
                disableSyncVendorRequest = true;
            }
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

        private static final int WORD_EVENT = 0;
        private static final int WORD_WRAP = 1;
        private static final int WORD_RESET = 2;
        private static final long BOOTSTRAP_MAX_WAIT_NS = 2_000_000_000L;
        private int printedSyncEventWarningCount = 0; // only print this many sync events
        private int resetTimestampWarningCount = 0;
        private final int RESET_TIMESTAMPS_INITIAL_PRINTING_LIMIT = 10;
        private final int RESET_TIMESTAMPS_WARNING_INTERVAL = 100000;
        private final boolean debugWordDumpEnabled = Boolean.getBoolean(DEBUG_WORD_DUMP_PROPERTY);
        private final int debugWordDumpLimit = Math.max(0, Integer.getInteger(DEBUG_WORD_DUMP_LIMIT_PROPERTY, DEFAULT_DEBUG_WORD_DUMP_LIMIT));
        private int debugWordDumpCount = 0;
        private DecodeProfile decodeProfile = DecodeProfile.CPLD_LE;
        private boolean decodeProfileLocked = true;
        private int bootstrapWordCount = 0;
        private final int[] bootstrapRawWords = new int[PROFILE_BOOTSTRAP_WORDS];
        private long bootstrapStartNs = 0;
        private long rateWindowStartNs = System.nanoTime();
        private int rateWindowDecodedEvents = 0;

        private final class DecodeQuality {

            private final DecodeProfile profile;
            private int eventCount = 0;
            private int resetCount = 0;
            private int wrapCount = 0;
            private int nonMonotonicCount = 0;

            private DecodeQuality(final DecodeProfile profile) {
                this.profile = profile;
            }

            private double resetRatio(final int totalWords) {
                return (totalWords <= 0) ? 0d : (resetCount / (double) totalWords);
            }
        }

        /**
         * Constructs a new reader for the interface.
         *
         * @param cypress The CypressFX2 interface.
         * @throws HardwareInterfaceException on any hardware error.
         */
        public RetinaAEReader(final CypressFX2 cypress) throws HardwareInterfaceException {
            super(cypress);
            if (isUsbaermini2) {
                // Ported from 20180323 CypressFX2MonitorSequencer:
                // DID>0 uses CPLD code path (bit14 reset marker), older firmware does not.
                decodeProfile = (usbaermini2Did > 0) ? DecodeProfile.CPLD_LE : DecodeProfile.ORIGINAL_LE;
                decodeProfileLocked = false;
                bootstrapStartNs = System.nanoTime();
                CypressFX2.log.info(String.format(
                        "USBAERmini2 decode bootstrap enabled: initial profile=%s, DID=0x%04X",
                        decodeProfile, (usbaermini2Did & 0xFFFF)));
            }
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
                    final int b0 = b.get(i) & 0xFF;
                    final int b1 = b.get(i + 1) & 0xFF;
                    final int b2 = b.get(i + 2) & 0xFF;
                    final int b3 = b.get(i + 3) & 0xFF;
                    final int rawWord = b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
                    if (isUsbaermini2 && !decodeProfileLocked) {
                        if (bootstrapWordCount < PROFILE_BOOTSTRAP_WORDS) {
                            bootstrapRawWords[bootstrapWordCount++] = rawWord;
                        }
                        if (shouldFinalizeBootstrap()) {
                            finalizeBootstrapAndDecode(buffer, addresses, timestamps);
                        }
                        continue;
                    }
                    final DecodeProfile activeProfile = isUsbaermini2 ? decodeProfile : DecodeProfile.CPLD_LE;
                    processWord(rawWord, b0, b1, b2, b3, activeProfile, buffer, addresses, timestamps);
                } // end for

                if (isUsbaermini2 && !decodeProfileLocked && shouldFinalizeBootstrap()) {
                    finalizeBootstrapAndDecode(buffer, addresses, timestamps);
                }

                // write capture size
                buffer.lastCaptureLength = eventCounter - buffer.lastCaptureIndex;
                buffer.systemModificationTimeNs = System.nanoTime();

                // if (NumberOfWrapEvents!=0) {
                // System.out.println("Number of wrap events received: "+ NumberOfWrapEvents);
                // }
                // System.out.println("wrapAdd : "+ wrapAdd);
            } // sync on aePacketRawPool

        }

        private boolean shouldFinalizeBootstrap() {
            if (decodeProfileLocked) {
                return false;
            }
            if (bootstrapWordCount >= PROFILE_BOOTSTRAP_WORDS) {
                return true;
            }
            if (bootstrapWordCount < 64) {
                return false;
            }
            return (System.nanoTime() - bootstrapStartNs) >= BOOTSTRAP_MAX_WAIT_NS;
        }

        private void finalizeBootstrapAndDecode(final AEPacketRaw buffer, final int[] addresses, final int[] timestamps) {
            if (decodeProfileLocked || (bootstrapWordCount == 0)) {
                decodeProfileLocked = true;
                return;
            }

            final DecodeQuality activeQuality = evaluateDecodeQuality(decodeProfile, bootstrapWordCount);
            if ((activeQuality.resetRatio(bootstrapWordCount) > 0.90d) || (activeQuality.eventCount == 0)) {
                final DecodeQuality bestQuality = chooseBestProfile(bootstrapWordCount);
                if (bestQuality.profile != decodeProfile) {
                    CypressFX2.log.warning(String.format(
                            "USBAERmini2 bootstrap detected implausible decode with profile %s "
                            + "(resets=%d/%d, events=%d). Switching to %s (events=%d, resets=%d, nonMonotonic=%d).",
                            decodeProfile, activeQuality.resetCount, bootstrapWordCount, activeQuality.eventCount,
                            bestQuality.profile, bestQuality.eventCount, bestQuality.resetCount, bestQuality.nonMonotonicCount));
                    decodeProfile = bestQuality.profile;
                }
            }
            decodeProfileLocked = true;
            if (debugWordDumpEnabled) {
                CypressFX2.log.info(String.format(
                        "USBAERmini2 decode bootstrap finalized with profile %s after %d words",
                        decodeProfile, bootstrapWordCount));
            }

            for (int idx = 0; idx < bootstrapWordCount; idx++) {
                final int rawWord = bootstrapRawWords[idx];
                final int b0 = rawWord & 0xFF;
                final int b1 = (rawWord >>> 8) & 0xFF;
                final int b2 = (rawWord >>> 16) & 0xFF;
                final int b3 = (rawWord >>> 24) & 0xFF;
                processWord(rawWord, b0, b1, b2, b3, decodeProfile, buffer, addresses, timestamps);
            }

            bootstrapWordCount = 0;
        }

        private DecodeQuality chooseBestProfile(final int totalWords) {
            DecodeQuality best = null;
            for (final DecodeProfile profile : DecodeProfile.values()) {
                final DecodeQuality candidate = evaluateDecodeQuality(profile, totalWords);
                if ((best == null) || isBetterQuality(candidate, best)) {
                    best = candidate;
                }
            }
            return best;
        }

        private boolean isBetterQuality(final DecodeQuality candidate, final DecodeQuality incumbent) {
            if (candidate.eventCount != incumbent.eventCount) {
                return candidate.eventCount > incumbent.eventCount;
            }
            if (candidate.resetCount != incumbent.resetCount) {
                return candidate.resetCount < incumbent.resetCount;
            }
            if (candidate.nonMonotonicCount != incumbent.nonMonotonicCount) {
                return candidate.nonMonotonicCount < incumbent.nonMonotonicCount;
            }
            if (candidate.wrapCount != incumbent.wrapCount) {
                return candidate.wrapCount > incumbent.wrapCount;
            }
            return candidate.profile.ordinal() < incumbent.profile.ordinal();
        }

        private DecodeQuality evaluateDecodeQuality(final DecodeProfile profile, final int totalWords) {
            final DecodeQuality quality = new DecodeQuality(profile);
            int wrapAddTmp = 0;
            int lastTimestampTmpLocal = 0;

            for (int idx = 0; idx < totalWords; idx++) {
                final int rawWord = bootstrapRawWords[idx];
                final int b0 = rawWord & 0xFF;
                final int b1 = (rawWord >>> 8) & 0xFF;
                final int b2 = (rawWord >>> 16) & 0xFF;
                final int b3 = (rawWord >>> 24) & 0xFF;
                final int markerByte = markerByteForProfile(b2, b3, profile);
                final int wordType = classifyWord(markerByte, profile);

                if (wordType == WORD_WRAP) {
                    quality.wrapCount++;
                    wrapAddTmp += wrapIncrementForProfile(profile);
                } else if (wordType == WORD_RESET) {
                    quality.resetCount++;
                    wrapAddTmp = 0;
                    lastTimestampTmpLocal = 0;
                } else {
                    quality.eventCount++;
                    final int shortTimestamp = shortTimestampForProfile(b2, b3, profile);
                    final int timestamp = TICK_US * (shortTimestamp + wrapAddTmp);
                    if (timestamp < lastTimestampTmpLocal) {
                        quality.nonMonotonicCount++;
                    }
                    lastTimestampTmpLocal = timestamp;
                    final int address = addressForProfile(b0, b1, profile);
                    if ((address & 0xFFFF) == 0xFFFF) {
                        quality.nonMonotonicCount++;
                    }
                }
            }
            return quality;
        }

        private void processWord(final int rawWord, final int b0, final int b1, final int b2, final int b3,
                final DecodeProfile profile, final AEPacketRaw buffer, final int[] addresses, final int[] timestamps) {
            final int markerByte = markerByteForProfile(b2, b3, profile);
            final int wordType = classifyWord(markerByte, profile);

            if (wordType == WORD_WRAP) {
                wrapAdd += wrapIncrementForProfile(profile);
                logDebugWordIfEnabled(rawWord, b0, b1, b2, b3, "wrap", -1, -1, profile, markerByte);
                return;
            }
            if (wordType == WORD_RESET) {
                // For reset markers from hardware, reset local unwrap state only.
                // Do not send resetTimestamps() back to device from stream parser.
                wrapAdd = 0;
                lastTimestampTmp = 0;
                logDebugWordIfEnabled(rawWord, b0, b1, b2, b3, "reset", -1, -1, profile, markerByte);
                if ((resetTimestampWarningCount < RESET_TIMESTAMPS_INITIAL_PRINTING_LIMIT)
                        || ((resetTimestampWarningCount % RESET_TIMESTAMPS_WARNING_INTERVAL) == 0)) {
                    final int resetShortTs = shortTimestampForProfile(b2, b3, profile) & 0x3FFF;
                    CypressFX2.log.info(this + ".translateEvents got reset event from hardware, timestamp " + resetShortTs);
                }
                if (resetTimestampWarningCount == RESET_TIMESTAMPS_INITIAL_PRINTING_LIMIT) {
                    CypressFX2.log.warning("will only print reset timestamps message every "
                            + RESET_TIMESTAMPS_WARNING_INTERVAL + " times now\n"
                            + "If you are injecting sync events using the DVS128 IN pin, try enabling sync events output in the DVS128 menu.");
                }
                resetTimestampWarningCount++;
                return;
            }

            if ((eventCounter > (aeBufferSize - 1)) || (buffer.overrunOccuredFlag)) {
                buffer.overrunOccuredFlag = true;
                return;
            }

            final int address = addressForProfile(b0, b1, profile);
            final int shortTimestamp = shortTimestampForProfile(b2, b3, profile);
            addresses[eventCounter] = address;
            timestamps[eventCounter] = TICK_US * (shortTimestamp + wrapAdd);
            logDebugWordIfEnabled(rawWord, b0, b1, b2, b3, "event", address, shortTimestamp, profile, markerByte);

            if (timestamps[eventCounter] < lastTimestampTmp) {
                CypressFX2.log.info(String.format("nonmonotonic timestamp: lastTimestamp=%,d, timestamp=%,d, dt=%,d",
                        lastTimestampTmp,
                        timestamps[eventCounter],
                        (lastTimestampTmp - timestamps[eventCounter])));
            }
            lastTimestampTmp = timestamps[eventCounter];

            if ((addresses[eventCounter] & CypressFX2LibUsbDVS128HardwareInterface.SYNC_EVENT_BITMASK) != 0) {
                if (printedSyncEventWarningCount < 10) {
                    if (printedSyncEventWarningCount < 10) {
                        CypressFX2.log.info("sync event at timestamp=" + timestamps[eventCounter]);
                    } else {
                        CypressFX2.log.warning("disabling further printing of sync events");
                    }
                    printedSyncEventWarningCount++;
                }
            }
            eventCounter++;
            buffer.setNumEvents(eventCounter);
            maybeLogDecodedRate();
        }

        private int addressForProfile(final int b0, final int b1, final DecodeProfile profile) {
            if ((profile == DecodeProfile.CPLD_BE) || (profile == DecodeProfile.ORIGINAL_BE)) {
                return (b0 << 8) | b1;
            }
            return b0 | (b1 << 8);
        }

        private int shortTimestampForProfile(final int b2, final int b3, final DecodeProfile profile) {
            if ((profile == DecodeProfile.CPLD_BE) || (profile == DecodeProfile.ORIGINAL_BE)) {
                return (b2 << 8) | b3;
            }
            return b2 | (b3 << 8);
        }

        private int markerByteForProfile(final int b2, final int b3, final DecodeProfile profile) {
            if ((profile == DecodeProfile.CPLD_BE) || (profile == DecodeProfile.ORIGINAL_BE)) {
                return b2;
            }
            return b3;
        }

        private boolean isCpldProfile(final DecodeProfile profile) {
            return (profile == DecodeProfile.CPLD_LE) || (profile == DecodeProfile.CPLD_BE);
        }

        private int wrapIncrementForProfile(final DecodeProfile profile) {
            return isCpldProfile(profile) ? 0x4000 : 0x8000;
        }

        private int classifyWord(final int markerByte, final DecodeProfile profile) {
            if ((markerByte & 0x80) == 0x80) {
                return WORD_WRAP;
            }
            // In the original USBAERmini2 firmware mode, bit 0x40 is normal timestamp data,
            // not a reset marker. Only CPLD profiles treat 0x40 as reset.
            if (isCpldProfile(profile) && ((markerByte & 0x40) == 0x40)) {
                return WORD_RESET;
            }
            return WORD_EVENT;
        }

        private void maybeLogDecodedRate() {
            if (!isUsbaermini2 || !debugWordDumpEnabled) {
                return;
            }
            rateWindowDecodedEvents++;
            final long nowNs = System.nanoTime();
            final long dtNs = nowNs - rateWindowStartNs;
            if (dtNs >= 1_000_000_000L) {
                final long rate = Math.round((rateWindowDecodedEvents * 1_000_000_000d) / dtNs);
                CypressFX2.log.info(String.format("USBAERmini2 decodedEventsPerSecond=%d (profile=%s)", rate, decodeProfile));
                rateWindowStartNs = nowNs;
                rateWindowDecodedEvents = 0;
            }
        }

        private void logDebugWordIfEnabled(final int rawWord, final int b0, final int b1, final int b2, final int b3,
                final String type, final int address, final int shortts, final DecodeProfile profile, final int markerByte) {
            if (!isUsbaermini2 || !debugWordDumpEnabled || (debugWordDumpCount >= debugWordDumpLimit)) {
                return;
            }

            final String eventFields = (address >= 0) ? String.format(", addr=0x%04X, shortts=0x%04X", address, shortts) : "";
            CypressFX2.log.info(String.format(
                    "USBAERmini2 decode[%d]: bytes=%02X %02X %02X %02X rawLE=0x%08X profile=%s marker=0x%02X type=%s wrapAdd=%d%s",
                    debugWordDumpCount, b0, b1, b2, b3, rawWord, profile, markerByte, type, wrapAdd, eventFields));
            debugWordDumpCount++;
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
