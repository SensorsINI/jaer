package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import eu.seebetter.ini.chips.DavisChip;
import eu.seebetter.ini.chips.davis.DavisUsbPacketBundleBuilder;
import eu.seebetter.ini.chips.davis.imu.IMUSample;
import java.util.function.IntSupplier;

/**
 * Package-local typed-first GAER sink. For each semantic action it dispatches
 * to the {@link DavisUsbPacketBundleBuilder} (typed output) first and then to
 * the {@link SciDVSGaerRawSink} (raw output), so the typed path is populated
 * even when the raw path overruns its capacity.
 */
final class SciDVSGaerTypedSink implements SciDVSGaerSink {

    private final DavisUsbPacketBundleBuilder builder;
    private final SciDVSGaerRawSink rawSink;
    private final IntSupplier typedUnflipSizeX;
    private final Runnable timestampResetHandler;
    private boolean rollingShutter;

    SciDVSGaerTypedSink(final DavisUsbPacketBundleBuilder builder,
            final SciDVSGaerRawSink rawSink, final IntSupplier typedUnflipSizeX,
            final Runnable timestampResetHandler) {
        this.builder = builder;
        this.rawSink = rawSink;
        this.typedUnflipSizeX = typedUnflipSizeX;
        this.timestampResetHandler = timestampResetHandler;
    }

    @Override
    public void onTimestampReset() {
        timestampResetHandler.run();
    }

    @Override
    public void onExternalInput(final int code, final int packedAddress,
            final int timestamp) {
        builder.addExternal(code, timestamp);
        rawSink.onExternalInput(code, packedAddress, timestamp);
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
        builder.addPolarity(typedX, typedY, on, timestamp);
        rawSink.onPolarity(packedAddress, x, y, on, timestamp);
    }

    @Override
    public void onApsSample(final int packedAddress, final int adcData,
            final int x, final int y, final boolean resetRead,
            final boolean pixelFirst, final boolean pixelLast, final int timestamp) {
        builder.setRollingShutter(rollingShutter);
        builder.addApsSample(adcData, timestamp, x, y, resetRead,
                pixelFirst, pixelLast);
        rawSink.onApsSample(packedAddress, adcData, x, y, resetRead,
                pixelFirst, pixelLast, timestamp);
    }

    @Override
    public void onImuSample(final IMUSample sample, final int timestamp) {
        builder.addImu(sample);
        rawSink.onImuSample(sample, timestamp);
    }

    @Override
    public void onAddressPatch(final int orMask) {
        rawSink.onAddressPatch(orMask);
    }
}
