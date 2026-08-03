/*
 * UsbDevices.java
 */
package net.sf.jaer;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares USB VID/PID pairs that an {@link net.sf.jaer.chip.AEChip} is
 * compatible with for live capture. Inherited by subclasses so a base camera
 * class (e.g. {@code DavisBaseCamera}) can cover a whole family that shares
 * the same USB identity.
 * <p>
 * Used by {@link net.sf.jaer.hardwareinterface.usb.LiveDeviceChipDetector} when
 * AEViewer offers a matching AEChip for a plugged-in camera.
 *
 * <pre>
 * {@code
 * @UsbDevices({
 *   @UsbDevice(vid = CypressFX3.VID, pid = DAViSFX3HardwareInterface.PID_FX3),
 *   @UsbDevice(vid = CypressFX3.VID, pid = DAViSFX3HardwareInterface.PID_FX2)
 * })
 * public class DavisBaseCamera extends DavisChip { ... }
 * }
 * </pre>
 *
 * @see UsbDevice
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface UsbDevices {

    UsbDevice[] value();
}
