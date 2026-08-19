package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import eu.seebetter.ini.chips.davis.imu.IMUSample;
import java.util.function.IntSupplier;
import net.sf.jaer.aemonitor.AEPacketRaw;

/** Writes GAER semantic actions into the legacy {@link AEPacketRaw} format. */
final class SciDVSGaerRawSink implements SciDVSGaerSink {

    private final IntSupplier aeBufferSize;
    private final Runnable timestampResetHandler;
    private AEPacketRaw packet;
    private int eventCounter;

    SciDVSGaerRawSink(final IntSupplier aeBufferSize,
            final Runnable timestampResetHandler) {
        this.aeBufferSize = aeBufferSize;
        this.timestampResetHandler = timestampResetHandler;
    }

    void begin(final AEPacketRaw packet, final int startingEventCounter) {
        this.packet = packet;
        eventCounter = startingEventCounter;
        packet.lastCaptureIndex = startingEventCounter;
    }

    int end() {
        packet.setNumEvents(eventCounter);
        packet.lastCaptureLength = eventCounter - packet.lastCaptureIndex;
        return eventCounter;
    }

    @Override
    public void onTimestampReset() {
        timestampResetHandler.run();
    }

    @Override
    public void onExternalInput(final int code, final int packedAddress,
            final int timestamp) {
        writeEvent(packedAddress, timestamp);
    }

    @Override
    public void onPolarity(final int packedAddress, final int x, final int y,
            final boolean on, final int timestamp) {
        writeEvent(packedAddress, timestamp);
    }

    @Override
    public void onApsSample(final int packedAddress, final int adcData,
            final int x, final int y, final boolean resetRead,
            final boolean pixelFirst, final boolean pixelLast, final int timestamp) {
        writeEvent(packedAddress, timestamp);
    }

    @Override
    public void onImuSample(final IMUSample sample, final int timestamp) {
        if (ensureCapacity(eventCounter + IMUSample.SIZE_EVENTS)) {
            eventCounter += sample.writeToPacket(packet, eventCounter);
        }
    }

    @Override
    public void onAddressPatch(final int orMask) {
        if ((eventCounter >= 1) && ensureCapacity(eventCounter)) {
            packet.getAddresses()[eventCounter - 1] |= orMask;
        }
    }

    private void writeEvent(final int packedAddress, final int timestamp) {
        if (ensureCapacity(eventCounter + 1)) {
            packet.getAddresses()[eventCounter] = packedAddress;
            packet.getTimestamps()[eventCounter++] = timestamp;
        }
    }

    private boolean ensureCapacity(final int capacity) {
        if (packet.getCapacity() > aeBufferSize.getAsInt()) {
            if (packet.overrunOccuredFlag || (capacity > packet.getCapacity())) {
                packet.overrunOccuredFlag = true;
                return false;
            }
            return true;
        }
        packet.ensureCapacity(capacity);
        return true;
    }
}
