package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

import eu.seebetter.ini.chips.davis.imu.IMUSample;

/** Semantic actions produced by the non-FX10 SciDVS GAER decoder. */
interface SciDVSGaerSink {

    default void onTimestampReset() {
    }

    default void onExternalInput(final int code, final int packedAddress, final int timestamp) {
    }

    default void onFrameStart(final boolean rollingShutter, final int timestamp) {
    }

    default void onFrameEnd(final boolean rollingShutter, final int timestamp) {
    }

    default void onExposureStart(final int timestamp) {
    }

    default void onExposureEnd(final int timestamp) {
    }

    default void onPolarity(final int packedAddress, final int x, final int y,
            final boolean on, final int timestamp) {
    }

    default void onApsSample(final int packedAddress, final int adcData,
            final int x, final int y, final boolean resetRead,
            final boolean pixelFirst, final boolean pixelLast, final int timestamp) {
    }

    default void onImuSample(final IMUSample sample, final int timestamp) {
    }

    default void onAddressPatch(final int orMask) {
    }
}
