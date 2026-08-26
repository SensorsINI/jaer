package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.DavisUsbPacketBundleBuilder;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import java.util.function.IntSupplier;

/**
 * Package-local authoritative typed GAER sink. It has no legacy raw packet or
 * raw sink dependency.
 */
final class SciDVSGaerTypedSink implements SciDVSGaerSink {

    private final DavisUsbPacketBundleBuilder builder;
    private final IntSupplier typedUnflipSizeX;
    private final Runnable timestampResetHandler;
    private boolean rollingShutter;

    SciDVSGaerTypedSink(final DavisUsbPacketBundleBuilder builder,
            final IntSupplier typedUnflipSizeX,
            final Runnable timestampResetHandler) {
        this.builder = builder;
        this.typedUnflipSizeX = typedUnflipSizeX;
        this.timestampResetHandler = timestampResetHandler;
    }

    @Override
    public void onTimestampReset() {
        // SciDVS has no IMU stream, so reset cannot discard hidden IMU state.
        builder.onTimestampReset(false);
        timestampResetHandler.run();
    }

    @Override
    public void onExternalInput(final int code, final int packedAddress,
            final int timestamp) {
        builder.addExternal(code, timestamp);
    }

    @Override
    public void onFrameStart(final boolean rolling, final int timestamp) {
        rollingShutter = rolling;
        builder.onFrameStart(rolling, timestamp);
    }

    @Override
    public void onPolarity(final int packedAddress, final int x, final int y,
            final boolean on, final int timestamp) {
        final int typedX = typedUnflipSizeX.getAsInt() - 1
                - ((packedAddress & DavisChip.XMASK) >>> DavisChip.XSHIFT);
        final int typedY = (packedAddress & DavisChip.YMASK) >>> DavisChip.YSHIFT;
        builder.addPolarity(typedX, typedY, on, timestamp, packedAddress);
    }

    @Override
    public void onApsSample(final int packedAddress, final int adcData,
            final int x, final int y, final boolean resetRead,
            final boolean pixelFirst, final boolean pixelLast, final int timestamp) {
        builder.setRollingShutter(rollingShutter);
        builder.addApsSample(adcData, timestamp, x, y, resetRead,
                pixelFirst, pixelLast);
    }

    @Override
    public void onImuSample(final IMUSample sample, final int timestamp) {
        builder.addImu(sample);
    }

    @Override
    public void onAddressPatch(final int orMask) {
        builder.patchLastPolarityAddress(orMask);
    }
}
