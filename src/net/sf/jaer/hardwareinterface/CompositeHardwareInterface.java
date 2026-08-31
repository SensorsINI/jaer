/*
 * CompositeHardwareInterface.java
 *
 * A HardwareInterface that owns more than one USB (or other) device, e.g. a
 * stereo / FlyEye pair. Enumeration and AEViewer claim checks must unwrap
 * these so both cameras show as taken by the same window.
 */
package net.sf.jaer.hardwareinterface;

/**
 * Marker for wrappers such as {@code StereoPairHardwareInterface} that bind
 * several physical devices to one {@code AEChip}.
 *
 * @see net.sf.jaer.hardwareinterface.usb.UsbIds#samePhysicalDevice
 */
public interface CompositeHardwareInterface extends HardwareInterface {

    /**
     * Component interfaces (left/right or camera 0..n-1). Null slots allowed.
     * Does not open USB.
     */
    HardwareInterface[] getComponentInterfaces();
}
