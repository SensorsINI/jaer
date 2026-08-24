package net.sf.jaer.hardwareinterface.usb.cypressfx3libusb;

/**
 * Package-local gate for repeated GAER decoder per-event warnings. A decoder
 * consults {@link #shouldLog()} once per occurrence of each per-event warning;
 * returning {@code false} swallows that occurrence. The {@link #ALWAYS}
 * implementation keeps every warning, preserving the legacy always-log
 * behaviour of the two-argument constructors.
 */
@FunctionalInterface
interface SciDVSGaerLogThrottle {

    /** Always logs every per-event warning (legacy behaviour). */
    SciDVSGaerLogThrottle ALWAYS = () -> true;

    /**
     * @return {@code true} to emit this occurrence of the per-event warning,
     *         {@code false} to suppress it.
     */
    boolean shouldLog();
}
